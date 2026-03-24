package org.kgen.target.jvm.asm

fun jvm(block: JvmAssembler.() -> Unit): ByteArray {
    val asm = JvmAssembler()
    asm.block()
    return asm.toByteArray()
}
