package org.kgen.ir

/**
 * A reference to a basic block by label. Used in branch targets and phi nodes.
 *
 * A [BlockRef] is the [Value] form of a [BasicBlock]'s label. Its [type] is always
 * [Type.Label], which is not a first-class value type — it cannot be stored in memory
 * or computed with arithmetic. [BlockRef] values appear only as:
 *
 * - Successor operands of terminator instructions (`br`, `condbr`, `switch`)
 * - Predecessor identifiers in `phi` node operand pairs
 *
 * Printed as `%label` in IR text (local label with a `%` prefix).
 *
 * ```java
 * // Branch unconditionally to the "merge" block
 * var mergeRef = new BlockRef("merge");
 * builder.br(mergeRef);
 *
 * // Conditional branch
 * var thenRef = new BlockRef("then");
 * var elseRef = new BlockRef("else");
 * builder.condBr(condition, thenRef, elseRef);
 *
 * // Phi node — value from predecessor blocks
 * var phiPairs = List.of(
 *     new PhiIncoming(Type.i32(0), new BlockRef("entry")),
 *     new PhiIncoming(increment,   new BlockRef("loop"))
 * );
 * Value counter = builder.phi(Type.I32, phiPairs);
 * ```
 *
 * @param label the unique label of the target block within the enclosing function (matches [BasicBlock.label])
 *
 * @see BasicBlock
 * @see Type.Label
 * @see Value
 */
data class BlockRef(val label: String) : Value {
    override val type: Type get() = Type.Label
    override val name: String get() = label
}
