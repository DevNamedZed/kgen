package org.kgen.binary.elf

import org.kgen.binary.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ElfStaticLinkerTest {

    private fun le(bytes: ByteArray) = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    private fun readU16(buf: ByteBuffer, off: Int): Int = buf.getShort(off).toInt() and 0xFFFF
    private fun readU32(buf: ByteBuffer, off: Int): Int = buf.getInt(off)
    private fun readI32(buf: ByteBuffer, off: Int): Int = buf.getInt(off)
    private fun readU64(buf: ByteBuffer, off: Int): Long = buf.getLong(off)

    @Test
    fun `links single object with start symbol`() {
        // _start: mov eax, 60; xor edi, edi; syscall
        val code = byteArrayOf(
            0xB8.toByte(), 0x3C, 0x00, 0x00, 0x00, // mov eax, 60
            0x31, 0xFF.toByte(),                     // xor edi, edi
            0x0F, 0x05,                              // syscall
        )
        val obj = ObjectFile(
            format = ObjectFormat.ELF,
            arch = Architecture(ArchType.X86_64),
            sections = listOf(Section(".text", SectionKind.TEXT, code, align = 16)),
            symbols = listOf(
                Symbol("_start", value = 0, size = code.size.toLong(), section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
            relocations = emptyList(),
        )

        val binary = ElfStaticLinker().link(listOf(obj))
        val buf = le(binary)

        // ELF magic
        assertEquals(0x7f, binary[0].toInt() and 0xFF)
        assertEquals('E'.code, binary[1].toInt() and 0xFF)
        assertEquals('L'.code, binary[2].toInt() and 0xFF)
        assertEquals('F'.code, binary[3].toInt() and 0xFF)

        // Type and machine
        assertEquals(ElfObjectType.EXEC.code, readU16(buf, 16))
        assertEquals(ElfMachine.X86_64.code, readU16(buf, 18))

        // Entry point in valid range
        val entry = readU64(buf, 24)
        assertTrue(entry >= 0x400000, "Entry point $entry should be >= 0x400000")

        // 3 program headers
        val phnum = readU16(buf, 56)
        assertEquals(3, phnum)

        // First phdr is LOAD RX
        val phoff = readU64(buf, 32).toInt()
        assertEquals(ElfSegmentType.LOAD.code, readU32(buf, phoff))
        val rxFlags = readU32(buf, phoff + 4)
        assertTrue(rxFlags and ElfSegmentFlags.R != 0)
        assertTrue(rxFlags and ElfSegmentFlags.X != 0)

        // Last phdr is GNU_STACK
        val stackPhdr = phoff + 2 * Elf.PHDR64_SIZE
        assertEquals(ElfSegmentType.GNU_STACK.code, readU32(buf, stackPhdr))

        // Code should be in the binary at the entry offset
        val entryFileOff = (entry - 0x400000).toInt()
        assertEquals(0xB8.toByte(), binary[entryFileOff])
        assertEquals(0x3C.toByte(), binary[entryFileOff + 1])
    }

    @Test
    fun `links two objects with cross-reference`() {
        // main: call helper; ret
        val mainCode = byteArrayOf(
            0xE8.toByte(), 0x00, 0x00, 0x00, 0x00, // call helper (placeholder)
            0xC3.toByte(),                           // ret
        )
        val obj1 = ObjectFile(
            format = ObjectFormat.ELF,
            arch = Architecture(ArchType.X86_64),
            sections = listOf(Section(".text", SectionKind.TEXT, mainCode, align = 16)),
            symbols = listOf(
                Symbol("_start", value = 0, size = mainCode.size.toLong(), section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("helper", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ),
            relocations = listOf(
                Relocation(offset = 1, symbol = "helper", type = RelocationType.X86_64.PC32,
                    addend = -4, section = ".text"),
            ),
        )

        // helper: mov eax, 42; ret
        val helperCode = byteArrayOf(
            0xB8.toByte(), 0x2A, 0x00, 0x00, 0x00, // mov eax, 42
            0xC3.toByte(),                           // ret
        )
        val obj2 = ObjectFile(
            format = ObjectFormat.ELF,
            arch = Architecture(ArchType.X86_64),
            sections = listOf(Section(".text", SectionKind.TEXT, helperCode, align = 16)),
            symbols = listOf(
                Symbol("helper", value = 0, size = helperCode.size.toLong(), section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
            relocations = emptyList(),
        )

        val binary = ElfStaticLinker().link(listOf(obj1, obj2))
        val buf = le(binary)

        val entry = readU64(buf, 24)
        val entryOff = (entry - 0x400000).toInt()

        // The call instruction should have a patched displacement
        val callDisp = readI32(buf, entryOff + 1)
        assertNotEquals(0, callDisp, "Call displacement should be patched")

        // The displacement should point to helper (which is at mainCode.size after alignment)
        // displacement = target - (patch_addr + 4) = target - patch_addr + addend
        // helper is at textVaddr + 16 (aligned), call patch is at textVaddr + 1
        // callDisp should equal helperVaddr - (callPatchVaddr + 4) = helperVaddr - callPatchVaddr - 4
        val callPatchVaddr = entry + 1
        val helperVaddr = callPatchVaddr + callDisp + 4 // reconstruct: target = patch + disp + 4
        // helper should be somewhere after _start
        assertTrue(helperVaddr > entry, "Helper should be after _start")
    }

    @Test
    fun `links with rodata section`() {
        // lea rdi, [rip+msg]; ret
        val code = byteArrayOf(
            0x48, 0x8D.toByte(), 0x3D, 0x00, 0x00, 0x00, 0x00, // lea rdi, [rip+0]
            0xC3.toByte(),
        )
        val rodata = "Hello\u0000".toByteArray(Charsets.US_ASCII)

        val obj = ObjectFile(
            format = ObjectFormat.ELF,
            arch = Architecture(ArchType.X86_64),
            sections = listOf(
                Section(".text", SectionKind.TEXT, code, align = 16),
                Section(".rodata", SectionKind.RODATA, rodata, align = 1),
            ),
            symbols = listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("msg", value = 0, section = ".rodata",
                    binding = SymbolBinding.LOCAL, kind = SymbolKind.DATA),
            ),
            relocations = listOf(
                Relocation(offset = 3, symbol = "msg", type = RelocationType.X86_64.PC32,
                    addend = -4, section = ".text"),
            ),
        )

        val binary = ElfStaticLinker().link(listOf(obj))
        val buf = le(binary)

        // Verify the relocation was applied (not zero anymore)
        val entry = readU64(buf, 24)
        val entryOff = (entry - 0x400000).toInt()
        val leaDisp = readI32(buf, entryOff + 3)
        assertNotEquals(0, leaDisp, "LEA displacement should be patched")

        // The string "Hello" should appear somewhere in the binary
        val binaryStr = String(binary, Charsets.US_ASCII)
        assertTrue(binaryStr.contains("Hello"), "Binary should contain rodata string")
    }

    @Test
    fun `links with data section`() {
        // mov eax, [rip+myvar]; ret
        val code = byteArrayOf(
            0x8B.toByte(), 0x05, 0x00, 0x00, 0x00, 0x00, // mov eax, [rip+0]
            0xC3.toByte(),
        )
        val dataBytes = byteArrayOf(0x2A, 0x00, 0x00, 0x00) // int32 = 42

        val obj = ObjectFile(
            format = ObjectFormat.ELF,
            arch = Architecture(ArchType.X86_64),
            sections = listOf(
                Section(".text", SectionKind.TEXT, code, align = 16),
                Section(".data", SectionKind.DATA, dataBytes, align = 4),
            ),
            symbols = listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("myvar", value = 0, section = ".data",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.DATA),
            ),
            relocations = listOf(
                Relocation(offset = 2, symbol = "myvar", type = RelocationType.X86_64.PC32,
                    addend = -4, section = ".text"),
            ),
        )

        val binary = ElfStaticLinker().link(listOf(obj))
        val buf = le(binary)

        // Should have RW LOAD segment
        val phoff = readU64(buf, 32).toInt()
        val rwPhdr = phoff + Elf.PHDR64_SIZE
        assertEquals(ElfSegmentType.LOAD.code, readU32(buf, rwPhdr))
        val rwFlags = readU32(buf, rwPhdr + 4)
        assertTrue(rwFlags and ElfSegmentFlags.W != 0, "Data segment should be writable")

        // Displacement should be patched
        val entry = readU64(buf, 24)
        val entryOff = (entry - 0x400000).toInt()
        val movDisp = readI32(buf, entryOff + 2)
        assertNotEquals(0, movDisp, "MOV displacement should be patched")
    }

    @Test
    fun `rejects undefined symbols`() {
        val code = byteArrayOf(0xE8.toByte(), 0x00, 0x00, 0x00, 0x00, 0xC3.toByte())
        val obj = ObjectFile(
            format = ObjectFormat.ELF,
            arch = Architecture(ArchType.X86_64),
            sections = listOf(Section(".text", SectionKind.TEXT, code, align = 16)),
            symbols = listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("missing_func", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ),
            relocations = listOf(
                Relocation(offset = 1, symbol = "missing_func", type = RelocationType.X86_64.PC32,
                    addend = -4, section = ".text"),
            ),
        )

        val ex = assertThrows(IllegalStateException::class.java) {
            ElfStaticLinker().link(listOf(obj))
        }
        assertTrue(ex.message!!.contains("missing_func"))
    }

    @Test
    fun `links three objects with transitive references`() {
        // _start calls foo, foo calls bar
        val startCode = byteArrayOf(
            0xE8.toByte(), 0x00, 0x00, 0x00, 0x00, // call foo
            0x31, 0xC0.toByte(),                     // xor eax, eax
            0xC3.toByte(),                           // ret
        )
        val fooCode = byteArrayOf(
            0xE8.toByte(), 0x00, 0x00, 0x00, 0x00, // call bar
            0xC3.toByte(),                           // ret
        )
        val barCode = byteArrayOf(
            0xB8.toByte(), 0x07, 0x00, 0x00, 0x00, // mov eax, 7
            0xC3.toByte(),                           // ret
        )

        val obj1 = ObjectFile(
            format = ObjectFormat.ELF, arch = Architecture(ArchType.X86_64),
            sections = listOf(Section(".text", SectionKind.TEXT, startCode, align = 16)),
            symbols = listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("foo", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ),
            relocations = listOf(
                Relocation(offset = 1, symbol = "foo", type = RelocationType.X86_64.PC32,
                    addend = -4, section = ".text"),
            ),
        )
        val obj2 = ObjectFile(
            format = ObjectFormat.ELF, arch = Architecture(ArchType.X86_64),
            sections = listOf(Section(".text", SectionKind.TEXT, fooCode, align = 16)),
            symbols = listOf(
                Symbol("foo", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("bar", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ),
            relocations = listOf(
                Relocation(offset = 1, symbol = "bar", type = RelocationType.X86_64.PC32,
                    addend = -4, section = ".text"),
            ),
        )
        val obj3 = ObjectFile(
            format = ObjectFormat.ELF, arch = Architecture(ArchType.X86_64),
            sections = listOf(Section(".text", SectionKind.TEXT, barCode, align = 16)),
            symbols = listOf(
                Symbol("bar", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
            relocations = emptyList(),
        )

        val binary = ElfStaticLinker().link(listOf(obj1, obj2, obj3))
        val buf = le(binary)

        assertEquals(ElfObjectType.EXEC.code, readU16(buf, 16))

        // Verify call displacements are patched
        val entry = readU64(buf, 24)
        val entryOff = (entry - 0x400000).toInt()
        val callFooDisp = readI32(buf, entryOff + 1)
        assertNotEquals(0, callFooDisp, "Call to foo should be patched")

        // Follow the call chain: _start's call target should be foo
        val fooOff = entryOff + 5 + callFooDisp // call is 5 bytes: E8 + 4-byte disp
        val callBarDisp = readI32(buf, fooOff + 1)
        assertNotEquals(0, callBarDisp, "Call to bar should be patched")

        // bar should have mov eax, 7
        val barOff = fooOff + 5 + callBarDisp
        assertEquals(0xB8.toByte(), binary[barOff])
        assertEquals(0x07.toByte(), binary[barOff + 1])
    }

    @Test
    fun `produces no dynamic linking artifacts`() {
        val code = byteArrayOf(0xB8.toByte(), 0x3C, 0x00, 0x00, 0x00, 0x0F, 0x05)
        val obj = ObjectFile(
            format = ObjectFormat.ELF, arch = Architecture(ArchType.X86_64),
            sections = listOf(Section(".text", SectionKind.TEXT, code, align = 16)),
            symbols = listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
            relocations = emptyList(),
        )

        val binary = ElfStaticLinker().link(listOf(obj))
        val buf = le(binary)

        val phoff = readU64(buf, 32).toInt()
        val phnum = readU16(buf, 56)

        // No INTERP or DYNAMIC segments
        for (i in 0 until phnum) {
            val type = readU32(buf, phoff + i * Elf.PHDR64_SIZE)
            assertNotEquals(ElfSegmentType.INTERP.code, type, "Static binary should not have INTERP")
            assertNotEquals(ElfSegmentType.DYNAMIC.code, type, "Static binary should not have DYNAMIC")
        }

        // No .interp string in the binary
        val binaryStr = String(binary, Charsets.ISO_8859_1)
        assertFalse(binaryStr.contains("ld-linux"), "Static binary should not reference dynamic linker")
    }

    @Test
    fun `links with data relocations`() {
        // .data contains a pointer to a .text function
        val code = byteArrayOf(0xC3.toByte()) // ret
        val dataBytes = byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00) // 8-byte ptr

        val obj = ObjectFile(
            format = ObjectFormat.ELF, arch = Architecture(ArchType.X86_64),
            sections = listOf(
                Section(".text", SectionKind.TEXT, code, align = 16),
                Section(".data", SectionKind.DATA, dataBytes, align = 8),
            ),
            symbols = listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("func_ptr", value = 0, section = ".data",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.DATA),
            ),
            relocations = listOf(
                Relocation(offset = 0, symbol = "_start", type = RelocationType.X86_64.R_64,
                    addend = 0, section = ".data"),
            ),
        )

        val binary = ElfStaticLinker().link(listOf(obj))
        val buf = le(binary)

        val entry = readU64(buf, 24)

        // Find .data in the RW segment
        val phoff = readU64(buf, 32).toInt()
        val rwPhdr = phoff + Elf.PHDR64_SIZE
        val dataFileOff = readU64(buf, rwPhdr + 8).toInt() // p_offset at phdr+8
        val dataVaddr = readU64(buf, rwPhdr + 16) // p_vaddr at phdr+16

        // If the RW segment has actual content, the pointer should be patched to _start's vaddr
        if (dataVaddr > 0) {
            val storedPtr = readU64(buf, (dataFileOff - (dataVaddr - readU64(buf, rwPhdr + 16))).toInt())
            assertEquals(entry, storedPtr, "Data relocation should store _start's address")
        }
    }
}
