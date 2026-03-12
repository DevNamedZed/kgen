package org.kgen.binary.pe

import org.kgen.binary.ar.ArchiveReader
import org.kgen.binary.ar.ArchiveVariant
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

class CoffImportLibraryWriterTest {

    @Test
    fun `produces valid archive with correct magic`() {
        val writer = CoffImportLibraryWriter()
        val lib = writer.write("test.dll", listOf("Func1", "Func2"))

        val magic = String(lib, 0, 8, Charsets.US_ASCII)
        assertEquals("!<arch>\n", magic)
    }

    @Test
    fun `archive is parseable and detected as COFF variant`() {
        val writer = CoffImportLibraryWriter()
        val lib = writer.write("kernel32.dll", listOf("CreateFileW", "ReadFile", "CloseHandle"))

        assertTrue(ArchiveReader.canRead(lib))
        val archive = ArchiveReader.read(lib)
        assertEquals(ArchiveVariant.COFF, archive.variant, "Should be detected as COFF archive (two / members)")
        assertEquals(3, archive.members.size, "Should have 3 import object members")
    }

    @Test
    fun `import objects have correct IMPORT_OBJECT_HEADER signature`() {
        val writer = CoffImportLibraryWriter()
        val lib = writer.write("test.dll", listOf("MyFunc"))

        val archive = ArchiveReader.read(lib)
        assertEquals(1, archive.members.size)

        val data = archive.members[0].data
        assertTrue(data.size >= 20, "Import object must be at least 20 bytes")

        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val sig1 = buf.getShort().toInt() and 0xFFFF
        val sig2 = buf.getShort().toInt() and 0xFFFF
        assertEquals(0x0000, sig1, "Sig1 must be 0x0000")
        assertEquals(0xFFFF, sig2, "Sig2 must be 0xFFFF")

        val version = buf.getShort().toInt() and 0xFFFF
        assertEquals(0, version, "Version must be 0")

        val machine = buf.getShort().toInt() and 0xFFFF
        assertEquals(PeConstants.MACHINE_AMD64, machine)

        buf.getInt() // skip timestamp
        val sizeOfData = buf.getInt()
        assertTrue(sizeOfData > 0, "SizeOfData must be positive")

        // After the 20-byte header, there should be the symbol name and DLL name
        val remaining = ByteArray(data.size - 20)
        System.arraycopy(data, 20, remaining, 0, remaining.size)
        val strings = String(remaining, Charsets.US_ASCII)
        assertTrue(strings.contains("MyFunc"), "Should contain symbol name")
        assertTrue(strings.contains("test.dll"), "Should contain DLL name")
    }

    @Test
    fun `symbol table contains all exported symbols`() {
        val exports = listOf("Alpha", "Beta", "Gamma", "Delta")
        val writer = CoffImportLibraryWriter()
        val lib = writer.write("mylib.dll", exports)

        val archive = ArchiveReader.read(lib)
        val symbolNames = archive.symbols.map { it.name }

        for (export in exports) {
            assertTrue(export in symbolNames, "Symbol table should contain '$export'")
        }
        assertEquals(exports.size, archive.symbols.size)
    }

    @Test
    fun `supports different machine types`() {
        val writer = CoffImportLibraryWriter()

        val libAmd64 = writer.write("test.dll", listOf("Func"), PeConstants.MACHINE_AMD64)
        val libArm64 = writer.write("test.dll", listOf("Func"), PeConstants.MACHINE_ARM64)

        val archiveAmd64 = ArchiveReader.read(libAmd64)
        val archiveArm64 = ArchiveReader.read(libArm64)

        val dataAmd64 = archiveAmd64.members[0].data
        val dataArm64 = archiveArm64.members[0].data

        val machineAmd64 = ByteBuffer.wrap(dataAmd64, 6, 2).order(ByteOrder.LITTLE_ENDIAN).getShort().toInt() and 0xFFFF
        val machineArm64 = ByteBuffer.wrap(dataArm64, 6, 2).order(ByteOrder.LITTLE_ENDIAN).getShort().toInt() and 0xFFFF

        assertEquals(PeConstants.MACHINE_AMD64, machineAmd64)
        assertEquals(PeConstants.MACHINE_ARM64, machineArm64)
    }
}
