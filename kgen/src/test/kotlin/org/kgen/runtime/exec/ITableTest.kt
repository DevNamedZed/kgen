package org.kgen.runtime.exec

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class ITableTest {

    @Nested
    inner class Construction {

        @Test
        fun createWithSlots() {
            val itable = ITable(10, 1, longArrayOf(0x100, 0x200))
            assertEquals(10, itable.interfaceId)
            assertEquals(1, itable.typeId)
            assertEquals(2, itable.slots.size)
            assertEquals(0x100L, itable.slots[0])
            assertEquals(0x200L, itable.slots[1])
        }

        @Test
        fun createWithEmptySlots() {
            val itable = ITable(5, 3, longArrayOf())
            assertEquals(5, itable.interfaceId)
            assertEquals(3, itable.typeId)
            assertEquals(0, itable.slots.size)
        }

        @Test
        fun createWithSingleSlot() {
            val itable = ITable(0, 0, longArrayOf(0xCAFE))
            assertEquals(0, itable.interfaceId)
            assertEquals(0, itable.typeId)
            assertEquals(0xCAFEL, itable.slots[0])
        }
    }

    @Nested
    inner class Equality {

        @Test
        fun sameInterfaceAndTypeIdAreEqual() {
            val i1 = ITable(10, 1, longArrayOf(100))
            val i2 = ITable(10, 1, longArrayOf(999))
            assertEquals(i1, i2)
        }

        @Test
        fun differentInterfaceIdAreNotEqual() {
            val i1 = ITable(10, 1, longArrayOf(100))
            val i2 = ITable(20, 1, longArrayOf(100))
            assertNotEquals(i1, i2)
        }

        @Test
        fun differentTypeIdAreNotEqual() {
            val i1 = ITable(10, 1, longArrayOf(100))
            val i2 = ITable(10, 2, longArrayOf(100))
            assertNotEquals(i1, i2)
        }

        @Test
        fun hashCodeConsistentWithEquals() {
            val i1 = ITable(10, 1, longArrayOf(100))
            val i2 = ITable(10, 1, longArrayOf(999))
            assertEquals(i1.hashCode(), i2.hashCode())
        }

        @Test
        fun hashCodeDiffersForDifferentIds() {
            val i1 = ITable(10, 1, longArrayOf())
            val i2 = ITable(10, 2, longArrayOf())
            assertNotEquals(i1.hashCode(), i2.hashCode())
        }

        @Test
        fun notEqualToNull() {
            val itable = ITable(1, 1, longArrayOf())
            assertNotEquals(itable, null)
        }

        @Test
        fun notEqualToDifferentType() {
            val itable = ITable(1, 1, longArrayOf())
            assertNotEquals(itable as Any, "not an itable")
        }

        @Test
        fun canBeUsedAsMapKey() {
            val map = mutableMapOf<ITable, String>()
            val i1 = ITable(10, 1, longArrayOf(100))
            val i2 = ITable(10, 1, longArrayOf(200))
            map[i1] = "first"
            map[i2] = "second"
            assertEquals(1, map.size)
            assertEquals("second", map[i1])
        }
    }
}
