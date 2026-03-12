package org.kgen.binary.dwarf

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.kgen.binary.*

class DwarfWriterTest {

    private fun roundTrip(debugInfo: DebugInfo, addrSize: Int = 8): DebugInfo {
        val sections = DwarfWriter.write(debugInfo, addrSize)
        return DwarfReader.read(sections.debugInfo, sections.debugAbbrev, sections.debugStr, is64Bit = addrSize == 8)
    }

    @Test
    fun `empty debug info produces valid sections`() {
        val result = DwarfWriter.write(DebugInfo())
        assertNotNull(result.debugInfo)
        assertNotNull(result.debugAbbrev)
        assertNotNull(result.debugStr)
    }

    @Test
    fun `minimal compile unit round-trips`() {
        val original = DebugInfo(listOf(CompileUnit(
            name = "test.c",
            directory = "/home/user",
            producer = "kgen 1.0",
            language = SourceLanguage.C,
        )))
        val parsed = roundTrip(original)
        assertEquals(1, parsed.compileUnits.size)
        val cu = parsed.compileUnits[0]
        assertEquals("test.c", cu.name)
        assertEquals("/home/user", cu.directory)
        assertEquals("kgen 1.0", cu.producer)
        assertEquals(SourceLanguage.C, cu.language)
    }

    @Test
    fun `compile unit language codes round-trip`() {
        for (lang in listOf(SourceLanguage.C, SourceLanguage.C_PLUS_PLUS, SourceLanguage.JAVA,
                            SourceLanguage.KOTLIN, SourceLanguage.RUST, SourceLanguage.GO)) {
            val original = DebugInfo(listOf(CompileUnit(
                name = "test", directory = "", producer = "kgen", language = lang,
            )))
            val parsed = roundTrip(original)
            assertEquals(lang, parsed.compileUnits[0].language, "Language $lang failed round-trip")
        }
    }

    @Test
    fun `base type round-trips via typedef`() {
        val intType = DebugType.Base("int", 32, DwarfEncoding.SIGNED)
        val typedef = DebugType.Typedef("myint", intType)
        val original = DebugInfo(listOf(CompileUnit(
            name = "test.c", directory = "", producer = "kgen", language = SourceLanguage.C,
            types = listOf(intType, typedef),
        )))
        val parsed = roundTrip(original)
        val typedefs = parsed.compileUnits[0].types.filterIsInstance<DebugType.Typedef>()
        assertTrue(typedefs.isNotEmpty(), "Expected typedef")
        val base = typedefs.first().baseType as? DebugType.Base
        assertNotNull(base)
        assertEquals("int", base!!.name)
        assertEquals(32, base.sizeInBits)
        assertEquals(DwarfEncoding.SIGNED, base.encoding)
    }

    @Test
    fun `multiple base types round-trip via struct members`() {
        val intType = DebugType.Base("int", 32, DwarfEncoding.SIGNED)
        val charType = DebugType.Base("char", 8, DwarfEncoding.SIGNED_CHAR)
        val doubleType = DebugType.Base("double", 64, DwarfEncoding.FLOAT)
        val structType = DebugType.Composite(
            name = "Multi", sizeInBits = 104, tag = CompositeTag.STRUCT,
            members = listOf(
                DebugMember("i", intType, offsetInBits = 0),
                DebugMember("c", charType, offsetInBits = 32),
                DebugMember("d", doubleType, offsetInBits = 40),
            ),
        )
        val original = DebugInfo(listOf(CompileUnit(
            name = "test.c", directory = "", producer = "kgen", language = SourceLanguage.C,
            types = listOf(intType, charType, doubleType, structType),
        )))
        val parsed = roundTrip(original)
        val composites = parsed.compileUnits[0].types.filterIsInstance<DebugType.Composite>()
        assertTrue(composites.isNotEmpty(), "Expected composite type")
        val multi = composites.first { it.name == "Multi" }
        assertEquals(3, multi.members.size)
        val memberTypes = multi.members.map { (it.type as DebugType.Base).name }
        assertEquals(listOf("int", "char", "double"), memberTypes)
    }

    @Test
    fun `composite struct round-trips`() {
        val intType = DebugType.Base("int", 32, DwarfEncoding.SIGNED)
        val structType = DebugType.Composite(
            name = "Point", sizeInBits = 64, tag = CompositeTag.STRUCT,
            members = listOf(
                DebugMember("x", intType, offsetInBits = 0),
                DebugMember("y", intType, offsetInBits = 32),
            )
        )
        val original = DebugInfo(listOf(CompileUnit(
            name = "test.c", directory = "", producer = "kgen", language = SourceLanguage.C,
            types = listOf(intType, structType),
        )))
        val parsed = roundTrip(original)
        val composites = parsed.compileUnits[0].types.filterIsInstance<DebugType.Composite>()
        assertTrue(composites.isNotEmpty(), "Expected composite types")
        val point = composites.first { it.name == "Point" }
        assertEquals(64, point.sizeInBits)
        assertEquals(CompositeTag.STRUCT, point.tag)
        assertEquals(2, point.members.size)
        assertEquals("x", point.members[0].name)
        assertEquals("y", point.members[1].name)
    }

    @Test
    fun `enum type round-trips`() {
        val intType = DebugType.Base("int", 32, DwarfEncoding.SIGNED)
        val enumType = DebugType.Enum(
            name = "Color", sizeInBits = 32, baseType = intType,
            enumerators = listOf("RED" to 0L, "GREEN" to 1L, "BLUE" to 2L),
        )
        val original = DebugInfo(listOf(CompileUnit(
            name = "test.c", directory = "", producer = "kgen", language = SourceLanguage.C,
            types = listOf(intType, enumType),
        )))
        val parsed = roundTrip(original)
        val enums = parsed.compileUnits[0].types.filterIsInstance<DebugType.Enum>()
        assertTrue(enums.isNotEmpty(), "Expected enum types")
        val color = enums.first { it.name == "Color" }
        assertEquals(3, color.enumerators.size)
        assertEquals("RED", color.enumerators[0].first)
        assertEquals(0L, color.enumerators[0].second)
        assertEquals("BLUE", color.enumerators[2].first)
        assertEquals(2L, color.enumerators[2].second)
    }

    @Test
    fun `typedef round-trips`() {
        val intType = DebugType.Base("int", 32, DwarfEncoding.SIGNED)
        val typedef = DebugType.Typedef("size_t", intType)
        val original = DebugInfo(listOf(CompileUnit(
            name = "test.c", directory = "", producer = "kgen", language = SourceLanguage.C,
            types = listOf(intType, typedef),
        )))
        val parsed = roundTrip(original)
        val typedefs = parsed.compileUnits[0].types.filterIsInstance<DebugType.Typedef>()
        assertTrue(typedefs.isNotEmpty(), "Expected typedef types")
        assertEquals("size_t", typedefs.first().name)
    }

    @Test
    fun `subprogram round-trips`() {
        val intType = DebugType.Base("int", 32, DwarfEncoding.SIGNED)
        val sub = DebugSubprogram(
            name = "add",
            linkageName = "_Z3addii",
            file = "test.c",
            line = 5,
            returnType = intType,
            params = listOf(
                DebugVariable("a", intType, isParameter = true),
                DebugVariable("b", intType, isParameter = true),
            ),
            lowPC = 0x1000,
            highPC = 0x1040,
        )
        val original = DebugInfo(listOf(CompileUnit(
            name = "test.c", directory = "", producer = "kgen", language = SourceLanguage.C,
            types = listOf(intType),
            subprograms = listOf(sub),
        )))
        val parsed = roundTrip(original)
        val subs = parsed.compileUnits[0].subprograms
        assertEquals(1, subs.size)
        assertEquals("add", subs[0].name)
        assertEquals("_Z3addii", subs[0].linkageName)
        assertEquals(5, subs[0].line)
        assertEquals(0x1000L, subs[0].lowPC)
        assertEquals(0x1040L, subs[0].highPC)
        assertEquals(2, subs[0].params.size)
        assertEquals("a", subs[0].params[0].name)
        assertEquals("b", subs[0].params[1].name)
    }

    @Test
    fun `toSections produces correct section kinds`() {
        val sections = DwarfWriter.write(DebugInfo(listOf(CompileUnit(
            name = "test.c", directory = "", producer = "kgen", language = SourceLanguage.C,
            lineInfo = listOf(LineEntry(0, "test.c", 1)),
        ))))
        val sectionList = sections.toSections()
        assertTrue(sectionList.any { it.kind == SectionKind.DEBUG_INFO })
        assertTrue(sectionList.any { it.kind == SectionKind.DEBUG_ABBREV })
        assertTrue(sectionList.any { it.kind == SectionKind.DEBUG_STR })
        assertTrue(sectionList.any { it.kind == SectionKind.DEBUG_LINE })
    }

    @Test
    fun `line info produces non-empty debug_line section`() {
        val sections = DwarfWriter.write(DebugInfo(listOf(CompileUnit(
            name = "test.c", directory = "", producer = "kgen", language = SourceLanguage.C,
            lineInfo = listOf(
                LineEntry(0x0, "test.c", 1),
                LineEntry(0x10, "test.c", 2),
                LineEntry(0x20, "test.c", 5),
            ),
        ))))
        assertTrue(sections.debugLine.isNotEmpty(), "Expected non-empty .debug_line")
    }

    @Test
    fun `32-bit address size round-trips`() {
        val original = DebugInfo(listOf(CompileUnit(
            name = "test.c", directory = "", producer = "kgen", language = SourceLanguage.C,
            lowPC = 0x8000,
            highPC = 0x8100,
        )))
        val sections = DwarfWriter.write(original, addrSize = 4)
        val parsed = DwarfReader.read(sections.debugInfo, sections.debugAbbrev, sections.debugStr, is64Bit = false)
        assertEquals(1, parsed.compileUnits.size)
        assertEquals("test.c", parsed.compileUnits[0].name)
    }

    @Test
    fun `string table deduplicates strings`() {
        val original = DebugInfo(listOf(CompileUnit(
            name = "same", directory = "same", producer = "same", language = SourceLanguage.C,
        )))
        val sections = DwarfWriter.write(original)
        // The string "same" should appear only once in .debug_str
        val str = String(sections.debugStr, Charsets.UTF_8)
        val count = str.windowed(4).count { it == "same" }
        assertEquals(1, count, "Expected 'same' to appear exactly once in .debug_str")
    }

    @Test
    fun `multiple compile units`() {
        val original = DebugInfo(listOf(
            CompileUnit(name = "a.c", directory = "/src", producer = "kgen", language = SourceLanguage.C),
            CompileUnit(name = "b.c", directory = "/src", producer = "kgen", language = SourceLanguage.C),
        ))
        val parsed = roundTrip(original)
        assertEquals(2, parsed.compileUnits.size)
        assertEquals("a.c", parsed.compileUnits[0].name)
        assertEquals("b.c", parsed.compileUnits[1].name)
    }

    @Test
    fun `subprogram without return type`() {
        val sub = DebugSubprogram(
            name = "init", file = "test.c", line = 1, returnType = null,
            params = emptyList(), lowPC = 0, highPC = 0x20,
        )
        val original = DebugInfo(listOf(CompileUnit(
            name = "test.c", directory = "", producer = "kgen", language = SourceLanguage.C,
            subprograms = listOf(sub),
        )))
        val parsed = roundTrip(original)
        assertEquals("init", parsed.compileUnits[0].subprograms[0].name)
    }

    @Test
    fun `compile unit with lowPC and highPC`() {
        val original = DebugInfo(listOf(CompileUnit(
            name = "test.c", directory = "", producer = "kgen", language = SourceLanguage.C,
            lowPC = 0x401000, highPC = 0x402000,
        )))
        val parsed = roundTrip(original)
        assertEquals(0x401000L, parsed.compileUnits[0].lowPC)
        assertEquals(0x402000L, parsed.compileUnits[0].highPC)
    }
}
