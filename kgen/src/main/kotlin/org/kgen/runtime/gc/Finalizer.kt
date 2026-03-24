package org.kgen.runtime.gc

/**
 * Called by the GC before reclaiming a dead object. Frees internal resources
 * (native allocations, file handles, etc.) that the object owns.
 *
 * Corresponds to `@KgenDestructor` on `@KgenNative` classes. When the GC sweeps
 * a dead object whose type has a registered finalizer, it calls [finalize] with
 * the object's heap address before adding the memory to the free list.
 *
 * ```java
 * gc.registerFinalizer(typeId, address -> {
 *     long dataPtr = heap.readField(address, 0);
 *     if (dataPtr != 0) heap.free(dataPtr);
 * });
 * ```
 */
fun interface Finalizer {
    fun finalize(objectAddress: Long)
}
