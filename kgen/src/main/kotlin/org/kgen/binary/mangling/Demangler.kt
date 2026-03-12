package org.kgen.binary.mangling

import org.kgen.binary.mangling.UniversalDemangler

/**
 * Demangles C++ and other mangled symbol names.
 *
 * Delegates to [UniversalDemangler] which supports Itanium ABI (`_Z...`),
 * MSVC (`?...`), and Rust (`_R...`) mangling schemes.
 */
object Demangler {

    private val demangler = UniversalDemangler()

    /**
     * Demangle a symbol name, or return null if it's not a recognized mangled name.
     */
    @JvmStatic
    fun demangle(name: String): String? = demangler.demangle(name)
}
