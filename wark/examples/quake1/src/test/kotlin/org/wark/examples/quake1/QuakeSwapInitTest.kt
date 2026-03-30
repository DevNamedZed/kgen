package org.wark.examples.quake1

import org.wark.ExecutionMode
import java.nio.file.Files
import java.nio.file.Path

class QuakeSwapInitTest {

    private val wasmPath = Path.of("../assets/quake.wasm")
    private val gameDirectory = Path.of("../assets")

    fun run() {
        val wasmBytes = Files.readAllBytes(wasmPath)
        val runtime = org.wark.WarkRuntime.create(org.wark.WasmTarget.V2_0, ExecutionMode.INTERPRET)
        val module = runtime.load(wasmBytes)
        val importCount = module.wasmModule.importedFunctionCount

        // Build function table to map table slots → function names
        val functionTable = mutableMapOf<Int, String>()
        for (element in module.wasmModule.elements) {
            if (element is org.kgen.target.wasm.module.WasmModule.Element.Active) {
                for ((slot, funcIndex) in element.funcIndices.withIndex()) {
                    val name = module.wasmModule.functionName(funcIndex) ?: "func_${funcIndex - importCount}"
                    functionTable[slot] = name
                }
            }
        }

        // Known function table slots for swap functions
        val swapFunctions = mapOf(
            "ShortSwap" to 111, "ShortNoSwap" to 112,
            "LongSwap" to 113, "LongNoSwap" to 114,
            "FloatSwap" to 115, "FloatNoSwap" to 116
        )

        println("=== Swap function table slots ===")
        for ((name, localIdx) in swapFunctions) {
            val globalIdx = localIdx + importCount
            for ((slot, funcName) in functionTable) {
                if (funcName == name) {
                    println("  $name → table slot $slot (global idx $globalIdx)")
                }
            }
        }

        // Initialize the WASM instance and check the function pointers
        val host = QuakeHost()
        val setjmpEmulation = SetjmpEmulation()
        val wasi = org.wark.wasi.WasiPreview1.builder().directory(gameDirectory).build()

        val builder = org.wark.WarkImports.builder()
        host.registerImports(builder)
        wasi.registerImports(builder)
        setjmpEmulation.registerImports(builder)

        val instance = module.instantiate(builder.build())
        instance.global(0).setI32(instance.memory().sizeBytes() - 16)
        instance.call("_initialize")

        val importCountVal = module.wasmModule.importedFunctionCount
        for ((li, _) in module.wasmModule.functions.withIndex()) {
            val n = module.wasmModule.functionName(li + importCountVal)
            if (n == "__wasilibc_populate_preopens") {
                instance.callByIndex(li + importCountVal)
                break
            }
        }

        instance.call("q_init", 32L)

        val memory = instance.memory()

        // SwapPic loads from address 288976 (0x46930) — that's one of the function pointers
        // The byte-swap function pointers are typically at consecutive addresses
        // Let's scan around that address
        println("\n=== Function pointers around address 288976 (0x46930) ===")
        val baseAddress = 288960  // Start slightly before
        for (offset in 0 until 48 step 4) {
            val address = baseAddress + offset
            val tableSlot = memory.readI32(address)
            val funcName = functionTable[tableSlot] ?: "unknown (slot $tableSlot)"
            println("  mem[0x${Integer.toHexString(address)}] = $tableSlot → $funcName")
        }

        // Also check the specific known global addresses for LittleLong/LittleFloat
        // In Quake, these are typically declared together
        println("\n=== Searching for swap function pointer blocks ===")
        for (address in 0 until 500000 step 4) {
            val v0 = memory.readI32(address)
            val v1 = memory.readI32(address + 4)
            val n0 = functionTable[v0]
            val n1 = functionTable[v1]
            if (n0 != null && n1 != null &&
                (n0.contains("Swap") || n0.contains("NoSwap")) &&
                (n1.contains("Swap") || n1.contains("NoSwap"))) {
                println("  addr 0x${Integer.toHexString(address)}:")
                for (fieldOffset in 0 until 24 step 4) {
                    val slot = memory.readI32(address + fieldOffset)
                    val name = functionTable[slot] ?: "unknown ($slot)"
                    println("    [+$fieldOffset] = $slot → $name")
                }
                break
            }
        }

        instance.call("q_shutdown")
        wasi.fileTable().closeAll()
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            if (Files.exists(Path.of("../assets/quake.wasm"))) {
                QuakeSwapInitTest().run()
            }
        }
    }
}
