package org.kgen.pass

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*
import org.kgen.ir.build.IrBuilder
import org.kgen.ir.target.Target

class PassPipelineExtendedTest {

    private fun build(target: Target = Target.x86_64(), block: IrBuilder.() -> Unit): Module {
        val ir = IrBuilder("test", target)
        ir.block()
        return ir.build()
    }

    // --- Pipeline construction ---

    @Test
    fun singlePassPipeline() {
        val module = build {
            createFunction("f", emptyList(), Type.I32)
            positionAtEnd(appendBlock("entry"))
            ret(add(Constant.I32(3), Constant.I32(7)))
            finalizeFunction()
        }
        val result = PassPipeline().add(ConstantFolding()).execute(module)
        val ret = result.functions[0].blocks[0].instructions.last() as Instruction.Ret
        assertEquals(10, (ret.value as Constant.I32).value)
    }

    @Test
    fun threePassPipeline() {
        val module = build {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val a = add(params[0], Constant.I32(0))
            val b = add(Constant.I32(1), Constant.I32(2))
            mul(Constant.I32(5), Constant.I32(6)) // dead
            val c = add(a, b)
            ret(c)
            finalizeFunction()
        }
        val result = PassPipeline()
            .add(InstructionCombining())
            .add(ConstantFolding())
            .add(DeadCodeElimination())
            .execute(module)
        val insts = result.functions[0].blocks[0].instructions
        // instcombine: add(x,0)->x, fold: add(1,2)->3, DCE: removes dead mul
        // Result: add(x, 3), ret
        assertEquals(2, insts.size, "Should have add and ret: $insts")
    }

    // --- Fold + instcombine + DCE comprehensive ---

    @Test
    fun foldThenInstcombineThenDCE() {
        val module = build {
            createFunction("f", emptyList(), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val a = add(Constant.I32(5), Constant.I32(5)) // fold → 10
            val b = mul(a, Constant.I32(1))                // instcombine → a (which is now 10)
            add(Constant.I32(99), Constant.I32(1))         // dead
            ret(b)
            finalizeFunction()
        }
        val result = PassPipeline()
            .add(ConstantFolding())
            .add(InstructionCombining())
            .add(DeadCodeElimination())
            .execute(module)
        val insts = result.functions[0].blocks[0].instructions
        assertEquals(1, insts.size)
        assertEquals(10, ((insts[0] as Instruction.Ret).value as Constant.I32).value)
    }

    // --- Multiple functions independently optimized ---

    @Test
    fun threeFunctionsOptimized() {
        val module = build {
            createFunction("f1", emptyList(), Type.I32)
            positionAtEnd(appendBlock("entry"))
            ret(add(Constant.I32(1), Constant.I32(1)))
            finalizeFunction()

            createFunction("f2", emptyList(), Type.I32)
            positionAtEnd(appendBlock("entry"))
            ret(mul(Constant.I32(3), Constant.I32(3)))
            finalizeFunction()

            createFunction("f3", emptyList(), Type.I32)
            positionAtEnd(appendBlock("entry"))
            ret(sub(Constant.I32(100), Constant.I32(42)))
            finalizeFunction()
        }
        val result = PassPipeline().add(ConstantFolding()).execute(module)
        assertEquals(2, ((result.functions[0].blocks[0].instructions.last() as Instruction.Ret).value as Constant.I32).value)
        assertEquals(9, ((result.functions[1].blocks[0].instructions.last() as Instruction.Ret).value as Constant.I32).value)
        assertEquals(58, ((result.functions[2].blocks[0].instructions.last() as Instruction.Ret).value as Constant.I32).value)
    }

    // --- Pass interaction: GVN + instcombine + DCE ---

    @Test
    fun gvnThenInstcombineThenDCE() {
        val module = build {
            val params = createFunction("f", listOf(Param("x", Type.I32), Param("y", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val a = add(params[0], params[1])
            val b = add(params[0], params[1]) // GVN replaces with a
            val c = add(a, b)
            val d = mul(c, Constant.I32(1)) // instcombine removes
            ret(d)
            finalizeFunction()
        }
        val result = PassPipeline()
            .add(GlobalValueNumbering())
            .add(InstructionCombining())
            .add(DeadCodeElimination())
            .execute(module)
        val insts = result.functions[0].blocks[0].instructions
        // GVN: b→a, instcombine: mul(c,1)→c, DCE: removes dead b
        // Result: add(x,y), add(result,result), ret
        assertEquals(3, insts.size, "Should have 3 instructions: $insts")
    }

    // --- Jump threading with multiple branches ---

    @Test
    fun jumpThreadingMultipleBranches() {
        val module = build {
            createFunction("f", emptyList(), Type.I32)
            positionAtEnd(appendBlock("entry"))
            br("b1")

            positionAtEnd(appendBlock("b1"))
            br("b2")

            positionAtEnd(appendBlock("b2"))
            ret(Constant.I32(42))

            positionAtEnd(appendBlock("dead1"))
            ret(Constant.I32(0))

            positionAtEnd(appendBlock("dead2"))
            ret(Constant.I32(-1))

            finalizeFunction()
        }
        val result = PassPipeline().add(JumpThreading()).execute(module)
        val blocks = result.functions[0].blocks
        assertFalse(blocks.any { it.label == "dead1" })
        assertFalse(blocks.any { it.label == "dead2" })
    }

    // --- Fold + jump threading: constant condition eliminates branch ---

    @Test
    fun foldAndJumpThreadingChain() {
        val module = build {
            createFunction("f", emptyList(), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val x = add(Constant.I32(10), Constant.I32(20))
            val cond = icmp(ICmpPredicate.SGT, x, Constant.I32(25))
            condBr(cond, "yes", "no")

            positionAtEnd(appendBlock("yes"))
            ret(Constant.I32(1))

            positionAtEnd(appendBlock("no"))
            ret(Constant.I32(0))

            finalizeFunction()
        }
        val result = PassPipeline()
            .add(ConstantFolding())
            .add(JumpThreading())
            .execute(module)
        val blocks = result.functions[0].blocks
        assertEquals(1, blocks.size)
        val ret = blocks[0].instructions.last() as Instruction.Ret
        assertEquals(1, (ret.value as Constant.I32).value, "10+20=30 > 25 → yes branch")
    }

    // --- O1 pipeline with identity ops ---

    @Test
    fun o1PipelineRemovesIdentityOps() {
        val module = build {
            val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val a = add(params[0], Constant.I32(0))
            val b = mul(a, Constant.I32(1))
            ret(b)
            finalizeFunction()
        }
        val result = OptLevel.O1.pipeline().execute(module)
        val insts = result.functions[0].blocks[0].instructions
        assertEquals(1, insts.size, "O1 should remove identity ops: $insts")
        assertTrue((insts[0] as Instruction.Ret).value is Parameter)
    }

    // --- O2 pipeline with complex function ---

    @Test
    fun o2PipelineComplexFunction() {
        val module = build {
            val params = createFunction("f", listOf(Param("x", Type.I32), Param("y", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val a = add(params[0], params[1])
            val b = add(params[0], params[1]) // GVN should deduplicate
            val c = add(a, b)
            val d = mul(c, Constant.I32(1)) // instcombine
            ret(d)
            finalizeFunction()
        }
        val result = OptLevel.O2.pipeline().execute(module)
        val insts = result.functions[0].blocks[0].instructions
        assertTrue(insts.size <= 3, "O2 should optimize: $insts")
    }

    // --- O0 preserves all ---

    @Test
    fun o0PreservesDeadCode() {
        val module = build {
            createFunction("f", emptyList(), Type.I32)
            positionAtEnd(appendBlock("entry"))
            add(Constant.I32(1), Constant.I32(2)) // dead
            add(Constant.I32(3), Constant.I32(4)) // dead
            ret(Constant.I32(0))
            finalizeFunction()
        }
        val result = OptLevel.O0.pipeline().execute(module)
        assertEquals(3, result.functions[0].blocks[0].instructions.size, "O0 should preserve dead code")
    }

    // --- Pipeline preserves function count ---

    @Test
    fun pipelinePreservesFunctionCount() {
        val module = build {
            for (i in 1..5) {
                createFunction("f$i", emptyList(), Type.I32)
                positionAtEnd(appendBlock("entry"))
                ret(add(Constant.I32(i), Constant.I32(i)))
                finalizeFunction()
            }
        }
        val result = PassPipeline()
            .add(ConstantFolding())
            .add(DeadCodeElimination())
            .execute(module)
        assertEquals(5, result.functions.size)
        for (i in 0..4) {
            val ret = result.functions[i].blocks[0].instructions.last() as Instruction.Ret
            assertEquals((i + 1) * 2, (ret.value as Constant.I32).value)
        }
    }

    // --- Pipeline with i64 ---

    @Test
    fun pipelineI64ConstantFolding() {
        val module = build {
            createFunction("f", emptyList(), Type.I64)
            positionAtEnd(appendBlock("entry"))
            val a = add(Constant.I64(1_000_000_000L), Constant.I64(2_000_000_000L))
            ret(a)
            finalizeFunction()
        }
        val result = PassPipeline().add(ConstantFolding()).execute(module)
        val ret = result.functions[0].blocks[0].instructions.last() as Instruction.Ret
        assertEquals(3_000_000_000L, (ret.value as Constant.I64).value)
    }

    // --- Pipeline with external declaration preserved ---

    @Test
    fun pipelinePreservesMultipleExternals() {
        val module = build {
            declareFunction("ext1", listOf(Param("x", Type.I32)), Type.I32)
            declareFunction("ext2", listOf(Param("x", Type.I64)), Type.I64)
            createFunction("f", emptyList(), Type.I32)
            positionAtEnd(appendBlock("entry"))
            ret(add(Constant.I32(1), Constant.I32(2)))
            finalizeFunction()
        }
        val result = PassPipeline()
            .add(ConstantFolding())
            .add(DeadCodeElimination())
            .execute(module)
        assertEquals(3, result.functions.size)
        assertTrue(result.functions[0].isExternal)
        assertTrue(result.functions[1].isExternal)
        val ret = result.functions[2].blocks[0].instructions.last() as Instruction.Ret
        assertEquals(3, (ret.value as Constant.I32).value)
    }

    // --- Pipeline with branching: fold + DCE ---

    @Test
    fun branchFoldAndDCE() {
        val module = build {
            createFunction("f", emptyList(), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val cond = icmp(ICmpPredicate.EQ, Constant.I32(5), Constant.I32(5))
            condBr(cond, "then", "else")

            positionAtEnd(appendBlock("then"))
            ret(Constant.I32(100))

            positionAtEnd(appendBlock("else"))
            ret(Constant.I32(200))

            finalizeFunction()
        }
        val result = PassPipeline()
            .add(ConstantFolding())
            .add(JumpThreading())
            .execute(module)
        val blocks = result.functions[0].blocks
        assertEquals(1, blocks.size)
        val ret = blocks[0].instructions.last() as Instruction.Ret
        assertEquals(100, (ret.value as Constant.I32).value, "5==5 is true → then branch")
    }

    @Test
    fun branchFoldFalseCondition() {
        val module = build {
            createFunction("f", emptyList(), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val cond = icmp(ICmpPredicate.SGT, Constant.I32(1), Constant.I32(10))
            condBr(cond, "then", "else")

            positionAtEnd(appendBlock("then"))
            ret(Constant.I32(100))

            positionAtEnd(appendBlock("else"))
            ret(Constant.I32(200))

            finalizeFunction()
        }
        val result = PassPipeline()
            .add(ConstantFolding())
            .add(JumpThreading())
            .execute(module)
        val blocks = result.functions[0].blocks
        assertEquals(1, blocks.size)
        val ret = blocks[0].instructions.last() as Instruction.Ret
        assertEquals(200, (ret.value as Constant.I32).value, "1>10 is false → else branch")
    }

    // --- Nested arithmetic all constant ---

    @Test
    fun deeplyNestedConstantFolding() {
        val module = build {
            createFunction("f", emptyList(), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val a = add(Constant.I32(1), Constant.I32(2))     // 3
            val b = mul(Constant.I32(3), Constant.I32(4))     // 12
            val c = add(a, b)                                   // 15
            val d = sub(c, Constant.I32(5))                     // 10
            ret(d)
            finalizeFunction()
        }
        val result = PassPipeline()
            .add(ConstantFolding())
            .add(DeadCodeElimination())
            .execute(module)
        val insts = result.functions[0].blocks[0].instructions
        assertEquals(1, insts.size)
        assertEquals(10, ((insts[0] as Instruction.Ret).value as Constant.I32).value)
    }

    // --- All dead code ---

    @Test
    fun allDeadCodeRemoved() {
        val module = build {
            createFunction("f", emptyList(), Type.Void)
            positionAtEnd(appendBlock("entry"))
            add(Constant.I32(1), Constant.I32(2))
            mul(Constant.I32(3), Constant.I32(4))
            sub(Constant.I32(5), Constant.I32(6))
            and(Constant.I32(7), Constant.I32(8))
            or(Constant.I32(9), Constant.I32(10))
            ret()
            finalizeFunction()
        }
        val result = PassPipeline().add(DeadCodeElimination()).execute(module)
        assertEquals(1, result.functions[0].blocks[0].instructions.size, "Only ret should remain")
    }

    // --- Inlining + fold + DCE full chain ---

    @Test
    fun inlineAndFoldChain() {
        val module = build {
            val addP = createFunction("add2", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            ret(add(addP[0], addP[1]))
            finalizeFunction()

            createFunction("main", emptyList(), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val r = call("add2", listOf(Constant.I32(20), Constant.I32(22)), Type.I32)!!
            ret(r)
            finalizeFunction()
        }
        val result = PassPipeline()
            .add(Inlining())
            .add(ConstantFolding())
            .add(DeadCodeElimination())
            .execute(module)
        val mainInsts = result.functions[1].blocks[0].instructions
        assertFalse(mainInsts.any { it is Instruction.Call })
    }

    // --- WASM target pipeline ---

    @Test
    fun wasmTargetPipeline() {
        val module = build(Target.wasm()) {
            createFunction("f", emptyList(), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val a = add(Constant.I32(10), Constant.I32(20))
            ret(a)
            finalizeFunction()
        }
        val result = PassPipeline()
            .add(ConstantFolding())
            .add(DeadCodeElimination())
            .execute(module)
        val ret = result.functions[0].blocks[0].instructions.last() as Instruction.Ret
        assertEquals(30, (ret.value as Constant.I32).value)
    }

    // --- Pipeline runs passes in correct order ---

    @Test
    fun passOrderMattersForOptimization() {
        val module = build {
            createFunction("f", emptyList(), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val a = add(Constant.I32(1), Constant.I32(2))
            mul(a, Constant.I32(0)) // dead after fold produces 3, but mul(3,0) = 0 which is also dead
            ret(a)
            finalizeFunction()
        }
        // Fold first, then DCE
        val r1 = PassPipeline().add(ConstantFolding()).add(DeadCodeElimination()).execute(module)
        assertEquals(1, r1.functions[0].blocks[0].instructions.size, "Fold+DCE: only ret")

        // DCE first, then fold
        val r2 = PassPipeline().add(DeadCodeElimination()).add(ConstantFolding()).execute(module)
        assertEquals(1, r2.functions[0].blocks[0].instructions.size, "DCE+Fold: only ret")
    }

    // --- Module name preserved through pipeline ---

    @Test
    fun moduleNamePreserved() {
        val ir = IrBuilder("my_module", Target.x86_64())
        ir.createFunction("f", emptyList(), Type.Void)
        ir.positionAtEnd(ir.appendBlock("entry"))
        ir.ret()
        ir.finalizeFunction()
        val module = ir.build()
        val result = PassPipeline()
            .add(ConstantFolding())
            .add(DeadCodeElimination())
            .execute(module)
        assertEquals("my_module", result.name)
    }

    // --- Idempotency of full pipeline ---

    @Test
    fun fullPipelineIdempotent() {
        val module = build {
            val params = createFunction("f", listOf(Param("x", Type.I32), Param("y", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val a = add(params[0], params[1])
            val b = add(params[0], params[1])
            val c = add(a, b)
            ret(c)
            finalizeFunction()
        }
        val pipeline = PassPipeline()
            .add(GlobalValueNumbering())
            .add(InstructionCombining())
            .add(ConstantFolding())
            .add(DeadCodeElimination())
        val once = pipeline.execute(module)
        val twice = pipeline.execute(once)
        assertEquals(
            once.functions[0].blocks[0].instructions.size,
            twice.functions[0].blocks[0].instructions.size,
            "Pipeline should be idempotent"
        )
    }
}
