package org.kgen.target.x86.codegen

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.binary.*
import org.kgen.ir.*
import org.kgen.ir.build.*
import org.kgen.codegen.*
import org.kgen.ir.target.Target

class X86TlsCodegenTest {

    @Test
    fun tlsGlobalGoesToTdataSection() {
        val ir = IrBuilder("test", Target.x86_64())
        ir.addGlobal("tls_var", Type.I32, Constant.I32(42), threadLocal = ThreadLocalMode.LOCAL_EXEC)
        ir.createFunction("get", emptyList(), Type.I32)
        ir.positionAtEnd(ir.appendBlock("entry"))
        ir.ret(Constant.I32(0))
        ir.finalizeFunction()
        val module = ir.build()

        val code = X86CodeGenerator().generateCode(module)
        assertTrue(code.tdataBytes.isNotEmpty(), "TLS global should produce .tdata bytes")
        assertEquals(4, code.tdataBytes.size, "I32 should be 4 bytes in .tdata")
    }

    @Test
    fun nonTlsGlobalGoesToRodata() {
        val ir = IrBuilder("test", Target.x86_64())
        ir.addGlobal("normal_var", Type.I32, Constant.I32(42))
        ir.createFunction("get", emptyList(), Type.I32)
        ir.positionAtEnd(ir.appendBlock("entry"))
        ir.ret(Constant.I32(0))
        ir.finalizeFunction()
        val module = ir.build()

        val code = X86CodeGenerator().generateCode(module)
        assertTrue(code.rodataBytes.isNotEmpty())
        assertTrue(code.tdataBytes.isEmpty())
    }

    @Test
    fun tlsObjectFileHasTdataSection() {
        val ir = IrBuilder("test", Target.x86_64())
        ir.addGlobal("tls_counter", Type.I64, Constant.I64(0), threadLocal = ThreadLocalMode.LOCAL_EXEC)
        ir.createFunction("inc", emptyList(), Type.Void)
        ir.positionAtEnd(ir.appendBlock("entry"))
        ir.ret()
        ir.finalizeFunction()
        val module = ir.build()

        val obj = X86CodeGenerator().generateObjectFile(module)
        val tdata = obj.sections.find { it.name == ".tdata" }
        assertNotNull(tdata, "Object file should have .tdata section")
        assertEquals(SectionKind.TDATA, tdata!!.kind)
        assertEquals(8, tdata.data.size)
    }

    @Test
    fun tlsLoadGeneratesFsSegmentPrefix() {
        val ir = IrBuilder("test", Target.x86_64())
        ir.addGlobal("tls_val", Type.I32, Constant.I32(0), threadLocal = ThreadLocalMode.LOCAL_EXEC)
        ir.createFunction("read_tls", emptyList(), Type.I32)
        ir.positionAtEnd(ir.appendBlock("entry"))
        val loaded = ir.load(Type.I32, GlobalRef("tls_val", Type.Pointer(Type.I32)))
        ir.ret(loaded)
        ir.finalizeFunction()
        val module = ir.build()

        val code = X86CodeGenerator().generateCode(module)
        val text = code.textBytes
        assertTrue(text.isNotEmpty())
        // Should contain FS prefix (0x64) for Linux TLS access
        assertTrue(text.any { it == 0x64.toByte() }, "Should contain FS segment prefix for TLS load")
        // Should have TPOFF32 relocation
        val tpoffReloc = code.relocations.any { it.type == RelocationType.X86_64.TPOFF32 }
        assertTrue(tpoffReloc, "Should have TPOFF32 relocation for TLS variable")
    }

    @Test
    fun windowsTlsUsesGsSegment() {
        val ir = IrBuilder("test", Target.x86_64())
        ir.targetTriple = "x86_64-windows"
        ir.addGlobal("tls_val", Type.I32, Constant.I32(0), threadLocal = ThreadLocalMode.LOCAL_EXEC)
        ir.createFunction("read_tls", emptyList(), Type.I32)
        ir.positionAtEnd(ir.appendBlock("entry"))
        val loaded = ir.load(Type.I32, GlobalRef("tls_val", Type.Pointer(Type.I32)))
        ir.ret(loaded)
        ir.finalizeFunction()
        val module = ir.build()

        val code = X86CodeGenerator().generateCode(module)
        val text = code.textBytes
        // Should contain GS prefix (0x65) for Windows TLS access
        assertTrue(text.any { it == 0x65.toByte() }, "Should contain GS segment prefix for Windows TLS")
    }

    @Test
    fun tlsStoreGeneratesSegmentPrefix() {
        val ir = IrBuilder("test", Target.x86_64())
        ir.addGlobal("tls_counter", Type.I32, Constant.I32(0), threadLocal = ThreadLocalMode.LOCAL_EXEC)
        val params = ir.createFunction("set_tls", listOf(Param("v", Type.I32)), Type.Void)
        ir.positionAtEnd(ir.appendBlock("entry"))
        ir.store(params[0], GlobalRef("tls_counter", Type.Pointer(Type.I32)))
        ir.ret()
        ir.finalizeFunction()
        val module = ir.build()

        val code = X86CodeGenerator().generateCode(module)
        assertTrue(code.textBytes.any { it == 0x64.toByte() }, "TLS store should use FS segment prefix")
    }

    @Test
    fun tlsGepGeneratesSegmentPrefix() {
        val ir = IrBuilder("test", Target.x86_64())
        val structType = Type.Struct(null, listOf(Type.I32, Type.I64))
        ir.addGlobal("tls_struct", structType, Constant.ZeroInitializer(structType), threadLocal = ThreadLocalMode.LOCAL_EXEC)
        ir.createFunction("get_field", emptyList(), Type.I64)
        ir.positionAtEnd(ir.appendBlock("entry"))
        val fieldPtr = ir.gep(structType, GlobalRef("tls_struct", Type.Pointer(structType)), Constant.I32(0), Constant.I32(1))
        val loaded = ir.load(Type.I64, fieldPtr)
        ir.ret(loaded)
        ir.finalizeFunction()
        val module = ir.build()

        val code = X86CodeGenerator().generateCode(module)
        assertTrue(code.textBytes.any { it == 0x64.toByte() }, "TLS GEP should use FS segment prefix")
    }

    @Test
    fun mixedTlsAndNormalGlobals() {
        val ir = IrBuilder("test", Target.x86_64())
        ir.addGlobal("normal", Type.I32, Constant.I32(10))
        ir.addGlobal("tls_var", Type.I32, Constant.I32(20), threadLocal = ThreadLocalMode.LOCAL_EXEC)
        ir.createFunction("func", emptyList(), Type.I32)
        ir.positionAtEnd(ir.appendBlock("entry"))
        ir.ret(Constant.I32(0))
        ir.finalizeFunction()
        val module = ir.build()

        val code = X86CodeGenerator().generateCode(module)
        assertTrue(code.rodataBytes.isNotEmpty(), "Normal global should be in rodata")
        assertTrue(code.tdataBytes.isNotEmpty(), "TLS global should be in tdata")
    }
}
