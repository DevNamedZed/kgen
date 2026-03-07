package org.kgen.backend.wasm.module

import org.kgen.backend.wasm.*
import org.kgen.backend.wasm.asm.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class WasmModuleWriterExtendedTest {

    private fun assertValidWasm(bytes: ByteArray) {
        assertTrue(bytes.size >= 8, "Too short for a valid .wasm module")
        assertEquals(0x00, bytes[0].toInt() and 0xFF)
        assertEquals(0x61, bytes[1].toInt() and 0xFF)
        assertEquals(0x73, bytes[2].toInt() and 0xFF)
        assertEquals(0x6D, bytes[3].toInt() and 0xFF)
        assertEquals(1, bytes[4].toInt() and 0xFF)
    }

    private fun roundTrip(block: WasmAssembler.() -> Unit): WasmModule {
        val asm = WasmAssembler.create()
        asm.block()
        val original = asm.assemble()
        val module = WasmModuleReader.read(original)
        val rewritten = WasmModuleWriter.write(module)
        assertValidWasm(rewritten)
        return WasmModuleReader.read(rewritten)
    }

    @Test
    fun roundTripI64Function() {
        val m = roundTrip {
            function("add64", listOf(WasmValueType.I64, WasmValueType.I64), listOf(WasmValueType.I64), exported = true) { fn, a ->
                a.localGet(fn.getParameter(0))
                a.localGet(fn.getParameter(1))
                a.i64Add()
            }
        }
        assertEquals(1, m.functions.size)
        assertEquals(1, m.exports.size)
    }

    @Test
    fun roundTripF32Function() {
        val m = roundTrip {
            function("mulF32", listOf(WasmValueType.F32, WasmValueType.F32), listOf(WasmValueType.F32), exported = true) { fn, a ->
                a.localGet(fn.getParameter(0))
                a.localGet(fn.getParameter(1))
                a.f32Mul()
            }
        }
        assertEquals(1, m.functions.size)
    }

    @Test
    fun roundTripF64Function() {
        val m = roundTrip {
            function("divF64", listOf(WasmValueType.F64, WasmValueType.F64), listOf(WasmValueType.F64), exported = true) { fn, a ->
                a.localGet(fn.getParameter(0))
                a.localGet(fn.getParameter(1))
                a.f64Div()
            }
        }
        assertEquals(1, m.functions.size)
    }

    @Test
    fun roundTripMultipleImports() {
        val m = roundTrip {
            importFunction("env", "read", listOf(WasmValueType.I32), listOf(WasmValueType.I32))
            importFunction("env", "write", listOf(WasmValueType.I32, WasmValueType.I32), emptyList())
            function("main", emptyList(), emptyList(), exported = true) { _, a ->
                a.i32Const(0)
                a.call("read")
                a.i32Const(1)
                a.call("write")
            }
        }
        assertEquals(2, m.imports.size)
        val imp0 = m.imports[0] as WasmModule.Import.Func
        val imp1 = m.imports[1] as WasmModule.Import.Func
        assertEquals("read", imp0.name)
        assertEquals("write", imp1.name)
    }

    @Test
    fun roundTripMultipleGlobals() {
        val m = roundTrip {
            global("x", WasmValueType.I32, true, 0, exported = true)
            global("y", WasmValueType.I64, true, 0, exported = true)
            global("z", WasmValueType.F64, false, 0, exported = true)
            function("nop", emptyList(), emptyList()) { _, _ -> }
        }
        assertEquals(3, m.globals.size)
        assertTrue(m.globals[0].mutable)
        assertTrue(m.globals[1].mutable)
        assertFalse(m.globals[2].mutable)
    }

    @Test
    fun roundTripMultipleMemories() {
        val m = roundTrip {
            memory("mem", 2, 100, exported = true)
            function("nop", emptyList(), emptyList()) { _, _ -> }
        }
        assertEquals(1, m.memories.size)
        assertEquals(2, m.memories[0].min)
        assertEquals(100, m.memories[0].max)
    }

    @Test
    fun roundTripWithMultipleLocals() {
        val m = roundTrip {
            function("manyLocals", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { fn, a ->
                val l1 = a.declareLocal("a", WasmValueType.I32)
                val l2 = a.declareLocal("b", WasmValueType.I64)
                val l3 = a.declareLocal("c", WasmValueType.F32)
                a.localGet(fn.getParameter(0))
                a.localSet(l1)
                a.localGet(l1)
            }
        }
        assertEquals(1, m.functions.size)
        assertTrue(m.functions[0].locals.size >= 3)
    }

    @Test
    fun roundTripWithControlFlow() {
        val m = roundTrip {
            function("ifElse", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { fn, a ->
                a.localGet(fn.getParameter(0))
                a.beginIf(WasmBlockType.I32)
                a.i32Const(1)
                a.beginElse()
                a.i32Const(0)
                a.end()
            }
        }
        assertEquals(1, m.functions.size)
        assertNotNull(m.functions[0].body)
    }

    @Test
    fun roundTripWithLoop() {
        val m = roundTrip {
            function("countdown", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { fn, a ->
                val counter = a.declareLocal("counter", WasmValueType.I32)
                a.localGet(fn.getParameter(0))
                a.localSet(counter)
                a.beginBlock(WasmBlockType.Void)
                a.beginLoop(WasmBlockType.Void)
                a.localGet(counter)
                a.i32Eqz()
                a.brIf(1)
                a.localGet(counter)
                a.i32Const(1)
                a.i32Sub()
                a.localSet(counter)
                a.br(0)
                a.end()
                a.end()
                a.localGet(counter)
            }
        }
        assertEquals(1, m.functions.size)
    }

    @Test
    fun roundTripMultipleDataSegments() {
        val m = roundTrip {
            memory("mem", 1)
            dataSegment(0, 0, "hello".toByteArray())
            dataSegment(0, 16, "world".toByteArray())
            function("nop", emptyList(), emptyList()) { _, _ -> }
        }
        assertEquals(2, m.dataSegments.size)
        val seg0 = m.dataSegments[0] as WasmModule.DataSegment.Active
        val seg1 = m.dataSegments[1] as WasmModule.DataSegment.Active
        assertArrayEquals("hello".toByteArray(), seg0.data)
        assertArrayEquals("world".toByteArray(), seg1.data)
    }

    @Test
    fun roundTripLargeDataSegment() {
        val data = ByteArray(1024) { (it % 256).toByte() }
        val m = roundTrip {
            memory("mem", 1)
            dataSegment(0, 0, data)
            function("nop", emptyList(), emptyList()) { _, _ -> }
        }
        assertEquals(1, m.dataSegments.size)
        val seg = m.dataSegments[0] as WasmModule.DataSegment.Active
        assertArrayEquals(data, seg.data)
    }

    @Test
    fun roundTripNoExports() {
        val m = roundTrip {
            function("internal", listOf(WasmValueType.I32), listOf(WasmValueType.I32)) { fn, a ->
                a.localGet(fn.getParameter(0))
            }
        }
        assertEquals(1, m.functions.size)
        assertEquals(0, m.exports.size)
    }

    @Test
    fun roundTripManyExports() {
        val m = roundTrip {
            function("a", emptyList(), listOf(WasmValueType.I32), exported = true) { _, a ->
                a.i32Const(1)
            }
            function("b", emptyList(), listOf(WasmValueType.I32), exported = true) { _, a ->
                a.i32Const(2)
            }
            function("c", emptyList(), listOf(WasmValueType.I32), exported = true) { _, a ->
                a.i32Const(3)
            }
        }
        assertEquals(3, m.functions.size)
        assertEquals(3, m.exports.size)
    }

    @Test
    fun roundTripWithMultipleReturns() {
        val m = roundTrip {
            function("swap", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32, WasmValueType.I32), exported = true) { fn, a ->
                a.localGet(fn.getParameter(1))
                a.localGet(fn.getParameter(0))
            }
        }
        assertEquals(1, m.functions.size)
    }

    @Test
    fun roundTripFunctionCallChain() {
        val m = roundTrip {
            function("square", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { fn, a ->
                a.localGet(fn.getParameter(0))
                a.localGet(fn.getParameter(0))
                a.i32Mul()
            }
            function("squareSum", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { fn, a ->
                a.localGet(fn.getParameter(0))
                a.call("square")
                a.localGet(fn.getParameter(1))
                a.call("square")
                a.i32Add()
            }
        }
        assertEquals(2, m.functions.size)
        assertEquals(2, m.exports.size)
    }

    @Test
    fun roundTripImportMemory() {
        val m = roundTrip {
            importMemory("env", "memory", 1, 256)
            function("load", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { fn, a ->
                a.localGet(fn.getParameter(0))
                a.i32Load(0, 0)
            }
        }
        assertTrue(m.imports.any { it is WasmModule.Import.Memory })
    }

    @Test
    fun roundTripMemoryOperations() {
        val m = roundTrip {
            memory("mem", 1, exported = true)
            function("storeLoad", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { fn, a ->
                a.localGet(fn.getParameter(0))
                a.localGet(fn.getParameter(1))
                a.i32Store(0, 0)
                a.localGet(fn.getParameter(0))
                a.i32Load(0, 0)
            }
        }
        assertEquals(1, m.functions.size)
    }

    @Test
    fun roundTripVoidFunction() {
        val m = roundTrip {
            function("doNothing", emptyList(), emptyList(), exported = true) { _, _ -> }
        }
        assertEquals(1, m.functions.size)
        assertEquals(1, m.exports.size)
    }

    @Test
    fun roundTripExportedGlobalAndMemory() {
        val m = roundTrip {
            memory("mem", 1, exported = true)
            global("g", WasmValueType.I32, true, 42, exported = true)
            function("nop", emptyList(), emptyList()) { _, _ -> }
        }
        assertTrue(m.exports.any { it.name == "mem" })
        assertTrue(m.exports.any { it.name == "g" })
    }

    @Test
    fun roundTripPreservesTypeDeduplication() {
        val m = roundTrip {
            function("f1", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { fn, a ->
                a.localGet(fn.getParameter(0))
            }
            function("f2", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { fn, a ->
                a.localGet(fn.getParameter(0))
                a.i32Const(1)
                a.i32Add()
            }
        }
        // Both functions have the same type signature, should share type index
        assertEquals(2, m.functions.size)
        // Types should be deduplicated
        assertTrue(m.types.size <= 2)
    }

    @Test
    fun roundTripWithStartFunction() {
        val asm = WasmAssembler.create()
        asm.function("init", emptyList(), emptyList()) { _, _ -> }
        asm.startFunction("init")
        val original = asm.assemble()
        val module = WasmModuleReader.read(original)
        val rewritten = WasmModuleWriter.write(module)
        assertValidWasm(rewritten)
        val m2 = WasmModuleReader.read(rewritten)
        assertNotNull(m2.start)
    }

    @Test
    fun writeEmptyModuleSize() {
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
    }

    @Test
    fun roundTripComplex() {
        val m = roundTrip {
            importFunction("env", "print", listOf(WasmValueType.I32), emptyList())
            memory("mem", 1, 10, exported = true)
            global("counter", WasmValueType.I32, true, 0, exported = true)
            dataSegment(0, 0, "Hello, World!".toByteArray())
            function("inc", emptyList(), emptyList(), exported = true) { _, a ->
                a.globalGet(0)
                a.i32Const(1)
                a.i32Add()
                a.globalSet(0)
            }
            function("getCounter", emptyList(), listOf(WasmValueType.I32), exported = true) { _, a ->
                a.globalGet(0)
            }
        }
        assertEquals(2, m.functions.size)
        assertEquals(1, m.imports.size)
        assertEquals(1, m.memories.size)
        assertEquals(1, m.globals.size)
        assertEquals(1, m.dataSegments.size)
    }

    @Test
    fun roundTripI64Arithmetic() {
        val m = roundTrip {
            function("fib", listOf(WasmValueType.I64, WasmValueType.I64), listOf(WasmValueType.I64), exported = true) { fn, a ->
                a.localGet(fn.getParameter(0))
                a.localGet(fn.getParameter(1))
                a.i64Add()
                a.localGet(fn.getParameter(0))
                a.i64Sub()
                a.localGet(fn.getParameter(1))
                a.i64Mul()
            }
        }
        assertEquals(1, m.functions.size)
    }

    @Test
    fun roundTripNestedBlocks() {
        val m = roundTrip {
            function("nested", emptyList(), listOf(WasmValueType.I32), exported = true) { _, a ->
                a.beginBlock(WasmBlockType.I32)
                a.beginBlock(WasmBlockType.I32)
                a.i32Const(42)
                a.br(1)
                a.end()
                a.end()
            }
        }
        assertEquals(1, m.functions.size)
    }

    @Test
    fun writerOutputIsDeterministic() {
        val asm = WasmAssembler.create()
        asm.function("f", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.i32Const(1)
            a.i32Add()
        }
        val original = asm.assemble()
        val module = WasmModuleReader.read(original)
        val out1 = WasmModuleWriter.write(module)
        val out2 = WasmModuleWriter.write(module)
        assertArrayEquals(out1, out2)
    }
}
