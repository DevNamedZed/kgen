package org.kgen.ir

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ValueTest {

    @Nested
    inner class ParameterTests {

        @Test
        fun hasCorrectName() {
            val p = Parameter("x", Type.I32, 0)
            assertEquals("x", p.name)
        }

        @Test
        fun hasCorrectType() {
            val p = Parameter("x", Type.I64, 0)
            assertEquals(Type.I64, p.type)
        }

        @Test
        fun hasCorrectIndex() {
            val p = Parameter("y", Type.F32, 2)
            assertEquals(2, p.index)
        }

        @Test
        fun defaultAttributesAreEmpty() {
            val p = Parameter("x", Type.I32, 0)
            assertTrue(p.attributes.isEmpty())
        }

        @Test
        fun canHaveSingleAttribute() {
            val p = Parameter("x", Type.I32, 0, setOf(ParamAttribute.ZEROEXT))
            assertEquals(setOf(ParamAttribute.ZEROEXT), p.attributes)
        }

        @Test
        fun canHaveMultipleAttributes() {
            val attrs = setOf(ParamAttribute.NOALIAS, ParamAttribute.NONNULL, ParamAttribute.READONLY)
            val p = Parameter("ptr", Type.Pointer(Type.I8), 0, attrs)
            assertEquals(attrs, p.attributes)
        }

        @Test
        fun implementsValue() {
            val p: Value = Parameter("x", Type.I32, 0)
            assertEquals("x", p.name)
            assertEquals(Type.I32, p.type)
        }

        @Test
        fun equalityForSameFields() {
            val a = Parameter("x", Type.I32, 0, setOf(ParamAttribute.SIGNEXT))
            val b = Parameter("x", Type.I32, 0, setOf(ParamAttribute.SIGNEXT))
            assertEquals(a, b)
            assertEquals(a.hashCode(), b.hashCode())
        }

        @Test
        fun inequalityForDifferentName() {
            val a = Parameter("x", Type.I32, 0)
            val b = Parameter("y", Type.I32, 0)
            assertNotEquals(a, b)
        }

        @Test
        fun inequalityForDifferentIndex() {
            val a = Parameter("x", Type.I32, 0)
            val b = Parameter("x", Type.I32, 1)
            assertNotEquals(a, b)
        }

        @Test
        fun inequalityForDifferentType() {
            val a = Parameter("x", Type.I32, 0)
            val b = Parameter("x", Type.I64, 0)
            assertNotEquals(a, b)
        }

        @Test
        fun inequalityForDifferentAttributes() {
            val a = Parameter("x", Type.I32, 0, setOf(ParamAttribute.ZEROEXT))
            val b = Parameter("x", Type.I32, 0, setOf(ParamAttribute.SIGNEXT))
            assertNotEquals(a, b)
        }
    }

    @Nested
    inner class GlobalRefTests {

        @Test
        fun hasCorrectName() {
            val g = GlobalRef("counter", Type.I32)
            assertEquals("counter", g.name)
        }

        @Test
        fun hasCorrectType() {
            val g = GlobalRef("counter", Type.I32)
            assertEquals(Type.I32, g.type)
        }

        @Test
        fun worksWithPointerType() {
            val g = GlobalRef("buffer", Type.Pointer(Type.I8))
            assertEquals(Type.Pointer(Type.I8), g.type)
        }

        @Test
        fun implementsValue() {
            val v: Value = GlobalRef("g", Type.I64)
            assertEquals("g", v.name)
            assertEquals(Type.I64, v.type)
        }

        @Test
        fun equalityForSameFields() {
            val a = GlobalRef("g", Type.I32)
            val b = GlobalRef("g", Type.I32)
            assertEquals(a, b)
            assertEquals(a.hashCode(), b.hashCode())
        }

        @Test
        fun inequalityForDifferentName() {
            assertNotEquals(GlobalRef("a", Type.I32), GlobalRef("b", Type.I32))
        }

        @Test
        fun inequalityForDifferentType() {
            assertNotEquals(GlobalRef("a", Type.I32), GlobalRef("a", Type.I64))
        }
    }

    @Nested
    inner class FunctionRefTests {

        private val simpleFuncType = Type.Function(listOf(Type.I32), Type.I32)

        @Test
        fun hasCorrectName() {
            val f = FunctionRef("add", simpleFuncType)
            assertEquals("add", f.name)
        }

        @Test
        fun hasCorrectType() {
            val f = FunctionRef("add", simpleFuncType)
            assertEquals(simpleFuncType, f.type)
        }

        @Test
        fun returnType() {
            val funcType = Type.Function(listOf(Type.I32, Type.I64), Type.F64)
            val f = FunctionRef("compute", funcType)
            assertEquals(Type.F64, f.type.ret)
        }

        @Test
        fun paramTypes() {
            val funcType = Type.Function(listOf(Type.I32, Type.I64), Type.Void)
            val f = FunctionRef("doStuff", funcType)
            assertEquals(listOf(Type.I32, Type.I64), f.type.params)
        }

        @Test
        fun isVarArgDefault() {
            val funcType = Type.Function(listOf(Type.I32), Type.I32)
            val f = FunctionRef("foo", funcType)
            assertFalse(f.type.vararg)
        }

        @Test
        fun isVarArgTrue() {
            val funcType = Type.Function(listOf(Type.Pointer(Type.I8)), Type.I32, vararg = true)
            val f = FunctionRef("printf", funcType)
            assertTrue(f.type.vararg)
        }

        @Test
        fun voidReturnNoParams() {
            val funcType = Type.Function(emptyList(), Type.Void)
            val f = FunctionRef("nop", funcType)
            assertTrue(f.type.params.isEmpty())
            assertEquals(Type.Void, f.type.ret)
        }

        @Test
        fun implementsValue() {
            val v: Value = FunctionRef("fn", simpleFuncType)
            assertEquals("fn", v.name)
            assertEquals(simpleFuncType, v.type)
        }

        @Test
        fun equalityForSameFields() {
            val a = FunctionRef("fn", simpleFuncType)
            val b = FunctionRef("fn", simpleFuncType)
            assertEquals(a, b)
            assertEquals(a.hashCode(), b.hashCode())
        }

        @Test
        fun inequalityForDifferentName() {
            assertNotEquals(
                FunctionRef("a", simpleFuncType),
                FunctionRef("b", simpleFuncType)
            )
        }

        @Test
        fun inequalityForDifferentSignature() {
            val other = Type.Function(listOf(Type.I64), Type.I64)
            assertNotEquals(
                FunctionRef("fn", simpleFuncType),
                FunctionRef("fn", other)
            )
        }
    }

    @Nested
    inner class BlockRefTests {

        @Test
        fun hasCorrectLabel() {
            val b = BlockRef("entry")
            assertEquals("entry", b.label)
        }

        @Test
        fun nameEqualsLabel() {
            val b = BlockRef("loop.header")
            assertEquals("loop.header", b.name)
        }

        @Test
        fun typeIsAlwaysLabel() {
            val b = BlockRef("exit")
            assertEquals(Type.Label, b.type)
        }

        @Test
        fun implementsValue() {
            val v: Value = BlockRef("bb0")
            assertEquals("bb0", v.name)
            assertEquals(Type.Label, v.type)
        }

        @Test
        fun equalityForSameLabel() {
            val a = BlockRef("entry")
            val b = BlockRef("entry")
            assertEquals(a, b)
            assertEquals(a.hashCode(), b.hashCode())
        }

        @Test
        fun inequalityForDifferentLabel() {
            assertNotEquals(BlockRef("entry"), BlockRef("exit"))
        }
    }

    @Nested
    inner class InstructionRefTests {

        @Test
        fun hasCorrectName() {
            val r = InstructionRef("%0", Type.I32)
            assertEquals("%0", r.name)
        }

        @Test
        fun hasCorrectType() {
            val r = InstructionRef("%1", Type.F64)
            assertEquals(Type.F64, r.type)
        }

        @Test
        fun implementsValue() {
            val v: Value = InstructionRef("%2", Type.Pointer(Type.I32))
            assertEquals("%2", v.name)
            assertEquals(Type.Pointer(Type.I32), v.type)
        }

        @Test
        fun equalityForSameFields() {
            val a = InstructionRef("%0", Type.I32)
            val b = InstructionRef("%0", Type.I32)
            assertEquals(a, b)
            assertEquals(a.hashCode(), b.hashCode())
        }

        @Test
        fun inequalityForDifferentName() {
            assertNotEquals(
                InstructionRef("%0", Type.I32),
                InstructionRef("%1", Type.I32)
            )
        }

        @Test
        fun inequalityForDifferentType() {
            assertNotEquals(
                InstructionRef("%0", Type.I32),
                InstructionRef("%0", Type.I64)
            )
        }
    }

    @Nested
    inner class ParamTests {

        @Test
        fun hasCorrectName() {
            val p = Param("argc", Type.I32)
            assertEquals("argc", p.name)
        }

        @Test
        fun hasCorrectType() {
            val p = Param("argc", Type.I32)
            assertEquals(Type.I32, p.type)
        }

        @Test
        fun factoryMethod() {
            val p = Param.of("argv", Type.Pointer(Type.Pointer(Type.I8)))
            assertEquals("argv", p.name)
            assertEquals(Type.Pointer(Type.Pointer(Type.I8)), p.type)
        }

        @Test
        fun typeCompanionFactory() {
            val p = Type.param("x", Type.F64)
            assertEquals("x", p.name)
            assertEquals(Type.F64, p.type)
        }

        @Test
        fun equalityForSameFields() {
            val a = Param("x", Type.I32)
            val b = Param("x", Type.I32)
            assertEquals(a, b)
            assertEquals(a.hashCode(), b.hashCode())
        }

        @Test
        fun inequalityForDifferentName() {
            assertNotEquals(Param("x", Type.I32), Param("y", Type.I32))
        }

        @Test
        fun inequalityForDifferentType() {
            assertNotEquals(Param("x", Type.I32), Param("x", Type.I64))
        }

        @Test
        fun isNotAValueSubtype() {
            val p = Param("x", Type.I32)
            assertFalse(p is Value)
        }
    }

    @Nested
    inner class CrossSubtypeTests {

        @Test
        fun differentSubtypesAreNotEqual() {
            val param = Parameter("x", Type.I32, 0)
            val global = GlobalRef("x", Type.I32)
            val instrRef = InstructionRef("x", Type.I32)

            assertNotEquals(param as Value, global as Value)
            assertNotEquals(param as Value, instrRef as Value)
            assertNotEquals(global as Value, instrRef as Value)
        }

        @Test
        fun blockRefNotEqualToOtherLabelValues() {
            val block = BlockRef("entry")
            val instrRef = InstructionRef("entry", Type.Label)
            assertNotEquals(block as Value, instrRef as Value)
        }

        @Test
        fun functionRefNotEqualToGlobalRefEvenWithSameNameAndType() {
            val funcType = Type.Function(emptyList(), Type.Void)
            val funcRef = FunctionRef("fn", funcType)
            val globalRef = GlobalRef("fn", funcType)
            assertNotEquals(funcRef as Value, globalRef as Value)
        }
    }
}
