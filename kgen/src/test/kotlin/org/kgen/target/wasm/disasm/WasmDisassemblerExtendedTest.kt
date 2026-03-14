package org.kgen.target.wasm.disasm

import org.kgen.target.wasm.*
import org.kgen.target.wasm.asm.*
import org.kgen.target.wasm.module.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class WasmDisassemblerExtendedTest {

    private val disasm = WasmDisassembler()

    @Test
    fun `disassembles i64 subtraction and division`() {
        val asm = WasmAssembler.create()
        asm.function("i64ops", listOf(WasmValueType.I64, WasmValueType.I64), listOf(WasmValueType.I64)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.i64Sub()
            a.localGet(fn.getParameter(1))
            a.i64DivS()
        }
        val module = WasmModuleReader.read(asm.assemble())
        val opcodes = disasm.disassemble(module.functions[0]).map { it.opcode }

        assertTrue(WasmOpCode.I64_SUB in opcodes)
        assertTrue(WasmOpCode.I64_DIV_S in opcodes)
    }

    @Test
    fun `disassembles i64 bitwise operations`() {
        val asm = WasmAssembler.create()
        asm.function("i64bits", listOf(WasmValueType.I64, WasmValueType.I64), listOf(WasmValueType.I64)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.i64And()
            a.localGet(fn.getParameter(0))
            a.i64Or()
            a.localGet(fn.getParameter(1))
            a.i64Xor()
        }
        val module = WasmModuleReader.read(asm.assemble())
        val opcodes = disasm.disassemble(module.functions[0]).map { it.opcode }

        assertTrue(WasmOpCode.I64_AND in opcodes)
        assertTrue(WasmOpCode.I64_OR in opcodes)
        assertTrue(WasmOpCode.I64_XOR in opcodes)
    }

    @Test
    fun `disassembles i64 shift operations`() {
        val asm = WasmAssembler.create()
        asm.function("i64shifts", listOf(WasmValueType.I64), listOf(WasmValueType.I64)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.i64Const(4)
            a.i64Shl()
            a.i64Const(2)
            a.i64ShrS()
            a.i64Const(1)
            a.i64ShrU()
        }
        val module = WasmModuleReader.read(asm.assemble())
        val opcodes = disasm.disassemble(module.functions[0]).map { it.opcode }

        assertTrue(WasmOpCode.I64_SHL in opcodes)
        assertTrue(WasmOpCode.I64_SHR_S in opcodes)
        assertTrue(WasmOpCode.I64_SHR_U in opcodes)
    }

    @Test
    fun `disassembles f32 arithmetic`() {
        val asm = WasmAssembler.create()
        asm.function("f32math", listOf(WasmValueType.F32, WasmValueType.F32), listOf(WasmValueType.F32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.f32Add()
            a.localGet(fn.getParameter(1))
            a.f32Sub()
            a.localGet(fn.getParameter(0))
            a.f32Mul()
            a.localGet(fn.getParameter(1))
            a.f32Div()
        }
        val module = WasmModuleReader.read(asm.assemble())
        val opcodes = disasm.disassemble(module.functions[0]).map { it.opcode }

        assertTrue(WasmOpCode.F32_ADD in opcodes)
        assertTrue(WasmOpCode.F32_SUB in opcodes)
        assertTrue(WasmOpCode.F32_MUL in opcodes)
        assertTrue(WasmOpCode.F32_DIV in opcodes)
    }

    @Test
    fun `disassembles f64 arithmetic`() {
        val asm = WasmAssembler.create()
        asm.function("f64math", listOf(WasmValueType.F64, WasmValueType.F64), listOf(WasmValueType.F64)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.f64Add()
            a.localGet(fn.getParameter(1))
            a.f64Sub()
            a.localGet(fn.getParameter(0))
            a.f64Mul()
            a.localGet(fn.getParameter(1))
            a.f64Div()
        }
        val module = WasmModuleReader.read(asm.assemble())
        val opcodes = disasm.disassemble(module.functions[0]).map { it.opcode }

        assertTrue(WasmOpCode.F64_ADD in opcodes)
        assertTrue(WasmOpCode.F64_SUB in opcodes)
        assertTrue(WasmOpCode.F64_MUL in opcodes)
        assertTrue(WasmOpCode.F64_DIV in opcodes)
    }

    @Test
    fun `disassembles f32 unary operations`() {
        val asm = WasmAssembler.create()
        asm.function("f32unary", listOf(WasmValueType.F32), listOf(WasmValueType.F32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.f32Neg()
            a.f32Abs()
            a.f32Sqrt()
        }
        val module = WasmModuleReader.read(asm.assemble())
        val opcodes = disasm.disassemble(module.functions[0]).map { it.opcode }

        assertTrue(WasmOpCode.F32_NEG in opcodes)
        assertTrue(WasmOpCode.F32_ABS in opcodes)
        assertTrue(WasmOpCode.F32_SQRT in opcodes)
    }

    @Test
    fun `disassembles f64 unary operations`() {
        val asm = WasmAssembler.create()
        asm.function("f64unary", listOf(WasmValueType.F64), listOf(WasmValueType.F64)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.f64Neg()
            a.f64Abs()
            a.f64Sqrt()
        }
        val module = WasmModuleReader.read(asm.assemble())
        val opcodes = disasm.disassemble(module.functions[0]).map { it.opcode }

        assertTrue(WasmOpCode.F64_NEG in opcodes)
        assertTrue(WasmOpCode.F64_ABS in opcodes)
        assertTrue(WasmOpCode.F64_SQRT in opcodes)
    }

    @Test
    fun `disassembles block with br`() {
        val asm = WasmAssembler.create()
        asm.function("block_br", listOf(WasmValueType.I32), emptyList()) { fn, a ->
            a.block(0x40) // void
            a.localGet(fn.getParameter(0))
            a.i32Const(0)
            a.i32Eq()
            a.brIf(0)
            a.nop()
            a.end()
        }
        val module = WasmModuleReader.read(asm.assemble())
        val opcodes = disasm.disassemble(module.functions[0]).map { it.opcode }

        assertTrue(WasmOpCode.BLOCK in opcodes)
        assertTrue(WasmOpCode.BR_IF in opcodes)
        assertEquals(2, opcodes.count { it == WasmOpCode.END })
    }

    @Test
    fun `disassembles loop with unconditional br`() {
        val asm = WasmAssembler.create()
        asm.function("infinite", emptyList(), emptyList()) { _, a ->
            a.loop(0x40)
            a.br(0) // branch back to loop start
            a.end()
        }
        val module = WasmModuleReader.read(asm.assemble())
        val opcodes = disasm.disassemble(module.functions[0]).map { it.opcode }

        assertTrue(WasmOpCode.LOOP in opcodes)
        assertTrue(WasmOpCode.BR in opcodes)
        val brInst = disasm.disassemble(module.functions[0]).first { it.opcode == WasmOpCode.BR }
        assertEquals(0, (brInst.operands as WasmInstruction.Operands.Index).value)
    }

    @Test
    fun `disassembles nested if-else`() {
        val asm = WasmAssembler.create()
        asm.function("nested_if", listOf(WasmValueType.I32), listOf(WasmValueType.I32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.i32Const(0)
            a.i32GtS()
            a.if_(0x7F) // i32
            a.localGet(fn.getParameter(0))
            a.i32Const(10)
            a.i32LtS()
            a.if_(0x7F) // i32
            a.i32Const(1) // 0 < x < 10
            a.else_()
            a.i32Const(2) // x >= 10
            a.end()
            a.else_()
            a.i32Const(0) // x <= 0
            a.end()
        }
        val module = WasmModuleReader.read(asm.assemble())
        val opcodes = disasm.disassemble(module.functions[0]).map { it.opcode }

        assertEquals(2, opcodes.count { it == WasmOpCode.IF })
        assertEquals(2, opcodes.count { it == WasmOpCode.ELSE })
        assertEquals(3, opcodes.count { it == WasmOpCode.END }) // 2 if/else + function
    }

    @Test
    fun `disassembles br_table`() {
        val asm = WasmAssembler.create()
        asm.function("switch", listOf(WasmValueType.I32), listOf(WasmValueType.I32)) { fn, a ->
            a.block(0x7F) // i32
            a.block(0x7F) // i32
            a.block(0x7F) // i32
            a.localGet(fn.getParameter(0))
            a.brTable(intArrayOf(0, 1, 2)) // targets: 0,1 default: 2
            a.end()
            a.i32Const(10)
            a.br(2)
            a.end()
            a.i32Const(20)
            a.br(1)
            a.end()
            a.i32Const(30)
        }
        val module = WasmModuleReader.read(asm.assemble())
        val instructions = disasm.disassemble(module.functions[0])
        val brTableInst = instructions.first { it.opcode == WasmOpCode.BR_TABLE }

        assertTrue(brTableInst.operands is WasmInstruction.Operands.BrTable)
        val brTable = brTableInst.operands as WasmInstruction.Operands.BrTable
        assertEquals(2, brTable.labels.size) // two targets
        assertEquals(2, brTable.default) // default label
    }

    @Test
    fun `disassembles call with function index`() {
        val asm = WasmAssembler.create()
        asm.function("helper", listOf(WasmValueType.I32), listOf(WasmValueType.I32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.i32Const(1)
            a.i32Add()
        }
        asm.function("caller", listOf(WasmValueType.I32), listOf(WasmValueType.I32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.call("helper")
        }
        val module = WasmModuleReader.read(asm.assemble())
        val instructions = disasm.disassemble(module.functions[1])

        assertEquals(WasmOpCode.CALL, instructions[1].opcode)
        assertEquals(0, (instructions[1].operands as WasmInstruction.Operands.Index).value)
    }

    @Test
    fun `disassembles call_indirect`() {
        val asm = WasmAssembler.create()
        asm.table("tbl", WasmRefType.FUNCREF, 10)
        asm.function("dispatch", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1)) // table index
            a.callIndirect(0, 0) // type 0, table 0
        }
        val module = WasmModuleReader.read(asm.assemble())
        val instructions = disasm.disassemble(module.functions[0])
        val callInd = instructions.first { it.opcode == WasmOpCode.CALL_INDIRECT }

        assertTrue(callInd.operands is WasmInstruction.Operands.CallIndirect)
        val operands = callInd.operands as WasmInstruction.Operands.CallIndirect
        assertEquals(0, operands.typeIndex)
        assertEquals(0, operands.tableIndex)
    }

    @Test
    fun `disassembles i64 load and store`() {
        val asm = WasmAssembler.create()
        asm.memory("mem", 1)
        asm.function("i64mem", listOf(WasmValueType.I32), listOf(WasmValueType.I64)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.i64Const(12345678L)
            a.i64Store(3, 0)
            a.localGet(fn.getParameter(0))
            a.i64Load(3, 0)
        }
        val module = WasmModuleReader.read(asm.assemble())
        val opcodes = disasm.disassemble(module.functions[0]).map { it.opcode }

        assertTrue(WasmOpCode.I64_STORE in opcodes)
        assertTrue(WasmOpCode.I64_LOAD in opcodes)
    }

    @Test
    fun `disassembles f32 and f64 load store`() {
        val asm = WasmAssembler.create()
        asm.memory("mem", 1)
        asm.function("fmem", listOf(WasmValueType.I32), emptyList()) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.f32Const(1.5f)
            a.f32Store(2, 0)
            a.localGet(fn.getParameter(0))
            a.f64Const(2.5)
            a.f64Store(3, 8)
        }
        val module = WasmModuleReader.read(asm.assemble())
        val opcodes = disasm.disassemble(module.functions[0]).map { it.opcode }

        assertTrue(WasmOpCode.F32_STORE in opcodes)
        assertTrue(WasmOpCode.F64_STORE in opcodes)
    }

    @Test
    fun `disassembles sub-word loads`() {
        val asm = WasmAssembler.create()
        asm.memory("mem", 1)
        asm.function("subloads", listOf(WasmValueType.I32), listOf(WasmValueType.I32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.i32Load8S(0, 0)
            a.drop()
            a.localGet(fn.getParameter(0))
            a.i32Load8U(0, 1)
            a.drop()
            a.localGet(fn.getParameter(0))
            a.i32Load16S(1, 2)
            a.drop()
            a.localGet(fn.getParameter(0))
            a.i32Load16U(1, 4)
        }
        val module = WasmModuleReader.read(asm.assemble())
        val opcodes = disasm.disassemble(module.functions[0]).map { it.opcode }

        assertTrue(WasmOpCode.I32_LOAD8_S in opcodes)
        assertTrue(WasmOpCode.I32_LOAD8_U in opcodes)
        assertTrue(WasmOpCode.I32_LOAD16_S in opcodes)
        assertTrue(WasmOpCode.I32_LOAD16_U in opcodes)
    }

    @Test
    fun `disassembles sub-word stores`() {
        val asm = WasmAssembler.create()
        asm.memory("mem", 1)
        asm.function("substores", listOf(WasmValueType.I32, WasmValueType.I32), emptyList()) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.i32Store8(0, 0)
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.i32Store16(1, 2)
        }
        val module = WasmModuleReader.read(asm.assemble())
        val opcodes = disasm.disassemble(module.functions[0]).map { it.opcode }

        assertTrue(WasmOpCode.I32_STORE8 in opcodes)
        assertTrue(WasmOpCode.I32_STORE16 in opcodes)
    }

    @Test
    fun `disassembles type conversion i32 wrap i64`() {
        val asm = WasmAssembler.create()
        asm.function("wrap", listOf(WasmValueType.I64), listOf(WasmValueType.I32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.i32WrapI64()
        }
        val module = WasmModuleReader.read(asm.assemble())
        val opcodes = disasm.disassemble(module.functions[0]).map { it.opcode }

        assertTrue(WasmOpCode.I32_WRAP_I64 in opcodes)
    }

    @Test
    fun `disassembles type conversion i64 extend i32`() {
        val asm = WasmAssembler.create()
        asm.function("extend_s", listOf(WasmValueType.I32), listOf(WasmValueType.I64)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.i64ExtendI32S()
        }
        val module = WasmModuleReader.read(asm.assemble())
        val opcodes = disasm.disassemble(module.functions[0]).map { it.opcode }

        assertTrue(WasmOpCode.I64_EXTEND_I32_S in opcodes)
    }

    @Test
    fun `disassembles type conversion i64 extend i32 unsigned`() {
        val asm = WasmAssembler.create()
        asm.function("extend_u", listOf(WasmValueType.I32), listOf(WasmValueType.I64)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.i64ExtendI32U()
        }
        val module = WasmModuleReader.read(asm.assemble())
        val opcodes = disasm.disassemble(module.functions[0]).map { it.opcode }

        assertTrue(WasmOpCode.I64_EXTEND_I32_U in opcodes)
    }

    @Test
    fun `disassembles float to int conversion`() {
        val asm = WasmAssembler.create()
        asm.function("trunc", listOf(WasmValueType.F64), listOf(WasmValueType.I32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.i32TruncF64S()
        }
        val module = WasmModuleReader.read(asm.assemble())
        val opcodes = disasm.disassemble(module.functions[0]).map { it.opcode }

        assertTrue(WasmOpCode.I32_TRUNC_F64_S in opcodes)
    }

    @Test
    fun `disassembles int to float conversions`() {
        val asm = WasmAssembler.create()
        asm.function("convert", listOf(WasmValueType.I64), listOf(WasmValueType.F64)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.f64ConvertI64S()
        }
        val module = WasmModuleReader.read(asm.assemble())
        val opcodes = disasm.disassemble(module.functions[0]).map { it.opcode }

        assertTrue(WasmOpCode.F64_CONVERT_I64_S in opcodes)
    }

    @Test
    fun `disassembles reinterpret operations`() {
        val asm = WasmAssembler.create()
        asm.function("reinterpret", listOf(WasmValueType.F32), listOf(WasmValueType.I32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.i32ReinterpretF32()
        }
        val module = WasmModuleReader.read(asm.assemble())
        val opcodes = disasm.disassemble(module.functions[0]).map { it.opcode }

        assertTrue(WasmOpCode.I32_REINTERPRET_F32 in opcodes)
    }

    @Test
    fun `disassembles i32 clz ctz popcnt`() {
        val asm = WasmAssembler.create()
        asm.function("bitops", listOf(WasmValueType.I32), listOf(WasmValueType.I32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.i32Clz()
            a.drop()
            a.localGet(fn.getParameter(0))
            a.i32Ctz()
            a.drop()
            a.localGet(fn.getParameter(0))
            a.i32Popcnt()
        }
        val module = WasmModuleReader.read(asm.assemble())
        val opcodes = disasm.disassemble(module.functions[0]).map { it.opcode }

        assertTrue(WasmOpCode.I32_CLZ in opcodes)
        assertTrue(WasmOpCode.I32_CTZ in opcodes)
        assertTrue(WasmOpCode.I32_POPCNT in opcodes)
    }

    @Test
    fun `disassembles memory size and grow`() {
        val asm = WasmAssembler.create()
        asm.memory("mem", 1)
        asm.function("memops", emptyList(), listOf(WasmValueType.I32)) { _, a ->
            a.memorySize(0)
            a.drop()
            a.i32Const(1)
            a.memoryGrow(0)
        }
        val module = WasmModuleReader.read(asm.assemble())
        val opcodes = disasm.disassemble(module.functions[0]).map { it.opcode }

        assertTrue(WasmOpCode.MEMORY_SIZE in opcodes)
        assertTrue(WasmOpCode.MEMORY_GROW in opcodes)
    }

    @Test
    fun `disassembles multi-value return type`() {
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

        val instructions = disasm.disassemble(module.functions[0])
        assertEquals(WasmOpCode.LOCAL_GET, instructions[0].opcode)
        assertEquals(1, (instructions[0].operands as WasmInstruction.Operands.Index).value)
        assertEquals(WasmOpCode.LOCAL_GET, instructions[1].opcode)
        assertEquals(0, (instructions[1].operands as WasmInstruction.Operands.Index).value)
    }

    @Test
    fun `disassembles empty function body`() {
        val asm = WasmAssembler.create()
        asm.function("empty", emptyList(), emptyList()) { _, _ -> }
        val module = WasmModuleReader.read(asm.assemble())
        val instructions = disasm.disassemble(module.functions[0])

        assertEquals(1, instructions.size)
        assertEquals(WasmOpCode.END, instructions[0].opcode)
    }

    @Test
    fun `disassembles complex control flow with loop and block`() {
        val asm = WasmAssembler.create()
        asm.function("sum", listOf(WasmValueType.I32), listOf(WasmValueType.I32)) { fn, a ->
            val result = a.declareLocal("result", WasmValueType.I32)
            val i = a.declareLocal("i", WasmValueType.I32)
            a.i32Const(0)
            a.localSet(result)
            a.i32Const(0)
            a.localSet(i)
            a.block(0x40) // outer block for breaking
            a.loop(0x40)  // loop
            a.localGet(i)
            a.localGet(fn.getParameter(0))
            a.i32GeS()
            a.brIf(1) // break out of block
            a.localGet(result)
            a.localGet(i)
            a.i32Add()
            a.localSet(result)
            a.localGet(i)
            a.i32Const(1)
            a.i32Add()
            a.localSet(i)
            a.br(0) // continue loop
            a.end() // end loop
            a.end() // end block
            a.localGet(result)
        }
        val module = WasmModuleReader.read(asm.assemble())
        val opcodes = disasm.disassemble(module.functions[0]).map { it.opcode }

        assertTrue(WasmOpCode.BLOCK in opcodes)
        assertTrue(WasmOpCode.LOOP in opcodes)
        assertTrue(WasmOpCode.BR_IF in opcodes)
        assertTrue(WasmOpCode.BR in opcodes)
        assertEquals(3, opcodes.count { it == WasmOpCode.END }) // loop, block, function
    }

    @Test
    fun `disassembles memory load with various offsets`() {
        val asm = WasmAssembler.create()
        asm.memory("mem", 1)
        asm.function("offsets", listOf(WasmValueType.I32), listOf(WasmValueType.I32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.i32Load(2, 0)
            a.drop()
            a.localGet(fn.getParameter(0))
            a.i32Load(2, 4)
            a.drop()
            a.localGet(fn.getParameter(0))
            a.i32Load(2, 128)
        }
        val module = WasmModuleReader.read(asm.assemble())
        val instructions = disasm.disassemble(module.functions[0])
        val loads = instructions.filter { it.opcode == WasmOpCode.I32_LOAD }

        assertEquals(3, loads.size)
        assertEquals(0, (loads[0].operands as WasmInstruction.Operands.MemArg).offset)
        assertEquals(4, (loads[1].operands as WasmInstruction.Operands.MemArg).offset)
        assertEquals(128, (loads[2].operands as WasmInstruction.Operands.MemArg).offset)
    }

    @Test
    fun `text output for control flow is readable`() {
        val asm = WasmAssembler.create()
        asm.function("ctrl", listOf(WasmValueType.I32), emptyList()) { fn, a ->
            a.block(0x40)
            a.localGet(fn.getParameter(0))
            a.brIf(0)
            a.end()
        }
        val module = WasmModuleReader.read(asm.assemble())
        val instructions = disasm.disassemble(module.functions[0])
        val text = instructions.joinToString("\n") { it.text() }

        assertTrue(text.contains("block"))
        assertTrue(text.contains("br_if"))
        assertTrue(text.contains("end"))
    }

    @Test
    fun `disassembles f32 reinterpret from i32`() {
        val asm = WasmAssembler.create()
        asm.function("reinterpret_back", listOf(WasmValueType.I32), listOf(WasmValueType.F32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.f32ReinterpretI32()
        }
        val module = WasmModuleReader.read(asm.assemble())
        val opcodes = disasm.disassemble(module.functions[0]).map { it.opcode }

        assertTrue(WasmOpCode.F32_REINTERPRET_I32 in opcodes)
    }
}
