package org.kgen.pass

import org.kgen.ir.*

/**
 * Incremental compilation support — tracks which functions have changed
 * between compilations and only recompiles affected parts.
 *
 * Uses content-based hashing to detect changes: each function's IR is hashed,
 * and only functions whose hash has changed (or whose dependencies changed)
 * are recompiled.
 *
 * ```java
 * var cache = new IncrementalCompilation.Cache();
 *
 * // First compile — everything is new
 * var result = IncrementalCompilation.compile(moduleV1, cache);
 * // result.changed = ["add", "sub", "main"]
 *
 * // Second compile — only "main" changed
 * var result2 = IncrementalCompilation.compile(moduleV2, cache);
 * // result2.changed = ["main"]  (add and sub unchanged)
 * ```
 */
class IncrementalCompilation {

    /**
     * Result of incremental analysis: which functions changed and which are unchanged.
     */
    data class IncrementalResult(
        /** Functions that changed and need recompilation. */
        val changed: Set<String>,
        /** Functions that are unchanged from the previous compilation. */
        val unchanged: Set<String>,
        /** Functions that are new (not in the previous compilation). */
        val added: Set<String>,
        /** Functions that were removed (in previous but not current). */
        val removed: Set<String>,
    ) {
        /** Whether any functions changed. */
        fun hasChanges(): Boolean = changed.isNotEmpty() || added.isNotEmpty() || removed.isNotEmpty()

        /** All functions that need recompilation (changed + added). */
        fun needsRecompilation(): Set<String> = changed + added
    }

    /**
     * Cache storing function hashes from the previous compilation.
     */
    class Cache {
        private val hashes = mutableMapOf<String, Long>()
        private val dependencies = mutableMapOf<String, Set<String>>()

        /** Store the hash for a function. */
        fun put(functionName: String, hash: Long) { hashes[functionName] = hash }

        /** Get the cached hash for a function, or null if not cached. */
        fun get(functionName: String): Long? = hashes[functionName]

        /** Store the dependency set for a function (functions it calls). */
        fun putDependencies(functionName: String, deps: Set<String>) { dependencies[functionName] = deps }

        /** Get the cached dependency set for a function. */
        fun getDependencies(functionName: String): Set<String>? = dependencies[functionName]

        /** All cached function names. */
        fun cachedFunctions(): Set<String> = hashes.keys.toSet()

        /** Remove a function from the cache. */
        fun remove(functionName: String) {
            hashes.remove(functionName)
            dependencies.remove(functionName)
        }

        /** Clear the entire cache. */
        fun clear() {
            hashes.clear()
            dependencies.clear()
        }

        /** Number of cached functions. */
        fun size(): Int = hashes.size
    }

    companion object {
        /**
         * Analyze a module against a cache to determine which functions changed.
         * Updates the cache with current hashes.
         */
        @JvmStatic
        fun analyze(module: Module, cache: Cache): IncrementalResult {
            val currentHashes = mutableMapOf<String, Long>()
            val currentDeps = mutableMapOf<String, Set<String>>()

            for (fn in module.functions) {
                currentHashes[fn.name] = hashFunction(fn)
                currentDeps[fn.name] = extractDependencies(fn)
            }

            val previousNames = cache.cachedFunctions()
            val currentNames = currentHashes.keys

            val added = currentNames - previousNames
            val removed = previousNames - currentNames
            val common = currentNames.intersect(previousNames)

            // Direct changes: hash differs
            val directlyChanged = common.filter { name ->
                currentHashes[name] != cache.get(name)
            }.toMutableSet()

            // Transitive changes: a dependency changed
            val transitivelyChanged = mutableSetOf<String>()
            for (name in common) {
                if (name in directlyChanged) continue
                val deps = currentDeps[name] ?: emptySet()
                if (deps.any { it in directlyChanged || it in added }) {
                    transitivelyChanged.add(name)
                }
            }

            val allChanged = directlyChanged + transitivelyChanged
            val unchanged = common - allChanged

            // Update cache
            for ((name, hash) in currentHashes) {
                cache.put(name, hash)
                cache.putDependencies(name, currentDeps[name] ?: emptySet())
            }
            for (name in removed) {
                cache.remove(name)
            }

            return IncrementalResult(
                changed = allChanged,
                unchanged = unchanged,
                added = added,
                removed = removed,
            )
        }

        /**
         * Compute a content-based hash for a function.
         * Changes to the function body, parameters, or return type change the hash.
         */
        @JvmStatic
        fun hashFunction(fn: IrFunction): Long {
            var hash = fn.name.hashCode().toLong()
            hash = hash * 31 + fn.returnType.hashCode()
            hash = hash * 31 + fn.params.hashCode()
            hash = hash * 31 + fn.linkage.hashCode()
            hash = hash * 31 + fn.attributes.hashCode()
            for (block in fn.blocks) {
                hash = hash * 31 + block.label.hashCode()
                for (inst in block.instructions) {
                    hash = hash * 31 + inst.hashCode()
                }
            }
            return hash
        }

        /**
         * Extract the set of functions called by this function.
         */
        @JvmStatic
        fun extractDependencies(fn: IrFunction): Set<String> {
            val deps = mutableSetOf<String>()
            for (block in fn.blocks) {
                for (inst in block.instructions) {
                    when (inst) {
                        is Instruction.Call -> deps.add(inst.function.name)
                        is Instruction.VirtualCall -> deps.add("${inst.className}.${inst.methodName}")
                        is Instruction.InterfaceCall -> deps.add("${inst.interfaceName}.${inst.methodName}")
                        is Instruction.SpecialCall -> deps.add("${inst.className}.${inst.methodName}")
                        else -> {}
                    }
                }
            }
            return deps
        }
    }
}
