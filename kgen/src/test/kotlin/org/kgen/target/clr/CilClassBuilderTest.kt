package org.kgen.target.clr

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.binary.pe.clr.ClrMetadata
import org.kgen.binary.pe.clr.ClrTableParser
import java.nio.ByteBuffer
import java.nio.ByteOrder

class CilClassBuilderTest {

    @Test
    fun buildEmptyClass() {
        val builder = CilClassBuilder("TestAssembly", "TestNamespace.MyClass")
        val meta = builder.build()

        assertEquals(1, meta.tables.assemblies.size)
        assertEquals("TestAssembly", meta.strings.get(meta.tables.assemblies[0].name))

        // Should have <Module> and MyClass
        assertEquals(2, meta.tables.typeDefs.size)
        val myClass = meta.tables.typeDefs[1]
        assertEquals("MyClass", meta.strings.get(myClass.name))
        assertEquals("TestNamespace", meta.strings.get(myClass.namespace))
    }

    @Test
    fun buildClassWithMethod() {
        val builder = CilClassBuilder("TestAsm", "MyClass")
        builder.method("Add", CilClassBuilder.sig(CilSigType.I4, CilSigType.I4, CilSigType.I4),
            CilMethodFlags.PUBLIC or CilMethodFlags.STATIC or CilMethodFlags.HIDE_BY_SIG
        ) { code ->
            code.ldarg(0)
            code.ldarg(1)
            code.add()
            code.ret()
        }

        val meta = builder.build()
        assertEquals(1, meta.tables.methodDefs.size)
        assertEquals("Add", meta.strings.get(meta.tables.methodDefs[0].name))
    }

    @Test
    fun buildClassWithField() {
        val builder = CilClassBuilder("TestAsm", "MyClass")
        builder.field("count", CilSigType.I4, CilFieldFlags.PRIVATE)
        builder.field("name", CilSigType.STRING, CilFieldFlags.PUBLIC)

        val meta = builder.build()
        assertEquals(2, meta.tables.fields.size)
        assertEquals("count", meta.strings.get(meta.tables.fields[0].name))
        assertEquals("name", meta.strings.get(meta.tables.fields[1].name))
    }

    @Test
    fun buildWithEntryPoint() {
        val builder = CilClassBuilder("TestAsm", "Program")
        builder.method("Main", CilClassBuilder.sig(CilSigType.VOID),
            CilMethodFlags.PUBLIC or CilMethodFlags.STATIC
        ) { code ->
            code.ret()
        }
        builder.entryPoint(0)

        val meta = builder.build()
        assertEquals(0x06000001, meta.entryPointToken)
    }

    @Test
    fun toBytesRoundTrips() {
        val builder = CilClassBuilder("RoundTrip", "RoundTrip.HelloClass")
        builder.method("DoNothing", CilClassBuilder.sig(CilSigType.VOID),
            CilMethodFlags.PUBLIC or CilMethodFlags.STATIC
        ) { code ->
            code.nop()
            code.ret()
        }

        val bytes = builder.toBytes()
        assertTrue(bytes.isNotEmpty())

        // Verify BSJB signature
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(0x424A5342, buf.getInt(0))

        // Round-trip parse
        val parsed = ClrTableParser(buf, bytes).parse(0, bytes.size, 2, 5,
            ClrMetadata.COR_FLAGS_ILONLY, 0)
        assertNotNull(parsed)
        assertEquals("RoundTrip.dll", parsed!!.strings.get(parsed.tables.modules[0].name))
        assertEquals(1, parsed.tables.methodDefs.size)
        assertEquals("DoNothing", parsed.strings.get(parsed.tables.methodDefs[0].name))
    }

    @Test
    fun sigStaticNoParams() {
        val sig = CilClassBuilder.sig(CilSigType.VOID)
        // 0x00 = DEFAULT, 0x00 = 0 params, 0x01 = void return
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x01), sig)
    }

    @Test
    fun sigStaticWithParams() {
        val sig = CilClassBuilder.sig(CilSigType.I4, CilSigType.I4, CilSigType.I4)
        // 0x00 = DEFAULT, 0x02 = 2 params, 0x08 = I4 return, 0x08 = I4, 0x08 = I4
        assertArrayEquals(byteArrayOf(0x00, 0x02, 0x08, 0x08, 0x08), sig)
    }

    @Test
    fun instanceSig() {
        val sig = CilClassBuilder.instanceSig(CilSigType.VOID)
        // 0x20 = HASTHIS, 0x00 = 0 params, 0x01 = void return
        assertArrayEquals(byteArrayOf(0x20, 0x00, 0x01), sig)
    }

    @Test
    fun addMemberRef() {
        val builder = CilClassBuilder("TestAsm", "MyClass")
        // Object..ctor()
        val ctorToken = builder.addMemberRef(1, ".ctor",
            CilClassBuilder.instanceSig(CilSigType.VOID))

        val meta = builder.build()
        assertEquals(1, meta.tables.memberRefs.size)
        assertEquals(".ctor", meta.strings.get(meta.tables.memberRefs[0].name))
        assertEquals(0x0A000001, ctorToken.value)
    }

    @Test
    fun multipleMethods() {
        val builder = CilClassBuilder("TestAsm", "Math")
        builder.method("Add", CilClassBuilder.sig(CilSigType.I4, CilSigType.I4, CilSigType.I4),
            CilMethodFlags.PUBLIC or CilMethodFlags.STATIC
        ) { code ->
            code.ldarg(0); code.ldarg(1); code.add(); code.ret()
        }
        builder.method("Sub", CilClassBuilder.sig(CilSigType.I4, CilSigType.I4, CilSigType.I4),
            CilMethodFlags.PUBLIC or CilMethodFlags.STATIC
        ) { code ->
            code.ldarg(0); code.ldarg(1); code.sub(); code.ret()
        }

        val meta = builder.build()
        assertEquals(2, meta.tables.methodDefs.size)
        assertEquals("Add", meta.strings.get(meta.tables.methodDefs[0].name))
        assertEquals("Sub", meta.strings.get(meta.tables.methodDefs[1].name))
    }

    @Test
    fun defaultAssemblyRef() {
        val builder = CilClassBuilder("TestAsm", "MyClass")
        val meta = builder.build()
        // Should have mscorlib by default
        assertEquals(1, meta.tables.assemblyRefs.size)
        assertEquals("mscorlib", meta.strings.get(meta.tables.assemblyRefs[0].name))
    }

    @Test
    fun pinvokeMethod() {
        val builder = CilClassBuilder("TestAsm", "NativeInterop")
        builder.pinvoke("kernel32.dll", "GetTickCount",
            signature = CilClassBuilder.sig(CilSigType.U4),
            flags = CilMethodFlags.PUBLIC or CilMethodFlags.STATIC)

        val meta = builder.build()
        assertEquals(1, meta.tables.methodDefs.size)
        // Method should have PInvokeImpl flag (0x2000)
        assertTrue(meta.tables.methodDefs[0].flags and 0x2000 != 0)
        assertEquals(1, meta.tables.moduleRefs.size)
        assertEquals("kernel32.dll", meta.strings.get(meta.tables.moduleRefs[0].name))
        assertEquals(1, meta.tables.implMaps.size)
        assertEquals("GetTickCount", meta.strings.get(meta.tables.implMaps[0].importName))
    }

    @Test
    fun pinvokeWithDifferentNativeName() {
        val builder = CilClassBuilder("TestAsm", "NativeInterop")
        builder.pinvoke("msvcrt.dll", "PrintString", "puts",
            signature = CilClassBuilder.sig(CilSigType.I4, CilSigType.I),
            flags = CilMethodFlags.PUBLIC or CilMethodFlags.STATIC)

        val meta = builder.build()
        assertEquals("PrintString", meta.strings.get(meta.tables.methodDefs[0].name))
        assertEquals("puts", meta.strings.get(meta.tables.implMaps[0].importName))
    }

    @Test
    fun multiplePinvokeSameDll() {
        val builder = CilClassBuilder("TestAsm", "Kernel32")
        builder.pinvoke("kernel32.dll", "GetTickCount",
            signature = CilClassBuilder.sig(CilSigType.U4),
            flags = CilMethodFlags.PUBLIC or CilMethodFlags.STATIC)
        builder.pinvoke("kernel32.dll", "GetCurrentProcessId",
            signature = CilClassBuilder.sig(CilSigType.U4),
            flags = CilMethodFlags.PUBLIC or CilMethodFlags.STATIC)

        val meta = builder.build()
        assertEquals(2, meta.tables.methodDefs.size)
        // Should reuse the same ModuleRef
        assertEquals(1, meta.tables.moduleRefs.size)
        assertEquals(2, meta.tables.implMaps.size)
    }

    @Test
    fun pinvokeRoundTrips() {
        val builder = CilClassBuilder("PInvokeTest", "NativeLib")
        builder.pinvoke("user32.dll", "MessageBoxA",
            signature = CilClassBuilder.sig(CilSigType.I4, CilSigType.I, CilSigType.I, CilSigType.I, CilSigType.U4),
            flags = CilMethodFlags.PUBLIC or CilMethodFlags.STATIC)

        val bytes = builder.toBytes()
        assertTrue(bytes.isNotEmpty())

        val buf = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        val parsed = ClrTableParser(buf, bytes).parse(0, bytes.size, 2, 5,
            ClrMetadata.COR_FLAGS_ILONLY, 0)!!
        assertEquals(1, parsed.tables.implMaps.size)
        assertEquals(1, parsed.tables.moduleRefs.size)
        assertEquals("user32.dll", parsed.strings.get(parsed.tables.moduleRefs[0].name))
        assertEquals("MessageBoxA", parsed.strings.get(parsed.tables.implMaps[0].importName))
    }

    @Test
    fun defaultTypeRef() {
        val builder = CilClassBuilder("TestAsm", "MyClass")
        val meta = builder.build()
        // Should have System.Object by default
        assertEquals(1, meta.tables.typeRefs.size)
        assertEquals("Object", meta.strings.get(meta.tables.typeRefs[0].name))
        assertEquals("System", meta.strings.get(meta.tables.typeRefs[0].namespace))
    }
}
