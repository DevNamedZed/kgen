package org.kgen.reflect

/**
 * Demangles C++ and other mangled symbol names.
 *
 * Supports Itanium ABI (`_Z...`) and MSVC (`?...`) mangling schemes.
 */
object Demangler {

    /**
     * Demangle a symbol name, or return null if it's not a recognized mangled name.
     */
    @JvmStatic
    fun demangle(name: String): String? {
        if (name.startsWith("_Z")) return demangleItanium(name)
        if (name.startsWith("?")) return demangleMsvc(name)
        return null
    }

    private fun demangleItanium(name: String): String? {
        // TODO: full Itanium ABI demangling
        return null
    }

    private fun demangleMsvc(name: String): String? {
        // TODO: full MSVC demangling
        return null
    }
}
