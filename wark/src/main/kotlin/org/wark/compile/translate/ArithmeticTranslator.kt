package org.wark.compile.translate

import org.kgen.ir.ICmpPredicate
import org.kgen.ir.Constant
import org.kgen.ir.Type
import org.kgen.ir.Value
import org.kgen.ir.build.ModuleBuilder
import org.kgen.target.wasm.disasm.WasmInstruction
import org.wark.compile.CompilationContext
import org.wark.compile.ValueStack

class ArithmeticTranslator : InstructionTranslator {

    private val handled = setOf(
        "i32.add", "i32.sub", "i32.mul", "i32.div_s", "i32.div_u",
        "i32.rem_s", "i32.rem_u", "i32.and", "i32.or", "i32.xor",
        "i32.shl", "i32.shr_s", "i32.shr_u",
        "i32.rotl", "i32.clz", "i32.ctz", "i32.popcnt",
        "i32.extend8_s", "i32.extend16_s",
        "i64.add", "i64.sub", "i64.mul", "i64.div_s", "i64.div_u",
        "i64.rem_s", "i64.rem_u", "i64.and", "i64.or", "i64.xor",
        "i64.shl", "i64.shr_s", "i64.shr_u",
        "f32.add", "f32.sub", "f32.mul", "f32.div",
        "f64.add", "f64.sub", "f64.mul", "f64.div",
        "f32.neg", "f64.neg", "f32.abs", "f64.abs",
        "i32.eqz", "i32.eq", "i32.ne", "i32.lt_s", "i32.lt_u",
        "i32.gt_s", "i32.gt_u", "i32.le_s", "i32.le_u", "i32.ge_s", "i32.ge_u",
        "f64.eq", "f64.ne", "f64.lt", "f64.ge", "f32.lt",
        "i64.eqz", "i64.eq", "i64.ne", "i64.lt_s", "i64.lt_u",
        "i64.gt_s", "i64.gt_u", "i64.le_s", "i64.le_u", "i64.ge_s", "i64.ge_u",
    )

    override fun canHandle(mnemonic: String): Boolean = mnemonic in handled

    override fun translate(context: CompilationContext, instruction: WasmInstruction) {
        val builder = context.builder
        val stack = context.stack

        when (instruction.opcode.mnemonic) {
            "i32.add", "i64.add" -> binaryOp(stack) { left, right -> builder.add(left, right) }
            "i32.sub", "i64.sub" -> binaryOp(stack) { left, right -> builder.sub(left, right) }
            "i32.mul", "i64.mul" -> binaryOp(stack) { left, right -> builder.mul(left, right) }
            "i32.div_s", "i64.div_s" -> binaryOp(stack) { left, right -> builder.sdiv(left, right) }
            "i32.div_u", "i64.div_u" -> binaryOp(stack) { left, right -> builder.udiv(left, right) }
            "i32.rem_s", "i64.rem_s" -> binaryOp(stack) { left, right -> builder.srem(left, right) }
            "i32.rem_u", "i64.rem_u" -> binaryOp(stack) { left, right -> builder.urem(left, right) }
            "i32.and", "i64.and" -> binaryOp(stack) { left, right -> builder.and(left, right) }
            "i32.or", "i64.or" -> binaryOp(stack) { left, right -> builder.or(left, right) }
            "i32.xor", "i64.xor" -> binaryOp(stack) { left, right -> builder.xor(left, right) }
            "i32.shl", "i64.shl" -> binaryOp(stack) { left, right -> builder.shl(left, right) }
            "i32.shr_s", "i64.shr_s" -> binaryOp(stack) { left, right -> builder.ashr(left, right) }
            "i32.shr_u", "i64.shr_u" -> binaryOp(stack) { left, right -> builder.lshr(left, right) }

            "i32.rotl" -> {
                val amount = stack.pop()
                val value = stack.pop()
                val leftShift = builder.shl(value, amount)
                val rightAmount = builder.sub(Constant.I32(32), amount)
                val rightShift = builder.lshr(value, rightAmount)
                stack.push(builder.or(leftShift, rightShift))
            }

            "i32.clz" -> {
                val value = stack.pop()
                stack.push(builder.call("__wark_i32_clz", listOf(value), Type.I32) ?: Constant.I32(0))
            }
            "i32.ctz" -> {
                val value = stack.pop()
                stack.push(builder.call("__wark_i32_ctz", listOf(value), Type.I32) ?: Constant.I32(0))
            }
            "i32.popcnt" -> {
                val value = stack.pop()
                stack.push(builder.call("__wark_i32_popcnt", listOf(value), Type.I32) ?: Constant.I32(0))
            }

            "i32.extend8_s" -> {
                val value = stack.pop()
                val truncated = builder.trunc(value, Type.I8)
                stack.push(builder.sext(truncated, Type.I32))
            }
            "i32.extend16_s" -> {
                val value = stack.pop()
                val truncated = builder.trunc(value, Type.I16)
                stack.push(builder.sext(truncated, Type.I32))
            }

            "f32.add", "f64.add" -> binaryOp(stack) { left, right -> builder.fadd(left, right) }
            "f32.sub", "f64.sub" -> binaryOp(stack) { left, right -> builder.fsub(left, right) }
            "f32.mul", "f64.mul" -> binaryOp(stack) { left, right -> builder.fmul(left, right) }
            "f32.div", "f64.div" -> binaryOp(stack) { left, right -> builder.fdiv(left, right) }
            "f32.neg", "f64.neg" -> { stack.push(builder.fneg(stack.pop())) }
            "f32.abs", "f64.abs" -> {
                val value = stack.pop()
                stack.push(builder.call("__wark_fabs", listOf(value), value.type) ?: value)
            }

            "f64.eq" -> { val b = stack.pop(); val a = stack.pop(); stack.push(builder.zext(builder.fcmp(org.kgen.ir.FCmpPredicate.OEQ, a, b), Type.I32)) }
            "f64.ne" -> { val b = stack.pop(); val a = stack.pop(); stack.push(builder.zext(builder.fcmp(org.kgen.ir.FCmpPredicate.ONE, a, b), Type.I32)) }
            "f64.lt", "f32.lt" -> { val b = stack.pop(); val a = stack.pop(); stack.push(builder.zext(builder.fcmp(org.kgen.ir.FCmpPredicate.OLT, a, b), Type.I32)) }
            "f64.ge" -> { val b = stack.pop(); val a = stack.pop(); stack.push(builder.zext(builder.fcmp(org.kgen.ir.FCmpPredicate.OGE, a, b), Type.I32)) }

            "i32.eqz" -> {
                val cmp = builder.icmp(ICmpPredicate.EQ, stack.pop(), Constant.I32(0))
                stack.push(builder.zext(cmp, Type.I32))
            }
            "i64.eqz" -> {
                val cmp = builder.icmp(ICmpPredicate.EQ, stack.pop(), Constant.I64(0))
                stack.push(builder.zext(cmp, Type.I32))
            }

            "i32.eq", "i64.eq" -> cmpOp(stack, ICmpPredicate.EQ, builder)
            "i32.ne", "i64.ne" -> cmpOp(stack, ICmpPredicate.NE, builder)
            "i32.lt_s", "i64.lt_s" -> cmpOp(stack, ICmpPredicate.SLT, builder)
            "i32.lt_u", "i64.lt_u" -> cmpOp(stack, ICmpPredicate.ULT, builder)
            "i32.gt_s", "i64.gt_s" -> cmpOp(stack, ICmpPredicate.SGT, builder)
            "i32.gt_u", "i64.gt_u" -> cmpOp(stack, ICmpPredicate.UGT, builder)
            "i32.le_s", "i64.le_s" -> cmpOp(stack, ICmpPredicate.SLE, builder)
            "i32.le_u", "i64.le_u" -> cmpOp(stack, ICmpPredicate.ULE, builder)
            "i32.ge_s", "i64.ge_s" -> cmpOp(stack, ICmpPredicate.SGE, builder)
            "i32.ge_u", "i64.ge_u" -> cmpOp(stack, ICmpPredicate.UGE, builder)
        }
    }

    private fun binaryOp(stack: ValueStack, operation: (Value, Value) -> Value) {
        val right = stack.pop()
        val left = stack.pop()
        stack.push(operation(left, right))
    }

    private fun cmpOp(stack: ValueStack, predicate: ICmpPredicate, builder: ModuleBuilder) {
        val right = stack.pop()
        val left = stack.pop()
        val cmp = builder.icmp(predicate, left, right)
        stack.push(builder.zext(cmp, Type.I32))
    }
}
