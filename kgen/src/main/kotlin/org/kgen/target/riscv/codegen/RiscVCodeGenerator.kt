package org.kgen.target.riscv.codegen

import org.kgen.target.riscv.*
import org.kgen.target.riscv.asm.RiscVAssembler
import org.kgen.binary.*
import org.kgen.binary.elf.ElfObjectWriter
import org.kgen.binary.elf.ElfLinker
import org.kgen.binary.elf.ElfMachine
import org.kgen.ir.*
import org.kgen.ir.instructions.*
import org.kgen.codegen.*
import org.kgen.codegen.alloc.*
import org.kgen.binary.dwarf.*

private fun isGcReferenceType(type: Type): Boolean = when (type) {
    is Type.Reference -> true
    is Type.InteriorRef -> true
    is Type.PinnedRef -> true
    is Type.WeakReference -> true
    else -> false
}

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

    override fun generateCode(module: Module): CompiledCode {
        val ctx = CodeGenContext(module)
        ctx.emitFunctions()
        return ctx.buildCompiledCode()
    }

    fun generateObjectFile(module: Module): ObjectFile {
        val ctx = CodeGenContext(module)
        ctx.emitFunctions()
        return ctx.buildCompiledCode().toObjectFile(
            format = ObjectFormat.ELF,
            arch = Architecture(ArchType.RISCV64),
        )
    }

    private class CodeGenContext(val module: Module) {
        val asm = RiscVAssembler()
        val symbols = mutableListOf<Symbol>()
        val relocations = mutableListOf<Relocation>()
        val stackMaps = mutableListOf<StackMap>()
        val fdeEntries = mutableListOf<FdeEntry>()
        val lsdaTables = mutableListOf<Pair<String, LsdaTable>>()
        val tdataBuilder = TdataBuilder()

        fun emitGlobals() {
            for (global in module.globals) {
                val init = global.initializer ?: continue
                val data = serializeConstant(init)
                val align = global.align ?: alignForType(global.type)
                if (global.threadLocal != null) {
                    tdataBuilder.addGlobal(global.name, data, align)
                }
            }
        }

        fun emitFunctions() {
            emitGlobals()
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

        fun buildCompiledCode(): CompiledCode {
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
            val definedSymbols = symbols.filter { it.kind != SymbolKind.UNDEFINED }
            val codeSymbols = definedSymbols.map { sym ->
                CompiledCode.CodeSymbol(
                    name = sym.name,
                    offset = sym.value,
                    kind = sym.kind,
                    isGlobal = sym.binding != SymbolBinding.LOCAL,
                )
            }
            val tdataSymbols = if (tdataBuilder.hasData()) {
                tdataBuilder.symbols().map { sym ->
                    CompiledCode.CodeSymbol(
                        name = sym.name,
                        offset = 0,
                        kind = SymbolKind.DATA,
                        isGlobal = false,
                        tdataOffset = sym.value,
                    )
                }
            } else emptyList()
            val definedNames = definedSymbols.map { it.name }.toSet() +
                tdataBuilder.symbols().map { it.name }.toSet()
            val externalNames = symbols
                .filter { it.kind == SymbolKind.UNDEFINED }
                .map { it.name }
                .toSet() + relocations
                .map { it.symbol }
                .filter { it !in definedNames }
                .toSet()
            val cie = CieEntry(
                codeAlignFactor = 2, // RISC-V compressed instructions are 2 bytes
                dataAlignFactor = -8,
                returnAddressRegister = 1, // ra (x1)
                initialInstructions = listOf(
                    CfiInstruction.defCfa(2, 0), // SP (x2) + 0
                )
            )
            val ehFrame = if (fdeEntries.isNotEmpty()) {
                EhFrameWriter.write(cie, fdeEntries)
            } else ByteArray(0)
            val ehFrameHdr = if (fdeEntries.isNotEmpty()) {
                EhFrameHdrWriter.write(cie, fdeEntries, 0)
            } else ByteArray(0)
            val exceptTable = if (lsdaTables.isNotEmpty()) {
                val combined = java.io.ByteArrayOutputStream()
                for ((_, table) in lsdaTables) {
                    combined.write(LsdaWriter.write(table))
                }
                combined.toByteArray()
            } else ByteArray(0)

            return CompiledCode(
                textBytes = textBytes,
                symbols = codeSymbols + tdataSymbols,
                relocations = relocations,
                externalSymbols = externalNames,
                stackMaps = stackMaps,
                ehFrameBytes = ehFrame,
                exceptTableBytes = exceptTable,
                ehFrameHdrBytes = ehFrameHdr,
                tdataBytes = if (tdataBuilder.hasData()) tdataBuilder.toByteArray() else ByteArray(0),
                tdataAlign = if (tdataBuilder.hasData()) tdataBuilder.maxAlign() else 1,
            )
        }

        private fun serializeConstant(c: Constant): ByteArray = when (c) {
            is Constant.StringConst -> {
                val bytes = c.value.toByteArray(Charsets.US_ASCII)
                if (c.nullTerminated) bytes + 0 else bytes
            }
            is Constant.I8 -> byteArrayOf(c.value.toByte())
            is Constant.I16 -> {
                val v = c.value.toInt()
                byteArrayOf((v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte())
            }
            is Constant.I32 -> {
                val v = c.value
                byteArrayOf((v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte(),
                    ((v shr 16) and 0xFF).toByte(), ((v shr 24) and 0xFF).toByte())
            }
            is Constant.I64 -> {
                val v = c.value
                ByteArray(8) { i -> ((v shr (i * 8)) and 0xFF).toByte() }
            }
            is Constant.NullPtr -> ByteArray(8)
            is Constant.ZeroInitializer -> ByteArray(sizeOfType(c.type))
            is Constant.ArrayConst -> c.elements.map { serializeConstant(it) }.reduce { a, b -> a + b }
            else -> error("Cannot serialize constant: ${c::class.simpleName}")
        }

        private fun sizeOfType(type: Type): Int = when (type) {
            Type.I8 -> 1; Type.I16 -> 2; Type.I32 -> 4; Type.I64 -> 8
            is Type.Array -> (sizeOfType(type.element) * type.size).toInt()
            else -> 8
        }

        private fun alignForType(type: Type): Int = when (type) {
            Type.I8 -> 1; Type.I16 -> 2; Type.I32 -> 4; Type.I64 -> 8
            is Type.Array -> alignForType(type.element)
            else -> 8
        }
    }

    private class TdataBuilder {
        private data class Entry(val name: String, val data: ByteArray, val align: Int)
        private val entries = mutableListOf<Entry>()

        fun addGlobal(name: String, data: ByteArray, align: Int) {
            entries.add(Entry(name, data, align))
        }

        fun hasData(): Boolean = entries.isNotEmpty()

        fun maxAlign(): Int = entries.maxOfOrNull { it.align } ?: 1

        fun toByteArray(): ByteArray {
            val buf = java.io.ByteArrayOutputStream()
            for (entry in entries) {
                while (buf.size() % entry.align != 0) buf.write(0)
                buf.write(entry.data)
            }
            return buf.toByteArray()
        }

        fun offsetOf(name: String): Int {
            var offset = 0
            for (entry in entries) {
                while (offset % entry.align != 0) offset++
                if (entry.name == name) return offset
                offset += entry.data.size
            }
            return -1
        }

        fun symbols(): List<Symbol> {
            val result = mutableListOf<Symbol>()
            var offset = 0
            for (entry in entries) {
                while (offset % entry.align != 0) offset++
                result.add(Symbol(entry.name, value = offset.toLong(), size = entry.data.size.toLong(),
                    section = ".tdata", binding = SymbolBinding.LOCAL, kind = SymbolKind.DATA))
                offset += entry.data.size
            }
            return result
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

        // FP allocatable: fa0-fa7 (args), ft0-ft7 (temps), fs0-fs11 (callee-saved)
        private val fpAllocatable: List<RiscVFpReg> = listOf(
            F10, F11, F12, F13, F14, F15, F16, F17, // fa0-fa7
            F0, F1, F2, F3, F4, F5, F6, F7,         // ft0-ft7
            F28, F29, F30, F31,                       // ft8-ft11
            F8, F9, F18, F19, F20, F21, F22, F23, F24, F25, F26, F27, // fs0-fs11
        )
        private val fpArgRegs = arrayOf(F10, F11, F12, F13, F14, F15, F16, F17)
        private val fpCalleeSavedSet = setOf<RiscVFpReg>(F8, F9, F18, F19, F20, F21, F22, F23, F24, F25, F26, F27)

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
                    if (inst !is Phi) break
                    for ((value, predLabel) in inst.incoming) {
                        map.getOrPut(predLabel.label to block.label) { mutableListOf() }
                            .add(inst.dest to value)
                    }
                }
            }
            map
        }

        private val stackMapEntries = mutableListOf<StackMapEntry>()
        private val gcRoots = mutableSetOf<String>()
        private val hasGcStrategy: Boolean = fn.gc != null && fn.gc != "none"
        private var funcStartOffset = 0
        private val hasExceptionHandling: Boolean = fn.blocks.any { b ->
            b.instructions.any { it is Invoke || it is LandingPad }
        }
        private val ehCallSites = mutableListOf<EhCallSite>()
        private val ehTypeNames = mutableListOf<String>()

        private data class EhCallSite(
            val callOffset: Int,
            val callLength: Int,
            val landingPadLabel: String,
            val actionIndex: Int,
        )

        fun emit() {
            funcStartOffset = asm.size
            runRegisterAllocator()
            emitPrologue()
            emitBlocks()
            if (hasGcStrategy && stackMapEntries.isNotEmpty()) {
                ctx.stackMaps.add(StackMap(fn.name, stackMapEntries.toList()))
            }
            if (hasExceptionHandling) buildLsda()
            val funcEnd = asm.size
            ctx.fdeEntries.add(buildFde(funcStartOffset, funcEnd - funcStartOffset))
        }

        private fun buildFde(startOffset: Int, length: Int): FdeEntry {
            val cfi = mutableListOf<CfiInstruction>()
            // After addi sp, sp, -frameSize (4 bytes): CFA = SP + frameSize
            cfi.add(CfiInstruction.advanceLoc(2)) // 4 bytes / codeAlignFactor(2) = 2
            cfi.add(CfiInstruction.defCfaOffset(stackReserve))
            // After sd ra, frameSize-8(sp) (4 bytes): RA saved
            cfi.add(CfiInstruction.advanceLoc(2))
            cfi.add(CfiInstruction.offset(1, (stackReserve - 8) / 8)) // ra at CFA - 8
            // After sd fp, frameSize-16(sp) (4 bytes): FP saved
            cfi.add(CfiInstruction.advanceLoc(2))
            cfi.add(CfiInstruction.offset(8, (stackReserve - 16) / 8)) // fp at CFA - 16
            // After addi fp, sp, frameSize (4 bytes): CFA = FP
            cfi.add(CfiInstruction.advanceLoc(2))
            cfi.add(CfiInstruction.defCfaRegister(8)) // FP-based
            return FdeEntry(fn.name, startOffset.toLong(), length.toLong(), cfi)
        }

        private fun runRegisterAllocator() {
            alloc = RiscVAllocator(
                fn,
                availableRegs = allocatable,
                calleeSaved = calleeSavedSet,
                paramRegs = argRegs,
                availableFpRegs = fpAllocatable,
                fpCalleeSaved = fpCalleeSavedSet,
                fpParamRegs = fpArgRegs,
            ).allocate()
        }

        private fun emitPrologue() {
            val calleeSaved = alloc.usedCalleeRegs.sortedBy { it.encoding }
            val calleeFpSaved = alloc.usedCalleeFpRegs.sortedBy { it.encoding }

            // Frame: ra + fp + GP callee-saved + FP callee-saved + spill slots, aligned to 16
            val saveSlots = 2 + calleeSaved.size + calleeFpSaved.size + alloc.spillSlots
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

            // Save GP callee-saved registers
            var offset = frameSize - 24
            for (reg in calleeSaved) {
                asm.sd(reg, RiscVMemory(X2, offset))
                offset -= 8
            }
            // Save FP callee-saved registers
            for (reg in calleeFpSaved) {
                asm.fsd(reg, RiscVMemory(X2, offset))
                offset -= 8
            }
        }

        private fun emitEpilogue() {
            val calleeSaved = alloc.usedCalleeRegs.sortedBy { it.encoding }
            val calleeFpSaved = alloc.usedCalleeFpRegs.sortedBy { it.encoding }

            // Restore GP callee-saved registers
            var offset = stackReserve - 24
            for (reg in calleeSaved) {
                asm.ld(reg, RiscVMemory(X2, offset))
                offset -= 8
            }
            // Restore FP callee-saved registers
            for (reg in calleeFpSaved) {
                asm.fld(reg, RiscVMemory(X2, offset))
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
                    if (cur is Phi) continue

                    // Fuse ICmp + CondBr
                    if (cur is ICmp) {
                        val nextInst = block.instructions.getOrNull(instIdx + 1)
                        if (nextInst is CondBr) {
                            val ni = nextInst
                            if (ni.condition.name == cur.dest.name) {
                                emitFusedCmpBranch(cur, ni, nextBlockLabel)
                                break
                            }
                        }
                    }
                    if (cur is CondBr) {
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
                is Add -> emitBinOp(i.dest, i.lhs, i.rhs) { rd, rs1, rs2 -> asm.add(rd, rs1, rs2) }
                is Sub -> emitBinOp(i.dest, i.lhs, i.rhs) { rd, rs1, rs2 -> asm.sub(rd, rs1, rs2) }
                is Mul -> emitBinOp(i.dest, i.lhs, i.rhs) { rd, rs1, rs2 -> asm.mul(rd, rs1, rs2) }
                is SDiv -> emitBinOp(i.dest, i.lhs, i.rhs) { rd, rs1, rs2 -> asm.div(rd, rs1, rs2) }
                is UDiv -> emitBinOp(i.dest, i.lhs, i.rhs) { rd, rs1, rs2 -> asm.divu(rd, rs1, rs2) }
                is SRem -> emitBinOp(i.dest, i.lhs, i.rhs) { rd, rs1, rs2 -> asm.rem(rd, rs1, rs2) }
                is URem -> emitBinOp(i.dest, i.lhs, i.rhs) { rd, rs1, rs2 -> asm.remu(rd, rs1, rs2) }
                is And -> emitBinOp(i.dest, i.lhs, i.rhs) { rd, rs1, rs2 -> asm.and(rd, rs1, rs2) }
                is Or -> emitBinOp(i.dest, i.lhs, i.rhs) { rd, rs1, rs2 -> asm.or(rd, rs1, rs2) }
                is Xor -> emitBinOp(i.dest, i.lhs, i.rhs) { rd, rs1, rs2 -> asm.xor(rd, rs1, rs2) }
                is Shl -> emitBinOp(i.dest, i.lhs, i.rhs) { rd, rs1, rs2 -> asm.sll(rd, rs1, rs2) }
                is LShr -> emitBinOp(i.dest, i.lhs, i.rhs) { rd, rs1, rs2 -> asm.srl(rd, rs1, rs2) }
                is AShr -> emitBinOp(i.dest, i.lhs, i.rhs) { rd, rs1, rs2 -> asm.sra(rd, rs1, rs2) }

                is ICmp -> emitICmp(i)
                is Ret -> emitReturn(i)
                is Br -> emitBr(i)
                is IndirectBr -> emitIndirectBr(i)
                is Call -> emitCall(i)
                is Select -> emitSelect(i)
                is SExt -> emitSExt(i)
                is ZExt -> emitZExt(i)
                is FTrunc -> emitFTrunc(i)
                is Load -> emitLoad(i)
                is Store -> emitStore(i)
                is Alloca -> {}

                is Neg -> emitNeg(i)
                is Not -> emitNot(i)
                is IntTrunc -> emitIntTrunc(i)

                is FAdd -> emitFpBinOp(i.dest, i.lhs, i.rhs) { rd, rs1, rs2 ->
                    if (i.lhs.type == Type.F32) asm.faddS(rd, rs1, rs2) else asm.faddD(rd, rs1, rs2)
                }
                is FSub -> emitFpBinOp(i.dest, i.lhs, i.rhs) { rd, rs1, rs2 ->
                    if (i.lhs.type == Type.F32) asm.fsubS(rd, rs1, rs2) else asm.fsubD(rd, rs1, rs2)
                }
                is FMul -> emitFpBinOp(i.dest, i.lhs, i.rhs) { rd, rs1, rs2 ->
                    if (i.lhs.type == Type.F32) asm.fmulS(rd, rs1, rs2) else asm.fmulD(rd, rs1, rs2)
                }
                is FDiv -> emitFpBinOp(i.dest, i.lhs, i.rhs) { rd, rs1, rs2 ->
                    if (i.lhs.type == Type.F32) asm.fdivS(rd, rs1, rs2) else asm.fdivD(rd, rs1, rs2)
                }
                is FNeg -> emitFpNeg(i)
                is FCmp -> emitFCmp(i)
                is SIToFP -> emitSIToFP(i)
                is UIToFP -> emitUIToFP(i)
                is FPToSI -> emitFPToSI(i)
                is FPToUI -> emitFPToUI(i)
                is FPExt -> emitFPExt(i)
                is FPTrunc -> emitFPTrunc(i)

                is Switch -> emitSwitch(i)

                is PtrToInt -> emitCopy(i.dest, i.value)
                is IntToPtr -> emitCopy(i.dest, i.value)
                is BitCast -> emitCopy(i.dest, i.value)

                is Sqrt -> emitFpSqrt(i)
                is FAbs -> emitFpAbs(i)
                is FMin -> emitFpBinOp(i.dest, i.lhs, i.rhs) { rd, rs1, rs2 ->
                    if (i.lhs.type == Type.F32) asm.fminS(rd, rs1, rs2) else asm.fminD(rd, rs1, rs2)
                }
                is FMax -> emitFpBinOp(i.dest, i.lhs, i.rhs) { rd, rs1, rs2 ->
                    if (i.lhs.type == Type.F32) asm.fmaxS(rd, rs1, rs2) else asm.fmaxD(rd, rs1, rs2)
                }

                is Unreachable -> asm.ebreak()
                is Trap -> asm.ebreak()
                is DebugTrap -> asm.ebreak()

                is DebugLoc -> {}
                is DebugValue -> {}
                is DebugDeclare -> {}

                is Fence -> asm.fence()

                is GetElementPtr -> emitGetElementPtr(i)
                is ExtractValue -> emitExtractValue(i)
                is InsertValue -> emitInsertValue(i)

                is Ctlz -> emitCtlz(i)
                is Cttz -> emitCttz(i)
                is Ctpop -> emitCtpop(i)
                is BSwap -> emitBSwap(i)

                is GCSafepoint -> emitGCSafepoint()
                is GCRoot -> emitGCRoot(i)

                is Invoke -> emitInvoke(i)
                is CallBr -> emitCallBr(i)
                is LandingPad -> emitLandingPad(i)
                is Resume -> emitResume(i)
                is Throw -> emitThrow(i)

                is CopySign -> emitCopySign(i)
                is SMin -> emitIntMinMax(i.dest, i.lhs, i.rhs, signed = true, isMin = true)
                is SMax -> emitIntMinMax(i.dest, i.lhs, i.rhs, signed = true, isMin = false)
                is UMin -> emitIntMinMax(i.dest, i.lhs, i.rhs, signed = false, isMin = true)
                is UMax -> emitIntMinMax(i.dest, i.lhs, i.rhs, signed = false, isMin = false)
                is Abs -> emitAbs(i)
                is FMA -> emitFMA(i)
                is FRem -> emitFRem(i)
                is BitReverse -> emitBitReverse(i)
                is Rotl -> emitRotl(i)
                is Rotr -> emitRotr(i)
                is MemCpy -> emitMemCpyLoop(i.dst, i.src, i.len)
                is MemSet -> emitMemSetLoop(i.dst, i.value, i.len)
                is MemMove -> emitMemCpyLoop(i.dst, i.src, i.len)
                is Prefetch -> {}
                is StackSave -> {
                    val dest = getDest(i.dest.name)
                    asm.addi(dest, X2, 0) // mv dest, sp
                    storeTo(i.dest.name, dest)
                }
                is StackRestore -> {
                    val src = getOrLoad(i.ptr)
                    asm.addi(X2, src, 0) // mv sp, src
                }

                else -> error("Unsupported IR instruction for RISC-V: ${i::class.simpleName}")
            }
        }

        private fun typeSizeBytes(type: Type): Int = when (type) {
            Type.I1, Type.I8 -> 1
            Type.I16 -> 2
            Type.I32, Type.F32 -> 4
            Type.I64, Type.F64, Type.OpaquePointer, is Type.Pointer -> 8
            is Type.Array -> typeSizeBytes(type.element) * type.size.toInt()
            is Type.Struct -> type.fields.sumOf { typeSizeBytes(it) }
            else -> 8
        }

        private fun structFieldOffset(struct: Type.Struct, fieldIndex: Int): Int {
            var offset = 0
            for (i in 0 until fieldIndex) {
                offset += typeSizeBytes(struct.fields[i])
            }
            return offset
        }

        private fun computeGepOffset(baseType: Type, indices: List<Value>): Int? {
            var offset = 0
            var currentType = baseType
            for ((i, idx) in indices.withIndex()) {
                val constIdx = when (idx) {
                    is Constant.I32 -> idx.value
                    is Constant.I64 -> idx.value.toInt()
                    else -> return null
                }
                if (i == 0) {
                    offset += constIdx * typeSizeBytes(currentType)
                } else {
                    when (currentType) {
                        is Type.Struct -> {
                            offset += structFieldOffset(currentType, constIdx)
                            currentType = currentType.fields[constIdx]
                        }
                        is Type.Array -> {
                            offset += constIdx * typeSizeBytes(currentType.element)
                            currentType = currentType.element
                        }
                        else -> offset += constIdx * typeSizeBytes(currentType)
                    }
                }
            }
            return offset
        }

        private fun aggregateFieldOffset(type: Type, indices: List<Int>): Int {
            var offset = 0
            var currentType = type
            for (idx in indices) {
                when (currentType) {
                    is Type.Struct -> {
                        offset += structFieldOffset(currentType, idx)
                        currentType = currentType.fields[idx]
                    }
                    is Type.Array -> {
                        offset += idx * typeSizeBytes(currentType.element)
                        currentType = currentType.element
                    }
                    else -> error("Cannot index into $currentType")
                }
            }
            return offset
        }

        private fun aggregateFieldType(type: Type, indices: List<Int>): Type {
            var currentType = type
            for (idx in indices) {
                currentType = when (currentType) {
                    is Type.Struct -> currentType.fields[idx]
                    is Type.Array -> currentType.element
                    else -> error("Cannot index into $currentType")
                }
            }
            return currentType
        }

        private fun emitGetElementPtr(inst: GetElementPtr) {
            val destReg = getDest(inst.dest.name)
            val ptrReg = getOrLoad(inst.ptr)
            if (destReg != ptrReg) asm.mv(destReg, ptrReg)

            val constOffset = computeGepOffset(inst.baseType, inst.indices)
            if (constOffset != null && constOffset != 0) {
                asm.addi(destReg, destReg, constOffset)
            } else if (constOffset == null) {
                // Dynamic index
                var currentType = inst.baseType
                for ((i, idx) in inst.indices.withIndex()) {
                    val constIdx = when (idx) {
                        is Constant.I32 -> idx.value
                        is Constant.I64 -> idx.value.toInt()
                        else -> null
                    }
                    if (constIdx != null) {
                        val off = if (i == 0) constIdx * typeSizeBytes(currentType)
                        else when (currentType) {
                            is Type.Struct -> structFieldOffset(currentType as Type.Struct, constIdx).also {
                                currentType = (currentType as Type.Struct).fields[constIdx]
                            }
                            is Type.Array -> (constIdx * typeSizeBytes((currentType as Type.Array).element)).also {
                                currentType = (currentType as Type.Array).element
                            }
                            else -> constIdx * typeSizeBytes(currentType)
                        }
                        if (off != 0) asm.addi(destReg, destReg, off)
                    } else {
                        val elemSize = if (i == 0) typeSizeBytes(currentType)
                        else when (currentType) {
                            is Type.Array -> typeSizeBytes((currentType as Type.Array).element).also {
                                currentType = (currentType as Type.Array).element
                            }
                            else -> typeSizeBytes(currentType)
                        }
                        val scratch2 = X6
                        val idxReg = getOrLoad(idx)
                        if (elemSize == 1) {
                            asm.add(destReg, destReg, idxReg)
                        } else {
                            emitLoadImm(scratch2, elemSize.toLong())
                            asm.mul(scratch2, idxReg, scratch2)
                            asm.add(destReg, destReg, scratch2)
                        }
                    }
                }
            }
            storeTo(inst.dest.name, destReg)
        }

        private fun emitExtractValue(inst: ExtractValue) {
            val fieldOffset = aggregateFieldOffset(inst.aggregate.type, inst.indices)
            val aggLoc = alloc.locations[inst.aggregate.name]
            val baseOffset = when (aggLoc) {
                is RiscVLocation.Spill -> aggLoc.offset
                else -> error("ExtractValue: aggregate ${inst.aggregate.name} must be spilled")
            }
            val memOffset = baseOffset + fieldOffset
            val destReg = getDest(inst.dest.name)
            emitLoadFromFp(destReg, memOffset)
            storeTo(inst.dest.name, destReg)
        }

        private fun emitInsertValue(inst: InsertValue) {
            val fieldOffset = aggregateFieldOffset(inst.aggregate.type, inst.indices)
            val aggLoc = alloc.locations[inst.aggregate.name]
            val destLoc = alloc.locations[inst.dest.name]

            if (aggLoc is RiscVLocation.Spill && destLoc is RiscVLocation.Spill && aggLoc.offset != destLoc.offset) {
                val aggSize = typeSizeBytes(inst.aggregate.type)
                val scratch2 = X6
                for (off in 0 until aggSize step 8) {
                    emitLoadFromFp(scratch2, aggLoc.offset + off)
                    emitStoreToFp(scratch2, destLoc.offset + off)
                }
            }

            val baseOffset = when (destLoc) {
                is RiscVLocation.Spill -> destLoc.offset
                else -> error("InsertValue: dest ${inst.dest.name} must be spilled")
            }
            val memOffset = baseOffset + fieldOffset
            val valReg = getOrLoad(inst.element)
            emitStoreToFp(valReg, memOffset)
        }

        private fun emitCtlz(inst: Ctlz) {
            // Software CLZ using binary search (branchless)
            // result = 0; if top half zero, shift up and add to result
            val src = getOrLoad(inst.operand)
            val destReg = getDest(inst.dest.name)
            val scratch2 = X6
            val is32 = inst.operand.type == Type.I32

            // Copy src to scratch for manipulation
            if (is32) {
                // Zero-extend 32-bit to 64-bit, then do 64-bit CLZ and subtract 32
                asm.slli(scratch, src, 32)
                asm.srli(scratch, scratch, 32)
            } else {
                if (scratch != src) asm.mv(scratch, src)
            }

            // Binary search CLZ for 64-bit:
            // n = 0
            // if (x <= 0x00000000FFFFFFFF) { n += 32; x <<= 32; }
            // if (x <= 0x0000FFFFFFFFFFFF) { n += 16; x <<= 16; }
            // if (x <= 0x00FFFFFFFFFFFFFF) { n +=  8; x <<=  8; }
            // if (x <= 0x0FFFFFFFFFFFFFFF) { n +=  4; x <<=  4; }
            // if (x <= 0x3FFFFFFFFFFFFFFF) { n +=  2; x <<=  2; }
            // if (x <= 0x7FFFFFFFFFFFFFFF) { n +=  1; }
            // if (x == 0) n = 64
            // This requires branches. Use a simpler approach with RISC-V branch instructions.

            // Use label-based approach (the assembler supports labels)
            val prefix = "clz_${asm.size}"
            asm.li(destReg, if (is32) 32 else 64)
            asm.beq(scratch, X0, "${prefix}_done") // if x == 0, result is 32/64

            asm.li(destReg, 0)
            if (!is32) {
                // Check upper 32 bits
                asm.srli(scratch2, scratch, 32)
                asm.bne(scratch2, X0, "${prefix}_16")
                asm.addi(destReg, destReg, 32)
                asm.slli(scratch, scratch, 32)
                asm.label("${prefix}_16")
            }
            // Check upper 16 bits of remaining
            asm.srli(scratch2, scratch, if (is32) 16 else 48)
            asm.bne(scratch2, X0, "${prefix}_8")
            asm.addi(destReg, destReg, 16)
            asm.slli(scratch, scratch, 16)
            asm.label("${prefix}_8")

            asm.srli(scratch2, scratch, if (is32) 24 else 56)
            asm.bne(scratch2, X0, "${prefix}_4")
            asm.addi(destReg, destReg, 8)
            asm.slli(scratch, scratch, 8)
            asm.label("${prefix}_4")

            asm.srli(scratch2, scratch, if (is32) 28 else 60)
            asm.bne(scratch2, X0, "${prefix}_2")
            asm.addi(destReg, destReg, 4)
            asm.slli(scratch, scratch, 4)
            asm.label("${prefix}_2")

            asm.srli(scratch2, scratch, if (is32) 30 else 62)
            asm.bne(scratch2, X0, "${prefix}_1")
            asm.addi(destReg, destReg, 2)
            asm.slli(scratch, scratch, 2)
            asm.label("${prefix}_1")

            asm.srli(scratch2, scratch, if (is32) 31 else 63)
            asm.bne(scratch2, X0, "${prefix}_done")
            asm.addi(destReg, destReg, 1)

            asm.label("${prefix}_done")
            storeTo(inst.dest.name, destReg)
        }

        private fun emitCttz(inst: Cttz) {
            // CTZ = popcount(~x & (x - 1))
            // Or use binary search similar to CLZ but from LSB
            val src = getOrLoad(inst.operand)
            val destReg = getDest(inst.dest.name)
            val scratch2 = X6
            val is32 = inst.operand.type == Type.I32

            if (scratch != src) asm.mv(scratch, src)

            val prefix = "ctz_${asm.size}"
            val bits = if (is32) 32 else 64
            asm.li(destReg, bits)
            asm.beq(scratch, X0, "${prefix}_done")

            asm.li(destReg, 0)
            if (!is32) {
                asm.andi(scratch2, scratch, -1) // lower 32 bits — use slli/srli
                asm.slli(scratch2, scratch, 32)
                asm.srli(scratch2, scratch2, 32)
                asm.bne(scratch2, X0, "${prefix}_16")
                asm.addi(destReg, destReg, 32)
                asm.srli(scratch, scratch, 32)
                asm.label("${prefix}_16")
            }
            // Check lower 16 bits
            asm.slli(scratch2, scratch, if (is32) 16 else 48)
            asm.srli(scratch2, scratch2, if (is32) 16 else 48)
            asm.bne(scratch2, X0, "${prefix}_8")
            asm.addi(destReg, destReg, 16)
            asm.srli(scratch, scratch, 16)
            asm.label("${prefix}_8")

            asm.andi(scratch2, scratch, 0xFF)
            asm.bne(scratch2, X0, "${prefix}_4")
            asm.addi(destReg, destReg, 8)
            asm.srli(scratch, scratch, 8)
            asm.label("${prefix}_4")

            asm.andi(scratch2, scratch, 0x0F)
            asm.bne(scratch2, X0, "${prefix}_2")
            asm.addi(destReg, destReg, 4)
            asm.srli(scratch, scratch, 4)
            asm.label("${prefix}_2")

            asm.andi(scratch2, scratch, 0x03)
            asm.bne(scratch2, X0, "${prefix}_1")
            asm.addi(destReg, destReg, 2)
            asm.srli(scratch, scratch, 2)
            asm.label("${prefix}_1")

            asm.andi(scratch2, scratch, 0x01)
            asm.bne(scratch2, X0, "${prefix}_done")
            asm.addi(destReg, destReg, 1)

            asm.label("${prefix}_done")
            storeTo(inst.dest.name, destReg)
        }

        private fun emitCtpop(inst: Ctpop) {
            // Software popcount using the parallel counting algorithm
            // For RISC-V without Zbb, use Kernighan's bit-counting trick with a loop
            val src = getOrLoad(inst.operand)
            val destReg = getDest(inst.dest.name)

            if (scratch != src) asm.mv(scratch, src)
            val prefix = "popcnt_${asm.size}"

            // count = 0; while (x) { x &= x - 1; count++; }
            asm.li(destReg, 0)
            asm.label("${prefix}_loop")
            asm.beq(scratch, X0, "${prefix}_done")
            asm.addi(destReg, destReg, 1)
            asm.addi(X6, scratch, -1)  // x - 1
            asm.and(scratch, scratch, X6)  // x &= x - 1
            asm.jal(X0, "${prefix}_loop") // j loop (using jal x0 = jump)
            asm.label("${prefix}_done")
            storeTo(inst.dest.name, destReg)
        }

        private fun emitBSwap(inst: BSwap) {
            // Byte-swap using shifts and masks
            val src = getOrLoad(inst.operand)
            val destReg = getDest(inst.dest.name)
            val scratch2 = X6
            val is32 = inst.operand.type == Type.I32

            if (is32) {
                // 32-bit byte swap: abcd -> dcba
                // Swap bytes using shift and OR
                // byte0 = (x >> 24) & 0xFF
                // byte1 = (x >> 8) & 0xFF00
                // byte2 = (x << 8) & 0xFF0000
                // byte3 = (x << 24) & 0xFF000000
                asm.srli(destReg, src, 24)          // byte 3 -> byte 0
                asm.slli(scratch, src, 24)           // byte 0 -> byte 3
                asm.or(destReg, destReg, scratch)
                asm.srli(scratch, src, 8)
                asm.andi(scratch, scratch, 0xFF)     // can only do 12-bit imm
                asm.slli(scratch, scratch, 8)         // byte 2 -> byte 1 position (but wrong)
                // Actually this is getting complex with 12-bit immediates.
                // Use a simpler sequence: store to memory, load reversed
                // For correctness, just reverse byte by byte using shifts
                asm.srli(scratch2, src, 8)
                asm.andi(scratch2, scratch2, 0xFF)
                asm.slli(scratch2, scratch2, 16)
                asm.or(destReg, destReg, scratch2)
                asm.slli(scratch2, src, 8)
                asm.srli(scratch2, scratch2, 24)     // isolate byte 1
                asm.slli(scratch2, scratch2, 8)
                asm.or(destReg, destReg, scratch2)
            } else {
                // 64-bit: reverse all 8 bytes
                // Use pairs of shifts and ORs
                // Start with reversing 32-bit halves, then reverse bytes within each half
                // Simpler: just do it byte by byte
                asm.li(destReg, 0)
                for (byteIdx in 0 until 8) {
                    asm.srli(scratch, src, byteIdx * 8)
                    asm.andi(scratch, scratch, 0xFF)
                    asm.slli(scratch, scratch, (7 - byteIdx) * 8)
                    asm.or(destReg, destReg, scratch)
                }
            }
            storeTo(inst.dest.name, destReg)
        }


        private fun emitGCSafepoint() {
            if (!hasGcStrategy) return
            val offset = (asm.size - funcStartOffset).toLong()
            val locations = collectGcLocations()
            stackMapEntries.add(StackMapEntry(offset, locations))

            // Emit call to safepoint poll function
            asm.call("kgen_safepoint_poll")
        }

        private fun emitGCRoot(inst: GCRoot) {
            gcRoots.add(inst.ptr.name)
        }

        private fun collectGcLocations(): List<StackMapLocation> {
            val locations = mutableListOf<StackMapLocation>()
            for ((name, loc) in alloc.locations) {
                val type = findValueType(name) ?: continue
                if (!isGcReferenceType(type)) continue
                when (loc) {
                    is RiscVLocation.Reg -> locations.add(StackMapLocation.Register(loc.reg.encoding))
                    is RiscVLocation.RegFp -> {} // FP regs don't hold GC references
                    is RiscVLocation.Spill -> locations.add(StackMapLocation.Stack(loc.offset))
                }
            }
            for (rootName in gcRoots) {
                val loc = alloc.locations[rootName]
                if (loc is RiscVLocation.Spill) {
                    locations.add(StackMapLocation.Stack(loc.offset))
                }
            }
            return locations
        }

        private fun findValueType(name: String): Type? {
            for (param in fn.params) {
                if (param.name == name) return param.type
            }
            for (block in fn.blocks) {
                for (inst in block.instructions) {
                    val result = inst.result
                    if (result != null && result.name == name) return result.type
                }
            }
            return null
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

        private fun emitICmp(inst: ICmp) {
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

        private fun emitFusedCmpBranch(cmp: ICmp, br: CondBr, nextBlockLabel: String?) {
            val lhsReg = getOrLoad(cmp.lhs)
            val rhsReg = getOrLoad(cmp.rhs)
            val trueTarget = br.trueTarget.label
            val falseTarget = br.falseTarget.label
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

        private fun emitCondBr(inst: CondBr, nextBlockLabel: String?) {
            val condReg = getOrLoad(inst.condition)
            val trueTarget = inst.trueTarget.label
            val falseTarget = inst.falseTarget.label
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

        private fun emitReturn(inst: Ret) {
            val retVal = inst.value
            if (retVal != null) {
                val reg = getOrLoad(retVal)
                if (reg != X10) asm.mv(X10, reg)
            }
            emitEpilogue()
        }

        private fun emitBr(inst: Br) {
            emitPhiMoves(inst.target.label)
            asm.j("${fn.name}.${inst.target.label}")
        }

        private fun emitIndirectBr(inst: IndirectBr) {
            val addrReg = getOrLoad(inst.address)
            asm.jalr(X0, addrReg, 0)
        }

        private fun emitSwitch(inst: Switch) {
            val valReg = getOrLoad(inst.value)
            for ((caseVal, target) in inst.cases) {
                val caseReg = getOrLoad(caseVal)
                asm.beq(valReg, caseReg, "${fn.name}.${target.label}")
            }
            asm.j("${fn.name}.${inst.defaultTarget.label}")
        }

        private fun emitCall(inst: Call) {
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

        private fun emitInvoke(inst: Invoke) {
            val funcName = when (val f = inst.function) {
                is FunctionRef -> f.name
                is GlobalRef -> f.name
                else -> error("Unsupported invoke target: $f")
            }

            // Load arguments (same as emitCall)
            for ((idx, arg) in inst.args.withIndex()) {
                if (idx >= argRegs.size) break
                val src = getOrLoad(arg)
                if (src != argRegs[idx]) asm.mv(argRegs[idx], src)
            }

            // Record call site
            val callStart = asm.size - funcStartOffset
            asm.call(funcName)
            val callEnd = asm.size - funcStartOffset

            val actionIndex = resolveActionIndex(inst.unwindDest.label)
            ehCallSites.add(EhCallSite(
                callOffset = callStart,
                callLength = callEnd - callStart,
                landingPadLabel = "${fn.name}.lp.${inst.unwindDest.label}",
                actionIndex = actionIndex,
            ))

            // Store return value
            val dest = inst.dest
            if (dest != null) {
                val destReg = getDest(dest.name)
                if (destReg != X10) asm.mv(destReg, X10)
                storeTo(dest.name, destReg)
            }

            // Branch to normal destination
            emitPhiMoves(inst.normalDest.label)
            asm.j("${fn.name}.${inst.normalDest.label}")
        }

        private fun emitCallBr(inst: CallBr) {
            // CallBr: call a function that may branch to indirect destinations (asm goto).
            val funcName = when (val f = inst.function) {
                is FunctionRef -> f.name
                is GlobalRef -> f.name
                else -> error("Unsupported callbr target: $f")
            }

            // Load arguments
            for ((idx, arg) in inst.args.withIndex()) {
                if (idx >= argRegs.size) { break }
                val src = getOrLoad(arg)
                if (src != argRegs[idx]) { asm.mv(argRegs[idx], src) }
            }

            // Emit the call
            asm.call(funcName)

            // Store return value
            val dest = inst.dest
            if (dest != null) {
                val destReg = getDest(dest.name)
                if (destReg != X10) { asm.mv(destReg, X10) }
                storeTo(dest.name, destReg)
            }

            // Fall through to fallthrough block
            emitPhiMoves(inst.fallthrough.label)
            asm.j("${fn.name}.${inst.fallthrough.label}")
        }

        private fun emitLandingPad(inst: LandingPad) {
            // Landing pad entry point — unwinder transfers control here.
            // RISC-V Itanium ABI: A0 (X10) = exception pointer, A1 (X11) = selector
            val labelName = "${fn.name}.lp.${currentBlockLabel}"
            asm.label(labelName)

            val dest = inst.dest
            val loc = alloc.locations[dest.name]
            if (loc != null) {
                when (loc) {
                    is RiscVLocation.Reg -> {
                        if (loc.reg != X10) asm.mv(loc.reg, X10)
                    }
                    is RiscVLocation.Spill -> {
                        emitStoreToFp(X10, loc.offset)
                    }
                    else -> {}
                }
            }
        }

        private fun emitResume(inst: Resume) {
            val src = getOrLoad(inst.value)
            if (src != X10) asm.mv(X10, src)
            asm.call("_Unwind_Resume")
            asm.ebreak()
        }

        private fun emitThrow(inst: Throw) {
            val src = getOrLoad(inst.exception)
            if (src != X10) asm.mv(X10, src)
            asm.call("kgen_throw")
            asm.ebreak()
        }

        private fun resolveActionIndex(unwindLabel: String): Int {
            val unwindBlock = fn.blocks.firstOrNull { it.label == unwindLabel } ?: return 0
            val lp = unwindBlock.instructions.firstOrNull { it is LandingPad } as? LandingPad
                ?: return 0
            if (lp.cleanup && lp.clauses.isEmpty()) return 0
            val catchClause = lp.clauses.filterIsInstance<LandingPadClause.Catch>().firstOrNull() ?: return 0
            val typeName = when (val v = catchClause.type) {
                is GlobalRef -> v.name
                else -> return 0
            }
            val idx = ehTypeNames.indexOf(typeName)
            return if (idx >= 0) idx + 1 else { ehTypeNames.add(typeName); ehTypeNames.size }
        }

        private fun buildLsda() {
            val callSiteEntries = ehCallSites.map { cs ->
                val lpOffset = asm.labelOffset(cs.landingPadLabel)
                val resolved = if (lpOffset >= 0) lpOffset - funcStartOffset else 0
                CallSiteEntry(cs.callOffset, cs.callLength, resolved, cs.actionIndex)
            }
            val actions = ehTypeNames.mapIndexed { idx, _ ->
                ActionEntry(typeIndex = idx + 1, nextAction = 0)
            }
            val lsda = LsdaTable(callSiteEntries, actions, ehTypeNames)
            ctx.lsdaTables.add(fn.name to lsda)
        }

        private fun emitSelect(inst: Select) {
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

        private fun emitSExt(inst: SExt) {
            val src = getOrLoad(inst.value)
            val destReg = getDest(inst.dest.name)
            // Sign-extend 32-bit to 64-bit: addiw dest, src, 0
            asm.addiw(destReg, src, 0)
            storeTo(inst.dest.name, destReg)
        }

        private fun emitZExt(inst: ZExt) {
            val src = getOrLoad(inst.value)
            val destReg = getDest(inst.dest.name)
            // Zero-extend 32 to 64: slli + srli by 32
            asm.slli(destReg, src, 32)
            asm.srli(destReg, destReg, 32)
            storeTo(inst.dest.name, destReg)
        }

        private fun emitFTrunc(inst: FTrunc) {
            val src = getOrLoad(inst.operand)
            val destReg = getDest(inst.dest.name)
            // Truncate to 32-bit: just copy (upper bits ignored by word operations)
            if (destReg != src) asm.mv(destReg, src)
            storeTo(inst.dest.name, destReg)
        }

        private fun isTlsGlobal(name: String): Boolean =
            ctx.module.globals.any { it.name == name && it.threadLocal != null }

        private fun loadTlsAddress(name: String, dest: RiscVGpReg): RiscVGpReg {
            // lui dest, %tprel_hi(sym)
            asm.lui(dest, 0)
            ctx.relocations.add(Relocation(
                offset = (asm.size - 4).toLong(), symbol = name,
                type = RelocationType.RiscV.TPREL_HI20, addend = 0, section = ".text"))
            // add dest, dest, tp  (with TPREL_ADD relocation for linker relaxation)
            asm.add(dest, dest, X4)
            ctx.relocations.add(Relocation(
                offset = (asm.size - 4).toLong(), symbol = name,
                type = RelocationType.RiscV.TPREL_ADD, addend = 0, section = ".text"))
            // addi dest, dest, %tprel_lo(sym)
            asm.addi(dest, dest, 0)
            ctx.relocations.add(Relocation(
                offset = (asm.size - 4).toLong(), symbol = name,
                type = RelocationType.RiscV.TPREL_LO12_I, addend = 0, section = ".text"))
            return dest
        }

        private fun emitLoad(inst: Load) {
            val addrReg = if (inst.ptr is GlobalRef && isTlsGlobal((inst.ptr as GlobalRef).name)) {
                loadTlsAddress((inst.ptr as GlobalRef).name, scratch)
            } else {
                getOrLoad(inst.ptr)
            }
            val destReg = getDest(inst.dest.name)
            val is64 = inst.dest.type.is64Bit()
            if (is64) {
                asm.ld(destReg, RiscVMemory(addrReg, 0))
            } else {
                asm.lw(destReg, RiscVMemory(addrReg, 0))
            }
            storeTo(inst.dest.name, destReg)
        }

        private fun emitStore(inst: Store) {
            val addrReg = if (inst.ptr is GlobalRef && isTlsGlobal((inst.ptr as GlobalRef).name)) {
                loadTlsAddress((inst.ptr as GlobalRef).name, scratch)
            } else {
                getOrLoad(inst.ptr)
            }
            val valReg = getOrLoad(inst.value)
            val is64 = inst.value.type.is64Bit()
            if (is64) {
                asm.sd(valReg, RiscVMemory(addrReg, 0))
            } else {
                asm.sw(valReg, RiscVMemory(addrReg, 0))
            }
        }

        // ── FP helpers ─────────────────────────────────────────────
        // Strategy: FP values live in GP registers between operations.
        // For FP ops, we move GP→FP, operate, move FP→GP.
        // F0, F1 are FP scratch registers (ft0, ft1 — caller-saved).

        private fun moveToFp(fpReg: RiscVFpReg, value: Value) {
            when (value) {
                is Constant.F64 -> {
                    val bits = java.lang.Double.doubleToRawLongBits(value.value)
                    emitLoadImm(scratch, bits)
                    asm.fmvDX(fpReg, scratch)
                }
                is Constant.F32 -> {
                    val bits = java.lang.Float.floatToRawIntBits(value.value).toLong()
                    emitLoadImm(scratch, bits)
                    asm.fmvWX(fpReg, scratch)
                }
                else -> {
                    val gpReg = getOrLoad(value)
                    if (value.type == Type.F32) asm.fmvWX(fpReg, gpReg) else asm.fmvDX(fpReg, gpReg)
                }
            }
        }

        private fun emitFpBinOp(
            dest: InstructionRef, lhs: Value, rhs: Value,
            op: (RiscVFpReg, RiscVFpReg, RiscVFpReg) -> Unit,
        ) {
            moveToFp(F0, lhs)
            moveToFp(F1, rhs)
            op(F0, F0, F1)
            val destReg = getDest(dest.name)
            if (lhs.type == Type.F32) asm.fmvXW(destReg, F0) else asm.fmvXD(destReg, F0)
            storeTo(dest.name, destReg)
        }

        private fun emitFpNeg(inst: FNeg) {
            moveToFp(F0, inst.operand)
            if (inst.operand.type == Type.F32) asm.fnegS(F0, F0) else asm.fnegD(F0, F0)
            val destReg = getDest(inst.dest.name)
            if (inst.operand.type == Type.F32) asm.fmvXW(destReg, F0) else asm.fmvXD(destReg, F0)
            storeTo(inst.dest.name, destReg)
        }

        private fun emitFCmp(inst: FCmp) {
            moveToFp(F0, inst.lhs)
            moveToFp(F1, inst.rhs)
            val destReg = getDest(inst.dest.name)
            val isF32 = inst.lhs.type == Type.F32
            when (inst.predicate) {
                FCmpPredicate.OEQ, FCmpPredicate.UEQ -> {
                    if (isF32) asm.feqS(destReg, F0, F1) else asm.feqD(destReg, F0, F1)
                }
                FCmpPredicate.ONE, FCmpPredicate.UNE -> {
                    if (isF32) asm.feqS(destReg, F0, F1) else asm.feqD(destReg, F0, F1)
                    asm.xori(destReg, destReg, 1)
                }
                FCmpPredicate.OLT, FCmpPredicate.ULT -> {
                    if (isF32) asm.fltS(destReg, F0, F1) else asm.fltD(destReg, F0, F1)
                }
                FCmpPredicate.OLE, FCmpPredicate.ULE -> {
                    if (isF32) asm.fleS(destReg, F0, F1) else asm.fleD(destReg, F0, F1)
                }
                FCmpPredicate.OGT, FCmpPredicate.UGT -> {
                    if (isF32) asm.fltS(destReg, F1, F0) else asm.fltD(destReg, F1, F0)
                }
                FCmpPredicate.OGE, FCmpPredicate.UGE -> {
                    if (isF32) asm.fleS(destReg, F1, F0) else asm.fleD(destReg, F1, F0)
                }
                FCmpPredicate.FALSE -> asm.li(destReg, 0)
                FCmpPredicate.TRUE -> asm.li(destReg, 1)
                FCmpPredicate.ORD -> {
                    // ordered: both not NaN — (a == a) & (b == b)
                    if (isF32) asm.feqS(destReg, F0, F0) else asm.feqD(destReg, F0, F0)
                    val tmp = X6 // t1 scratch
                    if (isF32) asm.feqS(tmp, F1, F1) else asm.feqD(tmp, F1, F1)
                    asm.and(destReg, destReg, tmp)
                }
                FCmpPredicate.UNO -> {
                    // unordered: either NaN — !(a == a) | !(b == b)
                    if (isF32) asm.feqS(destReg, F0, F0) else asm.feqD(destReg, F0, F0)
                    asm.xori(destReg, destReg, 1)
                    val tmp = X6
                    if (isF32) asm.feqS(tmp, F1, F1) else asm.feqD(tmp, F1, F1)
                    asm.xori(tmp, tmp, 1)
                    asm.or(destReg, destReg, tmp)
                }
            }
            storeTo(inst.dest.name, destReg)
        }

        private fun emitSIToFP(inst: SIToFP) {
            val src = getOrLoad(inst.value)
            val destReg = getDest(inst.dest.name)
            when {
                inst.value.type == Type.I64 && inst.toType == Type.F64 -> { asm.fcvtDL(F0, src); asm.fmvXD(destReg, F0) }
                inst.value.type == Type.I64 && inst.toType == Type.F32 -> { asm.fcvtSL(F0, src); asm.fmvXW(destReg, F0) }
                inst.toType == Type.F64 -> { asm.fcvtDW(F0, src); asm.fmvXD(destReg, F0) }
                else -> { asm.fcvtSW(F0, src); asm.fmvXW(destReg, F0) }
            }
            storeTo(inst.dest.name, destReg)
        }

        private fun emitUIToFP(inst: UIToFP) {
            val src = getOrLoad(inst.value)
            val destReg = getDest(inst.dest.name)
            when {
                inst.value.type == Type.I64 && inst.toType == Type.F64 -> { asm.fcvtDLu(F0, src); asm.fmvXD(destReg, F0) }
                inst.value.type == Type.I64 && inst.toType == Type.F32 -> { asm.fcvtSLu(F0, src); asm.fmvXW(destReg, F0) }
                inst.toType == Type.F64 -> { asm.fcvtDWu(F0, src); asm.fmvXD(destReg, F0) }
                else -> { asm.fcvtSWu(F0, src); asm.fmvXW(destReg, F0) }
            }
            storeTo(inst.dest.name, destReg)
        }

        private fun emitFPToSI(inst: FPToSI) {
            moveToFp(F0, inst.value)
            val destReg = getDest(inst.dest.name)
            when {
                inst.value.type == Type.F64 && inst.toType == Type.I64 -> asm.fcvtLD(destReg, F0)
                inst.value.type == Type.F32 && inst.toType == Type.I64 -> asm.fcvtLS(destReg, F0)
                inst.value.type == Type.F64 -> asm.fcvtWD(destReg, F0)
                else -> asm.fcvtWS(destReg, F0)
            }
            storeTo(inst.dest.name, destReg)
        }

        private fun emitFPToUI(inst: FPToUI) {
            moveToFp(F0, inst.value)
            val destReg = getDest(inst.dest.name)
            when {
                inst.value.type == Type.F64 && inst.toType == Type.I64 -> asm.fcvtLuD(destReg, F0)
                inst.value.type == Type.F32 && inst.toType == Type.I64 -> asm.fcvtLuS(destReg, F0)
                inst.value.type == Type.F64 -> asm.fcvtWuD(destReg, F0)
                else -> asm.fcvtWuS(destReg, F0)
            }
            storeTo(inst.dest.name, destReg)
        }

        private fun emitFPExt(inst: FPExt) {
            moveToFp(F0, inst.value)
            asm.fcvtDS(F0, F0)
            val destReg = getDest(inst.dest.name)
            asm.fmvXD(destReg, F0)
            storeTo(inst.dest.name, destReg)
        }

        private fun emitFPTrunc(inst: FPTrunc) {
            moveToFp(F0, inst.value)
            asm.fcvtSD(F0, F0)
            val destReg = getDest(inst.dest.name)
            asm.fmvXW(destReg, F0)
            storeTo(inst.dest.name, destReg)
        }

        private var memOpId = 0

        private fun emitCopySign(inst: CopySign) {
            // RISC-V has native fsgnj: takes magnitude from rs1, sign from rs2
            moveToFp(F0, inst.magnitude)
            moveToFp(F1, inst.sign)
            if (inst.magnitude.type == Type.F32) asm.fsgnjS(F0, F0, F1) else asm.fsgnjD(F0, F0, F1)
            val destReg = getDest(inst.dest.name)
            if (inst.magnitude.type == Type.F32) asm.fmvXW(destReg, F0) else asm.fmvXD(destReg, F0)
            storeTo(inst.dest.name, destReg)
        }

        private fun emitIntMinMax(dest: InstructionRef, lhs: Value, rhs: Value, signed: Boolean, isMin: Boolean) {
            // blt/bltu lhs, rhs, take_lhs; mv dest, rhs; j done; take_lhs: mv dest, lhs; done:
            val id = memOpId++
            val lhsReg = getOrLoad(lhs)
            val rhsReg = getOrLoad(rhs)
            val destReg = getDest(dest.name)
            val takeLhs = "${fn.name}.minmax_lhs_$id"
            val done = "${fn.name}.minmax_done_$id"
            if (isMin) {
                if (signed) asm.blt(lhsReg, rhsReg, takeLhs) else asm.bltu(lhsReg, rhsReg, takeLhs)
            } else {
                if (signed) asm.bge(lhsReg, rhsReg, takeLhs) else asm.bgeu(lhsReg, rhsReg, takeLhs)
            }
            asm.addi(destReg, rhsReg, 0) // mv dest, rhs
            asm.j(done)
            asm.label(takeLhs)
            asm.addi(destReg, lhsReg, 0) // mv dest, lhs
            asm.label(done)
            storeTo(dest.name, destReg)
        }

        private fun emitAbs(inst: Abs) {
            // abs(x) = x >= 0 ? x : -x → srai sign, x, 63; xor tmp, x, sign; sub dest, tmp, sign
            val src = getOrLoad(inst.operand)
            val destReg = getDest(inst.dest.name)
            val t = X6 // t1 scratch
            asm.srai(t, src, 63) // sign = arithmetic shift right by 63 (all 0s or all 1s)
            asm.xor(destReg, src, t) // xor with sign mask
            asm.sub(destReg, destReg, t) // subtract sign mask (adds 1 if negative)
            storeTo(inst.dest.name, destReg)
        }

        private fun emitFMA(inst: FMA) {
            // fmadd: rd = rs1*rs2 + rs3
            moveToFp(F0, inst.a)
            moveToFp(F1, inst.b)
            moveToFp(F2, inst.c)
            if (inst.a.type == Type.F32) asm.fmaddS(F0, F0, F1, F2) else asm.fmaddD(F0, F0, F1, F2)
            val destReg = getDest(inst.dest.name)
            if (inst.a.type == Type.F32) asm.fmvXW(destReg, F0) else asm.fmvXD(destReg, F0)
            storeTo(inst.dest.name, destReg)
        }

        private fun emitFRem(inst: FRem) {
            // a - trunc(a/b) * b: no native frem on RISC-V
            moveToFp(F0, inst.lhs)
            moveToFp(F1, inst.rhs)
            val isF32 = inst.lhs.type == Type.F32
            // F2 = a / b
            if (isF32) asm.fdivS(F2, F0, F1) else asm.fdivD(F2, F0, F1)
            // F2 = trunc(F2): convert to int and back
            if (isF32) {
                asm.fcvtWS(scratch, F2, 1) // rm=1 is RTZ (round toward zero)
                asm.fcvtSW(F2, scratch)
            } else {
                asm.fcvtLS(scratch, F2, 1)
                asm.fcvtSL(F2, scratch)
            }
            // F2 = trunc(a/b) * b
            if (isF32) asm.fmulS(F2, F2, F1) else asm.fmulD(F2, F2, F1)
            // F0 = a - trunc(a/b)*b
            if (isF32) asm.fsubS(F0, F0, F2) else asm.fsubD(F0, F0, F2)
            val destReg = getDest(inst.dest.name)
            if (isF32) asm.fmvXW(destReg, F0) else asm.fmvXD(destReg, F0)
            storeTo(inst.dest.name, destReg)
        }

        private fun emitBitReverse(inst: BitReverse) {
            // No native brev on base RISC-V — swap bits in a loop
            // For simplicity, use shift-and-mask approach (8 instructions for 64-bit)
            val src = getOrLoad(inst.operand)
            val destReg = getDest(inst.dest.name)
            val t = X6 // t1 scratch
            val bits = if (inst.dest.type.is64Bit()) 64 else 32
            // Swap consecutive bit pairs, nibbles, bytes, then bswap
            // Step 1: swap odd/even bits: x = ((x >> 1) & 0x5555...) | ((x & 0x5555...) << 1)
            // This is many instructions without Zbb. Use a simple loop instead.
            // Loop: for i in 0..bits-1: dest |= ((src >> i) & 1) << (bits-1-i)
            asm.li(destReg, 0)
            asm.li(t, 0) // counter
            val loopLabel = "${fn.name}.brev_loop_${memOpId}"
            val doneLabel = "${fn.name}.brev_done_${memOpId++}"
            asm.label(loopLabel)
            asm.addi(scratch, X0, bits) // bits constant
            asm.bge(t, scratch, doneLabel)
            // extract bit i from src
            asm.srl(scratch, src, t) // scratch = src >> i
            asm.andi(scratch, scratch, 1) // scratch = bit i
            // shift to position (bits-1-i)
            val t2 = X7 // t2 scratch
            asm.addi(t2, X0, bits - 1)
            asm.sub(t2, t2, t) // t2 = bits-1-i
            asm.sll(scratch, scratch, t2) // scratch <<= (bits-1-i)
            asm.or(destReg, destReg, scratch) // dest |= bit
            asm.addi(t, t, 1)
            asm.j(loopLabel)
            asm.label(doneLabel)
            storeTo(inst.dest.name, destReg)
        }

        private fun emitRotl(inst: Rotl) {
            // rotl(x, k) = (x << k) | (x >>> (bits - k))
            val src = getOrLoad(inst.value)
            val amt = getOrLoad(inst.amount)
            val destReg = getDest(inst.dest.name)
            val t = X6
            val bits = if (inst.dest.type.is64Bit()) 64 else 32
            asm.sll(destReg, src, amt) // destReg = x << k
            asm.addi(t, X0, bits)
            asm.sub(t, t, amt) // t = bits - k
            asm.srl(t, src, t) // t = x >>> (bits - k)
            asm.or(destReg, destReg, t)
            storeTo(inst.dest.name, destReg)
        }

        private fun emitRotr(inst: Rotr) {
            // rotr(x, k) = (x >>> k) | (x << (bits - k))
            val src = getOrLoad(inst.value)
            val amt = getOrLoad(inst.amount)
            val destReg = getDest(inst.dest.name)
            val t = X6
            val bits = if (inst.dest.type.is64Bit()) 64 else 32
            asm.srl(destReg, src, amt) // destReg = x >>> k
            asm.addi(t, X0, bits)
            asm.sub(t, t, amt) // t = bits - k
            asm.sll(t, src, t) // t = x << (bits - k)
            asm.or(destReg, destReg, t)
            storeTo(inst.dest.name, destReg)
        }

        private fun emitMemCpyLoop(dst: Value, src: Value, len: Value) {
            val id = memOpId++
            val dstReg = getOrLoad(dst)
            val srcReg = getOrLoad(src)
            val lenReg = getOrLoad(len)
            val counter = X6
            asm.li(counter, 0)
            val loopLabel = "${fn.name}.memcpy_loop_$id"
            val doneLabel = "${fn.name}.memcpy_done_$id"
            asm.label(loopLabel)
            asm.bge(counter, lenReg, doneLabel)
            asm.add(scratch, srcReg, counter)
            asm.lb(scratch, RiscVMemory(scratch, 0))
            asm.add(X7, dstReg, counter)
            asm.sb(scratch, RiscVMemory(X7, 0))
            asm.addi(counter, counter, 1)
            asm.j(loopLabel)
            asm.label(doneLabel)
        }

        private fun emitMemSetLoop(dst: Value, value: Value, len: Value) {
            val id = memOpId++
            val dstReg = getOrLoad(dst)
            val valReg = getOrLoad(value)
            val lenReg = getOrLoad(len)
            val counter = X6
            asm.li(counter, 0)
            val loopLabel = "${fn.name}.memset_loop_$id"
            val doneLabel = "${fn.name}.memset_done_$id"
            asm.label(loopLabel)
            asm.bge(counter, lenReg, doneLabel)
            asm.add(scratch, dstReg, counter)
            asm.sb(valReg, RiscVMemory(scratch, 0))
            asm.addi(counter, counter, 1)
            asm.j(loopLabel)
            asm.label(doneLabel)
        }

        private fun emitNeg(inst: Neg) {
            val src = getOrLoad(inst.operand)
            val destReg = getDest(inst.dest.name)
            asm.sub(destReg, X0, src)
            storeTo(inst.dest.name, destReg)
        }

        private fun emitNot(inst: Not) {
            val src = getOrLoad(inst.operand)
            val destReg = getDest(inst.dest.name)
            asm.xori(destReg, src, -1)
            storeTo(inst.dest.name, destReg)
        }

        private fun emitCopy(dest: InstructionRef, value: Value) {
            val src = getOrLoad(value)
            val destReg = getDest(dest.name)
            if (destReg != src) asm.mv(destReg, src)
            storeTo(dest.name, destReg)
        }

        private fun emitFpSqrt(inst: Sqrt) {
            moveToFp(F0, inst.operand)
            if (inst.operand.type == Type.F32) asm.fsqrtS(F0, F0) else asm.fsqrtD(F0, F0)
            val destReg = getDest(inst.dest.name)
            if (inst.operand.type == Type.F32) asm.fmvXW(destReg, F0) else asm.fmvXD(destReg, F0)
            storeTo(inst.dest.name, destReg)
        }

        private fun emitFpAbs(inst: FAbs) {
            moveToFp(F0, inst.operand)
            if (inst.operand.type == Type.F32) asm.fabsS(F0, F0) else asm.fabsD(F0, F0)
            val destReg = getDest(inst.dest.name)
            if (inst.operand.type == Type.F32) asm.fmvXW(destReg, F0) else asm.fmvXD(destReg, F0)
            storeTo(inst.dest.name, destReg)
        }

        private fun emitIntTrunc(inst: IntTrunc) {
            val src = getOrLoad(inst.value)
            val destReg = getDest(inst.dest.name)
            if (destReg != src) asm.mv(destReg, src)
            storeTo(inst.dest.name, destReg)
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
                        is RiscVLocation.RegFp -> {
                            // Move FP value to GP scratch for integer context
                            asm.fmvXD(scratch, loc.reg)
                            scratch
                        }
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
                is RiscVLocation.RegFp -> {
                    // Move GP value to FP register
                    asm.fmvDX(loc.reg, reg)
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
