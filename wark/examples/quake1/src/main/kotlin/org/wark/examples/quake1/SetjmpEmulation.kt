package org.wark.examples.quake1

import org.wark.HostFunction
import org.wark.WarkImports
import org.wark.exec.WasmException

/**
 * Provides setjmp/longjmp emulation and compiler runtime functions for WASM
 * modules compiled with LLVM/wasi-sdk.
 *
 * __wasm_longjmp throws a [WasmException] with tag index 0 (the imported
 * __c_longjmp tag). The interpreter's TRY/CATCH handling catches this and
 * routes it to the correct setjmp call site.
 *
 * Also provides __multi3 for 128-bit integer multiplication.
 */
class SetjmpEmulation {

    fun registerImports(builder: WarkImports.Builder) {
        builder.function("env", "__wasm_setjmp", wasmSetjmp())
        builder.function("env", "__wasm_longjmp", wasmLongjmp())
        builder.function("env", "__wasm_setjmp_test", wasmSetjmpTest())
        builder.function("env", "__multi3", multi3())
    }

    private fun wasmSetjmp(): HostFunction = HostFunction { instance, args ->
        val bufferAddress = args[0].toInt()
        val label = args[1].toInt()
        val tableAddress = args[2].toInt()
        val memory = instance.memory()

        memory.writeI32(bufferAddress, label)
        memory.writeI32(bufferAddress + 4, tableAddress)
        longArrayOf()
    }

    private fun wasmLongjmp(): HostFunction = HostFunction { instance, args ->
        val bufferAddress = args[0].toInt()
        val value = args[1].toInt()
        val longjmpValue = if (value == 0) 1 else value

        if (instance.module.runtime.executionMode == org.wark.ExecutionMode.INTERPRET) {
            throw WasmException(0, longArrayOf(bufferAddress.toLong(), longjmpValue.toLong()))
        } else {
            instance.setExceptionState(0, bufferAddress.toLong(), longjmpValue.toLong())
            longArrayOf()
        }
    }

    private fun wasmSetjmpTest(): HostFunction = HostFunction { instance, args ->
        val bufferAddress = args[0].toInt()
        val tableAddress = args[1].toInt()
        val memory = instance.memory()

        val savedTable = memory.readI32(bufferAddress + 4)
        val matches = if (savedTable == tableAddress) 1L else 0L
        longArrayOf(matches)
    }

    private fun multi3(): HostFunction = HostFunction { instance, args ->
        val resultAddress = args[0].toInt()
        val aLow = args[1]
        val aHigh = args[2]
        val bLow = args[3]
        val bHigh = args[4]
        val memory = instance.memory()

        val resultLow = aLow * bLow
        val unsignedHighProduct = Math.multiplyHigh(aLow, bLow) +
            ((aLow shr 63) and bLow) +
            ((bLow shr 63) and aLow)
        val resultHigh = unsignedHighProduct + aHigh * bLow + aLow * bHigh

        memory.writeI64(resultAddress, resultLow)
        memory.writeI64(resultAddress + 8, resultHigh)
        longArrayOf()
    }
}
