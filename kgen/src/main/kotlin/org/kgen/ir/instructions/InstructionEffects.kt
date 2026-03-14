package org.kgen.ir.instructions

/**
 * Static effect descriptor for an IR instruction.
 *
 * Every instruction has a fixed set of effects that describe its observable behavior.
 * Optimization passes use these effects to determine whether instructions can be
 * reordered, eliminated, or hoisted. The effect system replaces ad-hoc `when(inst)`
 * blocks with a single queryable descriptor.
 *
 * Effects are represented as a 32-bit bitmask for compact storage and fast queries.
 * Use the query methods ([isPure], [readsMemory], [writesMemory], etc.) rather than
 * inspecting the raw [bits] directly.
 *
 * **Static vs. dynamic effects:** The [effects][Instruction.effects] property on each
 * instruction returns the *static* (worst-case) effects for that opcode. Passes that
 * have additional information (e.g., alias analysis proving a load is from a local
 * alloca) can narrow effects dynamically, but never widen them.
 *
 * ```java
 * // Query whether an instruction can be safely removed if unused:
 * if (instruction.getEffects().isPure()) {
 *     // safe to eliminate if result is dead
 * }
 *
 * // Query whether an instruction reads memory:
 * if (instruction.getEffects().readsMemory()) {
 *     // must consider memory dependencies
 * }
 * ```
 *
 * @param bits the raw 32-bit bitmask of effect flags
 */
class InstructionEffects(val bits: Int) {

    // --- Query methods ---

    /**
     * Whether this instruction reads from heap memory (global variables, object fields,
     * array elements, or arbitrary pointers).
     */
    fun readsHeapMemory(): Boolean {
        return (bits and READS_HEAP_MEMORY) != 0
    }

    /**
     * Whether this instruction writes to heap memory.
     */
    fun writesHeapMemory(): Boolean {
        return (bits and WRITES_HEAP_MEMORY) != 0
    }

    /**
     * Whether this instruction reads from stack memory (alloca'd memory, local variables).
     */
    fun readsStackMemory(): Boolean {
        return (bits and READS_STACK_MEMORY) != 0
    }

    /**
     * Whether this instruction writes to stack memory.
     */
    fun writesStackMemory(): Boolean {
        return (bits and WRITES_STACK_MEMORY) != 0
    }

    /**
     * Whether this instruction reads from argument-pointed memory
     * (memory reachable through pointer-typed function arguments).
     */
    fun readsArgMemory(): Boolean {
        return (bits and READS_ARG_MEMORY) != 0
    }

    /**
     * Whether this instruction writes to argument-pointed memory.
     */
    fun writesArgMemory(): Boolean {
        return (bits and WRITES_ARG_MEMORY) != 0
    }

    /**
     * Whether this instruction reads any memory (heap, stack, or argument).
     */
    fun readsMemory(): Boolean {
        return (bits and (READS_HEAP_MEMORY or READS_STACK_MEMORY or READS_ARG_MEMORY)) != 0
    }

    /**
     * Whether this instruction writes any memory (heap, stack, or argument).
     */
    fun writesMemory(): Boolean {
        return (bits and (WRITES_HEAP_MEMORY or WRITES_STACK_MEMORY or WRITES_ARG_MEMORY)) != 0
    }

    /**
     * Whether this instruction may trigger a hardware trap (division by zero,
     * null pointer dereference, integer overflow with trapping semantics).
     */
    fun canTrap(): Boolean {
        return (bits and CAN_TRAP) != 0
    }

    /**
     * Whether this instruction may throw a managed exception (Java-style throw,
     * invoke with unwind destination).
     */
    fun canThrow(): Boolean {
        return (bits and CAN_THROW) != 0
    }

    /**
     * Whether this instruction is a GC safepoint — a point where the garbage
     * collector may relocate managed objects.
     */
    fun isSafepoint(): Boolean {
        return (bits and IS_SAFEPOINT) != 0
    }

    /**
     * Whether this instruction is a memory barrier that prevents reordering
     * of surrounding memory operations.
     */
    fun isBarrier(): Boolean {
        return (bits and IS_BARRIER) != 0
    }

    /**
     * Whether this instruction has observable side effects beyond its result value.
     *
     * An instruction with side effects cannot be removed even if its result is unused.
     * This includes memory writes, calls (which may have arbitrary effects), traps,
     * throws, barriers, terminators, and safepoints.
     */
    fun hasSideEffects(): Boolean {
        return (bits and HAS_SIDE_EFFECTS) != 0
    }

    /**
     * Whether this instruction terminates its basic block (branch, return, switch,
     * unreachable, trap).
     */
    fun isTerminator(): Boolean {
        return (bits and IS_TERMINATOR) != 0
    }

    /**
     * Whether this instruction is a conditional or unconditional branch
     * (subset of terminators that transfer control within the function).
     */
    fun isBranch(): Boolean {
        return (bits and IS_BRANCH) != 0
    }

    /**
     * Whether this instruction performs a function call (direct, indirect, or invoke).
     */
    fun isCall(): Boolean {
        return (bits and IS_CALL) != 0
    }

    /**
     * Whether this instruction returns from the current function.
     */
    fun isReturn(): Boolean {
        return (bits and IS_RETURN) != 0
    }

    /**
     * Whether this instruction's operands are commutative (the order of the
     * two primary operands does not affect the result). Useful for canonicalization
     * and common subexpression elimination.
     */
    fun commutes(): Boolean {
        return (bits and COMMUTES) != 0
    }

    /**
     * Whether this instruction has divergent control flow — different GPU lanes
     * may take different paths. Relevant for compute kernels where divergent
     * instructions cannot be reordered across barriers or fences.
     */
    fun isDivergent(): Boolean {
        return (bits and IS_DIVERGENT) != 0
    }

    /**
     * Whether this instruction is pure — it has no side effects, does not read
     * or write memory, cannot trap or throw, and is not a barrier or safepoint.
     *
     * Pure instructions can be freely reordered, duplicated, or eliminated if
     * their result is unused. The [commutes] flag is not considered an effect
     * and does not affect purity.
     */
    fun isPure(): Boolean {
        return (bits and EFFECT_MASK) == 0
    }

    /**
     * Combine this effect set with another, producing the union of all effects.
     */
    fun union(other: InstructionEffects): InstructionEffects {
        return InstructionEffects(bits or other.bits)
    }

    /**
     * Narrow this effect set by clearing the given flag bits.
     *
     * Dynamic effect refinement may only clear effects — never introduce effects
     * stronger than the static declaration. This method enforces that contract:
     * the result is always a subset of the original.
     *
     * @param clearBits the flag bits to clear (e.g., `IS_BARRIER or WRITES_HEAP_MEMORY`)
     */
    fun narrow(clearBits: Int): InstructionEffects {
        return InstructionEffects(bits and clearBits.inv())
    }

    override fun equals(other: Any?): Boolean {
        return other is InstructionEffects && bits == other.bits
    }

    override fun hashCode(): Int {
        return bits
    }

    override fun toString(): String {
        if (bits == 0) {
            return "InstructionEffects[PURE]"
        }
        val flags = mutableListOf<String>()
        if (readsHeapMemory()) { flags.add("readsHeap") }
        if (writesHeapMemory()) { flags.add("writesHeap") }
        if (readsStackMemory()) { flags.add("readsStack") }
        if (writesStackMemory()) { flags.add("writesStack") }
        if (readsArgMemory()) { flags.add("readsArg") }
        if (writesArgMemory()) { flags.add("writesArg") }
        if (canTrap()) { flags.add("canTrap") }
        if (canThrow()) { flags.add("canThrow") }
        if (isSafepoint()) { flags.add("safepoint") }
        if (isBarrier()) { flags.add("barrier") }
        if (hasSideEffects()) { flags.add("sideEffects") }
        if (isTerminator()) { flags.add("terminator") }
        if (isBranch()) { flags.add("branch") }
        if (isCall()) { flags.add("call") }
        if (isReturn()) { flags.add("return") }
        if (commutes()) { flags.add("commutes") }
        if (isDivergent()) { flags.add("divergent") }
        return "InstructionEffects[${flags.joinToString(", ")}]"
    }

    companion object {

        // --- Effect bit constants ---

        /** Reads from heap memory (globals, object fields, arbitrary pointers). */
        const val READS_HEAP_MEMORY = 1 shl 0

        /** Writes to heap memory. */
        const val WRITES_HEAP_MEMORY = 1 shl 1

        /** Reads from stack memory (alloca'd locals). */
        const val READS_STACK_MEMORY = 1 shl 2

        /** Writes to stack memory. */
        const val WRITES_STACK_MEMORY = 1 shl 3

        /** Reads from argument-pointed memory. */
        const val READS_ARG_MEMORY = 1 shl 4

        /** Writes to argument-pointed memory. */
        const val WRITES_ARG_MEMORY = 1 shl 5

        /** May trigger a hardware trap. */
        const val CAN_TRAP = 1 shl 6

        /** May throw a managed exception. */
        const val CAN_THROW = 1 shl 7

        /** Is a GC safepoint. */
        const val IS_SAFEPOINT = 1 shl 8

        /** Is a memory barrier. */
        const val IS_BARRIER = 1 shl 9

        /** Has observable side effects (cannot be dead-code eliminated). */
        const val HAS_SIDE_EFFECTS = 1 shl 10

        /** Terminates its basic block. */
        const val IS_TERMINATOR = 1 shl 11

        /** Is a branch instruction. */
        const val IS_BRANCH = 1 shl 12

        /** Performs a function call. */
        const val IS_CALL = 1 shl 13

        /** Returns from the function. */
        const val IS_RETURN = 1 shl 14

        /** Operands are commutative (not a side effect — does not affect purity). */
        const val COMMUTES = 1 shl 15

        /** Instruction has divergent control flow (GPU: lanes may take different paths). */
        const val IS_DIVERGENT = 1 shl 16

        /** Mask of all bits that represent actual effects (excludes property flags like [COMMUTES]). */
        private const val EFFECT_MASK = READS_HEAP_MEMORY or WRITES_HEAP_MEMORY or
            READS_STACK_MEMORY or WRITES_STACK_MEMORY or
            READS_ARG_MEMORY or WRITES_ARG_MEMORY or
            CAN_TRAP or CAN_THROW or IS_SAFEPOINT or IS_BARRIER or
            HAS_SIDE_EFFECTS or IS_TERMINATOR or IS_BRANCH or IS_CALL or IS_RETURN or
            IS_DIVERGENT

        // --- Common effect combinations ---

        /** No effects — pure computation. */
        @JvmField
        val PURE = InstructionEffects(0)

        /** Pure but with commutative operands. */
        @JvmField
        val PURE_COMMUTATIVE = InstructionEffects(COMMUTES)

        /** Reads heap memory only. */
        @JvmField
        val READS_HEAP = InstructionEffects(READS_HEAP_MEMORY)

        /** Writes heap memory with side effects. */
        @JvmField
        val WRITES_HEAP = InstructionEffects(WRITES_HEAP_MEMORY or HAS_SIDE_EFFECTS)

        /** Reads and writes heap memory with side effects. */
        @JvmField
        val READ_WRITE_HEAP = InstructionEffects(READS_HEAP_MEMORY or WRITES_HEAP_MEMORY or HAS_SIDE_EFFECTS)

        /** Reads stack memory only (e.g., stack load). */
        @JvmField
        val READS_STACK = InstructionEffects(READS_STACK_MEMORY)

        /** Writes stack memory with side effects (e.g., stack store). */
        @JvmField
        val WRITES_STACK = InstructionEffects(WRITES_STACK_MEMORY or HAS_SIDE_EFFECTS)

        /** Full memory read (heap + stack + arg). */
        @JvmField
        val READS_ALL_MEMORY = InstructionEffects(READS_HEAP_MEMORY or READS_STACK_MEMORY or READS_ARG_MEMORY)

        /** Full memory write with side effects. */
        @JvmField
        val WRITES_ALL_MEMORY = InstructionEffects(
            WRITES_HEAP_MEMORY or WRITES_STACK_MEMORY or WRITES_ARG_MEMORY or HAS_SIDE_EFFECTS
        )

        /** Full memory read + write (conservative for calls). */
        @JvmField
        val READ_WRITE_ALL_MEMORY = InstructionEffects(
            READS_HEAP_MEMORY or WRITES_HEAP_MEMORY or
                READS_STACK_MEMORY or WRITES_STACK_MEMORY or
                READS_ARG_MEMORY or WRITES_ARG_MEMORY or
                HAS_SIDE_EFFECTS
        )

        /** Unconditional branch terminator. */
        @JvmField
        val BRANCH = InstructionEffects(IS_TERMINATOR or IS_BRANCH or HAS_SIDE_EFFECTS)

        /** Conditional branch terminator. */
        @JvmField
        val CONDITIONAL_BRANCH = InstructionEffects(IS_TERMINATOR or IS_BRANCH or HAS_SIDE_EFFECTS)

        /** Divergent branch terminator (GPU lanes may take different paths). */
        @JvmField
        val DIVERGENT_BRANCH = InstructionEffects(IS_TERMINATOR or IS_BRANCH or IS_DIVERGENT or HAS_SIDE_EFFECTS)

        /** Return terminator. */
        @JvmField
        val RETURN = InstructionEffects(IS_TERMINATOR or IS_RETURN or HAS_SIDE_EFFECTS)

        /** Unreachable/trap terminator. */
        @JvmField
        val TRAP = InstructionEffects(IS_TERMINATOR or HAS_SIDE_EFFECTS or CAN_TRAP)

        /** Standard call (reads/writes all memory, may throw, is a safepoint). */
        @JvmField
        val CALL = InstructionEffects(
            READS_HEAP_MEMORY or WRITES_HEAP_MEMORY or
                READS_STACK_MEMORY or WRITES_STACK_MEMORY or
                READS_ARG_MEMORY or WRITES_ARG_MEMORY or
                CAN_THROW or IS_SAFEPOINT or IS_CALL or HAS_SIDE_EFFECTS
        )

        /** Invoke (call + terminator, may throw with unwind destination). */
        @JvmField
        val INVOKE = InstructionEffects(
            READS_HEAP_MEMORY or WRITES_HEAP_MEMORY or
                READS_STACK_MEMORY or WRITES_STACK_MEMORY or
                READS_ARG_MEMORY or WRITES_ARG_MEMORY or
                CAN_THROW or IS_SAFEPOINT or IS_CALL or IS_TERMINATOR or HAS_SIDE_EFFECTS
        )

        /** May trap (division, certain memory operations). */
        @JvmField
        val MAY_TRAP = InstructionEffects(CAN_TRAP)

        /** Memory fence/barrier. */
        @JvmField
        val FENCE = InstructionEffects(IS_BARRIER or HAS_SIDE_EFFECTS)

        /** Atomic read-modify-write (reads + writes heap, barrier, side effects). */
        @JvmField
        val ATOMIC_RMW = InstructionEffects(
            READS_HEAP_MEMORY or WRITES_HEAP_MEMORY or IS_BARRIER or HAS_SIDE_EFFECTS
        )

        /** Debug/metadata instruction — no effects at all. */
        @JvmField
        val NONE = PURE

        /** GC safepoint. */
        @JvmField
        val SAFEPOINT = InstructionEffects(IS_SAFEPOINT or HAS_SIDE_EFFECTS)

        /** GC allocation (writes heap, safepoint, may throw OOM). */
        @JvmField
        val GC_ALLOC = InstructionEffects(
            WRITES_HEAP_MEMORY or IS_SAFEPOINT or CAN_THROW or HAS_SIDE_EFFECTS
        )

        /** Write barrier (writes heap, side effects). */
        @JvmField
        val WRITE_BARRIER = InstructionEffects(WRITES_HEAP_MEMORY or HAS_SIDE_EFFECTS)

        /** Object allocation (heap write, may throw, safepoint). */
        @JvmField
        val OBJECT_ALLOC = GC_ALLOC

        /** Managed throw (terminates, throws, side effects). */
        @JvmField
        val MANAGED_THROW = InstructionEffects(
            CAN_THROW or IS_TERMINATOR or HAS_SIDE_EFFECTS
        )

        /** Monitor enter/exit (barrier, may throw, side effects). */
        @JvmField
        val MONITOR = InstructionEffects(
            IS_BARRIER or CAN_THROW or HAS_SIDE_EFFECTS
        )

        /**
         * Creates an effect set from individual flag bits OR'd together.
         *
         * ```java
         * var effects = InstructionEffects.of(
         *     InstructionEffects.READS_HEAP_MEMORY | InstructionEffects.CAN_TRAP
         * );
         * ```
         */
        @JvmStatic
        fun of(bits: Int): InstructionEffects {
            return InstructionEffects(bits)
        }
    }
}
