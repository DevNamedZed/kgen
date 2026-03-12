package org.kgen.ir

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.kgen.ir.build.IrBuilder
import org.kgen.ir.target.Target

class IrTierRestrictionTest {

    // --- NATIVE constraints: allows structural + machine, rejects runtime/interop/object ---

    @Test
    fun nativeAllowsArithmetic() {
        val ir = IrBuilder("test", Target.x86_64(), IrConstraints.NATIVE)
        val (x) = ir.createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
        ir.positionAtEnd(ir.appendBlock("entry"))
        val result = ir.add(x, x)
        ir.ret(result)
        ir.finalizeFunction()
        val mod = ir.build()
        assertEquals(1, mod.functions.size)
    }

    @Test
    fun nativeAllowsMemory() {
        val ir = IrBuilder("test", Target.x86_64(), IrConstraints.NATIVE)
        ir.createFunction("f", emptyList(), Type.Void)
        ir.positionAtEnd(ir.appendBlock("entry"))
        val ptr = ir.alloca(Type.I32)
        ir.store(Type.i32(42), ptr)
        ir.ret()
        ir.finalizeFunction()
        assertNotNull(ir.build())
    }

    @Test
    fun nativeAllowsTerminators() {
        val ir = IrBuilder("test", Target.x86_64(), IrConstraints.NATIVE)
        ir.createFunction("f", emptyList(), Type.Void)
        ir.positionAtEnd(ir.appendBlock("entry"))
        ir.ret()
        ir.finalizeFunction()
        assertNotNull(ir.build())
    }

    @Test
    fun nativeAllowsCalls() {
        val ir = IrBuilder("test", Target.x86_64(), IrConstraints.NATIVE)
        val func = ir.declareFunction("ext", listOf(Param("x", Type.I32)), Type.I32)
        ir.createFunction("f", emptyList(), Type.I32)
        ir.positionAtEnd(ir.appendBlock("entry"))
        val result = ir.call(func, listOf(Type.i32(1)), Type.I32)
        ir.ret(result)
        ir.finalizeFunction()
        assertNotNull(ir.build())
    }

    @Test
    fun nativeAllowsDebug() {
        val ir = IrBuilder("test", Target.x86_64(), IrConstraints.NATIVE)
        ir.createFunction("f", emptyList(), Type.Void)
        ir.positionAtEnd(ir.appendBlock("entry"))
        ir.debugLoc(1, 1, "main")
        ir.ret()
        ir.finalizeFunction()
        assertNotNull(ir.build())
    }

    @Test
    fun nativeAllowsComparison() {
        val ir = IrBuilder("test", Target.x86_64(), IrConstraints.NATIVE)
        val (x) = ir.createFunction("f", listOf(Param("x", Type.I32)), Type.I1)
        ir.positionAtEnd(ir.appendBlock("entry"))
        val cmp = ir.icmp(ICmpPredicate.EQ, x, Type.i32(0))
        ir.ret(cmp)
        ir.finalizeFunction()
        assertNotNull(ir.build())
    }

    @Test
    fun nativeAllowsConversion() {
        val ir = IrBuilder("test", Target.x86_64(), IrConstraints.NATIVE)
        val (x) = ir.createFunction("f", listOf(Param("x", Type.I32)), Type.I64)
        ir.positionAtEnd(ir.appendBlock("entry"))
        val ext = ir.sext(x, Type.I64)
        ir.ret(ext)
        ir.finalizeFunction()
        assertNotNull(ir.build())
    }

    @Test
    fun nativeAllowsBitwise() {
        val ir = IrBuilder("test", Target.x86_64(), IrConstraints.NATIVE)
        val (x) = ir.createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
        ir.positionAtEnd(ir.appendBlock("entry"))
        val result = ir.and(x, Type.i32(0xFF))
        ir.ret(result)
        ir.finalizeFunction()
        assertNotNull(ir.build())
    }

    @Test
    fun nativeRejectsNewObject() {
        val ir = IrBuilder("test", Target.x86_64(), IrConstraints.NATIVE)
        ir.createFunction("f", emptyList(), Type.Void)
        ir.positionAtEnd(ir.appendBlock("entry"))
        val ex = assertThrows(IllegalStateException::class.java) {
            ir.newObject("Foo")
        }
        assertTrue(ex.message!!.contains("OBJECT"))
    }

    @Test
    fun nativeRejectsVirtualCall() {
        val ir = IrBuilder("test", Target.x86_64(), IrConstraints.NATIVE)
        val (x) = ir.createFunction("f", listOf(Param("obj", Type.ClassRef("Foo"))), Type.Void)
        ir.positionAtEnd(ir.appendBlock("entry"))
        val ex = assertThrows(IllegalStateException::class.java) {
            ir.virtualCall(x, "Foo", "bar", Type.Function(emptyList(), Type.Void), emptyList())
        }
        assertTrue(ex.message!!.contains("OBJECT"))
    }

    @Test
    fun nativeRejectsGCAlloc() {
        val ir = IrBuilder("test", Target.x86_64(), IrConstraints.NATIVE)
        ir.createFunction("f", emptyList(), Type.Void)
        ir.positionAtEnd(ir.appendBlock("entry"))
        val ex = assertThrows(IllegalStateException::class.java) {
            ir.gcAlloc(Type.I32)
        }
        assertTrue(ex.message!!.contains("RUNTIME"))
    }

    @Test
    fun nativeRejectsGCSafepoint() {
        val ir = IrBuilder("test", Target.x86_64(), IrConstraints.NATIVE)
        ir.createFunction("f", emptyList(), Type.Void)
        ir.positionAtEnd(ir.appendBlock("entry"))
        val ex = assertThrows(IllegalStateException::class.java) {
            ir.gcSafepoint()
        }
        assertTrue(ex.message!!.contains("RUNTIME"))
    }

    @Test
    fun nativeRejectsWriteBarrier() {
        val ir = IrBuilder("test", Target.x86_64(), IrConstraints.NATIVE)
        val (obj) = ir.createFunction("f", listOf(Param("obj", Type.I32)), Type.Void)
        ir.positionAtEnd(ir.appendBlock("entry"))
        val ex = assertThrows(IllegalStateException::class.java) {
            ir.writeBarrier(obj, Type.i32(0), obj)
        }
        assertTrue(ex.message!!.contains("RUNTIME"))
    }

    @Test
    fun nativeRejectsPin() {
        val ir = IrBuilder("test", Target.x86_64(), IrConstraints.NATIVE)
        val refType = Type.Reference(Type.ClassRef("Foo"))
        val (obj) = ir.createFunction("f", listOf(Param("obj", refType)), Type.Void)
        ir.positionAtEnd(ir.appendBlock("entry"))
        val ex = assertThrows(IllegalStateException::class.java) {
            ir.pin(obj)
        }
        assertTrue(ex.message!!.contains("INTEROP"))
    }

    // --- RUNTIME_NATIVE: allows structural + machine + runtime, rejects interop/object ---

    @Test
    fun runtimeNativeAllowsArithmetic() {
        val ir = IrBuilder("test", Target.x86_64(), IrConstraints.RUNTIME_NATIVE)
        val (x) = ir.createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
        ir.positionAtEnd(ir.appendBlock("entry"))
        val result = ir.add(x, x)
        ir.ret(result)
        ir.finalizeFunction()
        assertNotNull(ir.build())
    }

    @Test
    fun runtimeNativeAllowsGCAlloc() {
        val ir = IrBuilder("test", Target.x86_64(), IrConstraints.RUNTIME_NATIVE)
        ir.createFunction("f", emptyList(), Type.Void)
        ir.positionAtEnd(ir.appendBlock("entry"))
        ir.gcAlloc(Type.I32)
        ir.ret()
        ir.finalizeFunction()
        assertNotNull(ir.build())
    }

    @Test
    fun runtimeNativeAllowsGCSafepoint() {
        val ir = IrBuilder("test", Target.x86_64(), IrConstraints.RUNTIME_NATIVE)
        ir.createFunction("f", emptyList(), Type.Void)
        ir.positionAtEnd(ir.appendBlock("entry"))
        ir.gcSafepoint()
        ir.ret()
        ir.finalizeFunction()
        assertNotNull(ir.build())
    }

    @Test
    fun runtimeNativeRejectsNewObject() {
        val ir = IrBuilder("test", Target.x86_64(), IrConstraints.RUNTIME_NATIVE)
        ir.createFunction("f", emptyList(), Type.Void)
        ir.positionAtEnd(ir.appendBlock("entry"))
        val ex = assertThrows(IllegalStateException::class.java) {
            ir.newObject("Foo")
        }
        assertTrue(ex.message!!.contains("OBJECT"))
    }

    @Test
    fun runtimeNativeRejectsGetField() {
        val ir = IrBuilder("test", Target.x86_64(), IrConstraints.RUNTIME_NATIVE)
        val (obj) = ir.createFunction("f", listOf(Param("obj", Type.ClassRef("Foo"))), Type.I32)
        ir.positionAtEnd(ir.appendBlock("entry"))
        val ex = assertThrows(IllegalStateException::class.java) {
            ir.getField(obj, "Foo", "x", Type.I32)
        }
        assertTrue(ex.message!!.contains("OBJECT"))
    }

    @Test
    fun runtimeNativeRejectsArrayGet() {
        val ir = IrBuilder("test", Target.x86_64(), IrConstraints.RUNTIME_NATIVE)
        val (arr) = ir.createFunction("f", listOf(Param("arr", Type.Array(Type.I32, 10))), Type.I32)
        ir.positionAtEnd(ir.appendBlock("entry"))
        val ex = assertThrows(IllegalStateException::class.java) {
            ir.arrayGet(arr, Type.i32(0), Type.I32)
        }
        assertTrue(ex.message!!.contains("OBJECT"))
    }

    @Test
    fun runtimeNativeRejectsPin() {
        val ir = IrBuilder("test", Target.x86_64(), IrConstraints.RUNTIME_NATIVE)
        val refType = Type.Reference(Type.ClassRef("Foo"))
        val (obj) = ir.createFunction("f", listOf(Param("obj", refType)), Type.Void)
        ir.positionAtEnd(ir.appendBlock("entry"))
        val ex = assertThrows(IllegalStateException::class.java) {
            ir.pin(obj)
        }
        assertTrue(ex.message!!.contains("INTEROP"))
    }

    // --- MIXED: allows structural + machine + runtime + interop, rejects object ---

    @Test
    fun mixedAllowsPin() {
        val ir = IrBuilder("test", Target.x86_64(), IrConstraints.MIXED)
        val refType = Type.Reference(Type.ClassRef("Foo"))
        val (obj) = ir.createFunction("f", listOf(Param("obj", refType)), Type.Void)
        ir.positionAtEnd(ir.appendBlock("entry"))
        val pinned = ir.pin(obj)
        ir.unpin(pinned)
        ir.ret()
        ir.finalizeFunction()
        assertNotNull(ir.build())
    }

    @Test
    fun mixedRejectsNewObject() {
        val ir = IrBuilder("test", Target.x86_64(), IrConstraints.MIXED)
        ir.createFunction("f", emptyList(), Type.Void)
        ir.positionAtEnd(ir.appendBlock("entry"))
        val ex = assertThrows(IllegalStateException::class.java) {
            ir.newObject("Foo")
        }
        assertTrue(ex.message!!.contains("OBJECT"))
    }

    // --- No constraints (null): allows everything ---

    @Test
    fun nullConstraintsAllowEverything() {
        val ir = IrBuilder("test", Target.x86_64())
        val (x) = ir.createFunction("f", listOf(Param("x", Type.I32)), Type.Void)
        ir.positionAtEnd(ir.appendBlock("entry"))
        ir.add(x, x)        // machine
        ir.gcSafepoint()     // runtime
        ir.newObject("Foo")  // object
        ir.debugLoc(1, 1, "s") // structural
        ir.ret()
        ir.finalizeFunction()
        assertNotNull(ir.build())
    }

    @Test
    fun defaultConstraintsAreNull() {
        val ir = IrBuilder("test", Target.x86_64())
        assertNull(ir.allowedCategories)
    }

    @Test
    fun defaultConstraintsAllowObjectInstructions() {
        val ir = IrBuilder("test", Target.x86_64())
        ir.createFunction("f", emptyList(), Type.Void)
        ir.positionAtEnd(ir.appendBlock("entry"))
        ir.newObject("Foo")
        ir.ret()
        ir.finalizeFunction()
        assertNotNull(ir.build())
    }

    // --- Exception category is MACHINE tier ---

    @Test
    fun nativeAllowsExceptionHandling() {
        val ir = IrBuilder("test", Target.x86_64(), IrConstraints.NATIVE)
        ir.createFunction("f", emptyList(), Type.Void)
        ir.positionAtEnd(ir.appendBlock("entry"))
        ir.landingPad(Type.I32, emptyList())
        ir.ret()
        ir.finalizeFunction()
        assertNotNull(ir.build())
    }

    @Test
    fun nativeAllowsThrowAsException() {
        // Throw is now OBJECT category, not EXCEPTION
        val ir = IrBuilder("test", Target.x86_64(), IrConstraints.NATIVE)
        val (x) = ir.createFunction("f", listOf(Param("x", Type.I32)), Type.Void)
        ir.positionAtEnd(ir.appendBlock("entry"))
        val ex = assertThrows(IllegalStateException::class.java) {
            ir.throwException(x)
        }
        assertTrue(ex.message!!.contains("OBJECT"))
    }

    // --- Error messages are descriptive ---

    @Test
    fun errorMessageIncludesCategoryName() {
        val ir = IrBuilder("test", Target.x86_64(), IrConstraints.NATIVE)
        ir.createFunction("f", emptyList(), Type.Void)
        ir.positionAtEnd(ir.appendBlock("entry"))
        val ex = assertThrows(IllegalStateException::class.java) {
            ir.newObject("Foo")
        }
        val msg = ex.message!!
        assertTrue(msg.contains("NewObject"), "Should mention instruction name: $msg")
        assertTrue(msg.contains("OBJECT"), "Should mention instruction's category: $msg")
    }

    // --- Structural instructions always pass ---

    @Test
    fun structuralInstructionsAlwaysAllowedWithNative() {
        val ir = IrBuilder("test", Target.x86_64(), IrConstraints.NATIVE)
        ir.createFunction("f", emptyList(), Type.Void)
        ir.positionAtEnd(ir.appendBlock("entry"))
        ir.debugLoc(1, 1, "scope")
        ir.assume(Type.i1(true))
        ir.ret()
        ir.finalizeFunction()
        assertNotNull(ir.build())
    }

    @Test
    fun ssaInstructionsAlwaysAllowedWithNative() {
        val ir = IrBuilder("test", Target.x86_64(), IrConstraints.NATIVE)
        val (x) = ir.createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
        ir.positionAtEnd(ir.appendBlock("entry"))
        val sel = ir.select(Type.i1(true), x, Type.i32(0))
        ir.ret(sel)
        ir.finalizeFunction()
        assertNotNull(ir.build())
    }

    @Test
    fun intrinsicAlwaysAllowedWithNative() {
        val ir = IrBuilder("test", Target.x86_64(), IrConstraints.NATIVE)
        val (x) = ir.createFunction("f", listOf(Param("x", Type.F64)), Type.F64)
        ir.positionAtEnd(ir.appendBlock("entry"))
        val result = ir.intrinsic("llvm.sqrt.f64", listOf(x), Type.F64)
        ir.ret(result)
        ir.finalizeFunction()
        assertNotNull(ir.build())
    }

    // --- Backward compatibility: maxTier derived property ---

    @Test
    fun maxTierDerivedFromConstraints() {
        assertEquals(IrTier.MACHINE, IrBuilder("t", Target.x86_64(), IrConstraints.NATIVE).maxTier)
        assertEquals(IrTier.RUNTIME, IrBuilder("t", Target.x86_64(), IrConstraints.RUNTIME_NATIVE).maxTier)
        assertEquals(IrTier.OBJECT, IrBuilder("t", Target.x86_64(), IrConstraints.ALL).maxTier)
        assertEquals(IrTier.OBJECT, IrBuilder("t", Target.x86_64()).maxTier)
        assertEquals(IrTier.STRUCTURAL, IrBuilder("t", Target.x86_64(), IrConstraints.STRUCTURAL).maxTier)
    }

    // --- Target.defaultConstraints() ---

    @Test
    fun jvmTargetDefaultsToManagedVm() {
        assertEquals(IrConstraints.MANAGED_VM, Target.jvm().defaultConstraints())
    }

    @Test
    fun msilTargetDefaultsToManagedVm() {
        assertEquals(IrConstraints.MANAGED_VM, Target.msil().defaultConstraints())
    }

    @Test
    fun msilMixedTargetDefaultsToAll() {
        assertEquals(IrConstraints.ALL, Target.msilMixed().defaultConstraints())
    }

    @Test
    fun nativeTargetDefaultsToNull() {
        assertNull(Target.x86_64().defaultConstraints())
        assertNull(Target.arm64().defaultConstraints())
        assertNull(Target.riscv64().defaultConstraints())
        assertNull(Target.wasm().defaultConstraints())
    }

    // --- IrConstraints presets ---

    @Test
    fun constraintPresetsContainExpectedCategories() {
        assertTrue(IrCategory.ARITHMETIC in IrConstraints.NATIVE)
        assertFalse(IrCategory.RUNTIME in IrConstraints.NATIVE)
        assertFalse(IrCategory.OBJECT in IrConstraints.NATIVE)
        assertFalse(IrCategory.INTEROP in IrConstraints.NATIVE)

        assertTrue(IrCategory.RUNTIME in IrConstraints.RUNTIME_NATIVE)
        assertFalse(IrCategory.INTEROP in IrConstraints.RUNTIME_NATIVE)

        assertTrue(IrCategory.INTEROP in IrConstraints.MIXED)
        assertFalse(IrCategory.OBJECT in IrConstraints.MIXED)

        assertTrue(IrCategory.OBJECT in IrConstraints.MANAGED_VM)
        assertFalse(IrCategory.ARITHMETIC in IrConstraints.MANAGED_VM)

        for (cat in IrCategory.entries) {
            assertTrue(cat in IrConstraints.ALL, "$cat should be in ALL")
        }
    }

    // --- Submodules ---

    @Test
    fun submoduleConstraintsOverrideModule() {
        val ir = IrBuilder("test", Target.x86_64()) // null = all allowed
        ir.beginSubmodule("native_gc", IrConstraints.NATIVE)
        val (x) = ir.createFunction("gc_func", listOf(Param("x", Type.I32)), Type.I32)
        ir.positionAtEnd(ir.appendBlock("entry"))
        ir.add(x, x) // machine — allowed
        val ex = assertThrows(IllegalStateException::class.java) {
            ir.gcSafepoint() // runtime — rejected by NATIVE
        }
        assertTrue(ex.message!!.contains("RUNTIME"))
    }

    @Test
    fun submoduleFunctionsTracked() {
        val ir = IrBuilder("test", Target.x86_64())
        ir.beginSubmodule("app", IrConstraints.ALL)
        ir.createFunction("f1", emptyList(), Type.Void)
        ir.positionAtEnd(ir.appendBlock("entry"))
        ir.ret()
        ir.finalizeFunction()
        ir.createFunction("f2", emptyList(), Type.Void)
        ir.positionAtEnd(ir.appendBlock("entry"))
        ir.ret()
        ir.finalizeFunction()
        ir.endSubmodule()
        val mod = ir.build()
        assertEquals(1, mod.submodules.size)
        assertEquals("app", mod.submodules[0].name)
        assertEquals(listOf("f1", "f2"), mod.submodules[0].functions)
    }

    @Test
    fun submoduleGlobalsTracked() {
        val ir = IrBuilder("test", Target.x86_64())
        ir.beginSubmodule("data", IrConstraints.NATIVE)
        ir.addGlobal("g1", Type.I32)
        ir.addGlobal("g2", Type.I64)
        ir.endSubmodule()
        val mod = ir.build()
        assertEquals(listOf("g1", "g2"), mod.submodules[0].globals)
    }

    @Test
    fun moduleConstraintsStoredInModule() {
        val ir = IrBuilder("test", Target.x86_64(), IrConstraints.NATIVE)
        val mod = ir.build()
        assertEquals(IrConstraints.NATIVE, mod.constraints)
    }

    @Test
    fun multipleSubmodules() {
        val ir = IrBuilder("test", Target.x86_64())
        ir.beginSubmodule("gc", IrConstraints.RUNTIME_NATIVE)
        ir.createFunction("gc_alloc", emptyList(), Type.Void)
        ir.positionAtEnd(ir.appendBlock("entry"))
        ir.ret()
        ir.finalizeFunction()
        ir.endSubmodule()

        ir.beginSubmodule("app", IrConstraints.ALL)
        ir.createFunction("main", emptyList(), Type.Void)
        ir.positionAtEnd(ir.appendBlock("entry"))
        ir.newObject("Foo") // object — allowed in ALL
        ir.ret()
        ir.finalizeFunction()
        ir.endSubmodule()

        val mod = ir.build()
        assertEquals(2, mod.submodules.size)
        assertEquals("gc", mod.submodules[0].name)
        assertEquals(IrConstraints.RUNTIME_NATIVE, mod.submodules[0].constraints)
        assertEquals("app", mod.submodules[1].name)
        assertEquals(IrConstraints.ALL, mod.submodules[1].constraints)
    }

    @Test
    fun cannotNestSubmodules() {
        val ir = IrBuilder("test", Target.x86_64())
        ir.beginSubmodule("a", IrConstraints.NATIVE)
        val ex = assertThrows(IllegalStateException::class.java) {
            ir.beginSubmodule("b", IrConstraints.ALL)
        }
        assertTrue(ex.message!!.contains("Already in submodule"))
    }

    @Test
    fun cannotBuildWithOpenSubmodule() {
        val ir = IrBuilder("test", Target.x86_64())
        ir.beginSubmodule("a", IrConstraints.NATIVE)
        val ex = assertThrows(IllegalStateException::class.java) {
            ir.build()
        }
        assertTrue(ex.message!!.contains("still open"))
    }

    @Test
    fun functionsOutsideSubmoduleUseModuleConstraints() {
        val ir = IrBuilder("test", Target.x86_64(), IrConstraints.NATIVE)
        // Outside any submodule — uses module-level NATIVE
        ir.createFunction("f", emptyList(), Type.Void)
        ir.positionAtEnd(ir.appendBlock("entry"))
        val ex = assertThrows(IllegalStateException::class.java) {
            ir.gcSafepoint() // RUNTIME — rejected by module-level NATIVE
        }
        assertTrue(ex.message!!.contains("RUNTIME"))
    }
}
