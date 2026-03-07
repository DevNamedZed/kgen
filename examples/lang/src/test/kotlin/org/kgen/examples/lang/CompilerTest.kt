package org.kgen.examples.lang

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*

class CompilerTest {

    private fun compileSource(source: String): Module {
        val tokens = Lexer(source).tokenize()
        val program = Parser(tokens).parseProgram()
        return Compiler().compile(program)
    }

    @Test
    fun compilesSimpleFunction() {
        val module = compileSource("fun f(): int { return 42 }")
        assertEquals(1, module.functions.size)
        assertEquals("f", module.functions[0].name)
        assertEquals(Type.I64, module.functions[0].returnType)
    }

    @Test
    fun compilesWithParams() {
        val module = compileSource("fun add(a: int, b: int): int { return a + b }")
        val fn = module.functions[0]
        assertEquals(2, fn.params.size)
        assertEquals("a", fn.params[0].name)
        assertEquals(Type.I64, fn.params[0].type)
    }

    @Test
    fun compilesVoidFunction() {
        val module = compileSource("fun noop(): void {}")
        val fn = module.functions[0]
        assertEquals(Type.Void, fn.returnType)
        // Should have an implicit return
        val lastInst = fn.blocks.last().instructions.last()
        assertTrue(lastInst is Instruction.Ret)
    }

    @Test
    fun compilesArithmetic() {
        val module = compileSource("fun calc(a: int, b: int): int { return a + b * 2 }")
        val fn = module.functions[0]
        // Should have instructions for: mul, add, ret
        val instrs = fn.blocks[0].instructions
        assertTrue(instrs.any { it is Instruction.Mul })
        assertTrue(instrs.any { it is Instruction.Add })
    }

    @Test
    fun compilesIfStatement() {
        val module = compileSource("""
            fun abs(x: int): int {
                if x < 0 {
                    return 0 - x
                } else {
                    return x
                }
            }
        """.trimIndent())
        val fn = module.functions[0]
        assertTrue(fn.blocks.size >= 3, "Should have entry + then + else + merge blocks")
    }

    @Test
    fun compilesWhileLoop() {
        val module = compileSource("""
            fun countdown(n: int): int {
                var i: int = n
                while i > 0 {
                    i = i - 1
                }
                return i
            }
        """.trimIndent())
        val fn = module.functions[0]
        assertTrue(fn.blocks.size >= 3, "Should have entry + cond + body + exit blocks")
    }

    @Test
    fun compilesFloatType() {
        val module = compileSource("fun pi(): float { return 3.14 }")
        val fn = module.functions[0]
        assertEquals(Type.F64, fn.returnType)
    }

    @Test
    fun compilesBoolType() {
        val module = compileSource("fun yes(): bool { return true }")
        val fn = module.functions[0]
        assertEquals(Type.I1, fn.returnType)
    }

    @Test
    fun compilesMultipleFunctions() {
        val module = compileSource("""
            fun a(): int { return 1 }
            fun b(): int { return 2 }
            fun c(): int { return 3 }
        """.trimIndent())
        assertEquals(3, module.functions.size)
    }

    @Test
    fun compilesExternFunction() {
        val module = compileSource("""
            extern fun puts(s: int): int
            fun main(): int { return puts(0) }
        """.trimIndent())
        // Should have both the declaration and the function
        assertEquals(2, module.functions.size)
        val puts = module.functions.first { it.name == "puts" }
        assertTrue(puts.isExternal)
        val main = module.functions.first { it.name == "main" }
        assertFalse(main.isExternal)
    }

    @Test
    fun compilesVarWithTypeInference() {
        val module = compileSource("fun f(): int { val x = 42; return x }")
        val fn = module.functions[0]
        assertNotNull(fn.blocks[0].instructions)
    }

    @Test
    fun compilesModuloOp() {
        val module = compileSource("fun f(a: int, b: int): int { return a % b }")
        val fn = module.functions[0]
        assertTrue(fn.blocks[0].instructions.any { it is Instruction.SRem })
    }

    @Test
    fun compilesComparison() {
        val module = compileSource("fun f(a: int, b: int): bool { return a < b }")
        val fn = module.functions[0]
        assertEquals(Type.I1, fn.returnType)
        assertTrue(fn.blocks[0].instructions.any { it is Instruction.ICmp })
    }

    @Test
    fun compilesNegation() {
        val module = compileSource("fun f(x: int): int { return 0 - x }")
        val fn = module.functions[0]
        assertTrue(fn.blocks[0].instructions.any { it is Instruction.Sub })
    }

    @Test
    fun compilesWhileWithMutableVar() {
        val module = compileSource("""
            fun f(): int {
                var i: int = 0
                while i < 10 {
                    i = i + 1
                }
                return i
            }
        """.trimIndent())
        val fn = module.functions[0]
        // Should have alloca, store, load instructions
        assertTrue(fn.blocks[0].instructions.any { it is Instruction.Alloca })
    }

    @Test
    fun compilesCallExpr() {
        val module = compileSource("""
            fun double(x: int): int { return x + x }
            fun main(): int { return double(5) }
        """.trimIndent())
        val main = module.functions.first { it.name == "main" }
        assertTrue(main.blocks[0].instructions.any { it is Instruction.Call })
    }

    @Test
    fun moduleNameBasedOnFirstFunction() {
        val module = compileSource("fun myFunc(): int { return 0 }")
        assertEquals("myFunc_module", module.name)
    }

    @Test
    fun compilesMultipleExterns() {
        val module = compileSource("""
            extern fun a(): int
            extern fun b(x: int): int
            fun main(): int { return a() + b(1) }
        """.trimIndent())
        val externs = module.functions.filter { it.isExternal }
        assertEquals(2, externs.size)
    }
}
