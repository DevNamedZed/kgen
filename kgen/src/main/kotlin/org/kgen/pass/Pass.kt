package org.kgen.pass

import org.kgen.ir.Module
import org.kgen.ir.PipelinePhase

fun interface ModulePass {
    fun run(module: Module): Module
}

/**
 * A [ModulePass] that declares which pipeline phase transition it performs.
 *
 * Passes that implement this interface indicate that after they run, the module
 * should satisfy the invariants of the [targetPhase]. This is purely descriptive —
 * the pipeline does not enforce phase transitions automatically.
 *
 * Post-phase validators can check these invariants to catch ordering errors early.
 */
interface PhasedPass : ModulePass {
    val targetPhase: PipelinePhase
}

class PassPipeline(private val passes: MutableList<ModulePass> = mutableListOf()) {
    fun add(pass: ModulePass): PassPipeline {
        passes += pass
        return this
    }

    fun execute(module: Module): Module =
        passes.fold(module) { m, pass -> pass.run(m) }
}
