package org.kgen.target.arm64.disasm

import org.kgen.reflect.Instruction

data class Arm64Instruction(
    override val address: Long,
    override val mnemonic: String,
    val operandsStr: String,
    val rawBytes: Int,
) : Instruction {
    override val bytes: ByteArray get() = byteArrayOf(
        (rawBytes and 0xFF).toByte(),
        ((rawBytes shr 8) and 0xFF).toByte(),
        ((rawBytes shr 16) and 0xFF).toByte(),
        ((rawBytes shr 24) and 0xFF).toByte(),
    )
    override val size: Int get() = 4

    override fun operandsText(): String = operandsStr
    override fun text(): String = if (operandsStr.isEmpty()) mnemonic else "$mnemonic $operandsStr"

    override fun toString(): String = text()
}
