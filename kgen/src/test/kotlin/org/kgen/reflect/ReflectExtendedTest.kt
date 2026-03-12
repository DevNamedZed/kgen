package org.kgen.reflect

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertThrows
import org.kgen.binary.*

class ReflectExtendedTest {

    // =====================================================================
    // TypeRef — extended tests
    // =====================================================================

    @Test
    fun `typeref unsigned primitives`() {
        assertEquals("u8", TypeRef.U8.fullName())
        assertEquals("u16", TypeRef.U16.fullName())
        assertEquals("u32", TypeRef.U32.fullName())
        assertEquals("u64", TypeRef.U64.fullName())
        assertTrue(TypeRef.U8.isPrimitive())
        assertTrue(TypeRef.U32.isPrimitive())
    }

    @Test
    fun `typeref float primitives`() {
        assertEquals("f32", TypeRef.F32.fullName())
        assertEquals("f64", TypeRef.F64.fullName())
        assertTrue(TypeRef.F32.isPrimitive())
    }

    @Test
    fun `typeref pointer to named type`() {
        val ptr = TypeRef.pointerTo(TypeRef.of("System.String"))
        assertTrue(ptr.isPointer())
        assertEquals("System.String*", ptr.fullName())
        assertEquals("System.String", ptr.elementType()!!.fullName())
    }

    @Test
    fun `typeref byRef of named type`() {
        val ref = TypeRef.byRef(TypeRef.of("MyStruct"))
        assertTrue(ref.isByRef())
        assertEquals("MyStruct&", ref.fullName())
    }

    @Test
    fun `typeref array of named type`() {
        val arr = TypeRef.arrayOf(TypeRef.of("System.String"))
        assertTrue(arr.isArray())
        assertEquals("System.String[]", arr.fullName())
    }

    @Test
    fun `typeref generic param is not primitive or void`() {
        val t = TypeRef.genericParam("T")
        assertFalse(t.isPrimitive())
        assertFalse(t.isVoid())
        assertFalse(t.isArray())
        assertFalse(t.isPointer())
        assertTrue(t.isGenericParameter())
    }

    @Test
    fun `typeref pointer to pointer`() {
        val pp = TypeRef.pointerTo(TypeRef.pointerTo(TypeRef.I32))
        assertTrue(pp.isPointer())
        assertTrue(pp.elementType()!!.isPointer())
        assertEquals("i32**", pp.fullName())
    }

    @Test
    fun `typeref void has no element type`() {
        assertNull(TypeRef.VOID.elementType())
        assertFalse(TypeRef.VOID.isArray())
        assertFalse(TypeRef.VOID.isPointer())
    }

    @Test
    fun `typeref named type without namespace`() {
        val ref = TypeRef.of("MyClass")
        assertEquals("MyClass", ref.fullName())
        assertEquals("MyClass", ref.name())
        assertNull(ref.namespace())
    }

    // =====================================================================
    // Signature — extended tests
    // =====================================================================

    @Test
    fun `signature with many params`() {
        val sig = Signature.returning(TypeRef.VOID)
            .param("a", TypeRef.I32)
            .param("b", TypeRef.I64)
            .param("c", TypeRef.F32)
            .param("d", TypeRef.F64)
            .param("e", TypeRef.POINTER)
            .build()
        assertEquals(5, sig.parameterCount())
        assertEquals(TypeRef.VOID, sig.returnType())
        assertEquals("a", sig.parameters()[0].name)
        assertEquals(TypeRef.POINTER, sig.parameterTypes()[4])
    }

    @Test
    fun `signature with pointer return`() {
        val sig = Signature.returning(TypeRef.POINTER)
            .param(TypeRef.I64)
            .build()
        assertEquals(TypeRef.POINTER, sig.returnType())
    }

    @Test
    fun `signature toString with single unnamed param`() {
        val sig = Signature.of(TypeRef.I32, TypeRef.I32)
        assertEquals("(i32) -> i32", sig.toString())
    }

    @Test
    fun `signature ofVoid with no params`() {
        val sig = Signature.ofVoid()
        assertEquals(TypeRef.VOID, sig.returnType())
        assertEquals(0, sig.parameterCount())
    }

    @Test
    fun `signature inequality different return types`() {
        val a = Signature.of(TypeRef.I32, TypeRef.I32)
        val b = Signature.of(TypeRef.I64, TypeRef.I32)
        assertNotEquals(a, b)
    }

    // =====================================================================
    // QualifiedName — extended tests
    // =====================================================================

    @Test
    fun `qualified name segments for cpp`() {
        val name = QualifiedName.parse("std::vector")
        assertEquals(listOf("std", "vector"), name.segments())
    }

    @Test
    fun `qualified name cpp single name`() {
        val name = QualifiedName.parse("main")
        assertEquals("main", name.name())
        assertNull(name.namespace())
        assertFalse(name.isNested())
        assertFalse(name.isGeneric())
    }

    @Test
    fun `qualified name CLR generic arity three`() {
        val name = QualifiedName.parse("System.Tuple`3")
        assertEquals("Tuple`3", name.rawName())
        assertEquals("Tuple", name.name())
        assertEquals(3, name.genericArity())
        assertTrue(name.isGeneric())
    }

    @Test
    fun `qualified name JVM deep package`() {
        val name = QualifiedName.parse("com/example/app/util/Helper")
        assertEquals("Helper", name.name())
        assertEquals("com.example.app.util", name.namespace())
    }

    @Test
    fun `qualified name of with namespace and name`() {
        val name = QualifiedName.of("com.example", "Widget")
        assertEquals("Widget", name.name())
        assertEquals("com.example", name.namespace())
        assertEquals("com.example.Widget", name.fullName())
    }

    @Test
    fun `qualified name matching different separators`() {
        val clr = QualifiedName.parse("System.Collections.Generic.List`1")
        val of = QualifiedName.of("System.Collections.Generic", "List`1")
        assertEquals(clr, of)
        assertEquals(clr, of)
    }

    @Test
    fun `qualified name JVM descriptor for array of objects`() {
        val name = QualifiedName.fromDescriptor("[Ljava/util/List;")
        assertTrue(name.isArray())
        assertEquals("java.util.List[]", name.fullName())
    }

    // =====================================================================
    // Symbol — extended tests
    // =====================================================================

    private fun sym(
        name: String = "test",
        kind: SymbolKind = SymbolKind.FUNCTION,
        binding: SymbolBinding = SymbolBinding.GLOBAL,
        visibility: SymbolVisibility = SymbolVisibility.DEFAULT,
        flags: Set<SymbolFlag> = emptySet(),
        value: Long = 0,
        size: Long = 0,
        section: String? = ".text",
    ): Symbol = Symbol(
        org.kgen.binary.Symbol(name, value, size, section, binding, kind, visibility, flags),
        null,
    )

    @Test
    fun `symbol hidden visibility is not exported`() {
        val s = sym(binding = SymbolBinding.GLOBAL, visibility = SymbolVisibility.HIDDEN)
        assertFalse(s.isExported())
    }

    @Test
    fun `symbol private access flag`() {
        val s = sym(flags = setOf(SymbolFlag.ACC_PRIVATE))
        assertTrue(s.isPrivate())
        assertFalse(s.isPublic())
        assertFalse(s.isProtected())
    }

    @Test
    fun `symbol protected access flag`() {
        val s = sym(flags = setOf(SymbolFlag.ACC_PROTECTED))
        assertTrue(s.isProtected())
        assertFalse(s.isPublic())
    }

    @Test
    fun `symbol synthetic and deprecated flags`() {
        val s = sym(flags = setOf(SymbolFlag.SYNTHETIC, SymbolFlag.DEPRECATED))
        assertTrue(s.isSynthetic())
        assertTrue(s.isDeprecated())
    }

    @Test
    fun `symbol data kind has no function`() {
        val s = sym(kind = SymbolKind.DATA)
        assertNull(s.function())
        assertFalse(s.isFunction())
        assertTrue(s.isData())
    }

    @Test
    fun `symbol weak binding`() {
        val s = sym(binding = SymbolBinding.WEAK)
        assertTrue(s.isWeak())
        assertFalse(s.isLocal())
        assertFalse(s.isGlobal())
    }

    // =====================================================================
    // Function — extended tests
    // =====================================================================

    private fun func(
        name: String = "test",
        kind: SymbolKind = SymbolKind.FUNCTION,
        sig: Signature? = null,
        value: Long = 0,
        size: Long = 0,
    ): Function {
        val raw = org.kgen.binary.Symbol(name, value, size, ".text", SymbolBinding.GLOBAL, kind)
        return Function(Symbol(raw, null), sig)
    }

    @Test
    fun `function with complex signature`() {
        val sig = Signature.returning(TypeRef.POINTER)
            .param("buf", TypeRef.POINTER)
            .param("size", TypeRef.I64)
            .param("count", TypeRef.I64)
            .build()
        val f = func(name = "memcpy").withSignature(sig)
        assertTrue(f.hasSignature())
        assertEquals(TypeRef.POINTER, f.returnType())
        assertEquals(3, f.parameterTypes().size)
        assertEquals("buf", f.parameters()[0].name)
    }

    @Test
    fun `function toString with void return`() {
        val sig = Signature.returningVoid()
            .param("ptr", TypeRef.POINTER)
            .build()
        val f = func(name = "free").withSignature(sig)
        assertEquals("free: (ptr: ptr) -> void", f.toString())
    }

    @Test
    fun `function native vs bytecode`() {
        val native = func(kind = SymbolKind.FUNCTION)
        assertTrue(native.isNative())
        assertFalse(native.isBytecode())

        val bytecode = func(kind = SymbolKind.METHOD)
        assertTrue(bytecode.isBytecode())
    }

    @Test
    fun `function equality same symbol`() {
        val raw = org.kgen.binary.Symbol("func", 0x100, 32)
        val sym = Symbol(raw, null)
        val a = Function(sym, Signature.VOID)
        val b = Function(sym, Signature.LONG_TO_LONG)
        assertEquals(a, b)
    }

    @Test
    fun `function qualified name parsing`() {
        val f = func(name = "std.io.print")
        assertEquals("print", f.qualifiedName().name())
        assertEquals("std.io", f.qualifiedName().namespace())
    }

    // =====================================================================
    // Module — extended tests
    // =====================================================================

    private fun testObjectFile(
        format: ObjectFormat = ObjectFormat.ELF,
        arch: Architecture = Architecture.X86_64_LINUX,
        symbols: List<org.kgen.binary.Symbol> = emptyList(),
        sections: List<Section> = emptyList(),
        imports: List<ImportEntry> = emptyList(),
        exports: List<ExportEntry> = emptyList(),
        flags: Set<ObjectFlag> = emptySet(),
        entryPoint: Long? = null,
        dynamicInfo: DynamicLinkInfo? = null,
    ): ObjectFile = ObjectFile(
        format = format,
        arch = arch,
        symbols = symbols,
        sections = sections,
        imports = imports,
        exports = exports,
        relocations = emptyList(),
        metadata = ObjectMetadata(entryPoint = entryPoint, flags = flags),
        dynamicInfo = dynamicInfo,
    )

    @Test
    fun `module with multiple functions and data symbols`() {
        val syms = listOf(
            org.kgen.binary.Symbol("init", 0, 16, ".text", SymbolBinding.GLOBAL, SymbolKind.FUNCTION),
            org.kgen.binary.Symbol("process", 16, 64, ".text", SymbolBinding.GLOBAL, SymbolKind.FUNCTION),
            org.kgen.binary.Symbol("cleanup", 80, 32, ".text", SymbolBinding.GLOBAL, SymbolKind.FUNCTION),
            org.kgen.binary.Symbol("buffer", 0, 1024, ".bss", SymbolBinding.GLOBAL, SymbolKind.DATA),
        )
        val module = Module.fromObjectFile(testObjectFile(symbols = syms))
        assertEquals(3, module.functions().size)
        assertEquals(4, module.symbols().size)
        assertNotNull(module.function("init"))
        assertNotNull(module.function("process"))
        assertNotNull(module.function("cleanup"))
        assertNull(module.function("buffer"))
    }

    @Test
    fun `module WASM format detection`() {
        val module = Module.fromObjectFile(testObjectFile(format = ObjectFormat.WASM_MODULE))
        assertTrue(module.hasWasm())
        assertFalse(module.hasNativeCode())
        assertFalse(module.hasClr())
        assertFalse(module.hasJvm())
    }

    @Test
    fun `module section lookup by kind`() {
        val sections = listOf(
            Section(".text", SectionKind.TEXT, ByteArray(100)),
            Section(".data", SectionKind.DATA, ByteArray(50)),
            Section(".rodata", SectionKind.RODATA, ByteArray(25)),
        )
        val module = Module.fromObjectFile(testObjectFile(sections = sections))
        assertNotNull(module.section(SectionKind.DATA))
        assertNotNull(module.section(SectionKind.RODATA))
        assertEquals(".data", module.section(SectionKind.DATA)!!.name)
    }

    @Test
    fun `module multiple dependencies`() {
        val dyn = DynamicLinkInfo(neededLibraries = listOf("libc.so.6", "libm.so.6", "libpthread.so.0"))
        val module = Module.fromObjectFile(testObjectFile(dynamicInfo = dyn))
        val deps = module.dependencies()
        assertEquals(3, deps.size)
        assertEquals("libc.so.6", deps[0].name())
        assertEquals("libm.so.6", deps[1].name())
        assertEquals("libpthread.so.0", deps[2].name())
    }

    @Test
    fun `module relocatable flag`() {
        val module = Module.fromObjectFile(testObjectFile(flags = setOf(ObjectFlag.RELOCATABLE)))
        assertTrue(module.isRelocatable())
        assertFalse(module.isExecutable())
        assertFalse(module.isSharedLibrary())
    }

    @Test
    fun `module toString includes format and name`() {
        val module = Module.fromObjectFile(
            testObjectFile(format = ObjectFormat.PE_COFF, arch = Architecture.X86_64_WINDOWS),
            "test.exe"
        )
        val str = module.toString()
        assertTrue(str.contains("test.exe"))
        assertTrue(str.contains("PE_COFF"))
    }

    // =====================================================================
    // TypeInfo — extended tests
    // =====================================================================

    @Test
    fun `typeinfo with multiple methods`() {
        val methods = listOf(
            MethodInfo("GetValue", returnType = TypeRef.I32, flags = setOf(MethodFlag.PUBLIC)),
            MethodInfo("SetValue", params = listOf(
                ParameterInfo("value", TypeRef.I32, 0),
            ), flags = setOf(MethodFlag.PUBLIC)),
            MethodInfo("ToString", returnType = TypeRef.of("System.String"),
                flags = setOf(MethodFlag.PUBLIC, MethodFlag.VIRTUAL)),
        )
        val type = TypeInfo.builder("MyType")
            .kind(TypeKind.CLASS)
            .apply { methods.forEach { addMethod(it) } }
            .build()
        assertEquals(3, type.methods().size)
        assertNotNull(type.method("GetValue"))
        assertNotNull(type.method("SetValue"))
        assertNotNull(type.method("ToString"))
    }

    @Test
    fun `typeinfo with fields and properties`() {
        val field = FieldInfo("id", TypeRef.I64, flags = setOf(FieldFlag.PRIVATE))
        val getter = MethodInfo("get_Id", returnType = TypeRef.I64)
        val prop = PropertyInfo("Id", TypeRef.I64, getter = getter)

        val type = TypeInfo.builder("Entity")
            .addField(field)
            .addProperty(prop)
            .build()

        assertEquals(1, type.fields().size)
        assertEquals(1, type.properties().size)
        assertEquals("id", type.field("id")!!.name())
        assertTrue(type.property("Id")!!.isReadOnly())
    }

    @Test
    fun `typeinfo generic with two params`() {
        val type = TypeInfo.builder("System.Collections.Generic.Dictionary`2")
            .addGenericArg(TypeRef.genericParam("TKey"))
            .addGenericArg(TypeRef.genericParam("TValue"))
            .build()

        assertTrue(type.isGeneric())
        assertEquals(2, type.genericArguments().size)
    }

    @Test
    fun `typeinfo interface kind`() {
        val iface = TypeInfo.builder("IComparable")
            .kind(TypeKind.INTERFACE)
            .addFlag(TypeFlag.PUBLIC)
            .addFlag(TypeFlag.ABSTRACT)
            .build()

        assertTrue(iface.isInterface())
        assertTrue(iface.isPublic())
        assertTrue(iface.isAbstract())
        assertFalse(iface.isClass())
    }

    @Test
    fun `typeinfo enum kind`() {
        val enumType = TypeInfo.builder("Color")
            .kind(TypeKind.ENUM)
            .addFlag(TypeFlag.PUBLIC)
            .build()

        assertTrue(enumType.isEnum())
        assertTrue(enumType.isPublic())
        assertFalse(enumType.isStruct())
    }
}
