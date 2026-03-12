package org.kgen.jit

import org.kgen.reflect.NativeMemory

/**
 * A compiled runtime stub — executable code at a known address.
 * Must be closed when no longer needed.
 */
class RuntimeStub(
    val name: String,
    private val memory: NativeMemory,
) : AutoCloseable {
    /** The entry point address of this stub. */
    fun address(): Long = memory.address

    /** The size of the stub in bytes. */
    fun size(): Long = memory.size

    override fun close() = memory.close()
}
