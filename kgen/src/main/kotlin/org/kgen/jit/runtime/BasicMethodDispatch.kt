package org.kgen.jit.runtime

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

    /** Register a method name to an entry point address. */
    fun register(name: String, address: Long) {
        methods[name] = address
    }

    override fun resolve(name: String): Long? = methods[name]

    override fun registerVTable(typeId: Int, vtable: VTable) {
        vtables[typeId] = vtable
    }

    override fun virtualLookup(typeId: Int, slotIndex: Int): Long {
        val vt = vtables[typeId]
            ?: throw IllegalArgumentException("No vtable for type $typeId")
        if (slotIndex < 0 || slotIndex >= vt.slots.size) {
            throw IndexOutOfBoundsException("VTable slot $slotIndex out of range for type $typeId (${vt.slots.size} slots)")
        }
        return vt.slots[slotIndex]
    }

    override fun registerITable(interfaceId: Int, typeId: Int, itable: ITable) {
        val key = interfaceId.toLong() shl 32 or typeId.toLong()
        itables[key] = itable
    }

    override fun interfaceLookup(interfaceId: Int, typeId: Int, slotIndex: Int): Long {
        val key = interfaceId.toLong() shl 32 or typeId.toLong()
        val it = itables[key]
            ?: throw IllegalArgumentException("No itable for interface $interfaceId, type $typeId")
        if (slotIndex < 0 || slotIndex >= it.slots.size) {
            throw IndexOutOfBoundsException("ITable slot $slotIndex out of range")
        }
        return it.slots[slotIndex]
    }

    /** Number of registered methods. */
    fun methodCount(): Int = methods.size

    /** Number of registered vtables. */
    fun vtableCount(): Int = vtables.size
}
