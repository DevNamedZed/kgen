// Generated from instructions.yaml — do not edit
package org.kgen.ir.visit

import org.kgen.ir.instructions.*

/**
 * Visitor interface for interop instructions.
 *
 * Implement this interface to receive callbacks for interop instructions
 * during instruction traversal. All methods have default no-op implementations,
 * so only override the instructions you care about.
 */
interface InteropInstructionVisitor : InstructionVisitor {

    fun visitPin(instruction: Pin) {}

    fun visitUnpin(instruction: Unpin) {}

    fun visitInteriorPtr(instruction: InteriorPtr) {}

    fun visitManagedCall(instruction: ManagedCall) {}

    fun visitManagedToDevice(instruction: ManagedToDevice) {}

    fun visitDeviceRelease(instruction: DeviceRelease) {}
}
