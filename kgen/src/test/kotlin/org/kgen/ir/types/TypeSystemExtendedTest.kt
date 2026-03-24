package org.kgen.ir.types

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*

class TypeSystemExtendedTest {

    // --- Primitive integer types are data objects ---

    @Test
    fun i1IsDistinctSingleton() {
        assertSame(Type.I1, Type.I1)
        assertNotEquals(Type.I1 as Type, Type.I8 as Type)
    }

    @Test
    fun i32IsDistinctSingleton() {
        assertSame(Type.I32, Type.I32)
        assertNotEquals(Type.I32 as Type, Type.I64 as Type)
    }

    @Test
    fun i128IsDistinctSingleton() {
        assertSame(Type.I128, Type.I128)
    }

    // --- Custom IntN widths ---

    @Test
    fun intNUnusualWidths() {
        val i3 = Type.IntN(3)
        val i7 = Type.IntN(7)
        val i24 = Type.IntN(24)
        val i48 = Type.IntN(48)
        assertEquals(3, i3.bits)
        assertEquals(7, i7.bits)
        assertEquals(24, i24.bits)
        assertEquals(48, i48.bits)
    }

    @Test
    fun intNLargeWidth() {
        val i512 = Type.IntN(512)
        assertEquals(512, i512.bits)
    }

    // --- Nested pointer types ---

    @Test
    fun pointerToPointer() {
        val pp = Type.Pointer(Type.Pointer(Type.I32))
        val inner = pp.pointee as Type.Pointer
        assertEquals(Type.I32, inner.pointee)
    }

    @Test
    fun pointerToArray() {
        val pa = Type.Pointer(Type.Array(Type.I8, 256))
        val arr = pa.pointee as Type.Array
        assertEquals(Type.I8, arr.element)
        assertEquals(256L, arr.size)
    }

    @Test
    fun pointerToStruct() {
        val ps = Type.Pointer(Type.Struct("Node", listOf(Type.I32, Type.OpaquePointer)))
        val s = ps.pointee as Type.Struct
        assertEquals("Node", s.name)
        assertEquals(2, s.fields.size)
    }

    @Test
    fun pointerToFunction() {
        val pf = Type.Pointer(Type.Function(listOf(Type.I32), Type.Void))
        val fn = pf.pointee as Type.Function
        assertEquals(1, fn.params.size)
        assertEquals(Type.Void, fn.ret)
    }

    @Test
    fun pointerAddressSpaces() {
        for (as_ in 0..5) {
            val p = Type.Pointer(Type.I32, as_)
            assertEquals(as_, p.addressSpace)
        }
    }

    @Test
    fun differentAddressSpacesNotEqual() {
        val p0 = Type.Pointer(Type.I32, 0)
        val p1 = Type.Pointer(Type.I32, 1)
        val p2 = Type.Pointer(Type.I32, 2)
        assertNotEquals(p0, p1)
        assertNotEquals(p1, p2)
        assertNotEquals(p0, p2)
    }

    // --- Array types ---

    @Test
    fun arrayOfZeroSize() {
        val arr = Type.Array(Type.I32, 0)
        assertEquals(0L, arr.size)
    }

    @Test
    fun arrayOfLargeSize() {
        val arr = Type.Array(Type.I8, 1_000_000)
        assertEquals(1_000_000L, arr.size)
    }

    @Test
    fun nestedArrays() {
        val mat = Type.Array(Type.Array(Type.F32, 4), 4)
        val inner = mat.element as Type.Array
        assertEquals(Type.F32, inner.element)
        assertEquals(4L, inner.size)
        assertEquals(4L, mat.size)
    }

    @Test
    fun arrayOfStructs() {
        val s = Type.Struct("Item", listOf(Type.I32, Type.F64))
        val arr = Type.Array(s, 10)
        assertEquals(s, arr.element)
        assertEquals(10L, arr.size)
    }

    // --- Vector types ---

    @Test
    fun vectorI32x4() {
        val v = Type.Vector(Type.I32, 4)
        assertEquals(Type.I32, v.element)
        assertEquals(4, v.lanes)
        assertFalse(v.scalable)
    }

    @Test
    fun vectorF64x2() {
        val v = Type.Vector(Type.F64, 2)
        assertEquals(Type.F64, v.element)
        assertEquals(2, v.lanes)
    }

    @Test
    fun vectorI8x16() {
        val v = Type.Vector(Type.I8, 16)
        assertEquals(Type.I8, v.element)
        assertEquals(16, v.lanes)
    }

    @Test
    fun scalableVectorSVE() {
        val v = Type.Vector(Type.F32, 4, scalable = true)
        assertTrue(v.scalable)
        assertEquals(4, v.lanes)
    }

    @Test
    fun vectorEquality() {
        assertEquals(Type.Vector(Type.I32, 4), Type.Vector(Type.I32, 4))
        assertNotEquals(Type.Vector(Type.I32, 4), Type.Vector(Type.I32, 8))
        assertNotEquals(Type.Vector(Type.I32, 4), Type.Vector(Type.F32, 4))
    }

    @Test
    fun vectorScalableNotEqualFixed() {
        assertNotEquals(
            Type.Vector(Type.I32, 4, scalable = false),
            Type.Vector(Type.I32, 4, scalable = true)
        )
    }

    // --- Struct types ---

    @Test
    fun emptyStruct() {
        val s = Type.Struct("Empty", emptyList())
        assertEquals(0, s.fields.size)
    }

    @Test
    fun structWithManyFields() {
        val fields = (1..20).map { Type.I32 as Type }
        val s = Type.Struct("Big", fields)
        assertEquals(20, s.fields.size)
    }

    @Test
    fun structEquality() {
        val s1 = Type.Struct("Point", listOf(Type.F64, Type.F64))
        val s2 = Type.Struct("Point", listOf(Type.F64, Type.F64))
        assertEquals(s1, s2)
    }

    @Test
    fun structInequalityDifferentName() {
        assertNotEquals(
            Type.Struct("A", listOf(Type.I32)),
            Type.Struct("B", listOf(Type.I32))
        )
    }

    @Test
    fun structInequalityDifferentFields() {
        assertNotEquals(
            Type.Struct("S", listOf(Type.I32)),
            Type.Struct("S", listOf(Type.I64))
        )
    }

    @Test
    fun packedStructNotEqualUnpacked() {
        assertNotEquals(
            Type.Struct("S", listOf(Type.I8, Type.I32), packed = true),
            Type.Struct("S", listOf(Type.I8, Type.I32), packed = false)
        )
    }

    @Test
    fun recursiveStructViaPointer() {
        val node = Type.Struct("Node", listOf(Type.I32, Type.Pointer(Type.OpaqueStruct("Node"))))
        assertEquals(2, node.fields.size)
        val ptrField = node.fields[1] as Type.Pointer
        val pointee = ptrField.pointee as Type.OpaqueStruct
        assertEquals("Node", pointee.name)
    }

    // --- Union types ---

    @Test
    fun unionEquality() {
        val u1 = Type.Union("U", listOf(Type.I32, Type.F32))
        val u2 = Type.Union("U", listOf(Type.I32, Type.F32))
        assertEquals(u1, u2)
    }

    @Test
    fun taggedUnionVariantFields() {
        val tu = Type.TaggedUnion("Option", Type.I8, listOf(
            TaggedVariant("None", 0, emptyList()),
            TaggedVariant("Some", 1, listOf(Type.I32)),
        ))
        assertEquals(0, tu.variants[0].fields.size)
        assertEquals(1, tu.variants[1].fields.size)
    }

    // --- Function types ---

    @Test
    fun functionWithNoParams() {
        val fn = Type.Function(emptyList(), Type.I32)
        assertEquals(0, fn.params.size)
        assertEquals(Type.I32, fn.ret)
    }

    @Test
    fun functionWithManyParams() {
        val params = (1..10).map { Type.I32 as Type }
        val fn = Type.Function(params, Type.Void)
        assertEquals(10, fn.params.size)
    }

    @Test
    fun functionReturningPointer() {
        val fn = Type.Function(listOf(Type.I32), Type.Pointer(Type.I8))
        val ret = fn.ret as Type.Pointer
        assertEquals(Type.I8, ret.pointee)
    }

    @Test
    fun functionReturningStruct() {
        val s = Type.Struct("Pair", listOf(Type.I32, Type.I32))
        val fn = Type.Function(emptyList(), s)
        assertEquals(s, fn.ret)
    }

    @Test
    fun varargFunctionInequality() {
        val f1 = Type.Function(listOf(Type.Pointer(Type.I8)), Type.I32, vararg = false)
        val f2 = Type.Function(listOf(Type.Pointer(Type.I8)), Type.I32, vararg = true)
        assertNotEquals(f1, f2)
    }

    // --- ClassRef / InterfaceRef ---

    @Test
    fun classRefWithPackage() {
        val ref = Type.ClassRef("java.util.ArrayList")
        assertEquals("java.util.ArrayList", ref.name)
    }

    @Test
    fun interfaceRefEquality() {
        assertEquals(Type.InterfaceRef("Runnable"), Type.InterfaceRef("Runnable"))
        assertNotEquals(Type.InterfaceRef("Runnable"), Type.InterfaceRef("Callable"))
    }

    @Test
    fun classRefNotEqualInterfaceRef() {
        assertNotEquals(Type.ClassRef("Foo") as Type, Type.InterfaceRef("Foo") as Type)
    }

    // --- Reference types ---

    @Test
    fun referenceEquality() {
        val r1 = Type.Reference(Type.ClassRef("Foo"))
        val r2 = Type.Reference(Type.ClassRef("Foo"))
        assertEquals(r1, r2)
    }

    @Test
    fun referenceNullableNotEqualNonNullable() {
        val nullable = Type.Reference(Type.ClassRef("Foo"), nullable = true)
        val nonNull = Type.Reference(Type.ClassRef("Foo"), nullable = false)
        assertNotEquals(nullable, nonNull)
    }

    @Test
    fun weakReferenceNotEqualReference() {
        val ref = Type.Reference(Type.ClassRef("Foo"))
        val weak = Type.WeakReference(Type.ClassRef("Foo"))
        assertNotEquals(ref as Type, weak as Type)
    }

    // --- Generics ---

    @Test
    fun typeParamNoBounds() {
        val tp = Type.TypeParam("T", 0)
        assertEquals(0, tp.bounds.size)
    }

    @Test
    fun typeParamMultipleBounds() {
        val tp = Type.TypeParam("T", 0, listOf(
            Type.ClassRef("Comparable"),
            Type.InterfaceRef("Serializable"),
        ))
        assertEquals(2, tp.bounds.size)
    }

    @Test
    fun parameterizedWithMultipleArgs() {
        val p = Type.Parameterized(
            Type.ClassRef("Map"),
            listOf(Type.ClassRef("String"), Type.ClassRef("Integer"))
        )
        assertEquals(2, p.typeArgs.size)
    }

    @Test
    fun nestedParameterized() {
        val inner = Type.Parameterized(Type.ClassRef("List"), listOf(Type.I32))
        val outer = Type.Parameterized(Type.ClassRef("Optional"), listOf(inner))
        val arg = outer.typeArgs[0] as Type.Parameterized
        assertEquals(Type.ClassRef("List"), arg.base)
    }

    // --- Nullable ---

    @Test
    fun nullableEquality() {
        assertEquals(Type.Nullable(Type.I32), Type.Nullable(Type.I32))
        assertNotEquals(Type.Nullable(Type.I32), Type.Nullable(Type.I64))
    }

    @Test
    fun nullableNested() {
        val nn = Type.Nullable(Type.Nullable(Type.I32))
        val inner = nn.inner as Type.Nullable
        assertEquals(Type.I32, inner.inner)
    }

    // --- PlatformType ---

    @Test
    fun platformTypeEquality() {
        val pt1 = Type.PlatformType("CLR.ValueType", mapOf("ns" to "System"))
        val pt2 = Type.PlatformType("CLR.ValueType", mapOf("ns" to "System"))
        assertEquals(pt1, pt2)
    }

    @Test
    fun platformTypeEmpty() {
        val pt = Type.PlatformType("JVM.void")
        assertEquals(0, pt.properties.size)
    }

    // --- Constant edge cases ---

    @Test
    fun constantI32Negative() {
        val c = Constant.I32(-1)
        assertEquals(-1, c.value)
    }

    @Test
    fun constantI32MinMax() {
        assertEquals(Int.MIN_VALUE, Constant.I32(Int.MIN_VALUE).value)
        assertEquals(Int.MAX_VALUE, Constant.I32(Int.MAX_VALUE).value)
    }

    @Test
    fun constantI64MinMax() {
        assertEquals(Long.MIN_VALUE, Constant.I64(Long.MIN_VALUE).value)
        assertEquals(Long.MAX_VALUE, Constant.I64(Long.MAX_VALUE).value)
    }

    @Test
    fun constantF32Special() {
        assertTrue(Constant.F32(Float.NaN).value.isNaN())
        assertEquals(Float.POSITIVE_INFINITY, Constant.F32(Float.POSITIVE_INFINITY).value)
        assertEquals(Float.NEGATIVE_INFINITY, Constant.F32(Float.NEGATIVE_INFINITY).value)
    }

    @Test
    fun constantF64Special() {
        assertTrue(Constant.F64(Double.NaN).value.isNaN())
        assertEquals(Double.POSITIVE_INFINITY, Constant.F64(Double.POSITIVE_INFINITY).value)
        assertEquals(Double.NEGATIVE_INFINITY, Constant.F64(Double.NEGATIVE_INFINITY).value)
    }

    @Test
    fun constantI8Range() {
        assertEquals(Byte.MIN_VALUE, Constant.I8(Byte.MIN_VALUE).value)
        assertEquals(Byte.MAX_VALUE, Constant.I8(Byte.MAX_VALUE).value)
    }

    @Test
    fun constantStringEmpty() {
        val c = Constant.StringConst("")
        val arr = c.type as Type.Array
        assertEquals(1L, arr.size) // just null terminator
    }

    @Test
    fun constantStringUnicode() {
        val c = Constant.StringConst("hello world", nullTerminated = false)
        val arr = c.type as Type.Array
        assertEquals(11L, arr.size)
    }

    @Test
    fun constantZeroInitializerTypes() {
        for (t in listOf(Type.I32, Type.I64, Type.F32, Type.F64, Type.Array(Type.I8, 10))) {
            val z = Constant.ZeroInitializer(t)
            assertEquals(t, z.type)
        }
    }

    @Test
    fun constantUndefTypes() {
        for (t in listOf(Type.I1, Type.I32, Type.F64, Type.Pointer(Type.I8))) {
            val u = Constant.Undef(t)
            assertEquals(t, u.type)
        }
    }

    @Test
    fun constantPoisonTypes() {
        for (t in listOf(Type.I32, Type.F32, Type.Vector(Type.I32, 4))) {
            val p = Constant.Poison(t)
            assertEquals(t, p.type)
        }
    }

    // --- ClassDefinition edge cases ---

    @Test
    fun classDefNoFieldsNoMethods() {
        val cls = ClassDefinition(name = "Empty")
        assertEquals(0, cls.fields.size)
        assertEquals(0, cls.methods.size)
    }

    @Test
    fun classDefFinal() {
        val cls = ClassDefinition(name = "Final", isFinal = true)
        assertTrue(cls.isFinal)
        assertFalse(cls.isAbstract)
    }

    @Test
    fun classDefAbstract() {
        val cls = ClassDefinition(name = "Abstract", isAbstract = true)
        assertTrue(cls.isAbstract)
        assertFalse(cls.isFinal)
    }

    @Test
    fun classDefWithMultipleInterfaces() {
        val cls = ClassDefinition(name = "Multi", interfaces = listOf("A", "B", "C", "D"))
        assertEquals(4, cls.interfaces.size)
    }

    // --- FieldDefinition ---

    @Test
    fun fieldDefDefault() {
        val f = FieldDefinition("x", Type.I32)
        assertEquals("x", f.name)
        assertEquals(Type.I32, f.type)
        assertEquals(MemberVisibility.PRIVATE, f.visibility)
        assertFalse(f.isFinal)
    }

    @Test
    fun fieldDefPublicFinal() {
        val f = FieldDefinition("count", Type.I32, isFinal = true, visibility = MemberVisibility.PUBLIC)
        assertTrue(f.isFinal)
        assertEquals(MemberVisibility.PUBLIC, f.visibility)
    }

    // --- MethodDefinition ---

    @Test
    fun methodDefDefault() {
        val m = MethodDefinition("foo", emptyList(), Type.Void)
        assertEquals("foo", m.name)
        assertEquals(0, m.params.size)
        assertEquals(Type.Void, m.returnType)
        assertFalse(m.isStatic)
        assertFalse(m.isAbstract)
        assertFalse(m.isFinal)
    }

    @Test
    fun methodDefWithParams() {
        val m = MethodDefinition("add", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        assertEquals(2, m.params.size)
        assertEquals("a", m.params[0].name)
    }

    @Test
    fun methodDefAbstract() {
        val m = MethodDefinition("draw", emptyList(), Type.Void, isAbstract = true)
        assertTrue(m.isAbstract)
    }

    // --- EnumDefinition ---

    @Test
    fun enumDefEmpty() {
        val e = EnumDefinition("Empty", emptyList())
        assertEquals(0, e.variants.size)
    }

    @Test
    fun enumVariantDefaults() {
        val v = EnumVariant("X", 42)
        assertEquals("X", v.name)
        assertEquals(42, v.ordinal)
        assertEquals(0, v.fields.size)
    }

    // --- StructDefinition ---

    @Test
    fun structDefEmpty() {
        val s = StructDefinition("Empty", emptyList())
        assertEquals(0, s.fields.size)
    }

    @Test
    fun structDefWithAlignment() {
        val s = StructDefinition("Aligned", listOf(Param("x", Type.I32)), align = 16)
        assertEquals(16, s.align)
    }

    // --- Type distinction ---

    @Test
    fun allPrimitiveTypesDistinct() {
        val types = setOf(
            Type.I1, Type.I8, Type.I16, Type.I32, Type.I64, Type.I128,
            Type.F16, Type.BF16, Type.F32, Type.F64, Type.F80, Type.F128,
            Type.Void, Type.Label, Type.Metadata, Type.Token,
            Type.OpaquePointer,
        )
        assertEquals(17, types.size, "All primitive types should be distinct")
    }

    @Test
    fun compoundTypesDistinct() {
        val types = setOf<Type>(
            Type.Pointer(Type.I32),
            Type.Array(Type.I32, 1),
            Type.Vector(Type.I32, 1),
            Type.Function(listOf(Type.I32), Type.I32),
            Type.Struct("S", listOf(Type.I32)),
            Type.ClassRef("C"),
            Type.InterfaceRef("I"),
            Type.Nullable(Type.I32),
        )
        assertEquals(8, types.size, "All compound types should be distinct")
    }

    // --- Type hashCode consistency ---

    @Test
    fun compoundTypeHashCodeConsistency() {
        val types = listOf(
            Type.Pointer(Type.I32),
            Type.Pointer(Type.I32, 1),
            Type.Array(Type.I8, 256),
            Type.Vector(Type.F32, 4),
            Type.Function(listOf(Type.I32, Type.I64), Type.Void),
            Type.Struct("P", listOf(Type.F64, Type.F64)),
            Type.ClassRef("Test"),
            Type.InterfaceRef("ITest"),
            Type.Nullable(Type.F32),
            Type.Reference(Type.ClassRef("Obj")),
        )
        for (t in types) {
            val h1 = t.hashCode()
            val h2 = t.hashCode()
            assertEquals(h1, h2, "hashCode must be stable for $t")
        }
    }
}
