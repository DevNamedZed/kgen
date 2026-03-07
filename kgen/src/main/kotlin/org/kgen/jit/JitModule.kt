package org.kgen.jit

import org.kgen.reflect.NativeMemory

/**
 * A compiled and loaded module in the JIT engine.
 *
 * Owns the executable memory containing the compiled code and provides
 * symbol lookup within the module.
 */
class JitModule internal constructor(
    val name: String,
    internal val memory: NativeMemory,
    internal val symbols: Map<String, JitSymbol>,
) : AutoCloseable {

    fun lookup(name: String): JitSymbol? = symbols[name]

    fun symbolNames(): Set<String> = symbols.keys

    override fun close() {
        memory.close()
    }

    override fun toString(): String = "JitModule($name, ${symbols.size} symbols)"
}
