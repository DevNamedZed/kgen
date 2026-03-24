package org.wark

/**
 * Import bindings for instantiating a WASM module. Maps (module, name) pairs
 * to host functions, memories, tables, and globals.
 *
 * ```java
 * var imports = WarkImports.builder()
 *     .function("env", "print", (instance, args) -> {
 *         System.out.println(instance.memory().readUtf8(args[0].intValue()));
 *         return new long[0];
 *     })
 *     .memory("env", "memory", WarkMemory.create(1, 256))
 *     .build();
 * ```
 */
class WarkImports private constructor(
    private val functions: Map<ImportKey, HostFunction>,
    private val memories: Map<ImportKey, WarkMemory>,
    private val globals: Map<ImportKey, WarkGlobal>,
) {

    fun resolveFunction(module: String, name: String): HostFunction? {
        return functions[ImportKey(module, name)]
    }

    fun resolveMemory(module: String, name: String): WarkMemory? {
        return memories[ImportKey(module, name)]
    }

    fun resolveGlobal(module: String, name: String): WarkGlobal? {
        return globals[ImportKey(module, name)]
    }

    fun functionCount(): Int = functions.size
    fun memoryCount(): Int = memories.size
    fun globalCount(): Int = globals.size

    class Builder {
        private val functions = mutableMapOf<ImportKey, HostFunction>()
        private val memories = mutableMapOf<ImportKey, WarkMemory>()
        private val globals = mutableMapOf<ImportKey, WarkGlobal>()

        fun function(module: String, name: String, function: HostFunction): Builder {
            functions[ImportKey(module, name)] = function
            return this
        }

        fun memory(module: String, name: String, memory: WarkMemory): Builder {
            memories[ImportKey(module, name)] = memory
            return this
        }

        fun global(module: String, name: String, global: WarkGlobal): Builder {
            globals[ImportKey(module, name)] = global
            return this
        }

        fun build(): WarkImports = WarkImports(functions.toMap(), memories.toMap(), globals.toMap())
    }

    companion object {
        @JvmStatic
        fun builder(): Builder = Builder()

        @JvmStatic
        fun empty(): WarkImports = WarkImports(emptyMap(), emptyMap(), emptyMap())
    }
}

data class ImportKey(val module: String, val name: String)
