package org.kgen.ir.codegen

import org.kgen.ir.IrFunction
import org.kgen.ir.Type

/**
 * Result of local slot allocation for stack machines (WASM, JVM, CLR).
 *
 * Maps each IR value to a numbered local variable slot. Parameters occupy
 * the first N slots.
 */
data class LocalSlotAssignment(
    /** Maps value name → local slot index. */
    val slotMap: Map<String, Int>,
    /** Total number of local slots (including parameters). */
    val totalSlots: Int,
    /** Type of each slot (for typed stack machines like WASM). */
    val slotTypes: Map<Int, Type> = emptyMap(),
)

/**
 * Assigns local variable slots for stack machine targets.
 *
 * Stack machines (WASM, JVM bytecode, CLR IL) don't have register pressure —
 * values are pushed and popped from an operand stack. But each named value
 * still needs a local variable slot for storage between uses.
 *
 * This allocator reuses slots when lifetimes don't overlap, minimizing
 * the local variable count.
 *
 * ```java
 * var allocator = new LocalSlotAllocator();
 * var analysis = new LivenessAnalysis(fn);
 * var assignment = allocator.allocate(fn, analysis.intervals());
 * // Use: local.get assignment.slotMap.get("x")
 * ```
 */
class LocalSlotAllocator {

    /**
     * Assign local slots to IR values.
     *
     * Parameters get fixed slots (0..N-1). Other values get slots assigned
     * by lifetime, reusing slots when possible.
     *
     * @param fn the function being compiled
     * @param intervals live intervals from [LivenessAnalysis]
     * @param reuseSlots if true, reuse slots when lifetimes don't overlap (default true)
     */
    @JvmOverloads
    fun allocate(
        fn: IrFunction,
        intervals: List<LiveInterval>,
        reuseSlots: Boolean = true,
    ): LocalSlotAssignment {
        val slotMap = mutableMapOf<String, Int>()
        val slotTypes = mutableMapOf<Int, Type>()
        var nextSlot = 0

        // Parameters get fixed slots first
        for (param in fn.params) {
            slotMap[param.name] = nextSlot
            slotTypes[nextSlot] = param.type
            nextSlot++
        }

        if (!reuseSlots) {
            // Simple: each value gets its own slot
            for (interval in intervals) {
                if (interval.name in slotMap) continue
                slotMap[interval.name] = nextSlot
                slotTypes[nextSlot] = interval.type
                nextSlot++
            }
        } else {
            // Reuse slots when lifetimes don't overlap
            // Track active slots: (end position, slot index, type)
            val activeSlots = mutableListOf<Triple<Int, Int, Type>>()
            val freeSlots = mutableMapOf<Type, MutableList<Int>>() // type → available slot indices

            for (interval in intervals) {
                if (interval.name in slotMap) continue

                // Free slots whose intervals have ended
                activeSlots.removeAll { (end, slot, type) ->
                    if (end < interval.start) {
                        freeSlots.getOrPut(type) { mutableListOf() }.add(slot)
                        true
                    } else false
                }

                // Try to reuse a slot of the same type
                val typeSlots = freeSlots[interval.type]
                val slot = if (typeSlots != null && typeSlots.isNotEmpty()) {
                    typeSlots.removeFirst()
                } else {
                    val s = nextSlot
                    nextSlot++
                    s
                }

                slotMap[interval.name] = slot
                slotTypes[slot] = interval.type
                activeSlots.add(Triple(interval.end, slot, interval.type))
            }
        }

        return LocalSlotAssignment(slotMap, nextSlot, slotTypes)
    }
}
