package org.kgen.ir.types

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*

class TypeSystemTest {

    // --- Primitive integer types ---

    @Test
    fun `primitive integer types are singletons`() {
        assertSame(Type.I1, Type.I1)
        assertSame(Type.I8, Type.I8)
        assertSame(Type.I16, Type.I16)
        assertSame(Type.I32, Type.I32)
        assertSame(Type.I64, Type.I64)
        assertSame(Type.I128, Type.I128)
    }

    @Test
    fun `primitive integer types are distinct`() {
        val types = listOf(Type.I1, Type.I8, Type.I16, Type.I32, Type.I64, Type.I128)
        assertEquals(types.size, types.toSet().size)
    }

    @Test
    fun `IntN creates arbitrary width integer`() {
        val i24 = Type.IntN(24)
        val i256 = Type.IntN(256)
        assertEquals(24, i24.bits)
        assertEquals(256, i256.bits)
        assertNotEquals(i24, i256)
    }

    @Test
    fun `IntN equality is structural`() {
        assertEquals(Type.IntN(24), Type.IntN(24))
        assertNotEquals(Type.IntN(24), Type.IntN(32))
    }

    // --- Floating point types ---

    @Test
    fun `floating point types are singletons`() {
        assertSame(Type.F16, Type.F16)
        assertSame(Type.BF16, Type.BF16)
        assertSame(Type.F32, Type.F32)
        assertSame(Type.F64, Type.F64)
        assertSame(Type.F80, Type.F80)
        assertSame(Type.F128, Type.F128)
    }

    @Test
    fun `floating point types are distinct`() {
        val types = listOf(Type.F16, Type.BF16, Type.F32, Type.F64, Type.F80, Type.F128)
        assertEquals(types.size, types.toSet().size)
    }

    // --- Void and special types ---

    @Test
    fun `void is a singleton`() {
        assertSame(Type.Void, Type.Void)
    }

    @Test
    fun `label metadata and token are singletons`() {
        assertSame(Type.Label, Type.Label)
        assertSame(Type.Metadata, Type.Metadata)
        assertSame(Type.Token, Type.Token)
    }

    // --- Pointer types ---

    @Test
    fun `typed pointer wraps pointee`() {
        val ptr = Type.Pointer(Type.I32)
        assertEquals(Type.I32, ptr.pointee)
        assertEquals(0, ptr.addressSpace)
    }

    @Test
    fun `typed pointer with address space`() {
        val ptr = Type.Pointer(Type.F64, addressSpace = 3)
        assertEquals(3, ptr.addressSpace)
        assertEquals(Type.F64, ptr.pointee)
    }

    @Test
    fun `pointer equality is structural`() {
        assertEquals(Type.Pointer(Type.I32), Type.Pointer(Type.I32))
        assertNotEquals(Type.Pointer(Type.I32), Type.Pointer(Type.I64))
        assertNotEquals(Type.Pointer(Type.I32, 0), Type.Pointer(Type.I32, 1))
    }

    @Test
    fun `opaque pointer is a singleton`() {
        assertSame(Type.OpaquePointer, Type.OpaquePointer)
        assertNotEquals(Type.OpaquePointer as Type, Type.Pointer(Type.I32) as Type)
    }

    @Test
    fun `companion pointer factory`() {
        val ptr = Type.pointer(Type.I32, 2)
        assertEquals(Type.I32, ptr.pointee)
        assertEquals(2, ptr.addressSpace)
    }

    // --- Reference types ---

    @Test
    fun `reference wraps referent`() {
        val ref = Type.Reference(Type.ClassRef("Foo"))
        assertEquals(Type.ClassRef("Foo"), ref.referent)
        assertTrue(ref.nullable)
    }

    @Test
    fun `non-nullable reference`() {
        val ref = Type.Reference(Type.ClassRef("Foo"), nullable = false)
        assertFalse(ref.nullable)
    }

    @Test
    fun `weak reference`() {
        val weak = Type.WeakReference(Type.ClassRef("Bar"))
        assertEquals(Type.ClassRef("Bar"), weak.referent)
    }

    // --- Array type ---

    @Test
    fun `array type has element and size`() {
        val arr = Type.Array(Type.I32, 10)
        assertEquals(Type.I32, arr.element)
        assertEquals(10L, arr.size)
    }

    @Test
    fun `array equality is structural`() {
        assertEquals(Type.Array(Type.I8, 256), Type.Array(Type.I8, 256))
        assertNotEquals(Type.Array(Type.I8, 256), Type.Array(Type.I8, 128))
        assertNotEquals(Type.Array(Type.I8, 256), Type.Array(Type.I16, 256))
    }

    // --- Vector type ---

    @Test
    fun `vector type has element and lanes`() {
        val vec = Type.Vector(Type.F32, 4)
        assertEquals(Type.F32, vec.element)
        assertEquals(4, vec.lanes)
        assertFalse(vec.scalable)
    }

    @Test
    fun `scalable vector`() {
        val vec = Type.Vector(Type.I32, 4, scalable = true)
        assertTrue(vec.scalable)
    }

    // --- Struct type ---

    @Test
    fun `named struct with fields`() {
        val s = Type.Struct("Point", listOf(Type.F64, Type.F64))
        assertEquals("Point", s.name)
        assertEquals(2, s.fields.size)
        assertFalse(s.packed)
    }

    @Test
    fun `packed struct`() {
        val s = Type.Struct("Packed", listOf(Type.I8, Type.I32), packed = true)
        assertTrue(s.packed)
    }

    @Test
    fun `anonymous struct`() {
        val s = Type.Struct(null, listOf(Type.I32, Type.I64))
        assertNull(s.name)
    }

    @Test
    fun `opaque struct`() {
        val s = Type.OpaqueStruct("Forward")
        assertEquals("Forward", s.name)
    }

    // --- Union types ---

    @Test
    fun `union with variants`() {
        val u = Type.Union("MyUnion", listOf(Type.I32, Type.F32, Type.I64))
        assertEquals("MyUnion", u.name)
        assertEquals(3, u.variants.size)
    }

    @Test
    fun `tagged union with variants`() {
        val tu = Type.TaggedUnion(
            "Result",
            Type.I8,
            listOf(
                TaggedVariant("Ok", 0, listOf(Type.I32)),
                TaggedVariant("Err", 1, listOf(Type.ClassRef("Error"))),
            )
        )
        assertEquals("Result", tu.name)
        assertEquals(Type.I8, tu.tagType)
        assertEquals(2, tu.variants.size)
        assertEquals("Ok", tu.variants[0].name)
        assertEquals(0L, tu.variants[0].tag)
    }

    // --- Function type ---

    @Test
    fun `function type with params and return`() {
        val fn = Type.Function(listOf(Type.I32, Type.I32), Type.I64)
        assertEquals(2, fn.params.size)
        assertEquals(Type.I64, fn.ret)
        assertFalse(fn.vararg)
    }

    @Test
    fun `vararg function type`() {
        val fn = Type.Function(listOf(Type.Pointer(Type.I8)), Type.I32, vararg = true)
        assertTrue(fn.vararg)
    }

    @Test
    fun `void function type`() {
        val fn = Type.Function(emptyList(), Type.Void)
        assertTrue(fn.params.isEmpty())
        assertEquals(Type.Void, fn.ret)
    }

    @Test
    fun `function type equality`() {
        val fn1 = Type.Function(listOf(Type.I32), Type.I64)
        val fn2 = Type.Function(listOf(Type.I32), Type.I64)
        val fn3 = Type.Function(listOf(Type.I32), Type.I32)
        assertEquals(fn1, fn2)
        assertNotEquals(fn1, fn3)
    }

    // --- ClassRef and InterfaceRef ---

    @Test
    fun `class ref by name`() {
        val ref = Type.ClassRef("java.lang.String")
        assertEquals("java.lang.String", ref.name)
    }

    @Test
    fun `interface ref by name`() {
        val ref = Type.InterfaceRef("Comparable")
        assertEquals("Comparable", ref.name)
    }

    @Test
    fun `class ref equality`() {
        assertEquals(Type.ClassRef("Foo"), Type.ClassRef("Foo"))
        assertNotEquals(Type.ClassRef("Foo"), Type.ClassRef("Bar"))
    }

    // --- Generics ---

    @Test
    fun `type param with bounds`() {
        val tp = Type.TypeParam("T", 0, listOf(Type.ClassRef("Comparable")))
        assertEquals("T", tp.name)
        assertEquals(0, tp.index)
        assertEquals(1, tp.bounds.size)
    }

    @Test
    fun `parameterized type`() {
        val p = Type.Parameterized(Type.ClassRef("List"), listOf(Type.I32))
        assertEquals(Type.ClassRef("List"), p.base)
        assertEquals(listOf(Type.I32), p.typeArgs)
    }

    // --- Nullable and PlatformType ---

    @Test
    fun `nullable wraps inner type`() {
        val n = Type.Nullable(Type.I32)
        assertEquals(Type.I32, n.inner)
    }

    @Test
    fun `platform type with properties`() {
        val pt = Type.PlatformType("CLR.ValueType", mapOf("namespace" to "System"))
        assertEquals("CLR.ValueType", pt.name)
        assertEquals("System", pt.properties["namespace"])
    }

    // --- Companion factory methods ---

    @Test
    fun `companion factories produce correct types`() {
        assertEquals(Type.Pointer(Type.I32), Type.pointer(Type.I32))
        assertEquals(Type.OpaquePointer, Type.opaquePointer())
        assertEquals(Type.Array(Type.I8, 100), Type.array(Type.I8, 100))
        assertEquals(Type.Function(listOf(Type.I32), Type.Void), Type.function(listOf(Type.I32), Type.Void))
        assertEquals(Type.ClassRef("X"), Type.classRef("X"))
        assertEquals(Type.InterfaceRef("Y"), Type.interfaceRef("Y"))
        assertEquals(Type.Nullable(Type.F32), Type.nullable(Type.F32))
    }

    // --- Constant creation ---

    @Test
    fun `constant i1 true and false`() {
        val t = Constant.I1(true)
        val f = Constant.I1(false)
        assertEquals(Type.I1, t.type)
        assertEquals(Type.I1, f.type)
        assertTrue(t.value)
        assertFalse(f.value)
        assertEquals("1", t.name)
        assertEquals("0", f.name)
    }

    @Test
    fun `constant i8`() {
        val c = Constant.I8(42)
        assertEquals(Type.I8, c.type)
        assertEquals(42.toByte(), c.value)
    }

    @Test
    fun `constant i32`() {
        val c = Constant.I32(12345)
        assertEquals(Type.I32, c.type)
        assertEquals(12345, c.value)
        assertEquals("12345", c.name)
    }

    @Test
    fun `constant i64`() {
        val c = Constant.I64(Long.MAX_VALUE)
        assertEquals(Type.I64, c.type)
        assertEquals(Long.MAX_VALUE, c.value)
    }

    @Test
    fun `constant f32`() {
        val c = Constant.F32(3.14f)
        assertEquals(Type.F32, c.type)
        assertEquals(3.14f, c.value)
    }

    @Test
    fun `constant f64`() {
        val c = Constant.F64(2.718281828)
        assertEquals(Type.F64, c.type)
        assertEquals(2.718281828, c.value)
    }

    @Test
    fun `constant null pointer`() {
        val c = Constant.NullPtr
        assertEquals(Type.OpaquePointer, c.type)
        assertEquals("null", c.name)
    }

    @Test
    fun `constant null ref`() {
        val c = Constant.NullRef
        val refType = c.type as Type.Reference
        assertEquals(Type.Void, refType.referent)
        assertTrue(refType.nullable)
    }

    @Test
    fun `constant string null terminated`() {
        val c = Constant.StringConst("hello")
        val arrType = c.type as Type.Array
        assertEquals(Type.I8, arrType.element)
        assertEquals(6L, arrType.size) // 5 chars + null terminator
        assertTrue(c.nullTerminated)
    }

    @Test
    fun `constant string not null terminated`() {
        val c = Constant.StringConst("hello", nullTerminated = false)
        val arrType = c.type as Type.Array
        assertEquals(5L, arrType.size)
        assertFalse(c.nullTerminated)
    }

    @Test
    fun `constant zero initializer`() {
        val c = Constant.ZeroInitializer(Type.I32)
        assertEquals(Type.I32, c.type)
        assertEquals("zeroinitializer", c.name)
    }

    @Test
    fun `constant undef`() {
        val c = Constant.Undef(Type.F64)
        assertEquals(Type.F64, c.type)
        assertEquals("undef", c.name)
    }

    @Test
    fun `constant poison`() {
        val c = Constant.Poison(Type.I32)
        assertEquals(Type.I32, c.type)
        assertEquals("poison", c.name)
    }

    @Test
    fun `companion constant factories`() {
        assertEquals(Constant.I1(true), Type.i1(true))
        assertEquals(Constant.I32(42), Type.i32(42))
        assertEquals(Constant.I64(100L), Type.i64(100L))
        assertEquals(Constant.F32(1.0f), Type.f32(1.0f))
        assertEquals(Constant.F64(2.0), Type.f64(2.0))
        assertEquals(Constant.NullPtr, Type.nullPtr())
        assertEquals(Constant.NullRef, Type.nullRef())
    }

    @Test
    fun `constant IntN with custom bit width`() {
        val c = Constant.IntN(255, 24)
        assertEquals(Type.IntN(24), c.type)
        assertEquals(255L, c.value)
        assertEquals(24, c.bits)
    }

    // --- ClassDef ---

    @Test
    fun `class def with fields and methods`() {
        val cls = ClassDef(
            name = "Point",
            fields = listOf(
                FieldDef("x", Type.F64),
                FieldDef("y", Type.F64),
            ),
            methods = listOf(
                MethodDef("distance", listOf(Param("other", Type.ClassRef("Point"))), Type.F64),
            ),
        )
        assertEquals("Point", cls.name)
        assertEquals(2, cls.fields.size)
        assertEquals(1, cls.methods.size)
        assertNull(cls.superClass)
        assertFalse(cls.isAbstract)
        assertFalse(cls.isFinal)
    }

    @Test
    fun `class def with inheritance`() {
        val cls = ClassDef(
            name = "Dog",
            superClass = "Animal",
            interfaces = listOf("Runnable", "Comparable"),
        )
        assertEquals("Animal", cls.superClass)
        assertEquals(2, cls.interfaces.size)
    }

    @Test
    fun `class def builder DSL`() {
        val cls = classDef("Vehicle") {
            extends("Machine")
            implements("Driveable")
            isAbstract()
            field("speed", Type.F32)
            field("name", Type.ClassRef("String"), visibility = MemberVisibility.PUBLIC)
        }
        assertEquals("Vehicle", cls.name)
        assertEquals("Machine", cls.superClass)
        assertTrue(cls.isAbstract)
        assertEquals(2, cls.fields.size)
        assertEquals(listOf("Driveable"), cls.interfaces)
    }

    // --- InterfaceDef ---

    @Test
    fun `interface def with methods`() {
        val iface = InterfaceDef(
            name = "Drawable",
            methods = listOf(
                MethodDef("draw", emptyList(), Type.Void, isAbstract = true),
            ),
        )
        assertEquals("Drawable", iface.name)
        assertEquals(1, iface.methods.size)
    }

    @Test
    fun `interface def builder DSL`() {
        val iface = interfaceDef("Serializable") {
            extends("Writable")
            method(MethodDef("serialize", emptyList(), Type.Array(Type.I8, 0), isAbstract = true))
        }
        assertEquals("Serializable", iface.name)
        assertEquals(listOf("Writable"), iface.superInterfaces)
        assertEquals(1, iface.methods.size)
    }

    // --- StructDef ---

    @Test
    fun `struct def basic`() {
        val s = StructDef(
            name = "Vec3",
            fields = listOf(Param("x", Type.F32), Param("y", Type.F32), Param("z", Type.F32)),
        )
        assertEquals("Vec3", s.name)
        assertEquals(3, s.fields.size)
        assertFalse(s.packed)
        assertNull(s.align)
    }

    @Test
    fun `struct def packed with alignment`() {
        val s = StructDef(
            name = "Header",
            fields = listOf(Param("magic", Type.I32), Param("version", Type.I16)),
            packed = true,
            align = 1,
        )
        assertTrue(s.packed)
        assertEquals(1, s.align)
    }

    // --- EnumDef ---

    @Test
    fun `enum def with variants`() {
        val e = EnumDef(
            name = "Color",
            variants = listOf(
                EnumVariant("RED", 0),
                EnumVariant("GREEN", 1),
                EnumVariant("BLUE", 2),
            ),
        )
        assertEquals("Color", e.name)
        assertEquals(3, e.variants.size)
        assertEquals("GREEN", e.variants[1].name)
        assertEquals(1, e.variants[1].ordinal)
    }

    @Test
    fun `enum variant with fields`() {
        val v = EnumVariant("Some", 0, fields = listOf(Param("value", Type.I32)))
        assertEquals(1, v.fields.size)
        assertEquals("value", v.fields[0].name)
    }

    // --- Type hashing ---

    @Test
    fun `type hashCode consistency`() {
        val types = listOf(
            Type.I32, Type.F64, Type.Void, Type.OpaquePointer,
            Type.Pointer(Type.I8), Type.Array(Type.I32, 10),
            Type.Function(listOf(Type.I32), Type.Void),
            Type.Struct("S", listOf(Type.I32)),
        )
        for (t in types) {
            assertEquals(t.hashCode(), t.hashCode(), "hashCode must be consistent for $t")
        }
    }

    @Test
    fun `equal types have equal hashCodes`() {
        val pairs = listOf(
            Type.Pointer(Type.I32) to Type.Pointer(Type.I32),
            Type.Array(Type.F64, 5) to Type.Array(Type.F64, 5),
            Type.Function(listOf(Type.I8), Type.I32) to Type.Function(listOf(Type.I8), Type.I32),
        )
        for ((a, b) in pairs) {
            assertEquals(a, b)
            assertEquals(a.hashCode(), b.hashCode())
        }
    }
}
