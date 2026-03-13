package org.kgen.binary.mangling

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested

class DemanglerFacadeTest {

    @Nested
    inner class DemanglerObject {

        @Test
        fun `demangles itanium symbol`() {
            assertEquals("foo()", Demangler.demangle("_Z3foov"))
        }

        @Test
        fun `demangles itanium namespaced symbol`() {
            val result = Demangler.demangle("_ZN3foo3barEv")
            assertNotNull(result)
            assertTrue(result!!.contains("foo::bar"))
        }

        @Test
        fun `demangles msvc symbol`() {
            val result = Demangler.demangle("?foo@@YAHXZ")
            assertNotNull(result)
            assertTrue(result!!.contains("foo"))
        }

        @Test
        fun `demangles rust legacy symbol`() {
            assertEquals("core::fmt::write", Demangler.demangle("_ZN4core3fmt5write17h1234567890abcdefE"))
        }

        @Test
        fun `demangles rust v0 symbol`() {
            val result = Demangler.demangle("_RNvCs1234_4core3foo")
            assertNotNull(result)
            assertTrue(result!!.contains("core"))
        }

        @Test
        fun `returns null for unmangled name`() {
            assertNull(Demangler.demangle("main"))
        }

        @Test
        fun `returns null for empty string`() {
            assertNull(Demangler.demangle(""))
        }

        @Test
        fun `returns null for plain C symbols`() {
            assertNull(Demangler.demangle("printf"))
            assertNull(Demangler.demangle("strlen"))
            assertNull(Demangler.demangle("_start"))
        }

        @Test
        fun `returns null for single character`() {
            assertNull(Demangler.demangle("x"))
        }

        @Test
        fun `returns null for numeric string`() {
            assertNull(Demangler.demangle("12345"))
        }

        @Test
        fun `handles underscore prefixed C symbol`() {
            assertNull(Demangler.demangle("_main"))
        }

        @Test
        fun `handles double underscore C symbol`() {
            assertNull(Demangler.demangle("__libc_start_main"))
        }
    }

    @Nested
    inner class DemanglerStrategyInterface {

        @Test
        fun `all implementations satisfy the interface`() {
            val strategies: List<DemanglerStrategy> = listOf(
                ItaniumDemangler(),
                MsvcDemangler(),
                RustDemangler(),
                UniversalDemangler(),
            )
            for (strategy in strategies) {
                assertFalse(strategy.canDemangle("plainSymbol"))
                assertNull(strategy.demangle("plainSymbol"))
            }
        }

        @Test
        fun `each demangler only claims its own scheme`() {
            val itanium = ItaniumDemangler()
            val msvc = MsvcDemangler()
            val rust = RustDemangler()

            assertTrue(itanium.canDemangle("_Z3foov"))
            assertFalse(msvc.canDemangle("_Z3foov"))
            assertFalse(rust.canDemangle("_Z3foov"))

            assertFalse(itanium.canDemangle("?foo@@YAHXZ"))
            assertTrue(msvc.canDemangle("?foo@@YAHXZ"))
            assertFalse(rust.canDemangle("?foo@@YAHXZ"))

            assertTrue(rust.canDemangle("_RNvCs1234_4core3foo"))
            assertFalse(itanium.canDemangle("_RNvCs1234_4core3foo"))
            assertFalse(msvc.canDemangle("_RNvCs1234_4core3foo"))
        }

        @Test
        fun `custom strategy implementation works`() {
            val custom = object : DemanglerStrategy {
                override fun demangle(mangledName: String): String? {
                    if (mangledName.startsWith("CUSTOM_")) {
                        return mangledName.removePrefix("CUSTOM_")
                    }
                    return null
                }

                override fun canDemangle(mangledName: String): Boolean {
                    return mangledName.startsWith("CUSTOM_")
                }
            }

            assertTrue(custom.canDemangle("CUSTOM_hello"))
            assertEquals("hello", custom.demangle("CUSTOM_hello"))
            assertFalse(custom.canDemangle("_Z3foov"))
            assertNull(custom.demangle("_Z3foov"))
        }
    }

    @Nested
    inner class UniversalDemanglerDetect {

        private val universal = UniversalDemangler()

        @Test
        fun `detect returns ITANIUM for _Z without rust hash`() {
            assertEquals(ManglingScheme.ITANIUM, universal.detect("_Z3foov"))
        }

        @Test
        fun `detect returns ITANIUM for __Z without rust hash`() {
            assertEquals(ManglingScheme.ITANIUM, universal.detect("__Z3foov"))
        }

        @Test
        fun `detect returns MSVC for question mark prefix`() {
            assertEquals(ManglingScheme.MSVC, universal.detect("?foo@@YAHXZ"))
        }

        @Test
        fun `detect returns RUST for _R prefix`() {
            assertEquals(ManglingScheme.RUST, universal.detect("_RNvCs1234_4core3foo"))
        }

        @Test
        fun `detect returns RUST for legacy rust with hash`() {
            assertEquals(ManglingScheme.RUST, universal.detect("_ZN4core3fmt5write17h1234567890abcdefE"))
        }

        @Test
        fun `detect returns null for plain symbol`() {
            assertNull(universal.detect("printf"))
        }

        @Test
        fun `detect returns null for empty`() {
            assertNull(universal.detect(""))
        }

        @Test
        fun `detect returns null for special characters only`() {
            assertNull(universal.detect("@#\$%"))
        }

        @Test
        fun `detect returns null for numeric only`() {
            assertNull(universal.detect("42"))
        }
    }

    @Nested
    inner class ManglingSchemeEnum {

        @Test
        fun `all expected values exist`() {
            val expected = listOf(
                ManglingScheme.ITANIUM,
                ManglingScheme.MSVC,
                ManglingScheme.RUST,
                ManglingScheme.DLANG,
                ManglingScheme.SWIFT,
                ManglingScheme.JVM,
                ManglingScheme.DOTNET,
                ManglingScheme.AUTO,
            )
            assertEquals(expected.size, ManglingScheme.entries.size)
            for (scheme in expected) {
                assertTrue(ManglingScheme.entries.contains(scheme))
            }
        }

        @Test
        fun `valueOf works for all entries`() {
            assertEquals(ManglingScheme.ITANIUM, ManglingScheme.valueOf("ITANIUM"))
            assertEquals(ManglingScheme.MSVC, ManglingScheme.valueOf("MSVC"))
            assertEquals(ManglingScheme.RUST, ManglingScheme.valueOf("RUST"))
            assertEquals(ManglingScheme.AUTO, ManglingScheme.valueOf("AUTO"))
        }
    }

    @Nested
    inner class EdgeCases {

        @Test
        fun `itanium truncated input does not crash`() {
            assertNull(ItaniumDemangler().demangle("_Z"))
        }

        @Test
        fun `itanium incomplete nested name does not crash`() {
            val result = ItaniumDemangler().demangle("_ZN")
            // Should not throw, may return null or partial
        }

        @Test
        fun `msvc truncated input does not crash`() {
            val result = MsvcDemangler().demangle("?")
            // Should not throw
        }

        @Test
        fun `msvc incomplete name does not crash`() {
            val result = MsvcDemangler().demangle("?foo")
            // Should not throw
        }

        @Test
        fun `rust v0 truncated input does not crash`() {
            assertNull(RustDemangler().demangle("_R"))
        }

        @Test
        fun `rust legacy incomplete does not crash`() {
            val result = RustDemangler().demangle("_ZN17h0000000000000000E")
            // Should not throw
        }

        @Test
        fun `very long mangled name does not crash`() {
            val longName = "_Z" + "3foo".repeat(1000) + "v"
            ItaniumDemangler().demangle(longName)
        }

        @Test
        fun `msvc very long name does not crash`() {
            val longName = "?" + "foo@".repeat(100) + "@@YAHXZ"
            MsvcDemangler().demangle(longName)
        }

        @Test
        fun `unicode in symbol name does not crash`() {
            // These should not throw exceptions; result may or may not be null
            ItaniumDemangler().demangle("_Z\u00e9")
            MsvcDemangler().demangle("?\u00e9")
        }

        @Test
        fun `null-like content does not crash`() {
            assertNull(Demangler.demangle("\u0000"))
        }

        @Test
        fun `itanium with only prefix and number but no name`() {
            val result = ItaniumDemangler().demangle("_Z99")
            assertNull(result)
        }

        @Test
        fun `itanium with zero length name`() {
            val result = ItaniumDemangler().demangle("_Z0v")
            // 0-length source name edge case
            assertNotNull(result)
        }
    }
}
