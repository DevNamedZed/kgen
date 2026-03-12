package org.kgen.codegen

import org.kgen.binary.LineEntry

/**
 * Maps native code offsets to source locations (file, line, column).
 *
 * Built during code generation by collecting [org.kgen.ir.Instruction.DebugLoc]
 * instructions and associating them with the machine code offsets where they appear.
 * Enables post-mortem debugging: given a crash address or profiler sample, look up
 * which source line produced that code.
 *
 * ```java
 * var map = compiledCode.debugLineMap();
 * var loc = map.lookup(0x42);          // SourceLoc("Foo.java", 17, 5)
 * var entries = map.entriesForFile("Foo.java");
 * ```
 */
class DebugLineMap private constructor(
    private val entries: List<Entry>,
) {
    /**
     * A single mapping from a native code offset to a source location.
     */
    data class Entry(
        /** Byte offset within the text section. */
        val codeOffset: Long,
        /** Source file path or name. */
        val file: String,
        /** Source line number (1-based). */
        val line: Int,
        /** Source column number (1-based, 0 if unknown). */
        val column: Int = 0,
        /** Function/scope name. */
        val scope: String = "",
        /** If this code was inlined, the location it was inlined from. */
        val inlinedAt: String? = null,
    )

    /** All entries, sorted by code offset. */
    fun entries(): List<Entry> = entries

    /** Number of entries. */
    fun size(): Int = entries.size

    /** Whether this map has any entries. */
    fun isEmpty(): Boolean = entries.isEmpty()

    /**
     * Look up the source location for a native code offset.
     * Returns the entry whose code offset is <= [codeOffset], or null if none.
     */
    fun lookup(codeOffset: Long): Entry? {
        if (entries.isEmpty()) return null
        // Binary search for the last entry with offset <= codeOffset
        var lo = 0
        var hi = entries.size - 1
        var result: Entry? = null
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (entries[mid].codeOffset <= codeOffset) {
                result = entries[mid]
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return result
    }

    /**
     * Look up the exact entry at [codeOffset], or null if no entry starts there.
     */
    fun lookupExact(codeOffset: Long): Entry? {
        val idx = entries.binarySearchBy(codeOffset) { it.codeOffset }
        return if (idx >= 0) entries[idx] else null
    }

    /** All entries for a given source file. */
    fun entriesForFile(file: String): List<Entry> =
        entries.filter { it.file == file }

    /** All entries within a given scope/function. */
    fun entriesForScope(scope: String): List<Entry> =
        entries.filter { it.scope == scope }

    /** All distinct source files referenced. */
    fun files(): Set<String> = entries.mapTo(mutableSetOf()) { it.file }

    /** All distinct scopes referenced. */
    fun scopes(): Set<String> = entries.mapTo(mutableSetOf()) { it.scope }

    /**
     * Convert to [LineEntry] list for integration with the binary debug info model.
     */
    fun toLineEntries(): List<LineEntry> = entries.map { entry ->
        LineEntry(
            address = entry.codeOffset,
            file = entry.file,
            line = entry.line,
            column = entry.column,
            isStatement = true,
        )
    }

    override fun toString(): String = buildString {
        append("DebugLineMap(${entries.size} entries)")
        if (entries.isNotEmpty()) {
            append(":\n")
            for (e in entries) {
                append("  0x${e.codeOffset.toString(16)}: ${e.file}:${e.line}")
                if (e.column > 0) append(":${e.column}")
                if (e.scope.isNotEmpty()) append(" [${e.scope}]")
                if (e.inlinedAt != null) append(" (inlined from ${e.inlinedAt})")
                append("\n")
            }
        }
    }

    /**
     * Builder for constructing a [DebugLineMap] during code generation.
     */
    class Builder {
        private val entries = mutableListOf<Entry>()

        /** Add a mapping from [codeOffset] to a source location. */
        fun add(codeOffset: Long, file: String, line: Int, column: Int = 0,
                scope: String = "", inlinedAt: String? = null) {
            entries.add(Entry(codeOffset, file, line, column, scope, inlinedAt))
        }

        /** Build the final [DebugLineMap], sorted by code offset. */
        fun build(): DebugLineMap {
            entries.sortBy { it.codeOffset }
            return DebugLineMap(entries.toList())
        }
    }

    companion object {
        /** Create an empty debug line map. */
        @JvmStatic
        fun empty(): DebugLineMap = DebugLineMap(emptyList())

        /** Create a builder. */
        @JvmStatic
        fun builder(): Builder = Builder()

        /**
         * Create a debug line map from a list of [LineEntry] values.
         */
        @JvmStatic
        fun fromLineEntries(entries: List<LineEntry>, scope: String = ""): DebugLineMap {
            return DebugLineMap(entries.map { entry ->
                Entry(
                    codeOffset = entry.address.toLong(),
                    file = entry.file,
                    line = entry.line,
                    column = entry.column,
                    scope = scope,
                )
            }.sortedBy { it.codeOffset })
        }
    }
}
