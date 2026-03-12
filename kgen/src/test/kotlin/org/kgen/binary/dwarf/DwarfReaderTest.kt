package org.kgen.binary.dwarf

import org.kgen.binary.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class DwarfReaderTest {

    private fun buildDwarf4(
        abbrevBuilder: DwarfAbbrevBuilder.() -> Unit,
        infoBuilder: DwarfInfoBuilder.() -> Unit,
        strings: List<String> = emptyList(),
    ): Triple<ByteArray, ByteArray, ByteArray?> {
        val abbrev = DwarfAbbrevBuilder().apply(abbrevBuilder).build()
        val strTable = if (strings.isNotEmpty()) buildStringTable(strings) else null
        val info = DwarfInfoBuilder(abbrev.size, strTable, strings).apply(infoBuilder).build()
        return Triple(info, abbrev, strTable)
    }

    private fun buildStringTable(strings: List<String>): ByteArray {
        val buf = mutableListOf<Byte>()
        buf.add(0) // offset 0 = empty string
        for (s in strings) {
            for (c in s.toByteArray(Charsets.UTF_8)) buf.add(c)
            buf.add(0)
        }
        return buf.toByteArray()
    }

    @Test
    fun emptyDebugInfoReturnsNull() {
        val obj = ObjectFile(
            ObjectFormat.ELF, Architecture.X86_64_LINUX,
            emptyList(), emptyList(), emptyList(),
        )
        assertNull(DwarfReader.read(obj))
    }

    @Test
    fun parseBaseType() {
        val (info, abbrev, str) = buildDwarf4(
            abbrevBuilder = {
                abbreviation(1, DwarfTag.COMPILE_UNIT.code, hasChildren = true) {
                    attr(DwarfAttribute.NAME.code, DwarfForm.STRING.code)
                    attr(DwarfAttribute.LANGUAGE.code, DwarfForm.DATA1.code)
                }
                abbreviation(2, DwarfTag.BASE_TYPE.code, hasChildren = false) {
                    attr(DwarfAttribute.NAME.code, DwarfForm.STRING.code)
                    attr(DwarfAttribute.BYTE_SIZE.code, DwarfForm.DATA1.code)
                    attr(DwarfAttribute.ENCODING.code, DwarfForm.DATA1.code)
                }
            },
            infoBuilder = {
                compileUnit(version = 4, abbrevOffset = 0, addrSize = 8) {
                    die(1) {
                        string("test.c")
                        data1(DwarfLanguage.C.code)
                    }
                    die(2) {
                        string("int")
                        data1(4)
                        data1(DwarfTypeEncoding.SIGNED.code)
                    }
                    nullEntry()
                }
            },
        )

        val debug = DwarfReader.read(info, abbrev, str)
        assertEquals(1, debug.compileUnits.size)
        val cu = debug.compileUnits[0]
        assertEquals("test.c", cu.name)
        assertEquals(SourceLanguage.C, cu.language)
    }

    @Test
    fun parseStructType() {
        val (info, abbrev, str) = buildDwarf4(
            abbrevBuilder = {
                abbreviation(1, DwarfTag.COMPILE_UNIT.code, hasChildren = true) {
                    attr(DwarfAttribute.NAME.code, DwarfForm.STRING.code)
                    attr(DwarfAttribute.LANGUAGE.code, DwarfForm.DATA1.code)
                }
                abbreviation(2, DwarfTag.BASE_TYPE.code, hasChildren = false) {
                    attr(DwarfAttribute.NAME.code, DwarfForm.STRING.code)
                    attr(DwarfAttribute.BYTE_SIZE.code, DwarfForm.DATA1.code)
                    attr(DwarfAttribute.ENCODING.code, DwarfForm.DATA1.code)
                }
                abbreviation(3, DwarfTag.STRUCTURE_TYPE.code, hasChildren = true) {
                    attr(DwarfAttribute.NAME.code, DwarfForm.STRING.code)
                    attr(DwarfAttribute.BYTE_SIZE.code, DwarfForm.DATA1.code)
                }
                abbreviation(4, DwarfTag.MEMBER.code, hasChildren = false) {
                    attr(DwarfAttribute.NAME.code, DwarfForm.STRING.code)
                    attr(DwarfAttribute.TYPE.code, DwarfForm.REF4.code)
                    attr(DwarfAttribute.DATA_MEMBER_LOCATION.code, DwarfForm.DATA1.code)
                }
            },
            infoBuilder = {
                compileUnit(version = 4, abbrevOffset = 0, addrSize = 8) {
                    die(1) {
                        string("point.c")
                        data1(DwarfLanguage.C.code)
                    }
                    // base type "int" at known offset
                    val intOffset = currentOffset()
                    die(2) {
                        string("int")
                        data1(4)
                        data1(DwarfTypeEncoding.SIGNED.code)
                    }
                    // struct "Point"
                    die(3) {
                        string("Point")
                        data1(8)
                    }
                    // member "x"
                    die(4) {
                        string("x")
                        ref4(intOffset)
                        data1(0)
                    }
                    // member "y"
                    die(4) {
                        string("y")
                        ref4(intOffset)
                        data1(4)
                    }
                    nullEntry() // end struct children
                    nullEntry() // end CU children
                }
            },
        )

        val debug = DwarfReader.read(info, abbrev, str)
        assertEquals(1, debug.compileUnits.size)
        val cu = debug.compileUnits[0]
        assertTrue(cu.types.isNotEmpty())

        val structType = cu.types.filterIsInstance<DebugType.Composite>().firstOrNull { it.name == "Point" }
        assertNotNull(structType)
        assertEquals(64, structType!!.sizeInBits)
        assertEquals(CompositeTag.STRUCT, structType.tag)
        assertEquals(2, structType.members.size)
        assertEquals("x", structType.members[0].name)
        assertEquals("y", structType.members[1].name)
        assertEquals(0, structType.members[0].offsetInBits)
        assertEquals(32, structType.members[1].offsetInBits)
    }

    @Test
    fun parseEnumType() {
        val (info, abbrev, str) = buildDwarf4(
            abbrevBuilder = {
                abbreviation(1, DwarfTag.COMPILE_UNIT.code, hasChildren = true) {
                    attr(DwarfAttribute.NAME.code, DwarfForm.STRING.code)
                    attr(DwarfAttribute.LANGUAGE.code, DwarfForm.DATA1.code)
                }
                abbreviation(2, DwarfTag.BASE_TYPE.code, hasChildren = false) {
                    attr(DwarfAttribute.NAME.code, DwarfForm.STRING.code)
                    attr(DwarfAttribute.BYTE_SIZE.code, DwarfForm.DATA1.code)
                    attr(DwarfAttribute.ENCODING.code, DwarfForm.DATA1.code)
                }
                abbreviation(3, DwarfTag.ENUMERATION_TYPE.code, hasChildren = true) {
                    attr(DwarfAttribute.NAME.code, DwarfForm.STRING.code)
                    attr(DwarfAttribute.BYTE_SIZE.code, DwarfForm.DATA1.code)
                    attr(DwarfAttribute.TYPE.code, DwarfForm.REF4.code)
                }
                abbreviation(4, DwarfTag.ENUMERATOR.code, hasChildren = false) {
                    attr(DwarfAttribute.NAME.code, DwarfForm.STRING.code)
                    attr(DwarfAttribute.CONST_VALUE.code, DwarfForm.DATA1.code)
                }
            },
            infoBuilder = {
                compileUnit(version = 4, abbrevOffset = 0, addrSize = 8) {
                    die(1) {
                        string("color.c")
                        data1(DwarfLanguage.C.code)
                    }
                    val intOffset = currentOffset()
                    die(2) {
                        string("unsigned int")
                        data1(4)
                        data1(DwarfTypeEncoding.UNSIGNED.code)
                    }
                    die(3) {
                        string("Color")
                        data1(4)
                        ref4(intOffset)
                    }
                    die(4) { string("RED"); data1(0) }
                    die(4) { string("GREEN"); data1(1) }
                    die(4) { string("BLUE"); data1(2) }
                    nullEntry() // end enum children
                    nullEntry() // end CU children
                }
            },
        )

        val debug = DwarfReader.read(info, abbrev, str)
        val cu = debug.compileUnits[0]
        val enumType = cu.types.filterIsInstance<DebugType.Enum>().firstOrNull()
        assertNotNull(enumType)
        assertEquals("Color", enumType!!.name)
        assertEquals(3, enumType.enumerators.size)
        assertEquals("RED", enumType.enumerators[0].first)
        assertEquals(0L, enumType.enumerators[0].second)
        assertEquals("GREEN", enumType.enumerators[1].first)
        assertEquals("BLUE", enumType.enumerators[2].first)
    }

    @Test
    fun parseSubprogram() {
        val (info, abbrev, str) = buildDwarf4(
            abbrevBuilder = {
                abbreviation(1, DwarfTag.COMPILE_UNIT.code, hasChildren = true) {
                    attr(DwarfAttribute.NAME.code, DwarfForm.STRING.code)
                    attr(DwarfAttribute.LANGUAGE.code, DwarfForm.DATA1.code)
                }
                abbreviation(2, DwarfTag.BASE_TYPE.code, hasChildren = false) {
                    attr(DwarfAttribute.NAME.code, DwarfForm.STRING.code)
                    attr(DwarfAttribute.BYTE_SIZE.code, DwarfForm.DATA1.code)
                    attr(DwarfAttribute.ENCODING.code, DwarfForm.DATA1.code)
                }
                abbreviation(3, DwarfTag.SUBPROGRAM.code, hasChildren = true) {
                    attr(DwarfAttribute.NAME.code, DwarfForm.STRING.code)
                    attr(DwarfAttribute.TYPE.code, DwarfForm.REF4.code)
                }
                abbreviation(4, DwarfTag.FORMAL_PARAMETER.code, hasChildren = false) {
                    attr(DwarfAttribute.NAME.code, DwarfForm.STRING.code)
                    attr(DwarfAttribute.TYPE.code, DwarfForm.REF4.code)
                }
            },
            infoBuilder = {
                compileUnit(version = 4, abbrevOffset = 0, addrSize = 8) {
                    die(1) {
                        string("func.c")
                        data1(DwarfLanguage.C.code)
                    }
                    val intOffset = currentOffset()
                    die(2) {
                        string("int")
                        data1(4)
                        data1(DwarfTypeEncoding.SIGNED.code)
                    }
                    die(3) {
                        string("add")
                        ref4(intOffset)
                    }
                    die(4) {
                        string("a")
                        ref4(intOffset)
                    }
                    die(4) {
                        string("b")
                        ref4(intOffset)
                    }
                    nullEntry() // end subprogram children
                    nullEntry() // end CU children
                }
            },
        )

        val debug = DwarfReader.read(info, abbrev, str)
        val cu = debug.compileUnits[0]
        assertEquals(1, cu.subprograms.size)
        val func = cu.subprograms[0]
        assertEquals("add", func.name)
        assertNotNull(func.returnType)
        assertEquals("int", func.returnType!!.name)
        assertEquals(2, func.params.size)
        assertEquals("a", func.params[0].name)
        assertEquals("b", func.params[1].name)
    }

    @Test
    fun typeMapperMapsStruct() {
        val cu = CompileUnit(
            name = "test.c",
            directory = "/tmp",
            producer = "gcc",
            language = SourceLanguage.C,
            types = listOf(
                DebugType.Composite(
                    name = "MyStruct",
                    sizeInBits = 64,
                    tag = CompositeTag.STRUCT,
                    members = listOf(
                        DebugMember("x", DebugType.Base("int", 32, DwarfEncoding.SIGNED), 0),
                        DebugMember("y", DebugType.Base("int", 32, DwarfEncoding.SIGNED), 32),
                    ),
                ),
            ),
        )
        val debugInfo = DebugInfo(listOf(cu))
        val types = DwarfTypeMapper.map(debugInfo)
        assertEquals(1, types.size)
        assertEquals("MyStruct", types[0].name())
        assertEquals(org.kgen.reflect.TypeKind.STRUCT, types[0].kind())
        assertEquals(2, types[0].fields().size)
        assertEquals("x", types[0].fields()[0].name())
        assertEquals("y", types[0].fields()[1].name())
    }

    @Test
    fun typeMapperMapsEnum() {
        val cu = CompileUnit(
            name = "test.c",
            directory = "/tmp",
            producer = "gcc",
            language = SourceLanguage.C,
            types = listOf(
                DebugType.Enum(
                    name = "Status",
                    sizeInBits = 32,
                    baseType = DebugType.Base("int", 32, DwarfEncoding.SIGNED),
                    enumerators = listOf("OK" to 0L, "ERROR" to 1L),
                ),
            ),
        )
        val debugInfo = DebugInfo(listOf(cu))
        val types = DwarfTypeMapper.map(debugInfo)
        assertEquals(1, types.size)
        assertEquals("Status", types[0].name())
        assertEquals(org.kgen.reflect.TypeKind.ENUM, types[0].kind())
        assertEquals(2, types[0].fields().size)
    }

    @Test
    fun dwarf4Format() {
        val (info, abbrev, _) = buildDwarf4(
            abbrevBuilder = {
                abbreviation(1, DwarfTag.COMPILE_UNIT.code, hasChildren = false) {
                    attr(DwarfAttribute.NAME.code, DwarfForm.STRING.code)
                    attr(DwarfAttribute.LANGUAGE.code, DwarfForm.DATA1.code)
                }
            },
            infoBuilder = {
                compileUnit(version = 4, abbrevOffset = 0, addrSize = 8) {
                    die(1) {
                        string("test.c")
                        data1(DwarfLanguage.C.code)
                    }
                }
            },
        )
        val debug = DwarfReader.read(info, abbrev)
        assertEquals(DebugFormat.DWARF4, debug.format)
    }

    @Test
    fun objectFileIntegration() {
        val (info, abbrev, _) = buildDwarf4(
            abbrevBuilder = {
                abbreviation(1, DwarfTag.COMPILE_UNIT.code, hasChildren = false) {
                    attr(DwarfAttribute.NAME.code, DwarfForm.STRING.code)
                    attr(DwarfAttribute.LANGUAGE.code, DwarfForm.DATA1.code)
                }
            },
            infoBuilder = {
                compileUnit(version = 4, abbrevOffset = 0, addrSize = 8) {
                    die(1) {
                        string("test.c")
                        data1(DwarfLanguage.C.code)
                    }
                }
            },
        )

        val obj = ObjectFile(
            ObjectFormat.ELF, Architecture.X86_64_LINUX,
            sections = listOf(
                Section(".debug_info", SectionKind.DEBUG_INFO, info),
                Section(".debug_abbrev", SectionKind.DEBUG_ABBREV, abbrev),
            ),
            symbols = emptyList(),
            relocations = emptyList(),
        )

        val debug = DwarfReader.read(obj)
        assertNotNull(debug)
        assertEquals(1, debug!!.compileUnits.size)
        assertEquals("test.c", debug.compileUnits[0].name)
    }

    // -- Test helpers for building synthetic DWARF data --

    class DwarfAbbrevBuilder {
        private val buf = mutableListOf<Byte>()

        fun abbreviation(code: Int, tag: Int, hasChildren: Boolean, block: AbbrevAttrBuilder.() -> Unit) {
            writeULEB128(code)
            writeULEB128(tag)
            buf.add(if (hasChildren) 1 else 0)
            AbbrevAttrBuilder(buf).apply(block)
            writeULEB128(0) // attr name terminator
            writeULEB128(0) // attr form terminator
        }

        fun build(): ByteArray {
            buf.add(0) // end of table
            return buf.toByteArray()
        }

        private fun writeULEB128(value: Int) {
            var v = value
            do {
                var b = v and 0x7F
                v = v ushr 7
                if (v != 0) b = b or 0x80
                buf.add(b.toByte())
            } while (v != 0)
        }
    }

    class AbbrevAttrBuilder(private val buf: MutableList<Byte>) {
        fun attr(name: Int, form: Int) {
            writeULEB128(name)
            writeULEB128(form)
        }

        private fun writeULEB128(value: Int) {
            var v = value
            do {
                var b = v and 0x7F
                v = v ushr 7
                if (v != 0) b = b or 0x80
                buf.add(b.toByte())
            } while (v != 0)
        }
    }

    class DwarfInfoBuilder(
        private val abbrevSize: Int,
        private val strTable: ByteArray?,
        private val strings: List<String>,
    ) {
        private val buf = mutableListOf<Byte>()
        private var cuHeaderStart = 0

        fun compileUnit(version: Int, abbrevOffset: Int, addrSize: Int, block: DwarfInfoBuilder.() -> Unit) {
            // Reserve 4 bytes for unit length (filled later)
            cuHeaderStart = buf.size
            writeU32(0) // placeholder for unit_length
            writeU16(version)
            // DWARF 4: abbrev_offset (4), addr_size (1)
            writeU32(abbrevOffset)
            buf.add(addrSize.toByte())

            block()

            // Patch unit length (length of content after the initial 4-byte length field)
            val unitLen = buf.size - cuHeaderStart - 4
            buf[cuHeaderStart] = (unitLen and 0xFF).toByte()
            buf[cuHeaderStart + 1] = ((unitLen shr 8) and 0xFF).toByte()
            buf[cuHeaderStart + 2] = ((unitLen shr 16) and 0xFF).toByte()
            buf[cuHeaderStart + 3] = ((unitLen shr 24) and 0xFF).toByte()
        }

        fun currentOffset(): Int = buf.size - cuHeaderStart // offset from CU header start (includes unit_length)

        fun die(abbrevCode: Int, block: DieBuilder.() -> Unit) {
            writeULEB128(abbrevCode)
            DieBuilder(buf, strTable, strings).apply(block)
        }

        fun nullEntry() {
            buf.add(0)
        }

        fun build(): ByteArray = buf.toByteArray()

        private fun writeU16(v: Int) {
            buf.add((v and 0xFF).toByte())
            buf.add(((v shr 8) and 0xFF).toByte())
        }

        private fun writeU32(v: Int) {
            buf.add((v and 0xFF).toByte())
            buf.add(((v shr 8) and 0xFF).toByte())
            buf.add(((v shr 16) and 0xFF).toByte())
            buf.add(((v shr 24) and 0xFF).toByte())
        }

        private fun writeULEB128(value: Int) {
            var v = value
            do {
                var b = v and 0x7F
                v = v ushr 7
                if (v != 0) b = b or 0x80
                buf.add(b.toByte())
            } while (v != 0)
        }
    }

    class DieBuilder(
        private val buf: MutableList<Byte>,
        private val strTable: ByteArray?,
        private val strings: List<String>,
    ) {
        fun string(s: String) {
            for (c in s.toByteArray(Charsets.UTF_8)) buf.add(c)
            buf.add(0)
        }

        fun data1(v: Int) {
            buf.add((v and 0xFF).toByte())
        }

        fun ref4(offset: Int) {
            buf.add((offset and 0xFF).toByte())
            buf.add(((offset shr 8) and 0xFF).toByte())
            buf.add(((offset shr 16) and 0xFF).toByte())
            buf.add(((offset shr 24) and 0xFF).toByte())
        }
    }
}
