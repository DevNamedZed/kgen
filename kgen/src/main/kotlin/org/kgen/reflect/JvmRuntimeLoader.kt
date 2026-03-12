package org.kgen.reflect

import org.kgen.reflect.process.ProcessSymbols
import java.lang.foreign.*
import java.lang.foreign.ValueLayout.*
import java.lang.invoke.MethodHandle
import java.nio.file.Files
import java.nio.file.Path

/**
 * Loads a JVM instance via JNI (libjvm).
 *
 * Since the JNI specification limits one JVM per process, and we are already
 * running inside a JVM, [load] will attempt to attach to the existing JVM
 * rather than create a new one. If that fails, it returns null.
 *
 * ```java
 * if (JvmRuntimeLoader.isAvailable()) {
 *     var handle = JvmRuntimeLoader.load();
 *     if (handle != null) {
 *         long clazz = handle.findClass("java/lang/System");
 *         handle.close();
 *     }
 * }
 * ```
 */
object JvmRuntimeLoader {
    private val isWindows = System.getProperty("os.name").lowercase().contains("win")
    private val isMac = System.getProperty("os.name").lowercase().contains("mac")

    /**
     * Find the path to libjvm.so / jvm.dll from JAVA_HOME or system properties.
     */
    @JvmStatic
    fun detectLibjvmPath(): String? {
        return detectFromJavaHome()
            ?: detectFromSystemProperty()
    }

    /**
     * Load libjvm and get a JVM handle. Since we are already in a JVM process,
     * this attempts to get the existing JVM via JNI_GetCreatedJavaVMs.
     * Returns null if loading fails or if a second JVM cannot be created.
     */
    @JvmStatic
    fun load(options: JvmOptions = JvmOptions()): JvmRuntimeHandle? {
        val path = detectLibjvmPath() ?: return null
        return loadFromPath(path, options)
    }

    /**
     * Load from a specific libjvm path.
     */
    @JvmStatic
    fun loadFromPath(libjvmPath: String, options: JvmOptions = JvmOptions()): JvmRuntimeHandle? {
        if (!Files.exists(Path.of(libjvmPath))) return null
        return try {
            val lib = ProcessSymbols.loadLibrary(libjvmPath)
            JvmRuntimeHandle.create(lib, options)
        } catch (_: Throwable) {
            null
        }
    }

    /** Whether libjvm can be found on this system. */
    @JvmStatic
    fun isAvailable(): Boolean = detectLibjvmPath() != null

    private fun detectFromJavaHome(): String? {
        val javaHome = System.getenv("JAVA_HOME")
            ?: System.getProperty("java.home")
            ?: return null
        return findLibjvm(Path.of(javaHome))
    }

    private fun detectFromSystemProperty(): String? {
        val javaHome = System.getProperty("java.home") ?: return null
        return findLibjvm(Path.of(javaHome))
    }

    internal fun findLibjvm(javaHome: Path): String? {
        val candidates = buildLibjvmCandidates(javaHome)
        return candidates.firstOrNull { Files.isRegularFile(Path.of(it)) }
    }

    internal fun buildLibjvmCandidates(javaHome: Path): List<String> {
        val lib = javaHome.resolve("lib")
        return buildList {
            if (isWindows) {
                add(lib.resolve("server").resolve("jvm.dll").toString())
                add(lib.resolve("client").resolve("jvm.dll").toString())
                add(javaHome.resolve("bin").resolve("server").resolve("jvm.dll").toString())
                add(javaHome.resolve("bin").resolve("client").resolve("jvm.dll").toString())
                add(javaHome.resolve("jre").resolve("bin").resolve("server").resolve("jvm.dll").toString())
            } else if (isMac) {
                add(lib.resolve("server").resolve("libjvm.dylib").toString())
                add(lib.resolve("libjvm.dylib").toString())
                add(javaHome.resolve("jre").resolve("lib").resolve("server").resolve("libjvm.dylib").toString())
            } else {
                add(lib.resolve("server").resolve("libjvm.so").toString())
                add(lib.resolve("libjvm.so").toString())
                add(lib.resolve("amd64").resolve("server").resolve("libjvm.so").toString())
                add(lib.resolve("aarch64").resolve("server").resolve("libjvm.so").toString())
                add(javaHome.resolve("jre").resolve("lib").resolve("server").resolve("libjvm.so").toString())
            }
        }
    }
}

/**
 * Options for creating a new JVM instance.
 */
data class JvmOptions(
    val classpath: String = "",
    val maxHeap: String = "256m",
    val additionalOptions: List<String> = emptyList(),
) {
    internal fun toOptionStrings(): List<String> = buildList {
        if (classpath.isNotEmpty()) add("-Djava.class.path=$classpath")
        if (maxHeap.isNotEmpty()) add("-Xmx$maxHeap")
        addAll(additionalOptions)
    }
}

/**
 * A handle to a JVM instance loaded via JNI.
 *
 * Since JNI allows only one JVM per process, this handle typically
 * references the already-running JVM obtained via JNI_GetCreatedJavaVMs.
 */
class JvmRuntimeHandle private constructor(
    private val javaVm: Long,
    private val jniEnv: Long,
    private val library: ProcessSymbols.Library,
    private val ownsVm: Boolean,
) : AutoCloseable {
    private var closed = false

    /** The JavaVM pointer. */
    fun javaVmPointer(): Long = javaVm

    /** The JNIEnv pointer. */
    fun jniEnvPointer(): Long = jniEnv

    /** Whether this handle is still open. */
    fun isOpen(): Boolean = !closed

    /** Whether this handle owns the JVM (created it, rather than attached). */
    fun ownsVm(): Boolean = ownsVm

    /**
     * Find a class by its JNI name (e.g., "java/lang/String").
     * Returns 0 if the class cannot be found.
     */
    fun findClass(name: String): Long {
        if (closed || jniEnv == 0L) return 0L
        return try {
            invokeFindClass(name)
        } catch (_: Throwable) {
            0L
        }
    }

    private fun invokeFindClass(name: String): Long {
        val fnTable = readFunctionTable()
        if (fnTable == 0L) return 0L
        // FindClass is at index 6 in the JNIEnv function table
        val findClassAddr = readPointerAt(fnTable + 6 * pointerSize())
        if (findClassAddr == 0L) return 0L
        val linker = Linker.nativeLinker()
        val handle = linker.downcallHandle(
            MemorySegment.ofAddress(findClassAddr),
            FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS)
        )
        Arena.ofConfined().use { arena ->
            val nameStr = arena.allocateFrom(name)
            val result = handle.invoke(MemorySegment.ofAddress(jniEnv), nameStr) as MemorySegment
            return result.address()
        }
    }

    /**
     * Get a static method ID. Returns 0 if not found.
     */
    fun getStaticMethodId(clazz: Long, name: String, signature: String): Long {
        if (closed || jniEnv == 0L || clazz == 0L) return 0L
        return try {
            invokeGetStaticMethodId(clazz, name, signature)
        } catch (_: Throwable) {
            0L
        }
    }

    private fun invokeGetStaticMethodId(clazz: Long, name: String, signature: String): Long {
        val fnTable = readFunctionTable()
        if (fnTable == 0L) return 0L
        // GetStaticMethodID is at index 113 in the JNIEnv function table
        val addr = readPointerAt(fnTable + 113 * pointerSize())
        if (addr == 0L) return 0L
        val linker = Linker.nativeLinker()
        val handle = linker.downcallHandle(
            MemorySegment.ofAddress(addr),
            FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS)
        )
        Arena.ofConfined().use { arena ->
            val nameStr = arena.allocateFrom(name)
            val sigStr = arena.allocateFrom(signature)
            val result = handle.invoke(
                MemorySegment.ofAddress(jniEnv),
                MemorySegment.ofAddress(clazz),
                nameStr, sigStr
            ) as MemorySegment
            return result.address()
        }
    }

    /**
     * Call a static int method. Returns 0 on failure.
     */
    fun callStaticInt(clazz: Long, method: Long, vararg args: Long): Int {
        if (closed || jniEnv == 0L || clazz == 0L || method == 0L) return 0
        return try {
            invokeCallStaticInt(clazz, method, args)
        } catch (_: Throwable) {
            0
        }
    }

    private fun invokeCallStaticInt(clazz: Long, method: Long, args: LongArray): Int {
        val fnTable = readFunctionTable()
        if (fnTable == 0L) return 0
        // CallStaticIntMethodA is at index 116 + (Int offset) in JNI function table
        // CallStaticIntMethod is at index 114
        val addr = readPointerAt(fnTable + 114 * pointerSize())
        if (addr == 0L) return 0
        val linker = Linker.nativeLinker()
        val paramLayouts = Array(args.size) { JAVA_LONG }
        val handle = linker.downcallHandle(
            MemorySegment.ofAddress(addr),
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, *paramLayouts)
        )
        return when (args.size) {
            0 -> handle.invoke(
                MemorySegment.ofAddress(jniEnv), MemorySegment.ofAddress(clazz),
                MemorySegment.ofAddress(method)
            ) as Int
            1 -> handle.invoke(
                MemorySegment.ofAddress(jniEnv), MemorySegment.ofAddress(clazz),
                MemorySegment.ofAddress(method), args[0]
            ) as Int
            else -> handle.invokeWithArguments(buildList {
                add(MemorySegment.ofAddress(jniEnv))
                add(MemorySegment.ofAddress(clazz))
                add(MemorySegment.ofAddress(method))
                args.forEach { add(it) }
            }) as Int
        }
    }

    /**
     * Call a static void method.
     */
    fun callStaticVoid(clazz: Long, method: Long, vararg args: Long) {
        if (closed || jniEnv == 0L || clazz == 0L || method == 0L) return
        try {
            invokeCallStaticVoid(clazz, method, args)
        } catch (_: Throwable) { }
    }

    private fun invokeCallStaticVoid(clazz: Long, method: Long, args: LongArray) {
        val fnTable = readFunctionTable()
        if (fnTable == 0L) return
        // CallStaticVoidMethod is at index 141
        val addr = readPointerAt(fnTable + 141 * pointerSize())
        if (addr == 0L) return
        val linker = Linker.nativeLinker()
        val paramLayouts = Array(args.size) { JAVA_LONG }
        val handle = linker.downcallHandle(
            MemorySegment.ofAddress(addr),
            FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, ADDRESS, *paramLayouts)
        )
        when (args.size) {
            0 -> handle.invoke(
                MemorySegment.ofAddress(jniEnv), MemorySegment.ofAddress(clazz),
                MemorySegment.ofAddress(method)
            )
            1 -> handle.invoke(
                MemorySegment.ofAddress(jniEnv), MemorySegment.ofAddress(clazz),
                MemorySegment.ofAddress(method), args[0]
            )
            else -> handle.invokeWithArguments(buildList {
                add(MemorySegment.ofAddress(jniEnv))
                add(MemorySegment.ofAddress(clazz))
                add(MemorySegment.ofAddress(method))
                args.forEach { add(it) }
            })
        }
    }

    private fun readFunctionTable(): Long {
        if (jniEnv == 0L) return 0L
        return readPointerAt(jniEnv)
    }

    private fun readPointerAt(address: Long): Long {
        val seg = MemorySegment.ofAddress(address).reinterpret(pointerSize())
        return seg.get(JAVA_LONG, 0)
    }

    private fun pointerSize(): Long = ADDRESS.byteSize()

    override fun close() {
        if (closed) return
        closed = true
        try {
            library.close()
        } catch (_: Throwable) { }
    }

    override fun toString(): String = "JvmRuntimeHandle(vm=0x${javaVm.toString(16)}, env=0x${jniEnv.toString(16)})"

    companion object {
        private val linker = Linker.nativeLinker()

        internal fun create(lib: ProcessSymbols.Library, options: JvmOptions): JvmRuntimeHandle? {
            // Try to get an already-created JVM first (JNI allows only one per process)
            val existing = getCreatedVm(lib)
            if (existing != null) return existing

            // Try to create a new JVM (will fail if one already exists)
            return createNewVm(lib, options)
        }

        private fun getCreatedVm(lib: ProcessSymbols.Library): JvmRuntimeHandle? {
            val getCreated = lib.find("JNI_GetCreatedJavaVMs") ?: return null
            val handle = linker.downcallHandle(
                MemorySegment.ofAddress(getCreated),
                FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS)
            )
            return Arena.ofConfined().use { arena ->
                val vmBuf = arena.allocate(ADDRESS)
                val countBuf = arena.allocate(JAVA_INT)
                val result = handle.invoke(vmBuf, 1, countBuf) as Int
                if (result != 0) return null
                val count = countBuf.get(JAVA_INT, 0)
                if (count == 0) return null
                val javaVm = vmBuf.get(ADDRESS, 0).address()
                if (javaVm == 0L) return null
                val jniEnv = attachCurrentThread(javaVm)
                JvmRuntimeHandle(javaVm, jniEnv, lib, ownsVm = false)
            }
        }

        private fun attachCurrentThread(javaVm: Long): Long {
            // Read the JavaVM function table: *javaVM -> function table
            val vmSeg = MemorySegment.ofAddress(javaVm).reinterpret(ADDRESS.byteSize())
            val fnTable = vmSeg.get(ADDRESS, 0).address()
            // AttachCurrentThread is at index 4 in the JavaVM function table
            val fnSeg = MemorySegment.ofAddress(fnTable + 4 * ADDRESS.byteSize()).reinterpret(ADDRESS.byteSize())
            val attachAddr = fnSeg.get(ADDRESS, 0).address()
            if (attachAddr == 0L) return 0L

            val attachHandle = linker.downcallHandle(
                MemorySegment.ofAddress(attachAddr),
                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS)
            )
            Arena.ofConfined().use { arena ->
                val envBuf = arena.allocate(ADDRESS)
                val result = attachHandle.invoke(
                    MemorySegment.ofAddress(javaVm), envBuf, MemorySegment.NULL
                ) as Int
                if (result != 0) return 0L
                return envBuf.get(ADDRESS, 0).address()
            }
        }

        private fun createNewVm(lib: ProcessSymbols.Library, options: JvmOptions): JvmRuntimeHandle? {
            val createVm = lib.find("JNI_CreateJavaVM") ?: return null
            val handle = linker.downcallHandle(
                MemorySegment.ofAddress(createVm),
                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS)
            )
            return try {
                Arena.ofConfined().use { arena ->
                    val optionStrings = options.toOptionStrings()
                    val optionsArray = allocateJvmOptions(arena, optionStrings)
                    val initArgs = allocateInitArgs(arena, optionsArray, optionStrings.size)

                    val vmBuf = arena.allocate(ADDRESS)
                    val envBuf = arena.allocate(ADDRESS)
                    val result = handle.invoke(vmBuf, envBuf, initArgs) as Int
                    if (result != 0) return null
                    val javaVm = vmBuf.get(ADDRESS, 0).address()
                    val jniEnv = envBuf.get(ADDRESS, 0).address()
                    JvmRuntimeHandle(javaVm, jniEnv, lib, ownsVm = true)
                }
            } catch (_: Throwable) {
                null
            }
        }

        private fun allocateJvmOptions(arena: Arena, options: List<String>): MemorySegment {
            // JavaVMOption: { char* optionString; void* extraInfo; }
            val optionSize = 2 * ADDRESS.byteSize()
            val array = arena.allocate(optionSize * options.size, ADDRESS.byteSize())
            for ((i, opt) in options.withIndex()) {
                val str = arena.allocateFrom(opt)
                array.set(ADDRESS, i * optionSize, str)
            }
            return array
        }

        private fun allocateInitArgs(arena: Arena, options: MemorySegment, count: Int): MemorySegment {
            // JavaVMInitArgs: { jint version; jint nOptions; JavaVMOption* options; jboolean ignoreUnrecognized; }
            val argsSize = 2 * JAVA_INT.byteSize() + ADDRESS.byteSize() + JAVA_INT.byteSize()
            val args = arena.allocate(argsSize.toLong(), ADDRESS.byteSize())
            var offset = 0L
            args.set(JAVA_INT, offset, 0x00010008) // JNI_VERSION_1_8
            offset += JAVA_INT.byteSize()
            args.set(JAVA_INT, offset, count)
            offset += JAVA_INT.byteSize()
            args.set(ADDRESS, offset, options)
            return args
        }
    }
}
