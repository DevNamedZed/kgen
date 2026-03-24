package org.kgen.runtime.gc

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FinalizationTest {

    private lateinit var heap: BumpHeap
    private lateinit var registry: TypeRegistry
    private lateinit var gc: MarkSweepGC

    @BeforeEach
    fun setup() {
        heap = BumpHeap(4096)
        registry = TypeRegistry()
        gc = MarkSweepGC(heap, registry)
    }

    private fun createLayout(name: String, vararg fields: FieldDescriptor): ObjectLayout {
        val dataSize = if (fields.isEmpty()) {
            8
        } else {
            fields.maxOf { it.offset + it.size }
        }
        return ObjectLayout(name, dataSize, fields.toList())
    }

    @Test
    fun finalizerCalledOnDeadObject() {
        val layout = createLayout("Buffer",
            FieldDescriptor("data", 0, 8, false),
            FieldDescriptor("size", 8, 4, false),
        )
        val typeId = registry.register(layout)
        val address = heap.allocate(layout.copy(typeId = typeId))

        heap.writeField(address, 0, 0xDEADBEEFL)
        heap.writeField(address, 1, 42L)

        var finalizerCalled = false
        var finalizedAddress = 0L
        gc.registerFinalizer(typeId) { objectAddress ->
            finalizerCalled = true
            finalizedAddress = objectAddress
        }

        gc.collect()

        assertTrue(finalizerCalled)
        assertEquals(address, finalizedAddress)
    }

    @Test
    fun finalizerNotCalledOnLiveObject() {
        val layout = createLayout("LiveObj",
            FieldDescriptor("value", 0, 8, false),
        )
        val typeId = registry.register(layout)
        val address = heap.allocate(layout.copy(typeId = typeId))

        gc.addRootProvider { visitor -> visitor.visitRoot(address) }

        var finalizerCalled = false
        gc.registerFinalizer(typeId) { finalizerCalled = true }

        gc.collect()

        assertTrue(!finalizerCalled)
    }

    @Test
    fun finalizerCanFreeInternalResources() {
        val bufferLayout = createLayout("RawBuffer",
            FieldDescriptor("ptr", 0, 8, false),
        )
        val bufferTypeId = registry.register(bufferLayout)

        val stringLayout = createLayout("ManagedString",
            FieldDescriptor("data", 0, 8, false),
            FieldDescriptor("length", 8, 4, false),
        )
        val stringTypeId = registry.register(stringLayout)

        val rawBuffer = heap.allocate(bufferLayout.copy(typeId = bufferTypeId))
        val managedString = heap.allocate(stringLayout.copy(typeId = stringTypeId))

        heap.writeField(managedString, 0, rawBuffer)

        val freedAddresses = mutableListOf<Long>()
        gc.registerFinalizer(stringTypeId) { objectAddress ->
            val dataPtr = heap.readField(objectAddress, 0)
            if (dataPtr != 0L) {
                freedAddresses.add(dataPtr)
            }
        }

        gc.collect()

        assertTrue(freedAddresses.contains(rawBuffer))
    }

    @Test
    fun multipleFinalizersForDifferentTypes() {
        val typeALayout = createLayout("TypeA", FieldDescriptor("x", 0, 8, false))
        val typeAId = registry.register(typeALayout)
        val typeBLayout = createLayout("TypeB", FieldDescriptor("y", 0, 8, false))
        val typeBId = registry.register(typeBLayout)

        heap.allocate(typeALayout.copy(typeId = typeAId))
        heap.allocate(typeBLayout.copy(typeId = typeBId))

        val finalized = mutableSetOf<String>()
        gc.registerFinalizer(typeAId) { finalized.add("TypeA") }
        gc.registerFinalizer(typeBId) { finalized.add("TypeB") }

        gc.collect()

        assertTrue(finalized.contains("TypeA"))
        assertTrue(finalized.contains("TypeB"))
    }

    @Test
    fun finalizerCalledBeforeMemoryReclaimed() {
        val layout = createLayout("Tracked",
            FieldDescriptor("data", 0, 8, false),
        )
        val typeId = registry.register(layout)
        val address = heap.allocate(layout.copy(typeId = typeId))

        heap.writeField(address, 0, 12345L)

        var dataValueDuringFinalization = 0L
        gc.registerFinalizer(typeId) { objectAddress ->
            dataValueDuringFinalization = heap.readField(objectAddress, 0)
        }

        gc.collect()

        assertEquals(12345L, dataValueDuringFinalization)
    }

    @Test
    fun noFinalizerRegisteredDoesNotCrash() {
        val layout = createLayout("Plain", FieldDescriptor("x", 0, 8, false))
        val typeId = registry.register(layout)
        heap.allocate(layout.copy(typeId = typeId))

        gc.collect()

        assertTrue(gc.bytesReclaimed() > 0)
    }

    @Test
    fun finalizerOnlyCalledOncePerCollection() {
        val layout = createLayout("OnceOnly", FieldDescriptor("x", 0, 8, false))
        val typeId = registry.register(layout)
        heap.allocate(layout.copy(typeId = typeId))

        var callCount = 0
        gc.registerFinalizer(typeId) { callCount++ }

        gc.collect()
        assertEquals(1, callCount)

        gc.collect()
        assertEquals(1, callCount)
    }

    @Test
    fun liveObjectWithDeadReferenceFinalizesReferenced() {
        val childLayout = createLayout("Child", FieldDescriptor("value", 0, 8, false))
        val childTypeId = registry.register(childLayout)

        val parentLayout = createLayout("Parent",
            FieldDescriptor("child", 0, 8, true),
        )
        val parentTypeId = registry.register(parentLayout)

        val child = heap.allocate(childLayout.copy(typeId = childTypeId))
        val parent = heap.allocate(parentLayout.copy(typeId = parentTypeId))
        heap.writeField(parent, 0, child)

        gc.addRootProvider { visitor -> visitor.visitRoot(parent) }

        var childFinalized = false
        gc.registerFinalizer(childTypeId) { childFinalized = true }

        gc.collect()

        assertFalse(childFinalized)
    }

    @Test
    fun unreachableChainFinalizesAll() {
        val nodeLayout = createLayout("Node",
            FieldDescriptor("next", 0, 8, true),
            FieldDescriptor("data", 8, 8, false),
        )
        val nodeTypeId = registry.register(nodeLayout)

        val node1 = heap.allocate(nodeLayout.copy(typeId = nodeTypeId))
        val node2 = heap.allocate(nodeLayout.copy(typeId = nodeTypeId))
        heap.writeField(node1, 0, node2)

        val finalizedNodes = mutableListOf<Long>()
        gc.registerFinalizer(nodeTypeId) { addr -> finalizedNodes.add(addr) }

        gc.collect()

        assertEquals(2, finalizedNodes.size)
        assertTrue(finalizedNodes.contains(node1))
        assertTrue(finalizedNodes.contains(node2))
    }
}
