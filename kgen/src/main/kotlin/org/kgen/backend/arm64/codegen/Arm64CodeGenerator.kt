package org.kgen.backend.arm64.codegen

import org.kgen.backend.arm64.*
import org.kgen.backend.arm64.asm.*
import org.kgen.binary.*
import org.kgen.binary.elf.ElfObjectWriter
import org.kgen.binary.elf.ElfLinker
import org.kgen.binary.elf.ElfMachine
import org.kgen.ir.*
import org.kgen.ir.FCmpPredicate
import org.kgen.ir.codegen.*

/**
 * Translates an IR [Module] into AArch64 machine code.
 *
 * Produces an [ObjectFile] with .text section, symbols, and relocations.
 * Uses the AAPCS64 calling convention (ARM Architecture Procedure Call Standard).
 *
 * AAPCS64 register usage:
 *  - X0-X7: argument/result registers
 *  - X8: indirect result location
 *  - X9-X15: caller-saved temporaries
 *  - X16-X17: intra-procedure call scratch (IP0/IP1)
 *  - X18: platform register (reserved on some OSes)
 *  - X19-X28: callee-saved
 *  - X29: frame pointer (FP)
 *  - X30: link register (LR)
 *  - SP: stack pointer
 */
class Arm64CodeGenerator : CodeGenerator {

    override val targetName: String = "aarch64"

    override fun generate(module: Module, options: CodeGenOptions): ByteArray {
        val obj = generateObjectFile(module)
        return when (options.outputFormat) {
            OutputFormat.OBJECT -> ElfObjectWriter(ElfMachine.AARCH64.code).write(obj)
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
        val asm = Arm64Assembler()
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
                val funcOffset = asm.size()
                asm.label(fn.name)
                symbols.add(Symbol(fn.name, value = funcOffset.toLong(), section = ".text",
                    binding = if (fn.linkage == Linkage.INTERNAL) SymbolBinding.LOCAL else SymbolBinding.GLOBAL,
                    kind = SymbolKind.FUNCTION))
                FunctionEmitter(fn, this).emit()
            }
        }

        fun buildObjectFile(): ObjectFile {
            val textBytes = asm.bytes()
            // Generate relocations for unresolved labels (external calls)
            for ((offset, label) in asm.unresolvedLabels()) {
                relocations.add(Relocation(
                    offset = offset.toLong(),
                    symbol = label,
                    type = RelocationType.AArch64.CALL26,
                    addend = 0,
                    section = ".text",
                ))
            }
            return ObjectFile(
                format = ObjectFormat.ELF,
                arch = Architecture(ArchType.AARCH64),
                sections = listOf(Section(".text", SectionKind.TEXT, textBytes, align = 4)),
                symbols = symbols,
                relocations = relocations,
            )
        }
    }

    companion object {
        // AAPCS64 argument registers (X0-X7)
        private val argRegs64 = arrayOf(
            Arm64Register.X0, Arm64Register.X1, Arm64Register.X2, Arm64Register.X3,
            Arm64Register.X4, Arm64Register.X5, Arm64Register.X6, Arm64Register.X7,
        )
        private val argRegs32 = arrayOf(
            Arm64Register.W0, Arm64Register.W1, Arm64Register.W2, Arm64Register.W3,
            Arm64Register.W4, Arm64Register.W5, Arm64Register.W6, Arm64Register.W7,
        )

        // Callee-saved: X19-X28
        private val calleeSavedSet = setOf(
            Arm64Register.X19, Arm64Register.X20, Arm64Register.X21, Arm64Register.X22,
            Arm64Register.X23, Arm64Register.X24, Arm64Register.X25, Arm64Register.X26,
            Arm64Register.X27, Arm64Register.X28,
        )

        // Allocatable registers: X0-X15, X19-X28 (not X16/X17=IP0/IP1, X18=platform, X29=FP, X30=LR)
        private val allocatable64 = listOf(
            Arm64Register.X0, Arm64Register.X1, Arm64Register.X2, Arm64Register.X3,
            Arm64Register.X4, Arm64Register.X5, Arm64Register.X6, Arm64Register.X7,
            Arm64Register.X8, Arm64Register.X9, Arm64Register.X10, Arm64Register.X11,
            Arm64Register.X12, Arm64Register.X13, Arm64Register.X14, Arm64Register.X15,
            Arm64Register.X19, Arm64Register.X20, Arm64Register.X21, Arm64Register.X22,
            Arm64Register.X23, Arm64Register.X24, Arm64Register.X25, Arm64Register.X26,
            Arm64Register.X27, Arm64Register.X28,
        )
        private val allocatable32 = listOf(
            Arm64Register.W0, Arm64Register.W1, Arm64Register.W2, Arm64Register.W3,
            Arm64Register.W4, Arm64Register.W5, Arm64Register.W6, Arm64Register.W7,
            Arm64Register.W8, Arm64Register.W9, Arm64Register.W10, Arm64Register.W11,
            Arm64Register.W12, Arm64Register.W13, Arm64Register.W14, Arm64Register.W15,
            Arm64Register.W19, Arm64Register.W20, Arm64Register.W21, Arm64Register.W22,
            Arm64Register.W23, Arm64Register.W24, Arm64Register.W25, Arm64Register.W26,
            Arm64Register.W27, Arm64Register.W28,
        )

        // Scratch registers (IP0/IP1)
        private val scratch64 = Arm64Register.X16
        private val scratch32 = Arm64Register.W16
        private val scratchFpD = Arm64Register.D16
        private val scratchFpS = Arm64Register.S16

        // AAPCS64 FP argument registers (D0-D7)
        private val fpArgRegs = arrayOf(
            Arm64Register.D0, Arm64Register.D1, Arm64Register.D2, Arm64Register.D3,
            Arm64Register.D4, Arm64Register.D5, Arm64Register.D6, Arm64Register.D7,
        )

        // FP callee-saved: D8-D15 (only lower 64 bits)
        private val fpCalleeSavedSet = setOf(
            Arm64Register.D8, Arm64Register.D9, Arm64Register.D10, Arm64Register.D11,
            Arm64Register.D12, Arm64Register.D13, Arm64Register.D14, Arm64Register.D15,
        )

        // FP allocatable: D0-D31 (except we use all of them)
        private val allocatableFp = (0..31).map { Arm64Register.byEncodingD(it) }
    }

    private class FunctionEmitter(
        private val fn: IrFunction,
        private val ctx: CodeGenContext,
    ) {
        private val asm = ctx.asm
        private lateinit var alloc: Arm64AllocResult
        private val hasCalls = fn.blocks.any { b -> b.instructions.any { it is Instruction.Call } }
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
            alloc = Arm64Allocator(
                fn,
                availableRegs64 = allocatable64,
                availableRegs32 = allocatable32,
                calleeSaved = calleeSavedSet,
                paramRegs64 = argRegs64,
                paramRegs32 = argRegs32,
                availableFpRegs = allocatableFp,
                fpCalleeSaved = fpCalleeSavedSet,
                fpParamRegs = fpArgRegs,
            ).allocate()
        }

        private fun emitPrologue() {
            val fp = Arm64Register.FP as Arm64Register64
            val lr = Arm64Register.LR as Arm64Register64
            val sp = Arm64Register.SP as Arm64Register64

            // Separate GP and FP callee-saved registers
            val gpCalleeSaved = alloc.usedCalleeRegs
                .filter { it in calleeSavedSet }
                .sortedBy { it.encoding() }
            val fpCalleeSaved = alloc.usedCalleeRegs
                .filter { it in fpCalleeSavedSet }
                .sortedBy { it.encoding() }

            // Total frame size: 16 (FP+LR) + GP callee-saved pairs + FP callee-saved pairs + spill slots
            val gpSaveSize = ((gpCalleeSaved.size + 1) / 2) * 16
            val fpSaveSize = ((fpCalleeSaved.size + 1) / 2) * 16
            val spillSize = alloc.spillSlots * 8
            val frameSize = alignTo16(16 + gpSaveSize + fpSaveSize + spillSize)
            this.stackReserve = frameSize

            // STP X29, X30, [SP, #-frameSize]!
            asm.stpPre(fp, lr, sp, -frameSize)
            // MOV X29, SP
            asm.movSp(fp, sp)

            // Save GP callee-saved registers
            var offset = 16
            var i = 0
            while (i < gpCalleeSaved.size) {
                val r1 = gpCalleeSaved[i] as Arm64Register64
                if (i + 1 < gpCalleeSaved.size) {
                    val r2 = gpCalleeSaved[i + 1] as Arm64Register64
                    asm.stp(r1, r2, fp, offset)
                    i += 2
                } else {
                    asm.str(r1, fp, offset)
                    i++
                }
                offset += 16
            }

            // Save FP callee-saved registers (D8-D15)
            i = 0
            while (i < fpCalleeSaved.size) {
                val r1 = fpCalleeSaved[i] as Arm64VecD
                if (i + 1 < fpCalleeSaved.size) {
                    val r2 = fpCalleeSaved[i + 1] as Arm64VecD
                    asm.fstp(r1, r2, fp, offset)
                    i += 2
                } else {
                    asm.fstr(r1, fp, offset)
                    i++
                }
                offset += 16
            }
        }

        private fun emitEpilogue() {
            val fp = Arm64Register.FP as Arm64Register64
            val lr = Arm64Register.LR as Arm64Register64
            val sp = Arm64Register.SP as Arm64Register64

            val gpCalleeSaved = alloc.usedCalleeRegs
                .filter { it in calleeSavedSet }
                .sortedBy { it.encoding() }
            val fpCalleeSaved = alloc.usedCalleeRegs
                .filter { it in fpCalleeSavedSet }
                .sortedBy { it.encoding() }

            // Restore GP callee-saved registers
            var offset = 16
            var i = 0
            while (i < gpCalleeSaved.size) {
                val r1 = gpCalleeSaved[i] as Arm64Register64
                if (i + 1 < gpCalleeSaved.size) {
                    val r2 = gpCalleeSaved[i + 1] as Arm64Register64
                    asm.ldp(r1, r2, fp, offset)
                    i += 2
                } else {
                    asm.ldr(r1, fp, offset)
                    i++
                }
                offset += 16
            }

            // Restore FP callee-saved registers
            i = 0
            while (i < fpCalleeSaved.size) {
                val r1 = fpCalleeSaved[i] as Arm64VecD
                if (i + 1 < fpCalleeSaved.size) {
                    val r2 = fpCalleeSaved[i + 1] as Arm64VecD
                    asm.fldp(r1, r2, fp, offset)
                    i += 2
                } else {
                    asm.fldr(r1, fp, offset)
                    i++
                }
                offset += 16
            }

            // LDP X29, X30, [SP], #frameSize
            asm.ldpPost(fp, lr, sp, stackReserve)
            asm.ret()
        }

        private fun emitBlocks() {
            for ((blockIdx, block) in fn.blocks.withIndex()) {
                currentBlockLabel = block.label
                asm.label("${fn.name}.${block.label}")
                val nextBlockLabel = fn.blocks.getOrNull(blockIdx + 1)?.label
                for ((instIdx, inst) in block.instructions.withIndex()) {
                    val cur = inst // local val for K2 smart cast
                    if (cur is Instruction.Phi) continue

                    // Fuse ICmp + CondBr
                    if (cur is Instruction.ICmp) {
                        val nextInst = block.instructions.getOrNull(instIdx + 1)
                        if (nextInst is Instruction.CondBr) {
                            val ni = nextInst // local val for K2
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
            // Extract to local val to enable K2 cross-module smart casts
            val i = inst
            when (i) {
                is Instruction.Add -> emitBinOp(i.dest, i.lhs, i.rhs) { rd, rn, rm -> asm.add(rd, rn, rm) }
                is Instruction.Sub -> emitBinOp(i.dest, i.lhs, i.rhs) { rd, rn, rm -> asm.sub(rd, rn, rm) }
                is Instruction.Mul -> emitBinOp(i.dest, i.lhs, i.rhs) { rd, rn, rm -> asm.mul(rd, rn, rm) }
                is Instruction.SDiv -> emitBinOp(i.dest, i.lhs, i.rhs) { rd, rn, rm -> asm.sdiv(rd, rn, rm) }
                is Instruction.UDiv -> emitBinOp(i.dest, i.lhs, i.rhs) { rd, rn, rm -> asm.udiv(rd, rn, rm) }
                is Instruction.And -> emitBinOp(i.dest, i.lhs, i.rhs) { rd, rn, rm -> asm.and_(rd, rn, rm) }
                is Instruction.Or -> emitBinOp(i.dest, i.lhs, i.rhs) { rd, rn, rm -> asm.orr(rd, rn, rm) }
                is Instruction.Xor -> emitBinOp(i.dest, i.lhs, i.rhs) { rd, rn, rm -> asm.eor(rd, rn, rm) }
                is Instruction.Shl -> emitBinOp(i.dest, i.lhs, i.rhs) { rd, rn, rm -> asm.lsl(rd, rn, rm) }
                is Instruction.LShr -> emitBinOp(i.dest, i.lhs, i.rhs) { rd, rn, rm -> asm.lsr(rd, rn, rm) }
                is Instruction.AShr -> emitBinOp(i.dest, i.lhs, i.rhs) { rd, rn, rm -> asm.asr(rd, rn, rm) }

                is Instruction.SRem -> emitRem(i.dest, i.lhs, i.rhs, signed = true)
                is Instruction.URem -> emitRem(i.dest, i.lhs, i.rhs, signed = false)

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

                is Instruction.FAdd -> emitFpBinOp(i.dest, i.lhs, i.rhs) { rd, rn, rm -> asm.fadd(rd, rn, rm) }
                is Instruction.FSub -> emitFpBinOp(i.dest, i.lhs, i.rhs) { rd, rn, rm -> asm.fsub(rd, rn, rm) }
                is Instruction.FMul -> emitFpBinOp(i.dest, i.lhs, i.rhs) { rd, rn, rm -> asm.fmul(rd, rn, rm) }
                is Instruction.FDiv -> emitFpBinOp(i.dest, i.lhs, i.rhs) { rd, rn, rm -> asm.fdiv(rd, rn, rm) }
                is Instruction.FNeg -> emitFpNeg(i)
                is Instruction.FCmp -> emitFCmp(i)
                is Instruction.SIToFP -> emitSIToFP(i)
                is Instruction.UIToFP -> emitUIToFP(i)
                is Instruction.FPToSI -> emitFPToSI(i)
                is Instruction.FPToUI -> emitFPToUI(i)
                is Instruction.FPExt -> emitFPExt(i)
                is Instruction.FPTrunc -> emitFPTrunc(i)

                is Instruction.VAStart -> error("VAStart not yet supported for ARM64")
                is Instruction.VAEnd -> {}
                is Instruction.VAArg -> error("VAArg not yet supported for ARM64")
                is Instruction.VACopy -> error("VACopy not yet supported for ARM64")

                else -> {}
            }
        }

        private fun emitBinOp(
            dest: InstructionRef, lhs: Value, rhs: Value,
            op64: (Arm64Register64, Arm64Register64, Arm64Register64) -> Unit,
        ) {
            val is64 = dest.type.is64Bit()
            if (is64) {
                val lhsReg = getOrLoad64(lhs)
                val rhsReg = getOrLoad64(rhs)
                val destReg = getDest64(dest.name)
                op64(destReg, lhsReg, rhsReg)
                storeTo64(dest.name, destReg)
            } else {
                val lhsReg = getOrLoad32(lhs)
                val rhsReg = getOrLoad32(rhs)
                val destReg = getDest32(dest.name)
                @Suppress("UNCHECKED_CAST")
                (op64 as (Any, Any, Any) -> Unit)(destReg as Arm64Register32, lhsReg as Arm64Register32, rhsReg as Arm64Register32)
                storeTo32(dest.name, destReg as Arm64Register)
            }
        }

        private fun emitRem(dest: InstructionRef, lhs: Value, rhs: Value, signed: Boolean) {
            val is64 = dest.type.is64Bit()
            if (is64) {
                val lhsReg = getOrLoad64(lhs)
                val rhsReg = getOrLoad64(rhs)
                val destReg = getDest64(dest.name)
                val scratchR = scratch64 as Arm64Register64
                // ARM64 has no remainder instruction: rem = lhs - (lhs / rhs) * rhs
                if (signed) asm.sdiv(scratchR, lhsReg, rhsReg) else asm.udiv(scratchR, lhsReg, rhsReg)
                asm.msub(destReg, scratchR, rhsReg, lhsReg)
                storeTo64(dest.name, destReg)
            } else {
                val lhsReg = getOrLoad32(lhs)
                val rhsReg = getOrLoad32(rhs)
                val destReg = getDest32(dest.name)
                val scratchR = scratch32 as Arm64Register32
                if (signed) asm.sdiv(scratchR, lhsReg, rhsReg) else asm.udiv(scratchR, lhsReg, rhsReg)
                @Suppress("UNCHECKED_CAST")
                (asm as Arm64Assembler).msub(destReg as Arm64Register64, scratchR as Arm64Register64, rhsReg as Arm64Register64, lhsReg as Arm64Register64)
                storeTo32(dest.name, destReg as Arm64Register)
            }
        }

        private fun emitICmp(inst: Instruction.ICmp) {
            val lhs = inst.lhs
            val rhs = inst.rhs
            val dest = inst.dest
            val pred = inst.predicate
            val is64 = lhs.type.is64Bit()
            if (is64) {
                asm.cmp(getOrLoad64(lhs), getOrLoad64(rhs))
            } else {
                asm.cmp(getOrLoad32(lhs), getOrLoad32(rhs))
            }
            val cond = mapIrCond(pred)
            val destReg = getDest32(dest.name)
            asm.csinc(destReg as Arm64Register64,
                Arm64Register.XZR as Arm64Register64,
                Arm64Register.XZR as Arm64Register64,
                cond.invert())
            storeTo32(dest.name, destReg as Arm64Register)
        }

        private fun emitFusedCmpBranch(cmp: Instruction.ICmp, br: Instruction.CondBr, nextBlockLabel: String?) {
            val cmpLhs = cmp.lhs
            val cmpRhs = cmp.rhs
            val is64 = cmpLhs.type.is64Bit()
            if (is64) {
                asm.cmp(getOrLoad64(cmpLhs), getOrLoad64(cmpRhs))
            } else {
                asm.cmp(getOrLoad32(cmpLhs), getOrLoad32(cmpRhs))
            }
            val cond = mapIrCond(cmp.predicate)
            val trueTarget = br.trueTarget
            val falseTarget = br.falseTarget
            emitPhiMoves(trueTarget)
            emitPhiMoves(falseTarget)

            if (falseTarget == nextBlockLabel) {
                asm.bCond(cond, "${fn.name}.$trueTarget")
            } else if (trueTarget == nextBlockLabel) {
                asm.bCond(cond.invert(), "${fn.name}.$falseTarget")
            } else {
                asm.bCond(cond, "${fn.name}.$trueTarget")
                asm.b("${fn.name}.$falseTarget")
            }
        }

        private fun emitCondBr(inst: Instruction.CondBr, nextBlockLabel: String?) {
            val condition = inst.condition
            val trueTarget = inst.trueTarget
            val falseTarget = inst.falseTarget
            val condReg = getOrLoad32(condition)
            asm.cmp(condReg, Arm64Register.WZR as Arm64Register32)
            emitPhiMoves(trueTarget)
            emitPhiMoves(falseTarget)

            if (falseTarget == nextBlockLabel) {
                asm.bCond(Arm64Condition.NE, "${fn.name}.$trueTarget")
            } else if (trueTarget == nextBlockLabel) {
                asm.bCond(Arm64Condition.EQ, "${fn.name}.$falseTarget")
            } else {
                asm.bCond(Arm64Condition.NE, "${fn.name}.$trueTarget")
                asm.b("${fn.name}.$falseTarget")
            }
        }

        private fun emitReturn(inst: Instruction.Ret) {
            val retVal = inst.value
            if (retVal != null) {
                if (retVal.type.isFloat()) {
                    val reg = getOrLoadFpD(retVal)
                    if (reg != Arm64Register.D0 as Arm64VecD) {
                        asm.fmov(Arm64Register.D0 as Arm64VecD, reg)
                    }
                } else if (retVal.type.is64Bit()) {
                    val reg = getOrLoad64(retVal)
                    if (reg != Arm64Register.X0 as Arm64Register64) {
                        asm.mov(Arm64Register.X0 as Arm64Register64, reg)
                    }
                } else {
                    val reg = getOrLoad32(retVal)
                    if (reg != Arm64Register.W0 as Arm64Register32) {
                        asm.mov(Arm64Register.W0 as Arm64Register32, reg)
                    }
                }
            }
            emitEpilogue()
        }

        private fun emitBr(inst: Instruction.Br) {
            val target = inst.target
            emitPhiMoves(target)
            asm.b("${fn.name}.$target")
        }

        private fun emitCall(inst: Instruction.Call) {
            val args = inst.args
            val func = inst.function
            val dest = inst.dest

            var gpIdx = 0
            var fpIdx = 0
            for (arg in args) {
                if (arg.type.isFloat()) {
                    if (fpIdx >= fpArgRegs.size) break
                    val src = getOrLoadFpD(arg)
                    val destReg = fpArgRegs[fpIdx] as Arm64VecD
                    if (src != destReg) asm.fmov(destReg, src)
                    fpIdx++
                } else {
                    if (gpIdx >= argRegs64.size) break
                    if (arg.type.is64Bit()) {
                        val src = getOrLoad64(arg)
                        val destReg = argRegs64[gpIdx] as Arm64Register64
                        if (src != destReg) asm.mov(destReg, src)
                    } else {
                        val src = getOrLoad32(arg)
                        val destReg = argRegs32[gpIdx] as Arm64Register32
                        if (src != destReg) asm.mov(destReg, src)
                    }
                    gpIdx++
                }
            }

            val funcName = when (func) {
                is FunctionRef -> func.name
                is GlobalRef -> func.name
                else -> error("Cannot call: ${func::class.simpleName}")
            }
            asm.bl(funcName)

            if (dest == null) return
            if (dest.type.isFloat()) {
                val destReg = getDestFpD(dest.name)
                if (destReg != Arm64Register.D0 as Arm64VecD) {
                    asm.fmov(destReg, Arm64Register.D0 as Arm64VecD)
                }
                storeToFpD(dest.name, destReg)
            } else if (dest.type.is64Bit()) {
                val destReg = getDest64(dest.name)
                if (destReg != Arm64Register.X0 as Arm64Register64) {
                    asm.mov(destReg, Arm64Register.X0 as Arm64Register64)
                }
                storeTo64(dest.name, destReg)
            } else {
                val destReg = getDest32(dest.name)
                if ((destReg as Arm64Register) != Arm64Register.W0) {
                    asm.mov(destReg as Arm64Register32, Arm64Register.W0 as Arm64Register32)
                }
                storeTo32(dest.name, destReg as Arm64Register)
            }
        }

        private fun emitSelect(inst: Instruction.Select) {
            val condition = inst.condition
            val trueVal = inst.trueValue
            val falseVal = inst.falseValue
            val dest = inst.dest
            val condReg = getOrLoad32(condition)
            asm.cmp(condReg, Arm64Register.WZR as Arm64Register32)
            val is64 = dest.type.is64Bit()
            if (is64) {
                val trueReg = getOrLoad64(trueVal)
                val falseReg = getOrLoad64(falseVal)
                val destReg = getDest64(dest.name)
                asm.csel(destReg, trueReg, falseReg, Arm64Condition.NE)
                storeTo64(dest.name, destReg)
            } else {
                val trueReg = getOrLoad32(trueVal)
                val falseReg = getOrLoad32(falseVal)
                val destReg = getDest32(dest.name)
                asm.csel(destReg as Arm64Register32, trueReg as Arm64Register32, falseReg as Arm64Register32, Arm64Condition.NE)
                storeTo32(dest.name, destReg as Arm64Register)
            }
        }

        private fun emitSExt(inst: Instruction.SExt) {
            val src = inst.value
            val dest = inst.dest
            val srcReg = getOrLoad32(src)
            val destReg = getDest64(dest.name)
            asm.sxtw(destReg, srcReg as Arm64Register32)
            storeTo64(dest.name, destReg)
        }

        private fun emitZExt(inst: Instruction.ZExt) {
            val src = inst.value
            val dest = inst.dest
            val srcReg = getOrLoad32(src)
            val destReg = getDest64(dest.name)
            val destW = reg64to32(destReg)
            if ((srcReg as Arm64Register) != destW) {
                asm.mov(destW as Arm64Register32, srcReg as Arm64Register32)
            }
            storeTo64(dest.name, destReg)
        }

        private fun emitTrunc(inst: Instruction.Trunc) {
            val src = inst.operand
            val dest = inst.dest
            val srcReg = getOrLoad64(src)
            val destReg = getDest32(dest.name)
            val srcW = reg64to32(srcReg)
            if ((destReg as Arm64Register) != srcW) {
                asm.mov(destReg as Arm64Register32, srcW as Arm64Register32)
            }
            storeTo32(dest.name, destReg as Arm64Register)
        }

        private fun emitLoad(inst: Instruction.Load) {
            val ptr = inst.ptr
            val dest = inst.dest
            val addrReg = getOrLoad64(ptr)
            val is64 = dest.type.is64Bit()
            if (is64) {
                val destReg = getDest64(dest.name)
                asm.ldr(destReg, addrReg)
                storeTo64(dest.name, destReg)
            } else {
                val destReg = getDest32(dest.name)
                asm.ldr(destReg as Arm64Register32, addrReg)
                storeTo32(dest.name, destReg as Arm64Register)
            }
        }

        private fun emitStore(inst: Instruction.Store) {
            val ptr = inst.ptr
            val storeVal = inst.value
            val addrReg = getOrLoad64(ptr)
            val is64 = storeVal.type.is64Bit()
            if (is64) {
                val valReg = getOrLoad64(storeVal)
                asm.str(valReg, addrReg)
            } else {
                val valReg = getOrLoad32(storeVal)
                asm.str(valReg as Arm64Register32, addrReg)
            }
        }

        private fun emitPhiMoves(targetLabel: String) {
            val moves = phiMoves[currentBlockLabel to targetLabel] ?: return
            for ((dest, value) in moves) {
                if (dest.type.isFloat()) {
                    val src = getOrLoadFpD(value)
                    val destReg = getDestFpD(dest.name)
                    if (src != destReg) asm.fmov(destReg, src)
                    storeToFpD(dest.name, destReg)
                } else if (dest.type.is64Bit()) {
                    val src = getOrLoad64(value)
                    val destReg = getDest64(dest.name)
                    if (src != destReg) asm.mov(destReg, src)
                    storeTo64(dest.name, destReg)
                } else {
                    val src = getOrLoad32(value)
                    val destReg = getDest32(dest.name)
                    if ((src as Arm64Register) != (destReg as Arm64Register)) {
                        asm.mov(destReg as Arm64Register32, src as Arm64Register32)
                    }
                    storeTo32(dest.name, destReg as Arm64Register)
                }
            }
        }

        // ── Register helpers ────────────────────────────────────────

        private fun getOrLoad64(value: Value): Arm64Register64 {
            return when (value) {
                is Constant.I64 -> {
                    val r = scratch64 as Arm64Register64
                    emitLoadImm64(r, value.value)
                    r
                }
                is Constant.I32 -> {
                    val r = scratch64 as Arm64Register64
                    emitLoadImm64(r, value.value.toLong())
                    r
                }
                is Constant.NullPtr -> {
                    val r = scratch64 as Arm64Register64
                    asm.mov(r, Arm64Register.XZR as Arm64Register64)
                    r
                }
                else -> {
                    when (val loc = alloc.locations[value.name]) {
                        is Arm64Location.Reg64 -> loc.reg as Arm64Register64
                        is Arm64Location.Reg32 -> loc.reg as Arm64Register64
                        is Arm64Location.RegFp -> {
                            val r = scratch64 as Arm64Register64
                            asm.fmovToGp64(r, loc.reg as Arm64VecD)
                            r
                        }
                        is Arm64Location.Spill -> {
                            val r = scratch64 as Arm64Register64
                            emitLoadFromFp64(r, loc.offset)
                            r
                        }
                        null -> error("No allocation for ${value.name}")
                    }
                }
            }
        }

        private fun getOrLoad32(value: Value): Arm64Register32 {
            return when (value) {
                is Constant.I32 -> {
                    val r = scratch32 as Arm64Register32
                    emitLoadImm32(r, value.value)
                    r
                }
                is Constant.I64 -> {
                    val r = scratch32 as Arm64Register32
                    emitLoadImm32(r, value.value.toInt())
                    r
                }
                is Constant.I1 -> {
                    val r = scratch32 as Arm64Register32
                    emitLoadImm32(r, if (value.value) 1 else 0)
                    r
                }
                else -> {
                    when (val loc = alloc.locations[value.name]) {
                        is Arm64Location.Reg32 -> loc.reg as Arm64Register32
                        is Arm64Location.Reg64 -> loc.reg as Arm64Register32
                        is Arm64Location.RegFp -> {
                            val r = scratch32 as Arm64Register32
                            asm.fmovToGp32(r, loc.reg as Arm64VecS)
                            r
                        }
                        is Arm64Location.Spill -> {
                            val r = scratch32 as Arm64Register32
                            emitLoadFromFp32(r, loc.offset)
                            r
                        }
                        null -> error("No allocation for ${value.name}")
                    }
                }
            }
        }

        private fun getDest64(name: String): Arm64Register64 {
            return when (val loc = alloc.locations[name]) {
                is Arm64Location.Reg64 -> loc.reg as Arm64Register64
                is Arm64Location.Spill -> scratch64 as Arm64Register64
                else -> scratch64 as Arm64Register64
            }
        }

        private fun getDest32(name: String): Arm64Register32 {
            return when (val loc = alloc.locations[name]) {
                is Arm64Location.Reg32 -> loc.reg as Arm64Register32
                is Arm64Location.Spill -> scratch32 as Arm64Register32
                else -> scratch32 as Arm64Register32
            }
        }

        private fun storeTo64(name: String, reg: Arm64Register64) {
            when (val loc = alloc.locations[name]) {
                is Arm64Location.Reg64 -> {
                    if (loc.reg != reg as Arm64Register) asm.mov(loc.reg as Arm64Register64, reg)
                }
                is Arm64Location.Spill -> emitStoreToFp64(reg, loc.offset)
                else -> {}
            }
        }

        private fun storeTo32(name: String, reg: Arm64Register) {
            when (val loc = alloc.locations[name]) {
                is Arm64Location.Reg32 -> {
                    if (loc.reg != reg) asm.mov(loc.reg as Arm64Register32, reg as Arm64Register32)
                }
                is Arm64Location.Spill -> emitStoreToFp32(reg as Arm64Register32, loc.offset)
                else -> {}
            }
        }

        private fun reg64to32(reg: Arm64Register64): Arm64Register {
            val enc = (reg as Arm64Register).encoding()
            return Arm64Register.byEncoding32(enc)
        }

        // ── FP register helpers ─────────────────────────────────────

        private fun getOrLoadFpD(value: Value): Arm64VecD {
            return when (value) {
                is Constant.F64 -> {
                    val r = scratchFpD as Arm64VecD
                    emitLoadFpConst64(r, value.value)
                    r
                }
                is Constant.F32 -> {
                    val r = scratchFpD as Arm64VecD
                    emitLoadFpConst64(r, value.value.toDouble())
                    r
                }
                else -> {
                    when (val loc = alloc.locations[value.name]) {
                        is Arm64Location.RegFp -> loc.reg as Arm64VecD
                        is Arm64Location.Spill -> {
                            val r = scratchFpD as Arm64VecD
                            emitLoadFromFpFpD(r, loc.offset)
                            r
                        }
                        is Arm64Location.Reg64 -> {
                            val r = scratchFpD as Arm64VecD
                            asm.fmovFromGp64(r, loc.reg as Arm64Register64)
                            r
                        }
                        else -> error("No FP allocation for ${value.name}")
                    }
                }
            }
        }

        private fun getOrLoadFpS(value: Value): Arm64VecS {
            return when (value) {
                is Constant.F32 -> {
                    val r = scratchFpS as Arm64VecS
                    emitLoadFpConst32(r, value.value)
                    r
                }
                else -> {
                    when (val loc = alloc.locations[value.name]) {
                        is Arm64Location.RegFp -> loc.reg as Arm64VecS
                        is Arm64Location.Spill -> {
                            val r = scratchFpS as Arm64VecS
                            emitLoadFromFpFpS(r, loc.offset)
                            r
                        }
                        else -> error("No FP allocation for ${value.name}")
                    }
                }
            }
        }

        private fun getDestFpD(name: String): Arm64VecD {
            return when (val loc = alloc.locations[name]) {
                is Arm64Location.RegFp -> loc.reg as Arm64VecD
                is Arm64Location.Spill -> scratchFpD as Arm64VecD
                else -> scratchFpD as Arm64VecD
            }
        }

        private fun getDestFpS(name: String): Arm64VecS {
            return when (val loc = alloc.locations[name]) {
                is Arm64Location.RegFp -> loc.reg as Arm64VecS
                is Arm64Location.Spill -> scratchFpS as Arm64VecS
                else -> scratchFpS as Arm64VecS
            }
        }

        private fun storeToFpD(name: String, reg: Arm64VecD) {
            when (val loc = alloc.locations[name]) {
                is Arm64Location.RegFp -> {
                    if (loc.reg as Arm64VecD != reg) asm.fmov(loc.reg as Arm64VecD, reg)
                }
                is Arm64Location.Spill -> emitStoreToFpFpD(reg, loc.offset)
                else -> {}
            }
        }

        private fun storeToFpS(name: String, reg: Arm64VecS) {
            when (val loc = alloc.locations[name]) {
                is Arm64Location.RegFp -> {
                    if (loc.reg as Arm64VecS != reg) asm.fmov(loc.reg as Arm64VecS, reg)
                }
                is Arm64Location.Spill -> emitStoreToFpFpS(reg, loc.offset)
                else -> {}
            }
        }

        // ── FP instruction emitters ─────────────────────────────────

        private fun emitFpBinOp(
            dest: InstructionRef, lhs: Value, rhs: Value,
            opD: (Arm64VecD, Arm64VecD, Arm64VecD) -> Unit,
        ) {
            if (dest.type == Type.F64) {
                val lhsReg = getOrLoadFpD(lhs)
                val rhsReg = getOrLoadFpD(rhs)
                val destReg = getDestFpD(dest.name)
                opD(destReg, lhsReg, rhsReg)
                storeToFpD(dest.name, destReg)
            } else {
                val lhsReg = getOrLoadFpS(lhs)
                val rhsReg = getOrLoadFpS(rhs)
                val destReg = getDestFpS(dest.name)
                @Suppress("UNCHECKED_CAST")
                (opD as (Any, Any, Any) -> Unit)(destReg, lhsReg, rhsReg)
                storeToFpS(dest.name, destReg)
            }
        }

        private fun emitFpNeg(inst: Instruction.FNeg) {
            if (inst.dest.type == Type.F64) {
                val src = getOrLoadFpD(inst.operand)
                val dst = getDestFpD(inst.dest.name)
                asm.fneg(dst, src)
                storeToFpD(inst.dest.name, dst)
            } else {
                val src = getOrLoadFpS(inst.operand)
                val dst = getDestFpS(inst.dest.name)
                asm.fneg(dst, src)
                storeToFpS(inst.dest.name, dst)
            }
        }

        private fun emitFCmp(inst: Instruction.FCmp) {
            if (inst.lhs.type == Type.F64) {
                asm.fcmp(getOrLoadFpD(inst.lhs), getOrLoadFpD(inst.rhs))
            } else {
                asm.fcmp(getOrLoadFpS(inst.lhs), getOrLoadFpS(inst.rhs))
            }
            val cond = mapFCmpCond(inst.predicate)
            val destReg = getDest32(inst.dest.name)
            asm.csinc(destReg as Arm64Register64,
                Arm64Register.XZR as Arm64Register64,
                Arm64Register.XZR as Arm64Register64,
                cond.invert())
            storeTo32(inst.dest.name, destReg as Arm64Register)
        }

        private fun emitSIToFP(inst: Instruction.SIToFP) {
            val srcType = inst.value.type
            val dstType = inst.toType
            if (dstType == Type.F64) {
                val dst = getDestFpD(inst.dest.name)
                if (srcType == Type.I64 || srcType == Type.OpaquePointer) {
                    asm.scvtf(dst, getOrLoad64(inst.value))
                } else {
                    asm.scvtfWtoD(dst, getOrLoad32(inst.value))
                }
                storeToFpD(inst.dest.name, dst)
            } else {
                val dst = getDestFpS(inst.dest.name)
                asm.scvtf(dst, getOrLoad32(inst.value))
                storeToFpS(inst.dest.name, dst)
            }
        }

        private fun emitUIToFP(inst: Instruction.UIToFP) {
            val dstType = inst.toType
            if (dstType == Type.F64) {
                val dst = getDestFpD(inst.dest.name)
                asm.ucvtf(dst, getOrLoad64(inst.value))
                storeToFpD(inst.dest.name, dst)
            } else {
                val dst = getDestFpS(inst.dest.name)
                asm.ucvtf(dst, getOrLoad32(inst.value))
                storeToFpS(inst.dest.name, dst)
            }
        }

        private fun emitFPToSI(inst: Instruction.FPToSI) {
            val srcType = inst.value.type
            val dstType = inst.toType
            if (dstType == Type.I64) {
                val dst = getDest64(inst.dest.name)
                asm.fcvtzs(dst, getOrLoadFpD(inst.value))
                storeTo64(inst.dest.name, dst)
            } else {
                val dst = getDest32(inst.dest.name)
                if (srcType == Type.F64) {
                    asm.fcvtzsDtoW(dst, getOrLoadFpD(inst.value))
                } else {
                    asm.fcvtzs(dst, getOrLoadFpS(inst.value))
                }
                storeTo32(inst.dest.name, dst as Arm64Register)
            }
        }

        private fun emitFPToUI(inst: Instruction.FPToUI) {
            val dstType = inst.toType
            if (dstType == Type.I64) {
                val dst = getDest64(inst.dest.name)
                asm.fcvtzu(dst, getOrLoadFpD(inst.value))
                storeTo64(inst.dest.name, dst)
            } else {
                val dst = getDest32(inst.dest.name)
                asm.fcvtzu(dst, getOrLoadFpS(inst.value))
                storeTo32(inst.dest.name, dst as Arm64Register)
            }
        }

        private fun emitFPExt(inst: Instruction.FPExt) {
            val src = getOrLoadFpS(inst.value)
            val dst = getDestFpD(inst.dest.name)
            asm.fcvtStoD(dst, src)
            storeToFpD(inst.dest.name, dst)
        }

        private fun emitFPTrunc(inst: Instruction.FPTrunc) {
            val src = getOrLoadFpD(inst.value)
            val dst = getDestFpS(inst.dest.name)
            asm.fcvtDtoS(dst, src)
            storeToFpS(inst.dest.name, dst)
        }

        private fun mapFCmpCond(pred: FCmpPredicate): Arm64Condition = when (pred) {
            FCmpPredicate.OEQ -> Arm64Condition.EQ
            FCmpPredicate.ONE -> Arm64Condition.MI // LT | GT, but MI works for ordered !=
            FCmpPredicate.OLT -> Arm64Condition.MI
            FCmpPredicate.OLE -> Arm64Condition.LS
            FCmpPredicate.OGT -> Arm64Condition.GT
            FCmpPredicate.OGE -> Arm64Condition.GE
            FCmpPredicate.UEQ -> Arm64Condition.EQ
            FCmpPredicate.UNE -> Arm64Condition.NE
            FCmpPredicate.ULT -> Arm64Condition.LO
            FCmpPredicate.ULE -> Arm64Condition.LS
            FCmpPredicate.UGT -> Arm64Condition.HI
            FCmpPredicate.UGE -> Arm64Condition.HS
            FCmpPredicate.ORD -> Arm64Condition.VC // no unordered
            FCmpPredicate.UNO -> Arm64Condition.VS // unordered
            FCmpPredicate.TRUE -> Arm64Condition.AL
            FCmpPredicate.FALSE -> Arm64Condition.NV
        }

        // ── Immediate loading ───────────────────────────────────────

        private fun emitLoadImm64(rd: Arm64Register64, value: Long) {
            if (value == 0L) {
                asm.mov(rd, Arm64Register.XZR as Arm64Register64)
                return
            }
            if (value in 0..0xFFFF) {
                asm.movz(rd, value.toInt())
                return
            }
            if (value in -0x10000..-1) {
                asm.movn(rd, (value.inv() and 0xFFFF).toInt())
                return
            }
            // Multi-part MOVZ + MOVK
            asm.movz(rd, (value and 0xFFFF).toInt(), shift = 0)
            if ((value ushr 16) and 0xFFFF != 0L) asm.movk(rd, ((value ushr 16) and 0xFFFF).toInt(), shift = 16)
            if ((value ushr 32) and 0xFFFF != 0L) asm.movk(rd, ((value ushr 32) and 0xFFFF).toInt(), shift = 32)
            if ((value ushr 48) and 0xFFFF != 0L) asm.movk(rd, ((value ushr 48) and 0xFFFF).toInt(), shift = 48)
        }

        private fun emitLoadImm32(rd: Arm64Register32, value: Int) {
            if (value == 0) {
                asm.mov(rd, Arm64Register.WZR as Arm64Register32)
                return
            }
            if (value in 0..0xFFFF) {
                asm.movz(rd, value)
                return
            }
            asm.movz(rd, value and 0xFFFF, shift = 0)
            if ((value ushr 16) and 0xFFFF != 0) asm.movk(rd as Arm64Register64, (value ushr 16) and 0xFFFF, shift = 16)
        }

        // ── Stack access ────────────────────────────────────────────

        private fun emitLoadFromFp64(rd: Arm64Register64, offset: Int) {
            val fp = Arm64Register.FP as Arm64Register64
            if (offset in -256..255) {
                asm.ldur(rd, fp, offset)
            } else if (offset >= 0 && offset % 8 == 0 && offset / 8 <= 4095) {
                asm.ldr(rd, fp, offset)
            } else {
                emitLoadImm64(scratch64 as Arm64Register64, offset.toLong())
                asm.add(scratch64 as Arm64Register64, fp, scratch64 as Arm64Register64)
                asm.ldr(rd, scratch64 as Arm64Register64)
            }
        }

        private fun emitStoreToFp64(rs: Arm64Register64, offset: Int) {
            val fp = Arm64Register.FP as Arm64Register64
            if (offset in -256..255) {
                asm.stur(rs, fp, offset)
            } else if (offset >= 0 && offset % 8 == 0 && offset / 8 <= 4095) {
                asm.str(rs, fp, offset)
            } else {
                // Need a second scratch for the address calc since rs might be scratch64
                val addrScratch = Arm64Register.X17 as Arm64Register64
                emitLoadImm64(addrScratch, offset.toLong())
                asm.add(addrScratch, fp, addrScratch)
                asm.str(rs, addrScratch)
            }
        }

        private fun emitLoadFromFp32(rd: Arm64Register32, offset: Int) {
            val fp = Arm64Register.FP as Arm64Register64
            if (offset in -256..255) {
                asm.ldur(rd, fp, offset)
            } else if (offset >= 0 && offset % 4 == 0 && offset / 4 <= 4095) {
                asm.ldr(rd, fp, offset)
            } else {
                val addrScratch = Arm64Register.X17 as Arm64Register64
                emitLoadImm64(addrScratch, offset.toLong())
                asm.add(addrScratch, fp, addrScratch)
                asm.ldr(rd, addrScratch)
            }
        }

        private fun emitStoreToFp32(rs: Arm64Register32, offset: Int) {
            val fp = Arm64Register.FP as Arm64Register64
            if (offset in -256..255) {
                asm.stur(rs, fp, offset)
            } else if (offset >= 0 && offset % 4 == 0 && offset / 4 <= 4095) {
                asm.str(rs, fp, offset)
            } else {
                val addrScratch = Arm64Register.X17 as Arm64Register64
                emitLoadImm64(addrScratch, offset.toLong())
                asm.add(addrScratch, fp, addrScratch)
                asm.str(rs, addrScratch)
            }
        }

        // ── FP stack access ──────────────────────────────────────────

        private fun emitLoadFromFpFpD(rd: Arm64VecD, offset: Int) {
            val fp = Arm64Register.FP as Arm64Register64
            if (offset in -256..255) {
                asm.fldur(rd, fp, offset)
            } else if (offset >= 0 && offset % 8 == 0 && offset / 8 <= 4095) {
                asm.fldr(rd, fp, offset)
            } else {
                val addrScratch = Arm64Register.X17 as Arm64Register64
                emitLoadImm64(addrScratch, offset.toLong())
                asm.add(addrScratch, fp, addrScratch)
                asm.fldr(rd, addrScratch)
            }
        }

        private fun emitStoreToFpFpD(rs: Arm64VecD, offset: Int) {
            val fp = Arm64Register.FP as Arm64Register64
            if (offset in -256..255) {
                asm.fstur(rs, fp, offset)
            } else if (offset >= 0 && offset % 8 == 0 && offset / 8 <= 4095) {
                asm.fstr(rs, fp, offset)
            } else {
                val addrScratch = Arm64Register.X17 as Arm64Register64
                emitLoadImm64(addrScratch, offset.toLong())
                asm.add(addrScratch, fp, addrScratch)
                asm.fstr(rs, addrScratch)
            }
        }

        private fun emitLoadFromFpFpS(rd: Arm64VecS, offset: Int) {
            val fp = Arm64Register.FP as Arm64Register64
            if (offset >= 0 && offset % 4 == 0 && offset / 4 <= 4095) {
                asm.fldr(rd, fp, offset)
            } else {
                val addrScratch = Arm64Register.X17 as Arm64Register64
                emitLoadImm64(addrScratch, offset.toLong())
                asm.add(addrScratch, fp, addrScratch)
                asm.fldr(rd, addrScratch)
            }
        }

        private fun emitStoreToFpFpS(rs: Arm64VecS, offset: Int) {
            val fp = Arm64Register.FP as Arm64Register64
            if (offset >= 0 && offset % 4 == 0 && offset / 4 <= 4095) {
                asm.fstr(rs, fp, offset)
            } else {
                val addrScratch = Arm64Register.X17 as Arm64Register64
                emitLoadImm64(addrScratch, offset.toLong())
                asm.add(addrScratch, fp, addrScratch)
                asm.fstr(rs, addrScratch)
            }
        }

        private fun emitLoadFpConst64(rd: Arm64VecD, value: Double) {
            val bits = java.lang.Double.doubleToRawLongBits(value)
            if (bits == 0L) {
                // FMOV Dd, XZR
                asm.fmovFromGp64(rd, Arm64Register.XZR as Arm64Register64)
                return
            }
            val r = scratch64 as Arm64Register64
            emitLoadImm64(r, bits)
            asm.fmovFromGp64(rd, r)
        }

        private fun emitLoadFpConst32(rd: Arm64VecS, value: Float) {
            val bits = java.lang.Float.floatToRawIntBits(value)
            if (bits == 0) {
                asm.fmovFromGp32(rd, Arm64Register.WZR as Arm64Register32)
                return
            }
            val r = scratch32 as Arm64Register32
            emitLoadImm32(r, bits)
            asm.fmovFromGp32(rd, r)
        }

        // ── Utilities ───────────────────────────────────────────────

        private fun mapIrCond(pred: ICmpPredicate): Arm64Condition = when (pred) {
            ICmpPredicate.EQ -> Arm64Condition.EQ
            ICmpPredicate.NE -> Arm64Condition.NE
            ICmpPredicate.SLT -> Arm64Condition.LT
            ICmpPredicate.SLE -> Arm64Condition.LE
            ICmpPredicate.SGT -> Arm64Condition.GT
            ICmpPredicate.SGE -> Arm64Condition.GE
            ICmpPredicate.ULT -> Arm64Condition.LO
            ICmpPredicate.ULE -> Arm64Condition.LS
            ICmpPredicate.UGT -> Arm64Condition.HI
            ICmpPredicate.UGE -> Arm64Condition.HS
        }

        private fun Type.is64Bit(): Boolean = when (this) {
            Type.I64, Type.F64, Type.OpaquePointer, is Type.Pointer -> true
            else -> false
        }

        private fun Type.isFloat(): Boolean = this == Type.F32 || this == Type.F64

        private fun alignTo16(value: Int): Int = (value + 15) and 15.inv()
    }
}
