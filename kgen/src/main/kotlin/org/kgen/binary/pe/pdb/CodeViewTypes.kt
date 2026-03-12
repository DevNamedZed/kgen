package org.kgen.binary.pe.pdb

/**
 * CodeView type record kinds (LF_xxx).
 * Used in TPI and IPI streams to describe types.
 */
enum class CvTypeKind(val code: Int) {
    // Leaf types
    LF_MODIFIER(0x1001),
    LF_POINTER(0x1002),
    LF_PROCEDURE(0x1008),
    LF_MFUNCTION(0x1009),
    LF_ARGLIST(0x1201),
    LF_FIELDLIST(0x1203),
    LF_BITFIELD(0x1205),
    LF_METHODLIST(0x1206),
    LF_BCLASS(0x1400),
    LF_VBCLASS(0x1401),
    LF_IVBCLASS(0x1402),
    LF_INDEX(0x1404),
    LF_VFUNCTAB(0x1409),
    LF_ENUMERATE(0x1502),
    LF_ARRAY(0x1503),
    LF_CLASS(0x1504),
    LF_STRUCTURE(0x1505),
    LF_UNION(0x1506),
    LF_ENUM(0x1507),
    LF_MEMBER(0x150D),
    LF_STMEMBER(0x150E),
    LF_METHOD(0x150F),
    LF_NESTTYPE(0x1510),
    LF_ONEMETHOD(0x1511),
    LF_VFTABLE(0x151D),
    LF_FUNC_ID(0x1601),
    LF_MFUNC_ID(0x1602),
    LF_BUILDINFO(0x1603),
    LF_SUBSTR_LIST(0x1604),
    LF_STRING_ID(0x1605),
    LF_UDT_SRC_LINE(0x1606),
    LF_UDT_MOD_SRC_LINE(0x1607),
    ;

    companion object {
        private val byCode = entries.associateBy { it.code }

        @JvmStatic
        fun fromCode(code: Int): CvTypeKind? = byCode[code]
    }
}

/**
 * CodeView built-in type indices.
 * Types below 0x1000 are built-in; types >= 0x1000 are in the TPI stream.
 */
enum class CvBuiltinType(val code: Int) {
    T_NOTYPE(0x0000),
    T_VOID(0x0003),
    T_HRESULT(0x0008),
    T_CHAR(0x0010),
    T_UCHAR(0x0020),
    T_RCHAR(0x0070),
    T_WCHAR(0x0071),
    T_CHAR16(0x007A),
    T_CHAR32(0x007B),
    T_CHAR8(0x007C),
    T_SHORT(0x0011),
    T_USHORT(0x0021),
    T_INT4(0x0074),
    T_UINT4(0x0075),
    T_LONG(0x0012),
    T_ULONG(0x0022),
    T_QUAD(0x0013),
    T_UQUAD(0x0023),
    T_INT8(0x0076),
    T_UINT8(0x0077),
    T_REAL32(0x0040),
    T_REAL64(0x0041),
    T_REAL80(0x0042),
    T_BOOL08(0x0030),
    T_BOOL16(0x0031),
    T_BOOL32(0x0032),

    // 64-bit pointer forms (mode = 0x06)
    T_64PVOID(0x0603),
    T_64PCHAR(0x0610),
    T_64PUCHAR(0x0620),
    T_64PINT4(0x0674),
    T_64PUINT4(0x0675),
    ;

    companion object {
        private val byCode = entries.associateBy { it.code }

        @JvmStatic
        fun fromCode(code: Int): CvBuiltinType? = byCode[code]
    }
}

/**
 * Pointer attributes for LF_POINTER records.
 */
enum class CvPointerKind(val code: Int) {
    PTR_NEAR32(0x0A),
    PTR_64(0x0C),
    PTR_UNUSEDPTR(0x00),
    ;

    companion object {
        @JvmStatic
        fun fromCode(code: Int): CvPointerKind? = entries.firstOrNull { it.code == code }
    }
}

enum class CvPointerMode(val code: Int) {
    POINTER(0x00),
    LVALUE_REFERENCE(0x01),
    POINTER_TO_MEMBER(0x02),
    RVALUE_REFERENCE(0x03),
    ;

    companion object {
        @JvmStatic
        fun fromCode(code: Int): CvPointerMode? = entries.firstOrNull { it.code == code }
    }
}

/**
 * Class/struct/union properties bitfield.
 */
object CvTypeProperties {
    const val PACKED = 0x0001
    const val HAS_CTOR = 0x0002
    const val HAS_OVERLOADED_OP = 0x0004
    const val IS_NESTED = 0x0008
    const val HAS_NESTED_TYPES = 0x0010
    const val HAS_OVERLOADED_ASSIGN = 0x0020
    const val HAS_CAST_OP = 0x0040
    const val FORWARD_REF = 0x0080
    const val SCOPED = 0x0100
    const val HAS_UNIQUE_NAME = 0x0200
    const val SEALED = 0x0400
    const val INTRINSIC = 0x2000
}

/**
 * Member access protection.
 */
enum class CvMemberAccess(val code: Int) {
    PRIVATE(1),
    PROTECTED(2),
    PUBLIC(3),
    ;

    companion object {
        @JvmStatic
        fun fromCode(code: Int): CvMemberAccess? = entries.firstOrNull { it.code == code }
    }
}
