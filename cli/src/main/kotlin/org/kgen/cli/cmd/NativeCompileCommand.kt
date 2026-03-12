package org.kgen.cli.cmd

import org.kgen.cli.*
import org.kgen.ir.target.Target
import org.kgen.runtime.compile.NativeCompiler
import org.kgen.runtime.compile.OutputPlatform
import java.io.File

object NativeCompileCommand {
    fun run(args: List<String>) {
        val parsed = parseArgs(args)
        val outputPath = parsed.get("o", "output")
        val targetArch = parsed.get("t", "target") ?: "x86_64"
        val platformStr = parsed.get("p", "platform") ?: detectPlatform()
        val mainClass = parsed.get("m", "main")

        // Collect all positional args as class files
        val classFilePaths = parsed.positional
        if (classFilePaths.isEmpty()) {
            err("usage: kgen native-compile <class-files...> -o <output>")
            err("  -o, --output FILE     Output executable path")
            err("  -t, --target ARCH     Target architecture (x86_64, aarch64, riscv64)")
            err("  -p, --platform OS     Target platform (linux, windows, macos)")
            err("  -m, --main CLASS      Main class (fully qualified, e.g. com/example/Main)")
            return
        }

        if (outputPath == null) {
            err("missing output: use -o <output-file>")
            return
        }

        val target = resolveTarget(targetArch) ?: run {
            err("unknown target: $targetArch (available: x86_64, aarch64, riscv64)")
            return
        }
        val platform = resolvePlatform(platformStr) ?: run {
            err("unknown platform: $platformStr (available: linux, windows, macos)")
            return
        }

        val classFiles = classFilePaths.map { path ->
            val file = File(path)
            if (!file.exists()) {
                err("file not found: $path")
                return
            }
            file.readBytes()
        }

        val compiler = NativeCompiler(target, platform)
        val exe = try {
            compiler.compile(classFiles, mainClass)
        } catch (e: Exception) {
            err("compilation failed: ${e.message}")
            return
        }

        File(outputPath).writeBytes(exe)
        println("native-compile: ${classFilePaths.size} class file(s) -> $outputPath (${exe.size} bytes)")
    }

    private fun resolveTarget(name: String): Target? = when (name.lowercase()) {
        "x86_64", "x86-64", "amd64" -> Target.x86_64()
        "aarch64", "arm64" -> Target.arm64()
        "riscv64", "riscv" -> Target.riscv64()
        else -> null
    }

    private fun resolvePlatform(name: String): OutputPlatform? = when (name.lowercase()) {
        "linux" -> OutputPlatform.LINUX
        "linux-dynamic" -> OutputPlatform.LINUX_DYNAMIC
        "windows" -> OutputPlatform.WINDOWS
        "macos", "darwin" -> OutputPlatform.MACOS
        else -> null
    }

    private fun detectPlatform(): String {
        val os = System.getProperty("os.name", "").lowercase()
        return when {
            os.contains("linux") -> "linux"
            os.contains("windows") -> "windows"
            os.contains("mac") || os.contains("darwin") -> "macos"
            else -> "linux"
        }
    }
}
