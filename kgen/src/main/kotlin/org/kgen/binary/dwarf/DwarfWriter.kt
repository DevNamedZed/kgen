package org.kgen.binary.dwarf

import org.kgen.binary.*
import java.io.ByteArrayOutputStream

/**
 * Generates DWARF 4 debug sections from a [DebugInfo] model.
 *
 * Produces four sections: `.debug_abbrev`, `.debug_info`, `.debug_str`, and `.debug_line`.
 * These can be added to an [ObjectFile] for inclusion in ELF/PE/Mach-O binaries.
 *
 * ```java
 * var debug = new DebugInfo(List.of(compileUnit));
 * var result = DwarfWriter.write(debug);
 * // result.debugInfo, result.debugAbbrev, result.debugStr, result.debugLine
 * ```
 */
class DwarfWriter private constructor(
    private val debugInfo: DebugInfo,
    private val addrSize: Int,
) {
    private val abbrevOut = ByteArrayOutputStream()
    private val infoOut = ByteArrayOutputStream()
    private val strTable = StringTable()
    private val lineOut = ByteArrayOutputStream()
    private var nextAbbrevCode = 1

    /**
     * Result of DWARF generation: raw bytes for each section.
     */
    data class DwarfSections(
        val debugInfo: ByteArray,
        val debugAbbrev: ByteArray,
        val debugStr: ByteArray,
        val debugLine: ByteArray,
    ) {
        /**
         * Convert to [Section] list for inclusion in an [ObjectFile].
         */
        fun toSections(): List<Section> = buildList {
            add(Section(".debug_info", SectionKind.DEBUG_INFO, debugInfo))
            add(Section(".debug_abbrev", SectionKind.DEBUG_ABBREV, debugAbbrev))
            add(Section(".debug_str", SectionKind.DEBUG_STR, debugStr))
            if (debugLine.isNotEmpty()) {
                add(Section(".debug_line", SectionKind.DEBUG_LINE, debugLine))
            }
        }
    }

    private fun generate(): DwarfSections {
        for (cu in debugInfo.compileUnits) {
            emitCompileUnit(cu)
        }
        // Terminate abbreviation table (single table shared by all CUs)
        abbrevOut.write(0)
        return DwarfSections(
            debugInfo = infoOut.toByteArray(),
            debugAbbrev = abbrevOut.toByteArray(),
            debugStr = strTable.toByteArray(),
            debugLine = lineOut.toByteArray(),
        )
    }

    // -- Compile unit emission --

    private fun emitCompileUnit(cu: CompileUnit) {
        // Track type offsets for cross-references
        val typeOffsets = mutableMapOf<DebugType, Int>()

        // Pre-register all abbreviation codes
        val cuAbbrev = nextAbbrev(
            DwarfTag.COMPILE_UNIT, hasChildren = true,
            listOf(
                DwarfAttribute.PRODUCER to DwarfForm.STRP,
                DwarfAttribute.LANGUAGE to DwarfForm.DATA2,
                DwarfAttribute.NAME to DwarfForm.STRP,
                DwarfAttribute.COMP_DIR to DwarfForm.STRP,
                DwarfAttribute.LOW_PC to DwarfForm.ADDR,
                DwarfAttribute.HIGH_PC to DwarfForm.ADDR,
            )
        )

        // Emit .debug_info for this CU
        // We'll write the unit header, then patch the length at the end
        val cuStart = infoOut.size()

        // Unit length placeholder (4 bytes, patched later)
        writeU32(infoOut, 0)
        // Version
        writeU16(infoOut, 4) // DWARF 4
        // Abbrev offset
        writeU32(infoOut, 0) // offset into .debug_abbrev — we only have one table at offset 0
        // Address size
        infoOut.write(addrSize)

        // CU DIE
        writeULEB128(infoOut, cuAbbrev)
        writeStrp(cu.producer)
        writeU16(infoOut, mapLanguageCode(cu.language))
        writeStrp(cu.name)
        writeStrp(cu.directory)
        writeAddr(cu.lowPC)
        writeAddr(cu.highPC)

        // Emit types
        for (type in cu.types) {
            emitType(type, typeOffsets)
        }

        // Emit subprograms
        for (sub in cu.subprograms) {
            emitSubprogram(sub, typeOffsets)
        }

        // Emit global variables
        for (variable in cu.variables) {
            emitVariable(variable, typeOffsets)
        }

        // Null terminator for CU children
        infoOut.write(0)

        // Patch unit length
        val cuEnd = infoOut.size()
        val unitLength = cuEnd - cuStart - 4 // exclude the 4-byte length field
        patchU32(infoOut, cuStart, unitLength)

        // Emit line number program if we have line info
        if (cu.lineInfo.isNotEmpty()) {
            emitLineProgram(cu)
        }
    }

    // -- Type emission --

    private fun emitType(type: DebugType, offsets: MutableMap<DebugType, Int>) {
        if (type in offsets) return

        // Emit referenced types first
        when (type) {
            is DebugType.Pointer -> emitType(type.pointee, offsets)
            is DebugType.Reference -> emitType(type.referent, offsets)
            is DebugType.Const -> emitType(type.baseType, offsets)
            is DebugType.Volatile -> emitType(type.baseType, offsets)
            is DebugType.Typedef -> emitType(type.baseType, offsets)
            is DebugType.Array -> emitType(type.element, offsets)
            is DebugType.Composite -> {
                for (member in type.members) emitType(member.type, offsets)
            }
            is DebugType.Enum -> emitType(type.baseType, offsets)
            is DebugType.Subroutine -> {
                type.returnType?.let { emitType(it, offsets) }
                for (pt in type.paramTypes) emitType(pt, offsets)
            }
            is DebugType.Base -> {}
        }

        // Record offset relative to CU header for this type
        offsets[type] = infoOut.size()

        when (type) {
            is DebugType.Base -> emitBaseType(type)
            is DebugType.Pointer -> emitPointerType(type, offsets)
            is DebugType.Reference -> emitReferenceType(type, offsets)
            is DebugType.Const -> emitConstType(type, offsets)
            is DebugType.Volatile -> emitVolatileType(type, offsets)
            is DebugType.Typedef -> emitTypedefType(type, offsets)
            is DebugType.Array -> emitArrayType(type, offsets)
            is DebugType.Composite -> emitCompositeType(type, offsets)
            is DebugType.Enum -> emitEnumType(type, offsets)
            is DebugType.Subroutine -> emitSubroutineType(type, offsets)
        }
    }

    private fun emitBaseType(type: DebugType.Base) {
        val abbrev = nextAbbrev(
            DwarfTag.BASE_TYPE, hasChildren = false,
            listOf(
                DwarfAttribute.NAME to DwarfForm.STRP,
                DwarfAttribute.ENCODING to DwarfForm.DATA1,
                DwarfAttribute.BYTE_SIZE to DwarfForm.DATA1,
            )
        )
        writeULEB128(infoOut, abbrev)
        writeStrp(type.name)
        infoOut.write(mapEncodingCode(type.encoding))
        infoOut.write((type.sizeInBits / 8).toInt())
    }

    private fun emitPointerType(type: DebugType.Pointer, offsets: Map<DebugType, Int>) {
        val abbrev = nextAbbrev(
            DwarfTag.POINTER_TYPE, hasChildren = false,
            listOf(
                DwarfAttribute.TYPE to DwarfForm.REF4,
                DwarfAttribute.BYTE_SIZE to DwarfForm.DATA1,
            )
        )
        writeULEB128(infoOut, abbrev)
        writeRef4(offsets[type.pointee] ?: 0)
        infoOut.write((type.sizeInBits / 8).toInt())
    }

    private fun emitReferenceType(type: DebugType.Reference, offsets: Map<DebugType, Int>) {
        val abbrev = nextAbbrev(
            DwarfTag.REFERENCE_TYPE, hasChildren = false,
            listOf(
                DwarfAttribute.TYPE to DwarfForm.REF4,
                DwarfAttribute.BYTE_SIZE to DwarfForm.DATA1,
            )
        )
        writeULEB128(infoOut, abbrev)
        writeRef4(offsets[type.referent] ?: 0)
        infoOut.write((type.sizeInBits / 8).toInt())
    }

    private fun emitConstType(type: DebugType.Const, offsets: Map<DebugType, Int>) {
        val abbrev = nextAbbrev(
            DwarfTag.CONST_TYPE, hasChildren = false,
            listOf(DwarfAttribute.TYPE to DwarfForm.REF4)
        )
        writeULEB128(infoOut, abbrev)
        writeRef4(offsets[type.baseType] ?: 0)
    }

    private fun emitVolatileType(type: DebugType.Volatile, offsets: Map<DebugType, Int>) {
        val abbrev = nextAbbrev(
            DwarfTag.VOLATILE_TYPE, hasChildren = false,
            listOf(DwarfAttribute.TYPE to DwarfForm.REF4)
        )
        writeULEB128(infoOut, abbrev)
        writeRef4(offsets[type.baseType] ?: 0)
    }

    private fun emitTypedefType(type: DebugType.Typedef, offsets: Map<DebugType, Int>) {
        val abbrev = nextAbbrev(
            DwarfTag.TYPEDEF, hasChildren = false,
            listOf(
                DwarfAttribute.NAME to DwarfForm.STRP,
                DwarfAttribute.TYPE to DwarfForm.REF4,
            )
        )
        writeULEB128(infoOut, abbrev)
        writeStrp(type.name)
        writeRef4(offsets[type.baseType] ?: 0)
    }

    private fun emitArrayType(type: DebugType.Array, offsets: Map<DebugType, Int>) {
        val abbrev = nextAbbrev(
            DwarfTag.ARRAY_TYPE, hasChildren = true,
            listOf(DwarfAttribute.TYPE to DwarfForm.REF4)
        )
        writeULEB128(infoOut, abbrev)
        writeRef4(offsets[type.element] ?: 0)

        // Subrange child
        val subrangeAbbrev = nextAbbrev(
            DwarfTag.SUBRANGE_TYPE, hasChildren = false,
            listOf(DwarfAttribute.UPPER_BOUND to DwarfForm.DATA4)
        )
        writeULEB128(infoOut, subrangeAbbrev)
        writeU32(infoOut, (type.count - 1).toInt())

        infoOut.write(0) // end children
    }

    private fun emitCompositeType(type: DebugType.Composite, offsets: Map<DebugType, Int>) {
        val tag = when (type.tag) {
            CompositeTag.STRUCT -> DwarfTag.STRUCTURE_TYPE
            CompositeTag.CLASS -> DwarfTag.CLASS_TYPE
            CompositeTag.UNION -> DwarfTag.UNION_TYPE
            CompositeTag.INTERFACE -> DwarfTag.CLASS_TYPE
        }
        val abbrev = nextAbbrev(
            tag, hasChildren = type.members.isNotEmpty(),
            listOf(
                DwarfAttribute.NAME to DwarfForm.STRP,
                DwarfAttribute.BYTE_SIZE to DwarfForm.DATA4,
            )
        )
        writeULEB128(infoOut, abbrev)
        writeStrp(type.name)
        writeU32(infoOut, (type.sizeInBits / 8).toInt())

        for (member in type.members) {
            emitMember(member, offsets)
        }
        if (type.members.isNotEmpty()) infoOut.write(0) // end children
    }

    private fun emitMember(member: DebugMember, offsets: Map<DebugType, Int>) {
        val abbrev = nextAbbrev(
            DwarfTag.MEMBER, hasChildren = false,
            listOf(
                DwarfAttribute.NAME to DwarfForm.STRP,
                DwarfAttribute.TYPE to DwarfForm.REF4,
                DwarfAttribute.DATA_MEMBER_LOCATION to DwarfForm.DATA4,
            )
        )
        writeULEB128(infoOut, abbrev)
        writeStrp(member.name)
        writeRef4(offsets[member.type] ?: 0)
        writeU32(infoOut, (member.offsetInBits / 8).toInt())
    }

    private fun emitEnumType(type: DebugType.Enum, offsets: Map<DebugType, Int>) {
        val abbrev = nextAbbrev(
            DwarfTag.ENUMERATION_TYPE, hasChildren = type.enumerators.isNotEmpty(),
            listOf(
                DwarfAttribute.NAME to DwarfForm.STRP,
                DwarfAttribute.TYPE to DwarfForm.REF4,
                DwarfAttribute.BYTE_SIZE to DwarfForm.DATA1,
            )
        )
        writeULEB128(infoOut, abbrev)
        writeStrp(type.name)
        writeRef4(offsets[type.baseType] ?: 0)
        infoOut.write((type.sizeInBits / 8).toInt())

        for ((name, value) in type.enumerators) {
            val enumAbbrev = nextAbbrev(
                DwarfTag.ENUMERATOR, hasChildren = false,
                listOf(
                    DwarfAttribute.NAME to DwarfForm.STRP,
                    DwarfAttribute.CONST_VALUE to DwarfForm.SDATA,
                )
            )
            writeULEB128(infoOut, enumAbbrev)
            writeStrp(name)
            writeSLEB128(infoOut, value)
        }
        if (type.enumerators.isNotEmpty()) infoOut.write(0) // end children
    }

    private fun emitSubroutineType(type: DebugType.Subroutine, offsets: Map<DebugType, Int>) {
        val attrs = mutableListOf<Pair<DwarfAttribute, DwarfForm>>()
        if (type.returnType != null) attrs.add(DwarfAttribute.TYPE to DwarfForm.REF4)

        val abbrev = nextAbbrev(
            DwarfTag.SUBROUTINE_TYPE, hasChildren = type.paramTypes.isNotEmpty(),
            attrs,
        )
        writeULEB128(infoOut, abbrev)
        if (type.returnType != null) writeRef4(offsets[type.returnType] ?: 0)

        for (pt in type.paramTypes) {
            val paramAbbrev = nextAbbrev(
                DwarfTag.FORMAL_PARAMETER, hasChildren = false,
                listOf(DwarfAttribute.TYPE to DwarfForm.REF4)
            )
            writeULEB128(infoOut, paramAbbrev)
            writeRef4(offsets[pt] ?: 0)
        }
        if (type.paramTypes.isNotEmpty()) infoOut.write(0) // end children
    }

    // -- Subprogram emission --

    private fun emitSubprogram(sub: DebugSubprogram, offsets: Map<DebugType, Int>) {
        val attrs = mutableListOf(
            DwarfAttribute.NAME to DwarfForm.STRP,
            DwarfAttribute.LOW_PC to DwarfForm.ADDR,
            DwarfAttribute.HIGH_PC to DwarfForm.ADDR,
            DwarfAttribute.DECL_FILE to DwarfForm.STRP,
            DwarfAttribute.DECL_LINE to DwarfForm.DATA2,
        )
        if (sub.linkageName != null) attrs.add(DwarfAttribute.LINKAGE_NAME to DwarfForm.STRP)
        if (sub.returnType != null) attrs.add(DwarfAttribute.TYPE to DwarfForm.REF4)

        val hasChildren = sub.params.isNotEmpty() || sub.localVariables.isNotEmpty()
        val abbrev = nextAbbrev(DwarfTag.SUBPROGRAM, hasChildren, attrs)
        writeULEB128(infoOut, abbrev)

        writeStrp(sub.name)
        writeAddr(sub.lowPC)
        writeAddr(sub.highPC)
        writeStrp(sub.file)
        writeU16(infoOut, sub.line)
        if (sub.linkageName != null) writeStrp(sub.linkageName)
        if (sub.returnType != null) writeRef4(offsets[sub.returnType] ?: 0)

        for (param in sub.params) {
            emitFormalParameter(param, offsets)
        }
        for (local in sub.localVariables) {
            emitVariable(local, offsets)
        }
        if (hasChildren) infoOut.write(0) // end children
    }

    private fun emitFormalParameter(param: DebugVariable, offsets: Map<DebugType, Int>) {
        val abbrev = nextAbbrev(
            DwarfTag.FORMAL_PARAMETER, hasChildren = false,
            listOf(
                DwarfAttribute.NAME to DwarfForm.STRP,
                DwarfAttribute.TYPE to DwarfForm.REF4,
            )
        )
        writeULEB128(infoOut, abbrev)
        writeStrp(param.name)
        writeRef4(offsets[param.type] ?: 0)
    }

    private fun emitVariable(variable: DebugVariable, offsets: Map<DebugType, Int>) {
        val abbrev = nextAbbrev(
            DwarfTag.VARIABLE, hasChildren = false,
            listOf(
                DwarfAttribute.NAME to DwarfForm.STRP,
                DwarfAttribute.TYPE to DwarfForm.REF4,
            )
        )
        writeULEB128(infoOut, abbrev)
        writeStrp(variable.name)
        writeRef4(offsets[variable.type] ?: 0)
    }

    // -- Line number program (DWARF 4 standard, section 6.2) --

    private fun emitLineProgram(cu: CompileUnit) {
        val entries = cu.lineInfo.sortedBy { it.address }
        if (entries.isEmpty()) return

        // Collect unique files
        val files = entries.mapTo(mutableSetOf()) { it.file }.toList()
        val fileMap = files.withIndex().associate { (i, f) -> f to (i + 1) }

        val programBytes = ByteArrayOutputStream()

        // State machine initial values
        var address = 0L
        var file = 1
        var line = 1
        var column = 0

        for (entry in entries) {
            val fileIdx = fileMap[entry.file] ?: 1
            if (fileIdx != file) {
                // DW_LNS_set_file
                programBytes.write(4)
                writeULEB128(programBytes, fileIdx)
                file = fileIdx
            }
            if (entry.column != column) {
                // DW_LNS_set_column
                programBytes.write(5)
                writeULEB128(programBytes, entry.column)
                column = entry.column
            }

            val addrDelta = entry.address - address
            val lineDelta = entry.line - line

            if (addrDelta != 0L) {
                // DW_LNS_advance_pc
                programBytes.write(2)
                writeULEB128(programBytes, addrDelta.toInt())
                address = entry.address
            }

            if (lineDelta != 0) {
                // DW_LNS_advance_line
                programBytes.write(3)
                writeSLEB128(programBytes, lineDelta.toLong())
                line = entry.line
            }

            // DW_LNS_copy — emit a row
            programBytes.write(1)
        }

        // End sequence: DW_LNE_end_sequence
        programBytes.write(0) // extended opcode marker
        programBytes.write(1) // length of extended opcode
        programBytes.write(1) // DW_LNE_end_sequence

        val program = programBytes.toByteArray()

        // Build the header
        val headerBytes = ByteArrayOutputStream()
        // minimum_instruction_length
        headerBytes.write(1)
        // maximum_operations_per_instruction (DWARF 4)
        headerBytes.write(1)
        // default_is_stmt
        headerBytes.write(1)
        // line_base
        headerBytes.write((-5 + 256) and 0xFF) // line_base = -5 (signed byte)
        // line_range
        headerBytes.write(14)
        // opcode_base
        headerBytes.write(13)
        // standard_opcode_lengths (opcodes 1-12)
        headerBytes.write(0) // DW_LNS_copy
        headerBytes.write(1) // DW_LNS_advance_pc
        headerBytes.write(1) // DW_LNS_advance_line
        headerBytes.write(1) // DW_LNS_set_file
        headerBytes.write(1) // DW_LNS_set_column
        headerBytes.write(0) // DW_LNS_negate_stmt
        headerBytes.write(0) // DW_LNS_set_basic_block
        headerBytes.write(0) // DW_LNS_const_add_pc
        headerBytes.write(1) // DW_LNS_fixed_advance_pc
        headerBytes.write(0) // DW_LNS_set_prologue_end
        headerBytes.write(0) // DW_LNS_set_epilogue_begin
        headerBytes.write(1) // DW_LNS_set_isa

        // Include directories (empty)
        headerBytes.write(0) // terminator

        // File names
        for (f in files) {
            headerBytes.write(f.toByteArray(Charsets.UTF_8))
            headerBytes.write(0) // null-terminate name
            writeULEB128(headerBytes, 0) // directory index
            writeULEB128(headerBytes, 0) // last modification
            writeULEB128(headerBytes, 0) // file size
        }
        headerBytes.write(0) // terminator

        val header = headerBytes.toByteArray()
        val headerLength = header.size

        // Write the complete .debug_line contribution
        val totalLength = 2 + 4 + headerLength + program.size // version(2) + header_length(4) + header + program
        writeU32(lineOut, totalLength)  // unit_length
        writeU16(lineOut, 4) // version (DWARF 4)
        writeU32(lineOut, headerLength) // header_length
        lineOut.write(header)
        lineOut.write(program)
    }

    // -- Abbreviation table emission --

    private fun nextAbbrev(
        tag: DwarfTag,
        hasChildren: Boolean,
        attrs: List<Pair<DwarfAttribute, DwarfForm>>,
    ): Int {
        val code = nextAbbrevCode++
        writeULEB128(abbrevOut, code)
        writeULEB128(abbrevOut, tag.code)
        abbrevOut.write(if (hasChildren) 1 else 0)
        for ((attr, form) in attrs) {
            writeULEB128(abbrevOut, attr.code)
            writeULEB128(abbrevOut, form.code)
        }
        writeULEB128(abbrevOut, 0) // terminate attr name
        writeULEB128(abbrevOut, 0) // terminate attr form
        return code
    }

    // -- String table --

    private class StringTable {
        private val strings = mutableMapOf<String, Int>()
        private val data = ByteArrayOutputStream()

        init {
            // First byte is null (offset 0 = empty string)
            data.write(0)
        }

        fun intern(s: String): Int {
            return strings.getOrPut(s) {
                val offset = data.size()
                data.write(s.toByteArray(Charsets.UTF_8))
                data.write(0)
                offset
            }
        }

        fun toByteArray(): ByteArray = data.toByteArray()
    }

    // -- Writing helpers --

    private fun writeStrp(s: String) {
        val offset = strTable.intern(s)
        writeU32(infoOut, offset)
    }

    private fun writeRef4(offset: Int) {
        writeU32(infoOut, offset)
    }

    private fun writeAddr(addr: Long) {
        if (addrSize == 8) {
            writeU64(infoOut, addr)
        } else {
            writeU32(infoOut, addr.toInt())
        }
    }

    companion object {
        /**
         * Generate DWARF 4 sections from a [DebugInfo] model.
         *
         * @param debugInfo the debug info model to serialize
         * @param addrSize address size in bytes (4 or 8, default 8)
         */
        @JvmStatic
        @JvmOverloads
        fun write(debugInfo: DebugInfo, addrSize: Int = 8): DwarfSections {
            return DwarfWriter(debugInfo, addrSize).generate()
        }

        // -- Binary writing helpers --

        private fun writeU16(out: ByteArrayOutputStream, value: Int) {
            out.write(value and 0xFF)
            out.write((value shr 8) and 0xFF)
        }

        private fun writeU32(out: ByteArrayOutputStream, value: Int) {
            out.write(value and 0xFF)
            out.write((value shr 8) and 0xFF)
            out.write((value shr 16) and 0xFF)
            out.write((value shr 24) and 0xFF)
        }

        private fun writeU64(out: ByteArrayOutputStream, value: Long) {
            writeU32(out, value.toInt())
            writeU32(out, (value shr 32).toInt())
        }

        private fun writeULEB128(out: ByteArrayOutputStream, value: Int) {
            var v = value
            do {
                var b = v and 0x7F
                v = v ushr 7
                if (v != 0) b = b or 0x80
                out.write(b)
            } while (v != 0)
        }

        private fun writeSLEB128(out: ByteArrayOutputStream, value: Long) {
            var v = value
            var more = true
            while (more) {
                val b = (v and 0x7F).toInt()
                v = v shr 7
                more = !(((v == 0L) && (b and 0x40 == 0)) || ((v == -1L) && (b and 0x40 != 0)))
                out.write(if (more) b or 0x80 else b)
            }
        }

        private fun patchU32(out: ByteArrayOutputStream, offset: Int, value: Int) {
            val buf = out.toByteArray()
            buf[offset] = (value and 0xFF).toByte()
            buf[offset + 1] = ((value shr 8) and 0xFF).toByte()
            buf[offset + 2] = ((value shr 16) and 0xFF).toByte()
            buf[offset + 3] = ((value shr 24) and 0xFF).toByte()
            // Rebuild — ByteArrayOutputStream doesn't support random writes
            out.reset()
            out.write(buf)
        }

        private fun mapLanguageCode(lang: SourceLanguage): Int = when (lang) {
            SourceLanguage.C -> DwarfLanguage.C.code
            SourceLanguage.C_PLUS_PLUS -> DwarfLanguage.C_PLUS_PLUS.code
            SourceLanguage.JAVA -> DwarfLanguage.JAVA.code
            SourceLanguage.KOTLIN -> DwarfLanguage.KOTLIN.code
            SourceLanguage.RUST -> DwarfLanguage.RUST.code
            SourceLanguage.SWIFT -> DwarfLanguage.SWIFT.code
            SourceLanguage.GO -> DwarfLanguage.GO.code
            SourceLanguage.PYTHON -> DwarfLanguage.PYTHON.code
            SourceLanguage.D -> DwarfLanguage.D.code
            SourceLanguage.FORTRAN -> DwarfLanguage.FORTRAN90.code
            SourceLanguage.ADA -> DwarfLanguage.ADA95.code
            SourceLanguage.PASCAL -> DwarfLanguage.PASCAL83.code
            SourceLanguage.ZIG -> DwarfLanguage.ZIG.code
            else -> 0
        }

        private fun mapEncodingCode(enc: DwarfEncoding): Int = when (enc) {
            DwarfEncoding.ADDRESS -> DwarfTypeEncoding.ADDRESS.code
            DwarfEncoding.BOOLEAN -> DwarfTypeEncoding.BOOLEAN.code
            DwarfEncoding.FLOAT -> DwarfTypeEncoding.FLOAT.code
            DwarfEncoding.SIGNED -> DwarfTypeEncoding.SIGNED.code
            DwarfEncoding.UNSIGNED -> DwarfTypeEncoding.UNSIGNED.code
            DwarfEncoding.SIGNED_CHAR -> DwarfTypeEncoding.SIGNED_CHAR.code
            DwarfEncoding.UNSIGNED_CHAR -> DwarfTypeEncoding.UNSIGNED_CHAR.code
            DwarfEncoding.UTF -> DwarfTypeEncoding.UTF.code
        }
    }
}
