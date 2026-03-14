package org.kgen.examples.api

import org.kgen.binary.elf.ElfReader
import org.kgen.binary.pe.PeReader
import org.kgen.binary.macho.MachOReader
import org.kgen.target.jvm.AccessFlags
import org.kgen.target.jvm.ClassFileBuilder
import org.kgen.target.jvm.JvmClassReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BinaryReadingExampleTest {

    @Test
    fun classFileRoundTrips() {
        val builder = ClassFileBuilder("com/example/Test")
        builder.method("greet", "()V", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
            code.return_()
        }
        val bytes = builder.toBytes()

        val classFile = JvmClassReader.read(bytes)
        assertEquals("com/example/Test", classFile.thisClassName)
        assertEquals("java/lang/Object", classFile.superClassName)
        assertTrue(classFile.methods.isNotEmpty())
    }

    @Test
    fun formatDetectionWorksForClassFiles() {
        val builder = ClassFileBuilder("com/example/Detect")
        val classBytes = builder.toBytes()

        val format = BinaryReadingExample.detectFormat(classBytes)
        assertEquals("Unknown", format)
    }

    @Test
    fun formatDetectionRejectsGarbage() {
        val garbage = byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7)
        assertFalse(ElfReader.canRead(garbage))
        assertFalse(PeReader.canRead(garbage))
        assertFalse(MachOReader.canRead(garbage))
    }
}
