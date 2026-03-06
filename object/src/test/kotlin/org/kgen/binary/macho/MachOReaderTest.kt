package org.kgen.binary.macho

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.binary.*
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MachOReaderTest {

    private fun buildMinimalMachO64Object(): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteBuffer.allocate(4096).order(ByteOrder.LITTLE_ENDIAN)

        // Code: ret (0xC3)
        val textCode = byteArrayOf(0xC3.toByte())
        val stringData = "hello\u0000".toByteArray(Charsets.US_ASCII)

        // String table: \0_main\0
        val strtab = ByteArrayOutputStream()
        strtab.write(0) // first byte is always null
        val mainNameOff = strtab.size()
        strtab.write("_main\u0000".toByteArray(Charsets.US_ASCII))
        val strtabBytes = strtab.toByteArray()

        // Layout:
        // Header: 32 bytes
        // LC_SEGMENT_64: 72 + 80*2 = 232 bytes (2 sections: __text, __cstring)
        // LC_SYMTAB: 24 bytes
        // Total load commands: 256 bytes
        // Sections data starts at: 32 + 256 = 288, align to 8 = 288
        // __text at 288, size 1
        // __cstring at 289, size 6
        // symtab at 296 (aligned), 1 symbol * 16 = 16 bytes
        // strtab at 312, size = strtab.size

        val headerSize = 32
        val segCmdSize = 72 + 80 * 2 // 232
        val symtabCmdSize = 24
        val loadCmdsSize = segCmdSize + symtabCmdSize // 256

        val dataStart = headerSize + loadCmdsSize // 288
        val textOff = dataStart
        val cstringOff = textOff + textCode.size // 289
        val symtabOff = ((cstringOff + stringData.size + 7) / 8) * 8 // align to 8
        val strtabOff = symtabOff + 16 // 1 symbol
        val totalSize = strtabOff + strtabBytes.size

        // Header
        buf.putInt(MachO.MH_MAGIC_64.toInt())
        buf.putInt(MachO.CPU_TYPE_X86_64)
        buf.putInt(MachO.CPU_SUBTYPE_ALL)
        buf.putInt(MachO.MH_OBJECT)
        buf.putInt(2) // ncmds
        buf.putInt(loadCmdsSize) // sizeofcmds
        buf.putInt(0) // flags
        buf.putInt(0) // reserved (64-bit)

        // LC_SEGMENT_64
        buf.putInt(MachO.LC_SEGMENT_64)
        buf.putInt(segCmdSize) // cmdsize
        // segname: 16 bytes, empty for object files
        for (i in 0 until 16) buf.put(0)
        buf.putLong(0) // vmaddr
        buf.putLong(textCode.size.toLong() + stringData.size) // vmsize
        buf.putLong(dataStart.toLong()) // fileoff
        buf.putLong(textCode.size.toLong() + stringData.size) // filesize
        buf.putInt(7) // maxprot (rwx)
        buf.putInt(5) // initprot (r-x)
        buf.putInt(2) // nsects
        buf.putInt(0) // flags

        // Section 1: __text
        writeFixedString(buf, "__text", 16)
        writeFixedString(buf, "__TEXT", 16)
        buf.putLong(0) // addr
        buf.putLong(textCode.size.toLong()) // size
        buf.putInt(textOff) // offset
        buf.putInt(0) // align (2^0 = 1)
        buf.putInt(0) // reloff
        buf.putInt(0) // nreloc
        buf.putInt(MachO.S_REGULAR or MachO.S_ATTR_PURE_INSTRUCTIONS) // flags
        buf.putInt(0) // reserved1
        buf.putInt(0) // reserved2
        buf.putInt(0) // reserved3 (64-bit only)

        // Section 2: __cstring
        writeFixedString(buf, "__cstring", 16)
        writeFixedString(buf, "__TEXT", 16)
        buf.putLong(textCode.size.toLong()) // addr
        buf.putLong(stringData.size.toLong()) // size
        buf.putInt(cstringOff) // offset
        buf.putInt(0) // align
        buf.putInt(0) // reloff
        buf.putInt(0) // nreloc
        buf.putInt(MachO.S_CSTRING_LITERALS) // flags
        buf.putInt(0); buf.putInt(0); buf.putInt(0) // reserved

        // LC_SYMTAB
        buf.putInt(MachO.LC_SYMTAB)
        buf.putInt(symtabCmdSize)
        buf.putInt(symtabOff) // symoff
        buf.putInt(1) // nsyms
        buf.putInt(strtabOff) // stroff
        buf.putInt(strtabBytes.size) // strsize

        // Now write section data at correct offsets
        val result = ByteArray(totalSize)
        buf.flip()
        buf.get(result, 0, buf.remaining())

        // __text data
        System.arraycopy(textCode, 0, result, textOff, textCode.size)
        // __cstring data
        System.arraycopy(stringData, 0, result, cstringOff, stringData.size)
        // symbol table: 1 nlist_64 entry (16 bytes)
        val symBuf = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
        symBuf.putInt(mainNameOff) // n_strx
        symBuf.put((MachO.N_SECT or MachO.N_EXT).toByte()) // n_type
        symBuf.put(1.toByte()) // n_sect (1-based, section 1 = __text)
        symBuf.putShort(0) // n_desc
        symBuf.putLong(0) // n_value
        symBuf.flip()
        symBuf.get(result, symtabOff, 16)
        // string table
        System.arraycopy(strtabBytes, 0, result, strtabOff, strtabBytes.size)

        return result
    }

    private fun writeFixedString(buf: ByteBuffer, s: String, maxLen: Int) {
        val bytes = s.toByteArray(Charsets.US_ASCII)
        for (i in 0 until maxLen) {
            buf.put(if (i < bytes.size) bytes[i] else 0)
        }
    }

    @Test
    fun `canRead detects Mach-O magic`() {
        val bytes = buildMinimalMachO64Object()
        assertTrue(MachOReader.canRead(bytes))
    }

    @Test
    fun `canRead rejects non-MachO`() {
        assertFalse(MachOReader.canRead(byteArrayOf(0, 0, 0, 0)))
        assertFalse(MachOReader.canRead(byteArrayOf(0x7f, 0x45, 0x4c, 0x46))) // ELF
    }

    @Test
    fun `reads header`() {
        val macho = MachOReader.read(buildMinimalMachO64Object())
        assertTrue(macho.header.is64Bit)
        assertEquals(MachO.CPU_TYPE_X86_64, macho.header.cpuType)
        assertEquals(MachO.MH_OBJECT, macho.header.fileType)
        assertTrue(macho.isObject)
    }

    @Test
    fun `reads segments and sections`() {
        val macho = MachOReader.read(buildMinimalMachO64Object())
        assertEquals(1, macho.segments.size)
        val sections = macho.allSections
        assertEquals(2, sections.size)
        assertEquals("__text", sections[0].sectionName)
        assertEquals("__TEXT", sections[0].segmentName)
        assertEquals("__cstring", sections[1].sectionName)
    }

    @Test
    fun `reads text section data`() {
        val macho = MachOReader.read(buildMinimalMachO64Object())
        val text = macho.allSections.first { it.sectionName == "__text" }
        assertEquals(1, text.data.size)
        assertEquals(0xC3.toByte(), text.data[0]) // ret
        assertTrue(text.isPureInstructions)
    }

    @Test
    fun `reads cstring section data`() {
        val macho = MachOReader.read(buildMinimalMachO64Object())
        val cstring = macho.allSections.first { it.sectionName == "__cstring" }
        val str = String(cstring.data, 0, cstring.data.size - 1, Charsets.US_ASCII)
        assertEquals("hello", str)
    }

    @Test
    fun `reads symbols`() {
        val macho = MachOReader.read(buildMinimalMachO64Object())
        assertEquals(1, macho.symbols.size)
        val sym = macho.symbols[0]
        assertEquals("_main", sym.name)
        assertTrue(sym.isExternal)
        assertTrue(sym.isInSection)
        assertEquals(1, sym.sectionIndex)
    }

    @Test
    fun `projects to ObjectFile`() {
        val macho = MachOReader.read(buildMinimalMachO64Object())
        val obj = MachOReader.toObjectFile(macho)
        assertEquals(ObjectFormat.MACH_O, obj.format)
        assertEquals(ArchType.X86_64, obj.arch.arch)
        assertTrue(obj.sections.any { it.name == "__text" })
        assertTrue(obj.symbols.any { it.name == "_main" })
        assertTrue(obj.metadata.flags.contains(ObjectFlag.RELOCATABLE))
    }

    @Test
    fun `section kinds are classified correctly`() {
        val macho = MachOReader.read(buildMinimalMachO64Object())
        val obj = MachOReader.toObjectFile(macho)
        val text = obj.sections.first { it.name == "__text" }
        assertEquals(SectionKind.TEXT, text.kind)
        val cstring = obj.sections.first { it.name == "__cstring" }
        assertEquals(SectionKind.RODATA, cstring.kind)
    }

    @Test
    fun `detectFormat identifies Mach-O`() {
        val bytes = buildMinimalMachO64Object()
        assertEquals(ObjectFormat.MACH_O, detectFormat(bytes))
    }

    @Test
    fun `MachOObjectFileReader implements interface`() {
        val reader = MachOObjectFileReader()
        assertEquals(ObjectFormat.MACH_O, reader.format)
        val bytes = buildMinimalMachO64Object()
        assertTrue(reader.canRead(bytes))
        val obj = reader.read(bytes)
        assertEquals(ObjectFormat.MACH_O, obj.format)
    }
}
