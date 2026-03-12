package org.kgen.binary.pe.pdb

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/**
 * PE/PDB matching via the RSDS debug directory entry.
 *
 * A PE file's IMAGE_DEBUG_DIRECTORY with type IMAGE_DEBUG_TYPE_CODEVIEW (2)
 * points to an RSDS record that contains a GUID and age matching the PDB file.
 *
 * ```kotlin
 * // Read RSDS from PE debug directory
 * val rsds = RsdsEntry.parse(debugData)
 * println("PDB path: ${rsds.pdbPath}")
 * println("GUID: ${rsds.guid}")
 *
 * // Check if PDB matches
 * val pdb = PdbReader.read(pdbBytes)
 * println("Match: ${rsds.matches(pdb)}")
 * ```
 */
data class RsdsEntry(
    val guid: UUID,
    val age: Int,
    val pdbPath: String,
) {
    /** Check if this RSDS entry matches a PDB file. */
    fun matches(pdb: PdbFile): Boolean = pdb.matchesGuid(guid, age)

    companion object {
        /** RSDS magic signature. */
        val RSDS_MAGIC = byteArrayOf('R'.code.toByte(), 'S'.code.toByte(), 'D'.code.toByte(), 'S'.code.toByte())

        /**
         * Parse an RSDS entry from the data pointed to by IMAGE_DEBUG_DIRECTORY.
         * @throws IllegalArgumentException if the data doesn't have RSDS signature.
         */
        @JvmStatic
        fun parse(data: ByteArray): RsdsEntry {
            require(data.size >= 24) { "Data too small for RSDS entry" }
            require(data[0] == 'R'.code.toByte() && data[1] == 'S'.code.toByte() &&
                    data[2] == 'D'.code.toByte() && data[3] == 'S'.code.toByte()) {
                "Not an RSDS entry: bad signature"
            }

            val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            buf.position(4)

            // GUID (mixed endian per Windows GUID convention)
            val d1 = buf.getInt()
            val d2 = buf.getShort()
            val d3 = buf.getShort()
            val guidBytes = ByteArray(8)
            buf.get(guidBytes)

            val msb = (d1.toLong() shl 32) or
                    ((d2.toLong() and 0xFFFF) shl 16) or
                    (d3.toLong() and 0xFFFF)
            var lsb = 0L
            for (b in guidBytes) {
                lsb = (lsb shl 8) or (b.toLong() and 0xFF)
            }
            val guid = UUID(msb, lsb)

            val age = buf.getInt()

            // PDB path (null-terminated string)
            val pathStart = buf.position()
            var pathEnd = pathStart
            while (pathEnd < data.size && data[pathEnd] != 0.toByte()) pathEnd++
            val pdbPath = String(data, pathStart, pathEnd - pathStart, Charsets.UTF_8)

            return RsdsEntry(guid, age, pdbPath)
        }

        /**
         * Build an RSDS entry for embedding in a PE debug directory.
         */
        @JvmStatic
        fun build(guid: UUID, age: Int, pdbPath: String): ByteArray {
            val pathBytes = pdbPath.toByteArray(Charsets.UTF_8)
            val buf = ByteBuffer.allocate(24 + pathBytes.size + 1).order(ByteOrder.LITTLE_ENDIAN)

            // RSDS signature
            buf.put(RSDS_MAGIC)

            // GUID (mixed endian)
            buf.putInt((guid.mostSignificantBits shr 32).toInt())
            buf.putShort(((guid.mostSignificantBits shr 16) and 0xFFFF).toInt().toShort())
            buf.putShort((guid.mostSignificantBits and 0xFFFF).toInt().toShort())
            var lsb = guid.leastSignificantBits
            val lsbBytes = ByteArray(8)
            for (i in 7 downTo 0) {
                lsbBytes[i] = (lsb and 0xFF).toByte()
                lsb = lsb shr 8
            }
            buf.put(lsbBytes)

            buf.putInt(age)
            buf.put(pathBytes)
            buf.put(0)

            return buf.array()
        }

        /**
         * Check if the data starts with the RSDS signature.
         */
        @JvmStatic
        fun isRsds(data: ByteArray): Boolean =
            data.size >= 4 && data[0] == 'R'.code.toByte() && data[1] == 'S'.code.toByte() &&
            data[2] == 'D'.code.toByte() && data[3] == 'S'.code.toByte()
    }
}
