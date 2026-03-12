package org.kgen.cli.cmd

import org.kgen.binary.elf.ElfReader
import org.kgen.binary.macho.MachOReader
import org.kgen.binary.pe.PeReader
import org.kgen.target.wasm.module.WasmModuleReader
import org.kgen.binary.inspect.Inspectors
import org.kgen.cli.*

object ImportsCommand {
    fun run(args: List<String>) {
        val parsed = parseArgs(args)
        val path = parsed.requireFile("kgen imports <file>")
        val data = readFileOrExit(path)

        when (detectFormat(data)) {
            BinaryFormat.PE -> importsPe(data)
            BinaryFormat.WASM -> importsWasm(data)
            else -> importsGeneric(data)
        }
    }

    private fun importsPe(data: ByteArray) {
        val pe = PeReader.read(data)
        if (pe.importDirectories.isEmpty() && pe.delayImportDirectories.isEmpty()) {
            println("No imports")
            return
        }
        if (pe.importDirectories.isNotEmpty()) {
            println("Imports:")
            for (dir in pe.importDirectories) {
                println("  %s".format(dir.name))
                for (entry in dir.entries) {
                    val name = entry.name ?: "ordinal#${entry.ordinal}"
                    println("    %-50s hint=%d".format(name, entry.ordinal))
                }
            }
        }
        if (pe.delayImportDirectories.isNotEmpty()) {
            println()
            println("Delay-loaded Imports:")
            for (dir in pe.delayImportDirectories) {
                println("  %s".format(dir.name))
                for (entry in dir.entries) {
                    println("    %s".format(entry.name ?: "ordinal#${entry.ordinal}"))
                }
            }
        }
    }

    private fun importsWasm(data: ByteArray) {
        val m = WasmModuleReader.read(data)
        if (m.imports.isEmpty()) {
            println("No imports")
            return
        }
        println("Imports (${m.imports.size}):")
        for (imp in m.imports) {
            val kind = imp::class.simpleName?.removePrefix("Import.")?.removeSuffix("Import") ?: "?"
            println("  %-10s %s.%s".format(kind, imp.module, imp.name))
        }
    }

    private fun importsGeneric(data: ByteArray) {
        try {
            val inspector = Inspectors.forBytes(data)
            val imports = inspector.imports(data)
            if (imports.isEmpty()) {
                println("No imports")
                return
            }
            println("Imports (${imports.size}):")
            for (imp in imports) {
                println("  %-50s from %s".format(imp.name, imp.module))
            }
        } catch (e: Exception) {
            err("cannot read imports from this format")
        }
    }
}

object ExportsCommand {
    fun run(args: List<String>) {
        val parsed = parseArgs(args)
        val path = parsed.requireFile("kgen exports <file>")
        val data = readFileOrExit(path)

        when (detectFormat(data)) {
            BinaryFormat.PE -> exportsPe(data)
            BinaryFormat.WASM -> exportsWasm(data)
            else -> exportsGeneric(data)
        }
    }

    private fun exportsPe(data: ByteArray) {
        val pe = PeReader.read(data)
        val exp = pe.exportDirectory
        if (exp == null || exp.entries.isEmpty()) {
            println("No exports")
            return
        }
        println("Exports from ${exp.name ?: "?"}:")
        println("  %-8s %-8s %s".format("Ordinal", "RVA", "Name"))
        for (entry in exp.entries) {
            println("  %-8d %08x %s".format(entry.ordinal, entry.rva, entry.name ?: "<by ordinal>"))
        }
    }

    private fun exportsWasm(data: ByteArray) {
        val m = WasmModuleReader.read(data)
        if (m.exports.isEmpty()) {
            println("No exports")
            return
        }
        println("Exports (${m.exports.size}):")
        for (exp in m.exports) {
            println("  %-10s [%d]  %s".format(exp.kind, exp.index, exp.name))
        }
    }

    private fun exportsGeneric(data: ByteArray) {
        try {
            val inspector = Inspectors.forBytes(data)
            val exports = inspector.exports(data)
            if (exports.isEmpty()) {
                println("No exports")
                return
            }
            println("Exports (${exports.size}):")
            for (exp in exports) {
                println("  %016x  %s".format(exp.address, exp.name))
            }
        } catch (e: Exception) {
            err("cannot read exports from this format")
        }
    }
}
