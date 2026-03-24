package org.kgen.ir.types

/**
 * Operator types for operator overloading in class definitions.
 *
 * Used with [ClassBuilder.defineOperator][org.kgen.ir.build.ClassBuilder] to
 * define operator methods on classes.
 *
 * ```java
 * cls.defineOperator(Operator.PLUS, params, Type.I32, fn -> { ... });
 * ```
 */
enum class Operator(val symbol: String, val methodName: String) {
    PLUS("+", "op_plus"),
    MINUS("-", "op_minus"),
    TIMES("*", "op_times"),
    DIVIDE("/", "op_divide"),
    REMAINDER("%", "op_remainder"),
    NEGATE("-", "op_negate"),
    EQUALS("==", "op_equals"),
    NOT_EQUALS("!=", "op_notEquals"),
    LESS_THAN("<", "op_lessThan"),
    LESS_THAN_OR_EQUAL("<=", "op_lessThanOrEqual"),
    GREATER_THAN(">", "op_greaterThan"),
    GREATER_THAN_OR_EQUAL(">=", "op_greaterThanOrEqual"),
    INDEX_GET("[]", "op_indexGet"),
    INDEX_SET("[]=", "op_indexSet"),
    INVOKE("()", "op_invoke"),
    CONTAINS("in", "op_contains"),
    BITWISE_AND("&", "op_bitwiseAnd"),
    BITWISE_OR("|", "op_bitwiseOr"),
    BITWISE_XOR("^", "op_bitwiseXor"),
    BITWISE_NOT("~", "op_bitwiseNot"),
    SHIFT_LEFT("<<", "op_shiftLeft"),
    SHIFT_RIGHT(">>", "op_shiftRight"),
}
