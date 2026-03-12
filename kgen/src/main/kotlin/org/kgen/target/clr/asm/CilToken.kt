package org.kgen.target.clr.asm

/**
 * A strongly-typed CLR metadata token.
 *
 * Tokens identify metadata entities (methods, fields, types, etc.) and are
 * encoded as a 4-byte value: upper byte = table ID, lower 3 bytes = row index.
 *
 * ```java
 * // From CilClassBuilder.addMemberRef()
 * CilToken ctorToken = builder.addMemberRef(1, ".ctor", sig);
 *
 * // Use in CIL instructions
 * asm.call(ctorToken);
 * asm.newobj(ctorToken);
 * ```
 */
class CilToken internal constructor(
    /** The raw 32-bit metadata token value. */
    val value: Int,
) {
    /** The metadata table ID (upper byte). */
    val tableId: Int get() = (value shr 24) and 0xFF

    /** The 1-based row index in the table. */
    val rowIndex: Int get() = value and 0x00FFFFFF

    /** Token for the Module table (0x00). */
    val isModule: Boolean get() = tableId == 0x00

    /** Token for the TypeRef table (0x01). */
    val isTypeRef: Boolean get() = tableId == 0x01

    /** Token for the TypeDef table (0x02). */
    val isTypeDef: Boolean get() = tableId == 0x02

    /** Token for the Field table (0x04). */
    val isField: Boolean get() = tableId == 0x04

    /** Token for the MethodDef table (0x06). */
    val isMethodDef: Boolean get() = tableId == 0x06

    /** Token for the MemberRef table (0x0A). */
    val isMemberRef: Boolean get() = tableId == 0x0A

    /** Token for a user string (0x70). */
    val isUserString: Boolean get() = tableId == 0x70

    override fun toString(): String = "Token(0x${value.toString(16).padStart(8, '0')})"
    override fun equals(other: Any?): Boolean = other is CilToken && value == other.value
    override fun hashCode(): Int = value

    companion object {
        /** Create a MethodDef token (table 0x06). */
        @JvmStatic fun methodDef(index: Int): CilToken = CilToken(0x06000000 or index)

        /** Create a MemberRef token (table 0x0A). */
        @JvmStatic fun memberRef(index: Int): CilToken = CilToken(0x0A000000 or index)

        /** Create a TypeRef token (table 0x01). */
        @JvmStatic fun typeRef(index: Int): CilToken = CilToken(0x01000000 or index)

        /** Create a TypeDef token (table 0x02). */
        @JvmStatic fun typeDef(index: Int): CilToken = CilToken(0x02000000 or index)

        /** Create a Field token (table 0x04). */
        @JvmStatic fun field(index: Int): CilToken = CilToken(0x04000000 or index)

        /** Create a user string token (table 0x70). */
        @JvmStatic fun userString(index: Int): CilToken = CilToken(0x70000000 or index)

        /** Create a token from a raw value. */
        @JvmStatic fun fromRaw(value: Int): CilToken = CilToken(value)
    }
}
