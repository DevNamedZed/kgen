package org.kgen.runtime.exec

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class BasicMethodDispatchTest {

    @Test
    fun resolveUnknownReturnsNull() {
        val dispatch = BasicMethodDispatch()
        assertNull(dispatch.resolve("nonexistent"))
    }

    @Test
    fun registerAndResolve() {
        val dispatch = BasicMethodDispatch()
        dispatch.register("add", 0x1000)
        assertEquals(0x1000L, dispatch.resolve("add"))
    }

    @Test
    fun overwriteExisting() {
        val dispatch = BasicMethodDispatch()
        dispatch.register("add", 0x1000)
        dispatch.register("add", 0x2000)
        assertEquals(0x2000L, dispatch.resolve("add"))
    }

    @Test
    fun multipleRegistrations() {
        val dispatch = BasicMethodDispatch()
        dispatch.register("add", 0x1000)
        dispatch.register("sub", 0x2000)
        dispatch.register("mul", 0x3000)
        assertEquals(3, dispatch.methodCount())
        assertEquals(0x1000L, dispatch.resolve("add"))
        assertEquals(0x2000L, dispatch.resolve("sub"))
        assertEquals(0x3000L, dispatch.resolve("mul"))
    }

    @Test
    fun vtableRegistration() {
        val dispatch = BasicMethodDispatch()
        val vtable = VTable(1, longArrayOf(100, 200, 300))
        dispatch.registerVTable(1, vtable)
        assertEquals(1, dispatch.vtableCount())
    }

    @Test
    fun vtableLookup() {
        val dispatch = BasicMethodDispatch()
        val vtable = VTable(1, longArrayOf(100, 200, 300))
        dispatch.registerVTable(1, vtable)
        assertEquals(100L, dispatch.virtualLookup(1, 0))
        assertEquals(200L, dispatch.virtualLookup(1, 1))
        assertEquals(300L, dispatch.virtualLookup(1, 2))
    }

    @Test
    fun vtableLookupMissingTypeThrows() {
        val dispatch = BasicMethodDispatch()
        assertThrows(IllegalArgumentException::class.java) {
            dispatch.virtualLookup(99, 0)
        }
    }

    @Test
    fun vtableLookupOutOfBoundsThrows() {
        val dispatch = BasicMethodDispatch()
        dispatch.registerVTable(1, VTable(1, longArrayOf(100)))
        assertThrows(IndexOutOfBoundsException::class.java) {
            dispatch.virtualLookup(1, 5)
        }
    }

    @Test
    fun itableRegistration() {
        val dispatch = BasicMethodDispatch()
        val itable = ITable(10, 1, longArrayOf(500, 600))
        dispatch.registerITable(10, 1, itable)
    }

    @Test
    fun itableLookup() {
        val dispatch = BasicMethodDispatch()
        val itable = ITable(10, 1, longArrayOf(500, 600))
        dispatch.registerITable(10, 1, itable)
        assertEquals(500L, dispatch.interfaceLookup(10, 1, 0))
        assertEquals(600L, dispatch.interfaceLookup(10, 1, 1))
    }

    @Test
    fun itableLookupMissingThrows() {
        val dispatch = BasicMethodDispatch()
        assertThrows(IllegalArgumentException::class.java) {
            dispatch.interfaceLookup(10, 1, 0)
        }
    }

    @Test
    fun itableLookupOutOfBoundsThrows() {
        val dispatch = BasicMethodDispatch()
        dispatch.registerITable(10, 1, ITable(10, 1, longArrayOf(500)))
        assertThrows(IndexOutOfBoundsException::class.java) {
            dispatch.interfaceLookup(10, 1, 5)
        }
    }

    @Test
    fun vtableForMultipleTypes() {
        val dispatch = BasicMethodDispatch()
        dispatch.registerVTable(1, VTable(1, longArrayOf(100, 200)))
        dispatch.registerVTable(2, VTable(2, longArrayOf(300, 400)))
        assertEquals(100L, dispatch.virtualLookup(1, 0))
        assertEquals(300L, dispatch.virtualLookup(2, 0))
    }

    @Test
    fun itableForMultipleInterfacesAndTypes() {
        val dispatch = BasicMethodDispatch()
        dispatch.registerITable(10, 1, ITable(10, 1, longArrayOf(100)))
        dispatch.registerITable(10, 2, ITable(10, 2, longArrayOf(200)))
        dispatch.registerITable(20, 1, ITable(20, 1, longArrayOf(300)))
        assertEquals(100L, dispatch.interfaceLookup(10, 1, 0))
        assertEquals(200L, dispatch.interfaceLookup(10, 2, 0))
        assertEquals(300L, dispatch.interfaceLookup(20, 1, 0))
    }

    @Test
    fun negativeSlotIndexThrows() {
        val dispatch = BasicMethodDispatch()
        dispatch.registerVTable(1, VTable(1, longArrayOf(100)))
        assertThrows(IndexOutOfBoundsException::class.java) {
            dispatch.virtualLookup(1, -1)
        }
    }
}
