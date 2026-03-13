package org.kgen.ir

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.kgen.ir.instructions.*

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
            Add(ref, ref, ref2),
            Sub(ref, ref, ref2),
            Mul(ref, ref, ref2),
            UDiv(ref, ref, ref2),
            SDiv(ref, ref, ref2),
            URem(ref, ref, ref2),
            SRem(ref, ref, ref2),
            Neg(ref, ref),
            SAddOverflow(ref, ref, ref2),
            UAddOverflow(ref, ref, ref2),
            SSubOverflow(ref, ref, ref2),
            USubOverflow(ref, ref, ref2),
            SMulOverflow(ref, ref, ref2),
            UMulOverflow(ref, ref, ref2),
            SAddSat(ref, ref, ref2),
            UAddSat(ref, ref, ref2),
            SSubSat(ref, ref, ref2),
            USubSat(ref, ref, ref2),
            SMin(ref, ref, ref2),
            SMax(ref, ref, ref2),
            UMin(ref, ref, ref2),
            UMax(ref, ref, ref2),
            Abs(ref, ref),
            FAdd(ref, ref, ref2),
            FSub(ref, ref, ref2),
            FMul(ref, ref, ref2),
            FDiv(ref, ref, ref2),
            FRem(ref, ref, ref2),
            FNeg(ref, ref),
            FAbs(ref, ref),
            FMA(ref, ref, ref2, ref),
            FMin(ref, ref, ref2),
            FMax(ref, ref, ref2),
            Sqrt(ref, ref),
            Ceil(ref, ref),
            Floor(ref, ref),
            Round(ref, ref),
            Trunc(ref, ref),
            CopySign(ref, ref, ref2),

            // Bitwise
            And(ref, ref, ref2),
            Or(ref, ref, ref2),
            Xor(ref, ref, ref2),
            Not(ref, ref),
            Shl(ref, ref, ref2),
            LShr(ref, ref, ref2),
            AShr(ref, ref, ref2),
            RotateLeft(ref, ref, ref2),
            RotateRight(ref, ref, ref2),
            Ctlz(ref, ref),
            Cttz(ref, ref),
            Ctpop(ref, ref),
            BSwap(ref, ref),
            BitReverse(ref, ref),
            Rotl(ref, ref, ref2),
            Rotr(ref, ref, ref2),

            // Comparison
            ICmp(ref, ICmpPredicate.EQ, ref, ref2),
            FCmp(ref, FCmpPredicate.OEQ, ref, ref2),

            // Memory
            Alloca(ref, Type.I32),
            Load(ref, ptr, Type.I32),
            Store(ref, ptr),
            GetElementPtr(ref, Type.I32, ptr, listOf(ref)),
            MemCpy(ptr, ptr, ref),
            MemSet(ptr, ref, ref2),
            MemMove(ptr, ptr, ref),
            Prefetch(ptr, 0, 3, 1),
            StackSave(ref),
            StackRestore(ptr),
            LifetimeStart(ptr, 4),
            LifetimeEnd(ptr, 4),

            // Atomic
            Fence(AtomicOrdering.SEQ_CST),
            CmpXchg(ref, ptr, ref, ref2, AtomicOrdering.SEQ_CST, AtomicOrdering.ACQUIRE),
            AtomicRMW(ref, AtomicRMWOp.ADD, ptr, ref, AtomicOrdering.SEQ_CST),

            // Conversion
            IntTrunc(ref, ref, Type.I16),
            ZExt(ref, ref, Type.I64),
            SExt(ref, ref, Type.I64),
            FPTrunc(ref, ref, Type.F32),
            FPExt(ref, ref, Type.F64),
            FPToUI(ref, ref, Type.I32),
            FPToSI(ref, ref, Type.I32),
            UIToFP(ref, ref, Type.F64),
            SIToFP(ref, ref, Type.F64),
            PtrToInt(ref, ptr, Type.I64),
            IntToPtr(ref, ref, Type.Pointer(Type.I32)),
            BitCast(ref, ref, Type.F32),
            AddrSpaceCast(ref, ptr, Type.Pointer(Type.I32, 1)),

            // Terminator
            Ret(ref),
            Br("target"),
            CondBr(ref, "t", "f"),
            Switch(ref, "default", emptyList()),
            IndirectBr(ptr, listOf("a", "b")),
            Unreachable(),
            Trap(),
            DebugTrap(),

            // Call
            Call(ref, funcRef, listOf(ref), Type.I32),
            Invoke(ref, funcRef, listOf(ref), Type.I32, "normal", "unwind"),
            CallBr(ref, funcRef, listOf(ref), Type.I32, "fall", listOf("a")),
            VAStart(ptr),
            VAEnd(ptr),
            VACopy(ptr, ptr),
            VAArg(ref, ptr, Type.I32),

            // SSA
            Phi(ref, listOf(ref to "a", ref2 to "b")),
            Select(ref, ref, ref, ref2),
            Freeze(ref, ref),

            // Vector
            ExtractElement(ref, vec, ref),
            InsertElement(ref, vec, ref, ref2),
            ShuffleVector(ref, vec, vec, listOf(0, 1, 2, 3)),
            Splat(ref, ref, vecType),
            VectorReduce(ref, VectorReduceOp.ADD, vec),

            // Aggregate
            ExtractValue(ref, structVal, listOf(0)),
            InsertValue(ref, structVal, ref, listOf(0)),

            // Exception
            LandingPad(ref, Type.I32, emptyList()),
            Resume(ref),
            CatchSwitch(ref, null, listOf("h1"), null),
            CatchPad(ref, ref, emptyList()),
            CleanupPad(ref, null, emptyList()),
            CatchRet(ref, "dest"),
            CleanupRet(ref, null),
            Throw(ref),
            TryCatchRegion("try", emptyList()),

            // Object
            NewObject(ref, "Foo"),
            NewArray(ref, Type.I32, ref),
            NewMultiArray(ref, Type.I32, listOf(ref)),
            GetField(ref, ref, "Foo", "x", Type.I32),
            PutField(ref, "Foo", "x", Type.I32, ref2),
            GetStatic(ref, "Foo", "y", Type.I32),
            PutStatic("Foo", "y", Type.I32, ref),
            VirtualCall(ref, ref, "Foo", "bar", funcType, listOf(ref)),
            InterfaceCall(ref, ref, "IFoo", "bar", funcType, listOf(ref)),
            SpecialCall(ref, ref, "Foo", "bar", funcType, listOf(ref)),
            StaticCall(ref, "Foo", "bar", funcType, listOf(ref)),
            DynamicCall(ref, BootstrapMethod("Cls", "bsm", funcType), "name", funcType, listOf(ref)),
            ConstructorCall(ref, "Foo", funcType, listOf(ref)),
            InstanceOf(ref, ref, Type.ClassRef("Foo")),
            CheckCast(ref, ref, Type.ClassRef("Foo")),
            TypeId(ref, ref),
            ArrayGet(ref, ref, ref2, Type.I32),
            ArraySet(ref, ref2, ref, Type.I32),
            ArrayLength(ref, ref),
            MonitorEnter(ref),
            MonitorExit(ref),
            Box(ref, ref, Type.ClassRef("Integer")),
            Unbox(ref, ref, Type.I32),
            ClosureCreate(ref, funcRef, listOf(ref), funcType),
            ClosureInvoke(ref, ref, listOf(ref), Type.I32),
            ConstructVariant(ref, taggedUnion, "A", listOf(ref)),
            GetTag(ref, unionVal),
            GetVariantField(ref, unionVal, "A", 0),
            TagSwitch(unionVal, listOf("A" to "block_a")),
            CatchValue(ref, Type.ClassRef("Exception")),
            MakeWeakRef(ref, ref),
            ReadWeakRef(ref, ref),
            ClearWeakRef(ref),

            // Runtime
            GCAlloc(ref, Type.I32),
            GCSafepoint(),
            GCRoot(ptr, null),
            Pin(ref, ref),
            Unpin(ref),
            InteriorPtr(ref, ref, ref2, Type.I32),
            WriteBarrier(ref, ref2, ref),
            ReadBarrier(ref, ref),
            ManagedCall(ref, funcRef, listOf(ref), Type.I32, ManagedCallDirection.MANAGED_TO_NATIVE),
            RefRetain(ref),
            RefRelease(ref),
            RefCount(ref, ref),
            CoroBegin(ref, ref, ptr),
            CoroEnd(ref),
            CoroSuspend(ref, null),
            CoroResume(ref),
            CoroDestroy(ref),
            CoroSize(ref),

            // Debug
            DebugLoc(1, 1, "scope"),
            DebugValue("x", ref),
            DebugDeclare("x", ptr),
            Assume(ref),
            Expect(ref, ref, Constant.I32(1)),

            // Intrinsic
            Intrinsic(ref, "llvm.sqrt", listOf(ref), Type.F64),
            InlineAsm(ref, "nop", "", args = emptyList()),
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
        assertTrue(arithmetic.any { it is Add })
        assertTrue(arithmetic.any { it is FMA })
        assertTrue(arithmetic.any { it is Sqrt })
    }

    @Test
    fun bitwiseInstructionsAreCategorizedCorrectly() {
        val bitwise = allInstructions.filter { it.category == IrCategory.BITWISE }
        assertTrue(bitwise.size >= 14, "Expected at least 14 bitwise instructions, got ${bitwise.size}")
        assertTrue(bitwise.any { it is And })
        assertTrue(bitwise.any { it is Ctlz })
        assertTrue(bitwise.any { it is BSwap })
    }

    @Test
    fun memoryInstructionsAreCategorizedCorrectly() {
        val memory = allInstructions.filter { it.category == IrCategory.MEMORY }
        assertTrue(memory.any { it is Alloca })
        assertTrue(memory.any { it is Load })
        assertTrue(memory.any { it is Store })
        assertTrue(memory.any { it is GetElementPtr })
        assertTrue(memory.any { it is MemCpy })
        assertTrue(memory.any { it is StackSave })
        assertTrue(memory.any { it is LifetimeStart })
    }

    @Test
    fun atomicInstructionsAreCategorizedCorrectly() {
        val atomic = allInstructions.filter { it.category == IrCategory.ATOMIC }
        assertEquals(3, atomic.size)
        assertTrue(atomic.any { it is Fence })
        assertTrue(atomic.any { it is CmpXchg })
        assertTrue(atomic.any { it is AtomicRMW })
    }

    @Test
    fun objectInstructionsAreCategorizedCorrectly() {
        val obj = allInstructions.filter { it.category == IrCategory.OBJECT }
        assertTrue(obj.size >= 31, "Expected at least 31 object instructions, got ${obj.size}")
        assertTrue(obj.any { it is NewObject })
        assertTrue(obj.any { it is VirtualCall })
        assertTrue(obj.any { it is ClosureCreate })
        assertTrue(obj.any { it is TagSwitch })
        assertTrue(obj.any { it is Throw })
        assertTrue(obj.any { it is TryCatchRegion })
    }

    @Test
    fun runtimeInstructionsAreCategorizedCorrectly() {
        val runtime = allInstructions.filter { it.category == IrCategory.RUNTIME }
        assertTrue(runtime.size >= 12, "Expected at least 12 runtime instructions, got ${runtime.size}")
        assertTrue(runtime.any { it is GCAlloc })
        assertTrue(runtime.any { it is WriteBarrier })
        assertTrue(runtime.any { it is CoroBegin })
        assertTrue(runtime.any { it is RefRetain })
    }

    @Test
    fun exceptionInstructionsAreCategorizedCorrectly() {
        val eh = allInstructions.filter { it.category == IrCategory.EXCEPTION }
        assertTrue(eh.any { it is LandingPad })
        assertTrue(eh.any { it is CatchSwitch })
    }

    @Test
    fun interopInstructionsAreCategorizedCorrectly() {
        val interop = allInstructions.filter { it.category == IrCategory.INTEROP }
        assertTrue(interop.any { it is Pin })
        assertTrue(interop.any { it is Unpin })
        assertTrue(interop.any { it is InteriorPtr })
        assertTrue(interop.any { it is ManagedCall })
    }

    @Test
    fun terminatorInstructionsAreCategorizedCorrectly() {
        val term = allInstructions.filter { it.category == IrCategory.TERMINATOR }
        assertTrue(term.any { it is Ret })
        assertTrue(term.any { it is Br })
        assertTrue(term.any { it is CondBr })
        assertTrue(term.any { it is Unreachable })
        assertTrue(term.any { it is Trap })
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
