package org.kgen.pass

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*
import org.kgen.ir.instructions.*
import org.kgen.ir.build.IrBuilder
import org.kgen.ir.target.Target

class PinningLoweringTest {

    @Test
    fun lowersPinToRuntimeCall() {
        val module = buildPinModule()
        val result = PinningLowering(movingGC = true).run(module)
        val instrs = findFunc(result, "f").blocks[0].instructions

        // Pin should become a Call to __kgen_rt_pin
        assertTrue(instrs[0] is Call)
        val call = instrs[0] as Call
        assertEquals(PinningLowering.RT_PIN, (call.function as GlobalRef).name)
    }

    @Test
    fun lowersUnpinToRuntimeCall() {
        val module = buildPinUnpinModule()
        val result = PinningLowering(movingGC = true).run(module)
        val instrs = findFunc(result, "f").blocks[0].instructions

        // Unpin should become a Call to __kgen_rt_unpin
        val unpinCall = instrs.filterIsInstance<Call>()
            .find { (it.function as? GlobalRef)?.name == PinningLowering.RT_UNPIN }
        assertNotNull(unpinCall)
    }

    @Test
    fun lowersPinToIdentityForNonMovingGC() {
        val module = buildPinModule()
        val result = PinningLowering(movingGC = false).run(module)
        val instrs = findFunc(result, "f").blocks[0].instructions

        // Pin should become a BitCast (identity)
        assertTrue(instrs[0] is BitCast)
    }

    @Test
    fun removesUnpinForNonMovingGC() {
        val module = buildPinUnpinModule()
        val result = PinningLowering(movingGC = false).run(module)
        val instrs = findFunc(result, "f").blocks[0].instructions

        // Unpin should be removed entirely (no-op)
        assertFalse(instrs.any { it is Unpin })
        // Should still have the pin (as BitCast) and ret
        assertTrue(instrs.any { it is BitCast })
    }

    @Test
    fun lowersWriteBarrierToCall() {
        val module = buildWriteBarrierModule()
        val result = PinningLowering(movingGC = true).run(module)
        val instrs = findFunc(result, "f").blocks[0].instructions

        val wbCall = instrs.filterIsInstance<Call>()
            .find { (it.function as? GlobalRef)?.name == PinningLowering.RT_WRITE_BARRIER }
        assertNotNull(wbCall)
        assertFalse(instrs.any { it is WriteBarrier })
    }

    @Test
    fun lowersReadBarrierToCallForMovingGC() {
        val module = buildReadBarrierModule()
        val result = PinningLowering(movingGC = true).run(module)
        val instrs = findFunc(result, "f").blocks[0].instructions

        val rbCall = instrs.filterIsInstance<Call>()
            .find { (it.function as? GlobalRef)?.name == PinningLowering.RT_READ_BARRIER }
        assertNotNull(rbCall)
    }

    @Test
    fun lowersReadBarrierToIdentityForNonMovingGC() {
        val module = buildReadBarrierModule()
        val result = PinningLowering(movingGC = false).run(module)
        val instrs = findFunc(result, "f").blocks[0].instructions

        assertTrue(instrs[0] is BitCast)
        assertFalse(instrs.any { it is ReadBarrier })
    }

    @Test
    fun skipsExternalFunctions() {
        val ir = IrBuilder("test", Target.x86_64())
        ir.declareFunction("ext", emptyList(), Type.Void)
        val module = ir.build()
        val result = PinningLowering().run(module)
        assertEquals(module, result)
    }

    @Test
    fun preservesOtherInstructions() {
        val module = buildPinModule()
        val result = PinningLowering(movingGC = true).run(module)
        val instrs = findFunc(result, "f").blocks[0].instructions

        // Should still have the return instruction
        assertTrue(instrs.last() is Ret)
    }

    private fun buildPinModule(): Module {
        val ir = IrBuilder("test", Target.x86_64())
        ir.createFunction("f", listOf(Param("obj", Type.Reference(Type.I64, false))), Type.Void)
        ir.positionAtEnd(ir.appendBlock("entry"))
        val param = Parameter("obj", Type.Reference(Type.I64, false), 0)
        ir.pin(param)
        ir.ret()
        ir.finalizeFunction()
        return ir.build()
    }

    private fun buildPinUnpinModule(): Module {
        val ir = IrBuilder("test", Target.x86_64())
        ir.createFunction("f", listOf(Param("obj", Type.Reference(Type.I64, false))), Type.Void)
        ir.positionAtEnd(ir.appendBlock("entry"))
        val param = Parameter("obj", Type.Reference(Type.I64, false), 0)
        val pinned = ir.pin(param)
        ir.unpin(pinned)
        ir.ret()
        ir.finalizeFunction()
        return ir.build()
    }

    private fun buildWriteBarrierModule(): Module {
        val ir = IrBuilder("test", Target.x86_64())
        ir.createFunction("f", listOf(
            Param("obj", Type.Reference(Type.I64, false)),
            Param("val", Type.Reference(Type.I32, false)),
        ), Type.Void)
        ir.positionAtEnd(ir.appendBlock("entry"))
        val obj = Parameter("obj", Type.Reference(Type.I64, false), 0)
        val v = Parameter("val", Type.Reference(Type.I32, false), 1)
        ir.writeBarrier(obj, Constant.I32(0), v)
        ir.ret()
        ir.finalizeFunction()
        return ir.build()
    }

    private fun buildReadBarrierModule(): Module {
        val ir = IrBuilder("test", Target.x86_64())
        ir.createFunction("f", listOf(Param("r", Type.Reference(Type.I64))), Type.Reference(Type.I64))
        ir.positionAtEnd(ir.appendBlock("entry"))
        val param = Parameter("r", Type.Reference(Type.I64), 0)
        val result = ir.readBarrier(param)
        ir.ret(result)
        ir.finalizeFunction()
        return ir.build()
    }

    private fun findFunc(module: Module, name: String): IrFunction {
        for (f in module.functions) { if (f.name == name) return f }
        throw IllegalArgumentException("Function not found: $name")
    }
}
