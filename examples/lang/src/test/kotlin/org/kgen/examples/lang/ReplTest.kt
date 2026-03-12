package org.kgen.examples.lang

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.io.StringReader

@EnabledOnOs(OS.WINDOWS, OS.LINUX)
class ReplTest {

    private fun runRepl(vararg lines: String): String {
        val input = BufferedReader(StringReader(lines.joinToString("\n") + "\n:quit\n"))
        val outputBytes = ByteArrayOutputStream()
        val output = PrintStream(outputBytes)
        Repl(input, output).use { it.run() }
        return outputBytes.toString()
    }

    @Test
    fun defineAndCallFunction() {
        val result = runRepl(
            "fun double(x: int): int { return x + x }",
            "double(21)"
        )
        assertTrue(result.contains("Defined: double"), result)
        assertTrue(result.contains("= 42"), result)
    }

    @Test
    fun evaluateExpression() {
        val result = runRepl("3 + 4")
        assertTrue(result.contains("= 7"), result)
    }

    @Test
    fun evaluateConstant() {
        val result = runRepl("100")
        assertTrue(result.contains("= 100"), result)
    }

    @Test
    fun listFunctionsEmpty() {
        val result = runRepl(":fns")
        assertTrue(result.contains("No functions defined"), result)
    }

    @Test
    fun listFunctionsAfterDefine() {
        val result = runRepl(
            "fun add(a: int, b: int): int { return a + b }",
            ":fns"
        )
        assertTrue(result.contains("fun add(a: int, b: int): int"), result)
    }

    @Test
    fun helpCommand() {
        val result = runRepl(":help")
        assertTrue(result.contains(":quit"), result)
        assertTrue(result.contains(":fns"), result)
    }

    @Test
    fun multipleExpressions() {
        val result = runRepl("10 * 5", "7 - 3")
        assertTrue(result.contains("= 50"), result)
        assertTrue(result.contains("= 4"), result)
    }

    @Test
    fun errorHandling() {
        val result = runRepl("undefined_var")
        assertTrue(result.contains("Error"), result)
    }
}
