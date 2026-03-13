package org.kgen.target.wasm.disasm

import org.kgen.target.wasm.*
import org.kgen.target.wasm.asm.*
import org.kgen.target.wasm.module.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.instructions.*

class WasmDisassemblerExtraTest {

    private val disasm = WasmDisassembler()

    // --- i32 arithmetic not covered in existing tests ---

    @Test
    fun `disassembles i32 division and remainder`() {
        val asm = WasmAssembler.create()
        asm.function("divrem", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.i32DivS()
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.i32DivU()
            a.i32Add()
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.i32RemS()
            a.i32Add()
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.i32RemU()
            a.i32Add()
        }
        val module = WasmModuleReader.read(asm.assemble())
        val opcodes = disasm.disassemble(module.functions[0]).map { it.opcode }

        assertTrue(WasmOpCode.I32_DIV_S in opcodes)
        assertTrue(WasmOpCode.I32_DIV_U in opcodes)
        assertTrue(WasmOpCode.I32_REM_S in opcodes)
        assertTrue(WasmOpCode.I32_REM_U in opcodes)
    }

    @Test
    fun `disassembles i32 rotate operations`() {
        val asm = WasmAssembler.create()
        asm.function("rotate", listOf(WasmValueType.I32), listOf(WasmValueType.I32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.i32Const(3)
            a.i32Rotl()
            a.i32Const(5)
            a.i32Rotr()
        }
        val module = WasmModuleReader.read(asm.assemble())
        val opcodes = disasm.disassemble(module.functions[0]).map { it.opcode }

        assertTrue(WasmOpCode.I32_ROTL in opcodes)
        assertTrue(WasmOpCode.I32_ROTR in opcodes)
    }

    // --- i64 unary operations ---

    @Test
    fun `disassembles i64 clz ctz popcnt`() {
        val asm = WasmAssembler.create()
        asm.function("i64unary", listOf(WasmValueType.I64), listOf(WasmValueType.I64)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.i64Clz()
            a.drop()
            a.localGet(fn.getParameter(0))
            a.i64Ctz()
            a.drop()
            a.localGet(fn.getParameter(0))
            a.i64Popcnt()
        }
        val module = WasmModuleReader.read(asm.assemble())
        val opcodes = disasm.disassemble(module.functions[0]).map { it.opcode }

        assertTrue(WasmOpCode.I64_CLZ in opcodes)
        assertTrue(WasmOpCode.I64_CTZ in opcodes)
        assertTrue(WasmOpCode.I64_POPCNT in opcodes)
    }

    @Test
    fun `disassembles i64 rotate operations`() {
        val asm = WasmAssembler.create()
        asm.function("i64rot", listOf(WasmValueType.I64), listOf(WasmValueType.I64)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.i64Const(7)
            a.i64Rotl()
            a.i64Const(3)
            a.i64Rotr()
        }
        val module = WasmModuleReader.read(asm.assemble())
        val opcodes = disasm.disassemble(module.functions[0]).map { it.opcode }

        assertTrue(WasmOpCode.I64_ROTL in opcodes)
        assertTrue(WasmOpCode.I64_ROTR in opcodes)
    }

    @Test
    fun `disassembles i64 division and remainder`() {
        val asm = WasmAssembler.create()
        asm.function("i64divrem", listOf(WasmValueType.I64, WasmValueType.I64), listOf(WasmValueType.I64)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.i64DivU()
            a.localGet(fn.getParameter(0))
            a.i64RemS()
            a.localGet(fn.getParameter(0))
            a.i64RemU()
            a.i64Add()
        }
        val module = WasmModuleReader.read(asm.assemble())
        val opcodes = disasm.disassemble(module.functions[0]).map { it.opcode }

        assertTrue(WasmOpCode.I64_DIV_U in opcodes)
        assertTrue(WasmOpCode.I64_REM_S in opcodes)
        assertTrue(WasmOpCode.I64_REM_U in opcodes)
    }

    // --- f32/f64 min, max, floor, ceil ---

    @Test
    fun `disassembles f32 min and max`() {
        val asm = WasmAssembler.create()
        asm.function("f32mm", listOf(WasmValueType.F32, WasmValueType.F32), listOf(WasmValueType.F32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.f32Min()
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.f32Max()
            a.f32Add()
        }
        val module = WasmModuleReader.read(asm.assemble())
        val opcodes = disasm.disassemble(module.functions[0]).map { it.opcode }

        assertTrue(WasmOpCode.F32_MIN in opcodes)
        assertTrue(WasmOpCode.F32_MAX in opcodes)
    }

    @Test
    fun `disassembles f64 min and max`() {
        val asm = WasmAssembler.create()
        asm.function("f64mm", listOf(WasmValueType.F64, WasmValueType.F64), listOf(WasmValueType.F64)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.f64Min()
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.f64Max()
            a.f64Add()
        }
        val module = WasmModuleReader.read(asm.assemble())
        val opcodes = disasm.disassemble(module.functions[0]).map { it.opcode }

        assertTrue(WasmOpCode.F64_MIN in opcodes)
        assertTrue(WasmOpCode.F64_MAX in opcodes)
    }

    @Test
    fun `disassembles f32 floor and ceil`() {
        val asm = WasmAssembler.create()
        asm.function("f32fc", listOf(WasmValueType.F32), listOf(WasmValueType.F32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.f32Floor()
            a.drop()
            a.localGet(fn.getParameter(0))
            a.f32Ceil()
        }
        val module = WasmModuleReader.read(asm.assemble())
        val opcodes = disasm.disassemble(module.functions[0]).map { it.opcode }

        assertTrue(WasmOpCode.F32_FLOOR in opcodes)
        assertTrue(WasmOpCode.F32_CEIL in opcodes)
    }

    @Test
    fun `disassembles f64 floor and ceil`() {
        val asm = WasmAssembler.create()
        asm.function("f64fc", listOf(WasmValueType.F64), listOf(WasmValueType.F64)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.f64Floor()
            a.drop()
            a.localGet(fn.getParameter(0))
            a.f64Ceil()
        }
        val module = WasmModuleReader.read(asm.assemble())
        val opcodes = disasm.disassemble(module.functions[0]).map { it.opcode }

        assertTrue(WasmOpCode.F64_FLOOR in opcodes)
        assertTrue(WasmOpCode.F64_CEIL in opcodes)
    }

    // --- Multi-byte 0xFC prefix: saturating truncation ---

    @Test
    fun `disassembles i32 trunc sat f32 instructions`() {
        val asm = WasmAssembler.create()
        asm.function("trunc_sat_f32", listOf(WasmValueType.F32), listOf(WasmValueType.I32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.i32TruncSatF32S()
            a.drop()
            a.localGet(fn.getParameter(0))
            a.i32TruncSatF32U()
        }
        val module = WasmModuleReader.read(asm.assemble())
        val opcodes = disasm.disassemble(module.functions[0]).map { it.opcode }

        assertTrue(WasmOpCode.I32_TRUNC_SAT_F32_S in opcodes)
        assertTrue(WasmOpCode.I32_TRUNC_SAT_F32_U in opcodes)
    }

    @Test
    fun `disassembles i32 trunc sat f64 instructions`() {
        val asm = WasmAssembler.create()
        asm.function("trunc_sat_f64", listOf(WasmValueType.F64), listOf(WasmValueType.I32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.i32TruncSatF64S()
            a.drop()
            a.localGet(fn.getParameter(0))
            a.i32TruncSatF64U()
        }
        val module = WasmModuleReader.read(asm.assemble())
        val opcodes = disasm.disassemble(module.functions[0]).map { it.opcode }

        assertTrue(WasmOpCode.I32_TRUNC_SAT_F64_S in opcodes)
        assertTrue(WasmOpCode.I32_TRUNC_SAT_F64_U in opcodes)
    }

    @Test
    fun `disassembles i64 trunc sat instructions`() {
        val asm = WasmAssembler.create()
        asm.function("trunc_sat_i64", listOf(WasmValueType.F64), listOf(WasmValueType.I64)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.i64TruncSatF64S()
            a.drop()
            a.localGet(fn.getParameter(0))
            a.i64TruncSatF64U()
        }
        val module = WasmModuleReader.read(asm.assemble())
        val opcodes = disasm.disassemble(module.functions[0]).map { it.opcode }

        assertTrue(WasmOpCode.I64_TRUNC_SAT_F64_S in opcodes)
        assertTrue(WasmOpCode.I64_TRUNC_SAT_F64_U in opcodes)
    }

    // --- 0xFC prefix: bulk memory ---

    @Test
    fun `disassembles memory copy instruction`() {
        val asm = WasmAssembler.create()
        asm.memory("mem", 1)
        asm.function("copy", emptyList(), emptyList()) { _, a ->
            a.i32Const(0)   // dest
            a.i32Const(100) // src
            a.i32Const(50)  // len
            a.memoryCopy(0, 0)
        }
        val module = WasmModuleReader.read(asm.assemble())
        val instructions = disasm.disassemble(module.functions[0])
        val copyInst = instructions.first { it.opcode == WasmOpCode.MEMORY_COPY }

        assertTrue(copyInst.operands is WasmInstruction.Operands.TwoIndex)
        val ops = copyInst.operands as WasmInstruction.Operands.TwoIndex
        assertEquals(0, ops.first)
        assertEquals(0, ops.second)
    }

    @Test
    fun `disassembles memory fill instruction`() {
        val asm = WasmAssembler.create()
        asm.memory("mem", 1)
        asm.function("fill", emptyList(), emptyList()) { _, a ->
            a.i32Const(0)   // dest
            a.i32Const(0)   // value
            a.i32Const(256) // len
            a.memoryFill(0)
        }
        val module = WasmModuleReader.read(asm.assemble())
        val instructions = disasm.disassemble(module.functions[0])

        assertTrue(instructions.any { it.opcode == WasmOpCode.MEMORY_FILL })
    }

    // --- Sign extension instructions ---

    @Test
    fun `disassembles i32 sign extension instructions`() {
        val asm = WasmAssembler.create()
        asm.function("signext32", listOf(WasmValueType.I32), listOf(WasmValueType.I32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.i32Extend8S()
            a.drop()
            a.localGet(fn.getParameter(0))
            a.i32Extend16S()
        }
        val module = WasmModuleReader.read(asm.assemble())
        val opcodes = disasm.disassemble(module.functions[0]).map { it.opcode }

        assertTrue(WasmOpCode.I32_EXTEND8_S in opcodes)
        assertTrue(WasmOpCode.I32_EXTEND16_S in opcodes)
    }

    @Test
    fun `disassembles i64 sign extension instructions`() {
        val asm = WasmAssembler.create()
        asm.function("signext64", listOf(WasmValueType.I64), listOf(WasmValueType.I64)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.i64Extend8S()
            a.drop()
            a.localGet(fn.getParameter(0))
            a.i64Extend16S()
            a.drop()
            a.localGet(fn.getParameter(0))
            a.i64Extend32S()
        }
        val module = WasmModuleReader.read(asm.assemble())
        val opcodes = disasm.disassemble(module.functions[0]).map { it.opcode }

        assertTrue(WasmOpCode.I64_EXTEND8_S in opcodes)
        assertTrue(WasmOpCode.I64_EXTEND16_S in opcodes)
        assertTrue(WasmOpCode.I64_EXTEND32_S in opcodes)
    }

    // --- Control flow with typed blocks ---

    @Test
    fun `disassembles block with i32 result type`() {
        val asm = WasmAssembler.create()
        asm.function("typed_block", emptyList(), listOf(WasmValueType.I32)) { _, a ->
            a.block(0x7F) // i32 result
            a.i32Const(42)
            a.br(0)
            a.end()
        }
        val module = WasmModuleReader.read(asm.assemble())
        val instructions = disasm.disassemble(module.functions[0])

        val blockInst = instructions.first { it.opcode == WasmOpCode.BLOCK }
        assertTrue(blockInst.operands is WasmInstruction.Operands.BlockType)
        val bt = blockInst.operands as WasmInstruction.Operands.BlockType
        assertEquals(-1, bt.type) // 0x7F signed = -1 = i32
    }

    @Test
    fun `disassembles loop with void type`() {
        val asm = WasmAssembler.create()
        asm.function("void_loop", listOf(WasmValueType.I32), emptyList()) { fn, a ->
            val i = a.declareLocal("i", WasmValueType.I32)
            a.localGet(fn.getParameter(0))
            a.localSet(i)
            a.loop(0x40) // void
            a.localGet(i)
            a.i32Const(1)
            a.i32Sub()
            a.localTee(i)
            a.i32Const(0)
            a.i32GtS()
            a.brIf(0)
            a.end()
        }
        val module = WasmModuleReader.read(asm.assemble())
        val instructions = disasm.disassemble(module.functions[0])

        val loopInst = instructions.first { it.opcode == WasmOpCode.LOOP }
        val bt = loopInst.operands as WasmInstruction.Operands.BlockType
        assertEquals(-64, bt.type) // 0x40 signed = -64 = void block type
    }

    // --- Memory instructions with various alignments and offsets ---

    @Test
    fun `disassembles i64 sub-word loads`() {
        val asm = WasmAssembler.create()
        asm.memory("mem", 1)
        asm.function("i64subloads", listOf(WasmValueType.I32), listOf(WasmValueType.I64)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.i64Load8S(0, 0)
            a.drop()
            a.localGet(fn.getParameter(0))
            a.i64Load8U(0, 1)
            a.drop()
            a.localGet(fn.getParameter(0))
            a.i64Load16S(1, 2)
            a.drop()
            a.localGet(fn.getParameter(0))
            a.i64Load16U(1, 4)
            a.drop()
            a.localGet(fn.getParameter(0))
            a.i64Load32S(2, 8)
            a.drop()
            a.localGet(fn.getParameter(0))
            a.i64Load32U(2, 12)
        }
        val module = WasmModuleReader.read(asm.assemble())
        val opcodes = disasm.disassemble(module.functions[0]).map { it.opcode }

        assertTrue(WasmOpCode.I64_LOAD8_S in opcodes)
        assertTrue(WasmOpCode.I64_LOAD8_U in opcodes)
        assertTrue(WasmOpCode.I64_LOAD16_S in opcodes)
        assertTrue(WasmOpCode.I64_LOAD16_U in opcodes)
        assertTrue(WasmOpCode.I64_LOAD32_S in opcodes)
        assertTrue(WasmOpCode.I64_LOAD32_U in opcodes)
    }

    @Test
    fun `disassembles i64 sub-word stores`() {
        val asm = WasmAssembler.create()
        asm.memory("mem", 1)
        asm.function("i64substores", listOf(WasmValueType.I32, WasmValueType.I64), emptyList()) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.i64Store8(0, 0)
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.i64Store16(1, 2)
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.i64Store32(2, 4)
        }
        val module = WasmModuleReader.read(asm.assemble())
        val opcodes = disasm.disassemble(module.functions[0]).map { it.opcode }

        assertTrue(WasmOpCode.I64_STORE8 in opcodes)
        assertTrue(WasmOpCode.I64_STORE16 in opcodes)
        assertTrue(WasmOpCode.I64_STORE32 in opcodes)
    }

    @Test
    fun `disassembles f32 and f64 load instructions`() {
        val asm = WasmAssembler.create()
        asm.memory("mem", 1)
        asm.function("floatloads", listOf(WasmValueType.I32), listOf(WasmValueType.F64)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.f32Load(2, 0)
            a.drop()
            a.localGet(fn.getParameter(0))
            a.f64Load(3, 8)
        }
        val module = WasmModuleReader.read(asm.assemble())
        val opcodes = disasm.disassemble(module.functions[0]).map { it.opcode }

        assertTrue(WasmOpCode.F32_LOAD in opcodes)
        assertTrue(WasmOpCode.F64_LOAD in opcodes)
    }

    // --- Round-trip: assemble -> disassemble -> verify ---

    @Test
    fun `round-trip complex function preserves all instructions`() {
        val asm = WasmAssembler.create()
        asm.memory("mem", 1)
        asm.global("g", WasmValueType.I32, mutable = true, initValue = 0)
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
            a.globalSet(0)
            a.globalGet(0)
        }
        val bytes = asm.assemble()
        val module = WasmModuleReader.read(bytes)
        val instructions = disasm.disassemble(module.functions[0])
        val opcodes = instructions.map { it.opcode }

        assertTrue(WasmOpCode.LOCAL_GET in opcodes)
        assertTrue(WasmOpCode.I32_ADD in opcodes)
        assertTrue(WasmOpCode.LOCAL_TEE in opcodes)
        assertTrue(WasmOpCode.I32_GT_S in opcodes)
        assertTrue(WasmOpCode.IF in opcodes)
        assertTrue(WasmOpCode.ELSE in opcodes)
        assertTrue(WasmOpCode.GLOBAL_SET in opcodes)
        assertTrue(WasmOpCode.GLOBAL_GET in opcodes)

        // Write, re-read, re-disassemble, should produce same opcodes
        val rewritten = WasmModuleWriter.write(module)
        val module2 = WasmModuleReader.read(rewritten)
        val instructions2 = disasm.disassemble(module2.functions[0])
        assertEquals(instructions.map { it.opcode }, instructions2.map { it.opcode })
    }

    @Test
    fun `round-trip arithmetic function preserves operand values`() {
        val asm = WasmAssembler.create()
        asm.function("arith", emptyList(), listOf(WasmValueType.I32), exported = true) { _, a ->
            a.i32Const(10)
            a.i32Const(20)
            a.i32Add()
            a.i32Const(3)
            a.i32Mul()
            a.i32Const(7)
            a.i32Sub()
        }
        val bytes = asm.assemble()
        val module = WasmModuleReader.read(bytes)
        val instructions = disasm.disassemble(module.functions[0])

        val consts = instructions.filter { it.opcode == WasmOpCode.I32_CONST }
        assertEquals(4, consts.size)
        assertEquals(10, (consts[0].operands as WasmInstruction.Operands.I32).value)
        assertEquals(20, (consts[1].operands as WasmInstruction.Operands.I32).value)
        assertEquals(3, (consts[2].operands as WasmInstruction.Operands.I32).value)
        assertEquals(7, (consts[3].operands as WasmInstruction.Operands.I32).value)

        // Round-trip preserves values
        val rewritten = WasmModuleWriter.write(module)
        val module2 = WasmModuleReader.read(rewritten)
        val instructions2 = disasm.disassemble(module2.functions[0])
        val consts2 = instructions2.filter { it.opcode == WasmOpCode.I32_CONST }
        assertEquals(consts.map { (it.operands as WasmInstruction.Operands.I32).value },
            consts2.map { (it.operands as WasmInstruction.Operands.I32).value })
    }

    @Test
    fun `text output for saturating truncation is readable`() {
        val asm = WasmAssembler.create()
        asm.function("sat", listOf(WasmValueType.F32), listOf(WasmValueType.I32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.i32TruncSatF32S()
        }
        val module = WasmModuleReader.read(asm.assemble())
        val instructions = disasm.disassemble(module.functions[0])
        val text = instructions.joinToString("\n") { it.text() }

        assertTrue(text.contains("i32.trunc_sat_f32_s"))
    }

    @Test
    fun `text output for memory instructions includes alignment and offset`() {
        val asm = WasmAssembler.create()
        asm.memory("mem", 1)
        asm.function("memtext", listOf(WasmValueType.I32), listOf(WasmValueType.I32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.i32Load(2, 64)
        }
        val module = WasmModuleReader.read(asm.assemble())
        val instructions = disasm.disassemble(module.functions[0])
        val loadInst = instructions.first { it.opcode == WasmOpCode.I32_LOAD }
        val text = loadInst.text()

        assertTrue(text.contains("i32.load"))
        assertTrue(text.contains("align=2"))
        assertTrue(text.contains("offset=64"))
    }

    @Test
    fun `disassembles f64 special values`() {
        val asm = WasmAssembler.create()
        asm.function("f64specials", emptyList(), listOf(WasmValueType.F64)) { _, a ->
            a.f64Const(Double.POSITIVE_INFINITY)
            a.drop()
            a.f64Const(Double.NEGATIVE_INFINITY)
            a.drop()
            a.f64Const(0.0)
        }
        val module = WasmModuleReader.read(asm.assemble())
        val instructions = disasm.disassemble(module.functions[0])
        val f64Instructions = instructions.filter { it.opcode == WasmOpCode.F64_CONST }

        assertEquals(3, f64Instructions.size)
        assertEquals(Double.POSITIVE_INFINITY, (f64Instructions[0].operands as WasmInstruction.Operands.F64).value)
        assertEquals(Double.NEGATIVE_INFINITY, (f64Instructions[1].operands as WasmInstruction.Operands.F64).value)
        assertEquals(0.0, (f64Instructions[2].operands as WasmInstruction.Operands.F64).value)
    }
}
