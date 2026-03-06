package org.kgen.wasm.module

import org.kgen.wasm.*
import org.kgen.wasm.asm.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertThrows

class WasmModuleReaderTest {

    private fun assembleAddModule(): ByteArray {
        val asm = WasmAssembler.create()
        asm.function("add", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.i32Add()
        }
        return asm.assemble()
    }

    @Test
    fun `reads WASM magic and version`() {
        val module = WasmModuleReader.read(assembleAddModule())
        assertEquals(1, module.version)
    }

    @Test
    fun `reads type section`() {
        val module = WasmModuleReader.read(assembleAddModule())
        assertEquals(1, module.types.size)
        assertEquals(listOf(WasmValueType.I32, WasmValueType.I32), module.types[0].params)
        assertEquals(listOf(WasmValueType.I32), module.types[0].results)
    }

    @Test
    fun `reads function section`() {
        val module = WasmModuleReader.read(assembleAddModule())
        assertEquals(1, module.functions.size)
        assertEquals(0, module.functions[0].typeIndex)
    }

    @Test
    fun `reads export section`() {
        val module = WasmModuleReader.read(assembleAddModule())
        assertEquals(1, module.exports.size)
        assertEquals("add", module.exports[0].name)
        assertEquals(WasmModule.ExportKind.FUNCTION, module.exports[0].kind)
        assertEquals(0, module.exports[0].index)
    }

    @Test
    fun `reads function body`() {
        val module = WasmModuleReader.read(assembleAddModule())
        val body = module.functions[0].body
        assertTrue(body.isNotEmpty())
        // Last byte should be 0x0B (end)
        assertEquals(0x0B, body.last().toInt() and 0xFF)
    }

    @Test
    fun `resolves function name from export`() {
        val module = WasmModuleReader.read(assembleAddModule())
        assertEquals("add", module.functions[0].name)
    }

    @Test
    fun `reads imports`() {
        val asm = WasmAssembler.create()
        asm.importFunction("env", "log", listOf(WasmValueType.I32), emptyList())
        asm.function("main", emptyList(), emptyList()) { _, a ->
            a.i32Const(42)
            a.call("log")
        }
        val module = WasmModuleReader.read(asm.assemble())

        assertEquals(1, module.imports.size)
        val imp = module.imports[0] as WasmModule.Import.Func
        assertEquals("env", imp.module)
        assertEquals("log", imp.name)
        assertEquals(1, module.importedFunctionCount)
    }

    @Test
    fun `reads memory`() {
        val asm = WasmAssembler.create()
        asm.memory("mem", 1, 10)
        asm.function("f", emptyList(), emptyList()) { _, _ -> }
        val module = WasmModuleReader.read(asm.assemble())

        assertEquals(1, module.memories.size)
        assertEquals(1, module.memories[0].min)
        assertEquals(10, module.memories[0].max)
    }

    @Test
    fun `reads globals`() {
        val asm = WasmAssembler.create()
        asm.global("g", WasmValueType.I32, mutable = true, initValue = 42)
        asm.function("f", emptyList(), emptyList()) { _, _ -> }
        val module = WasmModuleReader.read(asm.assemble())

        assertEquals(1, module.globals.size)
        assertEquals(WasmValueType.I32, module.globals[0].type)
        assertTrue(module.globals[0].mutable)
    }

    @Test
    fun `reads data segments`() {
        val asm = WasmAssembler.create()
        asm.memory("mem", 1)
        asm.dataSegment(0, 0, "hello".toByteArray())
        asm.function("f", emptyList(), emptyList()) { _, _ -> }
        val module = WasmModuleReader.read(asm.assemble())

        assertEquals(1, module.dataSegments.size)
        val seg = module.dataSegments[0] as WasmModule.DataSegment.Active
        assertEquals(0, seg.memoryIndex)
        assertEquals("hello", String(seg.data))
    }

    @Test
    fun `reads multi-function module`() {
        val asm = WasmAssembler.create()
        asm.function("square", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(0))
            a.i32Mul()
        }
        asm.function("double", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(0))
            a.i32Add()
        }
        val module = WasmModuleReader.read(asm.assemble())

        assertEquals(2, module.functions.size)
        assertEquals(2, module.exports.size)
        assertEquals("square", module.exports[0].name)
        assertEquals("double", module.exports[1].name)
    }

    @Test
    fun `reads module with locals`() {
        val asm = WasmAssembler.create()
        asm.function("compute", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { fn, a ->
            val tmp = a.declareLocal("tmp", WasmValueType.I32)
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(0))
            a.i32Add()
            a.localSet(tmp)
            a.localGet(tmp)
        }
        val module = WasmModuleReader.read(asm.assemble())

        assertEquals(1, module.functions.size)
        assertEquals(listOf(WasmValueType.I32), module.functions[0].locals)
    }

    @Test
    fun `reads table section`() {
        val asm = WasmAssembler.create()
        asm.table("tbl", WasmRefType.FUNCREF, 10, 100)
        asm.function("f", emptyList(), emptyList()) { _, _ -> }
        val module = WasmModuleReader.read(asm.assemble())

        assertEquals(1, module.tables.size)
        assertEquals(WasmRefType.FUNCREF, module.tables[0].refType)
        assertEquals(10, module.tables[0].min)
        assertEquals(100, module.tables[0].max)
    }

    @Test
    fun `reads import global`() {
        val asm = WasmAssembler.create()
        asm.importGlobal("env", "stack_ptr", WasmValueType.I32, mutable = true)
        asm.function("f", emptyList(), emptyList()) { _, _ -> }
        val module = WasmModuleReader.read(asm.assemble())

        assertEquals(1, module.imports.size)
        val imp = module.imports[0] as WasmModule.Import.Global
        assertEquals("env", imp.module)
        assertEquals("stack_ptr", imp.name)
        assertEquals(WasmValueType.I32, imp.type)
        assertTrue(imp.mutable)
    }

    @Test
    fun `reads import memory`() {
        val asm = WasmAssembler.create()
        asm.importMemory("env", "memory", 1, 16)
        asm.function("f", emptyList(), emptyList()) { _, _ -> }
        val module = WasmModuleReader.read(asm.assemble())

        val imp = module.imports[0] as WasmModule.Import.Memory
        assertEquals(1, imp.min)
        assertEquals(16, imp.max)
    }

    @Test
    fun `functionName resolves imports and defined functions`() {
        val asm = WasmAssembler.create()
        asm.importFunction("env", "log", listOf(WasmValueType.I32), emptyList())
        asm.function("main", emptyList(), emptyList(), exported = true) { _, a ->
            a.i32Const(1)
            a.call("log")
        }
        val module = WasmModuleReader.read(asm.assemble())

        assertEquals("log", module.functionName(0))
        assertEquals("main", module.functionName(1))
        assertNull(module.functionName(99))
    }

    // --- Error handling ---

    @Test
    fun `rejects invalid magic number`() {
        val bad = byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00)
        assertThrows<IllegalStateException> { WasmModuleReader.read(bad) }
    }

    @Test
    fun `rejects unsupported version`() {
        val bad = byteArrayOf(0x00, 0x61, 0x73, 0x6D, 0x02, 0x00, 0x00, 0x00)
        assertThrows<IllegalStateException> { WasmModuleReader.read(bad) }
    }

    @Test
    fun `rejects truncated file`() {
        assertThrows<Exception> { WasmModuleReader.read(byteArrayOf(0x00, 0x61)) }
    }

    // --- Edge cases ---

    @Test
    fun `reads minimal module with no functions`() {
        // Just magic + version, no sections
        val bytes = byteArrayOf(0x00, 0x61, 0x73, 0x6D, 0x01, 0x00, 0x00, 0x00)
        val module = WasmModuleReader.read(bytes)
        assertEquals(1, module.version)
        assertTrue(module.functions.isEmpty())
        assertTrue(module.imports.isEmpty())
        assertTrue(module.exports.isEmpty())
    }

    @Test
    fun `reads module with only imports and no defined functions`() {
        val asm = WasmAssembler.create()
        asm.importFunction("env", "a", listOf(WasmValueType.I32), emptyList())
        asm.importFunction("env", "b", listOf(WasmValueType.I64), listOf(WasmValueType.I64))
        asm.importMemory("env", "memory", 1)
        // Need at least one function for valid module (assembler requirement)
        asm.function("stub", emptyList(), emptyList()) { _, _ -> }
        val module = WasmModuleReader.read(asm.assemble())

        assertEquals(3, module.imports.size)
        assertEquals(2, module.importedFunctionCount)
        assertEquals(1, module.importedMemoryCount)
    }

    @Test
    fun `reads memory with no max`() {
        val asm = WasmAssembler.create()
        asm.memory("mem", 1)
        asm.function("f", emptyList(), emptyList()) { _, _ -> }
        val module = WasmModuleReader.read(asm.assemble())

        assertEquals(1, module.memories[0].min)
        assertNull(module.memories[0].max)
    }

    @Test
    fun `reads table with no max`() {
        val asm = WasmAssembler.create()
        asm.table("tbl", WasmRefType.FUNCREF, 0)
        asm.function("f", emptyList(), emptyList()) { _, _ -> }
        val module = WasmModuleReader.read(asm.assemble())

        assertEquals(0, module.tables[0].min)
        assertNull(module.tables[0].max)
    }

    @Test
    fun `reads import table`() {
        val asm = WasmAssembler.create()
        asm.importTable("env", "tbl", WasmRefType.FUNCREF, 0, 100)
        asm.function("f", emptyList(), emptyList()) { _, _ -> }
        val module = WasmModuleReader.read(asm.assemble())

        val imp = module.imports[0] as WasmModule.Import.Table
        assertEquals("env", imp.module)
        assertEquals("tbl", imp.name)
        assertEquals(WasmRefType.FUNCREF, imp.refType)
        assertEquals(0, imp.min)
        assertEquals(100, imp.max)
    }

    @Test
    fun `reads immutable global`() {
        val asm = WasmAssembler.create()
        asm.global("pi_approx", WasmValueType.I32, mutable = false, initValue = 3)
        asm.function("f", emptyList(), emptyList()) { _, _ -> }
        val module = WasmModuleReader.read(asm.assemble())

        assertFalse(module.globals[0].mutable)
    }

    @Test
    fun `reads multiple data segments`() {
        val asm = WasmAssembler.create()
        asm.memory("mem", 1)
        asm.dataSegment(0, 0, "hello".toByteArray())
        asm.dataSegment(0, 100, "world".toByteArray())
        asm.function("f", emptyList(), emptyList()) { _, _ -> }
        val module = WasmModuleReader.read(asm.assemble())

        assertEquals(2, module.dataSegments.size)
        assertEquals("hello", String((module.dataSegments[0] as WasmModule.DataSegment.Active).data))
        assertEquals("world", String((module.dataSegments[1] as WasmModule.DataSegment.Active).data))
    }

    @Test
    fun `reads passive data segment`() {
        val asm = WasmAssembler.create()
        asm.memory("mem", 1)
        asm.dataSegment("passive data".toByteArray())
        asm.function("f", emptyList(), emptyList()) { _, _ -> }
        val module = WasmModuleReader.read(asm.assemble())

        assertEquals(1, module.dataSegments.size)
        val seg = module.dataSegments[0] as WasmModule.DataSegment.Passive
        assertEquals("passive data", String(seg.data))
    }

    @Test
    fun `reads all value types in function signature`() {
        val asm = WasmAssembler.create()
        asm.function("all_types",
            listOf(WasmValueType.I32, WasmValueType.I64, WasmValueType.F32, WasmValueType.F64),
            listOf(WasmValueType.I32), exported = true) { fn, a ->
            a.localGet(fn.getParameter(0))
        }
        val module = WasmModuleReader.read(asm.assemble())

        val type = module.types[0]
        assertEquals(listOf(WasmValueType.I32, WasmValueType.I64, WasmValueType.F32, WasmValueType.F64), type.params)
        assertEquals(listOf(WasmValueType.I32), type.results)
    }

    @Test
    fun `reads function with multiple local types`() {
        val asm = WasmAssembler.create()
        asm.function("multi_locals", emptyList(), emptyList()) { _, a ->
            a.declareLocal(WasmValueType.I32)
            a.declareLocal(WasmValueType.I32)
            a.declareLocal(WasmValueType.I64)
            a.declareLocal(WasmValueType.F32)
            a.declareLocal(WasmValueType.F64)
        }
        val module = WasmModuleReader.read(asm.assemble())

        assertEquals(
            listOf(WasmValueType.I32, WasmValueType.I32, WasmValueType.I64, WasmValueType.F32, WasmValueType.F64),
            module.functions[0].locals
        )
    }

    @Test
    fun `reads export kinds correctly`() {
        val asm = WasmAssembler.create()
        asm.memory("mem", 1, exported = true)
        asm.global("g", WasmValueType.I32, mutable = false, initValue = 0, exported = true)
        asm.table("tbl", WasmRefType.FUNCREF, 1, exported = true)
        asm.function("f", emptyList(), emptyList(), exported = true) { _, _ -> }
        val module = WasmModuleReader.read(asm.assemble())

        val kinds = module.exports.map { it.kind }.toSet()
        assertTrue(WasmModule.ExportKind.FUNCTION in kinds)
        assertTrue(WasmModule.ExportKind.MEMORY in kinds)
        assertTrue(WasmModule.ExportKind.GLOBAL in kinds)
        assertTrue(WasmModule.ExportKind.TABLE in kinds)
    }

    @Test
    fun `type deduplication round-trips`() {
        val asm = WasmAssembler.create()
        // Two functions with identical signatures should share a type index
        asm.function("a", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { fn, a ->
            a.localGet(fn.getParameter(0))
        }
        asm.function("b", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { fn, a ->
            a.localGet(fn.getParameter(0))
        }
        val module = WasmModuleReader.read(asm.assemble())

        assertEquals(1, module.types.size) // deduplicated
        assertEquals(0, module.functions[0].typeIndex)
        assertEquals(0, module.functions[1].typeIndex)
    }

    @Test
    fun `reads many functions`() {
        val asm = WasmAssembler.create()
        for (i in 0 until 50) {
            asm.function("fn_$i", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { fn, a ->
                a.localGet(fn.getParameter(0))
                a.i32Const(i)
                a.i32Add()
            }
        }
        val module = WasmModuleReader.read(asm.assemble())

        assertEquals(50, module.functions.size)
        assertEquals(50, module.exports.size)
        assertEquals("fn_0", module.exports[0].name)
        assertEquals("fn_49", module.exports[49].name)
    }

    @Test
    fun `reads large data segment`() {
        val asm = WasmAssembler.create()
        asm.memory("mem", 1)
        val largeData = ByteArray(4096) { (it % 256).toByte() }
        asm.dataSegment(0, 0, largeData)
        asm.function("f", emptyList(), emptyList()) { _, _ -> }
        val module = WasmModuleReader.read(asm.assemble())

        val seg = module.dataSegments[0] as WasmModule.DataSegment.Active
        assertArrayEquals(largeData, seg.data)
    }

    @Test
    fun `reads mixed imports from different modules`() {
        val asm = WasmAssembler.create()
        asm.importFunction("env", "log", listOf(WasmValueType.I32), emptyList())
        asm.importFunction("wasi_snapshot_preview1", "fd_write",
            listOf(WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32),
            listOf(WasmValueType.I32))
        asm.importMemory("env", "memory", 1)
        asm.importGlobal("env", "stack_pointer", WasmValueType.I32, mutable = true)
        asm.function("f", emptyList(), emptyList()) { _, _ -> }
        val module = WasmModuleReader.read(asm.assemble())

        assertEquals(4, module.imports.size)
        assertEquals(2, module.importedFunctionCount)
        assertEquals(1, module.importedMemoryCount)
        assertEquals(1, module.importedGlobalCount)

        val funcImport = module.imports[1] as WasmModule.Import.Func
        assertEquals("wasi_snapshot_preview1", funcImport.module)
        assertEquals("fd_write", funcImport.name)
    }

    @Test
    fun `reads void function`() {
        val asm = WasmAssembler.create()
        asm.function("noop", emptyList(), emptyList(), exported = true) { _, _ -> }
        val module = WasmModuleReader.read(asm.assemble())

        val type = module.types[module.functions[0].typeIndex]
        assertTrue(type.params.isEmpty())
        assertTrue(type.results.isEmpty())
    }

    @Test
    fun `reads function returning multiple results via type`() {
        val asm = WasmAssembler.create()
        asm.function("swap",
            listOf(WasmValueType.I32, WasmValueType.I32),
            listOf(WasmValueType.I32, WasmValueType.I32), exported = true) { fn, a ->
            a.localGet(fn.getParameter(1))
            a.localGet(fn.getParameter(0))
        }
        val module = WasmModuleReader.read(asm.assemble())

        val type = module.types[module.functions[0].typeIndex]
        assertEquals(2, type.results.size)
    }
}
