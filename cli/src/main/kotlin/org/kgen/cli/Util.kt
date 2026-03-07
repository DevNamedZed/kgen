package org.kgen.cli

import org.kgen.binary.elf.ElfReader
import org.kgen.binary.macho.MachO
import org.kgen.binary.macho.MachOReader
import org.kgen.binary.pe.PeReader
import org.kgen.backend.arm64.disasm.Arm64Disassembler
import org.kgen.backend.riscv.disasm.RiscVDisassembler
import org.kgen.backend.x86.disasm.X86Disassembler
import java.io.File
import kotlin.system.exitProcess

fun err(msg: String) = System.err.println("kgen: $msg")

fun readFileOrExit(path: String): ByteArray {
    val file = File(path)
    if (!file.exists()) {
        err("file not found: $path")
        exitProcess(1)
    }
    if (!file.isFile) {
        err("not a file: $path")
        exitProcess(1)
    }
    return file.readBytes()
}

fun parseArgs(args: List<String>): ParsedArgs {
    val flags = mutableSetOf<String>()
    val options = mutableMapOf<String, String>()
    val positional = mutableListOf<String>()

    var i = 0
    while (i < args.size) {
        val arg = args[i]
        when {
            arg == "--" -> {
                positional.addAll(args.subList(i + 1, args.size))
                break
            }
            arg.startsWith("--") && "=" in arg -> {
                val eq = arg.indexOf('=')
                options[arg.substring(2, eq)] = arg.substring(eq + 1)
                i++
            }
            arg.startsWith("--") -> {
                val name = arg.substring(2)
                if (i + 1 < args.size && !args[i + 1].startsWith("-")) {
                    options[name] = args[i + 1]
                    i += 2
                } else {
                    flags.add(name)
                    i++
                }
            }
            arg.startsWith("-") && arg.length == 2 -> {
                val name = arg.substring(1)
                if (i + 1 < args.size && !args[i + 1].startsWith("-")) {
                    options[name] = args[i + 1]
                    i += 2
                } else {
                    flags.add(name)
                    i++
                }
            }
            else -> {
                positional.add(arg)
                i++
            }
        }
    }
    return ParsedArgs(flags, options, positional)
}

data class ParsedArgs(
    val flags: Set<String>,
    val options: Map<String, String>,
    val positional: List<String>,
) {
    fun has(vararg names: String): Boolean = names.any { it in flags || it in options }
    fun get(vararg names: String): String? = names.firstNotNullOfOrNull { options[it] }

    fun file(index: Int = 0): String? {
        if (index < positional.size) return positional[index]
        // Fall back: check if any option value looks like it was a misclassified positional arg
        // This handles cases like "-v /path/to/file" where the file was consumed as -v's value
        val consumed = options.entries.firstOrNull { (_, v) ->
            v.contains('/') || v.contains('\\') || v.contains('.')
        }
        return consumed?.value
    }

    fun requireFile(usage: String, index: Int = 0): String {
        val path = file(index)
        if (path == null) {
            err("missing file argument")
            System.err.println("Usage: $usage")
            exitProcess(1)
        }
        return path
    }
}

enum class BinaryFormat { ELF, PE, MACHO, WASM, CLASS, ARCHIVE, UNKNOWN }

fun detectFormat(data: ByteArray): BinaryFormat = when {
    ElfReader.canRead(data) -> BinaryFormat.ELF
    PeReader.canRead(data) -> BinaryFormat.PE
    MachOReader.canRead(data) -> BinaryFormat.MACHO
    data.size >= 4 && data[0] == 0x00.toByte() && data[1] == 0x61.toByte()
        && data[2] == 0x73.toByte() && data[3] == 0x6D.toByte() -> BinaryFormat.WASM
    data.size >= 4 && data[0] == 0xCA.toByte() && data[1] == 0xFE.toByte()
        && data[2] == 0xBA.toByte() && data[3] == 0xBE.toByte() -> BinaryFormat.CLASS
    data.size >= 8 && String(data, 0, 8) == "!<arch>\n" -> BinaryFormat.ARCHIVE
    else -> BinaryFormat.UNKNOWN
}

fun disassemble(arch: String, code: ByteArray, baseAddr: Long, att: Boolean = false, showBytes: Boolean = false) {
    when (arch) {
        "x86_64", "x86-64", "amd64" -> {
            for (inst in X86Disassembler().disassembleRaw(code, baseAddr)) {
                val addr = "0x${inst.address.toString(16).padStart(8, '0')}"
                val bytes = if (showBytes) "  %-24s".format(inst.bytes.joinToString(" ") { "%02x".format(it) }) else ""
                println("$addr:$bytes  ${inst.text(att)}")
            }
        }
        "arm64", "aarch64" -> {
            for (inst in Arm64Disassembler().disassemble(code, baseAddr)) {
                val addr = "0x${(baseAddr + inst.offset).toString(16).padStart(8, '0')}"
                if (showBytes) {
                    val b = inst.rawBytes
                    val hex = "%02x %02x %02x %02x".format(b and 0xFF, (b shr 8) and 0xFF, (b shr 16) and 0xFF, (b shr 24) and 0xFF)
                    println("$addr:  %-12s  %s".format(hex, inst))
                } else println("$addr:  $inst")
            }
        }
        "riscv", "riscv64" -> {
            for (inst in RiscVDisassembler().disassemble(code, baseAddr)) {
                val addr = "0x${(baseAddr + inst.offset).toString(16).padStart(8, '0')}"
                if (showBytes) {
                    val b = inst.bytes
                    val isCompressed = (b and 0x3) != 0x3
                    val hex = if (isCompressed)
                        "%02x %02x".format(b and 0xFF, (b shr 8) and 0xFF)
                    else "%02x %02x %02x %02x".format(b and 0xFF, (b shr 8) and 0xFF, (b shr 16) and 0xFF, (b shr 24) and 0xFF)
                    println("$addr:  %-12s  %s".format(hex, inst))
                } else println("$addr:  $inst")
            }
        }
        else -> err("unsupported architecture for disassembly: $arch")
    }
}

fun elfArch(machine: org.kgen.binary.elf.ElfMachine?): String = when (machine) {
    org.kgen.binary.elf.ElfMachine.X86_64 -> "x86_64"
    org.kgen.binary.elf.ElfMachine.AARCH64 -> "arm64"
    org.kgen.binary.elf.ElfMachine.RISCV -> "riscv64"
    else -> "unknown"
}

fun machoArch(cpuType: Int): String = when (cpuType) {
    MachO.CPU_TYPE_X86_64 -> "x86_64"
    MachO.CPU_TYPE_ARM64 -> "arm64"
    else -> "unknown"
}

fun hex(v: Long): String = "0x${v.toString(16)}"
fun hex(v: Int): String = "0x${v.toString(16)}"
fun hex8(v: Long): String = "%08x".format(v)
fun hex16(v: Long): String = "%016x".format(v)
