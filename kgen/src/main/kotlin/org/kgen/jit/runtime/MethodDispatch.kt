package org.kgen.jit.runtime

/**
 * Handles method entry and dispatch for JIT-compiled code.
 * Supports direct dispatch, virtual dispatch via vtables, and interface dispatch.
 *
 * ```java
 * MethodDispatch dispatch = runtime.dispatch();
 * long address = dispatch.resolve("Point.toString");
 * dispatch.registerVTable(typeId, vtable);
 * ```
 */
interface MethodDispatch {

    /** Resolve a function name to its entry point address. */
    fun resolve(name: String): Long?

    /** Register a vtable for virtual dispatch. */
    fun registerVTable(typeId: Int, vtable: VTable)

    /** Look up a vtable entry for a given type and slot index. */
    fun virtualLookup(typeId: Int, slotIndex: Int): Long

    /** Register an interface method table for interface dispatch. */
    fun registerITable(interfaceId: Int, typeId: Int, itable: ITable)

    /** Look up an interface method for a given interface, type, and slot. */
    fun interfaceLookup(interfaceId: Int, typeId: Int, slotIndex: Int): Long
}

/**
 * Virtual method table — maps slot indices to function entry points.
 */
data class VTable(
    val typeId: Int,
    val slots: LongArray,
) {
    override fun equals(other: Any?) = other is VTable && typeId == other.typeId
    override fun hashCode() = typeId
}

/**
 * Interface method table — maps slot indices to function entry points
 * for a specific type's implementation of an interface.
 */
data class ITable(
    val interfaceId: Int,
    val typeId: Int,
    val slots: LongArray,
) {
    override fun equals(other: Any?) = other is ITable && interfaceId == other.interfaceId && typeId == other.typeId
    override fun hashCode() = interfaceId * 31 + typeId
}
