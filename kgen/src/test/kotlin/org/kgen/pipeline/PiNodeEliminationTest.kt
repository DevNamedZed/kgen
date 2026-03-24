package org.kgen.pipeline

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*
import org.kgen.ir.instructions.*

class PiNodeEliminationTest {

    private val pass = PiNodeElimination()

    @Nested
    inner class BasicElimination {

        @Test
        fun removePiNodeAndReplaceUses() {
            val paramX = Parameter("x", Type.I32, 0)
            val piDest = InstructionRef("refined", Type.I32)
            val addDest = InstructionRef("sum", Type.I32)

            val module = Module(
                name = "test",
                functions = listOf(
                    IrFunction(
                        name = "foo",
                        params = listOf(paramX),
                        returnType = Type.I32,
                        blocks = listOf(
                            BasicBlock("entry", listOf(
                                PiNode(piDest, paramX, Type.I32),
                                Add(addDest, piDest, piDest),
                                Ret(addDest),
                            )),
                        ),
                    )
                ),
            )

            val result = pass.run(module)
            val fn = result.functions[0]
            val instructions = fn.blocks[0].instructions

            assertEquals(2, instructions.size)
            assertTrue(instructions.none { it is PiNode })

            val add = instructions[0] as Add
            assertEquals("x", add.lhs.name)
            assertEquals("x", add.rhs.name)
        }

        @Test
        fun chainedPiNodesResolveTransitively() {
            val paramX = Parameter("x", Type.I32, 0)
            val pi1 = InstructionRef("pi1", Type.I32)
            val pi2 = InstructionRef("pi2", Type.I32)
            val addDest = InstructionRef("sum", Type.I32)

            val module = Module(
                name = "test",
                functions = listOf(
                    IrFunction(
                        name = "bar",
                        params = listOf(paramX),
                        returnType = Type.I32,
                        blocks = listOf(
                            BasicBlock("entry", listOf(
                                PiNode(pi1, paramX, Type.I32),
                                PiNode(pi2, pi1, Type.I32),
                                Add(addDest, pi2, paramX),
                                Ret(addDest),
                            )),
                        ),
                    )
                ),
            )

            val result = pass.run(module)
            val fn = result.functions[0]
            val instructions = fn.blocks[0].instructions

            assertEquals(2, instructions.size)
            val add = instructions[0] as Add
            assertEquals("x", add.lhs.name)
            assertEquals("x", add.rhs.name)
        }

        @Test
        fun noPiNodesReturnsFunctionUnchanged() {
            val paramX = Parameter("x", Type.I32, 0)
            val addDest = InstructionRef("sum", Type.I32)

            val module = Module(
                name = "test",
                functions = listOf(
                    IrFunction(
                        name = "noop",
                        params = listOf(paramX),
                        returnType = Type.I32,
                        blocks = listOf(
                            BasicBlock("entry", listOf(
                                Add(addDest, paramX, paramX),
                                Ret(addDest),
                            )),
                        ),
                    )
                ),
            )

            val result = pass.run(module)
            val fn = result.functions[0]
            assertEquals(2, fn.blocks[0].instructions.size)
        }
    }

    @Nested
    inner class InstructionTypeReplacement {

        @Test
        fun replacesInLoadPointer() {
            val paramPtr = Parameter("ptr", Type.OpaquePointer, 0)
            val piDest = InstructionRef("refined_ptr", Type.OpaquePointer)
            val loadDest = InstructionRef("loaded", Type.I32)

            val module = Module(
                name = "test",
                functions = listOf(
                    IrFunction(
                        name = "load_test",
                        params = listOf(paramPtr),
                        returnType = Type.I32,
                        blocks = listOf(
                            BasicBlock("entry", listOf(
                                PiNode(piDest, paramPtr, Type.OpaquePointer),
                                Load(loadDest, piDest, Type.I32),
                                Ret(loadDest),
                            )),
                        ),
                    )
                ),
            )

            val result = pass.run(module)
            val fn = result.functions[0]
            val load = fn.blocks[0].instructions[0] as Load
            assertEquals("ptr", load.ptr.name)
        }

        @Test
        fun replacesInCondBr() {
            val paramCond = Parameter("cond", Type.I1, 0)
            val piDest = InstructionRef("refined_cond", Type.I1)

            val module = Module(
                name = "test",
                functions = listOf(
                    IrFunction(
                        name = "branch_test",
                        params = listOf(paramCond),
                        returnType = Type.Void,
                        blocks = listOf(
                            BasicBlock("entry", listOf(
                                PiNode(piDest, paramCond, Type.I1),
                                CondBr(piDest, BlockRef("then"), BlockRef("else")),
                            )),
                            BasicBlock("then", listOf(Ret(null))),
                            BasicBlock("else", listOf(Ret(null))),
                        ),
                    )
                ),
            )

            val result = pass.run(module)
            val fn = result.functions[0]
            val condBr = fn.blocks[0].instructions[0] as CondBr
            assertEquals("cond", condBr.condition.name)
        }
    }
}
