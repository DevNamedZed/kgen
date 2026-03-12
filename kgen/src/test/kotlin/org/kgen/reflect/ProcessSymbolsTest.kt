package org.kgen.reflect

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.kgen.reflect.process.ProcessSymbols

@EnabledOnOs(OS.LINUX, OS.WINDOWS)
class ProcessSymbolsTest {

    @Test
    fun `lookup returns non-null for common libc symbol`() {
        // strlen is available on all platforms via default linker namespace
        val addr = ProcessSymbols.lookup("strlen")
        assertNotNull(addr, "Expected to find strlen")
        assertTrue(addr!! > 0, "Address should be positive")
    }

    @Test
    fun `lookup returns null for nonexistent symbol`() {
        val addr = ProcessSymbols.lookup("__kgen_absolutely_does_not_exist_12345__")
        assertNull(addr)
    }

    @Test
    fun `lookup memcpy`() {
        val addr = ProcessSymbols.lookup("memcpy")
        assertNotNull(addr, "Expected to find memcpy")
    }

    @Test
    fun `lookup malloc`() {
        val addr = ProcessSymbols.lookup("malloc")
        assertNotNull(addr, "Expected to find malloc")
    }

    @Test
    fun `lookup free`() {
        val addr = ProcessSymbols.lookup("free")
        assertNotNull(addr, "Expected to find free")
    }

    @Test
    fun `multiple lookups return consistent addresses`() {
        val addr1 = ProcessSymbols.lookup("strlen")
        val addr2 = ProcessSymbols.lookup("strlen")
        assertEquals(addr1, addr2, "Same symbol should resolve to same address")
    }

    @EnabledOnOs(OS.LINUX)
    @Test
    fun `dlsymLookup finds libc function`() {
        val addr = ProcessSymbols.dlsymLookup("strlen")
        assertNotNull(addr)
        assertTrue(addr!! > 0)
    }

    @EnabledOnOs(OS.LINUX)
    @Test
    fun `dlsymLookup returns null for missing symbol`() {
        val addr = ProcessSymbols.dlsymLookup("__kgen_nonexistent__")
        assertNull(addr)
    }

    @Test
    fun `lookup different symbols return different addresses`() {
        val strlen = ProcessSymbols.lookup("strlen")
        val memcpy = ProcessSymbols.lookup("memcpy")
        assertNotNull(strlen)
        assertNotNull(memcpy)
        assertNotEquals(strlen, memcpy, "Different functions should have different addresses")
    }
}
