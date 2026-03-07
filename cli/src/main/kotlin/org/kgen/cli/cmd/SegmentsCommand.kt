package org.kgen.cli.cmd

import org.kgen.binary.elf.ElfReader
import org.kgen.binary.macho.MachOReader
import org.kgen.cli.*

object SegmentsCommand {
    fun run(args: List<String>) {
        val parsed = parseArgs(args)
        val path = parsed.requireFile("kgen segments <file>")
        val data = readFileOrExit(path)

        when (detectFormat(data)) {
            BinaryFormat.ELF -> segmentsElf(data)
            BinaryFormat.MACHO -> segmentsMachO(data)
            else -> err("segments command requires ELF or Mach-O file")
        }
    }

    private fun segmentsElf(data: ByteArray) {
        val elf = ElfReader.read(data)
        if (elf.segments.isEmpty()) {
            println("No program headers (relocatable object)")
            return
        }
        println("Program Headers (${elf.segments.size}):")
        println("  %-14s %-8s %-16s %-16s %-12s %-12s %-6s %s".format(
            "Type", "Offset", "VirtAddr", "PhysAddr", "FileSize", "MemSize", "Align", "Flags"))
        for (s in elf.segments) {
            val flags = buildString {
                if (s.flags and 4 != 0) append('R')
                if (s.flags and 2 != 0) append('W')
                if (s.flags and 1 != 0) append('E')
            }
            println("  %-14s %06x   %016x %016x %08x     %08x     %-6d %s".format(
                s.type, s.offset, s.virtualAddress, s.physicalAddress,
                s.fileSize, s.memorySize, s.alignment, flags))
        }
    }

    private fun segmentsMachO(data: ByteArray) {
        val m = MachOReader.read(data)
        println("Segments (${m.segments.size}):")
        for (seg in m.segments) {
            val prot = buildString {
                if (seg.initProtection and 1 != 0) append('R')
                if (seg.initProtection and 2 != 0) append('W')
                if (seg.initProtection and 4 != 0) append('X')
            }
            println("  %-16s vmaddr=%016x  vmsize=%08x  fileoff=%08x  filesz=%08x  prot=%s".format(
                seg.name, seg.vmAddress, seg.vmSize, seg.fileOffset, seg.fileSize, prot))
            for (sec in seg.sections) {
                println("    %-16s addr=%016x  size=%08x  offset=%08x".format(
                    sec.sectionName, sec.address, sec.size, sec.offset))
            }
        }
    }
}
