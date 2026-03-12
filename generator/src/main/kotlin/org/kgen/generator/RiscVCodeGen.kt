package org.kgen.generator

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import java.io.File

// ── RISC-V JSON data model ─────────────────────────────────────────

data class RiscVInstruction(
    val mnemonic: String,
    val summary: String,
    val forms: List<RiscVForm>,
)

data class RiscVForm(
    val operands: List<String>,
    val encoding: RiscVEncoding,
    val feature: String? = null,
) {
    val resolvedFeature: String get() = feature ?: "i"
}

data class RiscVEncoding(
    val opcode: Int,
    val funct3: Int? = null,
    val funct7: Int? = null,
    val funct5: Int? = null,
    val fmt: Int? = null,
    val rs2: Int? = null,
    val imm: Int? = null,
    val format: String,
)

// ── Operand classification ──────────────────────────────────────────

enum class RiscVOpCategory {
    GP, FP, MEM, IMM12, IMM, SHAMT, LABEL, NONE
}

fun classifyRiscVOperand(op: String): RiscVOpCategory = when (op) {
    "gp" -> RiscVOpCategory.GP
    "fp" -> RiscVOpCategory.FP
    "mem" -> RiscVOpCategory.MEM
    "imm12" -> RiscVOpCategory.IMM12
    "imm" -> RiscVOpCategory.IMM
    "shamt" -> RiscVOpCategory.SHAMT
    "label" -> RiscVOpCategory.LABEL
    else -> RiscVOpCategory.NONE
}

fun riscvOperandToKotlinType(cat: RiscVOpCategory): String = when (cat) {
    RiscVOpCategory.GP -> "RiscVGpReg"
    RiscVOpCategory.FP -> "RiscVFpReg"
    RiscVOpCategory.MEM -> "RiscVMemory"
    RiscVOpCategory.IMM12, RiscVOpCategory.IMM, RiscVOpCategory.SHAMT -> "Int"
    RiscVOpCategory.LABEL -> "String"
    RiscVOpCategory.NONE -> "Int"
}

fun riscvOperandToTypeName(op: String): String = when (op) {
    "gp" -> "GP"; "fp" -> "FP"; "mem" -> "MEM"
    "imm12" -> "IMM12"; "imm" -> "IMM"; "shamt" -> "SHAMT"
    "label" -> "LABEL"
    else -> "IMM"
}

data class RiscVMethodParam(val name: String, val type: String, val category: RiscVOpCategory)

fun riscvParamName(position: Int, cat: RiscVOpCategory): String = when (cat) {
    RiscVOpCategory.GP -> when (position) {
        0 -> "rd"; 1 -> "rs1"; 2 -> "rs2"; else -> "r${position + 1}"
    }
    RiscVOpCategory.FP -> when (position) {
        0 -> "fd"; 1 -> "fs1"; 2 -> "fs2"; else -> "f${position + 1}"
    }
    RiscVOpCategory.MEM -> "mem"
    RiscVOpCategory.IMM12 -> "imm"
    RiscVOpCategory.IMM -> "imm"
    RiscVOpCategory.SHAMT -> "shamt"
    RiscVOpCategory.LABEL -> "label"
    RiscVOpCategory.NONE -> "arg${position + 1}"
}

fun riscvFormToParams(form: RiscVForm): List<RiscVMethodParam> {
    val params = mutableListOf<RiscVMethodParam>()
    val namesSeen = mutableSetOf<String>()
    for ((i, op) in form.operands.withIndex()) {
        val cat = classifyRiscVOperand(op)
        val type = riscvOperandToKotlinType(cat)
        var name = riscvParamName(i, cat)
        if (name in namesSeen) {
            var suffix = 2
            while ("$name$suffix" in namesSeen) suffix++
            name = "$name$suffix"
        }
        namesSeen.add(name)
        params.add(RiscVMethodParam(name, type, cat))
    }
    return params
}

fun sanitizeRiscVMnemonic(mnemonic: String): String = when (mnemonic) {
    "and" -> "and_"
    "or" -> "or_"
    "xor" -> "xor_"
    "rem" -> "rem_"
    "div" -> "div_"
    "in" -> "in_"
    "break" -> "break_"
    "return" -> "return_"
    else -> mnemonic
}

// ── Main ────────────────────────────────────────────────────────────

fun riscvMain() {
    val resourceDir = "generator/src/main/resources/riscv"
    val outputDir = "kgen/src/main/kotlin/org/kgen/target/riscv"

    val instructions = loadRiscVInstructions(File(resourceDir))
    val totalForms = instructions.sumOf { it.forms.size }
    println("Loaded ${instructions.size} mnemonics, $totalForms forms")

    val outDir = File(outputDir)
    File(outDir, "generated").mkdirs()

    generateRiscVOperandType(File(outDir, "generated/RiscVOperandType.kt"))
    generateRiscVFeature(instructions, File(outDir, "generated/RiscVFeature.kt"))
    generateRiscVInstructionData(instructions, File(outDir, "generated/RiscVInstructionData.kt"))
    generateRiscVAssemblerOps(instructions, File(outDir, "generated/RiscVAssemblerOps.kt"))

    val features = instructions.flatMap { i -> i.forms.map { it.resolvedFeature } }.distinct().sorted()
    println("Generated files in $outDir/generated/")
    println("  Features: ${features.joinToString()}")
    println("  RiscVInstructionData.kt: $totalForms forms")
    println("  RiscVAssemblerOps.kt: ${instructions.size} instruction groups")
}

fun loadRiscVInstructions(dir: File): List<RiscVInstruction> {
    val gson = Gson()
    val manifest = JsonParser.parseReader(File(dir, "instructions.json").reader()).asJsonObject
    val files = manifest.getAsJsonArray("files")
    val type = object : TypeToken<List<RiscVInstruction>>() {}.type

    return files.flatMap { fileElem ->
        val file = File(dir, fileElem.asString)
        if (!file.exists()) {
            System.err.println("Warning: ${file.name} not found, skipping")
            emptyList()
        } else {
            gson.fromJson<List<RiscVInstruction>>(file.reader(), type)
        }
    }
}

// ── Generate RiscVOperandType ───────────────────────────────────────

fun generateRiscVOperandType(out: File) {
    val sb = StringBuilder()
    sb.appendLine("// Generated — do not edit")
    sb.appendLine("package org.kgen.target.riscv")
    sb.appendLine()
    sb.appendLine("/** Operand type classification for RISC-V instruction forms. */")
    sb.appendLine("enum class RiscVOperandType {")
    val types = listOf("GP", "FP", "MEM", "IMM12", "IMM", "SHAMT", "LABEL")
    for ((i, t) in types.withIndex()) {
        val comma = if (i < types.size - 1) "," else ";"
        sb.appendLine("    $t$comma")
    }
    sb.appendLine("}")
    out.writeText(sb.toString())
}

// ── Generate RiscVFeature ───────────────────────────────────────────

fun generateRiscVFeature(instructions: List<RiscVInstruction>, out: File) {
    val features = instructions.flatMap { i -> i.forms.map { it.resolvedFeature } }.distinct().sorted()
    val sb = StringBuilder()
    sb.appendLine("// Generated — do not edit")
    sb.appendLine("package org.kgen.target.riscv")
    sb.appendLine()
    sb.appendLine("/** RISC-V ISA extension flags. */")
    sb.appendLine("enum class RiscVFeature(val specName: String) {")
    for ((i, f) in features.withIndex()) {
        val enumName = f.uppercase().replace('-', '_').replace('.', '_')
        val comma = if (i < features.size - 1) "," else ";"
        sb.appendLine("    $enumName(\"$f\")$comma")
    }
    sb.appendLine("}")
    out.writeText(sb.toString())
}

// ── Generate RiscVInstructionData ───────────────────────────────────

fun generateRiscVInstructionData(instructions: List<RiscVInstruction>, out: File) {
    val sb = StringBuilder()
    sb.appendLine("// Generated — do not edit")
    sb.appendLine("package org.kgen.target.riscv")
    sb.appendLine()
    sb.appendLine("/**")
    sb.appendLine(" * Encoding metadata for a single RISC-V instruction form.")
    sb.appendLine(" *")
    sb.appendLine(" * RISC-V encoding fields follow the ISA specification:")
    sb.appendLine(" * R-type: funct7[31:25] | rs2[24:20] | rs1[19:15] | funct3[14:12] | rd[11:7] | opcode[6:0]")
    sb.appendLine(" * I-type: imm[31:20] | rs1[19:15] | funct3[14:12] | rd[11:7] | opcode[6:0]")
    sb.appendLine(" * S-type: imm[31:25] | rs2[24:20] | rs1[19:15] | funct3[14:12] | imm[11:7] | opcode[6:0]")
    sb.appendLine(" * B-type: imm[31:25] | rs2[24:20] | rs1[19:15] | funct3[14:12] | imm[11:7] | opcode[6:0]")
    sb.appendLine(" * U-type: imm[31:12] | rd[11:7] | opcode[6:0]")
    sb.appendLine(" * J-type: imm[31:12] | rd[11:7] | opcode[6:0]")
    sb.appendLine(" */")
    sb.appendLine("data class RiscVEncodingInfo(")
    sb.appendLine("    val opcode: Int,")
    sb.appendLine("    val funct3: Int = 0,")
    sb.appendLine("    val funct7: Int = 0,")
    sb.appendLine("    val funct5: Int = 0,")
    sb.appendLine("    val fmt: Int = 0,")
    sb.appendLine("    val rs2Fixed: Int = -1,")
    sb.appendLine("    val immFixed: Int = -1,")
    sb.appendLine("    val format: RiscVFormat,")
    sb.appendLine(")")
    sb.appendLine()

    val allFormats = instructions.flatMap { i -> i.forms.map { it.encoding.format } }.distinct().sorted()
    sb.appendLine("/** RISC-V instruction encoding format. */")
    sb.appendLine("enum class RiscVFormat {")
    for ((i, f) in allFormats.withIndex()) {
        val comma = if (i < allFormats.size - 1) "," else ";"
        sb.appendLine("    $f$comma")
    }
    sb.appendLine("}")
    sb.appendLine()

    sb.appendLine("data class RiscVInstructionForm(")
    sb.appendLine("    val mnemonic: String,")
    sb.appendLine("    val operands: List<RiscVOperandType>,")
    sb.appendLine("    val encoding: RiscVEncodingInfo,")
    sb.appendLine("    val feature: RiscVFeature,")
    sb.appendLine(")")
    sb.appendLine()

    sb.appendLine("object RiscVInstructionData {")
    sb.appendLine("    val forms: List<RiscVInstructionForm> by lazy { buildForms() }")
    sb.appendLine()
    sb.appendLine("    val byMnemonic: Map<String, List<RiscVInstructionForm>> by lazy {")
    sb.appendLine("        forms.groupBy { it.mnemonic }")
    sb.appendLine("    }")
    sb.appendLine()

    val allForms = instructions.flatMap { insn -> insn.forms.map { insn to it } }
    val chunkSize = 100
    val chunks = allForms.chunked(chunkSize)

    sb.appendLine("    private fun buildForms(): List<RiscVInstructionForm> {")
    sb.appendLine("        val list = ArrayList<RiscVInstructionForm>(${allForms.size})")
    for (i in chunks.indices) {
        sb.appendLine("        buildForms$i(list)")
    }
    sb.appendLine("        return list")
    sb.appendLine("    }")

    for ((chunkIdx, chunk) in chunks.withIndex()) {
        sb.appendLine()
        sb.appendLine("    private fun buildForms$chunkIdx(list: MutableList<RiscVInstructionForm>) {")
        for ((insn, form) in chunk) {
            val operandList = form.operands.joinToString(", ") { "RiscVOperandType.${riscvOperandToTypeName(it)}" }
            val featureEnum = form.resolvedFeature.uppercase().replace('-', '_').replace('.', '_')
            val enc = form.encoding
            val encParts = mutableListOf<String>()
            encParts.add("opcode = ${enc.opcode}")
            if (enc.funct3 != null) encParts.add("funct3 = ${enc.funct3}")
            if (enc.funct7 != null) encParts.add("funct7 = ${enc.funct7}")
            if (enc.funct5 != null) encParts.add("funct5 = ${enc.funct5}")
            if (enc.fmt != null) encParts.add("fmt = ${enc.fmt}")
            if (enc.rs2 != null) encParts.add("rs2Fixed = ${enc.rs2}")
            if (enc.imm != null) encParts.add("immFixed = ${enc.imm}")
            encParts.add("format = RiscVFormat.${enc.format}")
            sb.appendLine("        list.add(RiscVInstructionForm(\"${insn.mnemonic}\", listOf($operandList), RiscVEncodingInfo(${encParts.joinToString(", ")}), RiscVFeature.$featureEnum))")
        }
        sb.appendLine("    }")
    }
    sb.appendLine("}")
    out.writeText(sb.toString())
}

// ── Generate RiscVAssemblerOps ──────────────────────────────────────

fun generateRiscVAssemblerOps(instructions: List<RiscVInstruction>, out: File) {
    val sb = StringBuilder()
    sb.appendLine("// Generated — do not edit")
    sb.appendLine("package org.kgen.target.riscv")
    sb.appendLine()
    sb.appendLine("/**")
    sb.appendLine(" * Generated assembler dispatch methods for RISC-V (RV64IMAFDC).")
    sb.appendLine(" *")
    sb.appendLine(" * Each method corresponds to a RISC-V instruction mnemonic.")
    sb.appendLine(" * Overloads select the correct encoding based on operand types.")
    sb.appendLine(" * The abstract [encodeRiscV] method is implemented by the hand-written assembler.")
    sb.appendLine(" */")
    sb.appendLine("abstract class RiscVAssemblerOps {")
    sb.appendLine()
    sb.appendLine("    /** Encode and emit a single RISC-V instruction. Implemented by the assembler. */")
    sb.appendLine("    protected abstract fun encodeRiscV(info: RiscVEncodingInfo, vararg operands: Any)")
    sb.appendLine()

    for (insn in instructions) {
        val methodName = sanitizeRiscVMnemonic(insn.mnemonic)

        data class Sig(val params: List<String>)
        val seen = mutableSetOf<Sig>()

        for (form in insn.forms) {
            val params = riscvFormToParams(form)
            val sig = Sig(params.map { it.type })

            if (!seen.add(sig)) continue

            val paramStr = if (params.isEmpty()) "" else params.joinToString(", ") { "${it.name}: ${it.type}" }
            val argList = if (params.isEmpty()) "" else ", " + params.joinToString(", ") { it.name }

            val enc = form.encoding
            val encParts = mutableListOf<String>()
            encParts.add("opcode = ${enc.opcode}")
            if (enc.funct3 != null) encParts.add("funct3 = ${enc.funct3}")
            if (enc.funct7 != null) encParts.add("funct7 = ${enc.funct7}")
            if (enc.funct5 != null) encParts.add("funct5 = ${enc.funct5}")
            if (enc.fmt != null) encParts.add("fmt = ${enc.fmt}")
            if (enc.rs2 != null) encParts.add("rs2Fixed = ${enc.rs2}")
            if (enc.imm != null) encParts.add("immFixed = ${enc.imm}")
            encParts.add("format = RiscVFormat.${enc.format}")

            sb.appendLine("    /** ${insn.summary}: ${form.operands.joinToString(", ")} */")
            sb.appendLine("    fun $methodName($paramStr) {")
            sb.appendLine("        encodeRiscV(RiscVEncodingInfo(${encParts.joinToString(", ")})$argList)")
            sb.appendLine("    }")
            sb.appendLine()
        }
    }

    sb.appendLine("}")
    out.writeText(sb.toString())
}
