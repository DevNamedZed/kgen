package org.kgen.binary.dwarf

import org.kgen.binary.*

/**
 * Parses DWARF debug information from ELF/PE .debug_* sections into a [DebugInfo] model.
 *
 * Supports DWARF 4 and 5. Reads `.debug_abbrev`, `.debug_info`, and `.debug_str` sections
 * to extract compile units, types, variables, and subprograms.
 *
 * ```java
 * var obj = ObjectFile.readElf(elfBytes);
 * var debug = DwarfReader.read(obj);
 * for (var cu : debug.compileUnits()) {
 *     System.out.println(cu.name() + ": " + cu.types().size() + " types");
 * }
 * ```
 */
class DwarfReader private constructor(
    private val debugInfo: ByteArray,
    private val debugAbbrev: ByteArray,
    private val debugStr: ByteArray?,
    private val debugLineStr: ByteArray?,
    private val is64Bit: Boolean,
) {
    private val compileUnits = mutableListOf<ParsedCompileUnit>()

    fun read(): DebugInfo {
        parseCompileUnits()
        val format = if (compileUnits.any { it.dwarfVersion >= 5 }) DebugFormat.DWARF5 else DebugFormat.DWARF4
        return DebugInfo(
            compileUnits = compileUnits.map { buildCompileUnit(it) },
            format = format,
        )
    }

    // -- Abbreviation table parsing --

    private fun parseAbbrevTable(offset: Int): Map<Int, Abbreviation> {
        val abbrevs = mutableMapOf<Int, Abbreviation>()
        var pos = offset
        while (pos < debugAbbrev.size) {
            val (code, codeLen) = readULEB128(debugAbbrev, pos)
            pos += codeLen
            if (code == 0) break

            val (tagCode, tagLen) = readULEB128(debugAbbrev, pos)
            pos += tagLen
            val hasChildren = debugAbbrev[pos].toInt() and 0xFF == 1
            pos++

            val attrs = mutableListOf<AbbrevAttr>()
            while (true) {
                val (attrName, attrNameLen) = readULEB128(debugAbbrev, pos)
                pos += attrNameLen
                val (attrForm, attrFormLen) = readULEB128(debugAbbrev, pos)
                pos += attrFormLen

                if (attrName == 0 && attrForm == 0) break

                var implicitConst = 0L
                if (attrForm == DwarfForm.IMPLICIT_CONST.code) {
                    val (v, l) = readSLEB128(debugAbbrev, pos)
                    implicitConst = v
                    pos += l
                }

                attrs.add(AbbrevAttr(attrName, attrForm, implicitConst))
            }

            abbrevs[code] = Abbreviation(code, tagCode, hasChildren, attrs)
        }
        return abbrevs
    }

    // -- Compile unit parsing --

    private fun parseCompileUnits() {
        var pos = 0
        while (pos < debugInfo.size) {
            val cuStart = pos
            val (unitLength, is64) = readUnitLength(pos)
            pos += if (is64) 12 else 4
            val endPos = pos + unitLength.toInt()

            val version = readU16(debugInfo, pos)
            pos += 2

            val abbrevOffset: Int
            val addrSize: Int

            if (version >= 5) {
                val unitType = debugInfo[pos].toInt() and 0xFF
                pos++
                addrSize = debugInfo[pos].toInt() and 0xFF
                pos++
                abbrevOffset = readU32(debugInfo, pos).toInt()
                pos += 4
            } else {
                abbrevOffset = readU32(debugInfo, pos).toInt()
                pos += 4
                addrSize = debugInfo[pos].toInt() and 0xFF
                pos++
            }

            val abbrevTable = parseAbbrevTable(abbrevOffset)
            val cu = ParsedCompileUnit(version, addrSize, cuStart)

            parseDIETree(pos, endPos, abbrevTable, addrSize, cu, null)
            compileUnits.add(cu)

            pos = endPos
        }
    }

    private fun parseDIETree(
        startPos: Int,
        endPos: Int,
        abbrevTable: Map<Int, Abbreviation>,
        addrSize: Int,
        cu: ParsedCompileUnit,
        parent: ParsedDIE?,
    ): Int {
        var pos = startPos
        while (pos < endPos) {
            val dieOffset = pos
            val (abbrevCode, codeLen) = readULEB128(debugInfo, pos)
            pos += codeLen

            if (abbrevCode == 0) {
                return pos // null entry terminates sibling chain
            }

            val abbrev = abbrevTable[abbrevCode] ?: break
            val tag = DwarfTag.fromCode(abbrev.tagCode)
            val die = ParsedDIE(tag, dieOffset)

            for (attr in abbrev.attrs) {
                val attrEnum = DwarfAttribute.fromCode(attr.nameCode)
                val formEnum = DwarfForm.fromCode(attr.formCode)

                if (formEnum == DwarfForm.IMPLICIT_CONST) {
                    if (attrEnum != null) die.attrs[attrEnum] = attr.implicitConst
                    continue
                }

                val (value, valueLen) = readAttributeValue(pos, formEnum, addrSize)
                if (attrEnum != null && value != null) {
                    die.attrs[attrEnum] = value
                }
                pos += valueLen
            }

            // Register DIE
            when (tag) {
                DwarfTag.COMPILE_UNIT -> {
                    cu.name = die.stringAttr(DwarfAttribute.NAME) ?: ""
                    cu.compDir = die.stringAttr(DwarfAttribute.COMP_DIR) ?: ""
                    cu.producer = die.stringAttr(DwarfAttribute.PRODUCER) ?: ""
                    cu.language = die.longAttr(DwarfAttribute.LANGUAGE)?.toInt() ?: 0
                    cu.lowPC = die.longAttr(DwarfAttribute.LOW_PC) ?: 0
                    cu.highPC = die.longAttr(DwarfAttribute.HIGH_PC) ?: 0
                }
                DwarfTag.BASE_TYPE, DwarfTag.POINTER_TYPE, DwarfTag.REFERENCE_TYPE,
                DwarfTag.RVALUE_REFERENCE_TYPE, DwarfTag.CONST_TYPE, DwarfTag.VOLATILE_TYPE,
                DwarfTag.TYPEDEF, DwarfTag.ARRAY_TYPE, DwarfTag.STRUCTURE_TYPE,
                DwarfTag.CLASS_TYPE, DwarfTag.UNION_TYPE, DwarfTag.ENUMERATION_TYPE,
                DwarfTag.SUBROUTINE_TYPE, DwarfTag.RESTRICT_TYPE -> {
                    cu.typeDIEs[dieOffset] = die
                }
                DwarfTag.SUBPROGRAM -> cu.subprogramDIEs.add(die)
                DwarfTag.VARIABLE -> {
                    if (parent?.tag == DwarfTag.SUBPROGRAM) {
                        // local variable — attach to parent
                    } else {
                        cu.variableDIEs.add(die)
                    }
                }
                DwarfTag.MEMBER -> {
                    parent?.children?.add(die)
                }
                DwarfTag.ENUMERATOR -> {
                    parent?.children?.add(die)
                }
                DwarfTag.FORMAL_PARAMETER -> {
                    parent?.children?.add(die)
                }
                DwarfTag.SUBRANGE_TYPE -> {
                    parent?.children?.add(die)
                }
                DwarfTag.INHERITANCE -> {
                    parent?.children?.add(die)
                }
                else -> {}
            }

            if (abbrev.hasChildren) {
                pos = parseDIETree(pos, endPos, abbrevTable, addrSize, cu, die)
            }
        }
        return pos
    }

    // -- Attribute value reading --

    private fun readAttributeValue(pos: Int, form: DwarfForm?, addrSize: Int): Pair<Any?, Int> {
        if (form == null || pos >= debugInfo.size) return null to 0

        return when (form) {
            DwarfForm.ADDR -> readAddress(pos, addrSize) to addrSize
            DwarfForm.DATA1 -> (debugInfo[pos].toLong() and 0xFF) to 1
            DwarfForm.DATA2 -> readU16(debugInfo, pos).toLong() to 2
            DwarfForm.DATA4 -> readU32(debugInfo, pos) to 4
            DwarfForm.DATA8 -> readU64(debugInfo, pos) to 8
            DwarfForm.SDATA -> {
                val (v, l) = readSLEB128(debugInfo, pos)
                v to l
            }
            DwarfForm.UDATA -> {
                val (v, l) = readULEB128(debugInfo, pos)
                v.toLong() to l
            }
            DwarfForm.STRING -> {
                val end = debugInfo.indexOf(0, pos)
                val s = String(debugInfo, pos, end - pos, Charsets.UTF_8)
                s to (end - pos + 1)
            }
            DwarfForm.STRP -> {
                val offset = readU32(debugInfo, pos).toInt()
                val s = readStringFromTable(debugStr, offset)
                s to 4
            }
            DwarfForm.LINE_STRP -> {
                val offset = readU32(debugInfo, pos).toInt()
                val s = readStringFromTable(debugLineStr, offset)
                s to 4
            }
            DwarfForm.FLAG -> (debugInfo[pos].toInt() and 0xFF != 0) to 1
            DwarfForm.FLAG_PRESENT -> true to 0
            DwarfForm.REF1 -> (debugInfo[pos].toLong() and 0xFF) to 1
            DwarfForm.REF2 -> readU16(debugInfo, pos).toLong() to 2
            DwarfForm.REF4 -> readU32(debugInfo, pos) to 4
            DwarfForm.REF8 -> readU64(debugInfo, pos) to 8
            DwarfForm.REF_UDATA -> {
                val (v, l) = readULEB128(debugInfo, pos)
                v.toLong() to l
            }
            DwarfForm.REF_ADDR -> readAddress(pos, if (is64Bit) 8 else 4) to (if (is64Bit) 8 else 4)
            DwarfForm.SEC_OFFSET -> {
                if (is64Bit) readU64(debugInfo, pos) to 8
                else readU32(debugInfo, pos) to 4
            }
            DwarfForm.EXPRLOC -> {
                val (len, lenSize) = readULEB128(debugInfo, pos)
                val block = debugInfo.copyOfRange(pos + lenSize, pos + lenSize + len)
                block to (lenSize + len)
            }
            DwarfForm.BLOCK1 -> {
                val len = debugInfo[pos].toInt() and 0xFF
                debugInfo.copyOfRange(pos + 1, pos + 1 + len) to (1 + len)
            }
            DwarfForm.BLOCK2 -> {
                val len = readU16(debugInfo, pos)
                debugInfo.copyOfRange(pos + 2, pos + 2 + len) to (2 + len)
            }
            DwarfForm.BLOCK4 -> {
                val len = readU32(debugInfo, pos).toInt()
                debugInfo.copyOfRange(pos + 4, pos + 4 + len) to (4 + len)
            }
            DwarfForm.BLOCK -> {
                val (len, lenSize) = readULEB128(debugInfo, pos)
                debugInfo.copyOfRange(pos + lenSize, pos + lenSize + len) to (lenSize + len)
            }
            DwarfForm.STRX, DwarfForm.ADDRX, DwarfForm.LOCLISTX, DwarfForm.RNGLISTX -> {
                val (v, l) = readULEB128(debugInfo, pos)
                v.toLong() to l
            }
            DwarfForm.STRX1, DwarfForm.ADDRX1 -> (debugInfo[pos].toLong() and 0xFF) to 1
            DwarfForm.STRX2, DwarfForm.ADDRX2 -> readU16(debugInfo, pos).toLong() to 2
            DwarfForm.STRX3, DwarfForm.ADDRX3 -> {
                val b0 = debugInfo[pos].toLong() and 0xFF
                val b1 = debugInfo[pos + 1].toLong() and 0xFF
                val b2 = debugInfo[pos + 2].toLong() and 0xFF
                (b0 or (b1 shl 8) or (b2 shl 16)) to 3
            }
            DwarfForm.STRX4, DwarfForm.ADDRX4 -> readU32(debugInfo, pos) to 4
            DwarfForm.DATA16 -> debugInfo.copyOfRange(pos, pos + 16) to 16
            DwarfForm.REF_SIG8 -> readU64(debugInfo, pos) to 8
            DwarfForm.REF_SUP4 -> readU32(debugInfo, pos) to 4
            DwarfForm.REF_SUP8 -> readU64(debugInfo, pos) to 8
            DwarfForm.STRP_SUP -> readU32(debugInfo, pos) to 4
            DwarfForm.INDIRECT -> {
                val (formCode, formLen) = readULEB128(debugInfo, pos)
                val actualForm = DwarfForm.fromCode(formCode)
                val (v, vl) = readAttributeValue(pos + formLen, actualForm, addrSize)
                v to (formLen + vl)
            }
            DwarfForm.IMPLICIT_CONST -> null to 0 // handled separately
        }
    }

    // -- Type resolution --

    private fun resolveType(cu: ParsedCompileUnit, typeRefOffset: Long?): DebugType? {
        if (typeRefOffset == null) return null
        // REF4 gives CU-relative offset; convert to absolute by adding CU header start
        val absOffset = (typeRefOffset + cu.offset).toInt()
        val die = cu.typeDIEs[absOffset] ?: return null
        return resolveTypeDIE(cu, die)
    }

    private fun resolveTypeDIE(cu: ParsedCompileUnit, die: ParsedDIE): DebugType {
        cu.resolvedTypes[die.offset]?.let { return it }

        // Insert placeholder to break recursive type cycles (e.g., struct with pointer to self)
        val placeholder = DebugType.Base("<recursive>", 0, DwarfEncoding.ADDRESS)
        cu.resolvedTypes[die.offset] = placeholder

        val name = die.stringAttr(DwarfAttribute.NAME) ?: ""
        val byteSize = die.longAttr(DwarfAttribute.BYTE_SIZE) ?: 0
        val sizeInBits = byteSize * 8

        val result: DebugType = when (die.tag) {
            DwarfTag.BASE_TYPE -> {
                val encCode = die.longAttr(DwarfAttribute.ENCODING)?.toInt() ?: 0
                val enc = mapDwarfEncoding(encCode)
                DebugType.Base(name, sizeInBits, enc)
            }
            DwarfTag.POINTER_TYPE -> {
                val pointee = resolveType(cu, die.longAttr(DwarfAttribute.TYPE))
                    ?: DebugType.Base("void", 0, DwarfEncoding.ADDRESS)
                DebugType.Pointer(pointee, sizeInBits)
            }
            DwarfTag.REFERENCE_TYPE, DwarfTag.RVALUE_REFERENCE_TYPE -> {
                val referent = resolveType(cu, die.longAttr(DwarfAttribute.TYPE))
                    ?: DebugType.Base("void", 0, DwarfEncoding.ADDRESS)
                DebugType.Reference(referent, sizeInBits)
            }
            DwarfTag.CONST_TYPE -> {
                val base = resolveType(cu, die.longAttr(DwarfAttribute.TYPE))
                    ?: DebugType.Base("void", 0, DwarfEncoding.ADDRESS)
                DebugType.Const(base)
            }
            DwarfTag.VOLATILE_TYPE -> {
                val base = resolveType(cu, die.longAttr(DwarfAttribute.TYPE))
                    ?: DebugType.Base("void", 0, DwarfEncoding.ADDRESS)
                DebugType.Volatile(base)
            }
            DwarfTag.RESTRICT_TYPE -> {
                resolveType(cu, die.longAttr(DwarfAttribute.TYPE))
                    ?: DebugType.Base("void", 0, DwarfEncoding.ADDRESS)
            }
            DwarfTag.TYPEDEF -> {
                val base = resolveType(cu, die.longAttr(DwarfAttribute.TYPE))
                    ?: DebugType.Base("void", 0, DwarfEncoding.ADDRESS)
                DebugType.Typedef(name, base)
            }
            DwarfTag.ARRAY_TYPE -> {
                val elem = resolveType(cu, die.longAttr(DwarfAttribute.TYPE))
                    ?: DebugType.Base("void", 0, DwarfEncoding.ADDRESS)
                val count = die.children.firstOrNull { it.tag == DwarfTag.SUBRANGE_TYPE }?.let { sub ->
                    val ub = sub.longAttr(DwarfAttribute.UPPER_BOUND)
                    val cnt = sub.longAttr(DwarfAttribute.COUNT)
                    cnt ?: ub?.plus(1) ?: 0
                } ?: 0L
                DebugType.Array(elem, count, sizeInBits)
            }
            DwarfTag.STRUCTURE_TYPE, DwarfTag.CLASS_TYPE, DwarfTag.UNION_TYPE -> {
                val tag = when (die.tag) {
                    DwarfTag.CLASS_TYPE -> CompositeTag.CLASS
                    DwarfTag.UNION_TYPE -> CompositeTag.UNION
                    else -> CompositeTag.STRUCT
                }
                val members = die.children
                    .filter { it.tag == DwarfTag.MEMBER }
                    .map { memberDIE ->
                        val memberName = memberDIE.stringAttr(DwarfAttribute.NAME) ?: ""
                        val memberType = resolveType(cu, memberDIE.longAttr(DwarfAttribute.TYPE))
                            ?: DebugType.Base("?", 0, DwarfEncoding.UNSIGNED)
                        val memberOffset = memberDIE.memberOffset()
                        val access = memberDIE.accessibility()
                        DebugMember(memberName, memberType, memberOffset * 8, memberType.sizeInBits, access)
                    }
                DebugType.Composite(name, sizeInBits, tag, members)
            }
            DwarfTag.ENUMERATION_TYPE -> {
                val base = resolveType(cu, die.longAttr(DwarfAttribute.TYPE))
                    ?: DebugType.Base("int", 32, DwarfEncoding.SIGNED)
                val enumerators = die.children
                    .filter { it.tag == DwarfTag.ENUMERATOR }
                    .map { e ->
                        val eName = e.stringAttr(DwarfAttribute.NAME) ?: ""
                        val eVal = e.longAttr(DwarfAttribute.CONST_VALUE) ?: 0
                        eName to eVal
                    }
                DebugType.Enum(name, sizeInBits, base, enumerators)
            }
            DwarfTag.SUBROUTINE_TYPE -> {
                val retType = resolveType(cu, die.longAttr(DwarfAttribute.TYPE))
                val paramTypes = die.children
                    .filter { it.tag == DwarfTag.FORMAL_PARAMETER }
                    .mapNotNull { p -> resolveType(cu, p.longAttr(DwarfAttribute.TYPE)) }
                DebugType.Subroutine(retType, paramTypes)
            }
            else -> DebugType.Base(name.ifEmpty { "unknown" }, sizeInBits, DwarfEncoding.UNSIGNED)
        }

        cu.resolvedTypes[die.offset] = result
        return result
    }

    // -- Subprogram resolution --

    private fun resolveSubprogram(cu: ParsedCompileUnit, die: ParsedDIE): DebugSubprogram {
        val name = die.stringAttr(DwarfAttribute.NAME) ?: ""
        val linkageName = die.stringAttr(DwarfAttribute.LINKAGE_NAME)
        val file = die.stringAttr(DwarfAttribute.DECL_FILE) ?: cu.name
        val line = die.longAttr(DwarfAttribute.DECL_LINE)?.toInt() ?: 0
        val returnType = resolveType(cu, die.longAttr(DwarfAttribute.TYPE))

        val params = die.children
            .filter { it.tag == DwarfTag.FORMAL_PARAMETER }
            .map { p ->
                val pName = p.stringAttr(DwarfAttribute.NAME) ?: ""
                val pType = resolveType(cu, p.longAttr(DwarfAttribute.TYPE))
                    ?: DebugType.Base("?", 0, DwarfEncoding.UNSIGNED)
                val artificial = p.attrs[DwarfAttribute.ARTIFICIAL]
                DebugVariable(pName, pType, file, line, isParameter = true, isArtificial = artificial == true)
            }

        return DebugSubprogram(
            name = name,
            linkageName = linkageName,
            file = file,
            line = line,
            returnType = returnType,
            params = params,
            lowPC = die.longAttr(DwarfAttribute.LOW_PC) ?: 0,
            highPC = die.longAttr(DwarfAttribute.HIGH_PC) ?: 0,
        )
    }

    // -- Internal models --

    private fun buildCompileUnit(cu: ParsedCompileUnit): CompileUnit {
        val types = cu.typeDIEs.values
            .filter { it.tag in EXPORTED_TYPE_TAGS && it.stringAttr(DwarfAttribute.NAME) != null }
            .map { resolveTypeDIE(cu, it) }

        val subprograms = cu.subprogramDIEs.map { resolveSubprogram(cu, it) }

        val lang = mapLanguage(cu.language)
        return CompileUnit(
            name = cu.name,
            directory = cu.compDir,
            producer = cu.producer,
            language = lang,
            types = types,
            subprograms = subprograms,
            lowPC = cu.lowPC,
            highPC = cu.highPC,
        )
    }

    private class ParsedCompileUnit(val dwarfVersion: Int, val addrSize: Int, val offset: Int) {
        var name = ""
        var compDir = ""
        var producer = ""
        var language = 0
        var lowPC = 0L
        var highPC = 0L
        val typeDIEs = mutableMapOf<Int, ParsedDIE>()
        val subprogramDIEs = mutableListOf<ParsedDIE>()
        val variableDIEs = mutableListOf<ParsedDIE>()
        val resolvedTypes = mutableMapOf<Int, DebugType>()
    }

    private class ParsedDIE(val tag: DwarfTag?, val offset: Int) {
        val attrs = mutableMapOf<DwarfAttribute, Any>()
        val children = mutableListOf<ParsedDIE>()

        fun stringAttr(attr: DwarfAttribute): String? = attrs[attr] as? String

        fun longAttr(attr: DwarfAttribute): Long? {
            val v = attrs[attr] ?: return null
            return when (v) {
                is Long -> v
                is Int -> v.toLong()
                is Boolean -> if (v) 1L else 0L
                else -> null
            }
        }

        fun memberOffset(): Long {
            val v = attrs[DwarfAttribute.DATA_MEMBER_LOCATION] ?: return 0
            return when (v) {
                is Long -> v
                is ByteArray -> {
                    // Simple DW_OP_plus_uconst expression
                    if (v.isNotEmpty() && v[0].toInt() and 0xFF == 0x23) {
                        val (off, _) = readULEB128(v, 1)
                        off.toLong()
                    } else 0
                }
                else -> 0
            }
        }

        fun accessibility(): DebugAccessibility {
            val v = longAttr(DwarfAttribute.ACCESSIBILITY)?.toInt() ?: return DebugAccessibility.PUBLIC
            return when (v) {
                1 -> DebugAccessibility.PUBLIC
                2 -> DebugAccessibility.PROTECTED
                3 -> DebugAccessibility.PRIVATE
                else -> DebugAccessibility.PUBLIC
            }
        }
    }

    private data class Abbreviation(
        val code: Int,
        val tagCode: Int,
        val hasChildren: Boolean,
        val attrs: List<AbbrevAttr>,
    )

    private data class AbbrevAttr(val nameCode: Int, val formCode: Int, val implicitConst: Long = 0)

    // -- Binary helpers --

    private fun readUnitLength(pos: Int): Pair<Long, Boolean> {
        val initial = readU32(debugInfo, pos)
        return if (initial == 0xFFFFFFFFL) {
            readU64(debugInfo, pos + 4) to true
        } else {
            initial to false
        }
    }

    private fun readAddress(pos: Int, addrSize: Int): Long = when (addrSize) {
        4 -> readU32(debugInfo, pos)
        8 -> readU64(debugInfo, pos)
        else -> 0
    }

    private fun readStringFromTable(table: ByteArray?, offset: Int): String {
        if (table == null || offset >= table.size) return ""
        val end = table.indexOf(0, offset)
        val actualEnd = if (end < 0) table.size else end
        return String(table, offset, actualEnd - offset, Charsets.UTF_8)
    }

    companion object {
        private val EXPORTED_TYPE_TAGS = setOf(
            DwarfTag.STRUCTURE_TYPE, DwarfTag.CLASS_TYPE, DwarfTag.UNION_TYPE,
            DwarfTag.ENUMERATION_TYPE, DwarfTag.TYPEDEF,
        )

        @JvmStatic
        fun read(objectFile: ObjectFile): DebugInfo? {
            val infoSection = objectFile.sections.firstOrNull { it.kind == SectionKind.DEBUG_INFO } ?: return null
            val abbrevSection = objectFile.sections.firstOrNull { it.kind == SectionKind.DEBUG_ABBREV } ?: return null
            val strSection = objectFile.sections.firstOrNull { it.kind == SectionKind.DEBUG_STR }
            val lineStrSection = objectFile.sections.firstOrNull { it.kind == SectionKind.DEBUG_LINE_STR }

            val is64 = objectFile.arch.pointerSize == 8

            return DwarfReader(
                debugInfo = infoSection.data,
                debugAbbrev = abbrevSection.data,
                debugStr = strSection?.data,
                debugLineStr = lineStrSection?.data,
                is64Bit = is64,
            ).read()
        }

        @JvmStatic
        fun read(debugInfo: ByteArray, debugAbbrev: ByteArray, debugStr: ByteArray? = null, is64Bit: Boolean = true): DebugInfo {
            return DwarfReader(debugInfo, debugAbbrev, debugStr, null, is64Bit).read()
        }

        private fun mapLanguage(code: Int): SourceLanguage {
            val lang = DwarfLanguage.fromCode(code) ?: return SourceLanguage.CUSTOM
            return when (lang) {
                DwarfLanguage.C89, DwarfLanguage.C, DwarfLanguage.C99, DwarfLanguage.C11, DwarfLanguage.C17 -> SourceLanguage.C
                DwarfLanguage.C_PLUS_PLUS, DwarfLanguage.C_PLUS_PLUS_14, DwarfLanguage.C_PLUS_PLUS_17 -> SourceLanguage.C_PLUS_PLUS
                DwarfLanguage.JAVA -> SourceLanguage.JAVA
                DwarfLanguage.KOTLIN -> SourceLanguage.KOTLIN
                DwarfLanguage.RUST -> SourceLanguage.RUST
                DwarfLanguage.SWIFT -> SourceLanguage.SWIFT
                DwarfLanguage.GO -> SourceLanguage.GO
                DwarfLanguage.PYTHON -> SourceLanguage.PYTHON
                DwarfLanguage.D -> SourceLanguage.D
                DwarfLanguage.ADA83, DwarfLanguage.ADA95 -> SourceLanguage.ADA
                DwarfLanguage.PASCAL83 -> SourceLanguage.PASCAL
                DwarfLanguage.FORTRAN77, DwarfLanguage.FORTRAN90, DwarfLanguage.FORTRAN95,
                DwarfLanguage.FORTRAN03, DwarfLanguage.FORTRAN08 -> SourceLanguage.FORTRAN
                DwarfLanguage.ZIG -> SourceLanguage.ZIG
                else -> SourceLanguage.CUSTOM
            }
        }

        private fun mapDwarfEncoding(code: Int): DwarfEncoding {
            val enc = DwarfTypeEncoding.fromCode(code) ?: return DwarfEncoding.UNSIGNED
            return when (enc) {
                DwarfTypeEncoding.ADDRESS -> DwarfEncoding.ADDRESS
                DwarfTypeEncoding.BOOLEAN -> DwarfEncoding.BOOLEAN
                DwarfTypeEncoding.FLOAT, DwarfTypeEncoding.COMPLEX_FLOAT -> DwarfEncoding.FLOAT
                DwarfTypeEncoding.SIGNED -> DwarfEncoding.SIGNED
                DwarfTypeEncoding.UNSIGNED -> DwarfEncoding.UNSIGNED
                DwarfTypeEncoding.SIGNED_CHAR -> DwarfEncoding.SIGNED_CHAR
                DwarfTypeEncoding.UNSIGNED_CHAR -> DwarfEncoding.UNSIGNED_CHAR
                DwarfTypeEncoding.UTF -> DwarfEncoding.UTF
            }
        }

        private fun readU16(data: ByteArray, pos: Int): Int =
            (data[pos].toInt() and 0xFF) or ((data[pos + 1].toInt() and 0xFF) shl 8)

        private fun readU32(data: ByteArray, pos: Int): Long =
            (data[pos].toLong() and 0xFF) or
            ((data[pos + 1].toLong() and 0xFF) shl 8) or
            ((data[pos + 2].toLong() and 0xFF) shl 16) or
            ((data[pos + 3].toLong() and 0xFF) shl 24)

        private fun readU64(data: ByteArray, pos: Int): Long =
            readU32(data, pos) or (readU32(data, pos + 4) shl 32)

        private fun readULEB128(data: ByteArray, pos: Int): Pair<Int, Int> {
            var result = 0
            var shift = 0
            var i = pos
            while (i < data.size) {
                val b = data[i].toInt() and 0xFF
                result = result or ((b and 0x7F) shl shift)
                i++
                if (b and 0x80 == 0) break
                shift += 7
            }
            return result to (i - pos)
        }

        private fun readSLEB128(data: ByteArray, pos: Int): Pair<Long, Int> {
            var result = 0L
            var shift = 0
            var i = pos
            var b: Int
            do {
                b = data[i].toInt() and 0xFF
                result = result or ((b.toLong() and 0x7F) shl shift)
                shift += 7
                i++
            } while (b and 0x80 != 0 && i < data.size)
            if (shift < 64 && b and 0x40 != 0) {
                result = result or ((-1L) shl shift)
            }
            return result to (i - pos)
        }

        private fun ByteArray.indexOf(value: Byte, fromIndex: Int = 0): Int {
            for (i in fromIndex until size) {
                if (this[i] == value) return i
            }
            return size
        }
    }
}
