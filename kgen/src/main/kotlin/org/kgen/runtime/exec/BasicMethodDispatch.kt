package org.kgen.runtime.exec

/**
 * Simple method dispatch implementation using hash maps.
 * Supports direct lookup, vtable dispatch, and interface dispatch.
 *
 * ```java
 * var dispatch = new BasicMethodDispatch();
 * dispatch.register("add", entryPoint);
 * long addr = dispatch.resolve("add");
 * ```
 */
class BasicMethodDispatch : MethodDispatch {

    private val methods = mutableMapOf<String, Long>()
    private val vtables = mutableMapOf<Int, VTable>()
    private val itables = mutableMapOf<Long, ITable>() // key = interfaceId << 32 | typeId
    private val parentType = mutableMapOf<Int, Int>()   // child typeId -> parent typeId

    /** Register a method name to an entry point address. */
    fun register(name: String, address: Long) {
        methods[name] = address
    }

    override fun resolve(name: String): Long? = methods[name]

    override fun registerVTable(typeId: Int, vtable: VTable) {
        vtables[typeId] = vtable
    }

    /** Register a type's parent for vtable inheritance chain walking. */
    fun registerParent(typeId: Int, parentTypeId: Int) {
        parentType[typeId] = parentTypeId
    }

    override fun virtualLookup(typeId: Int, slotIndex: Int): Long {
        // Walk up the inheritance chain to find the vtable with this slot
        var currentType = typeId
        var foundVtable = false
        while (true) {
            val vt = vtables[currentType]
            if (vt != null) {
                foundVtable = true
                if (slotIndex >= 0 && slotIndex < vt.slots.size) {
                    return vt.slots[slotIndex]
                }
            }
            // Walk to parent
            val parent = parentType[currentType]
            if (parent == null) {
                if (foundVtable) {
                    throw IndexOutOfBoundsException(
                        "VTable slot $slotIndex out of range for type $typeId")
                }
                throw IllegalArgumentException("No vtable for type $typeId")
            }
            currentType = parent
        }
    }

    override fun registerITable(interfaceId: Int, typeId: Int, itable: ITable) {
        val key = interfaceId.toLong() shl 32 or typeId.toLong()
        itables[key] = itable
    }

    override fun interfaceLookup(interfaceId: Int, typeId: Int, slotIndex: Int): Long {
        // Walk up the inheritance chain for interface dispatch too
        var currentType = typeId
        var foundItable = false
        while (true) {
            val key = interfaceId.toLong() shl 32 or currentType.toLong()
            val it = itables[key]
            if (it != null) {
                foundItable = true
                if (slotIndex >= 0 && slotIndex < it.slots.size) {
                    return it.slots[slotIndex]
                }
            }
            val parent = parentType[currentType]
            if (parent == null) {
                if (foundItable) {
                    throw IndexOutOfBoundsException(
                        "ITable slot $slotIndex out of range for interface $interfaceId, type $typeId")
                }
                throw IllegalArgumentException(
                    "No itable for interface $interfaceId, type $typeId")
            }
            currentType = parent
        }
    }

    /** Number of registered methods. */
    fun methodCount(): Int = methods.size

    /** Number of registered vtables. */
    fun vtableCount(): Int = vtables.size
}
