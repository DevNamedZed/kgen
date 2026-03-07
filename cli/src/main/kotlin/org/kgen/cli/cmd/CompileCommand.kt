package org.kgen.cli.cmd

import org.kgen.cli.*
import org.kgen.ir.text.IrParser
import org.kgen.ir.text.IrSerializer
import org.kgen.ir.codegen.*
import org.kgen.backend.x86.codegen.X86CodeGenerator
import org.kgen.backend.wasm.codegen.WasmCodeGenerator
import org.kgen.backend.arm64.codegen.Arm64CodeGenerator
import org.kgen.backend.riscv.codegen.RiscVCodeGenerator
import java.io.ByteArrayInputStream
import java.io.File

object CompileCommand {
    private val KGEN_IR_MAGIC = byteArrayOf(0x4B, 0x47, 0x45, 0x4E) // "KGEN"

    fun run(args: List<String>) {
        val parsed = parseArgs(args)
        val path = parsed.requireFile("kgen compile <file.ir|file.kir> -t <target> -o <output>")
        val data = readFileOrExit(path)
        val targetName = parsed.get("t", "target")
        val outputPath = parsed.get("o", "output")
        val optStr = parsed.get("O", "opt")

        if (targetName == null) {
            err("missing target: use -t <wasm|x86_64|aarch64|riscv>")
            return
        }

        if (outputPath == null) {
            err("missing output: use -o <output-file>")
            return
        }

        val module = try {
            if (isBinaryIr(data)) {
                IrSerializer().deserialize(ByteArrayInputStream(data))
            } else {
                IrParser.parse(String(data))
            }
        } catch (e: Exception) {
            err("failed to parse IR: ${e.message}")
            return
        }

        val generator = resolveGenerator(targetName)
        if (generator == null) {
            err("unknown target: $targetName (available: wasm, x86_64, aarch64, riscv)")
            return
        }

        val optLevel = when (optStr?.lowercase()) {
            "0", null -> OptLevel.O0
            "1" -> OptLevel.O1
            "2" -> OptLevel.O2
            "3" -> OptLevel.O3
            "s" -> OptLevel.OS
            "z" -> OptLevel.OZ
            else -> {
                err("unknown optimization level: $optStr (use 0-3, s, z)")
                return
            }
        }

        val options = CodeGenOptions(optimizationLevel = optLevel)

        val output = try {
            generator.generate(module, options)
        } catch (e: Exception) {
            err("code generation failed: ${e.message}")
            return
        }

        File(outputPath).writeBytes(output)
        println("$targetName: ${output.size} bytes -> $outputPath")
    }

    private fun isBinaryIr(data: ByteArray): Boolean {
        if (data.size < 4) return false
        return data[0] == KGEN_IR_MAGIC[0] && data[1] == KGEN_IR_MAGIC[1]
            && data[2] == KGEN_IR_MAGIC[2] && data[3] == KGEN_IR_MAGIC[3]
    }

    private fun resolveGenerator(target: String): CodeGenerator? = when (target.lowercase()) {
        "wasm" -> WasmCodeGenerator()
        "x86_64", "x86-64", "x86", "amd64" -> X86CodeGenerator()
        "aarch64", "arm64" -> Arm64CodeGenerator()
        "riscv", "riscv64" -> RiscVCodeGenerator()
        else -> null
    }
}
