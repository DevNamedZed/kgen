package org.kgen.ir

/**
 * Action to take after deoptimization.
 */
enum class DeoptAction {
    NONE,
    INVALIDATE_REPROFILE,
    INVALIDATE_RECOMPILE,
    INVALIDATE_STOP_COMPILING,
}
