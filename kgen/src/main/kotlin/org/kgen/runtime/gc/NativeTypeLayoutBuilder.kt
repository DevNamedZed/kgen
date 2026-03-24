package org.kgen.runtime.gc

import org.kgen.target.jvm.AccessFlags
import org.kgen.target.jvm.ClassFile
import org.kgen.target.jvm.JvmClassReader

/**
 * Builds [ObjectLayout] and [FieldDescriptor] entries from `@KgenNative` class files.
 *
 * Bridges the JVM class file metadata to the GC type system. Reads field declarations,
 * computes offsets matching [org.kgen.runtime.compile.ClassLayout], and determines
 * which fields are reference types (Long pointers to other GC-managed objects).
 *
 * ```java
 * var builder = new NativeTypeLayoutBuilder();
 * ObjectLayout layout = builder.buildLayout(classFileBytes);
 * int typeId = registry.register(layout);
 * ```
 */
class NativeTypeLayoutBuilder {

    /**
     * Build an [ObjectLayout] from a class file's field declarations.
     *
     * @param classBytes the raw .class file bytes
     * @param referenceFields set of field names that hold GC-managed pointers.
     *   Fields not in this set are treated as primitives.
     * @param hasDestructor whether this class has a `@KgenDestructor` method
     */
    fun buildLayout(
        classBytes: ByteArray,
        referenceFields: Set<String> = emptySet(),
        hasDestructor: Boolean = false,
    ): ObjectLayout {
        val classFile = JvmClassReader.read(classBytes)
        return buildLayoutFromClassFile(classFile, referenceFields, hasDestructor)
    }

    /**
     * Build an [ObjectLayout] from a parsed [ClassFile].
     */
    fun buildLayoutFromClassFile(
        classFile: ClassFile,
        referenceFields: Set<String> = emptySet(),
        hasDestructor: Boolean = false,
    ): ObjectLayout {
        val className = classFile.thisClassName.replace('/', '_')
        val fields = mutableListOf<FieldDescriptor>()
        var offset = 0

        for (field in classFile.fields) {
            val isStatic = field.accessFlags and AccessFlags.STATIC != 0
            if (isStatic) {
                continue
            }

            val name = classFile.string(field.nameIndex)
            val descriptor = classFile.string(field.descriptorIndex)
            val size = descriptorSize(descriptor)
            val alignment = size.coerceAtMost(8)

            offset = (offset + alignment - 1) and (alignment - 1).inv()

            val isReference = name in referenceFields || isReferenceDescriptor(descriptor)
            fields.add(FieldDescriptor(name, offset, size, isReference))
            offset += size
        }

        val dataSize = if (offset == 0) { 8 } else { offset }
        val alignedSize = (dataSize + 7) and 7.inv()

        return ObjectLayout(
            name = className,
            size = alignedSize,
            fields = fields,
        )
    }

    /**
     * Scan a class file for the presence of `@KgenDestructor` annotation on any method.
     */
    fun hasDestructor(classBytes: ByteArray): Boolean {
        val classFile = JvmClassReader.read(classBytes)
        return findDestructorMethod(classFile) != null
    }

    /**
     * Find the name of the `@KgenDestructor` method in a class file, or null.
     */
    fun findDestructorMethodName(classBytes: ByteArray): String? {
        val classFile = JvmClassReader.read(classBytes)
        val method = findDestructorMethod(classFile) ?: return null
        return classFile.string(method.nameIndex)
    }

    private fun findDestructorMethod(classFile: ClassFile): org.kgen.target.jvm.MethodInfo? {
        for (method in classFile.methods) {
            for (attr in method.attributes) {
                val attrName = classFile.string(attr.nameIndex)
                if (attrName == "RuntimeVisibleAnnotations" || attrName == "RuntimeInvisibleAnnotations") {
                    val data = attr.data
                    if (data.size < 2) {
                        continue
                    }
                    val count = ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
                    var pos = 2
                    for (index in 0 until count) {
                        if (pos + 2 > data.size) {
                            break
                        }
                        val typeIdx = ((data[pos].toInt() and 0xFF) shl 8) or (data[pos + 1].toInt() and 0xFF)
                        val typeName = classFile.constantPool.utf8(typeIdx)
                        val annotationClass = typeName.removePrefix("L").removeSuffix(";")
                        if (annotationClass == "org/kgen/unmanaged/KgenDestructor") {
                            return method
                        }
                        pos = skipAnnotation(data, pos + 2)
                    }
                }
            }
        }
        return null
    }

    private fun skipAnnotation(data: ByteArray, start: Int): Int {
        var pos = start
        if (pos + 2 > data.size) {
            return data.size
        }
        val numPairs = ((data[pos].toInt() and 0xFF) shl 8) or (data[pos + 1].toInt() and 0xFF)
        pos += 2
        for (index in 0 until numPairs) {
            pos += 2
            pos = skipElementValue(data, pos)
        }
        return pos
    }

    private fun skipElementValue(data: ByteArray, start: Int): Int {
        if (start >= data.size) {
            return data.size
        }
        var pos = start
        val tag = data[pos].toInt().toChar()
        pos++
        when (tag) {
            'B', 'C', 'D', 'F', 'I', 'J', 'S', 'Z', 's' -> pos += 2
            'e' -> pos += 4
            'c' -> pos += 2
            '@' -> {
                pos += 2
                pos = skipAnnotation(data, pos)
            }
            '[' -> {
                if (pos + 2 > data.size) {
                    return data.size
                }
                val arrayCount = ((data[pos].toInt() and 0xFF) shl 8) or (data[pos + 1].toInt() and 0xFF)
                pos += 2
                for (index in 0 until arrayCount) {
                    pos = skipElementValue(data, pos)
                }
            }
        }
        return pos
    }

    private fun descriptorSize(descriptor: String): Int = when (descriptor.first()) {
        'Z', 'B' -> 1
        'C', 'S' -> 2
        'I', 'F' -> 4
        'J', 'D' -> 8
        'L', '[' -> 8
        else -> 8
    }

    private fun isReferenceDescriptor(descriptor: String): Boolean {
        return descriptor.startsWith("L") || descriptor.startsWith("[")
    }
}
