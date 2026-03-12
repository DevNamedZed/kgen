package org.kgen.binary.macho

data class MachOFile(
    val header: MachOHeader,
    val segments: List<MachOSegment>,
    val symbols: List<MachOSymbol>,
    val dylibs: List<String>,
    val uuid: ByteArray?,
    val mainEntryOffset: Long?,
    val sourceVersion: Long?,
    val chainedFixups: ChainedFixups? = null,
) {
    val isObject: Boolean get() = header.fileType == MachO.MH_OBJECT
    val isExecutable: Boolean get() = header.fileType == MachO.MH_EXECUTE
    val isDylib: Boolean get() = header.fileType == MachO.MH_DYLIB

    val allSections: List<MachOSection> get() = segments.flatMap { it.sections }

    fun sectionByName(segName: String, sectName: String): MachOSection? =
        allSections.firstOrNull { it.segmentName == segName && it.sectionName == sectName }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MachOFile) return false
        return header == other.header && segments == other.segments && symbols == other.symbols
    }
    override fun hashCode(): Int = header.hashCode() * 31 + segments.hashCode()
}

data class MachOHeader(
    val magic: UInt,
    val cpuType: Int,
    val cpuSubtype: Int,
    val fileType: Int,
    val numberOfCommands: Int,
    val sizeOfCommands: Int,
    val flags: Int,
) {
    val is64Bit: Boolean get() = magic == MachO.MH_MAGIC_64 || magic == MachO.MH_CIGAM_64
    val isBigEndian: Boolean get() = magic == MachO.MH_CIGAM_64 || magic == MachO.MH_CIGAM_32
}

data class MachOSegment(
    val name: String,
    val vmAddress: Long,
    val vmSize: Long,
    val fileOffset: Long,
    val fileSize: Long,
    val maxProtection: Int,
    val initProtection: Int,
    val flags: Int,
    val sections: List<MachOSection>,
)

data class MachOSection(
    val sectionName: String,
    val segmentName: String,
    val address: Long,
    val size: Long,
    val offset: Int,
    val align: Int,
    val relocationOffset: Int,
    val numberOfRelocations: Int,
    val flags: Int,
    val data: ByteArray,
    val relocations: List<MachORelocation>,
) {
    val type: Int get() = flags and 0xFF
    val attributes: Int get() = flags and 0xFFFFFF00.toInt()
    val isPureInstructions: Boolean get() = attributes and MachO.S_ATTR_PURE_INSTRUCTIONS != 0

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MachOSection) return false
        return sectionName == other.sectionName && segmentName == other.segmentName &&
            address == other.address && data.contentEquals(other.data)
    }
    override fun hashCode(): Int = sectionName.hashCode() * 31 + segmentName.hashCode()
}

data class MachOSymbol(
    val name: String,
    val type: Int,
    val sectionIndex: Int,
    val description: Int,
    val value: Long,
) {
    val isExternal: Boolean get() = type and MachO.N_EXT != 0
    val isPrivateExternal: Boolean get() = type and MachO.N_PEXT != 0
    val isUndefined: Boolean get() = (type and 0x0E) == MachO.N_UNDF
    val isAbsolute: Boolean get() = (type and 0x0E) == MachO.N_ABS
    val isInSection: Boolean get() = (type and 0x0E) == MachO.N_SECT
}

data class MachORelocation(
    val address: Int,
    val symbolIndex: Int,
    val pcRelative: Boolean,
    val length: Int,
    val extern: Boolean,
    val type: Int,
)
