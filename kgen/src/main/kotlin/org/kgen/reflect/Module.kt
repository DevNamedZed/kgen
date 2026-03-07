package org.kgen.reflect

import org.kgen.binary.*
import java.nio.file.Files
import java.nio.file.Path

/**
 * The central type in the reflect API. Represents any binary — ELF, PE, Mach-O,
 * JVM .class, WASM module — on disk or loaded in a process.
 *
 * File-based modules are just parsed data — no system resources, no close() needed.
 *
 * ```java
 * var module = Module.fromFile("libc.so.6");
 * module.name();               // "libc.so.6"
 * module.format();             // ObjectFormat.ELF
 * module.symbols();            // List<Symbol>
 * module.functions();          // List<Function>
 * module.function("strlen");   // Function?
 * ```
 */
class Module private constructor(
    private val name: String,
    private val path: Path?,
    private val obj: ObjectFile,
    private val rawBytes: ByteArray,
    private val loaded: Boolean = false,
    private val base: Long = 0,
) {
    private val symbolCache: List<Symbol> by lazy {
        obj.symbols.map { Symbol(it, this) }
    }

    /** The module name (file name or identifier). */
    fun name(): String = name

    /** The file path, or null if loaded from bytes. */
    fun path(): Path? = path

    /** The binary format. */
    fun format(): ObjectFormat = obj.format

    /** The target architecture. */
    fun arch(): Architecture = obj.arch

    /** Whether this module is loaded in a running process. */
    fun isLoaded(): Boolean = loaded

    /** Base address (0 for file-based modules). */
    fun baseAddress(): Long = base

    // -- Classification --

    fun isExecutable(): Boolean = ObjectFlag.EXECUTABLE in obj.metadata.flags
    fun isSharedLibrary(): Boolean = ObjectFlag.SHARED_LIBRARY in obj.metadata.flags
    fun isRelocatable(): Boolean = ObjectFlag.RELOCATABLE in obj.metadata.flags

    /** Whether this module contains CLR metadata. */
    fun hasClr(): Boolean = obj.format == ObjectFormat.MSIL_ASSEMBLY || obj.format == ObjectFormat.MSIL_MIXED

    /** Whether this is a JVM class file. */
    fun hasJvm(): Boolean = obj.format == ObjectFormat.JVM_CLASS

    /** Whether this is a WASM module. */
    fun hasWasm(): Boolean = obj.format == ObjectFormat.WASM_MODULE

    /** Whether this module contains native machine code. */
    fun hasNativeCode(): Boolean = obj.format != ObjectFormat.JVM_CLASS
            && obj.format != ObjectFormat.WASM_MODULE
            && obj.format != ObjectFormat.MSIL_ASSEMBLY

    /** Whether this is a mixed-mode module (native + managed). */
    fun isMixedMode(): Boolean = obj.format == ObjectFormat.MSIL_MIXED

    // -- Sections --

    fun sections(): List<Section> = obj.sections

    fun section(name: String): Section? = obj.sections.firstOrNull { it.name == name }

    fun section(kind: SectionKind): Section? = obj.sections.firstOrNull { it.kind == kind }

    fun textSection(): Section? = section(SectionKind.TEXT)

    // -- Symbols --

    fun symbols(): List<Symbol> = symbolCache

    fun symbol(name: String): Symbol? = symbolCache.firstOrNull { it.name() == name }

    // -- Functions --

    fun functions(): List<Function> = symbolCache.filter { it.isFunction() }.mapNotNull { it.function() }

    fun function(name: String): Function? = symbol(name)?.function()

    fun function(sym: Symbol): Function? = sym.function()

    // -- Imports / Exports --

    fun imports(): List<ImportEntry> = obj.imports

    fun exports(): List<ExportEntry> = obj.exports

    // -- Dependencies --

    fun dependencies(): List<Dependency> {
        val dyn = obj.dynamicInfo ?: return emptyList()
        return dyn.neededLibraries.map { Dependency(it) }
    }

    // -- Debug Info --

    fun debugInfo(): DebugInfo? = obj.debugInfo

    // -- Entry point --

    fun entryPoint(): Function? {
        val entry = obj.metadata.entryPoint ?: return null
        return symbolCache.firstOrNull { it.offset() == entry }?.function()
    }

    // -- Types --

    /** All types discoverable from metadata (JVM class files, CLR assemblies). */
    fun types(): List<TypeInfo> = typeCache

    /** Look up a type by full name. */
    fun type(name: String): TypeInfo? = typeCache.firstOrNull { it.fullName() == name }

    // -- Format-specific models (lazy, re-parsed from raw bytes) --

    private val elfCache: org.kgen.binary.elf.ElfFile? by lazy {
        if (obj.format == ObjectFormat.ELF && rawBytes.isNotEmpty()) {
            try { org.kgen.binary.elf.ElfReader.read(rawBytes) } catch (_: Throwable) { null }
        } else null
    }

    private val peCache: org.kgen.binary.pe.PeFile? by lazy {
        if ((obj.format == ObjectFormat.PE_COFF || obj.format == ObjectFormat.MSIL_ASSEMBLY
                    || obj.format == ObjectFormat.MSIL_MIXED) && rawBytes.isNotEmpty()) {
            try { org.kgen.binary.pe.PeReader.read(rawBytes) } catch (_: Throwable) { null }
        } else null
    }

    private val machOCache: org.kgen.binary.macho.MachOFile? by lazy {
        if (obj.format == ObjectFormat.MACH_O && rawBytes.isNotEmpty()) {
            try { org.kgen.binary.macho.MachOReader.read(rawBytes) } catch (_: Throwable) { null }
        } else null
    }

    private val classFileCache: org.kgen.binary.jvm.ClassFile? by lazy {
        if (obj.format == ObjectFormat.JVM_CLASS && rawBytes.isNotEmpty()) {
            try { org.kgen.binary.jvm.JvmClassReader.read(rawBytes) } catch (_: Throwable) { null }
        } else null
    }

    private val wasmCache: org.kgen.backend.wasm.module.WasmModule? by lazy {
        if (obj.format == ObjectFormat.WASM_MODULE && rawBytes.isNotEmpty()) {
            try { org.kgen.backend.wasm.module.WasmModuleReader.read(rawBytes) } catch (_: Throwable) { null }
        } else null
    }

    private val typeCache: List<TypeInfo> by lazy {
        when (obj.format) {
            ObjectFormat.JVM_CLASS -> {
                val cf = classFile() ?: return@lazy emptyList()
                JvmTypeMapper.map(cf, this)
            }
            ObjectFormat.MSIL_ASSEMBLY, ObjectFormat.MSIL_MIXED -> {
                val pe = pe() ?: return@lazy emptyList()
                val clr = pe.clrMetadata ?: return@lazy emptyList()
                ClrTypeMapper.map(clr, this)
            }
            else -> emptyList()
        }
    }

    /** The ELF model, or null if not an ELF binary. */
    fun elf(): org.kgen.binary.elf.ElfFile? = elfCache

    /** The PE/COFF model, or null if not a PE binary. */
    fun pe(): org.kgen.binary.pe.PeFile? = peCache

    /** The Mach-O model, or null if not a Mach-O binary. */
    fun machO(): org.kgen.binary.macho.MachOFile? = machOCache

    /** The JVM class file model, or null if not a .class file. */
    fun classFile(): org.kgen.binary.jvm.ClassFile? = classFileCache

    /** The CLR metadata, or null if not a .NET assembly. */
    fun clr(): org.kgen.binary.pe.clr.ClrMetadata? = peCache?.clrMetadata

    /** The WASM module model, or null if not a .wasm file. */
    fun wasm(): org.kgen.backend.wasm.module.WasmModule? = wasmCache

    // -- Raw bytes --

    fun bytes(): ByteArray = rawBytes.copyOf()

    /** Access the underlying ObjectFile model. */
    fun objectFile(): ObjectFile = obj

    override fun toString(): String = "Module($name, ${obj.format}, ${obj.arch.arch})"

    companion object {
        /**
         * Load a module from a file path.
         */
        @JvmStatic
        fun fromFile(path: String): Module = fromFile(Path.of(path))

        /**
         * Load a module from a file path.
         */
        @JvmStatic
        fun fromFile(path: Path): Module {
            val bytes = Files.readAllBytes(path)
            val format = detectFormat(bytes)
                ?: throw IllegalArgumentException("Unrecognized binary format: $path")
            val reader = findReader(format)
                ?: throw UnsupportedOperationException("No reader for format: $format")
            val obj = reader.read(bytes)
            return Module(path.fileName.toString(), path, obj, bytes)
        }

        /**
         * Load a module from raw bytes.
         */
        @JvmStatic
        fun fromBytes(bytes: ByteArray, name: String = "unknown"): Module {
            val format = detectFormat(bytes)
                ?: throw IllegalArgumentException("Unrecognized binary format")
            val reader = findReader(format)
                ?: throw UnsupportedOperationException("No reader for format: $format")
            val obj = reader.read(bytes)
            return Module(name, null, obj, bytes)
        }

        /**
         * Wrap an already-parsed ObjectFile.
         */
        @JvmStatic
        fun fromObjectFile(obj: ObjectFile, name: String = "unknown"): Module {
            return Module(name, null, obj, ByteArray(0))
        }

        private val builtinReaders = mutableListOf<ObjectFileReader>()

        init {
            // Register built-in readers
            try { builtinReaders.add(org.kgen.binary.elf.ElfObjectFileReader()) } catch (_: Throwable) {}
            try { builtinReaders.add(org.kgen.binary.jvm.JvmObjectFileReader()) } catch (_: Throwable) {}
            try { builtinReaders.add(org.kgen.binary.pe.PeObjectFileReader()) } catch (_: Throwable) {}
            try { builtinReaders.add(org.kgen.binary.macho.MachOObjectFileReader()) } catch (_: Throwable) {}
        }

        private fun findReader(format: ObjectFormat): ObjectFileReader? {
            return builtinReaders.firstOrNull { it.format == format }
                ?: java.util.ServiceLoader.load(ObjectFileReader::class.java)
                    .firstOrNull { it.format == format }
        }
    }
}
