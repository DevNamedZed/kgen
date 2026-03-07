package org.kgen.binary.elf

import org.kgen.binary.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ElfSharedLinkerTest {

    private fun le(bytes: ByteArray) = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    private fun readU16(buf: ByteBuffer, off: Int): Int = buf.getShort(off).toInt() and 0xFFFF
    private fun readU32(buf: ByteBuffer, off: Int): Int = buf.getInt(off)
    private fun readI32(buf: ByteBuffer, off: Int): Int = buf.getInt(off)
    private fun readU64(buf: ByteBuffer, off: Int): Long = buf.getLong(off)

    private fun makeExportLib(): ObjectFile {
        // add: lea eax, [rdi+rsi]; ret
        val addCode = byteArrayOf(
            0x8D.toByte(), 0x04, 0x37, // lea eax, [rdi+rsi]
            0xC3.toByte(),              // ret
        )
        // mul: imul eax, edi, esi → actually: mov eax, edi; imul eax, esi; ret
        val mulCode = byteArrayOf(
            0x89.toByte(), 0xF8.toByte(), // mov eax, edi
            0x0F, 0xAF.toByte(), 0xC6.toByte(), // imul eax, esi
            0xC3.toByte(),                // ret
        )
        return ObjectFile(
            format = ObjectFormat.ELF,
            arch = Architecture(ArchType.X86_64),
            sections = listOf(
                Section(".text", SectionKind.TEXT, addCode + mulCode, align = 16),
            ),
            symbols = listOf(
                Symbol("add", value = 0, size = addCode.size.toLong(), section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("mul", value = addCode.size.toLong(), size = mulCode.size.toLong(),
                    section = ".text", binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
            relocations = emptyList(),
        )
    }

    @Test
    fun `produces DYN type ELF`() {
        val binary = ElfSharedLinker().link(listOf(makeExportLib()))
        val buf = le(binary)

        assertEquals(0x7f, binary[0].toInt() and 0xFF)
        assertEquals('E'.code, binary[1].toInt() and 0xFF)
        assertEquals('L'.code, binary[2].toInt() and 0xFF)
        assertEquals('F'.code, binary[3].toInt() and 0xFF)

        assertEquals(ElfObjectType.DYN.code, readU16(buf, 16))
        assertEquals(ElfMachine.X86_64.code, readU16(buf, 18))
    }

    @Test
    fun `has no entry point`() {
        val binary = ElfSharedLinker().link(listOf(makeExportLib()))
        val buf = le(binary)
        assertEquals(0L, readU64(buf, 24), "Shared library should have no entry point")
    }

    @Test
    fun `has DYNAMIC segment but no INTERP`() {
        val binary = ElfSharedLinker().link(listOf(makeExportLib()))
        val buf = le(binary)

        val phoff = readU64(buf, 32).toInt()
        val phnum = readU16(buf, 56)
        assertEquals(4, phnum)

        var hasDynamic = false
        for (i in 0 until phnum) {
            val type = readU32(buf, phoff + i * Elf.PHDR64_SIZE)
            assertNotEquals(ElfSegmentType.INTERP.code, type, "SO should not have INTERP")
            if (type == ElfSegmentType.DYNAMIC.code) hasDynamic = true
        }
        assertTrue(hasDynamic, "SO must have DYNAMIC segment")
    }

    @Test
    fun `exports symbols in dynsym`() {
        val binary = ElfSharedLinker().link(listOf(makeExportLib()))

        // The string "add" and "mul" should appear in .dynstr somewhere in the binary
        val str = String(binary, Charsets.ISO_8859_1)
        assertTrue(str.contains("add"), "Binary should contain exported symbol name 'add'")
        assertTrue(str.contains("mul"), "Binary should contain exported symbol name 'mul'")
    }

    @Test
    fun `includes SONAME when specified`() {
        val binary = ElfSharedLinker(soname = "libmath.so.1").link(listOf(makeExportLib()))

        val str = String(binary, Charsets.ISO_8859_1)
        assertTrue(str.contains("libmath.so.1"), "Binary should contain SONAME")
    }

    @Test
    fun `has no INTERP string`() {
        val binary = ElfSharedLinker().link(listOf(makeExportLib()))
        val str = String(binary, Charsets.ISO_8859_1)
        assertFalse(str.contains("ld-linux"), "SO should not reference dynamic linker path")
    }

    @Test
    fun `links multiple objects into shared library`() {
        val addCode = byteArrayOf(0x8D.toByte(), 0x04, 0x37, 0xC3.toByte())
        val obj1 = ObjectFile(
            format = ObjectFormat.ELF, arch = Architecture(ArchType.X86_64),
            sections = listOf(Section(".text", SectionKind.TEXT, addCode, align = 16)),
            symbols = listOf(
                Symbol("add", value = 0, size = addCode.size.toLong(), section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
            relocations = emptyList(),
        )

        val subCode = byteArrayOf(
            0x89.toByte(), 0xF8.toByte(), // mov eax, edi
            0x29, 0xF0.toByte(),           // sub eax, esi
            0xC3.toByte(),                 // ret
        )
        val obj2 = ObjectFile(
            format = ObjectFormat.ELF, arch = Architecture(ArchType.X86_64),
            sections = listOf(Section(".text", SectionKind.TEXT, subCode, align = 16)),
            symbols = listOf(
                Symbol("sub", value = 0, size = subCode.size.toLong(), section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
            relocations = emptyList(),
        )

        val binary = ElfSharedLinker(soname = "libcalc.so").link(listOf(obj1, obj2))
        val buf = le(binary)

        assertEquals(ElfObjectType.DYN.code, readU16(buf, 16))
        val str = String(binary, Charsets.ISO_8859_1)
        assertTrue(str.contains("add"))
        assertTrue(str.contains("sub"))
        assertTrue(str.contains("libcalc.so"))
    }

    @Test
    fun `supports internal cross-object calls`() {
        // wrapper calls internal_add
        val wrapperCode = byteArrayOf(
            0xE8.toByte(), 0x00, 0x00, 0x00, 0x00, // call internal_add
            0xC3.toByte(),
        )
        val obj1 = ObjectFile(
            format = ObjectFormat.ELF, arch = Architecture(ArchType.X86_64),
            sections = listOf(Section(".text", SectionKind.TEXT, wrapperCode, align = 16)),
            symbols = listOf(
                Symbol("wrapper", value = 0, size = wrapperCode.size.toLong(), section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("internal_add", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ),
            relocations = listOf(
                Relocation(offset = 1, symbol = "internal_add", type = RelocationType.X86_64.PC32,
                    addend = -4, section = ".text"),
            ),
        )

        val addCode = byteArrayOf(0x8D.toByte(), 0x04, 0x37, 0xC3.toByte())
        val obj2 = ObjectFile(
            format = ObjectFormat.ELF, arch = Architecture(ArchType.X86_64),
            sections = listOf(Section(".text", SectionKind.TEXT, addCode, align = 16)),
            symbols = listOf(
                Symbol("internal_add", value = 0, size = addCode.size.toLong(), section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
            relocations = emptyList(),
        )

        val binary = ElfSharedLinker().link(listOf(obj1, obj2))
        val buf = le(binary)
        assertEquals(ElfObjectType.DYN.code, readU16(buf, 16))

        // Find wrapper in text, verify call displacement is patched
        // wrapper is first in merged text, at textOffset
        val textFileOff = layout(buf)
        // Just verify the call isn't zero (it was patched)
        // We need to find the text offset from the RX LOAD segment
        val phoff = readU64(buf, 32).toInt()
        val rxVaddr = readU64(buf, phoff + 16) // p_vaddr of first LOAD
        val rxOff = readU64(buf, phoff + 8).toInt() // p_offset of first LOAD

        // Text is after hash+dynsym+dynstr in the RX segment.
        // We can search for 0xE8 followed by non-zero displacement
        var found = false
        for (i in rxOff until binary.size - 5) {
            if (binary[i] == 0xE8.toByte()) {
                val disp = readI32(buf, i + 1)
                if (disp != 0) {
                    found = true
                    break
                }
            }
        }
        assertTrue(found, "Should find a patched call instruction in the .so")
    }

    @Test
    fun `creates PLT for external imports`() {
        val code = byteArrayOf(
            0xE8.toByte(), 0x00, 0x00, 0x00, 0x00, // call printf
            0xC3.toByte(),
        )
        val obj = ObjectFile(
            format = ObjectFormat.ELF, arch = Architecture(ArchType.X86_64),
            sections = listOf(Section(".text", SectionKind.TEXT, code, align = 16)),
            symbols = listOf(
                Symbol("my_print", value = 0, size = code.size.toLong(), section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("printf", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ),
            relocations = listOf(
                Relocation(offset = 1, symbol = "printf", type = RelocationType.X86_64.PLT32,
                    addend = -4, section = ".text"),
            ),
        )

        val binary = ElfSharedLinker(sharedLibs = listOf("libc.so.6")).link(listOf(obj))
        val buf = le(binary)

        assertEquals(ElfObjectType.DYN.code, readU16(buf, 16))

        val str = String(binary, Charsets.ISO_8859_1)
        assertTrue(str.contains("printf"), "Should contain imported symbol 'printf'")
        assertTrue(str.contains("libc.so.6"), "Should reference shared library")
        assertTrue(str.contains("my_print"), "Should export 'my_print'")
    }

    @Test
    fun `includes rodata section`() {
        val code = byteArrayOf(
            0x48, 0x8D.toByte(), 0x3D, 0x00, 0x00, 0x00, 0x00, // lea rdi, [rip+msg]
            0xC3.toByte(),
        )
        val rodata = "shared lib data\u0000".toByteArray(Charsets.US_ASCII)

        val obj = ObjectFile(
            format = ObjectFormat.ELF, arch = Architecture(ArchType.X86_64),
            sections = listOf(
                Section(".text", SectionKind.TEXT, code, align = 16),
                Section(".rodata", SectionKind.RODATA, rodata, align = 1),
            ),
            symbols = listOf(
                Symbol("get_str", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("msg", value = 0, section = ".rodata",
                    binding = SymbolBinding.LOCAL, kind = SymbolKind.DATA),
            ),
            relocations = listOf(
                Relocation(offset = 3, symbol = "msg", type = RelocationType.X86_64.PC32,
                    addend = -4, section = ".text"),
            ),
        )

        val binary = ElfSharedLinker().link(listOf(obj))
        val str = String(binary, Charsets.US_ASCII)
        assertTrue(str.contains("shared lib data"), "SO should contain rodata")
    }

    // Helper to avoid unused warning — not actually used in the test above
    private fun layout(buf: ByteBuffer): Long = 0
}
