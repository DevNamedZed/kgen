package org.kgen.binary.dwarf

/** DWARF tag constants (DW_TAG_*). */
enum class DwarfTag(val code: Int) {
    ARRAY_TYPE(0x01),
    CLASS_TYPE(0x02),
    ENTRY_POINT(0x03),
    ENUMERATION_TYPE(0x04),
    FORMAL_PARAMETER(0x05),
    LEXICAL_BLOCK(0x0B),
    MEMBER(0x0D),
    POINTER_TYPE(0x0F),
    REFERENCE_TYPE(0x10),
    COMPILE_UNIT(0x11),
    STRING_TYPE(0x12),
    STRUCTURE_TYPE(0x13),
    SUBROUTINE_TYPE(0x15),
    TYPEDEF(0x16),
    UNION_TYPE(0x17),
    UNSPECIFIED_PARAMETERS(0x18),
    VARIABLE(0x34),
    VOLATILE_TYPE(0x35),
    BASE_TYPE(0x24),
    CONST_TYPE(0x26),
    ENUMERATOR(0x28),
    SUBPROGRAM(0x2E),
    SUBRANGE_TYPE(0x21),
    NAMESPACE(0x39),
    RVALUE_REFERENCE_TYPE(0x42),
    RESTRICT_TYPE(0x37),
    TEMPLATE_TYPE_PARAMETER(0x2F),
    TEMPLATE_VALUE_PARAMETER(0x30),
    INHERITANCE(0x1C),
    ;

    companion object {
        private val byCode = entries.associateBy { it.code }

        @JvmStatic
        fun fromCode(code: Int): DwarfTag? = byCode[code]
    }
}

/** DWARF attribute constants (DW_AT_*). */
enum class DwarfAttribute(val code: Int) {
    NAME(0x03),
    BYTE_SIZE(0x0B),
    BIT_SIZE(0x0D),
    BIT_OFFSET(0x0C),
    STMT_LIST(0x10),
    LOW_PC(0x11),
    HIGH_PC(0x12),
    LANGUAGE(0x13),
    COMP_DIR(0x1B),
    CONST_VALUE(0x1C),
    CONTAINING_TYPE(0x1D),
    UPPER_BOUND(0x2F),
    ABSTRACT_ORIGIN(0x31),
    ACCESSIBILITY(0x32),
    ARTIFICIAL(0x33),
    COUNT(0x37),
    DATA_MEMBER_LOCATION(0x38),
    DECLARATION(0x3C),
    ENCODING(0x3E),
    EXTERNAL(0x3F),
    INLINE(0x20),
    LINKAGE_NAME(0x6E),
    PRODUCER(0x25),
    SPECIFICATION(0x47),
    TYPE(0x49),
    DECL_FILE(0x3A),
    DECL_LINE(0x3B),
    DECL_COLUMN(0x39),
    CALL_FILE(0x58),
    CALL_LINE(0x59),
    CALL_COLUMN(0x5A),
    RANGES(0x55),
    VIRTUALITY(0x4C),
    ;

    companion object {
        private val byCode = entries.associateBy { it.code }

        @JvmStatic
        fun fromCode(code: Int): DwarfAttribute? = byCode[code]
    }
}

/** DWARF form constants (DW_FORM_*). Determines how attribute values are encoded. */
enum class DwarfForm(val code: Int) {
    ADDR(0x01),
    BLOCK2(0x03),
    BLOCK4(0x04),
    DATA2(0x05),
    DATA4(0x06),
    DATA8(0x07),
    STRING(0x08),
    BLOCK(0x09),
    BLOCK1(0x0A),
    DATA1(0x0B),
    FLAG(0x0C),
    SDATA(0x0D),
    STRP(0x0E),
    UDATA(0x0F),
    REF_ADDR(0x10),
    REF1(0x11),
    REF2(0x12),
    REF4(0x13),
    REF8(0x14),
    REF_UDATA(0x15),
    INDIRECT(0x16),
    SEC_OFFSET(0x17),
    EXPRLOC(0x18),
    FLAG_PRESENT(0x19),
    STRX(0x1A),
    ADDRX(0x1B),
    REF_SUP4(0x1C),
    STRP_SUP(0x1D),
    DATA16(0x1E),
    LINE_STRP(0x1F),
    REF_SIG8(0x20),
    IMPLICIT_CONST(0x21),
    LOCLISTX(0x22),
    RNGLISTX(0x23),
    REF_SUP8(0x24),
    STRX1(0x25),
    STRX2(0x26),
    STRX3(0x27),
    STRX4(0x28),
    ADDRX1(0x29),
    ADDRX2(0x2A),
    ADDRX3(0x2B),
    ADDRX4(0x2C),
    ;

    companion object {
        private val byCode = entries.associateBy { it.code }

        @JvmStatic
        fun fromCode(code: Int): DwarfForm? = byCode[code]
    }
}

/** DWARF language codes (DW_LANG_*). */
enum class DwarfLanguage(val code: Int) {
    C89(0x01),
    C(0x02),
    ADA83(0x03),
    C_PLUS_PLUS(0x04),
    COBOL74(0x05),
    COBOL85(0x06),
    FORTRAN77(0x07),
    FORTRAN90(0x08),
    PASCAL83(0x09),
    MODULA2(0x0A),
    JAVA(0x0B),
    C99(0x0C),
    ADA95(0x0D),
    FORTRAN95(0x0E),
    PLI(0x0F),
    OBJC(0x10),
    OBJC_PLUS_PLUS(0x11),
    UPC(0x12),
    D(0x13),
    PYTHON(0x14),
    RUST(0x1C),
    C11(0x1D),
    SWIFT(0x1E),
    JULIA(0x1F),
    DYLAN(0x20),
    C_PLUS_PLUS_14(0x21),
    FORTRAN03(0x22),
    FORTRAN08(0x23),
    KOTLIN(0x25),
    GO(0x26),
    C_PLUS_PLUS_17(0x2A),
    C17(0x2C),
    ZIG(0x2D),
    ;

    companion object {
        private val byCode = entries.associateBy { it.code }

        @JvmStatic
        fun fromCode(code: Int): DwarfLanguage? = byCode[code]
    }
}

/** DWARF type encoding (DW_ATE_*). */
enum class DwarfTypeEncoding(val code: Int) {
    ADDRESS(0x01),
    BOOLEAN(0x02),
    COMPLEX_FLOAT(0x03),
    FLOAT(0x04),
    SIGNED(0x05),
    SIGNED_CHAR(0x06),
    UNSIGNED(0x07),
    UNSIGNED_CHAR(0x08),
    UTF(0x10),
    ;

    companion object {
        private val byCode = entries.associateBy { it.code }

        @JvmStatic
        fun fromCode(code: Int): DwarfTypeEncoding? = byCode[code]
    }
}
