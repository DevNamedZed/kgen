package org.kgen.binary.pe.pdb

/**
 * CodeView symbol record kinds (S_xxx).
 * Used in the symbol streams and module info substreams.
 */
enum class CvSymbolKind(val code: Int) {
    S_END(0x0006),
    S_FRAMEPROC(0x1012),
    S_OBJNAME(0x1101),
    S_THUNK32(0x1102),
    S_BLOCK32(0x1103),
    S_LABEL32(0x1105),
    S_REGISTER(0x1106),
    S_CONSTANT(0x1107),
    S_UDT(0x1108),
    S_BPREL32(0x110B),
    S_LDATA32(0x110C),
    S_GDATA32(0x110D),
    S_PUB32(0x110E),
    S_LPROC32(0x110F),
    S_GPROC32(0x1110),
    S_REGREL32(0x1111),
    S_LTHREAD32(0x1112),
    S_GTHREAD32(0x1113),
    S_PROCREF(0x1125),
    S_LPROCREF(0x1126),
    S_ENVBLOCK(0x113D),
    S_LOCAL(0x113E),
    S_DEFRANGE_REGISTER(0x1141),
    S_DEFRANGE_FRAMEPOINTER_REL(0x1142),
    S_DEFRANGE_SUBFIELD_REGISTER(0x1143),
    S_DEFRANGE_FRAMEPOINTER_REL_FULL_SCOPE(0x1144),
    S_DEFRANGE_REGISTER_REL(0x1145),
    S_BUILDINFO(0x114C),
    S_INLINESITE(0x114D),
    S_INLINESITE_END(0x114E),
    S_FILESTATIC(0x1153),
    S_CALLSITEINFO(0x1154),
    S_HEAPALLOCSITE(0x115E),
    S_COMPILE3(0x113C),
    S_UNAMESPACE(0x1124),
    S_TRAMPOLINE(0x112C),
    S_SECTION(0x1136),
    S_COFFGROUP(0x1137),
    S_EXPORT(0x1138),
    S_LPROC32_ID(0x1146),
    S_GPROC32_ID(0x1147),
    S_PROC_ID_END(0x114F),
    ;

    companion object {
        private val byCode = entries.associateBy { it.code }

        @JvmStatic
        fun fromCode(code: Int): CvSymbolKind? = byCode[code]
    }
}

/**
 * Public symbol flags (S_PUB32).
 */
object CvPublicSymbolFlags {
    const val CODE = 0x00000001
    const val FUNCTION = 0x00000002
    const val MANAGED = 0x00000004
    const val MSIL = 0x00000008
}

/**
 * Procedure flags for S_GPROC32/S_LPROC32.
 */
object CvProcFlags {
    const val FRAME_POINTER_PRESENT = 0x01
    const val HAS_ALLOCA = 0x02
    const val HAS_SETJMP = 0x04
    const val HAS_LONGJMP = 0x08
    const val HAS_INLINE_ASM = 0x10
    const val HAS_EH = 0x20
    const val INLINE_SPEC = 0x40
    const val HAS_SEH = 0x80
}

/**
 * Compile3 flags — machine type.
 */
enum class CvCpuType(val code: Int) {
    INTEL_8080(0x00),
    INTEL_8086(0x01),
    INTEL_80286(0x02),
    INTEL_80386(0x03),
    INTEL_80486(0x04),
    INTEL_PENTIUM(0x05),
    INTEL_PENTIUM_PRO(0x06),
    INTEL_PENTIUM3(0x07),
    X64_AMD64(0xD0),
    ARM(0x50),
    ARM_THUMB(0x51),
    ARM_NT(0x52),
    ARM64(0xF0),
    ;

    companion object {
        private val byCode = entries.associateBy { it.code }

        @JvmStatic
        fun fromCode(code: Int): CvCpuType? = byCode[code]
    }
}

/**
 * Compile3 source language.
 */
enum class CvSourceLanguage(val code: Int) {
    C(0x00),
    CPP(0x01),
    FORTRAN(0x02),
    MASM(0x03),
    PASCAL(0x04),
    BASIC(0x05),
    COBOL(0x06),
    LINK(0x07),
    CVTRES(0x08),
    CVTPGD(0x09),
    CSHARP(0x0A),
    VB(0x0B),
    ILASM(0x0C),
    JAVA(0x0D),
    JSCRIPT(0x0E),
    MSIL(0x0F),
    HLSL(0x10),
    D(0x11),
    SWIFT(0x12),
    RUST(0x13),
    KOTLIN(0x14),
    ;

    companion object {
        private val byCode = entries.associateBy { it.code }

        @JvmStatic
        fun fromCode(code: Int): CvSourceLanguage? = byCode[code]
    }
}
