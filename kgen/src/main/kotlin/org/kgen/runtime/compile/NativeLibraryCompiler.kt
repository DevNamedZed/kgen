package org.kgen.runtime.compile

import org.kgen.binary.*
import org.kgen.binary.elf.ElfMachine
import org.kgen.binary.elf.ElfSharedLinker
import org.kgen.binary.macho.MachO
import org.kgen.binary.pe.PeDllLinker
import org.kgen.codegen.CodeGenerator
import org.kgen.ir.*
import org.kgen.ir.target.Arch
import org.kgen.ir.target.Target
import org.kgen.pass.Mem2Reg

/**
 * Compiles classfiles to a native shared library.
 *
 * Methods annotated with `@KgenExport` become exported symbols in the
 * shared library, callable from C, C++, Rust, Python, or any language
 * with C FFI support.
 *
 * ```java
 * var compiler = new NativeLibraryCompiler(Target.x86_64(), OutputPlatform.LINUX);
 * byte[] so = compiler.compile(List.of(classBytes));
 * Files.write(Path.of("libcrypto.so"), so);
 * ```
 */
class NativeLibraryCompiler(
    private val target: Target,
    private val platform: OutputPlatform = OutputPlatform.LINUX,
    private val codeGenerator: CodeGenerator? = null,
    private val soname: String? = null,
) {

    /**
     * Compile classfiles to a shared library.
     * All `@KgenExport` methods become exported symbols.
     */
    fun compile(classFiles: List<ByteArray>): ByteArray {
        val layout = ClassLayout.build(classFiles)
        val rc = RuntimeCompiler(target, layout)
        val modules = classFiles.map { rc.compile(it) }
        val merged = mergeModules(modules)

        val optimized = Mem2Reg().run(merged)
        val gen = codeGenerator ?: resolveCodeGenerator()
        val code = gen.generateCode(optimized)
        val obj = code.toObjectFile(objectFormat(), architecture())
        return link(listOf(obj))
    }

    /**
     * Generate a C header file for the exported functions.
     *
     * @param classFiles the .class files to analyze
     * @param guardName include guard macro name (default: derived from soname or "KGEN_EXPORTS_H")
     * @return C header file contents as a string
     */
    @JvmOverloads
    fun generateHeader(classFiles: List<ByteArray>, guardName: String? = null): String {
        val layout = ClassLayout.build(classFiles)
        val rc = RuntimeCompiler(target, layout)
        val modules = classFiles.map { rc.compile(it) }
        val merged = mergeModules(modules)
        return CHeaderGenerator.generate(Mem2Reg().run(merged), guardName ?: defaultGuardName())
    }

    private fun mergeModules(modules: List<Module>): Module {
        return if (modules.size == 1) modules[0] else Module(
            name = modules.first().name,
            targetTriple = modules.first().targetTriple,
            functions = modules.flatMap { it.functions },
            globals = modules.flatMap { it.globals },
            structs = modules.flatMap { it.structs },
        )
    }

    private fun defaultGuardName(): String {
        val name = soname?.replace(Regex("[^a-zA-Z0-9]"), "_")?.uppercase() ?: "KGEN_EXPORTS"
        return "${name}_H"
    }

    private fun link(objects: List<ObjectFile>): ByteArray {
        return when (platform) {
            OutputPlatform.LINUX, OutputPlatform.LINUX_DYNAMIC -> ElfSharedLinker(
                machine = elfMachine().code,
                soname = soname,
            ).link(objects)

            OutputPlatform.WINDOWS -> PeDllLinker(
                dllName = soname ?: "output.dll",
            ).link(objects)

            OutputPlatform.MACOS -> org.kgen.binary.macho.MachODylibLinker(
                cpuType = machOCpuType(),
                installName = soname,
            ).link(objects)
        }
    }

    private fun resolveCodeGenerator(): CodeGenerator = when (target.arch) {
        Arch.X86_64 -> org.kgen.target.x86.codegen.X86CodeGenerator()
        Arch.ARM64 -> org.kgen.target.arm64.codegen.Arm64CodeGenerator()
        Arch.RISCV64 -> org.kgen.target.riscv.codegen.RiscVCodeGenerator()
        else -> error("No code generator for ${target.arch}")
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

    private fun machOCpuType(): Int = when (target.arch) {
        Arch.X86_64 -> MachO.CPU_TYPE_X86_64
        Arch.ARM64 -> MachO.CPU_TYPE_ARM64
        else -> error("No Mach-O CPU type for ${target.arch}")
    }
}
