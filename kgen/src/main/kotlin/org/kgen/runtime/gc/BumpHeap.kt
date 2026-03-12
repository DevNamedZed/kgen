package org.kgen.runtime.gc

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

    // Free list: sorted by offset, entries are (heapOffset, size) pairs
    private val freeList = mutableListOf<FreeBlock>()

    private data class FreeBlock(val offset: Long, val size: Long)

    override fun allocate(layout: ObjectLayout): Long {
        val totalSize = layout.totalSize().toLong()

        // Try free list first (first-fit)
        val freeIdx = freeList.indexOfFirst { it.size >= totalSize }
        if (freeIdx >= 0) {
            val block = freeList[freeIdx]
            val address = memory.address + block.offset
            val remaining = block.size - totalSize
            if (remaining >= ObjectLayout.HEADER_SIZE + 8) {
                // Split: keep remainder in free list
                freeList[freeIdx] = FreeBlock(block.offset + totalSize, remaining)
            } else {
                freeList.removeAt(freeIdx)
            }
            // Write object header
            memory.writeInt(block.offset, layout.typeId)
            memory.writeInt(block.offset + 4, 0)
            totalAllocated += totalSize
            return address
        }

        // Fall back to bump allocation
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
        freeList.clear()
    }

    /** Add a free block for reuse by the allocator. Called by GC sweep. */
    fun addFreeBlock(address: Long, size: Long) {
        val offset = address - memory.address
        freeList.add(FreeBlock(offset, size))
        // Zero out the header so this block is recognizable as dead
        memory.writeInt(offset, 0) // typeId = 0 = dead
        memory.writeInt(offset + 4, 0) // gc flags = 0
    }

    /**
     * Compact live objects (those with mark bit set) toward the start of the heap.
     * Returns a forwarding map from old address to new address.
     * Clears the free list and resets the cursor to the end of compacted data.
     */
    fun compact(registry: TypeRegistry): Map<Long, Long> {
        val forwarding = mutableMapOf<Long, Long>()
        var readOffset = 0L
        var writeOffset = 0L
        val cap = cursor

        // Phase 1: compute forwarding addresses
        while (readOffset < cap) {
            val typeId = memory.readInt(readOffset)
            if (typeId == 0) {
                readOffset += 8
                continue
            }
            val layout = registry.lookup(typeId) ?: break
            val totalSize = layout.totalSize().toLong()
            val alignedSize = (totalSize + 7) and 7L.inv()
            val gcFlags = memory.readInt(readOffset + 4)

            if (gcFlags and MARK_BIT != 0) {
                val oldAddr = memory.address + readOffset
                val newAddr = memory.address + writeOffset
                if (oldAddr != newAddr) {
                    forwarding[oldAddr] = newAddr
                }
                writeOffset += alignedSize
            }

            readOffset += alignedSize
        }

        // Phase 2: slide live objects forward
        copyLiveObjects(cap, registry)

        // Phase 3: clear free list and update cursor
        freeList.clear()
        cursor = writeOffset

        return forwarding
    }

    private fun copyLiveObjects(cap: Long, registry: TypeRegistry) {
        var readOffset = 0L
        var writeOffset = 0L

        while (readOffset < cap) {
            val typeId = memory.readInt(readOffset)
            if (typeId == 0) {
                readOffset += 8
                continue
            }
            val layout = registry.lookup(typeId) ?: break
            val totalSize = layout.totalSize().toLong()
            val alignedSize = (totalSize + 7) and 7L.inv()
            val gcFlags = memory.readInt(readOffset + 4)

            if (gcFlags and MARK_BIT != 0) {
                if (writeOffset != readOffset) {
                    val data = memory.read(readOffset, alignedSize.toInt())
                    memory.write(writeOffset, data)
                }
                writeOffset += alignedSize
            }

            readOffset += alignedSize
        }
    }

    /** Number of blocks on the free list (for testing). */
    fun freeBlockCount(): Int = freeList.size

    /** Total bytes available in free list (for testing). */
    fun freeBytes(): Long = freeList.sumOf { it.size }

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

    companion object {
        private const val MARK_BIT = 1
    }
}
