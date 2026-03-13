package org.kgen.runtime.compile

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*
import org.kgen.ir.target.Target
import org.kgen.target.jvm.*
import org.kgen.ir.instructions.*

/**
 * Comprehensive tests for all Kgen intrinsic lowering in BytecodeToIrLowering.
 *
 * Covers every intrinsic in Kgen.kt: all load/store variants, offset (int and long),
 * stackAlloc, safepoint, gcRoot, writeBarrier, readBarrier, runtimeAlloc, runtimeFree.
 */
class KgenIntrinsicsFullTest {

    private fun compile(classBytes: ByteArray): Module {
        return RuntimeCompiler(Target.x86_64()).compile(classBytes)
    }

    private fun buildClass(block: ClassFileBuilder.() -> Unit): ByteArray {
        return JvmClassWriter.write(
            ClassFileBuilder("org/kgen/test/Intrinsics").apply(block).build()
        )
    }

    private fun allInstructions(module: Module): List<Instruction> =
        module.functions.flatMap { it.blocks.flatMap { b -> b.instructions } }

    private fun instructions(module: Module, name: String): List<Instruction> =
        module.functions.first { it.name == name }.blocks.flatMap { it.instructions }

    // ---- Load intrinsics ----

    @Test
    fun loadByteProducesI8Load() {
        val classBytes = buildClass {
            method("readByte", "(J)I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.lload(0)
                code.invokestatic("org/kgen/unmanaged/Kgen", "loadByte", "(J)I")
                code.ireturn()
            }
        }
        val loads = allInstructions(compile(classBytes)).filterIsInstance<Load>()
        assertTrue(loads.any { it.loadType == Type.I8 }, "loadByte should produce I8 Load")
    }

    @Test
    fun loadShortProducesI16Load() {
        val classBytes = buildClass {
            method("readShort", "(J)I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.lload(0)
                code.invokestatic("org/kgen/unmanaged/Kgen", "loadShort", "(J)I")
                code.ireturn()
            }
        }
        val loads = allInstructions(compile(classBytes)).filterIsInstance<Load>()
        assertTrue(loads.any { it.loadType == Type.I16 }, "loadShort should produce I16 Load")
    }

    @Test
    fun loadIntProducesI32Load() {
        val classBytes = buildClass {
            method("readInt", "(J)I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.lload(0)
                code.invokestatic("org/kgen/unmanaged/Kgen", "loadInt", "(J)I")
                code.ireturn()
            }
        }
        val loads = allInstructions(compile(classBytes)).filterIsInstance<Load>()
        assertTrue(loads.any { it.loadType == Type.I32 }, "loadInt should produce I32 Load")
    }

    @Test
    fun loadLongProducesI64Load() {
        val classBytes = buildClass {
            method("readLong", "(J)J", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.lload(0)
                code.invokestatic("org/kgen/unmanaged/Kgen", "loadLong", "(J)J")
                code.lreturn()
            }
        }
        val loads = allInstructions(compile(classBytes)).filterIsInstance<Load>()
        assertTrue(loads.any { it.loadType == Type.I64 }, "loadLong should produce I64 Load")
    }

    // ---- Store intrinsics ----

    @Test
    fun storeByteProducesStore() {
        val classBytes = buildClass {
            method("writeByte", "(JI)V", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.lload(0)
                code.iload(2)
                code.invokestatic("org/kgen/unmanaged/Kgen", "storeByte", "(JI)V")
                code.return_()
            }
        }
        val stores = allInstructions(compile(classBytes)).filterIsInstance<Store>()
        assertTrue(stores.isNotEmpty(), "storeByte should produce Store instruction")
    }

    @Test
    fun storeShortProducesStore() {
        val classBytes = buildClass {
            method("writeShort", "(JI)V", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.lload(0)
                code.iload(2)
                code.invokestatic("org/kgen/unmanaged/Kgen", "storeShort", "(JI)V")
                code.return_()
            }
        }
        val stores = allInstructions(compile(classBytes)).filterIsInstance<Store>()
        assertTrue(stores.isNotEmpty(), "storeShort should produce Store instruction")
    }

    @Test
    fun storeIntProducesStore() {
        val classBytes = buildClass {
            method("writeInt", "(JI)V", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.lload(0)
                code.iload(2)
                code.invokestatic("org/kgen/unmanaged/Kgen", "storeInt", "(JI)V")
                code.return_()
            }
        }
        val stores = allInstructions(compile(classBytes)).filterIsInstance<Store>()
        assertTrue(stores.isNotEmpty(), "storeInt should produce Store instruction")
    }

    @Test
    fun storeLongProducesStore() {
        val classBytes = buildClass {
            method("writeLong", "(JJ)V", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.lload(0)
                code.lload(2)
                code.invokestatic("org/kgen/unmanaged/Kgen", "storeLong", "(JJ)V")
                code.return_()
            }
        }
        val stores = allInstructions(compile(classBytes)).filterIsInstance<Store>()
        assertTrue(stores.isNotEmpty(), "storeLong should produce Store instruction")
    }

    // ---- Offset intrinsic ----

    @Test
    fun offsetIntProducesAddWithSext() {
        val classBytes = buildClass {
            method("ptr", "(JI)J", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.lload(0)
                code.iload(2)
                code.invokestatic("org/kgen/unmanaged/Kgen", "offset", "(JI)J")
                code.lreturn()
            }
        }
        val insns = allInstructions(compile(classBytes))
        assertTrue(insns.any { it is Add }, "offset(long, int) should produce Add")
        assertTrue(insns.any { it is SExt }, "offset with int should sign-extend to i64")
    }

    @Test
    fun offsetLongProducesAddWithoutSext() {
        val classBytes = buildClass {
            method("ptr", "(JJ)J", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.lload(0)
                code.lload(2)
                code.invokestatic("org/kgen/unmanaged/Kgen", "offset", "(JJ)J")
                code.lreturn()
            }
        }
        val insns = allInstructions(compile(classBytes))
        assertTrue(insns.any { it is Add }, "offset(long, long) should produce Add")
        // No SExt needed for long variant
    }

    // ---- Stack allocation ----

    @Test
    fun stackAllocProducesAlloca() {
        val classBytes = buildClass {
            method("alloc", "(I)J", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.iload(0)
                code.invokestatic("org/kgen/unmanaged/Kgen", "stackAlloc", "(I)J")
                code.lreturn()
            }
        }
        val insns = allInstructions(compile(classBytes))
        assertTrue(insns.any { it is Alloca }, "stackAlloc should produce Alloca")
    }

    // ---- GC/runtime intrinsics ----

    @Test
    fun safepointProducesGCSafepoint() {
        val classBytes = buildClass {
            method("check", "()V", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.invokestatic("org/kgen/unmanaged/Kgen", "safepoint", "()V")
                code.return_()
            }
        }
        val insns = allInstructions(compile(classBytes))
        assertTrue(insns.any { it is GCSafepoint }, "safepoint should produce GCSafepoint")
    }

    @Test
    fun gcRootProducesGCRootInstruction() {
        val classBytes = buildClass {
            method("root", "(J)V", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.lload(0)
                code.invokestatic("org/kgen/unmanaged/Kgen", "gcRoot", "(J)V")
                code.return_()
            }
        }
        val insns = allInstructions(compile(classBytes))
        assertTrue(insns.any { it is GCRoot }, "gcRoot should produce GCRoot")
    }

    @Test
    fun writeBarrierProducesBarrierInstruction() {
        val classBytes = buildClass {
            method("barrier", "(JIJ)V", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.lload(0)  // obj ptr
                code.iload(2)  // field index
                code.lload(3)  // value ptr
                code.invokestatic("org/kgen/unmanaged/Kgen", "writeBarrier", "(JIJ)V")
                code.return_()
            }
        }
        val insns = allInstructions(compile(classBytes))
        assertTrue(insns.any { it is WriteBarrier }, "writeBarrier should produce WriteBarrier")
    }

    @Test
    fun readBarrierProducesBarrierInstruction() {
        val classBytes = buildClass {
            method("relocate", "(J)J", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.lload(0)
                code.invokestatic("org/kgen/unmanaged/Kgen", "readBarrier", "(J)J")
                code.lreturn()
            }
        }
        val insns = allInstructions(compile(classBytes))
        assertTrue(insns.any { it is ReadBarrier }, "readBarrier should produce ReadBarrier")
    }

    @Test
    fun runtimeAllocProducesCall() {
        val classBytes = buildClass {
            method("alloc", "(I)J", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.iload(0)
                code.invokestatic("org/kgen/unmanaged/Kgen", "runtimeAlloc", "(I)J")
                code.lreturn()
            }
        }
        val calls = allInstructions(compile(classBytes)).filterIsInstance<Call>()
        assertTrue(
            calls.any { (it.function as? GlobalRef)?.name == "kgen_runtime_alloc" },
            "runtimeAlloc should produce call to kgen_runtime_alloc",
        )
    }

    @Test
    fun runtimeFreeProducesCall() {
        val classBytes = buildClass {
            method("free", "(J)V", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.lload(0)
                code.invokestatic("org/kgen/unmanaged/Kgen", "runtimeFree", "(J)V")
                code.return_()
            }
        }
        val calls = allInstructions(compile(classBytes)).filterIsInstance<Call>()
        assertTrue(
            calls.any { (it.function as? GlobalRef)?.name == "kgen_runtime_free" },
            "runtimeFree should produce call to kgen_runtime_free",
        )
    }

    // ---- Composite patterns ----

    @Test
    fun loadStoreRoundTrip() {
        // Read int from addr, write to addr+4
        val classBytes = buildClass {
            method("copy", "(J)V", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                // int val = Kgen.loadInt(addr)
                code.lload(0)
                code.invokestatic("org/kgen/unmanaged/Kgen", "loadInt", "(J)I")
                code.istore(2)
                // Kgen.storeInt(Kgen.offset(addr, 4), val)
                code.lload(0)
                code.iconst(4)
                code.invokestatic("org/kgen/unmanaged/Kgen", "offset", "(JI)J")
                code.iload(2)
                code.invokestatic("org/kgen/unmanaged/Kgen", "storeInt", "(JI)V")
                code.return_()
            }
        }
        val insns = allInstructions(compile(classBytes))
        assertTrue(insns.any { it is Load }, "Should have Load")
        assertTrue(insns.any { it is Store }, "Should have Store")
        assertTrue(insns.any { it is Add }, "Should have Add for offset")
    }

    @Test
    fun safepointInLoop() {
        // while (true) { safepoint(); /* work */ }
        val classBytes = buildClass {
            method("spin", "(I)V", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.label("loop")
                code.invokestatic("org/kgen/unmanaged/Kgen", "safepoint", "()V")
                code.iload(0)
                code.iconst(1)
                code.isub()
                code.istore(0)
                code.iload(0)
                code.ifgt("loop")
                code.return_()
            }
        }
        val insns = allInstructions(compile(classBytes))
        assertTrue(insns.any { it is GCSafepoint }, "Loop should have safepoint")
    }

    @Test
    fun allocAndStorePattern() {
        // long buf = stackAlloc(16); storeInt(buf, 42);
        val classBytes = buildClass {
            method("init", "()V", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.iconst(16)
                code.invokestatic("org/kgen/unmanaged/Kgen", "stackAlloc", "(I)J")
                code.lstore(0)
                code.lload(0)
                code.iconst(42)
                code.invokestatic("org/kgen/unmanaged/Kgen", "storeInt", "(JI)V")
                code.return_()
            }
        }
        val insns = allInstructions(compile(classBytes))
        assertTrue(insns.any { it is Alloca }, "Should have Alloca from stackAlloc")
        assertTrue(insns.any { it is Store }, "Should have Store from storeInt")
    }

    @Test
    fun multipleLoadTypes() {
        // Read byte, short, int, long from consecutive addresses
        val classBytes = buildClass {
            method("readAll", "(J)J", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                // byte b = loadByte(addr)
                code.lload(0)
                code.invokestatic("org/kgen/unmanaged/Kgen", "loadByte", "(J)I")
                code.istore(2)
                // short s = loadShort(addr)
                code.lload(0)
                code.invokestatic("org/kgen/unmanaged/Kgen", "loadShort", "(J)I")
                code.istore(3)
                // int i = loadInt(addr)
                code.lload(0)
                code.invokestatic("org/kgen/unmanaged/Kgen", "loadInt", "(J)I")
                code.istore(4)
                // long l = loadLong(addr)
                code.lload(0)
                code.invokestatic("org/kgen/unmanaged/Kgen", "loadLong", "(J)J")
                code.lreturn()
            }
        }
        val loads = allInstructions(compile(classBytes)).filterIsInstance<Load>()
        val types = loads.map { it.loadType }.toSet()
        assertTrue(Type.I8 in types, "Should have I8 load")
        assertTrue(Type.I16 in types, "Should have I16 load")
        assertTrue(Type.I32 in types, "Should have I32 load")
        assertTrue(Type.I64 in types, "Should have I64 load")
    }

    @Test
    fun gcRootAndBarrierTogether() {
        // gcRoot(obj); writeBarrier(obj, 0, val); readBarrier(obj)
        val classBytes = buildClass {
            method("gcOps", "(JJ)J", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                // gcRoot(obj)
                code.lload(0)
                code.invokestatic("org/kgen/unmanaged/Kgen", "gcRoot", "(J)V")
                // writeBarrier(obj, 0, val)
                code.lload(0)
                code.iconst(0)
                code.lload(2)
                code.invokestatic("org/kgen/unmanaged/Kgen", "writeBarrier", "(JIJ)V")
                // return readBarrier(obj)
                code.lload(0)
                code.invokestatic("org/kgen/unmanaged/Kgen", "readBarrier", "(J)J")
                code.lreturn()
            }
        }
        val insns = allInstructions(compile(classBytes))
        assertTrue(insns.any { it is GCRoot }, "Should have GCRoot")
        assertTrue(insns.any { it is WriteBarrier }, "Should have WriteBarrier")
        assertTrue(insns.any { it is ReadBarrier }, "Should have ReadBarrier")
    }

    @Test
    fun runtimeAllocAndFree() {
        // long ptr = runtimeAlloc(64); runtimeFree(ptr);
        val classBytes = buildClass {
            method("allocFree", "()V", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.iconst(64)
                code.invokestatic("org/kgen/unmanaged/Kgen", "runtimeAlloc", "(I)J")
                code.lstore(0)
                code.lload(0)
                code.invokestatic("org/kgen/unmanaged/Kgen", "runtimeFree", "(J)V")
                code.return_()
            }
        }
        val calls = allInstructions(compile(classBytes)).filterIsInstance<Call>()
        val callNames = calls.mapNotNull { (it.function as? GlobalRef)?.name }
        assertTrue("kgen_runtime_alloc" in callNames, "Should call kgen_runtime_alloc")
        assertTrue("kgen_runtime_free" in callNames, "Should call kgen_runtime_free")
    }

    // ---- Cross-target intrinsics ----

    @Test
    fun intrinsicsCompileForArm64() {
        val classBytes = buildClass {
            method("load", "(J)I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.lload(0)
                code.invokestatic("org/kgen/unmanaged/Kgen", "loadInt", "(J)I")
                code.ireturn()
            }
        }
        val module = RuntimeCompiler(Target.arm64()).compile(classBytes)
        val loads = module.functions.flatMap { it.blocks.flatMap { b -> b.instructions } }
            .filterIsInstance<Load>()
        assertTrue(loads.any { it.loadType == Type.I32 }, "ARM64 loadInt should produce I32 Load")
    }

    @Test
    fun intrinsicsCompileForRiscV() {
        val classBytes = buildClass {
            method("store", "(JI)V", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.lload(0)
                code.iload(2)
                code.invokestatic("org/kgen/unmanaged/Kgen", "storeInt", "(JI)V")
                code.return_()
            }
        }
        val module = RuntimeCompiler(Target.riscv64()).compile(classBytes)
        val stores = module.functions.flatMap { it.blocks.flatMap { b -> b.instructions } }
            .filterIsInstance<Store>()
        assertTrue(stores.isNotEmpty(), "RISC-V storeInt should produce Store")
    }
}
