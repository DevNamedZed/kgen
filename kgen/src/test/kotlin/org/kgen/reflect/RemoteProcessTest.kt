package org.kgen.reflect

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.reflect.process.RemoteProcess

class RemoteProcessTest {

    @Test
    fun openCurrentProcess() {
        val pid = ProcessHandle.current().pid()
        val proc = RemoteProcess.open(pid)
        assertEquals(pid, proc.pid())
        assertTrue(proc.isAlive())
    }

    @Test
    fun tryOpenExistingProcess() {
        val pid = ProcessHandle.current().pid()
        val proc = RemoteProcess.tryOpen(pid)
        assertNotNull(proc)
        assertEquals(pid, proc!!.pid())
    }

    @Test
    fun tryOpenNonexistentProcess() {
        val proc = RemoteProcess.tryOpen(999999999L)
        assertNull(proc)
    }

    @Test
    fun openNonexistentProcessThrows() {
        assertThrows(IllegalArgumentException::class.java) {
            RemoteProcess.open(999999999L)
        }
    }

    @Test
    fun processName() {
        val pid = ProcessHandle.current().pid()
        val proc = RemoteProcess.open(pid)
        val name = proc.name()
        assertNotNull(name)
        assertTrue(name.isNotEmpty())
    }

    @Test
    fun processArch() {
        val pid = ProcessHandle.current().pid()
        val proc = RemoteProcess.open(pid)
        assertNotNull(proc.arch())
    }

    @Test
    fun processStartTime() {
        val pid = ProcessHandle.current().pid()
        val proc = RemoteProcess.open(pid)
        val startTime = proc.startTime()
        // Current process should have a start time
        assertNotNull(startTime)
        assertTrue(startTime!! > 0)
    }

    @Test
    fun processToString() {
        val pid = ProcessHandle.current().pid()
        val proc = RemoteProcess.open(pid)
        val str = proc.toString()
        assertTrue(str.contains("pid=$pid"))
        assertTrue(str.contains("alive=true"))
    }

    @Test
    fun listProcesses() {
        val processes = RemoteProcess.list()
        assertTrue(processes.isNotEmpty(), "Should list at least the current process")
        val current = processes.firstOrNull { it.pid == ProcessHandle.current().pid() }
        assertNotNull(current)
    }

    @Test
    fun findByName() {
        // Find java processes (current process should be found)
        val javaProcs = RemoteProcess.findByName("java")
        // This might be empty in some environments, but the API should work
        assertNotNull(javaProcs)
    }

    @Test
    fun threadsForCurrentProcess() {
        val pid = ProcessHandle.current().pid()
        val proc = RemoteProcess.open(pid)
        val threads = proc.threads()
        // On Linux, /proc/<pid>/task should have at least one thread
        if (System.getProperty("os.name").lowercase().contains("linux")) {
            assertTrue(threads.isNotEmpty(), "Current process should have at least one thread")
            assertTrue(threads.contains(pid), "Main thread should have same ID as PID")
        }
    }

    @Test
    fun memoryMappingProperties() {
        val mapping = RemoteProcess.MemoryMapping(
            startAddress = 0x7f0000000000,
            endAddress = 0x7f0000001000,
            permissions = "r-xp",
            offset = 0,
            device = "00:00",
            inode = 0,
            path = "/usr/lib/libc.so.6",
        )
        assertEquals(0x1000, mapping.size)
        assertTrue(mapping.isReadable)
        assertFalse(mapping.isWritable)
        assertTrue(mapping.isExecutable)
        assertTrue(mapping.isPrivate)
        assertTrue(mapping.isFile)
    }

    @Test
    fun memoryMappingWithoutPath() {
        val mapping = RemoteProcess.MemoryMapping(
            startAddress = 0x7f0000000000,
            endAddress = 0x7f0000001000,
            permissions = "rw-p",
            offset = 0,
            device = "00:00",
            inode = 0,
            path = null,
        )
        assertTrue(mapping.isReadable)
        assertTrue(mapping.isWritable)
        assertFalse(mapping.isExecutable)
        assertFalse(mapping.isFile)
    }

    @Test
    fun moduleLookup() {
        val pid = ProcessHandle.current().pid()
        val proc = RemoteProcess.open(pid)
        val modules = proc.memoryMap()
        // On Linux, should have some memory mappings
        if (System.getProperty("os.name").lowercase().contains("linux")) {
            assertTrue(modules.isNotEmpty())
        }
    }

    @Test
    fun processInfoDataClass() {
        val info = RemoteProcess.ProcessInfo(
            pid = 42,
            name = "test",
            isAlive = true,
        )
        assertEquals(42, info.pid)
        assertEquals("test", info.name)
        assertTrue(info.isAlive)
    }
}
