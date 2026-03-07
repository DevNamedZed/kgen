package org.kgen.binary.macho

import org.kgen.binary.*

internal object MachOObjectFileProjection {

    fun project(macho: MachOFile): ObjectFile {
        val sections = projectSections(macho)
        val symbols = projectSymbols(macho)
        val relocations = projectRelocations(macho)
        val arch = resolveArchitecture(macho)
        val metadata = buildMetadata(macho)

        return ObjectFile(
            format = ObjectFormat.MACH_O,
            arch = arch,
            sections = sections,
            symbols = symbols,
            relocations = relocations,
            metadata = metadata,
            dynamicInfo = projectDynamicInfo(macho),
        )
    }

    private fun projectSections(macho: MachOFile): List<Section> {
        return macho.allSections.mapIndexed { i, s ->
            Section(
                name = s.sectionName,
                kind = classifySectionKind(s),
                data = s.data,
                address = s.address,
                align = if (s.align > 0) 1 shl s.align else 1,
                flags = classifySectionFlags(s),
                index = i + 1,
            )
        }
    }

    private fun classifySectionKind(s: MachOSection): SectionKind {
        val seg = s.segmentName
        val sect = s.sectionName
        return when {
            seg == "__TEXT" && sect == "__text" -> SectionKind.TEXT
            seg == "__TEXT" && sect == "__cstring" -> SectionKind.RODATA
            seg == "__TEXT" && sect == "__const" -> SectionKind.RODATA
            seg == "__TEXT" && sect == "__stubs" -> SectionKind.MACHO_STUBS
            seg == "__TEXT" && sect == "__stub_helper" -> SectionKind.MACHO_STUB_HELPER
            seg == "__TEXT" && sect == "__unwind_info" -> SectionKind.UNWIND_INFO
            seg == "__TEXT" && sect == "__eh_frame" -> SectionKind.EH_FRAME
            seg == "__TEXT" && sect == "__gcc_except_tab" -> SectionKind.GCC_EXCEPT_TABLE
            seg == "__DATA" && sect == "__data" -> SectionKind.DATA
            seg == "__DATA" && sect == "__bss" -> SectionKind.BSS
            seg == "__DATA" && sect == "__common" -> SectionKind.COMMON
            seg == "__DATA" && sect == "__la_symbol_ptr" -> SectionKind.MACHO_LA_SYMBOL_PTR
            seg == "__DATA" && sect == "__nl_symbol_ptr" -> SectionKind.MACHO_NL_SYMBOL_PTR
            seg == "__DATA" && sect == "__got" -> SectionKind.GOT
            seg == "__DATA" && sect == "__const" -> SectionKind.RODATA
            seg == "__DATA_CONST" && sect == "__got" -> SectionKind.GOT
            seg == "__DATA" && sect == "__mod_init_func" -> SectionKind.INIT_ARRAY
            seg == "__DATA" && sect == "__mod_term_func" -> SectionKind.FINI_ARRAY
            seg == "__DATA" && sect == "__objc_classlist" -> SectionKind.MACHO_OBJC_CLASSLIST
            seg == "__TEXT" && sect == "__objc_methnames" -> SectionKind.MACHO_OBJC_METHNAMES
            seg == "__LD" && sect == "__compact_unwind" -> SectionKind.MACHO_COMPACT_UNWIND
            seg == "__DWARF" -> when {
                sect == "__debug_info" -> SectionKind.DEBUG_INFO
                sect == "__debug_abbrev" -> SectionKind.DEBUG_ABBREV
                sect == "__debug_line" -> SectionKind.DEBUG_LINE
                sect == "__debug_str" -> SectionKind.DEBUG_STR
                sect == "__debug_ranges" -> SectionKind.DEBUG_RANGES
                sect == "__debug_loc" -> SectionKind.DEBUG_LOC
                sect == "__debug_aranges" -> SectionKind.DEBUG_ARANGES
                else -> SectionKind.UNKNOWN
            }
            s.isPureInstructions -> SectionKind.TEXT
            s.type == MachO.S_ZEROFILL -> SectionKind.BSS
            else -> SectionKind.UNKNOWN
        }
    }

    private fun classifySectionFlags(s: MachOSection): Set<SectionFlag> {
        val flags = mutableSetOf<SectionFlag>()
        if (s.isPureInstructions) flags.add(SectionFlag.PURE_INSTRUCTIONS)
        if (s.attributes and MachO.S_ATTR_SOME_INSTRUCTIONS != 0) flags.add(SectionFlag.SOME_INSTRUCTIONS)
        if (s.segmentName == "__TEXT" || s.isPureInstructions) flags.add(SectionFlag.EXEC)
        if (s.segmentName == "__DATA" && s.sectionName != "__const") flags.add(SectionFlag.WRITE)
        flags.add(SectionFlag.ALLOC)
        return flags
    }

    private fun projectSymbols(macho: MachOFile): List<Symbol> {
        val sections = macho.allSections
        return macho.symbols.map { sym ->
            val binding = when {
                !sym.isExternal -> SymbolBinding.LOCAL
                else -> SymbolBinding.GLOBAL
            }
            val kind = when {
                sym.isUndefined -> SymbolKind.UNDEFINED
                sym.isAbsolute -> SymbolKind.ABSOLUTE
                else -> SymbolKind.FUNCTION // Mach-O doesn't distinguish func/data in nlist easily
            }
            val sectionName = if (sym.isInSection && sym.sectionIndex > 0 && sym.sectionIndex <= sections.size) {
                sections[sym.sectionIndex - 1].sectionName
            } else null

            val flags = mutableSetOf<SymbolFlag>()
            if (sym.isUndefined) flags.add(SymbolFlag.UNDEFINED)
            if (sym.isPrivateExternal) flags.add(SymbolFlag.PRIVATE_EXTERN)

            Symbol(
                name = sym.name, value = sym.value, section = sectionName,
                binding = binding, kind = kind,
                visibility = if (sym.isPrivateExternal) SymbolVisibility.HIDDEN else SymbolVisibility.DEFAULT,
                flags = flags,
            )
        }
    }

    private fun projectRelocations(macho: MachOFile): List<Relocation> {
        val result = mutableListOf<Relocation>()
        val sections = macho.allSections
        for (section in sections) {
            for (reloc in section.relocations) {
                val symbolName = if (reloc.extern && reloc.symbolIndex < macho.symbols.size) {
                    macho.symbols[reloc.symbolIndex].name
                } else ""
                val relocType = resolveMachORelocType(macho.header.cpuType, reloc.type)
                result.add(Relocation(
                    offset = reloc.address.toLong(),
                    symbol = symbolName,
                    type = relocType,
                    addend = 0,
                    section = section.sectionName,
                ))
            }
        }
        return result
    }

    private fun resolveMachORelocType(cpuType: Int, type: Int): RelocationType = when (cpuType) {
        MachO.CPU_TYPE_X86_64 -> RelocationType.MachO_X86_64.entries.firstOrNull { it.value == type }
            ?: RelocationType.Generic("MACHO_X86_64_$type", type)
        MachO.CPU_TYPE_ARM64 -> RelocationType.MachO_ARM64.entries.firstOrNull { it.value == type }
            ?: RelocationType.Generic("MACHO_ARM64_$type", type)
        else -> RelocationType.Generic("MACHO_$type", type)
    }

    private fun resolveArchitecture(macho: MachOFile): Architecture = when (macho.header.cpuType) {
        MachO.CPU_TYPE_X86_64 -> Architecture.X86_64_MACOS
        MachO.CPU_TYPE_ARM64 -> Architecture.AARCH64_MACOS
        else -> Architecture(ArchType.X86_64, vendor = "apple", os = "macos")
    }

    private fun buildMetadata(macho: MachOFile): ObjectMetadata {
        val flags = mutableSetOf<ObjectFlag>()
        when (macho.header.fileType) {
            MachO.MH_OBJECT -> flags.add(ObjectFlag.RELOCATABLE)
            MachO.MH_EXECUTE -> flags.add(ObjectFlag.EXECUTABLE)
            MachO.MH_DYLIB -> flags.add(ObjectFlag.SHARED_LIBRARY)
        }
        if (macho.header.flags and MachO.MH_PIE != 0) flags.add(ObjectFlag.POSITION_INDEPENDENT)

        return ObjectMetadata(
            entryPoint = macho.mainEntryOffset,
            flags = flags,
            osAbi = OsAbi.MACOS,
        )
    }

    private fun projectDynamicInfo(macho: MachOFile): DynamicLinkInfo? {
        if (macho.dylibs.isEmpty()) return null
        return DynamicLinkInfo(
            neededLibraries = macho.dylibs,
        )
    }
}
