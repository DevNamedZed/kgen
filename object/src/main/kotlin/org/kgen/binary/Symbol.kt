package org.kgen.binary

data class Symbol(
    val name: String,
    val value: Long = 0,                     // address or value
    val size: Long = 0,
    val section: String? = null,             // owning section name (null = external/undefined)
    val binding: SymbolBinding = SymbolBinding.GLOBAL,
    val kind: SymbolKind = SymbolKind.FUNCTION,
    val visibility: SymbolVisibility = SymbolVisibility.DEFAULT,
    val flags: Set<SymbolFlag> = emptySet(),
    val version: String? = null,             // symbol version (ELF)
    val comdat: String? = null,              // COMDAT group
    val importModule: String? = null,        // for imports (WASM, PE DLL name)
    val importName: String? = null,          // for imports (original name if different)
    val ordinal: Int? = null,                // PE export ordinal
    val align: Int? = null,                  // alignment (for COMMON symbols)
)

enum class SymbolBinding {
    LOCAL,            // not visible outside object file
    GLOBAL,           // globally visible
    WEAK,             // weak symbol — may be overridden
    GNU_UNIQUE,       // GNU unique binding (TLS)
}

enum class SymbolKind {
    FUNCTION,         // code
    DATA,             // data object
    TLS,              // thread-local storage
    COMMON,           // common / uninitialized
    SECTION,          // section symbol
    FILE,             // file symbol
    IFUNC,            // GNU indirect function (resolver)
    UNDEFINED,        // external reference
    ABSOLUTE,         // absolute value (not in any section)

    // High-level (JVM/WASM)
    CLASS,
    INTERFACE,
    METHOD,
    FIELD,
    MODULE,
}

enum class SymbolVisibility {
    DEFAULT,          // use binding to determine visibility
    HIDDEN,           // not visible in dynamic symbol table
    PROTECTED,        // visible but not preemptible
    INTERNAL,         // processor-specific hidden
}

enum class SymbolFlag {
    // General
    UNDEFINED,        // references an external symbol
    EXPORTED,         // exported to dynamic linker
    IMPORTED,         // imported from another module
    THUMB,            // ARM Thumb function

    // PE/COFF
    DLL_IMPORT,       // __declspec(dllimport)
    DLL_EXPORT,       // __declspec(dllexport)
    SEH_HANDLER,      // structured exception handler

    // Mach-O
    NO_DEAD_STRIP,    // don't dead-strip
    WEAK_DEF,         // weak definition
    WEAK_REF,         // weak reference
    PRIVATE_EXTERN,   // private external
    LAZY_BIND,        // lazy binding
    RESOLVER,         // symbol is a resolver function

    // JVM
    SYNTHETIC,        // compiler-generated
    BRIDGE,           // bridge method
    VARARGS,          // varargs
    ENUM,             // enum constant
    ANNOTATION,       // annotation type
    DEPRECATED,       // deprecated
    ACC_FINAL,
    ACC_ABSTRACT,
    ACC_NATIVE,
    ACC_SYNCHRONIZED,
    ACC_STATIC,
    ACC_PUBLIC,
    ACC_PRIVATE,
    ACC_PROTECTED,

    // WASM
    WASM_EXPORT,
    WASM_IMPORT,
}
