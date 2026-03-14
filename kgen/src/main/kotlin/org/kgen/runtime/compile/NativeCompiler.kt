package org.kgen.runtime.compile

import org.kgen.binary.*
import org.kgen.binary.elf.ElfLinker
import org.kgen.binary.elf.ElfMachine
import org.kgen.binary.elf.ElfStaticLinker
import org.kgen.binary.macho.MachO
import org.kgen.binary.macho.MachOLinker
import org.kgen.binary.pe.PeLinker
import org.kgen.codegen.CodeGenOptions
import org.kgen.codegen.CodeGenerator
import org.kgen.codegen.CompiledCode
import org.kgen.ir.*
import org.kgen.ir.instructions.*
import org.kgen.ir.target.Arch
import org.kgen.ir.target.Target
import org.kgen.pass.Mem2Reg
import org.kgen.unmanaged.lib.StdlibProvider

/**
 * AOT compiler that translates JVM `.class` files into standalone native executables.
 *
 * Implements the full Java-to-native pipeline:
 * 1. Parse `.class` files and validate against the Runtime Subset
 * 2. Lower JVM bytecode to kgen IR via [BytecodeToIrLowering][org.kgen.runtime.compile.BytecodeToIrLowering]
 * 3. Merge modules, inject stdlib (if referenced), and wire up `<clinit>` static initializers
 * 4. Run optimization passes (Mem2Reg)
 * 5. Generate native machine code via the appropriate [CodeGenerator]
 * 6. Link into a standalone executable (ELF, PE, or Mach-O via [ElfStaticLinker], [PeLinker], or [MachOLinker])
 *
 * Supports x86-64, ARM64, and RISC-V targets across Linux, Windows, and macOS.
 * If the `main` method accepts `String[]`, a wrapper is generated that converts
 * C-style `(argc, argv)` to a Java-style array using stack allocation.
 *
 * ```java
 * var compiler = new NativeCompiler(Target.x86_64(), OutputPlatform.LINUX);
 * byte[] exe = compiler.compile(List.of(classBytes), "com/example/Main");
 * Files.write(Path.of("myapp"), exe);
 * // ./myapp runs natively — no JVM required
 * ```
 *
 * See `spec/roadmap.md` for the full list of supported bytecode instructions and features.
 */
class NativeCompiler(
    private val target: Target,
    private val platform: OutputPlatform = OutputPlatform.LINUX,
    private val codeGenerator: CodeGenerator? = null,
    private val options: CodeGenOptions = CodeGenOptions(),
) {

    /**
     * Compile classfiles to a native executable.
     *
     * The class specified by [mainClass] must have a static `main` method
     * (any signature — it becomes the entry point). If no [mainClass] is
     * specified, the first `@KgenExport` method named "main" is used.
     *
     * @param classFiles list of raw `.class` file byte arrays
     * @param mainClass fully qualified class name (e.g. "com/example/Main"), or null for auto-detect
     * @return the linked executable bytes (ELF, PE, or Mach-O depending on platform)
     */
    @JvmOverloads
    fun compile(classFiles: List<ByteArray>, mainClass: String? = null): ByteArray {
        val modules = compileToModules(classFiles)
        val merged = mergeModules(modules, mainClass)
        val obj = generateObject(merged)
        return link(listOf(obj))
    }

    /**
     * Compile classfiles to IR modules without linking.
     * Useful for inspection or custom pipelines.
     */
    fun compileToModules(classFiles: List<ByteArray>): List<Module> {
        val layout = ClassLayout.build(classFiles)
        val rc = RuntimeCompiler(target, layout)
        return classFiles.map { rc.compile(it) }
    }

    /**
     * Compile classfiles to an object file (relocatable) without linking.
     */
    fun compileToObject(classFiles: List<ByteArray>, mainClass: String? = null): ObjectFile {
        val modules = compileToModules(classFiles)
        val merged = mergeModules(modules, mainClass)
        return generateObject(merged)
    }

    private fun mergeModules(modules: List<Module>, mainClass: String?): Module {
        if (modules.size == 1 && mainClass == null) return injectClinitCalls(addStdlib(modules[0]))

        // Merge all modules into one, conditionally include stdlib
        val allFunctions = modules.flatMap { it.functions }.toMutableList()
        val allGlobalsMut = modules.flatMap { it.globals }.toMutableList()

        val referencedNames = allFunctions.flatMap { fn ->
            fn.blocks.flatMap { block ->
                block.instructions.filterIsInstance<Call>()
                    .mapNotNull { (it.function as? GlobalRef)?.name }
            }
        }.toSet()
        val stdlib = StdlibProvider.generate(target)
        val stdlibNames = stdlib.functions.filter { !it.isExternal }.map { it.name }.toSet()
        if (referencedNames.any { it in stdlibNames }) {
            allFunctions.addAll(stdlib.functions)
            allGlobalsMut.addAll(stdlib.globals)
        }

        val allStructs = modules.flatMap { it.structs }
        val allGlobalCtors = modules.flatMap { it.globalCtors }
        var merged = Module(
            name = modules.first().name,
            targetTriple = modules.first().targetTriple,
            functions = allFunctions.toList(),
            globals = allGlobalsMut.toList(),
            structs = allStructs,
            globalCtors = allGlobalCtors,
        )

        // If mainClass specified, ensure the class has a "main" function and rename it
        if (mainClass != null) {
            val prefix = mainClass.replace('/', '_') + "_"
            val mainFn = merged.functions.firstOrNull { it.name == "${prefix}main" }
                ?: merged.functions.firstOrNull { it.name == "main" }
                ?: error("No main method found in $mainClass")

            if (mainFn.name != "main") {
                val renamed = mainFn.copy(name = "main")
                merged = merged.copy(
                    functions = merged.functions.map { if (it === mainFn) renamed else it }
                )
            }
        }

        merged = wrapMainWithArgvConversion(merged)
        return injectClinitCalls(merged)
    }

    /**
     * If the main function takes a String[] parameter, generate a wrapper that
     * converts C-style (argc, argv) to a Java-style String[] array and calls
     * the user's main. The wrapper becomes the new "main" symbol.
     *
     * Array layout: [i32 length][ptr arg0][ptr arg1]...
     * Each argv[i] is a null-terminated C string pointer, passed through directly.
     * Uses stack allocation (alloca) so no heap allocator is needed.
     */
    private fun wrapMainWithArgvConversion(module: Module): Module {
        val mainFn = module.functions.firstOrNull { it.name == "main" } ?: return module

        // Only wrap if main takes exactly one OpaquePointer parameter (String[] args)
        if (mainFn.params.size != 1 || mainFn.params[0].type != Type.OpaquePointer) return module

        // Rename the user's main to __user_main
        val userMainName = "__user_main"
        val renamedMain = mainFn.copy(name = userMainName)

        // Build the wrapper: main(argc: i32, argv: ptr) → calls __user_main(String[])
        val ptrSize = if (target.arch == Arch.ARM64 || target.arch == Arch.X86_64) 8 else 4
        val headerSize = 4 // i32 length field

        val argc = Parameter("argc", Type.I32, 0)
        val argv = Parameter("argv", Type.OpaquePointer, 1)

        var nextId = 0
        fun ref(type: Type) = InstructionRef("%${nextId++}", type)

        val instructions = mutableListOf<Instruction>()

        // %0 = zext i32 %argc to i64
        val argcExt = ref(Type.I64)
        instructions.add(ZExt(argcExt, argc, Type.I64))

        // %1 = mul i64 %0, ptrSize   (bytes needed for pointer slots)
        val slotBytes = ref(Type.I64)
        instructions.add(Mul(slotBytes, argcExt, Constant.I64(ptrSize.toLong())))

        // %2 = add i64 %1, headerSize (total allocation size)
        val totalSize = ref(Type.I64)
        instructions.add(Add(totalSize, slotBytes, Constant.I64(headerSize.toLong())))

        // %3 = alloca i8, %2  (stack-allocate the String[] array)
        val arrayPtr = ref(Type.OpaquePointer)
        instructions.add(Alloca(arrayPtr, Type.I8, totalSize, align = 8))

        // Store length: store i32 %argc, ptr %3
        instructions.add(Store(argc, arrayPtr))

        // Copy argv pointers into the array with a loop.
        //
        // entry block ends with: br loop_header
        // loop_header: phi i64 [0, entry], [%next_i, loop_body]
        //              icmp slt i64 %i, %argc_ext → cond_br loop_body, done
        // loop_body:   gep argv[i], gep array[header + i*ptrSize], load, store, i+1 → br loop_header
        // done:        call __user_main(arrayPtr), ret

        val loopI = InstructionRef("%loop_i", Type.I64)
        instructions.add(Br(BlockRef("loop_header")))

        // loop_header block
        val headerInsts = mutableListOf<Instruction>()
        headerInsts.add(Phi(loopI, listOf(
            Constant.I64(0) to BlockRef("entry"),
            InstructionRef("%next_i", Type.I64) to BlockRef("loop_body"),
        )))
        val loopCond = ref(Type.I1)
        headerInsts.add(ICmp(loopCond, ICmpPredicate.SLT, loopI, argcExt))
        headerInsts.add(CondBr(loopCond, BlockRef("loop_body"), BlockRef("done")))

        // loop_body block
        val bodyInsts = mutableListOf<Instruction>()
        // Load argv[i]: gep ptr, argv, i → load ptr
        val argvSlotPtr = ref(Type.OpaquePointer)
        bodyInsts.add(GetElementPtr(argvSlotPtr, Type.OpaquePointer, argv, listOf(loopI)))
        val argStr = ref(Type.OpaquePointer)
        bodyInsts.add(Load(argStr, argvSlotPtr, Type.OpaquePointer))
        // Compute array slot: header + i * ptrSize
        val iBytes = ref(Type.I64)
        bodyInsts.add(Mul(iBytes, loopI, Constant.I64(ptrSize.toLong())))
        val slotOff = ref(Type.I64)
        bodyInsts.add(Add(slotOff, iBytes, Constant.I64(headerSize.toLong())))
        // GEP from arrayPtr by slotOff bytes (i8 GEP)
        val destSlotPtr = ref(Type.OpaquePointer)
        bodyInsts.add(GetElementPtr(destSlotPtr, Type.I8, arrayPtr, listOf(slotOff)))
        // Store the string pointer
        bodyInsts.add(Store(argStr, destSlotPtr))
        // i + 1
        val nextI = InstructionRef("%next_i", Type.I64)
        bodyInsts.add(Add(nextI, loopI, Constant.I64(1)))
        bodyInsts.add(Br(BlockRef("loop_header")))

        // done block: call __user_main(arrayPtr) and return
        val doneInsts = mutableListOf<Instruction>()
        val userMainRef = GlobalRef(userMainName, Type.OpaquePointer)

        if (mainFn.returnType == Type.Void) {
            doneInsts.add(Call(
                dest = null, function = userMainRef, args = listOf(arrayPtr),
                returnType = Type.Void,
            ))
            doneInsts.add(Ret(Constant.I32(0)))
        } else {
            val retVal = ref(mainFn.returnType)
            doneInsts.add(Call(
                dest = retVal, function = userMainRef, args = listOf(arrayPtr),
                returnType = mainFn.returnType,
            ))
            doneInsts.add(Ret(retVal))
        }

        val wrapperFn = IrFunction(
            name = "main",
            params = listOf(argc, argv),
            returnType = if (mainFn.returnType == Type.Void) Type.I32 else mainFn.returnType,
            blocks = listOf(
                BasicBlock("entry", instructions),
                BasicBlock("loop_header", headerInsts),
                BasicBlock("loop_body", bodyInsts),
                BasicBlock("done", doneInsts),
            ),
        )

        val newFunctions = module.functions.map { if (it.name == "main") renamedMain else it } + wrapperFn
        return module.copy(functions = newFunctions)
    }

    /**
     * Inject static initializer calls at the start of main().
     * Global constructors (from <clinit>) need to run before main.
     */
    private fun addStdlib(module: Module): Module {
        val referencedNames = module.functions.flatMap { fn ->
            fn.blocks.flatMap { block ->
                block.instructions.filterIsInstance<Call>()
                    .mapNotNull { (it.function as? GlobalRef)?.name }
            }
        }.toSet()

        val stdlib = StdlibProvider.generate(target)
        val stdlibNames = stdlib.functions.filter { !it.isExternal }.map { it.name }.toSet()
        if (referencedNames.none { it in stdlibNames }) return module

        return module.copy(
            functions = module.functions + stdlib.functions,
            globals = module.globals + stdlib.globals,
        )
    }

    private fun injectClinitCalls(module: Module): Module {
        if (module.globalCtors.isEmpty()) return module

        val mainFn = module.functions.firstOrNull { it.name == "main" } ?: return module
        val entryBlock = mainFn.blocks.firstOrNull() ?: return module

        // Build call instructions for each clinit
        val clinitCalls = module.globalCtors.map { ctor ->
            Call(
                dest = null,
                function = GlobalRef(ctor.function, Type.Pointer(Type.Void)),
                args = emptyList(),
                returnType = Type.Void,
            )
        }

        // Prepend clinit calls to the entry block
        val newBlock = entryBlock.copy(
            instructions = clinitCalls + entryBlock.instructions
        )
        val newBlocks = listOf(newBlock) + mainFn.blocks.drop(1)
        val newMain = mainFn.copy(blocks = newBlocks)
        return module.copy(
            functions = module.functions.map { if (it.name == "main") newMain else it }
        )
    }

    internal fun generateCompiledCode(module: Module): CompiledCode {
        val optimized = Mem2Reg().run(module)
        val gen = codeGenerator ?: resolveCodeGenerator()
        return gen.generateCode(optimized)
    }

    internal fun generateObject(module: Module): ObjectFile {
        return generateCompiledCode(module).toObjectFile(objectFormat(), architecture())
    }

    internal fun link(objects: List<ObjectFile>): ByteArray {
        return when (platform) {
            OutputPlatform.LINUX -> ElfStaticLinker(
                machine = elfMachine().code,
            ).link(objects)

            OutputPlatform.LINUX_DYNAMIC -> ElfLinker(
                machine = elfMachine().code,
                interpreter = linuxInterpreter(),
                sharedLibs = listOf("libc.so.6"),
            ).link(objects)

            OutputPlatform.WINDOWS -> PeLinker(
                machine = peMachine(),
            ).link(objects)

            OutputPlatform.MACOS -> MachOLinker(
                cpuType = machOCpuType(),
            ).link(objects)
        }
    }

    private fun resolveCodeGenerator(): CodeGenerator {
        return when (target.arch) {
            Arch.X86_64 -> org.kgen.target.x86.codegen.X86CodeGenerator()
            Arch.ARM64 -> org.kgen.target.arm64.codegen.Arm64CodeGenerator()
            Arch.RISCV64 -> org.kgen.target.riscv.codegen.RiscVCodeGenerator()
            else -> error("No code generator for ${target.arch}")
        }
    }

    private fun objectFormat(): ObjectFormat = when (platform) {
        OutputPlatform.LINUX, OutputPlatform.LINUX_DYNAMIC -> ObjectFormat.ELF
        OutputPlatform.WINDOWS -> ObjectFormat.PE_COFF
        OutputPlatform.MACOS -> ObjectFormat.MACH_O
    }

    private fun architecture(): Architecture = when (target.arch) {
        Arch.X86_64 -> when (platform) {
            OutputPlatform.LINUX, OutputPlatform.LINUX_DYNAMIC -> Architecture.X86_64_LINUX
            OutputPlatform.WINDOWS -> Architecture.X86_64_WINDOWS
            OutputPlatform.MACOS -> Architecture.X86_64_MACOS
        }
        Arch.ARM64 -> when (platform) {
            OutputPlatform.LINUX, OutputPlatform.LINUX_DYNAMIC -> Architecture.AARCH64_LINUX
            OutputPlatform.MACOS -> Architecture.AARCH64_MACOS
            OutputPlatform.WINDOWS -> Architecture(ArchType.AARCH64, os = "windows")
        }
        Arch.RISCV64 -> Architecture(ArchType.RISCV64, os = "linux", environment = "gnu")
        else -> error("Unsupported architecture: ${target.arch}")
    }

    private fun elfMachine(): ElfMachine = when (target.arch) {
        Arch.X86_64 -> ElfMachine.X86_64
        Arch.ARM64 -> ElfMachine.AARCH64
        Arch.RISCV64 -> ElfMachine.RISCV
        else -> error("No ELF machine for ${target.arch}")
    }

    private fun peMachine(): Int = when (target.arch) {
        Arch.X86_64 -> org.kgen.binary.pe.PeConstants.MACHINE_AMD64
        Arch.ARM64 -> org.kgen.binary.pe.PeConstants.MACHINE_ARM64
        else -> error("No PE machine for ${target.arch}")
    }

    private fun machOCpuType(): Int = when (target.arch) {
        Arch.X86_64 -> MachO.CPU_TYPE_X86_64
        Arch.ARM64 -> MachO.CPU_TYPE_ARM64
        else -> error("No Mach-O CPU type for ${target.arch}")
    }

    private fun linuxInterpreter(): String = when (target.arch) {
        Arch.X86_64 -> "/lib64/ld-linux-x86-64.so.2"
        Arch.ARM64 -> "/lib/ld-linux-aarch64.so.1"
        Arch.RISCV64 -> "/lib/ld-linux-riscv64-lp64d.so.1"
        else -> "/lib64/ld-linux.so.2"
    }
}


