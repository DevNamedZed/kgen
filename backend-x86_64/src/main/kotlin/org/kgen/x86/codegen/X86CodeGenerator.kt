package org.kgen.x86.codegen

import org.kgen.x86.*
import org.kgen.x86.asm.*
import org.kgen.binary.*
import org.kgen.binary.elf.*
import org.kgen.binary.pe.PeLinker
import org.kgen.ir.*
import org.kgen.ir.codegen.*

/**
 * Translates an IR [Module] into x86-64 machine code.
 *
 * Produces an [ObjectFile] with .text and .rodata sections, symbols, and relocations.
 * The output can be fed to [ElfLinker] for a dynamically-linked executable,
 * or to [ElfObjectWriter] for a .o file.
 *
 * Uses System V AMD64 ABI calling convention.
 */
class X86CodeGenerator : CodeGenerator {

    override val targetName: String = "x86_64"

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

    fun generateObjectFile(module: Module, imports: List<ImportEntry> = emptyList()): ObjectFile {
        val ctx = CodeGenContext(module, imports)
        ctx.emitGlobals()
        ctx.emitFunctions()
        return ctx.buildObjectFile()
    }

    private class CodeGenContext(val module: Module, val userImports: List<ImportEntry> = emptyList()) {
        val asm = X86Assembler()
        val symbols = mutableListOf<Symbol>()
        val relocations = mutableListOf<Relocation>()
        val rodataBuilder = RodataBuilder()
        val isWindows = module.targetTriple?.contains("windows") == true

        fun emitGlobals() {
            for (global in module.globals) {
                val init = global.initializer ?: continue
                val data = serializeConstant(init)
                val align = global.align ?: alignForType(global.type)
                rodataBuilder.addGlobal(global.name, data, align)
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

        fun buildObjectFile(): ObjectFile {
            val textBytes = asm.toByteArray()
            val sections = mutableListOf(
                Section(".text", SectionKind.TEXT, textBytes, align = 16)
            )
            if (rodataBuilder.hasData()) {
                sections.add(Section(".rodata", SectionKind.RODATA, rodataBuilder.toByteArray(), align = rodataBuilder.maxAlign()))
                symbols.addAll(rodataBuilder.symbols())
            }
            return ObjectFile(
                format = if (isWindows) ObjectFormat.PE_COFF else ObjectFormat.ELF,
                arch = Architecture(ArchType.X86_64),
                sections = sections,
                symbols = symbols,
                relocations = relocations,
                imports = userImports,
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

        private lateinit var alloc: AllocResult
        private val hasCalls = fn.blocks.any { b -> b.instructions.any { it is Instruction.Call } }
        // Windows x64 ABI requires 32 bytes of shadow space for every call
        private val shadowSpace = if (isWindows && hasCalls) 32 else 0

        fun emit() {
            runRegisterAllocator()
            emitPrologue()
            emitBlocks()
        }

        private fun runRegisterAllocator() {
            alloc = LinearScanAllocator(
                fn,
                availableRegs64 = allocatableRegs64.toList(),
                availableRegs32 = allocatableRegs32.toList(),
                calleeSaved64 = calleeSavedSet64,
                calleeSaved32 = calleeSavedSet32,
                paramRegs64 = callArgRegs64,
                paramRegs32 = callArgRegs32,
            ).allocate()
        }

        private fun emitPrologue() {
            asm.push(rbp64)
            asm.mov(rbp64, rsp64 as X86Operand64)

            // Save callee-saved registers
            for (reg in alloc.usedCalleeRegs64) {
                asm.push(reg)
            }

            // Reserve stack: spill slots + shadow space, aligned to 16 bytes
            val spillSize = alloc.spillSlots * 8
            val pushCount = 1 + alloc.usedCalleeRegs64.size // rbp + callee-saved
            val totalFrameBeforeSub = pushCount * 8 + 8 // pushes + return address
            val needed = spillSize + shadowSpace
            // Align to 16: after sub rsp, stackReserve, RSP must be 16-byte aligned
            val stackReserve = if (needed > 0) {
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
        }

        private var stackReserve = 0

        private fun emitEpilogue() {
            if (stackReserve > 0) {
                asm.add(rsp64 as X86Operand64, stackReserve)
            }
            // Restore callee-saved registers in reverse order
            for (reg in alloc.usedCalleeRegs64.reversed()) {
                asm.pop(reg)
            }
            asm.pop(rbp64)
            asm.ret()
        }

        private fun emitBlocks() {
            for (block in fn.blocks) {
                asm.label("${fn.name}.${block.label}")
                for (inst in block.instructions) {
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

        private fun emitInstruction(inst: Instruction) {
            when (inst) {
                is Instruction.Add -> emitIntBinOp(inst.dest, inst.lhs, inst.rhs,
                    { d, rhs -> asm.add(d, rhs as X86Operand32) }, { d, imm -> asm.add(d as X86Operand32, imm) },
                    { d, rhs -> asm.add(d, rhs as X86Operand64) }, { d, imm -> asm.add(d as X86Operand64, imm) })

                is Instruction.Sub -> emitIntBinOp(inst.dest, inst.lhs, inst.rhs,
                    { d, rhs -> asm.sub(d, rhs as X86Operand32) }, { d, imm -> asm.sub(d as X86Operand32, imm) },
                    { d, rhs -> asm.sub(d, rhs as X86Operand64) }, { d, imm -> asm.sub(d as X86Operand64, imm) })

                is Instruction.Mul -> {
                    val dest = getDest32(inst.dest.name)
                    loadValue32(inst.lhs, dest)
                    val rhs = inst.rhs
                    if (rhs is Constant.I32) {
                        loadValue32(rhs, r11d32)
                        asm.imul(dest, r11d32 as X86Operand32)
                    } else {
                        val rhsReg = getOrLoad32(rhs.name, if (dest == r11d32) r10d32 else r11d32)
                        asm.imul(dest, rhsReg as X86Operand32)
                    }
                    if (isSpilled(inst.dest.name)) storeTo(inst.dest.name, reg32 = dest)
                }

                is Instruction.And -> emitIntBinOp32(inst.dest, inst.lhs, inst.rhs,
                    { d, rhs -> asm.and_(d, rhs as X86Operand32) }, { d, imm -> asm.and_(d as X86Operand32, imm) })

                is Instruction.Or -> emitIntBinOp32(inst.dest, inst.lhs, inst.rhs,
                    { d, rhs -> asm.or_(d, rhs as X86Operand32) }, { d, imm -> asm.or_(d as X86Operand32, imm) })

                is Instruction.Xor -> emitIntBinOp32(inst.dest, inst.lhs, inst.rhs,
                    { d, rhs -> asm.xor_(d, rhs as X86Operand32) }, { d, imm -> asm.xor_(d as X86Operand32, imm) })

                is Instruction.Ret -> emitReturn(inst)
                is Instruction.Call -> emitCall(inst)
                is Instruction.ICmp -> emitICmp(inst)
                is Instruction.GetElementPtr -> emitGetElementPtr(inst)

                else -> error("Unsupported IR instruction for x86-64: ${inst::class.simpleName}")
            }
        }

        private fun emitReturn(inst: Instruction.Ret) {
            val retVal = inst.value
            if (retVal != null) {
                when (retVal.type) {
                    Type.I32 -> loadValue32(retVal, eax32)
                    Type.I64 -> loadValue64(retVal, rax64)
                    else -> {}
                }
            }
            emitEpilogue()
        }

        private fun emitCall(inst: Instruction.Call) {
            for ((i, arg) in inst.args.withIndex()) {
                if (i >= callArgRegs64.size) error("Too many arguments (stack args not yet supported)")
                when (arg.type) {
                    Type.I32, Type.I16, Type.I8, Type.I1 -> loadValue32(arg, callArgRegs32[i])
                    Type.I64, Type.OpaquePointer, is Type.Pointer -> loadValue64(arg, callArgRegs64[i])
                    is Type.Array -> loadValue64(arg, callArgRegs64[i])
                    else -> error("Unsupported arg type: ${arg.type}")
                }
            }

            val funcName = when (val f = inst.function) {
                is FunctionRef -> f.name
                is GlobalRef -> f.name
                else -> error("Unsupported call target: $f")
            }

            val isLocal = module.functions.any { it.name == funcName && !it.isExternal }
            if (isLocal) {
                asm.callLabel(funcName)
            } else {
                asm.emitByte(0xE8)
                relocations.add(Relocation(
                    offset = asm.position().toLong(), symbol = funcName,
                    type = RelocationType.X86_64.PLT32, addend = -4, section = ".text"))
                asm.emitInt32(0)
            }

            val dest = inst.dest
            if (dest != null) {
                when (dest.type) {
                    Type.I32 -> {
                        val d = getDest32(dest.name)
                        if (d != eax32) asm.mov(d, eax32 as X86Operand32)
                        if (isSpilled(dest.name)) storeTo(dest.name, reg32 = d)
                    }
                    Type.I64 -> {
                        val d = getDest64(dest.name)
                        if (d != rax64) asm.mov(d, rax64 as X86Operand64)
                        if (isSpilled(dest.name)) storeTo(dest.name, reg64 = d)
                    }
                    else -> {}
                }
            }
        }

        private fun emitICmp(inst: Instruction.ICmp) {
            val dest = getDest32(inst.dest.name)
            val lhsReg = getOrLoad32(inst.lhs.name, if (dest == r11d32) r10d32 else dest)
            val rhs = inst.rhs
            if (rhs is Constant.I32) {
                asm.cmp(lhsReg as X86Operand32, rhs.value)
            } else {
                val rhsScratch = if (lhsReg == r11d32) r10d32 else r11d32
                asm.cmp(lhsReg as X86Operand32, getOrLoad32(rhs.name, rhsScratch) as X86Register32)
            }
            val cc = when (inst.predicate) {
                ICmpPredicate.EQ -> 0x04; ICmpPredicate.NE -> 0x05
                ICmpPredicate.SLT -> 0x0C; ICmpPredicate.SLE -> 0x0E
                ICmpPredicate.SGT -> 0x0F; ICmpPredicate.SGE -> 0x0D
                ICmpPredicate.ULT -> 0x02; ICmpPredicate.ULE -> 0x06
                ICmpPredicate.UGT -> 0x07; ICmpPredicate.UGE -> 0x03
            }
            asm.xor_(dest, dest as X86Operand32)
            val enc = (dest as X86Register).encoding
            if (enc >= 4) {
                asm.emitByte(0x40 or (if (enc >= 8) 0x01 else 0))
            }
            asm.emitBytes(0x0F, 0x90 + cc)
            asm.emitByte(0xC0 or (enc and 7))
            if (isSpilled(inst.dest.name)) storeTo(inst.dest.name, reg32 = dest)
        }

        private fun emitGetElementPtr(inst: Instruction.GetElementPtr) {
            val dest = getDest64(inst.dest.name)
            val ptr = inst.ptr
            if (ptr is GlobalRef) {
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
            if (isSpilled(inst.dest.name)) storeTo(inst.dest.name, reg64 = dest)
        }

        private fun loadValue32(v: Value, target: X86Register32) {
            when (v) {
                is Constant.I32 -> asm.mov(target, v.value)
                is Constant.I1 -> asm.mov(target, if (v.value) 1 else 0)
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
                    if (x in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) {
                        asm.mov(target, x.toInt())
                    } else {
                        asm.mov(target, x)
                    }
                }
                is Constant.I32 -> asm.mov(target, v.value)
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
        // Excludes: RAX (return value), RSP (stack), RBP (frame), R10/R11 (scratch for spill code)
        private val allocatableRegs64 = arrayOf(
            // Caller-saved (free to clobber)
            rcx64, rdx64, rsi64, rdi64, r8_64, r9_64,
            // Callee-saved (need push/pop in prologue/epilogue)
            rbx64, r12_64, r13_64, r14_64, r15_64,
        )
        private val allocatableRegs32 = arrayOf(
            ecx32, edx32, esi32, edi32, r8d32, r9d32,
            ebx32, r12d32, r13d32, r14d32, r15d32,
        )
        private val calleeSavedSet64 = setOf(rbx64, r12_64, r13_64, r14_64, r15_64)
        private val calleeSavedSet32 = setOf(ebx32, r12d32, r13d32, r14d32, r15d32)
    }
}
