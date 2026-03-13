# kgen

> **Experimental** — APIs are unstable and subject to change.

Compiler infrastructure and binary toolkit for the JVM. Build compilers, JITs, binary analysis tools, and native executables — all from Java or Kotlin, with zero native dependencies.

```
Source → [Your Frontend] → kgen IR → [Optimize] → x86-64 / ARM64 / RISC-V / WASM
                                                          ↓
                                                    ELF / PE / Mach-O / .wasm
```

## Use cases

**Build a compiler.** Emit kgen IR from your parser. The optimizer and code generators handle the rest — register allocation, instruction selection, binary output. Ship native executables for Linux, Windows, and macOS from one codebase.

**JIT-compile at runtime.** Load IR modules into the JIT engine, call compiled functions directly from Java via `MethodHandle`. Supports tiered compilation and lazy symbol resolution.

**Compile Java classes to native.** Feed `.class` files to `NativeCompiler` and get a standalone ELF, PE, or Mach-O executable. No JVM required at runtime.

**Generate code at runtime.** `DynamicMethod` compiles a single function on demand — define the body with the IR builder, call `invoke()`, get native performance with no intermediate files.

**Analyze binaries.** Read ELF, PE/COFF, Mach-O, WASM modules, JVM class files, and .NET assemblies. Query symbols, imports, relocations, sections, CLR metadata. Disassemble x86-64, ARM64, RISC-V, WASM, JVM bytecode, and CIL.

**Patch and transform binaries.** Rewrite RPATHs, rename symbols, diff binaries section-by-section. Demangle Itanium, MSVC, and Rust name schemes.

**Write an assembler.** Use the assembler APIs directly — `X86Assembler`, `Arm64Assembler`, `RiscVAssembler`, `WasmAssembler` — for structured machine code emission with type-safe register and memory operand models.

## Examples

### IR to native executable

```java
var ir = new IrBuilder("mymodule", Target.x86_64());

ir.createFunction("main", List.of(), Type.I32);
ir.positionAtEnd(ir.appendBlock("entry"));
ir.ret(new Constant.I32(42));
ir.finalizeFunction();

byte[] elf = new X86CodeGenerator().generate(ir.build(),
    new CodeGenOptions(OptLevel.O0, false, PICMode.STATIC,
        RelocationModel.STATIC, OutputFormat.BINARY));

Files.write(Path.of("output"), elf);
```

### JIT compilation

```java
var jit = new JitEngine(new X86CodeGenerator());
jit.addModule(irModule);

// Get a MethodHandle and call it
MethodHandle add = jit.handle("add",
    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
int result = (int) add.invokeExact(2, 3); // 5
```

### Java to native executable

```java
byte[] classBytes = Files.readAllBytes(Path.of("App.class"));
byte[] exe = new NativeCompiler(Target.x86_64(), OutputPlatform.LINUX)
    .compile(List.of(classBytes), "com/example/App");
Files.write(Path.of("app"), exe);
// ./app runs without a JVM
```

### Runtime code generation

```java
var square = DynamicMethod.native_("square",
    Signature.returning(TypeRef.I32).param("x", TypeRef.I32).build());
square.body((ir, params) -> {
    ir.ret(ir.mul(params.get(0), params.get(0)));
});
int result = (int) square.invoke(7); // 49
square.close();
```

### Binary inspection

```kotlin
// ELF
val elf = ElfReader.read(File("/usr/bin/ls").readBytes())
for (sym in elf.symbols) println("${sym.name} @ 0x${sym.value.toString(16)}")

// PE
val pe = PeReader.read(File("kernel32.dll").readBytes())
for (imp in pe.importDirectories) println("${imp.name}: ${imp.entries.size} imports")

// Unified reflection API — works across ELF, PE, Mach-O, WASM, JVM, CLR
val mod = Module.fromFile("libc.so.6")
for (fn in mod.functions()) println("${fn.name()}: ${fn.signature()}")
```

### Assembler

```kotlin
val asm = X86Assembler()
asm.push(rbp)
asm.mov(rbp, rsp)
asm.mov(eax, 42)
asm.pop(rbp)
asm.ret()
val machineCode: ByteArray = asm.assemble()
```

### Disassembler

```kotlin
val disasm = X86Disassembler()
val instructions = disasm.disassembleRaw(codeBytes, baseAddress = 0x401000)
for (inst in instructions) println(inst) // "0x00401000:  push rbp"
```

### CLI

```bash
kgen compile hello.ir -t x86_64 -o hello
kgen compile hello.ir -t wasm -o hello.wasm
kgen info -v /usr/bin/ls
kgen disasm --syntax att --bytes /usr/bin/ls
kgen symbols -D /usr/bin/ls
kgen patch input.elf --set-rpath /opt/lib --rename-symbol old new -o output.elf
kgen diff old.elf new.elf
echo "_ZN3foo3barEi" | kgen demangle
kgen classinfo App.class -c
kgen wasminfo module.wasm --all
```

The CLI builds as a native binary via GraalVM native-image (~18 MB, no JVM startup).

## Architecture

### IR

SSA-based, LLVM-style. 177 instruction types covering arithmetic, control flow, memory, aggregates, exceptions, atomics, SIMD, GC intrinsics, and debug info. Text and binary serialization. Full verifier. Tiered: high-level IR for managed targets (JVM, WASM), low-level for native, with lowering passes to convert between them.

### Optimization

Mem2Reg, constant folding, DCE, GVN, inlining, jump threading, LICM, SROA. Preset pipelines O0, O1, O2, Os. Pass infrastructure supports custom passes.

### Code generators

| Target | Assembler | Disassembler | Code generator |
|--------|-----------|--------------|----------------|
| x86-64 | 918 instruction forms, VEX/EVEX | Full | Full (register allocator, SSE2 FP, varargs, structs, TLS) |
| ARM64 | Full | Full | Full (AAPCS64, FP, TLS) |
| RISC-V | RV64IMAFDC | Full | RV64IM (FP, TLS) |
| WASM | 406 opcodes | Full | Full (module reader/writer) |
| JVM | Full | Full | Full (class file reader/writer/builder) |
| CIL/.NET | Full | Full | Full (CLR metadata, mixed-mode) |

### Binary formats

Read, write, and link ELF (32/64-bit). Read, write PE/COFF. Read, write Mach-O (including chained fixups). Read/write WASM modules, JVM class files, .NET CLR metadata, ar archives (.a/.lib, GNU + BSD).

Static and dynamic linkers for ELF. PE/COFF linker. Shared library linker for Mach-O (.dylib).

### JIT engine

Compiles IR to native code, loads into executable memory, resolves symbols, patches relocations. Tiered compilation (interpreter tier-0, baseline tier-1, optimized tier-2). Lazy stubs for deferred compilation. Managed runtime with bump-heap allocator and mark-sweep GC.

### Reflection

Unified binary inspection API across ELF, PE, Mach-O, WASM, JVM class files, and .NET assemblies. Query symbols, functions, types, methods, fields from any supported format through a single interface. Runtime process introspection on Linux and Windows.

### Java-to-native compilation

Compiles JVM bytecode to native code. Supports arithmetic, branches, loops, recursion, instance methods, object allocation, static/instance fields, arrays, string constants, exception handling, `invokedynamic` (string concat, lambdas), vtable dispatch. Platform-conditional compilation via `Kgen.isWindows()` / `Kgen.isLinux()` / `Kgen.isMacOS()` compile-time constants.

## Building

Requires JDK 21+.

```bash
./gradlew test                    # run tests (~17,200)
./gradlew :cli:shadowJar          # fat JAR
JAVA_HOME=/path/to/graalvm ./gradlew :cli:nativeCompile  # native binary
```

## Maven coordinates

```xml
<dependency>
    <groupId>org.kgen</groupId>
    <artifactId>kgen</artifactId>
</dependency>
```

## Status

Experimental. Under active development. APIs will change.

## License

MIT
