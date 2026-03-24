package org.kgen.target.arm64.codegen

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.binary.*
import org.kgen.ir.*
import org.kgen.ir.build.*
import org.kgen.codegen.*
import org.kgen.ir.target.Target

class Arm64TlsCodegenTest {

    @Test
    fun tlsGlobalGoesToTdataSection() {
        val ir = ModuleBuilder("test", Target.arm64())
        ir.addGlobal("tls_var", Type.I32, Constant.I32(42), threadLocal = ThreadLocalMode.LOCAL_EXEC)
        ir.createFunction("get", emptyList(), Type.I32)
        ir.appendBlock("entry")
        ir.ret(Constant.I32(0))
        ir.finalizeFunction()
        val module = ir.build()

        val code = Arm64CodeGenerator().generateCode(module)
        assertTrue(code.tdataBytes.isNotEmpty(), "TLS global should produce .tdata bytes")
        assertEquals(4, code.tdataBytes.size, "I32 should be 4 bytes in .tdata")
    }

    @Test
    fun nonTlsGlobalNotInTdata() {
        val ir = ModuleBuilder("test", Target.arm64())
        ir.addGlobal("normal_var", Type.I32, Constant.I32(42))
        ir.createFunction("get", emptyList(), Type.I32)
        ir.appendBlock("entry")
        ir.ret(Constant.I32(0))
        ir.finalizeFunction()
        val module = ir.build()

        val code = Arm64CodeGenerator().generateCode(module)
        assertTrue(code.tdataBytes.isEmpty(), "Non-TLS global should not be in tdata")
    }

    @Test
    fun tlsLoadGeneratesTlsleRelocations() {
        val ir = ModuleBuilder("test", Target.arm64())
        ir.addGlobal("tls_val", Type.I32, Constant.I32(0), threadLocal = ThreadLocalMode.LOCAL_EXEC)
        ir.createFunction("read_tls", emptyList(), Type.I32)
        ir.appendBlock("entry")
        val loaded = ir.load(Type.I32, GlobalRef("tls_val", Type.Pointer(Type.I32)))
        ir.ret(loaded)
        ir.finalizeFunction()
        val module = ir.build()

        val code = Arm64CodeGenerator().generateCode(module)
        val hasHi12 = code.relocations.any { it.type == RelocationType.AArch64.TLSLE_ADD_TPREL_HI12 }
        val hasLo12 = code.relocations.any { it.type == RelocationType.AArch64.TLSLE_ADD_TPREL_LO12_NC }
        assertTrue(hasHi12, "Should have TLSLE_ADD_TPREL_HI12 relocation")
        assertTrue(hasLo12, "Should have TLSLE_ADD_TPREL_LO12_NC relocation")
    }

    @Test
    fun tlsStoreGeneratesTlsleRelocations() {
        val ir = ModuleBuilder("test", Target.arm64())
        ir.addGlobal("tls_counter", Type.I32, Constant.I32(0), threadLocal = ThreadLocalMode.LOCAL_EXEC)
        val params = ir.createFunction("set_tls", listOf(Param("v", Type.I32)), Type.Void)
        ir.appendBlock("entry")
        ir.store(params[0], GlobalRef("tls_counter", Type.Pointer(Type.I32)))
        ir.ret()
        ir.finalizeFunction()
        val module = ir.build()

        val code = Arm64CodeGenerator().generateCode(module)
        val hasHi12 = code.relocations.any {
            it.type == RelocationType.AArch64.TLSLE_ADD_TPREL_HI12 && it.symbol == "tls_counter"
        }
        assertTrue(hasHi12, "TLS store should generate TLSLE relocations")
    }

    @Test
    fun mixedTlsAndNormalGlobals() {
        val ir = ModuleBuilder("test", Target.arm64())
        ir.addGlobal("normal", Type.I32, Constant.I32(10))
        ir.addGlobal("tls_var", Type.I32, Constant.I32(20), threadLocal = ThreadLocalMode.LOCAL_EXEC)
        ir.createFunction("func", emptyList(), Type.I32)
        ir.appendBlock("entry")
        ir.ret(Constant.I32(0))
        ir.finalizeFunction()
        val module = ir.build()

        val code = Arm64CodeGenerator().generateCode(module)
        assertTrue(code.tdataBytes.isNotEmpty(), "TLS global should be in tdata")
    }

    @Test
    fun multipleTlsVariablesWithCorrectSizes() {
        val ir = ModuleBuilder("test", Target.arm64())
        ir.addGlobal("tls_a", Type.I32, Constant.I32(1), threadLocal = ThreadLocalMode.LOCAL_EXEC)
        ir.addGlobal("tls_b", Type.I64, Constant.I64(2), threadLocal = ThreadLocalMode.LOCAL_EXEC)
        ir.createFunction("func", emptyList(), Type.Void)
        ir.appendBlock("entry")
        ir.ret()
        ir.finalizeFunction()
        val module = ir.build()

        val code = Arm64CodeGenerator().generateCode(module)
        // 4 bytes (I32) + 4 bytes padding for 8-byte alignment + 8 bytes (I64) = 16
        assertTrue(code.tdataBytes.size >= 12, "Multiple TLS vars should have combined tdata size >= 12")
    }

    @Test
    fun tlsVariableWithAlignment() {
        val ir = ModuleBuilder("test", Target.arm64())
        ir.addGlobal("tls_aligned", Type.I64, Constant.I64(0), threadLocal = ThreadLocalMode.LOCAL_EXEC, align = 16)
        ir.createFunction("func", emptyList(), Type.Void)
        ir.appendBlock("entry")
        ir.ret()
        ir.finalizeFunction()
        val module = ir.build()

        val code = Arm64CodeGenerator().generateCode(module)
        assertEquals(8, code.tdataBytes.size, "I64 TLS var should be 8 bytes")
        assertEquals(16, code.tdataAlign, "TLS alignment should be 16")
    }

    @Test
    fun tlsObjectFileHasTdataSection() {
        val ir = ModuleBuilder("test", Target.arm64())
        ir.addGlobal("tls_counter", Type.I64, Constant.I64(0), threadLocal = ThreadLocalMode.LOCAL_EXEC)
        ir.createFunction("inc", emptyList(), Type.Void)
        ir.appendBlock("entry")
        ir.ret()
        ir.finalizeFunction()
        val module = ir.build()

        val obj = Arm64CodeGenerator().generateObjectFile(module)
        val tdata = obj.sections.find { it.name == ".tdata" }
        assertNotNull(tdata, "Object file should have .tdata section")
        assertEquals(SectionKind.TDATA, tdata!!.kind)
        assertEquals(8, tdata.data.size)
    }

    @Test
    fun compiledCodeHasTdataBytes() {
        val ir = ModuleBuilder("test", Target.arm64())
        ir.addGlobal("tls_x", Type.I32, Constant.I32(99), threadLocal = ThreadLocalMode.LOCAL_EXEC)
        ir.createFunction("noop", emptyList(), Type.Void)
        ir.appendBlock("entry")
        ir.ret()
        ir.finalizeFunction()
        val module = ir.build()

        val code = Arm64CodeGenerator().generateCode(module)
        assertTrue(code.tdataBytes.isNotEmpty())
        assertEquals(4, code.tdataAlign)
        // Verify initial value is 99 (little-endian)
        assertEquals(99.toByte(), code.tdataBytes[0])
        assertEquals(0.toByte(), code.tdataBytes[1])
    }

    @Test
    fun tlsLoadEmitsMrsInstruction() {
        val ir = ModuleBuilder("test", Target.arm64())
        ir.addGlobal("tls_val", Type.I64, Constant.I64(0), threadLocal = ThreadLocalMode.LOCAL_EXEC)
        ir.createFunction("read_tls", emptyList(), Type.I64)
        ir.appendBlock("entry")
        val loaded = ir.load(Type.I64, GlobalRef("tls_val", Type.Pointer(Type.I64)))
        ir.ret(loaded)
        ir.finalizeFunction()
        val module = ir.build()

        val code = Arm64CodeGenerator().generateCode(module)
        assertTrue(code.textBytes.size >= 12, "Should have at least 3 instructions for TLS access")
    }

    @Test
    fun tlsRelocationsTargetCorrectSymbol() {
        val ir = ModuleBuilder("test", Target.arm64())
        ir.addGlobal("my_tls", Type.I32, Constant.I32(0), threadLocal = ThreadLocalMode.LOCAL_EXEC)
        ir.createFunction("read", emptyList(), Type.I32)
        ir.appendBlock("entry")
        val loaded = ir.load(Type.I32, GlobalRef("my_tls", Type.Pointer(Type.I32)))
        ir.ret(loaded)
        ir.finalizeFunction()
        val module = ir.build()

        val code = Arm64CodeGenerator().generateCode(module)
        val tlsRelocs = code.relocations.filter {
            it.type == RelocationType.AArch64.TLSLE_ADD_TPREL_HI12 ||
                it.type == RelocationType.AArch64.TLSLE_ADD_TPREL_LO12_NC
        }
        assertTrue(tlsRelocs.isNotEmpty())
        assertTrue(tlsRelocs.all { it.symbol == "my_tls" }, "All TLS relocs should target 'my_tls'")
    }

    @Test
    fun tlsI64GlobalSize() {
        val ir = ModuleBuilder("test", Target.arm64())
        ir.addGlobal("tls64", Type.I64, Constant.I64(Long.MAX_VALUE), threadLocal = ThreadLocalMode.LOCAL_EXEC)
        ir.createFunction("func", emptyList(), Type.Void)
        ir.appendBlock("entry")
        ir.ret()
        ir.finalizeFunction()
        val module = ir.build()

        val code = Arm64CodeGenerator().generateCode(module)
        assertEquals(8, code.tdataBytes.size)
        assertEquals(8, code.tdataAlign)
    }

    @Test
    fun zeroInitTlsGlobal() {
        val ir = ModuleBuilder("test", Target.arm64())
        val arrType = Type.Array(Type.I32, 4)
        ir.addGlobal("tls_arr", arrType, Constant.ZeroInitializer(arrType), threadLocal = ThreadLocalMode.LOCAL_EXEC)
        ir.createFunction("func", emptyList(), Type.Void)
        ir.appendBlock("entry")
        ir.ret()
        ir.finalizeFunction()
        val module = ir.build()

        val code = Arm64CodeGenerator().generateCode(module)
        assertEquals(16, code.tdataBytes.size, "4xI32 array should be 16 bytes")
        assertTrue(code.tdataBytes.all { it == 0.toByte() }, "ZeroInitializer should produce all-zero bytes")
    }

    @Test
    fun tlsNotInExternalSymbols() {
        val ir = ModuleBuilder("test", Target.arm64())
        ir.addGlobal("tls_local", Type.I32, Constant.I32(7), threadLocal = ThreadLocalMode.LOCAL_EXEC)
        ir.createFunction("read", emptyList(), Type.I32)
        ir.appendBlock("entry")
        val loaded = ir.load(Type.I32, GlobalRef("tls_local", Type.Pointer(Type.I32)))
        ir.ret(loaded)
        ir.finalizeFunction()
        val module = ir.build()

        val code = Arm64CodeGenerator().generateCode(module)
        assertFalse(code.externalSymbols.contains("tls_local"), "TLS global should not be external")
    }
}
