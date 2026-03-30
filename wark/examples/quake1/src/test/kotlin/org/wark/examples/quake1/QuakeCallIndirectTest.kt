package org.wark.examples.quake1

import org.wark.ExecutionMode
import org.wark.WarkImports
import org.wark.WarkRuntime
import org.wark.WasmTarget
import org.wark.wasi.WasiPreview1
import java.nio.file.Files
import java.nio.file.Path

class QuakeCallIndirectTest {

    private val wasmPath = Path.of("../assets/quake.wasm")
    private val gameDirectory = Path.of("../assets")

    fun run() {
        val wasmBytes = Files.readAllBytes(wasmPath)
        val runtime = WarkRuntime.create(WasmTarget.V2_0, ExecutionMode.INTERPRET)
        val module = runtime.load(wasmBytes)

        val host = QuakeHost()
        val setjmpEmulation = SetjmpEmulation()
        val wasi = WasiPreview1.builder().directory(gameDirectory).build()
        val builder = WarkImports.builder()
        host.registerImports(builder)
        wasi.registerImports(builder)
        setjmpEmulation.registerImports(builder)
        val instance = module.instantiate(builder.build())
        instance.global(0).setI32(instance.memory().sizeBytes() - 16)
        instance.call("_initialize")

        val importCount = module.wasmModule.importedFunctionCount
        for ((li, _) in module.wasmModule.functions.withIndex()) {
            val n = module.wasmModule.functionName(li + importCount)
            if (n == "__wasilibc_populate_preopens") {
                instance.callByIndex(li + importCount)
                break
            }
        }
        instance.call("q_init", 32L)

        val memory = instance.memory()

        println("=== Direct call_indirect verification ===")
        println("Element offset: 1")
        println()

        val addresses = mapOf(
            "BigShort" to 0x474FC,
            "LittleShort" to 0x476F4,
            "BigLong" to 0x476F8,
            "LittleLong" to 0x468D0,
            "BigFloat" to 0x476FC,
            "LittleFloat" to 0x47700,
        )

        for ((name, address) in addresses) {
            val tableIndex = memory.readI32(address)
            println("$name: mem[0x${Integer.toHexString(address)}] = table index $tableIndex")
        }

        println()
        println("=== Testing LittleLong(111120) via interpreter call_indirect ===")

        val littleLongIndex = memory.readI32(0x468D0)
        println("LittleLong table index: $littleLongIndex")

        val interp = instance.interpreterInstance()
        if (interp != null) {
            val funcTableEntry = interp.functionTableEntry(littleLongIndex)
            println("functionTable[$littleLongIndex] = func $funcTableEntry")
            val funcName = module.wasmModule.functionName(funcTableEntry)
            println("func $funcTableEntry = $funcName")

            val result = interp.call(funcTableEntry, longArrayOf(111120L))
            println("call($funcName, 111120) = ${result[0]}")
            println(if (result[0] == 111120L) "✓ CORRECT — LittleLong is identity" else "✗ WRONG — LittleLong corrupts the value!")
        }

        println()
        println("=== Testing LittleShort(6) via interpreter call_indirect ===")
        val littleShortIndex = memory.readI32(0x476F4)
        println("LittleShort table index: $littleShortIndex")
        if (interp != null) {
            val funcTableEntry = interp.functionTableEntry(littleShortIndex)
            println("functionTable[$littleShortIndex] = func $funcTableEntry")
            val funcName = module.wasmModule.functionName(funcTableEntry)
            println("func $funcTableEntry = $funcName")

            val result = interp.call(funcTableEntry, longArrayOf(6L))
            println("call($funcName, 6) = ${result[0]}")
            println(if (result[0] == 6L) "✓ CORRECT" else "✗ WRONG")
        }

        wasi.fileTable().closeAll()
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            if (Files.exists(Path.of("../assets/quake.wasm"))) {
                QuakeCallIndirectTest().run()
            }
        }
    }
}
