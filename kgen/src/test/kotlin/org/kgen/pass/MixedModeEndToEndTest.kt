package org.kgen.pass

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*
import org.kgen.ir.instructions.*
import org.kgen.ir.build.IrBuilder
import org.kgen.ir.target.Target
import java.io.ByteArrayInputStream
import java.util.jar.JarInputStream

class MixedModeEndToEndTest {

    @Test
    fun `partition native and managed functions from mixed IR module`() {
        val module = buildMixedModule()
        val compiler = MixedModeCompiler()
        val result = compiler.partition(module)

        // Native module gets only native functions + bridge declarations
        val nativeDefined = result.nativeModule.functions.filter { !it.isExternal }
        val managedDefined = result.managedModule.functions.filter { !it.isExternal }

        assertEquals(2, nativeDefined.size, "Two native functions expected")
        assertEquals(2, managedDefined.size, "Two managed functions expected")
        assertTrue(nativeDefined.any { it.name == "fastAdd" })
        assertTrue(nativeDefined.any { it.name == "fastMul" })
        assertTrue(managedDefined.any { it.name == "orchestrate" })
        assertTrue(managedDefined.any { it.name == "formatResult" })

        // Bridges should exist for managed->native calls
        val m2nBridges = result.bridges.filter { it.direction == MixedModeCompiler.BridgeDirection.MANAGED_TO_NATIVE }
        assertTrue(m2nBridges.any { it.targetFunction == "fastAdd" })
        assertTrue(m2nBridges.any { it.targetFunction == "fastMul" })

        // Stats should be consistent
        assertEquals(2, result.stats.nativeFunctionCount)
        assertEquals(2, result.stats.managedFunctionCount)
        assertEquals(result.bridges.size, result.stats.bridgeCount)
        assertTrue(result.stats.crossBoundaryCalls >= 2)
    }

    @Test
    fun `packaged JAR contains native library and JVM classes`() {
        val module = buildMixedModule()
        val compiler = MixedModeCompiler()
        val result = compiler.partition(module)

        // Simulate compiled artifacts
        val fakeClassBytes = byteArrayOf(0xCA.toByte(), 0xFE.toByte(), 0xBA.toByte(), 0xBE.toByte())
        val fakeNativeBytes = ByteArray(128) { 0xCC.toByte() }

        val packager = MixedModePackager("org.example")

        // Add managed classes (one per managed function + bridge stubs)
        for (fn in result.managedModule.functions.filter { !it.isExternal }) {
            packager.addClass("org/example/${fn.name}", fakeClassBytes)
        }

        // Add native library for current platform
        packager.addNativeLibrary("mixed_native", fakeNativeBytes, "linux-x86_64")

        // Add bridge metadata as resource
        val bridgeManifest = result.bridges.joinToString("\n") { "${it.name}:${it.targetFunction}:${it.direction}" }
        packager.addResource("META-INF/bridges.txt", bridgeManifest.toByteArray())

        val jar = packager.packageJar()
        val entries = readJarEntries(jar)

        assertTrue(entries.contains("org/example/orchestrate.class"))
        assertTrue(entries.contains("org/example/formatResult.class"))
        assertTrue(entries.contains("native/linux-x86_64/mixed_native.so"))
        assertTrue(entries.contains("META-INF/bridges.txt"))
        assertTrue(entries.contains("META-INF/native-libraries.txt"))
        assertTrue(entries.contains("META-INF/MANIFEST.MF"))
    }

    @Test
    fun `full pipeline from IR to packaged JAR`() {
        // Step 1: Build IR module with mixed-mode functions
        val module = buildMixedModule()
        assertTrue(MixedModeCompiler.hasMixedMode(module))

        // Step 2: Partition into native and managed
        val compiler = MixedModeCompiler()
        val partition = compiler.partition(module, "MixedApp")

        // Step 3: Verify native module is compilable (has entry blocks, correct structure)
        for (fn in partition.nativeModule.functions.filter { !it.isExternal }) {
            assertTrue(fn.blocks.isNotEmpty(), "Native function ${fn.name} should have blocks")
            assertTrue(fn.blocks.first().instructions.isNotEmpty(), "Native function ${fn.name} should have instructions")
        }

        // Step 4: Verify managed module is compilable
        for (fn in partition.managedModule.functions.filter { !it.isExternal }) {
            assertTrue(fn.blocks.isNotEmpty(), "Managed function ${fn.name} should have blocks")
        }

        // Step 5: Verify JNI descriptors are valid for all bridges
        for (bridge in partition.bridges) {
            val descriptor = compiler.jniDescriptor(bridge)
            assertTrue(descriptor.startsWith("("), "Descriptor should start with '('")
            assertTrue(descriptor.contains(")"), "Descriptor should contain ')'")
        }

        // Step 6: Package into JAR
        val fakeClassBytes = byteArrayOf(0xCA.toByte(), 0xFE.toByte(), 0xBA.toByte(), 0xBE.toByte())
        val fakeNativeBytes = ByteArray(64)

        val packager = MixedModePackager("org.example")
            .setMainClass("org/example/MixedApp")

        for (fn in partition.managedModule.functions.filter { !it.isExternal }) {
            packager.addClass("org/example/${fn.name}", fakeClassBytes)
        }
        packager.addNativeLibrary("mixed_native", fakeNativeBytes, "linux-x86_64")
        packager.addNativeLibrary("mixed_native", fakeNativeBytes, "windows-x86_64")

        val jar = packager.packageJar()
        assertTrue(jar.isNotEmpty())

        // Verify manifest main class
        val jis = JarInputStream(ByteArrayInputStream(jar))
        assertEquals("org.example.MixedApp", jis.manifest.mainAttributes.getValue("Main-Class"))
        jis.close()

        // Verify multi-platform native libs
        val entries = readJarEntries(jar)
        assertTrue(entries.contains("native/linux-x86_64/mixed_native.so"))
        assertTrue(entries.contains("native/windows-x86_64/mixed_native.dll"))
    }

    @Test
    fun `native to managed upcall bridge generation`() {
        val builder = IrBuilder("upcall_test", Target.x86_64())

        // Managed callback function
        val p1 = builder.createFunction("onEvent",
            listOf(Param("eventCode", Type.I32)), Type.I32,
            attributes = setOf(FnAttribute.MANAGED))
        builder.positionAtEnd(builder.appendBlock("entry"))
        builder.ret(p1[0])
        builder.finalizeFunction()

        // Another managed callback
        val p2 = builder.createFunction("onComplete",
            listOf(Param("result", Type.I64)), Type.Void,
            attributes = setOf(FnAttribute.MANAGED))
        builder.positionAtEnd(builder.appendBlock("entry"))
        builder.ret()
        builder.finalizeFunction()

        // Native function that calls both managed callbacks
        val p3 = builder.createFunction("nativeEventLoop",
            listOf(Param("count", Type.I32)), Type.I32,
            attributes = setOf(FnAttribute.NATIVE))
        builder.positionAtEnd(builder.appendBlock("entry"))
        val eventResult = builder.call("onEvent", listOf(p3[0]), Type.I32)
        builder.call("onComplete", listOf(Constant.I64(0)), Type.Void)
        builder.ret(eventResult!!)
        builder.finalizeFunction()

        val module = builder.build()
        val compiler = MixedModeCompiler()
        val result = compiler.partition(module)

        // Should generate native-to-managed (upcall) bridges
        val upcalls = result.bridges.filter { it.direction == MixedModeCompiler.BridgeDirection.NATIVE_TO_MANAGED }
        assertEquals(2, upcalls.size, "Should generate two upcall bridges")
        assertTrue(upcalls.any { it.targetFunction == "onEvent" && it.name == "upcall_onEvent" })
        assertTrue(upcalls.any { it.targetFunction == "onComplete" && it.name == "upcall_onComplete" })

        // Verify upcall JNI descriptors
        val onEventBridge = upcalls.first { it.targetFunction == "onEvent" }
        assertEquals("(I)I", compiler.jniDescriptor(onEventBridge))

        val onCompleteBridge = upcalls.first { it.targetFunction == "onComplete" }
        assertEquals("(J)V", compiler.jniDescriptor(onCompleteBridge))

        // Native module should have extern declarations for the upcalls
        val nativeExterns = result.nativeModule.functions.filter { it.isExternal }
        assertTrue(nativeExterns.any { it.name == "upcall_onEvent" })
        assertTrue(nativeExterns.any { it.name == "upcall_onComplete" })
    }

    @Test
    fun `multi-module mixed mode with separate native and managed modules`() {
        // Module 1: pure native compute kernels
        val nativeBuilder = IrBuilder("compute_kernels", Target.x86_64())

        val p1 = nativeBuilder.createFunction("matMul",
            listOf(Param("a", Type.I64), Param("b", Type.I64), Param("n", Type.I32)), Type.I64,
            attributes = setOf(FnAttribute.NATIVE))
        nativeBuilder.positionAtEnd(nativeBuilder.appendBlock("entry"))
        nativeBuilder.ret(p1[0])
        nativeBuilder.finalizeFunction()

        val p2 = nativeBuilder.createFunction("vectorAdd",
            listOf(Param("a", Type.I64), Param("b", Type.I64), Param("len", Type.I32)), Type.I64,
            attributes = setOf(FnAttribute.NATIVE))
        nativeBuilder.positionAtEnd(nativeBuilder.appendBlock("entry"))
        nativeBuilder.ret(p2[0])
        nativeBuilder.finalizeFunction()

        val nativeModule = nativeBuilder.build()

        // Module 2: managed application logic that calls into native kernels
        val managedBuilder = IrBuilder("app_logic", Target.x86_64())

        val p3 = managedBuilder.createFunction("runPipeline",
            listOf(Param("dataPtr", Type.I64), Param("size", Type.I32)), Type.I64,
            attributes = setOf(FnAttribute.MANAGED))
        managedBuilder.positionAtEnd(managedBuilder.appendBlock("entry"))
        managedBuilder.ret(p3[0])
        managedBuilder.finalizeFunction()

        val managedModule = managedBuilder.build()

        // Process both modules independently
        val compiler = MixedModeCompiler()

        // Pure native module: no mixed mode, all native
        assertFalse(MixedModeCompiler.hasMixedMode(nativeModule))
        val nativeCounts = MixedModeCompiler.countByMode(nativeModule)
        assertEquals(2, nativeCounts[FnAttribute.NATIVE])

        // Pure managed module: no mixed mode, all managed
        assertFalse(MixedModeCompiler.hasMixedMode(managedModule))
        val managedCounts = MixedModeCompiler.countByMode(managedModule)
        assertEquals(1, managedCounts[FnAttribute.MANAGED])

        // Partition each separately (no bridges since no cross-boundary calls within each module)
        val nativePartition = compiler.partition(nativeModule)
        val managedPartition = compiler.partition(managedModule)

        assertEquals(0, nativePartition.bridges.size, "Pure native module needs no bridges")
        assertEquals(0, managedPartition.bridges.size, "Pure managed module needs no bridges")

        // Package both into a single JAR
        val fakeClassBytes = byteArrayOf(0xCA.toByte(), 0xFE.toByte(), 0xBA.toByte(), 0xBE.toByte())
        val fakeNativeBytes = ByteArray(256)

        val packager = MixedModePackager("org.compute")
            .setMainClass("org/compute/App")
            .addClass("org/compute/App", fakeClassBytes)

        // Add managed module's functions as classes
        for (fn in managedPartition.managedModule.functions.filter { !it.isExternal }) {
            packager.addClass("org/compute/${fn.name}", fakeClassBytes)
        }

        // Add native module as a native library for multiple platforms
        packager.addNativeLibrary("compute_kernels", fakeNativeBytes, "linux-x86_64")
        packager.addNativeLibrary("compute_kernels", fakeNativeBytes, "darwin-aarch64")

        val jar = packager.packageJar()
        val entries = readJarEntries(jar)

        assertTrue(entries.contains("org/compute/App.class"))
        assertTrue(entries.contains("org/compute/runPipeline.class"))
        assertTrue(entries.contains("native/linux-x86_64/compute_kernels.so"))
        assertTrue(entries.contains("native/darwin-aarch64/compute_kernels.dylib"))
    }

    @Test
    fun `bidirectional bridges with JNI name mangling`() {
        val builder = IrBuilder("bidirectional", Target.x86_64())

        // Managed function that calls native
        val p1 = builder.createFunction("managedEntry",
            listOf(Param("x", Type.I32), Param("y", Type.F64)), Type.F64,
            attributes = setOf(FnAttribute.MANAGED))
        builder.positionAtEnd(builder.appendBlock("entry"))
        val nativeResult = builder.call("nativeCompute", listOf(p1[0], p1[1]), Type.F64)
        builder.ret(nativeResult!!)
        builder.finalizeFunction()

        // Native function that calls managed
        val p2 = builder.createFunction("nativeCompute",
            listOf(Param("x", Type.I32), Param("y", Type.F64)), Type.F64,
            attributes = setOf(FnAttribute.NATIVE))
        builder.positionAtEnd(builder.appendBlock("entry"))
        val callback = builder.call("managedLog", listOf(p2[0]), Type.Void)
        builder.ret(p2[1])
        builder.finalizeFunction()

        // Managed logging callback
        val p3 = builder.createFunction("managedLog",
            listOf(Param("code", Type.I32)), Type.Void,
            attributes = setOf(FnAttribute.MANAGED))
        builder.positionAtEnd(builder.appendBlock("entry"))
        builder.ret()
        builder.finalizeFunction()

        val module = builder.build()
        val compiler = MixedModeCompiler()
        val result = compiler.partition(module)

        // Should have both directions of bridges
        val m2n = result.bridges.filter { it.direction == MixedModeCompiler.BridgeDirection.MANAGED_TO_NATIVE }
        val n2m = result.bridges.filter { it.direction == MixedModeCompiler.BridgeDirection.NATIVE_TO_MANAGED }

        assertTrue(m2n.isNotEmpty(), "Should have managed-to-native bridge")
        assertTrue(n2m.isNotEmpty(), "Should have native-to-managed bridge")

        // Verify JNI method name mangling
        assertEquals("Java_org_example_App_nativeCompute",
            compiler.jniMethodName("org.example.App", "nativeCompute"))

        // Verify JNI descriptors for bridges with mixed types
        val m2nBridge = m2n.first { it.targetFunction == "nativeCompute" }
        assertEquals("(ID)D", compiler.jniDescriptor(m2nBridge))

        val n2mBridge = n2m.first { it.targetFunction == "managedLog" }
        assertEquals("(I)V", compiler.jniDescriptor(n2mBridge))

        // Both modules should have the right extern declarations
        val managedExterns = result.managedModule.functions.filter { it.isExternal }
        assertTrue(managedExterns.any { it.name == "jni_nativeCompute" })

        val nativeExterns = result.nativeModule.functions.filter { it.isExternal }
        assertTrue(nativeExterns.any { it.name == "upcall_managedLog" })
    }

    // Builds a module with two native hot-path functions and two managed wrappers
    private fun buildMixedModule(): Module {
        val builder = IrBuilder("mixed_app", Target.x86_64())

        // Native: fast integer add
        val p1 = builder.createFunction("fastAdd",
            listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32,
            attributes = setOf(FnAttribute.NATIVE))
        builder.positionAtEnd(builder.appendBlock("entry"))
        builder.ret(builder.add(p1[0], p1[1]))
        builder.finalizeFunction()

        // Native: fast integer multiply
        val p2 = builder.createFunction("fastMul",
            listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32,
            attributes = setOf(FnAttribute.NATIVE))
        builder.positionAtEnd(builder.appendBlock("entry"))
        builder.ret(builder.mul(p2[0], p2[1]))
        builder.finalizeFunction()

        // Managed: orchestration calling both native functions
        val p3 = builder.createFunction("orchestrate",
            listOf(Param("x", Type.I32), Param("y", Type.I32)), Type.I32,
            attributes = setOf(FnAttribute.MANAGED))
        builder.positionAtEnd(builder.appendBlock("entry"))
        val sum = builder.call("fastAdd", listOf(p3[0], p3[1]), Type.I32)
        val product = builder.call("fastMul", listOf(p3[0], p3[1]), Type.I32)
        builder.ret(builder.add(sum!!, product!!))
        builder.finalizeFunction()

        // Managed: result formatting (no native calls)
        val p4 = builder.createFunction("formatResult",
            listOf(Param("value", Type.I32)), Type.I32,
            attributes = setOf(FnAttribute.MANAGED))
        builder.positionAtEnd(builder.appendBlock("entry"))
        builder.ret(p4[0])
        builder.finalizeFunction()

        return builder.build()
    }

    private fun readJarEntries(jarBytes: ByteArray): Set<String> {
        val entries = mutableSetOf<String>()
        val jis = JarInputStream(ByteArrayInputStream(jarBytes))
        var entry = jis.nextJarEntry
        while (entry != null) {
            entries.add(entry.name)
            jis.closeEntry()
            entry = jis.nextJarEntry
        }
        if (jis.manifest != null) entries.add("META-INF/MANIFEST.MF")
        jis.close()
        return entries
    }
}
