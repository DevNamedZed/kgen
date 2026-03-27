package org.wark.examples.doom

import org.kgen.target.wasm.WasmValueType
import org.kgen.target.wasm.asm.WasmAssembler
import org.wark.ExecutionMode
import org.wark.WarkRuntime
import org.wark.WasmTarget

object FixedMathTest {

    @JvmStatic
    fun main(args: Array<String>) {
        val asm = WasmAssembler.create()

        // FixedMul(a, b) = (i64(a) * i64(b)) >> 16
        asm.beginFunction("fixedMul", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true)
        asm.localGet(0)
        asm.i64ExtendI32S()
        asm.localGet(1)
        asm.i64ExtendI32S()
        asm.i64Mul()
        asm.i64Const(16)
        asm.i64ShrU()
        asm.i32WrapI64()
        asm.endFunction()

        // FixedDiv(a, b) = (i64(a) << 16) / i64(b) [simplified, no overflow check]
        asm.beginFunction("fixedDiv", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true)
        asm.localGet(0)
        asm.i64ExtendI32S()
        asm.i64Const(16)
        asm.i64Shl()
        asm.localGet(1)
        asm.i64ExtendI32S()
        asm.i64DivS()
        asm.i32WrapI64()
        asm.endFunction()

        // shr_u_test(a, b) = a >>> b (unsigned shift)
        asm.beginFunction("shrU", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true)
        asm.localGet(0)
        asm.localGet(1)
        asm.i32ShrU()
        asm.endFunction()

        // shr_s_test(a, b) = a >> b (signed shift)
        asm.beginFunction("shrS", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true)
        asm.localGet(0)
        asm.localGet(1)
        asm.i32ShrS()
        asm.endFunction()

        val wasmBytes = asm.assemble()

        println("=== Interpreter ===")
        val interpInstance = WarkRuntime.create(WasmTarget.MVP, ExecutionMode.INTERPRET)
            .load(wasmBytes).instantiate()

        println("=== JIT ===")
        val jitInstance = WarkRuntime.create(WasmTarget.MVP, ExecutionMode.JIT)
            .load(wasmBytes).instantiate()

        var failures = 0

        // FixedMul tests
        val mulTests = listOf(
            10L to 65536L,
            -10L to 65536L,
            65536L to 65536L,
            -65536L to 65536L,
            100L to 200L,
            -100L to 200L,
            0x7FFFL to 0x7FFFL,
            -1000000L to 50000L,
            1000000L to -50000L,
            -1000000L to -50000L,
        )

        println("\nFixedMul(a, b):")
        for ((a, b) in mulTests) {
            val interp = interpInstance.call("fixedMul", a, b)[0]
            val jit = jitInstance.call("fixedMul", a, b)[0]
            val match = interp == jit
            if (!match) { failures++ }
            val tag = if (match) { "OK" } else { "FAIL" }
            println("  ($a, $b): interp=$interp jit=$jit [$tag]")
        }

        // FixedDiv tests
        val divTests = listOf(
            10L to 3L,
            -10L to 3L,
            65536L to 2L,
            -65536L to 2L,
            0L to 100L,
            983040L to 1302L,
            -983040L to 1302L,
            100000L to -7L,
        )

        println("\nFixedDiv(a, b):")
        for ((a, b) in divTests) {
            val interp = interpInstance.call("fixedDiv", a, b)[0]
            val jit = jitInstance.call("fixedDiv", a, b)[0]
            val match = interp == jit
            if (!match) { failures++ }
            val tag = if (match) { "OK" } else { "FAIL" }
            println("  ($a, $b): interp=$interp jit=$jit [$tag]")
        }

        // Shift tests — critical for P_InterceptVector
        val shiftTests = listOf(
            100L to 8L,
            -100L to 8L,
            0x7FFFFFFFL to 8L,
            -1L to 8L,
            -1000000L to 8L,
            1000000L to 8L,
            -27216888L to 8L,  // from DOOM trace
        )

        println("\ni32.shr_u(a, b):")
        for ((a, b) in shiftTests) {
            val interp = interpInstance.call("shrU", a, b)[0]
            val jit = jitInstance.call("shrU", a, b)[0]
            val match = interp == jit
            if (!match) { failures++ }
            val tag = if (match) { "OK" } else { "FAIL" }
            println("  ($a, $b): interp=$interp jit=$jit [$tag]")
        }

        println("\ni32.shr_s(a, b):")
        for ((a, b) in shiftTests) {
            val interp = interpInstance.call("shrS", a, b)[0].toInt()
            val jit = jitInstance.call("shrS", a, b)[0].toInt()
            val match = interp == jit
            if (!match) { failures++ }
            val tag = if (match) { "OK" } else { "FAIL" }
            println("  ($a, $b): interp=$interp jit=$jit [$tag]")
        }

        println("\n${if (failures == 0) { "ALL PASSED" } else { "$failures FAILURES" }}")

        // BUT: check if the Long representation difference matters for i64 operations
        // If shr_s result feeds into i64.extend_i32_s, the Long representation matters
        println("\nCritical: i64.extend_i32_s after i32.shr_s(-1000000, 8):")
        val interpShr = interpInstance.call("shrS", -1000000L, 8L)[0]
        val jitShr = jitInstance.call("shrS", -1000000L, 8L)[0]
        println("  interp raw Long: $interpShr (0x${interpShr.toULong().toString(16)})")
        println("  jit raw Long: $jitShr (0x${jitShr.toULong().toString(16)})")
        println("  interp as i32: ${interpShr.toInt()}")
        println("  jit as i32: ${jitShr.toInt()}")
        println("  SAME i32? ${interpShr.toInt() == jitShr.toInt()}")
    }
}
