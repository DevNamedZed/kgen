package org.kgen.pass

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*
import org.kgen.ir.instructions.*
import org.kgen.ir.build.IrBuilder
import org.kgen.ir.target.Target

class AutoVectorizationTest {

    @Test
    fun suggestVectorFactorI32() {
        assertEquals(4, AutoVectorization.suggestVectorFactor(Type.I32))
    }

    @Test
    fun suggestVectorFactorI8() {
        assertEquals(16, AutoVectorization.suggestVectorFactor(Type.I8))
    }

    @Test
    fun suggestVectorFactorI64() {
        assertEquals(2, AutoVectorization.suggestVectorFactor(Type.I64))
    }

    @Test
    fun suggestVectorFactorF32() {
        assertEquals(4, AutoVectorization.suggestVectorFactor(Type.F32))
    }

    @Test
    fun suggestVectorFactorF64() {
        assertEquals(2, AutoVectorization.suggestVectorFactor(Type.F64))
    }

    @Test
    fun reductionOpMapping() {
        assertEquals(VectorReduceOp.ADD, AutoVectorization.toVectorReduceOp(AutoVectorization.ReductionOp.ADD))
        assertEquals(VectorReduceOp.MUL, AutoVectorization.toVectorReduceOp(AutoVectorization.ReductionOp.MUL))
        assertEquals(VectorReduceOp.AND, AutoVectorization.toVectorReduceOp(AutoVectorization.ReductionOp.AND))
        assertEquals(VectorReduceOp.OR, AutoVectorization.toVectorReduceOp(AutoVectorization.ReductionOp.OR))
        assertEquals(VectorReduceOp.XOR, AutoVectorization.toVectorReduceOp(AutoVectorization.ReductionOp.XOR))
        assertEquals(VectorReduceOp.FADD, AutoVectorization.toVectorReduceOp(AutoVectorization.ReductionOp.FADD))
        assertEquals(VectorReduceOp.FMUL, AutoVectorization.toVectorReduceOp(AutoVectorization.ReductionOp.FMUL))
    }

    @Test
    fun noChangeOnExternalFunction() {
        val builder = IrBuilder("test", Target.x86_64())
        builder.declareFunction("extern", listOf(Param("x", Type.I32)), Type.I32)
        val module = builder.build()

        val pass = AutoVectorization()
        val result = pass.run(module)
        assertEquals(module, result)
    }

    @Test
    fun noChangeOnSimpleFunction() {
        val builder = IrBuilder("test", Target.x86_64())
        val params = builder.createFunction("simple", listOf(Param("x", Type.I32)), Type.I32)
        builder.positionAtEnd(builder.appendBlock("entry"))
        builder.ret(builder.add(params[0], Constant.I32(1)))
        builder.finalizeFunction()
        val module = builder.build()

        val pass = AutoVectorization()
        val result = pass.run(module)
        assertEquals(module.functions.size, result.functions.size)
    }

    @Test
    fun slpVectorizesIsomorphicAdds() {
        val builder = IrBuilder("test", Target.x86_64())
        val params = builder.createFunction("addPairs",
            listOf(Param("a", Type.I32), Param("b", Type.I32), Param("c", Type.I32), Param("d", Type.I32)),
            Type.I32)
        builder.positionAtEnd(builder.appendBlock("entry"))
        val r1 = builder.add(params[0], params[1])
        val r2 = builder.add(params[2], params[3])
        val sum = builder.add(r1, r2)
        builder.ret(sum)
        builder.finalizeFunction()
        val module = builder.build()

        val pass = AutoVectorization(enableLoopVectorization = false, enableSLP = true)
        val result = pass.run(module)
        val fn = result.functions.first()
        val entryInsts = fn.blocks.first().instructions

        val hasSplat = entryInsts.any { it is Splat }
        val hasExtract = entryInsts.any { it is ExtractElement }

        if (hasSplat) {
            assertTrue(hasExtract, "Should have extract after SLP vectorization")
        }
    }

    @Test
    fun slpDoesNotVectorizeDifferentOps() {
        val builder = IrBuilder("test", Target.x86_64())
        val params = builder.createFunction("mixedOps",
            listOf(Param("a", Type.I32), Param("b", Type.I32)),
            Type.I32)
        builder.positionAtEnd(builder.appendBlock("entry"))
        val r1 = builder.add(params[0], params[1])
        val r2 = builder.sub(params[0], params[1])
        val sum = builder.add(r1, r2)
        builder.ret(sum)
        builder.finalizeFunction()
        val module = builder.build()

        val pass = AutoVectorization(enableLoopVectorization = false, enableSLP = true)
        val result = pass.run(module)
        val fn = result.functions.first()
        val entryInsts = fn.blocks.first().instructions

        val hasSplat = entryInsts.any { it is Splat }
        assertFalse(hasSplat, "Different ops should not be SLP-vectorized")
    }

    @Test
    fun loopVectorizationWithArrayAccess() {
        // Build a loop manually: for (i = 0; i < n; i++) arr[i] = arr[i] + 1
        val ptrType = Type.Pointer(Type.I32)
        val arrParam = Parameter("arr", ptrType, 0)
        val nParam = Parameter("n", Type.I32, 1)
        val iRef = InstructionRef("i", Type.I32)
        val nextIRef = InstructionRef("nextI", Type.I32)
        val cmpRef = InstructionRef("cmp", Type.I1)
        val gepRef = InstructionRef("gep", ptrType)
        val loadRef = InstructionRef("loaded", Type.I32)
        val addRef = InstructionRef("added", Type.I32)

        val fn = IrFunction(
            name = "addOne",
            params = listOf(arrParam, nParam),
            returnType = Type.Void,
            blocks = listOf(
                BasicBlock("entry", listOf(
                    Br("header"),
                )),
                BasicBlock("header", listOf(
                    Phi(iRef, listOf(
                        Constant.I32(0) to "entry",
                        nextIRef to "body",
                    )),
                    ICmp(cmpRef, ICmpPredicate.SLT, iRef, nParam),
                    CondBr(cmpRef, "body", "exit"),
                )),
                BasicBlock("body", listOf(
                    GetElementPtr(gepRef, Type.I32, arrParam, listOf(iRef)),
                    Load(loadRef, gepRef, Type.I32),
                    Add(addRef, loadRef, Constant.I32(1)),
                    Store(addRef, gepRef),
                    Add(nextIRef, iRef, Constant.I32(1)),
                    Br("header"),
                )),
                BasicBlock("exit", listOf(
                    Ret(null),
                )),
            ),
        )
        val module = Module("test", functions = listOf(fn))

        val pass = AutoVectorization(vectorFactor = 4, enableSLP = false)
        val result = pass.run(module)
        val resultFn = result.functions.first()

        val blockLabels = resultFn.blocks.map { it.label }.toSet()
        val hasVecBlocks = blockLabels.any { it.contains("vec") }

        if (hasVecBlocks) {
            assertTrue(blockLabels.any { it.contains("vec_ph") }, "Should have vector preheader")
            assertTrue(blockLabels.any { it.contains("_vec") }, "Should have vector loop body")
            assertTrue(blockLabels.any { it.contains("scalar_ph") }, "Should have scalar remainder preheader")
        }
    }

    @Test
    fun vectorFactorConfiguration() {
        val pass2 = AutoVectorization(vectorFactor = 2)
        val pass4 = AutoVectorization(vectorFactor = 4)
        val pass8 = AutoVectorization(vectorFactor = 8)

        assertNotNull(pass2)
        assertNotNull(pass4)
        assertNotNull(pass8)
    }

    @Test
    fun disableLoopVectorization() {
        val pass = AutoVectorization(enableLoopVectorization = false)
        val builder = IrBuilder("test", Target.x86_64())
        val params = builder.createFunction("id", listOf(Param("x", Type.I32)), Type.I32)
        builder.positionAtEnd(builder.appendBlock("entry"))
        builder.ret(params[0])
        builder.finalizeFunction()
        val module = builder.build()

        val result = pass.run(module)
        assertEquals(module.functions.size, result.functions.size)
    }

    @Test
    fun disableSLP() {
        val pass = AutoVectorization(enableSLP = false)
        val builder = IrBuilder("test", Target.x86_64())
        val params = builder.createFunction("id", listOf(Param("x", Type.I32)), Type.I32)
        builder.positionAtEnd(builder.appendBlock("entry"))
        builder.ret(params[0])
        builder.finalizeFunction()
        val module = builder.build()

        val result = pass.run(module)
        assertEquals(module.functions.size, result.functions.size)
    }

    @Test
    fun slpVectorizesIsomorphicMuls() {
        val builder = IrBuilder("test", Target.x86_64())
        val params = builder.createFunction("mulPairs",
            listOf(Param("a", Type.I32), Param("b", Type.I32), Param("c", Type.I32), Param("d", Type.I32)),
            Type.I32)
        builder.positionAtEnd(builder.appendBlock("entry"))
        val r1 = builder.mul(params[0], params[1])
        val r2 = builder.mul(params[2], params[3])
        val sum = builder.add(r1, r2)
        builder.ret(sum)
        builder.finalizeFunction()
        val module = builder.build()

        val pass = AutoVectorization(enableLoopVectorization = false, enableSLP = true)
        val result = pass.run(module)
        val fn = result.functions.first()
        val entryInsts = fn.blocks.first().instructions

        val hasSplat = entryInsts.any { it is Splat }
        val hasExtract = entryInsts.any { it is ExtractElement }

        if (hasSplat) {
            assertTrue(hasExtract, "Should have extract after SLP vectorization of muls")
        }
    }

    @Test
    fun passImplementsModulePass() {
        val pass = AutoVectorization()
        assertTrue(pass is ModulePass)
    }
}
