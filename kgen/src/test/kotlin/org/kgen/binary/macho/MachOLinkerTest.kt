package org.kgen.binary.macho

import org.kgen.binary.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MachOLinkerTest {

    private fun le(bytes: ByteArray) = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    private fun readU32(buf: ByteBuffer, off: Int): Int = buf.getInt(off)
    private fun readI32(buf: ByteBuffer, off: Int): Int = buf.getInt(off)
    private fun readU64(buf: ByteBuffer, off: Int): Long = buf.getLong(off)

    @Test
    fun `produces MH_EXECUTE Mach-O`() {
        val code = byteArrayOf(
            0xB8.toByte(), 0x01, 0x00, 0x00, 0x02, // mov eax, 0x2000001 (exit syscall macOS)
            0x31, 0xFF.toByte(),                     // xor edi, edi
            0x0F, 0x05,                              // syscall
        )
        val obj = ObjectFile(
            format = ObjectFormat.MACH_O,
            arch = Architecture(ArchType.X86_64),
            sections = listOf(Section(".text", SectionKind.TEXT, code, align = 16)),
            symbols = listOf(
                Symbol("_main", value = 0, size = code.size.toLong(), section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
            relocations = emptyList(),
        )

        val binary = MachOLinker().link(listOf(obj))
        val buf = le(binary)

        // Mach-O magic
        assertEquals(MachO.MH_MAGIC_64.toInt(), readU32(buf, 0))
        // CPU type
        assertEquals(MachO.CPU_TYPE_X86_64, readU32(buf, 4))
        // File type = MH_EXECUTE
        assertEquals(MachO.MH_EXECUTE, readU32(buf, 12))
    }

    @Test
    fun `has PAGEZERO and TEXT segments`() {
        val code = byteArrayOf(0xC3.toByte()) // ret
        val obj = ObjectFile(
            format = ObjectFormat.MACH_O,
            arch = Architecture(ArchType.X86_64),
            sections = listOf(Section(".text", SectionKind.TEXT, code, align = 16)),
            symbols = listOf(
                Symbol("_main", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
            relocations = emptyList(),
        )

        val binary = MachOLinker().link(listOf(obj))
        val buf = le(binary)

        val ncmds = readU32(buf, 16)
        assertTrue(ncmds >= 3, "Should have at least 3 load commands (PAGEZERO + TEXT + MAIN)")

        // First load command should be LC_SEGMENT_64 for __PAGEZERO
        val firstCmdOff = 32 // after header
        assertEquals(MachO.LC_SEGMENT_64, readU32(buf, firstCmdOff))
        val pagezeroName = String(binary, firstCmdOff + 8, 10, Charsets.US_ASCII)
        assertTrue(pagezeroName.startsWith("__PAGEZERO"))
        assertEquals(0L, readU64(buf, firstCmdOff + 24)) // vmaddr = 0
        assertEquals(0x100000000L, readU64(buf, firstCmdOff + 32)) // vmsize = 4GB

        // Second load command should be __TEXT
        val textCmdOff = firstCmdOff + 72 // SEGMENT_CMD_SIZE for PAGEZERO (no sections)
        assertEquals(MachO.LC_SEGMENT_64, readU32(buf, textCmdOff))
        val textName = String(binary, textCmdOff + 8, 6, Charsets.US_ASCII)
        assertTrue(textName.startsWith("__TEXT"))
        assertEquals(0x100000000L, readU64(buf, textCmdOff + 24)) // vmaddr
    }

    @Test
    fun `has LC_MAIN with valid entry offset`() {
        val code = byteArrayOf(0x31, 0xC0.toByte(), 0xC3.toByte()) // xor eax, eax; ret
        val obj = ObjectFile(
            format = ObjectFormat.MACH_O,
            arch = Architecture(ArchType.X86_64),
            sections = listOf(Section(".text", SectionKind.TEXT, code, align = 16)),
            symbols = listOf(
                Symbol("_main", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
            relocations = emptyList(),
        )

        val binary = MachOLinker().link(listOf(obj))
        val buf = le(binary)

        // Find LC_MAIN
        var off = 32
        val ncmds = readU32(buf, 16)
        var foundMain = false
        for (i in 0 until ncmds) {
            val cmd = readU32(buf, off)
            val cmdSize = readU32(buf, off + 4)
            if (cmd == MachO.LC_MAIN) {
                foundMain = true
                val entryOff = readU64(buf, off + 8)
                assertTrue(entryOff > 0, "Entry offset should be positive")
                assertTrue(entryOff < binary.size, "Entry offset should be within binary")
                // Verify the code is at the entry offset
                assertEquals(0x31.toByte(), binary[entryOff.toInt()])
                break
            }
            off += cmdSize
        }
        assertTrue(foundMain, "Should have LC_MAIN")
    }

    @Test
    fun `links two objects with cross-reference`() {
        val mainCode = byteArrayOf(
            0xE8.toByte(), 0x00, 0x00, 0x00, 0x00, // call helper
            0xC3.toByte(),
        )
        val obj1 = ObjectFile(
            format = ObjectFormat.MACH_O, arch = Architecture(ArchType.X86_64),
            sections = listOf(Section(".text", SectionKind.TEXT, mainCode, align = 16)),
            symbols = listOf(
                Symbol("_main", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("_helper", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ),
            relocations = listOf(
                Relocation(offset = 1, symbol = "_helper", type = RelocationType.X86_64.PC32,
                    addend = -4, section = ".text"),
            ),
        )

        val helperCode = byteArrayOf(
            0xB8.toByte(), 0x2A, 0x00, 0x00, 0x00, // mov eax, 42
            0xC3.toByte(),
        )
        val obj2 = ObjectFile(
            format = ObjectFormat.MACH_O, arch = Architecture(ArchType.X86_64),
            sections = listOf(Section(".text", SectionKind.TEXT, helperCode, align = 16)),
            symbols = listOf(
                Symbol("_helper", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
            relocations = emptyList(),
        )

        val binary = MachOLinker().link(listOf(obj1, obj2))
        val buf = le(binary)

        assertEquals(MachO.MH_EXECUTE, readU32(buf, 12))

        // Find the entry point and verify call is patched
        var off = 32
        val ncmds = readU32(buf, 16)
        for (i in 0 until ncmds) {
            val cmd = readU32(buf, off)
            val cmdSize = readU32(buf, off + 4)
            if (cmd == MachO.LC_MAIN) {
                val entryOff = readU64(buf, off + 8).toInt()
                val callDisp = readI32(buf, entryOff + 1)
                assertNotEquals(0, callDisp, "Call displacement should be patched")
                // Follow the call
                val helperOff = entryOff + 5 + callDisp
                assertEquals(0xB8.toByte(), binary[helperOff], "Should reach helper's mov eax, 42")
                assertEquals(0x2A.toByte(), binary[helperOff + 1])
                break
            }
            off += cmdSize
        }
    }

    @Test
    fun `includes rodata as __const`() {
        val code = byteArrayOf(
            0x48, 0x8D.toByte(), 0x3D, 0x00, 0x00, 0x00, 0x00, // lea rdi, [rip+msg]
            0xC3.toByte(),
        )
        val rodata = "Mach-O test\u0000".toByteArray(Charsets.US_ASCII)

        val obj = ObjectFile(
            format = ObjectFormat.MACH_O, arch = Architecture(ArchType.X86_64),
            sections = listOf(
                Section(".text", SectionKind.TEXT, code, align = 16),
                Section(".rodata", SectionKind.RODATA, rodata, align = 1),
            ),
            symbols = listOf(
                Symbol("_main", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("_msg", value = 0, section = ".rodata",
                    binding = SymbolBinding.LOCAL, kind = SymbolKind.DATA),
            ),
            relocations = listOf(
                Relocation(offset = 3, symbol = "_msg", type = RelocationType.X86_64.PC32,
                    addend = -4, section = ".text"),
            ),
        )

        val binary = MachOLinker().link(listOf(obj))
        val str = String(binary, Charsets.US_ASCII)
        assertTrue(str.contains("Mach-O test"), "Binary should contain rodata")
    }

    @Test
    fun `includes data segment when data sections present`() {
        val code = byteArrayOf(0xC3.toByte())
        val dataBytes = byteArrayOf(0x2A, 0x00, 0x00, 0x00)

        val obj = ObjectFile(
            format = ObjectFormat.MACH_O, arch = Architecture(ArchType.X86_64),
            sections = listOf(
                Section(".text", SectionKind.TEXT, code, align = 16),
                Section(".data", SectionKind.DATA, dataBytes, align = 4),
            ),
            symbols = listOf(
                Symbol("_main", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("_var", value = 0, section = ".data",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.DATA),
            ),
            relocations = emptyList(),
        )

        val binary = MachOLinker().link(listOf(obj))
        val buf = le(binary)

        // Find __DATA segment
        var off = 32
        val ncmds = readU32(buf, 16)
        var foundData = false
        for (i in 0 until ncmds) {
            val cmd = readU32(buf, off)
            val cmdSize = readU32(buf, off + 4)
            if (cmd == MachO.LC_SEGMENT_64) {
                val name = String(binary, off + 8, 6, Charsets.US_ASCII)
                if (name.startsWith("__DATA")) {
                    foundData = true
                    val prot = readU32(buf, off + 60) // initprot
                    assertTrue(prot and 2 != 0, "__DATA should be writable")
                }
            }
            off += cmdSize
        }
        assertTrue(foundData, "Should have __DATA segment")
    }

    @Test
    fun `rejects undefined symbols`() {
        val code = byteArrayOf(0xE8.toByte(), 0x00, 0x00, 0x00, 0x00, 0xC3.toByte())
        val obj = ObjectFile(
            format = ObjectFormat.MACH_O, arch = Architecture(ArchType.X86_64),
            sections = listOf(Section(".text", SectionKind.TEXT, code, align = 16)),
            symbols = listOf(
                Symbol("_main", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("_missing", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ),
            relocations = listOf(
                Relocation(offset = 1, symbol = "_missing", type = RelocationType.X86_64.PC32,
                    addend = -4, section = ".text"),
            ),
        )

        val ex = assertThrows(IllegalStateException::class.java) {
            MachOLinker().link(listOf(obj))
        }
        assertTrue(ex.message!!.contains("_missing"))
    }

    @Test
    fun `has LC_SYMTAB with symbols`() {
        val code = byteArrayOf(0xC3.toByte())
        val obj = ObjectFile(
            format = ObjectFormat.MACH_O, arch = Architecture(ArchType.X86_64),
            sections = listOf(Section(".text", SectionKind.TEXT, code, align = 16)),
            symbols = listOf(
                Symbol("_main", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
            relocations = emptyList(),
        )

        val binary = MachOLinker().link(listOf(obj))
        val buf = le(binary)

        var off = 32
        val ncmds = readU32(buf, 16)
        for (i in 0 until ncmds) {
            val cmd = readU32(buf, off)
            val cmdSize = readU32(buf, off + 4)
            if (cmd == MachO.LC_SYMTAB) {
                val nsyms = readU32(buf, off + 12)
                assertTrue(nsyms >= 1, "Should have at least 1 symbol")
                val stroff = readU32(buf, off + 16)
                val strsize = readU32(buf, off + 20)
                assertTrue(strsize > 0, "String table should be non-empty")
                // Verify _main appears in string table
                val strtab = String(binary, stroff, strsize, Charsets.US_ASCII)
                assertTrue(strtab.contains("_main"), "String table should contain _main")
                return
            }
            off += cmdSize
        }
        fail<Unit>("Should find LC_SYMTAB")
    }

    @Test
    fun `works with ARM64 CPU type`() {
        // ARM64: mov x0, #0; ret
        val code = byteArrayOf(
            0x00, 0x00, 0x80.toByte(), 0xD2.toByte(), // mov x0, #0
            0xC0.toByte(), 0x03, 0x5F, 0xD6.toByte(), // ret
        )
        val obj = ObjectFile(
            format = ObjectFormat.MACH_O,
            arch = Architecture(ArchType.AARCH64),
            sections = listOf(Section(".text", SectionKind.TEXT, code, align = 4)),
            symbols = listOf(
                Symbol("_main", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
            relocations = emptyList(),
        )

        val binary = MachOLinker(
            cpuType = MachO.CPU_TYPE_ARM64,
            cpuSubtype = MachO.CPU_SUBTYPE_ARM64_ALL,
        ).link(listOf(obj))
        val buf = le(binary)

        assertEquals(MachO.MH_MAGIC_64.toInt(), readU32(buf, 0))
        assertEquals(MachO.CPU_TYPE_ARM64, readU32(buf, 4))
        assertEquals(MachO.MH_EXECUTE, readU32(buf, 12))
    }
}
