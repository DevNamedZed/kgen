package org.kgen.cli.cmd

import org.kgen.binary.inspect.Inspectors
import org.kgen.cli.*

object SizeCommand {
    fun run(args: List<String>) {
        val parsed = parseArgs(args)
        val path = parsed.requireFile("kgen size <file>")
        val data = readFileOrExit(path)

        try {
            val inspector = Inspectors.forBytes(data)
            val sections = inspector.sections(data)

            var textSize = 0L
            var dataSize = 0L
            var bssSize = 0L
            var otherSize = 0L

            println("  %-24s %12s  %s".format("Section", "Size", "Kind"))
            for (s in sections) {
                println("  %-24s %12d  %s".format(s.name, s.size, s.kind))
                when {
                    s.kind.toString().contains("CODE", ignoreCase = true) ||
                    s.name == ".text" || s.name == "__text" -> textSize += s.size
                    s.kind.toString().contains("BSS", ignoreCase = true) ||
                    s.name == ".bss" -> bssSize += s.size
                    s.kind.toString().contains("DATA", ignoreCase = true) ||
                    s.name in listOf(".data", ".rodata", ".rdata", "__data", "__const") -> dataSize += s.size
                    else -> otherSize += s.size
                }
            }

            println()
            println("  %-24s %12d".format("text (code)", textSize))
            println("  %-24s %12d".format("data", dataSize))
            println("  %-24s %12d".format("bss", bssSize))
            println("  %-24s %12d".format("other", otherSize))
            println("  %-24s %12d".format("total", textSize + dataSize + bssSize + otherSize))
        } catch (e: Exception) {
            err("cannot read sections from this format")
        }
    }
}
