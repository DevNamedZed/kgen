# kgen Architecture

## Module Structure

```
kgen/
├── ir/          Core IR: types, values, instructions, module model, builders, printer, verifier
├── pass/        ModulePass interface, PassPipeline, optimization/lowering passes
├── object/      Object file model + readers/writers (ELF, PE, Mach-O, WASM, JVM .class)
├── linker/      Linker interface, full link options
├── tools/       Binary utilities (inspector, patcher, diff, demangler, hex dump, loader)
├── backend-wasm/    WASM code generator + assembler
├── backend-jvm/     JVM bytecode generator (java.lang.classfile, no ASM library)
├── backend-x86_64/  x86-64 native backend
├── backend-arm64/   ARM64 native backend
├── backend-riscv/   RISC-V native backend
└── backend-msil/    MSIL/.NET backend
```

## Compilation Pipeline

```
Source Code
    │
    ▼
┌──────────────────┐
│  Frontend (yours) │  AST → IR translation using IrBuilder
└────────┬─────────┘
         │  Module (IR)
         ▼
┌──────────────────┐
│  Pass Pipeline    │  Optimization + lowering (high-level → low-level)
└────────┬─────────┘
         │  Module (optimized IR)
         ▼
┌──────────────────┐
│  Code Generator   │  IR → target binary (CodeGenerator.generate())
└────────┬─────────┘
         │  ByteArray
         ▼
┌──────────────────┐
│  Object Writer    │  Optional: wrap in ELF/PE/Mach-O container
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│  Linker           │  Optional: link multiple objects into executable
└──────────────────┘
```

## IR Design

### SSA Form

All values are defined exactly once (Single Static Assignment). Mutable variables
use the alloca/load/store pattern, which a mem2reg pass converts to phi nodes:

```
; Before mem2reg:              ; After mem2reg:
entry:                         entry:
  %x = alloca i32               br label %loop
  store i32 0, ptr %x
  br label %loop              loop:
                                 %x = phi i32 [0, %entry], [%x.next, %loop]
loop:                            %x.next = add i32 %x, 1
  %x.val = load i32, ptr %x     ...
  %x.next = add i32 %x.val, 1
  store i32 %x.next, ptr %x
  ...
```

### Tiered Instructions

**Low-level** (165 instruction types):
- Integer arithmetic: add, sub, mul, udiv, sdiv, urem, srem + overflow/saturating variants
- Float arithmetic: fadd, fsub, fmul, fdiv, frem + math (sqrt, ceil, floor, fma, etc.)
- Bitwise: and, or, xor, shl, lshr, ashr, rotl, rotr
- Bit manipulation: ctlz, cttz, ctpop, bswap, bitreverse
- Comparison: icmp (10 predicates), fcmp (16 predicates)
- Memory: alloca, load, store, getelementptr, fence, cmpxchg, atomicrmw, memcpy/set/move
- Control flow: ret, br, condbr, switch, indirectbr, unreachable
- Calls: call, invoke, callbr + varargs (va_start/end/copy/arg)
- SSA: phi, select, freeze
- Conversions: inttrunc, zext, sext, fptrunc, fpext, fptosi, fptoui, sitofp, uitofp, ptrtoint, inttoptr, bitcast
- Vectors: extractelement, insertelement, shufflevector, splat, vector.reduce
- Aggregates: extractvalue, insertvalue
- Exception handling: landingpad, resume, catchswitch, catchpad, cleanuppad, catchret, cleanupret
- Coroutines: coro.begin, coro.end, coro.suspend, coro.resume, coro.destroy, coro.size
- Misc: intrinsic, inline asm, debug info, assume, expect

**High-level** (for JVM/WASM):
- Objects: new, newarray, newmultiarray
- Fields: getfield, putfield, getstatic, putstatic
- Dispatch: virtualcall, interfacecall, specialcall, staticcall, dynamiccall, constructorcall
- Types: instanceof, checkcast, typeid
- Arrays: arrayget, arrayset, arraylength
- Monitors: monitorenter, monitorexit
- Exceptions: throw, trycatch
- Boxing: box, unbox
- Closures: closure.create, closure.invoke
- Tagged unions: construct.variant, gettag, getvariantfield, tagswitch
- GC: gc.alloc, gc.safepoint, gc.root
- Refcounting: ref.retain, ref.release, ref.count

### Type System

The type system covers both native and managed needs:

| Category | Types |
|----------|-------|
| Integers | i1, i8, i16, i32, i64, i128, iN (arbitrary) |
| Floats | f16, bf16, f32, f64, f80, f128 |
| Pointers | ptr (opaque), ptr<T> (typed), ptr addrspace(N) |
| References | ref<T>, ref<T>?, weakref<T> |
| Aggregates | [N x T] (array), <N x T> (vector), { T... } (struct), union, tagged union |
| Functions | (T...) -> T |
| OOP | class @Name, interface @Name |
| Generics | !T (type param), Base<T...> (parameterized) |
| Special | void, label, metadata, token |

## Backend API

Each backend can implement up to three interfaces:

```kotlin
// High-level: Module → binary
interface CodeGenerator {
    fun generate(module: Module, options: CodeGenOptions): ByteArray
}

// Mid-level: instruction-by-instruction emission
interface Assembler<I : Any> {
    fun emit(instruction: I)
    fun assemble(): ByteArray
}

// Reverse: binary → instructions
interface Disassembler<I : Any> {
    fun disassemble(bytes: ByteArray): List<DisassembledInstruction<I>>
}
```

Backends are discovered via `TargetRegistry` + `ServiceLoader`, keeping the IR module
completely decoupled from any specific target.

## Builder API

One builder class — `IrBuilder` — is the module. Two ways to build function bodies:

```
IrBuilder ("the module")
├── .function()       → FunctionScope (structured: variables, if/while, named comparisons)
├── .createFunction() → low-level SSA (basic blocks, phi nodes, insertion points)
├── .addClass(), .addGlobal(), .declareFunction(), ...
└── .build() → Module
```

`FunctionScope` translates structured constructs into SSA automatically:

| You write... | It generates... |
|-------------|-----------------|
| `variable(i32(0))` / `get(v)` / `set(v, x)` | `alloca` / `load` / `store` |
| `ifThen(cond) { }` | `condBr` + basic blocks + merge |
| `whileLoop { }` | `br` + condition block + body block + exit block |
| `breakLoop()` / `continueLoop()` | `br` to exit/condition block |
| `lt(a, b)` | `icmp slt a, b` |
| `intCast(val, type)` | `trunc` or `sext` based on bit widths |
| `raw { }` | escape hatch to full IrBuilder |

For tests and hand-written IR, there's also the `module { }` DSL (see `DslBuilders.kt`).

## File Layout (`:ir` module)

```
ir/src/main/kotlin/com/kgen/ir/
├── Type.kt              Sealed type hierarchy (30+ type variants)
├── Value.kt             Value hierarchy (Parameter, InstructionRef, GlobalRef, Constants)
├── Instruction.kt       165 instruction data classes with KDoc
├── Module.kt            Module, IrFunction, BasicBlock, ClassDef, InterfaceDef, enums, etc.
├── InstructionEmitter.kt Abstract base class with ~80 builder methods (shared by DSL + imperative)
├── FunctionScope.kt     Structured function building: variables, if/while/for, named comparisons
├── DslBuilders.kt       module() DSL, ModuleBuilder, FunctionBuilder, BlockBuilder, constant helpers
├── IrBuilder.kt         Imperative builder with insertion points (LLVM IRBuilder-style)
├── ClassDefBuilder.kt   classDef() / interfaceDef() DSL builders
├── IrPrinter.kt         Module → human-readable IR text
├── IrVerifier.kt        Structural + type verification
├── IrSerializer.kt      Binary serialization (Module ↔ ByteArray)
└── CodeGenerator.kt     Backend interfaces (CodeGenerator, Assembler, Disassembler, TargetRegistry)
```
