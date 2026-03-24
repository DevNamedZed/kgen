package org.kgen.ir.build

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.kgen.ir.*
import org.kgen.ir.build.scope.NativeScope
import org.kgen.ir.build.scope.ManagedScope
import org.kgen.ir.target.Target
import org.kgen.ir.types.ClassDefinition
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ClassBuilderTest {

    @Test
    fun createClassWithFieldsAndBuild() {
        val ir = ModuleBuilder("test", Target.x86_64())

        val cls = ir.createClass(NativeScope::class.java, "Point")
        cls.field("x", Type.F64)
        cls.field("y", Type.F64)

        val classDef = cls.build()

        assertEquals("Point", classDef.name)
        assertEquals(2, classDef.fields.size)
        assertEquals("x", classDef.fields[0].name)
        assertEquals(Type.F64, classDef.fields[0].type)
        assertEquals("y", classDef.fields[1].name)
        assertNotNull(ir.findClass("Point"))
    }

    @Test
    fun createClassWithMetadata() {
        val ir = ModuleBuilder("test", Target.x86_64())

        val cls = ir.createClass(NativeScope::class.java, "Animal")
            .extends("Creature")
            .implements("Movable")
            .implements("Drawable")
            .isAbstract()
            .visibility(ClassVisibility.PUBLIC)

        val classDef = cls.build()

        assertEquals("Creature", classDef.superClass)
        assertEquals(listOf("Movable", "Drawable"), classDef.interfaces)
        assertTrue(classDef.isAbstract)
        assertEquals(ClassVisibility.PUBLIC, classDef.visibility)
    }

    @Test
    fun createClassWithStaticFields() {
        val ir = ModuleBuilder("test", Target.x86_64())

        val cls = ir.createClass(NativeScope::class.java, "Counter")
        cls.field("value", Type.I32)
        cls.staticField("instanceCount", Type.I32, initializer = Constant.I32(0))

        val classDef = cls.build()

        assertEquals(1, classDef.fields.size)
        assertEquals(1, classDef.staticFields.size)
        assertEquals("instanceCount", classDef.staticFields[0].name)
        assertEquals(Constant.I32(0), classDef.staticFields[0].initializer)
    }

    @Test
    fun createFunctionWithinClass() {
        val ir = ModuleBuilder("test", Target.x86_64())

        val cls = ir.createClass(NativeScope::class.java, "MathLib")

        val addFn = cls.createStaticFunction("add",
            listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        val ins = addFn.instructions
        addFn.ret(ins.add(addFn.param(0), addFn.param(1)))
        addFn.end()

        val classDef = cls.build()
        val module = ir.build()

        assertEquals("MathLib", classDef.name)
        assertTrue(module.functions.any { it.name == "MathLib_add" })
    }

    @Test
    fun defineClassBlockStyle() {
        val ir = ModuleBuilder("test", Target.x86_64())

        val classDef = ir.defineClass(NativeScope::class.java, "Calculator") { calc ->
            calc.field("value", Type.I32)
            calc.defineStaticFunction("add",
                listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32) { fn ->
                val ins = fn.instructions
                fn.ret(ins.add(fn.param(0), fn.param(1)))
            }
        }

        assertEquals("Calculator", classDef.name)
        assertEquals(1, classDef.fields.size)
        assertNotNull(ir.findClass("Calculator"))
    }

    @Test
    fun defineClassKotlinReified() {
        val ir = ModuleBuilder("test", Target.x86_64())

        ir.defineClass<NativeScope>("Point") { point ->
            point.field("x", Type.F64)
            point.field("y", Type.F64)
        }

        val classDef = ir.findClass("Point")
        assertNotNull(classDef)
        assertEquals(2, classDef.fields.size)
    }

    @Test
    fun constructorCreation() {
        val ir = ModuleBuilder("test", Target.x86_64())

        val cls = ir.createClass(NativeScope::class.java, "Pair")
        cls.field("first", Type.I32)
        cls.field("second", Type.I32)

        val ctor = cls.createConstructor(
            listOf(Param("self", Type.OpaquePointer), Param("a", Type.I32), Param("b", Type.I32)))
        ctor.retVoid()
        ctor.end()

        val classDef = cls.build()
        val module = ir.build()

        assertEquals(1, classDef.constructors.size)
        assertTrue(module.functions.any { it.name == "Pair_init" })
    }

    @Test
    fun defineConstructorBlockStyle() {
        val ir = ModuleBuilder("test", Target.x86_64())

        ir.defineClass<NativeScope>("Pair") { cls ->
            cls.field("first", Type.I32)
            cls.field("second", Type.I32)

            cls.defineConstructor(
                listOf(Param("self", Type.OpaquePointer), Param("a", Type.I32), Param("b", Type.I32))) { ctor ->
                ctor.retVoid()
            }
        }

        val classDef = ir.findClass("Pair")!!
        assertEquals(1, classDef.constructors.size)
    }

    @Test
    fun cannotBuildTwice() {
        val ir = ModuleBuilder("test", Target.x86_64())
        val cls = ir.createClass(NativeScope::class.java, "Foo")
        cls.build()

        assertThrows<IllegalStateException> {
            cls.build()
        }
    }

    @Test
    fun autoCloseCallsBuild() {
        val ir = ModuleBuilder("test", Target.x86_64())

        ir.createClass(NativeScope::class.java, "AutoClosed").use { cls ->
            cls.field("x", Type.I32)
        }

        assertNotNull(ir.findClass("AutoClosed"))
    }

    @Test
    fun managedScopeClassBuilder() {
        val ir = ModuleBuilder("test", Target.jvm())

        val cls = ir.createClass(ManagedScope::class.java, "UserService")
        cls.field("name", Type.OpaquePointer)

        val classDef = cls.build()

        assertEquals("UserService", classDef.name)
        assertEquals(1, classDef.fields.size)
    }

    @Test
    fun chainingFieldsAndMetadata() {
        val ir = ModuleBuilder("test", Target.x86_64())

        val classDef = ir.createClass(NativeScope::class.java, "Config")
            .extends("BaseConfig")
            .implements("Serializable")
            .isFinal()
            .field("host", Type.OpaquePointer)
            .field("port", Type.I32)
            .staticField("defaultPort", Type.I32, initializer = Constant.I32(8080))
            .build()

        assertEquals("BaseConfig", classDef.superClass)
        assertEquals(listOf("Serializable"), classDef.interfaces)
        assertTrue(classDef.isFinal)
        assertEquals(2, classDef.fields.size)
        assertEquals(1, classDef.staticFields.size)
    }

    @Test
    fun operatorOverload() {
        val ir = ModuleBuilder("test", Target.x86_64())

        val cls = ir.createClass(NativeScope::class.java, "Vector2")
        cls.field("x", Type.F64)
        cls.field("y", Type.F64)

        cls.defineOperator(org.kgen.ir.types.Operator.PLUS,
            listOf(Param("self", Type.OpaquePointer), Param("other", Type.OpaquePointer)),
            Type.OpaquePointer) { fn ->
            fn.ret(fn.param(0))
        }

        val classDef = cls.build()
        val module = ir.build()

        assertTrue(classDef.methods.any { it.name == "op_plus" })
        assertTrue(module.functions.any { it.name == "Vector2_op_plus" })
    }

    @Test
    fun propertyGetterAndSetter() {
        val ir = ModuleBuilder("test", Target.x86_64())

        val cls = ir.createClass(NativeScope::class.java, "Box")
        cls.field("value", Type.I32)

        cls.defineGetter("value", Type.I32) { fn ->
            fn.ret(Constant.I32(0))
        }

        cls.defineSetter("value", Type.I32) { fn ->
            fn.retVoid()
        }

        val classDef = cls.build()
        val module = ir.build()

        assertTrue(classDef.methods.any { it.name == "get_value" })
        assertTrue(classDef.methods.any { it.name == "set_value" })
        assertTrue(module.functions.any { it.name == "Box_get_value" })
        assertTrue(module.functions.any { it.name == "Box_set_value" })
    }

    @Test
    fun operatorWithTypedProxy() {
        val ir = ModuleBuilder("test", Target.x86_64())

        ir.defineClass(NativeScope::class.java, "IntWrapper") { cls ->
            cls.field("value", Type.I32)

            cls.defineOperator(org.kgen.ir.types.Operator.PLUS,
                listOf(Param("self", Type.OpaquePointer), Param("other", Type.OpaquePointer)),
                Type.OpaquePointer) { fn ->
                val ins = fn.instructions
                val selfVal = ins.load(fn.param(0), Type.I32)
                val otherVal = ins.load(fn.param(1), Type.I32)
                val sum = ins.add(selfVal, otherVal)
                val result = ins.alloca(Type.I32)
                ins.store(sum, result)
                fn.ret(result)
            }
        }

        val module = ir.build()
        assertTrue(module.functions.any { it.name == "IntWrapper_op_plus" })
    }
}
