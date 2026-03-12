package org.kgen.binary.pe

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Writes COFF import libraries (.lib) for linking against DLLs on Windows.
 *
 * Produces a valid .lib archive containing short import objects (IMPORT_OBJECT_HEADER format)
 * that tell the linker which DLL provides each symbol.
 *
 * Usage:
 *   val writer = CoffImportLibraryWriter()
 *   val bytes = writer.write("kernel32.dll", listOf("CreateFileW", "ReadFile", "CloseHandle"))
 */
class CoffImportLibraryWriter {

    /**
     * Generates a COFF import library (.lib) for the given DLL and exported symbols.
     *
     * @param dllName the name of the DLL (e.g. "kernel32.dll")
     * @param exports list of symbol names to import
     * @param machine COFF machine type (default: x86-64)
     * @return the raw bytes of the .lib file
     */
    fun write(
        dllName: String,
        exports: List<String>,
        machine: Int = PeConstants.MACHINE_AMD64,
    ): ByteArray {
        require(exports.isNotEmpty()) { "exports must not be empty" }

        val importObjects = exports.mapIndexed { index, name ->
            buildImportObject(name, dllName, machine, index)
        }

        val buf = ByteArrayOutputStream()

        // Archive magic
        buf.write(ARCHIVE_MAGIC)

        // First linker member (big-endian symbol table)
        val firstLinker = buildFirstLinkerMember(exports, importObjects.size)
        writeArchiveHeader(buf, "/", firstLinker.size)
        buf.write(firstLinker)
        if (firstLinker.size % 2 != 0) buf.write('\n'.code)

        // Second linker member (little-endian, sorted symbol table)
        val secondLinkerOffset = buf.size()
        val secondLinker = buildSecondLinkerMember(exports, importObjects.size)
        writeArchiveHeader(buf, "/", secondLinker.size)
        buf.write(secondLinker)
        if (secondLinker.size % 2 != 0) buf.write('\n'.code)

        // Now we know where each import object will land; patch offsets in linker members
        val memberOffsets = computeMemberOffsets(buf.size(), importObjects)

        // Write import objects
        for ((index, obj) in importObjects.withIndex()) {
            val memberName = formatImportMemberName(exports[index], dllName)
            writeArchiveHeader(buf, memberName, obj.size)
            buf.write(obj)
            if (obj.size % 2 != 0) buf.write('\n'.code)
        }

        // Patch the linker member offsets
        val result = buf.toByteArray()
        patchFirstLinkerMember(result, ARCHIVE_MAGIC.size + HEADER_SIZE, exports, memberOffsets)
        patchSecondLinkerMember(result, secondLinkerOffset + HEADER_SIZE, exports, memberOffsets)

        return result
    }

    private fun buildImportObject(
        symbolName: String,
        dllName: String,
        machine: Int,
        hint: Int,
    ): ByteArray {
        val symbolBytes = symbolName.toByteArray(Charsets.US_ASCII)
        val dllBytes = dllName.toByteArray(Charsets.US_ASCII)
        val dataSize = symbolBytes.size + 1 + dllBytes.size + 1

        val buf = ByteBuffer.allocate(IMPORT_HEADER_SIZE + dataSize).order(ByteOrder.LITTLE_ENDIAN)

        // IMPORT_OBJECT_HEADER
        buf.putShort(SIG1) // Sig1 = 0x0000
        buf.putShort(SIG2) // Sig2 = 0xFFFF
        buf.putShort(IMPORT_OBJECT_VERSION) // Version
        buf.putShort(machine.toShort()) // Machine
        buf.putInt(0) // TimeDateStamp
        buf.putInt(dataSize) // SizeOfData
        buf.putShort(hint.toShort()) // Ordinal/Hint
        buf.putShort(buildTypeField(IMPORT_CODE, IMPORT_NAME)) // Type

        // Symbol name (null-terminated)
        buf.put(symbolBytes)
        buf.put(0)

        // DLL name (null-terminated)
        buf.put(dllBytes)
        buf.put(0)

        return buf.array()
    }

    private fun buildTypeField(importType: Int, nameType: Int): Short {
        // Bits 0-1: import type, Bits 2-4: name type
        return ((importType and 0x3) or ((nameType and 0x7) shl 2)).toShort()
    }

    private fun buildFirstLinkerMember(exports: List<String>, memberCount: Int): ByteArray {
        // First linker member: big-endian count + offsets + null-terminated strings
        val stringData = ByteArrayOutputStream()
        for (name in exports) {
            stringData.write(name.toByteArray(Charsets.US_ASCII))
            stringData.write(0)
        }
        val strings = stringData.toByteArray()

        val size = 4 + exports.size * 4 + strings.size
        val buf = ByteBuffer.allocate(size).order(ByteOrder.BIG_ENDIAN)
        buf.putInt(exports.size)
        // Offsets are placeholders — will be patched later
        for (i in exports.indices) buf.putInt(0)
        buf.put(strings)
        return buf.array()
    }

    private fun buildSecondLinkerMember(exports: List<String>, memberCount: Int): ByteArray {
        // Second linker member: little-endian
        // Format: memberCount(4) + memberOffsets[memberCount](4 each) +
        //         symbolCount(4) + indices[symbolCount](2 each) + strings

        val sorted = exports.withIndex().sortedBy { it.value }

        val stringData = ByteArrayOutputStream()
        for ((_, name) in sorted) {
            stringData.write(name.toByteArray(Charsets.US_ASCII))
            stringData.write(0)
        }
        val strings = stringData.toByteArray()

        val size = 4 + memberCount * 4 + 4 + exports.size * 2 + strings.size
        val buf = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)

        // Number of members (import objects)
        buf.putInt(memberCount)
        // Member offsets — placeholders, patched later
        for (i in 0 until memberCount) buf.putInt(0)

        // Number of symbols
        buf.putInt(exports.size)
        // 1-based member index for each symbol (sorted order)
        for ((originalIndex, _) in sorted) {
            buf.putShort((originalIndex + 1).toShort())
        }

        buf.put(strings)
        return buf.array()
    }

    private fun computeMemberOffsets(currentSize: Int, importObjects: List<ByteArray>): IntArray {
        val offsets = IntArray(importObjects.size)
        var offset = currentSize
        for (i in importObjects.indices) {
            offsets[i] = offset
            offset += HEADER_SIZE + importObjects[i].size
            if (importObjects[i].size % 2 != 0) offset++
        }
        return offsets
    }

    private fun patchFirstLinkerMember(
        data: ByteArray,
        dataStart: Int,
        exports: List<String>,
        memberOffsets: IntArray,
    ) {
        val buf = ByteBuffer.wrap(data, dataStart, 4 + exports.size * 4).order(ByteOrder.BIG_ENDIAN)
        buf.getInt() // skip count
        for (i in exports.indices) {
            // Each symbol maps to its corresponding import object
            buf.putInt(memberOffsets[i])
        }
    }

    private fun patchSecondLinkerMember(
        data: ByteArray,
        dataStart: Int,
        exports: List<String>,
        memberOffsets: IntArray,
    ) {
        val buf = ByteBuffer.wrap(data, dataStart, 4 + memberOffsets.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        buf.getInt() // skip member count
        for (offset in memberOffsets) {
            buf.putInt(offset)
        }
    }

    private fun formatImportMemberName(symbolName: String, dllName: String): String {
        // Truncate to fit 16-byte header name field
        val name = "$dllName/$symbolName"
        return if (name.length > 15) name.substring(0, 15) else name
    }

    private fun writeArchiveHeader(buf: ByteArrayOutputStream, name: String, size: Int) {
        buf.write(padRight(name, 16))
        buf.write(padRight("0", 12)) // mtime
        buf.write(padRight("0", 6)) // uid
        buf.write(padRight("0", 6)) // gid
        buf.write(padRight("0", 8)) // mode
        buf.write(padRight(size.toString(), 10)) // size
        buf.write("`\n".toByteArray(Charsets.US_ASCII))
    }

    private fun padRight(str: String, width: Int): ByteArray {
        val bytes = str.toByteArray(Charsets.US_ASCII)
        val result = ByteArray(width) { ' '.code.toByte() }
        System.arraycopy(bytes, 0, result, 0, minOf(bytes.size, width))
        return result
    }

    companion object {
        private val ARCHIVE_MAGIC = "!<arch>\n".toByteArray(Charsets.US_ASCII)
        private const val HEADER_SIZE = 60

        private const val IMPORT_HEADER_SIZE = 20
        private const val SIG1: Short = 0x0000
        private const val SIG2: Short = 0xFFFF.toShort()
        private const val IMPORT_OBJECT_VERSION: Short = 0

        // Import type (bits 0-1)
        private const val IMPORT_CODE = 0
        private const val IMPORT_DATA = 1
        private const val IMPORT_CONST = 2

        // Name type (bits 2-4)
        private const val IMPORT_ORDINAL = 0
        private const val IMPORT_NAME = 1
        private const val IMPORT_NAME_NO_PREFIX = 2
        private const val IMPORT_NAME_UNDECORATE = 3
    }
}
