package org.kgen.pass

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*
import org.kgen.ir.instructions.*
import org.kgen.ir.build.IrBuilder
import org.kgen.ir.target.Target

class AliasAnalysisTest {

    private fun buildFunction(block: IrBuilder.() -> Unit): IrFunction {
        val ir = IrBuilder("test", Target.x86_64())
        ir.block()
        return ir.build().functions[0]
    }

    // --- BasicAliasAnalysis: identity ---

    @Test
    fun `same value is MustAlias`() {
        val fn = buildFunction {
            createFunction("f", listOf(Param("ptr", Type.OpaquePointer)), Type.Void)
            appendBlock("entry")
            ret()
            finalizeFunction()
        }
        val aa = BasicAliasAnalysis(fn)
        val ptr = fn.params[0]
        assertEquals(AliasResult.MustAlias, aa.alias(ptr, ptr))
    }

    // --- BasicAliasAnalysis: distinct allocas ---

    @Test
    fun `distinct allocas are NoAlias`() {
        val fn = buildFunction {
            createFunction("f", emptyList(), Type.Void)
            appendBlock("entry")
            val a = alloca(Type.I32)
            val b = alloca(Type.I32)
            store(Constant.I32(1), a)
            store(Constant.I32(2), b)
            ret()
            finalizeFunction()
        }
        val aa = BasicAliasAnalysis(fn)
        val allocaA = fn.blocks[0].instructions[0].result!!
        val allocaB = fn.blocks[0].instructions[1].result!!
        assertEquals(AliasResult.NoAlias, aa.alias(allocaA, allocaB))
    }

    // --- BasicAliasAnalysis: global vs alloca ---

    @Test
    fun `global vs alloca is NoAlias`() {
        val fn = buildFunction {
            addGlobal("g", Type.I32, Constant.I32(0))
            createFunction("f", emptyList(), Type.Void)
            appendBlock("entry")
            val a = alloca(Type.I32)
            store(Constant.I32(1), a)
            ret()
            finalizeFunction()
        }
        val aa = BasicAliasAnalysis(fn)
        val globalRef = GlobalRef("g", Type.Pointer(Type.I32))
        val allocaResult = fn.blocks[0].instructions[0].result!!
        assertEquals(AliasResult.NoAlias, aa.alias(globalRef, allocaResult))
    }

    // --- BasicAliasAnalysis: distinct globals ---

    @Test
    fun `distinct globals are NoAlias`() {
        val fn = buildFunction {
            addGlobal("g1", Type.I32, Constant.I32(0))
            addGlobal("g2", Type.I32, Constant.I32(0))
            createFunction("f", emptyList(), Type.Void)
            appendBlock("entry")
            ret()
            finalizeFunction()
        }
        val aa = BasicAliasAnalysis(fn)
        val g1 = GlobalRef("g1", Type.Pointer(Type.I32))
        val g2 = GlobalRef("g2", Type.Pointer(Type.I32))
        assertEquals(AliasResult.NoAlias, aa.alias(g1, g2))
    }

    // --- BasicAliasAnalysis: parameter vs alloca ---

    @Test
    fun `parameter vs alloca is NoAlias`() {
        val fn = buildFunction {
            createFunction("f", listOf(Param("ptr", Type.OpaquePointer)), Type.Void)
            appendBlock("entry")
            val a = alloca(Type.I32)
            store(Constant.I32(1), a)
            ret()
            finalizeFunction()
        }
        val aa = BasicAliasAnalysis(fn)
        val param = fn.params[0]
        val allocaResult = fn.blocks[0].instructions[0].result!!
        assertEquals(AliasResult.NoAlias, aa.alias(param, allocaResult))
    }

    // --- BasicAliasAnalysis: null pointer ---

    @Test
    fun `null pointer vs alloca is NoAlias`() {
        val fn = buildFunction {
            createFunction("f", emptyList(), Type.Void)
            appendBlock("entry")
            val a = alloca(Type.I32)
            store(Constant.I32(1), a)
            ret()
            finalizeFunction()
        }
        val aa = BasicAliasAnalysis(fn)
        val allocaResult = fn.blocks[0].instructions[0].result!!
        assertEquals(AliasResult.NoAlias, aa.alias(Constant.NullPtr, allocaResult))
    }

    // --- BasicAliasAnalysis: GEP with different constant indices ---

    @Test
    fun `GEPs with different constant indices are NoAlias`() {
        val fn = buildFunction {
            val structType = Type.Struct(null, listOf(Type.I32, Type.I64))
            createFunction("f", emptyList(), Type.Void)
            appendBlock("entry")
            val s = alloca(structType)
            val f0 = gep(structType, s, Constant.I32(0), Constant.I32(0))
            val f1 = gep(structType, s, Constant.I32(0), Constant.I32(1))
            store(Constant.I32(42), f0)
            store(Constant.I64(99), f1)
            ret()
            finalizeFunction()
        }
        val aa = BasicAliasAnalysis(fn)
        val gep0 = fn.blocks[0].instructions[1].result!!
        val gep1 = fn.blocks[0].instructions[2].result!!
        assertEquals(AliasResult.NoAlias, aa.alias(gep0, gep1))
    }

    // --- BasicAliasAnalysis: GEPs with same indices are MustAlias ---

    @Test
    fun `GEPs with same constant indices are MustAlias`() {
        val fn = buildFunction {
            val structType = Type.Struct(null, listOf(Type.I32, Type.I64))
            createFunction("f", emptyList(), Type.Void)
            appendBlock("entry")
            val s = alloca(structType)
            val f0a = gep(structType, s, Constant.I32(0), Constant.I32(0))
            val f0b = gep(structType, s, Constant.I32(0), Constant.I32(0))
            store(Constant.I32(42), f0a)
            val v = load(Type.I32, f0b)
            ret(v)
            finalizeFunction()
        }
        val aa = BasicAliasAnalysis(fn)
        val gep0a = fn.blocks[0].instructions[1].result!!
        val gep0b = fn.blocks[0].instructions[2].result!!
        assertEquals(AliasResult.MustAlias, aa.alias(gep0a, gep0b))
    }

    // --- BasicAliasAnalysis: GEP from different allocas ---

    @Test
    fun `GEPs from different allocas are NoAlias`() {
        val fn = buildFunction {
            createFunction("f", emptyList(), Type.Void)
            appendBlock("entry")
            val a = alloca(Type.I32)
            val b = alloca(Type.I32)
            val ga = gep(Type.I32, a, Constant.I32(0))
            val gb = gep(Type.I32, b, Constant.I32(0))
            store(Constant.I32(1), ga)
            store(Constant.I32(2), gb)
            ret()
            finalizeFunction()
        }
        val aa = BasicAliasAnalysis(fn)
        val gepA = fn.blocks[0].instructions[2].result!!
        val gepB = fn.blocks[0].instructions[3].result!!
        assertEquals(AliasResult.NoAlias, aa.alias(gepA, gepB))
    }

    // --- BasicAliasAnalysis: unknown pointers are MayAlias ---

    @Test
    fun `two parameters are MayAlias`() {
        val fn = buildFunction {
            createFunction("f", listOf(Param("p", Type.OpaquePointer), Param("q", Type.OpaquePointer)), Type.Void)
            appendBlock("entry")
            ret()
            finalizeFunction()
        }
        val aa = BasicAliasAnalysis(fn)
        assertEquals(AliasResult.MayAlias, aa.alias(fn.params[0], fn.params[1]))
    }

    // --- BasicAliasAnalysis: GEP partial alias ---

    @Test
    fun `GEP from base vs base is PartialAlias`() {
        val fn = buildFunction {
            val structType = Type.Struct(null, listOf(Type.I32, Type.I64))
            val params = createFunction("f", listOf(Param("s", Type.Pointer(structType))), Type.Void)
            appendBlock("entry")
            val f0 = gep(structType, params[0], Constant.I32(0), Constant.I32(0))
            store(Constant.I32(42), f0)
            ret()
            finalizeFunction()
        }
        val aa = BasicAliasAnalysis(fn)
        val gepResult = fn.blocks[0].instructions[0].result!!
        assertEquals(AliasResult.PartialAlias, aa.alias(gepResult, fn.params[0]))
    }

    // --- AliasAnalysis interface: readsMemory / writesMemory ---

    @Test
    fun `readsMemory identifies loads and calls`() {
        val fn = buildFunction {
            createFunction("f", emptyList(), Type.Void)
            appendBlock("entry")
            ret()
            finalizeFunction()
        }
        val aa = BasicAliasAnalysis(fn)
        val loadInst = Load(InstructionRef("%0", Type.I32), Parameter("p", Type.OpaquePointer, 0), Type.I32)
        val storeInst = Store(Constant.I32(1), Parameter("p", Type.OpaquePointer, 0))
        val addInst = Add(InstructionRef("%1", Type.I32), Constant.I32(1), Constant.I32(2))

        assertTrue(aa.readsMemory(loadInst))
        assertFalse(aa.readsMemory(storeInst))
        assertFalse(aa.readsMemory(addInst))
    }

    @Test
    fun `writesMemory identifies stores and calls`() {
        val fn = buildFunction {
            createFunction("f", emptyList(), Type.Void)
            appendBlock("entry")
            ret()
            finalizeFunction()
        }
        val aa = BasicAliasAnalysis(fn)
        val loadInst = Load(InstructionRef("%0", Type.I32), Parameter("p", Type.OpaquePointer, 0), Type.I32)
        val storeInst = Store(Constant.I32(1), Parameter("p", Type.OpaquePointer, 0))

        assertFalse(aa.writesMemory(loadInst))
        assertTrue(aa.writesMemory(storeInst))
    }
}
