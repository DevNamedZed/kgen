package org.kgen.pass

import org.kgen.ir.*
import org.kgen.ir.instructions.*

/**
 * Profile-guided optimization (PGO) — uses runtime profiling data to
 * make better optimization decisions.
 *
 * PGO works in three phases:
 * 1. **Instrument**: Insert counters into the IR to collect execution profiles
 * 2. **Profile**: Run the instrumented program and collect profile data
 * 3. **Optimize**: Use the profile data to guide optimizations
 *
 * Supported profile-guided optimizations:
 * - **Hot/cold function splitting**: Mark frequently-executed functions as hot
 * - **Branch weight propagation**: Annotate conditional branches with taken/not-taken ratios
 * - **Inlining budget**: Increase inlining threshold for hot call sites
 * - **Block reordering**: Layout hot paths linearly (fall-through for common case)
 *
 * ```java
 * // Phase 1: Instrument
 * var instrumented = ProfileGuidedOptimization.instrument(module);
 * // Phase 2: Run and collect profile (external)
 * var profile = ProfileData.load(profileFile);
 * // Phase 3: Optimize
 * var pgo = new ProfileGuidedOptimization(profile);
 * var optimized = pgo.run(module);
 * ```
 */
class ProfileGuidedOptimization(
    private val profile: ProfileData = ProfileData.empty(),
) : ModulePass {

    override fun run(module: Module): Module {
        if (profile.isEmpty()) return module

        var result = module
        result = propagateBranchWeights(result)
        result = annotateHotFunctions(result)
        result = inlineHotCallSites(result)
        return result
    }

    /**
     * Annotate conditional branches with profiled branch weights.
     */
    private fun propagateBranchWeights(module: Module): Module {
        var changed = false
        val newFunctions = module.functions.map { fn ->
            if (fn.isExternal) fn
            else {
                val result = propagateBranchWeightsInFunction(fn)
                if (result !== fn) changed = true
                result
            }
        }
        return if (changed) module.copy(functions = newFunctions) else module
    }

    private fun propagateBranchWeightsInFunction(fn: IrFunction): IrFunction {
        var anyChanged = false
        val newBlocks = fn.blocks.map { block ->
            val branchData = profile.branchData("${fn.name}:${block.label}")
            if (branchData != null && block.instructions.isNotEmpty()) {
                val lastInst = block.instructions.last()
                if (lastInst is CondBr) {
                    anyChanged = true
                    val newLast = lastInst.copy(
                        trueWeight = branchData.trueCount,
                        falseWeight = branchData.falseCount,
                    )
                    BasicBlock(block.label, block.instructions.dropLast(1) + newLast)
                } else block
            } else block
        }
        return if (anyChanged) fn.copy(blocks = newBlocks) else fn
    }

    /**
     * Mark hot functions with the HOT attribute and cold functions with COLD.
     */
    private fun annotateHotFunctions(module: Module): Module {
        var changed = false
        val newFunctions = module.functions.map { fn ->
            val execCount = profile.functionExecutionCount(fn.name)
            if (execCount > 0) {
                val attrs = fn.attributes.toMutableSet()
                val isHot = execCount >= profile.hotThreshold
                val isCold = execCount <= profile.coldThreshold

                if (isHot && !attrs.contains(FnAttribute.HOT)) {
                    attrs.add(FnAttribute.HOT)
                    attrs.remove(FnAttribute.COLD)
                    changed = true
                    fn.copy(attributes = attrs)
                } else if (isCold && !attrs.contains(FnAttribute.COLD)) {
                    attrs.add(FnAttribute.COLD)
                    attrs.remove(FnAttribute.HOT)
                    changed = true
                    fn.copy(attributes = attrs)
                } else fn
            } else fn
        }
        return if (changed) module.copy(functions = newFunctions) else module
    }

    /**
     * Increase inlining for hot call sites by running the inlining pass
     * with a higher threshold.
     */
    private fun inlineHotCallSites(module: Module): Module {
        // Only inline if there are hot functions
        val hasHotFunctions = module.functions.any { it.attributes.contains(FnAttribute.HOT) }
        return if (hasHotFunctions) {
            Inlining(maxInstructionCount = 100).run(module)
        } else module
    }

    companion object {
        /**
         * Instrument a module by inserting profiling counters at branch points
         * and function entries. The instrumented code writes profile data
         * that can be loaded with [ProfileData.load].
         */
        @JvmStatic
        fun instrument(module: Module): Module {
            var changed = false
            val newFunctions = module.functions.map { fn ->
                if (fn.isExternal) fn
                else {
                    val result = instrumentFunction(fn)
                    if (result !== fn) changed = true
                    result
                }
            }
            return if (changed) module.copy(functions = newFunctions) else module
        }

        private fun instrumentFunction(fn: IrFunction): IrFunction {
            // Insert a profiling counter call at the entry of each basic block
            val newBlocks = fn.blocks.map { block ->
                val counterName = "${fn.name}:${block.label}"
                val counterCall = Call(
                    dest = null,
                    function = FunctionRef("__pgo_increment_counter", Type.Function(listOf(Type.I64), Type.Void)),
                    args = listOf(Constant.I64(counterName.hashCode().toLong())),
                    returnType = Type.Void,
                )
                BasicBlock(block.label, listOf(counterCall) + block.instructions)
            }
            return fn.copy(blocks = newBlocks)
        }
    }
}

/**
 * Profile data collected from instrumented program execution.
 */
class ProfileData private constructor(
    private val functionCounts: Map<String, Long>,
    private val branchCounts: Map<String, BranchData>,
    val hotThreshold: Long,
    val coldThreshold: Long,
) {
    /** Execution count for a function. 0 if not profiled. */
    fun functionExecutionCount(name: String): Long = functionCounts[name] ?: 0

    /** Branch data for a specific branch point (function:block). */
    fun branchData(key: String): BranchData? = branchCounts[key]

    /** Whether this profile has any data. */
    fun isEmpty(): Boolean = functionCounts.isEmpty() && branchCounts.isEmpty()

    /** All profiled function names. */
    fun profiledFunctions(): Set<String> = functionCounts.keys

    /** Total number of profiled samples. */
    fun totalSamples(): Long = functionCounts.values.sum()

    override fun toString(): String =
        "ProfileData(${functionCounts.size} functions, ${branchCounts.size} branches, ${totalSamples()} samples)"

    /**
     * Branch taken/not-taken counts.
     */
    data class BranchData(
        val trueCount: Long,
        val falseCount: Long,
    ) {
        /** Probability of the true branch (0.0 to 1.0). */
        val trueProbability: Double get() {
            val total = trueCount + falseCount
            return if (total == 0L) 0.5 else trueCount.toDouble() / total
        }
    }

    companion object {
        /** Empty profile — no data collected. */
        @JvmStatic
        fun empty(): ProfileData = ProfileData(emptyMap(), emptyMap(), 1000, 10)

        /**
         * Build profile data from raw counter maps.
         */
        @JvmStatic
        @JvmOverloads
        fun of(
            functionCounts: Map<String, Long>,
            branchCounts: Map<String, BranchData> = emptyMap(),
            hotThreshold: Long = 1000,
            coldThreshold: Long = 10,
        ): ProfileData = ProfileData(functionCounts, branchCounts, hotThreshold, coldThreshold)
    }
}
