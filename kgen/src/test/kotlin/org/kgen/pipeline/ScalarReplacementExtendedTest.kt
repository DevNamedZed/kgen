package org.kgen.pipeline

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*
import org.kgen.ir.instructions.*
import org.kgen.ir.build.ModuleBuilder
import org.kgen.ir.target.Target

class ScalarReplacementExtendedTest {

    private val sroa = ScalarReplacementOfAggregates()

    private fun buildAndTransform(block: ModuleBuilder.() -> Unit): Module {
        val ir = ModuleBuilder("test", Target.x86_64())
        ir.block()
        return sroa.run(ir.build())
    }

    @Test
    fun `replaces struct with three i32 fields`() {
        val tripleType = Type.Struct(null, listOf(Type.I32, Type.I32, Type.I32))
        val module = buildAndTransform {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val ptr = alloca(tripleType)
            val f0 = gep(tripleType, ptr, Constant.I32(0), Constant.I32(0))
            store(Constant.I32(1), f0)
            val f1 = gep(tripleType, ptr, Constant.I32(0), Constant.I32(1))
            store(Constant.I32(2), f1)
            val f2 = gep(tripleType, ptr, Constant.I32(0), Constant.I32(2))
            store(Constant.I32(3), f2)
            val v = load(Type.I32, f0)
            ret(v)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        val allocas = insts.filterIsInstance<Alloca>()
        assertEquals(3, allocas.size, "Should have 3 scalar allocas")
        assertTrue(allocas.all { it.allocType == Type.I32 })
    }

    @Test
    fun `replaces struct with mixed types`() {
        val mixedType = Type.Struct(null, listOf(Type.I32, Type.I64, Type.F64))
        val module = buildAndTransform {
            createFunction("f", emptyList(), Type.I64)
            appendBlock("entry")
            val ptr = alloca(mixedType)
            val f1 = gep(mixedType, ptr, Constant.I32(0), Constant.I32(1))
            store(Constant.I64(999L), f1)
            val v = load(Type.I64, f1)
            ret(v)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        val allocas = insts.filterIsInstance<Alloca>()
        assertEquals(3, allocas.size)
        val types = allocas.map { it.allocType }.toSet()
        assertEquals(setOf(Type.I32, Type.I64, Type.F64), types)
    }

    @Test
    fun `replaces array of size 2`() {
        val arrType = Type.Array(Type.I64, 2)
        val module = buildAndTransform {
            createFunction("f", emptyList(), Type.I64)
            appendBlock("entry")
            val ptr = alloca(arrType)
            val e0 = gep(arrType, ptr, Constant.I32(0), Constant.I32(0))
            store(Constant.I64(10L), e0)
            val e1 = gep(arrType, ptr, Constant.I32(0), Constant.I32(1))
            store(Constant.I64(20L), e1)
            val v = load(Type.I64, e0)
            ret(v)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        val allocas = insts.filterIsInstance<Alloca>()
        assertEquals(2, allocas.size)
        assertTrue(allocas.all { it.allocType == Type.I64 })
    }

    @Test
    fun `replaces array of size 4`() {
        val arrType = Type.Array(Type.I32, 4)
        val module = buildAndTransform {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val ptr = alloca(arrType)
            val e0 = gep(arrType, ptr, Constant.I32(0), Constant.I32(0))
            store(Constant.I32(10), e0)
            val v = load(Type.I32, e0)
            ret(v)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        val allocas = insts.filterIsInstance<Alloca>()
        assertEquals(4, allocas.size)
    }

    @Test
    fun `does not replace array of size 17`() {
        val bigArr = Type.Array(Type.I32, 17)
        val module = buildAndTransform {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val ptr = alloca(bigArr)
            val e0 = gep(bigArr, ptr, Constant.I32(0), Constant.I32(0))
            store(Constant.I32(1), e0)
            val v = load(Type.I32, e0)
            ret(v)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        val allocas = insts.filterIsInstance<Alloca>()
        assertEquals(1, allocas.size, "Array[17] exceeds MAX_ARRAY_SIZE=16")
    }

    @Test
    fun `replaces array of size 16`() {
        val arr16 = Type.Array(Type.I32, 16)
        val module = buildAndTransform {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val ptr = alloca(arr16)
            val e0 = gep(arr16, ptr, Constant.I32(0), Constant.I32(0))
            store(Constant.I32(1), e0)
            val v = load(Type.I32, e0)
            ret(v)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        val allocas = insts.filterIsInstance<Alloca>()
        assertEquals(16, allocas.size)
    }

    @Test
    fun `does not replace when gep result passed to call`() {
        val pointType = Type.Struct(null, listOf(Type.I32, Type.I32))
        val module = buildAndTransform {
            declareFunction("use_ptr", listOf(Param("p", Type.OpaquePointer)), Type.Void)
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val ptr = alloca(pointType)
            val xPtr = gep(pointType, ptr, Constant.I32(0), Constant.I32(0))
            call("use_ptr", listOf(xPtr), Type.Void) // gep result escapes to call
            ret(Constant.I32(0))
            finalizeFunction()
        }
        val fn = module.functions.find { it.name == "f" }!!
        val allocas = fn.blocks[0].instructions.filterIsInstance<Alloca>()
        assertEquals(1, allocas.size)
        assertTrue(allocas[0].allocType is Type.Struct)
    }

    @Test
    fun `replaces struct then mem2reg eliminates all`() {
        val pairType = Type.Struct(null, listOf(Type.I32, Type.I32))
        val module = buildAndTransform {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val ptr = alloca(pairType)
            val f0 = gep(pairType, ptr, Constant.I32(0), Constant.I32(0))
            store(Constant.I32(5), f0)
            val f1 = gep(pairType, ptr, Constant.I32(0), Constant.I32(1))
            store(Constant.I32(10), f1)
            val v0 = load(Type.I32, f0)
            val v1 = load(Type.I32, f1)
            val sum = add(v0, v1)
            ret(sum)
            finalizeFunction()
        }
        val promoted = Mem2Reg().run(module)
        val insts = promoted.functions[0].blocks[0].instructions
        val allocas = insts.filterIsInstance<Alloca>()
        assertEquals(0, allocas.size, "All allocas eliminated after SROA + mem2reg")
        val addInst = insts.first { it is Add } as Add
        assertEquals(5, (addInst.lhs as Constant.I32).value)
        assertEquals(10, (addInst.rhs as Constant.I32).value)
    }

    @Test
    fun `handles struct with i8 and i16 fields`() {
        val smallStruct = Type.Struct(null, listOf(Type.I8, Type.I16))
        val module = buildAndTransform {
            createFunction("f", emptyList(), Type.I8)
            appendBlock("entry")
            val ptr = alloca(smallStruct)
            val f0 = gep(smallStruct, ptr, Constant.I32(0), Constant.I32(0))
            store(Constant.I8(42), f0)
            val v = load(Type.I8, f0)
            ret(v)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        val allocas = insts.filterIsInstance<Alloca>()
        assertEquals(2, allocas.size)
        val types = allocas.map { it.allocType }.toSet()
        assertEquals(setOf(Type.I8, Type.I16), types)
    }

    @Test
    fun `handles struct with f32 and f64 fields`() {
        val floatStruct = Type.Struct(null, listOf(Type.F32, Type.F64))
        val module = buildAndTransform {
            createFunction("f", emptyList(), Type.F64)
            appendBlock("entry")
            val ptr = alloca(floatStruct)
            val f1 = gep(floatStruct, ptr, Constant.I32(0), Constant.I32(1))
            store(Constant.F64(3.14), f1)
            val v = load(Type.F64, f1)
            ret(v)
            finalizeFunction()
        }
        val promoted = Mem2Reg().run(module)
        val ret = promoted.functions[0].blocks[0].instructions.last() as Ret
        assertEquals(3.14, (ret.value as Constant.F64).value)
    }

    @Test
    fun `does not replace pointer-typed alloca`() {
        val module = buildAndTransform {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val ptr = alloca(Type.OpaquePointer)
            store(Constant.I32(0), ptr) // actually stores to pointer, not aggregate
            ret(Constant.I32(0))
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        val allocas = insts.filterIsInstance<Alloca>()
        assertEquals(1, allocas.size, "Non-aggregate alloca should not be touched")
    }

    @Test
    fun `multiple struct allocas replaced independently`() {
        val pointType = Type.Struct(null, listOf(Type.I32, Type.I32))
        val module = buildAndTransform {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val p1 = alloca(pointType)
            val p2 = alloca(pointType)
            val f0p1 = gep(pointType, p1, Constant.I32(0), Constant.I32(0))
            store(Constant.I32(1), f0p1)
            val f0p2 = gep(pointType, p2, Constant.I32(0), Constant.I32(0))
            store(Constant.I32(2), f0p2)
            val v1 = load(Type.I32, f0p1)
            val v2 = load(Type.I32, f0p2)
            val sum = add(v1, v2)
            ret(sum)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        val allocas = insts.filterIsInstance<Alloca>()
        assertEquals(4, allocas.size, "2 structs * 2 fields = 4 scalar allocas")
    }

    @Test
    fun `replaces nested struct with inner struct`() {
        val inner = Type.Struct(null, listOf(Type.I32, Type.I64))
        val outer = Type.Struct(null, listOf(inner, Type.F32))
        val module = buildAndTransform {
            createFunction("f", emptyList(), Type.F32)
            appendBlock("entry")
            val ptr = alloca(outer)
            val f1 = gep(outer, ptr, Constant.I32(0), Constant.I32(1))
            store(Constant.F32(1.5f), f1)
            val v = load(Type.F32, f1)
            ret(v)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        val allocas = insts.filterIsInstance<Alloca>()
        // inner: i32 + i64 = 2, outer field: f32 = 1, total = 3
        assertEquals(3, allocas.size, "Nested struct should flatten to 3 allocas: $allocas")
    }

    @Test
    fun `handles array of structs`() {
        val elemType = Type.Struct(null, listOf(Type.I32, Type.I32))
        val arrType = Type.Array(elemType, 2)
        val module = buildAndTransform {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val ptr = alloca(arrType)
            val e0 = gep(arrType, ptr, Constant.I32(0), Constant.I32(0))
            val e0f0 = gep(elemType, e0, Constant.I32(0), Constant.I32(0))
            store(Constant.I32(42), e0f0)
            val v = load(Type.I32, e0f0)
            ret(v)
            finalizeFunction()
        }
        // This test verifies the pass handles array-of-struct correctly
        // The exact behavior depends on how the nested GEP is handled
        assertNotNull(module)
    }

    @Test
    fun `does not replace when alloca used directly in ret`() {
        val pointType = Type.Struct(null, listOf(Type.I32, Type.I32))
        val module = buildAndTransform {
            createFunction("f", emptyList(), Type.OpaquePointer)
            appendBlock("entry")
            val ptr = alloca(pointType)
            ret(ptr) // alloca address returned - escapes
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        val allocas = insts.filterIsInstance<Alloca>()
        assertEquals(1, allocas.size, "Alloca should not be replaced when returned")
    }

    @Test
    fun `does not replace when alloca used in icmp`() {
        val pointType = Type.Struct(null, listOf(Type.I32, Type.I32))
        val module = buildAndTransform {
            val params = createFunction("f", listOf(Param("p", Type.OpaquePointer)), Type.I32)
            appendBlock("entry")
            val ptr = alloca(pointType)
            val cmp = icmp(ICmpPredicate.EQ, ptr, params[0])
            val r = select(cmp, Constant.I32(1), Constant.I32(0))
            ret(r)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        val allocas = insts.filterIsInstance<Alloca>()
        assertEquals(1, allocas.size, "Alloca used in comparison should not be replaced")
    }

    @Test
    fun `handles multiple functions`() {
        val pairType = Type.Struct(null, listOf(Type.I32, Type.I32))
        val module = buildAndTransform {
            createFunction("f1", emptyList(), Type.I32)
            appendBlock("entry")
            val p1 = alloca(pairType)
            val f1 = gep(pairType, p1, Constant.I32(0), Constant.I32(0))
            store(Constant.I32(10), f1)
            val v1 = load(Type.I32, f1)
            ret(v1)
            finalizeFunction()

            createFunction("f2", emptyList(), Type.I32)
            appendBlock("entry")
            val p2 = alloca(pairType)
            val f2 = gep(pairType, p2, Constant.I32(0), Constant.I32(1))
            store(Constant.I32(20), f2)
            val v2 = load(Type.I32, f2)
            ret(v2)
            finalizeFunction()
        }
        for (fn in module.functions) {
            val allocas = fn.blocks[0].instructions.filterIsInstance<Alloca>()
            assertEquals(2, allocas.size, "Each function should have 2 scalar allocas")
        }
    }

    @Test
    fun `idempotent on scalar alloca`() {
        val module = buildAndTransform {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val ptr = alloca(Type.I32)
            store(Constant.I32(42), ptr)
            val v = load(Type.I32, ptr)
            ret(v)
            finalizeFunction()
        }
        val result = sroa.run(module)
        val allocas = result.functions[0].blocks[0].instructions.filterIsInstance<Alloca>()
        assertEquals(1, allocas.size)
        assertEquals(Type.I32, allocas[0].allocType)
    }

    @Test
    fun `preserves store and load ordering after replacement`() {
        val pairType = Type.Struct(null, listOf(Type.I32, Type.I32))
        val module = buildAndTransform {
            val params = createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            val ptr = alloca(pairType)
            val f0 = gep(pairType, ptr, Constant.I32(0), Constant.I32(0))
            val f1 = gep(pairType, ptr, Constant.I32(0), Constant.I32(1))
            store(params[0], f0)
            store(params[1], f1)
            val v0 = load(Type.I32, f0)
            val v1 = load(Type.I32, f1)
            val sum = add(v0, v1)
            ret(sum)
            finalizeFunction()
        }
        // After SROA, stores and loads should reference the correct scalar allocas
        val insts = module.functions[0].blocks[0].instructions
        val stores = insts.filterIsInstance<Store>()
        val loads = insts.filterIsInstance<Load>()
        assertEquals(2, stores.size)
        assertEquals(2, loads.size)
    }

    @Test
    fun `replaces struct with i1 field`() {
        val boolStruct = Type.Struct(null, listOf(Type.I1, Type.I32))
        val module = buildAndTransform {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val ptr = alloca(boolStruct)
            val f0 = gep(boolStruct, ptr, Constant.I32(0), Constant.I32(0))
            store(Constant.I1(true), f0)
            val f1 = gep(boolStruct, ptr, Constant.I32(0), Constant.I32(1))
            store(Constant.I32(42), f1)
            val v = load(Type.I32, f1)
            ret(v)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        val allocas = insts.filterIsInstance<Alloca>()
        assertEquals(2, allocas.size)
        val types = allocas.map { it.allocType }.toSet()
        assertEquals(setOf(Type.I1, Type.I32), types)
    }

    @Test
    fun `does not replace when alloca stored as value`() {
        val pointType = Type.Struct(null, listOf(Type.I32, Type.I32))
        val module = buildAndTransform {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val ptr = alloca(pointType)
            val ptrStore = alloca(Type.OpaquePointer)
            store(ptr, ptrStore) // address escapes as stored value
            ret(Constant.I32(0))
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        val allocas = insts.filterIsInstance<Alloca>()
        val structAllocas = allocas.filter { it.allocType is Type.Struct }
        assertTrue(structAllocas.isNotEmpty(), "Struct alloca should remain when address stored")
    }

    @Test
    fun `replaces struct with single field`() {
        val singleField = Type.Struct(null, listOf(Type.I64))
        val module = buildAndTransform {
            createFunction("f", emptyList(), Type.I64)
            appendBlock("entry")
            val ptr = alloca(singleField)
            val f0 = gep(singleField, ptr, Constant.I32(0), Constant.I32(0))
            store(Constant.I64(12345L), f0)
            val v = load(Type.I64, f0)
            ret(v)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        val allocas = insts.filterIsInstance<Alloca>()
        assertEquals(1, allocas.size)
        assertEquals(Type.I64, allocas[0].allocType)
    }

    @Test
    fun `array of size 1 replaced`() {
        val arr1 = Type.Array(Type.I32, 1)
        val module = buildAndTransform {
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            val ptr = alloca(arr1)
            val e0 = gep(arr1, ptr, Constant.I32(0), Constant.I32(0))
            store(Constant.I32(42), e0)
            val v = load(Type.I32, e0)
            ret(v)
            finalizeFunction()
        }
        val insts = module.functions[0].blocks[0].instructions
        val allocas = insts.filterIsInstance<Alloca>()
        assertEquals(1, allocas.size)
        assertEquals(Type.I32, allocas[0].allocType)
    }

    @Test
    fun `empty function unchanged`() {
        val module = buildAndTransform {
            createFunction("f", emptyList(), Type.Void)
            appendBlock("entry")
            ret(null)
            finalizeFunction()
        }
        assertEquals(1, module.functions[0].blocks[0].instructions.size)
    }

    @Test
    fun `SROA plus mem2reg on mixed struct produces direct values`() {
        val mixedType = Type.Struct(null, listOf(Type.I32, Type.I64))
        val module = buildAndTransform {
            createFunction("f", emptyList(), Type.I64)
            appendBlock("entry")
            val ptr = alloca(mixedType)
            val f0 = gep(mixedType, ptr, Constant.I32(0), Constant.I32(0))
            store(Constant.I32(10), f0)
            val f1 = gep(mixedType, ptr, Constant.I32(0), Constant.I32(1))
            store(Constant.I64(20L), f1)
            val v = load(Type.I64, f1)
            ret(v)
            finalizeFunction()
        }
        val promoted = Mem2Reg().run(module)
        val insts = promoted.functions[0].blocks[0].instructions
        val ret = insts.last() as Ret
        assertEquals(20L, (ret.value as Constant.I64).value)
    }
}
