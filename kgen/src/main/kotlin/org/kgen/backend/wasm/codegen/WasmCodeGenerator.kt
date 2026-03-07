package org.kgen.backend.wasm.codegen

import org.kgen.backend.wasm.*
import org.kgen.backend.wasm.asm.*
import org.kgen.ir.*
import org.kgen.ir.codegen.*

/**
 * Translates an IR [Module] into a WASM binary (.wasm).
 *
 * Supports a subset of low-level IR instructions that map naturally to WASM:
 * integer/float arithmetic, comparisons, calls, returns.
 *
 * External function declarations become WASM imports (from "env" module).
 * Functions with external linkage become WASM exports.
 */
class WasmCodeGenerator : CodeGenerator {

    override val targetName: String = "wasm"

    override fun generate(module: Module, options: CodeGenOptions): ByteArray {
        val asm = WasmAssembler.create()

        // Import external functions
        val importedFuncs = mutableSetOf<String>()
        for (fn in module.functions) {
            if (fn.isExternal) {
                val results = if (fn.returnType == Type.Void) emptyList() else listOf(irTypeToWasm(fn.returnType))
                asm.importFunction("env", fn.name, fn.params.map { irTypeToWasm(it.type) }, results)
                importedFuncs.add(fn.name)
            }
        }

        // Emit defined functions
        for (fn in module.functions) {
            if (fn.isExternal) continue

            val params = fn.params.map { irTypeToWasm(it.type) }
            val results = if (fn.returnType == Type.Void) emptyList() else listOf(irTypeToWasm(fn.returnType))
            val isExported = fn.linkage == Linkage.EXTERNAL

            asm.function(fn.name, params, results, exported = isExported) { wasmFn, a ->
                val locals = mutableMapOf<String, Int>()
                fn.params.forEachIndexed { i, p -> locals[p.name] = i }

                // Pre-declare locals for instruction results
                for (block in fn.blocks) {
                    for (inst in block.instructions) {
                        val ref = inst.result
                        if (ref != null && ref.name !in locals) {
                            val wasmType = irTypeToWasm(ref.type)
                            val local = a.declareLocal(ref.name, wasmType)
                            locals[ref.name] = local.index
                        }
                    }
                }

                for (block in fn.blocks) {
                    for (inst in block.instructions) {
                        emitInstruction(inst, locals, a)
                    }
                }
            }
        }

        return asm.assemble()
    }

    private fun emitInstruction(inst: Instruction, locals: Map<String, Int>, asm: WasmAssembler) {
        when (inst) {
            is Instruction.Add -> {
                pushValue(inst.lhs, locals, asm)
                pushValue(inst.rhs, locals, asm)
                when (inst.lhs.type) { Type.I32 -> asm.i32Add(); Type.I64 -> asm.i64Add(); else -> error("Unsupported add type") }
                asm.localSet(locals[inst.dest.name]!!)
            }
            is Instruction.Sub -> {
                pushValue(inst.lhs, locals, asm)
                pushValue(inst.rhs, locals, asm)
                when (inst.lhs.type) { Type.I32 -> asm.i32Sub(); Type.I64 -> asm.i64Sub(); else -> error("Unsupported sub type") }
                asm.localSet(locals[inst.dest.name]!!)
            }
            is Instruction.Mul -> {
                pushValue(inst.lhs, locals, asm)
                pushValue(inst.rhs, locals, asm)
                when (inst.lhs.type) { Type.I32 -> asm.i32Mul(); Type.I64 -> asm.i64Mul(); else -> error("Unsupported mul type") }
                asm.localSet(locals[inst.dest.name]!!)
            }
            is Instruction.And -> {
                pushValue(inst.lhs, locals, asm)
                pushValue(inst.rhs, locals, asm)
                when (inst.lhs.type) { Type.I32 -> asm.i32And(); Type.I64 -> asm.i64And(); else -> error("Unsupported and type") }
                asm.localSet(locals[inst.dest.name]!!)
            }
            is Instruction.Or -> {
                pushValue(inst.lhs, locals, asm)
                pushValue(inst.rhs, locals, asm)
                when (inst.lhs.type) { Type.I32 -> asm.i32Or(); Type.I64 -> asm.i64Or(); else -> error("Unsupported or type") }
                asm.localSet(locals[inst.dest.name]!!)
            }
            is Instruction.Xor -> {
                pushValue(inst.lhs, locals, asm)
                pushValue(inst.rhs, locals, asm)
                when (inst.lhs.type) { Type.I32 -> asm.i32Xor(); Type.I64 -> asm.i64Xor(); else -> error("Unsupported xor type") }
                asm.localSet(locals[inst.dest.name]!!)
            }
            is Instruction.Shl -> {
                pushValue(inst.lhs, locals, asm)
                pushValue(inst.rhs, locals, asm)
                when (inst.lhs.type) { Type.I32 -> asm.i32Shl(); Type.I64 -> asm.i64Shl(); else -> error("Unsupported shl type") }
                asm.localSet(locals[inst.dest.name]!!)
            }

            is Instruction.FAdd -> {
                pushValue(inst.lhs, locals, asm)
                pushValue(inst.rhs, locals, asm)
                when (inst.lhs.type) { Type.F32 -> asm.f32Add(); Type.F64 -> asm.f64Add(); else -> error("Unsupported fadd type") }
                asm.localSet(locals[inst.dest.name]!!)
            }
            is Instruction.FSub -> {
                pushValue(inst.lhs, locals, asm)
                pushValue(inst.rhs, locals, asm)
                when (inst.lhs.type) { Type.F32 -> asm.f32Sub(); Type.F64 -> asm.f64Sub(); else -> error("Unsupported fsub type") }
                asm.localSet(locals[inst.dest.name]!!)
            }
            is Instruction.FMul -> {
                pushValue(inst.lhs, locals, asm)
                pushValue(inst.rhs, locals, asm)
                when (inst.lhs.type) { Type.F32 -> asm.f32Mul(); Type.F64 -> asm.f64Mul(); else -> error("Unsupported fmul type") }
                asm.localSet(locals[inst.dest.name]!!)
            }
            is Instruction.FDiv -> {
                pushValue(inst.lhs, locals, asm)
                pushValue(inst.rhs, locals, asm)
                when (inst.lhs.type) { Type.F32 -> asm.f32Div(); Type.F64 -> asm.f64Div(); else -> error("Unsupported fdiv type") }
                asm.localSet(locals[inst.dest.name]!!)
            }

            is Instruction.ICmp -> {
                pushValue(inst.lhs, locals, asm)
                pushValue(inst.rhs, locals, asm)
                val type = inst.lhs.type
                when (inst.predicate) {
                    ICmpPredicate.EQ -> when (type) { Type.I32 -> asm.i32Eq(); Type.I64 -> asm.i64Eq(); else -> error("Unsupported") }
                    ICmpPredicate.NE -> when (type) { Type.I32 -> asm.i32Ne(); Type.I64 -> asm.i64Ne(); else -> error("Unsupported") }
                    ICmpPredicate.SLT -> when (type) { Type.I32 -> asm.i32LtS(); Type.I64 -> asm.i64LtS(); else -> error("Unsupported") }
                    ICmpPredicate.SLE -> when (type) { Type.I32 -> asm.i32LeS(); Type.I64 -> asm.i64LeS(); else -> error("Unsupported") }
                    ICmpPredicate.SGT -> when (type) { Type.I32 -> asm.i32GtS(); Type.I64 -> asm.i64GtS(); else -> error("Unsupported") }
                    ICmpPredicate.SGE -> when (type) { Type.I32 -> asm.i32GeS(); Type.I64 -> asm.i64GeS(); else -> error("Unsupported") }
                    ICmpPredicate.ULT -> when (type) { Type.I32 -> asm.i32LtU(); Type.I64 -> asm.i64LtU(); else -> error("Unsupported") }
                    ICmpPredicate.ULE -> when (type) { Type.I32 -> asm.i32LeU(); Type.I64 -> asm.i64LeU(); else -> error("Unsupported") }
                    ICmpPredicate.UGT -> when (type) { Type.I32 -> asm.i32GtU(); Type.I64 -> asm.i64GtU(); else -> error("Unsupported") }
                    ICmpPredicate.UGE -> when (type) { Type.I32 -> asm.i32GeU(); Type.I64 -> asm.i64GeU(); else -> error("Unsupported") }
                }
                asm.localSet(locals[inst.dest.name]!!)
            }

            is Instruction.Ret -> {
                val retVal = inst.value
                if (retVal != null) pushValue(retVal, locals, asm)
                asm.return_()
            }

            is Instruction.Call -> {
                for (arg in inst.args) pushValue(arg, locals, asm)
                val funcName = when (val f = inst.function) {
                    is FunctionRef -> f.name
                    is GlobalRef -> f.name
                    else -> error("Unsupported call target: $f")
                }
                asm.call(funcName)
                val dest = inst.dest
                if (dest != null) asm.localSet(locals[dest.name]!!)
            }

            is Instruction.Select -> {
                pushValue(inst.trueValue, locals, asm)
                pushValue(inst.falseValue, locals, asm)
                pushValue(inst.condition, locals, asm)
                asm.select()
                asm.localSet(locals[inst.dest.name]!!)
            }

            else -> error("Unsupported IR instruction for WASM: ${inst::class.simpleName}")
        }
    }

    private fun pushValue(value: Value, locals: Map<String, Int>, asm: WasmAssembler) {
        when (value) {
            is Parameter -> asm.localGet(locals[value.name]!!)
            is InstructionRef -> asm.localGet(locals[value.name]!!)
            is Constant.I32 -> asm.i32Const(value.value)
            is Constant.I64 -> asm.i64Const(value.value)
            is Constant.F32 -> asm.f32Const(value.value)
            is Constant.F64 -> asm.f64Const(value.value)
            is Constant.I1 -> asm.i32Const(if (value.value) 1 else 0)
            else -> error("Unsupported value type for WASM: ${value::class.simpleName}")
        }
    }

    private fun irTypeToWasm(type: Type): WasmValueType = when (type) {
        Type.I1, Type.I8, Type.I16, Type.I32 -> WasmValueType.I32
        Type.I64 -> WasmValueType.I64
        Type.F32 -> WasmValueType.F32
        Type.F64 -> WasmValueType.F64
        else -> error("Unsupported IR type for WASM: $type")
    }
}
