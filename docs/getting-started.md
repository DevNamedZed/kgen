# Getting Started with kgen

kgen is a compiler infrastructure toolkit for the JVM. It provides a typed, SSA-form
intermediate representation (IR) with backends for WASM, JVM, x86-64, ARM64, RISC-V,
and MSIL. Every subsystem is independently useful as a library.

## Maven Coordinates

```kotlin
implementation("org.kgen:kgen:0.1.0")
```

## Quick Example

```kotlin
import org.kgen.ir.*
import org.kgen.ir.build.*
import org.kgen.ir.text.*
import org.kgen.ir.verify.*
import org.kgen.ir.target.Target

val ir = IrBuilder("example", Target.wasm())

val params = ir.createFunction("add",
    listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
ir.positionAtEnd(ir.appendBlock("entry"))
val sum = ir.add(params[0], params[1])
ir.ret(sum)
ir.finalizeFunction()

val mod = ir.build()
println(IrPrinter.print(mod))
println(IrVerifier.verify(mod))  // "Verification passed"
```

## Building IR

There's one builder: `IrBuilder`. It's the module. You add functions, classes, globals to it,
then call `build()` to get a `Module`.

For function bodies, you pick one of two styles:

### FunctionScope — structured, no SSA needed

Call `ir.function()` to get a `FunctionScope`. It gives you mutable variables,
structured control flow, and named comparisons. No basic blocks, no phi nodes,
no alloca/load/store.

```kotlin
val ir = IrBuilder("my_module")

val fn = ir.function("factorial", listOf(Param("n", Type.I32)), Type.I32)
val result = fn.variable(i32(1))
val i = fn.variable(i32(1))

fn.whileLoop(condition = { le(get(i), param(0)) }) {
    set(result, mul(get(result), get(i)))
    set(i, add(get(i), i32(1)))
}

fn.ret(fn.get(result))
fn.end()
```

### createFunction — SSA-level, full control

Call `ir.createFunction()` for LLVM IRBuilder-style basic blocks and insertion points.
Use when you need phi nodes, specific block layout, or are porting LLVM-style code.

```kotlin
val params = ir.createFunction("max", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)

ir.positionAtEnd(ir.appendBlock("entry"))
val cmp = ir.icmp(ICmpPredicate.SGT, params[0], params[1])
ir.condBr(cmp, "then", "else")

ir.positionAtEnd(ir.appendBlock("then"))
ir.ret(params[0])

ir.positionAtEnd(ir.appendBlock("else"))
ir.ret(params[1])

ir.finalizeFunction()
```

## Translating an AST

The primary use case. You have an AST, you walk it, you call builder methods.

```kotlin
class Compiler(private val ast: Program) {
    private val ir = IrBuilder("output")
    private val vars = mutableMapOf<String, VarRef>()

    fun compile(): Module {
        ir.targetTriple = "wasm32-unknown-unknown"

        // External functions
        ir.declareFunction("print_int", listOf(Param("n", Type.I32)), Type.Void)

        // Translate each function
        for (func in ast.functions) translateFunction(func)

        return ir.build()
    }

    private fun translateFunction(node: FuncNode) {
        val fn = ir.function(
            node.name,
            node.params.map { Param(it.name, mapType(it.type)) },
            mapType(node.returnType),
        )

        vars.clear()
        // Map params to named variables for easy lookup
        for ((i, param) in node.params.withIndex()) {
            vars[param.name] = fn.variable(fn.param(i))
        }

        for (stmt in node.body) translateStmt(fn, stmt)
        fn.end()
    }

    private fun translateStmt(fn: FunctionScope, stmt: Stmt) {
        when (stmt) {
            is VarDecl -> {
                vars[stmt.name] = fn.variable(translateExpr(fn, stmt.init))
            }
            is Assign -> {
                fn.set(vars[stmt.name]!!, translateExpr(fn, stmt.value))
            }
            is Return -> {
                fn.ret(translateExpr(fn, stmt.value))
            }
            is IfStmt -> {
                val cond = translateExpr(fn, stmt.condition)
                if (stmt.elseBranch != null) {
                    fn.ifThenElse(cond,
                        thenBody = { for (s in stmt.body) translateStmt(fn, s) },
                        elseBody = { for (s in stmt.elseBranch) translateStmt(fn, s) },
                    )
                } else {
                    fn.ifThen(cond) { for (s in stmt.body) translateStmt(fn, s) }
                }
            }
            is WhileStmt -> {
                fn.whileLoop(condition = { translateExpr(fn, stmt.condition) }) {
                    for (s in stmt.body) translateStmt(fn, s)
                }
            }
            is BreakStmt -> fn.breakOut()
            is ExprStmt -> translateExpr(fn, stmt.expr)
        }
    }

    private fun translateExpr(fn: FunctionScope, expr: Expr): Value = when (expr) {
        is IntLit -> i32(expr.value)
        is FloatLit -> f64(expr.value)
        is VarExpr -> fn.get(vars[expr.name]!!)
        is BinaryOp -> {
            val l = translateExpr(fn, expr.left)
            val r = translateExpr(fn, expr.right)
            when (expr.op) {
                "+" -> fn.add(l, r)
                "-" -> fn.sub(l, r)
                "*" -> fn.mul(l, r)
                "/" -> fn.div(l, r)
                "<" -> fn.lt(l, r)
                "<=" -> fn.le(l, r)
                ">" -> fn.gt(l, r)
                ">=" -> fn.ge(l, r)
                "==" -> fn.eq(l, r)
                "!=" -> fn.ne(l, r)
                else -> error("Unknown operator: ${expr.op}")
            }
        }
        is CallExpr -> {
            val args = expr.args.map { translateExpr(fn, it) }
            fn.call(expr.name, args, mapType(expr.returnType)) ?: error("void used as value")
        }
    }
}
```

## FunctionScope Reference

**Variables** — mutable, no SSA:
```kotlin
val x = fn.variable(i32(0))    // declare + initialize
val v = fn.get(x)              // read current value
fn.set(x, fn.add(v, i32(1)))   // write new value
```

**Control flow** — structured, no blocks:
```kotlin
fn.ifThen(cond) { ... }
fn.ifThenElse(cond, thenBody = { ... }, elseBody = { ... })
fn.whileLoop(condition = { ... }) { ... }
fn.doWhile(body = { ... }, condition = { ... })
fn.forLoop(init = { ... }, condition = { ... }, update = { ... }) { ... }
fn.breakOut()
fn.continueOn()
```

**Comparisons** — named, no enums:
```kotlin
fn.eq(a, b)   fn.ne(a, b)           // equal, not equal
fn.lt(a, b)   fn.le(a, b)           // less than, less or equal (signed)
fn.gt(a, b)   fn.ge(a, b)           // greater than, greater or equal (signed)
fn.ult(a, b)  fn.uge(a, b)          // unsigned variants
fn.flt(a, b)  fn.fge(a, b)          // float variants (ordered)
```

**Conversions** — auto-selecting:
```kotlin
fn.intCast(val, Type.I64)       // auto trunc or sign-extend
fn.uintCast(val, Type.I64)      // auto trunc or zero-extend
fn.floatCast(val, Type.F64)     // auto fptrunc or fpext
fn.toFloat(intVal, Type.F64)    // int → float
fn.toInt(floatVal, Type.I32)    // float → int
```

**Escape hatch** — full power when needed:
```kotlin
fn.raw {
    // Inside here, 'this' is IrBuilder — full SSA access
    val vec = splat(f32(1.0f), Type.Vector(Type.F32, 4))
    val sum = vectorReduce(VectorReduceOp.FADD, vec)
}
```

## OOP

Classes use the existing `classDef` / `interfaceDef` DSL for definition,
and `FunctionScope` methods for runtime operations:

```kotlin
val ir = IrBuilder("oop_example")

ir.addClass(classDef("Point") {
    field("x", Type.F64)
    field("y", Type.F64)
})

val fn = ir.function("demo", emptyList(), Type.Void)
val p = fn.newObject("Point")
fn.putField(p, "Point", "x", Type.F64, f64(3.0))
fn.putField(p, "Point", "y", Type.F64, f64(4.0))

val x = fn.getField(p, "Point", "x", Type.F64)
val y = fn.getField(p, "Point", "y", Type.F64)
// distance = sqrt(x*x + y*y)
val dist = fn.sqrt(fn.fadd(fn.fmul(x, x), fn.fmul(y, y)))
fn.retVoid()
fn.end()
```

## Architecture

Each target backend provides up to three components:

1. **CodeGenerator** — IR Module → binary
2. **Assembler\<I\>** — target instructions → binary
3. **Disassembler\<I\>** — binary → target instructions

Backends register via `TargetRegistry` + `ServiceLoader`.

## Single Module

kgen ships as a single Maven artifact — `org.kgen:kgen`. All packages (IR, code generators,
binary formats, JIT, reflection, runtime) are included. No transitive dependencies.

```kotlin
implementation("org.kgen:kgen:0.1.0")
```

## Next

- [IR Reference](ir.md) — Full instruction set, 177 opcodes
- [Architecture](architecture.md) — Pipeline, design decisions
