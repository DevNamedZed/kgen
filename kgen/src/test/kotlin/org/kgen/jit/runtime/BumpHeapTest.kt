package org.kgen.jit.runtime

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class BumpHeapTest {

    private fun pointLayout() = ObjectLayout(
        name = "Point",
        size = 16,
        fields = listOf(
            FieldDescriptor("x", 0, 8, false),
            FieldDescriptor("y", 8, 8, false),
        ),
    )

    @Test
    fun allocateAndReadWrite() {
        BumpHeap(4096).use { heap ->
            val layout = pointLayout()
            val addr = heap.allocate(layout)
            assertTrue(addr != 0L)

            heap.writeField(addr, 0, 42L)
            heap.writeField(addr, 1, 99L)

            assertEquals(42L, heap.readField(addr, 0))
            assertEquals(99L, heap.readField(addr, 1))
        }
    }

    @Test
    fun multipleAllocations() {
        BumpHeap(4096).use { heap ->
            val layout = pointLayout()
            val a = heap.allocate(layout)
            val b = heap.allocate(layout)
            assertNotEquals(a, b)

            heap.writeField(a, 0, 1L)
            heap.writeField(b, 0, 2L)

            assertEquals(1L, heap.readField(a, 0))
            assertEquals(2L, heap.readField(b, 0))
        }
    }

    @Test
    fun typeIdInHeader() {
        BumpHeap(4096).use { heap ->
            val layout = pointLayout().copy(typeId = 7)
            val addr = heap.allocate(layout)
            assertEquals(7, heap.typeIdAt(addr))
        }
    }

    @Test
    fun outOfMemory() {
        BumpHeap(64).use { heap ->
            val layout = pointLayout() // 24 bytes total (8 header + 16 data)
            heap.allocate(layout) // ok
            heap.allocate(layout) // ok (48 bytes used)
            assertThrows(OutOfMemoryError::class.java) {
                heap.allocate(layout) // should fail (72 > 64)
            }
        }
    }

    @Test
    fun bytesTracking() {
        BumpHeap(4096).use { heap ->
            assertEquals(0L, heap.bytesAllocated())
            val layout = pointLayout()
            heap.allocate(layout)
            assertTrue(heap.bytesAllocated() > 0)
            assertTrue(heap.bytesInUse() > 0)
        }
    }

    @Test
    fun baseAddressNonZero() {
        BumpHeap(4096).use { heap ->
            assertTrue(heap.baseAddress() != 0L)
        }
    }

    @Test
    fun capacityMatchesConstructor() {
        BumpHeap(2048).use { heap ->
            assertEquals(2048L, heap.capacity())
        }
    }

    @Test
    fun resetClearsInUse() {
        BumpHeap(4096).use { heap ->
            val layout = pointLayout()
            heap.allocate(layout)
            assertTrue(heap.bytesInUse() > 0)
            heap.reset()
            assertEquals(0L, heap.bytesInUse())
        }
    }

    @Test
    fun gcFlagsInitiallyZero() {
        BumpHeap(4096).use { heap ->
            val layout = pointLayout()
            val addr = heap.allocate(layout)
            assertEquals(0, heap.gcFlagsAt(addr))
        }
    }

    @Test
    fun setGcFlags() {
        BumpHeap(4096).use { heap ->
            val layout = pointLayout()
            val addr = heap.allocate(layout)
            heap.setGcFlagsAt(addr, 0x01)
            assertEquals(0x01, heap.gcFlagsAt(addr))
            heap.setGcFlagsAt(addr, 0xFF)
            assertEquals(0xFF, heap.gcFlagsAt(addr))
        }
    }

    @Test
    fun readWriteBytes() {
        BumpHeap(4096).use { heap ->
            val layout = pointLayout()
            val addr = heap.allocate(layout)
            val data = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
            val fieldAddr = addr + ObjectLayout.HEADER_SIZE
            heap.writeBytes(fieldAddr, data)
            val read = heap.readBytes(fieldAddr, 8)
            assertArrayEquals(data, read)
        }
    }

    @Test
    fun fieldIsolation() {
        BumpHeap(4096).use { heap ->
            val layout = pointLayout()
            val a = heap.allocate(layout)
            val b = heap.allocate(layout)
            heap.writeField(a, 0, 100L)
            heap.writeField(a, 1, 200L)
            heap.writeField(b, 0, 300L)
            heap.writeField(b, 1, 400L)
            assertEquals(100L, heap.readField(a, 0))
            assertEquals(200L, heap.readField(a, 1))
            assertEquals(300L, heap.readField(b, 0))
            assertEquals(400L, heap.readField(b, 1))
        }
    }

    @Test
    fun allocateAfterReset() {
        BumpHeap(4096).use { heap ->
            val layout = pointLayout()
            heap.allocate(layout)
            heap.reset()
            val addr = heap.allocate(layout)
            assertTrue(addr != 0L)
        }
    }

    @Test
    fun manyAllocations() {
        BumpHeap(65536).use { heap ->
            val layout = pointLayout()
            val addresses = mutableListOf<Long>()
            for (i in 0 until 100) {
                addresses.add(heap.allocate(layout))
            }
            // All addresses should be unique
            assertEquals(100, addresses.toSet().size)
        }
    }

    @Test
    fun writeFieldZero() {
        BumpHeap(4096).use { heap ->
            val layout = pointLayout()
            val addr = heap.allocate(layout)
            heap.writeField(addr, 0, 42L)
            heap.writeField(addr, 0, 0L)
            assertEquals(0L, heap.readField(addr, 0))
        }
    }

    @Test
    fun negativeFieldValue() {
        BumpHeap(4096).use { heap ->
            val layout = pointLayout()
            val addr = heap.allocate(layout)
            heap.writeField(addr, 0, -1L)
            assertEquals(-1L, heap.readField(addr, 0))
        }
    }

    @Test
    fun bytesAllocatedAccumulates() {
        BumpHeap(4096).use { heap ->
            val layout = pointLayout()
            val first = heap.bytesAllocated()
            heap.allocate(layout)
            val after1 = heap.bytesAllocated()
            heap.allocate(layout)
            val after2 = heap.bytesAllocated()
            assertTrue(after1 > first)
            assertTrue(after2 > after1)
        }
    }

    @Test
    fun typeIdForMultipleObjects() {
        BumpHeap(4096).use { heap ->
            val layout1 = pointLayout().copy(typeId = 5)
            val layout2 = pointLayout().copy(typeId = 10)
            val a = heap.allocate(layout1)
            val b = heap.allocate(layout2)
            assertEquals(5, heap.typeIdAt(a))
            assertEquals(10, heap.typeIdAt(b))
        }
    }
}
