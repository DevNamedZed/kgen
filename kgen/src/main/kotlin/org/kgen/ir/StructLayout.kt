package org.kgen.ir

import org.kgen.ir.target.Target

/**
 * Computes memory layout for [Type.Struct] and [Type.Union] types:
 * field offsets, alignment, padding, and total size.
 *
 * Uses the target's pointer size for pointer/reference types.
 * Follows C struct layout rules (natural alignment, padding between fields).
 *
 * ```java
 * var struct = Type.Struct(null, List.of(Type.I32, Type.I8, Type.I64), false);
 * var layout = StructLayout.of(struct, Target.x86_64());
 * layout.sizeInBytes();        // 16 (with padding)
 * layout.alignmentInBytes();   // 8
 * layout.fieldOffset(0);       // 0  (i32)
 * layout.fieldOffset(1);       // 4  (i8)
 * layout.fieldOffset(2);       // 8  (i64, aligned to 8)
 * ```
 */
class StructLayout private constructor(
    private val offsets: IntArray,
    private val size: Int,
    private val alignment: Int,
) {
    /** Total size of the struct in bytes (including trailing padding). */
    fun sizeInBytes(): Int = size

    /** Alignment requirement of the struct in bytes. */
    fun alignmentInBytes(): Int = alignment

    /** Number of fields. */
    fun fieldCount(): Int = offsets.size

    /** Byte offset of the field at [index]. */
    fun fieldOffset(index: Int): Int = offsets[index]

    /** All field offsets. */
    fun fieldOffsets(): IntArray = offsets.copyOf()

    override fun toString(): String = buildString {
        append("StructLayout(size=$size, align=$alignment, offsets=[")
        append(offsets.joinToString(", "))
        append("])")
    }

    companion object {
        /**
         * Compute the layout of a [Type.Struct].
         */
        @JvmStatic
        fun of(struct: Type.Struct, target: Target): StructLayout {
            return if (struct.packed) {
                computePacked(struct.fields, target)
            } else {
                computeAligned(struct.fields, target)
            }
        }

        /**
         * Compute the layout of a [Type.Union] (all fields at offset 0).
         */
        @JvmStatic
        fun ofUnion(union: Type.Union, target: Target): StructLayout {
            var maxSize = 0
            var maxAlign = 1
            for (v in union.variants) {
                val s = sizeOf(v, target)
                val a = alignOf(v, target)
                if (s > maxSize) maxSize = s
                if (a > maxAlign) maxAlign = a
            }
            val totalSize = alignUp(maxSize, maxAlign)
            return StructLayout(IntArray(union.variants.size), totalSize, maxAlign)
        }

        /**
         * Compute the size of any IR type in bytes.
         */
        @JvmStatic
        fun sizeOf(type: Type, target: Target): Int {
            return when (type) {
                Type.Void -> 0
                Type.I1 -> 1
                Type.I8 -> 1
                Type.I16 -> 2
                Type.I32 -> 4
                Type.I64 -> 8
                Type.I128 -> 16
                is Type.IntN -> (type.bits + 7) / 8
                Type.F16, Type.BF16 -> 2
                Type.F32 -> 4
                Type.F64 -> 8
                Type.F80 -> 10
                Type.F128 -> 16
                is Type.Pointer, Type.OpaquePointer -> target.pointerSize
                is Type.Reference, is Type.WeakReference, is Type.InteriorRef, is Type.PinnedRef ->
                    target.pointerSize
                is Type.Array -> sizeOf(type.element, target) * type.size.toInt()
                is Type.Vector -> sizeOf(type.element, target) * type.lanes
                is Type.Struct -> of(type, target).sizeInBytes()
                is Type.Union -> ofUnion(type, target).sizeInBytes()
                is Type.Function -> target.pointerSize
                is Type.ClassRef, is Type.InterfaceRef -> target.pointerSize
                is Type.Nullable -> target.pointerSize
                else -> target.pointerSize // fallback
            }
        }

        /**
         * Compute the alignment of any IR type in bytes.
         */
        @JvmStatic
        fun alignOf(type: Type, target: Target): Int {
            return when (type) {
                Type.Void -> 1
                Type.I1, Type.I8 -> 1
                Type.I16 -> 2
                Type.I32 -> 4
                Type.I64 -> 8
                Type.I128 -> 16
                is Type.IntN -> minOf((type.bits + 7) / 8, 8).let { if (it <= 0) 1 else it }
                Type.F16, Type.BF16 -> 2
                Type.F32 -> 4
                Type.F64 -> 8
                Type.F80 -> 16 // x86 long double alignment
                Type.F128 -> 16
                is Type.Pointer, Type.OpaquePointer -> target.pointerSize
                is Type.Reference, is Type.WeakReference, is Type.InteriorRef, is Type.PinnedRef ->
                    target.pointerSize
                is Type.Array -> alignOf(type.element, target)
                is Type.Vector -> {
                    val totalSize = sizeOf(type.element, target) * type.lanes
                    minOf(totalSize, 32) // vectors align to their total size, up to 32
                }
                is Type.Struct -> {
                    if (type.packed) 1
                    else type.fields.maxOfOrNull { alignOf(it, target) } ?: 1
                }
                is Type.Union -> type.variants.maxOfOrNull { alignOf(it, target) } ?: 1
                is Type.Function -> target.pointerSize
                is Type.ClassRef, is Type.InterfaceRef -> target.pointerSize
                is Type.Nullable -> target.pointerSize
                else -> target.pointerSize
            }
        }

        private fun computeAligned(fields: List<Type>, target: Target): StructLayout {
            val offsets = IntArray(fields.size)
            var offset = 0
            var maxAlign = 1

            for (i in fields.indices) {
                val fieldAlign = alignOf(fields[i], target)
                val fieldSize = sizeOf(fields[i], target)
                if (fieldAlign > maxAlign) maxAlign = fieldAlign
                offset = alignUp(offset, fieldAlign)
                offsets[i] = offset
                offset += fieldSize
            }

            val totalSize = alignUp(offset, maxAlign)
            return StructLayout(offsets, totalSize, maxAlign)
        }

        private fun computePacked(fields: List<Type>, target: Target): StructLayout {
            val offsets = IntArray(fields.size)
            var offset = 0
            for (i in fields.indices) {
                offsets[i] = offset
                offset += sizeOf(fields[i], target)
            }
            return StructLayout(offsets, offset, 1)
        }

        private fun alignUp(value: Int, alignment: Int): Int {
            return (value + alignment - 1) and (alignment - 1).inv()
        }
    }
}
