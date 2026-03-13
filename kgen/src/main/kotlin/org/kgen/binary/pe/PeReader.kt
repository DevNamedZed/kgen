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
 * and .NET assemblies with CLR metadata. Parses sections, import/export directories,
 * relocations, debug directories, and (for .NET binaries) the CLR metadata tables
 * via [ClrTableParser][org.kgen.binary.pe.clr.ClrTableParser].
 *
 * ```java
 * PeFile pe = PeReader.read(bytes);
 * for (PeSectionHeader s : pe.getSections()) {
 *     System.out.println(s.getName() + ": " + s.getVirtualSize() + " bytes");
 * }
 * for (PeImportDirectory dir : pe.getImportDirectories()) {
 *     System.out.println("DLL: " + dir.getName());
 * }
 * ```
 *
 * For the universal [ObjectFile] model, use [toObjectFile].
 *
 * See `spec/object-formats.md` for the object format model specification.
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
