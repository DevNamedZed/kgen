package org.kgen.ir.build

import org.kgen.ir.InstructionRef
import org.kgen.ir.Type
import org.kgen.ir.instructions.Instruction

/**
 * The primitive contract for emitting IR instructions and generating SSA names.
 *
 * This interface captures the two fundamental operations needed to build IR:
 * 1. [emit] — append an instruction to the current insertion point
 * 2. [nextRef] — generate a fresh [InstructionRef] with a unique SSA name
 *
 * [ModuleBuilder] implements this interface directly. The typed `InstructionSet`
 * implementations (e.g., `ArithmeticInstructionSet`) hold a sink reference
 * and delegate to it, enabling composition of multiple instruction sets over
 * a single instruction stream and SSA counter.
 *
 * Consumers should not typically implement this interface — use [ModuleBuilder]
 * or the DSL builders instead. This interface exists for the internal wiring
 * of the typed builder API.
 *
 * ```java
 * // Internal usage pattern (InstructionSet implementations):
 * class ArithmeticInstructionSetImpl implements ArithmeticInstructionSet {
 *     private final InstructionSink sink;
 *
 *     public Value add(Value lhs, Value rhs) {
 *         var ref = sink.nextRef(lhs.getType());
 *         sink.emit(new Add(ref, lhs, rhs, false, false));
 *         return ref;
 *     }
 * }
 * ```
 */
interface InstructionSink {

    /**
     * Append an instruction to the current insertion point.
     *
     * The instruction is added to the end of the current basic block. If category
     * constraints are active, the instruction's category is validated before insertion.
     *
     * @param instruction the instruction to emit
     * @throws IllegalStateException if no insertion point is set, or if the instruction's
     *   category is not in the allowed set
     */
    fun emit(instruction: Instruction)

    /**
     * Generate a fresh [InstructionRef] with a unique SSA name for the given type.
     *
     * Each call produces a new name (e.g., `%0`, `%1`, `%2`) that is unique within
     * the enclosing function. The returned ref is intended to be used as the `dest`
     * field of an instruction data class.
     *
     * @param type the IR type of the value that the instruction will produce
     * @return a fresh instruction reference with a unique SSA name
     */
    fun nextRef(type: Type): InstructionRef
}
