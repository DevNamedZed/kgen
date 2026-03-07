package org.kgen.ir.target

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.CallingConvention

class TargetExtendedTest {

    // --- Factory method return types and basic properties ---

    @Test
    fun `wasm factory returns wasm32 arch`() {
        val t = Target.wasm()
        assertEquals(Arch.WASM32, t.arch)
        assertEquals("generic", t.cpu)
        assertEquals(CallingConvention.C, t.defaultCallingConv)
        assertNull(t.classFileVersion)
    }

    @Test
    fun `x86_64 factory returns correct defaults`() {
        val t = Target.x86_64()
        assertEquals(Arch.X86_64, t.arch)
        assertEquals(8, t.pointerSize)
        assertEquals(Endianness.LITTLE, t.endianness)
        assertEquals("generic", t.cpu)
    }

    @Test
    fun `arm64 factory returns correct defaults`() {
        val t = Target.arm64()
        assertEquals(Arch.ARM64, t.arch)
        assertEquals(8, t.pointerSize)
        assertEquals(Endianness.LITTLE, t.endianness)
        assertEquals("generic", t.cpu)
        assertTrue(t.has(Arm64Feature.NEON))
    }

    @Test
    fun `riscv64 factory returns correct defaults`() {
        val t = Target.riscv64()
        assertEquals(Arch.RISCV64, t.arch)
        assertEquals(8, t.pointerSize)
        assertEquals(Endianness.LITTLE, t.endianness)
        assertEquals("generic", t.cpu)
    }

    @Test
    fun `jvm factory defaults to class file version 24`() {
        val t = Target.jvm()
        assertEquals(24, t.classFileVersion)
        assertEquals(Endianness.BIG, t.endianness)
        assertEquals(8, t.pointerSize)
    }

    // --- CPU enum discovery ---

    @Test
    fun `x86 cpu enum contains expected entries`() {
        val cpus = X86CPU.entries
        assertTrue(cpus.size >= 13)
        assertNotNull(X86CPU.valueOf("GENERIC"))
        assertNotNull(X86CPU.valueOf("HASWELL"))
        assertNotNull(X86CPU.valueOf("ZEN4"))
        assertNotNull(X86CPU.valueOf("SAPPHIRE_RAPIDS"))
    }

    @Test
    fun `arm64 cpu enum contains expected entries`() {
        val cpus = Arm64CPU.entries
        assertTrue(cpus.size >= 9)
        assertNotNull(Arm64CPU.valueOf("GENERIC"))
        assertNotNull(Arm64CPU.valueOf("APPLE_M1"))
        assertNotNull(Arm64CPU.valueOf("CORTEX_A76"))
    }

    @Test
    fun `riscv cpu enum contains expected entries`() {
        val cpus = RiscVCPU.entries
        assertTrue(cpus.size >= 3)
        assertNotNull(RiscVCPU.valueOf("GENERIC"))
        assertNotNull(RiscVCPU.valueOf("SIFIVE_U74"))
        assertNotNull(RiscVCPU.valueOf("SIFIVE_P670"))
    }

    // --- Feature flags per architecture ---

    @Test
    fun `x86 features have unique feature names`() {
        val names = X86Feature.entries.map { it.featureName }
        assertEquals(names.size, names.toSet().size, "Feature names must be unique")
    }

    @Test
    fun `arm64 features have unique feature names`() {
        val names = Arm64Feature.entries.map { it.featureName }
        assertEquals(names.size, names.toSet().size, "Feature names must be unique")
    }

    @Test
    fun `riscv features have unique feature names`() {
        val names = RiscVFeature.entries.map { it.featureName }
        assertEquals(names.size, names.toSet().size, "Feature names must be unique")
    }

    @Test
    fun `wasm features have unique feature names`() {
        val names = WasmFeature.entries.map { it.featureName }
        assertEquals(names.size, names.toSet().size, "Feature names must be unique")
    }

    @Test
    fun `riscv generic has standard IMAFD_C extensions`() {
        val features = RiscVCPU.GENERIC.features
        assertTrue(features.contains(RiscVFeature.M))
        assertTrue(features.contains(RiscVFeature.A))
        assertTrue(features.contains(RiscVFeature.F))
        assertTrue(features.contains(RiscVFeature.D))
        assertTrue(features.contains(RiscVFeature.C))
        assertTrue(features.contains(RiscVFeature.ZICSR))
        assertTrue(features.contains(RiscVFeature.ZIFENCEI))
    }

    @Test
    fun `sifive p670 inherits generic and adds vector`() {
        val t = Target.riscv64(RiscVCPU.SIFIVE_P670)
        assertTrue(t.has(RiscVFeature.M))
        assertTrue(t.has(RiscVFeature.V))
        assertTrue(t.has(RiscVFeature.ZBA))
        assertTrue(t.has(RiscVFeature.ZBB))
    }

    // --- CPU feature inheritance chains ---

    @Test
    fun `x86 broadwell inherits haswell features`() {
        val haswell = X86CPU.HASWELL.features
        val broadwell = X86CPU.BROADWELL.features
        assertTrue(broadwell.containsAll(haswell))
        assertTrue(broadwell.contains(X86Feature.ADX))
        assertTrue(broadwell.contains(X86Feature.RDSEED))
    }

    @Test
    fun `arm64 apple m2 inherits m1 features`() {
        val m1 = Arm64CPU.APPLE_M1.features
        val m2 = Arm64CPU.APPLE_M2.features
        assertTrue(m2.containsAll(m1))
        assertTrue(m2.contains(Arm64Feature.BF16))
        assertTrue(m2.contains(Arm64Feature.I8MM))
    }

    @Test
    fun `arm64 cortex x2 has sve2 and mte`() {
        val t = Target.arm64(Arm64CPU.CORTEX_X2)
        assertTrue(t.has(Arm64Feature.SVE2))
        assertTrue(t.has(Arm64Feature.MTE))
        assertTrue(t.has(Arm64Feature.BTI))
        assertTrue(t.has(Arm64Feature.PAC))
    }

    // --- Enable and disable ---

    @Test
    fun `disable vararg removes multiple features`() {
        val t = Target.x86_64(X86CPU.HASWELL)
            .disable(X86Feature.FMA, X86Feature.BMI1, X86Feature.BMI2)
        assertFalse(t.has(X86Feature.FMA))
        assertFalse(t.has(X86Feature.BMI1))
        assertFalse(t.has(X86Feature.BMI2))
        assertTrue(t.has(X86Feature.AVX2))
    }

    @Test
    fun `enable then disable same feature cancels out`() {
        val t = Target.x86_64()
            .enable(X86Feature.AVX512F)
            .disable(X86Feature.AVX512F)
        assertFalse(t.has(X86Feature.AVX512F))
    }

    @Test
    fun `enable wasm feature on wasm target`() {
        val t = Target.wasm()
            .enable(WasmFeature.GC)
            .enable(WasmFeature.THREADS)
        assertTrue(t.has(WasmFeature.GC))
        assertTrue(t.has(WasmFeature.THREADS))
    }

    // --- Target equality and hashing ---

    @Test
    fun `equal targets have same hashCode`() {
        val t1 = Target.x86_64(X86CPU.HASWELL)
        val t2 = Target.x86_64(X86CPU.HASWELL)
        assertEquals(t1, t2)
        assertEquals(t1.hashCode(), t2.hashCode())
    }

    @Test
    fun `different cpus produce different targets`() {
        val t1 = Target.x86_64(X86CPU.HASWELL)
        val t2 = Target.x86_64(X86CPU.ZEN4)
        assertNotEquals(t1, t2)
    }

    @Test
    fun `different archs produce different targets`() {
        assertNotEquals(Target.wasm(), Target.x86_64())
        assertNotEquals(Target.arm64(), Target.riscv64())
        assertNotEquals(Target.jvm(), Target.msil())
    }

    // --- Triple string ---

    @Test
    fun `arm64 triple contains aarch64`() {
        val triple = Target.arm64().tripleString()
        assertTrue(triple.startsWith("aarch64"))
    }

    @Test
    fun `riscv64 triple contains riscv64`() {
        val triple = Target.riscv64().tripleString()
        assertTrue(triple.startsWith("riscv64"))
    }

    // --- Custom target ---

    @Test
    fun `custom target with big endian`() {
        val t = Target.custom(
            arch = Arch.ARM64,
            cpu = "custom-arm",
            features = setOf(Arm64Feature.NEON, Arm64Feature.SVE),
            pointerSize = 8,
            endianness = Endianness.BIG,
        )
        assertEquals(Endianness.BIG, t.endianness)
        assertEquals("custom-arm", t.cpu)
        assertTrue(t.has(Arm64Feature.SVE))
        assertFalse(t.has(Arm64Feature.SVE2))
    }

    @Test
    fun `custom target with non-default calling convention`() {
        val t = Target.custom(
            arch = Arch.X86_64,
            cpu = "custom-x86",
            features = emptySet(),
            pointerSize = 8,
            endianness = Endianness.LITTLE,
            defaultCallingConv = CallingConvention.FAST,
        )
        assertEquals(CallingConvention.FAST, t.defaultCallingConv)
    }

    // --- Arch enum ---

    @Test
    fun `arch enum contains all expected values`() {
        val arches = Arch.entries.map { it.name }.toSet()
        assertTrue(arches.contains("WASM32"))
        assertTrue(arches.contains("WASM64"))
        assertTrue(arches.contains("JVM"))
        assertTrue(arches.contains("X86_64"))
        assertTrue(arches.contains("ARM64"))
        assertTrue(arches.contains("RISCV64"))
        assertTrue(arches.contains("MSIL"))
        assertTrue(arches.contains("MSIL_MIXED"))
        assertEquals(8, Arch.entries.size)
    }

    @Test
    fun `endianness enum has two values`() {
        assertEquals(2, Endianness.entries.size)
        assertNotEquals(Endianness.LITTLE, Endianness.BIG)
    }

    // --- TargetFeature interface ---

    @Test
    fun `all feature enums implement TargetFeature`() {
        val x86: TargetFeature = X86Feature.AVX
        val arm: TargetFeature = Arm64Feature.NEON
        val rv: TargetFeature = RiscVFeature.V
        val wasm: TargetFeature = WasmFeature.GC
        assertEquals("avx", x86.featureName)
        assertEquals("neon", arm.featureName)
        assertEquals("v", rv.featureName)
        assertEquals("gc", wasm.featureName)
    }

    @Test
    fun `zen5 inherits zen4 features`() {
        val zen4 = X86CPU.ZEN4.features
        val zen5 = X86CPU.ZEN5.features
        assertTrue(zen5.containsAll(zen4))
    }
}
