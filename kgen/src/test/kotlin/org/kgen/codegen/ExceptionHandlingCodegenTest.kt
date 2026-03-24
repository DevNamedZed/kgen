package org.kgen.codegen

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*
import org.kgen.ir.build.*
import org.kgen.ir.target.Target
import org.kgen.target.arm64.codegen.Arm64CodeGenerator
import org.kgen.target.riscv.codegen.RiscVCodeGenerator
import org.kgen.target.x86.codegen.X86CodeGenerator

class ExceptionHandlingCodegenTest {

    private fun buildInvokeModule(): Module {
        val ir = ModuleBuilder("test", Target.x86_64())

        // Declare an external function that might throw
        ir.declareFunction("may_throw", emptyList(), Type.Void)

        // Function with invoke + landing pad
        ir.createFunction(
            "tryCatch",
            listOf(Param("x", Type.I64)),
            Type.I64,
        )
        ir.appendBlock("entry")
        ir.invoke(
            FunctionRef("may_throw", Type.Function(emptyList(), Type.Void)),
            emptyList(), Type.Void,
            normalDest = BlockRef("normal"),
            unwindDest = BlockRef("catch"),
        )

        ir.appendBlock("normal")
        ir.ret(Constant.I64(0))

        ir.appendBlock("catch")
        ir.landingPad(
            Type.OpaquePointer,
            listOf(LandingPadClause.Catch(GlobalRef("java/lang/Exception", Type.OpaquePointer))),
        )
        ir.ret(Constant.I64(1))

        ir.finalizeFunction()
        return ir.build()
    }

    private fun buildCleanupModule(): Module {
        val ir = ModuleBuilder("test", Target.x86_64())
        ir.declareFunction("may_throw", emptyList(), Type.Void)

        ir.createFunction("cleanup", emptyList(), Type.Void)
        ir.appendBlock("entry")
        ir.invoke(
            FunctionRef("may_throw", Type.Function(emptyList(), Type.Void)),
            emptyList(), Type.Void,
            normalDest = BlockRef("normal"),
            unwindDest = BlockRef("cleanup"),
        )

        ir.appendBlock("normal")
        ir.ret()

        ir.appendBlock("cleanup")
        val lp = ir.landingPad(Type.OpaquePointer, emptyList(), cleanup = true)
        ir.resume(lp)

        ir.finalizeFunction()
        return ir.build()
    }

    private fun buildMultiInvokeModule(): Module {
        val ir = ModuleBuilder("test", Target.x86_64())
        ir.declareFunction("foo", emptyList(), Type.Void)
        ir.declareFunction("bar", emptyList(), Type.Void)

        ir.createFunction("multiInvoke", emptyList(), Type.I64)
        ir.appendBlock("entry")
        ir.invoke(
            FunctionRef("foo", Type.Function(emptyList(), Type.Void)),
            emptyList(), Type.Void,
            normalDest = BlockRef("after_foo"),
            unwindDest = BlockRef("catch"),
        )

        ir.appendBlock("after_foo")
        ir.invoke(
            FunctionRef("bar", Type.Function(emptyList(), Type.Void)),
            emptyList(), Type.Void,
            normalDest = BlockRef("done"),
            unwindDest = BlockRef("catch"),
        )

        ir.appendBlock("done")
        ir.ret(Constant.I64(0))

        ir.appendBlock("catch")
        ir.landingPad(
            Type.OpaquePointer,
            listOf(LandingPadClause.Catch(GlobalRef("Exception", Type.OpaquePointer))),
        )
        ir.ret(Constant.I64(1))

        ir.finalizeFunction()
        return ir.build()
    }

    @Test
    fun `invoke generates call with relocation`() {
        val code = X86CodeGenerator().generateCode(buildInvokeModule())
        val callRelocs = code.relocations.filter { it.symbol == "may_throw" }
        assertEquals(1, callRelocs.size, "Should emit call to may_throw")
    }

    @Test
    fun `invoke produces non-empty text bytes`() {
        val code = X86CodeGenerator().generateCode(buildInvokeModule())
        assertTrue(code.textBytes.isNotEmpty())
    }

    @Test
    fun `invoke generates gcc_except_table`() {
        val code = X86CodeGenerator().generateCode(buildInvokeModule())
        assertTrue(code.exceptTableBytes.isNotEmpty(),
            "Should produce .gcc_except_table for functions with invoke")
    }

    @Test
    fun `no invoke means no except table`() {
        val ir = ModuleBuilder("test", Target.x86_64())
        val params = ir.createFunction("simple", listOf(Param("a", Type.I64)), Type.I64)
        ir.appendBlock("entry")
        ir.ret(params[0])
        ir.finalizeFunction()
        val code = X86CodeGenerator().generateCode(ir.build())
        assertTrue(code.exceptTableBytes.isEmpty(),
            "Should not produce except table without invoke instructions")
    }

    @Test
    fun `cleanup landing pad with resume generates Unwind_Resume call`() {
        val code = X86CodeGenerator().generateCode(buildCleanupModule())
        val resumeRelocs = code.relocations.filter { it.symbol == "_Unwind_Resume" }
        assertEquals(1, resumeRelocs.size, "Should emit call to _Unwind_Resume")
    }

    @Test
    fun `multiple invokes produce multiple call sites`() {
        val code = X86CodeGenerator().generateCode(buildMultiInvokeModule())
        assertTrue(code.relocations.any { it.symbol == "foo" })
        assertTrue(code.relocations.any { it.symbol == "bar" })
        assertTrue(code.exceptTableBytes.isNotEmpty())
    }

    @Test
    fun `toObjectFile includes gcc_except_table section`() {
        val code = X86CodeGenerator().generateCode(buildInvokeModule())
        val obj = code.toObjectFile(
            org.kgen.binary.ObjectFormat.ELF,
            org.kgen.binary.Architecture(org.kgen.binary.ArchType.X86_64)
        )
        val exceptSection = obj.sections.find { it.name == ".gcc_except_table" }
        assertNotNull(exceptSection, "ObjectFile should contain .gcc_except_table section")
        assertTrue(exceptSection!!.data.isNotEmpty())
    }

    @Test
    fun `eh_frame still generated with exception handling`() {
        val code = X86CodeGenerator().generateCode(buildInvokeModule())
        assertTrue(code.ehFrameBytes.isNotEmpty())
    }

    @Test
    fun `invoke with return value produces valid code`() {
        val ir = ModuleBuilder("test", Target.x86_64())
        ir.declareFunction("get_value", emptyList(), Type.I64)

        ir.createFunction("invokeWithReturn", emptyList(), Type.I64)
        ir.appendBlock("entry")
        val result = ir.invoke(
            FunctionRef("get_value", Type.Function(emptyList(), Type.I64)),
            emptyList(), Type.I64,
            normalDest = BlockRef("normal"),
            unwindDest = BlockRef("catch"),
        )

        ir.appendBlock("normal")
        ir.ret(result!!)

        ir.appendBlock("catch")
        ir.landingPad(Type.OpaquePointer, emptyList(), cleanup = true)
        ir.ret(Constant.I64(-1))

        ir.finalizeFunction()
        val code = X86CodeGenerator().generateCode(ir.build())
        assertTrue(code.textBytes.isNotEmpty())
    }

    @Test
    fun `invoke with arguments emits proper code`() {
        val ir = ModuleBuilder("test", Target.x86_64())
        ir.declareFunction("process", listOf(Param("a", Type.I64), Param("b", Type.I32)), Type.I32)

        val params = ir.createFunction("invokeWithArgs", listOf(Param("a", Type.I64)), Type.I32)
        ir.appendBlock("entry")
        val result = ir.invoke(
            FunctionRef("process", Type.Function(listOf(Type.I64, Type.I32), Type.I32)),
            listOf(params[0], Constant.I32(42)), Type.I32,
            normalDest = BlockRef("normal"),
            unwindDest = BlockRef("catch"),
        )

        ir.appendBlock("normal")
        ir.ret(result!!)

        ir.appendBlock("catch")
        ir.landingPad(Type.OpaquePointer, emptyList(), cleanup = true)
        ir.ret(Constant.I32(-1))

        ir.finalizeFunction()
        val code = X86CodeGenerator().generateCode(ir.build())
        assertTrue(code.relocations.any { it.symbol == "process" })
    }

    // ── Throw instruction codegen ────────────────────────────────────

    private fun buildThrowModule(target: Target): Module {
        val ir = ModuleBuilder("test", target)
        ir.declareFunction("kgen_throw", listOf(Param("exception", Type.OpaquePointer)), Type.Void)

        val params = ir.createFunction("throwIt", listOf(Param("exn", Type.OpaquePointer)), Type.Void)
        ir.appendBlock("entry")
        ir.throwException(params[0])
        ir.finalizeFunction()
        return ir.build()
    }

    @Test
    fun `throw generates kgen_throw relocation x86`() {
        val module = buildThrowModule(Target.x86_64())
        val code = X86CodeGenerator().generateCode(module)
        assertTrue(code.relocations.any { it.symbol == "kgen_throw" },
            "Expected kgen_throw relocation, got: ${code.relocations.map { it.symbol }}")
    }

    @Test
    fun `throw generates code on arm64`() {
        val module = buildThrowModule(Target.arm64())
        val code = Arm64CodeGenerator().generateCode(module)
        assertTrue(code.textBytes.isNotEmpty())
    }

    @Test
    fun `throw generates code on riscv`() {
        val module = buildThrowModule(Target.riscv64())
        val code = RiscVCodeGenerator().generateCode(module)
        assertTrue(code.textBytes.isNotEmpty())
    }
}
