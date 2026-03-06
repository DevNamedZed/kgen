package org.kgen.x86.disasm

/**
 * A decoded x86-64 instruction.
 */
data class X86Instruction(
    val address: Long,
    val bytes: ByteArray,
    val mnemonic: String,
    val operands: List<X86Operand>,
) {
    val size: Int get() = bytes.size

    fun text(): String = buildString {
        append(mnemonic)
        if (operands.isNotEmpty()) {
            append(" ")
            append(operands.joinToString(", ") { it.text() })
        }
    }

    override fun toString(): String = "0x${address.toString(16).padStart(8, '0')}:  ${text()}"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is X86Instruction) return false
        return address == other.address && bytes.contentEquals(other.bytes) && mnemonic == other.mnemonic && operands == other.operands
    }

    override fun hashCode(): Int {
        var result = address.hashCode()
        result = 31 * result + bytes.contentHashCode()
        result = 31 * result + mnemonic.hashCode()
        result = 31 * result + operands.hashCode()
        return result
    }
}

sealed interface X86Operand {
    fun text(): String

    data class Register(val name: String) : X86Operand {
        override fun text(): String = name
    }

    data class Immediate(val value: Long, val bits: Int) : X86Operand {
        override fun text(): String = if (value < 0) "-0x${(-value).toString(16)}" else "0x${value.toString(16)}"
    }

    data class Memory(
        val size: Int,
        val base: String?,
        val index: String?,
        val scale: Int,
        val displacement: Long,
        val segment: String? = null,
        val ripRelative: Boolean = false,
    ) : X86Operand {
        override fun text(): String = buildString {
            append(sizePrefix())
            if (segment != null) append("$segment:")
            append("[")
            if (ripRelative) {
                append("rip")
                if (displacement != 0L) {
                    if (displacement > 0) append("+0x${displacement.toString(16)}")
                    else append("-0x${(-displacement).toString(16)}")
                }
            } else {
                var needPlus = false
                if (base != null) { append(base); needPlus = true }
                if (index != null) {
                    if (needPlus) append("+")
                    append(index)
                    if (scale > 1) append("*$scale")
                    needPlus = true
                }
                if (displacement != 0L || (!needPlus && base == null && index == null)) {
                    if (needPlus && displacement > 0) append("+")
                    if (displacement < 0) append("-0x${(-displacement).toString(16)}")
                    else append("0x${displacement.toString(16)}")
                }
            }
            append("]")
        }

        private fun sizePrefix(): String = when (size) {
            8 -> "byte ptr "
            16 -> "word ptr "
            32 -> "dword ptr "
            64 -> "qword ptr "
            128 -> "xmmword ptr "
            else -> ""
        }
    }

    data class Relative(val target: Long) : X86Operand {
        override fun text(): String = "0x${target.toString(16)}"
    }
}
