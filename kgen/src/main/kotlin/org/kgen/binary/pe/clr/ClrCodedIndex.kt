package org.kgen.binary.pe.clr

/**
 * Coded index table definitions for CLR metadata.
 *
 * A coded index combines a table row index with a tag that identifies which
 * table the row belongs to. The tag occupies the low bits, and the row index
 * occupies the high bits.
 */
object ClrCodedIndex {
    val TYPE_DEF_OR_REF = intArrayOf(0x02, 0x01, 0x1B)
    val HAS_CONSTANT = intArrayOf(0x04, 0x08, 0x17)
    val HAS_CUSTOM_ATTRIBUTE = intArrayOf(
        0x06, 0x04, 0x01, 0x02, 0x08, 0x09, 0x0A, 0x00,
        0x0E, 0x17, 0x14, 0x11, 0x1A, 0x1B, 0x20, 0x23,
        0x26, 0x27, 0x28, 0x2A, 0x2C,
    )
    val HAS_FIELD_MARSHAL = intArrayOf(0x04, 0x08)
    val HAS_DECL_SECURITY = intArrayOf(0x02, 0x06, 0x20)
    val MEMBER_REF_PARENT = intArrayOf(0x02, 0x01, 0x1A, 0x06, 0x1B)
    val HAS_SEMANTICS = intArrayOf(0x14, 0x17)
    val METHOD_DEF_OR_REF = intArrayOf(0x06, 0x0A)
    val MEMBER_FORWARDED = intArrayOf(0x04, 0x06)
    val IMPLEMENTATION = intArrayOf(0x26, 0x23, 0x27)
    val CUSTOM_ATTRIBUTE_TYPE = intArrayOf(0x7F, 0x7F, 0x06, 0x0A, 0x7F)
    val RESOLUTION_SCOPE = intArrayOf(0x00, 0x1A, 0x23, 0x01)
    val TYPE_OR_METHOD_DEF = intArrayOf(0x02, 0x06)
}
