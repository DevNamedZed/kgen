package org.kgen.examples.api

import org.kgen.codegen.CodeGenOptions
import org.kgen.codegen.OptLevel
import org.kgen.codegen.OutputFormat
import org.kgen.compile.Compiler
import org.kgen.ir.*
import org.kgen.ir.build.ModuleBuilder
import org.kgen.ir.build.scope.FullScope
import org.kgen.ir.build.scope.ManagedScope
import org.kgen.ir.build.scope.NativeScope
import org.kgen.ir.build.sets.InstructionBuilder
import org.kgen.ir.target.Target
import org.kgen.ir.text.IrPrinter
import org.kgen.pipeline.*
import org.kgen.target.x86.X86Condition
import org.kgen.target.x86.X86Register
import org.kgen.target.x86.X86Register32
import org.kgen.target.x86.X86Register64
import org.kgen.target.x86.X86Operand32
import org.kgen.target.x86.asm.X86Assembler
import org.kgen.target.x86.asm.x86

/**
 * Comprehensive example showcasing the scoped IR builder API, the Compiler,
 * the Pipeline system, and the x86 typed-label assembler DSL.
 *
 * Each static method demonstrates a self-contained pattern that users
 * can adapt into their own tooling.
 */
object ScopedApiExample {

    /**
     * Build a native math library using [NativeScope] and [org.kgen.ir.build.ClassBuilder].
     *
     * NativeScope provides arithmetic, memory, bitwise, comparison, and conversion
     * instructions. It does NOT include managed object operations.
     */
    @JvmStatic
    fun nativeMathLibrary(): Module {
        val module = ModuleBuilder("math_lib", Target.x86_64())

        module.defineClass(NativeScope::class.java, "MathLib") { cls ->

            cls.defineStaticFunction("add",
                listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32) { fn ->
                val ins = fn.instructions
                fn.ret(ins.add(fn.param(0), fn.param(1)))
            }

            cls.defineStaticFunction("factorial",
                listOf(Param("n", Type.I32)), Type.I32) { fn ->
                val ins = fn.instructions
                val result = ins.variable(Constant.I32(1))
                val counter = ins.variable(Constant.I32(1))

                fn.whileLoop(
                    condition = { ins.le(ins.get(counter), fn.param(0)) },
                    body = {
                        ins.set(result, ins.mul(ins.get(result), ins.get(counter)))
                        ins.set(counter, ins.add(ins.get(counter), Constant.I32(1)))
                    },
                )
                fn.ret(ins.get(result))
            }

            cls.defineStaticFunction("divmod",
                listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32) { fn ->
                val ins = fn.instructions
                val quotient = ins.div(fn.param(0), fn.param(1))
                val remainder = ins.rem(fn.param(0), fn.param(1))
                fn.ret(ins.add(quotient, remainder))
            }

            cls.defineStaticFunction("bitwiseDemo",
                listOf(Param("value", Type.I32)), Type.I32) { fn ->
                val ins = fn.instructions
                val shifted = ins.shl(fn.param(0), Constant.I32(2))
                val masked = ins.and(shifted, Constant.I32(0xFF))
                val rightShifted = ins.shr(masked, Constant.I32(1))
                fn.ret(rightShifted)
            }
        }

        return module.build()
    }

    /**
     * Build a managed service using [ManagedScope].
     *
     * ManagedScope provides object model operations (newObject, getField, putField,
     * virtualCall, etc.) plus arithmetic and comparisons. It does NOT include raw
     * memory operations or bitwise instructions.
     */
    @JvmStatic
    fun managedService(): Module {
        val module = ModuleBuilder("service", Target.jvm())

        module.defineClass(ManagedScope::class.java, "UserService") { cls ->

            cls.defineStaticFunction("createUser",
                listOf(Param("name", Type.OpaquePointer), Param("age", Type.I32)),
                Type.OpaquePointer) { fn ->
                val ins = fn.instructions
                val user = ins.newObject("User")
                ins.putField(user, "User", "name", Type.OpaquePointer, fn.param(0))
                ins.putField(user, "User", "age", Type.I32, fn.param(1))
                fn.ret(user)
            }

            cls.defineStaticFunction("isAdult",
                listOf(Param("user", Type.OpaquePointer)), Type.I1) { fn ->
                val ins = fn.instructions
                val age = ins.getField(fn.param(0), "User", "age", Type.I32)
                val result = ins.ge(age, Constant.I32(18))
                fn.ret(result)
            }
        }

        return module.build()
    }

    /**
     * Use the [Compiler] to compile both native and managed modules together.
     *
     * The Compiler is the top-level orchestrator: it manages modules, applies
     * pipelines, runs code generation, and produces binary output.
     */
    @JvmStatic
    fun compilerEndToEnd(): ByteArray {
        val compiler = Compiler("full-app")

        compiler.defineModule("math", TargetProfile.NATIVE) { module ->
            module.defineClass(NativeScope::class.java, "MathLib") { cls ->
                cls.defineStaticFunction("square",
                    listOf(Param("x", Type.I32)), Type.I32) { fn ->
                    val ins = fn.instructions
                    fn.ret(ins.mul(fn.param(0), fn.param(0)))
                }
            }
        }

        compiler.defineModule("main", TargetProfile.NATIVE) { module ->
            module.defineFunction(NativeScope::class.java, "main",
                emptyList(), Type.I32) { fn ->
                fn.ret(Constant.I32(0))
            }
        }

        compiler.setEntryPoint("main")
        compiler.addResource("version.txt", "1.0.0".toByteArray())

        return compiler.compile(Target.x86_64(), OptLevel.O2,
            CodeGenOptions(outputFormat = OutputFormat.OBJECT))
    }

    /**
     * Build a custom pipeline with named stages. Demonstrates:
     * - Pipeline.add(name, stage) for named stages
     * - Pipeline.addAfter for inserting custom stages
     * - Pipeline.skip for removing stages
     * - Pipeline.addValidator for phase validation
     */
    @JvmStatic
    fun customPipeline(): Module {
        val module = nativeMathLibrary()

        val pipeline = Pipeline()
        pipeline.add("mem2reg", Mem2Reg())
        pipeline.add("fold", ConstantFolding())
        pipeline.add("dce", DeadCodeElimination())

        pipeline.addAfter("fold", "analysis", PipelineStage { currentModule ->
            println("  [analysis] ${currentModule.functions.size} functions after folding")
            currentModule
        })

        pipeline.addValidator(PipelinePhase.POST_OBJECT_LOWERING)

        return pipeline.execute(module)
    }

    /**
     * Use [PipelineBuilder] to customize an O2 preset pipeline.
     *
     * PipelineBuilder starts from a preset (O0, O1, O2, O3) and allows
     * targeted modifications: skip, addAfter, addBefore, replace.
     */
    @JvmStatic
    fun customizedO2Pipeline(): Module {
        val module = nativeMathLibrary()

        val pipeline = PipelineBuilder.forOptLevel(OptLevel.O2, Target.x86_64())
            .skip("loop-invariant-code-motion")
            .addAfter("inlining", "custom-analysis", PipelineStage { currentModule ->
                println("  [custom] Post-inlining: ${currentModule.functions.size} functions")
                currentModule
            })
            .build()

        return pipeline.execute(module)
    }

    /**
     * Use the x86 assembler DSL with typed labels.
     *
     * Typed labels (via `label()` and `mark()`) are resolved at `toByteArray()` time,
     * supporting both forward and backward references without manual offset calculation.
     */
    @JvmStatic
    fun x86TypedLabelAssembler(): ByteArray {
        val rax = X86Register.RAX as X86Register64
        val eax = X86Register.EAX as X86Register32
        val ecx = X86Register.ECX as X86Register32

        return x86 {
            val loopStart = mark()
            val exitLabel = label()

            nop()
            cmp(eax, ecx as X86Operand32)
            je(exitLabel)
            nop()
            jmp(loopStart)

            mark(exitLabel)
            ret()
        }
    }

    /**
     * Use the x86 assembler directly (non-DSL) for more control.
     *
     * The X86Assembler class provides label(), mark(), and all branch/call
     * methods with typed label overloads. The DSL is sugar over this same API.
     */
    @JvmStatic
    fun x86DirectAssembler(): ByteArray {
        val assembler = X86Assembler()
        val eax = X86Register.EAX as X86Register32

        val skipLabel = assembler.label()
        assembler.mov(eax, 42)
        assembler.jz(skipLabel)
        assembler.nop()
        assembler.mark(skipLabel)
        assembler.ret()

        return assembler.toByteArray()
    }

    /**
     * Direct [InstructionBuilder] usage for low-level proxy creation.
     *
     * InstructionBuilder.create() takes a scope class and an [org.kgen.ir.build.InstructionSink],
     * returning a proxy that implements the scope interface. This is what FunctionBuilder
     * uses internally. Direct usage is for advanced scenarios (custom sinks, testing, tooling).
     */
    @JvmStatic
    fun directInstructionBuilder(): Module {
        val module = ModuleBuilder("direct_builder", Target.x86_64())

        val fn = module.createFunction(NativeScope::class.java, "direct_add",
            listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)

        val ins = fn.instructions
        val sum = ins.add(fn.param(0), fn.param(1))
        val doubled = ins.add(sum, sum)
        fn.ret(doubled)
        fn.end()

        return module.build()
    }

    /**
     * Using [FullScope] for mixed native + managed code.
     *
     * FullScope includes everything: arithmetic, memory, bitwise, object model,
     * runtime, interop, exceptions, and debug. Use when crossing native/managed boundaries.
     */
    @JvmStatic
    fun fullScopeBridge(): Module {
        val module = ModuleBuilder("bridge", Target.x86_64())

        val fn = module.createFunction(FullScope::class.java, "allocate_and_tag",
            listOf(Param("size", Type.I32)), Type.OpaquePointer)

        val ins = fn.instructions
        val ptr = ins.alloca(Type.I32)
        ins.store(fn.param(0), ptr)
        val loaded = ins.load(ptr, Type.I32)
        val shifted = ins.shl(loaded, Constant.I32(4))
        ins.store(shifted, ptr)
        fn.ret(ptr)
        fn.end()

        return module.build()
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val printer = IrPrinter()

        println("=== Native Math Library (NativeScope) ===")
        val nativeModule = nativeMathLibrary()
        println("  Functions: ${nativeModule.functions.map { it.name }}")

        println("\n=== Managed Service (ManagedScope) ===")
        val managedModule = managedService()
        println("  Functions: ${managedModule.functions.map { it.name }}")

        println("\n=== Compiler End-to-End ===")
        val compiled = compilerEndToEnd()
        println("  Output: ${compiled.size} bytes")

        println("\n=== Custom Pipeline ===")
        val pipelineResult = customPipeline()
        println("  Functions after pipeline: ${pipelineResult.functions.size}")

        println("\n=== Customized O2 Pipeline ===")
        val customO2Result = customizedO2Pipeline()
        println("  Functions: ${customO2Result.functions.size}")

        println("\n=== x86 Typed Label DSL ===")
        val x86Dsl = x86TypedLabelAssembler()
        println("  Machine code: ${x86Dsl.size} bytes")

        println("\n=== x86 Direct Assembler ===")
        val x86Direct = x86DirectAssembler()
        println("  Machine code: ${x86Direct.size} bytes")

        println("\n=== Direct InstructionBuilder ===")
        val directModule = directInstructionBuilder()
        println("  ${printer.print(directModule).lines().take(10).joinToString("\n")}")

        println("\n=== FullScope Bridge ===")
        val bridgeModule = fullScopeBridge()
        println("  Functions: ${bridgeModule.functions.map { it.name }}")
    }
}
