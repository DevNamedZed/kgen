package org.kgen.ir

/** Function-level attributes. */
enum class FnAttribute {
    NOUNWIND, NORETURN, NOINLINE, ALWAYSINLINE, OPTNONE, OPTSIZE,
    READONLY, READNONE, WRITEONLY, ARGMEMONLY, WILLRETURN,
    SPECULATABLE, CONVERGENT, COLD, HOT,
    NAKED, NOREDZONE, NOIMPLICITFLOAT,
    SANITIZE_ADDRESS, SANITIZE_MEMORY, SANITIZE_THREAD,
    NO_STACK_PROTECTOR, STACK_PROTECT, STACK_PROTECT_STRONG,
    /** Compile to native machine code (mixed-mode). */
    NATIVE,
    /** Compile to managed bytecode (mixed-mode). */
    MANAGED,
}
