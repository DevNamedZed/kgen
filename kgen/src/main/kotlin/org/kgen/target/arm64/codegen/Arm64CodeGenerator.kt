package org.kgen.target.arm64.codegen

import org.kgen.target.arm64.*
import org.kgen.target.arm64.asm.*
import org.kgen.binary.*
import org.kgen.binary.elf.ElfObjectWriter
import org.kgen.binary.elf.ElfLinker
import org.kgen.binary.elf.ElfMachine
import org.kgen.ir.*
import org.kgen.ir.FCmpPredicate
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
            arch = Architecture(ArchType.AARCH64),
        )
    }

    private class CodeGenContext(val module: Module) {
        val asm = Arm64Assembler()
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
                val funcOffset = asm.size()
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
                    type = RelocationType.AArch64.CALL26,
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
                codeAlignFactor = 4,
                dataAlignFactor = -8,
                returnAddressRegister = 30, // LR (X30)
                initialInstructions = listOf(
                    CfiInstruction.defCfa(31, 0), // SP + 0
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
                symbols = codeSymbols,
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
        // AAPCS64 argument registers (X0-X7)
        private val argRegs64 = listOf(
            Arm64Register.X0, Arm64Register.X1, Arm64Register.X2, Arm64Register.X3,
            Arm64Register.X4, Arm64Register.X5, Arm64Register.X6, Arm64Register.X7,
        )
        private val argRegs32 = listOf(
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
        private val fpArgRegs: List<Arm64Reg> = listOf(
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
        private var regSaveAreaOffset = 0
        private var fpRegSaveAreaOffset = 0
        private var vaLabelCounter = 0
        private val namedGpParams: Int by lazy { fn.params.count { it.type != Type.F32 && it.type != Type.F64 } }
        private val namedFpParams: Int by lazy { fn.params.count { it.type == Type.F32 || it.type == Type.F64 } }

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

        private val stackMapEntries = mutableListOf<StackMapEntry>()
        private val gcRoots = mutableSetOf<String>()
        private val hasGcStrategy: Boolean = fn.gc != null && fn.gc != "none"
        private var funcStartOffset = 0
        private val hasExceptionHandling: Boolean = fn.blocks.any { b ->
            b.instructions.any { it is Instruction.Invoke || it is Instruction.LandingPad }
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
            funcStartOffset = asm.size()
            runRegisterAllocator()
            emitPrologue()
            emitBlocks()
            if (hasGcStrategy && stackMapEntries.isNotEmpty()) {
                ctx.stackMaps.add(StackMap(fn.name, stackMapEntries.toList()))
            }
            if (hasExceptionHandling) buildLsda()
            val funcEnd = asm.size()
            ctx.fdeEntries.add(buildFde(funcStartOffset, funcEnd - funcStartOffset))
        }

        private fun buildFde(startOffset: Int, length: Int): FdeEntry {
            val cfi = mutableListOf<CfiInstruction>()
            // After stp fp, lr, [sp, #-frameSize]!  (4 bytes):
            // CFA = SP + frameSize, FP saved at CFA - frameSize, LR saved at CFA - frameSize + 8
            cfi.add(CfiInstruction.advanceLoc(1)) // 1 * codeAlignFactor(4) = 4 bytes
            cfi.add(CfiInstruction.defCfa(31, stackReserve)) // SP + frameSize
            cfi.add(CfiInstruction.offset(29, stackReserve / 8)) // FP at CFA - frameSize
            cfi.add(CfiInstruction.offset(30, stackReserve / 8 - 1)) // LR at CFA - frameSize + 8
            // After mov fp, sp (4 bytes): CFA = FP + frameSize
            cfi.add(CfiInstruction.advanceLoc(1))
            cfi.add(CfiInstruction.defCfaRegister(29)) // FP-based CFA
            return FdeEntry(fn.name, startOffset.toLong(), length.toLong(), cfi)
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
            val fp = Arm64Register.FP
            val lr = Arm64Register.LR
            val sp = Arm64Register.SP

            // Separate GP and FP callee-saved registers
            val gpCalleeSaved = alloc.usedCalleeRegs
                .filter { it in calleeSavedSet }
                .sortedBy { it.encoding() }
            val fpCalleeSaved = alloc.usedCalleeRegs
                .filter { it in fpCalleeSavedSet }
                .sortedBy { it.encoding() }

            // Total frame size: 16 (FP+LR) + GP callee-saved pairs + FP callee-saved pairs + spill slots
            // For vararg functions, also reserve 64 bytes (GP X0-X7) + 128 bytes (FP D0-D7) save area
            val gpSaveSize = ((gpCalleeSaved.size + 1) / 2) * 16
            val fpSaveSize = ((fpCalleeSaved.size + 1) / 2) * 16
            val spillSize = alloc.spillSlots * 8
            val varArgSaveSize = if (fn.isVarArg) 64 + 128 else 0
            val frameSize = alignTo16(16 + gpSaveSize + fpSaveSize + spillSize + varArgSaveSize)
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

            // Save vararg argument registers (GP X0-X7 and FP Q0-Q7)
            if (fn.isVarArg) {
                regSaveAreaOffset = offset
                // Save X0-X7 (8 GP regs × 8 bytes = 64 bytes, stored as STP pairs)
                for (pair in 0 until 4) {
                    val r1 = argRegs64[pair * 2] as Arm64Register64
                    val r2 = argRegs64[pair * 2 + 1] as Arm64Register64
                    asm.stp(r1, r2, fp, offset + pair * 16)
                }

                fpRegSaveAreaOffset = offset + 64
                // Save Q0-Q7 (8 FP/SIMD regs × 16 bytes = 128 bytes)
                // AAPCS64 va_list uses 16-byte slots for FP args (full Q register width)
                val fpBase = fpRegSaveAreaOffset
                for (idx in 0 until 8) {
                    val q = Arm64Register.byEncodingQ(idx)
                    // strQ requires 16-byte aligned offset; fpBase is 16-aligned (offset starts 16-aligned, +64 is fine)
                    asm.strQ(q, fp, fpBase + idx * 16)
                }
            }
        }

        private fun emitEpilogue() {
            val fp = Arm64Register.FP
            val lr = Arm64Register.LR
            val sp = Arm64Register.SP

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

                is Instruction.Neg -> emitNeg(i)
                is Instruction.Not -> emitNot(i)
                is Instruction.IntTrunc -> emitIntTrunc(i)

                is Instruction.ICmp -> emitICmp(i)
                is Instruction.Ret -> emitReturn(i)
                is Instruction.Br -> emitBr(i)
                is Instruction.IndirectBr -> emitIndirectBr(i)
                is Instruction.Call -> emitCall(i)
                is Instruction.Select -> emitSelect(i)
                is Instruction.SExt -> emitSExt(i)
                is Instruction.ZExt -> emitZExt(i)
                is Instruction.Trunc -> emitTrunc(i)
                is Instruction.Load -> emitLoad(i)
                is Instruction.Store -> emitStore(i)
                is Instruction.Alloca -> {}
                is Instruction.Switch -> emitSwitch(i)

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

                is Instruction.Ctlz -> emitUnaryIntOp(i.dest, i.operand) { rd, rn -> asm.clz(rd, rn) }
                is Instruction.Cttz -> emitCttz(i)
                is Instruction.Ctpop -> emitCtpop(i)
                is Instruction.BSwap -> emitUnaryIntOp(i.dest, i.operand) { rd, rn -> asm.rev(rd, rn) }

                is Instruction.PtrToInt -> emitCopy64(i.dest, i.value)
                is Instruction.IntToPtr -> emitCopy64(i.dest, i.value)
                is Instruction.BitCast -> emitCopy64(i.dest, i.value)

                is Instruction.Sqrt -> emitFpUnaryOp(i.dest, i.operand) { rd, rn -> asm.fsqrt(rd, rn) }
                is Instruction.Ceil -> emitFpUnaryOpD(i.dest, i.operand) { rd, rn -> asm.frintp(rd, rn) }
                is Instruction.Floor -> emitFpUnaryOpD(i.dest, i.operand) { rd, rn -> asm.frintm(rd, rn) }
                is Instruction.Round -> emitFpUnaryOpD(i.dest, i.operand) { rd, rn -> asm.frintn(rd, rn) }
                is Instruction.FAbs -> emitFpUnaryOp(i.dest, i.operand) { rd, rn -> asm.fabs(rd, rn) }
                is Instruction.FMin -> emitFpBinOp(i.dest, i.lhs, i.rhs) { rd, rn, rm -> asm.fmin(rd, rn, rm) }
                is Instruction.FMax -> emitFpBinOp(i.dest, i.lhs, i.rhs) { rd, rn, rm -> asm.fmax(rd, rn, rm) }

                is Instruction.Fence -> asm.dmb()

                is Instruction.Unreachable -> asm.udf()
                is Instruction.Trap -> asm.brk(0)
                is Instruction.DebugTrap -> asm.brk(0)

                is Instruction.DebugLoc -> {}
                is Instruction.DebugValue -> {}
                is Instruction.DebugDeclare -> {}

                is Instruction.GetElementPtr -> emitGetElementPtr(i)
                is Instruction.ExtractValue -> emitExtractValue(i)
                is Instruction.InsertValue -> emitInsertValue(i)

                is Instruction.FRem -> emitFRem(i)

                is Instruction.VAStart -> emitVAStart(i)
                is Instruction.VAEnd -> {}
                is Instruction.VAArg -> emitVAArg(i)
                is Instruction.VACopy -> emitVACopy(i)

                is Instruction.GCSafepoint -> emitGCSafepoint()
                is Instruction.GCRoot -> emitGCRoot(i)

                is Instruction.Invoke -> emitInvoke(i)
                is Instruction.LandingPad -> emitLandingPad(i)
                is Instruction.Resume -> emitResume(i)

                is Instruction.CopySign -> emitCopySign(i)
                is Instruction.SMin -> emitIntMinMax(i.dest, i.lhs, i.rhs, Arm64Condition.LT)
                is Instruction.SMax -> emitIntMinMax(i.dest, i.lhs, i.rhs, Arm64Condition.GT)
                is Instruction.UMin -> emitIntMinMax(i.dest, i.lhs, i.rhs, Arm64Condition.CC) // unsigned lower
                is Instruction.UMax -> emitIntMinMax(i.dest, i.lhs, i.rhs, Arm64Condition.HI) // unsigned higher
                is Instruction.Abs -> emitAbs(i)
                is Instruction.FMA -> emitFMA(i)
                is Instruction.BitReverse -> emitUnaryIntOp(i.dest, i.operand) { rd, rn -> asm.rbit(rd, rn) }
                is Instruction.Rotl -> emitRotl(i)
                is Instruction.Rotr -> emitBinOp(i.dest, i.value, i.amount) { rd, rn, rm -> asm.ror(rd, rn, rm) }
                is Instruction.MemCpy -> emitMemCpyLoop(i.dst, i.src, i.len)
                is Instruction.MemSet -> emitMemSetLoop(i.dst, i.value, i.len)
                is Instruction.MemMove -> emitMemCpyLoop(i.dst, i.src, i.len)
                is Instruction.Prefetch -> {} // hint only, no-op
                is Instruction.StackSave -> emitStackSave(i)
                is Instruction.StackRestore -> emitStackRestore(i)

                else -> error("Unsupported IR instruction for ARM64: ${i::class.simpleName}")
            }
        }

        private fun emitFRem(inst: Instruction.FRem) {
            // ARM64 has no direct FP remainder instruction
            // Compute: result = lhs - trunc(lhs / rhs) * rhs
            if (inst.lhs.type == Type.F64) {
                val lhsReg = getOrLoadFpD(inst.lhs)
                val rhsReg = getOrLoadFpD(inst.rhs)
                val destReg = getDestFpD(inst.dest.name)
                val scratchD = Arm64Register.D17

                // scratch = lhs / rhs
                asm.fdiv(scratchD, lhsReg, rhsReg)
                // scratch = trunc(scratch) — round toward zero
                asm.frintz(scratchD, scratchD)
                // scratch = trunc * rhs
                asm.fmul(scratchD, scratchD, rhsReg)
                // dest = lhs - scratch
                asm.fsub(destReg, lhsReg, scratchD)
                storeToFpD(inst.dest.name, destReg)
            } else {
                val lhsReg = getOrLoadFpS(inst.lhs)
                val rhsReg = getOrLoadFpS(inst.rhs)
                val destReg = getDestFpS(inst.dest.name)
                val scratchS = Arm64Register.S17

                asm.fdiv(scratchS, lhsReg, rhsReg)
                asm.frintz(scratchS, scratchS)
                asm.fmul(scratchS, scratchS, rhsReg)
                asm.fsub(destReg, lhsReg, scratchS)
                storeToFpS(inst.dest.name, destReg)
            }
        }

        private fun emitCopySign(inst: Instruction.CopySign) {
            // copysign(mag, sign): |mag| with sign of sign operand
            // fabs + fneg + fcmpZero + fcsel
            if (inst.magnitude.type == Type.F64) {
                val mag = getOrLoadFpD(inst.magnitude)
                val sign = getOrLoadFpD(inst.sign)
                val dest = getDestFpD(inst.dest.name)
                val negD = Arm64Register.D17
                asm.fabs(dest, mag)
                asm.fneg(negD, dest)
                asm.fcmpZero(sign)
                asm.fcsel(dest, negD, dest, Arm64Condition.LT)
                storeToFpD(inst.dest.name, dest)
            } else {
                val mag = getOrLoadFpS(inst.magnitude)
                val sign = getOrLoadFpS(inst.sign)
                val dest = getDestFpS(inst.dest.name)
                val negS = Arm64Register.S17
                asm.fabs(dest, mag)
                asm.fneg(negS, dest)
                asm.fcmpZero(sign)
                asm.fcsel(dest, negS, dest, Arm64Condition.LT)
                storeToFpS(inst.dest.name, dest)
            }
        }

        private fun emitIntMinMax(dest: InstructionRef, lhs: Value, rhs: Value, cond: Arm64Condition) {
            // cmp lhs, rhs; csel dest, lhs, rhs, cond
            val is64 = dest.type.is64Bit()
            if (is64) {
                val lhsReg = getOrLoad64(lhs)
                val rhsReg = getOrLoad64(rhs)
                val destReg = getDest64(dest.name)
                asm.cmp(lhsReg, rhsReg)
                asm.csel(destReg, lhsReg, rhsReg, cond)
                storeTo64(dest.name, destReg)
            } else {
                val lhsReg = getOrLoad32(lhs)
                val rhsReg = getOrLoad32(rhs)
                val destReg = getDest32(dest.name)
                asm.cmp(lhsReg, rhsReg)
                asm.csel(destReg, lhsReg, rhsReg, cond)
                storeTo32(dest.name, destReg as Arm64Register)
            }
        }

        private fun emitAbs(inst: Instruction.Abs) {
            // abs(x) = x >= 0 ? x : -x  →  cmp x, 0; csneg dest, x, x, GE
            val is64 = inst.dest.type.is64Bit()
            if (is64) {
                val src = getOrLoad64(inst.operand)
                val destReg = getDest64(inst.dest.name)
                asm.cmp(src, 0)
                asm.csneg(destReg, src, src, Arm64Condition.GE)
                storeTo64(inst.dest.name, destReg)
            } else {
                val src = getOrLoad32(inst.operand)
                val destReg = getDest32(inst.dest.name)
                asm.cmp(src, 0)
                asm.csneg(destReg, src, src, Arm64Condition.GE)
                storeTo32(inst.dest.name, destReg as Arm64Register)
            }
        }

        private fun emitFMA(inst: Instruction.FMA) {
            // fmadd fd, fn, fm, fa: fd = fn * fm + fa → fd = a * b + c
            if (inst.a.type == Type.F64) {
                val aReg = getOrLoadFpD(inst.a)
                val bReg = getOrLoadFpD(inst.b)
                val cReg = getOrLoadFpD(inst.c)
                val destReg = getDestFpD(inst.dest.name)
                asm.fmadd(destReg, aReg, bReg, cReg)
                storeToFpD(inst.dest.name, destReg)
            } else {
                val aReg = getOrLoadFpS(inst.a)
                val bReg = getOrLoadFpS(inst.b)
                val cReg = getOrLoadFpS(inst.c)
                val destReg = getDestFpS(inst.dest.name)
                asm.fmadd(destReg, aReg, bReg, cReg)
                storeToFpS(inst.dest.name, destReg)
            }
        }

        private fun emitRotl(inst: Instruction.Rotl) {
            // rotl(x, k) = ror(x, bitwidth - k) since ARM64 only has ROR
            val is64 = inst.dest.type.is64Bit()
            if (is64) {
                val valReg = getOrLoad64(inst.value)
                val amtReg = getOrLoad64(inst.amount)
                val destReg = getDest64(inst.dest.name)
                // scratch = 64 - amount
                asm.movz(scratch64, 64)
                asm.sub(scratch64, scratch64, amtReg)
                asm.ror(destReg, valReg, scratch64)
                storeTo64(inst.dest.name, destReg)
            } else {
                val valReg = getOrLoad32(inst.value)
                val amtReg = getOrLoad32(inst.amount)
                val destReg = getDest32(inst.dest.name)
                asm.movz(scratch32, 32)
                asm.sub(scratch32, scratch32, amtReg)
                asm.ror(destReg, valReg, scratch32)
                storeTo32(inst.dest.name, destReg as Arm64Register)
            }
        }

        private var memOpId = 0

        private fun emitMemCpyLoop(dst: Value, src: Value, len: Value) {
            // Byte-by-byte copy: while (counter < len) dst[counter] = src[counter]; counter++
            val id = memOpId++
            val dstReg = getOrLoad64(dst)
            val srcReg = getOrLoad64(src)
            val lenReg = getOrLoad64(len)
            val counter = scratch64
            val scratch17 = Arm64Register.X17
            asm.mov(counter, Arm64Register.XZR)
            val loopLabel = "${fn.name}.memcpy_loop_$id"
            val doneLabel = "${fn.name}.memcpy_done_$id"
            asm.label(loopLabel)
            asm.cmp(counter, lenReg)
            asm.bCond(Arm64Condition.GE, doneLabel)
            // addr = src + counter
            asm.add(scratch17, srcReg, counter)
            asm.ldrb(scratch32, scratch17 as Arm64Register)
            asm.add(scratch17, dstReg, counter)
            asm.strb(scratch32, scratch17 as Arm64Register)
            asm.add(counter, counter, 1)
            asm.b(loopLabel)
            asm.label(doneLabel)
        }

        private fun emitMemSetLoop(dst: Value, value: Value, len: Value) {
            val id = memOpId++
            val dstReg = getOrLoad64(dst)
            val valReg = getOrLoad32(value)
            val lenReg = getOrLoad64(len)
            val counter = scratch64
            val scratch17 = Arm64Register.X17
            asm.mov(counter, Arm64Register.XZR)
            val loopLabel = "${fn.name}.memset_loop_$id"
            val doneLabel = "${fn.name}.memset_done_$id"
            asm.label(loopLabel)
            asm.cmp(counter, lenReg)
            asm.bCond(Arm64Condition.GE, doneLabel)
            asm.add(scratch17, dstReg, counter)
            asm.strb(valReg, scratch17 as Arm64Register)
            asm.add(counter, counter, 1)
            asm.b(loopLabel)
            asm.label(doneLabel)
        }

        private fun emitStackSave(inst: Instruction.StackSave) {
            // Read SP into dest register
            val destReg = getDest64(inst.dest.name)
            // ARM64: mov xN, sp  — assembler may need add xN, sp, #0
            asm.add(destReg, Arm64Register.SP, 0)
            storeTo64(inst.dest.name, destReg)
        }

        private fun emitStackRestore(inst: Instruction.StackRestore) {
            val src = getOrLoad64(inst.ptr)
            // mov sp, xN — assembler may need add sp, xN, #0
            asm.add(Arm64Register.SP, src, 0)
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

        private fun emitGetElementPtr(inst: Instruction.GetElementPtr) {
            val destReg = getDest64(inst.dest.name)
            val ptrReg = getOrLoad64(inst.ptr)
            if (destReg != ptrReg) asm.mov(destReg, ptrReg)

            val constOffset = computeGepOffset(inst.baseType, inst.indices)
            if (constOffset != null && constOffset != 0) {
                val scratch2 = Arm64Register.X17
                emitLoadImm64(scratch2, constOffset.toLong())
                asm.add(destReg, destReg, scratch2)
            } else if (constOffset == null) {
                val scratch2 = Arm64Register.X17
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
                        if (off != 0) {
                            emitLoadImm64(scratch2, off.toLong())
                            asm.add(destReg, destReg, scratch2)
                        }
                    } else {
                        val elemSize = if (i == 0) typeSizeBytes(currentType)
                        else when (currentType) {
                            is Type.Array -> typeSizeBytes((currentType as Type.Array).element).also {
                                currentType = (currentType as Type.Array).element
                            }
                            else -> typeSizeBytes(currentType)
                        }
                        val idxReg = getOrLoad64(idx)
                        if (elemSize == 1) {
                            asm.add(destReg, destReg, idxReg)
                        } else {
                            emitLoadImm64(scratch2, elemSize.toLong())
                            asm.mul(scratch2, idxReg, scratch2)
                            asm.add(destReg, destReg, scratch2)
                        }
                    }
                }
            }
            storeTo64(inst.dest.name, destReg)
        }

        private fun emitExtractValue(inst: Instruction.ExtractValue) {
            val fieldType = aggregateFieldType(inst.aggregate.type, inst.indices)
            val fieldOffset = aggregateFieldOffset(inst.aggregate.type, inst.indices)

            val aggLoc = alloc.locations[inst.aggregate.name]
            val baseOffset = when (aggLoc) {
                is Arm64Location.Spill -> aggLoc.offset
                else -> error("ExtractValue: aggregate ${inst.aggregate.name} must be spilled")
            }

            val memOffset = baseOffset + fieldOffset
            when {
                fieldType == Type.I32 || fieldType == Type.F32 -> {
                    val dest = getDest32(inst.dest.name)
                    emitLoadFromFp32(dest, memOffset)
                    storeTo32(inst.dest.name, dest as Arm64Register)
                }
                else -> {
                    val dest = getDest64(inst.dest.name)
                    emitLoadFromFp64(dest, memOffset)
                    storeTo64(inst.dest.name, dest)
                }
            }
        }

        private fun emitInsertValue(inst: Instruction.InsertValue) {
            val fieldType = aggregateFieldType(inst.aggregate.type, inst.indices)
            val fieldOffset = aggregateFieldOffset(inst.aggregate.type, inst.indices)

            val aggLoc = alloc.locations[inst.aggregate.name]
            val destLoc = alloc.locations[inst.dest.name]

            if (aggLoc is Arm64Location.Spill && destLoc is Arm64Location.Spill && aggLoc.offset != destLoc.offset) {
                val aggSize = typeSizeBytes(inst.aggregate.type)
                val tmpReg = Arm64Register.X17
                for (off in 0 until aggSize step 8) {
                    emitLoadFromFp64(tmpReg, aggLoc.offset + off)
                    emitStoreToFp64(tmpReg, destLoc.offset + off)
                }
            }

            val baseOffset = when (destLoc) {
                is Arm64Location.Spill -> destLoc.offset
                else -> error("InsertValue: dest ${inst.dest.name} must be spilled")
            }

            val memOffset = baseOffset + fieldOffset
            when {
                fieldType == Type.I32 || fieldType == Type.F32 -> {
                    val valReg = getOrLoad32(inst.element)
                    emitStoreToFp32(valReg as Arm64Register32, memOffset)
                }
                else -> {
                    val valReg = getOrLoad64(inst.element)
                    emitStoreToFp64(valReg, memOffset)
                }
            }
        }

        private fun emitVAStart(inst: Instruction.VAStart) {
            val fp = Arm64Register.FP
            val x17 = Arm64Register.X17
            val x16 = Arm64Register.X16
            val w16 = Arm64Register.W16

            // Load va_list pointer into X17 (so X16 is free as scratch)
            val raw = getOrLoad64(inst.argList)
            if (raw != x17) asm.mov(x17, raw)
            val vaListPtr = x17

            // __stack (offset 0): FP + 16 (stack args past saved FP+LR)
            asm.add(x16, fp, 16)
            asm.str(x16, vaListPtr, 0)

            // __gr_top (offset 8): FP + regSaveAreaOffset + 64 (end of GP save area)
            val grTop = regSaveAreaOffset + 64
            if (grTop in 0..4095) {
                asm.add(x16, fp, grTop)
            } else {
                emitLoadImm64(x16, grTop.toLong())
                asm.add(x16, fp, x16)
            }
            asm.str(x16, vaListPtr, 8)

            // __vr_top (offset 16): FP + fpRegSaveAreaOffset + 128 (end of FP save area)
            val vrTop = fpRegSaveAreaOffset + 128
            if (vrTop in 0..4095) {
                asm.add(x16, fp, vrTop)
            } else {
                emitLoadImm64(x16, vrTop.toLong())
                asm.add(x16, fp, x16)
            }
            asm.str(x16, vaListPtr, 16)

            // __gr_offs (offset 24): -(8 - namedGpParams) × 8
            val grOffs = -(8 - namedGpParams) * 8
            emitLoadImm32(w16, grOffs)
            asm.str(w16, vaListPtr, 24)

            // __vr_offs (offset 28): -(8 - namedFpParams) × 16
            val vrOffs = -(8 - namedFpParams) * 16
            emitLoadImm32(w16, vrOffs)
            asm.str(w16, vaListPtr, 28)
        }

        private fun emitVAArg(inst: Instruction.VAArg) {
            val x17 = Arm64Register.X17
            val x16 = Arm64Register.X16
            val w16 = Arm64Register.W16
            val isGp = inst.argType != Type.F64 && inst.argType != Type.F32
            val id = vaLabelCounter++
            val overflowLabel = "${fn.name}.vaarg_overflow_$id"
            val doneLabel = "${fn.name}.vaarg_done_$id"

            if (isGp) {
                val dest64 = getDest64(inst.dest.name)
                val destIsScr = dest64 == x16

                // Load va_list pointer into X17
                val raw = getOrLoad64(inst.argList)
                if (raw != x17) asm.mov(x17, raw)

                // Check __gr_offs (offset 24)
                asm.ldr(w16, x17, 24)
                asm.cmp(w16, Arm64Register.WZR)
                asm.bCond(Arm64Condition.GE, overflowLabel)

                // Register path: compute arg address = __gr_top + sext(__gr_offs)
                // Use X16 for sign-extended offset, X17 for __gr_top (clobbers vaListPtr)
                asm.sxtw(x16, w16)              // X16 = sext(__gr_offs)
                asm.ldr(x17, x17, 8)            // X17 = __gr_top (vaListPtr lost)
                asm.add(x16, x17, x16)          // X16 = arg address
                asm.ldr(x16, x16, 0)            // X16 = arg value
                // Store result immediately (before clobbering X16)
                if (destIsScr) {
                    storeTo64(inst.dest.name, x16)
                } else {
                    asm.mov(dest64, x16)
                }
                // Advance __gr_offs: reload vaListPtr, load offs, add 8, store back
                val raw2 = getOrLoad64(inst.argList)
                if (raw2 != x17) asm.mov(x17, raw2)
                asm.ldr(w16, x17, 24)
                asm.add(w16, w16, 8)
                asm.str(w16, x17, 24)
                asm.b(doneLabel)

                // Overflow path: load from __stack
                asm.label(overflowLabel)
                // X17 still has vaListPtr here (we only clobbered it in register path)
                asm.ldr(x16, x17, 0)            // X16 = __stack
                asm.ldr(x16, x16, 0)            // X16 = *__stack (the arg)
                if (destIsScr) {
                    storeTo64(inst.dest.name, x16)
                } else {
                    asm.mov(dest64, x16)
                }
                // Advance __stack += 8
                asm.ldr(x16, x17, 0)
                asm.add(x16, x16, 8)
                asm.str(x16, x17, 0)

                asm.label(doneLabel)
                if (!destIsScr) {
                    storeTo64(inst.dest.name, dest64)
                }
            } else {
                val destFp = getDestFpD(inst.dest.name)

                // Load va_list pointer into X17
                val raw = getOrLoad64(inst.argList)
                if (raw != x17) asm.mov(x17, raw)

                // Check __vr_offs (offset 28)
                asm.ldr(w16, x17, 28)
                asm.cmp(w16, Arm64Register.WZR)
                asm.bCond(Arm64Condition.GE, overflowLabel)

                // Register path: arg address = __vr_top + sext(__vr_offs)
                asm.sxtw(x16, w16)              // X16 = sext(__vr_offs)
                asm.ldr(x17, x17, 16)           // X17 = __vr_top (vaListPtr lost)
                asm.add(x16, x17, x16)          // X16 = arg address
                asm.fldr(destFp, x16, 0)
                // Advance __vr_offs: reload vaListPtr
                val raw2 = getOrLoad64(inst.argList)
                if (raw2 != x17) asm.mov(x17, raw2)
                asm.ldr(w16, x17, 28)
                asm.add(w16, w16, 16)
                asm.str(w16, x17, 28)
                asm.b(doneLabel)

                // Overflow path: load from __stack
                asm.label(overflowLabel)
                asm.ldr(x16, x17, 0)            // X16 = __stack
                asm.fldr(destFp, x16, 0)
                // Advance __stack += 8
                asm.add(x16, x16, 8)
                asm.str(x16, x17, 0)

                asm.label(doneLabel)
                storeToFpD(inst.dest.name, destFp)
            }
        }

        private fun emitVACopy(inst: Instruction.VACopy) {
            // va_list is 32 bytes: copy 4 × 8 bytes, reloading src/dst pointers each iteration
            val x16 = Arm64Register.X16

            for (off in listOf(0, 8, 16, 24)) {
                // Load 8 bytes from src
                val src = getOrLoad64(inst.src)
                asm.ldr(x16, src, off)
                // Store 8 bytes to dst (getOrLoad64 may return X16 for dst, so we need X17)
                val x17 = Arm64Register.X17
                val dst = getOrLoad64(inst.dst)
                if (dst == x16) {
                    // dst pointer is in X16 which holds our data — reload dst into X17
                    // This shouldn't normally happen since dst is an alloca pointer, but handle it
                    asm.mov(x17, x16)
                    // We lost our data, need to reload
                    val srcAgain = getOrLoad64(inst.src)
                    asm.ldr(x16, srcAgain, off)
                    asm.str(x16, x17, off)
                } else {
                    asm.str(x16, dst, off)
                }
            }
        }

        private fun emitGCSafepoint() {
            if (!hasGcStrategy) return
            val offset = (asm.size() - funcStartOffset).toLong()
            val locations = collectGcLocations()
            stackMapEntries.add(StackMapEntry(offset, locations))

            // Emit call to safepoint poll function
            asm.bl("kgen_safepoint_poll")
        }

        private fun emitGCRoot(inst: Instruction.GCRoot) {
            gcRoots.add(inst.ptr.name)
        }

        private fun collectGcLocations(): List<StackMapLocation> {
            val locations = mutableListOf<StackMapLocation>()
            for ((name, loc) in alloc.locations) {
                val type = findValueType(name) ?: continue
                if (!isGcReferenceType(type)) continue
                when (loc) {
                    is Arm64Location.Reg64 -> locations.add(StackMapLocation.Register(loc.reg.encoding()))
                    is Arm64Location.Reg32 -> locations.add(StackMapLocation.Register(loc.reg.encoding()))
                    is Arm64Location.RegFp -> {}
                    is Arm64Location.Spill -> locations.add(StackMapLocation.Stack(loc.offset))
                }
            }
            for (rootName in gcRoots) {
                val allocaOff = alloc.locations[rootName]
                if (allocaOff is Arm64Location.Spill) {
                    locations.add(StackMapLocation.Stack(allocaOff.offset))
                }
            }
            return locations
        }

        private fun emitNeg(inst: Instruction.Neg) {
            val is64 = inst.dest.type.is64Bit()
            if (is64) {
                val src = getOrLoad64(inst.operand)
                val dest = getDest64(inst.dest.name)
                asm.neg(dest, src)
                storeTo64(inst.dest.name, dest)
            } else {
                val src = getOrLoad32(inst.operand)
                val dest = getDest32(inst.dest.name)
                asm.sub(dest as Arm64Register32, Arm64Register.WZR, src as Arm64Register32)
                storeTo32(inst.dest.name, dest as Arm64Register)
            }
        }

        private fun emitNot(inst: Instruction.Not) {
            val is64 = inst.dest.type.is64Bit()
            if (is64) {
                val src = getOrLoad64(inst.operand)
                val dest = getDest64(inst.dest.name)
                emitLoadImm64(scratch64, -1L)
                asm.eor(dest, src, scratch64)
                storeTo64(inst.dest.name, dest)
            } else {
                val src = getOrLoad32(inst.operand)
                val dest = getDest32(inst.dest.name)
                emitLoadImm32(scratch32, -1)
                asm.eor(dest as Arm64Register32, src as Arm64Register32, scratch32)
                storeTo32(inst.dest.name, dest as Arm64Register)
            }
        }

        private fun emitUnaryIntOp(
            dest: InstructionRef, operand: Value,
            op64: (Arm64Register64, Arm64Register64) -> Unit,
        ) {
            val is64 = dest.type.is64Bit()
            if (is64) {
                val src = getOrLoad64(operand)
                val destReg = getDest64(dest.name)
                op64(destReg, src)
                storeTo64(dest.name, destReg)
            } else {
                val src = getOrLoad32(operand)
                val destReg = getDest32(dest.name)
                @Suppress("UNCHECKED_CAST")
                (op64 as (Any, Any) -> Unit)(destReg, src)
                storeTo32(dest.name, destReg as Arm64Register)
            }
        }

        private fun emitCttz(inst: Instruction.Cttz) {
            // ARM64 has no CTZ — use RBIT + CLZ
            val is64 = inst.dest.type.is64Bit()
            if (is64) {
                val src = getOrLoad64(inst.operand)
                val destReg = getDest64(inst.dest.name)
                asm.rbit(destReg, src)
                asm.clz(destReg, destReg)
                storeTo64(inst.dest.name, destReg)
            } else {
                val src = getOrLoad32(inst.operand)
                val destReg = getDest32(inst.dest.name)
                asm.rbit(destReg, src)
                asm.clz(destReg, destReg)
                storeTo32(inst.dest.name, destReg as Arm64Register)
            }
        }

        private fun emitCtpop(inst: Instruction.Ctpop) {
            // ARM64 ctpop: FMOV GP→SIMD, CNT (count bits per byte), ADDV (horizontal sum), UMOV back to GP
            val scratchVec = Arm64Register.Q0
            if (inst.operand.type == Type.I64) {
                val src = getOrLoad64(inst.operand)
                val destReg = getDest64(inst.dest.name)
                asm.fmovFromGp64(Arm64Register.D0, src)
                asm.cnt(scratchVec, scratchVec, q128 = false)
                asm.addv(VectorArrangement.B8, scratchVec, scratchVec)
                asm.umovB(Arm64Register.W0, scratchVec, 0)
                if (destReg != Arm64Register.X0) asm.mov(destReg, Arm64Register.X0)
                storeTo64(inst.dest.name, destReg)
            } else {
                val src = getOrLoad32(inst.operand)
                val destReg = getDest32(inst.dest.name)
                asm.fmovFromGp32(Arm64Register.S0, src as Arm64Register32)
                asm.cnt(scratchVec, scratchVec, q128 = false)
                asm.addv(VectorArrangement.B8, scratchVec, scratchVec)
                asm.umovB(Arm64Register.W0, scratchVec, 0)
                if (destReg != Arm64Register.W0) asm.mov(destReg as Arm64Register32, Arm64Register.W0)
                storeTo32(inst.dest.name, destReg as Arm64Register)
            }
        }

        private fun emitCopy64(dest: InstructionRef, value: Value) {
            val src = getOrLoad64(value)
            val destReg = getDest64(dest.name)
            if (src != destReg) asm.mov(destReg, src)
            storeTo64(dest.name, destReg)
        }

        private fun emitFpUnaryOp(
            dest: InstructionRef, operand: Value,
            opD: (Arm64VecD, Arm64VecD) -> Unit,
        ) {
            if (dest.type == Type.F64) {
                val src = getOrLoadFpD(operand)
                val destReg = getDestFpD(dest.name)
                opD(destReg, src)
                storeToFpD(dest.name, destReg)
            } else {
                val src = getOrLoadFpS(operand)
                val destReg = getDestFpS(dest.name)
                @Suppress("UNCHECKED_CAST")
                (opD as (Any, Any) -> Unit)(destReg, src)
                storeToFpS(dest.name, destReg)
            }
        }

        private fun emitFpUnaryOpD(
            dest: InstructionRef, operand: Value,
            opD: (Arm64VecD, Arm64VecD) -> Unit,
        ) {
            // Ceil/Floor/Round only have double-precision assembler methods
            val src = getOrLoadFpD(operand)
            val destReg = getDestFpD(dest.name)
            opD(destReg, src)
            storeToFpD(dest.name, destReg)
        }

        private fun emitIntTrunc(inst: Instruction.IntTrunc) {
            val src = getOrLoad64(inst.value)
            val dest = getDest32(inst.dest.name)
            val srcW = reg64to32(src)
            if ((dest as Arm64Register) != srcW) {
                asm.mov(dest as Arm64Register32, srcW as Arm64Register32)
            }
            storeTo32(inst.dest.name, dest as Arm64Register)
        }

        private fun emitSwitch(inst: Instruction.Switch) {
            val is64 = inst.value.type.is64Bit()
            for ((caseVal, target) in inst.cases) {
                if (is64) {
                    val valReg = getOrLoad64(inst.value)
                    val caseReg = getOrLoad64(caseVal)
                    asm.cmp(valReg, caseReg)
                } else {
                    val valReg = getOrLoad32(inst.value)
                    val caseReg = getOrLoad32(caseVal)
                    asm.cmp(valReg as Arm64Register32, caseReg as Arm64Register32)
                }
                asm.bCond(Arm64Condition.EQ, "${fn.name}.$target")
            }
            asm.b("${fn.name}.${inst.defaultTarget}")
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
                val scratchR = scratch64
                // ARM64 has no remainder instruction: rem = lhs - (lhs / rhs) * rhs
                if (signed) asm.sdiv(scratchR, lhsReg, rhsReg) else asm.udiv(scratchR, lhsReg, rhsReg)
                asm.msub(destReg, scratchR, rhsReg, lhsReg)
                storeTo64(dest.name, destReg)
            } else {
                val lhsReg = getOrLoad32(lhs)
                val rhsReg = getOrLoad32(rhs)
                val destReg = getDest32(dest.name)
                val scratchR = scratch32
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
                Arm64Register.XZR,
                Arm64Register.XZR,
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
            asm.cmp(condReg, Arm64Register.WZR)
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
                    if (reg != Arm64Register.D0) {
                        asm.fmov(Arm64Register.D0, reg)
                    }
                } else if (retVal.type.is64Bit()) {
                    val reg = getOrLoad64(retVal)
                    if (reg != Arm64Register.X0) {
                        asm.mov(Arm64Register.X0, reg)
                    }
                } else {
                    val reg = getOrLoad32(retVal)
                    if (reg != Arm64Register.W0) {
                        asm.mov(Arm64Register.W0, reg)
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

        private fun emitIndirectBr(inst: Instruction.IndirectBr) {
            val addrReg = getOrLoad64(inst.address)
            asm.br(addrReg)
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
                        val destReg = argRegs64[gpIdx]
                        if (src != destReg) asm.mov(destReg, src)
                    } else {
                        val src = getOrLoad32(arg)
                        val destReg = argRegs32[gpIdx]
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
                if (destReg != Arm64Register.D0) {
                    asm.fmov(destReg, Arm64Register.D0)
                }
                storeToFpD(dest.name, destReg)
            } else if (dest.type.is64Bit()) {
                val destReg = getDest64(dest.name)
                if (destReg != Arm64Register.X0) {
                    asm.mov(destReg, Arm64Register.X0)
                }
                storeTo64(dest.name, destReg)
            } else {
                val destReg = getDest32(dest.name)
                if ((destReg as Arm64Register) != Arm64Register.W0) {
                    asm.mov(destReg as Arm64Register32, Arm64Register.W0)
                }
                storeTo32(dest.name, destReg as Arm64Register)
            }
        }

        private fun emitInvoke(inst: Instruction.Invoke) {
            val funcName = when (val f = inst.function) {
                is FunctionRef -> f.name
                is GlobalRef -> f.name
                else -> error("Unsupported invoke target: $f")
            }

            // Load arguments (same as emitCall)
            var gpIdx = 0
            var fpIdx = 0
            for (arg in inst.args) {
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
                        val destReg = argRegs64[gpIdx]
                        if (src != destReg) asm.mov(destReg, src)
                    } else {
                        val src = getOrLoad32(arg)
                        val destReg = argRegs32[gpIdx]
                        if (src != destReg) asm.mov(destReg, src)
                    }
                    gpIdx++
                }
            }

            // Record call site
            val callStart = asm.size() - funcStartOffset
            asm.bl(funcName)
            val callEnd = asm.size() - funcStartOffset

            val actionIndex = resolveActionIndex(inst.unwindDest)
            ehCallSites.add(EhCallSite(
                callOffset = callStart,
                callLength = callEnd - callStart,
                landingPadLabel = "${fn.name}.lp.${inst.unwindDest}",
                actionIndex = actionIndex,
            ))

            // Store return value
            val dest = inst.dest
            if (dest != null) {
                if (dest.type.isFloat()) {
                    val destReg = getDestFpD(dest.name)
                    if (destReg != Arm64Register.D0) {
                        asm.fmov(destReg, Arm64Register.D0)
                    }
                    storeToFpD(dest.name, destReg)
                } else if (dest.type.is64Bit()) {
                    val destReg = getDest64(dest.name)
                    if (destReg != Arm64Register.X0) {
                        asm.mov(destReg, Arm64Register.X0)
                    }
                    storeTo64(dest.name, destReg)
                } else {
                    val destReg = getDest32(dest.name)
                    if ((destReg as Arm64Register) != Arm64Register.W0) {
                        asm.mov(destReg as Arm64Register32, Arm64Register.W0)
                    }
                    storeTo32(dest.name, destReg as Arm64Register)
                }
            }

            // Branch to normal destination
            emitPhiMoves(inst.normalDest)
            asm.b("${fn.name}.${inst.normalDest}")
        }

        private fun emitLandingPad(inst: Instruction.LandingPad) {
            // Landing pad entry point — unwinder transfers control here.
            // ARM64 Itanium ABI: X0 = exception pointer, X1 = selector value
            val labelName = "${fn.name}.lp.${currentBlockLabel}"
            asm.label(labelName)

            val dest = inst.dest
            val loc = alloc.locations[dest.name]
            if (loc != null) {
                // Store exception pointer (X0) into dest
                when (loc) {
                    is Arm64Location.Reg64 -> {
                        if (loc.reg != Arm64Register.X0) {
                            asm.mov(loc.reg as Arm64Register64, Arm64Register.X0)
                        }
                    }
                    is Arm64Location.Spill -> {
                        emitStoreToFp64(Arm64Register.X0, loc.offset)
                    }
                    else -> {}
                }
            }
        }

        private fun emitResume(inst: Instruction.Resume) {
            // Call _Unwind_Resume(exceptionPtr) — X0 already holds the pointer
            val src = getOrLoad64(inst.value)
            if (src != Arm64Register.X0) {
                asm.mov(Arm64Register.X0, src)
            }
            asm.bl("_Unwind_Resume")
            asm.udf() // unreachable
        }

        private fun resolveActionIndex(unwindLabel: String): Int {
            val unwindBlock = fn.blocks.firstOrNull { it.label == unwindLabel } ?: return 0
            val lp = unwindBlock.instructions.firstOrNull { it is Instruction.LandingPad } as? Instruction.LandingPad
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

        private fun emitSelect(inst: Instruction.Select) {
            val condition = inst.condition
            val trueVal = inst.trueValue
            val falseVal = inst.falseValue
            val dest = inst.dest
            val condReg = getOrLoad32(condition)
            asm.cmp(condReg, Arm64Register.WZR)
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

        private fun isTlsGlobal(name: String): Boolean =
            ctx.module.globals.any { g -> g.name == name && g.threadLocal != null }

        private fun loadTlsAddress(name: String, dest: Arm64Register64): Arm64Register64 {
            // TPIDR_EL0 system register id: op0=3, op1=3, CRn=13, CRm=0, op2=2
            // Encoded as: (op0-2)<<14 | op1<<11 | CRn<<7 | CRm<<3 | op2 = 0x5E82
            // Actually: op0=11, op1=011, CRn=1101, CRm=0000, op2=010 → 0b11_011_1101_0000_010 = 0xDE82
            val tpidrEl0 = 0xDE82
            asm.mrs(dest, tpidrEl0)
            // add dest, dest, #:tprel_hi12:symbol
            asm.add(dest, dest, 0)
            ctx.relocations.add(Relocation(
                offset = (asm.size() - 4).toLong(), symbol = name,
                type = RelocationType.AArch64.TLSLE_ADD_TPREL_HI12, addend = 0, section = ".text"))
            // add dest, dest, #:tprel_lo12_nc:symbol
            asm.add(dest, dest, 0)
            ctx.relocations.add(Relocation(
                offset = (asm.size() - 4).toLong(), symbol = name,
                type = RelocationType.AArch64.TLSLE_ADD_TPREL_LO12_NC, addend = 0, section = ".text"))
            return dest
        }

        private fun emitLoad(inst: Instruction.Load) {
            val ptr = inst.ptr
            val dest = inst.dest
            val addrReg = if (ptr is GlobalRef && isTlsGlobal(ptr.name)) {
                loadTlsAddress(ptr.name, scratch64)
            } else {
                getOrLoad64(ptr)
            }
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
            val addrReg = if (ptr is GlobalRef && isTlsGlobal(ptr.name)) {
                loadTlsAddress(ptr.name, scratch64)
            } else {
                getOrLoad64(ptr)
            }
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
                    val r = scratch64
                    emitLoadImm64(r, value.value)
                    r
                }
                is Constant.I32 -> {
                    val r = scratch64
                    emitLoadImm64(r, value.value.toLong())
                    r
                }
                is Constant.NullPtr -> {
                    val r = scratch64
                    asm.mov(r, Arm64Register.XZR)
                    r
                }
                else -> {
                    when (val loc = alloc.locations[value.name]) {
                        is Arm64Location.Reg64 -> loc.reg
                        is Arm64Location.Reg32 -> loc.reg as Arm64Register64
                        is Arm64Location.RegFp -> {
                            val r = scratch64
                            asm.fmovToGp64(r, loc.reg as Arm64VecD)
                            r
                        }
                        is Arm64Location.Spill -> {
                            val r = scratch64
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
                    val r = scratch32
                    emitLoadImm32(r, value.value)
                    r
                }
                is Constant.I64 -> {
                    val r = scratch32
                    emitLoadImm32(r, value.value.toInt())
                    r
                }
                is Constant.I1 -> {
                    val r = scratch32
                    emitLoadImm32(r, if (value.value) 1 else 0)
                    r
                }
                else -> {
                    when (val loc = alloc.locations[value.name]) {
                        is Arm64Location.Reg32 -> loc.reg
                        is Arm64Location.Reg64 -> loc.reg as Arm64Register32
                        is Arm64Location.RegFp -> {
                            val r = scratch32
                            asm.fmovToGp32(r, loc.reg as Arm64VecS)
                            r
                        }
                        is Arm64Location.Spill -> {
                            val r = scratch32
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
                is Arm64Location.Reg64 -> loc.reg
                is Arm64Location.Spill -> scratch64
                else -> scratch64
            }
        }

        private fun getDest32(name: String): Arm64Register32 {
            return when (val loc = alloc.locations[name]) {
                is Arm64Location.Reg32 -> loc.reg
                is Arm64Location.Spill -> scratch32
                else -> scratch32
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

        private fun reg64to32(reg: Arm64Register64): Arm64Register32 {
            return Arm64Register.byEncoding32(reg.encoding())
        }

        // ── FP register helpers ─────────────────────────────────────

        private fun getOrLoadFpD(value: Value): Arm64VecD {
            return when (value) {
                is Constant.F64 -> {
                    val r = scratchFpD
                    emitLoadFpConst64(r, value.value)
                    r
                }
                is Constant.F32 -> {
                    val r = scratchFpD
                    emitLoadFpConst64(r, value.value.toDouble())
                    r
                }
                else -> {
                    when (val loc = alloc.locations[value.name]) {
                        is Arm64Location.RegFp -> loc.reg as Arm64VecD
                        is Arm64Location.Spill -> {
                            val r = scratchFpD
                            emitLoadFromFpFpD(r, loc.offset)
                            r
                        }
                        is Arm64Location.Reg64 -> {
                            val r = scratchFpD
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
                    val r = scratchFpS
                    emitLoadFpConst32(r, value.value)
                    r
                }
                else -> {
                    when (val loc = alloc.locations[value.name]) {
                        is Arm64Location.RegFp -> loc.reg as Arm64VecS
                        is Arm64Location.Spill -> {
                            val r = scratchFpS
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
                is Arm64Location.Spill -> scratchFpD
                else -> scratchFpD
            }
        }

        private fun getDestFpS(name: String): Arm64VecS {
            return when (val loc = alloc.locations[name]) {
                is Arm64Location.RegFp -> loc.reg as Arm64VecS
                is Arm64Location.Spill -> scratchFpS
                else -> scratchFpS
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
                Arm64Register.XZR,
                Arm64Register.XZR,
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
                asm.mov(rd, Arm64Register.XZR)
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
                asm.mov(rd, Arm64Register.WZR)
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
            val fp = Arm64Register.FP
            if (offset in -256..255) {
                asm.ldur(rd, fp, offset)
            } else if (offset >= 0 && offset % 8 == 0 && offset / 8 <= 4095) {
                asm.ldr(rd, fp, offset)
            } else {
                emitLoadImm64(scratch64, offset.toLong())
                asm.add(scratch64, fp, scratch64)
                asm.ldr(rd, scratch64)
            }
        }

        private fun emitStoreToFp64(rs: Arm64Register64, offset: Int) {
            val fp = Arm64Register.FP
            if (offset in -256..255) {
                asm.stur(rs, fp, offset)
            } else if (offset >= 0 && offset % 8 == 0 && offset / 8 <= 4095) {
                asm.str(rs, fp, offset)
            } else {
                // Need a second scratch for the address calc since rs might be scratch64
                val addrScratch = Arm64Register.X17
                emitLoadImm64(addrScratch, offset.toLong())
                asm.add(addrScratch, fp, addrScratch)
                asm.str(rs, addrScratch)
            }
        }

        private fun emitLoadFromFp32(rd: Arm64Register32, offset: Int) {
            val fp = Arm64Register.FP
            if (offset in -256..255) {
                asm.ldur(rd, fp, offset)
            } else if (offset >= 0 && offset % 4 == 0 && offset / 4 <= 4095) {
                asm.ldr(rd, fp, offset)
            } else {
                val addrScratch = Arm64Register.X17
                emitLoadImm64(addrScratch, offset.toLong())
                asm.add(addrScratch, fp, addrScratch)
                asm.ldr(rd, addrScratch)
            }
        }

        private fun emitStoreToFp32(rs: Arm64Register32, offset: Int) {
            val fp = Arm64Register.FP
            if (offset in -256..255) {
                asm.stur(rs, fp, offset)
            } else if (offset >= 0 && offset % 4 == 0 && offset / 4 <= 4095) {
                asm.str(rs, fp, offset)
            } else {
                val addrScratch = Arm64Register.X17
                emitLoadImm64(addrScratch, offset.toLong())
                asm.add(addrScratch, fp, addrScratch)
                asm.str(rs, addrScratch)
            }
        }

        // ── FP stack access ──────────────────────────────────────────

        private fun emitLoadFromFpFpD(rd: Arm64VecD, offset: Int) {
            val fp = Arm64Register.FP
            if (offset in -256..255) {
                asm.fldur(rd, fp, offset)
            } else if (offset >= 0 && offset % 8 == 0 && offset / 8 <= 4095) {
                asm.fldr(rd, fp, offset)
            } else {
                val addrScratch = Arm64Register.X17
                emitLoadImm64(addrScratch, offset.toLong())
                asm.add(addrScratch, fp, addrScratch)
                asm.fldr(rd, addrScratch)
            }
        }

        private fun emitStoreToFpFpD(rs: Arm64VecD, offset: Int) {
            val fp = Arm64Register.FP
            if (offset in -256..255) {
                asm.fstur(rs, fp, offset)
            } else if (offset >= 0 && offset % 8 == 0 && offset / 8 <= 4095) {
                asm.fstr(rs, fp, offset)
            } else {
                val addrScratch = Arm64Register.X17
                emitLoadImm64(addrScratch, offset.toLong())
                asm.add(addrScratch, fp, addrScratch)
                asm.fstr(rs, addrScratch)
            }
        }

        private fun emitLoadFromFpFpS(rd: Arm64VecS, offset: Int) {
            val fp = Arm64Register.FP
            if (offset >= 0 && offset % 4 == 0 && offset / 4 <= 4095) {
                asm.fldr(rd, fp, offset)
            } else {
                val addrScratch = Arm64Register.X17
                emitLoadImm64(addrScratch, offset.toLong())
                asm.add(addrScratch, fp, addrScratch)
                asm.fldr(rd, addrScratch)
            }
        }

        private fun emitStoreToFpFpS(rs: Arm64VecS, offset: Int) {
            val fp = Arm64Register.FP
            if (offset >= 0 && offset % 4 == 0 && offset / 4 <= 4095) {
                asm.fstr(rs, fp, offset)
            } else {
                val addrScratch = Arm64Register.X17
                emitLoadImm64(addrScratch, offset.toLong())
                asm.add(addrScratch, fp, addrScratch)
                asm.fstr(rs, addrScratch)
            }
        }

        private fun emitLoadFpConst64(rd: Arm64VecD, value: Double) {
            val bits = java.lang.Double.doubleToRawLongBits(value)
            if (bits == 0L) {
                // FMOV Dd, XZR
                asm.fmovFromGp64(rd, Arm64Register.XZR)
                return
            }
            val r = scratch64
            emitLoadImm64(r, bits)
            asm.fmovFromGp64(rd, r)
        }

        private fun emitLoadFpConst32(rd: Arm64VecS, value: Float) {
            val bits = java.lang.Float.floatToRawIntBits(value)
            if (bits == 0) {
                asm.fmovFromGp32(rd, Arm64Register.WZR)
                return
            }
            val r = scratch32
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
