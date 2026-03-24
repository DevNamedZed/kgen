package org.wark.compile

import org.kgen.ir.Value

/**
 * WASM operand value stack with block-level save/restore.
 *
 * When entering a WASM block, [save] records the current stack height.
 * When the block ends, [restore] pops everything above the saved height
 * and returns the block's result values. This correctly handles WASM's
 * block result type semantics.
 */
class ValueStack {
    private val values = ArrayDeque<Value>()
    private val savedHeights = ArrayDeque<Int>()

    fun push(value: Value) {
        values.addLast(value)
    }

    fun pop(): Value {
        if (values.isEmpty()) {
            throw IllegalStateException(
                "WASM value stack underflow: stack is empty (saved heights: ${savedHeights.toList()})"
            )
        }
        return values.removeLast()
    }

    fun peek(): Value {
        return values.last()
    }

    fun isEmpty(): Boolean = values.isEmpty()

    fun size(): Int = values.size

    /**
     * Save the current stack height before entering a block.
     */
    fun save() {
        savedHeights.addLast(values.size)
    }

    /**
     * Restore stack to the height saved at block entry.
     * If the block produced a result, it will be on top — we preserve it.
     *
     * @param resultCount how many values the block produces (0 or 1 in MVP)
     */
    fun restore(resultCount: Int) {
        if (savedHeights.isEmpty()) {
            return
        }
        val savedHeight = savedHeights.removeLast()

        if (resultCount > 0 && values.size > savedHeight) {
            val results = mutableListOf<Value>()
            for (index in 0 until resultCount) {
                if (values.isNotEmpty()) {
                    results.add(values.removeLast())
                }
            }
            while (values.size > savedHeight) {
                values.removeLast()
            }
            for (result in results.reversed()) {
                values.addLast(result)
            }
        } else {
            while (values.size > savedHeight) {
                values.removeLast()
            }
        }
    }
}
