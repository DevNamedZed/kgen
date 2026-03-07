package org.kgen.backend.wasm.module

import org.kgen.backend.wasm.*
import org.kgen.backend.wasm.asm.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class WasmModuleRoundTripTest {

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

    @Test
    fun `round-trip empty module`() {
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
        val module2 = WasmModuleReader.read(bytes)

        assertEquals(1, module2.version)
        assertTrue(module2.functions.isEmpty())
        assertTrue(module2.imports.isEmpty())
        assertTrue(module2.exports.isEmpty())
        assertTrue(module2.memories.isEmpty())
        assertTrue(module2.globals.isEmpty())
        assertTrue(module2.tables.isEmpty())
    }

    @Test
    fun `round-trip module with only memory`() {
        val asm = WasmAssembler.create()
        asm.memory("mem", 2, 64, exported = true)
        asm.function("f", emptyList(), emptyList()) { _, _ -> }
        val module2 = roundTrip(asm.assemble())

        assertEquals(1, module2.memories.size)
        assertEquals(2, module2.memories[0].min)
        assertEquals(64, module2.memories[0].max)
        assertTrue(module2.exports.any { it.kind == WasmModule.ExportKind.MEMORY })
    }

    @Test
    fun `round-trip preserves multiple functions`() {
        val asm = WasmAssembler.create()
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
        asm.function("mul", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.i32Mul()
        }
        val module2 = roundTrip(asm.assemble())

        assertEquals(3, module2.functions.size)
        assertEquals(3, module2.exports.size)
        assertEquals("add", module2.exports[0].name)
        assertEquals("sub", module2.exports[1].name)
        assertEquals("mul", module2.exports[2].name)

        for (fn in module2.functions) {
            assertTrue(fn.body.isNotEmpty())
            assertEquals(0x0B, fn.body.last().toInt() and 0xFF)
        }
    }

    @Test
    fun `round-trip preserves imports and exports`() {
        val asm = WasmAssembler.create()
        asm.importFunction("env", "log", listOf(WasmValueType.I32), emptyList())
        asm.importFunction("env", "read", emptyList(), listOf(WasmValueType.I32))
        asm.importMemory("env", "memory", 1, 256)
        asm.importGlobal("env", "stack_ptr", WasmValueType.I32, mutable = true)
        asm.function("main", emptyList(), emptyList(), exported = true) { _, a ->
            a.i32Const(42)
            a.call("log")
        }
        val module2 = roundTrip(asm.assemble())

        assertEquals(4, module2.imports.size)

        val funcImport0 = module2.imports[0] as WasmModule.Import.Func
        assertEquals("env", funcImport0.module)
        assertEquals("log", funcImport0.name)

        val funcImport1 = module2.imports[1] as WasmModule.Import.Func
        assertEquals("env", funcImport1.module)
        assertEquals("read", funcImport1.name)

        val memImport = module2.imports[2] as WasmModule.Import.Memory
        assertEquals(1, memImport.min)
        assertEquals(256, memImport.max)

        val globalImport = module2.imports[3] as WasmModule.Import.Global
        assertEquals("stack_ptr", globalImport.name)
        assertEquals(WasmValueType.I32, globalImport.type)
        assertTrue(globalImport.mutable)

        assertEquals(1, module2.exports.size)
        assertEquals("main", module2.exports[0].name)
        assertEquals(WasmModule.ExportKind.FUNCTION, module2.exports[0].kind)
    }

    @Test
    fun `round-trip preserves memory and globals`() {
        val asm = WasmAssembler.create()
        asm.memory("mem", 1, 100, exported = true)
        asm.global("counter", WasmValueType.I32, mutable = true, initValue = 0, exported = true)
        asm.global("limit", WasmValueType.I32, mutable = false, initValue = 1000, exported = true)
        asm.function("inc", emptyList(), emptyList(), exported = true) { _, a ->
            a.globalGet(0)
            a.i32Const(1)
            a.i32Add()
            a.globalSet(0)
        }
        val module2 = roundTrip(asm.assemble())

        assertEquals(1, module2.memories.size)
        assertEquals(1, module2.memories[0].min)
        assertEquals(100, module2.memories[0].max)

        assertEquals(2, module2.globals.size)
        assertTrue(module2.globals[0].mutable)
        assertFalse(module2.globals[1].mutable)
        assertEquals(WasmValueType.I32, module2.globals[0].type)
        assertEquals(WasmValueType.I32, module2.globals[1].type)
    }

    @Test
    fun `round-trip preserves start function`() {
        val asm = WasmAssembler.create()
        asm.function("init", emptyList(), emptyList()) { _, a ->
            a.nop()
        }
        asm.function("main", emptyList(), emptyList(), exported = true) { _, a ->
            a.nop()
        }
        asm.startFunction("init")
        val original = asm.assemble()

        val module1 = WasmModuleReader.read(original)
        assertNotNull(module1.start)

        val module2 = roundTrip(original)
        assertNotNull(module2.start)
        assertEquals(module1.start, module2.start)
    }

    @Test
    fun `round-trip preserves function body bytes exactly`() {
        val asm = WasmAssembler.create()
        asm.function("complex", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { fn, a ->
            val tmp = a.declareLocal("tmp", WasmValueType.I32)
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.i32Add()
            a.localTee(tmp)
            a.i32Const(100)
            a.i32GtS()
            a.if_(0x7F)
            a.i32Const(100)
            a.else_()
            a.localGet(tmp)
            a.end()
        }
        val original = asm.assemble()
        val module1 = WasmModuleReader.read(original)
        val module2 = roundTrip(original)

        assertArrayEquals(module1.functions[0].body, module2.functions[0].body)
    }

    @Test
    fun `round-trip preserves locals`() {
        val asm = WasmAssembler.create()
        asm.function("multi_locals", emptyList(), listOf(WasmValueType.I64), exported = true) { _, a ->
            a.declareLocal(WasmValueType.I32)
            a.declareLocal(WasmValueType.I32)
            a.declareLocal(WasmValueType.I64)
            a.declareLocal(WasmValueType.F32)
            a.declareLocal(WasmValueType.F64)
            a.i64Const(0)
        }
        val module2 = roundTrip(asm.assemble())

        assertEquals(
            listOf(WasmValueType.I32, WasmValueType.I32, WasmValueType.I64, WasmValueType.F32, WasmValueType.F64),
            module2.functions[0].locals
        )
    }

    @Test
    fun `round-trip preserves type deduplication`() {
        val asm = WasmAssembler.create()
        asm.function("a", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { fn, a ->
            a.localGet(fn.getParameter(0))
        }
        asm.function("b", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.i32Const(1)
            a.i32Add()
        }
        val module2 = roundTrip(asm.assemble())

        assertEquals(1, module2.types.size)
        assertEquals(module2.functions[0].typeIndex, module2.functions[1].typeIndex)
    }

    @Test
    fun `round-trip preserves tables`() {
        val asm = WasmAssembler.create()
        asm.table("tbl", WasmRefType.FUNCREF, 5, 50, exported = true)
        asm.function("f", emptyList(), emptyList()) { _, _ -> }
        val module2 = roundTrip(asm.assemble())

        assertEquals(1, module2.tables.size)
        assertEquals(WasmRefType.FUNCREF, module2.tables[0].refType)
        assertEquals(5, module2.tables[0].min)
        assertEquals(50, module2.tables[0].max)
    }

    @Test
    fun `round-trip preserves data segments`() {
        val asm = WasmAssembler.create()
        asm.memory("mem", 1)
        asm.dataSegment(0, 0, "hello world".toByteArray())
        asm.dataSegment(0, 256, byteArrayOf(0x01, 0x02, 0x03, 0x04))
        asm.function("f", emptyList(), emptyList()) { _, _ -> }
        val module2 = roundTrip(asm.assemble())

        assertEquals(2, module2.dataSegments.size)
        val seg0 = module2.dataSegments[0] as WasmModule.DataSegment.Active
        val seg1 = module2.dataSegments[1] as WasmModule.DataSegment.Active
        assertEquals("hello world", String(seg0.data))
        assertArrayEquals(byteArrayOf(0x01, 0x02, 0x03, 0x04), seg1.data)
    }

    @Test
    fun `round-trip preserves passive data segments`() {
        val asm = WasmAssembler.create()
        asm.memory("mem", 1)
        asm.dataSegment("passive content".toByteArray())
        asm.function("f", emptyList(), emptyList()) { _, _ -> }
        val module2 = roundTrip(asm.assemble())

        assertEquals(1, module2.dataSegments.size)
        val seg = module2.dataSegments[0] as WasmModule.DataSegment.Passive
        assertEquals("passive content", String(seg.data))
    }

    @Test
    fun `round-trip preserves import table`() {
        val asm = WasmAssembler.create()
        asm.importTable("env", "tbl", WasmRefType.FUNCREF, 0, 100)
        asm.function("f", emptyList(), emptyList()) { _, _ -> }
        val module2 = roundTrip(asm.assemble())

        assertEquals(1, module2.imports.size)
        val imp = module2.imports[0] as WasmModule.Import.Table
        assertEquals("env", imp.module)
        assertEquals("tbl", imp.name)
        assertEquals(WasmRefType.FUNCREF, imp.refType)
        assertEquals(0, imp.min)
        assertEquals(100, imp.max)
    }

    @Test
    fun `round-trip preserves all export kinds`() {
        val asm = WasmAssembler.create()
        asm.memory("mem", 1, exported = true)
        asm.global("g", WasmValueType.I32, mutable = false, initValue = 42, exported = true)
        asm.table("tbl", WasmRefType.FUNCREF, 1, exported = true)
        asm.function("f", emptyList(), emptyList(), exported = true) { _, _ -> }
        val module2 = roundTrip(asm.assemble())

        val kinds = module2.exports.map { it.kind }.toSet()
        assertTrue(WasmModule.ExportKind.FUNCTION in kinds)
        assertTrue(WasmModule.ExportKind.MEMORY in kinds)
        assertTrue(WasmModule.ExportKind.GLOBAL in kinds)
        assertTrue(WasmModule.ExportKind.TABLE in kinds)
    }

    @Test
    fun `round-trip preserves all value types in signatures`() {
        val asm = WasmAssembler.create()
        asm.function("all_types",
            listOf(WasmValueType.I32, WasmValueType.I64, WasmValueType.F32, WasmValueType.F64),
            listOf(WasmValueType.I32), exported = true) { fn, a ->
            a.localGet(fn.getParameter(0))
        }
        val module2 = roundTrip(asm.assemble())

        val type = module2.types[module2.functions[0].typeIndex]
        assertEquals(listOf(WasmValueType.I32, WasmValueType.I64, WasmValueType.F32, WasmValueType.F64), type.params)
        assertEquals(listOf(WasmValueType.I32), type.results)
    }

    @Test
    fun `round-trip complex module with everything`() {
        val asm = WasmAssembler.create()
        asm.importFunction("env", "log", listOf(WasmValueType.I32), emptyList())
        asm.importMemory("env", "memory", 1, 256)
        asm.global("counter", WasmValueType.I32, mutable = true, initValue = 0, exported = true)
        asm.table("tbl", WasmRefType.FUNCREF, 1, 10)
        asm.dataSegment(0, 0, "hello".toByteArray())

        asm.function("inc", emptyList(), emptyList(), exported = true) { _, a ->
            a.globalGet(0)
            a.i32Const(1)
            a.i32Add()
            a.globalSet(0)
        }
        asm.function("get", emptyList(), listOf(WasmValueType.I32), exported = true) { _, a ->
            a.globalGet(0)
        }
        asm.function("add", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.i32Add()
        }

        val module2 = roundTrip(asm.assemble())

        assertEquals(1, module2.imports.count { it is WasmModule.Import.Func })
        assertEquals(1, module2.imports.count { it is WasmModule.Import.Memory })
        assertEquals(1, module2.globals.size)
        assertEquals(1, module2.tables.size)
        assertEquals(1, module2.dataSegments.size)
        assertEquals(3, module2.functions.size)
        assertTrue(module2.exports.size >= 3)
    }

    @Test
    fun `round-trip preserves memory with no max`() {
        val asm = WasmAssembler.create()
        asm.memory("mem", 1)
        asm.function("f", emptyList(), emptyList()) { _, _ -> }
        val module2 = roundTrip(asm.assemble())

        assertEquals(1, module2.memories[0].min)
        assertNull(module2.memories[0].max)
    }

    @Test
    fun `round-trip with many functions preserves order`() {
        val asm = WasmAssembler.create()
        for (i in 0 until 20) {
            asm.function("fn_$i", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { fn, a ->
                a.localGet(fn.getParameter(0))
                a.i32Const(i)
                a.i32Add()
            }
        }
        val module2 = roundTrip(asm.assemble())

        assertEquals(20, module2.functions.size)
        assertEquals(20, module2.exports.size)
        for (i in 0 until 20) {
            assertEquals("fn_$i", module2.exports[i].name)
        }
    }

    @Test
    fun `double round-trip produces identical bytes`() {
        val asm = WasmAssembler.create()
        asm.memory("mem", 1, 10, exported = true)
        asm.global("g", WasmValueType.I32, mutable = true, initValue = 0)
        asm.function("add", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.i32Add()
        }
        val original = asm.assemble()

        val pass1 = WasmModuleWriter.write(WasmModuleReader.read(original))
        val pass2 = WasmModuleWriter.write(WasmModuleReader.read(pass1))

        assertArrayEquals(pass1, pass2)
    }
}
