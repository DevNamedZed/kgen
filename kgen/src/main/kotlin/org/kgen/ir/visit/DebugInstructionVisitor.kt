// Generated from instructions.yaml — do not edit
package org.kgen.ir.visit

import org.kgen.ir.instructions.*

/**
 * Visitor interface for debug instructions.
 *
 * Implement this interface to receive callbacks for debug instructions
 * during instruction traversal. All methods have default no-op implementations,
 * so only override the instructions you care about.
 */
interface DebugInstructionVisitor : InstructionVisitor {

    fun visitDebugLoc(instruction: DebugLoc) {}

    fun visitDebugValue(instruction: DebugValue) {}

    fun visitDebugDeclare(instruction: DebugDeclare) {}

    fun visitAssume(instruction: Assume) {}

    fun visitExpect(instruction: Expect) {}
}
