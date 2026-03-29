package org.wark.exec

/**
 * A WASM exception thrown by the THROW instruction or by host functions that
 * need to trigger WASM exception handling (e.g., __wasm_longjmp).
 *
 * Carries the tag index and exception values so TRY/CATCH blocks in the
 * interpreter can match and extract the payload.
 */
class WasmException(
    val tagIndex: Int,
    val values: LongArray,
) : RuntimeException("wasm exception: tag=$tagIndex")
