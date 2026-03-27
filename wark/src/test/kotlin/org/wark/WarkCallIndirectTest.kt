package org.wark

import org.junit.jupiter.api.Test
import org.kgen.target.wasm.WasmRefType
import org.kgen.target.wasm.WasmValueType
import org.kgen.target.wasm.asm.WasmAssembler
import kotlin.test.assertEquals

class WarkCallIndirectTest {

    private fun buildWasmBytes(block: WasmAssembler.() -> Unit): ByteArray {
        val assembler = WasmAssembler.create()
        assembler.block()
        return assembler.assemble()
    }

    @Test
    fun callIndirectDispatch() {
        val bytes = buildWasmBytes {
            table("funcs", WasmRefType.FUNCREF, 2)

            function("add", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32)) { func, asm ->
                asm.localGet(func.getParameter(0))
                asm.localGet(func.getParameter(1))
                asm.i32Add()
            }
            function("mul", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32)) { func, asm ->
                asm.localGet(func.getParameter(0))
                asm.localGet(func.getParameter(1))
                asm.i32Mul()
            }

            activeElement(0, 0, listOf(0, 1))

            function("dispatch", listOf(WasmValueType.I32, WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                asm.localGet(func.getParameter(1))
                asm.localGet(func.getParameter(2))
                asm.localGet(func.getParameter(0))
                asm.callIndirect(0, 0)
            }
        }

        val runtime = WarkRuntime.create(WasmTarget.MVP, ExecutionMode.INTERPRET)
        val instance = runtime.load(bytes).instantiate()

        assertEquals(7L, instance.call("dispatch", 0, 3, 4)[0])
        assertEquals(12L, instance.call("dispatch", 1, 3, 4)[0])
    }

    @Test
    fun brTableSwitch() {
        val bytes = buildWasmBytes {
            val func = beginFunction("classify", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true)

            beginBlock()
            beginBlock()
            beginBlock()
            localGet(func.getParameter(0))
            // brTable: labels=[0,1,2], default=2
            // IntArray format: [target0, target1, target2, default]
            brTable(intArrayOf(0, 1, 2, 2))
            end()
            // label 0 lands here
            i32Const(10)
            br(2)
            end()
            // label 1 lands here
            i32Const(20)
            br(1)
            end()
            // label 2 (default) lands here
            i32Const(30)

            endFunction()
        }

        val runtime = WarkRuntime.create(WasmTarget.MVP, ExecutionMode.INTERPRET)
        val instance = runtime.load(bytes).instantiate()

        assertEquals(10L, instance.call("classify", 0)[0])
        assertEquals(20L, instance.call("classify", 1)[0])
        assertEquals(30L, instance.call("classify", 2)[0])
        assertEquals(30L, instance.call("classify", 99)[0])
    }

    @Test
    fun callIndirectWithMultipleFunctions() {
        val bytes = buildWasmBytes {
            table("funcs", WasmRefType.FUNCREF, 4)

            function("identity", listOf(WasmValueType.I32), listOf(WasmValueType.I32)) { func, asm ->
                asm.localGet(func.getParameter(0))
            }
            function("double", listOf(WasmValueType.I32), listOf(WasmValueType.I32)) { func, asm ->
                asm.localGet(func.getParameter(0))
                asm.i32Const(2)
                asm.i32Mul()
            }
            function("triple", listOf(WasmValueType.I32), listOf(WasmValueType.I32)) { func, asm ->
                asm.localGet(func.getParameter(0))
                asm.i32Const(3)
                asm.i32Mul()
            }
            function("negate", listOf(WasmValueType.I32), listOf(WasmValueType.I32)) { func, asm ->
                asm.i32Const(0)
                asm.localGet(func.getParameter(0))
                asm.i32Sub()
            }

            activeElement(0, 0, listOf(0, 1, 2, 3))

            function("apply", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                asm.localGet(func.getParameter(1))
                asm.localGet(func.getParameter(0))
                asm.callIndirect(0, 0)
            }
        }

        val runtime = WarkRuntime.create(WasmTarget.MVP, ExecutionMode.INTERPRET)
        val instance = runtime.load(bytes).instantiate()

        assertEquals(5L, instance.call("apply", 0, 5)[0])
        assertEquals(10L, instance.call("apply", 1, 5)[0])
        assertEquals(15L, instance.call("apply", 2, 5)[0])
    }

    @Test
    fun callIndirectWithNonZeroTableOffset() {
        val bytes = buildWasmBytes {
            table("funcs", WasmRefType.FUNCREF, 4)

            function("add", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32)) { func, asm ->
                asm.localGet(func.getParameter(0))
                asm.localGet(func.getParameter(1))
                asm.i32Add()
            }
            function("mul", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32)) { func, asm ->
                asm.localGet(func.getParameter(0))
                asm.localGet(func.getParameter(1))
                asm.i32Mul()
            }

            // Element segment at offset 1 (not 0!) — slot 0 is empty
            activeElement(0, 1, listOf(0, 1))

            function("dispatch", listOf(WasmValueType.I32, WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                asm.localGet(func.getParameter(1))
                asm.localGet(func.getParameter(2))
                asm.localGet(func.getParameter(0))
                asm.callIndirect(0, 0)
            }
        }

        // Test with interpreter
        val interpInstance = WarkRuntime.create(WasmTarget.MVP, ExecutionMode.INTERPRET).load(bytes).instantiate()
        // Table slot 1 = add, slot 2 = mul
        assertEquals(7L, interpInstance.call("dispatch", 1, 3, 4)[0], "slot 1 = add(3,4)")
        assertEquals(12L, interpInstance.call("dispatch", 2, 3, 4)[0], "slot 2 = mul(3,4)")

        // Test with JIT — this was broken before the offset fix
        val jitInstance = WarkRuntime.create(WasmTarget.MVP, ExecutionMode.JIT).load(bytes).instantiate()
        assertEquals(7L, jitInstance.call("dispatch", 1, 3, 4)[0], "JIT slot 1 = add(3,4)")
        assertEquals(12L, jitInstance.call("dispatch", 2, 3, 4)[0], "JIT slot 2 = mul(3,4)")
    }

    @Test
    fun callIndirectWithNonZeroTableOffsetLargeOffset() {
        val bytes = buildWasmBytes {
            table("funcs", WasmRefType.FUNCREF, 10)

            function("add10", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32)) { func, asm ->
                asm.localGet(func.getParameter(0))
                asm.i32Const(10)
                asm.i32Add()
            }
            function("add20", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32)) { func, asm ->
                asm.localGet(func.getParameter(0))
                asm.i32Const(20)
                asm.i32Add()
            }

            // Element segment at offset 5
            activeElement(0, 5, listOf(0, 1))

            function("apply", listOf(WasmValueType.I32, WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                asm.localGet(func.getParameter(1))
                asm.localGet(func.getParameter(2))
                asm.localGet(func.getParameter(0))
                asm.callIndirect(0, 0)
            }
        }

        val interpInstance = WarkRuntime.create(WasmTarget.MVP, ExecutionMode.INTERPRET).load(bytes).instantiate()
        assertEquals(17L, interpInstance.call("apply", 5, 7, 0)[0], "slot 5 = add10(7)")
        assertEquals(27L, interpInstance.call("apply", 6, 7, 0)[0], "slot 6 = add20(7)")

        val jitInstance = WarkRuntime.create(WasmTarget.MVP, ExecutionMode.JIT).load(bytes).instantiate()
        assertEquals(17L, jitInstance.call("apply", 5, 7, 0)[0], "JIT slot 5 = add10(7)")
        assertEquals(27L, jitInstance.call("apply", 6, 7, 0)[0], "JIT slot 6 = add20(7)")
    }

    @Test
    fun callIndirectDispatchJit() {
        val bytes = buildWasmBytes {
            table("funcs", WasmRefType.FUNCREF, 2)

            function("add", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32)) { func, asm ->
                asm.localGet(func.getParameter(0))
                asm.localGet(func.getParameter(1))
                asm.i32Add()
            }
            function("mul", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32)) { func, asm ->
                asm.localGet(func.getParameter(0))
                asm.localGet(func.getParameter(1))
                asm.i32Mul()
            }

            activeElement(0, 0, listOf(0, 1))

            function("dispatch", listOf(WasmValueType.I32, WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                asm.localGet(func.getParameter(1))
                asm.localGet(func.getParameter(2))
                asm.localGet(func.getParameter(0))
                asm.callIndirect(0, 0)
            }
        }

        val runtime = WarkRuntime.create(WasmTarget.MVP, ExecutionMode.JIT)
        val instance = runtime.load(bytes).instantiate()

        assertEquals(7L, instance.call("dispatch", 0, 3, 4)[0])
        assertEquals(12L, instance.call("dispatch", 1, 3, 4)[0])
    }
}
