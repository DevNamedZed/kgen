package org.wark.wasi

import org.wark.HostFunction
import org.wark.WarkImports
import java.nio.file.Files

/**
 * WASI Preview 1 file descriptor and path operations. Provides all fd_* and path_*
 * host functions backed by a real filesystem via [WasiFileTable].
 */
class WasiFileOperations(private val fileTable: WasiFileTable) {

    fun registerImports(builder: WarkImports.Builder) {
        builder.function("wasi_snapshot_preview1", "fd_advise", fdAdvise())
        builder.function("wasi_snapshot_preview1", "fd_allocate", fdAllocate())
        builder.function("wasi_snapshot_preview1", "fd_close", fdClose())
        builder.function("wasi_snapshot_preview1", "fd_datasync", fdDatasync())
        builder.function("wasi_snapshot_preview1", "fd_fdstat_get", fdFdstatGet())
        builder.function("wasi_snapshot_preview1", "fd_fdstat_set_flags", fdFdstatSetFlags())
        builder.function("wasi_snapshot_preview1", "fd_fdstat_set_rights", fdFdstatSetRights())
        builder.function("wasi_snapshot_preview1", "fd_filestat_get", fdFilestatGet())
        builder.function("wasi_snapshot_preview1", "fd_filestat_set_size", fdFilestatSetSize())
        builder.function("wasi_snapshot_preview1", "fd_filestat_set_times", fdFilestatSetTimes())
        builder.function("wasi_snapshot_preview1", "fd_pread", fdPread())
        builder.function("wasi_snapshot_preview1", "fd_prestat_get", fdPrestatGet())
        builder.function("wasi_snapshot_preview1", "fd_prestat_dir_name", fdPrestatDirName())
        builder.function("wasi_snapshot_preview1", "fd_read", fdRead())
        builder.function("wasi_snapshot_preview1", "fd_readdir", fdReaddir())
        builder.function("wasi_snapshot_preview1", "fd_renumber", fdRenumber())
        builder.function("wasi_snapshot_preview1", "fd_seek", fdSeek())
        builder.function("wasi_snapshot_preview1", "fd_sync", fdSync())
        builder.function("wasi_snapshot_preview1", "fd_tell", fdTell())
        builder.function("wasi_snapshot_preview1", "fd_write", fdWrite())
        builder.function("wasi_snapshot_preview1", "fd_pwrite", fdPwrite())
        builder.function("wasi_snapshot_preview1", "path_create_directory", pathCreateDirectory())
        builder.function("wasi_snapshot_preview1", "path_filestat_get", pathFilestatGet())
        builder.function("wasi_snapshot_preview1", "path_filestat_set_times", pathFilestatSetTimes())
        builder.function("wasi_snapshot_preview1", "path_link", pathLink())
        builder.function("wasi_snapshot_preview1", "path_open", pathOpen())
        builder.function("wasi_snapshot_preview1", "path_readlink", pathReadlink())
        builder.function("wasi_snapshot_preview1", "path_remove_directory", pathRemoveDirectory())
        builder.function("wasi_snapshot_preview1", "path_rename", pathRename())
        builder.function("wasi_snapshot_preview1", "path_symlink", pathSymlink())
        builder.function("wasi_snapshot_preview1", "path_unlink_file", pathUnlinkFile())
    }

    private fun fdAdvise(): HostFunction = HostFunction { _, _ ->
        longArrayOf(WasiErrno.SUCCESS.toLong())
    }

    private fun fdAllocate(): HostFunction = HostFunction { _, _ ->
        longArrayOf(WasiErrno.NOTSUP.toLong())
    }

    private fun fdClose(): HostFunction = HostFunction { _, args ->
        longArrayOf(fileTable.close(args[0].toInt()).toLong())
    }

    private fun fdDatasync(): HostFunction = HostFunction { _, _ ->
        longArrayOf(WasiErrno.SUCCESS.toLong())
    }

    private fun fdFdstatGet(): HostFunction = HostFunction { instance, args ->
        val descriptor = args[0].toInt()
        val statAddress = args[1].toInt()
        val memory = instance.memory()

        if (!fileTable.isValid(descriptor)) {
            longArrayOf(WasiErrno.BADF.toLong())
        } else {
            memory.writeByte(statAddress, fileTable.fileType(descriptor))
            memory.writeByte(statAddress + 1, 0)
            memory.writeByte(statAddress + 2, 0)
            memory.writeByte(statAddress + 3, 0)
            memory.writeI32(statAddress + 4, 0)
            memory.writeI64(statAddress + 8, WasiErrno.RIGHTS_ALL)
            memory.writeI64(statAddress + 16, WasiErrno.RIGHTS_ALL)
            longArrayOf(WasiErrno.SUCCESS.toLong())
        }
    }

    private fun fdFdstatSetFlags(): HostFunction = HostFunction { _, _ ->
        longArrayOf(WasiErrno.SUCCESS.toLong())
    }

    private fun fdFdstatSetRights(): HostFunction = HostFunction { _, _ ->
        longArrayOf(WasiErrno.SUCCESS.toLong())
    }

    private fun fdFilestatGet(): HostFunction = HostFunction { instance, args ->
        val descriptor = args[0].toInt()
        val statAddress = args[1].toInt()
        val memory = instance.memory()

        if (!fileTable.isValid(descriptor)) {
            longArrayOf(WasiErrno.BADF.toLong())
        } else {
            memory.writeI64(statAddress, 0)
            memory.writeI64(statAddress + 8, 0)
            memory.writeByte(statAddress + 16, fileTable.fileType(descriptor))
            memory.writeI32(statAddress + 20, 1)
            memory.writeI64(statAddress + 24, fileTable.fileSize(descriptor).coerceAtLeast(0))
            memory.writeI64(statAddress + 32, 0)
            memory.writeI64(statAddress + 40, 0)
            memory.writeI64(statAddress + 48, 0)
            memory.writeI64(statAddress + 56, 0)
            longArrayOf(WasiErrno.SUCCESS.toLong())
        }
    }

    private fun fdFilestatSetSize(): HostFunction = HostFunction { _, _ ->
        longArrayOf(WasiErrno.NOTSUP.toLong())
    }

    private fun fdFilestatSetTimes(): HostFunction = HostFunction { _, _ ->
        longArrayOf(WasiErrno.SUCCESS.toLong())
    }

    private fun fdPread(): HostFunction = HostFunction { _, _ ->
        longArrayOf(WasiErrno.NOTSUP.toLong())
    }

    private fun fdPrestatGet(): HostFunction = HostFunction { instance, args ->
        val descriptor = args[0].toInt()
        val prestatAddress = args[1].toInt()

        val preopen = fileTable.preopenedDirectory(descriptor)
        if (preopen != null) {
            val memory = instance.memory()
            val pathBytes = preopen.mountPath.toByteArray(Charsets.UTF_8)
            memory.writeI32(prestatAddress, WasiErrno.PREOPENTYPE_DIR)
            memory.writeI32(prestatAddress + 4, pathBytes.size)
            longArrayOf(WasiErrno.SUCCESS.toLong())
        } else {
            longArrayOf(WasiErrno.BADF.toLong())
        }
    }

    private fun fdPrestatDirName(): HostFunction = HostFunction { instance, args ->
        val descriptor = args[0].toInt()
        val pathAddress = args[1].toInt()

        val preopen = fileTable.preopenedDirectory(descriptor)
        if (preopen != null) {
            val memory = instance.memory()
            val pathBytes = preopen.mountPath.toByteArray(Charsets.UTF_8)
            memory.writeBytes(pathAddress, pathBytes)
            longArrayOf(WasiErrno.SUCCESS.toLong())
        } else {
            longArrayOf(WasiErrno.BADF.toLong())
        }
    }

    private fun fdRead(): HostFunction = HostFunction { instance, args ->
        val descriptor = args[0].toInt()
        val iovecAddress = args[1].toInt()
        val iovecCount = args[2].toInt()
        val bytesReadAddress = args[3].toInt()
        val memory = instance.memory()

        if (!fileTable.isValid(descriptor) || descriptor < 3) {
            memory.writeI32(bytesReadAddress, 0)
            longArrayOf(WasiErrno.BADF.toLong())
        } else {
            var totalRead = 0
            for (index in 0 until iovecCount) {
                val iovecOffset = iovecAddress + index * 8
                val bufferAddress = memory.readI32(iovecOffset)
                val bufferLength = memory.readI32(iovecOffset + 4)
                if (bufferLength > 0 && bufferAddress >= 0 && bufferAddress + bufferLength <= memory.sizeBytes()) {
                    val buffer = ByteArray(bufferLength)
                    val bytesRead = fileTable.read(descriptor, buffer)
                    if (bytesRead > 0) {
                        memory.writeBytes(bufferAddress, buffer.copyOf(bytesRead))
                        totalRead += bytesRead
                    }
                    if (bytesRead < bufferLength) {
                        break
                    }
                } else if (bufferAddress < 0 || bufferAddress + bufferLength > memory.sizeBytes()) {
                    memory.writeI32(bytesReadAddress, totalRead)
                    return@HostFunction longArrayOf(WasiErrno.FAULT.toLong())
                }
            }
            memory.writeI32(bytesReadAddress, totalRead)
            longArrayOf(WasiErrno.SUCCESS.toLong())
        }
    }

    private fun fdReaddir(): HostFunction = HostFunction { _, _ ->
        longArrayOf(WasiErrno.NOTSUP.toLong())
    }

    private fun fdRenumber(): HostFunction = HostFunction { _, _ ->
        longArrayOf(WasiErrno.NOTSUP.toLong())
    }

    private fun fdSeek(): HostFunction = HostFunction { instance, args ->
        val descriptor = args[0].toInt()
        val offset = args[1]
        val whence = args[2].toInt()
        val newOffsetAddress = args[3].toInt()
        val memory = instance.memory()

        val newPosition = fileTable.seek(descriptor, offset, whence)
        if (newPosition < 0) {
            longArrayOf(WasiErrno.BADF.toLong())
        } else {
            memory.writeI64(newOffsetAddress, newPosition)
            longArrayOf(WasiErrno.SUCCESS.toLong())
        }
    }

    private fun fdSync(): HostFunction = HostFunction { _, _ ->
        longArrayOf(WasiErrno.SUCCESS.toLong())
    }

    private fun fdTell(): HostFunction = HostFunction { instance, args ->
        val descriptor = args[0].toInt()
        val offsetAddress = args[1].toInt()
        val memory = instance.memory()

        val position = fileTable.tell(descriptor)
        if (position < 0) {
            longArrayOf(WasiErrno.BADF.toLong())
        } else {
            memory.writeI64(offsetAddress, position)
            longArrayOf(WasiErrno.SUCCESS.toLong())
        }
    }

    private fun fdWrite(): HostFunction = HostFunction { instance, args ->
        val descriptor = args[0].toInt()
        val iovecAddress = args[1].toInt()
        val iovecCount = args[2].toInt()
        val bytesWrittenAddress = args[3].toInt()
        val memory = instance.memory()

        var totalWritten = 0
        for (index in 0 until iovecCount) {
            val iovecOffset = iovecAddress + index * 8
            val bufferAddress = memory.readI32(iovecOffset)
            val bufferLength = memory.readI32(iovecOffset + 4)
            if (bufferLength > 0) {
                val bytes = memory.readBytes(bufferAddress, bufferLength)
                val written = fileTable.write(descriptor, bytes)
                if (written < 0) {
                    memory.writeI32(bytesWrittenAddress, totalWritten)
                    return@HostFunction longArrayOf(WasiErrno.BADF.toLong())
                }
                totalWritten += written
            }
        }
        memory.writeI32(bytesWrittenAddress, totalWritten)
        longArrayOf(WasiErrno.SUCCESS.toLong())
    }

    private fun fdPwrite(): HostFunction = HostFunction { _, _ ->
        longArrayOf(WasiErrno.NOTSUP.toLong())
    }

    private fun pathOpen(): HostFunction = HostFunction { instance, args ->
        val directoryFd = args[0].toInt()
        val pathAddress = args[2].toInt()
        val pathLength = args[3].toInt()
        val openFlags = args[4].toInt()
        val resultFdAddress = args[8].toInt()
        val memory = instance.memory()

        val pathBytes = memory.readBytes(pathAddress, pathLength)
        val relativePath = String(pathBytes, Charsets.UTF_8)

        when (val result = fileTable.openFile(directoryFd, relativePath, openFlags)) {
            is WasiFileTable.OpenResult.Success -> {
                memory.writeI32(resultFdAddress, result.descriptor)
                longArrayOf(WasiErrno.SUCCESS.toLong())
            }
            is WasiFileTable.OpenResult.Error -> {
                longArrayOf(result.errno.toLong())
            }
        }
    }

    private fun pathCreateDirectory(): HostFunction = HostFunction { instance, args ->
        val directoryFd = args[0].toInt()
        val pathAddress = args[1].toInt()
        val pathLength = args[2].toInt()
        val memory = instance.memory()

        val pathBytes = memory.readBytes(pathAddress, pathLength)
        val relativePath = String(pathBytes, Charsets.UTF_8)
        val resolvedPath = fileTable.resolveDirectoryPath(directoryFd, relativePath)

        if (resolvedPath != null) {
            try {
                Files.createDirectories(resolvedPath)
                longArrayOf(WasiErrno.SUCCESS.toLong())
            } catch (exception: Exception) {
                longArrayOf(WasiErrno.IO.toLong())
            }
        } else {
            longArrayOf(WasiErrno.BADF.toLong())
        }
    }

    private fun pathFilestatGet(): HostFunction = HostFunction { instance, args ->
        val directoryFd = args[0].toInt()
        val pathAddress = args[2].toInt()
        val pathLength = args[3].toInt()
        val statAddress = args[4].toInt()
        val memory = instance.memory()

        val pathBytes = memory.readBytes(pathAddress, pathLength)
        val relativePath = String(pathBytes, Charsets.UTF_8)
        val resolvedPath = fileTable.resolveDirectoryPath(directoryFd, relativePath)

        if (resolvedPath != null && Files.exists(resolvedPath)) {
            val size = if (Files.isRegularFile(resolvedPath)) Files.size(resolvedPath) else 0L
            val fileType = if (Files.isDirectory(resolvedPath)) {
                WasiErrno.FILETYPE_DIRECTORY
            } else {
                WasiErrno.FILETYPE_REGULAR_FILE
            }
            memory.writeI64(statAddress, 0)
            memory.writeI64(statAddress + 8, 0)
            memory.writeByte(statAddress + 16, fileType)
            memory.writeI32(statAddress + 20, 1)
            memory.writeI64(statAddress + 24, size)
            memory.writeI64(statAddress + 32, 0)
            memory.writeI64(statAddress + 40, 0)
            memory.writeI64(statAddress + 48, 0)
            memory.writeI64(statAddress + 56, 0)
            longArrayOf(WasiErrno.SUCCESS.toLong())
        } else {
            longArrayOf(WasiErrno.NOENT.toLong())
        }
    }

    private fun pathFilestatSetTimes(): HostFunction = HostFunction { _, _ ->
        longArrayOf(WasiErrno.SUCCESS.toLong())
    }

    private fun pathLink(): HostFunction = HostFunction { _, _ ->
        longArrayOf(WasiErrno.NOTSUP.toLong())
    }

    private fun pathReadlink(): HostFunction = HostFunction { _, _ ->
        longArrayOf(WasiErrno.NOTSUP.toLong())
    }

    private fun pathRemoveDirectory(): HostFunction = HostFunction { instance, args ->
        val directoryFd = args[0].toInt()
        val pathAddress = args[1].toInt()
        val pathLength = args[2].toInt()
        val memory = instance.memory()

        val pathBytes = memory.readBytes(pathAddress, pathLength)
        val relativePath = String(pathBytes, Charsets.UTF_8)
        val resolvedPath = fileTable.resolveDirectoryPath(directoryFd, relativePath)

        if (resolvedPath != null) {
            try {
                Files.deleteIfExists(resolvedPath)
                longArrayOf(WasiErrno.SUCCESS.toLong())
            } catch (exception: Exception) {
                longArrayOf(WasiErrno.IO.toLong())
            }
        } else {
            longArrayOf(WasiErrno.BADF.toLong())
        }
    }

    private fun pathRename(): HostFunction = HostFunction { instance, args ->
        val oldDirFd = args[0].toInt()
        val oldPathAddress = args[1].toInt()
        val oldPathLength = args[2].toInt()
        val newDirFd = args[3].toInt()
        val newPathAddress = args[4].toInt()
        val newPathLength = args[5].toInt()
        val memory = instance.memory()

        val oldPath = String(memory.readBytes(oldPathAddress, oldPathLength), Charsets.UTF_8)
        val newPath = String(memory.readBytes(newPathAddress, newPathLength), Charsets.UTF_8)
        val resolvedOld = fileTable.resolveDirectoryPath(oldDirFd, oldPath)
        val resolvedNew = fileTable.resolveDirectoryPath(newDirFd, newPath)

        if (resolvedOld != null && resolvedNew != null) {
            try {
                Files.move(resolvedOld, resolvedNew)
                longArrayOf(WasiErrno.SUCCESS.toLong())
            } catch (exception: Exception) {
                longArrayOf(WasiErrno.IO.toLong())
            }
        } else {
            longArrayOf(WasiErrno.BADF.toLong())
        }
    }

    private fun pathSymlink(): HostFunction = HostFunction { _, _ ->
        longArrayOf(WasiErrno.NOTSUP.toLong())
    }

    private fun pathUnlinkFile(): HostFunction = HostFunction { instance, args ->
        val directoryFd = args[0].toInt()
        val pathAddress = args[1].toInt()
        val pathLength = args[2].toInt()
        val memory = instance.memory()

        val pathBytes = memory.readBytes(pathAddress, pathLength)
        val relativePath = String(pathBytes, Charsets.UTF_8)
        val resolvedPath = fileTable.resolveDirectoryPath(directoryFd, relativePath)

        if (resolvedPath != null) {
            try {
                Files.deleteIfExists(resolvedPath)
                longArrayOf(WasiErrno.SUCCESS.toLong())
            } catch (exception: Exception) {
                longArrayOf(WasiErrno.IO.toLong())
            }
        } else {
            longArrayOf(WasiErrno.BADF.toLong())
        }
    }
}
