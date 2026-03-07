package org.kgen.jit

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.kgen.backend.x86.codegen.X86CodeGenerator
import org.kgen.ir.*
import org.kgen.ir.build.IrBuilder
import org.kgen.ir.target.Target
import org.kgen.pass.ConstantFolding
import org.kgen.pass.DeadCodeElimination
import org.kgen.pass.PassPipeline
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.ValueLayout.*

@EnabledOnOs(OS.WINDOWS, OS.LINUX)
class JitEngineTest {

    private fun buildAddModule(): Module {
        val ir = IrBuilder("add_module", Target.x86_64())
        ir.createFunction("add", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
        ir.positionAtEnd(ir.appendBlock("entry"))
        val a = Parameter("a", Type.I64, 0)
        val b = Parameter("b", Type.I64, 1)
        val sum = ir.add(a, b)
        ir.ret(sum)
        ir.finalizeFunction()
        return ir.build()
    }

    private fun buildConstantModule(name: String, funcName: String, value: Long): Module {
        val ir = IrBuilder(name, Target.x86_64())
        ir.createFunction(funcName, emptyList(), Type.I64)
        ir.positionAtEnd(ir.appendBlock("entry"))
        ir.ret(Constant.I64(value))
        ir.finalizeFunction()
        return ir.build()
    }

    private fun buildMulModule(): Module {
        val ir = IrBuilder("mul_module", Target.x86_64())
        ir.createFunction("mul", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
        ir.positionAtEnd(ir.appendBlock("entry"))
        val a = Parameter("a", Type.I64, 0)
        val b = Parameter("b", Type.I64, 1)
        val product = ir.mul(a, b)
        ir.ret(product)
        ir.finalizeFunction()
        return ir.build()
    }

    @Test
    fun createEngine() {
        val jit = JitEngine(X86CodeGenerator())
        assertNotNull(jit)
        jit.close()
    }

    @Test
    fun addModuleAndLookup() {
        val jit = JitEngine(X86CodeGenerator())
        val module = buildAddModule()
        val jitModule = jit.addModule(module)

        assertNotNull(jitModule)
        assertEquals("add_module", jitModule.name)
        assertTrue(jitModule.symbolNames().contains("add"))

        val sym = jit.lookup("add")
        assertNotNull(sym)
        assertTrue(sym!!.address != 0L)
        assertEquals(JitSymbol.SymbolSource.JIT, sym.source)

        jit.close()
    }

    @Test
    fun callAddFunction() {
        val jit = JitEngine(X86CodeGenerator())
        jit.addModule(buildAddModule())

        val result = jit.call("add", 3, 4)
        assertEquals(7L, result)

        jit.close()
    }

    @Test
    fun callWithMethodHandle() {
        val jit = JitEngine(X86CodeGenerator())
        jit.addModule(buildAddModule())

        val desc = FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, JAVA_LONG)
        val handle = jit.handle("add", desc)
        val result = handle.invoke(10L, 20L) as Long
        assertEquals(30L, result)

        jit.close()
    }

    @Test
    fun callConstantFunction() {
        val jit = JitEngine(X86CodeGenerator())
        jit.addModule(buildConstantModule("m", "getFortyTwo", 42))

        val result = jit.call("getFortyTwo")
        assertEquals(42L, result)

        jit.close()
    }

    @Test
    fun multipleModules() {
        val jit = JitEngine(X86CodeGenerator())
        jit.addModule(buildAddModule())
        jit.addModule(buildMulModule())

        assertEquals(7L, jit.call("add", 3, 4))
        assertEquals(12L, jit.call("mul", 3, 4))
        assertEquals(2, jit.modules().size)

        jit.close()
    }

    @Test
    fun moduleShadowing() {
        val jit = JitEngine(X86CodeGenerator())
        jit.addModule(buildConstantModule("v1", "getValue", 100))
        assertEquals(100L, jit.call("getValue"))

        jit.addModule(buildConstantModule("v2", "getValue", 200))
        assertEquals(200L, jit.call("getValue"))

        jit.close()
    }

    @Test
    fun lookupMissing() {
        val jit = JitEngine(X86CodeGenerator())
        assertNull(jit.lookup("nonexistent"))
        jit.close()
    }

    @Test
    fun handleMissingThrows() {
        val jit = JitEngine(X86CodeGenerator())
        assertThrows(IllegalArgumentException::class.java) {
            jit.handle("nonexistent", FunctionDescriptor.of(JAVA_LONG))
        }
        jit.close()
    }

    @Test
    fun symbolNames() {
        val jit = JitEngine(X86CodeGenerator())
        jit.addModule(buildAddModule())
        jit.addModule(buildMulModule())
        val names = jit.symbolNames()
        assertTrue(names.contains("add"))
        assertTrue(names.contains("mul"))
        jit.close()
    }

    @Test
    fun closeReleasesMemory() {
        val jit = JitEngine(X86CodeGenerator())
        jit.addModule(buildAddModule())
        jit.close()
        assertTrue(jit.modules().isEmpty())
        assertTrue(jit.symbolNames().isEmpty())
    }

    @Test
    fun jitModuleLookup() {
        val jit = JitEngine(X86CodeGenerator())
        val jitModule = jit.addModule(buildAddModule())
        val sym = jitModule.lookup("add")
        assertNotNull(sym)
        assertEquals("add", sym!!.name)
        jit.close()
    }

    @Test
    fun addObjectFileDirect() {
        val gen = X86CodeGenerator()
        // Pre-compile with correct target triple for current platform
        val module = buildAddModule().copy(targetTriple = JitEngine.hostTriple)
        val obj = gen.generateObjectFile(module)
        val jit = JitEngine(gen)
        val jitModule = jit.addObjectFile("precompiled", obj)
        assertEquals("precompiled", jitModule.name)
        assertEquals(7L, jit.call("add", 3, 4))
        jit.close()
    }

    @Test
    fun resolverMap() {
        val jit = JitEngine(X86CodeGenerator())
        jit.addResolver(SymbolResolver.map(mapOf("external_fn" to 0xDEADBEEFL)))
        val sym = jit.lookup("external_fn")
        assertNotNull(sym)
        assertEquals(0xDEADBEEFL, sym!!.address)
        assertEquals(JitSymbol.SymbolSource.HOST, sym.source)
        jit.close()
    }

    @Test
    fun lazyCompilation() {
        val jit = JitEngine(X86CodeGenerator())
        var compiled = false
        jit.addLazy("lazyFunc") {
            compiled = true
            buildConstantModule("lazy", "lazyFunc", 99)
        }

        assertTrue(jit.symbolNames().contains("lazyFunc"))
        assertFalse(compiled)

        // Manually trigger materialization
        jit.materializeLazy("lazyFunc")
        assertTrue(compiled)

        val result = jit.call("lazyFunc")
        assertEquals(99L, result)
        jit.close()
    }

    @Test
    fun multipleCallsSameFunction() {
        val jit = JitEngine(X86CodeGenerator())
        jit.addModule(buildAddModule())

        for (i in 0L..10L) {
            assertEquals(i + 100L, jit.call("add", i, 100))
        }
        jit.close()
    }

    @Test
    fun jitSymbolData() {
        val sym = JitSymbol("test", 0x1000, 64, JitSymbol.SymbolSource.JIT)
        assertEquals("test", sym.name)
        assertEquals(0x1000L, sym.address)
        assertEquals(64L, sym.size)
        assertEquals(JitSymbol.SymbolSource.JIT, sym.source)
    }

    @Test
    fun jitSymbolSources() {
        assertEquals(4, JitSymbol.SymbolSource.entries.size)
    }

    @Test
    fun forCurrentPlatform() {
        val arch = System.getProperty("os.arch").lowercase()
        if (arch.contains("amd64") || arch.contains("x86_64")) {
            val jit = JitEngine.forCurrentPlatform()
            assertNotNull(jit)
            jit.close()
        }
    }

    @Test
    fun subtractFunction() {
        val ir = IrBuilder("sub_module", Target.x86_64())
        ir.createFunction("sub", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
        ir.positionAtEnd(ir.appendBlock("entry"))
        val a = Parameter("a", Type.I64, 0)
        val b = Parameter("b", Type.I64, 1)
        val diff = ir.sub(a, b)
        ir.ret(diff)
        ir.finalizeFunction()

        val jit = JitEngine(X86CodeGenerator())
        jit.addModule(ir.build())
        assertEquals(6L, jit.call("sub", 10, 4))
        assertEquals(-5L, jit.call("sub", 0, 5))
        jit.close()
    }

    @Test
    fun bitwiseOperations() {
        val ir = IrBuilder("bitwise", Target.x86_64())

        ir.createFunction("bitand", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
        ir.positionAtEnd(ir.appendBlock("entry"))
        val a1 = Parameter("a", Type.I64, 0)
        val b1 = Parameter("b", Type.I64, 1)
        ir.ret(ir.and(a1, b1))
        ir.finalizeFunction()

        ir.createFunction("bitor", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
        ir.positionAtEnd(ir.appendBlock("entry"))
        val a2 = Parameter("a", Type.I64, 0)
        val b2 = Parameter("b", Type.I64, 1)
        ir.ret(ir.or(a2, b2))
        ir.finalizeFunction()

        val jit = JitEngine(X86CodeGenerator())
        jit.addModule(ir.build())

        assertEquals(0x00FFL, jit.call("bitand", 0x0FFF, 0x00FF))
        assertEquals(0x0FFFL, jit.call("bitor", 0x0F00, 0x00FF))
        jit.close()
    }

    @Test
    fun multipleFunctionsInOneModule() {
        val ir = IrBuilder("multi", Target.x86_64())

        ir.createFunction("f1", emptyList(), Type.I64)
        ir.positionAtEnd(ir.appendBlock("entry"))
        ir.ret(Constant.I64(1))
        ir.finalizeFunction()

        ir.createFunction("f2", emptyList(), Type.I64)
        ir.positionAtEnd(ir.appendBlock("entry"))
        ir.ret(Constant.I64(2))
        ir.finalizeFunction()

        ir.createFunction("f3", emptyList(), Type.I64)
        ir.positionAtEnd(ir.appendBlock("entry"))
        ir.ret(Constant.I64(3))
        ir.finalizeFunction()

        val jit = JitEngine(X86CodeGenerator())
        jit.addModule(ir.build())

        assertEquals(1L, jit.call("f1"))
        assertEquals(2L, jit.call("f2"))
        assertEquals(3L, jit.call("f3"))
        jit.close()
    }

    @Test
    fun removeModule() {
        val jit = JitEngine(X86CodeGenerator())
        val m1 = jit.addModule(buildConstantModule("m1", "getValue", 100))
        assertEquals(100L, jit.call("getValue"))

        jit.removeModule(m1)
        assertNull(jit.lookup("getValue"))
        assertEquals(0, jit.modules().size)
        jit.close()
    }

    @Test
    fun removeModuleReexposesOlder() {
        val jit = JitEngine(X86CodeGenerator())
        jit.addModule(buildConstantModule("m1", "getValue", 100))
        val m2 = jit.addModule(buildConstantModule("m2", "getValue", 200))

        assertEquals(200L, jit.call("getValue"))
        jit.removeModule(m2)
        // m1's definition should be visible again
        assertEquals(100L, jit.call("getValue"))
        jit.close()
    }

    @Test
    fun optimizationPipeline() {
        val jit = JitEngine(X86CodeGenerator())
        val pipeline = PassPipeline()
        pipeline.add(ConstantFolding())
        pipeline.add(DeadCodeElimination())
        jit.setOptimizationPipeline(pipeline)

        jit.addModule(buildAddModule())
        assertEquals(7L, jit.call("add", 3, 4))
        jit.close()
    }

    @Test
    fun hostTripleContainsArch() {
        val triple = JitEngine.hostTriple
        assertTrue(
            triple.contains("x86_64") || triple.contains("aarch64") || triple.contains("arm64"),
            "Host triple should contain arch: $triple"
        )
    }

    @Test
    fun hostTripleContainsOS() {
        val triple = JitEngine.hostTriple
        assertTrue(
            triple.contains("windows") || triple.contains("linux") || triple.contains("macos"),
            "Host triple should contain OS: $triple"
        )
    }
}
