package org.kgen.binary.dwarf

import java.io.ByteArrayOutputStream

/**
 * Writes a `.eh_frame_hdr` section — a binary search table that maps
 * code addresses (initial locations) to their corresponding FDE entries
 * in `.eh_frame`. Used by the runtime unwinder for fast PC → FDE lookup.
 *
 * Format (GCC/LLVM convention):
 * ```
 * version:         1 byte  (always 1)
 * eh_frame_ptr_enc: 1 byte  (DW_EH_PE encoding for eh_frame pointer)
 * fde_count_enc:   1 byte  (DW_EH_PE encoding for FDE count)
 * table_enc:       1 byte  (DW_EH_PE encoding for table entries)
 * eh_frame_ptr:    encoded (pointer to .eh_frame start, relative to this field)
 * fde_count:       encoded (number of FDE entries)
 * table[]:         sorted pairs of (initial_location, fde_address)
 * ```
 */
object EhFrameHdrWriter {

    /**
     * Write the .eh_frame_hdr section.
     *
     * @param fdeEntries list of (codeOffset, codeLength) pairs for each FDE, sorted by codeOffset
     * @param ehFrameSectionOffset offset of .eh_frame section relative to .eh_frame_hdr section start
     * @param fdeOffsets byte offsets within .eh_frame where each FDE record starts
     */
    @JvmStatic
    fun write(
        fdeEntries: List<FdeEntry>,
        ehFrameSectionOffset: Int,
        fdeOffsets: List<Int>,
    ): ByteArray {
        require(fdeEntries.size == fdeOffsets.size) {
            "fdeEntries and fdeOffsets must have the same size"
        }
        val buf = ByteArrayOutputStream()

        // Header
        buf.write(1) // version
        buf.write(DW_EH_PE_pcrel or DW_EH_PE_sdata4) // eh_frame_ptr encoding
        buf.write(DW_EH_PE_udata4) // fde_count encoding
        buf.write(DW_EH_PE_datarel or DW_EH_PE_sdata4) // table entry encoding

        // eh_frame_ptr: offset from this field (at byte 4) to .eh_frame start
        writeI32(buf, ehFrameSectionOffset - 4)

        // fde_count
        writeU32(buf, fdeEntries.size)

        // Binary search table: sorted by initial_location
        // Each entry: (initial_location, fde_address) — both datarel from section start
        val headerSize = 4 + 4 + 4 // header(4) + eh_frame_ptr(4) + fde_count(4)
        for (i in fdeEntries.indices) {
            val entry = fdeEntries[i]
            // initial_location: code address relative to .eh_frame_hdr start
            // In a relocatable object, this is the code offset
            writeI32(buf, entry.codeOffset.toInt())
            // fde_address: offset to the FDE within .eh_frame, relative to .eh_frame_hdr start
            writeI32(buf, ehFrameSectionOffset + fdeOffsets[i])
        }

        return buf.toByteArray()
    }

    /**
     * Simplified write that computes FDE offsets from the EhFrameWriter output.
     * Uses the FDE entries and the CIE to compute byte offsets.
     */
    @JvmStatic
    fun write(
        cie: CieEntry,
        fdeEntries: List<FdeEntry>,
        ehFrameSectionOffset: Int,
    ): ByteArray {
        // Compute FDE byte offsets within the .eh_frame section.
        // Layout: CIE record | FDE 0 | FDE 1 | ... | terminator
        val cieBytes = EhFrameWriter.writeCieOnly(cie)
        val cieRecordSize = cieBytes.size
        val fdeOffsets = mutableListOf<Int>()
        var offset = cieRecordSize
        for (fde in fdeEntries) {
            fdeOffsets.add(offset)
            val fdeBytes = EhFrameWriter.writeFdeOnly(fde, cieRecordSize)
            offset += fdeBytes.size
        }
        return write(fdeEntries, ehFrameSectionOffset, fdeOffsets)
    }

    // DW_EH_PE encoding constants
    private const val DW_EH_PE_pcrel = 0x10
    private const val DW_EH_PE_datarel = 0x30
    private const val DW_EH_PE_sdata4 = 0x0B
    private const val DW_EH_PE_udata4 = 0x03

    private fun writeI32(buf: ByteArrayOutputStream, value: Int) {
        buf.write(value and 0xFF)
        buf.write((value shr 8) and 0xFF)
        buf.write((value shr 16) and 0xFF)
        buf.write((value shr 24) and 0xFF)
    }

    private fun writeU32(buf: ByteArrayOutputStream, value: Int) = writeI32(buf, value)
}
