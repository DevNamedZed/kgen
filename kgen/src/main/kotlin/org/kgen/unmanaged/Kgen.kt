package org.kgen.unmanaged

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Intrinsic API for the Runtime Subset.
 *
 * Each method has a JVM fallback implementation using a simulated memory model,
 * so `@KgenNative` code is runnable and debuggable on the JVM. In native mode,
 * kgen replaces each call with the corresponding IR instruction.
 *
 * The JVM memory simulation uses a `ByteBuffer` heap, allowing `@KgenNative` classes
 * to be tested as regular JVM objects before compiling to native code.
 *
 * ```java
 * @KgenNative
 * public class NativeArrayList {
 *     var data: Long = 0
 *     var size: Int = 0
 *     var capacity: Int = 0
 *
 *     fun add(element: Long) {
 *         if (size >= capacity) grow()
 *         Kgen.storeLong(Kgen.offset(data, size * 8), element)
 *         size++
 *     }
 * }
 *
 * // Works on JVM for testing:
 * val list = NativeArrayList()
 * list.add(42)
 * ```
 */
object Kgen {

    // -- JVM Memory Simulation --

    private const val HEAP_SIZE = 16 * 1024 * 1024  // 16 MB simulated heap
    private const val HEAP_BASE = 0x10000L

    private val heap: ByteBuffer = ByteBuffer.allocate(HEAP_SIZE).order(ByteOrder.LITTLE_ENDIAN)
    private var nextAlloc: Long = HEAP_BASE
    private val allocations = HashMap<Long, Int>()  // addr → size

    private fun addrToOffset(addr: Long): Int = (addr - HEAP_BASE).toInt()

    private fun isValidAddr(addr: Long): Boolean =
        addr >= HEAP_BASE && addrToOffset(addr) < HEAP_SIZE

    // -- Allocation --

    /** Allocate [size] bytes from the simulated heap. Returns the address. */
    @KgenIntrinsic @JvmStatic
    fun malloc(size: Long): Long {
        val aligned = ((size + 7) / 8) * 8
        val addr = nextAlloc
        nextAlloc += aligned
        if (addrToOffset(nextAlloc.toLong()) >= HEAP_SIZE) {
            error("Kgen JVM heap exhausted (${HEAP_SIZE} bytes)")
        }
        allocations[addr] = size.toInt()
        // Zero-initialize
        val offset = addrToOffset(addr)
        for (i in 0 until aligned.toInt()) {
            heap.put(offset + i, 0)
        }
        return addr
    }

    /** Reallocate [ptr] to [newSize] bytes. Copies existing data. */
    @KgenIntrinsic @JvmStatic
    fun realloc(ptr: Long, newSize: Long): Long {
        if (ptr == 0L) {
            return malloc(newSize)
        }
        val oldSize = allocations[ptr] ?: return malloc(newSize)
        val newAddr = malloc(newSize)
        val copySize = minOf(oldSize, newSize.toInt())
        val srcOffset = addrToOffset(ptr)
        val dstOffset = addrToOffset(newAddr)
        for (i in 0 until copySize) {
            heap.put(dstOffset + i, heap.get(srcOffset + i))
        }
        allocations.remove(ptr)
        return newAddr
    }

    /** Free previously allocated memory. */
    @KgenIntrinsic @JvmStatic
    fun free(ptr: Long) {
        allocations.remove(ptr)
    }

    // -- Memory access --

    /** Load a byte from the given address. Native: `Load(addr, i8)`. */
    @KgenIntrinsic @JvmStatic
    fun loadByte(addr: Long): Byte {
        if (!isValidAddr(addr)) { return 0 }
        return heap.get(addrToOffset(addr))
    }

    /** Load a 16-bit integer from the given address. Native: `Load(addr, i16)`. */
    @KgenIntrinsic @JvmStatic
    fun loadShort(addr: Long): Short {
        if (!isValidAddr(addr)) { return 0 }
        return heap.getShort(addrToOffset(addr))
    }

    /** Load a 32-bit integer from the given address. Native: `Load(addr, i32)`. */
    @KgenIntrinsic @JvmStatic
    fun loadInt(addr: Long): Int {
        if (!isValidAddr(addr)) { return 0 }
        return heap.getInt(addrToOffset(addr))
    }

    /** Load a 64-bit integer from the given address. Native: `Load(addr, i64)`. */
    @KgenIntrinsic @JvmStatic
    fun loadLong(addr: Long): Long {
        if (!isValidAddr(addr)) { return 0 }
        return heap.getLong(addrToOffset(addr))
    }

    /** Store a byte at the given address. Native: `Store(addr, val, i8)`. */
    @KgenIntrinsic @JvmStatic
    fun storeByte(addr: Long, value: Byte) {
        if (!isValidAddr(addr)) { return }
        heap.put(addrToOffset(addr), value)
    }

    /** Store a 16-bit integer at the given address. Native: `Store(addr, val, i16)`. */
    @KgenIntrinsic @JvmStatic
    fun storeShort(addr: Long, value: Short) {
        if (!isValidAddr(addr)) { return }
        heap.putShort(addrToOffset(addr), value)
    }

    /** Store a 32-bit integer at the given address. Native: `Store(addr, val, i32)`. */
    @KgenIntrinsic @JvmStatic
    fun storeInt(addr: Long, value: Int) {
        if (!isValidAddr(addr)) { return }
        heap.putInt(addrToOffset(addr), value)
    }

    /** Store a 64-bit integer at the given address. Native: `Store(addr, val, i64)`. */
    @KgenIntrinsic @JvmStatic
    fun storeLong(addr: Long, value: Long) {
        if (!isValidAddr(addr)) { return }
        heap.putLong(addrToOffset(addr), value)
    }

    // -- Pointer arithmetic --

    /** Add a byte offset to a base address. Native: `Add(base, offset)`. */
    @KgenIntrinsic @JvmStatic
    fun offset(base: Long, bytes: Int): Long = base + bytes

    /** Add a long byte offset to a base address. Native: `Add(base, offset)`. */
    @KgenIntrinsic @JvmStatic
    fun offset(base: Long, bytes: Long): Long = base + bytes

    // -- Stack allocation --

    /** Allocate bytes on the stack frame. Native: `Alloca(bytes)`. JVM: uses heap. */
    @KgenIntrinsic @JvmStatic
    fun stackAlloc(bytes: Int): Long = malloc(bytes.toLong())

    // -- GC / runtime coordination --

    /** Mark a GC safepoint. Native: `GCSafepoint`. */
    @KgenIntrinsic @JvmStatic
    fun safepoint() {}

    /** Declare a stack slot as a GC root. Native: `GCRoot(obj)`. */
    @KgenIntrinsic @JvmStatic
    fun gcRoot(obj: Any) {}

    /** Declare a pointer as a GC root (long-pointer form for runtime subset). */
    @KgenIntrinsic @JvmStatic
    fun gcRoot(ptr: Long) {}

    /** Write barrier for generational/concurrent GC. Native: `WriteBarrier(obj, fieldIndex, value)`. */
    @KgenIntrinsic @JvmStatic
    fun writeBarrier(obj: Any, fieldIndex: Int, value: Any) {}

    /** Write barrier (long-pointer form for runtime subset). */
    @KgenIntrinsic @JvmStatic
    fun writeBarrier(obj: Long, fieldIndex: Int, value: Long) {}

    /** Read barrier for relocating GC. Native: `ReadBarrier(obj)`. */
    @KgenIntrinsic @JvmStatic
    fun readBarrier(obj: Any) {}

    /** Read barrier (long-pointer form for runtime subset). Returns relocated pointer. */
    @KgenIntrinsic @JvmStatic
    fun readBarrier(ptr: Long): Long = ptr

    // -- Allocation (runtime subset) --

    /** Allocate bytes from the runtime heap. Native: runtime call. */
    @KgenIntrinsic @JvmStatic
    fun runtimeAlloc(bytes: Int): Long = malloc(bytes.toLong())

    /** Free previously allocated memory. Native: runtime call. */
    @KgenIntrinsic @JvmStatic
    fun runtimeFree(ptr: Long) { free(ptr) }

    // -- Compile-time constants --

    private val stringConstants = HashMap<String, Long>()

    /**
     * Create a compile-time string constant and return its address.
     *
     * In native mode, the compiler emits a null-terminated string constant global
     * and replaces this call with its address. On the JVM, allocates the string
     * in the simulated heap and caches it.
     */
    @KgenIntrinsic @JvmStatic
    fun stringConst(value: String): Long {
        return stringConstants.getOrPut(value) {
            val bytes = value.toByteArray(Charsets.UTF_8)
            val addr = malloc(bytes.size.toLong() + 1)
            val offset = addrToOffset(addr)
            for (i in bytes.indices) {
                heap.put(offset + i, bytes[i])
            }
            heap.put(offset + bytes.size, 0)  // null terminator
            addr
        }
    }

    // -- Platform detection --

    @KgenIntrinsic @JvmStatic
    fun isWindows(): Boolean = System.getProperty("os.name")?.lowercase()?.contains("win") == true

    @KgenIntrinsic @JvmStatic
    fun isLinux(): Boolean = System.getProperty("os.name")?.lowercase()?.contains("linux") == true

    @KgenIntrinsic @JvmStatic
    fun isMacOS(): Boolean {
        val os = System.getProperty("os.name")?.lowercase() ?: return false
        return "mac" in os || "darwin" in os
    }

    // -- Platform blocks (Kotlin DSL) --

    @JvmStatic inline fun onWindows(block: () -> Unit) { if (isWindows()) block() }
    @JvmStatic inline fun onLinux(block: () -> Unit) { if (isLinux()) block() }
    @JvmStatic inline fun onMacOS(block: () -> Unit) { if (isMacOS()) block() }

    // -- Hints --

    @KgenIntrinsic @JvmStatic
    fun likely(cond: Boolean): Boolean = cond

    @KgenIntrinsic @JvmStatic
    fun unlikely(cond: Boolean): Boolean = cond

    // -- Test support --

    /** Reset the simulated heap. Call between tests to avoid cross-test contamination. */
    @JvmStatic
    fun resetHeap() {
        nextAlloc = HEAP_BASE
        allocations.clear()
        stringConstants.clear()
        for (i in 0 until HEAP_SIZE) {
            heap.put(i, 0)
        }
    }
}
