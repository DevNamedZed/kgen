package org.kgen.binary.dwarf

import java.io.ByteArrayOutputStream

/**
 * Writes DWARF/GCC-style Language-Specific Data Area (LSDA) for `.gcc_except_table`.
 *
 * The LSDA format is:
 * 1. Header: landing pad base encoding, type table encoding, call site table encoding
 * 2. Call site table: maps code ranges to landing pads and action chains
 * 3. Action table: chains of type filter entries
 * 4. Type table: pointers to type info (grows backward from end)
 *
 * ```java
 * byte[] lsda = LsdaWriter.write(lsdaTable);
 * ```
 */
object LsdaWriter {

    /**
     * Write an LSDA table to bytes.
     *
     * @param table the LSDA table to write
     * @return raw bytes for the `.gcc_except_table` section
     */
    @JvmStatic
    fun write(table: LsdaTable): ByteArray {
        val out = ByteArrayOutputStream()

        // LSDA header
        // Landing pad base encoding: DW_EH_PE_omit (0xFF) = use function start
        out.write(DW_EH_PE_OMIT)

        // Type table encoding
        if (table.typeNames.isNotEmpty()) {
            out.write(DW_EH_PE_UDATA4) // 4-byte type info pointers
            // Type table offset: distance from here to end of type table
            // We need to compute this after writing the call site and action tables
            val callSiteBytes = encodeCallSiteTable(table.callSites)
            val actionBytes = encodeActionTable(table.actions)
            val typeTableSize = table.typeNames.size * 4
            val offsetFromHere = uleb128Size(callSiteBytes.size) + callSiteBytes.size + actionBytes.size + typeTableSize
            writeUleb128(out, offsetFromHere)
        } else {
            out.write(DW_EH_PE_OMIT) // no type table
        }

        // Call site table encoding
        out.write(DW_EH_PE_ULEB128)

        // Call site table
        val callSiteBytes = encodeCallSiteTable(table.callSites)
        writeUleb128(out, callSiteBytes.size)
        out.write(callSiteBytes)

        // Action table
        val actionBytes = encodeActionTable(table.actions)
        out.write(actionBytes)

        // Type table (written in reverse order — index 1 is last entry)
        for (i in table.typeNames.indices.reversed()) {
            // In a real implementation, these would be relocations to typeinfo symbols.
            // For now, write placeholder 4-byte entries (the linker resolves them).
            writeInt32(out, i + 1) // type index as placeholder
        }

        return out.toByteArray()
    }

    private fun encodeCallSiteTable(callSites: List<CallSiteEntry>): ByteArray {
        val out = ByteArrayOutputStream()
        for (cs in callSites) {
            writeUleb128(out, cs.callOffset)
            writeUleb128(out, cs.callLength)
            writeUleb128(out, cs.landingPadOffset)
            writeUleb128(out, cs.actionIndex)
        }
        return out.toByteArray()
    }

    private fun encodeActionTable(actions: List<ActionEntry>): ByteArray {
        val out = ByteArrayOutputStream()
        for (action in actions) {
            writeSleb128(out, action.typeIndex)
            writeSleb128(out, action.nextAction)
        }
        return out.toByteArray()
    }

    // DWARF pointer encoding constants
    private const val DW_EH_PE_OMIT = 0xFF
    private const val DW_EH_PE_UDATA4 = 0x03
    private const val DW_EH_PE_ULEB128 = 0x01

    private fun writeInt32(out: ByteArrayOutputStream, v: Int) {
        out.write(v and 0xFF)
        out.write((v shr 8) and 0xFF)
        out.write((v shr 16) and 0xFF)
        out.write((v shr 24) and 0xFF)
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

    private fun uleb128Size(value: Int): Int {
        var v = value
        var size = 0
        do {
            v = v ushr 7
            size++
        } while (v != 0)
        return size
    }
}
