package org.kgen.binary.jvm

import org.kgen.binary.*

/**
 * Reads a JVM .class file and produces an [ObjectFile] model.
 *
 * Maps JVM class structure to the universal binary model:
 * - Methods → FUNCTION/METHOD symbols
 * - Fields → DATA symbols
 * - Bytecode → TEXT section
 * - Constants → RODATA section
 */
class JvmObjectFileReader : ObjectFileReader {
    override val format: ObjectFormat = ObjectFormat.JVM_CLASS

    override fun canRead(bytes: ByteArray): Boolean =
        bytes.size >= 4
                && bytes[0] == 0xCA.toByte()
                && bytes[1] == 0xFE.toByte()
                && bytes[2] == 0xBA.toByte()
                && bytes[3] == 0xBE.toByte()

    override fun read(bytes: ByteArray): ObjectFile {
        val cf = JvmClassReader.read(bytes)
        return toObjectFile(cf, bytes)
    }

    private fun toObjectFile(cf: ClassFile, rawBytes: ByteArray): ObjectFile {
        val symbols = mutableListOf<Symbol>()
        val className = cf.thisClassName.replace('/', '.')

        // Class symbol
        symbols.add(Symbol(
            name = className,
            kind = classKind(cf.accessFlags),
            binding = if (cf.accessFlags and AccessFlags.PUBLIC != 0) SymbolBinding.GLOBAL else SymbolBinding.LOCAL,
            flags = classFlags(cf.accessFlags),
        ))

        // Method symbols
        for (m in cf.methods) {
            val name = cf.string(m.nameIndex)
            val desc = cf.string(m.descriptorIndex)
            symbols.add(Symbol(
                name = "$className.$name",
                kind = SymbolKind.METHOD,
                binding = if (m.accessFlags and AccessFlags.PUBLIC != 0) SymbolBinding.GLOBAL else SymbolBinding.LOCAL,
                flags = methodFlags(m.accessFlags),
            ))
        }

        // Field symbols
        for (f in cf.fields) {
            val name = cf.string(f.nameIndex)
            symbols.add(Symbol(
                name = "$className.$name",
                kind = SymbolKind.FIELD,
                binding = if (f.accessFlags and AccessFlags.PUBLIC != 0) SymbolBinding.GLOBAL else SymbolBinding.LOCAL,
                flags = fieldFlags(f.accessFlags),
            ))
        }

        val sections = listOf(
            Section(
                name = ".class",
                kind = SectionKind.TEXT,
                data = rawBytes,
            ),
        )

        return ObjectFile(
            format = ObjectFormat.JVM_CLASS,
            arch = Architecture.JVM,
            sections = sections,
            symbols = symbols,
            relocations = emptyList(),
            metadata = ObjectMetadata(
                properties = mapOf(
                    "java.version" to cf.javaVersion,
                    "class.name" to className,
                    "super.class" to (cf.superClassName?.replace('/', '.') ?: ""),
                ),
            ),
        )
    }

    private fun classKind(flags: Int): SymbolKind = when {
        flags and AccessFlags.INTERFACE != 0 -> SymbolKind.INTERFACE
        flags and AccessFlags.ENUM != 0 -> SymbolKind.CLASS
        else -> SymbolKind.CLASS
    }

    private fun classFlags(flags: Int): Set<SymbolFlag> = buildSet {
        if (flags and AccessFlags.PUBLIC != 0) add(SymbolFlag.ACC_PUBLIC)
        if (flags and AccessFlags.FINAL != 0) add(SymbolFlag.ACC_FINAL)
        if (flags and AccessFlags.ABSTRACT != 0) add(SymbolFlag.ACC_ABSTRACT)
        if (flags and AccessFlags.SYNTHETIC != 0) add(SymbolFlag.SYNTHETIC)
        if (flags and AccessFlags.ENUM != 0) add(SymbolFlag.ENUM)
        if (flags and AccessFlags.ANNOTATION != 0) add(SymbolFlag.ANNOTATION)
    }

    private fun methodFlags(flags: Int): Set<SymbolFlag> = buildSet {
        if (flags and AccessFlags.PUBLIC != 0) add(SymbolFlag.ACC_PUBLIC)
        if (flags and AccessFlags.PRIVATE != 0) add(SymbolFlag.ACC_PRIVATE)
        if (flags and AccessFlags.PROTECTED != 0) add(SymbolFlag.ACC_PROTECTED)
        if (flags and AccessFlags.STATIC != 0) add(SymbolFlag.ACC_STATIC)
        if (flags and AccessFlags.FINAL != 0) add(SymbolFlag.ACC_FINAL)
        if (flags and AccessFlags.SYNCHRONIZED != 0) add(SymbolFlag.ACC_SYNCHRONIZED)
        if (flags and AccessFlags.NATIVE != 0) add(SymbolFlag.ACC_NATIVE)
        if (flags and AccessFlags.ABSTRACT != 0) add(SymbolFlag.ACC_ABSTRACT)
        if (flags and AccessFlags.SYNTHETIC != 0) add(SymbolFlag.SYNTHETIC)
        if (flags and AccessFlags.BRIDGE != 0) add(SymbolFlag.BRIDGE)
        if (flags and AccessFlags.VARARGS != 0) add(SymbolFlag.VARARGS)
    }

    private fun fieldFlags(flags: Int): Set<SymbolFlag> = buildSet {
        if (flags and AccessFlags.PUBLIC != 0) add(SymbolFlag.ACC_PUBLIC)
        if (flags and AccessFlags.PRIVATE != 0) add(SymbolFlag.ACC_PRIVATE)
        if (flags and AccessFlags.PROTECTED != 0) add(SymbolFlag.ACC_PROTECTED)
        if (flags and AccessFlags.STATIC != 0) add(SymbolFlag.ACC_STATIC)
        if (flags and AccessFlags.FINAL != 0) add(SymbolFlag.ACC_FINAL)
        if (flags and AccessFlags.VOLATILE != 0) add(SymbolFlag.DEPRECATED) // reusing; TODO: add VOLATILE flag
        if (flags and AccessFlags.SYNTHETIC != 0) add(SymbolFlag.SYNTHETIC)
        if (flags and AccessFlags.ENUM != 0) add(SymbolFlag.ENUM)
    }
}
