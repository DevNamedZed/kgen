package org.kgen.target.jvm.asm

import org.kgen.target.jvm.JvmOpCode
import org.kgen.target.jvm.generated.JvmAssemblerOps

/**
 * JVM bytecode assembler. Emits bytecode instructions into a byte buffer
 * with label support for branch targets.
 *
 * ```java
 * var asm = new JvmAssembler();
 * asm.iload(0);
 * asm.iload(1);
 * asm.iadd();
 * asm.ireturn();
 * byte[] code = asm.toByteArray();
 * ```
 */
class JvmAssembler : JvmAssemblerOps() {

    private val buffer = mutableListOf<Byte>()
    private val labels = mutableMapOf<String, Int>()
    private val patches = mutableListOf<BranchPatch>()

    private data class BranchPatch(
        val offset: Int,    // offset of the branch operand in the buffer
        val label: String,  // target label
        val base: Int,      // offset of the opcode (for relative calculation)
        val wide: Boolean,  // true for goto_w (4-byte offset)
    )

    /** Current bytecode offset. */
    val size: Int get() = buffer.size

    /** Define a label at the current offset. */
    fun label(name: String) {
        labels[name] = buffer.size
    }

    /** Get the bytecode offset for a label, or null if undefined. */
    fun labelOffset(name: String): Int? = labels[name]

    /** Emit raw bytecode. */
    fun emit(vararg bytes: Int) {
        for (b in bytes) buffer.add(b.toByte())
    }

    // ── Abstract method implementations ──

    override fun emitByte(b: Int) {
        buffer.add(b.toByte())
    }

    override fun emitShort(v: Int) {
        buffer.add((v shr 8).toByte())
        buffer.add(v.toByte())
    }

    override fun emitBranch(opcode: Int, label: String) {
        val base = buffer.size
        emit(opcode)
        val patchOffset = buffer.size
        emitShort(0) // placeholder
        patches.add(BranchPatch(patchOffset, label, base, wide = false))
    }

    // ── Overrides with smart encoding ──

    /** Push constant pool entry, automatically switching to ldc_w for wide indices. */
    override fun ldc(index: Int) {
        if (index <= 255) {
            emit(JvmOpCode.LDC.code, index)
        } else {
            emit(JvmOpCode.LDC_W.code)
            emitShort(index)
        }
    }

    /** Increment local variable, with wide encoding for large index/increment. */
    override fun iinc(index: Int, increment: Int) {
        if (index <= 255 && increment in -128..127) {
            emit(JvmOpCode.IINC.code, index, increment and 0xFF)
        } else {
            emit(JvmOpCode.WIDE.code, JvmOpCode.IINC.code)
            emitShort(index)
            emitShort(increment)
        }
    }

    override fun iload(index: Int) = emitLocalAccess(JvmOpCode.ILOAD.code, index, 0x1A)
    override fun lload(index: Int) = emitLocalAccess(JvmOpCode.LLOAD.code, index, 0x1E)
    override fun fload(index: Int) = emitLocalAccess(JvmOpCode.FLOAD.code, index, 0x22)
    override fun dload(index: Int) = emitLocalAccess(JvmOpCode.DLOAD.code, index, 0x26)
    override fun aload(index: Int) = emitLocalAccess(JvmOpCode.ALOAD.code, index, 0x2A)
    override fun istore(index: Int) = emitLocalAccess(JvmOpCode.ISTORE.code, index, 0x3B)
    override fun lstore(index: Int) = emitLocalAccess(JvmOpCode.LSTORE.code, index, 0x3F)
    override fun fstore(index: Int) = emitLocalAccess(JvmOpCode.FSTORE.code, index, 0x43)
    override fun dstore(index: Int) = emitLocalAccess(JvmOpCode.DSTORE.code, index, 0x47)
    override fun astore(index: Int) = emitLocalAccess(JvmOpCode.ASTORE.code, index, 0x4B)

    // ── Convenience methods ──

    /** Push int constant, choosing the most compact encoding. */
    fun pushInt(value: Int) {
        when (value) {
            -1 -> iconstM1()
            0 -> iconst0()
            1 -> iconst1()
            2 -> iconst2()
            3 -> iconst3()
            4 -> iconst4()
            5 -> iconst5()
            in -128..127 -> bipush(value)
            in -32768..32767 -> sipush(value)
            else -> error("Use ldc for large int constants: $value")
        }
    }

    // ── Output ──

    /** Finalize and return the assembled bytecode. Resolves all branch targets. */
    fun toByteArray(): ByteArray {
        val result = buffer.toByteArray()
        for (patch in patches) {
            val targetOffset = labels[patch.label]
                ?: error("Undefined label: ${patch.label}")
            val delta = targetOffset - patch.base
            if (patch.wide) {
                result[patch.offset] = (delta shr 24).toByte()
                result[patch.offset + 1] = (delta shr 16).toByte()
                result[patch.offset + 2] = (delta shr 8).toByte()
                result[patch.offset + 3] = delta.toByte()
            } else {
                result[patch.offset] = (delta shr 8).toByte()
                result[patch.offset + 1] = delta.toByte()
            }
        }
        return result
    }

    /** Reset the assembler for reuse. */
    fun reset() {
        buffer.clear()
        labels.clear()
        patches.clear()
    }

    // ── Internal helpers ──

    private fun emitLocalAccess(opcode: Int, index: Int, shortBase: Int) {
        when {
            index in 0..3 -> emit(shortBase + index)
            index <= 255 -> emit(opcode, index)
            else -> {
                emit(JvmOpCode.WIDE.code, opcode)
                emitShort(index)
            }
        }
    }
}
