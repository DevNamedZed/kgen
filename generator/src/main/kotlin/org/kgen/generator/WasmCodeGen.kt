package org.kgen.generator

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import java.io.File

data class WasmOp(
    val opcode: String,
    val name: String,
    val mnemonic: String,
    val immediate: String?,
    val pop: List<String>,
    val push: List<String>,
    val group: String,
    val feature: String?,
    val prefix: String? = null,
)

fun main(args: Array<String>) {
    val resourceDir = args.getOrElse(0) {
        "generator/src/main/resources/wasm"
    }
    val outputDir = args.getOrElse(1) {
        "kgen/src/main/kotlin/org/kgen/backend/wasm"
    }

    val opcodes = loadOpcodes(File(resourceDir))
    println("Loaded ${opcodes.size} opcodes")

    val outDir = File(outputDir)
    outDir.mkdirs()
    File(outDir, "generated").mkdirs()

    generateWasmOpCode(opcodes, File(outDir, "generated/WasmOpCode.kt"))
    generateWasmAssembler(opcodes, File(outDir, "generated/WasmAssemblerOps.kt"))
    generateWasmDisassemblerTable(opcodes, File(outDir, "generated/WasmDisassemblerTable.kt"))

    val groups = opcodes.groupBy { it.group }
    val features = opcodes.mapNotNull { it.feature }.distinct().sorted()
    println("Generated files in $outDir")
    println("  Groups: ${groups.keys.sorted().joinToString()}")
    println("  Features: ${features.joinToString()}")
    println("  WasmOpCode.kt: ${opcodes.size} entries")
    println("  WasmAssemblerOps.kt: ${opcodes.size} methods")
}

fun loadOpcodes(dir: File): List<WasmOp> {
    val gson = Gson()
    val manifest = JsonParser.parseReader(File(dir, "opcodes.json").reader()).asJsonObject
    val includes = manifest.getAsJsonArray("includes")
    val type = object : TypeToken<List<WasmOp>>() {}.type

    return includes.flatMap { include ->
        val file = File(dir, include.asString)
        if (!file.exists()) {
            System.err.println("Warning: ${file.name} not found, skipping")
            emptyList()
        } else {
            gson.fromJson<List<WasmOp>>(file.reader(), type)
        }
    }
}

fun generateWasmOpCode(opcodes: List<WasmOp>, out: File) {
    val sb = StringBuilder()
    sb.appendLine("// Generated — do not edit")
    sb.appendLine("package org.kgen.backend.wasm")
    sb.appendLine()
    sb.appendLine("enum class WasmOpCode(")
    sb.appendLine("    val opcode: Int,")
    sb.appendLine("    val mnemonic: String,")
    sb.appendLine("    val immediate: WasmImmediate?,")
    sb.appendLine("    val group: WasmOpGroup,")
    sb.appendLine("    val feature: WasmFeatureFlag?,")
    sb.appendLine("    val prefix: Int?,")
    sb.appendLine(") {")

    for ((i, op) in opcodes.withIndex()) {
        val enumName = camelToScreamingSnake(op.name)
        val opcodeInt = parseHex(op.opcode)
        val immStr = if (op.immediate != null) "WasmImmediate.${op.immediate}" else "null"
        val featureStr = if (op.feature != null) "WasmFeatureFlag.${featureToEnum(op.feature)}" else "null"
        val prefixStr = if (op.prefix != null) "0x${op.prefix.removePrefix("0x")}" else "null"
        val groupStr = "WasmOpGroup.${op.group.uppercase()}"
        val comma = if (i < opcodes.size - 1) "," else ";"

        sb.appendLine("    $enumName($opcodeInt, \"${op.mnemonic}\", $immStr, $groupStr, $featureStr, $prefixStr)$comma")
    }

    sb.appendLine()
    sb.appendLine("    companion object {")
    sb.appendLine("        private val byOpcode: Map<Int, WasmOpCode> by lazy {")
    sb.appendLine("            val map = HashMap<Int, WasmOpCode>(entries.size)")
    sb.appendLine("            for (entry in entries) map[entry.opcode] = entry")
    sb.appendLine("            map")
    sb.appendLine("        }")
    sb.appendLine()
    sb.appendLine("        @JvmStatic fun fromOpcode(opcode: Int): WasmOpCode? = byOpcode[opcode]")
    sb.appendLine("    }")
    sb.appendLine("}")

    out.writeText(sb.toString())
}

fun generateWasmAssembler(opcodes: List<WasmOp>, out: File) {
    val sb = StringBuilder()
    sb.appendLine("// Generated — do not edit")
    sb.appendLine("package org.kgen.backend.wasm")
    sb.appendLine()
    sb.appendLine("/** Generated assembler methods for all WASM opcodes. */")
    sb.appendLine("abstract class WasmAssemblerOps {")
    sb.appendLine()
    sb.appendLine("    protected abstract fun emitByte(b: Int)")
    sb.appendLine("    protected abstract fun emitU32(value: Int)")
    sb.appendLine("    protected abstract fun emitS32(value: Int)")
    sb.appendLine("    protected abstract fun emitS64(value: Long)")
    sb.appendLine("    protected abstract fun emitF32(value: Float)")
    sb.appendLine("    protected abstract fun emitF64(value: Double)")
    sb.appendLine("    protected abstract fun emitBytes(bytes: ByteArray)")
    sb.appendLine()

    for (op in opcodes) {
        val params = immediateToParams(op.immediate)
        val paramStr = if (params.isEmpty()) "" else params.joinToString(", ") { "${it.first}: ${it.second}" }

        sb.appendLine("    fun ${op.name}($paramStr) {")
        val opcodeInt = parseHex(op.opcode)
        if (op.prefix != null) {
            val prefixByte = parseHex(op.prefix)
            val subOpcode = opcodeInt and 0xFFFF // strip prefix from full opcode
            sb.appendLine("        emitByte($prefixByte)")
            sb.appendLine("        emitU32($subOpcode)")
        } else {
            sb.appendLine("        emitByte($opcodeInt)")
        }

        for (param in params) {
            val emitCall = when (param.second) {
                "Int" -> if (param.first == "value") "emitS32(${param.first})" else "emitU32(${param.first})"
                "Long" -> "emitS64(${param.first})"
                "Float" -> "emitF32(${param.first})"
                "Double" -> "emitF64(${param.first})"
                "ByteArray" -> "emitBytes(${param.first})"
                "IntArray" -> {
                    // br_table: length-prefixed list + default
                    null
                }
                else -> "emitU32(${param.first})"
            }
            if (emitCall != null) {
                sb.appendLine("        $emitCall")
            } else if (param.second == "IntArray") {
                sb.appendLine("        emitU32(${param.first}.size - 1)")
                sb.appendLine("        for (label in ${param.first}) emitU32(label)")
            }
        }
        sb.appendLine("    }")
        sb.appendLine()
    }

    sb.appendLine("}")
    out.writeText(sb.toString())
}

fun generateWasmDisassemblerTable(opcodes: List<WasmOp>, out: File) {
    val sb = StringBuilder()
    sb.appendLine("// Generated — do not edit")
    sb.appendLine("package org.kgen.backend.wasm")
    sb.appendLine()
    sb.appendLine("/** Generated lookup table for WASM disassembly. */")
    sb.appendLine("object WasmDisassemblerTable {")
    sb.appendLine("    val opcodeMap: Map<Int, WasmOpCode> by lazy {")
    sb.appendLine("        buildMap {")
    for (op in opcodes.filter { it.prefix == null }) {
        val opcodeInt = parseHex(op.opcode)
        val enumName = camelToScreamingSnake(op.name)
        sb.appendLine("            put($opcodeInt, WasmOpCode.$enumName)")
    }
    sb.appendLine("        }")
    sb.appendLine("    }")
    sb.appendLine()

    val prefixes = opcodes.mapNotNull { it.prefix }.distinct().sorted()
    for (prefix in prefixes) {
        val prefixOps = opcodes.filter { it.prefix == prefix }
        val prefixInt = parseHex(prefix)
        sb.appendLine("    val prefix${prefix.removePrefix("0x")}Map: Map<Int, WasmOpCode> by lazy {")
        sb.appendLine("        buildMap {")
        for (op in prefixOps) {
            val opcodeInt = parseHex(op.opcode)
            val enumName = camelToScreamingSnake(op.name)
            sb.appendLine("            put($opcodeInt, WasmOpCode.$enumName)")
        }
        sb.appendLine("        }")
        sb.appendLine("    }")
        sb.appendLine()
    }

    sb.appendLine("    val prefixBytes: Set<Int> = setOf(${prefixes.joinToString(", ")})")
    sb.appendLine("}")
    out.writeText(sb.toString())
}

fun immediateToParams(immediate: String?): List<Pair<String, String>> = when (immediate) {
    null -> emptyList()
    "BlockType" -> listOf("blockType" to "Int")
    "LabelIdx" -> listOf("labelIdx" to "Int")
    "FuncIdx" -> listOf("funcIdx" to "Int")
    "LocalIdx" -> listOf("localIdx" to "Int")
    "GlobalIdx" -> listOf("globalIdx" to "Int")
    "TableIdx" -> listOf("tableIdx" to "Int")
    "MemIdx" -> listOf("memIdx" to "Int")
    "TagIdx" -> listOf("tagIdx" to "Int")
    "TypeIdx" -> listOf("typeIdx" to "Int")
    "DataIdx" -> listOf("dataIdx" to "Int")
    "ElemIdx" -> listOf("elemIdx" to "Int")
    "RefType" -> listOf("refType" to "Int")
    "HeapType" -> listOf("heapType" to "Int")
    "I32" -> listOf("value" to "Int")
    "I64" -> listOf("value" to "Long")
    "F32" -> listOf("value" to "Float")
    "F64" -> listOf("value" to "Double")
    "V128" -> listOf("bytes" to "ByteArray")
    "Byte" -> listOf("byteVal" to "Int")
    "MemArg" -> listOf("alignment" to "Int", "offset" to "Int")
    "MemArgLane" -> listOf("alignment" to "Int", "offset" to "Int", "laneIdx" to "Int")
    "LaneIdx" -> listOf("laneIdx" to "Int")
    "LaneIdx16" -> listOf("lanes" to "ByteArray")
    "ValTypes" -> listOf("typeCount" to "Int")
    "BrTable" -> listOf("labels" to "IntArray")
    "CallIndirect" -> listOf("typeIdx" to "Int", "tableIdx" to "Int")
    "DataMemIdx" -> listOf("dataIdx" to "Int", "memIdx" to "Int")
    "MemMemIdx" -> listOf("destMemIdx" to "Int", "srcMemIdx" to "Int")
    "ElemTableIdx" -> listOf("elemIdx" to "Int", "tableIdx" to "Int")
    "TableTableIdx" -> listOf("destTableIdx" to "Int", "srcTableIdx" to "Int")
    "FieldIdx" -> listOf("typeIdx" to "Int", "fieldIdx" to "Int")
    "TypeIdxLen" -> listOf("typeIdx" to "Int", "length" to "Int")
    "TypeDataIdx" -> listOf("typeIdx" to "Int", "dataIdx" to "Int")
    "TypeElemIdx" -> listOf("typeIdx" to "Int", "elemIdx" to "Int")
    "TypeTypeIdx" -> listOf("destTypeIdx" to "Int", "srcTypeIdx" to "Int")
    "BrCast" -> listOf("flags" to "Int", "labelIdx" to "Int", "srcType" to "Int", "destType" to "Int")
    else -> {
        System.err.println("Warning: unknown immediate type: $immediate")
        emptyList()
    }
}

fun camelToScreamingSnake(name: String): String {
    val sb = StringBuilder()
    for ((i, c) in name.withIndex()) {
        if (c.isUpperCase() && i > 0) {
            val prev = name[i - 1]
            if (prev.isLowerCase() || prev.isDigit()) {
                sb.append('_')
            } else if (i + 1 < name.length && name[i + 1].isLowerCase()) {
                sb.append('_')
            }
        }
        sb.append(c.uppercaseChar())
    }
    // Handle trailing _ from names like if_, else_, etc.
    return sb.toString().trimEnd('_')
}

fun featureToEnum(feature: String): String = feature.uppercase().replace('-', '_')

fun parseHex(hex: String): Int = Integer.decode(hex)
