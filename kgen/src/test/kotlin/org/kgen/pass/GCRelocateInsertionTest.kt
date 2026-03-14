package org.kgen.pass

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*
import org.kgen.ir.instructions.*

class GCRelocateInsertionTest {

    private val pass = GCRelocateInsertion()

    @Nested
    inner class BasicInsertion {

        @Test
        fun insertsRelocateForReferenceAfterSafepoint() {
            val objRef = Parameter("obj", Type.Reference(Type.ClassRef("Foo")), 0)
            val callResult = InstructionRef("callres", Type.I32)
            val funcRef = FunctionRef("bar", Type.Function(emptyList(), Type.I32))
            val fieldDest = InstructionRef("field", Type.I32)

            val module = Module(
                name = "test",
                functions = listOf(
                    IrFunction(
                        name = "test_gc",
                        params = listOf(objRef),
                        returnType = Type.I32,
                        blocks = listOf(
                            BasicBlock("entry", listOf(
                                Call(callResult, funcRef, emptyList(), Type.I32),
                                GetField(fieldDest, objRef, "Foo", "x", Type.I32),
                                Ret(fieldDest),
                            )),
                        ),
                    )
                ),
            )

            val result = pass.run(module)
            val fn = result.functions[0]
            val instructions = fn.blocks[0].instructions

            val gcRelocates = instructions.filterIsInstance<GCRelocate>()
            assertTrue(gcRelocates.isNotEmpty(), "Should have inserted GCRelocate")

            val relocate = gcRelocates.first()
            assertEquals("obj", relocate.base.name)

            val getField = instructions.filterIsInstance<GetField>().first()
            assertEquals(relocate.dest.name, getField.obj.name)
        }

        @Test
        fun noRelocateForPrimitiveValues() {
            val paramX = Parameter("x", Type.I32, 0)
            val callResult = InstructionRef("callres", Type.I32)
            val funcRef = FunctionRef("bar", Type.Function(emptyList(), Type.I32))
            val addDest = InstructionRef("sum", Type.I32)

            val module = Module(
                name = "test",
                functions = listOf(
                    IrFunction(
                        name = "test_no_gc",
                        params = listOf(paramX),
                        returnType = Type.I32,
                        blocks = listOf(
                            BasicBlock("entry", listOf(
                                Call(callResult, funcRef, emptyList(), Type.I32),
                                Add(addDest, paramX, callResult),
                                Ret(addDest),
                            )),
                        ),
                    )
                ),
            )

            val result = pass.run(module)
            val fn = result.functions[0]
            val gcRelocates = fn.blocks[0].instructions.filterIsInstance<GCRelocate>()
            assertTrue(gcRelocates.isEmpty(), "Should not insert GCRelocate for primitives")
        }

        @Test
        fun noRelocateWhenReferenceNotUsedAfterSafepoint() {
            val objRef = Parameter("obj", Type.Reference(Type.ClassRef("Foo")), 0)
            val fieldDest = InstructionRef("field", Type.I32)
            val callResult = InstructionRef("callres", Type.I32)
            val funcRef = FunctionRef("bar", Type.Function(emptyList(), Type.I32))

            val module = Module(
                name = "test",
                functions = listOf(
                    IrFunction(
                        name = "test_dead_ref",
                        params = listOf(objRef),
                        returnType = Type.I32,
                        blocks = listOf(
                            BasicBlock("entry", listOf(
                                GetField(fieldDest, objRef, "Foo", "x", Type.I32),
                                Call(callResult, funcRef, emptyList(), Type.I32),
                                Ret(fieldDest),
                            )),
                        ),
                    )
                ),
            )

            val result = pass.run(module)
            val fn = result.functions[0]
            val gcRelocates = fn.blocks[0].instructions.filterIsInstance<GCRelocate>()
            assertTrue(gcRelocates.isEmpty(), "Should not insert GCRelocate when ref is not used after safepoint")
        }
    }

    @Nested
    inner class MultipleReferences {

        @Test
        fun relocatesMultipleReferencesAfterSafepoint() {
            val obj1 = Parameter("obj1", Type.Reference(Type.ClassRef("A")), 0)
            val obj2 = Parameter("obj2", Type.Reference(Type.ClassRef("B")), 1)
            val callResult = InstructionRef("callres", Type.I32)
            val funcRef = FunctionRef("bar", Type.Function(emptyList(), Type.I32))
            val field1 = InstructionRef("f1", Type.I32)
            val field2 = InstructionRef("f2", Type.I32)
            val sumDest = InstructionRef("sum", Type.I32)

            val module = Module(
                name = "test",
                functions = listOf(
                    IrFunction(
                        name = "test_multi",
                        params = listOf(obj1, obj2),
                        returnType = Type.I32,
                        blocks = listOf(
                            BasicBlock("entry", listOf(
                                Call(callResult, funcRef, emptyList(), Type.I32),
                                GetField(field1, obj1, "A", "x", Type.I32),
                                GetField(field2, obj2, "B", "y", Type.I32),
                                Add(sumDest, field1, field2),
                                Ret(sumDest),
                            )),
                        ),
                    )
                ),
            )

            val result = pass.run(module)
            val fn = result.functions[0]
            val gcRelocates = fn.blocks[0].instructions.filterIsInstance<GCRelocate>()
            assertEquals(2, gcRelocates.size, "Should relocate both references")
        }
    }
}
