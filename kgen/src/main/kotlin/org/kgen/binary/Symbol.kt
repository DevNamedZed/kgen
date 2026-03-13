package org.kgen.binary

/**
 * A symbol in an object file — a named entity with an address, type, and visibility.
 *
 * Symbols represent functions, global variables, TLS variables, imports, and exports.
 * They are the primary mechanism for cross-module linking and dynamic binding.
 *
 * ```kotlin
 * val obj = ElfReader.toObjectFile(ElfReader.read(bytes))
 * // Find all exported functions
 * val exports = obj.symbols.filter { it.binding == SymbolBinding.GLOBAL && it.kind == SymbolKind.FUNCTION }
 * // Look up a specific symbol
 * val main = obj.symbols.firstOrNull { it.name == "main" }
 * ```
 *
 * @property name Symbol name (may be mangled — use [org.kgen.binary.mangling.Demangler] to decode).
 * @property value Virtual address or offset within the section.
 * @property size Size in bytes (0 if unknown).
 * @property section Owning section name (null for external/undefined symbols).
 * @property binding Visibility scope (LOCAL, GLOBAL, WEAK).
 * @property kind What the symbol represents (FUNCTION, DATA, TLS, etc.).
 * @property visibility Dynamic linker visibility (DEFAULT, HIDDEN, PROTECTED).
 */
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
    ACC_VOLATILE,
    ACC_TRANSIENT,

    // WASM
    WASM_EXPORT,
    WASM_IMPORT,
}
