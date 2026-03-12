package org.kgen.reflect

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.kgen.binary.pe.clr.ClrMetadata
import org.kgen.binary.pe.clr.ClrTableParser
import org.kgen.reflect.emit.DynamicMethod
import org.kgen.reflect.emit.ModuleBuilder
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ClrModuleBuilderTest {

    @Test
    fun emptyType() {
        val mod = ModuleBuilder.clr("TestAsm")
        mod.defineType("MyNamespace.MyClass", TypeFlag.PUBLIC)

        val parsed = parse(mod.toBytes())
        assertNotNull(parsed)
        assertEquals(2, parsed.tables.typeDefs.size)
        assertEquals("MyClass", parsed.strings.get(parsed.tables.typeDefs[1].name))
        assertEquals("MyNamespace", parsed.strings.get(parsed.tables.typeDefs[1].namespace))
    }

    @Test
    fun methodWithBody() {
        val mod = ModuleBuilder.clr("TestAsm")
        val type = mod.defineType("Math")

        val add = type.defineMethod("Add",
            Signature.of(TypeRef.I32, TypeRef.I32, TypeRef.I32),
            MethodFlag.PUBLIC, MethodFlag.STATIC)
        add.il().ldarg(0)
        add.il().ldarg(1)
        add.il().add()
        add.il().ret()

        val parsed = parse(mod.toBytes())
        assertNotNull(parsed)
        assertEquals(1, parsed.tables.methodDefs.size)
        assertEquals("Add", parsed.strings.get(parsed.tables.methodDefs[0].name))
    }

    @Test
    fun multipleMethods() {
        val mod = ModuleBuilder.clr("TestAsm")
        val type = mod.defineType("Math")

        type.defineMethod("Add",
            Signature.of(TypeRef.I32, TypeRef.I32, TypeRef.I32),
            MethodFlag.PUBLIC, MethodFlag.STATIC).apply {
            il().ldarg(0); il().ldarg(1); il().add(); il().ret()
        }
        type.defineMethod("Sub",
            Signature.of(TypeRef.I32, TypeRef.I32, TypeRef.I32),
            MethodFlag.PUBLIC, MethodFlag.STATIC).apply {
            il().ldarg(0); il().ldarg(1); il().sub(); il().ret()
        }

        val parsed = parse(mod.toBytes())
        assertNotNull(parsed)
        assertEquals(2, parsed.tables.methodDefs.size)
        assertEquals("Add", parsed.strings.get(parsed.tables.methodDefs[0].name))
        assertEquals("Sub", parsed.strings.get(parsed.tables.methodDefs[1].name))
    }

    @Test
    fun defineField() {
        val mod = ModuleBuilder.clr("TestAsm")
        val type = mod.defineType("Counter")

        val countToken = type.defineField("count", TypeRef.I32, FieldFlag.PRIVATE, FieldFlag.STATIC)
        assertTrue(countToken.isField)
        assertEquals(1, countToken.rowIndex)

        val parsed = parse(mod.toBytes())
        assertNotNull(parsed)
        assertEquals(1, parsed.tables.fields.size)
        assertEquals("count", parsed.strings.get(parsed.tables.fields[0].name))
    }

    @Test
    fun fieldTokenInInstructions() {
        val mod = ModuleBuilder.clr("TestAsm")
        val type = mod.defineType("Counter")

        val countField = type.defineField("count", TypeRef.I32, FieldFlag.PRIVATE, FieldFlag.STATIC)
        val inc = type.defineMethod("Increment",
            Signature.of(TypeRef.VOID),
            MethodFlag.PUBLIC, MethodFlag.STATIC)

        inc.il().ldsfld(countField)
        inc.il().ldcI4Auto(1)
        inc.il().add()
        inc.il().stsfld(countField)
        inc.il().ret()

        assertTrue(mod.toBytes().isNotEmpty())
    }

    @Test
    fun entryPoint() {
        val mod = ModuleBuilder.clr("TestAsm")
        val type = mod.defineType("Program")

        val main = type.defineMethod("Main",
            Signature.of(TypeRef.VOID),
            MethodFlag.PUBLIC, MethodFlag.STATIC)
        main.il().ret()
        mod.setEntryPoint(main)

        // Entry point token is in the CLR header, not in metadata bytes.
        // Verify the bytes are produced without error and the method exists.
        val parsed = parse(mod.toBytes())
        assertNotNull(parsed)
        assertEquals(1, parsed.tables.methodDefs.size)
        assertEquals("Main", parsed.strings.get(parsed.tables.methodDefs[0].name))
    }

    @Test
    fun methodToken() {
        val mod = ModuleBuilder.clr("TestAsm")
        val type = mod.defineType("Program")

        val helper = type.defineMethod("Helper",
            Signature.of(TypeRef.I32),
            MethodFlag.PUBLIC, MethodFlag.STATIC)
        helper.il().ldcI4Auto(42)
        helper.il().ret()

        val main = type.defineMethod("Main",
            Signature.of(TypeRef.I32),
            MethodFlag.PUBLIC, MethodFlag.STATIC)
        main.il().call(helper.token())
        main.il().ret()

        assertTrue(helper.token().isMethodDef)
        assertEquals(0x06000001, helper.token().value)
        assertTrue(mod.toBytes().isNotEmpty())
    }

    @Test
    fun labelsAndBranches() {
        val mod = ModuleBuilder.clr("TestAsm")
        val type = mod.defineType("Program")

        val abs = type.defineMethod("Abs",
            Signature.of(TypeRef.I32, TypeRef.I32),
            MethodFlag.PUBLIC, MethodFlag.STATIC)

        val il = abs.il()
        val positive = il.defineLabel()
        il.ldarg(0)
        il.ldcI4Auto(0)
        il.bge(positive)
        il.ldarg(0)
        il.neg()
        il.ret()
        il.markLabel(positive)
        il.ldarg(0)
        il.ret()

        assertTrue(mod.toBytes().isNotEmpty())
    }

    @Test
    fun memberRef() {
        val mod = ModuleBuilder.clr("TestAsm")
        val type = mod.defineType("Program")

        val ctorToken = mod.addMemberRef(1, ".ctor",
            Signature.returningVoid().build(), true)

        assertTrue(ctorToken.isMemberRef)
        assertEquals(0x0A000001, ctorToken.value)

        val method = type.defineMethod("Create",
            Signature.of(TypeRef.VOID),
            MethodFlag.PUBLIC, MethodFlag.STATIC)
        method.il().newobj(ctorToken)
        method.il().pop()
        method.il().ret()

        val parsed = parse(mod.toBytes())
        assertNotNull(parsed)
        assertEquals(1, parsed.tables.memberRefs.size)
        assertEquals(".ctor", parsed.strings.get(parsed.tables.memberRefs[0].name))
    }

    @Test
    fun roundTrip() {
        val mod = ModuleBuilder.clr("RoundTrip")
        val type = mod.defineType("RoundTrip.Calculator")

        type.defineMethod("Double",
            Signature.of(TypeRef.I32, TypeRef.I32),
            MethodFlag.PUBLIC, MethodFlag.STATIC).apply {
            il().ldarg(0); il().ldcI4Auto(2); il().mul(); il().ret()
        }

        val parsed = parse(mod.toBytes())
        assertNotNull(parsed)
        assertEquals("RoundTrip.dll", parsed.strings.get(parsed.tables.modules[0].name))
        assertEquals("Calculator", parsed.strings.get(parsed.tables.typeDefs[1].name))
        assertEquals("Double", parsed.strings.get(parsed.tables.methodDefs[0].name))
    }

    @Test
    fun dynamicMethod() {
        val method = DynamicMethod.clr("Add",
            Signature.of(TypeRef.I32, TypeRef.I32, TypeRef.I32))

        method.il().ldarg(0)
        method.il().ldarg(1)
        method.il().add()
        method.il().ret()

        val bytes = method.toBytes()
        assertEquals(4, bytes.size)
        assertEquals(0x02, bytes[0].toInt() and 0xFF) // ldarg.0
        assertEquals(0x03, bytes[1].toInt() and 0xFF) // ldarg.1
        assertEquals(0x58, bytes[2].toInt() and 0xFF) // add
        assertEquals(0x2A, bytes[3].toInt() and 0xFF) // ret
    }

    @Test
    fun dynamicMethodWithLabels() {
        val method = DynamicMethod.clr("Abs",
            Signature.of(TypeRef.I32, TypeRef.I32))

        val il = method.il()
        val positive = il.defineLabel()
        il.ldarg(0)
        il.ldcI4Auto(0)
        il.bge(positive)
        il.ldarg(0)
        il.neg()
        il.ret()
        il.markLabel(positive)
        il.ldarg(0)
        il.ret()

        val bytes = method.toBytes()
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun allPrimitiveTypes() {
        val mod = ModuleBuilder.clr("TestAsm")
        val type = mod.defineType("AllTypes")

        type.defineField("a", TypeRef.BOOL, FieldFlag.PUBLIC)
        type.defineField("b", TypeRef.I8, FieldFlag.PUBLIC)
        type.defineField("c", TypeRef.I16, FieldFlag.PUBLIC)
        type.defineField("d", TypeRef.I32, FieldFlag.PUBLIC)
        type.defineField("e", TypeRef.I64, FieldFlag.PUBLIC)
        type.defineField("f", TypeRef.U8, FieldFlag.PUBLIC)
        type.defineField("g", TypeRef.U16, FieldFlag.PUBLIC)
        type.defineField("h", TypeRef.U32, FieldFlag.PUBLIC)
        type.defineField("i", TypeRef.U64, FieldFlag.PUBLIC)
        type.defineField("j", TypeRef.F32, FieldFlag.PUBLIC)
        type.defineField("k", TypeRef.F64, FieldFlag.PUBLIC)
        type.defineField("l", TypeRef.POINTER, FieldFlag.PUBLIC)

        val parsed = parse(mod.toBytes())
        assertNotNull(parsed)
        assertEquals(12, parsed.tables.fields.size)
    }

    @Test
    fun onlyOneTypeAllowed() {
        val mod = ModuleBuilder.clr("TestAsm")
        mod.defineType("First")
        assertThrows<IllegalArgumentException> {
            mod.defineType("Second")
        }
    }

    private fun parse(bytes: ByteArray): ClrMetadata? {
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return ClrTableParser(buf, bytes).parse(0, bytes.size, 2, 5,
            ClrMetadata.COR_FLAGS_ILONLY, 0)
    }
}
