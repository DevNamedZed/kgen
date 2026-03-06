package org.kgen.linker

import org.kgen.binary.*

// Linker — combines object files into executables, shared libs, or merged objects

interface Linker {
    fun link(input: LinkInput): LinkOutput
}

data class LinkInput(
    val objects: List<ObjectFile>,
    val libraries: List<Library> = emptyList(),
    val outputType: OutputType,
    val outputFormat: ObjectFormat,
    val architecture: Architecture,
    val entryPoint: String? = null,               // _start, main, _DllMainCRTStartup, etc.
    val options: LinkOptions = LinkOptions(),
)

data class LinkOutput(
    val bytes: ByteArray,
    val format: ObjectFormat,
    val symbols: List<Symbol>,                    // final symbol table
    val warnings: List<String> = emptyList(),
    val mapFile: String? = null,                  // optional linker map
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LinkOutput) return false
        return bytes.contentEquals(other.bytes) && format == other.format
    }
    override fun hashCode(): Int = bytes.contentHashCode() * 31 + format.hashCode()
}

enum class OutputType {
    EXECUTABLE,
    SHARED_LIBRARY,       // .so / .dylib / .dll
    STATIC_LIBRARY,       // .a / .lib (archive)
    RELOCATABLE,          // merged .o (ld -r)
    PIE,                  // position-independent executable
}

data class LinkOptions(
    // Symbol resolution
    val allowUndefined: Boolean = false,
    val exportDynamic: Boolean = false,
    val asNeeded: Boolean = false,
    val wholeArchive: Boolean = false,
    val gcSections: Boolean = true,          // remove unused sections
    val icf: ICFMode = ICFMode.NONE,         // identical code folding
    val stripAll: Boolean = false,
    val stripDebug: Boolean = false,
    val keepSymbols: Set<String> = emptySet(),
    val undefinedSymbolPolicy: UndefinedPolicy = UndefinedPolicy.ERROR,

    // Layout
    val baseAddress: Long? = null,
    val sectionAlignment: Long? = null,
    val fileAlignment: Long? = null,
    val stackSize: Long? = null,
    val heapSize: Long? = null,
    val textSegmentAddress: Long? = null,
    val dataSegmentAddress: Long? = null,

    // Dynamic linking
    val soName: String? = null,              // DT_SONAME
    val rpath: List<String> = emptyList(),
    val runpath: List<String> = emptyList(),
    val dynamicLinker: String? = null,       // .interp
    val pie: Boolean = false,
    val relro: RelroMode = RelroMode.FULL,
    val bindNow: Boolean = false,
    val noExecStack: Boolean = true,

    // PE specific
    val subsystem: PESubsystem? = null,
    val dllCharacteristics: Set<DLLCharacteristic> = emptySet(),
    val manifestFile: String? = null,
    val defFile: String? = null,             // .def file for exports
    val implib: Boolean = false,             // generate import library

    // Mach-O specific
    val installName: String? = null,
    val minOsVersion: String? = null,
    val sdkVersion: String? = null,
    val platform: ApplePlatform? = null,
    val headerPad: Long? = null,

    // LTO
    val lto: LTOMode = LTOMode.NONE,
    val ltoPartitions: Int = 1,
    val ltoOptLevel: Int = 2,

    // Map file / diagnostics
    val emitMap: Boolean = false,
    val verbose: Boolean = false,
    val printGCedSections: Boolean = false,

    // Version script
    val versionScript: VersionScript? = null,
    val exportedSymbols: Set<String>? = null,  // explicit export list
    val hiddenSymbols: Set<String> = emptySet(),

    // Linker script
    val linkerScript: String? = null,
)

enum class ICFMode { NONE, SAFE, ALL }
enum class UndefinedPolicy { ERROR, WARN, IGNORE }
enum class RelroMode { NONE, PARTIAL, FULL }
enum class LTOMode { NONE, FULL, THIN }
enum class ApplePlatform { MACOS, IOS, TVOS, WATCHOS, VISIONOS, MACCATALYST, IOS_SIMULATOR, TVOS_SIMULATOR, WATCHOS_SIMULATOR }

// Library reference

sealed interface Library {
    val name: String

    data class Static(override val name: String, val path: String) : Library
    data class Shared(override val name: String, val path: String) : Library
    data class Framework(override val name: String, val path: String? = null) : Library   // macOS
    data class SystemLib(override val name: String) : Library                              // -lfoo
    data class Archive(override val name: String, val objects: List<ObjectFile>) : Library // .a in memory
}

// Version script (ELF)

data class VersionScript(
    val versions: List<VersionBlock>,
)

data class VersionBlock(
    val name: String,              // e.g., "MYLIB_1.0"
    val parent: String? = null,
    val globals: List<String>,     // symbol patterns to export
    val locals: List<String> = listOf("*"),  // hide everything else
)
