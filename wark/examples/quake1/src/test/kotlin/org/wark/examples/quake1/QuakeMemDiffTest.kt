package org.wark.examples.quake1

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import org.wark.ExecutionMode
import java.nio.file.Files
import java.nio.file.Path

class QuakeMemDiffTest {

    private val wasmPath = Path.of("../assets/quake.wasm")
    private val gameDirectory = Path.of("../assets")
    private fun quakeWasmExists(): Boolean = Files.exists(wasmPath)

    private fun initAndRunFrames(mode: ExecutionMode, frameCount: Int = 10): QuakeRunner {
        val runner = QuakeRunner.load(wasmPath, gameDirectory, mode)
        runner.initialize()
        val deltaTime = 1.0f / 30.0f
        for (frameIndex in 1..frameCount) {
            runner.frame(deltaTime)
        }
        return runner
    }

    @Test
    @EnabledIf("quakeWasmExists")
    fun compareMemoryAtKeyAddresses() {
        val frameCount = System.getProperty("frames", "0").toInt()
        println("=== Comparing with $frameCount frames ===")

        println("=== Loading with INTERPRETER ===")
        val interpRunner = initAndRunFrames(ExecutionMode.INTERPRET, frameCount)

        println("=== Loading with JIT ===")
        val jitRunner = initAndRunFrames(ExecutionMode.JIT, frameCount)

        val addresses = listOf(293648, 293936, 293940, 293944)
        println("\n=== Memory comparison at key addresses ===")
        for (address in addresses) {
            val interpValue = interpRunner.memory().readI32(address)
            val jitValue = jitRunner.memory().readI32(address)
            val match = if (interpValue == jitValue) "MATCH" else "MISMATCH"
            println("  mem[$address]: interp=0x${Integer.toHexString(interpValue)} jit=0x${Integer.toHexString(jitValue)} $match")
        }

        println("\n=== Linked list at 293648 (COM_LoadFile cache) ===")
        var interpPtr = interpRunner.memory().readI32(293648)
        var jitPtr = jitRunner.memory().readI32(293648)
        println("  Head: interp=0x${Integer.toHexString(interpPtr)} jit=0x${Integer.toHexString(jitPtr)}")

        for (step in 0..5) {
            if (interpPtr == 0 && jitPtr == 0) {
                break
            }
            if (interpPtr != 0) {
                val field128 = interpRunner.memory().readI32(interpPtr + 128)
                val field132 = interpRunner.memory().readI32(interpPtr + 132)
                println("  interp[$step]: ptr=0x${Integer.toHexString(interpPtr)} [+128]=0x${Integer.toHexString(field128)} [+132]=0x${Integer.toHexString(field132)}")
                interpPtr = field132
            }
            if (jitPtr != 0) {
                val field128 = jitRunner.memory().readI32(jitPtr + 128)
                val field132 = jitRunner.memory().readI32(jitPtr + 132)
                println("  jit[$step]: ptr=0x${Integer.toHexString(jitPtr)} [+128]=0x${Integer.toHexString(field128)} [+132]=0x${Integer.toHexString(field132)}")
                jitPtr = field132
            }
        }

        println("\n=== Global variables ===")
        val interpGlobal0 = interpRunner.instance().global(0).rawValue()
        val jitGlobal0 = jitRunner.instance().global(0).rawValue()
        println("  global[0] (SP): interp=0x${java.lang.Long.toHexString(interpGlobal0)} jit=0x${java.lang.Long.toHexString(jitGlobal0)} ${if (interpGlobal0 == jitGlobal0) "MATCH" else "MISMATCH"}")

        println("\n=== Wide memory scan for differences ===")
        val interpMem = interpRunner.memory()
        val jitMem = jitRunner.memory()
        val minSize = minOf(interpMem.sizeBytes(), jitMem.sizeBytes())
        var diffCount = 0
        var firstDiffAddr = -1
        for (address in 0 until minSize step 4) {
            val interpVal = interpMem.readI32(address)
            val jitVal = jitMem.readI32(address)
            if (interpVal != jitVal) {
                diffCount++
                if (firstDiffAddr == -1) {
                    firstDiffAddr = address
                }
                if (diffCount <= 20) {
                    println("  DIFF at 0x${Integer.toHexString(address)}: interp=0x${Integer.toHexString(interpVal)} jit=0x${Integer.toHexString(jitVal)}")
                }
            }
        }
        println("  Total diffs: $diffCount (first at 0x${Integer.toHexString(firstDiffAddr)})")

        interpRunner.shutdown()
        jitRunner.shutdown()
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val test = QuakeMemDiffTest()
            if (test.quakeWasmExists()) {
                test.compareMemoryAtKeyAddresses()
            }
        }
    }
}
