package org.kgen.binary.mangling

import org.junit.jupiter.api.Test
import org.kgen.codegen.CodeGenOptions
import org.kgen.ir.Type
import org.kgen.reflect.Signature
import org.kgen.reflect.TypeRef
import org.kgen.reflect.emit.NativeCodeBuilder
import org.kgen.ir.target.Target
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ManglingIntegrationTest {

    @Test
    fun symbolManglingItaniumSimple() {
        val mangled = SymbolMangling.mangleFunction("add", listOf(Type.I32, Type.I32), Type.I32, ManglingScheme.ITANIUM)
        assertEquals("_Z3addii", mangled)
    }

    @Test
    fun symbolManglingItaniumNoParams() {
        val mangled = SymbolMangling.mangleFunction("init", emptyList(), Type.Void, ManglingScheme.ITANIUM)
        assertEquals("_Z4initv", mangled)
    }

    @Test
    fun symbolManglingMsvcSimple() {
        val mangled = SymbolMangling.mangleFunction("add", listOf(Type.I32, Type.I32), Type.I32, ManglingScheme.MSVC)
        assertEquals("?add@@YAHHH@Z", mangled)
    }

    @Test
    fun symbolManglingMsvcVoidReturn() {
        val mangled = SymbolMangling.mangleFunction("run", emptyList(), Type.Void, ManglingScheme.MSVC)
        assertEquals("?run@@YAXXZ", mangled)
    }

    @Test
    fun symbolManglingQualifiedItanium() {
        val mangled = SymbolMangling.mangleQualifiedFunction(
            listOf("math"), "add", listOf(Type.I64, Type.I64), Type.I64, ManglingScheme.ITANIUM
        )
        assertEquals("_ZN4math3addExx", mangled)
    }

    @Test
    fun symbolManglingUnsupportedSchemePassesThrough() {
        val mangled = SymbolMangling.mangleFunction("foo", listOf(Type.I32), Type.I32, ManglingScheme.RUST)
        assertEquals("foo", mangled)
    }

    @Test
    fun codeGenOptionsManglingSchemeDefault() {
        val opts = CodeGenOptions()
        assertNull(opts.manglingScheme)
    }

    @Test
    fun codeGenOptionsManglingSchemeSet() {
        val opts = CodeGenOptions(manglingScheme = ManglingScheme.ITANIUM)
        assertEquals(ManglingScheme.ITANIUM, opts.manglingScheme)
    }

    @Test
    fun nativeCodeBuilderWithManglingProducesMangledSymbols() {
        val builder = NativeCodeBuilder.forTarget(Target.x86_64())
            .withMangling(ManglingScheme.ITANIUM)
            .function("add", Signature.of(TypeRef.I64, TypeRef.I64, TypeRef.I64))
            .returnDefault()

        val bytes = builder.compileBytes()
        assertTrue(bytes.isNotEmpty(), "Should produce compiled bytes with mangling enabled")
    }

    @Test
    fun typeToItaniumMappings() {
        assertEquals("int", SymbolMangling.toItaniumType(Type.I32))
        assertEquals("long long", SymbolMangling.toItaniumType(Type.I64))
        assertEquals("float", SymbolMangling.toItaniumType(Type.F32))
        assertEquals("double", SymbolMangling.toItaniumType(Type.F64))
        assertEquals("void", SymbolMangling.toItaniumType(Type.Void))
        assertEquals("bool", SymbolMangling.toItaniumType(Type.I1))
        assertEquals("void*", SymbolMangling.toItaniumType(Type.OpaquePointer))
    }

    @Test
    fun typeToMsvcMappings() {
        assertEquals("int", SymbolMangling.toMsvcType(Type.I32))
        assertEquals("__int64", SymbolMangling.toMsvcType(Type.I64))
        assertEquals("float", SymbolMangling.toMsvcType(Type.F32))
        assertEquals("double", SymbolMangling.toMsvcType(Type.F64))
        assertEquals("void", SymbolMangling.toMsvcType(Type.Void))
        assertEquals("bool", SymbolMangling.toMsvcType(Type.I1))
    }
}
