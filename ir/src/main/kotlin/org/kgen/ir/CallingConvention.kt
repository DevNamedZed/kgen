package org.kgen.ir

/** Function calling conventions. */
enum class CallingConvention {
    C, FAST, COLD, TAIL, SWIFT, CXX_FAST_TLS,
    WIN64, SYSV64, AAPCS, AAPCS_VFP, WASM, GHC, HHVM,
}
