package org.kgen.reflect

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.binary.mangling.ItaniumDemangler
import org.kgen.binary.mangling.ManglingScheme
import org.kgen.binary.mangling.MsvcDemangler
import org.kgen.binary.mangling.RustDemangler
import org.kgen.binary.mangling.UniversalDemangler

class DemanglerTest {

    // --- Itanium C++ ---

    private val itanium = ItaniumDemangler()

    @Test
    fun `itanium simple function`() {
        assertEquals("foo()", itanium.demangle("_Z3foov"))
    }

    @Test
    fun `itanium function with int param`() {
        assertEquals("foo(int)", itanium.demangle("_Z3fooi"))
    }

    @Test
    fun `itanium function returning int with params`() {
        val result = itanium.demangle("_Z3fooii")
        assertNotNull(result)
        assertTrue(result!!.contains("foo"))
        assertTrue(result.contains("int"))
    }

    @Test
    fun `itanium nested name`() {
        val result = itanium.demangle("_ZN3foo3barEv")
        assertNotNull(result)
        assertTrue(result!!.contains("foo::bar"))
    }

    @Test
    fun `itanium nested name with params`() {
        val result = itanium.demangle("_ZN3foo3barEi")
        assertNotNull(result)
        assertTrue(result!!.contains("foo::bar"))
        assertTrue(result.contains("int"))
    }

    @Test
    fun `itanium pointer type`() {
        val result = itanium.demangle("_Z3fooPi")
        assertNotNull(result)
        assertTrue(result!!.contains("int*"))
    }

    @Test
    fun `itanium const ref type`() {
        val result = itanium.demangle("_Z3fooRKi")
        assertNotNull(result)
        assertTrue(result!!.contains("const"))
    }

    @Test
    fun `itanium cannot demangle non-mangled name`() {
        assertFalse(itanium.canDemangle("main"))
        assertNull(itanium.demangle("main"))
    }

    @Test
    fun `itanium handles double underscore prefix`() {
        val result = itanium.demangle("__Z3foov")
        assertNotNull(result)
        assertEquals("foo()", result)
    }

    @Test
    fun `itanium multiple params`() {
        val result = itanium.demangle("_Z3fooidf")
        assertNotNull(result)
        assertTrue(result!!.contains("int"))
        assertTrue(result.contains("double"))
        assertTrue(result.contains("float"))
    }

    // --- Rust ---

    private val rust = RustDemangler()

    @Test
    fun `rust legacy simple`() {
        val result = rust.demangle("_ZN4core3fmt5write17h1234567890abcdefE")
        assertNotNull(result)
        assertEquals("core::fmt::write", result)
    }

    @Test
    fun `rust legacy strips hash`() {
        val result = rust.demangle("_ZN3std2io5stdio6_print17habcdef0123456789E")
        assertNotNull(result)
        assertEquals("std::io::stdio::_print", result)
    }

    @Test
    fun `rust cannot demangle non-rust`() {
        assertFalse(rust.canDemangle("main"))
        assertNull(rust.demangle("main"))
    }

    @Test
    fun `rust v0 crate path`() {
        val result = rust.demangle("_RNvCs1234_4core3foo")
        assertNotNull(result)
        assertTrue(result!!.contains("core"))
        assertTrue(result.contains("foo"))
    }

    // --- MSVC ---

    private val msvc = MsvcDemangler()

    @Test
    fun `msvc cannot demangle non-msvc`() {
        assertFalse(msvc.canDemangle("main"))
        assertNull(msvc.demangle("main"))
    }

    @Test
    fun `msvc detects mangled names`() {
        assertTrue(msvc.canDemangle("?foo@@YAHXZ"))
    }

    @Test
    fun `msvc simple function`() {
        val result = msvc.demangle("?foo@@YAHXZ")
        assertNotNull(result)
        assertTrue(result!!.contains("foo"))
    }

    @Test
    fun `msvc class method`() {
        val result = msvc.demangle("?method@MyClass@@QAEHXZ")
        assertNotNull(result)
        assertTrue(result!!.contains("MyClass"))
        assertTrue(result.contains("method"))
    }

    // --- Universal ---

    private val universal = UniversalDemangler()

    @Test
    fun `universal detects itanium`() {
        assertEquals(ManglingScheme.ITANIUM, universal.detect("_Z3foov"))
    }

    @Test
    fun `universal detects msvc`() {
        assertEquals(ManglingScheme.MSVC, universal.detect("?foo@@YAHXZ"))
    }

    @Test
    fun `universal detects rust v0`() {
        assertEquals(ManglingScheme.RUST, universal.detect("_RNvCs1234_4core3foo"))
    }

    @Test
    fun `universal detects rust legacy`() {
        assertEquals(ManglingScheme.RUST, universal.detect("_ZN4core3fmt5write17h1234567890abcdefE"))
    }

    @Test
    fun `universal returns null for unmangled`() {
        assertNull(universal.detect("main"))
        assertNull(universal.demangle("main"))
    }

    @Test
    fun `universal demangles itanium`() {
        val result = universal.demangle("_Z3foov")
        assertNotNull(result)
        assertEquals("foo()", result)
    }

    @Test
    fun `universal demangles rust legacy`() {
        val result = universal.demangle("_ZN4core3fmt5write17h1234567890abcdefE")
        assertNotNull(result)
        assertEquals("core::fmt::write", result)
    }

    @Test
    fun `universal demangles msvc`() {
        val result = universal.demangle("?foo@@YAHXZ")
        assertNotNull(result)
        assertTrue(result!!.contains("foo"))
    }
}
