package org.kgen.cli.cmd

import org.kgen.binary.elf.ElfReader
import org.kgen.binary.elf.ElfSymbolType
import org.kgen.binary.macho.MachO
import org.kgen.binary.macho.MachOReader
import org.kgen.binary.pe.PeReader
import org.kgen.target.wasm.disasm.WasmDisassembler
import org.kgen.target.wasm.module.WasmModuleReader
import org.kgen.cli.*

object DisasmCommand {
    fun run(args: List<String>) {
        val parsed = parseArgs(args)
        val path = parsed.requireFile("kgen disasm <file>")
        val data = readFileOrExit(path)
        val sectionName = parsed.get("s", "section")
        val symbolName = parsed.get("symbol")
        val startAddr = parsed.get("start")?.removePrefix("0x")?.toLongOrNull(16)
        val endAddr = parsed.get("end")?.removePrefix("0x")?.toLongOrNull(16)
        val att = parsed.get("syntax")?.lowercase() == "att"
        val showBytes = parsed.has("b", "bytes")
        val byFunction = parsed.has("F", "functions")

        when (detectFormat(data)) {
            BinaryFormat.ELF -> disasmElf(data, sectionName, symbolName, startAddr, endAddr, att, showBytes, byFunction)
            BinaryFormat.PE -> disasmPe(data, sectionName, att, showBytes, byFunction)
            BinaryFormat.MACHO -> disasmMachO(data, sectionName, att, showBytes, byFunction)
            BinaryFormat.WASM -> disasmWasm(data)
            BinaryFormat.CLASS -> {
                err("use 'kgen classinfo -c <file>' for JVM bytecode disassembly")
            }
            else -> err("cannot disassemble this format")
        }
    }

    private fun disasmElf(data: ByteArray, sectionName: String?, symbolName: String?,
                           startAddr: Long?, endAddr: Long?, att: Boolean, showBytes: Boolean, byFunction: Boolean) {
        val elf = ElfReader.read(data)
        val arch = elfArch(elf.header.machine)

        if (symbolName != null) {
            val sym = elf.symbols.firstOrNull { it.name == symbolName }
                ?: elf.dynamicSymbols.firstOrNull { it.name == symbolName }
            if (sym == null) {
                err("symbol not found: $symbolName")
                return
            }
            val section = elf.sections.firstOrNull { it.name == sym.sectionName }
            if (section == null || section.data.isEmpty()) {
                err("no code for symbol: $symbolName")
                return
            }
            val offset = (sym.value - section.address).toInt()
            val size = if (sym.size > 0) sym.size.toInt() else section.data.size - offset
            val code = section.data.copyOfRange(offset, minOf(offset + size, section.data.size))
            println("<$symbolName>:")
            disassemble(arch, code, sym.value, att, showBytes)
            return
        }

        val sectionsToDisasm = if (sectionName != null) {
            val sec = elf.sections.firstOrNull { it.name == sectionName }
            if (sec == null) {
                err("section not found: $sectionName")
                return
            }
            listOf(sec)
        } else {
            elf.sections.filter { it.flags and 0x4 != 0L && it.data.isNotEmpty() }
        }

        for (sec in sectionsToDisasm) {
            var code = sec.data
            var base = sec.address

            if (startAddr != null || endAddr != null) {
                val secStart = sec.address
                val secEnd = sec.address + sec.data.size
                val from = maxOf(startAddr ?: secStart, secStart)
                val to = minOf(endAddr ?: secEnd, secEnd)
                if (from >= to) continue
                code = sec.data.copyOfRange((from - secStart).toInt(), (to - secStart).toInt())
                base = from
            }

            if (byFunction) {
                disasmByFunction(sec.name, arch, code, base, elf.symbols, att, showBytes)
            } else {
                println("Disassembly of ${sec.name}:")
                disassemble(arch, code, base, att, showBytes)
                println()
            }
        }
    }

    private fun disasmByFunction(sectionName: String, arch: String, code: ByteArray, base: Long,
                                  symbols: List<org.kgen.binary.elf.ElfSymbolEntry>,
                                  att: Boolean, showBytes: Boolean) {
        val funcSyms = symbols
            .filter { it.type == ElfSymbolType.FUNC && it.sectionName == sectionName && it.size > 0 }
            .sortedBy { it.value }

        if (funcSyms.isEmpty()) {
            println("Disassembly of $sectionName:")
            disassemble(arch, code, base, att, showBytes)
            println()
            return
        }

        for (sym in funcSyms) {
            val offset = (sym.value - base).toInt()
            if (offset < 0 || offset >= code.size) continue
            val size = minOf(sym.size.toInt(), code.size - offset)
            val funcCode = code.copyOfRange(offset, offset + size)
            println()
            println("${hex(sym.value)} <${sym.name}>:")
            disassemble(arch, funcCode, sym.value, att, showBytes)
        }
    }

    private fun disasmPe(data: ByteArray, sectionName: String?, att: Boolean, showBytes: Boolean, byFunction: Boolean) {
        val pe = PeReader.read(data)
        val sections = if (sectionName != null) {
            val sec = pe.sections.firstOrNull { it.name.trim('\u0000') == sectionName }
            if (sec == null) {
                err("section not found: $sectionName")
                return
            }
            listOf(sec)
        } else {
            pe.sections.filter {
                it.characteristics and 0x20000000 != 0 && it.data.isNotEmpty()
            }
        }

        for (sec in sections) {
            println("Disassembly of ${sec.name.trim('\u0000')}:")
            disassemble("x86_64", sec.data, sec.virtualAddress.toLong(), att, showBytes)
            println()
        }
    }

    private fun disasmMachO(data: ByteArray, sectionName: String?, att: Boolean, showBytes: Boolean, byFunction: Boolean) {
        val m = MachOReader.read(data)
        val arch = machoArch(m.header.cpuType)

        val sections = if (sectionName != null) {
            val sec = m.allSections.firstOrNull { it.sectionName == sectionName }
            if (sec == null) {
                err("section not found: $sectionName")
                return
            }
            listOf(sec)
        } else {
            m.allSections.filter { it.isPureInstructions && it.data.isNotEmpty() }
        }

        for (sec in sections) {
            println("Disassembly of ${sec.segmentName},${sec.sectionName}:")
            disassemble(arch, sec.data, sec.address, att, showBytes)
            println()
        }
    }

    private fun disasmWasm(data: ByteArray) {
        val m = WasmModuleReader.read(data)
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
                val mnemonic = inst.opcode.mnemonic
                if (mnemonic == "end" || mnemonic == "else") indent = maxOf(1, indent - 1)
                val prefix = "  ".repeat(indent)
                println("  %04x: %s%s".format(inst.offset, prefix, inst.text()))
                if (mnemonic == "block" || mnemonic == "loop" || mnemonic == "if" || mnemonic == "else") indent++
            }
            println()
        }
    }
}
