// Generated from instructions.yaml — do not edit
package org.kgen.ir.build.sets

import org.kgen.ir.*
import org.kgen.ir.instructions.*
import org.kgen.ir.Type

/**
 * Emission interface for conversion instructions.
 *
 * Each method emits one instruction and returns the SSA result value
 * (or Unit for void instructions).
 */
interface ConversionInstructionSet : InstructionSet {

    /**
     * Integer truncation: narrows an integer to a smaller bit width.
     *
     * Emits a [IntTrunc] instruction into the current block.
     *
     * @param value source operand
     * @param toType the type
     * @return the SSA value produced by this instruction
     */
    fun trunc(value: Value, toType: Type): Value

    /**
     * Zero extension: widens an integer by padding with zeros.
     *
     * Emits a [ZExt] instruction into the current block.
     *
     * @param value source operand
     * @param toType the type
     * @return the SSA value produced by this instruction
     */
    fun zext(value: Value, toType: Type): Value

    /**
     * Sign extension: widens an integer by replicating the sign bit.
     *
     * Emits a [SExt] instruction into the current block.
     *
     * @param value source operand
     * @param toType the type
     * @return the SSA value produced by this instruction
     */
    fun sext(value: Value, toType: Type): Value

    /**
     * Floating-point truncation: narrows to a smaller precision type.
     *
     * Emits a [FPTrunc] instruction into the current block.
     *
     * @param value source operand
     * @param toType the type
     * @return the SSA value produced by this instruction
     */
    fun fptrunc(value: Value, toType: Type): Value

    /**
     * Floating-point extension: widens to a larger precision type.
     *
     * Emits a [FPExt] instruction into the current block.
     *
     * @param value source operand
     * @param toType the type
     * @return the SSA value produced by this instruction
     */
    fun fpext(value: Value, toType: Type): Value

    /**
     * Floating-point to unsigned integer.
     *
     * Emits a [FPToUI] instruction into the current block.
     *
     * @param value source operand
     * @param toType the type
     * @return the SSA value produced by this instruction
     */
    fun fptoui(value: Value, toType: Type): Value

    /**
     * Floating-point to signed integer.
     *
     * Emits a [FPToSI] instruction into the current block.
     *
     * @param value source operand
     * @param toType the type
     * @return the SSA value produced by this instruction
     */
    fun fptosi(value: Value, toType: Type): Value

    /**
     * Unsigned integer to floating-point.
     *
     * Emits a [UIToFP] instruction into the current block.
     *
     * @param value source operand
     * @param toType the type
     * @return the SSA value produced by this instruction
     */
    fun uitofp(value: Value, toType: Type): Value

    /**
     * Signed integer to floating-point.
     *
     * Emits a [SIToFP] instruction into the current block.
     *
     * @param value source operand
     * @param toType the type
     * @return the SSA value produced by this instruction
     */
    fun sitofp(value: Value, toType: Type): Value

    /**
     * Pointer to integer: reinterprets a pointer as an integer.
     *
     * Emits a [PtrToInt] instruction into the current block.
     *
     * @param value source operand
     * @param toType the type
     * @return the SSA value produced by this instruction
     */
    fun ptrtoint(value: Value, toType: Type): Value

    /**
     * Integer to pointer: reinterprets an integer as a pointer.
     *
     * Emits a [IntToPtr] instruction into the current block.
     *
     * @param value source operand
     * @param toType the type
     * @return the SSA value produced by this instruction
     */
    fun inttoptr(value: Value, toType: Type): Value

    /**
     * Bitcast: reinterprets the bits of a value as a different type.
     *
     * Emits a [BitCast] instruction into the current block.
     *
     * @param value source operand
     * @param toType the type
     * @return the SSA value produced by this instruction
     */
    fun bitcast(value: Value, toType: Type): Value

    /**
     * Address space cast: converts a pointer between address spaces.
     *
     * Emits a [AddrSpaceCast] instruction into the current block.
     *
     * @param value source operand
     * @param toType the type
     * @return the SSA value produced by this instruction
     */
    fun addrspacecast(value: Value, toType: Type): Value
}
