package org.kgen.pass

import org.kgen.ir.*

/**
 * Simplifies instruction patterns using algebraic identities.
 *
 * Examples:
 * - `x + 0` → `x`
 * - `x * 1` → `x`
 * - `x * 0` → `0`
 * - `x - 0` → `x`
 * - `x - x` → `0`
 * - `x & 0` → `0`
 * - `x & -1` → `x`
 * - `x | 0` → `x`
 * - `x ^ 0` → `x`
 * - `x ^ x` → `0`
 * - `x << 0` → `x`
 * - `x >> 0` → `x`
 * - `neg(neg(x))` → `x`
 * - `select(true, a, b)` → `a`
 * - `select(false, a, b)` → `b`
 *
 * This pass replaces simplified instructions with identity mappings,
 * then relies on [DeadCodeElimination] to clean up.
 */
class InstructionCombining : ModulePass {

    override fun run(module: Module): Module {
        return module.copy(functions = module.functions.map { fn ->
            if (fn.isExternal) fn else combineFunction(fn)
        })
    }

    private fun combineFunction(fn: IrFunction): IrFunction {
        val replacements = mutableMapOf<String, Value>()
        val newBlocks = fn.blocks.map { block ->
            val newInstructions = mutableListOf<Instruction>()
            for (inst in block.instructions) {
                val rewritten = rewriteOperands(inst, replacements)
                val simplified = trySimplify(rewritten)
                if (simplified != null) {
                    val destName = rewritten.result?.name ?: continue
                    replacements[destName] = simplified
                } else {
                    newInstructions.add(rewritten)
                }
            }
            BasicBlock(block.label, newInstructions)
        }
        return fn.copy(blocks = newBlocks)
    }

    private fun trySimplify(inst: Instruction): Value? = when (inst) {
        is Instruction.Add -> simplifyAdd(inst.lhs, inst.rhs)
        is Instruction.Sub -> simplifySub(inst.lhs, inst.rhs)
        is Instruction.Mul -> simplifyMul(inst.lhs, inst.rhs)
        is Instruction.And -> simplifyAnd(inst.lhs, inst.rhs)
        is Instruction.Or -> simplifyOr(inst.lhs, inst.rhs)
        is Instruction.Xor -> simplifyXor(inst.lhs, inst.rhs)
        is Instruction.Shl -> simplifyShift(inst.lhs, inst.rhs)
        is Instruction.LShr -> simplifyShift(inst.lhs, inst.rhs)
        is Instruction.AShr -> simplifyShift(inst.lhs, inst.rhs)
        is Instruction.Select -> simplifySelect(inst)
        is Instruction.ZExt -> simplifyZExt(inst)
        is Instruction.SExt -> simplifySExt(inst)
        else -> null
    }

    private fun isZero(v: Value): Boolean = when (v) {
        is Constant.I32 -> v.value == 0
        is Constant.I64 -> v.value == 0L
        is Constant.F64 -> v.value == 0.0
        is Constant.F32 -> v.value == 0.0f
        else -> false
    }

    private fun isOne(v: Value): Boolean = when (v) {
        is Constant.I32 -> v.value == 1
        is Constant.I64 -> v.value == 1L
        else -> false
    }

    private fun isAllOnes(v: Value): Boolean = when (v) {
        is Constant.I32 -> v.value == -1
        is Constant.I64 -> v.value == -1L
        else -> false
    }

    private fun zeroFor(type: Type): Constant? = when (type) {
        Type.I32 -> Constant.I32(0)
        Type.I64 -> Constant.I64(0L)
        Type.I16 -> Constant.I16(0)
        Type.I8 -> Constant.I8(0)
        else -> null
    }

    private fun sameValue(a: Value, b: Value): Boolean =
        a is InstructionRef && b is InstructionRef && a.name == b.name ||
        a is Parameter && b is Parameter && a.name == b.name

    private fun simplifyAdd(lhs: Value, rhs: Value): Value? {
        if (isZero(rhs)) return lhs  // x + 0 → x
        if (isZero(lhs)) return rhs  // 0 + x → x
        return null
    }

    private fun simplifySub(lhs: Value, rhs: Value): Value? {
        if (isZero(rhs)) return lhs  // x - 0 → x
        if (sameValue(lhs, rhs)) return zeroFor(lhs.type)  // x - x → 0
        return null
    }

    private fun simplifyMul(lhs: Value, rhs: Value): Value? {
        if (isOne(rhs)) return lhs   // x * 1 → x
        if (isOne(lhs)) return rhs   // 1 * x → x
        if (isZero(rhs)) return rhs  // x * 0 → 0
        if (isZero(lhs)) return lhs  // 0 * x → 0
        return null
    }

    private fun simplifyAnd(lhs: Value, rhs: Value): Value? {
        if (isZero(rhs)) return rhs         // x & 0 → 0
        if (isZero(lhs)) return lhs         // 0 & x → 0
        if (isAllOnes(rhs)) return lhs      // x & -1 → x
        if (isAllOnes(lhs)) return rhs      // -1 & x → x
        if (sameValue(lhs, rhs)) return lhs // x & x → x
        return null
    }

    private fun simplifyOr(lhs: Value, rhs: Value): Value? {
        if (isZero(rhs)) return lhs         // x | 0 → x
        if (isZero(lhs)) return rhs         // 0 | x → x
        if (sameValue(lhs, rhs)) return lhs // x | x → x
        return null
    }

    private fun simplifyXor(lhs: Value, rhs: Value): Value? {
        if (isZero(rhs)) return lhs  // x ^ 0 → x
        if (isZero(lhs)) return rhs  // 0 ^ x → x
        if (sameValue(lhs, rhs)) return zeroFor(lhs.type)  // x ^ x → 0
        return null
    }

    private fun simplifyShift(lhs: Value, rhs: Value): Value? {
        if (isZero(rhs)) return lhs  // x << 0 → x, x >> 0 → x
        return null
    }

    private fun simplifySelect(inst: Instruction.Select): Value? {
        val cond = inst.condition
        if (cond is Constant.I1) {
            return if (cond.value) inst.trueValue else inst.falseValue
        }
        if (sameValue(inst.trueValue, inst.falseValue)) return inst.trueValue
        return null
    }

    private fun simplifyZExt(inst: Instruction.ZExt): Value? {
        // zext of same-width type is identity
        if (inst.value.type == inst.dest.type) return inst.value
        return null
    }

    private fun simplifySExt(inst: Instruction.SExt): Value? {
        if (inst.value.type == inst.dest.type) return inst.value
        return null
    }

    private fun rewriteOperands(inst: Instruction, replacements: Map<String, Value>): Instruction {
        fun rw(v: Value): Value = if (v is InstructionRef || v is Parameter) replacements[v.name] ?: v else v

        return when (inst) {
            is Instruction.Add -> inst.copy(lhs = rw(inst.lhs), rhs = rw(inst.rhs))
            is Instruction.Sub -> inst.copy(lhs = rw(inst.lhs), rhs = rw(inst.rhs))
            is Instruction.Mul -> inst.copy(lhs = rw(inst.lhs), rhs = rw(inst.rhs))
            is Instruction.SDiv -> inst.copy(lhs = rw(inst.lhs), rhs = rw(inst.rhs))
            is Instruction.UDiv -> inst.copy(lhs = rw(inst.lhs), rhs = rw(inst.rhs))
            is Instruction.SRem -> inst.copy(lhs = rw(inst.lhs), rhs = rw(inst.rhs))
            is Instruction.URem -> inst.copy(lhs = rw(inst.lhs), rhs = rw(inst.rhs))
            is Instruction.And -> inst.copy(lhs = rw(inst.lhs), rhs = rw(inst.rhs))
            is Instruction.Or -> inst.copy(lhs = rw(inst.lhs), rhs = rw(inst.rhs))
            is Instruction.Xor -> inst.copy(lhs = rw(inst.lhs), rhs = rw(inst.rhs))
            is Instruction.Shl -> inst.copy(lhs = rw(inst.lhs), rhs = rw(inst.rhs))
            is Instruction.LShr -> inst.copy(lhs = rw(inst.lhs), rhs = rw(inst.rhs))
            is Instruction.AShr -> inst.copy(lhs = rw(inst.lhs), rhs = rw(inst.rhs))
            is Instruction.Neg -> inst.copy(operand = rw(inst.operand))
            is Instruction.ICmp -> inst.copy(lhs = rw(inst.lhs), rhs = rw(inst.rhs))
            is Instruction.FAdd -> inst.copy(lhs = rw(inst.lhs), rhs = rw(inst.rhs))
            is Instruction.FSub -> inst.copy(lhs = rw(inst.lhs), rhs = rw(inst.rhs))
            is Instruction.FMul -> inst.copy(lhs = rw(inst.lhs), rhs = rw(inst.rhs))
            is Instruction.FDiv -> inst.copy(lhs = rw(inst.lhs), rhs = rw(inst.rhs))
            is Instruction.FNeg -> inst.copy(operand = rw(inst.operand))
            is Instruction.FCmp -> inst.copy(lhs = rw(inst.lhs), rhs = rw(inst.rhs))
            is Instruction.ZExt -> inst.copy(value = rw(inst.value))
            is Instruction.SExt -> inst.copy(value = rw(inst.value))
            is Instruction.IntTrunc -> inst.copy(value = rw(inst.value))
            is Instruction.Trunc -> inst.copy(operand = rw(inst.operand))
            is Instruction.Ret -> inst.copy(value = inst.value?.let { rw(it) })
            is Instruction.Call -> inst.copy(args = inst.args.map { rw(it) })
            is Instruction.Select -> inst.copy(condition = rw(inst.condition), trueValue = rw(inst.trueValue), falseValue = rw(inst.falseValue))
            is Instruction.Store -> inst.copy(value = rw(inst.value), ptr = rw(inst.ptr))
            is Instruction.Load -> inst.copy(ptr = rw(inst.ptr))
            is Instruction.CondBr -> inst.copy(condition = rw(inst.condition))
            is Instruction.SIToFP -> inst.copy(value = rw(inst.value))
            is Instruction.UIToFP -> inst.copy(value = rw(inst.value))
            is Instruction.FPToSI -> inst.copy(value = rw(inst.value))
            is Instruction.FPToUI -> inst.copy(value = rw(inst.value))
            is Instruction.FPExt -> inst.copy(value = rw(inst.value))
            is Instruction.FPTrunc -> inst.copy(value = rw(inst.value))
            is Instruction.GetElementPtr -> inst.copy(ptr = rw(inst.ptr), indices = inst.indices.map { rw(it) })
            is Instruction.Phi -> inst.copy(incoming = inst.incoming.map { (v, l) -> rw(v) to l })
            else -> inst
        }
    }
}
