package org.kgen.pipeline

import org.kgen.ir.*
import org.kgen.ir.instructions.*

/**
 * Evaluates instructions with constant operands at compile time.
 *
 * When both operands of an arithmetic/comparison instruction are constants,
 * the instruction is replaced with the computed constant result. Subsequent
 * instructions that used the result are updated to use the constant directly.
 *
 * This is a function-local pass — it does not cross function boundaries.
 */
class ConstantFolding : PipelineStage {

    override fun run(module: Module): Module {
        return module.copy(functions = module.functions.map { fn ->
            if (fn.isExternal) fn else foldFunction(fn)
        })
    }

    private fun foldFunction(fn: IrFunction): IrFunction {
        val replacements = mutableMapOf<String, Value>()
        val newBlocks = fn.blocks.map { block ->
            val newInstructions = mutableListOf<Instruction>()
            for (inst in block.instructions) {
                val rewritten = rewriteOperands(inst, replacements)
                val folded = tryFold(rewritten)
                if (folded != null) {
                    val destName = rewritten.result?.name ?: continue
                    replacements[destName] = folded
                } else {
                    newInstructions.add(rewritten)
                }
            }
            BasicBlock(block.label, newInstructions)
        }
        return fn.copy(blocks = newBlocks)
    }

    private fun tryFold(inst: Instruction): Constant? = when (inst) {
        is Add -> foldBinOpI32(inst.lhs, inst.rhs) { a, b -> a + b }
            ?: foldBinOpI64(inst.lhs, inst.rhs) { a, b -> a + b }
        is Sub -> foldBinOpI32(inst.lhs, inst.rhs) { a, b -> a - b }
            ?: foldBinOpI64(inst.lhs, inst.rhs) { a, b -> a - b }
        is Mul -> foldBinOpI32(inst.lhs, inst.rhs) { a, b -> a * b }
            ?: foldBinOpI64(inst.lhs, inst.rhs) { a, b -> a * b }
        is SDiv -> foldDivI32(inst.lhs, inst.rhs, signed = true, rem = false)
            ?: foldDivI64(inst.lhs, inst.rhs, signed = true, rem = false)
        is UDiv -> foldDivI32(inst.lhs, inst.rhs, signed = false, rem = false)
        is SRem -> foldDivI32(inst.lhs, inst.rhs, signed = true, rem = true)
            ?: foldDivI64(inst.lhs, inst.rhs, signed = true, rem = true)
        is URem -> foldDivI32(inst.lhs, inst.rhs, signed = false, rem = true)
        is And -> foldBinOpI32(inst.lhs, inst.rhs) { a, b -> a and b }
            ?: foldBinOpI64(inst.lhs, inst.rhs) { a, b -> a and b }
        is Or -> foldBinOpI32(inst.lhs, inst.rhs) { a, b -> a or b }
            ?: foldBinOpI64(inst.lhs, inst.rhs) { a, b -> a or b }
        is Xor -> foldBinOpI32(inst.lhs, inst.rhs) { a, b -> a xor b }
            ?: foldBinOpI64(inst.lhs, inst.rhs) { a, b -> a xor b }
        is Shl -> foldBinOpI32(inst.lhs, inst.rhs) { a, b -> a shl b }
            ?: foldBinOpI64(inst.lhs, inst.rhs) { a, b -> a shl b.toInt() }
        is LShr -> foldBinOpI32(inst.lhs, inst.rhs) { a, b -> a ushr b }
            ?: foldBinOpI64(inst.lhs, inst.rhs) { a, b -> a ushr b.toInt() }
        is AShr -> foldBinOpI32(inst.lhs, inst.rhs) { a, b -> a shr b }
            ?: foldBinOpI64(inst.lhs, inst.rhs) { a, b -> a shr b.toInt() }
        is Neg -> when (val op = inst.operand) {
            is Constant.I32 -> Constant.I32(-op.value)
            is Constant.I64 -> Constant.I64(-op.value)
            else -> null
        }
        is ICmp -> foldICmp(inst)
        is FAdd -> foldBinOpF64(inst.lhs, inst.rhs) { a, b -> a + b }
        is FSub -> foldBinOpF64(inst.lhs, inst.rhs) { a, b -> a - b }
        is FMul -> foldBinOpF64(inst.lhs, inst.rhs) { a, b -> a * b }
        is FDiv -> foldBinOpF64(inst.lhs, inst.rhs) { a, b -> a / b }
        is FNeg -> when (val op = inst.operand) {
            is Constant.F64 -> Constant.F64(-op.value)
            is Constant.F32 -> Constant.F32(-op.value)
            else -> null
        }
        is ZExt -> foldZExt(inst)
        is SExt -> foldSExt(inst)
        is IntTrunc -> foldIntTrunc(inst)
        else -> null
    }

    private fun foldDivI32(lhs: Value, rhs: Value, signed: Boolean, rem: Boolean): Constant? {
        if (lhs !is Constant.I32 || rhs !is Constant.I32 || rhs.value == 0) return null
        return if (signed) {
            Constant.I32(if (rem) lhs.value % rhs.value else lhs.value / rhs.value)
        } else {
            val r = if (rem) (lhs.value.toUInt() % rhs.value.toUInt()) else (lhs.value.toUInt() / rhs.value.toUInt())
            Constant.I32(r.toInt())
        }
    }

    private fun foldDivI64(lhs: Value, rhs: Value, signed: Boolean, rem: Boolean): Constant? {
        if (lhs !is Constant.I64 || rhs !is Constant.I64 || rhs.value == 0L) return null
        return if (signed) {
            Constant.I64(if (rem) lhs.value % rhs.value else lhs.value / rhs.value)
        } else {
            val r = if (rem) (lhs.value.toULong() % rhs.value.toULong()) else (lhs.value.toULong() / rhs.value.toULong())
            Constant.I64(r.toLong())
        }
    }

    private fun foldBinOpI32(lhs: Value, rhs: Value, op: (Int, Int) -> Int): Constant? {
        if (lhs is Constant.I32 && rhs is Constant.I32) return Constant.I32(op(lhs.value, rhs.value))
        return null
    }

    private fun foldBinOpI64(lhs: Value, rhs: Value, op: (Long, Long) -> Long): Constant? {
        if (lhs is Constant.I64 && rhs is Constant.I64) return Constant.I64(op(lhs.value, rhs.value))
        return null
    }

    private fun foldBinOpF64(lhs: Value, rhs: Value, op: (Double, Double) -> Double): Constant? {
        if (lhs is Constant.F64 && rhs is Constant.F64) return Constant.F64(op(lhs.value, rhs.value))
        if (lhs is Constant.F32 && rhs is Constant.F32) return Constant.F32(op(lhs.value.toDouble(), rhs.value.toDouble()).toFloat())
        return null
    }

    private fun foldICmp(inst: ICmp): Constant? {
        val l = inst.lhs
        val r = inst.rhs
        val result = when {
            l is Constant.I32 && r is Constant.I32 -> evalICmp(inst.predicate, l.value.toLong(), r.value.toLong(), 32)
            l is Constant.I64 && r is Constant.I64 -> evalICmp(inst.predicate, l.value, r.value, 64)
            else -> return null
        }
        return Constant.I1(result)
    }

    private fun evalICmp(pred: ICmpPredicate, l: Long, r: Long, bits: Int): Boolean = when (pred) {
        ICmpPredicate.EQ -> l == r
        ICmpPredicate.NE -> l != r
        ICmpPredicate.SLT -> l < r
        ICmpPredicate.SLE -> l <= r
        ICmpPredicate.SGT -> l > r
        ICmpPredicate.SGE -> l >= r
        ICmpPredicate.ULT -> if (bits == 32) l.toUInt() < r.toUInt() else l.toULong() < r.toULong()
        ICmpPredicate.ULE -> if (bits == 32) l.toUInt() <= r.toUInt() else l.toULong() <= r.toULong()
        ICmpPredicate.UGT -> if (bits == 32) l.toUInt() > r.toUInt() else l.toULong() > r.toULong()
        ICmpPredicate.UGE -> if (bits == 32) l.toUInt() >= r.toUInt() else l.toULong() >= r.toULong()
    }

    private fun foldZExt(inst: ZExt): Constant? = when (val v = inst.value) {
        is Constant.I1 -> when (inst.dest.type) {
            Type.I32 -> Constant.I32(if (v.value) 1 else 0)
            Type.I64 -> Constant.I64(if (v.value) 1L else 0L)
            else -> null
        }
        is Constant.I8 -> when (inst.dest.type) {
            Type.I32 -> Constant.I32(v.value.toInt() and 0xFF)
            Type.I64 -> Constant.I64(v.value.toLong() and 0xFF)
            else -> null
        }
        is Constant.I16 -> when (inst.dest.type) {
            Type.I32 -> Constant.I32(v.value.toInt() and 0xFFFF)
            Type.I64 -> Constant.I64(v.value.toLong() and 0xFFFF)
            else -> null
        }
        is Constant.I32 -> when (inst.dest.type) {
            Type.I64 -> Constant.I64(v.value.toLong() and 0xFFFFFFFFL)
            else -> null
        }
        else -> null
    }

    private fun foldSExt(inst: SExt): Constant? = when (val v = inst.value) {
        is Constant.I1 -> when (inst.dest.type) {
            Type.I32 -> Constant.I32(if (v.value) -1 else 0)
            Type.I64 -> Constant.I64(if (v.value) -1L else 0L)
            else -> null
        }
        is Constant.I8 -> when (inst.dest.type) {
            Type.I32 -> Constant.I32(v.value.toInt())
            Type.I64 -> Constant.I64(v.value.toLong())
            else -> null
        }
        is Constant.I16 -> when (inst.dest.type) {
            Type.I32 -> Constant.I32(v.value.toInt())
            Type.I64 -> Constant.I64(v.value.toLong())
            else -> null
        }
        is Constant.I32 -> when (inst.dest.type) {
            Type.I64 -> Constant.I64(v.value.toLong())
            else -> null
        }
        else -> null
    }

    private fun foldIntTrunc(inst: IntTrunc): Constant? = when (val v = inst.value) {
        is Constant.I64 -> when (inst.toType) {
            Type.I32 -> Constant.I32(v.value.toInt())
            Type.I16 -> Constant.I16(v.value.toShort())
            Type.I8 -> Constant.I8(v.value.toByte())
            Type.I1 -> Constant.I1((v.value and 1L) != 0L)
            else -> null
        }
        is Constant.I32 -> when (inst.toType) {
            Type.I16 -> Constant.I16(v.value.toShort())
            Type.I8 -> Constant.I8(v.value.toByte())
            Type.I1 -> Constant.I1((v.value and 1) != 0)
            else -> null
        }
        else -> null
    }

    private fun rewriteOperands(inst: Instruction, replacements: Map<String, Value>): Instruction {
        fun rw(v: Value): Value = if (v is InstructionRef || v is Parameter) replacements[v.name] ?: v else v

        return when (inst) {
            is Add -> inst.copy(lhs = rw(inst.lhs), rhs = rw(inst.rhs))
            is Sub -> inst.copy(lhs = rw(inst.lhs), rhs = rw(inst.rhs))
            is Mul -> inst.copy(lhs = rw(inst.lhs), rhs = rw(inst.rhs))
            is SDiv -> inst.copy(lhs = rw(inst.lhs), rhs = rw(inst.rhs))
            is UDiv -> inst.copy(lhs = rw(inst.lhs), rhs = rw(inst.rhs))
            is SRem -> inst.copy(lhs = rw(inst.lhs), rhs = rw(inst.rhs))
            is URem -> inst.copy(lhs = rw(inst.lhs), rhs = rw(inst.rhs))
            is And -> inst.copy(lhs = rw(inst.lhs), rhs = rw(inst.rhs))
            is Or -> inst.copy(lhs = rw(inst.lhs), rhs = rw(inst.rhs))
            is Xor -> inst.copy(lhs = rw(inst.lhs), rhs = rw(inst.rhs))
            is Shl -> inst.copy(lhs = rw(inst.lhs), rhs = rw(inst.rhs))
            is LShr -> inst.copy(lhs = rw(inst.lhs), rhs = rw(inst.rhs))
            is AShr -> inst.copy(lhs = rw(inst.lhs), rhs = rw(inst.rhs))
            is Neg -> inst.copy(operand = rw(inst.operand))
            is ICmp -> inst.copy(lhs = rw(inst.lhs), rhs = rw(inst.rhs))
            is FAdd -> inst.copy(lhs = rw(inst.lhs), rhs = rw(inst.rhs))
            is FSub -> inst.copy(lhs = rw(inst.lhs), rhs = rw(inst.rhs))
            is FMul -> inst.copy(lhs = rw(inst.lhs), rhs = rw(inst.rhs))
            is FDiv -> inst.copy(lhs = rw(inst.lhs), rhs = rw(inst.rhs))
            is FNeg -> inst.copy(operand = rw(inst.operand))
            is FCmp -> inst.copy(lhs = rw(inst.lhs), rhs = rw(inst.rhs))
            is ZExt -> inst.copy(value = rw(inst.value))
            is SExt -> inst.copy(value = rw(inst.value))
            is IntTrunc -> inst.copy(value = rw(inst.value))
            is FTrunc -> inst.copy(operand = rw(inst.operand))
            is Ret -> inst.copy(value = inst.value?.let { rw(it) })
            is Call -> inst.copy(args = inst.args.map { rw(it) })
            is Select -> inst.copy(condition = rw(inst.condition), trueValue = rw(inst.trueValue), falseValue = rw(inst.falseValue))
            is Store -> inst.copy(value = rw(inst.value), ptr = rw(inst.ptr))
            is Load -> inst.copy(ptr = rw(inst.ptr))
            is CondBr -> inst.copy(condition = rw(inst.condition))
            is SIToFP -> inst.copy(value = rw(inst.value))
            is UIToFP -> inst.copy(value = rw(inst.value))
            is FPToSI -> inst.copy(value = rw(inst.value))
            is FPToUI -> inst.copy(value = rw(inst.value))
            is FPExt -> inst.copy(value = rw(inst.value))
            is FPTrunc -> inst.copy(value = rw(inst.value))
            is GetElementPtr -> inst.copy(ptr = rw(inst.ptr), indices = inst.indices.map { rw(it) })
            is Phi -> inst.copy(incoming = inst.incoming.map { (v, l) -> rw(v) to l })
            else -> inst
        }
    }
}
