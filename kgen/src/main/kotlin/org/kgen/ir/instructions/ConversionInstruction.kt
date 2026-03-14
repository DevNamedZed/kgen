// Generated from instructions.yaml — do not edit
package org.kgen.ir.instructions

import org.kgen.ir.InstructionRef
import org.kgen.ir.IrCategory
import org.kgen.ir.Value
import org.kgen.ir.Type

/**
 * Type conversion and casting instructions.
 *
 * Conversion instructions transform a value from one type to another.
 */
sealed interface ConversionInstruction : Instruction {
    override val category get() = IrCategory.CONVERSION
}

// --- Integer width conversions ---

/**
 * Integer truncation: narrows an integer to a smaller bit width.
 *
 * @param dest the SSA result reference
 * @param value operand value
 * @param toType configuration
 */
data class IntTrunc(
    val dest: InstructionRef,
    val value: Value,
    val toType: Type,
) : ConversionInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE
    override val operands get() = listOf(value)
}

/**
 * Zero extension: widens an integer by padding with zeros.
 *
 * @param dest the SSA result reference
 * @param value operand value
 * @param toType configuration
 */
data class ZExt(
    val dest: InstructionRef,
    val value: Value,
    val toType: Type,
) : ConversionInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE
    override val operands get() = listOf(value)
}

/**
 * Sign extension: widens an integer by replicating the sign bit.
 *
 * @param dest the SSA result reference
 * @param value operand value
 * @param toType configuration
 */
data class SExt(
    val dest: InstructionRef,
    val value: Value,
    val toType: Type,
) : ConversionInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE
    override val operands get() = listOf(value)
}

// --- Floating-point precision conversions ---

/**
 * Floating-point truncation: narrows to a smaller precision type.
 *
 * @param dest the SSA result reference
 * @param value operand value
 * @param toType configuration
 */
data class FPTrunc(
    val dest: InstructionRef,
    val value: Value,
    val toType: Type,
) : ConversionInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE
    override val operands get() = listOf(value)
}

/**
 * Floating-point extension: widens to a larger precision type.
 *
 * @param dest the SSA result reference
 * @param value operand value
 * @param toType configuration
 */
data class FPExt(
    val dest: InstructionRef,
    val value: Value,
    val toType: Type,
) : ConversionInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE
    override val operands get() = listOf(value)
}

// --- Float/integer conversions ---

/**
 * Floating-point to unsigned integer.
 *
 * @param dest the SSA result reference
 * @param value operand value
 * @param toType configuration
 */
data class FPToUI(
    val dest: InstructionRef,
    val value: Value,
    val toType: Type,
) : ConversionInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE
    override val operands get() = listOf(value)
}

/**
 * Floating-point to signed integer.
 *
 * @param dest the SSA result reference
 * @param value operand value
 * @param toType configuration
 */
data class FPToSI(
    val dest: InstructionRef,
    val value: Value,
    val toType: Type,
) : ConversionInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE
    override val operands get() = listOf(value)
}

/**
 * Unsigned integer to floating-point.
 *
 * @param dest the SSA result reference
 * @param value operand value
 * @param toType configuration
 */
data class UIToFP(
    val dest: InstructionRef,
    val value: Value,
    val toType: Type,
) : ConversionInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE
    override val operands get() = listOf(value)
}

/**
 * Signed integer to floating-point.
 *
 * @param dest the SSA result reference
 * @param value operand value
 * @param toType configuration
 */
data class SIToFP(
    val dest: InstructionRef,
    val value: Value,
    val toType: Type,
) : ConversionInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE
    override val operands get() = listOf(value)
}

// --- Pointer/integer casts ---

/**
 * Pointer to integer: reinterprets a pointer as an integer.
 *
 * @param dest the SSA result reference
 * @param value operand value
 * @param toType configuration
 */
data class PtrToInt(
    val dest: InstructionRef,
    val value: Value,
    val toType: Type,
) : ConversionInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE
    override val operands get() = listOf(value)
}

/**
 * Integer to pointer: reinterprets an integer as a pointer.
 *
 * @param dest the SSA result reference
 * @param value operand value
 * @param toType configuration
 */
data class IntToPtr(
    val dest: InstructionRef,
    val value: Value,
    val toType: Type,
) : ConversionInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE
    override val operands get() = listOf(value)
}

// --- Reinterpretation casts ---

/**
 * Bitcast: reinterprets the bits of a value as a different type.
 *
 * @param dest the SSA result reference
 * @param value operand value
 * @param toType configuration
 */
data class BitCast(
    val dest: InstructionRef,
    val value: Value,
    val toType: Type,
) : ConversionInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE
    override val operands get() = listOf(value)
}

/**
 * Address space cast: converts a pointer between address spaces.
 *
 * @param dest the SSA result reference
 * @param value operand value
 * @param toType configuration
 */
data class AddrSpaceCast(
    val dest: InstructionRef,
    val value: Value,
    val toType: Type,
) : ConversionInstruction {
    override val result get() = dest
    override val effects get() = InstructionEffects.PURE
    override val operands get() = listOf(value)
}

