package org.kgen.binary.pe.pdb

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Helpers for building CodeView type and symbol records.
 *
 * ```kotlin
 * // Build an LF_PROCEDURE type
 * val procType = CodeViewBuilder.procedure(
 *     returnType = 0x0074, // T_INT4
 *     paramCount = 2,
 *     argListIndex = argListTypeIndex,
 * )
 *
 * // Build an S_GPROC32 symbol
 * val procSym = CodeViewBuilder.gproc32(
 *     name = "main",
 *     typeIndex = procTypeIndex,
 *     offset = 0,
 *     section = 1,
 *     codeSize = 42,
 * )
 * ```
 */
object CodeViewBuilder {

    // ─── Type Records ──────────────────────────────────────────────

    /** LF_POINTER */
    @JvmStatic
    fun pointer(referentType: Int, pointerKind: CvPointerKind = CvPointerKind.PTR_64,
                mode: CvPointerMode = CvPointerMode.POINTER, size: Int = 8): CvTypeRecord {
        val buf = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(referentType)
        val attrs = (pointerKind.code and 0x1F) or
                ((mode.code and 0x07) shl 5) or
                ((size and 0xFF) shl 13)
        buf.putInt(attrs)
        return CvTypeRecord(CvTypeKind.LF_POINTER.code, buf.array())
    }

    /** LF_MODIFIER */
    @JvmStatic
    fun modifier(modifiedType: Int, isConst: Boolean = false, isVolatile: Boolean = false): CvTypeRecord {
        val buf = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(modifiedType)
        var flags = 0
        if (isConst) flags = flags or 1
        if (isVolatile) flags = flags or 2
        buf.putShort(flags.toShort())
        buf.putShort(0) // padding
        return CvTypeRecord(CvTypeKind.LF_MODIFIER.code, buf.array())
    }

    /** LF_ARGLIST */
    @JvmStatic
    fun argList(argTypes: List<Int>): CvTypeRecord {
        val buf = ByteBuffer.allocate(4 + argTypes.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(argTypes.size)
        for (type in argTypes) buf.putInt(type)
        return CvTypeRecord(CvTypeKind.LF_ARGLIST.code, buf.array())
    }

    /** LF_PROCEDURE */
    @JvmStatic
    fun procedure(returnType: Int, callingConvention: Int = 0, paramCount: Int,
                  argListIndex: Int): CvTypeRecord {
        val buf = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(returnType)
        buf.put(callingConvention.toByte())
        buf.put(0) // funcAttr
        buf.putShort(paramCount.toShort())
        buf.putInt(argListIndex)
        return CvTypeRecord(CvTypeKind.LF_PROCEDURE.code, buf.array())
    }

    /** LF_STRUCTURE or LF_CLASS */
    @JvmStatic
    fun structure(name: String, fieldListIndex: Int = 0, size: Int = 0,
                  properties: Int = 0, derivedFrom: Int = 0, vshape: Int = 0,
                  memberCount: Int = 0, isClass: Boolean = false): CvTypeRecord {
        val kind = if (isClass) CvTypeKind.LF_CLASS.code else CvTypeKind.LF_STRUCTURE.code
        val buf = ByteArrayOutputStream()
        val header = ByteBuffer.allocate(18).order(ByteOrder.LITTLE_ENDIAN)
        header.putShort(memberCount.toShort())
        header.putShort(properties.toShort())
        header.putInt(fieldListIndex)
        header.putInt(derivedFrom)
        header.putInt(vshape)
        buf.write(header.array())
        // Size as LF_USHORT numeric leaf
        if (size < 0x8000) {
            val sizeBuf = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN)
            sizeBuf.putShort(size.toShort())
            buf.write(sizeBuf.array())
        } else {
            val sizeBuf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
            sizeBuf.putShort(0x8002.toShort()) // LF_USHORT
            sizeBuf.putShort(size.toShort())
            buf.write(sizeBuf.array())
        }
        buf.write(name.toByteArray(Charsets.UTF_8))
        buf.write(0)
        return CvTypeRecord(kind, buf.toByteArray())
    }

    /** LF_UNION */
    @JvmStatic
    fun union(name: String, fieldListIndex: Int = 0, size: Int = 0,
              properties: Int = 0, memberCount: Int = 0): CvTypeRecord {
        val buf = ByteArrayOutputStream()
        val header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        header.putShort(memberCount.toShort())
        header.putShort(properties.toShort())
        header.putInt(fieldListIndex)
        buf.write(header.array())
        if (size < 0x8000) {
            val sizeBuf = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN)
            sizeBuf.putShort(size.toShort())
            buf.write(sizeBuf.array())
        }
        buf.write(name.toByteArray(Charsets.UTF_8))
        buf.write(0)
        return CvTypeRecord(CvTypeKind.LF_UNION.code, buf.toByteArray())
    }

    /** LF_ENUM */
    @JvmStatic
    fun enum_(name: String, underlyingType: Int, fieldListIndex: Int = 0,
              properties: Int = 0, memberCount: Int = 0): CvTypeRecord {
        val buf = ByteArrayOutputStream()
        val header = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
        header.putShort(memberCount.toShort())
        header.putShort(properties.toShort())
        header.putInt(underlyingType)
        header.putInt(fieldListIndex)
        buf.write(header.array())
        buf.write(name.toByteArray(Charsets.UTF_8))
        buf.write(0)
        return CvTypeRecord(CvTypeKind.LF_ENUM.code, buf.toByteArray())
    }

    /** LF_ARRAY */
    @JvmStatic
    fun array(elementType: Int, indexType: Int, size: Int): CvTypeRecord {
        val buf = ByteArrayOutputStream()
        val header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        header.putInt(elementType)
        header.putInt(indexType)
        buf.write(header.array())
        if (size < 0x8000) {
            val sizeBuf = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN)
            sizeBuf.putShort(size.toShort())
            buf.write(sizeBuf.array())
        }
        buf.write(0) // name (empty)
        return CvTypeRecord(CvTypeKind.LF_ARRAY.code, buf.toByteArray())
    }

    /** LF_BITFIELD */
    @JvmStatic
    fun bitfield(type: Int, length: Int, position: Int): CvTypeRecord {
        val buf = ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(type)
        buf.put(length.toByte())
        buf.put(position.toByte())
        return CvTypeRecord(CvTypeKind.LF_BITFIELD.code, buf.array())
    }

    // ─── Symbol Records ────────────────────────────────────────────

    /** S_GPROC32 — global procedure. */
    @JvmStatic
    fun gproc32(name: String, typeIndex: Int, offset: Int, section: Int,
                codeSize: Int, flags: Int = 0): CvSymbolRecord {
        val buf = ByteArrayOutputStream()
        val header = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN)
        header.putInt(0) // parent
        header.putInt(0) // end
        header.putInt(0) // next
        header.putInt(codeSize) // code size
        header.putInt(0) // debug start offset
        header.putInt(0) // debug end offset
        header.putInt(typeIndex)
        header.putInt(offset)
        buf.write(header.array())
        val secFlags = ByteBuffer.allocate(3).order(ByteOrder.LITTLE_ENDIAN)
        secFlags.putShort(section.toShort())
        secFlags.put(flags.toByte())
        buf.write(secFlags.array())
        buf.write(name.toByteArray(Charsets.UTF_8))
        buf.write(0)
        return CvSymbolRecord(CvSymbolKind.S_GPROC32.code, buf.toByteArray())
    }

    /** S_LPROC32 — local procedure. */
    @JvmStatic
    fun lproc32(name: String, typeIndex: Int, offset: Int, section: Int,
                codeSize: Int, flags: Int = 0): CvSymbolRecord {
        val data = gproc32(name, typeIndex, offset, section, codeSize, flags).data
        return CvSymbolRecord(CvSymbolKind.S_LPROC32.code, data)
    }

    /** S_END — end of scope. */
    @JvmStatic
    fun end(): CvSymbolRecord = CvSymbolRecord(CvSymbolKind.S_END.code, byteArrayOf())

    /** S_GDATA32 — global data. */
    @JvmStatic
    fun gdata32(name: String, typeIndex: Int, offset: Int, section: Int): CvSymbolRecord {
        val buf = ByteArrayOutputStream()
        val header = ByteBuffer.allocate(10).order(ByteOrder.LITTLE_ENDIAN)
        header.putInt(typeIndex)
        header.putInt(offset)
        header.putShort(section.toShort())
        buf.write(header.array())
        buf.write(name.toByteArray(Charsets.UTF_8))
        buf.write(0)
        return CvSymbolRecord(CvSymbolKind.S_GDATA32.code, buf.toByteArray())
    }

    /** S_LDATA32 — local data. */
    @JvmStatic
    fun ldata32(name: String, typeIndex: Int, offset: Int, section: Int): CvSymbolRecord {
        val data = gdata32(name, typeIndex, offset, section).data
        return CvSymbolRecord(CvSymbolKind.S_LDATA32.code, data)
    }

    /** S_PUB32 — public symbol. */
    @JvmStatic
    fun pub32(name: String, offset: Int, section: Int, flags: Int = 0): CvSymbolRecord {
        val buf = ByteArrayOutputStream()
        val header = ByteBuffer.allocate(10).order(ByteOrder.LITTLE_ENDIAN)
        header.putInt(flags)
        header.putInt(offset)
        header.putShort(section.toShort())
        buf.write(header.array())
        buf.write(name.toByteArray(Charsets.UTF_8))
        buf.write(0)
        return CvSymbolRecord(CvSymbolKind.S_PUB32.code, buf.toByteArray())
    }

    /** S_UDT — user-defined type. */
    @JvmStatic
    fun udt(name: String, typeIndex: Int): CvSymbolRecord {
        val buf = ByteArrayOutputStream()
        val header = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
        header.putInt(typeIndex)
        buf.write(header.array())
        buf.write(name.toByteArray(Charsets.UTF_8))
        buf.write(0)
        return CvSymbolRecord(CvSymbolKind.S_UDT.code, buf.toByteArray())
    }

    /** S_CONSTANT — named constant. */
    @JvmStatic
    fun constant(name: String, typeIndex: Int, value: Long): CvSymbolRecord {
        val buf = ByteArrayOutputStream()
        val header = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
        header.putInt(typeIndex)
        buf.write(header.array())
        // Numeric leaf for value
        if (value in 0..0x7FFF) {
            val valBuf = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN)
            valBuf.putShort(value.toShort())
            buf.write(valBuf.array())
        } else {
            val valBuf = ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN)
            valBuf.putShort(0x800A.toShort()) // LF_UQUADWORD
            valBuf.putInt(value.toInt())
            buf.write(valBuf.array())
        }
        buf.write(name.toByteArray(Charsets.UTF_8))
        buf.write(0)
        return CvSymbolRecord(CvSymbolKind.S_CONSTANT.code, buf.toByteArray())
    }

    /** S_COMPILE3 — compiler information. */
    @JvmStatic
    fun compile3(language: CvSourceLanguage, cpu: CvCpuType, compilerName: String,
                 frontendVersion: IntArray = intArrayOf(0, 0, 0, 0),
                 backendVersion: IntArray = intArrayOf(0, 0, 0, 0)): CvSymbolRecord {
        val buf = ByteArrayOutputStream()
        val header = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
        val flags = (language.code and 0xFF) or ((cpu.code and 0xFFFF) shl 8)
        header.putInt(flags)
        buf.write(header.array())

        val versions = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
        versions.putShort(frontendVersion[0].toShort())
        versions.putShort(frontendVersion[1].toShort())
        versions.putShort(frontendVersion[2].toShort())
        versions.putShort(frontendVersion[3].toShort())
        versions.putShort(backendVersion[0].toShort())
        versions.putShort(backendVersion[1].toShort())
        versions.putShort(backendVersion[2].toShort())
        versions.putShort(backendVersion[3].toShort())
        buf.write(versions.array())

        buf.write(compilerName.toByteArray(Charsets.UTF_8))
        buf.write(0)
        return CvSymbolRecord(CvSymbolKind.S_COMPILE3.code, buf.toByteArray())
    }

    /** S_REGREL32 — register-relative variable (e.g., local on stack). */
    @JvmStatic
    fun regrel32(name: String, typeIndex: Int, offset: Int, register: Int): CvSymbolRecord {
        val buf = ByteArrayOutputStream()
        val header = ByteBuffer.allocate(10).order(ByteOrder.LITTLE_ENDIAN)
        header.putInt(offset)
        header.putInt(typeIndex)
        header.putShort(register.toShort())
        buf.write(header.array())
        buf.write(name.toByteArray(Charsets.UTF_8))
        buf.write(0)
        return CvSymbolRecord(CvSymbolKind.S_REGREL32.code, buf.toByteArray())
    }

    /** S_OBJNAME — object file name. */
    @JvmStatic
    fun objname(name: String, signature: Int = 0): CvSymbolRecord {
        val buf = ByteArrayOutputStream()
        val header = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
        header.putInt(signature)
        buf.write(header.array())
        buf.write(name.toByteArray(Charsets.UTF_8))
        buf.write(0)
        return CvSymbolRecord(CvSymbolKind.S_OBJNAME.code, buf.toByteArray())
    }
}
