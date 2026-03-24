package org.kgen.compile

import org.junit.jupiter.api.Test
import org.kgen.ir.*
import org.kgen.ir.build.ModuleBuilder
import org.kgen.ir.build.scope.NativeScope
import org.kgen.ir.target.Target
import org.kgen.codegen.CodeGenOptions
import org.kgen.codegen.OptLevel
import org.kgen.codegen.OutputFormat
import kotlin.test.assertTrue

class CompilerTest {

    @Test
    fun defineModuleAndCompile() {
        val compiler = Compiler("test-app")

        compiler.defineModule("main", TargetProfile.NATIVE) { module ->
            val fn = module.function("main", listOf(Param("argc", Type.I32)), Type.I32)
            fn.ret(fn.param(0))
            fn.end()
        }

        val bytes = compiler.compile(Target.x86_64(), OptLevel.O0,
            CodeGenOptions(outputFormat = OutputFormat.OBJECT))
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun addPreBuiltModule() {
        val module = ModuleBuilder("math", Target.x86_64())
        val fn = module.function("negate", listOf(Param("x", Type.I32)), Type.I32)
        val ins = fn.instructions
        fn.ret(ins.neg(fn.param(0)))
        fn.end()
        val ir = module.build()

        val compiler = Compiler("test-app")
        compiler.addModule(ir)

        val bytes = compiler.compile(Target.x86_64(), OptLevel.O0,
            CodeGenOptions(outputFormat = OutputFormat.OBJECT))
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun compileWithOptLevel() {
        val compiler = Compiler("test-app")

        compiler.defineModule("main", TargetProfile.NATIVE) { module ->
            val fn = module.function("identity", listOf(Param("x", Type.I32)), Type.I32)
            fn.ret(fn.param(0))
            fn.end()
        }

        val bytes = compiler.compile(Target.x86_64(), OptLevel.O2,
            CodeGenOptions(optimizationLevel = OptLevel.O2, outputFormat = OutputFormat.OBJECT))
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun addResource() {
        val compiler = Compiler("test-app")

        compiler.defineModule("main", TargetProfile.NATIVE) { module ->
            val fn = module.function("noop", emptyList(), Type.Void)
            fn.retVoid()
            fn.end()
        }

        compiler.addResource("config.json", """{"key": "value"}""".toByteArray())

        val bytes = compiler.compile(Target.x86_64(), OptLevel.O0,
            CodeGenOptions(outputFormat = OutputFormat.OBJECT))
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun entryPointValidation() {
        val compiler = Compiler("test-app")

        compiler.defineModule("main", TargetProfile.NATIVE) { module ->
            val fn = module.function("main", emptyList(), Type.I32)
            fn.ret(Constant.I32(0))
            fn.end()
        }

        compiler.setEntryPoint("main")

        val bytes = compiler.compile(Target.x86_64(), OptLevel.O0,
            CodeGenOptions(outputFormat = OutputFormat.BINARY))
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun createModuleImperative() {
        val compiler = Compiler("test-app")

        val moduleBuilder = compiler.createModule("main", TargetProfile.NATIVE)
        val fn = moduleBuilder.function("constant", emptyList(), Type.I32)
        fn.ret(Constant.I32(42))
        fn.end()
        compiler.addModule(moduleBuilder.build())

        val bytes = compiler.compile(Target.x86_64(), OptLevel.O0,
            CodeGenOptions(outputFormat = OutputFormat.OBJECT))
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun compileWithClassBuilder() {
        val compiler = Compiler("test-app")

        compiler.defineModule("main", TargetProfile.NATIVE) { module ->
            module.defineClass(NativeScope::class.java, "MathLib") { cls ->
                cls.defineStaticFunction("add",
                    listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32) { fn ->
                    val ins = fn.instructions
                    fn.ret(ins.add(fn.param(0), fn.param(1)))
                }
            }
        }

        val bytes = compiler.compile(Target.x86_64(), OptLevel.O0,
            CodeGenOptions(outputFormat = OutputFormat.OBJECT))
        assertTrue(bytes.isNotEmpty())
    }
}
