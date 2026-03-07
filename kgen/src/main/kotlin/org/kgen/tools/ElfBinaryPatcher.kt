package org.kgen.tools

import org.kgen.binary.*
import org.kgen.binary.elf.*

class ElfBinaryPatcher : BinaryPatcher {

    override fun load(bytes: ByteArray): BinaryPatcher.PatchableBinary {
        val elf = ElfReader.read(bytes)
        return ElfPatchable(elf)
    }

    private class ElfPatchable(private var elf: ElfFile) : BinaryPatcher.PatchableBinary {

        override val format: ObjectFormat get() = ObjectFormat.ELF
        override val arch: Architecture get() = Architecture(
            when (elf.header.machine) {
                ElfMachine.X86_64 -> ArchType.X86_64
                ElfMachine.AARCH64 -> ArchType.AARCH64
                ElfMachine.RISCV -> ArchType.RISCV64
                else -> ArchType.X86_64
            }
        )

        override fun readSection(name: String): ByteArray? =
            elf.sections.firstOrNull { it.name == name }?.data

        override fun writeSection(name: String, data: ByteArray) {
            val idx = elf.sections.indexOfFirst { it.name == name }
            if (idx < 0) throw IllegalArgumentException("Section not found: $name")
            val updated = elf.sections.toMutableList()
            updated[idx] = updated[idx].copy(data = data, size = data.size.toLong())
            elf = elf.copy(sections = updated)
        }

        override fun addSection(name: String, kind: SectionKind, data: ByteArray, flags: Set<SectionFlag>) {
            val shFlags = sectionFlagsToElf(kind, flags)
            val shType = sectionKindToElfType(kind)
            val nextIndex = (elf.sections.maxOfOrNull { it.index } ?: 0) + 1
            val newSection = ElfSectionEntry(
                index = nextIndex, name = name, type = shType, flags = shFlags,
                address = 0, offset = 0, size = data.size.toLong(),
                link = 0, info = 0, alignment = 16, entrySize = 0,
                data = data
            )
            elf = elf.copy(sections = elf.sections + newSection)
        }

        override fun removeSection(name: String) {
            elf = elf.copy(sections = elf.sections.filter { it.name != name })
        }

        override fun renameSection(oldName: String, newName: String) {
            val updated = elf.sections.map {
                if (it.name == oldName) it.copy(name = newName) else it
            }
            elf = elf.copy(sections = updated)
        }

        override fun symbols(): List<Symbol> = elf.symbols.map { elfSymToSymbol(it) }

        override fun findSymbol(name: String): Symbol? =
            elf.symbols.firstOrNull { it.name == name }?.let { elfSymToSymbol(it) }

        override fun renameSymbol(oldName: String, newName: String) {
            val updated = elf.symbols.map {
                if (it.name == oldName) it.copy(name = newName) else it
            }
            elf = elf.copy(symbols = updated)
        }

        override fun addSymbol(symbol: Symbol) {
            val elfSym = symbolToElfSym(symbol)
            elf = elf.copy(symbols = elf.symbols + elfSym)
        }

        override fun removeSymbol(name: String) {
            elf = elf.copy(symbols = elf.symbols.filter { it.name != name })
        }

        override fun setSymbolVisibility(name: String, visibility: SymbolVisibility) {
            val elfVis = when (visibility) {
                SymbolVisibility.DEFAULT -> ElfSymbolVisibility.DEFAULT
                SymbolVisibility.HIDDEN -> ElfSymbolVisibility.HIDDEN
                SymbolVisibility.PROTECTED -> ElfSymbolVisibility.PROTECTED
                SymbolVisibility.INTERNAL -> ElfSymbolVisibility.HIDDEN
            }
            val updated = elf.symbols.map {
                if (it.name == name) it.copy(visibility = elfVis) else it
            }
            elf = elf.copy(symbols = updated)
        }

        override fun readBytes(section: String, offset: Long, length: Int): ByteArray {
            val sec = elf.sections.firstOrNull { it.name == section }
                ?: throw IllegalArgumentException("Section not found: $section")
            return sec.data.copyOfRange(offset.toInt(), offset.toInt() + length)
        }

        override fun writeBytes(section: String, offset: Long, data: ByteArray) {
            val idx = elf.sections.indexOfFirst { it.name == section }
            if (idx < 0) throw IllegalArgumentException("Section not found: $section")
            val sec = elf.sections[idx]
            val newData = sec.data.copyOf()
            data.copyInto(newData, offset.toInt())
            val updated = elf.sections.toMutableList()
            updated[idx] = sec.copy(data = newData)
            elf = elf.copy(sections = updated)
        }

        override fun patchInstruction(section: String, offset: Long, newInstruction: ByteArray) {
            writeBytes(section, offset, newInstruction)
        }

        override fun nopOut(section: String, offset: Long, length: Int) {
            val nops = ByteArray(length) { 0x90.toByte() }
            writeBytes(section, offset, nops)
        }

        override fun addImport(entry: ImportEntry) {}

        override fun removeImport(symbolName: String) {}

        override fun addExport(entry: ExportEntry) {}

        override fun removeExport(symbolName: String) {}

        override fun dependencies(): List<String> {
            return elf.dynamicInfo?.neededLibraries ?: emptyList()
        }

        override fun addDependency(name: String) {
            val info = elf.dynamicInfo ?: return
            elf = elf.copy(dynamicInfo = info.copy(neededLibraries = info.neededLibraries + name))
        }

        override fun removeDependency(name: String) {
            val info = elf.dynamicInfo ?: return
            elf = elf.copy(dynamicInfo = info.copy(neededLibraries = info.neededLibraries.filter { it != name }))
        }

        override fun renameDependency(oldName: String, newName: String) {
            val info = elf.dynamicInfo ?: return
            elf = elf.copy(dynamicInfo = info.copy(
                neededLibraries = info.neededLibraries.map { if (it == oldName) newName else it }
            ))
        }

        override fun rpath(): List<String> {
            val info = elf.dynamicInfo ?: return emptyList()
            return info.rpath + info.runpath
        }

        override fun addRpath(path: String) {
            val info = elf.dynamicInfo ?: return
            elf = elf.copy(dynamicInfo = info.copy(runpath = info.runpath + path))
        }

        override fun removeRpath(path: String) {
            val info = elf.dynamicInfo ?: return
            elf = elf.copy(dynamicInfo = info.copy(
                runpath = info.runpath.filter { it != path },
                rpath = info.rpath.filter { it != path }
            ))
        }

        override fun setRpath(paths: List<String>) {
            val info = elf.dynamicInfo ?: return
            elf = elf.copy(dynamicInfo = info.copy(
                runpath = paths,
                rpath = emptyList()
            ))
        }

        override fun entryPoint(): Long? {
            val ep = elf.header.entryPoint
            return if (ep != 0L) ep else null
        }

        override fun setEntryPoint(address: Long) {
            elf = elf.copy(header = elf.header.copy(entryPoint = address))
        }

        override fun assemble(): ByteArray {
            val obj = ElfReader.toObjectFile(elf)
            val machine = elf.header.machine?.code ?: ElfMachine.X86_64.code
            return ElfObjectWriter(machine).write(obj)
        }

        private fun elfSymToSymbol(sym: ElfSymbolEntry): Symbol = Symbol(
            name = sym.name,
            value = sym.value,
            size = sym.size,
            section = if (sym.sectionIndex != 0) sym.sectionName else null,
            binding = when (sym.binding) {
                ElfSymbolBinding.LOCAL -> SymbolBinding.LOCAL
                ElfSymbolBinding.GLOBAL -> SymbolBinding.GLOBAL
                ElfSymbolBinding.WEAK -> SymbolBinding.WEAK
                else -> SymbolBinding.GLOBAL
            },
            kind = when (sym.type) {
                ElfSymbolType.FUNC -> SymbolKind.FUNCTION
                ElfSymbolType.OBJECT -> SymbolKind.DATA
                ElfSymbolType.SECTION -> SymbolKind.SECTION
                ElfSymbolType.FILE -> SymbolKind.FILE
                ElfSymbolType.TLS -> SymbolKind.TLS
                ElfSymbolType.GNU_IFUNC -> SymbolKind.IFUNC
                ElfSymbolType.NOTYPE -> if (sym.sectionIndex == 0) SymbolKind.UNDEFINED else SymbolKind.DATA
                else -> SymbolKind.DATA
            },
            visibility = when (sym.visibility) {
                ElfSymbolVisibility.DEFAULT -> SymbolVisibility.DEFAULT
                ElfSymbolVisibility.HIDDEN -> SymbolVisibility.HIDDEN
                ElfSymbolVisibility.PROTECTED -> SymbolVisibility.PROTECTED
                else -> SymbolVisibility.DEFAULT
            }
        )

        private fun symbolToElfSym(sym: Symbol): ElfSymbolEntry {
            val sectionIdx = sym.section?.let { name ->
                elf.sections.indexOfFirst { it.name == name }.takeIf { it >= 0 }
            } ?: 0
            val sectionName = sym.section
            return ElfSymbolEntry(
                name = sym.name,
                value = sym.value,
                size = sym.size,
                binding = when (sym.binding) {
                    SymbolBinding.LOCAL -> ElfSymbolBinding.LOCAL
                    SymbolBinding.GLOBAL -> ElfSymbolBinding.GLOBAL
                    SymbolBinding.WEAK -> ElfSymbolBinding.WEAK
                    SymbolBinding.GNU_UNIQUE -> ElfSymbolBinding.GLOBAL
                },
                type = when (sym.kind) {
                    SymbolKind.FUNCTION -> ElfSymbolType.FUNC
                    SymbolKind.DATA -> ElfSymbolType.OBJECT
                    SymbolKind.SECTION -> ElfSymbolType.SECTION
                    SymbolKind.FILE -> ElfSymbolType.FILE
                    SymbolKind.TLS -> ElfSymbolType.TLS
                    SymbolKind.IFUNC -> ElfSymbolType.GNU_IFUNC
                    SymbolKind.UNDEFINED -> ElfSymbolType.NOTYPE
                    else -> ElfSymbolType.NOTYPE
                },
                visibility = when (sym.visibility) {
                    SymbolVisibility.DEFAULT -> ElfSymbolVisibility.DEFAULT
                    SymbolVisibility.HIDDEN -> ElfSymbolVisibility.HIDDEN
                    SymbolVisibility.PROTECTED -> ElfSymbolVisibility.PROTECTED
                    SymbolVisibility.INTERNAL -> ElfSymbolVisibility.HIDDEN
                },
                sectionIndex = sectionIdx,
                sectionName = sectionName
            )
        }

        private fun sectionKindToElfType(kind: SectionKind): ElfSectionType? = when (kind) {
            SectionKind.BSS, SectionKind.TBSS -> ElfSectionType.NOBITS
            SectionKind.NOTE, SectionKind.GNU_BUILD_ID -> ElfSectionType.NOTE
            SectionKind.INIT_ARRAY -> ElfSectionType.INIT_ARRAY
            SectionKind.FINI_ARRAY -> ElfSectionType.FINI_ARRAY
            else -> ElfSectionType.PROGBITS
        }

        private fun sectionFlagsToElf(kind: SectionKind, flags: Set<SectionFlag>): Long {
            var f = 0L
            if (SectionFlag.ALLOC in flags || kind in setOf(SectionKind.TEXT, SectionKind.DATA, SectionKind.RODATA, SectionKind.BSS))
                f = f or ElfSectionFlags.ALLOC
            if (SectionFlag.WRITE in flags || kind == SectionKind.DATA || kind == SectionKind.BSS)
                f = f or ElfSectionFlags.WRITE
            if (SectionFlag.EXEC in flags || kind == SectionKind.TEXT)
                f = f or ElfSectionFlags.EXECINSTR
            return f
        }
    }
}
