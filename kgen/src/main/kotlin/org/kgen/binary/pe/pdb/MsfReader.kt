package org.kgen.binary.pe.pdb

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Reads an MSF (Multi-Stream File) container from raw bytes.
 *
 * The MSF format is the physical container for PDB files. It organizes data into
 * fixed-size blocks with a stream directory that maps logical streams to block lists.
 *
 * Layout:
 * ```
 * Block 0: SuperBlock (magic, block size, block count, directory size)
 * Block 1-2: Free Page Map (FPM)
 * Block N: Directory map (list of blocks holding the stream directory)
 * Directory blocks: Stream sizes + block lists for each stream
 * Remaining blocks: Stream data
 * ```
 */
object MsfReader {

    /**
     * Parse an MSF file from raw bytes.
     * @throws IllegalArgumentException if the data is not a valid MSF file.
     */
    @JvmStatic
    fun read(data: ByteArray): MsfFile {
        val superBlock = MsfSuperBlock.parse(data)
        val blockSize = superBlock.blockSize

        // The blockMapAddr in the superblock points to a block containing the
        // list of block indices that make up the stream directory.
        val directoryBlockCount = ceilDiv(superBlock.directorySize, blockSize)

        // Read directory block indices directly from the block at blockMapAddr
        val directoryBlocks = readDirectoryBlockIndices(data, superBlock, directoryBlockCount)

        // Read the stream directory itself
        val directoryData = readBlockList(data, blockSize, directoryBlocks, superBlock.directorySize)
        val dirBuf = ByteBuffer.wrap(directoryData).order(ByteOrder.LITTLE_ENDIAN)

        val streamCount = dirBuf.getInt()
        val streamSizes = IntArray(streamCount)
        for (i in 0 until streamCount) {
            streamSizes[i] = dirBuf.getInt()
        }

        val streams = mutableListOf<MsfStream>()
        for (i in 0 until streamCount) {
            val size = streamSizes[i]
            if (size == -1 || size == 0) {
                streams.add(MsfStream(0, emptyList()))
            } else {
                val numBlocks = ceilDiv(size, blockSize)
                val blocks = mutableListOf<Int>()
                for (j in 0 until numBlocks) {
                    blocks.add(dirBuf.getInt())
                }
                streams.add(MsfStream(size, blocks))
            }
        }

        return MsfFile(
            blockSize = blockSize,
            blockCount = superBlock.blockCount,
            streams = streams,
            data = data,
        )
    }

    /**
     * Check if the given bytes look like an MSF/PDB file.
     */
    @JvmStatic
    fun isMsf(data: ByteArray): Boolean {
        if (data.size < MsfSuperBlock.SIZE) return false
        for (i in MsfSuperBlock.MAGIC.indices) {
            if (data[i] != MsfSuperBlock.MAGIC[i]) return false
        }
        return true
    }

    private fun readDirectoryBlockIndices(data: ByteArray, superBlock: MsfSuperBlock, count: Int): List<Int> {
        // The block at blockMapAddr contains an array of uint32 block indices
        // that make up the stream directory.
        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        buf.position(superBlock.directoryMapBlockIndex * superBlock.blockSize)
        val blocks = mutableListOf<Int>()
        for (i in 0 until count) {
            blocks.add(buf.getInt())
        }
        return blocks
    }

    private fun readBlockList(data: ByteArray, blockSize: Int, blocks: List<Int>, totalSize: Int): ByteArray {
        val result = ByteArray(totalSize)
        var written = 0
        for (block in blocks) {
            val offset = block * blockSize
            val count = minOf(blockSize, totalSize - written)
            System.arraycopy(data, offset, result, written, count)
            written += count
        }
        return result
    }

    private fun ceilDiv(a: Int, b: Int): Int = (a + b - 1) / b
}
