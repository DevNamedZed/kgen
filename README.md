# kgen

Compiler backend toolkit for the JVM. One IR in, native binaries out.

```
Your Language → [Parser] → kgen IR → [Optimize] → x86-64 / ARM64 / WASM / RISC-V
                                                         ↓
                                                   ELF / PE / Mach-O / .wasm
```

Pure JVM, no native dependencies.

## Examples

### Compile IR to an ELF executable

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

### Hello world with dynamic linking

```kotlin
val ir = IrBuilder("hello", Target.x86_64())

val strRef = ir.addGlobal("msg", Type.Array(Type.I8, 14),
    Constant.StringConst("Hello, World!"), isConstant = true, linkage = Linkage.INTERNAL)
ir.declareFunction("puts", listOf(Param("s", Type.OpaquePointer)), Type.I32)

ir.createFunction("main", emptyList(), Type.I32)
ir.positionAtEnd(ir.appendBlock("entry"))
ir.call("puts", listOf(strRef), Type.I32)
ir.ret(Constant.I32(0))
ir.finalizeFunction()

val binary = X86CodeGenerator().generate(ir.build(),
    CodeGenOptions(outputFormat = OutputFormat.BINARY))
```

### Read and inspect binaries

```kotlin
val elf = ElfReader.read(File("/usr/bin/ls").readBytes())
for (sym in elf.symbols) println("${sym.name} @ 0x${sym.value.toString(16)}")

val pe = PeReader.read(File("kernel32.dll").readBytes())
for (imp in pe.importDirectories) println("${imp.name}: ${imp.entries.size} imports")

val instructions = X86Disassembler().disassembleRaw(codeBytes, baseAddress = 0x401000)
for (inst in instructions) println(inst)  // "0x00401000:  push rbp"
```

### Use the assembler directly

```kotlin
val asm = X86Assembler()
asm.push(rbp)
asm.mov(rbp, rsp)
asm.mov(eax, 42)
asm.pop(rbp)
asm.ret_()
val machineCode: ByteArray = asm.assemble()
```

### CLI

The `kgen` CLI builds as a native binary via GraalVM native-image.

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

## What's in the box

**IR** — SSA-based, LLVM-style. ~120 instruction types. Text and binary serialization. Full verifier.

**Optimization** — Mem2Reg, constant folding, DCE, GVN, inlining, jump threading, LICM, SROA. Preset pipelines O0–O3.

**Backends** — x86-64 (full: 918 instruction forms, VEX/EVEX, register allocator, SSE2 FP, varargs, structs), ARM64 (full: AAPCS64, FP), RISC-V (RV64IMAFDC assembler/disassembler, RV64IM codegen), WASM (406 opcodes, module reader/writer).

**Binary formats** — Read, write, and link ELF, PE/COFF, Mach-O. Read/write WASM modules, JVM class files, .NET CLR metadata, ar archives.

**Tools** — Binary inspection, patching, diffing, demangling (Itanium/MSVC/Rust), hex dump. All usable as APIs or via the CLI.

## Building

Requires JDK 21+.

```bash
./gradlew test
./gradlew :cli:shadowJar
JAVA_HOME=/path/to/graalvm ./gradlew :cli:nativeCompile
```

## Status

Work in progress.

## License

MIT
