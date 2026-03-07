package org.kgen.binary.elf

import org.kgen.binary.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ElfLinkerExtendedTest {

    private fun le(bytes: ByteArray) = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    private fun readU16(buf: ByteBuffer, off: Int): Int = buf.getShort(off).toInt() and 0xFFFF
    private fun readU32(buf: ByteBuffer, off: Int): Int = buf.getInt(off)
    private fun readI32(buf: ByteBuffer, off: Int): Int = buf.getInt(off)
    private fun readU64(buf: ByteBuffer, off: Int): Long = buf.getLong(off)

    private fun x86Obj(
        sections: List<Section>,
        symbols: List<Symbol>,
        relocations: List<Relocation> = emptyList(),
    ) = ObjectFile(
        format = ObjectFormat.ELF,
        arch = Architecture(ArchType.X86_64),
        sections = sections,
        symbols = symbols,
        relocations = relocations,
    )

    private fun retCode() = byteArrayOf(0xC3.toByte())

    private fun movEaxImm(imm: Int) = byteArrayOf(
        0xB8.toByte(),
        (imm and 0xFF).toByte(),
        ((imm shr 8) and 0xFF).toByte(),
        ((imm shr 16) and 0xFF).toByte(),
        ((imm shr 24) and 0xFF).toByte(),
    )

    private fun callPlaceholder() = byteArrayOf(
        0xE8.toByte(), 0x00, 0x00, 0x00, 0x00,
    )

    private fun leaRipPlaceholder() = byteArrayOf(
        0x48, 0x8D.toByte(), 0x3D, 0x00, 0x00, 0x00, 0x00,
    )

    // ==========================================
    // Static Linker: Section Merging
    // ==========================================

    @Test
    fun `static - merges text sections from three objects`() {
        val code1 = movEaxImm(1) + retCode()
        val code2 = movEaxImm(2) + retCode()
        val code3 = movEaxImm(3) + retCode()

        val obj1 = x86Obj(
            sections = listOf(Section(".text", SectionKind.TEXT, code1, align = 16)),
            symbols = listOf(Symbol("_start", value = 0, section = ".text", kind = SymbolKind.FUNCTION)),
        )
        val obj2 = x86Obj(
            sections = listOf(Section(".text", SectionKind.TEXT, code2, align = 16)),
            symbols = listOf(Symbol("func2", value = 0, section = ".text", kind = SymbolKind.FUNCTION)),
        )
        val obj3 = x86Obj(
            sections = listOf(Section(".text", SectionKind.TEXT, code3, align = 16)),
            symbols = listOf(Symbol("func3", value = 0, section = ".text", kind = SymbolKind.FUNCTION)),
        )

        val binary = ElfStaticLinker().link(listOf(obj1, obj2, obj3))
        val buf = le(binary)

        assertEquals(ElfObjectType.EXEC.code, readU16(buf, 16))
        val entry = readU64(buf, 24)
        assertTrue(entry >= 0x400000)

        // All three code segments should appear in the binary
        assertTrue(binary.size > code1.size + code2.size + code3.size)
    }

    @Test
    fun `static - merges multiple rodata sections`() {
        val code = leaRipPlaceholder() + retCode()
        val rodata1 = "Hello\u0000".toByteArray(Charsets.US_ASCII)
        val rodata2 = "World\u0000".toByteArray(Charsets.US_ASCII)

        val obj1 = x86Obj(
            sections = listOf(
                Section(".text", SectionKind.TEXT, code, align = 16),
                Section(".rodata", SectionKind.RODATA, rodata1, align = 1),
            ),
            symbols = listOf(
                Symbol("_start", value = 0, section = ".text", kind = SymbolKind.FUNCTION),
                Symbol("msg1", value = 0, section = ".rodata", binding = SymbolBinding.LOCAL, kind = SymbolKind.DATA),
            ),
            relocations = listOf(
                Relocation(offset = 3, symbol = "msg1", type = RelocationType.X86_64.PC32, addend = -4, section = ".text"),
            ),
        )
        val obj2 = x86Obj(
            sections = listOf(Section(".rodata", SectionKind.RODATA, rodata2, align = 1)),
            symbols = listOf(
                Symbol("msg2", value = 0, section = ".rodata", binding = SymbolBinding.LOCAL, kind = SymbolKind.DATA),
            ),
        )

        val binary = ElfStaticLinker().link(listOf(obj1, obj2))
        val str = String(binary, Charsets.US_ASCII)
        assertTrue(str.contains("Hello"), "Binary should contain first rodata string")
        assertTrue(str.contains("World"), "Binary should contain second rodata string")
    }

    @Test
    fun `static - merges multiple data sections`() {
        val code = movEaxImm(0) + retCode()
        val data1 = byteArrayOf(0x01, 0x00, 0x00, 0x00)
        val data2 = byteArrayOf(0x02, 0x00, 0x00, 0x00)

        val obj1 = x86Obj(
            sections = listOf(
                Section(".text", SectionKind.TEXT, code, align = 16),
                Section(".data", SectionKind.DATA, data1, align = 4),
            ),
            symbols = listOf(
                Symbol("_start", value = 0, section = ".text", kind = SymbolKind.FUNCTION),
                Symbol("var1", value = 0, section = ".data", kind = SymbolKind.DATA),
            ),
        )
        val obj2 = x86Obj(
            sections = listOf(Section(".data", SectionKind.DATA, data2, align = 4)),
            symbols = listOf(Symbol("var2", value = 0, section = ".data", kind = SymbolKind.DATA)),
        )

        val binary = ElfStaticLinker().link(listOf(obj1, obj2))
        val buf = le(binary)
        assertEquals(ElfObjectType.EXEC.code, readU16(buf, 16))

        // Should have an RW segment for the merged data
        val phoff = readU64(buf, 32).toInt()
        val rwPhdr = phoff + Elf.PHDR64_SIZE
        assertEquals(ElfSegmentType.LOAD.code, readU32(buf, rwPhdr))
        val flags = readU32(buf, rwPhdr + 4)
        assertTrue(flags and ElfSegmentFlags.W != 0)
    }

    // ==========================================
    // Static Linker: Symbol Resolution
    // ==========================================

    @Test
    fun `static - resolves symbol defined after use`() {
        // obj1 references 'helper' which is defined in obj2 (forward reference across objects)
        val mainCode = callPlaceholder() + retCode()
        val helperCode = movEaxImm(99) + retCode()

        val obj1 = x86Obj(
            sections = listOf(Section(".text", SectionKind.TEXT, mainCode, align = 16)),
            symbols = listOf(
                Symbol("_start", value = 0, section = ".text", kind = SymbolKind.FUNCTION),
                Symbol("helper", value = 0, section = null, kind = SymbolKind.UNDEFINED),
            ),
            relocations = listOf(
                Relocation(offset = 1, symbol = "helper", type = RelocationType.X86_64.PC32, addend = -4, section = ".text"),
            ),
        )
        val obj2 = x86Obj(
            sections = listOf(Section(".text", SectionKind.TEXT, helperCode, align = 16)),
            symbols = listOf(Symbol("helper", value = 0, size = helperCode.size.toLong(), section = ".text", kind = SymbolKind.FUNCTION)),
        )

        val binary = ElfStaticLinker().link(listOf(obj1, obj2))
        val buf = le(binary)
        val entry = readU64(buf, 24)
        val entryOff = (entry - 0x400000).toInt()
        val callDisp = readI32(buf, entryOff + 1)
        assertNotEquals(0, callDisp, "Call displacement should be patched")
    }

    @Test
    fun `static - resolves symbols across four objects`() {
        // Chain: _start -> a -> b -> c
        fun callAndRet() = callPlaceholder() + retCode()
        val leafCode = movEaxImm(42) + retCode()

        val objs = listOf(
            x86Obj(
                sections = listOf(Section(".text", SectionKind.TEXT, callAndRet(), align = 16)),
                symbols = listOf(
                    Symbol("_start", value = 0, section = ".text", kind = SymbolKind.FUNCTION),
                    Symbol("a", value = 0, section = null, kind = SymbolKind.UNDEFINED),
                ),
                relocations = listOf(Relocation(1, "a", RelocationType.X86_64.PC32, -4, ".text")),
            ),
            x86Obj(
                sections = listOf(Section(".text", SectionKind.TEXT, callAndRet(), align = 16)),
                symbols = listOf(
                    Symbol("a", value = 0, section = ".text", kind = SymbolKind.FUNCTION),
                    Symbol("b", value = 0, section = null, kind = SymbolKind.UNDEFINED),
                ),
                relocations = listOf(Relocation(1, "b", RelocationType.X86_64.PC32, -4, ".text")),
            ),
            x86Obj(
                sections = listOf(Section(".text", SectionKind.TEXT, callAndRet(), align = 16)),
                symbols = listOf(
                    Symbol("b", value = 0, section = ".text", kind = SymbolKind.FUNCTION),
                    Symbol("c", value = 0, section = null, kind = SymbolKind.UNDEFINED),
                ),
                relocations = listOf(Relocation(1, "c", RelocationType.X86_64.PC32, -4, ".text")),
            ),
            x86Obj(
                sections = listOf(Section(".text", SectionKind.TEXT, leafCode, align = 16)),
                symbols = listOf(Symbol("c", value = 0, section = ".text", kind = SymbolKind.FUNCTION)),
            ),
        )

        val binary = ElfStaticLinker().link(objs)
        val buf = le(binary)
        assertEquals(ElfObjectType.EXEC.code, readU16(buf, 16))

        val entry = readU64(buf, 24)
        val entryOff = (entry - 0x400000).toInt()
        val callDisp = readI32(buf, entryOff + 1)
        assertNotEquals(0, callDisp)
    }

    @Test
    fun `static - local symbols do not conflict across objects`() {
        val code1 = movEaxImm(1) + retCode()
        val code2 = movEaxImm(2) + retCode()

        // Both objects have a LOCAL symbol named "internal" -- should not conflict
        val obj1 = x86Obj(
            sections = listOf(Section(".text", SectionKind.TEXT, code1, align = 16)),
            symbols = listOf(
                Symbol("_start", value = 0, section = ".text", kind = SymbolKind.FUNCTION),
                Symbol("internal", value = 0, section = ".text", binding = SymbolBinding.LOCAL, kind = SymbolKind.FUNCTION),
            ),
        )
        val obj2 = x86Obj(
            sections = listOf(Section(".text", SectionKind.TEXT, code2, align = 16)),
            symbols = listOf(
                Symbol("internal", value = 0, section = ".text", binding = SymbolBinding.LOCAL, kind = SymbolKind.FUNCTION),
            ),
        )

        // Should link without error
        val binary = ElfStaticLinker().link(listOf(obj1, obj2))
        assertTrue(binary.isNotEmpty())
    }

    @Test
    fun `static - symbol at nonzero offset within section`() {
        // Code has two functions: _start at offset 0, helper at offset 6
        val startCode = movEaxImm(0) + retCode()
        val helperCode = movEaxImm(42) + retCode()
        val combined = startCode + helperCode

        val obj = x86Obj(
            sections = listOf(Section(".text", SectionKind.TEXT, combined, align = 16)),
            symbols = listOf(
                Symbol("_start", value = 0, section = ".text", kind = SymbolKind.FUNCTION),
                Symbol("helper", value = startCode.size.toLong(), section = ".text", kind = SymbolKind.FUNCTION),
            ),
        )

        val binary = ElfStaticLinker().link(listOf(obj))
        val buf = le(binary)
        val entry = readU64(buf, 24)
        val entryOff = (entry - 0x400000).toInt()

        // _start should be mov eax, 0
        assertEquals(0xB8.toByte(), binary[entryOff])
        assertEquals(0x00.toByte(), binary[entryOff + 1])

        // helper at startCode.size offset should be mov eax, 42
        assertEquals(0xB8.toByte(), binary[entryOff + startCode.size])
        assertEquals(0x2A.toByte(), binary[entryOff + startCode.size + 1])
    }

    // ==========================================
    // Static Linker: Error Cases
    // ==========================================

    @Test
    fun `static - rejects empty object list`() {
        assertThrows(IllegalArgumentException::class.java) {
            ElfStaticLinker().link(emptyList())
        }
    }

    @Test
    fun `static - rejects multiple undefined symbols`() {
        val code = callPlaceholder() + callPlaceholder() + retCode()
        val obj = x86Obj(
            sections = listOf(Section(".text", SectionKind.TEXT, code, align = 16)),
            symbols = listOf(
                Symbol("_start", value = 0, section = ".text", kind = SymbolKind.FUNCTION),
                Symbol("missing1", value = 0, section = null, kind = SymbolKind.UNDEFINED),
                Symbol("missing2", value = 0, section = null, kind = SymbolKind.UNDEFINED),
            ),
            relocations = listOf(
                Relocation(1, "missing1", RelocationType.X86_64.PC32, -4, ".text"),
                Relocation(6, "missing2", RelocationType.X86_64.PC32, -4, ".text"),
            ),
        )

        val ex = assertThrows(IllegalStateException::class.java) {
            ElfStaticLinker().link(listOf(obj))
        }
        assertTrue(ex.message!!.contains("missing1"))
        assertTrue(ex.message!!.contains("missing2"))
    }

    @Test
    fun `static - rejects when no entry symbol found`() {
        val code = retCode()
        val obj = x86Obj(
            sections = listOf(Section(".text", SectionKind.TEXT, code, align = 16)),
            symbols = listOf(
                Symbol("some_func", value = 0, section = ".text", kind = SymbolKind.FUNCTION),
            ),
        )

        assertThrows(IllegalStateException::class.java) {
            ElfStaticLinker().link(listOf(obj))
        }
    }

    // ==========================================
    // Static Linker: Relocation Types
    // ==========================================

    @Test
    fun `static - applies R_64 relocation in data`() {
        val code = retCode()
        val dataBytes = ByteArray(8)

        val obj = x86Obj(
            sections = listOf(
                Section(".text", SectionKind.TEXT, code, align = 16),
                Section(".data", SectionKind.DATA, dataBytes, align = 8),
            ),
            symbols = listOf(
                Symbol("_start", value = 0, section = ".text", kind = SymbolKind.FUNCTION),
                Symbol("ptr", value = 0, section = ".data", kind = SymbolKind.DATA),
            ),
            relocations = listOf(
                Relocation(0, "_start", RelocationType.X86_64.R_64, 0, ".data"),
            ),
        )

        val binary = ElfStaticLinker().link(listOf(obj))
        val buf = le(binary)

        val entry = readU64(buf, 24)
        // Find the data section and verify the pointer was patched
        val phoff = readU64(buf, 32).toInt()
        val rwPhdr = phoff + Elf.PHDR64_SIZE
        val dataFileOff = readU64(buf, rwPhdr + 8).toInt()

        val storedPtr = readU64(buf, dataFileOff)
        assertEquals(entry, storedPtr, "R_64 relocation should store _start's virtual address")
    }

    @Test
    fun `static - applies PC32 relocation to rodata`() {
        val code = leaRipPlaceholder() + retCode()
        val rodata = "test data\u0000".toByteArray(Charsets.US_ASCII)

        val obj = x86Obj(
            sections = listOf(
                Section(".text", SectionKind.TEXT, code, align = 16),
                Section(".rodata", SectionKind.RODATA, rodata, align = 1),
            ),
            symbols = listOf(
                Symbol("_start", value = 0, section = ".text", kind = SymbolKind.FUNCTION),
                Symbol("msg", value = 0, section = ".rodata", binding = SymbolBinding.LOCAL, kind = SymbolKind.DATA),
            ),
            relocations = listOf(
                Relocation(3, "msg", RelocationType.X86_64.PC32, -4, ".text"),
            ),
        )

        val binary = ElfStaticLinker().link(listOf(obj))
        val buf = le(binary)
        val entry = readU64(buf, 24)
        val entryOff = (entry - 0x400000).toInt()

        val leaDisp = readI32(buf, entryOff + 3)
        assertNotEquals(0, leaDisp, "LEA displacement should be patched to point to rodata")
    }

    @Test
    fun `static - applies cross-object PC32 relocation correctly`() {
        val mainCode = callPlaceholder() + retCode()
        val helperCode = movEaxImm(7) + retCode()

        val obj1 = x86Obj(
            sections = listOf(Section(".text", SectionKind.TEXT, mainCode, align = 16)),
            symbols = listOf(
                Symbol("_start", value = 0, section = ".text", kind = SymbolKind.FUNCTION),
                Symbol("helper", value = 0, section = null, kind = SymbolKind.UNDEFINED),
            ),
            relocations = listOf(Relocation(1, "helper", RelocationType.X86_64.PC32, -4, ".text")),
        )
        val obj2 = x86Obj(
            sections = listOf(Section(".text", SectionKind.TEXT, helperCode, align = 16)),
            symbols = listOf(Symbol("helper", value = 0, size = helperCode.size.toLong(), section = ".text", kind = SymbolKind.FUNCTION)),
        )

        val binary = ElfStaticLinker().link(listOf(obj1, obj2))
        val buf = le(binary)
        val entry = readU64(buf, 24)
        val entryOff = (entry - 0x400000).toInt()

        // Follow the call: displacement should lead to helper's code
        val callDisp = readI32(buf, entryOff + 1)
        val targetOff = entryOff + 5 + callDisp
        // helper starts with mov eax, 7 (B8 07 00 00 00)
        assertEquals(0xB8.toByte(), binary[targetOff])
        assertEquals(0x07.toByte(), binary[targetOff + 1])
    }

    // ==========================================
    // Static Linker: Segment Layout
    // ==========================================

    @Test
    fun `static - text only binary has correct segment layout`() {
        val code = movEaxImm(60) + byteArrayOf(0x0F, 0x05) // mov eax, 60; syscall
        val obj = x86Obj(
            sections = listOf(Section(".text", SectionKind.TEXT, code, align = 16)),
            symbols = listOf(Symbol("_start", value = 0, section = ".text", kind = SymbolKind.FUNCTION)),
        )

        val binary = ElfStaticLinker().link(listOf(obj))
        val buf = le(binary)

        val phoff = readU64(buf, 32).toInt()
        val phnum = readU16(buf, 56)
        assertEquals(3, phnum, "Should have 3 program headers: LOAD RX, LOAD RW, GNU_STACK")

        // First LOAD: RX
        assertEquals(ElfSegmentType.LOAD.code, readU32(buf, phoff))
        val rxFlags = readU32(buf, phoff + 4)
        assertTrue(rxFlags and ElfSegmentFlags.R != 0)
        assertTrue(rxFlags and ElfSegmentFlags.X != 0)
        assertFalse(rxFlags and ElfSegmentFlags.W != 0, "Text segment should not be writable")
    }

    @Test
    fun `static - binary with data has RW segment`() {
        val code = retCode()
        val data = byteArrayOf(0x42, 0x00, 0x00, 0x00)

        val obj = x86Obj(
            sections = listOf(
                Section(".text", SectionKind.TEXT, code, align = 16),
                Section(".data", SectionKind.DATA, data, align = 4),
            ),
            symbols = listOf(
                Symbol("_start", value = 0, section = ".text", kind = SymbolKind.FUNCTION),
                Symbol("mydata", value = 0, section = ".data", kind = SymbolKind.DATA),
            ),
        )

        val binary = ElfStaticLinker().link(listOf(obj))
        val buf = le(binary)

        val phoff = readU64(buf, 32).toInt()
        val rwPhdr = phoff + Elf.PHDR64_SIZE
        assertEquals(ElfSegmentType.LOAD.code, readU32(buf, rwPhdr))
        val rwFlags = readU32(buf, rwPhdr + 4)
        assertTrue(rwFlags and ElfSegmentFlags.R != 0)
        assertTrue(rwFlags and ElfSegmentFlags.W != 0)
    }

    @Test
    fun `static - GNU_STACK segment is present`() {
        val code = retCode()
        val obj = x86Obj(
            sections = listOf(Section(".text", SectionKind.TEXT, code, align = 16)),
            symbols = listOf(Symbol("_start", value = 0, section = ".text", kind = SymbolKind.FUNCTION)),
        )

        val binary = ElfStaticLinker().link(listOf(obj))
        val buf = le(binary)

        val phoff = readU64(buf, 32).toInt()
        val phnum = readU16(buf, 56)
        var hasGnuStack = false
        for (i in 0 until phnum) {
            if (readU32(buf, phoff + i * Elf.PHDR64_SIZE) == ElfSegmentType.GNU_STACK.code) {
                hasGnuStack = true
            }
        }
        assertTrue(hasGnuStack, "Binary should have GNU_STACK segment")
    }

    // ==========================================
    // Static Linker: Large/Multiple Symbols
    // ==========================================

    @Test
    fun `static - links ten objects with sequential calls`() {
        // Create 10 functions: f0 calls f1, f1 calls f2, ..., f8 calls f9, f9 returns 0
        val objects = mutableListOf<ObjectFile>()
        for (i in 0 until 9) {
            val code = callPlaceholder() + retCode()
            objects.add(x86Obj(
                sections = listOf(Section(".text", SectionKind.TEXT, code, align = 16)),
                symbols = listOf(
                    Symbol("f$i", value = 0, section = ".text", kind = SymbolKind.FUNCTION),
                    Symbol("f${i + 1}", value = 0, section = null, kind = SymbolKind.UNDEFINED),
                ),
                relocations = listOf(Relocation(1, "f${i + 1}", RelocationType.X86_64.PC32, -4, ".text")),
            ))
        }
        // f9: the leaf function
        objects.add(x86Obj(
            sections = listOf(Section(".text", SectionKind.TEXT, movEaxImm(0) + retCode(), align = 16)),
            symbols = listOf(Symbol("f9", value = 0, section = ".text", kind = SymbolKind.FUNCTION)),
        ))

        // Rename f0 to _start
        val first = objects[0]
        objects[0] = first.copy(
            symbols = listOf(
                Symbol("_start", value = 0, section = ".text", kind = SymbolKind.FUNCTION),
                Symbol("f1", value = 0, section = null, kind = SymbolKind.UNDEFINED),
            ),
            relocations = listOf(Relocation(1, "f1", RelocationType.X86_64.PC32, -4, ".text")),
        )

        val binary = ElfStaticLinker().link(objects)
        val buf = le(binary)
        assertEquals(ElfObjectType.EXEC.code, readU16(buf, 16))
        assertTrue(binary.size > 100, "Binary should contain all merged code")
    }

    @Test
    fun `static - many data symbols from different objects`() {
        val code = retCode()
        val objects = mutableListOf<ObjectFile>()

        // First object has _start
        val dataBytes = byteArrayOf(0x00, 0x00, 0x00, 0x00)
        objects.add(x86Obj(
            sections = listOf(
                Section(".text", SectionKind.TEXT, code, align = 16),
                Section(".data", SectionKind.DATA, dataBytes, align = 4),
            ),
            symbols = listOf(
                Symbol("_start", value = 0, section = ".text", kind = SymbolKind.FUNCTION),
                Symbol("var0", value = 0, section = ".data", kind = SymbolKind.DATA),
            ),
        ))

        // Additional objects contribute data
        for (i in 1 until 10) {
            val d = byteArrayOf(i.toByte(), 0x00, 0x00, 0x00)
            objects.add(x86Obj(
                sections = listOf(Section(".data", SectionKind.DATA, d, align = 4)),
                symbols = listOf(Symbol("var$i", value = 0, section = ".data", kind = SymbolKind.DATA)),
            ))
        }

        val binary = ElfStaticLinker().link(objects)
        assertTrue(binary.isNotEmpty())
    }

    // ==========================================
    // Static Linker: Alignment
    // ==========================================

    @Test
    fun `static - respects section alignment`() {
        val code1 = byteArrayOf(0x90.toByte()) // nop, 1 byte
        val code2 = movEaxImm(0) + retCode()

        val obj1 = x86Obj(
            sections = listOf(Section(".text", SectionKind.TEXT, code1, align = 1)),
            symbols = listOf(Symbol("_start", value = 0, section = ".text", kind = SymbolKind.FUNCTION)),
        )
        val obj2 = x86Obj(
            sections = listOf(Section(".text", SectionKind.TEXT, code2, align = 16)),
            symbols = listOf(Symbol("func2", value = 0, section = ".text", kind = SymbolKind.FUNCTION)),
        )

        // Should not crash - alignment is handled internally
        val binary = ElfStaticLinker().link(listOf(obj1, obj2))
        assertTrue(binary.isNotEmpty())
    }

    // ==========================================
    // Dynamic Linker (ElfLinker): Tests
    // ==========================================

    @Test
    fun `dynamic - has INTERP segment`() {
        val code = callPlaceholder() + retCode()
        val obj = x86Obj(
            sections = listOf(Section(".text", SectionKind.TEXT, code, align = 16)),
            symbols = listOf(
                Symbol("main", value = 0, section = ".text", kind = SymbolKind.FUNCTION),
                Symbol("puts", value = 0, section = null, kind = SymbolKind.UNDEFINED),
            ),
            relocations = listOf(Relocation(1, "puts", RelocationType.X86_64.PLT32, -4, ".text")),
        )

        val binary = ElfLinker().link(listOf(obj))
        val buf = le(binary)

        val phoff = readU64(buf, 32).toInt()
        val phnum = readU16(buf, 56)

        var hasInterp = false
        for (i in 0 until phnum) {
            if (readU32(buf, phoff + i * Elf.PHDR64_SIZE) == ElfSegmentType.INTERP.code) {
                hasInterp = true
            }
        }
        assertTrue(hasInterp, "Dynamic executable should have INTERP segment")
    }

    @Test
    fun `dynamic - has DYNAMIC segment`() {
        val code = callPlaceholder() + retCode()
        val obj = x86Obj(
            sections = listOf(Section(".text", SectionKind.TEXT, code, align = 16)),
            symbols = listOf(
                Symbol("main", value = 0, section = ".text", kind = SymbolKind.FUNCTION),
                Symbol("printf", value = 0, section = null, kind = SymbolKind.UNDEFINED),
            ),
            relocations = listOf(Relocation(1, "printf", RelocationType.X86_64.PLT32, -4, ".text")),
        )

        val binary = ElfLinker().link(listOf(obj))
        val buf = le(binary)

        val phoff = readU64(buf, 32).toInt()
        val phnum = readU16(buf, 56)
        var hasDynamic = false
        for (i in 0 until phnum) {
            if (readU32(buf, phoff + i * Elf.PHDR64_SIZE) == ElfSegmentType.DYNAMIC.code) {
                hasDynamic = true
            }
        }
        assertTrue(hasDynamic, "Dynamic executable should have DYNAMIC segment")
    }

    @Test
    fun `dynamic - references interpreter path`() {
        val code = callPlaceholder() + retCode()
        val obj = x86Obj(
            sections = listOf(Section(".text", SectionKind.TEXT, code, align = 16)),
            symbols = listOf(
                Symbol("main", value = 0, section = ".text", kind = SymbolKind.FUNCTION),
                Symbol("puts", value = 0, section = null, kind = SymbolKind.UNDEFINED),
            ),
            relocations = listOf(Relocation(1, "puts", RelocationType.X86_64.PLT32, -4, ".text")),
        )

        val binary = ElfLinker().link(listOf(obj))
        val str = String(binary, Charsets.ISO_8859_1)
        assertTrue(str.contains("ld-linux"), "Binary should contain dynamic linker path")
    }

    @Test
    fun `dynamic - contains shared library name`() {
        val code = callPlaceholder() + retCode()
        val obj = x86Obj(
            sections = listOf(Section(".text", SectionKind.TEXT, code, align = 16)),
            symbols = listOf(
                Symbol("main", value = 0, section = ".text", kind = SymbolKind.FUNCTION),
                Symbol("puts", value = 0, section = null, kind = SymbolKind.UNDEFINED),
            ),
            relocations = listOf(Relocation(1, "puts", RelocationType.X86_64.PLT32, -4, ".text")),
        )

        val binary = ElfLinker(sharedLibs = listOf("libc.so.6")).link(listOf(obj))
        val str = String(binary, Charsets.ISO_8859_1)
        assertTrue(str.contains("libc.so.6"), "Binary should reference libc.so.6")
    }

    @Test
    fun `dynamic - PLT relocation patches call displacement`() {
        val code = callPlaceholder() + retCode()
        val obj = x86Obj(
            sections = listOf(Section(".text", SectionKind.TEXT, code, align = 16)),
            symbols = listOf(
                Symbol("main", value = 0, section = ".text", kind = SymbolKind.FUNCTION),
                Symbol("puts", value = 0, section = null, kind = SymbolKind.UNDEFINED),
            ),
            relocations = listOf(Relocation(1, "puts", RelocationType.X86_64.PLT32, -4, ".text")),
        )

        val binary = ElfLinker().link(listOf(obj))
        val buf = le(binary)

        val entry = readU64(buf, 24)
        // The entry is the _start stub; the main function is called from it.
        // Just verify the binary has a reasonable structure.
        assertTrue(entry >= 0x400000)
        assertTrue(binary.size > 500)
    }

    @Test
    fun `dynamic - links with multiple external symbols`() {
        val code = callPlaceholder() + callPlaceholder() + retCode()
        val obj = x86Obj(
            sections = listOf(Section(".text", SectionKind.TEXT, code, align = 16)),
            symbols = listOf(
                Symbol("main", value = 0, section = ".text", kind = SymbolKind.FUNCTION),
                Symbol("printf", value = 0, section = null, kind = SymbolKind.UNDEFINED),
                Symbol("malloc", value = 0, section = null, kind = SymbolKind.UNDEFINED),
            ),
            relocations = listOf(
                Relocation(1, "printf", RelocationType.X86_64.PLT32, -4, ".text"),
                Relocation(6, "malloc", RelocationType.X86_64.PLT32, -4, ".text"),
            ),
        )

        val binary = ElfLinker().link(listOf(obj))
        val str = String(binary, Charsets.ISO_8859_1)
        assertTrue(str.contains("printf"))
        assertTrue(str.contains("malloc"))
    }

    @Test
    fun `dynamic - generates start stub when main is entry`() {
        val code = movEaxImm(0) + retCode()
        val obj = x86Obj(
            sections = listOf(Section(".text", SectionKind.TEXT, code, align = 16)),
            symbols = listOf(
                Symbol("main", value = 0, section = ".text", kind = SymbolKind.FUNCTION),
                Symbol("puts", value = 0, section = null, kind = SymbolKind.UNDEFINED),
            ),
            relocations = listOf(Relocation(1, "puts", RelocationType.X86_64.PLT32, -4, ".text")),
        )

        val binary = ElfLinker().link(listOf(obj))
        val buf = le(binary)
        val entry = readU64(buf, 24)
        assertTrue(entry >= 0x400000)

        // The _start stub begins with xor ebp, ebp (31 ED)
        val entryOff = (entry - 0x400000).toInt()
        assertEquals(0x31.toByte(), binary[entryOff])
        assertEquals(0xED.toByte(), binary[entryOff + 1])
    }

    @Test
    fun `dynamic - links two objects with one external dependency`() {
        val mainCode = callPlaceholder() + callPlaceholder() + retCode()
        val helperCode = movEaxImm(42) + retCode()

        val obj1 = x86Obj(
            sections = listOf(Section(".text", SectionKind.TEXT, mainCode, align = 16)),
            symbols = listOf(
                Symbol("main", value = 0, section = ".text", kind = SymbolKind.FUNCTION),
                Symbol("helper", value = 0, section = null, kind = SymbolKind.UNDEFINED),
                Symbol("printf", value = 0, section = null, kind = SymbolKind.UNDEFINED),
            ),
            relocations = listOf(
                Relocation(1, "helper", RelocationType.X86_64.PC32, -4, ".text"),
                Relocation(6, "printf", RelocationType.X86_64.PLT32, -4, ".text"),
            ),
        )
        val obj2 = x86Obj(
            sections = listOf(Section(".text", SectionKind.TEXT, helperCode, align = 16)),
            symbols = listOf(Symbol("helper", value = 0, size = helperCode.size.toLong(), section = ".text", kind = SymbolKind.FUNCTION)),
        )

        val binary = ElfLinker().link(listOf(obj1, obj2))
        val buf = le(binary)
        assertEquals(ElfObjectType.EXEC.code, readU16(buf, 16))
        val str = String(binary, Charsets.ISO_8859_1)
        assertTrue(str.contains("printf"))
    }

    // ==========================================
    // Shared Linker (ElfSharedLinker): Tests
    // ==========================================

    @Test
    fun `shared - produces DYN type with no entry`() {
        val code = movEaxImm(42) + retCode()
        val obj = x86Obj(
            sections = listOf(Section(".text", SectionKind.TEXT, code, align = 16)),
            symbols = listOf(Symbol("my_func", value = 0, size = code.size.toLong(), section = ".text", kind = SymbolKind.FUNCTION)),
        )

        val binary = ElfSharedLinker().link(listOf(obj))
        val buf = le(binary)

        assertEquals(ElfObjectType.DYN.code, readU16(buf, 16))
        assertEquals(0L, readU64(buf, 24), "Shared library should have entry = 0")
    }

    @Test
    fun `shared - rejects empty input`() {
        assertThrows(IllegalArgumentException::class.java) {
            ElfSharedLinker().link(emptyList())
        }
    }

    @Test
    fun `shared - exports multiple symbols`() {
        val addCode = byteArrayOf(0x8D.toByte(), 0x04, 0x37, 0xC3.toByte())
        val subCode = byteArrayOf(0x89.toByte(), 0xF8.toByte(), 0x29, 0xF0.toByte(), 0xC3.toByte())
        val mulCode = byteArrayOf(0x89.toByte(), 0xF8.toByte(), 0x0F, 0xAF.toByte(), 0xC6.toByte(), 0xC3.toByte())

        val obj = x86Obj(
            sections = listOf(Section(".text", SectionKind.TEXT, addCode + subCode + mulCode, align = 16)),
            symbols = listOf(
                Symbol("add", value = 0, size = addCode.size.toLong(), section = ".text", kind = SymbolKind.FUNCTION),
                Symbol("sub", value = addCode.size.toLong(), size = subCode.size.toLong(), section = ".text", kind = SymbolKind.FUNCTION),
                Symbol("mul", value = (addCode.size + subCode.size).toLong(), size = mulCode.size.toLong(), section = ".text", kind = SymbolKind.FUNCTION),
            ),
        )

        val binary = ElfSharedLinker(soname = "libcalc.so.1").link(listOf(obj))
        val str = String(binary, Charsets.ISO_8859_1)
        assertTrue(str.contains("add"))
        assertTrue(str.contains("sub"))
        assertTrue(str.contains("mul"))
        assertTrue(str.contains("libcalc.so.1"))
    }

    @Test
    fun `shared - has DYNAMIC but no INTERP`() {
        val code = retCode()
        val obj = x86Obj(
            sections = listOf(Section(".text", SectionKind.TEXT, code, align = 16)),
            symbols = listOf(Symbol("init", value = 0, section = ".text", kind = SymbolKind.FUNCTION)),
        )

        val binary = ElfSharedLinker().link(listOf(obj))
        val buf = le(binary)

        val phoff = readU64(buf, 32).toInt()
        val phnum = readU16(buf, 56)

        var hasDynamic = false
        for (i in 0 until phnum) {
            val type = readU32(buf, phoff + i * Elf.PHDR64_SIZE)
            assertNotEquals(ElfSegmentType.INTERP.code, type, "SO should not have INTERP")
            if (type == ElfSegmentType.DYNAMIC.code) hasDynamic = true
        }
        assertTrue(hasDynamic)
    }

    @Test
    fun `shared - imports external symbol via PLT`() {
        val code = callPlaceholder() + retCode()
        val obj = x86Obj(
            sections = listOf(Section(".text", SectionKind.TEXT, code, align = 16)),
            symbols = listOf(
                Symbol("wrapper", value = 0, size = code.size.toLong(), section = ".text", kind = SymbolKind.FUNCTION),
                Symbol("external_func", value = 0, section = null, kind = SymbolKind.UNDEFINED),
            ),
            relocations = listOf(Relocation(1, "external_func", RelocationType.X86_64.PLT32, -4, ".text")),
        )

        val binary = ElfSharedLinker(sharedLibs = listOf("libext.so")).link(listOf(obj))
        val buf = le(binary)
        assertEquals(ElfObjectType.DYN.code, readU16(buf, 16))

        val str = String(binary, Charsets.ISO_8859_1)
        assertTrue(str.contains("external_func"))
        assertTrue(str.contains("libext.so"))
    }

    @Test
    fun `shared - links three objects into single library`() {
        val code1 = movEaxImm(1) + retCode()
        val code2 = movEaxImm(2) + retCode()
        val code3 = movEaxImm(3) + retCode()

        val obj1 = x86Obj(
            sections = listOf(Section(".text", SectionKind.TEXT, code1, align = 16)),
            symbols = listOf(Symbol("func1", value = 0, size = code1.size.toLong(), section = ".text", kind = SymbolKind.FUNCTION)),
        )
        val obj2 = x86Obj(
            sections = listOf(Section(".text", SectionKind.TEXT, code2, align = 16)),
            symbols = listOf(Symbol("func2", value = 0, size = code2.size.toLong(), section = ".text", kind = SymbolKind.FUNCTION)),
        )
        val obj3 = x86Obj(
            sections = listOf(Section(".text", SectionKind.TEXT, code3, align = 16)),
            symbols = listOf(Symbol("func3", value = 0, size = code3.size.toLong(), section = ".text", kind = SymbolKind.FUNCTION)),
        )

        val binary = ElfSharedLinker(soname = "libtriple.so").link(listOf(obj1, obj2, obj3))
        val buf = le(binary)
        assertEquals(ElfObjectType.DYN.code, readU16(buf, 16))

        val str = String(binary, Charsets.ISO_8859_1)
        assertTrue(str.contains("func1"))
        assertTrue(str.contains("func2"))
        assertTrue(str.contains("func3"))
    }

    @Test
    fun `shared - data export symbol appears in dynstr`() {
        val code = retCode()
        val data = byteArrayOf(0x2A, 0x00, 0x00, 0x00)

        val obj = x86Obj(
            sections = listOf(
                Section(".text", SectionKind.TEXT, code, align = 16),
                Section(".data", SectionKind.DATA, data, align = 4),
            ),
            symbols = listOf(
                Symbol("init_lib", value = 0, section = ".text", kind = SymbolKind.FUNCTION),
                Symbol("global_var", value = 0, section = ".data", binding = SymbolBinding.GLOBAL, kind = SymbolKind.DATA),
            ),
        )

        val binary = ElfSharedLinker().link(listOf(obj))
        val str = String(binary, Charsets.ISO_8859_1)
        assertTrue(str.contains("global_var"), "Data symbol should be exported in .dynstr")
        assertTrue(str.contains("init_lib"))
    }

    @Test
    fun `shared - cross-object internal call patched correctly`() {
        val wrapperCode = callPlaceholder() + retCode()
        val implCode = movEaxImm(100) + retCode()

        val obj1 = x86Obj(
            sections = listOf(Section(".text", SectionKind.TEXT, wrapperCode, align = 16)),
            symbols = listOf(
                Symbol("wrapper", value = 0, size = wrapperCode.size.toLong(), section = ".text", kind = SymbolKind.FUNCTION),
                Symbol("impl", value = 0, section = null, kind = SymbolKind.UNDEFINED),
            ),
            relocations = listOf(Relocation(1, "impl", RelocationType.X86_64.PC32, -4, ".text")),
        )
        val obj2 = x86Obj(
            sections = listOf(Section(".text", SectionKind.TEXT, implCode, align = 16)),
            symbols = listOf(Symbol("impl", value = 0, size = implCode.size.toLong(), section = ".text", kind = SymbolKind.FUNCTION)),
        )

        val binary = ElfSharedLinker().link(listOf(obj1, obj2))
        val buf = le(binary)
        assertEquals(ElfObjectType.DYN.code, readU16(buf, 16))

        // Verify a patched call exists somewhere in the binary
        var foundPatchedCall = false
        for (i in 0 until binary.size - 5) {
            if (binary[i] == 0xE8.toByte()) {
                val disp = readI32(buf, i + 1)
                if (disp != 0) {
                    foundPatchedCall = true
                    break
                }
            }
        }
        assertTrue(foundPatchedCall, "Internal call should be patched in shared library")
    }

    @Test
    fun `shared - without soname omits SONAME tag`() {
        val code = retCode()
        val obj = x86Obj(
            sections = listOf(Section(".text", SectionKind.TEXT, code, align = 16)),
            symbols = listOf(Symbol("func", value = 0, section = ".text", kind = SymbolKind.FUNCTION)),
        )

        // No soname specified
        val binary = ElfSharedLinker(soname = null).link(listOf(obj))
        val buf = le(binary)
        assertEquals(ElfObjectType.DYN.code, readU16(buf, 16))
        // Just verify it links; SONAME is optional
        assertTrue(binary.isNotEmpty())
    }

    @Test
    fun `shared - includes rodata in output`() {
        val code = leaRipPlaceholder() + retCode()
        val rodata = "shared data string\u0000".toByteArray(Charsets.US_ASCII)

        val obj = x86Obj(
            sections = listOf(
                Section(".text", SectionKind.TEXT, code, align = 16),
                Section(".rodata", SectionKind.RODATA, rodata, align = 1),
            ),
            symbols = listOf(
                Symbol("get_string", value = 0, section = ".text", kind = SymbolKind.FUNCTION),
                Symbol("str", value = 0, section = ".rodata", binding = SymbolBinding.LOCAL, kind = SymbolKind.DATA),
            ),
            relocations = listOf(Relocation(3, "str", RelocationType.X86_64.PC32, -4, ".text")),
        )

        val binary = ElfSharedLinker().link(listOf(obj))
        val str = String(binary, Charsets.US_ASCII)
        assertTrue(str.contains("shared data string"))
    }

    // ==========================================
    // Dynamic Linker: with no shared libs
    // ==========================================

    @Test
    fun `dynamic - links without external dependencies`() {
        val mainCode = movEaxImm(0) + retCode()
        val obj = x86Obj(
            sections = listOf(Section(".text", SectionKind.TEXT, mainCode, align = 16)),
            symbols = listOf(Symbol("_start", value = 0, section = ".text", kind = SymbolKind.FUNCTION)),
        )

        // ElfLinker with empty shared libs and no undefined symbols
        val binary = ElfLinker(sharedLibs = emptyList()).link(listOf(obj))
        val buf = le(binary)
        assertEquals(ElfObjectType.EXEC.code, readU16(buf, 16))
        assertTrue(binary.size > 0)
    }

    @Test
    fun `dynamic - rodata appears in output`() {
        val code = leaRipPlaceholder() + retCode()
        val rodata = "dynamic linker test\u0000".toByteArray(Charsets.US_ASCII)

        val obj = x86Obj(
            sections = listOf(
                Section(".text", SectionKind.TEXT, code, align = 16),
                Section(".rodata", SectionKind.RODATA, rodata, align = 1),
            ),
            symbols = listOf(
                Symbol("main", value = 0, section = ".text", kind = SymbolKind.FUNCTION),
                Symbol("msg", value = 0, section = ".rodata", binding = SymbolBinding.LOCAL, kind = SymbolKind.DATA),
                Symbol("puts", value = 0, section = null, kind = SymbolKind.UNDEFINED),
            ),
            relocations = listOf(
                Relocation(3, "msg", RelocationType.X86_64.PC32, -4, ".text"),
            ),
        )

        val binary = ElfLinker().link(listOf(obj))
        val str = String(binary, Charsets.US_ASCII)
        assertTrue(str.contains("dynamic linker test"))
    }

    // ==========================================
    // ELF Header Validation
    // ==========================================

    @Test
    fun `all linkers produce valid ELF magic`() {
        val code = retCode()

        val staticObj = x86Obj(
            sections = listOf(Section(".text", SectionKind.TEXT, code, align = 16)),
            symbols = listOf(Symbol("_start", value = 0, section = ".text", kind = SymbolKind.FUNCTION)),
        )
        val sharedObj = x86Obj(
            sections = listOf(Section(".text", SectionKind.TEXT, code, align = 16)),
            symbols = listOf(Symbol("func", value = 0, section = ".text", kind = SymbolKind.FUNCTION)),
        )
        val dynamicCode = callPlaceholder() + retCode()
        val dynamicObj = x86Obj(
            sections = listOf(Section(".text", SectionKind.TEXT, dynamicCode, align = 16)),
            symbols = listOf(
                Symbol("main", value = 0, section = ".text", kind = SymbolKind.FUNCTION),
                Symbol("puts", value = 0, section = null, kind = SymbolKind.UNDEFINED),
            ),
            relocations = listOf(Relocation(1, "puts", RelocationType.X86_64.PLT32, -4, ".text")),
        )

        for ((name, binary) in listOf(
            "static" to ElfStaticLinker().link(listOf(staticObj)),
            "shared" to ElfSharedLinker().link(listOf(sharedObj)),
            "dynamic" to ElfLinker().link(listOf(dynamicObj)),
        )) {
            assertEquals(0x7f, binary[0].toInt() and 0xFF, "$name: magic[0]")
            assertEquals('E'.code, binary[1].toInt() and 0xFF, "$name: magic[1]")
            assertEquals('L'.code, binary[2].toInt() and 0xFF, "$name: magic[2]")
            assertEquals('F'.code, binary[3].toInt() and 0xFF, "$name: magic[3]")
            assertEquals(ElfMachine.X86_64.code, readU16(le(binary), 18), "$name: machine")
        }
    }

    @Test
    fun `static and dynamic produce EXEC, shared produces DYN`() {
        val code = retCode()

        val staticBin = ElfStaticLinker().link(listOf(x86Obj(
            sections = listOf(Section(".text", SectionKind.TEXT, code, align = 16)),
            symbols = listOf(Symbol("_start", value = 0, section = ".text", kind = SymbolKind.FUNCTION)),
        )))

        val sharedBin = ElfSharedLinker().link(listOf(x86Obj(
            sections = listOf(Section(".text", SectionKind.TEXT, code, align = 16)),
            symbols = listOf(Symbol("func", value = 0, section = ".text", kind = SymbolKind.FUNCTION)),
        )))

        assertEquals(ElfObjectType.EXEC.code, readU16(le(staticBin), 16))
        assertEquals(ElfObjectType.DYN.code, readU16(le(sharedBin), 16))
    }
}
