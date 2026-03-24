package org.kgen.target.arm64.asm

fun arm64(block: Arm64Assembler.() -> Unit): ByteArray {
    val asm = Arm64Assembler()
    asm.block()
    return asm.bytes()
}
