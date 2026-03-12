package org.kgen.reflect

import org.kgen.reflect.process.ProcessSymbols
import java.lang.foreign.*
import java.lang.foreign.ValueLayout.*
import java.lang.invoke.MethodHandle
import java.nio.file.Files
import java.nio.file.Path

/**
 * Loads a CoreCLR runtime via the hostfxr hosting API using FFM downcalls.
 *
 * This provides the ability to initialize a .NET runtime, load assemblies,
 * and call managed methods from native code. All operations are graceful:
 * if the .NET SDK is not installed, methods return null instead of throwing.
 *
 * ```java
 * if (ClrRuntimeLoader.isAvailable()) {
 *     var handle = ClrRuntimeLoader.load();
 *     var ctx = handle.initialize("app.runtimeconfig.json");
 *     long fn = ctx.getDelegate("MyApp.dll", "MyApp.Program", "Main");
 *     ctx.close();
 *     handle.close();
 * }
 * ```
 */
object ClrRuntimeLoader {
    private val isWindows = System.getProperty("os.name").lowercase().contains("win")
    private val isLinux = System.getProperty("os.name").lowercase().contains("linux")
    private val isMac = System.getProperty("os.name").lowercase().contains("mac")

    /**
     * Detect the path to the hostfxr library.
     * Checks `dotnet --info`, `DOTNET_ROOT`, and common installation paths.
     */
    @JvmStatic
    fun detectHostfxrPath(): String? {
        return detectFromDotnetRoot()
            ?: detectFromCommonPaths()
            ?: detectFromDotnetInfo()
    }

    /**
     * Load the hostfxr library, returning a handle for initializing the runtime.
     * Returns null if hostfxr cannot be found or loaded.
     */
    @JvmStatic
    fun load(): ClrRuntimeHandle? {
        val path = detectHostfxrPath() ?: return null
        return loadFromPath(path)
    }

    /**
     * Load from a specific hostfxr path.
     */
    @JvmStatic
    fun loadFromPath(hostfxrPath: String): ClrRuntimeHandle? {
        if (!Files.exists(Path.of(hostfxrPath))) return null
        return try {
            val lib = ProcessSymbols.loadLibrary(hostfxrPath)
            ClrRuntimeHandle.create(lib)
        } catch (_: Throwable) {
            null
        }
    }

    /** Check if CoreCLR can be loaded via hostfxr. */
    @JvmStatic
    fun isAvailable(): Boolean = detectHostfxrPath() != null

    private fun detectFromDotnetRoot(): String? {
        val dotnetRoot = System.getenv("DOTNET_ROOT") ?: return null
        return findHostfxrInDirectory(Path.of(dotnetRoot, "host", "fxr"))
    }

    private fun detectFromCommonPaths(): String? {
        val candidates = buildList {
            if (isWindows) {
                add(Path.of("C:\\Program Files\\dotnet\\host\\fxr"))
                add(Path.of("C:\\Program Files (x86)\\dotnet\\host\\fxr"))
            } else if (isLinux) {
                add(Path.of("/usr/share/dotnet/host/fxr"))
                add(Path.of("/usr/lib/dotnet/host/fxr"))
                add(Path.of("/snap/dotnet-sdk/current/host/fxr"))
                val home = System.getProperty("user.home")
                if (home != null) add(Path.of(home, ".dotnet", "host", "fxr"))
            } else if (isMac) {
                add(Path.of("/usr/local/share/dotnet/host/fxr"))
                add(Path.of("/opt/homebrew/share/dotnet/host/fxr"))
            }
        }
        for (dir in candidates) {
            val result = findHostfxrInDirectory(dir)
            if (result != null) return result
        }
        return null
    }

    private fun detectFromDotnetInfo(): String? {
        return try {
            val pb = ProcessBuilder("dotnet", "--list-runtimes")
            pb.redirectErrorStream(true)
            val proc = pb.start()
            val output = proc.inputStream.bufferedReader().readText()
            proc.waitFor()
            if (proc.exitValue() != 0) return null
            extractHostfxrFromRuntimeList(output)
        } catch (_: Throwable) {
            null
        }
    }

    internal fun extractHostfxrFromRuntimeList(output: String): String? {
        for (line in output.lines()) {
            if (!line.contains("Microsoft.NETCore.App")) continue
            val bracketStart = line.indexOf('[')
            val bracketEnd = line.indexOf(']')
            if (bracketStart < 0 || bracketEnd < 0) continue
            val runtimeDir = line.substring(bracketStart + 1, bracketEnd)
            val runtimePath = Path.of(runtimeDir)
            val fxrDir = runtimePath.parent?.parent?.resolve("host")?.resolve("fxr")
            if (fxrDir != null) {
                val result = findHostfxrInDirectory(fxrDir)
                if (result != null) return result
            }
        }
        return null
    }

    internal fun findHostfxrInDirectory(fxrDir: Path): String? {
        if (!Files.isDirectory(fxrDir)) return null
        val libName = hostfxrLibraryName()
        try {
            val versions = Files.list(fxrDir).use { stream ->
                stream.filter { Files.isDirectory(it) }
                    .sorted(Comparator.reverseOrder())
                    .toList()
            }
            for (versionDir in versions) {
                val candidate = versionDir.resolve(libName)
                if (Files.isRegularFile(candidate)) return candidate.toString()
            }
        } catch (_: Throwable) { }
        return null
    }

    internal fun hostfxrLibraryName(): String = when {
        isWindows -> "hostfxr.dll"
        isMac -> "libhostfxr.dylib"
        else -> "libhostfxr.so"
    }
}

/**
 * A loaded hostfxr library with function pointers for initializing CoreCLR.
 */
class ClrRuntimeHandle private constructor(
    private val library: ProcessSymbols.Library,
    private val initForConfig: MethodHandle,
    private val getDelegate: MethodHandle,
    private val closeHandle: MethodHandle,
) : AutoCloseable {

    /**
     * Initialize a CoreCLR runtime context from a runtimeconfig.json file.
     * Returns null if initialization fails.
     */
    fun initialize(runtimeConfigPath: String): ClrContext? {
        if (!Files.exists(Path.of(runtimeConfigPath))) return null
        return try {
            Arena.ofConfined().use { arena ->
                val configStr = arena.allocateFrom(runtimeConfigPath)
                val handlePtr = arena.allocate(ADDRESS)
                val result = initForConfig.invoke(configStr, MemorySegment.NULL, handlePtr) as Int
                if (result != 0) return null
                val hostContext = handlePtr.get(ADDRESS, 0).address()
                if (hostContext == 0L) return null
                createContext(hostContext, arena)
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun createContext(hostContext: Long, arena: Arena): ClrContext? {
        val delegatePtr = arena.allocate(ADDRESS)
        // hdt_load_assembly_and_get_function_pointer = 5
        val result = getDelegate.invoke(
            MemorySegment.ofAddress(hostContext), 5, delegatePtr
        ) as Int
        if (result != 0) return ClrContext(hostContext, 0L, closeHandle)
        val loadAssemblyDelegate = delegatePtr.get(ADDRESS, 0).address()
        return ClrContext(hostContext, loadAssemblyDelegate, closeHandle)
    }

    override fun close() {
        try {
            library.close()
        } catch (_: Throwable) { }
    }

    override fun toString(): String = "ClrRuntimeHandle(library=${library.name})"

    companion object {
        private val linker = Linker.nativeLinker()

        internal fun create(lib: ProcessSymbols.Library): ClrRuntimeHandle? {
            val initAddr = lib.find("hostfxr_initialize_for_runtime_config") ?: return null
            val getDelegateAddr = lib.find("hostfxr_get_runtime_delegate") ?: return null
            val closeAddr = lib.find("hostfxr_close") ?: return null

            val initHandle = linker.downcallHandle(
                MemorySegment.ofAddress(initAddr),
                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS)
            )
            val getDelegateHandle = linker.downcallHandle(
                MemorySegment.ofAddress(getDelegateAddr),
                FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS)
            )
            val closeHandleFn = linker.downcallHandle(
                MemorySegment.ofAddress(closeAddr),
                FunctionDescriptor.of(JAVA_INT, ADDRESS)
            )

            return ClrRuntimeHandle(lib, initHandle, getDelegateHandle, closeHandleFn)
        }
    }
}

/**
 * An initialized CoreCLR runtime context.
 * Provides access to managed method delegates and assembly execution.
 */
class ClrContext(
    private val hostContext: Long,
    private val loadAssemblyDelegate: Long,
    private val closeHandle: MethodHandle,
) : AutoCloseable {
    private var closed = false

    /** Whether this context has a valid assembly loading delegate. */
    fun hasDelegate(): Boolean = loadAssemblyDelegate != 0L

    /**
     * Get a function pointer for a managed method.
     * Returns 0 if the method cannot be found or invoked.
     *
     * @param assemblyPath Path to the .NET assembly DLL
     * @param typeName Fully qualified type name (e.g., "MyApp.Program, MyApp")
     * @param methodName Method name to load
     */
    fun getDelegate(assemblyPath: String, typeName: String, methodName: String): Long {
        if (loadAssemblyDelegate == 0L) return 0L
        if (closed) return 0L
        return try {
            invokeDelegateLoader(assemblyPath, typeName, methodName)
        } catch (_: Throwable) {
            0L
        }
    }

    private fun invokeDelegateLoader(assemblyPath: String, typeName: String, methodName: String): Long {
        val linker = Linker.nativeLinker()
        val loaderHandle = linker.downcallHandle(
            MemorySegment.ofAddress(loadAssemblyDelegate),
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS)
        )
        Arena.ofConfined().use { arena ->
            val asmStr = arena.allocateFrom(assemblyPath, Charsets.UTF_16LE)
            val typeStr = arena.allocateFrom(typeName, Charsets.UTF_16LE)
            val methodStr = arena.allocateFrom(methodName, Charsets.UTF_16LE)
            val resultPtr = arena.allocate(ADDRESS)
            val result = loaderHandle.invoke(
                asmStr, typeStr, methodStr, MemorySegment.NULL, MemorySegment.NULL, resultPtr
            ) as Int
            if (result != 0) return 0L
            return resultPtr.get(ADDRESS, 0).address()
        }
    }

    /** Whether this context is still open. */
    fun isOpen(): Boolean = !closed

    override fun close() {
        if (closed) return
        closed = true
        try {
            closeHandle.invoke(MemorySegment.ofAddress(hostContext))
        } catch (_: Throwable) { }
    }

    override fun toString(): String = "ClrContext(handle=0x${hostContext.toString(16)}, hasDelegate=$loadAssemblyDelegate)"
}
