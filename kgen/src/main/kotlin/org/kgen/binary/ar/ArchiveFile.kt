package org.kgen.binary.ar

/**
 * Represents a Unix archive (.a) or Windows static library (.lib).
 *
 * Archive format:
 *   "!<arch>\n" magic (8 bytes)
 *   Repeated: [member header (60 bytes)][member data][optional padding]
 *
 * Special members:
 *   "/" or "__.SYMDEF" — symbol table (maps symbol names → member offsets)
 *   "//" — GNU extended name table (long filenames)
 *   "#1/N" — BSD extended name (name embedded in data)
 */
data class ArchiveFile(
    val members: List<ArchiveMember>,
    val symbols: List<ArchiveSymbol> = emptyList(),
    val variant: ArchiveVariant = ArchiveVariant.GNU,
) {
    fun memberByName(name: String): ArchiveMember? =
        members.firstOrNull { it.name == name }

    fun membersContainingSymbol(symbolName: String): List<ArchiveMember> {
        val offsets = symbols.filter { it.name == symbolName }.map { it.memberOffset }
        return members.filter { it.fileOffset in offsets }
    }
}

data class ArchiveMember(
    val name: String,
    val modificationTime: Long = 0,
    val ownerId: Int = 0,
    val groupId: Int = 0,
    val mode: Int = 0x1A4, // 0644
    val data: ByteArray,
    val fileOffset: Long = 0,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ArchiveMember) return false
        return name == other.name && data.contentEquals(other.data)
    }
    override fun hashCode(): Int = name.hashCode() * 31 + data.contentHashCode()
}

data class ArchiveSymbol(
    val name: String,
    val memberOffset: Long,
)

enum class ArchiveVariant {
    GNU,    // Linux/GNU ar: // for long names, / for symtab (big-endian offsets)
    BSD,    // macOS/BSD ar: #1/N for long names, __.SYMDEF for symtab
    COFF,   // Windows lib.exe: / for first linker member (big-endian), / for second (little-endian)
}
