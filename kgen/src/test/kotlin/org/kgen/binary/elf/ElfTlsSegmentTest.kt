package org.kgen.binary.elf

import org.kgen.binary.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ElfTlsSegmentTest {

    private fun le(bytes: ByteArray) = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    private fun readU16(buf: ByteBuffer, off: Int): Int = buf.getShort(off).toInt() and 0xFFFF
    private fun readU32(buf: ByteBuffer, off: Int): Int = buf.getInt(off)
    private fun readU64(buf: ByteBuffer, off: Int): Long = buf.getLong(off)

    private fun findPhdr(buf: ByteBuffer, phoff: Int, phnum: Int, type: Int): Int {
        for (i in 0 until phnum) {
            val off = phoff + i * Elf.PHDR64_SIZE
            if (readU32(buf, off) == type) return off
        }
        return -1
    }

    private fun makeObjWithTdata(tdataBytes: ByteArray, tdataAlign: Int = 4): ObjectFile {
        val code = byteArrayOf(0xC3.toByte()) // ret
        return ObjectFile(
            format = ObjectFormat.ELF,
            arch = Architecture(ArchType.X86_64),
            sections = listOf(
                Section(".text", SectionKind.TEXT, code, align = 16),
                Section(".tdata", SectionKind.TDATA, tdataBytes, align = tdataAlign),
            ),
            symbols = listOf(
                Symbol("_start", value = 0, size = 1, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
            relocations = emptyList(),
        )
    }

    private fun makeObjWithoutTdata(): ObjectFile {
        val code = byteArrayOf(0xC3.toByte())
        return ObjectFile(
            format = ObjectFormat.ELF,
            arch = Architecture(ArchType.X86_64),
            sections = listOf(
                Section(".text", SectionKind.TEXT, code, align = 16),
            ),
            symbols = listOf(
                Symbol("_start", value = 0, size = 1, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
            relocations = emptyList(),
        )
    }

    @Test
    fun ptTlsPresentWhenTdataExists() {
        val obj = makeObjWithTdata(ByteArray(4) { 0x42 })
        val binary = ElfStaticLinker().link(listOf(obj))
        val buf = le(binary)

        val phoff = readU64(buf, 32).toInt()
        val phnum = readU16(buf, 56)
        val tlsOff = findPhdr(buf, phoff, phnum, ElfSegmentType.TLS.code)
        assertTrue(tlsOff >= 0, "PT_TLS program header should be present when .tdata exists")
    }

    @Test
    fun ptTlsAbsentWhenNoTdata() {
        val obj = makeObjWithoutTdata()
        val binary = ElfStaticLinker().link(listOf(obj))
        val buf = le(binary)

        val phoff = readU64(buf, 32).toInt()
        val phnum = readU16(buf, 56)
        val tlsOff = findPhdr(buf, phoff, phnum, ElfSegmentType.TLS.code)
        assertEquals(-1, tlsOff, "PT_TLS should not be present when there is no .tdata")
    }

    @Test
    fun ptTlsHasCorrectFileSize() {
        val tdataContent = ByteArray(32) { i -> i.toByte() }
        val obj = makeObjWithTdata(tdataContent)
        val binary = ElfStaticLinker().link(listOf(obj))
        val buf = le(binary)

        val phoff = readU64(buf, 32).toInt()
        val phnum = readU16(buf, 56)
        val tlsOff = findPhdr(buf, phoff, phnum, ElfSegmentType.TLS.code)
        assertTrue(tlsOff >= 0)

        val filesz = readU64(buf, tlsOff + 32)
        assertEquals(32L, filesz, "PT_TLS filesz should match .tdata size")
    }

    @Test
    fun ptTlsHasCorrectMemSize() {
        val tdataContent = ByteArray(16) { 0xFF.toByte() }
        val obj = makeObjWithTdata(tdataContent)
        val binary = ElfStaticLinker().link(listOf(obj))
        val buf = le(binary)

        val phoff = readU64(buf, 32).toInt()
        val phnum = readU16(buf, 56)
        val tlsOff = findPhdr(buf, phoff, phnum, ElfSegmentType.TLS.code)
        assertTrue(tlsOff >= 0)

        val memsz = readU64(buf, tlsOff + 40)
        assertTrue(memsz >= 16, "PT_TLS memsz should be >= tdata size")
    }

    @Test
    fun ptTlsHasReadableFlag() {
        val obj = makeObjWithTdata(ByteArray(8))
        val binary = ElfStaticLinker().link(listOf(obj))
        val buf = le(binary)

        val phoff = readU64(buf, 32).toInt()
        val phnum = readU16(buf, 56)
        val tlsOff = findPhdr(buf, phoff, phnum, ElfSegmentType.TLS.code)
        assertTrue(tlsOff >= 0)

        val flags = readU32(buf, tlsOff + 4)
        assertTrue(flags and ElfSegmentFlags.R != 0, "PT_TLS should have R flag set")
    }

    @Test
    fun ptTlsHasCorrectAlignment() {
        val obj = makeObjWithTdata(ByteArray(8), tdataAlign = 8)
        val binary = ElfStaticLinker().link(listOf(obj))
        val buf = le(binary)

        val phoff = readU64(buf, 32).toInt()
        val phnum = readU16(buf, 56)
        val tlsOff = findPhdr(buf, phoff, phnum, ElfSegmentType.TLS.code)
        assertTrue(tlsOff >= 0)

        val align = readU64(buf, tlsOff + 48)
        assertEquals(8L, align, "PT_TLS alignment should match tdata alignment")
    }

    @Test
    fun phdrCountIncreasesWithTdata() {
        val objNo = makeObjWithoutTdata()
        val objYes = makeObjWithTdata(ByteArray(4))

        val binNo = ElfStaticLinker().link(listOf(objNo))
        val binYes = ElfStaticLinker().link(listOf(objYes))

        val phNumNo = readU16(le(binNo), 56)
        val phNumYes = readU16(le(binYes), 56)
        assertEquals(phNumNo + 1, phNumYes, "Should have one extra phdr for PT_TLS")
    }

    @Test
    fun ptTlsVaddrIsNonZero() {
        val obj = makeObjWithTdata(ByteArray(4) { 0x11 })
        val binary = ElfStaticLinker().link(listOf(obj))
        val buf = le(binary)

        val phoff = readU64(buf, 32).toInt()
        val phnum = readU16(buf, 56)
        val tlsOff = findPhdr(buf, phoff, phnum, ElfSegmentType.TLS.code)
        assertTrue(tlsOff >= 0)

        val vaddr = readU64(buf, tlsOff + 16)
        assertTrue(vaddr > 0, "PT_TLS vaddr should be non-zero")
    }

    @Test
    fun ptTlsOffsetPointsToTdataContent() {
        val tdataContent = byteArrayOf(0x11, 0x22, 0x33, 0x44)
        val obj = makeObjWithTdata(tdataContent)
        val binary = ElfStaticLinker().link(listOf(obj))
        val buf = le(binary)

        val phoff = readU64(buf, 32).toInt()
        val phnum = readU16(buf, 56)
        val tlsOff = findPhdr(buf, phoff, phnum, ElfSegmentType.TLS.code)
        assertTrue(tlsOff >= 0)

        val fileOff = readU64(buf, tlsOff + 8).toInt()
        assertTrue(fileOff > 0 && fileOff + 4 <= binary.size, "PT_TLS offset should be valid")
        assertEquals(0x11.toByte(), binary[fileOff])
        assertEquals(0x22.toByte(), binary[fileOff + 1])
        assertEquals(0x33.toByte(), binary[fileOff + 2])
        assertEquals(0x44.toByte(), binary[fileOff + 3])
    }

    @Test
    fun elfSegmentTypeFromCode() {
        assertEquals(ElfSegmentType.TLS, ElfSegmentType.fromCode(7))
    }

    @Test
    fun multipleTdataSectionsMerge() {
        val code1 = byteArrayOf(0xC3.toByte())
        val code2 = byteArrayOf(0xC3.toByte())
        val obj1 = ObjectFile(
            format = ObjectFormat.ELF,
            arch = Architecture(ArchType.X86_64),
            sections = listOf(
                Section(".text", SectionKind.TEXT, code1, align = 16),
                Section(".tdata", SectionKind.TDATA, ByteArray(4) { 0x11 }, align = 4),
            ),
            symbols = listOf(
                Symbol("_start", value = 0, size = 1, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
            relocations = emptyList(),
        )
        val obj2 = ObjectFile(
            format = ObjectFormat.ELF,
            arch = Architecture(ArchType.X86_64),
            sections = listOf(
                Section(".text", SectionKind.TEXT, code2, align = 16),
                Section(".tdata", SectionKind.TDATA, ByteArray(8) { 0x22 }, align = 8),
            ),
            symbols = emptyList(),
            relocations = emptyList(),
        )

        val binary = ElfStaticLinker().link(listOf(obj1, obj2))
        val buf = le(binary)
        val phoff = readU64(buf, 32).toInt()
        val phnum = readU16(buf, 56)
        val tlsOff = findPhdr(buf, phoff, phnum, ElfSegmentType.TLS.code)
        assertTrue(tlsOff >= 0, "PT_TLS should be present for merged tdata")

        val filesz = readU64(buf, tlsOff + 32)
        assertTrue(filesz >= 12, "Merged tdata should be at least 12 bytes (4 + padding + 8)")
    }

    @Test
    fun ptTlsTypeCodeIs7() {
        assertEquals(7, ElfSegmentType.TLS.code)
    }

    @Test
    fun gottpoffRelaxedToLocalExecInStaticLink() {
        // Build code: REX.W + ADD r11, [rip+disp32] (GOTTPOFF pattern)
        // 4C 03 1D 00000000 = add r11, [rip+0] with GOTTPOFF relocation at offset 3
        val code = byteArrayOf(
            0x64, 0x4C, 0x8B.toByte(), 0x1C, 0x25, 0x00, 0x00, 0x00, 0x00,  // mov r11, fs:[0]
            0x4C, 0x03, 0x1D, 0x00, 0x00, 0x00, 0x00,  // add r11, [rip+0] @GOTTPOFF
            0xC3.toByte(), // ret
        )
        val tdataBytes = ByteArray(4) { 0x42 }
        val obj = ObjectFile(
            format = ObjectFormat.ELF,
            arch = Architecture(ArchType.X86_64),
            sections = listOf(
                Section(".text", SectionKind.TEXT, code, align = 16),
                Section(".tdata", SectionKind.TDATA, tdataBytes, align = 4),
            ),
            symbols = listOf(
                Symbol("_start", value = 0, size = code.size.toLong(), section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("tls_var", value = 0, size = 4, section = ".tdata",
                    binding = SymbolBinding.LOCAL, kind = SymbolKind.DATA),
            ),
            relocations = listOf(
                Relocation(offset = 12, symbol = "tls_var",
                    type = RelocationType.X86_64.GOTTPOFF, addend = -4, section = ".text"),
            ),
        )
        val binary = ElfStaticLinker().link(listOf(obj))
        // Should succeed without error (GOTTPOFF is handled)
        assertTrue(binary.isNotEmpty())
        // The GOTTPOFF should have been relaxed: ADD (0x03) → LEA (0x8D)
        // Find the text section in the binary and check the opcode was rewritten
        val buf = le(binary)
        val textStart = findTextSectionOffset(buf, binary)
        assertTrue(textStart > 0, "Should find .text in binary")
        // At textStart + 10, we should see 0x8D (LEA) instead of 0x03 (ADD)
        assertEquals(0x8D.toByte(), binary[textStart + 10], "GOTTPOFF should be relaxed: ADD → LEA")
    }

    @Test
    fun gottpoffRelocationProducesValidBinary() {
        // End-to-end test: use X86CodeGenerator with INITIAL_EXEC, link statically
        val ir = org.kgen.ir.build.IrBuilder("test", org.kgen.ir.target.Target.x86_64())
        ir.addGlobal("tls_ie", org.kgen.ir.Type.I32, org.kgen.ir.Constant.I32(99),
            threadLocal = org.kgen.ir.ThreadLocalMode.INITIAL_EXEC)
        ir.createFunction("_start", emptyList(), org.kgen.ir.Type.I32)
        ir.appendBlock("entry")
        val loaded = ir.load(org.kgen.ir.Type.I32,
            org.kgen.ir.GlobalRef("tls_ie", org.kgen.ir.Type.Pointer(org.kgen.ir.Type.I32)))
        ir.ret(loaded)
        ir.finalizeFunction()
        val module = ir.build()

        val obj = org.kgen.target.x86.codegen.X86CodeGenerator().generateObjectFile(module)
        assertTrue(obj.relocations.any { it.type == RelocationType.X86_64.GOTTPOFF },
            "Object file should have GOTTPOFF relocation")

        val binary = ElfStaticLinker().link(listOf(obj))
        assertTrue(binary.isNotEmpty(), "Should produce valid binary with GOTTPOFF")
    }

    @Test
    fun tlsgdRelaxedToLocalExecInStaticLink() {
        // Build the standard 16-byte GD sequence:
        // 66 48 8d 3d 00000000  (data16 lea rdi,[rip+sym@TLSGD])
        // 66 66 48 e8 00000000  (data16 data16 rex.W call __tls_get_addr@PLT)
        // C3                    (ret)
        val code = byteArrayOf(
            0x66, 0x48, 0x8D.toByte(), 0x3D, 0x00, 0x00, 0x00, 0x00,  // data16 lea rdi,[rip+0]
            0x66, 0x66, 0x48, 0xE8.toByte(), 0x00, 0x00, 0x00, 0x00,  // data16 data16 rex.W call
            0xC3.toByte(), // ret
        )
        val tdataBytes = ByteArray(4) { 0x55 }
        val obj = ObjectFile(
            format = ObjectFormat.ELF,
            arch = Architecture(ArchType.X86_64),
            sections = listOf(
                Section(".text", SectionKind.TEXT, code, align = 16),
                Section(".tdata", SectionKind.TDATA, tdataBytes, align = 4),
            ),
            symbols = listOf(
                Symbol("_start", value = 0, size = code.size.toLong(), section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("tls_gd_var", value = 0, size = 4, section = ".tdata",
                    binding = SymbolBinding.LOCAL, kind = SymbolKind.DATA),
            ),
            relocations = listOf(
                // TLSGD relocation points at the disp32 in the LEA (offset 4)
                Relocation(offset = 4, symbol = "tls_gd_var",
                    type = RelocationType.X86_64.TLSGD, addend = -4, section = ".text"),
                // PLT32 for __tls_get_addr (offset 12)
                Relocation(offset = 12, symbol = "__tls_get_addr",
                    type = RelocationType.X86_64.PLT32, addend = -4, section = ".text"),
            ),
        )
        val binary = ElfStaticLinker().link(listOf(obj))
        assertTrue(binary.isNotEmpty())

        // Find the text section — should start with 0x64 (FS prefix from relaxation)
        val textStart = findGdRelaxedTextOffset(binary)
        assertTrue(textStart >= 0, "Should find relaxed GD sequence in binary")
        // Relaxed to: 64 48 8b 04 25 00000000 (mov rax, fs:[0])
        assertEquals(0x64.toByte(), binary[textStart + 0], "Should start with FS prefix")
        assertEquals(0x48.toByte(), binary[textStart + 1], "Should have REX.W")
        assertEquals(0x8B.toByte(), binary[textStart + 2], "Should have MOV opcode")
        // Then: 48 8d 80 XXXXXXXX (lea rax, [rax+tpoff32])
        assertEquals(0x48.toByte(), binary[textStart + 9], "LEA should have REX.W")
        assertEquals(0x8D.toByte(), binary[textStart + 10], "Should have LEA opcode")
        assertEquals(0x80.toByte(), binary[textStart + 11], "ModRM for [rax+disp32]")
    }

    @Test
    fun tlsgdEndToEndProducesValidBinary() {
        val ir = org.kgen.ir.build.IrBuilder("test", org.kgen.ir.target.Target.x86_64())
        ir.addGlobal("tls_gd", org.kgen.ir.Type.I32, org.kgen.ir.Constant.I32(77),
            threadLocal = org.kgen.ir.ThreadLocalMode.GENERAL_DYNAMIC)
        ir.createFunction("_start", emptyList(), org.kgen.ir.Type.I32)
        ir.appendBlock("entry")
        val loaded = ir.load(org.kgen.ir.Type.I32,
            org.kgen.ir.GlobalRef("tls_gd", org.kgen.ir.Type.Pointer(org.kgen.ir.Type.I32)))
        ir.ret(loaded)
        ir.finalizeFunction()
        val module = ir.build()

        val obj = org.kgen.target.x86.codegen.X86CodeGenerator().generateObjectFile(module)
        assertTrue(obj.relocations.any { it.type == RelocationType.X86_64.TLSGD },
            "Object file should have TLSGD relocation")
        assertTrue(obj.relocations.any {
            it.type == RelocationType.X86_64.PLT32 && it.symbol == "__tls_get_addr"
        }, "Object file should have PLT32 relocation to __tls_get_addr")

        val binary = ElfStaticLinker().link(listOf(obj))
        assertTrue(binary.isNotEmpty(), "Should produce valid binary with TLSGD")
    }

    private fun findGdRelaxedTextOffset(binary: ByteArray): Int {
        // After GD→LE relaxation: starts with 0x64 0x48 0x8B 0x04 0x25
        for (i in 0 until binary.size - 16) {
            if (binary[i] == 0x64.toByte() && binary[i + 1] == 0x48.toByte() &&
                binary[i + 2] == 0x8B.toByte() && binary[i + 3] == 0x04.toByte() &&
                binary[i + 4] == 0x25.toByte()) {
                return i
            }
        }
        return -1
    }

    private fun findTextSectionOffset(buf: ByteBuffer, binary: ByteArray): Int {
        // Simple heuristic: look for the FS prefix (0x64) which starts our TLS code
        for (i in 0 until binary.size - 16) {
            if (binary[i] == 0x64.toByte() && binary[i + 1] == 0x4C.toByte()) {
                return i
            }
        }
        return -1
    }
}
