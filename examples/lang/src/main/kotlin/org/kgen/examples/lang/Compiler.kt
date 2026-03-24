package org.kgen.examples.lang

import org.kgen.ir.*
import org.kgen.ir.build.ModuleBuilder
import org.kgen.ir.target.Target

/**
 * Compiles a [Program] AST into a kgen IR [Module].
 *
 * Each function becomes an IrFunction. Native-annotated functions get
 * compiled to machine code via the JIT engine; managed functions could
 * target JVM bytecode (future).
 */
class Compiler(private val target: Target = Target.x86_64()) {

    fun compile(program: Program): Module {
        val ir = ModuleBuilder(moduleName(program), target)

        // First pass: declare all extern functions
        val declaredExterns = mutableSetOf<String>()
        for (fn in program.functions) {
            if (fn.mode == FunMode.EXTERN) {
                val params = fn.params.map { Param(it.name, langTypeToIr(it.type)) }
                val retType = langTypeToIr(fn.returnType)
                ir.declareFunction(fn.name, params, retType)
                declaredExterns.add(fn.name)
            }
        }

        // Auto-declare string helper externs if string literals are used
        if (hasStringLiterals(program) || hasStringType(program)) {
            val i64 = Type.I64
            if ("new_array" !in declaredExterns)
                ir.declareFunction("new_array", listOf(Param("size", i64)), i64)
            if ("array_set" !in declaredExterns)
                ir.declareFunction("array_set", listOf(Param("arr", i64), Param("idx", i64), Param("val", i64)), i64)
            if ("str_len" !in declaredExterns)
                ir.declareFunction("str_len", listOf(Param("s", i64)), i64)
            if ("str_get" !in declaredExterns)
                ir.declareFunction("str_get", listOf(Param("s", i64), Param("idx", i64)), i64)
            if ("str_eq" !in declaredExterns)
                ir.declareFunction("str_eq", listOf(Param("s1", i64), Param("s2", i64)), i64)
            if ("str_concat" !in declaredExterns)
                ir.declareFunction("str_concat", listOf(Param("s1", i64), Param("s2", i64)), i64)
        }

        // Second pass: compile all functions with bodies
        for (fn in program.functions) {
            if (fn.mode == FunMode.EXTERN) continue
            compileFunction(ir, fn, program)
        }

        return ir.build()
    }

    private fun moduleName(program: Program): String {
        val firstName = program.functions.firstOrNull()?.name ?: "module"
        return "${firstName}_module"
    }

    private fun compileFunction(ir: ModuleBuilder, fn: FunDecl, program: Program) {
        val params = fn.params.map { Param(it.name, langTypeToIr(it.type)) }
        val retType = langTypeToIr(fn.returnType)

        ir.createFunction(fn.name, params, retType)
        ir.appendBlock("entry")

        val ctx = FunctionContext(ir, fn, program)

        // Create alloca-like storage for parameters (using parameter refs directly)
        for ((i, param) in fn.params.withIndex()) {
            ctx.locals[param.name] = Parameter(param.name, langTypeToIr(param.type), i)
        }

        val body = fn.body!!

        // Hoist all mutable variable allocas to the entry block
        hoistAllocas(ctx, body)

        compileBlock(ctx, body)

        // Add implicit return if the block doesn't end with one
        if (!ctx.hasTerminator) {
            if (fn.returnType == LangType.VOID) {
                ir.ret()
            } else {
                ir.ret(defaultValue(fn.returnType))
            }
        }

        ir.finalizeFunction()
    }

    private fun hoistAllocas(ctx: FunctionContext, block: Block) {
        for (stmt in block.stmts) {
            when (stmt) {
                is VarDecl -> if (stmt.mutable) {
                    val type = stmt.type?.let { langTypeToIr(it) } ?: Type.I64
                    val alloca = ctx.ir.alloca(type)
                    ctx.allocas[stmt.name] = alloca
                    ctx.locals[stmt.name] = alloca
                }
                is IfStmt -> {
                    hoistAllocas(ctx, stmt.thenBlock)
                    stmt.elseBlock?.let { hoistAllocas(ctx, it) }
                }
                is WhileStmt -> hoistAllocas(ctx, stmt.body)
                else -> {}
            }
        }
    }

    private fun compileBlock(ctx: FunctionContext, block: Block) {
        for (stmt in block.stmts) {
            if (ctx.hasTerminator) break
            compileStmt(ctx, stmt)
        }
    }

    private fun compileStmt(ctx: FunctionContext, stmt: Stmt) {
        when (stmt) {
            is VarDecl -> compileVarDecl(ctx, stmt)
            is Assignment -> compileAssignment(ctx, stmt)
            is ReturnStmt -> compileReturn(ctx, stmt)
            is ExprStmt -> compileExpr(ctx, stmt.expr)
            is IfStmt -> compileIf(ctx, stmt)
            is WhileStmt -> compileWhile(ctx, stmt)
        }
    }

    private fun compileVarDecl(ctx: FunctionContext, decl: VarDecl) {
        val value = compileExpr(ctx, decl.value)
        if (decl.mutable) {
            // Alloca was already hoisted to entry block
            val alloca = ctx.allocas[decl.name]
                ?: throw LangError("Missing alloca for var ${decl.name}", 0, 0)
            ctx.ir.store(value, alloca)
        } else {
            ctx.locals[decl.name] = value
        }
    }

    private fun compileAssignment(ctx: FunctionContext, assign: Assignment) {
        val value = compileExpr(ctx, assign.value)
        val alloca = ctx.allocas[assign.name]
            ?: throw LangError("Cannot assign to immutable variable: ${assign.name}", 0, 0)
        ctx.ir.store(value, alloca)
    }

    private fun compileReturn(ctx: FunctionContext, ret: ReturnStmt) {
        if (ret.value != null) {
            val value = compileExpr(ctx, ret.value)
            ctx.ir.ret(value)
        } else {
            ctx.ir.ret()
        }
        ctx.hasTerminator = true
    }

    private fun compileIf(ctx: FunctionContext, ifStmt: IfStmt) {
        val condition = compileExpr(ctx, ifStmt.condition)

        val thenLabel = ctx.freshLabel("then")
        val elseLabel = if (ifStmt.elseBlock != null) ctx.freshLabel("else") else null
        val mergeLabel = ctx.freshLabel("merge")

        ctx.ir.condBr(condition, thenLabel, elseLabel ?: mergeLabel)

        // Then block
        ctx.ir.appendBlock(thenLabel)
        ctx.hasTerminator = false
        compileBlock(ctx, ifStmt.thenBlock)
        if (!ctx.hasTerminator) {
            ctx.ir.br(mergeLabel)
        }

        // Else block
        if (ifStmt.elseBlock != null && elseLabel != null) {
            ctx.ir.appendBlock(elseLabel)
            ctx.hasTerminator = false
            compileBlock(ctx, ifStmt.elseBlock)
            if (!ctx.hasTerminator) {
                ctx.ir.br(mergeLabel)
            }
        }

        // Merge
        ctx.ir.appendBlock(mergeLabel)
        ctx.hasTerminator = false
    }

    private fun compileWhile(ctx: FunctionContext, whileStmt: WhileStmt) {
        val condLabel = ctx.freshLabel("while.cond")
        val bodyLabel = ctx.freshLabel("while.body")
        val exitLabel = ctx.freshLabel("while.exit")

        ctx.ir.br(condLabel)

        // Condition
        ctx.ir.appendBlock(condLabel)
        val condition = compileExpr(ctx, whileStmt.condition)
        ctx.ir.condBr(condition, bodyLabel, exitLabel)

        // Body
        ctx.ir.appendBlock(bodyLabel)
        ctx.hasTerminator = false
        compileBlock(ctx, whileStmt.body)
        if (!ctx.hasTerminator) {
            ctx.ir.br(condLabel)
        }

        // Exit
        ctx.ir.appendBlock(exitLabel)
        ctx.hasTerminator = false
    }

    private fun compileExpr(ctx: FunctionContext, expr: Expr): Value {
        return when (expr) {
            is IntLiteral -> Constant.I64(expr.value)
            is FloatLiteral -> Constant.F64(expr.value)
            is BoolLiteral -> Constant.I1(expr.value)
            is StringLiteral -> compileStringLiteral(ctx, expr)
            is Ident -> loadVariable(ctx, expr.name)
            is BinaryExpr -> compileBinary(ctx, expr)
            is UnaryExpr -> compileUnary(ctx, expr)
            is CallExpr -> compileCall(ctx, expr)
        }
    }

    private fun loadVariable(ctx: FunctionContext, name: String): Value {
        val alloca = ctx.allocas[name]
        if (alloca != null) {
            // Mutable variable — load from alloca
            val type = (alloca.type as Type.Pointer).pointee
            return ctx.ir.load(type, alloca)
        }
        return ctx.locals[name]
            ?: throw LangError("Undefined variable: $name", 0, 0)
    }

    private fun compileBinary(ctx: FunctionContext, expr: BinaryExpr): Value {
        val left = compileExpr(ctx, expr.left)
        val right = compileExpr(ctx, expr.right)
        val ir = ctx.ir

        return when (expr.op) {
            BinaryOp.ADD -> if (isFloat(left)) ir.fadd(left, right) else ir.add(left, right)
            BinaryOp.SUB -> if (isFloat(left)) ir.fsub(left, right) else ir.sub(left, right)
            BinaryOp.MUL -> if (isFloat(left)) ir.fmul(left, right) else ir.mul(left, right)
            BinaryOp.DIV -> if (isFloat(left)) ir.fdiv(left, right) else ir.sdiv(left, right)
            BinaryOp.MOD -> ir.srem(left, right)
            BinaryOp.EQ -> ir.icmp(ICmpPredicate.EQ, left, right)
            BinaryOp.NEQ -> ir.icmp(ICmpPredicate.NE, left, right)
            BinaryOp.LT -> if (isFloat(left)) ir.fcmp(FCmpPredicate.OLT, left, right) else ir.icmp(ICmpPredicate.SLT, left, right)
            BinaryOp.GT -> if (isFloat(left)) ir.fcmp(FCmpPredicate.OGT, left, right) else ir.icmp(ICmpPredicate.SGT, left, right)
            BinaryOp.LTE -> if (isFloat(left)) ir.fcmp(FCmpPredicate.OLE, left, right) else ir.icmp(ICmpPredicate.SLE, left, right)
            BinaryOp.GTE -> if (isFloat(left)) ir.fcmp(FCmpPredicate.OGE, left, right) else ir.icmp(ICmpPredicate.SGE, left, right)
            BinaryOp.AND -> ir.and(left, right)
            BinaryOp.OR -> ir.or(left, right)
        }
    }

    private fun compileUnary(ctx: FunctionContext, expr: UnaryExpr): Value {
        val operand = compileExpr(ctx, expr.operand)
        return when (expr.op) {
            UnaryOp.NEG -> if (isFloat(operand)) ctx.ir.fsub(Constant.F64(0.0), operand) else ctx.ir.sub(Constant.I64(0), operand)
            UnaryOp.NOT -> ctx.ir.xor(operand, Constant.I1(true))
        }
    }

    private fun compileCall(ctx: FunctionContext, expr: CallExpr): Value {
        val args = expr.args.map { compileExpr(ctx, it) }
        val calledFn = ctx.fn // current function context
        // Look up function return type from the program
        val retType = lookupReturnType(ctx, expr.name)
        val fnType = Type.Function(args.map { it.type }, retType)
        val fnRef = GlobalRef(expr.name, fnType)
        return ctx.ir.call(fnRef, args, retType) ?: Constant.I64(0)
    }

    private fun compileStringLiteral(ctx: FunctionContext, expr: StringLiteral): Value {
        val chars = expr.value
        val ir = ctx.ir
        val i64 = Type.I64
        val fnType1 = Type.Function(listOf(i64), i64)
        val fnType3 = Type.Function(listOf(i64, i64, i64), i64)

        // new_array(len)
        val arr = ir.call(GlobalRef("new_array", fnType1), listOf(Constant.I64(chars.length.toLong())), i64)
            ?: throw LangError("Failed to call new_array", 0, 0)

        // array_set(arr, i, charCode) for each character
        for ((i, ch) in chars.withIndex()) {
            ir.call(
                GlobalRef("array_set", fnType3),
                listOf(arr, Constant.I64(i.toLong()), Constant.I64(ch.code.toLong())),
                i64
            )
        }
        return arr
    }

    private fun lookupReturnType(ctx: FunctionContext, name: String): Type {
        val decl = ctx.program.functions.firstOrNull { it.name == name }
            ?: throw LangError("Unknown function: $name", 0, 0)
        return langTypeToIr(decl.returnType)
    }

    private fun isFloat(v: Value): Boolean =
        v.type == Type.F32 || v.type == Type.F64

    private fun defaultValue(type: LangType): Value = when (type) {
        LangType.INT -> Constant.I64(0)
        LangType.FLOAT -> Constant.F64(0.0)
        LangType.BOOL -> Constant.I1(false)
        LangType.STRING -> Constant.I64(0)
        LangType.VOID -> Constant.I64(0)
    }

    private fun hasStringLiterals(program: Program): Boolean {
        fun checkExpr(expr: Expr): Boolean = when (expr) {
            is StringLiteral -> true
            is BinaryExpr -> checkExpr(expr.left) || checkExpr(expr.right)
            is UnaryExpr -> checkExpr(expr.operand)
            is CallExpr -> expr.args.any { checkExpr(it) }
            else -> false
        }
        fun checkStmt(stmt: Stmt): Boolean = when (stmt) {
            is VarDecl -> checkExpr(stmt.value)
            is Assignment -> checkExpr(stmt.value)
            is ReturnStmt -> stmt.value?.let { checkExpr(it) } ?: false
            is ExprStmt -> checkExpr(stmt.expr)
            is IfStmt -> checkExpr(stmt.condition) || stmt.thenBlock.stmts.any { checkStmt(it) }
                || (stmt.elseBlock?.stmts?.any { checkStmt(it) } ?: false)
            is WhileStmt -> checkExpr(stmt.condition) || stmt.body.stmts.any { checkStmt(it) }
        }
        return program.functions.any { fn ->
            fn.body?.stmts?.any { checkStmt(it) } ?: false
        }
    }

    private fun hasStringType(program: Program): Boolean =
        program.functions.any { fn ->
            fn.returnType == LangType.STRING || fn.params.any { it.type == LangType.STRING }
        }

    companion object {
        fun langTypeToIr(type: LangType): Type = when (type) {
            LangType.INT -> Type.I64
            LangType.FLOAT -> Type.F64
            LangType.BOOL -> Type.I1
            LangType.STRING -> Type.Pointer(Type.I8)
            LangType.VOID -> Type.Void
        }
    }
}

private class FunctionContext(
    val ir: ModuleBuilder,
    val fn: FunDecl,
    val program: Program,
) {
    val locals = mutableMapOf<String, Value>()
    val allocas = mutableMapOf<String, Value>()
    var hasTerminator = false
    private var labelCounter = 0

    fun freshLabel(prefix: String): String = "${prefix}_${labelCounter++}"
}
