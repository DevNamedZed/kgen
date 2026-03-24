package org.wark

/**
 * A WASM trap — an unrecoverable error during execution.
 *
 * Traps are thrown for: out-of-bounds memory access, division by zero,
 * unreachable instruction, indirect call type mismatch, stack overflow,
 * and integer overflow in trunc operations (when sat-trunc is disabled).
 */
class WasmTrap(message: String) : RuntimeException(message)
