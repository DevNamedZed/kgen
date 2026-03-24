package org.kgen.target.wasm.asm

fun wasm(block: WasmAssembler.() -> Unit): ByteArray {
    val asm = WasmAssembler.create()
    asm.block()
    return asm.assemble()
}
