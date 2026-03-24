package org.wark

/**
 * Fixed layout of the native RuntimeContext struct passed to every JIT'd WASM function.
 *
 * The context is allocated in native memory (FFM Arena). Its address never changes —
 * only its contents change (e.g., memory_base after memory.grow).
 *
 * ```
 * struct RuntimeContext {
 *     i64 memory_base   // offset 0:  pointer to linear memory bytes
 *     i64 memory_size   // offset 8:  current memory size in bytes
 *     i64 globals_base  // offset 16: pointer to globals data section
 *     i64 table_base    // offset 24: pointer to function table
 *     i64 table_size    // offset 32: number of table entries
 * }
 * ```
 */
object RuntimeContextLayout {
    const val MEMORY_BASE: Long = 0L
    const val MEMORY_SIZE: Long = 8L
    const val GLOBALS_BASE: Long = 16L
    const val TABLE_BASE: Long = 24L
    const val TABLE_SIZE: Long = 32L
    const val SIZE: Long = 40L
}
