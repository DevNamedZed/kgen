package org.wark

import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.nio.ByteOrder

/**
 * WASM linear memory. A resizable byte array with page-granular growth.
 *
 * One page = 64 KiB (65,536 bytes). Memory grows in whole pages.
 * Backed by FFM [Arena]-allocated native memory for explicit lifetime control.
 * JIT code accesses memory through a base pointer — no GC can free
 * the underlying memory while the instance is alive.
 *
 * ```java
 * var memory = WarkMemory.create(1, 256);  // 1 initial page, max 256 pages (16 MB)
 * memory.writeI32(0, 42);
 * int value = memory.readI32(0);  // 42
 * ```
 */
class WarkMemory(
    initialPages: Int,
    val maxPages: Int,
) : AutoCloseable {
    private val arena = Arena.ofShared()
    private var segment: MemorySegment
    private var pageCount: Int = initialPages

    init {
        // Browsers map the full 4GB WASM address space, allowing reads beyond
        // the logical size (returning zeros). DOOM relies on this behavior.
        // Try full allocation, fall back to smaller if memory is insufficient.
        val preAllocPages = maxPages.coerceAtMost(1024)
        segment = arena.allocate(preAllocPages.toLong() * PAGE_SIZE, 8)
    }

    fun pages(): Int = pageCount

    fun sizeBytes(): Int = pageCount * PAGE_SIZE
    fun physicalSizeBytes(): Long = segment.byteSize()

    /**
     * Grow memory by [deltaPages]. Returns the previous page count, or -1 if
     * the growth would exceed [maxPages].
     *
     * Memory is pre-allocated up to maxPages at construction, so grow never
     * changes the base address. This is critical for JIT correctness — native
     * code holds raw pointers to the memory base.
     */
    fun grow(deltaPages: Int): Int {
        val newPageCount = pageCount + deltaPages
        if (newPageCount > maxPages) {
            return -1
        }
        if (newPageCount.toLong() * PAGE_SIZE > segment.byteSize()) {
            // Need to reallocate (exceeded pre-allocation)
            val oldSize = pageCount.toLong() * PAGE_SIZE
            val newSize = newPageCount.toLong() * PAGE_SIZE
            val newSegment = arena.allocate(newSize, 8)
            MemorySegment.copy(segment, 0, newSegment, 0, oldSize)
            segment = newSegment
        }
        val oldPageCount = pageCount
        pageCount = newPageCount
        return oldPageCount
    }

    /**
     * Returns the base address of the native memory. JIT code uses this
     * as the memory base pointer in the RuntimeContext.
     */
    fun baseAddress(): Long = segment.address()

    /**
     * Returns the backing memory segment.
     */
    fun backingSegment(): MemorySegment = segment

    // -- Read --

    fun readByte(offset: Int): Byte {
        checkBounds(offset, 1)
        return segment.get(ValueLayout.JAVA_BYTE, offset.toLong())
    }

    fun readI32(offset: Int): Int {
        checkBounds(offset, 4)
        return segment.get(LITTLE_ENDIAN_INT, offset.toLong())
    }

    fun readI64(offset: Int): Long {
        checkBounds(offset, 8)
        return segment.get(LITTLE_ENDIAN_LONG, offset.toLong())
    }

    fun readF32(offset: Int): Float {
        checkBounds(offset, 4)
        return segment.get(LITTLE_ENDIAN_FLOAT, offset.toLong())
    }

    fun readF64(offset: Int): Double {
        checkBounds(offset, 8)
        return segment.get(LITTLE_ENDIAN_DOUBLE, offset.toLong())
    }

    fun readBytes(offset: Int, length: Int): ByteArray {
        checkBounds(offset, length)
        val result = ByteArray(length)
        MemorySegment.copy(segment, ValueLayout.JAVA_BYTE, offset.toLong(), result, 0, length)
        return result
    }

    fun readUtf8(offset: Int): String {
        var end = offset
        while (end < sizeBytes() && segment.get(ValueLayout.JAVA_BYTE, end.toLong()) != 0.toByte()) {
            end++
        }
        val length = end - offset
        val bytes = ByteArray(length)
        MemorySegment.copy(segment, ValueLayout.JAVA_BYTE, offset.toLong(), bytes, 0, length)
        return String(bytes, Charsets.UTF_8)
    }

    // -- Write --

    fun writeByte(offset: Int, value: Byte) {
        checkBounds(offset, 1)
        checkWatch(offset, 1)
        segment.set(ValueLayout.JAVA_BYTE, offset.toLong(), value)
    }

    var watchAddress = -1
    var watchCallback: ((Int, Int, Int) -> Unit)? = null

    private fun checkWatch(offset: Int, size: Int) {
        if (watchAddress >= 0 && watchAddress < offset + size && watchAddress + 4 > offset) {
            val oldValue = readI32(watchAddress)
            watchCallback?.invoke(offset, 0, oldValue)
        }
    }

    fun writeI32(offset: Int, value: Int) {
        checkBounds(offset, 4)
        if (watchAddress in offset until offset + 4) {
            watchCallback?.invoke(offset, value, readI32(watchAddress))
        }
        segment.set(LITTLE_ENDIAN_INT, offset.toLong(), value)
    }

    fun writeI64(offset: Int, value: Long) {
        checkBounds(offset, 8)
        checkWatch(offset, 8)
        segment.set(LITTLE_ENDIAN_LONG, offset.toLong(), value)
    }

    fun writeF32(offset: Int, value: Float) {
        checkBounds(offset, 4)
        segment.set(LITTLE_ENDIAN_FLOAT, offset.toLong(), value)
    }

    fun writeF64(offset: Int, value: Double) {
        checkBounds(offset, 8)
        segment.set(LITTLE_ENDIAN_DOUBLE, offset.toLong(), value)
    }

    fun writeBytes(offset: Int, data: ByteArray) {
        checkBounds(offset, data.size)
        checkWatch(offset, data.size)
        MemorySegment.copy(data, 0, segment, ValueLayout.JAVA_BYTE, offset.toLong(), data.size)
    }

    fun writeUtf8(offset: Int, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeBytes(offset, bytes)
        writeByte(offset + bytes.size, 0)
    }

    // -- Bulk --

    fun fill(offset: Int, value: Byte, length: Int) {
        checkBounds(offset, length)
        checkWatch(offset, length)
        segment.asSlice(offset.toLong(), length.toLong()).fill(value)
    }

    fun copy(destination: Int, source: Int, length: Int) {
        checkBounds(source, length)
        checkBounds(destination, length)
        val temp = ByteArray(length)
        MemorySegment.copy(segment, ValueLayout.JAVA_BYTE, source.toLong(), temp, 0, length)
        MemorySegment.copy(temp, 0, segment, ValueLayout.JAVA_BYTE, destination.toLong(), length)
    }

    override fun close() {
        arena.close()
    }

    private fun checkBounds(offset: Int, length: Int) {
        if (offset < 0 || offset + length > sizeBytes()) {
            val caller = Thread.currentThread().stackTrace[3].methodName
            throw WasmTrap("OOB in $caller: offset=$offset (0x${Integer.toHexString(offset)}), length=$length, size=${sizeBytes()}")
        }
    }

    companion object {
        const val PAGE_SIZE = 65536

        private val LITTLE_ENDIAN_INT = ValueLayout.JAVA_INT_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN)
        private val LITTLE_ENDIAN_LONG = ValueLayout.JAVA_LONG_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN)
        private val LITTLE_ENDIAN_FLOAT = ValueLayout.JAVA_FLOAT_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN)
        private val LITTLE_ENDIAN_DOUBLE = ValueLayout.JAVA_DOUBLE_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN)

        @JvmStatic
        fun create(initialPages: Int, maxPages: Int = 65536): WarkMemory = WarkMemory(initialPages, maxPages)
    }
}
