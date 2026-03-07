package org.kgen.reflect

/**
 * A dependency of a module (shared library, DLL, assembly reference, etc.).
 *
 * ELF: DT_NEEDED entries. PE: import DLL names. Mach-O: LC_LOAD_DYLIB.
 * JVM: module-info requires. CLR: AssemblyRef table.
 */
class Dependency(
    private val name: String,
    private val version: String? = null,
    private val resolvedPath: String? = null,
) {
    fun name(): String = name
    fun version(): String? = version
    fun path(): String? = resolvedPath

    override fun toString(): String = buildString {
        append(name)
        if (version != null) append(" ($version)")
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Dependency) return false
        return name == other.name
    }

    override fun hashCode(): Int = name.hashCode()
}
