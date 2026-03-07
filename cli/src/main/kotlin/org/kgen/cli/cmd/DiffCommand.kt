package org.kgen.cli.cmd

import org.kgen.tools.ElfBinaryDiff
import org.kgen.cli.*

object DiffCommand {
    fun run(args: List<String>) {
        val parsed = parseArgs(args)
        if (parsed.positional.size < 2) {
            err("missing arguments")
            System.err.println("Usage: kgen diff <old-file> <new-file>")
            return
        }
        val oldData = readFileOrExit(parsed.positional[0])
        val newData = readFileOrExit(parsed.positional[1])
        val structuralOnly = parsed.has("structural")
        val bytesOnly = parsed.has("bytes")

        if (detectFormat(oldData) != BinaryFormat.ELF || detectFormat(newData) != BinaryFormat.ELF) {
            err("diff currently supports ELF files only")
            return
        }

        val diff = ElfBinaryDiff()

        if (!bytesOnly) {
            val delta = diff.structuralDiff(oldData, newData)

            if (delta.addedSections.isNotEmpty()) {
                println("Added sections:")
                for (s in delta.addedSections) println("  + $s")
            }
            if (delta.removedSections.isNotEmpty()) {
                println("Removed sections:")
                for (s in delta.removedSections) println("  - $s")
            }
            if (delta.modifiedSections.isNotEmpty()) {
                println("Modified sections:")
                for (s in delta.modifiedSections) println("  ~ $s")
            }
            if (delta.addedSymbols.isNotEmpty()) {
                println("Added symbols:")
                for (s in delta.addedSymbols) println("  + $s")
            }
            if (delta.removedSymbols.isNotEmpty()) {
                println("Removed symbols:")
                for (s in delta.removedSymbols) println("  - $s")
            }
            if (delta.modifiedSymbols.isNotEmpty()) {
                println("Modified symbols:")
                for (s in delta.modifiedSymbols) println("  ~ $s")
            }
            if (delta.addedImports.isNotEmpty()) {
                println("Added imports:")
                for (s in delta.addedImports) println("  + $s")
            }
            if (delta.removedImports.isNotEmpty()) {
                println("Removed imports:")
                for (s in delta.removedImports) println("  - $s")
            }
            if (delta.addedExports.isNotEmpty()) {
                println("Added exports:")
                for (s in delta.addedExports) println("  + $s")
            }
            if (delta.removedExports.isNotEmpty()) {
                println("Removed exports:")
                for (s in delta.removedExports) println("  - $s")
            }

            val allEmpty = delta.addedSections.isEmpty() && delta.removedSections.isEmpty() &&
                    delta.modifiedSections.isEmpty() && delta.addedSymbols.isEmpty() &&
                    delta.removedSymbols.isEmpty() && delta.modifiedSymbols.isEmpty() &&
                    delta.addedImports.isEmpty() && delta.removedImports.isEmpty() &&
                    delta.addedExports.isEmpty() && delta.removedExports.isEmpty()
            if (allEmpty) println("No structural differences")
        }

        if (!structuralOnly) {
            val deltas = diff.diff(oldData, newData)
            if (deltas.isEmpty()) {
                if (bytesOnly) println("Files are identical")
            } else {
                if (!bytesOnly) println()
                println("Byte-level changes (${deltas.size}):")
                for (d in deltas) {
                    val sym = if (d.nearestSymbol != null) " near ${d.nearestSymbol}" else ""
                    println("  @${hex(d.offset)} [${d.section}]$sym: ${d.oldBytes.size} bytes changed")
                }
            }
        }
    }
}
