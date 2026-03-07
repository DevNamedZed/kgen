package org.kgen.ir

import org.kgen.ir.types.TypeParamDef

/**
 * A function definition or declaration.
 *
 * If [isExternal] is true, the function has no body — it's a declaration
 * of a function defined elsewhere. Otherwise, [blocks] contains the function body in SSA form.
 */
data class IrFunction(
    val name: String,
    val params: List<Parameter>,
    val returnType: Type,
    val blocks: List<BasicBlock>,
    val isExternal: Boolean = false,
    val linkage: Linkage = Linkage.EXTERNAL,
    val visibility: Visibility = Visibility.DEFAULT,
    val callingConv: CallingConvention = CallingConvention.C,
    val attributes: Set<FnAttribute> = emptySet(),
    val section: String? = null,
    val align: Int? = null,
    val gc: String? = null,
    val isVarArg: Boolean = false,
    val typeParams: List<TypeParamDef> = emptyList(),
    val personality: FunctionRef? = null,
    val comdat: String? = null,
    val prefixData: Constant? = null,
    val prologueData: Constant? = null,
    val unnamedAddr: UnnamedAddr = UnnamedAddr.NONE,
    val dllStorageClass: DLLStorageClass = DLLStorageClass.NONE,
)

/**
 * A basic block: straight-line instructions ending with a terminator.
 */
data class BasicBlock(
    val label: String,
    val instructions: List<Instruction>,
)
