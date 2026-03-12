package org.kgen.binary.pe.pdb

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RsdsMatchTest {

    @Test
    fun `roundtrip RSDS entry`() {
        val guid = UUID.randomUUID()
        val rsdsBytes = RsdsEntry.build(guid, 1, "C:\\out\\program.pdb")
        val rsds = RsdsEntry.parse(rsdsBytes)

        assertEquals(guid, rsds.guid)
        assertEquals(1, rsds.age)
        assertEquals("C:\\out\\program.pdb", rsds.pdbPath)
    }

    @Test
    fun `RSDS matches PDB`() {
        val guid = UUID.randomUUID()
        val writer = PdbWriter()
        writer.guid = guid
        writer.age = 2
        val pdb = PdbReader.read(writer.build())

        val rsds = RsdsEntry(guid, 2, "test.pdb")
        assertTrue(rsds.matches(pdb))
    }

    @Test
    fun `RSDS mismatch on different GUID`() {
        val writer = PdbWriter()
        writer.guid = UUID.randomUUID()
        writer.age = 1
        val pdb = PdbReader.read(writer.build())

        val rsds = RsdsEntry(UUID.randomUUID(), 1, "test.pdb")
        assertFalse(rsds.matches(pdb))
    }

    @Test
    fun `RSDS mismatch on different age`() {
        val guid = UUID.randomUUID()
        val writer = PdbWriter()
        writer.guid = guid
        writer.age = 1
        val pdb = PdbReader.read(writer.build())

        val rsds = RsdsEntry(guid, 2, "test.pdb")
        assertFalse(rsds.matches(pdb))
    }

    @Test
    fun `isRsds detects valid signature`() {
        val bytes = RsdsEntry.build(UUID.randomUUID(), 1, "test.pdb")
        assertTrue(RsdsEntry.isRsds(bytes))
    }

    @Test
    fun `isRsds rejects invalid data`() {
        assertFalse(RsdsEntry.isRsds(byteArrayOf()))
        assertFalse(RsdsEntry.isRsds(byteArrayOf(0, 0, 0, 0)))
        assertFalse(RsdsEntry.isRsds("NBXX".toByteArray()))
    }

    @Test
    fun `parse rejects non-RSDS data`() {
        assertThrows<IllegalArgumentException> {
            RsdsEntry.parse(ByteArray(30))
        }
    }

    @Test
    fun `parse rejects small data`() {
        assertThrows<IllegalArgumentException> {
            RsdsEntry.parse(ByteArray(10))
        }
    }
}
