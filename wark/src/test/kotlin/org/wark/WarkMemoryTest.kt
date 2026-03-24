package org.wark

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WarkMemoryTest {

    @Test
    fun createWithInitialPages() {
        val memory = WarkMemory.create(1)
        assertEquals(1, memory.pages())
        assertEquals(65536, memory.sizeBytes())
    }

    @Test
    fun readWriteI32() {
        val memory = WarkMemory.create(1)
        memory.writeI32(0, 42)
        assertEquals(42, memory.readI32(0))
    }

    @Test
    fun readWriteI64() {
        val memory = WarkMemory.create(1)
        memory.writeI64(100, 9876543210L)
        assertEquals(9876543210L, memory.readI64(100))
    }

    @Test
    fun readWriteF32() {
        val memory = WarkMemory.create(1)
        memory.writeF32(0, 3.14f)
        assertEquals(3.14f, memory.readF32(0))
    }

    @Test
    fun readWriteF64() {
        val memory = WarkMemory.create(1)
        memory.writeF64(0, 3.14159265)
        assertEquals(3.14159265, memory.readF64(0))
    }

    @Test
    fun readWriteByte() {
        val memory = WarkMemory.create(1)
        memory.writeByte(0, 0xFF.toByte())
        assertEquals(0xFF.toByte(), memory.readByte(0))
    }

    @Test
    fun readWriteUtf8() {
        val memory = WarkMemory.create(1)
        memory.writeUtf8(100, "Hello, World")
        assertEquals("Hello, World", memory.readUtf8(100))
    }

    @Test
    fun readWriteBytes() {
        val memory = WarkMemory.create(1)
        val data = byteArrayOf(1, 2, 3, 4, 5)
        memory.writeBytes(0, data)
        val result = memory.readBytes(0, 5)
        assertEquals(data.toList(), result.toList())
    }

    @Test
    fun growMemory() {
        val memory = WarkMemory.create(1, 10)
        val previousPages = memory.grow(2)
        assertEquals(1, previousPages)
        assertEquals(3, memory.pages())
        assertEquals(3 * 65536, memory.sizeBytes())
    }

    @Test
    fun growPreservesData() {
        val memory = WarkMemory.create(1, 10)
        memory.writeI32(0, 42)
        memory.grow(1)
        assertEquals(42, memory.readI32(0))
    }

    @Test
    fun growBeyondMaxReturnsNegativeOne() {
        val memory = WarkMemory.create(1, 2)
        val result = memory.grow(5)
        assertEquals(-1, result)
        assertEquals(1, memory.pages())
    }

    @Test
    fun outOfBoundsReadTraps() {
        // maxPages=1 limits physical allocation to 1 page
        val memory = WarkMemory.create(1, maxPages = 1)
        assertFailsWith<WasmTrap> {
            memory.readI32(65536)
        }
    }

    @Test
    fun outOfBoundsWriteTraps() {
        val memory = WarkMemory.create(1, maxPages = 1)
        assertFailsWith<WasmTrap> {
            memory.writeI32(65536, 42)
        }
    }

    @Test
    fun fill() {
        val memory = WarkMemory.create(1)
        memory.fill(0, 0xAA.toByte(), 10)
        for (index in 0 until 10) {
            assertEquals(0xAA.toByte(), memory.readByte(index))
        }
    }

    @Test
    fun copyNonOverlapping() {
        val memory = WarkMemory.create(1)
        memory.writeI32(0, 42)
        memory.writeI32(4, 99)
        memory.copy(100, 0, 8)
        assertEquals(42, memory.readI32(100))
        assertEquals(99, memory.readI32(104))
    }

    @Test
    fun copyOverlapping() {
        val memory = WarkMemory.create(1)
        memory.writeI32(0, 1)
        memory.writeI32(4, 2)
        memory.writeI32(8, 3)
        memory.copy(4, 0, 8)
        assertEquals(1, memory.readI32(4))
        assertEquals(2, memory.readI32(8))
    }
}
