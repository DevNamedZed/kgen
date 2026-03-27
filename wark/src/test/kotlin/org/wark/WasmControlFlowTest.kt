package org.wark

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.kgen.target.wasm.WasmBlockType
import org.kgen.target.wasm.WasmValueType
import kotlin.test.assertFailsWith

class WasmControlFlowTest : WasmTestBase() {

    companion object {
    }

    @Nested
    inner class BasicBlocks {

        @Test
        fun `block with br early exit`() {
            val bytes = buildWasmBytes {
                val func = beginFunction("test", emptyList(), listOf(WasmValueType.I32), exported = true)
                i32Const(10)
                beginBlock()
                i32Const(20)
                drop()
                br(0)
                end()
                endFunction()
            }
            assertBothEqual(10L, bytes, "test")
        }
    }

    @Nested
    inner class Loops {

        @Test
        fun `loop count to 10`() {
            val bytes = buildWasmBytes {
                val func = beginFunction("countTo10", emptyList(), listOf(WasmValueType.I32), exported = true)
                val counter = declareLocal(WasmValueType.I32)

                i32Const(0)
                localSet(counter)

                beginLoop()
                localGet(counter)
                i32Const(1)
                i32Add()
                localSet(counter)

                localGet(counter)
                i32Const(10)
                i32LtS()
                brIf(0)
                end()

                localGet(counter)
                endFunction()
            }
            assertBothEqual(10L, bytes, "countTo10")
        }
    }

    @Nested
    inner class IfElse {

        @Test
        fun `if then true path`() {
            val bytes = buildWasmBytes {
                val func = beginFunction("test", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true)
                val result = declareLocal(WasmValueType.I32)

                i32Const(0)
                localSet(result)

                localGet(func.getParameter(0))
                beginIf()
                i32Const(42)
                localSet(result)
                endIf()

                localGet(result)
                endFunction()
            }
            assertBothEqual(42L, bytes, "test", 1L)
        }

        @Test
        fun `if then false path`() {
            val bytes = buildWasmBytes {
                val func = beginFunction("test", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true)
                val result = declareLocal(WasmValueType.I32)

                i32Const(0)
                localSet(result)

                localGet(func.getParameter(0))
                beginIf()
                i32Const(42)
                localSet(result)
                endIf()

                localGet(result)
                endFunction()
            }
            assertBothEqual(0L, bytes, "test", 0L)
        }

        @Test
        fun `if else with result`() {
            val bytes = buildWasmBytes {
                val func = beginFunction("test", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true)

                localGet(func.getParameter(0))
                beginIf(WasmBlockType.I32)
                i32Const(100)
                beginElse()
                i32Const(200)
                endIf()

                endFunction()
            }
            assertBothEqual(100L, bytes, "test", 1L)
            assertBothEqual(200L, bytes, "test", 0L)
        }
    }

    @Nested
    inner class BranchTable {

        @Test
        fun `br_if conditional skip`() {
            val bytes = buildWasmBytes {
                val func = beginFunction("test", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true)
                val result = declareLocal(WasmValueType.I32)

                i32Const(10)
                localSet(result)

                beginBlock()
                localGet(func.getParameter(0))
                brIf(0)
                i32Const(20)
                localSet(result)
                end()

                localGet(result)
                endFunction()
            }
            assertBothEqual(10L, bytes, "test", 1L)
            assertBothEqual(20L, bytes, "test", 0L)
        }

        @Test
        fun `br_table switch dispatch`() {
            val bytes = buildWasmBytes {
                val func = beginFunction("classify", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true)

                beginBlock(WasmBlockType.I32) // outermost, carries result
                beginBlock()
                beginBlock()
                beginBlock()
                localGet(func.getParameter(0))
                brTable(intArrayOf(0, 1, 2, 2))
                end()
                i32Const(10)
                br(2) // break to outermost result block
                end()
                i32Const(20)
                br(1) // break to outermost result block
                end()
                i32Const(30)
                end() // end outermost result block

                endFunction()
            }
            assertBothEqual(10L, bytes, "classify", 0L)
            assertBothEqual(20L, bytes, "classify", 1L)
            assertBothEqual(30L, bytes, "classify", 2L)
            assertBothEqual(30L, bytes, "classify", 99L)
        }
    }

    @Nested
    inner class BlockResults {

        @Test
        fun `block result from br`() {
            val bytes = buildWasmBytes {
                val func = beginFunction("test", emptyList(), listOf(WasmValueType.I32), exported = true)

                beginBlock(WasmBlockType.I32)
                i32Const(42)
                br(0)
                end()

                endFunction()
            }
            assertBothEqual(42L, bytes, "test")
        }

        @Test
        fun `br_if targeting block with result`() {
            val bytes = buildWasmBytes {
                val func = beginFunction("test", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true)

                beginBlock(WasmBlockType.I32)
                i32Const(100)
                localGet(func.getParameter(0))
                brIf(0)
                drop()
                i32Const(200)
                end()

                endFunction()
            }
            assertBothEqual(100L, bytes, "test", 1L)
            assertBothEqual(200L, bytes, "test", 0L)
        }
    }

    @Nested
    inner class NestedControlFlow {

        @Test
        fun `nested loop sum 1 to N`() {
            val bytes = buildWasmBytes {
                val func = beginFunction("sumTo", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true)
                val sum = declareLocal(WasmValueType.I32)
                val counter = declareLocal(WasmValueType.I32)

                i32Const(0)
                localSet(sum)
                i32Const(1)
                localSet(counter)

                beginBlock()
                beginLoop()

                localGet(counter)
                localGet(func.getParameter(0))
                i32GtS()
                brIf(1)

                localGet(sum)
                localGet(counter)
                i32Add()
                localSet(sum)

                localGet(counter)
                i32Const(1)
                i32Add()
                localSet(counter)

                br(0)
                end()
                end()

                localGet(sum)
                endFunction()
            }
            assertBothEqual(55L, bytes, "sumTo", 10L)
            assertBothEqual(0L, bytes, "sumTo", 0L)
            assertBothEqual(1L, bytes, "sumTo", 1L)
        }

        @Test
        fun `nested loop multiplication by repeated addition`() {
            val bytes = buildWasmBytes {
                val func = beginFunction("mulByAdd", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true)
                val result = declareLocal(WasmValueType.I32)
                val counter = declareLocal(WasmValueType.I32)

                i32Const(0)
                localSet(result)
                i32Const(0)
                localSet(counter)

                beginBlock()
                beginLoop()

                localGet(counter)
                localGet(func.getParameter(1))
                i32GeS()
                brIf(1)

                localGet(result)
                localGet(func.getParameter(0))
                i32Add()
                localSet(result)

                localGet(counter)
                i32Const(1)
                i32Add()
                localSet(counter)

                br(0)
                end()
                end()

                localGet(result)
                endFunction()
            }
            assertBothEqual(42L, bytes, "mulByAdd", 6L, 7L)
            assertBothEqual(0L, bytes, "mulByAdd", 5L, 0L)
            assertBothEqual(100L, bytes, "mulByAdd", 10L, 10L)
        }

        @Test
        fun `unreachable traps`() {
            val bytes = buildWasmBytes {
                function("trap", emptyList(), emptyList(), exported = true) { func, asm ->
                    asm.unreachable()
                }
            }
            for (mode in listOf(ExecutionMode.INTERPRET, ExecutionMode.JIT)) {
                assertFailsWith<WasmTrap>("$mode: unreachable should trap") {
                    run(bytes, "trap", mode)
                }
            }
        }
    }

    @Nested
    inner class ControlFlowPatterns {

        @Test
        fun `if without else void block skips body`() {
            val bytes = buildWasmBytes {
                val func = beginFunction("test", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true)
                val result = declareLocal(WasmValueType.I32)

                i32Const(99)
                localSet(result)

                localGet(func.getParameter(0))
                beginIf()
                i32Const(42)
                localSet(result)
                endIf()

                localGet(result)
                endFunction()
            }
            assertBothEqual(99L, bytes, "test", 0L)
            assertBothEqual(42L, bytes, "test", 1L)
        }

        @Test
        fun `loop breaks on first iteration`() {
            val bytes = buildWasmBytes {
                val func = beginFunction("test", emptyList(), listOf(WasmValueType.I32), exported = true)
                val counter = declareLocal(WasmValueType.I32)

                i32Const(0)
                localSet(counter)

                beginBlock()
                beginLoop()

                localGet(counter)
                i32Const(1)
                i32Add()
                localSet(counter)

                i32Const(1)
                brIf(1)

                localGet(counter)
                i32Const(1)
                i32Add()
                localSet(counter)

                br(0)
                end()
                end()

                localGet(counter)
                endFunction()
            }
            assertBothEqual(1L, bytes, "test")
        }

        @Test
        fun `block result from multiple br paths`() {
            val bytes = buildWasmBytes {
                val func = beginFunction("test", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true)

                beginBlock(WasmBlockType.I32)
                beginBlock()
                beginBlock()

                localGet(func.getParameter(0))
                brTable(intArrayOf(0, 1, 1))
                end()

                i32Const(10)
                br(1)
                end()

                i32Const(20)
                end()

                endFunction()
            }
            assertBothEqual(10L, bytes, "test", 0L)
            assertBothEqual(20L, bytes, "test", 1L)
            assertBothEqual(20L, bytes, "test", 99L)
        }

        @Test
        fun `deeply nested blocks with br targeting outermost`() {
            val bytes = buildWasmBytes {
                val func = beginFunction("test", emptyList(), listOf(WasmValueType.I32), exported = true)

                beginBlock(WasmBlockType.I32)
                beginBlock()
                beginBlock()
                beginBlock()
                beginBlock()

                i32Const(42)
                br(4)

                end()
                end()
                end()
                end()
                end()

                endFunction()
            }
            assertBothEqual(42L, bytes, "test")
        }
    }

    @Nested
    inner class Variables {

        @Test
        fun `local tee sets and leaves value on stack`() {
            val bytes = buildWasmBytes {
                val func = beginFunction("test", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true)
                val saved = declareLocal(WasmValueType.I32)

                localGet(func.getParameter(0))
                i32Const(10)
                i32Add()
                localTee(saved)
                localGet(saved)
                i32Add()

                endFunction()
            }
            assertBothEqual(30L, bytes, "test", 5L)
        }

        @Test
        fun `multiple locals of different types`() {
            val bytes = buildWasmBytes {
                val func = beginFunction("test", listOf(WasmValueType.I32, WasmValueType.I64), listOf(WasmValueType.I64), exported = true)
                val localI32 = declareLocal(WasmValueType.I32)
                val localI64 = declareLocal(WasmValueType.I64)

                localGet(func.getParameter(0))
                i32Const(1)
                i32Add()
                localSet(localI32)

                localGet(func.getParameter(1))
                i64Const(100)
                i64Add()
                localSet(localI64)

                localGet(localI32)
                i64ExtendI32S()
                localGet(localI64)
                i64Add()

                endFunction()
            }
            assertBothEqualI64(111L, bytes, "test", 10L, 0L)
        }

        @Test
        fun `return instruction exits function early`() {
            val bytes = buildWasmBytes {
                val func = beginFunction("test", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true)

                localGet(func.getParameter(0))
                i32Const(0)
                i32GtS()
                beginIf()
                i32Const(42)
                return_()
                endIf()

                i32Const(-1)
                endFunction()
            }
            assertBothEqual(42L, bytes, "test", 5L)
            assertBothEqual(-1L and 0xFFFFFFFFL, bytes, "test", 0L)
        }
    }
}
