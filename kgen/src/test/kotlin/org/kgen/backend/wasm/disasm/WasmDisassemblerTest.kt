package org.kgen.backend.wasm.disasm

import org.kgen.backend.wasm.*
import org.kgen.backend.wasm.asm.*
import org.kgen.backend.wasm.module.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class WasmDisassemblerTest {

    private val disasm = WasmDisassembler()

    @Test
    fun `disassembles add function`() {
        val asm = WasmAssembler.create()
        asm.function("add", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.i32Add()
        }
        val module = WasmModuleReader.read(asm.assemble())
        val instructions = disasm.disassemble(module.functions[0])

        assertEquals(WasmOpCode.LOCAL_GET, instructions[0].opcode)
        assertEquals(0, (instructions[0].operands as WasmInstruction.Operands.Index).value)
        assertEquals(WasmOpCode.LOCAL_GET, instructions[1].opcode)
        assertEquals(1, (instructions[1].operands as WasmInstruction.Operands.Index).value)
        assertEquals(WasmOpCode.I32_ADD, instructions[2].opcode)
        assertEquals(WasmOpCode.END, instructions[3].opcode)
    }

    @Test
    fun `disassembles constants`() {
        val asm = WasmAssembler.create()
        asm.function("consts", emptyList(), listOf(WasmValueType.I32)) { _, a ->
            a.i32Const(42)
            a.i32Const(-1)
            a.i32Add()
        }
        val module = WasmModuleReader.read(asm.assemble())
        val instructions = disasm.disassemble(module.functions[0])

        assertEquals(WasmOpCode.I32_CONST, instructions[0].opcode)
        assertEquals(42, (instructions[0].operands as WasmInstruction.Operands.I32).value)
        assertEquals(WasmOpCode.I32_CONST, instructions[1].opcode)
        assertEquals(-1, (instructions[1].operands as WasmInstruction.Operands.I32).value)
    }

    @Test
    fun `disassembles i64 constant`() {
        val asm = WasmAssembler.create()
        asm.function("big", emptyList(), listOf(WasmValueType.I64)) { _, a ->
            a.i64Const(Long.MAX_VALUE)
        }
        val module = WasmModuleReader.read(asm.assemble())
        val instructions = disasm.disassemble(module.functions[0])

        assertEquals(WasmOpCode.I64_CONST, instructions[0].opcode)
        assertEquals(Long.MAX_VALUE, (instructions[0].operands as WasmInstruction.Operands.I64).value)
    }

    @Test
    fun `disassembles call instruction`() {
        val asm = WasmAssembler.create()
        asm.importFunction("env", "log", listOf(WasmValueType.I32), emptyList())
        asm.function("main", emptyList(), emptyList()) { _, a ->
            a.i32Const(99)
            a.call("log")
        }
        val module = WasmModuleReader.read(asm.assemble())
        val instructions = disasm.disassemble(module.functions[0])

        assertEquals(WasmOpCode.I32_CONST, instructions[0].opcode)
        assertEquals(WasmOpCode.CALL, instructions[1].opcode)
        assertEquals(0, (instructions[1].operands as WasmInstruction.Operands.Index).value) // import index 0
    }

    @Test
    fun `disassembles arithmetic sequence`() {
        val asm = WasmAssembler.create()
        asm.function("math", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.i32Add()
            a.i32Const(2)
            a.i32Mul()
        }
        val module = WasmModuleReader.read(asm.assemble())
        val instructions = disasm.disassemble(module.functions[0])

        val opcodes = instructions.map { it.opcode }
        assertEquals(listOf(
            WasmOpCode.LOCAL_GET, WasmOpCode.LOCAL_GET,
            WasmOpCode.I32_ADD, WasmOpCode.I32_CONST, WasmOpCode.I32_MUL,
            WasmOpCode.END,
        ), opcodes)
    }

    @Test
    fun `disassembles block and branch`() {
        val asm = WasmAssembler.create()
        asm.function("cond", listOf(WasmValueType.I32), listOf(WasmValueType.I32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.i32Const(0)
            a.i32Eq()
            a.if_(0x7F) // block returning i32
            a.i32Const(1)
            a.else_()
            a.i32Const(2)
            a.end()
        }
        val module = WasmModuleReader.read(asm.assemble())
        val instructions = disasm.disassemble(module.functions[0])

        val opcodes = instructions.map { it.opcode }
        assertTrue(WasmOpCode.IF in opcodes)
        assertTrue(WasmOpCode.ELSE in opcodes)
        assertEquals(WasmOpCode.END, opcodes[opcodes.size - 1])
        // Two ENDs: one for if/else, one for function
        assertEquals(2, opcodes.count { it == WasmOpCode.END })
    }

    @Test
    fun `disassembles locals with set and tee`() {
        val asm = WasmAssembler.create()
        asm.function("locals", listOf(WasmValueType.I32), listOf(WasmValueType.I32)) { fn, a ->
            val tmp = a.declareLocal("tmp", WasmValueType.I32)
            a.localGet(fn.getParameter(0))
            a.localTee(tmp)
            a.localGet(tmp)
            a.i32Add()
        }
        val module = WasmModuleReader.read(asm.assemble())
        val instructions = disasm.disassemble(module.functions[0])

        assertEquals(WasmOpCode.LOCAL_GET, instructions[0].opcode)
        assertEquals(WasmOpCode.LOCAL_TEE, instructions[1].opcode)
        assertEquals(1, (instructions[1].operands as WasmInstruction.Operands.Index).value) // local index 1
    }

    @Test
    fun `disassembles memory operations`() {
        val asm = WasmAssembler.create()
        asm.memory("mem", 1)
        asm.function("memop", listOf(WasmValueType.I32), listOf(WasmValueType.I32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.i32Load(2, 0) // align=2, offset=0
        }
        val module = WasmModuleReader.read(asm.assemble())
        val instructions = disasm.disassemble(module.functions[0])

        assertEquals(WasmOpCode.I32_LOAD, instructions[1].opcode)
        val memArg = instructions[1].operands as WasmInstruction.Operands.MemArg
        assertEquals(2, memArg.align)
        assertEquals(0, memArg.offset)
    }

    @Test
    fun `text output is human-readable`() {
        val asm = WasmAssembler.create()
        asm.function("add", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.i32Add()
        }
        val module = WasmModuleReader.read(asm.assemble())
        val instructions = disasm.disassemble(module.functions[0])
        val text = instructions.joinToString("\n") { it.text() }

        assertTrue(text.contains("local.get"))
        assertTrue(text.contains("i32.add"))
        assertTrue(text.contains("end"))
    }

    @Test
    fun `disassembles f32 operations`() {
        val asm = WasmAssembler.create()
        asm.function("fmath", listOf(WasmValueType.F32, WasmValueType.F32), listOf(WasmValueType.F32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.f32Add()
            a.f32Const(2.0f)
            a.f32Mul()
        }
        val module = WasmModuleReader.read(asm.assemble())
        val instructions = disasm.disassemble(module.functions[0])

        val opcodes = instructions.map { it.opcode }
        assertTrue(WasmOpCode.F32_ADD in opcodes)
        assertTrue(WasmOpCode.F32_CONST in opcodes)
        assertTrue(WasmOpCode.F32_MUL in opcodes)

        val constInst = instructions.first { it.opcode == WasmOpCode.F32_CONST }
        assertEquals(2.0f, (constInst.operands as WasmInstruction.Operands.F32).value)
    }

    @Test
    fun `disassembles f64 operations`() {
        val asm = WasmAssembler.create()
        asm.function("dmath", listOf(WasmValueType.F64), listOf(WasmValueType.F64)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.f64Const(3.14159265358979)
            a.f64Mul()
        }
        val module = WasmModuleReader.read(asm.assemble())
        val instructions = disasm.disassemble(module.functions[0])

        val constInst = instructions.first { it.opcode == WasmOpCode.F64_CONST }
        assertEquals(3.14159265358979, (constInst.operands as WasmInstruction.Operands.F64).value, 1e-15)
    }

    @Test
    fun `disassembles i64 arithmetic`() {
        val asm = WasmAssembler.create()
        asm.function("i64math", listOf(WasmValueType.I64, WasmValueType.I64), listOf(WasmValueType.I64)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.i64Add()
            a.i64Const(100)
            a.i64Mul()
        }
        val module = WasmModuleReader.read(asm.assemble())
        val opcodes = disasm.disassemble(module.functions[0]).map { it.opcode }

        assertTrue(WasmOpCode.I64_ADD in opcodes)
        assertTrue(WasmOpCode.I64_CONST in opcodes)
        assertTrue(WasmOpCode.I64_MUL in opcodes)
    }

    @Test
    fun `disassembles comparison operators`() {
        val asm = WasmAssembler.create()
        asm.function("cmp", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.i32LtS()
        }
        val module = WasmModuleReader.read(asm.assemble())
        val opcodes = disasm.disassemble(module.functions[0]).map { it.opcode }

        assertTrue(WasmOpCode.I32_LT_S in opcodes)
    }

    @Test
    fun `disassembles drop and select`() {
        val asm = WasmAssembler.create()
        asm.function("sel", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.localGet(fn.getParameter(0))
            a.i32Const(0)
            a.i32GtS()
            a.select()
        }
        val module = WasmModuleReader.read(asm.assemble())
        val opcodes = disasm.disassemble(module.functions[0]).map { it.opcode }

        assertTrue(WasmOpCode.SELECT in opcodes)
    }

    @Test
    fun `disassembles nested blocks`() {
        val asm = WasmAssembler.create()
        asm.function("nested", emptyList(), emptyList()) { _, a ->
            a.block(0x40) // void block
            a.block(0x40)
            a.nop()
            a.end()
            a.end()
        }
        val module = WasmModuleReader.read(asm.assemble())
        val opcodes = disasm.disassemble(module.functions[0]).map { it.opcode }

        assertEquals(2, opcodes.count { it == WasmOpCode.BLOCK })
        assertEquals(3, opcodes.count { it == WasmOpCode.END }) // 2 blocks + function end
    }

    @Test
    fun `disassembles loop with br_if`() {
        val asm = WasmAssembler.create()
        asm.function("countdown", listOf(WasmValueType.I32), emptyList()) { fn, a ->
            val counter = a.declareLocal("counter", WasmValueType.I32)
            a.localGet(fn.getParameter(0))
            a.localSet(counter)
            a.loop(0x40)  // void loop
            a.localGet(counter)
            a.i32Const(1)
            a.i32Sub()
            a.localTee(counter)
            a.i32Const(0)
            a.i32GtS()
            a.brIf(0)  // branch to loop start
            a.end()
        }
        val module = WasmModuleReader.read(asm.assemble())
        val opcodes = disasm.disassemble(module.functions[0]).map { it.opcode }

        assertTrue(WasmOpCode.LOOP in opcodes)
        assertTrue(WasmOpCode.BR_IF in opcodes)
    }

    @Test
    fun `disassembles global get and set`() {
        val asm = WasmAssembler.create()
        asm.global("g", WasmValueType.I32, mutable = true, initValue = 0)
        asm.function("use_global", emptyList(), listOf(WasmValueType.I32)) { _, a ->
            a.globalGet(0)
            a.i32Const(1)
            a.i32Add()
            a.globalSet(0)
            a.globalGet(0)
        }
        val module = WasmModuleReader.read(asm.assemble())
        val instructions = disasm.disassemble(module.functions[0])

        assertEquals(WasmOpCode.GLOBAL_GET, instructions[0].opcode)
        assertEquals(0, (instructions[0].operands as WasmInstruction.Operands.Index).value)
        assertEquals(WasmOpCode.GLOBAL_SET, instructions[3].opcode)
    }

    @Test
    fun `disassembles store operations`() {
        val asm = WasmAssembler.create()
        asm.memory("mem", 1)
        asm.function("store", listOf(WasmValueType.I32, WasmValueType.I32), emptyList()) { fn, a ->
            a.localGet(fn.getParameter(0)) // address
            a.localGet(fn.getParameter(1)) // value
            a.i32Store(2, 0)
        }
        val module = WasmModuleReader.read(asm.assemble())
        val instructions = disasm.disassemble(module.functions[0])

        val storeInst = instructions.first { it.opcode == WasmOpCode.I32_STORE }
        val memArg = storeInst.operands as WasmInstruction.Operands.MemArg
        assertEquals(2, memArg.align)
        assertEquals(0, memArg.offset)
    }

    @Test
    fun `disassembles memory load with offset`() {
        val asm = WasmAssembler.create()
        asm.memory("mem", 1)
        asm.function("load_offset", listOf(WasmValueType.I32), listOf(WasmValueType.I32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.i32Load(2, 16) // offset 16
        }
        val module = WasmModuleReader.read(asm.assemble())
        val instructions = disasm.disassemble(module.functions[0])

        val loadInst = instructions.first { it.opcode == WasmOpCode.I32_LOAD }
        val memArg = loadInst.operands as WasmInstruction.Operands.MemArg
        assertEquals(16, memArg.offset)
    }

    @Test
    fun `disassembles return instruction`() {
        val asm = WasmAssembler.create()
        asm.function("early_return", listOf(WasmValueType.I32), listOf(WasmValueType.I32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.i32Const(0)
            a.i32Eq()
            a.if_(0x40) // void
            a.i32Const(-1)
            a.return_()
            a.end()
            a.localGet(fn.getParameter(0))
        }
        val module = WasmModuleReader.read(asm.assemble())
        val opcodes = disasm.disassemble(module.functions[0]).map { it.opcode }

        assertTrue(WasmOpCode.RETURN in opcodes)
    }

    @Test
    fun `disassembles unreachable`() {
        val asm = WasmAssembler.create()
        asm.function("trap", emptyList(), emptyList()) { _, a ->
            a.unreachable()
        }
        val module = WasmModuleReader.read(asm.assemble())
        val opcodes = disasm.disassemble(module.functions[0]).map { it.opcode }

        assertEquals(WasmOpCode.UNREACHABLE, opcodes[0])
    }

    @Test
    fun `disassembles nop`() {
        val asm = WasmAssembler.create()
        asm.function("nops", emptyList(), emptyList()) { _, a ->
            a.nop()
            a.nop()
            a.nop()
        }
        val module = WasmModuleReader.read(asm.assemble())
        val opcodes = disasm.disassemble(module.functions[0]).map { it.opcode }

        assertEquals(3, opcodes.count { it == WasmOpCode.NOP })
    }

    @Test
    fun `disassembles i32 bitwise operations`() {
        val asm = WasmAssembler.create()
        asm.function("bits", listOf(WasmValueType.I32, WasmValueType.I32), listOf(WasmValueType.I32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.localGet(fn.getParameter(1))
            a.i32And()
            a.localGet(fn.getParameter(0))
            a.i32Or()
            a.localGet(fn.getParameter(1))
            a.i32Xor()
        }
        val module = WasmModuleReader.read(asm.assemble())
        val opcodes = disasm.disassemble(module.functions[0]).map { it.opcode }

        assertTrue(WasmOpCode.I32_AND in opcodes)
        assertTrue(WasmOpCode.I32_OR in opcodes)
        assertTrue(WasmOpCode.I32_XOR in opcodes)
    }

    @Test
    fun `disassembles shift operations`() {
        val asm = WasmAssembler.create()
        asm.function("shifts", listOf(WasmValueType.I32), listOf(WasmValueType.I32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.i32Const(2)
            a.i32Shl()
            a.i32Const(1)
            a.i32ShrU()
        }
        val module = WasmModuleReader.read(asm.assemble())
        val opcodes = disasm.disassemble(module.functions[0]).map { it.opcode }

        assertTrue(WasmOpCode.I32_SHL in opcodes)
        assertTrue(WasmOpCode.I32_SHR_U in opcodes)
    }

    @Test
    fun `disassembles conversion operations`() {
        val asm = WasmAssembler.create()
        asm.function("convert", listOf(WasmValueType.I32), listOf(WasmValueType.F64)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.f64ConvertI32S()
        }
        val module = WasmModuleReader.read(asm.assemble())
        val opcodes = disasm.disassemble(module.functions[0]).map { it.opcode }

        assertTrue(WasmOpCode.F64_CONVERT_I32_S in opcodes)
    }

    @Test
    fun `disassembles eqz`() {
        val asm = WasmAssembler.create()
        asm.function("is_zero", listOf(WasmValueType.I32), listOf(WasmValueType.I32)) { fn, a ->
            a.localGet(fn.getParameter(0))
            a.i32Eqz()
        }
        val module = WasmModuleReader.read(asm.assemble())
        val opcodes = disasm.disassemble(module.functions[0]).map { it.opcode }

        assertTrue(WasmOpCode.I32_EQZ in opcodes)
    }

    @Test
    fun `disassembles negative i32 constant`() {
        val asm = WasmAssembler.create()
        asm.function("neg", emptyList(), listOf(WasmValueType.I32)) { _, a ->
            a.i32Const(Int.MIN_VALUE)
        }
        val module = WasmModuleReader.read(asm.assemble())
        val instructions = disasm.disassemble(module.functions[0])

        assertEquals(Int.MIN_VALUE, (instructions[0].operands as WasmInstruction.Operands.I32).value)
    }

    @Test
    fun `disassembles negative i64 constant`() {
        val asm = WasmAssembler.create()
        asm.function("neg64", emptyList(), listOf(WasmValueType.I64)) { _, a ->
            a.i64Const(Long.MIN_VALUE)
        }
        val module = WasmModuleReader.read(asm.assemble())
        val instructions = disasm.disassemble(module.functions[0])

        assertEquals(Long.MIN_VALUE, (instructions[0].operands as WasmInstruction.Operands.I64).value)
    }

    @Test
    fun `disassembles f32 special values`() {
        val asm = WasmAssembler.create()
        asm.function("specials", emptyList(), listOf(WasmValueType.F32)) { _, a ->
            a.f32Const(Float.POSITIVE_INFINITY)
            a.drop()
            a.f32Const(Float.NEGATIVE_INFINITY)
            a.drop()
            a.f32Const(0.0f)
        }
        val module = WasmModuleReader.read(asm.assemble())
        val instructions = disasm.disassemble(module.functions[0])
        val f32Instructions = instructions.filter { it.opcode == WasmOpCode.F32_CONST }

        assertEquals(Float.POSITIVE_INFINITY, (f32Instructions[0].operands as WasmInstruction.Operands.F32).value)
        assertEquals(Float.NEGATIVE_INFINITY, (f32Instructions[1].operands as WasmInstruction.Operands.F32).value)
        assertEquals(0.0f, (f32Instructions[2].operands as WasmInstruction.Operands.F32).value)
    }

    @Test
    fun `disassembles many functions in sequence`() {
        val asm = WasmAssembler.create()
        for (i in 0 until 20) {
            asm.function("fn_$i", listOf(WasmValueType.I32), listOf(WasmValueType.I32)) { fn, a ->
                a.localGet(fn.getParameter(0))
                a.i32Const(i)
                a.i32Add()
            }
        }
        val module = WasmModuleReader.read(asm.assemble())

        for (i in 0 until 20) {
            val instructions = disasm.disassemble(module.functions[i])
            val constInst = instructions.first { it.opcode == WasmOpCode.I32_CONST }
            assertEquals(i, (constInst.operands as WasmInstruction.Operands.I32).value)
        }
    }

    @Test
    fun `instruction offset tracking is correct`() {
        val asm = WasmAssembler.create()
        asm.function("offsets", emptyList(), listOf(WasmValueType.I32)) { _, a ->
            a.i32Const(42) // offset 0: 0x41, then LEB128
            a.i32Const(1)  // some offset after
            a.i32Add()     // single byte: 0x6A
        }
        val module = WasmModuleReader.read(asm.assemble())
        val instructions = disasm.disassemble(module.functions[0])

        assertEquals(0, instructions[0].offset)
        assertTrue(instructions[1].offset > instructions[0].offset)
        assertTrue(instructions[2].offset > instructions[1].offset)
    }

    @Test
    fun `round-trip assemble then read then disassemble`() {
        val asm = WasmAssembler.create()
        asm.importFunction("env", "print", listOf(WasmValueType.I32), emptyList())
        asm.memory("mem", 1, 16, exported = true)
        asm.global("counter", WasmValueType.I32, mutable = true, initValue = 0, exported = true)
        asm.dataSegment(0, 0, "Hello".toByteArray())

        asm.function("inc", emptyList(), emptyList(), exported = true) { _, a ->
            a.globalGet(0)
            a.i32Const(1)
            a.i32Add()
            a.globalSet(0)
        }

        asm.function("get", emptyList(), listOf(WasmValueType.I32), exported = true) { _, a ->
            a.globalGet(0)
        }

        val bytes = asm.assemble()
        val module = WasmModuleReader.read(bytes)

        // Verify structure
        assertEquals(1, module.imports.size)
        assertEquals(1, module.memories.size)
        assertEquals(1, module.globals.size)
        assertEquals(1, module.dataSegments.size)
        assertEquals(2, module.functions.size)
        assertEquals(4, module.exports.size) // inc, get, mem, counter

        // Disassemble and verify
        val incInstructions = disasm.disassemble(module.functions[0])
        val opcodes = incInstructions.map { it.opcode }
        assertTrue(WasmOpCode.GLOBAL_GET in opcodes)
        assertTrue(WasmOpCode.I32_CONST in opcodes)
        assertTrue(WasmOpCode.I32_ADD in opcodes)
        assertTrue(WasmOpCode.GLOBAL_SET in opcodes)
    }
}
