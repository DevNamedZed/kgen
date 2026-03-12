package org.kgen.pass

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*
import org.kgen.ir.build.IrBuilder
import org.kgen.ir.target.Target

class Mem2RegLoopTest {

    private val mem2reg = Mem2Reg()

    private fun buildAndPromote(block: IrBuilder.() -> Unit): Module {
        val ir = IrBuilder("test", Target.x86_64())
        ir.block()
        return mem2reg.run(ir.build())
    }

    private fun findPhis(fn: IrFunction): List<Pair<String, Instruction.Phi>> {
        return fn.blocks.flatMap { block ->
            block.instructions.filterIsInstance<Instruction.Phi>().map { block.label to it }
        }
    }

    private fun findPhisInBlock(fn: IrFunction, label: String): List<Instruction.Phi> {
        return fn.blocks.first { it.label == label }.instructions.filterIsInstance<Instruction.Phi>()
    }

    @Test
    fun `simple loop creates phi at header`() {
        // entry: i=0, br header
        // header: cond = i < 10, condBr exit/body
        // body: i = i+1, br header
        // exit: ret i
        val module = buildAndPromote {
            createFunction("loop", emptyList(), Type.I32)
            val entry = appendBlock("entry")
            val header = appendBlock("header")
            val body = appendBlock("body")
            val exit = appendBlock("exit")

            positionAtEnd(entry)
            val iPtr = alloca(Type.I32)
            store(Constant.I32(0), iPtr)
            br(header)

            positionAtEnd(header)
            val iVal = load(Type.I32, iPtr)
            val cond = icmp(ICmpPredicate.SLT, iVal, Constant.I32(10))
            condBr(cond, body, exit)

            positionAtEnd(body)
            val next = add(load(Type.I32, iPtr), Constant.I32(1))
            store(next, iPtr)
            br(header)

            positionAtEnd(exit)
            val result = load(Type.I32, iPtr)
            ret(result)
            finalizeFunction()
        }

        val fn = module.functions[0]

        // Header should have a phi node for the loop variable
        val headerPhis = findPhisInBlock(fn, "header")
        assertEquals(1, headerPhis.size, "Expected one phi at header: ${fn.blocks}")

        val phi = headerPhis[0]
        assertEquals(2, phi.incoming.size, "Phi should have two incoming edges")
        val preds = phi.incoming.map { it.second }.toSet()
        assertTrue("entry" in preds, "Phi should have entry as predecessor")
        assertTrue("body" in preds, "Phi should have body as predecessor")

        // Entry incoming value should be the initial constant 0
        val entryVal = phi.incoming.first { it.second == "entry" }.first
        assertTrue(entryVal is Constant.I32 && entryVal.value == 0, "Entry value should be 0, got: $entryVal")

        // No alloca, load, or store instructions should remain
        for (block in fn.blocks) {
            for (inst in block.instructions) {
                assertFalse(inst is Instruction.Alloca, "Alloca should be removed: $inst")
                assertFalse(inst is Instruction.Load, "Load should be removed: $inst in ${block.label}")
                assertFalse(inst is Instruction.Store, "Store should be removed: $inst in ${block.label}")
            }
        }
    }

    @Test
    fun `transitive replacement resolves through removed loads`() {
        // Two allocas where the second depends on loading the first:
        // entry: a = alloca, b = alloca
        //        store 42 → a
        //        tmp = load a
        //        store tmp → b
        //        result = load b
        //        ret result
        // After Mem2Reg, result should be 42 (transitively resolved)
        val module = buildAndPromote {
            createFunction("transitive", emptyList(), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val a = alloca(Type.I32)
            val b = alloca(Type.I32)
            store(Constant.I32(42), a)
            val tmp = load(Type.I32, a)
            store(tmp, b)
            val result = load(Type.I32, b)
            ret(result)
            finalizeFunction()
        }

        val fn = module.functions[0]
        val insts = fn.blocks[0].instructions
        assertEquals(1, insts.size, "Only ret should remain: $insts")
        val ret = insts[0] as Instruction.Ret
        val retVal = ret.value
        assertTrue(retVal is Constant.I32 && retVal.value == 42,
            "Return should be transitively resolved to 42, got: $retVal")
    }

    @Test
    fun `transitive replacement through loop phi`() {
        // entry: a = alloca, b = alloca, store 0 → a, br header
        // header: tmp = load a, store tmp → b, cond, condBr body/exit
        // body: store (load b + 1) → a, br header
        // exit: ret (load b)
        // After Mem2Reg, b's uses should resolve to a's phi, not a dangling ref
        val module = buildAndPromote {
            createFunction("transLoop", emptyList(), Type.I32)
            val entry = appendBlock("entry")
            val header = appendBlock("header")
            val body = appendBlock("body")
            val exit = appendBlock("exit")

            positionAtEnd(entry)
            val a = alloca(Type.I32)
            val b = alloca(Type.I32)
            store(Constant.I32(0), a)
            store(Constant.I32(0), b)
            br(header)

            positionAtEnd(header)
            val aVal = load(Type.I32, a)
            store(aVal, b)
            val cond = icmp(ICmpPredicate.SLT, aVal, Constant.I32(10))
            condBr(cond, body, exit)

            positionAtEnd(body)
            val bVal = load(Type.I32, b)
            val next = add(bVal, Constant.I32(1))
            store(next, a)
            br(header)

            positionAtEnd(exit)
            val result = load(Type.I32, b)
            ret(result)
            finalizeFunction()
        }

        val fn = module.functions[0]

        // No loads or stores should remain
        for (block in fn.blocks) {
            for (inst in block.instructions) {
                assertFalse(inst is Instruction.Alloca, "Alloca should be removed in ${block.label}: $inst")
                assertFalse(inst is Instruction.Load, "Load should be removed in ${block.label}: $inst")
                assertFalse(inst is Instruction.Store, "Store should be removed in ${block.label}: $inst")
            }
        }

        // The ret value should not be a dangling InstructionRef to a removed load
        val exitBlock = fn.blocks.first { it.label == "exit" }
        val ret = exitBlock.instructions.last() as Instruction.Ret
        val retVal = ret.value
        if (retVal is InstructionRef) {
            // It should reference a phi or an add, not a removed load
            val allDests = fn.blocks.flatMap { b -> b.instructions.mapNotNull { it.result?.name } }
            assertTrue(retVal.name in allDests,
                "Return value ${retVal.name} should reference an existing instruction, not a removed one")
        }
    }

    @Test
    fun `nested loop creates phis at both headers`() {
        // entry → outer_header → inner_header → inner_body → inner_header → outer_body → outer_header → exit
        val module = buildAndPromote {
            createFunction("nested", emptyList(), Type.I32)
            val entry = appendBlock("entry")
            val outerHeader = appendBlock("outer_header")
            val innerHeader = appendBlock("inner_header")
            val innerBody = appendBlock("inner_body")
            val outerBody = appendBlock("outer_body")
            val exit = appendBlock("exit")

            positionAtEnd(entry)
            val iPtr = alloca(Type.I32)
            val jPtr = alloca(Type.I32)
            store(Constant.I32(0), iPtr)
            br(outerHeader)

            positionAtEnd(outerHeader)
            val i = load(Type.I32, iPtr)
            val outerCond = icmp(ICmpPredicate.SLT, i, Constant.I32(5))
            store(Constant.I32(0), jPtr)
            condBr(outerCond, innerHeader, exit)

            positionAtEnd(innerHeader)
            val j = load(Type.I32, jPtr)
            val innerCond = icmp(ICmpPredicate.SLT, j, Constant.I32(3))
            condBr(innerCond, innerBody, outerBody)

            positionAtEnd(innerBody)
            val jNext = add(load(Type.I32, jPtr), Constant.I32(1))
            store(jNext, jPtr)
            br(innerHeader)

            positionAtEnd(outerBody)
            val iNext = add(load(Type.I32, iPtr), Constant.I32(1))
            store(iNext, iPtr)
            br(outerHeader)

            positionAtEnd(exit)
            val result = load(Type.I32, iPtr)
            ret(result)
            finalizeFunction()
        }

        val fn = module.functions[0]

        val outerPhis = findPhisInBlock(fn, "outer_header")
        assertTrue(outerPhis.isNotEmpty(), "Outer header should have phi for i: ${fn.blocks}")

        val innerPhis = findPhisInBlock(fn, "inner_header")
        assertTrue(innerPhis.isNotEmpty(), "Inner header should have phi for j: ${fn.blocks}")

        // No alloca/load/store should remain
        for (block in fn.blocks) {
            for (inst in block.instructions) {
                assertFalse(inst is Instruction.Alloca, "Alloca should be removed")
                assertFalse(inst is Instruction.Load, "Load should be removed in ${block.label}")
                assertFalse(inst is Instruction.Store, "Store should be removed in ${block.label}")
            }
        }
    }

    @Test
    fun `loop with multiple stores from different paths`() {
        // entry: ptr = alloca, store 0, br header
        // header: load ptr, cond, condBr left/right
        // left: store 10, br merge
        // right: store 20, br merge
        // merge: br header (or exit based on a counter — keep simple, just exit)
        // exit: ret load ptr
        val module = buildAndPromote {
            createFunction("multiStore", listOf(Param("flag", Type.I1)), Type.I32)
            val entry = appendBlock("entry")
            val header = appendBlock("header")
            val left = appendBlock("left")
            val right = appendBlock("right")
            val merge = appendBlock("merge")
            val exit = appendBlock("exit")

            positionAtEnd(entry)
            val ptr = alloca(Type.I32)
            store(Constant.I32(0), ptr)
            br(header)

            positionAtEnd(header)
            val v = load(Type.I32, ptr)
            val loopCond = icmp(ICmpPredicate.SLT, v, Constant.I32(100))
            condBr(loopCond, left, exit)

            positionAtEnd(left)
            store(Constant.I32(10), ptr)
            br(merge)

            positionAtEnd(right)
            store(Constant.I32(20), ptr)
            br(merge)

            positionAtEnd(merge)
            br(header)

            positionAtEnd(exit)
            ret(load(Type.I32, ptr))
            finalizeFunction()
        }

        val fn = module.functions[0]

        // Header needs a phi (entry + merge as predecessors)
        val headerPhis = findPhisInBlock(fn, "header")
        assertTrue(headerPhis.isNotEmpty(), "Header should have a phi node")

        // Merge needs a phi (left + right as predecessors, both store)
        val mergePhis = findPhisInBlock(fn, "merge")
        assertTrue(mergePhis.isNotEmpty(), "Merge should have a phi for different store values")

        for (block in fn.blocks) {
            for (inst in block.instructions) {
                assertFalse(inst is Instruction.Alloca, "Alloca should be removed")
                assertFalse(inst is Instruction.Load, "Load should be removed in ${block.label}")
                assertFalse(inst is Instruction.Store, "Store should be removed in ${block.label}")
            }
        }
    }

    @Test
    fun `straight line code needs no phi nodes`() {
        val module = buildAndPromote {
            createFunction("straight", emptyList(), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val a = alloca(Type.I32)
            store(Constant.I32(1), a)
            val v1 = load(Type.I32, a)
            val v2 = add(v1, Constant.I32(2))
            store(v2, a)
            val v3 = load(Type.I32, a)
            ret(v3)
            finalizeFunction()
        }

        val fn = module.functions[0]
        val phis = findPhis(fn)
        assertTrue(phis.isEmpty(), "Straight-line code should produce no phi nodes: $phis")

        // Should simplify to: add 1, 2 → ret result
        val insts = fn.blocks[0].instructions
        assertEquals(2, insts.size, "Should have add + ret: $insts")
        assertTrue(insts[0] is Instruction.Add)
        assertTrue(insts[1] is Instruction.Ret)

        val ret = insts[1] as Instruction.Ret
        assertEquals(insts[0].result, ret.value, "Ret should use the add result")
    }
}
