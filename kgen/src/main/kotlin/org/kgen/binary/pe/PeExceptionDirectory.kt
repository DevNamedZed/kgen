package org.kgen.binary.pe

/**
 * RUNTIME_FUNCTION entry from the `.pdata` section.
 *
 * Each entry maps a function's address range to its unwind information.
 * Entries must be sorted by [beginAddress] for binary search at runtime.
 *
 * ```java
 * var pe = PeReader.read(bytes);
 * for (var rf : pe.exceptionEntries) {
 *     System.out.printf("func [0x%x..0x%x) -> unwind at 0x%x%n",
 *         rf.beginAddress(), rf.endAddress(), rf.unwindInfoAddress());
 * }
 * ```
 */
data class RuntimeFunction(
    /** RVA of the function start. */
    val beginAddress: Int,
    /** RVA of the function end. */
    val endAddress: Int,
    /** RVA of the UNWIND_INFO structure in .xdata. */
    val unwindInfoAddress: Int,
)

/**
 * UNWIND_INFO structure from the `.xdata` section (x86-64 only).
 *
 * Describes how to unwind the stack through a function's prologue,
 * and optionally references a language-specific exception handler.
 */
data class PeUnwindInfo(
    /** Version (currently always 1). */
    val version: Int,
    /** Flags: UNW_FLAG_EHANDLER (0x01), UNW_FLAG_UHANDLER (0x02), UNW_FLAG_CHAININFO (0x04). */
    val flags: Int,
    /** Size of the function prologue in bytes. */
    val prologSize: Int,
    /** Frame register (0 = none, 1-15 = register index). */
    val frameRegister: Int,
    /** Frame register offset (scaled by 16). */
    val frameOffset: Int,
    /** Unwind codes, ordered from highest to lowest prologue offset. */
    val unwindCodes: List<UnwindCode>,
    /** RVA of the exception handler (if UNW_FLAG_EHANDLER or UNW_FLAG_UHANDLER). */
    val exceptionHandlerRva: Int? = null,
    /** RVA of the chained UNWIND_INFO (if UNW_FLAG_CHAININFO). */
    val chainedInfoRva: Int? = null,
) {
    val hasExceptionHandler: Boolean get() = flags and UNW_FLAG_EHANDLER != 0
    val hasTerminationHandler: Boolean get() = flags and UNW_FLAG_UHANDLER != 0
    val isChained: Boolean get() = flags and UNW_FLAG_CHAININFO != 0

    companion object {
        const val UNW_FLAG_EHANDLER = 0x01
        const val UNW_FLAG_UHANDLER = 0x02
        const val UNW_FLAG_CHAININFO = 0x04
    }
}

/**
 * A single unwind code describing one prologue operation.
 *
 * The operation and info are packed in the PE format as:
 * - byte 0: offset in prologue
 * - byte 1: low nibble = operation, high nibble = info
 */
data class UnwindCode(
    /** Offset within the prologue where this operation occurs. */
    val prologOffset: Int,
    /** The unwind operation. */
    val operation: UnwindOperation,
    /** Operation-specific info (interpretation depends on operation). */
    val operationInfo: Int,
    /** Extra data for operations that use 1 or 2 additional code slots. */
    val extraData: Long = 0,
)

/**
 * x86-64 unwind operation codes.
 */
enum class UnwindOperation(val code: Int) {
    /** Push a nonvolatile register. Info = register number. */
    PUSH_NONVOL(0),
    /** Allocate space on stack. Info indicates size encoding. */
    ALLOC_LARGE(1),
    /** Allocate small stack space (info * 8 + 8). */
    ALLOC_SMALL(2),
    /** Establish frame pointer (mov rbp, rsp + offset). */
    SET_FPREG(3),
    /** Save nonvolatile register to stack (scaled offset). */
    SAVE_NONVOL(4),
    /** Save nonvolatile register to stack (unscaled offset). */
    SAVE_NONVOL_FAR(5),
    /** Save XMM128 register (scaled offset). */
    SAVE_XMM128(8),
    /** Save XMM128 register (unscaled offset). */
    SAVE_XMM128_FAR(9),
    /** Push a machine frame (info = 0 or 1 for error code). */
    PUSH_MACHFRAME(10),
    ;

    companion object {
        private val byCode = entries.associateBy { it.code }
        @JvmStatic
        fun fromCode(code: Int): UnwindOperation = byCode[code]
            ?: throw IllegalArgumentException("Unknown unwind operation code: $code")
    }
}
