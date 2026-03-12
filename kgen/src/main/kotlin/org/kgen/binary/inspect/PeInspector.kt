package org.kgen.binary.inspect

import org.kgen.binary.*
import org.kgen.binary.pe.*

class PeInspector : BinaryInspector {

    override fun inspect(bytes: ByteArray): ObjectFile = PeReader.toObjectFile(PeReader.read(bytes))

    override fun headers(bytes: ByteArray): HeaderInfo {
        val pe = PeReader.read(bytes)
        val arch = when (pe.coffHeader.machine) {
            PeConstants.MACHINE_AMD64 -> Architecture(ArchType.X86_64, os = "windows")
            PeConstants.MACHINE_ARM64 -> Architecture(ArchType.AARCH64, os = "windows")
            PeConstants.MACHINE_I386 -> Architecture(ArchType.X86, os = "windows")
            else -> Architecture(ArchType.X86_64, os = "windows")
        }
        val type = when {
            pe.isDll -> "dynamic library"
            pe.isExecutable -> "executable"
            !pe.isPe -> "object"
            else -> "unknown"
        }
        val flags = mutableSetOf<String>()
        if (pe.isPe32Plus) flags.add("PE32+")
        if (pe.isPe && !pe.isPe32Plus) flags.add("PE32")
        if (pe.isManagedAssembly) flags.add("managed")
        if (pe.coffHeader.characteristics and PeConstants.IMAGE_FILE_LARGE_ADDRESS_AWARE != 0) {
            flags.add("large-address-aware")
        }

        val props = mutableMapOf<String, String>()
        props["machine"] = "0x${pe.coffHeader.machine.toString(16)}"
        props["sections"] = pe.coffHeader.numberOfSections.toString()
        pe.optionalHeader?.let { opt ->
            props["imageBase"] = "0x${opt.imageBase.toString(16)}"
            props["sectionAlignment"] = opt.sectionAlignment.toString()
            props["fileAlignment"] = opt.fileAlignment.toString()
            props["subsystem"] = opt.subsystem.toString()
        }

        val entryPoint = pe.optionalHeader?.let {
            if (it.entryPointRVA != 0) it.imageBase + it.entryPointRVA else null
        }

        return HeaderInfo(
            format = ObjectFormat.PE_COFF,
            arch = arch,
            type = type,
            entryPoint = entryPoint,
            flags = flags,
            properties = props,
        )
    }

    override fun sections(bytes: ByteArray): List<SectionInfo> {
        val pe = PeReader.read(bytes)
        return pe.sections.mapIndexed { i, s ->
            SectionInfo(
                index = i,
                name = s.name,
                kind = classifySectionKind(s),
                address = s.virtualAddress.toLong(),
                size = s.virtualSize.toLong(),
                offset = s.rawDataOffset.toLong(),
                align = 0,
                flags = classifySectionFlags(s.characteristics),
                entrySize = 0,
            )
        }
    }

    override fun sectionData(bytes: ByteArray, sectionName: String): ByteArray? {
        val pe = PeReader.read(bytes)
        return pe.sectionByName(sectionName)?.data
    }

    override fun symbols(bytes: ByteArray): List<SymbolInfo> {
        val pe = PeReader.read(bytes)
        return pe.symbols.map { it.toSymbolInfo(pe) }
    }

    override fun dynamicSymbols(bytes: ByteArray): List<SymbolInfo> = emptyList()

    override fun exports(bytes: ByteArray): List<ExportInfo> {
        val pe = PeReader.read(bytes)
        val dir = pe.exportDirectory ?: return emptyList()
        return dir.entries.map { e ->
            ExportInfo(
                name = e.name ?: "",
                address = e.rva.toLong(),
                ordinal = e.ordinal,
            )
        }
    }

    override fun imports(bytes: ByteArray): List<ImportInfo> {
        val pe = PeReader.read(bytes)
        val result = mutableListOf<ImportInfo>()
        for (dir in pe.importDirectories) {
            for (entry in dir.entries) {
                result.add(ImportInfo(
                    name = entry.name ?: "ordinal#${entry.ordinal}",
                    module = dir.name,
                    ordinal = entry.ordinal,
                    isDelayLoad = false,
                ))
            }
        }
        for (dir in pe.delayImportDirectories) {
            for (entry in dir.entries) {
                result.add(ImportInfo(
                    name = entry.name ?: "ordinal#${entry.ordinal}",
                    module = dir.name,
                    ordinal = entry.ordinal,
                    isDelayLoad = true,
                ))
            }
        }
        return result
    }

    override fun relocations(bytes: ByteArray): List<RelocationInfo> {
        val pe = PeReader.read(bytes)
        val result = mutableListOf<RelocationInfo>()
        for (block in pe.baseRelocations) {
            for (entry in block.entries) {
                if (entry.type == PeBaseRelocationEntry.IMAGE_REL_BASED_ABSOLUTE) continue
                val typeName = when (entry.type) {
                    PeBaseRelocationEntry.IMAGE_REL_BASED_HIGH -> "HIGH"
                    PeBaseRelocationEntry.IMAGE_REL_BASED_LOW -> "LOW"
                    PeBaseRelocationEntry.IMAGE_REL_BASED_HIGHLOW -> "HIGHLOW"
                    PeBaseRelocationEntry.IMAGE_REL_BASED_DIR64 -> "DIR64"
                    else -> "TYPE_${entry.type}"
                }
                result.add(RelocationInfo(
                    offset = (block.pageRVA + entry.offset).toLong(),
                    symbol = "",
                    type = typeName,
                    addend = 0,
                    section = ".reloc",
                ))
            }
        }
        return result
    }

    override fun debugInfo(bytes: ByteArray): DebugInfo? = null

    override fun lineInfo(bytes: ByteArray): List<LineMapping> = emptyList()

    override fun dependencies(bytes: ByteArray): List<String> {
        val pe = PeReader.read(bytes)
        return pe.importDirectories.map { it.name }
    }

    override fun dynamicEntries(bytes: ByteArray): List<DynamicEntry> = emptyList()

    override fun strings(bytes: ByteArray, minLength: Int): List<FoundString> {
        val pe = PeReader.read(bytes)
        val result = mutableListOf<FoundString>()
        for (section in pe.sections) {
            if (section.data.isEmpty()) continue
            extractStrings(section.data, minLength).forEach { (offset, str) ->
                result.add(FoundString(
                    offset = section.rawDataOffset.toLong() + offset,
                    section = section.name,
                    value = str,
                ))
            }
        }
        return result
    }

    private fun CoffSymbol.toSymbolInfo(pe: PeFile): SymbolInfo {
        val binding = when {
            isExternal -> SymbolBinding.GLOBAL
            isStatic -> SymbolBinding.LOCAL
            storageClass == PeConstants.IMAGE_SYM_CLASS_WEAK_EXTERNAL -> SymbolBinding.WEAK
            else -> SymbolBinding.GLOBAL
        }
        val kind = when {
            isUndefined -> SymbolKind.UNDEFINED
            isAbsolute -> SymbolKind.ABSOLUTE
            isFunction -> SymbolKind.FUNCTION
            storageClass == PeConstants.IMAGE_SYM_CLASS_FILE -> SymbolKind.FILE
            storageClass == PeConstants.IMAGE_SYM_CLASS_SECTION -> SymbolKind.SECTION
            else -> SymbolKind.DATA
        }
        val sectionName = if (sectionNumber > 0 && sectionNumber <= pe.sections.size) {
            pe.sections[sectionNumber - 1].name
        } else null

        return SymbolInfo(
            name = name, value = value, size = 0,
            binding = binding, kind = kind, visibility = SymbolVisibility.DEFAULT,
            section = sectionName, version = null, demangled = null,
        )
    }

    private fun classifySectionKind(s: PeSection): SectionKind = when {
        s.name == ".text" -> SectionKind.TEXT
        s.name == ".data" -> SectionKind.DATA
        s.name == ".rdata" -> SectionKind.RODATA
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

    private fun classifySectionFlags(chars: Int): Set<SectionFlag> {
        val result = mutableSetOf<SectionFlag>()
        if (chars and PeConstants.IMAGE_SCN_MEM_READ != 0) result.add(SectionFlag.READ)
        if (chars and PeConstants.IMAGE_SCN_MEM_WRITE != 0) result.add(SectionFlag.WRITE)
        if (chars and PeConstants.IMAGE_SCN_MEM_EXECUTE != 0) result.add(SectionFlag.EXEC)
        if (chars and PeConstants.IMAGE_SCN_MEM_DISCARDABLE != 0) result.add(SectionFlag.DISCARDABLE)
        if (chars and PeConstants.IMAGE_SCN_CNT_CODE != 0) result.add(SectionFlag.EXEC)
        if (chars and PeConstants.IMAGE_SCN_CNT_INITIALIZED_DATA != 0) result.add(SectionFlag.INITIALIZED_DATA)
        if (chars and PeConstants.IMAGE_SCN_CNT_UNINITIALIZED_DATA != 0) result.add(SectionFlag.UNINITIALIZED_DATA)
        if (chars and PeConstants.IMAGE_SCN_LNK_COMDAT != 0) result.add(SectionFlag.COMDAT)
        return result
    }
}
