package org.kgen.cli.cmd

import org.kgen.binary.elf.ElfReader
import org.kgen.binary.macho.MachOReader
import org.kgen.binary.pe.PeReader
import org.kgen.cli.*

object HeadersCommand {
    fun run(args: List<String>) {
        val parsed = parseArgs(args)
        val path = parsed.requireFile("kgen headers <file>")
        val data = readFileOrExit(path)

        when (detectFormat(data)) {
            BinaryFormat.ELF -> headersElf(data)
            BinaryFormat.PE -> headersPe(data)
            BinaryFormat.MACHO -> headersMachO(data)
            else -> err("headers command requires ELF, PE, or Mach-O file")
        }
    }

    private fun headersElf(data: ByteArray) {
        val elf = ElfReader.read(data)
        val h = elf.header
        println("ELF Header:")
        println("  Class:                             ${h.elfClass}")
        println("  Data:                              ${h.dataEncoding}")
        println("  OS/ABI:                            ${h.osAbi}")
        println("  Type:                              ${h.type}")
        println("  Machine:                           ${h.machine}")
        println("  Entry point address:               ${hex(h.entryPoint)}")
        println("  Start of program headers:          ${h.programHeaderOffset}")
        println("  Start of section headers:          ${h.sectionHeaderOffset}")
        println("  Flags:                             ${hex(h.flags.toLong())}")
        println("  Number of program headers:         ${h.programHeaderCount}")
        println("  Number of section headers:         ${h.sectionHeaderCount}")
        println("  Section header string table index: ${h.sectionNameStringTableIndex}")
    }

    private fun headersPe(data: ByteArray) {
        val pe = PeReader.read(data)
        val ch = pe.coffHeader
        println("COFF Header:")
        println("  Machine:                ${hex(ch.machine.toLong())}")
        println("  Number of Sections:     ${ch.numberOfSections}")
        println("  Timestamp:              ${ch.timestamp}")
        println("  Symbol Table Offset:    ${hex(ch.symbolTableOffset.toLong())}")
        println("  Number of Symbols:      ${ch.numberOfSymbols}")
        println("  Optional Header Size:   ${ch.optionalHeaderSize}")
        println("  Characteristics:        ${hex(ch.characteristics.toLong())}")

        val oh = pe.optionalHeader
        if (oh != null) {
            println()
            println("Optional Header:")
            println("  Magic:                  ${hex(oh.magic.toLong())} (${if (oh.magic == 0x20B) "PE32+" else "PE32"})")
            println("  Linker Version:         ${oh.linkerVersionMajor}.${oh.linkerVersionMinor}")
            println("  Size of Code:           ${oh.sizeOfCode}")
            println("  Entry Point:            ${hex(oh.entryPointRVA.toLong())}")
            println("  Image Base:             ${hex(oh.imageBase)}")
            println("  Section Alignment:      ${oh.sectionAlignment}")
            println("  File Alignment:         ${oh.fileAlignment}")
            println("  OS Version:             ${oh.osVersionMajor}.${oh.osVersionMinor}")
            println("  Image Version:          ${oh.imageVersionMajor}.${oh.imageVersionMinor}")
            println("  Subsystem Version:      ${oh.subsystemVersionMajor}.${oh.subsystemVersionMinor}")
            println("  Size of Image:          ${oh.sizeOfImage}")
            println("  Size of Headers:        ${oh.sizeOfHeaders}")
            println("  Checksum:               ${hex(oh.checksum.toLong())}")
            println("  Subsystem:              ${oh.subsystem}")
            println("  DLL Characteristics:    ${hex(oh.dllCharacteristics.toLong())}")
            println("  Stack Reserve:          ${oh.sizeOfStackReserve}")
            println("  Stack Commit:           ${oh.sizeOfStackCommit}")
            println("  Heap Reserve:           ${oh.sizeOfHeapReserve}")
            println("  Heap Commit:            ${oh.sizeOfHeapCommit}")
            println("  Data Directories:       ${pe.dataDirectories.size}")
        }

        if (pe.dataDirectories.isNotEmpty()) {
            println()
            println("Data Directories:")
            val names = listOf(
                "Export", "Import", "Resource", "Exception",
                "Certificate", "Base Relocation", "Debug", "Architecture",
                "Global Pointer", "TLS", "Load Config", "Bound Import",
                "IAT", "Delay Import", "CLR Runtime", "Reserved",
            )
            for ((i, dd) in pe.dataDirectories.withIndex()) {
                if (dd.rva == 0 && dd.size == 0) continue
                val name = names.getOrElse(i) { "Directory $i" }
                println("  %-20s RVA=%08x  Size=%08x".format(name, dd.rva, dd.size))
            }
        }
    }

    private fun headersMachO(data: ByteArray) {
        val m = MachOReader.read(data)
        val h = m.header
        println("Mach-O Header:")
        println("  Magic:           ${hex(h.magic.toLong())}")
        println("  CPU Type:        ${machoArch(h.cpuType)} (${hex(h.cpuType.toLong())})")
        println("  CPU Subtype:     ${hex(h.cpuSubtype.toLong())}")
        println("  File Type:       ${h.fileType}")
        println("  Load Commands:   ${h.numberOfCommands}")
        println("  Commands Size:   ${h.sizeOfCommands}")
        println("  Flags:           ${hex(h.flags.toLong())}")
        println("  64-bit:          ${h.is64Bit}")

        println()
        println("Load Commands:")
        for (seg in m.segments) {
            println("  LC_SEGMENT_64: ${seg.name}")
            println("    VM Address:  ${hex(seg.vmAddress)}")
            println("    VM Size:     ${hex(seg.vmSize)}")
            println("    File Offset: ${seg.fileOffset}")
            println("    File Size:   ${seg.fileSize}")
            println("    Max Prot:    ${hex(seg.maxProtection.toLong())}")
            println("    Init Prot:   ${hex(seg.initProtection.toLong())}")
            println("    Sections:    ${seg.sections.size}")
        }
    }
}
