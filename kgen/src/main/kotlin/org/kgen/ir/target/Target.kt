package org.kgen.ir.target

import org.kgen.ir.CallingConvention
import org.kgen.ir.IrCategory
import org.kgen.ir.IrConstraints
import org.kgen.ir.TargetProfile

/**
 * Compilation target. Determines valid instructions, calling conventions, pointer sizes,
 * and output format.
 *
 * Create targets through the companion factory methods:
 *
 * ```kotlin
 * Target.wasm()                                // WASM MVP
 * Target.jvm(21)                               // JVM class file version 21
 * Target.x86_64(X86CPU.HASWELL)                // x86-64 with Haswell features
 * Target.arm64(Arm64CPU.APPLE_M1)              // ARM64 with Apple M1 features
 * Target.msil()                                // .NET MSIL (pure managed)
 * Target.msilMixed()                           // .NET MSIL mixed mode (managed + native)
 * Target.native()                              // detect current machine
 * ```
 *
 * Targets are immutable. [enable] and [disable] return new instances:
 *
 * ```kotlin
 * val t = Target.x86_64(X86CPU.HASWELL)
 *     .enable(X86Feature.AVX512F)
 *     .disable(X86Feature.FMA)
 * ```
 */
@ConsistentCopyVisibility
data class Target internal constructor(
    val arch: Arch,
    val cpu: String,
    val features: Set<TargetFeature>,
    val pointerSize: Int,
    val endianness: Endianness,
    val defaultCallingConv: CallingConvention,
    val classFileVersion: Int? = null,
) {
    /** Default instruction constraints for this target. Returns `null` (all allowed) for native targets. */
    fun defaultConstraints(): Set<IrCategory>? = when (arch) {
        Arch.JVM, Arch.MSIL -> IrConstraints.MANAGED_VM
        Arch.MSIL_MIXED -> IrConstraints.ALL
        else -> null
    }

    fun has(feature: TargetFeature): Boolean = feature in features

    fun enable(feature: TargetFeature): Target = copy(features = features + feature)
    fun disable(feature: TargetFeature): Target = copy(features = features - feature)

    fun enable(vararg features: TargetFeature): Target = copy(features = this.features + features.toSet())
    fun disable(vararg features: TargetFeature): Target = copy(features = this.features - features.toSet())

    /**
     * Returns the [TargetProfile] that corresponds to this target's architecture.
     * Used when constructing a [ModuleBuilder] from a Target for backward compatibility.
     */
    fun profile(): TargetProfile = when (arch) {
        Arch.JVM -> TargetProfile.MANAGED_VM
        Arch.MSIL -> TargetProfile.MANAGED_VM
        Arch.MSIL_MIXED -> TargetProfile.MIXED
        Arch.WASM32, Arch.WASM64 -> TargetProfile.NATIVE
        Arch.X86_64, Arch.ARM64, Arch.RISCV64 -> TargetProfile.NATIVE
    }

    fun tripleString(): String = when (arch) {
        Arch.WASM32 -> "wasm32-unknown-unknown"
        Arch.WASM64 -> "wasm64-unknown-unknown"
        Arch.JVM -> "jvm"
        Arch.X86_64 -> "x86_64-unknown-${detectOS()}"
        Arch.ARM64 -> "aarch64-unknown-${detectOS()}"
        Arch.RISCV64 -> "riscv64-unknown-${detectOS()}"
        Arch.MSIL -> "msil"
        Arch.MSIL_MIXED -> "msil-mixed"
    }

    companion object {
        // --- WASM ---

        @JvmStatic fun wasm(): Target = wasm(emptySet())

        @JvmStatic fun wasm(features: Set<WasmFeature>): Target = Target(
            arch = Arch.WASM32, cpu = "generic", features = features,
            pointerSize = 4, endianness = Endianness.LITTLE,
            defaultCallingConv = CallingConvention.C,
        )

        // --- JVM ---

        @JvmStatic fun jvm(): Target = jvm(24)

        @JvmStatic fun jvm(classFileVersion: Int): Target = Target(
            arch = Arch.JVM, cpu = "jvm", features = emptySet(),
            pointerSize = 8, endianness = Endianness.BIG,
            defaultCallingConv = CallingConvention.C,
            classFileVersion = classFileVersion,
        )

        // --- x86-64 ---

        @JvmStatic fun x86_64(): Target = x86_64(X86CPU.GENERIC)

        @JvmStatic fun x86_64(cpu: X86CPU): Target = Target(
            arch = Arch.X86_64, cpu = cpu.cpuName, features = cpu.features,
            pointerSize = 8, endianness = Endianness.LITTLE,
            defaultCallingConv = CallingConvention.C,
        )

        // --- ARM64 ---

        @JvmStatic fun arm64(): Target = arm64(Arm64CPU.GENERIC)

        @JvmStatic fun arm64(cpu: Arm64CPU): Target = Target(
            arch = Arch.ARM64, cpu = cpu.cpuName, features = cpu.features,
            pointerSize = 8, endianness = Endianness.LITTLE,
            defaultCallingConv = CallingConvention.C,
        )

        // --- RISC-V 64 ---

        @JvmStatic fun riscv64(): Target = riscv64(RiscVCPU.GENERIC)

        @JvmStatic fun riscv64(cpu: RiscVCPU): Target = Target(
            arch = Arch.RISCV64, cpu = cpu.cpuName, features = cpu.features,
            pointerSize = 8, endianness = Endianness.LITTLE,
            defaultCallingConv = CallingConvention.C,
        )

        // --- MSIL ---

        @JvmStatic fun msil(): Target = Target(
            arch = Arch.MSIL, cpu = "msil", features = emptySet(),
            pointerSize = 8, endianness = Endianness.LITTLE,
            defaultCallingConv = CallingConvention.C,
        )

        @JvmStatic fun msilMixed(): Target = Target(
            arch = Arch.MSIL_MIXED, cpu = "msil-mixed", features = emptySet(),
            pointerSize = 8, endianness = Endianness.LITTLE,
            defaultCallingConv = CallingConvention.C,
        )

        // --- Native detection ---

        @JvmStatic fun native(): Target {
            val osArch = System.getProperty("os.arch")?.lowercase() ?: "unknown"
            return when {
                osArch == "amd64" || osArch == "x86_64" -> x86_64(X86CPU.GENERIC)
                osArch == "aarch64" || osArch == "arm64" -> arm64(Arm64CPU.GENERIC)
                osArch.startsWith("riscv") -> riscv64(RiscVCPU.GENERIC)
                else -> error("Cannot detect native target for os.arch=$osArch")
            }
        }

        /**
         * Create a custom target profile from scratch.
         */
        @JvmStatic fun custom(
            arch: Arch,
            cpu: String,
            features: Set<TargetFeature>,
            pointerSize: Int,
            endianness: Endianness,
            defaultCallingConv: CallingConvention = CallingConvention.C,
        ): Target = Target(arch, cpu, features, pointerSize, endianness, defaultCallingConv)

        private fun detectOS(): String {
            val os = System.getProperty("os.name")?.lowercase() ?: return "unknown"
            return when {
                "linux" in os -> "linux-gnu"
                "mac" in os || "darwin" in os -> "darwin"
                "win" in os -> "windows-msvc"
                else -> "unknown"
            }
        }
    }
}

/** Target architecture. */
enum class Arch {
    WASM32, WASM64, JVM, X86_64, ARM64, RISCV64, MSIL, MSIL_MIXED,
}

/** Byte order. */
enum class Endianness {
    LITTLE, BIG,
}

/** Marker interface for all target feature enums. */
interface TargetFeature {
    val featureName: String
}

// ---------------------------------------------------------------------------
// x86-64
// ---------------------------------------------------------------------------

enum class X86Feature(override val featureName: String) : TargetFeature {
    SSE3("sse3"), SSSE3("ssse3"), SSE4_1("sse4.1"), SSE4_2("sse4.2"),
    AVX("avx"), AVX2("avx2"),
    AVX512F("avx512f"), AVX512CD("avx512cd"), AVX512BW("avx512bw"),
    AVX512DQ("avx512dq"), AVX512VL("avx512vl"),
    AVX512VNNI("avx512vnni"), AVX512BITALG("avx512bitalg"),
    AVX512IFMA("avx512ifma"), AVX512VPOPCNTDQ("avx512vpopcntdq"),
    AVX512FP16("avx512fp16"),
    AVX_VNNI("avxvnni"), AVX_IFMA("avxifma"),
    FMA("fma"),
    BMI1("bmi"), BMI2("bmi2"),
    POPCNT("popcnt"), LZCNT("lzcnt"),
    ADX("adx"), RDSEED("rdseed"), RDRAND("rdrand"),
    AES("aes"), PCLMUL("pclmulqdq"), SHA("sha"),
    F16C("f16c"), MOVBE("movbe"), ABM("abm"),
    AMX_TILE("amx-tile"), AMX_INT8("amx-int8"), AMX_BF16("amx-bf16"),
    CMPXCHG16B("cx16"),
    SAHF("sahf"),
    ;
}

enum class X86CPU(val cpuName: String, val features: Set<X86Feature>) {
    GENERIC("generic", setOf(X86Feature.CMPXCHG16B, X86Feature.SAHF)),

    NEHALEM("nehalem", GENERIC.features + setOf(
        X86Feature.SSE3, X86Feature.SSSE3, X86Feature.SSE4_1, X86Feature.SSE4_2,
        X86Feature.POPCNT,
    )),

    SANDYBRIDGE("sandybridge", NEHALEM.features + setOf(
        X86Feature.AVX,
    )),

    HASWELL("haswell", SANDYBRIDGE.features + setOf(
        X86Feature.AVX2, X86Feature.FMA, X86Feature.BMI1, X86Feature.BMI2,
        X86Feature.LZCNT, X86Feature.MOVBE, X86Feature.F16C,
    )),

    BROADWELL("broadwell", HASWELL.features + setOf(
        X86Feature.ADX, X86Feature.RDSEED,
    )),

    SKYLAKE("skylake", BROADWELL.features + setOf(
        X86Feature.AES, X86Feature.PCLMUL,
    )),

    SKYLAKE_AVX512("skylake-avx512", SKYLAKE.features + setOf(
        X86Feature.AVX512F, X86Feature.AVX512CD, X86Feature.AVX512BW,
        X86Feature.AVX512DQ, X86Feature.AVX512VL,
    )),

    CASCADELAKE("cascadelake", SKYLAKE_AVX512.features + setOf(
        X86Feature.AVX512VNNI,
    )),

    ICELAKE_SERVER("icelake-server", CASCADELAKE.features + setOf(
        X86Feature.AVX512BITALG, X86Feature.AVX512IFMA, X86Feature.AVX512VPOPCNTDQ,
    )),

    SAPPHIRE_RAPIDS("sapphire-rapids", ICELAKE_SERVER.features + setOf(
        X86Feature.AVX512FP16, X86Feature.AMX_TILE, X86Feature.AMX_INT8, X86Feature.AMX_BF16,
        X86Feature.AVX_VNNI,
    )),

    ZEN1("znver1", GENERIC.features + setOf(
        X86Feature.SSE3, X86Feature.SSSE3, X86Feature.SSE4_1, X86Feature.SSE4_2,
        X86Feature.AVX, X86Feature.AVX2, X86Feature.FMA,
        X86Feature.BMI1, X86Feature.BMI2, X86Feature.POPCNT, X86Feature.LZCNT,
        X86Feature.AES, X86Feature.PCLMUL, X86Feature.SHA,
        X86Feature.ADX, X86Feature.RDSEED, X86Feature.RDRAND,
        X86Feature.MOVBE, X86Feature.F16C, X86Feature.ABM,
    )),

    ZEN2("znver2", ZEN1.features),

    ZEN3("znver3", ZEN2.features),

    ZEN4("znver4", ZEN3.features + setOf(
        X86Feature.AVX512F, X86Feature.AVX512CD, X86Feature.AVX512BW,
        X86Feature.AVX512DQ, X86Feature.AVX512VL,
        X86Feature.AVX512VNNI, X86Feature.AVX512BITALG,
        X86Feature.AVX512IFMA, X86Feature.AVX512VPOPCNTDQ,
    )),

    ZEN5("znver5", ZEN4.features),
    ;
}

// ---------------------------------------------------------------------------
// ARM64
// ---------------------------------------------------------------------------

enum class Arm64Feature(override val featureName: String) : TargetFeature {
    NEON("neon"),
    FP16("fp16"), BF16("bf16"),
    DOTPROD("dotprod"), I8MM("i8mm"),
    SVE("sve"), SVE2("sve2"),
    AES("aes"), SHA2("sha2"), SHA3("sha3"), SM4("sm4"),
    CRC32("crc"), ATOMICS("lse"), LSE2("lse2"),
    MTE("mte"), BTI("bti"), PAC("pauth"),
    FRINTTS("frintts"),
    FLAGM("flagm"),
    RCPC("rcpc"), RCPC2("rcpc-immo"),
    ;
}

enum class Arm64CPU(val cpuName: String, val features: Set<Arm64Feature>) {
    GENERIC("generic", setOf(Arm64Feature.NEON)),

    CORTEX_A55("cortex-a55", GENERIC.features + setOf(
        Arm64Feature.CRC32, Arm64Feature.ATOMICS, Arm64Feature.AES, Arm64Feature.SHA2,
        Arm64Feature.DOTPROD, Arm64Feature.RCPC,
    )),

    CORTEX_A76("cortex-a76", CORTEX_A55.features + setOf(
        Arm64Feature.FP16, Arm64Feature.RCPC2,
    )),

    CORTEX_A78("cortex-a78", CORTEX_A76.features),

    CORTEX_X2("cortex-x2", CORTEX_A78.features + setOf(
        Arm64Feature.SVE2, Arm64Feature.BF16, Arm64Feature.I8MM,
        Arm64Feature.BTI, Arm64Feature.MTE, Arm64Feature.PAC, Arm64Feature.FLAGM,
    )),

    NEOVERSE_N1("neoverse-n1", CORTEX_A76.features),

    NEOVERSE_V2("neoverse-v2", CORTEX_X2.features),

    APPLE_M1("apple-m1", GENERIC.features + setOf(
        Arm64Feature.FP16, Arm64Feature.DOTPROD,
        Arm64Feature.CRC32, Arm64Feature.ATOMICS, Arm64Feature.AES, Arm64Feature.SHA2, Arm64Feature.SHA3,
        Arm64Feature.RCPC, Arm64Feature.FRINTTS, Arm64Feature.FLAGM,
    )),

    APPLE_M2("apple-m2", APPLE_M1.features + setOf(
        Arm64Feature.BF16, Arm64Feature.I8MM, Arm64Feature.BTI,
    )),

    APPLE_M3("apple-m3", APPLE_M2.features),

    APPLE_M4("apple-m4", APPLE_M3.features + setOf(
        Arm64Feature.SVE2, Arm64Feature.SM4,
    )),
    ;
}

// ---------------------------------------------------------------------------
// RISC-V
// ---------------------------------------------------------------------------

enum class RiscVFeature(override val featureName: String) : TargetFeature {
    M("m"), A("a"), F("f"), D("d"), C("c"),
    V("v"),
    B("b"),
    ZICSR("zicsr"), ZIFENCEI("zifencei"),
    ZBA("zba"), ZBB("zbb"), ZBC("zbc"), ZBS("zbs"),
    ZMMUL("zmmul"),
    ZBKB("zbkb"), ZBKC("zbkc"), ZBKX("zbkx"),
    ZKNE("zkne"), ZKND("zknd"), ZKNH("zknh"),
    ;
}

enum class RiscVCPU(val cpuName: String, val features: Set<RiscVFeature>) {
    GENERIC("generic", setOf(
        RiscVFeature.M, RiscVFeature.A, RiscVFeature.F, RiscVFeature.D, RiscVFeature.C,
        RiscVFeature.ZICSR, RiscVFeature.ZIFENCEI,
    )),

    SIFIVE_U74("sifive-u74", GENERIC.features),

    SIFIVE_P670("sifive-p670", GENERIC.features + setOf(
        RiscVFeature.V, RiscVFeature.ZBA, RiscVFeature.ZBB, RiscVFeature.ZBS,
    )),
    ;
}

// ---------------------------------------------------------------------------
// WASM
// ---------------------------------------------------------------------------

enum class WasmFeature(override val featureName: String) : TargetFeature {
    MUTABLE_GLOBALS("mutable-globals"),
    SIGN_EXTENSION("sign-ext"),
    SATURATING_FLOAT("nontrapping-fptoint"),
    BULK_MEMORY("bulk-memory"),
    REFERENCE_TYPES("reference-types"),
    MULTI_VALUE("multivalue"),
    SIMD128("simd128"),
    RELAXED_SIMD("relaxed-simd"),
    TAIL_CALL("tail-call"),
    EXTENDED_CONST("extended-const"),
    GC("gc"),
    EXCEPTION_HANDLING("exception-handling"),
    THREADS("atomics"),
    MULTI_MEMORY("multi-memory"),
    MEMORY64("memory64"),
    ;
}
