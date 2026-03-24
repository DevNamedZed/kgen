package org.kgen.pipeline

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*
import org.kgen.ir.instructions.*
import org.kgen.ir.build.ModuleBuilder
import org.kgen.ir.target.Target
import org.kgen.ir.types.*

class DevirtualizationTest {

    @Test
    fun devirtualizeFinalClass() {
        val builder = ModuleBuilder("test", Target.x86_64())

        builder.addClass(ClassDefinition(
            name = "FinalPoint",
            isFinal = true,
            methods = listOf(MethodDefinition("toString", listOf(Param("this", Type.ClassRef("FinalPoint"))), Type.I32)),
        ))

        builder.createFunction("FinalPoint.toString",
            listOf(Param("this", Type.ClassRef("FinalPoint"))), Type.I32)
        builder.appendBlock("entry")
        builder.ret(Constant.I32(42))
        builder.finalizeFunction()

        val callerParams = builder.createFunction("caller",
            listOf(Param("obj", Type.ClassRef("FinalPoint"))), Type.I32)
        builder.appendBlock("entry")
        val methodType = Type.Function(listOf(Type.ClassRef("FinalPoint")), Type.I32)
        val result = builder.virtualCall(callerParams[0], "FinalPoint", "toString", methodType, emptyList())
        builder.ret(result!!)
        builder.finalizeFunction()

        val module = builder.build()
        val optimized = Devirtualization().run(module)

        val callerFn = optimized.functions.first { it.name == "caller" }
        val calls = callerFn.blocks.flatMap { it.instructions.filterIsInstance<Call>() }
        val vcalls = callerFn.blocks.flatMap { it.instructions.filterIsInstance<VirtualCall>() }

        assertTrue(vcalls.isEmpty(), "VirtualCall should be devirtualized for final class")
        assertTrue(calls.isNotEmpty(), "Should have direct Call instead")
        assertEquals("FinalPoint.toString", (calls[0].function as FunctionRef).name)
    }

    @Test
    fun devirtualizeSingleImplementor() {
        val builder = ModuleBuilder("test", Target.x86_64())

        builder.addClass(ClassDefinition(name = "Base", isAbstract = true))
        builder.addClass(ClassDefinition(name = "OnlySub", superClass = "Base"))

        builder.createFunction("OnlySub.method",
            listOf(Param("this", Type.ClassRef("OnlySub"))), Type.I32)
        builder.appendBlock("entry")
        builder.ret(Constant.I32(1))
        builder.finalizeFunction()

        val callerParams = builder.createFunction("caller",
            listOf(Param("obj", Type.ClassRef("Base"))), Type.I32)
        builder.appendBlock("entry")
        val methodType = Type.Function(listOf(Type.ClassRef("Base")), Type.I32)
        val result = builder.virtualCall(callerParams[0], "Base", "method", methodType, emptyList())
        builder.ret(result!!)
        builder.finalizeFunction()

        val module = builder.build()
        val optimized = Devirtualization().run(module)

        val callerFn = optimized.functions.first { it.name == "caller" }
        val calls = callerFn.blocks.flatMap { it.instructions.filterIsInstance<Call>() }

        assertTrue(calls.isNotEmpty(), "Should devirtualize to direct call")
        assertEquals("OnlySub.method", (calls[0].function as FunctionRef).name)
    }

    @Test
    fun noDevirtualizationWithMultipleImplementors() {
        val builder = ModuleBuilder("test", Target.x86_64())

        builder.addClass(ClassDefinition(name = "Shape", isAbstract = true))
        builder.addClass(ClassDefinition(name = "Circle", superClass = "Shape"))
        builder.addClass(ClassDefinition(name = "Square", superClass = "Shape"))

        for (cls in listOf("Circle", "Square")) {
            builder.createFunction("$cls.area",
                listOf(Param("this", Type.ClassRef(cls))), Type.I32)
            builder.appendBlock("entry")
            builder.ret(Constant.I32(1))
            builder.finalizeFunction()
        }

        val callerParams = builder.createFunction("caller",
            listOf(Param("obj", Type.ClassRef("Shape"))), Type.I32)
        builder.appendBlock("entry")
        val methodType = Type.Function(listOf(Type.ClassRef("Shape")), Type.I32)
        val result = builder.virtualCall(callerParams[0], "Shape", "area", methodType, emptyList())
        builder.ret(result!!)
        builder.finalizeFunction()

        val module = builder.build()
        val optimized = Devirtualization().run(module)

        val callerFn = optimized.functions.first { it.name == "caller" }
        val vcalls = callerFn.blocks.flatMap { it.instructions.filterIsInstance<VirtualCall>() }

        assertFalse(vcalls.isEmpty(), "VirtualCall should remain with multiple implementors")
    }

    @Test
    fun devirtualizeInterfaceWithSingleImplementor() {
        val builder = ModuleBuilder("test", Target.x86_64())

        builder.addInterface(InterfaceDefinition(name = "Printable"))
        builder.addClass(ClassDefinition(name = "Doc", interfaces = listOf("Printable")))

        builder.createFunction("Doc.print",
            listOf(Param("this", Type.ClassRef("Doc"))), Type.Void)
        builder.appendBlock("entry")
        builder.ret()
        builder.finalizeFunction()

        val callerParams = builder.createFunction("caller",
            listOf(Param("obj", Type.InterfaceRef("Printable"))), Type.Void)
        builder.appendBlock("entry")
        val methodType = Type.Function(listOf(Type.InterfaceRef("Printable")), Type.Void)
        builder.interfaceCall(callerParams[0], "Printable", "print", methodType, emptyList())
        builder.ret()
        builder.finalizeFunction()

        val module = builder.build()
        val optimized = Devirtualization().run(module)

        val callerFn = optimized.functions.first { it.name == "caller" }
        val calls = callerFn.blocks.flatMap { it.instructions.filterIsInstance<Call>() }
        val icalls = callerFn.blocks.flatMap { it.instructions.filterIsInstance<InterfaceCall>() }

        assertTrue(icalls.isEmpty(), "InterfaceCall should be devirtualized")
        assertTrue(calls.isNotEmpty())
    }

    @Test
    fun devirtualizeKnownConcreteType() {
        val builder = ModuleBuilder("test", Target.x86_64())

        builder.addClass(ClassDefinition(name = "MyObj", superClass = "Base"))
        builder.addClass(ClassDefinition(name = "Base"))

        builder.createFunction("MyObj.method",
            listOf(Param("this", Type.ClassRef("MyObj"))), Type.I32)
        builder.appendBlock("entry")
        builder.ret(Constant.I32(99))
        builder.finalizeFunction()

        builder.createFunction("caller", emptyList(), Type.I32)
        builder.appendBlock("entry")
        val obj = builder.gcAlloc(Type.ClassRef("MyObj"))
        val methodType = Type.Function(listOf(Type.ClassRef("Base")), Type.I32)
        val result = builder.virtualCall(obj, "Base", "method", methodType, emptyList())
        builder.ret(result!!)
        builder.finalizeFunction()

        val module = builder.build()
        val optimized = Devirtualization().run(module)

        val callerFn = optimized.functions.first { it.name == "caller" }
        val calls = callerFn.blocks.flatMap { it.instructions.filterIsInstance<Call>() }

        assertTrue(calls.isNotEmpty(), "Should devirtualize when concrete type is known from GCAlloc")
        assertEquals("MyObj.method", (calls[0].function as FunctionRef).name)
    }

    @Test
    fun noChangeWhenNoVirtualCalls() {
        val builder = ModuleBuilder("test", Target.x86_64())
        val params = builder.createFunction("add", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        builder.appendBlock("entry")
        builder.ret(builder.add(params[0], params[1]))
        builder.finalizeFunction()

        val module = builder.build()
        val optimized = Devirtualization().run(module)
        assertSame(module, optimized)
    }

    @Test
    fun classHierarchySingleImplementor() {
        val classes = listOf(
            ClassDefinition(name = "Animal", isAbstract = true),
            ClassDefinition(name = "Dog", superClass = "Animal"),
        )
        val functions = listOf(
            IrFunction("Dog.speak", listOf(Parameter("this", Type.ClassRef("Dog"), 0)), Type.Void,
                blocks = listOf(BasicBlock("entry", listOf(Ret(null)))))
        )
        val hierarchy = Devirtualization.ClassHierarchy(classes, emptyList(), functions)
        assertEquals("Dog", hierarchy.singleImplementor("Animal", "speak"))
    }

    @Test
    fun classHierarchyMultipleImplementors() {
        val classes = listOf(
            ClassDefinition(name = "Animal", isAbstract = true),
            ClassDefinition(name = "Dog", superClass = "Animal"),
            ClassDefinition(name = "Cat", superClass = "Animal"),
        )
        val functions = listOf(
            IrFunction("Dog.speak", listOf(Parameter("this", Type.ClassRef("Dog"), 0)), Type.Void,
                blocks = listOf(BasicBlock("entry", listOf(Ret(null))))),
            IrFunction("Cat.speak", listOf(Parameter("this", Type.ClassRef("Cat"), 0)), Type.Void,
                blocks = listOf(BasicBlock("entry", listOf(Ret(null))))),
        )
        val hierarchy = Devirtualization.ClassHierarchy(classes, emptyList(), functions)
        assertNull(hierarchy.singleImplementor("Animal", "speak"))
    }
}
