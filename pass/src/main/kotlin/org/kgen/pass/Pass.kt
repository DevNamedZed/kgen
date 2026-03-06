package org.kgen.pass

import org.kgen.ir.Module

fun interface ModulePass {
    fun run(module: Module): Module
}

class PassPipeline(private val passes: MutableList<ModulePass> = mutableListOf()) {
    fun add(pass: ModulePass): PassPipeline {
        passes += pass
        return this
    }

    fun execute(module: Module): Module =
        passes.fold(module) { m, pass -> pass.run(m) }
}
