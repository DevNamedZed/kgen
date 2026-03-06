package org.kgen.tools

// Symbol demangling — C++, Rust, D, Swift, JVM, etc.

interface Demangler {
    fun demangle(mangledName: String): String?
    fun canDemangle(mangledName: String): Boolean
}

enum class ManglingScheme {
    ITANIUM,       // C++ (GCC, Clang) — _Z prefix
    MSVC,          // C++ (MSVC) — ? prefix
    RUST,          // Rust — _R prefix (v0) or _ZN (legacy)
    DLANG,         // D — _D prefix
    SWIFT,         // Swift — $s prefix
    JVM,           // JVM internal names (Ljava/lang/String;)
    DOTNET,        // .NET metadata tokens
    AUTO,          // auto-detect
}
