package org.kgen.binary

// Universal object file model — abstracts ELF, PE/COFF, Mach-O, WASM, JVM

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

enum class ObjectFormat {
    ELF,
    PE_COFF,
    MACH_O,
    WASM_MODULE,
    JVM_CLASS,
    MSIL_ASSEMBLY,
    MSIL_MIXED,
    RAW_BINARY,
}

data class ObjectMetadata(
    val entryPoint: Long? = null,
    val stackSize: Long? = null,
    val heapSize: Long? = null,
    val flags: Set<ObjectFlag> = emptySet(),
    val osAbi: OsAbi = OsAbi.NONE,
    val moduleName: String? = null,
    val properties: Map<String, String> = emptyMap(),
)

enum class ObjectFlag {
    EXECUTABLE, RELOCATABLE, SHARED_LIBRARY, DLL, CORE_DUMP,
    POSITION_INDEPENDENT, LARGE_ADDRESS_AWARE,
    NO_EXEC_STACK, RELRO, BIND_NOW,
}

enum class OsAbi {
    NONE, LINUX, FREEBSD, NETBSD, OPENBSD, SOLARIS,
    MACOS, WINDOWS, IOS, ANDROID, WASI,
}
