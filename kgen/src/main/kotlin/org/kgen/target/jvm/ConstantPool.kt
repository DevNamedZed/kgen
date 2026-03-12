package org.kgen.target.jvm

/**
 * JVM constant pool — the typed entry table at the heart of every class file.
 *
 * Entries are 1-indexed (index 0 is unused). Long and Double entries occupy
 * two slots (the next index is unusable).
 *
 * ```kotlin
 * val cp = ConstantPool(entries)
 * val name = cp.utf8(cp[3] as CpClass).nameIndex)
 * ```
 */
class ConstantPool(private val entries: List<CpEntry?>) {

    val size: Int get() = entries.size

    operator fun get(index: Int): CpEntry =
        entries.getOrNull(index) ?: throw IllegalArgumentException("Invalid constant pool index: $index")

    fun getOrNull(index: Int): CpEntry? = entries.getOrNull(index)

    fun utf8(index: Int): String {
        val entry = get(index)
        return (entry as? CpUtf8)?.value
            ?: throw IllegalArgumentException("CP #$index is ${entry::class.simpleName}, expected Utf8")
    }

    fun className(index: Int): String {
        val entry = get(index)
        val classEntry = entry as? CpClass
            ?: throw IllegalArgumentException("CP #$index is ${entry::class.simpleName}, expected Class")
        return utf8(classEntry.nameIndex)
    }

    fun nameAndType(index: Int): Pair<String, String> {
        val entry = get(index) as CpNameAndType
        return utf8(entry.nameIndex) to utf8(entry.descriptorIndex)
    }

    fun allEntries(): List<IndexedValue<CpEntry?>> =
        entries.mapIndexed { i, e -> IndexedValue(i, e) }

    fun <T : CpEntry> findAll(type: Class<T>): List<IndexedValue<T>> =
        entries.mapIndexedNotNull { i, e ->
            if (type.isInstance(e)) IndexedValue(i, type.cast(e)) else null
        }

    inline fun <reified T : CpEntry> findAll(): List<IndexedValue<T>> = findAll(T::class.java)
}

sealed class CpEntry(val tag: Int)

data class CpUtf8(val value: String) : CpEntry(TAG) {
    companion object { const val TAG = 1 }
}

data class CpInteger(val value: Int) : CpEntry(TAG) {
    companion object { const val TAG = 3 }
}

data class CpFloat(val value: Float) : CpEntry(TAG) {
    companion object { const val TAG = 4 }
}

data class CpLong(val value: Long) : CpEntry(TAG) {
    companion object { const val TAG = 5 }
}

data class CpDouble(val value: Double) : CpEntry(TAG) {
    companion object { const val TAG = 6 }
}

data class CpClass(val nameIndex: Int) : CpEntry(TAG) {
    companion object { const val TAG = 7 }
}

data class CpString(val stringIndex: Int) : CpEntry(TAG) {
    companion object { const val TAG = 8 }
}

data class CpFieldRef(val classIndex: Int, val nameAndTypeIndex: Int) : CpEntry(TAG) {
    companion object { const val TAG = 9 }
}

data class CpMethodRef(val classIndex: Int, val nameAndTypeIndex: Int) : CpEntry(TAG) {
    companion object { const val TAG = 10 }
}

data class CpInterfaceMethodRef(val classIndex: Int, val nameAndTypeIndex: Int) : CpEntry(TAG) {
    companion object { const val TAG = 11 }
}

data class CpNameAndType(val nameIndex: Int, val descriptorIndex: Int) : CpEntry(TAG) {
    companion object { const val TAG = 12 }
}

data class CpMethodHandle(val referenceKind: Int, val referenceIndex: Int) : CpEntry(TAG) {
    companion object { const val TAG = 15 }
}

data class CpMethodType(val descriptorIndex: Int) : CpEntry(TAG) {
    companion object { const val TAG = 16 }
}

data class CpDynamic(val bootstrapMethodAttrIndex: Int, val nameAndTypeIndex: Int) : CpEntry(TAG) {
    companion object { const val TAG = 17 }
}

data class CpInvokeDynamic(val bootstrapMethodAttrIndex: Int, val nameAndTypeIndex: Int) : CpEntry(TAG) {
    companion object { const val TAG = 18 }
}

data class CpModule(val nameIndex: Int) : CpEntry(TAG) {
    companion object { const val TAG = 19 }
}

data class CpPackage(val nameIndex: Int) : CpEntry(TAG) {
    companion object { const val TAG = 20 }
}
