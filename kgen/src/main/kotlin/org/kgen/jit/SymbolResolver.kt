package org.kgen.jit

import org.kgen.reflect.process.ProcessSymbols

/**
 * Resolves symbol names to addresses. Multiple resolvers can be chained
 * in a [JitEngine] to search JIT'd modules, the host process, and loaded libraries.
 *
 * ```java
 * jit.addResolver(SymbolResolver.host());
 * jit.addResolver(SymbolResolver.library(ProcessSymbols.loadLibrary("libm.so.6")));
 * ```
 */
fun interface SymbolResolver {
    fun resolve(name: String): Long?

    companion object {
        @JvmStatic
        fun host(): SymbolResolver = HostSymbolResolver

        @JvmStatic
        fun library(lib: ProcessSymbols.Library): SymbolResolver =
            LibrarySymbolResolver(lib)

        @JvmStatic
        fun map(symbols: Map<String, Long>): SymbolResolver =
            MapSymbolResolver(symbols)
    }
}

private object HostSymbolResolver : SymbolResolver {
    override fun resolve(name: String): Long? {
        return ProcessSymbols.lookup(name) ?: ProcessSymbols.dlsymLookup(name)
    }
}

private class LibrarySymbolResolver(private val lib: ProcessSymbols.Library) : SymbolResolver {
    override fun resolve(name: String): Long? = lib.find(name)
}

private class MapSymbolResolver(private val symbols: Map<String, Long>) : SymbolResolver {
    override fun resolve(name: String): Long? = symbols[name]
}
