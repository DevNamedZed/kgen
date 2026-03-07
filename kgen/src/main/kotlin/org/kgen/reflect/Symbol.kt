package org.kgen.reflect

import org.kgen.binary.Section
import org.kgen.binary.SymbolBinding
import org.kgen.binary.SymbolFlag
import org.kgen.binary.SymbolKind
import org.kgen.binary.SymbolVisibility

/**
 * A rich, navigable symbol within a module.
 *
 * Provides classification, introspection, and navigation to related objects
 * (function, section, module). For loaded modules, also provides live
 * memory access at the symbol's address.
 *
 * ```java
 * var sym = module.symbol("strlen");
 * sym.name();          // "strlen"
 * sym.isFunction();    // true
 * sym.isExported();    // true
 * sym.function();      // Function
 * ```
 */
class Symbol internal constructor(
    private val raw: org.kgen.binary.Symbol,
    private val ownerModule: Module?,
) {
    /** The symbol name as it appears in the binary. */
    fun name(): String = raw.name

    /** The qualified name (parsed from the raw name). */
    fun qualifiedName(): QualifiedName = QualifiedName.parse(raw.name)

    /** The demangled name for C++ symbols, or null if not mangled. */
    fun demangledName(): String? {
        if (!isMangled()) return null
        return Demangler.demangle(raw.name)
    }

    /** Whether this symbol has a mangled (C++ or similar) name. */
    fun isMangled(): Boolean = raw.name.startsWith("_Z") || raw.name.startsWith("?")

    /** The module containing this symbol. */
    fun module(): Module? = ownerModule

    /** Offset within the module (section-relative or file-relative). */
    fun offset(): Long = raw.value

    /** Symbol size in bytes (0 if unknown). */
    fun size(): Long = raw.size

    // -- Classification --

    fun kind(): SymbolKind = raw.kind
    fun binding(): SymbolBinding = raw.binding
    fun visibility(): SymbolVisibility = raw.visibility
    fun flags(): Set<SymbolFlag> = raw.flags

    fun isFunction(): Boolean = raw.kind == SymbolKind.FUNCTION || raw.kind == SymbolKind.METHOD
    fun isData(): Boolean = raw.kind == SymbolKind.DATA
    fun isTls(): Boolean = raw.kind == SymbolKind.TLS
    fun isUndefined(): Boolean = raw.kind == SymbolKind.UNDEFINED || SymbolFlag.UNDEFINED in raw.flags
    fun isAbsolute(): Boolean = raw.kind == SymbolKind.ABSOLUTE
    fun isCommon(): Boolean = raw.kind == SymbolKind.COMMON

    fun isExported(): Boolean = SymbolFlag.EXPORTED in raw.flags
            || SymbolFlag.DLL_EXPORT in raw.flags
            || SymbolFlag.WASM_EXPORT in raw.flags
            || (raw.binding == SymbolBinding.GLOBAL && raw.visibility == SymbolVisibility.DEFAULT)

    fun isImported(): Boolean = SymbolFlag.IMPORTED in raw.flags
            || SymbolFlag.DLL_IMPORT in raw.flags
            || SymbolFlag.WASM_IMPORT in raw.flags

    fun isWeak(): Boolean = raw.binding == SymbolBinding.WEAK
    fun isLocal(): Boolean = raw.binding == SymbolBinding.LOCAL
    fun isGlobal(): Boolean = raw.binding == SymbolBinding.GLOBAL

    fun isPublic(): Boolean = SymbolFlag.ACC_PUBLIC in raw.flags
    fun isPrivate(): Boolean = SymbolFlag.ACC_PRIVATE in raw.flags
    fun isProtected(): Boolean = SymbolFlag.ACC_PROTECTED in raw.flags
    fun isStatic(): Boolean = SymbolFlag.ACC_STATIC in raw.flags
    fun isFinal(): Boolean = SymbolFlag.ACC_FINAL in raw.flags
    fun isAbstract(): Boolean = SymbolFlag.ACC_ABSTRACT in raw.flags
    fun isNative(): Boolean = SymbolFlag.ACC_NATIVE in raw.flags
    fun isSynthetic(): Boolean = SymbolFlag.SYNTHETIC in raw.flags
    fun isDeprecated(): Boolean = SymbolFlag.DEPRECATED in raw.flags

    // -- Section --

    fun sectionName(): String? = raw.section

    // -- Import/Export details --

    fun importModule(): String? = raw.importModule
    fun importName(): String? = raw.importName
    fun ordinal(): Int? = raw.ordinal

    // -- Navigate to function --

    /** Get the Function for this symbol, or null if it's not a function symbol. */
    fun function(): Function? {
        if (!isFunction()) return null
        return Function(this)
    }

    /** Access the underlying binary-level symbol data. */
    fun raw(): org.kgen.binary.Symbol = raw

    override fun toString(): String = buildString {
        append(raw.name)
        if (raw.kind != SymbolKind.UNDEFINED) {
            append(" [${raw.kind.name.lowercase()}")
            if (isExported()) append(", exported")
            if (isImported()) append(", imported")
            append("]")
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Symbol) return false
        return raw == other.raw && ownerModule === other.ownerModule
    }

    override fun hashCode(): Int {
        var result = raw.hashCode()
        result = 31 * result + (ownerModule?.hashCode() ?: 0)
        return result
    }
}
