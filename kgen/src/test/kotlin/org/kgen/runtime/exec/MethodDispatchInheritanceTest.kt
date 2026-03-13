package org.kgen.runtime.exec

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class MethodDispatchInheritanceTest {

    @Nested
    inner class VTableInheritance {

        @Test
        fun lookupWalksToParent() {
            val dispatch = BasicMethodDispatch()
            dispatch.registerVTable(1, VTable(1, longArrayOf(0x100, 0x200)))
            dispatch.registerParent(2, 1)

            assertEquals(0x100L, dispatch.virtualLookup(2, 0))
            assertEquals(0x200L, dispatch.virtualLookup(2, 1))
        }

        @Test
        fun lookupWalksMultipleLevels() {
            val dispatch = BasicMethodDispatch()
            dispatch.registerVTable(1, VTable(1, longArrayOf(0x100)))
            dispatch.registerParent(2, 1)
            dispatch.registerParent(3, 2)

            assertEquals(0x100L, dispatch.virtualLookup(3, 0))
        }

        @Test
        fun childOverridesParentSlot() {
            val dispatch = BasicMethodDispatch()
            dispatch.registerVTable(1, VTable(1, longArrayOf(0x100, 0x200)))
            dispatch.registerVTable(2, VTable(2, longArrayOf(0x300, 0x400)))
            dispatch.registerParent(2, 1)

            assertEquals(0x300L, dispatch.virtualLookup(2, 0))
            assertEquals(0x400L, dispatch.virtualLookup(2, 1))
        }

        @Test
        fun childHasFewerSlotsFallsToParent() {
            val dispatch = BasicMethodDispatch()
            dispatch.registerVTable(1, VTable(1, longArrayOf(0x100, 0x200, 0x300)))
            dispatch.registerVTable(2, VTable(2, longArrayOf(0x400)))
            dispatch.registerParent(2, 1)

            assertEquals(0x400L, dispatch.virtualLookup(2, 0))
            assertEquals(0x200L, dispatch.virtualLookup(2, 1))
            assertEquals(0x300L, dispatch.virtualLookup(2, 2))
        }

        @Test
        fun noParentNoVtableThrows() {
            val dispatch = BasicMethodDispatch()
            assertThrows(IllegalArgumentException::class.java) {
                dispatch.virtualLookup(99, 0)
            }
        }

        @Test
        fun slotOutOfRangeInEntireChainThrows() {
            val dispatch = BasicMethodDispatch()
            dispatch.registerVTable(1, VTable(1, longArrayOf(0x100)))
            dispatch.registerVTable(2, VTable(2, longArrayOf(0x200)))
            dispatch.registerParent(2, 1)

            assertThrows(IndexOutOfBoundsException::class.java) {
                dispatch.virtualLookup(2, 5)
            }
        }
    }

    @Nested
    inner class ITableInheritance {

        @Test
        fun lookupWalksToParent() {
            val dispatch = BasicMethodDispatch()
            dispatch.registerITable(10, 1, ITable(10, 1, longArrayOf(0x500)))
            dispatch.registerParent(2, 1)

            assertEquals(0x500L, dispatch.interfaceLookup(10, 2, 0))
        }

        @Test
        fun lookupWalksMultipleLevels() {
            val dispatch = BasicMethodDispatch()
            dispatch.registerITable(10, 1, ITable(10, 1, longArrayOf(0x500)))
            dispatch.registerParent(2, 1)
            dispatch.registerParent(3, 2)

            assertEquals(0x500L, dispatch.interfaceLookup(10, 3, 0))
        }

        @Test
        fun childOverridesParentInterface() {
            val dispatch = BasicMethodDispatch()
            dispatch.registerITable(10, 1, ITable(10, 1, longArrayOf(0x500)))
            dispatch.registerITable(10, 2, ITable(10, 2, longArrayOf(0x600)))
            dispatch.registerParent(2, 1)

            assertEquals(0x600L, dispatch.interfaceLookup(10, 2, 0))
        }

        @Test
        fun childHasFewerSlotsFallsToParent() {
            val dispatch = BasicMethodDispatch()
            dispatch.registerITable(10, 1, ITable(10, 1, longArrayOf(0x500, 0x600)))
            dispatch.registerITable(10, 2, ITable(10, 2, longArrayOf(0x700)))
            dispatch.registerParent(2, 1)

            assertEquals(0x700L, dispatch.interfaceLookup(10, 2, 0))
            assertEquals(0x600L, dispatch.interfaceLookup(10, 2, 1))
        }

        @Test
        fun noParentNoItableThrows() {
            val dispatch = BasicMethodDispatch()
            assertThrows(IllegalArgumentException::class.java) {
                dispatch.interfaceLookup(10, 99, 0)
            }
        }

        @Test
        fun slotOutOfRangeInEntireChainThrows() {
            val dispatch = BasicMethodDispatch()
            dispatch.registerITable(10, 1, ITable(10, 1, longArrayOf(0x500)))
            dispatch.registerParent(2, 1)

            assertThrows(IndexOutOfBoundsException::class.java) {
                dispatch.interfaceLookup(10, 2, 5)
            }
        }
    }

    @Nested
    inner class MethodCountAndVTableCount {

        @Test
        fun methodCountStartsAtZero() {
            val dispatch = BasicMethodDispatch()
            assertEquals(0, dispatch.methodCount())
        }

        @Test
        fun vtableCountStartsAtZero() {
            val dispatch = BasicMethodDispatch()
            assertEquals(0, dispatch.vtableCount())
        }

        @Test
        fun countIncrementsWithRegistrations() {
            val dispatch = BasicMethodDispatch()
            dispatch.register("a", 100)
            dispatch.register("b", 200)
            assertEquals(2, dispatch.methodCount())

            dispatch.registerVTable(1, VTable(1, longArrayOf()))
            dispatch.registerVTable(2, VTable(2, longArrayOf()))
            assertEquals(2, dispatch.vtableCount())
        }

        @Test
        fun overwriteDoesNotIncrementCount() {
            val dispatch = BasicMethodDispatch()
            dispatch.register("a", 100)
            dispatch.register("a", 200)
            assertEquals(1, dispatch.methodCount())
        }
    }
}
