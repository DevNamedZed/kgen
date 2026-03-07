package org.kgen.examples.lang

class Parser(private val tokens: List<Token>) {
    private var pos = 0

    fun parseProgram(): Program {
        val functions = mutableListOf<FunDecl>()
        while (!atEnd()) {
            functions.add(parseFunDecl())
        }
        return Program(functions)
    }

    fun parseExpr(): Expr = parseOr()

    private fun parseFunDecl(): FunDecl {
        var mode = FunMode.DEFAULT
        if (check(Token.Kind.NATIVE)) { advance(); mode = FunMode.NATIVE }
        else if (check(Token.Kind.MANAGED)) { advance(); mode = FunMode.MANAGED }
        else if (check(Token.Kind.EXTERN)) { advance(); mode = FunMode.EXTERN }

        expect(Token.Kind.FUN)
        val name = expect(Token.Kind.IDENT).text
        expect(Token.Kind.LPAREN)
        val params = parseParamList()
        expect(Token.Kind.RPAREN)
        expect(Token.Kind.COLON)
        val returnType = parseType()
        val body = if (mode == FunMode.EXTERN) null else parseBlock()
        return FunDecl(name, params, returnType, body, mode)
    }

    private fun parseParamList(): List<FunParam> {
        val params = mutableListOf<FunParam>()
        if (check(Token.Kind.RPAREN)) return params
        params.add(parseParam())
        while (check(Token.Kind.COMMA)) {
            advance()
            params.add(parseParam())
        }
        return params
    }

    private fun parseParam(): FunParam {
        val name = expect(Token.Kind.IDENT).text
        expect(Token.Kind.COLON)
        val type = parseType()
        return FunParam(name, type)
    }

    private fun parseType(): LangType {
        val tok = current()
        return when (tok.kind) {
            Token.Kind.INT, Token.Kind.FLOAT, Token.Kind.BOOL,
            Token.Kind.STRING, Token.Kind.VOID -> {
                advance()
                LangType.fromToken(tok.kind)
            }
            else -> throw LangError("Expected type, got ${tok.kind}", tok.line, tok.col)
        }
    }

    private fun parseBlock(): Block {
        expect(Token.Kind.LBRACE)
        val stmts = mutableListOf<Stmt>()
        while (!check(Token.Kind.RBRACE) && !atEnd()) {
            stmts.add(parseStmt())
        }
        expect(Token.Kind.RBRACE)
        return Block(stmts)
    }

    private fun parseStmt(): Stmt {
        return when (current().kind) {
            Token.Kind.VAR, Token.Kind.VAL -> parseVarDecl()
            Token.Kind.RETURN -> parseReturn()
            Token.Kind.IF -> parseIf()
            Token.Kind.WHILE -> parseWhile()
            Token.Kind.IDENT -> {
                if (pos + 1 < tokens.size && tokens[pos + 1].kind == Token.Kind.ASSIGN) {
                    parseAssignment()
                } else {
                    val expr = parseExpr()
                    optionalSemicolon()
                    ExprStmt(expr)
                }
            }
            else -> {
                val expr = parseExpr()
                optionalSemicolon()
                ExprStmt(expr)
            }
        }
    }

    private fun parseVarDecl(): VarDecl {
        val mutable = current().kind == Token.Kind.VAR
        advance()
        val name = expect(Token.Kind.IDENT).text
        var type: LangType? = null
        if (check(Token.Kind.COLON)) {
            advance()
            type = parseType()
        }
        expect(Token.Kind.ASSIGN)
        val value = parseExpr()
        optionalSemicolon()
        return VarDecl(name, type, value, mutable)
    }

    private fun parseAssignment(): Assignment {
        val name = expect(Token.Kind.IDENT).text
        expect(Token.Kind.ASSIGN)
        val value = parseExpr()
        optionalSemicolon()
        return Assignment(name, value)
    }

    private fun parseReturn(): ReturnStmt {
        advance() // skip 'return'
        val value = if (check(Token.Kind.RBRACE) || check(Token.Kind.SEMICOLON) || atEnd()) {
            null
        } else {
            parseExpr()
        }
        optionalSemicolon()
        return ReturnStmt(value)
    }

    private fun parseIf(): IfStmt {
        advance() // skip 'if'
        val condition = parseExpr()
        val thenBlock = parseBlock()
        val elseBlock = if (check(Token.Kind.ELSE)) {
            advance()
            parseBlock()
        } else null
        return IfStmt(condition, thenBlock, elseBlock)
    }

    private fun parseWhile(): WhileStmt {
        advance() // skip 'while'
        val condition = parseExpr()
        val body = parseBlock()
        return WhileStmt(condition, body)
    }

    // --- Expression parsing (precedence climbing) ---

    private fun parseOr(): Expr {
        var left = parseAnd()
        while (check(Token.Kind.OR)) {
            advance()
            left = BinaryExpr(left, BinaryOp.OR, parseAnd())
        }
        return left
    }

    private fun parseAnd(): Expr {
        var left = parseEquality()
        while (check(Token.Kind.AND)) {
            advance()
            left = BinaryExpr(left, BinaryOp.AND, parseEquality())
        }
        return left
    }

    private fun parseEquality(): Expr {
        var left = parseComparison()
        while (true) {
            val op = when {
                check(Token.Kind.EQ) -> BinaryOp.EQ
                check(Token.Kind.NEQ) -> BinaryOp.NEQ
                else -> break
            }
            advance()
            left = BinaryExpr(left, op, parseComparison())
        }
        return left
    }

    private fun parseComparison(): Expr {
        var left = parseAddSub()
        while (true) {
            val op = when {
                check(Token.Kind.LT) -> BinaryOp.LT
                check(Token.Kind.GT) -> BinaryOp.GT
                check(Token.Kind.LTE) -> BinaryOp.LTE
                check(Token.Kind.GTE) -> BinaryOp.GTE
                else -> break
            }
            advance()
            left = BinaryExpr(left, op, parseAddSub())
        }
        return left
    }

    private fun parseAddSub(): Expr {
        var left = parseMulDiv()
        while (true) {
            val op = when {
                check(Token.Kind.PLUS) -> BinaryOp.ADD
                check(Token.Kind.MINUS) -> BinaryOp.SUB
                else -> break
            }
            advance()
            left = BinaryExpr(left, op, parseMulDiv())
        }
        return left
    }

    private fun parseMulDiv(): Expr {
        var left = parseUnary()
        while (true) {
            val op = when {
                check(Token.Kind.STAR) -> BinaryOp.MUL
                check(Token.Kind.SLASH) -> BinaryOp.DIV
                check(Token.Kind.PERCENT) -> BinaryOp.MOD
                else -> break
            }
            advance()
            left = BinaryExpr(left, op, parseUnary())
        }
        return left
    }

    private fun parseUnary(): Expr {
        if (check(Token.Kind.MINUS)) {
            advance()
            return UnaryExpr(UnaryOp.NEG, parseUnary())
        }
        if (check(Token.Kind.NOT)) {
            advance()
            return UnaryExpr(UnaryOp.NOT, parseUnary())
        }
        return parsePrimary()
    }

    private fun parsePrimary(): Expr {
        val tok = current()
        return when (tok.kind) {
            Token.Kind.INT_LIT -> { advance(); IntLiteral(tok.text.toLong()) }
            Token.Kind.FLOAT_LIT -> { advance(); FloatLiteral(tok.text.toDouble()) }
            Token.Kind.TRUE -> { advance(); BoolLiteral(true) }
            Token.Kind.FALSE -> { advance(); BoolLiteral(false) }
            Token.Kind.STRING_LIT -> { advance(); StringLiteral(tok.text) }
            Token.Kind.IDENT -> {
                advance()
                if (check(Token.Kind.LPAREN)) {
                    advance()
                    val args = parseArgList()
                    expect(Token.Kind.RPAREN)
                    CallExpr(tok.text, args)
                } else {
                    Ident(tok.text)
                }
            }
            Token.Kind.LPAREN -> {
                advance()
                val expr = parseExpr()
                expect(Token.Kind.RPAREN)
                expr
            }
            else -> throw LangError("Unexpected token: ${tok.kind}", tok.line, tok.col)
        }
    }

    private fun parseArgList(): List<Expr> {
        val args = mutableListOf<Expr>()
        if (check(Token.Kind.RPAREN)) return args
        args.add(parseExpr())
        while (check(Token.Kind.COMMA)) {
            advance()
            args.add(parseExpr())
        }
        return args
    }

    // --- Helpers ---

    private fun current(): Token = tokens[pos]
    private fun atEnd(): Boolean = tokens[pos].kind == Token.Kind.EOF
    private fun check(kind: Token.Kind): Boolean = !atEnd() && tokens[pos].kind == kind

    private fun advance(): Token {
        val tok = tokens[pos]
        if (!atEnd()) pos++
        return tok
    }

    private fun expect(kind: Token.Kind): Token {
        val tok = current()
        if (tok.kind != kind) {
            throw LangError("Expected $kind, got ${tok.kind}", tok.line, tok.col)
        }
        return advance()
    }

    private fun optionalSemicolon() {
        if (check(Token.Kind.SEMICOLON)) advance()
    }
}
