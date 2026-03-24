package org.kgen.jit

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.kgen.target.x86.codegen.X86CodeGenerator
import org.kgen.ir.*
import org.kgen.ir.build.ModuleBuilder
import org.kgen.ir.target.Target

@EnabledOnOs(OS.WINDOWS, OS.LINUX)
class CodeCacheTest {

    private fun buildModule(name: String, funcName: String, value: Long): Module {
        val ir = ModuleBuilder(name, Target.x86_64())
        ir.createFunction(funcName, emptyList(), Type.I64)
        ir.appendBlock("entry")
        ir.ret(Constant.I64(value))
        ir.finalizeFunction()
        return ir.build()
    }

    @Test
    fun cacheTracksModules() {
        val jit = JitEngine(X86CodeGenerator())
        val cache = CodeCache()
        jit.setCodeCache(cache)

        jit.addModule(buildModule("m1", "f1", 1))
        jit.addModule(buildModule("m2", "f2", 2))

        assertEquals(2, cache.entryCount())
        assertTrue(cache.totalSize() > 0)
        assertTrue(cache.contains("m1"))
        assertTrue(cache.contains("m2"))

        jit.close()
    }

    @Test
    fun cacheEvictsLRU() {
        val jit = JitEngine(X86CodeGenerator())
        // Set a very small cache — each module is ~4096 bytes (minimum allocation)
        val cache = CodeCache(maxSize = 5000)
        jit.setCodeCache(cache)

        jit.addModule(buildModule("m1", "f1", 1))
        assertEquals(1, cache.entryCount())
        assertEquals(1L, jit.call("f1"))

        // Adding m2 should evict m1 (cache can only hold one ~4096 byte module)
        jit.addModule(buildModule("m2", "f2", 2))
        assertEquals(1, cache.entryCount())
        assertTrue(cache.contains("m2"))
        assertFalse(cache.contains("m1"))
        assertNull(jit.lookup("f1"))
        assertEquals(2L, jit.call("f2"))

        jit.close()
    }

    @Test
    fun cacheRemoveOnModuleRemoval() {
        val jit = JitEngine(X86CodeGenerator())
        val cache = CodeCache()
        jit.setCodeCache(cache)

        val m1 = jit.addModule(buildModule("m1", "f1", 1))
        assertEquals(1, cache.entryCount())

        jit.removeModule(m1)
        assertEquals(0, cache.entryCount())
        assertFalse(cache.contains("m1"))

        jit.close()
    }

    @Test
    fun unlimitedCacheNeverEvicts() {
        val jit = JitEngine(X86CodeGenerator())
        val cache = CodeCache(maxSize = 0) // unlimited
        jit.setCodeCache(cache)

        for (i in 1..10) {
            jit.addModule(buildModule("m$i", "f$i", i.toLong()))
        }
        assertEquals(10, cache.entryCount())
        for (i in 1..10) {
            assertEquals(i.toLong(), jit.call("f$i"))
        }

        jit.close()
    }

    @Test
    fun cacheTotalSize() {
        val cache = CodeCache(maxSize = 100_000)
        assertEquals(0L, cache.totalSize())
        assertEquals(0, cache.entryCount())
    }

    @Test
    fun noCacheStillWorks() {
        val jit = JitEngine(X86CodeGenerator())
        // No cache set
        jit.addModule(buildModule("m1", "f1", 42))
        assertEquals(42L, jit.call("f1"))
        jit.close()
    }
}
