package org.kgen.cli.cmd

import org.kgen.binary.elf.ElfReader
import org.kgen.binary.macho.MachOReader
import org.kgen.binary.pe.PeReader
import org.kgen.cli.*

object DepsCommand {
    fun run(args: List<String>) {
        val parsed = parseArgs(args)
        val path = parsed.requireFile("kgen deps <file>")
        val data = readFileOrExit(path)

        when (detectFormat(data)) {
            BinaryFormat.ELF -> depsElf(data)
            BinaryFormat.PE -> depsPe(data)
            BinaryFormat.MACHO -> depsMachO(data)
            else -> err("deps command requires ELF, PE, or Mach-O file")
        }
    }

    private fun depsElf(data: ByteArray) {
        val elf = ElfReader.read(data)
        val dyn = elf.dynamicInfo
        if (dyn == null) {
            println("No dynamic section (static binary or relocatable object)")
            return
        }

        if (dyn.neededLibraries.isNotEmpty()) {
            println("Dependencies:")
            for (lib in dyn.neededLibraries) {
                println("  $lib")
            }
        } else {
            println("No shared library dependencies")
        }

        if (dyn.rpath.isNotEmpty()) {
            println()
            println("RPATH:")
            for (p in dyn.rpath) println("  $p")
        }
        if (dyn.runpath.isNotEmpty()) {
            println()
            println("RUNPATH:")
            for (p in dyn.runpath) println("  $p")
        }
        dyn.soName?.let {
            println()
            println("SONAME: $it")
        }
    }

    private fun depsPe(data: ByteArray) {
        val pe = PeReader.read(data)
        val dlls = pe.importDirectories.map { it.name }.distinct()
        val delayDlls = pe.delayImportDirectories.map { it.name }.distinct()

        if (dlls.isEmpty() && delayDlls.isEmpty()) {
            println("No DLL dependencies")
            return
        }

        if (dlls.isNotEmpty()) {
            println("Dependencies:")
            for (dll in dlls.sorted()) {
                val count = pe.importDirectories.first { it.name == dll }.entries.size
                println("  %-30s (%d imports)".format(dll, count))
            }
        }

        if (delayDlls.isNotEmpty()) {
            println()
            println("Delay-loaded:")
            for (dll in delayDlls.sorted()) {
                println("  $dll")
            }
        }
    }

    private fun depsMachO(data: ByteArray) {
        val m = MachOReader.read(data)
        if (m.dylibs.isEmpty()) {
            println("No dylib dependencies")
            return
        }
        println("Dependencies:")
        for (lib in m.dylibs) {
            println("  $lib")
        }
    }
}
