package org.kgen.ir

/** A global variable definition. */
data class Global(
    val name: String,
    val type: Type,
    val initializer: Constant? = null,
    val isConstant: Boolean = false,
    val linkage: Linkage = Linkage.EXTERNAL,
    val visibility: Visibility = Visibility.DEFAULT,
    val threadLocal: ThreadLocalMode? = null,
    val section: String? = null,
    val align: Int? = null,
    val addressSpace: Int = 0,
)
