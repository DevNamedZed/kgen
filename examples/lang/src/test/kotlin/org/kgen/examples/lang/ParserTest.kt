package org.kgen.examples.lang

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class ParserTest {

    private fun parse(source: String): Program {
        val tokens = Lexer(source).tokenize()
        return Parser(tokens).parseProgram()
    }

    @Test
    fun emptyFunction() {
        val prog = parse("fun f(): void {}")
        assertEquals(1, prog.functions.size)
        assertEquals("f", prog.functions[0].name)
        assertEquals(LangType.VOID, prog.functions[0].returnType)
        assertEquals(0, prog.functions[0].params.size)
    }

    @Test
    fun functionWithParams() {
        val prog = parse("fun add(a: int, b: int): int { return a + b }")
        val fn = prog.functions[0]
        assertEquals("add", fn.name)
        assertEquals(2, fn.params.size)
        assertEquals("a", fn.params[0].name)
        assertEquals(LangType.INT, fn.params[0].type)
        assertEquals(LangType.INT, fn.returnType)
    }

    @Test
    fun nativeAnnotation() {
        val prog = parse("native fun f(): int { return 42 }")
        assertEquals(FunMode.NATIVE, prog.functions[0].mode)
    }

    @Test
    fun managedAnnotation() {
        val prog = parse("managed fun f(): void {}")
        assertEquals(FunMode.MANAGED, prog.functions[0].mode)
    }

    @Test
    fun varDecl() {
        val prog = parse("fun f(): void { var x: int = 42 }")
        val stmt = prog.functions[0].body!!.stmts[0] as VarDecl
        assertEquals("x", stmt.name)
        assertEquals(LangType.INT, stmt.type)
        assertTrue(stmt.mutable)
        assertEquals(42L, (stmt.value as IntLiteral).value)
    }

    @Test
    fun valDecl() {
        val prog = parse("fun f(): void { val x = 10 }")
        val stmt = prog.functions[0].body!!.stmts[0] as VarDecl
        assertFalse(stmt.mutable)
    }

    @Test
    fun ifStatement() {
        val prog = parse("fun f(x: int): int { if x > 0 { return 1 } else { return 0 } }")
        val stmt = prog.functions[0].body!!.stmts[0] as IfStmt
        assertNotNull(stmt.elseBlock)
    }

    @Test
    fun whileStatement() {
        val prog = parse("fun f(): void { var i: int = 0; while i < 10 { i = i + 1 } }")
        assertTrue(prog.functions[0].body!!.stmts[1] is WhileStmt)
    }

    @Test
    fun binaryExprPrecedence() {
        val tokens = Lexer("2 + 3 * 4").tokenize()
        val expr = Parser(tokens).parseExpr()
        // Should parse as 2 + (3 * 4), not (2 + 3) * 4
        assertTrue(expr is BinaryExpr)
        val add = expr as BinaryExpr
        assertEquals(BinaryOp.ADD, add.op)
        assertTrue(add.right is BinaryExpr)
        assertEquals(BinaryOp.MUL, (add.right as BinaryExpr).op)
    }

    @Test
    fun unaryNeg() {
        val tokens = Lexer("-5").tokenize()
        val expr = Parser(tokens).parseExpr()
        assertTrue(expr is UnaryExpr)
        assertEquals(UnaryOp.NEG, (expr as UnaryExpr).op)
    }

    @Test
    fun functionCall() {
        val tokens = Lexer("add(1, 2)").tokenize()
        val expr = Parser(tokens).parseExpr()
        assertTrue(expr is CallExpr)
        val call = expr as CallExpr
        assertEquals("add", call.name)
        assertEquals(2, call.args.size)
    }

    @Test
    fun multipleFunctions() {
        val prog = parse("""
            fun add(a: int, b: int): int { return a + b }
            fun mul(a: int, b: int): int { return a * b }
        """.trimIndent())
        assertEquals(2, prog.functions.size)
        assertEquals("add", prog.functions[0].name)
        assertEquals("mul", prog.functions[1].name)
    }

    @Test
    fun comparisonOperators() {
        val tokens = Lexer("a == b").tokenize()
        val expr = Parser(tokens).parseExpr()
        assertTrue(expr is BinaryExpr)
        assertEquals(BinaryOp.EQ, (expr as BinaryExpr).op)
    }

    @Test
    fun parenthesizedExpr() {
        val tokens = Lexer("(2 + 3) * 4").tokenize()
        val expr = Parser(tokens).parseExpr()
        assertTrue(expr is BinaryExpr)
        assertEquals(BinaryOp.MUL, (expr as BinaryExpr).op)
    }

    @Test
    fun externFunction() {
        val prog = parse("extern fun puts(s: int): int")
        val fn = prog.functions[0]
        assertEquals("puts", fn.name)
        assertEquals(FunMode.EXTERN, fn.mode)
        assertNull(fn.body)
        assertEquals(1, fn.params.size)
        assertEquals(LangType.INT, fn.returnType)
    }

    @Test
    fun externWithRegularFunction() {
        val prog = parse("""
            extern fun getTime(): int
            fun main(): int { return getTime() }
        """.trimIndent())
        assertEquals(2, prog.functions.size)
        assertEquals(FunMode.EXTERN, prog.functions[0].mode)
        assertNull(prog.functions[0].body)
        assertEquals(FunMode.DEFAULT, prog.functions[1].mode)
        assertNotNull(prog.functions[1].body)
    }

    @Test
    fun returnWithoutValue() {
        val prog = parse("fun f(): void { return }")
        val stmt = prog.functions[0].body!!.stmts[0] as ReturnStmt
        assertNull(stmt.value)
    }

    @Test
    fun assignment() {
        val prog = parse("fun f(): void { var x: int = 0; x = 42 }")
        assertTrue(prog.functions[0].body!!.stmts[1] is Assignment)
        assertEquals("x", (prog.functions[0].body!!.stmts[1] as Assignment).name)
    }

    @Test
    fun nestedBlocks() {
        val prog = parse("""
            fun f(): int {
                if true {
                    if false {
                        return 1
                    } else {
                        return 2
                    }
                }
                return 0
            }
        """.trimIndent())
        val stmts = prog.functions[0].body!!.stmts
        assertTrue(stmts[0] is IfStmt)
    }

    @Test
    fun expressionStatement() {
        val prog = parse("fun f(): void { add(1, 2) }")
        assertTrue(prog.functions[0].body!!.stmts[0] is ExprStmt)
    }

    @Test
    fun boolLiterals() {
        val tokens = Lexer("true").tokenize()
        val expr = Parser(tokens).parseExpr()
        assertTrue(expr is BoolLiteral)
        assertTrue((expr as BoolLiteral).value)
    }

    @Test
    fun stringLiteralExpr() {
        val tokens = Lexer("\"hello\"").tokenize()
        val expr = Parser(tokens).parseExpr()
        assertTrue(expr is StringLiteral)
        assertEquals("hello", (expr as StringLiteral).value)
    }

    @Test
    fun floatLiteralExpr() {
        val tokens = Lexer("3.14").tokenize()
        val expr = Parser(tokens).parseExpr()
        assertTrue(expr is FloatLiteral)
        assertEquals(3.14, (expr as FloatLiteral).value)
    }

    @Test
    fun nestedCallExpr() {
        val tokens = Lexer("f(g(1))").tokenize()
        val expr = Parser(tokens).parseExpr()
        assertTrue(expr is CallExpr)
        val call = expr as CallExpr
        assertEquals("f", call.name)
        assertTrue(call.args[0] is CallExpr)
        assertEquals("g", (call.args[0] as CallExpr).name)
    }

    @Test
    fun comparisonChain() {
        val tokens = Lexer("a < b").tokenize()
        val expr = Parser(tokens).parseExpr()
        assertTrue(expr is BinaryExpr)
        assertEquals(BinaryOp.LT, (expr as BinaryExpr).op)
    }

    @Test
    fun allComparisonOps() {
        for ((src, op) in listOf(
            "a < b" to BinaryOp.LT,
            "a > b" to BinaryOp.GT,
            "a <= b" to BinaryOp.LTE,
            "a >= b" to BinaryOp.GTE,
            "a == b" to BinaryOp.EQ,
            "a != b" to BinaryOp.NEQ,
        )) {
            val tokens = Lexer(src).tokenize()
            val expr = Parser(tokens).parseExpr() as BinaryExpr
            assertEquals(op, expr.op, "Failed for: $src")
        }
    }

    @Test
    fun allArithOps() {
        for ((src, op) in listOf(
            "a + b" to BinaryOp.ADD,
            "a - b" to BinaryOp.SUB,
            "a * b" to BinaryOp.MUL,
            "a / b" to BinaryOp.DIV,
            "a % b" to BinaryOp.MOD,
        )) {
            val tokens = Lexer(src).tokenize()
            val expr = Parser(tokens).parseExpr() as BinaryExpr
            assertEquals(op, expr.op, "Failed for: $src")
        }
    }

    @Test
    fun logicalOps() {
        val tokens = Lexer("a and b or c").tokenize()
        val expr = Parser(tokens).parseExpr()
        // Should be (a and b) or c due to precedence
        assertTrue(expr is BinaryExpr)
        assertEquals(BinaryOp.OR, (expr as BinaryExpr).op)
    }

    @Test
    fun unaryNot() {
        val tokens = Lexer("not true").tokenize()
        val expr = Parser(tokens).parseExpr()
        assertTrue(expr is UnaryExpr)
        assertEquals(UnaryOp.NOT, (expr as UnaryExpr).op)
    }

    @Test
    fun callWithNoArgs() {
        val tokens = Lexer("foo()").tokenize()
        val expr = Parser(tokens).parseExpr()
        assertTrue(expr is CallExpr)
        assertEquals(0, (expr as CallExpr).args.size)
    }

    @Test
    fun multipleParams() {
        val prog = parse("fun f(a: int, b: float, c: bool): void {}")
        val fn = prog.functions[0]
        assertEquals(3, fn.params.size)
        assertEquals(LangType.INT, fn.params[0].type)
        assertEquals(LangType.FLOAT, fn.params[1].type)
        assertEquals(LangType.BOOL, fn.params[2].type)
    }

    @Test
    fun allTypes() {
        for ((src, type) in listOf(
            "int" to LangType.INT,
            "float" to LangType.FLOAT,
            "bool" to LangType.BOOL,
            "string" to LangType.STRING,
            "void" to LangType.VOID,
        )) {
            val prog = parse("fun f(): $src {}")
            assertEquals(type, prog.functions[0].returnType, "Failed for: $src")
        }
    }

    @Test
    fun semicolonOptional() {
        val prog = parse("fun f(): void { var x: int = 1; var y: int = 2 }")
        assertEquals(2, prog.functions[0].body!!.stmts.size)
    }

    @Test
    fun whileWithMultipleStatements() {
        val prog = parse("""
            fun f(): void {
                var i: int = 0
                while i < 10 {
                    i = i + 1
                    i = i + 1
                }
            }
        """.trimIndent())
        val whileStmt = prog.functions[0].body!!.stmts[1] as WhileStmt
        assertEquals(2, whileStmt.body.stmts.size)
    }

    @Test
    fun externWithMultipleParams() {
        val prog = parse("extern fun add(a: int, b: int, c: int): int")
        val fn = prog.functions[0]
        assertEquals(3, fn.params.size)
        assertEquals(FunMode.EXTERN, fn.mode)
    }
}
