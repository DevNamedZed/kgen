package org.kgen.codegen

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*
import org.kgen.ir.build.ModuleBuilder
import org.kgen.ir.instructions.*
import org.kgen.ir.target.Target
import org.kgen.target.x86.codegen.X86CodeGenerator
import org.kgen.target.arm64.codegen.Arm64CodeGenerator
import org.kgen.target.riscv.codegen.RiscVCodeGenerator

class CallBrCodegenTest {

    private fun buildCallBrModule(target: Target): Module {
        val ir = ModuleBuilder("callbr_test", target)

        ir.declareFunction("target_func", emptyList(), Type.Void)

        val params = ir.createFunction(
            "caller",
            listOf(Param("x", Type.I32)),
            Type.I32,
        )
        ir.appendBlock("entry")
        val func = FunctionRef("target_func", Type.Function(emptyList(), Type.Void))
        ir.callBr(func, emptyList(), Type.Void, "fallthrough", listOf("indirect_target"))

        ir.appendBlock("fallthrough")
        ir.ret(Constant.I32(0))

        ir.appendBlock("indirect_target")
        ir.ret(Constant.I32(1))

        ir.finalizeFunction()
        return ir.build()
    }

    private fun buildCallBrWithResultModule(target: Target): Module {
        val ir = ModuleBuilder("callbr_result_test", target)

        ir.declareFunction("asm_func", listOf(Param("input", Type.I32)), Type.I32)

        val params = ir.createFunction(
            "caller",
            listOf(Param("x", Type.I32)),
            Type.I32,
        )
        ir.appendBlock("entry")
        val func = FunctionRef("asm_func", Type.Function(listOf(Type.I32), Type.I32))
        val result = ir.callBr(func, listOf(params[0]), Type.I32, "normal", listOf("error"))

        ir.appendBlock("normal")
        ir.ret(result ?: Constant.I32(0))

        ir.appendBlock("error")
        ir.ret(Constant.I32(-1))

        ir.finalizeFunction()
        return ir.build()
    }

    private fun buildCallBrMultipleIndirectModule(target: Target): Module {
        val ir = ModuleBuilder("callbr_multi_test", target)

        ir.declareFunction("dispatch_func", emptyList(), Type.Void)

        ir.createFunction(
            "caller",
            listOf(Param("x", Type.I32)),
            Type.I32,
        )
        ir.appendBlock("entry")
        val func = FunctionRef("dispatch_func", Type.Function(emptyList(), Type.Void))
        ir.callBr(func, emptyList(), Type.Void, "fallthrough", listOf("target_a", "target_b", "target_c"))

        ir.appendBlock("fallthrough")
        ir.ret(Constant.I32(0))

        ir.appendBlock("target_a")
        ir.ret(Constant.I32(1))

        ir.appendBlock("target_b")
        ir.ret(Constant.I32(2))

        ir.appendBlock("target_c")
        ir.ret(Constant.I32(3))

        ir.finalizeFunction()
        return ir.build()
    }

    @Test
    fun callBrInstructionHasCorrectFields() {
        val func = FunctionRef("target", Type.Function(emptyList(), Type.Void))
        val dest = InstructionRef("res", Type.I32)
        val inst = CallBr(dest, func, listOf(Constant.I32(1)), Type.I32, BlockRef("fall"), listOf(BlockRef("ind1"), BlockRef("ind2")))
        assertEquals(BlockRef("fall"), inst.fallthrough)
        assertEquals(2, inst.indirectDests.size)
        assertEquals(BlockRef("ind1"), inst.indirectDests[0])
        assertEquals(BlockRef("ind2"), inst.indirectDests[1])
        assertEquals(dest, inst.result)
        assertEquals(IrCategory.CALL, inst.category)
    }

    @Test
    fun callBrWithNullDestHasNoResult() {
        val func = FunctionRef("target", Type.Function(emptyList(), Type.Void))
        val inst = CallBr(null, func, emptyList(), Type.Void, BlockRef("fall"), listOf(BlockRef("ind")))
        assertNull(inst.result)
    }

    @Test
    fun x86GeneratesCodeForCallBr() {
        val module = buildCallBrModule(Target.x86_64())
        val obj = X86CodeGenerator().generateObjectFile(module)
        val textSection = obj.sections.first { it.name == ".text" }
        assertTrue(textSection.data.isNotEmpty())
    }

    @Test
    fun arm64GeneratesCodeForCallBr() {
        val module = buildCallBrModule(Target.arm64())
        val obj = Arm64CodeGenerator().generateObjectFile(module)
        val textSection = obj.sections.first { it.name == ".text" }
        assertTrue(textSection.data.isNotEmpty())
    }

    @Test
    fun riscvGeneratesCodeForCallBr() {
        val module = buildCallBrModule(Target.riscv64())
        val obj = RiscVCodeGenerator().generateObjectFile(module)
        val textSection = obj.sections.first { it.name == ".text" }
        assertTrue(textSection.data.isNotEmpty())
    }

    @Test
    fun x86CallBrWithResultProducesCode() {
        val module = buildCallBrWithResultModule(Target.x86_64())
        val obj = X86CodeGenerator().generateObjectFile(module)
        val textSection = obj.sections.first { it.name == ".text" }
        assertTrue(textSection.data.isNotEmpty())
    }

    @Test
    fun x86CallBrMultipleIndirectTargetsProducesCode() {
        val module = buildCallBrMultipleIndirectModule(Target.x86_64())
        val obj = X86CodeGenerator().generateObjectFile(module)
        val textSection = obj.sections.first { it.name == ".text" }
        assertTrue(textSection.data.isNotEmpty())
    }

    @Test
    fun callBrDataClassEquality() {
        val func = FunctionRef("target", Type.Function(emptyList(), Type.Void))
        val inst1 = CallBr(null, func, emptyList(), Type.Void, BlockRef("fall"), listOf(BlockRef("ind")))
        val inst2 = CallBr(null, func, emptyList(), Type.Void, BlockRef("fall"), listOf(BlockRef("ind")))
        assertEquals(inst1, inst2)
        assertEquals(inst1.hashCode(), inst2.hashCode())
    }

    @Test
    fun callBrDifferentFallthroughNotEqual() {
        val func = FunctionRef("target", Type.Function(emptyList(), Type.Void))
        val inst1 = CallBr(null, func, emptyList(), Type.Void, BlockRef("fall_a"), listOf(BlockRef("ind")))
        val inst2 = CallBr(null, func, emptyList(), Type.Void, BlockRef("fall_b"), listOf(BlockRef("ind")))
        assertNotEquals(inst1, inst2)
    }

    @Test
    fun callBrImplementsCallInstruction() {
        val func = FunctionRef("target", Type.Function(emptyList(), Type.Void))
        val inst: CallInstruction = CallBr(null, func, emptyList(), Type.Void, BlockRef("fall"), emptyList())
        assertEquals(func, inst.function)
        assertEquals(Type.Void, inst.returnType)
        assertTrue(inst.args.isEmpty())
    }
}
