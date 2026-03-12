package org.kgen.binary.pe.pdb

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MsfTest {

    @Test
    fun `roundtrip empty MSF`() {
        val writer = MsfWriter()
        val bytes = writer.build()

        assertTrue(MsfReader.isMsf(bytes))
        val msf = MsfReader.read(bytes)
        assertEquals(4096, msf.blockSize)
        assertEquals(0, msf.streamCount)
    }

    @Test
    fun `roundtrip single stream`() {
        val writer = MsfWriter()
        val data = "Hello, PDB!".toByteArray()
        writer.addStream(data)
        val bytes = writer.build()

        val msf = MsfReader.read(bytes)
        assertEquals(1, msf.streamCount)
        assertEquals(data.size, msf.streams[0].size)
        assertTrue(data.contentEquals(msf.streamData(0)))
    }

    @Test
    fun `roundtrip multiple streams`() {
        val writer = MsfWriter()
        val data1 = ByteArray(100) { it.toByte() }
        val data2 = ByteArray(200) { (it * 2).toByte() }
        val data3 = ByteArray(50) { (it + 128).toByte() }
        writer.addStream(data1)
        writer.addStream(data2)
        writer.addStream(data3)
        val bytes = writer.build()

        val msf = MsfReader.read(bytes)
        assertEquals(3, msf.streamCount)
        assertTrue(data1.contentEquals(msf.streamData(0)))
        assertTrue(data2.contentEquals(msf.streamData(1)))
        assertTrue(data3.contentEquals(msf.streamData(2)))
    }

    @Test
    fun `roundtrip empty stream`() {
        val writer = MsfWriter()
        writer.addEmptyStream()
        writer.addStream("data".toByteArray())
        val bytes = writer.build()

        val msf = MsfReader.read(bytes)
        assertEquals(2, msf.streamCount)
        assertEquals(0, msf.streams[0].size)
        assertEquals(0, msf.streamData(0).size)
        assertEquals("data", String(msf.streamData(1)))
    }

    @Test
    fun `large stream spanning multiple blocks`() {
        val writer = MsfWriter(blockSize = 4096)
        val data = ByteArray(10000) { (it % 256).toByte() }
        writer.addStream(data)
        val bytes = writer.build()

        val msf = MsfReader.read(bytes)
        assertEquals(1, msf.streamCount)
        assertTrue(data.contentEquals(msf.streamData(0)))
        assertTrue(msf.streams[0].blocks.size >= 3) // 10000 / 4096 = 3 blocks
    }

    @Test
    fun `isMsf detects valid MSF`() {
        val writer = MsfWriter()
        val bytes = writer.build()
        assertTrue(MsfReader.isMsf(bytes))
    }

    @Test
    fun `isMsf rejects non-MSF data`() {
        assertFalse(MsfReader.isMsf(byteArrayOf()))
        assertFalse(MsfReader.isMsf(byteArrayOf(0, 1, 2, 3)))
        assertFalse(MsfReader.isMsf("not an MSF file".toByteArray()))
    }

    @Test
    fun `superblock parse rejects small data`() {
        assertThrows<IllegalArgumentException> {
            MsfSuperBlock.parse(ByteArray(10))
        }
    }

    @Test
    fun `superblock parse rejects bad magic`() {
        assertThrows<IllegalArgumentException> {
            MsfSuperBlock.parse(ByteArray(64))
        }
    }

    @Test
    fun `stream data preserves exact bytes`() {
        val writer = MsfWriter()
        val data = byteArrayOf(0xCA.toByte(), 0xFE.toByte(), 0xBA.toByte(), 0xBE.toByte())
        writer.addStream(data)
        val bytes = writer.build()

        val msf = MsfReader.read(bytes)
        val result = msf.streamData(0)
        assertEquals(4, result.size)
        assertEquals(0xCA.toByte(), result[0])
        assertEquals(0xFE.toByte(), result[1])
        assertEquals(0xBA.toByte(), result[2])
        assertEquals(0xBE.toByte(), result[3])
    }

    @Test
    fun `multiple streams with exact block size`() {
        val writer = MsfWriter(blockSize = 4096)
        val data = ByteArray(4096) { 0xAB.toByte() }
        writer.addStream(data)
        writer.addStream(data)
        val bytes = writer.build()

        val msf = MsfReader.read(bytes)
        assertEquals(2, msf.streamCount)
        assertEquals(4096, msf.streamData(0).size)
        assertEquals(4096, msf.streamData(1).size)
    }
}
