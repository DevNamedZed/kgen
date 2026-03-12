package org.kgen.binary.macho

object MachO {
    const val MH_MAGIC_64 = 0xFEEDFACFu
    const val MH_CIGAM_64 = 0xCFFAEDFEu
    const val MH_MAGIC_32 = 0xFEEDFACEu
    const val MH_CIGAM_32 = 0xCEFAEDFEu

    // File types
    const val MH_OBJECT = 1
    const val MH_EXECUTE = 2
    const val MH_DYLIB = 6
    const val MH_DYLINKER = 7
    const val MH_BUNDLE = 8
    const val MH_DSYM = 10
    const val MH_KEXT_BUNDLE = 11

    // Flags
    const val MH_PIE = 0x00200000
    const val MH_TWOLEVEL = 0x00000080
    const val MH_DYLDLINK = 0x00000004
    const val MH_NOUNDEFS = 0x00000001

    // CPU types
    const val CPU_TYPE_X86_64 = 0x01000007
    const val CPU_TYPE_ARM64 = 0x0100000C
    const val CPU_TYPE_ARM = 12

    // CPU subtypes
    const val CPU_SUBTYPE_ALL = 0x00000003
    const val CPU_SUBTYPE_ARM64_ALL = 0x00000000
    const val CPU_SUBTYPE_ARM64E = 0x00000002

    // Load command types
    const val LC_SEGMENT = 0x01
    const val LC_SEGMENT_64 = 0x19
    const val LC_SYMTAB = 0x02
    const val LC_DYSYMTAB = 0x0B
    const val LC_LOAD_DYLIB = 0x0C
    const val LC_ID_DYLIB = 0x0D
    const val LC_LOAD_DYLINKER = 0x0E
    const val LC_UUID = 0x1B
    const val LC_RPATH = 0x8000001C.toInt()
    const val LC_CODE_SIGNATURE = 0x1D
    const val LC_SEGMENT_SPLIT_INFO = 0x1E
    const val LC_REEXPORT_DYLIB = 0x8000001F.toInt()
    const val LC_LAZY_LOAD_DYLIB = 0x20
    const val LC_DYLD_INFO = 0x22
    const val LC_DYLD_INFO_ONLY = 0x80000022.toInt()
    const val LC_FUNCTION_STARTS = 0x26
    const val LC_MAIN = 0x80000028.toInt()
    const val LC_DATA_IN_CODE = 0x29
    const val LC_SOURCE_VERSION = 0x2A
    const val LC_DYLD_EXPORTS_TRIE = 0x80000033.toInt()
    const val LC_DYLD_CHAINED_FIXUPS = 0x80000034.toInt()
    const val LC_BUILD_VERSION = 0x32

    // Section types
    const val S_REGULAR = 0x00
    const val S_ZEROFILL = 0x01
    const val S_CSTRING_LITERALS = 0x02
    const val S_4BYTE_LITERALS = 0x03
    const val S_8BYTE_LITERALS = 0x04
    const val S_LITERAL_POINTERS = 0x05
    const val S_NON_LAZY_SYMBOL_POINTERS = 0x06
    const val S_LAZY_SYMBOL_POINTERS = 0x07
    const val S_SYMBOL_STUBS = 0x08
    const val S_MOD_INIT_FUNC_POINTERS = 0x09
    const val S_MOD_TERM_FUNC_POINTERS = 0x0A

    // Section attributes
    const val S_ATTR_PURE_INSTRUCTIONS = 0x80000000.toInt()
    const val S_ATTR_SOME_INSTRUCTIONS = 0x00000400

    // N-type values (symbol types)
    const val N_UNDF = 0x00
    const val N_ABS = 0x02
    const val N_SECT = 0x0E
    const val N_PBUD = 0x0C
    const val N_INDR = 0x0A
    const val N_EXT = 0x01
    const val N_PEXT = 0x10

    const val REFERENCE_FLAG_UNDEFINED_NON_LAZY = 0
    const val REFERENCE_FLAG_UNDEFINED_LAZY = 1

    fun isMachO(bytes: ByteArray): Boolean {
        if (bytes.size < 4) return false
        val magic = ((bytes[0].toInt() and 0xFF).toLong()) or
            ((bytes[1].toInt() and 0xFF).toLong() shl 8) or
            ((bytes[2].toInt() and 0xFF).toLong() shl 16) or
            ((bytes[3].toInt() and 0xFF).toLong() shl 24)
        return magic == MH_MAGIC_64.toLong() || magic == MH_CIGAM_64.toLong() ||
               magic == MH_MAGIC_32.toLong() || magic == MH_CIGAM_32.toLong()
    }

    fun isMachOOrFat(bytes: ByteArray): Boolean =
        isMachO(bytes) || MachOFatBinary.isFat(bytes)
}
