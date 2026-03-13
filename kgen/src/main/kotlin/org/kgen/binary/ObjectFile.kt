package org.kgen.binary

/**
 * Universal object file model that abstracts ELF, PE/COFF, Mach-O, WASM, JVM, and CLR binaries
 * into a single, format-independent representation.
 *
 * This is the central data model for binary analysis, code generation output, and linker input.
 * Every binary reader ([org.kgen.binary.elf.ElfReader], [org.kgen.binary.pe.PeReader],
 * [org.kgen.binary.macho.MachOReader]) can produce an [ObjectFile], and every linker consumes them.
 *
 * ## Reading a binary
 * ```kotlin
 * val elf = ElfReader.read(bytes)
 * val obj = ElfReader.toObjectFile(elf)
 * obj.symbols.forEach { println("${it.name} @ 0x${it.value.toString(16)}") }
 * ```
 *
 * ## From code generation
 * ```kotlin
 * val module = IrBuilder("test", Target.x86_64()).apply {
 *     createFunction("add", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
 *     positionAtEnd(appendBlock("entry"))
 *     ret(add(currentParams[0], currentParams[1]))
 *     finalizeFunction()
 * }.build()
 * val obj = X86CodeGenerator().generateObjectFile(module)
 * ```
 *
 * @property format The binary format (ELF, PE, Mach-O, etc.)
 * @property arch Target architecture and platform details.
 * @property sections Binary sections (code, data, debug, etc.)
 * @property symbols Symbol table — functions, data, externals.
 * @property relocations Relocation entries that the linker must resolve.
 * @property imports Symbols imported from external modules (DLLs, shared libraries).
 * @property exports Symbols exported for use by other modules.
 * @property comdatGroups COMDAT groups for deduplication across translation units.
 * @property debugInfo Debug symbols (DWARF, CodeView).
 * @property unwindInfo Exception handling / stack unwinding data.
 * @property dynamicInfo Dynamic linking metadata (needed libraries, PLT/GOT, PE import tables).
 * @property metadata High-level properties (entry point, OS/ABI, flags).
 */
data class ObjectFile(
    val format: ObjectFormat,
    val arch: Architecture,
    val sections: List<Section>,
    val symbols: List<Symbol>,
    val relocations: List<Relocation>,
    val imports: List<ImportEntry> = emptyList(),
    val exports: List<ExportEntry> = emptyList(),
    val comdatGroups: List<ComdatGroup> = emptyList(),
    val debugInfo: DebugInfo? = null,
    val unwindInfo: List<UnwindEntry> = emptyList(),
    val dynamicInfo: DynamicLinkInfo? = null,
    val metadata: ObjectMetadata = ObjectMetadata(),
)

/** Binary format discriminator. Determines which reader/writer to use. */
enum class ObjectFormat {
    /** Executable and Linkable Format — Linux, BSD, Solaris, embedded. */
    ELF,
    /** Portable Executable / Common Object File Format — Windows. */
    PE_COFF,
    /** Mach Object — macOS, iOS. */
    MACH_O,
    /** WebAssembly module (.wasm). */
    WASM_MODULE,
    /** JVM class file (.class). */
    JVM_CLASS,
    /** .NET / CLR assembly (.dll/.exe with IL). */
    MSIL_ASSEMBLY,
    /** Mixed-mode .NET assembly (native + IL in one PE). */
    MSIL_MIXED,
    /** Raw binary blob (no format headers). */
    RAW_BINARY,
}

/**
 * High-level metadata about an object file that isn't tied to any specific section.
 *
 * @property entryPoint Virtual address of the entry point (executables only).
 * @property stackSize Requested stack size in bytes.
 * @property heapSize Requested heap reserve in bytes.
 * @property flags Binary capabilities and characteristics.
 * @property osAbi Target OS/ABI.
 * @property moduleName Module or library name (e.g., DT_SONAME, LC_ID_DYLIB).
 * @property properties Arbitrary key-value metadata.
 */
data class ObjectMetadata(
    val entryPoint: Long? = null,
    val stackSize: Long? = null,
    val heapSize: Long? = null,
    val flags: Set<ObjectFlag> = emptySet(),
    val osAbi: OsAbi = OsAbi.NONE,
    val moduleName: String? = null,
    val properties: Map<String, String> = emptyMap(),
)

/** Binary capability flags. */
enum class ObjectFlag {
    EXECUTABLE, RELOCATABLE, SHARED_LIBRARY, DLL, CORE_DUMP,
    POSITION_INDEPENDENT, LARGE_ADDRESS_AWARE,
    NO_EXEC_STACK, RELRO, BIND_NOW,
}

/** Target operating system / ABI. */
enum class OsAbi {
    NONE, LINUX, FREEBSD, NETBSD, OPENBSD, SOLARIS,
    MACOS, WINDOWS, IOS, ANDROID, WASI,
}
