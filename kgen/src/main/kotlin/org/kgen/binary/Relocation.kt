package org.kgen.binary

data class Relocation(
    val offset: Long,                  // offset within section
    val symbol: String,                // symbol being referenced
    val type: RelocationType,
    val addend: Long = 0,              // addend for RELA-style
    val section: String? = null,       // section this relocation applies to
)

// Comprehensive relocation types per architecture

sealed interface RelocationType {
    val relocName: String
    val value: Int

    // x86-64 (ELF: R_X86_64_*)
    enum class X86_64(override val value: Int) : RelocationType {
        NONE(0),
        R_64(1),               // S + A — absolute 64-bit
        PC32(2),               // S + A - P — PC-relative 32-bit
        GOT32(3),              // G + A
        PLT32(4),              // L + A - P — PLT entry
        COPY(5),               // copy relocation
        GLOB_DAT(6),           // S — create GOT entry
        JUMP_SLOT(7),          // S — create PLT entry
        RELATIVE(8),           // B + A — base-relative
        GOTPCREL(9),           // G + GOT + A - P — GOT entry PC-relative
        R_32(10),              // S + A — absolute 32-bit
        R_32S(11),             // S + A — signed 32-bit
        R_16(12),              // S + A — 16-bit
        PC16(13),              // S + A - P — PC-relative 16-bit
        R_8(14),               // S + A — 8-bit
        PC8(15),               // S + A - P — PC-relative 8-bit
        DTPMOD64(16),          // TLS module id
        DTPOFF64(17),          // TLS offset
        TPOFF64(18),           // TLS offset from TP
        TLSGD(19),             // TLS GD GOT entry
        TLSLD(20),             // TLS LD GOT entry
        DTPOFF32(21),
        GOTTPOFF(22),          // TLS IE GOT entry
        TPOFF32(23),
        PC64(24),              // S + A - P — PC-relative 64-bit
        GOTOFF64(25),          // S + A - GOT
        GOTPC32(26),           // GOT + A - P
        SIZE32(32),            // Z + A — symbol size
        SIZE64(33),
        GOTPC32_TLSDESC(34),
        TLSDESC_CALL(35),
        TLSDESC(36),
        IRELATIVE(37),
        GOTPCRELX(41),         // relaxable GOT
        REX_GOTPCRELX(42),     // relaxable GOT with REX
        ;
        override val relocName: String get() = "R_X86_64_$this"
    }

    // AArch64 (ELF: R_AARCH64_*)
    enum class AArch64(override val value: Int) : RelocationType {
        NONE(0),
        ABS64(257),            // S + A — absolute 64-bit
        ABS32(258),            // S + A — absolute 32-bit
        ABS16(259),
        PREL64(260),           // S + A - P
        PREL32(261),
        PREL16(262),
        MOVW_UABS_G0(263),    // movz/movk immediate
        MOVW_UABS_G0_NC(264),
        MOVW_UABS_G1(265),
        MOVW_UABS_G1_NC(266),
        MOVW_UABS_G2(267),
        MOVW_UABS_G2_NC(268),
        MOVW_UABS_G3(269),
        MOVW_SABS_G0(270),
        MOVW_SABS_G1(271),
        MOVW_SABS_G2(272),
        ADR_PREL_LO21(274),   // ADR immediate
        ADR_PREL_PG_HI21(275), // ADRP immediate
        ADR_PREL_PG_HI21_NC(276),
        ADD_ABS_LO12_NC(277), // ADD immediate
        LDST8_ABS_LO12_NC(278),
        LDST16_ABS_LO12_NC(284),
        LDST32_ABS_LO12_NC(285),
        LDST64_ABS_LO12_NC(286),
        LDST128_ABS_LO12_NC(299),
        TSTBR14(279),         // TBZ/TBNZ immediate
        CONDBR19(280),        // B.cond immediate
        JUMP26(282),          // B immediate
        CALL26(283),          // BL immediate
        GOT_LD_PREL19(309),
        ADR_GOT_PAGE(311),    // ADRP to GOT entry
        LD64_GOT_LO12_NC(312), // LD to GOT entry
        LD64_GOTPAGE_LO15(313),
        TLSGD_ADR_PREL21(512),
        TLSGD_ADR_PAGE21(513),
        TLSGD_ADD_LO12_NC(514),
        TLSLD_ADR_PREL21(517),
        TLSLD_ADR_PAGE21(518),
        TLSIE_MOVW_GOTTPREL_G1(539),
        TLSIE_MOVW_GOTTPREL_G0_NC(540),
        TLSIE_ADR_GOTTPREL_PAGE21(541),
        TLSIE_LD64_GOTTPREL_LO12_NC(542),
        TLSLE_ADD_TPREL_HI12(549),
        TLSLE_ADD_TPREL_LO12(550),
        TLSLE_ADD_TPREL_LO12_NC(551),
        TLSLE_MOVW_TPREL_G2(552),
        TLSLE_MOVW_TPREL_G1(553),
        TLSLE_MOVW_TPREL_G0(556),
        COPY(1024),
        GLOB_DAT(1025),
        JUMP_SLOT(1026),
        RELATIVE(1027),
        TLS_DTPREL64(1028),
        TLS_DTPMOD64(1029),
        TLS_TPREL64(1030),
        TLSDESC(1031),
        IRELATIVE(1032),
        ;
        override val relocName: String get() = "R_AARCH64_$this"
    }

    // x86-64 PE/COFF
    enum class COFF_X86_64(override val value: Int) : RelocationType {
        ABSOLUTE(0),
        ADDR64(1),
        ADDR32(2),
        ADDR32NB(3),        // RVA
        REL32(4),
        REL32_1(5),
        REL32_2(6),
        REL32_3(7),
        REL32_4(8),
        REL32_5(9),
        SECTION(10),
        SECREL(11),
        SECREL7(12),
        TOKEN(13),
        SREL32(14),
        PAIR(15),
        SSPAN32(16),
        ;
        override val relocName: String get() = "IMAGE_REL_AMD64_$this"
    }

    // AArch64 PE/COFF
    enum class COFF_ARM64(override val value: Int) : RelocationType {
        ABSOLUTE(0),
        ADDR32(1),
        ADDR32NB(2),
        BRANCH26(3),
        PAGEBASE_REL21(4),
        REL21(5),
        PAGEOFFSET_12A(6),
        PAGEOFFSET_12L(7),
        SECREL(8),
        SECREL_LOW12A(9),
        SECREL_HIGH12A(10),
        SECREL_LOW12L(11),
        TOKEN(12),
        SECTION(13),
        ADDR64(14),
        BRANCH19(15),
        BRANCH14(16),
        REL32(17),
        ;
        override val relocName: String get() = "IMAGE_REL_ARM64_$this"
    }

    // Mach-O x86-64
    enum class MachO_X86_64(override val value: Int) : RelocationType {
        UNSIGNED(0),
        SIGNED(1),
        BRANCH(2),
        GOT_LOAD(3),
        GOT(4),
        SUBTRACTOR(5),
        SIGNED_1(6),
        SIGNED_2(7),
        SIGNED_4(8),
        TLV(9),
        ;
        override val relocName: String get() = "X86_64_RELOC_$this"
    }

    // Mach-O AArch64
    enum class MachO_ARM64(override val value: Int) : RelocationType {
        UNSIGNED(0),
        SUBTRACTOR(1),
        BRANCH26(2),
        PAGE21(3),
        PAGEOFF12(4),
        GOT_LOAD_PAGE21(5),
        GOT_LOAD_PAGEOFF12(6),
        POINTER_TO_GOT(7),
        TLVP_LOAD_PAGE21(8),
        TLVP_LOAD_PAGEOFF12(9),
        ADDEND(10),
        AUTHENTICATED_POINTER(11),
        ;
        override val relocName: String get() = "ARM64_RELOC_$this"
    }

    // RISC-V (ELF: R_RISCV_*)
    enum class RiscV(override val value: Int) : RelocationType {
        NONE(0),
        R_32(1),               // S + A — absolute 32-bit
        R_64(2),               // S + A — absolute 64-bit
        RELATIVE(3),           // B + A — base-relative
        COPY(4),
        JUMP_SLOT(5),          // S — PLT entry
        TLS_DTPMOD32(6),
        TLS_DTPMOD64(7),
        TLS_DTPREL32(8),
        TLS_DTPREL64(9),
        TLS_TPREL32(10),
        TLS_TPREL64(11),
        BRANCH(16),            // S + A - P — B-type branch
        JAL(17),               // S + A - P — J-type jump
        CALL(18),              // S + A - P — auipc+jalr pair
        CALL_PLT(19),          // S + A - P — PLT call pair
        GOT_HI20(20),          // GOT entry hi20
        TLS_GOT_HI20(21),
        TLS_GD_HI20(22),
        PCREL_HI20(23),        // S + A - P [31:12] — auipc
        PCREL_LO12_I(24),      // S - P [11:0] — I-type low 12
        PCREL_LO12_S(25),      // S - P [11:0] — S-type low 12
        HI20(26),              // S + A [31:12] — lui
        LO12_I(27),            // S + A [11:0] — I-type
        LO12_S(28),            // S + A [11:0] — S-type
        TPREL_HI20(29),
        TPREL_LO12_I(30),
        TPREL_LO12_S(31),
        TPREL_ADD(32),
        ADD8(33),
        ADD16(34),
        ADD32(35),
        ADD64(36),
        SUB8(37),
        SUB16(38),
        SUB32(39),
        SUB64(40),
        ALIGN(43),
        RVC_BRANCH(44),        // compressed branch
        RVC_JUMP(45),          // compressed jump
        RELAX(51),             // linker relaxation marker
        SET6(53),
        SET8(54),
        SET16(55),
        SET32(56),
        R_32_PCREL(57),        // S + A - P — 32-bit PC-relative
        ;
        override val relocName: String get() = "R_RISCV_$this"
    }

    // WASM
    enum class Wasm(override val value: Int) : RelocationType {
        FUNCTION_INDEX_LEB(0),
        TABLE_INDEX_SLEB(1),
        TABLE_INDEX_I32(2),
        MEMORY_ADDR_LEB(3),
        MEMORY_ADDR_SLEB(4),
        MEMORY_ADDR_I32(5),
        TYPE_INDEX_LEB(6),
        GLOBAL_INDEX_LEB(7),
        FUNCTION_OFFSET_I32(8),
        SECTION_OFFSET_I32(9),
        TAG_INDEX_LEB(10),
        MEMORY_ADDR_REL_SLEB(11),
        TABLE_INDEX_REL_SLEB(12),
        GLOBAL_INDEX_I32(13),
        MEMORY_ADDR_LEB64(14),
        MEMORY_ADDR_SLEB64(15),
        MEMORY_ADDR_I64(16),
        MEMORY_ADDR_REL_SLEB64(17),
        TABLE_INDEX_SLEB64(18),
        TABLE_INDEX_I64(19),
        TABLE_NUMBER_LEB(20),
        MEMORY_ADDR_TLS_SLEB(21),
        MEMORY_ADDR_TLS_I32(22),
        ;
        override val relocName: String get() = "R_WASM_$this"
    }

    // Generic / unknown
    data class Generic(override val relocName: String, override val value: Int) : RelocationType
}
