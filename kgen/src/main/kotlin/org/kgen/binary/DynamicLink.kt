package org.kgen.binary

/**
 * An imported symbol — a function or data reference resolved at link/load time
 * from an external module (shared library, DLL, WASM import).
 *
 * @property symbolName The symbol being imported.
 * @property moduleName Source module (DLL name, .so name, WASM module).
 * @property ordinal PE import-by-ordinal (null for import-by-name).
 * @property isDelayLoad PE delay-loaded import (resolved on first call, not at load time).
 * @property kind What is being imported (function, data, TLS, WASM table/memory/global).
 */
data class ImportEntry(
    val symbolName: String,
    val moduleName: String,              // DLL name (PE) / shared lib (ELF) / WASM module
    val ordinal: Int? = null,            // PE: import by ordinal
    val isDelayLoad: Boolean = false,    // PE: delay-load
    val kind: ImportKind = ImportKind.FUNCTION,
)

data class ExportEntry(
    val symbolName: String,
    val exportName: String? = null,      // if different from symbol name
    val ordinal: Int? = null,            // PE export ordinal
    val isForwarder: Boolean = false,    // PE: forwarded export
    val forwarderName: String? = null,   // PE: "other_dll.function"
    val kind: ExportKind = ExportKind.FUNCTION,
)

enum class ImportKind { FUNCTION, DATA, TLS, WASM_TABLE, WASM_MEMORY, WASM_GLOBAL, WASM_TAG }
enum class ExportKind { FUNCTION, DATA, TLS, WASM_TABLE, WASM_MEMORY, WASM_GLOBAL, WASM_TAG }

data class DynamicLinkInfo(
    val neededLibraries: List<String> = emptyList(),        // DT_NEEDED / LC_LOAD_DYLIB
    val soName: String? = null,                             // DT_SONAME
    val rpath: List<String> = emptyList(),                  // DT_RPATH / DT_RUNPATH
    val initFunction: String? = null,                       // DT_INIT
    val finiFunction: String? = null,                       // DT_FINI
    val initArray: List<String> = emptyList(),              // DT_INIT_ARRAY
    val finiArray: List<String> = emptyList(),              // DT_FINI_ARRAY
    val symbolVersions: List<SymbolVersion> = emptyList(),
    val versionDefinitions: List<VersionDefinition> = emptyList(),
    val versionNeeds: List<VersionNeed> = emptyList(),
    val flags: Set<DynamicFlag> = emptySet(),

    // PLT/GOT info
    val pltType: PLTType = PLTType.LAZY,
    val gotEntries: List<GOTEntry> = emptyList(),
    val pltEntries: List<PLTEntry> = emptyList(),

    // PE specific
    val imageBase: Long? = null,
    val sectionAlignment: Long? = null,
    val fileAlignment: Long? = null,
    val subsystem: PESubsystem? = null,
    val dllCharacteristics: Set<DLLCharacteristic> = emptySet(),
    val importDirectory: List<ImportDirectory> = emptyList(),
    val exportDirectory: ExportDirectory? = null,
    val tlsDirectory: TLSDirectory? = null,

    // Mach-O specific
    val installName: String? = null,       // LC_ID_DYLIB
    val currentVersion: Long? = null,
    val compatVersion: Long? = null,
    val reexportedLibraries: List<String> = emptyList(),  // LC_REEXPORT_DYLIB
    val weakLibraries: List<String> = emptyList(),        // LC_LOAD_WEAK_DYLIB
    val twoLevelNamespace: Boolean = true,

    // JVM
    val classPath: List<String> = emptyList(),
    val modulePath: List<String> = emptyList(),
    val moduleInfo: JvmModuleInfo? = null,
)

data class SymbolVersion(val symbolName: String, val version: String, val isDefault: Boolean = true)
data class VersionDefinition(val name: String, val version: Int, val flags: Int = 0, val parent: String? = null)
data class VersionNeed(val fileName: String, val entries: List<VersionNeedEntry>)
data class VersionNeedEntry(val name: String, val version: Int, val flags: Int = 0)

data class GOTEntry(val symbolName: String, val offset: Long, val isResolved: Boolean = false)
data class PLTEntry(val symbolName: String, val offset: Long, val gotSlot: Long)

enum class PLTType { LAZY, EAGER, NONE }

enum class DynamicFlag {
    BIND_NOW,           // resolve all symbols at load time
    SYMBOLIC,           // prefer local symbols
    TEXTREL,            // text relocations (bad but sometimes needed)
    STATIC_TLS,         // uses static TLS model
    NOW,                // DT_FLAGS_1: bind now
    NODELETE,           // DT_FLAGS_1: cannot dlclose
    NODUMP,             // DT_FLAGS_1: not dumpable
    PIE,                // position-independent executable
    RELRO,              // read-only after relocation
}

// PE-specific structures

data class ImportDirectory(
    val dllName: String,
    val entries: List<ImportDirectoryEntry>,
    val isDelayLoad: Boolean = false,
)

data class ImportDirectoryEntry(
    val name: String?,              // null if by ordinal
    val ordinal: Int?,
    val hint: Int? = null,
    val thunkRVA: Long = 0,
)

data class ExportDirectory(
    val name: String,
    val ordinalBase: Int = 1,
    val entries: List<ExportDirectoryEntry>,
    val timestamp: Long = 0,
    val majorVersion: Int = 0,
    val minorVersion: Int = 0,
)

data class ExportDirectoryEntry(
    val name: String?,
    val ordinal: Int,
    val rva: Long,
    val forwarderName: String? = null,
)

data class TLSDirectory(
    val rawDataStart: Long,
    val rawDataEnd: Long,
    val indexAddress: Long,
    val callbacksAddress: Long,
    val zeroFillSize: Long = 0,
    val characteristics: Int = 0,
)

enum class PESubsystem(val value: Int) {
    UNKNOWN(0),
    NATIVE(1),
    WINDOWS_GUI(2),
    WINDOWS_CUI(3),
    OS2_CUI(5),
    POSIX_CUI(7),
    WINDOWS_CE_GUI(9),
    EFI_APPLICATION(10),
    EFI_BOOT_SERVICE_DRIVER(11),
    EFI_RUNTIME_DRIVER(12),
    EFI_ROM(13),
    XBOX(14),
    WINDOWS_BOOT_APPLICATION(16),
}

enum class DLLCharacteristic {
    HIGH_ENTROPY_VA,
    DYNAMIC_BASE,       // ASLR
    FORCE_INTEGRITY,
    NX_COMPAT,          // DEP
    NO_ISOLATION,
    NO_SEH,
    NO_BIND,
    APPCONTAINER,
    WDM_DRIVER,
    GUARD_CF,           // Control Flow Guard
    TERMINAL_SERVER_AWARE,
}

// JVM module info (Java 9+)

data class JvmModuleInfo(
    val name: String,
    val version: String? = null,
    val requires: List<JvmModuleRequires> = emptyList(),
    val exports: List<JvmModuleExports> = emptyList(),
    val opens: List<JvmModuleOpens> = emptyList(),
    val uses: List<String> = emptyList(),
    val provides: List<JvmModuleProvides> = emptyList(),
)

data class JvmModuleRequires(val module: String, val isTransitive: Boolean = false, val isStatic: Boolean = false)
data class JvmModuleExports(val packageName: String, val to: List<String> = emptyList())
data class JvmModuleOpens(val packageName: String, val to: List<String> = emptyList())
data class JvmModuleProvides(val service: String, val implementations: List<String>)
