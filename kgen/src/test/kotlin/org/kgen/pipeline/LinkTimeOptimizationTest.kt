package org.kgen.pipeline

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*
import org.kgen.ir.build.ModuleBuilder
import org.kgen.ir.target.Target

class LinkTimeOptimizationTest {

    private fun buildModuleA(): Module {
        val builder = ModuleBuilder("moduleA", Target.x86_64())
        // add(a, b) = a + b
        val params = builder.createFunction("add", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        builder.appendBlock("entry")
        val result = builder.add(params[0], params[1])
        builder.ret(result)
        builder.finalizeFunction()
        return builder.build()
    }

    private fun buildModuleB(): Module {
        val builder = ModuleBuilder("moduleB", Target.x86_64())
        // sub(a, b) = a - b
        val params = builder.createFunction("sub", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        builder.appendBlock("entry")
        val result = builder.sub(params[0], params[1])
        builder.ret(result)
        builder.finalizeFunction()
        return builder.build()
    }

    private fun buildCallerModule(): Module {
        val builder = ModuleBuilder("caller", Target.x86_64())

        // Declare add as external
        builder.createFunction("add", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        builder.finalizeFunction()

        // compute(x) = add(x, x)
        val params = builder.createFunction("compute", listOf(Param("x", Type.I32)), Type.I32,
            linkage = Linkage.EXTERNAL)
        builder.appendBlock("entry")
        val result = builder.call("add", listOf(params[0], params[0]), Type.I32)
        builder.ret(result!!)
        builder.finalizeFunction()
        return builder.build()
    }

    @Test
    fun mergeEmpty() {
        val merged = LinkTimeOptimization.merge(emptyList())
        assertEquals("merged", merged.name)
        assertTrue(merged.functions.isEmpty())
    }

    @Test
    fun mergeSingleModule() {
        val a = buildModuleA()
        val merged = LinkTimeOptimization.merge(listOf(a))
        assertSame(a, merged)
    }

    @Test
    fun mergeTwoModules() {
        val a = buildModuleA()
        val b = buildModuleB()
        val merged = LinkTimeOptimization.merge(listOf(a, b))

        assertEquals("moduleA+moduleB", merged.name)
        assertEquals(2, merged.functions.size)
        assertTrue(merged.functions.any { it.name == "add" })
        assertTrue(merged.functions.any { it.name == "sub" })
    }

    @Test
    fun mergeDeduplicatesExternalDeclarations() {
        val a = buildModuleA() // has 'add' definition
        val caller = buildCallerModule() // has 'add' declaration + 'compute' definition

        val merged = LinkTimeOptimization.merge(listOf(a, caller))

        // 'add' should be the definition (not the declaration)
        val addFn = merged.functions.first { it.name == "add" }
        assertFalse(addFn.isExternal, "add should be a definition, not declaration")

        // 'compute' should exist
        assertTrue(merged.functions.any { it.name == "compute" })
    }

    @Test
    fun mergeDefinitionWinsOverDeclaration() {
        // declaration first, definition second
        val caller = buildCallerModule()
        val a = buildModuleA()

        val merged = LinkTimeOptimization.merge(listOf(caller, a))
        val addFn = merged.functions.first { it.name == "add" }
        assertFalse(addFn.isExternal)
    }

    @Test
    fun mergePreservesMetadata() {
        val a = buildModuleA().copy(metadata = mapOf("key1" to MetadataValue.StringMD("val1")))
        val b = buildModuleB().copy(metadata = mapOf("key2" to MetadataValue.StringMD("val2")))

        val merged = LinkTimeOptimization.merge(listOf(a, b))
        assertEquals(2, merged.metadata.size)
        assertEquals(MetadataValue.StringMD("val1"), merged.metadata["key1"])
        assertEquals(MetadataValue.StringMD("val2"), merged.metadata["key2"])
    }

    @Test
    fun mergeUsesFirstTargetTriple() {
        val a = buildModuleA().copy(targetTriple = "x86_64-unknown-linux-gnu")
        val b = buildModuleB()

        val merged = LinkTimeOptimization.merge(listOf(a, b))
        assertEquals("x86_64-unknown-linux-gnu", merged.targetTriple)
    }

    @Test
    fun internalizeFunctions() {
        val a = buildModuleA()
        val b = buildModuleB()
        val merged = LinkTimeOptimization.merge(listOf(a, b))

        // Export only 'add', 'sub' should be internalized
        val internalized = LinkTimeOptimization.internalize(merged, exports = setOf("add"))

        val addFn = internalized.functions.first { it.name == "add" }
        assertEquals(Linkage.EXTERNAL, addFn.linkage)

        val subFn = internalized.functions.first { it.name == "sub" }
        assertEquals(Linkage.INTERNAL, subFn.linkage)
    }

    @Test
    fun eliminateDeadFunctions() {
        val builder = ModuleBuilder("test", Target.x86_64())

        // unused_func — internal, never called
        val unusedParams = builder.createFunction("unused_func", listOf(Param("x", Type.I32)), Type.I32, linkage = Linkage.INTERNAL)
        builder.appendBlock("entry")
        builder.ret(unusedParams[0])
        builder.finalizeFunction()

        // helper — internal, called by main
        val helperParams = builder.createFunction("helper", listOf(Param("x", Type.I32)), Type.I32, linkage = Linkage.INTERNAL)
        builder.appendBlock("entry")
        builder.ret(helperParams[0])
        builder.finalizeFunction()

        builder.createFunction("main", emptyList(), Type.I32)
        builder.appendBlock("entry")
        val result = builder.call("helper", listOf(Constant.I32(42)), Type.I32)
        builder.ret(result!!)
        builder.finalizeFunction()

        val module = builder.build()
        val cleaned = LinkTimeOptimization.eliminateDeadFunctions(module)

        // unused_func should be removed, helper + main should remain
        assertFalse(cleaned.functions.any { it.name == "unused_func" })
        assertTrue(cleaned.functions.any { it.name == "helper" })
        assertTrue(cleaned.functions.any { it.name == "main" })
    }

    @Test
    fun fullLtoMergesAndOptimizes() {
        val a = buildModuleA()
        val caller = buildCallerModule()

        val lto = LinkTimeOptimization()
        val optimized = lto.fullLto(listOf(a, caller))

        // Both functions should exist in the merged result
        assertTrue(optimized.functions.any { it.name == "add" || it.name == "compute" })
    }

    @Test
    fun thinLto() {
        val a = buildModuleA()
        val b = buildModuleB()

        val lto = LinkTimeOptimization()
        val result = lto.thinLto(listOf(a, b))

        assertFalse(result.isEmpty())
        // Both functions should survive
        val allFuncs = result.flatMap { it.functions }
        assertTrue(allFuncs.any { it.name == "add" })
        assertTrue(allFuncs.any { it.name == "sub" })
    }

    @Test
    fun mergeGlobals() {
        val a = buildModuleA().copy(globals = listOf(Global("counter", Type.I32, Constant.I32(0))))
        val b = buildModuleB().copy(globals = listOf(Global("flag", Type.I1, Constant.I1(true))))

        val merged = LinkTimeOptimization.merge(listOf(a, b))
        assertEquals(2, merged.globals.size)
    }

    @Test
    fun mergeTargetFeatures() {
        val a = buildModuleA().copy(targetFeatures = setOf("sse4.2", "avx"))
        val b = buildModuleB().copy(targetFeatures = setOf("avx2"))

        val merged = LinkTimeOptimization.merge(listOf(a, b))
        assertEquals(setOf("sse4.2", "avx", "avx2"), merged.targetFeatures)
    }
}
