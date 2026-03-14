// Generated from instructions.yaml — do not edit
package org.kgen.ir.visit

import org.kgen.ir.instructions.*

/**
 * Visitor interface for memory instructions.
 *
 * Implement this interface to receive callbacks for memory instructions
 * during instruction traversal. All methods have default no-op implementations,
 * so only override the instructions you care about.
 */
interface MemoryInstructionVisitor : InstructionVisitor {

    fun visitAlloca(instruction: Alloca) {}

    fun visitLoad(instruction: Load) {}

    fun visitStore(instruction: Store) {}

    fun visitGetElementPtr(instruction: GetElementPtr) {}

    fun visitMemCpy(instruction: MemCpy) {}

    fun visitMemSet(instruction: MemSet) {}

    fun visitMemMove(instruction: MemMove) {}

    fun visitPrefetch(instruction: Prefetch) {}

    fun visitStackSave(instruction: StackSave) {}

    fun visitStackRestore(instruction: StackRestore) {}

    fun visitLifetimeStart(instruction: LifetimeStart) {}

    fun visitLifetimeEnd(instruction: LifetimeEnd) {}

    fun visitVAStart(instruction: VAStart) {}

    fun visitVAEnd(instruction: VAEnd) {}

    fun visitVACopy(instruction: VACopy) {}

    fun visitVAArg(instruction: VAArg) {}
}
