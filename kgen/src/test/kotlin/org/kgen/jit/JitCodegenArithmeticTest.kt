package org.kgen.jit

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.kgen.ir.*
import org.kgen.ir.build.ModuleBuilder
import org.kgen.ir.target.Target
import org.kgen.target.x86.codegen.X86CodeGenerator

/**
 * JIT execution tests for arithmetic IR instructions.
 * Covers integer and float operations that DOOM uses.
 */
@EnabledOnOs(OS.WINDOWS, OS.LINUX)
class JitCodegenArithmeticTest {

    private fun jitCall(module: Module, name: String, vararg args: Long): Long {
        val engine = JitEngine(X86CodeGenerator())
        engine.addModule(module)
        return engine.call(name, *args)
    }

    private fun buildModule(block: ModuleBuilder.() -> Unit): Module {
        val builder = ModuleBuilder("test", Target.x86_64())
        builder.block()
        return builder.build()
    }

    // --- Integer Division ---

    @Test fun sdivI32() {
        val module = buildModule {
            val params = createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            ret(sdiv(params[0], params[1]))
            finalizeFunction()
        }
        assertEquals(3L, jitCall(module, "f", 10, 3))
        assertEquals(-3L and 0xFFFFFFFFL, jitCall(module, "f", -10, 3) and 0xFFFFFFFFL)
        assertEquals(0L, jitCall(module, "f", 0, 5))
    }

    @Test fun udivI32() {
        val module = buildModule {
            val params = createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            ret(udiv(params[0], params[1]))
            finalizeFunction()
        }
        assertEquals(3L, jitCall(module, "f", 10, 3))
        // -1 as unsigned I32 = 0xFFFFFFFF / 2 = 0x7FFFFFFF
        val result = jitCall(module, "f", -1, 2) and 0xFFFFFFFFL
        assertEquals(0x7FFFFFFFL, result)
    }

    @Test fun sdivI64() {
        val module = buildModule {
            val params = createFunction("f", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            appendBlock("entry")
            ret(sdiv(params[0], params[1]))
            finalizeFunction()
        }
        assertEquals(5L, jitCall(module, "f", 25, 5))
        assertEquals(-5L, jitCall(module, "f", -25, 5))
    }

    // --- Integer Remainder ---

    @Test fun sremI32() {
        val module = buildModule {
            val params = createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            ret(srem(params[0], params[1]))
            finalizeFunction()
        }
        assertEquals(1L, jitCall(module, "f", 10, 3))
        assertEquals(-1L and 0xFFFFFFFFL, jitCall(module, "f", -10, 3) and 0xFFFFFFFFL)
    }

    @Test fun uremI32() {
        val module = buildModule {
            val params = createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            ret(urem(params[0], params[1]))
            finalizeFunction()
        }
        assertEquals(1L, jitCall(module, "f", 10, 3))
    }

    // --- Shifts ---

    @Test fun ashrI32() {
        val module = buildModule {
            val params = createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            ret(ashr(params[0], params[1]))
            finalizeFunction()
        }
        assertEquals(5L, jitCall(module, "f", 40, 3))
        // Arithmetic right shift preserves sign
        val negResult = jitCall(module, "f", -8, 1) and 0xFFFFFFFFL
        assertEquals((-4L) and 0xFFFFFFFFL, negResult)
    }

    @Test fun lshrI32() {
        val module = buildModule {
            val params = createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            ret(lshr(params[0], params[1]))
            finalizeFunction()
        }
        assertEquals(5L, jitCall(module, "f", 40, 3))
        // Logical right shift fills with zeros
        val result = jitCall(module, "f", -1, 1) and 0xFFFFFFFFL
        assertEquals(0x7FFFFFFFL, result)
    }

    @Test fun shlI32() {
        val module = buildModule {
            val params = createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            ret(shl(params[0], params[1]))
            finalizeFunction()
        }
        // Dump Win64 ASM via JitEngine
        val dumpEngine = JitEngine(X86CodeGenerator())
        dumpEngine.addModule(module)
        val inspector = dumpEngine.inspector()
        val asmFile = java.io.File("build/shl-asm.log")
        asmFile.writeText(inspector.dumpAsm("f") ?: "no ASM")

        assertEquals(40L, jitCall(module, "f", 5, 3))
    }

    // --- Negation ---

    @Test fun negI32() {
        val module = buildModule {
            val params = createFunction("f", listOf(Param("a", Type.I32)), Type.I32)
            appendBlock("entry")
            ret(neg(params[0]))
            finalizeFunction()
        }
        assertEquals(-5L and 0xFFFFFFFFL, jitCall(module, "f", 5) and 0xFFFFFFFFL)
        assertEquals(5L, jitCall(module, "f", -5) and 0xFFFFFFFFL)
    }

    // --- Float Arithmetic (results via bitcast to I64 since engine.call returns Long) ---

    @Test fun faddF64() {
        val module = buildModule {
            val params = createFunction("f", listOf(Param("ptr", Type.I64)), Type.I64)
            appendBlock("entry")
            val a = load(Type.F64, params[0])
            val b = load(Type.F64, add(params[0], Constant.I64(8)))
            val sum = fadd(a, b)
            ret(bitcast(sum, Type.I64))
            finalizeFunction()
        }
        val engine = JitEngine(X86CodeGenerator())
        engine.addModule(module)
        val arena = java.lang.foreign.Arena.ofShared()
        val mem = arena.allocate(16, 8)
        mem.set(java.lang.foreign.ValueLayout.JAVA_DOUBLE, 0, 3.0)
        mem.set(java.lang.foreign.ValueLayout.JAVA_DOUBLE, 8, 4.0)
        val result = java.lang.Double.longBitsToDouble(engine.call("f", mem.address()))
        assertEquals(7.0, result, 0.001)
        arena.close()
    }

    @Test fun fsubF64() {
        val module = buildModule {
            val params = createFunction("f", listOf(Param("ptr", Type.I64)), Type.I64)
            appendBlock("entry")
            val a = load(Type.F64, params[0])
            val b = load(Type.F64, add(params[0], Constant.I64(8)))
            val diff = fsub(a, b)
            ret(bitcast(diff, Type.I64))
            finalizeFunction()
        }
        val engine = JitEngine(X86CodeGenerator())
        engine.addModule(module)
        val arena = java.lang.foreign.Arena.ofShared()
        val mem = arena.allocate(16, 8)
        mem.set(java.lang.foreign.ValueLayout.JAVA_DOUBLE, 0, 10.0)
        mem.set(java.lang.foreign.ValueLayout.JAVA_DOUBLE, 8, 3.0)
        val result = java.lang.Double.longBitsToDouble(engine.call("f", mem.address()))
        assertEquals(7.0, result, 0.001)
        arena.close()
    }

    @Test fun fmulF64() {
        val module = buildModule {
            val params = createFunction("f", listOf(Param("ptr", Type.I64)), Type.I64)
            appendBlock("entry")
            val a = load(Type.F64, params[0])
            val b = load(Type.F64, add(params[0], Constant.I64(8)))
            ret(bitcast(fmul(a, b), Type.I64))
            finalizeFunction()
        }
        val engine = JitEngine(X86CodeGenerator())
        engine.addModule(module)
        val arena = java.lang.foreign.Arena.ofShared()
        val mem = arena.allocate(16, 8)
        mem.set(java.lang.foreign.ValueLayout.JAVA_DOUBLE, 0, 3.0)
        mem.set(java.lang.foreign.ValueLayout.JAVA_DOUBLE, 8, 4.0)
        val result = java.lang.Double.longBitsToDouble(engine.call("f", mem.address()))
        assertEquals(12.0, result, 0.001)
        arena.close()
    }

    @Test fun fdivF64() {
        val module = buildModule {
            val params = createFunction("f", listOf(Param("ptr", Type.I64)), Type.I64)
            appendBlock("entry")
            val a = load(Type.F64, params[0])
            val b = load(Type.F64, add(params[0], Constant.I64(8)))
            ret(bitcast(fdiv(a, b), Type.I64))
            finalizeFunction()
        }
        val engine = JitEngine(X86CodeGenerator())
        engine.addModule(module)
        val arena = java.lang.foreign.Arena.ofShared()
        val mem = arena.allocate(16, 8)
        mem.set(java.lang.foreign.ValueLayout.JAVA_DOUBLE, 0, 12.0)
        mem.set(java.lang.foreign.ValueLayout.JAVA_DOUBLE, 8, 4.0)
        val result = java.lang.Double.longBitsToDouble(engine.call("f", mem.address()))
        assertEquals(3.0, result, 0.001)
        arena.close()
    }

    // --- Float Comparison ---

    @Test fun fcmpOeqF64() {
        val module = buildModule {
            val params = createFunction("f", listOf(Param("ptr", Type.I64)), Type.I32)
            appendBlock("entry")
            val a = load(Type.F64, params[0])
            val b = load(Type.F64, add(params[0], Constant.I64(8)))
            val cmp = fcmp(FCmpPredicate.OEQ, a, b)
            ret(zext(cmp, Type.I32))
            finalizeFunction()
        }
        val engine = JitEngine(X86CodeGenerator())
        engine.addModule(module)
        val arena = java.lang.foreign.Arena.ofShared()
        val mem = arena.allocate(16, 8)
        mem.set(java.lang.foreign.ValueLayout.JAVA_DOUBLE, 0, 3.14)
        mem.set(java.lang.foreign.ValueLayout.JAVA_DOUBLE, 8, 3.14)
        assertEquals(1L, engine.call("f", mem.address()))
        mem.set(java.lang.foreign.ValueLayout.JAVA_DOUBLE, 8, 2.71)
        assertEquals(0L, engine.call("f", mem.address()))
        arena.close()
    }

    @Test fun fcmpOltF64() {
        val module = buildModule {
            val params = createFunction("f", listOf(Param("ptr", Type.I64)), Type.I32)
            appendBlock("entry")
            val a = load(Type.F64, params[0])
            val b = load(Type.F64, add(params[0], Constant.I64(8)))
            val cmp = fcmp(FCmpPredicate.OLT, a, b)
            ret(zext(cmp, Type.I32))
            finalizeFunction()
        }
        val engine = JitEngine(X86CodeGenerator())
        engine.addModule(module)
        val arena = java.lang.foreign.Arena.ofShared()
        val mem = arena.allocate(16, 8)
        mem.set(java.lang.foreign.ValueLayout.JAVA_DOUBLE, 0, 2.0)
        mem.set(java.lang.foreign.ValueLayout.JAVA_DOUBLE, 8, 3.0)
        assertEquals(1L, engine.call("f", mem.address()))
        mem.set(java.lang.foreign.ValueLayout.JAVA_DOUBLE, 0, 5.0)
        assertEquals(0L, engine.call("f", mem.address()))
        arena.close()
    }

    // --- Shift edge cases ---

    @Test fun shlI64() {
        val module = buildModule {
            val params = createFunction("f", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            appendBlock("entry")
            ret(shl(params[0], params[1]))
            finalizeFunction()
        }
        assertEquals(40L, jitCall(module, "f", 5, 3))
        assertEquals(0x8000000000000000UL.toLong(), jitCall(module, "f", 1, 63))
    }

    @Test fun shlByConstant() {
        val module = buildModule {
            val params = createFunction("f", listOf(Param("a", Type.I32)), Type.I32)
            appendBlock("entry")
            ret(shl(params[0], Constant.I32(4)))
            finalizeFunction()
        }
        assertEquals(160L, jitCall(module, "f", 10))
    }

    @Test fun lshrI64() {
        val module = buildModule {
            val params = createFunction("f", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            appendBlock("entry")
            ret(lshr(params[0], params[1]))
            finalizeFunction()
        }
        assertEquals(5L, jitCall(module, "f", 40, 3))
    }

    // --- Float F32 ---

    @Test fun faddF32() {
        val module = buildModule {
            val params = createFunction("f", listOf(Param("ptr", Type.I64)), Type.I32)
            appendBlock("entry")
            val a = load(Type.F32, params[0])
            val b = load(Type.F32, add(params[0], Constant.I64(4)))
            val sum = fadd(a, b)
            val bits = bitcast(sum, Type.I32)
            ret(bits)
            finalizeFunction()
        }
        val engine = JitEngine(X86CodeGenerator())
        engine.addModule(module)
        val arena = java.lang.foreign.Arena.ofShared()
        val mem = arena.allocate(8, 4)
        mem.set(java.lang.foreign.ValueLayout.JAVA_FLOAT, 0, 3.0f)
        mem.set(java.lang.foreign.ValueLayout.JAVA_FLOAT, 4, 4.0f)
        val result = java.lang.Float.intBitsToFloat(engine.call("f", mem.address()).toInt())
        assertEquals(7.0f, result, 0.001f)
        arena.close()
    }

    // --- I64 division/remainder/shifts with variable amounts ---

    @Test fun udivI64() {
        val module = buildModule {
            val params = createFunction("f", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            appendBlock("entry")
            ret(udiv(params[0], params[1]))
            finalizeFunction()
        }
        assertEquals(5L, jitCall(module, "f", 25, 5))
    }

    @Test fun sremI64() {
        val module = buildModule {
            val params = createFunction("f", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            appendBlock("entry")
            ret(srem(params[0], params[1]))
            finalizeFunction()
        }
        assertEquals(1L, jitCall(module, "f", 10, 3))
    }

    @Test fun ashrI64() {
        val module = buildModule {
            val params = createFunction("f", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
            appendBlock("entry")
            ret(ashr(params[0], params[1]))
            finalizeFunction()
        }
        assertEquals(5L, jitCall(module, "f", 40, 3))
        assertEquals(-4L, jitCall(module, "f", -8, 1))
    }

    // --- Float unary ---

    @Test fun fnegF64() {
        val module = buildModule {
            val params = createFunction("f", listOf(Param("ptr", Type.I64)), Type.I64)
            appendBlock("entry")
            val loaded = load(Type.F64, params[0])
            val negated = fneg(loaded)
            ret(bitcast(negated, Type.I64))
            finalizeFunction()
        }
        val engine = JitEngine(X86CodeGenerator())
        engine.addModule(module)
        val arena = java.lang.foreign.Arena.ofShared()
        val mem = arena.allocate(8, 8)
        mem.set(java.lang.foreign.ValueLayout.JAVA_DOUBLE, 0, 3.14)
        val result = java.lang.Double.longBitsToDouble(engine.call("f", mem.address()))
        assertEquals(-3.14, result, 0.001)
        arena.close()
    }

    // --- More float comparisons ---

    @Test fun fcmpOneF64() {
        val module = buildModule {
            val params = createFunction("f", listOf(Param("ptr", Type.I64)), Type.I32)
            appendBlock("entry")
            val a = load(Type.F64, params[0])
            val b = load(Type.F64, add(params[0], Constant.I64(8)))
            ret(zext(fcmp(FCmpPredicate.ONE, a, b), Type.I32))
            finalizeFunction()
        }
        val engine = JitEngine(X86CodeGenerator())
        engine.addModule(module)
        val arena = java.lang.foreign.Arena.ofShared()
        val mem = arena.allocate(16, 8)
        mem.set(java.lang.foreign.ValueLayout.JAVA_DOUBLE, 0, 3.0)
        mem.set(java.lang.foreign.ValueLayout.JAVA_DOUBLE, 8, 4.0)
        assertEquals(1L, engine.call("f", mem.address()), "3.0 != 4.0")
        mem.set(java.lang.foreign.ValueLayout.JAVA_DOUBLE, 8, 3.0)
        assertEquals(0L, engine.call("f", mem.address()), "3.0 == 3.0")
        arena.close()
    }

    @Test fun fcmpOgeF64() {
        val module = buildModule {
            val params = createFunction("f", listOf(Param("ptr", Type.I64)), Type.I32)
            appendBlock("entry")
            val a = load(Type.F64, params[0])
            val b = load(Type.F64, add(params[0], Constant.I64(8)))
            ret(zext(fcmp(FCmpPredicate.OGE, a, b), Type.I32))
            finalizeFunction()
        }
        val engine = JitEngine(X86CodeGenerator())
        engine.addModule(module)
        val arena = java.lang.foreign.Arena.ofShared()
        val mem = arena.allocate(16, 8)
        mem.set(java.lang.foreign.ValueLayout.JAVA_DOUBLE, 0, 5.0)
        mem.set(java.lang.foreign.ValueLayout.JAVA_DOUBLE, 8, 3.0)
        assertEquals(1L, engine.call("f", mem.address()), "5.0 >= 3.0")
        mem.set(java.lang.foreign.ValueLayout.JAVA_DOUBLE, 0, 3.0)
        assertEquals(1L, engine.call("f", mem.address()), "3.0 >= 3.0")
        mem.set(java.lang.foreign.ValueLayout.JAVA_DOUBLE, 0, 2.0)
        assertEquals(0L, engine.call("f", mem.address()), "2.0 >= 3.0 = false")
        arena.close()
    }

    @Test fun fcmpOltF32() {
        val module = buildModule {
            val params = createFunction("f", listOf(Param("ptr", Type.I64)), Type.I32)
            appendBlock("entry")
            val a = load(Type.F32, params[0])
            val b = load(Type.F32, add(params[0], Constant.I64(4)))
            ret(zext(fcmp(FCmpPredicate.OLT, a, b), Type.I32))
            finalizeFunction()
        }
        val engine = JitEngine(X86CodeGenerator())
        engine.addModule(module)
        val arena = java.lang.foreign.Arena.ofShared()
        val mem = arena.allocate(8, 4)
        mem.set(java.lang.foreign.ValueLayout.JAVA_FLOAT, 0, 2.0f)
        mem.set(java.lang.foreign.ValueLayout.JAVA_FLOAT, 4, 3.0f)
        assertEquals(1L, engine.call("f", mem.address()), "2.0f < 3.0f")
        mem.set(java.lang.foreign.ValueLayout.JAVA_FLOAT, 0, 5.0f)
        assertEquals(0L, engine.call("f", mem.address()), "5.0f < 3.0f = false")
        arena.close()
    }

    // --- Int ↔ Float Conversions ---

    @Test fun sitofpI32ToF64() {
        val module = buildModule {
            val params = createFunction("f", listOf(Param("a", Type.I32)), Type.I64)
            appendBlock("entry")
            val fp = sitofp(params[0], Type.F64)
            ret(bitcast(fp, Type.I64))
            finalizeFunction()
        }
        val result = java.lang.Double.longBitsToDouble(jitCall(module, "f", 42))
        assertEquals(42.0, result, 0.001)
        val negResult = java.lang.Double.longBitsToDouble(jitCall(module, "f", -10))
        assertEquals(-10.0, negResult, 0.001)
    }

    @Test fun fptosiF64ToI32() {
        val module = buildModule {
            val params = createFunction("f", listOf(Param("ptr", Type.I64)), Type.I32)
            appendBlock("entry")
            val fp = load(Type.F64, params[0])
            ret(fptosi(fp, Type.I32))
            finalizeFunction()
        }
        val engine = JitEngine(X86CodeGenerator())
        engine.addModule(module)
        val arena = java.lang.foreign.Arena.ofShared()
        val mem = arena.allocate(8, 8)
        mem.set(java.lang.foreign.ValueLayout.JAVA_DOUBLE, 0, 42.9)
        assertEquals(42L, engine.call("f", mem.address()))
        mem.set(java.lang.foreign.ValueLayout.JAVA_DOUBLE, 0, -7.3)
        assertEquals(-7L and 0xFFFFFFFFL, engine.call("f", mem.address()) and 0xFFFFFFFFL)
        arena.close()
    }
}
