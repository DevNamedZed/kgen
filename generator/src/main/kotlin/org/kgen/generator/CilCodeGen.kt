package org.kgen.generator

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

data class CilInsn(
    val code: Int,
    val name: String,
    val operand: String,
    val summary: String,
    val twoBytePrefix: Boolean = false,
)

fun cilMain() {
    val resourceDir = "generator/src/main/resources/cil"
    val outputDir = "kgen/src/main/kotlin/org/kgen/target/clr/generated"

    val gson = Gson()
    val type = object : TypeToken<List<CilInsn>>() {}.type
    val insns: List<CilInsn> = gson.fromJson(File(resourceDir, "opcodes.json").reader(), type)
    println("Loaded ${insns.size} CIL instructions")

    val outDir = File(outputDir)
    outDir.mkdirs()

    generateCilAssemblerOps(insns, File(outDir, "CilAssemblerOps.kt"))
    println("Generated CilAssemblerOps.kt: ${insns.size} methods")
}

fun generateCilAssemblerOps(insns: List<CilInsn>, out: File) {
    val sb = StringBuilder()
    sb.appendLine("// Generated from cil/opcodes.json — do not edit")
    sb.appendLine("package org.kgen.target.clr.generated")
    sb.appendLine()
    sb.appendLine("import org.kgen.target.clr.asm.CilLabel")
    sb.appendLine("import org.kgen.target.clr.asm.CilToken")
    sb.appendLine()
    sb.appendLine("/**")
    sb.appendLine(" * Generated assembler methods for CIL bytecode (ECMA-335).")
    sb.appendLine(" *")
    sb.appendLine(" * Each method emits the corresponding CIL instruction.")
    sb.appendLine(" * The concrete assembler provides emit primitives and may override")
    sb.appendLine(" * methods for smart encoding (e.g., short/long local/arg switching).")
    sb.appendLine(" */")
    sb.appendLine("abstract class CilAssemblerOps {")
    sb.appendLine()
    sb.appendLine("    protected abstract fun emitByte(v: Int)")
    sb.appendLine("    protected abstract fun emitU16(v: Int)")
    sb.appendLine("    protected abstract fun emitI32(v: Int)")
    sb.appendLine("    protected abstract fun emitI64(v: Long)")
    sb.appendLine("    protected abstract fun emitF32(v: Float)")
    sb.appendLine("    protected abstract fun emitF64(v: Double)")
    sb.appendLine("    protected abstract fun emitToken(token: Int)")
    sb.appendLine("    protected abstract fun emitBranch(opcode: Int, twoBytePrefix: Boolean, label: CilLabel)")
    sb.appendLine()

    for (insn in insns) {
        sb.appendLine("    /** ${insn.summary}. */")

        val emitOpcode = if (insn.twoBytePrefix) {
            "emitByte(0xFE); emitByte(${insn.code and 0xFF})"
        } else {
            "emitByte(${insn.code})"
        }

        when (insn.operand) {
            "none" -> {
                sb.appendLine("    fun ${insn.name}() {")
                sb.appendLine("        $emitOpcode")
                sb.appendLine("    }")
            }
            "i8", "u8" -> {
                sb.appendLine("    open fun ${insn.name}(value: Int) {")
                sb.appendLine("        $emitOpcode; emitByte(value)")
                sb.appendLine("    }")
            }
            "u16" -> {
                sb.appendLine("    open fun ${insn.name}(index: Int) {")
                sb.appendLine("        $emitOpcode; emitU16(index)")
                sb.appendLine("    }")
            }
            "i32" -> {
                sb.appendLine("    fun ${insn.name}(value: Int) {")
                sb.appendLine("        $emitOpcode; emitI32(value)")
                sb.appendLine("    }")
            }
            "i64" -> {
                sb.appendLine("    fun ${insn.name}(value: Long) {")
                sb.appendLine("        $emitOpcode; emitI64(value)")
                sb.appendLine("    }")
            }
            "r32" -> {
                sb.appendLine("    fun ${insn.name}(value: Float) {")
                sb.appendLine("        $emitOpcode; emitF32(value)")
                sb.appendLine("    }")
            }
            "r64" -> {
                sb.appendLine("    fun ${insn.name}(value: Double) {")
                sb.appendLine("        $emitOpcode; emitF64(value)")
                sb.appendLine("    }")
            }
            "token" -> {
                sb.appendLine("    fun ${insn.name}(token: CilToken) {")
                sb.appendLine("        $emitOpcode; emitToken(token.value)")
                sb.appendLine("    }")
            }
            "branch" -> {
                sb.appendLine("    fun ${insn.name}(label: CilLabel) {")
                sb.appendLine("        emitBranch(${insn.code}, ${insn.twoBytePrefix}, label)")
                sb.appendLine("    }")
            }
            "switch" -> {
                // Switch is special — left for the concrete assembler
                sb.appendLine("    abstract fun ${insn.name}(labels: List<CilLabel>)")
            }
        }
        sb.appendLine()
    }

    sb.appendLine("}")

    out.writeText(sb.toString())
}
