package org.kgen.runtime.exec

/**
 * Visitor for walking stack frames.
 */
fun interface FrameVisitor {
    fun visitFrame(frame: StackFrame)
}
