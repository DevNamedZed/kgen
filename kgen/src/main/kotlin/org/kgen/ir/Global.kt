package org.kgen.ir

/**
 * A global variable definition. Global variables live in static memory for the lifetime of
 * the program and are accessible by [name] from any function in the module (and from other
 * modules, depending on [linkage]).
 *
 * The backend emits globals in an appropriate section of the binary (`.data` for mutable,
 * `.rodata` for [isConstant] = true globals). If [initializer] is `null`, the backend
 * emits the variable in `.bss` (zero-initialized storage).
 *
 * ```java
 * // A mutable i32 global, zero-initialized, external linkage
 * var counter = new Global("counter", Type.I32);
 *
 * // A read-only string constant in a specific section
 * var greeting = new Global(
 *     "greeting",
 *     Type.array(Type.I8, 6),
 *     Type.string("hello"),
 *     /* isConstant */ true,
 *     Linkage.INTERNAL
 * );
 * ```
 *
 * To reference a global in IR instructions, use [GlobalRef].
 *
 * @param name the global's symbol name, unique within the module
 * @param type the type of the value stored at this global (not a pointer type — the global itself
 *   has type [Type.Pointer] to this type when referenced via [GlobalRef])
 * @param initializer compile-time initial value, or `null` for zero-initialization (BSS placement)
 * @param isConstant if true, the global is read-only after initialization; the optimizer may
 *   treat loads as invariant and the backend places it in a read-only section
 * @param linkage controls symbol visibility to the linker and other translation units
 * @param visibility ELF/Mach-O symbol visibility attribute (default, hidden, protected)
 * @param threadLocal if non-null, the global is thread-local storage using the specified TLS model
 * @param section overrides the output section (e.g., `".mydata"`) for linker script control
 * @param align the required alignment in bytes (must be a power of 2), or `null` for the target default
 * @param addressSpace the address space in which this global resides (0 = default flat space;
 *   non-zero values are target-specific, e.g., GPU constant memory)
 *
 * @see GlobalRef
 * @see Linkage
 * @see ThreadLocalMode
 */
data class Global(
    val name: String,
    val type: Type,
    val initializer: Constant? = null,
    val isConstant: Boolean = false,
    val linkage: Linkage = Linkage.EXTERNAL,
    val visibility: Visibility = Visibility.DEFAULT,
    val threadLocal: ThreadLocalMode? = null,
    val section: String? = null,
    val align: Int? = null,
    val addressSpace: Int = 0,
)
