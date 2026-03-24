package org.kgen.codegen

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*
import org.kgen.ir.build.*
import org.kgen.ir.target.Target
import org.kgen.target.x86.codegen.X86CodeGenerator
import org.kgen.target.arm64.codegen.Arm64CodeGenerator
import org.kgen.target.riscv.codegen.RiscVCodeGenerator

class StackMapEmissionTest {

    private fun buildModuleWithSafepoint(target: Target): Module {
        val ir = ModuleBuilder("test", target)
        // Use OpaquePointer for the parameter — at the machine level, GC references are pointers.
        // The GC strategy + GCSafepoint instruction is what triggers stack map recording.
        val params = ir.createFunction(
            "gcFunc",
            listOf(Param("a", Type.I64), Param("b", Type.I64)),
            Type.I64,
            gc = "statepoint",
        )
        ir.appendBlock("entry")
        ir.gcSafepoint()
        val sum = ir.add(params[0], params[1])
        ir.ret(sum)
        ir.finalizeFunction()
        return ir.build()
    }

    private fun buildModuleWithGCRoot(target: Target): Module {
        val ir = ModuleBuilder("test", target)
        val params = ir.createFunction(
            "rootFunc",
            listOf(Param("x", Type.I64)),
            Type.I64,
            gc = "statepoint",
        )
        ir.appendBlock("entry")
        val slot = ir.alloca(Type.I64)
        ir.gcRoot(slot)
        ir.gcSafepoint()
        ir.ret(params[0])
        ir.finalizeFunction()
        return ir.build()
    }

    private fun buildModuleNoGcStrategy(target: Target): Module {
        val ir = ModuleBuilder("test", target)
        val params = ir.createFunction(
            "noGc",
            listOf(Param("a", Type.I32), Param("b", Type.I32)),
            Type.I32,
        )
        ir.appendBlock("entry")
        ir.gcSafepoint()
        val sum = ir.add(params[0], params[1])
        ir.ret(sum)
        ir.finalizeFunction()
        return ir.build()
    }

    private fun buildModuleMultipleSafepoints(target: Target): Module {
        val ir = ModuleBuilder("test", target)
        val params = ir.createFunction(
            "multiSafe",
            listOf(Param("a", Type.I64), Param("b", Type.I64)),
            Type.I64,
            gc = "statepoint",
        )
        ir.appendBlock("entry")
        ir.gcSafepoint()
        ir.gcSafepoint()
        val sum = ir.add(params[0], params[1])
        ir.ret(sum)
        ir.finalizeFunction()
        return ir.build()
    }

    @Test
    fun `x86 safepoint emits stack map entry`() {
        val module = buildModuleWithSafepoint(Target.x86_64())
        val code = X86CodeGenerator().generateCode(module)
        assertEquals(1, code.stackMaps.size)
        val map = code.stackMaps[0]
        assertEquals("gcFunc", map.functionName)
        assertEquals(1, map.entries.size)
        assertTrue(map.entries[0].instructionOffset > 0)
    }

    @Test
    fun `x86 no gc strategy produces no stack maps`() {
        val module = buildModuleNoGcStrategy(Target.x86_64())
        val code = X86CodeGenerator().generateCode(module)
        assertTrue(code.stackMaps.isEmpty())
    }

    @Test
    fun `x86 multiple safepoints produce multiple entries`() {
        val module = buildModuleMultipleSafepoints(Target.x86_64())
        val code = X86CodeGenerator().generateCode(module)
        assertEquals(1, code.stackMaps.size)
        assertEquals(2, code.stackMaps[0].entries.size)
    }

    @Test
    fun `x86 gc root tracked in stack map`() {
        val module = buildModuleWithGCRoot(Target.x86_64())
        val code = X86CodeGenerator().generateCode(module)
        assertEquals(1, code.stackMaps.size)
        val map = code.stackMaps[0]
        assertEquals("rootFunc", map.functionName)
        assertTrue(map.entries.isNotEmpty())
        val entry = map.entries[0]
        assertTrue(entry.locations.any { it is StackMapLocation.Stack })
    }

    @Test
    fun `x86 stack map locations are valid types`() {
        val module = buildModuleWithSafepoint(Target.x86_64())
        val code = X86CodeGenerator().generateCode(module)
        for (map in code.stackMaps) {
            for (entry in map.entries) {
                for (loc in entry.locations) {
                    assertTrue(
                        loc is StackMapLocation.Register ||
                        loc is StackMapLocation.Stack ||
                        loc is StackMapLocation.Constant,
                    )
                }
            }
        }
    }

    @Test
    fun `arm64 safepoint emits stack map entry`() {
        val module = buildModuleWithSafepoint(Target.arm64())
        val code = Arm64CodeGenerator().generateCode(module)
        assertEquals(1, code.stackMaps.size)
        val map = code.stackMaps[0]
        assertEquals("gcFunc", map.functionName)
        assertEquals(1, map.entries.size)
    }

    @Test
    fun `arm64 no gc strategy produces no stack maps`() {
        val module = buildModuleNoGcStrategy(Target.arm64())
        val code = Arm64CodeGenerator().generateCode(module)
        assertTrue(code.stackMaps.isEmpty())
    }

    @Test
    fun `riscv safepoint emits stack map entry`() {
        val module = buildModuleWithSafepoint(Target.riscv64())
        val code = RiscVCodeGenerator().generateCode(module)
        assertEquals(1, code.stackMaps.size)
        val map = code.stackMaps[0]
        assertEquals("gcFunc", map.functionName)
        assertEquals(1, map.entries.size)
    }

    @Test
    fun `riscv no gc strategy produces no stack maps`() {
        val module = buildModuleNoGcStrategy(Target.riscv64())
        val code = RiscVCodeGenerator().generateCode(module)
        assertTrue(code.stackMaps.isEmpty())
    }

    @Test
    fun `compiled code stack maps field defaults to empty`() {
        val code = CompiledCode(textBytes = ByteArray(0))
        assertTrue(code.stackMaps.isEmpty())
    }

    @Test
    fun `stack map entry offsets are non-negative`() {
        val module = buildModuleWithSafepoint(Target.x86_64())
        val code = X86CodeGenerator().generateCode(module)
        for (map in code.stackMaps) {
            for (entry in map.entries) {
                assertTrue(entry.instructionOffset >= 0, "Offset should be non-negative: ${entry.instructionOffset}")
            }
        }
    }

    @Test
    fun `x86 shadow stack strategy also produces stack maps`() {
        val ir = ModuleBuilder("test", Target.x86_64())
        val params = ir.createFunction(
            "shadowFunc",
            listOf(Param("a", Type.I64)),
            Type.I64,
            gc = "shadow-stack",
        )
        ir.appendBlock("entry")
        ir.gcSafepoint()
        ir.ret(params[0])
        ir.finalizeFunction()
        val code = X86CodeGenerator().generateCode(ir.build())
        assertEquals(1, code.stackMaps.size)
        assertEquals("shadowFunc", code.stackMaps[0].functionName)
    }

    // ── Safepoint poll call emission tests ──

    @Test
    fun `x86 safepoint emits call to kgen_safepoint_poll`() {
        val module = buildModuleWithSafepoint(Target.x86_64())
        val code = X86CodeGenerator().generateCode(module)
        val pollRelocs = code.relocations.filter { it.symbol == "kgen_safepoint_poll" }
        assertEquals(1, pollRelocs.size, "Should emit exactly one call to kgen_safepoint_poll")
    }

    @Test
    fun `x86 multiple safepoints emit multiple poll calls`() {
        val module = buildModuleMultipleSafepoints(Target.x86_64())
        val code = X86CodeGenerator().generateCode(module)
        val pollRelocs = code.relocations.filter { it.symbol == "kgen_safepoint_poll" }
        assertEquals(2, pollRelocs.size, "Should emit two calls to kgen_safepoint_poll")
    }

    @Test
    fun `x86 no gc strategy emits no poll calls`() {
        val module = buildModuleNoGcStrategy(Target.x86_64())
        val code = X86CodeGenerator().generateCode(module)
        val pollRelocs = code.relocations.filter { it.symbol == "kgen_safepoint_poll" }
        assertTrue(pollRelocs.isEmpty(), "Should not emit poll calls without GC strategy")
    }

    @Test
    fun `arm64 safepoint emits call to kgen_safepoint_poll`() {
        val module = buildModuleWithSafepoint(Target.arm64())
        val code = Arm64CodeGenerator().generateCode(module)
        val pollRelocs = code.relocations.filter { it.symbol == "kgen_safepoint_poll" }
        assertEquals(1, pollRelocs.size, "Should emit exactly one call to kgen_safepoint_poll")
    }

    @Test
    fun `arm64 no gc strategy emits no poll calls`() {
        val module = buildModuleNoGcStrategy(Target.arm64())
        val code = Arm64CodeGenerator().generateCode(module)
        val pollRelocs = code.relocations.filter { it.symbol == "kgen_safepoint_poll" }
        assertTrue(pollRelocs.isEmpty(), "Should not emit poll calls without GC strategy")
    }

    @Test
    fun `riscv safepoint emits call to kgen_safepoint_poll`() {
        val module = buildModuleWithSafepoint(Target.riscv64())
        val code = RiscVCodeGenerator().generateCode(module)
        val pollRelocs = code.relocations.filter { it.symbol == "kgen_safepoint_poll" }
        assertEquals(1, pollRelocs.size, "Should emit exactly one call to kgen_safepoint_poll")
    }

    @Test
    fun `riscv no gc strategy emits no poll calls`() {
        val module = buildModuleNoGcStrategy(Target.riscv64())
        val code = RiscVCodeGenerator().generateCode(module)
        val pollRelocs = code.relocations.filter { it.symbol == "kgen_safepoint_poll" }
        assertTrue(pollRelocs.isEmpty(), "Should not emit poll calls without GC strategy")
    }
}
