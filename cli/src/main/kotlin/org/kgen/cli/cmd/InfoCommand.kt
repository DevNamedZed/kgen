package org.kgen.cli.cmd

import org.kgen.binary.elf.ElfReader
import org.kgen.binary.elf.ElfSymbolType
import org.kgen.binary.macho.MachO
import org.kgen.binary.macho.MachOReader
import org.kgen.binary.pe.PeReader
import org.kgen.binary.pe.PeConstants
import org.kgen.target.wasm.module.WasmModule
import org.kgen.target.wasm.module.WasmModuleReader
import org.kgen.target.wasm.disasm.WasmDisassembler
import org.kgen.target.jvm.JvmClassReader
import org.kgen.target.jvm.AccessFlags
import org.kgen.binary.ar.ArchiveReader
import org.kgen.cli.*

object InfoCommand {
    fun run(args: List<String>) {
        val parsed = parseArgs(args)
        val path = parsed.requireFile("kgen info <file>")
        val data = readFileOrExit(path)
        val verbose = parsed.has("v", "verbose")

        when (detectFormat(data)) {
            BinaryFormat.ELF -> infoElf(data, verbose)
            BinaryFormat.PE -> infoPe(data, verbose)
            BinaryFormat.MACHO -> infoMachO(data, verbose)
            BinaryFormat.WASM -> infoWasm(data, verbose)
            BinaryFormat.CLASS -> infoClass(data, verbose)
            BinaryFormat.ARCHIVE -> infoArchive(data, verbose)
            BinaryFormat.UNKNOWN -> err("unrecognized binary format")
        }
    }

    private fun infoElf(data: ByteArray, verbose: Boolean) {
        val elf = ElfReader.read(data)
        val h = elf.header
        println("Format:       ELF${if (h.elfClass?.code == 2) "64" else "32"}")
        println("Machine:      ${h.machine ?: "unknown"}")
        println("Type:         ${h.type ?: "unknown"}")
        println("Endian:       ${h.dataEncoding ?: "unknown"}")
        println("OS/ABI:       ${h.osAbi}")
        println("Entry point:  ${hex(h.entryPoint)}")
        println("Sections:     ${elf.sections.size}")
        println("Symbols:      ${elf.symbols.size}")
        if (elf.dynamicSymbols.isNotEmpty())
            println("Dynamic syms: ${elf.dynamicSymbols.size}")
        if (elf.relocations.isNotEmpty())
            println("Relocations:  ${elf.relocations.size}")
        val dyn = elf.dynamicInfo
        if (dyn != null && dyn.neededLibraries.isNotEmpty()) {
            println("Dependencies: ${dyn.neededLibraries.joinToString(", ")}")
        }

        if (!verbose) return

        // Sections
        println()
        println("Sections:")
        println("  %-4s %-20s %-12s %10s %16s".format("Idx", "Name", "Type", "Size", "Address"))
        for ((i, sec) in elf.sections.withIndex()) {
            println("  %-4d %-20s %-12s %10d %16s".format(
                i, sec.name, sec.type ?: "", sec.size, hex(sec.address)))
        }

        // Symbols (globals and functions)
        val importantSyms = elf.symbols.filter { it.name.isNotEmpty() }
        if (importantSyms.isNotEmpty()) {
            println()
            println("Symbols (${importantSyms.size}):")
            for (sym in importantSyms.sortedBy { it.value }) {
                val bind = sym.binding?.name?.lowercase()?.padEnd(8) ?: "        "
                val kind = sym.type?.name?.lowercase()?.padEnd(8) ?: "        "
                println("  %16s  %s %s %s".format(hex(sym.value), bind, kind, sym.name))
            }
        }

        // Dynamic symbols
        if (elf.dynamicSymbols.isNotEmpty()) {
            println()
            println("Dynamic Symbols (${elf.dynamicSymbols.size}):")
            for (sym in elf.dynamicSymbols.filter { it.name.isNotEmpty() }) {
                val bind = sym.binding?.name?.lowercase()?.padEnd(8) ?: "        "
                val kind = sym.type?.name?.lowercase()?.padEnd(8) ?: "        "
                println("  %16s  %s %s %s".format(hex(sym.value), bind, kind, sym.name))
            }
        }

        // Relocations summary
        if (elf.relocations.isNotEmpty()) {
            println()
            println("Relocations (${elf.relocations.size}):")
            val bySection = elf.relocations.groupBy { it.sectionName ?: "?" }
            for ((secName, relocs) in bySection) {
                println("  $secName: ${relocs.size} entries")
                for (r in relocs.take(10)) {
                    val sym = r.symbolName ?: ""
                    println("    %16s  type=%-4d %s".format(hex(r.offset), r.type, sym))
                }
                if (relocs.size > 10) println("    ... and ${relocs.size - 10} more")
            }
        }

        // Disassembly of .text
        val arch = elfArch(elf.header.machine)
        val textSec = elf.sections.firstOrNull { it.name == ".text" && it.data.isNotEmpty() }
        if (textSec != null) {
            println()
            val funcSyms = elf.symbols.filter { it.type == ElfSymbolType.FUNC && it.sectionName == ".text" && it.size > 0 }
                .sortedBy { it.value }
            if (funcSyms.isNotEmpty()) {
                println("Functions (${funcSyms.size}):")
                for (sym in funcSyms) {
                    val offset = (sym.value - textSec.address).toInt()
                    if (offset < 0 || offset >= textSec.data.size) continue
                    val size = minOf(sym.size.toInt(), textSec.data.size - offset)
                    val code = textSec.data.copyOfRange(offset, offset + size)
                    println()
                    println("${hex(sym.value)} <${sym.name}>:")
                    disassemble(arch, code, sym.value, showBytes = true)
                }
            } else {
                println("Disassembly of .text:")
                disassemble(arch, textSec.data, textSec.address, showBytes = true)
            }
        }
    }

    private fun infoPe(data: ByteArray, verbose: Boolean) {
        val pe = PeReader.read(data)
        val kind = when {
            pe.isDll -> "DLL"
            pe.isExecutable -> "Executable"
            else -> "Object"
        }
        println("Format:       PE${if (pe.isPe32Plus) "32+" else "32"} ($kind)")
        println("Machine:      0x${pe.coffHeader.machine.toString(16)}")
        println("Sections:     ${pe.sections.size}")
        if (pe.optionalHeader != null) {
            println("Image base:   ${hex(pe.imageBase)}")
            println("Entry point:  ${hex(pe.entryPointRVA.toLong())}")
            val oh = pe.optionalHeader!!
            println("Subsystem:    ${oh.subsystem}")
        }
        if (pe.importDirectories.isNotEmpty()) {
            val total = pe.importDirectories.sumOf { it.entries.size }
            println("Imports:      $total from ${pe.importDirectories.size} DLLs")
        }
        pe.exportDirectory?.let { println("Exports:      ${it.entries.size}") }
        if (pe.isManagedAssembly) {
            val clr = pe.clrMetadata!!
            val mode = if (pe.isILOnly) "IL-only" else "mixed-mode"
            println("CLR:          ${clr.metadataVersion} ($mode)")
            println("  Types:      ${clr.tables.typeDefs.size}")
            println("  Methods:    ${clr.tables.methodDefs.size}")
        }

        if (!verbose) return

        // Sections
        println()
        println("Sections:")
        for (sec in pe.sections) {
            val name = sec.name.trim('\u0000')
            println("  %-10s  vsize=%-8d  vaddr=%08x  raw=%-8d  chars=%08x".format(
                name, sec.virtualSize, sec.virtualAddress, sec.data.size, sec.characteristics))
        }

        // Imports
        if (pe.importDirectories.isNotEmpty()) {
            println()
            println("Imports:")
            for (dir in pe.importDirectories) {
                println("  ${dir.name} (${dir.entries.size} functions):")
                for (entry in dir.entries.take(20)) {
                    println("    ${entry.name ?: "ordinal ${entry.ordinal}"}")
                }
                if (dir.entries.size > 20) println("    ... and ${dir.entries.size - 20} more")
            }
        }

        // Exports
        pe.exportDirectory?.let { exp ->
            if (exp.entries.isNotEmpty()) {
                println()
                println("Exports (${exp.entries.size}):")
                for (entry in exp.entries.take(50)) {
                    println("  ${entry.name ?: "ordinal ${entry.ordinal}"} @ ${hex(entry.rva.toLong())}")
                }
                if (exp.entries.size > 50) println("  ... and ${exp.entries.size - 50} more")
            }
        }

        // CLR types
        if (pe.isManagedAssembly) {
            val clr = pe.clrMetadata!!
            if (clr.tables.typeDefs.isNotEmpty()) {
                println()
                println("CLR Types (${clr.tables.typeDefs.size}):")
                for (td in clr.tables.typeDefs) {
                    val ns = clr.strings.get(td.namespace)
                    val name = clr.strings.get(td.name)
                    val full = if (ns.isNotEmpty()) "$ns.$name" else name
                    println("  $full")
                }
            }
        }

        // Disassembly
        val textSec = pe.sections.firstOrNull {
            it.characteristics and 0x20000000 != 0 && it.data.isNotEmpty()
        }
        if (textSec != null) {
            println()
            println("Disassembly of ${textSec.name.trim('\u0000')} (first 256 bytes):")
            val code = textSec.data.copyOfRange(0, minOf(256, textSec.data.size))
            disassemble("x86_64", code, textSec.virtualAddress.toLong(), showBytes = true)
        }
    }

    private fun infoMachO(data: ByteArray, verbose: Boolean) {
        val m = MachOReader.read(data)
        val fileType = when (m.header.fileType) {
            1 -> "Object"
            2 -> "Executable"
            6 -> "Dylib"
            8 -> "Bundle"
            else -> "0x${m.header.fileType.toString(16)}"
        }
        println("Format:       Mach-O${if (m.header.is64Bit) " 64-bit" else ""} ($fileType)")
        println("CPU:          ${machoArch(m.header.cpuType)}")
        println("Segments:     ${m.segments.size}")
        println("Sections:     ${m.allSections.size}")
        println("Symbols:      ${m.symbols.size}")
        if (m.dylibs.isNotEmpty())
            println("Dylibs:       ${m.dylibs.joinToString(", ")}")
        m.mainEntryOffset?.let { println("Entry offset: ${hex(it)}") }

        if (!verbose) return

        // Segments and sections
        println()
        for (seg in m.segments) {
            println("Segment ${seg.name}  addr=${hex(seg.vmAddress)}  size=${seg.vmSize}")
            for (sec in seg.sections) {
                println("  %-20s  addr=%-16s  size=%d".format(
                    sec.sectionName, hex(sec.address), sec.data.size))
            }
        }

        // Symbols
        if (m.symbols.isNotEmpty()) {
            println()
            println("Symbols (${m.symbols.size}):")
            for (sym in m.symbols.take(50)) {
                println("  %16s  %s".format(hex(sym.value), sym.name))
            }
            if (m.symbols.size > 50) println("  ... and ${m.symbols.size - 50} more")
        }

        // Disassembly
        val arch = machoArch(m.header.cpuType)
        val textSec = m.allSections.firstOrNull { it.sectionName == "__text" && it.data.isNotEmpty() }
        if (textSec != null) {
            println()
            println("Disassembly of __text (first 256 bytes):")
            val code = textSec.data.copyOfRange(0, minOf(256, textSec.data.size))
            disassemble(arch, code, textSec.address, showBytes = true)
        }
    }

    private fun infoWasm(data: ByteArray, verbose: Boolean) {
        val m = WasmModuleReader.read(data)
        println("Format:       WebAssembly")
        println("Version:      ${m.version}")
        println("Types:        ${m.types.size}")
        println("Functions:    ${m.functions.size} (+ ${m.importedFunctionCount} imported)")
        println("Tables:       ${m.tables.size}")
        println("Memories:     ${m.memories.size}")
        println("Globals:      ${m.globals.size}")
        println("Exports:      ${m.exports.size}")
        println("Imports:      ${m.imports.size}")
        if (m.dataSegments.isNotEmpty())
            println("Data segs:    ${m.dataSegments.size}")
        if (m.customSections.isNotEmpty())
            println("Custom secs:  ${m.customSections.map { it.name }.joinToString(", ")}")

        if (!verbose) return

        // Imports
        if (m.imports.isNotEmpty()) {
            println()
            println("Imports:")
            for (imp in m.imports) {
                val detail = when (imp) {
                    is WasmModule.Import.Func -> "func type=${imp.typeIndex}"
                    is WasmModule.Import.Table -> "table ${imp.refType}"
                    is WasmModule.Import.Memory -> "memory ${imp.min}..${imp.max ?: ""}"
                    is WasmModule.Import.Global -> "global ${if (imp.mutable) "var" else "const"} ${imp.type}"
                }
                println("  ${imp.module}::${imp.name}  $detail")
            }
        }

        // Exports
        if (m.exports.isNotEmpty()) {
            println()
            println("Exports:")
            for (exp in m.exports) {
                println("  ${exp.kind.name.lowercase().padEnd(10)} ${exp.name} (index ${exp.index})")
            }
        }

        // Functions with disassembly
        if (m.functions.isNotEmpty()) {
            println()
            val disasm = WasmDisassembler()
            for ((i, func) in m.functions.withIndex()) {
                val idx = m.importedFunctionCount + i
                val name = func.name ?: m.functionName(idx) ?: "func[$idx]"
                val sig = if (func.typeIndex < m.types.size) {
                    val t = m.types[func.typeIndex]
                    "(${t.params.joinToString(", ")}) -> (${t.results.joinToString(", ")})"
                } else "?"
                println("$name $sig:")
                val instructions = disasm.disassemble(func)
                var indent = 1
                for (inst in instructions) {
                    val mn = inst.opcode.mnemonic
                    if (mn == "end" || mn == "else") indent = maxOf(1, indent - 1)
                    println("  %04x: %s%s".format(inst.offset, "  ".repeat(indent), inst.text()))
                    if (mn == "block" || mn == "loop" || mn == "if" || mn == "else") indent++
                }
                println()
            }
        }
    }

    private fun infoClass(data: ByteArray, verbose: Boolean) {
        val cf = JvmClassReader.read(data)
        val kind = when {
            cf.accessFlags and AccessFlags.INTERFACE != 0 -> "interface"
            cf.accessFlags and AccessFlags.ENUM != 0 -> "enum"
            cf.accessFlags and AccessFlags.ANNOTATION != 0 -> "annotation"
            cf.accessFlags and AccessFlags.MODULE != 0 -> "module"
            cf.accessFlags and AccessFlags.ABSTRACT != 0 -> "abstract class"
            else -> "class"
        }
        val vis = when {
            cf.accessFlags and AccessFlags.PUBLIC != 0 -> "public"
            cf.accessFlags and AccessFlags.PRIVATE != 0 -> "private"
            cf.accessFlags and AccessFlags.PROTECTED != 0 -> "protected"
            else -> "package-private"
        }
        println("Format:       JVM class file")
        println("Version:      ${cf.majorVersion}.${cf.minorVersion} (Java ${cf.javaVersion})")
        println("Class:        ${cf.thisClassName.replace('/', '.')}")
        println("Access:       $vis $kind")
        cf.superClassName?.let { println("Extends:      ${it.replace('/', '.')}") }
        if (cf.interfaceNames.isNotEmpty())
            println("Implements:   ${cf.interfaceNames.joinToString(", ") { it.replace('/', '.') }}")
        println("Fields:       ${cf.fields.size}")
        println("Methods:      ${cf.methods.size}")
        println("CP entries:   ${cf.constantPool.size}")

        if (!verbose) return

        // Fields
        if (cf.fields.isNotEmpty()) {
            println()
            println("Fields:")
            for (f in cf.fields) {
                val acc = AccessFlags.toString(f.accessFlags, AccessFlags.Context.FIELD)
                println("  $acc ${cf.string(f.descriptorIndex)} ${cf.string(f.nameIndex)}")
            }
        }

        // Methods with descriptors
        if (cf.methods.isNotEmpty()) {
            println()
            println("Methods:")
            for (m in cf.methods) {
                val acc = AccessFlags.toString(m.accessFlags, AccessFlags.Context.METHOD)
                println("  $acc ${cf.string(m.nameIndex)}${cf.string(m.descriptorIndex)}")
            }
        }
    }

    private fun infoArchive(data: ByteArray, verbose: Boolean) {
        val ar = ArchiveReader.read(data)
        println("Format:       Archive (${ar.variant})")
        println("Members:      ${ar.members.size}")
        if (ar.symbols.isNotEmpty())
            println("Symbols:      ${ar.symbols.size}")
        val totalSize = ar.members.sumOf { it.data.size.toLong() }
        println("Total size:   $totalSize bytes")

        if (!verbose) return

        println()
        println("Members:")
        for (m in ar.members) {
            println("  %-30s %10d bytes".format(m.name, m.data.size))
        }

        if (ar.symbols.isNotEmpty()) {
            println()
            println("Symbol table (${ar.symbols.size}):")
            for (sym in ar.symbols.take(50)) {
                val memberName = ar.members.firstOrNull { it.fileOffset.toLong() == sym.memberOffset }?.name ?: "?"
                println("  %-40s in %s".format(sym.name, memberName))
            }
            if (ar.symbols.size > 50) println("  ... and ${ar.symbols.size - 50} more")
        }
    }
}
