package org.kgen.target.jvm

/**
 * Structured representation of a JVM class file.
 *
 * This is the primary model for both reading and writing .class files.
 * The constant pool is a first-class object with typed entries, and all
 * indices are resolved through it.
 *
 * ```kotlin
 * // Parse
 * val cf = JvmClassReader.read(bytes)
 * println("${cf.thisClassName} extends ${cf.superClassName}")
 * for (m in cf.methods) {
 *     println("  ${cf.string(m.nameIndex)}${cf.string(m.descriptorIndex)}")
 * }
 *
 * // Build
 * val cf = ClassFileBuilder("com/example/Hello")
 *     .superClass("java/lang/Object")
 *     .method("main", "([Ljava/lang/String;)V", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
 *         code.getstatic("java/lang/System", "out", "Ljava/io/PrintStream;")
 *         code.ldc("Hello, World!")
 *         code.invokevirtual("java/io/PrintStream", "println", "(Ljava/lang/String;)V")
 *         code.return_()
 *     }
 *     .build()
 * val bytes = JvmClassWriter.write(cf)
 * ```
 */
data class ClassFile(
    val minorVersion: Int,
    val majorVersion: Int,
    val constantPool: ConstantPool,
    val accessFlags: Int,
    val thisClass: Int,
    val superClass: Int,
    val interfaces: List<Int>,
    val fields: List<FieldInfo>,
    val methods: List<MethodInfo>,
    val attributes: List<AttributeInfo>,
) {
    val thisClassName: String get() = constantPool.className(thisClass)
    val superClassName: String? get() = if (superClass == 0) null else constantPool.className(superClass)
    val interfaceNames: List<String> get() = interfaces.map { constantPool.className(it) }

    fun string(index: Int): String = constantPool.utf8(index)

    /** Source file name from the SourceFile attribute, or null if absent. */
    val sourceFile: String? get() {
        val attr = attributes.firstOrNull { constantPool.utf8(it.nameIndex) == "SourceFile" }
            ?: return null
        val idx = ((attr.data[0].toInt() and 0xFF) shl 8) or (attr.data[1].toInt() and 0xFF)
        return constantPool.utf8(idx)
    }

    val javaVersion: String get() = when (majorVersion) {
        45 -> "1.1"
        46 -> "1.2"
        47 -> "1.3"
        48 -> "1.4"
        49 -> "5"
        50 -> "6"
        51 -> "7"
        52 -> "8"
        53 -> "9"
        54 -> "10"
        55 -> "11"
        56 -> "12"
        57 -> "13"
        58 -> "14"
        59 -> "15"
        60 -> "16"
        61 -> "17"
        62 -> "18"
        63 -> "19"
        64 -> "20"
        65 -> "21"
        66 -> "22"
        67 -> "23"
        68 -> "24"
        69 -> "25"
        else -> "$majorVersion.$minorVersion"
    }
}

data class FieldInfo(
    val accessFlags: Int,
    val nameIndex: Int,
    val descriptorIndex: Int,
    val attributes: List<AttributeInfo>,
)

data class MethodInfo(
    val accessFlags: Int,
    val nameIndex: Int,
    val descriptorIndex: Int,
    val attributes: List<AttributeInfo>,
)

data class AttributeInfo(
    val nameIndex: Int,
    val data: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AttributeInfo) return false
        return nameIndex == other.nameIndex && data.contentEquals(other.data)
    }

    override fun hashCode(): Int = 31 * nameIndex + data.contentHashCode()
}
