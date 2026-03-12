package org.kgen.reflect

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import java.io.File

class HookTest {

    // --- Data class tests ---

    @Test
    fun `GotEntry stores symbol name, offset, and relocation type`() {
        val entry = Hook.GotEntry("printf", 0x601020, 7)
        assertEquals("printf", entry.symbolName)
        assertEquals(0x601020, entry.offset)
        assertEquals(7, entry.relocType)
    }

    @Test
    fun `GotEntry equality and copy`() {
        val a = Hook.GotEntry("puts", 0x4000, 7)
        val b = Hook.GotEntry("puts", 0x4000, 7)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())

        val c = a.copy(symbolName = "printf")
        assertEquals("printf", c.symbolName)
        assertEquals(a.offset, c.offset)
    }

    @Test
    fun `IatEntry stores dll name, function name, and rva`() {
        val entry = Hook.IatEntry("kernel32.dll", "CreateFileW", 0x2000)
        assertEquals("kernel32.dll", entry.dllName)
        assertEquals("CreateFileW", entry.functionName)
        assertEquals(0x2000, entry.rva)
    }

    @Test
    fun `IatEntry equality and copy`() {
        val a = Hook.IatEntry("user32.dll", "MessageBoxA", 0x3000)
        val b = Hook.IatEntry("user32.dll", "MessageBoxA", 0x3000)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())

        val c = a.copy(functionName = "MessageBoxW")
        assertEquals("MessageBoxW", c.functionName)
        assertEquals(a.dllName, c.dllName)
    }

    @Test
    fun `StubEntry stores symbol name and address`() {
        val entry = Hook.StubEntry("_malloc", 0x100008000)
        assertEquals("_malloc", entry.symbolName)
        assertEquals(0x100008000, entry.address)
    }

    @Test
    fun `StubEntry equality and copy`() {
        val a = Hook.StubEntry("_free", 0x5000)
        val b = Hook.StubEntry("_free", 0x5000)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())

        val c = a.copy(address = 0x6000)
        assertEquals(0x6000, c.address)
        assertEquals(a.symbolName, c.symbolName)
    }

    @Test
    fun `GotEntry toString contains field values`() {
        val entry = Hook.GotEntry("test", 100, 7)
        val str = entry.toString()
        assertTrue(str.contains("test"))
        assertTrue(str.contains("100"))
        assertTrue(str.contains("7"))
    }

    @Test
    fun `IatEntry toString contains field values`() {
        val entry = Hook.IatEntry("ntdll.dll", "NtClose", 0x400)
        val str = entry.toString()
        assertTrue(str.contains("ntdll.dll"))
        assertTrue(str.contains("NtClose"))
    }

    @Test
    fun `StubEntry toString contains field values`() {
        val entry = Hook.StubEntry("_exit", 0x8000)
        val str = entry.toString()
        assertTrue(str.contains("_exit"))
    }

    @Test
    fun `data class destructuring works for GotEntry`() {
        val (name, offset, type) = Hook.GotEntry("sym", 42, 3)
        assertEquals("sym", name)
        assertEquals(42, offset)
        assertEquals(3, type)
    }

    @Test
    fun `data class destructuring works for IatEntry`() {
        val (dll, func, rva) = Hook.IatEntry("a.dll", "Foo", 99)
        assertEquals("a.dll", dll)
        assertEquals("Foo", func)
        assertEquals(99, rva)
    }

    @Test
    fun `data class destructuring works for StubEntry`() {
        val (name, addr) = Hook.StubEntry("bar", 123)
        assertEquals("bar", name)
        assertEquals(123, addr)
    }

    // --- createTrampoline tests ---

    @Test
    @EnabledOnOs(OS.LINUX, OS.WINDOWS)
    fun `createTrampoline returns NativeCode with correct size`() {
        val original = byteArrayOf(0x55, 0x48, 0x89.toByte(), 0xE5.toByte())
        val continueAddr = 0x00400004L

        val trampoline = Hook.createTrampoline(original, continueAddr)
        trampoline.use { code ->
            assertNotNull(code)
            assertTrue(code.baseAddress != 0L)
            // original (4 bytes) + jump (14 bytes) = 18 bytes
            val bytes = code.codeBytes()
            assertEquals(original.size + 14, bytes.size)
        }
    }

    @Test
    @EnabledOnOs(OS.LINUX, OS.WINDOWS)
    fun `createTrampoline preserves original bytes at start`() {
        val original = byteArrayOf(0x90.toByte(), 0xCC.toByte(), 0x41, 0x55)
        val continueAddr = 0x00500000L

        Hook.createTrampoline(original, continueAddr).use { code ->
            val bytes = code.codeBytes()
            for (i in original.indices) {
                assertEquals(original[i], bytes[i], "Byte at index $i should match original")
            }
        }
    }

    @Test
    @EnabledOnOs(OS.LINUX, OS.WINDOWS)
    fun `createTrampoline encodes absolute jump after original bytes`() {
        val original = byteArrayOf(0x55, 0x48)
        val continueAddr = 0x0000000012345678L

        Hook.createTrampoline(original, continueAddr).use { code ->
            val bytes = code.codeBytes()
            val jumpStart = original.size

            // FF 25 00 00 00 00 = jmp [rip+0]
            assertEquals(0xFF.toByte(), bytes[jumpStart])
            assertEquals(0x25.toByte(), bytes[jumpStart + 1])
            assertEquals(0x00.toByte(), bytes[jumpStart + 2])
            assertEquals(0x00.toByte(), bytes[jumpStart + 3])
            assertEquals(0x00.toByte(), bytes[jumpStart + 4])
            assertEquals(0x00.toByte(), bytes[jumpStart + 5])

            // Next 8 bytes = little-endian continue address
            var decoded = 0L
            for (i in 0..7) {
                decoded = decoded or ((bytes[jumpStart + 6 + i].toLong() and 0xFF) shl (i * 8))
            }
            assertEquals(continueAddr, decoded)
        }
    }

    @Test
    @EnabledOnOs(OS.LINUX, OS.WINDOWS)
    fun `createTrampoline encodes high address correctly`() {
        val original = byteArrayOf(0x90.toByte())
        val continueAddr = 0x7FFF_DEAD_BEEF_CAFEL

        Hook.createTrampoline(original, continueAddr).use { code ->
            val bytes = code.codeBytes()
            val jumpStart = original.size

            var decoded = 0L
            for (i in 0..7) {
                decoded = decoded or ((bytes[jumpStart + 6 + i].toLong() and 0xFF) shl (i * 8))
            }
            assertEquals(continueAddr, decoded)
        }
    }

    @Test
    @EnabledOnOs(OS.LINUX, OS.WINDOWS)
    fun `createTrampoline with empty original bytes`() {
        val original = byteArrayOf()
        val continueAddr = 0x1000L

        Hook.createTrampoline(original, continueAddr).use { code ->
            val bytes = code.codeBytes()
            assertEquals(14, bytes.size)
            assertEquals(0xFF.toByte(), bytes[0])
            assertEquals(0x25.toByte(), bytes[1])
        }
    }

    @Test
    @EnabledOnOs(OS.LINUX, OS.WINDOWS)
    fun `createTrampoline has trampoline symbol at offset zero`() {
        val original = byteArrayOf(0xCC.toByte())
        val continueAddr = 0x2000L

        Hook.createTrampoline(original, continueAddr).use { code ->
            assertTrue(code.symbols().contains("trampoline"))
            assertEquals(code.baseAddress, code.symbolAddress("trampoline"))
        }
    }

    // --- findGotEntry tests (ELF) ---

    @Test
    fun `findGotEntry returns null for non-existent symbol in valid ELF`() {
        val elfPath = findElfBinary() ?: return
        val result = Hook.findGotEntry(elfPath, "this_symbol_definitely_does_not_exist_xyz_12345")
        assertNull(result)
    }

    @Test
    fun `findGotEntry throws for invalid file`() {
        val tempFile = File.createTempFile("not_elf", ".bin")
        tempFile.deleteOnExit()
        tempFile.writeBytes(byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7))

        assertThrows(Exception::class.java) {
            Hook.findGotEntry(tempFile.absolutePath, "test")
        }
    }

    // --- findIatEntry tests (PE) ---

    @Test
    fun `findIatEntry returns null for non-existent dll in valid PE`() {
        val pePath = findPeBinary() ?: return
        val result = Hook.findIatEntry(pePath, "nonexistent_dll_xyz.dll", "SomeFunction")
        assertNull(result)
    }

    @Test
    fun `findIatEntry returns null for non-existent function in valid PE`() {
        val pePath = findPeBinary() ?: return
        val result = Hook.findIatEntry(pePath, "kernel32.dll", "NonExistentFunction_xyz_12345")
        assertNull(result)
    }

    @Test
    fun `findIatEntry throws for invalid file`() {
        val tempFile = File.createTempFile("not_pe", ".bin")
        tempFile.deleteOnExit()
        tempFile.writeBytes(byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7))

        assertThrows(Exception::class.java) {
            Hook.findIatEntry(tempFile.absolutePath, "test.dll", "Func")
        }
    }

    // --- findStubEntry tests (Mach-O) ---

    @Test
    fun `findStubEntry throws for invalid file`() {
        val tempFile = File.createTempFile("not_macho", ".bin")
        tempFile.deleteOnExit()
        tempFile.writeBytes(byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7))

        assertThrows(Exception::class.java) {
            Hook.findStubEntry(tempFile.absolutePath, "test")
        }
    }

    // --- Helpers ---

    private fun findElfBinary(): String? {
        // Try common system ELF binaries
        val candidates = listOf("/bin/ls", "/bin/sh", "/usr/bin/env")
        for (path in candidates) {
            val f = File(path)
            if (f.exists() && f.canRead()) {
                val magic = f.inputStream().use { it.readNBytes(4) }
                if (magic.size == 4 && magic[0] == 0x7F.toByte() &&
                    magic[1] == 'E'.code.toByte() && magic[2] == 'L'.code.toByte() && magic[3] == 'F'.code.toByte()
                ) {
                    return path
                }
            }
        }
        return null
    }

    private fun findPeBinary(): String? {
        // Check CLR fixture DLL
        val fixture = "/mnt/c/src/kgen/kgen/src/test/resources/fixtures/clr/TestLib.dll"
        val f = File(fixture)
        if (f.exists() && f.canRead()) {
            val magic = f.inputStream().use { it.readNBytes(2) }
            if (magic.size == 2 && magic[0] == 'M'.code.toByte() && magic[1] == 'Z'.code.toByte()) {
                return fixture
            }
        }
        // Try Windows system DLLs via WSL mount
        val candidates = listOf(
            "/mnt/c/Windows/System32/kernel32.dll",
            "/mnt/c/Windows/System32/ntdll.dll",
        )
        for (path in candidates) {
            val file = File(path)
            if (file.exists() && file.canRead()) return path
        }
        return null
    }
}
