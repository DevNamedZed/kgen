package org.wark

import org.junit.jupiter.api.Test
import org.kgen.target.wasm.WasmValueType
import org.kgen.target.wasm.asm.WasmAssembler
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WarkEndToEndTest {

    private fun buildWasmBytes(block: WasmAssembler.() -> Unit): ByteArray {
        val assembler = WasmAssembler.create()
        assembler.block()
        return assembler.assemble()
    }

    @Test
    fun loadAndQueryModule() {
        val bytes = buildWasmBytes {
            function("add", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                asm.localGet(func.getParameter(0))
                asm.localGet(func.getParameter(1))
                asm.i32Add()
            }
        }

        val runtime = WarkRuntime.create(WasmTarget.MVP)
        val module = runtime.load(bytes)

        val exports = module.exportedFunctionNames()
        assertTrue(exports.contains("add"))
    }

    @Test
    fun instantiateModule() {
        val bytes = buildWasmBytes {
            function("answer", emptyList(), listOf(WasmValueType.I32), exported = true) { func, asm ->
                asm.i32Const(42)
            }
        }

        val runtime = WarkRuntime.create(WasmTarget.MVP)
        val module = runtime.load(bytes)
        val instance = module.instantiate()

        val exportedFunctions = instance.exportedFunctions()
        assertTrue(exportedFunctions.contains("answer"))
    }

    @Test
    fun memoryInitialization() {
        val bytes = buildWasmBytes {
            memory("mem", 1)
            function("readMemory", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                asm.localGet(func.getParameter(0))
                asm.i32Load(0, 0)
            }
        }

        val runtime = WarkRuntime.create(WasmTarget.MVP)
        val module = runtime.load(bytes)
        val instance = module.instantiate()

        val memory = instance.memory()
        assertNotNull(memory)
        assertEquals(1, memory.pages())
        assertEquals(65536, memory.sizeBytes())
    }

    @Test
    fun memoryGrow() {
        val bytes = buildWasmBytes {
            memory("mem", 1)
            function("noop", emptyList(), emptyList(), exported = true) { func, asm -> }
        }

        val runtime = WarkRuntime.create(WasmTarget.MVP)
        val module = runtime.load(bytes)
        val instance = module.instantiate()

        val memory = instance.memory()
        assertEquals(1, memory.pages())

        val previousPages = memory.grow(2)
        assertEquals(1, previousPages)
        assertEquals(3, memory.pages())
    }

    @Test
    fun hostFunctionImport() {
        val bytes = buildWasmBytes {
            importFunction("env", "getAnswer", emptyList(), listOf(WasmValueType.I32))
        }

        var hostCalled = false
        val imports = WarkImports.builder()
            .function("env", "getAnswer") { instance, args ->
                hostCalled = true
                longArrayOf(42L)
            }
            .build()

        val runtime = WarkRuntime.create(WasmTarget.MVP)
        val module = runtime.load(bytes)
        val instance = module.instantiate(imports)

        assertEquals(1, module.importedFunctionCount())
    }

    @Test
    fun globalInitialization() {
        val bytes = buildWasmBytes {
            global("counter", WasmValueType.I32, true, 100)
            function("noop", emptyList(), emptyList(), exported = true) { func, asm -> }
        }

        val runtime = WarkRuntime.create(WasmTarget.MVP)
        val module = runtime.load(bytes)
        val instance = module.instantiate()

        val global = instance.global(0)
        assertEquals(100, global.getI32())
    }

    @Test
    fun featureSetEnforcement() {
        val mvpRuntime = WarkRuntime.create(WasmTarget.MVP)
        val features = mvpRuntime.features
        assertTrue(!features.isEnabled(WasmFeature.SIMD))
        assertTrue(!features.isEnabled(WasmFeature.BULK_MEMORY))

        val v2Runtime = WarkRuntime.create(WasmTarget.V2_0)
        assertTrue(v2Runtime.features.isEnabled(WasmFeature.BULK_MEMORY))
        assertTrue(v2Runtime.features.isEnabled(WasmFeature.REFERENCE_TYPES))
    }

    @Test
    fun multipleExports() {
        val bytes = buildWasmBytes {
            function("add", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                asm.localGet(func.getParameter(0))
                asm.localGet(func.getParameter(1))
                asm.i32Add()
            }
            function("sub", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { func, asm ->
                asm.localGet(func.getParameter(0))
                asm.localGet(func.getParameter(1))
                asm.i32Sub()
            }
        }

        val runtime = WarkRuntime.create(WasmTarget.MVP)
        val module = runtime.load(bytes)
        val exports = module.exportedFunctionNames()

        assertEquals(2, exports.size)
        assertTrue(exports.contains("add"))
        assertTrue(exports.contains("sub"))
    }

    @Test
    fun importedMemory() {
        val bytes = buildWasmBytes {
            importMemory("env", "memory", 1, 256)
            function("noop", emptyList(), emptyList(), exported = true) { func, asm -> }
        }

        val sharedMemory = WarkMemory.create(2, 256)
        sharedMemory.writeI32(0, 12345)

        val imports = WarkImports.builder()
            .memory("env", "memory", sharedMemory)
            .build()

        val runtime = WarkRuntime.create(WasmTarget.MVP)
        val module = runtime.load(bytes)
        val instance = module.instantiate(imports)

        val memory = instance.memory()
        assertEquals(12345, memory.readI32(0))
        assertEquals(2, memory.pages())
    }
}
