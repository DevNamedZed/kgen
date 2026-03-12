package org.kgen.binary.pe.pdb

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Writes an MSF (Multi-Stream File) container — the physical format of PDB files.
 *
 * Assembles streams into fixed-size blocks with a proper superblock, free page map,
 * and stream directory.
 *
 * ```kotlin
 * val writer = MsfWriter()
 * writer.addStream(pdbInfoBytes)   // Stream 0: PDB Info
 * writer.addStream(tpiBytes)       // Stream 1: TPI
 * writer.addStream(dbiBytes)       // Stream 2: DBI
 * val pdbBytes = writer.build()
 * ```
 */
class MsfWriter(
    private val blockSize: Int = 4096,
) {
    private val streams = mutableListOf<ByteArray>()

    /** Add a stream and return its stream index. */
    fun addStream(data: ByteArray): Int {
        streams.add(data)
        return streams.size - 1
    }

    /** Add an empty stream and return its stream index. */
    fun addEmptyStream(): Int = addStream(byteArrayOf())

    /** Build the complete MSF file. */
    fun build(): ByteArray {
        // Reserved blocks: 0 = superblock, 1 = FPM1, 2 = FPM2
        var nextBlock = 3

        // Build the stream directory content
        val directoryContent = buildDirectoryContent(nextBlock)

        // Now we know block assignments. Rebuild with correct assignments.
        val streamBlockAssignments = mutableListOf<List<Int>>()
        for (stream in streams) {
            if (stream.isEmpty()) {
                streamBlockAssignments.add(emptyList())
            } else {
                val numBlocks = ceilDiv(stream.size, blockSize)
                val blocks = (nextBlock until nextBlock + numBlocks).toList()
                nextBlock += numBlocks
                streamBlockAssignments.add(blocks)
            }
        }

        // Build the actual stream directory
        val dirBytes = buildDirectory(streamBlockAssignments)
        val dirBlockCount = ceilDiv(dirBytes.size, blockSize)
        val dirBlocks = (nextBlock until nextBlock + dirBlockCount).toList()
        nextBlock += dirBlockCount

        // Directory map — list of block indices for the stream directory
        val dirMapBytes = ByteArray(dirBlockCount * 4)
        val dirMapBuf = ByteBuffer.wrap(dirMapBytes).order(ByteOrder.LITTLE_ENDIAN)
        for (block in dirBlocks) {
            dirMapBuf.putInt(block)
        }
        val dirMapBlockCount = ceilDiv(dirMapBytes.size, blockSize)
        val dirMapBlock = nextBlock
        nextBlock += dirMapBlockCount

        val totalBlocks = nextBlock

        // Allocate the output
        val output = ByteArray(totalBlocks * blockSize)

        // Write superblock
        writeSuperBlock(output, totalBlocks, dirBytes.size, dirMapBlock)

        // Write FPM (mark all blocks as allocated for simplicity)
        writeFreePageMap(output, totalBlocks)

        // Write stream data
        for (i in streams.indices) {
            writeStreamData(output, streams[i], streamBlockAssignments[i])
        }

        // Write stream directory
        writeStreamData(output, dirBytes, dirBlocks)

        // Write directory map
        writeStreamData(output, dirMapBytes, (dirMapBlock until dirMapBlock + dirMapBlockCount).toList())

        return output
    }

    private fun buildDirectoryContent(startBlock: Int): ByteArray {
        // First pass to compute directory size
        var blockOffset = startBlock
        for (stream in streams) {
            if (stream.isNotEmpty()) {
                blockOffset += ceilDiv(stream.size, blockSize)
            }
        }
        return buildDirectory(emptyList()) // placeholder for size calculation
    }

    private fun buildDirectory(assignments: List<List<Int>>): ByteArray {
        // Directory format: streamCount, then sizes[streamCount], then block lists
        val streamCount = streams.size
        var size = 4 + streamCount * 4 // header
        for (a in assignments) {
            size += a.size * 4
        }
        if (assignments.isEmpty()) {
            // Estimate size
            size = 4 + streamCount * 4
            for (stream in streams) {
                if (stream.isNotEmpty()) {
                    size += ceilDiv(stream.size, blockSize) * 4
                }
            }
        }

        val buf = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(streamCount)
        for (i in streams.indices) {
            buf.putInt(if (streams[i].isEmpty()) 0 else streams[i].size)
        }
        for (a in assignments) {
            for (block in a) {
                buf.putInt(block)
            }
        }
        return buf.array()
    }

    private fun writeSuperBlock(output: ByteArray, blockCount: Int, directorySize: Int, dirMapBlock: Int) {
        System.arraycopy(MsfSuperBlock.MAGIC, 0, output, 0, MsfSuperBlock.MAGIC.size)
        val buf = ByteBuffer.wrap(output).order(ByteOrder.LITTLE_ENDIAN)
        buf.position(32)
        buf.putInt(blockSize)        // offset 32: block size
        buf.putInt(1)                // offset 36: FPM block index
        buf.putInt(blockCount)       // offset 40: total block count
        buf.putInt(directorySize)    // offset 44: stream directory size in bytes
        buf.putInt(0)                // offset 48: unknown/reserved
        buf.putInt(dirMapBlock)      // offset 52: block index of directory map
    }

    private fun writeFreePageMap(output: ByteArray, blockCount: Int) {
        // FPM at block 1: each bit represents a block (1=free, 0=used)
        // Mark all blocks as used (0) — simple approach
        val fpmOffset = blockSize
        val fpmSize = ceilDiv(blockCount, 8)
        // All bytes already 0 (allocated), which means all blocks are used
        // This is fine — the FPM is only used for incremental updates
    }

    private fun writeStreamData(output: ByteArray, data: ByteArray, blocks: List<Int>) {
        var written = 0
        for (block in blocks) {
            val offset = block * blockSize
            val count = minOf(blockSize, data.size - written)
            if (count > 0) {
                System.arraycopy(data, written, output, offset, count)
            }
            written += count
        }
    }

    private fun ceilDiv(a: Int, b: Int): Int = (a + b - 1) / b
}
