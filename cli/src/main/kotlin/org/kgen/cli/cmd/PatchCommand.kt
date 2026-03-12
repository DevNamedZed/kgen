package org.kgen.cli.cmd

import org.kgen.binary.patch.ElfBinaryPatcher
import org.kgen.cli.*
import java.io.File

object PatchCommand {
    fun run(args: List<String>) {
        if (args.isEmpty()) {
            err("missing arguments")
            System.err.println("Usage: kgen patch <file> [operations...] -o <output>")
            return
        }

        val path = args[0]
        val data = readFileOrExit(path)

        if (detectFormat(data) != BinaryFormat.ELF) {
            err("patch currently supports ELF files only")
            return
        }

        // Parse operations from remaining args
        val ops = mutableListOf<PatchOp>()
        var output: String? = null
        var i = 1
        while (i < args.size) {
            when (args[i]) {
                "-o", "--output" -> { output = args.getOrNull(i + 1); i += 2 }
                "--set-rpath" -> { ops.add(PatchOp.SetRpath(args[i + 1])); i += 2 }
                "--add-needed" -> { ops.add(PatchOp.AddNeeded(args[i + 1])); i += 2 }
                "--remove-needed" -> { ops.add(PatchOp.RemoveNeeded(args[i + 1])); i += 2 }
                "--replace-needed" -> {
                    ops.add(PatchOp.ReplaceNeeded(args[i + 1], args[i + 2])); i += 3
                }
                "--rename-symbol" -> {
                    ops.add(PatchOp.RenameSymbol(args[i + 1], args[i + 2])); i += 3
                }
                "--strip-section" -> { ops.add(PatchOp.StripSection(args[i + 1])); i += 2 }
                "--set-entry" -> {
                    val addr = args[i + 1].removePrefix("0x").toLong(16)
                    ops.add(PatchOp.SetEntry(addr)); i += 2
                }
                else -> { err("unknown patch operation: ${args[i]}"); return }
            }
        }

        if (output == null) {
            err("output file required (-o <file>)")
            return
        }
        if (ops.isEmpty()) {
            err("no patch operations specified")
            return
        }

        val patcher = ElfBinaryPatcher()
        val binary = patcher.load(data)

        for (op in ops) {
            when (op) {
                is PatchOp.SetRpath -> {
                    binary.setRpath(op.path.split(":"))
                    println("Set RPATH: ${op.path}")
                }
                is PatchOp.AddNeeded -> {
                    binary.addDependency(op.lib)
                    println("Added dependency: ${op.lib}")
                }
                is PatchOp.RemoveNeeded -> {
                    binary.removeDependency(op.lib)
                    println("Removed dependency: ${op.lib}")
                }
                is PatchOp.ReplaceNeeded -> {
                    binary.renameDependency(op.old, op.new)
                    println("Replaced dependency: ${op.old} -> ${op.new}")
                }
                is PatchOp.RenameSymbol -> {
                    binary.renameSymbol(op.old, op.new)
                    println("Renamed symbol: ${op.old} -> ${op.new}")
                }
                is PatchOp.StripSection -> {
                    binary.removeSection(op.name)
                    println("Removed section: ${op.name}")
                }
                is PatchOp.SetEntry -> {
                    binary.setEntryPoint(op.addr)
                    println("Set entry point: ${hex(op.addr)}")
                }
            }
        }

        val result = binary.assemble()
        File(output).writeBytes(result)
        println("Written: $output (${result.size} bytes)")
    }

    private sealed class PatchOp {
        data class SetRpath(val path: String) : PatchOp()
        data class AddNeeded(val lib: String) : PatchOp()
        data class RemoveNeeded(val lib: String) : PatchOp()
        data class ReplaceNeeded(val old: String, val new: String) : PatchOp()
        data class RenameSymbol(val old: String, val new: String) : PatchOp()
        data class StripSection(val name: String) : PatchOp()
        data class SetEntry(val addr: Long) : PatchOp()
    }
}

object StripCommand {
    fun run(args: List<String>) {
        val parsed = parseArgs(args)
        val path = parsed.requireFile("kgen strip <file>")
        val data = readFileOrExit(path)
        val output = parsed.get("o", "output") ?: path
        val keepSymbols = parsed.has("keep-symbols")

        if (detectFormat(data) != BinaryFormat.ELF) {
            err("strip currently supports ELF files only")
            return
        }

        val patcher = ElfBinaryPatcher()
        val binary = patcher.load(data)

        // Remove debug sections
        val debugSections = binary.symbols() // dummy call to ensure loaded
        val allSections = listOf(".debug_info", ".debug_abbrev", ".debug_line",
            ".debug_str", ".debug_ranges", ".debug_loc", ".debug_frame",
            ".debug_aranges", ".debug_pubnames", ".debug_pubtypes",
            ".debug_macinfo", ".debug_macro", ".comment", ".note.gnu.build-id")

        var removed = 0
        for (sec in allSections) {
            try {
                if (binary.readSection(sec) != null) {
                    binary.removeSection(sec)
                    removed++
                }
            } catch (_: Exception) {}
        }

        if (!keepSymbols) {
            // Remove local symbols (keep globals)
            val syms = binary.symbols()
            for (sym in syms) {
                if (sym.binding.toString() == "LOCAL" && sym.name.isNotEmpty()) {
                    try { binary.removeSymbol(sym.name) } catch (_: Exception) {}
                }
            }
        }

        val result = binary.assemble()
        File(output).writeBytes(result)
        println("Stripped $removed sections -> $output (${result.size} bytes)")
    }
}
