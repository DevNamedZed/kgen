package org.kgen.ir

/** Symbol linkage — controls how the linker resolves duplicate definitions. */
enum class Linkage {
    EXTERNAL, INTERNAL, PRIVATE, WEAK, WEAK_ODR,
    LINKONCE, LINKONCE_ODR, COMMON, APPENDING, AVAILABLE_EXTERNALLY,
}
