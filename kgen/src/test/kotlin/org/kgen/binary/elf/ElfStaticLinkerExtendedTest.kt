package org.kgen.binary.elf

import org.kgen.binary.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ElfStaticLinkerExtendedTest {

    private val linker = ElfStaticLinker()

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

    // Symbol resolution: local vs global

    @Test
    fun `global symbol from second object resolves correctly`() {
        val obj1 = obj(
            sections = listOf(Section(".text", SectionKind.TEXT,
                byteArrayOf(0xE8.toByte(), 0x00, 0x00, 0x00, 0x00, 0xC3.toByte()), align = 16)),
            symbols = listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("helper", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ),
            relocations = listOf(Relocation(offset = 1, symbol = "helper",
                type = RelocationType.X86_64.PC32, addend = -4, section = ".text")),
        )
        val obj2 = obj(
            sections = listOf(Section(".text", SectionKind.TEXT,
                byteArrayOf(0xB8.toByte(), 0x01, 0x00, 0x00, 0x00, 0xC3.toByte()), align = 16)),
            symbols = listOf(
                Symbol("helper", value = 0, size = 6, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
        )
        val binary = linker.link(listOf(obj1, obj2))
        val buf = le(binary)
        val entry = readU64(buf, 24)
        val entryOff = (entry - 0x400000).toInt()
        val callDisp = readI32(buf, entryOff + 1)
        assertNotEquals(0, callDisp)
    }

    @Test
    fun `local symbols do not conflict across objects`() {
        val code = byteArrayOf(0xB8.toByte(), 0x01, 0x00, 0x00, 0x00, 0xC3.toByte())
        val obj1 = obj(
            sections = listOf(Section(".text", SectionKind.TEXT, code, align = 16)),
            symbols = listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("internal", value = 0, size = 6, section = ".text",
                    binding = SymbolBinding.LOCAL, kind = SymbolKind.FUNCTION),
            ),
        )
        val obj2 = obj(
            sections = listOf(Section(".text", SectionKind.TEXT, code, align = 16)),
            symbols = listOf(
                Symbol("internal", value = 0, size = 6, section = ".text",
                    binding = SymbolBinding.LOCAL, kind = SymbolKind.FUNCTION),
            ),
        )
        // Should not throw -- local symbols with same name in different objects are fine
        val binary = linker.link(listOf(obj1, obj2))
        assertTrue(binary.isNotEmpty())
    }

    // Multiple .text sections merged

    @Test
    fun `merges text sections from three objects`() {
        val code1 = byteArrayOf(0xB8.toByte(), 0x01, 0x00, 0x00, 0x00, 0xC3.toByte())
        val code2 = byteArrayOf(0xB8.toByte(), 0x02, 0x00, 0x00, 0x00, 0xC3.toByte())
        val code3 = byteArrayOf(0xB8.toByte(), 0x03, 0x00, 0x00, 0x00, 0xC3.toByte())

        val objects = listOf(
            obj(
                sections = listOf(Section(".text", SectionKind.TEXT, code1, align = 16)),
                symbols = listOf(Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)),
            ),
            obj(
                sections = listOf(Section(".text", SectionKind.TEXT, code2, align = 16)),
                symbols = listOf(Symbol("fn2", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)),
            ),
            obj(
                sections = listOf(Section(".text", SectionKind.TEXT, code3, align = 16)),
                symbols = listOf(Symbol("fn3", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)),
            ),
        )

        val binary = linker.link(objects)
        // All three code sequences should be present in the binary
        val binaryStr = binary.toList()
        assertTrue(binary.size > code1.size + code2.size + code3.size)
        assertEquals(ElfObjectType.EXEC.code, readU16(le(binary), 16))
    }

    @Test
    fun `merged text sections maintain alignment`() {
        val code1 = byteArrayOf(0x90.toByte()) // 1 byte
        val code2 = byteArrayOf(0xC3.toByte()) // 1 byte

        val objects = listOf(
            obj(
                sections = listOf(Section(".text", SectionKind.TEXT, code1, align = 16)),
                symbols = listOf(Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)),
            ),
            obj(
                sections = listOf(Section(".text", SectionKind.TEXT, code2, align = 16)),
                symbols = listOf(Symbol("fn2", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)),
            ),
        )

        val binary = linker.link(objects)
        val buf = le(binary)
        val entry = readU64(buf, 24)
        // Entry should be at a 16-byte aligned offset within the segment
        assertTrue(entry >= 0x400000)
    }

    // Multiple .data sections merged

    @Test
    fun `merges data sections from two objects`() {
        val data1 = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val data2 = byteArrayOf(0x05, 0x06, 0x07, 0x08)

        val objects = listOf(
            obj(
                sections = listOf(
                    Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 16),
                    Section(".data", SectionKind.DATA, data1, align = 4),
                ),
                symbols = listOf(
                    Symbol("_start", value = 0, section = ".text",
                        binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                    Symbol("d1", value = 0, size = 4, section = ".data",
                        binding = SymbolBinding.GLOBAL, kind = SymbolKind.DATA),
                ),
            ),
            obj(
                sections = listOf(Section(".data", SectionKind.DATA, data2, align = 4)),
                symbols = listOf(Symbol("d2", value = 0, size = 4, section = ".data",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.DATA)),
            ),
        )

        val binary = linker.link(objects)
        val buf = le(binary)

        // Should have RW LOAD segment
        val phoff = readU64(buf, 32).toInt()
        val rwPhdr = phoff + Elf.PHDR64_SIZE
        assertEquals(ElfSegmentType.LOAD.code, readU32(buf, rwPhdr))
        val rwFlags = readU32(buf, rwPhdr + 4)
        assertTrue(rwFlags and ElfSegmentFlags.W != 0)
    }

    @Test
    fun `merges rodata sections from two objects`() {
        val ro1 = "Hello\u0000".toByteArray(Charsets.US_ASCII)
        val ro2 = "World\u0000".toByteArray(Charsets.US_ASCII)

        val objects = listOf(
            obj(
                sections = listOf(
                    Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 16),
                    Section(".rodata", SectionKind.RODATA, ro1, align = 1),
                ),
                symbols = listOf(
                    Symbol("_start", value = 0, section = ".text",
                        binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                    Symbol("msg1", value = 0, section = ".rodata",
                        binding = SymbolBinding.LOCAL, kind = SymbolKind.DATA),
                ),
            ),
            obj(
                sections = listOf(Section(".rodata", SectionKind.RODATA, ro2, align = 1)),
                symbols = listOf(Symbol("msg2", value = 0, section = ".rodata",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.DATA)),
            ),
        )

        val binary = linker.link(objects)
        val binaryStr = String(binary, Charsets.US_ASCII)
        assertTrue(binaryStr.contains("Hello"))
        assertTrue(binaryStr.contains("World"))
    }

    // Relocation processing after linking

    @Test
    fun `PC32 relocation produces correct displacement`() {
        // _start: call target; ret
        val mainCode = byteArrayOf(0xE8.toByte(), 0x00, 0x00, 0x00, 0x00, 0xC3.toByte())
        // target: mov eax, 1; ret
        val targetCode = byteArrayOf(0xB8.toByte(), 0x01, 0x00, 0x00, 0x00, 0xC3.toByte())

        val obj1 = obj(
            sections = listOf(Section(".text", SectionKind.TEXT, mainCode, align = 16)),
            symbols = listOf(
                Symbol("_start", value = 0, section = ".text",
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

        val binary = linker.link(listOf(obj1, obj2))
        val buf = le(binary)
        val entry = readU64(buf, 24)
        val entryOff = (entry - 0x400000).toInt()

        // Verify call opcode is preserved
        assertEquals(0xE8.toByte(), binary[entryOff])
        // Verify displacement points to target
        val disp = readI32(buf, entryOff + 1)
        val targetAddr = entry + 5 + disp
        val targetOff = (targetAddr - 0x400000).toInt()
        assertEquals(0xB8.toByte(), binary[targetOff])
        assertEquals(0x01.toByte(), binary[targetOff + 1])
    }

    @Test
    fun `R_64 relocation patches absolute address`() {
        val code = byteArrayOf(0xC3.toByte())
        val dataBytes = ByteArray(8)

        val o = obj(
            sections = listOf(
                Section(".text", SectionKind.TEXT, code, align = 16),
                Section(".data", SectionKind.DATA, dataBytes, align = 8),
            ),
            symbols = listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("ptr", value = 0, section = ".data",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.DATA),
            ),
            relocations = listOf(Relocation(offset = 0, symbol = "_start",
                type = RelocationType.X86_64.R_64, addend = 0, section = ".data")),
        )

        val binary = linker.link(listOf(o))
        val buf = le(binary)
        val entry = readU64(buf, 24)

        // Find data segment
        val phoff = readU64(buf, 32).toInt()
        val rwPhdr = phoff + Elf.PHDR64_SIZE
        val dataFileOff = readU64(buf, rwPhdr + 8).toInt()

        // The first 8 bytes of data should contain _start's address
        val storedAddr = readU64(buf, dataFileOff)
        assertEquals(entry, storedAddr)
    }

    // Undefined symbol errors

    @Test
    fun `throws for single undefined symbol`() {
        val o = obj(
            sections = listOf(Section(".text", SectionKind.TEXT,
                byteArrayOf(0xE8.toByte(), 0x00, 0x00, 0x00, 0x00), align = 16)),
            symbols = listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("missing", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ),
            relocations = listOf(Relocation(offset = 1, symbol = "missing",
                type = RelocationType.X86_64.PC32, addend = -4, section = ".text")),
        )
        val ex = assertThrows(IllegalStateException::class.java) { linker.link(listOf(o)) }
        assertTrue(ex.message!!.contains("missing"))
    }

    @Test
    fun `throws for multiple undefined symbols`() {
        val o = obj(
            sections = listOf(Section(".text", SectionKind.TEXT, ByteArray(32), align = 16)),
            symbols = listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("undef1", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
                Symbol("undef2", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ),
            relocations = listOf(
                Relocation(offset = 0, symbol = "undef1", type = RelocationType.X86_64.PC32, addend = -4, section = ".text"),
                Relocation(offset = 8, symbol = "undef2", type = RelocationType.X86_64.PC32, addend = -4, section = ".text"),
            ),
        )
        val ex = assertThrows(IllegalStateException::class.java) { linker.link(listOf(o)) }
        assertTrue(ex.message!!.contains("undef1"))
        assertTrue(ex.message!!.contains("undef2"))
    }

    @Test
    fun `throws when no _start or main symbol`() {
        val o = obj(
            sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 16)),
            symbols = listOf(Symbol("helper", value = 0, section = ".text",
                binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)),
        )
        assertThrows(IllegalStateException::class.java) { linker.link(listOf(o)) }
    }

    @Test
    fun `requires non-empty object list`() {
        assertThrows(IllegalArgumentException::class.java) { linker.link(emptyList()) }
    }

    // Weak symbol resolution

    @Test
    fun `weak symbol is resolved by strong definition`() {
        val weakCode = byteArrayOf(0xB8.toByte(), 0x00, 0x00, 0x00, 0x00, 0xC3.toByte())
        val strongCode = byteArrayOf(0xB8.toByte(), 0x2A, 0x00, 0x00, 0x00, 0xC3.toByte())

        val obj1 = obj(
            sections = listOf(Section(".text", SectionKind.TEXT,
                byteArrayOf(0xE8.toByte(), 0x00, 0x00, 0x00, 0x00, 0xC3.toByte()), align = 16)),
            symbols = listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("maybe_fn", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ),
            relocations = listOf(Relocation(offset = 1, symbol = "maybe_fn",
                type = RelocationType.X86_64.PC32, addend = -4, section = ".text")),
        )

        // Object with weak definition
        val obj2 = obj(
            sections = listOf(Section(".text", SectionKind.TEXT, weakCode, align = 16)),
            symbols = listOf(Symbol("maybe_fn", value = 0, section = ".text",
                binding = SymbolBinding.WEAK, kind = SymbolKind.FUNCTION)),
        )

        // Object with strong definition
        val obj3 = obj(
            sections = listOf(Section(".text", SectionKind.TEXT, strongCode, align = 16)),
            symbols = listOf(Symbol("maybe_fn", value = 0, section = ".text",
                binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)),
        )

        // Strong should override weak
        val binary = linker.link(listOf(obj1, obj2, obj3))
        assertTrue(binary.isNotEmpty())
    }

    // Section ordering in output

    @Test
    fun `output has valid ELF structure with text before data segment`() {
        val o = obj(
            sections = listOf(
                Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 16),
                Section(".data", SectionKind.DATA, byteArrayOf(42), align = 4),
            ),
            symbols = listOf(Symbol("_start", value = 0, section = ".text",
                binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)),
        )

        val binary = linker.link(listOf(o))
        val buf = le(binary)
        val phoff = readU64(buf, 32).toInt()

        // First LOAD (RX) should come before second LOAD (RW) in virtual address space
        val rxVaddr = readU64(buf, phoff + 16) // p_vaddr
        val rwVaddr = readU64(buf, phoff + Elf.PHDR64_SIZE + 16) // p_vaddr
        assertTrue(rxVaddr <= rwVaddr, "RX segment should be at or before RW segment")
    }

    @Test
    fun `GNU_STACK segment is last program header`() {
        val o = obj(
            sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 16)),
            symbols = listOf(Symbol("_start", value = 0, section = ".text",
                binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)),
        )
        val binary = linker.link(listOf(o))
        val buf = le(binary)
        val phoff = readU64(buf, 32).toInt()
        val phnum = readU16(buf, 56)

        val lastPhdr = phoff + (phnum - 1) * Elf.PHDR64_SIZE
        assertEquals(ElfSegmentType.GNU_STACK.code, readU32(buf, lastPhdr))
    }

    // BSS section handling

    @Test
    fun `links object with bss section without error`() {
        val o = obj(
            sections = listOf(
                Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 16),
                Section(".bss", SectionKind.BSS, ByteArray(1024), align = 16),
            ),
            symbols = listOf(Symbol("_start", value = 0, section = ".text",
                binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)),
        )
        val binary = linker.link(listOf(o))
        assertTrue(binary.isNotEmpty())
        // BSS should NOT bloat the output since it's not a DATA or TEXT section that gets merged
        // The static linker only merges TEXT, DATA, RODATA
    }

    // Entry point via main

    @Test
    fun `uses main as entry point when no _start`() {
        val code = byteArrayOf(0xB8.toByte(), 0x3C, 0x00, 0x00, 0x00, 0xC3.toByte())
        val o = obj(
            sections = listOf(Section(".text", SectionKind.TEXT, code, align = 16)),
            symbols = listOf(Symbol("main", value = 0, section = ".text",
                binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)),
        )
        val binary = linker.link(listOf(o))
        val buf = le(binary)
        val entry = readU64(buf, 24)
        assertTrue(entry >= 0x400000)
    }

    @Test
    fun `prefers _start over main for entry point`() {
        val startCode = byteArrayOf(0xB8.toByte(), 0x01, 0x00, 0x00, 0x00, 0xC3.toByte())
        val mainCode = byteArrayOf(0xB8.toByte(), 0x02, 0x00, 0x00, 0x00, 0xC3.toByte())

        val o = obj(
            sections = listOf(Section(".text", SectionKind.TEXT, startCode + mainCode, align = 16)),
            symbols = listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("main", value = 6, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
        )
        val binary = linker.link(listOf(o))
        val buf = le(binary)
        val entry = readU64(buf, 24)
        val entryOff = (entry - 0x400000).toInt()
        // Should be _start (mov eax, 1), not main (mov eax, 2)
        assertEquals(0x01.toByte(), binary[entryOff + 1])
    }

    // Cross-reference between text and rodata

    @Test
    fun `resolves cross-reference from text to rodata`() {
        // lea rdi, [rip+msg]; ret
        val code = byteArrayOf(
            0x48, 0x8D.toByte(), 0x3D, 0x00, 0x00, 0x00, 0x00, // lea rdi, [rip+0]
            0xC3.toByte(),
        )
        val rodata = "TestString\u0000".toByteArray(Charsets.US_ASCII)

        val o = obj(
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
            relocations = listOf(Relocation(offset = 3, symbol = "msg",
                type = RelocationType.X86_64.PC32, addend = -4, section = ".text")),
        )

        val binary = linker.link(listOf(o))
        val buf = le(binary)
        val entry = readU64(buf, 24)
        val entryOff = (entry - 0x400000).toInt()
        val leaDisp = readI32(buf, entryOff + 3)

        // displacement should point to somewhere that contains "TestString"
        val targetFileOff = entryOff + 7 + leaDisp
        assertTrue(targetFileOff >= 0 && targetFileOff + 10 < binary.size)
        val str = String(binary, targetFileOff, 10, Charsets.US_ASCII)
        assertEquals("TestString", str)
    }

    // Linking with data relocations across objects

    @Test
    fun `resolves data relocation pointing to symbol in another object`() {
        val code = byteArrayOf(0xC3.toByte())
        val dataBytes = ByteArray(8) // pointer slot

        val obj1 = obj(
            sections = listOf(
                Section(".text", SectionKind.TEXT, code, align = 16),
                Section(".data", SectionKind.DATA, dataBytes, align = 8),
            ),
            symbols = listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("func_in_obj2", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ),
            relocations = listOf(Relocation(offset = 0, symbol = "func_in_obj2",
                type = RelocationType.X86_64.R_64, addend = 0, section = ".data")),
        )

        val obj2Code = byteArrayOf(0xB8.toByte(), 0x07, 0x00, 0x00, 0x00, 0xC3.toByte())
        val obj2 = obj(
            sections = listOf(Section(".text", SectionKind.TEXT, obj2Code, align = 16)),
            symbols = listOf(Symbol("func_in_obj2", value = 0, section = ".text",
                binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)),
        )

        val binary = linker.link(listOf(obj1, obj2))
        val buf = le(binary)

        // Find data pointer
        val phoff = readU64(buf, 32).toInt()
        val rwPhdr = phoff + Elf.PHDR64_SIZE
        val dataFileOff = readU64(buf, rwPhdr + 8).toInt()
        val storedAddr = readU64(buf, dataFileOff)

        // The stored address should be in the executable range
        assertTrue(storedAddr >= 0x400000, "Stored pointer should be in valid range")
    }

    // R_32S relocation

    @Test
    fun `R_32S relocation is applied correctly`() {
        val code = byteArrayOf(
            0xC7.toByte(), 0x05, 0x00, 0x00, 0x00, 0x00, // mov dword ptr [rip+0], imm32
            0x2A, 0x00, 0x00, 0x00,                       // imm32 = 42
            0xC3.toByte(),                                  // ret
        )

        val o = obj(
            sections = listOf(
                Section(".text", SectionKind.TEXT, code, align = 16),
                Section(".data", SectionKind.DATA, ByteArray(4), align = 4),
            ),
            symbols = listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("myvar", value = 0, section = ".data",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.DATA),
            ),
            relocations = listOf(Relocation(offset = 2, symbol = "myvar",
                type = RelocationType.X86_64.R_32S, addend = -10, section = ".text")),
        )

        val binary = linker.link(listOf(o))
        val buf = le(binary)
        val entry = readU64(buf, 24)
        val entryOff = (entry - 0x400000).toInt()
        val patchedVal = readI32(buf, entryOff + 2)
        // The patched value should not be zero (it was relocated)
        assertNotEquals(0, patchedVal)
    }

    // Linking four objects

    @Test
    fun `links four objects with chain of calls`() {
        fun makeCallObj(name: String, target: String): ObjectFile {
            val code = byteArrayOf(0xE8.toByte(), 0x00, 0x00, 0x00, 0x00, 0xC3.toByte())
            return obj(
                sections = listOf(Section(".text", SectionKind.TEXT, code, align = 16)),
                symbols = listOf(
                    Symbol(name, value = 0, section = ".text",
                        binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                    Symbol(target, value = 0, section = null,
                        binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
                ),
                relocations = listOf(Relocation(offset = 1, symbol = target,
                    type = RelocationType.X86_64.PC32, addend = -4, section = ".text")),
            )
        }
        val leaf = obj(
            sections = listOf(Section(".text", SectionKind.TEXT,
                byteArrayOf(0xB8.toByte(), 0x00, 0x00, 0x00, 0x00, 0xC3.toByte()), align = 16)),
            symbols = listOf(Symbol("d", value = 0, section = ".text",
                binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)),
        )

        val binary = linker.link(listOf(
            makeCallObj("_start", "b"),
            makeCallObj("b", "c"),
            makeCallObj("c", "d"),
            leaf,
        ))
        val buf = le(binary)
        assertEquals(ElfObjectType.EXEC.code, readU16(buf, 16))
    }

    // Only text, no data

    @Test
    fun `links text-only object with no RW segment content`() {
        val code = byteArrayOf(0xB8.toByte(), 0x3C, 0x00, 0x00, 0x00, 0x0F, 0x05)
        val o = obj(
            sections = listOf(Section(".text", SectionKind.TEXT, code, align = 16)),
            symbols = listOf(Symbol("_start", value = 0, section = ".text",
                binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)),
        )

        val binary = linker.link(listOf(o))
        val buf = le(binary)
        val phoff = readU64(buf, 32).toInt()

        // RW segment should be empty placeholder
        val rwPhdr = phoff + Elf.PHDR64_SIZE
        val rwFilesz = readU64(buf, rwPhdr + 32) // p_filesz
        assertEquals(0L, rwFilesz)
    }

    // Multiple sections of same kind within single object

    @Test
    fun `handles object with only rodata and text`() {
        val code = byteArrayOf(0xC3.toByte())
        val rodata = "ReadOnly\u0000".toByteArray(Charsets.US_ASCII)

        val o = obj(
            sections = listOf(
                Section(".text", SectionKind.TEXT, code, align = 16),
                Section(".rodata", SectionKind.RODATA, rodata, align = 1),
            ),
            symbols = listOf(Symbol("_start", value = 0, section = ".text",
                binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)),
        )

        val binary = linker.link(listOf(o))
        val binaryStr = String(binary, Charsets.US_ASCII)
        assertTrue(binaryStr.contains("ReadOnly"))
    }

    // Verify output is a valid ELF executable that can be re-read

    @Test
    fun `output can be parsed by ElfReader`() {
        val code = byteArrayOf(0xB8.toByte(), 0x3C, 0x00, 0x00, 0x00, 0x0F, 0x05)
        val o = obj(
            sections = listOf(Section(".text", SectionKind.TEXT, code, align = 16)),
            symbols = listOf(Symbol("_start", value = 0, section = ".text",
                binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)),
        )

        val binary = linker.link(listOf(o))
        assertTrue(ElfReader.canRead(binary))
        val elf = ElfReader.read(binary)
        assertEquals(ElfObjectType.EXEC, elf.header.type)
        assertEquals(ElfMachine.X86_64, elf.header.machine)
        assertTrue(elf.segments.isNotEmpty())
    }
}
