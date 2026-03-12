package org.kgen.runtime.gc

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
    fun compactReducesFragmentation() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val layout = pointLayout().copy(typeId = 1)
            registry.register(layout)

            val a = heap.allocate(layout)
            val b = heap.allocate(layout)
            val c = heap.allocate(layout)
            heap.writeField(a, 0, 1L)
            heap.writeField(b, 0, 2L)
            heap.writeField(c, 0, 3L)

            // Mark a and c as live, b is dead
            heap.setGcFlagsAt(a, 1)
            heap.setGcFlagsAt(c, 1)
            // Make b dead (typeId stays but no mark bit)
            heap.addFreeBlock(b, layout.totalSize().toLong())

            val bytesBeforeCompact = heap.bytesInUse()
            val forwarding = heap.compact(registry)

            assertTrue(heap.bytesInUse() < bytesBeforeCompact, "Cursor should move back after compaction")
            assertEquals(0, heap.freeBlockCount(), "Free list should be cleared")
        }
    }

    @Test
    fun compactForwardingMapIsCorrect() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val layout = pointLayout().copy(typeId = 1)
            registry.register(layout)

            val a = heap.allocate(layout)
            val b = heap.allocate(layout)
            val c = heap.allocate(layout)
            heap.writeField(a, 0, 1L)
            heap.writeField(b, 0, 2L)
            heap.writeField(c, 0, 3L)

            // Mark a and c as live, b is dead
            heap.setGcFlagsAt(a, 1)
            heap.setGcFlagsAt(c, 1)

            val forwarding = heap.compact(registry)

            // 'a' stays at the same place (first live object)
            assertFalse(forwarding.containsKey(a), "First object should not move")
            // 'c' should be forwarded to where 'b' was
            assertTrue(forwarding.containsKey(c), "Third object should be forwarded")
            val newC = forwarding[c]!!
            assertEquals(3L, heap.readField(newC, 0), "Data should be preserved after compaction")
        }
    }

    @Test
    fun compactCursorResetCorrectly() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val layout = pointLayout().copy(typeId = 1)
            registry.register(layout)

            val a = heap.allocate(layout)
            heap.allocate(layout) // b (dead)
            heap.allocate(layout) // c (dead)
            heap.writeField(a, 0, 42L)

            // Only mark a as live
            heap.setGcFlagsAt(a, 1)

            heap.compact(registry)

            // Cursor should be right after the single live object
            val expectedSize = ((layout.totalSize().toLong() + 7) and 7L.inv())
            assertEquals(expectedSize, heap.bytesInUse(), "Cursor should be after single live object")

            // Should be able to allocate right after
            val d = heap.allocate(layout)
            assertNotEquals(a, d)
            heap.writeField(d, 0, 99L)
            assertEquals(99L, heap.readField(d, 0))
            assertEquals(42L, heap.readField(a, 0))
        }
    }

    @Test
    fun compactNoLiveObjectsResetsCursor() {
        BumpHeap(4096).use { heap ->
            val registry = TypeRegistry()
            val layout = pointLayout().copy(typeId = 1)
            registry.register(layout)

            heap.allocate(layout)
            heap.allocate(layout)

            // No mark bits set — all dead
            heap.compact(registry)

            assertEquals(0L, heap.bytesInUse(), "Cursor should be 0 when no live objects")
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
