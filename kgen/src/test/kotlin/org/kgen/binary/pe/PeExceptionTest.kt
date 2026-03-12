package org.kgen.binary.pe

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class PeExceptionTest {

    // --- UnwindOperation enum ---

    @Test
    fun unwindOperationFromCodeRoundTrips() {
        for (op in UnwindOperation.entries) {
            assertEquals(op, UnwindOperation.fromCode(op.code))
        }
    }

    @Test
    fun unwindOperationFromCodeThrowsForUnknown() {
        assertThrows(IllegalArgumentException::class.java) {
            UnwindOperation.fromCode(99)
        }
    }

    // --- PeExceptionWriter ---

    @Test
    fun writeEmptyFunctionsProducesEmptySections() {
        val result = PeExceptionWriter.write(emptyList())
        assertEquals(0, result.pdataBytes.size)
        assertEquals(0, result.xdataBytes.size)
        assertTrue(result.runtimeFunctions.isEmpty())
    }

    @Test
    fun writeSingleFunctionProduces12BytePdata() {
        val unwind = PeUnwindInfo(version = 1, flags = 0, prologSize = 4, frameRegister = 0, frameOffset = 0, unwindCodes = emptyList())
        val func = PeExceptionWriter.FunctionUnwind("test", 0x1000, 0x1080, unwind)
        val result = PeExceptionWriter.write(listOf(func))
        // .pdata: 1 RUNTIME_FUNCTION = 12 bytes
        assertEquals(12, result.pdataBytes.size)
        // Verify begin/end addresses
        assertEquals(0x1000, readI32(result.pdataBytes, 0))
        assertEquals(0x1080, readI32(result.pdataBytes, 4))
    }

    @Test
    fun writeSortsFunctionsByBeginAddress() {
        val unwind = PeUnwindInfo(version = 1, flags = 0, prologSize = 4, frameRegister = 0, frameOffset = 0, unwindCodes = emptyList())
        val funcs = listOf(
            PeExceptionWriter.FunctionUnwind("b", 0x2000, 0x2080, unwind),
            PeExceptionWriter.FunctionUnwind("a", 0x1000, 0x1080, unwind),
        )
        val result = PeExceptionWriter.write(funcs)
        // First entry should be the one with lower begin address
        assertEquals(0x1000, readI32(result.pdataBytes, 0))
        assertEquals(0x2000, readI32(result.pdataBytes, 12))
    }

    @Test
    fun writeUnwindInfoVersionAndFlags() {
        val unwind = PeUnwindInfo(version = 1, flags = PeUnwindInfo.UNW_FLAG_EHANDLER, prologSize = 10, frameRegister = 5, frameOffset = 2, unwindCodes = emptyList())
        val func = PeExceptionWriter.FunctionUnwind("test", 0, 100, unwind)
        val result = PeExceptionWriter.write(listOf(func))
        val xdata = result.xdataBytes
        // Byte 0: version (3 bits) | flags (5 bits)
        val byte0 = xdata[0].toInt() and 0xFF
        assertEquals(1, byte0 and 0x07) // version
        assertEquals(PeUnwindInfo.UNW_FLAG_EHANDLER, (byte0 shr 3) and 0x1F) // flags
        // Byte 1: prolog size
        assertEquals(10, xdata[1].toInt() and 0xFF)
        // Byte 3: frame register | frame offset
        val byte3 = xdata[3].toInt() and 0xFF
        assertEquals(5, byte3 and 0x0F) // frame register (RBP)
        assertEquals(2, (byte3 shr 4) and 0x0F) // frame offset
    }

    @Test
    fun writePushNonvolUnwindCode() {
        val codes = listOf(UnwindCode(1, UnwindOperation.PUSH_NONVOL, 5)) // push rbp at offset 1
        val unwind = PeUnwindInfo(version = 1, flags = 0, prologSize = 1, frameRegister = 0, frameOffset = 0, unwindCodes = codes)
        val func = PeExceptionWriter.FunctionUnwind("test", 0, 100, unwind)
        val result = PeExceptionWriter.write(listOf(func))
        val xdata = result.xdataBytes
        assertEquals(1, xdata[2].toInt() and 0xFF) // code count = 1
        // Unwind code at offset 4 (after 4-byte header)
        assertEquals(1, xdata[4].toInt() and 0xFF) // prolog offset
        val opByte = xdata[5].toInt() and 0xFF
        assertEquals(UnwindOperation.PUSH_NONVOL.code, opByte and 0x0F)
        assertEquals(5, (opByte shr 4) and 0x0F) // register = RBP
    }

    @Test
    fun writeAllocSmallUnwindCode() {
        // ALLOC_SMALL: info = (size / 8) - 1, so info=3 means 32 bytes
        val codes = listOf(UnwindCode(4, UnwindOperation.ALLOC_SMALL, 3))
        val unwind = PeUnwindInfo(version = 1, flags = 0, prologSize = 4, frameRegister = 0, frameOffset = 0, unwindCodes = codes)
        val func = PeExceptionWriter.FunctionUnwind("test", 0, 100, unwind)
        val result = PeExceptionWriter.write(listOf(func))
        val xdata = result.xdataBytes
        val opByte = xdata[5].toInt() and 0xFF
        assertEquals(UnwindOperation.ALLOC_SMALL.code, opByte and 0x0F)
        assertEquals(3, (opByte shr 4) and 0x0F)
    }

    @Test
    fun writeAllocLargeWithExtraSlot() {
        // ALLOC_LARGE with info=0: 1 extra slot (size/8 as 16-bit)
        val codes = listOf(UnwindCode(7, UnwindOperation.ALLOC_LARGE, 0, 256))
        val unwind = PeUnwindInfo(version = 1, flags = 0, prologSize = 7, frameRegister = 0, frameOffset = 0, unwindCodes = codes)
        val func = PeExceptionWriter.FunctionUnwind("test", 0, 100, unwind)
        val result = PeExceptionWriter.write(listOf(func))
        val xdata = result.xdataBytes
        // Extra data: 256 / 8 = 32 as 16-bit LE
        assertEquals(32, readI16(xdata, 6))
    }

    @Test
    fun writeExceptionHandlerRva() {
        val unwind = PeUnwindInfo(
            version = 1, flags = PeUnwindInfo.UNW_FLAG_EHANDLER,
            prologSize = 0, frameRegister = 0, frameOffset = 0,
            unwindCodes = emptyList(), exceptionHandlerRva = 0xDEAD
        )
        val func = PeExceptionWriter.FunctionUnwind("test", 0, 100, unwind)
        val result = PeExceptionWriter.write(listOf(func))
        val xdata = result.xdataBytes
        // After header (4 bytes) + 0 codes + no padding = handler RVA at byte 4
        assertEquals(0xDEAD, readI32(xdata, 4))
    }

    @Test
    fun standardPrologueProducesValidUnwindInfo() {
        val info = PeExceptionWriter.standardPrologue(8, 64)
        assertEquals(1, info.version)
        assertEquals(0, info.flags)
        assertEquals(8, info.prologSize)
        assertEquals(5, info.frameRegister) // RBP
        assertTrue(info.unwindCodes.isNotEmpty())
        // Should have: ALLOC_SMALL (or ALLOC_LARGE), SET_FPREG, PUSH_NONVOL
        val ops = info.unwindCodes.map { it.operation }
        assertTrue(UnwindOperation.PUSH_NONVOL in ops)
        assertTrue(UnwindOperation.SET_FPREG in ops)
    }

    @Test
    fun standardPrologueWithExceptionHandler() {
        val info = PeExceptionWriter.standardPrologue(8, 32, exceptionHandlerRva = 0x5000)
        assertEquals(PeUnwindInfo.UNW_FLAG_EHANDLER, info.flags)
        assertEquals(0x5000, info.exceptionHandlerRva)
    }

    // --- PeExceptionParser (round-trip) ---

    @Test
    fun writeAndParseRoundTrips() {
        val unwind = PeUnwindInfo(
            version = 1, flags = 0, prologSize = 4, frameRegister = 5, frameOffset = 0,
            unwindCodes = listOf(
                UnwindCode(4, UnwindOperation.ALLOC_SMALL, 3),
                UnwindCode(1, UnwindOperation.PUSH_NONVOL, 5),
            )
        )
        val func = PeExceptionWriter.FunctionUnwind("main", 0x1000, 0x1080, unwind)
        val result = PeExceptionWriter.write(listOf(func))

        val parsed = PeExceptionParser.parse(result.pdataBytes, result.xdataBytes)
        assertEquals(1, parsed.size)
        assertEquals(0x1000, parsed[0].function.beginAddress)
        assertEquals(0x1080, parsed[0].function.endAddress)
        assertEquals(1, parsed[0].unwindInfo.version)
        assertEquals(0, parsed[0].unwindInfo.flags)
        assertEquals(4, parsed[0].unwindInfo.prologSize)
        assertEquals(5, parsed[0].unwindInfo.frameRegister)
        assertEquals(2, parsed[0].unwindInfo.unwindCodes.size)
    }

    @Test
    fun parseMultipleFunctionsRoundTrips() {
        val unwind1 = PeUnwindInfo(version = 1, flags = 0, prologSize = 4, frameRegister = 0, frameOffset = 0, unwindCodes = emptyList())
        val unwind2 = PeUnwindInfo(version = 1, flags = 0, prologSize = 8, frameRegister = 5, frameOffset = 0,
            unwindCodes = listOf(UnwindCode(1, UnwindOperation.PUSH_NONVOL, 5)))
        val funcs = listOf(
            PeExceptionWriter.FunctionUnwind("a", 0x1000, 0x1040, unwind1),
            PeExceptionWriter.FunctionUnwind("b", 0x2000, 0x20C0, unwind2),
        )
        val result = PeExceptionWriter.write(funcs)
        val parsed = PeExceptionParser.parse(result.pdataBytes, result.xdataBytes)
        assertEquals(2, parsed.size)
        assertEquals(0x1000, parsed[0].function.beginAddress)
        assertEquals(0x2000, parsed[1].function.beginAddress)
        assertEquals(0, parsed[0].unwindInfo.unwindCodes.size)
        assertEquals(1, parsed[1].unwindInfo.unwindCodes.size)
    }

    @Test
    fun parseWithExceptionHandlerRoundTrips() {
        val unwind = PeUnwindInfo(
            version = 1, flags = PeUnwindInfo.UNW_FLAG_EHANDLER,
            prologSize = 0, frameRegister = 0, frameOffset = 0,
            unwindCodes = emptyList(), exceptionHandlerRva = 0xBEEF
        )
        val func = PeExceptionWriter.FunctionUnwind("test", 0, 100, unwind)
        val result = PeExceptionWriter.write(listOf(func))
        val parsed = PeExceptionParser.parse(result.pdataBytes, result.xdataBytes)
        assertEquals(1, parsed.size)
        assertTrue(parsed[0].unwindInfo.hasExceptionHandler)
        assertEquals(0xBEEF, parsed[0].unwindInfo.exceptionHandlerRva)
    }

    @Test
    fun standardPrologueRoundTrips() {
        val info = PeExceptionWriter.standardPrologue(8, 64)
        val func = PeExceptionWriter.FunctionUnwind("main", 0, 200, info)
        val result = PeExceptionWriter.write(listOf(func))
        val parsed = PeExceptionParser.parse(result.pdataBytes, result.xdataBytes)
        assertEquals(1, parsed.size)
        assertEquals(8, parsed[0].unwindInfo.prologSize)
        assertEquals(5, parsed[0].unwindInfo.frameRegister)
        assertEquals(info.unwindCodes.size, parsed[0].unwindInfo.unwindCodes.size)
    }

    // --- PeUnwindInfo flags ---

    @Test
    fun unwindInfoFlagProperties() {
        val ehandler = PeUnwindInfo(1, PeUnwindInfo.UNW_FLAG_EHANDLER, 0, 0, 0, emptyList())
        assertTrue(ehandler.hasExceptionHandler)
        assertFalse(ehandler.hasTerminationHandler)
        assertFalse(ehandler.isChained)

        val uhandler = PeUnwindInfo(1, PeUnwindInfo.UNW_FLAG_UHANDLER, 0, 0, 0, emptyList())
        assertFalse(uhandler.hasExceptionHandler)
        assertTrue(uhandler.hasTerminationHandler)

        val chained = PeUnwindInfo(1, PeUnwindInfo.UNW_FLAG_CHAININFO, 0, 0, 0, emptyList())
        assertTrue(chained.isChained)
    }

    private fun readI32(data: ByteArray, offset: Int): Int {
        return (data[offset].toInt() and 0xFF) or
            ((data[offset + 1].toInt() and 0xFF) shl 8) or
            ((data[offset + 2].toInt() and 0xFF) shl 16) or
            ((data[offset + 3].toInt() and 0xFF) shl 24)
    }

    private fun readI16(data: ByteArray, offset: Int): Int {
        return (data[offset].toInt() and 0xFF) or
            ((data[offset + 1].toInt() and 0xFF) shl 8)
    }
}
