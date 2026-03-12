package org.kgen.binary.inspect

import org.kgen.binary.*
import org.kgen.binary.elf.*

class ElfInspector : BinaryInspector {

    override fun inspect(bytes: ByteArray): ObjectFile = ElfReader.toObjectFile(ElfReader.read(bytes))

    override fun headers(bytes: ByteArray): HeaderInfo {
        val elf = ElfReader.read(bytes)
        val h = elf.header
        val arch = when (h.machine) {
            ElfMachine.X86_64 -> Architecture(ArchType.X86_64)
            ElfMachine.AARCH64 -> Architecture(ArchType.AARCH64)
            ElfMachine.RISCV -> Architecture(ArchType.RISCV64)
            else -> Architecture(ArchType.X86_64)
        }
        val type = when (h.type) {
            ElfObjectType.REL -> "relocatable"
            ElfObjectType.EXEC -> "executable"
            ElfObjectType.DYN -> "shared object"
            else -> "unknown"
        }
        val flags = mutableSetOf<String>()
        if (h.flags != 0) flags.add("flags=0x${h.flags.toString(16)}")

        val props = mutableMapOf<String, String>()
        props["class"] = h.elfClass.name
        props["encoding"] = h.dataEncoding.name
        props["osAbi"] = h.osAbi.toString()
        if (h.programHeaderCount > 0) props["programHeaders"] = h.programHeaderCount.toString()
        props["sectionHeaders"] = h.sectionHeaderCount.toString()

        return HeaderInfo(
            format = ObjectFormat.ELF,
            arch = arch,
            type = type,
            entryPoint = if (h.type != ElfObjectType.REL && h.entryPoint != 0L) h.entryPoint else null,
            flags = flags,
            properties = props,
        )
    }

    override fun sections(bytes: ByteArray): List<SectionInfo> {
        val elf = ElfReader.read(bytes)
        return elf.sections.mapNotNull { s ->
            if (s.index == 0 && s.name.isEmpty()) return@mapNotNull null
            val kind = classifySectionKind(s)
            val flags = classifySectionFlags(s.flags)
            SectionInfo(
                index = s.index,
                name = s.name,
                kind = kind,
                address = s.address,
                size = s.size,
                offset = s.offset,
                align = s.alignment.toInt(),
                flags = flags,
                entrySize = s.entrySize,
            )
        }
    }

    override fun sectionData(bytes: ByteArray, sectionName: String): ByteArray? {
        val elf = ElfReader.read(bytes)
        return elf.sectionByName(sectionName)?.data
    }

    override fun symbols(bytes: ByteArray): List<SymbolInfo> {
        val elf = ElfReader.read(bytes)
        return elf.symbols.map { it.toSymbolInfo() }
    }

    override fun dynamicSymbols(bytes: ByteArray): List<SymbolInfo> {
        val elf = ElfReader.read(bytes)
        return elf.dynamicSymbols.map { it.toSymbolInfo() }
    }

    override fun exports(bytes: ByteArray): List<ExportInfo> {
        val elf = ElfReader.read(bytes)
        return elf.dynamicSymbols
            .filter { !it.isUndefined && it.isGlobal }
            .map { ExportInfo(name = it.name, address = it.value, ordinal = null) }
    }

    override fun imports(bytes: ByteArray): List<ImportInfo> {
        val elf = ElfReader.read(bytes)
        val needed = elf.dynamicInfo?.neededLibraries.orEmpty()
        return elf.dynamicSymbols
            .filter { it.isUndefined }
            .map { sym ->
                ImportInfo(
                    name = sym.name,
                    module = needed.firstOrNull() ?: "",
                    ordinal = null,
                    isDelayLoad = false,
                )
            }
    }

    override fun relocations(bytes: ByteArray): List<RelocationInfo> {
        val elf = ElfReader.read(bytes)
        return elf.relocations.map { rel ->
            RelocationInfo(
                offset = rel.offset,
                symbol = rel.symbolName,
                type = resolveRelocTypeName(elf.header.machine, rel.type),
                addend = rel.addend,
                section = rel.sectionName ?: "",
            )
        }
    }

    override fun debugInfo(bytes: ByteArray): DebugInfo? {
        val elf = ElfReader.read(bytes)
        val hasDebug = elf.sections.any { it.name.startsWith(".debug_") }
        if (!hasDebug) return null
        return DebugInfo(format = DebugFormat.DWARF5)
    }

    override fun lineInfo(bytes: ByteArray): List<LineMapping> = emptyList()

    override fun dependencies(bytes: ByteArray): List<String> {
        val elf = ElfReader.read(bytes)
        return elf.dynamicInfo?.neededLibraries.orEmpty()
    }

    override fun dynamicEntries(bytes: ByteArray): List<DynamicEntry> {
        val elf = ElfReader.read(bytes)
        return elf.dynamicInfo?.entries?.map { e ->
            DynamicEntry(
                tag = e.tag?.name ?: "0x${e.tagCode.toString(16)}",
                value = e.value,
                stringValue = null,
            )
        }.orEmpty()
    }

    override fun strings(bytes: ByteArray, minLength: Int): List<FoundString> {
        val elf = ElfReader.read(bytes)
        val result = mutableListOf<FoundString>()
        for (section in elf.sections) {
            if (section.data.isEmpty()) continue
            extractStrings(section.data, minLength).forEach { (offset, str) ->
                result.add(FoundString(
                    offset = section.offset + offset,
                    section = section.name,
                    value = str,
                ))
            }
        }
        return result
    }

    private fun ElfSymbolEntry.toSymbolInfo(): SymbolInfo {
        val binding = when (this.binding) {
            ElfSymbolBinding.LOCAL -> SymbolBinding.LOCAL
            ElfSymbolBinding.GLOBAL -> SymbolBinding.GLOBAL
            ElfSymbolBinding.WEAK -> SymbolBinding.WEAK
            else -> SymbolBinding.GLOBAL
        }
        val kind = when {
            isUndefined -> SymbolKind.UNDEFINED
            isAbsolute -> SymbolKind.ABSOLUTE
            type == ElfSymbolType.FUNC -> SymbolKind.FUNCTION
            type == ElfSymbolType.OBJECT -> SymbolKind.DATA
            type == ElfSymbolType.SECTION -> SymbolKind.SECTION
            type == ElfSymbolType.FILE -> SymbolKind.FILE
            type == ElfSymbolType.COMMON -> SymbolKind.COMMON
            type == ElfSymbolType.TLS -> SymbolKind.TLS
            type == ElfSymbolType.GNU_IFUNC -> SymbolKind.IFUNC
            else -> SymbolKind.DATA
        }
        val vis = when (this.visibility) {
            ElfSymbolVisibility.DEFAULT -> SymbolVisibility.DEFAULT
            ElfSymbolVisibility.HIDDEN -> SymbolVisibility.HIDDEN
            ElfSymbolVisibility.PROTECTED -> SymbolVisibility.PROTECTED
            else -> SymbolVisibility.DEFAULT
        }
        return SymbolInfo(
            name = name, value = value, size = size,
            binding = binding, kind = kind, visibility = vis,
            section = sectionName, version = null, demangled = null,
        )
    }

    private fun resolveRelocTypeName(machine: ElfMachine?, type: Int): String = when (machine) {
        ElfMachine.X86_64 -> when (type) {
            0 -> "R_X86_64_NONE"; 1 -> "R_X86_64_64"; 2 -> "R_X86_64_PC32"
            4 -> "R_X86_64_PLT32"; 10 -> "R_X86_64_32"; 11 -> "R_X86_64_32S"
            else -> "R_X86_64_$type"
        }
        ElfMachine.AARCH64 -> "R_AARCH64_$type"
        else -> "R_UNKNOWN_$type"
    }

    private fun classifySectionKind(entry: ElfSectionEntry): SectionKind {
        val name = entry.name
        val type = entry.type
        val flags = entry.flags
        return when {
            type == ElfSectionType.SYMTAB -> SectionKind.SYMTAB
            type == ElfSectionType.DYNSYM -> SectionKind.DYNSYM
            type == ElfSectionType.STRTAB -> SectionKind.STRTAB
            type == ElfSectionType.RELA -> SectionKind.RELA
            type == ElfSectionType.REL -> SectionKind.REL
            type == ElfSectionType.DYNAMIC -> SectionKind.DYNAMIC
            type == ElfSectionType.NOTE -> SectionKind.NOTE
            type == ElfSectionType.NOBITS -> SectionKind.BSS
            name == ".text" -> SectionKind.TEXT
            name == ".data" -> SectionKind.DATA
            name == ".rodata" || name.startsWith(".rodata.") -> SectionKind.RODATA
            name.startsWith(".debug_") -> SectionKind.DEBUG_INFO
            type == ElfSectionType.PROGBITS && flags and ElfSectionFlags.EXECINSTR != 0L -> SectionKind.TEXT
            type == ElfSectionType.PROGBITS && flags and ElfSectionFlags.WRITE != 0L -> SectionKind.DATA
            type == ElfSectionType.PROGBITS && flags and ElfSectionFlags.ALLOC != 0L -> SectionKind.RODATA
            else -> SectionKind.UNKNOWN
        }
    }

    private fun classifySectionFlags(flags: Long): Set<SectionFlag> {
        val result = mutableSetOf<SectionFlag>()
        if (flags and ElfSectionFlags.ALLOC != 0L) result.add(SectionFlag.ALLOC)
        if (flags and ElfSectionFlags.WRITE != 0L) result.add(SectionFlag.WRITE)
        if (flags and ElfSectionFlags.EXECINSTR != 0L) result.add(SectionFlag.EXEC)
        return result
    }
}
