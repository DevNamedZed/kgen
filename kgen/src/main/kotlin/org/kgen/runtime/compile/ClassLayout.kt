package org.kgen.runtime.compile

import org.kgen.target.jvm.ClassFile
import org.kgen.target.jvm.JvmClassReader

/**
 * Pre-computed layout information for classes in a compilation unit.
 *
 * Object layout: [8-byte header (vtable/type ptr)][fields in declaration order...]
 * Each field is aligned to its natural size.
 */
class ClassLayout private constructor(
    private val classInfo: Map<String, ClassInfo>,
) {
    /** Total allocation size for an instance of [className], including header. */
    fun objectSize(className: String): Long {
        return classInfo[className]?.totalSize ?: DEFAULT_OBJECT_SIZE
    }

    /** Byte offset of [fieldName] within an instance of [className]. */
    fun fieldOffset(className: String, fieldName: String): Long? {
        return classInfo[className]?.fieldOffsets?.get(fieldName)
    }

    /** All known class names. */
    fun classNames(): Set<String> = classInfo.keys

    data class ClassInfo(
        val name: String,
        val totalSize: Long,
        val fieldOffsets: Map<String, Long>,
    )

    companion object {
        const val HEADER_SIZE = 8L
        const val DEFAULT_OBJECT_SIZE = 64L

        /** Build layout from a set of class files. */
        @JvmStatic
        fun build(classFiles: List<ByteArray>): ClassLayout {
            val info = mutableMapOf<String, ClassInfo>()
            for (bytes in classFiles) {
                val cf = JvmClassReader.read(bytes)
                val layout = computeLayout(cf)
                info[cf.thisClassName] = layout
            }
            return ClassLayout(info)
        }

        private fun computeLayout(cf: ClassFile): ClassInfo {
            var offset = HEADER_SIZE
            val fieldOffsets = mutableMapOf<String, Long>()

            for (field in cf.fields) {
                val name = cf.string(field.nameIndex)
                val desc = cf.string(field.descriptorIndex)
                val isStatic = field.accessFlags and 0x0008 != 0
                if (isStatic) continue

                val size = descriptorSize(desc)
                // Align to field's natural alignment
                val align = size.coerceAtMost(8)
                offset = (offset + align - 1) and (align - 1).inv().toLong()
                fieldOffsets[name] = offset
                offset += size
            }

            // Minimum object size is header + at least 8 bytes (for GC forwarding ptr etc.)
            val totalSize = maxOf(offset, HEADER_SIZE + 8)
            // Align total to 8 bytes
            val aligned = (totalSize + 7) and 7L.inv()

            return ClassInfo(cf.thisClassName, aligned, fieldOffsets)
        }

        private fun descriptorSize(desc: String): Long = when (desc.first()) {
            'Z', 'B' -> 1
            'C', 'S' -> 2
            'I', 'F' -> 4
            'J', 'D' -> 8
            'L', '[' -> 8 // references are pointers
            else -> 8
        }
    }
}
