package org.kgen.reflect.emit

import org.kgen.target.wasm.asm.WasmAssembler

/**
 * Module builder that produces WASM binary modules.
 *
 * Wraps [WasmAssembler] which handles type sections, function sections, exports,
 * and code sections internally.
 *
 * ```java
 * WasmModuleBuilder mod = ModuleBuilder.wasm("myModule");
 * mod.assembler().function("add", List.of(WasmValueType.I32, WasmValueType.I32),
 *     List.of(WasmValueType.I32), true, code -> {
 *         code.localGet(0);
 *         code.localGet(1);
 *         code.i32Add();
 *         code.end();
 *     });
 * byte[] bytes = mod.toBytes();
 * ```
 */
class WasmModuleBuilder(name: String) : ModuleBuilder(name) {

    private val asm = WasmAssembler.create()

    /** Access the underlying WASM assembler for defining functions, memory, etc. */
    fun assembler(): WasmAssembler = asm

    override fun toBytes(): ByteArray = asm.assemble()
}
