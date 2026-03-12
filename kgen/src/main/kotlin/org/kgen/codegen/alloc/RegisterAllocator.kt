package org.kgen.codegen.alloc

import org.kgen.ir.IrFunction

/**
 * A target-independent handle for a physical register.
 *
 * Each backend maps its arch-specific register type to/from this for use
 * with the allocator. The allocator reasons about register classes, encoding,
 * and constraints without knowing the target.
 *
 * ```java
 * var rax = new PhysicalRegister("RAX", 0, gpClass);
 * ```
 */
data class PhysicalRegister(
    val name: String,
    val encoding: Int,
    val registerClass: RegisterClass,
) {
    override fun toString(): String = name
}

/**
 * A class of registers with the same capabilities (GP, FP, SIMD, etc.).
 */
data class RegisterClass(
    val name: String,
    val registers: List<PhysicalRegister>,
) {
    val count: Int get() = registers.size
    override fun toString(): String = name
}

/**
 * Where a value lives after register allocation.
 */
sealed interface ValueLocation {
    /** Value lives in a physical register. */
    data class Register(val reg: PhysicalRegister) : ValueLocation
    /** Value lives on the stack at a negative offset from the frame pointer. */
    data class SpillSlot(val offset: Int) : ValueLocation
}

/**
 * Identifies instructions that implicitly clobber specific registers.
 *
 * The allocator uses this to avoid placing live values in registers that
 * will be destroyed by an instruction.
 */
enum class InstructionClobber {
    /** Integer division (x86 idiv/div): clobbers RAX and RDX. */
    INT_DIV,
    /** Shift with variable count (x86 shl/shr cl): clobbers RCX/CL. */
    VARIABLE_SHIFT,
    /** Function call: clobbers all caller-saved registers. */
    CALL,
}

/**
 * Describes the register file and calling convention constraints for a target.
 *
 * Built by each backend and passed to the [RegisterAllocator].
 *
 * ```java
 * var constraints = new RegisterConstraints.Builder()
 *     .allocatable(List.of(rcx, rsi, rdi, r8, r9, rbx, r12, r13, r14, r15))
 *     .reserved(List.of(rsp, rbp, r10, r11, rax, rdx))
 *     .calleeSaved(Set.of(rbx, r12, r13, r14, r15))
 *     .paramRegisters(List.of(rdi, rsi, rdx, rcx, r8, r9))
 *     .returnRegisters(List.of(rax))
 *     .clobber(InstructionClobber.INT_DIV, Set.of(rax, rdx))
 *     .build();
 * ```
 */
data class RegisterConstraints(
    /** Registers available for general allocation. */
    val allocatable: List<PhysicalRegister>,
    /** Registers reserved by the ABI (stack pointer, frame pointer, scratch). */
    val reserved: Set<PhysicalRegister>,
    /** Registers preserved across calls (callee-saved). */
    val calleeSaved: Set<PhysicalRegister>,
    /** Parameter passing registers, in ABI order. */
    val paramRegisters: List<PhysicalRegister>,
    /** Return value registers. */
    val returnRegisters: List<PhysicalRegister>,
    /** Instruction-specific clobber sets. */
    val clobbers: Map<InstructionClobber, Set<PhysicalRegister>> = emptyMap(),
) {
    class Builder {
        private var allocatable: List<PhysicalRegister> = emptyList()
        private var reserved: Set<PhysicalRegister> = emptySet()
        private var calleeSaved: Set<PhysicalRegister> = emptySet()
        private var paramRegisters: List<PhysicalRegister> = emptyList()
        private var returnRegisters: List<PhysicalRegister> = emptyList()
        private val clobbers = mutableMapOf<InstructionClobber, Set<PhysicalRegister>>()

        fun allocatable(regs: List<PhysicalRegister>) = apply { allocatable = regs }
        fun reserved(regs: Set<PhysicalRegister>) = apply { reserved = regs }
        fun calleeSaved(regs: Set<PhysicalRegister>) = apply { calleeSaved = regs }
        fun paramRegisters(regs: List<PhysicalRegister>) = apply { paramRegisters = regs }
        fun returnRegisters(regs: List<PhysicalRegister>) = apply { returnRegisters = regs }
        fun clobber(kind: InstructionClobber, regs: Set<PhysicalRegister>) = apply { clobbers[kind] = regs }

        fun build() = RegisterConstraints(
            allocatable, reserved, calleeSaved, paramRegisters, returnRegisters, clobbers
        )
    }

    companion object {
        @JvmStatic
        fun builder() = Builder()
    }
}

/**
 * A point where a value must be saved to a spill slot and later reloaded,
 * because its register is temporarily clobbered by an instruction.
 *
 * Unlike a full spill (where the value lives on the stack permanently),
 * a split keeps the value in a register for most of its lifetime, with
 * a brief save/reload around the clobber point.
 *
 * ```java
 * // Save RAX at position 5, reload at position 6
 * var split = new SplitPoint("x", 5, 6, -8);
 * ```
 */
data class SplitPoint(
    val valueName: String,
    val saveBeforePosition: Int,
    val reloadAfterPosition: Int,
    val spillOffset: Int,
)

/**
 * Result of register allocation.
 */
data class RegisterAssignment(
    /** Where each value lives (register or spill slot). */
    val locations: Map<String, ValueLocation>,
    /** Number of spill slots used. */
    val spillSlots: Int,
    /** Callee-saved registers that were used (need save/restore in prologue/epilogue). */
    val usedCalleeRegisters: Set<PhysicalRegister>,
    /** Parameters that need moves at function entry (param name → ABI register index). */
    val paramMoves: Map<String, Int> = emptyMap(),
    /** Split points where values need save/reload around clobber instructions. */
    val splitPoints: List<SplitPoint> = emptyList(),
)

/**
 * A point in the instruction stream where specific registers are clobbered.
 *
 * The allocator avoids assigning a live value to a register that would be
 * clobbered at a point within the value's live range.
 *
 * ```java
 * // idiv at instruction position 5 clobbers RAX and RDX
 * var event = new ClobberEvent(5, Set.of(rax, rdx));
 * ```
 */
data class ClobberEvent(
    val position: Int,
    val clobberedRegisters: Set<PhysicalRegister>,
)

/**
 * Allocates physical registers for IR values.
 *
 * Implementations include [org.kgen.target.x86.codegen.LinearScanAllocator] (fast, for JIT)
 * and potentially graph coloring (better quality, for AOT).
 *
 * ```java
 * RegisterAllocator allocator = new LinearScanRegisterAllocator();
 * var analysis = new LivenessAnalysis(fn);
 * var assignment = allocator.allocate(fn, analysis.intervals(), constraints);
 * ```
 */
interface RegisterAllocator {
    /**
     * Assign physical registers to IR values.
     *
     * @param fn the function being compiled
     * @param intervals live intervals from [LivenessAnalysis]
     * @param constraints target register constraints
     * @return register assignment mapping values to locations
     */
    fun allocate(
        fn: IrFunction,
        intervals: List<LiveInterval>,
        constraints: RegisterConstraints,
    ): RegisterAssignment
}
