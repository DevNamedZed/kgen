// Generated from instructions.yaml — do not edit
package org.kgen.ir.visit

import org.kgen.ir.instructions.*

/**
 * Visitor interface for terminator instructions.
 *
 * Implement this interface to receive callbacks for terminator instructions
 * during instruction traversal. All methods have default no-op implementations,
 * so only override the instructions you care about.
 */
interface TerminatorInstructionVisitor : InstructionVisitor {

    fun visitRet(instruction: Ret) {}

    fun visitBr(instruction: Br) {}

    fun visitCondBr(instruction: CondBr) {}

    fun visitSwitch(instruction: Switch) {}

    fun visitIndirectBr(instruction: IndirectBr) {}

    fun visitUnreachable(instruction: Unreachable) {}

    fun visitTrap(instruction: Trap) {}

    fun visitDebugTrap(instruction: DebugTrap) {}
}
