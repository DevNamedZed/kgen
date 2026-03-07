package org.kgen.ir

/** Direction of a managed/native boundary transition call. */
enum class ManagedCallDirection {
    /** Calling from managed code into native code (e.g., P/Invoke, JNI). */
    MANAGED_TO_NATIVE,

    /** Calling from native code back into managed code (e.g., reverse P/Invoke, callback). */
    NATIVE_TO_MANAGED,
}
