package org.kgen.generator

import java.io.File

fun main(args: Array<String>) {
    val resourceDir = args.getOrElse(0) { "generator/src/main/resources/ir" }
    val outputDir = args.getOrElse(1) { "build/generated/ir" }

    val spec = loadIrSpec(File(resourceDir))
    println("Loaded ${spec.categories.size} categories")

    val instructionOutputDir = File(outputDir, "ir/instructions")
    val visitorOutputDir = File(outputDir, "ir/visit")
    val setsOutputDir = File(outputDir, "ir/build/sets")
    val emitterOutputDir = File(outputDir, "ir/build")

    for (category in spec.categories) {
        val instructionCount = category.allInstructions.size
        println("  ${category.name}: $instructionCount instructions")

        generateCategoryFile(category, instructionOutputDir)
        generateVisitorInterface(category, visitorOutputDir)
        generateInstructionSetInterface(category, setsOutputDir)
        generateInstructionSetImpl(category, setsOutputDir)
    }

    generateDispatch(spec.categories, visitorOutputDir)
    generateEmitterMethods(spec.categories, emitterOutputDir)
    generateScopeFiles(setsOutputDir, File(resourceDir))
    generateInstructionSetRegistry(spec.categories, setsOutputDir)

    val totalInstructions = spec.categories.sumOf { it.allInstructions.size }
    println()
    println("Generated files:")
    println("  Instruction data classes: ${instructionOutputDir.absolutePath}")
    println("  Visitor interfaces: ${visitorOutputDir.absolutePath}")
    println("  InstructionSet interfaces + impls: ${setsOutputDir.absolutePath}")
    println("  Scope interfaces + InstructionBuilder: ${setsOutputDir.absolutePath}")
    println("  Emitter methods: ${emitterOutputDir.absolutePath}")
    println("  Total instructions: $totalInstructions")
}
