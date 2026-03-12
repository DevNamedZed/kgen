package org.kgen.runtime.gc

/**
 * Manages the heap for compiled code. Responsible for allocating objects,
 * tracking live references, and providing metadata for the garbage collector.
 *
 * ```java
 * HeapManager heap = HeapManager.bump(1024 * 1024); // 1MB bump allocator
 * long addr = heap.allocate(layout);
 * heap.writeField(addr, 0, 42L);
 * ```
 */
interface HeapManager : AutoCloseable {

    /** Allocate an object with the given layout. Returns the object's address. */
    fun allocate(layout: ObjectLayout): Long

    /** Read a field from an object at the given address. */
    fun readField(address: Long, fieldIndex: Int): Long

    /** Write a field to an object at the given address. */
    fun writeField(address: Long, fieldIndex: Int, value: Long)

    /** Read raw bytes from heap memory. */
    fun readBytes(address: Long, length: Int): ByteArray

    /** Write raw bytes to heap memory. */
    fun writeBytes(address: Long, data: ByteArray)

    /** Total bytes allocated since creation. */
    fun bytesAllocated(): Long

    /** Total bytes currently in use (after GC). */
    fun bytesInUse(): Long
}
