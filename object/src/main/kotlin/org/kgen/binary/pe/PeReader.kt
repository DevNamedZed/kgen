package org.kgen.binary.pe

import org.kgen.binary.*
import org.kgen.binary.pe.clr.ClrMetadata
import org.kgen.binary.pe.clr.ClrTableParser
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Reads PE/COFF binaries into a structured [PeFile] model.
 *
 * Supports full PE executables/DLLs (PE32 and PE32+), raw COFF .obj files,
 * and .NET assemblies with CLR metadata.
 *
 * ```kotlin
 * val pe = PeReader.read(bytes)
 * pe.sections.forEach { println("${it.name}: ${it.virtualSize} bytes") }
 * pe.importDirectories.forEach { dir ->
 *     println("DLL: ${dir.name}")
 *     dir.entries.forEach { println("  ${it.name}") }
 * }
 * ```
 */
object PeReader {

    @JvmStatic
    fun read(bytes: ByteArray): PeFile {
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return PeHeaderParser(buf, bytes).parse()
    }

    @JvmStatic
    fun canRead(bytes: ByteArray): Boolean {
        if (bytes.size < 2) return false
        if (bytes[0] == 0x4d.toByte() && bytes[1] == 0x5a.toByte()) return true
        val machine = (bytes[0].toInt() and 0xFF) or ((bytes[1].toInt() and 0xFF) shl 8)
        return machine in PeConstants.KNOWN_MACHINES
    }

    @JvmStatic
    fun toObjectFile(pe: PeFile): ObjectFile {
        return PeObjectFileProjection.project(pe)
    }
}
