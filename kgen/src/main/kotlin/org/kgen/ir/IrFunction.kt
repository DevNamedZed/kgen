package org.kgen.ir

import org.kgen.ir.instructions.Instruction
import org.kgen.ir.types.TypeParamDef

/**
 * A function definition or declaration in SSA form.
 *
 * If [isExternal] is true, the function has no body — it is a forward declaration
 * of a function defined elsewhere (e.g., a C library function or a runtime intrinsic).
 * Otherwise, [blocks] contains the function body as a list of [BasicBlock]s in SSA form.
 *
 * The first block in [blocks] is the entry block. Every block except the entry block
 * must be reachable from the entry block via the control-flow graph. The last
 * instruction in every block must be a terminator (see [org.kgen.ir.instructions.TerminatorInstruction]).
 *
 * **Example usage (via builder):**
 * ```java
 * var ir = new IrBuilder("module", Target.x86_64());
 * List<Parameter> params = ir.createFunction("add",
 *     List.of(new Param("a", Type.I32), new Param("b", Type.I32)), Type.I32);
 * ir.appendBlock("entry");
 * Value sum = ir.add(params.get(0), params.get(1));
 * ir.ret(sum);
 * ir.finalizeFunction();
 * ```
 *
 * @param name the function's symbol name (unique within the module)
 * @param params the function's formal parameter list, in order
 * @param returnType the type of the value returned by this function ([Type.Void] for void functions)
 * @param blocks the basic blocks forming the function body (empty for external declarations)
 * @param isExternal true if this is a declaration only (no body)
 * @param linkage symbol linkage class (controls visibility to the linker)
 * @param visibility ELF/Mach-O symbol visibility
 * @param callingConv the calling convention used for calls to and from this function
 * @param attributes function-level attributes (e.g., [FnAttribute.NOUNWIND], [FnAttribute.INLINE])
 * @param section optional section name for the linker (e.g., `.text.hot`)
 * @param align optional function alignment in bytes (must be a power of 2)
 * @param gc garbage collection strategy name (e.g., `"shadow-stack"`, `"statepoint-example"`)
 * @param isVarArg true if this function accepts a variable number of arguments
 * @param typeParams generic type parameter definitions (for unmonomorphized generic functions)
 * @param personality the personality function for exception handling (e.g., `__gxx_personality_v0`)
 * @param comdat COMDAT group name for deduplication by the linker
 * @param prefixData constant data placed immediately before the function entry point
 * @param prologueData constant data placed at the function entry point (before the first instruction)
 * @param unnamedAddr whether the function's address is significant
 * @param dllStorageClass Windows DLL import/export classification
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
 * A basic block: a straight-line sequence of instructions ending with a terminator.
 *
 * Basic blocks are the nodes of the control-flow graph (CFG). Each block has a unique
 * [label] within its enclosing function and contains a list of [instructions] where:
 * - All instructions except the last are non-terminator instructions
 * - The last instruction is always a terminator ([org.kgen.ir.instructions.TerminatorInstruction])
 * - Phi nodes ([org.kgen.ir.instructions.Phi]), if present, must appear before all other instructions
 *
 * Block labels are used as branch targets in terminator instructions (e.g., [org.kgen.ir.instructions.Br],
 * [org.kgen.ir.instructions.CondBr]) and as predecessor identifiers in [org.kgen.ir.instructions.Phi] nodes.
 *
 * @param label the block's unique label within the enclosing function (e.g., `"entry"`, `"loop.header"`)
 * @param instructions the ordered list of instructions in this block
 */
data class BasicBlock(
    val label: String,
    val instructions: List<Instruction>,
)
