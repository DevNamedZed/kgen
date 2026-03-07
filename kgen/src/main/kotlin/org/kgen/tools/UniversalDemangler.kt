package org.kgen.tools

/**
 * Auto-detecting demangler that tries all known schemes.
 *
 * ```kotlin
 * val demangler = UniversalDemangler()
 * println(demangler.demangle("_Z3foov"))                   // foo()
 * println(demangler.demangle("?foo@@YAHXZ"))               // int foo(void)
 * println(demangler.demangle("_ZN4core3fmt5write17h...E"))  // core::fmt::write
 * ```
 */
class UniversalDemangler : Demangler {

    private val demanglers = listOf(
        RustDemangler(),
        ItaniumDemangler(),
        MsvcDemangler(),
    )

    override fun canDemangle(mangledName: String): Boolean =
        demanglers.any { it.canDemangle(mangledName) }

    override fun demangle(mangledName: String): String? {
        for (d in demanglers) {
            if (d.canDemangle(mangledName)) {
                val result = d.demangle(mangledName)
                if (result != null) return result
            }
        }
        return null
    }

    fun detect(mangledName: String): ManglingScheme? {
        return when {
            mangledName.startsWith("_R") -> ManglingScheme.RUST
            RustDemangler().canDemangle(mangledName) && !mangledName.startsWith("_Z") -> ManglingScheme.RUST
            mangledName.startsWith("?") -> ManglingScheme.MSVC
            mangledName.startsWith("_Z") || mangledName.startsWith("__Z") -> {
                if (RustDemangler().canDemangle(mangledName)) ManglingScheme.RUST
                else ManglingScheme.ITANIUM
            }
            else -> null
        }
    }
}
