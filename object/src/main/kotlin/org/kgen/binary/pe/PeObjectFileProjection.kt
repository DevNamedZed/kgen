package org.kgen.binary.pe

import org.kgen.binary.*

/**
 * Projects a [PeFile] into the universal [ObjectFile] model.
 */
internal object PeObjectFileProjection {

    fun project(pe: PeFile): ObjectFile {
        val format = when {
            pe.clrMetadata?.isILOnly == true -> ObjectFormat.MSIL_ASSEMBLY
            pe.clrMetadata != null -> ObjectFormat.MSIL_MIXED
            else -> ObjectFormat.PE_COFF
        }

        return ObjectFile(
            format = format,
            arch = resolveArchitecture(pe),
            sections = projectSections(pe),
            symbols = projectSymbols(pe),
            relocations = emptyList(),
            imports = projectImports(pe),
            exports = projectExports(pe),
            dynamicInfo = projectDynamicInfo(pe),
            metadata = projectMetadata(pe),
        )
    }

    private fun resolveArchitecture(pe: PeFile): Architecture = when (pe.coffHeader.machine) {
        PeConstants.MACHINE_AMD64 -> Architecture.X86_64_WINDOWS
        PeConstants.MACHINE_ARM64 -> Architecture(ArchType.AARCH64, os = "windows")
        PeConstants.MACHINE_I386 -> Architecture(ArchType.X86, os = "windows")
        PeConstants.MACHINE_ARM -> Architecture(ArchType.ARM, os = "windows")
        else -> Architecture(ArchType.X86_64, os = "windows")
    }

    private fun projectSections(pe: PeFile): List<Section> = pe.sections.mapIndexed { i, s ->
        Section(
            name = s.name,
            kind = classifySectionKind(s),
            data = s.data,
            address = if (pe.isPe) pe.imageBase + s.virtualAddress else s.virtualAddress.toLong(),
            align = inferAlignment(s.characteristics),
            flags = classifySectionFlags(s.characteristics),
            index = i,
        )
    }

    private fun classifySectionKind(s: PeSection): SectionKind = when {
        s.name == ".text" -> SectionKind.TEXT
        s.name == ".data" -> SectionKind.DATA
        s.name == ".rdata" || s.name == ".rodata" -> SectionKind.RODATA
        s.name == ".bss" -> SectionKind.BSS
        s.name == ".idata" -> SectionKind.IDATA
        s.name == ".edata" -> SectionKind.EDATA
        s.name == ".rsrc" -> SectionKind.RSRC
        s.name == ".reloc" -> SectionKind.RELOC
        s.name == ".pdata" -> SectionKind.PDATA
        s.name == ".xdata" -> SectionKind.XDATA
        s.name == ".tls" -> SectionKind.TLS
        s.isCode -> SectionKind.TEXT
        s.isUninitializedData -> SectionKind.BSS
        s.isInitializedData && !s.isWritable -> SectionKind.RODATA
        s.isInitializedData -> SectionKind.DATA
        else -> SectionKind.UNKNOWN
    }

    private fun classifySectionFlags(ch: Int): Set<SectionFlag> {
        val flags = mutableSetOf(SectionFlag.ALLOC)
        if (ch and PeConstants.IMAGE_SCN_MEM_EXECUTE != 0) flags.add(SectionFlag.EXEC)
        if (ch and PeConstants.IMAGE_SCN_MEM_READ != 0) flags.add(SectionFlag.READ)
        if (ch and PeConstants.IMAGE_SCN_MEM_WRITE != 0) flags.add(SectionFlag.WRITE)
        if (ch and PeConstants.IMAGE_SCN_MEM_SHARED != 0) flags.add(SectionFlag.SHARED)
        if (ch and PeConstants.IMAGE_SCN_MEM_DISCARDABLE != 0) flags.add(SectionFlag.DISCARDABLE)
        if (ch and PeConstants.IMAGE_SCN_CNT_INITIALIZED_DATA != 0) flags.add(SectionFlag.INITIALIZED_DATA)
        if (ch and PeConstants.IMAGE_SCN_CNT_UNINITIALIZED_DATA != 0) flags.add(SectionFlag.UNINITIALIZED_DATA)
        if (ch and PeConstants.IMAGE_SCN_LNK_COMDAT != 0) flags.add(SectionFlag.COMDAT)
        return flags
    }

    private fun inferAlignment(ch: Int): Int {
        val alignBits = (ch shr 20) and 0xF
        return if (alignBits in 1..14) 1 shl (alignBits - 1) else 1
    }

    private fun projectSymbols(pe: PeFile): List<Symbol> = pe.symbols.mapNotNull { sym ->
        if (sym.name.isEmpty()) return@mapNotNull null
        val sectionName = if (sym.sectionNumber > 0 && sym.sectionNumber <= pe.sections.size) {
            pe.sections[sym.sectionNumber - 1].name
        } else null
        Symbol(
            name = sym.name,
            value = sym.value,
            section = sectionName,
            binding = when {
                sym.isExternal -> SymbolBinding.GLOBAL
                sym.storageClass == PeConstants.IMAGE_SYM_CLASS_WEAK_EXTERNAL -> SymbolBinding.WEAK
                else -> SymbolBinding.LOCAL
            },
            kind = when {
                sym.isUndefined -> SymbolKind.UNDEFINED
                sym.isAbsolute -> SymbolKind.ABSOLUTE
                sym.isFunction -> SymbolKind.FUNCTION
                sym.storageClass == PeConstants.IMAGE_SYM_CLASS_SECTION -> SymbolKind.SECTION
                else -> SymbolKind.DATA
            },
            flags = buildSet {
                if (sym.isUndefined && sym.isExternal) add(SymbolFlag.UNDEFINED)
            },
        )
    }

    private fun projectImports(pe: PeFile): List<ImportEntry> =
        pe.importDirectories.flatMap { dir ->
            dir.entries.map { entry ->
                ImportEntry(
                    symbolName = entry.name ?: "ordinal#${entry.ordinal}",
                    moduleName = dir.name,
                    ordinal = entry.ordinal,
                )
            }
        }

    private fun projectExports(pe: PeFile): List<ExportEntry> =
        pe.exportDirectory?.entries?.map { entry ->
            ExportEntry(
                symbolName = entry.name ?: "ordinal#${entry.ordinal}",
                exportName = entry.name,
                ordinal = entry.ordinal,
                isForwarder = entry.isForwarder,
                forwarderName = entry.forwarderName,
            )
        } ?: emptyList()

    private fun projectDynamicInfo(pe: PeFile): DynamicLinkInfo? {
        if (!pe.isPe) return null
        val opt = pe.optionalHeader ?: return null
        val sub = PESubsystem.entries.firstOrNull { it.value == opt.subsystem }
        val dllChars = buildDllCharacteristics(opt.dllCharacteristics)

        return DynamicLinkInfo(
            imageBase = opt.imageBase,
            sectionAlignment = opt.sectionAlignment.toLong(),
            fileAlignment = opt.fileAlignment.toLong(),
            subsystem = sub,
            dllCharacteristics = dllChars,
            importDirectory = pe.importDirectories.map { dir ->
                ImportDirectory(
                    dllName = dir.name,
                    entries = dir.entries.map { ImportDirectoryEntry(it.name, it.ordinal, it.hint) },
                )
            },
            exportDirectory = pe.exportDirectory?.let { exp ->
                org.kgen.binary.ExportDirectory(
                    name = exp.name,
                    ordinalBase = exp.ordinalBase,
                    entries = exp.entries.map { ExportDirectoryEntry(it.name, it.ordinal, it.rva.toLong(), it.forwarderName) },
                )
            },
        )
    }

    private fun buildDllCharacteristics(flags: Int): Set<DLLCharacteristic> = buildSet {
        if (flags and 0x0020 != 0) add(DLLCharacteristic.HIGH_ENTROPY_VA)
        if (flags and 0x0040 != 0) add(DLLCharacteristic.DYNAMIC_BASE)
        if (flags and 0x0080 != 0) add(DLLCharacteristic.FORCE_INTEGRITY)
        if (flags and 0x0100 != 0) add(DLLCharacteristic.NX_COMPAT)
        if (flags and 0x0200 != 0) add(DLLCharacteristic.NO_ISOLATION)
        if (flags and 0x0400 != 0) add(DLLCharacteristic.NO_SEH)
        if (flags and 0x0800 != 0) add(DLLCharacteristic.NO_BIND)
        if (flags and 0x1000 != 0) add(DLLCharacteristic.APPCONTAINER)
        if (flags and 0x2000 != 0) add(DLLCharacteristic.WDM_DRIVER)
        if (flags and 0x4000 != 0) add(DLLCharacteristic.GUARD_CF)
        if (flags and 0x8000.toInt() != 0) add(DLLCharacteristic.TERMINAL_SERVER_AWARE)
    }

    private fun projectMetadata(pe: PeFile): ObjectMetadata {
        val flags = buildSet {
            if (!pe.isPe) {
                add(ObjectFlag.RELOCATABLE)
            } else {
                if (pe.isExecutable) add(ObjectFlag.EXECUTABLE)
                if (pe.isDll) add(ObjectFlag.SHARED_LIBRARY)
                if (pe.coffHeader.characteristics and 0x0020 != 0) add(ObjectFlag.LARGE_ADDRESS_AWARE)
            }
        }
        val entry = if (pe.isPe && pe.entryPointRVA != 0) (pe.imageBase + pe.entryPointRVA) else null
        return ObjectMetadata(entryPoint = entry, flags = flags, osAbi = OsAbi.WINDOWS)
    }
}
