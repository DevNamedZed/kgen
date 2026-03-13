package org.kgen.ir

import org.kgen.ir.types.*

/**
 * A compilation unit containing all IR for a single input file or logical grouping.
 *
 * A module holds:
 * - [functions] -- the IR function bodies (each with basic blocks and instructions)
 * - [globals] -- module-level variables and constants
 * - [structs], [classes], [interfaces], [enums] -- type definitions
 * - [globalCtors] / [globalDtors] -- static initializers and finalizers
 * - [constraints] -- optional [IrCategory] restriction set (see [IrConstraints])
 * - [submodules] -- nested logical groupings within this module
 *
 * Modules are immutable. Build them using
 * [IrBuilder][org.kgen.ir.build.IrBuilder] (imperative) or the DSL builders.
 * Then pass to a [CodeGenerator][org.kgen.codegen.CodeGenerator] to compile.
 *
 * See `spec/ir.md` for the module model specification.
 */
data class Module(
    val name: String,
    val targetTriple: String? = null,
    val dataLayout: String? = null,
    val functions: List<IrFunction> = emptyList(),
    val globals: List<Global> = emptyList(),
    val structs: List<StructDef> = emptyList(),
    val classes: List<ClassDef> = emptyList(),
    val interfaces: List<InterfaceDef> = emptyList(),
    val enums: List<EnumDef> = emptyList(),
    val aliases: List<TypeAlias> = emptyList(),
    val metadata: Map<String, MetadataValue> = emptyMap(),
    val sourceFile: String? = null,
    val targetFeatures: Set<String> = emptySet(),
    val globalCtors: List<GlobalCtor> = emptyList(),
    val globalDtors: List<GlobalCtor> = emptyList(),
    val ifuncs: List<IFunc> = emptyList(),
    val comdats: List<ComdatDef> = emptyList(),
    val moduleInlineAsm: String? = null,
    val moduleFlags: Map<String, ModuleFlagValue> = emptyMap(),
    val constraints: Set<IrCategory>? = null,
    val submodules: List<Submodule> = emptyList(),
)
