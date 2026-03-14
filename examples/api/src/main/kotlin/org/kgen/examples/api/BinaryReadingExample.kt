package org.kgen.examples.api

import org.kgen.binary.elf.ElfReader
import org.kgen.binary.macho.MachOReader
import org.kgen.binary.pe.PeReader
import org.kgen.target.jvm.AccessFlags
import org.kgen.target.jvm.ClassFileBuilder
import org.kgen.target.jvm.JvmClassReader

/**
 * Demonstrates reading and inspecting binary files.
 *
 * kgen can parse ELF, PE/COFF, Mach-O, and JVM class files into rich structured
 * models. Each format has its own reader that produces a format-specific model,
 * plus a universal ObjectFile projection for format-independent analysis.
 */
object BinaryReadingExample {

    /**
     * Creates a sample JVM class file, then reads it back using the class file
     * reader and inspects its structure.
     */
    @JvmStatic
    fun inspectClassFile() {
        val builder = ClassFileBuilder("com/example/Calculator")
        builder.method("add", "(II)I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
            code.iload(0)
            code.iload(1)
            code.iadd()
            code.ireturn()
        }
        val classBytes = builder.toBytes()

        val classFile = JvmClassReader.read(classBytes)

        println("Class: ${classFile.thisClassName}")
        println("Super: ${classFile.superClassName}")
        println("Java version: ${classFile.javaVersion}")
        println("Methods:")
        for (method in classFile.methods) {
            val methodName = classFile.string(method.nameIndex)
            val descriptor = classFile.string(method.descriptorIndex)
            println("  $methodName$descriptor")
        }
    }

    /**
     * Detects the binary format of raw bytes by checking magic numbers.
     */
    @JvmStatic
    fun detectFormat(bytes: ByteArray): String = when {
        ElfReader.canRead(bytes) -> "ELF"
        PeReader.canRead(bytes) -> "PE/COFF"
        MachOReader.canRead(bytes) -> "Mach-O"
        else -> "Unknown"
    }

    /**
     * Shows how to use the universal ObjectFile model for format-independent analysis.
     * All format-specific readers can produce an ObjectFile projection.
     */
    @JvmStatic
    fun demonstrateObjectFileModel() {
        println("ObjectFile is the universal projection:")
        println("  ElfReader.read(bytes)        -> ElfFile   (format-specific)")
        println("  ElfReader.toObjectFile(elf)   -> ObjectFile (universal)")
        println("  PeReader.read(bytes)         -> PeFile    (format-specific)")
        println("  PeReader.toObjectFile(pe)    -> ObjectFile (universal)")
        println("  MachOReader.read(bytes)      -> MachOFile (format-specific)")
        println("  MachOReader.toObjectFile(m)  -> ObjectFile (universal)")
        println()
        println("ObjectFile fields: format, arch, sections, symbols,")
        println("  relocations, imports, exports, debugInfo, unwindInfo")
    }

    @JvmStatic
    fun main(args: Array<String>) {
        println("=== JVM Class File Inspection ===")
        inspectClassFile()

        println()
        println("=== Object File Model ===")
        demonstrateObjectFileModel()
    }
}
