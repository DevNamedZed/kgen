package org.kgen.ir

/**
 * Well-known address spaces for pointer types.
 *
 * Address space 0 is the default flat/native address space. Non-zero address spaces
 * distinguish managed heap pointers, GPU memory regions, and other target-specific
 * memory areas. Two pointers with different address spaces are distinct types —
 * an explicit [Instruction.AddrSpaceCast] is required to convert between them.
 *
 * ```java
 * // Native pointer (default)
 * var nativePtr = Type.pointer(Type.I32);                               // addrspace 0
 *
 * // Managed heap pointer
 * var managedPtr = Type.pointer(Type.I32, AddressSpace.MANAGED);        // addrspace 1
 *
 * // Explicit cast required
 * builder.addrSpaceCast(managedPtr, nativePtrType);
 * ```
 */
object AddressSpace {
    /** Default flat/native address space. Raw pointers, stack allocations, heap malloc. */
    const val GENERIC: Int = 0

    /** GC-managed heap. Pointers in this space may be relocated by the garbage collector. */
    const val MANAGED: Int = 1

    /** GPU global memory (CUDA/OpenCL global). */
    const val GPU_GLOBAL: Int = 2

    /** GPU shared/local memory (CUDA shared, OpenCL local). */
    const val GPU_SHARED: Int = 3

    /** GPU constant memory (read-only, cached). */
    const val GPU_CONSTANT: Int = 4

    /** Thread-local storage. */
    const val THREAD_LOCAL: Int = 5

    /** First address space available for user/target-specific use. */
    const val USER_START: Int = 256

    /**
     * Returns `true` if the given address space represents GC-managed memory.
     */
    @JvmStatic
    fun isManaged(addressSpace: Int): Boolean = addressSpace == MANAGED

    /**
     * Returns `true` if the given address space represents GPU memory.
     */
    @JvmStatic
    fun isGpu(addressSpace: Int): Boolean =
        addressSpace in GPU_GLOBAL..GPU_CONSTANT

    /**
     * Returns the address space of the given type, or [GENERIC] for non-pointer types.
     *
     * - [Type.Pointer] — returns its explicit address space
     * - [Type.Reference], [Type.WeakReference], [Type.InteriorRef], [Type.PinnedRef] — returns [MANAGED]
     * - Everything else — returns [GENERIC]
     */
    @JvmStatic
    fun of(type: Type): Int = when (type) {
        is Type.Pointer -> type.addressSpace
        is Type.Reference, is Type.WeakReference, is Type.InteriorRef -> MANAGED
        is Type.PinnedRef -> GENERIC // pinned refs are explicitly safe for native access
        else -> GENERIC
    }

    /**
     * Returns `true` if the type lives in a managed address space.
     */
    @JvmStatic
    fun isManagedType(type: Type): Boolean = of(type) == MANAGED

    /**
     * Returns `true` if the type is a pointer (typed or opaque) in a non-default address space.
     */
    @JvmStatic
    fun hasNonDefaultAddressSpace(type: Type): Boolean = when (type) {
        is Type.Pointer -> type.addressSpace != GENERIC
        else -> false
    }
}
