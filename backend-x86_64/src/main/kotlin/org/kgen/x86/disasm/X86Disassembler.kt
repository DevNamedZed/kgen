package org.kgen.x86.disasm

import org.kgen.ir.codegen.Disassembler
import org.kgen.ir.codegen.DisassembledInstruction

/**
 * Decodes x86-64 machine code into structured [X86Instruction] sequences.
 *
 * Handles legacy prefixes, REX, 1/2/3-byte opcodes, ModR/M, SIB,
 * displacement, and immediates for the most common instruction set.
 *
 * ```kotlin
 * val disasm = X86Disassembler()
 * val instructions = disasm.disassemble(codeBytes, baseAddress = 0x401000)
 * instructions.forEach { println(it) }
 * ```
 */
class X86Disassembler : Disassembler<X86Instruction> {

    override val targetName: String = "x86_64"

    override fun disassemble(bytes: ByteArray, baseAddress: Long): List<DisassembledInstruction<X86Instruction>> {
        val result = mutableListOf<DisassembledInstruction<X86Instruction>>()
        var offset = 0
        while (offset < bytes.size) {
            val inst = decodeOne(bytes, offset, baseAddress + offset) ?: break
            result.add(DisassembledInstruction(
                address = baseAddress + offset,
                bytes = bytes.copyOfRange(offset, offset + inst.size),
                instruction = inst,
                mnemonic = inst.mnemonic,
                operands = inst.operands.joinToString(", ") { it.text() },
                size = inst.size,
            ))
            offset += inst.size
        }
        return result
    }

    override fun disassembleOne(bytes: ByteArray, offset: Int, baseAddress: Long): DisassembledInstruction<X86Instruction>? {
        val inst = decodeOne(bytes, offset, baseAddress) ?: return null
        return DisassembledInstruction(
            address = baseAddress,
            bytes = bytes.copyOfRange(offset, offset + inst.size),
            instruction = inst,
            mnemonic = inst.mnemonic,
            operands = inst.operands.joinToString(", ") { it.text() },
            size = inst.size,
        )
    }

    fun disassembleRaw(bytes: ByteArray, baseAddress: Long = 0): List<X86Instruction> {
        val result = mutableListOf<X86Instruction>()
        var offset = 0
        while (offset < bytes.size) {
            val inst = decodeOne(bytes, offset, baseAddress + offset) ?: break
            result.add(inst)
            offset += inst.size
        }
        return result
    }

    private fun decodeOne(bytes: ByteArray, offset: Int, address: Long): X86Instruction? {
        if (offset >= bytes.size) return null
        val ctx = DecodeContext(bytes, offset, address)
        ctx.readPrefixes()
        return ctx.decodeInstruction()
    }

    private class DecodeContext(
        val bytes: ByteArray,
        val startOffset: Int,
        val address: Long,
    ) {
        var pos = startOffset

        // Prefixes
        var hasOperandSizeOverride = false
        var hasAddressSizeOverride = false
        var hasRepne = false
        var hasRep = false
        var segment: String? = null

        // REX
        var rexW = false
        var rexR = false
        var rexX = false
        var rexB = false
        var hasRex = false

        fun readPrefixes() {
            while (pos < bytes.size) {
                val b = u8()
                when (b) {
                    0x66 -> hasOperandSizeOverride = true
                    0x67 -> hasAddressSizeOverride = true
                    0xF0 -> {} // LOCK
                    0xF2 -> hasRepne = true
                    0xF3 -> hasRep = true
                    0x2E -> segment = "cs"
                    0x36 -> segment = "ss"
                    0x3E -> segment = "ds"
                    0x26 -> segment = "es"
                    0x64 -> segment = "fs"
                    0x65 -> segment = "gs"
                    in 0x40..0x4F -> {
                        hasRex = true
                        rexW = (b and 0x08) != 0
                        rexR = (b and 0x04) != 0
                        rexX = (b and 0x02) != 0
                        rexB = (b and 0x01) != 0
                    }
                    else -> {
                        pos-- // push back
                        return
                    }
                }
            }
        }

        fun decodeInstruction(): X86Instruction? {
            if (pos >= bytes.size) return null
            val b1 = u8()

            // Two-byte escape
            if (b1 == 0x0F) {
                if (pos >= bytes.size) return fail()
                val b2 = u8()
                return decodeTwoByte(b2)
            }

            return decodeOneByte(b1)
        }

        private fun decodeOneByte(op: Int): X86Instruction? = when (op) {
            // NOP
            0x90 -> if (!hasRex || !rexB) emit("nop") else emit("xchg", reg64(0), reg64(0 or rexBit()))

            // RET
            0xC3 -> emit("ret")
            0xCB -> emit("retf")
            0xC2 -> emit("ret", X86Operand.Immediate(readImm16().toLong(), 16))

            // PUSH r64
            in 0x50..0x57 -> emit("push", reg64(op - 0x50))
            // POP r64
            in 0x58..0x5F -> emit("pop", reg64(op - 0x58))

            // MOV r8, imm8
            in 0xB0..0xB7 -> emit("mov", reg8(op - 0xB0), X86Operand.Immediate(readImm8s().toLong(), 8))
            // MOV r32/r64, imm32/imm64
            in 0xB8..0xBF -> {
                val reg = op - 0xB8
                if (rexW) {
                    emit("mov", reg64(reg), X86Operand.Immediate(readImm64(), 64))
                } else if (hasOperandSizeOverride) {
                    emit("mov", reg16(reg), X86Operand.Immediate(readImm16().toLong(), 16))
                } else {
                    emit("mov", reg32(reg), X86Operand.Immediate(readImm32s().toLong() and 0xFFFFFFFFL, 32))
                }
            }

            // ADD/OR/ADC/SBB/AND/SUB/XOR/CMP rm, imm (Group 1)
            0x80 -> decodeGroup1Imm(8, false)
            0x81 -> decodeGroup1Imm(opSize(), false)
            0x83 -> decodeGroup1Imm(opSize(), true) // sign-extended imm8

            // ADD rm, r / r, rm
            0x00 -> decodeRmReg("add", 8, false)
            0x01 -> decodeRmReg("add", opSize(), false)
            0x02 -> decodeRmReg("add", 8, true)
            0x03 -> decodeRmReg("add", opSize(), true)
            0x04 -> emit("add", X86Operand.Register("al"), X86Operand.Immediate(readImm8s().toLong(), 8))
            0x05 -> emit("add", accumulator(), X86Operand.Immediate(readImm32s().toLong(), 32))

            // OR
            0x08 -> decodeRmReg("or", 8, false)
            0x09 -> decodeRmReg("or", opSize(), false)
            0x0A -> decodeRmReg("or", 8, true)
            0x0B -> decodeRmReg("or", opSize(), true)

            // AND
            0x20 -> decodeRmReg("and", 8, false)
            0x21 -> decodeRmReg("and", opSize(), false)
            0x22 -> decodeRmReg("and", 8, true)
            0x23 -> decodeRmReg("and", opSize(), true)
            0x24 -> emit("and", X86Operand.Register("al"), X86Operand.Immediate(readImm8s().toLong(), 8))
            0x25 -> emit("and", accumulator(), X86Operand.Immediate(readImm32s().toLong(), 32))

            // SUB
            0x28 -> decodeRmReg("sub", 8, false)
            0x29 -> decodeRmReg("sub", opSize(), false)
            0x2A -> decodeRmReg("sub", 8, true)
            0x2B -> decodeRmReg("sub", opSize(), true)
            0x2C -> emit("sub", X86Operand.Register("al"), X86Operand.Immediate(readImm8s().toLong(), 8))
            0x2D -> emit("sub", accumulator(), X86Operand.Immediate(readImm32s().toLong(), 32))

            // XOR
            0x30 -> decodeRmReg("xor", 8, false)
            0x31 -> decodeRmReg("xor", opSize(), false)
            0x32 -> decodeRmReg("xor", 8, true)
            0x33 -> decodeRmReg("xor", opSize(), true)

            // CMP
            0x38 -> decodeRmReg("cmp", 8, false)
            0x39 -> decodeRmReg("cmp", opSize(), false)
            0x3A -> decodeRmReg("cmp", 8, true)
            0x3B -> decodeRmReg("cmp", opSize(), true)
            0x3C -> emit("cmp", X86Operand.Register("al"), X86Operand.Immediate(readImm8s().toLong(), 8))
            0x3D -> emit("cmp", accumulator(), X86Operand.Immediate(readImm32s().toLong(), 32))

            // TEST
            0x84 -> decodeRmReg("test", 8, false)
            0x85 -> decodeRmReg("test", opSize(), false)
            0xA8 -> emit("test", X86Operand.Register("al"), X86Operand.Immediate(readImm8s().toLong(), 8))
            0xA9 -> emit("test", accumulator(), X86Operand.Immediate(readImm32s().toLong(), 32))

            // MOV rm, r / r, rm
            0x88 -> decodeRmReg("mov", 8, false)
            0x89 -> decodeRmReg("mov", opSize(), false)
            0x8A -> decodeRmReg("mov", 8, true)
            0x8B -> decodeRmReg("mov", opSize(), true)

            // LEA
            0x8D -> {
                val modrm = readModRM()
                val (rm, _) = decodeModRM(modrm, opSize())
                emit("lea", regOperand(modrm.reg, opSize()), rm)
            }

            // MOV rm, imm
            0xC6 -> {
                val modrm = readModRM()
                val (rm, _) = decodeModRM(modrm, 8)
                emit("mov", rm, X86Operand.Immediate(readImm8s().toLong(), 8))
            }
            0xC7 -> {
                val modrm = readModRM()
                val sz = opSize()
                val (rm, _) = decodeModRM(modrm, sz)
                emit("mov", rm, X86Operand.Immediate(readImm32s().toLong(), 32))
            }

            // INC/DEC/CALL/JMP Group 5
            0xFF -> decodeGroup5()
            // INC/DEC Group 4 (8-bit)
            0xFE -> {
                val modrm = readModRM()
                val (rm, _) = decodeModRM(modrm, 8)
                val mnemonic = when (modrm.ext) {
                    0 -> "inc"; 1 -> "dec"; else -> "?ff/${modrm.ext}"
                }
                emit(mnemonic, rm)
            }

            // CALL rel32
            0xE8 -> {
                val disp = readImm32s()
                val target = address + (pos - startOffset) + disp
                emit("call", X86Operand.Relative(target))
            }

            // JMP rel32
            0xE9 -> {
                val disp = readImm32s()
                val target = address + (pos - startOffset) + disp
                emit("jmp", X86Operand.Relative(target))
            }

            // JMP rel8
            0xEB -> {
                val disp = readImm8s()
                val target = address + (pos - startOffset) + disp
                emit("jmp", X86Operand.Relative(target))
            }

            // Jcc rel8
            in 0x70..0x7F -> {
                val cc = ccName(op - 0x70)
                val disp = readImm8s()
                val target = address + (pos - startOffset) + disp
                emit("j$cc", X86Operand.Relative(target))
            }

            // XCHG eax/rax, r
            in 0x91..0x97 -> {
                val reg = op - 0x90
                if (rexW) emit("xchg", reg64(0), reg64(reg))
                else emit("xchg", reg32(0), reg32(reg))
            }

            // CDQ/CQO
            0x99 -> emit(if (rexW) "cqo" else "cdq")

            // LEAVE
            0xC9 -> emit("leave")

            // INT3
            0xCC -> emit("int3")

            // INT imm8
            0xCD -> emit("int", X86Operand.Immediate(readImm8s().toLong(), 8))

            // SYSCALL (actually 0x0F 0x05, but for completeness)

            // Shift Group 2
            0xD0 -> decodeShiftGroup(8, shiftBy = 1)
            0xD1 -> decodeShiftGroup(opSize(), shiftBy = 1)
            0xD2 -> decodeShiftGroup(8, shiftBy = -1) // CL
            0xD3 -> decodeShiftGroup(opSize(), shiftBy = -1) // CL
            0xC0 -> decodeShiftGroup(8, shiftBy = 0) // imm8
            0xC1 -> decodeShiftGroup(opSize(), shiftBy = 0) // imm8

            // NOT/NEG/MUL/IMUL/DIV/IDIV Group 3
            0xF6 -> decodeGroup3(8)
            0xF7 -> decodeGroup3(opSize())

            // MOVS/STOS/LODS/SCAS string instructions
            0xA4 -> emit(if (hasRep) "rep movsb" else "movsb")
            0xA5 -> emit(if (hasRep) "rep movs${dqSuffix()}" else "movs${dqSuffix()}")
            0xAA -> emit(if (hasRep) "rep stosb" else "stosb")
            0xAB -> emit(if (hasRep) "rep stos${dqSuffix()}" else "stos${dqSuffix()}")

            else -> fail()
        }

        private fun decodeTwoByte(op2: Int): X86Instruction? = when (op2) {
            // SYSCALL
            0x05 -> emit("syscall")

            // Jcc rel32
            in 0x80..0x8F -> {
                val cc = ccName(op2 - 0x80)
                val disp = readImm32s()
                val target = address + (pos - startOffset) + disp
                emit("j$cc", X86Operand.Relative(target))
            }

            // SETcc
            in 0x90..0x9F -> {
                val cc = ccName(op2 - 0x90)
                val modrm = readModRM()
                val (rm, _) = decodeModRM(modrm, 8)
                emit("set$cc", rm)
            }

            // CMOVcc
            in 0x40..0x4F -> {
                val cc = ccName(op2 - 0x40)
                val modrm = readModRM()
                val sz = opSize()
                val (rm, _) = decodeModRM(modrm, sz)
                emit("cmov$cc", regOperand(modrm.reg, sz), rm)
            }

            // MOVZX
            0xB6 -> {
                val modrm = readModRM()
                val (rm, _) = decodeModRM(modrm, 8)
                emit("movzx", regOperand(modrm.reg, opSize()), rm)
            }
            0xB7 -> {
                val modrm = readModRM()
                val (rm, _) = decodeModRM(modrm, 16)
                emit("movzx", regOperand(modrm.reg, opSize()), rm)
            }

            // MOVSX
            0xBE -> {
                val modrm = readModRM()
                val (rm, _) = decodeModRM(modrm, 8)
                emit("movsx", regOperand(modrm.reg, opSize()), rm)
            }
            0xBF -> {
                val modrm = readModRM()
                val (rm, _) = decodeModRM(modrm, 16)
                emit("movsx", regOperand(modrm.reg, opSize()), rm)
            }

            // IMUL r, rm
            0xAF -> {
                val modrm = readModRM()
                val sz = opSize()
                val (rm, _) = decodeModRM(modrm, sz)
                emit("imul", regOperand(modrm.reg, sz), rm)
            }

            // BSF/BSR
            0xBC -> {
                val modrm = readModRM()
                val sz = opSize()
                val (rm, _) = decodeModRM(modrm, sz)
                emit(if (hasRep) "tzcnt" else "bsf", regOperand(modrm.reg, sz), rm)
            }
            0xBD -> {
                val modrm = readModRM()
                val sz = opSize()
                val (rm, _) = decodeModRM(modrm, sz)
                emit(if (hasRep) "lzcnt" else "bsr", regOperand(modrm.reg, sz), rm)
            }

            // BSWAP
            in 0xC8..0xCF -> {
                val reg = op2 - 0xC8
                if (rexW) emit("bswap", reg64(reg))
                else emit("bswap", reg32(reg))
            }

            // NOP rm (0F 1F /0)
            0x1F -> {
                val modrm = readModRM()
                decodeModRM(modrm, opSize())
                emit("nop")
            }

            else -> fail()
        }

        // Group decoders

        private fun decodeGroup1Imm(size: Int, signExtImm8: Boolean): X86Instruction? {
            val modrm = readModRM()
            val (rm, _) = decodeModRM(modrm, size)
            val mnemonic = GROUP1_MNEMONICS[modrm.ext]
            val imm = if (signExtImm8 || size == 8) {
                X86Operand.Immediate(readImm8s().toLong(), 8)
            } else {
                X86Operand.Immediate(readImm32s().toLong(), 32)
            }
            return emit(mnemonic, rm, imm)
        }

        private fun decodeGroup3(size: Int): X86Instruction? {
            val modrm = readModRM()
            val (rm, _) = decodeModRM(modrm, size)
            return when (modrm.ext) {
                0 -> {
                    val imm = if (size == 8) X86Operand.Immediate(readImm8s().toLong(), 8)
                    else X86Operand.Immediate(readImm32s().toLong(), 32)
                    emit("test", rm, imm)
                }
                2 -> emit("not", rm)
                3 -> emit("neg", rm)
                4 -> emit("mul", rm)
                5 -> emit("imul", rm)
                6 -> emit("div", rm)
                7 -> emit("idiv", rm)
                else -> fail()
            }
        }

        private fun decodeGroup5(): X86Instruction? {
            val modrm = readModRM()
            val sz = opSize()
            val (rm, _) = decodeModRM(modrm, sz)
            return when (modrm.ext) {
                0 -> emit("inc", rm)
                1 -> emit("dec", rm)
                2 -> emit("call", rm)
                4 -> emit("jmp", rm)
                6 -> emit("push", rm)
                else -> fail()
            }
        }

        private fun decodeShiftGroup(size: Int, shiftBy: Int): X86Instruction? {
            val modrm = readModRM()
            val (rm, _) = decodeModRM(modrm, size)
            val mnemonic = SHIFT_MNEMONICS[modrm.ext] ?: return fail()
            val count: X86Operand = when (shiftBy) {
                1 -> X86Operand.Immediate(1, 8)
                0 -> X86Operand.Immediate(readImm8s().toLong(), 8)
                else -> X86Operand.Register("cl") // -1 = CL
            }
            return emit(mnemonic, rm, count)
        }

        private fun decodeRmReg(mnemonic: String, size: Int, regIsDst: Boolean): X86Instruction? {
            val modrm = readModRM()
            val (rm, _) = decodeModRM(modrm, size)
            val reg = regOperand(modrm.reg, size)
            return if (regIsDst) emit(mnemonic, reg, rm) else emit(mnemonic, rm, reg)
        }

        // ModR/M + SIB decoding

        data class ModRM(val mod: Int, val reg: Int, val rm: Int, val ext: Int)

        fun readModRM(): ModRM {
            val b = u8()
            val mod = (b shr 6) and 3
            var reg = (b shr 3) and 7
            var rm = b and 7
            if (rexR) reg = reg or 8
            if (rexB && mod == 3) rm = rm or 8
            return ModRM(mod, reg, rm, (b shr 3) and 7) // ext = raw reg field (unmodified by REX.R)
        }

        fun decodeModRM(modrm: ModRM, size: Int): Pair<X86Operand, Int> {
            if (modrm.mod == 3) {
                return regOperand(modrm.rm, size) to 0
            }

            var rawRm = modrm.rm and 7 // without REX.B for memory
            val rmWithRex = if (rexB) rawRm or 8 else rawRm

            // SIB byte needed?
            if (rawRm == 4) { // SIB follows
                val sib = u8()
                val scale = 1 shl ((sib shr 6) and 3)
                var indexReg = (sib shr 3) and 7
                var baseReg = sib and 7
                if (rexX) indexReg = indexReg or 8
                if (rexB) baseReg = baseReg or 8

                val index = if (indexReg == 4 && !rexX) null else regName64(indexReg)
                val disp = when (modrm.mod) {
                    0 -> if ((baseReg and 7) == 5) readImm32s().toLong() else 0L
                    1 -> readImm8s().toLong()
                    2 -> readImm32s().toLong()
                    else -> 0L
                }
                val base = if (modrm.mod == 0 && (baseReg and 7) == 5) null else regName64(baseReg)
                return X86Operand.Memory(size, base, index, scale, disp, segment) to 0
            }

            // RIP-relative (mod=00, rm=5)
            if (modrm.mod == 0 && rawRm == 5) {
                val disp = readImm32s().toLong()
                return X86Operand.Memory(size, null, null, 1, disp, segment, ripRelative = true) to 0
            }

            val disp = when (modrm.mod) {
                0 -> 0L
                1 -> readImm8s().toLong()
                2 -> readImm32s().toLong()
                else -> 0L
            }
            val base = regName64(rmWithRex)
            return X86Operand.Memory(size, base, null, 1, disp, segment) to 0
        }

        // Helpers

        fun u8(): Int {
            if (pos >= bytes.size) return 0
            return bytes[pos++].toInt() and 0xFF
        }

        fun readImm8s(): Int {
            val b = u8()
            return if (b > 127) b - 256 else b
        }

        fun readImm16(): Int {
            val lo = u8()
            val hi = u8()
            return lo or (hi shl 8)
        }

        fun readImm32s(): Int {
            val b0 = u8(); val b1 = u8(); val b2 = u8(); val b3 = u8()
            return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
        }

        fun readImm64(): Long {
            val lo = readImm32s().toLong() and 0xFFFFFFFFL
            val hi = readImm32s().toLong() and 0xFFFFFFFFL
            return lo or (hi shl 32)
        }

        fun rexBit(): Int = if (rexB) 8 else 0

        fun opSize(): Int = when {
            rexW -> 64
            hasOperandSizeOverride -> 16
            else -> 32
        }

        fun accumulator(): X86Operand.Register = when {
            rexW -> X86Operand.Register("rax")
            hasOperandSizeOverride -> X86Operand.Register("ax")
            else -> X86Operand.Register("eax")
        }

        fun dqSuffix(): String = when {
            rexW -> "q"
            hasOperandSizeOverride -> "w"
            else -> "d"
        }

        fun regOperand(encoding: Int, size: Int): X86Operand.Register = X86Operand.Register(
            when (size) {
                8 -> regName8(encoding)
                16 -> regName16(encoding)
                32 -> regName32(encoding)
                64 -> regName64(encoding)
                else -> "r?$encoding"
            }
        )

        fun reg64(encoding: Int): X86Operand.Register =
            X86Operand.Register(regName64(encoding or rexBit()))
        fun reg32(encoding: Int): X86Operand.Register =
            X86Operand.Register(regName32(encoding or rexBit()))
        fun reg16(encoding: Int): X86Operand.Register =
            X86Operand.Register(regName16(encoding or rexBit()))
        fun reg8(encoding: Int): X86Operand.Register =
            X86Operand.Register(regName8(encoding or rexBit()))

        fun emit(mnemonic: String, vararg operands: X86Operand): X86Instruction {
            val instrBytes = bytes.copyOfRange(startOffset, pos)
            return X86Instruction(address, instrBytes, mnemonic, operands.toList())
        }

        fun fail(): X86Instruction {
            if (pos <= startOffset) pos = startOffset + 1
            return emit("db", X86Operand.Immediate(bytes[startOffset].toLong() and 0xFF, 8))
        }

        fun ccName(idx: Int): String = CC_NAMES[idx and 0xF]

        companion object {
            val CC_NAMES = arrayOf(
                "o", "no", "b", "nb", "z", "nz", "be", "a",
                "s", "ns", "p", "np", "l", "nl", "le", "g",
            )

            val GROUP1_MNEMONICS = arrayOf("add", "or", "adc", "sbb", "and", "sub", "xor", "cmp")

            val SHIFT_MNEMONICS = mapOf(
                0 to "rol", 1 to "ror", 2 to "rcl", 3 to "rcr",
                4 to "shl", 5 to "shr", 7 to "sar",
            )

            val GP_REGS_64 = arrayOf(
                "rax", "rcx", "rdx", "rbx", "rsp", "rbp", "rsi", "rdi",
                "r8", "r9", "r10", "r11", "r12", "r13", "r14", "r15",
            )

            val GP_REGS_32 = arrayOf(
                "eax", "ecx", "edx", "ebx", "esp", "ebp", "esi", "edi",
                "r8d", "r9d", "r10d", "r11d", "r12d", "r13d", "r14d", "r15d",
            )

            val GP_REGS_16 = arrayOf(
                "ax", "cx", "dx", "bx", "sp", "bp", "si", "di",
                "r8w", "r9w", "r10w", "r11w", "r12w", "r13w", "r14w", "r15w",
            )

            val GP_REGS_8 = arrayOf(
                "al", "cl", "dl", "bl", "spl", "bpl", "sil", "dil",
                "r8b", "r9b", "r10b", "r11b", "r12b", "r13b", "r14b", "r15b",
            )

            val GP_REGS_8_NO_REX = arrayOf(
                "al", "cl", "dl", "bl", "ah", "ch", "dh", "bh",
            )

            fun regName64(enc: Int) = GP_REGS_64[enc and 0xF]
            fun regName32(enc: Int) = GP_REGS_32[enc and 0xF]
            fun regName16(enc: Int) = GP_REGS_16[enc and 0xF]
            fun regName8(enc: Int) = GP_REGS_8[enc and 0xF]
        }
    }
}
