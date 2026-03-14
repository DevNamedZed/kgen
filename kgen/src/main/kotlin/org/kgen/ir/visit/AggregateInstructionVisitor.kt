// Generated from instructions.yaml — do not edit
package org.kgen.ir.visit

import org.kgen.ir.instructions.*

/**
 * Visitor interface for aggregate instructions.
 *
 * Implement this interface to receive callbacks for aggregate instructions
 * during instruction traversal. All methods have default no-op implementations,
 * so only override the instructions you care about.
 */
interface AggregateInstructionVisitor : InstructionVisitor {

    fun visitExtractValue(instruction: ExtractValue) {}

    fun visitInsertValue(instruction: InsertValue) {}
}
