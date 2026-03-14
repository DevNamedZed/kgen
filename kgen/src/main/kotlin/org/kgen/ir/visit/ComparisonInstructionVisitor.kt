// Generated from instructions.yaml — do not edit
package org.kgen.ir.visit

import org.kgen.ir.instructions.*

/**
 * Visitor interface for comparison instructions.
 *
 * Implement this interface to receive callbacks for comparison instructions
 * during instruction traversal. All methods have default no-op implementations,
 * so only override the instructions you care about.
 */
interface ComparisonInstructionVisitor : InstructionVisitor {

    fun visitICmp(instruction: ICmp) {}

    fun visitFCmp(instruction: FCmp) {}
}
