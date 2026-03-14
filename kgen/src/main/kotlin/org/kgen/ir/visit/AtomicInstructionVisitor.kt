// Generated from instructions.yaml — do not edit
package org.kgen.ir.visit

import org.kgen.ir.instructions.*

/**
 * Visitor interface for atomic instructions.
 *
 * Implement this interface to receive callbacks for atomic instructions
 * during instruction traversal. All methods have default no-op implementations,
 * so only override the instructions you care about.
 */
interface AtomicInstructionVisitor : InstructionVisitor {

    fun visitFence(instruction: Fence) {}

    fun visitCmpXchg(instruction: CmpXchg) {}

    fun visitAtomicRMW(instruction: AtomicRMW) {}
}
