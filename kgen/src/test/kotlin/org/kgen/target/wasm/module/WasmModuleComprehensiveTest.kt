package org.kgen.target.wasm.module

import org.kgen.target.wasm.*
import org.kgen.target.wasm.asm.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertThrows

class WasmModuleComprehensiveTest {

    private fun assertValidWasm(bytes: ByteArray) {
        assertTrue(bytes.size >= 8, "Too short for a valid .wasm module")
        assertEquals(0x00, bytes[0].toInt() and 0xFF)
        assertEquals(0x61, bytes[1].toInt() and 0xFF)
        assertEquals(0x73, bytes[2].toInt() and 0xFF)
        assertEquals(0x6D, bytes[3].toInt() and 0xFF)
        assertEquals(1, bytes[4].toInt() and 0xFF)
    }

    private fun roundTrip(asm: WasmAssembler): Pair<WasmModule, WasmModule> {
        val original = asm.assemble()
        val module1 = WasmModuleReader.read(original)
        val rewritten = WasmModuleWriter.write(module1)
        assertValidWasm(rewritten)
        val module2 = WasmModuleReader.read(rewritten)
        return module1 to module2
    }

    // Type section

    @Test
    fun `type section with void to void`() {
        val asm = WasmAssembler.create()
        asm.function("f", emptyList(), emptyList(), exported = true) { _, _ -> }
        val module = WasmModuleReader.read(asm.assemble())
        assertEquals(1, module.types.size)
        assertTrue(module.types[0].params.isEmpty())
        assertTrue(module.types[0].results.isEmpty())
    }

    @Test
    fun `type section with i32 to i32`() {
        val asm = WasmAssembler.create()
        asm.function("f", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { fn, a ->
            a.localGet(fn.getParameter(0))
        }
        val module = WasmModuleReader.read(asm.assemble())
        assertEquals(listOf(WasmValueType.I32), module.types[0].params)
        assertEquals(listOf(WasmValueType.I32), module.types[0].results)
    }

    @Test
    fun `type section with i64 to i64`() {
        val asm = WasmAssembler.create()
        asm.function("f", listOf(WasmValueType.I64), listOf(WasmValueType.I64), exported = true) { fn, a ->
            a.localGet(fn.getParameter(0))
        }
        val module = WasmModuleReader.read(asm.assemble())
        assertEquals(listOf(WasmValueType.I64), module.types[0].params)
        assertEquals(listOf(WasmValueType.I64), module.types[0].results)
    }

    @Test
    fun `type section with f32 to f32`() {
        val asm = WasmAssembler.create()
        asm.function("f", listOf(WasmValueType.F32), listOf(WasmValueType.F32), exported = true) { fn, a ->
            a.localGet(fn.getParameter(0))
        }
        val module = WasmModuleReader.read(asm.assemble())
        assertEquals(listOf(WasmValueType.F32), module.types[0].params)
        assertEquals(listOf(WasmValueType.F32), module.types[0].results)
    }

    @Test
    fun `type section with f64 to f64`() {
        val asm = WasmAssembler.create()
        asm.function("f", listOf(WasmValueType.F64), listOf(WasmValueType.F64), exported = true) { fn, a ->
            a.localGet(fn.getParameter(0))
        }
        val module = WasmModuleReader.read(asm.assemble())
        assertEquals(listOf(WasmValueType.F64), module.types[0].params)
        assertEquals(listOf(WasmValueType.F64), module.types[0].results)
    }

    @Test
    fun `type section with all four value types as params`() {
        val asm = WasmAssembler.create()
        asm.function("f",
            listOf(WasmValueType.I32, WasmValueType.I64, WasmValueType.F32, WasmValueType.F64),
            listOf(WasmValueType.I32), exported = true) { fn, a ->
            a.localGet(fn.getParameter(0))
        }
        val module = WasmModuleReader.read(asm.assemble())
        assertEquals(4, module.types[0].params.size)
        assertEquals(WasmValueType.I32, module.types[0].params[0])
        assertEquals(WasmValueType.I64, module.types[0].params[1])
        assertEquals(WasmValueType.F32, module.types[0].params[2])
        assertEquals(WasmValueType.F64, module.types[0].params[3])
    }

    @Test
    fun `type section with multiple results`() {
        val asm = WasmAssembler.create()
        asm.function("swap",
            listOf(WasmValueType.I32, WasmValueType.I32),
            listOf(WasmValueType.I32, WasmValueType.I32), exported = true) { fn, a ->
            a.localGet(fn.getParameter(1))
            a.localGet(fn.getParameter(0))
        }
        val module = WasmModuleReader.read(asm.assemble())
        assertEquals(2, module.types[0].results.size)
    }

    @Test
    fun `type section with many params`() {
        val asm = WasmAssembler.create()
        val params = List(8) { WasmValueType.I32 }
        asm.function("f", params, listOf(WasmValueType.I32), exported = true) { fn, a ->
            a.localGet(fn.getParameter(0))
        }
        val module = WasmModuleReader.read(asm.assemble())
        assertEquals(8, module.types[0].params.size)
    }

    // Function type deduplication

    @Test
    fun `two functions same signature share type index`() {
        val asm = WasmAssembler.create()
        asm.function("a", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { fn, a ->
            a.localGet(fn.getParameter(0))
        }
        asm.function("b", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { fn, a ->
            a.localGet(fn.getParameter(0))
        }
        val module = WasmModuleReader.read(asm.assemble())
        assertEquals(1, module.types.size)
        assertEquals(module.functions[0].typeIndex, module.functions[1].typeIndex)
    }

    @Test
    fun `three different signatures produce three types`() {
        val asm = WasmAssembler.create()
        asm.function("a", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { fn, a ->
            a.localGet(fn.getParameter(0))
        }
        asm.function("b", listOf(WasmValueType.I64), listOf(WasmValueType.I64), exported = true) { fn, a ->
            a.localGet(fn.getParameter(0))
        }
        asm.function("c", listOf(WasmValueType.F32), listOf(WasmValueType.F32), exported = true) { fn, a ->
            a.localGet(fn.getParameter(0))
        }
        val module = WasmModuleReader.read(asm.assemble())
        assertEquals(3, module.types.size)
    }

    @Test
    fun `five functions with two distinct signatures`() {
        val asm = WasmAssembler.create()
        for (i in 0 until 3) {
            asm.function("i32_fn$i", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { fn, a ->
                a.localGet(fn.getParameter(0))
            }
        }
        for (i in 0 until 2) {
            asm.function("void_fn$i", emptyList(), emptyList(), exported = true) { _, _ -> }
        }
        val module = WasmModuleReader.read(asm.assemble())
        assertEquals(2, module.types.size)
    }

    @Test
    fun `import and defined function share type when signatures match`() {
        val asm = WasmAssembler.create()
        asm.importFunction("env", "ext", listOf(WasmValueType.I32), listOf(WasmValueType.I32))
        asm.function("local", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { fn, a ->
            a.localGet(fn.getParameter(0))
        }
        val module = WasmModuleReader.read(asm.assemble())
        assertEquals(1, module.types.size)
    }

    // Import section - function imports

    @Test
    fun `import function with no params no results`() {
        val asm = WasmAssembler.create()
        asm.importFunction("env", "noop", emptyList(), emptyList())
        asm.function("f", emptyList(), emptyList()) { _, _ -> }
        val module = WasmModuleReader.read(asm.assemble())
        assertEquals(1, module.imports.size)
        val imp = module.imports[0] as WasmModule.Import.Func
        assertEquals("env", imp.module)
        assertEquals("noop", imp.name)
    }

    @Test
    fun `import function with params and result`() {
        val asm = WasmAssembler.create()
        asm.importFunction("env", "add", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32))
        asm.function("f", emptyList(), emptyList()) { _, _ -> }
        val module = WasmModuleReader.read(asm.assemble())
        val imp = module.imports[0] as WasmModule.Import.Func
        val typeIdx = imp.typeIndex
        assertEquals(listOf(WasmValueType.I32, WasmValueType.I32), module.types[typeIdx].params)
        assertEquals(listOf(WasmValueType.I32), module.types[typeIdx].results)
    }

    @Test
    fun `import multiple functions`() {
        val asm = WasmAssembler.create()
        asm.importFunction("env", "a", listOf(WasmValueType.I32), emptyList())
        asm.importFunction("env", "b", listOf(WasmValueType.I64), emptyList())
        asm.importFunction("math", "c", listOf(WasmValueType.F64), listOf(WasmValueType.F64))
        asm.function("f", emptyList(), emptyList()) { _, _ -> }
        val module = WasmModuleReader.read(asm.assemble())
        assertEquals(3, module.importedFunctionCount)
    }

    @Test
    fun `import from different modules`() {
        val asm = WasmAssembler.create()
        asm.importFunction("env", "log", listOf(WasmValueType.I32), emptyList())
        asm.importFunction("wasi_snapshot_preview1", "fd_write",
            listOf(WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32),
            listOf(WasmValueType.I32))
        asm.function("f", emptyList(), emptyList()) { _, _ -> }
        val module = WasmModuleReader.read(asm.assemble())
        assertEquals("env", (module.imports[0] as WasmModule.Import.Func).module)
        assertEquals("wasi_snapshot_preview1", (module.imports[1] as WasmModule.Import.Func).module)
    }

    // Import section - memory imports

    @Test
    fun `import memory with min only`() {
        val asm = WasmAssembler.create()
        asm.importMemory("env", "memory", 1)
        asm.function("f", emptyList(), emptyList()) { _, _ -> }
        val module = WasmModuleReader.read(asm.assemble())
        val imp = module.imports[0] as WasmModule.Import.Memory
        assertEquals(1, imp.min)
        assertNull(imp.max)
    }

    @Test
    fun `import memory with min and max`() {
        val asm = WasmAssembler.create()
        asm.importMemory("env", "memory", 1, 256)
        asm.function("f", emptyList(), emptyList()) { _, _ -> }
        val module = WasmModuleReader.read(asm.assemble())
        val imp = module.imports[0] as WasmModule.Import.Memory
        assertEquals(1, imp.min)
        assertEquals(256, imp.max)
    }

    // Import section - global imports

    @Test
    fun `import mutable global i32`() {
        val asm = WasmAssembler.create()
        asm.importGlobal("env", "sp", WasmValueType.I32, mutable = true)
        asm.function("f", emptyList(), emptyList()) { _, _ -> }
        val module = WasmModuleReader.read(asm.assemble())
        val imp = module.imports[0] as WasmModule.Import.Global
        assertEquals(WasmValueType.I32, imp.type)
        assertTrue(imp.mutable)
    }

    @Test
    fun `import immutable global i64`() {
        val asm = WasmAssembler.create()
        asm.importGlobal("env", "size", WasmValueType.I64, mutable = false)
        asm.function("f", emptyList(), emptyList()) { _, _ -> }
        val module = WasmModuleReader.read(asm.assemble())
        val imp = module.imports[0] as WasmModule.Import.Global
        assertEquals(WasmValueType.I64, imp.type)
        assertFalse(imp.mutable)
    }

    @Test
    fun `import global f32`() {
        val asm = WasmAssembler.create()
        asm.importGlobal("env", "scale", WasmValueType.F32, mutable = false)
        asm.function("f", emptyList(), emptyList()) { _, _ -> }
        val module = WasmModuleReader.read(asm.assemble())
        val imp = module.imports[0] as WasmModule.Import.Global
        assertEquals(WasmValueType.F32, imp.type)
    }

    @Test
    fun `import global f64`() {
        val asm = WasmAssembler.create()
        asm.importGlobal("env", "pi", WasmValueType.F64, mutable = false)
        asm.function("f", emptyList(), emptyList()) { _, _ -> }
        val module = WasmModuleReader.read(asm.assemble())
        val imp = module.imports[0] as WasmModule.Import.Global
        assertEquals(WasmValueType.F64, imp.type)
    }

    // Import section - table imports

    @Test
    fun `import funcref table`() {
        val asm = WasmAssembler.create()
        asm.importTable("env", "tbl", WasmRefType.FUNCREF, 0, 100)
        asm.function("f", emptyList(), emptyList()) { _, _ -> }
        val module = WasmModuleReader.read(asm.assemble())
        val imp = module.imports[0] as WasmModule.Import.Table
        assertEquals(WasmRefType.FUNCREF, imp.refType)
        assertEquals(0, imp.min)
        assertEquals(100, imp.max)
    }

    @Test
    fun `import externref table`() {
        val asm = WasmAssembler.create()
        asm.importTable("env", "ext_tbl", WasmRefType.EXTERNREF, 1, 50)
        asm.function("f", emptyList(), emptyList()) { _, _ -> }
        val module = WasmModuleReader.read(asm.assemble())
        val imp = module.imports[0] as WasmModule.Import.Table
        assertEquals(WasmRefType.EXTERNREF, imp.refType)
    }

    // Import section - mixed imports

    @Test
    fun `mixed imports all four kinds`() {
        val asm = WasmAssembler.create()
        asm.importFunction("env", "log", listOf(WasmValueType.I32), emptyList())
        asm.importMemory("env", "memory", 1)
        asm.importGlobal("env", "sp", WasmValueType.I32, mutable = true)
        asm.importTable("env", "tbl", WasmRefType.FUNCREF, 0, 10)
        asm.function("f", emptyList(), emptyList()) { _, _ -> }
        val module = WasmModuleReader.read(asm.assemble())
        assertEquals(4, module.imports.size)
        assertEquals(1, module.importedFunctionCount)
        assertEquals(1, module.importedMemoryCount)
        assertEquals(1, module.importedGlobalCount)
        assertEquals(1, module.importedTableCount)
    }

    // Function section

    @Test
    fun `single function has correct type index`() {
        val asm = WasmAssembler.create()
        asm.function("f", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { fn, a ->
            a.localGet(fn.getParameter(0))
        }
        val module = WasmModuleReader.read(asm.assemble())
        assertEquals(1, module.functions.size)
        assertEquals(0, module.functions[0].typeIndex)
    }

    @Test
    fun `multiple functions correct type indices`() {
        val asm = WasmAssembler.create()
        asm.function("a", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { fn, a ->
            a.localGet(fn.getParameter(0))
        }
        asm.function("b", listOf(WasmValueType.I64), listOf(WasmValueType.I64), exported = true) { fn, a ->
            a.localGet(fn.getParameter(0))
        }
        asm.function("c", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { fn, a ->
            a.localGet(fn.getParameter(0))
        }
        val module = WasmModuleReader.read(asm.assemble())
        assertEquals(3, module.functions.size)
        assertEquals(module.functions[0].typeIndex, module.functions[2].typeIndex)
        assertNotEquals(module.functions[0].typeIndex, module.functions[1].typeIndex)
    }

    // Table section

    @Test
    fun `funcref table with min and max`() {
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
    fun `funcref table with min only`() {
        val asm = WasmAssembler.create()
        asm.table("tbl", WasmRefType.FUNCREF, 0)
        asm.function("f", emptyList(), emptyList()) { _, _ -> }
        val module = WasmModuleReader.read(asm.assemble())
        assertEquals(0, module.tables[0].min)
        assertNull(module.tables[0].max)
    }

    @Test
    fun `externref table`() {
        val asm = WasmAssembler.create()
        asm.table("tbl", WasmRefType.EXTERNREF, 5, 50)
        asm.function("f", emptyList(), emptyList()) { _, _ -> }
        val module = WasmModuleReader.read(asm.assemble())
        assertEquals(WasmRefType.EXTERNREF, module.tables[0].refType)
    }

    // Memory section

    @Test
    fun `memory with min only`() {
        val asm = WasmAssembler.create()
        asm.memory("mem", 1)
        asm.function("f", emptyList(), emptyList()) { _, _ -> }
        val module = WasmModuleReader.read(asm.assemble())
        assertEquals(1, module.memories.size)
        assertEquals(1, module.memories[0].min)
        assertNull(module.memories[0].max)
    }

    @Test
    fun `memory with min and max`() {
        val asm = WasmAssembler.create()
        asm.memory("mem", 1, 256)
        asm.function("f", emptyList(), emptyList()) { _, _ -> }
        val module = WasmModuleReader.read(asm.assemble())
        assertEquals(1, module.memories[0].min)
        assertEquals(256, module.memories[0].max)
    }

    @Test
    fun `memory with zero min`() {
        val asm = WasmAssembler.create()
        asm.memory("mem", 0)
        asm.function("f", emptyList(), emptyList()) { _, _ -> }
        val module = WasmModuleReader.read(asm.assemble())
        assertEquals(0, module.memories[0].min)
    }

    @Test
    fun `memory with large max`() {
        val asm = WasmAssembler.create()
        asm.memory("mem", 1, 65536)
        asm.function("f", emptyList(), emptyList()) { _, _ -> }
        val module = WasmModuleReader.read(asm.assemble())
        assertEquals(65536, module.memories[0].max)
    }

    // Global section

    @Test
    fun `mutable i32 global`() {
        val asm = WasmAssembler.create()
        asm.global("g", WasmValueType.I32, mutable = true, initValue = 42)
        asm.function("f", emptyList(), emptyList()) { _, _ -> }
        val module = WasmModuleReader.read(asm.assemble())
        assertEquals(1, module.globals.size)
        assertEquals(WasmValueType.I32, module.globals[0].type)
        assertTrue(module.globals[0].mutable)
    }

    @Test
    fun `immutable i32 global`() {
        val asm = WasmAssembler.create()
        asm.global("g", WasmValueType.I32, mutable = false, initValue = 0)
        asm.function("f", emptyList(), emptyList()) { _, _ -> }
        val module = WasmModuleReader.read(asm.assemble())
        assertFalse(module.globals[0].mutable)
    }

    @Test
    fun `i64 global`() {
        val asm = WasmAssembler.create()
        asm.global("g", WasmValueType.I64, mutable = true, initValue = 100)
        asm.function("f", emptyList(), emptyList()) { _, _ -> }
        val module = WasmModuleReader.read(asm.assemble())
        assertEquals(WasmValueType.I64, module.globals[0].type)
    }

    @Test
    fun `multiple globals`() {
        val asm = WasmAssembler.create()
        asm.global("a", WasmValueType.I32, mutable = true, initValue = 0)
        asm.global("b", WasmValueType.I32, mutable = false, initValue = 10)
        asm.global("c", WasmValueType.I64, mutable = true, initValue = 999)
        asm.function("f", emptyList(), emptyList()) { _, _ -> }
        val module = WasmModuleReader.read(asm.assemble())
        assertEquals(3, module.globals.size)
    }

    // Export section

    @Test
    fun `export function`() {
        val asm = WasmAssembler.create()
        asm.function("myFunc", emptyList(), emptyList(), exported = true) { _, _ -> }
        val module = WasmModuleReader.read(asm.assemble())
        assertEquals(1, module.exports.size)
        assertEquals("myFunc", module.exports[0].name)
        assertEquals(WasmModule.ExportKind.FUNCTION, module.exports[0].kind)
    }

    @Test
    fun `export memory`() {
        val asm = WasmAssembler.create()
        asm.memory("memory", 1, exported = true)
        asm.function("f", emptyList(), emptyList()) { _, _ -> }
        val module = WasmModuleReader.read(asm.assemble())
        val memExport = module.exports.find { it.kind == WasmModule.ExportKind.MEMORY }
        assertNotNull(memExport)
        assertEquals("memory", memExport!!.name)
    }

    @Test
    fun `export global`() {
        val asm = WasmAssembler.create()
        asm.global("counter", WasmValueType.I32, mutable = true, initValue = 0, exported = true)
        asm.function("f", emptyList(), emptyList()) { _, _ -> }
        val module = WasmModuleReader.read(asm.assemble())
        val globalExport = module.exports.find { it.kind == WasmModule.ExportKind.GLOBAL }
        assertNotNull(globalExport)
        assertEquals("counter", globalExport!!.name)
    }

    @Test
    fun `export table`() {
        val asm = WasmAssembler.create()
        asm.table("tbl", WasmRefType.FUNCREF, 1, exported = true)
        asm.function("f", emptyList(), emptyList()) { _, _ -> }
        val module = WasmModuleReader.read(asm.assemble())
        val tableExport = module.exports.find { it.kind == WasmModule.ExportKind.TABLE }
        assertNotNull(tableExport)
    }

    @Test
    fun `export all four kinds`() {
        val asm = WasmAssembler.create()
        asm.memory("mem", 1, exported = true)
        asm.global("g", WasmValueType.I32, mutable = false, initValue = 0, exported = true)
        asm.table("tbl", WasmRefType.FUNCREF, 1, exported = true)
        asm.function("f", emptyList(), emptyList(), exported = true) { _, _ -> }
        val module = WasmModuleReader.read(asm.assemble())
        val kinds = module.exports.map { it.kind }.toSet()
        assertEquals(4, kinds.size)
        assertTrue(WasmModule.ExportKind.FUNCTION in kinds)
        assertTrue(WasmModule.ExportKind.MEMORY in kinds)
        assertTrue(WasmModule.ExportKind.GLOBAL in kinds)
        assertTrue(WasmModule.ExportKind.TABLE in kinds)
    }

    @Test
    fun `non-exported function not in exports`() {
        val asm = WasmAssembler.create()
        asm.function("internal", emptyList(), emptyList()) { _, _ -> }
        asm.function("public", emptyList(), emptyList(), exported = true) { _, _ -> }
        val module = WasmModuleReader.read(asm.assemble())
        assertEquals(1, module.exports.size)
        assertEquals("public", module.exports[0].name)
    }

    @Test
    fun `multiple function exports`() {
        val asm = WasmAssembler.create()
        for (i in 0 until 5) {
            asm.function("fn$i", emptyList(), emptyList(), exported = true) { _, _ -> }
        }
        val module = WasmModuleReader.read(asm.assemble())
        assertEquals(5, module.exports.size)
    }

    // Start section

    @Test
    fun `module with start function`() {
        val asm = WasmAssembler.create()
        asm.function("init", emptyList(), emptyList()) { _, _ -> }
        asm.startFunction("init")
        val module = WasmModuleReader.read(asm.assemble())
        assertNotNull(module.start)
    }

    // Data segments

    @Test
    fun `active data segment at offset zero`() {
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
    fun `active data segment at non-zero offset`() {
        val asm = WasmAssembler.create()
        asm.memory("mem", 1)
        asm.dataSegment(0, 1024, "data".toByteArray())
        asm.function("f", emptyList(), emptyList()) { _, _ -> }
        val module = WasmModuleReader.read(asm.assemble())
        val seg = module.dataSegments[0] as WasmModule.DataSegment.Active
        assertEquals("data", String(seg.data))
    }

    @Test
    fun `passive data segment`() {
        val asm = WasmAssembler.create()
        asm.memory("mem", 1)
        asm.dataSegment("passive content".toByteArray())
        asm.function("f", emptyList(), emptyList()) { _, _ -> }
        val module = WasmModuleReader.read(asm.assemble())
        val seg = module.dataSegments[0] as WasmModule.DataSegment.Passive
        assertEquals("passive content", String(seg.data))
    }

    @Test
    fun `multiple active data segments`() {
        val asm = WasmAssembler.create()
        asm.memory("mem", 1)
        asm.dataSegment(0, 0, "first".toByteArray())
        asm.dataSegment(0, 100, "second".toByteArray())
        asm.dataSegment(0, 200, "third".toByteArray())
        asm.function("f", emptyList(), emptyList()) { _, _ -> }
        val module = WasmModuleReader.read(asm.assemble())
        assertEquals(3, module.dataSegments.size)
    }

    @Test
    fun `mixed active and passive data segments`() {
        val asm = WasmAssembler.create()
        asm.memory("mem", 1)
        asm.dataSegment(0, 0, "active".toByteArray())
        asm.dataSegment("passive".toByteArray())
        asm.function("f", emptyList(), emptyList()) { _, _ -> }
        val module = WasmModuleReader.read(asm.assemble())
        assertEquals(2, module.dataSegments.size)
        assertTrue(module.dataSegments[0] is WasmModule.DataSegment.Active)
        assertTrue(module.dataSegments[1] is WasmModule.DataSegment.Passive)
    }

    @Test
    fun `empty data segment`() {
        val asm = WasmAssembler.create()
        asm.memory("mem", 1)
        asm.dataSegment(0, 0, byteArrayOf())
        asm.function("f", emptyList(), emptyList()) { _, _ -> }
        val module = WasmModuleReader.read(asm.assemble())
        val seg = module.dataSegments[0] as WasmModule.DataSegment.Active
        assertEquals(0, seg.data.size)
    }

    @Test
    fun `large data segment 4096 bytes`() {
        val asm = WasmAssembler.create()
        asm.memory("mem", 1)
        val data = ByteArray(4096) { (it % 256).toByte() }
        asm.dataSegment(0, 0, data)
        asm.function("f", emptyList(), emptyList()) { _, _ -> }
        val module = WasmModuleReader.read(asm.assemble())
        val seg = module.dataSegments[0] as WasmModule.DataSegment.Active
        assertArrayEquals(data, seg.data)
    }

    @Test
    fun `data segment with binary content`() {
        val asm = WasmAssembler.create()
        asm.memory("mem", 1)
        val data = byteArrayOf(0x00, 0x01, 0x7F, 0x80.toByte(), 0xFF.toByte())
        asm.dataSegment(0, 0, data)
        asm.function("f", emptyList(), emptyList()) { _, _ -> }
        val module = WasmModuleReader.read(asm.assemble())
        val seg = module.dataSegments[0] as WasmModule.DataSegment.Active
        assertArrayEquals(data, seg.data)
    }

    // Custom sections (via WasmModuleWriter/Reader round trip)

    @Test
    fun `custom section round trip`() {
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
            customSections = listOf(WasmModule.CustomSection("mySection", "custom data".toByteArray()))
        )
        val bytes = WasmModuleWriter.write(module)
        assertValidWasm(bytes)
        val read = WasmModuleReader.read(bytes)
        assertEquals(1, read.customSections.size)
        assertEquals("mySection", read.customSections[0].name)
        assertEquals("custom data", String(read.customSections[0].data))
    }

    @Test
    fun `multiple custom sections`() {
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
                WasmModule.CustomSection("section1", "data1".toByteArray()),
                WasmModule.CustomSection("section2", "data2".toByteArray()),
                WasmModule.CustomSection("section3", "data3".toByteArray()),
            )
        )
        val bytes = WasmModuleWriter.write(module)
        val read = WasmModuleReader.read(bytes)
        assertEquals(3, read.customSections.size)
    }

    @Test
    fun `custom section with empty data`() {
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
            customSections = listOf(WasmModule.CustomSection("empty", byteArrayOf()))
        )
        val bytes = WasmModuleWriter.write(module)
        val read = WasmModuleReader.read(bytes)
        assertEquals(1, read.customSections.size)
        assertEquals(0, read.customSections[0].data.size)
    }

    // Locals

    @Test
    fun `function with single local`() {
        val asm = WasmAssembler.create()
        asm.function("f", emptyList(), listOf(WasmValueType.I32), exported = true) { _, a ->
            val loc = a.declareLocal("x", WasmValueType.I32)
            a.i32Const(42)
            a.localSet(loc)
            a.localGet(loc)
        }
        val module = WasmModuleReader.read(asm.assemble())
        assertEquals(listOf(WasmValueType.I32), module.functions[0].locals)
    }

    @Test
    fun `function with multiple same-type locals`() {
        val asm = WasmAssembler.create()
        asm.function("f", emptyList(), emptyList()) { _, a ->
            a.declareLocal(WasmValueType.I32)
            a.declareLocal(WasmValueType.I32)
            a.declareLocal(WasmValueType.I32)
        }
        val module = WasmModuleReader.read(asm.assemble())
        assertEquals(3, module.functions[0].locals.size)
        assertTrue(module.functions[0].locals.all { it == WasmValueType.I32 })
    }

    @Test
    fun `function with different type locals`() {
        val asm = WasmAssembler.create()
        asm.function("f", emptyList(), emptyList()) { _, a ->
            a.declareLocal(WasmValueType.I32)
            a.declareLocal(WasmValueType.I64)
            a.declareLocal(WasmValueType.F32)
            a.declareLocal(WasmValueType.F64)
        }
        val module = WasmModuleReader.read(asm.assemble())
        assertEquals(
            listOf(WasmValueType.I32, WasmValueType.I64, WasmValueType.F32, WasmValueType.F64),
            module.functions[0].locals
        )
    }

    @Test
    fun `function with no locals`() {
        val asm = WasmAssembler.create()
        asm.function("f", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { fn, a ->
            a.localGet(fn.getParameter(0))
        }
        val module = WasmModuleReader.read(asm.assemble())
        assertTrue(module.functions[0].locals.isEmpty())
    }

    // Function body

    @Test
    fun `function body ends with 0x0B`() {
        val asm = WasmAssembler.create()
        asm.function("f", emptyList(), emptyList(), exported = true) { _, _ -> }
        val module = WasmModuleReader.read(asm.assemble())
        val body = module.functions[0].body
        assertEquals(0x0B, body.last().toInt() and 0xFF)
    }

    @Test
    fun `function body with arithmetic ops`() {
        val asm = WasmAssembler.create()
        asm.function("f", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.i32Add()
        }
        val module = WasmModuleReader.read(asm.assemble())
        assertTrue(module.functions[0].body.size > 1)
    }

    // Function name resolution

    @Test
    fun `functionName resolves import at index 0`() {
        val asm = WasmAssembler.create()
        asm.importFunction("env", "ext", emptyList(), emptyList())
        asm.function("local", emptyList(), emptyList(), exported = true) { _, _ -> }
        val module = WasmModuleReader.read(asm.assemble())
        assertEquals("ext", module.functionName(0))
        assertEquals("local", module.functionName(1))
    }

    @Test
    fun `functionName returns null for out of range index`() {
        val asm = WasmAssembler.create()
        asm.function("f", emptyList(), emptyList()) { _, _ -> }
        val module = WasmModuleReader.read(asm.assemble())
        assertNull(module.functionName(100))
    }

    @Test
    fun `functionName with multiple imports and functions`() {
        val asm = WasmAssembler.create()
        asm.importFunction("env", "imp0", emptyList(), emptyList())
        asm.importFunction("env", "imp1", listOf(WasmValueType.I32), emptyList())
        asm.function("fn0", emptyList(), emptyList(), exported = true) { _, _ -> }
        asm.function("fn1", emptyList(), emptyList(), exported = true) { _, _ -> }
        val module = WasmModuleReader.read(asm.assemble())
        assertEquals("imp0", module.functionName(0))
        assertEquals("imp1", module.functionName(1))
        assertEquals("fn0", module.functionName(2))
        assertEquals("fn1", module.functionName(3))
    }

    // Round-trip tests

    @Test
    fun `round-trip simple function`() {
        val asm = WasmAssembler.create()
        asm.function("f", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.i32Const(1)
            a.i32Add()
        }
        val (m1, m2) = roundTrip(asm)
        assertEquals(m1.types, m2.types)
        assertEquals(m1.exports.size, m2.exports.size)
        assertEquals(m1.functions.size, m2.functions.size)
    }

    @Test
    fun `round-trip with imports`() {
        val asm = WasmAssembler.create()
        asm.importFunction("env", "log", listOf(WasmValueType.I32), emptyList())
        asm.function("main", emptyList(), emptyList(), exported = true) { _, a ->
            a.i32Const(42)
            a.call("log")
        }
        val (m1, m2) = roundTrip(asm)
        assertEquals(m1.imports.size, m2.imports.size)
    }

    @Test
    fun `round-trip with memory`() {
        val asm = WasmAssembler.create()
        asm.memory("mem", 1, 10, exported = true)
        asm.function("f", emptyList(), emptyList()) { _, _ -> }
        val (_, m2) = roundTrip(asm)
        assertEquals(1, m2.memories.size)
        assertEquals(1, m2.memories[0].min)
        assertEquals(10, m2.memories[0].max)
    }

    @Test
    fun `round-trip with globals`() {
        val asm = WasmAssembler.create()
        asm.global("g1", WasmValueType.I32, mutable = true, initValue = 0)
        asm.global("g2", WasmValueType.I64, mutable = false, initValue = 42)
        asm.function("f", emptyList(), emptyList()) { _, _ -> }
        val (_, m2) = roundTrip(asm)
        assertEquals(2, m2.globals.size)
    }

    @Test
    fun `round-trip with table`() {
        val asm = WasmAssembler.create()
        asm.table("tbl", WasmRefType.FUNCREF, 1, 10, exported = true)
        asm.function("f", emptyList(), emptyList()) { _, _ -> }
        val (_, m2) = roundTrip(asm)
        assertEquals(1, m2.tables.size)
        assertEquals(WasmRefType.FUNCREF, m2.tables[0].refType)
    }

    @Test
    fun `round-trip with data segments`() {
        val asm = WasmAssembler.create()
        asm.memory("mem", 1)
        asm.dataSegment(0, 0, "hello".toByteArray())
        asm.dataSegment("passive".toByteArray())
        asm.function("f", emptyList(), emptyList()) { _, _ -> }
        val (_, m2) = roundTrip(asm)
        assertEquals(2, m2.dataSegments.size)
    }

    @Test
    fun `round-trip preserves function body`() {
        val asm = WasmAssembler.create()
        asm.function("f", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.i32Add()
            a.i32Const(2)
            a.i32Mul()
        }
        val (m1, m2) = roundTrip(asm)
        assertArrayEquals(m1.functions[0].body, m2.functions[0].body)
    }

    @Test
    fun `round-trip preserves locals`() {
        val asm = WasmAssembler.create()
        asm.function("f", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { fn, a ->
            val tmp = a.declareLocal("tmp", WasmValueType.I32)
            a.localGet(fn.getParameter(0))
            a.i32Const(10)
            a.i32Add()
            a.localSet(tmp)
            a.localGet(tmp)
        }
        val (m1, m2) = roundTrip(asm)
        assertEquals(m1.functions[0].locals, m2.functions[0].locals)
    }

    @Test
    fun `round-trip many functions`() {
        val asm = WasmAssembler.create()
        for (i in 0 until 25) {
            asm.function("fn$i", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { fn, a ->
                a.localGet(fn.getParameter(0))
                a.i32Const(i)
                a.i32Add()
            }
        }
        val (m1, m2) = roundTrip(asm)
        assertEquals(25, m2.functions.size)
        assertEquals(25, m2.exports.size)
    }

    // WasmModuleWriter direct construction

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
    fun `write module with types only`() {
        val module = WasmModule(
            version = 1,
            types = listOf(
                WasmModule.FuncType(listOf(WasmValueType.I32), listOf(WasmValueType.I32)),
                WasmModule.FuncType(emptyList(), emptyList()),
            ),
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
        val read = WasmModuleReader.read(bytes)
        assertEquals(2, read.types.size)
    }

    @Test
    fun `write module with start function index`() {
        val module = WasmModule(
            version = 1,
            types = listOf(WasmModule.FuncType(emptyList(), emptyList())),
            imports = emptyList(),
            functions = listOf(WasmModule.Function(null, 0, emptyList(), byteArrayOf(0x0B))),
            tables = emptyList(),
            memories = emptyList(),
            globals = emptyList(),
            exports = emptyList(),
            start = 0,
            elements = emptyList(),
            dataSegments = emptyList(),
            customSections = emptyList(),
        )
        val bytes = WasmModuleWriter.write(module)
        assertValidWasm(bytes)
        val read = WasmModuleReader.read(bytes)
        assertEquals(0, read.start)
    }

    @Test
    fun `write module with element segment`() {
        val module = WasmModule(
            version = 1,
            types = listOf(WasmModule.FuncType(emptyList(), emptyList())),
            imports = emptyList(),
            functions = listOf(WasmModule.Function(null, 0, emptyList(), byteArrayOf(0x0B))),
            tables = listOf(WasmModule.Table(WasmRefType.FUNCREF, 1, null)),
            memories = emptyList(),
            globals = emptyList(),
            exports = emptyList(),
            start = null,
            elements = listOf(WasmModule.Element(0, byteArrayOf(0x41, 0x00, 0x0B), listOf(0))),
            dataSegments = emptyList(),
            customSections = emptyList(),
        )
        val bytes = WasmModuleWriter.write(module)
        assertValidWasm(bytes)
        val read = WasmModuleReader.read(bytes)
        assertEquals(1, read.elements.size)
        assertEquals(listOf(0), read.elements[0].funcIndices)
    }

    @Test
    fun `write module with multiple element segments`() {
        val module = WasmModule(
            version = 1,
            types = listOf(WasmModule.FuncType(emptyList(), emptyList())),
            imports = emptyList(),
            functions = listOf(
                WasmModule.Function(null, 0, emptyList(), byteArrayOf(0x0B)),
                WasmModule.Function(null, 0, emptyList(), byteArrayOf(0x0B)),
            ),
            tables = listOf(WasmModule.Table(WasmRefType.FUNCREF, 4, null)),
            memories = emptyList(),
            globals = emptyList(),
            exports = emptyList(),
            start = null,
            elements = listOf(
                WasmModule.Element(0, byteArrayOf(0x41, 0x00, 0x0B), listOf(0, 1)),
                WasmModule.Element(0, byteArrayOf(0x41, 0x02, 0x0B), listOf(1, 0)),
            ),
            dataSegments = emptyList(),
            customSections = emptyList(),
        )
        val bytes = WasmModuleWriter.write(module)
        assertValidWasm(bytes)
        val read = WasmModuleReader.read(bytes)
        assertEquals(2, read.elements.size)
    }

    @Test
    fun `write module with all sections populated`() {
        val module = WasmModule(
            version = 1,
            types = listOf(
                WasmModule.FuncType(listOf(WasmValueType.I32), listOf(WasmValueType.I32)),
                WasmModule.FuncType(emptyList(), emptyList()),
            ),
            imports = listOf(
                WasmModule.Import.Func("env", "ext", 0),
                WasmModule.Import.Memory("env", "memory", 1, 10),
                WasmModule.Import.Global("env", "sp", WasmValueType.I32, true),
            ),
            functions = listOf(
                WasmModule.Function("main", 1, emptyList(), byteArrayOf(0x0B)),
            ),
            tables = listOf(WasmModule.Table(WasmRefType.FUNCREF, 1, 10)),
            memories = emptyList(),
            globals = listOf(
                WasmModule.Global(WasmValueType.I32, false, byteArrayOf(0x41, 0x00, 0x0B)),
            ),
            exports = listOf(
                WasmModule.Export("main", WasmModule.ExportKind.FUNCTION, 1),
            ),
            start = null,
            elements = emptyList(),
            dataSegments = emptyList(),
            customSections = listOf(WasmModule.CustomSection("metadata", "test".toByteArray())),
        )
        val bytes = WasmModuleWriter.write(module)
        assertValidWasm(bytes)
        val read = WasmModuleReader.read(bytes)
        assertEquals(2, read.types.size)
        assertEquals(3, read.imports.size)
        assertEquals(1, read.functions.size)
        assertEquals(1, read.tables.size)
        assertEquals(1, read.globals.size)
        assertEquals(1, read.exports.size)
    }

    // Multi-function modules

    @Test
    fun `fifty functions`() {
        val asm = WasmAssembler.create()
        for (i in 0 until 50) {
            asm.function("fn$i", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { fn, a ->
                a.localGet(fn.getParameter(0))
                a.i32Const(i)
                a.i32Add()
            }
        }
        val module = WasmModuleReader.read(asm.assemble())
        assertEquals(50, module.functions.size)
        assertEquals(50, module.exports.size)
    }

    @Test
    fun `functions with mixed signatures`() {
        val asm = WasmAssembler.create()
        asm.function("i32_id", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { fn, a ->
            a.localGet(fn.getParameter(0))
        }
        asm.function("i64_id", listOf(WasmValueType.I64), listOf(WasmValueType.I64), exported = true) { fn, a ->
            a.localGet(fn.getParameter(0))
        }
        asm.function("f32_id", listOf(WasmValueType.F32), listOf(WasmValueType.F32), exported = true) { fn, a ->
            a.localGet(fn.getParameter(0))
        }
        asm.function("f64_id", listOf(WasmValueType.F64), listOf(WasmValueType.F64), exported = true) { fn, a ->
            a.localGet(fn.getParameter(0))
        }
        asm.function("void_fn", emptyList(), emptyList(), exported = true) { _, _ -> }
        val module = WasmModuleReader.read(asm.assemble())
        assertEquals(5, module.functions.size)
        assertEquals(5, module.types.size)
    }

    @Test
    fun `function calls another function`() {
        val asm = WasmAssembler.create()
        asm.function("double", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(0))
            a.i32Add()
        }
        asm.function("quadruple", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.call("double")
            a.call("double")
        }
        val module = WasmModuleReader.read(asm.assemble())
        assertEquals(2, module.functions.size)
    }

    // Edge cases

    @Test
    fun `minimal module just magic and version`() {
        val bytes = byteArrayOf(0x00, 0x61, 0x73, 0x6D, 0x01, 0x00, 0x00, 0x00)
        val module = WasmModuleReader.read(bytes)
        assertEquals(1, module.version)
        assertTrue(module.functions.isEmpty())
        assertTrue(module.imports.isEmpty())
        assertTrue(module.exports.isEmpty())
        assertTrue(module.types.isEmpty())
        assertTrue(module.tables.isEmpty())
        assertTrue(module.memories.isEmpty())
        assertTrue(module.globals.isEmpty())
        assertTrue(module.dataSegments.isEmpty())
        assertTrue(module.elements.isEmpty())
        assertTrue(module.customSections.isEmpty())
    }

    @Test
    fun `module with only imports no defined functions`() {
        val asm = WasmAssembler.create()
        asm.importFunction("env", "a", listOf(WasmValueType.I32), emptyList())
        asm.importFunction("env", "b", listOf(WasmValueType.I64), listOf(WasmValueType.I64))
        asm.function("stub", emptyList(), emptyList()) { _, _ -> }
        val module = WasmModuleReader.read(asm.assemble())
        assertEquals(2, module.importedFunctionCount)
    }

    @Test
    fun `module with unicode export name`() {
        val asm = WasmAssembler.create()
        asm.function("add_\u00e9", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.i32Add()
        }
        val module = WasmModuleReader.read(asm.assemble())
        assertEquals("add_\u00e9", module.exports[0].name)
    }

    @Test
    fun `module with long function name`() {
        val longName = "a".repeat(200)
        val asm = WasmAssembler.create()
        asm.function(longName, emptyList(), emptyList(), exported = true) { _, _ -> }
        val module = WasmModuleReader.read(asm.assemble())
        assertEquals(longName, module.exports[0].name)
    }

    @Test
    fun `ExportKind fromCode`() {
        assertEquals(WasmModule.ExportKind.FUNCTION, WasmModule.ExportKind.fromCode(0x00))
        assertEquals(WasmModule.ExportKind.TABLE, WasmModule.ExportKind.fromCode(0x01))
        assertEquals(WasmModule.ExportKind.MEMORY, WasmModule.ExportKind.fromCode(0x02))
        assertEquals(WasmModule.ExportKind.GLOBAL, WasmModule.ExportKind.fromCode(0x03))
    }

    @Test
    fun `ExportKind fromCode invalid throws`() {
        assertThrows<NoSuchElementException> { WasmModule.ExportKind.fromCode(0x99) }
    }

    // Error handling

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
    fun `rejects truncated header`() {
        assertThrows<Exception> { WasmModuleReader.read(byteArrayOf(0x00, 0x61)) }
    }

    @Test
    fun `rejects empty byte array`() {
        assertThrows<Exception> { WasmModuleReader.read(byteArrayOf()) }
    }

    // Imported counts

    @Test
    fun `importedFunctionCount with no imports`() {
        val asm = WasmAssembler.create()
        asm.function("f", emptyList(), emptyList()) { _, _ -> }
        val module = WasmModuleReader.read(asm.assemble())
        assertEquals(0, module.importedFunctionCount)
    }

    @Test
    fun `importedMemoryCount with no memory imports`() {
        val asm = WasmAssembler.create()
        asm.importFunction("env", "f", emptyList(), emptyList())
        asm.function("g", emptyList(), emptyList()) { _, _ -> }
        val module = WasmModuleReader.read(asm.assemble())
        assertEquals(0, module.importedMemoryCount)
    }

    @Test
    fun `importedTableCount with no table imports`() {
        val asm = WasmAssembler.create()
        asm.function("f", emptyList(), emptyList()) { _, _ -> }
        val module = WasmModuleReader.read(asm.assemble())
        assertEquals(0, module.importedTableCount)
    }

    @Test
    fun `importedGlobalCount with no global imports`() {
        val asm = WasmAssembler.create()
        asm.function("f", emptyList(), emptyList()) { _, _ -> }
        val module = WasmModuleReader.read(asm.assemble())
        assertEquals(0, module.importedGlobalCount)
    }

    @Test
    fun `importedFunctionCount with mixed imports`() {
        val asm = WasmAssembler.create()
        asm.importFunction("env", "f1", emptyList(), emptyList())
        asm.importFunction("env", "f2", listOf(WasmValueType.I32), emptyList())
        asm.importMemory("env", "mem", 1)
        asm.importGlobal("env", "g", WasmValueType.I32, mutable = false)
        asm.function("local", emptyList(), emptyList()) { _, _ -> }
        val module = WasmModuleReader.read(asm.assemble())
        assertEquals(2, module.importedFunctionCount)
        assertEquals(1, module.importedMemoryCount)
        assertEquals(1, module.importedGlobalCount)
    }

    // WasmModule data class equality

    @Test
    fun `FuncType equality`() {
        val t1 = WasmModule.FuncType(listOf(WasmValueType.I32), listOf(WasmValueType.I32))
        val t2 = WasmModule.FuncType(listOf(WasmValueType.I32), listOf(WasmValueType.I32))
        assertEquals(t1, t2)
    }

    @Test
    fun `FuncType inequality by params`() {
        val t1 = WasmModule.FuncType(listOf(WasmValueType.I32), listOf(WasmValueType.I32))
        val t2 = WasmModule.FuncType(listOf(WasmValueType.I64), listOf(WasmValueType.I32))
        assertNotEquals(t1, t2)
    }

    @Test
    fun `FuncType inequality by results`() {
        val t1 = WasmModule.FuncType(listOf(WasmValueType.I32), listOf(WasmValueType.I32))
        val t2 = WasmModule.FuncType(listOf(WasmValueType.I32), listOf(WasmValueType.I64))
        assertNotEquals(t1, t2)
    }

    @Test
    fun `Export equality`() {
        val e1 = WasmModule.Export("f", WasmModule.ExportKind.FUNCTION, 0)
        val e2 = WasmModule.Export("f", WasmModule.ExportKind.FUNCTION, 0)
        assertEquals(e1, e2)
    }

    @Test
    fun `Import Func equality`() {
        val i1 = WasmModule.Import.Func("env", "f", 0)
        val i2 = WasmModule.Import.Func("env", "f", 0)
        assertEquals(i1, i2)
    }

    // Assembler round-trip consistency

    @Test
    fun `assemble and read produces consistent sizes`() {
        val asm = WasmAssembler.create()
        asm.memory("mem", 1)
        asm.global("g", WasmValueType.I32, mutable = true, initValue = 0)
        asm.table("tbl", WasmRefType.FUNCREF, 1)
        asm.importFunction("env", "log", listOf(WasmValueType.I32), emptyList())
        asm.dataSegment(0, 0, "test data".toByteArray())
        asm.function("f", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { fn, a ->
            a.localGet(fn.getParameter(0))
        }
        val module = WasmModuleReader.read(asm.assemble())
        assertEquals(1, module.memories.size)
        assertEquals(1, module.globals.size)
        assertEquals(1, module.tables.size)
        assertEquals(1, module.importedFunctionCount)
        assertEquals(1, module.dataSegments.size)
        assertEquals(1, module.functions.size)
        assertEquals(1, module.exports.size)
    }

    @Test
    fun `double round-trip produces identical bytes`() {
        val asm = WasmAssembler.create()
        asm.memory("mem", 1, 10)
        asm.global("g", WasmValueType.I32, mutable = true, initValue = 42)
        asm.function("f", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.i32Const(1)
            a.i32Add()
        }
        val original = asm.assemble()
        val m1 = WasmModuleReader.read(original)
        val rewritten1 = WasmModuleWriter.write(m1)
        val m2 = WasmModuleReader.read(rewritten1)
        val rewritten2 = WasmModuleWriter.write(m2)
        assertArrayEquals(rewritten1, rewritten2)
    }

    @Test
    fun `round-trip preserves import order`() {
        val asm = WasmAssembler.create()
        asm.importFunction("mod_a", "fn1", listOf(WasmValueType.I32), emptyList())
        asm.importFunction("mod_b", "fn2", listOf(WasmValueType.I64), listOf(WasmValueType.I64))
        asm.importMemory("mod_c", "mem", 1, 16)
        asm.importGlobal("mod_d", "g", WasmValueType.I32, mutable = false)
        asm.function("f", emptyList(), emptyList()) { _, _ -> }
        val (m1, m2) = roundTrip(asm)
        assertEquals(m1.imports.size, m2.imports.size)
        for (i in m1.imports.indices) {
            assertEquals(m1.imports[i].module, m2.imports[i].module)
            assertEquals(m1.imports[i].name, m2.imports[i].name)
        }
    }

    @Test
    fun `round-trip preserves export order`() {
        val asm = WasmAssembler.create()
        asm.function("alpha", emptyList(), emptyList(), exported = true) { _, _ -> }
        asm.function("beta", emptyList(), emptyList(), exported = true) { _, _ -> }
        asm.function("gamma", emptyList(), emptyList(), exported = true) { _, _ -> }
        val (m1, m2) = roundTrip(asm)
        for (i in m1.exports.indices) {
            assertEquals(m1.exports[i].name, m2.exports[i].name)
        }
    }

    @Test
    fun `WasmModule copy preserves all fields`() {
        val module = WasmModule(
            version = 1,
            types = listOf(WasmModule.FuncType(listOf(WasmValueType.I32), listOf(WasmValueType.I32))),
            imports = listOf(WasmModule.Import.Func("env", "f", 0)),
            functions = listOf(WasmModule.Function("main", 0, emptyList(), byteArrayOf(0x0B))),
            tables = listOf(WasmModule.Table(WasmRefType.FUNCREF, 1, 10)),
            memories = listOf(WasmModule.Memory(1, 256)),
            globals = listOf(WasmModule.Global(WasmValueType.I32, true, byteArrayOf(0x41, 0x00, 0x0B))),
            exports = listOf(WasmModule.Export("main", WasmModule.ExportKind.FUNCTION, 1)),
            start = 0,
            elements = emptyList(),
            dataSegments = emptyList(),
            customSections = emptyList(),
        )
        val copy = module.copy()
        assertEquals(module.version, copy.version)
        assertEquals(module.types, copy.types)
        assertEquals(module.imports, copy.imports)
        assertEquals(module.tables, copy.tables)
        assertEquals(module.memories, copy.memories)
        assertEquals(module.exports, copy.exports)
        assertEquals(module.start, copy.start)
    }
}
