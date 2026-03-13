package org.kgen.reflect.process

import org.kgen.binary.ArchType
import org.kgen.binary.Architecture
import java.io.RandomAccessFile
import java.lang.foreign.*
import java.lang.foreign.ValueLayout.*
import java.nio.file.Files
import java.nio.file.Path

/**
 * Represents a remote (external) process for inspection and manipulation.
 *
 * Provides memory read/write, module enumeration, and thread listing
 * for processes other than the current JVM.
 *
 * On Linux, uses `/proc/<pid>/` for module enumeration and memory access.
 * On Windows, uses kernel32 ReadProcessMemory/WriteProcessMemory.
 *
 * ```java
 * var proc = RemoteProcess.open(1234);
 * proc.pid();                         // 1234
 * proc.name();                        // "myapp"
 * proc.isAlive();                     // true/false
 *
 * // Memory access
 * byte[] data = proc.readMemory(0x7fff0000L, 64);
 * proc.writeMemory(0x7fff0000L, new byte[]{0x90});
 *
 * // Module enumeration (Linux: /proc/pid/maps)
 * var modules = proc.modules();
 * var libc = proc.module("libc");
 *
 * // Thread listing
 * var threads = proc.threads();
 * ```
 */
class RemoteProcess private constructor(
    private val pid: Long,
    private val processArch: Architecture,
    private val windowsHandle: MemorySegment? = null,
) : AutoCloseable {
    /** Process ID. */
    fun pid(): Long = pid

    /** Architecture of the process. */
    fun arch(): Architecture = processArch

    /** Check if the process is still running. */
    fun isAlive(): Boolean {
        return ProcessHandle.of(pid).map { it.isAlive }.orElse(false)
    }

    /** Get the process name. */
    fun name(): String {
        return ProcessHandle.of(pid)
            .flatMap { it.info().command() }
            .map { Path.of(it).fileName.toString() }
            .orElse("unknown")
    }

    /** Get the command line of the process, if available. */
    fun commandLine(): String? {
        return ProcessHandle.of(pid)
            .flatMap { it.info().commandLine() }
            .orElse(null)
    }

    /** Get the process start time as epoch millis, or null. */
    fun startTime(): Long? {
        return ProcessHandle.of(pid)
            .flatMap { it.info().startInstant() }
            .map { it.toEpochMilli() }
            .orElse(null)
    }

    /**
     * Read memory from the remote process.
     *
     * On Linux, reads from `/proc/<pid>/mem`.
     * Requires appropriate permissions (same user or ptrace attach).
     */
    fun readMemory(address: Long, length: Int): ByteArray {
        if (isLinux) {
            return readLinuxMemory(address, length)
        }
        if (isWindows && windowsHandle != null) {
            return WindowsProcessApi.readMemory(windowsHandle, address, length)
        }
        throw UnsupportedOperationException("Remote memory read not supported on this platform")
    }

    /**
     * Write memory to the remote process.
     *
     * On Linux, writes to `/proc/<pid>/mem`.
     * On Windows, uses WriteProcessMemory.
     */
    fun writeMemory(address: Long, data: ByteArray) {
        if (isLinux) {
            writeLinuxMemory(address, data)
            return
        }
        if (isWindows && windowsHandle != null) {
            WindowsProcessApi.writeMemory(windowsHandle, address, data)
            return
        }
        throw UnsupportedOperationException("Remote memory write not supported on this platform")
    }

    /**
     * Enumerate loaded modules in the remote process.
     * On Linux, reads `/proc/<pid>/maps`.
     */
    fun modules(): List<MemoryMapping> {
        if (isLinux) return enumerateLinuxModules()
        if (isWindows && windowsHandle != null) return WindowsProcessApi.enumerateModules(windowsHandle)
        return emptyList()
    }

    /**
     * Find a module by name (fuzzy match).
     */
    fun module(name: String): MemoryMapping? {
        return modules().firstOrNull { mapping ->
            mapping.path?.let { p ->
                val fileName = Path.of(p).fileName?.toString() ?: ""
                fileName == name || fileName.startsWith("$name.") || fileName.contains(name)
            } ?: false
        }
    }

    /**
     * List thread IDs for this process (Linux: reads /proc/<pid>/task/).
     */
    fun threads(): List<Long> {
        if (isLinux) {
            val taskDir = Path.of("/proc/$pid/task")
            if (!Files.exists(taskDir)) return emptyList()
            return Files.list(taskDir).use { stream ->
                stream.map { it.fileName.toString().toLongOrNull() }
                    .filter { it != null }
                    .map { it!! }
                    .toList()
            }
        }
        return emptyList()
    }

    /**
     * Get the memory map entries for the process.
     */
    fun memoryMap(): List<MemoryMapping> = modules()

    override fun close() {
        if (windowsHandle != null && isWindows) {
            WindowsProcessApi.closeHandle(windowsHandle)
        }
    }

    override fun toString(): String = "RemoteProcess(pid=$pid, name=${name()}, alive=${isAlive()})"

    // --- Memory mapping data ---

    /**
     * Represents a memory region in a process's address space.
     */
    data class MemoryMapping(
        val startAddress: Long,
        val endAddress: Long,
        val permissions: String,
        val offset: Long,
        val device: String,
        val inode: Long,
        val path: String?,
    ) {
        val size: Long get() = endAddress - startAddress
        val isReadable: Boolean get() = permissions.getOrNull(0) == 'r'
        val isWritable: Boolean get() = permissions.getOrNull(1) == 'w'
        val isExecutable: Boolean get() = permissions.getOrNull(2) == 'x'
        val isPrivate: Boolean get() = permissions.getOrNull(3) == 'p'
        val isFile: Boolean get() = path != null && path.startsWith("/")
    }

    // --- Linux implementation ---

    private fun readLinuxMemory(address: Long, length: Int): ByteArray {
        val memPath = Path.of("/proc/$pid/mem")
        if (!Files.exists(memPath)) throw IllegalStateException("Cannot access /proc/$pid/mem")
        val raf = RandomAccessFile(memPath.toFile(), "r")
        return try {
            raf.seek(address)
            val buf = ByteArray(length)
            val read = raf.read(buf)
            if (read < length) buf.copyOf(read) else buf
        } finally {
            raf.close()
        }
    }

    private fun writeLinuxMemory(address: Long, data: ByteArray) {
        val memPath = Path.of("/proc/$pid/mem")
        if (!Files.exists(memPath)) throw IllegalStateException("Cannot access /proc/$pid/mem")
        val raf = RandomAccessFile(memPath.toFile(), "rw")
        try {
            raf.seek(address)
            raf.write(data)
        } finally {
            raf.close()
        }
    }

    private fun enumerateLinuxModules(): List<MemoryMapping> {
        val mapsPath = Path.of("/proc/$pid/maps")
        if (!Files.exists(mapsPath)) return emptyList()
        val result = mutableListOf<MemoryMapping>()
        for (line in Files.readAllLines(mapsPath)) {
            val mapping = parseMapLine(line) ?: continue
            result.add(mapping)
        }
        return result
    }

    companion object {
        private val isLinux = System.getProperty("os.name").lowercase().contains("linux")
        private val isWindows = System.getProperty("os.name").lowercase().contains("win")

        /**
         * Open a remote process by PID.
         * @throws IllegalArgumentException if the process does not exist.
         */
        @JvmStatic
        fun open(pid: Long): RemoteProcess {
            val handle = ProcessHandle.of(pid)
            if (handle.isEmpty) throw IllegalArgumentException("No process with PID $pid")
            val winHandle = if (isWindows) WindowsProcessApi.openProcess(pid) else null
            return RemoteProcess(pid, detectArch(), winHandle)
        }

        /**
         * Open a remote process by PID, returning null if not found.
         */
        @JvmStatic
        fun tryOpen(pid: Long): RemoteProcess? {
            val handle = ProcessHandle.of(pid)
            if (handle.isEmpty) return null
            val winHandle = if (isWindows) WindowsProcessApi.openProcess(pid) else null
            return RemoteProcess(pid, detectArch(), winHandle)
        }

        /**
         * List all visible processes.
         */
        @JvmStatic
        fun list(): List<ProcessInfo> {
            return ProcessHandle.allProcesses().map { handle ->
                ProcessInfo(
                    pid = handle.pid(),
                    name = handle.info().command()
                        .map { Path.of(it).fileName.toString() }
                        .orElse("unknown"),
                    isAlive = handle.isAlive,
                )
            }.toList()
        }

        /**
         * Find processes by name.
         */
        @JvmStatic
        fun findByName(name: String): List<RemoteProcess> {
            return ProcessHandle.allProcesses()
                .filter { handle ->
                    handle.info().command()
                        .map { Path.of(it).fileName.toString() }
                        .orElse("")
                        .contains(name)
                }
                .map { RemoteProcess(it.pid(), detectArch()) }
                .toList()
        }

        private fun parseMapLine(line: String): MemoryMapping? {
            val parts = line.trim().split("\\s+".toRegex())
            if (parts.size < 5) return null
            val addrs = parts[0].split("-")
            if (addrs.size != 2) return null
            val start = addrs[0].toLongOrNull(16) ?: return null
            val end = addrs[1].toLongOrNull(16) ?: return null
            val perms = parts[1]
            val offset = parts[2].toLongOrNull(16) ?: 0
            val device = parts[3]
            val inode = parts[4].toLongOrNull() ?: 0
            val path = if (parts.size >= 6) parts[5] else null
            return MemoryMapping(start, end, perms, offset, device, inode, path)
        }

        private fun detectArch(): Architecture {
            val osArch = System.getProperty("os.arch").lowercase()
            return when {
                osArch.contains("amd64") || osArch.contains("x86_64") -> {
                    if (isLinux) Architecture.X86_64_LINUX
                    else if (isWindows) Architecture.X86_64_WINDOWS
                    else Architecture.X86_64_LINUX
                }
                osArch.contains("aarch64") -> Architecture(ArchType.AARCH64)
                else -> Architecture.X86_64_LINUX
            }
        }
    }

    /**
     * Basic process information returned by [list].
     */
    data class ProcessInfo(
        val pid: Long,
        val name: String,
        val isAlive: Boolean,
    )
}

/**
 * Windows kernel32 FFM bindings for remote process operations.
 */
private object WindowsProcessApi {
    private val linker = Linker.nativeLinker()
    private val kernel32 by lazy { SymbolLookup.libraryLookup("kernel32", Arena.global()) }

    private val openProcessFn by lazy {
        linker.downcallHandle(
            kernel32.find("OpenProcess").orElseThrow(),
            FunctionDescriptor.of(ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT)
        )
    }

    private val closeHandleFn by lazy {
        linker.downcallHandle(
            kernel32.find("CloseHandle").orElseThrow(),
            FunctionDescriptor.of(JAVA_INT, ADDRESS)
        )
    }

    private val readProcessMemoryFn by lazy {
        linker.downcallHandle(
            kernel32.find("ReadProcessMemory").orElseThrow(),
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, JAVA_LONG, ADDRESS)
        )
    }

    private val writeProcessMemoryFn by lazy {
        linker.downcallHandle(
            kernel32.find("WriteProcessMemory").orElseThrow(),
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, JAVA_LONG, ADDRESS)
        )
    }

    private val enumProcessModulesFn by lazy {
        linker.downcallHandle(
            kernel32.find("K32EnumProcessModules").orElseThrow(),
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, ADDRESS)
        )
    }

    private val getModuleFileNameExWFn by lazy {
        linker.downcallHandle(
            kernel32.find("K32GetModuleFileNameExW").orElseThrow(),
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, JAVA_INT)
        )
    }

    private val getModuleInformationFn by lazy {
        linker.downcallHandle(
            kernel32.find("K32GetModuleInformation").orElseThrow(),
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, JAVA_INT)
        )
    }

    private const val PROCESS_VM_READ = 0x0010
    private const val PROCESS_VM_WRITE = 0x0020
    private const val PROCESS_VM_OPERATION = 0x0008
    private const val PROCESS_QUERY_INFORMATION = 0x0400

    fun openProcess(pid: Long): MemorySegment? {
        val access = PROCESS_VM_READ or PROCESS_VM_WRITE or PROCESS_VM_OPERATION or PROCESS_QUERY_INFORMATION
        val handle = openProcessFn.invoke(access, 0, pid.toInt()) as MemorySegment
        if (handle.address() == 0L) return null
        return handle
    }

    fun closeHandle(handle: MemorySegment) {
        closeHandleFn.invoke(handle)
    }

    fun readMemory(handle: MemorySegment, address: Long, length: Int): ByteArray {
        Arena.ofConfined().use { arena ->
            val buffer = arena.allocate(length.toLong())
            val bytesRead = arena.allocate(JAVA_LONG)
            val ok = readProcessMemoryFn.invoke(
                handle,
                MemorySegment.ofAddress(address),
                buffer,
                length.toLong(),
                bytesRead
            ) as Int
            if (ok == 0) {
                throw IllegalStateException("ReadProcessMemory failed for address 0x${address.toString(16)}")
            }
            val read = bytesRead.get(JAVA_LONG, 0).toInt()
            return buffer.asSlice(0, read.toLong()).toArray(JAVA_BYTE)
        }
    }

    fun writeMemory(handle: MemorySegment, address: Long, data: ByteArray) {
        Arena.ofConfined().use { arena ->
            val buffer = arena.allocate(data.size.toLong())
            buffer.copyFrom(MemorySegment.ofArray(data))
            val bytesWritten = arena.allocate(JAVA_LONG)
            val ok = writeProcessMemoryFn.invoke(
                handle,
                MemorySegment.ofAddress(address),
                buffer,
                data.size.toLong(),
                bytesWritten
            ) as Int
            if (ok == 0) {
                throw IllegalStateException("WriteProcessMemory failed for address 0x${address.toString(16)}")
            }
        }
    }

    fun enumerateModules(handle: MemorySegment): List<RemoteProcess.MemoryMapping> {
        Arena.ofConfined().use { arena ->
            val cbNeeded = arena.allocate(JAVA_INT)
            val initialSlots = 256
            var hModArray = arena.allocate(ADDRESS, initialSlots.toLong())
            var ok = enumProcessModulesFn.invoke(
                handle, hModArray, (initialSlots * ADDRESS.byteSize()).toInt(), cbNeeded
            ) as Int
            if (ok == 0) return emptyList()

            val needed = cbNeeded.get(JAVA_INT, 0)
            val count = needed / ADDRESS.byteSize().toInt()

            if (needed > initialSlots * ADDRESS.byteSize().toInt()) {
                hModArray = arena.allocate(ADDRESS, count.toLong())
                ok = enumProcessModulesFn.invoke(handle, hModArray, needed, cbNeeded) as Int
                if (ok == 0) return emptyList()
            }

            val result = mutableListOf<RemoteProcess.MemoryMapping>()
            for (i in 0 until count) {
                val hModule = hModArray.getAtIndex(ADDRESS, i.toLong())
                val mapping = buildMapping(handle, hModule, arena)
                if (mapping != null) {
                    result.add(mapping)
                }
            }
            return result
        }
    }

    private fun buildMapping(
        handle: MemorySegment, hModule: MemorySegment, arena: Arena
    ): RemoteProcess.MemoryMapping? {
        // Get module path
        val maxPath = 260
        val pathBuf = arena.allocate(JAVA_CHAR, maxPath.toLong())
        val pathLen = getModuleFileNameExWFn.invoke(handle, hModule, pathBuf, maxPath) as Int
        val path = if (pathLen > 0) {
            val bytes = pathBuf.asSlice(0, pathLen.toLong() * 2).toArray(JAVA_BYTE)
            String(bytes, java.nio.charset.StandardCharsets.UTF_16LE)
        } else {
            null
        }

        // Get module info (base address + size): MODULEINFO struct = 3 pointers (24 bytes on 64-bit)
        val moduleInfoSize = 3 * ADDRESS.byteSize().toInt()
        val moduleInfo = arena.allocate(moduleInfoSize.toLong())
        val ok = getModuleInformationFn.invoke(handle, hModule, moduleInfo, moduleInfoSize) as Int
        if (ok == 0) return null

        val baseAddress = moduleInfo.get(ADDRESS, 0).address()
        // MODULEINFO: lpBaseOfDll (ptr), SizeOfImage (DWORD at offset 8), EntryPoint (ptr)
        val sizeOfImage = moduleInfo.get(JAVA_INT, 8).toLong() and 0xFFFFFFFFL

        return RemoteProcess.MemoryMapping(
            startAddress = baseAddress,
            endAddress = baseAddress + sizeOfImage,
            permissions = "r-xp",
            offset = 0,
            device = "00:00",
            inode = 0,
            path = path,
        )
    }
}
