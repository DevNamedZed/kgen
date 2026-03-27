package org.wark

import org.kgen.target.wasm.asm.WasmAssembler
import kotlin.test.assertEquals

abstract class WasmTestBase {

    protected fun buildWasmBytes(block: WasmAssembler.() -> Unit): ByteArray {
        val assembler = WasmAssembler.create()
        assembler.block()
        return assembler.assemble()
    }

    protected fun run(bytes: ByteArray, funcName: String, mode: ExecutionMode, vararg args: Long): LongArray {
        val instance = WarkRuntime.create(WasmTarget.V2_0, mode).load(bytes).instantiate()
        if (mode == ExecutionMode.INTERPRET) {
            instance.setInstructionLimit(Long.MAX_VALUE)
        }
        return instance.call(funcName, *args)
    }

    protected fun executeInBothModes(bytes: ByteArray, funcName: String, vararg args: Long): Pair<LongArray, LongArray> {
        val interpResult = run(bytes, funcName, ExecutionMode.INTERPRET, *args)
        val jitResult = run(bytes, funcName, ExecutionMode.JIT, *args)
        return interpResult to jitResult
    }

    protected fun assertBothEqual(expected: Long, bytes: ByteArray, funcName: String, vararg args: Long) {
        val (interp, jit) = executeInBothModes(bytes, funcName, *args)
        assertEquals(expected.toInt(), interp[0].toInt(), "Interpreter: $funcName(${args.toList()})")
        assertEquals(expected.toInt(), jit[0].toInt(), "JIT: $funcName(${args.toList()})")
        assertEquals(interp[0].toInt(), jit[0].toInt(), "Interp vs JIT: $funcName(${args.toList()})")
    }

    protected fun assertBothEqualI64(expected: Long, bytes: ByteArray, funcName: String, vararg args: Long) {
        val (interp, jit) = executeInBothModes(bytes, funcName, *args)
        assertEquals(expected, interp[0], "Interpreter i64: $funcName(${args.toList()})")
        assertEquals(expected, jit[0], "JIT i64: $funcName(${args.toList()})")
    }

    protected fun assertBothEqualF32(expected: Float, bytes: ByteArray, funcName: String, vararg args: Long) {
        val (interp, jit) = executeInBothModes(bytes, funcName, *args)
        val interpFloat = java.lang.Float.intBitsToFloat(interp[0].toInt())
        val jitFloat = java.lang.Float.intBitsToFloat(jit[0].toInt())
        assertEquals(expected, interpFloat, 0.001f, "Interpreter f32: $funcName(${args.toList()})")
        assertEquals(expected, jitFloat, 0.001f, "JIT f32: $funcName(${args.toList()})")
    }

    protected fun assertBothEqualF64(expected: Double, bytes: ByteArray, funcName: String, vararg args: Long) {
        val (interp, jit) = executeInBothModes(bytes, funcName, *args)
        val interpDouble = java.lang.Double.longBitsToDouble(interp[0])
        val jitDouble = java.lang.Double.longBitsToDouble(jit[0])
        assertEquals(expected, interpDouble, 0.001, "Interpreter f64: $funcName(${args.toList()})")
        assertEquals(expected, jitDouble, 0.001, "JIT f64: $funcName(${args.toList()})")
    }

    companion object {
        @JvmStatic fun floatBits(f: Float): Long = java.lang.Float.floatToRawIntBits(f).toLong() and 0xFFFFFFFFL

        @JvmStatic fun doubleBits(d: Double): Long = java.lang.Double.doubleToRawLongBits(d)
    }
}
