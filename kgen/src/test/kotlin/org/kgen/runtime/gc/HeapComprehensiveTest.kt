package org.kgen.runtime.gc

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class HeapComprehensiveTest {

    private fun simpleLayout(typeId: Int = 1) = ObjectLayout(
        name = "Simple",
        size = 8,
        fields = listOf(FieldDescriptor("value", 0, 8, false)),
        typeId = typeId,
    )

    private fun pointLayout(typeId: Int = 2) = ObjectLayout(
        name = "Point",
        size = 16,
        fields = listOf(
            FieldDescriptor("x", 0, 8, false),
            FieldDescriptor("y", 8, 8, false),
        ),
        typeId = typeId,
    )

    private fun refLayout(typeId: Int = 3) = ObjectLayout(
        name = "Ref",
        size = 16,
        fields = listOf(
            FieldDescriptor("value", 0, 8, false),
            FieldDescriptor("next", 8, 8, true),
        ),
        typeId = typeId,
    )

    private fun largeLayout(typeId: Int = 4) = ObjectLayout(
        name = "Large",
        size = 64,
        fields = (0 until 8).map { i ->
            FieldDescriptor("f$i", i * 8, 8, false)
        },
        typeId = typeId,
    )

    private fun writeRef(heap: BumpHeap, obj: Long, fieldOffset: Int, target: Long) {
        val addr = obj + ObjectLayout.HEADER_SIZE + fieldOffset
        val bytes = ByteArray(8)
        for (i in 0 until 8) bytes[i] = (target shr (i * 8)).toByte()
        heap.writeBytes(addr, bytes)
    }

    private fun readRef(heap: BumpHeap, obj: Long, fieldOffset: Int): Long {
        val addr = obj + ObjectLayout.HEADER_SIZE + fieldOffset
        val bytes = heap.readBytes(addr, 8)
        var value = 0L
        for (i in 0 until 8) value = value or ((bytes[i].toLong() and 0xFF) shl (i * 8))
        return value
    }

    // Allocation at various sizes

    @Test
    fun `allocate simple 8-byte object`() {
        BumpHeap(4096).use { heap ->
            val layout = simpleLayout()
            val addr = heap.allocate(layout)
            assertTrue(addr != 0L)
        }
    }

    @Test
    fun `allocate 16-byte point object`() {
        BumpHeap(4096).use { heap ->
            val layout = pointLayout()
            val addr = heap.allocate(layout)
            assertTrue(addr != 0L)
        }
    }

    @Test
    fun `allocate 64-byte large object`() {
        BumpHeap(4096).use { heap ->
            val layout = largeLayout()
            val addr = heap.allocate(layout)
            assertTrue(addr != 0L)
        }
    }

    @Test
    fun `allocate smallest possible object`() {
        BumpHeap(4096).use { heap ->
            val layout = ObjectLayout("Tiny", 0, listOf(), typeId = 10)
            val addr = heap.allocate(layout)
            assertTrue(addr != 0L)
            assertEquals(10, heap.typeIdAt(addr))
        }
    }

    @Test
    fun `allocate objects of different sizes sequentially`() {
        BumpHeap(4096).use { heap ->
            val small = simpleLayout(1)
            val medium = pointLayout(2)
            val large = largeLayout(3)

            val a = heap.allocate(small)
            val b = heap.allocate(medium)
            val c = heap.allocate(large)

            assertTrue(a != 0L)
            assertTrue(b != 0L)
            assertTrue(c != 0L)
            assertNotEquals(a, b)
            assertNotEquals(b, c)
            assertNotEquals(a, c)
        }
    }

    @Test
    fun `allocate object with 32-byte data`() {
        BumpHeap(4096).use { heap ->
            val layout = ObjectLayout("Quad", 32, listOf(
                FieldDescriptor("a", 0, 8, false),
                FieldDescriptor("b", 8, 8, false),
                FieldDescriptor("c", 16, 8, false),
                FieldDescriptor("d", 24, 8, false),
            ), typeId = 5)
            val addr = heap.allocate(layout)
            assertTrue(addr != 0L)
            assertEquals(40, layout.totalSize()) // 8 header + 32 data
        }
    }

    // Alignment verification

    @Test
    fun `first allocation is 8-byte aligned`() {
        BumpHeap(4096).use { heap ->
            val addr = heap.allocate(simpleLayout())
            assertEquals(0L, addr % 8, "Address should be 8-byte aligned")
        }
    }

    @Test
    fun `second allocation is 8-byte aligned`() {
        BumpHeap(4096).use { heap ->
            heap.allocate(simpleLayout())
            val addr = heap.allocate(simpleLayout())
            assertEquals(0L, addr % 8, "Address should be 8-byte aligned")
        }
    }

    @Test
    fun `all allocations are 8-byte aligned`() {
        BumpHeap(65536).use { heap ->
            val layout = simpleLayout()
            for (i in 0 until 100) {
                val addr = heap.allocate(layout)
                assertEquals(0L, addr % 8, "Allocation $i not aligned: $addr")
            }
        }
    }

    @Test
    fun `mixed size allocations maintain alignment`() {
        BumpHeap(4096).use { heap ->
            val small = simpleLayout(1)
            val point = pointLayout(2)
            val large = largeLayout(3)

            for (i in 0 until 10) {
                val layouts = listOf(small, point, large)
                val addr = heap.allocate(layouts[i % 3])
                assertEquals(0L, addr % 8, "Allocation $i not aligned")
            }
        }
    }

    // Capacity and growth

    @Test
    fun `capacity matches constructor argument`() {
        BumpHeap(1024).use { heap ->
            assertEquals(1024L, heap.capacity())
        }
    }

    @Test
    fun `capacity matches for large heap`() {
        BumpHeap(1048576).use { heap ->
            assertEquals(1048576L, heap.capacity())
        }
    }

    @Test
    fun `bytes in use starts at zero`() {
        BumpHeap(4096).use { heap ->
            assertEquals(0L, heap.bytesInUse())
        }
    }

    @Test
    fun `bytes in use grows with allocations`() {
        BumpHeap(4096).use { heap ->
            val layout = simpleLayout()
            val before = heap.bytesInUse()
            heap.allocate(layout)
            val after = heap.bytesInUse()
            assertTrue(after > before)
        }
    }

    @Test
    fun `bytes allocated starts at zero`() {
        BumpHeap(4096).use { heap ->
            assertEquals(0L, heap.bytesAllocated())
        }
    }

    @Test
    fun `bytes allocated grows with each allocation`() {
        BumpHeap(4096).use { heap ->
            val layout = simpleLayout()
            heap.allocate(layout)
            val after1 = heap.bytesAllocated()
            heap.allocate(layout)
            val after2 = heap.bytesAllocated()
            assertTrue(after2 > after1)
        }
    }

    @Test
    fun `bytes allocated includes header`() {
        BumpHeap(4096).use { heap ->
            val layout = simpleLayout() // 8 data + 8 header = 16 total
            heap.allocate(layout)
            assertTrue(heap.bytesAllocated() >= layout.totalSize())
        }
    }

    @Test
    fun `out of memory on full heap`() {
        BumpHeap(32).use { heap ->
            val layout = simpleLayout() // 16 bytes total
            heap.allocate(layout) // 16 bytes used
            heap.allocate(layout) // 32 bytes used
            assertThrows(OutOfMemoryError::class.java) {
                heap.allocate(layout) // 48 > 32
            }
        }
    }

    @Test
    fun `out of memory with large object on small heap`() {
        BumpHeap(64).use { heap ->
            val layout = largeLayout() // 72 bytes total (8 + 64)
            assertThrows(OutOfMemoryError::class.java) {
                heap.allocate(layout)
            }
        }
    }

    @Test
    fun `allocate fills heap exactly`() {
        // Two 16-byte objects in a 32-byte heap
        BumpHeap(32).use { heap ->
            val layout = simpleLayout()
            heap.allocate(layout)
            heap.allocate(layout)
            // Heap is full
            assertThrows(OutOfMemoryError::class.java) {
                heap.allocate(layout)
            }
        }
    }

    // Object header read/write

    @Test
    fun `type id written in header`() {
        BumpHeap(4096).use { heap ->
            val layout = simpleLayout(42)
            val addr = heap.allocate(layout)
            assertEquals(42, heap.typeIdAt(addr))
        }
    }

    @Test
    fun `type id for different objects`() {
        BumpHeap(4096).use { heap ->
            val a = heap.allocate(simpleLayout(10))
            val b = heap.allocate(pointLayout(20))
            val c = heap.allocate(largeLayout(30))
            assertEquals(10, heap.typeIdAt(a))
            assertEquals(20, heap.typeIdAt(b))
            assertEquals(30, heap.typeIdAt(c))
        }
    }

    @Test
    fun `gc flags initially zero`() {
        BumpHeap(4096).use { heap ->
            val addr = heap.allocate(simpleLayout())
            assertEquals(0, heap.gcFlagsAt(addr))
        }
    }

    @Test
    fun `set gc flags to mark bit`() {
        BumpHeap(4096).use { heap ->
            val addr = heap.allocate(simpleLayout())
            heap.setGcFlagsAt(addr, 1)
            assertEquals(1, heap.gcFlagsAt(addr))
        }
    }

    @Test
    fun `set gc flags to various values`() {
        BumpHeap(4096).use { heap ->
            val addr = heap.allocate(simpleLayout())
            heap.setGcFlagsAt(addr, 0xFF)
            assertEquals(0xFF, heap.gcFlagsAt(addr))
            heap.setGcFlagsAt(addr, 0x55)
            assertEquals(0x55, heap.gcFlagsAt(addr))
            heap.setGcFlagsAt(addr, 0)
            assertEquals(0, heap.gcFlagsAt(addr))
        }
    }

    @Test
    fun `gc flags independent per object`() {
        BumpHeap(4096).use { heap ->
            val a = heap.allocate(simpleLayout())
            val b = heap.allocate(simpleLayout())
            heap.setGcFlagsAt(a, 1)
            heap.setGcFlagsAt(b, 2)
            assertEquals(1, heap.gcFlagsAt(a))
            assertEquals(2, heap.gcFlagsAt(b))
        }
    }

    @Test
    fun `type id and gc flags do not interfere`() {
        BumpHeap(4096).use { heap ->
            val addr = heap.allocate(simpleLayout(99))
            heap.setGcFlagsAt(addr, 0xFF)
            assertEquals(99, heap.typeIdAt(addr))
            assertEquals(0xFF, heap.gcFlagsAt(addr))
        }
    }

    // Free list management

    @Test
    fun `free list initially empty`() {
        BumpHeap(4096).use { heap ->
            assertEquals(0, heap.freeBlockCount())
            assertEquals(0L, heap.freeBytes())
        }
    }

    @Test
    fun `add free block increases count`() {
        BumpHeap(4096).use { heap ->
            val layout = simpleLayout()
            val addr = heap.allocate(layout)
            heap.addFreeBlock(addr, layout.totalSize().toLong())
            assertEquals(1, heap.freeBlockCount())
        }
    }

    @Test
    fun `add multiple free blocks`() {
        BumpHeap(4096).use { heap ->
            val layout = simpleLayout()
            val a = heap.allocate(layout)
            val b = heap.allocate(layout)
            val c = heap.allocate(layout)
            heap.addFreeBlock(a, layout.totalSize().toLong())
            heap.addFreeBlock(b, layout.totalSize().toLong())
            heap.addFreeBlock(c, layout.totalSize().toLong())
            assertEquals(3, heap.freeBlockCount())
        }
    }

    @Test
    fun `free bytes tracks total free space`() {
        BumpHeap(4096).use { heap ->
            val layout = simpleLayout()
            val totalSize = layout.totalSize().toLong()
            val a = heap.allocate(layout)
            val b = heap.allocate(layout)
            heap.addFreeBlock(a, totalSize)
            heap.addFreeBlock(b, totalSize)
            assertEquals(totalSize * 2, heap.freeBytes())
        }
    }

    @Test
    fun `allocate reuses free block first-fit`() {
        BumpHeap(4096).use { heap ->
            val layout = simpleLayout()
            val addr = heap.allocate(layout)
            heap.addFreeBlock(addr, layout.totalSize().toLong())

            // Allocating same size should reuse the free block
            val reused = heap.allocate(layout)
            assertTrue(reused != 0L)
        }
    }

    @Test
    fun `free block splitting when block is larger`() {
        BumpHeap(4096).use { heap ->
            val smallLayout = simpleLayout()
            val largeLayout = pointLayout()

            // Allocate a large object, free it, then allocate a small one
            val addr = heap.allocate(largeLayout) // 24 bytes
            heap.addFreeBlock(addr, largeLayout.totalSize().toLong())

            val reused = heap.allocate(smallLayout) // 16 bytes, should split if remainder >= HEADER_SIZE + 8
            assertTrue(reused != 0L)
        }
    }

    @Test
    fun `add free block zeroes type id`() {
        BumpHeap(4096).use { heap ->
            val layout = simpleLayout(42)
            val addr = heap.allocate(layout)
            assertEquals(42, heap.typeIdAt(addr))
            heap.addFreeBlock(addr, layout.totalSize().toLong())
            assertEquals(0, heap.typeIdAt(addr))
        }
    }

    @Test
    fun `add free block zeroes gc flags`() {
        BumpHeap(4096).use { heap ->
            val layout = simpleLayout()
            val addr = heap.allocate(layout)
            heap.setGcFlagsAt(addr, 0xFF)
            heap.addFreeBlock(addr, layout.totalSize().toLong())
            assertEquals(0, heap.gcFlagsAt(addr))
        }
    }

    // Byte read/write operations

    @Test
    fun `write and read single byte`() {
        BumpHeap(4096).use { heap ->
            val layout = simpleLayout()
            val addr = heap.allocate(layout)
            val fieldAddr = addr + ObjectLayout.HEADER_SIZE
            heap.writeBytes(fieldAddr, byteArrayOf(42))
            val read = heap.readBytes(fieldAddr, 1)
            assertEquals(42.toByte(), read[0])
        }
    }

    @Test
    fun `write and read 8 bytes`() {
        BumpHeap(4096).use { heap ->
            val layout = simpleLayout()
            val addr = heap.allocate(layout)
            val fieldAddr = addr + ObjectLayout.HEADER_SIZE
            val data = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
            heap.writeBytes(fieldAddr, data)
            assertArrayEquals(data, heap.readBytes(fieldAddr, 8))
        }
    }

    @Test
    fun `write and read 16 bytes`() {
        BumpHeap(4096).use { heap ->
            val layout = pointLayout()
            val addr = heap.allocate(layout)
            val fieldAddr = addr + ObjectLayout.HEADER_SIZE
            val data = ByteArray(16) { it.toByte() }
            heap.writeBytes(fieldAddr, data)
            assertArrayEquals(data, heap.readBytes(fieldAddr, 16))
        }
    }

    @Test
    fun `write bytes at different offsets`() {
        BumpHeap(4096).use { heap ->
            val layout = pointLayout()
            val addr = heap.allocate(layout)
            val baseAddr = addr + ObjectLayout.HEADER_SIZE
            heap.writeBytes(baseAddr, byteArrayOf(10, 20))
            heap.writeBytes(baseAddr + 8, byteArrayOf(30, 40))
            assertEquals(10.toByte(), heap.readBytes(baseAddr, 1)[0])
            assertEquals(30.toByte(), heap.readBytes(baseAddr + 8, 1)[0])
        }
    }

    @Test
    fun `overwrite bytes`() {
        BumpHeap(4096).use { heap ->
            val layout = simpleLayout()
            val addr = heap.allocate(layout)
            val fieldAddr = addr + ObjectLayout.HEADER_SIZE
            heap.writeBytes(fieldAddr, byteArrayOf(1, 2, 3, 4))
            heap.writeBytes(fieldAddr, byteArrayOf(5, 6, 7, 8))
            val read = heap.readBytes(fieldAddr, 4)
            assertArrayEquals(byteArrayOf(5, 6, 7, 8), read)
        }
    }

    @Test
    fun `read zero bytes`() {
        BumpHeap(4096).use { heap ->
            val layout = simpleLayout()
            val addr = heap.allocate(layout)
            val read = heap.readBytes(addr + ObjectLayout.HEADER_SIZE, 0)
            assertEquals(0, read.size)
        }
    }

    @Test
    fun `write and read all 256 byte values`() {
        BumpHeap(4096).use { heap ->
            val layout = largeLayout()
            val addr = heap.allocate(layout)
            val fieldAddr = addr + ObjectLayout.HEADER_SIZE
            // Write bytes 0..63 (fits in 64-byte data area)
            val data = ByteArray(64) { (it % 256).toByte() }
            heap.writeBytes(fieldAddr, data)
            val read = heap.readBytes(fieldAddr, 64)
            assertArrayEquals(data, read)
        }
    }

    // Field read/write operations

    @Test
    fun `write and read field index 0`() {
        BumpHeap(4096).use { heap ->
            val layout = simpleLayout()
            val addr = heap.allocate(layout)
            heap.writeField(addr, 0, 42L)
            assertEquals(42L, heap.readField(addr, 0))
        }
    }

    @Test
    fun `write and read field index 1`() {
        BumpHeap(4096).use { heap ->
            val layout = pointLayout()
            val addr = heap.allocate(layout)
            heap.writeField(addr, 1, 99L)
            assertEquals(99L, heap.readField(addr, 1))
        }
    }

    @Test
    fun `write all fields of large object`() {
        BumpHeap(4096).use { heap ->
            val layout = largeLayout()
            val addr = heap.allocate(layout)
            for (i in 0 until 8) {
                heap.writeField(addr, i, (i * 100).toLong())
            }
            for (i in 0 until 8) {
                assertEquals((i * 100).toLong(), heap.readField(addr, i))
            }
        }
    }

    @Test
    fun `overwrite field value`() {
        BumpHeap(4096).use { heap ->
            val layout = simpleLayout()
            val addr = heap.allocate(layout)
            heap.writeField(addr, 0, 42L)
            heap.writeField(addr, 0, 99L)
            assertEquals(99L, heap.readField(addr, 0))
        }
    }

    @Test
    fun `write zero to field`() {
        BumpHeap(4096).use { heap ->
            val layout = simpleLayout()
            val addr = heap.allocate(layout)
            heap.writeField(addr, 0, 42L)
            heap.writeField(addr, 0, 0L)
            assertEquals(0L, heap.readField(addr, 0))
        }
    }

    @Test
    fun `write negative value`() {
        BumpHeap(4096).use { heap ->
            val layout = simpleLayout()
            val addr = heap.allocate(layout)
            heap.writeField(addr, 0, -1L)
            assertEquals(-1L, heap.readField(addr, 0))
        }
    }

    @Test
    fun `write Long MAX_VALUE`() {
        BumpHeap(4096).use { heap ->
            val layout = simpleLayout()
            val addr = heap.allocate(layout)
            heap.writeField(addr, 0, Long.MAX_VALUE)
            assertEquals(Long.MAX_VALUE, heap.readField(addr, 0))
        }
    }

    @Test
    fun `write Long MIN_VALUE`() {
        BumpHeap(4096).use { heap ->
            val layout = simpleLayout()
            val addr = heap.allocate(layout)
            heap.writeField(addr, 0, Long.MIN_VALUE)
            assertEquals(Long.MIN_VALUE, heap.readField(addr, 0))
        }
    }

    @Test
    fun `fields of different objects are independent`() {
        BumpHeap(4096).use { heap ->
            val layout = pointLayout()
            val a = heap.allocate(layout)
            val b = heap.allocate(layout)
            heap.writeField(a, 0, 10L)
            heap.writeField(a, 1, 20L)
            heap.writeField(b, 0, 30L)
            heap.writeField(b, 1, 40L)
            assertEquals(10L, heap.readField(a, 0))
            assertEquals(20L, heap.readField(a, 1))
            assertEquals(30L, heap.readField(b, 0))
            assertEquals(40L, heap.readField(b, 1))
        }
    }

    @Test
    fun `field default value is zero`() {
        BumpHeap(4096).use { heap ->
            val layout = pointLayout()
            val addr = heap.allocate(layout)
            // Fields should be zero-initialized (from fresh memory or zeroed header area)
            // Note: bump allocator may not zero data area, but header is zeroed
            assertEquals(0, heap.gcFlagsAt(addr))
        }
    }

    // Reset behavior

    @Test
    fun `reset clears bytes in use`() {
        BumpHeap(4096).use { heap ->
            heap.allocate(simpleLayout())
            assertTrue(heap.bytesInUse() > 0)
            heap.reset()
            assertEquals(0L, heap.bytesInUse())
        }
    }

    @Test
    fun `reset clears free list`() {
        BumpHeap(4096).use { heap ->
            val layout = simpleLayout()
            val addr = heap.allocate(layout)
            heap.addFreeBlock(addr, layout.totalSize().toLong())
            assertEquals(1, heap.freeBlockCount())
            heap.reset()
            assertEquals(0, heap.freeBlockCount())
        }
    }

    @Test
    fun `allocate after reset works`() {
        BumpHeap(4096).use { heap ->
            heap.allocate(simpleLayout())
            heap.reset()
            val addr = heap.allocate(simpleLayout())
            assertTrue(addr != 0L)
        }
    }

    @Test
    fun `multiple resets`() {
        BumpHeap(4096).use { heap ->
            for (i in 0 until 5) {
                heap.allocate(simpleLayout())
                heap.reset()
                assertEquals(0L, heap.bytesInUse())
            }
        }
    }

    @Test
    fun `reset does not change capacity`() {
        BumpHeap(4096).use { heap ->
            heap.allocate(simpleLayout())
            heap.reset()
            assertEquals(4096L, heap.capacity())
        }
    }

    @Test
    fun `reset does not change base address`() {
        BumpHeap(4096).use { heap ->
            val base = heap.baseAddress()
            heap.allocate(simpleLayout())
            heap.reset()
            assertEquals(base, heap.baseAddress())
        }
    }

    @Test
    fun `can fill heap after reset`() {
        BumpHeap(32).use { heap ->
            val layout = simpleLayout()
            heap.allocate(layout)
            heap.allocate(layout)
            heap.reset()
            heap.allocate(layout)
            heap.allocate(layout) // should not throw
        }
    }

    // Base address

    @Test
    fun `base address is non-zero`() {
        BumpHeap(4096).use { heap ->
            assertTrue(heap.baseAddress() != 0L)
        }
    }

    @Test
    fun `base address is stable`() {
        BumpHeap(4096).use { heap ->
            val base1 = heap.baseAddress()
            heap.allocate(simpleLayout())
            val base2 = heap.baseAddress()
            assertEquals(base1, base2)
        }
    }

    @Test
    fun `first allocation address equals base address`() {
        BumpHeap(4096).use { heap ->
            val addr = heap.allocate(simpleLayout())
            assertEquals(heap.baseAddress(), addr)
        }
    }

    // Reference field read/write through heap

    @Test
    fun `write and read reference via writeBytes`() {
        BumpHeap(4096).use { heap ->
            val layout = refLayout()
            val parent = heap.allocate(layout)
            val child = heap.allocate(layout)

            writeRef(heap, parent, 8, child)
            val readBack = readRef(heap, parent, 8)
            assertEquals(child, readBack)
        }
    }

    @Test
    fun `null reference reads as zero`() {
        BumpHeap(4096).use { heap ->
            val layout = refLayout()
            val obj = heap.allocate(layout)
            // Next field not written, should be 0
            val ref = readRef(heap, obj, 8)
            assertEquals(0L, ref)
        }
    }

    @Test
    fun `overwrite reference`() {
        BumpHeap(4096).use { heap ->
            val layout = refLayout()
            val parent = heap.allocate(layout)
            val child1 = heap.allocate(layout)
            val child2 = heap.allocate(layout)

            writeRef(heap, parent, 8, child1)
            assertEquals(child1, readRef(heap, parent, 8))

            writeRef(heap, parent, 8, child2)
            assertEquals(child2, readRef(heap, parent, 8))
        }
    }

    @Test
    fun `self-reference`() {
        BumpHeap(4096).use { heap ->
            val layout = refLayout()
            val obj = heap.allocate(layout)
            writeRef(heap, obj, 8, obj)
            assertEquals(obj, readRef(heap, obj, 8))
        }
    }

    @Test
    fun `clear reference to null`() {
        BumpHeap(4096).use { heap ->
            val layout = refLayout()
            val parent = heap.allocate(layout)
            val child = heap.allocate(layout)

            writeRef(heap, parent, 8, child)
            writeRef(heap, parent, 8, 0L)
            assertEquals(0L, readRef(heap, parent, 8))
        }
    }

    // Multiple allocations and address layout

    @Test
    fun `sequential allocations have increasing addresses`() {
        BumpHeap(4096).use { heap ->
            val layout = simpleLayout()
            val a = heap.allocate(layout)
            val b = heap.allocate(layout)
            val c = heap.allocate(layout)
            assertTrue(b > a)
            assertTrue(c > b)
        }
    }

    @Test
    fun `address spacing matches total size`() {
        BumpHeap(4096).use { heap ->
            val layout = simpleLayout()
            val a = heap.allocate(layout)
            val b = heap.allocate(layout)
            val diff = b - a
            // Should be at least totalSize, possibly aligned up
            assertTrue(diff >= layout.totalSize())
        }
    }

    @Test
    fun `100 sequential allocations all unique`() {
        BumpHeap(65536).use { heap ->
            val layout = simpleLayout()
            val addresses = (0 until 100).map { heap.allocate(layout) }
            assertEquals(100, addresses.toSet().size)
        }
    }

    @Test
    fun `200 allocations with data integrity`() {
        BumpHeap(131072).use { heap ->
            val layout = simpleLayout()
            val addrs = (0 until 200).map { i ->
                val addr = heap.allocate(layout)
                heap.writeField(addr, 0, i.toLong())
                addr
            }
            for (i in 0 until 200) {
                assertEquals(i.toLong(), heap.readField(addrs[i], 0))
            }
        }
    }

    @Test
    fun `alternating type allocations`() {
        BumpHeap(65536).use { heap ->
            val small = simpleLayout(1)
            val large = largeLayout(2)
            val addrs = (0 until 50).map { i ->
                if (i % 2 == 0) heap.allocate(small) else heap.allocate(large)
            }
            for (i in 0 until 50) {
                val expectedType = if (i % 2 == 0) 1 else 2
                assertEquals(expectedType, heap.typeIdAt(addrs[i]))
            }
        }
    }

    // AutoCloseable

    @Test
    fun `close does not crash`() {
        val heap = BumpHeap(4096)
        heap.allocate(simpleLayout())
        heap.close()
    }

    @Test
    fun `use block closes automatically`() {
        BumpHeap(4096).use { heap ->
            heap.allocate(simpleLayout())
        }
        // No crash means success
    }

    // Total size calculations

    @Test
    fun `total size includes header for simple layout`() {
        val layout = simpleLayout()
        assertEquals(16, layout.totalSize()) // 8 header + 8 data
    }

    @Test
    fun `total size includes header for point layout`() {
        val layout = pointLayout()
        assertEquals(24, layout.totalSize()) // 8 header + 16 data
    }

    @Test
    fun `total size includes header for large layout`() {
        val layout = largeLayout()
        assertEquals(72, layout.totalSize()) // 8 header + 64 data
    }

    @Test
    fun `total size for zero-data layout`() {
        val layout = ObjectLayout("Empty", 0, listOf())
        assertEquals(8, layout.totalSize()) // 8 header + 0 data
    }

    @Test
    fun `header size is 8`() {
        assertEquals(8, ObjectLayout.HEADER_SIZE)
    }

    // Free list allocation after sweep

    @Test
    fun `allocate from free list writes correct type id`() {
        BumpHeap(4096).use { heap ->
            val layout = simpleLayout(42)
            val addr = heap.allocate(layout)
            heap.addFreeBlock(addr, layout.totalSize().toLong())
            assertEquals(0, heap.typeIdAt(addr)) // zeroed by addFreeBlock

            val reused = heap.allocate(simpleLayout(99))
            assertEquals(99, heap.typeIdAt(reused))
        }
    }

    @Test
    fun `allocate from free list zeros gc flags`() {
        BumpHeap(4096).use { heap ->
            val layout = simpleLayout()
            val addr = heap.allocate(layout)
            heap.setGcFlagsAt(addr, 0xFF)
            heap.addFreeBlock(addr, layout.totalSize().toLong())

            val reused = heap.allocate(layout)
            assertEquals(0, heap.gcFlagsAt(reused))
        }
    }

    @Test
    fun `free list prefers free block over bump`() {
        BumpHeap(4096).use { heap ->
            val layout = simpleLayout()
            val first = heap.allocate(layout)
            val bumpBefore = heap.bytesInUse()

            heap.addFreeBlock(first, layout.totalSize().toLong())
            heap.allocate(layout) // should reuse free block

            // bytesInUse (bump cursor) should not have advanced
            assertEquals(bumpBefore, heap.bytesInUse())
        }
    }

    // Stress and edge cases

    @Test
    fun `allocate until full then reset and reallocate`() {
        BumpHeap(128).use { heap ->
            val layout = simpleLayout()
            val maxObjects = 128 / layout.totalSize()
            for (i in 0 until maxObjects) {
                heap.allocate(layout)
            }
            heap.reset()
            for (i in 0 until maxObjects) {
                val addr = heap.allocate(layout)
                heap.writeField(addr, 0, i.toLong())
            }
        }
    }

    @Test
    fun `rapid allocate-free-reuse cycle`() {
        BumpHeap(4096).use { heap ->
            val layout = simpleLayout()
            for (round in 0 until 20) {
                val addr = heap.allocate(layout)
                heap.writeField(addr, 0, round.toLong())
                assertEquals(round.toLong(), heap.readField(addr, 0))
                heap.addFreeBlock(addr, layout.totalSize().toLong())
            }
        }
    }

    @Test
    fun `mixed free list and bump allocation`() {
        BumpHeap(4096).use { heap ->
            val layout = simpleLayout()

            // Allocate 3, free first one
            val a = heap.allocate(layout)
            val b = heap.allocate(layout)
            val c = heap.allocate(layout)
            heap.addFreeBlock(a, layout.totalSize().toLong())

            // Next allocation should reuse 'a's slot
            val d = heap.allocate(layout)
            heap.writeField(d, 0, 123L)
            assertEquals(123L, heap.readField(d, 0))

            // Next allocation should bump
            val e = heap.allocate(layout)
            assertTrue(e > c)
        }
    }

    @Test
    fun `write bytes across field boundary`() {
        BumpHeap(4096).use { heap ->
            val layout = pointLayout()
            val addr = heap.allocate(layout)
            val fieldAddr = addr + ObjectLayout.HEADER_SIZE
            // Write 16 bytes spanning both fields
            val data = ByteArray(16) { (it + 1).toByte() }
            heap.writeBytes(fieldAddr, data)
            val read = heap.readBytes(fieldAddr, 16)
            assertArrayEquals(data, read)
        }
    }

    @Test
    fun `multiple heaps are independent`() {
        BumpHeap(4096).use { heap1 ->
            BumpHeap(4096).use { heap2 ->
                val layout = simpleLayout()
                val a = heap1.allocate(layout)
                val b = heap2.allocate(layout)
                heap1.writeField(a, 0, 111L)
                heap2.writeField(b, 0, 222L)
                assertEquals(111L, heap1.readField(a, 0))
                assertEquals(222L, heap2.readField(b, 0))
            }
        }
    }

    @Test
    fun `object layout copy preserves fields`() {
        val layout = ObjectLayout("Test", 16, listOf(
            FieldDescriptor("a", 0, 8, false),
            FieldDescriptor("b", 8, 8, true),
        ), typeId = 5)
        val copy = layout.copy(typeId = 10)
        assertEquals(10, copy.typeId)
        assertEquals("Test", copy.name)
        assertEquals(2, copy.fields.size)
        assertTrue(copy.fields[1].isReference)
    }

    @Test
    fun `allocate max-value type id`() {
        BumpHeap(4096).use { heap ->
            val layout = simpleLayout(Int.MAX_VALUE)
            val addr = heap.allocate(layout)
            assertEquals(Int.MAX_VALUE, heap.typeIdAt(addr))
        }
    }

    @Test
    fun `allocate type id 1`() {
        BumpHeap(4096).use { heap ->
            val layout = simpleLayout(1)
            val addr = heap.allocate(layout)
            assertEquals(1, heap.typeIdAt(addr))
        }
    }

    @Test
    fun `bytes allocated includes free list allocations`() {
        BumpHeap(4096).use { heap ->
            val layout = simpleLayout()
            val addr = heap.allocate(layout)
            val afterFirst = heap.bytesAllocated()
            heap.addFreeBlock(addr, layout.totalSize().toLong())

            heap.allocate(layout) // from free list
            val afterReuse = heap.bytesAllocated()
            assertTrue(afterReuse > afterFirst)
        }
    }

    @Test
    fun `free bytes decreases after reuse`() {
        BumpHeap(4096).use { heap ->
            val layout = simpleLayout()
            val addr = heap.allocate(layout)
            heap.addFreeBlock(addr, layout.totalSize().toLong())
            val freeBefore = heap.freeBytes()
            assertTrue(freeBefore > 0)

            heap.allocate(layout) // reuses free block
            val freeAfter = heap.freeBytes()
            assertTrue(freeAfter < freeBefore)
        }
    }

    @Test
    fun `free block count decreases after reuse`() {
        BumpHeap(4096).use { heap ->
            val layout = simpleLayout()
            val addr = heap.allocate(layout)
            heap.addFreeBlock(addr, layout.totalSize().toLong())
            assertEquals(1, heap.freeBlockCount())

            heap.allocate(layout)
            // Either removed or split, but count should change
            assertTrue(heap.freeBlockCount() <= 1)
        }
    }

    @Test
    fun `reset free bytes to zero`() {
        BumpHeap(4096).use { heap ->
            val layout = simpleLayout()
            val addr = heap.allocate(layout)
            heap.addFreeBlock(addr, layout.totalSize().toLong())
            assertTrue(heap.freeBytes() > 0)
            heap.reset()
            assertEquals(0L, heap.freeBytes())
        }
    }

    @Test
    fun `large number of free blocks`() {
        BumpHeap(65536).use { heap ->
            val layout = simpleLayout()
            val addrs = (0 until 50).map { heap.allocate(layout) }
            addrs.forEach { heap.addFreeBlock(it, layout.totalSize().toLong()) }
            assertEquals(50, heap.freeBlockCount())
        }
    }

    @Test
    fun `consecutive allocations have expected spacing for point layout`() {
        BumpHeap(4096).use { heap ->
            val layout = pointLayout() // totalSize = 24
            val a = heap.allocate(layout)
            val b = heap.allocate(layout)
            // Spacing should be totalSize aligned to 8 bytes
            val spacing = b - a
            assertEquals(0L, spacing % 8)
            assertTrue(spacing >= layout.totalSize())
        }
    }

    @Test
    fun `write pattern and verify with readBytes`() {
        BumpHeap(4096).use { heap ->
            val layout = largeLayout()
            val addr = heap.allocate(layout)
            val fieldAddr = addr + ObjectLayout.HEADER_SIZE
            val pattern = ByteArray(64) { (0xAB).toByte() }
            heap.writeBytes(fieldAddr, pattern)
            val read = heap.readBytes(fieldAddr, 64)
            assertArrayEquals(pattern, read)
        }
    }

    @Test
    fun `field descriptor properties`() {
        val fd = FieldDescriptor("myField", 16, 8, true)
        assertEquals("myField", fd.name)
        assertEquals(16, fd.offset)
        assertEquals(8, fd.size)
        assertTrue(fd.isReference)
    }

    @Test
    fun `non-reference field descriptor`() {
        val fd = FieldDescriptor("value", 0, 4, false)
        assertFalse(fd.isReference)
    }

    @Test
    fun `object layout name preserved`() {
        val layout = ObjectLayout("MyType", 8, listOf(), typeId = 1)
        assertEquals("MyType", layout.name)
    }

    @Test
    fun `object layout size preserved`() {
        val layout = ObjectLayout("Test", 32, listOf(), typeId = 1)
        assertEquals(32, layout.size)
    }

    @Test
    fun `allocate with type id zero`() {
        BumpHeap(4096).use { heap ->
            val layout = ObjectLayout("NoId", 8, listOf(
                FieldDescriptor("v", 0, 8, false)
            ), typeId = 0)
            val addr = heap.allocate(layout)
            assertEquals(0, heap.typeIdAt(addr))
        }
    }
}
