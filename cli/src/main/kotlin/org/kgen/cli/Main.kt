package org.kgen.cli

import org.kgen.cli.cmd.*
import java.io.File
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        printUsage()
        return
    }

    val command = args[0]
    val rest = args.drop(1)

    when (command) {
        // Inspection
        "info"       -> InfoCommand.run(rest)
        "headers"    -> HeadersCommand.run(rest)
        "sections"   -> SectionsCommand.run(rest)
        "segments"   -> SegmentsCommand.run(rest)
        "symbols"    -> SymbolsCommand.run(rest)
        "imports"    -> ImportsCommand.run(rest)
        "exports"    -> ExportsCommand.run(rest)
        "deps"       -> DepsCommand.run(rest)
        "relocs"     -> RelocsCommand.run(rest)
        "strings"    -> StringsCommand.run(rest)
        "size"       -> SizeCommand.run(rest)

        // Display
        "disasm"     -> DisasmCommand.run(rest)
        "hexdump"    -> HexDumpCommand.run(rest)

        // Manipulation
        "demangle"   -> DemangleCommand.run(rest)
        "diff"       -> DiffCommand.run(rest)
        "patch"      -> PatchCommand.run(rest)
        "strip"      -> StripCommand.run(rest)

        // Compilation
        "compile"    -> CompileCommand.run(rest)

        // Archive
        "ar"         -> ArCommand.run(rest)

        // Format-specific
        "classinfo"  -> ClassInfoCommand.run(rest)
        "clrinfo"    -> ClrInfoCommand.run(rest)
        "wasminfo"   -> WasmInfoCommand.run(rest)

        // Meta
        "version"    -> println("kgen 0.1.0-SNAPSHOT")
        "help"       -> if (rest.isNotEmpty()) printCommandHelp(rest[0]) else printUsage()
        "--help", "-h" -> printUsage()

        else -> {
            err("Unknown command: $command")
            printUsage()
            exitProcess(1)
        }
    }
}

private fun printUsage() {
    println("""kgen - binary toolkit

Usage: kgen <command> [options] [file]

Inspection:
  info <file>              File summary (format, arch, type, entry point)
  headers <file>           Detailed format-specific headers
  sections <file>          List sections with size, address, flags
  segments <file>          List segments / program headers
  symbols <file>           List symbols (-D demangle, -u undefined, -g global)
  imports <file>           List imported functions
  exports <file>           List exported functions
  deps <file>              List shared library dependencies
  relocs <file>            List relocations
  strings <file>           Extract printable strings (-n min-length)
  size <file>              Section size summary

Display:
  disasm <file>            Disassemble code sections
  hexdump <file>           Hex dump (-s section, -o offset, -n length)

Compilation:
  compile <file.ir>        Compile IR to binary (-t target -o output)

Manipulation:
  demangle [names...]      Demangle symbol names (args or stdin)
  diff <old> <new>         Binary diff (structural + byte-level)
  patch <file> [ops...]    Patch binary (--set-rpath, --rename-symbol, ...)
  strip <file>             Strip debug info and local symbols

Archive:
  ar list|extract|create   Archive operations

Format-specific:
  classinfo <file.class>   JVM class file inspection (like javap -v -p)
  clrinfo <file.dll>       .NET CLR metadata inspection
  wasminfo <file.wasm>     WASM module inspection

Meta:
  version                  Show version
  help [command]           Show help""")
}

private fun printCommandHelp(command: String) {
    when (command) {
        "symbols" -> println("""kgen symbols <file> [options]

Options:
  -D, --demangle         Demangle C++/Rust/MSVC symbol names
  -u, --undefined        Show only undefined (imported) symbols
  -d, --defined          Show only defined symbols
  -g, --global           Show only global symbols
  -S, --sort value       Sort by value (address)
  -n, --sort name        Sort by name""")

        "strings" -> println("""kgen strings <file> [options]

Options:
  -n, --min-length N     Minimum string length (default: 4)
  -s, --section NAME     Search only in named section""")

        "disasm" -> println("""kgen disasm <file> [options]

Options:
  -s, --section NAME     Disassemble specific section
  --symbol NAME          Disassemble specific symbol
  --start ADDR           Start address (hex)
  --end ADDR             End address (hex)
  --syntax att|intel     Assembly syntax (default: intel)
  -b, --bytes            Show instruction bytes
  -F, --functions        Group output by function (ELF)""")

        "hexdump" -> println("""kgen hexdump <file> [options]

Options:
  -s, --section NAME     Dump specific section
  -o, --offset N         Start offset in bytes
  -n, --length N         Number of bytes to dump""")

        "patch" -> println("""kgen patch <file> [operations...] -o <output>

Operations:
  --set-rpath PATH           Set RPATH (colon-separated)
  --add-needed LIB           Add shared library dependency
  --remove-needed LIB        Remove shared library dependency
  --replace-needed OLD NEW   Replace dependency name
  --rename-symbol OLD NEW    Rename symbol
  --strip-section NAME       Remove section
  --set-entry ADDR           Set entry point address (hex)

Options:
  -o, --output FILE          Output file (required)""")

        "diff" -> println("""kgen diff <old-file> <new-file> [options]

Options:
  --structural           Show only structural changes (no byte diffs)
  --bytes                Show only byte-level changes""")

        "classinfo" -> println("""kgen classinfo <file.class> [options]

Options:
  -c, --code             Show bytecode disassembly
  -p, --private          Show all members (including private)
  --cp                   Show constant pool
  --attrs                Show all attributes""")

        "clrinfo" -> println("""kgen clrinfo <file.dll|file.exe> [options]

Options:
  --types                Show type definitions
  --methods              Show method definitions with IL
  --refs                 Show assembly/type/member references
  --tables               Show all metadata tables
  --all                  Show everything""")

        "wasminfo" -> println("""kgen wasminfo <file.wasm> [options]

Options:
  --sections             Show all sections
  --imports              Show imports
  --exports              Show exports
  --functions            Show function signatures
  -c, --code             Disassemble function bodies
  --all                  Show everything""")

        "compile" -> println("""kgen compile <file.ir> [options]

Compiles kgen IR (text or binary) to a target binary.

Options:
  -t, --target TARGET    Target: wasm, x86_64, aarch64, riscv
  -o, --output FILE      Output file (required)
  -O, --opt LEVEL        Optimization: 0 (default), 1, 2, 3, s, z""")

        "info" -> println("""kgen info <file> [options]

Options:
  -v, --verbose          Show detailed info (sections, symbols, disassembly)""")

        "ar" -> println("""kgen ar <subcommand> [args...]

Subcommands:
  list <archive>              List archive members
  extract <archive> [dir]     Extract members to directory
  create <output> <files...>  Create archive from files""")

        "demangle" -> println("""kgen demangle [options] [names...]

Reads from arguments or stdin (one name per line).

Options:
  --format itanium|msvc|rust  Force mangling scheme (default: auto)""")

        "strip" -> println("""kgen strip <file> [options]

Options:
  -o, --output FILE      Output file (default: overwrite input)
  --keep-symbols         Keep global symbols, strip only debug
  --strip-all            Strip everything (symbols + debug)""")

        else -> {
            err("Unknown command: $command")
            printUsage()
        }
    }
}
