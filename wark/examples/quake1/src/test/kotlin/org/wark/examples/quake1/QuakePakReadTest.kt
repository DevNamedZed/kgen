package org.wark.examples.quake1

import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.CRC32

class QuakePakReadTest {

    private val pakPath = Path.of("../assets/pak0.pak")

    fun run() {
        val pakFile = RandomAccessFile(pakPath.toFile(), "r")

        pakFile.seek(4)
        val dirOffset = readIntLE(pakFile)
        val dirSize = readIntLE(pakFile)
        val entryCount = dirSize / 64

        var bspOffset = 0
        var bspSize = 0

        pakFile.seek(dirOffset.toLong())
        for (entryIndex in 0 until entryCount) {
            val nameBytes = ByteArray(56)
            pakFile.read(nameBytes)
            val name = String(nameBytes).trimEnd('\u0000')
            val fileOffset = readIntLE(pakFile)
            val fileSize = readIntLE(pakFile)
            if (name == "maps/start.bsp") {
                bspOffset = fileOffset
                bspSize = fileSize
            }
        }

        println("maps/start.bsp: offset=$bspOffset, size=$bspSize")

        pakFile.seek(bspOffset.toLong())
        val bspData = ByteArray(bspSize)
        var totalRead = 0
        while (totalRead < bspSize) {
            val bytesRead = pakFile.read(bspData, totalRead, bspSize - totalRead)
            if (bytesRead <= 0) { break }
            totalRead += bytesRead
        }
        println("Read $totalRead bytes from pak")

        val crc = CRC32()
        crc.update(bspData)
        println("CRC32 of raw BSP: ${crc.value}")

        val version = (bspData[0].toInt() and 0xFF) or
            ((bspData[1].toInt() and 0xFF) shl 8) or
            ((bspData[2].toInt() and 0xFF) shl 16) or
            ((bspData[3].toInt() and 0xFF) shl 24)
        println("BSP version: $version")

        println("\nFirst 32 bytes (hex): ${bspData.take(32).joinToString(" ") { "%02x".format(it) }}")

        println("\nNow reading same file via our WasiFileTable:")
        val wasi = org.wark.wasi.WasiPreview1.builder()
            .directory(Path.of("../assets"))
            .build()
        val table = wasi.fileTable()

        val openResult = table.openFile(3, "pak0.pak", 0)
        if (openResult is org.wark.wasi.WasiFileTable.OpenResult.Success) {
            val fd = openResult.descriptor
            println("Opened pak0.pak as fd=$fd")

            table.seek(fd, bspOffset.toLong(), 0)
            val position = table.tell(fd)
            println("After seek to $bspOffset, position=$position")

            val readBuffer = ByteArray(bspSize)
            var wasiTotalRead = 0
            while (wasiTotalRead < bspSize) {
                val chunk = ByteArray(minOf(65536, bspSize - wasiTotalRead))
                val bytesRead = table.read(fd, chunk)
                if (bytesRead <= 0) { break }
                System.arraycopy(chunk, 0, readBuffer, wasiTotalRead, bytesRead)
                wasiTotalRead += bytesRead
            }
            println("Read $wasiTotalRead bytes via WASI")

            val wasiCrc = CRC32()
            wasiCrc.update(readBuffer, 0, wasiTotalRead)
            println("CRC32 via WASI: ${wasiCrc.value}")

            println("First 32 bytes (hex): ${readBuffer.take(32).joinToString(" ") { "%02x".format(it) }}")

            var diffCount = 0
            for (byteIndex in 0 until minOf(wasiTotalRead, totalRead)) {
                if (bspData[byteIndex] != readBuffer[byteIndex]) {
                    diffCount++
                    if (diffCount <= 5) {
                        println("DIFF at byte $byteIndex: pak=0x${"%02x".format(bspData[byteIndex])} wasi=0x${"%02x".format(readBuffer[byteIndex])}")
                    }
                }
            }
            println("Total byte diffs: $diffCount / ${minOf(wasiTotalRead, totalRead)}")

            // Parse BSP face lump to check numedges
            val bspVersion2 = readI32LE(bspData, 0)
            val faceLumpOffset = readI32LE(bspData, 4 + 7 * 8)  // lump 7 = faces
            val faceLumpSize = readI32LE(bspData, 4 + 7 * 8 + 4)
            val faceCount = faceLumpSize / 20  // dface_t = 20 bytes
            println("\nFace lump: offset=$faceLumpOffset size=$faceLumpSize faces=$faceCount")
            var zeroEdgeFaces = 0
            for (faceIndex in 0 until faceCount) {
                val faceOffset = faceLumpOffset + faceIndex * 20
                val firstEdge = readI32LE(bspData, faceOffset + 4)
                val numEdges = readI16LE(bspData, faceOffset + 8)
                if (numEdges == 0) {
                    zeroEdgeFaces++
                    println("  Face $faceIndex: firstEdge=$firstEdge numEdges=0!")
                }
                if (firstEdge == 3814) {
                    println("  Face $faceIndex: firstEdge=3814 numEdges=$numEdges")
                }
            }
            println("Faces with 0 edges: $zeroEdgeFaces / $faceCount")

            table.close(fd)
        } else {
            println("Failed to open pak0.pak")
        }

        pakFile.close()
    }

    private fun readIntLE(file: RandomAccessFile): Int {
        val b0 = file.read(); val b1 = file.read(); val b2 = file.read(); val b3 = file.read()
        return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
    }

    private fun readI32LE(data: ByteArray, offset: Int): Int {
        return (data[offset].toInt() and 0xFF) or
            ((data[offset + 1].toInt() and 0xFF) shl 8) or
            ((data[offset + 2].toInt() and 0xFF) shl 16) or
            ((data[offset + 3].toInt() and 0xFF) shl 24)
    }

    private fun readI16LE(data: ByteArray, offset: Int): Int {
        val low = data[offset].toInt() and 0xFF
        val high = data[offset + 1].toInt()
        return (high shl 8) or low
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            if (Files.exists(Path.of("../assets/pak0.pak"))) {
                QuakePakReadTest().run()
            }
        }
    }
}
