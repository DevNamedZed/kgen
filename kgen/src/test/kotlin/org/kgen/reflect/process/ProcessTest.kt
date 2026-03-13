package org.kgen.reflect.process

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class ProcessTest {

    @Nested
    inner class CurrentProcess {
        @Test
        fun pidIsPositive() {
            val proc = Process.current()
            assertTrue(proc.pid() > 0)
        }

        @Test
        fun archIsNotNull() {
            val proc = Process.current()
            assertNotNull(proc.arch())
        }

        @Test
        fun isCurrentReturnsTrue() {
            val proc = Process.current()
            assertTrue(proc.isCurrent())
        }

        @Test
        fun sameInstanceReturnedEachTime() {
            val p1 = Process.current()
            val p2 = Process.current()
            assertSame(p1, p2)
        }

        @Test
        fun nameIsNotEmpty() {
            val proc = Process.current()
            assertTrue(proc.name().isNotEmpty())
        }

        @Test
        fun hasJvmReturnsTrue() {
            val proc = Process.current()
            assertTrue(proc.hasJvm())
        }

        @Test
        fun jvmVersionIsNotNull() {
            val proc = Process.current()
            val version = proc.jvmVersion()
            assertNotNull(version)
            assertTrue(version!!.isNotEmpty())
        }

        @Test
        fun toStringContainsPid() {
            val proc = Process.current()
            val str = proc.toString()
            assertTrue(str.contains(proc.pid().toString()))
        }

        @Test
        fun toStringContainsProcessPrefix() {
            val proc = Process.current()
            val str = proc.toString()
            assertTrue(str.startsWith("Process("))
        }
    }

    @Nested
    inner class SymbolLookup {
        @Test
        fun lookupNonExistentSymbolReturnsNull() {
            val proc = Process.current()
            val addr = proc.lookup("__definitely_not_a_real_symbol_xyz_12345__")
            assertNull(addr)
        }
    }
}
