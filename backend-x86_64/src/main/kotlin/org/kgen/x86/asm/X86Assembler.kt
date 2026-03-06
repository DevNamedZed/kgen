package org.kgen.x86.asm

import org.kgen.x86.*
import java.io.ByteArrayOutputStream

class X86Assembler : X86AssemblerOps() {

    private val buffer = ByteArrayOutputStream()
    private val labels = mutableMapOf<String, Int>()
    private val fixups = mutableListOf<Fixup>()

    private data class Fixup(
        val offset: Int,
        val label: String,
        val size: Int,
        val relative: Boolean,
        val addend: Int = 0,
    )

    fun position(): Int = buffer.size()

    fun label(name: String) {
        labels[name] = buffer.size()
    }

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

        val needRex = enc.rexW
            || (regOp != null && !enc.plusReg && regOp.encoding >= 8)
            || (rmReg != null && rmReg.encoding >= 8)
            || (enc.plusReg && regs.isNotEmpty() && regs[0].encoding >= 8)
            || (mem != null && (mem.base >= 8 || mem.index >= 8))

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
        TODO("VEX encoding not yet implemented")
    }

    override fun encodeEvex(enc: X86EncodingInfo, vararg operands: Any) {
        TODO("EVEX encoding not yet implemented")
    }

    companion object {
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
