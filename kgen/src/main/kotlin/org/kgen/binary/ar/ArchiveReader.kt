package org.kgen.binary.ar

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Reads Unix archive (.a) and Windows static library (.lib) files.
 *
 * Supports GNU, BSD, and COFF archive variants with automatic detection.
 */
object ArchiveReader {

    private const val MAGIC = "!<arch>\n"
    private const val HEADER_SIZE = 60

    @JvmStatic
    fun canRead(data: ByteArray): Boolean {
        if (data.size < MAGIC.length) return false
        return String(data, 0, MAGIC.length, Charsets.US_ASCII) == MAGIC
    }

    @JvmStatic
    fun read(data: ByteArray): ArchiveFile {
        if (!canRead(data)) throw IllegalArgumentException("Not a valid archive file")
        return ArchiveParser(data).parse()
    }

    private class ArchiveParser(private val data: ByteArray) {
        private var offset = MAGIC.length
        private val longNames = mutableMapOf<Int, String>() // offset → name (GNU style)
        private var variant = ArchiveVariant.GNU

        fun parse(): ArchiveFile {
            val rawMembers = mutableListOf<RawMember>()

            while (offset + HEADER_SIZE <= data.size) {
                val member = readMemberHeader() ?: break
                rawMembers.add(member)
            }

            detectVariant(rawMembers)
            parseSpecialMembers(rawMembers)

            val symbols = mutableListOf<ArchiveSymbol>()
            val members = mutableListOf<ArchiveMember>()

            var isFirstSlash = true
            for (raw in rawMembers) {
                when {
                    raw.headerName == "/" && isFirstSlash -> {
                        isFirstSlash = false
                        if (variant == ArchiveVariant.COFF) {
                            // First COFF linker member: big-endian count + offsets + strings
                            // Parse as fallback; prefer second member if present
                            symbols.addAll(parseCoffFirstLinkerMember(raw))
                        } else {
                            symbols.addAll(parseGnuSymtab(raw))
                        }
                    }
                    raw.headerName == "/" && variant == ArchiveVariant.COFF -> {
                        // Second COFF linker member: little-endian, more efficient format
                        // This is the authoritative symbol table for COFF .lib files
                        val coffSymbols = parseCoffSecondLinkerMember(raw)
                        if (coffSymbols.isNotEmpty()) {
                            symbols.clear()
                            symbols.addAll(coffSymbols)
                        }
                    }
                    raw.headerName == "//" -> {
                        // Long name table — already parsed
                    }
                    raw.headerName.startsWith("__.SYMDEF") -> {
                        symbols.addAll(parseBsdSymtab(raw))
                    }
                    else -> {
                        val name = resolveName(raw)
                        val memberData = extractData(raw)
                        members.add(ArchiveMember(
                            name = name,
                            modificationTime = raw.mtime,
                            ownerId = raw.uid,
                            groupId = raw.gid,
                            mode = raw.mode,
                            data = memberData,
                            fileOffset = raw.headerOffset,
                        ))
                    }
                }
            }

            return ArchiveFile(members = members, symbols = symbols, variant = variant)
        }

        private data class RawMember(
            val headerName: String,
            val mtime: Long,
            val uid: Int,
            val gid: Int,
            val mode: Int,
            val size: Int,
            val headerOffset: Long,
            val dataOffset: Int,
        )

        private fun readMemberHeader(): RawMember? {
            if (offset + HEADER_SIZE > data.size) return null

            val headerOffset = offset.toLong()
            val name = readAscii(offset, 16).trimEnd()
            val mtime = readAscii(offset + 16, 12).trim().toLongOrNull() ?: 0
            val uid = readAscii(offset + 28, 6).trim().toIntOrNull() ?: 0
            val gid = readAscii(offset + 34, 6).trim().toIntOrNull() ?: 0
            val mode = readAscii(offset + 40, 8).trim().toIntOrNull(8) ?: 0
            val size = readAscii(offset + 48, 10).trim().toIntOrNull() ?: 0

            val endMarker = readAscii(offset + 58, 2)
            if (endMarker != "`\n") return null

            val dataOffset = offset + HEADER_SIZE
            offset = dataOffset + size
            if (offset % 2 != 0) offset++ // pad to 2-byte alignment

            return RawMember(
                headerName = name,
                mtime = mtime,
                uid = uid,
                gid = gid,
                mode = mode,
                size = size,
                headerOffset = headerOffset,
                dataOffset = dataOffset,
            )
        }

        private fun detectVariant(members: List<RawMember>) {
            variant = when {
                members.any { it.headerName.startsWith("__.SYMDEF") } -> ArchiveVariant.BSD
                members.any { it.headerName.startsWith("#1/") } -> ArchiveVariant.BSD
                members.count { it.headerName == "/" } >= 2 -> ArchiveVariant.COFF
                else -> ArchiveVariant.GNU
            }
        }

        private fun parseSpecialMembers(members: List<RawMember>) {
            val longNameMember = members.firstOrNull { it.headerName == "//" }
            if (longNameMember != null) {
                parseLongNameTable(longNameMember)
            }
        }

        private fun parseLongNameTable(member: RawMember) {
            val tableData = String(data, member.dataOffset,
                minOf(member.size, data.size - member.dataOffset), Charsets.US_ASCII)
            var pos = 0
            var nameStart = 0
            while (pos < tableData.length) {
                if (tableData[pos] == '/' && pos + 1 < tableData.length && tableData[pos + 1] == '\n') {
                    longNames[nameStart] = tableData.substring(nameStart, pos)
                    pos += 2
                    nameStart = pos
                } else if (tableData[pos] == '\n') {
                    if (pos > nameStart) {
                        longNames[nameStart] = tableData.substring(nameStart, pos)
                    }
                    pos++
                    nameStart = pos
                } else {
                    pos++
                }
            }
        }

        private fun resolveName(member: RawMember): String {
            val raw = member.headerName
            return when {
                // GNU long name: "/N" where N is offset into long name table
                raw.startsWith("/") && raw.length > 1 && raw[1].isDigit() -> {
                    val idx = raw.substring(1).toIntOrNull() ?: 0
                    longNames[idx] ?: raw
                }
                // BSD long name: "#1/N" where N is length of name in data
                raw.startsWith("#1/") -> {
                    val nameLen = raw.substring(3).toIntOrNull() ?: 0
                    if (nameLen > 0 && member.dataOffset + nameLen <= data.size) {
                        String(data, member.dataOffset, nameLen, Charsets.US_ASCII).trimEnd('\u0000')
                    } else raw
                }
                // GNU short name: "name/" — strip trailing slash
                raw.endsWith("/") -> raw.dropLast(1)
                else -> raw
            }
        }

        private fun extractData(member: RawMember): ByteArray {
            val raw = member.headerName
            var dataStart = member.dataOffset
            var dataSize = member.size

            // BSD: long name is embedded at start of data
            if (raw.startsWith("#1/")) {
                val nameLen = raw.substring(3).toIntOrNull() ?: 0
                dataStart += nameLen
                dataSize -= nameLen
            }

            if (dataStart + dataSize > data.size) {
                dataSize = maxOf(0, data.size - dataStart)
            }
            return data.copyOfRange(dataStart, dataStart + dataSize)
        }

        private fun parseGnuSymtab(member: RawMember): List<ArchiveSymbol> {
            if (member.size < 4) return emptyList()
            val off = member.dataOffset
            val buf = ByteBuffer.wrap(data, off, member.size).order(ByteOrder.BIG_ENDIAN)
            val count = buf.getInt()
            if (member.size < 4 + count * 4) return emptyList()

            val offsets = IntArray(count) { buf.getInt() }
            val stringStart = off + 4 + count * 4
            val symbols = mutableListOf<ArchiveSymbol>()

            var strPos = stringStart
            for (i in 0 until count) {
                val name = readNullTerminated(strPos)
                symbols.add(ArchiveSymbol(name = name, memberOffset = offsets[i].toLong()))
                strPos += name.length + 1
            }
            return symbols
        }

        /**
         * Parses the COFF first linker member ("/" — first occurrence).
         *
         * Format: big-endian symbol count, big-endian offsets, null-terminated strings.
         * Identical layout to GNU symtab but semantically part of COFF.
         */
        private fun parseCoffFirstLinkerMember(member: RawMember): List<ArchiveSymbol> {
            if (member.size < 4) return emptyList()
            val off = member.dataOffset
            val buf = ByteBuffer.wrap(data, off, member.size).order(ByteOrder.BIG_ENDIAN)
            val count = buf.getInt()
            if (count < 0 || member.size < 4 + count * 4) return emptyList()

            val offsets = IntArray(count) { buf.getInt() }
            val stringStart = off + 4 + count * 4
            val symbols = mutableListOf<ArchiveSymbol>()

            var strPos = stringStart
            for (i in 0 until count) {
                val name = readNullTerminated(strPos)
                symbols.add(ArchiveSymbol(name = name, memberOffset = offsets[i].toLong()))
                strPos += name.length + 1
            }
            return symbols
        }

        /**
         * Parses the COFF second linker member ("/" — second occurrence).
         *
         * Format (all little-endian):
         *   - uint32 memberCount
         *   - uint32[memberCount] memberOffsets (file offsets to each archive member header)
         *   - uint32 symbolCount
         *   - uint16[symbolCount] memberIndices (1-based index into memberOffsets)
         *   - null-terminated symbol name strings
         */
        private fun parseCoffSecondLinkerMember(member: RawMember): List<ArchiveSymbol> {
            if (member.size < 4) return emptyList()
            val off = member.dataOffset
            val buf = ByteBuffer.wrap(data, off, member.size).order(ByteOrder.LITTLE_ENDIAN)

            val memberCount = buf.getInt()
            if (memberCount < 0 || member.size < 4 + memberCount * 4 + 4) return emptyList()

            val memberOffsets = IntArray(memberCount) { buf.getInt() }

            val symbolCount = buf.getInt()
            if (symbolCount < 0) return emptyList()
            val minSize = 4 + memberCount * 4 + 4 + symbolCount * 2
            if (member.size < minSize) return emptyList()

            val memberIndices = IntArray(symbolCount) {
                buf.getShort().toInt() and 0xFFFF // unsigned short, 1-based
            }

            val stringStart = off + 4 + memberCount * 4 + 4 + symbolCount * 2
            val symbols = mutableListOf<ArchiveSymbol>()

            var strPos = stringStart
            for (i in 0 until symbolCount) {
                val name = readNullTerminated(strPos)
                val memberIdx = memberIndices[i] - 1 // convert 1-based to 0-based
                val memberOffset = if (memberIdx in memberOffsets.indices) {
                    memberOffsets[memberIdx].toLong()
                } else {
                    0L
                }
                symbols.add(ArchiveSymbol(name = name, memberOffset = memberOffset))
                strPos += name.length + 1
            }
            return symbols
        }

        private fun parseBsdSymtab(member: RawMember): List<ArchiveSymbol> {
            if (member.size < 4) return emptyList()
            val off = member.dataOffset
            val buf = ByteBuffer.wrap(data, off, member.size).order(ByteOrder.LITTLE_ENDIAN)

            val ranLibSize = buf.getInt()
            val ranLibCount = ranLibSize / 8
            if (member.size < 4 + ranLibSize + 4) return emptyList()

            data class RanLib(val stringOffset: Int, val memberOffset: Int)
            val entries = (0 until ranLibCount).map {
                RanLib(buf.getInt(), buf.getInt())
            }

            val stringTableSize = buf.getInt()
            val stringTableStart = off + 4 + ranLibSize + 4

            return entries.map { entry ->
                val name = readNullTerminated(stringTableStart + entry.stringOffset)
                ArchiveSymbol(name = name, memberOffset = entry.memberOffset.toLong())
            }
        }

        private fun readAscii(off: Int, len: Int): String {
            val end = minOf(off + len, data.size)
            return String(data, off, end - off, Charsets.US_ASCII)
        }

        private fun readNullTerminated(off: Int): String {
            var end = off
            while (end < data.size && data[end] != 0.toByte()) end++
            return String(data, off, end - off, Charsets.US_ASCII)
        }
    }
}
