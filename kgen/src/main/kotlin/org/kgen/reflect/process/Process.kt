package org.kgen.reflect.process

import org.kgen.binary.ArchType
import org.kgen.binary.Architecture
import org.kgen.binary.ObjectFile
import org.kgen.binary.ObjectFormat
import org.kgen.reflect.Module
import org.kgen.reflect.NativeCode
import org.kgen.reflect.NativeMemory
import java.lang.foreign.*
import java.lang.foreign.ValueLayout.*
import java.nio.file.Files
import java.nio.file.Path

/**
 * Represents a running process. Provides module enumeration, symbol lookup,
 * and library loading/unloading.
 *
 * ```java
 * var process = Process.current();
 * process.pid();              // current PID
 * process.arch();             // e.g. Architecture.X86_64_LINUX
 *
 * // Loaded modules
 * process.modules();          // List<Module>
 * process.module("libc");     // Module? (fuzzy match)
 *
 * // Global symbol lookup
 * var addr = process.lookup("strlen");  // Long?
 *
 * // Load a shared library
 * var lib = process.loadLibrary("/usr/lib/libz.so");
 * process.unload(lib);
 * ```
 */
class Process private constructor(
    private val pid: Long,
    private val processArch: Architecture,
    private val isCurrentProcess: Boolean,
) {
    private val loadedModules = mutableListOf<LoadedModule>()

    /** Process ID. */
    fun pid(): Long = pid

    /** The architecture this process runs on. */
    fun arch(): Architecture = processArch

    /** Whether this represents the current JVM process. */
    fun isCurrent(): Boolean = isCurrentProcess

    /** The process name (for current process, this is "java" or the main class). */
    fun name(): String {
        val cmd = System.getProperty("sun.java.command") ?: "java"
        val space = cmd.indexOf(' ')
        val main = if (space > 0) cmd.substring(0, space) else cmd
        val dot = main.lastIndexOf('.')
        return if (dot > 0) main.substring(dot + 1) else main
    }

    /**
     * Enumerate loaded modules in the current process.
     *
     * On Linux, reads `/proc/self/maps` to find loaded shared objects.
     * On macOS/Windows, uses platform-specific APIs.
     */
    fun modules(): List<Module> {
        if (!isCurrentProcess) throw UnsupportedOperationException("Module enumeration requires Process.current()")
        val result = mutableListOf<Module>()
        if (isLinux) {
            result.addAll(enumerateLinuxModules())
        } else if (isWindows) {
            result.addAll(enumerateWindowsModules())
        }
        return result
    }

    /**
     * Find a loaded module by name (fuzzy match — matches filename or partial name).
     *
     * ```java
     * process.module("libc");    // matches libc.so.6
     * process.module("libz");    // matches libz.so.1
     * ```
     */
    fun module(name: String): Module? {
        return modules().firstOrNull { mod ->
            val modName = mod.name()
            modName == name || modName.startsWith("$name.") || modName.contains(name)
        }
    }

    /**
     * Look up a symbol address in the current process (all loaded modules).
     * Returns null if the symbol is not found.
     */
    fun lookup(name: String): Long? {
        if (!isCurrentProcess) throw UnsupportedOperationException("Symbol lookup requires Process.current()")
        return ProcessSymbols.lookup(name) ?: ProcessSymbols.dlsymLookup(name)
    }

    /**
     * Load a shared library into the current process.
     * The returned module is tracked and can be unloaded with [unload].
     */
    fun loadLibrary(path: String): Module {
        if (!isCurrentProcess) throw UnsupportedOperationException("loadLibrary requires Process.current()")
        val lib = ProcessSymbols.loadLibrary(path)
        val filePath = Path.of(path)
        val module = if (Files.exists(filePath)) {
            Module.Companion.fromFile(filePath)
        } else {
            Module.Companion.fromObjectFile(
                ObjectFile(format = ObjectFormat.ELF, arch = detectCurrentArch(),
                    sections = emptyList(), symbols = emptyList(), relocations = emptyList()),
                Path.of(path).fileName.toString()
            )
        }
        loadedModules.add(LoadedModule(module, lib))
        return module
    }

    /**
     * Load an [ObjectFile] into executable memory in the current process.
     */
    fun load(objectFile: ObjectFile): Module {
        if (!isCurrentProcess) throw UnsupportedOperationException("load requires Process.current()")
        val native = NativeCode.Companion.load(objectFile)
        val module = Module.Companion.fromObjectFile(objectFile, "loaded_code")
        loadedModules.add(LoadedModule(module, null, native))
        return module
    }

    /**
     * Load raw machine code bytes into executable memory.
     */
    fun loadCode(code: ByteArray, symbols: Map<String, Long> = emptyMap()): Module {
        if (!isCurrentProcess) throw UnsupportedOperationException("loadCode requires Process.current()")
        val native = NativeCode.Companion.loadBytes(code, symbols)
        val obj = ObjectFile(format = ObjectFormat.ELF, arch = detectCurrentArch(),
            sections = emptyList(), symbols = emptyList(), relocations = emptyList())
        val module = Module.Companion.fromObjectFile(obj, "loaded_code")
        loadedModules.add(LoadedModule(module, null, native))
        return module
    }

    /**
     * Unload a previously loaded module, freeing its resources.
     */
    fun unload(module: Module) {
        val loaded = loadedModules.firstOrNull { it.module === module } ?: return
        loaded.library?.close()
        loadedModules.remove(loaded)
    }

    /** Whether the current process has a JVM (always true for Process.current()). */
    fun hasJvm(): Boolean = isCurrentProcess

    /** Whether the current process has a CLR runtime loaded. */
    fun hasClr(): Boolean {
        if (!isCurrentProcess) return false
        return lookup("coreclr_initialize") != null || lookup("CLRCreateInstance") != null
    }

    /** The JVM version string. */
    fun jvmVersion(): String? = if (isCurrentProcess) System.getProperty("java.version") else null

    /** Read raw memory at the given address. */
    fun readMemory(address: Long, length: Int): ByteArray {
        if (!isCurrentProcess) throw UnsupportedOperationException("readMemory on remote process not yet implemented")
        return NativeMemory.Companion.readBytes(address, length)
    }

    /** Write raw bytes to the given address. */
    fun writeMemory(address: Long, data: ByteArray) {
        if (!isCurrentProcess) throw UnsupportedOperationException("writeMemory on remote process not yet implemented")
        NativeMemory.Companion.writeBytes(address, data)
    }

    override fun toString(): String = "Process(pid=$pid, arch=${processArch.arch})"

    private class LoadedModule(
        val module: Module,
        val library: ProcessSymbols.Library? = null,
        val nativeCode: NativeCode? = null,
    )

    private fun enumerateLinuxModules(): List<Module> {
        val mapsPath = Path.of("/proc/self/maps")
        if (!Files.exists(mapsPath)) return emptyList()
        val seen = mutableSetOf<String>()
        val result = mutableListOf<Module>()
        for (line in Files.readAllLines(mapsPath)) {
            // Format: addr-addr perms offset dev inode pathname
            val parts = line.trim().split("\\s+".toRegex())
            if (parts.size < 6) continue
            val path = parts.last()
            if (!path.startsWith("/")) continue
            if (path in seen) continue
            seen.add(path)
            val filePath = Path.of(path)
            if (!Files.exists(filePath)) continue
            if (!Files.isRegularFile(filePath)) continue
            try {
                result.add(Module.Companion.fromFile(filePath))
            } catch (_: Throwable) {
                // Skip files we can't parse (e.g. vdso, non-binary mappings)
            }
        }
        return result
    }

    private fun enumerateWindowsModules(): List<Module> {
        return WindowsModuleEnumerator.enumerate()
    }

    private object WindowsModuleEnumerator {
        private val linker = Linker.nativeLinker()
        private val kernel32 = SymbolLookup.libraryLookup("kernel32", Arena.global())

        private val getCurrentProcess = linker.downcallHandle(
            kernel32.find("GetCurrentProcess").orElseThrow(),
            FunctionDescriptor.of(ADDRESS)
        )

        private val enumProcessModules = linker.downcallHandle(
            kernel32.find("K32EnumProcessModules").orElseThrow(),
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, ADDRESS)
        )

        private val getModuleFileNameExW = linker.downcallHandle(
            kernel32.find("K32GetModuleFileNameExW").orElseThrow(),
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, JAVA_INT)
        )

        private val getModuleInformation = linker.downcallHandle(
            kernel32.find("K32GetModuleInformation").orElseThrow(),
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, JAVA_INT)
        )

        fun enumerate(): List<Module> {
            val hProcess = getCurrentProcess.invoke() as MemorySegment
            val moduleHandles = collectModuleHandles(hProcess) ?: return emptyList()
            val result = mutableListOf<Module>()
            for (hModule in moduleHandles) {
                val mod = buildModule(hProcess, hModule)
                if (mod != null) result.add(mod)
            }
            return result
        }

        private fun collectModuleHandles(hProcess: MemorySegment): List<MemorySegment>? {
            Arena.ofConfined().use { arena ->
                val cbNeeded = arena.allocate(JAVA_INT)
                // First call to get required size
                val initialSize = 1024
                var hModArray = arena.allocate(ADDRESS, initialSize.toLong())
                var ok = enumProcessModules.invoke(
                    hProcess, hModArray, (initialSize * ADDRESS.byteSize()).toInt(), cbNeeded
                ) as Int
                if (ok == 0) return null

                val needed = cbNeeded.get(JAVA_INT, 0)
                val count = needed / ADDRESS.byteSize().toInt()

                if (needed > initialSize * ADDRESS.byteSize().toInt()) {
                    hModArray = arena.allocate(ADDRESS, count.toLong())
                    ok = enumProcessModules.invoke(
                        hProcess, hModArray, needed, cbNeeded
                    ) as Int
                    if (ok == 0) return null
                }

                return (0 until count).map { i ->
                    hModArray.getAtIndex(ADDRESS, i.toLong())
                }
            }
        }

        private fun buildModule(hProcess: MemorySegment, hModule: MemorySegment): Module? {
            val filePath = getModulePath(hProcess, hModule) ?: return null
            val path = Path.of(filePath)
            if (!Files.exists(path) || !Files.isRegularFile(path)) return null
            return try {
                Module.Companion.fromFile(path)
            } catch (_: Throwable) {
                null
            }
        }

        private fun getModulePath(hProcess: MemorySegment, hModule: MemorySegment): String? {
            Arena.ofConfined().use { arena ->
                val maxPath = 260
                val buf = arena.allocate(JAVA_CHAR, maxPath.toLong())
                val len = getModuleFileNameExW.invoke(hProcess, hModule, buf, maxPath) as Int
                if (len == 0) return null
                val sb = StringBuilder(len)
                for (i in 0 until len) {
                    sb.append(buf.getAtIndex(JAVA_CHAR, i.toLong()))
                }
                return sb.toString()
            }
        }
    }

    companion object {
        private val isLinux = System.getProperty("os.name").lowercase().contains("linux")
        private val isWindows = System.getProperty("os.name").lowercase().contains("win")
        private val isMac = System.getProperty("os.name").lowercase().contains("mac")

        private val currentProcess: Process by lazy {
            Process(
                pid = ProcessHandle.current().pid(),
                processArch = detectCurrentArch(),
                isCurrentProcess = true,
            )
        }

        /**
         * Get the current process.
         */
        @JvmStatic
        fun current(): Process = currentProcess

        private fun detectCurrentArch(): Architecture {
            val osArch = System.getProperty("os.arch").lowercase()
            return when {
                osArch.contains("amd64") || osArch.contains("x86_64") -> {
                    when {
                        isLinux -> Architecture.X86_64_LINUX
                        isWindows -> Architecture.X86_64_WINDOWS
                        isMac -> Architecture.X86_64_MACOS
                        else -> Architecture.X86_64_LINUX
                    }
                }
                osArch.contains("aarch64") || osArch.contains("arm64") -> {
                    when {
                        isLinux -> Architecture(ArchType.AARCH64)
                        isMac -> Architecture(ArchType.AARCH64)
                        isWindows -> Architecture(ArchType.AARCH64)
                        else -> Architecture(ArchType.AARCH64)
                    }
                }
                else -> Architecture.X86_64_LINUX
            }
        }
    }
}
