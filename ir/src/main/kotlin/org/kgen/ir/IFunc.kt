package org.kgen.ir

/** GNU indirect function. [resolverFunction] is called at load time to pick an implementation. */
data class IFunc(
    val name: String,
    val resolverFunction: String,
    val type: Type.Function,
    val linkage: Linkage = Linkage.EXTERNAL,
    val visibility: Visibility = Visibility.DEFAULT,
)
