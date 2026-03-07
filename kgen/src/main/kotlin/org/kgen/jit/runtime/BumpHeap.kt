package org.kgen.jit.runtime

import org.kgen.reflect.NativeMemory

/**
 * Simple bump allocator for the managed heap. Allocates sequentially from a
 * contiguous memory region. Does not free individual objects — relies on the
 * garbage collector to reclaim memory via [compact] or region reuse.
 *
 * Thread safety: not thread-safe. Use one per thread or synchronize externally.
 */
class BumpHeap(private val capacity: Long) : HeapManager {

    private val memory = NativeMemory.allocateReadWrite(capacity)
    private var cursor: Long = 0
    private var totalAllocated: Long = 0

    override fun allocate(layout: ObjectLayout): Long {
        val totalSize = layout.totalSize().toLong()
        val aligned = (cursor + 7) and 7L.inv()
        if (aligned + totalSize > capacity) {
            throw OutOfMemoryError("Heap exhausted: requested $totalSize bytes, ${capacity - aligned} available")
        }
        val address = memory.address + aligned
        cursor = aligned + totalSize
        totalAllocated += totalSize

        // Write object header: type ID + GC flags (mark bit = 0)
        memory.writeInt(aligned, layout.typeId)
        memory.writeInt(aligned + 4, 0) // GC flags

        return address
    }

    override fun readField(address: Long, fieldIndex: Int): Long {
        val offset = address - memory.address + ObjectLayout.HEADER_SIZE
        return memory.readLong(offset + fieldIndex.toLong() * 8)
    }

    override fun writeField(address: Long, fieldIndex: Int, value: Long) {
        val offset = address - memory.address + ObjectLayout.HEADER_SIZE
        memory.writeLong(offset + fieldIndex.toLong() * 8, value)
    }

    override fun readBytes(address: Long, length: Int): ByteArray {
        val offset = address - memory.address
        return memory.read(offset, length)
    }

    override fun writeBytes(address: Long, data: ByteArray) {
        val offset = address - memory.address
        memory.write(offset, data)
    }

    override fun bytesAllocated(): Long = totalAllocated

    override fun bytesInUse(): Long = cursor

    /** Reset the cursor — used after GC compaction. */
    fun reset() {
        cursor = 0
    }

    /** The base address of the heap region. */
    fun baseAddress(): Long = memory.address

    /** The capacity in bytes. */
    fun capacity(): Long = capacity

    /** Read the type ID from an object header. */
    fun typeIdAt(address: Long): Int {
        val offset = address - memory.address
        return memory.readInt(offset)
    }

    /** Read GC flags from an object header. */
    fun gcFlagsAt(address: Long): Int {
        val offset = address - memory.address
        return memory.readInt(offset + 4)
    }

    /** Write GC flags to an object header. */
    fun setGcFlagsAt(address: Long, flags: Int) {
        val offset = address - memory.address
        memory.writeInt(offset + 4, flags)
    }

    override fun close() {
        memory.close()
    }
}
