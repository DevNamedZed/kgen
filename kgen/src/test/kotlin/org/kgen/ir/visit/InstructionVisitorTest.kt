package org.kgen.ir.visit

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*
import org.kgen.ir.instructions.*

class InstructionVisitorTest {

    private val refX = InstructionRef("x", Type.I32)
    private val refY = InstructionRef("y", Type.I32)
    private val paramA = Parameter("a", Type.I32, 0)
    private val paramB = Parameter("b", Type.I32, 1)

    @Nested
    inner class VisitorDispatch {

        @Test
        fun dispatchesToArithmeticVisitor() {
            val visited = mutableListOf<String>()
            val visitor = object : ArithmeticInstructionVisitor {
                override fun visitAdd(instruction: Add) {
                    visited.add("add")
                }
                override fun visitSub(instruction: Sub) {
                    visited.add("sub")
                }
            }

            Instructions.accept(Add(refX, paramA, paramB), visitor)
            Instructions.accept(Sub(refX, paramA, paramB), visitor)

            assertEquals(listOf("add", "sub"), visited)
        }

        @Test
        fun dispatchesToMemoryVisitor() {
            val visited = mutableListOf<String>()
            val visitor = object : MemoryInstructionVisitor {
                override fun visitLoad(instruction: Load) {
                    visited.add("load")
                }
                override fun visitStore(instruction: Store) {
                    visited.add("store")
                }
            }

            val ptrRef = InstructionRef("ptr", Type.OpaquePointer)
            Instructions.accept(Load(refX, ptrRef, Type.I32), visitor)
            Instructions.accept(Store(paramA, ptrRef), visitor)

            assertEquals(listOf("load", "store"), visited)
        }

        @Test
        fun silentlySkipsUnimplementedCategories() {
            val visited = mutableListOf<String>()
            val visitor = object : ArithmeticInstructionVisitor {
                override fun visitAdd(instruction: Add) {
                    visited.add("add")
                }
            }

            Instructions.accept(Add(refX, paramA, paramB), visitor)
            Instructions.accept(Store(paramA, InstructionRef("p", Type.OpaquePointer)), visitor)

            assertEquals(listOf("add"), visited)
        }

        @Test
        fun multiCategoryVisitorReceivesBothCategories() {
            val visited = mutableListOf<String>()
            val visitor = object : ArithmeticInstructionVisitor, ComparisonInstructionVisitor {
                override fun visitAdd(instruction: Add) {
                    visited.add("add")
                }
                override fun visitICmp(instruction: ICmp) {
                    visited.add("icmp")
                }
            }

            Instructions.accept(Add(refX, paramA, paramB), visitor)
            Instructions.accept(ICmp(InstructionRef("c", Type.I1), ICmpPredicate.EQ, paramA, paramB), visitor)

            assertEquals(listOf("add", "icmp"), visited)
        }

        @Test
        fun defaultNoOpMethodsDoNothing() {
            val visitor = object : ArithmeticInstructionVisitor {}

            Instructions.accept(Add(refX, paramA, paramB), visitor)
            Instructions.accept(Sub(refX, paramA, paramB), visitor)
            Instructions.accept(Mul(refX, paramA, paramB), visitor)
        }

        @Test
        fun dispatchesTerminatorInstructions() {
            val visited = mutableListOf<String>()
            val visitor = object : TerminatorInstructionVisitor {
                override fun visitRet(instruction: Ret) {
                    visited.add("ret")
                }
                override fun visitBr(instruction: Br) {
                    visited.add("br")
                }
                override fun visitCondBr(instruction: CondBr) {
                    visited.add("condbr")
                }
            }

            Instructions.accept(Ret(paramA), visitor)
            Instructions.accept(Br(BlockRef("target")), visitor)
            Instructions.accept(CondBr(paramA, BlockRef("then"), BlockRef("else")), visitor)

            assertEquals(listOf("ret", "br", "condbr"), visited)
        }

        @Test
        fun dispatchesObjectInstructions() {
            val visited = mutableListOf<String>()
            val visitor = object : ObjectInstructionVisitor {
                override fun visitNewObject(instruction: NewObject) {
                    visited.add("newobj")
                }
                override fun visitGetField(instruction: GetField) {
                    visited.add("getfield")
                }
            }

            Instructions.accept(NewObject(refX, "MyClass", emptyList()), visitor)
            Instructions.accept(
                GetField(refX, InstructionRef("obj", Type.ClassRef("MyClass")), "MyClass", "field", Type.I32),
                visitor
            )

            assertEquals(listOf("newobj", "getfield"), visited)
        }

        @Test
        fun walkFunctionWithVisitor() {
            val fn = IrFunction(
                name = "test",
                params = listOf(paramA, paramB),
                returnType = Type.I32,
                blocks = listOf(
                    BasicBlock("entry", listOf(
                        Add(refX, paramA, paramB),
                        Ret(refX)
                    ))
                )
            )

            val addCount = mutableListOf<Add>()
            val retCount = mutableListOf<Ret>()
            val visitor = object : ArithmeticInstructionVisitor, TerminatorInstructionVisitor {
                override fun visitAdd(instruction: Add) {
                    addCount.add(instruction)
                }
                override fun visitRet(instruction: Ret) {
                    retCount.add(instruction)
                }
            }

            for (block in fn.blocks) {
                for (inst in block.instructions) {
                    Instructions.accept(inst, visitor)
                }
            }

            assertEquals(1, addCount.size)
            assertEquals(1, retCount.size)
        }
    }
}
