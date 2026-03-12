package org.kgen.target.wasm.asm

import org.kgen.target.wasm.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WasmAssemblerTest {

    private fun assertValidWasm(bytes: ByteArray) {
        assertTrue(bytes.size >= 8, "Too short for a valid .wasm module")
        assertEquals(0x00, bytes[0].toInt() and 0xFF) // \0
        assertEquals(0x61, bytes[1].toInt() and 0xFF) // a
        assertEquals(0x73, bytes[2].toInt() and 0xFF) // s
        assertEquals(0x6D, bytes[3].toInt() and 0xFF) // m
        assertEquals(0x01, bytes[4].toInt() and 0xFF) // version 1
        assertEquals(0x00, bytes[5].toInt() and 0xFF)
        assertEquals(0x00, bytes[6].toInt() and 0xFF)
        assertEquals(0x00, bytes[7].toInt() and 0xFF)
    }

    @Test
    fun `empty module`() {
        val asm = WasmAssembler.create()
        val wasm = asm.assemble()
        assertValidWasm(wasm)
        assertEquals(8, wasm.size, "Empty module is just magic + version")
    }

    @Test
    fun `simple add function - callback style`() {
        val asm = WasmAssembler.create()
        asm.function(
            "add",
            listOf(WasmValueType.I32, WasmValueType.I32),
            listOf(WasmValueType.I32),
            exported = true,
        ) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.i32Add()
        }
        val wasm = asm.assemble()
        assertValidWasm(wasm)
        assertTrue(wasm.size > 8)
    }

    @Test
    fun `simple add function - flat style`() {
        val asm = WasmAssembler.create()
        val fn = asm.beginFunction("add", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true)
        asm.localGet(fn.getParameter(0))
        asm.localGet(fn.getParameter(1))
        asm.i32Add()
        asm.endFunction()
        val wasm = asm.assemble()
        assertValidWasm(wasm)
        assertTrue(wasm.size > 8)
    }

    @Test
    fun `callback and flat styles produce identical output`() {
        val asmCallback = WasmAssembler.create()
        asmCallback.function(
            "add",
            listOf(WasmValueType.I32, WasmValueType.I32),
            listOf(WasmValueType.I32),
            exported = true,
        ) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.i32Add()
        }
        val wasmCallback = asmCallback.assemble()

        val asmFlat = WasmAssembler.create()
        val fn = asmFlat.beginFunction("add", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32), exported = true)
        asmFlat.localGet(fn.getParameter(0))
        asmFlat.localGet(fn.getParameter(1))
        asmFlat.i32Add()
        asmFlat.endFunction()
        val wasmFlat = asmFlat.assemble()

        assertArrayEquals(wasmCallback, wasmFlat)
    }

    @Test
    fun `local variables`() {
        val asm = WasmAssembler.create()
        asm.function(
            "swap",
            listOf(WasmValueType.I32, WasmValueType.I32),
            listOf(WasmValueType.I32, WasmValueType.I32),
        ) { fn, a ->
            val tmp = a.declareLocal("tmp", WasmValueType.I32)
            a.localGet(fn.getParameter(0))
            a.localSet(tmp)
            a.localGet(fn.getParameter(1))
            a.localGet(tmp)
        }
        val wasm = asm.assemble()
        assertValidWasm(wasm)
    }

    @Test
    fun `if-then-else control flow`() {
        val asm = WasmAssembler.create()
        asm.function(
            "abs",
            listOf(WasmValueType.I32),
            listOf(WasmValueType.I32),
            exported = true,
        ) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.i32Const(0)
            a.i32LtS()
            a.ifThenElse(WasmBlockType.I32,
                thenBody = {
                    a.i32Const(0)
                    a.localGet(fn.getParameter(0))
                    a.i32Sub()
                },
                elseBody = {
                    a.localGet(fn.getParameter(0))
                }
            )
        }
        val wasm = asm.assemble()
        assertValidWasm(wasm)
    }

    @Test
    fun `block and branch`() {
        val asm = WasmAssembler.create()
        asm.function(
            "earlyReturn",
            listOf(WasmValueType.I32),
            listOf(WasmValueType.I32),
            exported = true,
        ) { fn, a ->
            a.block(WasmBlockType.I32) { label ->
                a.localGet(fn.getParameter(0))
                a.i32Const(0)
                a.i32Eqz()
                a.brIf(label)
                a.localGet(fn.getParameter(0))
                a.i32Const(2)
                a.i32Mul()
            }
        }
        val wasm = asm.assemble()
        assertValidWasm(wasm)
    }

    @Test
    fun `loop control flow`() {
        val asm = WasmAssembler.create()
        asm.function(
            "countDown",
            listOf(WasmValueType.I32),
            listOf(WasmValueType.I32),
            exported = true,
        ) { fn, a ->
            a.loop { loopLabel ->
                a.localGet(fn.getParameter(0))
                a.i32Const(0)
                a.i32GtS()
                a.ifThen {
                    a.localGet(fn.getParameter(0))
                    a.i32Const(1)
                    a.i32Sub()
                    a.localSet(fn.getParameter(0))
                    a.br(loopLabel)
                }
            }
            a.localGet(fn.getParameter(0))
        }
        val wasm = asm.assemble()
        assertValidWasm(wasm)
    }

    @Test
    fun `memory and data segment`() {
        val asm = WasmAssembler.create()
        asm.memory("memory", 1, exported = true)
        asm.dataSegment(0, 0, "Hello".toByteArray())
        asm.function(
            "loadByte",
            listOf(WasmValueType.I32),
            listOf(WasmValueType.I32),
            exported = true,
        ) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.i32Load8U(0, 0)
        }
        val wasm = asm.assemble()
        assertValidWasm(wasm)
    }

    @Test
    fun `import function`() {
        val asm = WasmAssembler.create()
        asm.importFunction("env", "log", listOf(WasmValueType.I32), emptyList())
        asm.function(
            "callLog",
            listOf(WasmValueType.I32),
            emptyList(),
            exported = true,
        ) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.call("log")
        }
        val wasm = asm.assemble()
        assertValidWasm(wasm)
    }

    @Test
    fun `global variable`() {
        val asm = WasmAssembler.create()
        asm.global("counter", WasmValueType.I32, mutable = true, initValue = 0, exported = true)
        asm.function(
            "increment",
            emptyList(),
            listOf(WasmValueType.I32),
            exported = true,
        ) { _, a ->
            a.globalGet(0)
            a.i32Const(1)
            a.i32Add()
            a.globalSet(0)
            a.globalGet(0)
        }
        val wasm = asm.assemble()
        assertValidWasm(wasm)
    }

    @Test
    fun `multiple functions with call`() {
        val asm = WasmAssembler.create()
        asm.function("double", listOf(WasmValueType.I32), listOf(WasmValueType.I32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.i32Const(2)
            a.i32Mul()
        }
        asm.function("quadruple", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.call("double")
            a.call("double")
        }
        val wasm = asm.assemble()
        assertValidWasm(wasm)
    }

    @Test
    fun `reset and reuse`() {
        val asm = WasmAssembler.create()
        asm.function("a", emptyList(), listOf(WasmValueType.I32)) { _, a -> a.i32Const(1) }
        val wasm1 = asm.assemble()

        asm.reset()
        asm.function("b", emptyList(), listOf(WasmValueType.I32)) { _, a -> a.i32Const(2) }
        val wasm2 = asm.assemble()

        assertValidWasm(wasm1)
        assertValidWasm(wasm2)
        assertFalse(wasm1.contentEquals(wasm2))
    }

    @Test
    fun `float operations`() {
        val asm = WasmAssembler.create()
        asm.function(
            "addF64",
            listOf(WasmValueType.F64, WasmValueType.F64),
            listOf(WasmValueType.F64),
            exported = true,
        ) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.f64Add()
        }
        val wasm = asm.assemble()
        assertValidWasm(wasm)
    }

    @Test
    fun `i64 constant`() {
        val asm = WasmAssembler.create()
        asm.function("bigConst", emptyList(), listOf(WasmValueType.I64), exported = true) { _, a ->
            a.i64Const(Long.MAX_VALUE)
        }
        val wasm = asm.assemble()
        assertValidWasm(wasm)
    }

    @Test
    fun `export function separately`() {
        val asm = WasmAssembler.create()
        asm.function("internal", emptyList(), listOf(WasmValueType.I32)) { _, a ->
            a.i32Const(42)
        }
        asm.exportFunction("answer", "internal")
        val wasm = asm.assemble()
        assertValidWasm(wasm)
    }

    @Test
    fun `unclosed function throws`() {
        val asm = WasmAssembler.create()
        asm.beginFunction("f", emptyList(), emptyList())
        assertThrows(IllegalStateException::class.java) { asm.assemble() }
    }

    @Test
    fun `nested function throws`() {
        val asm = WasmAssembler.create()
        asm.beginFunction("f", emptyList(), emptyList())
        assertThrows(IllegalStateException::class.java) {
            asm.beginFunction("g", emptyList(), emptyList())
        }
    }

    @Test
    fun `type deduplication`() {
        val asm = WasmAssembler.create()
        asm.function("a", listOf(WasmValueType.I32), listOf(WasmValueType.I32)) { fn, a ->
            a.localGet(fn.getParameter(0))
        }
        asm.function("b", listOf(WasmValueType.I32), listOf(WasmValueType.I32)) { fn, a ->
            a.localGet(fn.getParameter(0))
        }
        asm.function("c", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32)) { fn, a ->
            a.localGet(fn.getParameter(0))
        }
        val wasm = asm.assemble()
        assertValidWasm(wasm)
        // Count type section entries: should have 2 types (i32->i32 shared by a,b, and i32,i32->i32 for c)
        val typeSectionIdx = 8 // after magic+version
        assertEquals(1, wasm[typeSectionIdx].toInt() and 0xFF, "Section ID should be 1 (Type)")
        val typeSectionLen = wasm[typeSectionIdx + 1].toInt() and 0xFF
        assertEquals(2, wasm[typeSectionIdx + 2].toInt() and 0xFF, "Should have 2 type entries")
    }
}
