package org.kgen.reflect

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class TypeInfoExtendedTest {

    // --- TypeKind coverage ---

    @Test
    fun typeKindDelegate() {
        val d = TypeInfo.builder("MyDelegate").kind(TypeKind.DELEGATE).build()
        assertTrue(d.isDelegate())
        assertFalse(d.isClass())
    }

    @Test
    fun typeKindAnnotation() {
        val a = TypeInfo.builder("MyAnnotation").kind(TypeKind.ANNOTATION).build()
        assertFalse(a.isClass())
        assertFalse(a.isInterface())
    }

    // --- TypeFlag coverage ---

    @Test
    fun typeIsInternal() {
        val t = TypeInfo.builder("InternalType").addFlag(TypeFlag.INTERNAL).build()
        assertTrue(t.isInternal())
        assertFalse(t.isPublic())
    }

    @Test
    fun typeIsStatic() {
        val t = TypeInfo.builder("StaticClass")
            .addFlag(TypeFlag.STATIC)
            .addFlag(TypeFlag.SEALED)
            .addFlag(TypeFlag.ABSTRACT)
            .build()
        assertTrue(t.isSealed())
        assertTrue(t.isAbstract())
    }

    @Test
    fun noFlags() {
        val t = TypeInfo.builder("Plain").build()
        assertFalse(t.isPublic())
        assertFalse(t.isAbstract())
        assertFalse(t.isSealed())
        assertFalse(t.isInternal())
    }

    // --- Multiple methods ---

    @Test
    fun multipleMethodsLookup() {
        val m1 = MethodInfo("Add", returnType = TypeRef.I32,
            params = listOf(ParameterInfo("a", TypeRef.I32, 0), ParameterInfo("b", TypeRef.I32, 1)))
        val m2 = MethodInfo("Sub", returnType = TypeRef.I32,
            params = listOf(ParameterInfo("a", TypeRef.I32, 0), ParameterInfo("b", TypeRef.I32, 1)))
        val m3 = MethodInfo("Mul", returnType = TypeRef.I64)

        val type = TypeInfo.builder("Calculator")
            .addMethod(m1).addMethod(m2).addMethod(m3)
            .build()

        assertEquals(3, type.methods().size)
        assertEquals(m1, type.method("Add"))
        assertEquals(m2, type.method("Sub"))
        assertEquals(m3, type.method("Mul"))
        assertNull(type.method("Div"))
    }

    // --- Multiple fields ---

    @Test
    fun multipleFieldsLookup() {
        val f1 = FieldInfo("x", TypeRef.F64, flags = setOf(FieldFlag.PUBLIC))
        val f2 = FieldInfo("y", TypeRef.F64, flags = setOf(FieldFlag.PUBLIC))
        val f3 = FieldInfo("z", TypeRef.F64, flags = setOf(FieldFlag.PUBLIC))

        val type = TypeInfo.builder("Vec3")
            .kind(TypeKind.STRUCT)
            .addField(f1).addField(f2).addField(f3)
            .build()

        assertEquals(3, type.fields().size)
        assertEquals(f1, type.field("x"))
        assertEquals(f2, type.field("y"))
        assertEquals(f3, type.field("z"))
    }

    // --- Multiple interfaces ---

    @Test
    fun multipleInterfaces() {
        val i1 = TypeInfo.builder("IComparable").kind(TypeKind.INTERFACE).build()
        val i2 = TypeInfo.builder("IEquatable").kind(TypeKind.INTERFACE).build()
        val i3 = TypeInfo.builder("ICloneable").kind(TypeKind.INTERFACE).build()

        val type = TypeInfo.builder("MyType")
            .addInterface(i1).addInterface(i2).addInterface(i3)
            .build()

        assertEquals(3, type.interfaces().size)
    }

    // --- Generic types ---

    @Test
    fun genericWithMultipleArgs() {
        val type = TypeInfo.builder("System.Collections.Generic.Dictionary`2")
            .addGenericArg(TypeRef.genericParam("TKey"))
            .addGenericArg(TypeRef.genericParam("TValue"))
            .build()

        assertTrue(type.isGeneric())
        assertEquals(2, type.genericArguments().size)
    }

    @Test
    fun nonGenericType() {
        val type = TypeInfo.builder("System.String").build()
        assertFalse(type.isGeneric())
        assertEquals(0, type.genericArguments().size)
    }

    // --- Multiple nested types ---

    @Test
    fun multipleNestedTypes() {
        val inner1 = TypeInfo.builder("KeyCollection").build()
        val inner2 = TypeInfo.builder("ValueCollection").build()
        val inner3 = TypeInfo.builder("Enumerator").kind(TypeKind.STRUCT).build()

        val type = TypeInfo.builder("Dictionary")
            .addNestedType(inner1).addNestedType(inner2).addNestedType(inner3)
            .build()

        assertEquals(3, type.nestedTypes().size)
    }

    // --- Multiple properties ---

    @Test
    fun multipleProperties() {
        val getter1 = MethodInfo("get_Count", returnType = TypeRef.I32)
        val getter2 = MethodInfo("get_Capacity", returnType = TypeRef.I32)
        val p1 = PropertyInfo("Count", TypeRef.I32, getter = getter1)
        val p2 = PropertyInfo("Capacity", TypeRef.I32, getter = getter2)

        val type = TypeInfo.builder("List")
            .addProperty(p1).addProperty(p2)
            .build()

        assertEquals(2, type.properties().size)
        assertEquals(p1, type.property("Count"))
        assertEquals(p2, type.property("Capacity"))
        assertNull(type.property("Missing"))
    }

    // --- Multiple events ---

    @Test
    fun multipleEvents() {
        val e1 = EventInfo("Click", TypeRef.of("System.EventHandler"))
        val e2 = EventInfo("Load", TypeRef.of("System.EventHandler"))

        val type = TypeInfo.builder("Control")
            .addEvent(e1).addEvent(e2)
            .build()

        assertEquals(2, type.events().size)
        assertEquals(e1, type.event("Click"))
        assertEquals(e2, type.event("Load"))
    }

    // --- Multiple constructors ---

    @Test
    fun multipleConstructors() {
        val ctor1 = MethodInfo(".ctor", flags = setOf(MethodFlag.PUBLIC, MethodFlag.CONSTRUCTOR))
        val ctor2 = MethodInfo(".ctor",
            flags = setOf(MethodFlag.PUBLIC, MethodFlag.CONSTRUCTOR),
            params = listOf(ParameterInfo("value", TypeRef.I32, 0)))

        val type = TypeInfo.builder("MyClass")
            .addConstructor(ctor1).addConstructor(ctor2)
            .build()

        assertEquals(2, type.constructors().size)
    }

    // --- Multiple attributes ---

    @Test
    fun multipleAttributes() {
        val a1 = AttributeInfo("Serializable")
        val a2 = AttributeInfo("Obsolete", namedArgs = mapOf("Message" to "deprecated"))

        val type = TypeInfo.builder("OldClass")
            .addAttribute(a1).addAttribute(a2)
            .build()

        assertEquals(2, type.attributes().size)
        assertEquals(a1, type.attribute("Serializable"))
        assertEquals(a2, type.attribute("Obsolete"))
    }

    // --- Layout / size ---

    @Test
    fun defaultSizeZero() {
        val type = TypeInfo.builder("SomeClass").build()
        assertEquals(0, type.size())
        assertEquals(0, type.packingSize())
    }

    @Test
    fun structWithLayout() {
        val type = TypeInfo.builder("LargeStruct")
            .kind(TypeKind.STRUCT)
            .size(256)
            .packingSize(8)
            .build()
        assertEquals(256, type.size())
        assertEquals(8, type.packingSize())
    }

    // --- toTypeRef ---

    @Test
    fun toTypeRefPreservesFullName() {
        val type = TypeInfo.builder("System.Collections.Generic.List`1").build()
        val ref = type.toTypeRef()
        assertEquals("System.Collections.Generic.List`1", ref.fullName())
    }

    // --- Equality ---

    @Test
    fun equalityByName() {
        val a = TypeInfo.builder("System.String").build()
        val b = TypeInfo.builder("System.String").build()
        assertEquals(a, b)
    }

    @Test
    fun inequalityDifferentName() {
        assertNotEquals(
            TypeInfo.builder("System.String").build(),
            TypeInfo.builder("System.Int32").build()
        )
    }

    @Test
    fun hashCodeConsistent() {
        val a = TypeInfo.builder("System.String").build()
        assertEquals(a.hashCode(), a.hashCode())
    }

    // --- Complete type with everything ---

    @Test
    fun fullTypeInfo() {
        val baseType = TypeInfo.builder("System.Object").build()
        val iface = TypeInfo.builder("System.IDisposable").kind(TypeKind.INTERFACE).build()
        val ctor = MethodInfo(".ctor", flags = setOf(MethodFlag.PUBLIC, MethodFlag.CONSTRUCTOR))
        val method = MethodInfo("DoWork", returnType = TypeRef.VOID,
            flags = setOf(MethodFlag.PUBLIC, MethodFlag.VIRTUAL))
        val field = FieldInfo("_count", TypeRef.I32, flags = setOf(FieldFlag.PRIVATE))
        val getter = MethodInfo("get_Count", returnType = TypeRef.I32)
        val prop = PropertyInfo("Count", TypeRef.I32, getter = getter)
        val attr = AttributeInfo("Serializable")
        val nested = TypeInfo.builder("Inner").build()

        val type = TypeInfo.builder("System.MyType")
            .kind(TypeKind.CLASS)
            .addFlag(TypeFlag.PUBLIC)
            .baseType(baseType)
            .addInterface(iface)
            .addConstructor(ctor)
            .addMethod(method)
            .addField(field)
            .addProperty(prop)
            .addAttribute(attr)
            .addNestedType(nested)
            .size(16)
            .packingSize(4)
            .build()

        assertEquals("MyType", type.name())
        assertEquals("System", type.namespace())
        assertTrue(type.isClass())
        assertTrue(type.isPublic())
        assertEquals(baseType, type.baseType())
        assertEquals(1, type.interfaces().size)
        assertEquals(1, type.constructors().size)
        assertEquals(1, type.methods().size)
        assertEquals(1, type.fields().size)
        assertEquals(1, type.properties().size)
        assertEquals(1, type.attributes().size)
        assertEquals(1, type.nestedTypes().size)
        assertEquals(16, type.size())
        assertEquals(4, type.packingSize())
    }
}

class MethodInfoExtendedTest {

    @Test
    fun privateMethod() {
        val m = MethodInfo("helper", flags = setOf(MethodFlag.PRIVATE))
        assertTrue(m.isPrivate())
        assertFalse(m.isPublic())
    }

    @Test
    fun protectedMethod() {
        val m = MethodInfo("onInit", flags = setOf(MethodFlag.PROTECTED, MethodFlag.VIRTUAL))
        assertTrue(m.isProtected())
        assertTrue(m.isVirtual())
    }

    @Test
    fun nativeMethod() {
        val m = MethodInfo("nativeCall", flags = setOf(MethodFlag.NATIVE))
        assertTrue(m.isNative())
    }

    @Test
    fun noParams() {
        val m = MethodInfo("noArgs")
        assertEquals(0, m.parameterCount())
    }

    @Test
    fun manyParams() {
        val params = (0..9).map { ParameterInfo("p$it", TypeRef.I32, it) }
        val m = MethodInfo("manyArgs", params = params)
        assertEquals(10, m.parameterCount())
    }

    @Test
    fun defaultReturnType() {
        val m = MethodInfo("test")
        assertEquals(TypeRef.VOID, m.returnType())
    }

    @Test
    fun signatureMatchesMethod() {
        val m = MethodInfo("add",
            returnType = TypeRef.I32,
            params = listOf(
                ParameterInfo("a", TypeRef.I32, 0),
                ParameterInfo("b", TypeRef.I32, 1)))
        val sig = m.signature()
        assertEquals(TypeRef.I32, sig.returnType())
        assertEquals(2, sig.parameterCount())
    }
}

class FieldInfoExtendedTest {

    @Test
    fun privateField() {
        val f = FieldInfo("_x", TypeRef.F64, flags = setOf(FieldFlag.PRIVATE))
        assertTrue(f.isPrivate())
    }

    @Test
    fun protectedField() {
        val f = FieldInfo("value", TypeRef.I32, flags = setOf(FieldFlag.PROTECTED))
        assertTrue(f.isProtected())
    }

    @Test
    fun staticConstWithValue() {
        val f = FieldInfo("PI", TypeRef.F64,
            flags = setOf(FieldFlag.PUBLIC, FieldFlag.STATIC, FieldFlag.CONST),
            constantVal = 3.14159)
        assertTrue(f.isConst())
        assertTrue(f.isStatic())
        assertEquals(3.14159, f.constantValue())
    }

    @Test
    fun noConstValue() {
        val f = FieldInfo("x", TypeRef.I32)
        assertNull(f.constantValue())
    }

    @Test
    fun noOffset() {
        val f = FieldInfo("x", TypeRef.I32)
        assertEquals(-1, f.offset())
    }
}

class ParameterInfoExtendedTest {

    @Test
    fun paramsParam() {
        val p = ParameterInfo("args", TypeRef.arrayOf(TypeRef.of("System.Object")), 0,
            flags = setOf(ParameterFlag.PARAMS))
        assertTrue(p.isParams())
    }

    @Test
    fun multiplePositions() {
        val params = (0..4).map { ParameterInfo("p$it", TypeRef.I32, it) }
        for (i in params.indices) {
            assertEquals(i, params[i].position())
            assertEquals("p$i", params[i].name())
        }
    }
}

class AttributeInfoExtendedTest {

    @Test
    fun attributeWithBothArgs() {
        val attr = AttributeInfo("DllImport",
            ctorArgs = listOf("user32.dll"),
            namedArgs = mapOf("CharSet" to "Unicode", "SetLastError" to true))
        assertEquals(1, attr.constructorArguments().size)
        assertEquals("user32.dll", attr.constructorArguments()[0])
        assertEquals("Unicode", attr.arguments()["CharSet"])
        assertEquals(true, attr.arguments()["SetLastError"])
    }

    @Test
    fun attributeNoArgs() {
        val attr = AttributeInfo("TestFixture")
        assertTrue(attr.constructorArguments().isEmpty())
        assertTrue(attr.arguments().isEmpty())
    }
}
