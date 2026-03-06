package org.kgen.wasm.module

import org.kgen.wasm.*
import org.kgen.wasm.asm.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class WasmModuleWriterTest {

    private fun assertValidWasm(bytes: ByteArray) {
        assertTrue(bytes.size >= 8, "Too short for a valid .wasm module")
        assertEquals(0x00, bytes[0].toInt() and 0xFF)
        assertEquals(0x61, bytes[1].toInt() and 0xFF)
        assertEquals(0x73, bytes[2].toInt() and 0xFF)
        assertEquals(0x6D, bytes[3].toInt() and 0xFF)
        assertEquals(1, bytes[4].toInt() and 0xFF)
    }

    @Test
    fun `round-trip simple add function`() {
        val asm = WasmAssembler.create()
        asm.function("add", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.i32Add()
        }
        val original = asm.assemble()

        val module = WasmModuleReader.read(original)
        val rewritten = WasmModuleWriter.write(module)

        assertValidWasm(rewritten)

        val module2 = WasmModuleReader.read(rewritten)
        assertEquals(module.types, module2.types)
        assertEquals(module.exports.size, module2.exports.size)
        assertEquals(module.functions.size, module2.functions.size)
        assertEquals(module.exports[0].name, module2.exports[0].name)
    }

    @Test
    fun `round-trip with imports`() {
        val asm = WasmAssembler.create()
        asm.importFunction("env", "log", listOf(WasmValueType.I32), emptyList())
        asm.function("main", emptyList(), emptyList(), exported = true) { _, a ->
            a.i32Const(42)
            a.call("log")
        }
        val original = asm.assemble()

        val module = WasmModuleReader.read(original)
        val rewritten = WasmModuleWriter.write(module)

        assertValidWasm(rewritten)
        val module2 = WasmModuleReader.read(rewritten)
        assertEquals(module.imports.size, module2.imports.size)
        val imp = module2.imports[0] as WasmModule.Import.Func
        assertEquals("env", imp.module)
        assertEquals("log", imp.name)
    }

    @Test
    fun `round-trip with memory and globals`() {
        val asm = WasmAssembler.create()
        asm.memory("mem", 1, 10, exported = true)
        asm.global("counter", WasmValueType.I32, true, 0, exported = true)
        asm.function("inc", emptyList(), listOf(WasmValueType.I32), exported = true) { _, a ->
            a.globalGet(0)
            a.i32Const(1)
            a.i32Add()
            a.globalSet(0)
            a.globalGet(0)
        }
        val original = asm.assemble()

        val module = WasmModuleReader.read(original)
        val rewritten = WasmModuleWriter.write(module)

        assertValidWasm(rewritten)
        val module2 = WasmModuleReader.read(rewritten)
        assertEquals(1, module2.memories.size)
        assertEquals(1, module2.memories[0].min)
        assertEquals(10, module2.memories[0].max)
        assertEquals(1, module2.globals.size)
        assertTrue(module2.globals[0].mutable)
    }

    @Test
    fun `round-trip with multiple functions`() {
        val asm = WasmAssembler.create()
        asm.function("square", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(0))
            a.i32Mul()
        }
        asm.function("double", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.i32Const(2)
            a.i32Mul()
        }
        val original = asm.assemble()

        val module = WasmModuleReader.read(original)
        val rewritten = WasmModuleWriter.write(module)

        assertValidWasm(rewritten)
        val module2 = WasmModuleReader.read(rewritten)
        assertEquals(2, module2.functions.size)
        assertEquals(2, module2.exports.size)
    }

    @Test
    fun `round-trip with locals`() {
        val asm = WasmAssembler.create()
        asm.function("with_locals", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { fn, a ->
            val tmp = a.declareLocal("tmp", WasmValueType.I32)
            a.localGet(fn.getParameter(0))
            a.i32Const(10)
            a.i32Add()
            a.localSet(tmp)
            a.localGet(tmp)
        }
        val original = asm.assemble()

        val module = WasmModuleReader.read(original)
        val rewritten = WasmModuleWriter.write(module)

        assertValidWasm(rewritten)
        val module2 = WasmModuleReader.read(rewritten)
        assertEquals(1, module2.functions.size)
        assertEquals(listOf(WasmValueType.I32), module2.functions[0].locals)
    }

    @Test
    fun `round-trip with data segments`() {
        val asm = WasmAssembler.create()
        asm.memory("mem", 1)
        asm.dataSegment(0, 0, "hello".toByteArray())
        asm.function("nop", emptyList(), emptyList()) { _, a ->
            // empty function
        }
        val original = asm.assemble()

        val module = WasmModuleReader.read(original)
        val rewritten = WasmModuleWriter.write(module)

        assertValidWasm(rewritten)
        val module2 = WasmModuleReader.read(rewritten)
        assertEquals(1, module2.dataSegments.size)
        val seg = module2.dataSegments[0] as WasmModule.DataSegment.Active
        assertArrayEquals("hello".toByteArray(), seg.data)
    }

    @Test
    fun `write minimal empty module`() {
        val module = WasmModule(
            version = 1,
            types = emptyList(),
            imports = emptyList(),
            functions = emptyList(),
            tables = emptyList(),
            memories = emptyList(),
            globals = emptyList(),
            exports = emptyList(),
            start = null,
            elements = emptyList(),
            dataSegments = emptyList(),
            customSections = emptyList(),
        )

        val bytes = WasmModuleWriter.write(module)
        assertValidWasm(bytes)
        assertEquals(8, bytes.size)
    }

    @Test
    fun `round-trip preserves function body bytes`() {
        val asm = WasmAssembler.create()
        asm.function("compute", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.i32Add()
            a.i32Const(2)
            a.i32Mul()
            a.i32Const(1)
            a.i32Sub()
        }
        val original = asm.assemble()

        val module = WasmModuleReader.read(original)
        val rewritten = WasmModuleWriter.write(module)
        val module2 = WasmModuleReader.read(rewritten)

        assertArrayEquals(module.functions[0].body, module2.functions[0].body)
    }

    @Test
    fun `round-trip with tables`() {
        val asm = WasmAssembler.create()
        asm.table("tbl", WasmRefType.FUNCREF, 1, 10, exported = true)
        asm.function("nop", emptyList(), emptyList()) { _, _ -> }
        val original = asm.assemble()

        val module = WasmModuleReader.read(original)
        val rewritten = WasmModuleWriter.write(module)

        assertValidWasm(rewritten)
        val module2 = WasmModuleReader.read(rewritten)
        assertEquals(1, module2.tables.size)
        assertEquals(WasmRefType.FUNCREF, module2.tables[0].refType)
        assertEquals(1, module2.tables[0].min)
        assertEquals(10, module2.tables[0].max)
    }
}
