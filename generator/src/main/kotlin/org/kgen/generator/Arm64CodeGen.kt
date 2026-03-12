package org.kgen.generator

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import java.io.File

// ── ARM64 JSON data model ───────────────────────────────────────────

data class Arm64Instruction(
    val mnemonic: String,
    val summary: String,
    val forms: List<Arm64Form>,
)

data class Arm64Form(
    val operands: List<String>,
    val encoding: Arm64Encoding,
    val feature: String? = null,
) {
    val resolvedFeature: String get() = feature ?: "baseline"
}

data class Arm64Encoding(
    val base: String,   // hex string e.g. "8B000000"
    val format: String, // encoding format name
)

// ── Operand classification ──────────────────────────────────────────

enum class Arm64OpCategory {
    X, W, D, S, IMM12, IMM16, IMM, COND, LABEL, NONE
}

fun classifyArm64Operand(op: String): Arm64OpCategory = when (op) {
    "x" -> Arm64OpCategory.X
    "w" -> Arm64OpCategory.W
    "d" -> Arm64OpCategory.D
    "s" -> Arm64OpCategory.S
    "imm12" -> Arm64OpCategory.IMM12
    "imm16" -> Arm64OpCategory.IMM16
    "imm" -> Arm64OpCategory.IMM
    "cond" -> Arm64OpCategory.COND
    "label" -> Arm64OpCategory.LABEL
    else -> Arm64OpCategory.NONE
}

fun arm64OperandToKotlinType(cat: Arm64OpCategory): String = when (cat) {
    Arm64OpCategory.X -> "Arm64Register64"
    Arm64OpCategory.W -> "Arm64Register32"
    Arm64OpCategory.D -> "Arm64VecD"
    Arm64OpCategory.S -> "Arm64VecS"
    Arm64OpCategory.IMM12, Arm64OpCategory.IMM16, Arm64OpCategory.IMM -> "Int"
    Arm64OpCategory.COND -> "Arm64Condition"
    Arm64OpCategory.LABEL -> "String"
    Arm64OpCategory.NONE -> "Int"
}

fun arm64OperandToTypeName(op: String): String = when (op) {
    "x" -> "X"; "w" -> "W"; "d" -> "D"; "s" -> "S"
    "imm12" -> "IMM12"; "imm16" -> "IMM16"; "imm" -> "IMM"
    "cond" -> "COND"; "label" -> "LABEL"
    else -> "IMM"
}

data class Arm64MethodParam(val name: String, val type: String, val category: Arm64OpCategory)

fun arm64ParamName(position: Int, cat: Arm64OpCategory): String = when (cat) {
    Arm64OpCategory.X, Arm64OpCategory.W -> when (position) {
        0 -> "rd"; 1 -> "rn"; 2 -> "rm"; 3 -> "ra"; else -> "r${position + 1}"
    }
    Arm64OpCategory.D, Arm64OpCategory.S -> when (position) {
        0 -> "fd"; 1 -> "fn"; 2 -> "fm"; 3 -> "fa"; else -> "f${position + 1}"
    }
    Arm64OpCategory.IMM12, Arm64OpCategory.IMM16, Arm64OpCategory.IMM -> "imm"
    Arm64OpCategory.COND -> "cond"
    Arm64OpCategory.LABEL -> "label"
    Arm64OpCategory.NONE -> "arg${position + 1}"
}

fun arm64FormToParams(form: Arm64Form): List<Arm64MethodParam> {
    val params = mutableListOf<Arm64MethodParam>()
    val namesSeen = mutableSetOf<String>()
    for ((i, op) in form.operands.withIndex()) {
        val cat = classifyArm64Operand(op)
        val type = arm64OperandToKotlinType(cat)
        var name = arm64ParamName(i, cat)
        // Disambiguate duplicate names
        if (name in namesSeen) {
            var suffix = 2
            while ("$name$suffix" in namesSeen) suffix++
            name = "$name$suffix"
        }
        namesSeen.add(name)
        params.add(Arm64MethodParam(name, type, cat))
    }
    return params
}

fun sanitizeArm64Mnemonic(mnemonic: String): String = when (mnemonic) {
    "and" -> "and_"
    "or" -> "or_"
    "in" -> "in_"
    "break" -> "break_"
    "return" -> "return_"
    else -> mnemonic
}

// ── Main ────────────────────────────────────────────────────────────

fun arm64Main() {
    val resourceDir = "generator/src/main/resources/arm64"
    val outputDir = "kgen/src/main/kotlin/org/kgen/target/arm64"

    val instructions = loadArm64Instructions(File(resourceDir))
    val totalForms = instructions.sumOf { it.forms.size }
    println("Loaded ${instructions.size} mnemonics, $totalForms forms")

    val outDir = File(outputDir)
    File(outDir, "generated").mkdirs()

    generateArm64OperandType(File(outDir, "generated/Arm64OperandType.kt"))
    generateArm64Feature(instructions, File(outDir, "generated/Arm64Feature.kt"))
    generateArm64InstructionData(instructions, File(outDir, "generated/Arm64InstructionData.kt"))
    generateArm64AssemblerOps(instructions, File(outDir, "generated/Arm64AssemblerOps.kt"))

    val features = instructions.flatMap { i -> i.forms.map { it.resolvedFeature } }.distinct().sorted()
    println("Generated files in $outDir/generated/")
    println("  Features: ${features.joinToString()}")
    println("  Arm64InstructionData.kt: $totalForms forms")
    println("  Arm64AssemblerOps.kt: ${instructions.size} instruction groups")
}

fun loadArm64Instructions(dir: File): List<Arm64Instruction> {
    val gson = Gson()
    val manifest = JsonParser.parseReader(File(dir, "instructions.json").reader()).asJsonObject
    val files = manifest.getAsJsonArray("files")
    val type = object : TypeToken<List<Arm64Instruction>>() {}.type

    return files.flatMap { fileElem ->
        val file = File(dir, fileElem.asString)
        if (!file.exists()) {
            System.err.println("Warning: ${file.name} not found, skipping")
            emptyList()
        } else {
            gson.fromJson<List<Arm64Instruction>>(file.reader(), type)
        }
    }
}

// ── Generate Arm64OperandType ───────────────────────────────────────

fun generateArm64OperandType(out: File) {
    val sb = StringBuilder()
    sb.appendLine("// Generated — do not edit")
    sb.appendLine("package org.kgen.target.arm64")
    sb.appendLine()
    sb.appendLine("/** Operand type classification for ARM64 instruction forms. */")
    sb.appendLine("enum class Arm64OperandType {")
    val types = listOf("X", "W", "D", "S", "IMM12", "IMM16", "IMM", "COND", "LABEL")
    for ((i, t) in types.withIndex()) {
        val comma = if (i < types.size - 1) "," else ";"
        sb.appendLine("    $t$comma")
    }
    sb.appendLine("}")
    out.writeText(sb.toString())
}

// ── Generate Arm64Feature ───────────────────────────────────────────

fun generateArm64Feature(instructions: List<Arm64Instruction>, out: File) {
    val features = instructions.flatMap { i -> i.forms.map { it.resolvedFeature } }.distinct().sorted()
    val sb = StringBuilder()
    sb.appendLine("// Generated — do not edit")
    sb.appendLine("package org.kgen.target.arm64")
    sb.appendLine()
    sb.appendLine("/** ARM64 feature flags indicating required instruction set extensions. */")
    sb.appendLine("enum class Arm64Feature(val specName: String) {")
    for ((i, f) in features.withIndex()) {
        val enumName = f.uppercase().replace('-', '_').replace('.', '_')
        val comma = if (i < features.size - 1) "," else ";"
        sb.appendLine("    $enumName(\"$f\")$comma")
    }
    sb.appendLine("}")
    out.writeText(sb.toString())
}

// ── Generate Arm64InstructionData ───────────────────────────────────

fun generateArm64InstructionData(instructions: List<Arm64Instruction>, out: File) {
    val sb = StringBuilder()
    sb.appendLine("// Generated — do not edit")
    sb.appendLine("package org.kgen.target.arm64")
    sb.appendLine()
    sb.appendLine("/**")
    sb.appendLine(" * Encoding metadata for a single ARM64 instruction form.")
    sb.appendLine(" *")
    sb.appendLine(" * @property base The 32-bit base opcode with all operand fields zeroed.")
    sb.appendLine(" * @property format The encoding format that determines operand bit positions.")
    sb.appendLine(" */")
    sb.appendLine("data class Arm64EncodingInfo(")
    sb.appendLine("    val base: Long,")
    sb.appendLine("    val format: Arm64Format,")
    sb.appendLine(")")
    sb.appendLine()

    // Collect all formats from the instruction data
    val allFormats = instructions.flatMap { i -> i.forms.map { it.encoding.format } }.distinct().sorted()
    sb.appendLine("/** Encoding format — determines how operands are packed into the 32-bit instruction word. */")
    sb.appendLine("enum class Arm64Format {")
    for ((i, f) in allFormats.withIndex()) {
        val enumName = f.uppercase()
        val comma = if (i < allFormats.size - 1) "," else ";"
        sb.appendLine("    $enumName$comma")
    }
    sb.appendLine("}")
    sb.appendLine()

    sb.appendLine("data class Arm64InstructionForm(")
    sb.appendLine("    val mnemonic: String,")
    sb.appendLine("    val operands: List<Arm64OperandType>,")
    sb.appendLine("    val encoding: Arm64EncodingInfo,")
    sb.appendLine("    val feature: Arm64Feature,")
    sb.appendLine(")")
    sb.appendLine()

    sb.appendLine("object Arm64InstructionData {")
    sb.appendLine("    val forms: List<Arm64InstructionForm> by lazy { buildForms() }")
    sb.appendLine()
    sb.appendLine("    val byMnemonic: Map<String, List<Arm64InstructionForm>> by lazy {")
    sb.appendLine("        forms.groupBy { it.mnemonic }")
    sb.appendLine("    }")
    sb.appendLine()

    val allForms = instructions.flatMap { insn -> insn.forms.map { insn to it } }
    val chunkSize = 100
    val chunks = allForms.chunked(chunkSize)

    sb.appendLine("    private fun buildForms(): List<Arm64InstructionForm> {")
    sb.appendLine("        val list = ArrayList<Arm64InstructionForm>(${allForms.size})")
    for (i in chunks.indices) {
        sb.appendLine("        buildForms$i(list)")
    }
    sb.appendLine("        return list")
    sb.appendLine("    }")

    for ((chunkIdx, chunk) in chunks.withIndex()) {
        sb.appendLine()
        sb.appendLine("    private fun buildForms$chunkIdx(list: MutableList<Arm64InstructionForm>) {")
        for ((insn, form) in chunk) {
            val operandList = form.operands.joinToString(", ") { "Arm64OperandType.${arm64OperandToTypeName(it)}" }
            val featureEnum = form.resolvedFeature.uppercase().replace('-', '_').replace('.', '_')
            val formatEnum = form.encoding.format.uppercase()
            val baseHex = "0x${form.encoding.base}L"
            sb.appendLine("        list.add(Arm64InstructionForm(\"${insn.mnemonic}\", listOf($operandList), Arm64EncodingInfo($baseHex, Arm64Format.$formatEnum), Arm64Feature.$featureEnum))")
        }
        sb.appendLine("    }")
    }
    sb.appendLine("}")
    out.writeText(sb.toString())
}

// ── Generate Arm64AssemblerOps ──────────────────────────────────────

fun generateArm64AssemblerOps(instructions: List<Arm64Instruction>, out: File) {
    val sb = StringBuilder()
    sb.appendLine("// Generated — do not edit")
    sb.appendLine("package org.kgen.target.arm64")
    sb.appendLine()
    sb.appendLine("/**")
    sb.appendLine(" * Generated assembler dispatch methods for ARM64 (AArch64).")
    sb.appendLine(" *")
    sb.appendLine(" * Each method corresponds to an ARM64 instruction mnemonic.")
    sb.appendLine(" * Overloads select the correct encoding based on operand types.")
    sb.appendLine(" * The abstract [encodeArm64] method is implemented by the hand-written assembler.")
    sb.appendLine(" */")
    sb.appendLine("abstract class Arm64AssemblerOps {")
    sb.appendLine()
    sb.appendLine("    /** Encode and emit a single ARM64 instruction. Implemented by the assembler. */")
    sb.appendLine("    protected abstract fun encodeArm64(info: Arm64EncodingInfo, vararg operands: Any)")
    sb.appendLine()

    for (insn in instructions) {
        val methodName = sanitizeArm64Mnemonic(insn.mnemonic)

        // Deduplicate by Kotlin type signature
        data class Sig(val params: List<String>)
        val seen = mutableSetOf<Sig>()

        for (form in insn.forms) {
            val params = arm64FormToParams(form)
            val sig = Sig(params.map { it.type })

            if (!seen.add(sig)) continue

            val paramStr = if (params.isEmpty()) "" else params.joinToString(", ") { "${it.name}: ${it.type}" }
            val argList = if (params.isEmpty()) "" else ", " + params.joinToString(", ") { it.name }

            val formatEnum = form.encoding.format.uppercase()
            val baseHex = "0x${form.encoding.base}L"

            sb.appendLine("    /** ${insn.summary}: ${form.operands.joinToString(", ")} */")
            sb.appendLine("    fun $methodName($paramStr) {")
            sb.appendLine("        encodeArm64(Arm64EncodingInfo($baseHex, Arm64Format.$formatEnum)$argList)")
            sb.appendLine("    }")
            sb.appendLine()
        }
    }

    sb.appendLine("}")
    out.writeText(sb.toString())
}
