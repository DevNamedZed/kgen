package org.kgen.target.x86.asm

fun x86(block: X86Assembler.() -> Unit): ByteArray {
    val asm = X86Assembler()
    asm.block()
    return asm.toByteArray()
}
