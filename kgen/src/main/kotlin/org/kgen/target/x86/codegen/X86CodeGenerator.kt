package org.kgen.target.x86.codegen

import org.kgen.target.x86.*
import org.kgen.target.x86.asm.*
import org.kgen.binary.*
import org.kgen.binary.elf.*
import org.kgen.binary.pe.PeLinker
import org.kgen.binary.pe.PeExceptionWriter
import org.kgen.binary.pe.PeUnwindInfo
import org.kgen.binary.pe.UnwindCode
import org.kgen.binary.pe.UnwindOperation
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
 * x86-64 code generator: translates an IR [Module] into native machine code.
 *
 * This is the x86-64 backend of the three-layer code generation API. It takes a fully
 * constructed IR module and produces relocatable object code with `.text`, `.rodata`,
 * and optional `.tdata` (TLS) sections, along with symbols, relocations, and exception
 * handling metadata (`.eh_frame`, `.gcc_except_table` for ELF; `.pdata`/`.xdata` for PE).
 *
 * The code generation pipeline per function:
 * 1. **Register allocation** via [LinearScanRegisterAllocator][org.kgen.codegen.alloc.LinearScanRegisterAllocator]
 * 2. **Alloca offset computation** for stack-allocated variables
 * 3. **Prologue emission** (frame setup, callee-saved register spills, stack alignment)
 * 4. **Block-by-block instruction lowering** with ICmp+CondBr fusion optimization
 * 5. **Phi copy insertion** at block boundaries using topological ordering with cycle-breaking
 *    via scratch register (R10/XMM14)
 * 6. **Epilogue emission** (callee-saved register restores, frame teardown)
 * 7. **Exception handling metadata** (FDE entries for `.eh_frame`, LSDA tables, SEH unwind info)
 *
 * Supports both System V AMD64 ABI (Linux/macOS) and Windows x64 ABI (shadow space,
 * different argument registers). ABI selection is automatic based on the module's target triple.
 *
 * The output [CompiledCode] can be converted to an [ObjectFile] and fed to [ElfLinker],
 * [ElfStaticLinker], or [PeLinker] for linking, or to [ElfObjectWriter] for `.o` file output.
 *
 * ```java
 * var codegen = new X86CodeGenerator();
 * CompiledCode code = codegen.generateCode(module);
 * ObjectFile obj = code.toObjectFile(ObjectFormat.ELF, Architecture.X86_64_LINUX);
 * ```
 *
 * See `spec/roadmap.md` for the full list of implemented IR instructions and codegen features.
 */
class X86CodeGenerator : CodeGenerator {

    override val targetName: String = "x86_64"

    /**
     * Generate a complete binary from an IR module.
     *
     * Compiles all functions, applies relocations, and links into the format specified
     * by [options]. For Windows target triples, automatically derives DLL imports from
     * external function declarations.
     */
    override fun generate(module: Module, options: CodeGenOptions): ByteArray {
        val isWindows = module.targetTriple?.contains("windows") == true
        val imports = if (isWindows) deriveWindowsImports(module) else emptyList()
        val obj = generateObjectFile(module, imports)
        return when (options.outputFormat) {
            OutputFormat.OBJECT -> ElfObjectWriter().write(obj)
            OutputFormat.BINARY -> if (isWindows) PeLinker().link(listOf(obj)) else ElfLinker().link(listOf(obj))
            OutputFormat.ASSEMBLY_TEXT -> error("Assembly text output not yet supported")
        }
    }

    /**
     * Compile an IR module to [CompiledCode] without linking.
     *
     * Returns machine code bytes, symbols, relocations, and metadata (stack maps,
     * debug line maps, exception handling tables) that can be assembled into an
     * [ObjectFile] or used directly by the JIT engine.
     */
    override fun generateCode(module: Module): CompiledCode {
        val ctx = CodeGenContext(module)
        ctx.emitGlobals()
        ctx.emitFunctions()
        return ctx.buildCompiledCode()
    }

    private fun deriveWindowsImports(module: Module): List<ImportEntry> {
        val externals = module.functions.filter { it.isExternal }.map { it.name }
        val imports = mutableListOf<ImportEntry>()
        for (name in externals) {
            imports.add(ImportEntry(moduleName = "ucrtbase.dll", symbolName = name))
        }
        // Always add ExitProcess for the entry stub
        imports.add(ImportEntry(moduleName = "kernel32.dll", symbolName = "ExitProcess"))
        return imports
    }

    /**
     * Compile an IR module directly to a relocatable [ObjectFile].
     *
     * Convenience method that calls [generateCode] and wraps the result in an ObjectFile
     * with the appropriate format (ELF or PE/COFF based on the module's target triple).
     *
     * @param module the IR module to compile
     * @param imports PE import entries for Windows targets; ignored for ELF
     */
    fun generateObjectFile(module: Module, imports: List<ImportEntry> = emptyList()): ObjectFile {
        val ctx = CodeGenContext(module, imports)
        ctx.emitGlobals()
        ctx.emitFunctions()
        return ctx.buildCompiledCode().toObjectFile(
            format = if (ctx.isWindows) ObjectFormat.PE_COFF else ObjectFormat.ELF,
            arch = Architecture(ArchType.X86_64),
            imports = imports,
        )
    }

    private class CodeGenContext(val module: Module, val userImports: List<ImportEntry> = emptyList()) {
        val asm = X86Assembler()
        val symbols = mutableListOf<Symbol>()
        val relocations = mutableListOf<Relocation>()
        val rodataBuilder = RodataBuilder()
        val tdataBuilder = TdataBuilder()
        val isWindows = module.targetTriple?.contains("windows") == true
        val stackMaps = mutableListOf<StackMap>()
        val debugLineMapBuilder = DebugLineMap.builder()
        val fdeEntries = mutableListOf<FdeEntry>()
        val lsdaTables = mutableListOf<Pair<String, LsdaTable>>()
        val sehFunctions = mutableListOf<PeExceptionWriter.FunctionUnwind>()

        fun emitGlobals() {
            for (global in module.globals) {
                val init = global.initializer ?: continue
                val data = serializeConstant(init)
                val align = global.align ?: alignForType(global.type)
                if (global.threadLocal != null) {
                    tdataBuilder.addGlobal(global.name, data, align)
                } else {
                    rodataBuilder.addGlobal(global.name, data, align)
                }
            }
        }

        fun emitFunctions() {
            for (fn in module.functions) {
                if (fn.isExternal) {
                    symbols.add(Symbol(fn.name, value = 0, section = null,
                        binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED))
                }
            }
            for (fn in module.functions) {
                if (fn.isExternal) continue
                val funcOffset = asm.position()
                asm.label(fn.name)
                symbols.add(Symbol(fn.name, value = funcOffset.toLong(), section = ".text",
                    binding = if (fn.linkage == Linkage.INTERNAL) SymbolBinding.LOCAL else SymbolBinding.GLOBAL,
                    kind = SymbolKind.FUNCTION))
                FunctionEmitter(fn, this).emit()
            }
        }

        fun buildCompiledCode(): CompiledCode {
            val textBytes = asm.toByteArray()
            val definedSymbols = symbols.filter { it.kind != SymbolKind.UNDEFINED }
            val codeSymbols = definedSymbols.map { sym ->
                CompiledCode.CodeSymbol(
                    name = sym.name,
                    offset = sym.value,
                    kind = sym.kind,
                    isGlobal = sym.binding != SymbolBinding.LOCAL,
                )
            }
            val rodataSymbols = if (rodataBuilder.hasData()) {
                rodataBuilder.symbols().map { sym ->
                    CompiledCode.CodeSymbol(
                        name = sym.name,
                        offset = 0,
                        kind = SymbolKind.DATA,
                        isGlobal = false,
                        rodataOffset = sym.value,
                    )
                }
            } else emptyList()

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
                rodataBuilder.symbols().map { it.name }.toSet() +
                tdataBuilder.symbols().map { it.name }.toSet()
            val externalNames = symbols
                .filter { it.kind == SymbolKind.UNDEFINED }
                .map { it.name }
                .toSet() + relocations
                .map { it.symbol }
                .filter { it !in definedNames }
                .toSet()

            val cie = CieEntry(
                initialInstructions = listOf(
                    CfiInstruction.defCfa(7, 8),    // RSP + 8 (return address on stack)
                    CfiInstruction.offset(16, 1),   // RIP saved at CFA - 8 (factored: 1 * -8)
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

            val sehSections = if (sehFunctions.isNotEmpty()) {
                PeExceptionWriter.write(sehFunctions)
            } else null

            return CompiledCode(
                textBytes = textBytes,
                rodataBytes = if (rodataBuilder.hasData()) rodataBuilder.toByteArray() else ByteArray(0),
                symbols = codeSymbols + rodataSymbols + tdataSymbols,
                relocations = relocations,
                externalSymbols = externalNames,
                rodataAlign = if (rodataBuilder.hasData()) rodataBuilder.maxAlign() else 1,
                stackMaps = stackMaps,
                debugLineMap = debugLineMapBuilder.build(),
                ehFrameBytes = ehFrame,
                exceptTableBytes = exceptTable,
                ehFrameHdrBytes = ehFrameHdr,
                pdataBytes = sehSections?.pdataBytes ?: ByteArray(0),
                xdataBytes = sehSections?.xdataBytes ?: ByteArray(0),
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

    private class RodataBuilder {
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

        fun symbols(): List<Symbol> {
            val result = mutableListOf<Symbol>()
            var offset = 0
            for (entry in entries) {
                while (offset % entry.align != 0) offset++
                result.add(Symbol(entry.name, value = offset.toLong(), size = entry.data.size.toLong(),
                    section = ".rodata", binding = SymbolBinding.LOCAL, kind = SymbolKind.DATA))
                offset += entry.data.size
            }
            return result
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

        /** Get the TLS offset for a global name (offset within .tdata section). */
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

    /**
     * Lowers a single [IrFunction] to x86-64 machine code.
     *
     * Manages the complete per-function pipeline: register allocation, stack layout,
     * prologue/epilogue, instruction selection, phi copy lowering, and exception
     * handling metadata generation (FDE entries for DWARF unwinding, LSDA tables
     * for C++ personality-based EH, and SEH unwind info for Windows).
     */
    private class FunctionEmitter(
        private val fn: IrFunction,
        private val ctx: CodeGenContext,
    ) {
        private val asm = ctx.asm
        private val relocations = ctx.relocations
        private val module = ctx.module
        private val isWindows = ctx.isWindows

        // ABI-specific argument registers
        private val callArgRegs64 = if (isWindows) winArgRegs64 else argRegs64
        private val callArgRegs32 = if (isWindows) winArgRegs32 else argRegs32
        private val callArgRegsXmm = if (isWindows) winCallArgXmm else callArgXmm

        private lateinit var alloc: AllocResult
        private val hasCalls = fn.blocks.any { b -> b.instructions.any { it is Call } }
        // Windows x64 ABI requires 32 bytes of shadow space for every call
        private val shadowSpace = if (isWindows && hasCalls) 32 else 0
        private var currentBlockLabel = ""

        // Pre-computed phi move map: (fromBlock, toBlock) → list of (phiDest, value)
        private val phiMoves: Map<Pair<String, String>, List<Pair<InstructionRef, Value>>> by lazy {
            val map = mutableMapOf<Pair<String, String>, MutableList<Pair<InstructionRef, Value>>>()
            for (block in fn.blocks) {
                for (inst in block.instructions) {
                    if (inst !is Phi) break // phis must be at start of block
                    for ((value, predLabel) in inst.incoming) {
                        map.getOrPut(predLabel.label to block.label) { mutableListOf() }
                            .add(inst.dest to value)
                    }
                }
            }
            map
        }

        // Alloca offsets: name → negative offset from RBP (below spill slots)
        private val allocaOffsets = mutableMapOf<String, Int>()
        private var allocaSize = 0

        // sret: if function returns a struct > 16 bytes, the hidden sret pointer
        // is passed in the first GP arg register and saved to this RBP offset
        private var sretSlotOffset = 0
        private val hasSret: Boolean by lazy {
            val retType = fn.returnType
            retType is Type.Struct && typeSizeBytes(retType) > 16
        }

        // Varargs: register save area for callee-side vararg functions (System V AMD64)
        // Layout: 6 GP regs (48 bytes) at regSaveAreaOffset from RBP
        private var regSaveAreaOffset = 0
        private val namedGpParams: Int by lazy {
            fn.params.count { !isFloatType(it.type) }
        }
        private val namedFpParams: Int by lazy {
            fn.params.count { isFloatType(it.type) }
        }
        private fun isFloatType(type: Type) = type == Type.F32 || type == Type.F64

        // Stack map: entries collected at each GCSafepoint
        private val stackMapEntries = mutableListOf<StackMapEntry>()
        // Explicit GC roots declared via GCRoot instruction
        private val gcRoots = mutableSetOf<String>()
        private val hasGcStrategy: Boolean = fn.gc != null && fn.gc != "none"
        // Offset of the function start in the text section
        private var funcStartOffset = 0

        // Exception handling: call site entries for LSDA generation
        private data class EhCallSite(
            val callOffset: Int,       // relative to function start
            val callLength: Int,
            val landingPadLabel: String,
            val actionIndex: Int,      // 1-based into action table, 0 = cleanup
        )
        private val ehCallSites = mutableListOf<EhCallSite>()
        private val ehTypeNames = mutableListOf<String>()
        private val hasExceptionHandling: Boolean = fn.blocks.any { b ->
            b.instructions.any { it is Invoke || it is LandingPad }
        }

        /**
         * Run the full code generation pipeline for this function.
         *
         * Executes register allocation, computes stack layout, emits prologue/blocks/epilogue,
         * and records exception handling metadata (stack maps, FDE, LSDA, SEH unwind).
         */
        fun emit() {
            funcStartOffset = asm.position()
            runRegisterAllocator()
            computeAllocaOffsets()
            emitPrologue()
            emitBlocks()
            if (hasGcStrategy && stackMapEntries.isNotEmpty()) {
                ctx.stackMaps.add(StackMap(fn.name, stackMapEntries.toList()))
            }
            val funcEnd = asm.position()
            ctx.fdeEntries.add(buildFde(funcStartOffset, funcEnd - funcStartOffset))
            if (ehCallSites.isNotEmpty()) {
                buildLsda()
            }
            if (isWindows) {
                ctx.sehFunctions.add(buildSehUnwind(funcStartOffset, funcEnd))
            }
        }

        private fun buildLsda() {
            // Resolve landing pad labels to offsets relative to function start
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

        private fun buildFde(startOffset: Int, length: Int): FdeEntry {
            val cfi = mutableListOf<CfiInstruction>()
            // After push rbp (1 byte): CFA = RSP + 16, RBP saved at CFA - 16
            cfi.add(CfiInstruction.advanceLoc(1))
            cfi.add(CfiInstruction.defCfaOffset(16))
            cfi.add(CfiInstruction.offset(6, 2)) // RBP at CFA - 16 (factored: 2 * -8)
            // After mov rbp, rsp (3 bytes): CFA = RBP + 16
            cfi.add(CfiInstruction.advanceLoc(3))
            cfi.add(CfiInstruction.defCfaRegister(6))
            // Callee-saved registers pushed after mov rbp,rsp
            var pushOffset = 0
            for (reg in alloc.usedCalleeRegs64) {
                pushOffset++
                cfi.add(CfiInstruction.advanceLoc(2)) // push r64 is typically 2 bytes
                val dwarfReg = x86ToDwarfRegister(reg)
                // Saved at CFA - (16 + pushOffset * 8), factored offset = 2 + pushOffset
                cfi.add(CfiInstruction.offset(dwarfReg, 2 + pushOffset))
            }
            return FdeEntry(fn.name, startOffset.toLong(), length.toLong(), cfi)
        }

        private fun buildSehUnwind(funcStart: Int, funcEnd: Int): PeExceptionWriter.FunctionUnwind {
            val codes = mutableListOf<UnwindCode>()
            var offset = 1 // push rbp is 1 byte
            codes.add(UnwindCode(offset, UnwindOperation.PUSH_NONVOL, 5)) // RBP = 5

            offset += 3 // mov rbp, rsp is 3 bytes
            codes.add(UnwindCode(offset, UnwindOperation.SET_FPREG, 0))

            for (reg in alloc.usedCalleeRegs64) {
                offset += 2 // push r64 is typically 2 bytes (REX + 0x50+r)
                codes.add(UnwindCode(offset, UnwindOperation.PUSH_NONVOL, x86ToSehRegister(reg)))
            }

            if (stackReserve > 0) {
                val subInsnSize = if (stackReserve <= 127) 4 else 7
                offset += subInsnSize
                if (stackReserve <= 128) {
                    codes.add(UnwindCode(offset, UnwindOperation.ALLOC_SMALL, (stackReserve / 8) - 1))
                } else if (stackReserve <= 512 * 1024 - 8) {
                    codes.add(UnwindCode(offset, UnwindOperation.ALLOC_LARGE, 0, stackReserve.toLong()))
                } else {
                    codes.add(UnwindCode(offset, UnwindOperation.ALLOC_LARGE, 1, stackReserve.toLong()))
                }
            }

            val unwindInfo = PeUnwindInfo(
                version = 1,
                flags = if (hasExceptionHandling) PeUnwindInfo.UNW_FLAG_EHANDLER else 0,
                prologSize = offset,
                frameRegister = 5, // RBP
                frameOffset = 0,
                unwindCodes = codes.reversed(), // highest offset first per PE spec
            )
            return PeExceptionWriter.FunctionUnwind(fn.name, funcStart, funcEnd, unwindInfo)
        }

        private fun x86ToSehRegister(reg: X86Register64): Int = when (reg) {
            X86Register.RAX -> 0
            X86Register.RCX -> 1
            X86Register.RDX -> 2
            X86Register.RBX -> 3
            X86Register.RSP -> 4
            X86Register.RBP -> 5
            X86Register.RSI -> 6
            X86Register.RDI -> 7
            X86Register.R8 -> 8
            X86Register.R9 -> 9
            X86Register.R10 -> 10
            X86Register.R11 -> 11
            X86Register.R12 -> 12
            X86Register.R13 -> 13
            X86Register.R14 -> 14
            X86Register.R15 -> 15
            else -> 0
        }

        private fun x86ToDwarfRegister(reg: X86Register64): Int = when (reg) {
            X86Register.RAX -> 0
            X86Register.RDX -> 1
            X86Register.RCX -> 2
            X86Register.RBX -> 3
            X86Register.RSI -> 4
            X86Register.RDI -> 5
            X86Register.RBP -> 6
            X86Register.RSP -> 7
            X86Register.R8 -> 8
            X86Register.R9 -> 9
            X86Register.R10 -> 10
            X86Register.R11 -> 11
            X86Register.R12 -> 12
            X86Register.R13 -> 13
            X86Register.R14 -> 14
            X86Register.R15 -> 15
            else -> 0
        }

        private fun computeAllocaOffsets() {
            // Start after callee-saved pushes + spill slots
            var offset = alloc.usedCalleeRegs64.size * 8 + alloc.spillSlots * 8

            // Reserve a slot for sret pointer if needed
            if (hasSret) {
                offset += 8
                sretSlotOffset = -offset
            }

            // Reserve register save area for vararg functions (System V)
            if (fn.isVarArg && !isWindows) {
                // 6 GP registers × 8 bytes = 48 bytes
                offset = (offset + 7) and 7.inv() // align to 8
                offset += 48
                regSaveAreaOffset = -offset
            }

            for (block in fn.blocks) {
                for (inst in block.instructions) {
                    if (inst is Alloca) {
                        val size = typeSizeBytes(inst.allocType)
                        val count = when (val n = inst.numElements) {
                            is Constant.I32 -> n.value
                            is Constant.I64 -> n.value.toInt()
                            null -> 1
                            else -> 1
                        }
                        val totalSize = size * count
                        val align = inst.align ?: maxOf(size, 1)
                        offset = (offset + align - 1) and (align - 1).inv()
                        offset += totalSize
                        allocaOffsets[inst.dest.name] = -offset
                    }
                }
            }
            allocaSize = offset - alloc.usedCalleeRegs64.size * 8 - alloc.spillSlots * 8
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

        private fun runRegisterAllocator() {
            alloc = LinearScanAllocator(
                fn,
                availableRegs64 = allocatableRegs64.toList(),
                availableRegs32 = allocatableRegs32.toList(),
                availableXmm = allocatableXmm.toList(),
                calleeSaved64 = calleeSavedSet64,
                calleeSaved32 = calleeSavedSet32,
                paramRegs64 = callArgRegs64,
                paramRegs32 = callArgRegs32,
                paramXmm = callArgXmm,
                gpParamOffset = if (hasSret) 1 else 0,
            ).allocate()

            // Adjust spill offsets past the callee-saved register push area.
            // Callee-saved regs are pushed right after `mov rbp, rsp`, occupying
            // [rbp-8], [rbp-16], etc. Spill slots must start below them.
            val calleePushSize = alloc.usedCalleeRegs64.size * 8
            if (calleePushSize > 0) {
                val adjusted = alloc.locations.mapValues { (_, loc) ->
                    if (loc is Location.Spill) Location.Spill(loc.offset - calleePushSize)
                    else loc
                }
                alloc = alloc.copy(locations = adjusted)
            }
        }

        /**
         * Emit the function prologue: frame pointer setup, callee-saved register spills,
         * stack reservation (spill slots + alloca + shadow space), and argument register
         * saves for vararg functions. Ensures 16-byte RSP alignment for calls.
         */
        private fun emitPrologue() {
            asm.push(rbp64)
            asm.mov(rbp64, rsp64 as X86Operand64)

            // Save callee-saved registers
            for (reg in alloc.usedCalleeRegs64) {
                asm.push(reg)
            }

            // Reserve stack: spill slots + alloca space + shadow space, aligned to 16 bytes
            val spillSize = alloc.spillSlots * 8
            val pushCount = 1 + alloc.usedCalleeRegs64.size // rbp + callee-saved
            val totalFrameBeforeSub = pushCount * 8 + 8 // pushes + return address
            val needed = spillSize + allocaSize + shadowSpace
            // Align to 16: after sub rsp, RSP must be 16-byte aligned for calls
            // Even with needed=0, if the push count makes RSP misaligned and we have calls, fix it
            val stackReserve = if (needed > 0 || (hasCalls && totalFrameBeforeSub % 16 != 0)) {
                val total = totalFrameBeforeSub + needed
                val aligned = (total + 15) and 15.inv()
                aligned - totalFrameBeforeSub
            } else {
                0
            }
            if (stackReserve > 0) {
                asm.sub(rsp64 as X86Operand64, stackReserve)
            }
            this.stackReserve = stackReserve

            // Save the hidden sret pointer if this function returns a large struct
            if (hasSret) {
                val sretArgReg = callArgRegs64[0] // RDI (System V) or RCX (Windows)
                emitStoreToRbp64(sretArgReg, sretSlotOffset)
            }

            // Save all GP arg registers for vararg functions (System V)
            if (fn.isVarArg && !isWindows) {
                for (i in 0 until 6) {
                    emitStoreToRbp64(argRegs64[i], regSaveAreaOffset + i * 8)
                }
            }

            // Move parameters from ABI registers to their allocated locations
            // (for params that live across calls and were assigned callee-saved regs)
            for ((paramName, abiIdx) in alloc.paramMoves) {
                val param = fn.params.first { it.name == paramName }
                val loc = alloc.locations[paramName] ?: continue
                when {
                    isFloatType(param.type) -> {
                        val srcReg = callArgRegsXmm[abiIdx]
                        when (loc) {
                            is Location.RegXmm -> if (loc.reg != srcReg) asm.movsd(loc.reg, srcReg)
                            is Location.Spill -> {
                                asm.movsd(xmm15, srcReg)
                                emitStoreXmmToStack(xmm15, loc.offset)
                            }
                            else -> {}
                        }
                    }
                    param.type == Type.I64 || param.type == Type.OpaquePointer || param.type is Type.Pointer -> {
                        val srcReg = callArgRegs64[abiIdx]
                        when (loc) {
                            is Location.Reg64 -> if (loc.reg != srcReg) asm.mov(loc.reg, srcReg as X86Operand64)
                            is Location.Spill -> emitStoreToStack64(srcReg, loc.offset)
                            else -> {}
                        }
                    }
                    else -> {
                        val srcReg = callArgRegs32[abiIdx]
                        when (loc) {
                            is Location.Reg32 -> if (loc.reg != srcReg) asm.mov(loc.reg, srcReg as X86Operand32)
                            is Location.Spill -> emitStoreToStack32(srcReg, loc.offset)
                            else -> {}
                        }
                    }
                }
            }
        }

        private var stackReserve = 0

        private fun emitEpilogue(omitRet: Boolean = false) {
            if (stackReserve > 0) {
                asm.add(rsp64 as X86Operand64, stackReserve)
            }
            // Restore callee-saved registers in reverse order
            for (reg in alloc.usedCalleeRegs64.reversed()) {
                asm.pop(reg)
            }
            asm.pop(rbp64)
            if (!omitRet) asm.ret()
        }

        /**
         * Emit machine code for all basic blocks in the function.
         *
         * Iterates blocks in IR order, emitting labels and lowering each instruction.
         * Applies an ICmp+CondBr fusion optimization: when an ICmp is immediately
         * followed by a CondBr that consumes its result, the comparison and branch
         * are emitted as a single fused sequence (cmp + jcc) without materializing
         * the boolean result to a register.
         */
        private fun emitBlocks() {
            for ((blockIdx, block) in fn.blocks.withIndex()) {
                currentBlockLabel = block.label
                asm.label("${fn.name}.${block.label}")
                val nextBlockLabel = fn.blocks.getOrNull(blockIdx + 1)?.label
                for ((instIdx, inst) in block.instructions.withIndex()) {
                    // Skip phi nodes — they are lowered to copies at predecessor terminators
                    if (inst is Phi) continue
                    // Fuse ICmp + CondBr: skip materializing the ICmp result if it's
                    // only used by the immediately following CondBr
                    if (inst is ICmp) {
                        val nextInst = block.instructions.getOrNull(instIdx + 1)
                        if (nextInst is CondBr && nextInst.condition.name == inst.dest.name) {
                            emitFusedCmpBranch(inst, nextInst, nextBlockLabel)
                            break // CondBr is a terminator, nothing after it
                        }
                    }
                    if (inst is CondBr) {
                        // Already handled by fusion above; if we get here, the condition
                        // was materialized by a prior instruction (not fused)
                        emitCondBr(inst, nextBlockLabel)
                        continue
                    }
                    emitInstruction(inst)
                }
            }
        }

        // Location helpers — get register or load from spill slot

        private fun getOrLoad64(name: String, scratch: X86Register64 = r11_64): X86Register64 {
            return when (val loc = alloc.locations[name]) {
                is Location.Reg64 -> loc.reg
                is Location.Spill -> {
                    // Load from stack: mov scratch, [rbp + offset]
                    emitLoadFromStack64(scratch, loc.offset)
                    scratch
                }
                else -> error("No allocation for $name")
            }
        }

        private fun getOrLoad32(name: String, scratch: X86Register32 = r11d32): X86Register32 {
            return when (val loc = alloc.locations[name]) {
                is Location.Reg32 -> loc.reg
                is Location.Spill -> {
                    emitLoadFromStack32(scratch, loc.offset)
                    scratch
                }
                else -> error("No allocation for $name")
            }
        }

        private fun storeTo(name: String, reg64: X86Register64? = null, reg32: X86Register32? = null) {
            when (val loc = alloc.locations[name]) {
                is Location.Reg64 -> {
                    if (reg64 != null && reg64 != loc.reg) asm.mov(loc.reg, reg64 as X86Operand64)
                }
                is Location.Reg32 -> {
                    if (reg32 != null && reg32 != loc.reg) asm.mov(loc.reg, reg32 as X86Operand32)
                }
                is Location.Spill -> {
                    if (reg64 != null) emitStoreToStack64(reg64, loc.offset)
                    else if (reg32 != null) emitStoreToStack32(reg32, loc.offset)
                }
                else -> {}
            }
        }

        private fun emitLoadFromStack64(dest: X86Register64, offset: Int) {
            // mov dest, [rbp + offset]
            val enc = (dest as X86Register).encoding
            val rex = 0x48 or ((enc shr 3) and 1).shl(2)
            asm.emitByte(rex)
            asm.emitByte(0x8B)
            asm.emitByte(0x85 or ((enc and 7) shl 3)) // ModRM: mod=10 (disp32), rm=101 (rbp)
            asm.emitInt32(offset)
        }

        private fun emitStoreToStack64(src: X86Register64, offset: Int) {
            // mov [rbp + offset], src
            val enc = (src as X86Register).encoding
            val rex = 0x48 or ((enc shr 3) and 1).shl(2)
            asm.emitByte(rex)
            asm.emitByte(0x89)
            asm.emitByte(0x85 or ((enc and 7) shl 3))
            asm.emitInt32(offset)
        }

        private fun emitLoadFromStack32(dest: X86Register32, offset: Int) {
            val enc = (dest as X86Register).encoding
            if (enc >= 8) asm.emitByte(0x44)
            asm.emitByte(0x8B)
            asm.emitByte(0x85 or ((enc and 7) shl 3))
            asm.emitInt32(offset)
        }

        private fun emitStoreToStack32(src: X86Register32, offset: Int) {
            val enc = (src as X86Register).encoding
            if (enc >= 8) asm.emitByte(0x44)
            asm.emitByte(0x89)
            asm.emitByte(0x85 or ((enc and 7) shl 3))
            asm.emitInt32(offset)
        }

        private fun getDest64(name: String): X86Register64 {
            return when (val loc = alloc.locations[name]) {
                is Location.Reg64 -> loc.reg
                is Location.Spill -> r11_64 // use scratch, caller must store
                else -> error("No allocation for $name")
            }
        }

        private fun getDest32(name: String): X86Register32 {
            return when (val loc = alloc.locations[name]) {
                is Location.Reg32 -> loc.reg
                is Location.Spill -> r11d32
                else -> error("No allocation for $name")
            }
        }

        private fun isSpilled(name: String): Boolean = alloc.locations[name] is Location.Spill

        private fun getDestXmm(name: String): X86Xmm {
            return when (val loc = alloc.locations[name]) {
                is Location.RegXmm -> loc.reg
                is Location.Spill -> xmm15  // scratch XMM
                else -> error("No XMM allocation for $name")
            }
        }

        private fun getOrLoadXmm(name: String, scratch: X86Xmm = xmm15): X86Xmm {
            return when (val loc = alloc.locations[name]) {
                is Location.RegXmm -> loc.reg
                is Location.Spill -> {
                    emitLoadXmmFromStack(scratch, loc.offset)
                    scratch
                }
                else -> error("No XMM allocation for $name")
            }
        }

        private fun storeToXmm(name: String, reg: X86Xmm) {
            when (val loc = alloc.locations[name]) {
                is Location.RegXmm -> {
                    if (reg != loc.reg) asm.movsd(loc.reg, reg)
                }
                is Location.Spill -> emitStoreXmmToStack(reg, loc.offset)
                else -> {}
            }
        }

        private fun emitLoadXmmFromStack(dest: X86Xmm, offset: Int) {
            // movsd dest, [rbp + offset] — F2 0F 10 /r with ModRM
            val dEnc = (dest as X86Register).encoding
            asm.emitByte(0xF2)
            if (dEnc >= 8) asm.emitByte(0x44)
            asm.emitBytes(0x0F, 0x10)
            asm.emitByte(0x85 or ((dEnc and 7) shl 3)) // mod=10, rm=rbp
            asm.emitInt32(offset)
        }

        private fun emitStoreXmmToStack(src: X86Xmm, offset: Int) {
            // movsd [rbp + offset], src — F2 0F 11 /r
            val sEnc = (src as X86Register).encoding
            asm.emitByte(0xF2)
            if (sEnc >= 8) asm.emitByte(0x44)
            asm.emitBytes(0x0F, 0x11)
            asm.emitByte(0x85 or ((sEnc and 7) shl 3))
            asm.emitInt32(offset)
        }

        private fun emitStoreXmmToRsp(src: X86Xmm) {
            // movsd [rsp], src — F2 0F 11 /r with ModRM for [rsp] (SIB needed)
            val sEnc = (src as X86Register).encoding
            asm.emitByte(0xF2)
            if (sEnc >= 8) asm.emitByte(0x44)
            asm.emitBytes(0x0F, 0x11)
            asm.emitByte(0x04 or ((sEnc and 7) shl 3)) // ModRM: mod=00, rm=100 (SIB)
            asm.emitByte(0x24) // SIB: base=RSP, index=none, scale=1
        }

        private fun emitInstruction(inst: Instruction) {
            when (inst) {
                is Add -> emitIntBinOp(inst.dest, inst.lhs, inst.rhs,
                    { d, rhs -> asm.add(d, rhs as X86Operand32) }, { d, imm -> asm.add(d as X86Operand32, imm) },
                    { d, rhs -> asm.add(d, rhs as X86Operand64) }, { d, imm -> asm.add(d as X86Operand64, imm) })

                is Sub -> emitIntBinOp(inst.dest, inst.lhs, inst.rhs,
                    { d, rhs -> asm.sub(d, rhs as X86Operand32) }, { d, imm -> asm.sub(d as X86Operand32, imm) },
                    { d, rhs -> asm.sub(d, rhs as X86Operand64) }, { d, imm -> asm.sub(d as X86Operand64, imm) })

                is Mul -> emitMul(inst)
                is SDiv -> emitDiv(inst.dest, inst.lhs, inst.rhs, signed = true, remainder = false)
                is UDiv -> emitDiv(inst.dest, inst.lhs, inst.rhs, signed = false, remainder = false)
                is SRem -> emitDiv(inst.dest, inst.lhs, inst.rhs, signed = true, remainder = true)
                is URem -> emitDiv(inst.dest, inst.lhs, inst.rhs, signed = false, remainder = true)

                is And -> emitIntBinOp(inst.dest, inst.lhs, inst.rhs,
                    { d, rhs -> asm.and_(d, rhs as X86Operand32) }, { d, imm -> asm.and_(d as X86Operand32, imm) },
                    { d, rhs -> asm.and_(d, rhs as X86Operand64) }, { d, imm -> asm.and_(d as X86Operand64, imm) })

                is Or -> emitIntBinOp(inst.dest, inst.lhs, inst.rhs,
                    { d, rhs -> asm.or_(d, rhs as X86Operand32) }, { d, imm -> asm.or_(d as X86Operand32, imm) },
                    { d, rhs -> asm.or_(d, rhs as X86Operand64) }, { d, imm -> asm.or_(d as X86Operand64, imm) })

                is Xor -> emitIntBinOp(inst.dest, inst.lhs, inst.rhs,
                    { d, rhs -> asm.xor_(d, rhs as X86Operand32) }, { d, imm -> asm.xor_(d as X86Operand32, imm) },
                    { d, rhs -> asm.xor_(d, rhs as X86Operand64) }, { d, imm -> asm.xor_(d as X86Operand64, imm) })

                is Shl -> emitShift(inst.dest, inst.lhs, inst.rhs,
                    { rm -> asm.shl(rm as X86Operand32, cl8) }, { rm, imm -> asm.shl(rm as X86Operand32, imm) },
                    { rm -> asm.shl(rm as X86Operand64, cl8) }, { rm, imm -> asm.shl(rm as X86Operand64, imm) })

                is LShr -> emitShift(inst.dest, inst.lhs, inst.rhs,
                    { rm -> asm.shr(rm as X86Operand32, cl8) }, { rm, imm -> asm.shr(rm as X86Operand32, imm) },
                    { rm -> asm.shr(rm as X86Operand64, cl8) }, { rm, imm -> asm.shr(rm as X86Operand64, imm) })

                is AShr -> emitShift(inst.dest, inst.lhs, inst.rhs,
                    { rm -> asm.sar(rm as X86Operand32, cl8) }, { rm, imm -> asm.sar(rm as X86Operand32, imm) },
                    { rm -> asm.sar(rm as X86Operand64, cl8) }, { rm, imm -> asm.sar(rm as X86Operand64, imm) })

                is Rotl -> emitShift(inst.dest, inst.value, inst.amount,
                    { rm -> asm.rol(rm as X86Operand32, cl8) }, { rm, imm -> asm.rol(rm as X86Operand32, imm) },
                    { rm -> asm.rol(rm as X86Operand64, cl8) }, { rm, imm -> asm.rol(rm as X86Operand64, imm) })

                is Rotr -> emitShift(inst.dest, inst.value, inst.amount,
                    { rm -> asm.ror(rm as X86Operand32, cl8) }, { rm, imm -> asm.ror(rm as X86Operand32, imm) },
                    { rm -> asm.ror(rm as X86Operand64, cl8) }, { rm, imm -> asm.ror(rm as X86Operand64, imm) })

                is Neg -> emitNeg(inst)
                is Not -> emitNot(inst)
                is ZExt -> emitZExt(inst)
                is SExt -> emitSExt(inst)
                is FTrunc -> emitTrunc(inst.dest, inst.operand, inst.dest.type)
                is IntTrunc -> emitTrunc(inst.dest, inst.value, inst.toType)
                is PtrToInt -> emitCopy64(inst.dest, inst.value)
                is IntToPtr -> emitCopy64(inst.dest, inst.value)
                is BitCast -> emitBitCast(inst)
                is Alloca -> emitAlloca(inst)
                is Load -> emitLoad(inst)
                is Store -> emitStore(inst)

                is FAdd -> emitFBinOp(inst.dest, inst.lhs, inst.rhs) { d, s ->
                    if (inst.dest.type == Type.F32) asm.addss(d, s) else asm.addsd(d, s)
                }
                is FSub -> emitFBinOp(inst.dest, inst.lhs, inst.rhs) { d, s ->
                    if (inst.dest.type == Type.F32) asm.subss(d, s) else asm.subsd(d, s)
                }
                is FMul -> emitFBinOp(inst.dest, inst.lhs, inst.rhs) { d, s ->
                    if (inst.dest.type == Type.F32) asm.mulss(d, s) else asm.mulsd(d, s)
                }
                is FDiv -> emitFBinOp(inst.dest, inst.lhs, inst.rhs) { d, s ->
                    if (inst.dest.type == Type.F32) asm.divss(d, s) else asm.divsd(d, s)
                }
                is FNeg -> emitFNeg(inst)
                is FCmp -> emitFCmp(inst)
                is SIToFP -> emitSIToFP(inst)
                is UIToFP -> emitUIToFP(inst)
                is FPToSI -> emitFPToSI(inst)
                is FPToUI -> emitFPToUI(inst)
                is FPExt -> emitFPExt(inst)
                is FPTrunc -> emitFPTrunc(inst)

                is Ret -> emitReturn(inst)
                is Call -> emitCall(inst)
                is ICmp -> emitICmp(inst)
                is GetElementPtr -> emitGetElementPtr(inst)
                is ExtractValue -> emitExtractValue(inst)
                is InsertValue -> emitInsertValue(inst)
                is Br -> emitBr(inst)
                is IndirectBr -> emitIndirectBr(inst)
                is Switch -> emitSwitch(inst)
                is Select -> emitSelect(inst)

                is VAEnd -> {}
                is VAStart -> emitVAStart(inst)
                is VAArg -> emitVAArg(inst)
                is VACopy -> emitVACopy(inst)

                is GCSafepoint -> emitGCSafepoint()
                is GCRoot -> emitGCRoot(inst)

                is Ctlz -> emitCtlz(inst)
                is Cttz -> emitCttz(inst)
                is Ctpop -> emitCtpop(inst)
                is BSwap -> emitBSwap(inst)
                is BitReverse -> emitBitReverse(inst)

                is Sqrt -> emitFpUnaryXmm(inst.dest, inst.operand) { d, s -> asm.sqrtsd(d, s) }
                is Ceil -> emitFpUnaryXmm(inst.dest, inst.operand) { d, s -> asm.roundsd(d, s, 0x02) }
                is Floor -> emitFpUnaryXmm(inst.dest, inst.operand) { d, s -> asm.roundsd(d, s, 0x01) }
                is Round -> emitFpUnaryXmm(inst.dest, inst.operand) { d, s -> asm.roundsd(d, s, 0x00) }
                is FAbs -> emitFAbs(inst)
                is FMin -> emitFBinOp(inst.dest, inst.lhs, inst.rhs) { d, s ->
                    if (inst.dest.type == Type.F32) asm.minss(d, s) else asm.minsd(d, s)
                }
                is FMax -> emitFBinOp(inst.dest, inst.lhs, inst.rhs) { d, s ->
                    if (inst.dest.type == Type.F32) asm.maxss(d, s) else asm.maxsd(d, s)
                }
                is CopySign -> emitCopySign(inst)

                is SMin -> emitIntMinMax(inst.dest, inst.lhs, inst.rhs, signed = true, isMin = true)
                is SMax -> emitIntMinMax(inst.dest, inst.lhs, inst.rhs, signed = true, isMin = false)
                is UMin -> emitIntMinMax(inst.dest, inst.lhs, inst.rhs, signed = false, isMin = true)
                is UMax -> emitIntMinMax(inst.dest, inst.lhs, inst.rhs, signed = false, isMin = false)

                is MemCpy -> emitRepMovsb(inst.dst, inst.src, inst.len)
                is MemSet -> emitMemSet(inst)
                is MemMove -> emitRepMovsb(inst.dst, inst.src, inst.len)

                is Fence -> {
                    when (inst.ordering) {
                        AtomicOrdering.ACQUIRE -> asm.lfence()
                        AtomicOrdering.RELEASE -> asm.sfence()
                        else -> asm.mfence()
                    }
                }

                is FRem -> emitFRem(inst)
                is FMA -> emitFMA(inst)

                is SAddSat -> emitSatArith(inst.dest, inst.lhs, inst.rhs, signed = true, isAdd = true)
                is UAddSat -> emitSatArith(inst.dest, inst.lhs, inst.rhs, signed = false, isAdd = true)
                is SSubSat -> emitSatArith(inst.dest, inst.lhs, inst.rhs, signed = true, isAdd = false)
                is USubSat -> emitSatArith(inst.dest, inst.lhs, inst.rhs, signed = false, isAdd = false)

                is SAddOverflow -> emitOverflowArith(inst.dest, inst.lhs, inst.rhs, signed = true, op = "add")
                is UAddOverflow -> emitOverflowArith(inst.dest, inst.lhs, inst.rhs, signed = false, op = "add")
                is SSubOverflow -> emitOverflowArith(inst.dest, inst.lhs, inst.rhs, signed = true, op = "sub")
                is USubOverflow -> emitOverflowArith(inst.dest, inst.lhs, inst.rhs, signed = false, op = "sub")
                is SMulOverflow -> emitOverflowArith(inst.dest, inst.lhs, inst.rhs, signed = true, op = "mul")
                is UMulOverflow -> emitOverflowArith(inst.dest, inst.lhs, inst.rhs, signed = false, op = "mul")

                is AtomicRMW -> emitAtomicRMW(inst)
                is CmpXchg -> emitCmpXchg(inst)

                is Invoke -> emitInvoke(inst)
                is CallBr -> emitCallBr(inst)
                is LandingPad -> emitLandingPad(inst)
                is Resume -> emitResume(inst)
                is Throw -> emitThrow(inst)

                is Unreachable -> asm.ud2()
                is Trap -> asm.int3()
                is DebugTrap -> asm.int3()

                is DebugLoc -> {
                    ctx.debugLineMapBuilder.add(
                        codeOffset = asm.position().toLong(),
                        file = inst.scope,
                        line = inst.line,
                        column = inst.col,
                        scope = inst.scope,
                        inlinedAt = inst.inlinedAt,
                    )
                }
                is DebugValue -> {} // no-op at native level
                is DebugDeclare -> {} // no-op at native level

                else -> error("Unsupported IR instruction for x86-64: ${inst::class.simpleName}")
            }
        }

        private fun emitReturn(inst: Ret) {
            val retVal = inst.value
            if (retVal != null) {
                when (retVal.type) {
                    Type.I32 -> loadValue32(retVal, eax32)
                    Type.I64, Type.OpaquePointer, is Type.Pointer -> loadValue64(retVal, rax64)
                    Type.F32 -> {
                        val src = if (retVal is Parameter || retVal is InstructionRef) {
                            getOrLoadXmm(retVal.name, xmm0)
                        } else {
                            loadValueXmm(retVal, xmm0); xmm0
                        }
                        if (src != xmm0) asm.movss(xmm0, src)
                    }
                    Type.F64 -> {
                        val src = if (retVal is Parameter || retVal is InstructionRef) {
                            getOrLoadXmm(retVal.name, xmm0)
                        } else {
                            loadValueXmm(retVal, xmm0); xmm0
                        }
                        if (src != xmm0) asm.movsd(xmm0, src)
                    }
                    is Type.Struct -> emitStructReturn(retVal)
                    else -> {}
                }
            }
            emitEpilogue()
        }

        private fun emitStructReturn(retVal: Value) {
            val structType = retVal.type as Type.Struct
            val size = typeSizeBytes(structType)

            if (size <= 16) {
                val loc = alloc.locations[retVal.name]
                if (loc is Location.Spill) {
                    emitLoadFromRbp64(rax64, loc.offset)
                    if (size > 8) emitLoadFromRbp64(rdx64, loc.offset + 8)
                } else {
                    error("Struct return value must be spilled")
                }
            } else {
                // Large struct: hidden sret pointer was saved in prologue
                emitLoadFromRbp64(rax64, sretSlotOffset)

                val srcLoc = alloc.locations[retVal.name]
                if (srcLoc is Location.Spill) {
                    emitStructCopy(rax64, srcLoc.offset, size)
                } else {
                    error("Large struct return value must be spilled")
                }
                // ABI: return the sret pointer in RAX (already there)
            }
        }

        private fun emitStructCopy(destPtr: X86Register64, srcRbpOffset: Int, size: Int) {
            // Copy in 8-byte chunks. The spill area is 8-byte aligned and the
            // destination is caller-owned, so writing up to 7 extra bytes is safe.
            val scratch = if (destPtr == r11_64) r10_64 else r11_64
            val roundedSize = (size + 7) and 7.inv()
            var offset = 0
            while (offset < roundedSize) {
                emitLoadFromRbp64(scratch, srcRbpOffset + offset)
                asm.mov(X86Memory.base(destPtr).offset(offset), scratch)
                offset += 8
            }
        }

        private fun emitCall(inst: Call) {
            // System V: GP and XMM args use independent counters
            // Windows: GP and XMM share the same slot index
            var gpIdx = 0
            var xmmIdx = 0
            val stackArgs = mutableListOf<Pair<Value, Int>>() // (arg, stack offset)
            var stackArgsSize = 0

            // Large struct return: pass hidden sret pointer as first GP arg
            val dest = inst.dest
            val callerSret = dest != null && dest.type is Type.Struct && typeSizeBytes(dest.type) > 16
            if (callerSret) {
                val loc = alloc.locations[dest!!.name]
                if (loc is Location.Spill) {
                    // LEA first_arg_reg, [RBP + spill_offset]
                    asm.lea(callArgRegs64[0], X86Memory.base(rbp64).offset(loc.offset))
                } else {
                    error("Large struct call result must be spilled")
                }
                gpIdx = 1 // first GP slot consumed by sret
            }

            for (arg in inst.args) {
                when {
                    arg.type == Type.F64 || arg.type == Type.F32 -> {
                        if (isWindows) {
                            if (gpIdx < callArgRegsXmm.size) {
                                loadValueXmm(arg, callArgRegsXmm[gpIdx])
                            } else {
                                stackArgs.add(arg to stackArgsSize)
                                stackArgsSize += 8
                            }
                            gpIdx++
                        } else {
                            if (xmmIdx < callArgRegsXmm.size) {
                                loadValueXmm(arg, callArgRegsXmm[xmmIdx])
                            } else {
                                stackArgs.add(arg to stackArgsSize)
                                stackArgsSize += 8
                            }
                            xmmIdx++
                        }
                    }
                    else -> {
                        if (gpIdx < callArgRegs64.size) {
                            when (arg.type) {
                                Type.I32, Type.I16, Type.I8, Type.I1 -> loadValue32(arg, callArgRegs32[gpIdx])
                                Type.I64, Type.OpaquePointer, is Type.Pointer -> loadValue64(arg, callArgRegs64[gpIdx])
                                is Type.Array -> loadValue64(arg, callArgRegs64[gpIdx])
                                else -> error("Unsupported arg type: ${arg.type}")
                            }
                        } else {
                            stackArgs.add(arg to stackArgsSize)
                            stackArgsSize += 8
                        }
                        gpIdx++
                    }
                }
            }

            // Align stack args to 16 bytes
            if (stackArgsSize > 0) {
                val aligned = (stackArgsSize + 15) and 15.inv()
                if (aligned > stackArgsSize) {
                    asm.sub(rsp64 as X86Operand64, aligned - stackArgsSize)
                }
                // Push stack args in reverse order (right-to-left)
                for ((arg, _) in stackArgs.reversed()) {
                    when {
                        arg.type == Type.F64 || arg.type == Type.F32 -> {
                            // sub rsp, 8; movsd [rsp], xmm_scratch
                            asm.sub(rsp64 as X86Operand64, 8)
                            loadValueXmm(arg, xmm15)
                            emitStoreXmmToRsp(xmm15)
                        }
                        arg.type == Type.I32 || arg.type == Type.I16 || arg.type == Type.I8 || arg.type == Type.I1 -> {
                            loadValue64(arg, r11_64) // zero-extend to 64-bit for stack slot
                            asm.push(r11_64)
                        }
                        else -> {
                            loadValue64(arg, r11_64)
                            asm.push(r11_64)
                        }
                    }
                }
            }

            // System V ABI: vararg calls must set AL to the number of XMM registers used
            val isVararg = when (val f = inst.function) {
                is FunctionRef -> f.type.vararg
                else -> module.functions.firstOrNull { it.name == (f as? GlobalRef)?.name }?.isVarArg == true
            }
            if (isVararg && !isWindows) {
                val xmmCount = xmmIdx.coerceAtMost(8)
                asm.mov(al8, xmmCount.toByte())
            }

            val funcName = when (val f = inst.function) {
                is FunctionRef -> f.name
                is GlobalRef -> f.name
                else -> error("Unsupported call target: $f")
            }

            val isTailCall = inst.tailCall == TailCallKind.MUSTTAIL ||
                inst.tailCall == TailCallKind.TAIL
            val canTailCall = isTailCall && stackArgsSize == 0

            if (canTailCall) {
                // Tail call: restore frame, then jump instead of call
                emitEpilogue(omitRet = true)
                val isLocal = module.functions.any { it.name == funcName && !it.isExternal }
                if (isLocal) {
                    asm.jmpLabel(funcName)
                } else {
                    val off = asm.jmpExtern()
                    relocations.add(Relocation(
                        offset = off.toLong(), symbol = funcName,
                        type = RelocationType.X86_64.PLT32, addend = -4, section = ".text"))
                }
                return
            }

            val isLocal = module.functions.any { it.name == funcName && !it.isExternal }
            if (isLocal) {
                asm.callLabel(funcName)
            } else {
                val off = asm.callExtern()
                relocations.add(Relocation(
                    offset = off.toLong(), symbol = funcName,
                    type = RelocationType.X86_64.PLT32, addend = -4, section = ".text"))
            }

            if (dest != null) {
                when (dest.type) {
                    Type.I32 -> {
                        val d = getDest32(dest.name)
                        if (d != eax32) asm.mov(d, eax32 as X86Operand32)
                        if (isSpilled(dest.name)) storeTo(dest.name, reg32 = d)
                    }
                    Type.I64, Type.OpaquePointer, is Type.Pointer -> {
                        val d = getDest64(dest.name)
                        if (d != rax64) asm.mov(d, rax64 as X86Operand64)
                        if (isSpilled(dest.name)) storeTo(dest.name, reg64 = d)
                    }
                    Type.F64, Type.F32 -> {
                        val d = getDestXmm(dest.name)
                        if (d != xmm0) asm.movsd(d, xmm0)
                        if (isSpilled(dest.name)) storeToXmm(dest.name, d)
                    }
                    is Type.Struct -> {
                        val size = typeSizeBytes(dest.type)
                        if (size <= 16) {
                            // Small struct return: RAX has first 8 bytes, RDX has second 8 bytes
                            val loc = alloc.locations[dest.name]
                            if (loc is Location.Spill) {
                                emitStoreToRbp64(rax64, loc.offset)
                                if (size > 8) emitStoreToRbp64(rdx64, loc.offset + 8)
                            }
                        }
                        // Large struct: callee already wrote to spill slot via sret pointer
                    }
                    else -> {}
                }
            }

            // Clean up stack args (caller cleans up in System V and Windows x64)
            if (stackArgsSize > 0) {
                val aligned = (stackArgsSize + 15) and 15.inv()
                asm.add(rsp64 as X86Operand64, aligned)
            }
        }

        private fun emitInvoke(inst: Invoke) {
            // Invoke is a call with exception handling. Emit args + call like a normal Call,
            // but record the call site for the LSDA and branch to normalDest after.
            val funcName = when (val f = inst.function) {
                is FunctionRef -> f.name
                is GlobalRef -> f.name
                else -> error("Unsupported invoke target: $f")
            }

            // Emit arguments using the same logic as emitCall
            var gpIdx = 0
            var xmmIdx = 0
            for (arg in inst.args) {
                when {
                    arg.type == Type.F64 || arg.type == Type.F32 -> {
                        if (isWindows) {
                            if (gpIdx < callArgRegsXmm.size) loadValueXmm(arg, callArgRegsXmm[gpIdx])
                            gpIdx++
                        } else {
                            if (xmmIdx < callArgRegsXmm.size) loadValueXmm(arg, callArgRegsXmm[xmmIdx])
                            xmmIdx++
                        }
                    }
                    else -> {
                        if (gpIdx < callArgRegs64.size) {
                            when (arg.type) {
                                Type.I32, Type.I16, Type.I8, Type.I1 -> loadValue32(arg, callArgRegs32[gpIdx])
                                else -> loadValue64(arg, callArgRegs64[gpIdx])
                            }
                        }
                        gpIdx++
                    }
                }
            }

            // Record call site: offset before call instruction
            val callStart = asm.position() - funcStartOffset

            // Emit the call
            val isLocal = module.functions.any { it.name == funcName && !it.isExternal }
            if (isLocal) {
                asm.callLabel(funcName)
            } else {
                val off = asm.callExtern()
                relocations.add(Relocation(
                    offset = off.toLong(), symbol = funcName,
                    type = RelocationType.X86_64.PLT32, addend = -4, section = ".text"))
            }

            val callEnd = asm.position() - funcStartOffset

            // Determine action index from the landing pad's catch clauses
            val actionIndex = resolveActionIndex(inst.unwindDest.label)

            ehCallSites.add(EhCallSite(
                callOffset = callStart,
                callLength = callEnd - callStart,
                landingPadLabel = "${fn.name}.lp.${inst.unwindDest.label}",
                actionIndex = actionIndex,
            ))

            // Store return value if present
            val dest = inst.dest
            if (dest != null) {
                when (dest.type) {
                    Type.I32 -> {
                        val d = getDest32(dest.name)
                        if (d != eax32) asm.mov(d, eax32 as X86Operand32)
                        if (isSpilled(dest.name)) storeTo(dest.name, reg32 = d)
                    }
                    Type.I64, Type.OpaquePointer, is Type.Pointer -> {
                        val d = getDest64(dest.name)
                        if (d != rax64) asm.mov(d, rax64 as X86Operand64)
                        if (isSpilled(dest.name)) storeTo(dest.name, reg64 = d)
                    }
                    Type.F64, Type.F32 -> {
                        val d = getDestXmm(dest.name)
                        if (d != xmm0) asm.movsd(d, xmm0)
                        if (isSpilled(dest.name)) storeToXmm(dest.name, d)
                    }
                    else -> {}
                }
            }

            // Normal path: branch to normalDest
            emitPhiCopies(inst.normalDest.label)
            asm.jmpLabel("${fn.name}.${inst.normalDest.label}")
        }

        private fun emitCallBr(inst: CallBr) {
            // CallBr: call a function that may branch to indirect destinations (asm goto).
            // Emit as a normal call, store return value, then fall through to fallthrough block.
            // The indirect destinations are reached by the callee (inline asm), not by us.
            val funcName = when (val f = inst.function) {
                is FunctionRef -> f.name
                is GlobalRef -> f.name
                else -> error("Unsupported callbr target: $f")
            }

            // Emit arguments
            var gpIdx = 0
            var xmmIdx = 0
            for (arg in inst.args) {
                when {
                    arg.type == Type.F64 || arg.type == Type.F32 -> {
                        if (isWindows) {
                            if (gpIdx < callArgRegsXmm.size) { loadValueXmm(arg, callArgRegsXmm[gpIdx]) }
                            gpIdx++
                        } else {
                            if (xmmIdx < callArgRegsXmm.size) { loadValueXmm(arg, callArgRegsXmm[xmmIdx]) }
                            xmmIdx++
                        }
                    }
                    else -> {
                        if (gpIdx < callArgRegs64.size) {
                            when (arg.type) {
                                Type.I32, Type.I16, Type.I8, Type.I1 -> loadValue32(arg, callArgRegs32[gpIdx])
                                else -> loadValue64(arg, callArgRegs64[gpIdx])
                            }
                        }
                        gpIdx++
                    }
                }
            }

            // Emit the call
            val isLocal = module.functions.any { it.name == funcName && !it.isExternal }
            if (isLocal) {
                asm.callLabel(funcName)
            } else {
                val off = asm.callExtern()
                relocations.add(Relocation(
                    offset = off.toLong(), symbol = funcName,
                    type = RelocationType.X86_64.PLT32, addend = -4, section = ".text"))
            }

            // Store return value
            val dest = inst.dest
            if (dest != null) {
                when (dest.type) {
                    Type.I32 -> {
                        val d = getDest32(dest.name)
                        if (d != eax32) { asm.mov(d, eax32 as X86Operand32) }
                        if (isSpilled(dest.name)) { storeTo(dest.name, reg32 = d) }
                    }
                    Type.I64, Type.OpaquePointer, is Type.Pointer -> {
                        val d = getDest64(dest.name)
                        if (d != rax64) { asm.mov(d, rax64 as X86Operand64) }
                        if (isSpilled(dest.name)) { storeTo(dest.name, reg64 = d) }
                    }
                    Type.F64, Type.F32 -> {
                        val d = getDestXmm(dest.name)
                        if (d != xmm0) { asm.movsd(d, xmm0) }
                        if (isSpilled(dest.name)) { storeToXmm(dest.name, d) }
                    }
                    else -> {}
                }
            }

            // Fall through to fallthrough block
            emitPhiCopies(inst.fallthrough.label)
            asm.jmpLabel("${fn.name}.${inst.fallthrough.label}")
        }

        private fun emitLandingPad(inst: LandingPad) {
            // The landing pad label is where the unwinder transfers control.
            // By the Itanium ABI, on entry to a landing pad:
            //   RAX = exception pointer, RDX = selector value
            // We store both into the dest (which is a {ptr, i32} pair).
            val labelName = "${fn.name}.lp.${currentBlockLabel}"
            asm.label(labelName)

            // Store exception pointer (RAX) into the dest
            val dest = inst.dest
            val loc = alloc.locations[dest.name]
            if (loc is Location.Spill) {
                emitStoreToRbp64(rax64, loc.offset)
                // Store selector (RDX) at offset+8 if the result type is a struct
                if (inst.resultType is Type.Struct) {
                    emitStoreToRbp64(rdx64, loc.offset + 8)
                }
            } else if (loc is Location.Reg64) {
                if (loc.reg != rax64) asm.mov(loc.reg, rax64 as X86Operand64)
            } else {
                // If the landing pad result is used as a simple pointer, store RAX
                val d = getDest64(dest.name)
                if (d != rax64) asm.mov(d, rax64 as X86Operand64)
                if (isSpilled(dest.name)) storeTo(dest.name, reg64 = d)
            }
        }

        private fun emitResume(inst: Resume) {
            loadValue64(inst.value, callArgRegs64[0])
            val off = asm.callExtern()
            relocations.add(Relocation(
                offset = off.toLong(), symbol = "_Unwind_Resume",
                type = RelocationType.X86_64.PLT32, addend = -4, section = ".text"))
            asm.ud2()
        }

        private fun emitThrow(inst: Throw) {
            loadValue64(inst.exception, callArgRegs64[0])
            val off = asm.callExtern()
            relocations.add(Relocation(
                offset = off.toLong(), symbol = "kgen_throw",
                type = RelocationType.X86_64.PLT32, addend = -4, section = ".text"))
            asm.ud2()
        }

        private fun resolveActionIndex(unwindLabel: String): Int {
            val unwindBlock = fn.blocks.firstOrNull { it.label == unwindLabel } ?: return 0
            val lp = unwindBlock.instructions.firstOrNull { it is LandingPad } as? LandingPad
                ?: return 0
            if (lp.cleanup && lp.clauses.isEmpty()) return 0 // cleanup only, no type filter
            val catchClause = lp.clauses.filterIsInstance<LandingPadClause.Catch>().firstOrNull() ?: return 0
            val typeName = when (val v = catchClause.type) {
                is GlobalRef -> v.name
                else -> return 0
            }
            // Add type to table if not present, return 1-based index
            val idx = ehTypeNames.indexOf(typeName)
            return if (idx >= 0) {
                idx + 1
            } else {
                ehTypeNames.add(typeName)
                ehTypeNames.size
            }
        }

        private fun emitICmp(inst: ICmp) {
            val dest = getDest32(inst.dest.name)
            when (inst.lhs.type) {
                Type.I32, Type.I16, Type.I8, Type.I1 -> {
                    val lhs = inst.lhs
                    val lhsReg = if (lhs is Constant.I32) {
                        val scratch = if (dest == r11d32) r10d32 else dest
                        asm.mov(scratch, lhs.value)
                        scratch
                    } else {
                        getOrLoad32(lhs.name, if (dest == r11d32) r10d32 else dest)
                    }
                    val rhs = inst.rhs
                    if (rhs is Constant.I32 && rhs.value == 0) {
                        asm.test(lhsReg as X86Operand32, lhsReg)
                    } else if (rhs is Constant.I32) {
                        asm.cmp(lhsReg, rhs.value)
                    } else {
                        val rhsScratch = if (lhsReg == r11d32) r10d32 else r11d32
                        asm.cmp(lhsReg, getOrLoad32(rhs.name, rhsScratch) as X86Operand32)
                    }
                }
                Type.I64, Type.OpaquePointer, is Type.Pointer -> {
                    val lhs = inst.lhs
                    val lhsReg = if (lhs is Constant.I64) {
                        asm.mov(r10_64, lhs.value)
                        r10_64
                    } else if (lhs is Constant.I32) {
                        asm.mov(r10d32, lhs.value)
                        r10_64
                    } else {
                        getOrLoad64(lhs.name, r10_64)
                    }
                    val rhs = inst.rhs
                    if ((rhs is Constant.I32 && rhs.value == 0) || (rhs is Constant.I64 && rhs.value == 0L)) {
                        asm.test(lhsReg as X86Operand64, lhsReg)
                    } else if (rhs is Constant.I32) {
                        asm.cmp(lhsReg, rhs.value)
                    } else if (rhs is Constant.I64 && rhs.value in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) {
                        asm.cmp(lhsReg, rhs.value.toInt())
                    } else {
                        val rhsReg = getOrLoad64(rhs.name, if (lhsReg == r10_64) r11_64 else r10_64)
                        asm.cmp(lhsReg, rhsReg as X86Operand64)
                    }
                }
                else -> error("Unsupported icmp type: ${inst.lhs.type}")
            }
            val cc = icmpCondCode(inst.predicate)
            asm.xor_(dest, dest as X86Operand32)
            val enc = (dest as X86Register).encoding
            if (enc >= 4) {
                asm.emitByte(0x40 or (if (enc >= 8) 0x01 else 0))
            }
            asm.emitBytes(0x0F, 0x90 + cc)
            asm.emitByte(0xC0 or (enc and 7))
            if (isSpilled(inst.dest.name)) storeTo(inst.dest.name, reg32 = dest)
        }

        private fun emitGetElementPtr(inst: GetElementPtr) {
            val dest = getDest64(inst.dest.name)
            val ptr = inst.ptr
            if (ptr is GlobalRef && isTlsGlobal(ptr.name)) {
                loadTlsAddress(ptr.name, dest)
            } else if (ptr is GlobalRef) {
                val enc = (dest as X86Register).encoding
                val rex = 0x48 or ((enc shr 3) and 1).shl(2)
                asm.emitByte(rex)
                asm.emitByte(0x8D) // LEA
                asm.emitByte(0x05 or ((enc and 7) shl 3))
                relocations.add(Relocation(
                    offset = asm.position().toLong(), symbol = ptr.name,
                    type = RelocationType.X86_64.PC32, addend = -4, section = ".text"))
                asm.emitInt32(0)
            } else {
                loadValue64(ptr, dest)
            }

            // Compute offset from indices
            val constOffset = computeGepOffset(inst.baseType, inst.indices)
            if (constOffset != null && constOffset != 0) {
                emitAddImm64(dest, constOffset)
            } else if (constOffset == null) {
                // Dynamic index — compute at runtime
                emitDynamicGepOffset(dest, inst.baseType, inst.indices)
            }

            if (isSpilled(inst.dest.name)) storeTo(inst.dest.name, reg64 = dest)
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
                    // First index: offset from base pointer by element size
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
                        else -> {
                            offset += constIdx * typeSizeBytes(currentType)
                        }
                    }
                }
            }
            return offset
        }

        private fun emitDynamicGepOffset(dest: X86Register64, baseType: Type, indices: List<Value>) {
            var currentType = baseType
            for ((i, idx) in indices.withIndex()) {
                val constIdx = when (idx) {
                    is Constant.I32 -> idx.value
                    is Constant.I64 -> idx.value.toInt()
                    else -> null
                }
                if (constIdx != null) {
                    if (i == 0) {
                        val off = constIdx * typeSizeBytes(currentType)
                        if (off != 0) emitAddImm64(dest, off)
                    } else {
                        when (currentType) {
                            is Type.Struct -> {
                                val off = structFieldOffset(currentType, constIdx)
                                if (off != 0) emitAddImm64(dest, off)
                                currentType = currentType.fields[constIdx]
                            }
                            is Type.Array -> {
                                val off = constIdx * typeSizeBytes(currentType.element)
                                if (off != 0) emitAddImm64(dest, off)
                                currentType = currentType.element
                            }
                            else -> {
                                val off = constIdx * typeSizeBytes(currentType)
                                if (off != 0) emitAddImm64(dest, off)
                            }
                        }
                    }
                } else {
                    val elemSize = if (i == 0) typeSizeBytes(currentType)
                    else when (currentType) {
                        is Type.Array -> typeSizeBytes(currentType.element).also { currentType = currentType.element }
                        else -> typeSizeBytes(currentType)
                    }
                    val scratch = if (dest == r10_64) r11_64 else r10_64
                    loadValue64(idx, scratch)
                    if (elemSize != 1) {
                        emitImulImm64(scratch, scratch, elemSize)
                    }
                    asm.add(dest, scratch as X86Operand64)
                }
            }
        }

        private fun emitAddImm64(dest: X86Register64, imm: Int) {
            // add dest, imm32
            val enc = (dest as X86Register).encoding
            val rex = 0x48 or ((enc shr 3) and 1)
            asm.emitByte(rex)
            asm.emitByte(0x81)
            asm.emitByte(0xC0 or (enc and 7))
            asm.emitInt32(imm)
        }

        private fun emitImulImm64(dest: X86Register64, src: X86Register64, imm: Int) {
            // imul dest, src, imm32
            val dEnc = (dest as X86Register).encoding
            val sEnc = (src as X86Register).encoding
            val rex = 0x48 or ((dEnc shr 3) and 1).shl(2) or ((sEnc shr 3) and 1)
            asm.emitByte(rex)
            asm.emitByte(0x69)
            asm.emitByte(0xC0 or ((dEnc and 7) shl 3) or (sEnc and 7))
            asm.emitInt32(imm)
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

        private fun emitExtractValue(inst: ExtractValue) {
            val fieldType = aggregateFieldType(inst.aggregate.type, inst.indices)
            val fieldOffset = aggregateFieldOffset(inst.aggregate.type, inst.indices)

            // The aggregate must be in a spill slot (aggregates are always on the stack)
            val aggLoc = alloc.locations[inst.aggregate.name]
            val baseOffset = when (aggLoc) {
                is Location.Spill -> aggLoc.offset
                else -> error("ExtractValue: aggregate ${inst.aggregate.name} must be spilled")
            }

            val memOffset = baseOffset + fieldOffset
            when (fieldType) {
                Type.I32 -> {
                    val dest = getDest32(inst.dest.name)
                    emitLoadFromRbp32(dest, memOffset)
                    if (isSpilled(inst.dest.name)) storeTo(inst.dest.name, reg32 = dest)
                }
                Type.I64, Type.OpaquePointer, is Type.Pointer -> {
                    val dest = getDest64(inst.dest.name)
                    emitLoadFromRbp64(dest, memOffset)
                    if (isSpilled(inst.dest.name)) storeTo(inst.dest.name, reg64 = dest)
                }
                else -> {
                    val dest = getDest64(inst.dest.name)
                    emitLoadFromRbp64(dest, memOffset)
                    if (isSpilled(inst.dest.name)) storeTo(inst.dest.name, reg64 = dest)
                }
            }
        }

        private fun emitInsertValue(inst: InsertValue) {
            val fieldType = aggregateFieldType(inst.aggregate.type, inst.indices)
            val fieldOffset = aggregateFieldOffset(inst.aggregate.type, inst.indices)

            val aggLoc = alloc.locations[inst.aggregate.name]
            val destLoc = alloc.locations[inst.dest.name]

            // Copy aggregate to dest location if different
            if (aggLoc is Location.Spill && destLoc is Location.Spill && aggLoc.offset != destLoc.offset) {
                val aggSize = typeSizeBytes(inst.aggregate.type)
                // Copy word by word
                for (off in 0 until aggSize step 8) {
                    emitLoadFromRbp64(r10_64, aggLoc.offset + off)
                    emitStoreToRbp64(r10_64, destLoc.offset + off)
                }
            }

            val baseOffset = when (destLoc) {
                is Location.Spill -> destLoc.offset
                else -> error("InsertValue: dest ${inst.dest.name} must be spilled")
            }

            val memOffset = baseOffset + fieldOffset
            when (fieldType) {
                Type.I32 -> {
                    loadValue32(inst.element, r10d32)
                    emitStoreToRbp32(r10d32, memOffset)
                }
                Type.I64, Type.OpaquePointer, is Type.Pointer -> {
                    loadValue64(inst.element, r10_64)
                    emitStoreToRbp64(r10_64, memOffset)
                }
                else -> {
                    loadValue64(inst.element, r10_64)
                    emitStoreToRbp64(r10_64, memOffset)
                }
            }
        }

        private fun emitLoadFromRbp32(dest: X86Register32, offset: Int) {
            // mov dest32, [rbp + offset]
            val enc = (dest as X86Register).encoding
            val rex = if (enc >= 8) 0x44 else 0
            if (rex != 0) asm.emitByte(rex)
            asm.emitByte(0x8B)
            asm.emitByte(0x85 or ((enc and 7) shl 3))
            asm.emitInt32(offset)
        }

        private fun emitLoadFromRbp64(dest: X86Register64, offset: Int) {
            // mov dest64, [rbp + offset]
            val enc = (dest as X86Register).encoding
            val rex = 0x48 or ((enc shr 3) and 1).shl(2)
            asm.emitByte(rex)
            asm.emitByte(0x8B)
            asm.emitByte(0x85 or ((enc and 7) shl 3))
            asm.emitInt32(offset)
        }

        private fun emitStoreToRbp32(src: X86Register32, offset: Int) {
            // mov [rbp + offset], src32
            val enc = (src as X86Register).encoding
            val rex = if (enc >= 8) 0x44 else 0
            if (rex != 0) asm.emitByte(rex)
            asm.emitByte(0x89)
            asm.emitByte(0x85 or ((enc and 7) shl 3))
            asm.emitInt32(offset)
        }

        private fun emitStoreToRbp64(src: X86Register64, offset: Int) {
            // mov [rbp + offset], src64
            val enc = (src as X86Register).encoding
            val rex = 0x48 or ((enc shr 3) and 1).shl(2)
            asm.emitByte(rex)
            asm.emitByte(0x89)
            asm.emitByte(0x85 or ((enc and 7) shl 3))
            asm.emitInt32(offset)
        }

        private fun isPowerOf2(n: Int): Int? {
            if (n <= 0) return null
            if (n and (n - 1) != 0) return null
            return Integer.numberOfTrailingZeros(n)
        }

        private fun emitMul(inst: Mul) {
            when (inst.lhs.type) {
                Type.I32 -> {
                    val dest = getDest32(inst.dest.name)
                    val rhs = inst.rhs
                    // Strength reduction: multiply by power of 2 → shift
                    if (rhs is Constant.I32) {
                        val shift = isPowerOf2(rhs.value)
                        if (shift != null) {
                            loadValue32(inst.lhs, dest)
                            asm.shl(dest as X86Operand32, shift.toByte())
                            if (isSpilled(inst.dest.name)) storeTo(inst.dest.name, reg32 = dest)
                            return
                        }
                    }
                    loadValue32(inst.lhs, dest)
                    if (rhs is Constant.I32) {
                        loadValue32(rhs, r11d32)
                        asm.imul(dest, r11d32 as X86Operand32)
                    } else {
                        val rhsReg = getOrLoad32(rhs.name, if (dest == r11d32) r10d32 else r11d32)
                        asm.imul(dest, rhsReg as X86Operand32)
                    }
                    if (isSpilled(inst.dest.name)) storeTo(inst.dest.name, reg32 = dest)
                }
                Type.I64 -> {
                    val dest = getDest64(inst.dest.name)
                    val rhs = inst.rhs
                    if (rhs is Constant.I32) {
                        val shift = isPowerOf2(rhs.value)
                        if (shift != null) {
                            loadValue64(inst.lhs, dest)
                            asm.shl(dest as X86Operand64, shift.toByte())
                            if (isSpilled(inst.dest.name)) storeTo(inst.dest.name, reg64 = dest)
                            return
                        }
                    }
                    if (rhs is Constant.I64) {
                        val shift = if (rhs.value in 1..Int.MAX_VALUE.toLong()) isPowerOf2(rhs.value.toInt()) else null
                        if (shift != null) {
                            loadValue64(inst.lhs, dest)
                            asm.shl(dest as X86Operand64, shift.toByte())
                            if (isSpilled(inst.dest.name)) storeTo(inst.dest.name, reg64 = dest)
                            return
                        }
                    }
                    loadValue64(inst.lhs, dest)
                    if (rhs is Constant.I32) {
                        loadValue64(Constant.I64(rhs.value.toLong()), r11_64)
                        asm.imul(dest, r11_64 as X86Operand64)
                    } else if (rhs is Constant.I64) {
                        loadValue64(rhs, r11_64)
                        asm.imul(dest, r11_64 as X86Operand64)
                    } else {
                        val rhsReg = getOrLoad64(rhs.name, if (dest == r11_64) r10_64 else r11_64)
                        asm.imul(dest, rhsReg as X86Operand64)
                    }
                    if (isSpilled(inst.dest.name)) storeTo(inst.dest.name, reg64 = dest)
                }
                else -> error("Unsupported mul type: ${inst.lhs.type}")
            }
        }

        private fun emitDiv(dest: InstructionRef, lhs: Value, rhs: Value, signed: Boolean, remainder: Boolean) {
            // x86 div/idiv: EDX:EAX / operand → quotient in EAX, remainder in EDX
            // RAX and RDX are excluded from register allocation, so we can freely use them.
            // Load divisor into r11 FIRST to avoid any ordering issues.
            when (lhs.type) {
                Type.I32 -> {
                    val divisor = if (rhs is Constant.I32) {
                        loadValue32(rhs, r11d32); r11d32
                    } else {
                        val r = getOrLoad32(rhs.name, r11d32)
                        if (r != r11d32) { asm.mov(r11d32, r as X86Operand32) }
                        r11d32
                    }
                    loadValue32(lhs, eax32)
                    if (signed) asm.cdq() else { asm.xor_(edx32, edx32 as X86Operand32) }
                    if (signed) asm.idiv(divisor as X86Operand32) else asm.div(divisor as X86Operand32)
                    val resultReg = if (remainder) edx32 else eax32
                    val d = getDest32(dest.name)
                    if (d != resultReg) asm.mov(d, resultReg as X86Operand32)
                    if (isSpilled(dest.name)) storeTo(dest.name, reg32 = d)
                }
                Type.I64 -> {
                    val divisor = if (rhs is Constant.I64) {
                        loadValue64(rhs, r11_64); r11_64
                    } else if (rhs is Constant.I32) {
                        loadValue64(Constant.I64(rhs.value.toLong()), r11_64); r11_64
                    } else {
                        val r = getOrLoad64(rhs.name, r11_64)
                        if (r != r11_64) { asm.mov(r11_64, r as X86Operand64) }
                        r11_64
                    }
                    loadValue64(lhs, rax64)
                    if (signed) asm.cqo() else { asm.xor_(edx32, edx32 as X86Operand32) }
                    if (signed) asm.idiv(divisor as X86Operand64) else asm.div(divisor as X86Operand64)
                    val resultReg = if (remainder) rdx64 else rax64
                    val d = getDest64(dest.name)
                    if (d != resultReg) asm.mov(d, resultReg as X86Operand64)
                    if (isSpilled(dest.name)) storeTo(dest.name, reg64 = d)
                }
                else -> error("Unsupported div type: ${lhs.type}")
            }
        }

        private fun emitShift(
            dest: InstructionRef, lhs: Value, rhs: Value,
            clOp32: (X86Register32) -> Unit, immOp32: (X86Register32, Byte) -> Unit,
            clOp64: (X86Register64) -> Unit, immOp64: (X86Register64, Byte) -> Unit,
        ) {
            when (lhs.type) {
                Type.I32 -> {
                    val d = getDest32(dest.name)
                    loadValue32(lhs, d)
                    if (rhs is Constant.I32) {
                        immOp32(d, rhs.value.toByte())
                    } else {
                        // Shift amount must be in CL
                        loadValue32(rhs, ecx32)
                        clOp32(d)
                    }
                    if (isSpilled(dest.name)) storeTo(dest.name, reg32 = d)
                }
                Type.I64 -> {
                    val d = getDest64(dest.name)
                    loadValue64(lhs, d)
                    if (rhs is Constant.I32) {
                        immOp64(d, rhs.value.toByte())
                    } else if (rhs is Constant.I64) {
                        immOp64(d, rhs.value.toByte())
                    } else {
                        loadValue32(rhs, ecx32)
                        clOp64(d)
                    }
                    if (isSpilled(dest.name)) storeTo(dest.name, reg64 = d)
                }
                else -> error("Unsupported shift type: ${lhs.type}")
            }
        }

        private fun emitNeg(inst: Neg) {
            when (inst.operand.type) {
                Type.I32 -> {
                    val d = getDest32(inst.dest.name)
                    loadValue32(inst.operand, d)
                    asm.neg(d as X86Operand32)
                    if (isSpilled(inst.dest.name)) storeTo(inst.dest.name, reg32 = d)
                }
                Type.I64 -> {
                    val d = getDest64(inst.dest.name)
                    loadValue64(inst.operand, d)
                    asm.neg(d as X86Operand64)
                    if (isSpilled(inst.dest.name)) storeTo(inst.dest.name, reg64 = d)
                }
                else -> error("Unsupported neg type: ${inst.operand.type}")
            }
        }

        private fun emitNot(inst: Not) {
            when (inst.operand.type) {
                Type.I32 -> {
                    val d = getDest32(inst.dest.name)
                    loadValue32(inst.operand, d)
                    // not r32 = F7 /2
                    val enc = (d as X86Register).encoding
                    if (enc >= 8) asm.emitByte(0x41)
                    asm.emitByte(0xF7)
                    asm.emitByte(0xD0 or (enc and 7))
                    if (isSpilled(inst.dest.name)) storeTo(inst.dest.name, reg32 = d)
                }
                Type.I64 -> {
                    val d = getDest64(inst.dest.name)
                    loadValue64(inst.operand, d)
                    // not r64 = REX.W F7 /2
                    val enc = (d as X86Register).encoding
                    asm.emitByte(0x48 or ((enc shr 3) and 1))
                    asm.emitByte(0xF7)
                    asm.emitByte(0xD0 or (enc and 7))
                    if (isSpilled(inst.dest.name)) storeTo(inst.dest.name, reg64 = d)
                }
                else -> error("Unsupported not type: ${inst.operand.type}")
            }
        }

        private fun emitCopy64(dest: InstructionRef, value: Value) {
            val d = getDest64(dest.name)
            loadValue64(value, d)
            if (isSpilled(dest.name)) storeTo(dest.name, reg64 = d)
        }

        private fun emitBitCast(inst: BitCast) {
            val srcType = inst.value.type
            val destType = inst.dest.type
            when {
                // pointer/int ↔ pointer/int: just copy
                (srcType == Type.I64 || srcType == Type.OpaquePointer || srcType is Type.Pointer) &&
                (destType == Type.I64 || destType == Type.OpaquePointer || destType is Type.Pointer) -> {
                    emitCopy64(inst.dest, inst.value)
                }
                srcType == Type.I32 && destType == Type.I32 -> {
                    val d = getDest32(inst.dest.name)
                    loadValue32(inst.value, d)
                    if (isSpilled(inst.dest.name)) storeTo(inst.dest.name, reg32 = d)
                }
                else -> {
                    // Fallback: 64-bit copy
                    emitCopy64(inst.dest, inst.value)
                }
            }
        }

        private fun emitZExt(inst: ZExt) {
            val src = inst.value
            val srcType = src.type
            val destType = inst.dest.type
            when {
                srcType == Type.I32 && destType == Type.I64 -> {
                    // mov r32, src — implicit zero-extension to 64-bit
                    val d = getDest64(inst.dest.name)
                    val d32 = reg64to32(d)
                    loadValue32(src, d32)
                    // Writing to a 32-bit register auto-zeros the upper 32 bits
                    if (isSpilled(inst.dest.name)) storeTo(inst.dest.name, reg64 = d)
                }
                srcType == Type.I1 && destType == Type.I32 -> {
                    val d = getDest32(inst.dest.name)
                    loadValue32(src, d) // I1 is already 0 or 1 in i32
                    if (isSpilled(inst.dest.name)) storeTo(inst.dest.name, reg32 = d)
                }
                srcType == Type.I1 && destType == Type.I64 -> {
                    val d = getDest64(inst.dest.name)
                    val d32 = reg64to32(d)
                    loadValue32(src, d32)
                    if (isSpilled(inst.dest.name)) storeTo(inst.dest.name, reg64 = d)
                }
                srcType == Type.I8 && destType == Type.I32 -> {
                    val d = getDest32(inst.dest.name)
                    // Need movzx r32, r/m8 — emit manually since src is in a 32-bit reg
                    loadValue32(src, r11d32)
                    emitMovzx32from8(d, r11d32)
                    if (isSpilled(inst.dest.name)) storeTo(inst.dest.name, reg32 = d)
                }
                srcType == Type.I16 && destType == Type.I32 -> {
                    val d = getDest32(inst.dest.name)
                    loadValue32(src, r11d32)
                    emitMovzx32from16(d, r11d32)
                    if (isSpilled(inst.dest.name)) storeTo(inst.dest.name, reg32 = d)
                }
                else -> error("Unsupported zext: $srcType -> $destType")
            }
        }

        private fun emitSExt(inst: SExt) {
            val src = inst.value
            val srcType = src.type
            val destType = inst.dest.type
            when {
                srcType == Type.I32 && destType == Type.I64 -> {
                    // movsxd r64, r/m32
                    val d = getDest64(inst.dest.name)
                    loadValue32(src, r11d32)
                    asm.movsxd(d, r11d32 as X86Operand32)
                    if (isSpilled(inst.dest.name)) storeTo(inst.dest.name, reg64 = d)
                }
                srcType == Type.I1 && destType == Type.I32 -> {
                    // i1 is 0 or 1; sign-extend: 0→0, 1→-1
                    val d = getDest32(inst.dest.name)
                    loadValue32(src, d)
                    asm.neg(d as X86Operand32) // 0→0, 1→0xFFFFFFFF
                    if (isSpilled(inst.dest.name)) storeTo(inst.dest.name, reg32 = d)
                }
                srcType == Type.I8 && destType == Type.I32 -> {
                    val d = getDest32(inst.dest.name)
                    loadValue32(src, r11d32)
                    emitMovsx32from8(d, r11d32)
                    if (isSpilled(inst.dest.name)) storeTo(inst.dest.name, reg32 = d)
                }
                srcType == Type.I16 && destType == Type.I32 -> {
                    val d = getDest32(inst.dest.name)
                    loadValue32(src, r11d32)
                    emitMovsx32from16(d, r11d32)
                    if (isSpilled(inst.dest.name)) storeTo(inst.dest.name, reg32 = d)
                }
                else -> error("Unsupported sext: $srcType -> $destType")
            }
        }

        private fun emitTrunc(dest: InstructionRef, src: Value, destType: Type) {
            when {
                src.type == Type.I64 && destType == Type.I32 -> {
                    val d = getDest32(dest.name)
                    val srcReg = getOrLoad64(src.name, r11_64)
                    val src32 = reg64to32(srcReg)
                    if (d != src32) asm.mov(d, src32 as X86Operand32)
                    if (isSpilled(dest.name)) storeTo(dest.name, reg32 = d)
                }
                src.type == Type.I32 && destType == Type.I8 -> {
                    val d = getDest32(dest.name)
                    loadValue32(src, d)
                    asm.and_(d as X86Operand32, 0xFF)
                    if (isSpilled(dest.name)) storeTo(dest.name, reg32 = d)
                }
                src.type == Type.I32 && destType == Type.I16 -> {
                    val d = getDest32(dest.name)
                    loadValue32(src, d)
                    asm.and_(d as X86Operand32, 0xFFFF)
                    if (isSpilled(dest.name)) storeTo(dest.name, reg32 = d)
                }
                src.type == Type.I32 && destType == Type.I1 -> {
                    val d = getDest32(dest.name)
                    loadValue32(src, d)
                    asm.and_(d as X86Operand32, 1)
                    if (isSpilled(dest.name)) storeTo(dest.name, reg32 = d)
                }
                else -> error("Unsupported trunc: ${src.type} -> $destType")
            }
        }

        private fun emitAlloca(inst: Alloca) {
            val offset = allocaOffsets[inst.dest.name]
                ?: error("No alloca offset for ${inst.dest.name}")
            // lea dest, [rbp + offset]
            val d = getDest64(inst.dest.name)
            emitLea64(d, rbp64, offset)
            if (isSpilled(inst.dest.name)) storeTo(inst.dest.name, reg64 = d)
        }

        private fun emitLea64(dest: X86Register64, base: X86Register64, disp: Int) {
            val dEnc = (dest as X86Register).encoding
            val bEnc = (base as X86Register).encoding
            val rex = 0x48 or ((dEnc shr 3) shl 2) or (bEnc shr 3)
            asm.emitByte(rex)
            asm.emitByte(0x8D) // LEA
            if (disp == 0 && (bEnc and 7) != 5) {
                asm.emitByte(((dEnc and 7) shl 3) or (bEnc and 7))
                if ((bEnc and 7) == 4) asm.emitByte(0x24) // SIB for RSP
            } else if (disp in -128..127) {
                asm.emitByte(0x40 or ((dEnc and 7) shl 3) or (bEnc and 7))
                if ((bEnc and 7) == 4) asm.emitByte(0x24) // SIB for RSP
                asm.emitByte(disp and 0xFF)
            } else {
                asm.emitByte(0x80 or ((dEnc and 7) shl 3) or (bEnc and 7))
                if ((bEnc and 7) == 4) asm.emitByte(0x24) // SIB for RSP
                asm.emitInt32(disp)
            }
        }

        private fun isTlsGlobal(name: String): Boolean =
            module.globals.any { it.name == name && it.threadLocal != null }

        private fun tlsMode(name: String): ThreadLocalMode =
            module.globals.first { it.name == name && it.threadLocal != null }.threadLocal!!

        /**
         * Resolve a pointer value to a GP register holding its address.
         *
         * For [GlobalRef] values, emits a RIP-relative LEA with a PC32 relocation
         * (or a TLS address load for thread-local globals). For local SSA values,
         * loads from the register allocation or spill slot.
         */
        private fun resolvePointer(ptr: Value, scratch: X86Register64 = r11_64): X86Register64 {
            if (ptr is GlobalRef) {
                if (isTlsGlobal(ptr.name)) {
                    return loadTlsAddress(ptr.name, scratch)
                }
                val enc = (scratch as X86Register).encoding
                val rex = 0x48 or ((enc shr 3) and 1).shl(2)
                asm.emitByte(rex)
                asm.emitByte(0x8D) // LEA
                asm.emitByte(0x05 or ((enc and 7) shl 3))
                relocations.add(Relocation(
                    offset = asm.position().toLong(), symbol = ptr.name,
                    type = RelocationType.X86_64.PC32, addend = -4, section = ".text"))
                asm.emitInt32(0)
                return scratch
            }
            return getOrLoad64(ptr.name, scratch)
        }

        /**
         * Load the address of a thread-local variable into [dest].
         *
         * Supports three TLS access models on Linux:
         * - **LOCAL_EXEC**: direct FS/GS segment offset (static linking only)
         * - **INITIAL_EXEC**: GOT-relative TP offset (for shared libraries)
         * - **GENERAL_DYNAMIC**: full `__tls_get_addr` call sequence (relaxed to LOCAL_EXEC by static linker)
         *
         * On Windows, uses the TEB (Thread Environment Block) via GS segment.
         */
        private fun loadTlsAddress(name: String, dest: X86Register64): X86Register64 {
            val mode = tlsMode(name)
            if (isWindows) {
                return loadTlsAddressWindows(name, dest)
            }
            return when (mode) {
                ThreadLocalMode.GENERAL_DYNAMIC -> loadTlsAddressGeneralDynamic(name, dest)
                ThreadLocalMode.INITIAL_EXEC -> loadTlsAddressInitialExec(name, dest)
                else -> loadTlsAddressLocalExec(name, dest)
            }
        }

        /**
         * GENERAL_DYNAMIC: data16 lea rdi, [rip+sym@TLSGD]; data16 data16 rex.W call __tls_get_addr@PLT
         * Works everywhere (shared libraries, dlopen), calls __tls_get_addr.
         * Result in RAX. Clobbers caller-saved registers.
         */
        private fun loadTlsAddressGeneralDynamic(name: String, dest: X86Register64): X86Register64 {
            // Standard GD sequence (32 bytes, exactly as specified by x86-64 ABI):
            // 0x66                          data16 prefix (padding for linker relaxation)
            // 48 8d 3d XX XX XX XX          lea rdi, [rip + sym@TLSGD]
            // 0x66 0x66                     data16 data16 (padding)
            // 48 e8 XX XX XX XX             rex.W call __tls_get_addr@PLT

            // data16 + lea rdi, [rip + sym@TLSGD]
            asm.emitByte(0x66) // data16 prefix
            asm.emitByte(0x48) // REX.W
            asm.emitByte(0x8D) // LEA
            asm.emitByte(0x3D) // rdi, [rip+disp32]
            relocations.add(Relocation(
                offset = asm.position().toLong(), symbol = name,
                type = RelocationType.X86_64.TLSGD, addend = -4, section = ".text"))
            asm.emitInt32(0)

            // data16 data16 rex.W call __tls_get_addr@PLT
            asm.emitByte(0x66) // data16
            asm.emitByte(0x66) // data16
            asm.emitByte(0x48) // REX.W
            asm.emitByte(0xE8) // CALL rel32
            relocations.add(Relocation(
                offset = asm.position().toLong(), symbol = "__tls_get_addr",
                type = RelocationType.X86_64.PLT32, addend = -4, section = ".text"))
            asm.emitInt32(0)

            // Result is in RAX. Move to dest if different.
            if (dest != X86Register.RAX) {
                asm.mov(dest, X86Register.RAX as X86Register64)
            }
            return dest
        }

        /**
         * LOCAL_EXEC: mov dest, fs:[0]; lea dest, [dest + tpoff]
         * Static offset known at link time — most efficient.
         */
        private fun loadTlsAddressLocalExec(name: String, dest: X86Register64): X86Register64 {
            val enc = (dest as X86Register).encoding
            val rex = 0x48 or ((enc shr 3) and 1).shl(2)
            // mov dest, fs:[0]
            asm.emitByte(0x64) // FS prefix
            asm.emitByte(rex)
            asm.emitByte(0x8B) // MOV r64, [disp32]
            asm.emitByte(0x04 or ((enc and 7) shl 3))
            asm.emitByte(0x25) // SIB: [disp32]
            asm.emitInt32(0)
            // lea dest, [dest + tpoff]
            asm.emitByte(rex)
            asm.emitByte(0x8D) // LEA
            asm.emitByte(0x80 or ((enc and 7) shl 3) or (enc and 7))
            relocations.add(Relocation(
                offset = asm.position().toLong(), symbol = name,
                type = RelocationType.X86_64.TPOFF32, addend = 0, section = ".text"))
            asm.emitInt32(0)
            return dest
        }

        /**
         * INITIAL_EXEC: mov dest, fs:[0]; add dest, [rip + symbol@GOTTPOFF]
         * Offset loaded from GOT at runtime — works for shared libraries.
         */
        private fun loadTlsAddressInitialExec(name: String, dest: X86Register64): X86Register64 {
            val enc = (dest as X86Register).encoding
            val rex = 0x48 or ((enc shr 3) and 1).shl(2)
            // mov dest, fs:[0]
            asm.emitByte(0x64) // FS prefix
            asm.emitByte(rex)
            asm.emitByte(0x8B) // MOV r64, [disp32]
            asm.emitByte(0x04 or ((enc and 7) shl 3))
            asm.emitByte(0x25) // SIB: [disp32]
            asm.emitInt32(0)
            // add dest, qword ptr [rip + symbol@GOTTPOFF]
            asm.emitByte(rex)
            asm.emitByte(0x03) // ADD r64, r/m64
            asm.emitByte(0x05 or ((enc and 7) shl 3)) // [rip + disp32]
            relocations.add(Relocation(
                offset = asm.position().toLong(), symbol = name,
                type = RelocationType.X86_64.GOTTPOFF, addend = -4, section = ".text"))
            asm.emitInt32(0)
            return dest
        }

        /**
         * Windows: mov dest, gs:[0x58]; mov dest, [dest]; lea dest, [dest + offset]
         */
        private fun loadTlsAddressWindows(name: String, dest: X86Register64): X86Register64 {
            val enc = (dest as X86Register).encoding
            val rex = 0x48 or ((enc shr 3) and 1).shl(2)
            // mov dest, gs:[0x58] — TEB.ThreadLocalStoragePointer
            asm.emitByte(0x65) // GS prefix
            asm.emitByte(rex)
            asm.emitByte(0x8B) // MOV r64, [disp32]
            asm.emitByte(0x04 or ((enc and 7) shl 3))
            asm.emitByte(0x25) // SIB: [disp32]
            asm.emitInt32(0x58)
            // mov dest, [dest] — dereference TLS slot pointer
            asm.emitByte(rex)
            asm.emitByte(0x8B)
            asm.emitByte(((enc and 7) shl 3) or (enc and 7))
            // lea dest, [dest + offset]
            asm.emitByte(rex)
            asm.emitByte(0x8D) // LEA
            asm.emitByte(0x80 or ((enc and 7) shl 3) or (enc and 7))
            relocations.add(Relocation(
                offset = asm.position().toLong(), symbol = name,
                type = RelocationType.X86_64.R_32, addend = 0, section = ".text"))
            asm.emitInt32(0)
            return dest
        }

        private fun emitLoad(inst: Load) {
            val ptr = inst.ptr
            val destType = inst.dest.type
            when (destType) {
                Type.I32 -> {
                    val d = getDest32(inst.dest.name)
                    val addr = resolvePointer(ptr, r11_64)
                    emitLoadMem32(d, addr, 0)
                    if (isSpilled(inst.dest.name)) storeTo(inst.dest.name, reg32 = d)
                }
                Type.I64, Type.OpaquePointer, is Type.Pointer -> {
                    val d = getDest64(inst.dest.name)
                    val addr = resolvePointer(ptr, r11_64)
                    emitLoadMem64(d, addr, 0)
                    if (isSpilled(inst.dest.name)) storeTo(inst.dest.name, reg64 = d)
                }
                Type.I8 -> {
                    val d = getDest32(inst.dest.name)
                    val addr = resolvePointer(ptr, r11_64)
                    emitMovzxMem8(d, addr, 0)
                    if (isSpilled(inst.dest.name)) storeTo(inst.dest.name, reg32 = d)
                }
                Type.I16 -> {
                    val d = getDest32(inst.dest.name)
                    val addr = resolvePointer(ptr, r11_64)
                    emitMovzxMem16(d, addr, 0)
                    if (isSpilled(inst.dest.name)) storeTo(inst.dest.name, reg32 = d)
                }
                else -> error("Unsupported load type: $destType")
            }
        }

        private fun emitStore(inst: Store) {
            val ptr = inst.ptr
            val val_ = inst.value
            val addr = resolvePointer(ptr, r11_64)
            when (val_.type) {
                Type.I32 -> {
                    val src = if (val_ is Constant.I32) { loadValue32(val_, r10d32); r10d32 }
                              else getOrLoad32(val_.name, r10d32)
                    emitStoreMem32(addr, 0, src)
                }
                Type.I64, Type.OpaquePointer, is Type.Pointer -> {
                    val src = if (val_ is Constant.I64) { loadValue64(val_, r10_64); r10_64 }
                              else getOrLoad64(val_.name, r10_64)
                    emitStoreMem64(addr, 0, src)
                }
                Type.I8 -> {
                    val src = if (val_ is Constant.I8) { loadValue32(Constant.I32(val_.value.toInt()), r10d32); r10d32 }
                              else getOrLoad32(val_.name, r10d32)
                    emitStoreMem8(addr, 0, src)
                }
                Type.I16 -> {
                    val src = if (val_ is Constant.I16) { loadValue32(Constant.I32(val_.value.toInt()), r10d32); r10d32 }
                              else getOrLoad32(val_.name, r10d32)
                    emitStoreMem16(addr, 0, src)
                }
                else -> error("Unsupported store type: ${val_.type}")
            }
        }

        // ── Varargs (System V AMD64 ABI) ────────────────────────────

        private fun emitVAStart(inst: VAStart) {
            // va_list layout (System V): { u32 gp_offset, u32 fp_offset, ptr overflow_arg_area, ptr reg_save_area }
            val vaListPtr = getOrLoad64(inst.argList.name, r11_64)

            // gp_offset: number of named GP params × 8
            val gpOffset = namedGpParams * 8
            asm.mov(X86Memory.base(vaListPtr).offset(0) as X86Operand32, gpOffset)

            // fp_offset: 48 + number of named FP params × 16 (6 GP regs × 8 = 48 is the FP start)
            val fpOffset = 48 + namedFpParams * 16
            asm.mov(X86Memory.base(vaListPtr).offset(4) as X86Operand32, fpOffset)

            // overflow_arg_area: pointer to stack args = RBP + 16 (past saved RBP + return addr)
            asm.lea(r10_64, X86Memory.base(rbp64).offset(16))
            asm.mov(X86Memory.base(vaListPtr).offset(8), r10_64)

            // reg_save_area: pointer to where we saved the arg registers
            asm.lea(r10_64, X86Memory.base(rbp64).offset(regSaveAreaOffset))
            asm.mov(X86Memory.base(vaListPtr).offset(16), r10_64)
        }

        private fun emitVAArg(inst: VAArg) {
            val vaListPtr = getOrLoad64(inst.argList.name, r11_64)
            val dest = inst.dest
            val isGp = inst.argType != Type.F64 && inst.argType != Type.F32

            if (isGp) {
                // Read gp_offset
                emitLoadMem32(r10d32, vaListPtr, 0)
                // Compare gp_offset with 48 (6 regs × 8)
                asm.cmp(r10d32 as X86Operand32, 48)
                val overflowLabel = "${fn.name}.vaarg_overflow_${asm.position()}"
                val doneLabel = "${fn.name}.vaarg_done_${asm.position()}"
                asm.jccLabel(0x03, overflowLabel) // JAE

                // Register path: load from reg_save_area + gp_offset
                // Load reg_save_area pointer
                emitLoadMem64(rax64, vaListPtr, 16)
                // rax = reg_save_area + gp_offset (r10 zero-extended from 32-bit load)
                asm.mov(rax64, X86Memory.base(rax64).index(r10_64).build())
                // Advance gp_offset += 8
                asm.add(r10d32 as X86Operand32, 8)
                emitStoreMem32(vaListPtr, 0, r10d32)
                asm.jmpLabel(doneLabel)

                // Overflow path: load from overflow_arg_area
                asm.label(overflowLabel)
                emitLoadMem64(rax64, vaListPtr, 8)
                asm.mov(rax64, X86Memory.base(rax64).build() as X86Operand64)
                // Advance overflow_arg_area += 8
                emitLoadMem64(r10_64, vaListPtr, 8)
                asm.add(r10_64 as X86Operand64, 8)
                emitStoreMem64(vaListPtr, 8, r10_64)

                asm.label(doneLabel)
                // Result is in rax
                if (dest.type.is64Bit()) {
                    val d = getDest64(dest.name)
                    if (d != rax64) asm.mov(d, rax64 as X86Operand64)
                    if (isSpilled(dest.name)) storeTo(dest.name, reg64 = d)
                } else {
                    val d = getDest32(dest.name)
                    if (d != eax32) asm.mov(d, eax32 as X86Operand32)
                    if (isSpilled(dest.name)) storeTo(dest.name, reg32 = d)
                }
            } else {
                // FP varargs: for now, only read from overflow area
                emitLoadMem64(rax64, vaListPtr, 8)
                // Load XMM from [overflow_arg_area]
                val d = getDestXmm(dest.name)
                asm.movq(d, X86Memory.base(rax64).build())
                // Advance overflow_arg_area += 8
                asm.add(rax64 as X86Operand64, 8)
                emitStoreMem64(vaListPtr, 8, rax64)
                if (isSpilled(dest.name)) storeToXmm(dest.name, d)
            }
        }

        private fun emitVACopy(inst: VACopy) {
            // Copy 24 bytes from src va_list to dst va_list
            val src = getOrLoad64(inst.src.name, r10_64)
            val dst = getOrLoad64(inst.dst.name, r11_64)
            for (off in listOf(0, 8, 16)) {
                emitLoadMem64(rax64, src, off)
                emitStoreMem64(dst, off, rax64)
            }
        }

        private fun emitGCSafepoint() {
            if (!hasGcStrategy) return
            val offset = (asm.position() - funcStartOffset).toLong()
            val locations = collectGcLocations()
            stackMapEntries.add(StackMapEntry(offset, locations))

            // Emit call to safepoint poll function
            val off = asm.callExtern()
            relocations.add(Relocation(
                offset = off.toLong(), symbol = "kgen_safepoint_poll",
                type = RelocationType.X86_64.PLT32, addend = -4, section = ".text"))
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
                    is Location.Reg64 -> locations.add(StackMapLocation.Register((loc.reg as X86Register).encoding))
                    is Location.Reg32 -> locations.add(StackMapLocation.Register((loc.reg as X86Register).encoding))
                    is Location.RegXmm -> {}
                    is Location.Spill -> locations.add(StackMapLocation.Stack(loc.offset))
                }
            }
            for (rootName in gcRoots) {
                val allocaOff = allocaOffsets[rootName]
                if (allocaOff != null) {
                    locations.add(StackMapLocation.Stack(allocaOff))
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

        private fun Type.is64Bit(): Boolean = when (this) {
            Type.I64, Type.F64, Type.OpaquePointer, is Type.Pointer -> true
            else -> false
        }

        // Floating point instruction helpers

        private fun emitFBinOp(dest: InstructionRef, lhs: Value, rhs: Value, op: (X86Xmm, X86Xmm) -> Unit) {
            val d = getDestXmm(dest.name)
            loadValueXmm(lhs, d)
            val rhsReg = if (rhs is Parameter || rhs is InstructionRef) {
                getOrLoadXmm(rhs.name, if (d == xmm15) xmm14 else xmm15)
            } else {
                val scratch = if (d == xmm15) xmm14 else xmm15
                loadValueXmm(rhs, scratch)
                scratch
            }
            op(d, rhsReg)
            if (isSpilled(dest.name)) storeToXmm(dest.name, d)
        }

        private fun emitFAbs(inst: FAbs) {
            val d = getDestXmm(inst.dest.name)
            val src = if (inst.operand is Parameter || inst.operand is InstructionRef) {
                getOrLoadXmm(inst.operand.name, if (d == xmm15) xmm14 else xmm15)
            } else {
                val scratch = if (d == xmm15) xmm14 else xmm15
                loadValueXmm(inst.operand, scratch)
                scratch
            }
            if (d != src) asm.movsd(d, src)
            // Load abs mask (clear sign bit): 0x7FFFFFFFFFFFFFFF
            val maskReg = if (d != xmm13) xmm13 else xmm12
            loadValueXmm(Constant.F64(java.lang.Double.longBitsToDouble(0x7FFFFFFFFFFFFFFFL)), maskReg)
            asm.andpd(d, maskReg)
            if (isSpilled(inst.dest.name)) storeToXmm(inst.dest.name, d)
        }

        private fun emitCopySign(inst: CopySign) {
            val d = getDestXmm(inst.dest.name)
            // Load magnitude, clear its sign bit
            loadValueXmm(inst.magnitude, d)
            val maskReg = if (d != xmm13) xmm13 else xmm12
            loadValueXmm(Constant.F64(java.lang.Double.longBitsToDouble(0x7FFFFFFFFFFFFFFFL)), maskReg)
            asm.andpd(d, maskReg)
            // Load sign, extract its sign bit
            val signReg = if (d != xmm14) xmm14 else xmm15
            loadValueXmm(inst.sign, signReg)
            val signMaskReg = if (signReg != xmm12 && d != xmm12) xmm12 else xmm11
            loadValueXmm(Constant.F64(java.lang.Double.longBitsToDouble(Long.MIN_VALUE)), signMaskReg) // 0x8000000000000000
            asm.andpd(signReg, signMaskReg)
            // Combine: magnitude | sign
            asm.orpd(d, signReg)
            if (isSpilled(inst.dest.name)) storeToXmm(inst.dest.name, d)
        }

        private fun emitIntMinMax(dest: InstructionRef, lhs: Value, rhs: Value, signed: Boolean, isMin: Boolean) {
            // cmov: pick rhs if lhs is NOT the desired extreme
            // SMin: cmovg (pick rhs if lhs > rhs), SMax: cmovl, UMin: cmova, UMax: cmovb
            val cc = when {
                signed && isMin -> 0x4F   // CMOVG
                signed && !isMin -> 0x4C  // CMOVL
                !signed && isMin -> 0x47  // CMOVA
                else -> 0x42              // CMOVB
            }
            when (dest.type) {
                Type.I64, Type.OpaquePointer, is Type.Pointer -> {
                    val d = getDest64(dest.name)
                    loadValue64(lhs, d)
                    val rhsReg = if (d == r11_64) r10_64 else r11_64
                    loadValue64(rhs, rhsReg)
                    asm.cmp(d, rhsReg as X86Operand64)
                    emitCmov64(cc, d, rhsReg)
                    if (isSpilled(dest.name)) storeTo(dest.name, reg64 = d)
                }
                else -> {
                    val d = getDest32(dest.name)
                    loadValue32(lhs, d)
                    val rhsReg = if (d == r11d32) r10d32 else r11d32
                    loadValue32(rhs, rhsReg)
                    asm.cmp(d, rhsReg as X86Operand32)
                    emitCmov32(cc, d, rhsReg)
                    if (isSpilled(dest.name)) storeTo(dest.name, reg32 = d)
                }
            }
        }

        private fun emitFRem(inst: FRem) {
            // x86 doesn't have a direct float remainder instruction in SSE
            // Use x87 FPU: fprem (partial remainder, IEEE 754 behavior)
            // Or compute: result = lhs - trunc(lhs / rhs) * rhs
            val d = getDestXmm(inst.dest.name)
            val lhsXmm = if (inst.lhs is Parameter || inst.lhs is InstructionRef) {
                getOrLoadXmm(inst.lhs.name, if (d == xmm15) xmm14 else xmm15)
            } else {
                val s = if (d == xmm15) xmm14 else xmm15
                loadValueXmm(inst.lhs, s)
                s
            }
            val rhsXmm = if (inst.rhs is Parameter || inst.rhs is InstructionRef) {
                val s = if (d == xmm15 || d == lhsXmm) xmm13 else xmm15
                getOrLoadXmm(inst.rhs.name, s)
            } else {
                val s = if (d == xmm15 || lhsXmm == xmm15) xmm13 else xmm15
                loadValueXmm(inst.rhs, s)
                s
            }

            // d = lhs / rhs
            if (d != lhsXmm) asm.movsd(d, lhsXmm)
            asm.divsd(d, rhsXmm)
            // truncate: roundsd d, d, 3 (round toward zero)
            asm.roundsd(d, d, 0x03)
            // d = trunc * rhs
            asm.mulsd(d, rhsXmm)
            // result = lhs - d
            // Need lhs in a temp
            val lhsTemp = if (lhsXmm == d) {
                // lhs was in d and we clobbered it — this is a problem.
                // We need to reload lhs
                val t = xmm13
                loadValueXmm(inst.lhs, t)
                t
            } else lhsXmm

            // Move lhsTemp to a temp, then sub
            val resultXmm = if (d == xmm13) xmm12 else xmm13
            asm.movsd(resultXmm, lhsTemp)
            asm.subsd(resultXmm, d)
            if (d != resultXmm) asm.movsd(d, resultXmm)
            if (isSpilled(inst.dest.name)) storeToXmm(inst.dest.name, d)
        }

        private fun emitFMA(inst: FMA) {
            // FMA: dest = a * b + c
            // vfmadd213: dest = dest * src2 + src3  (put a in dest, b in src2, c in src3)
            val d = getDestXmm(inst.dest.name)
            val aXmm = if (inst.a is Parameter || inst.a is InstructionRef) {
                getOrLoadXmm(inst.a.name, if (d == xmm15) xmm14 else xmm15)
            } else {
                val s = if (d == xmm15) xmm14 else xmm15
                loadValueXmm(inst.a, s)
                s
            }
            val bXmm = if (inst.b is Parameter || inst.b is InstructionRef) {
                val s = if (d == xmm15 || aXmm == xmm15) xmm13 else xmm15
                getOrLoadXmm(inst.b.name, s)
            } else {
                val s = if (d == xmm15 || aXmm == xmm15) xmm13 else xmm15
                loadValueXmm(inst.b, s)
                s
            }
            val cXmm = if (inst.c is Parameter || inst.c is InstructionRef) {
                val s = pickScratchXmm(d, aXmm, bXmm)
                getOrLoadXmm(inst.c.name, s)
            } else {
                val s = pickScratchXmm(d, aXmm, bXmm)
                loadValueXmm(inst.c, s)
                s
            }

            // Move a into dest for the vfmadd213 destructive form
            if (d != aXmm) {
                asm.movsd(d, aXmm)
            }

            val isFloat = inst.dest.type == Type.F32
            if (isFloat) {
                asm.vfmadd213ss(d, bXmm, cXmm)
            } else {
                asm.vfmadd213sd(d, bXmm, cXmm)
            }
            if (isSpilled(inst.dest.name)) storeToXmm(inst.dest.name, d)
        }

        private fun pickScratchXmm(vararg avoid: X86Xmm): X86Xmm {
            val candidates = listOf(xmm15, xmm14, xmm13, xmm12)
            return candidates.first { it !in avoid }
        }

        private fun emitSatArith(dest: InstructionRef, lhs: Value, rhs: Value, signed: Boolean, isAdd: Boolean) {
            // Saturating: if overflow, clamp to max/min instead of wrapping
            // Use branchless approach with cmov
            when (dest.type) {
                Type.I64, Type.OpaquePointer, is Type.Pointer -> {
                    val d = getDest64(dest.name)
                    loadValue64(lhs, d)
                    val rhsReg = if (d == r11_64) r10_64 else r11_64
                    loadValue64(rhs, rhsReg)

                    if (isAdd) asm.add(d, rhsReg as X86Operand64)
                    else asm.sub(d, rhsReg as X86Operand64)

                    if (signed) {
                        // On signed overflow (OF=1): use saturated value
                        // Load MAX into scratch, CMOVO d, scratch
                        // For correct saturation direction, we'd need to check SF too
                        // Simplified: just clamp to MAX on overflow (close enough for most uses)
                        emitLoadImm64(rhsReg, Long.MAX_VALUE)
                        emitCmov64(0x40, d, rhsReg) // CMOVO (overflow → use MAX)
                    } else {
                        // Unsigned: if carry, set to ULONG_MAX
                        emitLoadImm64(rhsReg, -1L)
                        emitCmov64(0x42, d, rhsReg) // CMOVC (carry → use MAX)
                    }
                    if (isSpilled(dest.name)) storeTo(dest.name, reg64 = d)
                }
                else -> {
                    val d = getDest32(dest.name)
                    loadValue32(lhs, d)
                    val rhsReg = if (d == r11d32) r10d32 else r11d32
                    loadValue32(rhs, rhsReg)

                    if (isAdd) asm.add(d, rhsReg as X86Operand32)
                    else asm.sub(d, rhsReg as X86Operand32)

                    if (signed) {
                        asm.mov(rhsReg, Int.MAX_VALUE)
                        emitCmov32(0x40, d, rhsReg) // CMOVO
                    } else {
                        asm.mov(rhsReg, -1)
                        emitCmov32(0x42, d, rhsReg) // CMOVC
                    }
                    if (isSpilled(dest.name)) storeTo(dest.name, reg32 = d)
                }
            }
        }

        private fun emitOverflowArith(dest: InstructionRef, lhs: Value, rhs: Value, signed: Boolean, op: String) {
            // Overflow-checked: result is a struct {value, overflowed: i1}
            // We store the result struct in the spill slot: [value, overflow_flag]
            val destLoc = alloc.locations[dest.name]
            val baseOffset = when (destLoc) {
                is Location.Spill -> destLoc.offset
                else -> error("Overflow arithmetic result must be spilled (struct)")
            }

            when (dest.type) {
                is Type.Struct -> {
                    val fieldType = (dest.type as Type.Struct).fields[0]
                    val is64 = fieldType == Type.I64 || fieldType == Type.OpaquePointer || fieldType is Type.Pointer
                    val flagCc = if (signed) 0x90 else 0x92 // SETO / SETC

                    if (is64) {
                        val d = r10_64
                        loadValue64(lhs, d)
                        val rhsReg = r11_64
                        loadValue64(rhs, rhsReg)

                        when (op) {
                            "add" -> asm.add(d, rhsReg as X86Operand64)
                            "sub" -> asm.sub(d, rhsReg as X86Operand64)
                            "mul" -> asm.imul(d, rhsReg as X86Operand64)
                        }
                        emitStoreToRbp64(d, baseOffset)

                        // SETcc r10b: REX 0F cc C0|reg
                        val enc = (d as X86Register).encoding
                        asm.emitByte(0x41) // REX.B for r10
                        asm.emitBytes(0x0F, flagCc)
                        asm.emitByte(0xC0 or (enc and 7))
                        // Zero-extend r10b to r10 via AND r10d, 0xFF
                        asm.and_(r10d32 as X86Operand32, 0xFF as Int)
                        emitStoreToRbp64(r10_64, baseOffset + 8)
                    } else {
                        val d = r10d32
                        loadValue32(lhs, d)
                        val rhsReg = r11d32
                        loadValue32(rhs, rhsReg)

                        when (op) {
                            "add" -> asm.add(d, rhsReg as X86Operand32)
                            "sub" -> asm.sub(d, rhsReg as X86Operand32)
                            "mul" -> asm.imul(d, rhsReg as X86Operand32)
                        }
                        emitStoreToRbp32(d, baseOffset)

                        val enc = (d as X86Register).encoding
                        if (enc >= 8) asm.emitByte(0x41)
                        asm.emitBytes(0x0F, flagCc)
                        asm.emitByte(0xC0 or (enc and 7))
                        asm.and_(r10d32 as X86Operand32, 0xFF as Int)
                        emitStoreToRbp32(r10d32, baseOffset + 4)
                    }
                }
                else -> error("Overflow arithmetic must return a struct type, got: ${dest.type}")
            }
        }

        private fun emitLoadImm64(dest: X86Register64, value: Long) {
            // movabs dest, imm64
            val enc = (dest as X86Register).encoding
            asm.emitByte(0x48 or ((enc shr 3) and 1))
            asm.emitByte(0xB8 or (enc and 7))
            asm.emitInt64(value)
        }

        private fun emitAtomicRMW(inst: AtomicRMW) {
            // lock prefix + operation on [ptr]
            // Result is the old value at [ptr] before the operation
            val ptrReg = r10_64
            loadValue64(inst.ptr, ptrReg)

            when (inst.op) {
                AtomicRMWOp.XCHG -> {
                    // xchg is implicitly locked
                    when (inst.dest.type) {
                        Type.I64, Type.OpaquePointer, is Type.Pointer -> {
                            val dest = getDest64(inst.dest.name)
                            loadValue64(inst.value, dest)
                            // xchg [ptr], dest — REX.W 87 /r with memory operand
                            val dEnc = (dest as X86Register).encoding
                            val bEnc = (ptrReg as X86Register).encoding
                            asm.emitByte(0x48 or ((dEnc shr 3) shl 2) or (bEnc shr 3))
                            asm.emitByte(0x87)
                            asm.emitByte(((dEnc and 7) shl 3) or (bEnc and 7))
                            if (isSpilled(inst.dest.name)) storeTo(inst.dest.name, reg64 = dest)
                        }
                        else -> {
                            val dest = getDest32(inst.dest.name)
                            loadValue32(inst.value, dest)
                            val dEnc = (dest as X86Register).encoding
                            val bEnc = (ptrReg as X86Register).encoding
                            if (dEnc >= 8 || bEnc >= 8) asm.emitByte(0x40 or ((dEnc shr 3) shl 2) or (bEnc shr 3))
                            asm.emitByte(0x87)
                            asm.emitByte(((dEnc and 7) shl 3) or (bEnc and 7))
                            if (isSpilled(inst.dest.name)) storeTo(inst.dest.name, reg32 = dest)
                        }
                    }
                }
                AtomicRMWOp.ADD -> emitLockOp(inst, 0x01, 0x00) // lock xadd
                AtomicRMWOp.SUB -> {
                    // lock xadd with negated value
                    when (inst.dest.type) {
                        Type.I64, Type.OpaquePointer, is Type.Pointer -> {
                            val dest = getDest64(inst.dest.name)
                            loadValue64(inst.value, dest)
                            asm.neg(dest as X86Operand64)
                            emitLockXadd64(ptrReg, dest)
                            if (isSpilled(inst.dest.name)) storeTo(inst.dest.name, reg64 = dest)
                        }
                        else -> {
                            val dest = getDest32(inst.dest.name)
                            loadValue32(inst.value, dest)
                            asm.neg(dest as X86Operand32)
                            emitLockXadd32(ptrReg, dest)
                            if (isSpilled(inst.dest.name)) storeTo(inst.dest.name, reg32 = dest)
                        }
                    }
                }
                else -> {
                    // For AND, OR, XOR, etc.: use cmpxchg loop
                    // Load old value, compute new, cmpxchg, retry if failed
                    emitAtomicRMWLoop(inst, ptrReg)
                }
            }
        }

        private fun emitLockOp(inst: AtomicRMW, opcode: Int, subOpcode: Int) {
            // lock xadd for ADD
            when (inst.dest.type) {
                Type.I64, Type.OpaquePointer, is Type.Pointer -> {
                    val dest = getDest64(inst.dest.name)
                    loadValue64(inst.value, dest)
                    emitLockXadd64(r10_64, dest)
                    if (isSpilled(inst.dest.name)) storeTo(inst.dest.name, reg64 = dest)
                }
                else -> {
                    val dest = getDest32(inst.dest.name)
                    loadValue32(inst.value, dest)
                    emitLockXadd32(r10_64, dest)
                    if (isSpilled(inst.dest.name)) storeTo(inst.dest.name, reg32 = dest)
                }
            }
        }

        private fun emitLockXadd64(ptr: X86Register64, val64: X86Register64) {
            // lock xadd [ptr], val — F0 REX.W 0F C1 /r
            val vEnc = (val64 as X86Register).encoding
            val bEnc = (ptr as X86Register).encoding
            asm.emitByte(0xF0) // LOCK
            asm.emitByte(0x48 or ((vEnc shr 3) shl 2) or (bEnc shr 3))
            asm.emitBytes(0x0F, 0xC1)
            asm.emitByte(((vEnc and 7) shl 3) or (bEnc and 7))
        }

        private fun emitLockXadd32(ptr: X86Register64, val32: X86Register32) {
            // lock xadd [ptr], val — F0 0F C1 /r
            val vEnc = (val32 as X86Register).encoding
            val bEnc = (ptr as X86Register).encoding
            asm.emitByte(0xF0)
            if (vEnc >= 8 || bEnc >= 8) asm.emitByte(0x40 or ((vEnc shr 3) shl 2) or (bEnc shr 3))
            asm.emitBytes(0x0F, 0xC1)
            asm.emitByte(((vEnc and 7) shl 3) or (bEnc and 7))
        }

        private fun emitAtomicRMWLoop(inst: AtomicRMW, ptrReg: X86Register64) {
            // Generic atomic RMW via cmpxchg loop:
            // retry: mov rax, [ptr]       ; load old value
            //        mov tmp, rax
            //        <op> tmp, value       ; compute new value
            //        lock cmpxchg [ptr], tmp
            //        jne retry             ; retry if rax changed
            //        ; rax = old value (result)
            val retryOffset = asm.position()
            val is64 = inst.dest.type.is64Bit()

            if (is64) {
                // mov rax, [ptr]
                emitLoadMem64(rax64, ptrReg, 0)
                // mov r11, rax
                asm.mov(r11_64, rax64 as X86Operand64)
                // apply op to r11
                loadValue64(inst.value, r10_64) // reuse r10 — ptr already loaded
                // But wait, ptrReg IS r10! Need to reload ptr after.
                // Actually, let's use a different approach. Load ptr first, then keep it.
                // The cmpxchg will use rax as expected value.
                loadValue64(inst.value, rcx64)
                emitAtomicOp64(inst.op, r11_64, rcx64)
                // lock cmpxchg [ptr], r11 — F0 REX.W 0F B1 /r
                asm.emitByte(0xF0)
                asm.emitByte(0x4C or ((ptrReg as X86Register).encoding shr 3))
                asm.emitBytes(0x0F, 0xB1)
                val bEnc = (ptrReg as X86Register).encoding
                asm.emitByte(0x18 or (bEnc and 7)) // r11 = /3
                // jne retry
                val jneTarget = retryOffset - asm.position() - 2
                asm.emitByte(0x75)
                asm.emitByte(jneTarget and 0xFF)
                // result in rax
                val dest = getDest64(inst.dest.name)
                if (dest != rax64) asm.mov(dest, rax64 as X86Operand64)
                if (isSpilled(inst.dest.name)) storeTo(inst.dest.name, reg64 = dest)
            } else {
                emitLoadMem32(eax32, ptrReg, 0)
                asm.mov(r11d32, eax32 as X86Operand32)
                loadValue32(inst.value, ecx32)
                emitAtomicOp32(inst.op, r11d32, ecx32)
                asm.emitByte(0xF0)
                val bEnc = (ptrReg as X86Register).encoding
                if (bEnc >= 8) asm.emitByte(0x41)
                asm.emitBytes(0x0F, 0xB1)
                asm.emitByte(0x18 or (bEnc and 7))
                val jneTarget = retryOffset - asm.position() - 2
                asm.emitByte(0x75)
                asm.emitByte(jneTarget and 0xFF)
                val dest = getDest32(inst.dest.name)
                if (dest != eax32) asm.mov(dest, eax32 as X86Operand32)
                if (isSpilled(inst.dest.name)) storeTo(inst.dest.name, reg32 = dest)
            }
        }

        private fun emitAtomicOp64(op: AtomicRMWOp, dest: X86Register64, src: X86Register64) {
            when (op) {
                AtomicRMWOp.AND -> asm.and_(dest, src as X86Operand64)
                AtomicRMWOp.OR -> asm.or_(dest, src as X86Operand64)
                AtomicRMWOp.XOR -> asm.xor_(dest, src as X86Operand64)
                AtomicRMWOp.MAX, AtomicRMWOp.MIN -> {
                    asm.cmp(dest, src as X86Operand64)
                    val cc = if (op == AtomicRMWOp.MAX) 0x4C else 0x4F // CMOVL / CMOVG
                    emitCmov64(cc, dest, src)
                }
                AtomicRMWOp.UMAX, AtomicRMWOp.UMIN -> {
                    asm.cmp(dest, src as X86Operand64)
                    val cc = if (op == AtomicRMWOp.UMAX) 0x42 else 0x47 // CMOVB / CMOVA
                    emitCmov64(cc, dest, src)
                }
                AtomicRMWOp.NAND -> {
                    asm.and_(dest, src as X86Operand64)
                    // NOT r64: REX.W F7 /2
                    val enc = (dest as X86Register).encoding
                    asm.emitByte(0x48 or ((enc shr 3) and 1))
                    asm.emitByte(0xF7)
                    asm.emitByte(0xD0 or (enc and 7))
                }
                else -> error("Unsupported AtomicRMW operation: $op")
            }
        }

        private fun emitAtomicOp32(op: AtomicRMWOp, dest: X86Register32, src: X86Register32) {
            when (op) {
                AtomicRMWOp.AND -> asm.and_(dest, src as X86Operand32)
                AtomicRMWOp.OR -> asm.or_(dest, src as X86Operand32)
                AtomicRMWOp.XOR -> asm.xor_(dest, src as X86Operand32)
                AtomicRMWOp.MAX, AtomicRMWOp.MIN -> {
                    asm.cmp(dest, src as X86Operand32)
                    val cc = if (op == AtomicRMWOp.MAX) 0x4C else 0x4F
                    emitCmov32(cc, dest, src)
                }
                AtomicRMWOp.UMAX, AtomicRMWOp.UMIN -> {
                    asm.cmp(dest, src as X86Operand32)
                    val cc = if (op == AtomicRMWOp.UMAX) 0x42 else 0x47
                    emitCmov32(cc, dest, src)
                }
                AtomicRMWOp.NAND -> {
                    asm.and_(dest, src as X86Operand32)
                    // NOT r32: F7 /2
                    val enc = (dest as X86Register).encoding
                    if (enc >= 8) asm.emitByte(0x41)
                    asm.emitByte(0xF7)
                    asm.emitByte(0xD0 or (enc and 7))
                }
                else -> error("Unsupported AtomicRMW operation: $op")
            }
        }

        private fun emitCmpXchg(inst: CmpXchg) {
            // cmpxchg: RAX = expected, operand = new value
            // If [ptr] == RAX, set ZF and store new → [ptr], else load [ptr] → RAX
            // Result is old value
            val ptrReg = r10_64
            loadValue64(inst.ptr, ptrReg)

            when (inst.dest.type) {
                Type.I64, Type.OpaquePointer, is Type.Pointer -> {
                    loadValue64(inst.cmp, rax64)
                    loadValue64(inst.new, r11_64)
                    // lock cmpxchg [ptr], r11 — F0 REX.W 0F B1 /r
                    val bEnc = (ptrReg as X86Register).encoding
                    asm.emitByte(0xF0)
                    asm.emitByte(0x4C or (bEnc shr 3))
                    asm.emitBytes(0x0F, 0xB1)
                    asm.emitByte(0x18 or (bEnc and 7))
                    val dest = getDest64(inst.dest.name)
                    if (dest != rax64) asm.mov(dest, rax64 as X86Operand64)
                    if (isSpilled(inst.dest.name)) storeTo(inst.dest.name, reg64 = dest)
                }
                else -> {
                    loadValue32(inst.cmp, eax32)
                    loadValue32(inst.new, r11d32)
                    val bEnc = (ptrReg as X86Register).encoding
                    asm.emitByte(0xF0)
                    if (bEnc >= 8) asm.emitByte(0x41)
                    asm.emitBytes(0x0F, 0xB1)
                    asm.emitByte(0x18 or (bEnc and 7))
                    val dest = getDest32(inst.dest.name)
                    if (dest != eax32) asm.mov(dest, eax32 as X86Operand32)
                    if (isSpilled(inst.dest.name)) storeTo(inst.dest.name, reg32 = dest)
                }
            }
        }

        private fun emitFNeg(inst: FNeg) {
            // fneg = xor with sign bit mask, or sub from 0
            // Simplest: xorpd with sign mask. But easier: sub from 0
            val d = getDestXmm(inst.dest.name)
            // pxor d, d (zero it)
            emitPxor(d, d)
            val src = if (inst.operand is Parameter || inst.operand is InstructionRef) {
                getOrLoadXmm(inst.operand.name, if (d == xmm15) xmm14 else xmm15)
            } else {
                val scratch = if (d == xmm15) xmm14 else xmm15
                loadValueXmm(inst.operand, scratch)
                scratch
            }
            asm.subsd(d, src)
            if (isSpilled(inst.dest.name)) storeToXmm(inst.dest.name, d)
        }

        private fun emitFCmp(inst: FCmp) {
            val lhsReg = getOrLoadXmm(inst.lhs.name, xmm14)
            val rhsReg = if (inst.rhs is Parameter || inst.rhs is InstructionRef) {
                getOrLoadXmm(inst.rhs.name, xmm15)
            } else {
                loadValueXmm(inst.rhs, xmm15); xmm15
            }

            // Zero dest BEFORE ucomisd — xor clobbers flags
            val dest = getDest32(inst.dest.name)
            asm.xor_(dest, dest as X86Operand32)
            asm.ucomisd(lhsReg, rhsReg)
            val cc = when (inst.predicate) {
                FCmpPredicate.OEQ -> 0x04  // JE (and not unordered)
                FCmpPredicate.ONE -> 0x05  // JNE
                FCmpPredicate.OLT -> 0x02  // JB (below, for ordered)
                FCmpPredicate.OLE -> 0x06  // JBE
                FCmpPredicate.OGT -> 0x07  // JA (above, for ordered)
                FCmpPredicate.OGE -> 0x03  // JAE
                FCmpPredicate.UEQ -> 0x04
                FCmpPredicate.UNE -> 0x05
                FCmpPredicate.ULT -> 0x02
                FCmpPredicate.ULE -> 0x06
                FCmpPredicate.UGT -> 0x07
                FCmpPredicate.UGE -> 0x03
                FCmpPredicate.ORD -> 0x0B  // JNP (not parity = ordered)
                FCmpPredicate.UNO -> 0x0A  // JP (parity = unordered)
                FCmpPredicate.TRUE -> { asm.mov(dest, 1); if (isSpilled(inst.dest.name)) storeTo(inst.dest.name, reg32 = dest); return }
                FCmpPredicate.FALSE -> { if (isSpilled(inst.dest.name)) storeTo(inst.dest.name, reg32 = dest); return }
            }
            val enc = (dest as X86Register).encoding
            if (enc >= 4) asm.emitByte(0x40 or (if (enc >= 8) 0x01 else 0))
            asm.emitBytes(0x0F, 0x90 + cc)
            asm.emitByte(0xC0 or (enc and 7))
            if (isSpilled(inst.dest.name)) storeTo(inst.dest.name, reg32 = dest)
        }

        private fun emitSIToFP(inst: SIToFP) {
            val d = getDestXmm(inst.dest.name)
            val toF32 = inst.toType == Type.F32
            when (inst.value.type) {
                Type.I32 -> {
                    val src = getOrLoad32(inst.value.name, r11d32)
                    if (toF32) asm.cvtsi2ss(d, src as X86Operand32)
                    else asm.cvtsi2sd(d, src as X86Operand32)
                }
                Type.I64 -> {
                    val src = getOrLoad64(inst.value.name, r11_64)
                    if (toF32) asm.cvtsi2ss(d, src as X86Operand64)
                    else asm.cvtsi2sd(d, src as X86Operand64)
                }
                else -> error("Unsupported sitofp source: ${inst.value.type}")
            }
            if (isSpilled(inst.dest.name)) storeToXmm(inst.dest.name, d)
        }

        private fun emitFPToSI(inst: FPToSI) {
            val src = getOrLoadXmm(inst.value.name, xmm15)
            val fromF32 = inst.value.type == Type.F32
            when (inst.toType) {
                Type.I32 -> {
                    val d = getDest32(inst.dest.name)
                    if (fromF32) asm.cvtss2si(d, src) else asm.cvtsd2si(d, src)
                    if (isSpilled(inst.dest.name)) storeTo(inst.dest.name, reg32 = d)
                }
                Type.I64 -> {
                    val d = getDest64(inst.dest.name)
                    if (fromF32) asm.cvtss2si(d, src) else asm.cvtsd2si(d, src)
                    if (isSpilled(inst.dest.name)) storeTo(inst.dest.name, reg64 = d)
                }
                else -> error("Unsupported fptosi target: ${inst.toType}")
            }
        }

        private fun emitUIToFP(inst: UIToFP) {
            // Unsigned int to float: zero-extend to i64, then cvtsi2sd with 64-bit source
            val d = getDestXmm(inst.dest.name)
            when (inst.value.type) {
                Type.I32 -> {
                    // Zero-extend i32 → i64 (mov r32 auto-zeros upper bits), then cvtsi2sd r64
                    val src64 = r11_64
                    val src32 = r11d32
                    loadValue32(inst.value, src32)
                    // 32-bit mov already zero-extends to 64-bit
                    asm.cvtsi2sd(d, src64 as X86Operand64)
                }
                Type.I64 -> {
                    // For unsigned i64 → f64, we need to handle the sign bit
                    // Simple approach: use signed conversion (correct for values < 2^63)
                    val src = getOrLoad64(inst.value.name, r11_64)
                    asm.cvtsi2sd(d, src as X86Operand64)
                }
                else -> error("Unsupported uitofp source: ${inst.value.type}")
            }
            if (isSpilled(inst.dest.name)) storeToXmm(inst.dest.name, d)
        }

        private fun emitFPToUI(inst: FPToUI) {
            // Float to unsigned int: use cvtsd2si with 64-bit dest, then truncate
            val src = getOrLoadXmm(inst.value.name, xmm15)
            when (inst.toType) {
                Type.I32 -> {
                    // cvtsd2si to 64-bit, then use the 32-bit part
                    val d = getDest32(inst.dest.name)
                    val d64 = reg32to64(d)
                    asm.cvtsd2si(d64, src)
                    // The result is already in the lower 32 bits
                    if (isSpilled(inst.dest.name)) storeTo(inst.dest.name, reg32 = d)
                }
                Type.I64 -> {
                    val d = getDest64(inst.dest.name)
                    asm.cvtsd2si(d, src)
                    if (isSpilled(inst.dest.name)) storeTo(inst.dest.name, reg64 = d)
                }
                else -> error("Unsupported fptoui target: ${inst.toType}")
            }
        }

        private fun emitFPExt(inst: FPExt) {
            // f32 → f64: cvtss2sd
            val d = getDestXmm(inst.dest.name)
            val src = getOrLoadXmm(inst.value.name, if (d == xmm15) xmm14 else xmm15)
            // cvtss2sd d, src — F3 0F 5A /r
            val dEnc = (d as X86Register).encoding
            val sEnc = (src as X86Register).encoding
            asm.emitByte(0xF3)
            if (dEnc >= 8 || sEnc >= 8) asm.emitByte(0x40 or ((dEnc shr 3) shl 2) or (sEnc shr 3))
            asm.emitBytes(0x0F, 0x5A)
            asm.emitByte(0xC0 or ((dEnc and 7) shl 3) or (sEnc and 7))
            if (isSpilled(inst.dest.name)) storeToXmm(inst.dest.name, d)
        }

        private fun emitFPTrunc(inst: FPTrunc) {
            // f64 → f32: cvtsd2ss
            val d = getDestXmm(inst.dest.name)
            val src = getOrLoadXmm(inst.value.name, if (d == xmm15) xmm14 else xmm15)
            // cvtsd2ss d, src — F2 0F 5A /r
            val dEnc = (d as X86Register).encoding
            val sEnc = (src as X86Register).encoding
            asm.emitByte(0xF2)
            if (dEnc >= 8 || sEnc >= 8) asm.emitByte(0x40 or ((dEnc shr 3) shl 2) or (sEnc shr 3))
            asm.emitBytes(0x0F, 0x5A)
            asm.emitByte(0xC0 or ((dEnc and 7) shl 3) or (sEnc and 7))
            if (isSpilled(inst.dest.name)) storeToXmm(inst.dest.name, d)
        }

        private fun emitCtlz(inst: Ctlz) {
            when (inst.operand.type) {
                Type.I64 -> {
                    val d = getDest64(inst.dest.name)
                    loadValue64(inst.operand, r11_64)
                    asm.lzcnt(d, r11_64 as X86Operand64)
                    if (isSpilled(inst.dest.name)) storeTo(inst.dest.name, reg64 = d)
                }
                else -> {
                    val d = getDest32(inst.dest.name)
                    loadValue32(inst.operand, r11d32)
                    asm.lzcnt(d, r11d32 as X86Operand32)
                    if (isSpilled(inst.dest.name)) storeTo(inst.dest.name, reg32 = d)
                }
            }
        }

        private fun emitCttz(inst: Cttz) {
            when (inst.operand.type) {
                Type.I64 -> {
                    val d = getDest64(inst.dest.name)
                    loadValue64(inst.operand, r11_64)
                    asm.tzcnt(d, r11_64 as X86Operand64)
                    if (isSpilled(inst.dest.name)) storeTo(inst.dest.name, reg64 = d)
                }
                else -> {
                    val d = getDest32(inst.dest.name)
                    loadValue32(inst.operand, r11d32)
                    asm.tzcnt(d, r11d32 as X86Operand32)
                    if (isSpilled(inst.dest.name)) storeTo(inst.dest.name, reg32 = d)
                }
            }
        }

        private fun emitCtpop(inst: Ctpop) {
            when (inst.operand.type) {
                Type.I64 -> {
                    val d = getDest64(inst.dest.name)
                    loadValue64(inst.operand, r11_64)
                    asm.popcnt(d, r11_64 as X86Operand64)
                    if (isSpilled(inst.dest.name)) storeTo(inst.dest.name, reg64 = d)
                }
                else -> {
                    val d = getDest32(inst.dest.name)
                    loadValue32(inst.operand, r11d32)
                    asm.popcnt(d, r11d32 as X86Operand32)
                    if (isSpilled(inst.dest.name)) storeTo(inst.dest.name, reg32 = d)
                }
            }
        }

        private fun emitBSwap(inst: BSwap) {
            when (inst.operand.type) {
                Type.I64 -> {
                    val d = getDest64(inst.dest.name)
                    loadValue64(inst.operand, d)
                    asm.bswap(d)
                    if (isSpilled(inst.dest.name)) storeTo(inst.dest.name, reg64 = d)
                }
                else -> {
                    val d = getDest32(inst.dest.name)
                    loadValue32(inst.operand, d)
                    asm.bswap(d)
                    if (isSpilled(inst.dest.name)) storeTo(inst.dest.name, reg32 = d)
                }
            }
        }

        private fun emitBitReverse(inst: BitReverse) {
            when (inst.operand.type) {
                Type.I64 -> {
                    val d = getDest64(inst.dest.name)
                    loadValue64(inst.operand, d)
                    asm.bswap(d)
                    // Reverse bits within each byte using 3-step swap
                    bitReverseBytes64(d)
                    if (isSpilled(inst.dest.name)) storeTo(inst.dest.name, reg64 = d)
                }
                else -> {
                    val d = getDest32(inst.dest.name)
                    loadValue32(inst.operand, d)
                    asm.bswap(d)
                    bitReverseBytes32(d)
                    if (isSpilled(inst.dest.name)) storeTo(inst.dest.name, reg32 = d)
                }
            }
        }

        /** Reverse bits within each byte of a 64-bit register in place. Uses r10, r11 as scratch. */
        private fun bitReverseBytes64(d: X86Register64) {
            val dOp = d as X86Operand64
            val r10Op = r10_64 as X86Operand64
            val r11Op = r11_64 as X86Operand64
            // Step 1: swap odd/even bits — mask 0x5555555555555555
            asm.mov(r11_64, 0x5555555555555555L)
            asm.mov(r10_64, dOp); asm.shr(dOp, 1.toByte())
            asm.and_(d, r11Op); asm.and_(r10_64, r11Op)
            asm.shl(r10Op, 1.toByte()); asm.or_(d, r10Op)
            // Step 2: swap bit pairs — mask 0x3333333333333333
            asm.mov(r11_64, 0x3333333333333333L)
            asm.mov(r10_64, dOp); asm.shr(dOp, 2.toByte())
            asm.and_(d, r11Op); asm.and_(r10_64, r11Op)
            asm.shl(r10Op, 2.toByte()); asm.or_(d, r10Op)
            // Step 3: swap nibbles — mask 0x0F0F0F0F0F0F0F0F
            asm.mov(r11_64, 0x0F0F0F0F0F0F0F0FL)
            asm.mov(r10_64, dOp); asm.shr(dOp, 4.toByte())
            asm.and_(d, r11Op); asm.and_(r10_64, r11Op)
            asm.shl(r10Op, 4.toByte()); asm.or_(d, r10Op)
        }

        /** Reverse bits within each byte of a 32-bit register in place. Uses r10d, r11d as scratch. */
        private fun bitReverseBytes32(d: X86Register32) {
            val dOp = d as X86Operand32
            val r10Op = r10d32 as X86Operand32
            val r11Op = r11d32 as X86Operand32
            asm.mov(r11d32, 0x55555555)
            asm.mov(r10d32, dOp); asm.shr(dOp, 1.toByte())
            asm.and_(d, r11Op); asm.and_(r10d32, r11Op)
            asm.shl(r10Op, 1.toByte()); asm.or_(d, r10Op)

            asm.mov(r11d32, 0x33333333)
            asm.mov(r10d32, dOp); asm.shr(dOp, 2.toByte())
            asm.and_(d, r11Op); asm.and_(r10d32, r11Op)
            asm.shl(r10Op, 2.toByte()); asm.or_(d, r10Op)

            asm.mov(r11d32, 0x0F0F0F0F)
            asm.mov(r10d32, dOp); asm.shr(dOp, 4.toByte())
            asm.and_(d, r11Op); asm.and_(r10d32, r11Op)
            asm.shl(r10Op, 4.toByte()); asm.or_(d, r10Op)
        }

        private fun emitFpUnaryXmm(
            dest: InstructionRef, operand: Value,
            op: (X86Xmm, X86Xmm) -> Unit,
        ) {
            val d = getDestXmm(dest.name)
            val src = if (operand is Parameter || operand is InstructionRef) {
                getOrLoadXmm(operand.name, if (d == xmm15) xmm14 else xmm15)
            } else {
                val scratch = if (d == xmm15) xmm14 else xmm15
                loadValueXmm(operand, scratch)
                scratch
            }
            op(d, src)
            if (isSpilled(dest.name)) storeToXmm(dest.name, d)
        }

        private fun emitRepMovsb(dst: Value, src: Value, len: Value) {
            // rep movsb: RDI = dst, RSI = src, RCX = count
            loadValue64(dst, rdi64)
            loadValue64(src, rsi64)
            loadValue64(len, rcx64)
            asm.cld()
            asm.rep()
            asm.movsb()
        }

        private fun emitMemSet(inst: MemSet) {
            // rep stosb: RDI = dst, AL = value, RCX = count
            loadValue64(inst.dst, rdi64)
            loadValue64(inst.len, rcx64)
            // value goes into AL
            loadValue32(inst.value, eax32)
            asm.cld()
            asm.rep()
            asm.stosb()
        }

        private fun loadValueXmm(v: Value, target: X86Xmm) {
            when (v) {
                is Constant.F64 -> {
                    // Load f64 constant via rodata — store in rodata, LEA, movsd from memory
                    // Simpler: use integer register to load bits, then movq to XMM
                    val bits = java.lang.Double.doubleToRawLongBits(v.value)
                    if (bits == 0L) {
                        emitPxor(target, target) // zero
                    } else {
                        asm.mov(r11_64, bits)
                        // movq target, r11 — 66 REX.W 0F 6E /r
                        val tEnc = (target as X86Register).encoding
                        asm.emitByte(0x66)
                        asm.emitByte(0x48 or ((tEnc shr 3) shl 2) or (0x01)) // REX.W + R11 base
                        asm.emitBytes(0x0F, 0x6E)
                        asm.emitByte(0xC0 or ((tEnc and 7) shl 3) or (r11_64 as X86Register).encoding.and(7))
                    }
                }
                is Constant.F32 -> {
                    val bits = java.lang.Float.floatToRawIntBits(v.value)
                    if (bits == 0) {
                        emitPxor(target, target)
                    } else {
                        asm.mov(r11d32, bits)
                        // movd target, r11d — 66 0F 6E /r (with REX if needed)
                        val tEnc = (target as X86Register).encoding
                        val sEnc = (r11d32 as X86Register).encoding
                        asm.emitByte(0x66)
                        if (tEnc >= 8 || sEnc >= 8) asm.emitByte(0x40 or ((tEnc shr 3) shl 2) or (sEnc shr 3))
                        asm.emitBytes(0x0F, 0x6E)
                        asm.emitByte(0xC0 or ((tEnc and 7) shl 3) or (sEnc and 7))
                    }
                }
                is Parameter, is InstructionRef -> {
                    val src = getOrLoadXmm(v.name, target)
                    if (src != target) asm.movsd(target, src)
                }
                else -> error("Cannot load XMM value: ${v::class.simpleName}")
            }
        }

        private fun emitPxor(a: X86Xmm, b: X86Xmm) {
            // pxor a, b — 66 0F EF /r
            val aEnc = (a as X86Register).encoding
            val bEnc = (b as X86Register).encoding
            asm.emitByte(0x66)
            if (aEnc >= 8 || bEnc >= 8) asm.emitByte(0x40 or ((aEnc shr 3) shl 2) or (bEnc shr 3))
            asm.emitBytes(0x0F, 0xEF)
            asm.emitByte(0xC0 or ((aEnc and 7) shl 3) or (bEnc and 7))
        }

        // Memory access helpers — manual encoding for register-indirect addressing

        private fun emitLoadMem32(dest: X86Register32, base: X86Register64, disp: Int) {
            // mov dest, [base + disp]
            val dEnc = (dest as X86Register).encoding
            val bEnc = (base as X86Register).encoding
            if (dEnc >= 8 || bEnc >= 8) asm.emitByte(0x40 or ((dEnc shr 3) shl 2) or (bEnc shr 3))
            asm.emitByte(0x8B)
            emitModRM(dEnc, bEnc, disp)
        }

        private fun emitLoadMem64(dest: X86Register64, base: X86Register64, disp: Int) {
            val dEnc = (dest as X86Register).encoding
            val bEnc = (base as X86Register).encoding
            asm.emitByte(0x48 or ((dEnc shr 3) shl 2) or (bEnc shr 3))
            asm.emitByte(0x8B)
            emitModRM(dEnc, bEnc, disp)
        }

        private fun emitStoreMem32(base: X86Register64, disp: Int, src: X86Register32) {
            val sEnc = (src as X86Register).encoding
            val bEnc = (base as X86Register).encoding
            if (sEnc >= 8 || bEnc >= 8) asm.emitByte(0x40 or ((sEnc shr 3) shl 2) or (bEnc shr 3))
            asm.emitByte(0x89)
            emitModRM(sEnc, bEnc, disp)
        }

        private fun emitStoreMem64(base: X86Register64, disp: Int, src: X86Register64) {
            val sEnc = (src as X86Register).encoding
            val bEnc = (base as X86Register).encoding
            asm.emitByte(0x48 or ((sEnc shr 3) shl 2) or (bEnc shr 3))
            asm.emitByte(0x89)
            emitModRM(sEnc, bEnc, disp)
        }

        private fun emitStoreMem8(base: X86Register64, disp: Int, src: X86Register32) {
            // mov [base+disp], src8 — use low byte of src
            val sEnc = (src as X86Register).encoding
            val bEnc = (base as X86Register).encoding
            if (sEnc >= 4 || bEnc >= 8) asm.emitByte(0x40 or ((sEnc shr 3) shl 2) or (bEnc shr 3))
            asm.emitByte(0x88)
            emitModRM(sEnc, bEnc, disp)
        }

        private fun emitStoreMem16(base: X86Register64, disp: Int, src: X86Register32) {
            // mov [base+disp], src16 — operand size prefix
            val sEnc = (src as X86Register).encoding
            val bEnc = (base as X86Register).encoding
            asm.emitByte(0x66) // operand size prefix
            if (sEnc >= 8 || bEnc >= 8) asm.emitByte(0x40 or ((sEnc shr 3) shl 2) or (bEnc shr 3))
            asm.emitByte(0x89)
            emitModRM(sEnc, bEnc, disp)
        }

        private fun emitMovzxMem8(dest: X86Register32, base: X86Register64, disp: Int) {
            // movzx dest, byte [base+disp]
            val dEnc = (dest as X86Register).encoding
            val bEnc = (base as X86Register).encoding
            if (dEnc >= 8 || bEnc >= 8) asm.emitByte(0x40 or ((dEnc shr 3) shl 2) or (bEnc shr 3))
            asm.emitBytes(0x0F, 0xB6)
            emitModRM(dEnc, bEnc, disp)
        }

        private fun emitMovzxMem16(dest: X86Register32, base: X86Register64, disp: Int) {
            val dEnc = (dest as X86Register).encoding
            val bEnc = (base as X86Register).encoding
            if (dEnc >= 8 || bEnc >= 8) asm.emitByte(0x40 or ((dEnc shr 3) shl 2) or (bEnc shr 3))
            asm.emitBytes(0x0F, 0xB7)
            emitModRM(dEnc, bEnc, disp)
        }

        private fun emitModRM(regBits: Int, rmBits: Int, disp: Int) {
            val rm = rmBits and 7
            val reg = regBits and 7
            if (rm == 4) {
                // RSP/R12 as base needs SIB byte
                if (disp == 0) {
                    asm.emitByte(0x04 or (reg shl 3)); asm.emitByte(0x24)
                } else if (disp in -128..127) {
                    asm.emitByte(0x44 or (reg shl 3)); asm.emitByte(0x24); asm.emitByte(disp)
                } else {
                    asm.emitByte(0x84 or (reg shl 3)); asm.emitByte(0x24); asm.emitInt32(disp)
                }
            } else if (rm == 5 && disp == 0) {
                // RBP/R13 with disp=0 needs explicit disp8=0
                asm.emitByte(0x40 or (reg shl 3) or rm); asm.emitByte(0)
            } else if (disp == 0) {
                asm.emitByte(0x00 or (reg shl 3) or rm)
            } else if (disp in -128..127) {
                asm.emitByte(0x40 or (reg shl 3) or rm); asm.emitByte(disp)
            } else {
                asm.emitByte(0x80 or (reg shl 3) or rm); asm.emitInt32(disp)
            }
        }

        // Register-to-register movzx/movsx (source is low bits of a 32-bit reg)

        private fun emitMovzx32from8(dest: X86Register32, src: X86Register32) {
            val dEnc = (dest as X86Register).encoding
            val sEnc = (src as X86Register).encoding
            if (dEnc >= 8 || sEnc >= 4) asm.emitByte(0x40 or ((dEnc shr 3) shl 2) or (sEnc shr 3))
            asm.emitBytes(0x0F, 0xB6)
            asm.emitByte(0xC0 or ((dEnc and 7) shl 3) or (sEnc and 7))
        }

        private fun emitMovzx32from16(dest: X86Register32, src: X86Register32) {
            val dEnc = (dest as X86Register).encoding
            val sEnc = (src as X86Register).encoding
            if (dEnc >= 8 || sEnc >= 8) asm.emitByte(0x40 or ((dEnc shr 3) shl 2) or (sEnc shr 3))
            asm.emitBytes(0x0F, 0xB7)
            asm.emitByte(0xC0 or ((dEnc and 7) shl 3) or (sEnc and 7))
        }

        private fun emitMovsx32from8(dest: X86Register32, src: X86Register32) {
            val dEnc = (dest as X86Register).encoding
            val sEnc = (src as X86Register).encoding
            if (dEnc >= 8 || sEnc >= 4) asm.emitByte(0x40 or ((dEnc shr 3) shl 2) or (sEnc shr 3))
            asm.emitBytes(0x0F, 0xBE)
            asm.emitByte(0xC0 or ((dEnc and 7) shl 3) or (sEnc and 7))
        }

        private fun emitMovsx32from16(dest: X86Register32, src: X86Register32) {
            val dEnc = (dest as X86Register).encoding
            val sEnc = (src as X86Register).encoding
            if (dEnc >= 8 || sEnc >= 8) asm.emitByte(0x40 or ((dEnc shr 3) shl 2) or (sEnc shr 3))
            asm.emitBytes(0x0F, 0xBF)
            asm.emitByte(0xC0 or ((dEnc and 7) shl 3) or (sEnc and 7))
        }

        private fun reg64to32(r: X86Register64): X86Register32 = when (r) {
            rax64 -> eax32; rbx64 -> ebx32
            rcx64 -> ecx32; rdx64 -> edx32
            rsi64 -> esi32; rdi64 -> edi32
            r8_64 -> r8d32; r9_64 -> r9d32
            r10_64 -> r10d32; r11_64 -> r11d32
            r12_64 -> r12d32; r13_64 -> r13d32
            r14_64 -> r14d32; r15_64 -> r15d32
            else -> error("Unknown 64-bit register: $r")
        }

        private fun reg32to64(r: X86Register32): X86Register64 = when (r) {
            eax32 -> rax64; ebx32 -> rbx64
            ecx32 -> rcx64; edx32 -> rdx64
            esi32 -> rsi64; edi32 -> rdi64
            r8d32 -> r8_64; r9d32 -> r9_64
            r10d32 -> r10_64; r11d32 -> r11_64
            r12d32 -> r12_64; r13d32 -> r13_64
            r14d32 -> r14_64; r15d32 -> r15_64
            else -> error("Unknown 32-bit register: $r")
        }

        /**
         * Emit parallel phi copies when transitioning from the current block to [targetBlockLabel].
         *
         * Phi nodes in SSA form require simultaneous assignment at block boundaries.
         * This method uses topological ordering to emit non-conflicting moves first,
         * then breaks register cycles using a scratch register (R10 for GP, XMM14 for FP)
         * to avoid corrupting live values during sequential emission.
         */
        private fun emitPhiCopies(targetBlockLabel: String) {
            val moves = phiMoves[currentBlockLabel to targetBlockLabel] ?: return
            if (moves.size <= 1) {
                for ((dest, value) in moves) emitSinglePhiCopy(dest, value)
                return
            }

            // Parallel move algorithm: topological emit + scratch register for cycles.
            // Without this, sequential copies corrupt values when a dest register
            // is also a source of a later copy (e.g., swap patterns in loop phis).
            data class PhiMove(val dest: InstructionRef, val value: Value, var done: Boolean = false)
            val pending = moves.map { (d, v) -> PhiMove(d, v) }.toMutableList()

            // Get the encoding of a register that a move destination writes to
            fun destEncoding(m: PhiMove): Int {
                return when (val loc = alloc.locations[m.dest.name]) {
                    is Location.Reg32 -> (loc.reg as X86Register).encoding
                    is Location.Reg64 -> (loc.reg as X86Register).encoding
                    is Location.RegXmm -> (loc.reg as X86Register).encoding + 100
                    else -> -1
                }
            }
            // Get the encoding of a register that a move source reads from
            fun srcEncoding(m: PhiMove): Int {
                val v = m.value
                if (v !is Parameter && v !is InstructionRef) return -2
                return when (val loc = alloc.locations[v.name]) {
                    is Location.Reg32 -> (loc.reg as X86Register).encoding
                    is Location.Reg64 -> (loc.reg as X86Register).encoding
                    is Location.RegXmm -> (loc.reg as X86Register).encoding + 100
                    else -> -2
                }
            }

            // Emit moves whose dest register is not read by any other pending move
            var progress = true
            while (progress) {
                progress = false
                for (m in pending) {
                    if (m.done) continue
                    val dEnc = destEncoding(m)
                    val conflict = dEnc >= 0 && pending.any { other ->
                        !other.done && other !== m && srcEncoding(other) == dEnc
                    }
                    if (!conflict) {
                        emitSinglePhiCopy(m.dest, m.value)
                        m.done = true
                        progress = true
                    }
                }
            }

            // Remaining moves form cycles — break each with a scratch register
            for (m in pending) {
                if (m.done) continue
                // Save this move's source to scratch before cycle resolution
                when (m.dest.type) {
                    Type.I32, Type.I16, Type.I8, Type.I1 -> {
                        val src = getOrLoad32((m.value as? InstructionRef)?.name
                            ?: (m.value as Parameter).name, r10d32)
                        if (src != r10d32) asm.mov(r10d32, src as X86Operand32)
                    }
                    Type.F64, Type.F32 -> {
                        val src = getOrLoadXmm((m.value as? InstructionRef)?.name
                            ?: (m.value as Parameter).name, X86Register.XMM14 as X86Xmm)
                        if (src != X86Register.XMM14) asm.movsd(X86Register.XMM14 as X86Xmm, src as X86Xmm)
                    }
                    else -> {
                        val src = getOrLoad64((m.value as? InstructionRef)?.name
                            ?: (m.value as Parameter).name, r10_64)
                        if (src != r10_64) asm.mov(r10_64, src as X86Operand64)
                    }
                }
                m.done = true

                // Emit remaining cycle moves (now the saved source won't be overwritten)
                var current = m
                while (true) {
                    val dEnc = destEncoding(current)
                    if (dEnc < 0) break
                    val next = pending.firstOrNull { !it.done && srcEncoding(it) == dEnc } ?: break
                    emitSinglePhiCopy(next.dest, next.value)
                    next.done = true
                    current = next
                }

                // Complete the cycle: write saved scratch value to first move's dest
                when (m.dest.type) {
                    Type.I32, Type.I16, Type.I8, Type.I1 -> {
                        val d = getDest32(m.dest.name)
                        if (d != r10d32) asm.mov(d, r10d32 as X86Operand32)
                        if (isSpilled(m.dest.name)) storeTo(m.dest.name, reg32 = d)
                    }
                    Type.F64, Type.F32 -> {
                        val d = getDestXmm(m.dest.name)
                        val scratch = X86Register.XMM14 as X86Xmm
                        if (d != scratch) asm.movsd(d, scratch)
                        if (isSpilled(m.dest.name)) storeToXmm(m.dest.name, d)
                    }
                    else -> {
                        val d = getDest64(m.dest.name)
                        if (d != r10_64) asm.mov(d, r10_64 as X86Operand64)
                        if (isSpilled(m.dest.name)) storeTo(m.dest.name, reg64 = d)
                    }
                }
            }
        }

        private fun emitSinglePhiCopy(dest: InstructionRef, value: Value) {
            when (dest.type) {
                Type.F64, Type.F32 -> {
                    val d = getDestXmm(dest.name)
                    loadValueXmm(value, d)
                    if (isSpilled(dest.name)) storeToXmm(dest.name, d)
                }
                Type.I32, Type.I16, Type.I8, Type.I1 -> {
                    val d = getDest32(dest.name)
                    loadValue32(value, d)
                    if (isSpilled(dest.name)) storeTo(dest.name, reg32 = d)
                }
                else -> {
                    val d = getDest64(dest.name)
                    loadValue64(value, d)
                    if (isSpilled(dest.name)) storeTo(dest.name, reg64 = d)
                }
            }
        }

        private fun emitSwitch(inst: Switch) {
            // Lowered as a chain of compare-and-branch
            val valType = inst.value.type
            for ((caseVal, target) in inst.cases) {
                when (valType) {
                    Type.I64, Type.OpaquePointer, is Type.Pointer -> {
                        val valReg = getOrLoad64(inst.value.name, r10_64)
                        when (caseVal) {
                            is Constant.I32 -> asm.cmp(valReg as X86Operand64, caseVal.value)
                            is Constant.I64 -> {
                                if (caseVal.value in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) {
                                    asm.cmp(valReg as X86Operand64, caseVal.value.toInt())
                                } else {
                                    loadValue64(caseVal, r11_64)
                                    asm.cmp(valReg, r11_64 as X86Operand64)
                                }
                            }
                            else -> error("Unsupported switch case constant: $caseVal")
                        }
                    }
                    else -> {
                        val valReg = getOrLoad32(inst.value.name, r10d32)
                        when (caseVal) {
                            is Constant.I32 -> asm.cmp(valReg as X86Operand32, caseVal.value)
                            else -> error("Unsupported switch case constant: $caseVal")
                        }
                    }
                }
                asm.jccLabel(0x04, "${fn.name}.${target.label}") // JE
            }
            // Default target
            asm.jmpLabel("${fn.name}.${inst.defaultTarget.label}")
        }

        private fun emitBr(inst: Br) {
            emitPhiCopies(inst.target.label)
            asm.jmpLabel("${fn.name}.${inst.target.label}")
        }

        private fun emitIndirectBr(inst: IndirectBr) {
            val addrReg = getOrLoad64(inst.address.name, r11_64)
            asm.jmpReg(addrReg as X86Register64)
        }

        private fun emitCondBr(inst: CondBr, nextBlockLabel: String?) {
            val truePhis = phiMoves[currentBlockLabel to inst.trueTarget.label]
            val falsePhis = phiMoves[currentBlockLabel to inst.falseTarget.label]

            if (truePhis == null && falsePhis == null) {
                // No phi copies needed — simple case
                val condReg = getOrLoad32(inst.condition.name, r11d32)
                asm.cmp(condReg as X86Operand32, 0)
                asm.jccLabel(0x05, "${fn.name}.${inst.trueTarget.label}") // JNE
                if (inst.falseTarget.label != nextBlockLabel) {
                    asm.jmpLabel("${fn.name}.${inst.falseTarget.label}")
                }
            } else {
                // Phi copies needed — branch to intermediate labels
                val condReg = getOrLoad32(inst.condition.name, r11d32)
                asm.cmp(condReg as X86Operand32, 0)
                asm.jccLabel(0x05, "${fn.name}.${currentBlockLabel}.phi_true") // JNE
                // False path: emit false phi copies, then jump to false target
                emitPhiCopies(inst.falseTarget.label)
                if (inst.falseTarget.label != nextBlockLabel) {
                    asm.jmpLabel("${fn.name}.${inst.falseTarget.label}")
                } else {
                    // Fall through to false target (next block)
                }
                // True path
                asm.label("${fn.name}.${currentBlockLabel}.phi_true")
                emitPhiCopies(inst.trueTarget.label)
                asm.jmpLabel("${fn.name}.${inst.trueTarget.label}")
            }
        }

        private fun emitFusedCmpBranch(
            cmp: ICmp,
            br: CondBr,
            nextBlockLabel: String?,
        ) {
            // Emit the comparison — 32-bit or 64-bit based on operand type
            when (cmp.lhs.type) {
                Type.I64, Type.OpaquePointer, is Type.Pointer -> {
                    val lhs = cmp.lhs
                    val lhsReg = if (lhs is Constant.I64) {
                        asm.mov(r10_64, lhs.value)
                        r10_64
                    } else if (lhs is Constant.I32) {
                        asm.mov(r10d32, lhs.value)
                        r10_64
                    } else {
                        getOrLoad64(lhs.name, r10_64)
                    }
                    val rhs = cmp.rhs
                    if (rhs is Constant.I32) {
                        asm.cmp(lhsReg as X86Operand64, rhs.value)
                    } else if (rhs is Constant.I64 && rhs.value in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) {
                        asm.cmp(lhsReg as X86Operand64, rhs.value.toInt())
                    } else {
                        val rhsReg = getOrLoad64(rhs.name, if (lhsReg == r10_64) r11_64 else r10_64)
                        asm.cmp(lhsReg, rhsReg as X86Operand64)
                    }
                }
                else -> {
                    val lhs = cmp.lhs
                    val lhsReg = if (lhs is Constant.I32) {
                        asm.mov(r10d32, lhs.value)
                        r10d32
                    } else {
                        getOrLoad32(lhs.name, r10d32)
                    }
                    val rhs = cmp.rhs
                    if (rhs is Constant.I32) {
                        asm.cmp(lhsReg, rhs.value)
                    } else {
                        val rhsScratch = if (lhsReg == r11d32) r10d32 else r11d32
                        asm.cmp(lhsReg, getOrLoad32(rhs.name, rhsScratch) as X86Operand32)
                    }
                }
            }

            val cc = icmpCondCode(cmp.predicate)

            val truePhis = phiMoves[currentBlockLabel to br.trueTarget.label]
            val falsePhis = phiMoves[currentBlockLabel to br.falseTarget.label]

            if (truePhis == null && falsePhis == null) {
                asm.jccLabel(cc, "${fn.name}.${br.trueTarget.label}")
                if (br.falseTarget.label != nextBlockLabel) {
                    asm.jmpLabel("${fn.name}.${br.falseTarget.label}")
                }
            } else {
                // With phi copies: jump on condition to true phi path
                asm.jccLabel(cc, "${fn.name}.${currentBlockLabel}.phi_true")
                // False path
                emitPhiCopies(br.falseTarget.label)
                if (br.falseTarget.label != nextBlockLabel) {
                    asm.jmpLabel("${fn.name}.${br.falseTarget.label}")
                } else {
                    // Need to skip past the true phi path
                    asm.jmpLabel("${fn.name}.${currentBlockLabel}.phi_done")
                }
                // True path
                asm.label("${fn.name}.${currentBlockLabel}.phi_true")
                emitPhiCopies(br.trueTarget.label)
                asm.jmpLabel("${fn.name}.${br.trueTarget.label}")
                if (br.falseTarget.label == nextBlockLabel) {
                    asm.label("${fn.name}.${currentBlockLabel}.phi_done")
                }
            }
        }

        private fun emitSelect(inst: Select) {
            // select dest, cond, trueVal, falseVal
            // Strategy: load falseVal into dest, test cond, cmov trueVal if true
            when (inst.dest.type) {
                Type.I32 -> {
                    val dest = getDest32(inst.dest.name)
                    loadValue32(inst.falseValue, dest)
                    val trueReg = if (dest == r11d32) r10d32 else r11d32
                    loadValue32(inst.trueValue, trueReg)
                    val condReg = getOrLoad32(inst.condition.name,
                        if (dest == r10d32 || trueReg == r10d32) r11d32 else r10d32)
                    asm.cmp(condReg as X86Operand32, 0)
                    // CMOVNE dest, trueReg (0F 45 /r)
                    emitCmov32(0x45, dest, trueReg)
                    if (isSpilled(inst.dest.name)) storeTo(inst.dest.name, reg32 = dest)
                }
                Type.I64, Type.OpaquePointer, is Type.Pointer -> {
                    val dest = getDest64(inst.dest.name)
                    loadValue64(inst.falseValue, dest)
                    val trueReg = if (dest == r11_64) r10_64 else r11_64
                    loadValue64(inst.trueValue, trueReg)
                    val condReg = getOrLoad32(inst.condition.name, r10d32)
                    asm.cmp(condReg as X86Operand32, 0)
                    // CMOVNE dest, trueReg (REX.W 0F 45 /r)
                    emitCmov64(0x45, dest, trueReg)
                    if (isSpilled(inst.dest.name)) storeTo(inst.dest.name, reg64 = dest)
                }
                else -> error("Unsupported select type: ${inst.dest.type}")
            }
        }

        private fun emitCmov32(cc: Int, dest: X86Register32, src: X86Register32) {
            val dEnc = (dest as X86Register).encoding
            val sEnc = (src as X86Register).encoding
            if (dEnc >= 8 || sEnc >= 8) {
                asm.emitByte(0x40 or ((dEnc shr 3) shl 2) or (sEnc shr 3))
            }
            asm.emitBytes(0x0F, cc)
            asm.emitByte(0xC0 or ((dEnc and 7) shl 3) or (sEnc and 7))
        }

        private fun emitCmov64(cc: Int, dest: X86Register64, src: X86Register64) {
            val dEnc = (dest as X86Register).encoding
            val sEnc = (src as X86Register).encoding
            asm.emitByte(0x48 or ((dEnc shr 3) shl 2) or (sEnc shr 3))
            asm.emitBytes(0x0F, cc)
            asm.emitByte(0xC0 or ((dEnc and 7) shl 3) or (sEnc and 7))
        }

        private fun icmpCondCode(pred: ICmpPredicate): Int = when (pred) {
            ICmpPredicate.EQ -> 0x04  // JE
            ICmpPredicate.NE -> 0x05  // JNE
            ICmpPredicate.SLT -> 0x0C // JL
            ICmpPredicate.SLE -> 0x0E // JLE
            ICmpPredicate.SGT -> 0x0F // JG
            ICmpPredicate.SGE -> 0x0D // JGE
            ICmpPredicate.ULT -> 0x02 // JB
            ICmpPredicate.ULE -> 0x06 // JBE
            ICmpPredicate.UGT -> 0x07 // JA
            ICmpPredicate.UGE -> 0x03 // JAE
        }

        private fun loadValue32(v: Value, target: X86Register32) {
            when (v) {
                is Constant.I32 -> {
                    if (v.value == 0) asm.xor_(target, target as X86Operand32)
                    else asm.mov(target, v.value)
                }
                is Constant.I8 -> asm.mov(target, v.value.toInt())
                is Constant.I16 -> asm.mov(target, v.value.toInt())
                is Constant.I1 -> asm.mov(target, if (v.value) 1 else 0)
                is Constant.Undef, is Constant.Poison -> asm.xor_(target, target as X86Operand32)
                is Parameter, is InstructionRef -> {
                    val src = getOrLoad32(v.name, target)
                    if (src != target) asm.mov(target, src as X86Operand32)
                }
                else -> error("Cannot load value: ${v::class.simpleName}")
            }
        }

        private fun loadValue64(v: Value, target: X86Register64) {
            when (v) {
                is Constant.I64 -> {
                    val x = v.value
                    if (x == 0L) {
                        val t32 = reg64to32(target)
                        asm.xor_(t32, t32 as X86Operand32)
                    } else if (x in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) {
                        asm.mov(target, x.toInt())
                    } else {
                        asm.mov(target, x)
                    }
                }
                is Constant.I32 -> {
                    if (v.value == 0) {
                        val t32 = reg64to32(target)
                        asm.xor_(t32, t32 as X86Operand32)
                    } else {
                        asm.mov(target, v.value)
                    }
                }
                is Constant.NullPtr -> {
                    val t32 = reg64to32(target)
                    asm.xor_(t32, t32 as X86Operand32)
                }
                is Constant.Undef, is Constant.Poison -> {
                    // Undefined values: emit zero for deterministic behavior
                    val t32 = reg64to32(target)
                    asm.xor_(t32, t32 as X86Operand32)
                }
                is GlobalRef -> {
                    val enc = (target as X86Register).encoding
                    val rex = 0x48 or ((enc shr 3) and 1).shl(2)
                    asm.emitByte(rex)
                    asm.emitByte(0x8D) // LEA
                    asm.emitByte(0x05 or ((enc and 7) shl 3))
                    relocations.add(Relocation(
                        offset = asm.position().toLong(), symbol = v.name,
                        type = RelocationType.X86_64.PC32, addend = -4, section = ".text"))
                    asm.emitInt32(0)
                }
                is FunctionRef -> {
                    // Load function address (RIP-relative LEA)
                    val enc = (target as X86Register).encoding
                    val rex = 0x48 or ((enc shr 3) and 1).shl(2)
                    asm.emitByte(rex)
                    asm.emitByte(0x8D) // LEA
                    asm.emitByte(0x05 or ((enc and 7) shl 3))
                    relocations.add(Relocation(
                        offset = asm.position().toLong(), symbol = v.name,
                        type = RelocationType.X86_64.PC32, addend = -4, section = ".text"))
                    asm.emitInt32(0)
                }
                is Parameter, is InstructionRef -> {
                    val src = getOrLoad64(v.name, target)
                    if (src != target) asm.mov(target, src as X86Operand64)
                }
                else -> error("Cannot load value: ${v::class.simpleName}")
            }
        }

        // Binary operation helpers

        private fun emitIntBinOp(
            dest: InstructionRef, lhs: Value, rhs: Value,
            regOp32: (X86Register32, X86Register32) -> Unit, immOp32: (X86Register32, Int) -> Unit,
            regOp64: (X86Register64, X86Register64) -> Unit, immOp64: (X86Register64, Int) -> Unit,
        ) {
            when (lhs.type) {
                Type.I32 -> {
                    val d = getDest32(dest.name)
                    loadValue32(lhs, d)
                    if (rhs is Constant.I32) immOp32(d, rhs.value)
                    else {
                        val rhsReg = getOrLoad32(rhs.name, if (d == r11d32) r10d32 else r11d32)
                        regOp32(d, rhsReg)
                    }
                    if (isSpilled(dest.name)) storeTo(dest.name, reg32 = d)
                }
                Type.I64 -> {
                    val d = getDest64(dest.name)
                    loadValue64(lhs, d)
                    if (rhs is Constant.I32) immOp64(d, rhs.value)
                    else if (rhs is Constant.I64 && rhs.value in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) immOp64(d, rhs.value.toInt())
                    else {
                        val rhsReg = getOrLoad64(rhs.name, if (d == r11_64) r10_64 else r11_64)
                        regOp64(d, rhsReg)
                    }
                    if (isSpilled(dest.name)) storeTo(dest.name, reg64 = d)
                }
                else -> error("Unsupported type: ${lhs.type}")
            }
        }

        private fun emitIntBinOp32(
            dest: InstructionRef, lhs: Value, rhs: Value,
            regOp: (X86Register32, X86Register32) -> Unit, immOp: (X86Register32, Int) -> Unit,
        ) {
            val d = getDest32(dest.name)
            loadValue32(lhs, d)
            if (rhs is Constant.I32) immOp(d, rhs.value)
            else {
                val rhsReg = getOrLoad32(rhs.name, if (d == r11d32) r10d32 else r11d32)
                regOp(d, rhsReg)
            }
            if (isSpilled(dest.name)) storeTo(dest.name, reg32 = d)
        }
    }

    companion object {
        private val rax64 = X86Register.RAX as X86Register64
        private val rbx64 = X86Register.RBX as X86Register64
        private val rbp64 = X86Register.RBP as X86Register64
        private val rsp64 = X86Register.RSP as X86Register64
        private val rdi64 = X86Register.RDI as X86Register64
        private val rsi64 = X86Register.RSI as X86Register64
        private val rdx64 = X86Register.RDX as X86Register64
        private val rcx64 = X86Register.RCX as X86Register64
        private val r8_64 = X86Register.R8 as X86Register64
        private val r9_64 = X86Register.R9 as X86Register64
        private val r10_64 = X86Register.R10 as X86Register64
        private val r11_64 = X86Register.R11 as X86Register64
        private val r12_64 = X86Register.R12 as X86Register64
        private val r13_64 = X86Register.R13 as X86Register64
        private val r14_64 = X86Register.R14 as X86Register64
        private val r15_64 = X86Register.R15 as X86Register64

        private val al8 = X86Register.AL as X86Register8
        private val cl8 = X86Register.CL as X86Register8

        private val eax32 = X86Register.EAX as X86Register32
        private val ebx32 = X86Register.EBX as X86Register32
        private val edi32 = X86Register.EDI as X86Register32
        private val esi32 = X86Register.ESI as X86Register32
        private val edx32 = X86Register.EDX as X86Register32
        private val ecx32 = X86Register.ECX as X86Register32
        private val r8d32 = X86Register.R8D as X86Register32
        private val r9d32 = X86Register.R9D as X86Register32
        private val r10d32 = X86Register.R10D as X86Register32
        private val r11d32 = X86Register.R11D as X86Register32
        private val r12d32 = X86Register.R12D as X86Register32
        private val r13d32 = X86Register.R13D as X86Register32
        private val r14d32 = X86Register.R14D as X86Register32
        private val r15d32 = X86Register.R15D as X86Register32

        private val argRegs64 = arrayOf(rdi64, rsi64, rdx64, rcx64, r8_64, r9_64)
        private val argRegs32 = arrayOf(edi32, esi32, edx32, ecx32, r8d32, r9d32)
        private val winArgRegs64 = arrayOf(rcx64, rdx64, r8_64, r9_64)
        private val winArgRegs32 = arrayOf(ecx32, edx32, r8d32, r9d32)

        // Allocatable registers: caller-saved first (cheap), then callee-saved (need save/restore)
        // Excludes: RAX (return value), RDX (clobbered by idiv/cqo), RSP (stack), RBP (frame), R10/R11 (scratch)
        private val allocatableRegs64 = arrayOf(
            // Caller-saved (free to clobber)
            rcx64, rsi64, rdi64, r8_64, r9_64,
            // Callee-saved (need push/pop in prologue/epilogue)
            rbx64, r12_64, r13_64, r14_64, r15_64,
        )
        private val allocatableRegs32 = arrayOf(
            ecx32, esi32, edi32, r8d32, r9d32,
            ebx32, r12d32, r13d32, r14d32, r15d32,
        )
        private val calleeSavedSet64 = setOf(rbx64, r12_64, r13_64, r14_64, r15_64)
        private val calleeSavedSet32 = setOf(ebx32, r12d32, r13d32, r14d32, r15d32)

        private val xmm0 = X86Register.XMM0 as X86Xmm
        private val xmm1 = X86Register.XMM1 as X86Xmm
        private val xmm2 = X86Register.XMM2 as X86Xmm
        private val xmm3 = X86Register.XMM3 as X86Xmm
        private val xmm4 = X86Register.XMM4 as X86Xmm
        private val xmm5 = X86Register.XMM5 as X86Xmm
        private val xmm6 = X86Register.XMM6 as X86Xmm
        private val xmm7 = X86Register.XMM7 as X86Xmm
        private val xmm8 = X86Register.XMM8 as X86Xmm
        private val xmm9 = X86Register.XMM9 as X86Xmm
        private val xmm10 = X86Register.XMM10 as X86Xmm
        private val xmm11 = X86Register.XMM11 as X86Xmm
        private val xmm12 = X86Register.XMM12 as X86Xmm
        private val xmm13 = X86Register.XMM13 as X86Xmm
        private val xmm14 = X86Register.XMM14 as X86Xmm
        private val xmm15 = X86Register.XMM15 as X86Xmm

        // XMM0-XMM13 allocatable, XMM14-XMM15 reserved as scratch
        private val allocatableXmm = arrayOf(
            xmm0, xmm1, xmm2, xmm3, xmm4, xmm5, xmm6, xmm7,
            xmm8, xmm9, xmm10, xmm11, xmm12, xmm13,
        )

        // System V: XMM0-XMM7 for float args; Windows: XMM0-XMM3
        private val callArgXmm = arrayOf(xmm0, xmm1, xmm2, xmm3, xmm4, xmm5, xmm6, xmm7)
        private val winCallArgXmm = arrayOf(xmm0, xmm1, xmm2, xmm3)
    }
}
