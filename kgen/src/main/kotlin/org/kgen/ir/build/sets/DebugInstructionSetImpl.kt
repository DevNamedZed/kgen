// Generated from instructions.yaml — do not edit
package org.kgen.ir.build.sets

import org.kgen.ir.*
import org.kgen.ir.build.InstructionSink
import org.kgen.ir.instructions.*
import org.kgen.ir.Constant

/**
 * Default implementation of [DebugInstructionSet] backed by an [InstructionSink].
 */
internal class DebugInstructionSetImpl(private val sink: InstructionSink) : DebugInstructionSet {

    override fun debugLoc(line: Int, col: Int, scope: String, inlinedAt: String?) {
        sink.emit(DebugLoc(line, col, scope, inlinedAt))
    }

    override fun debugValue(variable: String, value: Value, expression: String?) {
        sink.emit(DebugValue(variable, value, expression))
    }

    override fun debugDeclare(variable: String, address: Value, expression: String?) {
        sink.emit(DebugDeclare(variable, address, expression))
    }

    override fun assume(condition: Value) {
        sink.emit(Assume(condition))
    }

    override fun expect(value: Value, expected: Constant): Value =
        sink.nextRef(value.type).also { sink.emit(Expect(it, value, expected)) }
}
