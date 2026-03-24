package org.wark.compile

import org.kgen.target.wasm.disasm.WasmDisassembler
import org.kgen.target.wasm.module.WasmModule
import org.wark.compile.translate.*

/**
 * Audits WASM opcode coverage — reports which instructions in a module
 * are handled by the JIT translator AND the interpreter.
 *
 * ```kotlin
 * val audit = WasmOpcodeAudit(wasmModule)
 * println(audit.report())
 * ```
 */
class WasmOpcodeAudit(private val wasmModule: WasmModule) {

    private val translators = listOf(
        VariableTranslator(),
        ArithmeticTranslator(),
        MemoryTranslator(),
        GlobalTranslator(),
        BlockTranslator(),
        ControlFlowTranslator(wasmModule),
    )

    data class OpcodeStats(
        val mnemonic: String,
        var count: Int = 0,
        var jitHandled: Boolean = false,
        var interpreterHandled: Boolean = false,
    )

    fun audit(): Map<String, OpcodeStats> {
        val stats = mutableMapOf<String, OpcodeStats>()
        val disassembler = WasmDisassembler()

        for (function in wasmModule.functions) {
            val instructions = disassembler.disassemble(function.body)
            for (instruction in instructions) {
                val mnemonic = instruction.opcode.mnemonic
                val stat = stats.getOrPut(mnemonic) { OpcodeStats(mnemonic) }
                stat.count++
                if (!stat.jitHandled) {
                    stat.jitHandled = translators.any { it.canHandle(mnemonic) }
                }
                if (!stat.interpreterHandled) {
                    stat.interpreterHandled = INTERPRETER_OPCODES.contains(mnemonic)
                }
            }
        }

        return stats
    }

    fun report(): String {
        val stats = audit()
        val sorted = stats.values.sortedByDescending { it.count }
        val totalInstructions = sorted.sumOf { it.count }

        val jitHandled = sorted.filter { it.jitHandled }
        val jitUnhandled = sorted.filter { !it.jitHandled }
        val interpHandled = sorted.filter { it.interpreterHandled }
        val interpUnhandled = sorted.filter { !it.interpreterHandled }

        val builder = StringBuilder()
        builder.appendLine("=== WASM Opcode Coverage ===")
        builder.appendLine("Total: $totalInstructions instructions, ${sorted.size} unique opcodes")
        builder.appendLine("JIT:         ${jitHandled.size} handled, ${jitUnhandled.size} missing")
        builder.appendLine("Interpreter: ${interpHandled.size} handled, ${interpUnhandled.size} missing")

        val bothMissing = sorted.filter { !it.jitHandled && !it.interpreterHandled }
        val jitOnly = sorted.filter { it.jitHandled && !it.interpreterHandled }
        val interpOnly = sorted.filter { !it.jitHandled && it.interpreterHandled }

        if (bothMissing.isNotEmpty()) {
            builder.appendLine()
            builder.appendLine("--- Missing from BOTH (critical) ---")
            for (stat in bothMissing) {
                builder.appendLine("  ${stat.mnemonic}: ${stat.count} occurrences")
            }
        }

        if (jitOnly.isNotEmpty()) {
            builder.appendLine()
            builder.appendLine("--- JIT only (interpreter missing) ---")
            for (stat in jitOnly) {
                builder.appendLine("  ${stat.mnemonic}: ${stat.count} occurrences")
            }
        }

        if (interpOnly.isNotEmpty()) {
            builder.appendLine()
            builder.appendLine("--- Interpreter only (JIT missing) ---")
            for (stat in interpOnly) {
                builder.appendLine("  ${stat.mnemonic}: ${stat.count} occurrences")
            }
        }

        return builder.toString()
    }

    companion object {
        /**
         * All opcodes handled by the WasmInterpreter.
         * Must be kept in sync with the when() block in WasmInterpreter.execute().
         */
        val INTERPRETER_OPCODES = setOf(
            "i32.const", "i64.const", "f32.const", "f64.const",
            "local.get", "local.set", "local.tee",
            "i32.add", "i32.sub", "i32.mul", "i32.div_s", "i32.div_u",
            "i32.rem_s", "i32.rem_u", "i32.and", "i32.or", "i32.xor",
            "i32.shl", "i32.shr_s", "i32.shr_u", "i32.rotl", "i32.rotr",
            "i64.add", "i64.sub", "i64.mul", "i64.div_s", "i64.div_u",
            "i64.rem_s", "i64.rem_u", "i64.and", "i64.or", "i64.xor",
            "i64.shl", "i64.shr_s", "i64.shr_u", "i64.rotl", "i64.rotr",
            "f32.add", "f32.sub", "f32.mul", "f32.div", "f32.neg", "f32.abs", "f32.sqrt",
            "f32.ceil", "f32.floor", "f32.trunc", "f32.nearest",
            "f32.min", "f32.max", "f32.copysign",
            "f32.eq", "f32.ne", "f32.lt", "f32.gt", "f32.le", "f32.ge",
            "f64.add", "f64.sub", "f64.mul", "f64.div", "f64.neg", "f64.abs", "f64.sqrt",
            "f64.ceil", "f64.floor", "f64.trunc", "f64.nearest",
            "f64.min", "f64.max", "f64.copysign",
            "f64.eq", "f64.ne", "f64.lt", "f64.gt", "f64.le", "f64.ge",
            "i32.eqz", "i32.eq", "i32.ne", "i32.lt_s", "i32.lt_u", "i32.gt_s", "i32.gt_u",
            "i32.le_s", "i32.le_u", "i32.ge_s", "i32.ge_u",
            "i32.clz", "i32.ctz", "i32.popcnt",
            "i64.eqz", "i64.eq", "i64.ne", "i64.lt_s", "i64.lt_u", "i64.gt_s", "i64.gt_u",
            "i64.le_s", "i64.le_u", "i64.ge_s", "i64.ge_u",
            "i64.clz", "i64.ctz", "i64.popcnt",
            "i32.wrap_i64", "i64.extend_i32_s", "i64.extend_i32_u",
            "f64.convert_i32_s", "f64.convert_i32_u", "f64.convert_i64_s", "f64.convert_i64_u",
            "f32.convert_i32_s", "f32.convert_i32_u", "f32.convert_i64_s", "f32.convert_i64_u",
            "i32.trunc_f64_s", "i32.trunc_f64_u", "i32.trunc_f32_s", "i32.trunc_f32_u",
            "i64.trunc_f64_s", "i64.trunc_f64_u", "i64.trunc_f32_s", "i64.trunc_f32_u",
            "f64.promote_f32", "f32.demote_f64",
            "i32.reinterpret_f32", "f32.reinterpret_i32",
            "i64.reinterpret_f64", "f64.reinterpret_i64",
            "i32.extend8_s", "i32.extend16_s",
            "i64.extend8_s", "i64.extend16_s", "i64.extend32_s",
            "i32.load", "i64.load", "f32.load", "f64.load",
            "i32.load8_s", "i32.load8_u", "i32.load16_s", "i32.load16_u",
            "i64.load8_s", "i64.load8_u", "i64.load16_s", "i64.load16_u",
            "i64.load32_s", "i64.load32_u",
            "i32.store", "i64.store", "f32.store", "f64.store",
            "i32.store8", "i32.store16", "i64.store8", "i64.store16", "i64.store32",
            "global.get", "global.set",
            "memory.copy", "memory.fill", "memory.init", "data.drop",
            "memory.size", "memory.grow",
            "drop", "select",
            "call", "call_indirect", "return",
            "block", "loop", "if", "else", "end",
            "br", "br_if", "br_table",
            "unreachable", "nop",
        )
    }
}
