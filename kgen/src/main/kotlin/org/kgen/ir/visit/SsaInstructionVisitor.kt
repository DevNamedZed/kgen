// Generated from instructions.yaml — do not edit
package org.kgen.ir.visit

import org.kgen.ir.instructions.*

/**
 * Visitor interface for ssa instructions.
 *
 * Implement this interface to receive callbacks for ssa instructions
 * during instruction traversal. All methods have default no-op implementations,
 * so only override the instructions you care about.
 */
interface SsaInstructionVisitor : InstructionVisitor {

    fun visitPhi(instruction: Phi) {}

    fun visitSelect(instruction: Select) {}

    fun visitFreeze(instruction: Freeze) {}

    fun visitPiNode(instruction: PiNode) {}
}
