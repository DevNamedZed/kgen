package org.wark.examples.quake1

import org.kgen.target.wasm.WasmOpCode
import org.kgen.target.wasm.disasm.WasmDisassembler
import org.kgen.target.wasm.disasm.WasmInstruction
import org.kgen.target.wasm.module.WasmModule
import org.kgen.target.wasm.module.WasmModuleReader
import org.wark.ExecutionMode
import org.wark.WarkImports
import org.wark.WarkRuntime
import org.wark.WasmTarget
import org.wark.wasi.WasiPreview1
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path

class QuakeLittleShortInvestigation {

    private val wasmPath = Path.of("../assets/quake.wasm")
    private val gameDirectory = Path.of("../assets")

    fun run() {
        val wasmBytes = Files.readAllBytes(wasmPath)
        val wasmModule = WasmModuleReader.read(wasmBytes)
        val importCount = wasmModule.importedFunctionCount
        val disasm = WasmDisassembler()

        println("Import count: $importCount")

        printComInitAnalysis(wasmModule, importCount, disasm)
        findFunctionsReferencing292500(wasmModule, importCount, disasm)
        findLittleShortReaders(wasmModule, importCount, disasm)
        runAndVerifyCorrectAddress(wasmBytes)
    }

    private fun printComInitAnalysis(wasmModule: WasmModule, importCount: Int, disasm: WasmDisassembler) {
        println("\n=== COM_Init FULL DISASSEMBLY (the function that sets swap pointers) ===")
        for ((localIndex, function) in wasmModule.functions.withIndex()) {
            val globalIndex = localIndex + importCount
            val name = wasmModule.functionName(globalIndex) ?: continue
            if (name == "COM_Init") {
                val instructions = disasm.disassemble(function.body)
                for ((instrIndex, inst) in instructions.withIndex()) {
                    println("  [$instrIndex] ${inst.opcode} ${inst.operands}")
                }

                println("\n  Swap pointer address map from COM_Init:")
                println("  BigShort(48)    → offset 292092 = 0x${Integer.toHexString(292092)}")
                println("  LittleShort(49) → offset 292596 = 0x${Integer.toHexString(292596)}")
                println("  BigLong(50)     → offset 292600 = 0x${Integer.toHexString(292600)}")
                println("  LittleLong(51)  → offset 288976 = 0x${Integer.toHexString(288976)}")
                println("  BigFloat(52)    → offset 292604 = 0x${Integer.toHexString(292604)}")
                println("  LittleFloat(53) → offset 292608 = 0x${Integer.toHexString(292608)}")
                break
            }
        }
    }

    private fun findFunctionsReferencing292500(wasmModule: WasmModule, importCount: Int, disasm: WasmDisassembler) {
        println("\n=== SEARCHING FOR FUNCTIONS THAT REFERENCE 0x47694 (292500) ===")
        println("  (This is the ASSUMED address for LittleShort)")
        val targetOffset = 292500

        for ((localIndex, function) in wasmModule.functions.withIndex()) {
            val instructions = disasm.disassemble(function.body)
            for ((instrIndex, inst) in instructions.withIndex()) {
                if (inst.opcode == WasmOpCode.I32_CONST) {
                    val value = (inst.operands as WasmInstruction.Operands.I32).value
                    if (value == targetOffset) {
                        val name = wasmModule.functionName(localIndex + importCount) ?: "func_$localIndex"
                        println("  $name: i32.const $value at [$instrIndex]")
                    }
                }
                if (inst.opcode.name.contains("LOAD") || inst.opcode.name.contains("STORE")) {
                    val memArg = inst.operands as WasmInstruction.Operands.MemArg
                    if (memArg.offset == targetOffset) {
                        val name = wasmModule.functionName(localIndex + importCount) ?: "func_$localIndex"
                        println("  $name: ${inst.opcode} offset=$targetOffset at [$instrIndex]")
                    }
                }
            }
        }
    }

    private fun findLittleShortReaders(wasmModule: WasmModule, importCount: Int, disasm: WasmDisassembler) {
        println("\n=== SEARCHING FOR FUNCTIONS THAT LOAD FROM 0x476F4 (292596) ===")
        println("  (This is the ACTUAL address for LittleShort per COM_Init)")
        val actualOffset = 292596

        for ((localIndex, function) in wasmModule.functions.withIndex()) {
            val instructions = disasm.disassemble(function.body)
            for ((instrIndex, inst) in instructions.withIndex()) {
                if (inst.opcode.name.contains("LOAD")) {
                    val memArg = inst.operands as WasmInstruction.Operands.MemArg
                    if (memArg.offset == actualOffset) {
                        val name = wasmModule.functionName(localIndex + importCount) ?: "func_$localIndex"
                        println("  $name: ${inst.opcode} offset=$actualOffset at [$instrIndex]")
                        // Print context around the load
                        val contextStart = maxOf(0, instrIndex - 3)
                        val contextEnd = minOf(instructions.size - 1, instrIndex + 5)
                        for (contextIdx in contextStart..contextEnd) {
                            val marker = if (contextIdx == instrIndex) ">>>" else "   "
                            println("    $marker [$contextIdx] ${instructions[contextIdx].opcode} ${instructions[contextIdx].operands}")
                        }
                    }
                }
                if (inst.opcode.name.contains("STORE")) {
                    val memArg = inst.operands as WasmInstruction.Operands.MemArg
                    if (memArg.offset == actualOffset) {
                        val name = wasmModule.functionName(localIndex + importCount) ?: "func_$localIndex"
                        println("  $name: ${inst.opcode} offset=$actualOffset at [$instrIndex]")
                    }
                }
            }
        }

        // Also search for LittleLong's offset (288976) to see which functions use it
        println("\n=== FUNCTIONS THAT LOAD FROM 0x468D0 (288976) = LittleLong ===")
        val littleLongOffset = 288976
        for ((localIndex, function) in wasmModule.functions.withIndex()) {
            val instructions = disasm.disassemble(function.body)
            for ((instrIndex, inst) in instructions.withIndex()) {
                if (inst.opcode.name.contains("LOAD")) {
                    val memArg = inst.operands as WasmInstruction.Operands.MemArg
                    if (memArg.offset == littleLongOffset) {
                        val name = wasmModule.functionName(localIndex + importCount) ?: "func_$localIndex"
                        println("  $name: load at [$instrIndex]")
                    }
                }
            }
        }

        // Check what's at BigShort address (292092 = 0x474FC)
        println("\n=== FUNCTIONS THAT LOAD FROM 0x474FC (292092) = BigShort ===")
        val bigShortOffset = 292092
        for ((localIndex, function) in wasmModule.functions.withIndex()) {
            val instructions = disasm.disassemble(function.body)
            for ((instrIndex, inst) in instructions.withIndex()) {
                if (inst.opcode.name.contains("LOAD")) {
                    val memArg = inst.operands as WasmInstruction.Operands.MemArg
                    if (memArg.offset == bigShortOffset) {
                        val name = wasmModule.functionName(localIndex + importCount) ?: "func_$localIndex"
                        println("  $name: load at [$instrIndex]")
                    }
                }
            }
        }

        // Show all Mod_Load* functions and their short swap usage
        println("\n=== Mod_Load* FUNCTIONS THAT USE LittleShort (call_indirect after loading a swap pointer) ===")
        val swapOffsets = setOf(292092, 292596, 292600, 288976, 292604, 292608)
        val swapOffsetNames = mapOf(
            292092 to "BigShort", 292596 to "LittleShort",
            292600 to "BigLong", 288976 to "LittleLong",
            292604 to "BigFloat", 292608 to "LittleFloat"
        )

        for ((localIndex, function) in wasmModule.functions.withIndex()) {
            val name = wasmModule.functionName(localIndex + importCount) ?: continue
            if (!name.startsWith("Mod_Load") && name != "CalcSurfaceExtents" && name != "SwapPic") {
                continue
            }
            val instructions = disasm.disassemble(function.body)
            var usesSwap = false
            for ((instrIndex, inst) in instructions.withIndex()) {
                if (inst.opcode.name.contains("LOAD")) {
                    val memArg = inst.operands as WasmInstruction.Operands.MemArg
                    if (memArg.offset in swapOffsets) {
                        usesSwap = true
                        val swapName = swapOffsetNames[memArg.offset]
                        println("  $name: loads $swapName (offset ${memArg.offset}) at [$instrIndex]")
                        val contextStart = maxOf(0, instrIndex - 2)
                        val contextEnd = minOf(instructions.size - 1, instrIndex + 4)
                        for (contextIdx in contextStart..contextEnd) {
                            println("    [$contextIdx] ${instructions[contextIdx].opcode} ${instructions[contextIdx].operands}")
                        }
                    }
                }
            }
        }
    }

    private fun runAndVerifyCorrectAddress(wasmBytes: ByteArray) {
        println("\n=== RUNTIME VERIFICATION ===")

        val runtime = WarkRuntime.create(WasmTarget.V2_0, ExecutionMode.INTERPRET)
        val module = runtime.load(wasmBytes)
        val importCount = module.wasmModule.importedFunctionCount

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

        for ((localIndex, _) in module.wasmModule.functions.withIndex()) {
            val name = module.wasmModule.functionName(localIndex + importCount)
            if (name == "__wasilibc_populate_preopens") {
                instance.callByIndex(localIndex + importCount)
                break
            }
        }

        try {
            instance.call("q_init", 32L)
        } catch (trap: org.wark.WasmTrap) {
            println("q_init trapped: ${trap.message}")
        }

        val memory = instance.memory()

        println("\nSwap function pointer locations (from COM_Init offsets):")
        println("  BigShort    [0x474FC] = ${memory.readI32(0x474FC)}")
        println("  LittleShort [0x476F4] = ${memory.readI32(0x476F4)}")
        println("  BigLong     [0x476F8] = ${memory.readI32(0x476F8)}")
        println("  LittleLong  [0x468D0] = ${memory.readI32(0x468D0)}")
        println("  BigFloat    [0x476FC] = ${memory.readI32(0x476FC)}")
        println("  LittleFloat [0x47700] = ${memory.readI32(0x47700)}")

        println("\nOLD assumed LittleShort address:")
        println("  [0x47694] = ${memory.readI32(0x47694)} (this is NOT LittleShort!)")

        // Check what variable is actually at 0x47694
        println("\nWhat is at 0x47694? Checking nearby addresses for context...")
        for (addr in 0x47680 until 0x476A0 step 4) {
            println("  [0x${Integer.toHexString(addr)}] = ${memory.readI32(addr)}")
        }

        instance.call("q_shutdown")
        wasi.fileTable().closeAll()
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            if (Files.exists(Path.of("../assets/quake.wasm"))) {
                QuakeLittleShortInvestigation().run()
            } else {
                println("quake.wasm not found at ../assets/quake.wasm")
            }
        }
    }
}
