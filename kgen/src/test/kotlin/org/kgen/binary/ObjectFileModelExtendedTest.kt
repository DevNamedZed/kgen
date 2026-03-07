package org.kgen.binary

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class ObjectFileModelExtendedTest {

    // --- Section equality edge cases ---

    @Test
    fun sectionEqualityWithEmptyData() {
        val a = Section(".bss", SectionKind.BSS, ByteArray(0), align = 16)
        val b = Section(".bss", SectionKind.BSS, ByteArray(0), align = 16)
        assertEquals(a, b)
    }

    @Test
    fun sectionEqualityDifferentAlign() {
        val a = Section(".text", SectionKind.TEXT, byteArrayOf(1), align = 1)
        val b = Section(".text", SectionKind.TEXT, byteArrayOf(1), align = 16)
        assertNotEquals(a, b)
    }

    @Test
    fun sectionEqualityLargeData() {
        val data = ByteArray(4096) { (it % 256).toByte() }
        val a = Section(".data", SectionKind.DATA, data.clone(), align = 4)
        val b = Section(".data", SectionKind.DATA, data.clone(), align = 4)
        assertEquals(a, b)
    }

    @Test
    fun sectionEqualityDifferentLargeData() {
        val d1 = ByteArray(4096) { (it % 256).toByte() }
        val d2 = d1.clone()
        d2[2048] = (d2[2048] + 1).toByte()
        assertNotEquals(
            Section(".data", SectionKind.DATA, d1, align = 4),
            Section(".data", SectionKind.DATA, d2, align = 4)
        )
    }

    // --- Section with all fields ---

    @Test
    fun sectionWithAllFields() {
        val sec = Section(
            name = ".text",
            kind = SectionKind.TEXT,
            data = byteArrayOf(0xCC.toByte()),
            address = 0x401000,
            align = 16,
            flags = setOf(SectionFlag.EXEC, SectionFlag.ALLOC),
            entrySize = 0,
            link = ".strtab",
            info = ".text",
            comdat = "group1",
            relocations = listOf(
                Relocation(offset = 0, symbol = "puts", type = RelocationType.X86_64.PLT32, addend = -4)
            ),
            index = 1
        )
        assertEquals(0x401000L, sec.address)
        assertEquals(16, sec.align)
        assertTrue(SectionFlag.EXEC in sec.flags)
        assertTrue(SectionFlag.ALLOC in sec.flags)
        assertEquals(".strtab", sec.link)
        assertEquals(".text", sec.info)
        assertEquals("group1", sec.comdat)
        assertEquals(1, sec.relocations.size)
        assertEquals(1, sec.index)
    }

    // --- Symbol varieties ---

    @Test
    fun symbolLocal() {
        val sym = Symbol("local_var", binding = SymbolBinding.LOCAL, kind = SymbolKind.DATA)
        assertEquals(SymbolBinding.LOCAL, sym.binding)
    }

    @Test
    fun symbolWeak() {
        val sym = Symbol("weak_fn", binding = SymbolBinding.WEAK, kind = SymbolKind.FUNCTION)
        assertEquals(SymbolBinding.WEAK, sym.binding)
    }

    @Test
    fun symbolWithComdat() {
        val sym = Symbol("inline_fn", comdat = "comdat_group")
        assertEquals("comdat_group", sym.comdat)
    }

    @Test
    fun symbolKindValues() {
        assertNotNull(SymbolKind.FUNCTION)
        assertNotNull(SymbolKind.DATA)
        assertNotNull(SymbolKind.SECTION)
        assertNotNull(SymbolKind.FILE)
    }

    @Test
    fun symbolVisibilityValues() {
        assertNotNull(SymbolVisibility.DEFAULT)
        assertNotNull(SymbolVisibility.HIDDEN)
        assertNotNull(SymbolVisibility.PROTECTED)
        assertNotNull(SymbolVisibility.INTERNAL)
    }

    @Test
    fun symbolFlagValues() {
        assertNotNull(SymbolFlag.EXPORTED)
        assertNotNull(SymbolFlag.IMPORTED)
    }

    // --- Relocation types ---

    @Test
    fun x86_64RelocationTypes() {
        assertEquals(1, RelocationType.X86_64.R_64.value)
        assertEquals(2, RelocationType.X86_64.PC32.value)
        assertEquals(4, RelocationType.X86_64.PLT32.value)
        assertEquals(10, RelocationType.X86_64.R_32.value)
        assertEquals(11, RelocationType.X86_64.R_32S.value)
    }

    @Test
    fun aarch64RelocationTypes() {
        assertEquals(283, RelocationType.AArch64.CALL26.value)
        assertEquals(275, RelocationType.AArch64.ADR_PREL_PG_HI21.value)
        assertEquals(286, RelocationType.AArch64.LDST64_ABS_LO12_NC.value)
    }

    @Test
    fun riscvRelocationTypes() {
        assertEquals("R_RISCV_CALL", RelocationType.RiscV.CALL.relocName)
        assertEquals("R_RISCV_PCREL_HI20", RelocationType.RiscV.PCREL_HI20.relocName)
    }

    @Test
    fun machoRelocationTypes() {
        assertEquals("X86_64_RELOC_BRANCH", RelocationType.MachO_X86_64.BRANCH.relocName)
        assertEquals("X86_64_RELOC_SIGNED", RelocationType.MachO_X86_64.SIGNED.relocName)
    }

    @Test
    fun coffRelocationTypes() {
        assertEquals("IMAGE_REL_AMD64_REL32", RelocationType.COFF_X86_64.REL32.relocName)
        assertEquals("IMAGE_REL_AMD64_ADDR64", RelocationType.COFF_X86_64.ADDR64.relocName)
    }

    @Test
    fun genericRelocationType() {
        val r = RelocationType.Generic("MY_RELOC", 42)
        assertEquals("MY_RELOC", r.relocName)
        assertEquals(42, r.value)
    }

    @Test
    fun genericRelocationEquality() {
        val a = RelocationType.Generic("R1", 1)
        val b = RelocationType.Generic("R1", 1)
        assertEquals(a, b)
    }

    @Test
    fun genericRelocationInequality() {
        val a = RelocationType.Generic("R1", 1)
        val b = RelocationType.Generic("R2", 2)
        assertNotEquals(a, b)
    }

    // --- Relocation fields ---

    @Test
    fun relocationWithSection() {
        val rel = Relocation(
            offset = 0x10, symbol = "memcpy", type = RelocationType.X86_64.PLT32,
            addend = -4, section = ".text"
        )
        assertEquals(0x10L, rel.offset)
        assertEquals("memcpy", rel.symbol)
        assertEquals(-4L, rel.addend)
        assertEquals(".text", rel.section)
    }

    @Test
    fun relocationDefaultAddend() {
        val rel = Relocation(offset = 0, symbol = "x", type = RelocationType.X86_64.R_64)
        assertEquals(0L, rel.addend)
    }

    // --- ImportEntry ---

    @Test
    fun importEntryWithOrdinal() {
        val imp = ImportEntry(symbolName = "func", moduleName = "kernel32.dll",
            ordinal = 42, kind = ImportKind.FUNCTION)
        assertEquals(42, imp.ordinal)
    }

    @Test
    fun importEntryDelayLoad() {
        val imp = ImportEntry(symbolName = "lazy_func", moduleName = "mylib.dll",
            isDelayLoad = true, kind = ImportKind.FUNCTION)
        assertTrue(imp.isDelayLoad)
    }

    @Test
    fun importKindValues() {
        assertNotNull(ImportKind.FUNCTION)
        assertNotNull(ImportKind.DATA)
    }

    // --- ExportEntry ---

    @Test
    fun exportEntryForwarder() {
        val exp = ExportEntry(symbolName = "HeapAlloc", ordinal = 1,
            kind = ExportKind.FUNCTION, isForwarder = true, forwarderName = "NTDLL.RtlAllocateHeap")
        assertTrue(exp.isForwarder)
        assertEquals("NTDLL.RtlAllocateHeap", exp.forwarderName)
    }

    @Test
    fun exportKindValues() {
        assertNotNull(ExportKind.FUNCTION)
        assertNotNull(ExportKind.DATA)
    }

    // --- ObjectFile ---

    @Test
    fun objectFileWithSectionsAndSymbols() {
        val obj = ObjectFile(
            format = ObjectFormat.ELF,
            arch = Architecture(ArchType.X86_64),
            sections = listOf(
                Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 16),
                Section(".data", SectionKind.DATA, byteArrayOf(0x42), align = 4),
            ),
            symbols = listOf(
                Symbol("main", value = 0, size = 1, section = ".text", kind = SymbolKind.FUNCTION),
                Symbol("var", value = 0, size = 1, section = ".data", kind = SymbolKind.DATA),
            ),
            relocations = listOf(
                Relocation(offset = 0, symbol = "puts", type = RelocationType.X86_64.PLT32, addend = -4, section = ".text")
            ),
        )
        assertEquals(2, obj.sections.size)
        assertEquals(2, obj.symbols.size)
        assertEquals(1, obj.relocations.size)
    }

    @Test
    fun objectFileWithImportsAndExports() {
        val obj = ObjectFile(
            format = ObjectFormat.PE_COFF,
            arch = Architecture.X86_64_WINDOWS,
            sections = emptyList(), symbols = emptyList(), relocations = emptyList(),
            imports = listOf(ImportEntry("printf", "msvcrt.dll", kind = ImportKind.FUNCTION)),
            exports = listOf(ExportEntry("add", ordinal = 1, kind = ExportKind.FUNCTION)),
        )
        assertEquals(1, obj.imports.size)
        assertEquals(1, obj.exports.size)
        assertEquals("printf", obj.imports[0].symbolName)
        assertEquals("add", obj.exports[0].symbolName)
    }

    @Test
    fun objectFileMetadataExecutable() {
        val obj = ObjectFile(
            format = ObjectFormat.ELF, arch = Architecture(ArchType.X86_64),
            sections = emptyList(), symbols = emptyList(), relocations = emptyList(),
            metadata = ObjectMetadata(
                entryPoint = 0x401000,
                flags = setOf(ObjectFlag.EXECUTABLE),
                osAbi = OsAbi.LINUX,
            ),
        )
        assertEquals(0x401000L, obj.metadata.entryPoint)
        assertTrue(ObjectFlag.EXECUTABLE in obj.metadata.flags)
        assertEquals(OsAbi.LINUX, obj.metadata.osAbi)
    }

    @Test
    fun objectFileMetadataRelocatable() {
        val obj = ObjectFile(
            format = ObjectFormat.ELF, arch = Architecture(ArchType.X86_64),
            sections = emptyList(), symbols = emptyList(), relocations = emptyList(),
            metadata = ObjectMetadata(flags = setOf(ObjectFlag.RELOCATABLE)),
        )
        assertTrue(ObjectFlag.RELOCATABLE in obj.metadata.flags)
        assertFalse(ObjectFlag.EXECUTABLE in obj.metadata.flags)
    }

    // --- Architecture ---

    @Test
    fun architectureTripleComponents() {
        val arch = Architecture(ArchType.AARCH64, vendor = "apple", os = "macos", environment = "")
        assertEquals(ArchType.AARCH64, arch.arch)
        assertEquals("apple", arch.vendor)
        assertEquals("macos", arch.os)
    }

    @Test
    fun architectureRiscV() {
        val arch = Architecture(ArchType.RISCV64)
        assertEquals(64, arch.arch.bits)
        assertEquals(Endianness.LITTLE, arch.endianness)
    }

    @Test
    fun architectureWasm32() {
        val arch = Architecture(ArchType.WASM32, pointerSize = 4)
        assertEquals(32, arch.arch.bits)
        assertEquals(4, arch.pointerSize)
    }

    @Test
    fun architecturePresets() {
        val presets = listOf(
            Architecture.X86_64_LINUX,
            Architecture.X86_64_WINDOWS,
            Architecture.X86_64_MACOS,
            Architecture.AARCH64_LINUX,
            Architecture.AARCH64_MACOS,
        )
        for (a in presets) {
            assertTrue(a.arch.bits >= 32)
            assertTrue(a.pointerSize >= 4)
        }
    }

    @Test
    fun archTypeBits() {
        assertEquals(32, ArchType.X86.bits)
        assertEquals(64, ArchType.X86_64.bits)
        assertEquals(32, ArchType.ARM.bits)
        assertEquals(64, ArchType.AARCH64.bits)
        assertEquals(32, ArchType.RISCV32.bits)
        assertEquals(64, ArchType.RISCV64.bits)
        assertEquals(32, ArchType.WASM32.bits)
        assertEquals(64, ArchType.WASM64.bits)
    }

    // --- DynamicLinkInfo ---

    @Test
    fun dynamicLinkInfoWithLibraries() {
        val info = DynamicLinkInfo(
            neededLibraries = listOf("libc.so.6", "libm.so.6"),
            soName = "libfoo.so.1",
            rpath = listOf("/usr/lib", "/opt/lib"),
            pltType = PLTType.EAGER,
        )
        assertEquals(2, info.neededLibraries.size)
        assertEquals("libfoo.so.1", info.soName)
        assertEquals(2, info.rpath.size)
        assertEquals(PLTType.EAGER, info.pltType)
    }

    @Test
    fun pltTypeValues() {
        assertNotNull(PLTType.LAZY)
        assertNotNull(PLTType.EAGER)
        assertNotNull(PLTType.NONE)
    }

    // --- OsAbi ---

    @Test
    fun osAbiValues() {
        assertNotNull(OsAbi.NONE)
        assertNotNull(OsAbi.LINUX)
        assertNotNull(OsAbi.FREEBSD)
    }

    // --- ObjectFlag ---

    @Test
    fun objectFlagValues() {
        assertNotNull(ObjectFlag.EXECUTABLE)
        assertNotNull(ObjectFlag.RELOCATABLE)
        assertNotNull(ObjectFlag.POSITION_INDEPENDENT)
        assertNotNull(ObjectFlag.LARGE_ADDRESS_AWARE)
    }

    @Test
    fun objectFlagCombinations() {
        val flags = setOf(ObjectFlag.EXECUTABLE, ObjectFlag.POSITION_INDEPENDENT)
        assertTrue(ObjectFlag.EXECUTABLE in flags)
        assertTrue(ObjectFlag.POSITION_INDEPENDENT in flags)
        assertFalse(ObjectFlag.RELOCATABLE in flags)
    }

    // --- SectionKind ---

    @Test
    fun sectionKindCoverage() {
        val kinds = SectionKind.entries
        assertTrue(kinds.size >= 10, "Should have many section kinds: $kinds")
        assertTrue(SectionKind.TEXT in kinds)
        assertTrue(SectionKind.DATA in kinds)
        assertTrue(SectionKind.RODATA in kinds)
        assertTrue(SectionKind.BSS in kinds)
    }

    // --- SectionFlag ---

    @Test
    fun sectionFlagValues() {
        assertNotNull(SectionFlag.WRITE)
        assertNotNull(SectionFlag.ALLOC)
        assertNotNull(SectionFlag.EXEC)
    }

    // --- Endianness ---

    @Test
    fun endiannessValues() {
        assertNotNull(Endianness.LITTLE)
        assertNotNull(Endianness.BIG)
    }
}
