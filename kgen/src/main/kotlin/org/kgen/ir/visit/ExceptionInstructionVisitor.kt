// Generated from instructions.yaml — do not edit
package org.kgen.ir.visit

import org.kgen.ir.instructions.*

/**
 * Visitor interface for exception instructions.
 *
 * Implement this interface to receive callbacks for exception instructions
 * during instruction traversal. All methods have default no-op implementations,
 * so only override the instructions you care about.
 */
interface ExceptionInstructionVisitor : InstructionVisitor {

    fun visitLandingPad(instruction: LandingPad) {}

    fun visitResume(instruction: Resume) {}

    fun visitCatchSwitch(instruction: CatchSwitch) {}

    fun visitCatchPad(instruction: CatchPad) {}

    fun visitCleanupPad(instruction: CleanupPad) {}

    fun visitCatchRet(instruction: CatchRet) {}

    fun visitCleanupRet(instruction: CleanupRet) {}
}
