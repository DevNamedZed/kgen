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
 * Reproduces the exact IR pattern of DOOM's func_122 to isolate
 * a codegen crash that only manifests in DOOM-scale modules.
 *
 * func_122 is a leaf function that takes (context: I64, p0: I32)
 * and does multiple loads/stores at various offsets from p0 in WASM memory.
 */
@EnabledOnOs(OS.WINDOWS, OS.LINUX)
class JitCodegenFunc122Test {

    private fun buildModule(block: ModuleBuilder.() -> Unit): Module {
        val builder = ModuleBuilder("test", Target.x86_64())
        builder.block()
        return builder.build()
    }

    private fun createContext(memoryBase: Long, memorySize: Long): Pair<java.lang.foreign.Arena, java.lang.foreign.MemorySegment> {
        val arena = java.lang.foreign.Arena.ofShared()
        val ctx = arena.allocate(40, 8)
        ctx.set(java.lang.foreign.ValueLayout.JAVA_LONG, 0, memoryBase)
        ctx.set(java.lang.foreign.ValueLayout.JAVA_LONG, 8, memorySize)
        return arena to ctx
    }

    /**
     * Reproduces func_122's exact IR pattern (after Mem2Reg):
     * - Multiple loads/stores at p0+{60, 4, 40, 24, 20, 44, 16}
     * - Each access: zext(p0) → add(context.memBase, zext) → add(offset) → load/store
     * - Branch based on bit test
     * - Two return paths
     */
    @Test
    fun func122PatternSmallModule() {
        val module = buildModule {
            val params = createFunction("func_122", listOf(Param("ctx", Type.I64), Param("p0", Type.I32)), Type.I32)
            appendBlock("entry")
            buildFunc122Body(params)
            finalizeFunction()
        }
        verifyFunc122(module)
    }

    @Test
    fun func122PatternLargeModule() {
        val module = buildModule {
            // 500 dummy functions to simulate DOOM scale
            for (idx in 0 until 500) {
                val funcParams = createFunction("fn_$idx", listOf(Param("ctx", Type.I64), Param("x", Type.I32)), Type.I32)
                appendBlock("entry")
                ret(add(funcParams[1], Constant.I32(idx)))
                finalizeFunction()
            }

            val params = createFunction("func_122", listOf(Param("ctx", Type.I64), Param("p0", Type.I32)), Type.I32)
            appendBlock("entry")
            buildFunc122Body(params)
            finalizeFunction()
        }
        verifyFunc122(module)
    }

    @Test
    fun func122PatternDoomScale() {
        val module = buildModule {
            // 829 functions like DOOM
            for (idx in 0 until 829) {
                val funcParams = createFunction("fn_$idx", listOf(Param("ctx", Type.I64), Param("x", Type.I32)), Type.I32)
                appendBlock("entry")
                ret(add(funcParams[1], Constant.I32(idx)))
                finalizeFunction()
            }

            val params = createFunction("func_122", listOf(Param("ctx", Type.I64), Param("p0", Type.I32)), Type.I32)
            appendBlock("entry")
            buildFunc122Body(params)
            finalizeFunction()
        }
        verifyFunc122(module)
    }

    private fun ModuleBuilder.buildFunc122Body(params: List<Value>) {
        val ctx = params[0]
        val p0 = params[1]

        // entry block: load mem[p0+60], compute (val-1)|val, store back, check bit 8
        val ext4 = zext(p0, Type.I64)
        val ctxAdd6 = add(ctx, Constant.I64(0))
        val memBase7 = load(Type.I64, ctxAdd6)
        val addr8 = add(memBase7, ext4)
        val addr9 = add(addr8, Constant.I64(60))
        val val10 = load(Type.I32, addr9)

        val sub11 = add(val10, Constant.I32(-1))
        val or13 = or(sub11, val10)

        // Store the or'd value back to p0+60
        val ext14 = zext(p0, Type.I64)
        val ctxAdd16 = add(ctx, Constant.I64(0))
        val memBase17 = load(Type.I64, ctxAdd16)
        val addr18 = add(memBase17, ext14)
        val addr19 = add(addr18, Constant.I64(60))
        store(or13, addr19)

        // Load from p0 (base), check bit 8
        val ext21 = zext(p0, Type.I64)
        val ctxAdd22 = add(ctx, Constant.I64(0))
        val memBase23 = load(Type.I64, ctxAdd22)
        val addr24 = add(memBase23, ext21)
        val val25 = load(Type.I32, addr24)

        val and26 = and(val25, Constant.I32(8))
        val cmp27 = icmp(ICmpPredicate.EQ, and26, Constant.I32(0))
        val ext28 = zext(cmp27, Type.I32)
        val cmp29 = icmp(ICmpPredicate.NE, ext28, Constant.I32(0))
        condBr(cmp29, "block_end_0", "br_if_cont_1")

        // br_if_cont_1: or(val25, 32), store to p0, return -1
        appendBlock("br_if_cont_1")
        val or32 = or(val25, Constant.I32(32))
        val ext33 = zext(p0, Type.I64)
        val ctxAdd34 = add(ctx, Constant.I64(0))
        val memBase35 = load(Type.I64, ctxAdd34)
        val addr36 = add(memBase35, ext33)
        store(or32, addr36)
        ret(Constant.I32(-1))

        // block_end_0: many stores at various offsets, return 0
        appendBlock("block_end_0")

        // Store I64(0) at p0+4
        val ext38 = zext(p0, Type.I64)
        val ctxAdd40 = add(ctx, Constant.I64(0))
        val memBase41 = load(Type.I64, ctxAdd40)
        val addr42 = add(memBase41, ext38)
        val addr43 = add(addr42, Constant.I64(4))
        store(Constant.I64(0), addr43)

        // Load from p0+40, store to p0+24
        val ext46 = zext(p0, Type.I64)
        val ctxAdd48 = add(ctx, Constant.I64(0))
        val memBase49 = load(Type.I64, ctxAdd48)
        val addr50 = add(memBase49, ext46)
        val addr51 = add(addr50, Constant.I64(40))
        val val52 = load(Type.I32, addr51)

        val ext53 = zext(p0, Type.I64)
        val ctxAdd55 = add(ctx, Constant.I64(0))
        val memBase56 = load(Type.I64, ctxAdd55)
        val addr57 = add(memBase56, ext53)
        val addr58 = add(addr57, Constant.I64(24))
        store(val52, addr58)

        // Store val25 (from entry) to p0+20
        val ext61 = zext(p0, Type.I64)
        val ctxAdd63 = add(ctx, Constant.I64(0))
        val memBase64 = load(Type.I64, ctxAdd63)
        val addr65 = add(memBase64, ext61)
        val addr66 = add(addr65, Constant.I64(20))
        store(val52, addr66)

        // Load from p0+44, add to val52, store to p0+16
        val ext70 = zext(p0, Type.I64)
        val ctxAdd72 = add(ctx, Constant.I64(0))
        val memBase73 = load(Type.I64, ctxAdd72)
        val addr74 = add(memBase73, ext70)
        val addr75 = add(addr74, Constant.I64(44))
        val val76 = load(Type.I32, addr75)
        val sum77 = add(val52, val76)

        val ext78 = zext(p0, Type.I64)
        val ctxAdd80 = add(ctx, Constant.I64(0))
        val memBase81 = load(Type.I64, ctxAdd80)
        val addr82 = add(memBase81, ext78)
        val addr83 = add(addr82, Constant.I64(16))
        store(sum77, addr83)

        ret(Constant.I32(0))
    }

    private fun verifyFunc122(module: Module) {
        val engine = JitEngine(X86CodeGenerator())
        engine.addModule(module)

        val memArena = java.lang.foreign.Arena.ofShared()
        val mem = memArena.allocate(5 * 1024 * 1024, 8)

        // Set up memory at offset 0x427848 (DOOM's actual p0 value)
        val base = 0x427848
        mem.set(java.lang.foreign.ValueLayout.JAVA_INT, (base + 60).toLong(), 0x0F)  // val at p0+60: has bit 3 set, bit 8 NOT set
        mem.set(java.lang.foreign.ValueLayout.JAVA_INT, (base).toLong(), 0x00)         // val at p0: bit 8 not set → goes to block_end_0
        mem.set(java.lang.foreign.ValueLayout.JAVA_INT, (base + 40).toLong(), 100)     // val at p0+40
        mem.set(java.lang.foreign.ValueLayout.JAVA_INT, (base + 44).toLong(), 200)     // val at p0+44

        val (ctxArena, ctx) = createContext(mem.address(), 5L * 1024 * 1024)

        val result = engine.call("func_122", ctx.address(), base.toLong())
        assertEquals(0L, result, "func_122 should return 0 (bit 8 not set → block_end_0 path)")

        // Verify stores happened correctly
        val storedAt4 = mem.get(java.lang.foreign.ValueLayout.JAVA_LONG_UNALIGNED, (base + 4).toLong())
        assertEquals(0L, storedAt4, "p0+4 should be zeroed (I64 store)")

        val storedAt24 = mem.get(java.lang.foreign.ValueLayout.JAVA_INT, (base + 24).toLong())
        assertEquals(100, storedAt24, "p0+24 should have value from p0+40")

        val storedAt16 = mem.get(java.lang.foreign.ValueLayout.JAVA_INT, (base + 16).toLong())
        assertEquals(300, storedAt16, "p0+16 should have sum of p0+40 and p0+44")

        ctxArena.close()
        memArena.close()
    }
}
