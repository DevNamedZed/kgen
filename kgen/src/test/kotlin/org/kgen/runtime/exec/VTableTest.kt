package org.kgen.runtime.exec

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class VTableTest {

    @Nested
    inner class Construction {

        @Test
        fun createWithSlots() {
            val vtable = VTable(1, longArrayOf(0x100, 0x200, 0x300))
            assertEquals(1, vtable.typeId)
            assertEquals(3, vtable.slots.size)
            assertEquals(0x100L, vtable.slots[0])
            assertEquals(0x200L, vtable.slots[1])
            assertEquals(0x300L, vtable.slots[2])
        }

        @Test
        fun createWithEmptySlots() {
            val vtable = VTable(42, longArrayOf())
            assertEquals(42, vtable.typeId)
            assertEquals(0, vtable.slots.size)
        }

        @Test
        fun createWithSingleSlot() {
            val vtable = VTable(0, longArrayOf(0xDEADBEEF))
            assertEquals(0, vtable.typeId)
            assertEquals(1, vtable.slots.size)
            assertEquals(0xDEADBEEFL, vtable.slots[0])
        }
    }

    @Nested
    inner class Equality {

        @Test
        fun sameTypeIdAreEqual() {
            val v1 = VTable(5, longArrayOf(100, 200))
            val v2 = VTable(5, longArrayOf(300, 400, 500))
            assertEquals(v1, v2)
        }

        @Test
        fun differentTypeIdAreNotEqual() {
            val v1 = VTable(1, longArrayOf(100))
            val v2 = VTable(2, longArrayOf(100))
            assertNotEquals(v1, v2)
        }

        @Test
        fun hashCodeConsistentWithEquals() {
            val v1 = VTable(7, longArrayOf(100))
            val v2 = VTable(7, longArrayOf(999))
            assertEquals(v1.hashCode(), v2.hashCode())
        }

        @Test
        fun notEqualToNull() {
            val vtable = VTable(1, longArrayOf())
            assertNotEquals(vtable, null)
        }

        @Test
        fun notEqualToDifferentType() {
            val vtable = VTable(1, longArrayOf())
            assertNotEquals(vtable as Any, "not a vtable")
        }

        @Test
        fun canBeUsedAsMapKey() {
            val map = mutableMapOf<VTable, String>()
            val v1 = VTable(1, longArrayOf(100))
            val v2 = VTable(1, longArrayOf(200))
            map[v1] = "first"
            map[v2] = "second"
            assertEquals(1, map.size)
            assertEquals("second", map[v1])
        }
    }
}
