package org.kgen.pass

/**
 * Preset optimization levels, analogous to -O0, -O1, -O2, -Os.
 *
 * ```kotlin
 * val pipeline = OptLevel.O1.pipeline()
 * val optimized = pipeline.execute(module)
 * ```
 */
enum class OptLevel {

    /** No optimization. Pass-through. */
    O0,

    /** Basic optimizations: mem2reg, constant folding, instruction combining, DCE. */
    O1,

    /** Standard optimizations: O1 + GVN, inlining, jump threading, LICM. */
    O2,

    /** Optimize for code size: same as O1 (size-specific passes added later). */
    Os;

    fun pipeline(): PassPipeline = when (this) {
        O0 -> PassPipeline()
        O1 -> PassPipeline()
            .add(Mem2Reg())
            .add(ConstantFolding())
            .add(InstructionCombining())
            .add(DeadCodeElimination())
        O2 -> PassPipeline()
            .add(ScalarReplacementOfAggregates())
            .add(Mem2Reg())
            .add(Inlining())
            .add(ConstantFolding())
            .add(InstructionCombining())
            .add(GlobalValueNumbering())
            .add(DeadCodeElimination())
            .add(JumpThreading())
            .add(LoopInvariantCodeMotion())
            .add(ConstantFolding())    // second pass catches newly exposed constants
            .add(DeadCodeElimination())
        Os -> PassPipeline()
            .add(Mem2Reg())
            .add(ConstantFolding())
            .add(InstructionCombining())
            .add(DeadCodeElimination())
    }
}
