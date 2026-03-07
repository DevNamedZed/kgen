package org.kgen.jit.runtime

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
) {
    /** Total size including object header. */
    fun totalSize(): Int = HEADER_SIZE + size

    companion object {
        /** Object header: type ID (4 bytes) + GC flags (4 bytes). */
        const val HEADER_SIZE = 8
    }
}

/**
 * Describes a single field within an [ObjectLayout].
 */
data class FieldDescriptor(
    val name: String,
    val offset: Int,
    val size: Int,
    val isReference: Boolean,
)
