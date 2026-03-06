package org.kgen.generator

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import java.io.File

data class X86Instruction(
    val mnemonic: String,
    val summary: String,
    val forms: List<X86Form>,
)

data class X86Form(
    val operands: List<String>,
    val encoding: X86Encoding,
    val flags: X86Flags? = null,
    val feature: String = "baseline",
)

data class X86Encoding(
    val opcode: List<Int>,
    val modrm: String? = null,
    val prefix: String? = null,
    val rex_w: Boolean? = null,
    val vex: String? = null,
    val evex: String? = null,
    val vex_map: String? = null,
    val vex_w: Int? = null,
    val plus_reg: Boolean? = null,
    val default_size: Int? = null,
)

data class X86Flags(
    val set: String? = null,
    val tested: String? = null,
    val undefined: String? = null,
)

fun main() {
    val resourceDir = "generator/src/main/resources/x86"
    val outputDir = "backend-x86_64/src/main/kotlin/com/kgen/x86"

    val instructions = loadX86Instructions(File(resourceDir))
    val totalForms = instructions.sumOf { it.forms.size }
    println("Loaded ${instructions.size} mnemonics, $totalForms forms")

    val outDir = File(outputDir)
    outDir.mkdirs()
    File(outDir, "generated").mkdirs()

    generateX86OperandType(File(outDir, "generated/X86OperandType.kt"))
    generateX86Feature(instructions, File(outDir, "generated/X86Feature.kt"))
    generateX86InstructionData(instructions, File(outDir, "generated/X86InstructionData.kt"))
    generateX86AssemblerOps(instructions, File(outDir, "generated/X86AssemblerOps.kt"))

    val features = instructions.flatMap { i -> i.forms.map { it.feature } }.distinct().sorted()
    println("Generated files in $outDir")
    println("  Features: ${features.joinToString()}")
    println("  X86InstructionData.kt: $totalForms forms")
    println("  X86AssemblerOps.kt: ${instructions.size} instruction groups")
}

fun loadX86Instructions(dir: File): List<X86Instruction> {
    val gson = Gson()
    val manifest = JsonParser.parseReader(File(dir, "instructions.json").reader()).asJsonObject
    val files = manifest.getAsJsonArray("files")
    val type = object : TypeToken<List<X86Instruction>>() {}.type

    return files.flatMap { fileElem ->
        val file = File(dir, fileElem.asString)
        if (!file.exists()) {
            System.err.println("Warning: ${file.name} not found, skipping")
            emptyList()
        } else {
            gson.fromJson<List<X86Instruction>>(file.reader(), type)
        }
    }
}

// Operand type categories for method signatures
enum class OpCategory {
    REG8, REG16, REG32, REG64,
    RM8, RM16, RM32, RM64,
    XMM, YMM, ZMM,
    XMM_M32, XMM_M64, XMM_M128,
    YMM_M256, ZMM_M512,
    MM, MM_M64,
    MEM8, MEM16, MEM32, MEM64, MEM128, MEM256, MEM512,
    IMM8, IMM16, IMM32, IMM64,
    REL8, REL32,
    AL, AX, EAX, RAX, CL,
    SREG, CR, DR,
    ST0, STI,
    K, K_M16,
    MOFFS8, MOFFS16, MOFFS32, MOFFS64,
    ONE, // literal 1
    NONE, // unknown
}

fun classifyOperand(op: String): OpCategory = when (op) {
    "r8" -> OpCategory.REG8
    "r16" -> OpCategory.REG16
    "r32" -> OpCategory.REG32
    "r64" -> OpCategory.REG64
    "r/m8" -> OpCategory.RM8
    "r/m16" -> OpCategory.RM16
    "r/m32" -> OpCategory.RM32
    "r/m64" -> OpCategory.RM64
    "xmm" -> OpCategory.XMM
    "ymm" -> OpCategory.YMM
    "zmm" -> OpCategory.ZMM
    "xmm/m32" -> OpCategory.XMM_M32
    "xmm/m64" -> OpCategory.XMM_M64
    "xmm/m128" -> OpCategory.XMM_M128
    "xmm/m16" -> OpCategory.XMM_M64 // treat xmm/m16 like xmm/m64 for typing
    "ymm/m256" -> OpCategory.YMM_M256
    "zmm/m512" -> OpCategory.ZMM_M512
    "mm" -> OpCategory.MM
    "mm/m64" -> OpCategory.MM_M64
    "m8" -> OpCategory.MEM8
    "m16" -> OpCategory.MEM16
    "m32" -> OpCategory.MEM32
    "m64" -> OpCategory.MEM64
    "m128" -> OpCategory.MEM128
    "m256" -> OpCategory.MEM256
    "m512" -> OpCategory.MEM512
    "imm8" -> OpCategory.IMM8
    "imm16" -> OpCategory.IMM16
    "imm32" -> OpCategory.IMM32
    "imm64" -> OpCategory.IMM64
    "rel8" -> OpCategory.REL8
    "rel16" -> OpCategory.REL8 // treat as rel8 for method typing
    "rel32" -> OpCategory.REL32
    "al" -> OpCategory.AL
    "ax" -> OpCategory.AX
    "eax" -> OpCategory.EAX
    "rax" -> OpCategory.RAX
    "cl" -> OpCategory.CL
    "sreg" -> OpCategory.SREG
    "cr" -> OpCategory.CR
    "dr" -> OpCategory.DR
    "st0" -> OpCategory.ST0
    "sti" -> OpCategory.STI
    "k" -> OpCategory.K
    "k/m16" -> OpCategory.K_M16
    "moffs8" -> OpCategory.MOFFS8
    "moffs16" -> OpCategory.MOFFS16
    "moffs32" -> OpCategory.MOFFS32
    "moffs64" -> OpCategory.MOFFS64
    "1" -> OpCategory.ONE
    else -> OpCategory.NONE
}

fun operandToKotlinType(cat: OpCategory): String = when (cat) {
    OpCategory.REG8, OpCategory.AL, OpCategory.CL -> "X86Register8"
    OpCategory.REG16, OpCategory.AX -> "X86Register16"
    OpCategory.REG32, OpCategory.EAX -> "X86Register32"
    OpCategory.REG64, OpCategory.RAX -> "X86Register64"
    OpCategory.RM8 -> "X86Operand8"
    OpCategory.RM16 -> "X86Operand16"
    OpCategory.RM32 -> "X86Operand32"
    OpCategory.RM64 -> "X86Operand64"
    OpCategory.XMM, OpCategory.XMM_M32, OpCategory.XMM_M64, OpCategory.XMM_M128 -> "X86Xmm"
    OpCategory.YMM, OpCategory.YMM_M256 -> "X86Ymm"
    OpCategory.ZMM, OpCategory.ZMM_M512 -> "X86Zmm"
    OpCategory.MM, OpCategory.MM_M64 -> "X86Mm"
    OpCategory.MEM8 -> "X86Memory"
    OpCategory.MEM16 -> "X86Memory"
    OpCategory.MEM32 -> "X86Memory"
    OpCategory.MEM64 -> "X86Memory"
    OpCategory.MEM128 -> "X86Memory"
    OpCategory.MEM256 -> "X86Memory"
    OpCategory.MEM512 -> "X86Memory"
    OpCategory.IMM8 -> "Byte"
    OpCategory.IMM16 -> "Short"
    OpCategory.IMM32 -> "Int"
    OpCategory.IMM64 -> "Long"
    OpCategory.REL8 -> "Byte"
    OpCategory.REL32 -> "Int"
    OpCategory.SREG -> "X86SegReg"
    OpCategory.CR -> "X86ControlReg"
    OpCategory.DR -> "X86DebugReg"
    OpCategory.ST0, OpCategory.STI -> "X86FpuReg"
    OpCategory.K, OpCategory.K_M16 -> "X86MaskReg"
    OpCategory.MOFFS8, OpCategory.MOFFS16, OpCategory.MOFFS32, OpCategory.MOFFS64 -> "Long"
    OpCategory.ONE -> "" // literal, no parameter
    OpCategory.NONE -> "Int"
}

fun operandToParamName(cat: OpCategory, index: Int): String = when (cat) {
    OpCategory.REG8, OpCategory.REG16, OpCategory.REG32, OpCategory.REG64,
    OpCategory.AL, OpCategory.AX, OpCategory.EAX, OpCategory.RAX, OpCategory.CL -> "r${index + 1}"
    OpCategory.RM8, OpCategory.RM16, OpCategory.RM32, OpCategory.RM64 -> "rm${index + 1}"
    OpCategory.XMM, OpCategory.XMM_M32, OpCategory.XMM_M64, OpCategory.XMM_M128 -> "xmm${index + 1}"
    OpCategory.YMM, OpCategory.YMM_M256 -> "ymm${index + 1}"
    OpCategory.ZMM, OpCategory.ZMM_M512 -> "zmm${index + 1}"
    OpCategory.MM, OpCategory.MM_M64 -> "mm${index + 1}"
    OpCategory.MEM8, OpCategory.MEM16, OpCategory.MEM32, OpCategory.MEM64,
    OpCategory.MEM128, OpCategory.MEM256, OpCategory.MEM512 -> "mem"
    OpCategory.IMM8, OpCategory.IMM16, OpCategory.IMM32, OpCategory.IMM64 -> "imm"
    OpCategory.REL8, OpCategory.REL32 -> "rel"
    OpCategory.SREG -> "sreg"
    OpCategory.CR -> "cr"
    OpCategory.DR -> "dr"
    OpCategory.ST0, OpCategory.STI -> "st"
    OpCategory.K, OpCategory.K_M16 -> "k${index + 1}"
    OpCategory.MOFFS8, OpCategory.MOFFS16, OpCategory.MOFFS32, OpCategory.MOFFS64 -> "offset"
    OpCategory.ONE -> ""
    OpCategory.NONE -> "arg${index + 1}"
}

fun formToMethodSuffix(form: X86Form): String {
    // For forms with operands that can't be distinguished by Kotlin overloading,
    // we append a suffix. Most overloads work naturally by type.
    return ""
}

data class MethodParam(val name: String, val type: String, val category: OpCategory)

fun formToParams(form: X86Form): List<MethodParam> {
    val params = mutableListOf<MethodParam>()
    val nameCounts = mutableMapOf<String, Int>()
    for ((i, op) in form.operands.withIndex()) {
        val cat = classifyOperand(op)
        if (cat == OpCategory.ONE) continue // literal 1, not a parameter
        val type = operandToKotlinType(cat)
        var name = operandToParamName(cat, i)
        // Disambiguate duplicate parameter names
        val count = nameCounts.getOrDefault(name, 0) + 1
        nameCounts[name] = count
        if (count > 1) name = "${name}$count"
        params.add(MethodParam(name, type, cat))
    }
    // If first occurrence also needs a suffix, go back and fix it
    for ((name, count) in nameCounts) {
        if (count > 1) {
            val idx = params.indexOfFirst { it.name == name }
            if (idx >= 0) params[idx] = params[idx].copy(name = "${name}1")
        }
    }
    return params
}

fun sanitizeMnemonic(mnemonic: String): String {
    // Kotlin reserved words and naming
    return when (mnemonic) {
        "and" -> "and_"
        "or" -> "or_"
        "xor" -> "xor_"
        "not" -> "not_"
        "int" -> "int_"
        "in" -> "in_"
        "out" -> "out_"
        "break" -> "break_"
        "return" -> "return_"
        else -> mnemonic.replace('.', '_')
    }
}

fun encodingToLiteral(enc: X86Encoding): String {
    val parts = mutableListOf<String>()
    parts.add("opcode = intArrayOf(${enc.opcode.joinToString()})")
    if (enc.modrm != null) {
        if (enc.modrm == "reg") {
            parts.add("modrmMode = ModrmMode.REG")
        } else {
            val digit = enc.modrm.removePrefix("/").toInt()
            parts.add("modrmMode = ModrmMode.EXT")
            parts.add("opcodeExt = $digit")
        }
    }
    if (enc.prefix != null) parts.add("mandatoryPrefix = ${enc.prefix}")
    if (enc.rex_w == true) parts.add("rexW = true")
    if (enc.plus_reg == true) parts.add("plusReg = true")
    if (enc.vex != null) parts.add("vexL = ${if (enc.vex == "256") 1 else 0}")
    if (enc.evex != null) {
        val l = when (enc.evex) { "512" -> 2; "256" -> 1; else -> 0 }
        parts.add("evexL = $l")
    }
    if (enc.vex_map != null) {
        val mapVal = when (enc.vex_map) {
            "0F" -> "VexMap.MAP_0F"
            "0F38" -> "VexMap.MAP_0F38"
            "0F3A" -> "VexMap.MAP_0F3A"
            else -> "VexMap.MAP_0F"
        }
        parts.add("vexMap = $mapVal")
    }
    if (enc.vex_w != null) parts.add("vexW = ${enc.vex_w}")
    if (enc.default_size != null) parts.add("defaultSize = ${enc.default_size}")
    return parts.joinToString(", ")
}

fun flagsToLiteral(flags: X86Flags?): String {
    if (flags == null) return "null"
    val parts = mutableListOf<String>()
    if (flags.set != null) parts.add("set = \"${flags.set}\"")
    if (flags.tested != null) parts.add("tested = \"${flags.tested}\"")
    if (flags.undefined != null) parts.add("undefined = \"${flags.undefined}\"")
    return "FlagsEffect(${parts.joinToString(", ")})"
}

// Generate X86OperandType enum
fun generateX86OperandType(out: File) {
    val sb = StringBuilder()
    sb.appendLine("// Generated — do not edit")
    sb.appendLine("package org.kgen.x86")
    sb.appendLine()
    sb.appendLine("enum class X86OperandType {")
    val types = listOf(
        "R8", "R16", "R32", "R64",
        "RM8", "RM16", "RM32", "RM64",
        "XMM", "YMM", "ZMM",
        "XMM_M32", "XMM_M64", "XMM_M128",
        "YMM_M256", "ZMM_M512",
        "MM", "MM_M64",
        "M8", "M16", "M32", "M64", "M128", "M256", "M512",
        "IMM8", "IMM16", "IMM32", "IMM64",
        "REL8", "REL32",
        "AL", "AX", "EAX", "RAX", "CL",
        "SREG", "CR", "DR",
        "ST0", "STI",
        "K", "K_M16",
        "MOFFS8", "MOFFS16", "MOFFS32", "MOFFS64",
        "ONE",
    )
    for ((i, t) in types.withIndex()) {
        val comma = if (i < types.size - 1) "," else ""
        sb.appendLine("    $t$comma")
    }
    sb.appendLine("}")
    out.writeText(sb.toString())
}

fun operandToTypeName(op: String): String = when (op) {
    "r8" -> "R8"; "r16" -> "R16"; "r32" -> "R32"; "r64" -> "R64"
    "r/m8" -> "RM8"; "r/m16" -> "RM16"; "r/m32" -> "RM32"; "r/m64" -> "RM64"
    "xmm" -> "XMM"; "ymm" -> "YMM"; "zmm" -> "ZMM"
    "xmm/m32" -> "XMM_M32"; "xmm/m64" -> "XMM_M64"; "xmm/m128" -> "XMM_M128"
    "xmm/m16" -> "XMM_M64"
    "ymm/m256" -> "YMM_M256"; "zmm/m512" -> "ZMM_M512"
    "mm" -> "MM"; "mm/m64" -> "MM_M64"
    "m8" -> "M8"; "m16" -> "M16"; "m32" -> "M32"; "m64" -> "M64"
    "m128" -> "M128"; "m256" -> "M256"; "m512" -> "M512"
    "imm8" -> "IMM8"; "imm16" -> "IMM16"; "imm32" -> "IMM32"; "imm64" -> "IMM64"
    "rel8" -> "REL8"; "rel16" -> "REL8"; "rel32" -> "REL32"
    "al" -> "AL"; "ax" -> "AX"; "eax" -> "EAX"; "rax" -> "RAX"; "cl" -> "CL"
    "sreg" -> "SREG"; "cr" -> "CR"; "dr" -> "DR"
    "st0" -> "ST0"; "sti" -> "STI"
    "k" -> "K"; "k/m16" -> "K_M16"
    "moffs8" -> "MOFFS8"; "moffs16" -> "MOFFS16"; "moffs32" -> "MOFFS32"; "moffs64" -> "MOFFS64"
    "1" -> "ONE"
    else -> "IMM8"
}

// Generate X86Feature enum
fun generateX86Feature(instructions: List<X86Instruction>, out: File) {
    val features = instructions.flatMap { i -> i.forms.map { it.feature } }.distinct().sorted()
    val sb = StringBuilder()
    sb.appendLine("// Generated — do not edit")
    sb.appendLine("package org.kgen.x86")
    sb.appendLine()
    sb.appendLine("enum class X86Feature(val specName: String) {")
    for ((i, f) in features.withIndex()) {
        val enumName = f.uppercase().replace('-', '_').replace('.', '_')
        val comma = if (i < features.size - 1) "," else ";"
        sb.appendLine("    $enumName(\"$f\")$comma")
    }
    sb.appendLine("}")
    out.writeText(sb.toString())
}

// Generate X86InstructionData — full instruction form database
fun generateX86InstructionData(instructions: List<X86Instruction>, out: File) {
    val sb = StringBuilder()
    sb.appendLine("// Generated — do not edit")
    sb.appendLine("package org.kgen.x86")
    sb.appendLine()
    sb.appendLine("/** Encoding metadata for a single instruction form. */")
    sb.appendLine("data class X86EncodingInfo(")
    sb.appendLine("    val opcode: IntArray,")
    sb.appendLine("    val modrmMode: ModrmMode = ModrmMode.NONE,")
    sb.appendLine("    val opcodeExt: Int = 0,")
    sb.appendLine("    val mandatoryPrefix: Int = 0,")
    sb.appendLine("    val rexW: Boolean = false,")
    sb.appendLine("    val plusReg: Boolean = false,")
    sb.appendLine("    val vexL: Int = -1,")
    sb.appendLine("    val evexL: Int = -1,")
    sb.appendLine("    val vexMap: VexMap = VexMap.NONE,")
    sb.appendLine("    val vexW: Int = -1,")
    sb.appendLine("    val defaultSize: Int = 0,")
    sb.appendLine(")")
    sb.appendLine()
    sb.appendLine("enum class ModrmMode { NONE, REG, EXT }")
    sb.appendLine("enum class VexMap { NONE, MAP_0F, MAP_0F38, MAP_0F3A }")
    sb.appendLine()
    sb.appendLine("data class FlagsEffect(")
    sb.appendLine("    val set: String = \"\",")
    sb.appendLine("    val tested: String = \"\",")
    sb.appendLine("    val undefined: String = \"\",")
    sb.appendLine(")")
    sb.appendLine()
    sb.appendLine("data class X86InstructionForm(")
    sb.appendLine("    val mnemonic: String,")
    sb.appendLine("    val operands: List<X86OperandType>,")
    sb.appendLine("    val encoding: X86EncodingInfo,")
    sb.appendLine("    val flags: FlagsEffect?,")
    sb.appendLine("    val feature: X86Feature,")
    sb.appendLine(")")
    sb.appendLine()
    sb.appendLine("object X86InstructionData {")
    sb.appendLine("    val forms: List<X86InstructionForm> by lazy { buildForms() }")
    sb.appendLine()
    sb.appendLine("    val byMnemonic: Map<String, List<X86InstructionForm>> by lazy {")
    sb.appendLine("        forms.groupBy { it.mnemonic }")
    sb.appendLine("    }")
    sb.appendLine()
    // Split into chunks to stay under JVM 64KB method limit
    val allForms = instructions.flatMap { insn -> insn.forms.map { insn to it } }
    val chunkSize = 100
    val chunks = allForms.chunked(chunkSize)

    sb.appendLine("    private fun buildForms(): List<X86InstructionForm> {")
    sb.appendLine("        val list = ArrayList<X86InstructionForm>(${allForms.size})")
    for (i in chunks.indices) {
        sb.appendLine("        buildForms$i(list)")
    }
    sb.appendLine("        return list")
    sb.appendLine("    }")

    for ((chunkIdx, chunk) in chunks.withIndex()) {
        sb.appendLine()
        sb.appendLine("    private fun buildForms$chunkIdx(list: MutableList<X86InstructionForm>) {")
        for ((insn, form) in chunk) {
            val operandList = form.operands.joinToString(", ") { "X86OperandType.${operandToTypeName(it)}" }
            val featureEnum = form.feature.uppercase().replace('-', '_').replace('.', '_')
            sb.appendLine("        list.add(X86InstructionForm(\"${insn.mnemonic}\", listOf($operandList), X86EncodingInfo(${encodingToLiteral(form.encoding)}), ${flagsToLiteral(form.flags)}, X86Feature.$featureEnum))")
        }
        sb.appendLine("    }")
    }
    sb.appendLine("}")
    out.writeText(sb.toString())
}

// Generate X86AssemblerOps — abstract class with one method per mnemonic, overloaded by operand types
fun generateX86AssemblerOps(instructions: List<X86Instruction>, out: File) {
    val sb = StringBuilder()
    sb.appendLine("// Generated — do not edit")
    sb.appendLine("package org.kgen.x86")
    sb.appendLine()
    sb.appendLine("/**")
    sb.appendLine(" * Generated assembler dispatch methods for x86-64.")
    sb.appendLine(" *")
    sb.appendLine(" * Each method corresponds to an x86 instruction mnemonic.")
    sb.appendLine(" * Overloads select the correct encoding based on operand types.")
    sb.appendLine(" * The abstract `encode*` methods are implemented by the hand-written assembler.")
    sb.appendLine(" */")
    sb.appendLine("abstract class X86AssemblerOps {")
    sb.appendLine()
    sb.appendLine("    // Encoding primitives — implemented by X86Assembler")
    sb.appendLine("    protected abstract fun encodeLegacy(enc: X86EncodingInfo, vararg operands: Any)")
    sb.appendLine("    protected abstract fun encodeVex(enc: X86EncodingInfo, vararg operands: Any)")
    sb.appendLine("    protected abstract fun encodeEvex(enc: X86EncodingInfo, vararg operands: Any)")
    sb.appendLine()

    for (insn in instructions) {
        val methodName = sanitizeMnemonic(insn.mnemonic)

        // Group forms by their Kotlin parameter signature to detect duplicates
        data class Sig(val params: List<String>)
        val seen = mutableSetOf<Sig>()

        for (form in insn.forms) {
            val params = formToParams(form)
            val sig = Sig(params.map { it.type })

            // Skip duplicate signatures within the same mnemonic
            if (!seen.add(sig)) continue

            val paramStr = if (params.isEmpty()) {
                ""
            } else {
                params.joinToString(", ") { "${it.name}: ${it.type}" }
            }

            // Build the encoding info as an inline constructor call
            val encLiteral = encodingToLiteral(form.encoding)

            // Determine which encoder to use
            val encoder = when {
                form.encoding.evex != null -> "encodeEvex"
                form.encoding.vex != null || form.encoding.vex_map != null -> "encodeVex"
                else -> "encodeLegacy"
            }

            // Build the operand pass-through
            val argList = if (params.isEmpty()) {
                ""
            } else {
                ", " + params.joinToString(", ") { it.name }
            }

            sb.appendLine("    /** ${insn.summary}: ${form.operands.joinToString(", ")} */")
            sb.appendLine("    fun $methodName($paramStr) {")
            sb.appendLine("        $encoder(X86EncodingInfo($encLiteral)$argList)")
            sb.appendLine("    }")
            sb.appendLine()
        }
    }

    sb.appendLine("}")
    out.writeText(sb.toString())
}
