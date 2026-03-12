package org.kgen.binary.macho

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MachO32Test {

    private fun buildMachO32(
        cpuType: Int = 0x00000007, // i386
        fileType: Int = MachO.MH_OBJECT,
        sections: List<Triple<String, String, ByteArray>> = emptyList(),
        symbols: List<Triple<String, Int, Long>> = emptyList(), // name, type, value
    ): ByteArray {
        // Build string table
        val strtab = StringBuilder()
        strtab.append('\u0000') // null byte at index 0
        val symNameOffsets = mutableMapOf<String, Int>()
        for ((name, _, _) in symbols) {
            symNameOffsets[name] = strtab.length
            strtab.append(name)
            strtab.append('\u0000')
        }
        val strtabBytes = strtab.toString().toByteArray()

        // Calculate layout
        val headerSize = 28 // Mach-O 32-bit header
        val segCmdSize = 56 + sections.size * 68 // segment_command + sections
        val ncmds = (if (sections.isNotEmpty()) 1 else 0) + (if (symbols.isNotEmpty()) 1 else 0)
        val symtabCmdSize = if (symbols.isNotEmpty()) 24 else 0
        val cmdsTotalSize = (if (sections.isNotEmpty()) segCmdSize else 0) + symtabCmdSize

        var fileOff = headerSize + cmdsTotalSize
        // Pad to 4-byte alignment
        fileOff = (fileOff + 3) and 3.inv()

        val sectionFileOffsets = mutableListOf<Int>()
        for ((_, _, data) in sections) {
            sectionFileOffsets.add(fileOff)
            fileOff += data.size
            fileOff = (fileOff + 3) and 3.inv()
        }

        val symtabOff = fileOff
        val symEntrySize = 12 // nlist 32-bit
        fileOff += symbols.size * symEntrySize

        val strtabOff = fileOff
        fileOff += strtabBytes.size

        val totalSize = fileOff
        val buf = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)

        // Write header
        buf.putInt(0, MachO.MH_MAGIC_32.toInt()) // magic (written as LE → on disk: CE FA ED FE)
        buf.putInt(4, cpuType)
        buf.putInt(8, 0) // subtype
        buf.putInt(12, fileType)
        buf.putInt(16, ncmds)
        buf.putInt(20, cmdsTotalSize)
        buf.putInt(24, 0) // flags

        var cmdOff = headerSize

        // Write segment command if we have sections
        if (sections.isNotEmpty()) {
            buf.putInt(cmdOff, MachO.LC_SEGMENT) // cmd
            buf.putInt(cmdOff + 4, segCmdSize) // cmdsize
            writeFixedString(buf, cmdOff + 8, "__TEXT", 16)
            buf.putInt(cmdOff + 24, 0) // vmaddr
            buf.putInt(cmdOff + 28, 0x1000) // vmsize
            buf.putInt(cmdOff + 32, 0) // fileoff
            buf.putInt(cmdOff + 36, totalSize) // filesize
            buf.putInt(cmdOff + 40, 7) // maxprot (rwx)
            buf.putInt(cmdOff + 44, 5) // initprot (rx)
            buf.putInt(cmdOff + 48, sections.size) // nsects
            buf.putInt(cmdOff + 52, 0) // flags

            var sectOff = cmdOff + 56
            for ((i, sec) in sections.withIndex()) {
                val (sectName, segName, data) = sec
                writeFixedString(buf, sectOff, sectName, 16)
                writeFixedString(buf, sectOff + 16, segName, 16)
                buf.putInt(sectOff + 32, 0) // addr
                buf.putInt(sectOff + 36, data.size) // size
                buf.putInt(sectOff + 40, sectionFileOffsets[i]) // offset
                buf.putInt(sectOff + 44, 2) // align (2^2 = 4)
                buf.putInt(sectOff + 48, 0) // reloff
                buf.putInt(sectOff + 52, 0) // nreloc
                buf.putInt(sectOff + 56, if (sectName == "__text") MachO.S_ATTR_PURE_INSTRUCTIONS else 0) // flags
                buf.putInt(sectOff + 60, 0) // reserved1
                buf.putInt(sectOff + 64, 0) // reserved2
                sectOff += 68
            }

            cmdOff += segCmdSize
        }

        // Write symtab command if we have symbols
        if (symbols.isNotEmpty()) {
            buf.putInt(cmdOff, MachO.LC_SYMTAB)
            buf.putInt(cmdOff + 4, 24) // cmdsize
            buf.putInt(cmdOff + 8, symtabOff) // symoff
            buf.putInt(cmdOff + 12, symbols.size) // nsyms
            buf.putInt(cmdOff + 16, strtabOff) // stroff
            buf.putInt(cmdOff + 20, strtabBytes.size) // strsize
        }

        // Write section data
        for ((i, sec) in sections.withIndex()) {
            System.arraycopy(sec.third, 0, buf.array(), sectionFileOffsets[i], sec.third.size)
        }

        // Write symbols (nlist 32-bit: 12 bytes each)
        for ((i, sym) in symbols.withIndex()) {
            val off = symtabOff + i * 12
            buf.putInt(off, symNameOffsets[sym.first]!!) // n_strx
            buf.array()[off + 4] = (sym.second and 0xFF).toByte() // n_type
            buf.array()[off + 5] = 0 // n_sect
            buf.putShort(off + 6, 0) // n_desc
            buf.putInt(off + 8, sym.third.toInt()) // n_value (32-bit)
        }

        // Write string table
        System.arraycopy(strtabBytes, 0, buf.array(), strtabOff, strtabBytes.size)

        return buf.array()
    }

    private fun writeFixedString(buf: ByteBuffer, offset: Int, str: String, maxLen: Int) {
        val bytes = str.toByteArray()
        for (i in 0 until minOf(bytes.size, maxLen)) {
            buf.array()[offset + i] = bytes[i]
        }
    }

    @Test
    fun `reads 32-bit Mach-O header`() {
        val bytes = buildMachO32()
        assertTrue(MachO.isMachO(bytes))
        val macho = MachOReader.read(bytes)
        assertFalse(macho.header.is64Bit)
        assertEquals(0x00000007, macho.header.cpuType) // i386
        assertEquals(MachO.MH_OBJECT, macho.header.fileType)
    }

    @Test
    fun `reads 32-bit Mach-O sections`() {
        val code = byteArrayOf(0xCC.toByte(), 0x90.toByte(), 0xC3.toByte(), 0x00)
        val bytes = buildMachO32(
            sections = listOf(Triple("__text", "__TEXT", code)),
        )
        val macho = MachOReader.read(bytes)

        assertEquals(1, macho.segments.size)
        assertEquals("__TEXT", macho.segments[0].name)
        assertEquals(1, macho.segments[0].sections.size)

        val sect = macho.segments[0].sections[0]
        assertEquals("__text", sect.sectionName)
        assertEquals("__TEXT", sect.segmentName)
        assertEquals(code.size.toLong(), sect.size)
        assertArrayEquals(code, sect.data)
    }

    @Test
    fun `reads 32-bit Mach-O symbols`() {
        val bytes = buildMachO32(
            symbols = listOf(
                Triple("_main", MachO.N_SECT or MachO.N_EXT, 0x1000L),
                Triple("_helper", MachO.N_SECT, 0x2000L),
            ),
        )
        val macho = MachOReader.read(bytes)

        assertEquals(2, macho.symbols.size)
        assertEquals("_main", macho.symbols[0].name)
        assertTrue(macho.symbols[0].isExternal)
        assertEquals(0x1000L, macho.symbols[0].value)

        assertEquals("_helper", macho.symbols[1].name)
        assertFalse(macho.symbols[1].isExternal)
        assertEquals(0x2000L, macho.symbols[1].value)
    }

    @Test
    fun `reads 32-bit Mach-O with sections and symbols`() {
        val code = byteArrayOf(0xCC.toByte(), 0x90.toByte())
        val bytes = buildMachO32(
            sections = listOf(Triple("__text", "__TEXT", code)),
            symbols = listOf(Triple("_start", MachO.N_SECT or MachO.N_EXT, 0L)),
        )
        val macho = MachOReader.read(bytes)

        assertEquals(1, macho.segments.size)
        assertEquals(1, macho.symbols.size)
        assertEquals("_start", macho.symbols[0].name)
        assertEquals(code.size.toLong(), macho.allSections[0].size)
    }

    @Test
    fun `reads multiple 32-bit sections`() {
        val textData = byteArrayOf(0x90.toByte(), 0xC3.toByte())
        val dataData = byteArrayOf(0x42, 0x00, 0x00, 0x00)
        val bytes = buildMachO32(
            sections = listOf(
                Triple("__text", "__TEXT", textData),
                Triple("__data", "__TEXT", dataData),
            ),
        )
        val macho = MachOReader.read(bytes)

        assertEquals(2, macho.allSections.size)
        assertEquals("__text", macho.allSections[0].sectionName)
        assertEquals("__data", macho.allSections[1].sectionName)
        assertEquals(textData.size.toLong(), macho.allSections[0].size)
        assertEquals(dataData.size.toLong(), macho.allSections[1].size)
    }

    @Test
    fun `32-bit Mach-O isObject check`() {
        val objBytes = buildMachO32(fileType = MachO.MH_OBJECT)
        val execBytes = buildMachO32(fileType = MachO.MH_EXECUTE)

        assertTrue(MachOReader.read(objBytes).isObject)
        assertFalse(MachOReader.read(objBytes).isExecutable)
        assertTrue(MachOReader.read(execBytes).isExecutable)
        assertFalse(MachOReader.read(execBytes).isObject)
    }

    @Test
    fun `canRead accepts 32-bit Mach-O`() {
        val bytes = buildMachO32()
        assertTrue(MachOReader.canRead(bytes))
    }
}
