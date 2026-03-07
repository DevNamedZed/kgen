package org.kgen.pass

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*
import org.kgen.ir.build.IrBuilder
import org.kgen.ir.target.Target

class ScalarReplacementOfAggregatesTest {

    private val sroa = ScalarReplacementOfAggregates()

    private fun buildAndTransform(block: IrBuilder.() -> Unit): Module {
        val ir = IrBuilder("test", Target.x86_64())
        ir.block()
        return sroa.run(ir.build())
    }

    @Test
    fun `replaces struct alloca with scalar allocas`() {
        val pointType = Type.Struct(null, listOf(Type.I32, Type.I32))
        val module = buildAndTransform {
            createFunction("f", emptyList(), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val ptr = alloca(pointType)
            val xPtr = gep(pointType, ptr, Constant.I32(0), Constant.I32(0))
            store(Constant.I32(10), xPtr)
            val yPtr = gep(pointType, ptr, Constant.I32(0), Constant.I32(1))
            store(Constant.I32(20), yPtr)
            val x = load(Type.I32, xPtr)
            ret(x)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        // Original struct alloca should be gone, replaced by scalar allocas
        val allocas = insts.filterIsInstance<Instruction.Alloca>()
        assertTrue(allocas.all { !isAggregate(it.allocType) }, "All allocas should be scalar: $allocas")
        // Should have 2 scalar allocas (one per field)
        assertEquals(2, allocas.size, "Should have 2 scalar allocas: $allocas")
    }

    @Test
    fun `preserves correct load after replacement`() {
        val pointType = Type.Struct(null, listOf(Type.I32, Type.I32))
        val module = buildAndTransform {
            createFunction("f", emptyList(), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val ptr = alloca(pointType)
            val xPtr = gep(pointType, ptr, Constant.I32(0), Constant.I32(0))
            store(Constant.I32(42), xPtr)
            val x = load(Type.I32, xPtr)
            ret(x)
            finalizeFunction()
        }
        // Run mem2reg on the result to promote scalar allocas
        val promoted = Mem2Reg().run(module)
        val insts = promoted.functions[0].blocks[0].instructions
        // Should just be ret 42
        val ret = insts.last() as Instruction.Ret
        assertEquals(42, (ret.value as Constant.I32).value)
    }

    @Test
    fun `replaces array alloca with scalar allocas`() {
        val arrType = Type.Array(Type.I32, 3)
        val module = buildAndTransform {
            createFunction("f", emptyList(), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val ptr = alloca(arrType)
            val e0 = gep(arrType, ptr, Constant.I32(0), Constant.I32(0))
            store(Constant.I32(100), e0)
            val e1 = gep(arrType, ptr, Constant.I32(0), Constant.I32(1))
            store(Constant.I32(200), e1)
            val v = load(Type.I32, e0)
            ret(v)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        val allocas = insts.filterIsInstance<Instruction.Alloca>()
        assertEquals(3, allocas.size, "Should have 3 scalar allocas for array[3]: $allocas")
        assertTrue(allocas.all { it.allocType == Type.I32 })
    }

    @Test
    fun `does not replace alloca with escaping address`() {
        val pointType = Type.Struct(null, listOf(Type.I32, Type.I32))
        val module = buildAndTransform {
            declareFunction("use_ptr", listOf(Param("p", Type.OpaquePointer)), Type.Void)
            createFunction("f", emptyList(), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val ptr = alloca(pointType)
            // Pass the alloca pointer to a call — address escapes
            call("use_ptr", listOf(ptr), Type.Void)
            ret(Constant.I32(0))
            finalizeFunction()
        }
        val fn = module.functions.first { it.name == "f" }
        val insts = fn.blocks[0].instructions
        val allocas = insts.filterIsInstance<Instruction.Alloca>()
        assertEquals(1, allocas.size, "Original alloca should remain")
        assertTrue(isAggregate(allocas[0].allocType), "Should still be aggregate")
    }

    @Test
    fun `does not replace alloca with non-constant GEP index`() {
        val arrType = Type.Array(Type.I32, 4)
        val module = buildAndTransform {
            val params = createFunction("f", listOf(Param("idx", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val ptr = alloca(arrType)
            // Dynamic index — can't do SROA
            val ePtr = gep(arrType, ptr, Constant.I32(0), params[0])
            store(Constant.I32(5), ePtr)
            val v = load(Type.I32, ePtr)
            ret(v)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        val allocas = insts.filterIsInstance<Instruction.Alloca>()
        assertEquals(1, allocas.size)
        assertTrue(isAggregate(allocas[0].allocType))
    }

    @Test
    fun `does not replace scalar alloca`() {
        val module = buildAndTransform {
            createFunction("f", emptyList(), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val ptr = alloca(Type.I32)
            store(Constant.I32(7), ptr)
            val v = load(Type.I32, ptr)
            ret(v)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        val allocas = insts.filterIsInstance<Instruction.Alloca>()
        assertEquals(1, allocas.size)
        assertEquals(Type.I32, allocas[0].allocType)
    }

    @Test
    fun `replaces nested struct alloca`() {
        val innerType = Type.Struct(null, listOf(Type.I32, Type.I32))
        val outerType = Type.Struct(null, listOf(innerType, Type.I64))
        val module = buildAndTransform {
            createFunction("f", emptyList(), Type.I64)
            positionAtEnd(appendBlock("entry"))
            val ptr = alloca(outerType)
            // Access outer.field1 (the i64)
            val f1Ptr = gep(outerType, ptr, Constant.I32(0), Constant.I32(1))
            store(Constant.I64(999), f1Ptr)
            val v = load(Type.I64, f1Ptr)
            ret(v)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        val allocas = insts.filterIsInstance<Instruction.Alloca>()
        // inner struct has 2 fields + 1 i64 = 3 scalar allocas
        assertEquals(3, allocas.size, "Should flatten nested struct: $allocas")
    }

    @Test
    fun `SROA then mem2reg eliminates struct completely`() {
        val pointType = Type.Struct(null, listOf(Type.I32, Type.I32))
        val module = buildAndTransform {
            val params = createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val ptr = alloca(pointType)
            val xPtr = gep(pointType, ptr, Constant.I32(0), Constant.I32(0))
            store(params[0], xPtr)
            val yPtr = gep(pointType, ptr, Constant.I32(0), Constant.I32(1))
            store(params[1], yPtr)
            val x = load(Type.I32, xPtr)
            val y = load(Type.I32, yPtr)
            val sum = add(x, y)
            ret(sum)
            finalizeFunction()
        }
        val promoted = Mem2Reg().run(module)
        val insts = promoted.functions[0].blocks[0].instructions
        // No allocas should remain after SROA + mem2reg
        val allocas = insts.filterIsInstance<Instruction.Alloca>()
        assertEquals(0, allocas.size, "All allocas should be eliminated: $insts")
        // Should have add + ret
        assertTrue(insts.any { it is Instruction.Add })
        assertTrue(insts.any { it is Instruction.Ret })
    }

    @Test
    fun `skips external functions`() {
        val module = buildAndTransform {
            declareFunction("ext", listOf(Param("x", Type.I32)), Type.I32)
        }
        assertEquals(1, module.functions.size)
        assertTrue(module.functions[0].isExternal)
    }

    @Test
    fun `does not replace large array`() {
        val bigArray = Type.Array(Type.I32, 100)
        val module = buildAndTransform {
            createFunction("f", emptyList(), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val ptr = alloca(bigArray)
            val e0 = gep(bigArray, ptr, Constant.I32(0), Constant.I32(0))
            store(Constant.I32(1), e0)
            val v = load(Type.I32, e0)
            ret(v)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        val allocas = insts.filterIsInstance<Instruction.Alloca>()
        assertEquals(1, allocas.size, "Large array should not be replaced")
        assertTrue(isAggregate(allocas[0].allocType))
    }

    private fun isAggregate(type: Type) = type is Type.Struct || type is Type.Array
}
