package org.kgen.binary.pe.pdb

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Multi-Stream File (MSF) container — the physical format of PDB files.
 *
 * An MSF file consists of fixed-size blocks (typically 4096 bytes).
 * Data is organized into numbered streams, each backed by a list of block indices.
 * The superblock at offset 0 describes the layout.
 *
 * ```kotlin
 * val msf = MsfReader.read(pdbBytes)
 * val pdbInfoStream = msf.streamData(0)
 * val tpiStream = msf.streamData(2)
 * ```
 */
class MsfFile(
    val blockSize: Int,
    val blockCount: Int,
    val streams: List<MsfStream>,
    private val data: ByteArray,
) {
    /** Read the raw bytes of a stream by index. */
    fun streamData(index: Int): ByteArray {
        val stream = streams[index]
        if (stream.size == 0) return byteArrayOf()
        val result = ByteArray(stream.size)
        var written = 0
        for (block in stream.blocks) {
            val offset = block * blockSize
            val count = minOf(blockSize, stream.size - written)
            System.arraycopy(data, offset, result, written, count)
            written += count
        }
        return result
    }

    /** Number of streams in the file. */
    val streamCount: Int get() = streams.size
}

/**
 * A stream within an MSF file.
 * @property size Stream size in bytes.
 * @property blocks Ordered list of block indices that hold the stream data.
 */
data class MsfStream(
    val size: Int,
    val blocks: List<Int>,
)

/**
 * MSF superblock — the first block of the file.
 */
data class MsfSuperBlock(
    val blockSize: Int,
    val freeBlockMapIndex: Int,
    val blockCount: Int,
    val directorySize: Int,
    val directoryMapBlockIndex: Int,
) {
    companion object {
        const val SIZE = 56
        val MAGIC = "Microsoft C/C++ MSF 7.00\r\n\u001aDS\u0000\u0000\u0000".toByteArray(Charsets.US_ASCII)

        fun parse(data: ByteArray): MsfSuperBlock {
            require(data.size >= SIZE) { "Data too small for MSF superblock" }
            // Verify magic
            for (i in MAGIC.indices) {
                require(data[i] == MAGIC[i]) { "Not an MSF file: bad magic at byte $i" }
            }
            val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            buf.position(32)
            val blockSize = buf.getInt()      // offset 32
            val fpmIndex = buf.getInt()       // offset 36
            val blockCount = buf.getInt()     // offset 40
            val directorySize = buf.getInt()  // offset 44
            buf.getInt()                       // offset 48: unknown/reserved
            val blockMapAddr = buf.getInt()   // offset 52
            return MsfSuperBlock(
                blockSize = blockSize,
                freeBlockMapIndex = fpmIndex,
                blockCount = blockCount,
                directorySize = directorySize,
                directoryMapBlockIndex = blockMapAddr,
            )
        }
    }
}
