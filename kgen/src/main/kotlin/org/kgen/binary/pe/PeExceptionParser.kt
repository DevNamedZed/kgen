package org.kgen.binary.pe

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Parses PE `.pdata` and `.xdata` sections for x86-64 exception handling.
 *
 * Reads RUNTIME_FUNCTION entries from `.pdata` and their corresponding
 * UNWIND_INFO structures from `.xdata`.
 *
 * ```java
 * var pe = PeReader.read(bytes);
 * var entries = PeExceptionParser.parse(pe);
 * for (var entry : entries) {
 *     System.out.printf("[0x%x..0x%x) unwind flags=0x%x%n",
 *         entry.function().beginAddress(),
 *         entry.function().endAddress(),
 *         entry.unwindInfo().flags());
 * }
 * ```
 */
object PeExceptionParser {

    data class ParsedEntry(
        val function: RuntimeFunction,
        val unwindInfo: PeUnwindInfo,
    )

    /**
     * Parse exception entries from a PE file.
     * Returns empty list if no .pdata section exists.
     */
    @JvmStatic
    fun parse(pe: PeFile): List<ParsedEntry> {
        val pdataSection = pe.sectionByName(".pdata") ?: return emptyList()
        val xdataSection = pe.sectionByName(".xdata") ?: return emptyList()

        val pdataData = pdataSection.data ?: return emptyList()
        val xdataData = xdataSection.data ?: return emptyList()

        return parse(pdataData, xdataData, xdataSection.virtualAddress)
    }

    /**
     * Parse from raw section bytes.
     *
     * @param pdataBytes raw .pdata section content
     * @param xdataBytes raw .xdata section content
     * @param xdataRva the RVA of the .xdata section (for resolving unwind info pointers)
     */
    @JvmStatic
    @JvmOverloads
    fun parse(pdataBytes: ByteArray, xdataBytes: ByteArray, xdataRva: Int = 0): List<ParsedEntry> {
        val results = mutableListOf<ParsedEntry>()
        val pdata = ByteBuffer.wrap(pdataBytes).order(ByteOrder.LITTLE_ENDIAN)

        while (pdata.remaining() >= 12) {
            val beginAddr = pdata.int
            val endAddr = pdata.int
            val unwindInfoRva = pdata.int

            if (beginAddr == 0 && endAddr == 0 && unwindInfoRva == 0) break

            val rf = RuntimeFunction(beginAddr, endAddr, unwindInfoRva)
            val xdataOffset = unwindInfoRva - xdataRva
            if (xdataOffset >= 0 && xdataOffset < xdataBytes.size) {
                val unwindInfo = parseUnwindInfo(xdataBytes, xdataOffset)
                results.add(ParsedEntry(rf, unwindInfo))
            }
        }

        return results
    }

    /**
     * Parse a single UNWIND_INFO structure from .xdata bytes at the given offset.
     */
    @JvmStatic
    fun parseUnwindInfo(xdataBytes: ByteArray, offset: Int): PeUnwindInfo {
        val buf = ByteBuffer.wrap(xdataBytes, offset, xdataBytes.size - offset).order(ByteOrder.LITTLE_ENDIAN)

        val byte0 = buf.get().toInt() and 0xFF
        val version = byte0 and 0x07
        val flags = (byte0 shr 3) and 0x1F
        val prologSize = buf.get().toInt() and 0xFF
        val codeCount = buf.get().toInt() and 0xFF
        val byte3 = buf.get().toInt() and 0xFF
        val frameRegister = byte3 and 0x0F
        val frameOffset = (byte3 shr 4) and 0x0F

        val codes = mutableListOf<UnwindCode>()
        var remaining = codeCount
        while (remaining > 0) {
            val prologOffset = buf.get().toInt() and 0xFF
            val opByte = buf.get().toInt() and 0xFF
            val opCode = opByte and 0x0F
            val opInfo = (opByte shr 4) and 0x0F
            val operation = UnwindOperation.fromCode(opCode)
            remaining--

            var extraData = 0L
            when (operation) {
                UnwindOperation.ALLOC_LARGE -> {
                    if (opInfo == 0) {
                        extraData = (buf.short.toInt() and 0xFFFF).toLong() * 8
                        remaining--
                    } else {
                        extraData = buf.int.toLong() and 0xFFFFFFFFL
                        remaining -= 2
                    }
                }
                UnwindOperation.SAVE_NONVOL -> {
                    extraData = (buf.short.toInt() and 0xFFFF).toLong() * 8
                    remaining--
                }
                UnwindOperation.SAVE_NONVOL_FAR -> {
                    extraData = buf.int.toLong() and 0xFFFFFFFFL
                    remaining -= 2
                }
                UnwindOperation.SAVE_XMM128 -> {
                    extraData = (buf.short.toInt() and 0xFFFF).toLong() * 16
                    remaining--
                }
                UnwindOperation.SAVE_XMM128_FAR -> {
                    extraData = buf.int.toLong() and 0xFFFFFFFFL
                    remaining -= 2
                }
                else -> {}
            }

            codes.add(UnwindCode(prologOffset, operation, opInfo, extraData))
        }

        // Skip padding if odd count
        if (codeCount % 2 != 0) {
            buf.short // padding
        }

        // Exception handler or chained info
        var exceptionHandlerRva: Int? = null
        var chainedInfoRva: Int? = null
        if (flags and PeUnwindInfo.UNW_FLAG_EHANDLER != 0 || flags and PeUnwindInfo.UNW_FLAG_UHANDLER != 0) {
            if (buf.remaining() >= 4) exceptionHandlerRva = buf.int
        } else if (flags and PeUnwindInfo.UNW_FLAG_CHAININFO != 0) {
            if (buf.remaining() >= 4) chainedInfoRva = buf.int
        }

        return PeUnwindInfo(
            version = version,
            flags = flags,
            prologSize = prologSize,
            frameRegister = frameRegister,
            frameOffset = frameOffset,
            unwindCodes = codes,
            exceptionHandlerRva = exceptionHandlerRva,
            chainedInfoRva = chainedInfoRva,
        )
    }
}
