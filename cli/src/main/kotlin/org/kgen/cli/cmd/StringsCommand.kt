package org.kgen.cli.cmd

import org.kgen.binary.inspect.Inspectors
import org.kgen.cli.*

object StringsCommand {
    fun run(args: List<String>) {
        val parsed = parseArgs(args)
        val path = parsed.requireFile("kgen strings <file>")
        val data = readFileOrExit(path)
        val minLen = parsed.get("n", "min-length")?.toIntOrNull() ?: 4
        val section = parsed.get("s", "section")

        try {
            val inspector = Inspectors.forBytes(data)
            val strings = inspector.strings(data, minLen)
            val filtered = if (section != null) {
                strings.filter { it.section == section }
            } else {
                strings
            }

            for (s in filtered) {
                val secLabel = if (s.section != null) "[${s.section}] " else ""
                println("  %08x  %s%s".format(s.offset, secLabel, s.value))
            }
            println()
            println("${filtered.size} strings found (min length: $minLen)")
        } catch (e: Exception) {
            // Fall back to raw string extraction
            extractRawStrings(data, minLen)
        }
    }

    private fun extractRawStrings(data: ByteArray, minLen: Int) {
        val sb = StringBuilder()
        var start = -1
        for (i in data.indices) {
            val b = data[i].toInt() and 0xFF
            if (b in 0x20..0x7E) {
                if (start == -1) start = i
                sb.append(b.toChar())
            } else {
                if (sb.length >= minLen) {
                    println("  %08x  %s".format(start, sb))
                }
                sb.clear()
                start = -1
            }
        }
        if (sb.length >= minLen) {
            println("  %08x  %s".format(start, sb))
        }
    }
}
