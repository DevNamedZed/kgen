package org.kgen.ir.build

import org.kgen.ir.*
import org.kgen.ir.instructions.Instruction

/**
 * Owns the per-function state during IR construction: basic blocks, insertion point,
 * SSA name counter, and category constraints.
 *
 * This is the sink that instruction set proxies emit into. It collects instructions
 * into named basic blocks and packages them into an [IrFunction] on [finalize].
 *
 * Not intended for direct use — accessed through [FunctionBuilder] or [ModuleBuilder]
 * low-level API.
 */
class FunctionContext internal constructor(
    val name: String,
    val params: List<Parameter>,
    val returnType: Type,
    private val allowedCategories: Set<IrCategory>?,
    private val meta: FunctionMeta = FunctionMeta(),
) : InstructionSink {

    private val blockInstructions = linkedMapOf<String, MutableList<Instruction>>()
    private var insertPoint: String? = null

    internal fun getInsertPoint(): String? = insertPoint
    private var nextId = 0
    private var nextBlockId = 0
    private var finalized = false

    internal data class FunctionMeta(
        val linkage: Linkage = Linkage.EXTERNAL,
        val visibility: Visibility = Visibility.DEFAULT,
        val callingConv: CallingConvention = CallingConvention.C,
        val attributes: Set<FnAttribute> = emptySet(),
        val isVarArg: Boolean = false,
        val section: String? = null,
        val align: Int? = null,
        val gc: String? = null,
        val personality: FunctionRef? = null,
        val isExternal: Boolean = false,
    )

    override fun emit(instruction: Instruction) {
        if (allowedCategories != null && instruction.category !in allowedCategories) {
            error("${instruction::class.simpleName} requires category ${instruction.category} " +
                "which is not in the allowed set")
        }
        val block = insertPoint ?: error("No insertion point set. Call appendBlock() first.")
        blockInstructions.getOrPut(block) { mutableListOf() } += instruction
    }

    override fun nextRef(type: Type) = InstructionRef("%${nextId++}", type)

    /**
     * Append a new basic block and set it as the current insertion point.
     */
    fun appendBlock(label: String? = null): String {
        val blockLabel = label ?: "bb${nextBlockId++}"
        blockInstructions.getOrPut(blockLabel) { mutableListOf() }
        insertPoint = blockLabel
        return blockLabel
    }

    /**
     * Create a forward-reference to a basic block without appending it yet.
     */
    fun createBlock(label: String? = null): BlockRef {
        val blockLabel = label ?: "bb${nextBlockId++}"
        return BlockRef(blockLabel)
    }

    /**
     * Append a pre-created block (from [createBlock]) and set it as insertion point.
     */
    fun appendBlock(blockRef: BlockRef): String {
        blockInstructions.getOrPut(blockRef.label) { mutableListOf() }
        insertPoint = blockRef.label
        return blockRef.label
    }

    /**
     * Set the insertion point to the end of an existing block.
     */
    fun positionAtEnd(blockLabel: String) {
        require(blockLabel in blockInstructions) { "Block '$blockLabel' does not exist" }
        insertPoint = blockLabel
    }

    /**
     * Get a parameter by index.
     */
    fun param(index: Int): Parameter = params[index]

    /**
     * Package the accumulated blocks into an [IrFunction] and return it.
     */
    fun finalize(): IrFunction {
        check(!finalized) { "Function '$name' already finalized" }
        finalized = true

        val blocks = blockInstructions.map { (label, instructions) ->
            BasicBlock(label, instructions.toList())
        }

        return IrFunction(
            name = name,
            params = params,
            returnType = returnType,
            blocks = blocks,
            isExternal = meta.isExternal,
            linkage = meta.linkage,
            visibility = meta.visibility,
            callingConv = meta.callingConv,
            attributes = meta.attributes,
            section = meta.section,
            align = meta.align,
            gc = meta.gc,
            isVarArg = meta.isVarArg,
            personality = meta.personality,
        )
    }
}
