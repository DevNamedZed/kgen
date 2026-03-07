package org.kgen.integration.runtime

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.reflect.*

class NativeMemoryTest {

    @Test
    fun allocateReadWrite() {
        val mem = NativeMemory.allocateExecutable(4096)
        assertTrue(mem.address != 0L)
        assertEquals(4096L, mem.size)

        mem.writeByte(0, 0xAB.toByte())
        assertEquals(0xAB.toByte(), mem.readByte(0))

        mem.writeInt(4, 0xDEADBEEF.toInt())
        assertEquals(0xDEADBEEF.toInt(), mem.readInt(4))

        mem.writeLong(16, 0x123456789ABCDEF0)
        assertEquals(0x123456789ABCDEF0, mem.readLong(16))

        val data = byteArrayOf(10, 20, 30, 40, 50)
        mem.write(100, data)
        assertArrayEquals(data, mem.read(100, 5))

        mem.close()
    }

    @Test
    fun staticReadWrite() {
        val mem = NativeMemory.allocateExecutable(64)
        val addr = mem.address
        NativeMemory.writeBytes(addr, byteArrayOf(1, 2, 3, 4))
        val read = NativeMemory.readBytes(addr, 4)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), read)
        mem.close()
    }

    @Test
    fun viewAt() {
        val mem = NativeMemory.allocateExecutable(64)
        mem.write(0, byteArrayOf(0x55, 0x48.toByte(), 0x89.toByte(), 0xE5.toByte()))

        val view = NativeMemory.viewAt(mem.address, 64)
        assertEquals(mem.address, view.address)
        assertEquals(0x55.toByte(), view.readByte(0))

        view.close()
        mem.close()
    }
}
