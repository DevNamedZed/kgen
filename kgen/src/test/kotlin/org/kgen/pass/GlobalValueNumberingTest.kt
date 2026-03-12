package org.kgen.pass

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*
import org.kgen.ir.build.IrBuilder
import org.kgen.ir.target.Target

class GlobalValueNumberingTest {

    private val gvn = GlobalValueNumbering()

    private fun buildAndGvn(block: IrBuilder.() -> Unit): Module {
        val ir = IrBuilder("test", Target.x86_64())
        ir.block()
        return gvn.run(ir.build())
    }

    @Test
    fun `eliminates redundant add`() {
        val module = buildAndGvn {
            val params = createFunction("f", listOf(Param("x", Type.I32), Param("y", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val a = add(params[0], params[1])
            val b = add(params[0], params[1])  // redundant
            val sum = add(a, b)
            ret(sum)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        assertEquals(3, insts.size, "Should have: add, add(a,a), ret: $insts")
        val sumInst = insts[1] as Instruction.Add
        assertEquals(sumInst.lhs.name, sumInst.rhs.name, "Both operands should reference the same value")
    }

    @Test
    fun `eliminates redundant mul`() {
        val module = buildAndGvn {
            val params = createFunction("f", listOf(Param("x", Type.I32), Param("y", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val a = mul(params[0], params[1])
            val b = mul(params[0], params[1])  // redundant
            val sum = add(a, b)
            ret(sum)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        assertEquals(3, insts.size, "Redundant mul should be eliminated: $insts")
    }

    @Test
    fun `eliminates redundant icmp`() {
        val module = buildAndGvn {
            val params = createFunction("f", listOf(Param("x", Type.I32), Param("y", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val a = icmp(ICmpPredicate.EQ, params[0], params[1])
            val b = icmp(ICmpPredicate.EQ, params[0], params[1])  // redundant
            val result = select(a, Constant.I32(1), Constant.I32(0))
            ret(result)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        // b should be eliminated, but since it's not used, DCE would handle it.
        // GVN tracks the replacement though
        val addCount = insts.count { it is Instruction.ICmp }
        assertEquals(1, addCount, "Redundant icmp should be eliminated")
    }

    @Test
    fun `does not eliminate different operations`() {
        val module = buildAndGvn {
            val params = createFunction("f", listOf(Param("x", Type.I32), Param("y", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val a = add(params[0], params[1])
            val b = sub(params[0], params[1])
            val sum = add(a, b)
            ret(sum)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        assertEquals(4, insts.size, "Different ops should both remain: $insts")
    }

    @Test
    fun `does not eliminate different operand order`() {
        val module = buildAndGvn {
            val params = createFunction("f", listOf(Param("x", Type.I32), Param("y", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val a = sub(params[0], params[1])
            val b = sub(params[1], params[0])  // different order, different result
            val sum = add(a, b)
            ret(sum)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        assertEquals(4, insts.size, "Different operand order should keep both: $insts")
    }

    @Test
    fun `does not eliminate calls`() {
        val module = buildAndGvn {
            declareFunction("side_effect", listOf(Param("x", Type.I32)), Type.I32)
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val a = call("side_effect", listOf(params[0]), Type.I32)!!
            val b = call("side_effect", listOf(params[0]), Type.I32)!!
            val sum = add(a, b)
            ret(sum)
            finalizeFunction()
        }
        val insts = module.functions[1].blocks[0].instructions
        val callCount = insts.count { it is Instruction.Call }
        assertEquals(2, callCount, "Calls should not be eliminated (side effects)")
    }

    @Test
    fun `eliminates across dominating blocks`() {
        val module = buildAndGvn {
            val params = createFunction("f", listOf(Param("x", Type.I32), Param("y", Type.I32), Param("cond", Type.I1)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val a = add(params[0], params[1])
            condBr(params[2], "then", "else")

            positionAtEnd(appendBlock("then"))
            val b = add(params[0], params[1])  // redundant, dominated by entry's add
            ret(b)

            positionAtEnd(appendBlock("else"))
            val c = add(params[0], params[1])  // redundant, dominated by entry's add
            ret(c)

            finalizeFunction()
        }
        val thenInsts = module.functions[0].blocks[1].instructions
        val elseInsts = module.functions[0].blocks[2].instructions
        // Both blocks should have just ret, with the add eliminated
        assertEquals(1, thenInsts.size, "then block: redundant add should be eliminated: $thenInsts")
        assertEquals(1, elseInsts.size, "else block: redundant add should be eliminated: $elseInsts")
    }

    @Test
    fun `scopes correctly across non-dominating blocks`() {
        val module = buildAndGvn {
            val params = createFunction("f", listOf(Param("x", Type.I32), Param("y", Type.I32), Param("cond", Type.I1)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            condBr(params[2], "then", "else")

            positionAtEnd(appendBlock("then"))
            val a = add(params[0], params[1])
            ret(a)

            positionAtEnd(appendBlock("else"))
            val b = add(params[0], params[1])
            ret(b)

            finalizeFunction()
        }
        // Neither dominates the other, so both adds should remain
        val thenInsts = module.functions[0].blocks[1].instructions
        val elseInsts = module.functions[0].blocks[2].instructions
        assertEquals(2, thenInsts.size, "then should keep its add: $thenInsts")
        assertEquals(2, elseInsts.size, "else should keep its add: $elseInsts")
    }

    @Test
    fun `eliminates chained redundancies`() {
        val module = buildAndGvn {
            val params = createFunction("f", listOf(Param("x", Type.I32), Param("y", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val a = add(params[0], params[1])
            val b = mul(a, params[0])
            val c = add(params[0], params[1])  // same as a
            val d = mul(c, params[0])           // same as b (after c→a substitution)
            val sum = add(b, d)
            ret(sum)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        // a, b, add(b,b), ret
        assertEquals(4, insts.size, "Chained redundancies should be eliminated: $insts")
        val sumInst = insts[2] as Instruction.Add
        assertEquals(sumInst.lhs.name, sumInst.rhs.name, "Both operands should be the same value")
    }

    @Test
    fun `eliminates redundant zext`() {
        val module = buildAndGvn {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I64)
            positionAtEnd(appendBlock("entry"))
            val a = zext(params[0], Type.I64)
            val b = zext(params[0], Type.I64)  // redundant
            val sum = add(a, b)
            ret(sum)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        val zextCount = insts.count { it is Instruction.ZExt }
        assertEquals(1, zextCount, "Redundant zext should be eliminated")
    }

    @Test
    fun `eliminates redundant load with no intervening store`() {
        // Two consecutive loads from the same pointer with no store between
        // are redundant — the second can be eliminated via alias analysis.
        val module = buildAndGvn {
            val params = createFunction("f", listOf(Param("p", Type.Pointer(Type.I32))), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val a = load(Type.I32, params[0])
            val b = load(Type.I32, params[0])  // redundant — no intervening store
            val sum = add(a, b)
            ret(sum)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        val loadCount = insts.count { it is Instruction.Load }
        assertEquals(1, loadCount, "Second load should be eliminated (no intervening store)")
    }
}
