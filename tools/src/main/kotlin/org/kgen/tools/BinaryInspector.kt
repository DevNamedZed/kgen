package org.kgen.tools

import org.kgen.binary.*

// Binary inspection — programmatic objdump / readelf / dumpbin / otool

interface BinaryInspector {

    fun inspect(bytes: ByteArray): ObjectFile

    // Header info
    fun headers(bytes: ByteArray): HeaderInfo

    // Sections
    fun sections(bytes: ByteArray): List<SectionInfo>
    fun sectionData(bytes: ByteArray, sectionName: String): ByteArray?

    // Symbols
    fun symbols(bytes: ByteArray): List<SymbolInfo>
    fun dynamicSymbols(bytes: ByteArray): List<SymbolInfo>
    fun exports(bytes: ByteArray): List<ExportInfo>
    fun imports(bytes: ByteArray): List<ImportInfo>

    // Relocations
    fun relocations(bytes: ByteArray): List<RelocationInfo>

    // Debug
    fun debugInfo(bytes: ByteArray): DebugInfo?
    fun lineInfo(bytes: ByteArray): List<LineMapping>

    // Dynamic
    fun dependencies(bytes: ByteArray): List<String>
    fun dynamicEntries(bytes: ByteArray): List<DynamicEntry>

    // Strings
    fun strings(bytes: ByteArray, minLength: Int = 4): List<FoundString>
}

data class HeaderInfo(
    val format: ObjectFormat,
    val arch: Architecture,
    val type: String,              // "executable", "shared library", "relocatable", etc.
    val entryPoint: Long?,
    val flags: Set<String>,
    val properties: Map<String, String>,
)

data class SectionInfo(
    val index: Int,
    val name: String,
    val kind: SectionKind,
    val address: Long,
    val size: Long,
    val offset: Long,
    val align: Int,
    val flags: Set<SectionFlag>,
    val entrySize: Long,
)

data class SymbolInfo(
    val name: String,
    val value: Long,
    val size: Long,
    val binding: SymbolBinding,
    val kind: SymbolKind,
    val visibility: SymbolVisibility,
    val section: String?,
    val version: String?,
    val demangled: String?,
)

data class ExportInfo(
    val name: String,
    val address: Long,
    val ordinal: Int?,
)

data class ImportInfo(
    val name: String,
    val module: String,
    val ordinal: Int?,
    val isDelayLoad: Boolean,
)

data class RelocationInfo(
    val offset: Long,
    val symbol: String,
    val type: String,
    val addend: Long,
    val section: String,
)

data class LineMapping(
    val address: Long,
    val file: String,
    val line: Int,
    val column: Int,
)

data class DynamicEntry(
    val tag: String,
    val value: Long,
    val stringValue: String?,
)

data class FoundString(
    val offset: Long,
    val section: String?,
    val value: String,
)
