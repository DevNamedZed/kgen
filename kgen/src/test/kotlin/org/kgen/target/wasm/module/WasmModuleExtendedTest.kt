package org.kgen.target.wasm.module

import org.kgen.target.wasm.*
import org.kgen.target.wasm.asm.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class WasmModuleExtendedTest {

    private fun assertValidWasm(bytes: ByteArray) {
        assertTrue(bytes.size >= 8, "Too short for a valid .wasm module")
        assertEquals(0x00, bytes[0].toInt() and 0xFF)
        assertEquals(0x61, bytes[1].toInt() and 0xFF)
        assertEquals(0x73, bytes[2].toInt() and 0xFF)
        assertEquals(0x6D, bytes[3].toInt() and 0xFF)
        assertEquals(1, bytes[4].toInt() and 0xFF)
    }

    private fun roundTrip(bytes: ByteArray): WasmModule {
        val module = WasmModuleReader.read(bytes)
        val rewritten = WasmModuleWriter.write(module)
        assertValidWasm(rewritten)
        return WasmModuleReader.read(rewritten)
    }

    // --- Writing and reading modules with various section combinations ---

    @Test
    fun `write and read module with only memory section`() {
        val asm = WasmAssembler.create()
        asm.memory("mem", 4, 128)
        asm.function("f", emptyList(), emptyList()) { _, _ -> }
        val module = WasmModuleReader.read(asm.assemble())

        assertEquals(1, module.memories.size)
        assertEquals(4, module.memories[0].min)
        assertEquals(128, module.memories[0].max)
    }

    @Test
    fun `write and read module with only globals`() {
        val asm = WasmAssembler.create()
        asm.global("a", WasmValueType.I32, mutable = true, initValue = 100)
        asm.global("b", WasmValueType.I64, mutable = false, initValue = 999L)
        asm.global("c", WasmValueType.F32, mutable = false, initValue = 0L)
        asm.function("f", emptyList(), emptyList()) { _, _ -> }
        val module = WasmModuleReader.read(asm.assemble())

        assertEquals(3, module.globals.size)
        assertEquals(WasmValueType.I32, module.globals[0].type)
        assertTrue(module.globals[0].mutable)
        assertEquals(WasmValueType.I64, module.globals[1].type)
        assertFalse(module.globals[1].mutable)
        assertEquals(WasmValueType.F32, module.globals[2].type)
    }

    @Test
    fun `write and read module with only tables`() {
        val asm = WasmAssembler.create()
        asm.table("tbl", WasmRefType.FUNCREF, 5, 200)
        asm.function("f", emptyList(), emptyList()) { _, _ -> }
        val module = WasmModuleReader.read(asm.assemble())

        assertEquals(1, module.tables.size)
        assertEquals(WasmRefType.FUNCREF, module.tables[0].refType)
        assertEquals(5, module.tables[0].min)
        assertEquals(200, module.tables[0].max)
    }

    // --- Function types with multiple params and returns ---

    @Test
    fun `function type with no params and multiple results`() {
        val asm = WasmAssembler.create()
        asm.function("multi_ret", emptyList(),
            listOf(WasmValueType.I32, WasmValueType.I64), exported = true) { _, a ->
            a.i32Const(1)
            a.i64Const(2)
        }
        val module = WasmModuleReader.read(asm.assemble())

        val type = module.types[module.functions[0].typeIndex]
        assertTrue(type.params.isEmpty())
        assertEquals(listOf(WasmValueType.I32, WasmValueType.I64), type.results)
    }

    @Test
    fun `function types with four different param types`() {
        val asm = WasmAssembler.create()
        asm.function("all",
            listOf(WasmValueType.I32, WasmValueType.I64, WasmValueType.F32, WasmValueType.F64),
            listOf(WasmValueType.F64), exported = true) { fn, a ->
            a.localGet(fn.getParameter(3))
        }
        val module = roundTrip(asm.assemble())

        val type = module.types[module.functions[0].typeIndex]
        assertEquals(4, type.params.size)
        assertEquals(WasmValueType.I32, type.params[0])
        assertEquals(WasmValueType.I64, type.params[1])
        assertEquals(WasmValueType.F32, type.params[2])
        assertEquals(WasmValueType.F64, type.params[3])
        assertEquals(listOf(WasmValueType.F64), type.results)
    }

    @Test
    fun `multiple distinct function types are preserved`() {
        val asm = WasmAssembler.create()
        asm.function("f1", listOf(WasmValueType.I32), listOf(WasmValueType.I32)) { fn, a ->
            a.localGet(fn.getParameter(0))
        }
        asm.function("f2", listOf(WasmValueType.I64), listOf(WasmValueType.I64)) { fn, a ->
            a.localGet(fn.getParameter(0))
        }
        asm.function("f3", emptyList(), emptyList()) { _, _ -> }
        val module = roundTrip(asm.assemble())

        assertEquals(3, module.types.size)
    }

    // --- Import/export of functions, memories, tables, globals ---

    @Test
    fun `round-trip module with all import kinds`() {
        val asm = WasmAssembler.create()
        asm.importFunction("env", "fn", listOf(WasmValueType.I32), listOf(WasmValueType.I32))
        asm.importMemory("env", "mem", 1, 64)
        asm.importGlobal("env", "g", WasmValueType.I32, mutable = false)
        asm.importTable("env", "tbl", WasmRefType.FUNCREF, 0, 10)
        asm.function("stub", emptyList(), emptyList()) { _, _ -> }
        val module = roundTrip(asm.assemble())

        assertEquals(4, module.imports.size)
        assertTrue(module.imports[0] is WasmModule.Import.Func)
        assertTrue(module.imports[1] is WasmModule.Import.Memory)
        assertTrue(module.imports[2] is WasmModule.Import.Global)
        assertTrue(module.imports[3] is WasmModule.Import.Table)
    }

    @Test
    fun `round-trip module exporting all four kinds`() {
        val asm = WasmAssembler.create()
        asm.memory("memory", 1, exported = true)
        asm.global("global", WasmValueType.I32, mutable = true, initValue = 0, exported = true)
        asm.table("table", WasmRefType.FUNCREF, 1, exported = true)
        asm.function("func", emptyList(), emptyList(), exported = true) { _, _ -> }
        val module = roundTrip(asm.assemble())

        assertEquals(4, module.exports.size)
        val kinds = module.exports.map { it.kind }.toSet()
        assertEquals(setOf(
            WasmModule.ExportKind.MEMORY,
            WasmModule.ExportKind.GLOBAL,
            WasmModule.ExportKind.TABLE,
            WasmModule.ExportKind.FUNCTION
        ), kinds)
    }

    @Test
    fun `imports from multiple modules are preserved`() {
        val asm = WasmAssembler.create()
        asm.importFunction("env", "log", listOf(WasmValueType.I32), emptyList())
        asm.importFunction("wasi", "fd_write",
            listOf(WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32),
            listOf(WasmValueType.I32))
        asm.importFunction("runtime", "alloc", listOf(WasmValueType.I32), listOf(WasmValueType.I32))
        asm.function("main", emptyList(), emptyList()) { _, _ -> }
        val module = roundTrip(asm.assemble())

        assertEquals(3, module.importedFunctionCount)
        val modules = module.imports.map { it.module }.toSet()
        assertEquals(setOf("env", "wasi", "runtime"), modules)
    }

    // --- Data segments (active and passive) ---

    @Test
    fun `round-trip active data segment at non-zero offset`() {
        val asm = WasmAssembler.create()
        asm.memory("mem", 1)
        asm.dataSegment(0, 512, "data at offset 512".toByteArray())
        asm.function("f", emptyList(), emptyList()) { _, _ -> }
        val module = roundTrip(asm.assemble())

        assertEquals(1, module.dataSegments.size)
        val seg = module.dataSegments[0] as WasmModule.DataSegment.Active
        assertEquals(0, seg.memoryIndex)
        assertEquals("data at offset 512", String(seg.data))
    }

    @Test
    fun `round-trip mixed active and passive data segments`() {
        val asm = WasmAssembler.create()
        asm.memory("mem", 1)
        asm.dataSegment(0, 0, "active1".toByteArray())
        asm.dataSegment("passive1".toByteArray())
        asm.dataSegment(0, 100, "active2".toByteArray())
        asm.function("f", emptyList(), emptyList()) { _, _ -> }
        val module = roundTrip(asm.assemble())

        assertEquals(3, module.dataSegments.size)
        assertTrue(module.dataSegments[0] is WasmModule.DataSegment.Active)
        assertTrue(module.dataSegments[1] is WasmModule.DataSegment.Passive)
        assertTrue(module.dataSegments[2] is WasmModule.DataSegment.Active)
        assertEquals("active1", String((module.dataSegments[0] as WasmModule.DataSegment.Active).data))
        assertEquals("passive1", String((module.dataSegments[1] as WasmModule.DataSegment.Passive).data))
        assertEquals("active2", String((module.dataSegments[2] as WasmModule.DataSegment.Active).data))
    }

    @Test
    fun `round-trip data segment with binary content`() {
        val asm = WasmAssembler.create()
        asm.memory("mem", 1)
        val binaryData = ByteArray(256) { it.toByte() }
        asm.dataSegment(0, 0, binaryData)
        asm.function("f", emptyList(), emptyList()) { _, _ -> }
        val module = roundTrip(asm.assemble())

        val seg = module.dataSegments[0] as WasmModule.DataSegment.Active
        assertArrayEquals(binaryData, seg.data)
    }

    @Test
    fun `round-trip empty data segment`() {
        val asm = WasmAssembler.create()
        asm.memory("mem", 1)
        asm.dataSegment(0, 0, ByteArray(0))
        asm.function("f", emptyList(), emptyList()) { _, _ -> }
        val module = roundTrip(asm.assemble())

        assertEquals(1, module.dataSegments.size)
        val seg = module.dataSegments[0] as WasmModule.DataSegment.Active
        assertEquals(0, seg.data.size)
    }

    // --- Start function ---

    @Test
    fun `round-trip module with start function`() {
        val asm = WasmAssembler.create()
        asm.function("init", emptyList(), emptyList()) { _, a ->
            a.nop()
        }
        asm.function("main", emptyList(), emptyList(), exported = true) { _, a ->
            a.nop()
        }
        asm.startFunction("init")
        val module = roundTrip(asm.assemble())

        assertNotNull(module.start)
        assertEquals(0, module.start)
    }

    @Test
    fun `module without start function has null start`() {
        val asm = WasmAssembler.create()
        asm.function("f", emptyList(), emptyList()) { _, _ -> }
        val module = roundTrip(asm.assemble())

        assertNull(module.start)
    }

    // --- Custom sections ---

    @Test
    fun `round-trip module with custom section via direct construction`() {
        val customData = "custom payload".toByteArray()
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
            customSections = listOf(WasmModule.CustomSection("my_section", customData)),
        )

        val bytes = WasmModuleWriter.write(module)
        assertValidWasm(bytes)
        val module2 = WasmModuleReader.read(bytes)

        assertEquals(1, module2.customSections.size)
        assertEquals("my_section", module2.customSections[0].name)
        assertArrayEquals(customData, module2.customSections[0].data)
    }

    @Test
    fun `round-trip module with multiple custom sections`() {
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
            customSections = listOf(
                WasmModule.CustomSection("section_a", "aaa".toByteArray()),
                WasmModule.CustomSection("section_b", "bbb".toByteArray()),
            ),
        )

        val bytes = WasmModuleWriter.write(module)
        val module2 = WasmModuleReader.read(bytes)

        assertEquals(2, module2.customSections.size)
        assertEquals("section_a", module2.customSections[0].name)
        assertEquals("section_b", module2.customSections[1].name)
    }

    // --- Edge cases ---

    @Test
    fun `empty module round-trips to 8 bytes`() {
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
        assertEquals(8, bytes.size)

        val module2 = WasmModuleReader.read(bytes)
        assertEquals(1, module2.version)
        assertTrue(module2.functions.isEmpty())
        assertTrue(module2.imports.isEmpty())
        assertTrue(module2.exports.isEmpty())
        assertTrue(module2.memories.isEmpty())
        assertTrue(module2.globals.isEmpty())
        assertTrue(module2.tables.isEmpty())
        assertNull(module2.start)
    }

    @Test
    fun `module with only imports and no defined functions round-trips`() {
        val asm = WasmAssembler.create()
        asm.importFunction("env", "a", listOf(WasmValueType.I32), emptyList())
        asm.importFunction("env", "b", emptyList(), listOf(WasmValueType.I32))
        asm.importMemory("env", "memory", 1)
        asm.importGlobal("env", "stack", WasmValueType.I32, mutable = true)
        asm.function("stub", emptyList(), emptyList()) { _, _ -> }
        val module = roundTrip(asm.assemble())

        assertEquals(4, module.imports.size)
        assertEquals(2, module.importedFunctionCount)
        assertEquals(1, module.importedMemoryCount)
        assertEquals(1, module.importedGlobalCount)
    }

    @Test
    fun `module with 100 functions round-trips preserving order`() {
        val asm = WasmAssembler.create()
        for (i in 0 until 100) {
            asm.function("fn_$i", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { fn, a ->
                a.localGet(fn.getParameter(0))
                a.i32Const(i)
                a.i32Add()
            }
        }
        val module = roundTrip(asm.assemble())

        assertEquals(100, module.functions.size)
        assertEquals(100, module.exports.size)
        for (i in 0 until 100) {
            assertEquals("fn_$i", module.exports[i].name)
        }
    }

    // --- Complex combined modules ---

    @Test
    fun `round-trip complex module with imports, globals, memory, data, and start`() {
        val asm = WasmAssembler.create()
        asm.importFunction("env", "print", listOf(WasmValueType.I32), emptyList())
        asm.memory("mem", 2, 256, exported = true)
        asm.global("counter", WasmValueType.I32, mutable = true, initValue = 0, exported = true)
        asm.global("limit", WasmValueType.I32, mutable = false, initValue = 100, exported = true)
        asm.table("tbl", WasmRefType.FUNCREF, 4, 64)
        asm.dataSegment(0, 0, "initialized data".toByteArray())
        asm.dataSegment(0, 256, byteArrayOf(0xFF.toByte(), 0x00, 0x01, 0x02))

        asm.function("init", emptyList(), emptyList()) { _, a ->
            a.nop()
        }
        asm.function("inc", emptyList(), emptyList(), exported = true) { _, a ->
            a.globalGet(0)
            a.i32Const(1)
            a.i32Add()
            a.globalSet(0)
        }
        asm.function("get", emptyList(), listOf(WasmValueType.I32), exported = true) { _, a ->
            a.globalGet(0)
        }
        asm.startFunction("init")

        val module = roundTrip(asm.assemble())

        assertEquals(1, module.importedFunctionCount)
        assertEquals(1, module.memories.size)
        assertEquals(2, module.globals.size)
        assertEquals(1, module.tables.size)
        assertEquals(2, module.dataSegments.size)
        assertEquals(3, module.functions.size)
        assertNotNull(module.start)
    }

    @Test
    fun `round-trip preserves function with many locals of mixed types`() {
        val asm = WasmAssembler.create()
        asm.function("lots_of_locals", emptyList(), listOf(WasmValueType.I32), exported = true) { _, a ->
            a.declareLocal(WasmValueType.I32)
            a.declareLocal(WasmValueType.I32)
            a.declareLocal(WasmValueType.I32)
            a.declareLocal(WasmValueType.I64)
            a.declareLocal(WasmValueType.I64)
            a.declareLocal(WasmValueType.F32)
            a.declareLocal(WasmValueType.F64)
            a.declareLocal(WasmValueType.F64)
            a.declareLocal(WasmValueType.F64)
            a.i32Const(42)
        }
        val module = roundTrip(asm.assemble())

        assertEquals(
            listOf(
                WasmValueType.I32, WasmValueType.I32, WasmValueType.I32,
                WasmValueType.I64, WasmValueType.I64,
                WasmValueType.F32,
                WasmValueType.F64, WasmValueType.F64, WasmValueType.F64
            ),
            module.functions[0].locals
        )
    }

    @Test
    fun `functionName resolves across imports and defined functions`() {
        val asm = WasmAssembler.create()
        asm.importFunction("env", "imp0", emptyList(), emptyList())
        asm.importFunction("env", "imp1", emptyList(), emptyList())
        asm.function("def0", emptyList(), emptyList(), exported = true) { _, _ -> }
        asm.function("def1", emptyList(), emptyList(), exported = true) { _, _ -> }
        val module = WasmModuleReader.read(asm.assemble())

        assertEquals("imp0", module.functionName(0))
        assertEquals("imp1", module.functionName(1))
        assertEquals("def0", module.functionName(2))
        assertEquals("def1", module.functionName(3))
        assertNull(module.functionName(4))
    }

    @Test
    fun `double round-trip with complex module produces identical bytes`() {
        val asm = WasmAssembler.create()
        asm.importFunction("env", "log", listOf(WasmValueType.I32), emptyList())
        asm.memory("mem", 1, 10, exported = true)
        asm.global("g", WasmValueType.I32, mutable = true, initValue = 0)
        asm.dataSegment(0, 0, "test data".toByteArray())
        asm.function("add", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.i32Add()
        }
        asm.function("sub", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.i32Sub()
        }
        val original = asm.assemble()

        val pass1 = WasmModuleWriter.write(WasmModuleReader.read(original))
        val pass2 = WasmModuleWriter.write(WasmModuleReader.read(pass1))

        assertArrayEquals(pass1, pass2)
    }

    @Test
    fun `round-trip preserves memory with no max limit`() {
        val asm = WasmAssembler.create()
        asm.memory("mem", 16)
        asm.function("f", emptyList(), emptyList()) { _, _ -> }
        val module = roundTrip(asm.assemble())

        assertEquals(16, module.memories[0].min)
        assertNull(module.memories[0].max)
    }

    @Test
    fun `round-trip preserves table with no max limit`() {
        val asm = WasmAssembler.create()
        asm.table("tbl", WasmRefType.FUNCREF, 10)
        asm.function("f", emptyList(), emptyList()) { _, _ -> }
        val module = roundTrip(asm.assemble())

        assertEquals(10, module.tables[0].min)
        assertNull(module.tables[0].max)
    }

    @Test
    fun `round-trip preserves export names with special characters`() {
        val asm = WasmAssembler.create()
        asm.function("my-func_123", emptyList(), emptyList(), exported = true) { _, _ -> }
        val module = roundTrip(asm.assemble())

        assertEquals("my-func_123", module.exports[0].name)
    }

    @Test
    fun `round-trip preserves function body with control flow`() {
        val asm = WasmAssembler.create()
        asm.function("ctrl", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.i32Const(0)
            a.i32GtS()
            a.if_(0x7F) // i32
            a.localGet(fn.getParameter(0))
            a.i32Const(2)
            a.i32Mul()
            a.else_()
            a.i32Const(0)
            a.end()
        }
        val original = asm.assemble()
        val module1 = WasmModuleReader.read(original)
        val module2 = roundTrip(original)

        assertArrayEquals(module1.functions[0].body, module2.functions[0].body)
    }

    @Test
    fun `round-trip preserves multiple passive data segments`() {
        val asm = WasmAssembler.create()
        asm.memory("mem", 1)
        asm.dataSegment("segment one".toByteArray())
        asm.dataSegment("segment two".toByteArray())
        asm.dataSegment("segment three".toByteArray())
        asm.function("f", emptyList(), emptyList()) { _, _ -> }
        val module = roundTrip(asm.assemble())

        assertEquals(3, module.dataSegments.size)
        for (seg in module.dataSegments) {
            assertTrue(seg is WasmModule.DataSegment.Passive)
        }
        assertEquals("segment one", String((module.dataSegments[0] as WasmModule.DataSegment.Passive).data))
        assertEquals("segment two", String((module.dataSegments[1] as WasmModule.DataSegment.Passive).data))
        assertEquals("segment three", String((module.dataSegments[2] as WasmModule.DataSegment.Passive).data))
    }

    @Test
    fun `round-trip preserves import memory with no max`() {
        val asm = WasmAssembler.create()
        asm.importMemory("env", "memory", 1)
        asm.function("f", emptyList(), emptyList()) { _, _ -> }
        val module = roundTrip(asm.assemble())

        val imp = module.imports[0] as WasmModule.Import.Memory
        assertEquals(1, imp.min)
        assertNull(imp.max)
    }

    @Test
    fun `round-trip preserves import table with no max`() {
        val asm = WasmAssembler.create()
        asm.importTable("env", "tbl", WasmRefType.FUNCREF, 0)
        asm.function("f", emptyList(), emptyList()) { _, _ -> }
        val module = roundTrip(asm.assemble())

        val imp = module.imports[0] as WasmModule.Import.Table
        assertEquals(0, imp.min)
        assertNull(imp.max)
    }

    @Test
    fun `imported function count is correct with mixed imports`() {
        val asm = WasmAssembler.create()
        asm.importFunction("env", "f1", emptyList(), emptyList())
        asm.importMemory("env", "mem", 1)
        asm.importFunction("env", "f2", listOf(WasmValueType.I32), emptyList())
        asm.importGlobal("env", "g", WasmValueType.I32, mutable = false)
        asm.importFunction("env", "f3", emptyList(), listOf(WasmValueType.I32))
        asm.function("stub", emptyList(), emptyList()) { _, _ -> }
        val module = roundTrip(asm.assemble())

        assertEquals(5, module.imports.size)
        assertEquals(3, module.importedFunctionCount)
        assertEquals(1, module.importedMemoryCount)
        assertEquals(1, module.importedGlobalCount)
        assertEquals(0, module.importedTableCount)
    }

    @Test
    fun `round-trip large data segment preserves content`() {
        val asm = WasmAssembler.create()
        asm.memory("mem", 2)
        val largeData = ByteArray(8192) { (it % 256).toByte() }
        asm.dataSegment(0, 0, largeData)
        asm.function("f", emptyList(), emptyList()) { _, _ -> }
        val module = roundTrip(asm.assemble())

        val seg = module.dataSegments[0] as WasmModule.DataSegment.Active
        assertArrayEquals(largeData, seg.data)
    }
}
