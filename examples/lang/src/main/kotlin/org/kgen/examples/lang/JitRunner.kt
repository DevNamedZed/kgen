package org.kgen.examples.lang

import org.kgen.ir.Module
import org.kgen.jit.JitEngine
import org.kgen.jit.SymbolResolver
import org.kgen.jit.TieredCompilation
import org.kgen.runtime.*
import org.kgen.runtime.gc.*
import org.kgen.runtime.exec.*
import org.kgen.pipeline.OptLevel
import org.kgen.pipeline.Pipeline
import java.lang.foreign.*
import java.lang.foreign.ValueLayout.JAVA_LONG
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType

/**
 * JIT-compiles and runs programs written in the example language.
 *
 * ```java
 * var runner = new JitRunner();
 * long result = runner.run("fun add(a: int, b: int): int { return a + b }", "add", 3L, 4L);
 * // result == 7
 * runner.close();
 * ```
 *
 * External functions can be registered as callbacks:
 * ```java
 * runner.registerCallback("getAnswer", 0, () -> 42L);
 * long result = runner.run("extern fun getAnswer(): int\n fun main(): int { return getAnswer() }", "main");
 * ```
 *
 * Managed heap is available for array operations:
 * ```
 * extern fun new_array(size: int): int
 * extern fun array_get(arr: int, idx: int): int
 * extern fun array_set(arr: int, idx: int, val: int): int
 * extern fun array_len(arr: int): int
 * extern fun gc_collect(): int
 * ```
 */
class JitRunner : AutoCloseable {
    private val jit = JitEngine.forCurrentPlatform()
    private val arena = Arena.ofShared()
    private val callbacks = mutableMapOf<String, CallbackRegistration>()
    private val callbackSymbols = mutableMapOf<String, Long>()
    private var runtime: DefaultManagedRuntime? = null

    init {
        jit.addResolver(SymbolResolver { name -> callbackSymbols[name] })
    }

    /**
     * Enable the managed runtime with heap allocation, arrays, and GC.
     * This registers built-in extern functions: new_array, array_get, array_set, array_len, gc_collect.
     */
    fun enableManagedRuntime(heapSize: Long = 4096) {
        val rt = DefaultManagedRuntime.create(heapSize)
        runtime = rt

        // Register the array layout: header(8) + length(8) + data(size*8)
        val arrayLayout = ObjectLayout(
            name = "Array",
            size = 0, // placeholder — actual size varies
            fields = listOf(FieldDescriptor("length", 0, 8, false)),
            typeId = ARRAY_TYPE_ID,
        )
        rt.typeRegistry().register(arrayLayout)

        // Built-in: new_array(size) -> address
        registerCallback("new_array", 1) { args ->
            val size = args[0]
            val totalDataSize = 8 + size * 8 // length field + elements
            val layout = ObjectLayout(
                name = "Array",
                size = totalDataSize.toInt(),
                fields = listOf(FieldDescriptor("length", 0, 8, false)),
                typeId = ARRAY_TYPE_ID,
            )
            val addr = rt.heap().allocate(layout)
            rt.heap().writeField(addr, 0, size) // store length
            addr
        }

        // Built-in: array_get(arr, idx) -> value
        registerCallback("array_get", 2) { args ->
            val arr = args[0]
            val idx = args[1]
            // Data starts after header(8) + length(8), each element is 8 bytes
            val dataOffset = arr + ObjectLayout.HEADER_SIZE + 8 + idx * 8
            val bytes = rt.heap().readBytes(dataOffset, 8)
            var value = 0L
            for (i in 0 until 8) value = value or ((bytes[i].toLong() and 0xFF) shl (i * 8))
            value
        }

        // Built-in: array_set(arr, idx, value) -> 0
        registerCallback("array_set", 3) { args ->
            val arr = args[0]
            val idx = args[1]
            val value = args[2]
            val dataOffset = arr + ObjectLayout.HEADER_SIZE + 8 + idx * 8
            val bytes = ByteArray(8)
            for (i in 0 until 8) bytes[i] = (value shr (i * 8)).toByte()
            rt.heap().writeBytes(dataOffset, bytes)
            0L
        }

        // Built-in: array_len(arr) -> length
        registerCallback("array_len", 1) { args ->
            val arr = args[0]
            rt.heap().readField(arr, 0)
        }

        // Built-in: gc_collect() -> collection count
        registerCallback("gc_collect", 0) {
            rt.gc().collect()
            rt.gc().collectionCount()
        }

        // String helpers (strings are arrays of char codes)

        // str_len(s) -> length (alias for array_len)
        registerCallback("str_len", 1) { args ->
            rt.heap().readField(args[0], 0)
        }

        // str_get(s, idx) -> char code
        registerCallback("str_get", 2) { args ->
            val dataOffset = args[0] + ObjectLayout.HEADER_SIZE + 8 + args[1] * 8
            val bytes = rt.heap().readBytes(dataOffset, 8)
            var value = 0L
            for (i in 0 until 8) value = value or ((bytes[i].toLong() and 0xFF) shl (i * 8))
            value
        }

        // str_eq(s1, s2) -> 1 if equal, 0 if not
        registerCallback("str_eq", 2) { args ->
            val len1 = rt.heap().readField(args[0], 0)
            val len2 = rt.heap().readField(args[1], 0)
            if (len1 != len2) return@registerCallback 0L
            for (i in 0 until len1) {
                val off1 = args[0] + ObjectLayout.HEADER_SIZE + 8 + i * 8
                val off2 = args[1] + ObjectLayout.HEADER_SIZE + 8 + i * 8
                val b1 = rt.heap().readBytes(off1, 8)
                val b2 = rt.heap().readBytes(off2, 8)
                if (!b1.contentEquals(b2)) return@registerCallback 0L
            }
            1L
        }

        // str_concat(s1, s2) -> new string
        registerCallback("str_concat", 2) { args ->
            val len1 = rt.heap().readField(args[0], 0)
            val len2 = rt.heap().readField(args[1], 0)
            val totalLen = len1 + len2
            val totalDataSize = 8 + totalLen * 8
            val layout = ObjectLayout(
                name = "Array",
                size = totalDataSize.toInt(),
                fields = listOf(FieldDescriptor("length", 0, 8, false)),
                typeId = ARRAY_TYPE_ID,
            )
            val newArr = rt.heap().allocate(layout)
            rt.heap().writeField(newArr, 0, totalLen)
            // Copy s1 elements
            for (i in 0 until len1) {
                val srcOff = args[0] + ObjectLayout.HEADER_SIZE + 8 + i * 8
                val dstOff = newArr + ObjectLayout.HEADER_SIZE + 8 + i * 8
                rt.heap().writeBytes(dstOff, rt.heap().readBytes(srcOff, 8))
            }
            // Copy s2 elements
            for (i in 0 until len2) {
                val srcOff = args[1] + ObjectLayout.HEADER_SIZE + 8 + i * 8
                val dstOff = newArr + ObjectLayout.HEADER_SIZE + 8 + (len1 + i) * 8
                rt.heap().writeBytes(dstOff, rt.heap().readBytes(srcOff, 8))
            }
            newArr
        }
    }

    /** Get the managed runtime (only available after enableManagedRuntime). */
    fun runtime(): DefaultManagedRuntime? = runtime

    /** Read a string from the managed heap (string handle → Kotlin String). */
    fun readString(handle: Long): String {
        val rt = runtime ?: throw IllegalStateException("Managed runtime not enabled")
        val len = rt.heap().readField(handle, 0)
        val sb = StringBuilder(len.toInt())
        for (i in 0 until len) {
            val offset = handle + ObjectLayout.HEADER_SIZE + 8 + i * 8
            val bytes = rt.heap().readBytes(offset, 8)
            var value = 0L
            for (j in 0 until 8) value = value or ((bytes[j].toLong() and 0xFF) shl (j * 8))
            sb.append(value.toInt().toChar())
        }
        return sb.toString()
    }

    /**
     * Enable tiered compilation — functions start at O0 and get recompiled
     * with optimization after the given number of calls.
     */
    fun enableTieredCompilation(threshold: Int = 100, pipeline: Pipeline = OptLevel.O2.pipeline()) {
        val tiered = TieredCompilation(threshold)
        tiered.setTier1Pipeline(pipeline)
        jit.setTieredCompilation(tiered)
    }

    /** Get the tiered compilation stats, if enabled. */
    fun tieredCompilation(): TieredCompilation? = jit.tieredCompilation()

    /**
     * Enable resolving symbols from the host process (libc, loaded libraries).
     */
    fun enableHostSymbols() {
        jit.addResolver(SymbolResolver.host())
    }

    fun compile(source: String): Module {
        val tokens = Lexer(source).tokenize()
        val program = Parser(tokens).parseProgram()
        return Compiler().compile(program)
    }

    fun load(source: String): Module {
        val module = compile(source)
        jit.addModule(module)
        return module
    }

    fun run(source: String, functionName: String, vararg args: Long): Long {
        load(source)
        return jit.call(functionName, *args)
    }

    fun runInt(source: String, functionName: String, vararg args: Long): Int {
        load(source)
        return jit.callInt(functionName, *args)
    }

    fun runDouble(source: String, functionName: String, vararg args: Double): Double {
        load(source)
        return callDouble(functionName, *args)
    }

    fun callDouble(functionName: String, vararg args: Double): Double {
        val descriptor = FunctionDescriptor.of(
            ValueLayout.JAVA_DOUBLE,
            *Array(args.size) { ValueLayout.JAVA_DOUBLE }
        )
        val handle = jit.handle(functionName, descriptor)
        return when (args.size) {
            0 -> handle.invoke() as Double
            1 -> handle.invoke(args[0]) as Double
            2 -> handle.invoke(args[0], args[1]) as Double
            3 -> handle.invoke(args[0], args[1], args[2]) as Double
            else -> handle.invokeWithArguments(*args.map { it as Any }.toTypedArray()) as Double
        }
    }

    fun call(functionName: String, vararg args: Long): Long {
        return jit.call(functionName, *args)
    }

    /**
     * Register a Kotlin callback that JIT'd code can call as an extern function.
     *
     * @param name the function name used in `extern fun name(...): type`
     * @param paramCount number of i64 parameters the callback accepts
     * @param callback the function to invoke — receives longs, returns long
     */
    fun registerCallback(name: String, paramCount: Int, callback: (LongArray) -> Long) {
        val wrapper = CallbackWrapper(callback)
        val methodName = when (paramCount) {
            0 -> "invoke0"
            1 -> "invoke1"
            2 -> "invoke2"
            3 -> "invoke3"
            4 -> "invoke4"
            else -> throw IllegalArgumentException("Callbacks with more than 4 parameters not yet supported")
        }
        val paramTypes = mutableListOf<Class<*>>()
        repeat(paramCount) { paramTypes.add(Long::class.java) }
        val handle = MethodHandles.lookup().bind(
            wrapper, methodName,
            MethodType.methodType(Long::class.java, paramTypes)
        )
        val descriptor = FunctionDescriptor.of(JAVA_LONG, *Array(paramCount) { JAVA_LONG })
        val stub = Linker.nativeLinker().upcallStub(handle, descriptor, arena)
        val address = stub.address()
        callbacks[name] = CallbackRegistration(stub, address)
        callbackSymbols[name] = address
    }

    fun engine(): JitEngine = jit

    override fun close() {
        jit.close()
        callbacks.clear()
        runtime?.close()
        if (arena.scope().isAlive) arena.close()
    }

    companion object {
        const val ARRAY_TYPE_ID = 100
    }
}

private class CallbackWrapper(private val fn: (LongArray) -> Long) {
    fun invoke0(): Long = fn(longArrayOf())
    fun invoke1(a: Long): Long = fn(longArrayOf(a))
    fun invoke2(a: Long, b: Long): Long = fn(longArrayOf(a, b))
    fun invoke3(a: Long, b: Long, c: Long): Long = fn(longArrayOf(a, b, c))
    fun invoke4(a: Long, b: Long, c: Long, d: Long): Long = fn(longArrayOf(a, b, c, d))
}

private class CallbackRegistration(
    val stub: MemorySegment,
    val address: Long,
)
