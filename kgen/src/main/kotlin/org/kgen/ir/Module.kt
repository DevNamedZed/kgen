package org.kgen.ir

import org.kgen.ir.types.*

/**
 * A compilation unit — the top-level container for all IR in a single logical grouping.
 *
 * A module is the unit of compilation: it is what you pass to a
 * [CodeGenerator][org.kgen.codegen.CodeGenerator] to produce machine code, or to a
 * [PassPipeline][org.kgen.pass.PassPipeline] for optimization. Modules are **immutable**
 * data classes — all transformations produce new modules via `copy()`.
 *
 * **Contents:**
 * - [functions] — function definitions and external declarations in SSA form
 * - [globals] — module-level variables and constants
 * - [structs], [classes], [interfaces], [enums] — type definitions for the type system
 * - [globalCtors] / [globalDtors] — static initializer and finalizer functions
 * - [submodules] — nested logical groupings with their own constraint sets
 * - [constraints] — optional restriction on which [IrCategory] values are legal
 *
 * **Building a module:**
 * ```java
 * var ir = new IrBuilder("my_module", Target.x86_64());
 * ir.createFunction("main", List.of(new Param("argc", Type.I32)), Type.I32);
 * ir.appendBlock("entry");
 * ir.ret(ir.add(ir.param(0), Constant.I32(1)));
 * ir.finalizeFunction();
 * Module module = ir.build();
 * ```
 *
 * @param name the module's name (used in debug output, symbol mangling, and diagnostics)
 * @param targetTriple target triple string (e.g., `"x86_64-unknown-linux-gnu"`)
 * @param dataLayout LLVM-compatible data layout string (e.g., `"e-m:e-p270:32:32"`)
 * @param functions all function definitions and external declarations
 * @param globals module-level global variables and constants
 * @param structs named struct type definitions
 * @param classes class type definitions (for the managed object model)
 * @param interfaces interface type definitions (for virtual dispatch)
 * @param enums enum type definitions
 * @param aliases type alias definitions
 * @param metadata arbitrary key-value metadata attached to the module
 * @param sourceFile the original source file path (for debug info)
 * @param targetFeatures CPU feature strings (e.g., `"+avx2"`, `"+neon"`)
 * @param globalCtors static initializer functions, ordered by priority (lower runs first)
 * @param globalDtors static finalizer functions, ordered by priority
 * @param ifuncs indirect function definitions (GNU ifunc mechanism)
 * @param comdats COMDAT section definitions for linker deduplication
 * @param moduleInlineAsm raw inline assembly to emit at module scope
 * @param moduleFlags module-level flag metadata (e.g., PIC level, stack protector mode)
 * @param constraints optional set of allowed [IrCategory] values; if non-null, emitting
 *   an instruction whose category is not in this set produces an error
 * @param submodules nested logical groupings within this module, each with its own constraints
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
