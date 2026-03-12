package org.kgen.binary.patch

import org.kgen.binary.*

// Binary patching — modify executables, libraries, and object files in place

interface BinaryPatcher {

    fun load(bytes: ByteArray): PatchableBinary

    interface PatchableBinary {

        val format: ObjectFormat
        val arch: Architecture

        // --- Section manipulation ---
        fun readSection(name: String): ByteArray?
        fun writeSection(name: String, data: ByteArray)
        fun addSection(name: String, kind: SectionKind, data: ByteArray, flags: Set<SectionFlag> = emptySet())
        fun removeSection(name: String)
        fun renameSection(oldName: String, newName: String)

        // --- Symbol manipulation ---
        fun symbols(): List<Symbol>
        fun findSymbol(name: String): Symbol?
        fun renameSymbol(oldName: String, newName: String)
        fun addSymbol(symbol: Symbol)
        fun removeSymbol(name: String)
        fun setSymbolVisibility(name: String, visibility: SymbolVisibility)

        // --- Code patching ---
        fun readBytes(section: String, offset: Long, length: Int): ByteArray
        fun writeBytes(section: String, offset: Long, data: ByteArray)
        fun patchInstruction(section: String, offset: Long, newInstruction: ByteArray)
        fun nopOut(section: String, offset: Long, length: Int)

        // --- Import/Export manipulation ---
        fun addImport(entry: ImportEntry)
        fun removeImport(symbolName: String)
        fun addExport(entry: ExportEntry)
        fun removeExport(symbolName: String)

        // --- Dependency manipulation ---
        fun dependencies(): List<String>
        fun addDependency(name: String)
        fun removeDependency(name: String)
        fun renameDependency(oldName: String, newName: String)

        // --- RPATH manipulation (ELF/Mach-O) ---
        fun rpath(): List<String>
        fun addRpath(path: String)
        fun removeRpath(path: String)
        fun setRpath(paths: List<String>)

        // --- Entry point ---
        fun entryPoint(): Long?
        fun setEntryPoint(address: Long)

        // --- Emit ---
        fun assemble(): ByteArray
    }
}
