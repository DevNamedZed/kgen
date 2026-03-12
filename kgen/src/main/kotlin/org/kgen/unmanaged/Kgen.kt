package org.kgen.unmanaged

/**
 * Intrinsic API for the Runtime Subset.
 *
 * Each method has a trivial JVM fallback body so runtime code is runnable
 * and debuggable on the JVM. In native mode, kgen replaces each call
 * with the corresponding IR instruction.
 *
 * ```java
 * @KgenRuntime
 * public class StringOps {
 *     @KgenExport
 *     public static int hash(long strPtr, int len) {
 *         int h = 0;
 *         for (int i = 0; i < len; i++) {
 *             h = 31 * h + Kgen.loadByte(strPtr + i);
 *         }
 *         return h;
 *     }
 * }
 * ```
 */
object Kgen {

    // -- Memory access --

    /** Load a byte from the given address. Native: `Load(addr, i8)`. */
    @KgenIntrinsic @JvmStatic
    fun loadByte(addr: Long): Byte = 0

    /** Load a 16-bit integer from the given address. Native: `Load(addr, i16)`. */
    @KgenIntrinsic @JvmStatic
    fun loadShort(addr: Long): Short = 0

    /** Load a 32-bit integer from the given address. Native: `Load(addr, i32)`. */
    @KgenIntrinsic @JvmStatic
    fun loadInt(addr: Long): Int = 0

    /** Load a 64-bit integer from the given address. Native: `Load(addr, i64)`. */
    @KgenIntrinsic @JvmStatic
    fun loadLong(addr: Long): Long = 0

    /** Store a byte at the given address. Native: `Store(addr, val, i8)`. */
    @KgenIntrinsic @JvmStatic
    fun storeByte(addr: Long, value: Byte) {}

    /** Store a 16-bit integer at the given address. Native: `Store(addr, val, i16)`. */
    @KgenIntrinsic @JvmStatic
    fun storeShort(addr: Long, value: Short) {}

    /** Store a 32-bit integer at the given address. Native: `Store(addr, val, i32)`. */
    @KgenIntrinsic @JvmStatic
    fun storeInt(addr: Long, value: Int) {}

    /** Store a 64-bit integer at the given address. Native: `Store(addr, val, i64)`. */
    @KgenIntrinsic @JvmStatic
    fun storeLong(addr: Long, value: Long) {}

    // -- Pointer arithmetic --

    /** Add a byte offset to a base address. Native: `Add(base, offset)`. */
    @KgenIntrinsic @JvmStatic
    fun offset(base: Long, bytes: Int): Long = base + bytes

    /** Add a long byte offset to a base address. Native: `Add(base, offset)`. */
    @KgenIntrinsic @JvmStatic
    fun offset(base: Long, bytes: Long): Long = base + bytes

    // -- Stack allocation --

    /** Allocate bytes on the stack frame. Native: `Alloca(bytes)`. */
    @KgenIntrinsic @JvmStatic
    fun stackAlloc(bytes: Int): Long = 0

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

    // -- Allocation --

    /** Allocate bytes from the runtime heap. Native: runtime call. */
    @KgenIntrinsic @JvmStatic
    fun runtimeAlloc(bytes: Int): Long = 0

    /** Free previously allocated memory. Native: runtime call. */
    @KgenIntrinsic @JvmStatic
    fun runtimeFree(ptr: Long) {}

    // -- Compile-time constants --

    /**
     * Create a compile-time string constant and return its address.
     *
     * In native mode, the compiler emits a null-terminated string constant global
     * and replaces this call with its address (as `long`). On the JVM, returns 0.
     *
     * Used in `@KgenNative` code to pass C string literals to imported functions:
     * ```java
     * @KgenImport("printf") static native int printfI(long fmt, int v);
     *
     * static void hello(int n) {
     *     printfI(Kgen.stringConst("n = %d\n"), n);
     * }
     * ```
     */
    @KgenIntrinsic @JvmStatic
    fun stringConst(value: String): Long = 0

    // -- Platform detection --

    /**
     * Returns `true` when compiling for Windows.
     *
     * In native mode, the compiler replaces this with a compile-time constant
     * (`true` or `false`) based on the target triple — dead code elimination
     * removes the unused branch. On the JVM, checks the host OS at runtime.
     */
    @KgenIntrinsic @JvmStatic
    fun isWindows(): Boolean = System.getProperty("os.name")?.lowercase()?.contains("win") == true

    /**
     * Returns `true` when compiling for Linux.
     *
     * In native mode, replaced with a compile-time constant based on the target triple.
     * On the JVM, checks the host OS at runtime.
     */
    @KgenIntrinsic @JvmStatic
    fun isLinux(): Boolean = System.getProperty("os.name")?.lowercase()?.contains("linux") == true

    /**
     * Returns `true` when compiling for macOS / Darwin.
     *
     * In native mode, replaced with a compile-time constant based on the target triple.
     * On the JVM, checks the host OS at runtime.
     */
    @KgenIntrinsic @JvmStatic
    fun isMacOS(): Boolean {
        val os = System.getProperty("os.name")?.lowercase() ?: return false
        return "mac" in os || "darwin" in os
    }

    // -- Platform blocks (Kotlin DSL) --

    /**
     * Execute [block] only when compiling for Windows.
     *
     * Kotlin `inline` — the lambda is inlined at compile time, so bytecode is
     * just `isWindows()` + conditional branch. DCE removes the dead path.
     *
     * ```kotlin
     * Kgen.onWindows {
     *     writeConsoleA(getStdHandle(-11), buf, len, 0, 0)
     * }
     * ```
     */
    @JvmStatic inline fun onWindows(block: () -> Unit) { if (isWindows()) block() }

    /**
     * Execute [block] only when compiling for Linux.
     *
     * ```kotlin
     * Kgen.onLinux {
     *     write(1, buf, len) // POSIX write to stdout
     * }
     * ```
     */
    @JvmStatic inline fun onLinux(block: () -> Unit) { if (isLinux()) block() }

    /**
     * Execute [block] only when compiling for macOS / Darwin.
     *
     * ```kotlin
     * Kgen.onMacOS {
     *     write(1, buf, len) // POSIX write to stdout
     * }
     * ```
     */
    @JvmStatic inline fun onMacOS(block: () -> Unit) { if (isMacOS()) block() }

    // -- Hints --

    /** Hint that this condition is likely true. Native: branch weight hint. */
    @KgenIntrinsic @JvmStatic
    fun likely(cond: Boolean): Boolean = cond

    /** Hint that this condition is likely false. Native: branch weight hint. */
    @KgenIntrinsic @JvmStatic
    fun unlikely(cond: Boolean): Boolean = cond
}
