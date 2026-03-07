package org.kgen.examples.lang

sealed interface AstNode

sealed interface Expr : AstNode
sealed interface Stmt : AstNode

// --- Expressions ---

data class IntLiteral(val value: Long) : Expr
data class FloatLiteral(val value: Double) : Expr
data class BoolLiteral(val value: Boolean) : Expr
data class StringLiteral(val value: String) : Expr
data class Ident(val name: String) : Expr

data class BinaryExpr(val left: Expr, val op: BinaryOp, val right: Expr) : Expr
data class UnaryExpr(val op: UnaryOp, val operand: Expr) : Expr
data class CallExpr(val name: String, val args: List<Expr>) : Expr

enum class BinaryOp { ADD, SUB, MUL, DIV, MOD, EQ, NEQ, LT, GT, LTE, GTE, AND, OR }
enum class UnaryOp { NEG, NOT }

// --- Statements ---

data class VarDecl(val name: String, val type: LangType?, val value: Expr, val mutable: Boolean) : Stmt
data class Assignment(val name: String, val value: Expr) : Stmt
data class ReturnStmt(val value: Expr?) : Stmt
data class ExprStmt(val expr: Expr) : Stmt
data class IfStmt(val condition: Expr, val thenBlock: Block, val elseBlock: Block?) : Stmt
data class WhileStmt(val condition: Expr, val body: Block) : Stmt
data class Block(val stmts: List<Stmt>) : AstNode

// --- Top-level ---

data class FunDecl(
    val name: String,
    val params: List<FunParam>,
    val returnType: LangType,
    val body: Block?,
    val mode: FunMode = FunMode.DEFAULT,
) : AstNode

data class FunParam(val name: String, val type: LangType)

enum class FunMode { DEFAULT, NATIVE, MANAGED, EXTERN }

enum class LangType {
    INT, FLOAT, BOOL, STRING, VOID;

    companion object {
        fun fromToken(kind: Token.Kind): LangType = when (kind) {
            Token.Kind.INT -> INT
            Token.Kind.FLOAT -> FLOAT
            Token.Kind.BOOL -> BOOL
            Token.Kind.STRING -> STRING
            Token.Kind.VOID -> VOID
            else -> throw IllegalArgumentException("Not a type token: $kind")
        }
    }
}

data class Program(val functions: List<FunDecl>) : AstNode
