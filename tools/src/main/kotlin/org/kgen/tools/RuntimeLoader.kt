package org.kgen.tools

// Runtime code loading — JIT-style: generate code and execute it

interface RuntimeLoader {

    fun canLoadFormat(format: String): Boolean

    fun load(name: String, bytes: ByteArray): LoadedModule

    interface LoadedModule : AutoCloseable {

        val name: String

        fun findSymbol(name: String): Long?

        fun findFunction(name: String): NativeFunction?

        fun symbols(): List<String>

        override fun close()
    }

    interface NativeFunction {
        val name: String
        val address: Long

        fun callInt(vararg args: Long): Int
        fun callLong(vararg args: Long): Long
        fun callFloat(vararg args: Long): Float
        fun callDouble(vararg args: Long): Double
        fun callVoid(vararg args: Long)
    }
}

// JVM-specific: load generated class bytes directly
interface JvmClassLoader {

    fun loadClass(name: String, bytes: ByteArray): Class<*>

    fun loadClasses(classes: Map<String, ByteArray>): Map<String, Class<*>>

    fun createInstance(className: String, bytes: ByteArray, vararg constructorArgs: Any?): Any
}
