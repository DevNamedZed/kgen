package org.kgen.runtime.gc

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ObjectLayoutAndRegistryTest {

    @Nested
    inner class ObjectLayoutTests {

        @Test
        fun totalSizeWithDefaultAlignment() {
            val layout = ObjectLayout("Point", 16, listOf(), alignment = 8)
            // raw = 8 (header) + 16 = 24, aligned to 8 = 24
            assertEquals(24, layout.totalSize())
        }

        @Test
        fun totalSizeRoundsUpToAlignment() {
            // size=10, alignment=8 => raw = 8 + 10 = 18, aligned to 8 = 24
            val layout = ObjectLayout("Odd", 10, listOf(), alignment = 8)
            assertEquals(24, layout.totalSize())
        }

        @Test
        fun totalSizeWithAlignment16() {
            // size=8, alignment=16 => raw = 8 + 8 = 16, aligned to 16 = 16
            val layout = ObjectLayout("Aligned16", 8, listOf(), alignment = 16)
            assertEquals(16, layout.totalSize())
        }

        @Test
        fun totalSizeWithAlignment16NeedsRoundUp() {
            // size=16, alignment=16 => raw = 8 + 16 = 24, aligned to 16 = 32
            val layout = ObjectLayout("Aligned16Big", 16, listOf(), alignment = 16)
            assertEquals(32, layout.totalSize())
        }

        @Test
        fun totalSizeWithAlignment1() {
            // alignment=1 means no rounding
            val layout = ObjectLayout("NoAlign", 10, listOf(), alignment = 1)
            assertEquals(18, layout.totalSize()) // 8 + 10 = 18, no alignment
        }

        @Test
        fun totalSizeZeroData() {
            val layout = ObjectLayout("Empty", 0, listOf())
            assertEquals(8, layout.totalSize()) // just header
        }

        @Test
        fun totalSizeAlignment4() {
            // size=5, alignment=4 => raw = 8 + 5 = 13, aligned to 4 = 16
            val layout = ObjectLayout("Small", 5, listOf(), alignment = 4)
            assertEquals(16, layout.totalSize())
        }

        @Test
        fun defaultAlignmentIs8() {
            val layout = ObjectLayout("Default", 8, listOf())
            assertEquals(8, layout.alignment)
        }

        @Test
        fun defaultTypeIdIsZero() {
            val layout = ObjectLayout("Default", 8, listOf())
            assertEquals(0, layout.typeId)
        }

        @Test
        fun dataClassEquality() {
            val a = ObjectLayout("Test", 16, listOf(
                FieldDescriptor("x", 0, 8, false)
            ), typeId = 1)
            val b = ObjectLayout("Test", 16, listOf(
                FieldDescriptor("x", 0, 8, false)
            ), typeId = 1)
            assertEquals(a, b)
            assertEquals(a.hashCode(), b.hashCode())
        }

        @Test
        fun dataClassInequalityOnTypeId() {
            val a = ObjectLayout("Test", 16, listOf(), typeId = 1)
            val b = ObjectLayout("Test", 16, listOf(), typeId = 2)
            assertNotEquals(a, b)
        }

        @Test
        fun dataClassInequalityOnName() {
            val a = ObjectLayout("A", 16, listOf())
            val b = ObjectLayout("B", 16, listOf())
            assertNotEquals(a, b)
        }

        @Test
        fun copyChangesFields() {
            val original = ObjectLayout("Orig", 16, listOf(), typeId = 1, alignment = 8)
            val copied = original.copy(name = "Copied", typeId = 2)
            assertEquals("Copied", copied.name)
            assertEquals(2, copied.typeId)
            assertEquals(16, copied.size)
            assertEquals(8, copied.alignment)
        }

        @Test
        fun fieldsListPreserved() {
            val fields = listOf(
                FieldDescriptor("a", 0, 4, false),
                FieldDescriptor("b", 4, 4, false),
                FieldDescriptor("ref", 8, 8, true),
            )
            val layout = ObjectLayout("WithFields", 16, fields)
            assertEquals(3, layout.fields.size)
            assertEquals("a", layout.fields[0].name)
            assertEquals("ref", layout.fields[2].name)
            assertTrue(layout.fields[2].isReference)
            assertFalse(layout.fields[0].isReference)
        }

        @Test
        fun headerSizeConstant() {
            assertEquals(8, ObjectLayout.HEADER_SIZE)
        }
    }

    @Nested
    inner class FieldDescriptorTests {

        @Test
        fun propertiesAccessible() {
            val fd = FieldDescriptor("counter", 12, 4, false)
            assertEquals("counter", fd.name)
            assertEquals(12, fd.offset)
            assertEquals(4, fd.size)
            assertFalse(fd.isReference)
        }

        @Test
        fun referenceField() {
            val fd = FieldDescriptor("next", 8, 8, true)
            assertTrue(fd.isReference)
        }

        @Test
        fun dataClassEquality() {
            val a = FieldDescriptor("x", 0, 8, false)
            val b = FieldDescriptor("x", 0, 8, false)
            assertEquals(a, b)
            assertEquals(a.hashCode(), b.hashCode())
        }

        @Test
        fun dataClassInequality() {
            val a = FieldDescriptor("x", 0, 8, false)
            val b = FieldDescriptor("y", 0, 8, false)
            assertNotEquals(a, b)
        }

        @Test
        fun copyChangesName() {
            val fd = FieldDescriptor("old", 0, 8, false)
            val copied = fd.copy(name = "new")
            assertEquals("new", copied.name)
            assertEquals(0, copied.offset)
        }

        @Test
        fun zeroOffsetAndSize() {
            val fd = FieldDescriptor("zero", 0, 0, false)
            assertEquals(0, fd.offset)
            assertEquals(0, fd.size)
        }
    }

    @Nested
    inner class TypeRegistryTests {

        @Test
        fun registerAutoIdStartsAtOne() {
            val registry = TypeRegistry()
            val id = registry.register(ObjectLayout("First", 8, listOf()))
            assertEquals(1, id)
        }

        @Test
        fun registerAutoIdIncrementsSequentially() {
            val registry = TypeRegistry()
            val id1 = registry.register(ObjectLayout("A", 8, listOf()))
            val id2 = registry.register(ObjectLayout("B", 8, listOf()))
            val id3 = registry.register(ObjectLayout("C", 8, listOf()))
            assertEquals(1, id1)
            assertEquals(2, id2)
            assertEquals(3, id3)
        }

        @Test
        fun registerExplicitIdUsesProvidedId() {
            val registry = TypeRegistry()
            val id = registry.register(ObjectLayout("Explicit", 8, listOf(), typeId = 42))
            assertEquals(42, id)
        }

        @Test
        fun registerExplicitIdDoesNotAffectAutoIdCounter() {
            val registry = TypeRegistry()
            registry.register(ObjectLayout("Explicit", 8, listOf(), typeId = 100))
            val autoId = registry.register(ObjectLayout("Auto", 8, listOf()))
            assertEquals(1, autoId)
        }

        @Test
        fun lookupReturnsLayoutWithAssignedId() {
            val registry = TypeRegistry()
            val id = registry.register(ObjectLayout("MyType", 16, listOf()))
            val result = registry.lookup(id)
            assertNotNull(result)
            assertEquals("MyType", result!!.name)
            assertEquals(id, result.typeId)
        }

        @Test
        fun lookupNonexistentReturnsNull() {
            val registry = TypeRegistry()
            assertNull(registry.lookup(999))
        }

        @Test
        fun allTypesReturnsAllRegistered() {
            val registry = TypeRegistry()
            registry.register(ObjectLayout("A", 8, listOf(), typeId = 1))
            registry.register(ObjectLayout("B", 16, listOf(), typeId = 2))
            registry.register(ObjectLayout("C", 24, listOf(), typeId = 3))
            val all = registry.allTypes()
            assertEquals(3, all.size)
            val names = all.map { it.name }.toSet()
            assertTrue(names.contains("A"))
            assertTrue(names.contains("B"))
            assertTrue(names.contains("C"))
        }

        @Test
        fun allTypesEmptyInitially() {
            val registry = TypeRegistry()
            assertTrue(registry.allTypes().isEmpty())
        }

        @Test
        fun overwriteSameIdReplacesLayout() {
            val registry = TypeRegistry()
            registry.register(ObjectLayout("Old", 8, listOf(), typeId = 5))
            registry.register(ObjectLayout("New", 16, listOf(), typeId = 5))
            val result = registry.lookup(5)
            assertEquals("New", result!!.name)
            assertEquals(16, result.size)
        }

        @Test
        fun registeredLayoutHasCorrectTypeId() {
            val registry = TypeRegistry()
            val layout = ObjectLayout("NoId", 8, listOf())
            val id = registry.register(layout)
            val stored = registry.lookup(id)!!
            assertEquals(id, stored.typeId)
        }

        @Test
        fun registeredLayoutPreservesFields() {
            val registry = TypeRegistry()
            val fields = listOf(
                FieldDescriptor("x", 0, 8, false),
                FieldDescriptor("ptr", 8, 8, true),
            )
            val id = registry.register(ObjectLayout("Node", 16, fields))
            val stored = registry.lookup(id)!!
            assertEquals(2, stored.fields.size)
            assertEquals("ptr", stored.fields[1].name)
            assertTrue(stored.fields[1].isReference)
        }

        @Test
        fun manyAutoRegistrations() {
            val registry = TypeRegistry()
            val ids = (0 until 100).map { i ->
                registry.register(ObjectLayout("Type$i", 8, listOf()))
            }
            assertEquals(100, ids.toSet().size)
            for (id in ids) {
                assertNotNull(registry.lookup(id))
            }
        }
    }

    @Nested
    inner class BumpHeapFreeListTests {

        private fun simpleLayout(typeId: Int = 1) = ObjectLayout(
            name = "Simple", size = 8,
            fields = listOf(FieldDescriptor("value", 0, 8, false)),
            typeId = typeId,
        )

        @Test
        fun freeBlockReuseWritesCorrectHeader() {
            BumpHeap(4096).use { heap ->
                val layout = simpleLayout(7)
                val addr = heap.allocate(layout)
                heap.addFreeBlock(addr, layout.totalSize().toLong())

                val reused = heap.allocate(simpleLayout(13))
                assertEquals(13, heap.typeIdAt(reused))
                assertEquals(0, heap.gcFlagsAt(reused))
            }
        }

        @Test
        fun freeBlockCountAfterAllocAndFree() {
            BumpHeap(4096).use { heap ->
                val layout = simpleLayout()
                assertEquals(0, heap.freeBlockCount())

                val a = heap.allocate(layout)
                val b = heap.allocate(layout)
                heap.addFreeBlock(a, layout.totalSize().toLong())
                assertEquals(1, heap.freeBlockCount())

                heap.addFreeBlock(b, layout.totalSize().toLong())
                assertEquals(2, heap.freeBlockCount())
            }
        }

        @Test
        fun freeBytesAccumulates() {
            BumpHeap(4096).use { heap ->
                val layout = simpleLayout()
                val size = layout.totalSize().toLong()
                val a = heap.allocate(layout)
                val b = heap.allocate(layout)
                val c = heap.allocate(layout)

                heap.addFreeBlock(a, size)
                assertEquals(size, heap.freeBytes())

                heap.addFreeBlock(b, size)
                assertEquals(size * 2, heap.freeBytes())

                heap.addFreeBlock(c, size)
                assertEquals(size * 3, heap.freeBytes())
            }
        }

        @Test
        fun allocateFromFreeListDoesNotAdvanceCursor() {
            BumpHeap(4096).use { heap ->
                val layout = simpleLayout()
                val addr = heap.allocate(layout)
                val cursorBefore = heap.bytesInUse()

                heap.addFreeBlock(addr, layout.totalSize().toLong())
                heap.allocate(layout)

                assertEquals(cursorBefore, heap.bytesInUse())
            }
        }

        @Test
        fun resetClearsFreeList() {
            BumpHeap(4096).use { heap ->
                val layout = simpleLayout()
                val addr = heap.allocate(layout)
                heap.addFreeBlock(addr, layout.totalSize().toLong())
                assertEquals(1, heap.freeBlockCount())

                heap.reset()
                assertEquals(0, heap.freeBlockCount())
                assertEquals(0L, heap.freeBytes())
            }
        }
    }

    @Nested
    inner class MarkSweepGCModelTests {

        private fun simpleLayout(typeId: Int = 1) = ObjectLayout(
            name = "Simple", size = 8,
            fields = listOf(FieldDescriptor("value", 0, 8, false)),
            typeId = typeId,
        )

        @Test
        fun writeBarrierIsNoOp() {
            BumpHeap(4096).use { heap ->
                val registry = TypeRegistry()
                val gc = MarkSweepGC(heap, registry)
                // Should not throw
                gc.writeBarrier(0L, 0, 0L)
            }
        }

        @Test
        fun readBarrierReturnsInput() {
            BumpHeap(4096).use { heap ->
                val registry = TypeRegistry()
                val gc = MarkSweepGC(heap, registry)
                assertEquals(12345L, gc.readBarrier(12345L))
                assertEquals(0L, gc.readBarrier(0L))
                assertEquals(-1L, gc.readBarrier(-1L))
            }
        }

        @Test
        fun collectionCountStartsAtZero() {
            BumpHeap(4096).use { heap ->
                val registry = TypeRegistry()
                val gc = MarkSweepGC(heap, registry)
                assertEquals(0L, gc.collectionCount())
            }
        }

        @Test
        fun collectionCountIncrements() {
            BumpHeap(4096).use { heap ->
                val registry = TypeRegistry()
                val gc = MarkSweepGC(heap, registry)
                gc.collect()
                gc.collect()
                gc.collect()
                assertEquals(3L, gc.collectionCount())
            }
        }

        @Test
        fun bytesReclaimedStartsAtZero() {
            BumpHeap(4096).use { heap ->
                val registry = TypeRegistry()
                val gc = MarkSweepGC(heap, registry)
                assertEquals(0L, gc.bytesReclaimed())
            }
        }

        @Test
        fun executionContextCountStartsAtZero() {
            BumpHeap(4096).use { heap ->
                val registry = TypeRegistry()
                val gc = MarkSweepGC(heap, registry)
                assertEquals(0, gc.executionContextCount())
            }
        }

        @Test
        fun stackMapsInitiallyEmpty() {
            BumpHeap(4096).use { heap ->
                val registry = TypeRegistry()
                val gc = MarkSweepGC(heap, registry)
                assertTrue(gc.stackMaps().isEmpty())
            }
        }

        @Test
        fun registerStackMapStoresIt() {
            BumpHeap(4096).use { heap ->
                val registry = TypeRegistry()
                val gc = MarkSweepGC(heap, registry)
                val stackMap = org.kgen.ir.StackMap("myFunc", listOf(
                    org.kgen.ir.StackMapEntry(0, listOf(org.kgen.ir.StackMapLocation.Constant(42L)))
                ))
                gc.registerStackMap(stackMap)
                assertEquals(1, gc.stackMaps().size)
                assertTrue(gc.stackMaps().containsKey("myFunc"))
            }
        }

        @Test
        fun sweepReclaimsUnrootedObject() {
            BumpHeap(4096).use { heap ->
                val registry = TypeRegistry()
                val layout = simpleLayout()
                registry.register(layout)

                heap.allocate(layout)

                val gc = MarkSweepGC(heap, registry)
                gc.collect()

                assertTrue(gc.bytesReclaimed() > 0)
            }
        }

        @Test
        fun sweepPreservesRootedObject() {
            BumpHeap(4096).use { heap ->
                val registry = TypeRegistry()
                val layout = simpleLayout()
                registry.register(layout)

                val obj = heap.allocate(layout)
                heap.writeField(obj, 0, 99L)

                val gc = MarkSweepGC(heap, registry)
                gc.addRootProvider { visitor -> visitor.visitRoot(obj) }
                gc.collect()

                assertEquals(0L, gc.bytesReclaimed())
                assertEquals(99L, heap.readField(obj, 0))
            }
        }

        @Test
        fun compactAndForwardIncreasesCollectionCount() {
            BumpHeap(4096).use { heap ->
                val registry = TypeRegistry()
                val layout = simpleLayout()
                registry.register(layout)
                val obj = heap.allocate(layout)

                val gc = MarkSweepGC(heap, registry)
                gc.addRootProvider { visitor -> visitor.visitRoot(obj) }
                gc.compactAndForward(mutableListOf(obj))
                assertEquals(1L, gc.collectionCount())
            }
        }

        @Test
        fun compactAndForwardEmptyForwardingWhenAllLive() {
            BumpHeap(4096).use { heap ->
                val registry = TypeRegistry()
                val layout = simpleLayout()
                registry.register(layout)

                val a = heap.allocate(layout)
                val b = heap.allocate(layout)

                val roots = mutableListOf(a, b)
                val gc = MarkSweepGC(heap, registry)
                gc.addRootProvider { visitor ->
                    for (r in roots) {
                        visitor.visitRoot(r)
                    }
                }
                val forwarding = gc.compactAndForward(roots)
                assertTrue(forwarding.isEmpty())
            }
        }

        @Test
        fun collectOnEmptyHeapDoesNotCrash() {
            BumpHeap(4096).use { heap ->
                val registry = TypeRegistry()
                val gc = MarkSweepGC(heap, registry)
                gc.collect()
                assertEquals(1L, gc.collectionCount())
                assertEquals(0L, gc.bytesReclaimed())
            }
        }
    }
}
