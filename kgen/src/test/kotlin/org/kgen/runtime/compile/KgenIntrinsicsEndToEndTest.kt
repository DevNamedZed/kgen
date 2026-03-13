package org.kgen.runtime.compile

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.kgen.ir.*
import org.kgen.ir.target.Target
import org.kgen.target.jvm.*
import org.kgen.ir.instructions.*

/**
 * Tests compilation of code using Kgen intrinsics (memory access, pointers, GC).
 */
class KgenIntrinsicsEndToEndTest {

    private fun compile(classBytes: ByteArray): Module {
        return RuntimeCompiler(Target.x86_64()).compile(classBytes)
    }

    private fun buildIntrinsicClass(block: ClassFileBuilder.() -> Unit): ByteArray {
        return JvmClassWriter.write(
            ClassFileBuilder("org/kgen/test/Intrinsics").apply(block).build()
        )
    }

    @Test
    fun `loadInt intrinsic produces load instruction`() {
        val classBytes = buildIntrinsicClass {
            method("readInt", "(J)I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.lload(0)
                code.invokestatic("org/kgen/unmanaged/Kgen", "loadInt", "(J)I")
                code.ireturn()
            }
        }

        val module = compile(classBytes)
        val fn = module.functions.first { it.name == "readInt" }
        val instructions = fn.blocks.flatMap { it.instructions }
        assertTrue(instructions.any { it is Load }, "Expected Load from loadInt intrinsic")
    }

    @Test
    fun `storeInt intrinsic produces store instruction`() {
        val classBytes = buildIntrinsicClass {
            method("writeInt", "(JI)V", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.lload(0)
                code.iload(2)
                code.invokestatic("org/kgen/unmanaged/Kgen", "storeInt", "(JI)V")
                code.return_()
            }
        }

        val module = compile(classBytes)
        val fn = module.functions.first { it.name == "writeInt" }
        val instructions = fn.blocks.flatMap { it.instructions }
        assertTrue(instructions.any { it is Store }, "Expected Store from storeInt intrinsic")
    }

    @Test
    fun `loadByte intrinsic produces i8 load`() {
        val classBytes = buildIntrinsicClass {
            method("readByte", "(J)I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.lload(0)
                code.invokestatic("org/kgen/unmanaged/Kgen", "loadByte", "(J)I")
                code.ireturn()
            }
        }

        val module = compile(classBytes)
        val fn = module.functions.first { it.name == "readByte" }
        val instructions = fn.blocks.flatMap { it.instructions }
        val loads = instructions.filterIsInstance<Load>()
        assertTrue(loads.any { it.loadType == Type.I8 }, "Expected I8 Load from loadByte")
    }

    @Test
    fun `stackAlloc intrinsic produces alloca`() {
        val classBytes = buildIntrinsicClass {
            method("allocBuffer", "(I)J", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.iload(0)
                code.invokestatic("org/kgen/unmanaged/Kgen", "stackAlloc", "(I)J")
                code.lreturn()
            }
        }

        val module = compile(classBytes)
        val fn = module.functions.first { it.name == "allocBuffer" }
        val instructions = fn.blocks.flatMap { it.instructions }
        assertTrue(instructions.any { it is Alloca }, "Expected Alloca from stackAlloc")
    }

    @Test
    fun `safepoint intrinsic produces gc safepoint`() {
        val classBytes = buildIntrinsicClass {
            method("checkpoint", "()V", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.invokestatic("org/kgen/unmanaged/Kgen", "safepoint", "()V")
                code.return_()
            }
        }

        val module = compile(classBytes)
        val fn = module.functions.first { it.name == "checkpoint" }
        val instructions = fn.blocks.flatMap { it.instructions }
        assertTrue(instructions.any { it is GCSafepoint }, "Expected GCSafepoint")
    }

    @Test
    fun `hash function with intrinsics compiles to native`() {
        // Simulate: int hash(long ptr, int len) { int h=0; for(int i=0;i<len;i++) h = 31*h + loadByte(ptr+i); return h; }
        // This is a simplified version using raw bytecode
        val cp = ConstantPoolBuilder()
        val thisClass = cp.classEntry("org/kgen/test/Hash")
        val superClass = cp.classEntry("java/lang/Object")
        val codeIdx = cp.utf8("Code")
        val loadByteRef = cp.methodRef("org/kgen/unmanaged/Kgen", "loadByte", "(J)I")

        val nameIdx = cp.utf8("hash")
        val descIdx = cp.utf8("(JI)I")

        // Bytecode: h=0, i=0, loop: if(i>=len) goto end, loadByte(ptr+i), h=31*h+byte, i++, goto loop, end: return h
        // Local 0-1: ptr (long), Local 2: len (int), Local 3: h (int), Local 4: i (int)
        val bytecode = byteArrayOf(
            0x03,                   // 0: iconst_0 (h = 0)
            0x3E,                   // 1: istore_3
            0x03,                   // 2: iconst_0 (i = 0)
            0x39, 0x04,             // 3: dstore 4 -- actually istore 4
        )
        // This gets complex with bytecode, so let's use ClassFileBuilder instead
        val classBytes = JvmClassWriter.write(
            ClassFileBuilder("org/kgen/test/Hash").apply {
                method("hash", "(JI)I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                    // int h = 0
                    code.iconst(0)
                    code.istore(3)
                    // int i = 0
                    code.iconst(0)
                    code.istore(4)
                    // loop header
                    code.label("loop")
                    code.iload(4)
                    code.iload(2)
                    code.ifIcmpge("end")
                    // h = 31 * h + loadByte(ptr + i)
                    code.iconst(31)
                    code.iload(3)
                    code.imul()
                    // loadByte(ptr + i) — ptr is long at local 0-1, i is int at local 4
                    code.lload(0)
                    code.iload(4)
                    code.i2l()
                    code.ladd()
                    code.invokestatic("org/kgen/unmanaged/Kgen", "loadByte", "(J)I")
                    code.iadd()
                    code.istore(3)
                    // i++
                    code.iinc(4, 1)
                    code.goto("loop")
                    // end: return h
                    code.label("end")
                    code.iload(3)
                    code.ireturn()
                }
            }.build()
        )

        val compiler = NativeCompiler(Target.x86_64())
        val obj = compiler.compileToObject(listOf(classBytes))
        assertTrue(obj.symbols.any { it.name == "hash" }, "Expected 'hash' symbol")

        // Also verify the IR has the expected structure
        val module = compile(classBytes)
        val fn = module.functions.first { it.name == "hash" }
        val instructions = fn.blocks.flatMap { it.instructions }
        // Should have loads (from loadByte intrinsic), muls (31 * h), adds
        assertTrue(instructions.any { it is Load }, "Expected Load from loadByte")
        assertTrue(instructions.any { it is Mul }, "Expected Mul for 31 * h")
        assertTrue(instructions.any { it is Add }, "Expected Add for h + byte")
    }

    @Test
    fun `runtimeAlloc intrinsic produces call to kgen_runtime_alloc`() {
        val classBytes = buildIntrinsicClass {
            method("alloc", "(I)J", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.iload(0)
                code.invokestatic("org/kgen/unmanaged/Kgen", "runtimeAlloc", "(I)J")
                code.lreturn()
            }
        }

        val module = compile(classBytes)
        val fn = module.functions.first { it.name == "alloc" }
        val instructions = fn.blocks.flatMap { it.instructions }
        val calls = instructions.filterIsInstance<Call>()
        assertTrue(calls.any { (it.function as? GlobalRef)?.name == "kgen_runtime_alloc" },
            "Expected call to kgen_runtime_alloc")
    }

    @Test
    fun `multiple intrinsic calls in one function`() {
        val classBytes = buildIntrinsicClass {
            method("copyInt", "(JJ)V", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                // int val = Kgen.loadInt(src); Kgen.storeInt(dst, val);
                code.lload(0) // src
                code.invokestatic("org/kgen/unmanaged/Kgen", "loadInt", "(J)I")
                code.lload(2) // dst
                // Need to swap: stack has [val, dst] but storeInt wants (addr, val)
                // Actually storeInt(long addr, int val) - addr first
                // Let's restructure
                code.istore(4) // save val
                code.lload(2) // dst addr
                code.iload(4) // val
                code.invokestatic("org/kgen/unmanaged/Kgen", "storeInt", "(JI)V")
                code.return_()
            }
        }

        val module = compile(classBytes)
        val fn = module.functions.first { it.name == "copyInt" }
        val instructions = fn.blocks.flatMap { it.instructions }
        val loads = instructions.filterIsInstance<Load>()
        val stores = instructions.filterIsInstance<Store>()
        assertTrue(loads.isNotEmpty(), "Expected Load from loadInt")
        assertTrue(stores.isNotEmpty(), "Expected Store from storeInt")
    }
}
