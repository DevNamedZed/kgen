package org.kgen.runtime.gc

/**
 * Provides GC roots — live object references that the collector must not reclaim.
 * Roots come from the stack, globals, and pinned handles.
 */
fun interface RootProvider {
    /** Enumerate all live references. The visitor receives each root address. */
    fun visitRoots(visitor: RootVisitor)
}
