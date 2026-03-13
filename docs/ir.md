# kgen IR Reference

The kgen IR is a typed, SSA-form intermediate representation with two tiers of
instructions: **low-level** operations for native backends (x86-64, ARM64, RISC-V)
and **high-level** operations for managed backends (JVM, WASM). A single `Module`
can contain both; lowering passes convert high-level ops to low-level equivalents
before native code generation.

**Instruction count**: 177 instruction types across both tiers, plus a generic
`Intrinsic` escape hatch and `InlineAsm` for target-specific needs. For
comparison, LLVM IR has ~67 core opcodes but offloads much of its functionality
to hundreds of intrinsics (`llvm.sadd.with.overflow`, `llvm.memcpy`, etc.).
kgen folds these directly into the instruction set.

## Table of Contents

- [Quick Start](#quick-start)
- [Building IR from an AST](#building-ir-from-an-ast)
- [Type System](#type-system)
- [Values](#values)
- [Constants](#constants)
- [Instruction Reference: Low-Level](#instruction-reference-low-level)
- [Instruction Reference: High-Level](#instruction-reference-high-level)
- [Module Structure](#module-structure)
- [Enums Reference](#enums-reference)
- [DSL Builders](#dsl-builders)
- [Imperative Builder (IrBuilder)](#imperative-builder-irbuilder)
- [Printer](#printer)
- [Verifier](#verifier)
- [Binary Serialization](#binary-serialization)

---

## Quick Start

```kotlin
import org.kgen.ir.*

val mod = module("example") {
    function("add", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32) {
        block("entry") {
            val result = add(param(0), param(1))
            ret(result)
        }
    }
}

println(IrPrinter.print(mod))
val result = IrVerifier.verify(mod)
```

Output:
```
module "example"

define i32 @add(i32 %a, i32 %b) {
entry:
  %0 = add i32 %a, %b
  ret i32 %0
}
```

---

## Building IR from an AST

Most consumers of kgen will be translating some form of high-level AST into
IR. kgen provides two builder APIs optimized for different use cases:

### DSL Builders (tests, hand-written IR, simple cases)

The DSL is great when you know the structure of the IR upfront:

```kotlin
val mod = module("test") {
    function("add", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32) {
        block("entry") {
            val result = add(param(0), param(1))
            ret(result)
        }
    }
}
```

### IrBuilder (compiler frontends, AST-to-IR translation)

Real compiler frontends discover control flow as they walk the AST.
`IrBuilder` provides LLVM IRBuilder-style insertion point semantics:

```kotlin
class MyCompiler(val ast: ProgramNode) {
    private val ir = IrBuilder("my_module")

    fun compile(): Module {
        ir.targetTriple = "x86_64-unknown-linux-gnu"

        // Declare external functions
        ir.declareFunction("printf", listOf(Param("fmt", Type.OpaquePointer)), Type.I32, isVarArg = true)

        // Translate each function
        for (func in ast.functions) {
            translateFunction(func)
        }
        return ir.build()
    }

    private fun translateFunction(node: FunctionNode) {
        val params = ir.createFunction(node.name,
            node.params.map { it.name to mapType(it.type) },
            mapType(node.returnType))

        val entry = ir.appendBlock("entry")
        ir.positionAtEnd(entry)

        // Translate the body
        val result = translateExpr(node.body)
        ir.ret(result)

        ir.finalizeFunction()
    }

    private fun translateExpr(node: ExprNode): Value = when (node) {
        is IntLiteral -> i32(node.value)
        is BinaryOp -> {
            val lhs = translateExpr(node.left)
            val rhs = translateExpr(node.right)
            when (node.op) {
                "+" -> ir.add(lhs, rhs)
                "-" -> ir.sub(lhs, rhs)
                "*" -> ir.mul(lhs, rhs)
                "/" -> ir.sdiv(lhs, rhs)
                else -> error("Unknown op: ${node.op}")
            }
        }
        is IfExpr -> translateIf(node)
        is VarRef -> loadVariable(node.name)
        // ...
    }

    private fun translateIf(node: IfExpr): Value {
        val cond = translateExpr(node.condition)

        val thenBb = ir.appendBlock("if.then")
        val elseBb = ir.appendBlock("if.else")
        val mergeBb = ir.appendBlock("if.merge")

        ir.condBr(cond, thenBb, elseBb)

        // Then branch
        ir.positionAtEnd(thenBb)
        val thenVal = translateExpr(node.thenExpr)
        ir.br(mergeBb)
        val thenFrom = ir.getInsertBlock()!!

        // Else branch
        ir.positionAtEnd(elseBb)
        val elseVal = translateExpr(node.elseExpr)
        ir.br(mergeBb)
        val elseFrom = ir.getInsertBlock()!!

        // Merge
        ir.positionAtEnd(mergeBb)
        return ir.phi(thenVal.type, listOf(thenVal to thenFrom, elseVal to elseFrom))
    }
}
```

### Handling Mutable Variables (alloca pattern)

SSA form requires each value to have a single definition. For source languages
with mutable variables, use the **alloca pattern** — allocate stack space for
each variable, then load/store through it. A later `mem2reg` optimization pass
will promote these to SSA registers:

```kotlin
// Translating: var x = 0; x = x + 1; return x

val xPtr = ir.alloca(Type.I32)            // stack slot for x
ir.store(i32(0), xPtr)                    // x = 0

val xVal = ir.load(Type.I32, xPtr)        // load x
val incremented = ir.add(xVal, i32(1))    // x + 1
ir.store(incremented, xPtr)               // x = x + 1

val result = ir.load(Type.I32, xPtr)      // load final value
ir.ret(result)
```

### Translating Loops

```kotlin
// Translating: while (i < n) { body; i++ }

val headerBb = ir.appendBlock("while.header")
val bodyBb = ir.appendBlock("while.body")
val exitBb = ir.appendBlock("while.exit")

ir.br(headerBb)

// Header: check condition
ir.positionAtEnd(headerBb)
val iVal = ir.load(Type.I32, iPtr)
val cond = ir.icmp(ICmpPredicate.SLT, iVal, nVal)
ir.condBr(cond, bodyBb, exitBb)

// Body
ir.positionAtEnd(bodyBb)
// ... translate body ...
val nextI = ir.add(iVal, i32(1))
ir.store(nextI, iPtr)
ir.br(headerBb)

// Exit
ir.positionAtEnd(exitBb)
```

### Translating Class Definitions

Use `ClassDefBuilder` for constructing class definitions:

```kotlin
val cls = classDef("Point") {
    extends("Object")
    implements("Serializable")

    field("x", Type.F64, visibility = MemberVisibility.PRIVATE)
    field("y", Type.F64, visibility = MemberVisibility.PRIVATE)

    staticField("ORIGIN", Type.ClassRef("Point"),
        visibility = MemberVisibility.PUBLIC, isFinal = true)

    constructor(MethodDef(
        name = "<init>",
        params = listOf(Param("x", Type.F64), Param("y", Type.F64)),
        returnType = Type.Void,
    ))

    method(MethodDef(
        name = "distanceTo",
        params = listOf(Param("other", Type.ClassRef("Point"))),
        returnType = Type.F64,
    ))
}

// Add to module
ir.addClass(cls)
```

---

## Type System

All types implement the sealed interface `Type`. Types are immutable data classes
and use structural equality.

### Integer Types

| Type | Bits | Constant Helper | IR Text |
|------|------|-----------------|---------|
| `Type.I1` | 1 | `i1(true/false)` | `i1` |
| `Type.I8` | 8 | `i8(42)` | `i8` |
| `Type.I16` | 16 | `i16(1000)` | `i16` |
| `Type.I32` | 32 | `i32(42)` | `i32` |
| `Type.I64` | 64 | `i64(123L)` | `i64` |
| `Type.I128` | 128 | `i128(0L)` | `i128` |
| `Type.IntN(24)` | N | — | `i24` |

`IntN` supports arbitrary bit widths. The named types (`I1`..`I128`) are
singleton objects for efficiency.

### Float Types

| Type | Bits | Format | Constant Helper | IR Text |
|------|------|--------|-----------------|---------|
| `Type.F16` | 16 | IEEE 754 half | `f16(1.0f)` | `f16` |
| `Type.BF16` | 16 | bfloat16 | `bf16(1.0f)` | `bf16` |
| `Type.F32` | 32 | IEEE 754 single | `f32(3.14f)` | `f32` |
| `Type.F64` | 64 | IEEE 754 double | `f64(2.718)` | `f64` |
| `Type.F80` | 80 | x87 extended | `f80(1.0)` | `f80` |
| `Type.F128` | 128 | IEEE 754 quad | `f128(1.0)` | `f128` |

### Pointer Types

| Type | Description | IR Text |
|------|-------------|---------|
| `Type.OpaquePointer` | Opaque pointer (like LLVM's `ptr`) | `ptr` |
| `Type.Pointer(Type.I32)` | Typed pointer to i32 | `ptr` |
| `Type.Pointer(Type.I32, 1)` | Pointer in address space 1 | `ptr addrspace(1)` |

### Reference Types (GC-managed)

| Type | Description | IR Text |
|------|-------------|---------|
| `Type.Reference(Type.I32, true)` | Nullable GC reference | `ref<i32>?` |
| `Type.Reference(Type.I32, false)` | Non-null GC reference | `ref<i32>` |
| `Type.WeakReference(Type.I32)` | Weak GC reference | `weakref<i32>` |

References are tracked by the garbage collector and cannot be cast to integers.

### Aggregate Types

| Type | Description | IR Text |
|------|-------------|---------|
| `Type.Array(Type.I8, 100)` | Fixed-size array | `[100 x i8]` |
| `Type.Vector(Type.F32, 4)` | SIMD vector | `<4 x f32>` |
| `Type.Vector(Type.F32, 4, scalable=true)` | Scalable vector (SVE) | `<vscale x 4 x f32>` |
| `Type.Struct(null, listOf(Type.I32, Type.F64))` | Anonymous struct | `{ i32, f64 }` |
| `Type.Struct("Point", fields, packed=true)` | Named packed struct | `%Point` |
| `Type.OpaqueStruct("Handle")` | Opaque struct | `%Handle` |
| `Type.Union(null, listOf(Type.I32, Type.F64))` | Untagged union | `union { i32 \| f64 }` |
| `Type.TaggedUnion("Option", tagType, variants)` | Discriminated union | `%Option` |

### Function Type

```kotlin
Type.Function(listOf(Type.I32, Type.I32), Type.I32)           // i32 (i32, i32)
Type.Function(listOf(Type.I32), Type.Void, vararg = true)     // void (i32, ...)
```

### Object-Oriented Types

| Type | IR Text |
|------|---------|
| `Type.ClassRef("MyClass")` | `class @MyClass` |
| `Type.InterfaceRef("Iterable")` | `interface @Iterable` |

### Generic Types

| Type | IR Text |
|------|---------|
| `Type.TypeParam("T", 0)` | `!T` |
| `Type.Parameterized(base, typeArgs)` | `Base<T1, T2>` |

### Other Types

| Type | IR Text |
|------|---------|
| `Type.Void` | `void` |
| `Type.Label` | `label` |
| `Type.Metadata` | `metadata` |
| `Type.Token` | `token` |
| `Type.Nullable(Type.I32)` | `i32?` |
| `Type.PlatformType("wasm.funcref")` | `platform(wasm.funcref)` |

---

## Values

| Value Kind | Description | IR Text |
|------------|-------------|---------|
| `Parameter(name, type, index)` | Function parameter | `%a` |
| `InstructionRef(name, type)` | SSA result | `%0` |
| `GlobalRef(name, type)` | Global variable | `@counter` |
| `FunctionRef(name, type)` | Function reference | `@add` |
| `BlockRef(label)` | Block reference | `%entry` |
| `Constant.*` | Compile-time constant | `42`, `null` |

### Parameter Attributes

| Attribute | Meaning |
|-----------|---------|
| `ZEROEXT` | Zero-extended to register width |
| `SIGNEXT` | Sign-extended to register width |
| `INREG` | Passed in register |
| `BYVAL` | Passed by value (stack copy) |
| `BYREF` | Passed by reference (no copy) |
| `SRET` | Struct return pointer |
| `NOALIAS` | No aliasing with other pointer args |
| `NOCAPTURE` | Pointer is not captured |
| `NONNULL` | Pointer is never null |
| `READONLY` | Only reads through pointer |
| `WRITEONLY` | Only writes through pointer |
| `READNONE` | Does not dereference pointer |
| `NEST` | Trampoline/closure context |
| `RETURNED` | Callee returns this value |

---

## Constants

### Scalar Constants

```kotlin
i1(true)              // Constant.Integer(1, 1)
i8(42)                // Constant.Integer(42, 8)
i16(1000)             // Constant.Integer(1000, 16)
i32(42)               // Constant.Integer(42, 32)
i64(123456789L)        // Constant.Integer(123456789, 64)
i128(0L)              // Constant.Integer(0, 128)
f16(1.0f)             // Constant.Float(1.0, HALF)
bf16(1.0f)            // Constant.Float(1.0, BFLOAT)
f32(3.14f)            // Constant.Float(3.14, SINGLE)
f64(2.718)            // Constant.Float(2.718, DOUBLE)
f80(1.0)              // Constant.Float(1.0, X87)
f128(1.0)             // Constant.Float(1.0, QUAD)
```

### Special Constants

| Constant | Type | Description |
|----------|------|-------------|
| `Constant.NullPtr` | `ptr` | Null pointer |
| `Constant.NullRef` | `ref<void>?` | Null reference |
| `Constant.Undef(type)` | any | Undefined value (each use may differ) |
| `Constant.Poison(type)` | any | Poison value (propagates through ops) |
| `Constant.ZeroInitializer(type)` | any | All bits zero |

**Undef vs. Poison**: `Undef` represents a value that could be anything —
each use may see a different bit pattern. `Poison` is stricter: it propagates
through any operation that uses it, triggering UB if it reaches a side-effecting
instruction. Poison is produced by operations with undefined results (e.g.,
overflow with `nsw`/`nuw` flags, exact division with remainder).

### Aggregate Constants

```kotlin
Constant.ArrayConst(Type.Array(Type.I32, 3), listOf(i32(1), i32(2), i32(3)))
Constant.VectorConst(Type.Vector(Type.F32, 4), listOf(f32(1f), f32(2f), f32(3f), f32(4f)))
Constant.StructConst(Type.Struct("Point", fields), listOf(f64(1.0), f64(2.0)))
Constant.StringConst("hello")              // null-terminated [6 x i8]
Constant.StringConst("raw", false)         // not null-terminated [3 x i8]
```

### Constant Expressions

```kotlin
Constant.GetElementPtr(type, base, indices, inBounds = true)
Constant.BitCast(targetType, value)
Constant.IntToPtr(ptrType, intConst)
Constant.PtrToInt(intType, ptrConst)
```

---

## Instruction Reference: Low-Level

Low-level instructions are consumed directly by native backends (x86-64, ARM64,
RISC-V). All instructions implement `sealed interface Instruction` with
`result: Value?` (null for void instructions).

Instructions that produce a value take a `dest: InstructionRef` as their first
parameter. The builder auto-generates these; you only deal with the returned
`Value`.

### Integer Arithmetic (9 instructions)

---

#### `Add` — Integer addition

```
%result = add [nuw] [nsw] <type> %lhs, %rhs
```

| Field | Type | Description |
|-------|------|-------------|
| `lhs` | `Value` | Left operand |
| `rhs` | `Value` | Right operand |
| `nuw` | `Boolean` | No unsigned wrap (result is poison on unsigned overflow) |
| `nsw` | `Boolean` | No signed wrap (result is poison on signed overflow) |

**Constraints**: `lhs` and `rhs` must have the same integer type.
**Result type**: Same as operands.
**Semantics**: Two's complement addition. If `nuw` is set and unsigned overflow
occurs, the result is poison. If `nsw` is set and signed overflow occurs, the
result is poison.

```kotlin
val sum = add(a, b)                        // %0 = add i32 %a, %b
val safe = add(a, b, nuw = true, nsw = true) // poison on any overflow
```

---

#### `Sub` — Integer subtraction

```
%result = sub [nuw] [nsw] <type> %lhs, %rhs
```

Same constraints and flags as `Add`. Computes `lhs - rhs`.

```kotlin
val diff = sub(a, b)
```

---

#### `Mul` — Integer multiplication

```
%result = mul [nuw] [nsw] <type> %lhs, %rhs
```

Same constraints and flags as `Add`. Computes `lhs * rhs`.

```kotlin
val prod = mul(a, b, nsw = true)
```

---

#### `UDiv` — Unsigned integer division

```
%result = udiv [exact] <type> %lhs, %rhs
```

| Field | Type | Description |
|-------|------|-------------|
| `exact` | `Boolean` | Result is poison if `lhs % rhs != 0` |

**Constraints**: Both operands same integer type. Division by zero is UB.
**Result type**: Same as operands.
**Semantics**: Unsigned division, result truncated toward zero.

```kotlin
val q = udiv(a, b)
val exact_q = udiv(a, b, exact = true)  // poison if a % b != 0
```

---

#### `SDiv` — Signed integer division

```
%result = sdiv [exact] <type> %lhs, %rhs
```

Same as `UDiv` but signed. Division by zero and `INT_MIN / -1` are both UB.

```kotlin
val q = sdiv(a, b)
```

---

#### `URem` — Unsigned integer remainder

```
%result = urem <type> %lhs, %rhs
```

**Constraints**: Both operands same integer type. Remainder by zero is UB.
**Result type**: Same as operands.
**Semantics**: `lhs - (lhs udiv rhs) * rhs`.

```kotlin
val r = urem(a, b)
```

---

#### `SRem` — Signed integer remainder

```
%result = srem <type> %lhs, %rhs
```

Same as `URem` but signed. Sign of result matches sign of dividend.

---

#### `Neg` — Integer negation

```
%result = neg <type> %operand
```

**Constraints**: Operand must be integer type.
**Result type**: Same as operand.
**Semantics**: Equivalent to `sub 0, %operand`. Note: negating `INT_MIN`
wraps to `INT_MIN` in two's complement.

```kotlin
val negated = neg(x)
```

---

### Overflow-Checked Arithmetic (6 instructions)

These return a struct `{result_type, i1}` where the `i1` indicates overflow.

---

#### `SAddOverflow` / `UAddOverflow` — Add with overflow detection

```
%result = sadd.overflow <type> %lhs, %rhs   ; signed
%result = uadd.overflow <type> %lhs, %rhs   ; unsigned
```

**Result type**: `{<type>, i1}` — the sum and an overflow flag.

```kotlin
val result = saddOverflow(a, b)
val sum = extractValue(result, 0)         // the actual sum
val overflow = extractValue(result, 1)    // i1: did overflow occur?
condBr(overflow, "overflow_handler", "continue")
```

---

#### `SSubOverflow` / `USubOverflow` — Subtract with overflow detection

Same structure as add overflow. Detects signed/unsigned underflow.

#### `SMulOverflow` / `UMulOverflow` — Multiply with overflow detection

Same structure. Detects when multiplication result doesn't fit in the type.

---

### Saturating Arithmetic (4 instructions)

Clamp results to the representable range instead of wrapping.

---

#### `SAddSat` / `UAddSat` — Saturating add

```
%result = sadd.sat <type> %lhs, %rhs
%result = uadd.sat <type> %lhs, %rhs
```

**Semantics**: `SAddSat` clamps to `[INT_MIN, INT_MAX]`. `UAddSat` clamps to
`[0, UINT_MAX]`.

```kotlin
val clamped = saddSat(i8(127), i8(1))    // result: 127 (not -128)
val uclamped = uaddSat(i8(255), i8(1))   // result: 255 (not 0)
```

---

#### `SSubSat` / `USubSat` — Saturating subtract

Same semantics with subtraction.

---

### Integer Min/Max/Abs (5 instructions)

---

#### `SMin` / `SMax` / `UMin` / `UMax` — Integer min/max

```
%result = smin <type> %lhs, %rhs
```

**Constraints**: Both operands same integer type.
**Semantics**: Returns the smaller/larger of the two operands, using
signed or unsigned comparison.

```kotlin
val minimum = smin(a, b)   // signed min
val maximum = umax(a, b)   // unsigned max
```

---

#### `Abs` — Integer absolute value

```
%result = abs <type> %operand [intmin_poison]
```

| Field | Type | Description |
|-------|------|-------------|
| `isIntMin` | `Boolean` | Result is poison when input is `INT_MIN` |

**Semantics**: Returns `|operand|`. When `isIntMin = true`, the backend
can use more efficient instructions that don't handle the `INT_MIN` case.

---

### Float Arithmetic (16 instructions)

---

#### `FAdd` / `FSub` / `FMul` / `FDiv` / `FRem` — Float binary arithmetic

```
%result = fadd [fast-math-flags] <type> %lhs, %rhs
%result = fsub [fast-math-flags] <type> %lhs, %rhs
%result = fmul [fast-math-flags] <type> %lhs, %rhs
%result = fdiv [fast-math-flags] <type> %lhs, %rhs
%result = frem [fast-math-flags] <type> %lhs, %rhs
```

**Constraints**: Both operands same float type.
**Result type**: Same as operands.
**Semantics**: IEEE 754 arithmetic. `FRem` computes the IEEE remainder
(sign of result matches dividend, unlike C `fmod`).

```kotlin
val sum = fadd(x, y)
val fast_sum = fadd(x, y, FastMathFlags.FAST)
val product = fmul(x, y, FastMathFlags(reassoc = true, allowContract = true))
```

#### Fast-Math Flags

| Flag | Text | Meaning |
|------|------|---------|
| `noNaNs` | `nnan` | Assume no NaN inputs/outputs |
| `noInfs` | `ninf` | Assume no infinity inputs/outputs |
| `noSignedZeros` | `nsz` | Treat -0.0 as +0.0 |
| `allowReciprocal` | `arcp` | Allow `x/y` → `x * (1/y)` |
| `allowContract` | `contract` | Allow fused operations (FMA) |
| `approxFunc` | `afn` | Allow approximate implementations |
| `reassoc` | `reassoc` | Allow reassociation |

Presets: `FastMathFlags.NONE` (strict IEEE), `FastMathFlags.FAST` (all flags).

---

#### `FNeg` — Float negation

```
%result = fneg [fast-math-flags] <type> %operand
```

**Semantics**: Flips the sign bit. `fneg(-0.0) = +0.0`.

---

#### `FAbs` — Float absolute value

```
%result = fabs <type> %operand
```

**Semantics**: Clears the sign bit. `fabs(-inf) = +inf`, `fabs(NaN)` = NaN
with sign bit cleared.

---

#### `FMA` — Fused multiply-add

```
%result = fma <type> %a, %b, %c
```

**Semantics**: Computes `a * b + c` with a single rounding step (more
accurate than separate multiply and add). Maps to hardware FMA instructions.

```kotlin
val result = fma(a, b, c)   // a*b + c, single rounding
```

---

#### `FMin` / `FMax` — IEEE 754 minimum/maximum

```
%result = fmin <type> %lhs, %rhs
%result = fmax <type> %lhs, %rhs
```

**Semantics**: IEEE 754-2019 minimum/maximum. If either operand is NaN,
returns the non-NaN operand. `fmin(-0.0, +0.0) = -0.0`.

---

#### `Sqrt` — Square root

```
%result = sqrt <type> %operand
```

**Semantics**: IEEE 754 square root. `sqrt(negative) = NaN`,
`sqrt(+inf) = +inf`, `sqrt(-0.0) = -0.0`.

---

#### `Ceil` / `Floor` / `Round` / `Trunc` — Rounding

```
%result = ceil <type> %operand     ; round toward +inf
%result = floor <type> %operand    ; round toward -inf
%result = round <type> %operand    ; round to nearest, ties away from zero
%result = trunc <type> %operand    ; round toward zero
```

**Note**: `Trunc` (float rounding) is `Instruction.Trunc`. Integer truncation
is `Instruction.IntTrunc`. In the builder, use `ftrunc()` for float rounding
and `trunc()` for integer truncation.

---

#### `CopySign` — Copy sign bit

```
%result = copysign <type> %magnitude, %sign
```

**Semantics**: Returns a value with the magnitude of `magnitude` and the sign
of `sign`.

---

### Bitwise Operations (10 instructions)

---

#### `And` / `Or` / `Xor` — Bitwise binary operations

```
%result = and <type> %lhs, %rhs
%result = or  <type> %lhs, %rhs
%result = xor <type> %lhs, %rhs
```

**Constraints**: Both operands same integer type.
**Result type**: Same as operands.

---

#### `Not` — Bitwise complement

```
%result = not <type> %operand
```

**Semantics**: One's complement. Equivalent to `xor %operand, -1`.

---

#### `Shl` — Shift left

```
%result = shl [nuw] [nsw] <type> %lhs, %rhs
```

**Semantics**: Shift `lhs` left by `rhs` bits, filling with zeros.
Shift amount must be less than bit width (UB otherwise).
`nuw`: result is poison if any shifted-out bits are non-zero.
`nsw`: result is poison if the sign bit changes.

---

#### `LShr` — Logical shift right

```
%result = lshr [exact] <type> %lhs, %rhs
```

**Semantics**: Shift right, filling with zeros (unsigned shift).
`exact`: result is poison if any shifted-out bits are non-zero.

---

#### `AShr` — Arithmetic shift right

```
%result = ashr [exact] <type> %lhs, %rhs
```

**Semantics**: Shift right, filling with the sign bit (signed shift).

---

#### `RotateLeft` / `RotateRight` — Bit rotation

```
%result = rotl <type> %value, %amount
%result = rotr <type> %value, %amount
```

**Semantics**: Circular shift. Bits shifted out one end re-enter the other.

---

### Bit Manipulation (5 instructions)

---

#### `Ctlz` — Count leading zeros

```
%result = ctlz <type> %operand [zero_poison]
```

| Field | Type | Description |
|-------|------|-------------|
| `isZeroPoison` | `Boolean` | Result is poison when operand is 0 |

**Result type**: Same as operand.
**Semantics**: Returns the number of leading zero bits. For an N-bit type,
returns N when the operand is 0 (unless `isZeroPoison` is set).

---

#### `Cttz` — Count trailing zeros

Same structure as `Ctlz`. Counts from the least significant bit.

---

#### `Ctpop` — Population count

```
%result = ctpop <type> %operand
```

**Semantics**: Returns the number of set (1) bits.

---

#### `BSwap` — Byte swap

```
%result = bswap <type> %operand
```

**Constraints**: Operand bit width must be a multiple of 16.
**Semantics**: Reverses the byte order. `bswap(0x12345678) = 0x78563412`.

---

#### `BitReverse` — Bit reversal

```
%result = bitreverse <type> %operand
```

**Semantics**: Reverses the order of all bits.

---

### Comparison (2 instructions)

---

#### `ICmp` — Integer comparison

```
%result = icmp <predicate> <type> %lhs, %rhs
```

**Result type**: `i1`.

| Predicate | Meaning |
|-----------|---------|
| `EQ` / `NE` | Equal / Not equal |
| `UGT` / `UGE` / `ULT` / `ULE` | Unsigned comparisons |
| `SGT` / `SGE` / `SLT` / `SLE` | Signed comparisons |

```kotlin
val isEqual = icmp(ICmpPredicate.EQ, a, b)
val isLess = icmp(ICmpPredicate.SLT, a, b)    // signed less than
```

---

#### `FCmp` — Float comparison

```
%result = fcmp [fast-math-flags] <predicate> <type> %lhs, %rhs
```

**Result type**: `i1`.

| Predicate | Meaning |
|-----------|---------|
| `FALSE` / `TRUE` | Always false / always true |
| `OEQ` / `OGT` / `OGE` / `OLT` / `OLE` / `ONE` | Ordered (false if NaN) |
| `UEQ` / `UGT` / `UGE` / `ULT` / `ULE` / `UNE` | Unordered (true if NaN) |
| `ORD` / `UNO` | Neither is NaN / Either is NaN |

**Ordered vs. Unordered**: Ordered comparisons return false if either operand
is NaN. Unordered comparisons return true if either is NaN.

```kotlin
val lt = fcmp(FCmpPredicate.OLT, x, y)    // x < y, false if NaN
val isNaN = fcmp(FCmpPredicate.UNO, x, x) // true if x is NaN
```

---

### Memory (14 instructions)

---

#### `Alloca` — Stack allocation

```
%ptr = alloca <type> [, <count>] [, align <n>]
```

| Field | Type | Description |
|-------|------|-------------|
| `allocType` | `Type` | Type to allocate |
| `numElements` | `Value?` | Array size (null = single element) |
| `align` | `Int?` | Alignment in bytes |

**Result type**: `Pointer(allocType)`.
**Semantics**: Allocates space on the stack frame. Automatically freed on
function return. Each `alloca` in a loop allocates additional stack space
(use `stackSave`/`stackRestore` to avoid stack growth).

```kotlin
val ptr = alloca(Type.I32)                     // one i32
val arr = alloca(Type.I32, numElements = i32(10))  // 10 i32s
val aligned = alloca(Type.I32, align = 16)     // 16-byte aligned
```

---

#### `Load` — Memory load

```
%val = load [volatile] <type>, ptr %ptr [, align <n>] [, <ordering>]
```

| Field | Type | Description |
|-------|------|-------------|
| `loadType` | `Type` | Type of value to load |
| `ptr` | `Value` | Pointer to load from |
| `align` | `Int?` | Alignment in bytes |
| `volatile` | `Boolean` | Cannot be reordered/eliminated |
| `ordering` | `AtomicOrdering?` | Atomic ordering (null = non-atomic) |

**Result type**: `loadType`.
**Semantics**: Reads a value from memory. Volatile loads cannot be optimized
away or reordered with other volatile operations.

```kotlin
val v = load(Type.I32, ptr)
val atomic = load(Type.I32, ptr, ordering = AtomicOrdering.ACQUIRE)
```

---

#### `Store` — Memory store

```
store [volatile] <type> %val, ptr %ptr [, align <n>] [, <ordering>]
```

**Result**: None (void instruction).
**Semantics**: Writes a value to memory.

```kotlin
store(i32(42), ptr)
store(value, ptr, ordering = AtomicOrdering.RELEASE)
```

---

#### `GetElementPtr` (GEP) — Element pointer computation

```
%ptr = getelementptr [inbounds] <type>, ptr %base, <idx1> [, <idx2>, ...]
```

**Result type**: Pointer.
**Semantics**: Computes a pointer to a sub-element **without accessing memory**.
The first index offsets from the base pointer. Subsequent indices drill into
aggregate types.

```kotlin
// Array element: &base[i]
val elemPtr = gep(Type.I32, basePtr, i32(i))

// Struct field: &ptr->fields[2]
val fieldPtr = gep(structType, ptr, i32(0), i32(2))

// The first i32(0) means "don't offset the base pointer"
// The second i32(2) means "third field of the struct"
```

`inBounds = true` (default): result is poison if the computed pointer is outside
the allocated object. This enables alias analysis optimizations.

---

#### `Fence` — Memory fence

```
fence [syncscope] <ordering>
```

**Result**: None.
**Semantics**: Memory barrier. Orders memory operations before and after the
fence according to the specified ordering.

---

#### `CmpXchg` — Compare and exchange

```
%result = cmpxchg [weak] [volatile] ptr %ptr, <type> %cmp, <type> %new,
          <success_ordering>, <failure_ordering>
```

**Result type**: `{<type>, i1}` — old value and success flag.
**Semantics**: Atomically reads `*ptr`. If it equals `cmp`, stores `new`.
Returns `{old_value, was_equal}`. `weak = true` allows spurious failures.

```kotlin
val result = cmpxchg(ptr, expected, desired,
    AtomicOrdering.SEQ_CST, AtomicOrdering.MONOTONIC)
val oldVal = extractValue(result, 0)
val success = extractValue(result, 1)
```

---

#### `AtomicRMW` — Atomic read-modify-write

```
%old = atomicrmw [volatile] <op> ptr %ptr, <type> %val, <ordering>
```

**Result type**: Same as `val` (the old value).

| Op | Operation |
|----|-----------|
| `XCHG` | Exchange |
| `ADD` / `SUB` | Integer add/subtract |
| `AND` / `NAND` / `OR` / `XOR` | Bitwise operations |
| `MAX` / `MIN` | Signed max/min |
| `UMAX` / `UMIN` | Unsigned max/min |
| `FADD` / `FSUB` / `FMAX` / `FMIN` | Float operations |

```kotlin
val old = atomicRMW(AtomicRMWOp.ADD, ptr, i32(1), AtomicOrdering.SEQ_CST)
```

---

#### `MemCpy` / `MemSet` / `MemMove` — Bulk memory operations

```
memcpy  [volatile] ptr %dst, ptr %src, <len_type> %len
memset  [volatile] ptr %dst, i8 %val, <len_type> %len
memmove [volatile] ptr %dst, ptr %src, <len_type> %len
```

**Result**: None.
- `MemCpy`: copies `len` bytes. Source and destination must not overlap.
- `MemSet`: fills `len` bytes with `val`.
- `MemMove`: copies `len` bytes. Handles overlapping regions correctly.

---

#### `Prefetch` — Cache prefetch hint

```
prefetch ptr %addr, <rw>, <locality>, <cache_type>
```

- `rw`: 0 = read, 1 = write
- `locality`: 0 (no locality) to 3 (high locality)
- `cacheType`: 0 = instruction cache, 1 = data cache

---

### Stack / Lifetime (4 instructions)

#### `StackSave` / `StackRestore` — Save/restore stack pointer

```
%sp = stacksave
stackrestore ptr %sp
```

Useful for dynamic stack allocation (e.g., `alloca` in loops).

#### `LifetimeStart` / `LifetimeEnd` — Lifetime markers

```
lifetime.start ptr %p, <size>
lifetime.end   ptr %p, <size>
```

Hints for stack slot reuse optimization.

---

### Conversions (13 instructions)

---

#### Integer Conversions

| Instruction | Builder | From → To | Operation |
|-------------|---------|-----------|-----------|
| `IntTrunc` | `trunc(v, type)` | wider int → narrower int | Drop high bits |
| `ZExt` | `zext(v, type)` | narrower int → wider int | Fill with zeros |
| `SExt` | `sext(v, type)` | narrower int → wider int | Fill with sign bit |

```kotlin
val wide = sext(i8_val, Type.I64)    // i8 → i64, sign-extended
val narrow = trunc(i32_val, Type.I8) // i32 → i8, drop top 24 bits
val zero = zext(i8_val, Type.I32)    // i8 → i32, zero-extended
```

#### Float Conversions

| Instruction | Builder | From → To | Operation |
|-------------|---------|-----------|-----------|
| `FPTrunc` | `fptrunc(v, type)` | wider float → narrower float | Round to fit |
| `FPExt` | `fpext(v, type)` | narrower float → wider float | Extend precision |

```kotlin
val single = fptrunc(f64_val, Type.F32)  // f64 → f32 (may lose precision)
val dbl = fpext(f32_val, Type.F64)       // f32 → f64 (exact)
```

#### Float ↔ Integer Conversions

| Instruction | Builder | From → To | Operation |
|-------------|---------|-----------|-----------|
| `FPToUI` | `fptoui(v, type)` | float → unsigned int | Truncate toward zero |
| `FPToSI` | `fptosi(v, type)` | float → signed int | Truncate toward zero |
| `UIToFP` | `uitofp(v, type)` | unsigned int → float | Nearest representable |
| `SIToFP` | `sitofp(v, type)` | signed int → float | Nearest representable |

```kotlin
val fp = sitofp(int_val, Type.F64)    // signed i32 → f64
val i = fptosi(fp_val, Type.I32)      // f64 → i32 (truncates toward zero)
```

#### Pointer Conversions

| Instruction | Builder | From → To |
|-------------|---------|-----------|
| `PtrToInt` | `ptrtoint(v, type)` | pointer → integer |
| `IntToPtr` | `inttoptr(v, type)` | integer → pointer |
| `BitCast` | `bitcast(v, type)` | any → same-size type |
| `AddrSpaceCast` | `addrspacecast(v, type)` | pointer → pointer (different address space) |

---

### Control Flow (8 instructions)

All are **terminators** — they must be the last instruction in a block.

---

#### `Ret` — Return

```
ret <type> %val
ret void
```

**Constraints**: Return value type must match function's return type.

```kotlin
ret(i32(0))   // return 0
ret()          // return void
```

---

#### `Br` — Unconditional branch

```
br label %target
```

```kotlin
br("next_block")
```

---

#### `CondBr` — Conditional branch

```
br i1 %cond, label %true, label %false
```

**Constraints**: `condition` must be `i1`.

```kotlin
condBr(cond, "then", "else")
```

---

#### `Switch` — Multi-way branch

```
switch <type> %val, label %default [
  <type> <case1>, label %dest1
  <type> <case2>, label %dest2
]
```

**Constraints**: Case values must be constants with the same type as `val`.

```kotlin
switch(value, "default", listOf(
    i32(0) to "case_zero",
    i32(1) to "case_one",
))
```

---

#### `IndirectBr` — Indirect branch

```
indirectbr ptr %addr, [label %t1, label %t2, ...]
```

**Semantics**: Branch to address. Target list is for the verifier/optimizer.

---

#### `Unreachable` — Mark unreachable code

```
unreachable
```

**Semantics**: If execution reaches this point, behavior is undefined. Used
after `noreturn` calls or in impossible branches.

---

#### `Trap` / `DebugTrap` — Abort / breakpoint

```
trap
debugtrap
```

`Trap` aborts immediately. `DebugTrap` hits a debugger breakpoint.

---

### Calls (3 instructions)

---

#### `Call` — Function call

```
[%result =] [tail|musttail|notail] call [cconv] <ret_type> @func(<args>)
```

| Field | Type | Description |
|-------|------|-------------|
| `function` | `Value` | Function to call (ref or pointer) |
| `args` | `List<Value>` | Arguments |
| `returnType` | `Type` | Return type |
| `callingConv` | `CallingConvention` | Calling convention |
| `tailCall` | `TailCallKind` | Tail call optimization |

```kotlin
val result = call("add", listOf(a, b), Type.I32)   // direct call
call("printf", listOf(fmt), Type.Void)              // void call
call(funcPtr, listOf(arg), Type.I32,                // indirect call
    tailCall = TailCallKind.MUSTTAIL)
```

---

#### `Invoke` — Call with exception handling

```
[%result =] invoke [cconv] <ret_type> @func(<args>)
    to label %normal unwind label %unwind
```

**Semantics**: Like `Call`, but if the callee throws, control goes to
`unwindDest` instead of `normalDest`. This is a **terminator**.

---

#### `CallBr` — Call with multiple successors

```
[%result =] callbr <ret_type> @func(<args>)
    to label %fallthrough [label %indirect1, ...]
```

Used with inline assembly that may branch to multiple destinations.

---

### Calling Conventions

| Convention | Description |
|------------|-------------|
| `C` | Standard C (default) |
| `FAST` | Optimized for speed |
| `COLD` | Optimized for code size |
| `TAIL` | Guaranteed TCO |
| `SWIFT` | Swift ABI |
| `WIN64` | Windows x64 |
| `SYSV64` | System V AMD64 (Linux/macOS) |
| `AAPCS` / `AAPCS_VFP` | ARM |
| `WASM` | WebAssembly |
| `GHC` / `HHVM` | Haskell / HipHop VM |

---

### Varargs (4 instructions)

#### `VAStart` / `VAEnd` / `VACopy` / `VAArg`

```
va_start ptr %ap
va_end   ptr %ap
va_copy  ptr %dst, ptr %src
%val = va_arg ptr %ap, <type>
```

```kotlin
function("variadic", listOf(Param("n", Type.I32)), Type.Void, isVarArg = true) {
    block("entry") {
        val ap = alloca(Type.OpaquePointer)
        vaStart(ap)
        val arg1 = vaArg(ap, Type.I32)
        vaEnd(ap)
        ret()
    }
}
```

---

### Exception Handling — Native (7 instructions)

#### Itanium/GNU model

`LandingPad` / `Resume` — landing pad receives exceptions, resume continues
unwinding.

#### Windows SEH model

`CatchSwitch` / `CatchPad` / `CleanupPad` / `CatchRet` / `CleanupRet` —
structured exception handling with funclets.

```kotlin
// Itanium model
block("lpad") {
    val exc = landingPad(excType, listOf(
        LandingPadClause.Catch(typeInfoPtr),
    ), cleanup = true)
    resume(exc)
}

// Windows SEH model
block("dispatch") {
    val cs = catchSwitch(null, listOf("handler"), "cleanup")
}
block("handler") {
    val cp = catchPad(cs, listOf(typeInfoPtr))
    catchRet(cp, "continue")
}
```

---

### SSA (3 instructions)

---

#### `Phi` — Value merge at control flow join

```
%result = phi <type> [%val1, %block1], [%val2, %block2], ...
```

**Constraints**: Must appear at the beginning of a block (before non-phi
instructions). All incoming values must have the same type. Each predecessor
block must have exactly one entry.

```kotlin
block("merge") {
    val result = phi(Type.I32, listOf(
        i32(0) to "entry",
        nextI to "loop_body"
    ))
}
```

---

#### `Select` — Conditional value

```
%result = select i1 %cond, <type> %true_val, <type> %false_val
```

Like a ternary operator. Unlike `CondBr`, this is not a terminator —
both values are computed and one is selected.

```kotlin
val max = select(icmp(ICmpPredicate.SGT, a, b), a, b)
```

---

#### `Freeze` — Stabilize undef/poison

```
%result = freeze <type> %val
```

**Semantics**: If `val` is undef or poison, returns an arbitrary but fixed
value. If `val` is a normal value, returns it unchanged. Prevents poison
propagation.

---

### Vector Operations (5 instructions)

---

#### `ExtractElement` — Extract scalar from vector

```
%elem = extractelement <vec_type> %vec, i32 %idx
```

**Result type**: Element type of the vector.

---

#### `InsertElement` — Insert scalar into vector

```
%new_vec = insertelement <vec_type> %vec, <elem_type> %elem, i32 %idx
```

---

#### `ShuffleVector` — Shuffle lanes from two vectors

```
%result = shufflevector <vec_type> %v1, <vec_type> %v2, <mask>
```

**Result type**: Vector with `len(mask)` lanes.

```kotlin
// Interleave two 4-element vectors into an 8-element vector
val interleaved = shuffleVector(v1, v2, listOf(0, 4, 1, 5, 2, 6, 3, 7))

// Reverse a 4-element vector
val reversed = shuffleVector(v, v, listOf(3, 2, 1, 0))
```

---

#### `Splat` — Broadcast scalar to vector

```
%vec = splat <elem_type> %scalar, <vec_type>
```

```kotlin
val broadcast = splat(f32(1.0f), Type.Vector(Type.F32, 4))  // <1.0, 1.0, 1.0, 1.0>
```

---

#### `VectorReduce` — Reduce vector to scalar

```
%scalar = reduce.<op> <vec_type> %vec
```

| Op | Description |
|----|-------------|
| `ADD` / `MUL` | Sum / product of all elements |
| `AND` / `OR` / `XOR` | Bitwise reduction |
| `SMIN` / `SMAX` / `UMIN` / `UMAX` | Integer min/max |
| `FADD` / `FMUL` / `FMIN` / `FMAX` | Float reduction |

```kotlin
val sum = vectorReduce(VectorReduceOp.ADD, intVec)
val hmax = vectorReduce(VectorReduceOp.FMAX, floatVec)
```

---

### Aggregate Operations (2 instructions)

#### `ExtractValue` — Extract from struct/array

```
%field = extractvalue <agg_type> %agg, <idx1> [, <idx2>, ...]
```

```kotlin
val y = extractValue(point, 1)         // second field
val nested = extractValue(outer, 0, 2) // field 2 of field 0
```

#### `InsertValue` — Insert into struct/array

```
%new = insertvalue <agg_type> %agg, <elem_type> %elem, <idx1> [, ...]
```

---

### Debug / Metadata (3 instructions)

#### `DebugLoc` — Source location

```kotlin
debugLoc(42, 10, "main.c")
debugLoc(42, 10, "main.c", inlinedAt = "caller.c:100")
```

#### `DebugValue` / `DebugDeclare` — Variable tracking

```kotlin
debugValue("x", someValue)
debugDeclare("arr", allocaPtr)
```

---

### Optimizer Hints (2 instructions)

#### `Assume` — Assert condition for optimizer

```kotlin
assume(icmp(ICmpPredicate.SGT, len, i32(0)))  // optimizer can assume len > 0
```

#### `Expect` — Branch prediction hint

```kotlin
val likely = expect(flag, i1(true))  // flag is expected to be true
```

---

### Inline Assembly / Intrinsics (2 instructions)

#### `InlineAsm` — Inline assembly

```kotlin
val result = inlineAsm(
    assembly = "mov \$1, \$0",
    constraints = "=r,r",
    args = listOf(input),
    returnType = Type.I32,
    sideEffects = true,
    dialect = AsmDialect.ATT    // or INTEL
)
```

#### `Intrinsic` — Target-specific operation

```kotlin
val result = intrinsic("llvm.x86.sse2.pmovmskb.128", listOf(vec), Type.I32)
```

---

## Instruction Reference: High-Level

High-level instructions target managed runtimes (JVM, WASM GC). They are
consumed directly by managed backends and lowered to low-level equivalents
for native backends.

### Object Lifecycle (3 instructions)

#### `NewObject` — Allocate object

```
%obj = new <ClassName> [<TypeArgs>]
```

```kotlin
val obj = newObject("Point")
val generic = newObject("List", typeArgs = listOf(Type.I32))
```

#### `NewArray` — Allocate managed array

```
%arr = newarray <elem_type>, <size_type> %size
```

```kotlin
val arr = newArray(Type.I32, i32(10))
```

#### `NewMultiArray` — Allocate multi-dimensional array

```
%arr = newmultiarray <elem_type>, [%dim1, %dim2, ...]
```

---

### Field Access (4 instructions)

#### `GetField` / `PutField` — Instance fields

```
%val = getfield <Class>.<field>: <type>, %obj
putfield <Class>.<field>: <type>, %obj, %val
```

```kotlin
val x = getField(point, "Point", "x", Type.F64)
putField(point, "Point", "x", Type.F64, f64(5.0))
```

#### `GetStatic` / `PutStatic` — Static fields

```
%val = getstatic <Class>.<field>: <type>
putstatic <Class>.<field>: <type>, %val
```

---

### Method Dispatch (6 instructions)

#### `VirtualCall` — Virtual method dispatch

```
%r = virtualcall %obj.<Class>::<method>(<args>): <ret_type>
```

Dispatches through the vtable. The actual method called depends on the
runtime type of `obj`.

#### `InterfaceCall` — Interface method dispatch

```
%r = interfacecall %obj.<Interface>::<method>(<args>): <ret_type>
```

#### `SpecialCall` — Direct call (super/private/init)

```
%r = specialcall %obj.<Class>::<method>(<args>): <ret_type>
```

No virtual dispatch. Used for `super` calls, private methods, and constructors.

#### `StaticCall` — Static method call

```
%r = staticcall <Class>::<method>(<args>): <ret_type>
```

#### `DynamicCall` — invokedynamic-style dispatch

```
%r = dynamiccall <bootstrap> "<name>" (<args>): <ret_type>
```

Used for lambdas, string concatenation, and other dynamic dispatch in JVM.

#### `ConstructorCall` — Constructor invocation

```
constructorcall %obj.<Class>::<init>(<args>)
```

**Result**: None (void). Initializes `obj` in place.

---

### Type Operations (3 instructions)

#### `InstanceOf` — Type check

```
%b = instanceof %obj, <Type>    ; result: i1
```

#### `CheckCast` — Type cast

```
%c = checkcast %obj to <Type>   ; throws on failure
```

#### `TypeId` — Runtime type identifier

```
%t = typeid %obj                ; result: i32
```

---

### Managed Array Operations (3 instructions)

```kotlin
val len = arrayLength(arr)                    // %0 = arraylength %arr
val elem = arrayGet(arr, i32(0), Type.I32)    // %1 = arrayget i32 %arr, 0
arraySet(arr, i32(0), i32(42), Type.I32)      // arrayset i32 %arr, 0, 42
```

Includes bounds checking on managed backends (unlike raw load/store).

---

### Monitor / Synchronization (2 instructions)

```kotlin
monitorEnter(lockObj)    // acquire object monitor
monitorExit(lockObj)     // release object monitor
```

---

### Exception Handling — Managed (2 instructions)

#### `Throw` — Throw exception (terminator)

```kotlin
throwException(exceptionObj)
```

#### `TryCatchRegion` — Try/catch/finally

```kotlin
tryCatch("try_body", listOf(
    CatchHandler(Type.ClassRef("IOException"), "io_handler"),
    CatchHandler(Type.ClassRef("Exception"), "generic_handler"),
), finallyBlock = "cleanup")
```

---

### Boxing / Unboxing (2 instructions)

```kotlin
val boxed = box(i32(42), Type.ClassRef("Integer"))
val unboxed = unbox(boxed, Type.I32)
```

---

### Closures / Lambdas (2 instructions)

```kotlin
val closure = closureCreate(funcRef, captures = listOf(x, y), closureType)
val result = closureInvoke(closure, args = listOf(i32(5)), Type.I32)
```

---

### Tagged Unions / ADTs (4 instructions)

```kotlin
val some = constructVariant(optionType, "Some", listOf(i32(42)))

tagSwitch(optionVal, listOf(
    "Some" to "handle_some",
    "None" to "handle_none",
))

// In handle_some:
val inner = getVariantField(optionVal, "Some", 0, Type.I32)
val tag = getTag(optionVal)
```

---

### GC Integration (3 instructions)

```kotlin
val obj = gcAlloc(Type.ClassRef("Node"))
gcRoot(localPtr, null)    // register stack root with GC
gcSafepoint()             // allow GC to run here
```

---

### Reference Counting (3 instructions)

```kotlin
refRetain(sharedObj)      // increment ref count
refRelease(sharedObj)     // decrement (may dealloc at 0)
val count = refCount(obj) // get current count
```

---

### Coroutines (6 instructions)

```kotlin
val frameSize = coroSize()
val mem = call("malloc", listOf(frameSize), Type.OpaquePointer)!!
val handle = coroBegin(coroId, mem)

// Suspend point
val state = coroSuspend(null, isFinal = false)
// state: 0 = resumed, 1 = cleanup

// Resume from another context
coroResume(handle)

// Cleanup
coroEnd(handle, unwind = false)
coroDestroy(handle)
```

---

## Module Structure

### Module

```kotlin
data class Module(
    val name: String,
    val targetTriple: String?,           // "x86_64-unknown-linux-gnu"
    val dataLayout: String?,             // memory layout spec
    val functions: List<IrFunction>,
    val globals: List<Global>,
    val structs: List<StructDef>,
    val classes: List<ClassDef>,
    val interfaces: List<InterfaceDef>,
    val enums: List<EnumDef>,
    val aliases: List<TypeAlias>,
    val metadata: Map<String, MetadataValue>,
    val sourceFile: String?,
    val targetFeatures: Set<String>,     // "+sse4.2", "+neon"
    val globalCtors: List<GlobalCtor>,
    val globalDtors: List<GlobalCtor>,
    val ifuncs: List<IFunc>,
    val comdats: List<ComdatDef>,
    val moduleInlineAsm: String?,
    val moduleFlags: Map<String, ModuleFlagValue>,
)
```

### IrFunction

```kotlin
data class IrFunction(
    val name: String,
    val params: List<Parameter>,
    val returnType: Type,
    val blocks: List<BasicBlock>,         // empty for declarations
    val isExternal: Boolean = false,
    val linkage: Linkage = Linkage.EXTERNAL,
    val visibility: Visibility = Visibility.DEFAULT,
    val callingConv: CallingConvention = CallingConvention.C,
    val attributes: Set<FnAttribute> = emptySet(),
    val section: String? = null,
    val align: Int? = null,
    val gc: String? = null,
    val isVarArg: Boolean = false,
    val typeParams: List<TypeParamDef> = emptyList(),
    val personality: FunctionRef? = null,
    val comdat: String? = null,
    val prefixData: Constant? = null,
    val prologueData: Constant? = null,
    val unnamedAddr: UnnamedAddr = UnnamedAddr.NONE,
    val dllStorageClass: DLLStorageClass = DLLStorageClass.NONE,
)
```

### BasicBlock

```kotlin
data class BasicBlock(
    val label: String,                    // unique within function
    val instructions: List<Instruction>,  // must end with terminator
)
```

### Global

```kotlin
data class Global(
    val name: String,
    val type: Type,
    val initializer: Constant? = null,
    val isConstant: Boolean = false,
    val linkage: Linkage = Linkage.EXTERNAL,
    val visibility: Visibility = Visibility.DEFAULT,
    val threadLocal: ThreadLocalMode? = null,
    val section: String? = null,
    val align: Int? = null,
    val addressSpace: Int = 0,
)
```

### ClassDef / InterfaceDef / EnumDef / StructDef

See source files `Module.kt` for full data class definitions.

---

## Enums Reference

### Linkage

| Value | Description |
|-------|-------------|
| `EXTERNAL` | Visible everywhere (default) |
| `INTERNAL` | Module-internal (like C `static`) |
| `PRIVATE` | Like internal, name may be discarded |
| `WEAK` / `WEAK_ODR` | May be overridden |
| `LINKONCE` / `LINKONCE_ODR` | May be discarded if unused |
| `COMMON` | Tentative definition |
| `APPENDING` | Append to array |
| `AVAILABLE_EXTERNALLY` | Available for inlining only |

### Visibility

`DEFAULT`, `HIDDEN`, `PROTECTED`

### FnAttribute

| Attribute | Description |
|-----------|-------------|
| `NOUNWIND` | Never throws |
| `NORETURN` | Never returns |
| `NOINLINE` / `ALWAYSINLINE` | Inlining control |
| `OPTNONE` / `OPTSIZE` | Optimization control |
| `READONLY` / `READNONE` / `WRITEONLY` / `ARGMEMONLY` | Memory effects |
| `WILLRETURN` | Always terminates |
| `COLD` / `HOT` | Frequency hints |
| `NAKED` | No prologue/epilogue |
| `SANITIZE_ADDRESS` / `SANITIZE_MEMORY` / `SANITIZE_THREAD` | Sanitizers |
| `STACK_PROTECT` / `STACK_PROTECT_STRONG` / `NO_STACK_PROTECTOR` | Stack protection |

### AtomicOrdering

| Ordering | Guarantee |
|----------|-----------|
| `UNORDERED` | Atomicity only |
| `MONOTONIC` | Total order per location |
| `ACQUIRE` | Reads see paired release's writes |
| `RELEASE` | Writes visible after paired acquire |
| `ACQ_REL` | Both acquire and release |
| `SEQ_CST` | Total global order |

### ThreadLocalMode

`GENERAL_DYNAMIC`, `LOCAL_DYNAMIC`, `INITIAL_EXEC`, `LOCAL_EXEC`

### Other Enums

- **`UnnamedAddr`**: `NONE`, `UNNAMED_ADDR`, `LOCAL_UNNAMED_ADDR`
- **`DLLStorageClass`**: `NONE`, `DLL_IMPORT`, `DLL_EXPORT`
- **`ComdatSelectionKind`**: `ANY`, `EXACT_MATCH`, `LARGEST`, `NO_DUPLICATES`, `SAME_SIZE`
- **`ClassVisibility`**: `PUBLIC`, `PACKAGE_PRIVATE`, `PROTECTED`, `PRIVATE`
- **`MemberVisibility`**: `PUBLIC`, `PROTECTED`, `PACKAGE_PRIVATE`, `PRIVATE`
- **`AnnotationRetention`**: `SOURCE`, `CLASS`, `RUNTIME`
- **`TypeVariance`**: `INVARIANT`, `COVARIANT`, `CONTRAVARIANT`

---

## DSL Builders

### Module

```kotlin
val mod = module("my_module") {
    targetTriple("x86_64-unknown-linux-gnu")
    dataLayout("e-m:e-p270:32:32-i64:64-f80:128-n8:16:32:64-S128")
    sourceFile("main.c")
    targetFeature("+sse4.2")

    struct("Point", listOf(Param("x", Type.F64), Param("y", Type.F64)))
    global("counter", Type.I32, i32(0))
    global("pi", Type.F64, f64(3.14159), isConstant = true)

    function("main", emptyList(), Type.I32) {
        block("entry") { ret(i32(0)) }
    }
    function("puts", listOf(Param("s", Type.OpaquePointer)), Type.I32, isExternal = true)
}
```

### Class Definition

```kotlin
val cls = classDef("Point") {
    extends("Object")
    implements("Serializable")
    field("x", Type.F64)
    field("y", Type.F64)
    method(MethodDef("getX", emptyList(), Type.F64))
}
```

### Interface Definition

```kotlin
val iface = interfaceDef("Drawable") {
    method(MethodDef("draw", listOf(Param("canvas", Type.ClassRef("Canvas"))), Type.Void, isAbstract = true))
    constant("MAX_Z", Type.I32, i32(1000))
}
```

### Flexible Block Construction

`FunctionBuilder` also supports imperative block creation within the DSL:

```kotlin
function("flexible", listOf(Param("x", Type.I32)), Type.I32) {
    val entry = createBlock("entry")
    val thenBb = createBlock("then")
    val elseBb = createBlock("else")

    entry.condBr(icmp(ICmpPredicate.SGT, param(0), i32(0)), "then", "else")
    thenBb.ret(i32(1))
    elseBb.ret(i32(0))

    addBlock(entry)
    addBlock(thenBb)
    addBlock(elseBb)
}
```

---

## Imperative Builder (IrBuilder)

`IrBuilder` provides LLVM IRBuilder-style semantics for compiler frontends.
Instead of nested DSL blocks, you create blocks and switch insertion points
freely.

```kotlin
val ir = IrBuilder("my_module")
ir.targetTriple = "x86_64-unknown-linux-gnu"

// Declare external functions
ir.declareFunction("printf", listOf(Param("fmt", Type.OpaquePointer)), Type.I32, isVarArg = true)

// Add globals
val counter = ir.addGlobal("counter", Type.I32, i32(0))

// Create a function
val params = ir.createFunction("main", listOf(Param("argc", Type.I32)), Type.I32)

// Create and position at blocks
val entry = ir.appendBlock("entry")
ir.positionAtEnd(entry)

val cmp = ir.icmp(ICmpPredicate.SGT, params[0], i32(0))
val thenBb = ir.appendBlock("then")
val elseBb = ir.appendBlock("else")
ir.condBr(cmp, thenBb, elseBb)

ir.positionAtEnd(thenBb)
ir.ret(i32(1))

ir.positionAtEnd(elseBb)
ir.ret(i32(0))

ir.finalizeFunction()

val module = ir.build()
```

### Key API

| Method | Description |
|--------|-------------|
| `createFunction(name, params, retType, ...)` | Start function definition, returns params |
| `declareFunction(name, params, retType)` | External function declaration |
| `finalizeFunction()` | Finish current function |
| `appendBlock(label)` | Create a new block, returns label |
| `positionAtEnd(label)` | Set insertion point to end of block |
| `getInsertBlock()` | Get current block label |
| `param(index)` | Get function parameter |
| `addGlobal(name, type, ...)` | Add global variable, returns GlobalRef |
| `addStruct(name, fields, ...)` | Add struct type |
| `addClass(cls)` | Add class definition |
| `build()` | Finalize and return Module |

All instruction methods from `InstructionEmitter` are available directly on
`IrBuilder` (`add`, `sub`, `load`, `store`, `call`, etc.).

---

## Printer

```kotlin
val text = IrPrinter.print(module)

// Static utilities
IrPrinter.typeStr(Type.I32)           // "i32"
IrPrinter.valStr(someValue)           // "%0", "@name", "42"
IrPrinter.constStr(someConstant)      // "42", "null", "zeroinitializer"
```

---

## Verifier

```kotlin
val result = IrVerifier.verify(module)
if (!result.isValid) {
    for (error in result.errors) {
        println("${error.function}/${error.block}: ${error.message}")
    }
}
```

### Checks

- Every block ends with a terminator; no terminators mid-block
- Phi nodes only at block start
- Type consistency for all operations
- Branch targets reference existing blocks
- Return type matches function signature
- Call arguments match callee signature
- No duplicate function/global/struct names
- External functions have no body
- Conversion type categories are correct

---

## Binary Serialization

```kotlin
val serializer = IrSerializer()
val bytes = serializer.serialize(module)
val restored = serializer.deserialize(bytes)
```

- **Magic**: `0x4B47454E` ("KGEN")
- **Format**: Version-tagged binary with length-prefixed strings
- **Guarantee**: Round-trip preserves all IR information

---

## Instruction Summary

| Category | Count | Instructions |
|----------|-------|-------------|
| Integer arithmetic | 9 | Add, Sub, Mul, UDiv, SDiv, URem, SRem, Neg |
| Overflow-checked | 6 | S/UAddOverflow, S/USubOverflow, S/UMulOverflow |
| Saturating | 4 | S/UAddSat, S/USubSat |
| Min/Max/Abs | 5 | SMin, SMax, UMin, UMax, Abs |
| Float arithmetic | 16 | FAdd, FSub, FMul, FDiv, FRem, FNeg, FAbs, FMA, FMin, FMax, Sqrt, Ceil, Floor, Round, Trunc, CopySign |
| Bitwise | 10 | And, Or, Xor, Not, Shl, LShr, AShr, RotateLeft, RotateRight |
| Bit manipulation | 5 | Ctlz, Cttz, Ctpop, BSwap, BitReverse |
| Comparison | 2 | ICmp, FCmp |
| Memory | 14 | Alloca, Load, Store, GEP, Fence, CmpXchg, AtomicRMW, MemCpy, MemSet, MemMove, Prefetch, StackSave, StackRestore, LifetimeStart/End |
| Conversions | 13 | IntTrunc, ZExt, SExt, FPTrunc, FPExt, FPToUI, FPToSI, UIToFP, SIToFP, PtrToInt, IntToPtr, BitCast, AddrSpaceCast |
| Control flow | 8 | Ret, Br, CondBr, Switch, IndirectBr, Unreachable, Trap, DebugTrap |
| Calls | 3 | Call, Invoke, CallBr |
| Varargs | 4 | VAStart, VAEnd, VACopy, VAArg |
| Exception handling (native) | 7 | LandingPad, Resume, CatchSwitch, CatchPad, CleanupPad, CatchRet, CleanupRet |
| SSA | 3 | Phi, Select, Freeze |
| Vector | 5 | ExtractElement, InsertElement, ShuffleVector, Splat, VectorReduce |
| Aggregate | 2 | ExtractValue, InsertValue |
| Debug | 3 | DebugLoc, DebugValue, DebugDeclare |
| Hints | 2 | Assume, Expect |
| Assembly/Intrinsic | 2 | InlineAsm, Intrinsic |
| **Low-level total** | **~122** | |
| Object lifecycle | 3 | NewObject, NewArray, NewMultiArray |
| Field access | 4 | GetField, PutField, GetStatic, PutStatic |
| Method dispatch | 6 | VirtualCall, InterfaceCall, SpecialCall, StaticCall, DynamicCall, ConstructorCall |
| Type operations | 3 | InstanceOf, CheckCast, TypeId |
| Managed arrays | 3 | ArrayGet, ArraySet, ArrayLength |
| Monitors | 2 | MonitorEnter, MonitorExit |
| Exception handling (managed) | 2 | Throw, TryCatchRegion |
| Boxing | 2 | Box, Unbox |
| Closures | 2 | ClosureCreate, ClosureInvoke |
| Tagged unions | 4 | ConstructVariant, GetTag, GetVariantField, TagSwitch |
| GC | 3 | GCAlloc, GCSafepoint, GCRoot |
| Ref counting | 3 | RefRetain, RefRelease, RefCount |
| Coroutines | 6 | CoroBegin, CoroEnd, CoroSuspend, CoroResume, CoroDestroy, CoroSize |
| **High-level total** | **~43** | |
| **Grand total** | **~165** | |
