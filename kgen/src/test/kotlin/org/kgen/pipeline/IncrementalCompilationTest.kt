package org.kgen.pipeline

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*
import org.kgen.ir.build.ModuleBuilder
import org.kgen.ir.target.Target

class IncrementalCompilationTest {

    private fun buildModule(vararg functions: Pair<String, (ModuleBuilder, List<Parameter>) -> Unit>): Module {
        val builder = ModuleBuilder("test", Target.x86_64())
        for ((name, body) in functions) {
            val params = builder.createFunction(name, listOf(Param("x", Type.I32)), Type.I32)
            builder.appendBlock("entry")
            body(builder, params)
            builder.finalizeFunction()
        }
        return builder.build()
    }

    @Test
    fun firstCompileAllNew() {
        val module = buildModule(
            "add" to { b, p -> b.ret(b.add(p[0], Constant.I32(1))) },
            "sub" to { b, p -> b.ret(b.sub(p[0], Constant.I32(1))) },
        )
        val cache = IncrementalCompilation.Cache()
        val result = IncrementalCompilation.analyze(module, cache)

        assertEquals(setOf("add", "sub"), result.added)
        assertTrue(result.changed.isEmpty())
        assertTrue(result.unchanged.isEmpty())
        assertTrue(result.removed.isEmpty())
        assertTrue(result.hasChanges())
    }

    @Test
    fun secondCompileNoChanges() {
        val module = buildModule(
            "add" to { b, p -> b.ret(b.add(p[0], Constant.I32(1))) },
        )
        val cache = IncrementalCompilation.Cache()
        IncrementalCompilation.analyze(module, cache) // first compile

        val result = IncrementalCompilation.analyze(module, cache) // second compile
        assertTrue(result.added.isEmpty())
        assertTrue(result.changed.isEmpty())
        assertEquals(setOf("add"), result.unchanged)
        assertFalse(result.hasChanges())
    }

    @Test
    fun detectChangedFunction() {
        val v1 = buildModule("add" to { b, p -> b.ret(b.add(p[0], Constant.I32(1))) })
        val cache = IncrementalCompilation.Cache()
        IncrementalCompilation.analyze(v1, cache)

        val v2 = buildModule("add" to { b, p -> b.ret(b.add(p[0], Constant.I32(2))) })
        val result = IncrementalCompilation.analyze(v2, cache)

        assertEquals(setOf("add"), result.changed)
        assertTrue(result.added.isEmpty())
        assertTrue(result.unchanged.isEmpty())
    }

    @Test
    fun detectAddedFunction() {
        val v1 = buildModule("add" to { b, p -> b.ret(b.add(p[0], Constant.I32(1))) })
        val cache = IncrementalCompilation.Cache()
        IncrementalCompilation.analyze(v1, cache)

        val v2 = buildModule(
            "add" to { b, p -> b.ret(b.add(p[0], Constant.I32(1))) },
            "mul" to { b, p -> b.ret(b.mul(p[0], Constant.I32(2))) },
        )
        val result = IncrementalCompilation.analyze(v2, cache)

        assertEquals(setOf("mul"), result.added)
        assertEquals(setOf("add"), result.unchanged)
        assertTrue(result.changed.isEmpty())
    }

    @Test
    fun detectRemovedFunction() {
        val v1 = buildModule(
            "add" to { b, p -> b.ret(b.add(p[0], Constant.I32(1))) },
            "sub" to { b, p -> b.ret(b.sub(p[0], Constant.I32(1))) },
        )
        val cache = IncrementalCompilation.Cache()
        IncrementalCompilation.analyze(v1, cache)

        val v2 = buildModule("add" to { b, p -> b.ret(b.add(p[0], Constant.I32(1))) })
        val result = IncrementalCompilation.analyze(v2, cache)

        assertEquals(setOf("sub"), result.removed)
        assertEquals(setOf("add"), result.unchanged)
    }

    @Test
    fun transitiveChangeWhenDependencyChanges() {
        val builder1 = ModuleBuilder("test", Target.x86_64())
        // helper: returns x + 1
        val hp = builder1.createFunction("helper", listOf(Param("x", Type.I32)), Type.I32)
        builder1.appendBlock("entry")
        builder1.ret(builder1.add(hp[0], Constant.I32(1)))
        builder1.finalizeFunction()
        // main: calls helper
        val mp = builder1.createFunction("main", listOf(Param("x", Type.I32)), Type.I32)
        builder1.appendBlock("entry")
        val result = builder1.call("helper", listOf(mp[0]), Type.I32)
        builder1.ret(result!!)
        builder1.finalizeFunction()
        val v1 = builder1.build()

        val cache = IncrementalCompilation.Cache()
        IncrementalCompilation.analyze(v1, cache)

        // Change helper's body
        val builder2 = ModuleBuilder("test", Target.x86_64())
        val hp2 = builder2.createFunction("helper", listOf(Param("x", Type.I32)), Type.I32)
        builder2.appendBlock("entry")
        builder2.ret(builder2.add(hp2[0], Constant.I32(2))) // changed from 1 to 2
        builder2.finalizeFunction()
        val mp2 = builder2.createFunction("main", listOf(Param("x", Type.I32)), Type.I32)
        builder2.appendBlock("entry")
        val result2 = builder2.call("helper", listOf(mp2[0]), Type.I32)
        builder2.ret(result2!!)
        builder2.finalizeFunction()
        val v2 = builder2.build()

        val result3 = IncrementalCompilation.analyze(v2, cache)

        // helper changed directly, main changed transitively
        assertTrue(result3.changed.contains("helper"), "helper should be changed")
        assertTrue(result3.changed.contains("main"), "main should be transitively changed")
    }

    @Test
    fun hashFunctionDeterministic() {
        val module = buildModule("add" to { b, p -> b.ret(b.add(p[0], Constant.I32(1))) })
        val fn = module.functions.first()

        val hash1 = IncrementalCompilation.hashFunction(fn)
        val hash2 = IncrementalCompilation.hashFunction(fn)
        assertEquals(hash1, hash2)
    }

    @Test
    fun hashFunctionChangesWithBody() {
        val m1 = buildModule("add" to { b, p -> b.ret(b.add(p[0], Constant.I32(1))) })
        val m2 = buildModule("add" to { b, p -> b.ret(b.add(p[0], Constant.I32(2))) })

        assertNotEquals(
            IncrementalCompilation.hashFunction(m1.functions.first()),
            IncrementalCompilation.hashFunction(m2.functions.first()),
        )
    }

    @Test
    fun extractDependencies() {
        val builder = ModuleBuilder("test", Target.x86_64())
        val params = builder.createFunction("caller", listOf(Param("x", Type.I32)), Type.I32)
        builder.appendBlock("entry")
        val r = builder.call("helper", listOf(params[0]), Type.I32)
        builder.ret(r!!)
        builder.finalizeFunction()
        val module = builder.build()

        val deps = IncrementalCompilation.extractDependencies(module.functions.first())
        assertEquals(setOf("helper"), deps)
    }

    @Test
    fun cacheOperations() {
        val cache = IncrementalCompilation.Cache()
        assertEquals(0, cache.size())

        cache.put("func1", 12345)
        assertEquals(12345, cache.get("func1"))
        assertEquals(1, cache.size())

        cache.putDependencies("func1", setOf("dep1", "dep2"))
        assertEquals(setOf("dep1", "dep2"), cache.getDependencies("func1"))

        cache.remove("func1")
        assertNull(cache.get("func1"))
        assertEquals(0, cache.size())
    }

    @Test
    fun cacheClear() {
        val cache = IncrementalCompilation.Cache()
        cache.put("a", 1)
        cache.put("b", 2)
        assertEquals(2, cache.size())

        cache.clear()
        assertEquals(0, cache.size())
    }

    @Test
    fun needsRecompilation() {
        val result = IncrementalCompilation.IncrementalResult(
            changed = setOf("a"),
            unchanged = setOf("b"),
            added = setOf("c"),
            removed = setOf("d"),
        )
        assertEquals(setOf("a", "c"), result.needsRecompilation())
    }
}
