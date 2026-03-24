package org.kgen.pipeline

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.kgen.ir.*
import org.kgen.ir.instructions.*
import org.kgen.ir.build.ModuleBuilder
import org.kgen.ir.target.Target
import org.kgen.ir.types.*

class VTableLoweringTest {

    @Test
    fun virtualCallLoweredToIndirectCall() {
        val builder = ModuleBuilder("test", Target.x86_64())

        builder.addClass(ClassDefinition(
            name = "Animal",
            methods = listOf(MethodDefinition("speak", listOf(Param("this", Type.OpaquePointer)), Type.I32)),
        ))

        val params = builder.createFunction("callSpeak", listOf(Param("obj", Type.OpaquePointer)), Type.I32)
        builder.appendBlock("entry")
        val methodType = Type.Function(listOf(Type.OpaquePointer), Type.I32)
        builder.virtualCall(params[0], "Animal", "speak", methodType, emptyList())
        builder.ret(Constant.I32(0))
        builder.finalizeFunction()

        val module = builder.build()
        val lowered = VTableLowering().run(module)

        val fn = lowered.functions.find { it.name == "callSpeak" }!!
        val instructions = fn.blocks.flatMap { it.instructions }

        val loads = instructions.filterIsInstance<Load>()
        val geps = instructions.filterIsInstance<GetElementPtr>()
        val calls = instructions.filterIsInstance<Call>()

        assertTrue(loads.size >= 2, "Should have at least 2 loads (vtable + fptr), got: ${loads.size}")
        assertTrue(geps.isNotEmpty(), "Should have GEP for vtable slot indexing")
        assertTrue(calls.isNotEmpty(), "Should have a call instruction")

        val indirectCall = calls.first()
        assertTrue(indirectCall.function is InstructionRef,
            "Call should be indirect (InstructionRef), got: ${indirectCall.function::class.simpleName}")
    }

    @Test
    fun interfaceCallLoweredToIndirectCall() {
        val builder = ModuleBuilder("test", Target.x86_64())

        builder.addInterface(InterfaceDefinition(
            name = "Comparable",
            methods = listOf(MethodDefinition("compareTo", listOf(
                Param("this", Type.OpaquePointer), Param("other", Type.OpaquePointer)
            ), Type.I32)),
        ))

        val params = builder.createFunction("compare", listOf(
            Param("a", Type.OpaquePointer),
            Param("b", Type.OpaquePointer),
        ), Type.I32)
        builder.appendBlock("entry")
        val methodType = Type.Function(listOf(Type.OpaquePointer, Type.OpaquePointer), Type.I32)
        builder.interfaceCall(params[0], "Comparable", "compareTo", methodType, listOf(params[1]))
        builder.ret(Constant.I32(0))
        builder.finalizeFunction()

        val module = builder.build()
        val lowered = VTableLowering().run(module)

        val fn = lowered.functions.find { it.name == "compare" }!!
        val calls = fn.blocks.flatMap { it.instructions }.filterIsInstance<Call>()
        assertTrue(calls.isNotEmpty())
        assertTrue(calls.first().function is InstructionRef,
            "Interface call should be lowered to indirect call")
    }

    @Test
    fun noClassDefinitionFallsBackToDirectCall() {
        val builder = ModuleBuilder("test", Target.x86_64())

        val params = builder.createFunction("callUnknown", listOf(Param("obj", Type.OpaquePointer)), Type.I32)
        builder.appendBlock("entry")
        val methodType = Type.Function(listOf(Type.OpaquePointer), Type.I32)
        builder.virtualCall(params[0], "Unknown", "doStuff", methodType, emptyList())
        builder.ret(Constant.I32(0))
        builder.finalizeFunction()

        val module = builder.build()
        val lowered = VTableLowering().run(module)

        val fn = lowered.functions.find { it.name == "callUnknown" }!!
        val calls = fn.blocks.flatMap { it.instructions }.filterIsInstance<Call>()
        assertTrue(calls.isNotEmpty())

        val call = calls.first()
        assertTrue(call.function is FunctionRef, "Fallback should use FunctionRef")
        assertEquals("Unknown_doStuff", (call.function as FunctionRef).name)
    }

    @Test
    fun vtableSlotsAreAssignedSequentially() {
        val builder = ModuleBuilder("test", Target.x86_64())

        builder.addClass(ClassDefinition(
            name = "Base",
            methods = listOf(
                MethodDefinition("methodA", listOf(Param("this", Type.OpaquePointer)), Type.Void),
                MethodDefinition("methodB", listOf(Param("this", Type.OpaquePointer)), Type.Void),
            ),
        ))

        val params = builder.createFunction("test", listOf(Param("obj", Type.OpaquePointer)), Type.Void)
        builder.appendBlock("entry")
        val voidMethod = Type.Function(listOf(Type.OpaquePointer), Type.Void)
        builder.virtualCall(params[0], "Base", "methodA", voidMethod, emptyList())
        builder.virtualCall(params[0], "Base", "methodB", voidMethod, emptyList())
        builder.ret()
        builder.finalizeFunction()

        val module = builder.build()
        val lowered = VTableLowering().run(module)

        val fn = lowered.functions.find { it.name == "test" }!!
        val geps = fn.blocks.flatMap { it.instructions }.filterIsInstance<GetElementPtr>()

        assertEquals(2, geps.size, "Should have 2 GEP instructions for 2 vtable slots")

        val offsets = geps.map { (it.indices.first() as Constant.I64).value }
        assertTrue(0L in offsets, "Should have slot offset 0 (methodA)")
        assertTrue(8L in offsets, "Should have slot offset 8 (methodB)")
    }

    @Test
    fun inheritedSlotsPreserved() {
        val builder = ModuleBuilder("test", Target.x86_64())

        builder.addClass(ClassDefinition(
            name = "Parent",
            methods = listOf(
                MethodDefinition("greet", listOf(Param("this", Type.OpaquePointer)), Type.Void),
            ),
        ))
        builder.addClass(ClassDefinition(
            name = "Child",
            superClass = "Parent",
            methods = listOf(
                MethodDefinition("greet", listOf(Param("this", Type.OpaquePointer)), Type.Void),
                MethodDefinition("extra", listOf(Param("this", Type.OpaquePointer)), Type.Void),
            ),
        ))

        val params = builder.createFunction("test", listOf(Param("obj", Type.OpaquePointer)), Type.Void)
        builder.appendBlock("entry")
        val voidMethod = Type.Function(listOf(Type.OpaquePointer), Type.Void)
        builder.virtualCall(params[0], "Child", "greet", voidMethod, emptyList())
        builder.virtualCall(params[0], "Child", "extra", voidMethod, emptyList())
        builder.ret()
        builder.finalizeFunction()

        val module = builder.build()
        val lowered = VTableLowering().run(module)

        val fn = lowered.functions.find { it.name == "test" }!!
        val geps = fn.blocks.flatMap { it.instructions }.filterIsInstance<GetElementPtr>()
        assertEquals(2, geps.size)

        val offsets = geps.map { (it.indices.first() as Constant.I64).value }
        assertEquals(0L, offsets[0], "Child.greet should be at slot 0 (inherited)")
        assertEquals(8L, offsets[1], "Child.extra should be at slot 1")
    }

    @Test
    fun noVirtualCallsNoChange() {
        val builder = ModuleBuilder("test", Target.x86_64())
        builder.createFunction("noop", emptyList(), Type.Void)
        builder.appendBlock("entry")
        builder.ret()
        builder.finalizeFunction()

        val module = builder.build()
        val lowered = VTableLowering().run(module)

        assertSame(module, lowered, "Module with no virtual calls should be unchanged")
    }

    @Test
    fun multipleVirtualCallsInSameFunction() {
        val builder = ModuleBuilder("test", Target.x86_64())

        builder.addClass(ClassDefinition(
            name = "Shape",
            methods = listOf(
                MethodDefinition("area", listOf(Param("this", Type.OpaquePointer)), Type.I32),
                MethodDefinition("perimeter", listOf(Param("this", Type.OpaquePointer)), Type.I32),
            ),
        ))

        val params = builder.createFunction("measure", listOf(Param("obj", Type.OpaquePointer)), Type.I32)
        builder.appendBlock("entry")
        val intMethod = Type.Function(listOf(Type.OpaquePointer), Type.I32)
        val area = builder.virtualCall(params[0], "Shape", "area", intMethod, emptyList())
        val perim = builder.virtualCall(params[0], "Shape", "perimeter", intMethod, emptyList())
        val sum = builder.add(area ?: Constant.I32(0), perim ?: Constant.I32(0))
        builder.ret(sum)
        builder.finalizeFunction()

        val module = builder.build()
        val lowered = VTableLowering().run(module)

        val fn = lowered.functions.find { it.name == "measure" }!!
        val calls = fn.blocks.flatMap { it.instructions }.filterIsInstance<Call>()
        assertEquals(2, calls.size, "Should have 2 indirect calls")
        assertTrue(calls.all { it.function is InstructionRef },
            "Both calls should be indirect")
    }

    @Test
    fun virtualCallWithReturnValuePreserved() {
        val builder = ModuleBuilder("test", Target.x86_64())

        builder.addClass(ClassDefinition(
            name = "Getter",
            methods = listOf(
                MethodDefinition("getValue", listOf(Param("this", Type.OpaquePointer)), Type.I32),
            ),
        ))

        val params = builder.createFunction("test", listOf(Param("obj", Type.OpaquePointer)), Type.I32)
        builder.appendBlock("entry")
        val intMethod = Type.Function(listOf(Type.OpaquePointer), Type.I32)
        val result = builder.virtualCall(params[0], "Getter", "getValue", intMethod, emptyList())
        builder.ret(result ?: Constant.I32(0))
        builder.finalizeFunction()

        val module = builder.build()
        val lowered = VTableLowering().run(module)

        val fn = lowered.functions.find { it.name == "test" }!!
        val calls = fn.blocks.flatMap { it.instructions }.filterIsInstance<Call>()
        assertTrue(calls.isNotEmpty())
        // The ret should use the call result
        val ret = fn.blocks.flatMap { it.instructions }.filterIsInstance<Ret>().first()
        assertNotNull(ret.value, "Should return the result of the virtual call")
    }

    @Test
    fun diamondInheritanceSlotAssignment() {
        val builder = ModuleBuilder("test", Target.x86_64())

        builder.addClass(ClassDefinition(
            name = "A",
            methods = listOf(
                MethodDefinition("foo", listOf(Param("this", Type.OpaquePointer)), Type.Void),
            ),
        ))
        builder.addClass(ClassDefinition(
            name = "B",
            superClass = "A",
            methods = listOf(
                MethodDefinition("foo", listOf(Param("this", Type.OpaquePointer)), Type.Void),
                MethodDefinition("bar", listOf(Param("this", Type.OpaquePointer)), Type.Void),
            ),
        ))
        builder.addClass(ClassDefinition(
            name = "C",
            superClass = "A",
            methods = listOf(
                MethodDefinition("foo", listOf(Param("this", Type.OpaquePointer)), Type.Void),
                MethodDefinition("baz", listOf(Param("this", Type.OpaquePointer)), Type.Void),
            ),
        ))

        val params = builder.createFunction("test", listOf(Param("obj", Type.OpaquePointer)), Type.Void)
        builder.appendBlock("entry")
        val voidMethod = Type.Function(listOf(Type.OpaquePointer), Type.Void)
        builder.virtualCall(params[0], "B", "foo", voidMethod, emptyList())
        builder.virtualCall(params[0], "C", "baz", voidMethod, emptyList())
        builder.ret()
        builder.finalizeFunction()

        val module = builder.build()
        val lowered = VTableLowering().run(module)

        val fn = lowered.functions.find { it.name == "test" }!!
        val calls = fn.blocks.flatMap { it.instructions }.filterIsInstance<Call>()
        assertEquals(2, calls.size, "Should have 2 virtual calls lowered")
    }

    @Test
    fun interfaceCallPreservesAllArguments() {
        val builder = ModuleBuilder("test", Target.x86_64())

        builder.addInterface(InterfaceDefinition(
            name = "BiFunction",
            methods = listOf(MethodDefinition("apply", listOf(
                Param("this", Type.OpaquePointer),
                Param("a", Type.I32),
                Param("b", Type.I32),
                Param("c", Type.I32),
            ), Type.I32)),
        ))

        val params = builder.createFunction("test", listOf(
            Param("obj", Type.OpaquePointer),
            Param("x", Type.I32),
            Param("y", Type.I32),
            Param("z", Type.I32),
        ), Type.I32)
        builder.appendBlock("entry")
        val fnType = Type.Function(listOf(Type.OpaquePointer, Type.I32, Type.I32, Type.I32), Type.I32)
        val result = builder.interfaceCall(
            params[0], "BiFunction", "apply", fnType,
            listOf(params[1], params[2], params[3]),
        )
        builder.ret(result ?: Constant.I32(0))
        builder.finalizeFunction()

        val module = builder.build()
        val lowered = VTableLowering().run(module)

        val fn = lowered.functions.find { it.name == "test" }!!
        val calls = fn.blocks.flatMap { it.instructions }.filterIsInstance<Call>()
        assertTrue(calls.isNotEmpty())
        assertEquals(4, calls.first().args.size, "Should preserve all 4 args (this + x + y + z)")
    }

    @Test
    fun emptyVtableForClassWithNoMethods() {
        val builder = ModuleBuilder("test", Target.x86_64())

        builder.addClass(ClassDefinition(
            name = "Empty",
            methods = emptyList(),
        ))

        builder.createFunction("test", emptyList(), Type.Void)
        builder.appendBlock("entry")
        builder.ret()
        builder.finalizeFunction()

        val module = builder.build()
        val lowered = VTableLowering().run(module)

        // No virtual calls means module should be unchanged
        assertSame(module, lowered)
    }

    @Test
    fun virtualCallPreservesArgs() {
        val builder = ModuleBuilder("test", Target.x86_64())

        builder.addClass(ClassDefinition(
            name = "Calculator",
            methods = listOf(
                MethodDefinition("add", listOf(
                    Param("this", Type.OpaquePointer),
                    Param("a", Type.I32),
                    Param("b", Type.I32),
                ), Type.I32),
            ),
        ))

        val params = builder.createFunction("test", listOf(
            Param("obj", Type.OpaquePointer),
            Param("a", Type.I32),
            Param("b", Type.I32),
        ), Type.I32)
        builder.appendBlock("entry")
        val addType = Type.Function(listOf(Type.OpaquePointer, Type.I32, Type.I32), Type.I32)
        val result = builder.virtualCall(params[0], "Calculator", "add", addType, listOf(params[1], params[2]))
        builder.ret(result ?: Constant.I32(0))
        builder.finalizeFunction()

        val module = builder.build()
        val lowered = VTableLowering().run(module)

        val fn = lowered.functions.find { it.name == "test" }!!
        val calls = fn.blocks.flatMap { it.instructions }.filterIsInstance<Call>()
        assertTrue(calls.isNotEmpty())

        val call = calls.first()
        assertEquals(3, call.args.size, "Indirect call should preserve all args (obj + a + b)")
    }
}
