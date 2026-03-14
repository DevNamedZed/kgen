// Generated from instructions.yaml — do not edit
package org.kgen.ir.visit

import org.kgen.ir.instructions.*

/**
 * Visitor interface for vector instructions.
 *
 * Implement this interface to receive callbacks for vector instructions
 * during instruction traversal. All methods have default no-op implementations,
 * so only override the instructions you care about.
 */
interface VectorInstructionVisitor : InstructionVisitor {

    fun visitExtractElement(instruction: ExtractElement) {}

    fun visitInsertElement(instruction: InsertElement) {}

    fun visitShuffleVector(instruction: ShuffleVector) {}

    fun visitSplat(instruction: Splat) {}

    fun visitVectorReduce(instruction: VectorReduce) {}
}
