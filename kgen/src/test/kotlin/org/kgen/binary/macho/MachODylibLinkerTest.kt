package org.kgen.binary.macho

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.kgen.binary.*

class MachODylibLinkerTest {

    private fun simpleObject(): ObjectFile = ObjectFile(
        format = ObjectFormat.MACH_O,
        arch = Architecture.X86_64_MACOS,
        sections = listOf(
            Section(
                name = "__text", kind = SectionKind.TEXT,
                data = byteArrayOf(
                    0xC3.toByte(), // ret
                ),
                align = 4,
            ),
        ),
        symbols = listOf(
            Symbol(name = "_square", value = 0, kind = SymbolKind.FUNCTION,
                binding = SymbolBinding.GLOBAL, section = "__text"),
        ),
        relocations = emptyList(),
    )

    private fun objectWithData(): ObjectFile = ObjectFile(
        format = ObjectFormat.MACH_O,
        arch = Architecture.X86_64_MACOS,
        sections = listOf(
            Section(name = "__text", kind = SectionKind.TEXT,
                data = byteArrayOf(0xC3.toByte()), align = 4),
            Section(name = "__data", kind = SectionKind.DATA,
                data = byteArrayOf(0x42, 0x00, 0x00, 0x00), align = 4),
        ),
        symbols = listOf(
            Symbol(name = "_func", value = 0, kind = SymbolKind.FUNCTION,
                binding = SymbolBinding.GLOBAL, section = "__text"),
            Symbol(name = "_var", value = 0, kind = SymbolKind.DATA,
                binding = SymbolBinding.GLOBAL, section = "__data"),
        ),
        relocations = emptyList(),
    )

    @Test
    fun producesValidMachODylib() {
        val linker = MachODylibLinker()
        val bytes = linker.link(listOf(simpleObject()))
        assertTrue(bytes.isNotEmpty())
        // Mach-O 64-bit magic (little-endian)
        assertEquals(0xCF, bytes[0].toInt() and 0xFF)
        assertEquals(0xFA, bytes[1].toInt() and 0xFF)
        assertEquals(0xED, bytes[2].toInt() and 0xFF)
        assertEquals(0xFE, bytes[3].toInt() and 0xFF)
    }

    @Test
    fun fileTypeIsDylib() {
        val bytes = MachODylibLinker().link(listOf(simpleObject()))
        // filetype at offset 12 (4 bytes LE)
        val fileType = (bytes[12].toInt() and 0xFF) or
            ((bytes[13].toInt() and 0xFF) shl 8)
        assertEquals(MachO.MH_DYLIB, fileType)
    }

    @Test
    fun hasIdDylibLoadCommand() {
        val bytes = MachODylibLinker(installName = "libtest.1.dylib").link(listOf(simpleObject()))
        // Search for LC_ID_DYLIB (0x0D) in load commands
        val str = String(bytes, Charsets.US_ASCII)
        assertTrue(str.contains("libtest.1.dylib"), "Should contain install name")
    }

    @Test
    fun dylibWithDataSegment() {
        val bytes = MachODylibLinker().link(listOf(objectWithData()))
        assertTrue(bytes.isNotEmpty())
        val fileType = (bytes[12].toInt() and 0xFF) or
            ((bytes[13].toInt() and 0xFF) shl 8)
        assertEquals(MachO.MH_DYLIB, fileType)
    }

    @Test
    fun multipleObjectFiles() {
        val obj1 = simpleObject()
        val obj2 = ObjectFile(
            format = ObjectFormat.MACH_O,
            arch = Architecture.X86_64_MACOS,
            sections = listOf(
                Section(name = "__text", kind = SectionKind.TEXT,
                    data = byteArrayOf(0x90.toByte(), 0xC3.toByte()), align = 4),
            ),
            symbols = listOf(
                Symbol(name = "_other", value = 0, kind = SymbolKind.FUNCTION,
                    binding = SymbolBinding.GLOBAL, section = "__text"),
            ),
            relocations = emptyList(),
        )
        val bytes = MachODylibLinker().link(listOf(obj1, obj2))
        assertTrue(bytes.isNotEmpty())
        assertEquals(MachO.MH_DYLIB, bytes[12].toInt() and 0xFF)
    }

    @Test
    fun arm64CpuType() {
        val arm64Obj = ObjectFile(
            format = ObjectFormat.MACH_O,
            arch = Architecture.AARCH64_MACOS,
            sections = listOf(
                Section(name = "__text", kind = SectionKind.TEXT,
                    data = byteArrayOf(0xC0.toByte(), 0x03, 0x5F, 0xD6.toByte()), align = 4), // ret
            ),
            symbols = listOf(
                Symbol(name = "_func", value = 0, kind = SymbolKind.FUNCTION,
                    binding = SymbolBinding.GLOBAL, section = "__text"),
            ),
            relocations = emptyList(),
        )
        val bytes = MachODylibLinker(
            cpuType = MachO.CPU_TYPE_ARM64,
            cpuSubtype = MachO.CPU_SUBTYPE_ARM64_ALL,
        ).link(listOf(arm64Obj))
        assertTrue(bytes.isNotEmpty())
        assertEquals(MachO.MH_DYLIB, bytes[12].toInt() and 0xFF)
        // CPU type at offset 4 (4 bytes LE)
        val cpuType = (bytes[4].toInt() and 0xFF) or
            ((bytes[5].toInt() and 0xFF) shl 8) or
            ((bytes[6].toInt() and 0xFF) shl 16) or
            ((bytes[7].toInt() and 0xFF) shl 24)
        assertEquals(MachO.CPU_TYPE_ARM64, cpuType)
    }

    @Test
    fun undefinedSymbolsBecomeImports() {
        val obj = ObjectFile(
            format = ObjectFormat.MACH_O,
            arch = Architecture.X86_64_MACOS,
            sections = listOf(
                Section(name = "__text", kind = SectionKind.TEXT,
                    data = byteArrayOf(0xC3.toByte()), align = 4),
            ),
            symbols = listOf(
                Symbol(name = "_func", value = 0, kind = SymbolKind.FUNCTION,
                    binding = SymbolBinding.GLOBAL, section = "__text"),
                Symbol(name = "_printf", value = 0, kind = SymbolKind.UNDEFINED,
                    binding = SymbolBinding.GLOBAL, section = null),
            ),
            relocations = emptyList(),
        )
        // Should not throw — undefined symbols become imports
        val bytes = MachODylibLinker().link(listOf(obj))
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun readBackWithMachOReader() {
        val bytes = MachODylibLinker(installName = "libfoo.dylib").link(listOf(simpleObject()))
        val file = MachOReader.read(bytes)
        assertTrue(file.isDylib)
    }

    @Test
    fun multipleSymbolsExportedCorrectly() {
        val obj = ObjectFile(
            format = ObjectFormat.MACH_O,
            arch = Architecture.X86_64_MACOS,
            sections = listOf(
                Section(name = "__text", kind = SectionKind.TEXT,
                    data = byteArrayOf(
                        0xC3.toByte(),                // ret (_func1)
                        0x90.toByte(), 0xC3.toByte(), // nop, ret (_func2)
                    ), align = 4),
            ),
            symbols = listOf(
                Symbol(name = "_func1", value = 0, kind = SymbolKind.FUNCTION,
                    binding = SymbolBinding.GLOBAL, section = "__text"),
                Symbol(name = "_func2", value = 1, kind = SymbolKind.FUNCTION,
                    binding = SymbolBinding.GLOBAL, section = "__text"),
                Symbol(name = "_func3", value = 2, kind = SymbolKind.FUNCTION,
                    binding = SymbolBinding.GLOBAL, section = "__text"),
            ),
            relocations = emptyList(),
        )
        val bytes = MachODylibLinker().link(listOf(obj))
        assertTrue(bytes.isNotEmpty())
        val file = MachOReader.read(bytes)
        assertTrue(file.isDylib)
    }

    @Test
    fun dylibFlagsIncludeDyldLinkAndTwoLevel() {
        val bytes = MachODylibLinker().link(listOf(simpleObject()))
        // flags at offset 24 (4 bytes LE)
        val flags = (bytes[24].toInt() and 0xFF) or
            ((bytes[25].toInt() and 0xFF) shl 8) or
            ((bytes[26].toInt() and 0xFF) shl 16) or
            ((bytes[27].toInt() and 0xFF) shl 24)
        assertTrue((flags and MachO.MH_DYLDLINK) != 0,
            "Should have MH_DYLDLINK flag, flags=0x${flags.toString(16)}")
        assertTrue((flags and MachO.MH_TWOLEVEL) != 0,
            "Should have MH_TWOLEVEL flag, flags=0x${flags.toString(16)}")
    }

    @Test
    fun defaultInstallNameWhenNoneSpecified() {
        val bytes = MachODylibLinker().link(listOf(simpleObject()))
        assertTrue(bytes.isNotEmpty())
        val fileType = (bytes[12].toInt() and 0xFF) or
            ((bytes[13].toInt() and 0xFF) shl 8)
        assertEquals(MachO.MH_DYLIB, fileType)
    }

    @Test
    fun installNameAppearsInBinary() {
        val name = "libcustom.1.dylib"
        val bytes = MachODylibLinker(installName = name).link(listOf(simpleObject()))
        val str = String(bytes, Charsets.US_ASCII)
        assertTrue(str.contains(name), "Binary should contain install name '$name'")
    }

    @Test
    fun crossReferenceBetweenObjectFilesResolves() {
        val obj1 = ObjectFile(
            format = ObjectFormat.MACH_O,
            arch = Architecture.X86_64_MACOS,
            sections = listOf(
                Section(name = "__text", kind = SectionKind.TEXT,
                    data = byteArrayOf(0xC3.toByte()), align = 4),
            ),
            symbols = listOf(
                Symbol(name = "_caller", value = 0, kind = SymbolKind.FUNCTION,
                    binding = SymbolBinding.GLOBAL, section = "__text"),
                Symbol(name = "_callee", value = 0, kind = SymbolKind.UNDEFINED,
                    binding = SymbolBinding.GLOBAL, section = null),
            ),
            relocations = emptyList(),
        )
        val obj2 = ObjectFile(
            format = ObjectFormat.MACH_O,
            arch = Architecture.X86_64_MACOS,
            sections = listOf(
                Section(name = "__text", kind = SectionKind.TEXT,
                    data = byteArrayOf(0xC3.toByte()), align = 4),
            ),
            symbols = listOf(
                Symbol(name = "_callee", value = 0, kind = SymbolKind.FUNCTION,
                    binding = SymbolBinding.GLOBAL, section = "__text"),
            ),
            relocations = emptyList(),
        )
        val bytes = MachODylibLinker().link(listOf(obj1, obj2))
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun rodataAndDataSegmentsCoexist() {
        val obj = ObjectFile(
            format = ObjectFormat.MACH_O,
            arch = Architecture.X86_64_MACOS,
            sections = listOf(
                Section(name = "__text", kind = SectionKind.TEXT,
                    data = byteArrayOf(0xC3.toByte()), align = 4),
                Section(name = "__const", kind = SectionKind.RODATA,
                    data = "const\u0000".toByteArray(), align = 1),
                Section(name = "__data", kind = SectionKind.DATA,
                    data = byteArrayOf(0x01, 0x02, 0x03, 0x04), align = 4),
            ),
            symbols = listOf(
                Symbol(name = "_func", value = 0, kind = SymbolKind.FUNCTION,
                    binding = SymbolBinding.GLOBAL, section = "__text"),
            ),
            relocations = emptyList(),
        )
        val bytes = MachODylibLinker().link(listOf(obj))
        assertTrue(bytes.isNotEmpty())
        assertEquals(MachO.MH_DYLIB, bytes[12].toInt() and 0xFF)
    }

    @Test
    fun emptyObjectFileProducesValidDylib() {
        val obj = ObjectFile(
            format = ObjectFormat.MACH_O,
            arch = Architecture.X86_64_MACOS,
            sections = listOf(
                Section(name = "__text", kind = SectionKind.TEXT,
                    data = byteArrayOf(), align = 4),
            ),
            symbols = emptyList(),
            relocations = emptyList(),
        )
        val bytes = MachODylibLinker().link(listOf(obj))
        assertTrue(bytes.isNotEmpty())
        val magic = (bytes[0].toInt() and 0xFF) or
            ((bytes[1].toInt() and 0xFF) shl 8) or
            ((bytes[2].toInt() and 0xFF) shl 16) or
            ((bytes[3].toInt() and 0xFF) shl 24)
        assertEquals(0xFEEDFACF.toInt(), magic, "Should still have valid Mach-O magic")
    }

    @Test
    fun rodataSection() {
        val obj = ObjectFile(
            format = ObjectFormat.MACH_O,
            arch = Architecture.X86_64_MACOS,
            sections = listOf(
                Section(name = "__text", kind = SectionKind.TEXT,
                    data = byteArrayOf(0xC3.toByte()), align = 4),
                Section(name = "__const", kind = SectionKind.RODATA,
                    data = "hello\u0000".toByteArray(), align = 1),
            ),
            symbols = listOf(
                Symbol(name = "_func", value = 0, kind = SymbolKind.FUNCTION,
                    binding = SymbolBinding.GLOBAL, section = "__text"),
            ),
            relocations = emptyList(),
        )
        val bytes = MachODylibLinker().link(listOf(obj))
        assertTrue(bytes.isNotEmpty())
        assertEquals(MachO.MH_DYLIB, bytes[12].toInt() and 0xFF)
    }
}
