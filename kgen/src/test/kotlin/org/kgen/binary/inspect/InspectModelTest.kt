package org.kgen.binary.inspect

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.binary.*

class InspectModelTest {

    @Nested
    inner class HeaderInfoTest {
        @Test
        fun fieldsAccessible() {
            val h = HeaderInfo(
                format = ObjectFormat.ELF,
                arch = Architecture(ArchType.X86_64),
                type = "executable",
                entryPoint = 0x1000L,
                flags = setOf("flag1"),
                properties = mapOf("key" to "value"),
            )
            assertEquals(ObjectFormat.ELF, h.format)
            assertEquals(ArchType.X86_64, h.arch.arch)
            assertEquals("executable", h.type)
            assertEquals(0x1000L, h.entryPoint)
            assertTrue(h.flags.contains("flag1"))
            assertEquals("value", h.properties["key"])
        }

        @Test
        fun entryPointCanBeNull() {
            val h = HeaderInfo(
                format = ObjectFormat.ELF,
                arch = Architecture(ArchType.X86_64),
                type = "relocatable",
                entryPoint = null,
                flags = emptySet(),
                properties = emptyMap(),
            )
            assertNull(h.entryPoint)
        }

        @Test
        fun equalsForSameValues() {
            val h1 = HeaderInfo(ObjectFormat.ELF, Architecture(ArchType.X86_64), "exec", 0L, emptySet(), emptyMap())
            val h2 = HeaderInfo(ObjectFormat.ELF, Architecture(ArchType.X86_64), "exec", 0L, emptySet(), emptyMap())
            assertEquals(h1, h2)
        }

        @Test
        fun notEqualsForDifferentFormat() {
            val h1 = HeaderInfo(ObjectFormat.ELF, Architecture(ArchType.X86_64), "exec", 0L, emptySet(), emptyMap())
            val h2 = HeaderInfo(ObjectFormat.PE_COFF, Architecture(ArchType.X86_64), "exec", 0L, emptySet(), emptyMap())
            assertNotEquals(h1, h2)
        }
    }

    @Nested
    inner class SectionInfoTest {
        @Test
        fun fieldsAccessible() {
            val s = SectionInfo(
                index = 1, name = ".text", kind = SectionKind.TEXT,
                address = 0x1000, size = 0x200, offset = 0x400,
                align = 16, flags = setOf(SectionFlag.EXEC, SectionFlag.ALLOC),
                entrySize = 0,
            )
            assertEquals(1, s.index)
            assertEquals(".text", s.name)
            assertEquals(SectionKind.TEXT, s.kind)
            assertEquals(0x1000L, s.address)
            assertEquals(0x200L, s.size)
            assertEquals(0x400L, s.offset)
            assertEquals(16, s.align)
            assertTrue(s.flags.contains(SectionFlag.EXEC))
            assertEquals(0L, s.entrySize)
        }

        @Test
        fun equalsSameValues() {
            val s1 = SectionInfo(0, ".data", SectionKind.DATA, 0, 100, 0, 4, emptySet(), 0)
            val s2 = SectionInfo(0, ".data", SectionKind.DATA, 0, 100, 0, 4, emptySet(), 0)
            assertEquals(s1, s2)
        }
    }

    @Nested
    inner class SymbolInfoTest {
        @Test
        fun fieldsAccessible() {
            val s = SymbolInfo(
                name = "main", value = 0x1000, size = 64,
                binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION,
                visibility = SymbolVisibility.DEFAULT,
                section = ".text", version = "GLIBC_2.17", demangled = null,
            )
            assertEquals("main", s.name)
            assertEquals(0x1000L, s.value)
            assertEquals(64L, s.size)
            assertEquals(SymbolBinding.GLOBAL, s.binding)
            assertEquals(SymbolKind.FUNCTION, s.kind)
            assertEquals(SymbolVisibility.DEFAULT, s.visibility)
            assertEquals(".text", s.section)
            assertEquals("GLIBC_2.17", s.version)
            assertNull(s.demangled)
        }

        @Test
        fun nullableFieldsCanBeNull() {
            val s = SymbolInfo("sym", 0, 0, SymbolBinding.LOCAL, SymbolKind.DATA,
                SymbolVisibility.HIDDEN, section = null, version = null, demangled = null)
            assertNull(s.section)
            assertNull(s.version)
            assertNull(s.demangled)
        }
    }

    @Nested
    inner class ExportInfoTest {
        @Test
        fun fieldsAccessible() {
            val e = ExportInfo(name = "foo", address = 0x2000, ordinal = 1)
            assertEquals("foo", e.name)
            assertEquals(0x2000L, e.address)
            assertEquals(1, e.ordinal)
        }

        @Test
        fun ordinalCanBeNull() {
            val e = ExportInfo("bar", 0, null)
            assertNull(e.ordinal)
        }
    }

    @Nested
    inner class ImportInfoTest {
        @Test
        fun fieldsAccessible() {
            val i = ImportInfo(name = "printf", module = "libc.so.6", ordinal = null, isDelayLoad = false)
            assertEquals("printf", i.name)
            assertEquals("libc.so.6", i.module)
            assertNull(i.ordinal)
            assertFalse(i.isDelayLoad)
        }

        @Test
        fun delayLoadFlag() {
            val i = ImportInfo("func", "mod.dll", ordinal = 5, isDelayLoad = true)
            assertTrue(i.isDelayLoad)
            assertEquals(5, i.ordinal)
        }
    }

    @Nested
    inner class RelocationInfoTest {
        @Test
        fun fieldsAccessible() {
            val r = RelocationInfo(offset = 0x100, symbol = "puts", type = "R_X86_64_PLT32", addend = -4, section = ".text")
            assertEquals(0x100L, r.offset)
            assertEquals("puts", r.symbol)
            assertEquals("R_X86_64_PLT32", r.type)
            assertEquals(-4L, r.addend)
            assertEquals(".text", r.section)
        }
    }

    @Nested
    inner class LineMappingTest {
        @Test
        fun fieldsAccessible() {
            val l = LineMapping(address = 0x1000, file = "main.c", line = 42, column = 5)
            assertEquals(0x1000L, l.address)
            assertEquals("main.c", l.file)
            assertEquals(42, l.line)
            assertEquals(5, l.column)
        }
    }

    @Nested
    inner class DynamicEntryTest {
        @Test
        fun fieldsAccessible() {
            val d = DynamicEntry(tag = "NEEDED", value = 1, stringValue = "libc.so.6")
            assertEquals("NEEDED", d.tag)
            assertEquals(1L, d.value)
            assertEquals("libc.so.6", d.stringValue)
        }

        @Test
        fun stringValueCanBeNull() {
            val d = DynamicEntry("FLAGS", 0x08, null)
            assertNull(d.stringValue)
        }
    }

    @Nested
    inner class FoundStringTest {
        @Test
        fun fieldsAccessible() {
            val f = FoundString(offset = 0x500, section = ".rodata", value = "Hello")
            assertEquals(0x500L, f.offset)
            assertEquals(".rodata", f.section)
            assertEquals("Hello", f.value)
        }

        @Test
        fun sectionCanBeNull() {
            val f = FoundString(0, null, "test")
            assertNull(f.section)
        }
    }
}
