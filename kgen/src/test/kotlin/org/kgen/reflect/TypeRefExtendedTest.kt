package org.kgen.reflect

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class TypeRefExtendedTest {

    // -- Primitives --

    @Test
    fun allPrimitivesArePrimitive() {
        val primitives = listOf(TypeRef.BOOL, TypeRef.I8, TypeRef.I16, TypeRef.I32, TypeRef.I64,
            TypeRef.U8, TypeRef.U16, TypeRef.U32, TypeRef.U64, TypeRef.F32, TypeRef.F64, TypeRef.POINTER)
        for (p in primitives) {
            assertTrue(p.isPrimitive(), "${p.fullName()} should be primitive")
        }
    }

    @Test
    fun primitiveFullNames() {
        assertEquals("bool", TypeRef.BOOL.fullName())
        assertEquals("i8", TypeRef.I8.fullName())
        assertEquals("i16", TypeRef.I16.fullName())
        assertEquals("i32", TypeRef.I32.fullName())
        assertEquals("i64", TypeRef.I64.fullName())
        assertEquals("u8", TypeRef.U8.fullName())
        assertEquals("u16", TypeRef.U16.fullName())
        assertEquals("u32", TypeRef.U32.fullName())
        assertEquals("u64", TypeRef.U64.fullName())
        assertEquals("f32", TypeRef.F32.fullName())
        assertEquals("f64", TypeRef.F64.fullName())
        assertEquals("ptr", TypeRef.POINTER.fullName())
    }

    @Test
    fun primitivesAreNotVoid() {
        assertFalse(TypeRef.I32.isVoid())
        assertFalse(TypeRef.F64.isVoid())
    }

    @Test
    fun primitivesAreNotArray() {
        assertFalse(TypeRef.I32.isArray())
    }

    @Test
    fun primitivesAreNotPointer() {
        assertFalse(TypeRef.I32.isPointer())
    }

    @Test
    fun primitivesAreNotByRef() {
        assertFalse(TypeRef.I32.isByRef())
    }

    @Test
    fun primitivesAreNotGenericParam() {
        assertFalse(TypeRef.I32.isGenericParameter())
    }

    // -- VOID --

    @Test
    fun voidIsVoid() {
        assertTrue(TypeRef.VOID.isVoid())
    }

    @Test
    fun voidIsNotPrimitive() {
        assertFalse(TypeRef.VOID.isPrimitive())
    }

    @Test
    fun voidFullName() {
        assertEquals("void", TypeRef.VOID.fullName())
    }

    // -- LONG alias --

    @Test
    fun longIsI64() {
        assertSame(TypeRef.I64, TypeRef.LONG)
    }

    // -- Named types --

    @Test
    fun namedTypeFullName() {
        val ref = TypeRef.of("System.Collections.Generic.List")
        assertEquals("System.Collections.Generic.List", ref.fullName())
    }

    @Test
    fun namedTypeName() {
        val ref = TypeRef.of("System.String")
        assertEquals("String", ref.name())
    }

    @Test
    fun namedTypeNamespace() {
        val ref = TypeRef.of("System.String")
        assertEquals("System", ref.namespace())
    }

    @Test
    fun namedTypeNotPrimitive() {
        assertFalse(TypeRef.of("System.String").isPrimitive())
    }

    @Test
    fun namedTypeNotVoid() {
        assertFalse(TypeRef.of("System.String").isVoid())
    }

    @Test
    fun namedTypeFromQualifiedName() {
        val qname = QualifiedName.of("System", "Int32")
        val ref = TypeRef.of(qname)
        assertEquals("System.Int32", ref.fullName())
    }

    // -- Array types --

    @Test
    fun arrayTypeIsArray() {
        assertTrue(TypeRef.arrayOf(TypeRef.I32).isArray())
    }

    @Test
    fun arrayTypeFullName() {
        assertEquals("i32[]", TypeRef.arrayOf(TypeRef.I32).fullName())
    }

    @Test
    fun arrayElementType() {
        val arr = TypeRef.arrayOf(TypeRef.F64)
        assertEquals(TypeRef.F64, arr.elementType())
    }

    @Test
    fun nestedArrayType() {
        val arr = TypeRef.arrayOf(TypeRef.arrayOf(TypeRef.I32))
        assertEquals("i32[][]", arr.fullName())
        assertTrue(arr.isArray())
        assertTrue(arr.elementType()!!.isArray())
        assertEquals(TypeRef.I32, arr.elementType()!!.elementType())
    }

    @Test
    fun arrayOfNamedType() {
        val arr = TypeRef.arrayOf(TypeRef.of("System.String"))
        assertEquals("System.String[]", arr.fullName())
    }

    // -- Pointer types --

    @Test
    fun pointerTypeIsPointer() {
        assertTrue(TypeRef.pointerTo(TypeRef.I8).isPointer())
    }

    @Test
    fun pointerTypeFullName() {
        assertEquals("i8*", TypeRef.pointerTo(TypeRef.I8).fullName())
    }

    @Test
    fun pointerElementType() {
        assertEquals(TypeRef.I32, TypeRef.pointerTo(TypeRef.I32).elementType())
    }

    @Test
    fun pointerToPointer() {
        val pp = TypeRef.pointerTo(TypeRef.pointerTo(TypeRef.I8))
        assertEquals("i8**", pp.fullName())
    }

    // -- ByRef types --

    @Test
    fun byRefTypeIsByRef() {
        assertTrue(TypeRef.byRef(TypeRef.I32).isByRef())
    }

    @Test
    fun byRefFullName() {
        assertEquals("i32&", TypeRef.byRef(TypeRef.I32).fullName())
    }

    @Test
    fun byRefElementType() {
        assertEquals(TypeRef.I32, TypeRef.byRef(TypeRef.I32).elementType())
    }

    // -- Generic parameters --

    @Test
    fun genericParamIsGenericParameter() {
        assertTrue(TypeRef.genericParam("T").isGenericParameter())
    }

    @Test
    fun genericParamName() {
        assertEquals("T", TypeRef.genericParam("T").name())
    }

    @Test
    fun genericParamNotPrimitive() {
        assertFalse(TypeRef.genericParam("T").isPrimitive())
    }

    // -- Equality --

    @Test
    fun sameTypeRefEquals() {
        assertEquals(TypeRef.I32, TypeRef.I32)
    }

    @Test
    fun equalNamedTypesEqual() {
        assertEquals(TypeRef.of("System.String"), TypeRef.of("System.String"))
    }

    @Test
    fun differentPrimitivesNotEqual() {
        assertNotEquals(TypeRef.I32, TypeRef.I64)
    }

    @Test
    fun differentNamedTypesNotEqual() {
        assertNotEquals(TypeRef.of("System.String"), TypeRef.of("System.Int32"))
    }

    @Test
    fun arrayEquality() {
        assertEquals(TypeRef.arrayOf(TypeRef.I32), TypeRef.arrayOf(TypeRef.I32))
    }

    @Test
    fun arrayInequality() {
        assertNotEquals(TypeRef.arrayOf(TypeRef.I32), TypeRef.arrayOf(TypeRef.I64))
    }

    @Test
    fun pointerEquality() {
        assertEquals(TypeRef.pointerTo(TypeRef.I8), TypeRef.pointerTo(TypeRef.I8))
    }

    @Test
    fun pointerInequality() {
        assertNotEquals(TypeRef.pointerTo(TypeRef.I8), TypeRef.pointerTo(TypeRef.I32))
    }

    @Test
    fun byRefEquality() {
        assertEquals(TypeRef.byRef(TypeRef.I32), TypeRef.byRef(TypeRef.I32))
    }

    // -- toString --

    @Test
    fun toStringIsPrimitiveName() {
        assertEquals("i32", TypeRef.I32.toString())
    }

    @Test
    fun toStringIsNamedFullName() {
        assertEquals("System.String", TypeRef.of("System.String").toString())
    }

    @Test
    fun toStringIsArrayFullName() {
        assertEquals("i32[]", TypeRef.arrayOf(TypeRef.I32).toString())
    }

    @Test
    fun toStringIsPointerFullName() {
        assertEquals("i8*", TypeRef.pointerTo(TypeRef.I8).toString())
    }

    // -- Resolve --

    @Test
    fun resolveNotYetImplemented() {
        assertFalse(TypeRef.of("System.String").isResolved())
        assertNull(TypeRef.of("System.String").resolve())
    }

    @Test
    fun primitiveElementTypeNull() {
        assertNull(TypeRef.I32.elementType())
    }
}
