package org.wark.compile.translate

import org.kgen.ir.Constant
import org.kgen.ir.FCmpPredicate
import org.kgen.ir.ICmpPredicate
import org.kgen.ir.Type
import org.kgen.ir.Value
import org.kgen.ir.build.ModuleBuilder
import org.kgen.target.wasm.WasmOpCode
import org.kgen.target.wasm.disasm.WasmInstruction
import org.wark.compile.CompilationContext
import org.wark.compile.ValueStack

class ArithmeticTranslator : InstructionTranslator {

    private val handled = setOf(
        WasmOpCode.I32_ADD, WasmOpCode.I32_SUB, WasmOpCode.I32_MUL,
        WasmOpCode.I32_DIV_S, WasmOpCode.I32_DIV_U, WasmOpCode.I32_REM_S, WasmOpCode.I32_REM_U,
        WasmOpCode.I32_AND, WasmOpCode.I32_OR, WasmOpCode.I32_XOR,
        WasmOpCode.I32_SHL, WasmOpCode.I32_SHR_S, WasmOpCode.I32_SHR_U,
        WasmOpCode.I32_ROTL, WasmOpCode.I32_ROTR,
        WasmOpCode.I32_CLZ, WasmOpCode.I32_CTZ, WasmOpCode.I32_POPCNT,
        WasmOpCode.I32_EXTEND8_S, WasmOpCode.I32_EXTEND16_S,
        WasmOpCode.I64_ADD, WasmOpCode.I64_SUB, WasmOpCode.I64_MUL,
        WasmOpCode.I64_DIV_S, WasmOpCode.I64_DIV_U, WasmOpCode.I64_REM_S, WasmOpCode.I64_REM_U,
        WasmOpCode.I64_AND, WasmOpCode.I64_OR, WasmOpCode.I64_XOR,
        WasmOpCode.I64_SHL, WasmOpCode.I64_SHR_S, WasmOpCode.I64_SHR_U,
        WasmOpCode.I64_ROTL, WasmOpCode.I64_ROTR,
        WasmOpCode.I64_CLZ, WasmOpCode.I64_CTZ, WasmOpCode.I64_POPCNT,
        WasmOpCode.I64_EXTEND8_S, WasmOpCode.I64_EXTEND16_S, WasmOpCode.I64_EXTEND32_S,
        WasmOpCode.F32_ADD, WasmOpCode.F32_SUB, WasmOpCode.F32_MUL, WasmOpCode.F32_DIV,
        WasmOpCode.F32_NEG, WasmOpCode.F32_ABS, WasmOpCode.F32_SQRT,
        WasmOpCode.F32_CEIL, WasmOpCode.F32_FLOOR, WasmOpCode.F32_TRUNC, WasmOpCode.F32_NEAREST,
        WasmOpCode.F32_MIN, WasmOpCode.F32_MAX, WasmOpCode.F32_COPYSIGN,
        WasmOpCode.F64_ADD, WasmOpCode.F64_SUB, WasmOpCode.F64_MUL, WasmOpCode.F64_DIV,
        WasmOpCode.F64_NEG, WasmOpCode.F64_ABS, WasmOpCode.F64_SQRT,
        WasmOpCode.F64_CEIL, WasmOpCode.F64_FLOOR, WasmOpCode.F64_TRUNC, WasmOpCode.F64_NEAREST,
        WasmOpCode.F64_MIN, WasmOpCode.F64_MAX,
        WasmOpCode.I32_EQZ, WasmOpCode.I32_EQ, WasmOpCode.I32_NE,
        WasmOpCode.I32_LT_S, WasmOpCode.I32_LT_U, WasmOpCode.I32_GT_S, WasmOpCode.I32_GT_U,
        WasmOpCode.I32_LE_S, WasmOpCode.I32_LE_U, WasmOpCode.I32_GE_S, WasmOpCode.I32_GE_U,
        WasmOpCode.I64_EQZ, WasmOpCode.I64_EQ, WasmOpCode.I64_NE,
        WasmOpCode.I64_LT_S, WasmOpCode.I64_LT_U, WasmOpCode.I64_GT_S, WasmOpCode.I64_GT_U,
        WasmOpCode.I64_LE_S, WasmOpCode.I64_LE_U, WasmOpCode.I64_GE_S, WasmOpCode.I64_GE_U,
        WasmOpCode.F32_EQ, WasmOpCode.F32_NE, WasmOpCode.F32_LT, WasmOpCode.F32_GT,
        WasmOpCode.F32_LE, WasmOpCode.F32_GE,
        WasmOpCode.F64_EQ, WasmOpCode.F64_NE, WasmOpCode.F64_LT, WasmOpCode.F64_GT,
        WasmOpCode.F64_LE, WasmOpCode.F64_GE,
    )

    override fun canHandle(opcode: WasmOpCode): Boolean = opcode in handled

    override fun translate(context: CompilationContext, instruction: WasmInstruction) {
        val builder = context.builder
        val stack = context.stack

        when (instruction.opcode) {
            WasmOpCode.I32_ADD, WasmOpCode.I64_ADD -> binaryOp(stack) { a, b -> builder.add(a, b) }
            WasmOpCode.I32_SUB, WasmOpCode.I64_SUB -> binaryOp(stack) { a, b -> builder.sub(a, b) }
            WasmOpCode.I32_MUL, WasmOpCode.I64_MUL -> binaryOp(stack) { a, b -> builder.mul(a, b) }
            WasmOpCode.I32_DIV_S, WasmOpCode.I64_DIV_S -> binaryOp(stack) { a, b -> builder.sdiv(a, b) }
            WasmOpCode.I32_DIV_U, WasmOpCode.I64_DIV_U -> binaryOp(stack) { a, b -> builder.udiv(a, b) }
            WasmOpCode.I32_REM_S, WasmOpCode.I64_REM_S -> binaryOp(stack) { a, b -> builder.srem(a, b) }
            WasmOpCode.I32_REM_U, WasmOpCode.I64_REM_U -> binaryOp(stack) { a, b -> builder.urem(a, b) }
            WasmOpCode.I32_AND, WasmOpCode.I64_AND -> binaryOp(stack) { a, b -> builder.and(a, b) }
            WasmOpCode.I32_OR, WasmOpCode.I64_OR -> binaryOp(stack) { a, b -> builder.or(a, b) }
            WasmOpCode.I32_XOR, WasmOpCode.I64_XOR -> binaryOp(stack) { a, b -> builder.xor(a, b) }
            WasmOpCode.I32_SHL, WasmOpCode.I64_SHL -> binaryOp(stack) { a, b -> builder.shl(a, b) }
            WasmOpCode.I32_SHR_S, WasmOpCode.I64_SHR_S -> binaryOp(stack) { a, b -> builder.ashr(a, b) }
            WasmOpCode.I32_SHR_U, WasmOpCode.I64_SHR_U -> binaryOp(stack) { a, b -> builder.lshr(a, b) }

            WasmOpCode.I32_ROTL -> rotateLeft(stack, builder, 32, Type.I32)
            WasmOpCode.I32_ROTR -> rotateRight(stack, builder, 32, Type.I32)
            WasmOpCode.I64_ROTL -> rotateLeft(stack, builder, 64, Type.I64)
            WasmOpCode.I64_ROTR -> rotateRight(stack, builder, 64, Type.I64)

            WasmOpCode.I32_CLZ -> unaryStub(stack, builder, "__wark_i32_clz", Type.I32)
            WasmOpCode.I32_CTZ -> unaryStub(stack, builder, "__wark_i32_ctz", Type.I32)
            WasmOpCode.I32_POPCNT -> unaryStub(stack, builder, "__wark_i32_popcnt", Type.I32)
            WasmOpCode.I64_CLZ -> unaryStub(stack, builder, "__wark_i64_clz", Type.I64)
            WasmOpCode.I64_CTZ -> unaryStub(stack, builder, "__wark_i64_ctz", Type.I64)
            WasmOpCode.I64_POPCNT -> unaryStub(stack, builder, "__wark_i64_popcnt", Type.I64)

            WasmOpCode.I32_EXTEND8_S -> { stack.push(builder.sext(builder.trunc(stack.pop(), Type.I8), Type.I32)) }
            WasmOpCode.I32_EXTEND16_S -> { stack.push(builder.sext(builder.trunc(stack.pop(), Type.I16), Type.I32)) }
            WasmOpCode.I64_EXTEND8_S -> { stack.push(builder.sext(builder.trunc(stack.pop(), Type.I8), Type.I64)) }
            WasmOpCode.I64_EXTEND16_S -> { stack.push(builder.sext(builder.trunc(stack.pop(), Type.I16), Type.I64)) }
            WasmOpCode.I64_EXTEND32_S -> { stack.push(builder.sext(builder.trunc(stack.pop(), Type.I32), Type.I64)) }

            WasmOpCode.F32_ADD, WasmOpCode.F64_ADD -> binaryOp(stack) { a, b -> builder.fadd(a, b) }
            WasmOpCode.F32_SUB, WasmOpCode.F64_SUB -> binaryOp(stack) { a, b -> builder.fsub(a, b) }
            WasmOpCode.F32_MUL, WasmOpCode.F64_MUL -> binaryOp(stack) { a, b -> builder.fmul(a, b) }
            WasmOpCode.F32_DIV, WasmOpCode.F64_DIV -> binaryOp(stack) { a, b -> builder.fdiv(a, b) }
            WasmOpCode.F32_NEG, WasmOpCode.F64_NEG -> { stack.push(builder.fneg(stack.pop())) }

            WasmOpCode.F32_ABS -> unaryStub(stack, builder, "__wark_f32_abs", Type.F32)
            WasmOpCode.F64_ABS -> unaryStub(stack, builder, "__wark_f64_abs", Type.F64)
            WasmOpCode.F32_SQRT -> unaryStub(stack, builder, "__wark_f32_sqrt", Type.F32)
            WasmOpCode.F64_SQRT -> unaryStub(stack, builder, "__wark_f64_sqrt", Type.F64)
            WasmOpCode.F32_CEIL -> unaryStub(stack, builder, "__wark_f32_ceil", Type.F32)
            WasmOpCode.F64_CEIL -> unaryStub(stack, builder, "__wark_f64_ceil", Type.F64)
            WasmOpCode.F32_FLOOR -> unaryStub(stack, builder, "__wark_f32_floor", Type.F32)
            WasmOpCode.F64_FLOOR -> unaryStub(stack, builder, "__wark_f64_floor", Type.F64)
            WasmOpCode.F32_TRUNC -> unaryStub(stack, builder, "__wark_f32_trunc", Type.F32)
            WasmOpCode.F64_TRUNC -> unaryStub(stack, builder, "__wark_f64_trunc", Type.F64)
            WasmOpCode.F32_NEAREST -> unaryStub(stack, builder, "__wark_f32_nearest", Type.F32)
            WasmOpCode.F64_NEAREST -> unaryStub(stack, builder, "__wark_f64_nearest", Type.F64)
            WasmOpCode.F32_MIN -> binaryStub(stack, builder, "__wark_f32_min", Type.F32)
            WasmOpCode.F64_MIN -> binaryStub(stack, builder, "__wark_f64_min", Type.F64)
            WasmOpCode.F32_MAX -> binaryStub(stack, builder, "__wark_f32_max", Type.F32)
            WasmOpCode.F64_MAX -> binaryStub(stack, builder, "__wark_f64_max", Type.F64)
            WasmOpCode.F32_COPYSIGN -> binaryStub(stack, builder, "__wark_f32_copysign", Type.F32)

            WasmOpCode.F32_EQ, WasmOpCode.F64_EQ -> fcmpOp(stack, FCmpPredicate.OEQ, builder)
            WasmOpCode.F32_NE, WasmOpCode.F64_NE -> fcmpOp(stack, FCmpPredicate.UNE, builder)
            WasmOpCode.F32_LT, WasmOpCode.F64_LT -> fcmpOp(stack, FCmpPredicate.OLT, builder)
            WasmOpCode.F32_GT, WasmOpCode.F64_GT -> fcmpOp(stack, FCmpPredicate.OGT, builder)
            WasmOpCode.F32_LE, WasmOpCode.F64_LE -> fcmpOp(stack, FCmpPredicate.OLE, builder)
            WasmOpCode.F32_GE, WasmOpCode.F64_GE -> fcmpOp(stack, FCmpPredicate.OGE, builder)

            WasmOpCode.I32_EQZ -> { stack.push(builder.zext(builder.icmp(ICmpPredicate.EQ, stack.pop(), Constant.I32(0)), Type.I32)) }
            WasmOpCode.I64_EQZ -> { stack.push(builder.zext(builder.icmp(ICmpPredicate.EQ, stack.pop(), Constant.I64(0)), Type.I32)) }
            WasmOpCode.I32_EQ, WasmOpCode.I64_EQ -> icmpOp(stack, ICmpPredicate.EQ, builder)
            WasmOpCode.I32_NE, WasmOpCode.I64_NE -> icmpOp(stack, ICmpPredicate.NE, builder)
            WasmOpCode.I32_LT_S, WasmOpCode.I64_LT_S -> icmpOp(stack, ICmpPredicate.SLT, builder)
            WasmOpCode.I32_LT_U, WasmOpCode.I64_LT_U -> icmpOp(stack, ICmpPredicate.ULT, builder)
            WasmOpCode.I32_GT_S, WasmOpCode.I64_GT_S -> icmpOp(stack, ICmpPredicate.SGT, builder)
            WasmOpCode.I32_GT_U, WasmOpCode.I64_GT_U -> icmpOp(stack, ICmpPredicate.UGT, builder)
            WasmOpCode.I32_LE_S, WasmOpCode.I64_LE_S -> icmpOp(stack, ICmpPredicate.SLE, builder)
            WasmOpCode.I32_LE_U, WasmOpCode.I64_LE_U -> icmpOp(stack, ICmpPredicate.ULE, builder)
            WasmOpCode.I32_GE_S, WasmOpCode.I64_GE_S -> icmpOp(stack, ICmpPredicate.SGE, builder)
            WasmOpCode.I32_GE_U, WasmOpCode.I64_GE_U -> icmpOp(stack, ICmpPredicate.UGE, builder)
            else -> { }
        }
    }

    private fun rotateLeft(stack: ValueStack, builder: ModuleBuilder, bits: Int, type: Type) {
        val amount = stack.pop()
        val value = stack.pop()
        val bitConst = if (type == Type.I32) { Constant.I32(bits) } else { Constant.I64(bits.toLong()) }
        stack.push(builder.or(builder.shl(value, amount), builder.lshr(value, builder.sub(bitConst, amount))))
    }

    private fun rotateRight(stack: ValueStack, builder: ModuleBuilder, bits: Int, type: Type) {
        val amount = stack.pop()
        val value = stack.pop()
        val bitConst = if (type == Type.I32) { Constant.I32(bits) } else { Constant.I64(bits.toLong()) }
        stack.push(builder.or(builder.lshr(value, amount), builder.shl(value, builder.sub(bitConst, amount))))
    }

    private fun unaryStub(stack: ValueStack, builder: ModuleBuilder, name: String, type: Type) {
        val value = stack.pop()
        stack.push(builder.call(name, listOf(value), type) ?: value)
    }

    private fun binaryStub(stack: ValueStack, builder: ModuleBuilder, name: String, type: Type) {
        val right = stack.pop()
        val left = stack.pop()
        stack.push(builder.call(name, listOf(left, right), type) ?: left)
    }

    private fun binaryOp(stack: ValueStack, operation: (Value, Value) -> Value) {
        val right = stack.pop()
        val left = stack.pop()
        stack.push(operation(left, right))
    }

    private fun icmpOp(stack: ValueStack, predicate: ICmpPredicate, builder: ModuleBuilder) {
        val right = stack.pop()
        val left = stack.pop()
        stack.push(builder.zext(builder.icmp(predicate, left, right), Type.I32))
    }

    private fun fcmpOp(stack: ValueStack, predicate: FCmpPredicate, builder: ModuleBuilder) {
        val right = stack.pop()
        val left = stack.pop()
        stack.push(builder.zext(builder.fcmp(predicate, left, right), Type.I32))
    }
}
