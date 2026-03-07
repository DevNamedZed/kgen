package org.kgen.cli.cmd

import org.kgen.tools.*
import org.kgen.cli.*

object DemangleCommand {
    fun run(args: List<String>) {
        val parsed = parseArgs(args)
        val format = parsed.get("format")

        val demangler: Demangler = when (format) {
            "itanium" -> ItaniumDemangler()
            "msvc" -> MsvcDemangler()
            "rust" -> RustDemangler()
            else -> UniversalDemangler()
        }

        if (parsed.positional.isNotEmpty()) {
            // Demangle from arguments
            for (name in parsed.positional) {
                val result = demangler.demangle(name)
                println(result ?: name)
            }
        } else {
            // Demangle from stdin
            val reader = System.`in`.bufferedReader()
            var line = reader.readLine()
            while (line != null) {
                val trimmed = line.trim()
                if (trimmed.isNotEmpty()) {
                    val result = demangler.demangle(trimmed)
                    println(result ?: trimmed)
                }
                line = reader.readLine()
            }
        }
    }
}
