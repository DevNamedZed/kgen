package org.kgen.integration.e2e

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.kgen.binary.SectionKind
import org.kgen.ir.*
import org.kgen.ir.target.Target
import org.kgen.jit.JitEngine
import org.kgen.runtime.compile.NativeCompiler
import org.kgen.runtime.compile.NativeLibraryCompiler
import org.kgen.runtime.compile.OutputPlatform
import org.kgen.runtime.compile.StdlibProvider
import org.kgen.target.jvm.*
import org.kgen.target.x86.codegen.X86CodeGenerator
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.ValueLayout.*
import org.kgen.ir.instructions.*

/**
 * Runtime Subset end-to-end: user-written low-level code in Java/Kotlin
 * compiled to native via Kgen intrinsics.
 *
 * This tests the scenario where users write GC, allocators, hash functions,
 * and other performance-critical code using `Kgen.loadInt()`, `Kgen.storeInt()`,
 * `Kgen.offset()`, etc. — Java/Kotlin code that looks like and performs like C.
 *
 * Pipeline: ClassFileBuilder -> RuntimeCompiler -> IR (with intrinsic lowering)
 *           -> CodeGenerator -> native object/executable
 */
class RuntimeSubsetEndToEndTest {

    // -- Helpers --

    private fun buildClass(
        className: String,
        methods: List<Pair<String, (ClassFileBuilder.CodeEmitter) -> Unit>>,
    ): ByteArray {
        val builder = ClassFileBuilder(className)
        for ((sig, body) in methods) {
            val parts = sig.split(":")
            builder.method(parts[0], parts[1], AccessFlags.PUBLIC or AccessFlags.STATIC, body)
        }
        return builder.toBytes()
    }

    private fun callI32(jit: JitEngine, name: String, vararg args: Int): Int {
        val descriptor = FunctionDescriptor.of(JAVA_INT, *Array(args.size) { JAVA_INT })
        val handle = jit.handle(name, descriptor)
        return when (args.size) {
            0 -> handle.invoke() as Int
            1 -> handle.invoke(args[0]) as Int
            2 -> handle.invoke(args[0], args[1]) as Int
            3 -> handle.invoke(args[0], args[1], args[2]) as Int
            else -> handle.invokeWithArguments(*args.map { it as Any }.toTypedArray()) as Int
        }
    }

    private fun callI64(jit: JitEngine, name: String, vararg args: Long): Long {
        val descriptor = FunctionDescriptor.of(JAVA_LONG, *Array(args.size) { JAVA_LONG })
        val handle = jit.handle(name, descriptor)
        return when (args.size) {
            0 -> handle.invoke() as Long
            1 -> handle.invoke(args[0]) as Long
            2 -> handle.invoke(args[0], args[1]) as Long
            3 -> handle.invoke(args[0], args[1], args[2]) as Long
            else -> handle.invokeWithArguments(*args.map { it as Any }.toTypedArray()) as Long
        }
    }

    // -- Intrinsic lowering: Kgen.loadInt/storeInt/offset -> native Load/Store/Add --

    @Test
    fun intrinsicLoadStoreCompilesToNative() {
        // User writes: Kgen.storeInt(addr, val) and Kgen.loadInt(addr)
        // These lower to native Load/Store instructions
        val classBytes = buildClass("org/kgen/test/MemOps", listOf(
            "readInt:(J)I" to { code ->
                code.lload(0)
                code.invokestatic("org/kgen/runtime/Kgen", "loadInt", "(J)I")
                code.ireturn()
            },
            "writeInt:(JI)V" to { code ->
                code.lload(0)
                code.iload(2)
                code.invokestatic("org/kgen/runtime/Kgen", "storeInt", "(JI)V")
                code.return_()
            },
        ))

        val compiler = NativeCompiler(Target.x86_64())
        val obj = compiler.compileToObject(listOf(classBytes))

        assertTrue(obj.sections.any { it.kind == SectionKind.TEXT })
        val symNames = obj.symbols.map { it.name }
        assertTrue("readInt" in symNames, "Missing 'readInt': $symNames")
        assertTrue("writeInt" in symNames, "Missing 'writeInt': $symNames")

        // Verify intrinsics lowered to IR (not left as calls to Kgen)
        val modules = compiler.compileToModules(listOf(classBytes))
        val mod = modules[0]
        val readFn = mod.functions.first { it.name == "readInt" }
        val insts = readFn.blocks.flatMap { it.instructions }
        assertTrue(insts.any { it is Load },
            "loadInt intrinsic should lower to Load: $insts")
    }

    @Test
    fun intrinsicOffsetCompilesToAdd() {
        // Kgen.offset(base, bytes) -> Add(base, bytes) — pointer arithmetic
        val classBytes = buildClass("org/kgen/test/PtrArith", listOf(
            "addOffset:(JI)J" to { code ->
                code.lload(0)
                code.iload(2)
                code.invokestatic("org/kgen/runtime/Kgen", "offset", "(JI)J")
                code.lreturn()
            },
        ))

        val modules = NativeCompiler(Target.x86_64()).compileToModules(listOf(classBytes))
        val fn = modules[0].functions.first { it.name == "addOffset" }
        val insts = fn.blocks.flatMap { it.instructions }
        assertTrue(insts.any { it is Add },
            "offset intrinsic should lower to Add: $insts")
    }

    // -- User-written bump allocator (Java that performs like C) --

    @Test
    fun bumpAllocatorCompilesToNativeObject() {
        // A user writes a bump allocator in Java using Kgen intrinsics.
        // It compiles to native code with direct memory access.
        // Signature: alloc(cursor_ptr: long, limit: long, size: int) -> long
        // JVM slots: cursor_ptr=0,1  limit=2,3  size=4  cursor=5,6  next=7,8
        val classBytes = buildClass("org/kgen/test/BumpAlloc", listOf(
            "alloc:(JJI)J" to { code ->
                // long cursor = Kgen.loadLong(cursor_ptr)
                code.lload(0)
                code.invokestatic("org/kgen/runtime/Kgen", "loadLong", "(J)J")
                code.lstore(5)

                // long next = cursor + (long)size
                code.lload(5)
                code.iload(4)
                code.i2l()
                code.ladd()
                code.lstore(7)

                // if (next > limit) return 0
                code.lload(7)
                code.lload(2)
                code.lcmp()
                code.ifle("ok")
                code.lconst(0)
                code.lreturn()

                // Kgen.storeLong(cursor_ptr, next)
                code.label("ok")
                code.lload(0)
                code.lload(7)
                code.invokestatic("org/kgen/runtime/Kgen", "storeLong", "(JJ)V")
                // return cursor
                code.lload(5)
                code.lreturn()
            },
        ))

        val compiler = NativeCompiler(Target.x86_64())
        val obj = compiler.compileToObject(listOf(classBytes))

        assertTrue(obj.sections.any { it.kind == SectionKind.TEXT })
        assertTrue(obj.symbols.any { it.name == "alloc" })

        // Verify the IR has loads and stores (from intrinsics), not calls to Kgen
        val modules = compiler.compileToModules(listOf(classBytes))
        val fn = modules[0].functions.first { it.name == "alloc" }
        val insts = fn.blocks.flatMap { it.instructions }
        assertTrue(insts.any { it is Load }, "Should have Load from loadLong")
        assertTrue(insts.any { it is Store }, "Should have Store from storeLong")
        // Should NOT have a call to "loadLong" or "storeLong" — intrinsics are inlined
        val calls = insts.filterIsInstance<Call>()
        assertFalse(calls.any { it.function.name.contains("loadLong") || it.function.name.contains("storeLong") },
            "Intrinsics should be lowered, not left as calls: $calls")
    }

    // -- User-written hash function --

    @Test
    fun stringHashCompilesToNative() {
        // hash(ptr, len) — reads bytes from memory and computes hash
        val classBytes = buildClass("org/kgen/test/StringHash", listOf(
            "hash:(JI)I" to { code ->
                // int h = 0;
                code.iconst(0); code.istore(3)

                // int i = 0;
                code.iconst(0); code.istore(4)

                code.label("loop")
                // if (i >= len) break
                code.iload(4); code.iload(2)
                code.ifIcmpge("done")

                // byte b = Kgen.loadByte(ptr + i)
                code.lload(0)   // ptr
                code.iload(4)   // i
                code.invokestatic("org/kgen/runtime/Kgen", "offset", "(JI)J")
                code.invokestatic("org/kgen/runtime/Kgen", "loadByte", "(J)I")
                code.istore(5)  // b

                // h = 31 * h + b
                code.iconst(31)
                code.iload(3)   // h
                code.imul()
                code.iload(5)   // b
                code.iadd()
                code.istore(3)  // h

                // i++
                code.iload(4); code.iconst(1); code.iadd(); code.istore(4)
                code.goto("loop")

                code.label("done")
                code.iload(3)
                code.ireturn()
            }
        ))

        val compiler = NativeCompiler(Target.x86_64())
        val obj = compiler.compileToObject(listOf(classBytes))

        assertTrue(obj.symbols.any { it.name == "hash" })
        // Verify the text section has real code
        val text = obj.sections.first { it.kind == SectionKind.TEXT }
        assertTrue(text.data.size > 10, "Hash function should produce non-trivial code: ${text.data.size}")
    }

    // -- GC mark phase in user code --

    @Test
    fun gcMarkPhaseCompilesToNative() {
        // User writes a GC mark phase: reads object header, sets mark bit,
        // traces reference fields — all via Kgen intrinsics
        val classBytes = buildClass("org/kgen/test/GCMark", listOf(
            // mark(objPtr, heapBase, heapEnd)
            // Simplified: just sets mark bit in header
            "mark:(JJJ)V" to { code ->
                // if (objPtr < heapBase || objPtr >= heapEnd) return
                code.lload(0)  // objPtr
                code.lload(2)  // heapBase
                code.lcmp()
                code.iflt("bail")
                code.lload(0)  // objPtr
                code.lload(4)  // heapEnd
                code.lcmp()
                code.ifge("bail")

                // int flags = Kgen.loadInt(objPtr + 4)  // GC flags at offset 4
                code.lload(0)
                code.iconst(4)
                code.invokestatic("org/kgen/runtime/Kgen", "offset", "(JI)J")
                code.invokestatic("org/kgen/runtime/Kgen", "loadInt", "(J)I")
                code.istore(6) // flags

                // if ((flags & 1) != 0) return  // already marked
                code.iload(6)
                code.iconst(1)
                code.iand()
                code.ifne("bail")

                // Kgen.storeInt(objPtr + 4, flags | 1)  // set mark bit
                code.lload(0)
                code.iconst(4)
                code.invokestatic("org/kgen/runtime/Kgen", "offset", "(JI)J")
                code.iload(6)
                code.iconst(1)
                code.ior()
                code.invokestatic("org/kgen/runtime/Kgen", "storeInt", "(JI)V")
                code.return_()

                code.label("bail")
                code.return_()
            },
        ))

        val compiler = NativeCompiler(Target.x86_64())
        val obj = compiler.compileToObject(listOf(classBytes))

        assertTrue(obj.symbols.any { it.name == "mark" })

        // Verify intrinsics lowered: should have Load + Store in IR, not Kgen calls
        val modules = compiler.compileToModules(listOf(classBytes))
        val fn = modules[0].functions.first { it.name == "mark" }
        val insts = fn.blocks.flatMap { it.instructions }
        assertTrue(insts.any { it is Load }, "Should load GC flags")
        assertTrue(insts.any { it is Store }, "Should store updated GC flags")
    }

    // -- Stdlib provider generates native implementations --

    @Test
    fun stdlibProviderGeneratesValidIr() {
        val stdlib = StdlibProvider.generate(Target.x86_64())

        // Should have stdlib functions
        assertTrue(stdlib.functions.isNotEmpty(), "Stdlib should generate functions")

        // Check some known functions exist
        val names = stdlib.functions.map { it.name }.toSet()
        assertTrue("kgen_println_int" in names || "kgen_println_str" in names,
            "Should have println variants: $names")
    }

    @Test
    fun stdlibProviderNativeNameMapping() {
        // System.out.println(String) -> kgen_println_str
        val name = StdlibProvider.nativeName("java/io/PrintStream", "println", "(Ljava/lang/String;)V")
        assertNotNull(name, "println(String) should map to native name")
        assertEquals("kgen_println_str", name)
    }

    @Test
    fun stdlibProviderMathMapping() {
        val absName = StdlibProvider.nativeName("java/lang/Math", "abs", "(I)I")
        assertNotNull(absName)
        assertTrue(absName!!.startsWith("kgen_math_"), "Math.abs should map to kgen_math_*: $absName")
    }

    // -- Intrinsic code compiles to all native backends --

    @Test
    fun intrinsicsCompileToArm64() {
        val classBytes = buildClass("org/kgen/test/ArmMem", listOf(
            "readInt:(J)I" to { code ->
                code.lload(0)
                code.invokestatic("org/kgen/runtime/Kgen", "loadInt", "(J)I")
                code.ireturn()
            },
        ))

        val compiler = NativeCompiler(Target.arm64())
        val obj = compiler.compileToObject(listOf(classBytes))

        assertTrue(obj.sections.any { it.kind == SectionKind.TEXT })
        val text = obj.sections.first { it.kind == SectionKind.TEXT }
        assertEquals(0, text.data.size % 4, "ARM64 code must be 4-byte aligned")
    }

    @Test
    fun intrinsicsCompileToRiscv() {
        val classBytes = buildClass("org/kgen/test/RvMem", listOf(
            "readInt:(J)I" to { code ->
                code.lload(0)
                code.invokestatic("org/kgen/runtime/Kgen", "loadInt", "(J)I")
                code.ireturn()
            },
        ))

        val compiler = NativeCompiler(Target.riscv64())
        val obj = compiler.compileToObject(listOf(classBytes))

        assertTrue(obj.sections.any { it.kind == SectionKind.TEXT })
    }

    // -- Full pipeline: intrinsic code -> executable --

    @Test
    fun intrinsicCodeCompilestoElfExecutable() {
        val classBytes = buildClass("org/kgen/test/MemMain", listOf(
            "main:()I" to { code ->
                // Simple: return 42 (no intrinsics in main, but proves pipeline works)
                code.iconst(42); code.ireturn()
            },
            "readMem:(J)I" to { code ->
                code.lload(0)
                code.invokestatic("org/kgen/runtime/Kgen", "loadInt", "(J)I")
                code.ireturn()
            },
        ))

        val compiler = NativeCompiler(Target.x86_64(), OutputPlatform.LINUX)
        val elfBytes = compiler.compile(listOf(classBytes))

        assertEquals(0x7F, elfBytes[0].toInt() and 0xFF)
        assertTrue(elfBytes.size > 200)
    }

    // -- JIT execution of runtime subset code --

    @Test
    @EnabledOnOs(OS.LINUX, OS.WINDOWS)
    fun jitExecutesArithmeticFromClassfile() {
        // Basic sanity: classfile -> JIT -> execute
        val classBytes = buildClass("org/kgen/test/JitArith", listOf(
            "add:(II)I" to { code ->
                code.iload(0); code.iload(1); code.iadd(); code.ireturn()
            },
            "mul:(II)I" to { code ->
                code.iload(0); code.iload(1); code.imul(); code.ireturn()
            },
        ))

        JitEngine(X86CodeGenerator()).use { jit ->
            jit.addRuntimeClass(classBytes)
            assertEquals(30, callI32(jit, "add", 10, 20))
            assertEquals(42, callI32(jit, "mul", 6, 7))
        }
    }

    @Test
    @EnabledOnOs(OS.LINUX, OS.WINDOWS)
    fun jitExecutesBranchingFromClassfile() {
        val classBytes = buildClass("org/kgen/test/JitMax", listOf(
            "max:(II)I" to { code ->
                code.iload(0); code.iload(1)
                code.ifIcmpge("first")
                code.iload(1); code.ireturn()
                code.label("first")
                code.iload(0); code.ireturn()
            }
        ))

        JitEngine(X86CodeGenerator()).use { jit ->
            jit.addRuntimeClass(classBytes)
            assertEquals(7, callI32(jit, "max", 3, 7))
            assertEquals(7, callI32(jit, "max", 7, 3))
            assertEquals(5, callI32(jit, "max", 5, 5))
        }
    }

    @Test
    @EnabledOnOs(OS.LINUX, OS.WINDOWS)
    fun jitExecutesLoopFromClassfile() {
        val classBytes = buildClass("org/kgen/test/JitSum", listOf(
            "sum:(I)I" to { code ->
                code.iconst(0); code.istore(1)
                code.label("loop")
                code.iload(0); code.ifle("done")
                code.iload(1); code.iload(0)
                code.iadd(); code.istore(1)
                code.iload(0); code.iconst(1)
                code.isub(); code.istore(0)
                code.goto("loop")
                code.label("done")
                code.iload(1); code.ireturn()
            }
        ))

        JitEngine(X86CodeGenerator()).use { jit ->
            jit.addRuntimeClass(classBytes)
            assertEquals(15, callI32(jit, "sum", 5))
            assertEquals(55, callI32(jit, "sum", 10))
            assertEquals(0, callI32(jit, "sum", 0))
        }
    }

    @Test
    @EnabledOnOs(OS.LINUX, OS.WINDOWS)
    fun jitExecutesFibonacci() {
        val classBytes = buildClass("org/kgen/test/JitFib", listOf(
            "fib:(I)I" to { code ->
                code.iconst(0); code.istore(1)
                code.iconst(1); code.istore(2)
                code.label("loop")
                code.iload(0); code.ifle("done")
                code.iload(1); code.iload(2)
                code.iadd()
                code.iload(2); code.istore(1)
                code.istore(2)
                code.iload(0); code.iconst(1)
                code.isub(); code.istore(0)
                code.goto("loop")
                code.label("done")
                code.iload(1); code.ireturn()
            }
        ))

        JitEngine(X86CodeGenerator()).use { jit ->
            jit.addRuntimeClass(classBytes)
            assertEquals(0, callI32(jit, "fib", 0))
            assertEquals(1, callI32(jit, "fib", 1))
            assertEquals(5, callI32(jit, "fib", 5))
        }
    }

    @Test
    @EnabledOnOs(OS.LINUX, OS.WINDOWS)
    fun jitExecutesFactorial() {
        val classBytes = buildClass("org/kgen/test/JitFact", listOf(
            "fact:(I)I" to { code ->
                code.iconst(1); code.istore(1)
                code.label("loop")
                code.iload(0); code.iconst(1)
                code.ifIcmple("done")
                code.iload(1); code.iload(0)
                code.imul(); code.istore(1)
                code.iload(0); code.iconst(1)
                code.isub(); code.istore(0)
                code.goto("loop")
                code.label("done")
                code.iload(1); code.ireturn()
            }
        ))

        JitEngine(X86CodeGenerator()).use { jit ->
            jit.addRuntimeClass(classBytes)
            assertEquals(1, callI32(jit, "fact", 0))
            assertEquals(6, callI32(jit, "fact", 3))
            assertEquals(120, callI32(jit, "fact", 5))
        }
    }
}
