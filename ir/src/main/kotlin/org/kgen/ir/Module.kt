package org.kgen.ir

import org.kgen.ir.types.*

/**
 * A compilation unit. Contains all the IR for a single input file or logical grouping.
 *
 * Modules are immutable — build them using [IrBuilder] (imperative) or [ModuleBuilder] (DSL).
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
)
