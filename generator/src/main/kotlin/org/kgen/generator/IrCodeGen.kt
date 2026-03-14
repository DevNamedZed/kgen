package org.kgen.generator

import com.google.gson.Gson
import org.yaml.snakeyaml.Yaml
import java.io.File

data class IrSpec(
    val categories: List<IrCategory>,
)

data class IrCategory(
    val name: String,
    val interfaceName: String,
    val enumValue: String,
    val description: String,
    val imports: List<String>?,
    val interfaceProperties: List<IrInterfaceProperty>?,
    val sections: List<IrSection>,
) {
    val allInstructions: List<IrInstruction>
        get() = sections.flatMap { it.instructions }

    val safeImports: List<String> get() = imports ?: emptyList()
    val safeInterfaceProperties: List<IrInterfaceProperty> get() = interfaceProperties ?: emptyList()
}

data class IrSection(
    val comment: String,
    val instructions: List<IrInstruction>,
)

data class IrEmitParam(
    val name: String,
    val type: String,
    val description: String? = null,
    val default: String? = null,
)

data class IrEmit(
    val name: String? = null,
    val resultType: String? = null,
    val jvmOverloads: Boolean? = null,
    val extraParams: List<IrEmitParam>? = null,
)

data class IrInstruction(
    val name: String,
    val description: String,
    val effects: Any,
    val fields: List<IrField>,
    val emit: IrEmit? = null,
) {
    val resultField: IrField?
        get() = fields.find { it.role == "result" || it.role == "resultNullable" }

    val operandFields: List<IrField>
        get() = fields.filter { it.role in setOf("operand", "operandNullable", "operandList", "operandMapFirst") }

    val hasNullableResult: Boolean
        get() = resultField?.role == "resultNullable" || resultField?.type?.endsWith("?") == true

    val hasResult: Boolean
        get() = resultField != null

    val emitName: String
        get() = emit?.name ?: name.replaceFirstChar { it.lowercase() }

    val emitResultType: String?
        get() {
            if (emit?.resultType != null) {
                return emit.resultType
            }
            return inferResultType()
        }

    val emitParams: List<IrField>
        get() {
            val dataClassParams = fields.filter { it.role != "result" && it.role != "resultNullable" }
            val extraParams = emit?.extraParams?.map { extra ->
                IrField(name = extra.name, type = extra.type, role = "emitOnly", default = extra.default, description = extra.description)
            } ?: emptyList()
            return extraParams + dataClassParams
        }

    val needsJvmOverloads: Boolean
        get() = emit?.jvmOverloads ?: emitParams.any { it.default != null }

    private fun inferResultType(): String? {
        if (!hasResult) {
            return null
        }
        if (hasNullableResult) {
            val typeField = emitParams.find { it.name == "returnType" }
            if (typeField != null) {
                return "returnType"
            }
            return null
        }
        val firstOperand = emitParams.firstOrNull { it.role == "operand" }
        if (firstOperand != null) {
            val name = firstOperand.name
            if (name in setOf("lhs", "operand", "a", "value", "magnitude", "scalar", "vector", "condition")) {
                return "$name.type"
            }
        }
        val typeConfigFields = listOf(
            "toType", "loadType", "argType", "resultType", "elementType",
            "boxType", "unboxType", "castType", "pointeeType", "exceptionType",
        )
        for (configName in typeConfigFields) {
            val configField = emitParams.find { it.name == configName && it.role == "config" }
            if (configField != null) {
                return configName
            }
        }
        return null
    }
}

data class IrInterfaceProperty(
    val name: String,
    val type: String,
)

data class IrField(
    val name: String,
    val type: String,
    val role: String,
    val default: String? = null,
    val description: String? = null,
)

fun loadIrSpec(resourceDir: File): IrSpec {
    val categoriesDir = File(resourceDir, "categories")
    if (categoriesDir.isDirectory) {
        return loadIrSpecFromDirectory(categoriesDir)
    }
    val yamlFile = File(resourceDir, "instructions.yaml")
    val jsonFile = File(resourceDir, "instructions.json")
    val specFile = if (yamlFile.exists()) { yamlFile } else { jsonFile }
    if (!specFile.exists()) {
        error("No instruction spec found in ${resourceDir.absolutePath}")
    }
    return loadIrSpecFromFile(specFile)
}

private fun loadIrSpecFromDirectory(categoriesDir: File): IrSpec {
    val yamlFiles = categoriesDir.listFiles { file -> file.extension == "yaml" || file.extension == "yml" }
        ?.sortedBy { it.name }
        ?: error("No YAML files found in ${categoriesDir.absolutePath}")

    val categories = yamlFiles.map { file ->
        val yamlData = Yaml().load<Any>(file.reader())
        val json = Gson().toJson(yamlData)
        Gson().fromJson(json, IrCategory::class.java)
    }
    return IrSpec(categories)
}

private fun loadIrSpecFromFile(file: File): IrSpec {
    if (file.name.endsWith(".yaml") || file.name.endsWith(".yml")) {
        val yamlData = Yaml().load<Any>(file.reader())
        val json = Gson().toJson(yamlData)
        return Gson().fromJson(json, IrSpec::class.java)
    }
    return Gson().fromJson(file.reader(), IrSpec::class.java)
}

fun generateCategoryFile(category: IrCategory, outputDir: File) {
    val sb = StringBuilder()
    sb.appendLine("// Generated from instructions.yaml — do not edit")
    sb.appendLine("package org.kgen.ir.instructions")
    sb.appendLine()
    sb.appendLine("import org.kgen.ir.InstructionRef")
    sb.appendLine("import org.kgen.ir.IrCategory")
    sb.appendLine("import org.kgen.ir.Value")
    for (importLine in category.safeImports) {
        sb.appendLine("import $importLine")
    }
    sb.appendLine()

    generateMarkerInterface(sb, category)
    sb.appendLine()

    for (section in category.sections) {
        sb.appendLine("// --- ${section.comment} ---")
        sb.appendLine()
        for (instruction in section.instructions) {
            generateInstructionClass(sb, instruction, category)
            sb.appendLine()
        }
    }

    outputDir.mkdirs()
    File(outputDir, "${category.interfaceName}.kt").writeText(sb.toString())
}

private fun generateMarkerInterface(sb: StringBuilder, category: IrCategory) {
    val descLines = category.description.split("\n")
    sb.appendLine("/**")
    for (line in descLines) {
        if (line.isBlank()) {
            sb.appendLine(" *")
        } else {
            sb.appendLine(" * $line")
        }
    }
    sb.appendLine(" */")
    sb.appendLine("sealed interface ${category.interfaceName} : Instruction {")
    sb.appendLine("    override val category get() = IrCategory.${category.enumValue}")
    for (prop in category.safeInterfaceProperties) {
        sb.appendLine("    val ${prop.name}: ${prop.type}")
    }
    sb.appendLine("}")
}

private fun generateInstructionClass(sb: StringBuilder, instruction: IrInstruction, category: IrCategory) {
    generateKDoc(sb, instruction)

    if (instruction.fields.isEmpty()) {
        // No-arg data class needs a dummy parameter
        sb.appendLine("data class ${instruction.name}(")
        sb.appendLine("    val dummy: Unit = Unit,")
        sb.appendLine(") : ${category.interfaceName} {")
    } else {
        val interfacePropNames = category.safeInterfaceProperties.map { it.name }.toSet()
        sb.appendLine("data class ${instruction.name}(")
        for (field in instruction.fields) {
            val defaultSuffix = if (field.default != null) " = ${field.default}" else ""
            val modifier = if (field.name in interfacePropNames) { "override val" } else { "val" }
            sb.appendLine("    $modifier ${field.name}: ${field.type}$defaultSuffix,")
        }
        sb.appendLine(") : ${category.interfaceName} {")
    }

    generateResultProperty(sb, instruction)
    generateEffectsProperty(sb, instruction)
    generateOperandsProperty(sb, instruction)

    sb.appendLine("}")
}

private fun generateKDoc(sb: StringBuilder, instruction: IrInstruction) {
    val descLines = instruction.description.split("\n")
    sb.appendLine("/**")
    for (line in descLines) {
        if (line.isBlank()) {
            sb.appendLine(" *")
        } else {
            sb.appendLine(" * $line")
        }
    }

    if (instruction.fields.isNotEmpty()) {
        sb.appendLine(" *")
        for (field in instruction.fields) {
            sb.appendLine(" * @param ${field.name} ${fieldDescription(field)}")
        }
    }

    sb.appendLine(" */")
}

private fun fieldDescription(field: IrField): String {
    if (field.description != null) {
        return field.description
    }
    return when (field.role) {
        "result" -> "the SSA result reference"
        "resultNullable" -> "the SSA result reference, or null for void"
        "operand" -> "operand value"
        "operandNullable" -> "optional operand value"
        "operandList" -> "list of operand values"
        "operandMapFirst" -> "incoming values"
        "flag" -> if (field.default != null) "flag (default: ${field.default})" else "flag"
        "config" -> "configuration"
        else -> ""
    }
}

private fun generateResultProperty(sb: StringBuilder, instruction: IrInstruction) {
    val resultField = instruction.resultField
    if (resultField != null) {
        if (instruction.hasNullableResult) {
            sb.appendLine("    override val result get() = ${resultField.name}")
        } else {
            sb.appendLine("    override val result get() = ${resultField.name}")
        }
    } else {
        sb.appendLine("    override val result: Value? get() = null")
    }
}

private fun generateEffectsProperty(sb: StringBuilder, instruction: IrInstruction) {
    val effects = instruction.effects
    when (effects) {
        is String -> {
            sb.appendLine("    override val effects get() = InstructionEffects.$effects")
        }
        is Map<*, *> -> {
            @Suppress("UNCHECKED_CAST")
            val bits = effects["bits"] as? List<String> ?: emptyList()
            val bitsExpr = bits.joinToString(" or ") { bitName ->
                if (bitName.endsWith("_BITS")) {
                    val constantName = bitName.removeSuffix("_BITS")
                    "InstructionEffects.$constantName.bits"
                } else {
                    "InstructionEffects.$bitName"
                }
            }
            sb.appendLine("    override val effects get() = InstructionEffects($bitsExpr)")
        }
        else -> {
            sb.appendLine("    override val effects get() = InstructionEffects.PURE")
        }
    }
}

private fun generateOperandsProperty(sb: StringBuilder, instruction: IrInstruction) {
    val operandFields = instruction.operandFields
    if (operandFields.isEmpty()) {
        sb.appendLine("    override val operands get() = emptyList<Value>()")
        return
    }

    val hasNullable = operandFields.any { it.role == "operandNullable" }
    val hasComplex = operandFields.any { it.role in setOf("operandList", "operandMapFirst") }
    val simpleOps = operandFields.filter { it.role == "operand" }
    val nullableOps = operandFields.filter { it.role == "operandNullable" }
    val listOps = operandFields.filter { it.role == "operandList" }
    val mapFirstOps = operandFields.filter { it.role == "operandMapFirst" }

    if (!hasNullable && !hasComplex) {
        sb.appendLine("    override val operands get() = listOf(${simpleOps.joinToString(", ") { it.name }})")
        return
    }

    if (hasNullable && simpleOps.isEmpty() && listOps.isEmpty() && mapFirstOps.isEmpty()) {
        sb.appendLine("    override val operands get() = listOfNotNull(${nullableOps.joinToString(", ") { it.name }})")
        return
    }

    // Build expression: listOf/listOfNotNull for scalar fields, then + for list/map fields
    val scalarFields = simpleOps + nullableOps
    val listFunction = if (hasNullable) "listOfNotNull" else "listOf"
    val scalarPart = if (scalarFields.isNotEmpty()) {
        "$listFunction(${scalarFields.joinToString(", ") { it.name }})"
    } else {
        null
    }

    val spreadParts = mutableListOf<String>()
    for (field in listOps) {
        spreadParts.add(field.name)
    }
    for (field in mapFirstOps) {
        spreadParts.add("${field.name}.map { it.first }")
    }

    val allParts = listOfNotNull(scalarPart) + spreadParts
    sb.appendLine("    override val operands get() = ${allParts.joinToString(" + ")}")
}

fun generateVisitorInterface(category: IrCategory, outputDir: File) {
    val sb = StringBuilder()
    sb.appendLine("// Generated from instructions.yaml — do not edit")
    sb.appendLine("package org.kgen.ir.visit")
    sb.appendLine()
    sb.appendLine("import org.kgen.ir.instructions.*")
    sb.appendLine()
    sb.appendLine("/**")
    sb.appendLine(" * Visitor interface for ${category.name.lowercase()} instructions.")
    sb.appendLine(" *")
    sb.appendLine(" * Implement this interface to receive callbacks for ${category.name.lowercase()} instructions")
    sb.appendLine(" * during instruction traversal. All methods have default no-op implementations,")
    sb.appendLine(" * so only override the instructions you care about.")
    sb.appendLine(" */")
    sb.appendLine("interface ${category.interfaceName}Visitor : InstructionVisitor {")

    for (instruction in category.allInstructions) {
        sb.appendLine()
        sb.appendLine("    fun visit${instruction.name}(instruction: ${instruction.name}) {}")
    }

    sb.appendLine("}")

    outputDir.mkdirs()
    File(outputDir, "${category.interfaceName}Visitor.kt").writeText(sb.toString())
}

fun generateDispatch(categories: List<IrCategory>, outputDir: File) {
    val sb = StringBuilder()
    sb.appendLine("// Generated from instructions.yaml — do not edit")
    sb.appendLine("package org.kgen.ir.visit")
    sb.appendLine()
    sb.appendLine("import org.kgen.ir.instructions.*")
    sb.appendLine()
    sb.appendLine("/**")
    sb.appendLine(" * Central dispatch for the acyclic visitor pattern.")
    sb.appendLine(" *")
    sb.appendLine(" * This is the only place the full instruction dispatch `when` exists.")
    sb.appendLine(" * Adding a new instruction means adding one line here plus the visitor method.")
    sb.appendLine(" */")
    sb.appendLine("object Instructions {")
    sb.appendLine()
    sb.appendLine("    @JvmStatic")
    sb.appendLine("    fun accept(instruction: Instruction, visitor: InstructionVisitor) {")
    sb.appendLine("        when (instruction) {")

    for (category in categories) {
        val visitorType = "${category.interfaceName}Visitor"
        for (instruction in category.allInstructions) {
            sb.appendLine("            is ${instruction.name} -> (visitor as? $visitorType)?.visit${instruction.name}(instruction)")
        }
    }

    sb.appendLine("            else -> error(\"Unhandled instruction type: \${instruction::class.simpleName}\")")
    sb.appendLine("        }")
    sb.appendLine("    }")
    sb.appendLine("}")

    outputDir.mkdirs()
    File(outputDir, "Instructions.kt").writeText(sb.toString())
}

fun generateInstructionSetInterface(category: IrCategory, outputDir: File) {
    val setName = "${category.interfaceName}Set"
    val sb = StringBuilder()
    sb.appendLine("// Generated from instructions.yaml — do not edit")
    sb.appendLine("package org.kgen.ir.build.sets")
    sb.appendLine()
    sb.appendLine("import org.kgen.ir.*")
    sb.appendLine("import org.kgen.ir.instructions.*")
    for (importLine in category.safeImports) {
        sb.appendLine("import $importLine")
    }
    sb.appendLine()
    sb.appendLine("/**")
    sb.appendLine(" * Emission interface for ${category.name.lowercase()} instructions.")
    sb.appendLine(" *")
    sb.appendLine(" * Each method emits one instruction and returns the SSA result value")
    sb.appendLine(" * (or Unit for void instructions).")
    sb.appendLine(" */")
    sb.appendLine("interface $setName : InstructionSet {")

    for (instruction in category.allInstructions) {
        if (instruction.emitResultType == null && !instruction.hasResult) {
            continue
        }
        sb.appendLine()
        generateEmitMethodSignature(sb, instruction, abstract = true)
    }

    // Void instructions
    for (instruction in category.allInstructions) {
        if (instruction.emitResultType != null || instruction.hasResult) {
            continue
        }
        sb.appendLine()
        generateEmitMethodSignature(sb, instruction, abstract = true)
    }

    sb.appendLine("}")

    outputDir.mkdirs()
    File(outputDir, "$setName.kt").writeText(sb.toString())
}

fun generateInstructionSetImpl(category: IrCategory, outputDir: File) {
    val setName = "${category.interfaceName}Set"
    val implName = "${setName}Impl"
    val sb = StringBuilder()
    sb.appendLine("// Generated from instructions.yaml — do not edit")
    sb.appendLine("package org.kgen.ir.build.sets")
    sb.appendLine()
    sb.appendLine("import org.kgen.ir.*")
    sb.appendLine("import org.kgen.ir.build.InstructionSink")
    sb.appendLine("import org.kgen.ir.instructions.*")
    for (importLine in category.safeImports) {
        sb.appendLine("import $importLine")
    }
    sb.appendLine()
    sb.appendLine("/**")
    sb.appendLine(" * Default implementation of [$setName] backed by an [InstructionSink].")
    sb.appendLine(" */")
    sb.appendLine("internal class $implName(private val sink: InstructionSink) : $setName {")

    for (instruction in category.allInstructions) {
        sb.appendLine()
        generateImplMethod(sb, instruction)
    }

    sb.appendLine("}")

    outputDir.mkdirs()
    File(outputDir, "$implName.kt").writeText(sb.toString())
}

private fun generateImplMethod(sb: StringBuilder, instruction: IrInstruction) {
    val params = instruction.emitParams
    val paramList = params.joinToString(", ") { field ->
        "${field.name}: ${field.type}"
    }

    if (!instruction.hasResult && instruction.emitResultType == null) {
        val constructorArgs = if (instruction.fields.isEmpty()) {
            ""
        } else {
            instruction.fields.joinToString(", ") { it.name }
        }
        sb.appendLine("    override fun ${instruction.emitName}($paramList) {")
        sb.appendLine("        sink.emit(${instruction.name}($constructorArgs))")
        sb.appendLine("    }")
    } else if (instruction.hasNullableResult) {
        val resultTypeExpr = instruction.emitResultType ?: "returnType"
        val resultField = instruction.resultField!!
        val constructorArgs = instruction.fields.joinToString(", ") { field ->
            if (field.name == resultField.name) { "dest" } else { field.name }
        }
        sb.appendLine("    override fun ${instruction.emitName}($paramList): Value? {")
        sb.appendLine("        val dest = if ($resultTypeExpr != Type.Void) { sink.nextRef($resultTypeExpr) } else { null }")
        sb.appendLine("        sink.emit(${instruction.name}($constructorArgs))")
        sb.appendLine("        return dest")
        sb.appendLine("    }")
    } else {
        val resultTypeExpr = instruction.emitResultType ?: "Type.I32"
        val resultField = instruction.resultField!!
        val constructorArgs = instruction.fields.joinToString(", ") { field ->
            if (field.name == resultField.name) { "it" } else { field.name }
        }
        sb.appendLine("    override fun ${instruction.emitName}($paramList): Value =")
        sb.appendLine("        sink.nextRef($resultTypeExpr).also { sink.emit(${instruction.name}($constructorArgs)) }")
    }
}

fun generateEmitterMethods(categories: List<IrCategory>, outputDir: File) {
    val sb = StringBuilder()
    sb.appendLine("// Generated from instructions.yaml — do not edit")
    sb.appendLine("package org.kgen.ir.build")
    sb.appendLine()
    sb.appendLine("import org.kgen.ir.*")
    sb.appendLine("import org.kgen.ir.instructions.*")
    sb.appendLine()

    for (category in categories) {
        sb.appendLine("// --- ${category.name} ---")
        sb.appendLine()
        for (instruction in category.allInstructions) {
            if (instruction.emitResultType == null && !instruction.hasResult && !instruction.hasNullableResult) {
                generateVoidEmitMethod(sb, instruction)
            } else if (instruction.hasNullableResult) {
                generateNullableEmitMethod(sb, instruction)
            } else {
                generateValueEmitMethod(sb, instruction)
            }
            sb.appendLine()
        }
    }

    outputDir.mkdirs()
    File(outputDir, "EmitterMethods.kt").writeText(sb.toString())
}

private fun generateEmitMethodSignature(sb: StringBuilder, instruction: IrInstruction, abstract: Boolean) {
    val params = instruction.emitParams
    val returnType = if (instruction.hasResult && !instruction.hasNullableResult) {
        "Value"
    } else if (instruction.hasNullableResult) {
        "Value?"
    } else {
        "Unit"
    }

    val paramList = params.joinToString(", ") { field ->
        val defaultSuffix = if (field.default != null) { " = ${field.default}" } else { "" }
        "${field.name}: ${field.type}$defaultSuffix"
    }

    if (abstract) {
        generateMethodKDoc(sb, instruction, params, returnType)
        sb.appendLine("    fun ${instruction.emitName}($paramList): $returnType")
    }
}

private fun generateMethodKDoc(sb: StringBuilder, instruction: IrInstruction, params: List<IrField>, returnType: String) {
    sb.appendLine("    /**")
    sb.appendLine("     * ${instruction.description}")
    sb.appendLine("     *")
    sb.appendLine("     * Emits a [${instruction.name}] instruction into the current block.")
    if (params.isNotEmpty()) {
        sb.appendLine("     *")
        for (param in params) {
            val paramDoc = param.description ?: describeParam(param)
            sb.appendLine("     * @param ${param.name} $paramDoc")
        }
    }
    if (returnType == "Value") {
        sb.appendLine("     * @return the SSA value produced by this instruction")
    } else if (returnType == "Value?") {
        sb.appendLine("     * @return the SSA value produced by this instruction, or null if void")
    }
    sb.appendLine("     */")
}

private fun describeParam(field: IrField): String {
    return when (field.role) {
        "operand" -> "source operand"
        "operandNullable" -> "optional source operand"
        "operandList" -> "list of source operands"
        "operandMapFirst" -> "source operand map"
        "result" -> "destination for the result"
        "resultNullable" -> "optional destination for the result"
        else -> when {
            field.name == "lhs" -> "left-hand side operand"
            field.name == "rhs" -> "right-hand side operand"
            field.name == "type" || field.name.endsWith("Type") -> "the type"
            field.name == "name" -> "the name"
            field.name == "label" || field.name == "target" -> "target basic block label"
            field.name == "condition" -> "the branch condition"
            field.name == "callee" -> "the function to call"
            field.name == "args" -> "call arguments"
            field.name == "align" || field.name == "alignment" -> "memory alignment in bytes"
            field.name == "volatile" || field.name == "isVolatile" -> "whether this is a volatile memory access"
            field.name == "ordering" -> "the memory ordering for this atomic operation"
            else -> field.type.removeSuffix("?").removeSuffix(" = ${field.default ?: ""}")
        }
    }
}

private fun generateVoidEmitMethod(sb: StringBuilder, instruction: IrInstruction) {
    val params = instruction.emitParams
    val annotation = if (instruction.needsJvmOverloads) { "@JvmOverloads " } else { "" }
    val paramList = params.joinToString(", ") { field ->
        val defaultSuffix = if (field.default != null) { " = ${field.default}" } else { "" }
        "${field.name}: ${field.type}$defaultSuffix"
    }

    val constructorArgs = if (instruction.fields.isEmpty()) {
        ""
    } else {
        instruction.fields.joinToString(", ") { it.name }
    }

    sb.appendLine("    ${annotation}fun ${instruction.emitName}($paramList) {")
    sb.appendLine("        emit(${instruction.name}($constructorArgs))")
    sb.appendLine("    }")
}

private fun generateValueEmitMethod(sb: StringBuilder, instruction: IrInstruction) {
    val params = instruction.emitParams
    val annotation = if (instruction.needsJvmOverloads) { "@JvmOverloads " } else { "" }
    val paramList = params.joinToString(", ") { field ->
        val defaultSuffix = if (field.default != null) { " = ${field.default}" } else { "" }
        "${field.name}: ${field.type}$defaultSuffix"
    }

    val resultTypeExpr = instruction.emitResultType ?: "Type.I32"
    val resultField = instruction.resultField!!

    val constructorArgs = instruction.fields.joinToString(", ") { field ->
        if (field.name == resultField.name) { "it" } else { field.name }
    }

    sb.appendLine("    ${annotation}fun ${instruction.emitName}($paramList): Value =")
    sb.appendLine("        nextRef($resultTypeExpr).also { emit(${instruction.name}($constructorArgs)) }")
}

data class ScopeSpec(
    val scopes: List<ScopeDefinition>,
)

data class ScopeDefinition(
    val name: String,
    val description: String,
    val example: String,
    val sets: List<String>,
)

fun loadScopeSpec(resourceDir: File): ScopeSpec {
    val yamlFile = File(resourceDir, "scopes.yaml")
    if (!yamlFile.exists()) {
        error("Scope spec not found: ${yamlFile.absolutePath}")
    }
    val yamlData = Yaml().load<Any>(yamlFile.reader())
    val json = Gson().toJson(yamlData)
    return Gson().fromJson(json, ScopeSpec::class.java)
}

fun generateScopeFiles(outputDir: File, resourceDir: File) {
    outputDir.mkdirs()
    val scopeSpec = loadScopeSpec(resourceDir)
    generateInstructionBuilder(outputDir, scopeSpec)
    generateScopeInterfaces(outputDir, scopeSpec)
}

private fun generateInstructionBuilder(outputDir: File, scopeSpec: ScopeSpec) {
    val scopeLinks = scopeSpec.scopes.joinToString(", ") { "[${it.name}]" }
    val sb = StringBuilder()
    sb.appendLine("// Generated — do not edit")
    sb.appendLine("package org.kgen.ir.build.sets")
    sb.appendLine()
    sb.appendLine("import org.kgen.ir.build.InstructionSink")
    sb.appendLine("import java.lang.reflect.Proxy")
    sb.appendLine()
    sb.appendLine("/**")
    sb.appendLine(" * Factory for creating typed instruction builders from scope interfaces.")
    sb.appendLine(" *")
    sb.appendLine(" * A scope interface declares which instruction set categories are available by extending")
    sb.appendLine(" * them. The factory reflects on the scope's super-interfaces, creates a backing")
    sb.appendLine(" * implementation for each one via [InstructionSetRegistry], and returns a")
    sb.appendLine(" * [java.lang.reflect.Proxy] that implements the scope interface directly.")
    sb.appendLine(" *")
    sb.appendLine(" * Built-in scopes: $scopeLinks.")
    sb.appendLine(" * Users define their own for any combination:")
    sb.appendLine(" *")
    sb.appendLine(" * ```kotlin")
    sb.appendLine(" * interface MyScope : ArithmeticInstructionSet, MemoryInstructionSet, TerminatorInstructionSet")
    sb.appendLine(" *")
    sb.appendLine(" * val b = ir.createInstructionBuilder<MyScope>()")
    sb.appendLine(" * b.add(x, y)        // ArithmeticInstructionSet")
    sb.appendLine(" * b.load(ptr)         // MemoryInstructionSet")
    sb.appendLine(" * b.ret(result)       // TerminatorInstructionSet")
    sb.appendLine(" * b.newObject(\"Foo\")  // compile error — ObjectInstructionSet not in MyScope")
    sb.appendLine(" * ```")
    sb.appendLine(" *")
    sb.appendLine(" * Java usage:")
    sb.appendLine(" * ```java")
    sb.appendLine(" * NativeScope b = ir.createInstructionBuilder(NativeScope.class);")
    sb.appendLine(" * b.add(x, y);")
    sb.appendLine(" * ```")
    sb.appendLine(" */")
    sb.appendLine("object InstructionBuilder {")
    sb.appendLine()
    sb.appendLine("    /**")
    sb.appendLine("     * Creates a proxy implementing the given scope interface (Kotlin reified overload).")
    sb.appendLine("     *")
    sb.appendLine("     * @param T the scope interface (must extend one or more [InstructionSet] interfaces)")
    sb.appendLine("     * @param sink the shared [InstructionSink] for all emitted instructions")
    sb.appendLine("     * @return a proxy implementing [T]")
    sb.appendLine("     * @throws IllegalArgumentException if [T] does not extend any [InstructionSet] interfaces")
    sb.appendLine("     * @throws IllegalStateException if any constituent interface is not registered")
    sb.appendLine("     */")
    sb.appendLine("    inline fun <reified T : InstructionSet> create(sink: InstructionSink): T {")
    sb.appendLine("        return create(T::class.java, sink)")
    sb.appendLine("    }")
    sb.appendLine()
    sb.appendLine("    /**")
    sb.appendLine("     * Creates a proxy implementing the given scope interface.")
    sb.appendLine("     *")
    sb.appendLine("     * Reflects on the scope's super-interfaces to discover which [InstructionSet] types")
    sb.appendLine("     * it extends, creates an implementation for each via [InstructionSetRegistry],")
    sb.appendLine("     * and returns a [java.lang.reflect.Proxy] that implements the scope directly.")
    sb.appendLine("     *")
    sb.appendLine("     * @param T the scope interface type")
    sb.appendLine("     * @param scope the scope interface class")
    sb.appendLine("     * @param sink the shared [InstructionSink] for all emitted instructions")
    sb.appendLine("     * @return a proxy implementing the scope interface")
    sb.appendLine("     * @throws IllegalArgumentException if the scope does not extend any [InstructionSet] interfaces")
    sb.appendLine("     * @throws IllegalStateException if any constituent interface is not registered")
    sb.appendLine("     */")
    sb.appendLine("    @JvmStatic")
    sb.appendLine("    fun <T : InstructionSet> create(scope: Class<T>, sink: InstructionSink): T {")
    sb.appendLine("        val instructionSetInterfaces = scope.interfaces")
    sb.appendLine("            .filter { InstructionSet::class.java.isAssignableFrom(it) && it != InstructionSet::class.java }")
    sb.appendLine("            .toList()")
    sb.appendLine()
    sb.appendLine("        require(instructionSetInterfaces.isNotEmpty()) {")
    sb.appendLine("            \"\${scope.simpleName} does not extend any InstructionSet interfaces\"")
    sb.appendLine("        }")
    sb.appendLine()
    sb.appendLine("        val allInterfaces = (instructionSetInterfaces + scope).toTypedArray()")
    sb.appendLine()
    sb.appendLine("        val implementations = instructionSetInterfaces.associate { iface ->")
    sb.appendLine("            iface to InstructionSetRegistry.createForInterface(iface, sink)")
    sb.appendLine("        }")
    sb.appendLine()
    sb.appendLine("        @Suppress(\"UNCHECKED_CAST\")")
    sb.appendLine("        return Proxy.newProxyInstance(")
    sb.appendLine("            InstructionSet::class.java.classLoader,")
    sb.appendLine("            allInterfaces,")
    sb.appendLine("        ) { _, method, args ->")
    sb.appendLine("            val target = implementations[method.declaringClass]")
    sb.appendLine("                ?: implementations.values.first { method.declaringClass.isInstance(it) }")
    sb.appendLine("            if (args != null) { method.invoke(target, *args) } else { method.invoke(target) }")
    sb.appendLine("        } as T")
    sb.appendLine("    }")
    sb.appendLine("}")
    File(outputDir, "InstructionBuilder.kt").writeText(sb.toString())
}

private fun generateScopeInterfaces(outputDir: File, scopeSpec: ScopeSpec) {
    val sb = StringBuilder()
    sb.appendLine("// Generated from scopes.yaml — do not edit")
    sb.appendLine("package org.kgen.ir.build.sets")
    sb.appendLine()

    for ((index, scope) in scopeSpec.scopes.withIndex()) {
        if (index > 0) {
            sb.appendLine()
        }
        val setNames = scope.sets.map { "${it}InstructionSet" }

        sb.appendLine("/**")
        for (line in scope.description.lines()) {
            if (line.isBlank()) {
                sb.appendLine(" *")
            } else {
                sb.appendLine(" * $line")
            }
        }
        sb.appendLine(" *")
        sb.appendLine(" * ```kotlin")
        for (line in scope.example.lines()) {
            sb.appendLine(" * $line")
        }
        sb.appendLine(" * ```")
        sb.appendLine(" */")

        val firstSet = setNames.first()
        val remainingSets = setNames.drop(1)
        sb.append("interface ${scope.name} : $firstSet")
        if (remainingSets.isNotEmpty()) {
            sb.appendLine(",")
            sb.append("    ${remainingSets.joinToString(",\n    ")}")
        }
        sb.appendLine()
    }

    File(outputDir, "ScopeInterfaces.kt").writeText(sb.toString())
}

fun generateInstructionSetRegistry(categories: List<IrCategory>, outputDir: File) {
    val sb = StringBuilder()
    sb.appendLine("// Generated — do not edit")
    sb.appendLine("package org.kgen.ir.build.sets")
    sb.appendLine()
    sb.appendLine("import org.kgen.ir.build.InstructionSink")
    sb.appendLine("import kotlin.reflect.KClass")
    sb.appendLine()
    sb.appendLine("/**")
    sb.appendLine(" * Maps [InstructionSet] interfaces to their backing implementation factories.")
    sb.appendLine(" *")
    sb.appendLine(" * Built-in instruction sets (arithmetic, memory, terminators, etc.) are registered")
    sb.appendLine(" * at class-load time. Third-party extensions register via [register].")
    sb.appendLine(" *")
    sb.appendLine(" * The registry is used by [ProxyFactory] to resolve each interface to a concrete")
    sb.appendLine(" * implementation when building proxies.")
    sb.appendLine(" *")
    sb.appendLine(" * ```kotlin")
    sb.appendLine(" * // Register a third-party instruction set")
    sb.appendLine(" * InstructionSetRegistry.register(MyDSLInstructionSet::class) { sink ->")
    sb.appendLine(" *     MyDSLInstructionSetProvider(sink)")
    sb.appendLine(" * }")
    sb.appendLine(" * ```")
    sb.appendLine(" */")
    sb.appendLine("object InstructionSetRegistry {")
    sb.appendLine()
    sb.appendLine("    private val factories = mutableMapOf<Class<*>, (InstructionSink) -> InstructionSet>()")
    sb.appendLine()
    sb.appendLine("    init {")
    for (category in categories) {
        val setName = "${category.interfaceName}Set"
        val implName = "${setName}Impl"
        sb.appendLine("        factories[${setName}::class.java] = { sink -> $implName(sink) }")
    }
    sb.appendLine("    }")
    sb.appendLine()
    sb.appendLine("    /**")
    sb.appendLine("     * Registers a factory for a third-party [InstructionSet] interface.")
    sb.appendLine("     *")
    sb.appendLine("     * @param T the instruction set interface type")
    sb.appendLine("     * @param iface the KClass of the interface to register")
    sb.appendLine("     * @param factory a function that creates the implementation given an [InstructionSink]")
    sb.appendLine("     */")
    sb.appendLine("    fun <T : InstructionSet> register(iface: KClass<T>, factory: (InstructionSink) -> T) {")
    sb.appendLine("        factories[iface.java] = factory")
    sb.appendLine("    }")
    sb.appendLine()
    sb.appendLine("    /**")
    sb.appendLine("     * Registers a factory for a third-party [InstructionSet] interface (Java overload).")
    sb.appendLine("     *")
    sb.appendLine("     * @param T the instruction set interface type")
    sb.appendLine("     * @param iface the Class of the interface to register")
    sb.appendLine("     * @param factory a function that creates the implementation given an [InstructionSink]")
    sb.appendLine("     */")
    sb.appendLine("    @JvmStatic")
    sb.appendLine("    fun <T : InstructionSet> register(iface: Class<T>, factory: (InstructionSink) -> T) {")
    sb.appendLine("        factories[iface] = factory")
    sb.appendLine("    }")
    sb.appendLine()
    sb.appendLine("    /**")
    sb.appendLine("     * Creates an implementation for the given instruction set interface.")
    sb.appendLine("     *")
    sb.appendLine("     * @param iface the instruction set interface class")
    sb.appendLine("     * @param sink the shared instruction sink")
    sb.appendLine("     * @return the implementation instance")
    sb.appendLine("     * @throws IllegalStateException if no factory is registered for the interface")
    sb.appendLine("     */")
    sb.appendLine("    @JvmStatic")
    sb.appendLine("    fun createForInterface(iface: Class<*>, sink: InstructionSink): InstructionSet {")
    sb.appendLine("        return factories[iface]?.invoke(sink)")
    sb.appendLine("            ?: error(\"No implementation registered for \${iface.simpleName}\")")
    sb.appendLine("    }")
    sb.appendLine("}")

    outputDir.mkdirs()
    File(outputDir, "InstructionSetRegistry.kt").writeText(sb.toString())
}

private fun generateNullableEmitMethod(sb: StringBuilder, instruction: IrInstruction) {
    val params = instruction.emitParams
    val annotation = if (instruction.needsJvmOverloads) { "@JvmOverloads " } else { "" }
    val paramList = params.joinToString(", ") { field ->
        val defaultSuffix = if (field.default != null) { " = ${field.default}" } else { "" }
        "${field.name}: ${field.type}$defaultSuffix"
    }

    val resultTypeExpr = instruction.emitResultType ?: "returnType"
    val resultField = instruction.resultField!!

    val constructorArgs = instruction.fields.joinToString(", ") { field ->
        if (field.name == resultField.name) { "dest" } else { field.name }
    }

    sb.appendLine("    ${annotation}fun ${instruction.emitName}($paramList): Value? {")
    sb.appendLine("        val dest = if ($resultTypeExpr != Type.Void) { nextRef($resultTypeExpr) } else { null }")
    sb.appendLine("        emit(${instruction.name}($constructorArgs))")
    sb.appendLine("        return dest")
    sb.appendLine("    }")
}
