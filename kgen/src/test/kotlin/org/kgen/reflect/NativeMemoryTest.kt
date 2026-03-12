package org.kgen.reflect

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class NativeMemoryTest {

    @Test
    fun `allocate read-write and round-trip bytes`() {
        NativeMemory.allocateReadWrite(256).use { mem ->
            val data = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
            mem.write(0, data)
            assertContentEquals(data, mem.read(0, 8))
        }
    }

    @Test
    fun `write and read individual bytes at various offsets`() {
        NativeMemory.allocateReadWrite(128).use { mem ->
            mem.writeByte(0, 0xAB.toByte())
            mem.writeByte(63, 0xCD.toByte())
            mem.writeByte(127, 0xEF.toByte())
            assertEquals(0xAB.toByte(), mem.readByte(0))
            assertEquals(0xCD.toByte(), mem.readByte(63))
            assertEquals(0xEF.toByte(), mem.readByte(127))
        }
    }

    @Test
    fun `write and read int little-endian`() {
        NativeMemory.allocateReadWrite(64).use { mem ->
            mem.writeInt(0, 0x04030201)
            assertEquals(0x04030201, mem.readInt(0))
            // verify little-endian byte order
            assertEquals(0x01.toByte(), mem.readByte(0))
            assertEquals(0x02.toByte(), mem.readByte(1))
            assertEquals(0x03.toByte(), mem.readByte(2))
            assertEquals(0x04.toByte(), mem.readByte(3))
        }
    }

    @Test
    fun `write and read int at non-zero offset`() {
        NativeMemory.allocateReadWrite(64).use { mem ->
            mem.writeInt(16, 0xDEADBEEF.toInt())
            assertEquals(0xDEADBEEF.toInt(), mem.readInt(16))
            // offset 0 should still be zero
            assertEquals(0, mem.readInt(0))
        }
    }

    @Test
    fun `write and read long`() {
        NativeMemory.allocateReadWrite(64).use { mem ->
            val value = 0x0807060504030201L
            mem.writeLong(0, value)
            assertEquals(value, mem.readLong(0))
            // verify little-endian byte order
            assertEquals(0x01.toByte(), mem.readByte(0))
            assertEquals(0x08.toByte(), mem.readByte(7))
        }
    }

    @Test
    fun `write and read long at non-zero offset`() {
        NativeMemory.allocateReadWrite(64).use { mem ->
            mem.writeLong(24, Long.MAX_VALUE)
            assertEquals(Long.MAX_VALUE, mem.readLong(24))
            assertEquals(0L, mem.readLong(0))
        }
    }

    @Test
    fun `write array then read sub-ranges`() {
        NativeMemory.allocateReadWrite(256).use { mem ->
            val data = ByteArray(100) { it.toByte() }
            mem.write(0, data)
            assertContentEquals(byteArrayOf(0, 1, 2, 3, 4), mem.read(0, 5))
            assertContentEquals(byteArrayOf(50, 51, 52), mem.read(50, 3))
            assertContentEquals(byteArrayOf(97, 98, 99), mem.read(97, 3))
        }
    }

    @Test
    fun `write at offset preserves other regions`() {
        NativeMemory.allocateReadWrite(128).use { mem ->
            mem.write(0, byteArrayOf(0xAA.toByte(), 0xBB.toByte()))
            mem.write(64, byteArrayOf(0xCC.toByte(), 0xDD.toByte()))
            assertEquals(0xAA.toByte(), mem.readByte(0))
            assertEquals(0xBB.toByte(), mem.readByte(1))
            assertEquals(0xCC.toByte(), mem.readByte(64))
            assertEquals(0xDD.toByte(), mem.readByte(65))
            // region between should still be zero
            assertEquals(0.toByte(), mem.readByte(32))
        }
    }

    @Test
    fun `close does not throw`() {
        val mem = NativeMemory.allocateReadWrite(64)
        mem.close()
    }

    @Test
    fun `address and size properties`() {
        NativeMemory.allocateReadWrite(4096).use { mem ->
            assertNotEquals(0L, mem.address)
            assertEquals(4096L, mem.size)
        }
    }

    @Test
    fun `allocation is zero-filled`() {
        NativeMemory.allocateReadWrite(256).use { mem ->
            val bytes = mem.read(0, 256)
            assertTrue(bytes.all { it == 0.toByte() })
        }
    }

    @Test
    fun `boundary write at last byte`() {
        NativeMemory.allocateReadWrite(64).use { mem ->
            mem.writeByte(63, 0xFF.toByte())
            assertEquals(0xFF.toByte(), mem.readByte(63))
        }
    }

    @Test
    fun `boundary write array ending at last byte`() {
        NativeMemory.allocateReadWrite(64).use { mem ->
            val data = byteArrayOf(1, 2, 3, 4)
            mem.write(60, data)
            assertContentEquals(data, mem.read(60, 4))
        }
    }

    @Test
    fun `PROT constants have correct values`() {
        assertEquals(1, NativeMemory.PROT_READ)
        assertEquals(2, NativeMemory.PROT_WRITE)
        assertEquals(4, NativeMemory.PROT_EXEC)
    }

    @EnabledOnOs(OS.LINUX, OS.WINDOWS)
    @Test
    fun `allocate executable memory`() {
        NativeMemory.allocateExecutable(4096).use { mem ->
            assertNotEquals(0L, mem.address)
            assertEquals(4096L, mem.size)
            // write and read back to verify it is writable too
            val code = byteArrayOf(0x90.toByte(), 0xC3.toByte()) // nop; ret
            mem.write(0, code)
            assertContentEquals(code, mem.read(0, 2))
        }
    }

    @Test
    fun `viewAt reads from allocated memory`() {
        NativeMemory.allocateReadWrite(128).use { backing ->
            backing.write(0, byteArrayOf(10, 20, 30, 40, 50))
            NativeMemory.viewAt(backing.address, 128).use { view ->
                assertEquals(10.toByte(), view.readByte(0))
                assertEquals(50.toByte(), view.readByte(4))
                assertContentEquals(byteArrayOf(10, 20, 30, 40, 50), view.read(0, 5))
            }
        }
    }

    @Test
    fun `viewAt with offset into allocated memory`() {
        NativeMemory.allocateReadWrite(256).use { backing ->
            backing.write(64, byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte()))
            NativeMemory.viewAt(backing.address + 64, 64).use { view ->
                assertEquals(0xAA.toByte(), view.readByte(0))
                assertEquals(0xBB.toByte(), view.readByte(1))
                assertEquals(0xCC.toByte(), view.readByte(2))
            }
        }
    }

    @Test
    fun `static readBytes and writeBytes`() {
        NativeMemory.allocateReadWrite(128).use { mem ->
            val data = byteArrayOf(11, 22, 33, 44, 55)
            NativeMemory.writeBytes(mem.address, data)
            val result = NativeMemory.readBytes(mem.address, 5)
            assertContentEquals(data, result)
        }
    }

    @Test
    fun `static writeBytes then instance read`() {
        NativeMemory.allocateReadWrite(64).use { mem ->
            NativeMemory.writeBytes(mem.address, byteArrayOf(0x12, 0x34, 0x56))
            assertEquals(0x12.toByte(), mem.readByte(0))
            assertEquals(0x34.toByte(), mem.readByte(1))
            assertEquals(0x56.toByte(), mem.readByte(2))
        }
    }

    @Test
    fun `instance write then static readBytes`() {
        NativeMemory.allocateReadWrite(64).use { mem ->
            mem.write(0, byteArrayOf(0x78.toByte(), 0x9A.toByte()))
            val result = NativeMemory.readBytes(mem.address, 2)
            assertContentEquals(byteArrayOf(0x78.toByte(), 0x9A.toByte()), result)
        }
    }

    @Test
    fun `multiple allocations have distinct addresses`() {
        NativeMemory.allocateReadWrite(64).use { a ->
            NativeMemory.allocateReadWrite(64).use { b ->
                assertNotEquals(a.address, b.address)
            }
        }
    }

    @Test
    fun `large allocation write and read`() {
        val size = 1024L * 64 // 64KB
        NativeMemory.allocateReadWrite(size).use { mem ->
            val data = ByteArray(1024) { (it % 256).toByte() }
            mem.write(0, data)
            mem.write(63 * 1024L, data)
            assertContentEquals(data, mem.read(0, 1024))
            assertContentEquals(data, mem.read(63 * 1024L, 1024))
        }
    }

    @Test
    fun `writeInt and writeLong overlap correctly`() {
        NativeMemory.allocateReadWrite(64).use { mem ->
            mem.writeLong(0, -1L) // all 0xFF bytes
            mem.writeInt(0, 0)    // overwrite first 4 bytes with zeros
            assertEquals(0, mem.readInt(0))
            assertEquals(-1, mem.readInt(4)) // second 4 bytes untouched
        }
    }

    @Test
    fun `negative byte values round-trip`() {
        NativeMemory.allocateReadWrite(16).use { mem ->
            mem.writeByte(0, (-1).toByte())
            mem.writeByte(1, (-128).toByte())
            mem.writeByte(2, 127.toByte())
            assertEquals((-1).toByte(), mem.readByte(0))
            assertEquals((-128).toByte(), mem.readByte(1))
            assertEquals(127.toByte(), mem.readByte(2))
        }
    }

    @Test
    fun `negative int and long values round-trip`() {
        NativeMemory.allocateReadWrite(32).use { mem ->
            mem.writeInt(0, Int.MIN_VALUE)
            mem.writeInt(4, -1)
            mem.writeLong(8, Long.MIN_VALUE)
            mem.writeLong(16, -1L)
            assertEquals(Int.MIN_VALUE, mem.readInt(0))
            assertEquals(-1, mem.readInt(4))
            assertEquals(Long.MIN_VALUE, mem.readLong(8))
            assertEquals(-1L, mem.readLong(16))
        }
    }
}
