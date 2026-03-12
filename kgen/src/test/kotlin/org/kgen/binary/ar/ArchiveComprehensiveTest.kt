package org.kgen.binary.ar

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested

class ArchiveComprehensiveTest {

    private fun makeArchive(
        members: List<ArchiveMember>,
        symbols: List<ArchiveSymbol> = emptyList(),
        variant: ArchiveVariant = ArchiveVariant.GNU,
    ): ByteArray {
        return ArchiveWriter(variant).write(members, symbols)
    }

    private fun roundTrip(
        members: List<ArchiveMember>,
        symbols: List<ArchiveSymbol> = emptyList(),
        variant: ArchiveVariant = ArchiveVariant.GNU,
    ): ArchiveFile {
        val bytes = makeArchive(members, symbols, variant)
        return ArchiveReader.read(bytes)
    }

    @Nested
    inner class CanRead {

        @Test
        fun `detects valid archive magic`() {
            val data = "!<arch>\n".toByteArray(Charsets.US_ASCII)
            assertTrue(ArchiveReader.canRead(data))
        }

        @Test
        fun `detects valid archive magic with trailing data`() {
            val data = "!<arch>\nSOME_DATA_HERE".toByteArray(Charsets.US_ASCII)
            assertTrue(ArchiveReader.canRead(data))
        }

        @Test
        fun `rejects ELF magic`() {
            assertFalse(ArchiveReader.canRead(byteArrayOf(0x7f, 0x45, 0x4c, 0x46)))
        }

        @Test
        fun `rejects PE magic`() {
            assertFalse(ArchiveReader.canRead(byteArrayOf(0x4d, 0x5a, 0x90.toByte(), 0x00)))
        }

        @Test
        fun `rejects empty bytes`() {
            assertFalse(ArchiveReader.canRead(byteArrayOf()))
        }

        @Test
        fun `rejects too-short bytes`() {
            assertFalse(ArchiveReader.canRead(byteArrayOf(0x21, 0x3c)))
        }

        @Test
        fun `rejects partial magic`() {
            assertFalse(ArchiveReader.canRead("!<arch>".toByteArray(Charsets.US_ASCII)))
        }

        @Test
        fun `rejects corrupted magic`() {
            assertFalse(ArchiveReader.canRead("!<ARCH>\n".toByteArray(Charsets.US_ASCII)))
        }

        @Test
        fun `rejects all zeros`() {
            assertFalse(ArchiveReader.canRead(ByteArray(64)))
        }

        @Test
        fun `rejects random bytes`() {
            assertFalse(ArchiveReader.canRead(ByteArray(64) { (it * 37).toByte() }))
        }
    }

    @Nested
    inner class EmptyArchives {

        @Test
        fun `empty archive round-trips`() {
            val archive = roundTrip(emptyList())
            assertTrue(archive.members.isEmpty())
        }

        @Test
        fun `empty archive has no symbols`() {
            val archive = roundTrip(emptyList())
            assertTrue(archive.symbols.isEmpty())
        }

        @Test
        fun `empty archive bytes start with magic`() {
            val bytes = makeArchive(emptyList())
            assertTrue(ArchiveReader.canRead(bytes))
            assertEquals("!<arch>\n", String(bytes, 0, 8, Charsets.US_ASCII))
        }

        @Test
        fun `empty archive has minimal size`() {
            val bytes = makeArchive(emptyList())
            assertEquals(8, bytes.size)
        }

        @Test
        fun `empty archive memberByName returns null`() {
            val archive = roundTrip(emptyList())
            assertNull(archive.memberByName("anything"))
        }
    }

    @Nested
    inner class SingleMember {

        @Test
        fun `single member with short name`() {
            val archive = roundTrip(listOf(ArchiveMember("a.o", data = byteArrayOf(1, 2, 3))))
            assertEquals(1, archive.members.size)
            assertEquals("a.o", archive.members[0].name)
            assertArrayEquals(byteArrayOf(1, 2, 3), archive.members[0].data)
        }

        @Test
        fun `single member with exactly 15 char name`() {
            val name = "abcdefghij.abcd"
            assertEquals(15, name.length)
            val archive = roundTrip(listOf(ArchiveMember(name, data = byteArrayOf(42))))
            assertEquals(name, archive.members[0].name)
        }

        @Test
        fun `single member with one byte data`() {
            val archive = roundTrip(listOf(ArchiveMember("x.o", data = byteArrayOf(0xFF.toByte()))))
            assertEquals(1, archive.members[0].data.size)
            assertEquals(0xFF.toByte(), archive.members[0].data[0])
        }

        @Test
        fun `single member with empty data`() {
            val archive = roundTrip(listOf(ArchiveMember("empty.o", data = ByteArray(0))))
            assertEquals(1, archive.members.size)
            assertEquals(0, archive.members[0].data.size)
        }

        @Test
        fun `single member preserves binary data`() {
            val data = ByteArray(256) { it.toByte() }
            val archive = roundTrip(listOf(ArchiveMember("all_bytes.o", data = data)))
            assertArrayEquals(data, archive.members[0].data)
        }
    }

    @Nested
    inner class MultipleMembers {

        @Test
        fun `two members round-trip`() {
            val archive = roundTrip(listOf(
                ArchiveMember("a.o", data = byteArrayOf(1, 2, 3)),
                ArchiveMember("b.o", data = byteArrayOf(4, 5, 6, 7)),
            ))
            assertEquals(2, archive.members.size)
            assertEquals("a.o", archive.members[0].name)
            assertEquals("b.o", archive.members[1].name)
            assertArrayEquals(byteArrayOf(1, 2, 3), archive.members[0].data)
            assertArrayEquals(byteArrayOf(4, 5, 6, 7), archive.members[1].data)
        }

        @Test
        fun `three members round-trip preserving order`() {
            val archive = roundTrip(listOf(
                ArchiveMember("first.o", data = byteArrayOf(0x10)),
                ArchiveMember("second.o", data = byteArrayOf(0x20)),
                ArchiveMember("third.o", data = byteArrayOf(0x30)),
            ))
            assertEquals(3, archive.members.size)
            assertEquals("first.o", archive.members[0].name)
            assertEquals("second.o", archive.members[1].name)
            assertEquals("third.o", archive.members[2].name)
        }

        @Test
        fun `five members round-trip`() {
            val members = (1..5).map { i ->
                ArchiveMember("file$i.o", data = ByteArray(i * 10) { (i * 10 + it).toByte() })
            }
            val archive = roundTrip(members)
            assertEquals(5, archive.members.size)
            for (i in 0 until 5) {
                assertEquals("file${i + 1}.o", archive.members[i].name)
                assertEquals((i + 1) * 10, archive.members[i].data.size)
            }
        }

        @Test
        fun `ten members round-trip`() {
            val members = (1..10).map { i ->
                ArchiveMember("obj$i.o", data = ByteArray(i) { it.toByte() })
            }
            val archive = roundTrip(members)
            assertEquals(10, archive.members.size)
        }

        @Test
        fun `members with identical names`() {
            val archive = roundTrip(listOf(
                ArchiveMember("dup.o", data = byteArrayOf(1)),
                ArchiveMember("dup.o", data = byteArrayOf(2)),
            ))
            assertEquals(2, archive.members.size)
            assertArrayEquals(byteArrayOf(1), archive.members[0].data)
            assertArrayEquals(byteArrayOf(2), archive.members[1].data)
        }

        @Test
        fun `members with mix of empty and non-empty data`() {
            val archive = roundTrip(listOf(
                ArchiveMember("empty1.o", data = ByteArray(0)),
                ArchiveMember("full.o", data = byteArrayOf(1, 2, 3)),
                ArchiveMember("empty2.o", data = ByteArray(0)),
                ArchiveMember("full2.o", data = byteArrayOf(4)),
            ))
            assertEquals(4, archive.members.size)
            assertEquals(0, archive.members[0].data.size)
            assertEquals(3, archive.members[1].data.size)
            assertEquals(0, archive.members[2].data.size)
            assertEquals(1, archive.members[3].data.size)
        }
    }

    @Nested
    inner class OddSizedDataAlignment {

        @Test
        fun `odd-sized first member followed by second`() {
            val archive = roundTrip(listOf(
                ArchiveMember("odd.o", data = byteArrayOf(1, 2, 3)),
                ArchiveMember("even.o", data = byteArrayOf(4, 5, 6, 7)),
            ))
            assertArrayEquals(byteArrayOf(1, 2, 3), archive.members[0].data)
            assertArrayEquals(byteArrayOf(4, 5, 6, 7), archive.members[1].data)
        }

        @Test
        fun `single byte members`() {
            val archive = roundTrip(listOf(
                ArchiveMember("a.o", data = byteArrayOf(1)),
                ArchiveMember("b.o", data = byteArrayOf(2)),
                ArchiveMember("c.o", data = byteArrayOf(3)),
            ))
            assertEquals(3, archive.members.size)
            assertArrayEquals(byteArrayOf(1), archive.members[0].data)
            assertArrayEquals(byteArrayOf(2), archive.members[1].data)
            assertArrayEquals(byteArrayOf(3), archive.members[2].data)
        }

        @Test
        fun `alternating odd and even sized members`() {
            val archive = roundTrip(listOf(
                ArchiveMember("m1.o", data = ByteArray(1) { 0x10 }),
                ArchiveMember("m2.o", data = ByteArray(2) { 0x20 }),
                ArchiveMember("m3.o", data = ByteArray(3) { 0x30 }),
                ArchiveMember("m4.o", data = ByteArray(4) { 0x40 }),
                ArchiveMember("m5.o", data = ByteArray(5) { 0x50 }),
            ))
            assertEquals(5, archive.members.size)
            for (i in 0 until 5) {
                assertEquals(i + 1, archive.members[i].data.size)
            }
        }

        @Test
        fun `exact two byte alignment in archive bytes`() {
            val bytes = makeArchive(listOf(
                ArchiveMember("odd.o", data = byteArrayOf(1, 2, 3)),
            ))
            // Archive: 8 (magic) + 60 (header) + 3 (data) + 1 (padding) = 72
            assertEquals(0, bytes.size % 2)
        }
    }

    @Nested
    inner class LongNames {

        @Test
        fun `GNU long name exceeding 15 chars`() {
            val longName = "very_long_filename_that_exceeds_15_chars.o"
            val archive = roundTrip(listOf(ArchiveMember(longName, data = byteArrayOf(0xCA.toByte()))))
            assertEquals(longName, archive.members[0].name)
        }

        @Test
        fun `GNU long name exactly 16 chars`() {
            val name = "1234567890abcdef"
            assertEquals(16, name.length)
            val archive = roundTrip(listOf(ArchiveMember(name, data = byteArrayOf(42))))
            assertEquals(name, archive.members[0].name)
        }

        @Test
        fun `GNU multiple long names`() {
            val names = listOf(
                "very_long_name_number_one.o",
                "another_quite_long_name_here.o",
                "yet_another_extremely_long_filename.o",
            )
            val members = names.map { ArchiveMember(it, data = byteArrayOf(1)) }
            val archive = roundTrip(members)
            assertEquals(3, archive.members.size)
            for (i in names.indices) {
                assertEquals(names[i], archive.members[i].name)
            }
        }

        @Test
        fun `GNU mix of short and long names`() {
            val archive = roundTrip(listOf(
                ArchiveMember("short.o", data = byteArrayOf(1)),
                ArchiveMember("a_very_long_filename_here.o", data = byteArrayOf(2)),
                ArchiveMember("tiny.o", data = byteArrayOf(3)),
                ArchiveMember("another_long_name_exceeding_limit.o", data = byteArrayOf(4)),
            ))
            assertEquals(4, archive.members.size)
            assertEquals("short.o", archive.members[0].name)
            assertEquals("a_very_long_filename_here.o", archive.members[1].name)
            assertEquals("tiny.o", archive.members[2].name)
            assertEquals("another_long_name_exceeding_limit.o", archive.members[3].name)
        }

        @Test
        fun `BSD long name exceeding 15 chars`() {
            val longName = "a_very_long_bsd_style_name.o"
            val archive = roundTrip(
                listOf(ArchiveMember(longName, data = byteArrayOf(0x55, 0xAA.toByte()))),
                variant = ArchiveVariant.BSD
            )
            assertEquals(longName, archive.members[0].name)
            assertArrayEquals(byteArrayOf(0x55, 0xAA.toByte()), archive.members[0].data)
        }

        @Test
        fun `BSD multiple long names`() {
            val names = listOf(
                "bsd_long_name_one.o",
                "bsd_long_name_two_even_longer.o",
            )
            val members = names.map { ArchiveMember(it, data = byteArrayOf(0x42)) }
            val archive = roundTrip(members, variant = ArchiveVariant.BSD)
            assertEquals(2, archive.members.size)
            for (i in names.indices) {
                assertEquals(names[i], archive.members[i].name)
            }
        }

        @Test
        fun `long name with 100 characters`() {
            val longName = "a".repeat(96) + ".o.o"
            assertEquals(100, longName.length)
            val archive = roundTrip(listOf(ArchiveMember(longName, data = byteArrayOf(1))))
            assertEquals(longName, archive.members[0].name)
        }

        @Test
        fun `long name with path separators`() {
            val name = "some_path_to_a_very_long_file.o"
            val archive = roundTrip(listOf(ArchiveMember(name, data = byteArrayOf(1))))
            assertEquals(name, archive.members[0].name)
        }
    }

    @Nested
    inner class SymbolTable {

        @Test
        fun `single symbol round-trips`() {
            val members = listOf(ArchiveMember("main.o", data = ByteArray(16)))
            val symbols = listOf(ArchiveSymbol("_main", memberOffset = 0))
            val archive = roundTrip(members, symbols)
            assertEquals(1, archive.symbols.size)
            assertEquals("_main", archive.symbols[0].name)
        }

        @Test
        fun `multiple symbols round-trip`() {
            val members = listOf(
                ArchiveMember("main.o", data = ByteArray(32)),
                ArchiveMember("util.o", data = ByteArray(16)),
            )
            val symbols = listOf(
                ArchiveSymbol("_main", memberOffset = 0),
                ArchiveSymbol("_helper", memberOffset = 1),
            )
            val archive = roundTrip(members, symbols)
            assertEquals(2, archive.symbols.size)
            assertEquals("_main", archive.symbols[0].name)
            assertEquals("_helper", archive.symbols[1].name)
        }

        @Test
        fun `symbol offsets point to member headers`() {
            val members = listOf(
                ArchiveMember("main.o", data = ByteArray(32)),
                ArchiveMember("util.o", data = ByteArray(16)),
            )
            val symbols = listOf(
                ArchiveSymbol("_main", memberOffset = 0),
                ArchiveSymbol("_helper", memberOffset = 1),
            )
            val archive = roundTrip(members, symbols)
            val mainMember = archive.members.first { it.name == "main.o" }
            assertEquals(mainMember.fileOffset, archive.symbols[0].memberOffset)
        }

        @Test
        fun `multiple symbols per member`() {
            val members = listOf(ArchiveMember("code.o", data = ByteArray(64)))
            val symbols = listOf(
                ArchiveSymbol("func1", memberOffset = 0),
                ArchiveSymbol("func2", memberOffset = 0),
                ArchiveSymbol("func3", memberOffset = 0),
            )
            val archive = roundTrip(members, symbols)
            assertEquals(3, archive.symbols.size)
            // All symbols should point to same member
            val offset = archive.symbols[0].memberOffset
            assertTrue(archive.symbols.all { it.memberOffset == offset })
        }

        @Test
        fun `empty symbol table`() {
            val members = listOf(ArchiveMember("noexport.o", data = byteArrayOf(1)))
            val archive = roundTrip(members, emptyList())
            assertTrue(archive.symbols.isEmpty())
        }

        @Test
        fun `symbol names with underscores`() {
            val members = listOf(ArchiveMember("c.o", data = ByteArray(8)))
            val symbols = listOf(
                ArchiveSymbol("__Z3foov", memberOffset = 0),
                ArchiveSymbol("___cxa_atexit", memberOffset = 0),
            )
            val archive = roundTrip(members, symbols)
            assertEquals("__Z3foov", archive.symbols[0].name)
            assertEquals("___cxa_atexit", archive.symbols[1].name)
        }

        @Test
        fun `symbol names with dots`() {
            val members = listOf(ArchiveMember("cpp.o", data = ByteArray(8)))
            val symbols = listOf(ArchiveSymbol("std.io.printf", memberOffset = 0))
            val archive = roundTrip(members, symbols)
            assertEquals("std.io.printf", archive.symbols[0].name)
        }

        @Test
        fun `symbols with long names`() {
            val longSymbol = "_Z" + "N" + "a".repeat(200) + "Ev"
            val members = listOf(ArchiveMember("mangled.o", data = ByteArray(8)))
            val symbols = listOf(ArchiveSymbol(longSymbol, memberOffset = 0))
            val archive = roundTrip(members, symbols)
            assertEquals(longSymbol, archive.symbols[0].name)
        }

        @Test
        fun `many symbols across many members`() {
            val members = (0 until 20).map { i ->
                ArchiveMember("obj$i.o", data = ByteArray(8))
            }
            val symbols = (0 until 20).map { i ->
                ArchiveSymbol("sym_$i", memberOffset = i.toLong())
            }
            val archive = roundTrip(members, symbols)
            assertEquals(20, archive.members.size)
            assertEquals(20, archive.symbols.size)
        }
    }

    @Nested
    inner class MemberMetadata {

        @Test
        fun `preserves modification time`() {
            val member = ArchiveMember("test.o", modificationTime = 1709726400, data = byteArrayOf(1))
            val archive = roundTrip(listOf(member))
            assertEquals(1709726400L, archive.members[0].modificationTime)
        }

        @Test
        fun `preserves owner and group IDs`() {
            val member = ArchiveMember("test.o", ownerId = 1000, groupId = 1000, data = byteArrayOf(1))
            val archive = roundTrip(listOf(member))
            assertEquals(1000, archive.members[0].ownerId)
            assertEquals(1000, archive.members[0].groupId)
        }

        @Test
        fun `preserves file mode`() {
            val member = ArchiveMember("test.o", mode = 0x1A4, data = byteArrayOf(1))
            val archive = roundTrip(listOf(member))
            assertEquals(0x1A4, archive.members[0].mode)
        }

        @Test
        fun `preserves all metadata together`() {
            val member = ArchiveMember(
                "meta.o",
                modificationTime = 1700000000,
                ownerId = 501,
                groupId = 20,
                mode = 0x1ED, // 0755
                data = byteArrayOf(0x42)
            )
            val archive = roundTrip(listOf(member))
            val m = archive.members[0]
            assertEquals(1700000000L, m.modificationTime)
            assertEquals(501, m.ownerId)
            assertEquals(20, m.groupId)
            assertEquals(0x1ED, m.mode)
        }

        @Test
        fun `zero metadata values`() {
            val member = ArchiveMember("zero.o", modificationTime = 0, ownerId = 0, groupId = 0, mode = 0, data = byteArrayOf(1))
            val archive = roundTrip(listOf(member))
            assertEquals(0L, archive.members[0].modificationTime)
            assertEquals(0, archive.members[0].ownerId)
            assertEquals(0, archive.members[0].groupId)
        }

        @Test
        fun `multiple members with different metadata`() {
            val archive = roundTrip(listOf(
                ArchiveMember("a.o", modificationTime = 100, ownerId = 1, groupId = 1, mode = 0x1A4, data = byteArrayOf(1)),
                ArchiveMember("b.o", modificationTime = 200, ownerId = 2, groupId = 2, mode = 0x1ED, data = byteArrayOf(2)),
            ))
            assertEquals(100L, archive.members[0].modificationTime)
            assertEquals(200L, archive.members[1].modificationTime)
            assertEquals(1, archive.members[0].ownerId)
            assertEquals(2, archive.members[1].ownerId)
        }
    }

    @Nested
    inner class LargeMembers {

        @Test
        fun `64KB member round-trips`() {
            val data = ByteArray(65536) { (it % 256).toByte() }
            val archive = roundTrip(listOf(ArchiveMember("large.o", data = data)))
            assertArrayEquals(data, archive.members[0].data)
        }

        @Test
        fun `128KB member round-trips`() {
            val data = ByteArray(131072) { (it * 3 % 256).toByte() }
            val archive = roundTrip(listOf(ArchiveMember("big.o", data = data)))
            assertArrayEquals(data, archive.members[0].data)
        }

        @Test
        fun `multiple large members`() {
            val data1 = ByteArray(32768) { (it % 256).toByte() }
            val data2 = ByteArray(49152) { ((it + 1) % 256).toByte() }
            val archive = roundTrip(listOf(
                ArchiveMember("big1.o", data = data1),
                ArchiveMember("big2.o", data = data2),
            ))
            assertArrayEquals(data1, archive.members[0].data)
            assertArrayEquals(data2, archive.members[1].data)
        }

        @Test
        fun `large member with odd size`() {
            val data = ByteArray(65537) { (it % 256).toByte() }
            val archive = roundTrip(listOf(
                ArchiveMember("odd_large.o", data = data),
                ArchiveMember("after.o", data = byteArrayOf(0xAB.toByte())),
            ))
            assertArrayEquals(data, archive.members[0].data)
            assertArrayEquals(byteArrayOf(0xAB.toByte()), archive.members[1].data)
        }
    }

    @Nested
    inner class MemberByName {

        @Test
        fun `finds existing member`() {
            val archive = ArchiveFile(members = listOf(
                ArchiveMember("a.o", data = byteArrayOf(1)),
                ArchiveMember("b.o", data = byteArrayOf(2)),
                ArchiveMember("c.o", data = byteArrayOf(3)),
            ))
            val found = archive.memberByName("b.o")
            assertNotNull(found)
            assertArrayEquals(byteArrayOf(2), found!!.data)
        }

        @Test
        fun `returns null for missing member`() {
            val archive = ArchiveFile(members = listOf(
                ArchiveMember("a.o", data = byteArrayOf(1)),
            ))
            assertNull(archive.memberByName("nonexistent.o"))
        }

        @Test
        fun `returns first match for duplicate names`() {
            val archive = ArchiveFile(members = listOf(
                ArchiveMember("dup.o", data = byteArrayOf(1)),
                ArchiveMember("dup.o", data = byteArrayOf(2)),
            ))
            val found = archive.memberByName("dup.o")
            assertNotNull(found)
            assertArrayEquals(byteArrayOf(1), found!!.data)
        }

        @Test
        fun `case-sensitive name lookup`() {
            val archive = ArchiveFile(members = listOf(
                ArchiveMember("Test.o", data = byteArrayOf(1)),
            ))
            assertNull(archive.memberByName("test.o"))
            assertNotNull(archive.memberByName("Test.o"))
        }
    }

    @Nested
    inner class MembersContainingSymbol {

        @Test
        fun `finds member by symbol`() {
            val archive = ArchiveFile(
                members = listOf(
                    ArchiveMember("main.o", data = ByteArray(8), fileOffset = 100),
                    ArchiveMember("util.o", data = ByteArray(8), fileOffset = 200),
                ),
                symbols = listOf(
                    ArchiveSymbol("_main", memberOffset = 100),
                    ArchiveSymbol("_helper", memberOffset = 200),
                )
            )
            val result = archive.membersContainingSymbol("_main")
            assertEquals(1, result.size)
            assertEquals("main.o", result[0].name)
        }

        @Test
        fun `returns empty for unknown symbol`() {
            val archive = ArchiveFile(
                members = listOf(ArchiveMember("a.o", data = ByteArray(8), fileOffset = 100)),
                symbols = listOf(ArchiveSymbol("_known", memberOffset = 100))
            )
            assertTrue(archive.membersContainingSymbol("_unknown").isEmpty())
        }

        @Test
        fun `multiple symbols in same member`() {
            val archive = ArchiveFile(
                members = listOf(ArchiveMember("code.o", data = ByteArray(8), fileOffset = 100)),
                symbols = listOf(
                    ArchiveSymbol("sym1", memberOffset = 100),
                    ArchiveSymbol("sym2", memberOffset = 100),
                )
            )
            val result1 = archive.membersContainingSymbol("sym1")
            val result2 = archive.membersContainingSymbol("sym2")
            assertEquals(1, result1.size)
            assertEquals(1, result2.size)
            assertEquals("code.o", result1[0].name)
            assertEquals("code.o", result2[0].name)
        }

        @Test
        fun `empty symbols list`() {
            val archive = ArchiveFile(members = listOf(ArchiveMember("a.o", data = ByteArray(8))))
            assertTrue(archive.membersContainingSymbol("anything").isEmpty())
        }
    }

    @Nested
    inner class ArchiveVariants {

        @Test
        fun `GNU variant is default`() {
            val archive = roundTrip(listOf(ArchiveMember("test.o", data = byteArrayOf(1))))
            assertEquals(ArchiveVariant.GNU, archive.variant)
        }

        @Test
        fun `BSD variant round-trips short names`() {
            val archive = roundTrip(
                listOf(ArchiveMember("short.o", data = byteArrayOf(1, 2, 3))),
                variant = ArchiveVariant.BSD
            )
            assertEquals("short.o", archive.members[0].name)
        }

        @Test
        fun `BSD variant round-trips long names`() {
            val name = "this_is_a_very_long_name_for_bsd.o"
            val archive = roundTrip(
                listOf(ArchiveMember(name, data = byteArrayOf(0xAB.toByte()))),
                variant = ArchiveVariant.BSD
            )
            assertEquals(name, archive.members[0].name)
            assertArrayEquals(byteArrayOf(0xAB.toByte()), archive.members[0].data)
        }

        @Test
        fun `BSD variant detected on read`() {
            val name = "bsd_long_name_member.o"
            val bytes = makeArchive(
                listOf(ArchiveMember(name, data = byteArrayOf(1))),
                variant = ArchiveVariant.BSD
            )
            val archive = ArchiveReader.read(bytes)
            assertEquals(ArchiveVariant.BSD, archive.variant)
        }

        @Test
        fun `GNU variant with long names writes extended name table`() {
            val longName = "long_enough_to_need_extended_names.o"
            val bytes = makeArchive(listOf(ArchiveMember(longName, data = byteArrayOf(1))))
            val content = String(bytes, Charsets.US_ASCII)
            assertTrue(content.contains("//"), "Should contain long name table: ${content.take(200)}")
        }

        @Test
        fun `enum values exist`() {
            assertEquals(3, ArchiveVariant.entries.size)
            assertNotNull(ArchiveVariant.GNU)
            assertNotNull(ArchiveVariant.BSD)
            assertNotNull(ArchiveVariant.COFF)
        }
    }

    @Nested
    inner class HandCraftedArchives {

        @Test
        fun `minimal hand-crafted archive parses`() {
            val raw = buildString {
                append("!<arch>\n")
                append("test.o/         ")   // name (16)
                append("0           ")       // mtime (12)
                append("0     ")             // uid (6)
                append("0     ")             // gid (6)
                append("100644  ")           // mode (8)
                append("4         ")         // size (10)
                append("`\n")                // end marker (2)
                append("\u0001\u0002\u0003\u0004")
            }
            val archive = ArchiveReader.read(raw.toByteArray(Charsets.US_ASCII))
            assertEquals(1, archive.members.size)
            assertEquals("test.o", archive.members[0].name)
            assertEquals(4, archive.members[0].data.size)
        }

        @Test
        fun `invalid magic throws`() {
            assertThrows(IllegalArgumentException::class.java) {
                ArchiveReader.read("INVALID!".toByteArray(Charsets.US_ASCII))
            }
        }

        @Test
        fun `hand-crafted with two members`() {
            val raw = buildString {
                append("!<arch>\n")
                // First member
                append("a.o/            ")   // name (16)
                append("0           ")       // mtime (12)
                append("0     ")             // uid (6)
                append("0     ")             // gid (6)
                append("100644  ")           // mode (8)
                append("2         ")         // size (10)
                append("`\n")                // end marker (2)
                append("\u0001\u0002")        // data (2 bytes, even)
                // Second member
                append("b.o/            ")   // name (16)
                append("0           ")       // mtime (12)
                append("0     ")             // uid (6)
                append("0     ")             // gid (6)
                append("100644  ")           // mode (8)
                append("1         ")         // size (10)
                append("`\n")                // end marker (2)
                append("\u0003")              // data (1 byte)
            }
            val archive = ArchiveReader.read(raw.toByteArray(Charsets.US_ASCII))
            assertEquals(2, archive.members.size)
            assertEquals("a.o", archive.members[0].name)
            assertEquals("b.o", archive.members[1].name)
        }
    }

    @Nested
    inner class ArchiveWriterFromArchiveFile {

        @Test
        fun `write from ArchiveFile object`() {
            val archiveFile = ArchiveFile(
                members = listOf(
                    ArchiveMember("test.o", data = byteArrayOf(1, 2, 3)),
                ),
            )
            val bytes = ArchiveWriter().write(archiveFile)
            val parsed = ArchiveReader.read(bytes)
            assertEquals(1, parsed.members.size)
            assertEquals("test.o", parsed.members[0].name)
        }

        @Test
        fun `write from ArchiveFile with symbols`() {
            val archiveFile = ArchiveFile(
                members = listOf(
                    ArchiveMember("main.o", data = ByteArray(16)),
                ),
                symbols = listOf(
                    ArchiveSymbol("_start", memberOffset = 0),
                ),
            )
            val bytes = ArchiveWriter().write(archiveFile)
            val parsed = ArchiveReader.read(bytes)
            assertEquals(1, parsed.members.size)
            assertEquals(1, parsed.symbols.size)
            assertEquals("_start", parsed.symbols[0].name)
        }
    }

    @Nested
    inner class SpecialCharactersInNames {

        @Test
        fun `name with dots`() {
            val archive = roundTrip(listOf(ArchiveMember("lib.a.o", data = byteArrayOf(1))))
            assertEquals("lib.a.o", archive.members[0].name)
        }

        @Test
        fun `name with hyphens`() {
            val archive = roundTrip(listOf(ArchiveMember("my-lib.o", data = byteArrayOf(1))))
            assertEquals("my-lib.o", archive.members[0].name)
        }

        @Test
        fun `name with plus signs`() {
            val archive = roundTrip(listOf(ArchiveMember("c++.o", data = byteArrayOf(1))))
            assertEquals("c++.o", archive.members[0].name)
        }

        @Test
        fun `name with numbers`() {
            val archive = roundTrip(listOf(ArchiveMember("123.o", data = byteArrayOf(1))))
            assertEquals("123.o", archive.members[0].name)
        }

        @Test
        fun `name with at sign`() {
            val archive = roundTrip(listOf(ArchiveMember("sym@ver.o", data = byteArrayOf(1))))
            assertEquals("sym@ver.o", archive.members[0].name)
        }

        @Test
        fun `name with mixed extensions`() {
            val archive = roundTrip(listOf(ArchiveMember("test.obj", data = byteArrayOf(1))))
            assertEquals("test.obj", archive.members[0].name)
        }

        @Test
        fun `long name with special characters`() {
            val name = "my-complicated_lib+v2.0.object"
            val archive = roundTrip(listOf(ArchiveMember(name, data = byteArrayOf(1))))
            assertEquals(name, archive.members[0].name)
        }
    }

    @Nested
    inner class BinaryDataPatterns {

        @Test
        fun `all zeros data`() {
            val data = ByteArray(1024)
            val archive = roundTrip(listOf(ArchiveMember("zeros.o", data = data)))
            assertArrayEquals(data, archive.members[0].data)
        }

        @Test
        fun `all 0xFF data`() {
            val data = ByteArray(512) { 0xFF.toByte() }
            val archive = roundTrip(listOf(ArchiveMember("ones.o", data = data)))
            assertArrayEquals(data, archive.members[0].data)
        }

        @Test
        fun `ELF-like magic in member data`() {
            val data = byteArrayOf(0x7f, 0x45, 0x4c, 0x46, 2, 1, 1, 0)
            val archive = roundTrip(listOf(ArchiveMember("elf_obj.o", data = data)))
            assertArrayEquals(data, archive.members[0].data)
        }

        @Test
        fun `PE-like magic in member data`() {
            val data = byteArrayOf(0x4d, 0x5a, 0x90.toByte(), 0x00, 0x03, 0x00, 0x00, 0x00)
            val archive = roundTrip(listOf(ArchiveMember("pe_obj.o", data = data)))
            assertArrayEquals(data, archive.members[0].data)
        }

        @Test
        fun `archive magic in member data does not confuse reader`() {
            val data = "!<arch>\nNOT_AN_ARCHIVE".toByteArray(Charsets.US_ASCII)
            val archive = roundTrip(listOf(ArchiveMember("tricky.o", data = data)))
            assertArrayEquals(data, archive.members[0].data)
        }

        @Test
        fun `newline characters in data`() {
            val data = byteArrayOf(0x0A, 0x0D, 0x0A, 0x0D, 0x60, 0x0A)
            val archive = roundTrip(listOf(ArchiveMember("newlines.o", data = data)))
            assertArrayEquals(data, archive.members[0].data)
        }

        @Test
        fun `backtick-newline (end marker) pattern in data`() {
            // Data containing the "`\n" end marker pattern should not break parsing
            val data = byteArrayOf(0x60, 0x0A, 0x60, 0x0A, 0x60, 0x0A)
            val archive = roundTrip(listOf(ArchiveMember("markers.o", data = data)))
            assertArrayEquals(data, archive.members[0].data)
        }
    }

    @Nested
    inner class ArchiveFileEquality {

        @Test
        fun `members with same name and data are equal`() {
            val a = ArchiveMember("test.o", data = byteArrayOf(1, 2, 3))
            val b = ArchiveMember("test.o", data = byteArrayOf(1, 2, 3))
            assertEquals(a, b)
        }

        @Test
        fun `members with different data are not equal`() {
            val a = ArchiveMember("test.o", data = byteArrayOf(1, 2, 3))
            val b = ArchiveMember("test.o", data = byteArrayOf(4, 5, 6))
            assertNotEquals(a, b)
        }

        @Test
        fun `members with different names are not equal`() {
            val a = ArchiveMember("a.o", data = byteArrayOf(1))
            val b = ArchiveMember("b.o", data = byteArrayOf(1))
            assertNotEquals(a, b)
        }

        @Test
        fun `member hashCode is consistent`() {
            val a = ArchiveMember("test.o", data = byteArrayOf(1, 2, 3))
            val b = ArchiveMember("test.o", data = byteArrayOf(1, 2, 3))
            assertEquals(a.hashCode(), b.hashCode())
        }
    }

    @Nested
    inner class ComplexScenarios {

        @Test
        fun `realistic library with multiple object files and symbols`() {
            val members = listOf(
                ArchiveMember("string_utils.o", data = ByteArray(256) { (it * 7).toByte() }),
                ArchiveMember("math_utils.o", data = ByteArray(512) { (it * 13).toByte() }),
                ArchiveMember("io_helpers.o", data = ByteArray(128) { (it * 3).toByte() }),
                ArchiveMember("main_entry.o", data = ByteArray(64) { (it * 17).toByte() }),
            )
            val symbols = listOf(
                ArchiveSymbol("strlen", memberOffset = 0),
                ArchiveSymbol("strcpy", memberOffset = 0),
                ArchiveSymbol("strcmp", memberOffset = 0),
                ArchiveSymbol("sqrt", memberOffset = 1),
                ArchiveSymbol("sin", memberOffset = 1),
                ArchiveSymbol("cos", memberOffset = 1),
                ArchiveSymbol("printf", memberOffset = 2),
                ArchiveSymbol("scanf", memberOffset = 2),
                ArchiveSymbol("_start", memberOffset = 3),
            )
            val archive = roundTrip(members, symbols)

            assertEquals(4, archive.members.size)
            assertEquals(9, archive.symbols.size)
            assertEquals("string_utils.o", archive.members[0].name)
            assertEquals("math_utils.o", archive.members[1].name)
            assertEquals(256, archive.members[0].data.size)
            assertEquals(512, archive.members[1].data.size)
        }

        @Test
        fun `double round-trip preserves data`() {
            val members = listOf(
                ArchiveMember("a.o", data = byteArrayOf(1, 2, 3)),
                ArchiveMember("very_long_name_for_testing.o", data = byteArrayOf(4, 5)),
            )
            val symbols = listOf(ArchiveSymbol("_sym", memberOffset = 0))

            val bytes1 = makeArchive(members, symbols)
            val archive1 = ArchiveReader.read(bytes1)

            val bytes2 = ArchiveWriter().write(archive1.members, archive1.symbols)
            val archive2 = ArchiveReader.read(bytes2)

            assertEquals(archive1.members.size, archive2.members.size)
            for (i in archive1.members.indices) {
                assertEquals(archive1.members[i].name, archive2.members[i].name)
                assertArrayEquals(archive1.members[i].data, archive2.members[i].data)
            }
            assertEquals(archive1.symbols.size, archive2.symbols.size)
            for (i in archive1.symbols.indices) {
                assertEquals(archive1.symbols[i].name, archive2.symbols[i].name)
            }
        }

        @Test
        fun `twenty members with long names`() {
            val members = (0 until 20).map { i ->
                ArchiveMember("very_long_module_name_number_$i.o", data = ByteArray(i + 1) { it.toByte() })
            }
            val archive = roundTrip(members)
            assertEquals(20, archive.members.size)
            for (i in 0 until 20) {
                assertEquals("very_long_module_name_number_$i.o", archive.members[i].name)
                assertEquals(i + 1, archive.members[i].data.size)
            }
        }

        @Test
        fun `BSD variant with symbols and long names`() {
            val members = listOf(
                ArchiveMember("bsd_long_name_object_file.o", data = ByteArray(32)),
                ArchiveMember("short.o", data = ByteArray(16)),
            )
            val symbols = listOf(
                ArchiveSymbol("_bsd_func", memberOffset = 0),
                ArchiveSymbol("_short_func", memberOffset = 1),
            )
            val archive = roundTrip(members, symbols, ArchiveVariant.BSD)
            assertEquals(2, archive.members.size)
            assertEquals("bsd_long_name_object_file.o", archive.members[0].name)
            assertEquals("short.o", archive.members[1].name)
        }

        @Test
        fun `archive file offset tracking`() {
            val members = listOf(
                ArchiveMember("first.o", data = ByteArray(100)),
                ArchiveMember("second.o", data = ByteArray(200)),
                ArchiveMember("third.o", data = ByteArray(50)),
            )
            val archive = roundTrip(members)
            // Each member should have a different file offset
            val offsets = archive.members.map { it.fileOffset }.toSet()
            assertEquals(3, offsets.size)
            // Offsets should be in order
            assertTrue(archive.members[0].fileOffset < archive.members[1].fileOffset)
            assertTrue(archive.members[1].fileOffset < archive.members[2].fileOffset)
        }
    }
}
