package org.kgen.runtime.compile

/**
 * Target output platform for native compilation.
 */
enum class OutputPlatform {
    /** Linux ELF, statically linked (no libc dependency). */
    LINUX,
    /** Linux ELF, dynamically linked (depends on libc). */
    LINUX_DYNAMIC,
    /** Windows PE executable. */
    WINDOWS,
    /** macOS Mach-O executable. */
    MACOS,
}
