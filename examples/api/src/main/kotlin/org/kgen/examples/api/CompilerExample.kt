package org.kgen.examples.api

import org.kgen.codegen.CodeGenOptions
import org.kgen.codegen.OptLevel
import org.kgen.codegen.OutputFormat
import org.kgen.compile.Compiler
import org.kgen.ir.*
import org.kgen.ir.build.ModuleBuilder
import org.kgen.ir.build.scope.NativeScope
import org.kgen.ir.build.scope.ManagedScope
import org.kgen.ir.target.Target
import org.kgen.ir.types.Operator

/**
 * Demonstrates the Compiler API — the top-level orchestrator that ties
 * together IR construction, pipeline transforms, code generation, and binary output.
 *
 * The Compiler lives in `org.kgen.compile` and is the highest-level entry point.
 */
object CompilerExample {

    /**
     * Simplest usage: one module, one function, compile to object file.
     */
    @JvmStatic
    fun singleFunction() {
        val compiler = Compiler("simple")

        compiler.defineModule("main", TargetProfile.NATIVE) { module ->
            module.defineFunction(NativeScope::class.java, "add",
                listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32) { fn ->
                val ins = fn.instructions
                fn.ret(ins.add(fn.param(0), fn.param(1)))
            }
        }

        val bytes = compiler.compile(Target.x86_64(), OptLevel.O0,
            CodeGenOptions(outputFormat = OutputFormat.OBJECT))
        println("Single function: ${bytes.size} bytes")
    }

    /**
     * Build a class with multiple methods using ClassBuilder + NativeScope.
     */
    @JvmStatic
    fun classWithMethods() {
        val compiler = Compiler("math-lib")

        compiler.defineModule("math", TargetProfile.NATIVE) { module ->
            module.defineClass(NativeScope::class.java, "Calculator") { cls ->
                cls.field("accumulator", Type.I32)

                cls.defineStaticFunction("add",
                    listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32) { fn ->
                    val ins = fn.instructions
                    fn.ret(ins.add(fn.param(0), fn.param(1)))
                }

                cls.defineStaticFunction("multiply",
                    listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32) { fn ->
                    val ins = fn.instructions
                    fn.ret(ins.mul(fn.param(0), fn.param(1)))
                }

                cls.defineStaticFunction("negate",
                    listOf(Param("x", Type.I32)), Type.I32) { fn ->
                    val ins = fn.instructions
                    fn.ret(ins.neg(fn.param(0)))
                }

                cls.defineOperator(Operator.PLUS,
                    listOf(Param("self", Type.OpaquePointer), Param("other", Type.OpaquePointer)),
                    Type.OpaquePointer) { fn ->
                    fn.ret(fn.param(0))
                }
            }
        }

        val bytes = compiler.compile(Target.x86_64(), OptLevel.O2,
            CodeGenOptions(outputFormat = OutputFormat.OBJECT))
        println("Class with methods: ${bytes.size} bytes")
    }

    /**
     * Multiple modules compiled together.
     */
    @JvmStatic
    fun multiModule() {
        val compiler = Compiler("multi-module-app")

        compiler.defineModule("math", TargetProfile.NATIVE) { module ->
            module.defineFunction(NativeScope::class.java, "square",
                listOf(Param("x", Type.I32)), Type.I32) { fn ->
                val ins = fn.instructions
                fn.ret(ins.mul(fn.param(0), fn.param(0)))
            }
        }

        compiler.defineModule("main", TargetProfile.NATIVE) { module ->
            module.defineFunction(NativeScope::class.java, "main",
                listOf(Param("argc", Type.I32)), Type.I32) { fn ->
                fn.ret(fn.param(0))
            }
        }

        val bytes = compiler.compile(Target.x86_64(), OptLevel.O0,
            CodeGenOptions(outputFormat = OutputFormat.OBJECT))
        println("Multi-module: ${bytes.size} bytes, modules merged")
    }

    /**
     * Add resources and entry point.
     */
    @JvmStatic
    fun withResources() {
        val compiler = Compiler("resource-app")

        compiler.defineModule("main", TargetProfile.NATIVE) { module ->
            module.defineFunction(NativeScope::class.java, "main", emptyList(), Type.I32) { fn ->
                fn.ret(Constant.I32(0))
            }
        }

        compiler.addResource("config.json", """{"version": "1.0"}""".toByteArray())
        compiler.addResource("schema.sql", "CREATE TABLE users (id INT);".toByteArray())
        compiler.setEntryPoint("main")

        val bytes = compiler.compile(Target.x86_64())
        println("With resources: ${bytes.size} bytes (includes embedded config + schema)")
    }

    /**
     * Pre-built module added to compiler.
     */
    @JvmStatic
    fun preBuiltModule() {
        val module = ModuleBuilder("utils", Target.x86_64())
        module.defineFunction(NativeScope::class.java, "abs",
            listOf(Param("x", Type.I32)), Type.I32) { fn ->
            val ins = fn.instructions
            val isNegative = ins.lt(fn.param(0), Constant.I32(0))
            fn.ret(fn.select(isNegative, ins.neg(fn.param(0)), fn.param(0)))
        }
        val preBuilt = module.build()

        val compiler = Compiler("app")
        compiler.addModule(preBuilt)
        compiler.defineModule("main", TargetProfile.NATIVE) { mod ->
            mod.defineFunction(NativeScope::class.java, "main", emptyList(), Type.I32) { fn ->
                fn.ret(Constant.I32(0))
            }
        }

        val bytes = compiler.compile(Target.x86_64(), OptLevel.O0,
            CodeGenOptions(outputFormat = OutputFormat.OBJECT))
        println("Pre-built + inline modules: ${bytes.size} bytes")
    }

    /**
     * Loops and control flow with the scoped API.
     */
    @JvmStatic
    fun controlFlow() {
        val compiler = Compiler("algorithms")

        compiler.defineModule("main", TargetProfile.NATIVE) { module ->
            module.defineClass(NativeScope::class.java, "Algorithms") { cls ->

                cls.defineStaticFunction("factorial",
                    listOf(Param("n", Type.I32)), Type.I32) { fn ->
                    val ins = fn.instructions
                    val result = ins.variable(Constant.I32(1))
                    val i = ins.variable(Constant.I32(1))

                    fn.whileLoop(
                        condition = { ins.le(ins.get(i), fn.param(0)) },
                        body = {
                            ins.set(result, ins.mul(ins.get(result), ins.get(i)))
                            ins.set(i, ins.add(ins.get(i), Constant.I32(1)))
                        },
                    )

                    fn.ret(ins.get(result))
                }

                cls.defineStaticFunction("fibonacci",
                    listOf(Param("n", Type.I32)), Type.I32) { fn ->
                    val ins = fn.instructions
                    val a = ins.variable(Constant.I32(0))
                    val b = ins.variable(Constant.I32(1))
                    val i = ins.variable(Constant.I32(0))

                    fn.whileLoop(
                        condition = { ins.lt(ins.get(i), fn.param(0)) },
                        body = {
                            val next = ins.add(ins.get(a), ins.get(b))
                            ins.set(a, ins.get(b))
                            ins.set(b, next)
                            ins.set(i, ins.add(ins.get(i), Constant.I32(1)))
                        },
                    )

                    fn.ret(ins.get(a))
                }

                cls.defineStaticFunction("sumToN",
                    listOf(Param("n", Type.I32)), Type.I32) { fn ->
                    val ins = fn.instructions
                    val sum = ins.variable(Constant.I32(0))
                    val i = ins.variable(Constant.I32(1))

                    fn.forLoop(
                        init = {},
                        condition = { ins.le(ins.get(i), fn.param(0)) },
                        update = { ins.set(i, ins.add(ins.get(i), Constant.I32(1))) },
                        body = { ins.set(sum, ins.add(ins.get(sum), ins.get(i))) },
                    )

                    fn.ret(ins.get(sum))
                }
            }
        }

        val bytes = compiler.compile(Target.x86_64(), OptLevel.O2,
            CodeGenOptions(outputFormat = OutputFormat.OBJECT))
        println("Control flow algorithms: ${bytes.size} bytes")
    }

    @JvmStatic
    fun main(args: Array<String>) {
        println("=== Single Function ===")
        singleFunction()

        println("\n=== Class With Methods ===")
        classWithMethods()

        println("\n=== Multi-Module ===")
        multiModule()

        println("\n=== With Resources ===")
        withResources()

        println("\n=== Pre-Built Module ===")
        preBuiltModule()

        println("\n=== Control Flow ===")
        controlFlow()
    }
}
