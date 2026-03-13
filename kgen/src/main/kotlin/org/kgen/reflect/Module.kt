package org.kgen.reflect

import org.kgen.binary.*
import java.nio.file.Files
import java.nio.file.Path

/**
 * Unified binary introspection entry point for the reflect API.
 *
 * A Module represents any binary artifact — ELF, PE/COFF, Mach-O, JVM `.class`, .NET assembly,
 * or WASM module — providing a single API surface to query symbols, functions, sections, types,
 * imports, exports, debug info, and disassembly regardless of the underlying format.
 *
 * Modules can be loaded from files, raw byte arrays, or pre-parsed [ObjectFile] instances.
 * File-based modules are pure data (no system resources, no `close()` needed). Format-specific
 * models ([elf], [pe], [machO], [classFile], [clr], [wasm]) are lazily parsed from the raw
 * bytes on first access, giving you full access to format-level detail when needed.
 *
 * Typical usage for binary analysis:
 * ```java
 * // Open any binary and inspect it
 * var module = Module.fromFile("libc.so.6");
 * module.name();               // "libc.so.6"
 * module.format();             // ObjectFormat.ELF
 * module.symbols();            // List<Symbol>
 * module.functions();          // List<Function>
 * module.function("strlen");   // Function?
 *
 * // Disassemble a function
 * var insns = module.disassemble("strlen");
 *
 * // Access format-specific model
 * var elf = module.elf();      // ElfFile with full ELF detail
 *
 * // Type reflection (JVM, CLR, or DWARF-based)
 * var types = module.types();  // List<TypeInfo>
 * ```
 *
 * See `spec/reflect.md` for the full specification of the reflect API.
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

    // -- Disassembly --

    /**
     * Disassemble a function by name.
     * Uses the module's architecture to select the correct disassembler.
     * Returns null if the symbol is not found or has no code.
     */
    fun disassemble(name: String): List<Instruction>? {
        val sym = symbol(name) ?: return null
        return disassemble(sym)
    }

    /**
     * Disassemble a symbol's code.
     */
    fun disassemble(sym: Symbol): List<Instruction>? {
        return disassemble(sym.function() ?: return null)
    }

    /**
     * Disassemble a function.
     */
    fun disassemble(func: Function): List<Instruction>? {
        val codeBytes = extractCode(func.offset(), func.size()) ?: return null
        return disassembleBytes(codeBytes, func.offset())
    }

    /**
     * Disassemble raw bytes using this module's architecture.
     * Automatically selects the correct disassembler backend based on [arch].
     */
    fun disassembleBytes(code: ByteArray, baseAddress: Long = 0): List<Instruction> {
        return when (obj.arch.arch) {
            ArchType.X86_64, ArchType.X86 -> {
                val dis = org.kgen.target.x86.disasm.X86Disassembler()
                dis.disassemble(code, baseAddress).map { insn ->
                    object : Instruction {
                        override val address: Long = insn.address
                        override val bytes: ByteArray = insn.bytes
                        override val mnemonic: String = insn.mnemonic
                        override fun operandsText(): String = insn.operands
                        override fun text(): String = insn.toString()
                    }
                }
            }
            ArchType.AARCH64 -> {
                val dis = org.kgen.target.arm64.disasm.Arm64Disassembler()
                dis.disassemble(code, baseAddress)
            }
            ArchType.RISCV64, ArchType.RISCV32 -> {
                val dis = org.kgen.target.riscv.disasm.RiscVDisassembler()
                dis.disassemble(code, baseAddress)
            }
            ArchType.JVM -> {
                val dis = org.kgen.target.jvm.asm.JvmDisassembler()
                val insns = dis.disassemble(code)
                insns.map { insn ->
                    object : Instruction {
                        override val address: Long = baseAddress + insn.offset
                        override val bytes: ByteArray = code.copyOfRange(insn.offset, insn.offset + insn.size)
                        override val mnemonic: String = insn.opcode.name.lowercase()
                        override fun operandsText(): String = insn.operands
                        override fun text(): String = insn.toString()
                    }
                }
            }
            ArchType.WASM32, ArchType.WASM64 -> {
                val dis = org.kgen.target.wasm.disasm.WasmDisassembler()
                dis.disassemble(code)
            }
            else -> emptyList()
        }
    }

    private fun extractCode(offset: Long, size: Long): ByteArray? {
        if (size <= 0) return null
        // Find the text section containing this offset
        val textSection = obj.sections.firstOrNull { section ->
            section.kind == SectionKind.TEXT
                    && offset >= section.address
                    && offset + size <= section.address + section.data.size
        } ?: return null
        val start = (offset - textSection.address).toInt()
        val end = start + size.toInt()
        if (start < 0 || end > textSection.data.size) return null
        return textSection.data.copyOfRange(start, end)
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

    private val classFileCache: org.kgen.target.jvm.ClassFile? by lazy {
        if (obj.format == ObjectFormat.JVM_CLASS && rawBytes.isNotEmpty()) {
            try { org.kgen.target.jvm.JvmClassReader.read(rawBytes) } catch (_: Throwable) { null }
        } else null
    }

    private val wasmCache: org.kgen.target.wasm.module.WasmModule? by lazy {
        if (obj.format == ObjectFormat.WASM_MODULE && rawBytes.isNotEmpty()) {
            try { org.kgen.target.wasm.module.WasmModuleReader.read(rawBytes) } catch (_: Throwable) { null }
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
            ObjectFormat.ELF, ObjectFormat.PE_COFF, ObjectFormat.MACH_O -> {
                val debugInfo = try { org.kgen.binary.dwarf.DwarfReader.read(obj) } catch (_: Throwable) { null }
                if (debugInfo != null) org.kgen.binary.dwarf.DwarfTypeMapper.map(debugInfo, this)
                else emptyList()
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
    fun classFile(): org.kgen.target.jvm.ClassFile? = classFileCache

    /** The CLR metadata, or null if not a .NET assembly. */
    fun clr(): org.kgen.binary.pe.clr.ClrMetadata? = peCache?.clrMetadata

    /** The WASM module model, or null if not a .wasm file. */
    fun wasm(): org.kgen.target.wasm.module.WasmModule? = wasmCache

    // -- Raw bytes --

    fun bytes(): ByteArray = rawBytes.copyOf()

    /** Access the underlying ObjectFile model. */
    fun objectFile(): ObjectFile = obj

    /**
     * Writes this module to disk at the given path.
     *
     * For modules loaded from files or bytes, this writes the current binary content.
     * Format-specific models (ELF, PE, JVM, WASM) can be modified before saving —
     * pass `rewrite = true` to regenerate from the format-specific model.
     *
     * ```java
     * var module = Module.fromFile("input.o");
     * module.save("output.o");
     *
     * // Modify and rewrite
     * var cf = module.classFile();
     * // ... modify cf ...
     * module.save("Modified.class", true);
     * ```
     */
    @JvmOverloads
    fun save(path: String, rewrite: Boolean = false) = save(Path.of(path), rewrite)

    @JvmOverloads
    fun save(path: Path, rewrite: Boolean = false) {
        val bytes = if (rewrite) rewriteBytes() else rawBytes
        require(bytes.isNotEmpty()) { "No binary data to save (module created from ObjectFile without raw bytes)" }
        Files.write(path, bytes)
    }

    fun toBytes(rewrite: Boolean = false): ByteArray {
        return if (rewrite) rewriteBytes() else rawBytes.copyOf()
    }

    private fun rewriteBytes(): ByteArray {
        return when (obj.format) {
            ObjectFormat.JVM_CLASS -> {
                val cf = classFile() ?: throw IllegalStateException("Cannot reconstruct JVM class file")
                org.kgen.target.jvm.JvmClassWriter.write(cf)
            }
            ObjectFormat.WASM_MODULE -> {
                val wasm = wasm() ?: throw IllegalStateException("Cannot reconstruct WASM module")
                org.kgen.target.wasm.module.WasmModuleWriter.write(wasm)
            }
            ObjectFormat.ELF -> {
                val machine = elfMachineCode()
                org.kgen.binary.elf.ElfObjectWriter(machine).write(obj)
            }
            ObjectFormat.PE_COFF -> {
                val machine = coffMachineCode()
                org.kgen.binary.pe.CoffObjectWriter(machine).write(obj)
            }
            ObjectFormat.MACH_O -> {
                val (cpuType, cpuSub) = machoCpuType()
                org.kgen.binary.macho.MachOObjectWriter(cpuType, cpuSub).write(obj)
            }
            else -> throw UnsupportedOperationException("Rewrite not supported for ${obj.format}")
        }
    }

    private fun elfMachineCode(): Int {
        return when (obj.arch.arch) {
            ArchType.X86_64 -> org.kgen.binary.elf.ElfMachine.X86_64.code
            ArchType.AARCH64 -> org.kgen.binary.elf.ElfMachine.AARCH64.code
            ArchType.RISCV64, ArchType.RISCV32 -> org.kgen.binary.elf.ElfMachine.RISCV.code
            else -> org.kgen.binary.elf.ElfMachine.X86_64.code
        }
    }

    private fun coffMachineCode(): Int {
        return when (obj.arch.arch) {
            ArchType.X86_64 -> org.kgen.binary.pe.PeConstants.MACHINE_AMD64
            ArchType.AARCH64 -> org.kgen.binary.pe.PeConstants.MACHINE_ARM64
            else -> org.kgen.binary.pe.PeConstants.MACHINE_AMD64
        }
    }

    private fun machoCpuType(): Pair<Int, Int> {
        return when (obj.arch.arch) {
            ArchType.X86_64 -> org.kgen.binary.macho.MachO.CPU_TYPE_X86_64 to org.kgen.binary.macho.MachO.CPU_SUBTYPE_ALL
            ArchType.AARCH64 -> org.kgen.binary.macho.MachO.CPU_TYPE_ARM64 to org.kgen.binary.macho.MachO.CPU_SUBTYPE_ALL
            else -> org.kgen.binary.macho.MachO.CPU_TYPE_X86_64 to org.kgen.binary.macho.MachO.CPU_SUBTYPE_ALL
        }
    }

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
            try { builtinReaders.add(org.kgen.target.jvm.JvmObjectFileReader()) } catch (_: Throwable) {}
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
