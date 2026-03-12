package org.kgen.target.clr.asm

import org.kgen.target.clr.CilSigType

/**
 * A strongly-typed local variable declaration.
 *
 * Locals are created by [CilAssembler.declareLocal]. Each local has a
 * type and an auto-assigned index. Use with [CilAssembler.ldloc],
 * [CilAssembler.stloc], and [CilAssembler.ldloca].
 *
 * ```java
 * var asm = new CilAssembler();
 * CilLocal counter = asm.declareLocal(CilSigType.I4);
 * CilLocal sum = asm.declareLocal(CilSigType.I8);
 * asm.ldcI4Auto(0);
 * asm.stloc(counter);
 * asm.ldloc(counter);
 * ```
 */
class CilLocal internal constructor(
    /** The 0-based index of this local in the locals list. */
    val index: Int,
    /** The CIL type of this local. */
    val type: CilSigType,
) {
    override fun toString(): String = "Local($index, $type)"
    override fun equals(other: Any?): Boolean = other is CilLocal && index == other.index
    override fun hashCode(): Int = index
}
