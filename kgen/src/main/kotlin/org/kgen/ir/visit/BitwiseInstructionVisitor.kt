// Generated from instructions.yaml — do not edit
package org.kgen.ir.visit

import org.kgen.ir.instructions.*

/**
 * Visitor interface for bitwise instructions.
 *
 * Implement this interface to receive callbacks for bitwise instructions
 * during instruction traversal. All methods have default no-op implementations,
 * so only override the instructions you care about.
 */
interface BitwiseInstructionVisitor : InstructionVisitor {

    fun visitAnd(instruction: And) {}

    fun visitOr(instruction: Or) {}

    fun visitXor(instruction: Xor) {}

    fun visitNot(instruction: Not) {}

    fun visitShl(instruction: Shl) {}

    fun visitLShr(instruction: LShr) {}

    fun visitAShr(instruction: AShr) {}

    fun visitRotl(instruction: Rotl) {}

    fun visitRotr(instruction: Rotr) {}

    fun visitCtlz(instruction: Ctlz) {}

    fun visitCttz(instruction: Cttz) {}

    fun visitCtpop(instruction: Ctpop) {}

    fun visitBSwap(instruction: BSwap) {}

    fun visitBitReverse(instruction: BitReverse) {}
}
