package org.kgen.jit

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.kgen.ir.*
import org.kgen.ir.build.ModuleBuilder
import org.kgen.ir.target.Target

/**
 * Tests for type conversion codegen: zext, sext, trunc, bitcast.
 */
@EnabledOnOs(OS.WINDOWS, OS.LINUX)
class JitCodegenConversionTest {

    private fun jitCall(module: Module, functionName: String, vararg args: Long): Long {
        val engine = JitEngine(org.kgen.target.x86.codegen.X86CodeGenerator())
        engine.addModule(module)
        return engine.call(functionName, *args)
    }

    private fun buildModule(name: String, block: ModuleBuilder.() -> Unit): Module {
        val builder = ModuleBuilder(name, Target.x86_64())
        builder.block()
        return builder.build()
    }

    @Test
    fun zextI32ToI64() {
        val module = buildModule("test") {
            val params = createFunction("f", listOf(Param("a", Type.I32)), Type.I64)
            appendBlock("entry")
            ret(zext(params[0], Type.I64))
            finalizeFunction()
        }
        assertEquals(42L, jitCall(module, "f", 42))
        // -1 as I32 = 0xFFFFFFFF, zext to I64 = 0x00000000FFFFFFFF
        assertEquals(0xFFFFFFFFL, jitCall(module, "f", -1) and 0xFFFFFFFFL)
    }

    @Test
    fun sextI32ToI64() {
        val module = buildModule("test") {
            val params = createFunction("f", listOf(Param("a", Type.I32)), Type.I64)
            appendBlock("entry")
            ret(sext(params[0], Type.I64))
            finalizeFunction()
        }
        assertEquals(42L, jitCall(module, "f", 42))
        assertEquals(-1L, jitCall(module, "f", -1))
    }

    @Test
    fun truncI64ToI32() {
        val module = buildModule("test") {
            val params = createFunction("f", listOf(Param("a", Type.I64)), Type.I32)
            appendBlock("entry")
            ret(trunc(params[0], Type.I32))
            finalizeFunction()
        }
        assertEquals(42L, jitCall(module, "f", 42))
        // 0x100000042 truncated to I32 = 0x42
        assertEquals(0x42L, jitCall(module, "f", 0x100000042L))
    }

    @Test
    fun zextI1ToI32() {
        val module = buildModule("test") {
            val params = createFunction("f", listOf(Param("a", Type.I32)), Type.I32)
            appendBlock("entry")
            val cmp = icmp(ICmpPredicate.EQ, params[0], Constant.I32(0))
            ret(zext(cmp, Type.I32))
            finalizeFunction()
        }
        assertEquals(1L, jitCall(module, "f", 0), "0==0 → 1")
        assertEquals(0L, jitCall(module, "f", 5), "5!=0 → 0")
    }

    @Test
    fun sextI16ToI64() {
        val module = buildModule("test") {
            val params = createFunction("f", listOf(Param("a", Type.I32)), Type.I64)
            appendBlock("entry")
            val truncated = trunc(params[0], Type.I16)
            ret(sext(truncated, Type.I64))
            finalizeFunction()
        }
        assertEquals(100L, jitCall(module, "f", 100))
        // 0xFFFF as I16 = -1, sext to I64 = -1
        assertEquals(-1L, jitCall(module, "f", 0xFFFF))
    }

    @Test
    fun truncI64ToI8() {
        val module = buildModule("test") {
            val params = createFunction("f", listOf(Param("a", Type.I64)), Type.I32)
            appendBlock("entry")
            val truncated = trunc(params[0], Type.I8)
            ret(zext(truncated, Type.I32))
            finalizeFunction()
        }
        assertEquals(0xABL, jitCall(module, "f", 0x12345678ABL))
    }

    @Test
    fun bitcastI64ToF64AndBack() {
        // I64 → bitcast to F64 → bitcast back to I64 (roundtrip in one function)
        val module = buildModule("test") {
            val params = createFunction("f", listOf(Param("a", Type.I64)), Type.I64)
            appendBlock("entry")
            val asFloat = bitcast(params[0], Type.F64)
            val backToInt = bitcast(asFloat, Type.I64)
            ret(backToInt)
            finalizeFunction()
        }
        val bits = java.lang.Double.doubleToLongBits(3.14)
        assertEquals(bits, jitCall(module, "f", bits), "I64→F64→I64 roundtrip")
    }

    @Test
    fun fpextF32ToF64() {
        val module = buildModule("test") {
            val params = createFunction("f", listOf(Param("ptr", Type.I64)), Type.I64)
            appendBlock("entry")
            val f32val = load(Type.F32, params[0])
            val f64val = fpext(f32val, Type.F64)
            ret(bitcast(f64val, Type.I64))
            finalizeFunction()
        }
        val engine = JitEngine(org.kgen.target.x86.codegen.X86CodeGenerator())
        engine.addModule(module)
        val arena = java.lang.foreign.Arena.ofShared()
        val mem = arena.allocate(4, 4)
        mem.set(java.lang.foreign.ValueLayout.JAVA_FLOAT, 0, 3.14f)
        val result = java.lang.Double.longBitsToDouble(engine.call("f", mem.address()))
        assertEquals(3.14, result, 0.01)
        arena.close()
    }

    @Test
    fun fptruncF64ToF32() {
        val module = buildModule("test") {
            val params = createFunction("f", listOf(Param("ptr", Type.I64)), Type.I32)
            appendBlock("entry")
            val f64val = load(Type.F64, params[0])
            val f32val = fptrunc(f64val, Type.F32)
            ret(bitcast(f32val, Type.I32))
            finalizeFunction()
        }
        val engine = JitEngine(org.kgen.target.x86.codegen.X86CodeGenerator())
        engine.addModule(module)
        val arena = java.lang.foreign.Arena.ofShared()
        val mem = arena.allocate(8, 8)
        mem.set(java.lang.foreign.ValueLayout.JAVA_DOUBLE, 0, 3.14)
        val result = java.lang.Float.intBitsToFloat(engine.call("f", mem.address()).toInt())
        assertEquals(3.14f, result, 0.01f)
        arena.close()
    }

    @Test
    fun sitofpI64ToF64() {
        val module = buildModule("test") {
            val params = createFunction("f", listOf(Param("a", Type.I64)), Type.I64)
            appendBlock("entry")
            val fp = sitofp(params[0], Type.F64)
            ret(bitcast(fp, Type.I64))
            finalizeFunction()
        }
        val result = java.lang.Double.longBitsToDouble(jitCall(module, "f", 1000000))
        assertEquals(1000000.0, result, 0.001)
    }

    @Test
    fun fptosiF64ToI64() {
        val module = buildModule("test") {
            val params = createFunction("f", listOf(Param("ptr", Type.I64)), Type.I64)
            appendBlock("entry")
            val fp = load(Type.F64, params[0])
            ret(fptosi(fp, Type.I64))
            finalizeFunction()
        }
        val engine = JitEngine(org.kgen.target.x86.codegen.X86CodeGenerator())
        engine.addModule(module)
        val arena = java.lang.foreign.Arena.ofShared()
        val mem = arena.allocate(8, 8)
        mem.set(java.lang.foreign.ValueLayout.JAVA_DOUBLE, 0, 99.9)
        assertEquals(99L, engine.call("f", mem.address()))
        arena.close()
    }

    @Test
    fun bitcastI64ToF64MemoryRoundtrip() {
        // Store I64, load as F64, bitcast to I64
        val module = buildModule("test") {
            val params = createFunction("f", listOf(Param("ptr", Type.I64), Param("bits", Type.I64)), Type.I64)
            appendBlock("entry")
            store(params[1], params[0])
            val loaded = load(Type.F64, params[0])
            val result = bitcast(loaded, Type.I64)
            ret(result)
            finalizeFunction()
        }
        val engine = JitEngine(org.kgen.target.x86.codegen.X86CodeGenerator())
        engine.addModule(module)

        val arena = java.lang.foreign.Arena.ofShared()
        val mem = arena.allocate(8, 8)
        val bits = java.lang.Double.doubleToLongBits(2.718)
        val result = engine.call("f", mem.address(), bits)
        assertEquals(bits, result, "Store I64 → Load F64 → Bitcast I64")
        arena.close()
    }
}
