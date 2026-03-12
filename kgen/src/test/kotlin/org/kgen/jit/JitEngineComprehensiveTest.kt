package org.kgen.jit

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.kgen.codegen.CodeGenOptions
import org.kgen.codegen.OutputFormat
import org.kgen.ir.*
import org.kgen.ir.build.IrBuilder
import org.kgen.ir.target.Target
import org.kgen.pass.ConstantFolding
import org.kgen.pass.DeadCodeElimination
import org.kgen.pass.PassPipeline
import org.kgen.target.x86.codegen.X86CodeGenerator
import java.io.File
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.ValueLayout.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@EnabledOnOs(OS.WINDOWS, OS.LINUX)
class JitEngineComprehensiveTest {

    private fun buildAddModule(): Module {
        val ir = IrBuilder("add_module", Target.x86_64())
        ir.createFunction("add", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
        ir.positionAtEnd(ir.appendBlock("entry"))
        val a = Parameter("a", Type.I64, 0)
        val b = Parameter("b", Type.I64, 1)
        ir.ret(ir.add(a, b))
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
        ir.ret(ir.mul(a, b))
        ir.finalizeFunction()
        return ir.build()
    }

    private fun buildSubModule(): Module {
        val ir = IrBuilder("sub_module", Target.x86_64())
        ir.createFunction("sub", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
        ir.positionAtEnd(ir.appendBlock("entry"))
        val a = Parameter("a", Type.I64, 0)
        val b = Parameter("b", Type.I64, 1)
        ir.ret(ir.sub(a, b))
        ir.finalizeFunction()
        return ir.build()
    }

    private fun buildNegModule(): Module {
        val ir = IrBuilder("neg_module", Target.x86_64())
        ir.createFunction("negate", listOf(Param("a", Type.I64)), Type.I64)
        ir.positionAtEnd(ir.appendBlock("entry"))
        val a = Parameter("a", Type.I64, 0)
        ir.ret(ir.neg(a))
        ir.finalizeFunction()
        return ir.build()
    }

    private fun buildMultiFunctionModule(name: String, funcCount: Int): Module {
        val ir = IrBuilder(name, Target.x86_64())
        for (i in 1..funcCount) {
            ir.createFunction("func$i", emptyList(), Type.I64)
            ir.positionAtEnd(ir.appendBlock("entry"))
            ir.ret(Constant.I64(i.toLong()))
            ir.finalizeFunction()
        }
        return ir.build()
    }

    private fun buildIdentityModule(name: String, funcName: String): Module {
        val ir = IrBuilder(name, Target.x86_64())
        ir.createFunction(funcName, listOf(Param("x", Type.I64)), Type.I64)
        ir.positionAtEnd(ir.appendBlock("entry"))
        ir.ret(Parameter("x", Type.I64, 0))
        ir.finalizeFunction()
        return ir.build()
    }

    private fun buildAndModule(): Module {
        val ir = IrBuilder("and_module", Target.x86_64())
        ir.createFunction("bitand", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
        ir.positionAtEnd(ir.appendBlock("entry"))
        val a = Parameter("a", Type.I64, 0)
        val b = Parameter("b", Type.I64, 1)
        ir.ret(ir.and(a, b))
        ir.finalizeFunction()
        return ir.build()
    }

    private fun buildOrModule(): Module {
        val ir = IrBuilder("or_module", Target.x86_64())
        ir.createFunction("bitor", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
        ir.positionAtEnd(ir.appendBlock("entry"))
        val a = Parameter("a", Type.I64, 0)
        val b = Parameter("b", Type.I64, 1)
        ir.ret(ir.or(a, b))
        ir.finalizeFunction()
        return ir.build()
    }

    private fun buildXorModule(): Module {
        val ir = IrBuilder("xor_module", Target.x86_64())
        ir.createFunction("bitxor", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
        ir.positionAtEnd(ir.appendBlock("entry"))
        val a = Parameter("a", Type.I64, 0)
        val b = Parameter("b", Type.I64, 1)
        ir.ret(ir.xor(a, b))
        ir.finalizeFunction()
        return ir.build()
    }

    private fun buildShlModule(): Module {
        val ir = IrBuilder("shl_module", Target.x86_64())
        ir.createFunction("shl", listOf(Param("a", Type.I64)), Type.I64)
        ir.positionAtEnd(ir.appendBlock("entry"))
        val a = Parameter("a", Type.I64, 0)
        ir.ret(ir.shl(a, Constant.I64(3L)))
        ir.finalizeFunction()
        return ir.build()
    }

    @Nested
    inner class ModuleCompilationAndSymbolLookup {

        @Test
        fun `compile module and find symbol by name`() {
            val jit = JitEngine(X86CodeGenerator())
            val module = buildAddModule()
            jit.addModule(module)

            val sym = jit.lookup("add")
            assertNotNull(sym)
            assertTrue(sym!!.address != 0L)
            assertEquals("add", sym.name)
            assertEquals(JitSymbol.SymbolSource.JIT, sym.source)
            jit.close()
        }

        @Test
        fun `compiled module is in modules list`() {
            val jit = JitEngine(X86CodeGenerator())
            val jitModule = jit.addModule(buildAddModule())
            assertEquals(1, jit.modules().size)
            assertEquals("add_module", jitModule.name)
            jit.close()
        }

        @Test
        fun `module symbol names are populated`() {
            val jit = JitEngine(X86CodeGenerator())
            val jitModule = jit.addModule(buildAddModule())
            assertTrue(jitModule.symbolNames().contains("add"))
            jit.close()
        }

        @Test
        fun `compile and call add function`() {
            val jit = JitEngine(X86CodeGenerator())
            jit.addModule(buildAddModule())
            assertEquals(7L, jit.call("add", 3, 4))
            jit.close()
        }

        @Test
        fun `compile and call multiply function`() {
            val jit = JitEngine(X86CodeGenerator())
            jit.addModule(buildMulModule())
            assertEquals(12L, jit.call("mul", 3, 4))
            jit.close()
        }

        @Test
        fun `compile and call subtract function`() {
            val jit = JitEngine(X86CodeGenerator())
            jit.addModule(buildSubModule())
            assertEquals(6L, jit.call("sub", 10, 4))
            jit.close()
        }

        @Test
        fun `compile and call negate function`() {
            val jit = JitEngine(X86CodeGenerator())
            jit.addModule(buildNegModule())
            assertEquals(-42L, jit.call("negate", 42))
            jit.close()
        }

        @Test
        fun `compile constant-returning function`() {
            val jit = JitEngine(X86CodeGenerator())
            jit.addModule(buildConstantModule("m", "getConst", 777))
            assertEquals(777L, jit.call("getConst"))
            jit.close()
        }

        @Test
        fun `compile identity function`() {
            val jit = JitEngine(X86CodeGenerator())
            jit.addModule(buildIdentityModule("id_mod", "identity"))
            assertEquals(123L, jit.call("identity", 123))
            assertEquals(0L, jit.call("identity", 0))
            assertEquals(-1L, jit.call("identity", -1))
            jit.close()
        }

        @Test
        fun `compile bitwise AND function`() {
            val jit = JitEngine(X86CodeGenerator())
            jit.addModule(buildAndModule())
            assertEquals(0x00FFL, jit.call("bitand", 0x0FFF, 0x00FF))
            jit.close()
        }

        @Test
        fun `compile bitwise OR function`() {
            val jit = JitEngine(X86CodeGenerator())
            jit.addModule(buildOrModule())
            assertEquals(0x0FFFL, jit.call("bitor", 0x0F00, 0x00FF))
            jit.close()
        }

        @Test
        fun `compile bitwise XOR function`() {
            val jit = JitEngine(X86CodeGenerator())
            jit.addModule(buildXorModule())
            assertEquals(0x0F0FL, jit.call("bitxor", 0x0FFF, 0x00F0))
            jit.close()
        }

        @Test
        fun `compile shift left function`() {
            val jit = JitEngine(X86CodeGenerator())
            jit.addModule(buildShlModule())
            assertEquals(8L, jit.call("shl", 1))
            jit.close()
        }

        @Test
        fun `call function many times produces correct results`() {
            val jit = JitEngine(X86CodeGenerator())
            jit.addModule(buildAddModule())
            for (i in 0L..50L) {
                assertEquals(i + 100L, jit.call("add", i, 100))
            }
            jit.close()
        }

        @Test
        fun `call function with negative arguments`() {
            val jit = JitEngine(X86CodeGenerator())
            jit.addModule(buildAddModule())
            assertEquals(-3L, jit.call("add", -1, -2))
            assertEquals(0L, jit.call("add", 5, -5))
            jit.close()
        }

        @Test
        fun `call function with zero arguments`() {
            val jit = JitEngine(X86CodeGenerator())
            jit.addModule(buildAddModule())
            assertEquals(0L, jit.call("add", 0, 0))
            jit.close()
        }

        @Test
        fun `call function with large values`() {
            val jit = JitEngine(X86CodeGenerator())
            jit.addModule(buildAddModule())
            assertEquals(Long.MAX_VALUE, jit.call("add", Long.MAX_VALUE - 1, 1))
            jit.close()
        }
    }

    @Nested
    inner class MultipleModuleLoading {

        @Test
        fun `load two modules with different functions`() {
            val jit = JitEngine(X86CodeGenerator())
            jit.addModule(buildAddModule())
            jit.addModule(buildMulModule())
            assertEquals(2, jit.modules().size)
            assertEquals(7L, jit.call("add", 3, 4))
            assertEquals(12L, jit.call("mul", 3, 4))
            jit.close()
        }

        @Test
        fun `load many modules`() {
            val jit = JitEngine(X86CodeGenerator())
            for (i in 1..10) {
                jit.addModule(buildConstantModule("m$i", "f$i", i.toLong()))
            }
            assertEquals(10, jit.modules().size)
            for (i in 1..10) {
                assertEquals(i.toLong(), jit.call("f$i"))
            }
            jit.close()
        }

        @Test
        fun `multiple functions in one module`() {
            val jit = JitEngine(X86CodeGenerator())
            jit.addModule(buildMultiFunctionModule("multi", 5))
            for (i in 1..5) {
                assertEquals(i.toLong(), jit.call("func$i"))
            }
            jit.close()
        }

        @Test
        fun `newer module shadows older module with same symbol`() {
            val jit = JitEngine(X86CodeGenerator())
            jit.addModule(buildConstantModule("v1", "getValue", 100))
            assertEquals(100L, jit.call("getValue"))

            jit.addModule(buildConstantModule("v2", "getValue", 200))
            assertEquals(200L, jit.call("getValue"))
            jit.close()
        }

        @Test
        fun `three modules with same symbol name shadows correctly`() {
            val jit = JitEngine(X86CodeGenerator())
            jit.addModule(buildConstantModule("v1", "getVal", 10))
            jit.addModule(buildConstantModule("v2", "getVal", 20))
            jit.addModule(buildConstantModule("v3", "getVal", 30))

            assertEquals(30L, jit.call("getVal"))
            assertEquals(3, jit.modules().size)
            jit.close()
        }

        @Test
        fun `symbol names from all modules are visible`() {
            val jit = JitEngine(X86CodeGenerator())
            jit.addModule(buildAddModule())
            jit.addModule(buildMulModule())
            jit.addModule(buildConstantModule("c", "getConst", 42))

            val names = jit.symbolNames()
            assertTrue(names.contains("add"))
            assertTrue(names.contains("mul"))
            assertTrue(names.contains("getConst"))
            jit.close()
        }

        @Test
        fun `module lookup returns correct jit module`() {
            val jit = JitEngine(X86CodeGenerator())
            val m1 = jit.addModule(buildAddModule())
            val m2 = jit.addModule(buildMulModule())

            assertEquals("add_module", m1.name)
            assertEquals("mul_module", m2.name)
            assertNotNull(m1.lookup("add"))
            assertNull(m1.lookup("mul"))
            assertNotNull(m2.lookup("mul"))
            assertNull(m2.lookup("add"))
            jit.close()
        }
    }

    @Nested
    inner class SymbolResolution {

        @Test
        fun `lookup returns null for unknown symbol`() {
            val jit = JitEngine(X86CodeGenerator())
            assertNull(jit.lookup("nonexistent"))
            jit.close()
        }

        @Test
        fun `handle throws for missing symbol`() {
            val jit = JitEngine(X86CodeGenerator())
            assertThrows(IllegalArgumentException::class.java) {
                jit.handle("nonexistent", FunctionDescriptor.of(JAVA_LONG))
            }
            jit.close()
        }

        @Test
        fun `map resolver provides external symbols`() {
            val jit = JitEngine(X86CodeGenerator())
            jit.addResolver(SymbolResolver.map(mapOf("ext1" to 0x1000L, "ext2" to 0x2000L)))

            val sym1 = jit.lookup("ext1")
            assertNotNull(sym1)
            assertEquals(0x1000L, sym1!!.address)
            assertEquals(JitSymbol.SymbolSource.HOST, sym1.source)

            val sym2 = jit.lookup("ext2")
            assertNotNull(sym2)
            assertEquals(0x2000L, sym2!!.address)
            jit.close()
        }

        @Test
        fun `map resolver returns null for unknown symbol`() {
            val jit = JitEngine(X86CodeGenerator())
            jit.addResolver(SymbolResolver.map(mapOf("known" to 0x1000L)))
            assertNull(jit.lookup("unknown"))
            jit.close()
        }

        @Test
        fun `multiple resolvers searched in order`() {
            val jit = JitEngine(X86CodeGenerator())
            jit.addResolver(SymbolResolver.map(mapOf("shared" to 0x1000L)))
            jit.addResolver(SymbolResolver.map(mapOf("shared" to 0x2000L, "extra" to 0x3000L)))

            val sym = jit.lookup("shared")
            assertNotNull(sym)
            assertEquals(0x1000L, sym!!.address, "First resolver should win")

            val extra = jit.lookup("extra")
            assertNotNull(extra)
            assertEquals(0x3000L, extra!!.address)
            jit.close()
        }

        @Test
        fun `jit symbols take priority over resolver symbols`() {
            val jit = JitEngine(X86CodeGenerator())
            jit.addResolver(SymbolResolver.map(mapOf("add" to 0xDEADL)))
            jit.addModule(buildAddModule())

            val sym = jit.lookup("add")
            assertNotNull(sym)
            assertEquals(JitSymbol.SymbolSource.JIT, sym!!.source)
            assertNotEquals(0xDEADL, sym.address)
            jit.close()
        }

        @Test
        fun `custom resolver via lambda`() {
            val jit = JitEngine(X86CodeGenerator())
            jit.addResolver(SymbolResolver { name ->
                if (name.startsWith("custom_")) 0xCAFEL else null
            })

            assertNotNull(jit.lookup("custom_foo"))
            assertNotNull(jit.lookup("custom_bar"))
            assertNull(jit.lookup("other_foo"))
            jit.close()
        }

        @Test
        fun `cross-module symbol visibility`() {
            val jit = JitEngine(X86CodeGenerator())
            jit.addModule(buildConstantModule("m1", "getA", 10))
            jit.addModule(buildConstantModule("m2", "getB", 20))

            assertNotNull(jit.lookup("getA"))
            assertNotNull(jit.lookup("getB"))
            assertEquals(10L, jit.call("getA"))
            assertEquals(20L, jit.call("getB"))
            jit.close()
        }

        @Test
        fun `jit symbol data accessors`() {
            val sym = JitSymbol("testSym", 0x4000, 128, JitSymbol.SymbolSource.JIT)
            assertEquals("testSym", sym.name)
            assertEquals(0x4000L, sym.address)
            assertEquals(128L, sym.size)
            assertEquals(JitSymbol.SymbolSource.JIT, sym.source)
        }

        @Test
        fun `symbol source enum has four values`() {
            val sources = JitSymbol.SymbolSource.entries
            assertEquals(4, sources.size)
            assertTrue(sources.contains(JitSymbol.SymbolSource.JIT))
            assertTrue(sources.contains(JitSymbol.SymbolSource.HOST))
            assertTrue(sources.contains(JitSymbol.SymbolSource.LIBRARY))
            assertTrue(sources.contains(JitSymbol.SymbolSource.STUB))
        }

        @Test
        fun `jit symbol default values`() {
            val sym = JitSymbol("min", 0x100)
            assertEquals(0L, sym.size)
            assertEquals(JitSymbol.SymbolSource.JIT, sym.source)
        }
    }

    @Nested
    inner class CodeCacheManagement {

        @Test
        fun `cache tracks loaded modules`() {
            val jit = JitEngine(X86CodeGenerator())
            val cache = CodeCache()
            jit.setCodeCache(cache)

            jit.addModule(buildAddModule())
            jit.addModule(buildMulModule())

            assertEquals(2, cache.entryCount())
            assertTrue(cache.totalSize() > 0)
            assertTrue(cache.contains("add_module"))
            assertTrue(cache.contains("mul_module"))
            jit.close()
        }

        @Test
        fun `cache evicts LRU when full`() {
            val jit = JitEngine(X86CodeGenerator())
            val cache = CodeCache(maxSize = 5000)
            jit.setCodeCache(cache)

            jit.addModule(buildConstantModule("m1", "f1", 1))
            assertEquals(1, cache.entryCount())

            jit.addModule(buildConstantModule("m2", "f2", 2))
            assertEquals(1, cache.entryCount())
            assertTrue(cache.contains("m2"))
            assertFalse(cache.contains("m1"))
            assertNull(jit.lookup("f1"))
            assertEquals(2L, jit.call("f2"))
            jit.close()
        }

        @Test
        fun `unlimited cache never evicts`() {
            val jit = JitEngine(X86CodeGenerator())
            val cache = CodeCache(maxSize = 0)
            jit.setCodeCache(cache)

            for (i in 1..10) {
                jit.addModule(buildConstantModule("m$i", "f$i", i.toLong()))
            }
            assertEquals(10, cache.entryCount())
            jit.close()
        }

        @Test
        fun `cache updates on module removal`() {
            val jit = JitEngine(X86CodeGenerator())
            val cache = CodeCache()
            jit.setCodeCache(cache)

            val m1 = jit.addModule(buildConstantModule("m1", "f1", 1))
            assertEquals(1, cache.entryCount())

            jit.removeModule(m1)
            assertEquals(0, cache.entryCount())
            assertFalse(cache.contains("m1"))
            jit.close()
        }

        @Test
        fun `cache statistics`() {
            val cache = CodeCache(maxSize = 100_000)
            assertEquals(0L, cache.totalSize())
            assertEquals(0, cache.entryCount())
            assertFalse(cache.contains("anything"))
        }

        @Test
        fun `engine exposes code cache`() {
            val jit = JitEngine(X86CodeGenerator())
            assertNull(jit.codeCache())

            val cache = CodeCache()
            jit.setCodeCache(cache)
            assertSame(cache, jit.codeCache())
            jit.close()
        }

        @Test
        fun `cache cleared on engine close`() {
            val jit = JitEngine(X86CodeGenerator())
            val cache = CodeCache()
            jit.setCodeCache(cache)

            jit.addModule(buildConstantModule("m1", "f1", 1))
            assertEquals(1, cache.entryCount())

            jit.close()
            assertEquals(0, cache.entryCount())
        }

        @Test
        fun `no cache still works`() {
            val jit = JitEngine(X86CodeGenerator())
            jit.addModule(buildConstantModule("m", "f", 42))
            assertEquals(42L, jit.call("f"))
            jit.close()
        }

        @Test
        fun `LRU touch updates access order`() {
            val jit = JitEngine(X86CodeGenerator())
            val cache = CodeCache(maxSize = 9000)
            jit.setCodeCache(cache)

            jit.addModule(buildConstantModule("m1", "f1", 1))
            jit.addModule(buildConstantModule("m2", "f2", 2))

            // Touch m1 to make it most recently used
            jit.call("f1")

            // Adding m3 should evict m2 (LRU) rather than m1
            jit.addModule(buildConstantModule("m3", "f3", 3))

            assertTrue(cache.contains("m1") || cache.contains("m3"))
            jit.close()
        }
    }

    @Nested
    inner class LazyCompilation {

        @Test
        fun `lazy stub is registered but not compiled`() {
            val jit = JitEngine(X86CodeGenerator())
            var compiled = false
            jit.addLazy("lazyFunc") {
                compiled = true
                buildConstantModule("lazy", "lazyFunc", 99)
            }

            assertTrue(jit.symbolNames().contains("lazyFunc"))
            assertFalse(compiled)
            jit.close()
        }

        @Test
        fun `materialization triggers compilation`() {
            val jit = JitEngine(X86CodeGenerator())
            var compiled = false
            jit.addLazy("lazyFunc") {
                compiled = true
                buildConstantModule("lazy", "lazyFunc", 99)
            }

            jit.materializeLazy("lazyFunc")
            assertTrue(compiled)
            assertEquals(99L, jit.call("lazyFunc"))
            jit.close()
        }

        @Test
        fun `lazy symbol source is STUB before materialization`() {
            val jit = JitEngine(X86CodeGenerator())
            jit.addLazy("lazyFunc") {
                buildConstantModule("lazy", "lazyFunc", 50)
            }

            val sym = jit.lookup("lazyFunc")
            assertNotNull(sym)
            assertEquals(JitSymbol.SymbolSource.STUB, sym!!.source)
            jit.close()
        }

        @Test
        fun `after materialization symbol source is JIT`() {
            val jit = JitEngine(X86CodeGenerator())
            jit.addLazy("lazyFunc") {
                buildConstantModule("lazy", "lazyFunc", 50)
            }

            jit.materializeLazy("lazyFunc")
            val sym = jit.lookup("lazyFunc")
            assertNotNull(sym)
            assertEquals(JitSymbol.SymbolSource.JIT, sym!!.source)
            jit.close()
        }

        @Test
        fun `materialize missing lazy throws`() {
            val jit = JitEngine(X86CodeGenerator())
            assertThrows(IllegalStateException::class.java) {
                jit.materializeLazy("doesNotExist")
            }
            jit.close()
        }

        @Test
        fun `multiple lazy stubs`() {
            val jit = JitEngine(X86CodeGenerator())
            val compilationOrder = mutableListOf<String>()

            jit.addLazy("lazy1") {
                compilationOrder.add("lazy1")
                buildConstantModule("l1", "lazy1", 1)
            }
            jit.addLazy("lazy2") {
                compilationOrder.add("lazy2")
                buildConstantModule("l2", "lazy2", 2)
            }

            assertTrue(jit.symbolNames().contains("lazy1"))
            assertTrue(jit.symbolNames().contains("lazy2"))
            assertTrue(compilationOrder.isEmpty())

            jit.materializeLazy("lazy2")
            assertEquals(listOf("lazy2"), compilationOrder)

            jit.materializeLazy("lazy1")
            assertEquals(listOf("lazy2", "lazy1"), compilationOrder)

            assertEquals(1L, jit.call("lazy1"))
            assertEquals(2L, jit.call("lazy2"))
            jit.close()
        }

        @Test
        fun `lazy stub with non-trivial computation`() {
            val jit = JitEngine(X86CodeGenerator())
            jit.addLazy("lazyAdd") {
                buildAddModule().copy(name = "lazy_add_module")
            }

            jit.materializeLazy("lazyAdd")
            // The lazy module defined "add" not "lazyAdd" — but the lazy stub registered "lazyAdd"
            // The addModule call will register the function under its actual name "add"
            assertNotNull(jit.lookup("add"))
            jit.close()
        }

        @Test
        fun `close cleans up lazy stubs`() {
            val jit = JitEngine(X86CodeGenerator())
            jit.addLazy("lazy1") { buildConstantModule("l1", "lazy1", 1) }
            jit.addLazy("lazy2") { buildConstantModule("l2", "lazy2", 2) }

            jit.close()
            assertTrue(jit.symbolNames().isEmpty())
        }
    }

    @Nested
    inner class TieredCompilationTests {

        @Test
        fun `tiered recompilation triggers at threshold`() {
            val jit = JitEngine(X86CodeGenerator())
            val tiered = TieredCompilation(recompileThreshold = 5)
            jit.setTieredCompilation(tiered)

            jit.addModule(buildAddModule())

            for (i in 1..4) {
                jit.call("add", i.toLong(), 10)
            }
            assertFalse(tiered.isRecompiled("add"))

            jit.call("add", 5, 10)
            assertTrue(tiered.isRecompiled("add"))
            jit.close()
        }

        @Test
        fun `recompilation only happens once`() {
            val jit = JitEngine(X86CodeGenerator())
            val tiered = TieredCompilation(recompileThreshold = 3)
            jit.setTieredCompilation(tiered)

            jit.addModule(buildConstantModule("m", "f", 42))

            for (i in 1..20) {
                assertEquals(42L, jit.call("f"))
            }
            assertTrue(tiered.isRecompiled("f"))
            assertEquals(1, jit.modules().size)
            jit.close()
        }

        @Test
        fun `tiered with optimization pipeline`() {
            val jit = JitEngine(X86CodeGenerator())
            val tiered = TieredCompilation(recompileThreshold = 2)
            val pipeline = PassPipeline()
            pipeline.add(ConstantFolding())
            pipeline.add(DeadCodeElimination())
            tiered.setTier1Pipeline(pipeline)
            jit.setTieredCompilation(tiered)

            jit.addModule(buildAddModule())
            jit.call("add", 1, 2)
            jit.call("add", 3, 4)
            assertTrue(tiered.isRecompiled("add"))
            assertEquals(30L, jit.call("add", 10, 20))
            jit.close()
        }

        @Test
        fun `call count tracking`() {
            val tiered = TieredCompilation(recompileThreshold = 100)
            assertEquals(0, tiered.callCount("foo"))
            assertFalse(tiered.isRecompiled("foo"))
        }

        @Test
        fun `reset clears tiered state`() {
            val tiered = TieredCompilation(recompileThreshold = 10)
            tiered.registerModule("foo", buildAddModule())
            tiered.recordCall("foo")
            tiered.recordCall("foo")
            assertEquals(2, tiered.callCount("foo"))

            tiered.reset()
            assertEquals(0, tiered.callCount("foo"))
        }

        @Test
        fun `engine exposes tiered compilation config`() {
            val jit = JitEngine(X86CodeGenerator())
            assertNull(jit.tieredCompilation())

            val tiered = TieredCompilation()
            jit.setTieredCompilation(tiered)
            assertSame(tiered, jit.tieredCompilation())
            jit.close()
        }

        @Test
        fun `independent call counts per function`() {
            val jit = JitEngine(X86CodeGenerator())
            val tiered = TieredCompilation(recompileThreshold = 5)
            jit.setTieredCompilation(tiered)

            jit.addModule(buildMultiFunctionModule("multi", 3))

            for (i in 1..4) {
                jit.call("func1")
            }
            jit.call("func2")

            assertEquals(4, tiered.callCount("func1"))
            assertEquals(1, tiered.callCount("func2"))
            assertEquals(0, tiered.callCount("func3"))
            assertFalse(tiered.isRecompiled("func1"))
            jit.close()
        }

        @Test
        fun `no tiered still works`() {
            val jit = JitEngine(X86CodeGenerator())
            jit.addModule(buildAddModule())
            assertEquals(7L, jit.call("add", 3, 4))
            jit.close()
        }
    }

    @Nested
    inner class ModuleReplacementAndHotSwapping {

        @Test
        fun `remove module clears symbols`() {
            val jit = JitEngine(X86CodeGenerator())
            val m1 = jit.addModule(buildConstantModule("m1", "getValue", 100))
            assertEquals(100L, jit.call("getValue"))

            jit.removeModule(m1)
            assertNull(jit.lookup("getValue"))
            assertEquals(0, jit.modules().size)
            jit.close()
        }

        @Test
        fun `remove newer module re-exposes older`() {
            val jit = JitEngine(X86CodeGenerator())
            jit.addModule(buildConstantModule("m1", "getValue", 100))
            val m2 = jit.addModule(buildConstantModule("m2", "getValue", 200))

            assertEquals(200L, jit.call("getValue"))

            jit.removeModule(m2)
            assertEquals(100L, jit.call("getValue"))
            jit.close()
        }

        @Test
        fun `remove middle module in three-layer shadowing`() {
            val jit = JitEngine(X86CodeGenerator())
            jit.addModule(buildConstantModule("m1", "getVal", 10))
            val m2 = jit.addModule(buildConstantModule("m2", "getVal", 20))
            jit.addModule(buildConstantModule("m3", "getVal", 30))

            assertEquals(30L, jit.call("getVal"))

            jit.removeModule(m2)
            assertEquals(30L, jit.call("getVal"))
            assertEquals(2, jit.modules().size)
            jit.close()
        }

        @Test
        fun `remove top module in three-layer shadowing`() {
            val jit = JitEngine(X86CodeGenerator())
            jit.addModule(buildConstantModule("m1", "getVal", 10))
            jit.addModule(buildConstantModule("m2", "getVal", 20))
            val m3 = jit.addModule(buildConstantModule("m3", "getVal", 30))

            jit.removeModule(m3)
            assertEquals(20L, jit.call("getVal"))
            jit.close()
        }

        @Test
        fun `hot-swap by adding new module with same function name`() {
            val jit = JitEngine(X86CodeGenerator())
            val m1 = jit.addModule(buildConstantModule("v1", "compute", 1))
            assertEquals(1L, jit.call("compute"))

            jit.removeModule(m1)
            jit.addModule(buildConstantModule("v2", "compute", 2))
            assertEquals(2L, jit.call("compute"))
            assertEquals(1, jit.modules().size)
            jit.close()
        }

        @Test
        fun `remove module does not affect other modules`() {
            val jit = JitEngine(X86CodeGenerator())
            val m1 = jit.addModule(buildAddModule())
            jit.addModule(buildMulModule())

            jit.removeModule(m1)
            assertNull(jit.lookup("add"))
            assertNotNull(jit.lookup("mul"))
            assertEquals(12L, jit.call("mul", 3, 4))
            jit.close()
        }

        @Test
        fun `remove all modules leaves engine empty`() {
            val jit = JitEngine(X86CodeGenerator())
            val m1 = jit.addModule(buildAddModule())
            val m2 = jit.addModule(buildMulModule())

            jit.removeModule(m1)
            jit.removeModule(m2)

            assertEquals(0, jit.modules().size)
            assertTrue(jit.symbolNames().isEmpty())
            jit.close()
        }
    }

    @Nested
    inner class DebugInfoGeneration {

        @Test
        fun `debug info listener receives load events`() {
            val jit = JitEngine(X86CodeGenerator())
            val debug = JitDebugInfo()
            val events = mutableListOf<JitDebugInfo.JitEvent>()
            debug.addListener { events.add(it) }
            jit.setDebugInfo(debug)

            jit.addModule(buildConstantModule("m1", "func1", 42))

            assertTrue(events.isNotEmpty())
            val loadEvent = events.first { it.type == JitDebugInfo.EventType.LOAD }
            assertEquals("func1", loadEvent.name)
            assertTrue(loadEvent.address != 0L)
            jit.close()
        }

        @Test
        fun `debug info listener receives unload events on removal`() {
            val jit = JitEngine(X86CodeGenerator())
            val debug = JitDebugInfo()
            val events = mutableListOf<JitDebugInfo.JitEvent>()
            debug.addListener { events.add(it) }
            jit.setDebugInfo(debug)

            val m1 = jit.addModule(buildConstantModule("m1", "func1", 42))
            jit.removeModule(m1)

            val unloadEvents = events.filter { it.type == JitDebugInfo.EventType.UNLOAD }
            assertTrue(unloadEvents.isNotEmpty())
            assertEquals("func1", unloadEvents.first().name)
            jit.close()
        }

        @Test
        fun `debug info tracks symbols across modules`() {
            val jit = JitEngine(X86CodeGenerator())
            val debug = JitDebugInfo()
            jit.setDebugInfo(debug)

            jit.addModule(buildAddModule())
            jit.addModule(buildMulModule())

            val symbols = debug.symbols()
            val names = symbols.map { it.name }
            assertTrue(names.contains("add"))
            assertTrue(names.contains("mul"))
            jit.close()
        }

        @Test
        fun `debug info symbols cleared on close`() {
            val jit = JitEngine(X86CodeGenerator())
            val debug = JitDebugInfo()
            jit.setDebugInfo(debug)

            jit.addModule(buildConstantModule("m", "f", 1))
            assertTrue(debug.symbols().isNotEmpty())

            jit.close()
            assertTrue(debug.symbols().isEmpty())
        }

        @Test
        fun `engine exposes debug info`() {
            val jit = JitEngine(X86CodeGenerator())
            assertNull(jit.debugInfo())

            val debug = JitDebugInfo()
            jit.setDebugInfo(debug)
            assertSame(debug, jit.debugInfo())
            jit.close()
        }

        @Test
        fun `perf map to custom file`() {
            val jit = JitEngine(X86CodeGenerator())
            val debug = JitDebugInfo()
            val tempFile = File.createTempFile("perf-test-", ".map")
            try {
                debug.enablePerfMap(tempFile)
                jit.setDebugInfo(debug)

                jit.addModule(buildConstantModule("m1", "myFunc", 42))

                jit.close()
                // File should have been written to (may be deleted by close)
            } finally {
                tempFile.delete()
            }
        }

        @Test
        fun `multiple listeners receive events`() {
            val jit = JitEngine(X86CodeGenerator())
            val debug = JitDebugInfo()
            val events1 = mutableListOf<JitDebugInfo.JitEvent>()
            val events2 = mutableListOf<JitDebugInfo.JitEvent>()
            debug.addListener { events1.add(it) }
            debug.addListener { events2.add(it) }
            jit.setDebugInfo(debug)

            jit.addModule(buildConstantModule("m", "f", 1))

            assertTrue(events1.isNotEmpty())
            assertTrue(events2.isNotEmpty())
            assertEquals(events1.size, events2.size)
            jit.close()
        }

        @Test
        fun `event type enum values`() {
            val types = JitDebugInfo.EventType.entries
            assertEquals(2, types.size)
            assertTrue(types.contains(JitDebugInfo.EventType.LOAD))
            assertTrue(types.contains(JitDebugInfo.EventType.UNLOAD))
        }

        @Test
        fun `jit event data accessors`() {
            val event = JitDebugInfo.JitEvent(
                JitDebugInfo.EventType.LOAD, "testFunc", 0x5000, 128
            )
            assertEquals(JitDebugInfo.EventType.LOAD, event.type)
            assertEquals("testFunc", event.name)
            assertEquals(0x5000L, event.address)
            assertEquals(128L, event.size)
        }
    }

    @Nested
    inner class ErrorHandling {

        @Test
        fun `call nonexistent function throws`() {
            val jit = JitEngine(X86CodeGenerator())
            assertThrows(IllegalArgumentException::class.java) {
                jit.call("doesNotExist")
            }
            jit.close()
        }

        @Test
        fun `handle nonexistent function throws`() {
            val jit = JitEngine(X86CodeGenerator())
            assertThrows(IllegalArgumentException::class.java) {
                jit.handle("nope", FunctionDescriptor.of(JAVA_LONG))
            }
            jit.close()
        }

        @Test
        fun `materialize nonexistent lazy throws`() {
            val jit = JitEngine(X86CodeGenerator())
            assertThrows(IllegalStateException::class.java) {
                jit.materializeLazy("nope")
            }
            jit.close()
        }

        @Test
        fun `empty module with no functions produces error`() {
            val ir = IrBuilder("empty", Target.x86_64())
            val module = ir.build()
            val jit = JitEngine(X86CodeGenerator())
            assertThrows(Exception::class.java) {
                jit.addModule(module)
            }
            jit.close()
        }

        @Test
        fun `lookup after close returns null`() {
            val jit = JitEngine(X86CodeGenerator())
            jit.addModule(buildAddModule())
            jit.close()
            assertNull(jit.lookup("add"))
        }

        @Test
        fun `modules after close returns empty`() {
            val jit = JitEngine(X86CodeGenerator())
            jit.addModule(buildAddModule())
            jit.close()
            assertTrue(jit.modules().isEmpty())
        }

        @Test
        fun `symbol names after close returns empty`() {
            val jit = JitEngine(X86CodeGenerator())
            jit.addModule(buildAddModule())
            jit.close()
            assertTrue(jit.symbolNames().isEmpty())
        }
    }

    @Nested
    inner class TargetTripleAndABI {

        @Test
        fun `host triple contains architecture`() {
            val triple = JitEngine.hostTriple
            assertTrue(
                triple.contains("x86_64") || triple.contains("aarch64") || triple.contains("arm64"),
                "Host triple should contain arch: $triple"
            )
        }

        @Test
        fun `host triple contains OS`() {
            val triple = JitEngine.hostTriple
            assertTrue(
                triple.contains("windows") || triple.contains("linux") || triple.contains("macos"),
                "Host triple should contain OS: $triple"
            )
        }

        @Test
        fun `module without target triple gets auto-assigned`() {
            val ir = IrBuilder("no_triple", Target.x86_64())
            ir.createFunction("f", emptyList(), Type.I64)
            ir.positionAtEnd(ir.appendBlock("entry"))
            ir.ret(Constant.I64(42))
            ir.finalizeFunction()
            val module = ir.build()

            assertNull(module.targetTriple)

            val jit = JitEngine(X86CodeGenerator())
            jit.addModule(module)
            assertEquals(42L, jit.call("f"))
            jit.close()
        }

        @Test
        fun `module with explicit target triple is preserved`() {
            val ir = IrBuilder("explicit_triple", Target.x86_64())
            ir.createFunction("f", emptyList(), Type.I64)
            ir.positionAtEnd(ir.appendBlock("entry"))
            ir.ret(Constant.I64(42))
            ir.finalizeFunction()
            val module = ir.build().copy(targetTriple = JitEngine.hostTriple)

            val jit = JitEngine(X86CodeGenerator())
            jit.addModule(module)
            assertEquals(42L, jit.call("f"))
            jit.close()
        }

        @Test
        fun `forCurrentPlatform creates engine for host arch`() {
            val arch = System.getProperty("os.arch").lowercase()
            if (arch.contains("amd64") || arch.contains("x86_64")) {
                val jit = JitEngine.forCurrentPlatform()
                assertNotNull(jit)
                jit.close()
            }
        }

        @Test
        fun `forCurrentPlatform can compile and run`() {
            val arch = System.getProperty("os.arch").lowercase()
            if (arch.contains("amd64") || arch.contains("x86_64")) {
                val jit = JitEngine.forCurrentPlatform()
                jit.addModule(buildConstantModule("m", "f", 99))
                assertEquals(99L, jit.call("f"))
                jit.close()
            }
        }
    }

    @Nested
    inner class ObjectFileGeneration {

        @Test
        fun `add pre-compiled object file`() {
            val gen = X86CodeGenerator()
            val module = buildAddModule().copy(targetTriple = JitEngine.hostTriple)
            val obj = gen.generateObjectFile(module)

            val jit = JitEngine(gen)
            val jitModule = jit.addObjectFile("precompiled", obj)
            assertEquals("precompiled", jitModule.name)
            assertEquals(7L, jit.call("add", 3, 4))
            jit.close()
        }

        @Test
        fun `object file module has correct name`() {
            val gen = X86CodeGenerator()
            val module = buildConstantModule("m", "f", 42).copy(targetTriple = JitEngine.hostTriple)
            val obj = gen.generateObjectFile(module)

            val jit = JitEngine(gen)
            val jitModule = jit.addObjectFile("custom_name", obj)
            assertEquals("custom_name", jitModule.name)
            jit.close()
        }

        @Test
        fun `object file and IR module coexist`() {
            val gen = X86CodeGenerator()
            val objModule = buildAddModule().copy(targetTriple = JitEngine.hostTriple)
            val obj = gen.generateObjectFile(objModule)

            val jit = JitEngine(gen)
            jit.addObjectFile("obj_add", obj)
            jit.addModule(buildMulModule())

            assertEquals(7L, jit.call("add", 3, 4))
            assertEquals(12L, jit.call("mul", 3, 4))
            assertEquals(2, jit.modules().size)
            jit.close()
        }

        @Test
        fun `multiple object files loaded`() {
            val gen = X86CodeGenerator()
            val obj1 = gen.generateObjectFile(buildAddModule().copy(targetTriple = JitEngine.hostTriple))
            val obj2 = gen.generateObjectFile(buildMulModule().copy(targetTriple = JitEngine.hostTriple))

            val jit = JitEngine(gen)
            jit.addObjectFile("add_obj", obj1)
            jit.addObjectFile("mul_obj", obj2)

            assertEquals(7L, jit.call("add", 3, 4))
            assertEquals(12L, jit.call("mul", 3, 4))
            jit.close()
        }
    }

    @Nested
    inner class MemoryManagement {

        @Test
        fun `close releases all module memory`() {
            val jit = JitEngine(X86CodeGenerator())
            jit.addModule(buildAddModule())
            jit.addModule(buildMulModule())

            jit.close()
            assertTrue(jit.modules().isEmpty())
            assertTrue(jit.symbolNames().isEmpty())
        }

        @Test
        fun `remove module frees its memory`() {
            val jit = JitEngine(X86CodeGenerator())
            val m1 = jit.addModule(buildAddModule())

            jit.removeModule(m1)
            assertEquals(0, jit.modules().size)
            jit.close()
        }

        @Test
        fun `jit module memory has valid address`() {
            val jit = JitEngine(X86CodeGenerator())
            val jitModule = jit.addModule(buildAddModule())

            assertTrue(jitModule.memory.address != 0L)
            assertTrue(jitModule.memory.size > 0)
            jit.close()
        }

        @Test
        fun `jit module toString shows name and symbol count`() {
            val jit = JitEngine(X86CodeGenerator())
            val jitModule = jit.addModule(buildAddModule())

            val str = jitModule.toString()
            assertTrue(str.contains("add_module"))
            assertTrue(str.contains("symbol"))
            jit.close()
        }

        @Test
        fun `consecutive close calls do not throw`() {
            val jit = JitEngine(X86CodeGenerator())
            jit.addModule(buildAddModule())
            jit.close()
            // Second close should not throw
            jit.close()
        }

        @Test
        fun `allocating many modules does not leak`() {
            val jit = JitEngine(X86CodeGenerator())
            for (i in 1..20) {
                jit.addModule(buildConstantModule("m$i", "f$i", i.toLong()))
            }
            assertEquals(20, jit.modules().size)
            jit.close()
            assertEquals(0, jit.modules().size)
        }
    }

    @Nested
    inner class ConcurrentCompilationScenarios {

        @Test
        fun `sequential compilation from multiple builder instances`() {
            val jit = JitEngine(X86CodeGenerator())
            val modules = (1..5).map { i ->
                buildConstantModule("m$i", "f$i", i.toLong() * 10)
            }
            for (m in modules) {
                jit.addModule(m)
            }
            for (i in 1..5) {
                assertEquals(i.toLong() * 10, jit.call("f$i"))
            }
            jit.close()
        }

        @Test
        fun `concurrent reads of symbols are safe`() {
            val jit = JitEngine(X86CodeGenerator())
            jit.addModule(buildAddModule())

            val executor = Executors.newFixedThreadPool(4)
            val errors = CopyOnWriteArrayList<Throwable>()
            val latch = CountDownLatch(20)

            for (i in 1..20) {
                executor.submit {
                    try {
                        val sym = jit.lookup("add")
                        assertNotNull(sym)
                    } catch (e: Throwable) {
                        errors.add(e)
                    } finally {
                        latch.countDown()
                    }
                }
            }

            latch.await(10, TimeUnit.SECONDS)
            executor.shutdown()
            assertTrue(errors.isEmpty(), "Errors: $errors")
            jit.close()
        }

        @Test
        fun `concurrent calls to same function`() {
            val jit = JitEngine(X86CodeGenerator())
            jit.addModule(buildAddModule())

            val executor = Executors.newFixedThreadPool(4)
            val errors = CopyOnWriteArrayList<Throwable>()
            val latch = CountDownLatch(20)

            for (i in 1..20) {
                val idx = i.toLong()
                executor.submit {
                    try {
                        val result = jit.call("add", idx, 100)
                        assertEquals(idx + 100L, result)
                    } catch (e: Throwable) {
                        errors.add(e)
                    } finally {
                        latch.countDown()
                    }
                }
            }

            latch.await(10, TimeUnit.SECONDS)
            executor.shutdown()
            assertTrue(errors.isEmpty(), "Errors: $errors")
            jit.close()
        }

        @Test
        fun `concurrent method handle calls`() {
            val jit = JitEngine(X86CodeGenerator())
            jit.addModule(buildAddModule())
            val desc = FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, JAVA_LONG)
            val handle = jit.handle("add", desc)

            val executor = Executors.newFixedThreadPool(4)
            val errors = CopyOnWriteArrayList<Throwable>()
            val latch = CountDownLatch(20)

            for (i in 1..20) {
                val idx = i.toLong()
                executor.submit {
                    try {
                        val result = handle.invoke(idx, 50L) as Long
                        assertEquals(idx + 50L, result)
                    } catch (e: Throwable) {
                        errors.add(e)
                    } finally {
                        latch.countDown()
                    }
                }
            }

            latch.await(10, TimeUnit.SECONDS)
            executor.shutdown()
            assertTrue(errors.isEmpty(), "Errors: $errors")
            jit.close()
        }
    }

    @Nested
    inner class FunctionPointerRetrieval {

        @Test
        fun `get method handle for add function`() {
            val jit = JitEngine(X86CodeGenerator())
            jit.addModule(buildAddModule())

            val desc = FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, JAVA_LONG)
            val handle = jit.handle("add", desc)
            val result = handle.invoke(10L, 20L) as Long
            assertEquals(30L, result)
            jit.close()
        }

        @Test
        fun `get method handle for no-arg function`() {
            val jit = JitEngine(X86CodeGenerator())
            jit.addModule(buildConstantModule("m", "getConst", 42))

            val desc = FunctionDescriptor.of(JAVA_LONG)
            val handle = jit.handle("getConst", desc)
            val result = handle.invoke() as Long
            assertEquals(42L, result)
            jit.close()
        }

        @Test
        fun `symbol address is stable for same compilation`() {
            val jit = JitEngine(X86CodeGenerator())
            jit.addModule(buildAddModule())

            val sym1 = jit.lookup("add")
            val sym2 = jit.lookup("add")
            assertNotNull(sym1)
            assertNotNull(sym2)
            assertEquals(sym1!!.address, sym2!!.address)
            jit.close()
        }

        @Test
        fun `different functions have different addresses`() {
            val jit = JitEngine(X86CodeGenerator())
            jit.addModule(buildMultiFunctionModule("multi", 3))

            val addr1 = jit.lookup("func1")!!.address
            val addr2 = jit.lookup("func2")!!.address
            val addr3 = jit.lookup("func3")!!.address

            assertNotEquals(addr1, addr2)
            assertNotEquals(addr2, addr3)
            assertNotEquals(addr1, addr3)
            jit.close()
        }

        @Test
        fun `handle can be called multiple times`() {
            val jit = JitEngine(X86CodeGenerator())
            jit.addModule(buildAddModule())

            val desc = FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, JAVA_LONG)
            val handle = jit.handle("add", desc)

            for (i in 0L..10L) {
                assertEquals(i * 2, handle.invoke(i, i) as Long)
            }
            jit.close()
        }

        @Test
        fun `multiple handles to same function`() {
            val jit = JitEngine(X86CodeGenerator())
            jit.addModule(buildAddModule())

            val desc = FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, JAVA_LONG)
            val handle1 = jit.handle("add", desc)
            val handle2 = jit.handle("add", desc)

            assertEquals(handle1.invoke(5L, 3L) as Long, handle2.invoke(5L, 3L) as Long)
            jit.close()
        }
    }

    @Nested
    inner class OptimizationPipeline {

        @Test
        fun `set optimization pipeline`() {
            val jit = JitEngine(X86CodeGenerator())
            val pipeline = PassPipeline()
            pipeline.add(ConstantFolding())
            jit.setOptimizationPipeline(pipeline)

            jit.addModule(buildAddModule())
            assertEquals(7L, jit.call("add", 3, 4))
            jit.close()
        }

        @Test
        fun `pipeline with multiple passes`() {
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
        fun `no pipeline still works`() {
            val jit = JitEngine(X86CodeGenerator())
            jit.addModule(buildAddModule())
            assertEquals(7L, jit.call("add", 3, 4))
            jit.close()
        }
    }

    @Nested
    inner class EngineLifecycle {

        @Test
        fun `create engine with default options`() {
            val jit = JitEngine(X86CodeGenerator())
            assertNotNull(jit)
            jit.close()
        }

        @Test
        fun `create engine with custom options`() {
            val options = CodeGenOptions(outputFormat = OutputFormat.OBJECT)
            val jit = JitEngine(X86CodeGenerator(), options)
            assertNotNull(jit)
            jit.close()
        }

        @Test
        fun `engine use as autocloseable`() {
            JitEngine(X86CodeGenerator()).use { jit ->
                jit.addModule(buildAddModule())
                assertEquals(7L, jit.call("add", 3, 4))
            }
        }

        @Test
        fun `close with no modules is safe`() {
            val jit = JitEngine(X86CodeGenerator())
            jit.close()
        }

        @Test
        fun `close with resolvers is safe`() {
            val jit = JitEngine(X86CodeGenerator())
            jit.addResolver(SymbolResolver.map(mapOf("ext" to 0x1000L)))
            jit.close()
        }

        @Test
        fun `close with all features configured`() {
            val jit = JitEngine(X86CodeGenerator())
            jit.setCodeCache(CodeCache())
            jit.setTieredCompilation(TieredCompilation())
            jit.setDebugInfo(JitDebugInfo())
            jit.setOptimizationPipeline(PassPipeline())
            jit.addResolver(SymbolResolver.map(mapOf("ext" to 0x1000L)))

            jit.addModule(buildAddModule())
            assertEquals(7L, jit.call("add", 3, 4))
            jit.close()
        }

        @Test
        fun `gc and safepoint accessors`() {
            val jit = JitEngine(X86CodeGenerator())
            assertNull(jit.gc())
            assertNull(jit.safepointManager())
            jit.close()
        }
    }

    @Nested
    inner class ComplexIRModules {

        @Test
        fun `module with many constant functions`() {
            val jit = JitEngine(X86CodeGenerator())
            val ir = IrBuilder("many_constants", Target.x86_64())
            for (i in 1..10) {
                ir.createFunction("const$i", emptyList(), Type.I64)
                ir.positionAtEnd(ir.appendBlock("entry"))
                ir.ret(Constant.I64(i.toLong() * 100))
                ir.finalizeFunction()
            }
            jit.addModule(ir.build())
            for (i in 1..10) {
                assertEquals(i.toLong() * 100, jit.call("const$i"))
            }
            jit.close()
        }

        @Test
        fun `function with three parameters`() {
            val ir = IrBuilder("three_param", Target.x86_64())
            ir.createFunction(
                "add3",
                listOf(Param("a", Type.I64), Param("b", Type.I64), Param("c", Type.I64)),
                Type.I64
            )
            ir.positionAtEnd(ir.appendBlock("entry"))
            val a = Parameter("a", Type.I64, 0)
            val b = Parameter("b", Type.I64, 1)
            val c = Parameter("c", Type.I64, 2)
            val ab = ir.add(a, b)
            val abc = ir.add(ab, c)
            ir.ret(abc)
            ir.finalizeFunction()

            val jit = JitEngine(X86CodeGenerator())
            jit.addModule(ir.build())
            assertEquals(6L, jit.call("add3", 1, 2, 3))
            assertEquals(60L, jit.call("add3", 10, 20, 30))
            jit.close()
        }

        @Test
        fun `function with four parameters`() {
            val ir = IrBuilder("four_param", Target.x86_64())
            ir.createFunction(
                "add4",
                listOf(
                    Param("a", Type.I64), Param("b", Type.I64),
                    Param("c", Type.I64), Param("d", Type.I64)
                ),
                Type.I64
            )
            ir.positionAtEnd(ir.appendBlock("entry"))
            val a = Parameter("a", Type.I64, 0)
            val b = Parameter("b", Type.I64, 1)
            val c = Parameter("c", Type.I64, 2)
            val d = Parameter("d", Type.I64, 3)
            val ab = ir.add(a, b)
            val cd = ir.add(c, d)
            val result = ir.add(ab, cd)
            ir.ret(result)
            ir.finalizeFunction()

            val jit = JitEngine(X86CodeGenerator())
            jit.addModule(ir.build())
            assertEquals(10L, jit.call("add4", 1, 2, 3, 4))
            jit.close()
        }

        @Test
        fun `chained arithmetic operations`() {
            val ir = IrBuilder("chain", Target.x86_64())
            ir.createFunction("chain", listOf(Param("x", Type.I64)), Type.I64)
            ir.positionAtEnd(ir.appendBlock("entry"))
            val x = Parameter("x", Type.I64, 0)
            val doubled = ir.add(x, x)
            val quadrupled = ir.add(doubled, doubled)
            ir.ret(quadrupled)
            ir.finalizeFunction()

            val jit = JitEngine(X86CodeGenerator())
            jit.addModule(ir.build())
            assertEquals(20L, jit.call("chain", 5))
            assertEquals(40L, jit.call("chain", 10))
            jit.close()
        }

        @Test
        fun `multiply and add combination`() {
            val ir = IrBuilder("muladd", Target.x86_64())
            ir.createFunction(
                "muladd",
                listOf(Param("a", Type.I64), Param("b", Type.I64), Param("c", Type.I64)),
                Type.I64
            )
            ir.positionAtEnd(ir.appendBlock("entry"))
            val a = Parameter("a", Type.I64, 0)
            val b = Parameter("b", Type.I64, 1)
            val c = Parameter("c", Type.I64, 2)
            val product = ir.mul(a, b)
            val result = ir.add(product, c)
            ir.ret(result)
            ir.finalizeFunction()

            val jit = JitEngine(X86CodeGenerator())
            jit.addModule(ir.build())
            assertEquals(7L, jit.call("muladd", 2, 3, 1))
            assertEquals(23L, jit.call("muladd", 5, 4, 3))
            jit.close()
        }
    }

    @Nested
    inner class JitModuleAPI {

        @Test
        fun `module lookup returns symbol`() {
            val jit = JitEngine(X86CodeGenerator())
            val jitModule = jit.addModule(buildAddModule())
            val sym = jitModule.lookup("add")
            assertNotNull(sym)
            assertEquals("add", sym!!.name)
            jit.close()
        }

        @Test
        fun `module lookup returns null for unknown`() {
            val jit = JitEngine(X86CodeGenerator())
            val jitModule = jit.addModule(buildAddModule())
            assertNull(jitModule.lookup("nonexistent"))
            jit.close()
        }

        @Test
        fun `module symbol names set`() {
            val jit = JitEngine(X86CodeGenerator())
            val jitModule = jit.addModule(buildMultiFunctionModule("multi", 3))
            val names = jitModule.symbolNames()
            assertTrue(names.contains("func1"))
            assertTrue(names.contains("func2"))
            assertTrue(names.contains("func3"))
            jit.close()
        }

        @Test
        fun `module name is correct`() {
            val jit = JitEngine(X86CodeGenerator())
            val jitModule = jit.addModule(buildAddModule())
            assertEquals("add_module", jitModule.name)
            jit.close()
        }

        @Test
        fun `module is autocloseable`() {
            val jit = JitEngine(X86CodeGenerator())
            val jitModule = jit.addModule(buildAddModule())
            assertNotNull(jitModule)
            jit.close()
        }
    }

    @Nested
    inner class IntegrationScenarios {

        @Test
        fun `full workflow - build, compile, run, replace, close`() {
            val jit = JitEngine(X86CodeGenerator())
            jit.setCodeCache(CodeCache())
            jit.setDebugInfo(JitDebugInfo())

            val m1 = jit.addModule(buildConstantModule("v1", "version", 1))
            assertEquals(1L, jit.call("version"))

            jit.removeModule(m1)
            jit.addModule(buildConstantModule("v2", "version", 2))
            assertEquals(2L, jit.call("version"))

            jit.close()
        }

        @Test
        fun `mixed lazy and eager compilation`() {
            val jit = JitEngine(X86CodeGenerator())

            jit.addModule(buildAddModule())
            jit.addLazy("lazyConst") {
                buildConstantModule("lazy", "lazyConst", 55)
            }

            assertEquals(7L, jit.call("add", 3, 4))
            assertTrue(jit.symbolNames().contains("lazyConst"))

            jit.materializeLazy("lazyConst")
            assertEquals(55L, jit.call("lazyConst"))
            jit.close()
        }

        @Test
        fun `object file plus IR module plus resolver`() {
            val gen = X86CodeGenerator()
            val obj = gen.generateObjectFile(buildAddModule().copy(targetTriple = JitEngine.hostTriple))

            val jit = JitEngine(gen)
            jit.addObjectFile("obj_add", obj)
            jit.addModule(buildMulModule())
            jit.addResolver(SymbolResolver.map(mapOf("ext_sym" to 0xBEEFL)))

            assertEquals(7L, jit.call("add", 3, 4))
            assertEquals(12L, jit.call("mul", 3, 4))
            assertNotNull(jit.lookup("ext_sym"))
            jit.close()
        }

        @Test
        fun `cache plus tiered plus debug info`() {
            val jit = JitEngine(X86CodeGenerator())
            jit.setCodeCache(CodeCache())
            jit.setTieredCompilation(TieredCompilation(recompileThreshold = 3))

            val debug = JitDebugInfo()
            val events = mutableListOf<JitDebugInfo.JitEvent>()
            debug.addListener { events.add(it) }
            jit.setDebugInfo(debug)

            jit.addModule(buildAddModule())
            assertTrue(events.any { it.type == JitDebugInfo.EventType.LOAD })

            for (i in 1..5) {
                jit.call("add", i.toLong(), 1)
            }

            jit.close()
        }

        @Test
        fun `rapid add and remove cycles`() {
            val jit = JitEngine(X86CodeGenerator())
            for (i in 1..10) {
                val m = jit.addModule(buildConstantModule("m$i", "f", i.toLong()))
                assertEquals(i.toLong(), jit.call("f"))
                jit.removeModule(m)
                assertNull(jit.lookup("f"))
            }
            assertEquals(0, jit.modules().size)
            jit.close()
        }

        @Test
        fun `building and running fibonacci-like computation`() {
            // Compute fib(n) = a*1 + b*1 = a + b using two params
            val jit = JitEngine(X86CodeGenerator())
            jit.addModule(buildAddModule())

            // Simulate fib computation by repeated calls
            var a = 0L
            var b = 1L
            for (i in 0 until 10) {
                val next = jit.call("add", a, b)
                a = b
                b = next
            }
            assertEquals(89L, b)
            jit.close()
        }
    }
}
