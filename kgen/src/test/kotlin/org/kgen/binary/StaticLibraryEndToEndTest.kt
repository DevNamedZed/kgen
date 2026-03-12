package org.kgen.binary

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.binary.ar.*
import org.kgen.binary.elf.ElfMachine
import org.kgen.binary.elf.ElfObjectFileReader
import org.kgen.binary.elf.ElfObjectWriter

/**
 * End-to-end tests: compile object files from ObjectFile models, package them
 * into .a archives using ArchiveWriter, then read back and verify symbols and
 * sections are accessible.
 */
class StaticLibraryEndToEndTest {

    private val elfReader = ElfObjectFileReader()

    private fun makeX86Object(
        symbolName: String,
        code: ByteArray = byteArrayOf(0x55, 0x48, 0x89.toByte(), 0xE5.toByte(), 0x5D, 0xC3.toByte()),
    ): ObjectFile = ObjectFile(
        format = ObjectFormat.ELF,
        arch = Architecture(ArchType.X86_64),
        sections = listOf(Section(".text", SectionKind.TEXT, code, align = 16)),
        symbols = listOf(Symbol(symbolName, value = 0, size = code.size.toLong(),
            section = ".text", binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)),
        relocations = emptyList(),
    )

    private fun makeArm64Object(
        symbolName: String,
        code: ByteArray = byteArrayOf(0xC0.toByte(), 0x03, 0x5F, 0xD6.toByte()), // ret
    ): ObjectFile = ObjectFile(
        format = ObjectFormat.ELF,
        arch = Architecture(ArchType.AARCH64),
        sections = listOf(Section(".text", SectionKind.TEXT, code, align = 4)),
        symbols = listOf(Symbol(symbolName, value = 0, size = code.size.toLong(),
            section = ".text", binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)),
        relocations = emptyList(),
    )

    @Test
    fun `compile single function to object then archive then read back and verify symbol`() {
        val obj = makeX86Object("my_function")
        val objBytes = ElfObjectWriter().write(obj)

        // Package into an archive
        val archive = ArchiveWriter().write(
            members = listOf(ArchiveMember("my_function.o", data = objBytes)),
            symbols = listOf(ArchiveSymbol("my_function", memberOffset = 0)),
        )

        // Read back the archive
        val parsed = ArchiveReader.read(archive)
        assertEquals(1, parsed.members.size)
        assertEquals("my_function.o", parsed.members[0].name)
        assertEquals(1, parsed.symbols.size)
        assertEquals("my_function", parsed.symbols[0].name)

        // Extract the member and verify it's a valid ELF object with the symbol
        val memberData = parsed.members[0].data
        val readBack = elfReader.read(memberData)
        val sym = readBack.symbols.firstOrNull { it.name == "my_function" }
        assertNotNull(sym, "Should find 'my_function' symbol in extracted object")
        assertEquals(SymbolKind.FUNCTION, sym!!.kind)
        assertEquals(SymbolBinding.GLOBAL, sym.binding)
    }

    @Test
    fun `multiple object files in one archive with all symbols accessible`() {
        val addObj = makeX86Object("add_numbers")
        val mulObj = makeX86Object("multiply_numbers")
        val divObj = makeX86Object("divide_numbers")

        val addBytes = ElfObjectWriter().write(addObj)
        val mulBytes = ElfObjectWriter().write(mulObj)
        val divBytes = ElfObjectWriter().write(divObj)

        val archive = ArchiveWriter().write(
            members = listOf(
                ArchiveMember("add.o", data = addBytes),
                ArchiveMember("mul.o", data = mulBytes),
                ArchiveMember("div.o", data = divBytes),
            ),
            symbols = listOf(
                ArchiveSymbol("add_numbers", memberOffset = 0),
                ArchiveSymbol("multiply_numbers", memberOffset = 1),
                ArchiveSymbol("divide_numbers", memberOffset = 2),
            ),
        )

        val parsed = ArchiveReader.read(archive)
        assertEquals(3, parsed.members.size)
        assertEquals(3, parsed.symbols.size)

        // Verify each member name
        assertEquals("add.o", parsed.members[0].name)
        assertEquals("mul.o", parsed.members[1].name)
        assertEquals("div.o", parsed.members[2].name)

        // Verify symbol names
        val symbolNames = parsed.symbols.map { it.name }.toSet()
        assertTrue("add_numbers" in symbolNames)
        assertTrue("multiply_numbers" in symbolNames)
        assertTrue("divide_numbers" in symbolNames)

        // Verify each extracted object has the expected symbol
        for (member in parsed.members) {
            val readBack = elfReader.read(member.data)
            assertTrue(readBack.symbols.any { it.kind == SymbolKind.FUNCTION && it.binding == SymbolBinding.GLOBAL },
                "Member '${member.name}' should contain a global function symbol")
        }
    }

    @Test
    fun `object with data section and code section in archive`() {
        val code = byteArrayOf(0x55, 0x48, 0x89.toByte(), 0xE5.toByte(), 0x5D, 0xC3.toByte())
        val data = "Hello, kgen!\u0000".toByteArray(Charsets.US_ASCII)

        val obj = ObjectFile(
            format = ObjectFormat.ELF,
            arch = Architecture(ArchType.X86_64),
            sections = listOf(
                Section(".text", SectionKind.TEXT, code, align = 16),
                Section(".rodata", SectionKind.RODATA, data, align = 4),
            ),
            symbols = listOf(
                Symbol("greet", value = 0, size = code.size.toLong(),
                    section = ".text", binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("greeting_msg", value = 0, size = data.size.toLong(),
                    section = ".rodata", binding = SymbolBinding.GLOBAL, kind = SymbolKind.DATA),
            ),
            relocations = emptyList(),
        )

        val objBytes = ElfObjectWriter().write(obj)
        val archive = ArchiveWriter().write(
            members = listOf(ArchiveMember("greet.o", data = objBytes)),
            symbols = listOf(
                ArchiveSymbol("greet", memberOffset = 0),
                ArchiveSymbol("greeting_msg", memberOffset = 0),
            ),
        )

        val parsed = ArchiveReader.read(archive)
        assertEquals(1, parsed.members.size)
        assertEquals(2, parsed.symbols.size)

        val readBack = elfReader.read(parsed.members[0].data)

        val textSection = readBack.sections.firstOrNull { it.name == ".text" }
        assertNotNull(textSection, "Should have .text section")
        assertArrayEquals(code, textSection!!.data)

        val rodataSection = readBack.sections.firstOrNull { it.name == ".rodata" }
        assertNotNull(rodataSection, "Should have .rodata section")
        assertArrayEquals(data, rodataSection!!.data)

        val greetSym = readBack.symbols.firstOrNull { it.name == "greet" }
        assertNotNull(greetSym)
        assertEquals(SymbolKind.FUNCTION, greetSym!!.kind)

        val msgSym = readBack.symbols.firstOrNull { it.name == "greeting_msg" }
        assertNotNull(msgSym)
        assertEquals(SymbolKind.DATA, msgSym!!.kind)
    }

    @Test
    fun `separate archives for x86 and ARM64 objects`() {
        val x86Obj = makeX86Object("compute")
        val arm64Obj = makeArm64Object("compute")

        val x86Bytes = ElfObjectWriter().write(x86Obj)
        val arm64Bytes = ElfObjectWriter(ElfMachine.AARCH64.code).write(arm64Obj)

        // Create separate archives per architecture
        val x86Archive = ArchiveWriter().write(
            members = listOf(ArchiveMember("compute_x86.o", data = x86Bytes)),
            symbols = listOf(ArchiveSymbol("compute", memberOffset = 0)),
        )
        val arm64Archive = ArchiveWriter().write(
            members = listOf(ArchiveMember("compute_arm64.o", data = arm64Bytes)),
            symbols = listOf(ArchiveSymbol("compute", memberOffset = 0)),
        )

        // Read x86 archive
        val parsedX86 = ArchiveReader.read(x86Archive)
        assertEquals(1, parsedX86.members.size)
        val x86ReadBack = elfReader.read(parsedX86.members[0].data)
        assertEquals(ArchType.X86_64, x86ReadBack.arch.arch)
        assertNotNull(x86ReadBack.symbols.firstOrNull { it.name == "compute" })

        // Read ARM64 archive
        val parsedArm64 = ArchiveReader.read(arm64Archive)
        assertEquals(1, parsedArm64.members.size)
        val arm64ReadBack = elfReader.read(parsedArm64.members[0].data)
        assertEquals(ArchType.AARCH64, arm64ReadBack.arch.arch)
        assertNotNull(arm64ReadBack.symbols.firstOrNull { it.name == "compute" })
    }

    @Test
    fun `read archive and extract specific member then verify code section exists`() {
        val helperCode = byteArrayOf(0xB8.toByte(), 0x2A, 0x00, 0x00, 0x00, 0xC3.toByte()) // mov eax, 42; ret
        val mainCode = byteArrayOf(0x55, 0x48, 0x89.toByte(), 0xE5.toByte(),
            0xE8.toByte(), 0x00, 0x00, 0x00, 0x00, 0x5D, 0xC3.toByte()) // push rbp; mov rbp,rsp; call rel32; pop rbp; ret

        val helperObj = ObjectFile(
            format = ObjectFormat.ELF,
            arch = Architecture(ArchType.X86_64),
            sections = listOf(Section(".text", SectionKind.TEXT, helperCode, align = 16)),
            symbols = listOf(Symbol("get_answer", value = 0, size = helperCode.size.toLong(),
                section = ".text", binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)),
            relocations = emptyList(),
        )
        val mainObj = ObjectFile(
            format = ObjectFormat.ELF,
            arch = Architecture(ArchType.X86_64),
            sections = listOf(Section(".text", SectionKind.TEXT, mainCode, align = 16)),
            symbols = listOf(Symbol("main", value = 0, size = mainCode.size.toLong(),
                section = ".text", binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)),
            relocations = emptyList(),
        )

        val helperBytes = ElfObjectWriter().write(helperObj)
        val mainBytes = ElfObjectWriter().write(mainObj)

        val archive = ArchiveWriter().write(
            members = listOf(
                ArchiveMember("helper.o", data = helperBytes),
                ArchiveMember("main.o", data = mainBytes),
            ),
            symbols = listOf(
                ArchiveSymbol("get_answer", memberOffset = 0),
                ArchiveSymbol("main", memberOffset = 1),
            ),
        )

        val parsed = ArchiveReader.read(archive)

        // Use memberByName to extract specific member
        val helperMember = parsed.memberByName("helper.o")
        assertNotNull(helperMember, "Should find helper.o by name")

        val extractedObj = elfReader.read(helperMember!!.data)
        val textSection = extractedObj.sections.firstOrNull { it.name == ".text" }
        assertNotNull(textSection, "Extracted object should have .text section")
        assertTrue(textSection!!.data.isNotEmpty(), "Code section should not be empty")
        assertArrayEquals(helperCode, textSection.data)

        // Verify the extracted object's symbol
        val sym = extractedObj.symbols.firstOrNull { it.name == "get_answer" }
        assertNotNull(sym, "Should find 'get_answer' symbol")
        assertEquals(SymbolKind.FUNCTION, sym!!.kind)
    }

    @Test
    fun `archive with symbol table enables symbol lookup across members`() {
        val fooObj = makeX86Object("foo")
        val barObj = makeX86Object("bar")
        val bazObj = makeX86Object("baz")

        val fooBytes = ElfObjectWriter().write(fooObj)
        val barBytes = ElfObjectWriter().write(barObj)
        val bazBytes = ElfObjectWriter().write(bazObj)

        val members = listOf(
            ArchiveMember("foo.o", data = fooBytes),
            ArchiveMember("bar.o", data = barBytes),
            ArchiveMember("baz.o", data = bazBytes),
        )
        val symbols = listOf(
            ArchiveSymbol("foo", memberOffset = 0),
            ArchiveSymbol("bar", memberOffset = 1),
            ArchiveSymbol("baz", memberOffset = 2),
        )

        val archiveBytes = ArchiveWriter().write(members, symbols)
        val parsed = ArchiveReader.read(archiveBytes)

        // Verify symbol table was round-tripped
        assertEquals(3, parsed.symbols.size)

        // Use membersContainingSymbol to locate specific objects
        val barMembers = parsed.membersContainingSymbol("bar")
        assertEquals(1, barMembers.size)
        assertEquals("bar.o", barMembers[0].name)

        // Verify the object from symbol lookup contains the right function
        val barReadBack = elfReader.read(barMembers[0].data)
        val barSym = barReadBack.symbols.firstOrNull { it.name == "bar" }
        assertNotNull(barSym)
        assertEquals(SymbolBinding.GLOBAL, barSym!!.binding)

        // Missing symbol should return empty
        val missing = parsed.membersContainingSymbol("nonexistent")
        assertTrue(missing.isEmpty())
    }
}
