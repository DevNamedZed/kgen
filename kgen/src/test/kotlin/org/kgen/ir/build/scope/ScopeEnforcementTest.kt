package org.kgen.ir.build.scope

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.kgen.ir.*
import org.kgen.ir.build.FunctionContext
import org.kgen.ir.build.InstructionSink
import org.kgen.ir.build.ModuleBuilder
import org.kgen.ir.build.sets.ArithmeticInstructionSet
import org.kgen.ir.build.sets.InstructionBuilder
import org.kgen.ir.build.sets.InstructionSet
import org.kgen.ir.build.sets.TerminatorInstructionSet
import org.kgen.ir.target.Target
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ScopeEnforcementTest {

    private fun createSink(): InstructionSink {
        val context = FunctionContext(
            "test_fn",
            listOf(Parameter("a", Type.I32, 0), Parameter("b", Type.I32, 1)),
            Type.I32,
            null,
        )
        context.appendBlock("entry")
        return context
    }

    // --- NativeScope: has arithmetic, bitwise, memory, comparison methods ---

    @Test
    fun nativeScopeHasArithmeticMethods() {
        val proxy = InstructionBuilder.create(NativeScope::class.java, createSink())
        val paramA = Parameter("a", Type.I32, 0)
        val paramB = Parameter("b", Type.I32, 1)

        val result = proxy.add(paramA, paramB)
        assertNotNull(result)
    }

    @Test
    fun nativeScopeHasBitwiseMethods() {
        val proxy = InstructionBuilder.create(NativeScope::class.java, createSink())
        val paramA = Parameter("a", Type.I32, 0)
        val paramB = Parameter("b", Type.I32, 1)

        val result = proxy.and(paramA, paramB)
        assertNotNull(result)
    }

    @Test
    fun nativeScopeHasMemoryMethods() {
        val proxy = InstructionBuilder.create(NativeScope::class.java, createSink())
        val allocated = proxy.alloca(Type.I32)
        assertNotNull(allocated)
    }

    @Test
    fun nativeScopeHasComparisonMethods() {
        val proxy = InstructionBuilder.create(NativeScope::class.java, createSink())
        val paramA = Parameter("a", Type.I32, 0)
        val paramB = Parameter("b", Type.I32, 1)

        val result = proxy.icmp(ICmpPredicate.EQ, paramA, paramB)
        assertNotNull(result)
    }

    // --- NativeScope: does NOT have object methods ---

    @Test
    fun nativeScopeDoesNotExposeObjectMethods() {
        val nativeScopeMethods = NativeScope::class.java.methods.map { it.name }.toSet()

        assertFalse("newObject" in nativeScopeMethods,
            "NativeScope should not have newObject")
        assertFalse("putField" in nativeScopeMethods,
            "NativeScope should not have putField")
        assertFalse("getField" in nativeScopeMethods,
            "NativeScope should not have getField")
        assertFalse("virtualCall" in nativeScopeMethods,
            "NativeScope should not have virtualCall")
        assertFalse("newArray" in nativeScopeMethods,
            "NativeScope should not have newArray")
        assertFalse("instanceOf" in nativeScopeMethods,
            "NativeScope should not have instanceOf")
    }

    // --- ManagedScope: has object methods ---

    @Test
    fun managedScopeHasObjectMethods() {
        val proxy = InstructionBuilder.create(ManagedScope::class.java, createSink())
        val obj = proxy.newObject("Widget")
        assertNotNull(obj)
    }

    @Test
    fun managedScopeHasPutFieldMethod() {
        val proxy = InstructionBuilder.create(ManagedScope::class.java, createSink())
        val obj = proxy.newObject("Widget")
        proxy.putField(obj, "Widget", "count", Type.I32, Constant.I32(0))
    }

    @Test
    fun managedScopeHasGetFieldMethod() {
        val proxy = InstructionBuilder.create(ManagedScope::class.java, createSink())
        val obj = proxy.newObject("Widget")
        val fieldValue = proxy.getField(obj, "Widget", "count", Type.I32)
        assertNotNull(fieldValue)
    }

    // --- ManagedScope: does NOT have memory methods ---

    @Test
    fun managedScopeDoesNotExposeMemoryMethods() {
        val managedScopeMethods = ManagedScope::class.java.methods.map { it.name }.toSet()

        assertFalse("alloca" in managedScopeMethods,
            "ManagedScope should not have alloca")
        assertFalse("store" in managedScopeMethods,
            "ManagedScope should not have store")
        assertFalse("load" in managedScopeMethods,
            "ManagedScope should not have load")
        assertFalse("gep" in managedScopeMethods,
            "ManagedScope should not have gep")
        assertFalse("memcpy" in managedScopeMethods,
            "ManagedScope should not have memcpy")
    }

    @Test
    fun managedScopeDoesNotExposeBitwiseMethods() {
        val managedScopeMethods = ManagedScope::class.java.methods.map { it.name }.toSet()

        assertFalse("and" in managedScopeMethods,
            "ManagedScope should not have and")
        assertFalse("or" in managedScopeMethods,
            "ManagedScope should not have or")
        assertFalse("shl" in managedScopeMethods,
            "ManagedScope should not have shl")
    }

    // --- Extension methods work through the proxy ---

    @Test
    fun comparisonExtensionEq() {
        val proxy = InstructionBuilder.create(NativeScope::class.java, createSink())
        val paramA = Parameter("a", Type.I32, 0)
        val paramB = Parameter("b", Type.I32, 1)

        val result = proxy.eq(paramA, paramB)
        assertNotNull(result)
        assertEquals(Type.I1, result.type)
    }

    @Test
    fun comparisonExtensionLt() {
        val proxy = InstructionBuilder.create(NativeScope::class.java, createSink())
        val paramA = Parameter("a", Type.I32, 0)
        val paramB = Parameter("b", Type.I32, 1)

        val result = proxy.lt(paramA, paramB)
        assertNotNull(result)
    }

    @Test
    fun memoryExtensionVariable() {
        val proxy = InstructionBuilder.create(NativeScope::class.java, createSink())
        val varRef = proxy.variable(Constant.I32(42))
        assertNotNull(varRef)
        assertEquals(Type.I32, varRef.type)
    }

    @Test
    fun memoryExtensionGetAndSet() {
        val proxy = InstructionBuilder.create(NativeScope::class.java, createSink())
        val varRef = proxy.variable(Constant.I32(0))

        val loaded = proxy.get(varRef)
        assertNotNull(loaded)

        proxy.set(varRef, Constant.I32(99))
    }

    @Test
    fun arithmeticExtensionDiv() {
        val proxy = InstructionBuilder.create(NativeScope::class.java, createSink())
        val paramA = Parameter("a", Type.I32, 0)
        val paramB = Parameter("b", Type.I32, 1)

        val result = proxy.div(paramA, paramB)
        assertNotNull(result)
    }

    @Test
    fun arithmeticExtensionRem() {
        val proxy = InstructionBuilder.create(NativeScope::class.java, createSink())
        val paramA = Parameter("a", Type.I32, 0)
        val paramB = Parameter("b", Type.I32, 1)

        val result = proxy.rem(paramA, paramB)
        assertNotNull(result)
    }

    @Test
    fun bitwiseExtensionShr() {
        val proxy = InstructionBuilder.create(NativeScope::class.java, createSink())
        val paramA = Parameter("a", Type.I32, 0)
        val shiftAmount = Constant.I32(2)

        val result = proxy.shr(paramA, shiftAmount)
        assertNotNull(result)
    }

    // --- FullScope has everything ---

    @Test
    fun fullScopeHasArithmeticMethods() {
        val proxy = InstructionBuilder.create(FullScope::class.java, createSink())
        val result = proxy.add(Parameter("a", Type.I32, 0), Parameter("b", Type.I32, 1))
        assertNotNull(result)
    }

    @Test
    fun fullScopeHasObjectMethods() {
        val proxy = InstructionBuilder.create(FullScope::class.java, createSink())
        val obj = proxy.newObject("Thing")
        assertNotNull(obj)
    }

    @Test
    fun fullScopeHasMemoryMethods() {
        val proxy = InstructionBuilder.create(FullScope::class.java, createSink())
        val ptr = proxy.alloca(Type.I64)
        assertNotNull(ptr)
    }

    @Test
    fun fullScopeHasBitwiseMethods() {
        val proxy = InstructionBuilder.create(FullScope::class.java, createSink())
        val result = proxy.xor(Parameter("a", Type.I32, 0), Parameter("b", Type.I32, 1))
        assertNotNull(result)
    }

    @Test
    fun fullScopeHasRuntimeMethods() {
        val fullScopeMethods = FullScope::class.java.methods.map { it.name }.toSet()
        assertTrue("alloca" in fullScopeMethods)
        assertTrue("newObject" in fullScopeMethods)
        assertTrue("add" in fullScopeMethods)
        assertTrue("and" in fullScopeMethods)
    }

    // --- ComputeScope has compute instructions ---

    @Test
    fun computeScopeHasComputeInstructions() {
        val computeMethods = ComputeScope::class.java.methods.map { it.name }.toSet()
        assertTrue("threadId" in computeMethods)
        assertTrue("blockId" in computeMethods)
        assertTrue("computeBarrier" in computeMethods)
        assertTrue("sharedMemAlloc" in computeMethods)
    }

    @Test
    fun computeScopeHasNativeInstructions() {
        val computeMethods = ComputeScope::class.java.methods.map { it.name }.toSet()
        assertTrue("add" in computeMethods)
        assertTrue("alloca" in computeMethods)
        assertTrue("and" in computeMethods)
    }

    @Test
    fun computeScopeDoesNotHaveObjectInstructions() {
        val computeMethods = ComputeScope::class.java.methods.map { it.name }.toSet()
        assertFalse("newObject" in computeMethods)
        assertFalse("putField" in computeMethods)
    }

    // --- Custom scope composition ---

    interface MinimalScope : ArithmeticInstructionSet, TerminatorInstructionSet

    @Test
    fun customScopeWithMinimalInstructionSets() {
        val proxy = InstructionBuilder.create(MinimalScope::class.java, createSink())
        val result = proxy.add(Parameter("a", Type.I32, 0), Parameter("b", Type.I32, 1))
        assertNotNull(result)
    }

    @Test
    fun customScopeDoesNotExposeUnincludedSets() {
        val minimalMethods = MinimalScope::class.java.methods.map { it.name }.toSet()
        assertFalse("alloca" in minimalMethods, "MinimalScope should not have alloca")
        assertFalse("newObject" in minimalMethods, "MinimalScope should not have newObject")
        assertFalse("and" in minimalMethods, "MinimalScope should not have and")
    }

    // --- InstructionBuilder.create with invalid scope ---

    interface EmptyScope : InstructionSet

    @Test
    fun createWithNoInstructionSetsThrows() {
        assertThrows<IllegalArgumentException> {
            InstructionBuilder.create(EmptyScope::class.java, createSink())
        }
    }

    // --- Proxy handles default methods from extensions correctly ---

    @Test
    fun extensionDefaultMethodsDelegateCorrectly() {
        val proxy = InstructionBuilder.create(NativeScope::class.java, createSink())
        val paramA = Parameter("a", Type.I32, 0)
        val paramB = Parameter("b", Type.I32, 1)

        val eqResult = proxy.eq(paramA, paramB)
        val neResult = proxy.ne(paramA, paramB)
        val ltResult = proxy.lt(paramA, paramB)
        val gtResult = proxy.gt(paramA, paramB)
        val leResult = proxy.le(paramA, paramB)
        val geResult = proxy.ge(paramA, paramB)

        for (result in listOf(eqResult, neResult, ltResult, gtResult, leResult, geResult)) {
            assertNotNull(result)
            assertEquals(Type.I1, result.type)
        }
    }

    @Test
    fun memoryExtensionVariableUsesAllocaAndStore() {
        val module = ModuleBuilder("test", Target.x86_64())
        val fn = module.createFunction(NativeScope::class.java, "test_var",
            listOf(Param("n", Type.I32)), Type.I32)

        val ins = fn.instructions
        val varRef = ins.variable(Constant.I32(10))

        val value = ins.get(varRef)
        ins.set(varRef, ins.add(value, fn.param(0)))
        fn.ret(ins.get(varRef))
        fn.end()

        val ir = module.build()
        val func = ir.functions.first()
        val instructions = func.blocks.flatMap { it.instructions }

        val hasAlloca = instructions.any { it::class.simpleName == "Alloca" }
        val hasLoad = instructions.any { it::class.simpleName == "Load" }
        val hasStore = instructions.any { it::class.simpleName == "Store" }

        assertTrue(hasAlloca, "variable() should emit an Alloca")
        assertTrue(hasLoad, "get() should emit a Load")
        assertTrue(hasStore, "set() should emit a Store")
    }

    @Test
    fun extensionDivDelegatesToSdiv() {
        val module = ModuleBuilder("test", Target.x86_64())
        val fn = module.createFunction(NativeScope::class.java, "test_div",
            listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)

        val ins = fn.instructions
        val result = ins.div(fn.param(0), fn.param(1))
        fn.ret(result)
        fn.end()

        val ir = module.build()
        val func = ir.functions.first()
        val instructions = func.blocks.flatMap { it.instructions }

        val hasSDiv = instructions.any { it::class.simpleName == "SDiv" }
        assertTrue(hasSDiv, "div() extension should delegate to sdiv")
    }

    @Test
    fun extensionRemDelegatesToSrem() {
        val module = ModuleBuilder("test", Target.x86_64())
        val fn = module.createFunction(NativeScope::class.java, "test_rem",
            listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)

        val ins = fn.instructions
        val result = ins.rem(fn.param(0), fn.param(1))
        fn.ret(result)
        fn.end()

        val ir = module.build()
        val func = ir.functions.first()
        val instructions = func.blocks.flatMap { it.instructions }

        val hasSRem = instructions.any { it::class.simpleName == "SRem" }
        assertTrue(hasSRem, "rem() extension should delegate to srem")
    }

    @Test
    fun extensionShrDelegatesToAshr() {
        val module = ModuleBuilder("test", Target.x86_64())
        val fn = module.createFunction(NativeScope::class.java, "test_shr",
            listOf(Param("a", Type.I32)), Type.I32)

        val ins = fn.instructions
        val result = ins.shr(fn.param(0), Constant.I32(2))
        fn.ret(result)
        fn.end()

        val ir = module.build()
        val func = ir.functions.first()
        val instructions = func.blocks.flatMap { it.instructions }

        val hasAShr = instructions.any { it::class.simpleName == "AShr" }
        assertTrue(hasAShr, "shr() extension should delegate to ashr")
    }

    // --- End-to-end: typed proxy through FunctionBuilder ---

    @Test
    fun functionBuilderNativeScopeProxy() {
        val module = ModuleBuilder("test", Target.x86_64())
        val fn = module.createFunction(NativeScope::class.java, "sum",
            listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)

        val ins = fn.instructions
        val sum = ins.add(fn.param(0), fn.param(1))
        fn.ret(sum)
        fn.end()

        val ir = module.build()
        assertEquals(1, ir.functions.size)
        assertEquals("sum", ir.functions.first().name)
    }

    @Test
    fun functionBuilderManagedScopeProxy() {
        val module = ModuleBuilder("test", Target.jvm())
        val fn = module.createFunction(ManagedScope::class.java, "createWidget",
            emptyList(), Type.OpaquePointer)

        val ins = fn.instructions
        val widget = ins.newObject("Widget")
        ins.putField(widget, "Widget", "active", Type.I1, Constant.I1(true))
        fn.ret(widget)
        fn.end()

        val ir = module.build()
        val func = ir.functions.first()
        val instructions = func.blocks.flatMap { it.instructions }

        assertTrue(instructions.any { it::class.simpleName == "NewObject" })
        assertTrue(instructions.any { it::class.simpleName == "PutField" })
    }

    @Test
    fun functionBuilderFullScopeHasBothNativeAndManaged() {
        val module = ModuleBuilder("test", Target.x86_64())
        val fn = module.createFunction(FullScope::class.java, "bridge",
            listOf(Param("size", Type.I32)), Type.OpaquePointer)

        val ins = fn.instructions
        val ptr = ins.alloca(Type.I32)
        ins.store(fn.param(0), ptr)
        val obj = ins.newObject("Result")
        fn.ret(obj)
        fn.end()

        val ir = module.build()
        val func = ir.functions.first()
        val instructions = func.blocks.flatMap { it.instructions }

        assertTrue(instructions.any { it::class.simpleName == "Alloca" })
        assertTrue(instructions.any { it::class.simpleName == "Store" })
        assertTrue(instructions.any { it::class.simpleName == "NewObject" })
    }

    // --- Unsigned comparison extensions ---

    @Test
    fun unsignedComparisonExtensions() {
        val proxy = InstructionBuilder.create(NativeScope::class.java, createSink())
        val paramA = Parameter("a", Type.I32, 0)
        val paramB = Parameter("b", Type.I32, 1)

        assertNotNull(proxy.ult(paramA, paramB))
        assertNotNull(proxy.ule(paramA, paramB))
        assertNotNull(proxy.ugt(paramA, paramB))
        assertNotNull(proxy.uge(paramA, paramB))
    }

    // --- Floating-point comparison extensions ---

    @Test
    fun floatingPointComparisonExtensions() {
        val proxy = InstructionBuilder.create(NativeScope::class.java, createSink())
        val paramA = Parameter("a", Type.F64, 0)
        val paramB = Parameter("b", Type.F64, 1)

        assertNotNull(proxy.feq(paramA, paramB))
        assertNotNull(proxy.fne(paramA, paramB))
        assertNotNull(proxy.flt(paramA, paramB))
        assertNotNull(proxy.fle(paramA, paramB))
        assertNotNull(proxy.fgt(paramA, paramB))
        assertNotNull(proxy.fge(paramA, paramB))
    }

    // --- Ushr extension ---

    @Test
    fun bitwiseExtensionUshr() {
        val proxy = InstructionBuilder.create(NativeScope::class.java, createSink())
        val result = proxy.ushr(Parameter("a", Type.I32, 0), Constant.I32(4))
        assertNotNull(result)
    }
}
