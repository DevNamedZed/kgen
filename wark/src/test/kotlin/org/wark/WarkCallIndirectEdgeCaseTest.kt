package org.wark

import org.junit.jupiter.api.Test
import org.kgen.target.wasm.WasmValueType
import org.kgen.target.wasm.WasmRefType
import org.kgen.target.wasm.asm.WasmAssembler
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WarkCallIndirectEdgeCaseTest {

    private fun buildWasmBytes(block: WasmAssembler.() -> Unit): ByteArray {
        val assembler = WasmAssembler.create()
        assembler.block()
        return assembler.assemble()
    }

    @Test
    fun multipleElementSegments() {
        val bytes = buildWasmBytes {
            table("funcs", WasmRefType.FUNCREF, 8)

            function("addTen", listOf(WasmValueType.I32), listOf(WasmValueType.I32)) { func, asm ->
                asm.localGet(func.getParameter(0)); asm.i32Const(10); asm.i32Add()
            }
            function("addTwenty", listOf(WasmValueType.I32), listOf(WasmValueType.I32)) { func, asm ->
                asm.localGet(func.getParameter(0)); asm.i32Const(20); asm.i32Add()
            }
            function("addThirty", listOf(WasmValueType.I32), listOf(WasmValueType.I32)) { func, asm ->
                asm.localGet(func.getParameter(0)); asm.i32Const(30); asm.i32Add()
            }

            activeElement(0, 1, listOf(0, 1))
            activeElement(0, 5, listOf(2))

            function("apply", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                asm.localGet(func.getParameter(1))
                asm.localGet(func.getParameter(0))
                asm.callIndirect(0, 0)
            }
        }

        for (mode in listOf(ExecutionMode.INTERPRET, ExecutionMode.JIT)) {
            val instance = WarkRuntime.create(WasmTarget.MVP, mode).load(bytes).instantiate()
            assertEquals(15L, instance.call("apply", 1, 5)[0], "$mode: slot 1 = addTen(5)")
            assertEquals(25L, instance.call("apply", 2, 5)[0], "$mode: slot 2 = addTwenty(5)")
            assertEquals(35L, instance.call("apply", 5, 5)[0], "$mode: slot 5 = addThirty(5)")
        }
    }

    @Test
    fun callIndirectTableIndexOutOfBounds() {
        val bytes = buildWasmBytes {
            table("funcs", WasmRefType.FUNCREF, 2)

            function("identity", listOf(WasmValueType.I32), listOf(WasmValueType.I32)) { func, asm ->
                asm.localGet(func.getParameter(0))
            }

            activeElement(0, 0, listOf(0))

            function("dispatch", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                asm.localGet(func.getParameter(1))
                asm.localGet(func.getParameter(0))
                asm.callIndirect(0, 0)
            }
        }

        // Interpreter: valid call works, OOB traps
        val interpInstance = WarkRuntime.create(WasmTarget.MVP, ExecutionMode.INTERPRET).load(bytes).instantiate()
        assertEquals(42L, interpInstance.call("dispatch", 0, 42)[0], "interp: valid index works")
        assertFailsWith<WasmTrap>("interp: out-of-bounds should trap") {
            interpInstance.call("dispatch", 99, 42)
        }

        // JIT: valid call works (OOB would halt the JVM, can't test in-process)
        val jitInstance = WarkRuntime.create(WasmTarget.MVP, ExecutionMode.JIT).load(bytes).instantiate()
        assertEquals(42L, jitInstance.call("dispatch", 0, 42)[0], "jit: valid index works")
    }
}
