package org.kgen.binary.pe.pdb

import org.kgen.binary.*
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Emits CodeView debug sections (`.debug$S` and `.debug$T`) for COFF object files.
 *
 * Converts a [DebugInfo] model into CodeView-format type and symbol records
 * that link tools (MSVC linker, WinDbg) can consume.
 *
 * ```java
 * var debug = new DebugInfo(List.of(compileUnit));
 * var result = CodeViewEmitter.emit(debug, "module.obj");
 * // result.debugS, result.debugT
 * ```
 */
class CodeViewEmitter private constructor(
    private val debugInfo: DebugInfo,
    private val objectName: String,
) {
    private var nextTypeIndex = 0x1000 // CodeView user types start at 0x1000
    private val typeRecords = mutableListOf<CvTypeRecord>()
    private val symbolRecords = mutableListOf<CvSymbolRecord>()
    private val typeIndexMap = mutableMapOf<DebugType, Int>()

    data class CodeViewSections(
        val debugS: ByteArray,
        val debugT: ByteArray,
    ) {
        fun toSections(): List<Section> = buildList {
            if (debugS.isNotEmpty()) add(Section(".debug\$S", SectionKind.CUSTOM, debugS))
            if (debugT.isNotEmpty()) add(Section(".debug\$T", SectionKind.CUSTOM, debugT))
        }
    }

    private fun emit(): CodeViewSections {
        for (cu in debugInfo.compileUnits) {
            emitCompileUnit(cu)
        }
        return CodeViewSections(
            debugS = buildDebugS(),
            debugT = buildDebugT(),
        )
    }

    private fun emitCompileUnit(cu: CompileUnit) {
        // S_OBJNAME
        symbolRecords.add(CodeViewBuilder.objname(objectName))

        // S_COMPILE3
        val lang = mapLanguage(cu.language)
        symbolRecords.add(CodeViewBuilder.compile3(lang, CvCpuType.X64_AMD64, cu.producer))

        // Emit types
        for (type in cu.types) {
            emitType(type)
        }

        // Emit subprograms
        for (sub in cu.subprograms) {
            emitSubprogram(sub)
        }

        // Emit global variables
        for (variable in cu.variables) {
            emitGlobalVariable(variable)
        }
    }

    // -- Type emission --

    private fun emitType(type: DebugType): Int {
        typeIndexMap[type]?.let { return it }

        val index = when (type) {
            is DebugType.Base -> emitBaseType(type)
            is DebugType.Pointer -> {
                val pointeeIdx = emitType(type.pointee)
                addType(CodeViewBuilder.pointer(pointeeIdx))
            }
            is DebugType.Reference -> {
                val refIdx = emitType(type.referent)
                addType(CodeViewBuilder.pointer(refIdx, mode = CvPointerMode.LVALUE_REFERENCE))
            }
            is DebugType.Const -> {
                val baseIdx = emitType(type.baseType)
                addType(CodeViewBuilder.modifier(baseIdx, isConst = true))
            }
            is DebugType.Volatile -> {
                val baseIdx = emitType(type.baseType)
                addType(CodeViewBuilder.modifier(baseIdx, isVolatile = true))
            }
            is DebugType.Typedef -> {
                val baseIdx = emitType(type.baseType)
                // Emit UDT symbol for the typedef
                symbolRecords.add(CodeViewBuilder.udt(type.name, baseIdx))
                baseIdx
            }
            is DebugType.Array -> {
                val elemIdx = emitType(type.element)
                val byteSize = (type.sizeInBits / 8).toInt()
                addType(CodeViewBuilder.array(elemIdx, CvBuiltinType.T_INT4.code, byteSize))
            }
            is DebugType.Composite -> emitCompositeType(type)
            is DebugType.Enum -> emitEnumType(type)
            is DebugType.Subroutine -> emitSubroutineType(type)
        }
        typeIndexMap[type] = index
        return index
    }

    private fun emitBaseType(type: DebugType.Base): Int {
        // Map to CodeView built-in types (no need for type records)
        return when {
            type.name == "void" -> CvBuiltinType.T_VOID.code
            type.encoding == DwarfEncoding.BOOLEAN && type.sizeInBits <= 8 -> CvBuiltinType.T_BOOL08.code
            type.encoding == DwarfEncoding.SIGNED && type.sizeInBits == 8L -> CvBuiltinType.T_CHAR.code
            type.encoding == DwarfEncoding.SIGNED && type.sizeInBits == 16L -> CvBuiltinType.T_SHORT.code
            type.encoding == DwarfEncoding.SIGNED && type.sizeInBits == 32L -> CvBuiltinType.T_INT4.code
            type.encoding == DwarfEncoding.SIGNED && type.sizeInBits == 64L -> CvBuiltinType.T_INT8.code
            type.encoding == DwarfEncoding.UNSIGNED && type.sizeInBits == 8L -> CvBuiltinType.T_UCHAR.code
            type.encoding == DwarfEncoding.UNSIGNED && type.sizeInBits == 16L -> CvBuiltinType.T_USHORT.code
            type.encoding == DwarfEncoding.UNSIGNED && type.sizeInBits == 32L -> CvBuiltinType.T_UINT4.code
            type.encoding == DwarfEncoding.UNSIGNED && type.sizeInBits == 64L -> CvBuiltinType.T_UINT8.code
            type.encoding == DwarfEncoding.FLOAT && type.sizeInBits == 32L -> CvBuiltinType.T_REAL32.code
            type.encoding == DwarfEncoding.FLOAT && type.sizeInBits == 64L -> CvBuiltinType.T_REAL64.code
            type.encoding == DwarfEncoding.SIGNED_CHAR -> CvBuiltinType.T_RCHAR.code
            type.encoding == DwarfEncoding.UNSIGNED_CHAR -> CvBuiltinType.T_UCHAR.code
            type.encoding == DwarfEncoding.UTF -> CvBuiltinType.T_WCHAR.code
            else -> CvBuiltinType.T_INT4.code
        }
    }

    private fun emitCompositeType(type: DebugType.Composite): Int {
        val isClass = type.tag == CompositeTag.CLASS || type.tag == CompositeTag.INTERFACE
        val isUnion = type.tag == CompositeTag.UNION
        val byteSize = (type.sizeInBits / 8).toInt()

        return if (isUnion) {
            addType(CodeViewBuilder.union(type.name, size = byteSize, memberCount = type.members.size))
        } else {
            addType(CodeViewBuilder.structure(
                type.name, size = byteSize, memberCount = type.members.size, isClass = isClass
            ))
        }
    }

    private fun emitEnumType(type: DebugType.Enum): Int {
        val baseIdx = emitType(type.baseType)
        return addType(CodeViewBuilder.enum_(type.name, baseIdx, memberCount = type.enumerators.size))
    }

    private fun emitSubroutineType(type: DebugType.Subroutine): Int {
        val retIdx = type.returnType?.let { emitType(it) } ?: CvBuiltinType.T_VOID.code
        val argTypeIndices = type.paramTypes.map { emitType(it) }
        val argListIdx = addType(CodeViewBuilder.argList(argTypeIndices))
        return addType(CodeViewBuilder.procedure(retIdx, paramCount = type.paramTypes.size, argListIndex = argListIdx))
    }

    // -- Symbol emission --

    private fun emitSubprogram(sub: DebugSubprogram) {
        val retTypeIdx = sub.returnType?.let { emitType(it) } ?: CvBuiltinType.T_VOID.code
        val paramTypeIndices = sub.params.map { emitType(it.type) }
        val argListIdx = addType(CodeViewBuilder.argList(paramTypeIndices))
        val procTypeIdx = addType(CodeViewBuilder.procedure(retTypeIdx, paramCount = sub.params.size, argListIndex = argListIdx))

        val codeSize = (sub.highPC - sub.lowPC).toInt()
        symbolRecords.add(CodeViewBuilder.gproc32(
            name = sub.linkageName ?: sub.name,
            typeIndex = procTypeIdx,
            offset = sub.lowPC.toInt(),
            section = 1,
            codeSize = codeSize,
        ))

        // Parameters
        for (param in sub.params) {
            val paramTypeIdx = emitType(param.type)
            when (val loc = param.location) {
                is DebugLocation.FrameOffset -> {
                    symbolRecords.add(CodeViewBuilder.regrel32(param.name, paramTypeIdx, loc.offset, CV_REG_RSP))
                }
                else -> {} // skip params without frame locations
            }
        }

        // Local variables
        for (local in sub.localVariables) {
            val localTypeIdx = emitType(local.type)
            when (val loc = local.location) {
                is DebugLocation.FrameOffset -> {
                    symbolRecords.add(CodeViewBuilder.regrel32(local.name, localTypeIdx, loc.offset, CV_REG_RSP))
                }
                else -> {}
            }
        }

        symbolRecords.add(CodeViewBuilder.end())
    }

    private fun emitGlobalVariable(variable: DebugVariable) {
        val typeIdx = emitType(variable.type)
        when (val loc = variable.location) {
            is DebugLocation.Address -> {
                symbolRecords.add(CodeViewBuilder.gdata32(variable.name, typeIdx, loc.address.toInt(), 2))
            }
            else -> {
                symbolRecords.add(CodeViewBuilder.gdata32(variable.name, typeIdx, 0, 2))
            }
        }
    }

    // -- Section builders --

    private fun buildDebugT(): ByteArray {
        if (typeRecords.isEmpty()) return ByteArray(0)
        val out = ByteArrayOutputStream()
        // CV signature
        writeU32LE(out, CV_SIGNATURE_C13)
        for (rec in typeRecords) {
            val totalLen = 2 + rec.data.size // kind(2) + data
            writeU16LE(out, totalLen)
            writeU16LE(out, rec.kind)
            out.write(rec.data)
            // Pad to 4-byte alignment
            val pad = (4 - (totalLen + 2) % 4) % 4
            for (i in 0 until pad) out.write(0)
        }
        return out.toByteArray()
    }

    private fun buildDebugS(): ByteArray {
        if (symbolRecords.isEmpty()) return ByteArray(0)
        val out = ByteArrayOutputStream()
        // CV signature
        writeU32LE(out, CV_SIGNATURE_C13)

        // Symbol subsection (DEBUG_S_SYMBOLS = 0xF1)
        val symBytes = ByteArrayOutputStream()
        for (rec in symbolRecords) {
            val recLen = 2 + rec.data.size // kind(2) + data
            writeU16LE(symBytes, recLen)
            writeU16LE(symBytes, rec.kind)
            symBytes.write(rec.data)
            // Pad to 4-byte alignment
            val pad = (4 - (recLen + 2) % 4) % 4
            for (i in 0 until pad) symBytes.write(0)
        }
        val symData = symBytes.toByteArray()

        writeU32LE(out, 0xF1) // DEBUG_S_SYMBOLS
        writeU32LE(out, symData.size)
        out.write(symData)

        return out.toByteArray()
    }

    // -- Helpers --

    private fun addType(record: CvTypeRecord): Int {
        val index = nextTypeIndex++
        typeRecords.add(record)
        return index
    }

    companion object {
        private const val CV_SIGNATURE_C13 = 4
        private const val CV_REG_RSP = 335 // x86-64 RSP register

        @JvmStatic
        @JvmOverloads
        fun emit(debugInfo: DebugInfo, objectName: String = "module.obj"): CodeViewSections {
            return CodeViewEmitter(debugInfo, objectName).emit()
        }

        private fun mapLanguage(lang: SourceLanguage): CvSourceLanguage = when (lang) {
            SourceLanguage.C -> CvSourceLanguage.C
            SourceLanguage.C_PLUS_PLUS -> CvSourceLanguage.CPP
            SourceLanguage.JAVA -> CvSourceLanguage.JAVA
            SourceLanguage.RUST -> CvSourceLanguage.RUST
            SourceLanguage.KOTLIN -> CvSourceLanguage.KOTLIN
            else -> CvSourceLanguage.C
        }

        private fun writeU16LE(out: ByteArrayOutputStream, value: Int) {
            out.write(value and 0xFF)
            out.write((value shr 8) and 0xFF)
        }

        private fun writeU32LE(out: ByteArrayOutputStream, value: Int) {
            out.write(value and 0xFF)
            out.write((value shr 8) and 0xFF)
            out.write((value shr 16) and 0xFF)
            out.write((value shr 24) and 0xFF)
        }
    }
}
