package org.kgen.tools

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class DemanglerExtendedTest {

    private val itanium = ItaniumDemangler()
    private val rust = RustDemangler()
    private val msvc = MsvcDemangler()
    private val universal = UniversalDemangler()

    // --- Itanium: type codes ---

    @Test
    fun itaniumVoidParam() {
        assertEquals("foo()", itanium.demangle("_Z3foov"))
    }

    @Test
    fun itaniumBoolParam() {
        val result = itanium.demangle("_Z3foob")
        assertNotNull(result)
        assertTrue(result!!.contains("bool"))
    }

    @Test
    fun itaniumCharParam() {
        val result = itanium.demangle("_Z3fooc")
        assertNotNull(result)
        assertTrue(result!!.contains("char"))
    }

    @Test
    fun itaniumLongParam() {
        val result = itanium.demangle("_Z3fool")
        assertNotNull(result)
        assertTrue(result!!.contains("long"))
    }

    @Test
    fun itaniumUnsignedIntParam() {
        val result = itanium.demangle("_Z3fooj")
        assertNotNull(result)
        assertTrue(result!!.contains("unsigned"))
    }

    @Test
    fun itaniumDoubleParam() {
        val result = itanium.demangle("_Z3food")
        assertNotNull(result)
        assertTrue(result!!.contains("double"))
    }

    @Test
    fun itaniumFloatParam() {
        val result = itanium.demangle("_Z3foof")
        assertNotNull(result)
        assertTrue(result!!.contains("float"))
    }

    // --- Itanium: pointer and reference types ---

    @Test
    fun itaniumPointerToPointer() {
        val result = itanium.demangle("_Z3fooPPi")
        assertNotNull(result)
        assertTrue(result!!.contains("int**"))
    }

    @Test
    fun itaniumReferenceType() {
        val result = itanium.demangle("_Z3fooRi")
        assertNotNull(result)
        assertTrue(result!!.contains("int"))
    }

    @Test
    fun itaniumConstPointer() {
        val result = itanium.demangle("_Z3fooPKc")
        assertNotNull(result)
        assertTrue(result!!.contains("const"))
        assertTrue(result.contains("char"))
    }

    // --- Itanium: longer names ---

    @Test
    fun itaniumLongFunctionName() {
        val result = itanium.demangle("_Z12longFuncNamei")
        assertNotNull(result)
        assertTrue(result!!.contains("longFuncName"))
    }

    @Test
    fun itaniumSingleCharName() {
        val result = itanium.demangle("_Z1fv")
        assertNotNull(result)
        assertEquals("f()", result)
    }

    // --- Itanium: nested names ---

    @Test
    fun itaniumDeeplyNested() {
        val result = itanium.demangle("_ZN1A1B1C3fooEv")
        assertNotNull(result)
        assertTrue(result!!.contains("A::B::C::foo"))
    }

    @Test
    fun itaniumNestedWithMultipleParams() {
        val result = itanium.demangle("_ZN3Foo6methodEidf")
        assertNotNull(result)
        assertTrue(result!!.contains("Foo::method"))
    }

    // --- Itanium: special names ---

    @Test
    fun itaniumConstructor() {
        val result = itanium.demangle("_ZN3FooC1Ev")
        assertNotNull(result)
        assertTrue(result!!.contains("Foo"))
    }

    @Test
    fun itaniumDestructor() {
        val result = itanium.demangle("_ZN3FooD1Ev")
        assertNotNull(result)
        assertTrue(result!!.contains("Foo"))
    }

    // --- Itanium: canDemangle ---

    @Test
    fun itaniumCanDemangleValid() {
        assertTrue(itanium.canDemangle("_Z3foov"))
        assertTrue(itanium.canDemangle("__Z3foov"))
    }

    @Test
    fun itaniumCannotDemangleRust() {
        // Rust legacy also starts with _ZN, but itanium should still try
        assertTrue(itanium.canDemangle("_ZN3foo3barEv"))
    }

    @Test
    fun itaniumCannotDemangleMsvc() {
        assertFalse(itanium.canDemangle("?foo@@YAHXZ"))
    }

    @Test
    fun itaniumCannotDemanglePlainC() {
        assertFalse(itanium.canDemangle("printf"))
        assertFalse(itanium.canDemangle("_main"))
    }

    // --- Rust: various paths ---

    @Test
    fun rustSingleComponent() {
        val result = rust.demangle("_ZN4main17h0123456789abcdefE")
        assertNotNull(result)
        assertEquals("main", result)
    }

    @Test
    fun rustDeepPath() {
        val result = rust.demangle("_ZN3std3sys4unix3net11TcpListener4bind17habcdef0123456789E")
        assertNotNull(result)
        assertEquals("std::sys::unix::net::TcpListener::bind", result)
    }

    @Test
    fun rustWithUnderscore() {
        val result = rust.demangle("_ZN4core6option15Option\$LT\$T\$GT\$6unwrap17h0123456789abcdefE")
        assertNotNull(result)
        assertTrue(result!!.contains("core"))
        assertTrue(result.contains("option"))
    }

    @Test
    fun rustCanDemangleLegacy() {
        assertTrue(rust.canDemangle("_ZN4core3fmt5write17h1234567890abcdefE"))
    }

    @Test
    fun rustCanDemangleV0() {
        assertTrue(rust.canDemangle("_RNvCs1234_4core3foo"))
    }

    @Test
    fun rustCannotDemanglePlainItanium() {
        // Pure itanium without hash should not match rust
        assertFalse(rust.canDemangle("_Z3foov"))
    }

    // --- MSVC: various patterns ---

    @Test
    fun msvcCanDemangleQuestionMark() {
        assertTrue(msvc.canDemangle("?method@Class@@QEAAHXZ"))
    }

    @Test
    fun msvcNamespaced() {
        val result = msvc.demangle("?method@Class@NS@@QEAAHXZ")
        assertNotNull(result)
        assertTrue(result!!.contains("NS"))
        assertTrue(result.contains("Class"))
        assertTrue(result.contains("method"))
    }

    @Test
    fun msvcSimpleVoid() {
        val result = msvc.demangle("?foo@@YAXXZ")
        assertNotNull(result)
        assertTrue(result!!.contains("foo"))
    }

    @Test
    fun msvcWithIntParam() {
        val result = msvc.demangle("?foo@@YAHH@Z")
        assertNotNull(result)
        assertTrue(result!!.contains("foo"))
    }

    @Test
    fun msvcCannotDemangleItanium() {
        assertFalse(msvc.canDemangle("_Z3foov"))
    }

    @Test
    fun msvcCannotDemangleRust() {
        assertFalse(msvc.canDemangle("_RNvCs1234_4core3foo"))
    }

    // --- Universal: auto-detection ---

    @Test
    fun universalDemangleItaniumNested() {
        val result = universal.demangle("_ZN3foo3barEi")
        assertNotNull(result)
        assertTrue(result!!.contains("foo::bar"))
    }

    @Test
    fun universalDemangleRustV0() {
        val result = universal.demangle("_RNvCs1234_4core3foo")
        assertNotNull(result)
        assertTrue(result!!.contains("core"))
    }

    @Test
    fun universalDetectsNone() {
        assertNull(universal.detect("printf"))
        assertNull(universal.detect(""))
        assertNull(universal.detect("_main"))
    }

    @Test
    fun universalDetectsItaniumDoubleUnderscore() {
        assertEquals(ManglingScheme.ITANIUM, universal.detect("__Z3foov"))
    }

    @Test
    fun universalDemangleReturnsNullForPlain() {
        assertNull(universal.demangle("printf"))
        assertNull(universal.demangle("_start"))
        assertNull(universal.demangle(""))
    }

    // --- Edge cases ---

    @Test
    fun itaniumEmptyString() {
        assertFalse(itanium.canDemangle(""))
        assertNull(itanium.demangle(""))
    }

    @Test
    fun rustEmptyString() {
        assertFalse(rust.canDemangle(""))
        assertNull(rust.demangle(""))
    }

    @Test
    fun msvcEmptyString() {
        assertFalse(msvc.canDemangle(""))
        assertNull(msvc.demangle(""))
    }

    @Test
    fun universalEmptyString() {
        assertNull(universal.detect(""))
        assertNull(universal.demangle(""))
    }

    @Test
    fun itaniumJustPrefix() {
        assertTrue(itanium.canDemangle("_Z"))
        // May fail to demangle but should not crash
        itanium.demangle("_Z")
    }

    @Test
    fun itaniumMultiplePointers() {
        val result = itanium.demangle("_Z3fooPPPi")
        assertNotNull(result)
        assertTrue(result!!.contains("int***"))
    }

    @Test
    fun itaniumMixedParams() {
        val result = itanium.demangle("_Z3fooPciRd")
        assertNotNull(result)
        assertTrue(result!!.contains("char*"))
        assertTrue(result.contains("int"))
    }

    // --- Consistent results ---

    @Test
    fun sameInputGivesSameOutput() {
        val input = "_Z3fooi"
        val r1 = itanium.demangle(input)
        val r2 = itanium.demangle(input)
        assertEquals(r1, r2)
    }

    @Test
    fun rustSameInputSameOutput() {
        val input = "_ZN4core3fmt5write17h1234567890abcdefE"
        val r1 = rust.demangle(input)
        val r2 = rust.demangle(input)
        assertEquals(r1, r2)
    }

    @Test
    fun msvcSameInputSameOutput() {
        val input = "?foo@@YAHXZ"
        val r1 = msvc.demangle(input)
        val r2 = msvc.demangle(input)
        assertEquals(r1, r2)
    }
}
