package org.kgen.ir

/** Thread-local storage model. */
enum class ThreadLocalMode {
    GENERAL_DYNAMIC, LOCAL_DYNAMIC, INITIAL_EXEC, LOCAL_EXEC,
}
