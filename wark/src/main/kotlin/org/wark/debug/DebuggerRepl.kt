package org.wark.debug

import org.kgen.target.wasm.module.WasmModule
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter

/**
 * Interactive REPL shell over [WarkDebugger]. Parses commands,
 * calls API methods, formats output.
 *
 * ```kotlin
 * val debugger = WarkDebugger(path, imports)
 * debugger.load()
 * DebuggerRepl(debugger).run()
 * ```
 */
class DebuggerRepl(
    private val debugger: WarkDebugger,
    private val output: PrintWriter = PrintWriter(System.out, true),
    private val prompt: String = "wark",
) {
    fun run() {
        val info = debugger.moduleInfo()
        output.println("${info.functionCount} functions, ${info.importCount} imports")
        output.println("Memory: ${info.memoryPages} pages (${info.memoryBytes} bytes)")
        output.println("Exports: ${info.exports.joinToString(", ")}")
        if (debugger.jitAvailable) {
            output.println("JIT: compiled ${info.functionCount} functions")
        }
        output.println("Type 'help' for commands.")
        prompt()

        val reader = BufferedReader(InputStreamReader(System.`in`))
        while (true) {
            val line = reader.readLine() ?: break
            val trimmed = line.trim()
            if (trimmed.isEmpty()) { prompt(); continue }
            try {
                if (!dispatch(trimmed)) { break }
            } catch (exception: Exception) {
                output.println("Error: ${exception.javaClass.simpleName}: ${exception.message}")
            }
            prompt()
        }
    }

    protected fun prompt() {
        output.print("$prompt> ")
        output.flush()
    }

    protected open fun dispatch(input: String): Boolean {
        val parts = input.split("\\s+".toRegex())
        val command = parts[0].lowercase()

        when (command) {
            "help", "h" -> printHelp()
            "quit", "q", "exit" -> return false
            "info", "i" -> printInfo()
            "exports" -> printExports()
            "imports" -> printImports()
            "types" -> printTypes()
            "elements" -> printElements()
            "func" -> commandFunc(parts)
            "disasm", "dis" -> commandDisasm(parts)
            "jit-asm", "asm" -> commandJitAsm(parts)
            "jit-ir", "ir" -> commandJitIr(parts)
            "call" -> commandCall(parts)
            "icall" -> commandIcall(parts)
            "step", "s" -> commandStep(parts)
            "run" -> commandRun()
            "run-to" -> commandRunTo(parts)
            "continue", "cont", "c" -> commandContinue(parts)
            "jit-call", "jcall" -> commandJitCall(parts)
            "compare", "cmp" -> commandCompare(parts)
            "sync-memory" -> commandSyncMemory()
            "where", "w" -> commandWhere()
            "trace", "t" -> commandTrace(parts)
            "globals", "g" -> commandGlobals()
            "memory", "m" -> commandMemory(parts)
            "memory32", "m32" -> commandMemory32(parts)
            "alloc" -> {
                val name = parts.getOrNull(1) ?: run { output.println("Usage: alloc <func_N>"); return true }
                output.println(debugger.dumpAlloc(name))
            }
            "break" -> commandBreak(parts)
            "clear" -> commandClear()
            else -> output.println("Unknown command: $command (type 'help')")
        }
        return true
    }

    private fun commandFunc(parts: List<String>) {
        val index = resolveFunc(parts.getOrNull(1)) ?: return
        val info = debugger.functionInfo(index)
        output.println("${info.name} (local=${info.localIndex}, global=${info.globalIndex})")
        output.println("  (${info.params.joinToString(", ")}) -> (${info.results.joinToString(", ")})")
        output.println("  Locals: ${info.localCount}, Body: ${info.bodySize} bytes, Instructions: ${info.instructionCount}")
    }

    private fun commandDisasm(parts: List<String>) {
        val index = resolveFunc(parts.getOrNull(1)) ?: return
        val info = debugger.functionInfo(index)
        val instructions = debugger.disassemble(index)
        output.println("${info.name}: ${instructions.size} instructions, ${info.bodySize} bytes")
        for (instruction in instructions) {
            output.println("  %04x  %s".format(instruction.offset, instruction.text()))
        }
    }

    private fun commandJitAsm(parts: List<String>) {
        val name = parts.getOrNull(1) ?: run { output.println("Usage: jit-asm <func>"); return }
        val result = debugger.jitAsm(name)
        output.println(result ?: "JIT not available")
    }

    private fun commandJitIr(parts: List<String>) {
        val name = parts.getOrNull(1) ?: run { output.println("Usage: jit-ir <func>"); return }
        val result = debugger.jitIr(name)
        output.println(result ?: "JIT not available")
    }

    private fun commandCall(parts: List<String>) {
        val name = parts.getOrNull(1) ?: run { output.println("Usage: call <export> [limit] [args...]"); return }
        val limit = parts.getOrNull(2)?.toLongOrNull() ?: Long.MAX_VALUE
        val args = if (limit == Long.MAX_VALUE && parts.size > 2) {
            parts.drop(2).map { parseNumber(it) }.toLongArray()
        } else {
            parts.drop(3).map { parseNumber(it) }.toLongArray()
        }
        printCallResult(debugger.call(name, limit, *args))
    }

    private fun commandIcall(parts: List<String>) {
        val index = resolveFunc(parts.getOrNull(1)) ?: return
        val globalIndex = index + debugger.importCount
        val args = parts.drop(2).map { parseNumber(it) }.toLongArray()
        output.println("[interpreter] ${debugger.functionName(globalIndex)}(${formatArgs(args)})...")
        printCallResult(debugger.callByIndex(globalIndex, *args))
    }

    private fun commandStep(parts: List<String>) {
        val count = parts.getOrNull(1)?.toLongOrNull() ?: 1
        printCallResult(debugger.step(count))
    }

    private fun commandRun() {
        printCallResult(debugger.continueExecution(Long.MAX_VALUE))
    }

    private fun commandRunTo(parts: List<String>) {
        val name = parts.getOrNull(1) ?: run { output.println("Usage: run-to <func_name>"); return }
        printCallResult(debugger.runTo(name))
    }

    private fun commandContinue(parts: List<String>) {
        val limit = parts.getOrNull(1)?.toLongOrNull() ?: 10000
        printCallResult(debugger.continueExecution(limit))
    }

    private fun commandJitCall(parts: List<String>) {
        val index = resolveFunc(parts.getOrNull(1)) ?: return
        val globalIndex = index + debugger.importCount
        val args = parts.drop(2).map { parseNumber(it) }.toLongArray()
        output.println("[jit] ${debugger.functionName(globalIndex)}(${formatArgs(args)})...")
        printCallResult(debugger.jitCall(globalIndex, *args))
    }

    private fun commandCompare(parts: List<String>) {
        val index = resolveFunc(parts.getOrNull(1)) ?: return
        val globalIndex = index + debugger.importCount
        val args = parts.drop(2).map { parseNumber(it) }.toLongArray()
        output.println("Comparing ${debugger.functionName(globalIndex)}(${formatArgs(args)})...")
        val result = debugger.compare(globalIndex, *args)
        output.println("[interp] ${formatCallResult(result.interpreterResult)}")
        output.println("[jit]    ${formatCallResult(result.jitResult)}")
        output.println(if (result.match) { "MATCH" } else { "MISMATCH!" })
    }

    private fun commandSyncMemory() {
        val bytes = debugger.syncMemoryToJit()
        output.println("Synced $bytes bytes to JIT memory")
    }

    private fun commandWhere() {
        val state = debugger.where()
        output.println("  Instructions: ${state.totalInstructions}")
        output.println("  Call depth: ${state.callDepth}")
        output.println("  Functions called: ${state.functionsCalled}")
    }

    private fun commandTrace(parts: List<String>) {
        val count = parts.getOrNull(1)?.toIntOrNull() ?: 20
        val entries = debugger.trace(count)
        output.println("Last ${entries.size} calls:")
        for ((index, entry) in entries.withIndex()) {
            output.println("  [$index] $entry")
        }
    }

    private fun commandGlobals() {
        for ((index, value) in debugger.globals()) {
            output.println("  global[$index] = $value (0x${java.lang.Long.toHexString(value)})")
        }
    }

    private fun commandMemory(parts: List<String>) {
        val address = parts.getOrNull(1)?.let { parseNumber(it).toInt() } ?: 0
        val length = parts.getOrNull(2)?.toIntOrNull() ?: 64
        printHexDump(address, debugger.readMemory(address, length))
    }

    private fun commandMemory32(parts: List<String>) {
        val address = parts.getOrNull(1)?.let { parseNumber(it).toInt() } ?: 0
        val count = parts.getOrNull(2)?.toIntOrNull() ?: 8
        val memory = debugger.memory()
        for (index in 0 until count) {
            val offset = address + index * 4
            if (offset + 4 <= memory.sizeBytes()) {
                val value = memory.readI32(offset)
                output.println("  [0x${offset.toString(16)}] = $value (0x${Integer.toHexString(value)})")
            }
        }
    }

    private fun commandBreak(parts: List<String>) {
        val name = parts.getOrNull(1) ?: run { output.println("Usage: break <func_name>"); return }
        debugger.breakOnFunction(name)
        output.println("Breakpoint set: $name")
    }

    private fun commandClear() {
        debugger.clearBreakpoints()
        output.println("All breakpoints cleared")
    }

    private fun printInfo() {
        val info = debugger.moduleInfo()
        output.println("Functions: ${info.functionCount} local + ${info.importCount} imports = ${info.totalFunctionCount}")
        output.println("Types: ${info.typeCount}, Globals: ${info.globalCount}")
        output.println("Memory: ${info.memoryPages} pages (${info.memoryBytes} bytes)")
        output.println("Exports: ${info.exports.joinToString(", ")}")
        output.println("Instructions executed: ${info.totalInstructions}")
    }

    private fun printExports() {
        for (export in debugger.exports()) {
            output.println("  ${export.kind.name.lowercase()} ${export.name} → index ${export.index}")
        }
    }

    private fun printImports() {
        for ((index, imp) in debugger.imports().withIndex()) {
            output.println("  [$index] $imp")
        }
    }

    private fun printTypes() {
        for ((index, type) in debugger.types().withIndex()) {
            output.println("  type[$index]: (${type.params.joinToString(", ")}) -> (${type.results.joinToString(", ")})")
        }
    }

    private fun printElements() {
        for ((index, elem) in debugger.elements().withIndex()) {
            if (elem is WasmModule.Element.Active) {
                output.println("  element[$index]: table=${elem.tableIndex}, entries=${elem.funcIndices.size}")
            }
        }
    }

    private fun printCallResult(result: CallResult) {
        when {
            result.completed -> output.println("Result: ${formatArgs(result.result)}")
            result.paused -> output.println("Paused at ${debugger.where().totalInstructions} instructions")
            result.trap != null -> output.println("TRAP: ${result.trap}")
        }
    }

    private fun formatCallResult(result: CallResult): String {
        return when {
            result.completed -> formatArgs(result.result)
            result.trap != null -> "TRAP: ${result.trap}"
            else -> "paused"
        }
    }

    private fun resolveFunc(spec: String?): Int? {
        if (spec == null) { output.println("Usage: <command> <func_index or func_N>"); return null }
        val result = debugger.resolveFunction(spec)
        if (result == null) { output.println("Unknown function: $spec") }
        return result
    }

    private fun printHexDump(baseAddress: Int, bytes: ByteArray) {
        for (row in bytes.indices step 16) {
            val hex = StringBuilder()
            val ascii = StringBuilder()
            for (col in 0 until 16) {
                val offset = row + col
                if (offset < bytes.size) {
                    val byte = bytes[offset].toInt() and 0xFF
                    hex.append("%02x ".format(byte))
                    ascii.append(if (byte in 32..126) { byte.toChar() } else { '.' })
                } else {
                    hex.append("   ")
                }
            }
            output.println("  %08x  %s %s".format(baseAddress + row, hex, ascii))
        }
    }

    protected fun parseNumber(value: String): Long {
        return if (value.startsWith("0x") || value.startsWith("0X")) {
            java.lang.Long.parseUnsignedLong(value.substring(2), 16)
        } else {
            value.toLong()
        }
    }

    protected fun formatArgs(args: LongArray): String {
        return args.joinToString(", ") { "$it (0x${java.lang.Long.toHexString(it)})" }
    }

    private fun printHelp() {
        output.println("""
            |Inspection:
            |  info, i                       Module summary
            |  exports / imports / types      List module sections
            |  elements                       List element segments
            |  func <N|func_N>               Function metadata
            |  disasm <N|func_N>             Disassemble WASM bytecode
            |  jit-asm <func_N>              Dump JIT native x86 code
            |  jit-ir <func_N>              Dump JIT IR (after Mem2Reg)
            |
            |Execution (interpreter):
            |  call <export> [limit] [args]  Call export, optional instruction limit
            |  icall <N|func_N> [args]       Call by function index
            |  step [N], s [N]               Execute N instructions (default 1)
            |  run                           Run until break/trap/completion
            |  run-to <func_name>            Run until function entry
            |  continue [N], c [N]           Run N more instructions (default 10000)
            |
            |JIT:
            |  jit-call <N|func_N> [args]   Call by index via JIT
            |  compare <N|func_N> [args]    Call both, compare results
            |  sync-memory                   Copy interpreter memory to JIT
            |
            |State:
            |  where, w                      Execution state
            |  trace [N], t [N]              Last N function calls (default 20)
            |  globals, g                    Show globals
            |  memory <addr> [len], m        Hex dump (addr: decimal or 0x)
            |  memory32 <addr> [N], m32      Show N i32 values
            |
            |Breakpoints:
            |  break <func_name>             Break on function entry
            |  clear                         Clear all breakpoints
            |
            |  help, h / quit, q             Help / Exit
        """.trimMargin())
    }
}
