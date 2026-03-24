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
 * JIT execution tests for global variable access (.data section).
 * Globals are accessed via RIP-relative addressing in the JIT code.
 */
@EnabledOnOs(OS.WINDOWS, OS.LINUX)
class JitCodegenGlobalTest {

    private fun buildModule(block: ModuleBuilder.() -> Unit): Module {
        val builder = ModuleBuilder("test", Target.x86_64())
        builder.block()
        return builder.build()
    }

    @Test
    fun readConstantGlobal() {
        val module = buildModule {
            addGlobal("val", Type.I32, Constant.I32(42), isConstant = true, linkage = Linkage.INTERNAL)
            createFunction("f", emptyList(), Type.I32)
            appendBlock("entry")
            ret(load(Type.I32, GlobalRef("val", Type.I32)))
            finalizeFunction()
        }
        val engine = JitEngine(X86CodeGenerator())
        engine.addModule(module)
        assertEquals(42L, engine.call("f"))
    }

    @Test
    fun readWriteMutableGlobal() {
        val module = buildModule {
            addGlobal("counter", Type.I32, Constant.I32(0), isConstant = false, linkage = Linkage.INTERNAL)
            createFunction("inc", emptyList(), Type.I32)
            appendBlock("entry")
            val globalPtr = GlobalRef("counter", Type.I32)
            val current = load(Type.I32, globalPtr)
            val next = add(current, Constant.I32(1))
            store(next, globalPtr)
            ret(next)
            finalizeFunction()
        }
        val engine = JitEngine(X86CodeGenerator())
        engine.addModule(module)
        assertEquals(1L, engine.call("inc"))
        assertEquals(2L, engine.call("inc"))
        assertEquals(3L, engine.call("inc"))
    }

    @Test
    fun globalWithLargeInitialValue() {
        val module = buildModule {
            addGlobal("sp", Type.I32, Constant.I32(4659040), isConstant = false, linkage = Linkage.INTERNAL)
            createFunction("getSp", emptyList(), Type.I32)
            appendBlock("entry")
            ret(load(Type.I32, GlobalRef("sp", Type.I32)))
            finalizeFunction()

            val decParams = createFunction("decSp", listOf(Param("n", Type.I32)), Type.I32)
            appendBlock("entry")
            val globalPtr2 = GlobalRef("sp", Type.I32)
            val current = load(Type.I32, globalPtr2)
            val next = sub(current, decParams[0])
            store(next, globalPtr2)
            ret(next)
            finalizeFunction()
        }
        val engine = JitEngine(X86CodeGenerator())
        engine.addModule(module)
        assertEquals(4659040L, engine.call("getSp"))
        assertEquals(4659024L, engine.call("decSp", 16))
        assertEquals(4659024L, engine.call("getSp"))
    }

    @Test
    fun multipleGlobals() {
        val module = buildModule {
            addGlobal("a", Type.I32, Constant.I32(10), isConstant = false, linkage = Linkage.INTERNAL)
            addGlobal("b", Type.I32, Constant.I32(20), isConstant = false, linkage = Linkage.INTERNAL)
            createFunction("sum", emptyList(), Type.I32)
            appendBlock("entry")
            val va = load(Type.I32, GlobalRef("a", Type.I32))
            val vb = load(Type.I32, GlobalRef("b", Type.I32))
            ret(add(va, vb))
            finalizeFunction()
        }
        val engine = JitEngine(X86CodeGenerator())
        engine.addModule(module)
        assertEquals(30L, engine.call("sum"))
    }

    @Test
    fun manyGlobalsReadSpecific() {
        // DOOM has 16 globals — test that reading global_1 (second) gives the right value
        val module = buildModule {
            addGlobal("g0", Type.I32, Constant.I32(4659040), isConstant = false, linkage = Linkage.INTERNAL)
            addGlobal("g1", Type.I32, Constant.I32(0), isConstant = true, linkage = Linkage.INTERNAL)
            addGlobal("g2", Type.I32, Constant.I32(172), isConstant = true, linkage = Linkage.INTERNAL)
            addGlobal("g3", Type.I32, Constant.I32(174), isConstant = true, linkage = Linkage.INTERNAL)
            addGlobal("g4", Type.I32, Constant.I32(173), isConstant = true, linkage = Linkage.INTERNAL)
            addGlobal("g5", Type.I32, Constant.I32(175), isConstant = true, linkage = Linkage.INTERNAL)
            addGlobal("g6", Type.I32, Constant.I32(160), isConstant = true, linkage = Linkage.INTERNAL)
            addGlobal("g7", Type.I32, Constant.I32(161), isConstant = true, linkage = Linkage.INTERNAL)

            createFunction("readG0", emptyList(), Type.I32)
            appendBlock("entry")
            ret(load(Type.I32, GlobalRef("g0", Type.I32)))
            finalizeFunction()

            createFunction("readG1", emptyList(), Type.I32)
            appendBlock("entry")
            ret(load(Type.I32, GlobalRef("g1", Type.I32)))
            finalizeFunction()

            createFunction("readG2", emptyList(), Type.I32)
            appendBlock("entry")
            ret(load(Type.I32, GlobalRef("g2", Type.I32)))
            finalizeFunction()

            createFunction("readG7", emptyList(), Type.I32)
            appendBlock("entry")
            ret(load(Type.I32, GlobalRef("g7", Type.I32)))
            finalizeFunction()
        }
        val engine = JitEngine(X86CodeGenerator())
        engine.addModule(module)
        assertEquals(4659040L, engine.call("readG0"), "g0 = 4659040")
        assertEquals(0L, engine.call("readG1"), "g1 = 0")
        assertEquals(172L, engine.call("readG2"), "g2 = 172")
        assertEquals(161L, engine.call("readG7"), "g7 = 161")
    }

    @Test
    fun mutableGlobalAcrossCalls() {
        // Modify global in callee, read in caller
        val module = buildModule {
            addGlobal("counter", Type.I32, Constant.I32(0), isConstant = false, linkage = Linkage.INTERNAL)

            createFunction("inc", emptyList(), Type.Void)
            appendBlock("entry")
            val gref = GlobalRef("counter", Type.I32)
            val cur = load(Type.I32, gref)
            store(add(cur, Constant.I32(1)), gref)
            ret()
            finalizeFunction()

            createFunction("getAndInc", emptyList(), Type.I32)
            appendBlock("entry")
            val before = load(Type.I32, GlobalRef("counter", Type.I32))
            call("inc", emptyList(), Type.Void)
            val after = load(Type.I32, GlobalRef("counter", Type.I32))
            ret(sub(after, before))
            finalizeFunction()
        }
        val engine = JitEngine(X86CodeGenerator())
        engine.addModule(module)
        assertEquals(1L, engine.call("getAndInc"))
        assertEquals(1L, engine.call("getAndInc"))
    }

    @Test
    fun globalWithManyFunctions() {
        // Multiple functions sharing the same global — like DOOM
        val module = buildModule {
            addGlobal("sp", Type.I32, Constant.I32(1000), isConstant = false, linkage = Linkage.INTERNAL)

            createFunction("getSp", emptyList(), Type.I32)
            appendBlock("entry")
            ret(load(Type.I32, GlobalRef("sp", Type.I32)))
            finalizeFunction()

            val p1 = createFunction("push", listOf(Param("n", Type.I32)), Type.I32)
            appendBlock("entry")
            val gref = GlobalRef("sp", Type.I32)
            val old = load(Type.I32, gref)
            store(sub(old, p1[0]), gref)
            ret(old)
            finalizeFunction()

            val popParams = createFunction("pop", listOf(Param("n", Type.I32)), Type.I32)
            appendBlock("entry")
            val gref2 = GlobalRef("sp", Type.I32)
            val cur = load(Type.I32, gref2)
            store(add(cur, popParams[0]), gref2)
            ret(cur)
            finalizeFunction()
        }
        val engine = JitEngine(X86CodeGenerator())
        engine.addModule(module)
        assertEquals(1000L, engine.call("getSp"))
        assertEquals(1000L, engine.call("push", 16))
        assertEquals(984L, engine.call("getSp"))
        assertEquals(984L, engine.call("pop", 16))
        assertEquals(1000L, engine.call("getSp"))
    }

    @Test
    fun globalWithManyFunctionsLargeText() {
        // Simulate DOOM: 100 functions + globals to stress relocation offsets
        val module = buildModule {
            addGlobal("g0", Type.I32, Constant.I32(999), isConstant = false, linkage = Linkage.INTERNAL)
            addGlobal("g1", Type.I32, Constant.I32(0), isConstant = true, linkage = Linkage.INTERNAL)

            // Generate 100 dummy functions to make a large text section
            for (idx in 0 until 100) {
                val params = createFunction("dummy_$idx", listOf(Param("x", Type.I64)), Type.I64)
                appendBlock("entry")
                ret(add(params[0], Constant.I64(idx.toLong())))
                finalizeFunction()
            }

            // The actual test function: read g1 (should be 0, not 999)
            createFunction("readG1", emptyList(), Type.I32)
            appendBlock("entry")
            ret(load(Type.I32, GlobalRef("g1", Type.I32)))
            finalizeFunction()

            createFunction("readG0", emptyList(), Type.I32)
            appendBlock("entry")
            ret(load(Type.I32, GlobalRef("g0", Type.I32)))
            finalizeFunction()
        }
        val engine = JitEngine(X86CodeGenerator())
        engine.addModule(module)
        assertEquals(0L, engine.call("readG1"), "g1 should be 0 (not 999 from g0)")
        assertEquals(999L, engine.call("readG0"), "g0 should be 999")
    }

    @Test
    fun globalUsedAsMemoryOffset() {
        val module = buildModule {
            addGlobal("offset", Type.I32, Constant.I32(100), isConstant = true, linkage = Linkage.INTERNAL)
            val params = createFunction("f", listOf(Param("base", Type.I64)), Type.I32)
            appendBlock("entry")
            val offset = load(Type.I32, GlobalRef("offset", Type.I32))
            val extOffset = zext(offset, Type.I64)
            val addr = add(params[0], extOffset)
            ret(load(Type.I32, addr))
            finalizeFunction()
        }
        val engine = JitEngine(X86CodeGenerator())
        engine.addModule(module)
        val arena = java.lang.foreign.Arena.ofShared()
        val mem = arena.allocate(256, 8)
        mem.set(java.lang.foreign.ValueLayout.JAVA_INT, 100, 999)
        assertEquals(999L, engine.call("f", mem.address()))
        arena.close()
    }
}
