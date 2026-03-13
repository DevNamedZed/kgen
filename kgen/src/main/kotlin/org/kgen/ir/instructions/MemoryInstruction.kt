package org.kgen.ir.instructions

import org.kgen.ir.AtomicOrdering
import org.kgen.ir.InstructionRef
import org.kgen.ir.IrCategory
import org.kgen.ir.Type
import org.kgen.ir.Value

sealed interface MemoryInstruction : Instruction {
    override val category get() = IrCategory.MEMORY
}

data class Alloca(
    val dest: InstructionRef,
    val allocType: Type,
    val numElements: Value? = null,
    val align: Int? = null,
) : MemoryInstruction {
    override val result get() = dest
}

data class Load(
    val dest: InstructionRef,
    val ptr: Value,
    val loadType: Type,
    val align: Int? = null,
    val volatile: Boolean = false,
    val ordering: AtomicOrdering? = null,
) : MemoryInstruction {
    override val result get() = dest
}

data class Store(
    val value: Value,
    val ptr: Value,
    val align: Int? = null,
    val volatile: Boolean = false,
    val ordering: AtomicOrdering? = null,
) : MemoryInstruction {
    override val result: Value? get() = null
}

data class GetElementPtr(
    val dest: InstructionRef,
    val baseType: Type,
    val ptr: Value,
    val indices: List<Value>,
    val inBounds: Boolean = true,
) : MemoryInstruction {
    override val result get() = dest
}

data class MemCpy(
    val dst: Value,
    val src: Value,
    val len: Value,
    val volatile: Boolean = false,
) : MemoryInstruction {
    override val result: Value? get() = null
}

data class MemSet(
    val dst: Value,
    val value: Value,
    val len: Value,
    val volatile: Boolean = false,
) : MemoryInstruction {
    override val result: Value? get() = null
}

data class MemMove(
    val dst: Value,
    val src: Value,
    val len: Value,
    val volatile: Boolean = false,
) : MemoryInstruction {
    override val result: Value? get() = null
}

data class Prefetch(
    val address: Value,
    val rw: Int,
    val locality: Int,
    val cacheType: Int,
) : MemoryInstruction {
    override val result: Value? get() = null
}

data class StackSave(
    val dest: InstructionRef,
) : MemoryInstruction {
    override val result get() = dest
}

data class StackRestore(
    val ptr: Value,
) : MemoryInstruction {
    override val result: Value? get() = null
}

data class LifetimeStart(
    val ptr: Value,
    val size: Long,
) : MemoryInstruction {
    override val result: Value? get() = null
}

data class LifetimeEnd(
    val ptr: Value,
    val size: Long,
) : MemoryInstruction {
    override val result: Value? get() = null
}

data class VAStart(
    val argList: Value,
) : MemoryInstruction {
    override val result: Value? get() = null
}

data class VAEnd(
    val argList: Value,
) : MemoryInstruction {
    override val result: Value? get() = null
}

data class VACopy(
    val dst: Value,
    val src: Value,
) : MemoryInstruction {
    override val result: Value? get() = null
}

data class VAArg(
    val dest: InstructionRef,
    val argList: Value,
    val argType: Type,
) : MemoryInstruction {
    override val result get() = dest
}
