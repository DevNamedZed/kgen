package org.kgen.reflect

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class TypeInfoTest {

    @Test
    fun basicIdentity() {
        val type = TypeInfo.builder("System.String")
            .kind(TypeKind.CLASS)
            .addFlag(TypeFlag.PUBLIC)
            .addFlag(TypeFlag.SEALED)
            .build()

        assertEquals("String", type.name())
        assertEquals("System", type.namespace())
        assertEquals("System.String", type.fullName())
        assertEquals("System.String", type.toString())
    }

    @Test
    fun classification() {
        val cls = TypeInfo.builder("MyClass").kind(TypeKind.CLASS).build()
        assertTrue(cls.isClass())
        assertFalse(cls.isInterface())
        assertFalse(cls.isEnum())

        val iface = TypeInfo.builder("IDisposable").kind(TypeKind.INTERFACE).build()
        assertTrue(iface.isInterface())
        assertFalse(iface.isClass())

        val enum = TypeInfo.builder("Color").kind(TypeKind.ENUM).build()
        assertTrue(enum.isEnum())

        val struct = TypeInfo.builder("Point").kind(TypeKind.STRUCT).build()
        assertTrue(struct.isStruct())
    }

    @Test
    fun flags() {
        val type = TypeInfo.builder("Test")
            .addFlag(TypeFlag.PUBLIC)
            .addFlag(TypeFlag.ABSTRACT)
            .addFlag(TypeFlag.SEALED)
            .build()

        assertTrue(type.isPublic())
        assertTrue(type.isAbstract())
        assertTrue(type.isSealed())
        assertFalse(type.isInternal())
    }

    @Test
    fun hierarchy() {
        val baseType = TypeInfo.builder("System.Object").build()
        val iface = TypeInfo.builder("System.IComparable").kind(TypeKind.INTERFACE).build()
        val derived = TypeInfo.builder("System.String")
            .baseType(baseType)
            .addInterface(iface)
            .build()

        assertEquals(baseType, derived.baseType())
        assertEquals(1, derived.interfaces().size)
        assertEquals(iface, derived.interfaces()[0])
    }

    @Test
    fun nestedTypes() {
        val inner = TypeInfo.builder("InnerClass").build()
        val outer = TypeInfo.builder("OuterClass")
            .addNestedType(inner)
            .build()

        assertEquals(1, outer.nestedTypes().size)
        assertEquals(inner, outer.nestedTypes()[0])
    }

    @Test
    fun genericType() {
        val type = TypeInfo.builder("System.Collections.Generic.List`1")
            .addGenericArg(TypeRef.genericParam("T"))
            .build()

        assertTrue(type.isGeneric())
        assertEquals(1, type.genericArguments().size)
    }

    @Test
    fun methods() {
        val method = MethodInfo("Contains", returnType = TypeRef.BOOL, params = listOf(
            ParameterInfo("value", TypeRef.of("System.Object"), 0),
        ), flags = setOf(MethodFlag.PUBLIC, MethodFlag.VIRTUAL))

        val type = TypeInfo.builder("System.String")
            .addMethod(method)
            .build()

        assertEquals(1, type.methods().size)
        assertEquals(method, type.method("Contains"))
        assertNull(type.method("Missing"))
    }

    @Test
    fun fields() {
        val field = FieldInfo("Length", TypeRef.I32, flags = setOf(FieldFlag.PUBLIC, FieldFlag.READONLY))

        val type = TypeInfo.builder("System.String")
            .addField(field)
            .build()

        assertEquals(1, type.fields().size)
        assertEquals(field, type.field("Length"))
        assertNull(type.field("Missing"))
    }

    @Test
    fun properties() {
        val getter = MethodInfo("get_Count", returnType = TypeRef.I32)
        val prop = PropertyInfo("Count", TypeRef.I32, getter = getter)

        val type = TypeInfo.builder("System.Collections.Generic.List`1")
            .addProperty(prop)
            .build()

        assertEquals(1, type.properties().size)
        assertEquals(prop, type.property("Count"))
        assertTrue(type.property("Count")!!.isReadOnly())
    }

    @Test
    fun events() {
        val add = MethodInfo("add_Click")
        val remove = MethodInfo("remove_Click")
        val event = EventInfo("Click", TypeRef.of("System.EventHandler"), addMethod = add, removeMethod = remove)

        val type = TypeInfo.builder("Button")
            .addEvent(event)
            .build()

        assertEquals(1, type.events().size)
        assertEquals(event, type.event("Click"))
    }

    @Test
    fun constructors() {
        val ctor = MethodInfo(".ctor", flags = setOf(MethodFlag.PUBLIC, MethodFlag.CONSTRUCTOR))
        val type = TypeInfo.builder("MyClass")
            .addConstructor(ctor)
            .build()

        assertEquals(1, type.constructors().size)
        assertTrue(type.constructors()[0].isConstructor())
    }

    @Test
    fun attributes() {
        val attr = AttributeInfo("Obsolete", namedArgs = mapOf("Message" to "Use NewClass instead"))
        val type = TypeInfo.builder("OldClass")
            .addAttribute(attr)
            .build()

        assertEquals(1, type.attributes().size)
        assertEquals(attr, type.attribute("Obsolete"))
        assertNull(type.attribute("Missing"))
    }

    @Test
    fun layout() {
        val type = TypeInfo.builder("Point")
            .kind(TypeKind.STRUCT)
            .size(8)
            .packingSize(4)
            .build()

        assertEquals(8, type.size())
        assertEquals(4, type.packingSize())
    }

    @Test
    fun toTypeRef() {
        val type = TypeInfo.builder("System.String").build()
        val ref = type.toTypeRef()
        assertEquals("System.String", ref.fullName())
    }

    @Test
    fun equality() {
        val a = TypeInfo.builder("System.String").build()
        val b = TypeInfo.builder("System.String").build()
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())

        val c = TypeInfo.builder("System.Int32").build()
        assertNotEquals(a, c)
    }
}

class MethodInfoTest {

    @Test
    fun basicMethod() {
        val method = MethodInfo("Add",
            returnType = TypeRef.I32,
            params = listOf(
                ParameterInfo("a", TypeRef.I32, 0),
                ParameterInfo("b", TypeRef.I32, 1),
            ),
            flags = setOf(MethodFlag.PUBLIC, MethodFlag.STATIC),
        )

        assertEquals("Add", method.name())
        assertEquals(TypeRef.I32, method.returnType())
        assertEquals(2, method.parameterCount())
        assertTrue(method.isPublic())
        assertTrue(method.isStatic())
        assertFalse(method.isVirtual())
        assertFalse(method.isAbstract())
    }

    @Test
    fun signature() {
        val method = MethodInfo("Add",
            returnType = TypeRef.I64,
            params = listOf(
                ParameterInfo("a", TypeRef.I64, 0),
                ParameterInfo("b", TypeRef.I64, 1),
            ),
        )
        val sig = method.signature()
        assertEquals(TypeRef.I64, sig.returnType())
        assertEquals(2, sig.parameterCount())
        assertEquals("a", sig.parameters()[0].name)
    }

    @Test
    fun flags() {
        val method = MethodInfo("Dispose",
            flags = setOf(MethodFlag.PUBLIC, MethodFlag.VIRTUAL, MethodFlag.FINAL),
        )
        assertTrue(method.isPublic())
        assertTrue(method.isVirtual())
        assertTrue(method.isFinal())
        assertFalse(method.isAbstract())
        assertFalse(method.isNative())
    }

    @Test
    fun constructor() {
        val ctor = MethodInfo(".ctor",
            flags = setOf(MethodFlag.PUBLIC, MethodFlag.CONSTRUCTOR),
            params = listOf(ParameterInfo("value", TypeRef.I32, 0)),
        )
        assertTrue(ctor.isConstructor())
        assertEquals(1, ctor.parameterCount())
    }

    @Test
    fun genericMethod() {
        val method = MethodInfo("Cast",
            genericArgs = listOf(TypeRef.genericParam("T")),
        )
        assertTrue(method.isGeneric())
        assertEquals(1, method.genericArguments().size)
    }

    @Test
    fun bodyIl() {
        val il = byteArrayOf(0x02, 0x03, 0x58, 0x2A) // ldarg.0, ldarg.1, add, ret
        val method = MethodInfo("Add", ilBytes = il)
        assertTrue(method.hasBody())
        assertNotNull(method.il())
        assertArrayEquals(il, method.il())
        assertNull(method.bytecode())
    }

    @Test
    fun bodyBytecode() {
        val bc = byteArrayOf(0x1A, 0x1B, 0x60.toByte(), 0xAC.toByte())
        val method = MethodInfo("add", bytecodeBytes = bc)
        assertTrue(method.hasBody())
        assertNotNull(method.bytecode())
        assertNull(method.il())
    }

    @Test
    fun noBody() {
        val method = MethodInfo("abstractMethod", flags = setOf(MethodFlag.ABSTRACT))
        assertFalse(method.hasBody())
        assertNull(method.il())
        assertNull(method.bytecode())
    }

    @Test
    fun attributes() {
        val attr = AttributeInfo("Obsolete")
        val method = MethodInfo("old", attrs = listOf(attr))
        assertEquals(1, method.attributes().size)
    }

    @Test
    fun toStringFormat() {
        val method = MethodInfo("Add",
            returnType = TypeRef.I32,
            params = listOf(
                ParameterInfo("a", TypeRef.I32, 0),
                ParameterInfo("b", TypeRef.I32, 1),
            ),
        )
        assertEquals("Add(a: i32, b: i32): i32", method.toString())
    }
}

class FieldInfoTest {

    @Test
    fun basicField() {
        val field = FieldInfo("x", TypeRef.F64, flags = setOf(FieldFlag.PUBLIC))
        assertEquals("x", field.name())
        assertEquals(TypeRef.F64, field.fieldType())
        assertTrue(field.isPublic())
        assertFalse(field.isStatic())
        assertFalse(field.isReadOnly())
    }

    @Test
    fun constField() {
        val field = FieldInfo("MAX", TypeRef.I32,
            flags = setOf(FieldFlag.PUBLIC, FieldFlag.STATIC, FieldFlag.CONST),
            constantVal = 2147483647,
        )
        assertTrue(field.isConst())
        assertTrue(field.isStatic())
        assertEquals(2147483647, field.constantValue())
    }

    @Test
    fun readOnlyField() {
        val field = FieldInfo("Length", TypeRef.I32, flags = setOf(FieldFlag.PUBLIC, FieldFlag.READONLY))
        assertTrue(field.isReadOnly())
        assertFalse(field.isConst())
    }

    @Test
    fun offset() {
        val field = FieldInfo("x", TypeRef.F32, byteOffset = 8)
        assertEquals(8, field.offset())
    }

    @Test
    fun toStringFormat() {
        val field = FieldInfo("count", TypeRef.I32)
        assertEquals("count: i32", field.toString())
    }
}

class ParameterInfoTest {

    @Test
    fun basicParam() {
        val p = ParameterInfo("value", TypeRef.I32, 0)
        assertEquals("value", p.name())
        assertEquals(TypeRef.I32, p.type())
        assertEquals(0, p.position())
        assertFalse(p.isOptional())
        assertFalse(p.isOut())
        assertFalse(p.isRef())
    }

    @Test
    fun optionalWithDefault() {
        val p = ParameterInfo("count", TypeRef.I32, 0,
            flags = setOf(ParameterFlag.OPTIONAL),
            defaultVal = 10,
        )
        assertTrue(p.isOptional())
        assertEquals(10, p.defaultValue())
    }

    @Test
    fun outParam() {
        val p = ParameterInfo("result", TypeRef.byRef(TypeRef.I32), 0,
            flags = setOf(ParameterFlag.OUT),
        )
        assertTrue(p.isOut())
    }

    @Test
    fun refParam() {
        val p = ParameterInfo("value", TypeRef.byRef(TypeRef.I32), 0,
            flags = setOf(ParameterFlag.REF),
        )
        assertTrue(p.isRef())
    }

    @Test
    fun toStringNamed() {
        val p = ParameterInfo("x", TypeRef.F64, 0)
        assertEquals("x: f64", p.toString())
    }

    @Test
    fun toStringUnnamed() {
        val p = ParameterInfo(null, TypeRef.I32, 0)
        assertEquals("i32", p.toString())
    }
}

class PropertyInfoTest {

    @Test
    fun readWriteProperty() {
        val getter = MethodInfo("get_Name", returnType = TypeRef.of("System.String"))
        val setter = MethodInfo("set_Name", params = listOf(
            ParameterInfo("value", TypeRef.of("System.String"), 0),
        ))
        val prop = PropertyInfo("Name", TypeRef.of("System.String"), getter = getter, setter = setter)

        assertEquals("Name", prop.name())
        assertNotNull(prop.getter())
        assertNotNull(prop.setter())
        assertFalse(prop.isReadOnly())
    }

    @Test
    fun readOnlyProperty() {
        val getter = MethodInfo("get_Count", returnType = TypeRef.I32)
        val prop = PropertyInfo("Count", TypeRef.I32, getter = getter)

        assertTrue(prop.isReadOnly())
        assertNull(prop.setter())
    }

    @Test
    fun toStringFormat() {
        val prop = PropertyInfo("Count", TypeRef.I32)
        assertEquals("Count: i32", prop.toString())
    }
}

class EventInfoTest {

    @Test
    fun basicEvent() {
        val add = MethodInfo("add_Click")
        val remove = MethodInfo("remove_Click")
        val event = EventInfo("Click", TypeRef.of("System.EventHandler"),
            addMethod = add, removeMethod = remove)

        assertEquals("Click", event.name())
        assertEquals("System.EventHandler", event.eventType().fullName())
        assertNotNull(event.addMethod())
        assertNotNull(event.removeMethod())
        assertNull(event.raiseMethod())
    }

    @Test
    fun toStringFormat() {
        val event = EventInfo("Click", TypeRef.of("System.EventHandler"))
        assertEquals("event Click: System.EventHandler", event.toString())
    }
}

class AttributeInfoTest {

    @Test
    fun basicAttribute() {
        val attr = AttributeInfo("Obsolete")
        assertEquals("Obsolete", attr.name())
        assertTrue(attr.constructorArguments().isEmpty())
        assertTrue(attr.arguments().isEmpty())
    }

    @Test
    fun withConstructorArgs() {
        val attr = AttributeInfo("DllImport", ctorArgs = listOf("kernel32.dll"))
        assertEquals(1, attr.constructorArguments().size)
        assertEquals("kernel32.dll", attr.constructorArguments()[0])
    }

    @Test
    fun withNamedArgs() {
        val attr = AttributeInfo("Obsolete",
            namedArgs = mapOf("Message" to "Use NewMethod", "IsError" to true))
        assertEquals("Use NewMethod", attr.arguments()["Message"])
        assertEquals(true, attr.arguments()["IsError"])
    }

    @Test
    fun toStringFormat() {
        val attr = AttributeInfo("Serializable")
        assertEquals("[Serializable]", attr.toString())
    }
}
