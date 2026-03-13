package org.kgen.ir.instructions

import org.kgen.ir.InstructionRef
import org.kgen.ir.IrCategory
import org.kgen.ir.Type
import org.kgen.ir.Value

sealed interface RuntimeInstruction : Instruction {
    override val category get() = IrCategory.RUNTIME
}

data class GCAlloc(
    val dest: InstructionRef,
    val allocType: Type,
    val size: Value? = null,
) : RuntimeInstruction {
    override val result get() = dest
}

data class GCSafepoint(
    val dummy: Unit = Unit,
) : RuntimeInstruction {
    override val result: Value? get() = null
}

data class GCRoot(
    val ptr: Value,
    val metadata: Value?,
) : RuntimeInstruction {
    override val result: Value? get() = null
}

data class WriteBarrier(
    val obj: Value,
    val fieldIndex: Value,
    val value: Value,
) : RuntimeInstruction {
    override val result: Value? get() = null
}

data class ReadBarrier(
    val dest: InstructionRef,
    val ref: Value,
) : RuntimeInstruction {
    override val result get() = dest
}

data class RefRetain(
    val obj: Value,
) : RuntimeInstruction {
    override val result: Value? get() = null
}

data class RefRelease(
    val obj: Value,
) : RuntimeInstruction {
    override val result: Value? get() = null
}

data class RefCount(
    val dest: InstructionRef,
    val obj: Value,
) : RuntimeInstruction {
    override val result get() = dest
}

data class CoroBegin(
    val dest: InstructionRef,
    val id: Value,
    val mem: Value,
) : RuntimeInstruction {
    override val result get() = dest
}

data class CoroEnd(
    val handle: Value,
    val unwind: Boolean = false,
) : RuntimeInstruction {
    override val result: Value? get() = null
}

data class CoroSuspend(
    val dest: InstructionRef,
    val save: Value?,
    val isFinal: Boolean = false,
) : RuntimeInstruction {
    override val result get() = dest
}

data class CoroResume(
    val handle: Value,
) : RuntimeInstruction {
    override val result: Value? get() = null
}

data class CoroDestroy(
    val handle: Value,
) : RuntimeInstruction {
    override val result: Value? get() = null
}

data class CoroSize(
    val dest: InstructionRef,
) : RuntimeInstruction {
    override val result get() = dest
}
