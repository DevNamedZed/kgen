package org.kgen.reflect

import java.lang.foreign.*
import java.lang.foreign.ValueLayout.*

/**
 * Low-level native memory operations via FFM.
 *
 * Allocate executable memory, read/write bytes at arbitrary addresses,
 * change memory protection. Works on Linux, macOS, and Windows.
 *
 * ```java
 * var mem = NativeMemory.allocateExecutable(4096);
 * mem.write(0, machineCode);
 * // ... call the code ...
 * mem.close();
 * ```
 */
class NativeMemory private constructor(
    val address: Long,
    val size: Long,
    private val segment: MemorySegment,
    private val arena: Arena,
) : AutoCloseable {

    fun read(offset: Long, length: Int): ByteArray {
        val bytes = ByteArray(length)
        MemorySegment.copy(segment, JAVA_BYTE, offset, bytes, 0, length)
        return bytes
    }

    fun write(offset: Long, data: ByteArray) {
        MemorySegment.copy(data, 0, segment, JAVA_BYTE, offset, data.size)
    }

    fun readByte(offset: Long): Byte = segment.get(JAVA_BYTE, offset)
    fun readInt(offset: Long): Int = segment.get(JAVA_INT_UNALIGNED, offset)
    fun readLong(offset: Long): Long = segment.get(JAVA_LONG_UNALIGNED, offset)

    fun writeByte(offset: Long, value: Byte) = segment.set(JAVA_BYTE, offset, value)
    fun writeInt(offset: Long, value: Int) = segment.set(JAVA_INT_UNALIGNED, offset, value)
    fun writeLong(offset: Long, value: Long) = segment.set(JAVA_LONG_UNALIGNED, offset, value)

    override fun close() {
        arena.close()
    }

    companion object {
        private val linker = Linker.nativeLinker()
        private val nativeLookup = linker.defaultLookup()
        private val isWindows = System.getProperty("os.name").lowercase().contains("win")

        /**
         * Allocate a region of executable memory (RWX).
         * The returned [NativeMemory] must be closed when no longer needed.
         */
        @JvmStatic
        fun allocateExecutable(size: Long): NativeMemory {
            val arena = Arena.ofConfined()
            val segment = if (isWindows) virtualAllocRwx(size, arena) else mmapRwx(size, arena)
            return NativeMemory(segment.address(), size, segment, arena)
        }

        /**
         * Allocate a region of read-write memory (no execute permission).
         * Suitable for heap data. The returned [NativeMemory] must be closed when no longer needed.
         */
        @JvmStatic
        fun allocateReadWrite(size: Long): NativeMemory {
            val arena = Arena.ofConfined()
            val segment = arena.allocate(size, 8)
            return NativeMemory(segment.address(), size, segment, arena)
        }

        /**
         * Create a read-only view of existing memory at [address] for [size] bytes.
         * No cleanup is performed on close — the caller is responsible for the memory.
         */
        @JvmStatic
        fun viewAt(address: Long, size: Long): NativeMemory {
            val arena = Arena.ofConfined()
            val segment = MemorySegment.ofAddress(address).reinterpret(size, arena) { }
            return NativeMemory(address, size, segment, arena)
        }

        /**
         * Read [length] bytes from an arbitrary memory address.
         */
        @JvmStatic
        fun readBytes(address: Long, length: Int): ByteArray {
            val seg = MemorySegment.ofAddress(address).reinterpret(length.toLong())
            val bytes = ByteArray(length)
            MemorySegment.copy(seg, JAVA_BYTE, 0, bytes, 0, length)
            return bytes
        }

        /**
         * Write [data] to an arbitrary memory address. The memory must be writable.
         */
        @JvmStatic
        fun writeBytes(address: Long, data: ByteArray) {
            val seg = MemorySegment.ofAddress(address).reinterpret(data.size.toLong())
            MemorySegment.copy(data, 0, seg, JAVA_BYTE, 0, data.size)
        }

        private fun mmapRwx(size: Long, arena: Arena): MemorySegment {
            val mmap = linker.downcallHandle(
                nativeLookup.find("mmap").orElseThrow(),
                FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_LONG, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_LONG)
            )
            val munmap = linker.downcallHandle(
                nativeLookup.find("munmap").orElseThrow(),
                FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_LONG)
            )

            val result = mmap.invoke(
                MemorySegment.NULL, size,
                0x1 or 0x2 or 0x4,  // PROT_READ | PROT_WRITE | PROT_EXEC
                0x02 or 0x20,       // MAP_PRIVATE | MAP_ANONYMOUS
                -1, 0L
            ) as MemorySegment

            if (result.address() == -1L) throw RuntimeException("mmap failed")

            val addr = result.address()
            return result.reinterpret(size, arena) { _ ->
                munmap.invoke(MemorySegment.ofAddress(addr).reinterpret(size), size)
            }
        }

        private val kernel32 by lazy {
            SymbolLookup.libraryLookup("kernel32", Arena.global())
        }

        private fun virtualAllocRwx(size: Long, arena: Arena): MemorySegment {
            val virtualAlloc = linker.downcallHandle(
                kernel32.find("VirtualAlloc").orElseThrow(),
                FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_LONG, JAVA_INT, JAVA_INT)
            )
            val virtualFree = linker.downcallHandle(
                kernel32.find("VirtualFree").orElseThrow(),
                FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_LONG, JAVA_INT)
            )

            val result = virtualAlloc.invoke(
                MemorySegment.NULL, size,
                0x1000 or 0x2000,  // MEM_COMMIT | MEM_RESERVE
                0x40               // PAGE_EXECUTE_READWRITE
            ) as MemorySegment

            if (result.address() == 0L) throw RuntimeException("VirtualAlloc failed")

            val addr = result.address()
            return result.reinterpret(size, arena) { _ ->
                virtualFree.invoke(MemorySegment.ofAddress(addr).reinterpret(size), 0L, 0x8000)
            }
        }

        /**
         * Change memory protection on a region. Linux/macOS only.
         * [prot] is a combination of PROT_READ (1), PROT_WRITE (2), PROT_EXEC (4).
         */
        @JvmStatic
        fun mprotect(address: Long, size: Long, prot: Int) {
            if (isWindows) {
                mprotectWindows(address, size, prot)
            } else {
                mprotectUnix(address, size, prot)
            }
        }

        private fun mprotectUnix(address: Long, size: Long, prot: Int) {
            val handle = linker.downcallHandle(
                nativeLookup.find("mprotect").orElseThrow(),
                FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_LONG, JAVA_INT)
            )
            val pageSize = 4096L
            val alignedAddr = address and (pageSize - 1).inv()
            val alignedSize = (address - alignedAddr) + size
            val result = handle.invoke(
                MemorySegment.ofAddress(alignedAddr).reinterpret(alignedSize), alignedSize, prot
            ) as Int
            if (result != 0) throw RuntimeException("mprotect failed: $result")
        }

        private fun mprotectWindows(address: Long, size: Long, prot: Int) {
            val winProt = when {
                prot and 4 != 0 && prot and 2 != 0 -> 0x40 // PAGE_EXECUTE_READWRITE
                prot and 4 != 0 && prot and 1 != 0 -> 0x20 // PAGE_EXECUTE_READ
                prot and 4 != 0 -> 0x10                     // PAGE_EXECUTE
                prot and 2 != 0 -> 0x04                     // PAGE_READWRITE
                prot and 1 != 0 -> 0x02                     // PAGE_READONLY
                else -> 0x01                                 // PAGE_NOACCESS
            }
            val virtualProtect = linker.downcallHandle(
                kernel32.find("VirtualProtect").orElseThrow(),
                FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_LONG, JAVA_INT, ADDRESS)
            )
            val oldProtArena = Arena.ofConfined()
            val oldProtBuf = oldProtArena.allocate(JAVA_INT)
            val result = virtualProtect.invoke(
                MemorySegment.ofAddress(address).reinterpret(size), size, winProt, oldProtBuf
            ) as Int
            oldProtArena.close()
            if (result == 0) throw RuntimeException("VirtualProtect failed")
        }

        const val PROT_READ = 1
        const val PROT_WRITE = 2
        const val PROT_EXEC = 4
    }
}
