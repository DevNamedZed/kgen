// Generated from instructions.yaml — do not edit
package org.kgen.ir.build.sets

import org.kgen.ir.*
import org.kgen.ir.build.InstructionSink
import org.kgen.ir.instructions.*
import org.kgen.ir.AsmDialect
import org.kgen.ir.Type

/**
 * Default implementation of [IntrinsicInstructionSet] backed by an [InstructionSink].
 */
internal class IntrinsicInstructionSetImpl(private val sink: InstructionSink) : IntrinsicInstructionSet {

    override fun intrinsic(name: String, args: List<Value>, returnType: Type): Value? {
        val dest = if (returnType != Type.Void) { sink.nextRef(returnType) } else { null }
        sink.emit(Intrinsic(dest, name, args, returnType))
        return dest
    }

    override fun inlineAsm(assembly: String, constraints: String, sideEffects: Boolean, alignStack: Boolean, dialect: AsmDialect, args: List<Value>, returnType: Type): Value? {
        val dest = if (returnType != Type.Void) { sink.nextRef(returnType) } else { null }
        sink.emit(InlineAsm(dest, assembly, constraints, sideEffects, alignStack, dialect, args, returnType))
        return dest
    }
}
