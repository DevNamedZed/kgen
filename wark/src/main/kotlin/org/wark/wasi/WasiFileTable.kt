package org.wark.wasi

import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.Path

/**
 * Manages WASI file descriptors. Tracks open files, pre-opened directories,
 * and standard I/O streams.
 *
 * File descriptor layout:
 * - 0: stdin
 * - 1: stdout
 * - 2: stderr
 * - 3+: pre-opened directories and opened files
 */
class WasiFileTable(
    private val stdout: OutputStream,
    private val stderr: OutputStream,
) {

    private val entries = mutableMapOf<Int, WasiFileEntry>()
    private var nextDescriptor = 3

    fun addPreopenedDirectory(path: Path, mountPath: String): Int {
        val descriptor = nextDescriptor++
        entries[descriptor] = WasiFileEntry.PreopenedDirectory(path, mountPath)
        return descriptor
    }

    fun openFile(directoryFd: Int, relativePath: String, flags: Int): OpenResult {
        val dirEntry = entries[directoryFd]
        if (dirEntry !is WasiFileEntry.PreopenedDirectory) {
            return OpenResult.Error(WasiErrno.BADF)
        }

        val normalizedPath = normalizePath(relativePath)
        val shouldCreate = (flags and WasiErrno.OFLAG_CREAT) != 0
        val shouldTruncate = (flags and WasiErrno.OFLAG_TRUNC) != 0

        val resolvedPath = if (shouldCreate) {
            resolveForCreate(dirEntry.hostPath, normalizedPath)
        } else {
            resolveWithFallback(dirEntry.hostPath, normalizedPath)
        } ?: return OpenResult.Error(WasiErrno.NOENT)

        if (!Files.exists(resolvedPath) && !shouldCreate) {
            return OpenResult.Error(WasiErrno.NOENT)
        }

        return try {
            val mode = if (shouldCreate || shouldTruncate) "rw" else "r"
            if (shouldCreate) {
                val parent = resolvedPath.parent
                if (parent != null && !Files.exists(parent)) {
                    Files.createDirectories(parent)
                }
            }
            val randomAccessFile = RandomAccessFile(resolvedPath.toFile(), mode)
            if (shouldTruncate) {
                randomAccessFile.setLength(0)
            }
            val descriptor = nextDescriptor++
            entries[descriptor] = WasiFileEntry.OpenFile(randomAccessFile, relativePath, resolvedPath)
            OpenResult.Success(descriptor)
        } catch (exception: Exception) {
            OpenResult.Error(WasiErrno.NOENT)
        }
    }

    fun close(descriptor: Int): Int {
        val entry = entries.remove(descriptor)
        if (entry is WasiFileEntry.OpenFile) {
            entry.file.close()
            return WasiErrno.SUCCESS
        }
        return if (entry != null) WasiErrno.SUCCESS else WasiErrno.BADF
    }

    fun read(descriptor: Int, buffer: ByteArray): Int {
        val entry = entries[descriptor] as? WasiFileEntry.OpenFile ?: return -1
        return entry.file.read(buffer)
    }

    fun write(descriptor: Int, data: ByteArray): Int {
        return when (descriptor) {
            1 -> { stdout.write(data); stdout.flush(); data.size }
            2 -> { stderr.write(data); stderr.flush(); data.size }
            else -> {
                val entry = entries[descriptor] as? WasiFileEntry.OpenFile ?: return -1
                entry.file.write(data)
                data.size
            }
        }
    }

    fun seek(descriptor: Int, offset: Long, whence: Int): Long {
        val entry = entries[descriptor] as? WasiFileEntry.OpenFile ?: return -1
        val newPosition = when (whence) {
            WasiErrno.WHENCE_SET -> offset
            WasiErrno.WHENCE_CUR -> entry.file.filePointer + offset
            WasiErrno.WHENCE_END -> entry.file.length() + offset
            else -> entry.file.filePointer
        }
        entry.file.seek(newPosition)
        return newPosition
    }

    fun tell(descriptor: Int): Long {
        val entry = entries[descriptor] as? WasiFileEntry.OpenFile ?: return -1
        return entry.file.filePointer
    }

    fun fileSize(descriptor: Int): Long {
        val entry = entries[descriptor] as? WasiFileEntry.OpenFile ?: return -1
        return entry.file.length()
    }

    fun fileType(descriptor: Int): Byte {
        return when {
            descriptor in 0..2 -> WasiErrno.FILETYPE_CHARACTER_DEVICE
            entries[descriptor] is WasiFileEntry.PreopenedDirectory -> WasiErrno.FILETYPE_DIRECTORY
            entries[descriptor] is WasiFileEntry.OpenFile -> WasiErrno.FILETYPE_REGULAR_FILE
            else -> WasiErrno.FILETYPE_UNKNOWN
        }
    }

    fun isValid(descriptor: Int): Boolean = descriptor in 0..2 || entries.containsKey(descriptor)

    fun preopenedDirectory(descriptor: Int): WasiFileEntry.PreopenedDirectory? {
        return entries[descriptor] as? WasiFileEntry.PreopenedDirectory
    }

    fun preopenedDirectoryCount(): Int {
        return entries.values.count { it is WasiFileEntry.PreopenedDirectory }
    }

    fun resolveDirectoryPath(directoryFd: Int, relativePath: String): Path? {
        val dirEntry = entries[directoryFd] as? WasiFileEntry.PreopenedDirectory ?: return null
        val resolvedPath = dirEntry.hostPath.resolve(relativePath).normalize()
        return if (resolvedPath.startsWith(dirEntry.hostPath)) resolvedPath else null
    }

    fun closeAll() {
        for (entry in entries.values) {
            if (entry is WasiFileEntry.OpenFile) {
                entry.file.close()
            }
        }
        entries.clear()
    }

    private fun resolveForCreate(hostPath: Path, normalizedPath: String): Path? {
        val absoluteHost = hostPath.toAbsolutePath().normalize()
        val directPath = absoluteHost.resolve(normalizedPath).normalize()
        if (directPath.startsWith(absoluteHost)) {
            return directPath
        }
        val slashIndex = normalizedPath.indexOf('/')
        if (slashIndex > 0) {
            val withoutFirstComponent = normalizedPath.substring(slashIndex + 1)
            val fallbackPath = absoluteHost.resolve(withoutFirstComponent).normalize()
            if (fallbackPath.startsWith(absoluteHost)) {
                return fallbackPath
            }
        }
        return null
    }

    private fun resolveWithFallback(hostPath: Path, normalizedPath: String): Path? {
        val absoluteHost = hostPath.toAbsolutePath().normalize()
        val directPath = absoluteHost.resolve(normalizedPath).normalize()
        if (directPath.startsWith(absoluteHost) && Files.exists(directPath)) {
            return directPath
        }
        val slashIndex = normalizedPath.indexOf('/')
        if (slashIndex > 0) {
            val withoutFirstComponent = normalizedPath.substring(slashIndex + 1)
            val fallbackPath = absoluteHost.resolve(withoutFirstComponent).normalize()
            if (fallbackPath.startsWith(absoluteHost) && Files.exists(fallbackPath)) {
                return fallbackPath
            }
        }
        if (directPath.startsWith(absoluteHost)) {
            return directPath
        }
        return null
    }

    private fun normalizePath(path: String): String {
        var result = path
        if (result.startsWith("./")) {
            result = result.substring(2)
        }
        if (result.startsWith("/")) {
            result = result.substring(1)
        }
        return result
    }

    sealed interface OpenResult {
        data class Success(val descriptor: Int) : OpenResult
        data class Error(val errno: Int) : OpenResult
    }
}

sealed interface WasiFileEntry {
    data class PreopenedDirectory(
        val hostPath: Path,
        val mountPath: String,
    ) : WasiFileEntry

    data class OpenFile(
        val file: RandomAccessFile,
        val path: String,
        val hostPath: Path,
    ) : WasiFileEntry
}
