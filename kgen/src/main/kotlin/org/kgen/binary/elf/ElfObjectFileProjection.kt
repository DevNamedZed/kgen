package org.kgen.binary.elf

import org.kgen.binary.*

internal object ElfObjectFileProjection {

    fun project(elf: ElfFile): ObjectFile {
        val sections = projectSections(elf)
        val symbols = projectSymbols(elf)
        val relocations = projectRelocations(elf)
        val dynamicInfo = projectDynamicInfo(elf)
        val arch = resolveArchitecture(elf)
        val metadata = buildMetadata(elf)
        val flags = buildObjectFlags(elf)

        return ObjectFile(
            format = ObjectFormat.ELF,
            arch = arch,
            sections = sections,
            symbols = symbols,
            relocations = relocations,
            dynamicInfo = dynamicInfo,
            metadata = metadata.copy(flags = flags),
        )
    }

    private fun projectSections(elf: ElfFile): List<Section> {
        return elf.sections.mapNotNull { entry ->
            if (entry.index == 0) return@mapNotNull null
            if (entry.name.isEmpty() && entry.type == ElfSectionType.NULL) return@mapNotNull null

            val kind = classifySectionKind(entry)
            val flags = classifySectionFlags(entry.flags)

            val linkName = if (entry.link > 0) elf.sectionByIndex(entry.link)?.name else null
            val infoName = if (entry.info > 0 && entry.info < elf.sections.size &&
                (entry.flags and ElfSectionFlags.INFO_LINK != 0L ||
                 entry.type == ElfSectionType.RELA || entry.type == ElfSectionType.REL)
            ) elf.sectionByIndex(entry.info)?.name else null

            Section(
                name = entry.name,
                kind = kind,
                data = entry.data,
                address = entry.address,
                align = entry.alignment.toInt(),
                flags = flags,
                entrySize = entry.entrySize,
                link = linkName,
                info = infoName,
                index = entry.index,
            )
        }
    }

    private fun classifySectionKind(entry: ElfSectionEntry): SectionKind {
        val type = entry.type
        val name = entry.name
        val flags = entry.flags
        return when {
            type == ElfSectionType.SYMTAB -> SectionKind.SYMTAB
            type == ElfSectionType.DYNSYM -> SectionKind.DYNSYM
            type == ElfSectionType.STRTAB && name == ".dynstr" -> SectionKind.DYNSTR
            type == ElfSectionType.STRTAB -> SectionKind.STRTAB
            type == ElfSectionType.RELA -> SectionKind.RELA
            type == ElfSectionType.REL -> SectionKind.REL
            type == ElfSectionType.DYNAMIC -> SectionKind.DYNAMIC
            type == ElfSectionType.NOTE -> SectionKind.NOTE
            type == ElfSectionType.HASH -> SectionKind.HASH
            type == ElfSectionType.GNU_HASH -> SectionKind.GNU_HASH
            type == ElfSectionType.INIT_ARRAY -> SectionKind.INIT_ARRAY
            type == ElfSectionType.FINI_ARRAY -> SectionKind.FINI_ARRAY
            type == ElfSectionType.PREINIT_ARRAY -> SectionKind.PREINIT_ARRAY
            type == ElfSectionType.NOBITS && name == ".tbss" -> SectionKind.TBSS
            type == ElfSectionType.NOBITS -> SectionKind.BSS
            name == ".text" -> SectionKind.TEXT
            name == ".data" -> SectionKind.DATA
            name == ".rodata" || name.startsWith(".rodata.") -> SectionKind.RODATA
            name == ".bss" -> SectionKind.BSS
            name == ".tdata" -> SectionKind.TDATA
            name == ".tbss" -> SectionKind.TBSS
            name == ".got" -> SectionKind.GOT
            name == ".got.plt" -> SectionKind.GOT_PLT
            name == ".plt" -> SectionKind.PLT
            name == ".plt.got" -> SectionKind.PLT_GOT
            name == ".interp" -> SectionKind.INTERP
            name == ".init" -> SectionKind.INIT
            name == ".fini" -> SectionKind.FINI
            name == ".eh_frame" -> SectionKind.EH_FRAME
            name == ".eh_frame_hdr" -> SectionKind.EH_FRAME_HDR
            name == ".gcc_except_table" -> SectionKind.GCC_EXCEPT_TABLE
            name.startsWith(".debug_info") -> SectionKind.DEBUG_INFO
            name.startsWith(".debug_abbrev") -> SectionKind.DEBUG_ABBREV
            name.startsWith(".debug_line") && name != ".debug_line_str" -> SectionKind.DEBUG_LINE
            name == ".debug_str" -> SectionKind.DEBUG_STR
            name == ".debug_line_str" -> SectionKind.DEBUG_LINE_STR
            name == ".debug_ranges" -> SectionKind.DEBUG_RANGES
            name == ".debug_loc" -> SectionKind.DEBUG_LOC
            name == ".debug_frame" -> SectionKind.DEBUG_FRAME
            name == ".debug_aranges" -> SectionKind.DEBUG_ARANGES
            name == ".debug_rnglists" -> SectionKind.DEBUG_RNGLISTS
            name == ".debug_loclists" -> SectionKind.DEBUG_LOCLISTS
            name == ".debug_addr" -> SectionKind.DEBUG_ADDR
            name == ".debug_str_offsets" -> SectionKind.DEBUG_STR_OFFSETS
            name == ".note.gnu.build-id" -> SectionKind.GNU_BUILD_ID
            name == ".note.gnu.property" -> SectionKind.GNU_PROPERTY
            name.startsWith(".note") -> SectionKind.NOTE
            name == ".gnu.version" -> SectionKind.VERSION
            name == ".gnu.version_r" -> SectionKind.VERSION_NEEDED
            name == ".gnu.version_d" -> SectionKind.VERSION_DEF
            name == ".ARM.exidx" -> SectionKind.ARM_EXIDX
            name == ".ARM.extab" -> SectionKind.ARM_EXTAB
            name == ".ARM.attributes" -> SectionKind.ARM_ATTRIBUTES
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
        if (flags and ElfSectionFlags.INFO_LINK != 0L) result.add(SectionFlag.INFO_LINK)
        return result
    }

    private fun projectSymbols(elf: ElfFile): List<Symbol> {
        return (elf.symbols + elf.dynamicSymbols).map { sym ->
            val binding = when (sym.binding) {
                ElfSymbolBinding.LOCAL -> SymbolBinding.LOCAL
                ElfSymbolBinding.GLOBAL -> SymbolBinding.GLOBAL
                ElfSymbolBinding.WEAK -> SymbolBinding.WEAK
                else -> SymbolBinding.GLOBAL
            }
            val kind = when {
                sym.isUndefined -> SymbolKind.UNDEFINED
                sym.isAbsolute -> SymbolKind.ABSOLUTE
                sym.type == ElfSymbolType.FUNC -> SymbolKind.FUNCTION
                sym.type == ElfSymbolType.OBJECT -> SymbolKind.DATA
                sym.type == ElfSymbolType.SECTION -> SymbolKind.SECTION
                sym.type == ElfSymbolType.FILE -> SymbolKind.FILE
                sym.type == ElfSymbolType.COMMON -> SymbolKind.COMMON
                sym.type == ElfSymbolType.TLS -> SymbolKind.TLS
                sym.type == ElfSymbolType.GNU_IFUNC -> SymbolKind.IFUNC
                else -> SymbolKind.DATA
            }
            val visibility = when (sym.visibility) {
                ElfSymbolVisibility.DEFAULT -> SymbolVisibility.DEFAULT
                ElfSymbolVisibility.HIDDEN -> SymbolVisibility.HIDDEN
                ElfSymbolVisibility.PROTECTED -> SymbolVisibility.PROTECTED
                else -> SymbolVisibility.DEFAULT
            }
            val symFlags = mutableSetOf<SymbolFlag>()
            if (sym.isUndefined) symFlags.add(SymbolFlag.UNDEFINED)
            if (sym in elf.dynamicSymbols && !sym.isUndefined) symFlags.add(SymbolFlag.EXPORTED)

            Symbol(
                name = sym.name, value = sym.value, size = sym.size,
                section = sym.sectionName, binding = binding, kind = kind,
                visibility = visibility, flags = symFlags,
            )
        }
    }

    private fun projectRelocations(elf: ElfFile): List<Relocation> {
        return elf.relocations.map { rel ->
            Relocation(
                offset = rel.offset,
                symbol = rel.symbolName,
                type = resolveRelocationType(elf, rel.type),
                addend = rel.addend,
                section = rel.sectionName,
            )
        }
    }

    private fun resolveRelocationType(elf: ElfFile, typeValue: Int): RelocationType {
        return when (elf.header.machine) {
            ElfMachine.X86_64 -> RelocationType.X86_64.entries.firstOrNull { it.value == typeValue }
                ?: RelocationType.Generic("R_X86_64_$typeValue", typeValue)
            ElfMachine.AARCH64 -> RelocationType.AArch64.entries.firstOrNull { it.value == typeValue }
                ?: RelocationType.Generic("R_AARCH64_$typeValue", typeValue)
            ElfMachine.RISCV -> RelocationType.RiscV.entries.firstOrNull { it.value == typeValue }
                ?: RelocationType.Generic("R_RISCV_$typeValue", typeValue)
            else -> RelocationType.Generic("R_UNKNOWN_$typeValue", typeValue)
        }
    }

    private fun projectDynamicInfo(elf: ElfFile): DynamicLinkInfo? {
        val info = elf.dynamicInfo ?: return null
        val flags = mutableSetOf<DynamicFlag>()

        for (entry in info.entries) {
            when (entry.tag) {
                ElfDynamicTag.BIND_NOW -> flags.add(DynamicFlag.BIND_NOW)
                ElfDynamicTag.FLAGS -> {
                    if (entry.value and ElfDynFlags.SYMBOLIC != 0L) flags.add(DynamicFlag.SYMBOLIC)
                    if (entry.value and ElfDynFlags.TEXTREL != 0L) flags.add(DynamicFlag.TEXTREL)
                    if (entry.value and ElfDynFlags.BIND_NOW != 0L) flags.add(DynamicFlag.BIND_NOW)
                    if (entry.value and ElfDynFlags.STATIC_TLS != 0L) flags.add(DynamicFlag.STATIC_TLS)
                }
                ElfDynamicTag.FLAGS_1 -> {
                    if (entry.value and ElfDynFlags1.NOW != 0L) flags.add(DynamicFlag.NOW)
                    if (entry.value and ElfDynFlags1.NODELETE != 0L) flags.add(DynamicFlag.NODELETE)
                    if (entry.value and ElfDynFlags1.PIE != 0L) flags.add(DynamicFlag.PIE)
                }
                else -> {}
            }
        }

        return DynamicLinkInfo(
            neededLibraries = info.neededLibraries,
            soName = info.soName,
            rpath = info.rpath + info.runpath,
            flags = flags,
        )
    }

    private fun resolveArchitecture(elf: ElfFile): Architecture {
        val is32 = elf.header.elfClass == ElfClass.ELF32
        return when (elf.header.machine) {
            ElfMachine.I386 -> Architecture(ArchType.X86)
            ElfMachine.ARM -> Architecture(ArchType.ARM)
            ElfMachine.X86_64 -> Architecture(ArchType.X86_64)
            ElfMachine.AARCH64 -> Architecture(ArchType.AARCH64)
            ElfMachine.RISCV -> Architecture(if (is32) ArchType.RISCV32 else ArchType.RISCV64)
            else -> Architecture(if (is32) ArchType.X86 else ArchType.X86_64)
        }
    }

    private fun buildMetadata(elf: ElfFile): ObjectMetadata {
        val entry = if (elf.header.type != ElfObjectType.REL && elf.header.entryPoint != 0L) {
            elf.header.entryPoint
        } else null
        val osAbi = when (elf.header.osAbi) {
            0 -> OsAbi.NONE
            3 -> OsAbi.LINUX
            6 -> OsAbi.SOLARIS
            9 -> OsAbi.FREEBSD
            else -> OsAbi.NONE
        }
        return ObjectMetadata(entryPoint = entry, osAbi = osAbi)
    }

    private fun buildObjectFlags(elf: ElfFile): Set<ObjectFlag> {
        val flags = mutableSetOf<ObjectFlag>()
        when (elf.header.type) {
            ElfObjectType.REL -> flags.add(ObjectFlag.RELOCATABLE)
            ElfObjectType.EXEC -> flags.add(ObjectFlag.EXECUTABLE)
            ElfObjectType.DYN -> flags.add(ObjectFlag.SHARED_LIBRARY)
            else -> {}
        }
        return flags
    }
}
