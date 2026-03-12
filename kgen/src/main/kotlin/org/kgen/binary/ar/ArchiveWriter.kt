package org.kgen.binary.ar

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Writes Unix archive (.a) and Windows static library (.lib) files.
 *
 * Supports GNU, BSD, and COFF archive variants. COFF archives write both
 * linker members: first (big-endian) and second (little-endian with indexed lookup).
 *
 * Usage:
 *   val writer = ArchiveWriter()
 *   val bytes = writer.write(archiveFile)
 *   // or build from object files:
 *   val bytes = writer.write(members, symbols)
 */
class ArchiveWriter(
    private val variant: ArchiveVariant = ArchiveVariant.GNU,
) {

    fun write(archive: ArchiveFile): ByteArray {
        return write(archive.members, archive.symbols)
    }

    fun write(
        members: List<ArchiveMember>,
        symbols: List<ArchiveSymbol> = emptyList(),
    ): ByteArray {
        val buf = ByteArrayOutputStream()
        buf.write("!<arch>\n".toByteArray(Charsets.US_ASCII))

        // Phase 1: compute member offsets (needed for symtab)
        val longNameTable = if (variant == ArchiveVariant.GNU || variant == ArchiveVariant.COFF) buildLongNameTable(members) else null
        val memberOffsets = computeMemberOffsets(members, symbols, longNameTable)

        // Phase 2: write symbol table(s)
        if (symbols.isNotEmpty()) {
            val resolvedSymbols = resolveSymbolOffsets(symbols, memberOffsets)
            writeSymtab(buf, resolvedSymbols)
            if (variant == ArchiveVariant.COFF) {
                writeCoffSecondLinkerMember(buf, resolvedSymbols, memberOffsets)
            }
        }

        // Phase 3: write long name table (GNU and COFF)
        if (longNameTable != null && longNameTable.data.isNotEmpty()) {
            writeMemberHeader(buf, "//", longNameTable.data.size, 0, 0, 0, 0)
            buf.write(longNameTable.data)
            if (longNameTable.data.size % 2 != 0) buf.write('\n'.code)
        }

        // Phase 4: write members
        for ((idx, member) in members.withIndex()) {
            val headerName = formatMemberName(member.name, longNameTable, idx)
            val memberData = if (variant == ArchiveVariant.BSD && member.name.length > 15) {
                val nameBytes = member.name.toByteArray(Charsets.US_ASCII)
                val padded = if (nameBytes.size % 4 != 0) {
                    nameBytes + ByteArray(4 - nameBytes.size % 4)
                } else nameBytes
                padded + member.data
            } else {
                member.data
            }

            writeMemberHeader(buf, headerName, memberData.size,
                member.modificationTime, member.ownerId, member.groupId, member.mode)
            buf.write(memberData)
            if (memberData.size % 2 != 0) buf.write('\n'.code)
        }

        return buf.toByteArray()
    }

    private data class LongNameTable(
        val data: ByteArray,
        val offsets: Map<Int, Int>, // member index → offset in table
    )

    private fun buildLongNameTable(members: List<ArchiveMember>): LongNameTable {
        val table = ByteArrayOutputStream()
        val offsets = mutableMapOf<Int, Int>()

        for ((idx, member) in members.withIndex()) {
            if (member.name.length > 15) {
                offsets[idx] = table.size()
                table.write(member.name.toByteArray(Charsets.US_ASCII))
                table.write("/\n".toByteArray(Charsets.US_ASCII))
            }
        }

        return LongNameTable(table.toByteArray(), offsets)
    }

    private fun computeMemberOffsets(
        members: List<ArchiveMember>,
        symbols: List<ArchiveSymbol>,
        longNameTable: LongNameTable?,
    ): Map<Int, Long> {
        var offset = 8L // "!<arch>\n"

        // First symbol table (symtab / first linker member) size
        if (symbols.isNotEmpty()) {
            val symtabSize = computeSymtabSize(symbols)
            offset += 60 + symtabSize
            if (symtabSize % 2 != 0) offset++

            // COFF second linker member
            if (variant == ArchiveVariant.COFF) {
                val secondSize = computeCoffSecondLinkerMemberSize(symbols, members.size)
                offset += 60 + secondSize
                if (secondSize % 2 != 0) offset++
            }
        }

        // Long name table
        if (longNameTable != null && longNameTable.data.isNotEmpty()) {
            offset += 60 + longNameTable.data.size
            if (longNameTable.data.size % 2 != 0) offset++
        }

        val offsets = mutableMapOf<Int, Long>()
        for ((idx, member) in members.withIndex()) {
            offsets[idx] = offset
            val dataSize = if (variant == ArchiveVariant.BSD && member.name.length > 15) {
                val nameBytes = member.name.toByteArray(Charsets.US_ASCII)
                val padLen = if (nameBytes.size % 4 != 0) 4 - nameBytes.size % 4 else 0
                nameBytes.size + padLen + member.data.size
            } else {
                member.data.size
            }
            offset += 60 + dataSize
            if (dataSize % 2 != 0) offset++
        }

        return offsets
    }

    private fun resolveSymbolOffsets(
        symbols: List<ArchiveSymbol>,
        memberOffsets: Map<Int, Long>,
    ): List<ArchiveSymbol> {
        // If symbols already have valid offsets, use them directly
        // Otherwise, try to resolve by matching memberOffset as an index
        return symbols.map { sym ->
            val resolvedOffset = memberOffsets[sym.memberOffset.toInt()] ?: sym.memberOffset
            sym.copy(memberOffset = resolvedOffset)
        }
    }

    private fun computeSymtabSize(symbols: List<ArchiveSymbol>): Int {
        val stringSize = symbols.sumOf { it.name.length + 1 }
        return 4 + symbols.size * 4 + stringSize
    }

    private fun writeSymtab(buf: ByteArrayOutputStream, symbols: List<ArchiveSymbol>) {
        val symData = ByteArrayOutputStream()
        val symBuf = ByteBuffer.allocate(4 + symbols.size * 4).order(ByteOrder.BIG_ENDIAN)
        symBuf.putInt(symbols.size)
        for (sym in symbols) symBuf.putInt(sym.memberOffset.toInt())
        symBuf.flip()
        val headerBytes = ByteArray(symBuf.remaining())
        symBuf.get(headerBytes)
        symData.write(headerBytes)
        for (sym in symbols) {
            symData.write(sym.name.toByteArray(Charsets.US_ASCII))
            symData.write(0)
        }
        val data = symData.toByteArray()

        writeMemberHeader(buf, "/", data.size, 0, 0, 0, 0)
        buf.write(data)
        if (data.size % 2 != 0) buf.write('\n'.code)
    }

    /**
     * Computes the size of the COFF second linker member data.
     *
     * Format: memberCount(4) + memberOffsets(4*N) + symbolCount(4) + indices(2*S) + strings
     */
    private fun computeCoffSecondLinkerMemberSize(symbols: List<ArchiveSymbol>, memberCount: Int): Int {
        val stringSize = symbols.sumOf { it.name.length + 1 }
        return 4 + memberCount * 4 + 4 + symbols.size * 2 + stringSize
    }

    /**
     * Writes the COFF second linker member with little-endian format.
     *
     * Format:
     *   - uint32 memberCount (LE)
     *   - uint32[memberCount] memberOffsets (LE) — file offsets to each member header
     *   - uint32 symbolCount (LE)
     *   - uint16[symbolCount] memberIndices (LE) — 1-based index into memberOffsets
     *   - null-terminated symbol name strings
     */
    private fun writeCoffSecondLinkerMember(
        buf: ByteArrayOutputStream,
        symbols: List<ArchiveSymbol>,
        memberOffsets: Map<Int, Long>,
    ) {
        val memberCount = memberOffsets.size
        val sortedMemberEntries = memberOffsets.entries.sortedBy { it.key }
        val offsetToIndex = mutableMapOf<Long, Int>() // file offset → 1-based index
        for ((idx, entry) in sortedMemberEntries.withIndex()) {
            offsetToIndex[entry.value] = idx + 1
        }

        val symData = ByteArrayOutputStream()

        // Member count + offsets (little-endian)
        val headerBuf = ByteBuffer.allocate(4 + memberCount * 4 + 4 + symbols.size * 2)
            .order(ByteOrder.LITTLE_ENDIAN)
        headerBuf.putInt(memberCount)
        for (entry in sortedMemberEntries) {
            headerBuf.putInt(entry.value.toInt())
        }

        // Symbol count + indices (little-endian, 1-based)
        headerBuf.putInt(symbols.size)
        for (sym in symbols) {
            val memberIdx = offsetToIndex[sym.memberOffset] ?: 1
            headerBuf.putShort(memberIdx.toShort())
        }

        headerBuf.flip()
        val headerBytes = ByteArray(headerBuf.remaining())
        headerBuf.get(headerBytes)
        symData.write(headerBytes)

        // Symbol name strings
        for (sym in symbols) {
            symData.write(sym.name.toByteArray(Charsets.US_ASCII))
            symData.write(0)
        }

        val data = symData.toByteArray()
        writeMemberHeader(buf, "/", data.size, 0, 0, 0, 0)
        buf.write(data)
        if (data.size % 2 != 0) buf.write('\n'.code)
    }

    private fun formatMemberName(name: String, longNameTable: LongNameTable?, memberIndex: Int): String {
        return when {
            variant == ArchiveVariant.BSD && name.length > 15 -> {
                val nameBytes = name.toByteArray(Charsets.US_ASCII)
                val padLen = if (nameBytes.size % 4 != 0) 4 - nameBytes.size % 4 else 0
                "#1/${nameBytes.size + padLen}"
            }
            (variant == ArchiveVariant.GNU || variant == ArchiveVariant.COFF) && longNameTable != null -> {
                val tableOffset = longNameTable.offsets[memberIndex]
                if (tableOffset != null) "/$tableOffset" else "$name/"
            }
            else -> "$name/"
        }
    }

    private fun writeMemberHeader(
        buf: ByteArrayOutputStream,
        name: String,
        size: Int,
        mtime: Long,
        uid: Int,
        gid: Int,
        mode: Int,
    ) {
        buf.write(padRight(name, 16))
        buf.write(padRight(mtime.toString(), 12))
        buf.write(padRight(uid.toString(), 6))
        buf.write(padRight(gid.toString(), 6))
        buf.write(padRight(if (mode != 0) mode.toString(8) else "0", 8))
        buf.write(padRight(size.toString(), 10))
        buf.write("`\n".toByteArray(Charsets.US_ASCII))
    }

    private fun padRight(str: String, width: Int): ByteArray {
        val bytes = str.toByteArray(Charsets.US_ASCII)
        val result = ByteArray(width) { ' '.code.toByte() }
        System.arraycopy(bytes, 0, result, 0, minOf(bytes.size, width))
        return result
    }
}
