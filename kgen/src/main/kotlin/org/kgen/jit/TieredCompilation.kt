package org.kgen.jit

import org.kgen.ir.Module
import org.kgen.pipeline.Pipeline

/**
 * Tiered compilation strategy — compile functions at low optimization first,
 * then recompile hot functions at higher optimization levels.
 *
 * Tier 0: Compile with no optimization (fast startup).
 * Tier 1: Recompile with full optimization after [recompileThreshold] invocations.
 *
 * Call counts are tracked per-function. When a function's count exceeds the threshold,
 * its module is recompiled with the tier-1 pipeline and hot-swapped into the engine.
 *
 * ```java
 * var tiered = new TieredCompilation();
 * tiered.setRecompileThreshold(100);
 * tiered.setTier1Pipeline(optimizingPipeline);
 *
 * var jit = new JitEngine(generator);
 * jit.setTieredCompilation(tiered);
 *
 * // First call compiles at O0, after 100 calls recompiles at O2
 * for (int i = 0; i < 200; i++) {
 *     jit.call("hotFunction", i);
 * }
 * ```
 */
class TieredCompilation(
    /** Number of calls before triggering tier-1 recompilation. */
    var recompileThreshold: Int = 100,
    /** Number of calls before triggering tier-2 recompilation. 0 = disabled. */
    var tier2Threshold: Int = 0,
) {
    private var tier1Pipeline: Pipeline? = null
    private var tier2Pipeline: Pipeline? = null
    private val callCounts = mutableMapOf<String, Int>()
    private val currentTier = mutableMapOf<String, Int>()
    private val moduleForFunction = mutableMapOf<String, Module>()

    /**
     * Set the optimization pipeline for tier-1 (optimized) recompilation.
     * If not set, tier-1 uses the engine's default pipeline.
     */
    fun setTier1Pipeline(pipeline: Pipeline) {
        this.tier1Pipeline = pipeline
    }

    /** The tier-1 pipeline, if set. */
    fun tier1Pipeline(): Pipeline? = tier1Pipeline

    /**
     * Set the optimization pipeline for tier-2 (aggressively optimized) recompilation.
     * Only used if [tier2Threshold] > 0.
     */
    fun setTier2Pipeline(pipeline: Pipeline) {
        this.tier2Pipeline = pipeline
    }

    /** The tier-2 pipeline, if set. */
    fun tier2Pipeline(): Pipeline? = tier2Pipeline

    /**
     * Register the source module for a function, enabling future recompilation.
     */
    internal fun registerModule(functionName: String, module: Module) {
        moduleForFunction[functionName] = module
    }

    /**
     * Register all function names from a module for recompilation tracking.
     */
    internal fun registerAllFunctions(module: Module) {
        for (fn in module.functions) {
            if (!fn.isExternal) {
                moduleForFunction[fn.name] = module
            }
        }
    }

    /**
     * Record a function call. Returns true if recompilation should be triggered.
     */
    internal fun recordCall(name: String): Boolean {
        val tier = currentTier.getOrDefault(name, 0)
        val maxTier = if (tier2Threshold > 0) 2 else 1
        if (tier >= maxTier) return false

        val count = callCounts.getOrDefault(name, 0) + 1
        callCounts[name] = count

        val threshold = when (tier) {
            0 -> recompileThreshold
            1 -> tier2Threshold
            else -> return false
        }
        return count == threshold && name in moduleForFunction
    }

    /**
     * Get the pipeline for the next tier of a function, or null if no more tiers.
     */
    internal fun pipelineForNextTier(name: String): Pipeline? {
        val tier = currentTier.getOrDefault(name, 0)
        return when (tier) {
            0 -> tier1Pipeline
            1 -> tier2Pipeline
            else -> null
        }
    }

    /**
     * Get the source module for a function that needs recompilation.
     */
    internal fun moduleForRecompilation(name: String): Module? {
        val tier = currentTier.getOrDefault(name, 0)
        val maxTier = if (tier2Threshold > 0) 2 else 1
        if (tier >= maxTier) return null
        return moduleForFunction[name]
    }

    /**
     * Mark a function as recompiled, advancing it to the next tier.
     * Counter is reset so the next tier's threshold starts fresh.
     */
    internal fun markRecompiled(name: String) {
        val tier = currentTier.getOrDefault(name, 0)
        currentTier[name] = tier + 1
        callCounts[name] = 0
    }

    /** Current call count for a function. */
    fun callCount(name: String): Int = callCounts.getOrDefault(name, 0)

    /** Current tier of a function (0 = initial, 1 = tier-1, 2 = tier-2). */
    fun currentTier(name: String): Int = currentTier.getOrDefault(name, 0)

    /** Whether a function has been recompiled at tier 1 or higher. */
    fun isRecompiled(name: String): Boolean = currentTier.getOrDefault(name, 0) >= 1

    /** Reset all counters and recompilation state. */
    fun reset() {
        callCounts.clear()
        currentTier.clear()
        moduleForFunction.clear()
    }
}
