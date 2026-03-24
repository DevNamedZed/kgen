package org.kgen.examples.api

import org.kgen.ir.*
import org.kgen.ir.build.ModuleBuilder
import org.kgen.ir.build.scope.NativeScope
import org.kgen.ir.build.scope.ManagedScope
import org.kgen.ir.target.Target
import org.kgen.ir.text.IrPrinter
import org.kgen.ir.types.Operator

/**
 * Demonstrates ClassBuilder — building classes with scoped methods,
 * constructors, operators, getters, and setters.
 */
object ClassBuilderExample {

    /**
     * Native class with fields and static methods.
     */
    @JvmStatic
    fun nativeClass(): Module {
        val module = ModuleBuilder("native_class", Target.x86_64())

        module.defineClass(NativeScope::class.java, "Vec2") { cls ->
            cls.field("x", Type.F64)
            cls.field("y", Type.F64)

            cls.defineConstructor(
                listOf(Param("self", Type.OpaquePointer),
                       Param("x", Type.F64),
                       Param("y", Type.F64))) { fn ->
                val ins = fn.instructions
                ins.store(fn.param(1), fn.param(0))
                fn.retVoid()
            }

            cls.defineFunction("length",
                listOf(Param("self", Type.OpaquePointer)), Type.F64) { fn ->
                val ins = fn.instructions
                val x = ins.load(fn.param(0), Type.F64)
                fn.ret(ins.sqrt(ins.fmul(x, x)))
            }

            cls.defineStaticFunction("zero", emptyList(), Type.OpaquePointer) { fn ->
                val ins = fn.instructions
                val ptr = ins.alloca(Type.F64)
                ins.store(Constant.F64(0.0), ptr)
                fn.ret(ptr)
            }

            cls.defineOperator(Operator.PLUS,
                listOf(Param("self", Type.OpaquePointer),
                       Param("other", Type.OpaquePointer)),
                Type.OpaquePointer) { fn ->
                val ins = fn.instructions
                val selfX = ins.load(fn.param(0), Type.F64)
                val otherX = ins.load(fn.param(1), Type.F64)
                val result = ins.alloca(Type.F64)
                ins.store(ins.fadd(selfX, otherX), result)
                fn.ret(result)
            }

            cls.defineGetter("x", Type.F64) { fn ->
                fn.ret(Constant.F64(0.0))
            }

            cls.defineSetter("x", Type.F64) { fn ->
                fn.retVoid()
            }
        }

        return module.build()
    }

    /**
     * Managed class with object model operations.
     */
    @JvmStatic
    fun managedClass(): Module {
        val module = ModuleBuilder("managed_class", Target.jvm())

        module.defineClass(ManagedScope::class.java, "User") { cls ->
            cls.field("name", Type.OpaquePointer)
            cls.field("age", Type.I32)
            cls.extends("Object")

            cls.defineConstructor(
                listOf(Param("self", Type.ClassRef("User")),
                       Param("name", Type.OpaquePointer),
                       Param("age", Type.I32))) { fn ->
                val ins = fn.instructions
                ins.putField(fn.param(0), "User", "name", Type.OpaquePointer, fn.param(1))
                ins.putField(fn.param(0), "User", "age", Type.I32, fn.param(2))
                fn.retVoid()
            }

            cls.defineFunction("getName",
                listOf(Param("self", Type.ClassRef("User"))),
                Type.OpaquePointer) { fn ->
                val ins = fn.instructions
                fn.ret(ins.getField(fn.param(0), "User", "name", Type.OpaquePointer))
            }

            cls.defineFunction("isAdult",
                listOf(Param("self", Type.ClassRef("User"))),
                Type.I1) { fn ->
                val ins = fn.instructions
                val age = ins.getField(fn.param(0), "User", "age", Type.I32)
                fn.ret(ins.ge(age, Constant.I32(18)))
            }
        }

        return module.build()
    }

    /**
     * Class with inheritance metadata.
     */
    @JvmStatic
    fun classHierarchy(): Module {
        val module = ModuleBuilder("hierarchy", Target.x86_64())

        module.defineClass(NativeScope::class.java, "Shape") { cls ->
            cls.isAbstract()
            cls.field("color", Type.I32)

            cls.defineFunction("area",
                listOf(Param("self", Type.OpaquePointer)), Type.F64) { fn ->
                fn.ret(Constant.F64(0.0))
            }
        }

        module.defineClass(NativeScope::class.java, "Circle") { cls ->
            cls.extends("Shape")
            cls.isFinal()
            cls.field("radius", Type.F64)

            cls.defineFunction("area",
                listOf(Param("self", Type.OpaquePointer)), Type.F64) { fn ->
                val ins = fn.instructions
                val radius = ins.load(fn.param(0), Type.F64)
                val radiusSquared = ins.fmul(radius, radius)
                fn.ret(ins.fmul(Constant.F64(3.14159265), radiusSquared))
            }
        }

        return module.build()
    }

    /**
     * Imperative style with create* instead of define*.
     */
    @JvmStatic
    fun imperativeStyle(): Module {
        val module = ModuleBuilder("imperative", Target.x86_64())

        val cls = module.createClass(NativeScope::class.java, "Counter")
        cls.field("value", Type.I32)

        val increment = cls.createStaticFunction("increment",
            listOf(Param("self", Type.OpaquePointer)), Type.I32)
        val ins = increment.instructions
        val current = ins.load(increment.param(0), Type.I32)
        val next = ins.add(current, Constant.I32(1))
        ins.store(next, increment.param(0))
        increment.ret(next)
        increment.end()

        cls.build()
        return module.build()
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val printer = IrPrinter()

        println("=== Native Class (Vec2) ===")
        val native = nativeClass()
        println("  Classes: ${native.classes.map { it.name }}")
        println("  Functions: ${native.functions.map { it.name }}")

        println("\n=== Managed Class (User) ===")
        val managed = managedClass()
        println("  Classes: ${managed.classes.map { it.name }}")
        println("  Functions: ${managed.functions.map { it.name }}")

        println("\n=== Class Hierarchy ===")
        val hierarchy = classHierarchy()
        println("  Classes: ${hierarchy.classes.map { "${it.name}${if (it.isAbstract) " (abstract)" else ""}${if (it.isFinal) " (final)" else ""}" }}")

        println("\n=== Imperative Style ===")
        val imperative = imperativeStyle()
        println("  Functions: ${imperative.functions.map { it.name }}")
    }
}
