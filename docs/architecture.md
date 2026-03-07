# kgen Architecture

## Module Structure

Single `:kgen` module with hierarchical packages:

```
org.kgen.ir.*              Core IR: types, values, instructions, module model, builders
  ir.build                 IrBuilder, FunctionScope, DSL builders
  ir.text                  IrPrinter, IrParser, IrSerializer
  ir.verify                IrVerifier
  ir.target                Target, CPU/feature enums
  ir.codegen               CodeGenerator, Assembler, Disassembler, TargetRegistry
  ir.types                 ClassDef, InterfaceDef, EnumDef, StructDef

org.kgen.pass              ModulePass, PassPipeline, optimization passes

org.kgen.binary.*          Object file model, readers/writers
  binary.elf               ELF reader/writer/linker (static, dynamic, shared)
  binary.pe                PE/COFF reader/writer/linker (exe, DLL)
  binary.pe.clr            CLR metadata reader/writer
  binary.macho             Mach-O reader/writer/linker
  binary.jvm               JVM class file reader/writer
  binary.ar                Archive reader/writer

org.kgen.tools             Binary inspector, patcher, diff, demangler, hex dump

org.kgen.backend.wasm.*    WASM backend (asm, codegen, disasm, module reader/writer)
org.kgen.backend.x86.*     x86-64 backend (asm, codegen, disasm)
org.kgen.backend.arm64.*   ARM64 backend (asm, codegen, disasm)
org.kgen.backend.riscv.*   RISC-V backend (asm, codegen, disasm)
```

Separate modules: `:cli` (22-command CLI tool), `:generator` (JSON spec → Kotlin codegen), `:integration` (end-to-end tests)

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

## File Layout (`:kgen` module, IR packages)

```
kgen/src/main/kotlin/org/kgen/ir/
├── Type.kt              Sealed type hierarchy (30+ type variants)
├── Value.kt             Value hierarchy (Parameter, InstructionRef, GlobalRef, Constants)
├── Instruction.kt       165 instruction data classes
├── Module.kt            Module, IrFunction, BasicBlock
├── build/
│   ├── IrBuilder.kt         Imperative builder with insertion points
│   ├── FunctionScope.kt     Structured: variables, if/while/for, comparisons
│   ├── InstructionEmitter.kt Shared ~80 builder methods
│   └── DslBuilders.kt       module() DSL, constant helpers
├── text/
│   ├── IrPrinter.kt         Module → human-readable text
│   ├── IrParser.kt          Text → Module (recursive descent)
│   └── IrSerializer.kt      Binary serialization (Module ↔ ByteArray)
├── verify/
│   └── IrVerifier.kt        SSA dominance + type checking
├── target/
│   └── Target.kt            Target, CPU enums, feature enums
├── codegen/
│   └── CodeGenerator.kt     CodeGenerator, Assembler, Disassembler, TargetRegistry
└── types/
    ├── ClassDef.kt          ClassDef, ClassDefBuilder
    ├── InterfaceDef.kt      InterfaceDef
    ├── EnumDef.kt           EnumDef, EnumVariant
    └── StructDef.kt         StructDef
```
