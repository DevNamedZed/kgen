package org.kgen.binary.mangling

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested

class MsvcManglerTest {

    private val mangler = MsvcMangler()

    @Nested
    inner class MangleFunction {

        @Test
        fun `void function no params cdecl`() {
            assertEquals("?foo@@YAXXZ", mangler.mangleFunction("foo"))
        }

        @Test
        fun `int return no params`() {
            assertEquals("?foo@@YAHXZ", mangler.mangleFunction("foo", returnType = "int"))
        }

        @Test
        fun `void return with int param`() {
            assertEquals("?foo@@YAXH@Z", mangler.mangleFunction("foo", listOf("int")))
        }

        @Test
        fun `int return with int params`() {
            assertEquals("?add@@YAHHH@Z", mangler.mangleFunction("add", listOf("int", "int"), "int"))
        }

        @Test
        fun `double return with double param`() {
            assertEquals("?sqrt@@YANN@Z", mangler.mangleFunction("sqrt", listOf("double"), "double"))
        }

        @Test
        fun `void return with float param`() {
            assertEquals("?process@@YAXM@Z", mangler.mangleFunction("process", listOf("float")))
        }

        @Test
        fun `stdcall calling convention`() {
            val result = mangler.mangleFunction("foo", callingConvention = "stdcall")
            assertTrue(result.contains("G"))
        }

        @Test
        fun `fastcall calling convention`() {
            val result = mangler.mangleFunction("foo", callingConvention = "fastcall")
            assertTrue(result.contains("I"))
        }

        @Test
        fun `all basic return types`() {
            assertEquals("?f@@YAXXZ", mangler.mangleFunction("f", returnType = "void"))
            assertEquals("?f@@YADH@Z", mangler.mangleFunction("f", listOf("int"), "char"))
            assertEquals("?f@@YAFXZ", mangler.mangleFunction("f", returnType = "short"))
            assertEquals("?f@@YAHXZ", mangler.mangleFunction("f", returnType = "int"))
            assertEquals("?f@@YAJXZ", mangler.mangleFunction("f", returnType = "long"))
            assertEquals("?f@@YAMXZ", mangler.mangleFunction("f", returnType = "float"))
            assertEquals("?f@@YANXZ", mangler.mangleFunction("f", returnType = "double"))
            assertEquals("?f@@YAOXZ", mangler.mangleFunction("f", returnType = "long double"))
        }

        @Test
        fun `unsigned types as params`() {
            assertEquals("?f@@YAXE@Z", mangler.mangleFunction("f", listOf("unsigned char")))
            assertEquals("?f@@YAXG@Z", mangler.mangleFunction("f", listOf("unsigned short")))
            assertEquals("?f@@YAXI@Z", mangler.mangleFunction("f", listOf("unsigned int")))
            assertEquals("?f@@YAXK@Z", mangler.mangleFunction("f", listOf("unsigned long")))
        }

        @Test
        fun `pointer parameter`() {
            val result = mangler.mangleFunction("f", listOf("int*"))
            assertTrue(result.contains("PE"))
        }

        @Test
        fun `reference parameter`() {
            val result = mangler.mangleFunction("f", listOf("int&"))
            assertTrue(result.contains("AE"))
        }

        @Test
        fun `bool type`() {
            assertEquals("?f@@YA_NXZ", mangler.mangleFunction("f", returnType = "bool"))
        }

        @Test
        fun `__int64 type`() {
            assertEquals("?f@@YA_JXZ", mangler.mangleFunction("f", returnType = "__int64"))
        }

        @Test
        fun `long long maps to __int64`() {
            assertEquals("?f@@YA_JXZ", mangler.mangleFunction("f", returnType = "long long"))
        }

        @Test
        fun `wchar_t type`() {
            assertEquals("?f@@YA_WXZ", mangler.mangleFunction("f", returnType = "wchar_t"))
        }

        @Test
        fun `multiple mixed params`() {
            val result = mangler.mangleFunction("calc", listOf("int", "double", "float"), "int")
            assertEquals("?calc@@YAHHNM@Z", result)
        }
    }

    @Nested
    inner class MangleMethod {

        @Test
        fun `public instance method`() {
            val result = mangler.mangleMethod("MyClass", "method", returnType = "int")
            assertTrue(result.startsWith("?method@MyClass@@"))
            assertTrue(result.contains("Q")) // public
            assertTrue(result.contains("E")) // thiscall
        }

        @Test
        fun `private instance method`() {
            val result = mangler.mangleMethod("MyClass", "method", access = "private")
            assertTrue(result.contains("A")) // private
        }

        @Test
        fun `protected instance method`() {
            val result = mangler.mangleMethod("MyClass", "method", access = "protected")
            assertTrue(result.contains("I")) // protected
        }

        @Test
        fun `public static method`() {
            val result = mangler.mangleMethod("MyClass", "method", isStatic = true)
            assertTrue(result.contains("S")) // public static
        }

        @Test
        fun `private static method`() {
            val result = mangler.mangleMethod("MyClass", "method", access = "private", isStatic = true)
            assertTrue(result.contains("C")) // private static
        }

        @Test
        fun `protected static method`() {
            val result = mangler.mangleMethod("MyClass", "method", access = "protected", isStatic = true)
            assertTrue(result.contains("K")) // protected static
        }

        @Test
        fun `method with params`() {
            val result = mangler.mangleMethod("Vec", "push", listOf("int"), "void")
            assertTrue(result.startsWith("?push@Vec@@"))
        }

        @Test
        fun `nested class method`() {
            val result = mangler.mangleMethod("Outer::Inner", "func")
            assertTrue(result.contains("Inner@"))
            assertTrue(result.contains("Outer@"))
        }

        @Test
        fun `cdecl calling convention`() {
            val result = mangler.mangleMethod("C", "m", callingConvention = "cdecl")
            assertTrue(result.contains("A")) // cdecl code
        }
    }

    @Nested
    inner class MangleVariable {

        @Test
        fun `simple global variable`() {
            val result = mangler.mangleVariable("count", "int")
            assertEquals("?count@@3HA", result)
        }

        @Test
        fun `double variable`() {
            val result = mangler.mangleVariable("pi", "double")
            assertEquals("?pi@@3NA", result)
        }

        @Test
        fun `pointer variable`() {
            val result = mangler.mangleVariable("ptr", "int*")
            assertTrue(result.startsWith("?ptr@@3"))
        }
    }

    @Nested
    inner class EncodeType {

        @Test
        fun `const type appends B`() {
            val result = mangler.encodeType("const int")
            assertEquals("HB", result)
        }

        @Test
        fun `user defined type`() {
            val result = mangler.encodeType("MyClass")
            assertEquals("VMyClass@@", result)
        }

        @Test
        fun `qualified user type`() {
            val result = mangler.encodeType("std::vector")
            assertTrue(result.contains("Vstd@"))
            assertTrue(result.contains("vector@"))
        }

        @Test
        fun `ir type i32 maps to int`() {
            assertEquals("H", mangler.encodeType("i32"))
        }

        @Test
        fun `ir type i64 maps to __int64`() {
            assertEquals("_J", mangler.encodeType("i64"))
        }

        @Test
        fun `ir type u8 maps to unsigned char`() {
            assertEquals("E", mangler.encodeType("u8"))
        }

        @Test
        fun `ir type u16 maps to unsigned short`() {
            assertEquals("G", mangler.encodeType("u16"))
        }

        @Test
        fun `ir type u32 maps to unsigned int`() {
            assertEquals("I", mangler.encodeType("u32"))
        }

        @Test
        fun `ir type u64 maps to unsigned __int64`() {
            assertEquals("_K", mangler.encodeType("u64"))
        }

        @Test
        fun `ir type f32 maps to float`() {
            assertEquals("M", mangler.encodeType("f32"))
        }

        @Test
        fun `ir type f64 maps to double`() {
            assertEquals("N", mangler.encodeType("f64"))
        }

        @Test
        fun `signed char`() {
            assertEquals("C", mangler.encodeType("signed char"))
        }

        @Test
        fun `unsigned long long maps to __int64 unsigned`() {
            assertEquals("_K", mangler.encodeType("unsigned long long"))
        }

        @Test
        fun `unknown unsigned falls back to user type`() {
            val result = mangler.encodeType("unsigned Widget")
            assertTrue(result.startsWith("V"))
        }
    }

    @Nested
    inner class RoundTrip {

        private val demangler = MsvcDemangler()

        @Test
        fun `simple void function roundtrips`() {
            val mangled = mangler.mangleFunction("foo")
            val result = demangler.demangle(mangled)
            assertNotNull(result)
            assertTrue(result!!.contains("foo"))
        }

        @Test
        fun `function with int return roundtrips`() {
            val mangled = mangler.mangleFunction("bar", returnType = "int")
            val result = demangler.demangle(mangled)
            assertNotNull(result)
            assertTrue(result!!.contains("bar"))
        }

        @Test
        fun `method roundtrips`() {
            val mangled = mangler.mangleMethod("MyClass", "doWork", returnType = "int")
            val result = demangler.demangle(mangled)
            assertNotNull(result)
            assertTrue(result!!.contains("MyClass"))
            assertTrue(result.contains("doWork"))
        }
    }
}
