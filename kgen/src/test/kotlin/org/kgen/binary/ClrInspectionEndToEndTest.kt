package org.kgen.binary

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.binary.pe.MixedModeAssembler
import org.kgen.binary.pe.PeReader
import org.kgen.binary.pe.clr.*
import org.kgen.reflect.Module
import org.kgen.target.clr.*
import org.kgen.target.clr.asm.CilAssembler
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * End-to-end tests: build a CLR assembly using CilClassBuilder and MixedModeAssembler,
 * then read it back via PeReader and the reflect Module API to inspect types, methods,
 * fields, and metadata tables.
 */
class ClrInspectionEndToEndTest {

    @Test
    fun `build CLR assembly with types then read back and enumerate type defs`() {
        val builder = CilClassBuilder("InspectAsm", "MyApp.Calculator")
        builder.method("Add", CilClassBuilder.sig(CilSigType.I4, CilSigType.I4, CilSigType.I4),
            CilMethodFlags.PUBLIC or CilMethodFlags.STATIC
        ) { code ->
            code.ldarg(0)
            code.ldarg(1)
            code.add()
            code.ret()
        }
        val meta = builder.build()

        // Verify type defs: <Module> + Calculator
        assertEquals(2, meta.tables.typeDefs.size)
        assertEquals("<Module>", meta.strings.get(meta.tables.typeDefs[0].name))
        assertEquals("Calculator", meta.strings.get(meta.tables.typeDefs[1].name))
        assertEquals("MyApp", meta.strings.get(meta.tables.typeDefs[1].namespace))

        // Round-trip through bytes and re-parse
        val bytes = builder.toBytes()
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val parsed = ClrTableParser(buf, bytes).parse(0, bytes.size, 2, 5,
            ClrMetadata.COR_FLAGS_ILONLY, 0)!!

        assertEquals(2, parsed.tables.typeDefs.size)
        assertEquals("Calculator", parsed.strings.get(parsed.tables.typeDefs[1].name))
        assertEquals("MyApp", parsed.strings.get(parsed.tables.typeDefs[1].namespace))
    }

    @Test
    fun `build assembly with multiple methods then inspect method signatures`() {
        val builder = CilClassBuilder("MethodInspect", "MathLib.Operations")

        builder.method("Add", CilClassBuilder.sig(CilSigType.I4, CilSigType.I4, CilSigType.I4),
            CilMethodFlags.PUBLIC or CilMethodFlags.STATIC or CilMethodFlags.HIDE_BY_SIG
        ) { code ->
            code.ldarg(0); code.ldarg(1); code.add(); code.ret()
        }

        builder.method("Negate", CilClassBuilder.sig(CilSigType.I4, CilSigType.I4),
            CilMethodFlags.PUBLIC or CilMethodFlags.STATIC or CilMethodFlags.HIDE_BY_SIG
        ) { code ->
            code.ldarg(0); code.neg(); code.ret()
        }

        builder.method("NoOp", CilClassBuilder.sig(CilSigType.VOID),
            CilMethodFlags.PUBLIC or CilMethodFlags.STATIC
        ) { code ->
            code.nop(); code.ret()
        }

        val bytes = builder.toBytes()
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val parsed = ClrTableParser(buf, bytes).parse(0, bytes.size, 2, 5,
            ClrMetadata.COR_FLAGS_ILONLY, 0)!!

        assertEquals(3, parsed.tables.methodDefs.size)
        assertEquals("Add", parsed.strings.get(parsed.tables.methodDefs[0].name))
        assertEquals("Negate", parsed.strings.get(parsed.tables.methodDefs[1].name))
        assertEquals("NoOp", parsed.strings.get(parsed.tables.methodDefs[2].name))

        // Verify Add has public+static+hidebysig flags
        val addFlags = parsed.tables.methodDefs[0].flags
        assertTrue(addFlags and CilMethodFlags.PUBLIC != 0, "Add should be public")
        assertTrue(addFlags and CilMethodFlags.STATIC != 0, "Add should be static")
        assertTrue(addFlags and CilMethodFlags.HIDE_BY_SIG != 0, "Add should be hidebysig")

        // Verify signature blobs are present and non-empty
        for (method in parsed.tables.methodDefs) {
            val sigBytes = parsed.blobs.get(method.signature)
            assertTrue(sigBytes.isNotEmpty(), "Method '${parsed.strings.get(method.name)}' should have a signature blob")
        }
    }

    @Test
    fun `build assembly with fields then inspect field types and flags`() {
        val builder = CilClassBuilder("FieldInspect", "Models.Person")
        builder.field("age", CilSigType.I4, CilFieldFlags.PRIVATE)
        builder.field("name", CilSigType.STRING, CilFieldFlags.PUBLIC)
        builder.field("id", CilSigType.I8, CilFieldFlags.PRIVATE or CilFieldFlags.INIT_ONLY)
        builder.field("active", CilSigType.BOOLEAN, CilFieldFlags.PUBLIC or CilFieldFlags.STATIC)

        val bytes = builder.toBytes()
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val parsed = ClrTableParser(buf, bytes).parse(0, bytes.size, 2, 5,
            ClrMetadata.COR_FLAGS_ILONLY, 0)!!

        assertEquals(4, parsed.tables.fields.size)

        assertEquals("age", parsed.strings.get(parsed.tables.fields[0].name))
        assertEquals(CilFieldFlags.PRIVATE, parsed.tables.fields[0].flags and 0x07)

        assertEquals("name", parsed.strings.get(parsed.tables.fields[1].name))
        assertEquals(CilFieldFlags.PUBLIC, parsed.tables.fields[1].flags and 0x07)

        assertEquals("id", parsed.strings.get(parsed.tables.fields[2].name))
        assertTrue(parsed.tables.fields[2].flags and CilFieldFlags.INIT_ONLY != 0, "id should be initonly")

        assertEquals("active", parsed.strings.get(parsed.tables.fields[3].name))
        assertTrue(parsed.tables.fields[3].flags and CilFieldFlags.STATIC != 0, "active should be static")
    }

    @Test
    fun `build PE with CLR metadata then read via Module reflect API`() {
        val classBuilder = CilClassBuilder("ReflectAsm", "ReflectDemo.Greeter")
        classBuilder.method("Greet", CilClassBuilder.sig(CilSigType.VOID),
            CilMethodFlags.PUBLIC or CilMethodFlags.STATIC
        ) { code ->
            code.nop()
            code.ret()
        }
        classBuilder.field("message", CilSigType.STRING, CilFieldFlags.PRIVATE)

        val metadataBytes = classBuilder.toBytes()
        val asm = MixedModeAssembler("ReflectAsm")
        asm.setClrMetadata(metadataBytes)
        val peBytes = asm.assemble()

        // Read via PeReader to verify it's a valid PE
        val pe = PeReader.read(peBytes)
        assertTrue(pe.isPe, "Should be a valid PE file")

        // Read via reflect Module API
        val module = Module.fromBytes(peBytes, "ReflectAsm.dll")
        assertNotNull(module)
        assertEquals("ReflectAsm.dll", module.name())

        // Should have sections (at minimum .cil for CLR metadata)
        val sections = module.sections()
        assertTrue(sections.isNotEmpty(), "PE should have sections")
    }

    @Test
    fun `inspect CLR metadata tables directly after round-trip`() {
        val builder = CilClassBuilder("TableInspect", "Data.Repository")

        builder.field("connectionString", CilSigType.STRING, CilFieldFlags.PRIVATE)
        builder.field("timeout", CilSigType.I4, CilFieldFlags.PRIVATE)

        builder.method("Connect", CilClassBuilder.sig(CilSigType.VOID),
            CilMethodFlags.PUBLIC or CilMethodFlags.STATIC
        ) { code ->
            code.nop(); code.ret()
        }

        builder.method("Query", CilClassBuilder.sig(CilSigType.I4, CilSigType.I4),
            CilMethodFlags.PUBLIC or CilMethodFlags.STATIC
        ) { code ->
            code.ldarg(0); code.ret()
        }

        val meta = builder.build()

        // Module table
        assertEquals(1, meta.tables.modules.size)
        assertEquals("TableInspect.dll", meta.strings.get(meta.tables.modules[0].name))

        // Assembly table
        assertEquals(1, meta.tables.assemblies.size)
        assertEquals("TableInspect", meta.strings.get(meta.tables.assemblies[0].name))
        assertEquals(0x8004, meta.tables.assemblies[0].hashAlgId)

        // TypeDef table: <Module> + Repository
        assertEquals(2, meta.tables.typeDefs.size)
        val classDef = meta.tables.typeDefs[1]
        assertEquals("Repository", meta.strings.get(classDef.name))
        assertEquals("Data", meta.strings.get(classDef.namespace))

        // MethodDef table
        assertEquals(2, meta.tables.methodDefs.size)
        assertEquals("Connect", meta.strings.get(meta.tables.methodDefs[0].name))
        assertEquals("Query", meta.strings.get(meta.tables.methodDefs[1].name))

        // Field table
        assertEquals(2, meta.tables.fields.size)
        assertEquals("connectionString", meta.strings.get(meta.tables.fields[0].name))
        assertEquals("timeout", meta.strings.get(meta.tables.fields[1].name))

        // TypeRef table should have System.Object (added by default)
        assertEquals(1, meta.tables.typeRefs.size)
        assertEquals("Object", meta.strings.get(meta.tables.typeRefs[0].name))
        assertEquals("System", meta.strings.get(meta.tables.typeRefs[0].namespace))

        // AssemblyRef table should have mscorlib (added by default)
        assertEquals(1, meta.tables.assemblyRefs.size)
        assertEquals("mscorlib", meta.strings.get(meta.tables.assemblyRefs[0].name))
    }

    @Test
    fun `build assembly with entry point and P-Invoke then inspect all metadata`() {
        val builder = CilClassBuilder("FullInspect", "App.Program")

        builder.method("Main", CilClassBuilder.sig(CilSigType.VOID),
            CilMethodFlags.PUBLIC or CilMethodFlags.STATIC
        ) { code ->
            code.nop(); code.ret()
        }
        builder.entryPoint(0)

        builder.pinvoke("kernel32.dll", "GetTickCount",
            signature = CilClassBuilder.sig(CilSigType.U4),
            flags = CilMethodFlags.PUBLIC or CilMethodFlags.STATIC)

        builder.field("counter", CilSigType.I4, CilFieldFlags.PRIVATE or CilFieldFlags.STATIC)

        val meta = builder.build()

        // Entry point should be set to first method (MethodDef token 0x06000001)
        assertEquals(0x06000001, meta.entryPointToken)

        // Two methods: Main and GetTickCount
        assertEquals(2, meta.tables.methodDefs.size)
        assertEquals("Main", meta.strings.get(meta.tables.methodDefs[0].name))
        assertEquals("GetTickCount", meta.strings.get(meta.tables.methodDefs[1].name))

        // GetTickCount should have PInvokeImpl flag
        assertTrue(meta.tables.methodDefs[1].flags and 0x2000 != 0, "P/Invoke method needs PInvokeImpl flag")

        // ImplMap and ModuleRef tables for P/Invoke
        assertEquals(1, meta.tables.implMaps.size)
        assertEquals("GetTickCount", meta.strings.get(meta.tables.implMaps[0].importName))
        assertEquals(1, meta.tables.moduleRefs.size)
        assertEquals("kernel32.dll", meta.strings.get(meta.tables.moduleRefs[0].name))

        // Field table
        assertEquals(1, meta.tables.fields.size)
        val field = meta.tables.fields[0]
        assertEquals("counter", meta.strings.get(field.name))
        assertTrue(field.flags and CilFieldFlags.STATIC != 0, "counter should be static")

        // Metadata version and flags
        assertTrue(meta.isILOnly, "Should be IL-only assembly")
        assertEquals("v4.0.30319", meta.metadataVersion)

        // Round-trip to bytes and back
        val bytes = builder.toBytes()
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val reparsed = ClrTableParser(buf, bytes).parse(0, bytes.size, 2, 5,
            ClrMetadata.COR_FLAGS_ILONLY, meta.entryPointToken)!!

        assertEquals(2, reparsed.tables.methodDefs.size)
        assertEquals(1, reparsed.tables.implMaps.size)
        assertEquals(1, reparsed.tables.moduleRefs.size)
        assertEquals("Main", reparsed.strings.get(reparsed.tables.methodDefs[0].name))
    }

    @Test
    fun `build assembly with member refs and type refs for external calls`() {
        val builder = CilClassBuilder("RefInspect", "Lib.Caller")

        // Add a type ref to System.Console (in a second assembly ref)
        builder.addAssemblyRef("System.Console", 6, 0, 0, 0)
        val consoleTypeRef = builder.addTypeRef(2, "Console", "System")

        // Add a member ref to Console.WriteLine(string)
        val writeLineSig = CilClassBuilder.sig(CilSigType.VOID, CilSigType.STRING)
        builder.addMemberRef(consoleTypeRef, "WriteLine", writeLineSig)

        builder.method("Run", CilClassBuilder.sig(CilSigType.VOID),
            CilMethodFlags.PUBLIC or CilMethodFlags.STATIC
        ) { code ->
            code.nop(); code.ret()
        }

        val meta = builder.build()

        // Should have 2 assembly refs: mscorlib (default) + System.Console
        assertEquals(2, meta.tables.assemblyRefs.size)
        assertEquals("mscorlib", meta.strings.get(meta.tables.assemblyRefs[0].name))
        assertEquals("System.Console", meta.strings.get(meta.tables.assemblyRefs[1].name))

        // Should have 2 type refs: System.Object (default) + System.Console
        assertEquals(2, meta.tables.typeRefs.size)
        assertEquals("Object", meta.strings.get(meta.tables.typeRefs[0].name))
        assertEquals("Console", meta.strings.get(meta.tables.typeRefs[1].name))
        assertEquals("System", meta.strings.get(meta.tables.typeRefs[1].namespace))

        // Should have 1 member ref: Console.WriteLine
        assertEquals(1, meta.tables.memberRefs.size)
        assertEquals("WriteLine", meta.strings.get(meta.tables.memberRefs[0].name))

        // Round-trip
        val bytes = builder.toBytes()
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val reparsed = ClrTableParser(buf, bytes).parse(0, bytes.size, 2, 5,
            ClrMetadata.COR_FLAGS_ILONLY, 0)!!

        assertEquals(2, reparsed.tables.assemblyRefs.size)
        assertEquals(2, reparsed.tables.typeRefs.size)
        assertEquals(1, reparsed.tables.memberRefs.size)
        assertEquals("WriteLine", reparsed.strings.get(reparsed.tables.memberRefs[0].name))
    }
}
