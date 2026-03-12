package org.kgen.target.arm64

/** Common base for all ARM64 register types. Provides encoding, name, and size. */
sealed interface Arm64Reg {
    fun encoding(): Int
    fun name(): String
    fun bits(): Int
}

sealed interface Arm64Operand32
sealed interface Arm64Operand64

sealed interface Arm64Register32 : Arm64Operand32, Arm64Reg
sealed interface Arm64Register64 : Arm64Operand64, Arm64Reg

sealed interface Arm64VecS : Arm64Reg
sealed interface Arm64VecD : Arm64Reg
sealed interface Arm64VecQ : Arm64Reg

/**
 * NEON vector arrangement specifier — defines element size and lane count.
 *
 * Used with SIMD instructions to specify how 64-bit (D) or 128-bit (Q)
 * vectors are partitioned into lanes:
 *
 * ```java
 * asm.addVec(VectorArrangement.S4, V0, V1, V2); // ADD V0.4S, V1.4S, V2.4S
 * ```
 */
enum class VectorArrangement(
    /** The Q bit: 0 for 64-bit vectors, 1 for 128-bit vectors. */
    val q: Int,
    /** The size field (bits 23:22). */
    val size: Int,
    /** Number of lanes. */
    val lanes: Int,
    /** Element size in bits. */
    val elementBits: Int,
) {
    /** 8 bytes in a 64-bit register. */
    B8(0, 0, 8, 8),
    /** 16 bytes in a 128-bit register. */
    B16(1, 0, 16, 8),
    /** 4 half-words (16-bit) in a 64-bit register. */
    H4(0, 1, 4, 16),
    /** 8 half-words (16-bit) in a 128-bit register. */
    H8(1, 1, 8, 16),
    /** 2 single-words (32-bit) in a 64-bit register. */
    S2(0, 2, 2, 32),
    /** 4 single-words (32-bit) in a 128-bit register. */
    S4(1, 2, 4, 32),
    /** 2 double-words (64-bit) in a 128-bit register. */
    D2(1, 3, 2, 64);

    /** Total vector width in bits (64 or 128). */
    val totalBits: Int get() = if (q == 1) 128 else 64

    /** Suffix string for disassembly (e.g., "4s", "16b", "2d"). */
    val suffix: String get() = when (this) {
        B8 -> "8b"; B16 -> "16b"
        H4 -> "4h"; H8 -> "8h"
        S2 -> "2s"; S4 -> "4s"
        D2 -> "2d"
    }

    companion object {
        /** Look up arrangement from Q bit and size field. */
        @JvmStatic
        fun fromEncoding(q: Int, size: Int): VectorArrangement = when {
            q == 0 && size == 0 -> B8
            q == 1 && size == 0 -> B16
            q == 0 && size == 1 -> H4
            q == 1 && size == 1 -> H8
            q == 0 && size == 2 -> S2
            q == 1 && size == 2 -> S4
            q == 1 && size == 3 -> D2
            else -> error("Invalid NEON arrangement: Q=$q, size=$size")
        }
    }
}
