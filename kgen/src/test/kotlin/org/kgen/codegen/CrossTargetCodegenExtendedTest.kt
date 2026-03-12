package org.kgen.codegen

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*
import org.kgen.ir.build.IrBuilder
import org.kgen.ir.target.Target
import org.kgen.ir.verify.IrVerifier
import org.kgen.target.x86.codegen.X86CodeGenerator
import org.kgen.target.arm64.codegen.Arm64CodeGenerator
import org.kgen.target.riscv.codegen.RiscVCodeGenerator
import org.kgen.target.wasm.codegen.WasmCodeGenerator
import org.kgen.binary.SectionKind

class CrossTargetCodegenExtendedTest {

    private fun buildModule(target: Target, block: IrBuilder.() -> Unit): Module {
        val ir = IrBuilder("cross_target_ext", target)
        ir.block()
        val mod = ir.build()
        assertTrue(IrVerifier.verify(mod).isValid, "IR should be valid before codegen")
        return mod
    }

    private fun compileX86(block: IrBuilder.() -> Unit) {
        val obj = X86CodeGenerator().generateObjectFile(buildModule(Target.x86_64(), block))
        assertTrue(obj.sections.any { it.kind == SectionKind.TEXT && it.data.isNotEmpty() })
    }

    private fun compileArm64(block: IrBuilder.() -> Unit) {
        val obj = Arm64CodeGenerator().generateObjectFile(buildModule(Target.arm64(), block))
        assertTrue(obj.sections.any { it.kind == SectionKind.TEXT && it.data.isNotEmpty() })
    }

    private fun compileRiscV(block: IrBuilder.() -> Unit) {
        val obj = RiscVCodeGenerator().generateObjectFile(buildModule(Target.riscv64(), block))
        assertTrue(obj.sections.any { it.kind == SectionKind.TEXT && it.data.isNotEmpty() })
    }

    private fun compileWasm(block: IrBuilder.() -> Unit) {
        val bytes = WasmCodeGenerator().generate(buildModule(Target.wasm(), block))
        assertTrue(bytes.size > 8)
    }

    private fun compileAllNative(block: IrBuilder.() -> Unit) {
        compileX86(block)
        compileArm64(block)
        compileRiscV(block)
    }

    private fun compileX86AndArm64(block: IrBuilder.() -> Unit) {
        compileX86(block)
        compileArm64(block)
    }

    // --- 1. Integer udiv on all native targets ---

    @Test
    fun udivCompilesOnX86() {
        compileX86 {
            val params = createFunction("udiv_fn", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            ret(udiv(params[0], params[1]))
            finalizeFunction()
        }
    }

    @Test
    fun udivCompilesOnArm64() {
        compileArm64 {
            val params = createFunction("udiv_fn", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            ret(udiv(params[0], params[1]))
            finalizeFunction()
        }
    }

    @Test
    fun udivCompilesOnRiscV() {
        compileRiscV {
            val params = createFunction("udiv_fn", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            ret(udiv(params[0], params[1]))
            finalizeFunction()
        }
    }

    // --- 2. Integer urem on all native targets ---

    @Test
    fun uremCompilesOnX86() {
        compileX86 {
            val params = createFunction("urem_fn", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            ret(urem(params[0], params[1]))
            finalizeFunction()
        }
    }

    @Test
    fun uremCompilesOnArm64() {
        compileArm64 {
            val params = createFunction("urem_fn", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            ret(urem(params[0], params[1]))
            finalizeFunction()
        }
    }

    @Test
    fun uremCompilesOnRiscV() {
        compileRiscV {
            val params = createFunction("urem_fn", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            ret(urem(params[0], params[1]))
            finalizeFunction()
        }
    }

    // --- 3. Integer srem on all native targets ---

    @Test
    fun sremCompilesOnX86() {
        compileX86 {
            val params = createFunction("srem_fn", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            ret(srem(params[0], params[1]))
            finalizeFunction()
        }
    }

    @Test
    fun sremCompilesOnArm64() {
        compileArm64 {
            val params = createFunction("srem_fn", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            ret(srem(params[0], params[1]))
            finalizeFunction()
        }
    }

    @Test
    fun sremCompilesOnRiscV() {
        compileRiscV {
            val params = createFunction("srem_fn", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            ret(srem(params[0], params[1]))
            finalizeFunction()
        }
    }

    // --- 4. lshr (logical shift right) on all native targets ---

    @Test
    fun lshrCompilesOnX86() {
        compileX86 {
            val params = createFunction("lshr_fn", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            ret(lshr(params[0], params[1]))
            finalizeFunction()
        }
    }

    @Test
    fun lshrCompilesOnArm64() {
        compileArm64 {
            val params = createFunction("lshr_fn", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            ret(lshr(params[0], params[1]))
            finalizeFunction()
        }
    }

    @Test
    fun lshrCompilesOnRiscV() {
        compileRiscV {
            val params = createFunction("lshr_fn", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            ret(lshr(params[0], params[1]))
            finalizeFunction()
        }
    }

    // --- 5. ashr (arithmetic shift right) on all native targets ---

    @Test
    fun ashrCompilesOnX86() {
        compileX86 {
            val params = createFunction("ashr_fn", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            ret(ashr(params[0], params[1]))
            finalizeFunction()
        }
    }

    @Test
    fun ashrCompilesOnArm64() {
        compileArm64 {
            val params = createFunction("ashr_fn", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            ret(ashr(params[0], params[1]))
            finalizeFunction()
        }
    }

    @Test
    fun ashrCompilesOnRiscV() {
        compileRiscV {
            val params = createFunction("ashr_fn", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            ret(ashr(params[0], params[1]))
            finalizeFunction()
        }
    }

    // --- 6. select instruction on x86 and ARM64 ---

    @Test
    fun selectCompilesOnX86() {
        compileX86 {
            val params = createFunction("max", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val cond = icmp(ICmpPredicate.SGT, params[0], params[1])
            val result = select(cond, params[0], params[1])
            ret(result)
            finalizeFunction()
        }
    }

    @Test
    fun selectCompilesOnArm64() {
        compileArm64 {
            val params = createFunction("max", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val cond = icmp(ICmpPredicate.SGT, params[0], params[1])
            val result = select(cond, params[0], params[1])
            ret(result)
            finalizeFunction()
        }
    }

    // --- 7. icmp with all predicates + condBr on x86 and ARM64 ---

    @Test
    fun icmpSgtCondBrCompilesOnX86AndArm64() {
        compileX86AndArm64 {
            val params = createFunction("cmp_sgt", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val cond = icmp(ICmpPredicate.SGT, params[0], params[1])
            condBr(cond, "then", "else")
            positionAtEnd(appendBlock("then"))
            ret(Constant.I32(1))
            positionAtEnd(appendBlock("else"))
            ret(Constant.I32(0))
            finalizeFunction()
        }
    }

    @Test
    fun icmpSltCondBrCompilesOnX86AndArm64() {
        compileX86AndArm64 {
            val params = createFunction("cmp_slt", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val cond = icmp(ICmpPredicate.SLT, params[0], params[1])
            condBr(cond, "then", "else")
            positionAtEnd(appendBlock("then"))
            ret(Constant.I32(1))
            positionAtEnd(appendBlock("else"))
            ret(Constant.I32(0))
            finalizeFunction()
        }
    }

    @Test
    fun icmpSgeCondBrCompilesOnX86AndArm64() {
        compileX86AndArm64 {
            val params = createFunction("cmp_sge", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val cond = icmp(ICmpPredicate.SGE, params[0], params[1])
            condBr(cond, "then", "else")
            positionAtEnd(appendBlock("then"))
            ret(Constant.I32(1))
            positionAtEnd(appendBlock("else"))
            ret(Constant.I32(0))
            finalizeFunction()
        }
    }

    @Test
    fun icmpSleCondBrCompilesOnX86AndArm64() {
        compileX86AndArm64 {
            val params = createFunction("cmp_sle", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val cond = icmp(ICmpPredicate.SLE, params[0], params[1])
            condBr(cond, "then", "else")
            positionAtEnd(appendBlock("then"))
            ret(Constant.I32(1))
            positionAtEnd(appendBlock("else"))
            ret(Constant.I32(0))
            finalizeFunction()
        }
    }

    @Test
    fun icmpEqCondBrCompilesOnX86AndArm64() {
        compileX86AndArm64 {
            val params = createFunction("cmp_eq", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val cond = icmp(ICmpPredicate.EQ, params[0], params[1])
            condBr(cond, "then", "else")
            positionAtEnd(appendBlock("then"))
            ret(Constant.I32(1))
            positionAtEnd(appendBlock("else"))
            ret(Constant.I32(0))
            finalizeFunction()
        }
    }

    @Test
    fun icmpNeCondBrCompilesOnX86AndArm64() {
        compileX86AndArm64 {
            val params = createFunction("cmp_ne", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val cond = icmp(ICmpPredicate.NE, params[0], params[1])
            condBr(cond, "then", "else")
            positionAtEnd(appendBlock("then"))
            ret(Constant.I32(1))
            positionAtEnd(appendBlock("else"))
            ret(Constant.I32(0))
            finalizeFunction()
        }
    }

    // --- 8. Multiple phi nodes in same block ---

    @Test
    fun multiplePhiNodesOnX86() {
        compileX86 {
            val params = createFunction("multi_phi", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val cond = icmp(ICmpPredicate.SGT, params[0], params[1])
            condBr(cond, "left", "right")
            positionAtEnd(appendBlock("left"))
            val leftSum = add(params[0], Constant.I32(1))
            val leftDiff = sub(params[0], Constant.I32(1))
            br("merge")
            positionAtEnd(appendBlock("right"))
            val rightSum = add(params[1], Constant.I32(2))
            val rightDiff = sub(params[1], Constant.I32(2))
            br("merge")
            positionAtEnd(appendBlock("merge"))
            val phiSum = phi(Type.I32, listOf(leftSum to "left", rightSum to "right"))
            val phiDiff = phi(Type.I32, listOf(leftDiff to "left", rightDiff to "right"))
            val result = add(phiSum, phiDiff)
            ret(result)
            finalizeFunction()
        }
    }

    @Test
    fun multiplePhiNodesOnArm64() {
        compileArm64 {
            val params = createFunction("multi_phi", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val cond = icmp(ICmpPredicate.SGT, params[0], params[1])
            condBr(cond, "left", "right")
            positionAtEnd(appendBlock("left"))
            val leftSum = add(params[0], Constant.I32(1))
            val leftDiff = sub(params[0], Constant.I32(1))
            br("merge")
            positionAtEnd(appendBlock("right"))
            val rightSum = add(params[1], Constant.I32(2))
            val rightDiff = sub(params[1], Constant.I32(2))
            br("merge")
            positionAtEnd(appendBlock("merge"))
            val phiSum = phi(Type.I32, listOf(leftSum to "left", rightSum to "right"))
            val phiDiff = phi(Type.I32, listOf(leftDiff to "left", rightDiff to "right"))
            val result = add(phiSum, phiDiff)
            ret(result)
            finalizeFunction()
        }
    }

    // --- 9. Chain of arithmetic (a+b*c-d) on all native targets ---

    @Test
    fun arithmeticChainCompilesOnX86() {
        compileX86 {
            val params = createFunction("chain", listOf(
                Param("a", Type.I32), Param("b", Type.I32),
                Param("c", Type.I32), Param("d", Type.I32)
            ), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val bc = mul(params[1], params[2])
            val abc = add(params[0], bc)
            val result = sub(abc, params[3])
            ret(result)
            finalizeFunction()
        }
    }

    @Test
    fun arithmeticChainCompilesOnArm64() {
        compileArm64 {
            val params = createFunction("chain", listOf(
                Param("a", Type.I32), Param("b", Type.I32),
                Param("c", Type.I32), Param("d", Type.I32)
            ), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val bc = mul(params[1], params[2])
            val abc = add(params[0], bc)
            val result = sub(abc, params[3])
            ret(result)
            finalizeFunction()
        }
    }

    @Test
    fun arithmeticChainCompilesOnRiscV() {
        compileRiscV {
            val params = createFunction("chain", listOf(
                Param("a", Type.I32), Param("b", Type.I32),
                Param("c", Type.I32), Param("d", Type.I32)
            ), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val bc = mul(params[1], params[2])
            val abc = add(params[0], bc)
            val result = sub(abc, params[3])
            ret(result)
            finalizeFunction()
        }
    }

    // --- 10. Nested branches (if-elseif-else pattern) on x86 and ARM64 ---

    @Test
    fun nestedBranchesCompilesOnX86() {
        compileX86 {
            val params = createFunction("classify", listOf(Param("x", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val isPositive = icmp(ICmpPredicate.SGT, params[0], Constant.I32(0))
            condBr(isPositive, "positive", "check_neg")
            positionAtEnd(appendBlock("check_neg"))
            val isNegative = icmp(ICmpPredicate.SLT, params[0], Constant.I32(0))
            condBr(isNegative, "negative", "zero")
            positionAtEnd(appendBlock("positive"))
            ret(Constant.I32(1))
            positionAtEnd(appendBlock("negative"))
            ret(Constant.I32(-1))
            positionAtEnd(appendBlock("zero"))
            ret(Constant.I32(0))
            finalizeFunction()
        }
    }

    @Test
    fun nestedBranchesCompilesOnArm64() {
        compileArm64 {
            val params = createFunction("classify", listOf(Param("x", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val isPositive = icmp(ICmpPredicate.SGT, params[0], Constant.I32(0))
            condBr(isPositive, "positive", "check_neg")
            positionAtEnd(appendBlock("check_neg"))
            val isNegative = icmp(ICmpPredicate.SLT, params[0], Constant.I32(0))
            condBr(isNegative, "negative", "zero")
            positionAtEnd(appendBlock("positive"))
            ret(Constant.I32(1))
            positionAtEnd(appendBlock("negative"))
            ret(Constant.I32(-1))
            positionAtEnd(appendBlock("zero"))
            ret(Constant.I32(0))
            finalizeFunction()
        }
    }

    // --- 11. i64 arithmetic (sub, mul, sdiv) on all native targets ---

    @Test
    fun i64SubCompilesOnAllNative() {
        compileAllNative {
            val params = createFunction("sub64", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            positionAtEnd(appendBlock("entry"))
            ret(sub(params[0], params[1]))
            finalizeFunction()
        }
    }

    @Test
    fun i64MulCompilesOnAllNative() {
        compileAllNative {
            val params = createFunction("mul64", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            positionAtEnd(appendBlock("entry"))
            ret(mul(params[0], params[1]))
            finalizeFunction()
        }
    }

    @Test
    fun i64SdivCompilesOnAllNative() {
        compileAllNative {
            val params = createFunction("sdiv64", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            positionAtEnd(appendBlock("entry"))
            ret(sdiv(params[0], params[1]))
            finalizeFunction()
        }
    }

    // --- 12. Constant return values (0, -1, max_int, min_int) on x86 and ARM64 ---

    @Test
    fun constantZeroReturnOnX86AndArm64() {
        compileX86AndArm64 {
            createFunction("zero", emptyList(), Type.I32)
            positionAtEnd(appendBlock("entry"))
            ret(Constant.I32(0))
            finalizeFunction()
        }
    }

    @Test
    fun constantNegOneReturnOnX86AndArm64() {
        compileX86AndArm64 {
            createFunction("neg_one", emptyList(), Type.I32)
            positionAtEnd(appendBlock("entry"))
            ret(Constant.I32(-1))
            finalizeFunction()
        }
    }

    @Test
    fun constantMaxIntReturnOnX86AndArm64() {
        compileX86AndArm64 {
            createFunction("max_int", emptyList(), Type.I32)
            positionAtEnd(appendBlock("entry"))
            ret(Constant.I32(Int.MAX_VALUE))
            finalizeFunction()
        }
    }

    @Test
    fun constantMinIntReturnOnX86AndArm64() {
        compileX86AndArm64 {
            createFunction("min_int", emptyList(), Type.I32)
            positionAtEnd(appendBlock("entry"))
            ret(Constant.I32(Int.MIN_VALUE))
            finalizeFunction()
        }
    }

    // --- 13. fsub on x86 and ARM64 ---

    @Test
    fun fsubCompilesOnX86() {
        compileX86 {
            val params = createFunction("fsub_fn", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.F64)
            positionAtEnd(appendBlock("entry"))
            ret(fsub(params[0], params[1]))
            finalizeFunction()
        }
    }

    @Test
    fun fsubCompilesOnArm64() {
        compileArm64 {
            val params = createFunction("fsub_fn", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.F64)
            positionAtEnd(appendBlock("entry"))
            ret(fsub(params[0], params[1]))
            finalizeFunction()
        }
    }

    // --- 14. fdiv on x86 and ARM64 ---

    @Test
    fun fdivCompilesOnX86() {
        compileX86 {
            val params = createFunction("fdiv_fn", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.F64)
            positionAtEnd(appendBlock("entry"))
            ret(fdiv(params[0], params[1]))
            finalizeFunction()
        }
    }

    @Test
    fun fdivCompilesOnArm64() {
        compileArm64 {
            val params = createFunction("fdiv_fn", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.F64)
            positionAtEnd(appendBlock("entry"))
            ret(fdiv(params[0], params[1]))
            finalizeFunction()
        }
    }

    // --- 15. F32 arithmetic (fadd, fmul) on x86 and ARM64 ---

    @Test
    fun f32AddCompilesOnX86() {
        compileX86 {
            val params = createFunction("f32add", listOf(Param("a", Type.F32), Param("b", Type.F32)), Type.F32)
            positionAtEnd(appendBlock("entry"))
            ret(fadd(params[0], params[1]))
            finalizeFunction()
        }
    }

    @Test
    fun f32AddCompilesOnArm64() {
        compileArm64 {
            val params = createFunction("f32add", listOf(Param("a", Type.F32), Param("b", Type.F32)), Type.F32)
            positionAtEnd(appendBlock("entry"))
            ret(fadd(params[0], params[1]))
            finalizeFunction()
        }
    }

    @Test
    fun f32MulCompilesOnX86() {
        compileX86 {
            val params = createFunction("f32mul", listOf(Param("a", Type.F32), Param("b", Type.F32)), Type.F32)
            positionAtEnd(appendBlock("entry"))
            ret(fmul(params[0], params[1]))
            finalizeFunction()
        }
    }

    @Test
    fun f32MulCompilesOnArm64() {
        compileArm64 {
            val params = createFunction("f32mul", listOf(Param("a", Type.F32), Param("b", Type.F32)), Type.F32)
            positionAtEnd(appendBlock("entry"))
            ret(fmul(params[0], params[1]))
            finalizeFunction()
        }
    }

    // --- 16. Multiple functions calling each other on x86 and ARM64 ---

    @Test
    fun functionCallCompilesOnX86() {
        val mod = buildModule(Target.x86_64()) {
            val helperParams = createFunction("helper", listOf(Param("x", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            ret(add(helperParams[0], Constant.I32(1)))
            finalizeFunction()

            val mainParams = createFunction("main_fn", listOf(Param("n", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val result = call("helper", listOf(mainParams[0]), Type.I32)
            ret(result!!)
            finalizeFunction()
        }
        val obj = X86CodeGenerator().generateObjectFile(mod)
        assertTrue(obj.sections.any { it.kind == SectionKind.TEXT && it.data.isNotEmpty() })
        assertTrue(obj.symbols.any { it.name == "helper" })
        assertTrue(obj.symbols.any { it.name == "main_fn" })
    }

    @Test
    fun functionCallCompilesOnArm64() {
        val mod = buildModule(Target.arm64()) {
            val helperParams = createFunction("helper", listOf(Param("x", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            ret(add(helperParams[0], Constant.I32(1)))
            finalizeFunction()

            val mainParams = createFunction("main_fn", listOf(Param("n", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val result = call("helper", listOf(mainParams[0]), Type.I32)
            ret(result!!)
            finalizeFunction()
        }
        val obj = Arm64CodeGenerator().generateObjectFile(mod)
        assertTrue(obj.sections.any { it.kind == SectionKind.TEXT && it.data.isNotEmpty() })
        assertTrue(obj.symbols.any { it.name == "helper" })
        assertTrue(obj.symbols.any { it.name == "main_fn" })
    }

    // --- 17. Function with many parameters (6+) on x86 ---

    @Test
    fun manyParametersCompilesOnX86() {
        compileX86 {
            val params = createFunction("sum6", listOf(
                Param("a", Type.I32), Param("b", Type.I32), Param("c", Type.I32),
                Param("d", Type.I32), Param("e", Type.I32), Param("f", Type.I32)
            ), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val ab = add(params[0], params[1])
            val abc = add(ab, params[2])
            val abcd = add(abc, params[3])
            val abcde = add(abcd, params[4])
            val result = add(abcde, params[5])
            ret(result)
            finalizeFunction()
        }
    }

    // --- 18. Empty function (just ret void) on all native targets ---

    @Test
    fun emptyVoidFunctionCompilesOnAllNative() {
        compileAllNative {
            createFunction("empty", emptyList(), Type.Void)
            positionAtEnd(appendBlock("entry"))
            ret()
            finalizeFunction()
        }
    }

    // --- 19. WASM i64 operations ---

    @Test
    fun wasmI64Add() {
        compileWasm {
            val params = createFunction("add64", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            positionAtEnd(appendBlock("entry"))
            ret(add(params[0], params[1]))
            finalizeFunction()
        }
    }

    @Test
    fun wasmI64Sub() {
        compileWasm {
            val params = createFunction("sub64", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            positionAtEnd(appendBlock("entry"))
            ret(sub(params[0], params[1]))
            finalizeFunction()
        }
    }

    @Test
    fun wasmI64Mul() {
        compileWasm {
            val params = createFunction("mul64", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            positionAtEnd(appendBlock("entry"))
            ret(mul(params[0], params[1]))
            finalizeFunction()
        }
    }

    // --- 20. WASM FP operations (fadd, fmul F64) ---

    @Test
    fun wasmFaddF64() {
        compileWasm {
            val params = createFunction("fadd64", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.F64)
            positionAtEnd(appendBlock("entry"))
            ret(fadd(params[0], params[1]))
            finalizeFunction()
        }
    }

    @Test
    fun wasmFmulF64() {
        compileWasm {
            val params = createFunction("fmul64", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.F64)
            positionAtEnd(appendBlock("entry"))
            ret(fmul(params[0], params[1]))
            finalizeFunction()
        }
    }

    // --- Additional tests for coverage ---

    @Test
    fun i64UdivCompilesOnAllNative() {
        compileAllNative {
            val params = createFunction("udiv64", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            positionAtEnd(appendBlock("entry"))
            ret(udiv(params[0], params[1]))
            finalizeFunction()
        }
    }

    @Test
    fun i64ShlCompilesOnX86() {
        compileX86 {
            val params = createFunction("shl64", listOf(Param("a", Type.I64)), Type.I64)
            positionAtEnd(appendBlock("entry"))
            ret(shl(params[0], Constant.I64(4)))
            finalizeFunction()
        }
    }

    @Test
    fun i64LshrCompilesOnX86() {
        compileX86 {
            val params = createFunction("lshr64", listOf(Param("a", Type.I64)), Type.I64)
            positionAtEnd(appendBlock("entry"))
            ret(lshr(params[0], Constant.I64(4)))
            finalizeFunction()
        }
    }

    @Test
    fun i64AshrCompilesOnX86() {
        compileX86 {
            val params = createFunction("ashr64", listOf(Param("a", Type.I64)), Type.I64)
            positionAtEnd(appendBlock("entry"))
            ret(ashr(params[0], Constant.I64(4)))
            finalizeFunction()
        }
    }

    @Test
    fun bitwiseAndOrXorChainOnX86() {
        compileX86 {
            val params = createFunction("bitwise_chain", listOf(
                Param("a", Type.I32), Param("b", Type.I32), Param("c", Type.I32)
            ), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val ab = and(params[0], params[1])
            val result = or(ab, xor(params[1], params[2]))
            ret(result)
            finalizeFunction()
        }
    }

    @Test
    fun bitwiseAndOrXorChainOnArm64() {
        compileArm64 {
            val params = createFunction("bitwise_chain", listOf(
                Param("a", Type.I32), Param("b", Type.I32), Param("c", Type.I32)
            ), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val ab = and(params[0], params[1])
            val result = or(ab, xor(params[1], params[2]))
            ret(result)
            finalizeFunction()
        }
    }

    @Test
    fun selectWithConstantsOnX86() {
        compileX86 {
            val params = createFunction("clamp_sign", listOf(Param("x", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val isNeg = icmp(ICmpPredicate.SLT, params[0], Constant.I32(0))
            val result = select(isNeg, Constant.I32(0), params[0])
            ret(result)
            finalizeFunction()
        }
    }

    @Test
    fun selectWithConstantsOnArm64() {
        compileArm64 {
            val params = createFunction("clamp_sign", listOf(Param("x", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val isNeg = icmp(ICmpPredicate.SLT, params[0], Constant.I32(0))
            val result = select(isNeg, Constant.I32(0), params[0])
            ret(result)
            finalizeFunction()
        }
    }

    @Test
    fun constantI64ReturnOnX86AndArm64() {
        compileX86AndArm64 {
            createFunction("big_const", emptyList(), Type.I64)
            positionAtEnd(appendBlock("entry"))
            ret(Constant.I64(0x1_0000_0000L))
            finalizeFunction()
        }
    }

    @Test
    fun wasmAndOrXorI32() {
        compileWasm {
            val params = createFunction("bitops", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val r1 = and(params[0], params[1])
            val r2 = or(r1, params[1])
            val result = xor(r2, params[0])
            ret(result)
            finalizeFunction()
        }
    }

    @Test
    fun wasmFsubF64() {
        compileWasm {
            val params = createFunction("fsub64", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.F64)
            positionAtEnd(appendBlock("entry"))
            ret(fsub(params[0], params[1]))
            finalizeFunction()
        }
    }

    @Test
    fun wasmFdivF64() {
        compileWasm {
            val params = createFunction("fdiv64", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.F64)
            positionAtEnd(appendBlock("entry"))
            ret(fdiv(params[0], params[1]))
            finalizeFunction()
        }
    }

    @Test
    fun wasmConstantReturn() {
        compileWasm {
            createFunction("const42", emptyList(), Type.I32)
            positionAtEnd(appendBlock("entry"))
            ret(Constant.I32(42))
            finalizeFunction()
        }
    }

    @Test
    fun diamondCfgWithArithmeticOnX86() {
        compileX86 {
            val params = createFunction("abs_diff", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val cond = icmp(ICmpPredicate.SGT, params[0], params[1])
            condBr(cond, "a_greater", "b_greater")
            positionAtEnd(appendBlock("a_greater"))
            val diffA = sub(params[0], params[1])
            br("done")
            positionAtEnd(appendBlock("b_greater"))
            val diffB = sub(params[1], params[0])
            br("done")
            positionAtEnd(appendBlock("done"))
            val result = phi(Type.I32, listOf(diffA to "a_greater", diffB to "b_greater"))
            ret(result)
            finalizeFunction()
        }
    }

    @Test
    fun diamondCfgWithArithmeticOnArm64() {
        compileArm64 {
            val params = createFunction("abs_diff", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val cond = icmp(ICmpPredicate.SGT, params[0], params[1])
            condBr(cond, "a_greater", "b_greater")
            positionAtEnd(appendBlock("a_greater"))
            val diffA = sub(params[0], params[1])
            br("done")
            positionAtEnd(appendBlock("b_greater"))
            val diffB = sub(params[1], params[0])
            br("done")
            positionAtEnd(appendBlock("done"))
            val result = phi(Type.I32, listOf(diffA to "a_greater", diffB to "b_greater"))
            ret(result)
            finalizeFunction()
        }
    }

    @Test
    fun fpMixedOpsOnX86() {
        compileX86 {
            val params = createFunction("fp_expr", listOf(
                Param("a", Type.F64), Param("b", Type.F64), Param("c", Type.F64)
            ), Type.F64)
            positionAtEnd(appendBlock("entry"))
            val ab = fmul(params[0], params[1])
            val result = fadd(ab, params[2])
            ret(result)
            finalizeFunction()
        }
    }

    @Test
    fun fpMixedOpsOnArm64() {
        compileArm64 {
            val params = createFunction("fp_expr", listOf(
                Param("a", Type.F64), Param("b", Type.F64), Param("c", Type.F64)
            ), Type.F64)
            positionAtEnd(appendBlock("entry"))
            val ab = fmul(params[0], params[1])
            val result = fadd(ab, params[2])
            ret(result)
            finalizeFunction()
        }
    }
}
