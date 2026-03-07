package org.kgen.jit.runtime

/**
 * Registry of object types and their layouts. Used by the GC to know
 * which fields are references, and by the runtime for type checks.
 *
 * ```java
 * var registry = new TypeRegistry();
 * int id = registry.register(layout);
 * ObjectLayout layout = registry.lookup(id);
 * ```
 */
class TypeRegistry {
    private val types = mutableMapOf<Int, ObjectLayout>()
    private var nextId = 1

    /** Register a layout and assign it a type ID. Returns the assigned ID. */
    fun register(layout: ObjectLayout): Int {
        val id = if (layout.typeId != 0) layout.typeId else nextId++
        types[id] = layout.copy(typeId = id)
        return id
    }

    /** Look up a layout by type ID. */
    fun lookup(typeId: Int): ObjectLayout? = types[typeId]

    /** All registered types. */
    fun allTypes(): Collection<ObjectLayout> = types.values
}
