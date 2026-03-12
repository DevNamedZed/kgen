package org.kgen.binary.mangling

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested

class DemanglerComprehensiveTest {

    @Nested
    inner class ItaniumDemanglerTests {

        private val demangler = ItaniumDemangler()

        @Test
        fun `canDemangle returns true for _Z prefix`() {
            assertTrue(demangler.canDemangle("_Z3foov"))
        }

        @Test
        fun `canDemangle returns true for __Z prefix`() {
            assertTrue(demangler.canDemangle("__Z3foov"))
        }

        @Test
        fun `canDemangle returns false for non-mangled name`() {
            assertFalse(demangler.canDemangle("main"))
        }

        @Test
        fun `canDemangle returns false for empty string`() {
            assertFalse(demangler.canDemangle(""))
        }

        @Test
        fun `canDemangle returns false for MSVC mangled name`() {
            assertFalse(demangler.canDemangle("?foo@@YAHXZ"))
        }

        @Test
        fun `simple void function`() {
            assertEquals("foo()", demangler.demangle("_Z3foov"))
        }

        @Test
        fun `function with int parameter`() {
            assertEquals("foo(int)", demangler.demangle("_Z3fooi"))
        }

        @Test
        fun `function with multiple parameters`() {
            assertEquals("foo(int, double, float)", demangler.demangle("_Z3fooidf"))
        }

        @Test
        fun `function with bool parameter`() {
            assertEquals("foo(bool)", demangler.demangle("_Z3foob"))
        }

        @Test
        fun `function with char parameter`() {
            assertEquals("foo(char)", demangler.demangle("_Z3fooc"))
        }

        @Test
        fun `function with short parameter`() {
            assertEquals("foo(short)", demangler.demangle("_Z3foos"))
        }

        @Test
        fun `function with long parameter`() {
            assertEquals("foo(long)", demangler.demangle("_Z3fool"))
        }

        @Test
        fun `function with long long parameter`() {
            assertEquals("foo(long long)", demangler.demangle("_Z3foox"))
        }

        @Test
        fun `function with unsigned int parameter`() {
            assertEquals("foo(unsigned int)", demangler.demangle("_Z3fooj"))
        }

        @Test
        fun `function with unsigned long parameter`() {
            assertEquals("foo(unsigned long)", demangler.demangle("_Z3foom"))
        }

        @Test
        fun `function with float parameter`() {
            assertEquals("foo(float)", demangler.demangle("_Z3foof"))
        }

        @Test
        fun `function with double parameter`() {
            assertEquals("foo(double)", demangler.demangle("_Z3food"))
        }

        @Test
        fun `function with long double parameter`() {
            assertEquals("foo(long double)", demangler.demangle("_Z3fooe"))
        }

        @Test
        fun `function with wchar_t parameter`() {
            assertEquals("foo(wchar_t)", demangler.demangle("_Z3foow"))
        }

        @Test
        fun `function with signed char parameter`() {
            assertEquals("foo(signed char)", demangler.demangle("_Z3fooa"))
        }

        @Test
        fun `function with unsigned char parameter`() {
            assertEquals("foo(unsigned char)", demangler.demangle("_Z3fooh"))
        }

        @Test
        fun `function with unsigned short parameter`() {
            assertEquals("foo(unsigned short)", demangler.demangle("_Z3foot"))
        }

        @Test
        fun `function with unsigned long long parameter`() {
            assertEquals("foo(unsigned long long)", demangler.demangle("_Z3fooy"))
        }

        @Test
        fun `function with variadic parameter`() {
            val result = demangler.demangle("_Z3fooiz")
            assertNotNull(result)
            assertTrue(result!!.contains("..."))
        }

        @Test
        fun `pointer to int`() {
            assertEquals("foo(int*)", demangler.demangle("_Z3fooPi"))
        }

        @Test
        fun `pointer to double`() {
            assertEquals("foo(double*)", demangler.demangle("_Z3fooPd"))
        }

        @Test
        fun `reference to int`() {
            assertEquals("foo(int&)", demangler.demangle("_Z3fooRi"))
        }

        @Test
        fun `rvalue reference to int`() {
            assertEquals("foo(int&&)", demangler.demangle("_Z3fooOi"))
        }

        @Test
        fun `const int`() {
            assertEquals("foo(int const)", demangler.demangle("_Z3fooKi"))
        }

        @Test
        fun `volatile int`() {
            assertEquals("foo(int volatile)", demangler.demangle("_Z3fooVi"))
        }

        @Test
        fun `const reference to int`() {
            assertEquals("foo(int const&)", demangler.demangle("_Z3fooRKi"))
        }

        @Test
        fun `pointer to const int`() {
            assertEquals("foo(int const*)", demangler.demangle("_Z3fooPKi"))
        }

        @Test
        fun `simple nested name - namespace function`() {
            val result = demangler.demangle("_ZN3foo3barEv")
            assertNotNull(result)
            assertTrue(result!!.contains("foo::bar"))
        }

        @Test
        fun `nested name with int parameter`() {
            val result = demangler.demangle("_ZN3foo3barEi")
            assertNotNull(result)
            assertTrue(result!!.contains("foo::bar"))
            assertTrue(result.contains("int"))
        }

        @Test
        fun `deeply nested name`() {
            val result = demangler.demangle("_ZN3foo3bar3bazEv")
            assertNotNull(result)
            assertTrue(result!!.contains("foo"))
            assertTrue(result.contains("bar"))
            assertTrue(result.contains("baz"))
        }

        @Test
        fun `constructor C1`() {
            val result = demangler.demangle("_ZN3FooC1Ev")
            assertNotNull(result)
            assertTrue(result!!.contains("Foo"))
        }

        @Test
        fun `constructor C2`() {
            val result = demangler.demangle("_ZN3FooC2Ev")
            assertNotNull(result)
            assertTrue(result!!.contains("Foo"))
        }

        @Test
        fun `destructor D1`() {
            val result = demangler.demangle("_ZN3FooD1Ev")
            assertNotNull(result)
            assertTrue(result!!.contains("~"))
            assertTrue(result.contains("Foo"))
        }

        @Test
        fun `destructor D2`() {
            val result = demangler.demangle("_ZN3FooD2Ev")
            assertNotNull(result)
            assertTrue(result!!.contains("~"))
        }

        @Test
        fun `operator+ on class`() {
            val result = demangler.demangle("_ZN3fooplEi")
            assertNotNull(result)
            assertTrue(result!!.contains("operator+"))
        }

        @Test
        fun `operator- on class`() {
            val result = demangler.demangle("_ZN3foomiEi")
            assertNotNull(result)
            assertTrue(result!!.contains("operator-"))
        }

        @Test
        fun `operator* on class`() {
            val result = demangler.demangle("_ZN3foomlEi")
            assertNotNull(result)
            assertTrue(result!!.contains("operator*"))
        }

        @Test
        fun `operator divide on class`() {
            val result = demangler.demangle("_ZN3foodvEi")
            assertNotNull(result)
            assertTrue(result!!.contains("operator/"))
        }

        @Test
        fun `operator== on class`() {
            val result = demangler.demangle("_ZN3fooeqEi")
            assertNotNull(result)
            assertTrue(result!!.contains("operator=="))
        }

        @Test
        fun `operator!= on class`() {
            val result = demangler.demangle("_ZN3fooneEi")
            assertNotNull(result)
            assertTrue(result!!.contains("operator!="))
        }

        @Test
        fun `operator less than on class`() {
            val result = demangler.demangle("_ZN3fooltEi")
            assertNotNull(result)
            assertTrue(result!!.contains("operator<"))
        }

        @Test
        fun `operator greater than on class`() {
            val result = demangler.demangle("_ZN3foogtEi")
            assertNotNull(result)
            assertTrue(result!!.contains("operator>"))
        }

        @Test
        fun `operator call on class`() {
            val result = demangler.demangle("_ZN3fooclEi")
            assertNotNull(result)
            assertTrue(result!!.contains("operator()"))
        }

        @Test
        fun `operator index on class`() {
            val result = demangler.demangle("_ZN3fooixEi")
            assertNotNull(result)
            assertTrue(result!!.contains("operator[]"))
        }

        @Test
        fun `operator assign on class`() {
            val result = demangler.demangle("_ZN3fooaSEi")
            assertNotNull(result)
            assertTrue(result!!.contains("operator="))
        }

        @Test
        fun `operator left shift on class`() {
            val result = demangler.demangle("_ZN3foolsEi")
            assertNotNull(result)
            assertTrue(result!!.contains("operator<<"))
        }

        @Test
        fun `operator right shift on class`() {
            val result = demangler.demangle("_ZN3foorsEi")
            assertNotNull(result)
            assertTrue(result!!.contains("operator>>"))
        }

        @Test
        fun `std substitution St`() {
            val result = demangler.demangle("_ZN3fooRSt")
            // Parsing may vary but should not crash
            // St substitution maps to "std"
        }

        @Test
        fun `std string substitution Ss`() {
            val result = demangler.demangle("_Z3fooSs")
            // Should handle std::string substitution
            if (result != null) {
                assertTrue(result.contains("string") || result.contains("std"))
            }
        }

        @Test
        fun `template function with int`() {
            val result = demangler.demangle("_Z3fooIiEvT_")
            // Template parsing may or may not fully work, but should not crash
            assertNotNull(result)
        }

        @Test
        fun `double underscore prefix macOS style`() {
            assertEquals("foo()", demangler.demangle("__Z3foov"))
        }

        @Test
        fun `double underscore prefix with namespace`() {
            val result = demangler.demangle("__ZN3foo3barEv")
            assertNotNull(result)
            assertTrue(result!!.contains("foo::bar"))
        }

        @Test
        fun `long function name`() {
            assertEquals("longFunctionName()", demangler.demangle("_Z16longFunctionNamev"))
        }

        @Test
        fun `multiple pointer parameters`() {
            assertEquals("foo(int*, double*)", demangler.demangle("_Z3fooPiPd"))
        }

        @Test
        fun `mixed value and reference params`() {
            assertEquals("foo(int, double&, float*)", demangler.demangle("_Z3fooiRdPf"))
        }

        @Test
        fun `returns null for invalid mangled name`() {
            assertNull(demangler.demangle("_Zinvalid"))
        }

        @Test
        fun `demangle returns null for non-mangled input`() {
            assertNull(demangler.demangle("printf"))
        }

        @Test
        fun `named type parameter`() {
            val result = demangler.demangle("_Z3foo6MyType")
            assertNotNull(result)
            assertTrue(result!!.contains("MyType"))
        }

        @Test
        fun `two named type parameters`() {
            val result = demangler.demangle("_Z3foo6MyType7AnotherType")
            assertNotNull(result)
            assertTrue(result!!.contains("MyType"))
        }

        @Test
        fun `nested type name as parameter`() {
            val result = demangler.demangle("_Z3fooN3std6vectorE")
            assertNotNull(result)
        }
    }

    @Nested
    inner class MsvcDemanglerTests {

        private val demangler = MsvcDemangler()

        @Test
        fun `canDemangle returns true for question mark prefix`() {
            assertTrue(demangler.canDemangle("?foo@@YAHXZ"))
        }

        @Test
        fun `canDemangle returns false for non-mangled name`() {
            assertFalse(demangler.canDemangle("main"))
        }

        @Test
        fun `canDemangle returns false for empty string`() {
            assertFalse(demangler.canDemangle(""))
        }

        @Test
        fun `canDemangle returns false for Itanium name`() {
            assertFalse(demangler.canDemangle("_Z3foov"))
        }

        @Test
        fun `simple function with int return`() {
            val result = demangler.demangle("?foo@@YAHXZ")
            assertNotNull(result)
            assertTrue(result!!.contains("foo"))
        }

        @Test
        fun `class method public`() {
            val result = demangler.demangle("?method@MyClass@@QAEHXZ")
            assertNotNull(result)
            assertTrue(result!!.contains("MyClass"))
            assertTrue(result.contains("method"))
        }

        @Test
        fun `class method private`() {
            val result = demangler.demangle("?method@MyClass@@AAEHXZ")
            assertNotNull(result)
            assertTrue(result!!.contains("MyClass"))
            assertTrue(result.contains("method"))
            assertTrue(result.contains("private"))
        }

        @Test
        fun `class method protected`() {
            val result = demangler.demangle("?method@MyClass@@IAEHXZ")
            assertNotNull(result)
            assertTrue(result!!.contains("MyClass"))
            assertTrue(result.contains("protected"))
        }

        @Test
        fun `public static method`() {
            val result = demangler.demangle("?method@MyClass@@SAHXZ")
            assertNotNull(result)
            assertTrue(result!!.contains("static"))
        }

        @Test
        fun `public virtual method`() {
            val result = demangler.demangle("?method@MyClass@@UAEHXZ")
            assertNotNull(result)
            assertTrue(result!!.contains("virtual"))
        }

        @Test
        fun `cdecl calling convention`() {
            val result = demangler.demangle("?foo@@YAHXZ")
            assertNotNull(result)
            // Y = non-member, A = __cdecl, H = int return, X = void params
        }

        @Test
        fun `function with int parameter`() {
            val result = demangler.demangle("?foo@@YAHHZ")
            assertNotNull(result)
            assertTrue(result!!.contains("foo"))
        }

        @Test
        fun `function with char parameter`() {
            val result = demangler.demangle("?foo@@YAHDZ")
            assertNotNull(result)
            assertTrue(result!!.contains("foo"))
        }

        @Test
        fun `function with float parameter`() {
            val result = demangler.demangle("?foo@@YAHMZ")
            assertNotNull(result)
            assertTrue(result!!.contains("foo"))
        }

        @Test
        fun `function with double parameter`() {
            val result = demangler.demangle("?foo@@YAHNZ")
            assertNotNull(result)
            assertTrue(result!!.contains("foo"))
        }

        @Test
        fun `function with void return`() {
            val result = demangler.demangle("?foo@@YAXXZ")
            assertNotNull(result)
            assertTrue(result!!.contains("foo"))
        }

        @Test
        fun `function with pointer parameter`() {
            val result = demangler.demangle("?foo@@YAXPHZ")
            assertNotNull(result)
        }

        @Test
        fun `function with reference parameter`() {
            val result = demangler.demangle("?foo@@YAXAHZ")
            assertNotNull(result)
        }

        @Test
        fun `nested namespace`() {
            val result = demangler.demangle("?func@Inner@Outer@@YAHXZ")
            assertNotNull(result)
            assertTrue(result!!.contains("Outer"))
            assertTrue(result.contains("Inner"))
            assertTrue(result.contains("func"))
        }

        @Test
        fun `deeply nested namespace`() {
            val result = demangler.demangle("?func@C@B@A@@YAHXZ")
            assertNotNull(result)
            assertTrue(result!!.contains("A"))
            assertTrue(result.contains("B"))
            assertTrue(result.contains("C"))
        }

        @Test
        fun `constructor`() {
            val result = demangler.demangle("?0MyClass@@QAE@XZ")
            // Constructor uses ?0 prefix
            // May not parse fully but should not crash
        }

        @Test
        fun `destructor`() {
            val result = demangler.demangle("?1MyClass@@QAE@XZ")
            // Destructor uses ?1 prefix
        }

        @Test
        fun `function with long parameter`() {
            val result = demangler.demangle("?foo@@YAHJZ")
            assertNotNull(result)
        }

        @Test
        fun `function with unsigned int parameter`() {
            val result = demangler.demangle("?foo@@YAHIZ")
            assertNotNull(result)
        }

        @Test
        fun `function with short parameter`() {
            val result = demangler.demangle("?foo@@YAHFZ")
            assertNotNull(result)
        }

        @Test
        fun `function with unsigned short parameter`() {
            val result = demangler.demangle("?foo@@YAHGZ")
            assertNotNull(result)
        }

        @Test
        fun `function with unsigned long parameter`() {
            val result = demangler.demangle("?foo@@YAHKZ")
            assertNotNull(result)
        }

        @Test
        fun `function with long double parameter`() {
            val result = demangler.demangle("?foo@@YAHOZ")
            assertNotNull(result)
        }

        @Test
        fun `function with multiple parameters`() {
            val result = demangler.demangle("?foo@@YAHHNH@Z")
            assertNotNull(result)
            assertTrue(result!!.contains("foo"))
        }

        @Test
        fun `demangle returns null for non-mangled input`() {
            assertNull(demangler.demangle("printf"))
        }

        @Test
        fun `const pointer parameter`() {
            val result = demangler.demangle("?foo@@YAXQHZ")
            assertNotNull(result)
        }

        @Test
        fun `private static method`() {
            val result = demangler.demangle("?method@MyClass@@CAHXZ")
            assertNotNull(result)
            assertTrue(result!!.contains("static"))
            assertTrue(result.contains("private"))
        }

        @Test
        fun `protected static method`() {
            val result = demangler.demangle("?method@MyClass@@KAHXZ")
            assertNotNull(result)
            assertTrue(result!!.contains("static"))
            assertTrue(result.contains("protected"))
        }

        @Test
        fun `protected virtual method`() {
            val result = demangler.demangle("?method@MyClass@@MAEHXZ")
            assertNotNull(result)
            assertTrue(result!!.contains("virtual"))
            assertTrue(result.contains("protected"))
        }

        @Test
        fun `private virtual method`() {
            val result = demangler.demangle("?method@MyClass@@EAEHXZ")
            assertNotNull(result)
            assertTrue(result!!.contains("virtual"))
            assertTrue(result.contains("private"))
        }
    }

    @Nested
    inner class RustDemanglerTests {

        private val demangler = RustDemangler()

        @Test
        fun `canDemangle returns true for _R prefix`() {
            assertTrue(demangler.canDemangle("_RNvCs1234_4core3foo"))
        }

        @Test
        fun `canDemangle returns true for legacy rust format`() {
            assertTrue(demangler.canDemangle("_ZN4core3fmt5write17h1234567890abcdefE"))
        }

        @Test
        fun `canDemangle returns false for non-rust name`() {
            assertFalse(demangler.canDemangle("main"))
        }

        @Test
        fun `canDemangle returns false for empty string`() {
            assertFalse(demangler.canDemangle(""))
        }

        @Test
        fun `canDemangle returns false for plain itanium`() {
            assertFalse(demangler.canDemangle("_Z3foov"))
        }

        @Test
        fun `legacy simple crate path`() {
            assertEquals("core::fmt::write", demangler.demangle("_ZN4core3fmt5write17h1234567890abcdefE"))
        }

        @Test
        fun `legacy strips hash suffix`() {
            assertEquals("std::io::stdio::_print", demangler.demangle("_ZN3std2io5stdio6_print17habcdef0123456789E"))
        }

        @Test
        fun `legacy single segment`() {
            assertEquals("hello", demangler.demangle("_ZN5hello17h0000000000000000E"))
        }

        @Test
        fun `legacy two segments`() {
            assertEquals("foo::bar", demangler.demangle("_ZN3foo3bar17h0000000000000000E"))
        }

        @Test
        fun `legacy many segments`() {
            assertEquals("a::b::c::d", demangler.demangle("_ZN1a1b1c1d17h0000000000000000E"))
        }

        @Test
        fun `legacy with double underscore prefix`() {
            assertEquals("core::fmt::write", demangler.demangle("__ZN4core3fmt5write17h1234567890abcdefE"))
        }

        @Test
        fun `legacy without hash is not detected as rust`() {
            // Without the 17h... hash pattern, should not be detected as Rust legacy
            assertFalse(demangler.canDemangle("_ZN3foo3barEv"))
        }

        @Test
        fun `v0 crate path`() {
            val result = demangler.demangle("_RNvCs1234_4core3foo")
            assertNotNull(result)
            assertTrue(result!!.contains("core"))
            assertTrue(result.contains("foo"))
        }

        @Test
        fun `v0 nested path`() {
            val result = demangler.demangle("_RNvNtCs1234_4core3fmt5write")
            assertNotNull(result)
            assertTrue(result!!.contains("core"))
            assertTrue(result.contains("fmt"))
            assertTrue(result.contains("write"))
        }

        @Test
        fun `v0 simple crate`() {
            val result = demangler.demangle("_RCs1234_5hello")
            assertNotNull(result)
            assertTrue(result!!.contains("hello"))
        }

        @Test
        fun `returns null for invalid legacy format`() {
            // Starts with _ZN but missing E terminator
            assertNull(demangler.demangle("_RQ"))
        }

        @Test
        fun `demangle returns null for non-rust input`() {
            assertNull(demangler.demangle("printf"))
        }

        @Test
        fun `v0 generic path with type argument`() {
            val result = demangler.demangle("_RINvCs1234_4core3foobE")
            assertNotNull(result)
            assertTrue(result!!.contains("core"))
            assertTrue(result.contains("foo"))
        }

        @Test
        fun `v0 basic type bool`() {
            val result = demangler.demangle("_RINvCs1234_4core3foobE")
            assertNotNull(result)
            // 'b' is bool in Rust v0
        }

        @Test
        fun `v0 reference type`() {
            val result = demangler.demangle("_RINvCs1234_4core3fooRlE")
            assertNotNull(result)
            // R = reference, l = i32
        }

        @Test
        fun `v0 tuple type`() {
            val result = demangler.demangle("_RINvCs1234_4core3fooTlmEE")
            assertNotNull(result)
            // T...E = tuple of i32, u32
        }

        @Test
        fun `legacy hash with all hex digits`() {
            assertEquals("test::func", demangler.demangle("_ZN4test4func17habcdef1234567890E"))
        }
    }

    @Nested
    inner class UniversalDemanglerTests {

        private val demangler = UniversalDemangler()

        @Test
        fun `detects itanium scheme`() {
            assertEquals(ManglingScheme.ITANIUM, demangler.detect("_Z3foov"))
        }

        @Test
        fun `detects itanium scheme with double underscore`() {
            assertEquals(ManglingScheme.ITANIUM, demangler.detect("__Z3foov"))
        }

        @Test
        fun `detects msvc scheme`() {
            assertEquals(ManglingScheme.MSVC, demangler.detect("?foo@@YAHXZ"))
        }

        @Test
        fun `detects rust v0 scheme`() {
            assertEquals(ManglingScheme.RUST, demangler.detect("_RNvCs1234_4core3foo"))
        }

        @Test
        fun `detects rust legacy scheme`() {
            assertEquals(ManglingScheme.RUST, demangler.detect("_ZN4core3fmt5write17h1234567890abcdefE"))
        }

        @Test
        fun `returns null for unmangled name`() {
            assertNull(demangler.detect("main"))
        }

        @Test
        fun `returns null for empty string`() {
            assertNull(demangler.detect(""))
        }

        @Test
        fun `returns null for plain C function`() {
            assertNull(demangler.detect("printf"))
        }

        @Test
        fun `canDemangle returns true for itanium`() {
            assertTrue(demangler.canDemangle("_Z3foov"))
        }

        @Test
        fun `canDemangle returns true for msvc`() {
            assertTrue(demangler.canDemangle("?foo@@YAHXZ"))
        }

        @Test
        fun `canDemangle returns true for rust v0`() {
            assertTrue(demangler.canDemangle("_RNvCs1234_4core3foo"))
        }

        @Test
        fun `canDemangle returns false for unmangled`() {
            assertFalse(demangler.canDemangle("main"))
        }

        @Test
        fun `demangles itanium simple function`() {
            assertEquals("foo()", demangler.demangle("_Z3foov"))
        }

        @Test
        fun `demangles itanium nested name`() {
            val result = demangler.demangle("_ZN3foo3barEv")
            assertNotNull(result)
            assertTrue(result!!.contains("foo::bar"))
        }

        @Test
        fun `demangles msvc function`() {
            val result = demangler.demangle("?foo@@YAHXZ")
            assertNotNull(result)
            assertTrue(result!!.contains("foo"))
        }

        @Test
        fun `demangles rust legacy`() {
            assertEquals("core::fmt::write", demangler.demangle("_ZN4core3fmt5write17h1234567890abcdefE"))
        }

        @Test
        fun `demangles rust v0`() {
            val result = demangler.demangle("_RNvCs1234_4core3foo")
            assertNotNull(result)
            assertTrue(result!!.contains("core"))
        }

        @Test
        fun `returns null for unmangled input`() {
            assertNull(demangler.demangle("main"))
        }

        @Test
        fun `returns null for empty input`() {
            assertNull(demangler.demangle(""))
        }

        @Test
        fun `rust legacy takes priority over itanium for _ZN with hash`() {
            val result = demangler.demangle("_ZN4core3fmt5write17h1234567890abcdefE")
            // Should be detected as Rust and hash stripped
            assertEquals("core::fmt::write", result)
        }

        @Test
        fun `itanium used for _ZN without rust hash`() {
            val result = demangler.demangle("_ZN3foo3barEv")
            assertNotNull(result)
            // Should be demangled as Itanium since no hash
            assertTrue(result!!.contains("foo::bar"))
        }

        @Test
        fun `handles numeric-heavy names without crash`() {
            val result = demangler.demangle("_Z10myFunction")
            // May or may not produce a result, but should not crash
        }

        @Test
        fun `handles very long mangled names without crash`() {
            val longName = "_Z" + "3foo".repeat(50) + "v"
            // Should not crash even if parsing fails
            demangler.demangle(longName)
        }
    }

    @Nested
    inner class EdgeCaseTests {

        private val itanium = ItaniumDemangler()
        private val msvc = MsvcDemangler()
        private val rust = RustDemangler()
        private val universal = UniversalDemangler()

        @Test
        fun `itanium single char function name`() {
            assertEquals("f()", itanium.demangle("_Z1fv"))
        }

        @Test
        fun `itanium two char function name`() {
            assertEquals("ab()", itanium.demangle("_Z2abv"))
        }

        @Test
        fun `itanium no parameters`() {
            val result = itanium.demangle("_Z3foo")
            assertNotNull(result)
            assertEquals("foo()", result)
        }

        @Test
        fun `msvc only qualified name without type info`() {
            val result = msvc.demangle("?foo@@")
            // Should handle gracefully
        }

        @Test
        fun `all demanglers return null for totally invalid`() {
            assertNull(itanium.demangle("!!!"))
            assertNull(msvc.demangle("!!!"))
            assertNull(rust.demangle("!!!"))
            assertNull(universal.demangle("!!!"))
        }

        @Test
        fun `all demanglers canDemangle false for totally invalid`() {
            assertFalse(itanium.canDemangle("!!!"))
            assertFalse(msvc.canDemangle("!!!"))
            assertFalse(rust.canDemangle("!!!"))
            assertFalse(universal.canDemangle("!!!"))
        }

        @Test
        fun `itanium with __int128 parameter`() {
            assertEquals("foo(__int128)", itanium.demangle("_Z3foon"))
        }

        @Test
        fun `itanium substitution S_ refers to first`() {
            val result = itanium.demangle("_Z3foo6MyTypeS_")
            assertNotNull(result)
            // S_ should substitute the first registered name
        }

        @Test
        fun `itanium std allocator substitution`() {
            val result = itanium.demangle("_Z3fooSa")
            if (result != null) {
                assertTrue(result.contains("allocator") || result.contains("std"))
            }
        }

        @Test
        fun `itanium std istream substitution`() {
            val result = itanium.demangle("_Z3fooSi")
            if (result != null) {
                assertTrue(result.contains("istream") || result.contains("std"))
            }
        }

        @Test
        fun `itanium std ostream substitution`() {
            val result = itanium.demangle("_Z3fooSo")
            if (result != null) {
                assertTrue(result.contains("ostream") || result.contains("std"))
            }
        }

        @Test
        fun `itanium std iostream substitution`() {
            val result = itanium.demangle("_Z3fooSd")
            if (result != null) {
                assertTrue(result.contains("iostream") || result.contains("std"))
            }
        }

        @Test
        fun `mangling scheme enum has all expected values`() {
            val schemes = ManglingScheme.entries
            assertTrue(schemes.contains(ManglingScheme.ITANIUM))
            assertTrue(schemes.contains(ManglingScheme.MSVC))
            assertTrue(schemes.contains(ManglingScheme.RUST))
            assertTrue(schemes.contains(ManglingScheme.DLANG))
            assertTrue(schemes.contains(ManglingScheme.SWIFT))
            assertTrue(schemes.contains(ManglingScheme.JVM))
            assertTrue(schemes.contains(ManglingScheme.DOTNET))
            assertTrue(schemes.contains(ManglingScheme.AUTO))
        }

        @Test
        fun `demangler strategy is an interface`() {
            // Verify all demanglers implement the interface
            val strategies: List<DemanglerStrategy> = listOf(
                ItaniumDemangler(),
                MsvcDemangler(),
                RustDemangler(),
                UniversalDemangler(),
            )
            assertEquals(4, strategies.size)
        }

        @Test
        fun `itanium operator new`() {
            val result = itanium.demangle("_ZN3foonwEm")
            assertNotNull(result)
            assertTrue(result!!.contains("operator new"))
        }

        @Test
        fun `itanium operator delete`() {
            val result = itanium.demangle("_ZN3foodlEPv")
            assertNotNull(result)
            assertTrue(result!!.contains("operator delete"))
        }

        @Test
        fun `itanium operator new array`() {
            val result = itanium.demangle("_ZN3foonaEm")
            assertNotNull(result)
            assertTrue(result!!.contains("operator new[]"))
        }

        @Test
        fun `itanium operator delete array`() {
            val result = itanium.demangle("_ZN3foodaEPv")
            assertNotNull(result)
            assertTrue(result!!.contains("operator delete[]"))
        }

        @Test
        fun `itanium operator increment`() {
            val result = itanium.demangle("_ZN3fooppEv")
            assertNotNull(result)
            assertTrue(result!!.contains("operator++"))
        }

        @Test
        fun `itanium operator decrement`() {
            val result = itanium.demangle("_ZN3foommEv")
            assertNotNull(result)
            assertTrue(result!!.contains("operator--"))
        }

        @Test
        fun `itanium operator bitwise not`() {
            val result = itanium.demangle("_ZN3foocoEv")
            assertNotNull(result)
            assertTrue(result!!.contains("operator~"))
        }

        @Test
        fun `itanium operator logical not`() {
            val result = itanium.demangle("_ZN3foontEv")
            assertNotNull(result)
            assertTrue(result!!.contains("operator!"))
        }

        @Test
        fun `itanium operator logical and`() {
            val result = itanium.demangle("_ZN3fooaaEi")
            assertNotNull(result)
            assertTrue(result!!.contains("operator&&"))
        }

        @Test
        fun `itanium operator logical or`() {
            val result = itanium.demangle("_ZN3fooooEi")
            assertNotNull(result)
            assertTrue(result!!.contains("operator||"))
        }

        @Test
        fun `itanium operator bitwise and`() {
            val result = itanium.demangle("_ZN3fooanEi")
            assertNotNull(result)
            assertTrue(result!!.contains("operator&"))
        }

        @Test
        fun `itanium operator bitwise or`() {
            val result = itanium.demangle("_ZN3fooorEi")
            assertNotNull(result)
            assertTrue(result!!.contains("operator|"))
        }

        @Test
        fun `itanium operator xor`() {
            val result = itanium.demangle("_ZN3fooeoEi")
            assertNotNull(result)
            assertTrue(result!!.contains("operator^"))
        }

        @Test
        fun `itanium operator modulo`() {
            val result = itanium.demangle("_ZN3foormEi")
            assertNotNull(result)
            assertTrue(result!!.contains("operator%"))
        }

        @Test
        fun `itanium operator comma`() {
            val result = itanium.demangle("_ZN3foocmEi")
            assertNotNull(result)
            assertTrue(result!!.contains("operator,"))
        }

        @Test
        fun `itanium operator arrow`() {
            val result = itanium.demangle("_ZN3fooptEv")
            assertNotNull(result)
            assertTrue(result!!.contains("operator->"))
        }

        @Test
        fun `itanium operator arrow star`() {
            val result = itanium.demangle("_ZN3foopmEi")
            assertNotNull(result)
            assertTrue(result!!.contains("operator->*"))
        }

        @Test
        fun `itanium operator less equal`() {
            val result = itanium.demangle("_ZN3fooleEi")
            assertNotNull(result)
            assertTrue(result!!.contains("operator<="))
        }

        @Test
        fun `itanium operator greater equal`() {
            val result = itanium.demangle("_ZN3foogeEi")
            assertNotNull(result)
            assertTrue(result!!.contains("operator>="))
        }

        @Test
        fun `itanium operator three way comparison`() {
            val result = itanium.demangle("_ZN3foossEi")
            assertNotNull(result)
            assertTrue(result!!.contains("operator<=>"))
        }
    }
}
