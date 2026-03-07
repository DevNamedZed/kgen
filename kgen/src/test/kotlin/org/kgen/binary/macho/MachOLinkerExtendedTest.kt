package org.kgen.binary.macho

import org.kgen.binary.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MachOLinkerExtendedTest {

    private fun le(bytes: ByteArray) = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    private fun readU32(buf: ByteBuffer, off: Int): Int = buf.getInt(off)
    private fun readI32(buf: ByteBuffer, off: Int): Int = buf.getInt(off)
    private fun readU64(buf: ByteBuffer, off: Int): Long = buf.getLong(off)

    private fun obj(
        sections: List<Section> = emptyList(),
        symbols: List<Symbol> = emptyList(),
        relocations: List<Relocation> = emptyList(),
    ) = ObjectFile(
        format = ObjectFormat.MACH_O,
        arch = Architecture(ArchType.X86_64),
        sections = sections,
        symbols = symbols,
        relocations = relocations,
    )

    private fun simpleMainObj(code: ByteArray = byteArrayOf(0x31, 0xC0.toByte(), 0xC3.toByte())): ObjectFile {
        return obj(
            sections = listOf(Section(".text", SectionKind.TEXT, code, align = 16)),
            symbols = listOf(Symbol("_main", value = 0, size = code.size.toLong(),
                section = ".text", binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)),
        )
    }

    private fun findLoadCommand(binary: ByteArray, cmdType: Int): Int? {
        val buf = le(binary)
        var off = 32
        val ncmds = readU32(buf, 16)
        for (i in 0 until ncmds) {
            val cmd = readU32(buf, off)
            val cmdSize = readU32(buf, off + 4)
            if (cmd == cmdType) return off
            off += cmdSize
        }
        return null
    }

    @Test
    fun `produces MH_EXECUTE file type`() {
        val binary = MachOLinker().link(listOf(simpleMainObj()))
        val buf = le(binary)
        assertEquals(MachO.MH_EXECUTE, readU32(buf, 12))
    }

    @Test
    fun `has correct magic number`() {
        val binary = MachOLinker().link(listOf(simpleMainObj()))
        val buf = le(binary)
        assertEquals(MachO.MH_MAGIC_64.toInt(), readU32(buf, 0))
    }

    @Test
    fun `CPU type is x86_64 by default`() {
        val binary = MachOLinker().link(listOf(simpleMainObj()))
        val buf = le(binary)
        assertEquals(MachO.CPU_TYPE_X86_64, readU32(buf, 4))
    }

    @Test
    fun `has PAGEZERO segment`() {
        val binary = MachOLinker().link(listOf(simpleMainObj()))
        val firstCmdOff = 32
        val buf = le(binary)
        assertEquals(MachO.LC_SEGMENT_64, readU32(buf, firstCmdOff))
        val name = String(binary, firstCmdOff + 8, 10, Charsets.US_ASCII)
        assertTrue(name.startsWith("__PAGEZERO"))
    }

    @Test
    fun `PAGEZERO has vmaddr 0 and vmsize 4GB`() {
        val binary = MachOLinker().link(listOf(simpleMainObj()))
        val buf = le(binary)
        val off = 32
        assertEquals(0L, readU64(buf, off + 24))
        assertEquals(0x100000000L, readU64(buf, off + 32))
    }

    @Test
    fun `has TEXT segment at 4GB`() {
        val binary = MachOLinker().link(listOf(simpleMainObj()))
        val buf = le(binary)
        val textCmdOff = 32 + 72 // after PAGEZERO (no sections = 72 bytes)
        assertEquals(MachO.LC_SEGMENT_64, readU32(buf, textCmdOff))
        assertEquals(0x100000000L, readU64(buf, textCmdOff + 24))
    }

    @Test
    fun `has LC_MAIN`() {
        val binary = MachOLinker().link(listOf(simpleMainObj()))
        val mainOff = findLoadCommand(binary, MachO.LC_MAIN)
        assertNotNull(mainOff, "Should have LC_MAIN")
    }

    @Test
    fun `LC_MAIN entry offset points to code`() {
        val code = byteArrayOf(0x31, 0xC0.toByte(), 0xC3.toByte())
        val binary = MachOLinker().link(listOf(simpleMainObj(code)))
        val buf = le(binary)
        val mainOff = findLoadCommand(binary, MachO.LC_MAIN)!!
        val entryOff = readU64(buf, mainOff + 8).toInt()
        assertTrue(entryOff > 0)
        assertTrue(entryOff < binary.size)
        assertEquals(0x31.toByte(), binary[entryOff])
    }

    @Test
    fun `has LC_SYMTAB`() {
        val binary = MachOLinker().link(listOf(simpleMainObj()))
        val symtabOff = findLoadCommand(binary, MachO.LC_SYMTAB)
        assertNotNull(symtabOff)
    }

    @Test
    fun `LC_SYMTAB contains _main`() {
        val binary = MachOLinker().link(listOf(simpleMainObj()))
        val buf = le(binary)
        val symtabOff = findLoadCommand(binary, MachO.LC_SYMTAB)!!
        val stroff = readU32(buf, symtabOff + 16)
        val strsize = readU32(buf, symtabOff + 20)
        val strtab = String(binary, stroff, strsize, Charsets.US_ASCII)
        assertTrue(strtab.contains("_main"))
    }

    @Test
    fun `cross-object call is patched`() {
        val mainCode = byteArrayOf(0xE8.toByte(), 0x00, 0x00, 0x00, 0x00, 0xC3.toByte())
        val helperCode = byteArrayOf(0xB8.toByte(), 0x2A, 0x00, 0x00, 0x00, 0xC3.toByte())

        val obj1 = obj(
            sections = listOf(Section(".text", SectionKind.TEXT, mainCode, align = 16)),
            symbols = listOf(
                Symbol("_main", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("_helper", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ),
            relocations = listOf(Relocation(offset = 1, symbol = "_helper",
                type = RelocationType.X86_64.PC32, addend = -4, section = ".text")),
        )
        val obj2 = obj(
            sections = listOf(Section(".text", SectionKind.TEXT, helperCode, align = 16)),
            symbols = listOf(Symbol("_helper", value = 0, section = ".text",
                binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)),
        )

        val binary = MachOLinker().link(listOf(obj1, obj2))
        val buf = le(binary)
        val mainOff = findLoadCommand(binary, MachO.LC_MAIN)!!
        val entryOff = readU64(buf, mainOff + 8).toInt()
        val callDisp = readI32(buf, entryOff + 1)
        assertNotEquals(0, callDisp)
    }

    @Test
    fun `rodata string is in binary`() {
        val code = byteArrayOf(0xC3.toByte())
        val rodata = "LINKED_RODATA\u0000".toByteArray()
        val o = obj(
            sections = listOf(
                Section(".text", SectionKind.TEXT, code, align = 16),
                Section(".rodata", SectionKind.RODATA, rodata, align = 1),
            ),
            symbols = listOf(Symbol("_main", value = 0, section = ".text",
                binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)),
        )
        val binary = MachOLinker().link(listOf(o))
        assertTrue(String(binary, Charsets.US_ASCII).contains("LINKED_RODATA"))
    }

    @Test
    fun `data segment is writable`() {
        val o = obj(
            sections = listOf(
                Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 16),
                Section(".data", SectionKind.DATA, byteArrayOf(42), align = 4),
            ),
            symbols = listOf(Symbol("_main", value = 0, section = ".text",
                binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)),
        )
        val binary = MachOLinker().link(listOf(o))
        val buf = le(binary)
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
                    assertTrue(prot and 2 != 0, "DATA should be writable")
                }
            }
            off += cmdSize
        }
        assertTrue(foundData)
    }

    @Test
    fun `rejects undefined symbols`() {
        val o = obj(
            sections = listOf(Section(".text", SectionKind.TEXT,
                byteArrayOf(0xE8.toByte(), 0x00, 0x00, 0x00, 0x00, 0xC3.toByte()), align = 16)),
            symbols = listOf(
                Symbol("_main", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("_undef", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ),
            relocations = listOf(Relocation(offset = 1, symbol = "_undef",
                type = RelocationType.X86_64.PC32, addend = -4, section = ".text")),
        )
        val ex = assertThrows(IllegalStateException::class.java) {
            MachOLinker().link(listOf(o))
        }
        assertTrue(ex.message!!.contains("_undef"))
    }

    @Test
    fun `three objects linked successfully`() {
        val startCode = byteArrayOf(0xE8.toByte(), 0x00, 0x00, 0x00, 0x00, 0xC3.toByte())
        val fooCode = byteArrayOf(0xE8.toByte(), 0x00, 0x00, 0x00, 0x00, 0xC3.toByte())
        val barCode = byteArrayOf(0xB8.toByte(), 0x07, 0x00, 0x00, 0x00, 0xC3.toByte())

        val obj1 = obj(
            sections = listOf(Section(".text", SectionKind.TEXT, startCode, align = 16)),
            symbols = listOf(
                Symbol("_main", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("_foo", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ),
            relocations = listOf(Relocation(offset = 1, symbol = "_foo",
                type = RelocationType.X86_64.PC32, addend = -4, section = ".text")),
        )
        val obj2 = obj(
            sections = listOf(Section(".text", SectionKind.TEXT, fooCode, align = 16)),
            symbols = listOf(
                Symbol("_foo", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("_bar", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ),
            relocations = listOf(Relocation(offset = 1, symbol = "_bar",
                type = RelocationType.X86_64.PC32, addend = -4, section = ".text")),
        )
        val obj3 = obj(
            sections = listOf(Section(".text", SectionKind.TEXT, barCode, align = 16)),
            symbols = listOf(Symbol("_bar", value = 0, section = ".text",
                binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)),
        )

        val binary = MachOLinker().link(listOf(obj1, obj2, obj3))
        assertEquals(MachO.MH_EXECUTE, readU32(le(binary), 12))
    }

    @Test
    fun `ARM64 executable has correct CPU type`() {
        val code = byteArrayOf(0x00, 0x00, 0x80.toByte(), 0xD2.toByte(),
            0xC0.toByte(), 0x03, 0x5F, 0xD6.toByte())
        val o = ObjectFile(
            format = ObjectFormat.MACH_O,
            arch = Architecture(ArchType.AARCH64),
            sections = listOf(Section(".text", SectionKind.TEXT, code, align = 4)),
            symbols = listOf(Symbol("_main", value = 0, section = ".text",
                binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)),
            relocations = emptyList(),
        )
        val binary = MachOLinker(
            cpuType = MachO.CPU_TYPE_ARM64,
            cpuSubtype = MachO.CPU_SUBTYPE_ARM64_ALL,
        ).link(listOf(o))
        val buf = le(binary)
        assertEquals(MachO.CPU_TYPE_ARM64, readU32(buf, 4))
        assertEquals(MachO.MH_EXECUTE, readU32(buf, 12))
    }

    @Test
    fun `text only binary has no DATA segment`() {
        val binary = MachOLinker().link(listOf(simpleMainObj()))
        val buf = le(binary)
        var off = 32
        val ncmds = readU32(buf, 16)
        for (i in 0 until ncmds) {
            val cmd = readU32(buf, off)
            val cmdSize = readU32(buf, off + 4)
            if (cmd == MachO.LC_SEGMENT_64) {
                val name = String(binary, off + 8, 6, Charsets.US_ASCII)
                assertFalse(name.startsWith("__DATA"), "Text-only binary should not have __DATA")
            }
            off += cmdSize
        }
    }

    @Test
    fun `at least three load commands`() {
        val binary = MachOLinker().link(listOf(simpleMainObj()))
        val buf = le(binary)
        val ncmds = readU32(buf, 16)
        assertTrue(ncmds >= 3, "Need at least PAGEZERO + TEXT + MAIN")
    }

    @Test
    fun `binary size is reasonable`() {
        val binary = MachOLinker().link(listOf(simpleMainObj()))
        assertTrue(binary.size > 32, "Should be larger than header")
        assertTrue(binary.size < 64 * 1024, "Simple binary should be small: ${binary.size}")
    }

    @Test
    fun `rodata relocation is patched`() {
        val code = byteArrayOf(
            0x48, 0x8D.toByte(), 0x3D, 0x00, 0x00, 0x00, 0x00, 0xC3.toByte())
        val rodata = "TestData\u0000".toByteArray()
        val o = obj(
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
            relocations = listOf(Relocation(offset = 3, symbol = "_msg",
                type = RelocationType.X86_64.PC32, addend = -4, section = ".text")),
        )
        val binary = MachOLinker().link(listOf(o))
        val buf = le(binary)
        val mainOff = findLoadCommand(binary, MachO.LC_MAIN)!!
        val entryOff = readU64(buf, mainOff + 8).toInt()
        val leaDisp = readI32(buf, entryOff + 3)
        assertNotEquals(0, leaDisp)
    }
}
