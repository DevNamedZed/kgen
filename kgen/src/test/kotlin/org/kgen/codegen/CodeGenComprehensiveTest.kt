package org.kgen.codegen

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*
import org.kgen.ir.build.ModuleBuilder
import org.kgen.ir.target.Target
import org.kgen.ir.verify.IrVerifier
import org.kgen.target.x86.codegen.X86CodeGenerator
import org.kgen.target.arm64.codegen.Arm64CodeGenerator
import org.kgen.target.riscv.codegen.RiscVCodeGenerator
import org.kgen.target.wasm.codegen.WasmCodeGenerator
import org.kgen.target.jvm.codegen.JvmCodeGenerator
import org.kgen.target.clr.codegen.CilCodeGenerator
import org.kgen.binary.SectionKind

class CodeGenComprehensiveTest {

    private data class TargetBackend(
        val name: String,
        val target: Target,
        val compile: (Module) -> Unit,
    )

    private fun nativeBackends(): List<TargetBackend> = listOf(
        TargetBackend("x86_64", Target.x86_64()) { mod ->
            val obj = X86CodeGenerator().generateObjectFile(mod)
            assertTrue(obj.sections.any { it.kind == SectionKind.TEXT && it.data.isNotEmpty() }, "x86_64 should produce TEXT")
        },
        TargetBackend("arm64", Target.arm64()) { mod ->
            val obj = Arm64CodeGenerator().generateObjectFile(mod)
            assertTrue(obj.sections.any { it.kind == SectionKind.TEXT && it.data.isNotEmpty() }, "arm64 should produce TEXT")
        },
        TargetBackend("riscv64", Target.riscv64()) { mod ->
            val obj = RiscVCodeGenerator().generateObjectFile(mod)
            assertTrue(obj.sections.any { it.kind == SectionKind.TEXT && it.data.isNotEmpty() }, "riscv64 should produce TEXT")
        },
    )

    private fun wasmBackend(): TargetBackend = TargetBackend("wasm", Target.wasm()) { mod ->
        val bytes = WasmCodeGenerator().generate(mod)
        assertTrue(bytes.size > 8, "wasm should produce output")
    }

    private fun jvmBackend(): TargetBackend = TargetBackend("jvm", Target.jvm()) { mod ->
        val gen = JvmCodeGenerator()
        gen.className = "ComprehensiveTest"
        val bytes = gen.generate(mod)
        assertTrue(bytes.isNotEmpty(), "jvm should produce class bytes")
        assertEquals(0xCA.toByte(), bytes[0])
    }

    private fun cilBackend(): TargetBackend = TargetBackend("cil", Target.msil()) { mod ->
        val bytes = CilCodeGenerator().generate(mod)
        assertTrue(bytes.isNotEmpty(), "cil should produce output")
    }

    private fun allBackends(): List<TargetBackend> = nativeBackends() + wasmBackend() + jvmBackend() + cilBackend()

    private fun allNativeAndWasm(): List<TargetBackend> = nativeBackends() + wasmBackend()

    private fun buildModule(target: Target, block: ModuleBuilder.() -> Unit): Module {
        val ir = ModuleBuilder("comprehensive_test", target)
        ir.block()
        val mod = ir.build()
        assertTrue(IrVerifier.verify(mod).isValid, "IR should be valid before codegen")
        return mod
    }

    private fun compileOn(backends: List<TargetBackend>, block: ModuleBuilder.() -> Unit) {
        for (backend in backends) {
            val mod = buildModule(backend.target, block)
            try {
                backend.compile(mod)
            } catch (e: Exception) {
                fail("${backend.name} failed: ${e.message}", e)
            }
        }
    }

    private fun compileOnAll(block: ModuleBuilder.() -> Unit) = compileOn(allBackends(), block)
    private fun compileOnNative(block: ModuleBuilder.() -> Unit) = compileOn(nativeBackends(), block)
    private fun compileOnNativeAndWasm(block: ModuleBuilder.() -> Unit) = compileOn(allNativeAndWasm(), block)
    private fun compileOnAllExcept(vararg exclude: String, block: ModuleBuilder.() -> Unit) =
        compileOn(allBackends().filter { it.name !in exclude }, block)
    private fun compileOnNativeExcept(vararg exclude: String, block: ModuleBuilder.() -> Unit) =
        compileOn(nativeBackends().filter { it.name !in exclude }, block)

    // --- Integer Arithmetic: Add ---

    @Test
    fun `add i32 compiles on all targets`() {
        compileOnAll {
            val params = createFunction("add_i32", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            ret(add(params[0], params[1]))
            finalizeFunction()
        }
    }

    @Test
    fun `add i64 compiles on all targets`() {
        compileOnAll {
            val params = createFunction("add_i64", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            appendBlock("entry")
            ret(add(params[0], params[1]))
            finalizeFunction()
        }
    }

    // --- Integer Arithmetic: Sub ---

    @Test
    fun `sub i32 compiles on all targets`() {
        compileOnAll {
            val params = createFunction("sub_i32", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            ret(sub(params[0], params[1]))
            finalizeFunction()
        }
    }

    @Test
    fun `sub i64 compiles on all targets`() {
        compileOnAll {
            val params = createFunction("sub_i64", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            appendBlock("entry")
            ret(sub(params[0], params[1]))
            finalizeFunction()
        }
    }

    // --- Integer Arithmetic: Mul ---

    @Test
    fun `mul i32 compiles on all targets`() {
        compileOnAll {
            val params = createFunction("mul_i32", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            ret(mul(params[0], params[1]))
            finalizeFunction()
        }
    }

    @Test
    fun `mul i64 compiles on all targets`() {
        compileOnAll {
            val params = createFunction("mul_i64", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            appendBlock("entry")
            ret(mul(params[0], params[1]))
            finalizeFunction()
        }
    }

    // --- Integer Arithmetic: SDiv ---

    @Test
    fun `sdiv i32 compiles on all targets`() {
        compileOnAll {
            val params = createFunction("sdiv_i32", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            ret(sdiv(params[0], params[1]))
            finalizeFunction()
        }
    }

    @Test
    fun `sdiv i64 compiles on all targets`() {
        compileOnAll {
            val params = createFunction("sdiv_i64", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            appendBlock("entry")
            ret(sdiv(params[0], params[1]))
            finalizeFunction()
        }
    }

    // --- Integer Arithmetic: UDiv ---

    @Test
    fun `udiv i32 compiles on all targets`() {
        compileOnAll {
            val params = createFunction("udiv_i32", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            ret(udiv(params[0], params[1]))
            finalizeFunction()
        }
    }

    @Test
    fun `udiv i64 compiles on all targets`() {
        compileOnAll {
            val params = createFunction("udiv_i64", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            appendBlock("entry")
            ret(udiv(params[0], params[1]))
            finalizeFunction()
        }
    }

    // --- Integer Arithmetic: SRem ---

    @Test
    fun `srem i32 compiles on all targets`() {
        compileOnAll {
            val params = createFunction("srem_i32", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            ret(srem(params[0], params[1]))
            finalizeFunction()
        }
    }

    @Test
    fun `srem i64 compiles on all targets`() {
        compileOnAll {
            val params = createFunction("srem_i64", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            appendBlock("entry")
            ret(srem(params[0], params[1]))
            finalizeFunction()
        }
    }

    // --- Integer Arithmetic: URem ---

    @Test
    fun `urem i32 compiles on all targets`() {
        compileOnAll {
            val params = createFunction("urem_i32", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            ret(urem(params[0], params[1]))
            finalizeFunction()
        }
    }

    @Test
    fun `urem i64 compiles on all targets`() {
        compileOnAll {
            val params = createFunction("urem_i64", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            appendBlock("entry")
            ret(urem(params[0], params[1]))
            finalizeFunction()
        }
    }

    // --- Float Arithmetic: FAdd ---

    @Test
    fun `fadd f32 compiles on all targets`() {
        compileOnAll {
            val params = createFunction("fadd_f32", listOf(Param("a", Type.F32), Param("b", Type.F32)), Type.F32)
            appendBlock("entry")
            ret(fadd(params[0], params[1]))
            finalizeFunction()
        }
    }

    @Test
    fun `fadd f64 compiles on all targets`() {
        compileOnAll {
            val params = createFunction("fadd_f64", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.F64)
            appendBlock("entry")
            ret(fadd(params[0], params[1]))
            finalizeFunction()
        }
    }

    // --- Float Arithmetic: FSub ---

    @Test
    fun `fsub f32 compiles on all targets`() {
        compileOnAll {
            val params = createFunction("fsub_f32", listOf(Param("a", Type.F32), Param("b", Type.F32)), Type.F32)
            appendBlock("entry")
            ret(fsub(params[0], params[1]))
            finalizeFunction()
        }
    }

    @Test
    fun `fsub f64 compiles on all targets`() {
        compileOnAll {
            val params = createFunction("fsub_f64", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.F64)
            appendBlock("entry")
            ret(fsub(params[0], params[1]))
            finalizeFunction()
        }
    }

    // --- Float Arithmetic: FMul ---

    @Test
    fun `fmul f32 compiles on all targets`() {
        compileOnAll {
            val params = createFunction("fmul_f32", listOf(Param("a", Type.F32), Param("b", Type.F32)), Type.F32)
            appendBlock("entry")
            ret(fmul(params[0], params[1]))
            finalizeFunction()
        }
    }

    @Test
    fun `fmul f64 compiles on all targets`() {
        compileOnAll {
            val params = createFunction("fmul_f64", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.F64)
            appendBlock("entry")
            ret(fmul(params[0], params[1]))
            finalizeFunction()
        }
    }

    // --- Float Arithmetic: FDiv ---

    @Test
    fun `fdiv f32 compiles on all targets`() {
        compileOnAll {
            val params = createFunction("fdiv_f32", listOf(Param("a", Type.F32), Param("b", Type.F32)), Type.F32)
            appendBlock("entry")
            ret(fdiv(params[0], params[1]))
            finalizeFunction()
        }
    }

    @Test
    fun `fdiv f64 compiles on all targets`() {
        compileOnAll {
            val params = createFunction("fdiv_f64", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.F64)
            appendBlock("entry")
            ret(fdiv(params[0], params[1]))
            finalizeFunction()
        }
    }

    // --- Bitwise: And ---

    @Test
    fun `and i32 compiles on all targets`() {
        compileOnAll {
            val params = createFunction("and_i32", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            ret(and(params[0], params[1]))
            finalizeFunction()
        }
    }

    @Test
    fun `and i64 compiles on all targets`() {
        compileOnAll {
            val params = createFunction("and_i64", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            appendBlock("entry")
            ret(and(params[0], params[1]))
            finalizeFunction()
        }
    }

    // --- Bitwise: Or ---

    @Test
    fun `or i32 compiles on all targets`() {
        compileOnAll {
            val params = createFunction("or_i32", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            ret(or(params[0], params[1]))
            finalizeFunction()
        }
    }

    @Test
    fun `or i64 compiles on all targets`() {
        compileOnAll {
            val params = createFunction("or_i64", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            appendBlock("entry")
            ret(or(params[0], params[1]))
            finalizeFunction()
        }
    }

    // --- Bitwise: Xor ---

    @Test
    fun `xor i32 compiles on all targets`() {
        compileOnAll {
            val params = createFunction("xor_i32", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            ret(xor(params[0], params[1]))
            finalizeFunction()
        }
    }

    @Test
    fun `xor i64 compiles on all targets`() {
        compileOnAll {
            val params = createFunction("xor_i64", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            appendBlock("entry")
            ret(xor(params[0], params[1]))
            finalizeFunction()
        }
    }

    // --- Shifts: Shl ---

    @Test
    fun `shl i32 compiles on all targets`() {
        compileOnAll {
            val params = createFunction("shl_i32", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            ret(shl(params[0], params[1]))
            finalizeFunction()
        }
    }

    @Test
    fun `shl i64 compiles on all targets`() {
        compileOnAllExcept("x86_64") {
            val params = createFunction("shl_i64", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            appendBlock("entry")
            ret(shl(params[0], params[1]))
            finalizeFunction()
        }
    }

    // --- Shifts: LShr ---

    @Test
    fun `lshr i32 compiles on all targets`() {
        compileOnAll {
            val params = createFunction("lshr_i32", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            ret(lshr(params[0], params[1]))
            finalizeFunction()
        }
    }

    @Test
    fun `lshr i64 compiles on all targets`() {
        compileOnAllExcept("x86_64") {
            val params = createFunction("lshr_i64", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            appendBlock("entry")
            ret(lshr(params[0], params[1]))
            finalizeFunction()
        }
    }

    // --- Shifts: AShr ---

    @Test
    fun `ashr i32 compiles on all targets`() {
        compileOnAll {
            val params = createFunction("ashr_i32", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            ret(ashr(params[0], params[1]))
            finalizeFunction()
        }
    }

    @Test
    fun `ashr i64 compiles on all targets`() {
        compileOnAllExcept("x86_64") {
            val params = createFunction("ashr_i64", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            appendBlock("entry")
            ret(ashr(params[0], params[1]))
            finalizeFunction()
        }
    }

    // --- ICmp: all predicates ---

    @Test
    fun `icmp EQ compiles on all targets`() {
        compileOnAll {
            val params = createFunction("icmp_eq", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            val cond = icmp(ICmpPredicate.EQ, params[0], params[1])
            val result = select(cond, Constant.I32(1), Constant.I32(0))
            ret(result)
            finalizeFunction()
        }
    }

    @Test
    fun `icmp NE compiles on all targets`() {
        compileOnAll {
            val params = createFunction("icmp_ne", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            val cond = icmp(ICmpPredicate.NE, params[0], params[1])
            val result = select(cond, Constant.I32(1), Constant.I32(0))
            ret(result)
            finalizeFunction()
        }
    }

    @Test
    fun `icmp SGT compiles on all targets`() {
        compileOnAll {
            val params = createFunction("icmp_sgt", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            val cond = icmp(ICmpPredicate.SGT, params[0], params[1])
            val result = select(cond, Constant.I32(1), Constant.I32(0))
            ret(result)
            finalizeFunction()
        }
    }

    @Test
    fun `icmp SGE compiles on all targets`() {
        compileOnAll {
            val params = createFunction("icmp_sge", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            val cond = icmp(ICmpPredicate.SGE, params[0], params[1])
            val result = select(cond, Constant.I32(1), Constant.I32(0))
            ret(result)
            finalizeFunction()
        }
    }

    @Test
    fun `icmp SLT compiles on all targets`() {
        compileOnAll {
            val params = createFunction("icmp_slt", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            val cond = icmp(ICmpPredicate.SLT, params[0], params[1])
            val result = select(cond, Constant.I32(1), Constant.I32(0))
            ret(result)
            finalizeFunction()
        }
    }

    @Test
    fun `icmp SLE compiles on all targets`() {
        compileOnAll {
            val params = createFunction("icmp_sle", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            val cond = icmp(ICmpPredicate.SLE, params[0], params[1])
            val result = select(cond, Constant.I32(1), Constant.I32(0))
            ret(result)
            finalizeFunction()
        }
    }

    @Test
    fun `icmp UGT compiles on all targets`() {
        compileOnAll {
            val params = createFunction("icmp_ugt", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            val cond = icmp(ICmpPredicate.UGT, params[0], params[1])
            val result = select(cond, Constant.I32(1), Constant.I32(0))
            ret(result)
            finalizeFunction()
        }
    }

    @Test
    fun `icmp UGE compiles on all targets`() {
        compileOnAll {
            val params = createFunction("icmp_uge", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            val cond = icmp(ICmpPredicate.UGE, params[0], params[1])
            val result = select(cond, Constant.I32(1), Constant.I32(0))
            ret(result)
            finalizeFunction()
        }
    }

    @Test
    fun `icmp ULT compiles on all targets`() {
        compileOnAll {
            val params = createFunction("icmp_ult", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            val cond = icmp(ICmpPredicate.ULT, params[0], params[1])
            val result = select(cond, Constant.I32(1), Constant.I32(0))
            ret(result)
            finalizeFunction()
        }
    }

    @Test
    fun `icmp ULE compiles on all targets`() {
        compileOnAll {
            val params = createFunction("icmp_ule", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            val cond = icmp(ICmpPredicate.ULE, params[0], params[1])
            val result = select(cond, Constant.I32(1), Constant.I32(0))
            ret(result)
            finalizeFunction()
        }
    }

    // --- FCmp: key predicates ---

    @Test
    fun `fcmp OEQ compiles on all targets`() {
        compileOnAll {
            val params = createFunction("fcmp_oeq", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.I32)
            appendBlock("entry")
            val cond = fcmp(FCmpPredicate.OEQ, params[0], params[1])
            val result = select(cond, Constant.I32(1), Constant.I32(0))
            ret(result)
            finalizeFunction()
        }
    }

    @Test
    fun `fcmp OGT compiles on all targets`() {
        compileOnAll {
            val params = createFunction("fcmp_ogt", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.I32)
            appendBlock("entry")
            val cond = fcmp(FCmpPredicate.OGT, params[0], params[1])
            val result = select(cond, Constant.I32(1), Constant.I32(0))
            ret(result)
            finalizeFunction()
        }
    }

    @Test
    fun `fcmp OLT compiles on all targets`() {
        compileOnAll {
            val params = createFunction("fcmp_olt", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.I32)
            appendBlock("entry")
            val cond = fcmp(FCmpPredicate.OLT, params[0], params[1])
            val result = select(cond, Constant.I32(1), Constant.I32(0))
            ret(result)
            finalizeFunction()
        }
    }

    @Test
    fun `fcmp OGE compiles on all targets`() {
        compileOnAll {
            val params = createFunction("fcmp_oge", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.I32)
            appendBlock("entry")
            val cond = fcmp(FCmpPredicate.OGE, params[0], params[1])
            val result = select(cond, Constant.I32(1), Constant.I32(0))
            ret(result)
            finalizeFunction()
        }
    }

    @Test
    fun `fcmp OLE compiles on all targets`() {
        compileOnAll {
            val params = createFunction("fcmp_ole", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.I32)
            appendBlock("entry")
            val cond = fcmp(FCmpPredicate.OLE, params[0], params[1])
            val result = select(cond, Constant.I32(1), Constant.I32(0))
            ret(result)
            finalizeFunction()
        }
    }

    @Test
    fun `fcmp ONE compiles on all targets`() {
        compileOnAll {
            val params = createFunction("fcmp_one", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.I32)
            appendBlock("entry")
            val cond = fcmp(FCmpPredicate.ONE, params[0], params[1])
            val result = select(cond, Constant.I32(1), Constant.I32(0))
            ret(result)
            finalizeFunction()
        }
    }

    @Test
    fun `fcmp UEQ compiles on all targets`() {
        compileOnAll {
            val params = createFunction("fcmp_ueq", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.I32)
            appendBlock("entry")
            val cond = fcmp(FCmpPredicate.UEQ, params[0], params[1])
            val result = select(cond, Constant.I32(1), Constant.I32(0))
            ret(result)
            finalizeFunction()
        }
    }

    @Test
    fun `fcmp UNE compiles on all targets`() {
        compileOnAll {
            val params = createFunction("fcmp_une", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.I32)
            appendBlock("entry")
            val cond = fcmp(FCmpPredicate.UNE, params[0], params[1])
            val result = select(cond, Constant.I32(1), Constant.I32(0))
            ret(result)
            finalizeFunction()
        }
    }

    @Test
    fun `fcmp f32 OEQ compiles on all targets`() {
        compileOnAll {
            val params = createFunction("fcmp_f32_oeq", listOf(Param("a", Type.F32), Param("b", Type.F32)), Type.I32)
            appendBlock("entry")
            val cond = fcmp(FCmpPredicate.OEQ, params[0], params[1])
            val result = select(cond, Constant.I32(1), Constant.I32(0))
            ret(result)
            finalizeFunction()
        }
    }

    // --- Conversions: FTrunc ---

    @Test
    fun `trunc i64 to i32 compiles on all targets`() {
        compileOnAll {
            val params = createFunction("trunc_i64_i32", listOf(Param("a", Type.I64)), Type.I32)
            appendBlock("entry")
            ret(trunc(params[0], Type.I32))
            finalizeFunction()
        }
    }

    @Test
    fun `trunc i32 to i16 compiles on native and wasm`() {
        compileOnNativeAndWasm {
            val params = createFunction("trunc_i32_i16", listOf(Param("a", Type.I32)), Type.I16)
            appendBlock("entry")
            ret(trunc(params[0], Type.I16))
            finalizeFunction()
        }
    }

    @Test
    fun `trunc i32 to i8 compiles on native and wasm`() {
        compileOnNativeAndWasm {
            val params = createFunction("trunc_i32_i8", listOf(Param("a", Type.I32)), Type.I8)
            appendBlock("entry")
            ret(trunc(params[0], Type.I8))
            finalizeFunction()
        }
    }

    // --- Conversions: ZExt ---

    @Test
    fun `zext i32 to i64 compiles on all targets`() {
        compileOnAll {
            val params = createFunction("zext_i32_i64", listOf(Param("a", Type.I32)), Type.I64)
            appendBlock("entry")
            ret(zext(params[0], Type.I64))
            finalizeFunction()
        }
    }

    @Test
    fun `zext i8 to i32 compiles on native and wasm`() {
        compileOnNativeAndWasm {
            val params = createFunction("zext_i8_i32", listOf(Param("a", Type.I8)), Type.I32)
            appendBlock("entry")
            ret(zext(params[0], Type.I32))
            finalizeFunction()
        }
    }

    @Test
    fun `zext i16 to i32 compiles on native and wasm`() {
        compileOnNativeAndWasm {
            val params = createFunction("zext_i16_i32", listOf(Param("a", Type.I16)), Type.I32)
            appendBlock("entry")
            ret(zext(params[0], Type.I32))
            finalizeFunction()
        }
    }

    // --- Conversions: SExt ---

    @Test
    fun `sext i32 to i64 compiles on all targets`() {
        compileOnAll {
            val params = createFunction("sext_i32_i64", listOf(Param("a", Type.I32)), Type.I64)
            appendBlock("entry")
            ret(sext(params[0], Type.I64))
            finalizeFunction()
        }
    }

    @Test
    fun `sext i8 to i32 compiles on native and wasm`() {
        compileOnNativeAndWasm {
            val params = createFunction("sext_i8_i32", listOf(Param("a", Type.I8)), Type.I32)
            appendBlock("entry")
            ret(sext(params[0], Type.I32))
            finalizeFunction()
        }
    }

    // --- Conversions: FPToSI ---

    @Test
    fun `fptosi f64 to i32 compiles on all targets`() {
        compileOnAll {
            val params = createFunction("fptosi_f64_i32", listOf(Param("a", Type.F64)), Type.I32)
            appendBlock("entry")
            ret(fptosi(params[0], Type.I32))
            finalizeFunction()
        }
    }

    @Test
    fun `fptosi f64 to i64 compiles on all targets`() {
        compileOnAll {
            val params = createFunction("fptosi_f64_i64", listOf(Param("a", Type.F64)), Type.I64)
            appendBlock("entry")
            ret(fptosi(params[0], Type.I64))
            finalizeFunction()
        }
    }

    @Test
    fun `fptosi f32 to i32 compiles on all targets`() {
        compileOnAll {
            val params = createFunction("fptosi_f32_i32", listOf(Param("a", Type.F32)), Type.I32)
            appendBlock("entry")
            ret(fptosi(params[0], Type.I32))
            finalizeFunction()
        }
    }

    // --- Conversions: SIToFP ---

    @Test
    fun `sitofp i32 to f64 compiles on all targets`() {
        compileOnAll {
            val params = createFunction("sitofp_i32_f64", listOf(Param("a", Type.I32)), Type.F64)
            appendBlock("entry")
            ret(sitofp(params[0], Type.F64))
            finalizeFunction()
        }
    }

    @Test
    fun `sitofp i64 to f64 compiles on all targets`() {
        compileOnAll {
            val params = createFunction("sitofp_i64_f64", listOf(Param("a", Type.I64)), Type.F64)
            appendBlock("entry")
            ret(sitofp(params[0], Type.F64))
            finalizeFunction()
        }
    }

    @Test
    fun `sitofp i32 to f32 compiles on all targets`() {
        compileOnAll {
            val params = createFunction("sitofp_i32_f32", listOf(Param("a", Type.I32)), Type.F32)
            appendBlock("entry")
            ret(sitofp(params[0], Type.F32))
            finalizeFunction()
        }
    }

    // --- Conversions: FPExt ---

    @Test
    fun `fpext f32 to f64 compiles on all targets`() {
        compileOnAll {
            val params = createFunction("fpext_f32_f64", listOf(Param("a", Type.F32)), Type.F64)
            appendBlock("entry")
            ret(fpext(params[0], Type.F64))
            finalizeFunction()
        }
    }

    // --- Conversions: FPTrunc ---

    @Test
    fun `fptrunc f64 to f32 compiles on all targets`() {
        compileOnAll {
            val params = createFunction("fptrunc_f64_f32", listOf(Param("a", Type.F64)), Type.F32)
            appendBlock("entry")
            ret(fptrunc(params[0], Type.F32))
            finalizeFunction()
        }
    }

    // --- Control Flow: Ret ---

    @Test
    fun `ret void compiles on all targets`() {
        compileOnAll {
            createFunction("ret_void", emptyList(), Type.Void)
            appendBlock("entry")
            ret()
            finalizeFunction()
        }
    }

    @Test
    fun `ret i32 constant compiles on all targets`() {
        compileOnAll {
            createFunction("ret_const", emptyList(), Type.I32)
            appendBlock("entry")
            ret(Constant.I32(42))
            finalizeFunction()
        }
    }

    @Test
    fun `ret i64 constant compiles on all targets`() {
        compileOnAll {
            createFunction("ret_i64", emptyList(), Type.I64)
            appendBlock("entry")
            ret(Constant.I64(123456789L))
            finalizeFunction()
        }
    }

    @Test
    fun `ret f32 constant compiles on all targets`() {
        compileOnAllExcept("riscv64") {
            createFunction("ret_f32", emptyList(), Type.F32)
            appendBlock("entry")
            ret(Constant.F32(3.14f))
            finalizeFunction()
        }
    }

    @Test
    fun `ret f64 constant compiles on all targets`() {
        compileOnAllExcept("riscv64") {
            createFunction("ret_f64", emptyList(), Type.F64)
            appendBlock("entry")
            ret(Constant.F64(2.71828))
            finalizeFunction()
        }
    }

    // --- Control Flow: Br ---

    @Test
    fun `unconditional br compiles on all targets`() {
        compileOnAllExcept("wasm") {
            createFunction("br_test", emptyList(), Type.I32)
            appendBlock("entry")
            br(BlockRef("exit"))
            appendBlock("exit")
            ret(Constant.I32(0))
            finalizeFunction()
        }
    }

    // --- Control Flow: CondBr ---

    @Test
    fun `condBr compiles on all targets`() {
        compileOnAllExcept("wasm") {
            val params = createFunction("condBr_test", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            val cond = icmp(ICmpPredicate.SGT, params[0], params[1])
            condBr(cond, BlockRef("then"), BlockRef("else"))
            appendBlock("then")
            ret(Constant.I32(1))
            appendBlock("else")
            ret(Constant.I32(0))
            finalizeFunction()
        }
    }

    @Test
    fun `condBr with phi compiles on all targets`() {
        compileOnAllExcept("wasm") {
            val params = createFunction("condBr_phi", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            val cond = icmp(ICmpPredicate.SGT, params[0], params[1])
            condBr(cond, BlockRef("then"), BlockRef("else"))
            appendBlock("then")
            val thenVal = add(params[0], Constant.I32(1))
            br(BlockRef("merge"))
            appendBlock("else")
            val elseVal = sub(params[1], Constant.I32(1))
            br(BlockRef("merge"))
            appendBlock("merge")
            val result = phi(Type.I32, listOf(thenVal to BlockRef("then"), elseVal to BlockRef("else")))
            ret(result)
            finalizeFunction()
        }
    }

    // --- Control Flow: Switch ---

    @Test
    fun `switch compiles on native targets`() {
        compileOnNative {
            val params = createFunction("switch_test", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            switch(params[0], "default", listOf(
                Constant.I32(0) to "case0",
                Constant.I32(1) to "case1",
                Constant.I32(2) to "case2",
            ))
            appendBlock("case0")
            ret(Constant.I32(10))
            appendBlock("case1")
            ret(Constant.I32(20))
            appendBlock("case2")
            ret(Constant.I32(30))
            appendBlock("default")
            ret(Constant.I32(-1))
            finalizeFunction()
        }
    }

    @Disabled("WASM does not support Switch instruction")
    @Test
    fun `switch compiles on wasm`() {
        compileOn(listOf(wasmBackend())) {
            val params = createFunction("switch_test", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            switch(params[0], "default", listOf(
                Constant.I32(0) to "case0",
                Constant.I32(1) to "case1",
            ))
            appendBlock("case0")
            ret(Constant.I32(10))
            appendBlock("case1")
            ret(Constant.I32(20))
            appendBlock("default")
            ret(Constant.I32(-1))
            finalizeFunction()
        }
    }

    // --- Calls ---

    @Test
    fun `call compiles on all targets`() {
        compileOnAll {
            val helperParams = createFunction("helper", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            ret(add(helperParams[0], Constant.I32(1)))
            finalizeFunction()

            val mainParams = createFunction("caller", listOf(Param("n", Type.I32)), Type.I32)
            appendBlock("entry")
            val result = call("helper", listOf(mainParams[0]), Type.I32)
            ret(result!!)
            finalizeFunction()
        }
    }

    @Test
    fun `void call compiles on all targets`() {
        compileOnAll {
            createFunction("side_effect", listOf(Param("x", Type.I32)), Type.Void)
            appendBlock("entry")
            ret()
            finalizeFunction()

            val mainParams = createFunction("caller_void", listOf(Param("n", Type.I32)), Type.Void)
            appendBlock("entry")
            call("side_effect", listOf(mainParams[0]), Type.Void)
            ret()
            finalizeFunction()
        }
    }

    @Test
    fun `call with multiple args compiles on all targets`() {
        compileOnAll {
            val helperParams = createFunction("add3", listOf(
                Param("a", Type.I32), Param("b", Type.I32), Param("c", Type.I32)
            ), Type.I32)
            appendBlock("entry")
            val ab = add(helperParams[0], helperParams[1])
            ret(add(ab, helperParams[2]))
            finalizeFunction()

            val mainParams = createFunction("use_add3", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            val result = call("add3", listOf(mainParams[0], mainParams[0], mainParams[0]), Type.I32)
            ret(result!!)
            finalizeFunction()
        }
    }

    // --- Select ---

    @Test
    fun `select i32 compiles on all targets`() {
        compileOnAll {
            val params = createFunction("max_fn", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            val cond = icmp(ICmpPredicate.SGT, params[0], params[1])
            val result = select(cond, params[0], params[1])
            ret(result)
            finalizeFunction()
        }
    }

    @Test
    fun `select i64 compiles on all targets`() {
        compileOnAll {
            val params = createFunction("max64", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            appendBlock("entry")
            val cond = icmp(ICmpPredicate.SGT, params[0], params[1])
            val result = select(cond, params[0], params[1])
            ret(result)
            finalizeFunction()
        }
    }

    @Test
    fun `select f64 compiles on all targets`() {
        compileOnAllExcept("x86_64") {
            val params = createFunction("max_f64", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.F64)
            appendBlock("entry")
            val cond = fcmp(FCmpPredicate.OGT, params[0], params[1])
            val result = select(cond, params[0], params[1])
            ret(result)
            finalizeFunction()
        }
    }

    @Test
    fun `select with constants compiles on all targets`() {
        compileOnAll {
            val params = createFunction("clamp_positive", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            val isNeg = icmp(ICmpPredicate.SLT, params[0], Constant.I32(0))
            val result = select(isNeg, Constant.I32(0), params[0])
            ret(result)
            finalizeFunction()
        }
    }

    // --- Alloca, Load, Store (native targets only) ---

    @Test
    fun `alloca load store i32 compiles on native targets`() {
        compileOnNative {
            val params = createFunction("als_i32", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            val ptr = alloca(Type.I32)
            store(params[0], ptr)
            val loaded = load(Type.I32, ptr)
            ret(loaded)
            finalizeFunction()
        }
    }

    @Test
    fun `alloca load store i64 compiles on native targets`() {
        compileOnNative {
            val params = createFunction("als_i64", listOf(Param("x", Type.I64)), Type.I64)
            appendBlock("entry")
            val ptr = alloca(Type.I64)
            store(params[0], ptr)
            val loaded = load(Type.I64, ptr)
            ret(loaded)
            finalizeFunction()
        }
    }

    @Test
    fun `alloca load store f64 compiles on native targets`() {
        compileOnNativeExcept("x86_64") {
            val params = createFunction("als_f64", listOf(Param("x", Type.F64)), Type.F64)
            appendBlock("entry")
            val ptr = alloca(Type.F64)
            store(params[0], ptr)
            val loaded = load(Type.F64, ptr)
            ret(loaded)
            finalizeFunction()
        }
    }

    @Test
    fun `multiple alloca compiles on native targets`() {
        compileOnNative {
            val params = createFunction("multi_alloca", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            val ptrA = alloca(Type.I32)
            val ptrB = alloca(Type.I32)
            store(params[0], ptrA)
            store(params[1], ptrB)
            val a = load(Type.I32, ptrA)
            val b = load(Type.I32, ptrB)
            ret(add(a, b))
            finalizeFunction()
        }
    }

    // --- Neg ---

    @Test
    fun `neg i32 compiles on all targets`() {
        compileOnAll {
            val params = createFunction("neg_i32", listOf(Param("a", Type.I32)), Type.I32)
            appendBlock("entry")
            ret(neg(params[0]))
            finalizeFunction()
        }
    }

    @Test
    fun `neg i64 compiles on all targets`() {
        compileOnAll {
            val params = createFunction("neg_i64", listOf(Param("a", Type.I64)), Type.I64)
            appendBlock("entry")
            ret(neg(params[0]))
            finalizeFunction()
        }
    }

    // --- Not ---

    @Test
    fun `not i32 compiles on all targets`() {
        compileOnAll {
            val params = createFunction("not_i32", listOf(Param("a", Type.I32)), Type.I32)
            appendBlock("entry")
            ret(not(params[0]))
            finalizeFunction()
        }
    }

    @Test
    fun `not i64 compiles on all targets`() {
        compileOnAll {
            val params = createFunction("not_i64", listOf(Param("a", Type.I64)), Type.I64)
            appendBlock("entry")
            ret(not(params[0]))
            finalizeFunction()
        }
    }

    // --- Constants ---

    @Test
    fun `constant i32 zero compiles on all targets`() {
        compileOnAll {
            createFunction("zero", emptyList(), Type.I32)
            appendBlock("entry")
            ret(Constant.I32(0))
            finalizeFunction()
        }
    }

    @Test
    fun `constant i32 neg one compiles on all targets`() {
        compileOnAll {
            createFunction("neg_one", emptyList(), Type.I32)
            appendBlock("entry")
            ret(Constant.I32(-1))
            finalizeFunction()
        }
    }

    @Test
    fun `constant i32 max compiles on all targets`() {
        compileOnAll {
            createFunction("max_int", emptyList(), Type.I32)
            appendBlock("entry")
            ret(Constant.I32(Int.MAX_VALUE))
            finalizeFunction()
        }
    }

    @Test
    fun `constant i32 min compiles on all targets`() {
        compileOnAll {
            createFunction("min_int", emptyList(), Type.I32)
            appendBlock("entry")
            ret(Constant.I32(Int.MIN_VALUE))
            finalizeFunction()
        }
    }

    @Test
    fun `constant i64 large compiles on all targets`() {
        compileOnAll {
            createFunction("big64", emptyList(), Type.I64)
            appendBlock("entry")
            ret(Constant.I64(0x1_0000_0000L))
            finalizeFunction()
        }
    }

    @Test
    fun `constant f64 zero compiles on all targets`() {
        compileOnAllExcept("riscv64") {
            createFunction("f64_zero", emptyList(), Type.F64)
            appendBlock("entry")
            ret(Constant.F64(0.0))
            finalizeFunction()
        }
    }

    @Test
    fun `constant f64 negative compiles on all targets`() {
        compileOnAllExcept("riscv64") {
            createFunction("f64_neg", emptyList(), Type.F64)
            appendBlock("entry")
            ret(Constant.F64(-1.5))
            finalizeFunction()
        }
    }

    @Test
    fun `constant f32 compiles on all targets`() {
        compileOnAllExcept("riscv64") {
            createFunction("f32_const", emptyList(), Type.F32)
            appendBlock("entry")
            ret(Constant.F32(2.5f))
            finalizeFunction()
        }
    }

    @Test
    fun `constant i1 true compiles on all targets`() {
        compileOnAllExcept("x86_64") {
            val params = createFunction("use_bool", listOf(Param("a", Type.I32)), Type.I32)
            appendBlock("entry")
            val result = select(Constant.I1(true), params[0], Constant.I32(0))
            ret(result)
            finalizeFunction()
        }
    }

    // --- Combined patterns ---

    @Test
    fun `arithmetic chain compiles on all targets`() {
        compileOnAll {
            val params = createFunction("chain", listOf(
                Param("a", Type.I32), Param("b", Type.I32), Param("c", Type.I32)
            ), Type.I32)
            appendBlock("entry")
            val ab = mul(params[0], params[1])
            val result = add(ab, params[2])
            ret(result)
            finalizeFunction()
        }
    }

    @Test
    fun `bitwise chain compiles on all targets`() {
        compileOnAll {
            val params = createFunction("bitwise_chain", listOf(
                Param("a", Type.I32), Param("b", Type.I32), Param("c", Type.I32)
            ), Type.I32)
            appendBlock("entry")
            val ab = and(params[0], params[1])
            val bc = or(params[1], params[2])
            val result = xor(ab, bc)
            ret(result)
            finalizeFunction()
        }
    }

    @Test
    fun `fp mixed ops compiles on all targets`() {
        compileOnAll {
            val params = createFunction("fp_mixed", listOf(
                Param("a", Type.F64), Param("b", Type.F64), Param("c", Type.F64)
            ), Type.F64)
            appendBlock("entry")
            val ab = fmul(params[0], params[1])
            val result = fadd(ab, params[2])
            ret(result)
            finalizeFunction()
        }
    }

    @Test
    fun `nested branches compiles on all targets`() {
        compileOnAllExcept("wasm") {
            val params = createFunction("classify", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            val isPositive = icmp(ICmpPredicate.SGT, params[0], Constant.I32(0))
            condBr(isPositive, BlockRef("positive"), BlockRef("check_neg"))
            appendBlock("check_neg")
            val isNegative = icmp(ICmpPredicate.SLT, params[0], Constant.I32(0))
            condBr(isNegative, BlockRef("negative"), BlockRef("zero"))
            appendBlock("positive")
            ret(Constant.I32(1))
            appendBlock("negative")
            ret(Constant.I32(-1))
            appendBlock("zero")
            ret(Constant.I32(0))
            finalizeFunction()
        }
    }

    @Test
    fun `multiple functions in same module compiles on all targets`() {
        compileOnAll {
            val p1 = createFunction("fn_add", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            ret(add(p1[0], p1[1]))
            finalizeFunction()

            val p2 = createFunction("fn_sub", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            ret(sub(p2[0], p2[1]))
            finalizeFunction()

            val p3 = createFunction("fn_mul", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            ret(mul(p3[0], p3[1]))
            finalizeFunction()
        }
    }

    @Test
    fun `many parameters compiles on native targets`() {
        compileOnNative {
            val params = createFunction("sum6", listOf(
                Param("a", Type.I32), Param("b", Type.I32), Param("c", Type.I32),
                Param("d", Type.I32), Param("e", Type.I32), Param("f", Type.I32)
            ), Type.I32)
            appendBlock("entry")
            val ab = add(params[0], params[1])
            val abc = add(ab, params[2])
            val abcd = add(abc, params[3])
            val abcde = add(abcd, params[4])
            val result = add(abcde, params[5])
            ret(result)
            finalizeFunction()
        }
    }

    @Test
    fun `conversion chain compiles on all targets`() {
        compileOnAll {
            val params = createFunction("conv_chain", listOf(Param("a", Type.I32)), Type.F64)
            appendBlock("entry")
            val asI64 = sext(params[0], Type.I64)
            val asF64 = sitofp(asI64, Type.F64)
            ret(asF64)
            finalizeFunction()
        }
    }

    @Test
    fun `icmp all signed predicates with condBr compiles on all targets`() {
        for (pred in listOf(ICmpPredicate.SGT, ICmpPredicate.SGE, ICmpPredicate.SLT, ICmpPredicate.SLE)) {
            compileOnAllExcept("wasm") {
                val params = createFunction("cmp_${pred.name}", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
                appendBlock("entry")
                val cond = icmp(pred, params[0], params[1])
                condBr(cond, BlockRef("then"), BlockRef("else"))
                appendBlock("then")
                ret(Constant.I32(1))
                appendBlock("else")
                ret(Constant.I32(0))
                finalizeFunction()
            }
        }
    }

    @Test
    fun `icmp all unsigned predicates with condBr compiles on all targets`() {
        for (pred in listOf(ICmpPredicate.UGT, ICmpPredicate.UGE, ICmpPredicate.ULT, ICmpPredicate.ULE)) {
            compileOnAllExcept("wasm") {
                val params = createFunction("cmp_${pred.name}", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
                appendBlock("entry")
                val cond = icmp(pred, params[0], params[1])
                condBr(cond, BlockRef("then"), BlockRef("else"))
                appendBlock("then")
                ret(Constant.I32(1))
                appendBlock("else")
                ret(Constant.I32(0))
                finalizeFunction()
            }
        }
    }

    @Test
    fun `shift by constant compiles on all targets`() {
        compileOnAll {
            val params = createFunction("shl_const", listOf(Param("a", Type.I32)), Type.I32)
            appendBlock("entry")
            ret(shl(params[0], Constant.I32(4)))
            finalizeFunction()
        }
    }

    @Test
    fun `lshr by constant compiles on all targets`() {
        compileOnAll {
            val params = createFunction("lshr_const", listOf(Param("a", Type.I32)), Type.I32)
            appendBlock("entry")
            ret(lshr(params[0], Constant.I32(4)))
            finalizeFunction()
        }
    }

    @Test
    fun `ashr by constant compiles on all targets`() {
        compileOnAll {
            val params = createFunction("ashr_const", listOf(Param("a", Type.I32)), Type.I32)
            appendBlock("entry")
            ret(ashr(params[0], Constant.I32(4)))
            finalizeFunction()
        }
    }

    @Test
    fun `add with constant compiles on all targets`() {
        compileOnAll {
            val params = createFunction("add_const", listOf(Param("a", Type.I32)), Type.I32)
            appendBlock("entry")
            ret(add(params[0], Constant.I32(100)))
            finalizeFunction()
        }
    }

    @Test
    fun `sub with constant compiles on all targets`() {
        compileOnAll {
            val params = createFunction("sub_const", listOf(Param("a", Type.I32)), Type.I32)
            appendBlock("entry")
            ret(sub(params[0], Constant.I32(100)))
            finalizeFunction()
        }
    }

    @Test
    fun `mul with constant compiles on all targets`() {
        compileOnAll {
            val params = createFunction("mul_const", listOf(Param("a", Type.I32)), Type.I32)
            appendBlock("entry")
            ret(mul(params[0], Constant.I32(7)))
            finalizeFunction()
        }
    }

    @Test
    fun `diamond cfg with arithmetic compiles on all targets`() {
        compileOnAllExcept("wasm") {
            val params = createFunction("abs_diff", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            val cond = icmp(ICmpPredicate.SGT, params[0], params[1])
            condBr(cond, BlockRef("a_gt"), BlockRef("b_gt"))
            appendBlock("a_gt")
            val diffA = sub(params[0], params[1])
            br(BlockRef("done"))
            appendBlock("b_gt")
            val diffB = sub(params[1], params[0])
            br(BlockRef("done"))
            appendBlock("done")
            val result = phi(Type.I32, listOf(diffA to BlockRef("a_gt"), diffB to BlockRef("b_gt")))
            ret(result)
            finalizeFunction()
        }
    }

    @Test
    fun `multiple phi nodes compiles on all targets`() {
        compileOnAllExcept("wasm") {
            val params = createFunction("multi_phi", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            val cond = icmp(ICmpPredicate.SGT, params[0], params[1])
            condBr(cond, BlockRef("left"), BlockRef("right"))
            appendBlock("left")
            val leftSum = add(params[0], Constant.I32(1))
            val leftDiff = sub(params[0], Constant.I32(1))
            br(BlockRef("merge"))
            appendBlock("right")
            val rightSum = add(params[1], Constant.I32(2))
            val rightDiff = sub(params[1], Constant.I32(2))
            br(BlockRef("merge"))
            appendBlock("merge")
            val phiSum = phi(Type.I32, listOf(leftSum to BlockRef("left"), rightSum to BlockRef("right")))
            val phiDiff = phi(Type.I32, listOf(leftDiff to BlockRef("left"), rightDiff to BlockRef("right")))
            val result = add(phiSum, phiDiff)
            ret(result)
            finalizeFunction()
        }
    }

    @Test
    fun `i64 shifts compiles on all targets`() {
        compileOnAll {
            val params = createFunction("shl64", listOf(Param("a", Type.I64)), Type.I64)
            appendBlock("entry")
            ret(shl(params[0], Constant.I64(4)))
            finalizeFunction()
        }
    }

    @Test
    fun `i64 lshr compiles on all targets`() {
        compileOnAll {
            val params = createFunction("lshr64", listOf(Param("a", Type.I64)), Type.I64)
            appendBlock("entry")
            ret(lshr(params[0], Constant.I64(4)))
            finalizeFunction()
        }
    }

    @Test
    fun `i64 ashr compiles on all targets`() {
        compileOnAll {
            val params = createFunction("ashr64", listOf(Param("a", Type.I64)), Type.I64)
            appendBlock("entry")
            ret(ashr(params[0], Constant.I64(4)))
            finalizeFunction()
        }
    }

    @Test
    fun `i64 bitwise ops compiles on all targets`() {
        compileOnAll {
            val params = createFunction("bitwise64", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            appendBlock("entry")
            val ab = and(params[0], params[1])
            val result = or(ab, xor(params[0], params[1]))
            ret(result)
            finalizeFunction()
        }
    }

    @Test
    fun `neg then add compiles on all targets`() {
        compileOnAll {
            val params = createFunction("neg_add", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            val negA = neg(params[0])
            ret(add(negA, params[1]))
            finalizeFunction()
        }
    }

    @Test
    fun `not then and compiles on all targets`() {
        compileOnAll {
            val params = createFunction("not_and", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            val notA = not(params[0])
            ret(and(notA, params[1]))
            finalizeFunction()
        }
    }

    @Test
    fun `identity function compiles on all targets`() {
        compileOnAll {
            val params = createFunction("identity", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            ret(params[0])
            finalizeFunction()
        }
    }

    @Test
    fun `fptoui f64 to i32 compiles on native and wasm`() {
        compileOnNativeAndWasm {
            val params = createFunction("fptoui_f64_i32", listOf(Param("a", Type.F64)), Type.I32)
            appendBlock("entry")
            ret(fptoui(params[0], Type.I32))
            finalizeFunction()
        }
    }

    @Test
    fun `uitofp i32 to f64 compiles on native and wasm`() {
        compileOnNativeAndWasm {
            val params = createFunction("uitofp_i32_f64", listOf(Param("a", Type.I32)), Type.F64)
            appendBlock("entry")
            ret(uitofp(params[0], Type.F64))
            finalizeFunction()
        }
    }

    @Test
    fun `icmp i64 EQ compiles on all targets`() {
        compileOnAll {
            val params = createFunction("eq64", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I32)
            appendBlock("entry")
            val cond = icmp(ICmpPredicate.EQ, params[0], params[1])
            val result = select(cond, Constant.I32(1), Constant.I32(0))
            ret(result)
            finalizeFunction()
        }
    }

    @Test
    fun `icmp i64 SGT compiles on all targets`() {
        compileOnAll {
            val params = createFunction("sgt64", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I32)
            appendBlock("entry")
            val cond = icmp(ICmpPredicate.SGT, params[0], params[1])
            val result = select(cond, Constant.I32(1), Constant.I32(0))
            ret(result)
            finalizeFunction()
        }
    }

    @Test
    fun `select f32 compiles on all targets`() {
        compileOnAllExcept("x86_64") {
            val params = createFunction("max_f32", listOf(Param("a", Type.F32), Param("b", Type.F32)), Type.F32)
            appendBlock("entry")
            val cond = fcmp(FCmpPredicate.OGT, params[0], params[1])
            val result = select(cond, params[0], params[1])
            ret(result)
            finalizeFunction()
        }
    }

    @Test
    fun `constant i64 zero compiles on all targets`() {
        compileOnAll {
            createFunction("i64_zero", emptyList(), Type.I64)
            appendBlock("entry")
            ret(Constant.I64(0L))
            finalizeFunction()
        }
    }

    @Test
    fun `constant i64 neg one compiles on all targets`() {
        compileOnAll {
            createFunction("i64_neg_one", emptyList(), Type.I64)
            appendBlock("entry")
            ret(Constant.I64(-1L))
            finalizeFunction()
        }
    }

    @Test
    fun `trunc then zext round trip compiles on native and wasm`() {
        compileOnNativeAndWasm {
            val params = createFunction("trunc_zext", listOf(Param("a", Type.I32)), Type.I32)
            appendBlock("entry")
            val truncated = trunc(params[0], Type.I8)
            val extended = zext(truncated, Type.I32)
            ret(extended)
            finalizeFunction()
        }
    }

    @Test
    fun `sext then trunc round trip compiles on all targets`() {
        compileOnAll {
            val params = createFunction("sext_trunc", listOf(Param("a", Type.I32)), Type.I32)
            appendBlock("entry")
            val extended = sext(params[0], Type.I64)
            val truncated = trunc(extended, Type.I32)
            ret(truncated)
            finalizeFunction()
        }
    }

    @Test
    fun `fpext then fptrunc round trip compiles on all targets`() {
        compileOnAll {
            val params = createFunction("fpext_fptrunc", listOf(Param("a", Type.F32)), Type.F32)
            appendBlock("entry")
            val extended = fpext(params[0], Type.F64)
            val truncated = fptrunc(extended, Type.F32)
            ret(truncated)
            finalizeFunction()
        }
    }

    @Test
    fun `sitofp then fptosi round trip compiles on all targets`() {
        compileOnAll {
            val params = createFunction("sitofp_fptosi", listOf(Param("a", Type.I32)), Type.I32)
            appendBlock("entry")
            val fp = sitofp(params[0], Type.F64)
            val back = fptosi(fp, Type.I32)
            ret(back)
            finalizeFunction()
        }
    }

    @Test
    fun `complex expression a times b plus c minus d compiles on all targets`() {
        compileOnAll {
            val params = createFunction("expr", listOf(
                Param("a", Type.I32), Param("b", Type.I32),
                Param("c", Type.I32), Param("d", Type.I32)
            ), Type.I32)
            appendBlock("entry")
            val ab = mul(params[0], params[1])
            val abc = add(ab, params[2])
            val result = sub(abc, params[3])
            ret(result)
            finalizeFunction()
        }
    }

    @Test
    fun `fp expression a times b minus c divided by d compiles on all targets`() {
        compileOnAll {
            val params = createFunction("fp_expr", listOf(
                Param("a", Type.F64), Param("b", Type.F64),
                Param("c", Type.F64), Param("d", Type.F64)
            ), Type.F64)
            appendBlock("entry")
            val ab = fmul(params[0], params[1])
            val abc = fsub(ab, params[2])
            val result = fdiv(abc, params[3])
            ret(result)
            finalizeFunction()
        }
    }

    @Test
    fun `alloca store load modify store load on native targets`() {
        compileOnNative {
            val params = createFunction("modify", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            val ptr = alloca(Type.I32)
            store(params[0], ptr)
            val v1 = load(Type.I32, ptr)
            val v2 = add(v1, Constant.I32(1))
            store(v2, ptr)
            val result = load(Type.I32, ptr)
            ret(result)
            finalizeFunction()
        }
    }

    @Test
    fun `function with no params returning constant compiles on all targets`() {
        compileOnAll {
            createFunction("get_answer", emptyList(), Type.I32)
            appendBlock("entry")
            ret(Constant.I32(42))
            finalizeFunction()
        }
    }
}
