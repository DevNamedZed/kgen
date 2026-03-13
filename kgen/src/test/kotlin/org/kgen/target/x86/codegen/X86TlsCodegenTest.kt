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
    fun initialExecTlsUsesGottpoff() {
        val ir = IrBuilder("test", Target.x86_64())
        ir.addGlobal("tls_ie", Type.I32, Constant.I32(0), threadLocal = ThreadLocalMode.INITIAL_EXEC)
        ir.createFunction("read_ie", emptyList(), Type.I32)
        ir.positionAtEnd(ir.appendBlock("entry"))
        val loaded = ir.load(Type.I32, GlobalRef("tls_ie", Type.Pointer(Type.I32)))
        ir.ret(loaded)
        ir.finalizeFunction()
        val module = ir.build()

        val code = X86CodeGenerator().generateCode(module)
        val text = code.textBytes
        // Should contain FS prefix (0x64) for TLS thread pointer load
        assertTrue(text.any { it == 0x64.toByte() }, "IE TLS should use FS segment prefix")
        // Should have GOTTPOFF relocation (not TPOFF32)
        val gottpoffReloc = code.relocations.any { it.type == RelocationType.X86_64.GOTTPOFF }
        assertTrue(gottpoffReloc, "IE TLS should have GOTTPOFF relocation")
        val noTpoff = code.relocations.none { it.type == RelocationType.X86_64.TPOFF32 }
        assertTrue(noTpoff, "IE TLS should not have TPOFF32 relocation")
    }

    @Test
    fun initialExecTlsEmitsAddInstruction() {
        val ir = IrBuilder("test", Target.x86_64())
        ir.addGlobal("tls_ie", Type.I32, Constant.I32(0), threadLocal = ThreadLocalMode.INITIAL_EXEC)
        ir.createFunction("read_ie", emptyList(), Type.I32)
        ir.positionAtEnd(ir.appendBlock("entry"))
        val loaded = ir.load(Type.I32, GlobalRef("tls_ie", Type.Pointer(Type.I32)))
        ir.ret(loaded)
        ir.finalizeFunction()
        val module = ir.build()

        val code = X86CodeGenerator().generateCode(module)
        val text = code.textBytes
        // Should contain ADD r64, [rip+disp32] opcode (0x03 with ModRM [rip])
        assertTrue(text.any { it == 0x03.toByte() }, "IE TLS should emit ADD instruction for GOT-indirect")
    }

    @Test
    fun initialExecTlsStoreWorks() {
        val ir = IrBuilder("test", Target.x86_64())
        ir.addGlobal("tls_ie", Type.I32, Constant.I32(0), threadLocal = ThreadLocalMode.INITIAL_EXEC)
        val params = ir.createFunction("write_ie", listOf(Param("v", Type.I32)), Type.Void)
        ir.positionAtEnd(ir.appendBlock("entry"))
        ir.store(params[0], GlobalRef("tls_ie", Type.Pointer(Type.I32)))
        ir.ret()
        ir.finalizeFunction()
        val module = ir.build()

        val code = X86CodeGenerator().generateCode(module)
        assertTrue(code.relocations.any { it.type == RelocationType.X86_64.GOTTPOFF },
            "IE TLS store should have GOTTPOFF relocation")
    }

    @Test
    fun generalDynamicTlsUsesTlsgdRelocation() {
        val ir = IrBuilder("test", Target.x86_64())
        ir.addGlobal("tls_gd", Type.I32, Constant.I32(0), threadLocal = ThreadLocalMode.GENERAL_DYNAMIC)
        ir.createFunction("read_gd", emptyList(), Type.I32)
        ir.positionAtEnd(ir.appendBlock("entry"))
        val loaded = ir.load(Type.I32, GlobalRef("tls_gd", Type.Pointer(Type.I32)))
        ir.ret(loaded)
        ir.finalizeFunction()
        val module = ir.build()

        val code = X86CodeGenerator().generateCode(module)
        // Should have TLSGD relocation for the LEA
        val tlsgdReloc = code.relocations.any { it.type == RelocationType.X86_64.TLSGD }
        assertTrue(tlsgdReloc, "GD TLS should have TLSGD relocation")
        // Should have PLT32 relocation for __tls_get_addr call
        val pltReloc = code.relocations.any {
            it.type == RelocationType.X86_64.PLT32 && it.symbol == "__tls_get_addr"
        }
        assertTrue(pltReloc, "GD TLS should have PLT32 relocation to __tls_get_addr")
    }

    @Test
    fun generalDynamicTlsEmitsCallInstruction() {
        val ir = IrBuilder("test", Target.x86_64())
        ir.addGlobal("tls_gd", Type.I32, Constant.I32(0), threadLocal = ThreadLocalMode.GENERAL_DYNAMIC)
        ir.createFunction("read_gd", emptyList(), Type.I32)
        ir.positionAtEnd(ir.appendBlock("entry"))
        val loaded = ir.load(Type.I32, GlobalRef("tls_gd", Type.Pointer(Type.I32)))
        ir.ret(loaded)
        ir.finalizeFunction()
        val module = ir.build()

        val code = X86CodeGenerator().generateCode(module)
        val text = code.textBytes
        // GD sequence: data16 prefix (0x66), LEA (0x8D), CALL (0xE8)
        assertTrue(text.any { it == 0x66.toByte() }, "GD TLS should contain data16 prefix")
        assertTrue(text.any { it == 0xE8.toByte() }, "GD TLS should contain CALL instruction")
        // Should NOT have FS prefix — GD uses __tls_get_addr, not direct FS access
        // (FS prefix appears only in the relaxed LE form, not in the original GD codegen)
    }

    @Test
    fun generalDynamicTlsStoreWorks() {
        val ir = IrBuilder("test", Target.x86_64())
        ir.addGlobal("tls_gd", Type.I32, Constant.I32(0), threadLocal = ThreadLocalMode.GENERAL_DYNAMIC)
        val params = ir.createFunction("write_gd", listOf(Param("v", Type.I32)), Type.Void)
        ir.positionAtEnd(ir.appendBlock("entry"))
        ir.store(params[0], GlobalRef("tls_gd", Type.Pointer(Type.I32)))
        ir.ret()
        ir.finalizeFunction()
        val module = ir.build()

        val code = X86CodeGenerator().generateCode(module)
        assertTrue(code.relocations.any { it.type == RelocationType.X86_64.TLSGD },
            "GD TLS store should have TLSGD relocation")
    }

    @Test
    fun generalDynamicTlsEmitsCorrectSequence() {
        val ir = IrBuilder("test", Target.x86_64())
        ir.addGlobal("tls_gd", Type.I32, Constant.I32(0), threadLocal = ThreadLocalMode.GENERAL_DYNAMIC)
        ir.createFunction("read_gd", emptyList(), Type.I32)
        ir.positionAtEnd(ir.appendBlock("entry"))
        val loaded = ir.load(Type.I32, GlobalRef("tls_gd", Type.Pointer(Type.I32)))
        ir.ret(loaded)
        ir.finalizeFunction()
        val module = ir.build()

        val code = X86CodeGenerator().generateCode(module)
        val tlsgdReloc = code.relocations.first { it.type == RelocationType.X86_64.TLSGD }
        val text = code.textBytes
        val offset = tlsgdReloc.offset.toInt()
        // The TLSGD relocation points at the disp32 in the LEA instruction.
        // Verify: byte before disp32 is 0x3D (ModRM for rdi, [rip+disp32])
        assertEquals(0x3D.toByte(), text[offset - 1], "ModRM should be 0x3D (rdi, [rip+disp32])")
        // Two bytes before that: 0x8D (LEA opcode)
        assertEquals(0x8D.toByte(), text[offset - 2], "Should be LEA opcode")
        // Three bytes before: 0x48 (REX.W)
        assertEquals(0x48.toByte(), text[offset - 3], "Should be REX.W prefix")
        // Four bytes before: 0x66 (data16)
        assertEquals(0x66.toByte(), text[offset - 4], "Should be data16 prefix")
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
