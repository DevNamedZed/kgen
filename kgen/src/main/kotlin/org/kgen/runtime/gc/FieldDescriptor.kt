package org.kgen.runtime.gc

/**
 * Describes a single field within an [ObjectLayout].
 */
data class FieldDescriptor(
    val name: String,
    val offset: Int,
    val size: Int,
    val isReference: Boolean,
)
