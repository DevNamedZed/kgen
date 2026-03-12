package org.kgen.runtime.exec

/** Simplified type classification for ABI purposes. */
enum class ArgType {
    INTEGER,
    FLOAT,
    POINTER,
    STRUCT_SMALL,
    STRUCT_LARGE,
}
