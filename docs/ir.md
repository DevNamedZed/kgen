# kgen IR Reference

The kgen IR is a typed, SSA-form intermediate representation with two tiers of
instructions: **low-level** operations for native backends (x86-64, ARM64, RISC-V)
and **high-level** operations for managed backends (JVM, WASM). A single `Module`
can contain both; lowering passes convert high-level ops to low-level equivalents
before native code generation.

**Instruction count**: 206 instruction types across 19 categories, plus a generic
`Intrinsic` escape hatch and `InlineAsm` for target-specific needs. For
comparison, LLVM IR has ~67 core opcodes but offloads much of its functionality
to hundreds of intrinsics (`llvm.sadd.with.overflow`, `llvm.memcpy`, etc.).
kgen folds these directly into the instruction set.

**Code generation**: All 206 instructions, their data classes, visitor interfaces,
instruction set interfaces, and emitter implementations are generated from YAML
specs in `generator/src/main/resources/ir/`. Adding a new instruction means adding
YAML and running `gradlew :generator:runIr`.

## Table of Contents

- [Quick Start](#quick-start)
- [Building IR from an AST](#building-ir-from-an-ast)
- [InstructionBuilder: Scoped Emission](#instructionbuilder-scoped-emission)
- [Type System](#type-system)
- [Values](#values)
- [Constants](#constants)
- [Category System](#category-system)
- [Effect System](#effect-system)
- [Capability System](#capability-system)
- [Pipeline Phases](#pipeline-phases)
- [Instruction Reference: Low-Level](#instruction-reference-low-level)
- [Instruction Reference: High-Level](#instruction-reference-high-level)
- [Instruction Reference: Deoptimization](#instruction-reference-deoptimization)
- [Instruction Reference: Compute](#instruction-reference-compute)
- [Module Structure](#module-structure)
- [Enums Reference](#enums-reference)
- [DSL Builders](#dsl-builders)
- [Imperative Builder (IrBuilder)](#imperative-builder-irbuilder)
- [Printer](#printer)
- [Verifier](#verifier)
- [Binary Serialization](#binary-serialization)

---

## Quick Start

### Java

```java
IrBuilder ir = new IrBuilder("example", Target.x86_64());
NativeScope b = ir.createInstructionBuilder(NativeScope.class);

try (DefinedFunction add = ir.defineFunction("add",
        List.of(Param.of("a", Type.I32), Param.of("b", Type.I32)), Type.I32)) {
    ir.appendBlock("entry");
    Value sum = b.add(add.param("a"), add.param("b"));
    b.ret(sum);
}

Module module = ir.build();
System.out.println(IrPrinter.print(module));
```

### Kotlin

```kotlin
val ir = IrBuilder("example", Target.x86_64())
val b = ir.createInstructionBuilder<NativeScope>()

ir.defineFunction("add", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32).use { fn ->
    ir.appendBlock("entry")
    val sum = b.add(fn.param("a"), fn.param("b"))
    b.ret(sum)
}

val module = ir.build()
println(IrPrinter.print(module))
```

### Kotlin DSL

```kotlin
val mod = module("example") {
    function("add", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32) {
        block("entry") {
            val result = add(param(0), param(1))
            ret(result)
        }
    }
}
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
IR. The builder API separates two concerns:

1. **IrBuilder** — module structure: functions, blocks, globals, types
2. **InstructionBuilder** — instruction emission, scoped by category

### Java: Compiler frontend example

```java
public class MyCompiler {
    private final ProgramNode ast;
    private final IrBuilder ir;
    private final NativeScope b;

    public MyCompiler(ProgramNode ast) {
        this.ast = ast;
        this.ir = new IrBuilder("my_module", Target.x86_64());
        this.b = ir.createInstructionBuilder(NativeScope.class);
    }

    public Module compile() {
        ir.setTargetTriple("x86_64-unknown-linux-gnu");

        ir.declareFunction("printf", List.of(Param.of("fmt", Type.OpaquePointer)),
            Type.I32, /* isVarArg */ true);

        for (FunctionNode func : ast.getFunctions()) {
            translateFunction(func);
        }
        return ir.build();
    }

    private void translateFunction(FunctionNode node) {
        try (DefinedFunction fn = ir.defineFunction(node.getName(),
                node.getParams().stream()
                    .map(p -> Param.of(p.getName(), mapType(p.getType())))
                    .collect(Collectors.toList()),
                mapType(node.getReturnType()))) {

            ir.appendBlock("entry");
            Value result = translateExpr(fn, node.getBody());
            b.ret(result);
        }
    }

    private Value translateExpr(DefinedFunction fn, ExprNode node) {
        if (node instanceof IntLiteral lit) {
            return new Constant.I32(lit.getValue());
        } else if (node instanceof BinaryOp op) {
            Value lhs = translateExpr(fn, op.getLeft());
            Value rhs = translateExpr(fn, op.getRight());
            return switch (op.getOp()) {
                case "+" -> b.add(lhs, rhs);
                case "-" -> b.sub(lhs, rhs);
                case "*" -> b.mul(lhs, rhs);
                case "/" -> b.sdiv(lhs, rhs);
                default -> throw new IllegalArgumentException("Unknown op: " + op.getOp());
            };
        } else if (node instanceof IfExpr ifExpr) {
            return translateIf(fn, ifExpr);
        }
        throw new IllegalArgumentException("Unknown node: " + node);
    }

    private Value translateIf(DefinedFunction fn, IfExpr node) {
        Value cond = translateExpr(fn, node.getCondition());

        BlockRef thenBlock = ir.createBlock("if.then");
        BlockRef elseBlock = ir.createBlock("if.else");
        BlockRef mergeBlock = ir.createBlock("if.merge");

        b.condBr(cond, thenBlock, elseBlock);

        ir.appendBlock(thenBlock);
        Value thenVal = translateExpr(fn, node.getThenExpr());
        b.br(mergeBlock);

        ir.appendBlock(elseBlock);
        Value elseVal = translateExpr(fn, node.getElseExpr());
        b.br(mergeBlock);

        ir.appendBlock(mergeBlock);
        return b.phi(Type.I32, List.of(
            new Pair<>(thenVal, thenBlock),
            new Pair<>(elseVal, elseBlock)
        ));
    }
}
```

### Handling Mutable Variables (alloca pattern)

SSA form requires each value to have a single definition. For source languages
with mutable variables, use the **alloca pattern** — allocate stack space for
each variable, then load/store through it. A later `mem2reg` optimization pass
will promote these to SSA registers:

```java
// Translating: var x = 0; x = x + 1; return x

Value xPtr = b.alloca(Type.I32);             // stack slot for x
b.store(new Constant.I32(0), xPtr);          // x = 0

Value xVal = b.load(Type.I32, xPtr);         // load x
Value incremented = b.add(xVal, new Constant.I32(1));  // x + 1
b.store(incremented, xPtr);                  // x = x + 1

Value result = b.load(Type.I32, xPtr);       // load final value
b.ret(result);
```

### Translating Loops

```java
BlockRef header = ir.createBlock("while.header");
BlockRef body = ir.createBlock("while.body");
BlockRef exit = ir.createBlock("while.exit");

b.br(header);

// Header: check condition
ir.appendBlock(header);
Value iVal = b.load(Type.I32, iPtr);
Value cond = b.icmp(ICmpPredicate.SLT, iVal, nVal);
b.condBr(cond, body, exit);

// Body
ir.appendBlock(body);
// ... translate body ...
Value nextI = b.add(iVal, new Constant.I32(1));
b.store(nextI, iPtr);
b.br(header);

// Exit
ir.appendBlock(exit);
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

ir.addClass(cls)
```

---

## InstructionBuilder: Scoped Emission

The InstructionBuilder is a typed, scoped API for emitting instructions. You
choose which instruction categories are available, and the compiler enforces it.
This prevents a "sea of methods" problem where 200+ instruction methods are
always visible.

### Creating an InstructionBuilder

```java
// Java — pass the scope class
NativeScope b = ir.createInstructionBuilder(NativeScope.class);
```

```kotlin
// Kotlin — reified type parameter
val b = ir.createInstructionBuilder<NativeScope>()
```

The returned object has only the methods from the requested scope:

```java
b.add(x, y);           // NativeScope includes ArithmeticInstructionSet — allowed
b.load(Type.I32, ptr); // NativeScope includes MemoryInstructionSet — allowed
b.ret(result);          // NativeScope includes TerminatorInstructionSet — allowed
b.newObject("Foo");     // NOT in NativeScope — compile error
```

The instruction builder is created once and reused across all functions in the
module. It shares the IrBuilder's instruction sink — instructions go into
whatever block IrBuilder is currently positioned at.

### Built-in Scopes

| Scope | Instruction Sets | Use Case |
|-------|-----------------|----------|
| `NativeScope` | Arithmetic, Memory, Bitwise, Call, Comparison, Conversion, Terminator, SSA | x86/ARM64/RISC-V native code |
| `ManagedScope` | Arithmetic, Object, Call, Comparison, Terminator, SSA, Debug | JVM/CLR bytecode |
| `FullScope` | All commonly used (Native + Managed + Runtime, Interop, Exception, Debug) | Compiler internals, passes |
| `ComputeScope` | Arithmetic, Memory, Bitwise, Call, Comparison, Conversion, Terminator, SSA, Compute | GPU kernels |

### Custom Scopes

Define your own scope by combining instruction set interfaces:

```java
public interface MyScope extends ArithmeticInstructionSet,
    MemoryInstructionSet, TerminatorInstructionSet {}

MyScope b = ir.createInstructionBuilder(MyScope.class);
```

### Instruction Set Interfaces

Each of the 19 categories has a corresponding `*InstructionSet` interface and
`*InstructionSetImpl` implementation, all generated from YAML:

| Interface | Category | Example Methods |
|-----------|----------|-----------------|
| `ArithmeticInstructionSet` | ARITHMETIC | `add`, `sub`, `mul`, `fadd`, `fma` |
| `MemoryInstructionSet` | MEMORY | `alloca`, `load`, `store`, `gep`, `memcpy` |
| `BitwiseInstructionSet` | BITWISE | `and`, `or`, `xor`, `shl`, `rotl` |
| `ComparisonInstructionSet` | COMPARISON | `icmp`, `fcmp` |
| `ConversionInstructionSet` | CONVERSION | `trunc`, `zext`, `sext`, `bitcast` |
| `TerminatorInstructionSet` | TERMINATOR | `ret`, `br`, `condBr`, `switchBr` |
| `CallInstructionSet` | CALL | `call`, `invoke` |
| `SsaInstructionSet` | SSA | `phi`, `select`, `freeze` |
| `DebugInstructionSet` | DEBUG | `debugLoc`, `debugValue`, `assume` |
| `ObjectInstructionSet` | OBJECT | `newObject`, `getField`, `virtualCall` |
| `RuntimeInstructionSet` | RUNTIME | `gcAlloc`, `gcSafepoint`, `writeBarrier` |
| `InteropInstructionSet` | INTEROP | `pin`, `unpin`, `managedCall` |
| `ExceptionInstructionSet` | EXCEPTION | `landingPad`, `resume`, `catchSwitch` |
| `VectorInstructionSet` | VECTOR | `extractElement`, `splat`, `vectorReduce` |
| `AggregateInstructionSet` | AGGREGATE | `extractValue`, `insertValue` |
| `AtomicInstructionSet` | ATOMIC | `fence`, `cmpxchg`, `atomicRMW` |
| `IntrinsicInstructionSet` | INTRINSIC | `intrinsic`, `inlineAsm` |
| `DeoptimizationInstructionSet` | DEOPTIMIZATION | `guard`, `deoptimize`, `frameState` |
| `ComputeInstructionSet` | COMPUTE | `threadId`, `computeBarrier`, `warpShuffle` |

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
| `FunctionRef(name, type)` | External function reference | `@add` |
| `DefinedFunction` | Defined function (also a `Value`) | `@add` |
| `BlockRef(label)` | Block reference | `%entry` |
| `Constant.*` | Compile-time constant | `42`, `null` |

`DefinedFunction` implements `Value`, so it can be passed directly to `call()`
as a function reference. `BlockRef` is used in terminators (`br`, `condBr`,
`switch`) and in `phi` nodes.

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

## Category System

Every instruction belongs to one of 19 categories, grouped into tiers that
describe their abstraction level.

### IrCategory

| Tier | Category | Description |
|------|----------|-------------|
| STRUCTURAL | `TERMINATOR` | Control flow terminators (ret, br, switch) |
| STRUCTURAL | `CALL` | Function calls (call, invoke, callbr) |
| STRUCTURAL | `SSA` | SSA management (phi, select, freeze, piNode) |
| STRUCTURAL | `DEBUG` | Debug info and optimizer hints |
| STRUCTURAL | `INTRINSIC` | Target-specific operations |
| MACHINE | `ARITHMETIC` | Integer and float computation |
| MACHINE | `BITWISE` | Bit manipulation operations |
| MACHINE | `COMPARISON` | Integer and float comparisons |
| MACHINE | `CONVERSION` | Type conversions |
| MACHINE | `MEMORY` | Memory operations (load, store, alloca, varargs) |
| MACHINE | `ATOMIC` | Atomic operations (fence, cmpxchg, atomicrmw) |
| MACHINE | `VECTOR` | SIMD vector operations |
| MACHINE | `AGGREGATE` | Struct/array field access |
| MACHINE | `EXCEPTION` | Native exception handling (landing pad, SEH) |
| RUNTIME | `RUNTIME` | GC, barriers, coroutines, ref counting |
| RUNTIME | `INTEROP` | Managed/native boundary crossing |
| OBJECT | `OBJECT` | High-level OOP (classes, arrays, dispatch) |
| DEOPTIMIZATION | `DEOPTIMIZATION` | Speculative optimization support |
| COMPUTE | `COMPUTE` | GPU/compute kernel operations |

### IrTier

Tiers describe where instructions live in the compilation pipeline:

| Tier | Description |
|------|-------------|
| `STRUCTURAL` | Always legal — control flow, SSA, debug |
| `MACHINE` | Native backend instructions |
| `RUNTIME` | Runtime integration (GC, interop) |
| `OBJECT` | High-level managed instructions |
| `COMPUTE` | GPU/parallel compute |

### IrConstraints

Predefined category whitelists for common compilation contexts:

| Preset | Categories | Use Case |
|--------|-----------|----------|
| `STRUCTURAL` | Terminators, calls, SSA, debug, intrinsics | Minimal |
| `NATIVE` | Structural + all machine categories | Native compilation |
| `RUNTIME_NATIVE` | Native + runtime | GC-aware native code |
| `MIXED` | Runtime-native + interop | Mixed managed/native |
| `MANAGED_VM` | Structural + object | Pure JVM/CLR |
| `MANAGED_NATIVE` | All machine + runtime + interop + object | Java-to-native |
| `JIT` | Managed-native + deoptimization | JIT with speculation |
| `COMPUTE_KERNEL` | Native + compute | GPU kernels |
| `ALL` | Everything | No restrictions |

```java
// Constrain an IrBuilder to only allow native instructions
IrBuilder ir = new IrBuilder("module", Target.x86_64(), IrConstraints.NATIVE);
```

---

## Effect System

Every instruction declares a static `InstructionEffects` bitmask describing its
semantic behavior. The effect system drives optimization passes — dead code
elimination, loop-invariant code motion, instruction reordering, and GVN all
query effects rather than maintaining per-instruction special cases.

### Effect Bits

| Effect | Meaning |
|--------|---------|
| `readsHeapMemory` | Reads from heap (globals, fields, pointers) |
| `writesHeapMemory` | Writes to heap |
| `readsStackMemory` | Reads from alloca'd stack memory |
| `writesStackMemory` | Writes to stack memory |
| `readsArgMemory` | Reads through argument pointers |
| `writesArgMemory` | Writes through argument pointers |
| `canTrap` | May trap (div by zero, null deref) |
| `canThrow` | May throw a managed exception |
| `isSafepoint` | GC may run and relocate objects |
| `isBarrier` | Memory barrier preventing reordering |
| `hasSideEffects` | Cannot be removed even if result is dead |
| `isTerminator` | Must be last instruction in block |
| `isBranch` | Conditional control flow transfer |
| `isCall` | Function call |
| `isReturn` | Function return |
| `commutes` | Operand order doesn't matter (a+b = b+a) |
| `isDivergent` | GPU lanes may take different paths |

### Derived Queries

```java
instruction.effects().isPure();         // no effects at all
instruction.effects().readsMemory();    // reads heap, stack, or arg memory
instruction.effects().writesMemory();   // writes heap, stack, or arg memory
```

### Predefined Effect Sets

| Name | Bits |
|------|------|
| `PURE` | No effects |
| `PURE_COMMUTATIVE` | No effects, operands commute |
| `READS_HEAP` | Reads heap memory |
| `WRITES_HEAP` | Writes heap, has side effects |
| `CALL` | Function call |
| `INVOKE` | Call that may throw |
| `BRANCH` | Unconditional branch terminator |
| `CONDITIONAL_BRANCH` | Conditional branch terminator |
| `RETURN` | Return terminator |
| `TRAP` | Terminator that aborts |
| `SAFEPOINT` | GC safepoint |
| `GC_ALLOC` | GC allocation (safepoint + side effects) |
| `OBJECT_ALLOC` | Object allocation (writes heap) |
| `WRITE_BARRIER` | Write barrier (writes heap) |
| `MANAGED_THROW` | Managed exception throw (terminator) |

### Effect Computation (Dynamic Refinement)

`EffectComputation.computeEffects(instruction)` narrows static effects based on
operand analysis. For example, a `WriteBarrier` storing a primitive (non-reference)
value is refined to `PURE` since no actual barrier is needed.

### Effect Ordering

`EffectOrdering` determines whether two instructions must maintain their relative
order. Seven rules govern ordering:

1. Either instruction has side effects
2. Safepoint ↔ reference-touching instruction
3. Barrier ↔ heap access
4. Memory conflict (at least one write)
5. Either is a control flow instruction (terminator, branch, return)
6. Either can trap or throw
7. Divergent ↔ compute synchronization (barrier/fence)

```java
boolean mustKeepOrder = EffectOrdering.mustOrder(first, second);
boolean refined = EffectOrdering.mustOrderRefined(first, second);
```

---

## Capability System

Capabilities describe semantic features of the compilation context — what
runtime services are available, what memory models apply, what exception
mechanisms exist. Unlike categories (which filter instruction opcodes),
capabilities validate that the IR makes semantic sense for the target.

### Capability Enum (28 values)

| Group | Capabilities |
|-------|-------------|
| Memory | `GC_MANAGED`, `GC_MOVING`, `GC_BARRIERS`, `REF_COUNTED`, `NATIVE_MEMORY`, `COMPUTE_MEMORY` |
| Types | `MANAGED_OBJECTS`, `VIRTUAL_DISPATCH`, `TYPE_CHECKS`, `GENERICS` |
| Exceptions | `MANAGED_EXCEPTIONS`, `NATIVE_EXCEPTIONS`, `NO_EXCEPTIONS` |
| Interop | `NATIVE_INTEROP`, `JNI`, `P_INVOKE` |
| Control | `COROUTINES` |
| Speculation | `DEOPTIMIZATION`, `OSR` |
| Compute | `COMPUTE_KERNEL`, `KERNEL_LAUNCH` |

### Implication Rules

Some capabilities imply others:
- `GC_MOVING` → `GC_MANAGED`
- `VIRTUAL_DISPATCH` → `MANAGED_OBJECTS`
- `GC_BARRIERS` → `GC_MANAGED`

### Mutual Exclusivity

Some capabilities cannot coexist:
- `NO_EXCEPTIONS` conflicts with `MANAGED_EXCEPTIONS` and `NATIVE_EXCEPTIONS`
- `COMPUTE_KERNEL` conflicts with `MANAGED_OBJECTS` and `GC_MANAGED`

### Predefined Capability Sets

| Name | Description |
|------|-------------|
| `KGEN_NATIVE` | Full-featured: native memory, managed objects, GC, deoptimization |
| `KGEN_JIT` | KGEN_NATIVE + OSR |
| `PLAIN_NATIVE` | Just native memory + native exceptions |
| `EXTERNAL_VM` | For code targeting an external VM (no GC barriers) |
| `COMPUTE_KERNEL` | Native + compute memory |

---

## Pipeline Phases

Pipeline phases are descriptive labels for where code sits in the compilation
pipeline. They are documentation and tooling aids, not enforcement mechanisms.

| Phase | Value | Description |
|-------|-------|-------------|
| `FRONTEND_OBJECT` | 0 | All Object instructions present |
| `POST_OBJECT_LOWERING` | 1 | Object instructions replaced with Runtime primitives |
| `POST_RUNTIME_LOWERING` | 2 | Runtime expanded to machine-level sequences |
| `BACKEND_LEGAL` | 3 | Only machine-level instructions remain |

Passes can optionally declare a phase transition via the `PhasedPass` interface.
Post-phase validators can check that specific instruction categories have been
eliminated.

---

## Instruction Reference: Low-Level

Low-level instructions are consumed directly by native backends (x86-64, ARM64,
RISC-V). All instructions implement `sealed interface Instruction` with
`result: Value?` (null for void instructions) and `effects: InstructionEffects`.

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
**Effects**: `PURE_COMMUTATIVE`
**Semantics**: Two's complement addition. If `nuw` is set and unsigned overflow
occurs, the result is poison. If `nsw` is set and signed overflow occurs, the
result is poison.

```java
Value sum = b.add(a, b);
Value safe = b.add(a, b, true, true);  // poison on any overflow
```

---

#### `Sub` — Integer subtraction

```
%result = sub [nuw] [nsw] <type> %lhs, %rhs
```

Same constraints and flags as `Add`. Computes `lhs - rhs`.
**Effects**: `PURE`

---

#### `Mul` — Integer multiplication

```
%result = mul [nuw] [nsw] <type> %lhs, %rhs
```

Same constraints and flags as `Add`. Computes `lhs * rhs`.
**Effects**: `PURE_COMMUTATIVE`

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
**Effects**: `MAY_TRAP`

---

#### `SDiv` — Signed integer division

```
%result = sdiv [exact] <type> %lhs, %rhs
```

Same as `UDiv` but signed. Division by zero and `INT_MIN / -1` are both UB.

---

#### `URem` — Unsigned integer remainder

```
%result = urem <type> %lhs, %rhs
```

**Constraints**: Both operands same integer type. Remainder by zero is UB.
**Effects**: `MAY_TRAP`

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

**Effects**: `PURE`
**Semantics**: Equivalent to `sub 0, %operand`.

---

### Overflow-Checked Arithmetic (6 instructions)

These return a struct `{result_type, i1}` where the `i1` indicates overflow.
**Effects**: `PURE` / `PURE_COMMUTATIVE`

---

#### `SAddOverflow` / `UAddOverflow` — Add with overflow detection

```
%result = sadd.overflow <type> %lhs, %rhs   ; signed
%result = uadd.overflow <type> %lhs, %rhs   ; unsigned
```

**Result type**: `{<type>, i1}` — the sum and an overflow flag.

```java
Value result = b.saddOverflow(a, b);
Value sum = b.extractValue(result, 0);
Value overflow = b.extractValue(result, 1);
b.condBr(overflow, overflowHandler, continueBlock);
```

---

#### `SSubOverflow` / `USubOverflow` — Subtract with overflow detection

Same structure as add overflow. Detects signed/unsigned underflow.

#### `SMulOverflow` / `UMulOverflow` — Multiply with overflow detection

Same structure. Detects when multiplication result doesn't fit in the type.

---

### Saturating Arithmetic (4 instructions)

Clamp results to the representable range instead of wrapping.
**Effects**: `PURE` / `PURE_COMMUTATIVE`

---

#### `SAddSat` / `UAddSat` — Saturating add

```
%result = sadd.sat <type> %lhs, %rhs
%result = uadd.sat <type> %lhs, %rhs
```

**Semantics**: `SAddSat` clamps to `[INT_MIN, INT_MAX]`. `UAddSat` clamps to
`[0, UINT_MAX]`.

---

#### `SSubSat` / `USubSat` — Saturating subtract

Same semantics with subtraction.

---

### Integer Min/Max/Abs (5 instructions)

**Effects**: `PURE_COMMUTATIVE` (min/max), `PURE` (abs)

---

#### `SMin` / `SMax` / `UMin` / `UMax` — Integer min/max

```
%result = smin <type> %lhs, %rhs
```

**Constraints**: Both operands same integer type.
**Semantics**: Returns the smaller/larger of the two operands, using
signed or unsigned comparison.

---

#### `Abs` — Integer absolute value

```
%result = abs <type> %operand [intmin_poison]
```

| Field | Type | Description |
|-------|------|-------------|
| `isIntMin` | `Boolean` | Result is poison when input is `INT_MIN` |

---

### Float Arithmetic (16 instructions)

---

#### `FAdd` / `FSub` / `FMul` / `FDiv` / `FRem` — Float binary arithmetic

```
%result = fadd [fast-math-flags] <type> %lhs, %rhs
```

**Constraints**: Both operands same float type.
**Result type**: Same as operands.
**Effects**: `PURE_COMMUTATIVE` (fadd, fmul), `PURE` (fsub, fdiv, frem)

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

**Effects**: `PURE`
**Semantics**: Flips the sign bit. `fneg(-0.0) = +0.0`.

#### `FAbs` — Float absolute value

**Effects**: `PURE`
**Semantics**: Clears the sign bit.

#### `FMA` — Fused multiply-add

**Effects**: `PURE`
**Semantics**: Computes `a * b + c` with a single rounding step.

#### `FMin` / `FMax` — IEEE 754 minimum/maximum

**Effects**: `PURE_COMMUTATIVE`
**Semantics**: IEEE 754-2019 minimum/maximum. If either operand is NaN,
returns the non-NaN operand.

#### `Sqrt` — Square root

**Effects**: `PURE`

#### `Ceil` / `Floor` / `Round` / `FTrunc` — Rounding

```
%result = ceil <type> %operand     ; round toward +inf
%result = floor <type> %operand    ; round toward -inf
%result = round <type> %operand    ; round to nearest, ties away from zero
%result = ftrunc <type> %operand   ; round toward zero
```

**Effects**: `PURE`

**Note**: `FTrunc` is float rounding toward zero. Integer truncation is
`IntTrunc`. In the builder, use `ftrunc()` for float rounding and `trunc()`
for integer truncation.

#### `CopySign` — Copy sign bit

**Effects**: `PURE`
**Semantics**: Returns a value with the magnitude of `magnitude` and the sign
of `sign`.

---

### Bitwise Operations (14 instructions)

---

#### `And` / `Or` / `Xor` — Bitwise binary operations

```
%result = and <type> %lhs, %rhs
%result = or  <type> %lhs, %rhs
%result = xor <type> %lhs, %rhs
```

**Effects**: `PURE_COMMUTATIVE`

---

#### `Not` — Bitwise complement

**Effects**: `PURE`
**Semantics**: One's complement. Equivalent to `xor %operand, -1`.

---

#### `Shl` — Shift left

```
%result = shl [nuw] [nsw] <type> %lhs, %rhs
```

**Effects**: `PURE`
**Semantics**: Shift `lhs` left by `rhs` bits, filling with zeros.

---

#### `LShr` — Logical shift right

```
%result = lshr [exact] <type> %lhs, %rhs
```

**Effects**: `PURE`
**Semantics**: Shift right, filling with zeros (unsigned shift).

---

#### `AShr` — Arithmetic shift right

```
%result = ashr [exact] <type> %lhs, %rhs
```

**Effects**: `PURE`
**Semantics**: Shift right, filling with the sign bit (signed shift).

---

#### `Rotl` / `Rotr` — Bit rotation

```
%result = rotl <type> %value, %amount
%result = rotr <type> %value, %amount
```

**Effects**: `PURE`
**Semantics**: Circular shift. Bits shifted out one end re-enter the other.

---

#### `Ctlz` — Count leading zeros

```
%result = ctlz <type> %operand [zero_poison]
```

| Field | Type | Description |
|-------|------|-------------|
| `isZeroPoison` | `Boolean` | Result is poison when operand is 0 |

**Effects**: `PURE`

---

#### `Cttz` — Count trailing zeros

Same structure as `Ctlz`. Counts from the least significant bit.

---

#### `Ctpop` — Population count

**Effects**: `PURE`
**Semantics**: Returns the number of set (1) bits.

---

#### `BSwap` — Byte swap

**Effects**: `PURE`
**Constraints**: Operand bit width must be a multiple of 16.
**Semantics**: Reverses the byte order. `bswap(0x12345678) = 0x78563412`.

---

#### `BitReverse` — Bit reversal

**Effects**: `PURE`
**Semantics**: Reverses the order of all bits.

---

### Comparison (2 instructions)

---

#### `ICmp` — Integer comparison

```
%result = icmp <predicate> <type> %lhs, %rhs
```

**Result type**: `i1`.
**Effects**: `PURE`

| Predicate | Meaning |
|-----------|---------|
| `EQ` / `NE` | Equal / Not equal |
| `UGT` / `UGE` / `ULT` / `ULE` | Unsigned comparisons |
| `SGT` / `SGE` / `SLT` / `SLE` | Signed comparisons |

---

#### `FCmp` — Float comparison

```
%result = fcmp [fast-math-flags] <predicate> <type> %lhs, %rhs
```

**Result type**: `i1`.
**Effects**: `PURE`

| Predicate | Meaning |
|-----------|---------|
| `FALSE` / `TRUE` | Always false / always true |
| `OEQ` / `OGT` / `OGE` / `OLT` / `OLE` / `ONE` | Ordered (false if NaN) |
| `UEQ` / `UGT` / `UGE` / `ULT` / `ULE` / `UNE` | Unordered (true if NaN) |
| `ORD` / `UNO` | Neither is NaN / Either is NaN |

---

### Memory (16 instructions)

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
**Effects**: `WRITES_STACK`

---

#### `Load` — Memory load

```
%val = load [volatile] <type>, ptr %ptr [, align <n>] [, <ordering>]
```

**Result type**: `loadType`.
**Effects**: `READS_HEAP` (or `READS_ALL_MEMORY` for volatile)

---

#### `Store` — Memory store

```
store [volatile] <type> %val, ptr %ptr [, align <n>] [, <ordering>]
```

**Result**: None (void instruction).
**Effects**: `WRITES_HEAP`

---

#### `GetElementPtr` (GEP) — Element pointer computation

```
%ptr = getelementptr [inbounds] <type>, ptr %base, <idx1> [, <idx2>, ...]
```

**Result type**: Pointer.
**Effects**: `PURE`
**Semantics**: Computes a pointer to a sub-element **without accessing memory**.

```java
// Array element: &base[i]
Value elemPtr = b.gep(Type.I32, basePtr, new Constant.I32(i));

// Struct field: &ptr->fields[2]
Value fieldPtr = b.gep(structType, ptr, new Constant.I32(0), new Constant.I32(2));
```

---

#### `MemCpy` / `MemSet` / `MemMove` — Bulk memory operations

**Effects**: `WRITES_ALL_MEMORY` (with side effects)
- `MemCpy`: copies `len` bytes. Source and destination must not overlap.
- `MemSet`: fills `len` bytes with `val`.
- `MemMove`: copies `len` bytes. Handles overlapping regions correctly.

---

#### `Prefetch` — Cache prefetch hint

**Effects**: has side effects (hint to hardware)

---

#### `StackSave` / `StackRestore` — Save/restore stack pointer

**Effects**: `READS_STACK` / `WRITES_STACK`

#### `LifetimeStart` / `LifetimeEnd` — Lifetime markers

**Effects**: has side effects (hints for stack slot reuse)

---

#### `VAStart` / `VAEnd` / `VACopy` / `VAArg` — Varargs

```
va_start ptr %ap
va_end   ptr %ap
va_copy  ptr %dst, ptr %src
%val = va_arg ptr %ap, <type>
```

**Effects**: `WRITES_ALL_MEMORY` (vastart/vaend/vacopy), `READ_WRITE_ALL_MEMORY` (vaarg)

---

### Atomic Operations (3 instructions)

---

#### `Fence` — Memory fence

```
fence [syncscope] <ordering>
```

**Effects**: `FENCE`

---

#### `CmpXchg` — Compare and exchange

```
%result = cmpxchg [weak] [volatile] ptr %ptr, <type> %cmp, <type> %new,
          <success_ordering>, <failure_ordering>
```

**Result type**: `{<type>, i1}` — old value and success flag.
**Effects**: `ATOMIC_RMW`

---

#### `AtomicRMW` — Atomic read-modify-write

```
%old = atomicrmw [volatile] <op> ptr %ptr, <type> %val, <ordering>
```

**Result type**: Same as `val` (the old value).
**Effects**: `ATOMIC_RMW`

| Op | Operation |
|----|-----------|
| `XCHG` | Exchange |
| `ADD` / `SUB` | Integer add/subtract |
| `AND` / `NAND` / `OR` / `XOR` | Bitwise operations |
| `MAX` / `MIN` | Signed max/min |
| `UMAX` / `UMIN` | Unsigned max/min |
| `FADD` / `FSUB` / `FMAX` / `FMIN` | Float operations |

---

### Conversions (13 instructions)

**Effects**: All `PURE`.

---

#### Integer Conversions

| Instruction | Builder | From → To | Operation |
|-------------|---------|-----------|-----------|
| `IntTrunc` | `trunc(v, type)` | wider int → narrower int | Drop high bits |
| `ZExt` | `zext(v, type)` | narrower int → wider int | Fill with zeros |
| `SExt` | `sext(v, type)` | narrower int → wider int | Fill with sign bit |

#### Float Conversions

| Instruction | Builder | From → To | Operation |
|-------------|---------|-----------|-----------|
| `FPTrunc` | `fptrunc(v, type)` | wider float → narrower float | Round to fit |
| `FPExt` | `fpext(v, type)` | narrower float → wider float | Extend precision |

#### Float ↔ Integer Conversions

| Instruction | Builder | From → To | Operation |
|-------------|---------|-----------|-----------|
| `FPToUI` | `fptoui(v, type)` | float → unsigned int | Truncate toward zero |
| `FPToSI` | `fptosi(v, type)` | float → signed int | Truncate toward zero |
| `UIToFP` | `uitofp(v, type)` | unsigned int → float | Nearest representable |
| `SIToFP` | `sitofp(v, type)` | signed int → float | Nearest representable |

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

**Effects**: `RETURN`

```java
b.ret(new Constant.I32(0));   // return 0
b.ret();                       // return void
```

---

#### `Br` — Unconditional branch

```
br label %target
```

**Effects**: `BRANCH`

```java
b.br(nextBlock);
```

---

#### `CondBr` — Conditional branch

```
br i1 %cond, label %true, label %false
```

**Effects**: `CONDITIONAL_BRANCH`
**Constraints**: `condition` must be `i1`.

```java
b.condBr(cond, thenBlock, elseBlock);
```

---

#### `Switch` — Multi-way branch

```
switch <type> %val, label %default [
  <type> <case1>, label %dest1
  <type> <case2>, label %dest2
]
```

**Effects**: `CONDITIONAL_BRANCH`

```java
b.switchBr(value, defaultBlock, List.of(
    new Pair<>(new Constant.I32(0), caseZeroBlock),
    new Pair<>(new Constant.I32(1), caseOneBlock)
));
```

---

#### `IndirectBr` — Indirect branch

**Effects**: `BRANCH`
**Semantics**: Branch to address. Target list is for the verifier/optimizer.

#### `Unreachable` — Mark unreachable code

**Effects**: `TRAP`
**Semantics**: If execution reaches this point, behavior is undefined.

#### `Trap` / `DebugTrap` — Abort / breakpoint

**Effects**: `TRAP`
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

**Effects**: `CALL`

```java
Value result = b.call(addFunction, List.of(a, b), Type.I32);
b.call(printfRef, List.of(fmt), Type.Void);
```

---

#### `Invoke` — Call with exception handling

```
[%result =] invoke [cconv] <ret_type> @func(<args>)
    to label %normal unwind label %unwind
```

**Effects**: `INVOKE` (call + may throw + terminator)
**Semantics**: Like `Call`, but if the callee throws, control goes to
`unwindDest` instead of `normalDest`. This is a **terminator**.

---

#### `CallBr` — Call with multiple successors

**Effects**: `CALL` + `IS_TERMINATOR`
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

### SSA (4 instructions)

---

#### `Phi` — Value merge at control flow join

```
%result = phi <type> [%val1, %block1], [%val2, %block2], ...
```

**Effects**: `PURE`
**Constraints**: Must appear at the beginning of a block (before non-phi
instructions). All incoming values must have the same type. Each predecessor
block must have exactly one entry.

```java
Value result = b.phi(Type.I32, List.of(
    new Pair<>(new Constant.I32(0), entryBlock),
    new Pair<>(nextI, loopBodyBlock)
));
```

---

#### `Select` — Conditional value

```
%result = select i1 %cond, <type> %true_val, <type> %false_val
```

**Effects**: `PURE`
Like a ternary operator. Unlike `CondBr`, this is not a terminator.

---

#### `Freeze` — Stabilize undef/poison

```
%result = freeze <type> %val
```

**Effects**: `PURE`
**Semantics**: If `val` is undef or poison, returns an arbitrary but fixed
value. If `val` is a normal value, returns it unchanged.

---

#### `PiNode` — Type refinement pseudo-instruction

```
%result = pi <type> %base, refined <refined_type>
```

**Effects**: `PURE`
**Semantics**: Produces a new SSA value with a narrowed type for a value proven
to be of that type at a given program point (e.g., after a guard). Emits no
machine code — eliminated before instruction selection by the `PiNodeElimination`
pass.

---

### Vector Operations (5 instructions)

**Effects**: All `PURE`.

---

#### `ExtractElement` — Extract scalar from vector

```
%elem = extractelement <vec_type> %vec, i32 %idx
```

#### `InsertElement` — Insert scalar into vector

```
%new_vec = insertelement <vec_type> %vec, <elem_type> %elem, i32 %idx
```

#### `ShuffleVector` — Shuffle lanes from two vectors

```
%result = shufflevector <vec_type> %v1, <vec_type> %v2, <mask>
```

#### `Splat` — Broadcast scalar to vector

```
%vec = splat <elem_type> %scalar, <vec_type>
```

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

---

### Aggregate Operations (2 instructions)

**Effects**: `PURE`.

#### `ExtractValue` — Extract from struct/array

```
%field = extractvalue <agg_type> %agg, <idx1> [, <idx2>, ...]
```

#### `InsertValue` — Insert into struct/array

```
%new = insertvalue <agg_type> %agg, <elem_type> %elem, <idx1> [, ...]
```

---

### Debug / Metadata (5 instructions)

#### `DebugLoc` — Source location

```java
b.debugLoc(42, 10, "main.c");
b.debugLoc(42, 10, "main.c", "caller.c:100");
```

#### `DebugValue` / `DebugDeclare` — Variable tracking

```java
b.debugValue("x", someValue);
b.debugDeclare("arr", allocaPtr);
```

#### `Assume` — Assert condition for optimizer

```java
b.assume(b.icmp(ICmpPredicate.SGT, len, new Constant.I32(0)));
```

#### `Expect` — Branch prediction hint

```java
Value likely = b.expect(flag, new Constant.I1(true));
```

---

### Inline Assembly / Intrinsics (2 instructions)

#### `InlineAsm` — Inline assembly

```java
Value result = b.inlineAsm("mov $1, $0", "=r,r", List.of(input),
    Type.I32, true, AsmDialect.ATT);
```

#### `Intrinsic` — Target-specific operation

```java
Value result = b.intrinsic("llvm.x86.sse2.pmovmskb.128", List.of(vec), Type.I32);
```

---

## Instruction Reference: High-Level

High-level instructions target managed runtimes (JVM, WASM GC). They are
consumed directly by managed backends and lowered to low-level equivalents
for native backends.

### Object Lifecycle (3 instructions)

#### `NewObject` — Allocate object

**Effects**: `OBJECT_ALLOC`

```java
Value obj = b.newObject("Point");
Value generic = b.newObject("List", List.of(Type.I32));
```

#### `NewArray` — Allocate managed array

**Effects**: `OBJECT_ALLOC`

```java
Value arr = b.newArray(Type.I32, new Constant.I32(10));
```

#### `NewMultiArray` — Allocate multi-dimensional array

**Effects**: `OBJECT_ALLOC`

---

### Field Access (4 instructions)

#### `GetField` / `PutField` — Instance fields

**Effects**: `READS_HEAP` (get), `WRITES_HEAP` (put)

```java
Value x = b.getField(point, "Point", "x", Type.F64);
b.putField(point, "Point", "x", Type.F64, new Constant.F64(5.0));
```

#### `GetStatic` / `PutStatic` — Static fields

**Effects**: `READS_HEAP` (get), `WRITES_HEAP` (put)

---

### Method Dispatch (6 instructions)

#### `VirtualCall` — Virtual method dispatch

**Effects**: `CALL`
Dispatches through the vtable. The actual method called depends on the
runtime type of `obj`.

#### `InterfaceCall` — Interface method dispatch

**Effects**: `CALL`

#### `SpecialCall` — Direct call (super/private/init)

**Effects**: `CALL`
No virtual dispatch. Used for `super` calls, private methods, and constructors.

#### `StaticCall` — Static method call

**Effects**: `CALL`

#### `DynamicCall` — invokedynamic-style dispatch

**Effects**: `CALL`
Used for lambdas, string concatenation, and other dynamic dispatch in JVM.

#### `ConstructorCall` — Constructor invocation

**Effects**: `CALL`
**Result**: None (void). Initializes `obj` in place.

---

### Type Operations (3 instructions)

#### `InstanceOf` — Type check

**Effects**: `PURE`
**Result type**: `i1`

#### `CheckCast` — Type cast

**Effects**: can throw, has side effects
**Semantics**: Throws on failure.

#### `TypeId` — Runtime type identifier

**Effects**: `PURE`
**Result type**: `i32`

---

### Managed Array Operations (3 instructions)

```java
Value len = b.arrayLength(arr);
Value elem = b.arrayGet(arr, new Constant.I32(0), Type.I32);
b.arraySet(arr, new Constant.I32(0), new Constant.I32(42), Type.I32);
```

Includes bounds checking on managed backends (unlike raw load/store).

---

### Monitor / Synchronization (2 instructions)

**Effects**: `MONITOR`

```java
b.monitorEnter(lockObj);
b.monitorExit(lockObj);
```

---

### Exception Handling — Managed (2 instructions)

#### `Throw` — Throw exception (terminator)

**Effects**: `MANAGED_THROW`

```java
b.throwException(exceptionObj);
```

#### `TryCatchRegion` — Try/catch/finally

```java
b.tryCatch(tryBlock, List.of(
    new CatchHandler(Type.ClassRef("IOException"), ioHandlerBlock),
    new CatchHandler(Type.ClassRef("Exception"), genericHandlerBlock)
), cleanupBlock);
```

---

### Boxing / Unboxing (2 instructions)

```java
Value boxed = b.box(new Constant.I32(42), Type.ClassRef("Integer"));
Value unboxed = b.unbox(boxed, Type.I32);
```

---

### Closures / Lambdas (3 instructions)

#### `ClosureCreate` — Create closure

**Effects**: `OBJECT_ALLOC`

```java
Value closure = b.closureCreate(funcRef, List.of(x, y), closureType);
```

| Field | Type | Description |
|-------|------|-------------|
| `escaping` | `Boolean` | If false, environment may be stack-allocated (default: true) |

#### `ClosureInvoke` — Invoke closure

**Effects**: `CALL`

```java
Value result = b.closureInvoke(closure, List.of(new Constant.I32(5)), Type.I32);
```

#### `ClosureInvokeOnce` — Invoke with move semantics (FnOnce)

**Effects**: `CALL`
**Semantics**: The closure value is consumed on invocation — no further uses
are legal. The verifier checks that the closure SSA value has no uses after
`ClosureInvokeOnce`. Lowers to the same indirect call as `ClosureInvoke`.

---

### Tagged Unions / ADTs (4 instructions)

```java
Value some = b.constructVariant(optionType, "Some", List.of(new Constant.I32(42)));
b.tagSwitch(optionVal, List.of(
    new Pair<>("Some", handleSomeBlock),
    new Pair<>("None", handleNoneBlock)
));
Value inner = b.getVariantField(optionVal, "Some", 0, Type.I32);
Value tag = b.getTag(optionVal);
```

---

### Catch and Weak References (4 instructions)

#### `CatchValue` — Retrieve caught exception

**Effects**: has side effects

```java
Value exception = b.catchValue(Type.ClassRef("Exception"));
```

#### `MakeWeakRef` — Create weak reference

**Effects**: writes heap

#### `ReadWeakRef` — Read weak reference

**Effects**: reads heap
**Semantics**: Returns null if the referent has been collected.

#### `ClearWeakRef` — Clear weak reference

**Effects**: writes heap

---

### GC Integration (4 instructions)

```java
Value obj = b.gcAlloc(Type.ClassRef("Node"));
b.gcRoot(localPtr, null);
b.gcSafepoint();
```

#### `GCRelocate` — Post-safepoint reference relocation

**Effects**: `PURE`
**Semantics**: After any instruction with `isSafepoint=true`, live GC references
may have been moved by a compacting collector. `GCRelocate` produces a new SSA
value representing the post-safepoint location. For non-moving GC strategies,
`GCRelocate` is the identity and is eliminated.

```java
Value relocated = b.gcRelocate(safepointInst, baseRef, derivedRef);
```

---

### Write / Read Barriers (2 instructions)

```java
b.writeBarrier(obj, fieldIndex, value);
Value loaded = b.readBarrier(ref);
```

**Effects**: `WRITE_BARRIER` / `READS_HEAP`

---

### Reference Counting (3 instructions)

```java
b.refRetain(sharedObj);
b.refRelease(sharedObj);
Value count = b.refCount(obj);
```

---

### Coroutines (6 instructions)

```java
Value frameSize = b.coroSize();
Value handle = b.coroBegin(coroId, mem);
Value state = b.coroSuspend(null, false);
b.coroResume(handle);
b.coroEnd(handle, false);
b.coroDestroy(handle);
```

---

### Interop (6 instructions)

#### `Pin` / `Unpin` — Pin/unpin managed objects

**Effects**: has side effects
**Semantics**: Prevents GC from relocating the object while pinned.

#### `InteriorPtr` — Interior pointer

**Effects**: `PURE`
**Semantics**: Computes a pointer to an element within a managed object.

#### `ManagedCall` — Cross-boundary call

**Effects**: `CALL`
**Semantics**: Calls across the managed/native boundary with appropriate
transition logic.

#### `ManagedToDevice` — Transfer to GPU

**Effects**: has side effects
**Semantics**: Pins a managed reference into GPU-accessible memory.

#### `DeviceRelease` — Release GPU pin

**Effects**: has side effects
**Semantics**: Releases a ManagedToDevice pin after confirming no in-flight
kernel holds the pointer.

---

## Instruction Reference: Deoptimization

Deoptimization instructions support JIT compilation with speculative
optimization. These are in the `DEOPTIMIZATION` category and require the
`IrConstraints.JIT` preset.

### `FrameState` — Capture interpreter state

**Effects**: `PURE` (pseudo-instruction, not emitted as machine code)

```java
b.frameState(methodRef, bci, locals, stack, locks, outerFrame, virtualObjects);
```

Captures interpreter state for deoptimization. Must exist for every instruction
with `mayDeopt=true` or `isSafepoint=true` in managed compilation contexts.

### `Guard` — Floating conditional deoptimization

**Effects**: has side effects

```java
b.guard(condition, false, DeoptReason.NULL_CHECK, DeoptAction.INVALIDATE_RECOMPILE,
    null, frameState);
```

Deoptimize if condition is false (or true if negated). May float freely
between its anchor and the first use of any value it protects. Lowered by
the `GuardLowering` pass to `CondBr` + `Deoptimize` stub blocks.

### `FixedGuard` — Control-flow-fixed guard

**Effects**: has side effects

Unlike `Guard`, `FixedGuard` is structurally fixed in the control flow and
cannot float. Must be lowered to an explicit conditional branch.

### `Deoptimize` — Unconditional deoptimization (terminator)

**Effects**: terminator, has side effects

Must be the last instruction in a basic block. Immediately triggers
deoptimization with the attached frame state.

### `OSREntry` — On-stack replacement entry

**Effects**: `PURE`

Used to transition from the interpreter to compiled code at a loop header.

### Supporting Types

| Type | Values |
|------|--------|
| `DeoptReason` | `NULL_CHECK`, `BOUNDS_CHECK`, `CLASS_CAST`, `ARRAY_STORE`, `ARITHMETIC_EXCEPTION`, `TYPE_CHECK_VIOLATED`, `UNREACHED_CODE`, `ALIASING`, `TRANSFER_TO_INTERPRETER`, `RUNTIME_CONSTRAINT`, `SPECULATIVE_INLINING`, `LOOP_LIMIT_CHECK` |
| `DeoptAction` | `NONE`, `INVALIDATE_REPROFILE`, `INVALIDATE_RECOMPILE`, `INVALIDATE_STOP_COMPILING` |
| `SpeculationId` | Stable identifier for speculative assumptions |
| `VirtualObjectState` | Escape-analyzed object (type + fields) for FrameState materialization |
| `MonitorId` | Opaque identifier for locks in FrameState |

---

## Instruction Reference: Compute

Compute instructions represent operations specific to massively parallel
execution contexts (GPU kernels). These are in the `COMPUTE` category and
require the `IrConstraints.COMPUTE_KERNEL` preset.

### Thread/Grid Identification (6 instructions)

**Effects**: `PURE` — hardware register reads.

| Instruction | Description | Result |
|-------------|-------------|--------|
| `ThreadId(dim)` | Thread ID within block | `i32` |
| `BlockId(dim)` | Block ID within grid | `i32` |
| `WarpId` | Warp/wavefront ID within block | `i32` |
| `LaneId` | Lane ID within warp | `i32` |
| `BlockDim(dim)` | Threads per block in dimension | `i32` |
| `GridDim(dim)` | Blocks in grid in dimension | `i32` |

`dim` is a `ThreadDim` enum: `X`, `Y`, or `Z`.

```java
Value threadX = b.threadId(ThreadDim.X);
Value blockSize = b.blockDim(ThreadDim.X);
Value globalIdx = b.add(b.mul(b.blockId(ThreadDim.X), blockSize), threadX);
```

### Synchronization (2 instructions)

#### `ComputeBarrier` — Thread synchronization

**Effects**: has side effects
**Semantics**: All threads in the scope must reach the barrier before any can
proceed. Scope is a `ComputeScope` enum: `WARP`, `BLOCK`, or `DEVICE`.

#### `ComputeFence` — Memory ordering fence

**Effects**: has side effects
**Semantics**: Ensures memory operations are visible to threads at the specified
scope. Takes a `ComputeScope` and `MemorySpace`.

### Kernel Launch (1 instruction)

#### `KernelLaunch` — Launch compute kernel

**Effects**: has side effects
**Semantics**: Host-side instruction that launches a kernel asynchronously.

```java
b.kernelLaunch(kernelFn, gridDims, blockDims, sharedMemSize, stream, args);
```

### Compute Atomics (4 instructions)

**Effects**: reads + writes heap, has side effects.

| Instruction | Operation |
|-------------|-----------|
| `ComputeAtomicAdd` | GPU-native atomic add |
| `ComputeAtomicMin` | GPU-native atomic min |
| `ComputeAtomicMax` | GPU-native atomic max |
| `ComputeAtomicCAS` | GPU-native compare-and-swap |

All take a pointer, value, `AtomicOrdering`, and `MemorySpace`.

### Warp Shuffle (4 instructions)

**Effects**: `PURE` — intra-warp register exchange.

| Instruction | Source Lane |
|-------------|------------|
| `WarpShuffle` | Arbitrary source lane |
| `WarpShuffleDown` | Current lane + delta |
| `WarpShuffleUp` | Current lane - delta |
| `WarpShuffleXor` | Current lane XOR mask |

All return the same type as their value operand. Optional `width` parameter
(default 32) for sub-warp operations.

### Warp Vote (2 instructions)

**Effects**: `PURE`

| Instruction | Description | Result |
|-------------|-------------|--------|
| `Ballot` | Bitmask of lanes where predicate is true | `i32` |
| `WarpVote(op)` | Lane-predicate reduction | `i1` |

`VoteOp` enum: `ALL` (all lanes true?), `ANY` (any lane true?), `BALLOT`.

### Shared Memory (1 instruction)

#### `SharedMemAlloc` — Allocate workgroup-local memory

**Effects**: has side effects
**Result type**: `ptr`
**Semantics**: Memory is shared among all threads in the block.

### Divergence (1 instruction)

#### `DivergentBranch` — Branch with expected lane divergence

**Effects**: terminator, branch, divergent, has side effects
**Semantics**: Signals that different lanes may take different paths. May be
lowered to predicated instructions.

### Supporting Types

| Type | Values |
|------|--------|
| `ThreadDim` | `X`, `Y`, `Z` |
| `ComputeScope` | `WARP`, `BLOCK`, `DEVICE` |
| `MemorySpace` | `GENERIC(0)`, `GLOBAL(1)`, `CONSTANT(2)`, `SHARED(3)`, `LOCAL(4)`, `MANAGED_HEAP(5)` |
| `VoteOp` | `ALL`, `ANY`, `BALLOT` |

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

### CaptureMode

| Value | Description |
|-------|-------------|
| `BY_VALUE` | Capture by value (copy) |
| `BY_REF` | Capture by shared reference |
| `BY_MUT_REF` | Capture by mutable reference |

### BarrierType

| Value | Description |
|-------|-------------|
| `FIELD` | Instance field store |
| `ARRAY` | Array element store |
| `WEAK_FIELD` | Weak reference field |
| `STATIC` | Static field store |
| `UNKNOWN` | Unclassified barrier |

### Other Enums

- **`UnnamedAddr`**: `NONE`, `UNNAMED_ADDR`, `LOCAL_UNNAMED_ADDR`
- **`DLLStorageClass`**: `NONE`, `DLL_IMPORT`, `DLL_EXPORT`
- **`ComdatSelectionKind`**: `ANY`, `EXACT_MATCH`, `LARGEST`, `NO_DUPLICATES`, `SAME_SIZE`
- **`ClassVisibility`**: `PUBLIC`, `PACKAGE_PRIVATE`, `PROTECTED`, `PRIVATE`
- **`MemberVisibility`**: `PUBLIC`, `PROTECTED`, `PACKAGE_PRIVATE`, `PRIVATE`
- **`AnnotationRetention`**: `SOURCE`, `CLASS`, `RUNTIME`
- **`TypeVariance`**: `INVARIANT`, `COVARIANT`, `CONTRAVARIANT`
- **`ManagedCallDirection`**: `MANAGED_TO_NATIVE`, `NATIVE_TO_MANAGED`

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
It manages module structure (functions, blocks, globals, types) while
`InstructionBuilder` handles instruction emission through scoped interfaces.

### Java Example

```java
IrBuilder ir = new IrBuilder("my_module", Target.x86_64());
NativeScope b = ir.createInstructionBuilder(NativeScope.class);

// Declare external function
FunctionRef printf = ir.declareFunction("printf",
    List.of(Param.of("fmt", Type.OpaquePointer)), Type.I32, true);

// Add globals
GlobalRef counter = ir.addGlobal("counter", Type.I32, new Constant.I32(0));

// Define a function
try (DefinedFunction main = ir.defineFunction("main",
        List.of(Param.of("argc", Type.I32)), Type.I32)) {

    // Create forward-reference blocks
    BlockRef thenBlock = ir.createBlock("then");
    BlockRef elseBlock = ir.createBlock("else");

    // Create entry block (positions insertion point)
    ir.appendBlock("entry");
    Value cmp = b.icmp(ICmpPredicate.SGT, main.param("argc"), new Constant.I32(0));
    b.condBr(cmp, thenBlock, elseBlock);

    // Switch to then block
    ir.appendBlock(thenBlock);
    b.ret(new Constant.I32(1));

    // Switch to else block
    ir.appendBlock(elseBlock);
    b.ret(new Constant.I32(0));
}

Module module = ir.build();
```

### Kotlin Example

```kotlin
val ir = IrBuilder("my_module", Target.x86_64())
val b = ir.createInstructionBuilder<NativeScope>()

val printf = ir.declareFunction("printf", listOf(Param("fmt", Type.OpaquePointer)), Type.I32, isVarArg = true)
val counter = ir.addGlobal("counter", Type.I32, Constant.I32(0))

ir.defineFunction("main", listOf(Param("argc", Type.I32)), Type.I32).use { main ->
    val thenBlock = ir.createBlock("then")
    val elseBlock = ir.createBlock("else")

    ir.appendBlock("entry")
    val cmp = b.icmp(ICmpPredicate.SGT, main.param("argc"), Constant.I32(0))
    b.condBr(cmp, thenBlock, elseBlock)

    ir.appendBlock(thenBlock)
    b.ret(Constant.I32(1))

    ir.appendBlock(elseBlock)
    b.ret(Constant.I32(0))
}

val module = ir.build()
```

### Key API

| Method | Description |
|--------|-------------|
| `defineFunction(name, params, retType)` | Start function definition, returns `DefinedFunction` |
| `declareFunction(name, params, retType)` | External function declaration, returns `FunctionRef` |
| `finalizeFunction()` | Finish current function (or use `DefinedFunction.close()`) |
| `createBlock(label?)` | Create block reference without positioning (forward reference) |
| `appendBlock(label?)` | Create and position at new block, returns `BlockRef` |
| `appendBlock(blockRef)` | Position at existing block |
| `getInsertBlock()` | Get current block label |
| `createInstructionBuilder(scope)` | Create scoped instruction emitter |
| `addGlobal(name, type, ...)` | Add global variable, returns `GlobalRef` |
| `addStruct(name, fields, ...)` | Add struct type |
| `addClass(cls)` | Add class definition |
| `build()` | Finalize and return `Module` |

### DefinedFunction

`DefinedFunction` implements `Value` (usable as function reference in `call`)
and `AutoCloseable` (for try-with-resources / `use {}`).

| Method | Description |
|--------|-------------|
| `param(index)` | Get parameter by index |
| `param(name)` | Get parameter by name |
| `ref()` | Get `FunctionRef` for this function |

### BlockRef

`BlockRef` is used for all block references in terminators and phi nodes.
Created by `createBlock()` or `appendBlock()`. Anonymous blocks use
auto-generated names (`bb0`, `bb1`, `bb2`, ...).

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
- `ClosureInvokeOnce` linear-use check (same closure not reused)

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
| Arithmetic | 39 | Add, Sub, Mul, UDiv, SDiv, URem, SRem, Neg, FAdd, FSub, FMul, FDiv, FRem, FNeg, FAbs, FMA, FMin, FMax, Sqrt, Ceil, Floor, Round, FTrunc, CopySign, S/UAddOverflow, S/USubOverflow, S/UMulOverflow, S/UAddSat, S/USubSat, SMin, SMax, UMin, UMax, Abs |
| Bitwise | 14 | And, Or, Xor, Not, Shl, LShr, AShr, Rotl, Rotr, Ctlz, Cttz, Ctpop, BSwap, BitReverse |
| Comparison | 2 | ICmp, FCmp |
| Conversion | 13 | IntTrunc, ZExt, SExt, FPTrunc, FPExt, FPToUI, FPToSI, UIToFP, SIToFP, PtrToInt, IntToPtr, BitCast, AddrSpaceCast |
| Memory | 16 | Alloca, Load, Store, GEP, MemCpy, MemSet, MemMove, Prefetch, StackSave, StackRestore, LifetimeStart, LifetimeEnd, VAStart, VAEnd, VACopy, VAArg |
| Atomic | 3 | Fence, CmpXchg, AtomicRMW |
| Vector | 5 | ExtractElement, InsertElement, ShuffleVector, Splat, VectorReduce |
| Aggregate | 2 | ExtractValue, InsertValue |
| Terminator | 8 | Ret, Br, CondBr, Switch, IndirectBr, Unreachable, Trap, DebugTrap |
| Call | 3 | Call, Invoke, CallBr |
| SSA | 4 | Phi, Select, Freeze, PiNode |
| Debug | 5 | DebugLoc, DebugValue, DebugDeclare, Assume, Expect |
| Intrinsic | 2 | Intrinsic, InlineAsm |
| Exception | 7 | LandingPad, Resume, CatchSwitch, CatchPad, CleanupPad, CatchRet, CleanupRet |
| Runtime | 15 | GCAlloc, GCSafepoint, GCRoot, WriteBarrier, ReadBarrier, RefRetain, RefRelease, RefCount, CoroBegin, CoroEnd, CoroSuspend, CoroResume, CoroDestroy, CoroSize, GCRelocate |
| Interop | 6 | Pin, Unpin, InteriorPtr, ManagedCall, ManagedToDevice, DeviceRelease |
| Object | 36 | NewObject, NewArray, NewMultiArray, GetField, PutField, GetStatic, PutStatic, VirtualCall, InterfaceCall, SpecialCall, StaticCall, DynamicCall, ConstructorCall, InstanceOf, CheckCast, TypeId, ArrayGet, ArraySet, ArrayLength, MonitorEnter, MonitorExit, Throw, TryCatchRegion, Box, Unbox, ClosureCreate, ClosureInvoke, ClosureInvokeOnce, ConstructVariant, GetTag, GetVariantField, TagSwitch, CatchValue, MakeWeakRef, ReadWeakRef, ClearWeakRef |
| Deoptimization | 5 | FrameState, Guard, FixedGuard, Deoptimize, OSREntry |
| Compute | 21 | ThreadId, BlockId, WarpId, LaneId, BlockDim, GridDim, ComputeBarrier, ComputeFence, KernelLaunch, ComputeAtomicAdd, ComputeAtomicMin, ComputeAtomicMax, ComputeAtomicCAS, WarpShuffle, WarpShuffleDown, WarpShuffleUp, WarpShuffleXor, Ballot, WarpVote, SharedMemAlloc, DivergentBranch |
| **Total** | **206** | |
