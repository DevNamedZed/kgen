package org.wark.examples.quake1

import org.wark.ExecutionMode
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.Path

class QuakeBspVerifyTest {

    private val wasmPath = Path.of("../assets/quake.wasm")
    private val gameDirectory = Path.of("../assets")
    private val pakPath = Path.of("../assets/pak0.pak")

    fun run() {
        println("=== Verifying BSP data integrity ===")

        val pakFile = RandomAccessFile(pakPath.toFile(), "r")
        val pakMagic = ByteArray(4)
        pakFile.read(pakMagic)
        println("PAK magic: ${String(pakMagic)} (expect PACK)")

        pakFile.seek(4)
        val dirOffset = pakFile.readIntLE()
        val dirSize = pakFile.readIntLE()
        val entryCount = dirSize / 64
        println("PAK directory: offset=$dirOffset, size=$dirSize, entries=$entryCount")

        pakFile.seek(dirOffset.toLong())
        var bspOffset = -1L
        var bspSize = -1
        for (entryIndex in 0 until entryCount) {
            val nameBytes = ByteArray(56)
            pakFile.read(nameBytes)
            val name = String(nameBytes).trimEnd('\u0000')
            val fileOffset = pakFile.readIntLE()
            val fileSize = pakFile.readIntLE()
            if (name == "maps/start.bsp") {
                bspOffset = fileOffset.toLong()
                bspSize = fileSize
                println("Found maps/start.bsp: offset=$bspOffset, size=$bspSize")
            }
        }

        if (bspOffset < 0) {
            println("maps/start.bsp NOT FOUND in pak!")
            return
        }

        pakFile.seek(bspOffset)
        val bspVersion = pakFile.readIntLE()
        println("BSP version: $bspVersion (expect 29 for Quake 1)")

        val lumps = arrayOf("entities", "planes", "textures", "vertices", "visibility",
            "nodes", "texinfo", "faces", "lighting", "clipnodes",
            "leaves", "marksurfaces", "edges", "surfedges", "models")
        for ((lumpIndex, lumpName) in lumps.withIndex()) {
            val lumpOffset = pakFile.readIntLE()
            val lumpSize = pakFile.readIntLE()
            if (lumpName == "vertices" || lumpName == "texinfo" || lumpName == "faces" || lumpName == "edges" || lumpName == "surfedges") {
                println("  Lump $lumpIndex ($lumpName): offset=$lumpOffset, size=$lumpSize")
            }
        }

        println("\n=== Now loading via WASM and comparing ===")
        val runner = QuakeRunner.load(wasmPath, gameDirectory, ExecutionMode.INTERPRET)
        runner.initialize()
        for (frameIndex in 1..3) { runner.frame(1.0f / 30.0f) }

        runner.keyEvent(27, true)
        runner.frame(1.0f / 30.0f)
        runner.keyEvent(27, false)
        for (frameIndex in 1..3) { runner.frame(1.0f / 30.0f) }

        runner.keyEvent(13, true)
        runner.frame(1.0f / 30.0f)
        runner.keyEvent(13, false)
        for (frameIndex in 1..3) { runner.frame(1.0f / 30.0f) }

        runner.keyEvent(13, true)
        runner.frame(1.0f / 30.0f)
        runner.keyEvent(13, false)
        for (frameIndex in 1..5) { runner.frame(1.0f / 30.0f) }

        val memory = runner.memory()
        val memSize = memory.sizeBytes()
        println("WASM memory size: $memSize")

        pakFile.seek(bspOffset)
        val bspData = ByteArray(minOf(bspSize, 256))
        pakFile.read(bspData)

        println("\nSearching WASM memory for BSP header (version $bspVersion)...")
        var foundCount = 0
        for (address in 0 until memSize - 4 step 4) {
            val value = memory.readI32(address)
            if (value == bspVersion) {
                val nextValue = memory.readI32(address + 4)
                if (nextValue > 0 && nextValue < bspSize) {
                    println("  BSP header candidate at 0x${Integer.toHexString(address)}: version=$value, first lump offset=$nextValue")
                    var matchCount = 0
                    for (byteIndex in 0 until minOf(64, bspData.size)) {
                        if (memory.readByte(address + byteIndex).toInt() == bspData[byteIndex].toInt()) {
                            matchCount++
                        }
                    }
                    println("    Matches first 64 bytes: $matchCount/64")
                    foundCount++
                    if (foundCount >= 3) { break }
                }
            }
        }

        runner.shutdown()
        pakFile.close()
    }

    private fun RandomAccessFile.readIntLE(): Int {
        val byte0 = read()
        val byte1 = read()
        val byte2 = read()
        val byte3 = read()
        return byte0 or (byte1 shl 8) or (byte2 shl 16) or (byte3 shl 24)
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val test = QuakeBspVerifyTest()
            if (Files.exists(test.pakPath)) {
                test.run()
            }
        }
    }
}
