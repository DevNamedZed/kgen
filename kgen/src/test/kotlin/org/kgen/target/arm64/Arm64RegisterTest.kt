package org.kgen.target.arm64

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class Arm64RegisterTest {

    @Test
    fun `X registers have correct encodings`() {
        assertEquals(0, Arm64Register.X0.encoding())
        assertEquals(30, Arm64Register.X30.encoding())
        assertEquals(31, Arm64Register.SP.encoding())
        assertEquals(31, Arm64Register.XZR.encoding())
    }

    @Test
    fun `W registers have correct encodings`() {
        assertEquals(0, Arm64Register.W0.encoding())
        assertEquals(30, Arm64Register.W30.encoding())
        assertEquals(31, Arm64Register.WZR.encoding())
    }

    @Test
    fun `FP and LR are aliases`() {
        assertSame(Arm64Register.X29, Arm64Register.FP)
        assertSame(Arm64Register.X30, Arm64Register.LR)
    }

    @Test
    fun `SIMD registers have correct encodings`() {
        assertEquals(0, Arm64Register.S0.encoding())
        assertEquals(31, Arm64Register.S31.encoding())
        assertEquals(0, Arm64Register.D0.encoding())
        assertEquals(31, Arm64Register.D31.encoding())
        assertEquals(0, Arm64Register.Q0.encoding())
        assertEquals(31, Arm64Register.Q31.encoding())
    }

    @Test
    fun `X register sizes are 64 bit`() {
        assertEquals(64, Arm64Register.X0.bits())
        assertEquals(64, Arm64Register.X30.bits())
    }

    @Test
    fun `W register sizes are 32 bit`() {
        assertEquals(32, Arm64Register.W0.bits())
        assertEquals(32, Arm64Register.W30.bits())
    }

    @Test
    fun `allX returns 31 registers`() {
        val all = Arm64Register.allX()
        assertEquals(31, all.size)
        assertEquals(Arm64Register.X0, all[0])
        assertEquals(Arm64Register.X30, all[30])
    }

    @Test
    fun `byEncoding64 round-trips`() {
        for (i in 0..30) {
            val reg = Arm64Register.byEncoding64(i)
            assertEquals(i, reg.encoding())
            assertEquals(64, reg.bits())
        }
    }

    @Test
    fun `register names are correct`() {
        assertEquals("x0", Arm64Register.X0.name())
        assertEquals("sp", Arm64Register.SP.name())
        assertEquals("xzr", Arm64Register.XZR.name())
        assertEquals("w0", Arm64Register.W0.name())
        assertEquals("wzr", Arm64Register.WZR.name())
        assertEquals("d0", Arm64Register.D0.name())
        assertEquals("q15", Arm64Register.Q15.name())
    }
}
