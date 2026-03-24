package org.wark

import org.junit.jupiter.api.Test
import org.kgen.target.wasm.WasmValueType
import org.kgen.target.wasm.asm.WasmAssembler
import kotlin.test.assertEquals

class WarkControlFlowExecutionTest {

    private fun buildWasmBytes(block: WasmAssembler.() -> Unit): ByteArray {
        val assembler = WasmAssembler.create()
        assembler.block()
        return assembler.assemble()
    }

    @Test
    fun executeBlockWithBr() {
        val bytes = buildWasmBytes {
            val func = beginFunction("blockTest", emptyList(), listOf(WasmValueType.I32), exported = true)
            i32Const(10)
            beginBlock()
            i32Const(20)
            drop()
            br(0)
            end()
            endFunction()
        }

        val runtime = WarkRuntime.create(WasmTarget.MVP)
        val instance = runtime.load(bytes).instantiate()
        val result = instance.call("blockTest")
        assertEquals(10L, result[0])
    }

    @Test
    fun executeLoopCounter() {
        val bytes = buildWasmBytes {
            val func = beginFunction("countTo5", emptyList(), listOf(WasmValueType.I32), exported = true)
            val counter = declareLocal(WasmValueType.I32)

            i32Const(0)
            localSet(counter)

            beginLoop()
            localGet(counter)
            i32Const(1)
            i32Add()
            localSet(counter)

            localGet(counter)
            i32Const(5)
            i32LtS()
            brIf(0)
            end()

            localGet(counter)
            endFunction()
        }

        val runtime = WarkRuntime.create(WasmTarget.MVP)
        val instance = runtime.load(bytes).instantiate()
        val result = instance.call("countTo5")
        assertEquals(5L, result[0])
    }

    @Test
    fun executeSum1to10() {
        val bytes = buildWasmBytes {
            val func = beginFunction("sum", emptyList(), listOf(WasmValueType.I32), exported = true)
            val sum = declareLocal(WasmValueType.I32)
            val counter = declareLocal(WasmValueType.I32)

            i32Const(0)
            localSet(sum)
            i32Const(1)
            localSet(counter)

            beginLoop()
            localGet(sum)
            localGet(counter)
            i32Add()
            localSet(sum)

            localGet(counter)
            i32Const(1)
            i32Add()
            localSet(counter)

            localGet(counter)
            i32Const(11)
            i32LtS()
            brIf(0)
            end()

            localGet(sum)
            endFunction()
        }

        val runtime = WarkRuntime.create(WasmTarget.MVP)
        val instance = runtime.load(bytes).instantiate()
        assertEquals(55L, instance.call("sum")[0])
    }

    @Test
    fun executeNestedBlocks() {
        val bytes = buildWasmBytes {
            val func = beginFunction("nested", emptyList(), listOf(WasmValueType.I32), exported = true)
            i32Const(1)
            beginBlock()
            i32Const(2)
            i32Add()
            beginBlock()
            i32Const(3)
            i32Add()
            end()
            end()
            endFunction()
        }

        val runtime = WarkRuntime.create(WasmTarget.MVP)
        val instance = runtime.load(bytes).instantiate()
        assertEquals(6L, instance.call("nested")[0])
    }
}
