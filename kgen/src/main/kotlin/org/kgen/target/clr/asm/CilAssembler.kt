package org.kgen.target.clr.asm

import org.kgen.target.clr.CilSigType
import org.kgen.target.clr.generated.CilAssemblerOps

/**
 * CIL bytecode assembler. Emits CIL instructions into a byte buffer
 * with strongly-typed label, local variable, and token support.
 *
 * ```java
 * var asm = new CilAssembler();
 * CilLocal x = asm.declareLocal(CilSigType.I4);
 * CilLabel done = asm.defineLabel();
 *
 * asm.ldarg(0);
 * asm.brtrue(done);
 * asm.ldcI4Auto(42);
 * asm.stloc(x);
 * asm.markLabel(done);
 * asm.ldloc(x);
 * asm.ret();
 * byte[] code = asm.toByteArray();
 * ```
 */
class CilAssembler : CilAssemblerOps() {

    private val buffer = mutableListOf<Byte>()
    private val labelPatches = mutableListOf<BranchPatch>()
    private val declaredLocals = mutableListOf<CilLocal>()
    private var nextLabelId = 0

    private data class BranchPatch(
        val offset: Int,
        val label: CilLabel,
        val base: Int,
        val shortBranch: Boolean,
    )

    val size: Int get() = buffer.size

    /** The list of declared locals (for building method headers). */
    val locals: List<CilLocal> get() = declaredLocals

    /** Number of declared locals. */
    val localCount: Int get() = declaredLocals.size

    // ── Labels ──

    /** Define a new label. The label must be placed later with [markLabel]. */
    fun defineLabel(): CilLabel = CilLabel(nextLabelId++)

    /** Mark a label at the current position. */
    fun markLabel(label: CilLabel) {
        require(!label.isMarked) { "Label ${label.id} already marked" }
        label.offset = buffer.size
    }

    /** Allocate a label as a forward reference (alias for [defineLabel]). */
    fun label(): CilLabel = defineLabel()

    /** Allocate a label and mark it at the current position. */
    fun mark(): CilLabel {
        val label = defineLabel()
        markLabel(label)
        return label
    }

    /** Mark an existing label at the current position (alias for [markLabel]). */
    fun mark(label: CilLabel) = markLabel(label)

    // ── Locals ──

    /** Declare a local variable and return a handle for it. */
    fun declareLocal(type: CilSigType): CilLocal {
        val local = CilLocal(declaredLocals.size, type)
        declaredLocals.add(local)
        return local
    }

    // ── Abstract method implementations ──

    override fun emitByte(v: Int) {
        buffer.add(v.toByte())
    }

    override fun emitU16(v: Int) {
        buffer.add((v and 0xFF).toByte())
        buffer.add(((v shr 8) and 0xFF).toByte())
    }

    override fun emitI32(v: Int) {
        buffer.add((v and 0xFF).toByte())
        buffer.add(((v shr 8) and 0xFF).toByte())
        buffer.add(((v shr 16) and 0xFF).toByte())
        buffer.add(((v shr 24) and 0xFF).toByte())
    }

    override fun emitI64(v: Long) {
        for (i in 0 until 8) buffer.add(((v shr (i * 8)) and 0xFF).toByte())
    }

    override fun emitF32(v: Float) = emitI32(v.toBits())
    override fun emitF64(v: Double) = emitI64(v.toBits())
    override fun emitToken(token: Int) = emitI32(token)

    override fun emitBranch(opcode: Int, twoBytePrefix: Boolean, label: CilLabel) {
        if (twoBytePrefix) emitByte(0xFE)
        val opcByte = if (twoBytePrefix) opcode and 0xFF else opcode
        emitByte(opcByte)
        // Determine short vs long from the opcode
        val shortBranch = isShortBranch(opcode, twoBytePrefix)
        labelPatches.add(BranchPatch(buffer.size, label, buffer.size - 1, shortBranch))
        if (shortBranch) emitByte(0) else emitI32(0)
    }

    override fun switch_(labels: List<CilLabel>) {
        emitByte(0x45)
        val base = buffer.size
        emitI32(labels.size)
        for (label in labels) {
            labelPatches.add(BranchPatch(buffer.size, label, base - 1, shortBranch = false))
            emitI32(0)
        }
    }

    // ── Smart encoding convenience methods ──

    /** Load argument with optimal encoding (short form 0-3, short index 4-255, wide). */
    fun ldarg(index: Int) {
        when (index) {
            0 -> ldarg0()
            1 -> ldarg1()
            2 -> ldarg2()
            3 -> ldarg3()
            in 4..255 -> ldargS(index)
            else -> ldargW(index)
        }
    }

    /** Load address of argument with optimal encoding. */
    fun ldarga(index: Int) {
        if (index <= 255) ldargaS(index)
        else ldargaW(index)
    }

    /** Store to argument with optimal encoding. */
    fun starg(index: Int) {
        if (index <= 255) stargS(index)
        else stargW(index)
    }

    /** Load local variable (typed). */
    fun ldloc(local: CilLocal) = ldloc(local.index)

    /** Load address of local variable (typed). */
    fun ldloca(local: CilLocal) = ldloca(local.index)

    /** Store to local variable (typed). */
    fun stloc(local: CilLocal) = stloc(local.index)

    /** Load local variable with optimal encoding. */
    fun ldloc(index: Int) {
        when (index) {
            0 -> ldloc0()
            1 -> ldloc1()
            2 -> ldloc2()
            3 -> ldloc3()
            in 4..255 -> ldlocS(index)
            else -> ldlocW(index)
        }
    }

    /** Load address of local variable with optimal encoding. */
    fun ldloca(index: Int) {
        if (index <= 255) ldlocaS(index)
        else ldlocaW(index)
    }

    /** Store to local variable with optimal encoding. */
    fun stloc(index: Int) {
        when (index) {
            0 -> stloc0()
            1 -> stloc1()
            2 -> stloc2()
            3 -> stloc3()
            in 4..255 -> stlocS(index)
            else -> stlocW(index)
        }
    }

    /** Emit optimal ldc.i4 for the given value. */
    fun ldcI4Auto(v: Int) {
        when (v) {
            -1 -> ldcI4M1()
            0 -> ldcI4_0()
            1 -> ldcI4_1()
            2 -> ldcI4_2()
            3 -> ldcI4_3()
            4 -> ldcI4_4()
            5 -> ldcI4_5()
            6 -> ldcI4_6()
            7 -> ldcI4_7()
            8 -> ldcI4_8()
            in -128..127 -> ldcI4S(v)
            else -> ldcI4(v)
        }
    }

    // ── Finalization ──

    /** Finalize and return the assembled bytecode. Resolves all branch targets. */
    fun toByteArray(): ByteArray {
        val result = buffer.toByteArray()
        for (patch in labelPatches) {
            require(patch.label.isMarked) { "Unresolved label: ${patch.label}" }
            val target = patch.label.offset
            val relative = target - (patch.offset + if (patch.shortBranch) 1 else 4)
            if (patch.shortBranch) {
                result[patch.offset] = relative.toByte()
            } else {
                writeI32(result, patch.offset, relative)
            }
        }
        return result
    }

    // ── Helpers ──

    private fun isShortBranch(opcode: Int, twoBytePrefix: Boolean): Boolean {
        if (twoBytePrefix) return false
        // Short branches: br.s (0x2B) through blt.un.s (0x37), leave.s (0xDE)
        return opcode in 0x2B..0x37 || opcode == 0xDE
    }

    private fun writeI32(buf: ByteArray, offset: Int, value: Int) {
        buf[offset] = (value and 0xFF).toByte()
        buf[offset + 1] = ((value shr 8) and 0xFF).toByte()
        buf[offset + 2] = ((value shr 16) and 0xFF).toByte()
        buf[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }
}
