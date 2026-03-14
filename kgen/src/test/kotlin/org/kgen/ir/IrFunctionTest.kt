package org.kgen.ir

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.instructions.*

class IrFunctionTest {

    @Nested
    inner class Construction {

        @Test
        fun minimalFunction() {
            val fn = IrFunction("main", emptyList(), Type.Void, emptyList())
            assertEquals("main", fn.name)
            assertTrue(fn.params.isEmpty())
            assertEquals(Type.Void, fn.returnType)
            assertTrue(fn.blocks.isEmpty())
        }

        @Test
        fun functionWithParams() {
            val params = listOf(
                Parameter("x", Type.I32, 0),
                Parameter("y", Type.I64, 1),
            )
            val fn = IrFunction("add", params, Type.I64, emptyList())
            assertEquals(2, fn.params.size)
            assertEquals("x", fn.params[0].name)
            assertEquals(Type.I32, fn.params[0].type)
            assertEquals("y", fn.params[1].name)
            assertEquals(Type.I64, fn.params[1].type)
        }

        @Test
        fun functionWithBlocks() {
            val entry = BasicBlock("entry", listOf(Ret(Constant.I32(0))))
            val fn = IrFunction("foo", emptyList(), Type.I32, listOf(entry))
            assertEquals(1, fn.blocks.size)
            assertEquals("entry", fn.blocks[0].label)
        }

        @Test
        fun functionWithMultipleBlocks() {
            val entry = BasicBlock("entry", listOf(
                CondBr(Constant.I1(true), BlockRef("then"), BlockRef("else"))
            ))
            val thenBlock = BasicBlock("then", listOf(Ret(Constant.I32(1))))
            val elseBlock = BasicBlock("else", listOf(Ret(Constant.I32(0))))
            val fn = IrFunction("branch", listOf(Parameter("c", Type.I1, 0)), Type.I32,
                listOf(entry, thenBlock, elseBlock))
            assertEquals(3, fn.blocks.size)
        }
    }

    @Nested
    inner class DefaultValues {

        @Test
        fun defaultIsNotExternal() {
            val fn = IrFunction("foo", emptyList(), Type.Void, emptyList())
            assertFalse(fn.isExternal)
        }

        @Test
        fun defaultLinkageIsExternal() {
            val fn = IrFunction("foo", emptyList(), Type.Void, emptyList())
            assertEquals(Linkage.EXTERNAL, fn.linkage)
        }

        @Test
        fun defaultVisibilityIsDefault() {
            val fn = IrFunction("foo", emptyList(), Type.Void, emptyList())
            assertEquals(Visibility.DEFAULT, fn.visibility)
        }

        @Test
        fun defaultCallingConventionIsC() {
            val fn = IrFunction("foo", emptyList(), Type.Void, emptyList())
            assertEquals(CallingConvention.C, fn.callingConv)
        }

        @Test
        fun defaultAttributesAreEmpty() {
            val fn = IrFunction("foo", emptyList(), Type.Void, emptyList())
            assertTrue(fn.attributes.isEmpty())
        }

        @Test
        fun defaultSectionIsNull() {
            val fn = IrFunction("foo", emptyList(), Type.Void, emptyList())
            assertNull(fn.section)
        }

        @Test
        fun defaultAlignIsNull() {
            val fn = IrFunction("foo", emptyList(), Type.Void, emptyList())
            assertNull(fn.align)
        }

        @Test
        fun defaultGcIsNull() {
            val fn = IrFunction("foo", emptyList(), Type.Void, emptyList())
            assertNull(fn.gc)
        }

        @Test
        fun defaultIsNotVarArg() {
            val fn = IrFunction("foo", emptyList(), Type.Void, emptyList())
            assertFalse(fn.isVarArg)
        }

        @Test
        fun defaultTypeParamsAreEmpty() {
            val fn = IrFunction("foo", emptyList(), Type.Void, emptyList())
            assertTrue(fn.typeParams.isEmpty())
        }

        @Test
        fun defaultPersonalityIsNull() {
            val fn = IrFunction("foo", emptyList(), Type.Void, emptyList())
            assertNull(fn.personality)
        }

        @Test
        fun defaultComdatIsNull() {
            val fn = IrFunction("foo", emptyList(), Type.Void, emptyList())
            assertNull(fn.comdat)
        }

        @Test
        fun defaultUnnamedAddrIsNone() {
            val fn = IrFunction("foo", emptyList(), Type.Void, emptyList())
            assertEquals(UnnamedAddr.NONE, fn.unnamedAddr)
        }

        @Test
        fun defaultDllStorageClassIsNone() {
            val fn = IrFunction("foo", emptyList(), Type.Void, emptyList())
            assertEquals(DLLStorageClass.NONE, fn.dllStorageClass)
        }
    }

    @Nested
    inner class ExternalFunctions {

        @Test
        fun externalFunctionHasNoBlocks() {
            val fn = IrFunction("printf", listOf(Parameter("fmt", Type.OpaquePointer, 0)), Type.I32, emptyList(), isExternal = true)
            assertTrue(fn.isExternal)
            assertTrue(fn.blocks.isEmpty())
        }

        @Test
        fun externalFunctionWithVarArgs() {
            val fn = IrFunction("printf", listOf(Parameter("fmt", Type.OpaquePointer, 0)), Type.I32, emptyList(),
                isExternal = true, isVarArg = true)
            assertTrue(fn.isVarArg)
        }
    }

    @Nested
    inner class OptionalMetadata {

        @Test
        fun functionWithSection() {
            val fn = IrFunction("init", emptyList(), Type.Void, emptyList(), section = ".init")
            assertEquals(".init", fn.section)
        }

        @Test
        fun functionWithAlignment() {
            val fn = IrFunction("aligned", emptyList(), Type.Void, emptyList(), align = 16)
            assertEquals(16, fn.align)
        }

        @Test
        fun functionWithGcStrategy() {
            val fn = IrFunction("gc_func", emptyList(), Type.Void, emptyList(), gc = "shadow-stack")
            assertEquals("shadow-stack", fn.gc)
        }

        @Test
        fun functionWithPersonality() {
            val personality = FunctionRef("__gxx_personality_v0", Type.Function(emptyList(), Type.I32))
            val fn = IrFunction("throws", emptyList(), Type.Void, emptyList(), personality = personality)
            assertNotNull(fn.personality)
            assertEquals("__gxx_personality_v0", fn.personality!!.name)
        }

        @Test
        fun functionWithComdat() {
            val fn = IrFunction("inline_func", emptyList(), Type.Void, emptyList(), comdat = "grp1")
            assertEquals("grp1", fn.comdat)
        }

        @Test
        fun functionWithAttributes() {
            val fn = IrFunction("pure", emptyList(), Type.I32, emptyList(),
                attributes = setOf(FnAttribute.NOUNWIND, FnAttribute.READNONE))
            assertEquals(2, fn.attributes.size)
            assertTrue(fn.attributes.contains(FnAttribute.NOUNWIND))
            assertTrue(fn.attributes.contains(FnAttribute.READNONE))
        }

        @Test
        fun functionWithDllStorageClass() {
            val fn = IrFunction("exported", emptyList(), Type.Void, emptyList(),
                dllStorageClass = DLLStorageClass.DLLEXPORT)
            assertEquals(DLLStorageClass.DLLEXPORT, fn.dllStorageClass)
        }
    }

    @Nested
    inner class DataClassSemantics {

        @Test
        fun equalFunctionsAreEqual() {
            val fn1 = IrFunction("foo", emptyList(), Type.Void, emptyList())
            val fn2 = IrFunction("foo", emptyList(), Type.Void, emptyList())
            assertEquals(fn1, fn2)
            assertEquals(fn1.hashCode(), fn2.hashCode())
        }

        @Test
        fun differentNameNotEqual() {
            val fn1 = IrFunction("foo", emptyList(), Type.Void, emptyList())
            val fn2 = IrFunction("bar", emptyList(), Type.Void, emptyList())
            assertNotEquals(fn1, fn2)
        }

        @Test
        fun differentReturnTypeNotEqual() {
            val fn1 = IrFunction("foo", emptyList(), Type.Void, emptyList())
            val fn2 = IrFunction("foo", emptyList(), Type.I32, emptyList())
            assertNotEquals(fn1, fn2)
        }

        @Test
        fun copyWithDifferentLinkage() {
            val original = IrFunction("foo", emptyList(), Type.Void, emptyList())
            val copied = original.copy(linkage = Linkage.INTERNAL)
            assertEquals(Linkage.EXTERNAL, original.linkage)
            assertEquals(Linkage.INTERNAL, copied.linkage)
        }
    }

    @Nested
    inner class BasicBlockConstruction {

        @Test
        fun emptyBlockHasLabel() {
            val block = BasicBlock("entry", emptyList())
            assertEquals("entry", block.label)
            assertTrue(block.instructions.isEmpty())
        }

        @Test
        fun blockWithInstructions() {
            val instructions = listOf<Instruction>(
                Add(InstructionRef("sum", Type.I32), Parameter("a", Type.I32, 0), Parameter("b", Type.I32, 1)),
                Ret(InstructionRef("sum", Type.I32)),
            )
            val block = BasicBlock("entry", instructions)
            assertEquals(2, block.instructions.size)
        }

        @Test
        fun equalBlocksAreEqual() {
            val block1 = BasicBlock("test", listOf(Ret(null)))
            val block2 = BasicBlock("test", listOf(Ret(null)))
            assertEquals(block1, block2)
        }

        @Test
        fun differentLabelNotEqual() {
            val block1 = BasicBlock("a", listOf(Ret(null)))
            val block2 = BasicBlock("b", listOf(Ret(null)))
            assertNotEquals(block1, block2)
        }
    }
}
