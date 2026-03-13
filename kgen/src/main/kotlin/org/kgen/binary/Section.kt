package org.kgen.binary

/**
 * A section in an object file — a contiguous block of bytes with a name, type, and flags.
 *
 * Sections hold code (.text), data (.data/.rodata), debug info, relocation tables,
 * and format-specific metadata. Use [kind] to classify sections regardless of format.
 *
 * ```kotlin
 * val obj = X86CodeGenerator().generateObjectFile(module)
 * val text = obj.sections.first { it.kind == SectionKind.TEXT }
 * println("Code size: ${text.data.size} bytes")
 * ```
 *
 * @property name Section name (e.g., ".text", ".data", "__TEXT,__text").
 * @property kind Format-independent section classification.
 * @property data Raw section bytes.
 * @property address Virtual address when loaded into memory.
 * @property align Required alignment in bytes.
 * @property flags Section attributes (writable, executable, etc.).
 */
data class Section(
    val name: String,
    val kind: SectionKind,
    val data: ByteArray,
    val address: Long = 0,
    val align: Int = 1,
    val flags: Set<SectionFlag> = emptySet(),
    val entrySize: Long = 0,               // for fixed-size entry sections (symbol tables, etc.)
    val link: String? = null,              // linked section name (e.g., .strtab for .symtab)
    val info: String? = null,              // info section name
    val comdat: String? = null,            // COMDAT group name
    val relocations: List<Relocation> = emptyList(),
    val index: Int = 0,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Section) return false
        return name == other.name && kind == other.kind && data.contentEquals(other.data) &&
                address == other.address && align == other.align && flags == other.flags
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + kind.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}

enum class SectionKind {
    // Code
    TEXT,                  // .text — executable code
    PLT,                   // .plt — procedure linkage table
    PLT_GOT,               // .plt.got

    // Data
    DATA,                  // .data — initialized writable data
    RODATA,                // .rodata — read-only data
    BSS,                   // .bss — uninitialized data
    COMMON,                // common symbols

    // Thread-local storage
    TDATA,                 // .tdata — initialized TLS
    TBSS,                  // .tbss — uninitialized TLS

    // Dynamic linking
    GOT,                   // .got — global offset table
    GOT_PLT,               // .got.plt
    DYNAMIC,               // .dynamic — dynamic linking info
    DYNSYM,                // .dynsym — dynamic symbol table
    DYNSTR,                // .dynstr — dynamic string table
    HASH,                  // .hash / .gnu.hash
    GNU_HASH,
    INTERP,                // .interp — path to dynamic linker
    REL,                   // .rel.* — relocations
    RELA,                  // .rela.* — relocations with addend
    RELR,                  // .relr — relative relocations (compact)
    VERSION,               // .gnu.version
    VERSION_NEEDED,        // .gnu.version_r
    VERSION_DEF,           // .gnu.version_d

    // Symbol / string tables
    SYMTAB,                // .symtab
    STRTAB,                // .strtab

    // Constructors / destructors
    INIT,                  // .init
    FINI,                  // .fini
    INIT_ARRAY,            // .init_array
    FINI_ARRAY,            // .fini_array
    PREINIT_ARRAY,         // .preinit_array
    CTORS,                 // .ctors (legacy)
    DTORS,                 // .dtors (legacy)

    // Exception handling / unwind
    EH_FRAME,              // .eh_frame
    EH_FRAME_HDR,          // .eh_frame_hdr
    GCC_EXCEPT_TABLE,      // .gcc_except_table
    PDATA,                 // .pdata (Windows)
    XDATA,                 // .xdata (Windows)
    UNWIND_INFO,           // __unwind_info (Mach-O)

    // Debug info (DWARF)
    DEBUG_INFO,            // .debug_info
    DEBUG_ABBREV,          // .debug_abbrev
    DEBUG_LINE,            // .debug_line
    DEBUG_STR,             // .debug_str
    DEBUG_RANGES,          // .debug_ranges
    DEBUG_LOC,             // .debug_loc
    DEBUG_FRAME,           // .debug_frame
    DEBUG_ARANGES,         // .debug_aranges
    DEBUG_PUBNAMES,        // .debug_pubnames
    DEBUG_PUBTYPES,        // .debug_pubtypes
    DEBUG_MACRO,           // .debug_macro
    DEBUG_LINE_STR,        // .debug_line_str
    DEBUG_STR_OFFSETS,     // .debug_str_offsets
    DEBUG_ADDR,            // .debug_addr
    DEBUG_RNGLISTS,        // .debug_rnglists
    DEBUG_LOCLISTS,        // .debug_loclists

    // Notes
    NOTE,                  // .note.*
    GNU_BUILD_ID,          // .note.gnu.build-id
    GNU_PROPERTY,          // .note.gnu.property

    // ARM specific
    ARM_EXIDX,             // .ARM.exidx — ARM exception index
    ARM_EXTAB,             // .ARM.extab — ARM exception table
    ARM_ATTRIBUTES,        // .ARM.attributes

    // PE/COFF specific
    IDATA,                 // .idata — import directory
    EDATA,                 // .edata — export directory
    RSRC,                  // .rsrc — resources
    RELOC,                 // .reloc — base relocations (PE)
    TLS,                   // .tls — TLS directory (PE)
    LOAD_CONFIG,           // load configuration directory
    DELAY_IMPORT,          // delay-load import directory

    // Mach-O specific
    MACHO_STUBS,           // __stubs
    MACHO_STUB_HELPER,     // __stub_helper
    MACHO_LA_SYMBOL_PTR,   // __la_symbol_ptr
    MACHO_NL_SYMBOL_PTR,   // __nl_symbol_ptr
    MACHO_OBJC_METHNAMES,  // __objc_methnames
    MACHO_OBJC_CLASSLIST,  // __objc_classlist
    MACHO_COMPACT_UNWIND,  // __compact_unwind

    // WASM specific
    WASM_TYPE,
    WASM_IMPORT,
    WASM_FUNCTION,
    WASM_TABLE,
    WASM_MEMORY,
    WASM_GLOBAL,
    WASM_EXPORT,
    WASM_START,
    WASM_ELEMENT,
    WASM_CODE,
    WASM_DATA,
    WASM_DATA_COUNT,
    WASM_TAG,
    WASM_CUSTOM,
    WASM_LINKING,          // linking metadata
    WASM_NAME,             // name section

    // JVM specific
    JVM_CONSTANT_POOL,
    JVM_CODE,
    JVM_ATTRIBUTES,

    // Generic
    CUSTOM,
    UNKNOWN,
    ;

    val isDebug: Boolean get() = when (this) {
        DEBUG_INFO, DEBUG_ABBREV, DEBUG_LINE, DEBUG_STR, DEBUG_RANGES, DEBUG_LOC,
        DEBUG_FRAME, DEBUG_ARANGES, DEBUG_PUBNAMES, DEBUG_PUBTYPES, DEBUG_MACRO,
        DEBUG_LINE_STR, DEBUG_STR_OFFSETS, DEBUG_ADDR, DEBUG_RNGLISTS, DEBUG_LOCLISTS -> true
        else -> false
    }

    /** True for sections that should be included in the binary but not loaded into memory. */
    val isNonLoaded: Boolean get() = isDebug || this == CUSTOM
}

enum class SectionFlag {
    ALLOC,          // occupies memory at runtime
    WRITE,          // writable
    EXEC,           // executable
    MERGE,          // can be merged
    STRINGS,        // contains null-terminated strings
    INFO_LINK,      // sh_info holds section index
    LINK_ORDER,     // preserve link order
    GROUP,          // member of a section group
    TLS,            // thread-local storage
    COMPRESSED,     // compressed
    EXCLUDE,        // excluded from linking
    LARGE,          // large section (x86-64)
    GNU_RETAIN,     // retained by linker
    PURE_INSTRUCTIONS,   // Mach-O: only machine instructions
    SOME_INSTRUCTIONS,   // Mach-O: contains some machine instructions
    NO_DEAD_STRIP,       // Mach-O: do not dead-strip
    LIVE_SUPPORT,        // Mach-O: blocks must be live if referenced
    SELF_MODIFYING_CODE, // Mach-O: contains self-modifying code
    DISCARDABLE,         // PE: can be discarded
    NOT_CACHED,          // PE: not cacheable
    NOT_PAGED,           // PE: not pageable
    SHARED,              // PE: shared between processes
    READ,                // PE: readable
    INITIALIZED_DATA,    // PE: initialized data
    UNINITIALIZED_DATA,  // PE: uninitialized data
    COMDAT,              // PE: COMDAT section
    ALIGN_1,             // PE: alignment hints
    ALIGN_2,
    ALIGN_4,
    ALIGN_8,
    ALIGN_16,
    ALIGN_32,
    ALIGN_64,
    ALIGN_128,
    ALIGN_256,
    ALIGN_512,
    ALIGN_1024,
    ALIGN_2048,
    ALIGN_4096,
    ALIGN_8192,
}
