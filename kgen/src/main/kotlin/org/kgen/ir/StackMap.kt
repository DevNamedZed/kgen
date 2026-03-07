package org.kgen.ir

/**
 * Stack map for a function — records which stack slots and registers
 * hold live GC references at each safepoint.
 *
 * Used by the GC to find all roots when scanning a thread's stack.
 * Emitted during code generation for functions with a GC strategy.
 *
 * ```java
 * StackMap map = codeGen.getStackMap("myFunction");
 * for (StackMapEntry entry : map.entries()) {
 *     System.out.println("Safepoint at offset " + entry.instructionOffset());
 *     for (StackMapLocation loc : entry.locations()) {
 *         System.out.println("  GC root: " + loc);
 *     }
 * }
 * ```
 */
data class StackMap(
    val functionName: String,
    val entries: List<StackMapEntry>,
)

/**
 * A single safepoint record — the instruction offset and the set of
 * locations that hold live GC references at that point.
 */
data class StackMapEntry(
    val instructionOffset: Long,
    val locations: List<StackMapLocation>,
)

/**
 * A location that holds a GC reference at a safepoint.
 */
sealed interface StackMapLocation {
    /** A register holding a GC reference. */
    data class Register(val registerIndex: Int) : StackMapLocation

    /** A stack slot (RBP-relative offset) holding a GC reference. */
    data class Stack(val rbpOffset: Int) : StackMapLocation

    /** A direct constant (e.g., null reference). */
    data class Constant(val value: Long) : StackMapLocation
}
