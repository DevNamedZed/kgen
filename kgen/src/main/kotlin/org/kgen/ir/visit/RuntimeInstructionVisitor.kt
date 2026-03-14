// Generated from instructions.yaml — do not edit
package org.kgen.ir.visit

import org.kgen.ir.instructions.*

/**
 * Visitor interface for runtime instructions.
 *
 * Implement this interface to receive callbacks for runtime instructions
 * during instruction traversal. All methods have default no-op implementations,
 * so only override the instructions you care about.
 */
interface RuntimeInstructionVisitor : InstructionVisitor {

    fun visitGCAlloc(instruction: GCAlloc) {}

    fun visitGCSafepoint(instruction: GCSafepoint) {}

    fun visitGCRoot(instruction: GCRoot) {}

    fun visitWriteBarrier(instruction: WriteBarrier) {}

    fun visitReadBarrier(instruction: ReadBarrier) {}

    fun visitRefRetain(instruction: RefRetain) {}

    fun visitRefRelease(instruction: RefRelease) {}

    fun visitRefCount(instruction: RefCount) {}

    fun visitCoroBegin(instruction: CoroBegin) {}

    fun visitCoroEnd(instruction: CoroEnd) {}

    fun visitCoroSuspend(instruction: CoroSuspend) {}

    fun visitCoroResume(instruction: CoroResume) {}

    fun visitCoroDestroy(instruction: CoroDestroy) {}

    fun visitCoroSize(instruction: CoroSize) {}

    fun visitGCRelocate(instruction: GCRelocate) {}
}
