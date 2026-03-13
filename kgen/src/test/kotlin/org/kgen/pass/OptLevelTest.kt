package org.kgen.pass

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*
import org.kgen.ir.instructions.*
import org.kgen.ir.build.IrBuilder
import org.kgen.ir.target.Target

class OptLevelTest {

    @Test
    fun `O0 does nothing`() {
        val ir = IrBuilder("test", Target.x86_64())
        ir.createFunction("f", emptyList(), Type.I32)
        ir.positionAtEnd(ir.appendBlock("entry"))
        val dead = ir.add(Constant.I32(1), Constant.I32(2))
        ir.ret(Constant.I32(0))
        ir.finalizeFunction()

        val result = OptLevel.O0.pipeline().execute(ir.build())
        val insts = result.functions[0].blocks[0].instructions
        assertEquals(2, insts.size, "O0 should not remove dead code")
    }

    @Test
    fun `O1 folds constants and eliminates dead code`() {
        val ir = IrBuilder("test", Target.x86_64())
        ir.createFunction("f", emptyList(), Type.I32)
        ir.positionAtEnd(ir.appendBlock("entry"))
        val a = ir.add(Constant.I32(10), Constant.I32(20))
        val dead = ir.mul(Constant.I32(3), Constant.I32(4)) // dead
        ir.ret(a)
        ir.finalizeFunction()

        val result = OptLevel.O1.pipeline().execute(ir.build())
        val insts = result.functions[0].blocks[0].instructions
        assertEquals(1, insts.size, "O1 should fold + DCE: $insts")
        val ret = insts[0] as Ret
        assertEquals(30, (ret.value as Constant.I32).value)
    }

    @Test
    fun `O1 combines identity operations`() {
        val ir = IrBuilder("test", Target.x86_64())
        val params = ir.createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
        ir.positionAtEnd(ir.appendBlock("entry"))
        val a = ir.add(params[0], Constant.I32(0))
        val b = ir.mul(a, Constant.I32(1))
        ir.ret(b)
        ir.finalizeFunction()

        val result = OptLevel.O1.pipeline().execute(ir.build())
        val insts = result.functions[0].blocks[0].instructions
        assertEquals(1, insts.size, "O1 should simplify identity chain: $insts")
        val ret = insts[0] as Ret
        assertTrue(ret.value is Parameter)
    }

    @Test
    fun `O2 catches multi-pass opportunities`() {
        val ir = IrBuilder("test", Target.x86_64())
        ir.createFunction("f", emptyList(), Type.I32)
        ir.positionAtEnd(ir.appendBlock("entry"))
        // Chain that benefits from second constant folding pass
        val a = ir.add(Constant.I32(5), Constant.I32(5))   // → 10
        val b = ir.mul(a, Constant.I32(3))                  // after fold: 10*3=30
        val c = ir.sub(b, Constant.I32(30))                 // after fold: 30-30=0
        ir.ret(c)
        ir.finalizeFunction()

        val result = OptLevel.O2.pipeline().execute(ir.build())
        val insts = result.functions[0].blocks[0].instructions
        assertEquals(1, insts.size, "O2 should fully reduce: $insts")
        val ret = insts[0] as Ret
        assertEquals(0, (ret.value as Constant.I32).value)
    }

    @Test
    fun `O1 preserves necessary operations`() {
        val ir = IrBuilder("test", Target.x86_64())
        ir.declareFunction("external_fn", listOf(Param("x", Type.I32)), Type.I32)
        val params = ir.createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
        ir.positionAtEnd(ir.appendBlock("entry"))
        val sum = ir.add(params[0], Constant.I32(5))
        val result = ir.call("external_fn", listOf(sum), Type.I32)
        ir.ret(result)
        ir.finalizeFunction()

        val opt = OptLevel.O1.pipeline().execute(ir.build())
        val insts = opt.functions[1].blocks[0].instructions
        assertEquals(3, insts.size, "add + call + ret should all survive: $insts")
    }
}
