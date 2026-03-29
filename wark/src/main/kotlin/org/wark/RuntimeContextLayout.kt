package org.wark

/**
 * Fixed layout of the native RuntimeContext struct passed to every JIT'd WASM function.
 *
 * The context is allocated in native memory (FFM Arena). Its address never changes —
 * only its contents change (e.g., memory_base after memory.grow).
 *
 * ```
 * struct RuntimeContext {
 *     i64 memory_base      // offset 0:  pointer to linear memory bytes
 *     i64 memory_size      // offset 8:  current memory size in bytes
 *     i64 globals_base     // offset 16: pointer to globals data section
 *     i64 table_base       // offset 24: pointer to function table
 *     i64 table_size       // offset 32: number of table entries
 *     i32 exc_pending      // offset 40: 1 if exception pending, 0 otherwise
 *     i32 exc_tag          // offset 44: tag index of pending exception
 *     i64 exc_values[2]    // offset 48: exception payload (up to 2 i64 values)
 * }
 * ```
 */
object RuntimeContextLayout {
    const val MEMORY_BASE: Long = 0L
    const val MEMORY_SIZE: Long = 8L
    const val GLOBALS_BASE: Long = 16L
    const val TABLE_BASE: Long = 24L
    const val TABLE_SIZE: Long = 32L
    const val EXC_PENDING: Long = 40L
    const val EXC_TAG: Long = 44L
    const val EXC_VALUES: Long = 48L
    const val SIZE: Long = 64L
}
