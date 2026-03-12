package org.kgen.reflect.emit

import org.kgen.reflect.MethodFlag
import org.kgen.reflect.Signature
import org.kgen.target.clr.asm.CilAssembler
import org.kgen.target.clr.asm.CilToken

/**
 * Defines a method within a [ClrTypeBuilder].
 * Use [il] to emit CIL instructions into the method body.
 */
class ClrMethodBuilder internal constructor(
    internal val name: String,
    internal val signature: Signature,
    internal val flags: Set<MethodFlag>,
    private val index: Int,
) {
    private val asm = CilAssembler()

    fun il(): CilAssembler = asm
    fun token(): CilToken = CilToken.methodDef(index + 1)
}
