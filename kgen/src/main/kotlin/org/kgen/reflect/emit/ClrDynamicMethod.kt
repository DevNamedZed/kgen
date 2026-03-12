package org.kgen.reflect.emit

import org.kgen.reflect.Signature
import org.kgen.target.clr.asm.CilAssembler

/**
 * A standalone CLR method (CIL bytecode).
 *
 * CIL methods cannot be executed directly on the JVM. Use [toBytes] to get
 * the CIL bytecode for embedding in a .NET assembly, or use [il] to emit
 * instructions for serialization.
 */
class ClrDynamicMethod(name: String, signature: Signature) : DynamicMethod(name, signature) {
    private val asm = CilAssembler()

    fun il(): CilAssembler = asm

    fun toBytes(): ByteArray = asm.toByteArray()

    override fun invoke(vararg args: Any?): Any? {
        throw UnsupportedOperationException("CLR dynamic methods cannot be invoked on the JVM. Use toBytes() to get CIL bytecode.")
    }
}
