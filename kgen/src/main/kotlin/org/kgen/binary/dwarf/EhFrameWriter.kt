package org.kgen.binary.dwarf

import java.io.ByteArrayOutputStream

/**
 * Writes DWARF `.eh_frame` sections from CIE and FDE entries.
 *
 * The `.eh_frame` section uses a length-prefixed record format where each CIE/FDE
 * starts with a 4-byte length, followed by a CIE pointer (0 for CIE, offset to CIE for FDE),
 * then the record-specific data.
 *
 * ```java
 * var cie = new CieEntry(1, 1, -8, 16);
 * var fdes = List.of(new FdeEntry("main", 0, 64, instructions));
 * byte[] ehFrame = EhFrameWriter.write(cie, fdes);
 * ```
 */
object EhFrameWriter {

    /**
     * Write a complete `.eh_frame` section containing one CIE and its FDEs.
     *
     * @param cie the Common Information Entry
     * @param fdes the Frame Description Entries (one per function)
     * @param addrSize address size in bytes (4 or 8)
     * @return the raw bytes of the `.eh_frame` section
     */
    @JvmStatic
    @JvmOverloads
    fun write(cie: CieEntry, fdes: List<FdeEntry>, addrSize: Int = 8): ByteArray {
        val out = ByteArrayOutputStream()

        // Write CIE
        val cieOffset = out.size()
        writeCie(out, cie, addrSize)

        // Write FDEs
        for (fde in fdes) {
            writeFde(out, fde, cieOffset, addrSize)
        }

        // Terminator: zero-length record
        writeInt32(out, 0)

        return out.toByteArray()
    }

    /** Write just the CIE record (for computing FDE offsets). */
    @JvmStatic
    @JvmOverloads
    fun writeCieOnly(cie: CieEntry, addrSize: Int = 8): ByteArray {
        val out = ByteArrayOutputStream()
        writeCie(out, cie, addrSize)
        return out.toByteArray()
    }

    /** Write just one FDE record (for computing FDE sizes). */
    @JvmStatic
    @JvmOverloads
    fun writeFdeOnly(fde: FdeEntry, cieRecordSize: Int, addrSize: Int = 8): ByteArray {
        val out = ByteArrayOutputStream()
        writeFde(out, fde, 0, addrSize)
        return out.toByteArray()
    }

    private fun writeCie(out: ByteArrayOutputStream, cie: CieEntry, addrSize: Int) {
        val body = ByteArrayOutputStream()

        // CIE ID: 0 for .eh_frame (0xFFFFFFFF for .debug_frame)
        writeInt32(body, 0)

        // Version
        body.write(cie.version)

        // Augmentation string (null-terminated)
        for (c in cie.augmentation) body.write(c.code)
        body.write(0)

        // Code alignment factor (ULEB128)
        writeUleb128(body, cie.codeAlignFactor)

        // Data alignment factor (SLEB128)
        writeSleb128(body, cie.dataAlignFactor)

        // Return address register (ULEB128)
        writeUleb128(body, cie.returnAddressRegister)

        // Augmentation data (for "zR": length + pointer encoding)
        if (cie.augmentation.contains('z')) {
            val augData = ByteArrayOutputStream()
            if (cie.augmentation.contains('R')) {
                augData.write(cie.pointerEncoding)
            }
            writeUleb128(body, augData.size())
            body.write(augData.toByteArray())
        }

        // Initial instructions
        for (inst in cie.initialInstructions) {
            writeCfiInstruction(body, inst)
        }

        // Pad to pointer-size alignment
        padToAlignment(body, addrSize)

        // Write length + body
        writeInt32(out, body.size())
        out.write(body.toByteArray())
    }

    private fun writeFde(out: ByteArrayOutputStream, fde: FdeEntry, cieOffset: Int, addrSize: Int) {
        val body = ByteArrayOutputStream()

        // CIE pointer: offset from this field to the CIE start
        // In .eh_frame, this is (current position + 4) - cieOffset
        // We'll fix this up after we know the body size
        val fdeStart = out.size()
        val ciePointerPos = fdeStart + 4 // after the length field
        writeInt32(body, ciePointerPos + 4 - cieOffset) // placeholder, will be body[0..3]

        // Initial location (pc-relative in .eh_frame with pcrel encoding)
        if (addrSize == 8) {
            writeInt64(body, fde.codeOffset) // will be relocated
        } else {
            writeInt32(body, fde.codeOffset.toInt())
        }

        // Address range
        if (addrSize == 8) {
            writeInt64(body, fde.codeLength)
        } else {
            writeInt32(body, fde.codeLength.toInt())
        }

        // Augmentation data length (for "z" augmentation)
        writeUleb128(body, 0)

        // CFI instructions
        for (inst in fde.instructions) {
            writeCfiInstruction(body, inst)
        }

        // Pad to pointer-size alignment
        padToAlignment(body, addrSize)

        // Write length + body
        writeInt32(out, body.size())
        out.write(body.toByteArray())
    }

    private fun writeCfiInstruction(out: ByteArrayOutputStream, inst: CfiInstruction) {
        when (inst) {
            is CfiInstruction.DefCfa -> {
                out.write(DW_CFA_DEF_CFA)
                writeUleb128(out, inst.register)
                writeUleb128(out, inst.offset)
            }
            is CfiInstruction.DefCfaOffset -> {
                out.write(DW_CFA_DEF_CFA_OFFSET)
                writeUleb128(out, inst.offset)
            }
            is CfiInstruction.DefCfaRegister -> {
                out.write(DW_CFA_DEF_CFA_REGISTER)
                writeUleb128(out, inst.register)
            }
            is CfiInstruction.Offset -> {
                if (inst.register < 64) {
                    // High 2 bits = 10 (DW_CFA_offset), low 6 bits = register
                    out.write(0x80 or inst.register)
                } else {
                    out.write(DW_CFA_OFFSET_EXTENDED)
                    writeUleb128(out, inst.register)
                }
                writeUleb128(out, inst.factoredOffset)
            }
            is CfiInstruction.AdvanceLoc -> {
                when {
                    inst.delta < 64 -> {
                        // High 2 bits = 01, low 6 bits = delta
                        out.write(0x40 or inst.delta)
                    }
                    inst.delta < 256 -> {
                        out.write(DW_CFA_ADVANCE_LOC1)
                        out.write(inst.delta)
                    }
                    inst.delta < 65536 -> {
                        out.write(DW_CFA_ADVANCE_LOC2)
                        out.write(inst.delta and 0xFF)
                        out.write((inst.delta shr 8) and 0xFF)
                    }
                    else -> {
                        out.write(DW_CFA_ADVANCE_LOC4)
                        writeInt32(out, inst.delta)
                    }
                }
            }
            is CfiInstruction.Restore -> {
                if (inst.register < 64) {
                    out.write(0xC0 or inst.register)
                } else {
                    out.write(DW_CFA_RESTORE_EXTENDED)
                    writeUleb128(out, inst.register)
                }
            }
            is CfiInstruction.RememberState -> out.write(DW_CFA_REMEMBER_STATE)
            is CfiInstruction.RestoreState -> out.write(DW_CFA_RESTORE_STATE)
            is CfiInstruction.Nop -> out.write(DW_CFA_NOP)
        }
    }

    // DWARF CFA opcodes
    private const val DW_CFA_NOP = 0x00
    private const val DW_CFA_ADVANCE_LOC1 = 0x02
    private const val DW_CFA_ADVANCE_LOC2 = 0x03
    private const val DW_CFA_ADVANCE_LOC4 = 0x04
    private const val DW_CFA_OFFSET_EXTENDED = 0x05
    private const val DW_CFA_RESTORE_EXTENDED = 0x06
    private const val DW_CFA_DEF_CFA = 0x0C
    private const val DW_CFA_DEF_CFA_REGISTER = 0x0D
    private const val DW_CFA_DEF_CFA_OFFSET = 0x0E
    private const val DW_CFA_REMEMBER_STATE = 0x0A
    private const val DW_CFA_RESTORE_STATE = 0x0B

    private fun writeInt32(out: ByteArrayOutputStream, v: Int) {
        out.write(v and 0xFF)
        out.write((v shr 8) and 0xFF)
        out.write((v shr 16) and 0xFF)
        out.write((v shr 24) and 0xFF)
    }

    private fun writeInt64(out: ByteArrayOutputStream, v: Long) {
        writeInt32(out, v.toInt())
        writeInt32(out, (v shr 32).toInt())
    }

    private fun writeUleb128(out: ByteArrayOutputStream, value: Int) {
        var v = value
        do {
            var byte = v and 0x7F
            v = v ushr 7
            if (v != 0) byte = byte or 0x80
            out.write(byte)
        } while (v != 0)
    }

    private fun writeSleb128(out: ByteArrayOutputStream, value: Int) {
        var v = value
        var more = true
        while (more) {
            var byte = v and 0x7F
            v = v shr 7
            if ((v == 0 && (byte and 0x40) == 0) || (v == -1 && (byte and 0x40) != 0)) {
                more = false
            } else {
                byte = byte or 0x80
            }
            out.write(byte)
        }
    }

    private fun padToAlignment(out: ByteArrayOutputStream, align: Int) {
        while (out.size() % align != 0) {
            out.write(DW_CFA_NOP)
        }
    }
}
