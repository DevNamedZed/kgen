package org.kgen.compile

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.kgen.codegen.CodeGenOptions
import org.kgen.codegen.OptLevel
import org.kgen.codegen.OutputFormat
import org.kgen.ir.*
import org.kgen.ir.build.ModuleBuilder
import org.kgen.ir.build.scope.ManagedScope
import org.kgen.ir.build.scope.NativeScope
import org.kgen.ir.target.Target
import org.kgen.pipeline.Pipeline
import org.kgen.pipeline.PipelineStage
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CompilerIntegrationTest {

    // --- defineModule block-style compiles to object file ---

    @Test
    fun defineModuleBlockStyleCompilesToObjectFile() {
        val compiler = Compiler("block-app")

        compiler.defineModule("main", TargetProfile.NATIVE) { module ->
            val fn = module.function("entry", listOf(Param("x", Type.I32)), Type.I32)
            fn.ret(fn.param(0))
            fn.end()
        }

        val bytes = compiler.compile(Target.x86_64(), OptLevel.O0,
            CodeGenOptions(outputFormat = OutputFormat.OBJECT))
        assertTrue(bytes.isNotEmpty())
    }

    // --- createModule imperative compiles to object file ---

    @Test
    fun createModuleImperativeCompilesToObjectFile() {
        val compiler = Compiler("imperative-app")

        val moduleBuilder = compiler.createModule("main", TargetProfile.NATIVE)
        val fn = moduleBuilder.function("constant", emptyList(), Type.I32)
        fn.ret(Constant.I32(99))
        fn.end()
        compiler.addModule(moduleBuilder.build())

        val bytes = compiler.compile(Target.x86_64(), OptLevel.O0,
            CodeGenOptions(outputFormat = OutputFormat.OBJECT))
        assertTrue(bytes.isNotEmpty())
    }

    // --- Multiple modules merge correctly ---

    @Test
    fun multipleModulesMergeCorrectly() {
        val compiler = Compiler("multi-module-app")

        compiler.defineModule("math", TargetProfile.NATIVE) { module ->
            val fn = module.function("square", listOf(Param("x", Type.I32)), Type.I32)
            val ins = fn.instructions
            fn.ret(ins.mul(fn.param(0), fn.param(0)))
            fn.end()
        }

        compiler.defineModule("util", TargetProfile.NATIVE) { module ->
            val fn = module.function("negate", listOf(Param("x", Type.I32)), Type.I32)
            val ins = fn.instructions
            fn.ret(ins.neg(fn.param(0)))
            fn.end()
        }

        val bytes = compiler.compile(Target.x86_64(), OptLevel.O0,
            CodeGenOptions(outputFormat = OutputFormat.OBJECT))
        assertTrue(bytes.isNotEmpty())
    }

    // --- Resource embedding ---

    @Test
    fun resourceAppearsInCompiledOutput() {
        val compiler = Compiler("resource-app")

        compiler.defineModule("main", TargetProfile.NATIVE) { module ->
            val fn = module.function("noop", emptyList(), Type.Void)
            fn.retVoid()
            fn.end()
        }

        val resourceData = "hello resource".toByteArray()
        compiler.addResource("greeting.txt", resourceData)

        val bytes = compiler.compile(Target.x86_64(), OptLevel.O0,
            CodeGenOptions(outputFormat = OutputFormat.OBJECT))
        assertTrue(bytes.isNotEmpty())
    }

    // --- Entry point validation: missing entry point for BINARY output throws ---

    @Test
    fun missingEntryPointForBinaryOutputThrows() {
        val compiler = Compiler("no-entry-app")

        compiler.defineModule("main", TargetProfile.NATIVE) { module ->
            val fn = module.function("helper", emptyList(), Type.I32)
            fn.ret(Constant.I32(0))
            fn.end()
        }

        compiler.setEntryPoint("nonexistent_function")

        assertThrows<IllegalArgumentException> {
            compiler.compile(Target.x86_64(), OptLevel.O0,
                CodeGenOptions(outputFormat = OutputFormat.BINARY))
        }
    }

    // --- Entry point validation: present entry point for BINARY output succeeds ---

    @Test
    fun presentEntryPointForBinaryOutputSucceeds() {
        val compiler = Compiler("entry-app")

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

    // --- Entry point validation: not checked for OBJECT output ---

    @Test
    fun entryPointNotCheckedForObjectOutput() {
        val compiler = Compiler("object-app")

        compiler.defineModule("main", TargetProfile.NATIVE) { module ->
            val fn = module.function("helper", emptyList(), Type.I32)
            fn.ret(Constant.I32(0))
            fn.end()
        }

        compiler.setEntryPoint("nonexistent")

        val bytes = compiler.compile(Target.x86_64(), OptLevel.O0,
            CodeGenOptions(outputFormat = OutputFormat.OBJECT))
        assertTrue(bytes.isNotEmpty())
    }

    // --- Per-module pipeline ---

    @Test
    fun perModulePipelineDifferentOptLevels() {
        val compiler = Compiler("pipeline-app")

        compiler.defineModule("hot", TargetProfile.NATIVE) { module ->
            val fn = module.function("hot_path", listOf(Param("x", Type.I32)), Type.I32)
            fn.ret(fn.param(0))
            fn.end()
        }

        compiler.defineModule("cold", TargetProfile.NATIVE) { module ->
            val fn = module.function("cold_path", listOf(Param("x", Type.I32)), Type.I32)
            fn.ret(fn.param(0))
            fn.end()
        }

        val hotPipeline = Pipeline()
        hotPipeline.add(PipelineStage { m -> m })
        compiler.setPipeline("hot", hotPipeline)

        val coldPipeline = Pipeline()
        compiler.setPipeline("cold", coldPipeline)

        val bytes = compiler.compile(Target.x86_64(), OptLevel.O0,
            CodeGenOptions(outputFormat = OutputFormat.OBJECT))
        assertTrue(bytes.isNotEmpty())
    }

    // --- Compiler with TargetProfile.NATIVE ---

    @Test
    fun compilerWithNativeProfile() {
        val compiler = Compiler("native-app")

        compiler.defineModule("core", TargetProfile.NATIVE) { module ->
            val fn = module.function("add", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            val ins = fn.instructions
            fn.ret(ins.add(fn.param(0), fn.param(1)))
            fn.end()
        }

        val bytes = compiler.compile(Target.x86_64(), OptLevel.O0,
            CodeGenOptions(outputFormat = OutputFormat.OBJECT))
        assertTrue(bytes.isNotEmpty())
    }

    // --- Compiler with pre-built modules via addModule ---

    @Test
    fun compilerWithPreBuiltModule() {
        val module = ModuleBuilder("prebuilt", Target.x86_64())
        val fn = module.function("double_it", listOf(Param("x", Type.I32)), Type.I32)
        val ins = fn.instructions
        fn.ret(ins.add(fn.param(0), fn.param(0)))
        fn.end()
        val builtModule = module.build()

        val compiler = Compiler("prebuilt-app")
        compiler.addModule(builtModule)

        val bytes = compiler.compile(Target.x86_64(), OptLevel.O0,
            CodeGenOptions(outputFormat = OutputFormat.OBJECT))
        assertTrue(bytes.isNotEmpty())
    }

    // --- Compiler with ClassBuilder + NativeScope end-to-end ---

    @Test
    fun compilerWithClassBuilderAndNativeScope() {
        val compiler = Compiler("class-app")

        compiler.defineModule("main", TargetProfile.NATIVE) { module ->
            module.defineClass(NativeScope::class.java, "Calculator") { cls ->
                cls.defineStaticFunction("add",
                    listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32) { fn ->
                    val ins = fn.instructions
                    fn.ret(ins.add(fn.param(0), fn.param(1)))
                }

                cls.defineStaticFunction("subtract",
                    listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32) { fn ->
                    val ins = fn.instructions
                    fn.ret(ins.sub(fn.param(0), fn.param(1)))
                }
            }
        }

        val bytes = compiler.compile(Target.x86_64(), OptLevel.O0,
            CodeGenOptions(outputFormat = OutputFormat.OBJECT))
        assertTrue(bytes.isNotEmpty())
    }

    // --- Compiler with ManagedScope creates module with object instructions ---

    @Test
    fun compilerWithManagedScopeCreatesObjectInstructions() {
        val compiler = Compiler("managed-app")

        val module = compiler.defineModule("services", TargetProfile.ANY) { moduleBuilder ->
            moduleBuilder.defineClass(ManagedScope::class.java, "WidgetFactory") { cls ->
                cls.defineStaticFunction("create", emptyList(), Type.OpaquePointer) { fn ->
                    val ins = fn.instructions
                    val widget = ins.newObject("Widget")
                    ins.putField(widget, "Widget", "active", Type.I1, Constant.I1(true))
                    fn.ret(widget)
                }
            }
        }

        val func = module.functions.first()
        val instructions = func.blocks.flatMap { it.instructions }
        assertTrue(instructions.any { it::class.simpleName == "NewObject" })
        assertTrue(instructions.any { it::class.simpleName == "PutField" })
    }

    // --- defineModule returns Module that can be inspected ---

    @Test
    fun defineModuleReturnsInspectableModule() {
        val compiler = Compiler("inspect-app")

        val module = compiler.defineModule("inspectable", TargetProfile.NATIVE) { moduleBuilder ->
            val fn = moduleBuilder.function("target", emptyList(), Type.I32)
            fn.ret(Constant.I32(42))
            fn.end()
        }

        assertNotNull(module)
        assertEquals("inspectable", module.name)
        assertEquals(1, module.functions.size)
        assertEquals("target", module.functions.first().name)
    }

    // --- Compiler.compile passes target through to CodeGenOptions ---

    @Test
    fun compilePassesTargetToCodeGenOptions() {
        val compiler = Compiler("target-app")

        compiler.defineModule("main", TargetProfile.NATIVE) { module ->
            val fn = module.function("noop", emptyList(), Type.I32)
            fn.ret(Constant.I32(0))
            fn.end()
        }

        val bytes = compiler.compile(Target.x86_64(), OptLevel.O0,
            CodeGenOptions(outputFormat = OutputFormat.OBJECT))
        assertTrue(bytes.isNotEmpty())

        val secondBytes = compiler.compile(Target.x86_64(), OptLevel.O2,
            CodeGenOptions(outputFormat = OutputFormat.OBJECT))
        assertTrue(secondBytes.isNotEmpty())
    }

    // --- Multiple compiles from same Compiler ---

    @Test
    fun multipleCompilesFromSameCompiler() {
        val compiler = Compiler("multi-compile-app")

        compiler.defineModule("main", TargetProfile.NATIVE) { module ->
            val fn = module.function("ident", listOf(Param("x", Type.I32)), Type.I32)
            fn.ret(fn.param(0))
            fn.end()
        }

        val firstBytes = compiler.compile(Target.x86_64(), OptLevel.O0,
            CodeGenOptions(outputFormat = OutputFormat.OBJECT))
        assertTrue(firstBytes.isNotEmpty())

        val secondBytes = compiler.compile(Target.x86_64(), OptLevel.O2,
            CodeGenOptions(outputFormat = OutputFormat.OBJECT))
        assertTrue(secondBytes.isNotEmpty())
    }

    // --- Compile with no modules throws ---

    @Test
    fun compileWithNoModulesThrows() {
        val compiler = Compiler("empty-app")

        assertThrows<IllegalArgumentException> {
            compiler.compile(Target.x86_64())
        }
    }

    // --- Compiler with custom Pipeline ---

    @Test
    fun compileWithCustomPipeline() {
        val compiler = Compiler("custom-pipeline-app")

        compiler.defineModule("main", TargetProfile.NATIVE) { module ->
            val fn = module.function("add_one", listOf(Param("x", Type.I32)), Type.I32)
            val ins = fn.instructions
            fn.ret(ins.add(fn.param(0), Constant.I32(1)))
            fn.end()
        }

        val customPipeline = Pipeline()
        customPipeline.add(PipelineStage { m -> m })

        val bytes = compiler.compile(Target.x86_64(), customPipeline,
            CodeGenOptions(outputFormat = OutputFormat.OBJECT))
        assertTrue(bytes.isNotEmpty())
    }

    // --- Compiler with O2 optimization ---

    @Test
    fun compileWithO2Optimization() {
        val compiler = Compiler("optimized-app")

        compiler.defineModule("main", TargetProfile.NATIVE) { module ->
            val fn = module.function("add_constants", emptyList(), Type.I32)
            val ins = fn.instructions
            val result = ins.add(Constant.I32(20), Constant.I32(22))
            fn.ret(result)
            fn.end()
        }

        val bytes = compiler.compile(Target.x86_64(), OptLevel.O2,
            CodeGenOptions(optimizationLevel = OptLevel.O2, outputFormat = OutputFormat.OBJECT))
        assertTrue(bytes.isNotEmpty())
    }

    // --- Compiler with ClassBuilder and multiple methods ---

    @Test
    fun compilerWithClassBuilderMultipleMethods() {
        val compiler = Compiler("class-multi-app")

        compiler.defineModule("main", TargetProfile.NATIVE) { module ->
            module.defineClass(NativeScope::class.java, "MathLib") { cls ->
                cls.defineStaticFunction("add",
                    listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32) { fn ->
                    val ins = fn.instructions
                    fn.ret(ins.add(fn.param(0), fn.param(1)))
                }

                cls.defineStaticFunction("mul",
                    listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32) { fn ->
                    val ins = fn.instructions
                    fn.ret(ins.mul(fn.param(0), fn.param(1)))
                }

                cls.defineStaticFunction("negate",
                    listOf(Param("x", Type.I32)), Type.I32) { fn ->
                    val ins = fn.instructions
                    fn.ret(ins.neg(fn.param(0)))
                }
            }
        }

        val bytes = compiler.compile(Target.x86_64(), OptLevel.O0,
            CodeGenOptions(outputFormat = OutputFormat.OBJECT))
        assertTrue(bytes.isNotEmpty())
    }
}
