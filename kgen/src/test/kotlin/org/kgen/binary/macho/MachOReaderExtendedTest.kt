package org.kgen.binary.macho

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.binary.*
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MachOReaderExtendedTest {

    private fun writeFixedString(buf: ByteBuffer, s: String, maxLen: Int) {
        val bytes = s.toByteArray(Charsets.US_ASCII)
        for (i in 0 until maxLen) {
            buf.put(if (i < bytes.size) bytes[i] else 0)
        }
    }

    private fun buildMachO64Object(
        code: ByteArray = byteArrayOf(0xC3.toByte()),
        extraSections: List<Triple<String, String, ByteArray>> = emptyList(),
        symbols: List<Triple<String, Int, Boolean>> = listOf(Triple("_main", 0, true)),
    ): ByteArray {
        val strtab = ByteArrayOutputStream()
        strtab.write(0)
        val symOffsets = mutableListOf<Int>()
        for ((name, _, _) in symbols) {
            symOffsets.add(strtab.size())
            strtab.write("$name\u0000".toByteArray(Charsets.US_ASCII))
        }
        val strtabBytes = strtab.toByteArray()

        val allSections = mutableListOf(Triple("__text", "__TEXT", code))
        allSections.addAll(extraSections)
        val nsects = allSections.size

        val headerSize = 32
        val segCmdSize = 72 + 80 * nsects
        val symtabCmdSize = 24
        val loadCmdsSize = segCmdSize + symtabCmdSize

        var dataOffset = headerSize + loadCmdsSize
        val sectionOffsets = mutableListOf<Int>()
        for ((_, _, data) in allSections) {
            sectionOffsets.add(dataOffset)
            dataOffset += data.size
        }
        val symtabOff = ((dataOffset + 7) / 8) * 8
        val strtabOff = symtabOff + 16 * symbols.size
        val totalSize = strtabOff + strtabBytes.size

        val buf = ByteBuffer.allocate(totalSize + 256).order(ByteOrder.LITTLE_ENDIAN)

        // Header
        buf.putInt(MachO.MH_MAGIC_64.toInt())
        buf.putInt(MachO.CPU_TYPE_X86_64)
        buf.putInt(MachO.CPU_SUBTYPE_ALL)
        buf.putInt(MachO.MH_OBJECT)
        buf.putInt(2)
        buf.putInt(loadCmdsSize)
        buf.putInt(0)
        buf.putInt(0)

        // LC_SEGMENT_64
        buf.putInt(MachO.LC_SEGMENT_64)
        buf.putInt(segCmdSize)
        for (i in 0 until 16) buf.put(0)
        buf.putLong(0)
        val totalDataSize = allSections.sumOf { it.third.size }.toLong()
        buf.putLong(totalDataSize)
        buf.putLong((headerSize + loadCmdsSize).toLong())
        buf.putLong(totalDataSize)
        buf.putInt(7)
        buf.putInt(5)
        buf.putInt(nsects)
        buf.putInt(0)

        var addr = 0L
        for (i in allSections.indices) {
            val (sectName, segName, data) = allSections[i]
            writeFixedString(buf, sectName, 16)
            writeFixedString(buf, segName, 16)
            buf.putLong(addr)
            buf.putLong(data.size.toLong())
            buf.putInt(sectionOffsets[i])
            buf.putInt(0)
            buf.putInt(0)
            buf.putInt(0)
            val flags = if (sectName == "__text") MachO.S_REGULAR or MachO.S_ATTR_PURE_INSTRUCTIONS
                else if (sectName == "__cstring") MachO.S_CSTRING_LITERALS
                else MachO.S_REGULAR
            buf.putInt(flags)
            buf.putInt(0); buf.putInt(0); buf.putInt(0)
            addr += data.size
        }

        // LC_SYMTAB
        buf.putInt(MachO.LC_SYMTAB)
        buf.putInt(symtabCmdSize)
        buf.putInt(symtabOff)
        buf.putInt(symbols.size)
        buf.putInt(strtabOff)
        buf.putInt(strtabBytes.size)

        val result = ByteArray(totalSize)
        buf.flip()
        val headerBytes = minOf(buf.remaining(), totalSize)
        buf.get(result, 0, headerBytes)

        // Section data
        for (i in allSections.indices) {
            System.arraycopy(allSections[i].third, 0, result, sectionOffsets[i], allSections[i].third.size)
        }

        // Symbols
        for (i in symbols.indices) {
            val (_, sectIdx, isExt) = symbols[i]
            val symBuf = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
            symBuf.putInt(symOffsets[i])
            val nType = MachO.N_SECT or (if (isExt) MachO.N_EXT else 0)
            symBuf.put(nType.toByte())
            symBuf.put((sectIdx + 1).toByte()) // 1-based
            symBuf.putShort(0)
            symBuf.putLong(0)
            symBuf.flip()
            symBuf.get(result, symtabOff + i * 16, 16)
        }

        // String table
        System.arraycopy(strtabBytes, 0, result, strtabOff, strtabBytes.size)

        return result
    }

    @Test
    fun canReadDetectsMachOMagic() {
        assertTrue(MachOReader.canRead(buildMachO64Object()))
    }

    @Test
    fun canReadRejectsNonMachO() {
        assertFalse(MachOReader.canRead(byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0)))
    }

    @Test
    fun canReadRejectsElfMagic() {
        assertFalse(MachOReader.canRead(byteArrayOf(0x7f, 0x45, 0x4c, 0x46)))
    }

    @Test
    fun canReadRejectsPeMagic() {
        assertFalse(MachOReader.canRead(byteArrayOf(0x4d, 0x5a, 0x00, 0x00)))
    }

    @Test
    fun canReadRejectsEmptyBytes() {
        assertFalse(MachOReader.canRead(byteArrayOf()))
    }

    @Test
    fun canReadRejectsTooShort() {
        assertFalse(MachOReader.canRead(byteArrayOf(0xCF.toByte(), 0xFA.toByte())))
    }

    @Test
    fun readsHeaderIs64Bit() {
        val macho = MachOReader.read(buildMachO64Object())
        assertTrue(macho.header.is64Bit)
    }

    @Test
    fun readsHeaderCpuType() {
        val macho = MachOReader.read(buildMachO64Object())
        assertEquals(MachO.CPU_TYPE_X86_64, macho.header.cpuType)
    }

    @Test
    fun readsHeaderFileType() {
        val macho = MachOReader.read(buildMachO64Object())
        assertEquals(MachO.MH_OBJECT, macho.header.fileType)
        assertTrue(macho.isObject)
    }

    @Test
    fun readsSegmentCount() {
        val macho = MachOReader.read(buildMachO64Object())
        assertEquals(1, macho.segments.size)
    }

    @Test
    fun readsTextSection() {
        val macho = MachOReader.read(buildMachO64Object())
        val text = macho.allSections.first { it.sectionName == "__text" }
        assertEquals("__TEXT", text.segmentName)
        assertTrue(text.isPureInstructions)
    }

    @Test
    fun readsTextSectionData() {
        val code = byteArrayOf(
            0xB8.toByte(), 0x2A, 0x00, 0x00, 0x00, // mov eax, 42
            0xC3.toByte()                             // ret
        )
        val macho = MachOReader.read(buildMachO64Object(code = code))
        val text = macho.allSections.first { it.sectionName == "__text" }
        assertEquals(code.size, text.data.size)
        assertEquals(0xB8.toByte(), text.data[0])
        assertEquals(0xC3.toByte(), text.data[5])
    }

    @Test
    fun readsCstringSection() {
        val cstringData = "Hello, World!\u0000".toByteArray(Charsets.US_ASCII)
        val macho = MachOReader.read(buildMachO64Object(
            extraSections = listOf(Triple("__cstring", "__TEXT", cstringData))
        ))
        val cstring = macho.allSections.first { it.sectionName == "__cstring" }
        assertEquals(cstringData.size, cstring.data.size)
        val str = String(cstring.data, 0, cstring.data.size - 1, Charsets.US_ASCII)
        assertEquals("Hello, World!", str)
    }

    @Test
    fun readsMultipleSections() {
        val dataBytes = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val macho = MachOReader.read(buildMachO64Object(
            extraSections = listOf(Triple("__data", "__DATA", dataBytes))
        ))
        assertEquals(2, macho.allSections.size)
        assertTrue(macho.allSections.any { it.sectionName == "__text" })
        assertTrue(macho.allSections.any { it.sectionName == "__data" })
    }

    @Test
    fun readsSymbolName() {
        val macho = MachOReader.read(buildMachO64Object())
        assertEquals(1, macho.symbols.size)
        assertEquals("_main", macho.symbols[0].name)
    }

    @Test
    fun readsExternalSymbol() {
        val macho = MachOReader.read(buildMachO64Object())
        assertTrue(macho.symbols[0].isExternal)
        assertTrue(macho.symbols[0].isInSection)
    }

    @Test
    fun readsMultipleSymbols() {
        val code = byteArrayOf(
            0xB8.toByte(), 0x2A, 0x00, 0x00, 0x00, 0xC3.toByte(), // mov eax,42; ret
            0xB8.toByte(), 0x07, 0x00, 0x00, 0x00, 0xC3.toByte()  // mov eax,7; ret
        )
        val macho = MachOReader.read(buildMachO64Object(
            code = code,
            symbols = listOf(
                Triple("_foo", 0, true),
                Triple("_bar", 0, true),
            )
        ))
        assertEquals(2, macho.symbols.size)
        val names = macho.symbols.map { it.name }.toSet()
        assertTrue("_foo" in names)
        assertTrue("_bar" in names)
    }

    @Test
    fun projectsToObjectFile() {
        val obj = MachOReader.toObjectFile(MachOReader.read(buildMachO64Object()))
        assertEquals(ObjectFormat.MACH_O, obj.format)
        assertEquals(ArchType.X86_64, obj.arch.arch)
    }

    @Test
    fun objectFileHasTextSection() {
        val obj = MachOReader.toObjectFile(MachOReader.read(buildMachO64Object()))
        assertTrue(obj.sections.any { it.name == "__text" })
    }

    @Test
    fun objectFileSectionKindText() {
        val obj = MachOReader.toObjectFile(MachOReader.read(buildMachO64Object()))
        val text = obj.sections.first { it.name == "__text" }
        assertEquals(SectionKind.TEXT, text.kind)
    }

    @Test
    fun objectFileSectionKindRodata() {
        val cstring = "data\u0000".toByteArray(Charsets.US_ASCII)
        val obj = MachOReader.toObjectFile(MachOReader.read(buildMachO64Object(
            extraSections = listOf(Triple("__cstring", "__TEXT", cstring))
        )))
        val section = obj.sections.firstOrNull { it.name == "__cstring" }
        assertNotNull(section)
        assertEquals(SectionKind.RODATA, section!!.kind)
    }

    @Test
    fun objectFileHasSymbol() {
        val obj = MachOReader.toObjectFile(MachOReader.read(buildMachO64Object()))
        assertTrue(obj.symbols.any { it.name == "_main" })
    }

    @Test
    fun objectFileIsRelocatable() {
        val obj = MachOReader.toObjectFile(MachOReader.read(buildMachO64Object()))
        assertTrue(obj.metadata.flags.contains(ObjectFlag.RELOCATABLE))
    }

    @Test
    fun detectFormatIdentifiesMachO() {
        assertEquals(ObjectFormat.MACH_O, detectFormat(buildMachO64Object()))
    }

    @Test
    fun machOObjectFileReaderInterface() {
        val reader = MachOObjectFileReader()
        assertEquals(ObjectFormat.MACH_O, reader.format)
        val bytes = buildMachO64Object()
        assertTrue(reader.canRead(bytes))
        val obj = reader.read(bytes)
        assertEquals(ObjectFormat.MACH_O, obj.format)
    }

    @Test
    fun textSectionContainsActualCode() {
        val code = byteArrayOf(
            0x55,                                    // push rbp
            0x48, 0x89.toByte(), 0xE5.toByte(),     // mov rbp, rsp
            0xB8.toByte(), 0x2A, 0x00, 0x00, 0x00,  // mov eax, 42
            0x5D,                                    // pop rbp
            0xC3.toByte()                            // ret
        )
        val macho = MachOReader.read(buildMachO64Object(code = code))
        val text = macho.allSections.first { it.sectionName == "__text" }
        assertEquals(code.size, text.data.size)
        assertArrayEquals(code, text.data)
    }

    @Test
    fun emptyCodeSection() {
        val macho = MachOReader.read(buildMachO64Object(code = byteArrayOf()))
        val text = macho.allSections.first { it.sectionName == "__text" }
        assertEquals(0, text.data.size)
    }

    @Test
    fun symbolSectionIndex() {
        val macho = MachOReader.read(buildMachO64Object())
        val sym = macho.symbols[0]
        assertEquals(1, sym.sectionIndex)
    }

    @Test
    fun symbolInDataSection() {
        val dataBytes = byteArrayOf(0x2A, 0x00, 0x00, 0x00)
        val macho = MachOReader.read(buildMachO64Object(
            extraSections = listOf(Triple("__data", "__DATA", dataBytes)),
            symbols = listOf(
                Triple("_main", 0, true),
                Triple("_myvar", 1, true),
            )
        ))
        assertEquals(2, macho.symbols.size)
        val myvar = macho.symbols.first { it.name == "_myvar" }
        assertEquals(2, myvar.sectionIndex) // __data is section 2 (1-based)
    }

    @Test
    fun largeSectionData() {
        val largeCode = ByteArray(4096) { (it % 256).toByte() }
        val macho = MachOReader.read(buildMachO64Object(code = largeCode))
        val text = macho.allSections.first { it.sectionName == "__text" }
        assertEquals(4096, text.data.size)
        assertArrayEquals(largeCode, text.data)
    }
}
