package org.kgen.ir.target

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.CallingConvention

class TargetTest {

    @Test
    fun `wasm target defaults`() {
        val t = Target.wasm()
        assertEquals(Arch.WASM32, t.arch)
        assertEquals(4, t.pointerSize)
        assertEquals(Endianness.LITTLE, t.endianness)
        assertTrue(t.features.isEmpty())
    }

    @Test
    fun `wasm target with features`() {
        val t = Target.wasm(setOf(WasmFeature.SIMD128, WasmFeature.BULK_MEMORY))
        assertTrue(t.has(WasmFeature.SIMD128))
        assertTrue(t.has(WasmFeature.BULK_MEMORY))
        assertFalse(t.has(WasmFeature.GC))
    }

    @Test
    fun `jvm target defaults`() {
        val t = Target.jvm()
        assertEquals(Arch.JVM, t.arch)
        assertEquals(24, t.classFileVersion)
    }

    @Test
    fun `jvm target with version`() {
        val t = Target.jvm(21)
        assertEquals(21, t.classFileVersion)
    }

    @Test
    fun `x86_64 generic`() {
        val t = Target.x86_64()
        assertEquals(Arch.X86_64, t.arch)
        assertEquals(8, t.pointerSize)
        assertEquals("generic", t.cpu)
        assertFalse(t.has(X86Feature.AVX))
    }

    @Test
    fun `x86_64 haswell features`() {
        val t = Target.x86_64(X86CPU.HASWELL)
        assertTrue(t.has(X86Feature.AVX2))
        assertTrue(t.has(X86Feature.FMA))
        assertTrue(t.has(X86Feature.BMI1))
        assertTrue(t.has(X86Feature.BMI2))
        assertTrue(t.has(X86Feature.SSE4_2))
        assertFalse(t.has(X86Feature.AVX512F))
    }

    @Test
    fun `x86_64 skylake_avx512 has avx512`() {
        val t = Target.x86_64(X86CPU.SKYLAKE_AVX512)
        assertTrue(t.has(X86Feature.AVX512F))
        assertTrue(t.has(X86Feature.AVX512BW))
        assertTrue(t.has(X86Feature.AVX512DQ))
        assertTrue(t.has(X86Feature.AVX512VL))
        assertTrue(t.has(X86Feature.AVX2))
    }

    @Test
    fun `enable adds feature`() {
        val t = Target.x86_64(X86CPU.HASWELL).enable(X86Feature.AVX512F)
        assertTrue(t.has(X86Feature.AVX512F))
        assertTrue(t.has(X86Feature.AVX2))
    }

    @Test
    fun `disable removes feature`() {
        val t = Target.x86_64(X86CPU.HASWELL).disable(X86Feature.FMA)
        assertFalse(t.has(X86Feature.FMA))
        assertTrue(t.has(X86Feature.AVX2))
    }

    @Test
    fun `enable and disable are immutable`() {
        val original = Target.x86_64(X86CPU.HASWELL)
        val modified = original.enable(X86Feature.AVX512F)
        assertFalse(original.has(X86Feature.AVX512F))
        assertTrue(modified.has(X86Feature.AVX512F))
    }

    @Test
    fun `enable vararg`() {
        val t = Target.x86_64(X86CPU.GENERIC)
            .enable(X86Feature.AVX, X86Feature.AVX2, X86Feature.FMA)
        assertTrue(t.has(X86Feature.AVX))
        assertTrue(t.has(X86Feature.AVX2))
        assertTrue(t.has(X86Feature.FMA))
    }

    @Test
    fun `arm64 apple m1`() {
        val t = Target.arm64(Arm64CPU.APPLE_M1)
        assertTrue(t.has(Arm64Feature.NEON))
        assertTrue(t.has(Arm64Feature.FP16))
        assertTrue(t.has(Arm64Feature.DOTPROD))
        assertTrue(t.has(Arm64Feature.ATOMICS))
        assertFalse(t.has(Arm64Feature.SVE2))
    }

    @Test
    fun `arm64 apple m4 has sve2`() {
        val t = Target.arm64(Arm64CPU.APPLE_M4)
        assertTrue(t.has(Arm64Feature.SVE2))
        assertTrue(t.has(Arm64Feature.NEON))
    }

    @Test
    fun `riscv64 generic`() {
        val t = Target.riscv64()
        assertTrue(t.has(RiscVFeature.M))
        assertTrue(t.has(RiscVFeature.A))
        assertTrue(t.has(RiscVFeature.F))
        assertTrue(t.has(RiscVFeature.D))
        assertTrue(t.has(RiscVFeature.C))
    }

    @Test
    fun `native detects architecture`() {
        val t = Target.native()
        assertNotNull(t.arch)
        assertTrue(t.pointerSize > 0)
    }

    @Test
    fun `custom target`() {
        val t = Target.custom(
            arch = Arch.X86_64,
            cpu = "my-cpu",
            features = setOf(X86Feature.SSE4_2, X86Feature.POPCNT),
            pointerSize = 8,
            endianness = Endianness.LITTLE,
        )
        assertEquals("my-cpu", t.cpu)
        assertTrue(t.has(X86Feature.SSE4_2))
        assertTrue(t.has(X86Feature.POPCNT))
        assertFalse(t.has(X86Feature.AVX))
    }

    @Test
    fun `triple string`() {
        assertTrue(Target.wasm().tripleString().startsWith("wasm32"))
        assertEquals("jvm", Target.jvm().tripleString())
        assertTrue(Target.x86_64().tripleString().startsWith("x86_64"))
    }

    @Test
    fun `zen4 has avx512`() {
        val t = Target.x86_64(X86CPU.ZEN4)
        assertTrue(t.has(X86Feature.AVX512F))
        assertTrue(t.has(X86Feature.AVX512BW))
        assertTrue(t.has(X86Feature.SHA))
    }

    @Test
    fun `msil target`() {
        val t = Target.msil()
        assertEquals(Arch.MSIL, t.arch)
        assertEquals(8, t.pointerSize)
        assertEquals("msil", t.tripleString())
    }

    @Test
    fun `msil mixed target`() {
        val t = Target.msilMixed()
        assertEquals(Arch.MSIL_MIXED, t.arch)
        assertEquals(8, t.pointerSize)
        assertEquals("msil-mixed", t.tripleString())
    }

    @Test
    fun `cpu profiles inherit features`() {
        val nehalem = X86CPU.NEHALEM.features
        val sandy = X86CPU.SANDYBRIDGE.features
        val haswell = X86CPU.HASWELL.features
        assertTrue(sandy.containsAll(nehalem))
        assertTrue(haswell.containsAll(sandy))
    }
}
