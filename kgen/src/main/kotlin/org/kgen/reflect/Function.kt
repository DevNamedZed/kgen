package org.kgen.reflect

/**
 * A callable, hookable, patchable function within a module.
 *
 * Represents any function — native machine code, CLR IL, JVM bytecode, or WASM.
 * Provides inspection (signature, disassembly, bytecode), calling (via FFM downcall),
 * hooking (inline, GOT/IAT, stub), and patching.
 *
 * ```java
 * var func = module.function("strlen");
 * func.name();             // "strlen"
 * func.isNative();         // true
 * func.disassemble();      // List<Instruction>
 * ```
 */
class Function internal constructor(
    private val sym: Symbol,
    private var sig: Signature? = null,
) {
    /** The function name. */
    fun name(): String = sym.name()

    /** The qualified name (parsed from the symbol name). */
    fun qualifiedName(): QualifiedName = sym.qualifiedName()

    /** The underlying symbol. */
    fun symbol(): Symbol = sym

    /** The module containing this function. */
    fun module(): Module? = sym.module()

    /** Offset within the module. */
    fun offset(): Long = sym.offset()

    /** Function size in bytes (0 if unknown). */
    fun size(): Long = sym.size()

    // -- Code classification --

    /** Whether this function contains native machine code. */
    fun isNative(): Boolean = sym.isNative() || (!isBytecode())

    /** Whether this function is managed bytecode (CLR IL, JVM bytecode, WASM). */
    fun isBytecode(): Boolean = sym.kind() == org.kgen.binary.SymbolKind.METHOD

    // -- Signature --

    /** Whether this function has known type information (signature). */
    fun hasSignature(): Boolean = sig != null

    /** The function's signature, or null if unknown. */
    fun signature(): Signature? = sig

    /** Return a copy of this function with an explicit signature attached. */
    fun withSignature(signature: Signature): Function = Function(sym, signature)

    /** The return type (from signature), or null if no signature. */
    fun returnType(): TypeRef? = sig?.returnType()

    /** The parameter types (from signature), or empty if no signature. */
    fun parameterTypes(): List<TypeRef> = sig?.parameterTypes() ?: emptyList()

    /** The parameters (from signature), or empty if no signature. */
    fun parameters(): List<SignatureParam> = sig?.parameters() ?: emptyList()

    override fun toString(): String = buildString {
        append(sym.name())
        if (sig != null) {
            append(": ")
            append(sig)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Function) return false
        return sym == other.sym
    }

    override fun hashCode(): Int = sym.hashCode()
}
