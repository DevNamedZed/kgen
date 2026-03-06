package org.kgen.ir

/** Attributes that can be applied to function parameters to guide code generation. */
enum class ParamAttribute {
    /** Zero-extend the value to the full register width. */
    ZEROEXT,
    /** Sign-extend the value to the full register width. */
    SIGNEXT,
    /** Pass in a register (target-specific). */
    INREG,
    /** Pass by value (copy the pointee to the stack). */
    BYVAL,
    /** Pass by reference (the callee gets a pointer to the caller's copy). */
    BYREF,
    /** Hidden pointer for struct return values (the caller allocates, callee writes through the pointer). */
    SRET,
    /** The pointer does not alias any other pointer visible to the callee. */
    NOALIAS,
    /** The pointer is not captured (not stored or returned). */
    NOCAPTURE,
    /** The pointer is guaranteed non-null. */
    NONNULL,
    /** The pointer is only read through (not written). */
    READONLY,
    /** The pointer is only written through (not read). */
    WRITEONLY,
    /** The pointer is neither read nor written (used only for its address). */
    READNONE,
    /** Nested function static chain pointer (used by trampolines). */
    NEST,
    /** The parameter value is also the return value. */
    RETURNED,
}
