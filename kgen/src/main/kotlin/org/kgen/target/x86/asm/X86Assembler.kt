package org.kgen.target.x86.asm

import org.kgen.target.x86.*
import java.io.ByteArrayOutputStream

class X86Assembler : X86AssemblerOps() {

    private val buffer = ByteArrayOutputStream()
    private val labels = mutableMapOf<String, Int>()
    private val fixups = mutableListOf<Fixup>()
    private val typedLabels = mutableListOf<Label>()

    private data class Fixup(
        val offset: Int,
        val label: String,
        val size: Int,
        val relative: Boolean,
        val addend: Int = 0,
    )

    class Label internal constructor(val id: Int) {
        internal var offset: Int = -1
        internal val marked: Boolean get() = offset >= 0
    }

    fun label(): Label {
        val label = Label(typedLabels.size)
        typedLabels.add(label)
        return label
    }

    fun mark(): Label {
        val label = label()
        mark(label)
        return label
    }

    fun mark(label: Label) {
        check(!label.marked) { "label ${label.id} already marked" }
        label.offset = buffer.size()
        labels["__typed_label_${label.id}"] = buffer.size()
    }

    // Typed label branch overloads

    fun jmp(label: Label) {
        emitByte(0xE9)
        emitLabelFixup(label, 4)
    }

    fun jcc(condition: X86Condition, label: Label) {
        emitByte(0x0F)
        emitByte(0x80 + condition.ordinal)
        emitLabelFixup(label, 4)
    }

    fun je(label: Label) = jcc(X86Condition.EQUAL, label)
    fun jz(label: Label) = je(label)
    fun jne(label: Label) = jcc(X86Condition.NOT_EQUAL, label)
    fun jnz(label: Label) = jne(label)
    fun jl(label: Label) = jcc(X86Condition.LESS, label)
    fun jge(label: Label) = jcc(X86Condition.GREATER_EQUAL, label)
    fun jle(label: Label) = jcc(X86Condition.LESS_EQUAL, label)
    fun jg(label: Label) = jcc(X86Condition.GREATER, label)
    fun jb(label: Label) = jcc(X86Condition.BELOW, label)
    fun jae(label: Label) = jcc(X86Condition.ABOVE_EQUAL, label)
    fun ja(label: Label) = jcc(X86Condition.ABOVE, label)
    fun jbe(label: Label) = jcc(X86Condition.BELOW_EQUAL, label)
    fun jo(label: Label) = jcc(X86Condition.OVERFLOW, label)
    fun jno(label: Label) = jcc(X86Condition.NOT_OVERFLOW, label)
    fun js(label: Label) = jcc(X86Condition.SIGN, label)
    fun jns(label: Label) = jcc(X86Condition.NOT_SIGN, label)

    fun call(label: Label) {
        emitByte(0xE8)
        emitLabelFixup(label, 4)
    }

    private fun emitLabelFixup(label: Label, size: Int) {
        if (label.marked) {
            val rel = label.offset - (buffer.size() + size)
            when (size) {
                1 -> emitByte(rel)
                4 -> emitInt32(rel)
            }
        } else {
            fixups.add(Fixup(buffer.size(), "__typed_label_${label.id}", size, relative = true))
            when (size) {
                1 -> emitByte(0)
                4 -> emitInt32(0)
            }
        }
    }

    fun position(): Int = buffer.size()

    fun label(name: String) {
        labels[name] = buffer.size()
    }

    /** Returns the byte offset of a previously defined label, or -1 if not found. */
    fun labelOffset(name: String): Int = labels[name] ?: -1

    fun toByteArray(): ByteArray {
        val bytes = buffer.toByteArray()
        for (fixup in fixups) {
            val target = labels[fixup.label]
                ?: throw IllegalStateException("Unresolved label: ${fixup.label}")
            if (fixup.relative) {
                val rel = target - (fixup.offset + fixup.size)
                when (fixup.size) {
                    1 -> bytes[fixup.offset] = rel.toByte()
                    4 -> putInt32(bytes, fixup.offset, rel + fixup.addend)
                }
            } else {
                when (fixup.size) {
                    4 -> putInt32(bytes, fixup.offset, target + fixup.addend)
                    8 -> putInt64(bytes, fixup.offset, target.toLong() + fixup.addend)
                }
            }
        }
        return bytes
    }

    fun reset() {
        buffer.reset()
        labels.clear()
        fixups.clear()
    }

    fun emitByte(b: Int) {
        buffer.write(b and 0xFF)
    }

    fun emitBytes(vararg bytes: Int) {
        for (b in bytes) buffer.write(b and 0xFF)
    }

    fun emitInt16(v: Int) {
        buffer.write(v and 0xFF)
        buffer.write((v shr 8) and 0xFF)
    }

    fun emitInt32(v: Int) {
        buffer.write(v and 0xFF)
        buffer.write((v shr 8) and 0xFF)
        buffer.write((v shr 16) and 0xFF)
        buffer.write((v shr 24) and 0xFF)
    }

    fun emitInt64(v: Long) {
        emitInt32(v.toInt())
        emitInt32((v shr 32).toInt())
    }

    fun emitRaw(bytes: ByteArray) {
        buffer.write(bytes)
    }

    fun jmpLabel(label: String) {
        emitByte(0xE9)
        fixups.add(Fixup(buffer.size(), label, 4, relative = true))
        emitInt32(0)
    }

    fun jmpReg(reg: X86Register64) {
        val enc = (reg as X86Register).encoding
        if (enc >= 8) emitByte(0x41) // REX.B
        emitByte(0xFF)
        emitByte(0xE0 + (enc and 7)) // ModR/M: mod=11, reg=4 (/4), rm=enc
    }

    fun jccLabel(condCode: Int, label: String) {
        emitByte(0x0F)
        emitByte(0x80 + condCode)
        fixups.add(Fixup(buffer.size(), label, 4, relative = true))
        emitInt32(0)
    }

    fun callLabel(label: String) {
        emitByte(0xE8)
        fixups.add(Fixup(buffer.size(), label, 4, relative = true))
        emitInt32(0)
    }

    /**
     * Emit a `call rel32` for an external symbol.
     * Returns the offset of the rel32 field where a relocation should be placed.
     */
    fun callExtern(): Int {
        emitByte(0xE8)
        val relocOffset = buffer.size()
        emitInt32(0)
        return relocOffset
    }

    /**
     * Emit a `jmp rel32` for an external symbol.
     * Returns the offset of the rel32 field where a relocation should be placed.
     */
    fun jmpExtern(): Int {
        emitByte(0xE9)
        val relocOffset = buffer.size()
        emitInt32(0)
        return relocOffset
    }

    override fun encodeLegacy(enc: X86EncodingInfo, vararg operands: Any) {
        // Classify operands
        val regs = mutableListOf<X86Register>()
        val mem: X86Memory? = operands.firstOrNull { it is X86Memory } as? X86Memory
        var imm: Long? = null
        var immSize = 0

        for (op in operands) {
            when (op) {
                is X86Register -> regs.add(op)
                is X86Memory -> {} // already captured
                is Byte -> { imm = op.toLong(); immSize = 1 }
                is Short -> { imm = op.toLong(); immSize = 2 }
                is Int -> { imm = op.toLong(); immSize = 4 }
                is Long -> {
                    imm = op
                    immSize = if (enc.rexW && enc.plusReg) 8 else 4
                }
            }
        }

        // Mandatory prefix (0x66 for 16-bit, 0xF2/0xF3 for SSE)
        if (enc.mandatoryPrefix != 0) {
            emitByte(enc.mandatoryPrefix)
        }

        // REX prefix
        val regOp = if (enc.modrmMode != ModrmMode.NONE && regs.isNotEmpty()) regs[0] else null
        val rmReg = when {
            enc.modrmMode == ModrmMode.REG && regs.size >= 2 -> regs[1]
            enc.modrmMode == ModrmMode.EXT && regs.isNotEmpty() -> regs[0]
            else -> null
        }

        // SPL/BPL/SIL/DIL (encoding 4-7, 8-bit) require REX to distinguish from AH/CH/DH/BH
        val hasRexByteReg = regs.any { it.bits() == 8 && it.encoding in 4..7 && it.name() in rexByteRegNames }

        val needRex = enc.rexW
            || (regOp != null && !enc.plusReg && regOp.encoding >= 8)
            || (rmReg != null && rmReg.encoding >= 8)
            || (enc.plusReg && regs.isNotEmpty() && regs[0].encoding >= 8)
            || (mem != null && (mem.base >= 8 || mem.index >= 8))
            || hasRexByteReg

        if (needRex) {
            var rex = 0x40
            if (enc.rexW) rex = rex or 0x08
            if (enc.plusReg && regs.isNotEmpty() && regs[0].encoding >= 8) {
                rex = rex or 0x01 // REX.B for +rd
            } else if (enc.modrmMode != ModrmMode.NONE) {
                // REX.R = reg field extension
                val r = when (enc.modrmMode) {
                    ModrmMode.REG -> regOp
                    else -> null
                }
                if (r != null && r.encoding >= 8) rex = rex or 0x04

                // REX.B = r/m or base extension
                if (rmReg != null && rmReg.encoding >= 8) {
                    rex = rex or 0x01
                } else if (mem != null && mem.base >= 8) {
                    rex = rex or 0x01
                }
                // REX.X = index extension
                if (mem != null && mem.index >= 8) {
                    rex = rex or 0x02
                }
            }
            emitByte(rex)
        }

        // Opcode bytes
        if (enc.plusReg && regs.isNotEmpty()) {
            // +rd encoding: last opcode byte has register encoding added
            for (i in 0 until enc.opcode.size - 1) {
                emitByte(enc.opcode[i])
            }
            emitByte(enc.opcode.last() + (regs[0].encoding and 7))
        } else {
            for (b in enc.opcode) {
                emitByte(b)
            }
        }

        // ModR/M + SIB + Displacement
        when (enc.modrmMode) {
            ModrmMode.NONE -> {}
            ModrmMode.REG -> {
                if (mem != null) {
                    val reg = regs[0].encoding and 7
                    emitModRM_Mem(reg, mem)
                } else if (regs.size >= 2) {
                    // reg-reg: ModR/M mod=11
                    val reg = regs[0].encoding and 7
                    val rm = regs[1].encoding and 7
                    emitByte(0xC0 or (reg shl 3) or rm)
                }
            }
            ModrmMode.EXT -> {
                val ext = enc.opcodeExt and 7
                if (mem != null) {
                    emitModRM_Mem(ext, mem)
                } else if (regs.isNotEmpty()) {
                    val rm = regs[0].encoding and 7
                    emitByte(0xC0 or (ext shl 3) or rm)
                }
            }
        }

        // Immediate
        if (imm != null) {
            when (immSize) {
                1 -> emitByte(imm.toInt())
                2 -> emitInt16(imm.toInt())
                4 -> emitInt32(imm.toInt())
                8 -> emitInt64(imm)
            }
        }
    }

    private fun emitModRM_Mem(reg: Int, mem: X86Memory) {
        if (mem.ripRelative) {
            // RIP-relative: mod=00, r/m=101
            emitByte((reg shl 3) or 5)
            if (mem.label != null) {
                fixups.add(Fixup(buffer.size(), mem.label, 4, relative = true, addend = 0))
                emitInt32(0)
            } else {
                emitInt32(mem.displacement.toInt())
            }
            return
        }

        val base = mem.base and 7
        val hasIndex = mem.index >= 0
        val needsSIB = hasIndex || base == 4 // RSP/R12 always need SIB

        val disp = mem.displacement
        val mod = when {
            mem.base < 0 -> 0x00 // absolute / no base
            disp == 0L && base != 5 -> 0x00 // [base] (RBP/R13 needs disp8=0)
            disp in -128..127 -> 0x40
            else -> 0x80
        }

        if (mem.base < 0 && !hasIndex) {
            // Absolute address: mod=00, r/m=100 (SIB), SIB=0x25
            emitByte((reg shl 3) or 4)
            emitByte(0x25) // SIB: scale=0, index=4(none), base=5(disp32)
            emitInt32(disp.toInt())
            return
        }

        if (needsSIB) {
            emitByte(mod or (reg shl 3) or 4)
            val idxBits = if (hasIndex) (mem.index and 7) else 4 // 4=no index
            val scaleBits = when (mem.scale) {
                1 -> 0; 2 -> 1; 4 -> 2; 8 -> 3; else -> 0
            }
            emitByte((scaleBits shl 6) or (idxBits shl 3) or base)
        } else {
            emitByte(mod or (reg shl 3) or base)
        }

        when (mod) {
            0x40 -> emitByte(disp.toInt())
            0x80 -> emitInt32(disp.toInt())
            0x00 -> if (base == 5) emitInt32(0) // RBP special case
        }
    }

    override fun encodeVex(enc: X86EncodingInfo, vararg operands: Any) {
        // Classify operands: registers, memory, immediates
        val regs = mutableListOf<X86Register>()
        val mem: X86Memory? = operands.firstOrNull { it is X86Memory } as? X86Memory
        var imm: Long? = null
        var immSize = 0

        for (op in operands) {
            when (op) {
                is X86Register -> regs.add(op)
                is X86Memory -> {}
                is Byte -> { imm = op.toLong(); immSize = 1 }
                is Short -> { imm = op.toLong(); immSize = 2 }
                is Int -> { imm = op.toLong(); immSize = 4 }
                is Long -> { imm = op.toLong(); immSize = 4 }
            }
        }

        // Determine reg (ModRM.reg), rm (ModRM.r/m), and vvvv (VEX source register)
        val regReg: X86Register?   // ModRM.reg field
        val rmReg: X86Register?    // ModRM.r/m field (if reg-reg)
        val vvvvReg: X86Register?  // VEX.vvvv field

        when (enc.modrmMode) {
            ModrmMode.REG -> {
                // 3-operand: dest(reg), vvvv(src1), rm(src2)
                // 2-operand: dest(reg), rm(src)
                if (regs.size >= 3 || (regs.size == 2 && mem != null)) {
                    // 3 reg operands, or 2 regs + mem
                    regReg = regs[0]
                    vvvvReg = if (mem != null) regs[1] else regs[1]
                    rmReg = if (mem != null) null else if (regs.size >= 3) regs[2] else regs[1]
                } else if (regs.size == 2) {
                    // dest(reg), rm(src) — no vvvv
                    regReg = regs[0]
                    rmReg = regs[1]
                    vvvvReg = null
                } else {
                    regReg = regs.getOrNull(0)
                    rmReg = null
                    vvvvReg = null
                }
            }
            ModrmMode.EXT -> {
                // EXT mode: opcodeExt in reg field, operands are vvvv + r/m
                regReg = null // opcodeExt goes in reg field
                if (regs.size >= 2) {
                    vvvvReg = regs[0]
                    rmReg = regs[1]
                } else {
                    vvvvReg = null
                    rmReg = regs.getOrNull(0)
                }
            }
            ModrmMode.NONE -> {
                regReg = null; rmReg = null; vvvvReg = null
            }
        }

        // Compute REX-like bits (inverted for VEX)
        val regEnc = regReg?.encoding ?: 0
        val rmEnc = rmReg?.encoding ?: 0
        val vvvvEnc = vvvvReg?.encoding ?: 0

        val rxb_R = if (regEnc >= 8) 0 else 1  // inverted
        val rxb_X = if (mem != null && mem.index >= 8) 0 else 1  // inverted
        val rxb_B = when {
            mem != null && mem.base >= 8 -> 0
            rmReg != null && rmEnc >= 8 -> 0
            else -> 1
        }  // inverted

        val pp = when (enc.mandatoryPrefix) {
            0x66 -> 1; 0xF3 -> 2; 0xF2 -> 3; else -> 0
        }
        val vvvv = (vvvvEnc xor 0xF) and 0xF  // inverted
        val l = if (enc.vexL >= 0) enc.vexL else 0
        val w = if (enc.vexW >= 0) enc.vexW else 0

        val mapSelect = when (enc.vexMap) {
            VexMap.MAP_0F -> 1; VexMap.MAP_0F38 -> 2; VexMap.MAP_0F3A -> 3; else -> 1
        }

        // Use 2-byte VEX (0xC5) if possible: map=0F, no REX.X, no REX.B, W=0
        val canUse2Byte = mapSelect == 1 && rxb_X == 1 && rxb_B == 1 && w == 0
        if (canUse2Byte) {
            emitByte(0xC5)
            emitByte((rxb_R shl 7) or (vvvv shl 3) or (l shl 2) or pp)
        } else {
            emitByte(0xC4)
            emitByte((rxb_R shl 7) or (rxb_X shl 6) or (rxb_B shl 5) or mapSelect)
            emitByte((w shl 7) or (vvvv shl 3) or (l shl 2) or pp)
        }

        // Opcode byte(s)
        for (b in enc.opcode) emitByte(b)

        // ModR/M + SIB + Displacement
        when (enc.modrmMode) {
            ModrmMode.NONE -> {}
            ModrmMode.REG -> {
                val reg = regEnc and 7
                if (mem != null) {
                    emitModRM_Mem(reg, mem)
                } else if (rmReg != null) {
                    emitByte(0xC0 or (reg shl 3) or (rmEnc and 7))
                }
            }
            ModrmMode.EXT -> {
                val ext = enc.opcodeExt and 7
                if (mem != null) {
                    emitModRM_Mem(ext, mem)
                } else if (rmReg != null) {
                    emitByte(0xC0 or (ext shl 3) or (rmEnc and 7))
                }
            }
        }

        // Immediate
        if (imm != null) {
            when (immSize) {
                1 -> emitByte(imm.toInt())
                2 -> emitInt16(imm.toInt())
                4 -> emitInt32(imm.toInt())
            }
        }
    }

    override fun encodeEvex(enc: X86EncodingInfo, vararg operands: Any) {
        // Classify operands
        val regs = mutableListOf<X86Register>()
        val mem: X86Memory? = operands.firstOrNull { it is X86Memory } as? X86Memory
        var imm: Long? = null
        var immSize = 0

        for (op in operands) {
            when (op) {
                is X86Register -> regs.add(op)
                is X86Memory -> {}
                is Byte -> { imm = op.toLong(); immSize = 1 }
                is Short -> { imm = op.toLong(); immSize = 2 }
                is Int -> { imm = op.toLong(); immSize = 4 }
                is Long -> { imm = op.toLong(); immSize = 4 }
            }
        }

        // Determine reg, vvvv, and rm registers (same logic as VEX)
        val regReg: X86Register?
        val rmReg: X86Register?
        val vvvvReg: X86Register?

        when (enc.modrmMode) {
            ModrmMode.REG -> {
                if (regs.size >= 3 || (regs.size == 2 && mem != null)) {
                    regReg = regs[0]; vvvvReg = regs[1]
                    rmReg = if (mem != null) null else if (regs.size >= 3) regs[2] else regs[1]
                } else if (regs.size == 2) {
                    regReg = regs[0]; rmReg = regs[1]; vvvvReg = null
                } else {
                    regReg = regs.getOrNull(0); rmReg = null; vvvvReg = null
                }
            }
            ModrmMode.EXT -> {
                regReg = null
                if (regs.size >= 2) { vvvvReg = regs[0]; rmReg = regs[1] }
                else { vvvvReg = null; rmReg = regs.getOrNull(0) }
            }
            ModrmMode.NONE -> { regReg = null; rmReg = null; vvvvReg = null }
        }

        val regEnc = regReg?.encoding ?: 0
        val rmEnc = rmReg?.encoding ?: 0
        val vvvvEnc = vvvvReg?.encoding ?: 0

        // EVEX byte layout:
        // Byte 0: 0x62
        // Byte 1: R.X.B.R'.00.mm
        // Byte 2: W.vvvv.1.pp
        // Byte 3: z.L'L.b.V'.aaa

        val rxbR = if (regEnc >= 8) 0 else 1  // R (inverted)
        val rxbX = if (mem != null && mem.index >= 8) 0 else 1  // X (inverted)
        val rxbB = when {
            mem != null && mem.base >= 8 -> 0
            rmReg != null && rmEnc >= 8 -> 0
            else -> 1
        }  // B (inverted)
        val rPrime = if (regEnc >= 16) 0 else 1  // R' for regs 16-31 (inverted)

        val mm = when (enc.vexMap) {
            VexMap.MAP_0F -> 1; VexMap.MAP_0F38 -> 2; VexMap.MAP_0F3A -> 3; else -> 1
        }

        val pp = when (enc.mandatoryPrefix) {
            0x66 -> 1; 0xF3 -> 2; 0xF2 -> 3; else -> 0
        }
        val w = if (enc.vexW >= 0) enc.vexW else 0
        val vvvv = (vvvvEnc xor 0xF) and 0xF  // inverted, lower 4 bits
        val vPrime = if (vvvvEnc >= 16) 0 else 1  // V' for vvvv bit 4 (inverted)

        // L'L: 00=128, 01=256, 10=512
        val ll = if (enc.evexL >= 0) enc.evexL else 0

        emitByte(0x62)
        emitByte((rxbR shl 7) or (rxbX shl 6) or (rxbB shl 5) or (rPrime shl 4) or mm)
        emitByte((w shl 7) or (vvvv shl 3) or (1 shl 2) or pp)  // bit 2 is always 1
        emitByte((ll shl 5) or (vPrime shl 3))  // z=0, b=0, aaa=000

        // Opcode
        for (b in enc.opcode) emitByte(b)

        // ModR/M
        when (enc.modrmMode) {
            ModrmMode.NONE -> {}
            ModrmMode.REG -> {
                val reg = regEnc and 7
                if (mem != null) {
                    emitModRM_Mem(reg, mem)
                } else if (rmReg != null) {
                    emitByte(0xC0 or (reg shl 3) or (rmEnc and 7))
                }
            }
            ModrmMode.EXT -> {
                val ext = enc.opcodeExt and 7
                if (mem != null) {
                    emitModRM_Mem(ext, mem)
                } else if (rmReg != null) {
                    emitByte(0xC0 or (ext shl 3) or (rmEnc and 7))
                }
            }
        }

        // Immediate
        if (imm != null) {
            when (immSize) {
                1 -> emitByte(imm.toInt())
                2 -> emitInt16(imm.toInt())
                4 -> emitInt32(imm.toInt())
            }
        }
    }

    companion object {
        private val rexByteRegNames = setOf("spl", "bpl", "sil", "dil")

        private fun putInt32(bytes: ByteArray, offset: Int, value: Int) {
            bytes[offset] = value.toByte()
            bytes[offset + 1] = (value shr 8).toByte()
            bytes[offset + 2] = (value shr 16).toByte()
            bytes[offset + 3] = (value shr 24).toByte()
        }

        private fun putInt64(bytes: ByteArray, offset: Int, value: Long) {
            putInt32(bytes, offset, value.toInt())
            putInt32(bytes, offset + 4, (value shr 32).toInt())
        }
    }
}
