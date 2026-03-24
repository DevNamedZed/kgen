package org.kgen.examples.gc

import org.kgen.runtime.gc.*
import org.kgen.unmanaged.lib.NativeString

/**
 * Demonstrates registering @KgenNative class layouts with the GC type system.
 *
 * The [NativeTypeLayoutBuilder] reads the JVM class file metadata of a
 * @KgenNative class and generates an [ObjectLayout] compatible with the GC.
 * This bridges the JVM world (class files, field descriptors) to the native
 * GC world (heap headers, field offsets, reference tracking).
 *
 * In a full native compilation pipeline, this registration happens automatically
 * when the RuntimeCompiler processes @KgenNative classes. Here we show the
 * manual steps to demonstrate the mechanism.
 */
object NativeTypeRegistrationExample {

    /**
     * Build an ObjectLayout from the NativeString class file and register it
     * with a TypeRegistry.
     */
    @JvmStatic
    fun registerNativeStringLayout(): LayoutRegistration {
        val classBytes = NativeString::class.java.classLoader
            .getResourceAsStream("org/kgen/unmanaged/lib/NativeString.class")
            ?.readAllBytes()
            ?: error("NativeString.class not found")

        val builder = NativeTypeLayoutBuilder()
        val layout = builder.buildLayout(classBytes)
        val hasDestructor = builder.hasDestructor(classBytes)
        val destructorName = builder.findDestructorMethodName(classBytes)

        val registry = TypeRegistry()
        val typeId = registry.register(layout)

        return LayoutRegistration(
            typeName = layout.name,
            typeId = typeId,
            fieldCount = layout.fields.size,
            fieldNames = layout.fields.map { it.name },
            totalSize = layout.totalSize(),
            hasDestructor = hasDestructor,
            destructorMethodName = destructorName,
        )
    }

    /**
     * Allocate a NativeString-shaped object on the managed heap and trigger
     * GC collection with finalization.
     */
    @JvmStatic
    fun gcManagedNativeString(): GcResult {
        val classBytes = NativeString::class.java.classLoader
            .getResourceAsStream("org/kgen/unmanaged/lib/NativeString.class")
            ?.readAllBytes()
            ?: error("NativeString.class not found")

        val layoutBuilder = NativeTypeLayoutBuilder()
        val layout = layoutBuilder.buildLayout(classBytes)

        val heap = BumpHeap(4096)
        val registry = TypeRegistry()
        val gc = MarkSweepGC(heap, registry)

        val typeId = registry.register(layout)
        val registeredLayout = registry.lookup(typeId)!!

        val liveString = heap.allocate(registeredLayout)
        heap.writeField(liveString, 0, 0x1000L)
        heap.writeField(liveString, 1, 5L)

        val deadString = heap.allocate(registeredLayout)
        heap.writeField(deadString, 0, 0x2000L)
        heap.writeField(deadString, 1, 10L)

        var finalizerCalledWith = 0L
        gc.registerFinalizer(typeId) { address ->
            finalizerCalledWith = heap.readField(address, 0)
        }

        gc.addRootProvider { visitor -> visitor.visitRoot(liveString) }

        gc.collect()

        val liveDataField = heap.readField(liveString, 0)

        return GcResult(
            liveDataPointer = liveDataField,
            deadDataPointer = finalizerCalledWith,
            reclaimedBytes = gc.bytesReclaimed(),
        )
    }

    data class LayoutRegistration(
        val typeName: String,
        val typeId: Int,
        val fieldCount: Int,
        val fieldNames: List<String>,
        val totalSize: Int,
        val hasDestructor: Boolean,
        val destructorMethodName: String?,
    )

    data class GcResult(
        val liveDataPointer: Long,
        val deadDataPointer: Long,
        val reclaimedBytes: Long,
    )
}
