package org.kgen.examples.api

import org.kgen.ir.*
import org.kgen.ir.build.ModuleBuilder
import org.kgen.ir.build.scope.NativeScope
import org.kgen.ir.target.Target
import org.kgen.ir.text.IrPrinter

/**
 * Demonstrates building IR modules using the new scoped builder API.
 *
 * ModuleBuilder → ClassBuilder<T> → FunctionBuilder<T> → instructions: T
 *
 * Each level is independently useful. The scope type parameter (NativeScope, ManagedScope, etc.)
 * determines which instructions are available at compile time.
 */
object ModuleBuilderExample {

    /**
     * Builds a simple function that adds two i32 parameters.
     * Uses defineFunction with typed scope — instructions accessed via fn.instructions.
     */
    @JvmStatic
    fun buildAddFunction(): Module {
        val module = ModuleBuilder("add_example", Target.x86_64())

        module.defineFunction(NativeScope::class.java, "add",
            listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32) { fn ->
            val ins = fn.instructions
            fn.ret(ins.add(fn.param(0), fn.param(1)))
        }

        return module.build()
    }

    /**
     * Builds a function with control flow: returns the absolute value of an i32.
     * Uses createFunction for manual lifecycle with typed scope.
     */
    @JvmStatic
    fun buildAbsFunction(): Module {
        val module = ModuleBuilder("abs_example", Target.x86_64())

        val fn = module.createFunction(NativeScope::class.java, "abs",
            listOf(Param("x", Type.I32)), Type.I32)
        val ins = fn.instructions

        val isNegative = ins.lt(fn.param(0), Constant.I32(0))
        fn.ifElse(isNegative,
            thenBody = { fn.ret(ins.neg(fn.param(0))) },
            elseBody = { fn.ret(fn.param(0)) },
        )
        fn.end()

        return module.build()
    }

    /**
     * Builds a module with a global variable and a function that reads/writes it.
     * Uses the typed scope for all instruction emission.
     */
    @JvmStatic
    fun buildGlobalVariableExample(): Module {
        val module = ModuleBuilder("globals_example", Target.x86_64())

        val counter = module.addGlobal("counter", Type.I32, Constant.I32(0))

        module.defineFunction(NativeScope::class.java, "increment",
            emptyList(), Type.I32) { fn ->
            val ins = fn.instructions
            val current = ins.load(counter, Type.I32)
            val incremented = ins.add(current, Constant.I32(1))
            ins.store(incremented, counter)
            fn.ret(incremented)
        }

        return module.build()
    }

    /**
     * Builds a module with multiple functions that call each other.
     * Uses the FunctionBuilder's call method with FunctionRef.
     */
    @JvmStatic
    fun buildFunctionCallExample(): Module {
        val module = ModuleBuilder("call_example", Target.x86_64())

        val square = module.defineFunction(NativeScope::class.java, "square",
            listOf(Param("x", Type.I32)), Type.I32) { fn ->
            val ins = fn.instructions
            fn.ret(ins.mul(fn.param(0), fn.param(0)))
        }

        module.defineFunction(NativeScope::class.java, "sum_of_squares",
            listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32) { fn ->
            val ins = fn.instructions
            val squareA = ins.call(square, listOf(fn.param(0)), Type.I32)
            val squareB = ins.call(square, listOf(fn.param(1)), Type.I32)
            fn.ret(ins.add(squareA!!, squareB!!))
        }

        return module.build()
    }

    /**
     * Builds a function calling an external function (declared, not defined).
     */
    @JvmStatic
    fun buildExternalCallExample(): Module {
        val module = ModuleBuilder("external_example", Target.x86_64())

        val puts = module.declareFunction("puts",
            listOf(Param("str", Type.OpaquePointer)), Type.I32)

        val helloStr = module.addGlobal("hello_str", Type.OpaquePointer,
            Constant.StringConst("hello world"),
            isConstant = true, linkage = Linkage.PRIVATE)

        module.defineFunction(NativeScope::class.java, "main",
            emptyList(), Type.I32) { fn ->
            val ins = fn.instructions
            ins.call(puts, listOf(helloStr), Type.I32)
            fn.ret(Constant.I32(0))
        }

        return module.build()
    }

    /**
     * Builds a class with methods using ClassBuilder + scoped FunctionBuilder.
     * Demonstrates the full builder hierarchy: Module → Class → Function → Instructions.
     */
    @JvmStatic
    fun buildClassExample(): Module {
        val module = ModuleBuilder("class_example", Target.x86_64())

        module.defineClass(NativeScope::class.java, "Counter") { cls ->
            cls.field("value", Type.I32)

            cls.defineStaticFunction("create", emptyList(), Type.OpaquePointer) { fn ->
                val ins = fn.instructions
                val ptr = ins.alloca(Type.I32)
                ins.store(Constant.I32(0), ptr)
                fn.ret(ptr)
            }

            cls.defineStaticFunction("increment",
                listOf(Param("self", Type.OpaquePointer)), Type.I32) { fn ->
                val ins = fn.instructions
                val current = ins.load(fn.param(0), Type.I32)
                val next = ins.add(current, Constant.I32(1))
                ins.store(next, fn.param(0))
                fn.ret(next)
            }
        }

        return module.build()
    }

    /**
     * Builds a loop using the scoped FunctionBuilder control flow helpers
     * with the typed instruction proxy.
     */
    @JvmStatic
    fun buildLoopExample(): Module {
        val module = ModuleBuilder("loop_example", Target.x86_64())

        module.defineFunction(NativeScope::class.java, "sum_to_n",
            listOf(Param("n", Type.I32)), Type.I32) { fn ->
            val ins = fn.instructions

            val sum = ins.variable(Constant.I32(0))
            val i = ins.variable(Constant.I32(1))

            fn.whileLoop(
                condition = { ins.le(ins.get(i), fn.param(0)) },
                body = {
                    ins.set(sum, ins.add(ins.get(sum), ins.get(i)))
                    ins.set(i, ins.add(ins.get(i), Constant.I32(1)))
                },
            )

            fn.ret(ins.get(sum))
        }

        return module.build()
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val printer = IrPrinter()

        println("=== Add Function ===")
        println(printer.print(buildAddFunction()))

        println("=== Abs Function ===")
        println(printer.print(buildAbsFunction()))

        println("=== Global Variable ===")
        println(printer.print(buildGlobalVariableExample()))

        println("=== Function Call ===")
        println(printer.print(buildFunctionCallExample()))

        println("=== External Call ===")
        println(printer.print(buildExternalCallExample()))

        println("=== Class Example ===")
        println(printer.print(buildClassExample()))

        println("=== Loop Example ===")
        println(printer.print(buildLoopExample()))
    }
}
