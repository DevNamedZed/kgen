// Generated from instructions.yaml — do not edit
package org.kgen.ir.visit

import org.kgen.ir.instructions.*

/**
 * Visitor interface for conversion instructions.
 *
 * Implement this interface to receive callbacks for conversion instructions
 * during instruction traversal. All methods have default no-op implementations,
 * so only override the instructions you care about.
 */
interface ConversionInstructionVisitor : InstructionVisitor {

    fun visitIntTrunc(instruction: IntTrunc) {}

    fun visitZExt(instruction: ZExt) {}

    fun visitSExt(instruction: SExt) {}

    fun visitFPTrunc(instruction: FPTrunc) {}

    fun visitFPExt(instruction: FPExt) {}

    fun visitFPToUI(instruction: FPToUI) {}

    fun visitFPToSI(instruction: FPToSI) {}

    fun visitUIToFP(instruction: UIToFP) {}

    fun visitSIToFP(instruction: SIToFP) {}

    fun visitPtrToInt(instruction: PtrToInt) {}

    fun visitIntToPtr(instruction: IntToPtr) {}

    fun visitBitCast(instruction: BitCast) {}

    fun visitAddrSpaceCast(instruction: AddrSpaceCast) {}
}
