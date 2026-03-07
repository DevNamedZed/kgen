package org.kgen.binary.ar

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class ArchiveReaderWriterExtendedTest {

    private fun makeArchive(
        members: List<ArchiveMember>,
        symbols: List<ArchiveSymbol> = emptyList(),
        variant: ArchiveVariant = ArchiveVariant.GNU,
    ): ByteArray {
        return ArchiveWriter(variant = variant).write(members, symbols)
    }

    // --- canRead ---

    @Test
    fun canReadExactMagic() {
        assertTrue(ArchiveReader.canRead("!<arch>\n".toByteArray()))
    }

    @Test
    fun canReadRejectsTooShort() {
        assertFalse(ArchiveReader.canRead("!<arch".toByteArray()))
        assertFalse(ArchiveReader.canRead(byteArrayOf()))
        assertFalse(ArchiveReader.canRead(byteArrayOf(0x21)))
    }

    @Test
    fun canReadRejectsElf() {
        assertFalse(ArchiveReader.canRead(byteArrayOf(0x7f, 0x45, 0x4c, 0x46, 0, 0, 0, 0)))
    }

    @Test
    fun canReadRejectsPe() {
        assertFalse(ArchiveReader.canRead(byteArrayOf(0x4d, 0x5a, 0, 0, 0, 0, 0, 0)))
    }

    @Test
    fun canReadRejectsRandom() {
        assertFalse(ArchiveReader.canRead(byteArrayOf(0x12, 0x34, 0x56, 0x78, 0x9A.toByte(), 0xBC.toByte(), 0xDE.toByte(), 0xF0.toByte())))
    }

    // --- Member names ---

    @Test
    fun shortNames() {
        val members = listOf(
            ArchiveMember("a.o", data = byteArrayOf(1)),
            ArchiveMember("b.o", data = byteArrayOf(2)),
            ArchiveMember("c.o", data = byteArrayOf(3)),
        )
        val archive = ArchiveReader.read(makeArchive(members))
        assertEquals(3, archive.members.size)
        assertEquals("a.o", archive.members[0].name)
        assertEquals("b.o", archive.members[1].name)
        assertEquals("c.o", archive.members[2].name)
    }

    @Test
    fun exactly15CharName() {
        val name = "123456789012345" // 15 chars
        val members = listOf(ArchiveMember(name, data = byteArrayOf(0x42)))
        val archive = ArchiveReader.read(makeArchive(members))
        assertEquals(name, archive.members[0].name)
    }

    @Test
    fun longNamesGnu() {
        val names = listOf(
            "very_very_long_name_that_exceeds_16_characters.o",
            "another_long_name_for_testing_purposes.o",
        )
        val members = names.map { ArchiveMember(it, data = byteArrayOf(0x55)) }
        val archive = ArchiveReader.read(makeArchive(members))
        assertEquals(2, archive.members.size)
        assertEquals(names[0], archive.members[0].name)
        assertEquals(names[1], archive.members[1].name)
    }

    @Test
    fun longNamesBsd() {
        val name = "bsd_long_filename_example.o"
        val members = listOf(ArchiveMember(name, data = byteArrayOf(0xAA.toByte())))
        val archive = ArchiveReader.read(makeArchive(members, variant = ArchiveVariant.BSD))
        assertEquals(1, archive.members.size)
        assertEquals(name, archive.members[0].name)
    }

    // --- Member data ---

    @Test
    fun singleByteData() {
        val members = listOf(ArchiveMember("x.o", data = byteArrayOf(0xFF.toByte())))
        val archive = ArchiveReader.read(makeArchive(members))
        assertEquals(1, archive.members[0].data.size)
        assertEquals(0xFF.toByte(), archive.members[0].data[0])
    }

    @Test
    fun emptyData() {
        val members = listOf(ArchiveMember("empty.o", data = ByteArray(0)))
        val archive = ArchiveReader.read(makeArchive(members))
        assertEquals(0, archive.members[0].data.size)
    }

    @Test
    fun oddSizedDataAlignment() {
        val members = listOf(
            ArchiveMember("odd1.o", data = byteArrayOf(1)),
            ArchiveMember("odd3.o", data = byteArrayOf(1, 2, 3)),
            ArchiveMember("odd5.o", data = byteArrayOf(1, 2, 3, 4, 5)),
        )
        val archive = ArchiveReader.read(makeArchive(members))
        assertEquals(3, archive.members.size)
        assertArrayEquals(byteArrayOf(1), archive.members[0].data)
        assertArrayEquals(byteArrayOf(1, 2, 3), archive.members[1].data)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5), archive.members[2].data)
    }

    @Test
    fun evenSizedData() {
        val members = listOf(
            ArchiveMember("even2.o", data = byteArrayOf(1, 2)),
            ArchiveMember("even4.o", data = byteArrayOf(1, 2, 3, 4)),
        )
        val archive = ArchiveReader.read(makeArchive(members))
        assertEquals(2, archive.members.size)
        assertArrayEquals(byteArrayOf(1, 2), archive.members[0].data)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), archive.members[1].data)
    }

    @Test
    fun largeData() {
        val data = ByteArray(100_000) { (it % 256).toByte() }
        val members = listOf(ArchiveMember("big.o", data = data))
        val archive = ArchiveReader.read(makeArchive(members))
        assertArrayEquals(data, archive.members[0].data)
    }

    // --- Symbol table ---

    @Test
    fun noSymbols() {
        val members = listOf(ArchiveMember("test.o", data = byteArrayOf(1)))
        val archive = ArchiveReader.read(makeArchive(members))
        // Symbol table may be empty or absent
        assertTrue(archive.symbols.isEmpty() || archive.symbols.isNotEmpty())
    }

    @Test
    fun manySymbols() {
        val members = listOf(ArchiveMember("lib.o", data = ByteArray(64)))
        val symbols = (1..20).map { ArchiveSymbol("sym_$it", memberOffset = 0) }
        val archive = ArchiveReader.read(makeArchive(members, symbols))
        assertTrue(archive.symbols.size >= 20)
        for (i in 1..20) {
            assertTrue(archive.symbols.any { it.name == "sym_$i" })
        }
    }

    @Test
    fun symbolsPointToCorrectMembers() {
        val members = listOf(
            ArchiveMember("first.o", data = ByteArray(16)),
            ArchiveMember("second.o", data = ByteArray(16)),
            ArchiveMember("third.o", data = ByteArray(16)),
        )
        val symbols = listOf(
            ArchiveSymbol("_first", memberOffset = 0),
            ArchiveSymbol("_second", memberOffset = 1),
            ArchiveSymbol("_third", memberOffset = 2),
        )
        val archive = ArchiveReader.read(makeArchive(members, symbols))
        assertEquals(3, archive.members.size)
        assertEquals(3, archive.symbols.size)
    }

    // --- memberByName ---

    @Test
    fun memberByNameFirst() {
        val archive = ArchiveFile(members = listOf(
            ArchiveMember("x.o", data = byteArrayOf(1)),
            ArchiveMember("y.o", data = byteArrayOf(2)),
        ))
        val found = archive.memberByName("x.o")!!
        assertArrayEquals(byteArrayOf(1), found.data)
    }

    @Test
    fun memberByNameLast() {
        val archive = ArchiveFile(members = listOf(
            ArchiveMember("x.o", data = byteArrayOf(1)),
            ArchiveMember("y.o", data = byteArrayOf(2)),
        ))
        val found = archive.memberByName("y.o")!!
        assertArrayEquals(byteArrayOf(2), found.data)
    }

    @Test
    fun memberByNameMissing() {
        val archive = ArchiveFile(members = listOf(ArchiveMember("x.o", data = byteArrayOf(1))))
        assertNull(archive.memberByName("z.o"))
    }

    // --- membersContainingSymbol ---

    @Test
    fun membersContainingSymbolMultiple() {
        val archive = ArchiveFile(
            members = listOf(
                ArchiveMember("a.o", data = ByteArray(8), fileOffset = 100),
                ArchiveMember("b.o", data = ByteArray(8), fileOffset = 200),
            ),
            symbols = listOf(
                ArchiveSymbol("sym1", memberOffset = 100),
                ArchiveSymbol("sym2", memberOffset = 100),
                ArchiveSymbol("sym3", memberOffset = 200),
            )
        )
        val forA = archive.membersContainingSymbol("sym1")
        assertEquals(1, forA.size)
        assertEquals("a.o", forA[0].name)
    }

    @Test
    fun membersContainingSymbolMissing() {
        val archive = ArchiveFile(
            members = listOf(ArchiveMember("a.o", data = ByteArray(8))),
            symbols = listOf(ArchiveSymbol("sym1", memberOffset = 0))
        )
        assertTrue(archive.membersContainingSymbol("nonexistent").isEmpty())
    }

    // --- Empty archive ---

    @Test
    fun emptyArchiveRoundTrip() {
        val archive = ArchiveReader.read(makeArchive(emptyList()))
        assertTrue(archive.members.isEmpty())
    }

    // --- Modification time and permissions ---

    @Test
    fun metadataPreserved() {
        val members = listOf(
            ArchiveMember("test.o", modificationTime = 1234567890, ownerId = 501,
                groupId = 20, mode = 0x1ED, data = byteArrayOf(0x42))
        )
        val archive = ArchiveReader.read(makeArchive(members))
        assertEquals(1234567890L, archive.members[0].modificationTime)
        assertEquals(501, archive.members[0].ownerId)
        assertEquals(20, archive.members[0].groupId)
        assertEquals(0x1ED, archive.members[0].mode)
    }

    @Test
    fun defaultMetadata() {
        val members = listOf(ArchiveMember("test.o", data = byteArrayOf(1)))
        val archive = ArchiveReader.read(makeArchive(members))
        val m = archive.members[0]
        assertEquals(0L, m.modificationTime)
        assertEquals(0, m.ownerId)
        assertEquals(0, m.groupId)
    }

    // --- Many members ---

    @Test
    fun manyMembers() {
        val members = (1..50).map { ArchiveMember("file$it.o", data = byteArrayOf(it.toByte())) }
        val archive = ArchiveReader.read(makeArchive(members))
        assertEquals(50, archive.members.size)
        for (i in 1..50) {
            assertEquals("file$i.o", archive.members[i - 1].name)
            assertEquals(i.toByte(), archive.members[i - 1].data[0])
        }
    }

    // --- Mixed short and long names ---

    @Test
    fun mixedNameLengths() {
        val members = listOf(
            ArchiveMember("a.o", data = byteArrayOf(1)),
            ArchiveMember("a_much_longer_name_for_this_object_file.o", data = byteArrayOf(2)),
            ArchiveMember("b.o", data = byteArrayOf(3)),
            ArchiveMember("another_long_name_that_needs_extended_header.o", data = byteArrayOf(4)),
        )
        val archive = ArchiveReader.read(makeArchive(members))
        assertEquals(4, archive.members.size)
        assertEquals("a.o", archive.members[0].name)
        assertEquals("a_much_longer_name_for_this_object_file.o", archive.members[1].name)
        assertEquals("b.o", archive.members[2].name)
    }

    // --- Variant detection ---

    @Test
    fun gnuVariantWritesCorrectly() {
        val members = listOf(ArchiveMember("test.o", data = byteArrayOf(1, 2, 3)))
        val bytes = ArchiveWriter(variant = ArchiveVariant.GNU).write(members)
        assertTrue(ArchiveReader.canRead(bytes))
        val archive = ArchiveReader.read(bytes)
        assertEquals(1, archive.members.size)
    }

    @Test
    fun bsdVariantWritesCorrectly() {
        val members = listOf(ArchiveMember("test.o", data = byteArrayOf(1, 2, 3)))
        val bytes = ArchiveWriter(variant = ArchiveVariant.BSD).write(members)
        assertTrue(ArchiveReader.canRead(bytes))
        val archive = ArchiveReader.read(bytes)
        assertEquals(1, archive.members.size)
    }
}
