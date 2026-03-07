package org.kgen.binary

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.binary.elf.ElfObjectWriter
import org.kgen.binary.pe.CoffObjectWriter
import org.kgen.binary.macho.MachOObjectWriter

class FormatDetectionExtendedTest {

    // --- ELF variants ---

    @Test
    fun detectsElf64LittleEndian() {
        val bytes = byteArrayOf(0x7f, 0x45, 0x4c, 0x46, 0x02, 0x01)
        assertEquals(ObjectFormat.ELF, detectFormat(bytes))
    }

    @Test
    fun detectsElf32BigEndian() {
        val bytes = byteArrayOf(0x7f, 0x45, 0x4c, 0x46, 0x01, 0x02)
        assertEquals(ObjectFormat.ELF, detectFormat(bytes))
    }

    @Test
    fun detectsElfWithTrailingData() {
        val bytes = ByteArray(1024)
        bytes[0] = 0x7f
        bytes[1] = 0x45
        bytes[2] = 0x4c
        bytes[3] = 0x46
        assertEquals(ObjectFormat.ELF, detectFormat(bytes))
    }

    // --- PE/COFF variants ---

    @Test
    fun detectsPeWithExtendedHeader() {
        val bytes = ByteArray(256)
        bytes[0] = 'M'.code.toByte()
        bytes[1] = 'Z'.code.toByte()
        assertEquals(ObjectFormat.PE_COFF, detectFormat(bytes))
    }

    @Test
    fun detectsCoffAmd64WithSections() {
        val bytes = ByteArray(64)
        bytes[0] = 0x64
        bytes[1] = 0x86.toByte()
        assertEquals(ObjectFormat.PE_COFF, detectFormat(bytes))
    }

    @Test
    fun detectsCoffArm64() {
        val bytes = byteArrayOf(0x64, 0xAA.toByte(), 0x00, 0x00, 0x00, 0x00)
        assertEquals(ObjectFormat.PE_COFF, detectFormat(bytes))
    }

    @Test
    fun detectsCoffi386() {
        val bytes = byteArrayOf(0x4c, 0x01, 0x00, 0x00, 0x00, 0x00)
        assertEquals(ObjectFormat.PE_COFF, detectFormat(bytes))
    }

    // --- Mach-O variants ---

    @Test
    fun detectsMachO64LE() {
        val bytes = byteArrayOf(0xCF.toByte(), 0xFA.toByte(), 0xED.toByte(), 0xFE.toByte())
        assertEquals(ObjectFormat.MACH_O, detectFormat(bytes))
    }

    @Test
    fun detectsMachO64BE() {
        val bytes = byteArrayOf(0xFE.toByte(), 0xED.toByte(), 0xFA.toByte(), 0xCF.toByte())
        assertEquals(ObjectFormat.MACH_O, detectFormat(bytes))
    }

    @Test
    fun detectsMachO32LE() {
        val bytes = byteArrayOf(0xCE.toByte(), 0xFA.toByte(), 0xED.toByte(), 0xFE.toByte())
        assertEquals(ObjectFormat.MACH_O, detectFormat(bytes))
    }

    @Test
    fun detectsMachO32BE() {
        val bytes = byteArrayOf(0xFE.toByte(), 0xED.toByte(), 0xFA.toByte(), 0xCE.toByte())
        assertEquals(ObjectFormat.MACH_O, detectFormat(bytes))
    }

    @Test
    fun detectsMachOWithTrailingData() {
        val bytes = ByteArray(512)
        bytes[0] = 0xCF.toByte()
        bytes[1] = 0xFA.toByte()
        bytes[2] = 0xED.toByte()
        bytes[3] = 0xFE.toByte()
        assertEquals(ObjectFormat.MACH_O, detectFormat(bytes))
    }

    // --- WASM ---

    @Test
    fun detectsWasmWithVersion() {
        val bytes = byteArrayOf(0x00, 0x61, 0x73, 0x6D, 0x01, 0x00, 0x00, 0x00)
        assertEquals(ObjectFormat.WASM_MODULE, detectFormat(bytes))
    }

    @Test
    fun detectsWasmMinimal() {
        val bytes = byteArrayOf(0x00, 0x61, 0x73, 0x6D)
        assertEquals(ObjectFormat.WASM_MODULE, detectFormat(bytes))
    }

    // --- JVM ---

    @Test
    fun detectsJvmClass() {
        val bytes = byteArrayOf(0xCA.toByte(), 0xFE.toByte(), 0xBA.toByte(), 0xBE.toByte())
        assertEquals(ObjectFormat.JVM_CLASS, detectFormat(bytes))
    }

    @Test
    fun detectsJvmClassWithVersion() {
        val bytes = byteArrayOf(
            0xCA.toByte(), 0xFE.toByte(), 0xBA.toByte(), 0xBE.toByte(),
            0x00, 0x00, 0x00, 0x3D // Java 17
        )
        assertEquals(ObjectFormat.JVM_CLASS, detectFormat(bytes))
    }

    // --- Negative cases ---

    @Test
    fun nullForEmptyArray() {
        assertNull(detectFormat(ByteArray(0)))
    }

    @Test
    fun nullForSingleByte() {
        assertNull(detectFormat(byteArrayOf(0x7f)))
    }

    @Test
    fun nullForTwoBytes() {
        assertNull(detectFormat(byteArrayOf(0x7f, 0x45)))
    }

    @Test
    fun nullForThreeBytes() {
        assertNull(detectFormat(byteArrayOf(0x7f, 0x45, 0x4c)))
    }

    @Test
    fun nullForAllZeros() {
        assertNull(detectFormat(byteArrayOf(0x00, 0x00, 0x00, 0x00)))
    }

    @Test
    fun nullForAllOnes() {
        assertNull(detectFormat(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())))
    }

    @Test
    fun nullForTextFile() {
        assertNull(detectFormat("Hello, World!".toByteArray()))
    }

    @Test
    fun nullForRandomBytes() {
        assertNull(detectFormat(byteArrayOf(0x12, 0x34, 0x56, 0x78.toByte())))
        assertNull(detectFormat(byteArrayOf(0xAB.toByte(), 0xCD.toByte(), 0xEF.toByte(), 0x01)))
    }

    @Test
    fun nullForAlmostElf() {
        assertNull(detectFormat(byteArrayOf(0x7f, 0x45, 0x4c, 0x47))) // 'G' instead of 'F'
    }

    @Test
    fun nullForAlmostWasm() {
        assertNull(detectFormat(byteArrayOf(0x00, 0x61, 0x73, 0x6E))) // 'n' instead of 'm'
    }

    // --- Round-trip with writers ---

    @Test
    fun elfWriterRoundTrip() {
        val obj = ObjectFile(
            format = ObjectFormat.ELF, arch = Architecture(ArchType.X86_64),
            sections = listOf(
                Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1),
                Section(".data", SectionKind.DATA, byteArrayOf(0x42), align = 4),
            ),
            symbols = listOf(Symbol("main", kind = SymbolKind.FUNCTION)),
            relocations = emptyList(),
        )
        assertEquals(ObjectFormat.ELF, detectFormat(ElfObjectWriter().write(obj)))
    }

    @Test
    fun coffWriterRoundTrip() {
        val obj = ObjectFile(
            format = ObjectFormat.PE_COFF, arch = Architecture.X86_64_WINDOWS,
            sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1)),
            symbols = emptyList(), relocations = emptyList(),
        )
        assertEquals(ObjectFormat.PE_COFF, detectFormat(CoffObjectWriter().write(obj)))
    }

    @Test
    fun machoWriterRoundTrip() {
        val obj = ObjectFile(
            format = ObjectFormat.MACH_O, arch = Architecture(ArchType.X86_64),
            sections = listOf(Section(".text", SectionKind.TEXT, byteArrayOf(0xC3.toByte()), align = 1)),
            symbols = emptyList(), relocations = emptyList(),
        )
        assertEquals(ObjectFormat.MACH_O, detectFormat(MachOObjectWriter().write(obj)))
    }

    // --- Mutual exclusivity ---

    @Test
    fun formatsAreMutuallyExclusive() {
        val magics = mapOf(
            ObjectFormat.ELF to byteArrayOf(0x7f, 0x45, 0x4c, 0x46),
            ObjectFormat.PE_COFF to byteArrayOf(0x4d, 0x5a, 0x00, 0x00),
            ObjectFormat.WASM_MODULE to byteArrayOf(0x00, 0x61, 0x73, 0x6d),
            ObjectFormat.JVM_CLASS to byteArrayOf(0xCA.toByte(), 0xFE.toByte(), 0xBA.toByte(), 0xBE.toByte()),
            ObjectFormat.MACH_O to byteArrayOf(0xCF.toByte(), 0xFA.toByte(), 0xED.toByte(), 0xFE.toByte()),
        )
        for ((expected, magic) in magics) {
            assertEquals(expected, detectFormat(magic), "Magic for $expected should detect correctly")
        }
    }

    // --- ObjectFormat enum coverage ---

    @Test
    fun objectFormatEnumValues() {
        val formats = ObjectFormat.entries
        assertTrue(formats.contains(ObjectFormat.ELF))
        assertTrue(formats.contains(ObjectFormat.PE_COFF))
        assertTrue(formats.contains(ObjectFormat.MACH_O))
        assertTrue(formats.contains(ObjectFormat.WASM_MODULE))
        assertTrue(formats.contains(ObjectFormat.JVM_CLASS))
    }

    // --- Large input ---

    @Test
    fun detectsFormatInLargeFile() {
        val bytes = ByteArray(1_000_000)
        bytes[0] = 0x7f
        bytes[1] = 0x45
        bytes[2] = 0x4c
        bytes[3] = 0x46
        assertEquals(ObjectFormat.ELF, detectFormat(bytes))
    }
}
