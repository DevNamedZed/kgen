package org.kgen.backend.wasm.module

import org.kgen.backend.wasm.*
import org.kgen.backend.wasm.asm.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertThrows

class WasmModuleReaderExtendedTest {

    private fun assemble(block: WasmAssembler.() -> Unit): ByteArray {
        val asm = WasmAssembler.create()
        asm.block()
        return asm.assemble()
    }

    private fun readModule(block: WasmAssembler.() -> Unit): WasmModule {
        return WasmModuleReader.read(assemble(block))
    }

    // --- Type section ---

    @Test
    fun typeSectionWithMultipleDistinctSignatures() {
        val module = readModule {
            function("f1", listOf(WasmValueType.I32), listOf(WasmValueType.I32)) { fn, a ->
                a.localGet(fn.getParameter(0))
            }
            function("f2", listOf(WasmValueType.I64), listOf(WasmValueType.I64)) { fn, a ->
                a.localGet(fn.getParameter(0))
            }
            function("f3", emptyList(), emptyList()) { _, _ -> }
        }
        assertTrue(module.types.size >= 3, "Should have at least 3 distinct types: ${module.types}")
    }

    @Test
    fun typeDeduplicationWithIdenticalSignatures() {
        val module = readModule {
            function("a", listOf(WasmValueType.I32), listOf(WasmValueType.I32)) { fn, a ->
                a.localGet(fn.getParameter(0))
            }
            function("b", listOf(WasmValueType.I32), listOf(WasmValueType.I32)) { fn, a ->
                a.localGet(fn.getParameter(0))
            }
            function("c", listOf(WasmValueType.I32), listOf(WasmValueType.I32)) { fn, a ->
                a.localGet(fn.getParameter(0))
            }
        }
        assertEquals(1, module.types.size, "Identical signatures should be deduplicated")
        assertEquals(module.functions[0].typeIndex, module.functions[1].typeIndex)
        assertEquals(module.functions[1].typeIndex, module.functions[2].typeIndex)
    }

    // --- Function section ---

    @Test
    fun functionCountMatchesDefinedFunctions() {
        val module = readModule {
            for (i in 0 until 10) {
                function("f$i", emptyList(), emptyList()) { _, _ -> }
            }
        }
        assertEquals(10, module.functions.size)
    }

    @Test
    fun functionBodiesAreNonEmpty() {
        val module = readModule {
            function("f", listOf(WasmValueType.I32), listOf(WasmValueType.I32)) { fn, a ->
                a.localGet(fn.getParameter(0))
                a.localGet(fn.getParameter(0))
                a.i32Add()
            }
        }
        assertTrue(module.functions[0].body.isNotEmpty())
        assertEquals(0x0B, module.functions[0].body.last().toInt() and 0xFF)
    }

    @Test
    fun functionWithManyParams() {
        val params = listOf(
            WasmValueType.I32, WasmValueType.I32, WasmValueType.I64,
            WasmValueType.F32, WasmValueType.F64
        )
        val module = readModule {
            function("multi", params, listOf(WasmValueType.I32)) { fn, a ->
                a.localGet(fn.getParameter(0))
            }
        }
        assertEquals(params, module.types[module.functions[0].typeIndex].params)
    }

    // --- Export section ---

    @Test
    fun exportedFunctionNames() {
        val module = readModule {
            function("alpha", emptyList(), emptyList(), exported = true) { _, _ -> }
            function("beta", emptyList(), emptyList(), exported = true) { _, _ -> }
            function("gamma", emptyList(), emptyList(), exported = true) { _, _ -> }
        }
        val names = module.exports.map { it.name }
        assertEquals(listOf("alpha", "beta", "gamma"), names)
    }

    @Test
    fun nonExportedFunctionsNotInExports() {
        val module = readModule {
            function("exported", emptyList(), emptyList(), exported = true) { _, _ -> }
            function("internal", emptyList(), emptyList()) { _, _ -> }
        }
        assertEquals(1, module.exports.size)
        assertEquals("exported", module.exports[0].name)
    }

    @Test
    fun exportKindsForMemoryGlobalTable() {
        val module = readModule {
            memory("mem", 1, exported = true)
            global("g", WasmValueType.I32, mutable = false, initValue = 0, exported = true)
            table("tbl", WasmRefType.FUNCREF, 1, exported = true)
            function("f", emptyList(), emptyList(), exported = true) { _, _ -> }
        }
        val kinds = module.exports.map { it.kind }.toSet()
        assertTrue(WasmModule.ExportKind.FUNCTION in kinds)
        assertTrue(WasmModule.ExportKind.MEMORY in kinds)
        assertTrue(WasmModule.ExportKind.GLOBAL in kinds)
        assertTrue(WasmModule.ExportKind.TABLE in kinds)
    }

    // --- Import section ---

    @Test
    fun importFunctionCountIsCorrect() {
        val module = readModule {
            importFunction("env", "a", listOf(WasmValueType.I32), emptyList())
            importFunction("env", "b", listOf(WasmValueType.I64), listOf(WasmValueType.I64))
            importFunction("env", "c", emptyList(), emptyList())
            function("f", emptyList(), emptyList()) { _, _ -> }
        }
        assertEquals(3, module.importedFunctionCount)
    }

    @Test
    fun importFunctionModuleAndName() {
        val module = readModule {
            importFunction("wasi_snapshot_preview1", "fd_write",
                listOf(WasmValueType.I32, WasmValueType.I32, WasmValueType.I32, WasmValueType.I32),
                listOf(WasmValueType.I32))
            function("f", emptyList(), emptyList()) { _, _ -> }
        }
        val imp = module.imports[0] as WasmModule.Import.Func
        assertEquals("wasi_snapshot_preview1", imp.module)
        assertEquals("fd_write", imp.name)
    }

    @Test
    fun importMemoryMinMax() {
        val module = readModule {
            importMemory("env", "memory", 2, 32)
            function("f", emptyList(), emptyList()) { _, _ -> }
        }
        val imp = module.imports[0] as WasmModule.Import.Memory
        assertEquals(2, imp.min)
        assertEquals(32, imp.max)
    }

    @Test
    fun importGlobalMutableAndImmutable() {
        val module = readModule {
            importGlobal("env", "mut_g", WasmValueType.I32, mutable = true)
            importGlobal("env", "const_g", WasmValueType.I64, mutable = false)
            function("f", emptyList(), emptyList()) { _, _ -> }
        }
        val mutImp = module.imports[0] as WasmModule.Import.Global
        assertTrue(mutImp.mutable)
        assertEquals(WasmValueType.I32, mutImp.type)
        val constImp = module.imports[1] as WasmModule.Import.Global
        assertFalse(constImp.mutable)
        assertEquals(WasmValueType.I64, constImp.type)
    }

    @Test
    fun importTableRefType() {
        val module = readModule {
            importTable("env", "tbl", WasmRefType.FUNCREF, 5, 50)
            function("f", emptyList(), emptyList()) { _, _ -> }
        }
        val imp = module.imports[0] as WasmModule.Import.Table
        assertEquals(WasmRefType.FUNCREF, imp.refType)
        assertEquals(5, imp.min)
        assertEquals(50, imp.max)
    }

    @Test
    fun mixedImportTypes() {
        val module = readModule {
            importFunction("env", "log", listOf(WasmValueType.I32), emptyList())
            importMemory("env", "memory", 1)
            importGlobal("env", "stack", WasmValueType.I32, mutable = true)
            importTable("env", "tbl", WasmRefType.FUNCREF, 0)
            function("f", emptyList(), emptyList()) { _, _ -> }
        }
        assertEquals(4, module.imports.size)
        assertEquals(1, module.importedFunctionCount)
        assertEquals(1, module.importedMemoryCount)
        assertEquals(1, module.importedGlobalCount)
        assertEquals(1, module.importedTableCount)
    }

    // --- Memory section ---

    @Test
    fun memoryWithMinOnly() {
        val module = readModule {
            memory("mem", 1)
            function("f", emptyList(), emptyList()) { _, _ -> }
        }
        assertEquals(1, module.memories.size)
        assertEquals(1, module.memories[0].min)
        assertNull(module.memories[0].max)
    }

    @Test
    fun memoryWithMinAndMax() {
        val module = readModule {
            memory("mem", 4, 256)
            function("f", emptyList(), emptyList()) { _, _ -> }
        }
        assertEquals(4, module.memories[0].min)
        assertEquals(256, module.memories[0].max)
    }

    // --- Global section ---

    @Test
    fun mutableGlobal() {
        val module = readModule {
            global("counter", WasmValueType.I32, mutable = true, initValue = 0)
            function("f", emptyList(), emptyList()) { _, _ -> }
        }
        assertEquals(1, module.globals.size)
        assertTrue(module.globals[0].mutable)
        assertEquals(WasmValueType.I32, module.globals[0].type)
    }

    @Test
    fun immutableGlobal() {
        val module = readModule {
            global("constant", WasmValueType.I32, mutable = false, initValue = 42)
            function("f", emptyList(), emptyList()) { _, _ -> }
        }
        assertFalse(module.globals[0].mutable)
    }

    @Test
    fun multipleGlobals() {
        val module = readModule {
            global("g1", WasmValueType.I32, mutable = true, initValue = 0)
            global("g2", WasmValueType.I64, mutable = false, initValue = 100L)
            function("f", emptyList(), emptyList()) { _, _ -> }
        }
        assertEquals(2, module.globals.size)
        assertEquals(WasmValueType.I32, module.globals[0].type)
        assertEquals(WasmValueType.I64, module.globals[1].type)
    }

    // --- Table section ---

    @Test
    fun tableFuncref() {
        val module = readModule {
            table("tbl", WasmRefType.FUNCREF, 10, 100)
            function("f", emptyList(), emptyList()) { _, _ -> }
        }
        assertEquals(1, module.tables.size)
        assertEquals(WasmRefType.FUNCREF, module.tables[0].refType)
        assertEquals(10, module.tables[0].min)
        assertEquals(100, module.tables[0].max)
    }

    @Test
    fun tableWithNoMax() {
        val module = readModule {
            table("tbl", WasmRefType.FUNCREF, 0)
            function("f", emptyList(), emptyList()) { _, _ -> }
        }
        assertEquals(0, module.tables[0].min)
        assertNull(module.tables[0].max)
    }

    // --- Data section ---

    @Test
    fun activeDataSegmentContent() {
        val module = readModule {
            memory("mem", 1)
            dataSegment(0, 0, "test data".toByteArray())
            function("f", emptyList(), emptyList()) { _, _ -> }
        }
        val seg = module.dataSegments[0] as WasmModule.DataSegment.Active
        assertEquals("test data", String(seg.data))
        assertEquals(0, seg.memoryIndex)
    }

    @Test
    fun multipleActiveDataSegments() {
        val module = readModule {
            memory("mem", 1)
            dataSegment(0, 0, "first".toByteArray())
            dataSegment(0, 100, "second".toByteArray())
            dataSegment(0, 200, "third".toByteArray())
            function("f", emptyList(), emptyList()) { _, _ -> }
        }
        assertEquals(3, module.dataSegments.size)
        assertEquals("first", String((module.dataSegments[0] as WasmModule.DataSegment.Active).data))
        assertEquals("second", String((module.dataSegments[1] as WasmModule.DataSegment.Active).data))
        assertEquals("third", String((module.dataSegments[2] as WasmModule.DataSegment.Active).data))
    }

    @Test
    fun passiveDataSegment() {
        val module = readModule {
            memory("mem", 1)
            dataSegment("passive content".toByteArray())
            function("f", emptyList(), emptyList()) { _, _ -> }
        }
        val seg = module.dataSegments[0] as WasmModule.DataSegment.Passive
        assertEquals("passive content", String(seg.data))
    }

    @Test
    fun largeDataSegment() {
        val data = ByteArray(8192) { (it % 256).toByte() }
        val module = readModule {
            memory("mem", 1)
            dataSegment(0, 0, data)
            function("f", emptyList(), emptyList()) { _, _ -> }
        }
        val seg = module.dataSegments[0] as WasmModule.DataSegment.Active
        assertArrayEquals(data, seg.data)
    }

    // --- Locals ---

    @Test
    fun functionWithMultipleLocalTypes() {
        val module = readModule {
            function("f", emptyList(), emptyList()) { _, a ->
                a.declareLocal(WasmValueType.I32)
                a.declareLocal(WasmValueType.I64)
                a.declareLocal(WasmValueType.F32)
                a.declareLocal(WasmValueType.F64)
            }
        }
        assertEquals(
            listOf(WasmValueType.I32, WasmValueType.I64, WasmValueType.F32, WasmValueType.F64),
            module.functions[0].locals
        )
    }

    @Test
    fun functionWithRepeatedLocalTypes() {
        val module = readModule {
            function("f", emptyList(), emptyList()) { _, a ->
                a.declareLocal(WasmValueType.I32)
                a.declareLocal(WasmValueType.I32)
                a.declareLocal(WasmValueType.I32)
            }
        }
        assertEquals(
            listOf(WasmValueType.I32, WasmValueType.I32, WasmValueType.I32),
            module.functions[0].locals
        )
    }

    @Test
    fun functionWithNamedLocals() {
        val module = readModule {
            function("f", listOf(WasmValueType.I32), listOf(WasmValueType.I32)) { fn, a ->
                val tmp = a.declareLocal("tmp", WasmValueType.I32)
                a.localGet(fn.getParameter(0))
                a.localSet(tmp)
                a.localGet(tmp)
            }
        }
        assertEquals(listOf(WasmValueType.I32), module.functions[0].locals)
    }

    // --- Function name resolution ---

    @Test
    fun functionNameFromExport() {
        val module = readModule {
            function("myFunc", emptyList(), emptyList(), exported = true) { _, _ -> }
        }
        assertEquals("myFunc", module.functions[0].name)
    }

    @Test
    fun functionNameForImportedFunction() {
        val module = readModule {
            importFunction("env", "log", listOf(WasmValueType.I32), emptyList())
            function("main", emptyList(), emptyList(), exported = true) { _, _ -> }
        }
        assertEquals("log", module.functionName(0))
        assertEquals("main", module.functionName(1))
    }

    @Test
    fun functionNameForInvalidIndex() {
        val module = readModule {
            function("f", emptyList(), emptyList()) { _, _ -> }
        }
        assertNull(module.functionName(999))
    }

    // --- All value types ---

    @Test
    fun allValueTypesInSignature() {
        val allTypes = listOf(WasmValueType.I32, WasmValueType.I64, WasmValueType.F32, WasmValueType.F64)
        val module = readModule {
            function("all", allTypes, listOf(WasmValueType.I32)) { fn, a ->
                a.localGet(fn.getParameter(0))
            }
        }
        assertEquals(allTypes, module.types[module.functions[0].typeIndex].params)
    }

    @Test
    fun multipleReturnValues() {
        val module = readModule {
            function("swap",
                listOf(WasmValueType.I32, WasmValueType.I64),
                listOf(WasmValueType.I64, WasmValueType.I32)) { fn, a ->
                a.localGet(fn.getParameter(1))
                a.localGet(fn.getParameter(0))
            }
        }
        val type = module.types[module.functions[0].typeIndex]
        assertEquals(2, type.results.size)
        assertEquals(WasmValueType.I64, type.results[0])
        assertEquals(WasmValueType.I32, type.results[1])
    }

    @Test
    fun voidFunction() {
        val module = readModule {
            function("noop", emptyList(), emptyList()) { _, _ -> }
        }
        val type = module.types[module.functions[0].typeIndex]
        assertTrue(type.params.isEmpty())
        assertTrue(type.results.isEmpty())
    }

    // --- Error handling ---

    @Test
    fun rejectsEmptyBytes() {
        assertThrows<Exception> { WasmModuleReader.read(byteArrayOf()) }
    }

    @Test
    fun rejectsTooShortBytes() {
        assertThrows<Exception> { WasmModuleReader.read(byteArrayOf(0x00, 0x61)) }
    }

    @Test
    fun rejectsBadMagic() {
        val bad = byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00)
        assertThrows<IllegalStateException> { WasmModuleReader.read(bad) }
    }

    @Test
    fun rejectsBadVersion() {
        val bad = byteArrayOf(0x00, 0x61, 0x73, 0x6D, 0x02, 0x00, 0x00, 0x00)
        assertThrows<IllegalStateException> { WasmModuleReader.read(bad) }
    }

    // --- Edge cases ---

    @Test
    fun minimalModuleNoSections() {
        val bytes = byteArrayOf(0x00, 0x61, 0x73, 0x6D, 0x01, 0x00, 0x00, 0x00)
        val module = WasmModuleReader.read(bytes)
        assertEquals(1, module.version)
        assertTrue(module.functions.isEmpty())
        assertTrue(module.imports.isEmpty())
        assertTrue(module.exports.isEmpty())
        assertTrue(module.globals.isEmpty())
        assertTrue(module.memories.isEmpty())
        assertTrue(module.tables.isEmpty())
        assertTrue(module.dataSegments.isEmpty())
    }

    @Test
    fun manyFunctionsRoundTrip() {
        val module = readModule {
            for (i in 0 until 100) {
                function("fn_$i", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { fn, a ->
                    a.localGet(fn.getParameter(0))
                    a.i32Const(i)
                    a.i32Add()
                }
            }
        }
        assertEquals(100, module.functions.size)
        assertEquals(100, module.exports.size)
        assertEquals("fn_0", module.exports[0].name)
        assertEquals("fn_99", module.exports[99].name)
    }

    @Test
    fun writeReadRoundTripPreservesStructure() {
        val bytes = assemble {
            importFunction("env", "log", listOf(WasmValueType.I32), emptyList())
            memory("mem", 1, 10)
            global("g", WasmValueType.I32, mutable = true, initValue = 0)
            dataSegment(0, 0, "hello".toByteArray())
            function("main", emptyList(), listOf(WasmValueType.I32), exported = true) { _, a ->
                a.i32Const(42)
                a.call("log")
                a.i32Const(0)
            }
        }
        val module = WasmModuleReader.read(bytes)
        val bytes2 = WasmModuleWriter.write(module)
        val module2 = WasmModuleReader.read(bytes2)

        assertEquals(module.functions.size, module2.functions.size)
        assertEquals(module.imports.size, module2.imports.size)
        assertEquals(module.exports.size, module2.exports.size)
        assertEquals(module.memories.size, module2.memories.size)
        assertEquals(module.globals.size, module2.globals.size)
        assertEquals(module.dataSegments.size, module2.dataSegments.size)
    }

    @Test
    fun importedFunctionIndicesAreBeforeDefinedFunctions() {
        val module = readModule {
            importFunction("env", "a", emptyList(), emptyList())
            importFunction("env", "b", emptyList(), emptyList())
            function("c", emptyList(), emptyList(), exported = true) { _, _ -> }
        }
        assertEquals("a", module.functionName(0))
        assertEquals("b", module.functionName(1))
        assertEquals("c", module.functionName(2))
    }

    @Test
    fun functionWithControlFlow() {
        val module = readModule {
            function("abs", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { fn, a ->
                a.localGet(fn.getParameter(0))
                a.i32Const(0)
                a.i32LtS()
                a.beginIf(WasmBlockType.I32)
                a.i32Const(0)
                a.localGet(fn.getParameter(0))
                a.i32Sub()
                a.beginElse()
                a.localGet(fn.getParameter(0))
                a.end()
            }
        }
        assertEquals(1, module.functions.size)
        assertTrue(module.functions[0].body.isNotEmpty())
    }

    @Test
    fun functionWithLoop() {
        val module = readModule {
            function("count", emptyList(), listOf(WasmValueType.I32), exported = true) { _, a ->
                val counter = a.declareLocal(WasmValueType.I32)
                a.i32Const(0)
                a.localSet(counter)
                a.beginBlock(WasmBlockType.Void)
                a.beginLoop(WasmBlockType.Void)
                a.localGet(counter)
                a.i32Const(1)
                a.i32Add()
                a.localSet(counter)
                a.localGet(counter)
                a.i32Const(10)
                a.i32GeU()
                a.brIf(1)
                a.br(0)
                a.end()
                a.end()
                a.localGet(counter)
            }
        }
        assertEquals(1, module.functions.size)
    }
}
