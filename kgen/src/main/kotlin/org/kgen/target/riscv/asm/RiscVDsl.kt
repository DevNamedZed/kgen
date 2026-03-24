package org.kgen.target.riscv.asm

fun riscv(block: RiscVAssembler.() -> Unit): ByteArray {
    val asm = RiscVAssembler()
    asm.block()
    return asm.bytes()
}
