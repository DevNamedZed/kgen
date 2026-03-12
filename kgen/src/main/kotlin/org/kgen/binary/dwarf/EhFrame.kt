package org.kgen.binary.dwarf

/**
 * DWARF Call Frame Information (CFI) model for `.eh_frame` / `.debug_frame` sections.
 *
 * A CIE defines shared unwinding parameters (alignment factors, return register).
 * Each FDE references a CIE and describes how to unwind a specific function.
 *
 * ```java
 * var cie = new CieEntry(1, 1, -8, 16); // x86-64 defaults
 * var fde = new FdeEntry("main", 0, 64, List.of(
 *     CfiInstruction.defCfa(7, 8),       // RSP + 8
 *     CfiInstruction.offset(16, 1)       // RIP saved at CFA-8
 * ));
 * byte[] ehFrame = EhFrameWriter.write(cie, List.of(fde));
 * ```
 */

/**
 * Common Information Entry — shared by all FDEs with the same unwinding convention.
 *
 * @param version DWARF version for CFI (typically 1 for .eh_frame, 4 for .debug_frame)
 * @param codeAlignFactor alignment of code addresses (1 for byte-addressable architectures)
 * @param dataAlignFactor alignment of data (stack slot size, negative = grows down; -8 for x86-64)
 * @param returnAddressRegister DWARF register number for the return address (16 for x86-64 RIP)
 * @param initialInstructions CFI instructions that define the initial unwinding state
 * @param augmentation augmentation string (typically "zR" for .eh_frame)
 * @param pointerEncoding FDE pointer encoding (0x1B = pcrel|sdata4 for typical .eh_frame)
 */
data class CieEntry(
    val version: Int = 1,
    val codeAlignFactor: Int = 1,
    val dataAlignFactor: Int = -8,
    val returnAddressRegister: Int = 16,
    val initialInstructions: List<CfiInstruction> = emptyList(),
    val augmentation: String = "zR",
    val pointerEncoding: Int = 0x1B,
)

/**
 * Frame Description Entry — describes unwinding for a single function.
 *
 * @param functionName symbol name of the function
 * @param codeOffset start offset of the function in the text section
 * @param codeLength length of the function in bytes
 * @param instructions CFI instructions for this function
 */
data class FdeEntry(
    val functionName: String,
    val codeOffset: Long,
    val codeLength: Long,
    val instructions: List<CfiInstruction>,
)

/**
 * DWARF Call Frame Instruction. Each instruction modifies the virtual unwinding table
 * to describe how registers are saved/restored at each point in the code.
 */
sealed interface CfiInstruction {

    /** DW_CFA_def_cfa: Set CFA to register + offset. */
    data class DefCfa(val register: Int, val offset: Int) : CfiInstruction

    /** DW_CFA_def_cfa_offset: Change only the CFA offset. */
    data class DefCfaOffset(val offset: Int) : CfiInstruction

    /** DW_CFA_def_cfa_register: Change only the CFA register. */
    data class DefCfaRegister(val register: Int) : CfiInstruction

    /** DW_CFA_offset: Register saved at CFA + (factored offset * dataAlignFactor). */
    data class Offset(val register: Int, val factoredOffset: Int) : CfiInstruction

    /** DW_CFA_advance_loc: Advance the location counter by delta * codeAlignFactor. */
    data class AdvanceLoc(val delta: Int) : CfiInstruction

    /** DW_CFA_restore: Restore register to its initial rule. */
    data class Restore(val register: Int) : CfiInstruction

    /** DW_CFA_remember_state: Push the current register state. */
    data object RememberState : CfiInstruction

    /** DW_CFA_restore_state: Pop and restore register state. */
    data object RestoreState : CfiInstruction

    /** DW_CFA_nop: No operation (padding). */
    data object Nop : CfiInstruction

    companion object {
        @JvmStatic fun defCfa(register: Int, offset: Int) = DefCfa(register, offset)
        @JvmStatic fun defCfaOffset(offset: Int) = DefCfaOffset(offset)
        @JvmStatic fun defCfaRegister(register: Int) = DefCfaRegister(register)
        @JvmStatic fun offset(register: Int, factoredOffset: Int) = Offset(register, factoredOffset)
        @JvmStatic fun advanceLoc(delta: Int) = AdvanceLoc(delta)
        @JvmStatic fun restore(register: Int) = Restore(register)
        @JvmStatic fun rememberState() = RememberState
        @JvmStatic fun restoreState() = RestoreState
        @JvmStatic fun nop() = Nop
    }
}
