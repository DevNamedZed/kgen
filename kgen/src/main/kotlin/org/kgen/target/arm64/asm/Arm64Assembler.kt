package org.kgen.target.arm64.asm

import org.kgen.target.arm64.*
import java.io.ByteArrayOutputStream

/**
 * ARM64 (AArch64) assembler. Emits fixed-width 32-bit instructions in little-endian order.
 *
 * Covers the core integer instruction set: arithmetic, logical, shift, move,
 * branch, load/store, compare, and system instructions.
 */
class Arm64Assembler {

    private val buf = ByteArrayOutputStream()
    private val labels = mutableMapOf<String, Int>()
    private val fixups = mutableListOf<Fixup>()
    private val typedLabels = mutableListOf<Label>()

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
        label.offset = buf.size()
        labels["__typed_label_${label.id}"] = buf.size()
    }

    private data class Fixup(val offset: Int, val label: String, val kind: FixupKind)

    private enum class FixupKind {
        BRANCH_26,   // B, BL — 26-bit signed offset (imm26 * 4)
        BRANCH_19,   // B.cond, CBZ, CBNZ — 19-bit signed offset (imm19 * 4)
        BRANCH_14,   // TBZ, TBNZ — 14-bit signed offset
        ADRP_21,     // ADRP — 21-bit page offset
    }

    fun bytes(): ByteArray {
        resolveFixups()
        return buf.toByteArray()
    }

    fun size(): Int = buf.size()

    fun label(name: String) {
        labels[name] = buf.size()
    }

    private fun emit(inst: Int) {
        buf.write(inst and 0xFF)
        buf.write((inst shr 8) and 0xFF)
        buf.write((inst shr 16) and 0xFF)
        buf.write((inst shr 24) and 0xFF)
    }

    private fun enc(reg: Arm64Register64): Int = reg.encoding()
    private fun enc(reg: Arm64Register32): Int = reg.encoding()
    private fun encD(reg: Arm64VecD): Int = reg.encoding()
    private fun encS(reg: Arm64VecS): Int = reg.encoding()

    fun labelOffset(name: String): Int = labels[name] ?: -1

    fun unresolvedLabels(): List<Pair<Int, String>> {
        return fixups.filter { it.label !in labels }.map { it.offset to it.label }
    }

    private fun resolveFixups() {
        val data = buf.toByteArray()
        for (fixup in fixups) {
            val target = labels[fixup.label] ?: continue // skip unresolved (external symbols)
            val delta = (target - fixup.offset) / 4
            val existing = readLE32(data, fixup.offset)
            val patched = when (fixup.kind) {
                FixupKind.BRANCH_26 -> existing or (delta and 0x03FFFFFF)
                FixupKind.BRANCH_19 -> existing or ((delta and 0x7FFFF) shl 5)
                FixupKind.BRANCH_14 -> existing or ((delta and 0x3FFF) shl 5)
                FixupKind.ADRP_21 -> {
                    // ADRP uses split 21-bit page-relative offset: immhi[23:5] | immlo[30:29]
                    val pageTarget = target and 0xFFFFF000.toInt()
                    val pageFixup = fixup.offset and 0xFFFFF000.toInt()
                    val pageDelta = (pageTarget - pageFixup) shr 12
                    val immlo = (pageDelta and 0x3) shl 29
                    val immhi = ((pageDelta shr 2) and 0x7FFFF) shl 5
                    (existing and 0x9F00001F.toInt()) or immlo or immhi
                }
            }
            writeLE32(data, fixup.offset, patched)
        }
        buf.reset()
        buf.write(data)
    }

    private fun readLE32(data: ByteArray, off: Int): Int =
        (data[off].toInt() and 0xFF) or
        ((data[off + 1].toInt() and 0xFF) shl 8) or
        ((data[off + 2].toInt() and 0xFF) shl 16) or
        ((data[off + 3].toInt() and 0xFF) shl 24)

    private fun writeLE32(data: ByteArray, off: Int, value: Int) {
        data[off] = (value and 0xFF).toByte()
        data[off + 1] = ((value shr 8) and 0xFF).toByte()
        data[off + 2] = ((value shr 16) and 0xFF).toByte()
        data[off + 3] = ((value shr 24) and 0xFF).toByte()
    }

    // ── Data Processing (Register) ──────────────────────────────────

    // ADD Xd, Xn, Xm
    fun add(rd: Arm64Register64, rn: Arm64Register64, rm: Arm64Register64) {
        // sf=1, op=0, S=0, shift=00, Rm, imm6=000000, Rn, Rd
        emit(0x8B000000.toInt() or (enc(rm) shl 16) or (enc(rn) shl 5) or enc(rd))
    }

    // ADD Wd, Wn, Wm
    fun add(rd: Arm64Register32, rn: Arm64Register32, rm: Arm64Register32) {
        emit(0x0B000000 or (enc(rm) shl 16) or (enc(rn) shl 5) or enc(rd))
    }

    // ADD Xd, Xn, #imm12
    fun add(rd: Arm64Register64, rn: Arm64Register64, imm: Int) {
        require(imm in 0..4095) { "ADD immediate must be 0..4095, got $imm" }
        emit(0x91000000.toInt() or (imm shl 10) or (enc(rn) shl 5) or enc(rd))
    }

    // ADD Wd, Wn, #imm12
    fun add(rd: Arm64Register32, rn: Arm64Register32, imm: Int) {
        require(imm in 0..4095) { "ADD immediate must be 0..4095, got $imm" }
        emit(0x11000000 or (imm shl 10) or (enc(rn) shl 5) or enc(rd))
    }

    // ADDS (sets flags) — Xd, Xn, Xm
    fun adds(rd: Arm64Register64, rn: Arm64Register64, rm: Arm64Register64) {
        emit(0xAB000000.toInt() or (enc(rm) shl 16) or (enc(rn) shl 5) or enc(rd))
    }

    // ADDS Wd, Wn, Wm
    fun adds(rd: Arm64Register32, rn: Arm64Register32, rm: Arm64Register32) {
        emit(0x2B000000 or (enc(rm) shl 16) or (enc(rn) shl 5) or enc(rd))
    }

    // SUB Xd, Xn, Xm
    fun sub(rd: Arm64Register64, rn: Arm64Register64, rm: Arm64Register64) {
        emit(0xCB000000.toInt() or (enc(rm) shl 16) or (enc(rn) shl 5) or enc(rd))
    }

    // SUB Wd, Wn, Wm
    fun sub(rd: Arm64Register32, rn: Arm64Register32, rm: Arm64Register32) {
        emit(0x4B000000 or (enc(rm) shl 16) or (enc(rn) shl 5) or enc(rd))
    }

    // SUB Xd, Xn, #imm12
    fun sub(rd: Arm64Register64, rn: Arm64Register64, imm: Int) {
        require(imm in 0..4095) { "SUB immediate must be 0..4095, got $imm" }
        emit(0xD1000000.toInt() or (imm shl 10) or (enc(rn) shl 5) or enc(rd))
    }

    // SUB Wd, Wn, #imm12
    fun sub(rd: Arm64Register32, rn: Arm64Register32, imm: Int) {
        require(imm in 0..4095) { "SUB immediate must be 0..4095, got $imm" }
        emit(0x51000000 or (imm shl 10) or (enc(rn) shl 5) or enc(rd))
    }

    // SUBS (sets flags) — Xd, Xn, Xm
    fun subs(rd: Arm64Register64, rn: Arm64Register64, rm: Arm64Register64) {
        emit(0xEB000000.toInt() or (enc(rm) shl 16) or (enc(rn) shl 5) or enc(rd))
    }

    // SUBS Wd, Wn, Wm
    fun subs(rd: Arm64Register32, rn: Arm64Register32, rm: Arm64Register32) {
        emit(0x6B000000 or (enc(rm) shl 16) or (enc(rn) shl 5) or enc(rd))
    }

    // SUBS Xd, Xn, #imm12
    fun subs(rd: Arm64Register64, rn: Arm64Register64, imm: Int) {
        require(imm in 0..4095) { "SUBS immediate must be 0..4095, got $imm" }
        emit(0xF1000000.toInt() or (imm shl 10) or (enc(rn) shl 5) or enc(rd))
    }

    // NEG Xd, Xm (alias: SUB Xd, XZR, Xm)
    fun neg(rd: Arm64Register64, rm: Arm64Register64) {
        sub(rd, Arm64Register.XZR, rm)
    }

    // AND Xd, Xn, Xm
    fun and_(rd: Arm64Register64, rn: Arm64Register64, rm: Arm64Register64) {
        emit(0x8A000000.toInt() or (enc(rm) shl 16) or (enc(rn) shl 5) or enc(rd))
    }

    // AND Wd, Wn, Wm
    fun and_(rd: Arm64Register32, rn: Arm64Register32, rm: Arm64Register32) {
        emit(0x0A000000 or (enc(rm) shl 16) or (enc(rn) shl 5) or enc(rd))
    }

    // ORR Xd, Xn, Xm
    fun orr(rd: Arm64Register64, rn: Arm64Register64, rm: Arm64Register64) {
        emit(0xAA000000.toInt() or (enc(rm) shl 16) or (enc(rn) shl 5) or enc(rd))
    }

    // ORR Wd, Wn, Wm
    fun orr(rd: Arm64Register32, rn: Arm64Register32, rm: Arm64Register32) {
        emit(0x2A000000 or (enc(rm) shl 16) or (enc(rn) shl 5) or enc(rd))
    }

    // EOR Xd, Xn, Xm
    fun eor(rd: Arm64Register64, rn: Arm64Register64, rm: Arm64Register64) {
        emit(0xCA000000.toInt() or (enc(rm) shl 16) or (enc(rn) shl 5) or enc(rd))
    }

    // EOR Wd, Wn, Wm
    fun eor(rd: Arm64Register32, rn: Arm64Register32, rm: Arm64Register32) {
        emit(0x4A000000 or (enc(rm) shl 16) or (enc(rn) shl 5) or enc(rd))
    }

    // ── Shifts ──────────────────────────────────────────────────────

    // LSL Xd, Xn, Xm (LSLV)
    fun lsl(rd: Arm64Register64, rn: Arm64Register64, rm: Arm64Register64) {
        emit(0x9AC02000.toInt() or (enc(rm) shl 16) or (enc(rn) shl 5) or enc(rd))
    }

    // LSL Wd, Wn, Wm
    fun lsl(rd: Arm64Register32, rn: Arm64Register32, rm: Arm64Register32) {
        emit(0x1AC02000 or (enc(rm) shl 16) or (enc(rn) shl 5) or enc(rd))
    }

    // LSR Xd, Xn, Xm (LSRV)
    fun lsr(rd: Arm64Register64, rn: Arm64Register64, rm: Arm64Register64) {
        emit(0x9AC02400.toInt() or (enc(rm) shl 16) or (enc(rn) shl 5) or enc(rd))
    }

    // LSR Wd, Wn, Wm
    fun lsr(rd: Arm64Register32, rn: Arm64Register32, rm: Arm64Register32) {
        emit(0x1AC02400 or (enc(rm) shl 16) or (enc(rn) shl 5) or enc(rd))
    }

    // ASR Xd, Xn, Xm (ASRV)
    fun asr(rd: Arm64Register64, rn: Arm64Register64, rm: Arm64Register64) {
        emit(0x9AC02800.toInt() or (enc(rm) shl 16) or (enc(rn) shl 5) or enc(rd))
    }

    // ASR Wd, Wn, Wm
    fun asr(rd: Arm64Register32, rn: Arm64Register32, rm: Arm64Register32) {
        emit(0x1AC02800 or (enc(rm) shl 16) or (enc(rn) shl 5) or enc(rd))
    }

    // ── Multiply/Divide ─────────────────────────────────────────────

    // MUL Xd, Xn, Xm (MADD Xd, Xn, Xm, XZR)
    fun mul(rd: Arm64Register64, rn: Arm64Register64, rm: Arm64Register64) {
        emit(0x9B007C00.toInt() or (enc(rm) shl 16) or (enc(rn) shl 5) or enc(rd))
    }

    // MUL Wd, Wn, Wm
    fun mul(rd: Arm64Register32, rn: Arm64Register32, rm: Arm64Register32) {
        emit(0x1B007C00 or (enc(rm) shl 16) or (enc(rn) shl 5) or enc(rd))
    }

    // SDIV Xd, Xn, Xm
    fun sdiv(rd: Arm64Register64, rn: Arm64Register64, rm: Arm64Register64) {
        emit(0x9AC00C00.toInt() or (enc(rm) shl 16) or (enc(rn) shl 5) or enc(rd))
    }

    // SDIV Wd, Wn, Wm
    fun sdiv(rd: Arm64Register32, rn: Arm64Register32, rm: Arm64Register32) {
        emit(0x1AC00C00 or (enc(rm) shl 16) or (enc(rn) shl 5) or enc(rd))
    }

    // UDIV Xd, Xn, Xm
    fun udiv(rd: Arm64Register64, rn: Arm64Register64, rm: Arm64Register64) {
        emit(0x9AC00800.toInt() or (enc(rm) shl 16) or (enc(rn) shl 5) or enc(rd))
    }

    // UDIV Wd, Wn, Wm
    fun udiv(rd: Arm64Register32, rn: Arm64Register32, rm: Arm64Register32) {
        emit(0x1AC00800 or (enc(rm) shl 16) or (enc(rn) shl 5) or enc(rd))
    }

    // MSUB Xd, Xn, Xm, Xa (Xd = Xa - Xn * Xm)
    fun msub(rd: Arm64Register64, rn: Arm64Register64, rm: Arm64Register64, ra: Arm64Register64) {
        emit(0x9B008000.toInt() or (enc(rm) shl 16) or (enc(ra) shl 10) or (enc(rn) shl 5) or enc(rd))
    }

    // ── Bit Manipulation ──────────────────────────────────────────

    // CLZ Xd, Xn (count leading zeros, 64-bit)
    fun clz(rd: Arm64Register64, rn: Arm64Register64) {
        emit(0xDAC01000.toInt() or (enc(rn) shl 5) or enc(rd))
    }

    // CLZ Wd, Wn (count leading zeros, 32-bit)
    fun clz(rd: Arm64Register32, rn: Arm64Register32) {
        emit(0x5AC01000 or (enc(rn) shl 5) or enc(rd))
    }

    // RBIT Xd, Xn (reverse bits, 64-bit)
    fun rbit(rd: Arm64Register64, rn: Arm64Register64) {
        emit(0xDAC00000.toInt() or (enc(rn) shl 5) or enc(rd))
    }

    // RBIT Wd, Wn (reverse bits, 32-bit)
    fun rbit(rd: Arm64Register32, rn: Arm64Register32) {
        emit(0x5AC00000 or (enc(rn) shl 5) or enc(rd))
    }

    // REV Xd, Xn (reverse bytes, 64-bit)
    fun rev(rd: Arm64Register64, rn: Arm64Register64) {
        emit(0xDAC00C00.toInt() or (enc(rn) shl 5) or enc(rd))
    }

    // REV Wd, Wn (reverse bytes, 32-bit)
    fun rev(rd: Arm64Register32, rn: Arm64Register32) {
        emit(0x5AC00800 or (enc(rn) shl 5) or enc(rd))
    }

    // RORV Xd, Xn, Xm (rotate right variable, 64-bit)
    fun ror(rd: Arm64Register64, rn: Arm64Register64, rm: Arm64Register64) {
        emit(0x9AC02C00.toInt() or (enc(rm) shl 16) or (enc(rn) shl 5) or enc(rd))
    }

    // RORV Wd, Wn, Wm (rotate right variable, 32-bit)
    fun ror(rd: Arm64Register32, rn: Arm64Register32, rm: Arm64Register32) {
        emit(0x1AC02C00 or (enc(rm) shl 16) or (enc(rn) shl 5) or enc(rd))
    }

    // FMADD Dd, Dn, Dm, Da (fused multiply-add, double)
    fun fmadd(fd: Arm64VecD, fn: Arm64VecD, fm: Arm64VecD, fa: Arm64VecD) {
        emit(0x1F400000 or (encD(fm) shl 16) or (encD(fa) shl 10) or (encD(fn) shl 5) or encD(fd))
    }

    // FMADD Sd, Sn, Sm, Sa (fused multiply-add, single)
    fun fmadd(fd: Arm64VecS, fn: Arm64VecS, fm: Arm64VecS, fa: Arm64VecS) {
        emit(0x1F000000 or (encS(fm) shl 16) or (encS(fa) shl 10) or (encS(fn) shl 5) or encS(fd))
    }

    // ── Move ────────────────────────────────────────────────────────

    // MOV Xd, Xm (alias: ORR Xd, XZR, Xm)
    fun mov(rd: Arm64Register64, rm: Arm64Register64) {
        orr(rd, Arm64Register.XZR, rm)
    }

    // MOV Wd, Wm (alias: ORR Wd, WZR, Wm)
    fun mov(rd: Arm64Register32, rm: Arm64Register32) {
        orr(rd, Arm64Register.WZR, rm)
    }

    // MOV Xd, SP (or SP, Xn) — uses ADD Xd, SP, #0
    fun movSp(rd: Arm64Register64, rn: Arm64Register64) {
        emit(0x91000000.toInt() or (enc(rn) shl 5) or enc(rd))
    }

    // MOVZ Xd, #imm16 {, LSL #shift}
    fun movz(rd: Arm64Register64, imm: Int, shift: Int = 0) {
        require(imm in 0..0xFFFF) { "MOVZ immediate must fit in 16 bits" }
        val hw = shift / 16
        emit(0xD2800000.toInt() or (hw shl 21) or (imm shl 5) or enc(rd))
    }

    // MOVZ Wd, #imm16
    fun movz(rd: Arm64Register32, imm: Int, shift: Int = 0) {
        require(imm in 0..0xFFFF) { "MOVZ immediate must fit in 16 bits" }
        val hw = shift / 16
        emit(0x52800000 or (hw shl 21) or (imm shl 5) or enc(rd))
    }

    // MOVK Xd, #imm16 {, LSL #shift}
    fun movk(rd: Arm64Register64, imm: Int, shift: Int = 0) {
        require(imm in 0..0xFFFF) { "MOVK immediate must fit in 16 bits" }
        val hw = shift / 16
        emit(0xF2800000.toInt() or (hw shl 21) or (imm shl 5) or enc(rd))
    }

    // MOVN Xd, #imm16 {, LSL #shift}
    fun movn(rd: Arm64Register64, imm: Int, shift: Int = 0) {
        require(imm in 0..0xFFFF) { "MOVN immediate must fit in 16 bits" }
        val hw = shift / 16
        emit(0x92800000.toInt() or (hw shl 21) or (imm shl 5) or enc(rd))
    }

    // ── Compare ─────────────────────────────────────────────────────

    // CMP Xn, Xm (alias: SUBS XZR, Xn, Xm)
    fun cmp(rn: Arm64Register64, rm: Arm64Register64) {
        subs(Arm64Register.XZR, rn, rm)
    }

    // CMP Wn, Wm
    fun cmp(rn: Arm64Register32, rm: Arm64Register32) {
        subs(Arm64Register.WZR, rn, rm)
    }

    // CMP Xn, #imm12
    fun cmp(rn: Arm64Register64, imm: Int) {
        subs(Arm64Register.XZR, rn, imm)
    }

    // CMP Wn, #imm12
    fun cmp(rn: Arm64Register32, imm: Int) {
        // SUBS WZR, Wn, #imm12: 0x71000000 | (imm12 << 10) | (Rn << 5) | 0x1F (WZR=31)
        emit(0x71000000 or ((imm and 0xFFF) shl 10) or (enc(rn) shl 5) or 0x1F)
    }

    // CMN Xn, Xm (alias: ADDS XZR, Xn, Xm)
    fun cmn(rn: Arm64Register64, rm: Arm64Register64) {
        adds(Arm64Register.XZR, rn, rm)
    }

    // TST Xn, Xm (alias: ANDS XZR, Xn, Xm)
    fun tst(rn: Arm64Register64, rm: Arm64Register64) {
        // ANDS Xd, Xn, Xm — sf=1, opc=11, N=0
        emit(0xEA000000.toInt() or (enc(rm) shl 16) or (enc(rn) shl 5) or 31)
    }

    // ── Branch ──────────────────────────────────────────────────────

    // B label (26-bit offset)
    fun b(label: String) {
        fixups.add(Fixup(buf.size(), label, FixupKind.BRANCH_26))
        emit(0x14000000)
    }

    // B with raw byte offset (must be 4-byte aligned)
    fun b(offset: Int) {
        require(offset % 4 == 0) { "ARM64 branch offset must be 4-byte aligned" }
        val imm26 = (offset shr 2) and 0x03FFFFFF
        emit(0x14000000 or imm26)
    }

    // BL label (26-bit offset, sets LR)
    fun bl(label: String) {
        fixups.add(Fixup(buf.size(), label, FixupKind.BRANCH_26))
        emit(0x94000000.toInt())
    }

    // B.cond label (19-bit offset)
    fun bCond(cond: Arm64Condition, label: String) {
        fixups.add(Fixup(buf.size(), label, FixupKind.BRANCH_19))
        emit(0x54000000 or cond.code)
    }

    // CBZ Xt, label
    fun cbz(rt: Arm64Register64, label: String) {
        fixups.add(Fixup(buf.size(), label, FixupKind.BRANCH_19))
        emit(0xB4000000.toInt() or enc(rt))
    }

    // CBZ Wt, label
    fun cbz(rt: Arm64Register32, label: String) {
        fixups.add(Fixup(buf.size(), label, FixupKind.BRANCH_19))
        emit(0x34000000 or enc(rt))
    }

    // CBNZ Xt, label
    fun cbnz(rt: Arm64Register64, label: String) {
        fixups.add(Fixup(buf.size(), label, FixupKind.BRANCH_19))
        emit(0xB5000000.toInt() or enc(rt))
    }

    // CBNZ Wt, label
    fun cbnz(rt: Arm64Register32, label: String) {
        fixups.add(Fixup(buf.size(), label, FixupKind.BRANCH_19))
        emit(0x35000000 or enc(rt))
    }

    // Typed label branch overloads

    fun b(label: Label) {
        fixups.add(Fixup(buf.size(), "__typed_label_${label.id}", FixupKind.BRANCH_26))
        emit(0x14000000)
    }

    fun bl(label: Label) {
        fixups.add(Fixup(buf.size(), "__typed_label_${label.id}", FixupKind.BRANCH_26))
        emit(0x94000000.toInt())
    }

    fun bCond(cond: Arm64Condition, label: Label) {
        fixups.add(Fixup(buf.size(), "__typed_label_${label.id}", FixupKind.BRANCH_19))
        emit(0x54000000 or cond.code)
    }

    fun cbz(rt: Arm64Register64, label: Label) {
        fixups.add(Fixup(buf.size(), "__typed_label_${label.id}", FixupKind.BRANCH_19))
        emit(0xB4000000.toInt() or enc(rt))
    }

    fun cbz(rt: Arm64Register32, label: Label) {
        fixups.add(Fixup(buf.size(), "__typed_label_${label.id}", FixupKind.BRANCH_19))
        emit(0x34000000 or enc(rt))
    }

    fun cbnz(rt: Arm64Register64, label: Label) {
        fixups.add(Fixup(buf.size(), "__typed_label_${label.id}", FixupKind.BRANCH_19))
        emit(0xB5000000.toInt() or enc(rt))
    }

    fun cbnz(rt: Arm64Register32, label: Label) {
        fixups.add(Fixup(buf.size(), "__typed_label_${label.id}", FixupKind.BRANCH_19))
        emit(0x35000000 or enc(rt))
    }

    // BR Xn (indirect branch)
    fun br(rn: Arm64Register64) {
        emit(0xD61F0000.toInt() or (enc(rn) shl 5))
    }

    // BLR Xn (indirect call)
    fun blr(rn: Arm64Register64) {
        emit(0xD63F0000.toInt() or (enc(rn) shl 5))
    }

    // RET {Xn} (defaults to X30/LR)
    @JvmOverloads
    fun ret(rn: Arm64Register64 = Arm64Register.LR) {
        emit(0xD65F0000.toInt() or (enc(rn) shl 5))
    }

    // ── Load/Store ──────────────────────────────────────────────────

    // LDR Xt, [Xn, #imm] (unsigned offset, imm must be 8-byte aligned, 0..32760)
    fun ldr(rt: Arm64Register64, rn: Arm64Register64, imm: Int = 0) {
        val scaledImm = imm / 8
        require(imm >= 0 && imm % 8 == 0 && scaledImm <= 4095) { "LDR offset must be 8-aligned, 0..32760" }
        emit(0xF9400000.toInt() or (scaledImm shl 10) or (enc(rn) shl 5) or enc(rt))
    }

    // LDR Wt, [Xn, #imm] (unsigned offset, imm must be 4-byte aligned, 0..16380)
    fun ldr(rt: Arm64Register32, rn: Arm64Register64, imm: Int = 0) {
        val scaledImm = imm / 4
        require(imm >= 0 && imm % 4 == 0 && scaledImm <= 4095) { "LDR offset must be 4-aligned, 0..16380" }
        emit(0xB9400000.toInt() or (scaledImm shl 10) or (enc(rn) shl 5) or enc(rt))
    }

    // LDUR Xt, [Xn, #simm9] (unscaled, -256..255)
    fun ldur(rt: Arm64Register64, rn: Arm64Register64, imm: Int = 0) {
        require(imm in -256..255) { "LDUR offset must be -256..255" }
        emit(0xF8400000.toInt() or ((imm and 0x1FF) shl 12) or (enc(rn) shl 5) or enc(rt))
    }

    // LDUR Wt, [Xn, #simm9]
    fun ldur(rt: Arm64Register32, rn: Arm64Register64, imm: Int = 0) {
        require(imm in -256..255) { "LDUR offset must be -256..255" }
        emit(0xB8400000.toInt() or ((imm and 0x1FF) shl 12) or (enc(rn) shl 5) or enc(rt))
    }

    // STR Xt, [Xn, #imm] (unsigned offset, 8-byte aligned)
    fun str(rt: Arm64Register64, rn: Arm64Register64, imm: Int = 0) {
        val scaledImm = imm / 8
        require(imm >= 0 && imm % 8 == 0 && scaledImm <= 4095) { "STR offset must be 8-aligned, 0..32760" }
        emit(0xF9000000.toInt() or (scaledImm shl 10) or (enc(rn) shl 5) or enc(rt))
    }

    // STR Wt, [Xn, #imm] (unsigned offset, 4-byte aligned)
    fun str(rt: Arm64Register32, rn: Arm64Register64, imm: Int = 0) {
        val scaledImm = imm / 4
        require(imm >= 0 && imm % 4 == 0 && scaledImm <= 4095) { "STR offset must be 4-aligned, 0..16380" }
        emit(0xB9000000.toInt() or (scaledImm shl 10) or (enc(rn) shl 5) or enc(rt))
    }

    // STUR Xt, [Xn, #simm9]
    fun stur(rt: Arm64Register64, rn: Arm64Register64, imm: Int = 0) {
        require(imm in -256..255) { "STUR offset must be -256..255" }
        emit(0xF8000000.toInt() or ((imm and 0x1FF) shl 12) or (enc(rn) shl 5) or enc(rt))
    }

    // STUR Wt, [Xn, #simm9]
    fun stur(rt: Arm64Register32, rn: Arm64Register64, imm: Int = 0) {
        require(imm in -256..255) { "STUR offset must be -256..255" }
        emit(0xB8000000.toInt() or ((imm and 0x1FF) shl 12) or (enc(rn) shl 5) or enc(rt))
    }

    // STP Xt1, Xt2, [Xn, #imm] (signed offset, 8-byte aligned, -512..504)
    fun stp(rt1: Arm64Register64, rt2: Arm64Register64, rn: Arm64Register64, imm: Int = 0) {
        require(imm % 8 == 0 && imm / 8 in -64..63) { "STP offset must be 8-aligned, -512..504" }
        val scaledImm = (imm / 8) and 0x7F
        emit(0xA9000000.toInt() or (scaledImm shl 15) or (enc(rt2) shl 10) or (enc(rn) shl 5) or enc(rt1))
    }

    // LDP Xt1, Xt2, [Xn, #imm]
    fun ldp(rt1: Arm64Register64, rt2: Arm64Register64, rn: Arm64Register64, imm: Int = 0) {
        require(imm % 8 == 0 && imm / 8 in -64..63) { "LDP offset must be 8-aligned, -512..504" }
        val scaledImm = (imm / 8) and 0x7F
        emit(0xA9400000.toInt() or (scaledImm shl 15) or (enc(rt2) shl 10) or (enc(rn) shl 5) or enc(rt1))
    }

    // STP pre-index: STP Xt1, Xt2, [Xn, #imm]!
    fun stpPre(rt1: Arm64Register64, rt2: Arm64Register64, rn: Arm64Register64, imm: Int) {
        require(imm % 8 == 0 && imm / 8 in -64..63) { "STP pre-index offset must be 8-aligned, -512..504" }
        val scaledImm = (imm / 8) and 0x7F
        emit(0xA9800000.toInt() or (scaledImm shl 15) or (enc(rt2) shl 10) or (enc(rn) shl 5) or enc(rt1))
    }

    // LDP post-index: LDP Xt1, Xt2, [Xn], #imm
    fun ldpPost(rt1: Arm64Register64, rt2: Arm64Register64, rn: Arm64Register64, imm: Int) {
        require(imm % 8 == 0 && imm / 8 in -64..63) { "LDP post-index offset must be 8-aligned, -512..504" }
        val scaledImm = (imm / 8) and 0x7F
        emit(0xA8C00000.toInt() or (scaledImm shl 15) or (enc(rt2) shl 10) or (enc(rn) shl 5) or enc(rt1))
    }

    // LDRB Wt, [Xn, #imm] (unsigned offset, 0..4095)
    fun ldrb(rt: Arm64Register32, rn: Arm64Register64, imm: Int = 0) {
        require(imm in 0..4095) { "LDRB offset must be 0..4095" }
        emit(0x39400000 or (imm shl 10) or (enc(rn) shl 5) or enc(rt))
    }

    // STRB Wt, [Xn, #imm]
    fun strb(rt: Arm64Register32, rn: Arm64Register64, imm: Int = 0) {
        require(imm in 0..4095) { "STRB offset must be 0..4095" }
        emit(0x39000000 or (imm shl 10) or (enc(rn) shl 5) or enc(rt))
    }

    // LDRH Wt, [Xn, #imm] (2-byte aligned, 0..8190)
    fun ldrh(rt: Arm64Register32, rn: Arm64Register64, imm: Int = 0) {
        val scaledImm = imm / 2
        require(imm >= 0 && imm % 2 == 0 && scaledImm <= 4095) { "LDRH offset must be 2-aligned, 0..8190" }
        emit(0x79400000 or (scaledImm shl 10) or (enc(rn) shl 5) or enc(rt))
    }

    // STRH Wt, [Xn, #imm]
    fun strh(rt: Arm64Register32, rn: Arm64Register64, imm: Int = 0) {
        val scaledImm = imm / 2
        require(imm >= 0 && imm % 2 == 0 && scaledImm <= 4095) { "STRH offset must be 2-aligned, 0..8190" }
        emit(0x79000000 or (scaledImm shl 10) or (enc(rn) shl 5) or enc(rt))
    }

    // ── System ──────────────────────────────────────────────────────

    // MRS Xt, <sysreg> — read system register
    // sysreg is encoded as op0:op1:CRn:CRm:op2 (15-bit system register id)
    fun mrs(rd: Arm64Register64, sysRegId: Int) {
        emit(0xD5300000.toInt() or ((sysRegId and 0x7FFF) shl 5) or enc(rd))
    }

    // NOP
    fun nop() = emit(0xD503201F.toInt())

    // BRK #imm16 (breakpoint)
    fun brk(imm: Int = 0) {
        require(imm in 0..0xFFFF) { "BRK immediate must be 0..65535" }
        emit(0xD4200000.toInt() or (imm shl 5))
    }

    // SVC #imm16 (supervisor call)
    fun svc(imm: Int = 0) {
        require(imm in 0..0xFFFF) { "SVC immediate must be 0..65535" }
        emit(0xD4000001.toInt() or (imm shl 5))
    }

    // UDF #imm16 (permanently undefined / trap)
    fun udf(imm: Int = 0) {
        require(imm in 0..0xFFFF) { "UDF immediate must be 0..65535" }
        emit(imm)
    }

    // DMB SY (data memory barrier, full system)
    fun dmb() = emit(0xD5033FBF.toInt())

    // DSB SY (data synchronization barrier, full system)
    fun dsb() = emit(0xD5033F9F.toInt())

    // ISB (instruction synchronization barrier)
    fun isb() = emit(0xD5033FDF.toInt())

    // ── Conditional Select ──────────────────────────────────────────

    // CSEL Xd, Xn, Xm, cond
    fun csel(rd: Arm64Register64, rn: Arm64Register64, rm: Arm64Register64, cond: Arm64Condition) {
        emit(0x9A800000.toInt() or (enc(rm) shl 16) or (cond.code shl 12) or (enc(rn) shl 5) or enc(rd))
    }

    // CSEL Wd, Wn, Wm, cond
    fun csel(rd: Arm64Register32, rn: Arm64Register32, rm: Arm64Register32, cond: Arm64Condition) {
        emit(0x1A800000 or (enc(rm) shl 16) or (cond.code shl 12) or (enc(rn) shl 5) or enc(rd))
    }

    // CSNEG Xd, Xn, Xm, cond
    fun csneg(rd: Arm64Register64, rn: Arm64Register64, rm: Arm64Register64, cond: Arm64Condition) {
        emit(0xDA800400.toInt() or (enc(rm) shl 16) or (cond.code shl 12) or (enc(rn) shl 5) or enc(rd))
    }

    // CSNEG Wd, Wn, Wm, cond
    fun csneg(rd: Arm64Register32, rn: Arm64Register32, rm: Arm64Register32, cond: Arm64Condition) {
        emit(0x5A800400 or (enc(rm) shl 16) or (cond.code shl 12) or (enc(rn) shl 5) or enc(rd))
    }

    // CSINC Xd, Xn, Xm, cond
    fun csinc(rd: Arm64Register64, rn: Arm64Register64, rm: Arm64Register64, cond: Arm64Condition) {
        emit(0x9A800400.toInt() or (enc(rm) shl 16) or (cond.code shl 12) or (enc(rn) shl 5) or enc(rd))
    }

    // ── Extend/Extract ──────────────────────────────────────────────

    // SXTW Xd, Wn (alias: SBFM Xd, Xn, #0, #31)
    fun sxtw(rd: Arm64Register64, rn: Arm64Register32) {
        emit(0x93407C00.toInt() or (enc(rn) shl 5) or enc(rd))
    }

    // SXTH Xd, Wn (alias: SBFM Xd, Xn, #0, #15)
    fun sxth(rd: Arm64Register64, rn: Arm64Register32) {
        emit(0x93403C00.toInt() or (enc(rn) shl 5) or enc(rd))
    }

    // SXTB Xd, Wn (alias: SBFM Xd, Xn, #0, #7)
    fun sxtb(rd: Arm64Register64, rn: Arm64Register32) {
        emit(0x93401C00.toInt() or (enc(rn) shl 5) or enc(rd))
    }

    // UXTB Wd, Wn (alias: UBFM Wd, Wn, #0, #7)
    fun uxtb(rd: Arm64Register32, rn: Arm64Register32) {
        emit(0x53001C00 or (enc(rn) shl 5) or enc(rd))
    }

    // UXTH Wd, Wn (alias: UBFM Wd, Wn, #0, #15)
    fun uxth(rd: Arm64Register32, rn: Arm64Register32) {
        emit(0x53003C00 or (enc(rn) shl 5) or enc(rd))
    }

    // ── Floating Point Arithmetic ─────────────────────────────────

    // FADD Dd, Dn, Dm
    fun fadd(rd: Arm64VecD, rn: Arm64VecD, rm: Arm64VecD) {
        emit(0x1E602800 or (encD(rm) shl 16) or (encD(rn) shl 5) or encD(rd))
    }

    // FADD Sd, Sn, Sm
    fun fadd(rd: Arm64VecS, rn: Arm64VecS, rm: Arm64VecS) {
        emit(0x1E202800 or (encS(rm) shl 16) or (encS(rn) shl 5) or encS(rd))
    }

    // FSUB Dd, Dn, Dm
    fun fsub(rd: Arm64VecD, rn: Arm64VecD, rm: Arm64VecD) {
        emit(0x1E603800 or (encD(rm) shl 16) or (encD(rn) shl 5) or encD(rd))
    }

    // FSUB Sd, Sn, Sm
    fun fsub(rd: Arm64VecS, rn: Arm64VecS, rm: Arm64VecS) {
        emit(0x1E203800 or (encS(rm) shl 16) or (encS(rn) shl 5) or encS(rd))
    }

    // FMUL Dd, Dn, Dm
    fun fmul(rd: Arm64VecD, rn: Arm64VecD, rm: Arm64VecD) {
        emit(0x1E600800 or (encD(rm) shl 16) or (encD(rn) shl 5) or encD(rd))
    }

    // FMUL Sd, Sn, Sm
    fun fmul(rd: Arm64VecS, rn: Arm64VecS, rm: Arm64VecS) {
        emit(0x1E200800 or (encS(rm) shl 16) or (encS(rn) shl 5) or encS(rd))
    }

    // FDIV Dd, Dn, Dm
    fun fdiv(rd: Arm64VecD, rn: Arm64VecD, rm: Arm64VecD) {
        emit(0x1E601800 or (encD(rm) shl 16) or (encD(rn) shl 5) or encD(rd))
    }

    // FDIV Sd, Sn, Sm
    fun fdiv(rd: Arm64VecS, rn: Arm64VecS, rm: Arm64VecS) {
        emit(0x1E201800 or (encS(rm) shl 16) or (encS(rn) shl 5) or encS(rd))
    }

    // FMAX Dd, Dn, Dm
    fun fmax(rd: Arm64VecD, rn: Arm64VecD, rm: Arm64VecD) {
        emit(0x1E604800 or (encD(rm) shl 16) or (encD(rn) shl 5) or encD(rd))
    }

    // FMAX Sd, Sn, Sm
    fun fmax(rd: Arm64VecS, rn: Arm64VecS, rm: Arm64VecS) {
        emit(0x1E204800 or (encS(rm) shl 16) or (encS(rn) shl 5) or encS(rd))
    }

    // FMIN Dd, Dn, Dm
    fun fmin(rd: Arm64VecD, rn: Arm64VecD, rm: Arm64VecD) {
        emit(0x1E605800 or (encD(rm) shl 16) or (encD(rn) shl 5) or encD(rd))
    }

    // FMIN Sd, Sn, Sm
    fun fmin(rd: Arm64VecS, rn: Arm64VecS, rm: Arm64VecS) {
        emit(0x1E205800 or (encS(rm) shl 16) or (encS(rn) shl 5) or encS(rd))
    }

    // FNEG Dd, Dn
    fun fneg(rd: Arm64VecD, rn: Arm64VecD) {
        emit(0x1E614000 or (encD(rn) shl 5) or encD(rd))
    }

    // FNEG Sd, Sn
    fun fneg(rd: Arm64VecS, rn: Arm64VecS) {
        emit(0x1E214000 or (encS(rn) shl 5) or encS(rd))
    }

    // FABS Dd, Dn
    fun fabs(rd: Arm64VecD, rn: Arm64VecD) {
        emit(0x1E60C000 or (encD(rn) shl 5) or encD(rd))
    }

    // FABS Sd, Sn
    fun fabs(rd: Arm64VecS, rn: Arm64VecS) {
        emit(0x1E20C000 or (encS(rn) shl 5) or encS(rd))
    }

    // FSQRT Dd, Dn
    fun fsqrt(rd: Arm64VecD, rn: Arm64VecD) {
        emit(0x1E61C000 or (encD(rn) shl 5) or encD(rd))
    }

    // FSQRT Sd, Sn
    fun fsqrt(rd: Arm64VecS, rn: Arm64VecS) {
        emit(0x1E21C000 or (encS(rn) shl 5) or encS(rd))
    }

    // FRINTM Dd, Dn (round toward minus infinity / floor)
    fun frintm(rd: Arm64VecD, rn: Arm64VecD) {
        emit(0x1E654000 or (encD(rn) shl 5) or encD(rd))
    }

    // FRINTP Dd, Dn (round toward plus infinity / ceil)
    fun frintp(rd: Arm64VecD, rn: Arm64VecD) {
        emit(0x1E64C000 or (encD(rn) shl 5) or encD(rd))
    }

    // FRINTN Dd, Dn (round to nearest even)
    fun frintn(rd: Arm64VecD, rn: Arm64VecD) {
        emit(0x1E644000 or (encD(rn) shl 5) or encD(rd))
    }

    // FRINTZ Dd, Dn (round toward zero / truncate) — double
    fun frintz(rd: Arm64VecD, rn: Arm64VecD) {
        emit(0x1E65C000 or (encD(rn) shl 5) or encD(rd))
    }

    // FRINTZ Sd, Sn (round toward zero / truncate) — single
    fun frintz(rd: Arm64VecS, rn: Arm64VecS) {
        emit(0x1E25C000 or (encS(rn) shl 5) or encS(rd))
    }

    // ── Floating Point Compare ──────────────────────────────────────

    // FCMP Dn, Dm
    fun fcmp(rn: Arm64VecD, rm: Arm64VecD) {
        emit(0x1E602000 or (encD(rm) shl 16) or (encD(rn) shl 5))
    }

    // FCMP Sn, Sm
    fun fcmp(rn: Arm64VecS, rm: Arm64VecS) {
        emit(0x1E202000 or (encS(rm) shl 16) or (encS(rn) shl 5))
    }

    // FCMP Dn, #0.0
    fun fcmpZero(rn: Arm64VecD) {
        emit(0x1E602008 or (encD(rn) shl 5))
    }

    // FCMP Sn, #0.0
    fun fcmpZero(rn: Arm64VecS) {
        emit(0x1E202008 or (encS(rn) shl 5))
    }

    // ── Floating Point Move ─────────────────────────────────────────

    // FMOV Dd, Dn
    fun fmov(rd: Arm64VecD, rn: Arm64VecD) {
        emit(0x1E604000 or (encD(rn) shl 5) or encD(rd))
    }

    // FMOV Sd, Sn
    fun fmov(rd: Arm64VecS, rn: Arm64VecS) {
        emit(0x1E204000 or (encS(rn) shl 5) or encS(rd))
    }

    // FMOV Dd, Xn (GP → FP double)
    fun fmovFromGp64(rd: Arm64VecD, rn: Arm64Register64) {
        emit(0x9E670000.toInt() or (enc(rn) shl 5) or encD(rd))
    }

    // FMOV Sd, Wn (GP → FP single)
    fun fmovFromGp32(rd: Arm64VecS, rn: Arm64Register32) {
        emit(0x1E270000 or (enc(rn) shl 5) or encS(rd))
    }

    // FMOV Xd, Dn (FP → GP double)
    fun fmovToGp64(rd: Arm64Register64, rn: Arm64VecD) {
        emit(0x9E660000.toInt() or (encD(rn) shl 5) or enc(rd))
    }

    // FMOV Wd, Sn (FP → GP single)
    fun fmovToGp32(rd: Arm64Register32, rn: Arm64VecS) {
        emit(0x1E260000 or (encS(rn) shl 5) or enc(rd))
    }

    // ── Floating Point Conversion ───────────────────────────────────

    // SCVTF Dd, Xn (signed i64 → f64)
    fun scvtf(rd: Arm64VecD, rn: Arm64Register64) {
        emit(0x9E620000.toInt() or (enc(rn) shl 5) or encD(rd))
    }

    // SCVTF Dd, Wn (signed i32 → f64)
    fun scvtfWtoD(rd: Arm64VecD, rn: Arm64Register32) {
        emit(0x1E620000 or (enc(rn) shl 5) or encD(rd))
    }

    // SCVTF Sd, Wn (signed i32 → f32)
    fun scvtf(rd: Arm64VecS, rn: Arm64Register32) {
        emit(0x1E220000 or (enc(rn) shl 5) or encS(rd))
    }

    // SCVTF Sd, Xn (signed i64 → f32)
    fun scvtfXtoS(rd: Arm64VecS, rn: Arm64Register64) {
        emit(0x9E220000.toInt() or (enc(rn) shl 5) or encS(rd))
    }

    // UCVTF Dd, Xn (unsigned i64 → f64)
    fun ucvtf(rd: Arm64VecD, rn: Arm64Register64) {
        emit(0x9E630000.toInt() or (enc(rn) shl 5) or encD(rd))
    }

    // UCVTF Sd, Wn (unsigned i32 → f32)
    fun ucvtf(rd: Arm64VecS, rn: Arm64Register32) {
        emit(0x1E230000 or (enc(rn) shl 5) or encS(rd))
    }

    // FCVTZS Xd, Dn (f64 → signed i64, round toward zero)
    fun fcvtzs(rd: Arm64Register64, rn: Arm64VecD) {
        emit(0x9E780000.toInt() or (encD(rn) shl 5) or enc(rd))
    }

    // FCVTZS Wd, Sn (f32 → signed i32, round toward zero)
    fun fcvtzs(rd: Arm64Register32, rn: Arm64VecS) {
        emit(0x1E380000 or (encS(rn) shl 5) or enc(rd))
    }

    // FCVTZS Wd, Dn (f64 → signed i32)
    fun fcvtzsDtoW(rd: Arm64Register32, rn: Arm64VecD) {
        emit(0x1E780000 or (encD(rn) shl 5) or enc(rd))
    }

    // FCVTZU Xd, Dn (f64 → unsigned i64)
    fun fcvtzu(rd: Arm64Register64, rn: Arm64VecD) {
        emit(0x9E790000.toInt() or (encD(rn) shl 5) or enc(rd))
    }

    // FCVTZU Wd, Sn (f32 → unsigned i32)
    fun fcvtzu(rd: Arm64Register32, rn: Arm64VecS) {
        emit(0x1E390000 or (encS(rn) shl 5) or enc(rd))
    }

    // FCVT Dd, Sn (f32 → f64, widen)
    fun fcvtStoD(rd: Arm64VecD, rn: Arm64VecS) {
        emit(0x1E22C000 or (encS(rn) shl 5) or encD(rd))
    }

    // FCVT Sd, Dn (f64 → f32, narrow)
    fun fcvtDtoS(rd: Arm64VecS, rn: Arm64VecD) {
        emit(0x1E624000 or (encD(rn) shl 5) or encS(rd))
    }

    // ── Floating Point Load/Store ───────────────────────────────────

    // LDR Dt, [Xn, #imm] (unsigned offset, 8-byte aligned)
    fun fldr(rt: Arm64VecD, rn: Arm64Register64, imm: Int = 0) {
        val scaledImm = imm / 8
        require(imm >= 0 && imm % 8 == 0 && scaledImm <= 4095) { "FP LDR offset must be 8-aligned, 0..32760" }
        emit(0xFD400000.toInt() or (scaledImm shl 10) or (enc(rn) shl 5) or encD(rt))
    }

    // LDR St, [Xn, #imm] (unsigned offset, 4-byte aligned)
    fun fldr(rt: Arm64VecS, rn: Arm64Register64, imm: Int = 0) {
        val scaledImm = imm / 4
        require(imm >= 0 && imm % 4 == 0 && scaledImm <= 4095) { "FP LDR offset must be 4-aligned, 0..16380" }
        emit(0xBD400000.toInt() or (scaledImm shl 10) or (enc(rn) shl 5) or encS(rt))
    }

    // STR Dt, [Xn, #imm] (unsigned offset, 8-byte aligned)
    fun fstr(rt: Arm64VecD, rn: Arm64Register64, imm: Int = 0) {
        val scaledImm = imm / 8
        require(imm >= 0 && imm % 8 == 0 && scaledImm <= 4095) { "FP STR offset must be 8-aligned, 0..32760" }
        emit(0xFD000000.toInt() or (scaledImm shl 10) or (enc(rn) shl 5) or encD(rt))
    }

    // STR St, [Xn, #imm] (unsigned offset, 4-byte aligned)
    fun fstr(rt: Arm64VecS, rn: Arm64Register64, imm: Int = 0) {
        val scaledImm = imm / 4
        require(imm >= 0 && imm % 4 == 0 && scaledImm <= 4095) { "FP STR offset must be 4-aligned, 0..16380" }
        emit(0xBD000000.toInt() or (scaledImm shl 10) or (enc(rn) shl 5) or encS(rt))
    }

    // LDUR Dt, [Xn, #simm9]
    fun fldur(rt: Arm64VecD, rn: Arm64Register64, imm: Int = 0) {
        require(imm in -256..255) { "FP LDUR offset must be -256..255" }
        emit(0xFC400000.toInt() or ((imm and 0x1FF) shl 12) or (enc(rn) shl 5) or encD(rt))
    }

    // STUR Dt, [Xn, #simm9]
    fun fstur(rt: Arm64VecD, rn: Arm64Register64, imm: Int = 0) {
        require(imm in -256..255) { "FP STUR offset must be -256..255" }
        emit(0xFC000000.toInt() or ((imm and 0x1FF) shl 12) or (enc(rn) shl 5) or encD(rt))
    }

    // STP Dt1, Dt2, [Xn, #imm]! (pre-index, 8-byte aligned)
    fun fstpPre(rt1: Arm64VecD, rt2: Arm64VecD, rn: Arm64Register64, imm: Int) {
        require(imm % 8 == 0 && imm / 8 in -64..63) { "FP STP offset must be 8-aligned, -512..504" }
        val scaledImm = (imm / 8) and 0x7F
        emit(0x6D800000 or (scaledImm shl 15) or (encD(rt2) shl 10) or (enc(rn) shl 5) or encD(rt1))
    }

    // LDP Dt1, Dt2, [Xn], #imm (post-index, 8-byte aligned)
    fun fldpPost(rt1: Arm64VecD, rt2: Arm64VecD, rn: Arm64Register64, imm: Int) {
        require(imm % 8 == 0 && imm / 8 in -64..63) { "FP LDP offset must be 8-aligned, -512..504" }
        val scaledImm = (imm / 8) and 0x7F
        emit(0x6CC00000 or (scaledImm shl 15) or (encD(rt2) shl 10) or (enc(rn) shl 5) or encD(rt1))
    }

    // STP Dt1, Dt2, [Xn, #imm] (signed offset)
    fun fstp(rt1: Arm64VecD, rt2: Arm64VecD, rn: Arm64Register64, imm: Int = 0) {
        require(imm % 8 == 0 && imm / 8 in -64..63) { "FP STP offset must be 8-aligned, -512..504" }
        val scaledImm = (imm / 8) and 0x7F
        emit(0x6D000000 or (scaledImm shl 15) or (encD(rt2) shl 10) or (enc(rn) shl 5) or encD(rt1))
    }

    // LDP Dt1, Dt2, [Xn, #imm] (signed offset)
    fun fldp(rt1: Arm64VecD, rt2: Arm64VecD, rn: Arm64Register64, imm: Int = 0) {
        require(imm % 8 == 0 && imm / 8 in -64..63) { "FP LDP offset must be 8-aligned, -512..504" }
        val scaledImm = (imm / 8) and 0x7F
        emit(0x6D400000 or (scaledImm shl 15) or (encD(rt2) shl 10) or (enc(rn) shl 5) or encD(rt1))
    }

    // ── Floating Point Conditional Select ───────────────────────────

    // FCSEL Dd, Dn, Dm, cond
    fun fcsel(rd: Arm64VecD, rn: Arm64VecD, rm: Arm64VecD, cond: Arm64Condition) {
        emit(0x1E600C00 or (encD(rm) shl 16) or (cond.code shl 12) or (encD(rn) shl 5) or encD(rd))
    }

    // FCSEL Sd, Sn, Sm, cond
    fun fcsel(rd: Arm64VecS, rn: Arm64VecS, rm: Arm64VecS, cond: Arm64Condition) {
        emit(0x1E200C00 or (encS(rm) shl 16) or (cond.code shl 12) or (encS(rn) shl 5) or encS(rd))
    }

    // ── Address Generation ──────────────────────────────────────────

    // ADR Xd, label (21-bit PC-relative, ±1MB)
    fun adr(rd: Arm64Register64, label: String) {
        fixups.add(Fixup(buf.size(), label, FixupKind.ADRP_21))
        emit(0x10000000 or enc(rd))
    }

    // ── NEON (Advanced SIMD) ───────────────────────────────────────

    private fun encQ(reg: Arm64VecQ): Int = (reg as Arm64Register).encoding

    private fun simd3Same(q: Int, u: Int, size: Int, opcode: Int, rd: Int, rn: Int, rm: Int) {
        emit((q shl 30) or (u shl 29) or (0x0E200400 or (size shl 22) or (rm shl 16) or (opcode shl 11) or (rn shl 5) or rd))
    }

    private fun simd2Reg(q: Int, u: Int, size: Int, opcode5: Int, rd: Int, rn: Int) {
        emit((q shl 30) or (u shl 29) or (0x0E200800 or (size shl 22) or (opcode5 shl 12) or (rn shl 5) or rd))
    }

    // ADD Vd.<T>, Vn.<T>, Vm.<T> — integer vector add
    fun addVec(arr: VectorArrangement, rd: Arm64VecQ, rn: Arm64VecQ, rm: Arm64VecQ) {
        simd3Same(arr.q, 0, arr.size, 0b10000, encQ(rd), encQ(rn), encQ(rm))
    }

    // SUB Vd.<T>, Vn.<T>, Vm.<T> — integer vector subtract
    fun subVec(arr: VectorArrangement, rd: Arm64VecQ, rn: Arm64VecQ, rm: Arm64VecQ) {
        simd3Same(arr.q, 1, arr.size, 0b10000, encQ(rd), encQ(rn), encQ(rm))
    }

    // MUL Vd.<T>, Vn.<T>, Vm.<T> — integer vector multiply (8B/16B/4H/8H/2S/4S only)
    fun mulVec(arr: VectorArrangement, rd: Arm64VecQ, rn: Arm64VecQ, rm: Arm64VecQ) {
        require(arr.size != 3) { "MUL vector does not support 64-bit elements (2D)" }
        simd3Same(arr.q, 0, arr.size, 0b10011, encQ(rd), encQ(rn), encQ(rm))
    }

    // AND Vd.<T>, Vn.<T>, Vm.<T> — bitwise AND (only 8B/16B)
    fun andVec(rd: Arm64VecQ, rn: Arm64VecQ, rm: Arm64VecQ, q128: Boolean = true) {
        val qBit = if (q128) 1 else 0
        emit((qBit shl 30) or (0x0E201C00 or (encQ(rm) shl 16) or (encQ(rn) shl 5) or encQ(rd)))
    }

    // ORR Vd.<T>, Vn.<T>, Vm.<T> — bitwise OR (only 8B/16B)
    fun orrVec(rd: Arm64VecQ, rn: Arm64VecQ, rm: Arm64VecQ, q128: Boolean = true) {
        val qBit = if (q128) 1 else 0
        emit((qBit shl 30) or (0x0EA01C00 or (encQ(rm) shl 16) or (encQ(rn) shl 5) or encQ(rd)))
    }

    // EOR Vd.<T>, Vn.<T>, Vm.<T> — bitwise XOR (only 8B/16B)
    fun eorVec(rd: Arm64VecQ, rn: Arm64VecQ, rm: Arm64VecQ, q128: Boolean = true) {
        val qBit = if (q128) 1 else 0
        emit((qBit shl 30) or (0x2E201C00 or (encQ(rm) shl 16) or (encQ(rn) shl 5) or encQ(rd)))
    }

    // NOT Vd.<T>, Vn.<T> — bitwise NOT (MVN alias, only 8B/16B)
    fun notVec(rd: Arm64VecQ, rn: Arm64VecQ, q128: Boolean = true) {
        val qBit = if (q128) 1 else 0
        emit((qBit shl 30) or (0x2E205800 or (encQ(rn) shl 5) or encQ(rd)))
    }

    // CMEQ Vd.<T>, Vn.<T>, Vm.<T> — compare equal
    fun cmeq(arr: VectorArrangement, rd: Arm64VecQ, rn: Arm64VecQ, rm: Arm64VecQ) {
        simd3Same(arr.q, 1, arr.size, 0b10001, encQ(rd), encQ(rn), encQ(rm))
    }

    // CMGT Vd.<T>, Vn.<T>, Vm.<T> — compare signed greater than
    fun cmgt(arr: VectorArrangement, rd: Arm64VecQ, rn: Arm64VecQ, rm: Arm64VecQ) {
        simd3Same(arr.q, 0, arr.size, 0b00110, encQ(rd), encQ(rn), encQ(rm))
    }

    // CMGE Vd.<T>, Vn.<T>, Vm.<T> — compare signed greater than or equal
    fun cmge(arr: VectorArrangement, rd: Arm64VecQ, rn: Arm64VecQ, rm: Arm64VecQ) {
        simd3Same(arr.q, 0, arr.size, 0b00111, encQ(rd), encQ(rn), encQ(rm))
    }

    // CMHI Vd.<T>, Vn.<T>, Vm.<T> — compare unsigned higher
    fun cmhi(arr: VectorArrangement, rd: Arm64VecQ, rn: Arm64VecQ, rm: Arm64VecQ) {
        simd3Same(arr.q, 1, arr.size, 0b00110, encQ(rd), encQ(rn), encQ(rm))
    }

    // CMHS Vd.<T>, Vn.<T>, Vm.<T> — compare unsigned higher or same
    fun cmhs(arr: VectorArrangement, rd: Arm64VecQ, rn: Arm64VecQ, rm: Arm64VecQ) {
        simd3Same(arr.q, 1, arr.size, 0b00111, encQ(rd), encQ(rn), encQ(rm))
    }

    // SHL Vd.<T>, Vn.<T>, #shift — shift left immediate
    fun shlVec(arr: VectorArrangement, rd: Arm64VecQ, rn: Arm64VecQ, shift: Int) {
        val immh: Int
        val immb: Int
        when (arr.elementBits) {
            8 -> { require(shift in 0..7); immh = 0b0001; immb = shift }
            16 -> { require(shift in 0..15); immh = 0b0010 or (shift shr 3); immb = shift and 0x7 }
            32 -> { require(shift in 0..31); immh = 0b0100 or (shift shr 3); immb = shift and 0x7 }
            64 -> { require(shift in 0..63); immh = 0b1000 or (shift shr 3); immb = shift and 0x7 }
            else -> error("Invalid element size")
        }
        emit((arr.q shl 30) or (0x0F005400 or (immh shl 19) or (immb shl 16) or (encQ(rn) shl 5) or encQ(rd)))
    }

    // SSHR Vd.<T>, Vn.<T>, #shift — signed shift right immediate
    fun sshr(arr: VectorArrangement, rd: Arm64VecQ, rn: Arm64VecQ, shift: Int) {
        val immh: Int
        val immb: Int
        when (arr.elementBits) {
            8 -> { require(shift in 1..8); val v = 16 - shift; immh = 0b0001; immb = v and 0x7 }
            16 -> { require(shift in 1..16); val v = 32 - shift; immh = 0b0010 or ((v shr 3) and 1); immb = v and 0x7 }
            32 -> { require(shift in 1..32); val v = 64 - shift; immh = 0b0100 or ((v shr 3) and 3); immb = v and 0x7 }
            64 -> { require(shift in 1..64); val v = 128 - shift; immh = 0b1000 or ((v shr 3) and 7); immb = v and 0x7 }
            else -> error("Invalid element size")
        }
        emit((arr.q shl 30) or (0x0F000400 or (immh shl 19) or (immb shl 16) or (encQ(rn) shl 5) or encQ(rd)))
    }

    // USHR Vd.<T>, Vn.<T>, #shift — unsigned shift right immediate
    fun ushr(arr: VectorArrangement, rd: Arm64VecQ, rn: Arm64VecQ, shift: Int) {
        val immh: Int
        val immb: Int
        when (arr.elementBits) {
            8 -> { require(shift in 1..8); val v = 16 - shift; immh = 0b0001; immb = v and 0x7 }
            16 -> { require(shift in 1..16); val v = 32 - shift; immh = 0b0010 or ((v shr 3) and 1); immb = v and 0x7 }
            32 -> { require(shift in 1..32); val v = 64 - shift; immh = 0b0100 or ((v shr 3) and 3); immb = v and 0x7 }
            64 -> { require(shift in 1..64); val v = 128 - shift; immh = 0b1000 or ((v shr 3) and 7); immb = v and 0x7 }
            else -> error("Invalid element size")
        }
        emit((arr.q shl 30) or (0x2F000400 or (immh shl 19) or (immb shl 16) or (encQ(rn) shl 5) or encQ(rd)))
    }

    // FADD Vd.<T>, Vn.<T>, Vm.<T> — floating-point vector add (2S/4S/2D)
    fun faddVec(arr: VectorArrangement, rd: Arm64VecQ, rn: Arm64VecQ, rm: Arm64VecQ) {
        require(arr.elementBits >= 32) { "FADD vector requires 32 or 64-bit elements" }
        val sz = if (arr.elementBits == 64) 1 else 0
        emit((arr.q shl 30) or (0x0E20D400 or (sz shl 22) or (encQ(rm) shl 16) or (encQ(rn) shl 5) or encQ(rd)))
    }

    // FSUB Vd.<T>, Vn.<T>, Vm.<T> — floating-point vector subtract (2S/4S/2D)
    fun fsubVec(arr: VectorArrangement, rd: Arm64VecQ, rn: Arm64VecQ, rm: Arm64VecQ) {
        require(arr.elementBits >= 32) { "FSUB vector requires 32 or 64-bit elements" }
        val sz = if (arr.elementBits == 64) 1 else 0
        emit((arr.q shl 30) or (0x0EA0D400.toInt() or (sz shl 22) or (encQ(rm) shl 16) or (encQ(rn) shl 5) or encQ(rd)))
    }

    // FMUL Vd.<T>, Vn.<T>, Vm.<T> — floating-point vector multiply (2S/4S/2D)
    fun fmulVec(arr: VectorArrangement, rd: Arm64VecQ, rn: Arm64VecQ, rm: Arm64VecQ) {
        require(arr.elementBits >= 32) { "FMUL vector requires 32 or 64-bit elements" }
        val sz = if (arr.elementBits == 64) 1 else 0
        emit((arr.q shl 30) or (0x2E20DC00 or (sz shl 22) or (encQ(rm) shl 16) or (encQ(rn) shl 5) or encQ(rd)))
    }

    // FDIV Vd.<T>, Vn.<T>, Vm.<T> — floating-point vector divide (2S/4S/2D)
    fun fdivVec(arr: VectorArrangement, rd: Arm64VecQ, rn: Arm64VecQ, rm: Arm64VecQ) {
        require(arr.elementBits >= 32) { "FDIV vector requires 32 or 64-bit elements" }
        val sz = if (arr.elementBits == 64) 1 else 0
        emit((arr.q shl 30) or (0x2E20FC00 or (sz shl 22) or (encQ(rm) shl 16) or (encQ(rn) shl 5) or encQ(rd)))
    }

    // FNEG Vd.<T>, Vn.<T> — floating-point vector negate (2S/4S/2D)
    fun fnegVec(arr: VectorArrangement, rd: Arm64VecQ, rn: Arm64VecQ) {
        require(arr.elementBits >= 32) { "FNEG vector requires 32 or 64-bit elements" }
        val sz = if (arr.elementBits == 64) 1 else 0
        emit((arr.q shl 30) or (0x2EA0F800.toInt() or (sz shl 22) or (encQ(rn) shl 5) or encQ(rd)))
    }

    // FABS Vd.<T>, Vn.<T> — floating-point vector absolute value (2S/4S/2D)
    fun fabsVec(arr: VectorArrangement, rd: Arm64VecQ, rn: Arm64VecQ) {
        require(arr.elementBits >= 32) { "FABS vector requires 32 or 64-bit elements" }
        val sz = if (arr.elementBits == 64) 1 else 0
        emit((arr.q shl 30) or (0x0EA0F800 or (sz shl 22) or (encQ(rn) shl 5) or encQ(rd)))
    }

    // DUP Vd.<T>, Xn — duplicate GP register to all lanes
    fun dupFromGp(arr: VectorArrangement, rd: Arm64VecQ, rn: Arm64Register64) {
        val imm5 = when (arr.elementBits) {
            8 -> 0b00001; 16 -> 0b00010; 32 -> 0b00100; 64 -> 0b01000
            else -> error("Invalid element size")
        }
        emit((arr.q shl 30) or (0x0E000C00 or (imm5 shl 16) or (enc(rn) shl 5) or encQ(rd)))
    }

    // DUP Vd.<T>, Vn.<T>[index] — duplicate element to all lanes
    fun dupElement(arr: VectorArrangement, rd: Arm64VecQ, rn: Arm64VecQ, index: Int) {
        val imm5 = when (arr.elementBits) {
            8 -> (index shl 1) or 0b00001
            16 -> (index shl 2) or 0b00010
            32 -> (index shl 3) or 0b00100
            64 -> (index shl 4) or 0b01000
            else -> error("Invalid element size")
        }
        emit((arr.q shl 30) or (0x0E000400 or (imm5 shl 16) or (encQ(rn) shl 5) or encQ(rd)))
    }

    // CNT Vd.<T>, Vn.<T> — count set bits per byte (8B/16B only)
    fun cnt(rd: Arm64VecQ, rn: Arm64VecQ, q128: Boolean = false) {
        val qBit = if (q128) 1 else 0
        simd2Reg(qBit, 0, 0, 0b00101, encQ(rd), encQ(rn))
    }

    // UMOV Wd, Vn.B[index] — unsigned move byte lane to GP 32-bit register
    fun umovB(rd: Arm64Register32, rn: Arm64VecQ, index: Int) {
        val imm5 = (index shl 1) or 1
        emit(0x0E003C00 or (imm5 shl 16) or (encQ(rn) shl 5) or enc(rd))
    }

    // UMOV Xd, Vn.D[index] — unsigned move 64-bit lane to GP 64-bit register
    fun umovD(rd: Arm64Register64, rn: Arm64VecQ, index: Int) {
        val imm5 = (index shl 4) or 0b01000
        emit((1 shl 30) or (0x0E003C00 or (imm5 shl 16) or (encQ(rn) shl 5) or enc(rd)))
    }

    // ADDV Vd, Vn.<T> — add across vector (result is scalar, 8B/16B/4H/8H/4S only)
    fun addv(arr: VectorArrangement, rd: Arm64VecQ, rn: Arm64VecQ) {
        require(arr.size != 3) { "ADDV does not support 64-bit elements" }
        emit((arr.q shl 30) or (0x0E31B800 or (arr.size shl 22) or (encQ(rn) shl 5) or encQ(rd)))
    }

    // LDR Qt, [Xn, #imm] (unsigned offset, 16-byte aligned, 0..65520)
    fun ldrQ(rt: Arm64VecQ, rn: Arm64Register64, imm: Int = 0) {
        val scaledImm = imm / 16
        require(imm >= 0 && imm % 16 == 0 && scaledImm <= 4095) { "LDR Q offset must be 16-aligned, 0..65520" }
        emit(0x3DC00000 or (scaledImm shl 10) or (enc(rn) shl 5) or encQ(rt))
    }

    // STR Qt, [Xn, #imm] (unsigned offset, 16-byte aligned, 0..65520)
    fun strQ(rt: Arm64VecQ, rn: Arm64Register64, imm: Int = 0) {
        val scaledImm = imm / 16
        require(imm >= 0 && imm % 16 == 0 && scaledImm <= 4095) { "STR Q offset must be 16-aligned, 0..65520" }
        emit(0x3D800000 or (scaledImm shl 10) or (enc(rn) shl 5) or encQ(rt))
    }

    // MOVI Vd.<T>, #imm8 — move immediate to vector (8-bit value replicated)
    fun moviVec(rd: Arm64VecQ, imm8: Int, q128: Boolean = true) {
        require(imm8 in 0..255) { "MOVI immediate must be 0..255" }
        val qBit = if (q128) 1 else 0
        val abc = (imm8 shr 5) and 0x7
        val defgh = imm8 and 0x1F
        emit((qBit shl 30) or (0x0F000400 or (abc shl 16) or (defgh shl 5) or encQ(rd)))
    }

    // ── Raw Emit ────────────────────────────────────────────────────

    fun emitRaw(inst: Int) = emit(inst)

    fun emitBytes(data: ByteArray) = buf.write(data)
}
