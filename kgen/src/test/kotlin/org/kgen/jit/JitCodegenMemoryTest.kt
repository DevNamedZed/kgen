package org.kgen.jit

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.kgen.ir.*
import org.kgen.ir.build.ModuleBuilder
import org.kgen.ir.target.Target

/**
 * Tests for memory load/store codegen via JIT.
 *
 * Functions receive a base pointer (I64) and perform loads/stores relative to it.
 * Tests cover I32, I64, F64 types, large offsets, and store-then-load roundtrips.
 */
@EnabledOnOs(OS.WINDOWS, OS.LINUX)
class JitCodegenMemoryTest {

    private fun buildModule(name: String, block: ModuleBuilder.() -> Unit): Module {
        val builder = ModuleBuilder(name, Target.x86_64())
        builder.block()
        return builder.build()
    }

    // --- I32 load/store ---

    @Test
    fun loadI32FromPointer() {
        val module = buildModule("test") {
            val params = createFunction("f", listOf(Param("ptr", Type.I64)), Type.I32)
            appendBlock("entry")
            val value = load(Type.I32, params[0])
            ret(value)
            finalizeFunction()
        }
        val engine = JitEngine(org.kgen.target.x86.codegen.X86CodeGenerator())
        engine.addModule(module)

        val arena = java.lang.foreign.Arena.ofShared()
        val mem = arena.allocate(4, 4)
        mem.set(java.lang.foreign.ValueLayout.JAVA_INT, 0, 42)
        assertEquals(42L, engine.call("f", mem.address()))
        arena.close()
    }

    @Test
    fun storeI32ThenLoad() {
        val module = buildModule("test") {
            val params = createFunction("f", listOf(Param("ptr", Type.I64), Param("val", Type.I32)), Type.I32)
            appendBlock("entry")
            store(params[1], params[0])
            val loaded = load(Type.I32, params[0])
            ret(loaded)
            finalizeFunction()
        }
        val engine = JitEngine(org.kgen.target.x86.codegen.X86CodeGenerator())
        engine.addModule(module)

        val arena = java.lang.foreign.Arena.ofShared()
        val mem = arena.allocate(4, 4)
        assertEquals(99L, engine.call("f", mem.address(), 99))
        assertEquals(-1L and 0xFFFFFFFFL, engine.call("f", mem.address(), -1) and 0xFFFFFFFFL)
        arena.close()
    }

    // --- Pointer arithmetic (base + offset) ---

    @Test
    fun loadFromBaseWithI64Offset() {
        // Pattern: base pointer + i64 offset → load i32
        val module = buildModule("test") {
            val params = createFunction("f", listOf(Param("base", Type.I64), Param("offset", Type.I64)), Type.I32)
            appendBlock("entry")
            val addr = add(params[0], params[1])
            val value = load(Type.I32, addr)
            ret(value)
            finalizeFunction()
        }
        val engine = JitEngine(org.kgen.target.x86.codegen.X86CodeGenerator())
        engine.addModule(module)

        val arena = java.lang.foreign.Arena.ofShared()
        val mem = arena.allocate(1024, 8)
        mem.set(java.lang.foreign.ValueLayout.JAVA_INT, 100, 0xDEAD)
        assertEquals(0xDEADL, engine.call("f", mem.address(), 100))
        arena.close()
    }

    @Test
    fun loadFromBaseWithZextI32Offset() {
        // DOOM pattern: base + zext(i32 addr) → load i32
        val module = buildModule("test") {
            val params = createFunction("f", listOf(Param("base", Type.I64), Param("addr", Type.I32)), Type.I32)
            appendBlock("entry")
            val extended = zext(params[1], Type.I64)
            val ptr = add(params[0], extended)
            val value = load(Type.I32, ptr)
            ret(value)
            finalizeFunction()
        }
        val engine = JitEngine(org.kgen.target.x86.codegen.X86CodeGenerator())
        engine.addModule(module)

        val arena = java.lang.foreign.Arena.ofShared()
        val mem = arena.allocate(65536, 8)
        mem.set(java.lang.foreign.ValueLayout.JAVA_INT, 1000, 777)
        assertEquals(777L, engine.call("f", mem.address(), 1000))
        arena.close()
    }

    // --- I64 load/store ---

    @Test
    fun loadStoreI64() {
        val module = buildModule("test") {
            val params = createFunction("f", listOf(Param("ptr", Type.I64), Param("val", Type.I64)), Type.I64)
            appendBlock("entry")
            store(params[1], params[0])
            val loaded = load(Type.I64, params[0])
            ret(loaded)
            finalizeFunction()
        }
        val engine = JitEngine(org.kgen.target.x86.codegen.X86CodeGenerator())
        engine.addModule(module)

        val arena = java.lang.foreign.Arena.ofShared()
        val mem = arena.allocate(8, 8)
        assertEquals(Long.MAX_VALUE, engine.call("f", mem.address(), Long.MAX_VALUE))
        arena.close()
    }

    // --- F64 load/store via bitcast ---

    @Test
    fun loadStoreF64ViaBitcast() {
        // Write I64 bits to memory, bitcast-load as F64, bitcast back to I64
        val module = buildModule("test") {
            val params = createFunction("f", listOf(Param("ptr", Type.I64), Param("bits", Type.I64)), Type.I64)
            appendBlock("entry")
            // Store the I64 bits to memory
            store(params[1], params[0])
            // Load as F64
            val loaded = load(Type.F64, params[0])
            // Bitcast F64 back to I64
            val result = bitcast(loaded, Type.I64)
            ret(result)
            finalizeFunction()
        }
        val engine = JitEngine(org.kgen.target.x86.codegen.X86CodeGenerator())
        engine.addModule(module)

        val arena = java.lang.foreign.Arena.ofShared()
        val mem = arena.allocate(8, 8)
        val bits = java.lang.Double.doubleToLongBits(3.14)
        val result = engine.call("f", mem.address(), bits)
        assertEquals(bits, result, "F64 load → bitcast roundtrip")
        arena.close()
    }

    // --- Partial loads (I8, I16) with extension ---

    @Test
    fun loadI8ZeroExtend() {
        val module = buildModule("test") {
            val params = createFunction("f", listOf(Param("ptr", Type.I64)), Type.I32)
            appendBlock("entry")
            val byte = load(Type.I8, params[0])
            ret(zext(byte, Type.I32))
            finalizeFunction()
        }
        val engine = JitEngine(org.kgen.target.x86.codegen.X86CodeGenerator())
        engine.addModule(module)
        val arena = java.lang.foreign.Arena.ofShared()
        val mem = arena.allocate(4, 4)
        mem.set(java.lang.foreign.ValueLayout.JAVA_BYTE, 0, 0xFF.toByte())
        assertEquals(255L, engine.call("f", mem.address()), "0xFF zext to I32 = 255")
        arena.close()
    }

    @Test
    fun loadI8SignExtend() {
        val module = buildModule("test") {
            val params = createFunction("f", listOf(Param("ptr", Type.I64)), Type.I32)
            appendBlock("entry")
            val byte = load(Type.I8, params[0])
            ret(sext(byte, Type.I32))
            finalizeFunction()
        }
        val engine = JitEngine(org.kgen.target.x86.codegen.X86CodeGenerator())
        engine.addModule(module)
        val arena = java.lang.foreign.Arena.ofShared()
        val mem = arena.allocate(4, 4)
        mem.set(java.lang.foreign.ValueLayout.JAVA_BYTE, 0, 0xFF.toByte())
        assertEquals(-1L and 0xFFFFFFFFL, engine.call("f", mem.address()) and 0xFFFFFFFFL, "0xFF sext to I32 = -1")
        arena.close()
    }

    @Test
    fun loadI16ZeroExtend() {
        val module = buildModule("test") {
            val params = createFunction("f", listOf(Param("ptr", Type.I64)), Type.I32)
            appendBlock("entry")
            val short = load(Type.I16, params[0])
            ret(zext(short, Type.I32))
            finalizeFunction()
        }
        val engine = JitEngine(org.kgen.target.x86.codegen.X86CodeGenerator())
        engine.addModule(module)
        val arena = java.lang.foreign.Arena.ofShared()
        val mem = arena.allocate(4, 4)
        mem.set(java.lang.foreign.ValueLayout.JAVA_SHORT, 0, 0x8000.toShort())
        assertEquals(0x8000L, engine.call("f", mem.address()), "0x8000 zext = 32768")
        arena.close()
    }

    @Test
    fun storeTruncI8() {
        val module = buildModule("test") {
            val params = createFunction("f", listOf(Param("ptr", Type.I64), Param("val", Type.I32)), Type.I32)
            appendBlock("entry")
            val truncated = trunc(params[1], Type.I8)
            store(truncated, params[0])
            val loaded = load(Type.I8, params[0])
            ret(zext(loaded, Type.I32))
            finalizeFunction()
        }
        val engine = JitEngine(org.kgen.target.x86.codegen.X86CodeGenerator())
        engine.addModule(module)
        val arena = java.lang.foreign.Arena.ofShared()
        val mem = arena.allocate(4, 4)
        assertEquals(0xABL, engine.call("f", mem.address(), 0x12AB), "Store+load I8 truncates to low byte")
        arena.close()
    }

    // --- F32 load/store ---

    @Test
    fun loadStoreF32() {
        val module = buildModule("test") {
            val params = createFunction("f", listOf(Param("ptr", Type.I64), Param("bits", Type.I32)), Type.I32)
            appendBlock("entry")
            val f32val = bitcast(params[1], Type.F32)
            store(f32val, params[0])
            val loaded = load(Type.F32, params[0])
            ret(bitcast(loaded, Type.I32))
            finalizeFunction()
        }
        val engine = JitEngine(org.kgen.target.x86.codegen.X86CodeGenerator())
        engine.addModule(module)
        val arena = java.lang.foreign.Arena.ofShared()
        val mem = arena.allocate(4, 4)
        val bits = java.lang.Float.floatToIntBits(2.718f)
        val result = engine.call("f", mem.address(), bits.toLong())
        assertEquals(bits.toLong() and 0xFFFFFFFFL, result and 0xFFFFFFFFL)
        arena.close()
    }

    // --- Store I16 ---

    @Test
    fun storeTruncI16() {
        val module = buildModule("test") {
            val params = createFunction("f", listOf(Param("ptr", Type.I64), Param("val", Type.I32)), Type.I32)
            appendBlock("entry")
            val truncated = trunc(params[1], Type.I16)
            store(truncated, params[0])
            val loaded = load(Type.I16, params[0])
            ret(zext(loaded, Type.I32))
            finalizeFunction()
        }
        val engine = JitEngine(org.kgen.target.x86.codegen.X86CodeGenerator())
        engine.addModule(module)
        val arena = java.lang.foreign.Arena.ofShared()
        val mem = arena.allocate(4, 4)
        assertEquals(0x5678L, engine.call("f", mem.address(), 0x12345678))
        arena.close()
    }

    // --- Large buffer access (DOOM-scale offsets) ---

    @Test
    fun accessLargeOffset() {
        val module = buildModule("test") {
            val params = createFunction("f", listOf(Param("base", Type.I64), Param("offset", Type.I32)), Type.I32)
            appendBlock("entry")
            val ext = zext(params[1], Type.I64)
            val addr = add(params[0], ext)
            ret(load(Type.I32, addr))
            finalizeFunction()
        }
        val engine = JitEngine(org.kgen.target.x86.codegen.X86CodeGenerator())
        engine.addModule(module)
        val arena = java.lang.foreign.Arena.ofShared()
        val mem = arena.allocate(5 * 1024 * 1024, 8) // 5MB like DOOM
        val offset = 0x4278c0 // DOOM's guard offset
        mem.set(java.lang.foreign.ValueLayout.JAVA_INT, offset.toLong(), 0xCAFE)
        assertEquals(0xCAFEL, engine.call("f", mem.address(), offset.toLong()))
        arena.close()
    }

    // --- Multiple stores then loads (register pressure) ---

    @Test
    fun multipleStoresAndLoads() {
        val module = buildModule("test") {
            val params = createFunction("f", listOf(Param("ptr", Type.I64), Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            // Store a at [ptr], b at [ptr+4]
            store(params[1], params[0])
            val ptr4 = add(params[0], Constant.I64(4))
            store(params[2], ptr4)
            // Load both back and add
            val loadedA = load(Type.I32, params[0])
            val loadedB = load(Type.I32, ptr4)
            val sum = add(loadedA, loadedB)
            ret(sum)
            finalizeFunction()
        }
        val engine = JitEngine(org.kgen.target.x86.codegen.X86CodeGenerator())
        engine.addModule(module)

        val arena = java.lang.foreign.Arena.ofShared()
        val mem = arena.allocate(8, 8)
        assertEquals(30L, engine.call("f", mem.address(), 10, 20))
        arena.close()
    }
}
