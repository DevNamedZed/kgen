package org.kgen.target.x86

/**
 * x86-64 register. All registers are singleton instances discoverable via `X86Register.*`.
 *
 * Each field is typed with its sized interface (`X86Register8`, `X86Register32`, etc.)
 * so method overloading on the assembler resolves to the correct encoding.
 */
class X86Register private constructor(
    private val _name: String,
    private val _bits: Int,
    internal val encoding: Int,
    private val interfaces: Set<Class<*>>,
) : X86Register8, X86Register16, X86Register32, X86Register64,
    X86SegReg, X86ControlReg, X86DebugReg, X86FpuReg, X86MaskReg,
    X86Xmm, X86Ymm, X86Zmm, X86Mm {

    fun name(): String = _name
    fun bits(): Int = _bits
    fun encoding(): Int = encoding

    override fun toString(): String = _name

    companion object {
        // 64-bit general purpose
        @JvmField val RAX: X86Register64 = X86Register("rax", 64, 0, setOf(X86Register64::class.java))
        @JvmField val RCX: X86Register64 = X86Register("rcx", 64, 1, setOf(X86Register64::class.java))
        @JvmField val RDX: X86Register64 = X86Register("rdx", 64, 2, setOf(X86Register64::class.java))
        @JvmField val RBX: X86Register64 = X86Register("rbx", 64, 3, setOf(X86Register64::class.java))
        @JvmField val RSP: X86Register64 = X86Register("rsp", 64, 4, setOf(X86Register64::class.java))
        @JvmField val RBP: X86Register64 = X86Register("rbp", 64, 5, setOf(X86Register64::class.java))
        @JvmField val RSI: X86Register64 = X86Register("rsi", 64, 6, setOf(X86Register64::class.java))
        @JvmField val RDI: X86Register64 = X86Register("rdi", 64, 7, setOf(X86Register64::class.java))
        @JvmField val R8: X86Register64  = X86Register("r8",  64, 8, setOf(X86Register64::class.java))
        @JvmField val R9: X86Register64  = X86Register("r9",  64, 9, setOf(X86Register64::class.java))
        @JvmField val R10: X86Register64 = X86Register("r10", 64, 10, setOf(X86Register64::class.java))
        @JvmField val R11: X86Register64 = X86Register("r11", 64, 11, setOf(X86Register64::class.java))
        @JvmField val R12: X86Register64 = X86Register("r12", 64, 12, setOf(X86Register64::class.java))
        @JvmField val R13: X86Register64 = X86Register("r13", 64, 13, setOf(X86Register64::class.java))
        @JvmField val R14: X86Register64 = X86Register("r14", 64, 14, setOf(X86Register64::class.java))
        @JvmField val R15: X86Register64 = X86Register("r15", 64, 15, setOf(X86Register64::class.java))

        // 32-bit general purpose
        @JvmField val EAX: X86Register32  = X86Register("eax",  32, 0, setOf(X86Register32::class.java))
        @JvmField val ECX: X86Register32  = X86Register("ecx",  32, 1, setOf(X86Register32::class.java))
        @JvmField val EDX: X86Register32  = X86Register("edx",  32, 2, setOf(X86Register32::class.java))
        @JvmField val EBX: X86Register32  = X86Register("ebx",  32, 3, setOf(X86Register32::class.java))
        @JvmField val ESP: X86Register32  = X86Register("esp",  32, 4, setOf(X86Register32::class.java))
        @JvmField val EBP: X86Register32  = X86Register("ebp",  32, 5, setOf(X86Register32::class.java))
        @JvmField val ESI: X86Register32  = X86Register("esi",  32, 6, setOf(X86Register32::class.java))
        @JvmField val EDI: X86Register32  = X86Register("edi",  32, 7, setOf(X86Register32::class.java))
        @JvmField val R8D: X86Register32  = X86Register("r8d",  32, 8, setOf(X86Register32::class.java))
        @JvmField val R9D: X86Register32  = X86Register("r9d",  32, 9, setOf(X86Register32::class.java))
        @JvmField val R10D: X86Register32 = X86Register("r10d", 32, 10, setOf(X86Register32::class.java))
        @JvmField val R11D: X86Register32 = X86Register("r11d", 32, 11, setOf(X86Register32::class.java))
        @JvmField val R12D: X86Register32 = X86Register("r12d", 32, 12, setOf(X86Register32::class.java))
        @JvmField val R13D: X86Register32 = X86Register("r13d", 32, 13, setOf(X86Register32::class.java))
        @JvmField val R14D: X86Register32 = X86Register("r14d", 32, 14, setOf(X86Register32::class.java))
        @JvmField val R15D: X86Register32 = X86Register("r15d", 32, 15, setOf(X86Register32::class.java))

        // 16-bit general purpose
        @JvmField val AX: X86Register16  = X86Register("ax",  16, 0, setOf(X86Register16::class.java))
        @JvmField val CX: X86Register16  = X86Register("cx",  16, 1, setOf(X86Register16::class.java))
        @JvmField val DX: X86Register16  = X86Register("dx",  16, 2, setOf(X86Register16::class.java))
        @JvmField val BX: X86Register16  = X86Register("bx",  16, 3, setOf(X86Register16::class.java))
        @JvmField val SP: X86Register16  = X86Register("sp",  16, 4, setOf(X86Register16::class.java))
        @JvmField val BP: X86Register16  = X86Register("bp",  16, 5, setOf(X86Register16::class.java))
        @JvmField val SI: X86Register16  = X86Register("si",  16, 6, setOf(X86Register16::class.java))
        @JvmField val DI: X86Register16  = X86Register("di",  16, 7, setOf(X86Register16::class.java))
        @JvmField val R8W: X86Register16  = X86Register("r8w",  16, 8, setOf(X86Register16::class.java))
        @JvmField val R9W: X86Register16  = X86Register("r9w",  16, 9, setOf(X86Register16::class.java))
        @JvmField val R10W: X86Register16 = X86Register("r10w", 16, 10, setOf(X86Register16::class.java))
        @JvmField val R11W: X86Register16 = X86Register("r11w", 16, 11, setOf(X86Register16::class.java))
        @JvmField val R12W: X86Register16 = X86Register("r12w", 16, 12, setOf(X86Register16::class.java))
        @JvmField val R13W: X86Register16 = X86Register("r13w", 16, 13, setOf(X86Register16::class.java))
        @JvmField val R14W: X86Register16 = X86Register("r14w", 16, 14, setOf(X86Register16::class.java))
        @JvmField val R15W: X86Register16 = X86Register("r15w", 16, 15, setOf(X86Register16::class.java))

        // 8-bit general purpose
        @JvmField val AL: X86Register8  = X86Register("al",  8, 0, setOf(X86Register8::class.java))
        @JvmField val CL: X86Register8  = X86Register("cl",  8, 1, setOf(X86Register8::class.java))
        @JvmField val DL: X86Register8  = X86Register("dl",  8, 2, setOf(X86Register8::class.java))
        @JvmField val BL: X86Register8  = X86Register("bl",  8, 3, setOf(X86Register8::class.java))
        @JvmField val SPL: X86Register8 = X86Register("spl", 8, 4, setOf(X86Register8::class.java))
        @JvmField val BPL: X86Register8 = X86Register("bpl", 8, 5, setOf(X86Register8::class.java))
        @JvmField val SIL: X86Register8 = X86Register("sil", 8, 6, setOf(X86Register8::class.java))
        @JvmField val DIL: X86Register8 = X86Register("dil", 8, 7, setOf(X86Register8::class.java))
        @JvmField val R8B: X86Register8  = X86Register("r8b",  8, 8, setOf(X86Register8::class.java))
        @JvmField val R9B: X86Register8  = X86Register("r9b",  8, 9, setOf(X86Register8::class.java))
        @JvmField val R10B: X86Register8 = X86Register("r10b", 8, 10, setOf(X86Register8::class.java))
        @JvmField val R11B: X86Register8 = X86Register("r11b", 8, 11, setOf(X86Register8::class.java))
        @JvmField val R12B: X86Register8 = X86Register("r12b", 8, 12, setOf(X86Register8::class.java))
        @JvmField val R13B: X86Register8 = X86Register("r13b", 8, 13, setOf(X86Register8::class.java))
        @JvmField val R14B: X86Register8 = X86Register("r14b", 8, 14, setOf(X86Register8::class.java))
        @JvmField val R15B: X86Register8 = X86Register("r15b", 8, 15, setOf(X86Register8::class.java))
        @JvmField val AH: X86Register8  = X86Register("ah",  8, 4, setOf(X86Register8::class.java)) // legacy high byte
        @JvmField val CH: X86Register8  = X86Register("ch",  8, 5, setOf(X86Register8::class.java))
        @JvmField val DH: X86Register8  = X86Register("dh",  8, 6, setOf(X86Register8::class.java))
        @JvmField val BH: X86Register8  = X86Register("bh",  8, 7, setOf(X86Register8::class.java))

        // SSE/AVX XMM registers
        @JvmField val XMM0: X86Xmm  = X86Register("xmm0",  128, 0, setOf(X86Xmm::class.java))
        @JvmField val XMM1: X86Xmm  = X86Register("xmm1",  128, 1, setOf(X86Xmm::class.java))
        @JvmField val XMM2: X86Xmm  = X86Register("xmm2",  128, 2, setOf(X86Xmm::class.java))
        @JvmField val XMM3: X86Xmm  = X86Register("xmm3",  128, 3, setOf(X86Xmm::class.java))
        @JvmField val XMM4: X86Xmm  = X86Register("xmm4",  128, 4, setOf(X86Xmm::class.java))
        @JvmField val XMM5: X86Xmm  = X86Register("xmm5",  128, 5, setOf(X86Xmm::class.java))
        @JvmField val XMM6: X86Xmm  = X86Register("xmm6",  128, 6, setOf(X86Xmm::class.java))
        @JvmField val XMM7: X86Xmm  = X86Register("xmm7",  128, 7, setOf(X86Xmm::class.java))
        @JvmField val XMM8: X86Xmm  = X86Register("xmm8",  128, 8, setOf(X86Xmm::class.java))
        @JvmField val XMM9: X86Xmm  = X86Register("xmm9",  128, 9, setOf(X86Xmm::class.java))
        @JvmField val XMM10: X86Xmm = X86Register("xmm10", 128, 10, setOf(X86Xmm::class.java))
        @JvmField val XMM11: X86Xmm = X86Register("xmm11", 128, 11, setOf(X86Xmm::class.java))
        @JvmField val XMM12: X86Xmm = X86Register("xmm12", 128, 12, setOf(X86Xmm::class.java))
        @JvmField val XMM13: X86Xmm = X86Register("xmm13", 128, 13, setOf(X86Xmm::class.java))
        @JvmField val XMM14: X86Xmm = X86Register("xmm14", 128, 14, setOf(X86Xmm::class.java))
        @JvmField val XMM15: X86Xmm = X86Register("xmm15", 128, 15, setOf(X86Xmm::class.java))

        // AVX YMM registers
        @JvmField val YMM0: X86Ymm  = X86Register("ymm0",  256, 0, setOf(X86Ymm::class.java))
        @JvmField val YMM1: X86Ymm  = X86Register("ymm1",  256, 1, setOf(X86Ymm::class.java))
        @JvmField val YMM2: X86Ymm  = X86Register("ymm2",  256, 2, setOf(X86Ymm::class.java))
        @JvmField val YMM3: X86Ymm  = X86Register("ymm3",  256, 3, setOf(X86Ymm::class.java))
        @JvmField val YMM4: X86Ymm  = X86Register("ymm4",  256, 4, setOf(X86Ymm::class.java))
        @JvmField val YMM5: X86Ymm  = X86Register("ymm5",  256, 5, setOf(X86Ymm::class.java))
        @JvmField val YMM6: X86Ymm  = X86Register("ymm6",  256, 6, setOf(X86Ymm::class.java))
        @JvmField val YMM7: X86Ymm  = X86Register("ymm7",  256, 7, setOf(X86Ymm::class.java))
        @JvmField val YMM8: X86Ymm  = X86Register("ymm8",  256, 8, setOf(X86Ymm::class.java))
        @JvmField val YMM9: X86Ymm  = X86Register("ymm9",  256, 9, setOf(X86Ymm::class.java))
        @JvmField val YMM10: X86Ymm = X86Register("ymm10", 256, 10, setOf(X86Ymm::class.java))
        @JvmField val YMM11: X86Ymm = X86Register("ymm11", 256, 11, setOf(X86Ymm::class.java))
        @JvmField val YMM12: X86Ymm = X86Register("ymm12", 256, 12, setOf(X86Ymm::class.java))
        @JvmField val YMM13: X86Ymm = X86Register("ymm13", 256, 13, setOf(X86Ymm::class.java))
        @JvmField val YMM14: X86Ymm = X86Register("ymm14", 256, 14, setOf(X86Ymm::class.java))
        @JvmField val YMM15: X86Ymm = X86Register("ymm15", 256, 15, setOf(X86Ymm::class.java))

        // AVX-512 ZMM registers (0-15 only for now)
        @JvmField val ZMM0: X86Zmm  = X86Register("zmm0",  512, 0, setOf(X86Zmm::class.java))
        @JvmField val ZMM1: X86Zmm  = X86Register("zmm1",  512, 1, setOf(X86Zmm::class.java))
        @JvmField val ZMM2: X86Zmm  = X86Register("zmm2",  512, 2, setOf(X86Zmm::class.java))
        @JvmField val ZMM3: X86Zmm  = X86Register("zmm3",  512, 3, setOf(X86Zmm::class.java))
        @JvmField val ZMM4: X86Zmm  = X86Register("zmm4",  512, 4, setOf(X86Zmm::class.java))
        @JvmField val ZMM5: X86Zmm  = X86Register("zmm5",  512, 5, setOf(X86Zmm::class.java))
        @JvmField val ZMM6: X86Zmm  = X86Register("zmm6",  512, 6, setOf(X86Zmm::class.java))
        @JvmField val ZMM7: X86Zmm  = X86Register("zmm7",  512, 7, setOf(X86Zmm::class.java))

        // Mask registers (AVX-512)
        @JvmField val K0: X86MaskReg = X86Register("k0", 64, 0, setOf(X86MaskReg::class.java))
        @JvmField val K1: X86MaskReg = X86Register("k1", 64, 1, setOf(X86MaskReg::class.java))
        @JvmField val K2: X86MaskReg = X86Register("k2", 64, 2, setOf(X86MaskReg::class.java))
        @JvmField val K3: X86MaskReg = X86Register("k3", 64, 3, setOf(X86MaskReg::class.java))
        @JvmField val K4: X86MaskReg = X86Register("k4", 64, 4, setOf(X86MaskReg::class.java))
        @JvmField val K5: X86MaskReg = X86Register("k5", 64, 5, setOf(X86MaskReg::class.java))
        @JvmField val K6: X86MaskReg = X86Register("k6", 64, 6, setOf(X86MaskReg::class.java))
        @JvmField val K7: X86MaskReg = X86Register("k7", 64, 7, setOf(X86MaskReg::class.java))

        // Segment registers
        @JvmField val CS: X86SegReg = X86Register("cs", 16, 1, setOf(X86SegReg::class.java))
        @JvmField val DS: X86SegReg = X86Register("ds", 16, 3, setOf(X86SegReg::class.java))
        @JvmField val ES: X86SegReg = X86Register("es", 16, 0, setOf(X86SegReg::class.java))
        @JvmField val FS: X86SegReg = X86Register("fs", 16, 4, setOf(X86SegReg::class.java))
        @JvmField val GS: X86SegReg = X86Register("gs", 16, 5, setOf(X86SegReg::class.java))
        @JvmField val SS: X86SegReg = X86Register("ss", 16, 2, setOf(X86SegReg::class.java))

        @JvmStatic fun allGPR64(): List<X86Register64> = listOf(RAX, RCX, RDX, RBX, RSP, RBP, RSI, RDI, R8, R9, R10, R11, R12, R13, R14, R15)
        @JvmStatic fun allGPR32(): List<X86Register32> = listOf(EAX, ECX, EDX, EBX, ESP, EBP, ESI, EDI, R8D, R9D, R10D, R11D, R12D, R13D, R14D, R15D)
        @JvmStatic fun allXMM(): List<X86Xmm> = listOf(XMM0, XMM1, XMM2, XMM3, XMM4, XMM5, XMM6, XMM7, XMM8, XMM9, XMM10, XMM11, XMM12, XMM13, XMM14, XMM15)
        @JvmStatic fun allYMM(): List<X86Ymm> = listOf(YMM0, YMM1, YMM2, YMM3, YMM4, YMM5, YMM6, YMM7, YMM8, YMM9, YMM10, YMM11, YMM12, YMM13, YMM14, YMM15)
    }
}
