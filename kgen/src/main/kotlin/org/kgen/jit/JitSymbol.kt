package org.kgen.jit

/**
 * A resolved symbol in the JIT engine — an address in executable memory plus metadata.
 *
 * ```java
 * JitSymbol sym = jit.lookup("add");
 * long addr = sym.address();
 * ```
 */
data class JitSymbol(
    val name: String,
    val address: Long,
    val size: Long = 0,
    val source: SymbolSource = SymbolSource.JIT,
) {
    enum class SymbolSource {
        JIT,
        HOST,
        LIBRARY,
        STUB,
    }
}
