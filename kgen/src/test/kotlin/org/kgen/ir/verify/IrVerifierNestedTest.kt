package org.kgen.ir.verify

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*
import org.kgen.ir.instructions.*
import org.kgen.ir.types.StructDefinition

class IrVerifierNestedTest {

    private val verifier = IrVerifier()

    private fun verifyModule(vararg functions: IrFunction, globals: List<Global> = emptyList(),
                             structs: List<StructDefinition> = emptyList()): VerificationResult {
        return verifier.verify(Module("test", functions = functions.toList(), globals = globals, structs = structs))
    }

    @Nested
    inner class DuplicateDetection {

        @Test
        fun duplicateFunctionNamesFail() {
            val fn1 = IrFunction("foo", emptyList(), Type.Void, listOf(BasicBlock("entry", listOf(Ret(null)))))
            val fn2 = IrFunction("foo", emptyList(), Type.Void, listOf(BasicBlock("entry", listOf(Ret(null)))))
            val result = verifyModule(fn1, fn2)
            assertFalse(result.isValid)
            assertTrue(result.errors.any { it.message.contains("Duplicate function name") })
        }

        @Test
        fun duplicateGlobalNamesFail() {
            val globals = listOf(
                Global("x", Type.I32, Constant.I32(1)),
                Global("x", Type.I32, Constant.I32(2)),
            )
            val result = verifyModule(globals = globals)
            assertFalse(result.isValid)
            assertTrue(result.errors.any { it.message.contains("Duplicate global name") })
        }

        @Test
        fun duplicateBlockLabelsFail() {
            val fn = IrFunction("foo", emptyList(), Type.Void, listOf(
                BasicBlock("entry", listOf(Br(BlockRef("entry2")))),
                BasicBlock("entry2", listOf(Ret(null))),
                BasicBlock("entry2", listOf(Ret(null))),
            ))
            val result = verifyModule(fn)
            assertFalse(result.isValid)
            assertTrue(result.errors.any { it.message.contains("Duplicate block label") })
        }

        @Test
        fun duplicateValueDefinitionsFail() {
            val fn = IrFunction("foo", emptyList(), Type.I32, listOf(
                BasicBlock("entry", listOf(
                    Add(InstructionRef("r", Type.I32), Constant.I32(1), Constant.I32(2)),
                    Add(InstructionRef("r", Type.I32), Constant.I32(3), Constant.I32(4)),
                    Ret(InstructionRef("r", Type.I32)),
                )),
            ))
            val result = verifyModule(fn)
            assertFalse(result.isValid)
            assertTrue(result.errors.any { it.message.contains("Duplicate value definition") })
        }
    }

    @Nested
    inner class TerminatorValidation {

        @Test
        fun blockWithoutTerminatorFails() {
            val fn = IrFunction("foo", emptyList(), Type.Void, listOf(
                BasicBlock("entry", listOf(
                    Add(InstructionRef("r", Type.I32), Constant.I32(1), Constant.I32(2)),
                )),
            ))
            val result = verifyModule(fn)
            assertFalse(result.isValid)
            assertTrue(result.errors.any { it.message.contains("does not end with a terminator") })
        }

        @Test
        fun terminatorInMiddleOfBlockFails() {
            val fn = IrFunction("foo", emptyList(), Type.Void, listOf(
                BasicBlock("entry", listOf(
                    Ret(null),
                    Add(InstructionRef("r", Type.I32), Constant.I32(1), Constant.I32(2)),
                    Ret(null),
                )),
            ))
            val result = verifyModule(fn)
            assertFalse(result.isValid)
            assertTrue(result.errors.any { it.message.contains("Terminator in middle") })
        }

        @Test
        fun emptyBlockFails() {
            val fn = IrFunction("foo", emptyList(), Type.Void, listOf(
                BasicBlock("entry", emptyList()),
            ))
            val result = verifyModule(fn)
            assertFalse(result.isValid)
            assertTrue(result.errors.any { it.message.contains("empty") })
        }
    }

    @Nested
    inner class ExternalFunctions {

        @Test
        fun externalFunctionWithBodyFails() {
            val fn = IrFunction("ext", emptyList(), Type.Void,
                listOf(BasicBlock("entry", listOf(Ret(null)))), isExternal = true)
            val result = verifyModule(fn)
            assertFalse(result.isValid)
            assertTrue(result.errors.any { it.message.contains("External function") && it.message.contains("must not have a body") })
        }

        @Test
        fun nonExternalFunctionWithoutBodyFails() {
            val fn = IrFunction("foo", emptyList(), Type.Void, emptyList(), isExternal = false)
            val result = verifyModule(fn)
            assertFalse(result.isValid)
            assertTrue(result.errors.any { it.message.contains("must have at least one basic block") })
        }

        @Test
        fun externalFunctionWithoutBodyPasses() {
            val fn = IrFunction("ext", emptyList(), Type.Void, emptyList(), isExternal = true)
            val result = verifyModule(fn)
            assertTrue(result.isValid)
        }
    }

    @Nested
    inner class PhiValidation {

        @Test
        fun phiAfterNonPhiFails() {
            val fn = IrFunction("foo", emptyList(), Type.I32, listOf(
                BasicBlock("entry", listOf(
                    Br(BlockRef("loop")),
                )),
                BasicBlock("loop", listOf(
                    Add(InstructionRef("x", Type.I32), Constant.I32(1), Constant.I32(2)),
                    Phi(InstructionRef("p", Type.I32), listOf(Constant.I32(0) to BlockRef("entry"))),
                    Ret(InstructionRef("p", Type.I32)),
                )),
            ))
            val result = verifyModule(fn)
            assertFalse(result.isValid)
            assertTrue(result.errors.any { it.message.contains("Phi node after non-phi") })
        }

        @Test
        fun phiAtStartOfBlockPasses() {
            val fn = IrFunction("foo", emptyList(), Type.I32, listOf(
                BasicBlock("entry", listOf(
                    Br(BlockRef("loop")),
                )),
                BasicBlock("loop", listOf(
                    Phi(InstructionRef("p", Type.I32), listOf(Constant.I32(0) to BlockRef("entry"))),
                    Ret(InstructionRef("p", Type.I32)),
                )),
            ))
            val result = verifyModule(fn)
            assertTrue(result.isValid, "Expected valid: $result")
        }
    }

    @Nested
    inner class BranchTargetValidation {

        @Test
        fun brToNonexistentBlockFails() {
            val fn = IrFunction("foo", emptyList(), Type.Void, listOf(
                BasicBlock("entry", listOf(
                    Br(BlockRef("nonexistent")),
                )),
            ))
            val result = verifyModule(fn)
            assertFalse(result.isValid)
        }

        @Test
        fun condBrToExistingBlocksPasses() {
            val fn = IrFunction("foo", listOf(Parameter("c", Type.I1, 0)), Type.Void, listOf(
                BasicBlock("entry", listOf(
                    CondBr(Parameter("c", Type.I1, 0), BlockRef("then"), BlockRef("else")),
                )),
                BasicBlock("then", listOf(Ret(null))),
                BasicBlock("else", listOf(Ret(null))),
            ))
            val result = verifyModule(fn)
            assertTrue(result.isValid, "Expected valid: $result")
        }
    }

    @Nested
    inner class ExceptionHandling {

        @Test
        fun invokeWithoutPersonalityFails() {
            val func = FunctionRef("may_throw", Type.Function(emptyList(), Type.Void))
            val landingPadType = Type.Struct(null, listOf(Type.OpaquePointer, Type.I32))
            val fn = IrFunction("caller", emptyList(), Type.Void, listOf(
                BasicBlock("entry", listOf(
                    Invoke(null, func, emptyList(), Type.Void, BlockRef("cont"), BlockRef("lpad")),
                )),
                BasicBlock("cont", listOf(Ret(null))),
                BasicBlock("lpad", listOf(
                    LandingPad(InstructionRef("lp", landingPadType), landingPadType, emptyList(), cleanup = true),
                    Resume(InstructionRef("lp", landingPadType)),
                )),
            ))
            val result = verifyModule(fn)
            assertFalse(result.isValid)
            assertTrue(result.errors.any { it.message.contains("personality") })
        }

        @Test
        fun invokeWithPersonalityPasses() {
            val func = FunctionRef("may_throw", Type.Function(emptyList(), Type.Void))
            val personality = FunctionRef("__gxx_personality_v0", Type.Function(emptyList(), Type.I32))
            val landingPadType = Type.Struct(null, listOf(Type.OpaquePointer, Type.I32))
            val fn = IrFunction("caller", emptyList(), Type.Void, listOf(
                BasicBlock("entry", listOf(
                    Invoke(null, func, emptyList(), Type.Void, BlockRef("cont"), BlockRef("lpad")),
                )),
                BasicBlock("cont", listOf(Ret(null))),
                BasicBlock("lpad", listOf(
                    LandingPad(InstructionRef("lp", landingPadType), landingPadType, emptyList(), cleanup = true),
                    Resume(InstructionRef("lp", landingPadType)),
                )),
            ), personality = personality)
            val result = verifyModule(fn)
            assertTrue(result.isValid, "Expected valid: $result")
        }

        @Test
        fun invokeUnwindDestMustStartWithLandingPad() {
            val func = FunctionRef("may_throw", Type.Function(emptyList(), Type.Void))
            val personality = FunctionRef("__gxx_personality_v0", Type.Function(emptyList(), Type.I32))
            val fn = IrFunction("caller", emptyList(), Type.Void, listOf(
                BasicBlock("entry", listOf(
                    Invoke(null, func, emptyList(), Type.Void, BlockRef("cont"), BlockRef("bad_lpad")),
                )),
                BasicBlock("cont", listOf(Ret(null))),
                BasicBlock("bad_lpad", listOf(
                    Add(InstructionRef("x", Type.I32), Constant.I32(1), Constant.I32(2)),
                    Ret(null),
                )),
            ), personality = personality)
            val result = verifyModule(fn)
            assertFalse(result.isValid)
            assertTrue(result.errors.any { it.message.contains("must begin with LandingPad") })
        }
    }

    @Nested
    inner class GcValidation {

        @Test
        fun gcRootWithoutGcStrategyFails() {
            val rootRef = InstructionRef("root", Type.Pointer(Type.I8))
            val fn = IrFunction("foo", emptyList(), Type.Void, listOf(
                BasicBlock("entry", listOf(
                    Alloca(rootRef, Type.I8),
                    GCRoot(rootRef, null),
                    Ret(null),
                )),
            ))
            val result = verifyModule(fn)
            assertFalse(result.isValid)
            assertTrue(result.errors.any { it.message.contains("gc strategy") })
        }

        @Test
        fun gcRootWithGcStrategyPasses() {
            val rootRef = InstructionRef("root", Type.Pointer(Type.I8))
            val fn = IrFunction("foo", emptyList(), Type.Void, listOf(
                BasicBlock("entry", listOf(
                    Alloca(rootRef, Type.I8),
                    GCRoot(rootRef, null),
                    Ret(null),
                )),
            ), gc = "shadow-stack")
            val result = verifyModule(fn)
            assertTrue(result.isValid, "Expected valid: $result")
        }
    }

    @Nested
    inner class ValidModules {

        @Test
        fun emptyPipelineStagees() {
            val result = verifier.verify(Module("empty"))
            assertTrue(result.isValid)
        }

        @Test
        fun moduleWithOnlyExternalFunctionsPasses() {
            val fn1 = IrFunction("malloc", listOf(Parameter("size", Type.I64, 0)),
                Type.OpaquePointer, emptyList(), isExternal = true)
            val fn2 = IrFunction("free", listOf(Parameter("ptr", Type.OpaquePointer, 0)),
                Type.Void, emptyList(), isExternal = true)
            val result = verifyModule(fn1, fn2)
            assertTrue(result.isValid)
        }

        @Test
        fun simpleFunctionPasses() {
            val fn = IrFunction("add",
                listOf(Parameter("a", Type.I32, 0), Parameter("b", Type.I32, 1)),
                Type.I32,
                listOf(BasicBlock("entry", listOf(
                    Add(InstructionRef("sum", Type.I32), Parameter("a", Type.I32, 0), Parameter("b", Type.I32, 1)),
                    Ret(InstructionRef("sum", Type.I32)),
                ))),
            )
            val result = verifyModule(fn)
            assertTrue(result.isValid, "Expected valid: $result")
        }

        @Test
        fun branchingFunctionPasses() {
            val fn = IrFunction("abs",
                listOf(Parameter("x", Type.I32, 0)),
                Type.I32,
                listOf(
                    BasicBlock("entry", listOf(
                        ICmp(InstructionRef("cmp", Type.I1), ICmpPredicate.SGT, Parameter("x", Type.I32, 0), Constant.I32(0)),
                        CondBr(InstructionRef("cmp", Type.I1), BlockRef("positive"), BlockRef("negative")),
                    )),
                    BasicBlock("positive", listOf(
                        Ret(Parameter("x", Type.I32, 0)),
                    )),
                    BasicBlock("negative", listOf(
                        Neg(InstructionRef("neg", Type.I32), Parameter("x", Type.I32, 0)),
                        Ret(InstructionRef("neg", Type.I32)),
                    )),
                ),
            )
            val result = verifyModule(fn)
            assertTrue(result.isValid, "Expected valid: $result")
        }
    }

    @Nested
    inner class VerificationResultApi {

        @Test
        fun validResultHasNoErrors() {
            val result = VerificationResult(emptyList())
            assertTrue(result.isValid)
            assertTrue(result.errors.isEmpty())
        }

        @Test
        fun invalidResultHasErrors() {
            val result = VerificationResult(listOf(VerificationError("test error")))
            assertFalse(result.isValid)
            assertEquals(1, result.errors.size)
        }

        @Test
        fun verificationErrorToString() {
            val error = VerificationError("Something went wrong")
            assertEquals("Something went wrong", error.toString())
        }

        @Test
        fun validResultToStringIndicatesSuccess() {
            val result = VerificationResult(emptyList())
            assertTrue(result.toString().contains("passed"))
        }

        @Test
        fun invalidResultToStringIndicatesFailure() {
            val result = VerificationResult(listOf(VerificationError("bad")))
            assertTrue(result.toString().contains("failed"))
        }
    }
}
