package org.kgen.cli.cmd

import org.kgen.binary.elf.ElfReader
import org.kgen.binary.macho.MachOReader
import org.kgen.binary.pe.PeReader
import org.kgen.cli.*

object RelocsCommand {
    fun run(args: List<String>) {
        val parsed = parseArgs(args)
        val path = parsed.requireFile("kgen relocs <file>")
        val data = readFileOrExit(path)

        when (detectFormat(data)) {
            BinaryFormat.ELF -> relocsElf(data)
            BinaryFormat.PE -> relocsPe(data)
            BinaryFormat.MACHO -> relocsMachO(data)
            else -> err("relocs command requires ELF, PE, or Mach-O file")
        }
    }

    private fun relocsElf(data: ByteArray) {
        val elf = ElfReader.read(data)
        if (elf.relocations.isEmpty()) {
            println("No relocations")
            return
        }

        // Group by section
        val bySec = elf.relocations.groupBy { it.sectionName ?: "?" }
        for ((sec, relocs) in bySec) {
            println("Relocations in $sec (${relocs.size}):")
            println("  %-16s %-8s %-30s %s".format("Offset", "Type", "Symbol", "Addend"))
            for (r in relocs) {
                println("  %016x %-8d %-30s %d".format(
                    r.offset, r.type, r.symbolName ?: "<none>", r.addend))
            }
            println()
        }
    }

    private fun relocsPe(data: ByteArray) {
        val pe = PeReader.read(data)
        if (pe.baseRelocations.isEmpty()) {
            println("No base relocations")
            return
        }
        println("Base Relocations (${pe.baseRelocations.size} blocks):")
        for (block in pe.baseRelocations) {
            println("  Page RVA: %08x  (%d entries)".format(block.pageRVA, block.entries.size))
            for (entry in block.entries) {
                if (entry.type == 0) continue // IMAGE_REL_BASED_ABSOLUTE (padding)
                println("    %08x  type=%d".format(
                    block.pageRVA + entry.offset, entry.type))
            }
        }
    }

    private fun relocsMachO(data: ByteArray) {
        val m = MachOReader.read(data)
        var found = false
        for (sec in m.allSections) {
            if (sec.relocations.isEmpty()) continue
            found = true
            println("Relocations in ${sec.segmentName},${sec.sectionName} (${sec.relocations.size}):")
            println("  %-12s %-6s %-4s %-6s %-8s".format("Address", "PCRel", "Len", "Ext", "Type"))
            for (r in sec.relocations) {
                val symName = if (r.extern && r.symbolIndex < m.symbols.size)
                    m.symbols[r.symbolIndex].name else "sect#${r.symbolIndex}"
                println("  %08x     %-6s %-4d %-6s %-8d  %s".format(
                    r.address, r.pcRelative, 1 shl r.length, r.extern, r.type, symName))
            }
            println()
        }
        if (!found) println("No relocations")
    }
}
