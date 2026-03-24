package org.kgen.pipeline

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*
import org.kgen.ir.instructions.*
import org.kgen.ir.build.ModuleBuilder
import org.kgen.ir.target.Target

class TransitionThunkLoweringTest {

    @Test
    fun lowersManagedToNative() {
        val module = buildManagedCallModule(ManagedCallDirection.MANAGED_TO_NATIVE)
        val result = TransitionThunkLowering().run(module)
        val instrs = findFunc(result, "caller").blocks[0].instructions

        // Should be: GCSafepoint, Call(leave_managed), Call(target), Call(enter_managed), ret
        assertTrue(instrs[0] is GCSafepoint, "Should start with safepoint")
        val leaveCall = instrs[1] as Call
        assertEquals(TransitionThunkLowering.RT_LEAVE_MANAGED, (leaveCall.function as GlobalRef).name)
        assertTrue(instrs[2] is Call, "Should have actual call")
        val enterCall = instrs[3] as Call
        assertEquals(TransitionThunkLowering.RT_ENTER_MANAGED, (enterCall.function as GlobalRef).name)
    }

    @Test
    fun lowersNativeToManaged() {
        val module = buildManagedCallModule(ManagedCallDirection.NATIVE_TO_MANAGED)
        val result = TransitionThunkLowering().run(module)
        val instrs = findFunc(result, "caller").blocks[0].instructions

        // Should be: Call(enter_managed), Call(target), GCSafepoint, Call(leave_managed), ret
        val enterCall = instrs[0] as Call
        assertEquals(TransitionThunkLowering.RT_ENTER_MANAGED, (enterCall.function as GlobalRef).name)
        assertTrue(instrs[1] is Call, "Should have actual call")
        assertTrue(instrs[2] is GCSafepoint, "Should have safepoint after")
        val leaveCall = instrs[3] as Call
        assertEquals(TransitionThunkLowering.RT_LEAVE_MANAGED, (leaveCall.function as GlobalRef).name)
    }

    @Test
    fun preservesReturnValue() {
        val module = buildManagedCallWithReturn()
        val result = TransitionThunkLowering().run(module)
        val instrs = findFunc(result, "caller").blocks[0].instructions

        // The lowered Call should preserve the dest from the original ManagedCall
        val actualCall = instrs[2] as Call
        assertNotNull(actualCall.result)
    }

    @Test
    fun skipsExternalFunctions() {
        val ir = ModuleBuilder("test", Target.x86_64())
        ir.declareFunction("external", emptyList(), Type.Void)
        val module = ir.build()
        val result = TransitionThunkLowering().run(module)
        assertEquals(module, result)
    }

    @Test
    fun lowersVoidManagedCall() {
        val module = buildVoidManagedCall()
        val result = TransitionThunkLowering().run(module)
        val instrs = findFunc(result, "caller").blocks[0].instructions

        // No ManagedCall should remain
        assertFalse(instrs.any { it is ManagedCall })
        // Should have regular Call instructions
        assertTrue(instrs.any { it is Call })
    }

    private fun buildManagedCallModule(direction: ManagedCallDirection): Module {
        val ir = ModuleBuilder("test", Target.x86_64())
        ir.declareFunction("target", listOf(Param("x", Type.I32)), Type.Void)
        ir.createFunction("caller", emptyList(), Type.Void)
        ir.appendBlock("entry")
        val fn = GlobalRef("target", Type.Function(listOf(Type.I32), Type.Void))
        ir.managedCall(fn, listOf(Constant.I32(1)), Type.Void, direction)
        ir.ret()
        ir.finalizeFunction()
        return ir.build()
    }

    private fun buildManagedCallWithReturn(): Module {
        val ir = ModuleBuilder("test", Target.x86_64())
        ir.declareFunction("target", listOf(Param("x", Type.I32)), Type.I32)
        ir.createFunction("caller", emptyList(), Type.I32)
        ir.appendBlock("entry")
        val fn = GlobalRef("target", Type.Function(listOf(Type.I32), Type.I32))
        val r = ir.managedCall(fn, listOf(Constant.I32(1)), Type.I32, ManagedCallDirection.MANAGED_TO_NATIVE)!!
        ir.ret(r)
        ir.finalizeFunction()
        return ir.build()
    }

    private fun buildVoidManagedCall(): Module {
        val ir = ModuleBuilder("test", Target.x86_64())
        ir.declareFunction("log", listOf(Param("x", Type.I32)), Type.Void)
        ir.createFunction("caller", emptyList(), Type.Void)
        ir.appendBlock("entry")
        val fn = GlobalRef("log", Type.Function(listOf(Type.I32), Type.Void))
        ir.managedCall(fn, listOf(Constant.I32(0)), Type.Void, ManagedCallDirection.NATIVE_TO_MANAGED)
        ir.ret()
        ir.finalizeFunction()
        return ir.build()
    }

    private fun findFunc(module: Module, name: String): IrFunction {
        for (f in module.functions) { if (f.name == name) return f }
        throw IllegalArgumentException("Function not found: $name")
    }
}
