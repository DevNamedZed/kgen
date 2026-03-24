package org.kgen.pipeline

import org.kgen.codegen.OptLevel

/**
 * Extension to create a [Pipeline] from a [OptLevel].
 *
 * ```kotlin
 * val pipeline = OptLevel.O2.pipeline()
 * val optimized = pipeline.execute(module)
 * ```
 */
fun OptLevel.pipeline(): Pipeline = when (this) {
    OptLevel.O0 -> Pipeline()
    OptLevel.O1 -> Pipeline()
        .add(Mem2Reg())
        .add(ConstantFolding())
        .add(InstructionCombining())
        .add(DeadCodeElimination())
    OptLevel.O2 -> Pipeline()
        .add(ScalarReplacementOfAggregates())
        .add(Mem2Reg())
        .add(Inlining())
        .add(ConstantFolding())
        .add(InstructionCombining())
        .add(GlobalValueNumbering())
        .add(DeadCodeElimination())
        .add(JumpThreading())
        .add(LoopInvariantCodeMotion())
        .add(ConstantFolding())
        .add(DeadCodeElimination())
    OptLevel.O3 -> OptLevel.O2.pipeline()
    OptLevel.OS -> OptLevel.O1.pipeline()
    OptLevel.OZ -> OptLevel.O1.pipeline()
}
