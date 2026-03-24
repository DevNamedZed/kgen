package org.kgen.ir.build.scope

import org.junit.jupiter.api.Test
import org.kgen.codegen.CodeGenOptions
import org.kgen.codegen.OutputFormat
import org.kgen.compile.Compiler
import org.kgen.ir.*
import org.kgen.ir.build.ModuleBuilder
import org.kgen.ir.build.sets.InstructionBuilder
import org.kgen.ir.target.Target
import org.kgen.ir.types.Operator
import org.kgen.codegen.OptLevel
import org.kgen.pipeline.Pipeline
import org.kgen.pipeline.PipelineBuilder
import org.kgen.pipeline.PipelineStage
import org.kgen.target.x86.codegen.X86CodeGenerator
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FullApiIntegrationTest {

    @Test
    fun moduleBuilderWithScopedClassAndFunctions() {
        val module = ModuleBuilder("geometry", TargetProfile.NATIVE)

        module.defineClass(NativeScope::class.java, "Point") { cls ->
            cls.field("x", Type.F64)
            cls.field("y", Type.F64)

            cls.defineConstructor(
                listOf(Param("self", Type.OpaquePointer), Param("x", Type.F64), Param("y", Type.F64))) { fn ->
                val ins = fn.instructions
                ins.store(fn.param(1), fn.param(0))
                fn.retVoid()
            }

            cls.defineFunction("magnitude",
                listOf(Param("self", Type.OpaquePointer)), Type.F64) { fn ->
                val ins = fn.instructions
                val x = ins.load(fn.param(0), Type.F64)
                val xSquared = ins.fmul(x, x)
                fn.ret(ins.sqrt(xSquared))
            }

            cls.defineOperator(Operator.PLUS,
                listOf(Param("self", Type.OpaquePointer), Param("other", Type.OpaquePointer)),
                Type.OpaquePointer) { fn ->
                val ins = fn.instructions
                val result = ins.alloca(Type.F64)
                val selfVal = ins.load(fn.param(0), Type.F64)
                val otherVal = ins.load(fn.param(1), Type.F64)
                ins.store(ins.fadd(selfVal, otherVal), result)
                fn.ret(result)
            }
        }

        val ir = module.build()

        assertEquals(TargetProfile.NATIVE, ir.profile)
        assertNotNull(module.findClass("Point"))
        assertTrue(ir.functions.any { it.name == "Point_init" })
        assertTrue(ir.functions.any { it.name == "Point_magnitude" })
        assertTrue(ir.functions.any { it.name == "Point_op_plus" })
    }

    @Test
    fun compilerEndToEnd() {
        val compiler = Compiler("calculator")

        compiler.defineModule("main", TargetProfile.NATIVE) { module ->
            module.defineClass(NativeScope::class.java, "Calculator") { cls ->
                cls.field("accumulator", Type.I32)

                cls.defineStaticFunction("add",
                    listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32) { fn ->
                    val ins = fn.instructions
                    fn.ret(ins.add(fn.param(0), fn.param(1)))
                }

                cls.defineStaticFunction("factorial",
                    listOf(Param("n", Type.I32)), Type.I32) { fn ->
                    val ins = fn.instructions
                    val result = ins.variable(Constant.I32(1))
                    val i = ins.variable(Constant.I32(1))

                    fn.whileLoop(
                        { ins.le(ins.get(i), fn.param(0)) },
                        {
                            ins.set(result, ins.mul(ins.get(result), ins.get(i)))
                            ins.set(i, ins.add(ins.get(i), Constant.I32(1)))
                        },
                    )

                    fn.ret(ins.get(result))
                }
            }
        }

        val bytes = compiler.compile(Target.x86_64(), OptLevel.O0,
            CodeGenOptions(outputFormat = OutputFormat.OBJECT))
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun pipelineBuilderWithCustomStage() {
        val module = ModuleBuilder("test", Target.x86_64())
        module.defineFunction(NativeScope::class.java, "identity",
            listOf(Param("x", Type.I32)), Type.I32) { fn ->
            fn.ret(fn.param(0))
        }
        val ir = module.build()

        val stagesExecuted = mutableListOf<String>()
        val pipeline = PipelineBuilder.forOptLevel(OptLevel.O1)
            .addAfter("mem2reg", "my-analysis", PipelineStage { m ->
                stagesExecuted.add("my-analysis")
                m
            })
            .build()

        val optimized = pipeline.execute(ir)
        assertTrue(stagesExecuted.contains("my-analysis"))
        assertEquals(1, optimized.functions.size)
    }

    @Test
    fun directCodegenWithScopedFunction() {
        val module = ModuleBuilder("test", Target.x86_64())

        module.defineFunction(NativeScope::class.java, "sum_to_n",
            listOf(Param("n", Type.I32)), Type.I32) { fn ->
            val ins = fn.instructions

            val sum = ins.variable(Constant.I32(0))
            val i = ins.variable(Constant.I32(1))

            fn.whileLoop(
                { ins.le(ins.get(i), fn.param(0)) },
                {
                    ins.set(sum, ins.add(ins.get(sum), ins.get(i)))
                    ins.set(i, ins.add(ins.get(i), Constant.I32(1)))
                },
            )

            fn.ret(ins.get(sum))
        }

        val ir = module.build()
        val code = X86CodeGenerator().generateCode(ir)
        assertTrue(code.textBytes.isNotEmpty())
    }

    @Test
    fun managedScopeClassWithObjectModel() {
        val module = ModuleBuilder("test", Target.jvm())

        module.defineClass(ManagedScope::class.java, "UserService") { cls ->
            cls.field("connectionPool", Type.OpaquePointer)

            cls.defineFunction("createUser",
                listOf(Param("self", Type.ClassRef("UserService")), Param("name", Type.OpaquePointer)),
                Type.ClassRef("User")) { fn ->
                val ins = fn.instructions
                val user = ins.newObject("User")
                ins.putField(user, "User", "name", Type.OpaquePointer, fn.param(1))
                fn.ret(user)
            }
        }

        val ir = module.build()
        assertTrue(ir.functions.any { it.name == "UserService_createUser" })
        assertNotNull(module.findClass("UserService"))
    }

    @Test
    fun lowLevelInstructionBuilderDirectUsage() {
        val module = ModuleBuilder("test", Target.x86_64())
        module.createFunction("test_fn", listOf(Param("x", Type.I32)), Type.I32)
        module.appendBlock("entry")

        val builder = InstructionBuilder.create(NativeScope::class.java, module)

        val doubled = builder.add(module.param(0), module.param(0))
        val isPositive = builder.gt(doubled, Constant.I32(0))
        val result = builder.variable(doubled)
        module.ret(builder.get(result))
        module.finalizeFunction()

        val ir = module.build()
        assertEquals(1, ir.functions.size)
    }

    @Test
    fun multipleClassesInOneModule() {
        val module = ModuleBuilder("test", TargetProfile.NATIVE)

        module.defineClass(NativeScope::class.java, "Vector2") { cls ->
            cls.field("x", Type.F64)
            cls.field("y", Type.F64)

            cls.defineStaticFunction("zero", emptyList(), Type.OpaquePointer) { fn ->
                val ins = fn.instructions
                val ptr = ins.alloca(Type.F64)
                ins.store(Constant.F64(0.0), ptr)
                fn.ret(ptr)
            }
        }

        module.defineClass(NativeScope::class.java, "Matrix2x2") { cls ->
            cls.field("data", Type.Array(Type.F64, 4))

            cls.defineStaticFunction("identity", emptyList(), Type.OpaquePointer) { fn ->
                val ins = fn.instructions
                val ptr = ins.alloca(Type.Array(Type.F64, 4))
                fn.ret(ptr)
            }
        }

        val ir = module.build()
        assertEquals(2, ir.classes.size)
        assertTrue(ir.functions.any { it.name == "Vector2_zero" })
        assertTrue(ir.functions.any { it.name == "Matrix2x2_identity" })
    }
}
