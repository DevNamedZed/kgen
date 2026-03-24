package org.kgen.target.wasm.asm

import org.kgen.target.wasm.WasmBlockType
import org.kgen.target.wasm.WasmValueType
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class WasmTypedLabelTest {

    @Test
    fun markBlockIsSameAsBeginBlock() {
        val asm1 = WasmAssembler.create()
        val fn1 = asm1.beginFunction("test", emptyList(), listOf(WasmValueType.I32), exported = true)
        val label1 = asm1.beginBlock(WasmBlockType.I32)
        asm1.i32Const(42)
        asm1.br(label1)
        asm1.endBlock()
        asm1.endFunction()

        val asm2 = WasmAssembler.create()
        val fn2 = asm2.beginFunction("test", emptyList(), listOf(WasmValueType.I32), exported = true)
        val label2 = asm2.markBlock(WasmBlockType.I32)
        asm2.i32Const(42)
        asm2.br(label2)
        asm2.endBlock()
        asm2.endFunction()

        assertArrayEquals(asm1.assemble(), asm2.assemble())
    }

    @Test
    fun markLoopIsSameAsBeginLoop() {
        val asm1 = WasmAssembler.create()
        val fn1 = asm1.beginFunction("test", emptyList(), emptyList(), exported = true)
        val blockLabel1 = asm1.beginBlock()
        val label1 = asm1.beginLoop()
        asm1.br(label1)
        asm1.endLoop()
        asm1.endBlock()
        asm1.endFunction()

        val asm2 = WasmAssembler.create()
        val fn2 = asm2.beginFunction("test", emptyList(), emptyList(), exported = true)
        val blockLabel2 = asm2.beginBlock()
        val label2 = asm2.markLoop()
        asm2.br(label2)
        asm2.endLoop()
        asm2.endBlock()
        asm2.endFunction()

        assertArrayEquals(asm1.assemble(), asm2.assemble())
    }

    @Test
    fun markIfIsSameAsBeginIf() {
        val asm1 = WasmAssembler.create()
        val fn1 = asm1.beginFunction("test", emptyList(), listOf(WasmValueType.I32), exported = true)
        asm1.i32Const(1)
        asm1.beginIf(WasmBlockType.I32)
        asm1.i32Const(42)
        asm1.beginElse()
        asm1.i32Const(0)
        asm1.endIf()
        asm1.endFunction()

        val asm2 = WasmAssembler.create()
        val fn2 = asm2.beginFunction("test", emptyList(), listOf(WasmValueType.I32), exported = true)
        asm2.i32Const(1)
        asm2.markIf(WasmBlockType.I32)
        asm2.i32Const(42)
        asm2.beginElse()
        asm2.i32Const(0)
        asm2.endIf()
        asm2.endFunction()

        assertArrayEquals(asm1.assemble(), asm2.assemble())
    }

    @Test
    fun markBlockDefaultsToVoid() {
        val asm1 = WasmAssembler.create()
        val fn1 = asm1.beginFunction("test", emptyList(), emptyList(), exported = true)
        asm1.beginBlock()
        asm1.endBlock()
        asm1.endFunction()

        val asm2 = WasmAssembler.create()
        val fn2 = asm2.beginFunction("test", emptyList(), emptyList(), exported = true)
        asm2.markBlock()
        asm2.endBlock()
        asm2.endFunction()

        assertArrayEquals(asm1.assemble(), asm2.assemble())
    }

    @Test
    fun markLoopWithBrIf() {
        val asm = WasmAssembler.create()
        val fn = asm.beginFunction("test", listOf(WasmValueType.I32), emptyList(), exported = true)
        val loop = asm.markLoop()
        asm.localGet(fn.getParameter(0))
        asm.i32Const(1)
        asm.i32Sub()
        asm.localTee(fn.getParameter(0))
        asm.brIf(loop)
        asm.endLoop()
        asm.endFunction()

        val bytes = asm.assemble()
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun markBlockWithBrIf() {
        val asm = WasmAssembler.create()
        val fn = asm.beginFunction("test", emptyList(), emptyList(), exported = true)
        val outer = asm.markBlock()
        asm.i32Const(1)
        asm.brIf(outer)
        asm.nop()
        asm.endBlock()
        asm.endFunction()

        val bytes = asm.assemble()
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun nestedMarkBlockAndMarkLoop() {
        val asm = WasmAssembler.create()
        val fn = asm.beginFunction("test", listOf(WasmValueType.I32), emptyList(), exported = true)
        val outer = asm.markBlock()
        val inner = asm.markLoop()
        asm.localGet(fn.getParameter(0))
        asm.i32Const(1)
        asm.i32Sub()
        asm.localTee(fn.getParameter(0))
        asm.brIf(inner)
        asm.endLoop()
        asm.endBlock()
        asm.endFunction()

        val bytes = asm.assemble()
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun markLoopCountdown() {
        val asm = WasmAssembler.create()
        val fn = asm.beginFunction("countdown", listOf(WasmValueType.I32), listOf(WasmValueType.I32), exported = true)
        val result = asm.declareLocal("result", WasmValueType.I32)
        asm.i32Const(0)
        asm.localSet(result)
        val loop = asm.markLoop()
        asm.localGet(fn.getParameter(0))
        asm.i32Const(0)
        asm.i32GtS()
        val check = asm.beginIf()
        asm.localGet(result)
        asm.localGet(fn.getParameter(0))
        asm.i32Add()
        asm.localSet(result)
        asm.localGet(fn.getParameter(0))
        asm.i32Const(1)
        asm.i32Sub()
        asm.localSet(fn.getParameter(0))
        asm.br(loop)
        asm.endIf()
        asm.endLoop()
        asm.localGet(result)
        asm.endFunction()

        val bytes = asm.assemble()
        assertTrue(bytes.isNotEmpty())
    }
}
