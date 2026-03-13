package org.kgen.reflect.process

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class RemoteProcessTest {

    @Nested
    inner class ProcessInfo {
        @Test
        fun fieldsAccessible() {
            val info = RemoteProcess.ProcessInfo(pid = 42, name = "myapp", isAlive = true)
            assertEquals(42L, info.pid)
            assertEquals("myapp", info.name)
            assertTrue(info.isAlive)
        }

        @Test
        fun equalsWhenSameValues() {
            val a = RemoteProcess.ProcessInfo(1, "test", true)
            val b = RemoteProcess.ProcessInfo(1, "test", true)
            assertEquals(a, b)
        }

        @Test
        fun notEqualsWhenDifferentPid() {
            val a = RemoteProcess.ProcessInfo(1, "test", true)
            val b = RemoteProcess.ProcessInfo(2, "test", true)
            assertNotEquals(a, b)
        }

        @Test
        fun notEqualsWhenDifferentName() {
            val a = RemoteProcess.ProcessInfo(1, "a", true)
            val b = RemoteProcess.ProcessInfo(1, "b", true)
            assertNotEquals(a, b)
        }

        @Test
        fun notEqualsWhenDifferentAlive() {
            val a = RemoteProcess.ProcessInfo(1, "test", true)
            val b = RemoteProcess.ProcessInfo(1, "test", false)
            assertNotEquals(a, b)
        }

        @Test
        fun hashCodeConsistent() {
            val a = RemoteProcess.ProcessInfo(1, "test", true)
            val b = RemoteProcess.ProcessInfo(1, "test", true)
            assertEquals(a.hashCode(), b.hashCode())
        }

        @Test
        fun copyChangesField() {
            val info = RemoteProcess.ProcessInfo(1, "test", true)
            val copy = info.copy(name = "updated")
            assertEquals("updated", copy.name)
            assertEquals("test", info.name)
        }
    }

    @Nested
    inner class Open {
        @Test
        fun openNonExistentPidThrows() {
            assertThrows(IllegalArgumentException::class.java) {
                RemoteProcess.open(999999999L)
            }
        }

        @Test
        fun tryOpenNonExistentPidReturnsNull() {
            val result = RemoteProcess.tryOpen(999999999L)
            assertNull(result)
        }

        @Test
        fun openCurrentProcessSucceeds() {
            val currentPid = ProcessHandle.current().pid()
            val remote = RemoteProcess.open(currentPid)
            assertEquals(currentPid, remote.pid())
            remote.close()
        }

        @Test
        fun tryOpenCurrentProcessNotNull() {
            val currentPid = ProcessHandle.current().pid()
            val remote = RemoteProcess.tryOpen(currentPid)
            assertNotNull(remote)
            remote?.close()
        }
    }

    @Nested
    inner class CurrentAsRemote {
        @Test
        fun isAliveReturnsTrue() {
            val currentPid = ProcessHandle.current().pid()
            val remote = RemoteProcess.open(currentPid)
            assertTrue(remote.isAlive())
            remote.close()
        }

        @Test
        fun archIsNotNull() {
            val currentPid = ProcessHandle.current().pid()
            val remote = RemoteProcess.open(currentPid)
            assertNotNull(remote.arch())
            remote.close()
        }

        @Test
        fun toStringContainsPid() {
            val currentPid = ProcessHandle.current().pid()
            val remote = RemoteProcess.open(currentPid)
            val str = remote.toString()
            assertTrue(str.contains(currentPid.toString()))
            remote.close()
        }

        @Test
        fun toStringStartsWithRemoteProcess() {
            val currentPid = ProcessHandle.current().pid()
            val remote = RemoteProcess.open(currentPid)
            assertTrue(remote.toString().startsWith("RemoteProcess("))
            remote.close()
        }

        @Test
        fun startTimeIsNotNull() {
            val currentPid = ProcessHandle.current().pid()
            val remote = RemoteProcess.open(currentPid)
            val startTime = remote.startTime()
            assertNotNull(startTime)
            assertTrue(startTime!! > 0)
            remote.close()
        }

        @Test
        fun threadsReturnsList() {
            val currentPid = ProcessHandle.current().pid()
            val remote = RemoteProcess.open(currentPid)
            val threads = remote.threads()
            assertNotNull(threads)
            remote.close()
        }

        @Test
        fun memoryMapReturnsList() {
            val currentPid = ProcessHandle.current().pid()
            val remote = RemoteProcess.open(currentPid)
            val map = remote.memoryMap()
            assertNotNull(map)
            remote.close()
        }
    }

    @Nested
    inner class ListProcesses {
        @Test
        fun listReturnsNonEmpty() {
            val procs = RemoteProcess.list()
            assertTrue(procs.isNotEmpty())
        }

        @Test
        fun listContainsCurrentProcess() {
            val currentPid = ProcessHandle.current().pid()
            val procs = RemoteProcess.list()
            assertTrue(procs.any { it.pid == currentPid })
        }

        @Test
        fun listEntriesHaveNonNegativePids() {
            val procs = RemoteProcess.list()
            for (p in procs) {
                assertTrue(p.pid >= 0)
            }
        }

        @Test
        fun findByNameReturnsEmptyForGibberish() {
            val results = RemoteProcess.findByName("__absolutely_not_a_real_process_name_xyz__")
            assertTrue(results.isEmpty())
        }
    }

    @Nested
    inner class ModuleLookup {
        @Test
        fun moduleReturnsNullForNonExistent() {
            val currentPid = ProcessHandle.current().pid()
            val remote = RemoteProcess.open(currentPid)
            val mod = remote.module("__nonexistent_module_xyz__")
            assertNull(mod)
            remote.close()
        }
    }
}
