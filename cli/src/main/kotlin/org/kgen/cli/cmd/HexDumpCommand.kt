package org.kgen.cli.cmd

import org.kgen.binary.elf.ElfReader
import org.kgen.binary.macho.MachOReader
import org.kgen.binary.pe.PeReader
import org.kgen.binary.HexDump
import org.kgen.cli.*

object HexDumpCommand {
    fun run(args: List<String>) {
        val parsed = parseArgs(args)
        val path = parsed.requireFile("kgen hexdump <file>")
        val data = readFileOrExit(path)
        val sectionName = parsed.get("s", "section")
        val offset = parsed.get("o", "offset")?.toIntOrNull() ?: 0
        val length = parsed.get("n", "length")?.toIntOrNull()

        val (dumpData, baseAddr) = if (sectionName != null) {
            findSection(data, sectionName)
        } else {
            data to 0L
        }

        val start = offset.coerceAtMost(dumpData.size)
        val end = if (length != null) (start + length).coerceAtMost(dumpData.size) else dumpData.size
        val slice = dumpData.copyOfRange(start, end)

        println(HexDump.format(slice, baseAddress = baseAddr + start))
    }

    private fun findSection(data: ByteArray, name: String): Pair<ByteArray, Long> {
        return when (detectFormat(data)) {
            BinaryFormat.ELF -> {
                val elf = ElfReader.read(data)
                val sec = elf.sections.firstOrNull { it.name == name }
                    ?: run { err("section not found: $name"); return data to 0L }
                sec.data to sec.address
            }
            BinaryFormat.PE -> {
                val pe = PeReader.read(data)
                val sec = pe.sections.firstOrNull { it.name.trim('\u0000') == name }
                    ?: run { err("section not found: $name"); return data to 0L }
                sec.data to sec.virtualAddress.toLong()
            }
            BinaryFormat.MACHO -> {
                val m = MachOReader.read(data)
                val sec = m.allSections.firstOrNull { it.sectionName == name }
                    ?: run { err("section not found: $name"); return data to 0L }
                sec.data to sec.address
            }
            else -> data to 0L
        }
    }
}
