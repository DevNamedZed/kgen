package org.kgen.target.clr.asm

fun cil(block: CilAssembler.() -> Unit): ByteArray {
    val asm = CilAssembler()
    asm.block()
    return asm.toByteArray()
}
