package org.kgen.ir

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class IrCategoryTest {

    // --- Category-to-tier mapping ---

    @Test
    fun structuralCategoriesMapToTier0() {
        val structural = listOf(
            IrCategory.TERMINATOR, IrCategory.CALL, IrCategory.SSA,
            IrCategory.DEBUG, IrCategory.INTRINSIC,
        )
        for (cat in structural) {
            assertEquals(IrTier.STRUCTURAL, cat.tier, "$cat should be STRUCTURAL")
        }
    }

    @Test
    fun machineCategoriesMapToTier1() {
        val machine = listOf(
            IrCategory.ARITHMETIC, IrCategory.BITWISE, IrCategory.COMPARISON,
            IrCategory.CONVERSION, IrCategory.MEMORY, IrCategory.ATOMIC,
            IrCategory.VECTOR, IrCategory.AGGREGATE, IrCategory.EXCEPTION,
        )
        for (cat in machine) {
            assertEquals(IrTier.MACHINE, cat.tier, "$cat should be MACHINE")
        }
    }

    @Test
    fun runtimeCategoryMapToTier2() {
        assertEquals(IrTier.RUNTIME, IrCategory.RUNTIME.tier)
    }

    @Test
    fun interopCategoryMapsToTier2() {
        assertEquals(IrTier.INTEROP, IrCategory.INTEROP.tier)
        assertEquals(2, IrCategory.INTEROP.tier.level)
    }

    @Test
    fun objectCategoryMapsToTier3() {
        assertEquals(IrTier.OBJECT, IrCategory.OBJECT.tier)
    }

    // --- Tier ordering ---

    @Test
    fun tierLevelsAreOrdered() {
        assertTrue(IrTier.STRUCTURAL.level < IrTier.MACHINE.level)
        assertTrue(IrTier.MACHINE.level < IrTier.RUNTIME.level)
        assertEquals(IrTier.RUNTIME.level, IrTier.INTEROP.level)
        assertTrue(IrTier.RUNTIME.level < IrTier.OBJECT.level)
    }

    // --- Every instruction has a category ---

    private val allInstructions: List<Instruction> by lazy {
        val ref = InstructionRef("%0", Type.I32)
        val ref2 = InstructionRef("%1", Type.I32)
        val ptr = InstructionRef("%p", Type.Pointer(Type.I32))
        val vecType = Type.Vector(Type.I32, 4)
        val vec = InstructionRef("%v", vecType)
        val structType = Type.Struct(null, listOf(Type.I32, Type.I64))
        val structVal = InstructionRef("%s", structType)
        val funcType = Type.Function(listOf(Type.I32), Type.I32)
        val funcRef = FunctionRef("func", funcType)
        val taggedUnion = Type.TaggedUnion("MyUnion", Type.I32, listOf(
            TaggedVariant("A", 0, listOf(Type.I32)),
        ))
        val unionVal = InstructionRef("%u", taggedUnion)

        listOf(
            // Arithmetic
            Instruction.Add(ref, ref, ref2),
            Instruction.Sub(ref, ref, ref2),
            Instruction.Mul(ref, ref, ref2),
            Instruction.UDiv(ref, ref, ref2),
            Instruction.SDiv(ref, ref, ref2),
            Instruction.URem(ref, ref, ref2),
            Instruction.SRem(ref, ref, ref2),
            Instruction.Neg(ref, ref),
            Instruction.SAddOverflow(ref, ref, ref2),
            Instruction.UAddOverflow(ref, ref, ref2),
            Instruction.SSubOverflow(ref, ref, ref2),
            Instruction.USubOverflow(ref, ref, ref2),
            Instruction.SMulOverflow(ref, ref, ref2),
            Instruction.UMulOverflow(ref, ref, ref2),
            Instruction.SAddSat(ref, ref, ref2),
            Instruction.UAddSat(ref, ref, ref2),
            Instruction.SSubSat(ref, ref, ref2),
            Instruction.USubSat(ref, ref, ref2),
            Instruction.SMin(ref, ref, ref2),
            Instruction.SMax(ref, ref, ref2),
            Instruction.UMin(ref, ref, ref2),
            Instruction.UMax(ref, ref, ref2),
            Instruction.Abs(ref, ref),
            Instruction.FAdd(ref, ref, ref2),
            Instruction.FSub(ref, ref, ref2),
            Instruction.FMul(ref, ref, ref2),
            Instruction.FDiv(ref, ref, ref2),
            Instruction.FRem(ref, ref, ref2),
            Instruction.FNeg(ref, ref),
            Instruction.FAbs(ref, ref),
            Instruction.FMA(ref, ref, ref2, ref),
            Instruction.FMin(ref, ref, ref2),
            Instruction.FMax(ref, ref, ref2),
            Instruction.Sqrt(ref, ref),
            Instruction.Ceil(ref, ref),
            Instruction.Floor(ref, ref),
            Instruction.Round(ref, ref),
            Instruction.Trunc(ref, ref),
            Instruction.CopySign(ref, ref, ref2),

            // Bitwise
            Instruction.And(ref, ref, ref2),
            Instruction.Or(ref, ref, ref2),
            Instruction.Xor(ref, ref, ref2),
            Instruction.Not(ref, ref),
            Instruction.Shl(ref, ref, ref2),
            Instruction.LShr(ref, ref, ref2),
            Instruction.AShr(ref, ref, ref2),
            Instruction.RotateLeft(ref, ref, ref2),
            Instruction.RotateRight(ref, ref, ref2),
            Instruction.Ctlz(ref, ref),
            Instruction.Cttz(ref, ref),
            Instruction.Ctpop(ref, ref),
            Instruction.BSwap(ref, ref),
            Instruction.BitReverse(ref, ref),
            Instruction.Rotl(ref, ref, ref2),
            Instruction.Rotr(ref, ref, ref2),

            // Comparison
            Instruction.ICmp(ref, ICmpPredicate.EQ, ref, ref2),
            Instruction.FCmp(ref, FCmpPredicate.OEQ, ref, ref2),

            // Memory
            Instruction.Alloca(ref, Type.I32),
            Instruction.Load(ref, ptr, Type.I32),
            Instruction.Store(ref, ptr),
            Instruction.GetElementPtr(ref, Type.I32, ptr, listOf(ref)),
            Instruction.MemCpy(ptr, ptr, ref),
            Instruction.MemSet(ptr, ref, ref2),
            Instruction.MemMove(ptr, ptr, ref),
            Instruction.Prefetch(ptr, 0, 3, 1),
            Instruction.StackSave(ref),
            Instruction.StackRestore(ptr),
            Instruction.LifetimeStart(ptr, 4),
            Instruction.LifetimeEnd(ptr, 4),

            // Atomic
            Instruction.Fence(AtomicOrdering.SEQ_CST),
            Instruction.CmpXchg(ref, ptr, ref, ref2, AtomicOrdering.SEQ_CST, AtomicOrdering.ACQUIRE),
            Instruction.AtomicRMW(ref, AtomicRMWOp.ADD, ptr, ref, AtomicOrdering.SEQ_CST),

            // Conversion
            Instruction.IntTrunc(ref, ref, Type.I16),
            Instruction.ZExt(ref, ref, Type.I64),
            Instruction.SExt(ref, ref, Type.I64),
            Instruction.FPTrunc(ref, ref, Type.F32),
            Instruction.FPExt(ref, ref, Type.F64),
            Instruction.FPToUI(ref, ref, Type.I32),
            Instruction.FPToSI(ref, ref, Type.I32),
            Instruction.UIToFP(ref, ref, Type.F64),
            Instruction.SIToFP(ref, ref, Type.F64),
            Instruction.PtrToInt(ref, ptr, Type.I64),
            Instruction.IntToPtr(ref, ref, Type.Pointer(Type.I32)),
            Instruction.BitCast(ref, ref, Type.F32),
            Instruction.AddrSpaceCast(ref, ptr, Type.Pointer(Type.I32, 1)),

            // Terminator
            Instruction.Ret(ref),
            Instruction.Br("target"),
            Instruction.CondBr(ref, "t", "f"),
            Instruction.Switch(ref, "default", emptyList()),
            Instruction.IndirectBr(ptr, listOf("a", "b")),
            Instruction.Unreachable(),
            Instruction.Trap(),
            Instruction.DebugTrap(),

            // Call
            Instruction.Call(ref, funcRef, listOf(ref), Type.I32),
            Instruction.Invoke(ref, funcRef, listOf(ref), Type.I32, "normal", "unwind"),
            Instruction.CallBr(ref, funcRef, listOf(ref), Type.I32, "fall", listOf("a")),
            Instruction.VAStart(ptr),
            Instruction.VAEnd(ptr),
            Instruction.VACopy(ptr, ptr),
            Instruction.VAArg(ref, ptr, Type.I32),

            // SSA
            Instruction.Phi(ref, listOf(ref to "a", ref2 to "b")),
            Instruction.Select(ref, ref, ref, ref2),
            Instruction.Freeze(ref, ref),

            // Vector
            Instruction.ExtractElement(ref, vec, ref),
            Instruction.InsertElement(ref, vec, ref, ref2),
            Instruction.ShuffleVector(ref, vec, vec, listOf(0, 1, 2, 3)),
            Instruction.Splat(ref, ref, vecType),
            Instruction.VectorReduce(ref, VectorReduceOp.ADD, vec),

            // Aggregate
            Instruction.ExtractValue(ref, structVal, listOf(0)),
            Instruction.InsertValue(ref, structVal, ref, listOf(0)),

            // Exception
            Instruction.LandingPad(ref, Type.I32, emptyList()),
            Instruction.Resume(ref),
            Instruction.CatchSwitch(ref, null, listOf("h1"), null),
            Instruction.CatchPad(ref, ref, emptyList()),
            Instruction.CleanupPad(ref, null, emptyList()),
            Instruction.CatchRet(ref, "dest"),
            Instruction.CleanupRet(ref, null),
            Instruction.Throw(ref),
            Instruction.TryCatchRegion("try", emptyList()),

            // Object
            Instruction.NewObject(ref, "Foo"),
            Instruction.NewArray(ref, Type.I32, ref),
            Instruction.NewMultiArray(ref, Type.I32, listOf(ref)),
            Instruction.GetField(ref, ref, "Foo", "x", Type.I32),
            Instruction.PutField(ref, "Foo", "x", Type.I32, ref2),
            Instruction.GetStatic(ref, "Foo", "y", Type.I32),
            Instruction.PutStatic("Foo", "y", Type.I32, ref),
            Instruction.VirtualCall(ref, ref, "Foo", "bar", funcType, listOf(ref)),
            Instruction.InterfaceCall(ref, ref, "IFoo", "bar", funcType, listOf(ref)),
            Instruction.SpecialCall(ref, ref, "Foo", "bar", funcType, listOf(ref)),
            Instruction.StaticCall(ref, "Foo", "bar", funcType, listOf(ref)),
            Instruction.DynamicCall(ref, BootstrapMethod("Cls", "bsm", funcType), "name", funcType, listOf(ref)),
            Instruction.ConstructorCall(ref, "Foo", funcType, listOf(ref)),
            Instruction.InstanceOf(ref, ref, Type.ClassRef("Foo")),
            Instruction.CheckCast(ref, ref, Type.ClassRef("Foo")),
            Instruction.TypeId(ref, ref),
            Instruction.ArrayGet(ref, ref, ref2, Type.I32),
            Instruction.ArraySet(ref, ref2, ref, Type.I32),
            Instruction.ArrayLength(ref, ref),
            Instruction.MonitorEnter(ref),
            Instruction.MonitorExit(ref),
            Instruction.Box(ref, ref, Type.ClassRef("Integer")),
            Instruction.Unbox(ref, ref, Type.I32),
            Instruction.ClosureCreate(ref, funcRef, listOf(ref), funcType),
            Instruction.ClosureInvoke(ref, ref, listOf(ref), Type.I32),
            Instruction.ConstructVariant(ref, taggedUnion, "A", listOf(ref)),
            Instruction.GetTag(ref, unionVal),
            Instruction.GetVariantField(ref, unionVal, "A", 0),
            Instruction.TagSwitch(unionVal, listOf("A" to "block_a")),
            Instruction.CatchValue(ref, Type.ClassRef("Exception")),
            Instruction.MakeWeakRef(ref, ref),
            Instruction.ReadWeakRef(ref, ref),
            Instruction.ClearWeakRef(ref),

            // Runtime
            Instruction.GCAlloc(ref, Type.I32),
            Instruction.GCSafepoint(),
            Instruction.GCRoot(ptr, null),
            Instruction.Pin(ref, ref),
            Instruction.Unpin(ref),
            Instruction.InteriorPtr(ref, ref, ref2, Type.I32),
            Instruction.WriteBarrier(ref, ref2, ref),
            Instruction.ReadBarrier(ref, ref),
            Instruction.ManagedCall(ref, funcRef, listOf(ref), Type.I32, ManagedCallDirection.MANAGED_TO_NATIVE),
            Instruction.RefRetain(ref),
            Instruction.RefRelease(ref),
            Instruction.RefCount(ref, ref),
            Instruction.CoroBegin(ref, ref, ptr),
            Instruction.CoroEnd(ref),
            Instruction.CoroSuspend(ref, null),
            Instruction.CoroResume(ref),
            Instruction.CoroDestroy(ref),
            Instruction.CoroSize(ref),

            // Debug
            Instruction.DebugLoc(1, 1, "scope"),
            Instruction.DebugValue("x", ref),
            Instruction.DebugDeclare("x", ptr),
            Instruction.Assume(ref),
            Instruction.Expect(ref, ref, Constant.I32(1)),

            // Intrinsic
            Instruction.Intrinsic(ref, "llvm.sqrt", listOf(ref), Type.F64),
            Instruction.InlineAsm(ref, "nop", "", args = emptyList()),
        )
    }

    @Test
    fun everyInstructionHasCategory() {
        for (instr in allInstructions) {
            assertNotNull(instr.category, "${instr::class.simpleName} should have a category")
        }
    }

    @Test
    fun arithmeticInstructionsAreCategorizedCorrectly() {
        val arithmetic = allInstructions.filter { it.category == IrCategory.ARITHMETIC }
        assertTrue(arithmetic.size >= 27, "Expected at least 27 arithmetic instructions, got ${arithmetic.size}")
        assertTrue(arithmetic.any { it is Instruction.Add })
        assertTrue(arithmetic.any { it is Instruction.FMA })
        assertTrue(arithmetic.any { it is Instruction.Sqrt })
    }

    @Test
    fun bitwiseInstructionsAreCategorizedCorrectly() {
        val bitwise = allInstructions.filter { it.category == IrCategory.BITWISE }
        assertTrue(bitwise.size >= 14, "Expected at least 14 bitwise instructions, got ${bitwise.size}")
        assertTrue(bitwise.any { it is Instruction.And })
        assertTrue(bitwise.any { it is Instruction.Ctlz })
        assertTrue(bitwise.any { it is Instruction.BSwap })
    }

    @Test
    fun memoryInstructionsAreCategorizedCorrectly() {
        val memory = allInstructions.filter { it.category == IrCategory.MEMORY }
        assertTrue(memory.any { it is Instruction.Alloca })
        assertTrue(memory.any { it is Instruction.Load })
        assertTrue(memory.any { it is Instruction.Store })
        assertTrue(memory.any { it is Instruction.GetElementPtr })
        assertTrue(memory.any { it is Instruction.MemCpy })
        assertTrue(memory.any { it is Instruction.StackSave })
        assertTrue(memory.any { it is Instruction.LifetimeStart })
    }

    @Test
    fun atomicInstructionsAreCategorizedCorrectly() {
        val atomic = allInstructions.filter { it.category == IrCategory.ATOMIC }
        assertEquals(3, atomic.size)
        assertTrue(atomic.any { it is Instruction.Fence })
        assertTrue(atomic.any { it is Instruction.CmpXchg })
        assertTrue(atomic.any { it is Instruction.AtomicRMW })
    }

    @Test
    fun objectInstructionsAreCategorizedCorrectly() {
        val obj = allInstructions.filter { it.category == IrCategory.OBJECT }
        assertTrue(obj.size >= 31, "Expected at least 31 object instructions, got ${obj.size}")
        assertTrue(obj.any { it is Instruction.NewObject })
        assertTrue(obj.any { it is Instruction.VirtualCall })
        assertTrue(obj.any { it is Instruction.ClosureCreate })
        assertTrue(obj.any { it is Instruction.TagSwitch })
        assertTrue(obj.any { it is Instruction.Throw })
        assertTrue(obj.any { it is Instruction.TryCatchRegion })
    }

    @Test
    fun runtimeInstructionsAreCategorizedCorrectly() {
        val runtime = allInstructions.filter { it.category == IrCategory.RUNTIME }
        assertTrue(runtime.size >= 12, "Expected at least 12 runtime instructions, got ${runtime.size}")
        assertTrue(runtime.any { it is Instruction.GCAlloc })
        assertTrue(runtime.any { it is Instruction.WriteBarrier })
        assertTrue(runtime.any { it is Instruction.CoroBegin })
        assertTrue(runtime.any { it is Instruction.RefRetain })
    }

    @Test
    fun exceptionInstructionsAreCategorizedCorrectly() {
        val eh = allInstructions.filter { it.category == IrCategory.EXCEPTION }
        assertTrue(eh.any { it is Instruction.LandingPad })
        assertTrue(eh.any { it is Instruction.CatchSwitch })
    }

    @Test
    fun interopInstructionsAreCategorizedCorrectly() {
        val interop = allInstructions.filter { it.category == IrCategory.INTEROP }
        assertTrue(interop.any { it is Instruction.Pin })
        assertTrue(interop.any { it is Instruction.Unpin })
        assertTrue(interop.any { it is Instruction.InteriorPtr })
        assertTrue(interop.any { it is Instruction.ManagedCall })
    }

    @Test
    fun terminatorInstructionsAreCategorizedCorrectly() {
        val term = allInstructions.filter { it.category == IrCategory.TERMINATOR }
        assertTrue(term.any { it is Instruction.Ret })
        assertTrue(term.any { it is Instruction.Br })
        assertTrue(term.any { it is Instruction.CondBr })
        assertTrue(term.any { it is Instruction.Unreachable })
        assertTrue(term.any { it is Instruction.Trap })
    }

    @Test
    fun debugInstructionsAreCategorizedCorrectly() {
        val debug = allInstructions.filter { it.category == IrCategory.DEBUG }
        assertEquals(5, debug.size)
    }

    @Test
    fun intrinsicInstructionsAreCategorizedCorrectly() {
        val intr = allInstructions.filter { it.category == IrCategory.INTRINSIC }
        assertEquals(2, intr.size)
    }

    @Test
    fun allCategoriesHaveAtLeastOneInstruction() {
        val usedCategories = allInstructions.map { it.category }.toSet()
        for (cat in IrCategory.entries) {
            assertTrue(cat in usedCategories, "Category $cat has no instructions in test list")
        }
    }

    // --- Tier level values ---

    @Test
    fun tierEnumValues() {
        assertEquals(0, IrTier.STRUCTURAL.level)
        assertEquals(1, IrTier.MACHINE.level)
        assertEquals(2, IrTier.RUNTIME.level)
        assertEquals(2, IrTier.INTEROP.level)
        assertEquals(3, IrTier.OBJECT.level)
    }

    @Test
    fun allCategoriesCovered() {
        assertEquals(17, IrCategory.entries.size)
    }

    @Test
    fun allTiersCovered() {
        assertEquals(5, IrTier.entries.size)
    }
}
