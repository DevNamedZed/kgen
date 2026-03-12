package org.kgen.target.clr.asm

/**
 * An opaque label for use in CIL branch instructions.
 *
 * Labels are created by [CilAssembler.defineLabel] and placed with
 * [CilAssembler.markLabel]. They cannot be constructed directly — the
 * assembler owns label allocation.
 *
 * ```java
 * var asm = new CilAssembler();
 * CilLabel target = asm.defineLabel();
 * asm.ldarg(0);
 * asm.brtrue(target);
 * asm.ldcI4Auto(0);
 * asm.ret();
 * asm.markLabel(target);
 * asm.ldcI4Auto(1);
 * asm.ret();
 * ```
 */
class CilLabel internal constructor(
    internal val id: Int,
) {
    internal var offset: Int = -1
    internal val isMarked: Boolean get() = offset >= 0

    override fun toString(): String = "Label($id)"
    override fun equals(other: Any?): Boolean = other is CilLabel && id == other.id
    override fun hashCode(): Int = id
}
