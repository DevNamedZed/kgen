package org.kgen.runtime.gc

/**
 * Describes the memory layout of a heap-allocated object.
 * Used by [HeapManager] for allocation and by [GarbageCollector] for scanning.
 *
 * ```java
 * var layout = new ObjectLayout("Point", 24,
 *     List.of(new FieldDescriptor("x", 8, 8, false),
 *             new FieldDescriptor("y", 16, 8, false)));
 * ```
 */
data class ObjectLayout(
    val name: String,
    val size: Int,
    val fields: List<FieldDescriptor>,
    val typeId: Int = 0,
    /** Alignment requirement in bytes. Must be a power of two. Default 8 (pointer-aligned). */
    val alignment: Int = 8,
) {
    /** Total size including object header, rounded up to alignment. */
    fun totalSize(): Int {
        val raw = HEADER_SIZE + size
        return if (alignment <= 1) raw else (raw + alignment - 1) and (alignment - 1).inv()
    }

    companion object {
        /** Object header: type ID (4 bytes) + GC flags (4 bytes). */
        const val HEADER_SIZE = 8
    }
}
