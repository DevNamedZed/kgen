package org.kgen.binary.ar

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class ArchiveReaderWriterTest {

    private fun makeArchive(
        members: List<ArchiveMember>,
        symbols: List<ArchiveSymbol> = emptyList(),
    ): ByteArray {
        return ArchiveWriter().write(members, symbols)
    }

    @Test
    fun `canRead detects archive magic`() {
        val data = "!<arch>\n".toByteArray(Charsets.US_ASCII)
        assertTrue(ArchiveReader.canRead(data))
    }

    @Test
    fun `canRead rejects non-archive`() {
        assertFalse(ArchiveReader.canRead(byteArrayOf(0x7f, 0x45, 0x4c, 0x46)))
        assertFalse(ArchiveReader.canRead(byteArrayOf(0, 0, 0)))
    }

    @Test
    fun `round-trip single member`() {
        val content = "hello world".toByteArray()
        val members = listOf(ArchiveMember("test.o", data = content))
        val bytes = makeArchive(members)
        val archive = ArchiveReader.read(bytes)

        assertEquals(1, archive.members.size)
        assertEquals("test.o", archive.members[0].name)
        assertArrayEquals(content, archive.members[0].data)
    }

    @Test
    fun `round-trip multiple members`() {
        val members = listOf(
            ArchiveMember("a.o", data = byteArrayOf(1, 2, 3)),
            ArchiveMember("b.o", data = byteArrayOf(4, 5, 6, 7)),
            ArchiveMember("c.o", data = byteArrayOf(8)),
        )
        val bytes = makeArchive(members)
        val archive = ArchiveReader.read(bytes)

        assertEquals(3, archive.members.size)
        assertEquals("a.o", archive.members[0].name)
        assertEquals("b.o", archive.members[1].name)
        assertEquals("c.o", archive.members[2].name)
        assertArrayEquals(byteArrayOf(1, 2, 3), archive.members[0].data)
        assertArrayEquals(byteArrayOf(4, 5, 6, 7), archive.members[1].data)
        assertArrayEquals(byteArrayOf(8), archive.members[2].data)
    }

    @Test
    fun `round-trip with odd-sized data preserves alignment`() {
        val members = listOf(
            ArchiveMember("odd.o", data = byteArrayOf(1, 2, 3)), // 3 bytes, needs padding
            ArchiveMember("even.o", data = byteArrayOf(4, 5, 6, 7)),
        )
        val bytes = makeArchive(members)
        val archive = ArchiveReader.read(bytes)

        assertEquals(2, archive.members.size)
        assertArrayEquals(byteArrayOf(1, 2, 3), archive.members[0].data)
        assertArrayEquals(byteArrayOf(4, 5, 6, 7), archive.members[1].data)
    }

    @Test
    fun `round-trip with long filenames (GNU style)`() {
        val longName = "very_long_filename_that_exceeds_15_chars.o"
        val members = listOf(
            ArchiveMember(longName, data = byteArrayOf(0xCA.toByte(), 0xFE.toByte())),
            ArchiveMember("short.o", data = byteArrayOf(0x42)),
        )
        val bytes = makeArchive(members)
        val archive = ArchiveReader.read(bytes)

        assertEquals(2, archive.members.size)
        assertEquals(longName, archive.members[0].name)
        assertEquals("short.o", archive.members[1].name)
    }

    @Test
    fun `round-trip with symbol table`() {
        val members = listOf(
            ArchiveMember("main.o", data = ByteArray(32)),
            ArchiveMember("util.o", data = ByteArray(16)),
        )
        // Symbols reference members by index (resolved during write)
        val symbols = listOf(
            ArchiveSymbol("_main", memberOffset = 0),
            ArchiveSymbol("_helper", memberOffset = 1),
        )
        val bytes = makeArchive(members, symbols)
        val archive = ArchiveReader.read(bytes)

        assertEquals(2, archive.members.size)
        assertEquals(2, archive.symbols.size)
        assertEquals("_main", archive.symbols[0].name)
        assertEquals("_helper", archive.symbols[1].name)

        // Symbol offsets should point to actual member header positions
        val mainMember = archive.members.first { it.name == "main.o" }
        assertEquals(mainMember.fileOffset, archive.symbols[0].memberOffset)
    }

    @Test
    fun `round-trip empty archive`() {
        val bytes = makeArchive(emptyList())
        val archive = ArchiveReader.read(bytes)
        assertTrue(archive.members.isEmpty())
    }

    @Test
    fun `round-trip preserves modification time and permissions`() {
        val members = listOf(
            ArchiveMember("test.o", modificationTime = 1709726400, ownerId = 1000,
                groupId = 1000, mode = 0x1A4, data = byteArrayOf(0x42))
        )
        val bytes = makeArchive(members)
        val archive = ArchiveReader.read(bytes)

        assertEquals(1709726400L, archive.members[0].modificationTime)
        assertEquals(1000, archive.members[0].ownerId)
        assertEquals(1000, archive.members[0].groupId)
        assertEquals(0x1A4, archive.members[0].mode)
    }

    @Test
    fun `memberByName finds correct member`() {
        val archive = ArchiveFile(
            members = listOf(
                ArchiveMember("a.o", data = byteArrayOf(1)),
                ArchiveMember("b.o", data = byteArrayOf(2)),
            )
        )
        val found = archive.memberByName("b.o")
        assertNotNull(found)
        assertArrayEquals(byteArrayOf(2), found!!.data)
        assertNull(archive.memberByName("c.o"))
    }

    @Test
    fun `round-trip with large member data`() {
        val largeData = ByteArray(65536) { (it % 256).toByte() }
        val members = listOf(ArchiveMember("large.o", data = largeData))
        val bytes = makeArchive(members)
        val archive = ArchiveReader.read(bytes)

        assertEquals(1, archive.members.size)
        assertArrayEquals(largeData, archive.members[0].data)
    }

    @Test
    fun `round-trip with empty member data`() {
        val members = listOf(ArchiveMember("empty.o", data = ByteArray(0)))
        val bytes = makeArchive(members)
        val archive = ArchiveReader.read(bytes)

        assertEquals(1, archive.members.size)
        assertEquals(0, archive.members[0].data.size)
    }

    @Test
    fun `BSD variant round-trip with long names`() {
        val longName = "a_very_long_bsd_style_name.o"
        val members = listOf(
            ArchiveMember(longName, data = byteArrayOf(0x55, 0xAA.toByte())),
        )
        val bytes = ArchiveWriter(variant = ArchiveVariant.BSD).write(members)
        val archive = ArchiveReader.read(bytes)

        assertEquals(1, archive.members.size)
        assertEquals(longName, archive.members[0].name)
        assertArrayEquals(byteArrayOf(0x55, 0xAA.toByte()), archive.members[0].data)
    }

    @Test
    fun `hand-crafted minimal archive parses correctly`() {
        val raw = buildString {
            append("!<arch>\n")
            // Member header (60 bytes)
            append("test.o/         ")  // name (16)
            append("0           ")      // mtime (12)
            append("0     ")            // uid (6)
            append("0     ")            // gid (6)
            append("100644  ")          // mode (8)
            append("4         ")        // size (10)
            append("`\n")               // end marker (2)
            // Data (4 bytes)
            append("\u0001\u0002\u0003\u0004")
        }
        val archive = ArchiveReader.read(raw.toByteArray(Charsets.US_ASCII))

        assertEquals(1, archive.members.size)
        assertEquals("test.o", archive.members[0].name)
        assertEquals(4, archive.members[0].data.size)
    }

    @Test
    fun `membersContainingSymbol resolves through symbol table`() {
        val members = listOf(
            ArchiveMember("main.o", data = ByteArray(8), fileOffset = 100),
            ArchiveMember("util.o", data = ByteArray(8), fileOffset = 200),
        )
        val symbols = listOf(
            ArchiveSymbol("_main", memberOffset = 100),
            ArchiveSymbol("_helper", memberOffset = 200),
            ArchiveSymbol("_helper2", memberOffset = 200),
        )
        val archive = ArchiveFile(members = members, symbols = symbols)

        val mainMembers = archive.membersContainingSymbol("_main")
        assertEquals(1, mainMembers.size)
        assertEquals("main.o", mainMembers[0].name)

        val helperMembers = archive.membersContainingSymbol("_helper")
        assertEquals(1, helperMembers.size)
        assertEquals("util.o", helperMembers[0].name)

        val missing = archive.membersContainingSymbol("_missing")
        assertTrue(missing.isEmpty())
    }
}
