package org.kgen.jit.runtime

/**
 * Describes how function arguments and return values are passed at the ABI level.
 * Used by the code generator to emit correct call sequences and by the JIT engine
 * to set up calls from the host.
 *
 * ```java
 * CallAbi abi = CallAbi.systemV();
 * ArgLocation loc = abi.classifyArg(0, Type.I64);  // → Register(RDI)
 * ArgLocation ret = abi.classifyReturn(Type.I64);    // → Register(RAX)
 * ```
 */
interface CallAbi {
    /** ABI name (e.g., "sysv", "win64"). */
    val name: String

    /** Classify where argument at the given index should be passed. */
    fun classifyArg(index: Int, type: ArgType): ArgLocation

    /** Classify where the return value should be passed. */
    fun classifyReturn(type: ArgType): ArgLocation

    /** Size of the shadow space / red zone in bytes. */
    fun shadowSpace(): Int

    /** Registers that are caller-saved (volatile). */
    fun volatileRegisters(): List<Int>

    /** Registers that are callee-saved (non-volatile). */
    fun preservedRegisters(): List<Int>

    /** Stack alignment requirement in bytes. */
    fun stackAlignment(): Int
}

/** Simplified type classification for ABI purposes. */
enum class ArgType {
    INTEGER,
    FLOAT,
    POINTER,
    STRUCT_SMALL,
    STRUCT_LARGE,
}

/** Where an argument or return value is passed. */
sealed interface ArgLocation {
    data class Register(val registerIndex: Int) : ArgLocation
    data class FloatRegister(val registerIndex: Int) : ArgLocation
    data class Stack(val offset: Int) : ArgLocation
    data class Indirect(val registerIndex: Int) : ArgLocation
}
