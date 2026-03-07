package org.kgen.binary

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class ObjectFileModelTest {

    @Test
    fun `Section equality uses contentEquals for data`() {
        val a = Section(".text", SectionKind.TEXT, byteArrayOf(1, 2, 3), align = 1)
        val b = Section(".text", SectionKind.TEXT, byteArrayOf(1, 2, 3), align = 1)
        assertEquals(a, b)
    }

    @Test
    fun `Section inequality on different data`() {
        val a = Section(".text", SectionKind.TEXT, byteArrayOf(1, 2, 3), align = 1)
        val b = Section(".text", SectionKind.TEXT, byteArrayOf(1, 2, 4), align = 1)
        assertNotEquals(a, b)
    }

    @Test
    fun `Section inequality on different name`() {
        val a = Section(".text", SectionKind.TEXT, byteArrayOf(1), align = 1)
        val b = Section(".code", SectionKind.TEXT, byteArrayOf(1), align = 1)
        assertNotEquals(a, b)
    }

    @Test
    fun `Section inequality on different kind`() {
        val a = Section(".data", SectionKind.DATA, byteArrayOf(1), align = 1)
        val b = Section(".data", SectionKind.RODATA, byteArrayOf(1), align = 1)
        assertNotEquals(a, b)
    }

    @Test
    fun `Section with empty data`() {
        val sec = Section(".bss", SectionKind.BSS, ByteArray(0), align = 16)
        assertEquals(0, sec.data.size)
        assertEquals(16, sec.align)
    }

    @Test
    fun `Section default values`() {
        val sec = Section(".text", SectionKind.TEXT, byteArrayOf(0xCC.toByte()))
        assertEquals(0L, sec.address)
        assertEquals(1, sec.align)
        assertTrue(sec.flags.isEmpty())
        assertEquals(0L, sec.entrySize)
        assertNull(sec.link)
        assertNull(sec.info)
        assertNull(sec.comdat)
        assertTrue(sec.relocations.isEmpty())
        assertEquals(0, sec.index)
    }

    @Test
    fun `Symbol default values`() {
        val sym = Symbol("test")
        assertEquals(0L, sym.value)
        assertEquals(0L, sym.size)
        assertNull(sym.section)
        assertEquals(SymbolBinding.GLOBAL, sym.binding)
        assertEquals(SymbolKind.FUNCTION, sym.kind)
        assertEquals(SymbolVisibility.DEFAULT, sym.visibility)
        assertTrue(sym.flags.isEmpty())
        assertNull(sym.version)
        assertNull(sym.comdat)
    }

    @Test
    fun `Symbol with all fields`() {
        val sym = Symbol(
            name = "test_func",
            value = 0x1000,
            size = 64,
            section = ".text",
            binding = SymbolBinding.WEAK,
            kind = SymbolKind.FUNCTION,
            visibility = SymbolVisibility.HIDDEN,
            flags = setOf(SymbolFlag.EXPORTED),
            version = "1.0",
        )
        assertEquals("test_func", sym.name)
        assertEquals(0x1000L, sym.value)
        assertEquals(64L, sym.size)
        assertEquals(".text", sym.section)
        assertEquals(SymbolBinding.WEAK, sym.binding)
        assertEquals(SymbolVisibility.HIDDEN, sym.visibility)
        assertTrue(SymbolFlag.EXPORTED in sym.flags)
    }

    @Test
    fun `Relocation fields`() {
        val rel = Relocation(
            offset = 42,
            symbol = "puts",
            type = RelocationType.X86_64.PLT32,
            addend = -4,
            section = ".text",
        )
        assertEquals(42L, rel.offset)
        assertEquals("puts", rel.symbol)
        assertEquals(RelocationType.X86_64.PLT32, rel.type)
        assertEquals(-4L, rel.addend)
        assertEquals(".text", rel.section)
    }

    @Test
    fun `Relocation default addend is zero`() {
        val rel = Relocation(offset = 0, symbol = "foo", type = RelocationType.X86_64.R_64)
        assertEquals(0L, rel.addend)
        assertNull(rel.section)
    }

    @Test
    fun `ImportEntry fields`() {
        val imp = ImportEntry(
            symbolName = "printf",
            moduleName = "libc.so.6",
            kind = ImportKind.FUNCTION,
        )
        assertEquals("printf", imp.symbolName)
        assertEquals("libc.so.6", imp.moduleName)
        assertEquals(ImportKind.FUNCTION, imp.kind)
        assertNull(imp.ordinal)
        assertFalse(imp.isDelayLoad)
    }

    @Test
    fun `ExportEntry fields`() {
        val exp = ExportEntry(
            symbolName = "add",
            ordinal = 1,
            kind = ExportKind.FUNCTION,
        )
        assertEquals("add", exp.symbolName)
        assertEquals(1, exp.ordinal)
        assertFalse(exp.isForwarder)
        assertNull(exp.forwarderName)
    }

    @Test
    fun `ObjectFile default metadata`() {
        val obj = ObjectFile(
            format = ObjectFormat.ELF,
            arch = Architecture(ArchType.X86_64),
            sections = emptyList(),
            symbols = emptyList(),
            relocations = emptyList(),
        )
        assertTrue(obj.imports.isEmpty())
        assertTrue(obj.exports.isEmpty())
        assertNull(obj.debugInfo)
        assertTrue(obj.unwindInfo.isEmpty())
        assertNull(obj.dynamicInfo)
        assertTrue(obj.metadata.flags.isEmpty())
        assertEquals(OsAbi.NONE, obj.metadata.osAbi)
    }

    @Test
    fun `ObjectMetadata with flags`() {
        val meta = ObjectMetadata(
            entryPoint = 0x1000,
            flags = setOf(ObjectFlag.EXECUTABLE, ObjectFlag.POSITION_INDEPENDENT),
            osAbi = OsAbi.LINUX,
        )
        assertEquals(0x1000L, meta.entryPoint)
        assertTrue(ObjectFlag.EXECUTABLE in meta.flags)
        assertTrue(ObjectFlag.POSITION_INDEPENDENT in meta.flags)
        assertFalse(ObjectFlag.RELOCATABLE in meta.flags)
        assertEquals(OsAbi.LINUX, meta.osAbi)
    }

    @Test
    fun `Architecture companion presets`() {
        assertEquals(ArchType.X86_64, Architecture.X86_64_LINUX.arch)
        assertEquals("linux", Architecture.X86_64_LINUX.os)
        assertEquals("gnu", Architecture.X86_64_LINUX.environment)

        assertEquals(ArchType.X86_64, Architecture.X86_64_WINDOWS.arch)
        assertEquals("windows", Architecture.X86_64_WINDOWS.os)
        assertEquals("msvc", Architecture.X86_64_WINDOWS.environment)

        assertEquals(ArchType.X86_64, Architecture.X86_64_MACOS.arch)
        assertEquals("macos", Architecture.X86_64_MACOS.os)
        assertEquals("apple", Architecture.X86_64_MACOS.vendor)

        assertEquals(ArchType.AARCH64, Architecture.AARCH64_LINUX.arch)
        assertEquals(ArchType.AARCH64, Architecture.AARCH64_MACOS.arch)
    }

    @Test
    fun `Architecture triple format`() {
        val arch = Architecture(ArchType.X86_64, vendor = "unknown", os = "linux", environment = "gnu")
        assertEquals("x86_64-unknown-linux-gnu", arch.triple)
    }

    @Test
    fun `Architecture defaults`() {
        val arch = Architecture(ArchType.X86_64)
        assertEquals(Endianness.LITTLE, arch.endianness)
        assertEquals(8, arch.pointerSize)
        assertTrue(arch.features.isEmpty())
    }

    @Test
    fun `ArchType bits are correct`() {
        assertEquals(32, ArchType.X86.bits)
        assertEquals(64, ArchType.X86_64.bits)
        assertEquals(64, ArchType.AARCH64.bits)
        assertEquals(32, ArchType.ARM.bits)
        assertEquals(32, ArchType.WASM32.bits)
        assertEquals(64, ArchType.WASM64.bits)
        assertEquals(64, ArchType.RISCV64.bits)
    }

    @Test
    fun `RelocationType names`() {
        assertEquals("R_X86_64_PLT32", RelocationType.X86_64.PLT32.relocName)
        assertEquals("R_X86_64_PC32", RelocationType.X86_64.PC32.relocName)
        assertEquals("R_AARCH64_CALL26", RelocationType.AArch64.CALL26.relocName)
        assertEquals("X86_64_RELOC_BRANCH", RelocationType.MachO_X86_64.BRANCH.relocName)
        assertEquals("IMAGE_REL_AMD64_REL32", RelocationType.COFF_X86_64.REL32.relocName)
        assertEquals("R_RISCV_CALL", RelocationType.RiscV.CALL.relocName)
    }

    @Test
    fun `RelocationType values`() {
        assertEquals(4, RelocationType.X86_64.PLT32.value)
        assertEquals(2, RelocationType.X86_64.PC32.value)
        assertEquals(1, RelocationType.X86_64.R_64.value)
        assertEquals(283, RelocationType.AArch64.CALL26.value)
        assertEquals(2, RelocationType.MachO_X86_64.BRANCH.value)
    }

    @Test
    fun `Generic relocation type`() {
        val gen = RelocationType.Generic("CUSTOM_RELOC", 999)
        assertEquals("CUSTOM_RELOC", gen.relocName)
        assertEquals(999, gen.value)
    }

    @Test
    fun `DynamicLinkInfo defaults`() {
        val info = DynamicLinkInfo()
        assertTrue(info.neededLibraries.isEmpty())
        assertNull(info.soName)
        assertTrue(info.rpath.isEmpty())
        assertEquals(PLTType.LAZY, info.pltType)
    }

    @Test
    fun `SectionKind covers all major categories`() {
        // Verify key section kinds exist
        assertNotNull(SectionKind.TEXT)
        assertNotNull(SectionKind.DATA)
        assertNotNull(SectionKind.RODATA)
        assertNotNull(SectionKind.BSS)
        assertNotNull(SectionKind.SYMTAB)
        assertNotNull(SectionKind.STRTAB)
        assertNotNull(SectionKind.RELA)
        assertNotNull(SectionKind.GOT)
        assertNotNull(SectionKind.PLT)
        assertNotNull(SectionKind.DYNAMIC)
        assertNotNull(SectionKind.IDATA)
        assertNotNull(SectionKind.EDATA)
    }

    @Test
    fun `SymbolBinding values`() {
        val bindings = SymbolBinding.entries
        assertTrue(bindings.size >= 4)
        assertTrue(SymbolBinding.LOCAL in bindings)
        assertTrue(SymbolBinding.GLOBAL in bindings)
        assertTrue(SymbolBinding.WEAK in bindings)
    }

    @Test
    fun `SymbolVisibility values`() {
        val vis = SymbolVisibility.entries
        assertTrue(vis.size >= 4)
        assertTrue(SymbolVisibility.DEFAULT in vis)
        assertTrue(SymbolVisibility.HIDDEN in vis)
        assertTrue(SymbolVisibility.PROTECTED in vis)
    }
}
