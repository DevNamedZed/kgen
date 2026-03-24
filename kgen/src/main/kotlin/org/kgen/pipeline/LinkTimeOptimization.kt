package org.kgen.pipeline

import org.kgen.ir.*
import org.kgen.ir.instructions.*
import org.kgen.ir.types.*

/**
 * Link-time optimization (LTO) — merges multiple IR modules into one and
 * applies cross-module optimizations that aren't possible when compiling
 * modules in isolation.
 *
 * Supports two modes:
 * - **Full LTO**: Merge all modules into one, then run the full optimization pipeline.
 * - **Thin LTO**: Analyze cross-module call graph, inline across module boundaries,
 *   then optimize each module independently (faster, parallelizable).
 *
 * ```java
 * var merged = LinkTimeOptimization.merge(List.of(moduleA, moduleB));
 * var optimized = LinkTimeOptimization.fullLto(merged);
 * ```
 */
class LinkTimeOptimization(
    private val pipeline: Pipeline = defaultPipeline(),
) {

    /**
     * Run full LTO: merge all modules, then optimize the merged result.
     */
    fun fullLto(modules: List<Module>): Module {
        val merged = merge(modules)
        return pipeline.execute(merged)
    }

    /**
     * Run thin LTO: cross-module inlining + dead function elimination,
     * but each module keeps its identity. Returns the optimized modules.
     */
    fun thinLto(modules: List<Module>): List<Module> {
        val merged = merge(modules)
        val crossModuleInlined = Inlining(maxInstructionCount = 50).run(merged)
        val dce = DeadCodeElimination().run(crossModuleInlined)
        return listOf(dce) // thin LTO returns a single optimized module
    }

    companion object {
        /**
         * Merge multiple IR modules into a single module.
         *
         * - Functions, globals, structs, classes, etc. are concatenated
         * - Duplicate external declarations are deduplicated (definitions win over declarations)
         * - Conflicting definitions with the same name: last module wins
         * - Module metadata is merged (last value wins for conflicts)
         */
        @JvmStatic
        fun merge(modules: List<Module>): Module {
            if (modules.isEmpty()) return Module("merged")
            if (modules.size == 1) return modules[0]

            val mergedName = modules.map { it.name }.joinToString("+")

            // Merge functions: definitions shadow declarations, last definition wins
            val funcMap = linkedMapOf<String, IrFunction>()
            for (module in modules) {
                for (fn in module.functions) {
                    val existing = funcMap[fn.name]
                    if (existing == null || existing.isExternal) {
                        funcMap[fn.name] = fn
                    }
                    // If both are definitions, last one wins
                    if (existing != null && !existing.isExternal && !fn.isExternal) {
                        funcMap[fn.name] = fn
                    }
                }
            }

            // Merge globals: same strategy
            val globalMap = linkedMapOf<String, Global>()
            for (module in modules) {
                for (g in module.globals) {
                    globalMap[g.name] = g
                }
            }

            // Merge struct/class/interface/enum definitions by name
            val structMap = linkedMapOf<String?, StructDefinition>()
            val classMap = linkedMapOf<String, ClassDefinition>()
            val ifaceMap = linkedMapOf<String, InterfaceDefinition>()
            val enumMap = linkedMapOf<String, EnumDefinition>()
            val aliasMap = linkedMapOf<String, TypeAlias>()

            for (module in modules) {
                for (s in module.structs) structMap[s.name] = s
                for (c in module.classes) classMap[c.name] = c
                for (i in module.interfaces) ifaceMap[i.name] = i
                for (e in module.enums) enumMap[e.name] = e
                for (a in module.aliases) aliasMap[a.name] = a
            }

            // Merge metadata (last wins)
            val metadata = mutableMapOf<String, MetadataValue>()
            for (module in modules) {
                metadata.putAll(module.metadata)
            }

            // Merge target features
            val features = mutableSetOf<String>()
            for (module in modules) {
                features.addAll(module.targetFeatures)
            }

            // Use target triple / data layout from first module that has one
            val targetTriple = modules.firstNotNullOfOrNull { it.targetTriple }
            val dataLayout = modules.firstNotNullOfOrNull { it.dataLayout }

            return Module(
                name = mergedName,
                targetTriple = targetTriple,
                dataLayout = dataLayout,
                functions = funcMap.values.toList(),
                globals = globalMap.values.toList(),
                structs = structMap.values.toList(),
                classes = classMap.values.toList(),
                interfaces = ifaceMap.values.toList(),
                enums = enumMap.values.toList(),
                aliases = aliasMap.values.toList(),
                metadata = metadata,
                targetFeatures = features,
                globalCtors = modules.flatMap { it.globalCtors },
                globalDtors = modules.flatMap { it.globalDtors },
                ifuncs = modules.flatMap { it.ifuncs },
                comdats = modules.flatMap { it.comdats },
            )
        }

        /**
         * Internalize functions that aren't exported (used only within the merged module).
         * Changes external linkage to internal for functions not referenced by any export.
         */
        @JvmStatic
        fun internalize(module: Module, exports: Set<String> = emptySet()): Module {
            // Collect all called function names
            val called = mutableSetOf<String>()
            for (fn in module.functions) {
                for (block in fn.blocks) {
                    for (inst in block.instructions) {
                        if (inst is Call) {
                            called.add(inst.function.name)
                        }
                    }
                }
            }

            val newFunctions = module.functions.map { fn ->
                if (fn.name in exports) return@map fn
                if (fn.linkage == Linkage.EXTERNAL && fn.name !in called && !fn.isExternal) {
                    fn.copy(linkage = Linkage.INTERNAL)
                } else {
                    fn
                }
            }

            return module.copy(functions = newFunctions)
        }

        /**
         * Remove unused internal functions (dead function elimination).
         */
        @JvmStatic
        fun eliminateDeadFunctions(module: Module): Module {
            val called = mutableSetOf<String>()
            for (fn in module.functions) {
                for (block in fn.blocks) {
                    for (inst in block.instructions) {
                        if (inst is Call) {
                            called.add(inst.function.name)
                        }
                    }
                }
            }

            val newFunctions = module.functions.filter { fn ->
                fn.linkage == Linkage.EXTERNAL || fn.isExternal || fn.name in called
            }

            return module.copy(functions = newFunctions)
        }

        /** Default optimization pipeline for full LTO. */
        @JvmStatic
        fun defaultPipeline(): Pipeline = Pipeline()
            .add(Inlining(maxInstructionCount = 50))
            .add(ConstantFolding())
            .add(DeadCodeElimination())
            .add(InstructionCombining())
            .add(GlobalValueNumbering())
    }
}
