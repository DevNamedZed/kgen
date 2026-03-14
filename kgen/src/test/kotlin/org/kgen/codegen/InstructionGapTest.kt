package org.kgen.codegen

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.kgen.ir.*
import org.kgen.ir.build.IrBuilder
import org.kgen.ir.target.Target
import org.kgen.target.x86.codegen.X86CodeGenerator
import org.kgen.target.arm64.codegen.Arm64CodeGenerator
import org.kgen.target.riscv.codegen.RiscVCodeGenerator
import org.kgen.target.wasm.codegen.WasmCodeGenerator
import org.kgen.target.jvm.codegen.JvmCodeGenerator
import org.kgen.target.clr.codegen.CilCodeGenerator

class InstructionGapTest {

    // --- Helpers ---

    private fun buildI64Module(target: Target, block: (IrBuilder, List<Value>) -> Unit): Module {
        val ir = IrBuilder("test", target)
        val params = ir.createFunction("test_func", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
        ir.appendBlock("entry")
        block(ir, params)
        ir.finalizeFunction()
        return ir.build()
    }

    private fun buildF64Module(target: Target, block: (IrBuilder, List<Value>) -> Unit): Module {
        val ir = IrBuilder("test", target)
        val params = ir.createFunction("test_func", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.F64)
        ir.appendBlock("entry")
        block(ir, params)
        ir.finalizeFunction()
        return ir.build()
    }

    private fun buildVoidModule(target: Target, block: (IrBuilder, List<Value>) -> Unit): Module {
        val ir = IrBuilder("test", target)
        val params = ir.createFunction("test_func", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.Void)
        ir.appendBlock("entry")
        block(ir, params)
        ir.finalizeFunction()
        return ir.build()
    }

    private fun buildPtrModule(target: Target, block: (IrBuilder, List<Value>) -> Unit): Module {
        val ir = IrBuilder("test", target)
        val params = ir.createFunction("test_func", listOf(Param("ptr", Type.OpaquePointer), Param("n", Type.I64)), Type.I64)
        ir.appendBlock("entry")
        block(ir, params)
        ir.finalizeFunction()
        return ir.build()
    }

    private fun buildPtrRetModule(target: Target, block: (IrBuilder, List<Value>) -> Unit): Module {
        val ir = IrBuilder("test", target)
        val params = ir.createFunction("test_func", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.OpaquePointer)
        ir.appendBlock("entry")
        block(ir, params)
        ir.finalizeFunction()
        return ir.build()
    }

    private fun compileX86(mod: Module) { X86CodeGenerator().generateObjectFile(mod) }
    private fun compileArm64(mod: Module) { Arm64CodeGenerator().generateObjectFile(mod) }
    private fun compileRiscV(mod: Module) { RiscVCodeGenerator().generateObjectFile(mod) }
    private fun compileWasm(mod: Module) { WasmCodeGenerator().generate(mod) }
    private fun compileJvm(mod: Module) { val g = JvmCodeGenerator(); g.className = "InstructionGapTest"; g.generate(mod) }
    private fun compileCil(mod: Module) { CilCodeGenerator().generate(mod) }

    // =========================================================================
    // 1. PtrToInt
    // =========================================================================

    @Test fun `ptrToInt compiles on x86`() = assertDoesNotThrow {
        compileX86(buildPtrModule(Target.x86_64()) { ir, p -> ir.ret(ir.ptrtoint(p[0], Type.I64)) })
    }

    @Test fun `ptrToInt compiles on arm64`() = assertDoesNotThrow {
        compileArm64(buildPtrModule(Target.arm64()) { ir, p -> ir.ret(ir.ptrtoint(p[0], Type.I64)) })
    }

    @Test fun `ptrToInt compiles on riscv`() = assertDoesNotThrow {
        compileRiscV(buildPtrModule(Target.riscv64()) { ir, p -> ir.ret(ir.ptrtoint(p[0], Type.I64)) })
    }

    @Test fun `ptrToInt compiles on wasm`() = assertDoesNotThrow {
        compileWasm(buildPtrModule(Target.wasm()) { ir, p -> ir.ret(ir.ptrtoint(p[0], Type.I64)) })
    }

    @Test fun `ptrToInt compiles on jvm`() = assertDoesNotThrow {
        compileJvm(buildPtrModule(Target.jvm()) { ir, p -> ir.ret(ir.ptrtoint(p[0], Type.I64)) })
    }

    @Test fun `ptrToInt compiles on cil`() = assertDoesNotThrow {
        compileCil(buildPtrModule(Target.msil()) { ir, p -> ir.ret(ir.ptrtoint(p[0], Type.I64)) })
    }

    // =========================================================================
    // 2. IntToPtr
    // =========================================================================

    @Test fun `intToPtr compiles on x86`() = assertDoesNotThrow {
        compileX86(buildPtrRetModule(Target.x86_64()) { ir, p -> ir.ret(ir.inttoptr(p[0], Type.OpaquePointer)) })
    }

    @Test fun `intToPtr compiles on arm64`() = assertDoesNotThrow {
        compileArm64(buildPtrRetModule(Target.arm64()) { ir, p -> ir.ret(ir.inttoptr(p[0], Type.OpaquePointer)) })
    }

    @Test fun `intToPtr compiles on riscv`() = assertDoesNotThrow {
        compileRiscV(buildPtrRetModule(Target.riscv64()) { ir, p -> ir.ret(ir.inttoptr(p[0], Type.OpaquePointer)) })
    }

    @Test fun `intToPtr compiles on wasm`() = assertDoesNotThrow {
        compileWasm(buildPtrRetModule(Target.wasm()) { ir, p -> ir.ret(ir.inttoptr(p[0], Type.OpaquePointer)) })
    }

    @Test fun `intToPtr compiles on jvm`() = assertDoesNotThrow {
        compileJvm(buildPtrRetModule(Target.jvm()) { ir, p -> ir.ret(ir.inttoptr(p[0], Type.OpaquePointer)) })
    }

    @Test fun `intToPtr compiles on cil`() = assertDoesNotThrow {
        compileCil(buildPtrRetModule(Target.msil()) { ir, p -> ir.ret(ir.inttoptr(p[0], Type.OpaquePointer)) })
    }

    // =========================================================================
    // 3. BitCast
    // =========================================================================

    @Test fun `bitCast compiles on x86`() = assertDoesNotThrow {
        compileX86(buildI64Module(Target.x86_64()) { ir, p -> ir.ret(ir.bitcast(p[0], Type.I64)) })
    }

    @Test fun `bitCast compiles on arm64`() = assertDoesNotThrow {
        compileArm64(buildI64Module(Target.arm64()) { ir, p -> ir.ret(ir.bitcast(p[0], Type.I64)) })
    }

    @Test fun `bitCast compiles on riscv`() = assertDoesNotThrow {
        compileRiscV(buildI64Module(Target.riscv64()) { ir, p -> ir.ret(ir.bitcast(p[0], Type.I64)) })
    }

    @Test fun `bitCast compiles on wasm`() = assertDoesNotThrow {
        compileWasm(buildI64Module(Target.wasm()) { ir, p -> ir.ret(ir.bitcast(p[0], Type.I64)) })
    }

    @Test fun `bitCast compiles on jvm`() = assertDoesNotThrow {
        compileJvm(buildI64Module(Target.jvm()) { ir, p -> ir.ret(ir.bitcast(p[0], Type.I64)) })
    }

    @Test fun `bitCast compiles on cil`() = assertDoesNotThrow {
        compileCil(buildI64Module(Target.msil()) { ir, p -> ir.ret(ir.bitcast(p[0], Type.I64)) })
    }

    // =========================================================================
    // 4. Unreachable
    // =========================================================================

    @Test fun `unreachable compiles on x86`() = assertDoesNotThrow {
        compileX86(buildI64Module(Target.x86_64()) { ir, p -> ir.ret(p[0]); ir.unreachable() })
    }

    @Test fun `unreachable compiles on arm64`() = assertDoesNotThrow {
        compileArm64(buildI64Module(Target.arm64()) { ir, p -> ir.ret(p[0]); ir.unreachable() })
    }

    @Test fun `unreachable compiles on riscv`() = assertDoesNotThrow {
        compileRiscV(buildI64Module(Target.riscv64()) { ir, p -> ir.ret(p[0]); ir.unreachable() })
    }

    @Test fun `unreachable compiles on wasm`() = assertDoesNotThrow {
        compileWasm(buildI64Module(Target.wasm()) { ir, p -> ir.ret(p[0]); ir.unreachable() })
    }

    @Test fun `unreachable compiles on jvm`() = assertDoesNotThrow {
        compileJvm(buildI64Module(Target.jvm()) { ir, p -> ir.ret(p[0]); ir.unreachable() })
    }

    @Test fun `unreachable compiles on cil`() = assertDoesNotThrow {
        compileCil(buildI64Module(Target.msil()) { ir, p -> ir.ret(p[0]); ir.unreachable() })
    }

    // =========================================================================
    // 5. Trap
    // =========================================================================

    @Test fun `trap compiles on x86`() = assertDoesNotThrow {
        compileX86(buildI64Module(Target.x86_64()) { ir, p -> ir.trap(); ir.ret(p[0]) })
    }

    @Test fun `trap compiles on arm64`() = assertDoesNotThrow {
        compileArm64(buildI64Module(Target.arm64()) { ir, p -> ir.trap(); ir.ret(p[0]) })
    }

    @Test fun `trap compiles on riscv`() = assertDoesNotThrow {
        compileRiscV(buildI64Module(Target.riscv64()) { ir, p -> ir.trap(); ir.ret(p[0]) })
    }

    @Test fun `trap compiles on wasm`() = assertDoesNotThrow {
        compileWasm(buildI64Module(Target.wasm()) { ir, p -> ir.trap(); ir.ret(p[0]) })
    }

    @Test fun `trap compiles on jvm`() = assertDoesNotThrow {
        compileJvm(buildI64Module(Target.jvm()) { ir, p -> ir.trap(); ir.ret(p[0]) })
    }

    @Test fun `trap compiles on cil`() = assertDoesNotThrow {
        compileCil(buildI64Module(Target.msil()) { ir, p -> ir.trap(); ir.ret(p[0]) })
    }

    // =========================================================================
    // 6. DebugTrap
    // =========================================================================

    @Test fun `debugTrap compiles on x86`() = assertDoesNotThrow {
        compileX86(buildI64Module(Target.x86_64()) { ir, p -> ir.debugTrap(); ir.ret(p[0]) })
    }

    @Test fun `debugTrap compiles on arm64`() = assertDoesNotThrow {
        compileArm64(buildI64Module(Target.arm64()) { ir, p -> ir.debugTrap(); ir.ret(p[0]) })
    }

    @Test fun `debugTrap compiles on riscv`() = assertDoesNotThrow {
        compileRiscV(buildI64Module(Target.riscv64()) { ir, p -> ir.debugTrap(); ir.ret(p[0]) })
    }

    @Test fun `debugTrap compiles on wasm`() = assertDoesNotThrow {
        compileWasm(buildI64Module(Target.wasm()) { ir, p -> ir.debugTrap(); ir.ret(p[0]) })
    }

    @Test fun `debugTrap compiles on jvm`() = assertDoesNotThrow {
        compileJvm(buildI64Module(Target.jvm()) { ir, p -> ir.debugTrap(); ir.ret(p[0]) })
    }

    @Test fun `debugTrap compiles on cil`() = assertDoesNotThrow {
        compileCil(buildI64Module(Target.msil()) { ir, p -> ir.debugTrap(); ir.ret(p[0]) })
    }

    // =========================================================================
    // 7. Ctlz — x86, ARM64, JVM, WASM, CIL (RISC-V doesn't support)
    // =========================================================================

    @Test fun `ctlz compiles on x86`() = assertDoesNotThrow {
        compileX86(buildI64Module(Target.x86_64()) { ir, p -> ir.ret(ir.ctlz(p[0])) })
    }

    @Test fun `ctlz compiles on arm64`() = assertDoesNotThrow {
        compileArm64(buildI64Module(Target.arm64()) { ir, p -> ir.ret(ir.ctlz(p[0])) })
    }

    @Test fun `ctlz compiles on jvm`() = assertDoesNotThrow {
        compileJvm(buildI64Module(Target.jvm()) { ir, p -> ir.ret(ir.ctlz(p[0])) })
    }

    @Test fun `ctlz compiles on wasm`() = assertDoesNotThrow {
        compileWasm(buildI64Module(Target.wasm()) { ir, p -> ir.ret(ir.ctlz(p[0])) })
    }

    @Test fun `ctlz compiles on riscv`() = assertDoesNotThrow {
        compileRiscV(buildI64Module(Target.riscv64()) { ir, p -> ir.ret(ir.ctlz(p[0])) })
    }

    @Test fun `ctlz compiles on cil`() = assertDoesNotThrow {
        compileCil(buildI64Module(Target.msil()) { ir, p -> ir.ret(ir.ctlz(p[0])) })
    }

    // =========================================================================
    // 8. Cttz — x86, ARM64, JVM, WASM, CIL
    // =========================================================================

    @Test fun `cttz compiles on x86`() = assertDoesNotThrow {
        compileX86(buildI64Module(Target.x86_64()) { ir, p -> ir.ret(ir.cttz(p[0])) })
    }

    @Test fun `cttz compiles on arm64`() = assertDoesNotThrow {
        compileArm64(buildI64Module(Target.arm64()) { ir, p -> ir.ret(ir.cttz(p[0])) })
    }

    @Test fun `cttz compiles on jvm`() = assertDoesNotThrow {
        compileJvm(buildI64Module(Target.jvm()) { ir, p -> ir.ret(ir.cttz(p[0])) })
    }

    @Test fun `cttz compiles on wasm`() = assertDoesNotThrow {
        compileWasm(buildI64Module(Target.wasm()) { ir, p -> ir.ret(ir.cttz(p[0])) })
    }

    @Test fun `cttz compiles on riscv`() = assertDoesNotThrow {
        compileRiscV(buildI64Module(Target.riscv64()) { ir, p -> ir.ret(ir.cttz(p[0])) })
    }

    @Test fun `cttz compiles on cil`() = assertDoesNotThrow {
        compileCil(buildI64Module(Target.msil()) { ir, p -> ir.ret(ir.cttz(p[0])) })
    }

    // =========================================================================
    // 9. Ctpop — x86, JVM, WASM, CIL (ARM64 throws, RISC-V doesn't support)
    // =========================================================================

    @Test fun `ctpop compiles on x86`() = assertDoesNotThrow {
        compileX86(buildI64Module(Target.x86_64()) { ir, p -> ir.ret(ir.ctpop(p[0])) })
    }

    @Test fun `ctpop compiles on jvm`() = assertDoesNotThrow {
        compileJvm(buildI64Module(Target.jvm()) { ir, p -> ir.ret(ir.ctpop(p[0])) })
    }

    @Test fun `ctpop compiles on wasm`() = assertDoesNotThrow {
        compileWasm(buildI64Module(Target.wasm()) { ir, p -> ir.ret(ir.ctpop(p[0])) })
    }

    @Test fun `ctpop compiles on arm64`() = assertDoesNotThrow {
        compileArm64(buildI64Module(Target.arm64()) { ir, p -> ir.ret(ir.ctpop(p[0])) })
    }

    @Test fun `ctpop compiles on riscv`() = assertDoesNotThrow {
        compileRiscV(buildI64Module(Target.riscv64()) { ir, p -> ir.ret(ir.ctpop(p[0])) })
    }

    @Test fun `ctpop compiles on cil`() = assertDoesNotThrow {
        compileCil(buildI64Module(Target.msil()) { ir, p -> ir.ret(ir.ctpop(p[0])) })
    }

    // =========================================================================
    // 10. BSwap — x86, ARM64, JVM, CIL (RISC-V/WASM don't support)
    // =========================================================================

    @Test fun `bswap compiles on x86`() = assertDoesNotThrow {
        compileX86(buildI64Module(Target.x86_64()) { ir, p -> ir.ret(ir.bswap(p[0])) })
    }

    @Test fun `bswap compiles on arm64`() = assertDoesNotThrow {
        compileArm64(buildI64Module(Target.arm64()) { ir, p -> ir.ret(ir.bswap(p[0])) })
    }

    @Test fun `bswap compiles on jvm`() = assertDoesNotThrow {
        compileJvm(buildI64Module(Target.jvm()) { ir, p -> ir.ret(ir.bswap(p[0])) })
    }

    @Test fun `bswap compiles on riscv`() = assertDoesNotThrow {
        compileRiscV(buildI64Module(Target.riscv64()) { ir, p -> ir.ret(ir.bswap(p[0])) })
    }

    @Test fun `bswap compiles on cil`() = assertDoesNotThrow {
        compileCil(buildI64Module(Target.msil()) { ir, p -> ir.ret(ir.bswap(p[0])) })
    }

    @Test fun `bswap errors on wasm`() {
        assertThrows<Exception> {
            compileWasm(buildI64Module(Target.wasm()) { ir, p -> ir.ret(ir.bswap(p[0])) })
        }
    }

    // =========================================================================
    // 11. Sqrt — all backends
    // =========================================================================

    @Test fun `sqrt compiles on x86`() = assertDoesNotThrow {
        compileX86(buildF64Module(Target.x86_64()) { ir, p -> ir.ret(ir.sqrt(p[0])) })
    }

    @Test fun `sqrt compiles on arm64`() = assertDoesNotThrow {
        compileArm64(buildF64Module(Target.arm64()) { ir, p -> ir.ret(ir.sqrt(p[0])) })
    }

    @Test fun `sqrt compiles on riscv`() = assertDoesNotThrow {
        compileRiscV(buildF64Module(Target.riscv64()) { ir, p -> ir.ret(ir.sqrt(p[0])) })
    }

    @Test fun `sqrt compiles on jvm`() = assertDoesNotThrow {
        compileJvm(buildF64Module(Target.jvm()) { ir, p -> ir.ret(ir.sqrt(p[0])) })
    }

    @Test fun `sqrt compiles on wasm`() = assertDoesNotThrow {
        compileWasm(buildF64Module(Target.wasm()) { ir, p -> ir.ret(ir.sqrt(p[0])) })
    }

    @Test fun `sqrt compiles on cil`() = assertDoesNotThrow {
        compileCil(buildF64Module(Target.msil()) { ir, p -> ir.ret(ir.sqrt(p[0])) })
    }

    // =========================================================================
    // 12. Ceil — x86, ARM64, JVM, WASM, CIL
    // =========================================================================

    @Test fun `ceil compiles on x86`() = assertDoesNotThrow {
        compileX86(buildF64Module(Target.x86_64()) { ir, p -> ir.ret(ir.ceil(p[0])) })
    }

    @Test fun `ceil compiles on arm64`() = assertDoesNotThrow {
        compileArm64(buildF64Module(Target.arm64()) { ir, p -> ir.ret(ir.ceil(p[0])) })
    }

    @Test fun `ceil compiles on jvm`() = assertDoesNotThrow {
        compileJvm(buildF64Module(Target.jvm()) { ir, p -> ir.ret(ir.ceil(p[0])) })
    }

    @Test fun `ceil compiles on wasm`() = assertDoesNotThrow {
        compileWasm(buildF64Module(Target.wasm()) { ir, p -> ir.ret(ir.ceil(p[0])) })
    }

    @Test fun `ceil compiles on cil`() = assertDoesNotThrow {
        compileCil(buildF64Module(Target.msil()) { ir, p -> ir.ret(ir.ceil(p[0])) })
    }

    // =========================================================================
    // 13. Floor — x86, ARM64, JVM, WASM, CIL
    // =========================================================================

    @Test fun `floor compiles on x86`() = assertDoesNotThrow {
        compileX86(buildF64Module(Target.x86_64()) { ir, p -> ir.ret(ir.floor(p[0])) })
    }

    @Test fun `floor compiles on arm64`() = assertDoesNotThrow {
        compileArm64(buildF64Module(Target.arm64()) { ir, p -> ir.ret(ir.floor(p[0])) })
    }

    @Test fun `floor compiles on jvm`() = assertDoesNotThrow {
        compileJvm(buildF64Module(Target.jvm()) { ir, p -> ir.ret(ir.floor(p[0])) })
    }

    @Test fun `floor compiles on wasm`() = assertDoesNotThrow {
        compileWasm(buildF64Module(Target.wasm()) { ir, p -> ir.ret(ir.floor(p[0])) })
    }

    @Test fun `floor compiles on cil`() = assertDoesNotThrow {
        compileCil(buildF64Module(Target.msil()) { ir, p -> ir.ret(ir.floor(p[0])) })
    }

    // =========================================================================
    // 14. Round — x86, ARM64, JVM, WASM, CIL
    // =========================================================================

    @Test fun `round compiles on x86`() = assertDoesNotThrow {
        compileX86(buildF64Module(Target.x86_64()) { ir, p -> ir.ret(ir.round(p[0])) })
    }

    @Test fun `round compiles on arm64`() = assertDoesNotThrow {
        compileArm64(buildF64Module(Target.arm64()) { ir, p -> ir.ret(ir.round(p[0])) })
    }

    @Test fun `round compiles on jvm`() = assertDoesNotThrow {
        compileJvm(buildF64Module(Target.jvm()) { ir, p -> ir.ret(ir.round(p[0])) })
    }

    @Test fun `round compiles on wasm`() = assertDoesNotThrow {
        compileWasm(buildF64Module(Target.wasm()) { ir, p -> ir.ret(ir.round(p[0])) })
    }

    @Test fun `round compiles on cil`() = assertDoesNotThrow {
        compileCil(buildF64Module(Target.msil()) { ir, p -> ir.ret(ir.round(p[0])) })
    }

    // =========================================================================
    // 15. FAbs — x86, ARM64, RISC-V, WASM, JVM, CIL
    // =========================================================================

    @Test fun `fabs compiles on x86`() = assertDoesNotThrow {
        compileX86(buildF64Module(Target.x86_64()) { ir, p -> ir.ret(ir.fabs(p[0])) })
    }

    @Test fun `fabs compiles on arm64`() = assertDoesNotThrow {
        compileArm64(buildF64Module(Target.arm64()) { ir, p -> ir.ret(ir.fabs(p[0])) })
    }

    @Test fun `fabs compiles on riscv`() = assertDoesNotThrow {
        compileRiscV(buildF64Module(Target.riscv64()) { ir, p -> ir.ret(ir.fabs(p[0])) })
    }

    @Test fun `fabs compiles on wasm`() = assertDoesNotThrow {
        compileWasm(buildF64Module(Target.wasm()) { ir, p -> ir.ret(ir.fabs(p[0])) })
    }

    @Test fun `fabs compiles on jvm`() = assertDoesNotThrow {
        compileJvm(buildF64Module(Target.jvm()) { ir, p -> ir.ret(ir.fabs(p[0])) })
    }

    @Test fun `fabs compiles on cil`() = assertDoesNotThrow {
        compileCil(buildF64Module(Target.msil()) { ir, p -> ir.ret(ir.fabs(p[0])) })
    }

    // =========================================================================
    // 16. FMin — x86, ARM64, RISC-V, WASM, JVM, CIL
    // =========================================================================

    @Test fun `fmin compiles on x86`() = assertDoesNotThrow {
        compileX86(buildF64Module(Target.x86_64()) { ir, p -> ir.ret(ir.fmin(p[0], p[1])) })
    }

    @Test fun `fmin compiles on arm64`() = assertDoesNotThrow {
        compileArm64(buildF64Module(Target.arm64()) { ir, p -> ir.ret(ir.fmin(p[0], p[1])) })
    }

    @Test fun `fmin compiles on riscv`() = assertDoesNotThrow {
        compileRiscV(buildF64Module(Target.riscv64()) { ir, p -> ir.ret(ir.fmin(p[0], p[1])) })
    }

    @Test fun `fmin compiles on wasm`() = assertDoesNotThrow {
        compileWasm(buildF64Module(Target.wasm()) { ir, p -> ir.ret(ir.fmin(p[0], p[1])) })
    }

    @Test fun `fmin compiles on jvm`() = assertDoesNotThrow {
        compileJvm(buildF64Module(Target.jvm()) { ir, p -> ir.ret(ir.fmin(p[0], p[1])) })
    }

    @Test fun `fmin compiles on cil`() = assertDoesNotThrow {
        compileCil(buildF64Module(Target.msil()) { ir, p -> ir.ret(ir.fmin(p[0], p[1])) })
    }

    // =========================================================================
    // 17. FMax — x86, ARM64, RISC-V, WASM, JVM, CIL
    // =========================================================================

    @Test fun `fmax compiles on x86`() = assertDoesNotThrow {
        compileX86(buildF64Module(Target.x86_64()) { ir, p -> ir.ret(ir.fmax(p[0], p[1])) })
    }

    @Test fun `fmax compiles on arm64`() = assertDoesNotThrow {
        compileArm64(buildF64Module(Target.arm64()) { ir, p -> ir.ret(ir.fmax(p[0], p[1])) })
    }

    @Test fun `fmax compiles on riscv`() = assertDoesNotThrow {
        compileRiscV(buildF64Module(Target.riscv64()) { ir, p -> ir.ret(ir.fmax(p[0], p[1])) })
    }

    @Test fun `fmax compiles on wasm`() = assertDoesNotThrow {
        compileWasm(buildF64Module(Target.wasm()) { ir, p -> ir.ret(ir.fmax(p[0], p[1])) })
    }

    @Test fun `fmax compiles on jvm`() = assertDoesNotThrow {
        compileJvm(buildF64Module(Target.jvm()) { ir, p -> ir.ret(ir.fmax(p[0], p[1])) })
    }

    @Test fun `fmax compiles on cil`() = assertDoesNotThrow {
        compileCil(buildF64Module(Target.msil()) { ir, p -> ir.ret(ir.fmax(p[0], p[1])) })
    }

    // =========================================================================
    // 18. CopySign — x86, WASM
    // =========================================================================

    @Test fun `copySign compiles on x86`() = assertDoesNotThrow {
        compileX86(buildF64Module(Target.x86_64()) { ir, p -> ir.ret(ir.copySign(p[0], p[1])) })
    }

    @Test fun `copySign compiles on wasm`() = assertDoesNotThrow {
        compileWasm(buildF64Module(Target.wasm()) { ir, p -> ir.ret(ir.copySign(p[0], p[1])) })
    }

    // =========================================================================
    // 19. Fence — x86, ARM64, RISC-V
    // =========================================================================

    @Test fun `fence compiles on x86`() = assertDoesNotThrow {
        compileX86(buildVoidModule(Target.x86_64()) { ir, _ ->
            ir.fence(AtomicOrdering.SEQ_CST)
            ir.ret()
        })
    }

    @Test fun `fence compiles on arm64`() = assertDoesNotThrow {
        compileArm64(buildVoidModule(Target.arm64()) { ir, _ ->
            ir.fence(AtomicOrdering.SEQ_CST)
            ir.ret()
        })
    }

    @Test fun `fence compiles on riscv`() = assertDoesNotThrow {
        compileRiscV(buildVoidModule(Target.riscv64()) { ir, _ ->
            ir.fence(AtomicOrdering.SEQ_CST)
            ir.ret()
        })
    }

    // =========================================================================
    // 20. GCSafepoint — all backends (no-op)
    // =========================================================================

    @Test fun `gcSafepoint compiles on x86`() = assertDoesNotThrow {
        compileX86(buildI64Module(Target.x86_64()) { ir, p -> ir.gcSafepoint(); ir.ret(p[0]) })
    }

    @Test fun `gcSafepoint compiles on arm64`() = assertDoesNotThrow {
        compileArm64(buildI64Module(Target.arm64()) { ir, p -> ir.gcSafepoint(); ir.ret(p[0]) })
    }

    @Test fun `gcSafepoint compiles on riscv`() = assertDoesNotThrow {
        compileRiscV(buildI64Module(Target.riscv64()) { ir, p -> ir.gcSafepoint(); ir.ret(p[0]) })
    }

    @Test fun `gcSafepoint compiles on wasm`() = assertDoesNotThrow {
        compileWasm(buildI64Module(Target.wasm()) { ir, p -> ir.gcSafepoint(); ir.ret(p[0]) })
    }

    @Test fun `gcSafepoint compiles on jvm`() = assertDoesNotThrow {
        compileJvm(buildI64Module(Target.jvm()) { ir, p -> ir.gcSafepoint(); ir.ret(p[0]) })
    }

    @Test fun `gcSafepoint compiles on cil`() = assertDoesNotThrow {
        compileCil(buildI64Module(Target.msil()) { ir, p -> ir.gcSafepoint(); ir.ret(p[0]) })
    }

    // =========================================================================
    // 21. GCRoot — all backends (no-op)
    // =========================================================================

    @Test fun `gcRoot compiles on x86`() = assertDoesNotThrow {
        compileX86(buildI64Module(Target.x86_64()) { ir, p -> ir.gcRoot(p[0]); ir.ret(p[0]) })
    }

    @Test fun `gcRoot compiles on arm64`() = assertDoesNotThrow {
        compileArm64(buildI64Module(Target.arm64()) { ir, p -> ir.gcRoot(p[0]); ir.ret(p[0]) })
    }

    @Test fun `gcRoot compiles on riscv`() = assertDoesNotThrow {
        compileRiscV(buildI64Module(Target.riscv64()) { ir, p -> ir.gcRoot(p[0]); ir.ret(p[0]) })
    }

    @Test fun `gcRoot compiles on wasm`() = assertDoesNotThrow {
        compileWasm(buildI64Module(Target.wasm()) { ir, p -> ir.gcRoot(p[0]); ir.ret(p[0]) })
    }

    @Test fun `gcRoot compiles on jvm`() = assertDoesNotThrow {
        compileJvm(buildI64Module(Target.jvm()) { ir, p -> ir.gcRoot(p[0]); ir.ret(p[0]) })
    }

    @Test fun `gcRoot compiles on cil`() = assertDoesNotThrow {
        compileCil(buildI64Module(Target.msil()) { ir, p -> ir.gcRoot(p[0]); ir.ret(p[0]) })
    }

    // =========================================================================
    // 22. MemCpy — x86, WASM, JVM (no-op), CIL (cpblk)
    // =========================================================================

    @Test fun `memCpy compiles on x86`() = assertDoesNotThrow {
        compileX86(buildVoidModule(Target.x86_64()) { ir, p ->
            val dst = ir.inttoptr(p[0], Type.OpaquePointer)
            val src = ir.inttoptr(p[1], Type.OpaquePointer)
            ir.memcpy(dst, src, Constant.I64(16))
            ir.ret()
        })
    }

    @Test fun `memCpy compiles on wasm`() = assertDoesNotThrow {
        compileWasm(buildVoidModule(Target.wasm()) { ir, p ->
            val dst = ir.inttoptr(p[0], Type.OpaquePointer)
            val src = ir.inttoptr(p[1], Type.OpaquePointer)
            ir.memcpy(dst, src, Constant.I64(16))
            ir.ret()
        })
    }

    @Test fun `memCpy compiles on jvm`() = assertDoesNotThrow {
        compileJvm(buildVoidModule(Target.jvm()) { ir, p ->
            val dst = ir.inttoptr(p[0], Type.OpaquePointer)
            val src = ir.inttoptr(p[1], Type.OpaquePointer)
            ir.memcpy(dst, src, Constant.I64(16))
            ir.ret()
        })
    }

    @Test fun `memCpy compiles on cil`() = assertDoesNotThrow {
        compileCil(buildVoidModule(Target.msil()) { ir, p ->
            val dst = ir.inttoptr(p[0], Type.OpaquePointer)
            val src = ir.inttoptr(p[1], Type.OpaquePointer)
            ir.memcpy(dst, src, Constant.I64(16))
            ir.ret()
        })
    }

    // =========================================================================
    // 23. MemSet — x86, WASM, JVM (no-op), CIL (initblk)
    // =========================================================================

    @Test fun `memSet compiles on x86`() = assertDoesNotThrow {
        compileX86(buildVoidModule(Target.x86_64()) { ir, p ->
            val dst = ir.inttoptr(p[0], Type.OpaquePointer)
            ir.memset(dst, Constant.I8(0), Constant.I64(32))
            ir.ret()
        })
    }

    @Test fun `memSet compiles on wasm`() = assertDoesNotThrow {
        compileWasm(buildVoidModule(Target.wasm()) { ir, p ->
            val dst = ir.inttoptr(p[0], Type.OpaquePointer)
            ir.memset(dst, Constant.I8(0), Constant.I64(32))
            ir.ret()
        })
    }

    @Test fun `memSet compiles on jvm`() = assertDoesNotThrow {
        compileJvm(buildVoidModule(Target.jvm()) { ir, p ->
            val dst = ir.inttoptr(p[0], Type.OpaquePointer)
            ir.memset(dst, Constant.I8(0), Constant.I64(32))
            ir.ret()
        })
    }

    @Test fun `memSet compiles on cil`() = assertDoesNotThrow {
        compileCil(buildVoidModule(Target.msil()) { ir, p ->
            val dst = ir.inttoptr(p[0], Type.OpaquePointer)
            ir.memset(dst, Constant.I8(0), Constant.I64(32))
            ir.ret()
        })
    }

    // =========================================================================
    // 24. SMin/SMax — x86, JVM, CIL
    // =========================================================================

    @Test fun `smin compiles on x86`() = assertDoesNotThrow {
        compileX86(buildI64Module(Target.x86_64()) { ir, p -> ir.ret(ir.smin(p[0], p[1])) })
    }

    @Test fun `smin compiles on jvm`() = assertDoesNotThrow {
        compileJvm(buildI64Module(Target.jvm()) { ir, p -> ir.ret(ir.smin(p[0], p[1])) })
    }

    @Test fun `smin compiles on cil`() = assertDoesNotThrow {
        compileCil(buildI64Module(Target.msil()) { ir, p -> ir.ret(ir.smin(p[0], p[1])) })
    }

    @Test fun `smax compiles on x86`() = assertDoesNotThrow {
        compileX86(buildI64Module(Target.x86_64()) { ir, p -> ir.ret(ir.smax(p[0], p[1])) })
    }

    @Test fun `smax compiles on jvm`() = assertDoesNotThrow {
        compileJvm(buildI64Module(Target.jvm()) { ir, p -> ir.ret(ir.smax(p[0], p[1])) })
    }

    @Test fun `smax compiles on cil`() = assertDoesNotThrow {
        compileCil(buildI64Module(Target.msil()) { ir, p -> ir.ret(ir.smax(p[0], p[1])) })
    }

    // =========================================================================
    // 25. UMin/UMax — x86
    // =========================================================================

    @Test fun `umin compiles on x86`() = assertDoesNotThrow {
        compileX86(buildI64Module(Target.x86_64()) { ir, p -> ir.ret(ir.umin(p[0], p[1])) })
    }

    @Test fun `umax compiles on x86`() = assertDoesNotThrow {
        compileX86(buildI64Module(Target.x86_64()) { ir, p -> ir.ret(ir.umax(p[0], p[1])) })
    }

    // =========================================================================
    // 26. CopySign — x86, WASM, JVM, CIL
    // =========================================================================

    @Test fun `copySign compiles on jvm`() = assertDoesNotThrow {
        compileJvm(buildF64Module(Target.jvm()) { ir, p -> ir.ret(ir.copySign(p[0], p[1])) })
    }

    @Test fun `copySign compiles on cil`() = assertDoesNotThrow {
        compileCil(buildF64Module(Target.msil()) { ir, p -> ir.ret(ir.copySign(p[0], p[1])) })
    }

    // =========================================================================
    // 27. Fence on JVM/CIL (no-ops)
    // =========================================================================

    @Test fun `fence compiles on jvm`() = assertDoesNotThrow {
        compileJvm(buildVoidModule(Target.jvm()) { ir, _ ->
            ir.fence(AtomicOrdering.SEQ_CST)
            ir.ret()
        })
    }

    @Test fun `fence compiles on cil`() = assertDoesNotThrow {
        compileCil(buildVoidModule(Target.msil()) { ir, _ ->
            ir.fence(AtomicOrdering.SEQ_CST)
            ir.ret()
        })
    }

    private fun buildF64TernaryModule(target: Target, block: (IrBuilder, List<Value>) -> Unit): Module {
        val ir = IrBuilder("test", target)
        val params = ir.createFunction("test_func",
            listOf(Param("a", Type.F64), Param("b", Type.F64), Param("c", Type.F64)), Type.F64)
        ir.appendBlock("entry")
        block(ir, params)
        ir.finalizeFunction()
        return ir.build()
    }

    @Test fun `fma compiles on x86 with vfmadd`() = assertDoesNotThrow {
        compileX86(buildF64TernaryModule(Target.x86_64()) { ir, p -> ir.ret(ir.fma(p[0], p[1], p[2])) })
    }

    @Test fun `fma compiles on arm64`() = assertDoesNotThrow {
        compileArm64(buildF64TernaryModule(Target.arm64()) { ir, p -> ir.ret(ir.fma(p[0], p[1], p[2])) })
    }

    @Test fun `fma compiles on riscv`() = assertDoesNotThrow {
        compileRiscV(buildF64TernaryModule(Target.riscv64()) { ir, p -> ir.ret(ir.fma(p[0], p[1], p[2])) })
    }

    @Test fun `fma compiles on wasm`() = assertDoesNotThrow {
        compileWasm(buildF64TernaryModule(Target.wasm()) { ir, p -> ir.ret(ir.fma(p[0], p[1], p[2])) })
    }

    @Test fun `fma compiles on jvm`() = assertDoesNotThrow {
        compileJvm(buildF64TernaryModule(Target.jvm()) { ir, p -> ir.ret(ir.fma(p[0], p[1], p[2])) })
    }

    @Test fun `fma compiles on cil`() = assertDoesNotThrow {
        compileCil(buildF64TernaryModule(Target.msil()) { ir, p -> ir.ret(ir.fma(p[0], p[1], p[2])) })
    }
}
