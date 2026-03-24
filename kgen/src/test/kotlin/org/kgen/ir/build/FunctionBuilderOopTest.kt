package org.kgen.ir.build

import org.kgen.ir.*
import org.kgen.ir.instructions.*
import org.kgen.ir.target.Target
import org.kgen.ir.types.*
import org.kgen.ir.verify.IrVerifier
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class FunctionBuilderOopTest {

    @Nested
    inner class ObjectOperations {

        @Test
        fun `newObject emits NewObject instruction`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            val fn = ir.function("create", emptyList(), Type.ClassRef("Widget"))
            val ins = fn.instructions
            val obj = ins.newObject("Widget")
            assertEquals(Type.ClassRef("Widget"), obj.type)
            fn.ret(obj)
            fn.end()

            val mod = ir.build()
            val instrs = mod.functions[0].blocks[0].instructions
            assertTrue(instrs.any { it is NewObject })
        }

        @Test
        fun `newArray emits NewArray instruction`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            val fn = ir.function("makeArray", listOf(Param("n", Type.I32)), Type.Array(Type.I32, 0))
            val ins = fn.instructions
            val arr = ins.newArray(Type.I32, fn.param(0))
            fn.ret(arr)
            fn.end()

            val mod = ir.build()
            val instrs = mod.functions[0].blocks[0].instructions
            assertTrue(instrs.any { it is NewArray })
        }

        @Test
        fun `instanceOf emits InstanceOf instruction`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            val fn = ir.function("check", listOf(Param("obj", Type.ClassRef("Base"))), Type.I1)
            val ins = fn.instructions
            val result = ins.instanceOf(fn.param(0), Type.ClassRef("Derived"))
            assertEquals(Type.I1, result.type)
            fn.ret(result)
            fn.end()

            val mod = ir.build()
            val instrs = mod.functions[0].blocks[0].instructions
            assertTrue(instrs.any { it is InstanceOf })
        }

        @Test
        fun `checkCast emits CheckCast instruction`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            val fn = ir.function("cast", listOf(Param("obj", Type.ClassRef("Base"))), Type.ClassRef("Derived"))
            val ins = fn.instructions
            val casted = ins.checkCast(fn.param(0), Type.ClassRef("Derived"))
            assertEquals(Type.ClassRef("Derived"), casted.type)
            fn.ret(casted)
            fn.end()

            val mod = ir.build()
            val instrs = mod.functions[0].blocks[0].instructions
            assertTrue(instrs.any { it is CheckCast })
        }
    }

    @Nested
    inner class ExplicitFieldOperations {

        @Test
        fun `explicit getField and putField`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            val fn = ir.function("field_ops",
                listOf(Param("obj", Type.ClassRef("Point"))), Type.I32)
            val ins = fn.instructions
            val x = ins.getField(fn.param(0), "Point", "x", Type.I32)
            ins.putField(fn.param(0), "Point", "x", Type.I32, Type.i32(42))
            fn.ret(x)
            fn.end()

            val mod = ir.build()
            val instrs = mod.functions[0].blocks[0].instructions
            assertTrue(instrs.any { it is GetField })
            assertTrue(instrs.any { it is PutField })
        }

        @Test
        fun `getStatic and putStatic`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            val fn = ir.function("static_ops", emptyList(), Type.I32)
            val ins = fn.instructions
            val count = ins.getStatic("Counter", "count", Type.I32)
            ins.putStatic("Counter", "count", Type.I32, Type.i32(10))
            fn.ret(count)
            fn.end()

            val mod = ir.build()
            val instrs = mod.functions[0].blocks[0].instructions
            assertTrue(instrs.any { it is GetStatic })
            assertTrue(instrs.any { it is PutStatic })
        }
    }

    @Nested
    inner class DispatchOperations {

        @Test
        fun `explicit virtualCall`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            val fn = ir.function("dispatch",
                listOf(Param("obj", Type.ClassRef("Animal"))), Type.I32)
            val ins = fn.instructions
            val methodType = Type.Function(emptyList(), Type.I32)
            val result = ins.virtualCall(fn.param(0), "Animal", "speak", methodType, emptyList())
            fn.ret(result!!)
            fn.end()

            val mod = ir.build()
            val instrs = mod.functions[0].blocks[0].instructions
            assertTrue(instrs.any { it is VirtualCall })
        }

        @Test
        fun `explicit interfaceCall`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            val fn = ir.function("iface_call",
                listOf(Param("obj", Type.ClassRef("Runnable"))), Type.Void)
            val ins = fn.instructions
            val methodType = Type.Function(emptyList(), Type.Void)
            ins.interfaceCall(fn.param(0), "Runnable", "run", methodType, emptyList())
            fn.retVoid()
            fn.end()

            val mod = ir.build()
            val instrs = mod.functions[0].blocks[0].instructions
            assertTrue(instrs.any { it is InterfaceCall })
        }

        @Test
        fun `explicit staticCall`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            val fn = ir.function("static_dispatch", emptyList(), Type.I32)
            val ins = fn.instructions
            val methodType = Type.Function(emptyList(), Type.I32)
            val result = ins.staticCall("Math", "random", methodType, emptyList())
            fn.ret(result!!)
            fn.end()

            val mod = ir.build()
            val instrs = mod.functions[0].blocks[0].instructions
            assertTrue(instrs.any { it is StaticCall })
        }

        @Test
        fun `explicit constructorCall`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            val fn = ir.function("construct",
                listOf(Param("obj", Type.ClassRef("Widget"))), Type.Void)
            val ins = fn.instructions
            val ctorType = Type.Function(listOf(Type.I32), Type.Void)
            ins.constructorCall(fn.param(0), "Widget", ctorType, listOf(Type.i32(5)))
            fn.retVoid()
            fn.end()

            val mod = ir.build()
            val instrs = mod.functions[0].blocks[0].instructions
            assertTrue(instrs.any { it is ConstructorCall })
        }
    }

    @Nested
    inner class ArrayOperations {

        @Test
        fun `arrayGet emits ArrayGet`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            val fn = ir.function("arr_get",
                listOf(Param("arr", Type.Array(Type.I32, 0)), Param("idx", Type.I32)), Type.I32)
            val ins = fn.instructions
            val elem = ins.arrayGet(fn.param(0), fn.param(1), Type.I32)
            fn.ret(elem)
            fn.end()

            val mod = ir.build()
            val instrs = mod.functions[0].blocks[0].instructions
            assertTrue(instrs.any { it is ArrayGet })
        }

        @Test
        fun `arraySet emits ArraySet`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            val fn = ir.function("arr_set",
                listOf(Param("arr", Type.Array(Type.I32, 0)), Param("idx", Type.I32)), Type.Void)
            val ins = fn.instructions
            ins.arraySet(fn.param(0), fn.param(1), Type.i32(99), Type.I32)
            fn.retVoid()
            fn.end()

            val mod = ir.build()
            val instrs = mod.functions[0].blocks[0].instructions
            assertTrue(instrs.any { it is ArraySet })
        }

        @Test
        fun `arrayLength emits ArrayLength`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            val fn = ir.function("arr_len",
                listOf(Param("arr", Type.Array(Type.I32, 0))), Type.I32)
            val ins = fn.instructions
            val len = ins.arrayLength(fn.param(0))
            assertEquals(Type.I32, len.type)
            fn.ret(len)
            fn.end()

            val mod = ir.build()
            val instrs = mod.functions[0].blocks[0].instructions
            assertTrue(instrs.any { it is ArrayLength })
        }
    }

    @Nested
    inner class ExceptionOperations {

        @Test
        fun `raise emits Throw and terminates`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            val fn = ir.function("throw_it",
                listOf(Param("ex", Type.ClassRef("Exception"))), Type.Void)
            val ins = fn.instructions
            ins.throwException(fn.param(0))
            fn.end()

            val mod = ir.build()
            val instrs = mod.functions[0].blocks[0].instructions
            assertTrue(instrs.any { it is Throw })
        }
    }

    @Nested
    inner class BoxingOperations {

        @Test
        fun `box emits Box instruction`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            val fn = ir.function("box_int",
                listOf(Param("x", Type.I32)), Type.ClassRef("Integer"))
            val ins = fn.instructions
            val boxed = ins.box(fn.param(0), Type.ClassRef("Integer"))
            fn.ret(boxed)
            fn.end()

            val mod = ir.build()
            val instrs = mod.functions[0].blocks[0].instructions
            assertTrue(instrs.any { it is Box })
        }

        @Test
        fun `unbox emits Unbox instruction`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            val fn = ir.function("unbox_int",
                listOf(Param("obj", Type.ClassRef("Integer"))), Type.I32)
            val ins = fn.instructions
            val unboxed = ins.unbox(fn.param(0), Type.I32)
            fn.ret(unboxed)
            fn.end()

            val mod = ir.build()
            val instrs = mod.functions[0].blocks[0].instructions
            assertTrue(instrs.any { it is Unbox })
        }
    }

    @Nested
    inner class SmartFieldResolution {

        @Test
        fun `explicit getField with class and field info`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            ir.addClass(ClassDefinition("Point", fields = listOf(
                FieldDefinition("x", Type.I32),
                FieldDefinition("y", Type.I32),
            )))

            val fn = ir.function("get_x",
                listOf(Param("p", Type.ClassRef("Point"))), Type.I32)
            val ins = fn.instructions
            val x = ins.getField(fn.param(0), "Point", "x", Type.I32)
            fn.ret(x)
            fn.end()

            val mod = ir.build()
            val getField = mod.functions[0].blocks[0].instructions[0] as GetField
            assertEquals("Point", getField.className)
            assertEquals("x", getField.fieldName)
            assertEquals(Type.I32, getField.fieldType)
        }

        @Test
        fun `explicit putField with class and field info`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            ir.addClass(ClassDefinition("Point", fields = listOf(
                FieldDefinition("x", Type.I32),
            )))

            val fn = ir.function("set_x",
                listOf(Param("p", Type.ClassRef("Point"))), Type.Void)
            val ins = fn.instructions
            ins.putField(fn.param(0), "Point", "x", Type.I32, Type.i32(42))
            fn.retVoid()
            fn.end()

            val mod = ir.build()
            val putField = mod.functions[0].blocks[0].instructions[0] as PutField
            assertEquals("Point", putField.className)
            assertEquals("x", putField.fieldName)
        }

        @Test
        fun `explicit getField for static fields`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            ir.addClass(ClassDefinition("Config", staticFields = listOf(
                FieldDefinition("version", Type.I32),
            )))

            val fn = ir.function("get_version",
                listOf(Param("c", Type.ClassRef("Config"))), Type.I32)
            val ins = fn.instructions
            val v = ins.getField(fn.param(0), "Config", "version", Type.I32)
            fn.ret(v)
            fn.end()

            val mod = ir.build()
            val getField = mod.functions[0].blocks[0].instructions[0] as GetField
            assertEquals("version", getField.fieldName)
        }

        @Test
        fun `explicit virtualCall with method type`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            ir.addClass(ClassDefinition("Calculator", methods = listOf(
                MethodDefinition("compute", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32),
            )))

            val fn = ir.function("use_calc",
                listOf(Param("c", Type.ClassRef("Calculator"))), Type.I32)
            val ins = fn.instructions
            val methodType = Type.Function(listOf(Type.I32, Type.I32), Type.I32)
            val result = ins.virtualCall(fn.param(0), "Calculator", "compute", methodType, listOf(Type.i32(3), Type.i32(4)))
            fn.ret(result!!)
            fn.end()

            val mod = ir.build()
            val vcall = mod.functions[0].blocks[0].instructions[0] as VirtualCall
            assertEquals("Calculator", vcall.className)
            assertEquals("compute", vcall.methodName)
            assertEquals(2, vcall.args.size)
        }

        @Test
        fun `virtualCall on unregistered class still emits instruction`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            val fn = ir.function("dispatch_ghost",
                listOf(Param("obj", Type.ClassRef("Ghost"))), Type.Void)
            val ins = fn.instructions
            val methodType = Type.Function(emptyList(), Type.Void)
            ins.virtualCall(fn.param(0), "Ghost", "method", methodType, emptyList())
            fn.retVoid()
            fn.end()

            val mod = ir.build()
            val instrs = mod.functions[0].blocks[0].instructions
            assertTrue(instrs.any { it is VirtualCall })
        }

        @Test
        fun `virtualCall with explicit types does not require class registration`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            ir.addClass(ClassDefinition("Empty"))
            val fn = ir.function("dispatch_missing",
                listOf(Param("obj", Type.ClassRef("Empty"))), Type.Void)
            val ins = fn.instructions
            val methodType = Type.Function(emptyList(), Type.Void)
            ins.virtualCall(fn.param(0), "Empty", "noSuchMethod", methodType, emptyList())
            fn.retVoid()
            fn.end()

            val mod = ir.build()
            val instrs = mod.functions[0].blocks[0].instructions
            assertTrue(instrs.any { it is VirtualCall })
        }
    }

    @Nested
    inner class FunctionBuilderCalls {

        @Test
        fun `call with FunctionRef`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            ir.declareFunction("helper", listOf(Param("x", Type.I32)), Type.I32)

            val fn = ir.function("caller", emptyList(), Type.I32)
            val ins = fn.instructions
            val ref = fn.functionRef("helper", listOf(Type.I32), Type.I32)
            val result = ins.call(ref, listOf(Type.i32(5)))
            fn.ret(result!!)
            fn.end()

            val mod = ir.build()
            val instrs = mod.functions[1].blocks[0].instructions
            assertTrue(instrs.any { it is Call })
        }

        @Test
        fun `call by name via GlobalRef`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            ir.declareFunction("helper", emptyList(), Type.I32)

            val fn = ir.function("caller", emptyList(), Type.I32)
            val ins = fn.instructions
            val helperRef = GlobalRef("helper", Type.Function(emptyList(), Type.I32))
            val result = ins.call(helperRef, emptyList(), Type.I32)
            fn.ret(result!!)
            fn.end()

            val mod = ir.build()
            val instrs = mod.functions[1].blocks[0].instructions
            assertTrue(instrs.any { it is Call })
        }

        @Test
        fun `call void function returns null`() {
            val ir = ModuleBuilder("test", Target.x86_64())
            ir.declareFunction("sideEffect", emptyList(), Type.Void)

            val fn = ir.function("caller", emptyList(), Type.Void)
            val ins = fn.instructions
            val sideEffectRef = GlobalRef("sideEffect", Type.Function(emptyList(), Type.Void))
            val result = ins.call(sideEffectRef, emptyList(), Type.Void)
            assertNull(result)
            fn.retVoid()
            fn.end()
        }
    }
}
