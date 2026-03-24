package org.wark.exec

/**
 * Pre-decoded instruction for fast interpreter dispatch.
 * Avoids string comparison on every instruction execution.
 */
class DecodedInstruction(
    val opcode: Int,
    val operandI32: Int = 0,
    val operandI64: Long = 0,
    val operandF32: Float = 0f,
    val operandF64: Double = 0.0,
    val operandI32B: Int = 0,
) {
    companion object {
        const val NOP = 0
        const val I32_CONST = 1
        const val I64_CONST = 2
        const val F32_CONST = 3
        const val F64_CONST = 4
        const val LOCAL_GET = 5
        const val LOCAL_SET = 6
        const val LOCAL_TEE = 7
        const val I32_ADD = 10
        const val I32_SUB = 11
        const val I32_MUL = 12
        const val I32_DIV_S = 13
        const val I32_DIV_U = 14
        const val I32_REM_S = 15
        const val I32_REM_U = 16
        const val I32_AND = 17
        const val I32_OR = 18
        const val I32_XOR = 19
        const val I32_SHL = 20
        const val I32_SHR_S = 21
        const val I32_SHR_U = 22
        const val I64_ADD = 30
        const val I64_SUB = 31
        const val I64_MUL = 32
        const val I64_DIV_S = 33
        const val I64_AND = 37
        const val I64_OR = 38
        const val I64_XOR = 39
        const val I64_SHL = 40
        const val I64_SHR_S = 41
        const val I64_SHR_U = 42
        const val I32_EQZ = 50
        const val I32_EQ = 51
        const val I32_NE = 52
        const val I32_LT_S = 53
        const val I32_LT_U = 54
        const val I32_GT_S = 55
        const val I32_GT_U = 56
        const val I32_LE_S = 57
        const val I32_LE_U = 58
        const val I32_GE_S = 59
        const val I32_GE_U = 60
        const val I32_LOAD = 70
        const val I32_STORE = 71
        const val I64_LOAD = 72
        const val I64_STORE = 73
        const val I32_LOAD8_S = 74
        const val I32_LOAD8_U = 75
        const val I32_STORE8 = 76
        const val I32_LOAD16_U = 77
        const val GLOBAL_GET = 80
        const val GLOBAL_SET = 81
        const val MEMORY_SIZE = 82
        const val MEMORY_GROW = 83
        const val MEMORY_COPY = 84
        const val MEMORY_FILL = 85
        const val DROP = 90
        const val SELECT = 91
        const val CALL = 92
        const val CALL_INDIRECT = 93
        const val RETURN = 94
        const val BLOCK = 100
        const val LOOP = 101
        const val IF = 102
        const val ELSE = 103
        const val END = 104
        const val BR = 105
        const val BR_IF = 106
        const val BR_TABLE = 107
        const val UNREACHABLE = 108
        const val I32_CLZ = 110
        const val I32_CTZ = 111
        const val I32_POPCNT = 112
        const val I32_WRAP_I64 = 120
        const val I64_EXTEND_I32_S = 121
        const val I64_EXTEND_I32_U = 122
        const val F64_ADD = 130
        const val F64_SUB = 131
        const val F64_MUL = 132
        const val F64_DIV = 133
        const val F32_ADD = 134
        const val F32_SUB = 135
        const val F32_MUL = 136
        const val F32_DIV = 137
        const val UNKNOWN = 999
    }
}
