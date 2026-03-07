package org.kgen.reflect

import java.lang.foreign.*
import java.lang.foreign.ValueLayout.*

/**
 * Look up symbols in the current process and loaded shared libraries.
 *
 * Uses the system linker and `dlsym`/`GetProcAddress` to find function addresses.
 *
 * ```java
 * long strlen = ProcessSymbols.lookup("strlen");
 * long printf = ProcessSymbols.lookup("printf");
 *
 * // From a specific library
 * var lib = ProcessSymbols.loadLibrary("libz.so");
 * long deflate = lib.find("deflate");
 * lib.close();
 * ```
 */
object ProcessSymbols {
    private val linker = Linker.nativeLinker()
    private val defaultLookup = linker.defaultLookup()
    private val isWindows = System.getProperty("os.name").lowercase().contains("win")
    private val isLinux = System.getProperty("os.name").lowercase().contains("linux")

    // dlsym/dlopen handles (cached)
    private val dlopen: java.lang.invoke.MethodHandle? by lazy {
        defaultLookup.find("dlopen").map { sym ->
            linker.downcallHandle(sym, FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT))
        }.orElse(null)
    }

    private val dlsym: java.lang.invoke.MethodHandle? by lazy {
        defaultLookup.find("dlsym").map { sym ->
            linker.downcallHandle(sym, FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS))
        }.orElse(null)
    }

    private val dlclose: java.lang.invoke.MethodHandle? by lazy {
        defaultLookup.find("dlclose").map { sym ->
            linker.downcallHandle(sym, FunctionDescriptor.of(JAVA_INT, ADDRESS))
        }.orElse(null)
    }

    /**
     * Look up a symbol in the default linker scope (libc, loaded libraries, etc.).
     * Returns the address or null if not found.
     */
    @JvmStatic
    fun lookup(name: String): Long? {
        return defaultLookup.find(name).map { it.address() }.orElse(null)
    }

    /**
     * Look up a symbol using dlsym with RTLD_DEFAULT (search all loaded objects).
     * This finds symbols that the default FFM lookup might not expose.
     * Linux/macOS only.
     */
    @JvmStatic
    fun dlsymLookup(name: String): Long? {
        val dlsymHandle = dlsym ?: return null
        Arena.ofConfined().use { arena ->
            val nameStr = arena.allocateFrom(name)
            // RTLD_DEFAULT = NULL on Linux, ((void *) -2) on macOS
            val rtldDefault = if (isLinux) MemorySegment.NULL
                else MemorySegment.ofAddress(-2L)
            val result = dlsymHandle.invoke(rtldDefault, nameStr) as MemorySegment
            return if (result.address() == 0L) null else result.address()
        }
    }

    /**
     * Load a shared library and return a handle for looking up its symbols.
     */
    @JvmStatic
    fun loadLibrary(path: String): Library {
        return if (isWindows) loadLibraryWindows(path) else loadLibraryUnix(path)
    }

    private fun loadLibraryUnix(path: String): Library {
        val dlopenHandle = dlopen ?: throw UnsupportedOperationException("dlopen not available")
        val arena = Arena.ofConfined()
        val pathStr = arena.allocateFrom(path)
        val handle = dlopenHandle.invoke(pathStr, 0x1 or 0x100) as MemorySegment // RTLD_LAZY | RTLD_GLOBAL
        arena.close()
        if (handle.address() == 0L) throw RuntimeException("Failed to load library: $path")
        return UnixLibrary(path, handle)
    }

    private fun loadLibraryWindows(path: String): Library {
        val kernel32 = SymbolLookup.libraryLookup("kernel32", Arena.global())
        val loadLib = linker.downcallHandle(
            kernel32.find("LoadLibraryA").orElseThrow(),
            FunctionDescriptor.of(ADDRESS, ADDRESS)
        )
        val arena = Arena.ofConfined()
        val pathStr = arena.allocateFrom(path)
        val handle = loadLib.invoke(pathStr) as MemorySegment
        arena.close()
        if (handle.address() == 0L) throw RuntimeException("Failed to load library: $path")
        return WindowsLibrary(path, handle)
    }

    interface Library : AutoCloseable {
        val name: String

        /** Find a symbol in this library. Returns null if not found. */
        fun find(name: String): Long?

        /** Find a symbol or throw. */
        fun require(name: String): Long =
            find(name) ?: throw IllegalArgumentException("Symbol not found in $this: $name")
    }

    private class UnixLibrary(override val name: String, private val handle: MemorySegment) : Library {
        override fun find(name: String): Long? {
            val dlsymHandle = dlsym ?: return null
            Arena.ofConfined().use { arena ->
                val nameStr = arena.allocateFrom(name)
                val result = dlsymHandle.invoke(handle, nameStr) as MemorySegment
                return if (result.address() == 0L) null else result.address()
            }
        }

        override fun close() {
            dlclose?.invoke(handle)
        }

        override fun toString(): String = "Library($name)"
    }

    private class WindowsLibrary(override val name: String, private val handle: MemorySegment) : Library {
        private val getProcAddr by lazy {
            val kernel32 = SymbolLookup.libraryLookup("kernel32", Arena.global())
            linker.downcallHandle(
                kernel32.find("GetProcAddress").orElseThrow(),
                FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS)
            )
        }

        override fun find(name: String): Long? {
            Arena.ofConfined().use { arena ->
                val nameStr = arena.allocateFrom(name)
                val result = getProcAddr.invoke(handle, nameStr) as MemorySegment
                return if (result.address() == 0L) null else result.address()
            }
        }

        override fun close() {
            val kernel32 = SymbolLookup.libraryLookup("kernel32", Arena.global())
            val freeLib = linker.downcallHandle(
                kernel32.find("FreeLibrary").orElseThrow(),
                FunctionDescriptor.of(JAVA_INT, ADDRESS)
            )
            freeLib.invoke(handle)
        }

        override fun toString(): String = "Library($name)"
    }
}
