// Generated from instructions.yaml — do not edit
package org.kgen.ir.visit

import org.kgen.ir.instructions.*

/**
 * Visitor interface for call instructions.
 *
 * Implement this interface to receive callbacks for call instructions
 * during instruction traversal. All methods have default no-op implementations,
 * so only override the instructions you care about.
 */
interface CallInstructionVisitor : InstructionVisitor {

    fun visitCall(instruction: Call) {}

    fun visitInvoke(instruction: Invoke) {}

    fun visitCallBr(instruction: CallBr) {}
}
