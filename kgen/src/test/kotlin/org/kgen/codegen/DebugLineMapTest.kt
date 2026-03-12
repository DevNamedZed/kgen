package org.kgen.codegen

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class DebugLineMapTest {

    @Test
    fun emptyMap() {
        val map = DebugLineMap.empty()
        assertTrue(map.isEmpty())
        assertEquals(0, map.size())
        assertNull(map.lookup(0))
        assertTrue(map.entries().isEmpty())
    }

    @Test
    fun builderAddsEntries() {
        val map = DebugLineMap.builder()
            .apply {
                add(0, "Foo.java", 10)
                add(8, "Foo.java", 11)
                add(16, "Foo.java", 12)
            }
            .build()

        assertEquals(3, map.size())
        assertFalse(map.isEmpty())
    }

    @Test
    fun lookupExactOffset() {
        val map = DebugLineMap.builder()
            .apply {
                add(0, "Foo.java", 10)
                add(8, "Foo.java", 11)
                add(16, "Foo.java", 12)
            }
            .build()

        val entry = map.lookupExact(8)
        assertNotNull(entry)
        assertEquals("Foo.java", entry!!.file)
        assertEquals(11, entry.line)
    }

    @Test
    fun lookupFloorOffset() {
        val map = DebugLineMap.builder()
            .apply {
                add(0, "Foo.java", 10)
                add(8, "Foo.java", 11)
                add(16, "Foo.java", 12)
            }
            .build()

        // Offset 5 falls between entries 0 and 8 → returns entry at 0
        val entry = map.lookup(5)
        assertNotNull(entry)
        assertEquals(10, entry!!.line)

        // Offset 12 falls between entries 8 and 16 → returns entry at 8
        val entry2 = map.lookup(12)
        assertNotNull(entry2)
        assertEquals(11, entry2!!.line)
    }

    @Test
    fun lookupBeforeFirstEntry() {
        val map = DebugLineMap.builder()
            .apply { add(10, "Foo.java", 5) }
            .build()

        assertNull(map.lookup(5))
    }

    @Test
    fun entriesForFile() {
        val map = DebugLineMap.builder()
            .apply {
                add(0, "Foo.java", 10)
                add(8, "Bar.java", 20)
                add(16, "Foo.java", 11)
            }
            .build()

        val fooEntries = map.entriesForFile("Foo.java")
        assertEquals(2, fooEntries.size)
        assertEquals(10, fooEntries[0].line)
        assertEquals(11, fooEntries[1].line)
    }

    @Test
    fun entriesForScope() {
        val map = DebugLineMap.builder()
            .apply {
                add(0, "Foo.java", 10, scope = "add")
                add(8, "Foo.java", 20, scope = "sub")
                add(16, "Foo.java", 11, scope = "add")
            }
            .build()

        val addEntries = map.entriesForScope("add")
        assertEquals(2, addEntries.size)
    }

    @Test
    fun files() {
        val map = DebugLineMap.builder()
            .apply {
                add(0, "Foo.java", 10)
                add(8, "Bar.java", 20)
                add(16, "Foo.java", 11)
            }
            .build()

        assertEquals(setOf("Foo.java", "Bar.java"), map.files())
    }

    @Test
    fun toLineEntries() {
        val map = DebugLineMap.builder()
            .apply {
                add(0, "Foo.java", 10, column = 5)
                add(8, "Foo.java", 11, column = 3)
            }
            .build()

        val lineEntries = map.toLineEntries()
        assertEquals(2, lineEntries.size)
        assertEquals(0L, lineEntries[0].address)
        assertEquals("Foo.java", lineEntries[0].file)
        assertEquals(10, lineEntries[0].line)
        assertEquals(5, lineEntries[0].column)
    }

    @Test
    fun fromLineEntries() {
        val lineEntries = listOf(
            org.kgen.binary.LineEntry(address = 0, file = "Test.java", line = 5),
            org.kgen.binary.LineEntry(address = 12, file = "Test.java", line = 8),
        )
        val map = DebugLineMap.fromLineEntries(lineEntries, scope = "main")
        assertEquals(2, map.size())
        assertEquals(5, map.lookup(0)!!.line)
        assertEquals("main", map.lookup(0)!!.scope)
    }

    @Test
    fun builderSortsByOffset() {
        val map = DebugLineMap.builder()
            .apply {
                add(16, "Foo.java", 12)
                add(0, "Foo.java", 10)
                add(8, "Foo.java", 11)
            }
            .build()

        val entries = map.entries()
        assertEquals(0, entries[0].codeOffset)
        assertEquals(8, entries[1].codeOffset)
        assertEquals(16, entries[2].codeOffset)
    }

    @Test
    fun toStringDescriptive() {
        val map = DebugLineMap.builder()
            .apply {
                add(0, "Foo.java", 10)
                add(8, "Foo.java", 11)
            }
            .build()

        val str = map.toString()
        assertTrue(str.contains("2 entries"))
        assertTrue(str.contains("Foo.java:10"))
        assertTrue(str.contains("Foo.java:11"))
    }

    @Test
    fun entryWithInlinedAt() {
        val map = DebugLineMap.builder()
            .apply {
                add(0, "Foo.java", 10, scope = "add", inlinedAt = "main:5")
            }
            .build()

        val entry = map.lookup(0)!!
        assertEquals("main:5", entry.inlinedAt)
        assertTrue(map.toString().contains("inlined from main:5"))
    }

    @Test
    fun entryWithColumn() {
        val entry = DebugLineMap.Entry(0, "Foo.java", 10, column = 5, scope = "test")
        assertEquals(5, entry.column)
    }

    @Test
    fun scopes() {
        val map = DebugLineMap.builder()
            .apply {
                add(0, "Foo.java", 10, scope = "add")
                add(8, "Foo.java", 20, scope = "sub")
            }
            .build()

        assertEquals(setOf("add", "sub"), map.scopes())
    }
}
