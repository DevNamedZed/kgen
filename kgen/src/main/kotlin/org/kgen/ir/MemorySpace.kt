package org.kgen.ir

/**
 * Memory address space for GPU compute operations.
 *
 * Each address space has a numeric [id] that matches the canonical addressing scheme
 * used in compute IR (SPIR-V, PTX, AMDGPU). Address spaces guarantee non-aliasing:
 * a pointer in [SHARED] never aliases a pointer in [GLOBAL].
 *
 * Used by [org.kgen.ir.instructions.ComputeFence], compute atomic instructions,
 * and `AddrSpaceCast`.
 *
 * @param id the numeric address space identifier
 */
enum class MemorySpace(val id: Int) {
    /** May alias any address space. Default for non-compute code. */
    GENERIC(0),

    /** Device-wide DRAM. */
    GLOBAL(1),

    /** Read-only cached memory. */
    CONSTANT(2),

    /** Workgroup-local SRAM. */
    SHARED(3),

    /** Thread-private memory. */
    LOCAL(4),

    /** GC-managed heap (CPU-GPU unified memory). */
    MANAGED_HEAP(5);

    companion object {
        private val byId = entries.associateBy { it.id }

        /**
         * Returns the [MemorySpace] with the given numeric [id].
         *
         * @throws IllegalArgumentException if no address space has the given id
         */
        @JvmStatic
        fun fromId(id: Int): MemorySpace {
            return byId[id] ?: throw IllegalArgumentException("Unknown address space id: $id")
        }
    }
}
