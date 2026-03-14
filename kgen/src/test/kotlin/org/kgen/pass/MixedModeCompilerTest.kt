package org.kgen.pass

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*
import org.kgen.ir.build.IrBuilder
import org.kgen.ir.target.Target

class MixedModeCompilerTest {

    private fun buildMixedModule(): Module {
        val builder = IrBuilder("mixed", Target.x86_64())

        // Native hot path
        val p1 = builder.createFunction("hotCompute",
            listOf(Param("x", Type.I32), Param("y", Type.I32)), Type.I32,
            attributes = setOf(FnAttribute.NATIVE))
        builder.appendBlock("entry")
        builder.ret(builder.add(p1[0], p1[1]))
        builder.finalizeFunction()

        // Managed wrapper
        val p2 = builder.createFunction("managedWrapper",
            listOf(Param("x", Type.I32)), Type.I32,
            attributes = setOf(FnAttribute.MANAGED))
        builder.appendBlock("entry")
        val result = builder.call("hotCompute", listOf(p2[0], p2[0]), Type.I32)
        builder.ret(result!!)
        builder.finalizeFunction()

        // Another managed function
        val p3 = builder.createFunction("managedHelper",
            listOf(Param("n", Type.I32)), Type.I32,
            attributes = setOf(FnAttribute.MANAGED))
        builder.appendBlock("entry")
        builder.ret(p3[0])
        builder.finalizeFunction()

        return builder.build()
    }

    @Test
    fun partitionSplitsCorrectly() {
        val module = buildMixedModule()
        val compiler = MixedModeCompiler()
        val result = compiler.partition(module)

        assertEquals(1, result.stats.nativeFunctionCount)
        assertEquals(2, result.stats.managedFunctionCount)
    }

    @Test
    fun nativeModuleContainsNativeFunctions() {
        val module = buildMixedModule()
        val compiler = MixedModeCompiler()
        val result = compiler.partition(module)

        val nativeNames = result.nativeModule.functions.filter { !it.isExternal }.map { it.name }
        assertTrue(nativeNames.contains("hotCompute"))
        assertFalse(nativeNames.contains("managedWrapper"))
    }

    @Test
    fun managedModuleContainsManagedFunctions() {
        val module = buildMixedModule()
        val compiler = MixedModeCompiler()
        val result = compiler.partition(module)

        val managedNames = result.managedModule.functions.filter { !it.isExternal }.map { it.name }
        assertTrue(managedNames.contains("managedWrapper"))
        assertTrue(managedNames.contains("managedHelper"))
        assertFalse(managedNames.contains("hotCompute"))
    }

    @Test
    fun bridgesGeneratedForCrossBoundaryCalls() {
        val module = buildMixedModule()
        val compiler = MixedModeCompiler()
        val result = compiler.partition(module)

        // managedWrapper calls hotCompute (managed→native bridge)
        assertTrue(result.bridges.isNotEmpty())
        val m2nBridge = result.bridges.first { it.direction == MixedModeCompiler.BridgeDirection.MANAGED_TO_NATIVE }
        assertEquals("hotCompute", m2nBridge.targetFunction)
        assertEquals("jni_hotCompute", m2nBridge.name)
    }

    @Test
    fun bridgeDeclarationsAddedToModules() {
        val module = buildMixedModule()
        val compiler = MixedModeCompiler()
        val result = compiler.partition(module)

        // Managed module should have a bridge declaration for calling native
        val managedExterns = result.managedModule.functions.filter { it.isExternal }
        assertTrue(managedExterns.any { it.name == "jni_hotCompute" })
    }

    @Test
    fun classifyNativeFunction() {
        val compiler = MixedModeCompiler()
        val fn = IrFunction(
            name = "native_fn",
            params = emptyList(),
            returnType = Type.I32,
            blocks = emptyList(),
            attributes = setOf(FnAttribute.NATIVE),
        )
        assertEquals(FnAttribute.NATIVE, compiler.classifyFunction(fn))
    }

    @Test
    fun classifyManagedFunction() {
        val compiler = MixedModeCompiler()
        val fn = IrFunction(
            name = "managed_fn",
            params = emptyList(),
            returnType = Type.I32,
            blocks = emptyList(),
            attributes = setOf(FnAttribute.MANAGED),
        )
        assertEquals(FnAttribute.MANAGED, compiler.classifyFunction(fn))
    }

    @Test
    fun classifyDefaultMode() {
        val compiler = MixedModeCompiler(defaultMode = FnAttribute.NATIVE)
        val fn = IrFunction(
            name = "plain_fn",
            params = emptyList(),
            returnType = Type.I32,
            blocks = emptyList(),
        )
        assertEquals(FnAttribute.NATIVE, compiler.classifyFunction(fn))
    }

    @Test
    fun hasMixedModeDetection() {
        val module = buildMixedModule()
        assertTrue(MixedModeCompiler.hasMixedMode(module))
    }

    @Test
    fun noMixedModeDetection() {
        val builder = IrBuilder("pure", Target.x86_64())
        val p = builder.createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
        builder.appendBlock("entry")
        builder.ret(p[0])
        builder.finalizeFunction()
        val module = builder.build()

        assertFalse(MixedModeCompiler.hasMixedMode(module))
    }

    @Test
    fun countByMode() {
        val module = buildMixedModule()
        val counts = MixedModeCompiler.countByMode(module)
        assertEquals(1, counts[FnAttribute.NATIVE])
        assertEquals(2, counts[FnAttribute.MANAGED])
    }

    @Test
    fun jniDescriptorGeneration() {
        val compiler = MixedModeCompiler()
        val bridge = MixedModeCompiler.Bridge(
            name = "jni_add",
            targetFunction = "add",
            direction = MixedModeCompiler.BridgeDirection.MANAGED_TO_NATIVE,
            paramTypes = listOf(Type.I32, Type.I32),
            returnType = Type.I32,
        )
        assertEquals("(II)I", compiler.jniDescriptor(bridge))
    }

    @Test
    fun jniDescriptorWithVarious() {
        val compiler = MixedModeCompiler()
        val bridge = MixedModeCompiler.Bridge(
            name = "jni_compute",
            targetFunction = "compute",
            direction = MixedModeCompiler.BridgeDirection.MANAGED_TO_NATIVE,
            paramTypes = listOf(Type.I64, Type.F64, Type.I1),
            returnType = Type.Void,
        )
        assertEquals("(JDZ)V", compiler.jniDescriptor(bridge))
    }

    @Test
    fun jniMethodNameMangling() {
        val compiler = MixedModeCompiler()
        assertEquals("Java_com_example_MyClass_add", compiler.jniMethodName("com.example.MyClass", "add"))
        assertEquals("Java_MyClass_compute", compiler.jniMethodName("MyClass", "compute"))
    }

    @Test
    fun partitionStats() {
        val module = buildMixedModule()
        val compiler = MixedModeCompiler()
        val result = compiler.partition(module)

        assertTrue(result.stats.crossBoundaryCalls > 0)
        assertEquals(result.bridges.size, result.stats.bridgeCount)
    }

    @Test
    fun nativeToManagedBridge() {
        val builder = IrBuilder("reverse", Target.x86_64())

        // Managed helper
        val p1 = builder.createFunction("managedFn",
            listOf(Param("x", Type.I32)), Type.I32,
            attributes = setOf(FnAttribute.MANAGED))
        builder.appendBlock("entry")
        builder.ret(p1[0])
        builder.finalizeFunction()

        // Native function that calls managed
        val p2 = builder.createFunction("nativeFn",
            listOf(Param("x", Type.I32)), Type.I32,
            attributes = setOf(FnAttribute.NATIVE))
        builder.appendBlock("entry")
        val r = builder.call("managedFn", listOf(p2[0]), Type.I32)
        builder.ret(r!!)
        builder.finalizeFunction()

        val module = builder.build()
        val compiler = MixedModeCompiler()
        val result = compiler.partition(module)

        val n2mBridges = result.bridges.filter { it.direction == MixedModeCompiler.BridgeDirection.NATIVE_TO_MANAGED }
        assertTrue(n2mBridges.isNotEmpty(), "Should generate native-to-managed bridge")
        assertEquals("managedFn", n2mBridges.first().targetFunction)
    }

    @Test
    fun emptyModulePartitions() {
        val module = Module("empty")
        val compiler = MixedModeCompiler()
        val result = compiler.partition(module)

        assertEquals(0, result.stats.nativeFunctionCount)
        assertEquals(0, result.stats.managedFunctionCount)
        assertTrue(result.bridges.isEmpty())
    }
}
