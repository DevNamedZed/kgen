package org.kgen.backend.riscv.codegen

import org.kgen.backend.riscv.*
import org.kgen.backend.riscv.asm.RiscVAssembler
import org.kgen.binary.*
import org.kgen.binary.elf.ElfObjectWriter
import org.kgen.binary.elf.ElfLinker
import org.kgen.binary.elf.ElfMachine
import org.kgen.ir.*
import org.kgen.ir.codegen.*

/**
 * Translates an IR [Module] into RISC-V (RV64IM) machine code.
 *
 * Produces an [ObjectFile] with .text section, symbols, and relocations.
 * Uses the RISC-V LP64 calling convention.
 *
 * RISC-V LP64 register usage:
 *  - a0-a7 (x10-x17): argument/result registers
 *  - t0-t6 (x5-x7, x28-x31): caller-saved temporaries
 *  - s0-s11 (x8-x9, x18-x27): callee-saved
 *  - ra (x1): return address
 *  - sp (x2): stack pointer
 *  - gp (x3): global pointer (reserved)
 *  - tp (x4): thread pointer (reserved)
 */
class RiscVCodeGenerator : CodeGenerator {

    override val targetName: String = "riscv"

    override fun generate(module: Module, options: CodeGenOptions): ByteArray {
        val obj = generateObjectFile(module)
        return when (options.outputFormat) {
            OutputFormat.OBJECT -> ElfObjectWriter(ElfMachine.RISCV.code).write(obj)
            OutputFormat.BINARY -> ElfLinker().link(listOf(obj))
            OutputFormat.ASSEMBLY_TEXT -> error("Assembly text output not yet supported")
        }
    }

    fun generateObjectFile(module: Module): ObjectFile {
        val ctx = CodeGenContext(module)
        ctx.emitFunctions()
        return ctx.buildObjectFile()
    }

    private class CodeGenContext(val module: Module) {
        val asm = RiscVAssembler()
        val symbols = mutableListOf<Symbol>()
        val relocations = mutableListOf<Relocation>()

        fun emitFunctions() {
            for (fn in module.functions) {
                if (fn.isExternal) {
                    symbols.add(Symbol(fn.name, value = 0, section = null,
                        binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED))
                }
            }
            for (fn in module.functions) {
                if (fn.isExternal) continue
                val funcOffset = asm.size
                asm.label(fn.name)
                symbols.add(Symbol(fn.name, value = funcOffset.toLong(), section = ".text",
                    binding = if (fn.linkage == Linkage.INTERNAL) SymbolBinding.LOCAL else SymbolBinding.GLOBAL,
                    kind = SymbolKind.FUNCTION))
                FunctionEmitter(fn, this).emit()
            }
        }

        fun buildObjectFile(): ObjectFile {
            val textBytes = asm.bytes()
            for ((offset, label) in asm.unresolvedLabels()) {
                relocations.add(Relocation(
                    offset = offset.toLong(),
                    symbol = label,
                    type = RelocationType.RiscV.CALL_PLT,
                    addend = 0,
                    section = ".text",
                ))
            }
            return ObjectFile(
                format = ObjectFormat.ELF,
                arch = Architecture(ArchType.RISCV64),
                sections = listOf(Section(".text", SectionKind.TEXT, textBytes, align = 4)),
                symbols = symbols,
                relocations = relocations,
            )
        }
    }

    companion object {
        // LP64 argument registers (a0-a7)
        private val argRegs = arrayOf(X10, X11, X12, X13, X14, X15, X16, X17)

        // Callee-saved: s0-s11 (x8-x9, x18-x27)
        private val calleeSavedSet = setOf<RiscVGpReg>(X8, X9, X18, X19, X20, X21, X22, X23, X24, X25, X26, X27)

        // Allocatable registers: a0-a7 (args), t0-t6 (temps), s1-s11 (callee-saved, NOT s0/fp)
        // Exclude: x0 (zero), x1 (ra), x2 (sp), x3 (gp), x4 (tp), x8 (fp/s0)
        private val allocatable: List<RiscVGpReg> = listOf(
            X10, X11, X12, X13, X14, X15, X16, X17, // a0-a7
            X5, X6, X7, X28, X29, X30, X31,          // t0-t6
            X9, X18, X19, X20, X21, X22, X23, X24, X25, X26, X27, // s1-s11
        )

        // Scratch register (t0) — used for loading constants and spills
        private val scratch = X5
    }

    private class FunctionEmitter(
        private val fn: IrFunction,
        private val ctx: CodeGenContext,
    ) {
        private val asm = ctx.asm
        private lateinit var alloc: RiscVAllocResult
        private var stackReserve = 0
        private var currentBlockLabel = ""

        private val phiMoves: Map<Pair<String, String>, List<Pair<InstructionRef, Value>>> by lazy {
            val map = mutableMapOf<Pair<String, String>, MutableList<Pair<InstructionRef, Value>>>()
            for (block in fn.blocks) {
                for (inst in block.instructions) {
                    if (inst !is Instruction.Phi) break
                    for ((value, predLabel) in inst.incoming) {
                        map.getOrPut(predLabel to block.label) { mutableListOf() }
                            .add(inst.dest to value)
                    }
                }
            }
            map
        }

        fun emit() {
            runRegisterAllocator()
            emitPrologue()
            emitBlocks()
        }

        private fun runRegisterAllocator() {
            alloc = RiscVAllocator(
                fn,
                availableRegs = allocatable,
                calleeSaved = calleeSavedSet,
                paramRegs = argRegs,
            ).allocate()
        }

        private fun emitPrologue() {
            val calleeSaved = alloc.usedCalleeRegs.sortedBy { it.encoding }

            // Frame: ra + fp + callee-saved regs + spill slots, aligned to 16
            val saveSlots = 2 + calleeSaved.size + alloc.spillSlots
            val frameSize = alignTo16(saveSlots * 8)
            this.stackReserve = frameSize

            // addi sp, sp, -frameSize
            asm.addi(X2, X2, -frameSize)
            // sd ra, frameSize-8(sp)
            asm.sd(X1, RiscVMemory(X2, frameSize - 8))
            // sd fp, frameSize-16(sp)
            asm.sd(X8, RiscVMemory(X2, frameSize - 16))
            // addi fp, sp, frameSize (set up frame pointer)
            asm.addi(X8, X2, frameSize)

            // Save callee-saved registers
            var offset = frameSize - 24
            for (reg in calleeSaved) {
                asm.sd(reg, RiscVMemory(X2, offset))
                offset -= 8
            }
        }

        private fun emitEpilogue() {
            val calleeSaved = alloc.usedCalleeRegs.sortedBy { it.encoding }

            // Restore callee-saved registers
            var offset = stackReserve - 24
            for (reg in calleeSaved) {
                asm.ld(reg, RiscVMemory(X2, offset))
                offset -= 8
            }

            // ld ra, frameSize-8(sp)
            asm.ld(X1, RiscVMemory(X2, stackReserve - 8))
            // ld fp, frameSize-16(sp)
            asm.ld(X8, RiscVMemory(X2, stackReserve - 16))
            // addi sp, sp, frameSize
            asm.addi(X2, X2, stackReserve)
            asm.ret()
        }

        private fun emitBlocks() {
            for ((blockIdx, block) in fn.blocks.withIndex()) {
                currentBlockLabel = block.label
                asm.label("${fn.name}.${block.label}")
                val nextBlockLabel = fn.blocks.getOrNull(blockIdx + 1)?.label
                for ((instIdx, inst) in block.instructions.withIndex()) {
                    val cur = inst
                    if (cur is Instruction.Phi) continue

                    // Fuse ICmp + CondBr
                    if (cur is Instruction.ICmp) {
                        val nextInst = block.instructions.getOrNull(instIdx + 1)
                        if (nextInst is Instruction.CondBr) {
                            val ni = nextInst
                            if (ni.condition.name == cur.dest.name) {
                                emitFusedCmpBranch(cur, ni, nextBlockLabel)
                                break
                            }
                        }
                    }
                    if (cur is Instruction.CondBr) {
                        emitCondBr(cur, nextBlockLabel)
                        continue
                    }
                    emitInstruction(cur)
                }
            }
        }

        private fun emitInstruction(inst: Instruction) {
            val i = inst
            when (i) {
                is Instruction.Add -> emitBinOp(i.dest, i.lhs, i.rhs) { rd, rs1, rs2 -> asm.add(rd, rs1, rs2) }
                is Instruction.Sub -> emitBinOp(i.dest, i.lhs, i.rhs) { rd, rs1, rs2 -> asm.sub(rd, rs1, rs2) }
                is Instruction.Mul -> emitBinOp(i.dest, i.lhs, i.rhs) { rd, rs1, rs2 -> asm.mul(rd, rs1, rs2) }
                is Instruction.SDiv -> emitBinOp(i.dest, i.lhs, i.rhs) { rd, rs1, rs2 -> asm.div(rd, rs1, rs2) }
                is Instruction.UDiv -> emitBinOp(i.dest, i.lhs, i.rhs) { rd, rs1, rs2 -> asm.divu(rd, rs1, rs2) }
                is Instruction.SRem -> emitBinOp(i.dest, i.lhs, i.rhs) { rd, rs1, rs2 -> asm.rem(rd, rs1, rs2) }
                is Instruction.URem -> emitBinOp(i.dest, i.lhs, i.rhs) { rd, rs1, rs2 -> asm.remu(rd, rs1, rs2) }
                is Instruction.And -> emitBinOp(i.dest, i.lhs, i.rhs) { rd, rs1, rs2 -> asm.and(rd, rs1, rs2) }
                is Instruction.Or -> emitBinOp(i.dest, i.lhs, i.rhs) { rd, rs1, rs2 -> asm.or(rd, rs1, rs2) }
                is Instruction.Xor -> emitBinOp(i.dest, i.lhs, i.rhs) { rd, rs1, rs2 -> asm.xor(rd, rs1, rs2) }
                is Instruction.Shl -> emitBinOp(i.dest, i.lhs, i.rhs) { rd, rs1, rs2 -> asm.sll(rd, rs1, rs2) }
                is Instruction.LShr -> emitBinOp(i.dest, i.lhs, i.rhs) { rd, rs1, rs2 -> asm.srl(rd, rs1, rs2) }
                is Instruction.AShr -> emitBinOp(i.dest, i.lhs, i.rhs) { rd, rs1, rs2 -> asm.sra(rd, rs1, rs2) }

                is Instruction.ICmp -> emitICmp(i)
                is Instruction.Ret -> emitReturn(i)
                is Instruction.Br -> emitBr(i)
                is Instruction.Call -> emitCall(i)
                is Instruction.Select -> emitSelect(i)
                is Instruction.SExt -> emitSExt(i)
                is Instruction.ZExt -> emitZExt(i)
                is Instruction.Trunc -> emitTrunc(i)
                is Instruction.Load -> emitLoad(i)
                is Instruction.Store -> emitStore(i)
                is Instruction.Alloca -> {}

                else -> {}
            }
        }

        private fun emitBinOp(
            dest: InstructionRef, lhs: Value, rhs: Value,
            op: (RiscVGpReg, RiscVGpReg, RiscVGpReg) -> Unit,
        ) {
            val lhsReg = getOrLoad(lhs)
            val rhsReg = getOrLoad(rhs)
            val destReg = getDest(dest.name)
            op(destReg, lhsReg, rhsReg)
            storeTo(dest.name, destReg)
        }

        private fun emitICmp(inst: Instruction.ICmp) {
            val lhsReg = getOrLoad(inst.lhs)
            val rhsReg = getOrLoad(inst.rhs)
            val destReg = getDest(inst.dest.name)

            when (inst.predicate) {
                ICmpPredicate.EQ -> {
                    // xor tmp, lhs, rhs; sltiu dest, tmp, 1
                    asm.xor(destReg, lhsReg, rhsReg)
                    asm.sltiu(destReg, destReg, 1)
                }
                ICmpPredicate.NE -> {
                    // xor tmp, lhs, rhs; sltu dest, zero, tmp
                    asm.xor(destReg, lhsReg, rhsReg)
                    asm.sltu(destReg, X0, destReg)
                }
                ICmpPredicate.SLT -> asm.slt(destReg, lhsReg, rhsReg)
                ICmpPredicate.SGE -> {
                    asm.slt(destReg, lhsReg, rhsReg)
                    asm.xori(destReg, destReg, 1)
                }
                ICmpPredicate.SGT -> asm.slt(destReg, rhsReg, lhsReg)
                ICmpPredicate.SLE -> {
                    asm.slt(destReg, rhsReg, lhsReg)
                    asm.xori(destReg, destReg, 1)
                }
                ICmpPredicate.ULT -> asm.sltu(destReg, lhsReg, rhsReg)
                ICmpPredicate.UGE -> {
                    asm.sltu(destReg, lhsReg, rhsReg)
                    asm.xori(destReg, destReg, 1)
                }
                ICmpPredicate.UGT -> asm.sltu(destReg, rhsReg, lhsReg)
                ICmpPredicate.ULE -> {
                    asm.sltu(destReg, rhsReg, lhsReg)
                    asm.xori(destReg, destReg, 1)
                }
            }
            storeTo(inst.dest.name, destReg)
        }

        private fun emitFusedCmpBranch(cmp: Instruction.ICmp, br: Instruction.CondBr, nextBlockLabel: String?) {
            val lhsReg = getOrLoad(cmp.lhs)
            val rhsReg = getOrLoad(cmp.rhs)
            val trueTarget = br.trueTarget
            val falseTarget = br.falseTarget
            emitPhiMoves(trueTarget)
            emitPhiMoves(falseTarget)

            // RISC-V has direct compare-and-branch: beq, bne, blt, bge, bltu, bgeu
            when (cmp.predicate) {
                ICmpPredicate.EQ -> emitBranchPair(lhsReg, rhsReg, trueTarget, falseTarget, nextBlockLabel, BranchKind.BEQ)
                ICmpPredicate.NE -> emitBranchPair(lhsReg, rhsReg, trueTarget, falseTarget, nextBlockLabel, BranchKind.BNE)
                ICmpPredicate.SLT -> emitBranchPair(lhsReg, rhsReg, trueTarget, falseTarget, nextBlockLabel, BranchKind.BLT)
                ICmpPredicate.SGE -> emitBranchPair(lhsReg, rhsReg, trueTarget, falseTarget, nextBlockLabel, BranchKind.BGE)
                ICmpPredicate.SGT -> emitBranchPair(rhsReg, lhsReg, trueTarget, falseTarget, nextBlockLabel, BranchKind.BLT)
                ICmpPredicate.SLE -> emitBranchPair(rhsReg, lhsReg, trueTarget, falseTarget, nextBlockLabel, BranchKind.BGE)
                ICmpPredicate.ULT -> emitBranchPair(lhsReg, rhsReg, trueTarget, falseTarget, nextBlockLabel, BranchKind.BLTU)
                ICmpPredicate.UGE -> emitBranchPair(lhsReg, rhsReg, trueTarget, falseTarget, nextBlockLabel, BranchKind.BGEU)
                ICmpPredicate.UGT -> emitBranchPair(rhsReg, lhsReg, trueTarget, falseTarget, nextBlockLabel, BranchKind.BLTU)
                ICmpPredicate.ULE -> emitBranchPair(rhsReg, lhsReg, trueTarget, falseTarget, nextBlockLabel, BranchKind.BGEU)
            }
        }

        private enum class BranchKind { BEQ, BNE, BLT, BGE, BLTU, BGEU }

        private fun emitBranchPair(
            rs1: RiscVGpReg, rs2: RiscVGpReg,
            trueTarget: String, falseTarget: String,
            nextBlockLabel: String?, kind: BranchKind,
        ) {
            val trueLabel = "${fn.name}.$trueTarget"
            val falseLabel = "${fn.name}.$falseTarget"

            if (falseTarget == nextBlockLabel) {
                emitBranch(kind, rs1, rs2, trueLabel)
            } else if (trueTarget == nextBlockLabel) {
                emitBranch(invertBranch(kind), rs1, rs2, falseLabel)
            } else {
                emitBranch(kind, rs1, rs2, trueLabel)
                asm.j(falseLabel)
            }
        }

        private fun emitBranch(kind: BranchKind, rs1: RiscVGpReg, rs2: RiscVGpReg, label: String) {
            when (kind) {
                BranchKind.BEQ -> asm.beq(rs1, rs2, label)
                BranchKind.BNE -> asm.bne(rs1, rs2, label)
                BranchKind.BLT -> asm.blt(rs1, rs2, label)
                BranchKind.BGE -> asm.bge(rs1, rs2, label)
                BranchKind.BLTU -> asm.bltu(rs1, rs2, label)
                BranchKind.BGEU -> asm.bgeu(rs1, rs2, label)
            }
        }

        private fun invertBranch(kind: BranchKind): BranchKind = when (kind) {
            BranchKind.BEQ -> BranchKind.BNE
            BranchKind.BNE -> BranchKind.BEQ
            BranchKind.BLT -> BranchKind.BGE
            BranchKind.BGE -> BranchKind.BLT
            BranchKind.BLTU -> BranchKind.BGEU
            BranchKind.BGEU -> BranchKind.BLTU
        }

        private fun emitCondBr(inst: Instruction.CondBr, nextBlockLabel: String?) {
            val condReg = getOrLoad(inst.condition)
            val trueTarget = inst.trueTarget
            val falseTarget = inst.falseTarget
            emitPhiMoves(trueTarget)
            emitPhiMoves(falseTarget)

            if (falseTarget == nextBlockLabel) {
                asm.bne(condReg, X0, "${fn.name}.$trueTarget")
            } else if (trueTarget == nextBlockLabel) {
                asm.beq(condReg, X0, "${fn.name}.$falseTarget")
            } else {
                asm.bne(condReg, X0, "${fn.name}.$trueTarget")
                asm.j("${fn.name}.$falseTarget")
            }
        }

        private fun emitReturn(inst: Instruction.Ret) {
            val retVal = inst.value
            if (retVal != null) {
                val reg = getOrLoad(retVal)
                if (reg != X10) asm.mv(X10, reg)
            }
            emitEpilogue()
        }

        private fun emitBr(inst: Instruction.Br) {
            emitPhiMoves(inst.target)
            asm.j("${fn.name}.${inst.target}")
        }

        private fun emitCall(inst: Instruction.Call) {
            val args = inst.args
            val func = inst.function
            val dest = inst.dest

            for ((idx, arg) in args.withIndex()) {
                if (idx >= argRegs.size) break
                val src = getOrLoad(arg)
                if (src != argRegs[idx]) asm.mv(argRegs[idx], src)
            }

            val funcName = when (func) {
                is FunctionRef -> func.name
                is GlobalRef -> func.name
                else -> error("Cannot call: ${func::class.simpleName}")
            }
            asm.call(funcName)

            if (dest == null) return
            val destReg = getDest(dest.name)
            if (destReg != X10) asm.mv(destReg, X10)
            storeTo(dest.name, destReg)
        }

        private fun emitSelect(inst: Instruction.Select) {
            val condReg = getOrLoad(inst.condition)
            val trueReg = getOrLoad(inst.trueValue)
            val falseReg = getOrLoad(inst.falseValue)
            val destReg = getDest(inst.dest.name)

            // RISC-V has no csel, use branch: mv dest, false; bne cond, zero, +8; mv dest, true
            asm.mv(destReg, falseReg)
            asm.beq(condReg, X0, 8) // skip next instruction if condition is false
            asm.mv(destReg, trueReg)
            storeTo(inst.dest.name, destReg)
        }

        private fun emitSExt(inst: Instruction.SExt) {
            val src = getOrLoad(inst.value)
            val destReg = getDest(inst.dest.name)
            // Sign-extend 32-bit to 64-bit: addiw dest, src, 0
            asm.addiw(destReg, src, 0)
            storeTo(inst.dest.name, destReg)
        }

        private fun emitZExt(inst: Instruction.ZExt) {
            val src = getOrLoad(inst.value)
            val destReg = getDest(inst.dest.name)
            // Zero-extend 32 to 64: slli + srli by 32
            asm.slli(destReg, src, 32)
            asm.srli(destReg, destReg, 32)
            storeTo(inst.dest.name, destReg)
        }

        private fun emitTrunc(inst: Instruction.Trunc) {
            val src = getOrLoad(inst.operand)
            val destReg = getDest(inst.dest.name)
            // Truncate to 32-bit: just copy (upper bits ignored by word operations)
            if (destReg != src) asm.mv(destReg, src)
            storeTo(inst.dest.name, destReg)
        }

        private fun emitLoad(inst: Instruction.Load) {
            val addrReg = getOrLoad(inst.ptr)
            val destReg = getDest(inst.dest.name)
            val is64 = inst.dest.type.is64Bit()
            if (is64) {
                asm.ld(destReg, RiscVMemory(addrReg, 0))
            } else {
                asm.lw(destReg, RiscVMemory(addrReg, 0))
            }
            storeTo(inst.dest.name, destReg)
        }

        private fun emitStore(inst: Instruction.Store) {
            val addrReg = getOrLoad(inst.ptr)
            val valReg = getOrLoad(inst.value)
            val is64 = inst.value.type.is64Bit()
            if (is64) {
                asm.sd(valReg, RiscVMemory(addrReg, 0))
            } else {
                asm.sw(valReg, RiscVMemory(addrReg, 0))
            }
        }

        private fun emitPhiMoves(targetLabel: String) {
            val moves = phiMoves[currentBlockLabel to targetLabel] ?: return
            for ((dest, value) in moves) {
                val src = getOrLoad(value)
                val destReg = getDest(dest.name)
                if (src != destReg) asm.mv(destReg, src)
                storeTo(dest.name, destReg)
            }
        }

        // ── Register helpers ────────────────────────────────────────

        private fun getOrLoad(value: Value): RiscVGpReg {
            return when (value) {
                is Constant.I64 -> {
                    emitLoadImm(scratch, value.value)
                    scratch
                }
                is Constant.I32 -> {
                    emitLoadImm(scratch, value.value.toLong())
                    scratch
                }
                is Constant.I1 -> {
                    asm.li(scratch, if (value.value) 1 else 0)
                    scratch
                }
                is Constant.NullPtr -> {
                    asm.mv(scratch, X0)
                    scratch
                }
                else -> {
                    when (val loc = alloc.locations[value.name]) {
                        is RiscVLocation.Reg -> loc.reg
                        is RiscVLocation.Spill -> {
                            emitLoadFromFp(scratch, loc.offset)
                            scratch
                        }
                        null -> error("No allocation for ${value.name}")
                    }
                }
            }
        }

        private fun getDest(name: String): RiscVGpReg {
            return when (val loc = alloc.locations[name]) {
                is RiscVLocation.Reg -> loc.reg
                is RiscVLocation.Spill -> scratch
                else -> scratch
            }
        }

        private fun storeTo(name: String, reg: RiscVGpReg) {
            when (val loc = alloc.locations[name]) {
                is RiscVLocation.Reg -> {
                    if (loc.reg != reg) asm.mv(loc.reg, reg)
                }
                is RiscVLocation.Spill -> emitStoreToFp(reg, loc.offset)
                null -> {}
            }
        }

        // ── Immediate and stack helpers ────────────────────────────

        private fun emitLoadImm(dest: RiscVGpReg, value: Long) {
            if (value in -2048..2047) {
                asm.li(dest, value.toInt())
            } else {
                asm.li(dest, value)
            }
        }

        private fun emitLoadFromFp(dest: RiscVGpReg, offset: Int) {
            // Spill offsets are relative to FP (X8), negative
            // But our frame uses SP-relative: spill offset within the spill area
            // spillArea starts after saved regs. Use SP + spillAreaStart + abs(offset)
            val fpOffset = offset // already negative from allocator
            if (fpOffset in -2048..2047) {
                asm.ld(dest, RiscVMemory(X2, -fpOffset))
            } else {
                emitLoadImm(dest, (-fpOffset).toLong())
                asm.add(dest, X2, dest)
                asm.ld(dest, RiscVMemory(dest, 0))
            }
        }

        private fun emitStoreToFp(src: RiscVGpReg, offset: Int) {
            val fpOffset = offset
            if (fpOffset in -2048..2047) {
                asm.sd(src, RiscVMemory(X2, -fpOffset))
            } else {
                // Need scratch2 — use t1 (X6) as secondary scratch
                val scratch2 = X6
                emitLoadImm(scratch2, (-fpOffset).toLong())
                asm.add(scratch2, X2, scratch2)
                asm.sd(src, RiscVMemory(scratch2, 0))
            }
        }

        private fun alignTo16(n: Int): Int = (n + 15) and 0xFFFF_FFF0.toInt()

        private fun Type.is64Bit(): Boolean = when (this) {
            Type.I64, Type.OpaquePointer, is Type.Pointer -> true
            else -> false
        }
    }
}
