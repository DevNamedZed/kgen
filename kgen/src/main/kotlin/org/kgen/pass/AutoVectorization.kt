package org.kgen.pass

import org.kgen.ir.*

/**
 * Auto-vectorization pass — transforms scalar loops and straight-line code
 * into vector operations.
 *
 * Two strategies:
 * - **Loop vectorization**: Detects simple counted loops with strided array
 *   accesses and replaces N scalar iterations with N/VF vector iterations.
 * - **SLP (Superword Level Parallelism)**: Detects groups of isomorphic
 *   scalar operations in straight-line code and packs them into vectors.
 *
 * ```java
 * var pass = new AutoVectorization(4); // vector factor 4
 * var optimized = pass.run(module);
 * ```
 */
class AutoVectorization(
    private val vectorFactor: Int = 4,
    private val enableLoopVectorization: Boolean = true,
    private val enableSLP: Boolean = true,
) : ModulePass {

    override fun run(module: Module): Module {
        return module.copy(functions = module.functions.map { fn ->
            if (fn.isExternal || fn.blocks.size < 2) fn else vectorizeFunction(fn)
        })
    }

    private fun vectorizeFunction(fn: IrFunction): IrFunction {
        var result = fn
        if (enableLoopVectorization) {
            result = vectorizeLoops(result)
        }
        if (enableSLP) {
            result = slpVectorize(result)
        }
        return result
    }

    // ========== Loop Vectorization ==========

    private fun vectorizeLoops(fn: IrFunction): IrFunction {
        val cfg = buildCfg(fn)
        val domSets = computeDomSets(fn, cfg)
        val loops = findNaturalLoops(fn, cfg, domSets)
        if (loops.isEmpty()) return fn

        var blocks = fn.blocks
        for (loop in loops) {
            val analysis = analyzeLoop(blocks, loop) ?: continue
            blocks = vectorizeLoop(blocks, loop, analysis, fn)
        }
        return fn.copy(blocks = blocks)
    }

    /**
     * Analysis result for a vectorizable loop.
     */
    private data class LoopAnalysis(
        val inductionVar: String,
        val start: Value,
        val step: Value,
        val bound: Value,
        val predicate: ICmpPredicate,
        val headerLabel: String,
        val bodyLabel: String,
        val exitLabel: String,
        val latchLabel: String,
        val arrayAccesses: List<ArrayAccess>,
        val reductions: List<Reduction>,
        val bodyInstructions: List<Instruction>,
    )

    private data class ArrayAccess(
        val ptr: Value,
        val index: Value,
        val gepInst: Instruction.GetElementPtr,
        val loadOrStore: Instruction,
        val isLoad: Boolean,
        val elementType: Type,
    )

    private data class Reduction(
        val phiInst: Instruction.Phi,
        val op: ReductionOp,
        val accumulator: String,
        val bodyValue: String,
    )

    enum class ReductionOp { ADD, MUL, AND, OR, XOR, FADD, FMUL }

    /**
     * Analyze a loop to determine if it can be vectorized.
     * Requirements:
     * - Single induction variable with constant step
     * - Known trip count (or at least a comparison bound)
     * - Array accesses with stride-1 pattern
     * - No complex control flow inside the loop body
     */
    private fun analyzeLoop(blocks: List<BasicBlock>, loop: NaturalLoop): LoopAnalysis? {
        val headerBlock = blocks.find { it.label == loop.header } ?: return null
        val bodyBlocks = loop.body - loop.header

        // Find the induction variable (phi in header)
        val phis = headerBlock.instructions.filterIsInstance<Instruction.Phi>()
        val inductionPhi = findInductionVariable(phis, bodyBlocks, blocks) ?: return null

        // Find the loop bound from the header's conditional branch
        val condBr = headerBlock.instructions.filterIsInstance<Instruction.CondBr>().firstOrNull() ?: return null
        val icmp = headerBlock.instructions.filterIsInstance<Instruction.ICmp>()
            .firstOrNull { it.result?.name == (condBr.condition as? InstructionRef)?.name } ?: return null

        // Determine exit and body targets
        val trueInLoop = condBr.trueTarget in loop.body
        val falseInLoop = condBr.falseTarget in loop.body
        if (trueInLoop == falseInLoop) return null // both in or both out — not a simple loop

        val bodyTarget = if (trueInLoop) condBr.trueTarget else condBr.falseTarget
        val exitTarget = if (trueInLoop) condBr.falseTarget else condBr.trueTarget
        val predicate = if (trueInLoop) icmp.predicate else invertPredicate(icmp.predicate)

        // Determine start value and step
        val inductionVarName = inductionPhi.dest.name
        val externalIncoming = inductionPhi.incoming.firstOrNull { it.second !in loop.body }
        val loopIncoming = inductionPhi.incoming.firstOrNull { it.second in loop.body }
        if (externalIncoming == null || loopIncoming == null) return null

        val startValue = externalIncoming.first

        // Find the step (increment instruction)
        val stepInst = findStepInstruction(inductionVarName, loopIncoming.first, blocks, loop)
            ?: return null

        // Determine bound
        val bound = if (icmp.lhs is InstructionRef && (icmp.lhs as InstructionRef).name == inductionVarName) {
            icmp.rhs
        } else if (icmp.rhs is InstructionRef && (icmp.rhs as InstructionRef).name == inductionVarName) {
            icmp.lhs
        } else return null

        // Find latch block (the block with the back edge)
        val latchLabel = loopIncoming.second

        // Collect body instructions
        val bodyInstructions = mutableListOf<Instruction>()
        val arrayAccesses = mutableListOf<ArrayAccess>()
        val reductions = mutableListOf<Reduction>()

        for (blockLabel in bodyBlocks) {
            val block = blocks.find { it.label == blockLabel } ?: continue
            for (inst in block.instructions) {
                bodyInstructions.add(inst)
                // Detect array accesses: GEP + Load/Store
                if (inst is Instruction.GetElementPtr) {
                    val nextInst = findUserOfGep(inst, blocks, loop.body)
                    if (nextInst != null) {
                        val isLoad = nextInst is Instruction.Load
                        val elemType = if (isLoad) (nextInst as Instruction.Load).loadType else {
                            (nextInst as Instruction.Store).value.type
                        }
                        if (isStrideOneAccess(inst, inductionVarName)) {
                            arrayAccesses.add(ArrayAccess(inst.ptr, inst.indices.last(), inst, nextInst, isLoad, elemType))
                        }
                    }
                }
            }
        }

        // Detect reductions (phi + binary op pattern)
        for (phi in phis) {
            if (phi.dest.name == inductionVarName) continue
            val reduction = detectReduction(phi, blocks, loop) ?: continue
            reductions.add(reduction)
        }

        // Must have at least one array access or reduction to be worth vectorizing
        if (arrayAccesses.isEmpty() && reductions.isEmpty()) return null

        return LoopAnalysis(
            inductionVar = inductionVarName,
            start = startValue,
            step = stepInst,
            bound = bound,
            predicate = predicate,
            headerLabel = loop.header,
            bodyLabel = bodyTarget,
            exitLabel = exitTarget,
            latchLabel = latchLabel,
            arrayAccesses = arrayAccesses,
            reductions = reductions,
            bodyInstructions = bodyInstructions,
        )
    }

    private fun findInductionVariable(
        phis: List<Instruction.Phi>,
        bodyBlocks: Set<String>,
        blocks: List<BasicBlock>,
    ): Instruction.Phi? {
        for (phi in phis) {
            val loopUpdate = phi.incoming.firstOrNull { it.second in bodyBlocks } ?: continue
            val updateValue = loopUpdate.first
            if (updateValue !is InstructionRef) continue
            // Check if the update is an add/sub with constant step
            for (bodyLabel in bodyBlocks) {
                val block = blocks.find { it.label == bodyLabel } ?: continue
                for (inst in block.instructions) {
                    if (inst.result?.name != updateValue.name) continue
                    if (inst is Instruction.Add || inst is Instruction.Sub) {
                        return phi
                    }
                }
            }
        }
        return null
    }

    private fun findStepInstruction(
        inductionVar: String,
        updateRef: Value,
        blocks: List<BasicBlock>,
        loop: NaturalLoop,
    ): Value? {
        if (updateRef !is InstructionRef) return null
        for (blockLabel in loop.body) {
            val block = blocks.find { it.label == blockLabel } ?: continue
            for (inst in block.instructions) {
                if (inst.result?.name != updateRef.name) continue
                when (inst) {
                    is Instruction.Add -> {
                        if (inst.lhs is InstructionRef && (inst.lhs as InstructionRef).name == inductionVar) return inst.rhs
                        if (inst.rhs is InstructionRef && (inst.rhs as InstructionRef).name == inductionVar) return inst.lhs
                    }
                    is Instruction.Sub -> {
                        if (inst.lhs is InstructionRef && (inst.lhs as InstructionRef).name == inductionVar) return inst.rhs
                    }
                    else -> {}
                }
            }
        }
        return null
    }

    private fun findUserOfGep(gep: Instruction.GetElementPtr, blocks: List<BasicBlock>, loopBlocks: Set<String>): Instruction? {
        val gepName = gep.dest.name
        for (label in loopBlocks) {
            val block = blocks.find { it.label == label } ?: continue
            for (inst in block.instructions) {
                when (inst) {
                    is Instruction.Load -> if (inst.ptr is InstructionRef && (inst.ptr as InstructionRef).name == gepName) return inst
                    is Instruction.Store -> if (inst.ptr is InstructionRef && (inst.ptr as InstructionRef).name == gepName) return inst
                    else -> {}
                }
            }
        }
        return null
    }

    private fun isStrideOneAccess(gep: Instruction.GetElementPtr, inductionVar: String): Boolean {
        val lastIndex = gep.indices.lastOrNull() ?: return false
        return lastIndex is InstructionRef && lastIndex.name == inductionVar
    }

    private fun detectReduction(phi: Instruction.Phi, blocks: List<BasicBlock>, loop: NaturalLoop): Reduction? {
        val loopIncoming = phi.incoming.firstOrNull { it.second in loop.body } ?: return null
        val updateRef = loopIncoming.first as? InstructionRef ?: return null

        for (blockLabel in loop.body) {
            val block = blocks.find { it.label == blockLabel } ?: continue
            for (inst in block.instructions) {
                if (inst.result?.name != updateRef.name) continue
                val op = when (inst) {
                    is Instruction.Add -> ReductionOp.ADD
                    is Instruction.Mul -> ReductionOp.MUL
                    is Instruction.And -> ReductionOp.AND
                    is Instruction.Or -> ReductionOp.OR
                    is Instruction.Xor -> ReductionOp.XOR
                    is Instruction.FAdd -> ReductionOp.FADD
                    is Instruction.FMul -> ReductionOp.FMUL
                    else -> return null
                }
                return Reduction(phi, op, phi.dest.name, updateRef.name)
            }
        }
        return null
    }

    private fun vectorizeLoop(
        blocks: List<BasicBlock>,
        loop: NaturalLoop,
        analysis: LoopAnalysis,
        fn: IrFunction,
    ): List<BasicBlock> {
        val vf = vectorFactor
        var nextId = findMaxInstructionId(fn) + 1

        fun nextRef(type: Type): InstructionRef {
            val ref = InstructionRef("v${nextId}", type)
            nextId++
            return ref
        }

        // Build vectorized preheader, vector loop body, scalar remainder
        val vecPreheaderLabel = "${analysis.headerLabel}_vec_ph"
        val vecHeaderLabel = "${analysis.headerLabel}_vec"
        val vecBodyLabel = "${analysis.bodyLabel}_vec"
        val vecLatchLabel = "${analysis.latchLabel}_vec"
        val scalarPreheaderLabel = "${analysis.headerLabel}_scalar_ph"

        val vecPreheaderInsts = mutableListOf<Instruction>()
        val vecHeaderInsts = mutableListOf<Instruction>()
        val vecBodyInsts = mutableListOf<Instruction>()
        val vecLatchInsts = mutableListOf<Instruction>()

        // Compute vector trip count: (bound - start) / vf * vf + start
        val tripCountRef = nextRef(Type.I32)
        vecPreheaderInsts.add(Instruction.Sub(tripCountRef, analysis.bound, analysis.start))

        val vfConst = Constant.I32(vf)
        val vfMask = Constant.I32(vf - 1)
        val vecTripRef = nextRef(Type.I32)
        vecPreheaderInsts.add(Instruction.And(vecTripRef, tripCountRef, Constant.I32(vf.inv().and(0x7FFFFFFF) + 1))) // round down

        // Actually, simpler: vecLimit = start + vecTrip
        // vecTrip = tripCount & ~(vf - 1) = tripCount - (tripCount % vf)
        val remRef = nextRef(Type.I32)
        vecPreheaderInsts.add(Instruction.And(remRef, tripCountRef, vfConst.let { Constant.I32(vf - 1) }))
        val cleanTripRef = nextRef(Type.I32)
        vecPreheaderInsts.add(Instruction.Sub(cleanTripRef, tripCountRef, remRef))
        val vecLimitRef = nextRef(Type.I32)
        vecPreheaderInsts.add(Instruction.Add(vecLimitRef, analysis.start, cleanTripRef))

        // Vector header: induction phi
        val vecIndRef = nextRef(Type.I32)
        val vecStepConst = Constant.I32(vf)

        // Initialize reduction accumulators as vector splats
        val reductionVecPhis = mutableListOf<Pair<Reduction, InstructionRef>>()

        vecPreheaderInsts.add(Instruction.Br(vecHeaderLabel))

        // Vec header: phi for induction + check
        val vecIndPhi = Instruction.Phi(vecIndRef, listOf(
            analysis.start to vecPreheaderLabel,
        ))
        vecHeaderInsts.add(vecIndPhi)

        // Reduction phis
        for (red in analysis.reductions) {
            val vecType = Type.Vector(red.phiInst.dest.type, vf)
            val vecAccRef = nextRef(vecType)
            val initScalar = red.phiInst.incoming.firstOrNull { it.second !in loop.body }?.first
                ?: continue
            reductionVecPhis.add(red to vecAccRef)
            // We'll create phis with placeholders — to be updated
            vecHeaderInsts.add(Instruction.Phi(vecAccRef, listOf(
                // initial splat will be in preheader
            )))
        }

        // Compare: vecInd < vecLimit
        val vecCmpRef = nextRef(Type.I1)
        vecHeaderInsts.add(Instruction.ICmp(vecCmpRef, analysis.predicate, vecIndRef, vecLimitRef))
        vecHeaderInsts.add(Instruction.CondBr(vecCmpRef, vecBodyLabel, scalarPreheaderLabel))

        // Vec body: widen loads, compute arithmetic, widen stores
        // Map from scalar instruction result name → widened vector value
        val scalarToVector = mutableMapOf<String, Value>()

        // Map induction variable to the vector induction ref
        scalarToVector[analysis.inductionVar] = vecIndRef

        // Map reduction phi accumulators to their vector versions
        for ((red, vecAccRef) in reductionVecPhis) {
            scalarToVector[red.accumulator] = vecAccRef
        }

        // First pass: emit widened loads (GEP + vector load)
        for (access in analysis.arrayAccesses) {
            if (access.isLoad) {
                val gepRef = nextRef(access.gepInst.ptr.type)
                vecBodyInsts.add(Instruction.GetElementPtr(
                    gepRef, access.gepInst.baseType, access.gepInst.ptr,
                    access.gepInst.indices.map { idx ->
                        if (idx is InstructionRef && idx.name == analysis.inductionVar) vecIndRef
                        else idx
                    },
                    access.gepInst.inBounds,
                ))
                val vecType = Type.Vector(access.elementType, vf)
                val vecLoadRef = nextRef(vecType)
                vecBodyInsts.add(Instruction.Load(vecLoadRef, gepRef, vecType))
                // Map the original load's result to the vector load
                val loadResult = access.loadOrStore.result
                if (loadResult != null) {
                    scalarToVector[loadResult.name] = vecLoadRef
                }
                // Also map the GEP result
                scalarToVector[access.gepInst.dest.name] = gepRef
            }
        }

        // Second pass: emit widened arithmetic operations from the loop body
        // Skip instructions that are already handled (GEP, Load, Store, Phi, terminators, induction increment)
        val handledGeps = analysis.arrayAccesses.map { it.gepInst.dest.name }.toSet()
        val handledLoadsStores = analysis.arrayAccesses.map { it.loadOrStore.result?.name }.filterNotNull().toSet()
        val inductionUpdateNames = findInductionUpdateNames(analysis, blocks, loop)

        for (inst in analysis.bodyInstructions) {
            val resultName = inst.result?.name ?: continue
            // Skip already-handled instructions
            if (resultName in handledGeps || resultName in handledLoadsStores) continue
            if (resultName in inductionUpdateNames) continue
            if (inst is Instruction.Phi || inst is Instruction.Br || inst is Instruction.CondBr) continue

            // Try to widen this arithmetic instruction
            val widened = widenInstruction(inst, scalarToVector, vf, ::nextRef)
            if (widened != null) {
                vecBodyInsts.add(widened)
                scalarToVector[resultName] = widened.result!!
            }
        }

        // Third pass: emit widened stores
        for (access in analysis.arrayAccesses) {
            if (!access.isLoad) {
                val gepRef = nextRef(access.gepInst.ptr.type)
                vecBodyInsts.add(Instruction.GetElementPtr(
                    gepRef, access.gepInst.baseType, access.gepInst.ptr,
                    access.gepInst.indices.map { idx ->
                        if (idx is InstructionRef && idx.name == analysis.inductionVar) vecIndRef
                        else idx
                    },
                    access.gepInst.inBounds,
                ))
                // Find the vector value to store
                val storeInst = access.loadOrStore as Instruction.Store
                val storeVal = storeInst.value
                val vecStoreVal = if (storeVal is InstructionRef) {
                    scalarToVector[storeVal.name] ?: storeVal
                } else storeVal
                val vecType = Type.Vector(access.elementType, vf)
                vecBodyInsts.add(Instruction.Store(vecStoreVal, gepRef))
            }
        }

        // Update reduction accumulators in the vector body
        for ((red, vecAccRef) in reductionVecPhis) {
            val updatedVal = scalarToVector[red.bodyValue]
            if (updatedVal != null) {
                scalarToVector[red.accumulator] = updatedVal
            }
        }

        // Vec latch: increment induction by VF
        val vecNextIndRef = nextRef(Type.I32)
        vecLatchInsts.add(Instruction.Add(vecNextIndRef, vecIndRef, vecStepConst))
        vecLatchInsts.add(Instruction.Br(vecHeaderLabel))

        // Build new blocks
        val newBlocks = mutableListOf<BasicBlock>()
        for (block in blocks) {
            if (block.label == analysis.headerLabel) {
                // Insert vector preheader and vector loop before scalar loop
                newBlocks.add(BasicBlock(vecPreheaderLabel, vecPreheaderInsts))
                newBlocks.add(BasicBlock(vecHeaderLabel, vecHeaderInsts))
                newBlocks.add(BasicBlock(vecBodyLabel, vecBodyInsts + Instruction.Br(vecLatchLabel)))
                newBlocks.add(BasicBlock(vecLatchLabel, vecLatchInsts))

                // Scalar preheader: branch to original header
                newBlocks.add(BasicBlock(scalarPreheaderLabel, listOf(Instruction.Br(analysis.headerLabel))))

                // Update original header's phi: start from vecLimit instead of original start
                val updatedInsts = block.instructions.map { inst ->
                    if (inst is Instruction.Phi && inst.dest.name == analysis.inductionVar) {
                        Instruction.Phi(inst.dest, inst.incoming.map { (v, label) ->
                            if (label !in loop.body) vecLimitRef to scalarPreheaderLabel
                            else v to label
                        })
                    } else inst
                }
                newBlocks.add(BasicBlock(block.label, updatedInsts))
            } else {
                newBlocks.add(block)
            }
        }
        return newBlocks
    }

    // ========== SLP Vectorization ==========

    /**
     * SLP vectorization: find groups of isomorphic scalar operations in
     * straight-line code and combine them into vector operations.
     */
    private fun slpVectorize(fn: IrFunction): IrFunction {
        var anyChanged = false
        val newBlocks = fn.blocks.map { block ->
            val result = slpVectorizeBlock(block, fn)
            if (result != block) anyChanged = true
            result
        }
        return if (anyChanged) fn.copy(blocks = newBlocks) else fn
    }

    private fun slpVectorizeBlock(block: BasicBlock, fn: IrFunction): BasicBlock {
        val instructions = block.instructions
        if (instructions.size < 2) return block

        // Find groups of consecutive isomorphic instructions
        val groups = findIsomorphicGroups(instructions)
        if (groups.isEmpty()) return block

        val newInstructions = mutableListOf<Instruction>()
        val consumed = mutableSetOf<Int>()
        var nextId = findMaxInstructionId(fn) + 1

        fun nextRef(type: Type): InstructionRef {
            val ref = InstructionRef("slp${nextId}", type)
            nextId++
            return ref
        }

        for (group in groups) {
            if (group.size < 2) continue
            val first = instructions[group[0]]
            val elementType = first.result?.type ?: continue
            val vecType = Type.Vector(elementType, group.size)

            // Pack operands into vectors
            val packedOps = packOperands(instructions, group, vecType, ::nextRef)
            if (packedOps == null) continue

            // Create the vector operation
            val vecResult = nextRef(vecType)
            val vecOp = createVectorOp(first, vecResult, packedOps)
            if (vecOp == null) continue

            // Add pack, op, and unpack instructions
            newInstructions.addAll(packedOps.packInstructions)
            newInstructions.add(vecOp)

            // Extract results back to scalars
            for ((i, idx) in group.withIndex()) {
                val origResult = instructions[idx].result ?: continue
                val extractRef = InstructionRef(origResult.name, elementType)
                newInstructions.add(Instruction.ExtractElement(extractRef, vecResult, Constant.I32(i)))
                consumed.add(idx)
            }
        }

        if (consumed.isEmpty()) return block

        // Rebuild instruction list preserving order for non-consumed instructions
        val result = mutableListOf<Instruction>()
        var insertedPack = false
        for ((i, inst) in instructions.withIndex()) {
            if (i in consumed) {
                if (!insertedPack) {
                    result.addAll(newInstructions)
                    insertedPack = true
                }
            } else {
                result.add(inst)
            }
        }
        return BasicBlock(block.label, result)
    }

    private data class PackedOperands(
        val operandVectors: List<Value>,
        val packInstructions: List<Instruction>,
    )

    private fun packOperands(
        instructions: List<Instruction>,
        group: List<Int>,
        vecType: Type.Vector,
        nextRef: (Type) -> InstructionRef,
    ): PackedOperands? {
        val first = instructions[group[0]]
        val operandCount = operandCountOf(first)
        if (operandCount == 0) return null

        val packInsts = mutableListOf<Instruction>()
        val operandVecs = mutableListOf<Value>()

        for (opIdx in 0 until operandCount) {
            val scalars = group.map { instrIdx -> getOperand(instructions[instrIdx], opIdx) ?: return null }
            // Create a vector by inserting each scalar
            var vec: Value = nextRef(vecType)
            val splatRef = vec as InstructionRef
            packInsts.add(Instruction.Splat(splatRef, scalars[0], vecType))
            for (i in 1 until scalars.size) {
                val insertRef = nextRef(vecType)
                packInsts.add(Instruction.InsertElement(insertRef, vec, scalars[i], Constant.I32(i)))
                vec = insertRef
            }
            operandVecs.add(vec)
        }

        return PackedOperands(operandVecs, packInsts)
    }

    private fun createVectorOp(template: Instruction, dest: InstructionRef, packed: PackedOperands): Instruction? {
        val ops = packed.operandVectors
        return when (template) {
            is Instruction.Add -> Instruction.Add(dest, ops[0], ops[1])
            is Instruction.Sub -> Instruction.Sub(dest, ops[0], ops[1])
            is Instruction.Mul -> Instruction.Mul(dest, ops[0], ops[1])
            is Instruction.FAdd -> Instruction.FAdd(dest, ops[0], ops[1])
            is Instruction.FSub -> Instruction.FSub(dest, ops[0], ops[1])
            is Instruction.FMul -> Instruction.FMul(dest, ops[0], ops[1])
            is Instruction.FDiv -> Instruction.FDiv(dest, ops[0], ops[1])
            is Instruction.And -> Instruction.And(dest, ops[0], ops[1])
            is Instruction.Or -> Instruction.Or(dest, ops[0], ops[1])
            is Instruction.Xor -> Instruction.Xor(dest, ops[0], ops[1])
            is Instruction.Shl -> Instruction.Shl(dest, ops[0], ops[1])
            is Instruction.LShr -> Instruction.LShr(dest, ops[0], ops[1])
            is Instruction.AShr -> Instruction.AShr(dest, ops[0], ops[1])
            is Instruction.Neg -> Instruction.Neg(dest, ops[0])
            is Instruction.FNeg -> Instruction.FNeg(dest, ops[0])
            else -> null
        }
    }

    private fun findIsomorphicGroups(instructions: List<Instruction>): List<List<Int>> {
        val groups = mutableListOf<List<Int>>()
        var i = 0
        while (i < instructions.size) {
            val first = instructions[i]
            if (!isSLPCandidate(first)) { i++; continue }

            val group = mutableListOf(i)
            var j = i + 1
            while (j < instructions.size && group.size < 8) {
                if (isIsomorphic(first, instructions[j])) {
                    group.add(j)
                }
                j++
                // Only look at nearby instructions
                if (j - i > 16) break
            }

            if (group.size >= 2 && isPowerOfTwo(group.size)) {
                groups.add(group)
                i = group.last() + 1
            } else {
                i++
            }
        }
        return groups
    }

    private fun isSLPCandidate(inst: Instruction): Boolean = when (inst) {
        is Instruction.Add, is Instruction.Sub, is Instruction.Mul,
        is Instruction.FAdd, is Instruction.FSub, is Instruction.FMul, is Instruction.FDiv,
        is Instruction.And, is Instruction.Or, is Instruction.Xor,
        is Instruction.Shl, is Instruction.LShr, is Instruction.AShr,
        is Instruction.Neg, is Instruction.FNeg -> true
        else -> false
    }

    private fun isIsomorphic(a: Instruction, b: Instruction): Boolean {
        if (a::class != b::class) return false
        val aResult = a.result ?: return false
        val bResult = b.result ?: return false
        return aResult.type == bResult.type
    }

    private fun operandCountOf(inst: Instruction): Int = when (inst) {
        is Instruction.Neg, is Instruction.FNeg -> 1
        is Instruction.Add, is Instruction.Sub, is Instruction.Mul,
        is Instruction.FAdd, is Instruction.FSub, is Instruction.FMul, is Instruction.FDiv,
        is Instruction.And, is Instruction.Or, is Instruction.Xor,
        is Instruction.Shl, is Instruction.LShr, is Instruction.AShr -> 2
        else -> 0
    }

    private fun getOperand(inst: Instruction, index: Int): Value? = when (inst) {
        is Instruction.Add -> if (index == 0) inst.lhs else inst.rhs
        is Instruction.Sub -> if (index == 0) inst.lhs else inst.rhs
        is Instruction.Mul -> if (index == 0) inst.lhs else inst.rhs
        is Instruction.FAdd -> if (index == 0) inst.lhs else inst.rhs
        is Instruction.FSub -> if (index == 0) inst.lhs else inst.rhs
        is Instruction.FMul -> if (index == 0) inst.lhs else inst.rhs
        is Instruction.FDiv -> if (index == 0) inst.lhs else inst.rhs
        is Instruction.And -> if (index == 0) inst.lhs else inst.rhs
        is Instruction.Or -> if (index == 0) inst.lhs else inst.rhs
        is Instruction.Xor -> if (index == 0) inst.lhs else inst.rhs
        is Instruction.Shl -> if (index == 0) inst.lhs else inst.rhs
        is Instruction.LShr -> if (index == 0) inst.lhs else inst.rhs
        is Instruction.AShr -> if (index == 0) inst.lhs else inst.rhs
        is Instruction.Neg -> if (index == 0) inst.operand else null
        is Instruction.FNeg -> if (index == 0) inst.operand else null
        else -> null
    }

    private fun isPowerOfTwo(n: Int): Boolean = n > 0 && (n and (n - 1)) == 0

    private fun invertPredicate(pred: ICmpPredicate): ICmpPredicate = when (pred) {
        ICmpPredicate.EQ -> ICmpPredicate.NE
        ICmpPredicate.NE -> ICmpPredicate.EQ
        ICmpPredicate.SGT -> ICmpPredicate.SLE
        ICmpPredicate.SGE -> ICmpPredicate.SLT
        ICmpPredicate.SLT -> ICmpPredicate.SGE
        ICmpPredicate.SLE -> ICmpPredicate.SGT
        ICmpPredicate.UGT -> ICmpPredicate.ULE
        ICmpPredicate.UGE -> ICmpPredicate.ULT
        ICmpPredicate.ULT -> ICmpPredicate.UGE
        ICmpPredicate.ULE -> ICmpPredicate.UGT
    }

    private fun findMaxInstructionId(fn: IrFunction): Long {
        var maxId = 0L
        for (block in fn.blocks) {
            for (inst in block.instructions) {
                val result = inst.result ?: continue
                val name = result.name
                val num = name.removePrefix("v").removePrefix("slp").toLongOrNull()
                if (num != null && num > maxId) maxId = num
            }
        }
        return maxId
    }

    /**
     * Widen a scalar arithmetic instruction to operate on vector types,
     * remapping operands via the scalarToVector map.
     */
    private fun widenInstruction(
        inst: Instruction,
        scalarToVector: Map<String, Value>,
        vf: Int,
        nextRef: (Type) -> InstructionRef,
    ): Instruction? {
        val scalarType = inst.result?.type ?: return null
        val vecType = Type.Vector(scalarType, vf)
        val dest = nextRef(vecType)

        fun widen(v: Value): Value {
            if (v is InstructionRef) return scalarToVector[v.name] ?: v
            // Constants get splatted — but for simplicity in IR, we keep them as-is
            // (backends should handle scalar-broadcast in vector ops)
            return v
        }

        return when (inst) {
            is Instruction.Add -> Instruction.Add(dest, widen(inst.lhs), widen(inst.rhs))
            is Instruction.Sub -> Instruction.Sub(dest, widen(inst.lhs), widen(inst.rhs))
            is Instruction.Mul -> Instruction.Mul(dest, widen(inst.lhs), widen(inst.rhs))
            is Instruction.SDiv -> Instruction.SDiv(dest, widen(inst.lhs), widen(inst.rhs))
            is Instruction.UDiv -> Instruction.UDiv(dest, widen(inst.lhs), widen(inst.rhs))
            is Instruction.SRem -> Instruction.SRem(dest, widen(inst.lhs), widen(inst.rhs))
            is Instruction.URem -> Instruction.URem(dest, widen(inst.lhs), widen(inst.rhs))
            is Instruction.FAdd -> Instruction.FAdd(dest, widen(inst.lhs), widen(inst.rhs))
            is Instruction.FSub -> Instruction.FSub(dest, widen(inst.lhs), widen(inst.rhs))
            is Instruction.FMul -> Instruction.FMul(dest, widen(inst.lhs), widen(inst.rhs))
            is Instruction.FDiv -> Instruction.FDiv(dest, widen(inst.lhs), widen(inst.rhs))
            is Instruction.And -> Instruction.And(dest, widen(inst.lhs), widen(inst.rhs))
            is Instruction.Or -> Instruction.Or(dest, widen(inst.lhs), widen(inst.rhs))
            is Instruction.Xor -> Instruction.Xor(dest, widen(inst.lhs), widen(inst.rhs))
            is Instruction.Shl -> Instruction.Shl(dest, widen(inst.lhs), widen(inst.rhs))
            is Instruction.LShr -> Instruction.LShr(dest, widen(inst.lhs), widen(inst.rhs))
            is Instruction.AShr -> Instruction.AShr(dest, widen(inst.lhs), widen(inst.rhs))
            is Instruction.Neg -> Instruction.Neg(dest, widen(inst.operand))
            is Instruction.FNeg -> Instruction.FNeg(dest, widen(inst.operand))
            else -> null
        }
    }

    /**
     * Find the names of instructions that update the induction variable (the increment),
     * so they can be skipped when widening body instructions.
     */
    private fun findInductionUpdateNames(
        analysis: LoopAnalysis,
        blocks: List<BasicBlock>,
        loop: NaturalLoop,
    ): Set<String> {
        val names = mutableSetOf<String>()
        for (blockLabel in loop.body) {
            val block = blocks.find { it.label == blockLabel } ?: continue
            for (inst in block.instructions) {
                val resultName = inst.result?.name ?: continue
                when (inst) {
                    is Instruction.Add -> {
                        val lhsName = (inst.lhs as? InstructionRef)?.name
                        val rhsName = (inst.rhs as? InstructionRef)?.name
                        if (lhsName == analysis.inductionVar || rhsName == analysis.inductionVar) {
                            names.add(resultName)
                        }
                    }
                    is Instruction.Sub -> {
                        val lhsName = (inst.lhs as? InstructionRef)?.name
                        if (lhsName == analysis.inductionVar) {
                            names.add(resultName)
                        }
                    }
                    else -> {}
                }
            }
        }
        return names
    }

    // --- CFG and loop detection infrastructure ---

    private data class Cfg(
        val predecessors: Map<String, Set<String>>,
        val successors: Map<String, Set<String>>,
    )

    private data class NaturalLoop(
        val header: String,
        val body: Set<String>,
    )

    private fun buildCfg(fn: IrFunction): Cfg {
        val preds = mutableMapOf<String, MutableSet<String>>()
        val succs = mutableMapOf<String, MutableSet<String>>()
        for (block in fn.blocks) {
            preds.getOrPut(block.label) { mutableSetOf() }
            succs.getOrPut(block.label) { mutableSetOf() }
        }
        for (block in fn.blocks) {
            val term = block.instructions.lastOrNull() ?: continue
            for (t in terminatorTargets(term)) {
                succs.getOrPut(block.label) { mutableSetOf() }.add(t)
                preds.getOrPut(t) { mutableSetOf() }.add(block.label)
            }
        }
        return Cfg(preds, succs)
    }

    private fun terminatorTargets(inst: Instruction): List<String> = when (inst) {
        is Instruction.Br -> listOf(inst.target)
        is Instruction.CondBr -> listOf(inst.trueTarget, inst.falseTarget)
        is Instruction.Switch -> listOf(inst.defaultTarget) + inst.cases.map { it.second }
        is Instruction.IndirectBr -> inst.targets
        else -> emptyList()
    }

    private fun computeDomSets(fn: IrFunction, cfg: Cfg): Map<String, Set<String>> {
        val blocks = fn.blocks.map { it.label }
        val entry = blocks[0]
        val allBlocks = blocks.toSet()

        val dom = mutableMapOf<String, MutableSet<String>>()
        dom[entry] = mutableSetOf(entry)
        for (b in blocks) {
            if (b != entry) dom[b] = allBlocks.toMutableSet()
        }

        var changed = true
        while (changed) {
            changed = false
            for (b in blocks) {
                if (b == entry) continue
                val preds = cfg.predecessors[b] ?: continue
                if (preds.isEmpty()) continue
                var newDom = allBlocks.toMutableSet()
                for (p in preds) {
                    newDom = newDom.intersect(dom[p] ?: emptySet()).toMutableSet()
                }
                newDom.add(b)
                if (newDom != dom[b]) {
                    dom[b] = newDom
                    changed = true
                }
            }
        }
        return dom
    }

    private fun findNaturalLoops(fn: IrFunction, cfg: Cfg, domSets: Map<String, Set<String>>): List<NaturalLoop> {
        val loops = mutableListOf<NaturalLoop>()
        for (block in fn.blocks) {
            val term = block.instructions.lastOrNull() ?: continue
            for (target in terminatorTargets(term)) {
                val domSet = domSets[block.label] ?: emptySet()
                if (target in domSet) {
                    val body = computeLoopBody(target, block.label, cfg)
                    loops.add(NaturalLoop(header = target, body = body))
                }
            }
        }
        return loops
    }

    private fun computeLoopBody(header: String, backEdgeSource: String, cfg: Cfg): Set<String> {
        val body = mutableSetOf(header, backEdgeSource)
        val worklist = ArrayDeque<String>()
        if (backEdgeSource != header) {
            worklist.add(backEdgeSource)
        }
        while (worklist.isNotEmpty()) {
            val block = worklist.removeFirst()
            for (pred in cfg.predecessors[block] ?: emptySet()) {
                if (pred !in body) {
                    body.add(pred)
                    worklist.add(pred)
                }
            }
        }
        return body
    }

    companion object {
        /** Compute the optimal vector factor for a given element type and target. */
        @JvmStatic
        fun suggestVectorFactor(elementType: Type): Int = when (elementType) {
            Type.I8 -> 16
            Type.I16 -> 8
            Type.I32, Type.F32 -> 4
            Type.I64, Type.F64 -> 2
            else -> 1
        }

        /** Convert a scalar reduction operation to the corresponding VectorReduceOp. */
        @JvmStatic
        fun toVectorReduceOp(op: ReductionOp): VectorReduceOp = when (op) {
            ReductionOp.ADD -> VectorReduceOp.ADD
            ReductionOp.MUL -> VectorReduceOp.MUL
            ReductionOp.AND -> VectorReduceOp.AND
            ReductionOp.OR -> VectorReduceOp.OR
            ReductionOp.XOR -> VectorReduceOp.XOR
            ReductionOp.FADD -> VectorReduceOp.FADD
            ReductionOp.FMUL -> VectorReduceOp.FMUL
        }
    }
}
