package org.kgen.cli.cmd

import org.kgen.binary.elf.ElfReader
import org.kgen.binary.macho.MachOReader
import org.kgen.binary.pe.PeReader
import org.kgen.tools.Inspectors
import org.kgen.tools.UniversalDemangler
import org.kgen.cli.*

object SymbolsCommand {
    fun run(args: List<String>) {
        val parsed = parseArgs(args)
        val path = parsed.requireFile("kgen symbols <file>")
        val data = readFileOrExit(path)
        val demangle = parsed.has("D", "demangle")
        val undefinedOnly = parsed.has("u", "undefined")
        val definedOnly = parsed.has("d", "defined")
        val globalOnly = parsed.has("g", "global")
        val sortByValue = parsed.has("S")
        val sortByName = parsed.has("n")

        when (detectFormat(data)) {
            BinaryFormat.ELF -> symbolsElf(data, demangle, undefinedOnly, definedOnly, globalOnly, sortByValue, sortByName)
            BinaryFormat.PE -> symbolsPe(data, demangle)
            BinaryFormat.MACHO -> symbolsMachO(data, demangle, undefinedOnly, definedOnly, globalOnly, sortByValue, sortByName)
            else -> symbolsGeneric(data, demangle)
        }
    }

    private val demangler = UniversalDemangler()

    private fun dem(name: String, demangle: Boolean): String {
        if (!demangle) return name
        return demangler.demangle(name) ?: name
    }

    private fun symbolsElf(data: ByteArray, demangle: Boolean, undefinedOnly: Boolean,
                            definedOnly: Boolean, globalOnly: Boolean,
                            sortByValue: Boolean, sortByName: Boolean) {
        val elf = ElfReader.read(data)
        var syms = elf.symbols.toList()

        if (undefinedOnly) syms = syms.filter { it.sectionIndex == 0 }
        if (definedOnly) syms = syms.filter { it.sectionIndex != 0 }
        if (globalOnly) syms = syms.filter {
            it.binding?.name == "GLOBAL" || it.binding?.name == "WEAK"
        }
        if (sortByValue) syms = syms.sortedBy { it.value }
        if (sortByName) syms = syms.sortedBy { it.name }

        println("Symbols (${syms.size}):")
        println("  %-16s %-8s %-8s %-8s %-8s %s".format("Value", "Size", "Bind", "Type", "Vis", "Name"))
        for (s in syms) {
            val name = dem(s.name, demangle)
            val section = if (s.sectionIndex == 0) "UND" else s.sectionName ?: "${s.sectionIndex}"
            println("  %016x %-8d %-8s %-8s %-8s %s  [%s]".format(
                s.value, s.size,
                s.binding?.name ?: "?",
                s.type?.name ?: "?",
                s.visibility?.name ?: "?",
                name, section))
        }

        // Also show dynamic symbols if present
        if (elf.dynamicSymbols.isNotEmpty()) {
            println()
            println("Dynamic Symbols (${elf.dynamicSymbols.size}):")
            for (s in elf.dynamicSymbols) {
                println("  %016x %-8s %-8s %s".format(
                    s.value, s.binding?.name ?: "?", s.type?.name ?: "?",
                    dem(s.name, demangle)))
            }
        }
    }

    private fun symbolsPe(data: ByteArray, demangle: Boolean) {
        val pe = PeReader.read(data)
        if (pe.symbols.isNotEmpty()) {
            println("COFF Symbols (${pe.symbols.size}):")
            for (s in pe.symbols) {
                println("  %08x  sect=%-4d  type=%04x  class=%-4d  %s".format(
                    s.value, s.sectionNumber, s.type, s.storageClass, dem(s.name, demangle)))
            }
        }

        // PE imports as "undefined" symbols
        if (pe.importDirectories.isNotEmpty()) {
            println()
            println("Import Symbols:")
            for (dir in pe.importDirectories) {
                for (entry in dir.entries) {
                    val name = entry.name ?: "ordinal#${entry.ordinal}"
                    println("  %-40s  from %s".format(dem(name, demangle), dir.name))
                }
            }
        }

        // PE exports as "defined" symbols
        val exp = pe.exportDirectory
        if (exp != null && exp.entries.isNotEmpty()) {
            println()
            println("Export Symbols:")
            for (entry in exp.entries) {
                println("  %08x  %s".format(entry.rva, dem(entry.name ?: "ordinal#${entry.ordinal}", demangle)))
            }
        }
    }

    private fun symbolsMachO(data: ByteArray, demangle: Boolean, undefinedOnly: Boolean,
                              definedOnly: Boolean, globalOnly: Boolean,
                              sortByValue: Boolean, sortByName: Boolean) {
        val m = MachOReader.read(data)
        var syms = m.symbols.toList()

        if (undefinedOnly) syms = syms.filter { it.isUndefined }
        if (definedOnly) syms = syms.filter { !it.isUndefined }
        if (globalOnly) syms = syms.filter { it.isExternal }
        if (sortByValue) syms = syms.sortedBy { it.value }
        if (sortByName) syms = syms.sortedBy { it.name }

        println("Symbols (${syms.size}):")
        for (s in syms) {
            val kind = when {
                s.isUndefined -> "U"
                s.isExternal -> "T"
                s.isAbsolute -> "A"
                else -> "t"
            }
            println("  %016x %s %s".format(s.value, kind, dem(s.name, demangle)))
        }
    }

    private fun symbolsGeneric(data: ByteArray, demangle: Boolean) {
        try {
            val inspector = Inspectors.forBytes(data)
            val syms = inspector.symbols(data)
            println("Symbols (${syms.size}):")
            for (s in syms) {
                println("  %016x %-8s %-8s %s".format(
                    s.value, s.binding, s.kind, dem(s.name, demangle)))
            }
        } catch (e: Exception) {
            err("cannot read symbols from this format")
        }
    }
}
