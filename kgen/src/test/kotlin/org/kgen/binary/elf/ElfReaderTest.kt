package org.kgen.binary.elf

import org.kgen.binary.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ElfReaderTest {

    private val reader = ElfObjectFileReader()

    private fun writeObj(
        sections: List<Section> = emptyList(),
        symbols: List<Symbol> = emptyList(),
        relocations: List<Relocation> = emptyList(),
        machine: Int = ElfMachine.X86_64.code,
    ): ByteArray = ElfObjectWriter(machine).write(ObjectFile(
        format = ObjectFormat.ELF,
        arch = Architecture(ArchType.X86_64),
        sections = sections,
        symbols = symbols,
        relocations = relocations,
    ))

    // Magic / header validation

    @Test
    fun `canRead returns true for valid ELF64`() {
        val bytes = writeObj(sections = listOf(
            Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1)
        ))
        assertTrue(reader.canRead(bytes))
    }

    @Test
    fun `canRead returns false for non-ELF`() {
        assertFalse(reader.canRead(byteArrayOf(0, 0, 0, 0)))
        assertFalse(reader.canRead(byteArrayOf(0x7f, 'E'.code.toByte(), 'L'.code.toByte())))
        assertFalse(reader.canRead(ByteArray(0)))
    }

    @Test
    fun `canRead returns true for ELF32`() {
        val bytes = buildMinimalElf32(
            machine = ElfMachine.I386.code,
            textData = byteArrayOf(0xC3.toByte()),
        )
        assertTrue(ElfReader.canRead(bytes))
    }

    @Test
    fun `reads ELF format`() {
        val bytes = writeObj(sections = listOf(
            Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1)
        ))
        val obj = reader.read(bytes)
        assertEquals(ObjectFormat.ELF, obj.format)
    }

    // Architecture

    @Test
    fun `reads x86-64 architecture`() {
        val bytes = writeObj(sections = listOf(
            Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1)
        ))
        val obj = reader.read(bytes)
        assertEquals(ArchType.X86_64, obj.arch.arch)
    }

    @Test
    fun `reads aarch64 architecture`() {
        val bytes = writeObj(
            sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC0.toByte(), 0x03, 0x5F, 0xD6.toByte()), align = 4)),
            machine = ElfMachine.AARCH64.code,
        )
        val obj = reader.read(bytes)
        assertEquals(ArchType.AARCH64, obj.arch.arch)
    }

    // Object flags

    @Test
    fun `relocatable object has RELOCATABLE flag`() {
        val bytes = writeObj(sections = listOf(
            Section(".text", SectionKind.TEXT, byteArrayOf(0xCC.toByte()), align = 1)
        ))
        val obj = reader.read(bytes)
        assertTrue(ObjectFlag.RELOCATABLE in obj.metadata.flags)
    }

    // Sections

    @Test
    fun `reads text section`() {
        val code = byteArrayOf(0x55, 0x48, 0x89.toByte(), 0xE5.toByte(), 0xC3.toByte())
        val bytes = writeObj(sections = listOf(
            Section(".text", SectionKind.TEXT, code, align = 16)
        ))
        val obj = reader.read(bytes)
        val text = obj.sections.first { it.name == ".text" }
        assertEquals(SectionKind.TEXT, text.kind)
        assertArrayEquals(code, text.data)
        assertTrue(SectionFlag.ALLOC in text.flags)
        assertTrue(SectionFlag.EXEC in text.flags)
    }

    @Test
    fun `reads data section`() {
        val data = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        val bytes = writeObj(sections = listOf(
            Section(".data", SectionKind.DATA, data, align = 8)
        ))
        val obj = reader.read(bytes)
        val sec = obj.sections.first { it.name == ".data" }
        assertEquals(SectionKind.DATA, sec.kind)
        assertArrayEquals(data, sec.data)
        assertTrue(SectionFlag.ALLOC in sec.flags)
        assertTrue(SectionFlag.WRITE in sec.flags)
    }

    @Test
    fun `reads rodata section`() {
        val rodata = "Hello, World!\u0000".toByteArray()
        val bytes = writeObj(sections = listOf(
            Section(".rodata", SectionKind.RODATA, rodata, align = 1)
        ))
        val obj = reader.read(bytes)
        val sec = obj.sections.first { it.name == ".rodata" }
        assertEquals(SectionKind.RODATA, sec.kind)
        assertArrayEquals(rodata, sec.data)
    }

    @Test
    fun `reads bss section with correct size`() {
        val bytes = writeObj(sections = listOf(
            Section(".bss", SectionKind.BSS, ByteArray(512), align = 16)
        ))
        val obj = reader.read(bytes)
        val bss = obj.sections.first { it.name == ".bss" }
        assertEquals(SectionKind.BSS, bss.kind)
        assertEquals(512, bss.data.size)
    }

    @Test
    fun `reads multiple sections`() {
        val code = byteArrayOf(0xC3.toByte())
        val data = byteArrayOf(0x42)
        val bytes = writeObj(sections = listOf(
            Section(".text", SectionKind.TEXT, code, align = 16),
            Section(".data", SectionKind.DATA, data, align = 8),
            Section(".rodata", SectionKind.RODATA, byteArrayOf(0x01), align = 1),
            Section(".bss", SectionKind.BSS, ByteArray(256), align = 16),
        ))
        val obj = reader.read(bytes)

        assertNotNull(obj.sections.firstOrNull { it.name == ".text" })
        assertNotNull(obj.sections.firstOrNull { it.name == ".data" })
        assertNotNull(obj.sections.firstOrNull { it.name == ".rodata" })
        assertNotNull(obj.sections.firstOrNull { it.name == ".bss" })
    }

    @Test
    fun `preserves section alignment`() {
        val bytes = writeObj(sections = listOf(
            Section(".text", SectionKind.TEXT, byteArrayOf(0xCC.toByte()), align = 16),
        ))
        val obj = reader.read(bytes)
        val text = obj.sections.first { it.name == ".text" }
        assertEquals(16, text.align)
    }

    @Test
    fun `includes symtab and strtab sections`() {
        val bytes = writeObj(sections = listOf(
            Section(".text", SectionKind.TEXT, byteArrayOf(0xCC.toByte()), align = 1)
        ))
        val obj = reader.read(bytes)
        assertTrue(obj.sections.any { it.kind == SectionKind.SYMTAB })
        assertTrue(obj.sections.any { it.kind == SectionKind.STRTAB })
    }

    // Symbols

    @Test
    fun `reads global function symbol`() {
        val bytes = writeObj(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(32), align = 16)),
            symbols = listOf(
                Symbol("main", value = 0, size = 32, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)
            ),
        )
        val obj = reader.read(bytes)
        val main = obj.symbols.first { it.name == "main" }
        assertEquals(SymbolBinding.GLOBAL, main.binding)
        assertEquals(SymbolKind.FUNCTION, main.kind)
        assertEquals(0L, main.value)
        assertEquals(32L, main.size)
        assertEquals(".text", main.section)
    }

    @Test
    fun `reads local symbol`() {
        val bytes = writeObj(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(16), align = 16)),
            symbols = listOf(
                Symbol("helper", value = 0, size = 8, section = ".text",
                    binding = SymbolBinding.LOCAL, kind = SymbolKind.FUNCTION)
            ),
        )
        val obj = reader.read(bytes)
        val helper = obj.symbols.first { it.name == "helper" }
        assertEquals(SymbolBinding.LOCAL, helper.binding)
        assertEquals(SymbolKind.FUNCTION, helper.kind)
    }

    @Test
    fun `reads undefined symbol`() {
        val bytes = writeObj(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(16), align = 16)),
            symbols = listOf(
                Symbol("puts", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED)
            ),
        )
        val obj = reader.read(bytes)
        val puts = obj.symbols.first { it.name == "puts" }
        assertEquals(SymbolBinding.GLOBAL, puts.binding)
        assertEquals(SymbolKind.UNDEFINED, puts.kind)
        assertNull(puts.section)
        assertTrue(SymbolFlag.UNDEFINED in puts.flags)
    }

    @Test
    fun `reads multiple symbols with correct binding order`() {
        val bytes = writeObj(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(64), align = 16)),
            symbols = listOf(
                Symbol("main", value = 0, size = 32, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("helper", value = 32, size = 16, section = ".text",
                    binding = SymbolBinding.LOCAL, kind = SymbolKind.FUNCTION),
                Symbol("exit", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ),
        )
        val obj = reader.read(bytes)
        assertEquals(3, obj.symbols.count { it.name.isNotEmpty() && it.kind != SymbolKind.SECTION })
        assertNotNull(obj.symbols.firstOrNull { it.name == "main" })
        assertNotNull(obj.symbols.firstOrNull { it.name == "helper" })
        assertNotNull(obj.symbols.firstOrNull { it.name == "exit" })
    }

    @Test
    fun `reads weak symbol`() {
        val bytes = writeObj(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(16), align = 16)),
            symbols = listOf(
                Symbol("weak_fn", value = 0, size = 8, section = ".text",
                    binding = SymbolBinding.WEAK, kind = SymbolKind.FUNCTION)
            ),
        )
        val obj = reader.read(bytes)
        val sym = obj.symbols.first { it.name == "weak_fn" }
        assertEquals(SymbolBinding.WEAK, sym.binding)
    }

    @Test
    fun `reads symbol with value offset`() {
        val bytes = writeObj(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(64), align = 16)),
            symbols = listOf(
                Symbol("second_fn", value = 32, size = 16, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)
            ),
        )
        val obj = reader.read(bytes)
        val sym = obj.symbols.first { it.name == "second_fn" }
        assertEquals(32L, sym.value)
        assertEquals(16L, sym.size)
    }

    // Relocations

    @Test
    fun `reads PLT32 relocation`() {
        val bytes = writeObj(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(32), align = 16)),
            symbols = listOf(
                Symbol("puts", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED)
            ),
            relocations = listOf(
                Relocation(offset = 5, symbol = "puts", type = RelocationType.X86_64.PLT32,
                    addend = -4, section = ".text")
            ),
        )
        val obj = reader.read(bytes)
        assertEquals(1, obj.relocations.size)
        val rel = obj.relocations[0]
        assertEquals(5L, rel.offset)
        assertEquals("puts", rel.symbol)
        assertEquals(RelocationType.X86_64.PLT32, rel.type)
        assertEquals(-4L, rel.addend)
        assertEquals(".text", rel.section)
    }

    @Test
    fun `reads PC32 relocation`() {
        val bytes = writeObj(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(32), align = 16)),
            symbols = listOf(
                Symbol("printf", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED)
            ),
            relocations = listOf(
                Relocation(offset = 10, symbol = "printf", type = RelocationType.X86_64.PC32,
                    addend = -4, section = ".text")
            ),
        )
        val obj = reader.read(bytes)
        val rel = obj.relocations[0]
        assertEquals(RelocationType.X86_64.PC32, rel.type)
        assertEquals(10L, rel.offset)
    }

    @Test
    fun `reads R_64 relocation`() {
        val bytes = writeObj(
            sections = listOf(
                Section(".text", SectionKind.TEXT, ByteArray(16), align = 16),
                Section(".data", SectionKind.DATA, ByteArray(16), align = 8),
            ),
            symbols = listOf(
                Symbol("global_var", value = 0, size = 8, section = ".data",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)
            ),
            relocations = listOf(
                Relocation(offset = 0, symbol = "global_var", type = RelocationType.X86_64.R_64,
                    addend = 0, section = ".data")
            ),
        )
        val obj = reader.read(bytes)
        val rel = obj.relocations[0]
        assertEquals(RelocationType.X86_64.R_64, rel.type)
    }

    @Test
    fun `reads multiple relocations`() {
        val bytes = writeObj(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(64), align = 16)),
            symbols = listOf(
                Symbol("foo", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
                Symbol("bar", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ),
            relocations = listOf(
                Relocation(offset = 1, symbol = "foo", type = RelocationType.X86_64.PLT32,
                    addend = -4, section = ".text"),
                Relocation(offset = 20, symbol = "bar", type = RelocationType.X86_64.PLT32,
                    addend = -4, section = ".text"),
            ),
        )
        val obj = reader.read(bytes)
        assertEquals(2, obj.relocations.size)
        assertEquals("foo", obj.relocations[0].symbol)
        assertEquals("bar", obj.relocations[1].symbol)
    }

    @Test
    fun `reads R_32S relocation`() {
        val bytes = writeObj(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(16), align = 16)),
            symbols = listOf(
                Symbol("data_ref", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED)
            ),
            relocations = listOf(
                Relocation(offset = 3, symbol = "data_ref", type = RelocationType.X86_64.R_32S,
                    addend = 0, section = ".text")
            ),
        )
        val obj = reader.read(bytes)
        assertEquals(RelocationType.X86_64.R_32S, obj.relocations[0].type)
    }

    // Round-trip tests

    @Test
    fun `round-trip preserves section data`() {
        val textCode = byteArrayOf(0x55, 0x48, 0x89.toByte(), 0xE5.toByte(), 0xC3.toByte())
        val rodata = "test string\u0000".toByteArray()
        val data = byteArrayOf(1, 2, 3, 4)

        val original = ObjectFile(
            format = ObjectFormat.ELF,
            arch = Architecture(ArchType.X86_64),
            sections = listOf(
                Section(".text", SectionKind.TEXT, textCode, align = 16),
                Section(".rodata", SectionKind.RODATA, rodata, align = 1),
                Section(".data", SectionKind.DATA, data, align = 8),
            ),
            symbols = emptyList(),
            relocations = emptyList(),
        )

        val bytes = ElfObjectWriter().write(original)
        val parsed = reader.read(bytes)

        val parsedText = parsed.sections.first { it.name == ".text" }
        assertArrayEquals(textCode, parsedText.data)

        val parsedRodata = parsed.sections.first { it.name == ".rodata" }
        assertArrayEquals(rodata, parsedRodata.data)

        val parsedData = parsed.sections.first { it.name == ".data" }
        assertArrayEquals(data, parsedData.data)
    }

    @Test
    fun `round-trip preserves symbols`() {
        val original = ObjectFile(
            format = ObjectFormat.ELF,
            arch = Architecture(ArchType.X86_64),
            sections = listOf(
                Section(".text", SectionKind.TEXT, ByteArray(48), align = 16),
            ),
            symbols = listOf(
                Symbol("main", value = 0, size = 32, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("_start", value = 32, size = 16, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
            relocations = emptyList(),
        )

        val bytes = ElfObjectWriter().write(original)
        val parsed = reader.read(bytes)

        val main = parsed.symbols.first { it.name == "main" }
        assertEquals(0L, main.value)
        assertEquals(32L, main.size)
        assertEquals(SymbolBinding.GLOBAL, main.binding)
        assertEquals(SymbolKind.FUNCTION, main.kind)

        val start = parsed.symbols.first { it.name == "_start" }
        assertEquals(32L, start.value)
        assertEquals(16L, start.size)
    }

    @Test
    fun `round-trip preserves relocations`() {
        val original = ObjectFile(
            format = ObjectFormat.ELF,
            arch = Architecture(ArchType.X86_64),
            sections = listOf(
                Section(".text", SectionKind.TEXT, ByteArray(32), align = 16),
            ),
            symbols = listOf(
                Symbol("puts", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ),
            relocations = listOf(
                Relocation(offset = 5, symbol = "puts", type = RelocationType.X86_64.PLT32,
                    addend = -4, section = ".text"),
            ),
        )

        val bytes = ElfObjectWriter().write(original)
        val parsed = reader.read(bytes)

        assertEquals(1, parsed.relocations.size)
        val rel = parsed.relocations[0]
        assertEquals(5L, rel.offset)
        assertEquals("puts", rel.symbol)
        assertEquals(RelocationType.X86_64.PLT32, rel.type)
        assertEquals(-4L, rel.addend)
    }

    @Test
    fun `round-trip complex object`() {
        val textCode = byteArrayOf(
            0x55,                                    // push rbp
            0x48, 0x89.toByte(), 0xE5.toByte(),      // mov rbp, rsp
            0xE8.toByte(), 0x00, 0x00, 0x00, 0x00,   // call <reloc>
            0x5D,                                     // pop rbp
            0xC3.toByte(),                            // ret
        )

        val original = ObjectFile(
            format = ObjectFormat.ELF,
            arch = Architecture(ArchType.X86_64),
            sections = listOf(
                Section(".text", SectionKind.TEXT, textCode, align = 16),
                Section(".rodata", SectionKind.RODATA, "Hello\u0000".toByteArray(), align = 1),
                Section(".data", SectionKind.DATA, ByteArray(8), align = 8),
                Section(".bss", SectionKind.BSS, ByteArray(256), align = 16),
            ),
            symbols = listOf(
                Symbol("main", value = 0, size = textCode.size.toLong(), section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("puts", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
                Symbol("local_helper", value = 0, size = 4, section = ".text",
                    binding = SymbolBinding.LOCAL, kind = SymbolKind.FUNCTION),
            ),
            relocations = listOf(
                Relocation(offset = 5, symbol = "puts", type = RelocationType.X86_64.PLT32,
                    addend = -4, section = ".text"),
            ),
        )

        val bytes = ElfObjectWriter().write(original)
        val parsed = reader.read(bytes)

        assertEquals(ObjectFormat.ELF, parsed.format)
        assertEquals(ArchType.X86_64, parsed.arch.arch)
        assertTrue(ObjectFlag.RELOCATABLE in parsed.metadata.flags)

        // Sections
        assertNotNull(parsed.sections.firstOrNull { it.name == ".text" })
        assertNotNull(parsed.sections.firstOrNull { it.name == ".rodata" })
        assertNotNull(parsed.sections.firstOrNull { it.name == ".data" })
        assertNotNull(parsed.sections.firstOrNull { it.name == ".bss" })

        // Symbols
        assertNotNull(parsed.symbols.firstOrNull { it.name == "main" })
        assertNotNull(parsed.symbols.firstOrNull { it.name == "puts" })
        assertNotNull(parsed.symbols.firstOrNull { it.name == "local_helper" })

        // Relocations
        assertEquals(1, parsed.relocations.size)
    }

    // Edge cases

    @Test
    fun `handles empty text section`() {
        val bytes = writeObj(sections = listOf(
            Section(".text", SectionKind.TEXT, ByteArray(0), align = 1)
        ))
        val obj = reader.read(bytes)
        val text = obj.sections.first { it.name == ".text" }
        assertEquals(0, text.data.size)
    }

    @Test
    fun `handles large section data`() {
        val bigData = ByteArray(4096) { (it % 256).toByte() }
        val bytes = writeObj(sections = listOf(
            Section(".data", SectionKind.DATA, bigData, align = 16)
        ))
        val obj = reader.read(bytes)
        val sec = obj.sections.first { it.name == ".data" }
        assertArrayEquals(bigData, sec.data)
    }

    @Test
    fun `handles many symbols`() {
        val symbols = (0 until 50).map { i ->
            Symbol("func_$i", value = i.toLong() * 16, size = 16, section = ".text",
                binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)
        }
        val bytes = writeObj(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(800), align = 16)),
            symbols = symbols,
        )
        val obj = reader.read(bytes)
        for (i in 0 until 50) {
            assertNotNull(obj.symbols.firstOrNull { it.name == "func_$i" },
                "Missing symbol func_$i")
        }
    }

    @Test
    fun `section link points to strtab for symtab`() {
        val bytes = writeObj(
            sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xCC.toByte()), align = 1)),
            symbols = listOf(
                Symbol("main", value = 0, size = 1, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)
            ),
        )
        val obj = reader.read(bytes)
        val symtab = obj.sections.first { it.kind == SectionKind.SYMTAB }
        assertEquals(".strtab", symtab.link)
    }

    @Test
    fun `rela section links back to target section`() {
        val bytes = writeObj(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(16), align = 16)),
            symbols = listOf(
                Symbol("ext", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED)
            ),
            relocations = listOf(
                Relocation(offset = 0, symbol = "ext", type = RelocationType.X86_64.PLT32,
                    addend = -4, section = ".text")
            ),
        )
        val obj = reader.read(bytes)
        val relaSec = obj.sections.first { it.name.startsWith(".rela") }
        assertEquals(SectionKind.RELA, relaSec.kind)
        assertEquals(".text", relaSec.info)
    }

    // Direct binary tests (hand-crafted minimal ELF)

    @Test
    fun `parses minimal ELF header`() {
        val bytes = writeObj(sections = listOf(
            Section(".text", SectionKind.TEXT, byteArrayOf(0x90.toByte()), align = 1)
        ))
        val obj = reader.read(bytes)
        assertEquals(ObjectFormat.ELF, obj.format)
        assertEquals(ArchType.X86_64, obj.arch.arch)
    }

    @Test
    fun `reads section entry size for symtab`() {
        val bytes = writeObj(
            sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xCC.toByte()), align = 1)),
            symbols = listOf(
                Symbol("test", value = 0, size = 1, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)
            ),
        )
        val obj = reader.read(bytes)
        val symtab = obj.sections.first { it.kind == SectionKind.SYMTAB }
        assertEquals(Elf.SYM64_SIZE.toLong(), symtab.entrySize)
    }

    @Test
    fun `skips section symbols in output`() {
        val bytes = writeObj(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(16), align = 16)),
            symbols = listOf(
                Symbol("main", value = 0, size = 16, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)
            ),
        )
        val obj = reader.read(bytes)
        // Section symbols generated by the writer should still be present but typed correctly
        val sectionSyms = obj.symbols.filter { it.kind == SymbolKind.SECTION }
        assertTrue(sectionSyms.isNotEmpty())
    }

    @Test
    fun `handles object with only bss`() {
        val bytes = writeObj(sections = listOf(
            Section(".bss", SectionKind.BSS, ByteArray(1024), align = 32)
        ))
        val obj = reader.read(bytes)
        val bss = obj.sections.first { it.name == ".bss" }
        assertEquals(SectionKind.BSS, bss.kind)
        assertEquals(1024, bss.data.size)
    }

    @Test
    fun `reads correct shstrtab section index`() {
        val bytes = writeObj(sections = listOf(
            Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1)
        ))
        val obj = reader.read(bytes)
        val shstrtab = obj.sections.firstOrNull { it.name == ".shstrtab" }
        assertNotNull(shstrtab)
        assertEquals(SectionKind.STRTAB, shstrtab!!.kind)
    }

    @Test
    fun `mixed local and global symbols maintain correct sections`() {
        val bytes = writeObj(
            sections = listOf(
                Section(".text", SectionKind.TEXT, ByteArray(32), align = 16),
                Section(".data", SectionKind.DATA, ByteArray(16), align = 8),
            ),
            symbols = listOf(
                Symbol("main", value = 0, size = 16, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("_data_start", value = 0, size = 8, section = ".data",
                    binding = SymbolBinding.LOCAL, kind = SymbolKind.FUNCTION),
            ),
        )
        val obj = reader.read(bytes)
        val main = obj.symbols.first { it.name == "main" }
        assertEquals(".text", main.section)
        val dataStart = obj.symbols.first { it.name == "_data_start" }
        assertEquals(".data", dataStart.section)
    }

    @Test
    fun `relocation addend is preserved`() {
        val bytes = writeObj(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(32), align = 16)),
            symbols = listOf(
                Symbol("target", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED)
            ),
            relocations = listOf(
                Relocation(offset = 12, symbol = "target", type = RelocationType.X86_64.R_32S,
                    addend = 42, section = ".text")
            ),
        )
        val obj = reader.read(bytes)
        assertEquals(42L, obj.relocations[0].addend)
    }

    @Test
    fun `negative addend is preserved`() {
        val bytes = writeObj(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(32), align = 16)),
            symbols = listOf(
                Symbol("sym", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED)
            ),
            relocations = listOf(
                Relocation(offset = 0, symbol = "sym", type = RelocationType.X86_64.PLT32,
                    addend = -4, section = ".text")
            ),
        )
        val obj = reader.read(bytes)
        assertEquals(-4L, obj.relocations[0].addend)
    }

    @Test
    fun `no dynamic info for relocatable object`() {
        val bytes = writeObj(sections = listOf(
            Section(".text", SectionKind.TEXT, byteArrayOf(0xCC.toByte()), align = 1)
        ))
        val obj = reader.read(bytes)
        assertNull(obj.dynamicInfo)
    }

    @Test
    fun `detectFormat identifies ELF`() {
        val bytes = writeObj(sections = listOf(
            Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1)
        ))
        assertEquals(ObjectFormat.ELF, detectFormat(bytes))
    }

    // ELF32 tests

    @Test
    fun `reads ELF32 header`() {
        val bytes = buildMinimalElf32(machine = ElfMachine.I386.code, textData = byteArrayOf(0xC3.toByte()))
        val elf = ElfReader.read(bytes)
        assertEquals(ElfClass.ELF32, elf.header.elfClass)
        assertEquals(ElfMachine.I386, elf.header.machine)
        assertEquals(ElfObjectType.REL, elf.header.type)
    }

    @Test
    fun `reads ELF32 sections`() {
        val textData = byteArrayOf(0x55, 0x89.toByte(), 0xE5.toByte(), 0xC3.toByte())
        val bytes = buildMinimalElf32(machine = ElfMachine.I386.code, textData = textData)
        val elf = ElfReader.read(bytes)
        val text = elf.sections.firstOrNull { it.name == ".text" }
        assertNotNull(text)
        assertArrayEquals(textData, text!!.data)
    }

    @Test
    fun `reads ELF32 symbols`() {
        val bytes = buildMinimalElf32WithSymbol(
            machine = ElfMachine.I386.code,
            textData = ByteArray(16),
            symbolName = "main",
            symbolValue = 0,
            symbolSize = 16,
        )
        val elf = ElfReader.read(bytes)
        val main = elf.symbols.firstOrNull { it.name == "main" }
        assertNotNull(main)
        assertEquals(0L, main!!.value)
        assertEquals(16L, main.size)
        assertEquals(ElfSymbolBinding.GLOBAL, main.binding)
        assertEquals(ElfSymbolType.FUNC, main.type)
    }

    @Test
    fun `ELF32 projects to correct architecture`() {
        val bytes = buildMinimalElf32(machine = ElfMachine.I386.code, textData = byteArrayOf(0xC3.toByte()))
        val elf = ElfReader.read(bytes)
        val obj = ElfReader.toObjectFile(elf)
        assertEquals(ArchType.X86, obj.arch.arch)
    }

    @Test
    fun `ELF32 ARM projects to ARM architecture`() {
        val bytes = buildMinimalElf32(machine = ElfMachine.ARM.code, textData = byteArrayOf(0x1E, 0xFF.toByte(), 0x2F, 0xE1.toByte()))
        val elf = ElfReader.read(bytes)
        val obj = ElfReader.toObjectFile(elf)
        assertEquals(ArchType.ARM, obj.arch.arch)
    }

    @Test
    fun `ELF32 relocatable has RELOCATABLE flag`() {
        val bytes = buildMinimalElf32(machine = ElfMachine.I386.code, textData = byteArrayOf(0xCC.toByte()))
        val elf = ElfReader.read(bytes)
        val obj = ElfReader.toObjectFile(elf)
        assertTrue(ObjectFlag.RELOCATABLE in obj.metadata.flags)
    }

    @Test
    fun `reads ELF32 with RELA relocations`() {
        val bytes = buildMinimalElf32WithRelocation(
            machine = ElfMachine.I386.code,
            textData = ByteArray(16),
            symbolName = "puts",
            relOffset = 5,
            relType = 4, // R_386_PLT32
            relAddend = -4,
        )
        val elf = ElfReader.read(bytes)
        assertEquals(1, elf.relocations.size)
        val rel = elf.relocations[0]
        assertEquals(5L, rel.offset)
        assertEquals("puts", rel.symbolName)
        assertEquals(4, rel.type)
        assertEquals(-4L, rel.addend)
    }

    // ELF32 binary builders

    private fun buildMinimalElf32(machine: Int, textData: ByteArray): ByteArray {
        val shstrtab = buildShstrtab(listOf("", ".text", ".shstrtab"))
        val shstrtabNameOffsets = resolveShstrtabOffsets(listOf("", ".text", ".shstrtab"))

        val textOffset = Elf.EHDR32_SIZE
        val shstrtabOffset = textOffset + textData.size
        val shoff = align(shstrtabOffset + shstrtab.size, 4)
        val sectionCount = 3 // NULL + .text + .shstrtab

        val buf = ByteArrayOutputStream()
        writeElf32Header(buf, machine, shoff, sectionCount, shstrtabIdx = 2)
        buf.write(textData)
        buf.write(shstrtab)
        padTo(buf, shoff)

        // NULL section header
        writeShdr32(buf, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        // .text
        writeShdr32(buf, shstrtabNameOffsets[1], ElfSectionType.PROGBITS.code,
            (ElfSectionFlags.ALLOC or ElfSectionFlags.EXECINSTR).toInt(),
            0, textOffset, textData.size, 0, 0, 1, 0)
        // .shstrtab
        writeShdr32(buf, shstrtabNameOffsets[2], ElfSectionType.STRTAB.code,
            0, 0, shstrtabOffset, shstrtab.size, 0, 0, 1, 0)

        return buf.toByteArray()
    }

    private fun buildMinimalElf32WithSymbol(
        machine: Int, textData: ByteArray,
        symbolName: String, symbolValue: Int, symbolSize: Int,
    ): ByteArray {
        val names = listOf("", ".text", ".symtab", ".strtab", ".shstrtab")
        val shstrtab = buildShstrtab(names)
        val shstrtabNameOffsets = resolveShstrtabOffsets(names)

        val strtabBytes = buildShstrtab(listOf("", symbolName))
        val symNameOffset = 1 // after the leading NUL

        // Two symbols: NULL + the user symbol
        val symtabSize = Elf.SYM32_SIZE * 2
        val symtabData = ByteArray(symtabSize)
        val symBuf = ByteBuffer.wrap(symtabData).order(ByteOrder.LITTLE_ENDIAN)
        // NULL symbol (first 16 bytes are zero)
        // User symbol at offset SYM32_SIZE
        val symOff = Elf.SYM32_SIZE
        symBuf.putInt(symOff, symNameOffset)
        symBuf.putInt(symOff + 4, symbolValue)
        symBuf.putInt(symOff + 8, symbolSize)
        symtabData[symOff + 12] = Elf.stInfo(ElfSymbolBinding.GLOBAL, ElfSymbolType.FUNC).toByte()
        symtabData[symOff + 13] = 0 // other
        symBuf.putShort(symOff + 14, 1) // shndx = .text section index

        val textOffset = Elf.EHDR32_SIZE
        val symtabOffset = textOffset + textData.size
        val strtabOffset = symtabOffset + symtabSize
        val shstrtabOffset = strtabOffset + strtabBytes.size
        val shoff = align(shstrtabOffset + shstrtab.size, 4)
        val sectionCount = 5 // NULL + .text + .symtab + .strtab + .shstrtab

        val buf = ByteArrayOutputStream()
        writeElf32Header(buf, machine, shoff, sectionCount, shstrtabIdx = 4)
        buf.write(textData)
        buf.write(symtabData)
        buf.write(strtabBytes)
        buf.write(shstrtab)
        padTo(buf, shoff)

        writeShdr32(buf, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        writeShdr32(buf, shstrtabNameOffsets[1], ElfSectionType.PROGBITS.code,
            (ElfSectionFlags.ALLOC or ElfSectionFlags.EXECINSTR).toInt(),
            0, textOffset, textData.size, 0, 0, 1, 0)
        writeShdr32(buf, shstrtabNameOffsets[2], ElfSectionType.SYMTAB.code,
            0, 0, symtabOffset, symtabSize, 3, 1, 4, Elf.SYM32_SIZE)
        writeShdr32(buf, shstrtabNameOffsets[3], ElfSectionType.STRTAB.code,
            0, 0, strtabOffset, strtabBytes.size, 0, 0, 1, 0)
        writeShdr32(buf, shstrtabNameOffsets[4], ElfSectionType.STRTAB.code,
            0, 0, shstrtabOffset, shstrtab.size, 0, 0, 1, 0)

        return buf.toByteArray()
    }

    private fun buildMinimalElf32WithRelocation(
        machine: Int, textData: ByteArray,
        symbolName: String, relOffset: Int, relType: Int, relAddend: Int,
    ): ByteArray {
        val names = listOf("", ".text", ".rela.text", ".symtab", ".strtab", ".shstrtab")
        val shstrtab = buildShstrtab(names)
        val shstrtabNameOffsets = resolveShstrtabOffsets(names)

        val strtabBytes = buildShstrtab(listOf("", symbolName))

        // Two symbols: NULL + the user symbol (undefined)
        val symtabSize = Elf.SYM32_SIZE * 2
        val symtabData = ByteArray(symtabSize)
        val symBuf = ByteBuffer.wrap(symtabData).order(ByteOrder.LITTLE_ENDIAN)
        val symOff = Elf.SYM32_SIZE
        symBuf.putInt(symOff, 1)
        symBuf.putInt(symOff + 4, 0) // value
        symBuf.putInt(symOff + 8, 0) // size
        symtabData[symOff + 12] = Elf.stInfo(ElfSymbolBinding.GLOBAL, ElfSymbolType.NOTYPE).toByte()
        symBuf.putShort(symOff + 14, 0) // SHN_UNDEF

        // RELA entry (12 bytes for ELF32)
        val relaSize = Elf.RELA32_SIZE
        val relaData = ByteArray(relaSize)
        val relaBuf = ByteBuffer.wrap(relaData).order(ByteOrder.LITTLE_ENDIAN)
        relaBuf.putInt(0, relOffset)
        relaBuf.putInt(4, (1 shl 8) or (relType and 0xFF)) // symIdx=1, type
        relaBuf.putInt(8, relAddend)

        val textOffset = Elf.EHDR32_SIZE
        val relaOffset = textOffset + textData.size
        val symtabOffset = relaOffset + relaSize
        val strtabOffset = symtabOffset + symtabSize
        val shstrtabOffset = strtabOffset + strtabBytes.size
        val shoff = align(shstrtabOffset + shstrtab.size, 4)
        val sectionCount = 6

        val buf = ByteArrayOutputStream()
        writeElf32Header(buf, machine, shoff, sectionCount, shstrtabIdx = 5)
        buf.write(textData)
        buf.write(relaData)
        buf.write(symtabData)
        buf.write(strtabBytes)
        buf.write(shstrtab)
        padTo(buf, shoff)

        writeShdr32(buf, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        writeShdr32(buf, shstrtabNameOffsets[1], ElfSectionType.PROGBITS.code,
            (ElfSectionFlags.ALLOC or ElfSectionFlags.EXECINSTR).toInt(),
            0, textOffset, textData.size, 0, 0, 1, 0)
        writeShdr32(buf, shstrtabNameOffsets[2], ElfSectionType.RELA.code,
            ElfSectionFlags.INFO_LINK.toInt(),
            0, relaOffset, relaSize, 3, 1, 4, Elf.RELA32_SIZE)
        writeShdr32(buf, shstrtabNameOffsets[3], ElfSectionType.SYMTAB.code,
            0, 0, symtabOffset, symtabSize, 4, 1, 4, Elf.SYM32_SIZE)
        writeShdr32(buf, shstrtabNameOffsets[4], ElfSectionType.STRTAB.code,
            0, 0, strtabOffset, strtabBytes.size, 0, 0, 1, 0)
        writeShdr32(buf, shstrtabNameOffsets[5], ElfSectionType.STRTAB.code,
            0, 0, shstrtabOffset, shstrtab.size, 0, 0, 1, 0)

        return buf.toByteArray()
    }

    private fun writeElf32Header(buf: ByteArrayOutputStream, machine: Int, shoff: Int, shnum: Int, shstrtabIdx: Int) {
        buf.write(Elf.MAGIC)
        buf.write(ElfClass.ELF32.code)
        buf.write(ElfData.LSB.code)
        buf.write(Elf.VERSION)
        buf.write(0) // OS/ABI
        buf.write(ByteArray(8)) // padding
        writeU16(buf, ElfObjectType.REL.code)
        writeU16(buf, machine)
        writeU32(buf, Elf.VERSION)
        writeU32(buf, 0) // e_entry
        writeU32(buf, 0) // e_phoff
        writeU32(buf, shoff) // e_shoff
        writeU32(buf, 0) // e_flags
        writeU16(buf, Elf.EHDR32_SIZE)
        writeU16(buf, Elf.PHDR32_SIZE)
        writeU16(buf, 0) // phnum
        writeU16(buf, Elf.SHDR32_SIZE)
        writeU16(buf, shnum)
        writeU16(buf, shstrtabIdx)
    }

    private fun writeShdr32(
        buf: ByteArrayOutputStream,
        name: Int, type: Int, flags: Int, addr: Int, offset: Int, size: Int,
        link: Int, info: Int, addralign: Int, entsize: Int,
    ) {
        writeU32(buf, name)
        writeU32(buf, type)
        writeU32(buf, flags)
        writeU32(buf, addr)
        writeU32(buf, offset)
        writeU32(buf, size)
        writeU32(buf, link)
        writeU32(buf, info)
        writeU32(buf, addralign)
        writeU32(buf, entsize)
    }

    private fun buildShstrtab(names: List<String>): ByteArray {
        val buf = ByteArrayOutputStream()
        for (name in names) {
            buf.write(name.toByteArray(Charsets.US_ASCII))
            buf.write(0)
        }
        return buf.toByteArray()
    }

    private fun resolveShstrtabOffsets(names: List<String>): List<Int> {
        val offsets = mutableListOf<Int>()
        var pos = 0
        for (name in names) {
            offsets.add(pos)
            pos += name.length + 1
        }
        return offsets
    }

    private fun align(value: Int, alignment: Int): Int {
        if (alignment <= 1) return value
        return (value + alignment - 1) and (alignment - 1).inv()
    }

    private fun padTo(buf: ByteArrayOutputStream, target: Int) {
        val current = buf.size()
        if (current < target) buf.write(ByteArray(target - current))
    }

    private fun writeU16(buf: ByteArrayOutputStream, v: Int) {
        buf.write(v and 0xFF)
        buf.write((v shr 8) and 0xFF)
    }

    private fun writeU32(buf: ByteArrayOutputStream, v: Int) {
        buf.write(v and 0xFF)
        buf.write((v shr 8) and 0xFF)
        buf.write((v shr 16) and 0xFF)
        buf.write((v shr 24) and 0xFF)
    }
}
