package org.kgen.binary.pe

import org.kgen.binary.*

/**
 * Reads PE/COFF binaries and produces the universal [ObjectFile] model.
 */
class PeObjectFileReader : ObjectFileReader {
    override val format: ObjectFormat = ObjectFormat.PE_COFF

    override fun canRead(bytes: ByteArray): Boolean =
        bytes.size >= 2
                && bytes[0] == 0x4D.toByte()
                && bytes[1] == 0x5A.toByte()

    override fun read(bytes: ByteArray): ObjectFile {
        val pe = PeReader.read(bytes)
        return PeObjectFileProjection.project(pe)
    }
}
