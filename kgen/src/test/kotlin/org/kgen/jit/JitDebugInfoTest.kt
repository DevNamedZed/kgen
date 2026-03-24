package org.kgen.jit

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.kgen.target.x86.codegen.X86CodeGenerator
import org.kgen.ir.*
import org.kgen.ir.build.ModuleBuilder
import org.kgen.ir.target.Target
import java.io.File

@EnabledOnOs(OS.WINDOWS, OS.LINUX)
class JitDebugInfoTest {

    private fun buildModule(name: String, funcName: String, value: Long): Module {
        val ir = ModuleBuilder(name, Target.x86_64())
        ir.createFunction(funcName, emptyList(), Type.I64)
        ir.appendBlock("entry")
        ir.ret(Constant.I64(value))
        ir.finalizeFunction()
        return ir.build()
    }

    @Test
    fun listenerReceivesLoadEvents() {
        val jit = JitEngine(X86CodeGenerator())
        val debug = JitDebugInfo()
        val events = mutableListOf<JitDebugInfo.JitEvent>()
        debug.addListener { events.add(it) }
        jit.setDebugInfo(debug)

        jit.addModule(buildModule("m1", "f1", 42))

        assertTrue(events.any { it.type == JitDebugInfo.EventType.LOAD && it.name == "f1" })
        assertTrue(events.all { it.address != 0L })

        jit.close()
    }

    @Test
    fun listenerReceivesUnloadEvents() {
        val jit = JitEngine(X86CodeGenerator())
        val debug = JitDebugInfo()
        val events = mutableListOf<JitDebugInfo.JitEvent>()
        debug.addListener { events.add(it) }
        jit.setDebugInfo(debug)

        val m = jit.addModule(buildModule("m1", "f1", 42))
        jit.removeModule(m)

        assertTrue(events.any { it.type == JitDebugInfo.EventType.UNLOAD && it.name == "f1" })

        jit.close()
    }

    @Test
    fun perfMapWritesSymbols() {
        val tempFile = File.createTempFile("jit-perf-", ".map")
        try {
            val jit = JitEngine(X86CodeGenerator())
            val debug = JitDebugInfo()
            debug.enablePerfMap(tempFile)
            jit.setDebugInfo(debug)

            jit.addModule(buildModule("m1", "f1", 42))
            jit.addModule(buildModule("m2", "f2", 99))

            val content = tempFile.readText()
            assertTrue(content.contains("f1"), "perf map should contain f1")
            assertTrue(content.contains("f2"), "perf map should contain f2")
            // Format: <hex-address> <hex-size> <name>
            val lines = content.lines().filter { it.isNotBlank() }
            assertTrue(lines.size >= 2)
            for (line in lines) {
                val parts = line.split(" ")
                assertTrue(parts.size >= 3, "perf map line should have 3+ parts: $line")
            }

            jit.close()
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun perfMapRemovesUnloadedSymbols() {
        val tempFile = File.createTempFile("jit-perf-", ".map")
        try {
            val jit = JitEngine(X86CodeGenerator())
            val debug = JitDebugInfo()
            debug.enablePerfMap(tempFile)
            jit.setDebugInfo(debug)

            val m1 = jit.addModule(buildModule("m1", "f1", 42))
            jit.addModule(buildModule("m2", "f2", 99))
            jit.removeModule(m1)

            val content = tempFile.readText()
            assertFalse(content.contains("f1"), "perf map should not contain unloaded f1")
            assertTrue(content.contains("f2"), "perf map should still contain f2")

            jit.close()
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun symbolsReturnsCurrentState() {
        val jit = JitEngine(X86CodeGenerator())
        val debug = JitDebugInfo()
        jit.setDebugInfo(debug)

        jit.addModule(buildModule("m1", "f1", 42))
        val symbols = debug.symbols()
        assertEquals(1, symbols.size)
        assertEquals("f1", symbols[0].name)

        jit.close()
    }

    @Test
    fun multipleListeners() {
        val jit = JitEngine(X86CodeGenerator())
        val debug = JitDebugInfo()
        var count1 = 0
        var count2 = 0
        debug.addListener { count1++ }
        debug.addListener { count2++ }
        jit.setDebugInfo(debug)

        jit.addModule(buildModule("m1", "f1", 42))
        assertEquals(1, count1)
        assertEquals(1, count2)

        jit.close()
    }

    @Test
    fun noDebugInfoStillWorks() {
        val jit = JitEngine(X86CodeGenerator())
        jit.addModule(buildModule("m1", "f1", 42))
        assertEquals(42L, jit.call("f1"))
        jit.close()
    }
}
