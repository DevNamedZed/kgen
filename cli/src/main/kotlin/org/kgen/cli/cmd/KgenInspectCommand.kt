package org.kgen.cli.cmd

import org.kgen.cli.*
import org.kgen.binary.elf.ElfReader
import org.kgen.binary.pe.PeReader
import org.kgen.runtime.compile.ExecutableReader

object KgenInspectCommand {
    fun run(args: List<String>) {
        val parsed = parseArgs(args)
        val path = parsed.requireFile("kgen inspect <executable>")
        val data = readFileOrExit(path)

        val format = detectFormat(data)

        val metaSection: ByteArray?
        val typesSection: ByteArray?
        val resourcesSection: ByteArray?

        when (format) {
            BinaryFormat.ELF -> {
                val elf = ElfReader.read(data)
                metaSection = elf.sectionByName(".kgen.meta")?.data
                typesSection = elf.sectionByName(".kgen.types")?.data
                resourcesSection = elf.sectionByName(".kgen.resources")?.data
            }
            BinaryFormat.PE -> {
                val pe = PeReader.read(data)
                metaSection = pe.sections.firstOrNull { it.name == ".kgen.me" }?.data
                typesSection = pe.sections.firstOrNull { it.name == ".kgen.ty" }?.data
                resourcesSection = pe.sections.firstOrNull { it.name == ".kgen.re" }?.data
            }
            else -> {
                err("unsupported format for inspect (expected ELF or PE)")
                return
            }
        }

        if (metaSection == null) {
            err("no .kgen.meta section found — not a kgen executable")
            return
        }

        val meta = try {
            ExecutableReader.read(metaSection, typesSection, resourcesSection)
        } catch (e: Exception) {
            err("failed to read kgen metadata: ${e.message}")
            return
        }

        println("kgen executable: $path")
        println()
        println("Module:  ${meta.moduleName}")
        println("Version: ${meta.version}")
        println("Target:  ${meta.targetTriple}")
        println()

        if (meta.exports.isNotEmpty()) {
            println("Exports (${meta.exports.size}):")
            for (exp in meta.exports) {
                println("  $exp")
            }
            println()
        }

        if (meta.types.isNotEmpty()) {
            println("Function signatures:")
            for (fn in meta.types) {
                val params = fn.params.joinToString(", ") { "${it.second} ${it.first}" }
                println("  ${fn.returnType} ${fn.name}($params)")
            }
            println()
        }

        if (meta.metadata.isNotEmpty()) {
            println("Metadata:")
            for ((key, value) in meta.metadata) {
                println("  $key = $value")
            }
            println()
        }

        if (meta.resources.isNotEmpty()) {
            println("Resources (${meta.resources.size}):")
            for ((name, data) in meta.resources) {
                println("  $name (${data.size} bytes)")
            }
        }
    }
}
