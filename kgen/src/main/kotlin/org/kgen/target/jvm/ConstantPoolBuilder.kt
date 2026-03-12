package org.kgen.target.jvm

/**
 * Mutable builder for constructing a constant pool.
 *
 * Deduplicates entries — adding the same UTF-8 string or class reference
 * twice returns the same index. Long and Double entries automatically
 * consume two slots.
 *
 * ```kotlin
 * val cp = ConstantPoolBuilder()
 * val nameIdx = cp.utf8("main")
 * val descIdx = cp.utf8("([Ljava/lang/String;)V")
 * val classIdx = cp.classEntry("java/lang/Object")
 * val pool = cp.build()
 * ```
 */
class ConstantPoolBuilder {
    private val entries = mutableListOf<CpEntry?>(null) // index 0 is unused
    private val utf8Map = HashMap<String, Int>()
    private val intMap = HashMap<Int, Int>()
    private val floatMap = HashMap<Float, Int>()
    private val longMap = HashMap<Long, Int>()
    private val doubleMap = HashMap<Double, Int>()
    private val classMap = HashMap<Int, Int>()
    private val stringMap = HashMap<Int, Int>()
    private val natMap = HashMap<Long, Int>()
    private val fieldRefMap = HashMap<Long, Int>()
    private val methodRefMap = HashMap<Long, Int>()
    private val ifaceMethodRefMap = HashMap<Long, Int>()

    val size: Int get() = entries.size

    private fun addEntry(entry: CpEntry): Int {
        val index = entries.size
        entries.add(entry)
        if (entry is CpLong || entry is CpDouble) {
            entries.add(null) // occupies two slots
        }
        return index
    }

    fun utf8(value: String): Int = utf8Map.getOrPut(value) { addEntry(CpUtf8(value)) }

    fun integer(value: Int): Int = intMap.getOrPut(value) { addEntry(CpInteger(value)) }

    fun float(value: Float): Int = floatMap.getOrPut(value) { addEntry(CpFloat(value)) }

    fun long(value: Long): Int = longMap.getOrPut(value) { addEntry(CpLong(value)) }

    fun double(value: Double): Int = doubleMap.getOrPut(value) { addEntry(CpDouble(value)) }

    fun classEntry(name: String): Int {
        val nameIdx = utf8(name)
        return classMap.getOrPut(nameIdx) { addEntry(CpClass(nameIdx)) }
    }

    fun string(value: String): Int {
        val utf8Idx = utf8(value)
        return stringMap.getOrPut(utf8Idx) { addEntry(CpString(utf8Idx)) }
    }

    fun nameAndType(name: String, descriptor: String): Int {
        val nameIdx = utf8(name)
        val descIdx = utf8(descriptor)
        val key = nameIdx.toLong() shl 32 or descIdx.toLong()
        return natMap.getOrPut(key) { addEntry(CpNameAndType(nameIdx, descIdx)) }
    }

    fun fieldRef(className: String, name: String, descriptor: String): Int {
        val classIdx = classEntry(className)
        val natIdx = nameAndType(name, descriptor)
        val key = classIdx.toLong() shl 32 or natIdx.toLong()
        return fieldRefMap.getOrPut(key) { addEntry(CpFieldRef(classIdx, natIdx)) }
    }

    fun methodRef(className: String, name: String, descriptor: String): Int {
        val classIdx = classEntry(className)
        val natIdx = nameAndType(name, descriptor)
        val key = classIdx.toLong() shl 32 or natIdx.toLong()
        return methodRefMap.getOrPut(key) { addEntry(CpMethodRef(classIdx, natIdx)) }
    }

    fun interfaceMethodRef(className: String, name: String, descriptor: String): Int {
        val classIdx = classEntry(className)
        val natIdx = nameAndType(name, descriptor)
        val key = classIdx.toLong() shl 32 or natIdx.toLong()
        return ifaceMethodRefMap.getOrPut(key) { addEntry(CpInterfaceMethodRef(classIdx, natIdx)) }
    }

    fun methodHandle(referenceKind: Int, referenceIndex: Int): Int =
        addEntry(CpMethodHandle(referenceKind, referenceIndex))

    fun methodType(descriptor: String): Int =
        addEntry(CpMethodType(utf8(descriptor)))

    fun invokeDynamic(bootstrapMethodAttrIndex: Int, name: String, descriptor: String): Int =
        addEntry(CpInvokeDynamic(bootstrapMethodAttrIndex, nameAndType(name, descriptor)))

    fun dynamic(bootstrapMethodAttrIndex: Int, name: String, descriptor: String): Int =
        addEntry(CpDynamic(bootstrapMethodAttrIndex, nameAndType(name, descriptor)))

    fun module(name: String): Int = addEntry(CpModule(utf8(name)))

    fun packageEntry(name: String): Int = addEntry(CpPackage(utf8(name)))

    fun build(): ConstantPool = ConstantPool(entries.toList())
}
