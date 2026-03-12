package org.kgen.examples.lang

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintStream

/**
 * Interactive REPL for the example language.
 *
 * Supports:
 * - Function definitions: `fun add(a: int, b: int): int { return a + b }`
 * - Expressions evaluated as statements: `add(3, 4)` prints the result
 * - Extern declarations: `extern fun print_val(x: int): int`
 * - Multi-line input (brace matching)
 * - `:quit` / `:q` to exit, `:help` for help, `:fns` to list functions
 */
class Repl(
    private val input: BufferedReader = BufferedReader(InputStreamReader(System.`in`)),
    private val output: PrintStream = System.out,
) : AutoCloseable {

    private val runner = JitRunner()
    private val definedFunctions = mutableMapOf<String, FunDecl>()
    private val externFunctions = mutableMapOf<String, FunDecl>()
    private var exprCounter = 0

    init {
        runner.enableManagedRuntime()
    }

    fun run() {
        output.println("kgen lang REPL")
        output.println("Type :help for help, :quit to exit")
        output.println()

        while (true) {
            output.print(">>> ")
            output.flush()
            val line = readInput() ?: break
            if (line.isBlank()) continue

            when {
                line.startsWith(":") -> if (!handleCommand(line)) break
                else -> handleInput(line)
            }
        }
    }

    private fun readInput(): String? {
        val first = input.readLine() ?: return null
        val trimmed = first.trim()

        // Single-line input with balanced braces
        var braceCount = trimmed.count { it == '{' } - trimmed.count { it == '}' }
        if (braceCount <= 0) return trimmed

        // Multi-line: keep reading until braces balance
        val sb = StringBuilder(trimmed)
        while (braceCount > 0) {
            output.print("... ")
            output.flush()
            val next = input.readLine() ?: break
            sb.append("\n").append(next)
            braceCount += next.count { it == '{' } - next.count { it == '}' }
        }
        return sb.toString()
    }

    private fun handleCommand(cmd: String): Boolean {
        return when (cmd.trim().lowercase()) {
            ":quit", ":q" -> false
            ":help", ":h" -> {
                output.println("Commands:")
                output.println("  :quit, :q    Exit the REPL")
                output.println("  :help, :h    Show this help")
                output.println("  :fns         List defined functions")
                output.println()
                output.println("Usage:")
                output.println("  fun add(a: int, b: int): int { return a + b }")
                output.println("  add(3, 4)")
                output.println("  extern fun print_val(x: int): int")
                true
            }
            ":fns" -> {
                if (definedFunctions.isEmpty() && externFunctions.isEmpty()) {
                    output.println("No functions defined")
                } else {
                    for (fn in externFunctions.values) {
                        val params = fn.params.joinToString(", ") { "${it.name}: ${it.type.name.lowercase()}" }
                        output.println("  extern fun ${fn.name}($params): ${fn.returnType.name.lowercase()}")
                    }
                    for (fn in definedFunctions.values) {
                        val params = fn.params.joinToString(", ") { "${it.name}: ${it.type.name.lowercase()}" }
                        output.println("  fun ${fn.name}($params): ${fn.returnType.name.lowercase()}")
                    }
                }
                true
            }
            else -> {
                output.println("Unknown command: $cmd")
                true
            }
        }
    }

    private fun handleInput(input: String) {
        try {
            val trimmed = input.trim()
            when {
                trimmed.startsWith("fun ") || trimmed.startsWith("extern ") -> handleFunctionDef(trimmed)
                else -> handleExpression(trimmed)
            }
        } catch (e: LangError) {
            output.println("Error: ${e.message}")
        } catch (e: Exception) {
            output.println("Error: ${e.message}")
        }
    }

    private fun handleFunctionDef(source: String) {
        // Build a complete program with all existing externs + this new function
        val allExterns = externFunctions.values.joinToString("\n") { fn ->
            val params = fn.params.joinToString(", ") { "${it.name}: ${it.type.name.lowercase()}" }
            "extern fun ${fn.name}($params): ${fn.returnType.name.lowercase()}"
        }
        val fullSource = if (allExterns.isNotEmpty()) "$allExterns\n$source" else source

        val tokens = Lexer(fullSource).tokenize()
        val program = Parser(tokens).parseProgram()

        // Find the new function(s) that aren't externs
        for (fn in program.functions) {
            if (fn.mode == FunMode.EXTERN) {
                externFunctions[fn.name] = fn
            } else {
                definedFunctions[fn.name] = fn
            }
        }

        runner.load(fullSource)

        val newFns = program.functions.filter { it.mode != FunMode.EXTERN }
        for (fn in newFns) {
            val params = fn.params.joinToString(", ") { "${it.name}: ${it.type.name.lowercase()}" }
            output.println("Defined: ${fn.name}($params): ${fn.returnType.name.lowercase()}")
        }
    }

    private fun buildExternDeclarations(): List<String> {
        val parts = mutableListOf<String>()
        for (fn in externFunctions.values) {
            val params = fn.params.joinToString(", ") { "${it.name}: ${it.type.name.lowercase()}" }
            parts.add("extern fun ${fn.name}($params): ${fn.returnType.name.lowercase()}")
        }
        // Previously defined functions are available via JIT symbol resolution
        for (fn in definedFunctions.values) {
            val params = fn.params.joinToString(", ") { "${it.name}: ${it.type.name.lowercase()}" }
            parts.add("extern fun ${fn.name}($params): ${fn.returnType.name.lowercase()}")
        }
        return parts
    }

    private fun handleExpression(source: String) {
        val exprName = "__repl_expr_${exprCounter++}"

        val parts = buildExternDeclarations().toMutableList()

        // Wrap expression as: fun __repl_expr_N(): int { return <expr> }
        parts.add("fun $exprName(): int { return $source }")

        val fullSource = parts.joinToString("\n")

        try {
            val result = runner.run(fullSource, exprName)
            output.println("= $result")
        } catch (e: Exception) {
            // Maybe it's a statement, not an expression — try as void
            try {
                val stmtSource = parts.dropLast(1).joinToString("\n") +
                    "\nfun $exprName(): void { $source }"
                runner.run(stmtSource, exprName)
            } catch (e2: Exception) {
                throw e // throw original error
            }
        }
    }

    override fun close() {
        runner.close()
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            Repl().use { it.run() }
        }
    }
}
