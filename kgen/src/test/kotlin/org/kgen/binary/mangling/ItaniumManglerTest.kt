package org.kgen.binary.mangling

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested

class ItaniumManglerTest {

    private val mangler = ItaniumMangler()

    @Nested
    inner class MangleSimple {

        @Test
        fun `void function no parameters`() {
            assertEquals("_Z3foov", mangler.mangleSimple("foo"))
        }

        @Test
        fun `function with int parameter`() {
            assertEquals("_Z3fooi", mangler.mangleSimple("foo", listOf("int")))
        }

        @Test
        fun `function with multiple parameters`() {
            assertEquals("_Z3fooid", mangler.mangleSimple("foo", listOf("int", "double")))
        }

        @Test
        fun `function with all builtin types`() {
            assertEquals("_Z1fv", mangler.mangleSimple("f", listOf("void")))
            assertEquals("_Z1fb", mangler.mangleSimple("f", listOf("bool")))
            assertEquals("_Z1fc", mangler.mangleSimple("f", listOf("char")))
            assertEquals("_Z1fs", mangler.mangleSimple("f", listOf("short")))
            assertEquals("_Z1fi", mangler.mangleSimple("f", listOf("int")))
            assertEquals("_Z1fl", mangler.mangleSimple("f", listOf("long")))
            assertEquals("_Z1fx", mangler.mangleSimple("f", listOf("long long")))
            assertEquals("_Z1ff", mangler.mangleSimple("f", listOf("float")))
            assertEquals("_Z1fd", mangler.mangleSimple("f", listOf("double")))
            assertEquals("_Z1fe", mangler.mangleSimple("f", listOf("long double")))
            assertEquals("_Z1fw", mangler.mangleSimple("f", listOf("wchar_t")))
        }

        @Test
        fun `function with signed char`() {
            assertEquals("_Z1fa", mangler.mangleSimple("f", listOf("signed char")))
        }

        @Test
        fun `function with __int128`() {
            assertEquals("_Z1fn", mangler.mangleSimple("f", listOf("__int128")))
        }

        @Test
        fun `function with __float128`() {
            assertEquals("_Z1fg", mangler.mangleSimple("f", listOf("__float128")))
        }

        @Test
        fun `function with variadic`() {
            assertEquals("_Z1fiz", mangler.mangleSimple("f", listOf("int", "...")))
        }

        @Test
        fun `single char name`() {
            assertEquals("_Z1fv", mangler.mangleSimple("f"))
        }

        @Test
        fun `long function name`() {
            assertEquals("_Z16longFunctionNamev", mangler.mangleSimple("longFunctionName"))
        }

        @Test
        fun `empty param list produces void`() {
            val result = mangler.mangleSimple("foo", emptyList())
            assertEquals("_Z3foov", result)
        }

        @Test
        fun `three parameters`() {
            assertEquals("_Z3fooidf", mangler.mangleSimple("foo", listOf("int", "double", "float")))
        }
    }

    @Nested
    inner class MangleFunction {

        @Test
        fun `single namespace`() {
            assertEquals("_ZN3std4sortEv", mangler.mangleFunction("std", name = "sort"))
        }

        @Test
        fun `two namespaces`() {
            assertEquals("_ZN3std6vector9push_backEi", mangler.mangleFunction("std", "vector", name = "push_back", paramTypes = listOf("int")))
        }

        @Test
        fun `no qualifiers falls back to simple mangling`() {
            assertEquals("_Z3foov", mangler.mangleFunction(name = "foo"))
        }

        @Test
        fun `namespace with parameters`() {
            assertEquals("_ZN5outer5inner4funcEid", mangler.mangleFunction("outer", "inner", name = "func", paramTypes = listOf("int", "double")))
        }

        @Test
        fun `single qualifier no params`() {
            assertEquals("_ZN7MyClass6methodEv", mangler.mangleFunction("MyClass", name = "method"))
        }
    }

    @Nested
    inner class MangleConstructor {

        @Test
        fun `complete constructor C1 no params`() {
            assertEquals("_ZN3FooC1Ev", mangler.mangleConstructor("Foo"))
        }

        @Test
        fun `base constructor C2 no params`() {
            assertEquals("_ZN3FooC2Ev", mangler.mangleConstructor("Foo", kind = 2))
        }

        @Test
        fun `constructor with int param`() {
            assertEquals("_ZN3FooC1Ei", mangler.mangleConstructor("Foo", paramTypes = listOf("int")))
        }

        @Test
        fun `constructor with multiple params`() {
            assertEquals("_ZN3FooC1Eid", mangler.mangleConstructor("Foo", paramTypes = listOf("int", "double")))
        }

        @Test
        fun `nested class constructor`() {
            assertEquals("_ZN5Outer5InnerC1Ev", mangler.mangleConstructor("Outer", "Inner"))
        }
    }

    @Nested
    inner class MangleDestructor {

        @Test
        fun `complete destructor D1`() {
            assertEquals("_ZN3FooD1Ev", mangler.mangleDestructor("Foo"))
        }

        @Test
        fun `base destructor D2`() {
            assertEquals("_ZN3FooD2Ev", mangler.mangleDestructor("Foo", kind = 2))
        }

        @Test
        fun `deleting destructor D0`() {
            assertEquals("_ZN3FooD0Ev", mangler.mangleDestructor("Foo", kind = 0))
        }

        @Test
        fun `nested class destructor`() {
            assertEquals("_ZN5Outer5InnerD1Ev", mangler.mangleDestructor("Outer", "Inner"))
        }
    }

    @Nested
    inner class MangleVariable {

        @Test
        fun `simple global variable`() {
            assertEquals("_Z5myVar", mangler.mangleVariable(name = "myVar"))
        }

        @Test
        fun `namespaced variable`() {
            assertEquals("_ZN3foo5myVarE", mangler.mangleVariable("foo", name = "myVar"))
        }

        @Test
        fun `deeply nested variable`() {
            assertEquals("_ZN1a1b1c3varE", mangler.mangleVariable("a", "b", "c", name = "var"))
        }
    }

    @Nested
    inner class EncodeType {

        @Test
        fun `pointer type`() {
            assertEquals("Pi", mangler.encodeType("int*"))
        }

        @Test
        fun `reference type`() {
            assertEquals("Ri", mangler.encodeType("int&"))
        }

        @Test
        fun `const type`() {
            assertEquals("Ki", mangler.encodeType("const int"))
        }

        @Test
        fun `pointer to const`() {
            assertEquals("PKi", mangler.encodeType("const int*"))
        }

        @Test
        fun `unsigned int`() {
            assertEquals("j", mangler.encodeType("unsigned int"))
        }

        @Test
        fun `unsigned char`() {
            assertEquals("h", mangler.encodeType("unsigned char"))
        }

        @Test
        fun `unsigned short`() {
            assertEquals("t", mangler.encodeType("unsigned short"))
        }

        @Test
        fun `unsigned long`() {
            assertEquals("m", mangler.encodeType("unsigned long"))
        }

        @Test
        fun `unsigned long long`() {
            assertEquals("y", mangler.encodeType("unsigned long long"))
        }

        @Test
        fun `unsigned __int128`() {
            assertEquals("o", mangler.encodeType("unsigned __int128"))
        }

        @Test
        fun `user defined type`() {
            assertEquals("6MyType", mangler.encodeType("MyType"))
        }

        @Test
        fun `qualified user type`() {
            assertEquals("N3std6vectorE", mangler.encodeType("std::vector"))
        }

        @Test
        fun `ir type aliases i32 to int`() {
            assertEquals("i", mangler.encodeType("i32"))
        }

        @Test
        fun `ir type aliases u64 to unsigned long long`() {
            assertEquals("y", mangler.encodeType("u64"))
        }

        @Test
        fun `ir type aliases f32 to float`() {
            assertEquals("f", mangler.encodeType("f32"))
        }

        @Test
        fun `ir type aliases f64 to double`() {
            assertEquals("d", mangler.encodeType("f64"))
        }

        @Test
        fun `whitespace is trimmed`() {
            assertEquals("i", mangler.encodeType("  int  "))
        }

        @Test
        fun `char8_t type`() {
            assertEquals("Du", mangler.encodeType("char8_t"))
        }

        @Test
        fun `char16_t type`() {
            assertEquals("Ds", mangler.encodeType("char16_t"))
        }

        @Test
        fun `char32_t type`() {
            assertEquals("Di", mangler.encodeType("char32_t"))
        }

        @Test
        fun `auto type`() {
            assertEquals("Da", mangler.encodeType("auto"))
        }

        @Test
        fun `decltype auto`() {
            assertEquals("Dc", mangler.encodeType("decltype(auto)"))
        }

        @Test
        fun `nullptr_t`() {
            assertEquals("Dn", mangler.encodeType("std::nullptr_t"))
        }
    }

    @Nested
    inner class RoundTrip {

        private val demangler = ItaniumDemangler()

        @Test
        fun `simple void function roundtrips`() {
            val mangled = mangler.mangleSimple("foo")
            assertEquals("foo()", demangler.demangle(mangled))
        }

        @Test
        fun `function with int param roundtrips`() {
            val mangled = mangler.mangleSimple("bar", listOf("int"))
            assertEquals("bar(int)", demangler.demangle(mangled))
        }

        @Test
        fun `function with multiple params roundtrips`() {
            val mangled = mangler.mangleSimple("calc", listOf("int", "double", "float"))
            assertEquals("calc(int, double, float)", demangler.demangle(mangled))
        }

        @Test
        fun `namespaced function roundtrips`() {
            val mangled = mangler.mangleFunction("ns", name = "func")
            val result = demangler.demangle(mangled)
            assertNotNull(result)
            assertTrue(result!!.contains("ns::func"))
        }

        @Test
        fun `constructor roundtrips`() {
            val mangled = mangler.mangleConstructor("Foo")
            val result = demangler.demangle(mangled)
            assertNotNull(result)
            assertTrue(result!!.contains("Foo"))
        }

        @Test
        fun `destructor roundtrips`() {
            val mangled = mangler.mangleDestructor("Foo")
            val result = demangler.demangle(mangled)
            assertNotNull(result)
            assertTrue(result!!.contains("~"))
            assertTrue(result.contains("Foo"))
        }
    }
}
