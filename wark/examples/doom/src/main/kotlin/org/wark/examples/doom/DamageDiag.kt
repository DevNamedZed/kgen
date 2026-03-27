package org.wark.examples.doom

import org.kgen.ir.target.Target
import org.kgen.ir.text.IrPrinter
import org.kgen.pipeline.Mem2Reg
import org.kgen.target.wasm.disasm.WasmDisassembler
import org.kgen.target.wasm.module.WasmModuleReader
import org.wark.compile.WasmToIrCompiler
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

object DamageDiag {

    @JvmStatic
    fun main(args: Array<String>) {
        val wasmPath = args.firstOrNull()?.let { Path.of(it) } ?: Path.of("../assets/doom.wasm")
        if (!Files.exists(wasmPath)) {
            println("WASM not found: $wasmPath")
            return
        }

        val wasmBytes = Files.readAllBytes(wasmPath)
        val wasmModule = WasmModuleReader.read(wasmBytes)
        val importCount = wasmModule.importedFunctionCount
        val disassembler = WasmDisassembler()

        println("Functions: ${wasmModule.functions.size}, imports: $importCount")

        // Find damage-path functions by name
        val targetNames = setOf(
            "PTR_ShootTraverse", "PTR_AimTraverse", "P_DamageMobj",
            "P_LineAttack", "P_TraverseIntercepts", "P_PathTraverse",
        )
        val foundFunctions = mutableMapOf<String, Int>()

        for ((localIndex, func) in wasmModule.functions.withIndex()) {
            val globalIndex = localIndex + importCount
            val name = wasmModule.functionName(globalIndex) ?: "func_$localIndex"
            if (name in targetNames) {
                foundFunctions[name] = localIndex
                println("  $name = func_${localIndex + importCount} (local $localIndex)")
            }
        }

        // Also find by table slot - slot 137 = PTR_ShootTraverse, slot 136 = PTR_AimTraverse
        println()
        println("=== Function table entries around damage callbacks ===")
        for (element in wasmModule.elements) {
            if (element is org.kgen.target.wasm.module.WasmModule.Element.Active) {
                val offsetExpr = element.offsetExpr
                var offset = 0
                if (offsetExpr.isNotEmpty() && offsetExpr[0].toInt() and 0xFF == 0x41) {
                    var shift = 0; var position = 1
                    while (position < offsetExpr.size) {
                        val byte = offsetExpr[position].toInt() and 0xFF; position++
                        offset = offset or ((byte and 0x7F) shl shift); shift += 7
                        if (byte and 0x80 == 0) { break }
                    }
                }
                for ((slotOffset, funcIndex) in element.funcIndices.withIndex()) {
                    val slot = offset + slotOffset
                    if (slot in 130..145) {
                        val localIdx = funcIndex - importCount
                        val name = wasmModule.functionName(funcIndex) ?: "func_$localIdx"
                        val funcType = if (funcIndex >= importCount && localIdx < wasmModule.functions.size) {
                            val typeIdx = wasmModule.functions[localIdx].typeIndex
                            val ft = wasmModule.types[typeIdx]
                            "(${ft.params.joinToString(",") { it.name }}) -> (${ft.results.joinToString(",") { it.name }})"
                        } else {
                            "?"
                        }
                        println("  table[$slot] = func_$funcIndex ($name) $funcType")
                    }
                }
            }
        }

        // Disassemble PTR_ShootTraverse WASM bytecode
        val shootTraverseLocal = findLocalIndex(wasmModule, importCount, "PTR_ShootTraverse")
        if (shootTraverseLocal >= 0) {
            println()
            println("=== PTR_ShootTraverse WASM disassembly ===")
            val func = wasmModule.functions[shootTraverseLocal]
            val instructions = disassembler.disassemble(func.body)
            println("  ${instructions.size} instructions, ${func.locals.size} locals")
            for (inst in instructions) {
                println("  ${inst.text()}")
            }

            // Check what functions PTR_ShootTraverse calls
            println()
            println("=== PTR_ShootTraverse call targets ===")
            for (inst in instructions) {
                if (inst.opcode.mnemonic == "call") {
                    val callIndex = (inst.operands as org.kgen.target.wasm.disasm.WasmInstruction.Operands.Index).value
                    val callName = wasmModule.functionName(callIndex) ?: "func_${callIndex - importCount}"
                    println("  calls func_$callIndex ($callName)")
                }
            }
        }

        // Compile to IR and dump PTR_ShootTraverse
        println()
        println("=== Compiling WASM -> IR ===")
        val compiler = WasmToIrCompiler(Target.x86_64(), wasmModule)
        var irModule = compiler.compileAll()
        irModule = Mem2Reg().run(irModule)

        val shootName = if (shootTraverseLocal >= 0) {
            wasmModule.functionName(shootTraverseLocal + importCount) ?: "func_$shootTraverseLocal"
        } else {
            "func_630" // fallback
        }

        val irFunc = irModule.functions.find { it.name == shootName }
        if (irFunc != null) {
            println()
            println("=== PTR_ShootTraverse IR ($shootName) ===")
            println("  ${irFunc.blocks.size} blocks, ${irFunc.params.size} params")
            val irText = printFunction(irFunc)
            println(irText)

            val outFile = File("PTR_ShootTraverse_ir.txt")
            outFile.writeText(irText)
            println("(Written to ${outFile.absolutePath})")
        } else {
            println("Could not find $shootName in IR module")
            println("Available functions containing 'Shoot' or 'Traverse':")
            for (func in irModule.functions) {
                if (func.name.contains("Shoot") || func.name.contains("Traverse") || func.name.contains("PTR")) {
                    println("  ${func.name}")
                }
            }
        }

        // Also dump P_DamageMobj if it exists
        val damageLocal = findLocalIndex(wasmModule, importCount, "P_DamageMobj")
        if (damageLocal >= 0) {
            val damageName = wasmModule.functionName(damageLocal + importCount) ?: "func_$damageLocal"
            val damageFunc = irModule.functions.find { it.name == damageName }
            if (damageFunc != null) {
                println()
                println("=== P_DamageMobj IR ($damageName) ===")
                val damageText = printFunction(damageFunc)
                File("P_DamageMobj_ir.txt").writeText(damageText)
                println("  ${damageFunc.blocks.size} blocks (written to P_DamageMobj_ir.txt)")
            }
        }

        // Check call_indirect dispatchers for type coverage
        println()
        println("=== call_indirect dispatch analysis ===")
        for (func in irModule.functions) {
            if (func.name.startsWith("__wark_call_indirect")) {
                val callTargets = mutableListOf<String>()
                for (block in func.blocks) {
                    for (inst in block.instructions) {
                        if (inst is org.kgen.ir.instructions.Call) {
                            val target = inst.function.name
                            if (!target.startsWith("__wark_")) {
                                callTargets.add(target)
                            }
                        }
                    }
                }
                println("  ${func.name}: dispatches to ${callTargets.size} targets")
                for (target in callTargets.take(20)) {
                    println("    -> $target")
                }
                if (callTargets.size > 20) {
                    println("    ... and ${callTargets.size - 20} more")
                }
            }
        }
    }

    private fun printFunction(func: org.kgen.ir.IrFunction): String {
        val sb = StringBuilder()
        val params = func.params.joinToString(", ") { "${it.name}: ${it.type}" }
        sb.appendLine("define ${func.returnType} @${func.name}($params) {")
        for (block in func.blocks) {
            sb.appendLine("${block.label}:")
            for (inst in block.instructions) {
                sb.appendLine("  $inst")
            }
        }
        sb.appendLine("}")
        return sb.toString()
    }

    private fun findLocalIndex(wasmModule: org.kgen.target.wasm.module.WasmModule, importCount: Int, name: String): Int {
        for ((localIndex, func) in wasmModule.functions.withIndex()) {
            val globalIndex = localIndex + importCount
            val funcName = wasmModule.functionName(globalIndex)
            if (funcName == name) {
                return localIndex
            }
        }
        return -1
    }
}
