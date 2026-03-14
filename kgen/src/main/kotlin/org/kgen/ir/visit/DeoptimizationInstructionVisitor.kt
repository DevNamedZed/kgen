// Generated from instructions.yaml — do not edit
package org.kgen.ir.visit

import org.kgen.ir.instructions.*

/**
 * Visitor interface for deoptimization instructions.
 *
 * Implement this interface to receive callbacks for deoptimization instructions
 * during instruction traversal. All methods have default no-op implementations,
 * so only override the instructions you care about.
 */
interface DeoptimizationInstructionVisitor : InstructionVisitor {

    fun visitFrameState(instruction: FrameState) {}

    fun visitGuard(instruction: Guard) {}

    fun visitFixedGuard(instruction: FixedGuard) {}

    fun visitDeoptimize(instruction: Deoptimize) {}

    fun visitOSREntry(instruction: OSREntry) {}
}
