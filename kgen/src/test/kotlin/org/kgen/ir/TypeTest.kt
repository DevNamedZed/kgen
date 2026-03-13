package org.kgen.ir

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class TypeTest {

    @Nested
    inner class Singletons {

        @Test
        fun i1IsSingleton() {
            assertSame(Type.I1, Type.I1)
        }

        @Test
        fun i8IsSingleton() {
            assertSame(Type.I8, Type.I8)
        }

        @Test
        fun i16IsSingleton() {
            assertSame(Type.I16, Type.I16)
        }

        @Test
        fun i32IsSingleton() {
            assertSame(Type.I32, Type.I32)
        }

        @Test
        fun i64IsSingleton() {
            assertSame(Type.I64, Type.I64)
        }

        @Test
        fun i128IsSingleton() {
            assertSame(Type.I128, Type.I128)
        }

        @Test
        fun f16IsSingleton() {
            assertSame(Type.F16, Type.F16)
        }

        @Test
        fun bf16IsSingleton() {
            assertSame(Type.BF16, Type.BF16)
        }

        @Test
        fun f32IsSingleton() {
            assertSame(Type.F32, Type.F32)
        }

        @Test
        fun f64IsSingleton() {
            assertSame(Type.F64, Type.F64)
        }

        @Test
        fun f80IsSingleton() {
            assertSame(Type.F80, Type.F80)
        }

        @Test
        fun f128IsSingleton() {
            assertSame(Type.F128, Type.F128)
        }

        @Test
        fun voidIsSingleton() {
            assertSame(Type.Void, Type.Void)
        }

        @Test
        fun labelIsSingleton() {
            assertSame(Type.Label, Type.Label)
        }

        @Test
        fun metadataIsSingleton() {
            assertSame(Type.Metadata, Type.Metadata)
        }

        @Test
        fun tokenIsSingleton() {
            assertSame(Type.Token, Type.Token)
        }

        @Test
        fun opaquePointerIsSingleton() {
            assertSame(Type.OpaquePointer, Type.OpaquePointer)
        }

        @Test
        fun allSingletonsAreDistinct() {
            val singletons: List<Type> = listOf(
                Type.I1, Type.I8, Type.I16, Type.I32, Type.I64, Type.I128,
                Type.F16, Type.BF16, Type.F32, Type.F64, Type.F80, Type.F128,
                Type.Void, Type.Label, Type.Metadata, Type.Token, Type.OpaquePointer
            )
            for (i in singletons.indices) {
                for (j in singletons.indices) {
                    if (i != j) {
                        assertNotEquals(singletons[i], singletons[j])
                    }
                }
            }
        }
    }

    @Nested
    inner class IntNTests {

        @Test
        fun equalityForSameBitWidth() {
            assertEquals(Type.IntN(24), Type.IntN(24))
        }

        @Test
        fun inequalityForDifferentBitWidth() {
            assertNotEquals(Type.IntN(24), Type.IntN(48))
        }

        @Test
        fun hashCodeConsistency() {
            assertEquals(Type.IntN(256).hashCode(), Type.IntN(256).hashCode())
        }

        @Test
        fun bitsProperty() {
            assertEquals(24, Type.IntN(24).bits)
        }
    }

    @Nested
    inner class PointerTests {

        @Test
        fun defaultAddressSpaceIsZero() {
            val ptr = Type.Pointer(Type.I32)
            assertEquals(0, ptr.addressSpace)
        }

        @Test
        fun customAddressSpace() {
            val ptr = Type.Pointer(Type.I32, 3)
            assertEquals(3, ptr.addressSpace)
        }

        @Test
        fun equalityWithSamePointeeAndAddressSpace() {
            assertEquals(Type.Pointer(Type.I64, 1), Type.Pointer(Type.I64, 1))
        }

        @Test
        fun inequalityWithDifferentPointee() {
            assertNotEquals(Type.Pointer(Type.I32), Type.Pointer(Type.I64))
        }

        @Test
        fun inequalityWithDifferentAddressSpace() {
            assertNotEquals(Type.Pointer(Type.I32, 0), Type.Pointer(Type.I32, 1))
        }

        @Test
        fun hashCodeConsistency() {
            assertEquals(
                Type.Pointer(Type.F32, 2).hashCode(),
                Type.Pointer(Type.F32, 2).hashCode()
            )
        }
    }

    @Nested
    inner class ReferenceTests {

        @Test
        fun defaultNullableIsTrue() {
            val ref = Type.Reference(Type.ClassRef("Foo"))
            assertTrue(ref.nullable)
        }

        @Test
        fun explicitNonNullable() {
            val ref = Type.Reference(Type.ClassRef("Foo"), nullable = false)
            assertFalse(ref.nullable)
        }

        @Test
        fun equalityWithSameReferentAndNullability() {
            assertEquals(
                Type.Reference(Type.I32, true),
                Type.Reference(Type.I32, true)
            )
        }

        @Test
        fun inequalityWithDifferentNullability() {
            assertNotEquals(
                Type.Reference(Type.I32, true),
                Type.Reference(Type.I32, false)
            )
        }

        @Test
        fun inequalityWithDifferentReferent() {
            assertNotEquals(
                Type.Reference(Type.I32),
                Type.Reference(Type.I64)
            )
        }
    }

    @Nested
    inner class WeakReferenceTests {

        @Test
        fun equality() {
            assertEquals(Type.WeakReference(Type.I32), Type.WeakReference(Type.I32))
        }

        @Test
        fun inequality() {
            assertNotEquals(Type.WeakReference(Type.I32), Type.WeakReference(Type.I64))
        }

        @Test
        fun referentProperty() {
            assertEquals(Type.I64, Type.WeakReference(Type.I64).referent)
        }
    }

    @Nested
    inner class InteriorRefTests {

        @Test
        fun equality() {
            assertEquals(Type.InteriorRef(Type.F32), Type.InteriorRef(Type.F32))
        }

        @Test
        fun inequality() {
            assertNotEquals(Type.InteriorRef(Type.F32), Type.InteriorRef(Type.F64))
        }

        @Test
        fun pointeeProperty() {
            assertEquals(Type.I8, Type.InteriorRef(Type.I8).pointee)
        }
    }

    @Nested
    inner class PinnedRefTests {

        @Test
        fun equality() {
            assertEquals(Type.PinnedRef(Type.I32), Type.PinnedRef(Type.I32))
        }

        @Test
        fun inequality() {
            assertNotEquals(Type.PinnedRef(Type.I32), Type.PinnedRef(Type.I64))
        }

        @Test
        fun referentProperty() {
            assertEquals(Type.I16, Type.PinnedRef(Type.I16).referent)
        }
    }

    @Nested
    inner class ArrayTests {

        @Test
        fun equality() {
            assertEquals(Type.Array(Type.I32, 10), Type.Array(Type.I32, 10))
        }

        @Test
        fun inequalityByElement() {
            assertNotEquals(Type.Array(Type.I32, 10), Type.Array(Type.I64, 10))
        }

        @Test
        fun inequalityBySize() {
            assertNotEquals(Type.Array(Type.I32, 10), Type.Array(Type.I32, 20))
        }

        @Test
        fun properties() {
            val arr = Type.Array(Type.F64, 100)
            assertEquals(Type.F64, arr.element)
            assertEquals(100L, arr.size)
        }
    }

    @Nested
    inner class VectorTests {

        @Test
        fun defaultScalableIsFalse() {
            val vec = Type.Vector(Type.F32, 4)
            assertFalse(vec.scalable)
        }

        @Test
        fun scalableVector() {
            val vec = Type.Vector(Type.I32, 4, scalable = true)
            assertTrue(vec.scalable)
        }

        @Test
        fun equalityWithSameFields() {
            assertEquals(Type.Vector(Type.I32, 8, false), Type.Vector(Type.I32, 8, false))
        }

        @Test
        fun inequalityByScalable() {
            assertNotEquals(Type.Vector(Type.I32, 4, false), Type.Vector(Type.I32, 4, true))
        }

        @Test
        fun inequalityByLanes() {
            assertNotEquals(Type.Vector(Type.I32, 4), Type.Vector(Type.I32, 8))
        }

        @Test
        fun inequalityByElement() {
            assertNotEquals(Type.Vector(Type.I32, 4), Type.Vector(Type.F32, 4))
        }
    }

    @Nested
    inner class StructTests {

        @Test
        fun defaultPackedIsFalse() {
            val s = Type.Struct("point", listOf(Type.F64, Type.F64))
            assertFalse(s.packed)
        }

        @Test
        fun packedStruct() {
            val s = Type.Struct("point", listOf(Type.F64, Type.F64), packed = true)
            assertTrue(s.packed)
        }

        @Test
        fun anonymousStruct() {
            val s = Type.Struct(null, listOf(Type.I32, Type.I64))
            assertNull(s.name)
        }

        @Test
        fun equalityWithSameFields() {
            assertEquals(
                Type.Struct("s", listOf(Type.I32, Type.I64), false),
                Type.Struct("s", listOf(Type.I32, Type.I64), false)
            )
        }

        @Test
        fun inequalityByName() {
            assertNotEquals(
                Type.Struct("a", listOf(Type.I32)),
                Type.Struct("b", listOf(Type.I32))
            )
        }

        @Test
        fun inequalityByPacked() {
            assertNotEquals(
                Type.Struct("s", listOf(Type.I32), false),
                Type.Struct("s", listOf(Type.I32), true)
            )
        }

        @Test
        fun inequalityByFields() {
            assertNotEquals(
                Type.Struct("s", listOf(Type.I32)),
                Type.Struct("s", listOf(Type.I64))
            )
        }
    }

    @Nested
    inner class OpaqueStructTests {

        @Test
        fun equality() {
            assertEquals(Type.OpaqueStruct("opaque"), Type.OpaqueStruct("opaque"))
        }

        @Test
        fun inequality() {
            assertNotEquals(Type.OpaqueStruct("a"), Type.OpaqueStruct("b"))
        }

        @Test
        fun nameProperty() {
            assertEquals("forward", Type.OpaqueStruct("forward").name)
        }
    }

    @Nested
    inner class UnionTests {

        @Test
        fun equality() {
            assertEquals(
                Type.Union("u", listOf(Type.I32, Type.F32)),
                Type.Union("u", listOf(Type.I32, Type.F32))
            )
        }

        @Test
        fun inequalityByName() {
            assertNotEquals(
                Type.Union("a", listOf(Type.I32)),
                Type.Union("b", listOf(Type.I32))
            )
        }

        @Test
        fun inequalityByVariants() {
            assertNotEquals(
                Type.Union("u", listOf(Type.I32)),
                Type.Union("u", listOf(Type.I64))
            )
        }

        @Test
        fun anonymousUnion() {
            val u = Type.Union(null, listOf(Type.I32))
            assertNull(u.name)
        }
    }

    @Nested
    inner class TaggedUnionTests {

        @Test
        fun equality() {
            val variants = listOf(
                TaggedVariant("None", 0, emptyList()),
                TaggedVariant("Some", 1, listOf(Type.I32))
            )
            assertEquals(
                Type.TaggedUnion("Option", Type.I8, variants),
                Type.TaggedUnion("Option", Type.I8, variants)
            )
        }

        @Test
        fun inequalityByName() {
            val variants = listOf(TaggedVariant("A", 0, emptyList()))
            assertNotEquals(
                Type.TaggedUnion("X", Type.I8, variants),
                Type.TaggedUnion("Y", Type.I8, variants)
            )
        }

        @Test
        fun inequalityByTagType() {
            val variants = listOf(TaggedVariant("A", 0, emptyList()))
            assertNotEquals(
                Type.TaggedUnion("T", Type.I8, variants),
                Type.TaggedUnion("T", Type.I32, variants)
            )
        }

        @Test
        fun properties() {
            val v = TaggedVariant("Val", 42, listOf(Type.I64))
            val tu = Type.TaggedUnion("MyUnion", Type.I16, listOf(v))
            assertEquals("MyUnion", tu.name)
            assertEquals(Type.I16, tu.tagType)
            assertEquals(1, tu.variants.size)
            assertEquals("Val", tu.variants[0].name)
            assertEquals(42L, tu.variants[0].tag)
            assertEquals(listOf(Type.I64), tu.variants[0].fields)
        }
    }

    @Nested
    inner class TaggedVariantTests {

        @Test
        fun equality() {
            assertEquals(
                TaggedVariant("A", 0, listOf(Type.I32)),
                TaggedVariant("A", 0, listOf(Type.I32))
            )
        }

        @Test
        fun inequalityByName() {
            assertNotEquals(
                TaggedVariant("A", 0, emptyList()),
                TaggedVariant("B", 0, emptyList())
            )
        }

        @Test
        fun inequalityByTag() {
            assertNotEquals(
                TaggedVariant("A", 0, emptyList()),
                TaggedVariant("A", 1, emptyList())
            )
        }

        @Test
        fun inequalityByFields() {
            assertNotEquals(
                TaggedVariant("A", 0, listOf(Type.I32)),
                TaggedVariant("A", 0, listOf(Type.I64))
            )
        }

        @Test
        fun hashCodeConsistency() {
            assertEquals(
                TaggedVariant("X", 5, listOf(Type.F64)).hashCode(),
                TaggedVariant("X", 5, listOf(Type.F64)).hashCode()
            )
        }
    }

    @Nested
    inner class FunctionTests {

        @Test
        fun defaultVarargIsFalse() {
            val fn = Type.Function(listOf(Type.I32), Type.I64)
            assertFalse(fn.vararg)
        }

        @Test
        fun varargFunction() {
            val fn = Type.Function(listOf(Type.Pointer(Type.I8)), Type.I32, vararg = true)
            assertTrue(fn.vararg)
        }

        @Test
        fun equalityWithSameSignature() {
            assertEquals(
                Type.Function(listOf(Type.I32, Type.F64), Type.Void, false),
                Type.Function(listOf(Type.I32, Type.F64), Type.Void, false)
            )
        }

        @Test
        fun inequalityByParams() {
            assertNotEquals(
                Type.Function(listOf(Type.I32), Type.Void),
                Type.Function(listOf(Type.I64), Type.Void)
            )
        }

        @Test
        fun inequalityByReturn() {
            assertNotEquals(
                Type.Function(listOf(Type.I32), Type.I32),
                Type.Function(listOf(Type.I32), Type.I64)
            )
        }

        @Test
        fun inequalityByVararg() {
            assertNotEquals(
                Type.Function(listOf(Type.I32), Type.Void, false),
                Type.Function(listOf(Type.I32), Type.Void, true)
            )
        }

        @Test
        fun emptyParams() {
            val fn = Type.Function(emptyList(), Type.Void)
            assertTrue(fn.params.isEmpty())
        }
    }

    @Nested
    inner class ClassRefTests {

        @Test
        fun equality() {
            assertEquals(Type.ClassRef("com.example.Foo"), Type.ClassRef("com.example.Foo"))
        }

        @Test
        fun inequality() {
            assertNotEquals(Type.ClassRef("Foo"), Type.ClassRef("Bar"))
        }

        @Test
        fun nameProperty() {
            assertEquals("MyClass", Type.ClassRef("MyClass").name)
        }
    }

    @Nested
    inner class InterfaceRefTests {

        @Test
        fun equality() {
            assertEquals(Type.InterfaceRef("Iterable"), Type.InterfaceRef("Iterable"))
        }

        @Test
        fun inequality() {
            assertNotEquals(Type.InterfaceRef("Iterable"), Type.InterfaceRef("Comparable"))
        }

        @Test
        fun nameProperty() {
            assertEquals("Runnable", Type.InterfaceRef("Runnable").name)
        }
    }

    @Nested
    inner class TypeParamTests {

        @Test
        fun equality() {
            assertEquals(Type.TypeParam("T", 0), Type.TypeParam("T", 0))
        }

        @Test
        fun inequalityByName() {
            assertNotEquals(Type.TypeParam("T", 0), Type.TypeParam("U", 0))
        }

        @Test
        fun inequalityByIndex() {
            assertNotEquals(Type.TypeParam("T", 0), Type.TypeParam("T", 1))
        }

        @Test
        fun defaultBoundsEmpty() {
            assertTrue(Type.TypeParam("T", 0).bounds.isEmpty())
        }

        @Test
        fun withBounds() {
            val tp = Type.TypeParam("T", 0, listOf(Type.ClassRef("Comparable")))
            assertEquals(1, tp.bounds.size)
            assertEquals(Type.ClassRef("Comparable"), tp.bounds[0])
        }

        @Test
        fun inequalityByBounds() {
            assertNotEquals(
                Type.TypeParam("T", 0, listOf(Type.ClassRef("A"))),
                Type.TypeParam("T", 0, listOf(Type.ClassRef("B")))
            )
        }
    }

    @Nested
    inner class ParameterizedTests {

        @Test
        fun equality() {
            assertEquals(
                Type.Parameterized(Type.ClassRef("List"), listOf(Type.I32)),
                Type.Parameterized(Type.ClassRef("List"), listOf(Type.I32))
            )
        }

        @Test
        fun inequalityByBase() {
            assertNotEquals(
                Type.Parameterized(Type.ClassRef("List"), listOf(Type.I32)),
                Type.Parameterized(Type.ClassRef("Set"), listOf(Type.I32))
            )
        }

        @Test
        fun inequalityByTypeArgs() {
            assertNotEquals(
                Type.Parameterized(Type.ClassRef("List"), listOf(Type.I32)),
                Type.Parameterized(Type.ClassRef("List"), listOf(Type.I64))
            )
        }

        @Test
        fun multipleTypeArgs() {
            val map = Type.Parameterized(Type.ClassRef("Map"), listOf(Type.ClassRef("String"), Type.I32))
            assertEquals(2, map.typeArgs.size)
        }
    }

    @Nested
    inner class NullableTests {

        @Test
        fun equality() {
            assertEquals(Type.Nullable(Type.I32), Type.Nullable(Type.I32))
        }

        @Test
        fun inequality() {
            assertNotEquals(Type.Nullable(Type.I32), Type.Nullable(Type.I64))
        }

        @Test
        fun innerProperty() {
            assertEquals(Type.F64, Type.Nullable(Type.F64).inner)
        }
    }

    @Nested
    inner class PlatformTypeTests {

        @Test
        fun equality() {
            assertEquals(
                Type.PlatformType("cuda.shared", mapOf("align" to "16")),
                Type.PlatformType("cuda.shared", mapOf("align" to "16"))
            )
        }

        @Test
        fun inequalityByName() {
            assertNotEquals(
                Type.PlatformType("a"),
                Type.PlatformType("b")
            )
        }

        @Test
        fun inequalityByProperties() {
            assertNotEquals(
                Type.PlatformType("x", mapOf("k" to "v1")),
                Type.PlatformType("x", mapOf("k" to "v2"))
            )
        }

        @Test
        fun defaultPropertiesEmpty() {
            assertTrue(Type.PlatformType("native").properties.isEmpty())
        }
    }

    @Nested
    inner class CompanionFactoryMethods {

        @Test
        fun pointerFactory() {
            assertEquals(Type.Pointer(Type.I32), Type.pointer(Type.I32))
        }

        @Test
        fun pointerFactoryWithAddressSpace() {
            assertEquals(Type.Pointer(Type.I32, 3), Type.pointer(Type.I32, 3))
        }

        @Test
        fun opaquePointerFactory() {
            assertSame(Type.OpaquePointer, Type.opaquePointer())
        }

        @Test
        fun referenceFactory() {
            assertEquals(Type.Reference(Type.ClassRef("Foo")), Type.reference(Type.ClassRef("Foo")))
        }

        @Test
        fun referenceFactoryNonNullable() {
            assertEquals(
                Type.Reference(Type.ClassRef("Foo"), false),
                Type.reference(Type.ClassRef("Foo"), false)
            )
        }

        @Test
        fun weakReferenceFactory() {
            assertEquals(Type.WeakReference(Type.I32), Type.weakReference(Type.I32))
        }

        @Test
        fun interiorRefFactory() {
            assertEquals(Type.InteriorRef(Type.F64), Type.interiorRef(Type.F64))
        }

        @Test
        fun pinnedRefFactory() {
            assertEquals(Type.PinnedRef(Type.I32), Type.pinnedRef(Type.I32))
        }

        @Test
        fun arrayFactory() {
            assertEquals(Type.Array(Type.I32, 10), Type.array(Type.I32, 10))
        }

        @Test
        fun vectorFactory() {
            assertEquals(Type.Vector(Type.F32, 4), Type.vector(Type.F32, 4))
        }

        @Test
        fun vectorFactoryScalable() {
            assertEquals(Type.Vector(Type.F32, 4, true), Type.vector(Type.F32, 4, true))
        }

        @Test
        fun structFactory() {
            assertEquals(
                Type.Struct("s", listOf(Type.I32, Type.F64)),
                Type.struct("s", listOf(Type.I32, Type.F64))
            )
        }

        @Test
        fun structFactoryPacked() {
            assertEquals(
                Type.Struct("s", listOf(Type.I32), true),
                Type.struct("s", listOf(Type.I32), true)
            )
        }

        @Test
        fun unionFactory() {
            assertEquals(
                Type.Union("u", listOf(Type.I32, Type.F32)),
                Type.union("u", listOf(Type.I32, Type.F32))
            )
        }

        @Test
        fun functionFactory() {
            assertEquals(
                Type.Function(listOf(Type.I32), Type.I64),
                Type.function(listOf(Type.I32), Type.I64)
            )
        }

        @Test
        fun functionFactoryVararg() {
            assertEquals(
                Type.Function(listOf(Type.Pointer(Type.I8)), Type.I32, true),
                Type.function(listOf(Type.Pointer(Type.I8)), Type.I32, true)
            )
        }

        @Test
        fun classRefFactory() {
            assertEquals(Type.ClassRef("Foo"), Type.classRef("Foo"))
        }

        @Test
        fun interfaceRefFactory() {
            assertEquals(Type.InterfaceRef("Bar"), Type.interfaceRef("Bar"))
        }

        @Test
        fun nullableFactory() {
            assertEquals(Type.Nullable(Type.I32), Type.nullable(Type.I32))
        }
    }

    @Nested
    inner class ConstantFactoryMethods {

        @Test
        fun i1Factory() {
            val c = Type.i1(true)
            assertEquals(Constant.I1(true), c)
            assertEquals(Type.I1, c.type)
        }

        @Test
        fun i8Factory() {
            val c = Type.i8(42)
            assertEquals(Constant.I8(42.toByte()), c)
            assertEquals(Type.I8, c.type)
        }

        @Test
        fun i16Factory() {
            val c = Type.i16(1000)
            assertEquals(Constant.I16(1000.toShort()), c)
            assertEquals(Type.I16, c.type)
        }

        @Test
        fun i32Factory() {
            val c = Type.i32(123456)
            assertEquals(Constant.I32(123456), c)
            assertEquals(Type.I32, c.type)
        }

        @Test
        fun i64Factory() {
            val c = Type.i64(9876543210L)
            assertEquals(Constant.I64(9876543210L), c)
            assertEquals(Type.I64, c.type)
        }

        @Test
        fun i128Factory() {
            val c = Type.i128(42L)
            assertEquals(Constant.I128(42L), c)
            assertEquals(Type.I128, c.type)
        }

        @Test
        fun f16Factory() {
            val c = Type.f16(1.5f)
            assertEquals(Constant.F16(1.5f), c)
            assertEquals(Type.F16, c.type)
        }

        @Test
        fun bf16Factory() {
            val c = Type.bf16(2.0f)
            assertEquals(Constant.BF16(2.0f), c)
            assertEquals(Type.BF16, c.type)
        }

        @Test
        fun f32Factory() {
            val c = Type.f32(3.14f)
            assertEquals(Constant.F32(3.14f), c)
            assertEquals(Type.F32, c.type)
        }

        @Test
        fun f64Factory() {
            val c = Type.f64(2.71828)
            assertEquals(Constant.F64(2.71828), c)
            assertEquals(Type.F64, c.type)
        }

        @Test
        fun stringFactory() {
            val c = Type.string("hello")
            assertEquals(Constant.StringConst("hello", true), c)
        }

        @Test
        fun stringFactoryNotNullTerminated() {
            val c = Type.string("hello", false)
            assertEquals(Constant.StringConst("hello", false), c)
        }

        @Test
        fun nullPtrFactory() {
            assertSame(Constant.NullPtr, Type.nullPtr())
        }

        @Test
        fun nullRefFactory() {
            assertSame(Constant.NullRef, Type.nullRef())
        }

        @Test
        fun zeroFactory() {
            val c = Type.zero(Type.I32)
            assertEquals(Constant.ZeroInitializer(Type.I32), c)
        }

        @Test
        fun undefFactory() {
            val c = Type.undef(Type.F64)
            assertEquals(Constant.Undef(Type.F64), c)
        }

        @Test
        fun poisonFactory() {
            val c = Type.poison(Type.I32)
            assertEquals(Constant.Poison(Type.I32), c)
        }
    }

    @Nested
    inner class ParamFactory {

        @Test
        fun paramFactory() {
            val p = Type.param("x", Type.I32)
            assertEquals(Param("x", Type.I32), p)
        }

        @Test
        fun paramProperties() {
            val p = Type.param("y", Type.F64)
            assertEquals("y", p.name)
            assertEquals(Type.F64, p.type)
        }
    }

    @Nested
    inner class NestedTypes {

        @Test
        fun pointerToArrayOfI32() {
            val inner = Type.Array(Type.I32, 10)
            val ptr = Type.Pointer(inner)
            assertEquals(Type.Pointer(Type.Array(Type.I32, 10)), ptr)
            assertEquals(inner, ptr.pointee)
        }

        @Test
        fun arrayOfPointers() {
            val arr = Type.Array(Type.Pointer(Type.I8), 5)
            assertEquals(Type.Pointer(Type.I8), arr.element)
        }

        @Test
        fun functionReturningPointerToStruct() {
            val structType = Type.Struct("node", listOf(Type.I32, Type.Pointer(Type.OpaqueStruct("node"))))
            val fnType = Type.Function(listOf(Type.I32), Type.Pointer(structType))
            assertEquals(Type.Pointer(structType), fnType.ret)
        }

        @Test
        fun structWithNestedTypes() {
            val innerStruct = Type.Struct("inner", listOf(Type.I8, Type.I16))
            val outerStruct = Type.Struct("outer", listOf(innerStruct, Type.Array(Type.F64, 3)))
            assertEquals(2, outerStruct.fields.size)
            assertEquals(innerStruct, outerStruct.fields[0])
        }

        @Test
        fun vectorOfVectors() {
            val innerVec = Type.Vector(Type.F32, 4)
            val arr = Type.Array(innerVec, 8)
            assertEquals(innerVec, arr.element)
        }

        @Test
        fun parameterizedWithNestedTypeArgs() {
            val listOfLists = Type.Parameterized(
                Type.ClassRef("List"),
                listOf(Type.Parameterized(Type.ClassRef("List"), listOf(Type.I32)))
            )
            val innerArg = listOfLists.typeArgs[0]
            assertTrue(innerArg is Type.Parameterized)
            assertEquals(Type.ClassRef("List"), (innerArg as Type.Parameterized).base)
        }

        @Test
        fun referenceToParameterizedType() {
            val ref = Type.Reference(
                Type.Parameterized(Type.ClassRef("Optional"), listOf(Type.ClassRef("String"))),
                nullable = false
            )
            assertFalse(ref.nullable)
            assertTrue(ref.referent is Type.Parameterized)
        }

        @Test
        fun deeplyNestedStructuralEquality() {
            val type1 = Type.Pointer(
                Type.Function(
                    listOf(Type.Array(Type.I32, 10), Type.Pointer(Type.I8)),
                    Type.Struct(null, listOf(Type.I64, Type.F64))
                )
            )
            val type2 = Type.Pointer(
                Type.Function(
                    listOf(Type.Array(Type.I32, 10), Type.Pointer(Type.I8)),
                    Type.Struct(null, listOf(Type.I64, Type.F64))
                )
            )
            assertEquals(type1, type2)
            assertEquals(type1.hashCode(), type2.hashCode())
        }

        @Test
        fun deeplyNestedStructuralInequality() {
            val type1 = Type.Pointer(
                Type.Function(
                    listOf(Type.Array(Type.I32, 10)),
                    Type.Void
                )
            )
            val type2 = Type.Pointer(
                Type.Function(
                    listOf(Type.Array(Type.I32, 11)),
                    Type.Void
                )
            )
            assertNotEquals(type1, type2)
        }

        @Test
        fun nullableWrappingNestedType() {
            val t = Type.Nullable(Type.Pointer(Type.Array(Type.F32, 4)))
            assertEquals(Type.Pointer(Type.Array(Type.F32, 4)), t.inner)
        }

        @Test
        fun taggedUnionWithComplexVariants() {
            val variants = listOf(
                TaggedVariant("None", 0, emptyList()),
                TaggedVariant("Some", 1, listOf(Type.Pointer(Type.Struct("Data", listOf(Type.I32, Type.F64)))))
            )
            val tu = Type.TaggedUnion("Option", Type.I8, variants)
            val someFields = tu.variants[1].fields
            assertEquals(1, someFields.size)
            assertTrue(someFields[0] is Type.Pointer)
        }
    }

    @Nested
    inner class SealedInterfaceContract {

        @Test
        fun allTypesImplementType() {
            val types: List<Type> = listOf(
                Type.I1, Type.I8, Type.I16, Type.I32, Type.I64, Type.I128,
                Type.IntN(24),
                Type.F16, Type.BF16, Type.F32, Type.F64, Type.F80, Type.F128,
                Type.Void, Type.Label, Type.Metadata, Type.Token,
                Type.Pointer(Type.I32),
                Type.OpaquePointer,
                Type.Reference(Type.I32),
                Type.WeakReference(Type.I32),
                Type.InteriorRef(Type.I32),
                Type.PinnedRef(Type.I32),
                Type.Array(Type.I32, 1),
                Type.Vector(Type.I32, 4),
                Type.Struct("s", listOf(Type.I32)),
                Type.OpaqueStruct("o"),
                Type.Union("u", listOf(Type.I32)),
                Type.TaggedUnion("t", Type.I8, listOf(TaggedVariant("A", 0, emptyList()))),
                Type.Function(emptyList(), Type.Void),
                Type.ClassRef("C"),
                Type.InterfaceRef("I"),
                Type.TypeParam("T", 0),
                Type.Parameterized(Type.ClassRef("L"), listOf(Type.I32)),
                Type.Nullable(Type.I32),
                Type.PlatformType("custom")
            )
            for (t in types) {
                assertTrue(t is Type, "Expected ${t::class.simpleName} to implement Type")
            }
        }
    }
}
