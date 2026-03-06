package org.kgen.ir

/** A reference to a basic block by label. Used in branch targets and phi nodes. */
data class BlockRef(val label: String) : Value {
    override val type: Type get() = Type.Label
    override val name: String get() = label
}
