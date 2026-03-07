package org.kgen.reflect

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class TypeRefTest {

    @Test
    fun primitives() {
        assertEquals("i32", TypeRef.I32.fullName())
        assertEquals("i64", TypeRef.I64.fullName())
        assertEquals("f64", TypeRef.F64.fullName())
        assertEquals("ptr", TypeRef.POINTER.fullName())
        assertEquals("bool", TypeRef.BOOL.fullName())
        assertTrue(TypeRef.I32.isPrimitive())
        assertFalse(TypeRef.I32.isVoid())
    }

    @Test
    fun voidType() {
        assertTrue(TypeRef.VOID.isVoid())
        assertFalse(TypeRef.VOID.isPrimitive())
        assertEquals("void", TypeRef.VOID.fullName())
    }

    @Test
    fun longIsI64() {
        assertSame(TypeRef.I64, TypeRef.LONG)
    }

    @Test
    fun namedType() {
        val ref = TypeRef.of("System.String")
        assertEquals("System.String", ref.fullName())
        assertEquals("String", ref.name())
        assertEquals("System", ref.namespace())
        assertFalse(ref.isPrimitive())
        assertFalse(ref.isVoid())
        assertFalse(ref.isArray())
    }

    @Test
    fun namedTypeFromQualifiedName() {
        val qname = QualifiedName.of("System", "String")
        val ref = TypeRef.of(qname)
        assertEquals("System.String", ref.fullName())
    }

    @Test
    fun arrayType() {
        val arr = TypeRef.arrayOf(TypeRef.I32)
        assertTrue(arr.isArray())
        assertEquals("i32[]", arr.fullName())
        assertEquals(TypeRef.I32, arr.elementType())
    }

    @Test
    fun nestedArrayType() {
        val arr = TypeRef.arrayOf(TypeRef.arrayOf(TypeRef.F64))
        assertEquals("f64[][]", arr.fullName())
        assertTrue(arr.isArray())
        assertTrue(arr.elementType()!!.isArray())
    }

    @Test
    fun pointerType() {
        val ptr = TypeRef.pointerTo(TypeRef.I8)
        assertTrue(ptr.isPointer())
        assertEquals("i8*", ptr.fullName())
        assertEquals(TypeRef.I8, ptr.elementType())
    }

    @Test
    fun byRefType() {
        val ref = TypeRef.byRef(TypeRef.I32)
        assertTrue(ref.isByRef())
        assertEquals("i32&", ref.fullName())
        assertEquals(TypeRef.I32, ref.elementType())
    }

    @Test
    fun genericParam() {
        val t = TypeRef.genericParam("T")
        assertTrue(t.isGenericParameter())
        assertEquals("T", t.name())
    }

    @Test
    fun equality() {
        assertEquals(TypeRef.I32, TypeRef.I32)
        assertEquals(TypeRef.of("System.String"), TypeRef.of("System.String"))
        assertNotEquals(TypeRef.I32, TypeRef.I64)
        assertNotEquals(TypeRef.of("System.String"), TypeRef.of("System.Int32"))
        assertEquals(TypeRef.arrayOf(TypeRef.I32), TypeRef.arrayOf(TypeRef.I32))
        assertNotEquals(TypeRef.arrayOf(TypeRef.I32), TypeRef.arrayOf(TypeRef.I64))
    }

    @Test
    fun toStringIsFullName() {
        assertEquals("i32", TypeRef.I32.toString())
        assertEquals("System.String", TypeRef.of("System.String").toString())
        assertEquals("i32[]", TypeRef.arrayOf(TypeRef.I32).toString())
    }

    @Test
    fun resolveNotYetImplemented() {
        assertFalse(TypeRef.of("System.String").isResolved())
        assertNull(TypeRef.of("System.String").resolve())
    }
}
