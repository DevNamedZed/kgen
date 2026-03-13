package org.kgen.ir

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.instructions.*

class AddressSpaceTest {

    @Test
    fun wellKnownConstants() {
        assertEquals(0, AddressSpace.GENERIC)
        assertEquals(1, AddressSpace.MANAGED)
        assertEquals(2, AddressSpace.GPU_GLOBAL)
        assertEquals(3, AddressSpace.GPU_SHARED)
        assertEquals(4, AddressSpace.GPU_CONSTANT)
        assertEquals(5, AddressSpace.THREAD_LOCAL)
        assertEquals(256, AddressSpace.USER_START)
    }

    @Test
    fun isManagedAddressSpace() {
        assertTrue(AddressSpace.isManaged(AddressSpace.MANAGED))
        assertFalse(AddressSpace.isManaged(AddressSpace.GENERIC))
        assertFalse(AddressSpace.isManaged(AddressSpace.GPU_GLOBAL))
    }

    @Test
    fun isGpuAddressSpace() {
        assertTrue(AddressSpace.isGpu(AddressSpace.GPU_GLOBAL))
        assertTrue(AddressSpace.isGpu(AddressSpace.GPU_SHARED))
        assertTrue(AddressSpace.isGpu(AddressSpace.GPU_CONSTANT))
        assertFalse(AddressSpace.isGpu(AddressSpace.GENERIC))
        assertFalse(AddressSpace.isGpu(AddressSpace.MANAGED))
        assertFalse(AddressSpace.isGpu(AddressSpace.THREAD_LOCAL))
    }

    @Test
    fun ofPointerType() {
        assertEquals(AddressSpace.GENERIC, AddressSpace.of(Type.Pointer(Type.I32)))
        assertEquals(AddressSpace.MANAGED, AddressSpace.of(Type.Pointer(Type.I32, AddressSpace.MANAGED)))
        assertEquals(AddressSpace.GPU_GLOBAL, AddressSpace.of(Type.Pointer(Type.F32, AddressSpace.GPU_GLOBAL)))
    }

    @Test
    fun ofReferenceTypes() {
        assertEquals(AddressSpace.MANAGED, AddressSpace.of(Type.Reference(Type.I32)))
        assertEquals(AddressSpace.MANAGED, AddressSpace.of(Type.WeakReference(Type.I32)))
        assertEquals(AddressSpace.MANAGED, AddressSpace.of(Type.InteriorRef(Type.I32)))
    }

    @Test
    fun ofPinnedRef() {
        // Pinned refs are safe for native access — address space is GENERIC
        assertEquals(AddressSpace.GENERIC, AddressSpace.of(Type.PinnedRef(Type.I32)))
    }

    @Test
    fun ofNonPointerType() {
        assertEquals(AddressSpace.GENERIC, AddressSpace.of(Type.I32))
        assertEquals(AddressSpace.GENERIC, AddressSpace.of(Type.F64))
        assertEquals(AddressSpace.GENERIC, AddressSpace.of(Type.Void))
    }

    @Test
    fun isManagedType() {
        assertTrue(AddressSpace.isManagedType(Type.Reference(Type.I32)))
        assertTrue(AddressSpace.isManagedType(Type.WeakReference(Type.I32)))
        assertTrue(AddressSpace.isManagedType(Type.InteriorRef(Type.I32)))
        assertTrue(AddressSpace.isManagedType(Type.Pointer(Type.I32, AddressSpace.MANAGED)))
        assertFalse(AddressSpace.isManagedType(Type.Pointer(Type.I32)))
        assertFalse(AddressSpace.isManagedType(Type.PinnedRef(Type.I32)))
        assertFalse(AddressSpace.isManagedType(Type.I32))
    }

    @Test
    fun hasNonDefaultAddressSpace() {
        assertTrue(AddressSpace.hasNonDefaultAddressSpace(Type.Pointer(Type.I32, AddressSpace.MANAGED)))
        assertTrue(AddressSpace.hasNonDefaultAddressSpace(Type.Pointer(Type.I32, AddressSpace.GPU_SHARED)))
        assertFalse(AddressSpace.hasNonDefaultAddressSpace(Type.Pointer(Type.I32)))
        assertFalse(AddressSpace.hasNonDefaultAddressSpace(Type.I32))
        assertFalse(AddressSpace.hasNonDefaultAddressSpace(Type.Reference(Type.I32)))
    }

    @Test
    fun pointerAddressSpaceAffectsEquality() {
        val nativePtr = Type.Pointer(Type.I32, AddressSpace.GENERIC)
        val managedPtr = Type.Pointer(Type.I32, AddressSpace.MANAGED)
        val gpuPtr = Type.Pointer(Type.I32, AddressSpace.GPU_GLOBAL)

        assertNotEquals(nativePtr, managedPtr)
        assertNotEquals(nativePtr, gpuPtr)
        assertNotEquals(managedPtr, gpuPtr)
        assertEquals(nativePtr, Type.Pointer(Type.I32, 0))
    }

    @Test
    fun managedPointerInStruct() {
        val struct = Type.Struct(null, listOf(
            Type.Pointer(Type.I32, AddressSpace.GENERIC),
            Type.Pointer(Type.I32, AddressSpace.MANAGED)
        ), false)

        assertFalse(AddressSpace.isManagedType(struct.fields[0]))
        assertTrue(AddressSpace.isManagedType(struct.fields[1]))
    }

    @Test
    fun addrSpaceCastInstruction() {
        val src = Type.Pointer(Type.I32, AddressSpace.MANAGED)
        val dst = Type.Pointer(Type.I32, AddressSpace.GENERIC)
        val srcRef = InstructionRef("%src", src)
        val dstRef = InstructionRef("%dst", dst)
        val cast = AddrSpaceCast(dstRef, srcRef, dst)

        assertEquals(AddressSpace.MANAGED, AddressSpace.of(cast.value.type))
        assertEquals(AddressSpace.GENERIC, AddressSpace.of(cast.toType))
    }
}
