package org.kgen.unmanaged

/**
 * Compile a class or method to native code using the native subset rules.
 *
 * Pure native — only memory intrinsics are available (`Kgen.load*`, `Kgen.store*`,
 * `Kgen.offset`, `Kgen.stackAlloc`). No runtime coordination intrinsics
 * (safepoints, GC roots, barriers).
 *
 * When applied to a class, all methods are compiled as native subset.
 * When applied to a method, only that method is compiled as native subset
 * (mixed mode — other methods in the class remain regular Java).
 *
 * ```java
 * @KgenNative
 * public class StringOps {
 *     public static int hash(long strPtr, int len) {
 *         int h = 0;
 *         for (int i = 0; i < len; i++) {
 *             h = 31 * h + Kgen.loadByte(Kgen.offset(strPtr, i));
 *         }
 *         return h;
 *     }
 * }
 * ```
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class KgenNative

/**
 * Marks a class as a runtime component that compiles to native code.
 *
 * Like [KgenNative], but with access to the full set of runtime intrinsics:
 * `Kgen.safepoint()`, `Kgen.gcRoot()`, `Kgen.writeBarrier()`,
 * `Kgen.readBarrier()`, `Kgen.runtimeAlloc()`, `Kgen.runtimeFree()`.
 *
 * Used for implementing runtime plugins (custom HeapManager, GarbageCollector,
 * SafepointManager, etc.) that get compiled to native and linked into the
 * JIT engine. JIT-compiled code calls into these directly — no FFM bridge.
 *
 * ```java
 * @KgenRuntime
 * public class PoolAllocator implements HeapManager {
 *     static long freeList;
 *
 *     public long allocate(int size) {
 *         long block = freeList;
 *         if (block != 0) {
 *             freeList = Kgen.loadLong(block);
 *             return block;
 *         }
 *         return 0; // OOM
 *     }
 * }
 * ```
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class KgenRuntime

/**
 * Exports a method as a C-callable symbol in the compiled output.
 *
 * The method gets external linkage with the given symbol name, making it
 * callable from C, other native code, or resolvable by the JIT engine.
 * Implies native compilation — the containing class must be `@KgenNative`
 * or `@KgenRuntime`.
 *
 * ```java
 * @KgenExport(value = "my_func", convention = "win64")
 * static int myFunc(int x) { return x * 2; }
 * ```
 *
 * @param value Custom export symbol name. If empty (default), the method name is used.
 * @param convention Calling convention: "c" (default), "fast", "cold", "tail",
 *   "win64", "sysv64", "aapcs", "swift". Maps to [org.kgen.ir.CallingConvention].
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class KgenExport(val value: String = "", val convention: String = "")

/**
 * Marks a method whose body is replaced by kgen with special IR.
 *
 * Intrinsic methods have trivial JVM fallback bodies (return 0, no-op)
 * so the code is debuggable on the JVM. In native mode, kgen replaces
 * each call with the corresponding IR instruction.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class KgenIntrinsic

/**
 * Marks a method that must not allocate.
 *
 * Enforced by the subset validator — any call to `Kgen.runtimeAlloc`
 * or other allocating intrinsics within this method is a compile error.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class KgenNoAlloc

/**
 * Marks a method for mandatory cross-method inlining.
 *
 * Functions with this annotation are always inlined at call sites during
 * native compilation, regardless of size. Useful for small helper methods
 * that should be zero-cost abstractions.
 *
 * ```java
 * @KgenInline
 * public static int doubleIt(int x) { return x + x; }
 * ```
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class KgenInline

/**
 * Marks a method that does not call back into managed code.
 *
 * Leaf methods can skip managed-to-native transition overhead.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class KgenLeaf

/**
 * Declares an external C function that can be called from `@KgenNative` code.
 *
 * Applied to `native` methods — the method has no body in Java, and kgen
 * emits an external function declaration resolved at link time. Uses the
 * same type mapping as `@KgenExport`.
 *
 * ```java
 * @KgenNative
 * public class StdlibImpl {
 *     @KgenImport static native int puts(long s);
 *     @KgenImport static native long malloc(long size);
 *     @KgenImport static native void free(long ptr);
 *
 *     @KgenExport("kgen_println_str")
 *     static void printlnStr(long s) {
 *         puts(s);
 *     }
 * }
 * ```
 *
 * @param value Custom C symbol name. If empty (default), the method name is used.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class KgenImport(val value: String = "")

/**
 * Marks a method as a destructor for automatic resource cleanup.
 *
 * When a `@KgenNative` class has a method annotated with `@KgenDestructor`,
 * the native compiler inserts calls to it at scope exits for local variables
 * of that type — like C++ destructors. The method must be idempotent (safe
 * to call multiple times).
 *
 * On the JVM, the class should also implement `AutoCloseable` to enable
 * Kotlin `use {}` and Java try-with-resources for explicit cleanup.
 *
 * ```java
 * @KgenNative
 * public class NativeBuffer implements AutoCloseable {
 *     long data;
 *     int size;
 *
 *     @KgenDestructor
 *     public void destroy() {
 *         if (data != 0) {
 *             Kgen.free(data);
 *             data = 0;
 *         }
 *     }
 *
 *     @Override public void close() { destroy(); }
 * }
 * ```
 *
 * Native compilation: destructor is called implicitly when local variables
 * go out of scope. Returned values transfer ownership to the caller.
 *
 * JVM testing: use `AutoCloseable` / `use {}` for deterministic cleanup.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class KgenDestructor
