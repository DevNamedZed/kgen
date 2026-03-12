package org.kgen.target.arm64

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class Arm64RegisterExtendedTest {

    // --- X register encodings ---

    @Test
    fun allXRegistersHaveSequentialEncodings() {
        val all = Arm64Register.allX()
        for (i in all.indices) {
            assertEquals(i, all[i].encoding(), "X$i encoding")
        }
    }

    @Test
    fun x0Through10Encodings() {
        assertEquals(0, Arm64Register.X0.encoding())
        assertEquals(1, Arm64Register.X1.encoding())
        assertEquals(2, Arm64Register.X2.encoding())
        assertEquals(3, Arm64Register.X3.encoding())
        assertEquals(4, Arm64Register.X4.encoding())
        assertEquals(5, Arm64Register.X5.encoding())
        assertEquals(6, Arm64Register.X6.encoding())
        assertEquals(7, Arm64Register.X7.encoding())
        assertEquals(8, Arm64Register.X8.encoding())
        assertEquals(9, Arm64Register.X9.encoding())
        assertEquals(10, Arm64Register.X10.encoding())
    }

    @Test
    fun x11Through20Encodings() {
        assertEquals(11, Arm64Register.X11.encoding())
        assertEquals(12, Arm64Register.X12.encoding())
        assertEquals(13, Arm64Register.X13.encoding())
        assertEquals(14, Arm64Register.X14.encoding())
        assertEquals(15, Arm64Register.X15.encoding())
        assertEquals(16, Arm64Register.X16.encoding())
        assertEquals(17, Arm64Register.X17.encoding())
        assertEquals(18, Arm64Register.X18.encoding())
        assertEquals(19, Arm64Register.X19.encoding())
        assertEquals(20, Arm64Register.X20.encoding())
    }

    @Test
    fun x21Through30Encodings() {
        assertEquals(21, Arm64Register.X21.encoding())
        assertEquals(22, Arm64Register.X22.encoding())
        assertEquals(23, Arm64Register.X23.encoding())
        assertEquals(24, Arm64Register.X24.encoding())
        assertEquals(25, Arm64Register.X25.encoding())
        assertEquals(26, Arm64Register.X26.encoding())
        assertEquals(27, Arm64Register.X27.encoding())
        assertEquals(28, Arm64Register.X28.encoding())
        assertEquals(29, Arm64Register.X29.encoding())
        assertEquals(30, Arm64Register.X30.encoding())
    }

    // --- W register encodings ---

    @Test
    fun w0Through15Encodings() {
        assertEquals(0, Arm64Register.W0.encoding())
        assertEquals(1, Arm64Register.W1.encoding())
        assertEquals(2, Arm64Register.W2.encoding())
        assertEquals(3, Arm64Register.W3.encoding())
        assertEquals(4, Arm64Register.W4.encoding())
        assertEquals(5, Arm64Register.W5.encoding())
        assertEquals(6, Arm64Register.W6.encoding())
        assertEquals(7, Arm64Register.W7.encoding())
        assertEquals(8, Arm64Register.W8.encoding())
        assertEquals(9, Arm64Register.W9.encoding())
        assertEquals(10, Arm64Register.W10.encoding())
        assertEquals(11, Arm64Register.W11.encoding())
        assertEquals(12, Arm64Register.W12.encoding())
        assertEquals(13, Arm64Register.W13.encoding())
        assertEquals(14, Arm64Register.W14.encoding())
        assertEquals(15, Arm64Register.W15.encoding())
    }

    @Test
    fun w16Through30Encodings() {
        assertEquals(16, Arm64Register.W16.encoding())
        assertEquals(17, Arm64Register.W17.encoding())
        assertEquals(18, Arm64Register.W18.encoding())
        assertEquals(19, Arm64Register.W19.encoding())
        assertEquals(20, Arm64Register.W20.encoding())
        assertEquals(21, Arm64Register.W21.encoding())
        assertEquals(22, Arm64Register.W22.encoding())
        assertEquals(23, Arm64Register.W23.encoding())
        assertEquals(24, Arm64Register.W24.encoding())
        assertEquals(25, Arm64Register.W25.encoding())
        assertEquals(26, Arm64Register.W26.encoding())
        assertEquals(27, Arm64Register.W27.encoding())
        assertEquals(28, Arm64Register.W28.encoding())
        assertEquals(29, Arm64Register.W29.encoding())
        assertEquals(30, Arm64Register.W30.encoding())
    }

    // --- Bit widths ---

    @Test
    fun allXRegistersAre64Bit() {
        val all = Arm64Register.allX()
        for (reg in all) {
            assertEquals(64, reg.bits(), "${reg.name()} should be 64-bit")
        }
    }

    @Test
    fun spIs64Bit() {
        assertEquals(64, Arm64Register.SP.bits())
    }

    @Test
    fun xzrIs64Bit() {
        assertEquals(64, Arm64Register.XZR.bits())
    }

    @Test
    fun allWRegistersAre32Bit() {
        for (i in 0..30) {
            val reg = Arm64Register.byEncoding32(i)
            assertEquals(32, reg.bits(), "W$i should be 32-bit")
        }
    }

    @Test
    fun wzrIs32Bit() {
        assertEquals(32, Arm64Register.WZR.bits())
    }

    // --- SIMD register widths ---

    @Test
    fun sRegistersAre32Bit() {
        assertEquals(32, Arm64Register.S0.bits())
        assertEquals(32, Arm64Register.S15.bits())
        assertEquals(32, Arm64Register.S31.bits())
    }

    @Test
    fun dRegistersAre64Bit() {
        assertEquals(64, Arm64Register.D0.bits())
        assertEquals(64, Arm64Register.D15.bits())
        assertEquals(64, Arm64Register.D31.bits())
    }

    @Test
    fun qRegistersAre128Bit() {
        assertEquals(128, Arm64Register.Q0.bits())
        assertEquals(128, Arm64Register.Q15.bits())
        assertEquals(128, Arm64Register.Q31.bits())
    }

    // --- SIMD register encodings ---

    @Test
    fun sRegisterEncodings() {
        assertEquals(0, Arm64Register.S0.encoding())
        assertEquals(15, Arm64Register.S15.encoding())
        assertEquals(31, Arm64Register.S31.encoding())
    }

    @Test
    fun dRegisterEncodings() {
        assertEquals(0, Arm64Register.D0.encoding())
        assertEquals(15, Arm64Register.D15.encoding())
        assertEquals(31, Arm64Register.D31.encoding())
    }

    @Test
    fun qRegisterEncodings() {
        assertEquals(0, Arm64Register.Q0.encoding())
        assertEquals(15, Arm64Register.Q15.encoding())
        assertEquals(31, Arm64Register.Q31.encoding())
    }

    // --- byEncoding round-trips ---

    @Test
    fun byEncoding32RoundTrips() {
        for (i in 0..30) {
            val reg = Arm64Register.byEncoding32(i)
            assertEquals(i, reg.encoding())
            assertEquals(32, reg.bits())
        }
    }

    @Test
    fun byEncoding64RoundTripsAll() {
        for (i in 0..30) {
            val reg = Arm64Register.byEncoding64(i)
            assertEquals(i, reg.encoding(), "X$i round-trip encoding")
            assertEquals(64, reg.bits(), "X$i bits")
        }
    }

    // --- Names ---

    @Test
    fun xRegisterNames() {
        for (i in 0..28) {
            assertEquals("x$i", Arm64Register.byEncoding64(i).name())
        }
    }

    @Test
    fun wRegisterNames() {
        for (i in 0..28) {
            assertEquals("w$i", Arm64Register.byEncoding32(i).name())
        }
    }

    @Test
    fun specialRegisterNames() {
        assertEquals("x29", Arm64Register.X29.name())
        assertEquals("x30", Arm64Register.X30.name())
        assertEquals("sp", Arm64Register.SP.name())
        assertEquals("xzr", Arm64Register.XZR.name())
        assertEquals("wzr", Arm64Register.WZR.name())
    }

    @Test
    fun simdRegisterNames() {
        assertEquals("s0", Arm64Register.S0.name())
        assertEquals("s15", Arm64Register.S15.name())
        assertEquals("d0", Arm64Register.D0.name())
        assertEquals("d31", Arm64Register.D31.name())
        assertEquals("q0", Arm64Register.Q0.name())
        assertEquals("q31", Arm64Register.Q31.name())
    }

    // --- Aliases ---

    @Test
    fun fpIsX29() {
        assertSame(Arm64Register.X29, Arm64Register.FP)
        assertEquals(29, Arm64Register.FP.encoding())
    }

    @Test
    fun lrIsX30() {
        assertSame(Arm64Register.X30, Arm64Register.LR)
        assertEquals(30, Arm64Register.LR.encoding())
    }

    @Test
    fun spAndXzrShareEncoding31() {
        assertEquals(31, Arm64Register.SP.encoding())
        assertEquals(31, Arm64Register.XZR.encoding())
        assertNotSame(Arm64Register.SP, Arm64Register.XZR)
    }

    // --- Type checks ---

    @Test
    fun xRegistersAre64BitType() {
        assertTrue(Arm64Register.X0 is Arm64Register64)
        assertTrue(Arm64Register.X30 is Arm64Register64)
        assertTrue(Arm64Register.SP is Arm64Register64)
        assertTrue(Arm64Register.XZR is Arm64Register64)
    }

    @Test
    fun wRegistersAre32BitType() {
        assertTrue(Arm64Register.W0 is Arm64Register32)
        assertTrue(Arm64Register.W30 is Arm64Register32)
        assertTrue(Arm64Register.WZR is Arm64Register32)
    }

    @Test
    fun sRegistersAreVecSType() {
        assertTrue(Arm64Register.S0 is Arm64VecS)
        assertTrue(Arm64Register.S31 is Arm64VecS)
    }

    @Test
    fun dRegistersAreVecDType() {
        assertTrue(Arm64Register.D0 is Arm64VecD)
        assertTrue(Arm64Register.D31 is Arm64VecD)
    }

    @Test
    fun qRegistersAreVecQType() {
        assertTrue(Arm64Register.Q0 is Arm64VecQ)
        assertTrue(Arm64Register.Q31 is Arm64VecQ)
    }

    // --- allW returns 31 registers ---

    @Test
    fun allWReturns31Registers() {
        val all = Arm64Register.allW()
        assertEquals(31, all.size)
        assertEquals(Arm64Register.W0, all[0])
        assertEquals(Arm64Register.W30, all[30])
    }

    // --- toString ---

    @Test
    fun toStringMatchesName() {
        assertEquals(Arm64Register.X0.name(), Arm64Register.X0.toString())
        assertEquals(Arm64Register.SP.name(), Arm64Register.SP.toString())
        assertEquals(Arm64Register.D0.name(), Arm64Register.D0.toString())
    }
}
