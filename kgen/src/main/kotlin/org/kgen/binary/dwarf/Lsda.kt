package org.kgen.binary.dwarf

/**
 * Language-Specific Data Area (LSDA) model for `.gcc_except_table`.
 *
 * The LSDA is read by the personality function during stack unwinding to determine
 * which landing pad handles an exception thrown at a given call site.
 *
 * ```java
 * var lsda = new LsdaTable(List.of(
 *     new CallSiteEntry(0, 8, 16, 1),   // call at offset 0, length 8, landing pad at 16, action 1
 *     new CallSiteEntry(20, 4, 0, 0)    // call at offset 20, length 4, no landing pad
 * ), List.of(
 *     new ActionEntry(1, 0)             // action 1: catch type index 1, no next action
 * ), List.of("java/lang/Exception"));
 * byte[] table = LsdaWriter.write(lsda);
 * ```
 */

/**
 * A call site entry in the LSDA call site table.
 *
 * @param callOffset start of the call instruction relative to function start
 * @param callLength length of the call instruction in bytes
 * @param landingPadOffset offset of the landing pad relative to function start (0 = no handler)
 * @param actionIndex 1-based index into the action table (0 = no action / cleanup only)
 */
data class CallSiteEntry(
    val callOffset: Int,
    val callLength: Int,
    val landingPadOffset: Int,
    val actionIndex: Int,
)

/**
 * An action entry in the LSDA action table.
 *
 * @param typeIndex 1-based index into the type table (positive = catch, negative = filter, 0 = cleanup)
 * @param nextAction byte offset to the next action entry (0 = no more actions)
 */
data class ActionEntry(
    val typeIndex: Int,
    val nextAction: Int,
)

/**
 * Complete LSDA for a single function.
 *
 * @param callSites the call site table entries
 * @param actions the action table entries
 * @param typeNames catch type names (indexed 1-based from end of list)
 */
data class LsdaTable(
    val callSites: List<CallSiteEntry>,
    val actions: List<ActionEntry> = emptyList(),
    val typeNames: List<String> = emptyList(),
)
