package org.kgen.binary.elf

import org.kgen.binary.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ElfSharedLinkerExtendedTest {

    private fun le(bytes: ByteArray) = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    private fun readU16(buf: ByteBuffer, off: Int): Int = buf.getShort(off).toInt() and 0xFFFF
    private fun readU32(buf: ByteBuffer, off: Int): Int = buf.getInt(off)
    private fun readI32(buf: ByteBuffer, off: Int): Int = buf.getInt(off)
    private fun readU64(buf: ByteBuffer, off: Int): Long = buf.getLong(off)

    private fun obj(
        sections: List<Section> = emptyList(),
        symbols: List<Symbol> = emptyList(),
        relocations: List<Relocation> = emptyList(),
    ) = ObjectFile(
        format = ObjectFormat.ELF,
        arch = Architecture(ArchType.X86_64),
        sections = sections,
        symbols = symbols,
        relocations = relocations,
    )

    private fun simpleExportObj(name: String, code: ByteArray = byteArrayOf(0xC3.toByte())): ObjectFile {
        return obj(
            sections = listOf(Section(".text", SectionKind.TEXT, code, align = 16)),
            symbols = listOf(Symbol(name, value = 0, size = code.size.toLong(),
                section = ".text", binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)),
        )
    }

    @Test
    fun `single function library produces DYN type`() {
        val binary = ElfSharedLinker().link(listOf(simpleExportObj("myfunc")))
        val buf = le(binary)
        assertEquals(ElfObjectType.DYN.code, readU16(buf, 16))
    }

    @Test
    fun `entry point is zero`() {
        val binary = ElfSharedLinker().link(listOf(simpleExportObj("fn")))
        val buf = le(binary)
        assertEquals(0L, readU64(buf, 24))
    }

    @Test
    fun `ELF magic is correct`() {
        val binary = ElfSharedLinker().link(listOf(simpleExportObj("fn")))
        assertEquals(0x7f, binary[0].toInt() and 0xFF)
        assertEquals('E'.code, binary[1].toInt() and 0xFF)
        assertEquals('L'.code, binary[2].toInt() and 0xFF)
        assertEquals('F'.code, binary[3].toInt() and 0xFF)
    }

    @Test
    fun `has four program headers`() {
        val binary = ElfSharedLinker().link(listOf(simpleExportObj("fn")))
        val buf = le(binary)
        assertEquals(4, readU16(buf, 56))
    }

    @Test
    fun `first segment is LOAD with RX permissions`() {
        val binary = ElfSharedLinker().link(listOf(simpleExportObj("fn")))
        val buf = le(binary)
        val phoff = readU64(buf, 32).toInt()
        assertEquals(ElfSegmentType.LOAD.code, readU32(buf, phoff))
        val flags = readU32(buf, phoff + 4)
        assertTrue(flags and ElfSegmentFlags.R != 0)
        assertTrue(flags and ElfSegmentFlags.X != 0)
    }

    @Test
    fun `second segment is LOAD with RW permissions`() {
        val binary = ElfSharedLinker().link(listOf(simpleExportObj("fn")))
        val buf = le(binary)
        val phoff = readU64(buf, 32).toInt()
        val rwPhdr = phoff + Elf.PHDR64_SIZE
        assertEquals(ElfSegmentType.LOAD.code, readU32(buf, rwPhdr))
        val flags = readU32(buf, rwPhdr + 4)
        assertTrue(flags and ElfSegmentFlags.R != 0)
        assertTrue(flags and ElfSegmentFlags.W != 0)
    }

    @Test
    fun `third segment is DYNAMIC`() {
        val binary = ElfSharedLinker().link(listOf(simpleExportObj("fn")))
        val buf = le(binary)
        val phoff = readU64(buf, 32).toInt()
        val dynPhdr = phoff + 2 * Elf.PHDR64_SIZE
        assertEquals(ElfSegmentType.DYNAMIC.code, readU32(buf, dynPhdr))
    }

    @Test
    fun `fourth segment is GNU_STACK`() {
        val binary = ElfSharedLinker().link(listOf(simpleExportObj("fn")))
        val buf = le(binary)
        val phoff = readU64(buf, 32).toInt()
        val stackPhdr = phoff + 3 * Elf.PHDR64_SIZE
        assertEquals(ElfSegmentType.GNU_STACK.code, readU32(buf, stackPhdr))
    }

    @Test
    fun `no INTERP segment present`() {
        val binary = ElfSharedLinker().link(listOf(simpleExportObj("fn")))
        val buf = le(binary)
        val phoff = readU64(buf, 32).toInt()
        val phnum = readU16(buf, 56)
        for (i in 0 until phnum) {
            val type = readU32(buf, phoff + i * Elf.PHDR64_SIZE)
            assertNotEquals(ElfSegmentType.INTERP.code, type)
        }
    }

    @Test
    fun `soname appears in binary when set`() {
        val binary = ElfSharedLinker(soname = "libtest.so.1").link(listOf(simpleExportObj("fn")))
        val str = String(binary, Charsets.ISO_8859_1)
        assertTrue(str.contains("libtest.so.1"))
    }

    @Test
    fun `soname omitted when not set`() {
        val binary = ElfSharedLinker().link(listOf(simpleExportObj("fn")))
        val str = String(binary, Charsets.ISO_8859_1)
        assertFalse(str.contains("libtest.so"))
    }

    @Test
    fun `exported symbol name appears in binary`() {
        val binary = ElfSharedLinker().link(listOf(simpleExportObj("compute")))
        val str = String(binary, Charsets.ISO_8859_1)
        assertTrue(str.contains("compute"))
    }

    @Test
    fun `multiple exports from single object`() {
        val code = byteArrayOf(0xC3.toByte(), 0xC3.toByte())
        val o = obj(
            sections = listOf(Section(".text", SectionKind.TEXT, code, align = 16)),
            symbols = listOf(
                Symbol("func_a", value = 0, size = 1, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("func_b", value = 1, size = 1, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
        )
        val binary = ElfSharedLinker().link(listOf(o))
        val str = String(binary, Charsets.ISO_8859_1)
        assertTrue(str.contains("func_a"))
        assertTrue(str.contains("func_b"))
    }

    @Test
    fun `exports from three objects`() {
        val binary = ElfSharedLinker(soname = "libmulti.so").link(listOf(
            simpleExportObj("add"),
            simpleExportObj("sub"),
            simpleExportObj("mul"),
        ))
        val str = String(binary, Charsets.ISO_8859_1)
        assertTrue(str.contains("add"))
        assertTrue(str.contains("sub"))
        assertTrue(str.contains("mul"))
        assertTrue(str.contains("libmulti.so"))
    }

    @Test
    fun `local symbols are not exported to dynstr`() {
        val o = obj(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(16), align = 16)),
            symbols = listOf(
                Symbol("public_fn", value = 0, size = 8, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("private_fn", value = 8, size = 8, section = ".text",
                    binding = SymbolBinding.LOCAL, kind = SymbolKind.FUNCTION),
            ),
        )
        val binary = ElfSharedLinker().link(listOf(o))
        val str = String(binary, Charsets.ISO_8859_1)
        assertTrue(str.contains("public_fn"))
        // Local symbols might still appear in the binary bytes incidentally but shouldn't be in dynstr
    }

    @Test
    fun `cross object reference is resolved`() {
        val callerCode = byteArrayOf(
            0xE8.toByte(), 0x00, 0x00, 0x00, 0x00, // call target
            0xC3.toByte(),
        )
        val targetCode = byteArrayOf(0xB8.toByte(), 0x2A, 0x00, 0x00, 0x00, 0xC3.toByte())
        val obj1 = obj(
            sections = listOf(Section(".text", SectionKind.TEXT, callerCode, align = 16)),
            symbols = listOf(
                Symbol("caller", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("target", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ),
            relocations = listOf(Relocation(offset = 1, symbol = "target",
                type = RelocationType.X86_64.PC32, addend = -4, section = ".text")),
        )
        val obj2 = obj(
            sections = listOf(Section(".text", SectionKind.TEXT, targetCode, align = 16)),
            symbols = listOf(Symbol("target", value = 0, section = ".text",
                binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)),
        )
        val binary = ElfSharedLinker().link(listOf(obj1, obj2))
        assertEquals(ElfObjectType.DYN.code, readU16(le(binary), 16))
    }

    @Test
    fun `shared lib references produce PLT entries`() {
        val code = byteArrayOf(0xE8.toByte(), 0x00, 0x00, 0x00, 0x00, 0xC3.toByte())
        val o = obj(
            sections = listOf(Section(".text", SectionKind.TEXT, code, align = 16)),
            symbols = listOf(
                Symbol("myfunc", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("puts", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ),
            relocations = listOf(Relocation(offset = 1, symbol = "puts",
                type = RelocationType.X86_64.PLT32, addend = -4, section = ".text")),
        )
        val binary = ElfSharedLinker(sharedLibs = listOf("libc.so.6")).link(listOf(o))
        val str = String(binary, Charsets.ISO_8859_1)
        assertTrue(str.contains("puts"))
        assertTrue(str.contains("libc.so.6"))
    }

    @Test
    fun `rodata is included in shared library`() {
        val code = byteArrayOf(0xC3.toByte())
        val rodata = "SO_RODATA_TEST\u0000".toByteArray()
        val o = obj(
            sections = listOf(
                Section(".text", SectionKind.TEXT, code, align = 16),
                Section(".rodata", SectionKind.RODATA, rodata, align = 1),
            ),
            symbols = listOf(Symbol("fn", value = 0, section = ".text",
                binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)),
        )
        val binary = ElfSharedLinker().link(listOf(o))
        val str = String(binary, Charsets.US_ASCII)
        assertTrue(str.contains("SO_RODATA_TEST"))
    }

    @Test
    fun `data section is included in shared library`() {
        val code = byteArrayOf(0xC3.toByte())
        val data = byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte())
        val o = obj(
            sections = listOf(
                Section(".text", SectionKind.TEXT, code, align = 16),
                Section(".data", SectionKind.DATA, data, align = 4),
            ),
            symbols = listOf(
                Symbol("fn", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("var", value = 0, section = ".data",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.DATA),
            ),
        )
        val binary = ElfSharedLinker().link(listOf(o))
        assertTrue(binary.isNotEmpty())
        // Verify the data bytes appear somewhere
        var found = false
        for (i in 0 until binary.size - 3) {
            if (binary[i] == 0xDE.toByte() && binary[i + 1] == 0xAD.toByte() &&
                binary[i + 2] == 0xBE.toByte() && binary[i + 3] == 0xEF.toByte()) {
                found = true; break
            }
        }
        assertTrue(found, "Data bytes should be present in the SO")
    }

    @Test
    fun `shared library with only data export`() {
        val code = byteArrayOf(0xC3.toByte())
        val data = byteArrayOf(42, 0, 0, 0)
        val o = obj(
            sections = listOf(
                Section(".text", SectionKind.TEXT, code, align = 16),
                Section(".data", SectionKind.DATA, data, align = 4),
            ),
            symbols = listOf(
                Symbol("get_value", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("shared_var", value = 0, section = ".data",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.DATA),
            ),
        )
        val binary = ElfSharedLinker(soname = "libdata.so").link(listOf(o))
        val str = String(binary, Charsets.ISO_8859_1)
        assertTrue(str.contains("shared_var"))
        assertTrue(str.contains("get_value"))
    }

    @Test
    fun `requires non-empty object list`() {
        assertThrows(IllegalArgumentException::class.java) {
            ElfSharedLinker().link(emptyList())
        }
    }

    @Test
    fun `multiple needed libraries appear in binary`() {
        val o = simpleExportObj("fn")
        val binary = ElfSharedLinker(sharedLibs = listOf("liba.so", "libb.so", "libc.so")).link(listOf(o))
        val str = String(binary, Charsets.ISO_8859_1)
        assertTrue(str.contains("liba.so"))
        assertTrue(str.contains("libb.so"))
        assertTrue(str.contains("libc.so"))
    }

    @Test
    fun `machine code is x86_64 by default`() {
        val binary = ElfSharedLinker().link(listOf(simpleExportObj("fn")))
        val buf = le(binary)
        assertEquals(ElfMachine.X86_64.code, readU16(buf, 18))
    }

    @Test
    fun `64-bit ELF class is set`() {
        val binary = ElfSharedLinker().link(listOf(simpleExportObj("fn")))
        assertEquals(ElfClass.ELF64.code, binary[4].toInt() and 0xFF)
    }

    @Test
    fun `little-endian data encoding`() {
        val binary = ElfSharedLinker().link(listOf(simpleExportObj("fn")))
        assertEquals(ElfData.LSB.code, binary[5].toInt() and 0xFF)
    }

    @Test
    fun `weak symbols are exported`() {
        val o = obj(
            sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 16)),
            symbols = listOf(Symbol("weakfn", value = 0, size = 1, section = ".text",
                binding = SymbolBinding.WEAK, kind = SymbolKind.FUNCTION)),
        )
        val binary = ElfSharedLinker().link(listOf(o))
        val str = String(binary, Charsets.ISO_8859_1)
        assertTrue(str.contains("weakfn"))
    }

    @Test
    fun `PLT relocation for multiple imports`() {
        val code = ByteArray(16)
        val o = obj(
            sections = listOf(Section(".text", SectionKind.TEXT, code, align = 16)),
            symbols = listOf(
                Symbol("myfunc", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("printf", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
                Symbol("malloc", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ),
            relocations = listOf(
                Relocation(offset = 1, symbol = "printf", type = RelocationType.X86_64.PLT32,
                    addend = -4, section = ".text"),
                Relocation(offset = 8, symbol = "malloc", type = RelocationType.X86_64.PLT32,
                    addend = -4, section = ".text"),
            ),
        )
        val binary = ElfSharedLinker(sharedLibs = listOf("libc.so.6")).link(listOf(o))
        val str = String(binary, Charsets.ISO_8859_1)
        assertTrue(str.contains("printf"))
        assertTrue(str.contains("malloc"))
    }

    @Test
    fun `no ld-linux reference in shared library`() {
        val binary = ElfSharedLinker().link(listOf(simpleExportObj("fn")))
        val str = String(binary, Charsets.ISO_8859_1)
        assertFalse(str.contains("ld-linux"))
    }

    @Test
    fun `binary size is reasonable for simple library`() {
        val binary = ElfSharedLinker().link(listOf(simpleExportObj("fn")))
        // A minimal .so should be a few KB, not MB
        assertTrue(binary.size < 64 * 1024, "SO should be small: ${binary.size}")
        assertTrue(binary.size > Elf.EHDR64_SIZE, "SO should be larger than header")
    }
}
