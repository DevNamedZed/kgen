package org.kgen.pass

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*
import org.kgen.ir.instructions.*
import org.kgen.ir.build.IrBuilder
import org.kgen.ir.target.Target

class BasicAliasAnalysisTest {

    private fun buildFunction(block: IrBuilder.() -> Unit): IrFunction {
        val ir = IrBuilder("test", Target.x86_64())
        ir.block()
        return ir.build().functions[0]
    }

    @Nested
    inner class IdentityRule {

        @Test
        fun `same parameter is MustAlias`() {
            val fn = buildFunction {
                createFunction("f", listOf(Param("ptr", Type.OpaquePointer)), Type.Void)
                positionAtEnd(appendBlock("entry"))
                ret()
                finalizeFunction()
            }
            val aa = BasicAliasAnalysis(fn)
            val ptr = fn.params[0]
            assertEquals(AliasResult.MustAlias, aa.alias(ptr, ptr))
        }

        @Test
        fun `same alloca result is MustAlias`() {
            val fn = buildFunction {
                createFunction("f", emptyList(), Type.Void)
                positionAtEnd(appendBlock("entry"))
                val a = alloca(Type.I32)
                store(Constant.I32(1), a)
                ret()
                finalizeFunction()
            }
            val aa = BasicAliasAnalysis(fn)
            val allocaResult = fn.blocks[0].instructions[0].result!!
            assertEquals(AliasResult.MustAlias, aa.alias(allocaResult, allocaResult))
        }

        @Test
        fun `same global is MustAlias`() {
            val fn = buildFunction {
                addGlobal("g", Type.I32, Constant.I32(0))
                createFunction("f", emptyList(), Type.Void)
                positionAtEnd(appendBlock("entry"))
                ret()
                finalizeFunction()
            }
            val aa = BasicAliasAnalysis(fn)
            val g1 = GlobalRef("g", Type.Pointer(Type.I32))
            val g2 = GlobalRef("g", Type.Pointer(Type.I32))
            assertEquals(AliasResult.MustAlias, aa.alias(g1, g2))
        }
    }

    @Nested
    inner class DistinctAllocasRule {

        @Test
        fun `two different allocas are NoAlias`() {
            val fn = buildFunction {
                createFunction("f", emptyList(), Type.Void)
                positionAtEnd(appendBlock("entry"))
                val a = alloca(Type.I32)
                val b = alloca(Type.I64)
                store(Constant.I32(1), a)
                store(Constant.I64(2), b)
                ret()
                finalizeFunction()
            }
            val aa = BasicAliasAnalysis(fn)
            val allocaA = fn.blocks[0].instructions[0].result!!
            val allocaB = fn.blocks[0].instructions[1].result!!
            assertEquals(AliasResult.NoAlias, aa.alias(allocaA, allocaB))
        }

        @Test
        fun `three allocas are pairwise NoAlias`() {
            val fn = buildFunction {
                createFunction("f", emptyList(), Type.Void)
                positionAtEnd(appendBlock("entry"))
                val a = alloca(Type.I32)
                val b = alloca(Type.I32)
                val c = alloca(Type.I32)
                store(Constant.I32(1), a)
                store(Constant.I32(2), b)
                store(Constant.I32(3), c)
                ret()
                finalizeFunction()
            }
            val aa = BasicAliasAnalysis(fn)
            val ra = fn.blocks[0].instructions[0].result!!
            val rb = fn.blocks[0].instructions[1].result!!
            val rc = fn.blocks[0].instructions[2].result!!
            assertEquals(AliasResult.NoAlias, aa.alias(ra, rb))
            assertEquals(AliasResult.NoAlias, aa.alias(ra, rc))
            assertEquals(AliasResult.NoAlias, aa.alias(rb, rc))
        }
    }

    @Nested
    inner class GlobalVsAllocaRule {

        @Test
        fun `global ref vs alloca is NoAlias both directions`() {
            val fn = buildFunction {
                addGlobal("g", Type.I32, Constant.I32(0))
                createFunction("f", emptyList(), Type.Void)
                positionAtEnd(appendBlock("entry"))
                val a = alloca(Type.I32)
                store(Constant.I32(1), a)
                ret()
                finalizeFunction()
            }
            val aa = BasicAliasAnalysis(fn)
            val globalRef = GlobalRef("g", Type.Pointer(Type.I32))
            val allocaResult = fn.blocks[0].instructions[0].result!!
            assertEquals(AliasResult.NoAlias, aa.alias(globalRef, allocaResult))
            assertEquals(AliasResult.NoAlias, aa.alias(allocaResult, globalRef))
        }
    }

    @Nested
    inner class DistinctGlobalsRule {

        @Test
        fun `different globals are NoAlias`() {
            val fn = buildFunction {
                addGlobal("g1", Type.I32, Constant.I32(0))
                addGlobal("g2", Type.I64, Constant.I64(0))
                createFunction("f", emptyList(), Type.Void)
                positionAtEnd(appendBlock("entry"))
                ret()
                finalizeFunction()
            }
            val aa = BasicAliasAnalysis(fn)
            val g1 = GlobalRef("g1", Type.Pointer(Type.I32))
            val g2 = GlobalRef("g2", Type.Pointer(Type.I64))
            assertEquals(AliasResult.NoAlias, aa.alias(g1, g2))
        }
    }

    @Nested
    inner class NullPointerRule {

        @Test
        fun `null vs global is NoAlias`() {
            val fn = buildFunction {
                addGlobal("g", Type.I32, Constant.I32(0))
                createFunction("f", emptyList(), Type.Void)
                positionAtEnd(appendBlock("entry"))
                ret()
                finalizeFunction()
            }
            val aa = BasicAliasAnalysis(fn)
            val g = GlobalRef("g", Type.Pointer(Type.I32))
            assertEquals(AliasResult.NoAlias, aa.alias(Constant.NullPtr, g))
            assertEquals(AliasResult.NoAlias, aa.alias(g, Constant.NullPtr))
        }

        @Test
        fun `null vs parameter is NoAlias`() {
            val fn = buildFunction {
                createFunction("f", listOf(Param("p", Type.OpaquePointer)), Type.Void)
                positionAtEnd(appendBlock("entry"))
                ret()
                finalizeFunction()
            }
            val aa = BasicAliasAnalysis(fn)
            assertEquals(AliasResult.NoAlias, aa.alias(Constant.NullPtr, fn.params[0]))
        }

        @Test
        fun `null vs null is MustAlias by identity`() {
            val fn = buildFunction {
                createFunction("f", emptyList(), Type.Void)
                positionAtEnd(appendBlock("entry"))
                ret()
                finalizeFunction()
            }
            val aa = BasicAliasAnalysis(fn)
            assertEquals(AliasResult.MustAlias, aa.alias(Constant.NullPtr, Constant.NullPtr))
        }
    }

    @Nested
    inner class ParameterVsAllocaRule {

        @Test
        fun `parameter vs alloca is NoAlias both directions`() {
            val fn = buildFunction {
                createFunction("f", listOf(Param("p", Type.OpaquePointer)), Type.Void)
                positionAtEnd(appendBlock("entry"))
                val a = alloca(Type.I32)
                store(Constant.I32(1), a)
                ret()
                finalizeFunction()
            }
            val aa = BasicAliasAnalysis(fn)
            val param = fn.params[0]
            val allocaResult = fn.blocks[0].instructions[0].result!!
            assertEquals(AliasResult.NoAlias, aa.alias(param, allocaResult))
            assertEquals(AliasResult.NoAlias, aa.alias(allocaResult, param))
        }
    }

    @Nested
    inner class GepRules {

        @Test
        fun `GEPs with different constant indices from same base are NoAlias`() {
            val fn = buildFunction {
                val structType = Type.Struct(null, listOf(Type.I32, Type.I64, Type.F32))
                createFunction("f", emptyList(), Type.Void)
                positionAtEnd(appendBlock("entry"))
                val s = alloca(structType)
                val f0 = gep(structType, s, Constant.I32(0), Constant.I32(0))
                val f2 = gep(structType, s, Constant.I32(0), Constant.I32(2))
                store(Constant.I32(1), f0)
                store(Constant.F32(3.14f), f2)
                ret()
                finalizeFunction()
            }
            val aa = BasicAliasAnalysis(fn)
            val gep0 = fn.blocks[0].instructions[1].result!!
            val gep2 = fn.blocks[0].instructions[2].result!!
            assertEquals(AliasResult.NoAlias, aa.alias(gep0, gep2))
        }

        @Test
        fun `GEPs with same constant indices from same base are MustAlias`() {
            val fn = buildFunction {
                val structType = Type.Struct(null, listOf(Type.I32, Type.I64))
                createFunction("f", emptyList(), Type.Void)
                positionAtEnd(appendBlock("entry"))
                val s = alloca(structType)
                val f0a = gep(structType, s, Constant.I32(0), Constant.I32(1))
                val f0b = gep(structType, s, Constant.I32(0), Constant.I32(1))
                store(Constant.I64(42), f0a)
                val v = load(Type.I64, f0b)
                ret()
                finalizeFunction()
            }
            val aa = BasicAliasAnalysis(fn)
            val gepA = fn.blocks[0].instructions[1].result!!
            val gepB = fn.blocks[0].instructions[2].result!!
            assertEquals(AliasResult.MustAlias, aa.alias(gepA, gepB))
        }

        @Test
        fun `GEP from different allocas are NoAlias via base decomposition`() {
            val fn = buildFunction {
                createFunction("f", emptyList(), Type.Void)
                positionAtEnd(appendBlock("entry"))
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

        @Test
        fun `GEP vs its own base pointer is PartialAlias`() {
            val fn = buildFunction {
                val structType = Type.Struct(null, listOf(Type.I32, Type.I64))
                val params = createFunction("f", listOf(Param("s", Type.Pointer(structType))), Type.Void)
                positionAtEnd(appendBlock("entry"))
                val f0 = gep(structType, params[0], Constant.I32(0), Constant.I32(0))
                store(Constant.I32(42), f0)
                ret()
                finalizeFunction()
            }
            val aa = BasicAliasAnalysis(fn)
            val gepResult = fn.blocks[0].instructions[0].result!!
            assertEquals(AliasResult.PartialAlias, aa.alias(gepResult, fn.params[0]))
            assertEquals(AliasResult.PartialAlias, aa.alias(fn.params[0], gepResult))
        }
    }

    @Nested
    inner class UnknownPointers {

        @Test
        fun `two unknown parameters are MayAlias`() {
            val fn = buildFunction {
                createFunction("f", listOf(
                    Param("p", Type.OpaquePointer),
                    Param("q", Type.OpaquePointer)
                ), Type.Void)
                positionAtEnd(appendBlock("entry"))
                ret()
                finalizeFunction()
            }
            val aa = BasicAliasAnalysis(fn)
            assertEquals(AliasResult.MayAlias, aa.alias(fn.params[0], fn.params[1]))
        }

        @Test
        fun `parameter vs different parameter is MayAlias`() {
            val fn = buildFunction {
                createFunction("f", listOf(
                    Param("a", Type.Pointer(Type.I32)),
                    Param("b", Type.Pointer(Type.I32))
                ), Type.Void)
                positionAtEnd(appendBlock("entry"))
                ret()
                finalizeFunction()
            }
            val aa = BasicAliasAnalysis(fn)
            assertEquals(AliasResult.MayAlias, aa.alias(fn.params[0], fn.params[1]))
        }
    }

    @Nested
    inner class MemoryClassification {

        @Test
        fun `readsMemory identifies loads`() {
            val fn = buildFunction {
                createFunction("f", emptyList(), Type.Void)
                positionAtEnd(appendBlock("entry"))
                ret()
                finalizeFunction()
            }
            val aa = BasicAliasAnalysis(fn)
            val load = Load(InstructionRef("%0", Type.I32), Parameter("p", Type.OpaquePointer, 0), Type.I32)
            assertTrue(aa.readsMemory(load))
        }

        @Test
        fun `readsMemory is false for stores`() {
            val fn = buildFunction {
                createFunction("f", emptyList(), Type.Void)
                positionAtEnd(appendBlock("entry"))
                ret()
                finalizeFunction()
            }
            val aa = BasicAliasAnalysis(fn)
            val store = Store(Constant.I32(1), Parameter("p", Type.OpaquePointer, 0))
            assertFalse(aa.readsMemory(store))
        }

        @Test
        fun `readsMemory is false for arithmetic`() {
            val fn = buildFunction {
                createFunction("f", emptyList(), Type.Void)
                positionAtEnd(appendBlock("entry"))
                ret()
                finalizeFunction()
            }
            val aa = BasicAliasAnalysis(fn)
            val add = Add(InstructionRef("%0", Type.I32), Constant.I32(1), Constant.I32(2))
            assertFalse(aa.readsMemory(add))
        }

        @Test
        fun `writesMemory identifies stores`() {
            val fn = buildFunction {
                createFunction("f", emptyList(), Type.Void)
                positionAtEnd(appendBlock("entry"))
                ret()
                finalizeFunction()
            }
            val aa = BasicAliasAnalysis(fn)
            val store = Store(Constant.I32(1), Parameter("p", Type.OpaquePointer, 0))
            assertTrue(aa.writesMemory(store))
        }

        @Test
        fun `writesMemory is false for loads`() {
            val fn = buildFunction {
                createFunction("f", emptyList(), Type.Void)
                positionAtEnd(appendBlock("entry"))
                ret()
                finalizeFunction()
            }
            val aa = BasicAliasAnalysis(fn)
            val load = Load(InstructionRef("%0", Type.I32), Parameter("p", Type.OpaquePointer, 0), Type.I32)
            assertFalse(aa.writesMemory(load))
        }

        @Test
        fun `readsMemory is true for calls`() {
            val fn = buildFunction {
                createFunction("f", emptyList(), Type.Void)
                positionAtEnd(appendBlock("entry"))
                ret()
                finalizeFunction()
            }
            val aa = BasicAliasAnalysis(fn)
            val call = Call(InstructionRef("%0", Type.I32), FunctionRef("foo", Type.Function(emptyList(), Type.I32)), emptyList(), Type.I32)
            assertTrue(aa.readsMemory(call))
            assertTrue(aa.writesMemory(call))
        }

        @Test
        fun `memoryPointer extracts pointer from load`() {
            val fn = buildFunction {
                createFunction("f", emptyList(), Type.Void)
                positionAtEnd(appendBlock("entry"))
                ret()
                finalizeFunction()
            }
            val aa = BasicAliasAnalysis(fn)
            val ptr = Parameter("p", Type.OpaquePointer, 0)
            val load = Load(InstructionRef("%0", Type.I32), ptr, Type.I32)
            assertEquals(ptr, aa.memoryPointer(load))
        }

        @Test
        fun `memoryPointer extracts pointer from store`() {
            val fn = buildFunction {
                createFunction("f", emptyList(), Type.Void)
                positionAtEnd(appendBlock("entry"))
                ret()
                finalizeFunction()
            }
            val aa = BasicAliasAnalysis(fn)
            val ptr = Parameter("p", Type.OpaquePointer, 0)
            val store = Store(Constant.I32(1), ptr)
            assertEquals(ptr, aa.memoryPointer(store))
        }

        @Test
        fun `memoryPointer returns null for non-memory instruction`() {
            val fn = buildFunction {
                createFunction("f", emptyList(), Type.Void)
                positionAtEnd(appendBlock("entry"))
                ret()
                finalizeFunction()
            }
            val aa = BasicAliasAnalysis(fn)
            val add = Add(InstructionRef("%0", Type.I32), Constant.I32(1), Constant.I32(2))
            assertNull(aa.memoryPointer(add))
        }
    }
}
