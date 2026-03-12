package org.kgen.binary.pe

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MixedModeAssemblerTest {

    private fun readU16(data: ByteArray, offset: Int): Int {
        return (data[offset].toInt() and 0xFF) or
            ((data[offset + 1].toInt() and 0xFF) shl 8)
    }

    private fun readU32(data: ByteArray, offset: Int): Int {
        return (data[offset].toInt() and 0xFF) or
            ((data[offset + 1].toInt() and 0xFF) shl 8) or
            ((data[offset + 2].toInt() and 0xFF) shl 16) or
            ((data[offset + 3].toInt() and 0xFF) shl 24)
    }

    @Test
    fun assembleWithNativeCodeOnly() {
        val asm = MixedModeAssembler("NativeOnly")
        asm.setNativeCode(byteArrayOf(0xC3.toByte())) // ret
        val pe = asm.assemble()

        // Verify MZ signature
        assertEquals('M'.code.toByte(), pe[0])
        assertEquals('Z'.code.toByte(), pe[1])

        // Verify PE signature
        val peOffset = readU32(pe, 0x3C)
        assertEquals('P'.code, pe[peOffset].toInt())
        assertEquals('E'.code, pe[peOffset + 1].toInt())
    }

    @Test
    fun assembleWithClrMetadataOnly() {
        val metadata = ByteArray(256) // dummy metadata
        val asm = MixedModeAssembler("ManagedOnly")
        asm.setClrMetadata(metadata)
        val pe = asm.assemble()

        // Valid PE
        assertEquals('M'.code.toByte(), pe[0])

        val peOffset = readU32(pe, 0x3C)
        assertEquals('P'.code, pe[peOffset].toInt())
    }

    @Test
    fun assembleWithBothNativeAndClr() {
        val nativeCode = byteArrayOf(0x48, 0x89.toByte(), 0xC8.toByte(), 0xC3.toByte()) // mov rax, rcx; ret
        val metadata = ByteArray(128)

        val asm = MixedModeAssembler("MixedMode")
        asm.setNativeCode(nativeCode)
        asm.setClrMetadata(metadata)
        val pe = asm.assemble()

        // Verify it's a valid PE
        assertEquals('M'.code.toByte(), pe[0])
        val peOffset = readU32(pe, 0x3C)

        // Machine type should be x86-64
        val machineType = readU16(pe, peOffset + 4)
        assertEquals(0x8664, machineType)

        // Should have 4 sections (.text, .cil, .idata, .reloc)
        val numSections = readU16(pe, peOffset + 6)
        assertEquals(4, numSections)
    }

    @Test
    fun clrHeaderPresent() {
        val metadata = ByteArray(64)
        val asm = MixedModeAssembler("WithClr")
        asm.setClrMetadata(metadata)
        val pe = asm.assemble()

        val peOffset = readU32(pe, 0x3C)
        val optHeaderOffset = peOffset + 4 + 20

        // Data directory 14 (CLR): offset 112 + 112 = 224
        val clrRVA = readU32(pe, optHeaderOffset + 112 + 112)
        val clrSize = readU32(pe, optHeaderOffset + 112 + 116)

        assertTrue(clrRVA > 0, "CLR header RVA should be set")
        assertEquals(72, clrSize, "CLR header size should be 72 bytes")
    }

    @Test
    fun mixedModeClrFlagsNotILOnly() {
        val metadata = ByteArray(64)
        val nativeCode = byteArrayOf(0xC3.toByte())
        val asm = MixedModeAssembler("MixedFlags")
        asm.setNativeCode(nativeCode)
        asm.setClrMetadata(metadata)
        val pe = asm.assemble()

        // Find the CLR header in the PE
        val peOffset = readU32(pe, 0x3C)
        val optHeaderOffset = peOffset + 4 + 20
        val clrRVA = readU32(pe, optHeaderOffset + 112 + 112)

        // Find section containing the CLR RVA
        val numSections = readU16(pe, peOffset + 6)
        val sectionTableOffset = optHeaderOffset + 240
        var clrFileOffset = -1
        for (i in 0 until numSections) {
            val secOffset = sectionTableOffset + i * 40
            val secRVA = readU32(pe, secOffset + 12)
            val secVSize = readU32(pe, secOffset + 8)
            val secRawOffset = readU32(pe, secOffset + 20)
            if (clrRVA >= secRVA && clrRVA < secRVA + secVSize) {
                clrFileOffset = secRawOffset + (clrRVA - secRVA)
                break
            }
        }
        assertTrue(clrFileOffset >= 0, "Should find CLR header in sections")

        // CLR flags at offset 16 in CLR header
        val flags = readU32(pe, clrFileOffset + 16)
        assertEquals(0, flags and 1, "COR_FLAGS_ILONLY should NOT be set for mixed-mode")
    }

    @Test
    fun layoutReturnsCorrectInfo() {
        val asm = MixedModeAssembler("LayoutTest")
        asm.setNativeCode(byteArrayOf(0xCC.toByte()))
        asm.setClrMetadata(ByteArray(32))

        val layout = asm.layout()
        assertTrue(layout.hasNativeCode)
        assertTrue(layout.hasClrMetadata)
        assertTrue(layout.nativeCodeRVA > 0)
        assertTrue(layout.clrMetadataRVA > layout.nativeCodeRVA)
        assertEquals(0x140000000L, layout.imageBase)
    }

    @Test
    fun layoutWithoutNativeCode() {
        val asm = MixedModeAssembler("NoNative")
        asm.setClrMetadata(ByteArray(32))

        val layout = asm.layout()
        assertFalse(layout.hasNativeCode)
        assertTrue(layout.hasClrMetadata)
        assertEquals(0, layout.nativeCodeRVA)
    }

    @Test
    fun nativeExports() {
        val asm = MixedModeAssembler("WithExports")
        asm.setNativeCode(ByteArray(64))
        asm.addNativeExport("add", 0)
        asm.addNativeExport("multiply", 16)
        asm.setClrMetadata(ByteArray(32))

        val layout = asm.layout()
        assertEquals(2, layout.nativeExportCount)
    }

    @Test
    fun fluentApi() {
        val pe = MixedModeAssembler("Fluent")
            .setNativeCode(byteArrayOf(0xC3.toByte()))
            .setClrMetadata(ByteArray(16))
            .addNativeExport("entry", 0)
            .assemble()

        assertTrue(pe.isNotEmpty())
        assertEquals('M'.code.toByte(), pe[0])
    }

    @Test
    fun staticHelpers() {
        assertEquals(0x200, MixedModeAssembler.fileAlignment())
        assertEquals(0x1000, MixedModeAssembler.sectionAlignment())
        assertEquals(0x140000000L, MixedModeAssembler.imageBase())
    }

    @Test
    fun sectionHeaders() {
        val asm = MixedModeAssembler("Sections")
        asm.setNativeCode(byteArrayOf(0x90.toByte())) // nop
        asm.setClrMetadata(ByteArray(16))
        val pe = asm.assemble()

        val peOffset = readU32(pe, 0x3C)
        val optHeaderOffset = peOffset + 4 + 20
        val sectionTableOffset = optHeaderOffset + 240
        val numSections = readU16(pe, peOffset + 6)

        val sectionNames = mutableListOf<String>()
        for (i in 0 until numSections) {
            val secOffset = sectionTableOffset + i * 40
            val nameBytes = pe.copyOfRange(secOffset, secOffset + 8)
            val name = String(nameBytes, Charsets.US_ASCII).trimEnd('\u0000')
            sectionNames.add(name)
        }

        assertTrue(sectionNames.contains(".text"), "Should have .text section")
        assertTrue(sectionNames.contains(".cil"), "Should have .cil section")
        assertTrue(sectionNames.contains(".idata"), "Should have .idata section")
        assertTrue(sectionNames.contains(".reloc"), "Should have .reloc section")
    }

    @Test
    fun importsMscoree() {
        val asm = MixedModeAssembler("MscoreeImport")
        asm.setClrMetadata(ByteArray(16))
        val pe = asm.assemble()

        // The PE should contain the string "mscoree.dll" somewhere in the .idata section
        val peString = String(pe, Charsets.US_ASCII)
        assertTrue(peString.contains("mscoree.dll"), "Should import mscoree.dll")
        assertTrue(peString.contains("_CorExeMain"), "Should import _CorExeMain")
    }

    @Test
    fun subsystemConfiguration() {
        val asm = MixedModeAssembler("GuiApp")
        asm.setNativeCode(byteArrayOf(0xC3.toByte()))
        asm.setSubsystem(2) // GUI

        val pe = asm.assemble()
        val peOffset = readU32(pe, 0x3C)
        val optHeaderOffset = peOffset + 4 + 20
        val subsystem = readU16(pe, optHeaderOffset + 68)
        assertEquals(2, subsystem, "Subsystem should be GUI (2)")
    }
}
