package org.kgen.pass

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*
import org.kgen.ir.instructions.*
import org.kgen.ir.build.IrBuilder
import org.kgen.ir.target.Target

class SafepointInsertionTest {

    @Test
    fun noInsertionWithoutGcStrategy() {
        val ir = IrBuilder("test", Target.x86_64())
        ir.createFunction("f", emptyList(), Type.Void)
        ir.positionAtEnd(ir.appendBlock("entry"))
        ir.ret()
        ir.finalizeFunction()
        val module = ir.build()

        val result = SafepointInsertion().run(module)
        val instrs = result.functions[0].blocks[0].instructions
        assertEquals(1, instrs.size)
        assertTrue(instrs[0] is Ret)
    }

    @Test
    fun insertsBeforeCallSite() {
        val module = buildFunctionWithCall("statepoint")
        val result = SafepointInsertion().run(module)
        val instrs = findFunc(result, "f").blocks[0].instructions
        assertTrue(instrs[0] is GCSafepoint, "Should insert safepoint before call")
        assertTrue(instrs[1] is Call, "Call should follow safepoint")
    }

    @Test
    fun insertsAtLoopBackEdge() {
        val module = buildLoopFunction("statepoint")
        val result = SafepointInsertion().run(module)

        val loopBlock = result.functions[0].blocks[1]
        val lastTwo = loopBlock.instructions.takeLast(2)
        assertTrue(lastTwo[0] is GCSafepoint, "Should insert safepoint before back-edge")
        assertTrue(lastTwo[1] is CondBr, "Back-edge branch should follow")
    }

    @Test
    fun noDuplicateSafepoints() {
        val module = buildFunctionWithExistingSafepoint("statepoint")
        val result = SafepointInsertion().run(module)
        val instrs = findFunc(result, "f").blocks[0].instructions
        val safepointCount = instrs.count { it is GCSafepoint }
        assertEquals(1, safepointCount, "Should not duplicate existing safepoint")
    }

    @Test
    fun skipsNoneStrategy() {
        val module = buildFunctionWithCall("none")
        val result = SafepointInsertion().run(module)
        val instrs = findFunc(result, "f").blocks[0].instructions
        assertFalse(instrs.any { it is GCSafepoint })
    }

    @Test
    fun worksWithShadowStack() {
        val module = buildFunctionWithCall("shadow-stack")
        val result = SafepointInsertion().run(module)
        val instrs = findFunc(result, "f").blocks[0].instructions
        assertTrue(instrs[0] is GCSafepoint)
    }

    @Test
    fun skipsExternalFunctions() {
        val ir = IrBuilder("test", Target.x86_64())
        ir.declareFunction("external", emptyList(), Type.Void)
        val module = ir.build()
        val result = SafepointInsertion().run(module)
        assertEquals(module, result)
    }

    @Test
    fun insertsBeforeManagedCall() {
        val ir = IrBuilder("test", Target.x86_64())
        ir.declareFunction("target", listOf(Param("x", Type.I32)), Type.I32)
        ir.createFunction("f", emptyList(), Type.I32)
        ir.positionAtEnd(ir.appendBlock("entry"))
        val fn = GlobalRef("target", Type.Function(listOf(Type.I32), Type.I32))
        val r = ir.managedCall(fn, listOf(Constant.I32(1)), Type.I32, ManagedCallDirection.MANAGED_TO_NATIVE)!!
        ir.ret(r)
        ir.finalizeFunction()
        val module = ir.build()
        val withGc = module.copy(functions = module.functions.map {
            if (it.name == "f") it.copy(gc = "statepoint") else it
        })

        val result = SafepointInsertion().run(withGc)
        val instrs = findFunc(result, "f").blocks[0].instructions
        assertTrue(instrs[0] is GCSafepoint)
        assertTrue(instrs[1] is ManagedCall)
    }

    private fun buildFunctionWithCall(gc: String): Module {
        val ir = IrBuilder("test", Target.x86_64())
        ir.declareFunction("puts", listOf(Param("s", Type.I32)), Type.Void)
        ir.createFunction("f", emptyList(), Type.Void)
        ir.positionAtEnd(ir.appendBlock("entry"))
        ir.call(GlobalRef("puts", Type.Function(listOf(Type.I32), Type.Void)), listOf(Constant.I32(0)), Type.Void)
        ir.ret()
        ir.finalizeFunction()
        val module = ir.build()
        return module.copy(functions = module.functions.map {
            if (it.name == "f") it.copy(gc = gc) else it
        })
    }

    private fun buildLoopFunction(gc: String): Module {
        val ir = IrBuilder("test", Target.x86_64())
        ir.createFunction("loop", listOf(Param("n", Type.I32)), Type.Void)

        val entry = ir.appendBlock("entry")
        val loopBody = ir.appendBlock("loop")
        val exit = ir.appendBlock("exit")

        ir.positionAtEnd(entry)
        ir.br("loop")

        ir.positionAtEnd(loopBody)
        val n = Parameter("n", Type.I32, 0)
        val cond = ir.icmp(ICmpPredicate.EQ, n, Constant.I32(0))
        ir.condBr(cond, "exit", "loop")

        ir.positionAtEnd(exit)
        ir.ret()
        ir.finalizeFunction()

        val module = ir.build()
        return module.copy(functions = module.functions.map {
            if (it.name == "loop") it.copy(gc = gc) else it
        })
    }

    private fun buildFunctionWithExistingSafepoint(gc: String): Module {
        val ir = IrBuilder("test", Target.x86_64())
        ir.declareFunction("puts", listOf(Param("s", Type.I32)), Type.Void)
        ir.createFunction("f", emptyList(), Type.Void)
        ir.positionAtEnd(ir.appendBlock("entry"))
        ir.gcSafepoint()
        ir.call(GlobalRef("puts", Type.Function(listOf(Type.I32), Type.Void)), listOf(Constant.I32(0)), Type.Void)
        ir.ret()
        ir.finalizeFunction()
        val module = ir.build()
        return module.copy(functions = module.functions.map {
            if (it.name == "f") it.copy(gc = gc) else it
        })
    }

    private fun findFunc(module: Module, name: String): IrFunction {
        for (f in module.functions) { if (f.name == name) return f }
        throw IllegalArgumentException("Function not found: $name")
    }
}
