package org.kgen.binary.pe

import java.io.ByteArrayOutputStream

/**
 * Writes PE `.pdata` and `.xdata` sections for x86-64 Structured Exception Handling.
 *
 * `.pdata` contains a sorted array of [RuntimeFunction] entries (12 bytes each).
 * `.xdata` contains [PeUnwindInfo] structures referenced by the function entries.
 *
 * ```java
 * var funcs = List.of(
 *     new PeExceptionWriter.FunctionUnwind("main", 0, 128, unwindInfo)
 * );
 * var result = PeExceptionWriter.write(funcs);
 * // result.pdataBytes() -> .pdata section
 * // result.xdataBytes() -> .xdata section
 * ```
 */
object PeExceptionWriter {

    /**
     * A function's unwind information for PE SEH emission.
     */
    data class FunctionUnwind(
        val name: String,
        /** RVA of function start within .text. */
        val beginRva: Int,
        /** RVA of function end within .text. */
        val endRva: Int,
        /** Unwind info for this function. */
        val unwindInfo: PeUnwindInfo,
    )

    /**
     * Result of writing PE exception tables.
     */
    data class ExceptionSections(
        val pdataBytes: ByteArray,
        val xdataBytes: ByteArray,
        val runtimeFunctions: List<RuntimeFunction>,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ExceptionSections) return false
            return pdataBytes.contentEquals(other.pdataBytes) && xdataBytes.contentEquals(other.xdataBytes)
        }
        override fun hashCode(): Int = pdataBytes.contentHashCode() * 31 + xdataBytes.contentHashCode()
    }

    /**
     * Write .pdata and .xdata sections for the given functions.
     * Functions are sorted by begin address in the output.
     */
    @JvmStatic
    fun write(functions: List<FunctionUnwind>): ExceptionSections {
        if (functions.isEmpty()) {
            return ExceptionSections(ByteArray(0), ByteArray(0), emptyList())
        }

        val sorted = functions.sortedBy { it.beginRva }
        val xdata = ByteArrayOutputStream()
        val pdata = ByteArrayOutputStream()
        val runtimeFunctions = mutableListOf<RuntimeFunction>()

        for (func in sorted) {
            // Write unwind info to .xdata, record its offset
            val xdataOffset = xdata.size()
            writeUnwindInfo(xdata, func.unwindInfo)

            // Write RUNTIME_FUNCTION to .pdata
            writeInt32(pdata, func.beginRva)
            writeInt32(pdata, func.endRva)
            writeInt32(pdata, xdataOffset) // relative to .xdata section start

            runtimeFunctions.add(RuntimeFunction(func.beginRva, func.endRva, xdataOffset))
        }

        return ExceptionSections(pdata.toByteArray(), xdata.toByteArray(), runtimeFunctions)
    }

    /**
     * Write a single UNWIND_INFO structure.
     */
    internal fun writeUnwindInfo(out: ByteArrayOutputStream, info: PeUnwindInfo) {
        // Byte 0: version (3 bits) | flags (5 bits)
        out.write((info.version and 0x07) or ((info.flags and 0x1F) shl 3))
        // Byte 1: size of prolog
        out.write(info.prologSize and 0xFF)
        // Byte 2: count of unwind codes
        out.write(info.unwindCodes.size and 0xFF)
        // Byte 3: frame register (4 bits) | frame offset (4 bits)
        out.write((info.frameRegister and 0x0F) or ((info.frameOffset and 0x0F) shl 4))

        // Unwind codes (2 bytes each)
        for (code in info.unwindCodes) {
            out.write(code.prologOffset and 0xFF)
            out.write((code.operation.code and 0x0F) or ((code.operationInfo and 0x0F) shl 4))

            // Extra slots for large operations
            when (code.operation) {
                UnwindOperation.ALLOC_LARGE -> {
                    if (code.operationInfo == 0) {
                        // 1 extra slot: size / 8
                        writeInt16(out, (code.extraData / 8).toInt())
                    } else {
                        // 2 extra slots: full 32-bit size
                        writeInt32(out, code.extraData.toInt())
                    }
                }
                UnwindOperation.SAVE_NONVOL -> writeInt16(out, (code.extraData / 8).toInt())
                UnwindOperation.SAVE_NONVOL_FAR -> writeInt32(out, code.extraData.toInt())
                UnwindOperation.SAVE_XMM128 -> writeInt16(out, (code.extraData / 16).toInt())
                UnwindOperation.SAVE_XMM128_FAR -> writeInt32(out, code.extraData.toInt())
                else -> {} // No extra data
            }
        }

        // Pad to 4-byte alignment (unwind codes are 2 bytes each, need even count)
        if (info.unwindCodes.size % 2 != 0) {
            writeInt16(out, 0) // padding
        }

        // Exception handler or chained info (if flags indicate)
        if (info.hasExceptionHandler || info.hasTerminationHandler) {
            writeInt32(out, info.exceptionHandlerRva ?: 0)
        } else if (info.isChained) {
            writeInt32(out, info.chainedInfoRva ?: 0)
        }
    }

    /**
     * Create a simple unwind info for a function with a standard prologue:
     * push rbp; mov rbp, rsp; sub rsp, frameSize
     */
    @JvmStatic
    fun standardPrologue(prologSize: Int, frameSize: Int, exceptionHandlerRva: Int? = null): PeUnwindInfo {
        val codes = mutableListOf<UnwindCode>()
        var offset = 0

        // push rbp (1 byte: 0x55)
        offset += 1
        codes.add(UnwindCode(offset, UnwindOperation.PUSH_NONVOL, 5)) // RBP = register 5

        // mov rbp, rsp (3 bytes: 48 89 E5) — set frame pointer
        offset += 3
        codes.add(UnwindCode(offset, UnwindOperation.SET_FPREG, 0))

        // sub rsp, frameSize
        if (frameSize > 0) {
            offset += if (frameSize <= 128) 4 else 7
            if (frameSize <= 128) {
                codes.add(UnwindCode(offset, UnwindOperation.ALLOC_SMALL, (frameSize / 8) - 1))
            } else if (frameSize <= 512 * 1024 - 8) {
                codes.add(UnwindCode(offset, UnwindOperation.ALLOC_LARGE, 0, frameSize.toLong()))
            } else {
                codes.add(UnwindCode(offset, UnwindOperation.ALLOC_LARGE, 1, frameSize.toLong()))
            }
        }

        val flags = if (exceptionHandlerRva != null) PeUnwindInfo.UNW_FLAG_EHANDLER else 0
        return PeUnwindInfo(
            version = 1,
            flags = flags,
            prologSize = prologSize,
            frameRegister = 5, // RBP
            frameOffset = 0,
            unwindCodes = codes.reversed(), // highest offset first
            exceptionHandlerRva = exceptionHandlerRva,
        )
    }

    private fun writeInt16(out: ByteArrayOutputStream, v: Int) {
        out.write(v and 0xFF)
        out.write((v shr 8) and 0xFF)
    }

    private fun writeInt32(out: ByteArrayOutputStream, v: Int) {
        out.write(v and 0xFF)
        out.write((v shr 8) and 0xFF)
        out.write((v shr 16) and 0xFF)
        out.write((v shr 24) and 0xFF)
    }
}
