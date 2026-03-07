package org.kgen.cli.cmd

import org.kgen.binary.ar.ArchiveReader
import org.kgen.binary.ar.ArchiveWriter
import org.kgen.binary.ar.ArchiveMember
import org.kgen.cli.*
import java.io.File
import java.time.Instant

object ArCommand {
    fun run(args: List<String>) {
        if (args.isEmpty()) {
            err("missing subcommand")
            System.err.println("Usage: kgen ar list|extract|create ...")
            return
        }
        when (args[0]) {
            "list", "t" -> list(args.drop(1))
            "extract", "x" -> extract(args.drop(1))
            "create", "r" -> create(args.drop(1))
            else -> err("unknown ar subcommand: ${args[0]}")
        }
    }

    private fun list(args: List<String>) {
        if (args.isEmpty()) {
            err("missing archive file")
            return
        }
        val data = readFileOrExit(args[0])
        val ar = ArchiveReader.read(data)

        println("Archive: ${args[0]} (${ar.variant})")
        println("  %-30s %10s  %s".format("Name", "Size", "Modified"))
        for (m in ar.members) {
            val time = try {
                Instant.ofEpochSecond(m.modificationTime).toString()
            } catch (_: Exception) { "${m.modificationTime}" }
            println("  %-30s %10d  %s".format(m.name, m.data.size, time))
        }

        if (ar.symbols.isNotEmpty()) {
            println()
            println("Symbol table (${ar.symbols.size} symbols):")
            for (sym in ar.symbols) {
                val memberName = ar.members.firstOrNull { it.fileOffset.toLong() == sym.memberOffset }?.name ?: "?"
                println("  %-40s in %s".format(sym.name, memberName))
            }
        }
    }

    private fun extract(args: List<String>) {
        if (args.isEmpty()) {
            err("missing archive file")
            return
        }
        val data = readFileOrExit(args[0])
        val ar = ArchiveReader.read(data)
        val outDir = if (args.size > 1) args[1] else "."

        File(outDir).mkdirs()
        for (m in ar.members) {
            val outFile = File(outDir, m.name)
            outFile.writeBytes(m.data)
            println("  x ${m.name} (${m.data.size} bytes)")
        }
        println("Extracted ${ar.members.size} members to $outDir")
    }

    private fun create(args: List<String>) {
        if (args.size < 2) {
            err("usage: kgen ar create <output> <files...>")
            return
        }
        val output = args[0]
        val files = args.drop(1)

        val members = files.map { path ->
            val file = File(path)
            if (!file.exists()) {
                err("file not found: $path")
                return
            }
            ArchiveMember(
                name = file.name,
                modificationTime = file.lastModified() / 1000,
                ownerId = 0,
                groupId = 0,
                mode = 0b110_100_100, // rw-r--r--
                data = file.readBytes(),
            )
        }

        val result = ArchiveWriter().write(members)
        File(output).writeBytes(result)
        println("Created $output (${files.size} members, ${result.size} bytes)")
    }
}
