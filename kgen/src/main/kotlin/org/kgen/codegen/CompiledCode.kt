package org.kgen.codegen

import org.kgen.binary.*
import org.kgen.ir.StackMap

/**
 * Lightweight result of code generation, containing assembled bytes and metadata
 * without the full [ObjectFile] overhead.
 *
 * This is the primary output for JIT compilation where constructing sections,
 * symbol tables, and format-specific headers is unnecessary. For AOT compilation,
 * use [toObjectFile] to wrap the result in a full [ObjectFile].
 *
 * ```java
 * var code = generator.generateCode(module);
 * // JIT path: use bytes + relocations directly
 * var text = code.textBytes();
 *
 * // AOT path: wrap in ObjectFile for linking
 * var obj = code.toObjectFile(ObjectFormat.ELF, new Architecture(ArchType.X86_64));
 * ```
 */
data class CompiledCode(
    /** Assembled machine code bytes. */
    val textBytes: ByteArray,
    /** Read-only data (string constants, jump tables, etc.). Empty if none. */
    val rodataBytes: ByteArray = ByteArray(0),
    val dataBytes: ByteArray = ByteArray(0),
    /** Symbols defined in the compiled code (functions, data labels). */
    val symbols: List<CodeSymbol> = emptyList(),
    /** Relocations that need patching when the code is loaded. */
    val relocations: List<Relocation> = emptyList(),
    /** External symbols referenced but not defined (need resolution at load time). */
    val externalSymbols: Set<String> = emptySet(),
    /** Rodata alignment requirement. */
    val rodataAlign: Int = 1,
    /** Stack maps for GC integration — one per function with a GC strategy. */
    val stackMaps: List<StackMap> = emptyList(),
    /** Debug line map — maps code offsets to source locations. Empty if no debug info. */
    val debugLineMap: DebugLineMap = DebugLineMap.empty(),
    /** DWARF .eh_frame section bytes for stack unwinding. Empty if not generated. */
    val ehFrameBytes: ByteArray = ByteArray(0),
    /** GCC-style .gcc_except_table (LSDA) for exception handling. Empty if no exceptions. */
    val exceptTableBytes: ByteArray = ByteArray(0),
    /** .eh_frame_hdr section — binary search table for fast PC→FDE lookup. Empty if no eh_frame. */
    val ehFrameHdrBytes: ByteArray = ByteArray(0),
    /** PE .pdata section (RUNTIME_FUNCTION table for SEH). Empty if not PE target. */
    val pdataBytes: ByteArray = ByteArray(0),
    /** PE .xdata section (UNWIND_INFO structures for SEH). Empty if not PE target. */
    val xdataBytes: ByteArray = ByteArray(0),
    /** Thread-local initialized data (.tdata). Empty if no TLS globals. */
    val tdataBytes: ByteArray = ByteArray(0),
    /** TLS data alignment requirement. */
    val tdataAlign: Int = 1,
) {

    /**
     * A symbol in compiled code. Simpler than [Symbol] — just name, offset, and kind.
     */
    data class CodeSymbol(
        val name: String,
        /** Byte offset within the text section. */
        val offset: Long,
        /** Whether this is a function or data. */
        val kind: SymbolKind = SymbolKind.FUNCTION,
        /** Whether this symbol is visible outside the module. */
        val isGlobal: Boolean = true,
        /** Byte offset within rodata section (for data symbols). -1 if in text. */
        val rodataOffset: Long = -1,
        /** Byte offset within data section (for mutable data symbols). -1 if not data. */
        val dataOffset: Long = -1,
        /** Byte offset within tdata section (for TLS symbols). -1 if not TLS. */
        val tdataOffset: Long = -1,
    )

    /**
     * Wrap this compiled code in a full [ObjectFile] for AOT output (linking, writing to disk).
     */
    @JvmOverloads
    fun toObjectFile(
        format: ObjectFormat,
        arch: Architecture,
        imports: List<ImportEntry> = emptyList(),
    ): ObjectFile {
        val sections = mutableListOf(
            Section(".text", SectionKind.TEXT, textBytes, align = 16)
        )
        if (rodataBytes.isNotEmpty()) {
            sections.add(Section(".rodata", SectionKind.RODATA, rodataBytes, align = rodataAlign))
        }
        if (dataBytes.isNotEmpty()) {
            sections.add(Section(".data", SectionKind.DATA, dataBytes, align = 8))
        }
        if (ehFrameBytes.isNotEmpty()) {
            sections.add(Section(".eh_frame", SectionKind.EH_FRAME, ehFrameBytes, align = 8))
        }
        if (exceptTableBytes.isNotEmpty()) {
            sections.add(Section(".gcc_except_table", SectionKind.GCC_EXCEPT_TABLE, exceptTableBytes, align = 4))
        }
        if (ehFrameHdrBytes.isNotEmpty()) {
            sections.add(Section(".eh_frame_hdr", SectionKind.EH_FRAME_HDR, ehFrameHdrBytes, align = 4))
        }
        if (pdataBytes.isNotEmpty()) {
            sections.add(Section(".pdata", SectionKind.PDATA, pdataBytes, align = 4))
        }
        if (xdataBytes.isNotEmpty()) {
            sections.add(Section(".xdata", SectionKind.XDATA, xdataBytes, align = 4))
        }
        if (tdataBytes.isNotEmpty()) {
            sections.add(Section(".tdata", SectionKind.TDATA, tdataBytes, align = tdataAlign))
        }
        val objSymbols = symbols.map { sym ->
            Symbol(
                name = sym.name,
                value = when {
                    sym.dataOffset >= 0 -> sym.dataOffset
                    sym.rodataOffset >= 0 -> sym.rodataOffset
                    else -> sym.offset
                },
                section = when {
                    sym.dataOffset >= 0 -> ".data"
                    sym.rodataOffset >= 0 -> ".rodata"
                    else -> ".text"
                },
                binding = if (sym.isGlobal) SymbolBinding.GLOBAL else SymbolBinding.LOCAL,
                kind = sym.kind,
            )
        } + externalSymbols.map { name ->
            Symbol(name = name, kind = SymbolKind.UNDEFINED)
        }
        return ObjectFile(
            format = format,
            arch = arch,
            sections = sections,
            symbols = objSymbols,
            relocations = relocations,
            imports = imports,
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CompiledCode) return false
        return textBytes.contentEquals(other.textBytes) &&
            rodataBytes.contentEquals(other.rodataBytes) &&
            ehFrameBytes.contentEquals(other.ehFrameBytes) &&
            symbols == other.symbols &&
            relocations == other.relocations
    }

    override fun hashCode(): Int {
        var result = textBytes.contentHashCode()
        result = 31 * result + rodataBytes.contentHashCode()
        result = 31 * result + ehFrameBytes.contentHashCode()
        result = 31 * result + symbols.hashCode()
        result = 31 * result + relocations.hashCode()
        return result
    }
}
