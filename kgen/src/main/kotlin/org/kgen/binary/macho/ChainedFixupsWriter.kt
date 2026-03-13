package org.kgen.binary.macho

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Serializes [ChainedFixups] to the binary format used by LC_DYLD_CHAINED_FIXUPS.
 *
 * Produces the fixups payload that sits at the file offset referenced by the
 * load command. The caller is responsible for emitting the LC_DYLD_CHAINED_FIXUPS
 * load command header (cmd, cmdsize, dataoff, datasize).
 *
 * ```java
 * var fixups = new ChainedFixups(0, DYLD_CHAINED_IMPORT, 0, imports, segments);
 * byte[] payload = ChainedFixupsWriter.write(fixups);
 * // embed payload at dataoff in the Mach-O file
 * ```
 */
object ChainedFixupsWriter {

    /**
     * Serialize a [ChainedFixups] to its binary representation.
     *
     * @param fixups the chained fixups data to serialize
     * @return the binary payload (fixups header + starts-in-image + imports + symbol pool)
     */
    @JvmStatic
    fun write(fixups: ChainedFixups): ByteArray {
        val symbolPool = buildSymbolPool(fixups.imports.map { it.name })
        val symbolOffsets = computeSymbolOffsets(fixups.imports.map { it.name })

        // Build starts-in-image data
        val maxSegIndex = if (fixups.segments.isEmpty()) {
            0
        } else {
            fixups.segments.maxOf { it.segmentIndex } + 1
        }
        val segCount = maxOf(maxSegIndex, 1)
        val startsHeaderSize = 4 + segCount * 4
        val segStartsData = fixups.segments.map { writeSegmentStarts(it) }
        val segInfoOffsets = computeSegInfoOffsets(fixups.segments, segCount, startsHeaderSize, segStartsData)
        val startsSize = startsHeaderSize + segStartsData.sumOf { it.size }

        val importEntrySize = when (fixups.importsFormat) {
            ChainedImportFormat.DYLD_CHAINED_IMPORT -> 4
            ChainedImportFormat.DYLD_CHAINED_IMPORT_ADDEND -> 8
            ChainedImportFormat.DYLD_CHAINED_IMPORT_ADDEND64 -> 16
        }

        val headerSize = 28
        val startsOffset = headerSize
        val importsOffset = startsOffset + startsSize
        val symbolsOffset = importsOffset + fixups.imports.size * importEntrySize

        val totalSize = symbolsOffset + symbolPool.size
        val buf = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)

        // Header (28 bytes)
        buf.putInt(fixups.fixupsVersion)
        buf.putInt(startsOffset)
        buf.putInt(importsOffset)
        buf.putInt(symbolsOffset)
        buf.putInt(fixups.imports.size)
        buf.putInt(fixups.importsFormat.code)
        buf.putInt(fixups.symbolsFormat)

        // Starts-in-image
        buf.putInt(segCount)
        for (i in 0 until segCount) {
            buf.putInt(segInfoOffsets[i])
        }
        for (data in segStartsData) {
            buf.put(data)
        }

        // Imports
        for ((idx, imp) in fixups.imports.withIndex()) {
            writeImport(buf, fixups.importsFormat, imp, symbolOffsets[idx])
        }

        // Symbol pool
        buf.put(symbolPool)

        val result = ByteArray(buf.position())
        buf.flip()
        buf.get(result)
        return result
    }

    private fun writeImport(
        buf: ByteBuffer,
        format: ChainedImportFormat,
        imp: ChainedFixupImport,
        symbolOffset: Int,
    ) {
        when (format) {
            ChainedImportFormat.DYLD_CHAINED_IMPORT -> {
                // 4 bytes: libOrdinal[7:0] | weakImport[8] | nameOffset[31:9]
                val packed = (imp.libOrdinal and 0xFF) or
                    ((if (imp.weakImport) 1 else 0) shl 8) or
                    ((symbolOffset and 0x7FFFFF) shl 9)
                buf.putInt(packed)
            }
            ChainedImportFormat.DYLD_CHAINED_IMPORT_ADDEND -> {
                // 4 bytes packed + 4 bytes addend
                val packed = (imp.libOrdinal and 0xFF) or
                    ((if (imp.weakImport) 1 else 0) shl 8) or
                    ((symbolOffset and 0x7FFFFF) shl 9)
                buf.putInt(packed)
                buf.putInt(imp.addend.toInt())
            }
            ChainedImportFormat.DYLD_CHAINED_IMPORT_ADDEND64 -> {
                // 8 bytes packed + 8 bytes addend
                val packed = (imp.libOrdinal.toLong() and 0xFFFF) or
                    ((if (imp.weakImport) 1L else 0L) shl 16) or
                    ((symbolOffset.toLong() and 0x7FFF) shl 17)
                buf.putLong(packed)
                buf.putLong(imp.addend)
            }
        }
    }

    private fun writeSegmentStarts(seg: ChainedFixupSegment): ByteArray {
        val size = 22 + seg.pageStarts.size * 2
        val buf = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(size)                                    // size
        buf.putShort(seg.pageSize.toShort())                // page_size
        buf.putShort(seg.pointerFormat.code.toShort())      // pointer_format
        buf.putLong(seg.segmentOffset)                      // segment_offset
        buf.putInt(seg.maxValidPointer.toInt())              // max_valid_pointer
        buf.putShort(seg.pageStarts.size.toShort())          // page_count
        for (ps in seg.pageStarts) {
            buf.putShort(ps.toShort())
        }
        buf.flip()
        val result = ByteArray(buf.remaining())
        buf.get(result)
        return result
    }

    private fun computeSegInfoOffsets(
        segments: List<ChainedFixupSegment>,
        segCount: Int,
        startsHeaderSize: Int,
        segStartsData: List<ByteArray>,
    ): IntArray {
        val offsets = IntArray(segCount)
        var dataOffset = startsHeaderSize
        val segByIndex = segments.associateBy { it.segmentIndex }
        for (i in 0 until segCount) {
            val seg = segByIndex[i]
            if (seg != null) {
                offsets[i] = dataOffset
                val idx = segments.indexOf(seg)
                dataOffset += segStartsData[idx].size
            }
        }
        return offsets
    }

    private fun buildSymbolPool(names: List<String>): ByteArray {
        val pool = ByteArrayOutputStream()
        for (name in names) {
            pool.write(name.toByteArray(Charsets.US_ASCII))
            pool.write(0)
        }
        return pool.toByteArray()
    }

    private fun computeSymbolOffsets(names: List<String>): List<Int> {
        val offsets = mutableListOf<Int>()
        var pos = 0
        for (name in names) {
            offsets.add(pos)
            pos += name.length + 1
        }
        return offsets
    }
}
