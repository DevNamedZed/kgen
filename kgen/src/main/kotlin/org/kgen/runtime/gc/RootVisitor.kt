package org.kgen.runtime.gc

/**
 * Callback for visiting GC root addresses.
 */
fun interface RootVisitor {
    fun visitRoot(address: Long)
}
