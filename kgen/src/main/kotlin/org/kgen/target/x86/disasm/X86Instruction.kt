package org.kgen.target.x86.disasm

import org.kgen.reflect.Instruction

/**
 * A decoded x86-64 instruction.
 */
data class X86Instruction(
    override val address: Long,
    override val bytes: ByteArray,
    override val mnemonic: String,
    val operands: List<X86Operand>,
) : Instruction {
    override val size: Int get() = bytes.size

    override fun operandsText(): String = operands.joinToString(", ") { it.text() }

    override fun text(): String = intelText()

    fun text(att: Boolean = false): String = if (att) attText() else intelText()

    fun intelText(): String = buildString {
        append(mnemonic)
        if (operands.isNotEmpty()) {
            append(" ")
            append(operands.joinToString(", ") { it.text() })
        }
    }

    fun attText(): String = buildString {
        append(attMnemonic())
        if (operands.isNotEmpty()) {
            append(" ")
            append(operands.reversed().joinToString(", ") { it.attText() })
        }
    }

    private fun attMnemonic(): String {
        if (operands.isEmpty()) return mnemonic
        val suffix = when {
            mnemonic.startsWith("j") || mnemonic.startsWith("call") || mnemonic.startsWith("ret") -> return mnemonic
            mnemonic.startsWith("set") || mnemonic.startsWith("cmov") -> return mnemonic
            mnemonic == "lea" || mnemonic == "nop" || mnemonic == "push" || mnemonic == "pop" -> return mnemonic
            mnemonic.endsWith("sd") || mnemonic.endsWith("ss") || mnemonic.endsWith("pd") || mnemonic.endsWith("ps") -> return mnemonic
            mnemonic.startsWith("v") -> return mnemonic
            mnemonic.startsWith("movsx") || mnemonic.startsWith("movzx") -> return mnemonic
            else -> {
                val bits = operands.firstNotNullOfOrNull { it.operandBits() } ?: return mnemonic
                when (bits) {
                    8 -> "b"; 16 -> "w"; 32 -> "l"; 64 -> "q"; 128 -> "" ; else -> ""
                }
            }
        }
        return "$mnemonic$suffix"
    }

    override fun toString(): String = "0x${address.toString(16).padStart(8, '0')}:  ${intelText()}"

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
    fun attText(): String
    fun operandBits(): Int?

    data class Register(val name: String) : X86Operand {
        override fun text(): String = name
        override fun attText(): String = "%$name"
        override fun operandBits(): Int? = when {
            name.startsWith("r") && name.length <= 3 && name[1].isDigit() -> 64
            name.startsWith("r") && name.endsWith("d") -> 32
            name.startsWith("r") && name.endsWith("w") -> 16
            name.startsWith("r") && name.endsWith("b") -> 8
            name.startsWith("e") -> 32
            name.length == 3 && name.endsWith("x") -> when (name[0]) {
                'r' -> 64; else -> 16
            }
            name.length == 2 && (name.endsWith("l") || name.endsWith("h")) -> 8
            name.startsWith("xmm") -> 128
            name.startsWith("ymm") -> 256
            name.startsWith("zmm") -> 512
            name == "rax" || name == "rbx" || name == "rcx" || name == "rdx" ||
            name == "rsi" || name == "rdi" || name == "rbp" || name == "rsp" -> 64
            else -> null
        }
    }

    data class Immediate(val value: Long, val bits: Int) : X86Operand {
        override fun text(): String = if (value < 0) "-0x${(-value).toString(16)}" else "0x${value.toString(16)}"
        override fun attText(): String = "\$${text()}"
        override fun operandBits(): Int? = null
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

        override fun attText(): String = buildString {
            if (segment != null) append("%$segment:")
            if (displacement != 0L) {
                if (displacement < 0) append("-0x${(-displacement).toString(16)}")
                else append("0x${displacement.toString(16)}")
            }
            append("(")
            if (ripRelative) {
                append("%rip")
            } else {
                if (base != null) append("%$base")
                if (index != null) {
                    append(",%$index")
                    if (scale > 1) append(",$scale")
                }
            }
            append(")")
        }

        override fun operandBits(): Int? = size

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
        override fun attText(): String = "0x${target.toString(16)}"
        override fun operandBits(): Int? = null
    }
}
