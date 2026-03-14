package org.kgen.jit

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.kgen.ir.*
import org.kgen.ir.build.IrBuilder
import org.kgen.ir.target.Target
import org.kgen.target.x86.codegen.X86CodeGenerator

@EnabledOnOs(OS.WINDOWS, OS.LINUX)
class JitAdvancedScenariosTest {

    private fun buildConstantModule(name: String, funcName: String, value: Long): Module {
        val ir = IrBuilder(name, Target.x86_64())
        ir.createFunction(funcName, emptyList(), Type.I64)
        ir.appendBlock("entry")
        ir.ret(Constant.I64(value))
        ir.finalizeFunction()
        return ir.build()
    }

    private fun buildAddModule(moduleName: String, funcName: String): Module {
        val ir = IrBuilder(moduleName, Target.x86_64())
        ir.createFunction(funcName, listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
        ir.appendBlock("entry")
        val a = Parameter("a", Type.I64, 0)
        val b = Parameter("b", Type.I64, 1)
        ir.ret(ir.add(a, b))
        ir.finalizeFunction()
        return ir.build()
    }

    // -- Lazy compilation scenarios --

    @Test
    fun `lazy stub is visible as STUB source before materialization`() {
        val jit = JitEngine(X86CodeGenerator())
        jit.addLazy("lazyOne") { buildConstantModule("lazy1", "lazyOne", 10) }

        assertTrue(jit.symbolNames().contains("lazyOne"))
        val sym = jit.lookup("lazyOne")
        assertNotNull(sym)
        assertEquals(JitSymbol.SymbolSource.STUB, sym!!.source)

        jit.close()
    }

    @Test
    fun `materialization compiles and makes function callable`() {
        val jit = JitEngine(X86CodeGenerator())
        var compiled = false
        jit.addLazy("compute") {
            compiled = true
            buildConstantModule("computeModule", "compute", 77)
        }

        assertFalse(compiled)
        jit.materializeLazy("compute")
        assertTrue(compiled)

        val sym = jit.lookup("compute")
        assertNotNull(sym)
        assertEquals(JitSymbol.SymbolSource.JIT, sym!!.source)

        val result = jit.call("compute")
        assertEquals(77L, result)

        jit.close()
    }

    @Test
    fun `multiple lazy stubs materialized on demand`() {
        val jit = JitEngine(X86CodeGenerator())
        val compiledSet = mutableSetOf<String>()

        for (i in 1..5) {
            val name = "lazy$i"
            jit.addLazy(name) {
                compiledSet.add(name)
                buildConstantModule("mod$i", name, i.toLong() * 10)
            }
        }

        assertEquals(5, jit.symbolNames().count { it.startsWith("lazy") })

        jit.materializeLazy("lazy2")
        jit.materializeLazy("lazy4")

        assertEquals(setOf("lazy2", "lazy4"), compiledSet)
        assertEquals(20L, jit.call("lazy2"))
        assertEquals(40L, jit.call("lazy4"))

        assertEquals(JitSymbol.SymbolSource.JIT, jit.lookup("lazy2")!!.source)
        assertEquals(JitSymbol.SymbolSource.JIT, jit.lookup("lazy4")!!.source)
        assertEquals(JitSymbol.SymbolSource.STUB, jit.lookup("lazy1")!!.source)
        assertEquals(JitSymbol.SymbolSource.STUB, jit.lookup("lazy3")!!.source)
        assertEquals(JitSymbol.SymbolSource.STUB, jit.lookup("lazy5")!!.source)

        jit.close()
    }

    @Test
    fun `lazy stub for complex function with parameters`() {
        val jit = JitEngine(X86CodeGenerator())
        jit.addLazy("addTwo") {
            buildAddModule("addMod", "addTwo")
        }

        assertEquals(JitSymbol.SymbolSource.STUB, jit.lookup("addTwo")!!.source)

        jit.materializeLazy("addTwo")
        assertEquals(JitSymbol.SymbolSource.JIT, jit.lookup("addTwo")!!.source)

        assertEquals(15L, jit.call("addTwo", 7, 8))
        assertEquals(0L, jit.call("addTwo", -5, 5))
        assertEquals(200L, jit.call("addTwo", 100, 100))

        jit.close()
    }

    @Test
    fun `lazy and eager modules coexist`() {
        val jit = JitEngine(X86CodeGenerator())

        jit.addModule(buildConstantModule("eagerMod", "eagerFunc", 42))
        jit.addLazy("lazyFunc") {
            buildConstantModule("lazyMod", "lazyFunc", 99)
        }

        assertEquals(42L, jit.call("eagerFunc"))
        assertEquals(JitSymbol.SymbolSource.JIT, jit.lookup("eagerFunc")!!.source)
        assertEquals(JitSymbol.SymbolSource.STUB, jit.lookup("lazyFunc")!!.source)

        jit.materializeLazy("lazyFunc")

        assertEquals(42L, jit.call("eagerFunc"))
        assertEquals(99L, jit.call("lazyFunc"))
        assertEquals(JitSymbol.SymbolSource.JIT, jit.lookup("eagerFunc")!!.source)
        assertEquals(JitSymbol.SymbolSource.JIT, jit.lookup("lazyFunc")!!.source)

        jit.close()
    }

    // -- Code cache eviction scenarios --

    @Test
    fun `code cache tracks module count and size`() {
        val jit = JitEngine(X86CodeGenerator())
        val cache = CodeCache()
        jit.setCodeCache(cache)

        jit.addModule(buildConstantModule("m1", "f1", 1))
        jit.addModule(buildConstantModule("m2", "f2", 2))
        jit.addModule(buildConstantModule("m3", "f3", 3))

        assertEquals(3, cache.entryCount())
        assertTrue(cache.totalSize() > 0)
        assertTrue(cache.contains("m1"))
        assertTrue(cache.contains("m2"))
        assertTrue(cache.contains("m3"))

        assertEquals(1L, jit.call("f1"))
        assertEquals(2L, jit.call("f2"))
        assertEquals(3L, jit.call("f3"))

        jit.close()
    }

    @Test
    fun `LRU eviction removes least recently used modules`() {
        val jit = JitEngine(X86CodeGenerator())
        val cache = CodeCache(maxSize = 5000)
        jit.setCodeCache(cache)

        jit.addModule(buildConstantModule("m1", "f1", 1))
        assertEquals(1, cache.entryCount())
        assertTrue(cache.contains("m1"))

        jit.addModule(buildConstantModule("m2", "f2", 2))
        assertEquals(1, cache.entryCount())
        assertFalse(cache.contains("m1"))
        assertTrue(cache.contains("m2"))

        assertEquals(2L, jit.call("f2"))

        jit.close()
    }

    @Test
    fun `evicted module symbols are removed`() {
        val jit = JitEngine(X86CodeGenerator())
        val cache = CodeCache(maxSize = 5000)
        jit.setCodeCache(cache)

        jit.addModule(buildConstantModule("m1", "f1", 1))
        assertNotNull(jit.lookup("f1"))

        jit.addModule(buildConstantModule("m2", "f2", 2))
        assertNull(jit.lookup("f1"))
        assertFalse(jit.symbolNames().contains("f1"))
        assertTrue(jit.symbolNames().contains("f2"))

        jit.close()
    }

    @Test
    fun `cache eviction under pressure with many modules`() {
        val jit = JitEngine(X86CodeGenerator())
        val cache = CodeCache(maxSize = 5000)
        jit.setCodeCache(cache)

        for (i in 1..12) {
            jit.addModule(buildConstantModule("m$i", "f$i", i.toLong()))
        }

        // With a 5000-byte cache and ~4096-byte modules, only the most recent should survive
        assertTrue(cache.entryCount() <= 2, "Cache should hold at most 1-2 modules, got ${cache.entryCount()}")
        assertTrue(cache.contains("m12"), "Most recent module should be in cache")

        // Earlier modules should have been evicted
        for (i in 1..10) {
            assertFalse(cache.contains("m$i"), "Module m$i should have been evicted")
        }

        assertEquals(12L, jit.call("f12"))

        jit.close()
    }

    @Test
    fun `unlimited cache retains all modules`() {
        val jit = JitEngine(X86CodeGenerator())
        val cache = CodeCache(maxSize = 0)
        jit.setCodeCache(cache)

        for (i in 1..20) {
            jit.addModule(buildConstantModule("m$i", "f$i", i.toLong()))
        }

        assertEquals(20, cache.entryCount())
        assertTrue(cache.totalSize() > 0)

        for (i in 1..20) {
            assertTrue(cache.contains("m$i"), "Module m$i should be retained")
            assertEquals(i.toLong(), jit.call("f$i"))
        }

        jit.close()
    }

    // -- Module replacement safety scenarios --

    @Test
    fun `module replacement shadows old symbols`() {
        val jit = JitEngine(X86CodeGenerator())

        val modA = jit.addModule(buildConstantModule("modA", "compute", 1))
        assertEquals(1L, jit.call("compute"))

        val modB = jit.addModule(buildConstantModule("modB", "compute", 2))
        assertEquals(2L, jit.call("compute"), "Newer module should shadow older symbol")

        // Both modules are still loaded
        assertTrue(jit.modules().contains(modA))
        assertTrue(jit.modules().contains(modB))

        jit.close()
    }

    @Test
    fun `removed module symbols are gone`() {
        val jit = JitEngine(X86CodeGenerator())

        val mod = jit.addModule(buildConstantModule("onlyMod", "myFunc", 42))
        assertNotNull(jit.lookup("myFunc"))
        assertEquals(42L, jit.call("myFunc"))

        jit.removeModule(mod)

        assertNull(jit.lookup("myFunc"), "Symbol should be gone after module removal")
        assertFalse(jit.symbolNames().contains("myFunc"))

        jit.close()
    }

    @Test
    fun `replaced module old symbol not callable`() {
        val jit = JitEngine(X86CodeGenerator())

        val modA = jit.addModule(buildConstantModule("modA", "compute", 100))
        assertEquals(100L, jit.call("compute"))

        // Replace: add module B with same symbol, then remove module A
        val modB = jit.addModule(buildConstantModule("modB", "compute", 200))
        jit.removeModule(modA)

        // B's version should be the active one
        assertEquals(200L, jit.call("compute"), "Module B's symbol should be active")
        assertNotNull(jit.lookup("compute"))
        assertEquals(1, jit.modules().size, "Only module B should remain")

        jit.close()
    }
}
