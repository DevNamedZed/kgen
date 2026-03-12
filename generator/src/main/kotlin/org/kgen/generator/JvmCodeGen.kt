package org.kgen.generator

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

data class JvmInsn(
    val code: Int,
    val name: String,
    val operand: String,
    val summary: String,
    val shortBase: Int? = null,
)

fun jvmMain() {
    val resourceDir = "generator/src/main/resources/jvm"
    val outputDir = "kgen/src/main/kotlin/org/kgen/target/jvm/generated"

    val gson = Gson()
    val type = object : TypeToken<List<JvmInsn>>() {}.type
    val insns: List<JvmInsn> = gson.fromJson(File(resourceDir, "opcodes.json").reader(), type)
    println("Loaded ${insns.size} JVM instructions")

    val outDir = File(outputDir)
    outDir.mkdirs()

    generateJvmAssemblerOps(insns, File(outDir, "JvmAssemblerOps.kt"))
    println("Generated JvmAssemblerOps.kt: ${insns.size} methods")
}

fun generateJvmAssemblerOps(insns: List<JvmInsn>, out: File) {
    val sb = StringBuilder()
    sb.appendLine("// Generated from jvm/opcodes.json — do not edit")
    sb.appendLine("package org.kgen.target.jvm.generated")
    sb.appendLine()
    sb.appendLine("/**")
    sb.appendLine(" * Generated assembler methods for JVM bytecode.")
    sb.appendLine(" *")
    sb.appendLine(" * Each method emits the corresponding bytecode instruction.")
    sb.appendLine(" * Methods are `open` where the concrete assembler may need to override")
    sb.appendLine(" * (e.g., branch instructions with label support, ldc with size switching).")
    sb.appendLine(" */")
    sb.appendLine("abstract class JvmAssemblerOps {")
    sb.appendLine()
    sb.appendLine("    protected abstract fun emitByte(b: Int)")
    sb.appendLine("    protected abstract fun emitShort(v: Int)")
    sb.appendLine()

    for (insn in insns) {
        sb.appendLine("    /** ${insn.summary}. */")
        when (insn.operand) {
            "none" -> {
                sb.appendLine("    fun ${insn.name}() {")
                sb.appendLine("        emitByte(${insn.code})")
                sb.appendLine("    }")
            }
            "local" -> {
                val shortBase = insn.shortBase
                sb.appendLine("    open fun ${insn.name}(index: Int) {")
                if (shortBase != null) {
                    sb.appendLine("        if (index in 0..3) emitByte($shortBase + index)")
                    sb.appendLine("        else { emitByte(${insn.code}); emitByte(index) }")
                } else {
                    sb.appendLine("        emitByte(${insn.code}); emitByte(index)")
                }
                sb.appendLine("    }")
            }
            "byte" -> {
                sb.appendLine("    fun ${insn.name}(value: Int) {")
                sb.appendLine("        emitByte(${insn.code}); emitByte(value and 0xFF)")
                sb.appendLine("    }")
            }
            "short" -> {
                sb.appendLine("    fun ${insn.name}(value: Int) {")
                sb.appendLine("        emitByte(${insn.code}); emitShort(value)")
                sb.appendLine("    }")
            }
            "cpref8" -> {
                sb.appendLine("    open fun ${insn.name}(index: Int) {")
                sb.appendLine("        emitByte(${insn.code}); emitByte(index)")
                sb.appendLine("    }")
            }
            "cpref" -> {
                sb.appendLine("    fun ${insn.name}(index: Int) {")
                sb.appendLine("        emitByte(${insn.code}); emitShort(index)")
                sb.appendLine("    }")
            }
            "branch" -> {
                sb.appendLine("    open fun ${insn.name}(label: String) {")
                sb.appendLine("        emitBranch(${insn.code}, label)")
                sb.appendLine("    }")
            }
            "local_byte" -> {
                sb.appendLine("    open fun ${insn.name}(index: Int, increment: Int) {")
                sb.appendLine("        emitByte(${insn.code}); emitByte(index); emitByte(increment and 0xFF)")
                sb.appendLine("    }")
            }
            "interface" -> {
                sb.appendLine("    fun ${insn.name}(index: Int, count: Int) {")
                sb.appendLine("        emitByte(${insn.code}); emitShort(index); emitByte(count); emitByte(0)")
                sb.appendLine("    }")
            }
            "atype" -> {
                sb.appendLine("    fun ${insn.name}(type: Int) {")
                sb.appendLine("        emitByte(${insn.code}); emitByte(type)")
                sb.appendLine("    }")
            }
        }
        sb.appendLine()
    }

    sb.appendLine("    /** Emit a branch instruction with label-based offset resolution. */")
    sb.appendLine("    protected abstract fun emitBranch(opcode: Int, label: String)")
    sb.appendLine("}")

    out.writeText(sb.toString())
}
