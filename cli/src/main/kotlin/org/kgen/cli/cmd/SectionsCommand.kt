package org.kgen.cli.cmd

import org.kgen.binary.elf.ElfReader
import org.kgen.binary.macho.MachOReader
import org.kgen.binary.pe.PeReader
import org.kgen.cli.*

object SectionsCommand {
    fun run(args: List<String>) {
        val parsed = parseArgs(args)
        val path = parsed.requireFile("kgen sections <file>")
        val data = readFileOrExit(path)

        when (detectFormat(data)) {
            BinaryFormat.ELF -> sectionsElf(data)
            BinaryFormat.PE -> sectionsPe(data)
            BinaryFormat.MACHO -> sectionsMachO(data)
            else -> {
                // Fall back to generic inspector
                try {
                    val inspector = org.kgen.tools.Inspectors.forBytes(data)
                    val sections = inspector.sections(data)
                    println("  %-4s %-24s %-10s %-12s %-12s %-8s %-6s".format(
                        "Idx", "Name", "Kind", "Size", "Address", "Offset", "Align"))
                    for (s in sections) {
                        println("  %-4d %-24s %-10s %-12d %s  %s  %d".format(
                            s.index, s.name, s.kind, s.size,
                            hex8(s.address), hex8(s.offset), s.align))
                    }
                } catch (e: Exception) {
                    err("cannot read sections from this format")
                }
            }
        }
    }

    private fun sectionsElf(data: ByteArray) {
        val elf = ElfReader.read(data)
        println("Sections (${elf.sections.size}):")
        println("  %-4s %-24s %-14s %-12s %-12s %-8s %-6s %s".format(
            "Idx", "Name", "Type", "Size", "Address", "Offset", "Align", "Flags"))
        for (s in elf.sections) {
            val flags = buildString {
                if (s.flags and 0x1L != 0L) append('W')
                if (s.flags and 0x2L != 0L) append('A')
                if (s.flags and 0x4L != 0L) append('X')
                if (s.flags and 0x10L != 0L) append('M')
                if (s.flags and 0x20L != 0L) append('S')
                if (s.flags and 0x40L != 0L) append('I')
                if (s.flags and 0x80L != 0L) append('L')
            }
            println("  %-4d %-24s %-14s %08x     %08x     %06x   %-6d %s".format(
                s.index, s.name, s.type ?: "NULL", s.size, s.address, s.offset, s.alignment, flags))
        }
    }

    private fun sectionsPe(data: ByteArray) {
        val pe = PeReader.read(data)
        println("Sections (${pe.sections.size}):")
        println("  %-12s %-12s %-12s %-12s %-12s %s".format(
            "Name", "VirtSize", "VirtAddr", "RawSize", "RawOffset", "Flags"))
        for (s in pe.sections) {
            val name = s.name.trim('\u0000')
            val flags = buildString {
                val ch = s.characteristics
                if (ch and 0x00000020 != 0) append("CODE ")
                if (ch and 0x00000040 != 0) append("IDATA ")
                if (ch and 0x00000080 != 0) append("UDATA ")
                if (ch and 0x20000000 != 0) append("X ")
                if (ch and 0x40000000 != 0) append("R ")
                if (ch and -0x80000000 != 0) append("W ")
            }.trim()
            println("  %-12s %08x     %08x     %08x     %08x     %s".format(
                name, s.virtualSize, s.virtualAddress, s.rawDataSize, s.rawDataOffset, flags))
        }
    }

    private fun sectionsMachO(data: ByteArray) {
        val m = MachOReader.read(data)
        println("Sections (${m.allSections.size}):")
        println("  %-16s %-16s %-12s %-16s %-8s %s".format(
            "Section", "Segment", "Size", "Address", "Align", "Flags"))
        for (s in m.allSections) {
            val flags = buildString {
                if (s.isPureInstructions) append("CODE ")
            }.trim()
            println("  %-16s %-16s %08x     %016x %-8d %s".format(
                s.sectionName, s.segmentName, s.size, s.address, 1 shl s.align, flags))
        }
    }
}
