package org.kgen.reflect.process

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class ProcessSymbolsTest {

    @Nested
    inner class Lookup {
        @Test
        fun nonExistentSymbolReturnsNull() {
            val addr = ProcessSymbols.lookup("__definitely_not_a_symbol_zxy_99__")
            assertNull(addr)
        }

        @Test
        fun foundSymbolHasNonZeroAddress() {
            val addr = ProcessSymbols.lookup("strlen")
            if (addr != null) {
                assertTrue(addr != 0L)
            }
        }
    }

    @Nested
    inner class DlsymLookup {
        @Test
        fun nonExistentSymbolReturnsNull() {
            val addr = ProcessSymbols.dlsymLookup("__definitely_not_a_symbol_zxy_99__")
            assertNull(addr)
        }

        @Test
        fun foundSymbolHasNonZeroAddress() {
            val addr = ProcessSymbols.dlsymLookup("strlen")
            if (addr != null) {
                assertTrue(addr != 0L)
            }
        }
    }

    @Nested
    inner class LoadLibrary {
        @Test
        fun loadingNonExistentLibraryThrows() {
            assertThrows(RuntimeException::class.java) {
                ProcessSymbols.loadLibrary("/nonexistent/lib.so")
            }
        }
    }

    @Nested
    inner class LibraryInterface {
        @Test
        fun requireThrowsForMissingSymbol() {
            val lib = object : ProcessSymbols.Library {
                override val name: String = "test"
                override fun find(name: String): Long? = null
                override fun close() {}
            }
            assertThrows(IllegalArgumentException::class.java) {
                lib.require("missing_symbol")
            }
        }

        @Test
        fun requireReturnsAddressForFoundSymbol() {
            val lib = object : ProcessSymbols.Library {
                override val name: String = "test"
                override fun find(name: String): Long? = 0x1234L
                override fun close() {}
            }
            assertEquals(0x1234L, lib.require("anything"))
        }
    }
}
