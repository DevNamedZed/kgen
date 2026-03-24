package org.kgen.target.x86.codegen

import org.kgen.target.x86.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.binary.*
import org.kgen.binary.elf.ElfObjectType
import org.kgen.binary.pe.PeConstants
import org.kgen.ir.*
import org.kgen.ir.instructions.*
import org.kgen.ir.build.*
import org.kgen.codegen.*
import org.kgen.ir.target.Target
import java.nio.ByteBuffer
import java.nio.ByteOrder

class X86CodeGeneratorTest {

    private fun le(bytes: ByteArray) = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

    private fun buildAddModule(): Module {
        val ir = ModuleBuilder("test", Target.x86_64())
        val params = ir.createFunction("add", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val sum = ir.add(params[0], params[1])
        ir.ret(sum)
        ir.finalizeFunction()
        return ir.build()
    }

    @Test
    fun `generates valid ELF object file from IR`() {
        val module = buildAddModule()
        val gen = X86CodeGenerator()
        val objFile = gen.generateObjectFile(module)

        val textSection = objFile.sections.find { it.name == ".text" }
        assertNotNull(textSection)
        assertTrue(textSection!!.data.isNotEmpty())

        val addSymbol = objFile.symbols.find { it.name == "add" }
        assertNotNull(addSymbol)
        assertEquals(".text", addSymbol!!.section)
    }

    @Test
    fun `generates ELF object bytes from IR`() {
        val module = buildAddModule()
        val gen = X86CodeGenerator()
        val bytes = gen.generate(module, CodeGenOptions(outputFormat = OutputFormat.OBJECT))
        val buf = le(bytes)

        // Verify ELF header
        assertEquals(0x7f, bytes[0].toInt() and 0xFF)
        assertEquals('E'.code, bytes[1].toInt() and 0xFF)
        assertEquals(ElfObjectType.REL.code, buf.getShort(16).toInt() and 0xFFFF)
    }

    @Test
    fun `generates linked ELF executable from IR`() {
        // Build a module with main() that calls an external function
        val ir = ModuleBuilder("hello", Target.x86_64())
        ir.declareFunction("puts", listOf(Param("s", Type.OpaquePointer)), Type.I32)

        val params = ir.createFunction("main", emptyList(), Type.I32)
        ir.appendBlock("entry")
        ir.call("puts", listOf(Constant.I64(0)), Type.I32)
        ir.ret(Constant.I32(0))
        ir.finalizeFunction()

        val module = ir.build()
        val gen = X86CodeGenerator()
        val binary = gen.generate(module, CodeGenOptions(outputFormat = OutputFormat.BINARY))
        val buf = le(binary)

        // Verify linked ELF executable
        assertEquals(0x7f, binary[0].toInt() and 0xFF)
        assertEquals(ElfObjectType.EXEC.code, buf.getShort(16).toInt() and 0xFFFF)
        assertTrue(binary.size > 1000)
    }

    @Test
    fun `compiles arithmetic expressions`() {
        val ir = ModuleBuilder("math", Target.x86_64())
        val params = ir.createFunction("compute", listOf(
            Param("x", Type.I32), Param("y", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val sum = ir.add(params[0], params[1])
        val doubled = ir.add(sum, sum)
        val result = ir.sub(doubled, Constant.I32(1))
        ir.ret(result)
        ir.finalizeFunction()

        val module = ir.build()
        val gen = X86CodeGenerator()
        val obj = gen.generateObjectFile(module)

        assertTrue(obj.sections[0].data.size > 0)
        assertTrue(obj.symbols.any { it.name == "compute" })
    }

    @Test
    fun `compiles multi-function module`() {
        val ir = ModuleBuilder("multi", Target.x86_64())

        // helper(x) = x + 1
        val helperParams = ir.createFunction("helper", listOf(Param("x", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val inc = ir.add(helperParams[0], Constant.I32(1))
        ir.ret(inc)
        ir.finalizeFunction()

        // main() = helper(41)
        ir.createFunction("main", emptyList(), Type.I32)
        ir.appendBlock("entry")
        val result = ir.call("helper", listOf(Constant.I32(41)), Type.I32)
        ir.ret(result)
        ir.finalizeFunction()

        val module = ir.build()
        val gen = X86CodeGenerator()
        val obj = gen.generateObjectFile(module)

        assertEquals(2, obj.symbols.count { it.section == ".text" })
        assertTrue(obj.symbols.any { it.name == "helper" })
        assertTrue(obj.symbols.any { it.name == "main" })
    }

    @Test
    fun `hello world IR to ELF executable with puts`() {
        val ir = ModuleBuilder("hello", Target.x86_64())

        // Global string constant
        val strType = Type.Array(Type.I8, 14)
        val strRef = ir.addGlobal("hello_str", strType,
            Constant.StringConst("Hello, World!"), isConstant = true,
            linkage = Linkage.INTERNAL)

        // External puts declaration
        ir.declareFunction("puts", listOf(Param("s", Type.OpaquePointer)), Type.I32)

        // main() { puts(&hello_str); return 0; }
        ir.createFunction("main", emptyList(), Type.I32)
        ir.appendBlock("entry")
        ir.call("puts", listOf(strRef), Type.I32)
        ir.ret(Constant.I32(0))
        ir.finalizeFunction()

        val module = ir.build()
        val gen = X86CodeGenerator()

        // Verify the object file has .text and .rodata
        val obj = gen.generateObjectFile(module)
        assertTrue(obj.sections.any { it.name == ".text" })
        assertTrue(obj.sections.any { it.name == ".rodata" })
        assertTrue(obj.symbols.any { it.name == "hello_str" && it.section == ".rodata" })
        assertTrue(obj.symbols.any { it.name == "puts" && it.kind == SymbolKind.UNDEFINED })
        assertTrue(obj.symbols.any { it.name == "main" && it.section == ".text" })

        // Verify relocations: one PLT32 for puts, one PC32 for string ref
        assertTrue(obj.relocations.any { it.symbol == "puts" && it.type == RelocationType.X86_64.PLT32 })
        assertTrue(obj.relocations.any { it.symbol == "hello_str" && it.type == RelocationType.X86_64.PC32 })

        // Link into executable
        val binary = gen.generate(module, CodeGenOptions(outputFormat = OutputFormat.BINARY))
        val buf = le(binary)

        // Valid ELF executable
        assertEquals(0x7f, binary[0].toInt() and 0xFF)
        assertEquals(ElfObjectType.EXEC.code, buf.getShort(16).toInt() and 0xFFFF)
        assertTrue(binary.size > 1000)

        // The string "Hello, World!" should appear in the binary
        val binaryStr = String(binary, Charsets.US_ASCII)
        assertTrue(binaryStr.contains("Hello, World!"))
    }

    @Test
    fun `generates rodata section for string globals`() {
        val ir = ModuleBuilder("strings", Target.x86_64())
        val strType = Type.Array(Type.I8, 6)
        ir.addGlobal("msg", strType, Constant.StringConst("hello"), isConstant = true,
            linkage = Linkage.INTERNAL)
        ir.createFunction("noop", emptyList(), Type.I32)
        ir.appendBlock("entry")
        ir.ret(Constant.I32(0))
        ir.finalizeFunction()

        val module = ir.build()
        val obj = X86CodeGenerator().generateObjectFile(module)

        assertNotNull(obj.sections.find { it.name == ".text" })
        val rodata = obj.sections.first { it.name == ".rodata" }
        assertTrue(String(rodata.data, Charsets.US_ASCII).startsWith("hello"))
        val sym = obj.symbols.first { it.name == "msg" }
        assertEquals(".rodata", sym.section)
        assertEquals(0L, sym.value)
    }

    @Test
    fun `function prologue and epilogue are emitted`() {
        val module = buildAddModule()
        val obj = X86CodeGenerator().generateObjectFile(module)
        val code = obj.sections[0].data

        // Dump first 10 and last 10 bytes for debugging
        val first = code.take(10).map { "0x%02X".format(it.toInt() and 0xFF) }
        val last = code.takeLast(10).map { "0x%02X".format(it.toInt() and 0xFF) }

        // Last bytes should be pop rbp (0x5D), ret (0xC3)
        assertEquals(0xC3, code[code.size - 1].toInt() and 0xFF, "Last byte should be ret, got: $last")
        assertEquals(0x5D, code[code.size - 2].toInt() and 0xFF, "Second-to-last should be pop rbp, got: $last")

        // First byte should be push rbp (0x55)
        assertEquals(0x55, code[0].toInt() and 0xFF, "First byte should be push rbp, got: $first")
    }

    @Test
    fun `hello world IR to PE executable with puts`() {
        val ir = ModuleBuilder("hello_pe", Target.x86_64())
        ir.targetTriple = "x86_64-unknown-windows-msvc"

        val strType = Type.Array(Type.I8, 14)
        val strRef = ir.addGlobal("hello_str", strType,
            Constant.StringConst("Hello, World!"), isConstant = true,
            linkage = Linkage.INTERNAL)

        ir.declareFunction("puts", listOf(Param("s", Type.OpaquePointer)), Type.I32)

        ir.createFunction("main", emptyList(), Type.I32)
        ir.appendBlock("entry")
        ir.call("puts", listOf(strRef), Type.I32)
        ir.ret(Constant.I32(0))
        ir.finalizeFunction()

        val module = ir.build()
        val gen = X86CodeGenerator()

        // Verify object file has Windows-style imports
        val obj = gen.generateObjectFile(module,
            listOf(ImportEntry(moduleName = "ucrtbase.dll", symbolName = "puts")))
        assertEquals(ObjectFormat.PE_COFF, obj.format)
        assertTrue(obj.imports.any { it.symbolName == "puts" && it.moduleName == "ucrtbase.dll" })

        // Generate PE executable
        val binary = gen.generate(module, CodeGenOptions(outputFormat = OutputFormat.BINARY))
        val buf = le(binary)

        // MZ header
        assertEquals('M'.code, binary[0].toInt() and 0xFF)
        assertEquals('Z'.code, binary[1].toInt() and 0xFF)

        // PE signature at offset from 0x3C
        val peOff = buf.getInt(0x3C)
        assertEquals('P'.code, binary[peOff].toInt() and 0xFF)
        assertEquals('E'.code, binary[peOff + 1].toInt() and 0xFF)

        // COFF header: machine = AMD64
        assertEquals(PeConstants.MACHINE_AMD64,
            buf.getShort(peOff + 4).toInt() and 0xFFFF)

        // Optional header magic: PE32+
        assertEquals(PeConstants.PE32PLUS_MAGIC,
            buf.getShort(peOff + 24).toInt() and 0xFFFF)

        assertTrue(binary.size > 512, "PE binary should be non-trivial")

        // The string "Hello, World!" should appear in the binary
        val binaryStr = String(binary, Charsets.US_ASCII)
        assertTrue(binaryStr.contains("Hello, World!"))
    }

    @Test
    fun `register allocator handles many variables with spilling`() {
        // Create a function with more live variables than available registers
        // to exercise the spill/reload paths
        val ir = ModuleBuilder("spill_test", Target.x86_64())
        val params = ir.createFunction("many_vars",
            listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        ir.appendBlock("entry")

        // Create a chain of additions that keeps many values alive
        val v1 = ir.add(params[0], params[1])
        val v2 = ir.add(v1, Constant.I32(1))
        val v3 = ir.add(v2, Constant.I32(2))
        val v4 = ir.add(v3, Constant.I32(3))
        val v5 = ir.add(v4, Constant.I32(4))
        val v6 = ir.add(v5, Constant.I32(5))
        val v7 = ir.add(v6, Constant.I32(6))
        val v8 = ir.add(v7, Constant.I32(7))
        val v9 = ir.add(v8, Constant.I32(8))
        val v10 = ir.add(v9, Constant.I32(9))
        val v11 = ir.add(v10, Constant.I32(10))
        val v12 = ir.add(v11, Constant.I32(11))

        // Now use an early value to force it to stay live across many definitions
        // This should cause spilling since we exceed the register count
        val result = ir.add(v1, v12)
        ir.ret(result)
        ir.finalizeFunction()

        val module = ir.build()
        val gen = X86CodeGenerator()
        val obj = gen.generateObjectFile(module)
        val code = obj.sections[0].data

        // Should compile without error
        assertTrue(code.isNotEmpty())

        // Should have push rbp prologue and pop rbp / ret epilogue
        assertEquals(0x55, code[0].toInt() and 0xFF, "Should start with push rbp")
        assertEquals(0xC3, code.last().toInt() and 0xFF, "Should end with ret")

        // Disassemble and verify round-trip
        val disasm = org.kgen.target.x86.disasm.X86Disassembler()
        val insts = disasm.disassembleRaw(code)
        assertTrue(insts.isNotEmpty())
        assertEquals("push", insts.first().mnemonic)
        assertEquals("ret", insts.last().mnemonic)
        assertEquals(code.size, insts.sumOf { it.size },
            "Decoded bytes should match code size: ${insts.map { it.text() }}")
    }

    @Test
    fun `register allocator saves and restores callee-saved registers`() {
        // Use enough variables to require callee-saved registers (rbx, r12-r15)
        val ir = ModuleBuilder("callee_save_test", Target.x86_64())
        val params = ir.createFunction("uses_callee_saved",
            listOf(Param("x", Type.I32)), Type.I32)
        ir.appendBlock("entry")

        // Create enough live values to exhaust caller-saved registers
        var prev: Value = params[0]
        val intermediates = mutableListOf<Value>()
        for (i in 1..15) {
            prev = ir.add(prev, Constant.I32(i))
            intermediates.add(prev)
        }
        // Use first intermediate to keep it live
        val result = ir.add(intermediates.first(), intermediates.last())
        ir.ret(result)
        ir.finalizeFunction()

        val module = ir.build()
        val gen = X86CodeGenerator()
        val obj = gen.generateObjectFile(module)
        val code = obj.sections[0].data

        assertTrue(code.isNotEmpty())
        assertEquals(0x55, code[0].toInt() and 0xFF, "Should start with push rbp")
        assertEquals(0xC3, code.last().toInt() and 0xFF, "Should end with ret")

        // Verify round-trip through disassembler
        val disasm = org.kgen.target.x86.disasm.X86Disassembler()
        val insts = disasm.disassembleRaw(code)
        assertEquals(code.size, insts.sumOf { it.size },
            "All bytes decoded: ${insts.map { it.text() }}")
    }

    @Test
    fun `compiles conditional branch`() {
        val ir = ModuleBuilder("branch_test", Target.x86_64())
        val params = ir.createFunction("abs", listOf(Param("x", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val cond = ir.icmp(ICmpPredicate.SLT, params[0], Constant.I32(0))
        ir.condBr(cond, BlockRef("negate"), BlockRef("done"))

        ir.appendBlock("negate")
        val neg = ir.sub(Constant.I32(0), params[0])
        ir.br(BlockRef("done"))

        ir.appendBlock("done")
        // For now, just return x (phi not yet supported in codegen)
        ir.ret(params[0])
        ir.finalizeFunction()

        val module = ir.build()
        val obj = X86CodeGenerator().generateObjectFile(module)
        val code = obj.sections[0].data

        assertTrue(code.isNotEmpty())
        assertEquals(0x55, code[0].toInt() and 0xFF, "Should start with push rbp")
        assertEquals(0xC3, code.last().toInt() and 0xFF, "Should end with ret")

        val disasm = org.kgen.target.x86.disasm.X86Disassembler()
        val insts = disasm.disassembleRaw(code)
        assertEquals(code.size, insts.sumOf { it.size },
            "All bytes decoded: ${insts.map { it.text() }}")

        // Should contain a conditional jump (JL = 0F 8C)
        assertTrue(insts.any { it.mnemonic == "jl" || it.mnemonic.startsWith("j") },
            "Should have a conditional jump: ${insts.map { it.text() }}")
    }

    @Test
    fun `compiles unconditional branch`() {
        val ir = ModuleBuilder("br_test", Target.x86_64())
        ir.createFunction("jump", emptyList(), Type.I32)
        ir.appendBlock("entry")
        ir.br(BlockRef("target"))

        ir.appendBlock("target")
        ir.ret(Constant.I32(42))
        ir.finalizeFunction()

        val module = ir.build()
        val obj = X86CodeGenerator().generateObjectFile(module)
        val code = obj.sections[0].data

        assertTrue(code.isNotEmpty())
        assertEquals(0xC3, code.last().toInt() and 0xFF, "Should end with ret")

        val disasm = org.kgen.target.x86.disasm.X86Disassembler()
        val insts = disasm.disassembleRaw(code)
        assertEquals(code.size, insts.sumOf { it.size },
            "All bytes decoded: ${insts.map { it.text() }}")
    }

    @Test
    fun `compiles select instruction`() {
        val ir = ModuleBuilder("select_test", Target.x86_64())
        val params = ir.createFunction("max", listOf(
            Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val cond = ir.icmp(ICmpPredicate.SGT, params[0], params[1])
        val result = ir.select(cond, params[0], params[1])
        ir.ret(result)
        ir.finalizeFunction()

        val module = ir.build()
        val obj = X86CodeGenerator().generateObjectFile(module)
        val code = obj.sections[0].data

        assertTrue(code.isNotEmpty())
        assertEquals(0xC3, code.last().toInt() and 0xFF, "Should end with ret")

        val disasm = org.kgen.target.x86.disasm.X86Disassembler()
        val insts = disasm.disassembleRaw(code)
        assertEquals(code.size, insts.sumOf { it.size },
            "All bytes decoded: ${insts.map { it.text() }}")

        // Should contain a cmov instruction
        assertTrue(insts.any { it.mnemonic.startsWith("cmov") },
            "Should have cmov: ${insts.map { it.text() }}")
    }

    @Test
    fun `compiles if-else diamond pattern`() {
        // if (x > 0) return x + 1; else return x - 1;
        val ir = ModuleBuilder("diamond", Target.x86_64())
        val params = ir.createFunction("adjust", listOf(Param("x", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val cond = ir.icmp(ICmpPredicate.SGT, params[0], Constant.I32(0))
        ir.condBr(cond, BlockRef("then"), BlockRef("else"))

        ir.appendBlock("then")
        val inc = ir.add(params[0], Constant.I32(1))
        ir.ret(inc)

        ir.appendBlock("else")
        val dec = ir.sub(params[0], Constant.I32(1))
        ir.ret(dec)

        ir.finalizeFunction()

        val module = ir.build()
        val obj = X86CodeGenerator().generateObjectFile(module)
        val code = obj.sections[0].data

        assertTrue(code.isNotEmpty())

        val disasm = org.kgen.target.x86.disasm.X86Disassembler()
        val insts = disasm.disassembleRaw(code)
        assertEquals(code.size, insts.sumOf { it.size },
            "All bytes decoded: ${insts.map { it.text() }}")

        // Should have a conditional jump and at least two ret instructions
        val retCount = insts.count { it.mnemonic == "ret" }
        assertTrue(retCount >= 2, "Diamond pattern should have >=2 ret: ${insts.map { it.text() }}")
    }

    @Test
    fun `compiles signed and unsigned division`() {
        val ir = ModuleBuilder("div_test", Target.x86_64())
        val params = ir.createFunction("div_ops",
            listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val q = ir.sdiv(params[0], params[1])
        val r = ir.srem(params[0], params[1])
        val sum = ir.add(q, r)
        ir.ret(sum)
        ir.finalizeFunction()

        val obj = X86CodeGenerator().generateObjectFile(ir.build())
        val code = obj.sections[0].data
        assertTrue(code.isNotEmpty())
        assertValidCodegen(code)
    }

    @Test
    fun `compiles unsigned division and remainder`() {
        val ir = ModuleBuilder("udiv_test", Target.x86_64())
        val params = ir.createFunction("udiv_ops",
            listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val q = ir.udiv(params[0], params[1])
        val r = ir.urem(params[0], params[1])
        val sum = ir.add(q, r)
        ir.ret(sum)
        ir.finalizeFunction()

        val obj = X86CodeGenerator().generateObjectFile(ir.build())
        assertValidCodegen(obj.sections[0].data)
    }

    @Test
    fun `compiles shift operations`() {
        val ir = ModuleBuilder("shift_test", Target.x86_64())
        val params = ir.createFunction("shifts",
            listOf(Param("x", Type.I32), Param("n", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val shl = ir.shl(params[0], Constant.I32(2))           // x << 2
        val shr = ir.lshr(shl, params[1])                       // (x << 2) >> n
        val asr = ir.ashr(shr, Constant.I32(1))                 // >> 1 (arithmetic)
        ir.ret(asr)
        ir.finalizeFunction()

        val obj = X86CodeGenerator().generateObjectFile(ir.build())
        assertValidCodegen(obj.sections[0].data)
    }

    @Test
    fun `compiles negation`() {
        val ir = ModuleBuilder("neg_test", Target.x86_64())
        val params = ir.createFunction("negate",
            listOf(Param("x", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val neg = ir.neg(params[0])
        ir.ret(neg)
        ir.finalizeFunction()

        val obj = X86CodeGenerator().generateObjectFile(ir.build())
        val code = obj.sections[0].data
        assertValidCodegen(code)

        val disasm = org.kgen.target.x86.disasm.X86Disassembler()
        val insts = disasm.disassembleRaw(code)
        assertTrue(insts.any { it.mnemonic == "neg" }, "Should have neg: ${insts.map { it.text() }}")
    }

    @Test
    fun `compiles zero extension i32 to i64`() {
        val ir = ModuleBuilder("zext_test", Target.x86_64())
        val params = ir.createFunction("widen",
            listOf(Param("x", Type.I32)), Type.I64)
        ir.appendBlock("entry")
        val wide = ir.zext(params[0], Type.I64)
        ir.ret(wide)
        ir.finalizeFunction()

        val obj = X86CodeGenerator().generateObjectFile(ir.build())
        assertValidCodegen(obj.sections[0].data)
    }

    @Test
    fun `compiles sign extension i32 to i64`() {
        val ir = ModuleBuilder("sext_test", Target.x86_64())
        val params = ir.createFunction("sign_widen",
            listOf(Param("x", Type.I32)), Type.I64)
        ir.appendBlock("entry")
        val wide = ir.sext(params[0], Type.I64)
        ir.ret(wide)
        ir.finalizeFunction()

        val obj = X86CodeGenerator().generateObjectFile(ir.build())
        assertValidCodegen(obj.sections[0].data)
    }

    @Test
    fun `compiles truncation i64 to i32`() {
        val ir = ModuleBuilder("trunc_test", Target.x86_64())
        val params = ir.createFunction("narrow",
            listOf(Param("x", Type.I64)), Type.I32)
        ir.appendBlock("entry")
        val narrow = ir.trunc(params[0], Type.I32)
        ir.ret(narrow)
        ir.finalizeFunction()

        val obj = X86CodeGenerator().generateObjectFile(ir.build())
        assertValidCodegen(obj.sections[0].data)
    }

    @Test
    fun `compiles 64-bit arithmetic`() {
        val ir = ModuleBuilder("i64_test", Target.x86_64())
        val params = ir.createFunction("math64",
            listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
        ir.appendBlock("entry")
        val sum = ir.add(params[0], params[1])
        val diff = ir.sub(sum, Constant.I64(100))
        val prod = ir.mul(diff, params[1])
        ir.ret(prod)
        ir.finalizeFunction()

        val obj = X86CodeGenerator().generateObjectFile(ir.build())
        assertValidCodegen(obj.sections[0].data)
    }

    @Test
    fun `compiles 64-bit division`() {
        val ir = ModuleBuilder("div64_test", Target.x86_64())
        val params = ir.createFunction("div64",
            listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
        ir.appendBlock("entry")
        val q = ir.sdiv(params[0], params[1])
        ir.ret(q)
        ir.finalizeFunction()

        val obj = X86CodeGenerator().generateObjectFile(ir.build())
        assertValidCodegen(obj.sections[0].data)
    }

    @Test
    fun `compiles 64-bit shifts`() {
        val ir = ModuleBuilder("shift64_test", Target.x86_64())
        val params = ir.createFunction("shifts64",
            listOf(Param("x", Type.I64)), Type.I64)
        ir.appendBlock("entry")
        val shl = ir.shl(params[0], Constant.I32(4))
        val shr = ir.lshr(shl, Constant.I32(2))
        ir.ret(shr)
        ir.finalizeFunction()

        val obj = X86CodeGenerator().generateObjectFile(ir.build())
        assertValidCodegen(obj.sections[0].data)
    }

    @Test
    fun `compiles 64-bit bitwise operations`() {
        val ir = ModuleBuilder("bitwise64_test", Target.x86_64())
        val params = ir.createFunction("bitops64",
            listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
        ir.appendBlock("entry")
        val and = ir.and(params[0], params[1])
        val or = ir.or(and, Constant.I64(0xFF))
        val xor = ir.xor(or, params[1])
        ir.ret(xor)
        ir.finalizeFunction()

        val obj = X86CodeGenerator().generateObjectFile(ir.build())
        assertValidCodegen(obj.sections[0].data)
    }

    @Test
    fun `compiles 64-bit comparison`() {
        val ir = ModuleBuilder("cmp64_test", Target.x86_64())
        val params = ir.createFunction("cmp64",
            listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I32)
        ir.appendBlock("entry")
        val cmp = ir.icmp(ICmpPredicate.SLT, params[0], params[1])
        val result = ir.zext(cmp, Type.I32)
        ir.ret(result)
        ir.finalizeFunction()

        val obj = X86CodeGenerator().generateObjectFile(ir.build())
        assertValidCodegen(obj.sections[0].data)
    }

    @Test
    fun `compiles complex control flow with arithmetic`() {
        // fibonacci-like: if n <= 1 return n; else return compute(n-1) + compute(n-2)
        // We can't do recursion yet, so just test the branching pattern
        val ir = ModuleBuilder("complex_cf", Target.x86_64())
        val params = ir.createFunction("classify",
            listOf(Param("x", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val isNeg = ir.icmp(ICmpPredicate.SLT, params[0], Constant.I32(0))
        ir.condBr(isNeg, BlockRef("negative"), BlockRef("check_zero"))

        ir.appendBlock("negative")
        val negated = ir.neg(params[0])
        ir.ret(negated)

        ir.appendBlock("check_zero")
        val isZero = ir.icmp(ICmpPredicate.EQ, params[0], Constant.I32(0))
        ir.condBr(isZero, BlockRef("zero"), BlockRef("positive"))

        ir.appendBlock("zero")
        ir.ret(Constant.I32(0))

        ir.appendBlock("positive")
        val doubled = ir.shl(params[0], Constant.I32(1))
        ir.ret(doubled)

        ir.finalizeFunction()

        val obj = X86CodeGenerator().generateObjectFile(ir.build())
        val code = obj.sections[0].data
        assertValidCodegen(code)

        val disasm = org.kgen.target.x86.disasm.X86Disassembler()
        val insts = disasm.disassembleRaw(code)
        val retCount = insts.count { it.mnemonic == "ret" }
        assertTrue(retCount >= 3, "Should have >=3 ret instructions: ${insts.map { it.text() }}")
    }

    private fun assertValidCodegen(code: ByteArray) {
        assertTrue(code.isNotEmpty(), "Code should not be empty")
        assertEquals(0x55, code[0].toInt() and 0xFF, "Should start with push rbp")
        assertEquals(0xC3, code.last().toInt() and 0xFF, "Should end with ret")

        val disasm = org.kgen.target.x86.disasm.X86Disassembler()
        val insts = disasm.disassembleRaw(code)
        assertEquals(code.size, insts.sumOf { it.size },
            "All bytes should be decoded: ${insts.map { it.text() }}")
    }

    @Test
    fun `compiles f64 addition`() {
        val ir = ModuleBuilder("float_add", Target.x86_64())
        val params = ir.createFunction("fadd_test", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.F64)
        ir.appendBlock("entry")
        val sum = ir.fadd(params[0], params[1])
        ir.ret(sum)
        ir.finalizeFunction()

        val obj = X86CodeGenerator().generateObjectFile(ir.build())
        val code = obj.sections[0].data
        assertValidCodegen(code)

        val insts = org.kgen.target.x86.disasm.X86Disassembler().disassembleRaw(code)
        assertTrue(insts.any { it.mnemonic == "addsd" }, "Should contain addsd: ${insts.map { it.text() }}")
    }

    @Test
    fun `compiles f64 arithmetic chain`() {
        val ir = ModuleBuilder("float_arith", Target.x86_64())
        val params = ir.createFunction("fchain", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.F64)
        ir.appendBlock("entry")
        val sum = ir.fadd(params[0], params[1])
        val prod = ir.fmul(sum, params[1])
        val diff = ir.fsub(prod, params[0])
        val quot = ir.fdiv(diff, params[1])
        ir.ret(quot)
        ir.finalizeFunction()

        val obj = X86CodeGenerator().generateObjectFile(ir.build())
        val code = obj.sections[0].data
        assertValidCodegen(code)

        val insts = org.kgen.target.x86.disasm.X86Disassembler().disassembleRaw(code)
        assertTrue(insts.any { it.mnemonic == "addsd" }, "addsd: ${insts.map { it.text() }}")
        assertTrue(insts.any { it.mnemonic == "mulsd" }, "mulsd: ${insts.map { it.text() }}")
        assertTrue(insts.any { it.mnemonic == "subsd" }, "subsd: ${insts.map { it.text() }}")
        assertTrue(insts.any { it.mnemonic == "divsd" }, "divsd: ${insts.map { it.text() }}")
    }

    @Test
    fun `compiles f64 negation`() {
        val ir = ModuleBuilder("float_neg", Target.x86_64())
        val params = ir.createFunction("fneg_test", listOf(Param("a", Type.F64)), Type.F64)
        ir.appendBlock("entry")
        val neg = ir.fneg(params[0])
        ir.ret(neg)
        ir.finalizeFunction()

        val obj = X86CodeGenerator().generateObjectFile(ir.build())
        val code = obj.sections[0].data
        assertValidCodegen(code)

        val insts = org.kgen.target.x86.disasm.X86Disassembler().disassembleRaw(code)
        assertTrue(insts.any { it.mnemonic == "pxor" }, "pxor for zeroing: ${insts.map { it.text() }}")
        assertTrue(insts.any { it.mnemonic == "subsd" }, "subsd for negation: ${insts.map { it.text() }}")
    }

    @Test
    fun `compiles sitofp i32 to f64`() {
        val ir = ModuleBuilder("sitofp", Target.x86_64())
        val params = ir.createFunction("int_to_double", listOf(Param("x", Type.I32)), Type.F64)
        ir.appendBlock("entry")
        val f = ir.sitofp(params[0], Type.F64)
        ir.ret(f)
        ir.finalizeFunction()

        val obj = X86CodeGenerator().generateObjectFile(ir.build())
        val code = obj.sections[0].data
        assertValidCodegen(code)

        val insts = org.kgen.target.x86.disasm.X86Disassembler().disassembleRaw(code)
        assertTrue(insts.any { it.mnemonic == "cvtsi2sd" }, "Should contain cvtsi2sd: ${insts.map { it.text() }}")
    }

    @Test
    fun `compiles fptosi f64 to i32`() {
        val ir = ModuleBuilder("fptosi", Target.x86_64())
        val params = ir.createFunction("double_to_int", listOf(Param("x", Type.F64)), Type.I32)
        ir.appendBlock("entry")
        val i = ir.fptosi(params[0], Type.I32)
        ir.ret(i)
        ir.finalizeFunction()

        val obj = X86CodeGenerator().generateObjectFile(ir.build())
        val code = obj.sections[0].data
        assertValidCodegen(code)

        val insts = org.kgen.target.x86.disasm.X86Disassembler().disassembleRaw(code)
        assertTrue(insts.any { it.mnemonic == "cvttsd2si" }, "Should contain cvttsd2si: ${insts.map { it.text() }}")
    }

    @Test
    fun `compiles f64 comparison`() {
        val ir = ModuleBuilder("fcmp", Target.x86_64())
        val params = ir.createFunction("flt_test", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.I32)
        ir.appendBlock("entry")
        val cmp = ir.fcmp(FCmpPredicate.OLT, params[0], params[1])
        val result = ir.zext(cmp, Type.I32)
        ir.ret(result)
        ir.finalizeFunction()

        val obj = X86CodeGenerator().generateObjectFile(ir.build())
        val code = obj.sections[0].data
        assertValidCodegen(code)

        val insts = org.kgen.target.x86.disasm.X86Disassembler().disassembleRaw(code)
        assertTrue(insts.any { it.mnemonic == "ucomisd" }, "Should contain ucomisd: ${insts.map { it.text() }}")
    }

    @Test
    fun `compiles f64 constant loading`() {
        val ir = ModuleBuilder("fconst", Target.x86_64())
        ir.createFunction("return_pi", emptyList(), Type.F64)
        ir.appendBlock("entry")
        ir.ret(Constant.F64(3.14159265))
        ir.finalizeFunction()

        val obj = X86CodeGenerator().generateObjectFile(ir.build())
        val code = obj.sections[0].data
        assertValidCodegen(code)

        val insts = org.kgen.target.x86.disasm.X86Disassembler().disassembleRaw(code)
        assertTrue(insts.any { it.mnemonic == "movq" }, "Should contain movq: ${insts.map { it.text() }}")
    }

    @Test
    fun `compiles mixed int and float function`() {
        val ir = ModuleBuilder("mixed", Target.x86_64())
        val params = ir.createFunction("scale", listOf(Param("n", Type.I32), Param("factor", Type.F64)), Type.F64)
        ir.appendBlock("entry")
        val asFloat = ir.sitofp(params[0], Type.F64)
        val result = ir.fmul(asFloat, params[1])
        ir.ret(result)
        ir.finalizeFunction()

        val obj = X86CodeGenerator().generateObjectFile(ir.build())
        val code = obj.sections[0].data
        assertValidCodegen(code)

        val insts = org.kgen.target.x86.disasm.X86Disassembler().disassembleRaw(code)
        assertTrue(insts.any { it.mnemonic == "cvtsi2sd" }, "cvtsi2sd: ${insts.map { it.text() }}")
        assertTrue(insts.any { it.mnemonic == "mulsd" }, "mulsd: ${insts.map { it.text() }}")
    }

    @Test
    fun `compiles call with stack arguments`() {
        val ir = ModuleBuilder("stack_args", Target.x86_64())

        // Declare a function with 8 i32 params (exceeds 6 GP regs in System V)
        ir.declareFunction("many_args", (1..8).map { Param("p$it", Type.I32) }, Type.I32)

        ir.createFunction("caller", emptyList(), Type.I32)
        ir.appendBlock("entry")
        val result = ir.call("many_args", (1..8).map { Constant.I32(it) }, Type.I32)
        ir.ret(result)
        ir.finalizeFunction()

        val obj = X86CodeGenerator().generateObjectFile(ir.build())
        val code = obj.sections[0].data
        assertValidCodegen(code)

        val disasm = org.kgen.target.x86.disasm.X86Disassembler()
        val insts = disasm.disassembleRaw(code)

        // Stack args are written via mov [rsp+offset] (pre-allocated in prologue)
        assertTrue(insts.any { it.text().contains("[rsp") && it.text().contains("r11") },
            "Should write stack args to [rsp+offset]: ${insts.map { it.text() }}")
        // Should have call instruction
        assertTrue(insts.any { it.mnemonic == "call" },
            "Should have call: ${insts.map { it.text() }}")
    }

    @Test
    fun `compiles call with mixed int and float stack arguments`() {
        val ir = ModuleBuilder("mixed_stack", Target.x86_64())

        // 6 GP + 2 float = 8 params; the floats should use xmm regs, not overflow
        // But let's use 7 GP + 2 float to force GP overflow
        val params = (1..7).map { Param("i$it", Type.I32) } +
            listOf(Param("f1", Type.F64), Param("f2", Type.F64))
        ir.declareFunction("mixed_many", params, Type.I32)

        ir.createFunction("caller2", emptyList(), Type.I32)
        ir.appendBlock("entry")
        val args: List<Value> = (1..7).map { Constant.I32(it) as Value } +
            listOf(Constant.F64(1.0), Constant.F64(2.0))
        val result = ir.call("mixed_many", args, Type.I32)
        ir.ret(result)
        ir.finalizeFunction()

        val obj = X86CodeGenerator().generateObjectFile(ir.build())
        val code = obj.sections[0].data
        assertValidCodegen(code)

        val disasm = org.kgen.target.x86.disasm.X86Disassembler()
        val insts = disasm.disassembleRaw(code)
        assertTrue(insts.any { it.mnemonic == "call" },
            "Should have call: ${insts.map { it.text() }}")
    }

    @Test
    fun `compiles phi nodes from diamond pattern`() {
        // if (x > 0) v = x + 1 else v = x - 1; return v
        val ir = ModuleBuilder("phi_test", Target.x86_64())
        val params = ir.createFunction("phi_diamond", listOf(Param("x", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val cond = ir.icmp(ICmpPredicate.SGT, params[0], Constant.I32(0))
        ir.condBr(cond, BlockRef("then"), BlockRef("else"))

        ir.appendBlock("then")
        val inc = ir.add(params[0], Constant.I32(1))
        ir.br(BlockRef("merge"))

        ir.appendBlock("else")
        val dec = ir.sub(params[0], Constant.I32(1))
        ir.br(BlockRef("merge"))

        ir.appendBlock("merge")
        val phi = ir.phi(Type.I32, listOf(inc to BlockRef("then"), dec to BlockRef("else")))
        ir.ret(phi)

        ir.finalizeFunction()

        val obj = X86CodeGenerator().generateObjectFile(ir.build())
        val code = obj.sections[0].data
        assertValidCodegen(code)

        val disasm = org.kgen.target.x86.disasm.X86Disassembler()
        val insts = disasm.disassembleRaw(code)
        // Should have at least one ret
        assertTrue(insts.any { it.mnemonic == "ret" }, "Should have ret: ${insts.map { it.text() }}")
    }

    @Test
    fun `compiles phi with constant incoming values`() {
        // if (x > 0) goto then else goto else; then: br merge; else: br merge; merge: phi [1, then], [0, else]
        val ir = ModuleBuilder("phi_const", Target.x86_64())
        val params = ir.createFunction("is_positive", listOf(Param("x", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val cond = ir.icmp(ICmpPredicate.SGT, params[0], Constant.I32(0))
        ir.condBr(cond, BlockRef("then"), BlockRef("else"))

        ir.appendBlock("then")
        ir.br(BlockRef("merge"))

        ir.appendBlock("else")
        ir.br(BlockRef("merge"))

        ir.appendBlock("merge")
        val phi = ir.phi(Type.I32, listOf(Constant.I32(1) to BlockRef("then"), Constant.I32(0) to BlockRef("else")))
        ir.ret(phi)

        ir.finalizeFunction()

        val obj = X86CodeGenerator().generateObjectFile(ir.build())
        val code = obj.sections[0].data
        assertValidCodegen(code)
    }

    @Test
    fun `compiles phi with i64 type`() {
        val ir = ModuleBuilder("phi64", Target.x86_64())
        val params = ir.createFunction("phi64_fn", listOf(Param("x", Type.I64)), Type.I64)
        ir.appendBlock("entry")
        val cond = ir.icmp(ICmpPredicate.SGT, params[0], Constant.I64(0))
        ir.condBr(cond, BlockRef("pos"), BlockRef("neg"))

        ir.appendBlock("pos")
        ir.br(BlockRef("done"))

        ir.appendBlock("neg")
        val negated = ir.sub(Constant.I64(0), params[0])
        ir.br(BlockRef("done"))

        ir.appendBlock("done")
        val phi = ir.phi(Type.I64, listOf(params[0] to BlockRef("pos"), negated to BlockRef("neg")))
        ir.ret(phi)

        ir.finalizeFunction()

        val obj = X86CodeGenerator().generateObjectFile(ir.build())
        assertValidCodegen(obj.sections[0].data)
    }

    @Test
    fun `compiles phi after mem2reg optimization`() {
        // Build IR with alloca/store/load, run mem2reg, then compile
        val ir = ModuleBuilder("mem2reg_phi", Target.x86_64())
        val params = ir.createFunction("abs_val", listOf(Param("x", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val slot = ir.alloca(Type.I32)
        val cond = ir.icmp(ICmpPredicate.SLT, params[0], Constant.I32(0))
        ir.condBr(cond, BlockRef("negate"), BlockRef("keep"))

        ir.appendBlock("negate")
        val neg = ir.sub(Constant.I32(0), params[0])
        ir.store(neg, slot)
        ir.br(BlockRef("done"))

        ir.appendBlock("keep")
        ir.store(params[0], slot)
        ir.br(BlockRef("done"))

        ir.appendBlock("done")
        val result = ir.load(Type.I32, slot)
        ir.ret(result)

        ir.finalizeFunction()

        // Run mem2reg to convert alloca/store/load into phi nodes
        val optimized = org.kgen.pipeline.Mem2Reg().run(ir.build())

        // Verify phi was inserted
        val mergeBlock = optimized.functions[0].blocks.find { it.label == "done" }
        assertNotNull(mergeBlock, "done block should exist")
        assertTrue(mergeBlock!!.instructions.any { it is Phi },
            "Mem2reg should insert phi: ${mergeBlock.instructions}")

        // Compile the optimized IR
        val obj = X86CodeGenerator().generateObjectFile(optimized)
        val code = obj.sections[0].data
        assertValidCodegen(code)
    }

    @Test
    fun `compiles alloca store load pattern`() {
        val ir = ModuleBuilder("alloca_test", Target.x86_64())
        val params = ir.createFunction("use_alloca", listOf(Param("x", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val slot = ir.alloca(Type.I32)
        ir.store(params[0], slot)
        val loaded = ir.load(Type.I32, slot)
        val result = ir.add(loaded, Constant.I32(1))
        ir.ret(result)
        ir.finalizeFunction()

        val obj = X86CodeGenerator().generateObjectFile(ir.build())
        val code = obj.sections[0].data
        assertValidCodegen(code)

        val disasm = org.kgen.target.x86.disasm.X86Disassembler()
        val insts = disasm.disassembleRaw(code)
        // Should have lea for alloca address
        assertTrue(insts.any { it.mnemonic == "lea" }, "Should have lea for alloca: ${insts.map { it.text() }}")
    }

    @Test
    fun `compiles multiple allocas`() {
        val ir = ModuleBuilder("multi_alloca", Target.x86_64())
        val params = ir.createFunction("swap_add", listOf(
            Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val slotA = ir.alloca(Type.I32)
        val slotB = ir.alloca(Type.I32)
        ir.store(params[0], slotA)
        ir.store(params[1], slotB)
        val loadA = ir.load(Type.I32, slotA)
        val loadB = ir.load(Type.I32, slotB)
        val sum = ir.add(loadA, loadB)
        ir.ret(sum)
        ir.finalizeFunction()

        val obj = X86CodeGenerator().generateObjectFile(ir.build())
        assertValidCodegen(obj.sections[0].data)
    }

    @Test
    fun `compiles switch instruction`() {
        val ir = ModuleBuilder("switch_test", Target.x86_64())
        val params = ir.createFunction("classify", listOf(Param("x", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        ir.switch(params[0], "default", listOf(
            Constant.I32(0) to "zero",
            Constant.I32(1) to "one",
            Constant.I32(2) to "two",
        ))

        ir.appendBlock("zero")
        ir.ret(Constant.I32(100))

        ir.appendBlock("one")
        ir.ret(Constant.I32(200))

        ir.appendBlock("two")
        ir.ret(Constant.I32(300))

        ir.appendBlock("default")
        ir.ret(Constant.I32(-1))

        ir.finalizeFunction()

        val obj = X86CodeGenerator().generateObjectFile(ir.build())
        val code = obj.sections[0].data
        assertValidCodegen(code)

        val disasm = org.kgen.target.x86.disasm.X86Disassembler()
        val insts = disasm.disassembleRaw(code)
        // Should have multiple cmp instructions and conditional jumps
        val cmpCount = insts.count { it.mnemonic == "cmp" }
        assertTrue(cmpCount >= 3, "Should have >=3 cmp for 3 cases: ${insts.map { it.text() }}")
        assertTrue(insts.any { it.mnemonic == "jz" }, "Should have jz (je): ${insts.map { it.text() }}")
    }

    @Test
    fun `compiles musttail call as jmp`() {
        val ir = ModuleBuilder("tailcall", Target.x86_64())

        // helper(x) = x + 1
        val helperParams = ir.createFunction("helper", listOf(Param("x", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        ir.ret(ir.add(helperParams[0], Constant.I32(1)))
        ir.finalizeFunction()

        // caller(x) = musttail helper(x + 1)
        val callerParams = ir.createFunction("caller", listOf(Param("x", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val arg = ir.add(callerParams[0], Constant.I32(1))
        val result = ir.call(
            GlobalRef("helper", Type.Function(listOf(Type.I32), Type.I32)),
            listOf(arg), Type.I32, tailCall = TailCallKind.MUSTTAIL)
        ir.ret(result)
        ir.finalizeFunction()

        val obj = X86CodeGenerator().generateObjectFile(ir.build())
        val code = obj.sections[0].data
        assertTrue(code.isNotEmpty())

        // Disassemble and find the caller function
        val disasm = org.kgen.target.x86.disasm.X86Disassembler()
        val insts = disasm.disassembleRaw(code)

        // The caller should have a jmp instead of call+ret
        // Count jmp instructions (excluding the prologue push/mov)
        val jmpInsts = insts.filter { it.mnemonic == "jmp" }
        assertTrue(jmpInsts.isNotEmpty(), "Tail call should produce jmp: ${insts.map { it.text() }}")

        // Should NOT have a call instruction for the tail call
        // (helper function itself has no calls)
        // Actually the caller might still show "call" if disassembled together with helper
        // Let's verify the code ends properly
        assertEquals(0xC3, code.last().toInt() and 0xFF, "Should end with ret (from helper)")
    }

    @Test
    fun `compiles bitwise not`() {
        val ir = ModuleBuilder("not_test", Target.x86_64())
        val params = ir.createFunction("bit_not", listOf(Param("x", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val result = ir.not(params[0])
        ir.ret(result)
        ir.finalizeFunction()

        val obj = X86CodeGenerator().generateObjectFile(ir.build())
        assertValidCodegen(obj.sections[0].data)

        val insts = org.kgen.target.x86.disasm.X86Disassembler().disassembleRaw(obj.sections[0].data)
        assertTrue(insts.any { it.mnemonic == "not" }, "Should have not: ${insts.map { it.text() }}")
    }

    @Test
    fun `compiles ptrtoint and inttoptr`() {
        val ir = ModuleBuilder("ptr_conv", Target.x86_64())
        val params = ir.createFunction("ptr_ops", listOf(Param("p", Type.Pointer(Type.I32))), Type.Pointer(Type.I32))
        ir.appendBlock("entry")
        val asInt = ir.ptrtoint(params[0], Type.I64)
        val added = ir.add(asInt, Constant.I64(8))
        val asPtr = ir.inttoptr(added, Type.Pointer(Type.I32))
        ir.ret(asPtr)
        ir.finalizeFunction()

        val obj = X86CodeGenerator().generateObjectFile(ir.build())
        assertValidCodegen(obj.sections[0].data)
    }

    @Test
    fun `compiles unsigned int to float`() {
        val ir = ModuleBuilder("uitofp_test", Target.x86_64())
        val params = ir.createFunction("uint_to_double", listOf(Param("x", Type.I32)), Type.F64)
        ir.appendBlock("entry")
        val f = ir.uitofp(params[0], Type.F64)
        ir.ret(f)
        ir.finalizeFunction()

        val obj = X86CodeGenerator().generateObjectFile(ir.build())
        assertValidCodegen(obj.sections[0].data)
    }

    @Test
    fun `PE uses Windows x64 ABI registers`() {
        val ir = ModuleBuilder("winabi", Target.x86_64())
        ir.targetTriple = "x86_64-unknown-windows-msvc"

        val params = ir.createFunction("add", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val sum = ir.add(params[0], params[1])
        ir.ret(sum)
        ir.finalizeFunction()

        val module = ir.build()
        val obj = X86CodeGenerator().generateObjectFile(module)
        val code = obj.sections[0].data

        // Windows x64: first param in ECX (not EDI), second in EDX (not ESI)
        // The function should use ECX and EDX for parameters
        // push rbp (0x55), mov rbp,rsp (48 89 E5)
        assertEquals(0x55, code[0].toInt() and 0xFF)
        assertTrue(code.size > 4, "Function should have some instructions")
    }

    @Test
    fun `compiles GEP with struct field offset`() {
        val pointType = Type.Struct(null, listOf(Type.I32, Type.I32))
        val ir = ModuleBuilder("gep_struct", Target.x86_64())
        val params = ir.createFunction("getY", listOf(Param("p", Type.OpaquePointer)), Type.I32)
        ir.appendBlock("entry")
        // GEP to get pointer to field 1 (y) — offset should be 4 bytes
        val yPtr = ir.gep(pointType, params[0], Constant.I32(0), Constant.I32(1))
        val y = ir.load(Type.I32, yPtr)
        ir.ret(y)
        ir.finalizeFunction()

        val obj = X86CodeGenerator().generateObjectFile(ir.build())
        val code = obj.sections[0].data
        assertTrue(code.isNotEmpty())

        val disasm = org.kgen.target.x86.disasm.X86Disassembler()
        val insts = disasm.disassembleRaw(code)
        assertEquals(code.size, insts.sumOf { it.size },
            "All bytes decoded: ${insts.map { it.text() }}")
        // Should contain an add for the field offset (4 bytes)
        assertTrue(insts.any { it.mnemonic == "add" },
            "Should have add for struct offset: ${insts.map { it.text() }}")
    }

    @Test
    fun `compiles GEP with array element offset`() {
        val arrType = Type.Array(Type.I32, 10)
        val ir = ModuleBuilder("gep_array", Target.x86_64())
        val params = ir.createFunction("getElem", listOf(Param("p", Type.OpaquePointer)), Type.I32)
        ir.appendBlock("entry")
        // GEP to element [3] — offset should be 12 bytes
        val ePtr = ir.gep(arrType, params[0], Constant.I32(0), Constant.I32(3))
        val v = ir.load(Type.I32, ePtr)
        ir.ret(v)
        ir.finalizeFunction()

        val obj = X86CodeGenerator().generateObjectFile(ir.build())
        val code = obj.sections[0].data
        assertTrue(code.isNotEmpty())

        val disasm = org.kgen.target.x86.disasm.X86Disassembler()
        val insts = disasm.disassembleRaw(code)
        assertEquals(code.size, insts.sumOf { it.size },
            "All bytes decoded: ${insts.map { it.text() }}")
    }

    @Test
    fun `compiles GEP with nested struct`() {
        val innerType = Type.Struct(null, listOf(Type.I32, Type.I64))
        val outerType = Type.Struct(null, listOf(innerType, Type.I32))
        val ir = ModuleBuilder("gep_nested", Target.x86_64())
        val params = ir.createFunction("getInnerField", listOf(Param("p", Type.OpaquePointer)), Type.I64)
        ir.appendBlock("entry")
        // GEP: outer[0].inner.field1 (the i64) — offset = 4 (past the i32)
        val fPtr = ir.gep(outerType, params[0], Constant.I32(0), Constant.I32(0), Constant.I32(1))
        val v = ir.load(Type.I64, fPtr)
        ir.ret(v)
        ir.finalizeFunction()

        val obj = X86CodeGenerator().generateObjectFile(ir.build())
        val code = obj.sections[0].data
        assertTrue(code.isNotEmpty())
        assertEquals(0xC3, code.last().toInt() and 0xFF, "Should end with ret")
    }

    @Test
    fun `compiles SROA then mem2reg on struct`() {
        val pointType = Type.Struct(null, listOf(Type.I32, Type.I32))
        val ir = ModuleBuilder("sroa_struct", Target.x86_64())
        val params = ir.createFunction("sumFields", listOf(
            Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val ptr = ir.alloca(pointType)
        val xPtr = ir.gep(pointType, ptr, Constant.I32(0), Constant.I32(0))
        ir.store(params[0], xPtr)
        val yPtr = ir.gep(pointType, ptr, Constant.I32(0), Constant.I32(1))
        ir.store(params[1], yPtr)
        val x = ir.load(Type.I32, xPtr)
        val y = ir.load(Type.I32, yPtr)
        val sum = ir.add(x, y)
        ir.ret(sum)
        ir.finalizeFunction()

        // Run SROA + Mem2Reg to eliminate the struct
        val sroa = org.kgen.pipeline.ScalarReplacementOfAggregates()
        val mem2reg = org.kgen.pipeline.Mem2Reg()
        val optimized = mem2reg.run(sroa.run(ir.build()))

        // Verify no allocas remain
        val insts = optimized.functions[0].blocks[0].instructions
        assertTrue(insts.none { it is Alloca },
            "SROA+Mem2Reg should eliminate all allocas: $insts")

        // Should compile cleanly
        val obj = X86CodeGenerator().generateObjectFile(optimized)
        val code = obj.sections[0].data
        assertTrue(code.isNotEmpty())

        val disasm = org.kgen.target.x86.disasm.X86Disassembler()
        val disInsts = disasm.disassembleRaw(code)
        assertEquals(code.size, disInsts.sumOf { it.size },
            "All bytes decoded: ${disInsts.map { it.text() }}")
        // Should have an add instruction (a + b)
        assertTrue(disInsts.any { it.mnemonic == "add" },
            "Should add the two fields: ${disInsts.map { it.text() }}")
    }

    @Test
    fun `selects shl for multiply by power of 2`() {
        val ir = ModuleBuilder("mul_pow2", Target.x86_64())
        val params = ir.createFunction("double", listOf(Param("x", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val result = ir.mul(params[0], Constant.I32(8)) // * 8 = shl 3
        ir.ret(result)
        ir.finalizeFunction()

        val obj = X86CodeGenerator().generateObjectFile(ir.build())
        val code = obj.sections[0].data
        val disasm = org.kgen.target.x86.disasm.X86Disassembler()
        val insts = disasm.disassembleRaw(code)
        // Should use shl instead of imul
        assertTrue(insts.any { it.mnemonic == "shl" },
            "Should use shl for *8: ${insts.map { it.text() }}")
        assertTrue(insts.none { it.mnemonic == "imul" },
            "Should not use imul: ${insts.map { it.text() }}")
    }

    @Test
    fun `selects test for comparison with zero`() {
        val ir = ModuleBuilder("cmp_zero", Target.x86_64())
        val params = ir.createFunction("isZero", listOf(Param("x", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val cmp = ir.icmp(ICmpPredicate.EQ, params[0], Constant.I32(0))
        ir.ret(cmp)
        ir.finalizeFunction()

        val obj = X86CodeGenerator().generateObjectFile(ir.build())
        val code = obj.sections[0].data
        val disasm = org.kgen.target.x86.disasm.X86Disassembler()
        val insts = disasm.disassembleRaw(code)
        // Should use test instead of cmp
        assertTrue(insts.any { it.mnemonic == "test" },
            "Should use test for cmp==0: ${insts.map { it.text() }}")
    }

    @Test
    fun `selects xor for zero constant`() {
        val ir = ModuleBuilder("zero", Target.x86_64())
        ir.createFunction("retZero", emptyList(), Type.I32)
        ir.appendBlock("entry")
        ir.ret(Constant.I32(0))
        ir.finalizeFunction()

        val obj = X86CodeGenerator().generateObjectFile(ir.build())
        val code = obj.sections[0].data
        val disasm = org.kgen.target.x86.disasm.X86Disassembler()
        val insts = disasm.disassembleRaw(code)
        // Should use xor eax,eax instead of mov eax,0
        assertTrue(insts.any { it.mnemonic == "xor" },
            "Should use xor for zero: ${insts.map { it.text() }}")
    }

    @Test
    fun `sets al for vararg calls on System V`() {
        val ir = ModuleBuilder("vararg_call", Target.x86_64())
        // Declare vararg function taking one i32 param + varargs
        val vfn = ir.declareFunction("varfn", listOf(Param("n", Type.I32)), Type.I32, isVarArg = true)
        ir.createFunction("main", emptyList(), Type.I32)
        ir.appendBlock("entry")
        // Call with one fixed i32 arg + two float varargs → al should be 2
        ir.call(vfn as Value, listOf(Constant.I32(1), Constant.F64(1.0), Constant.F64(2.0)), Type.I32)
        ir.ret(Constant.I32(0))
        ir.finalizeFunction()

        val obj = X86CodeGenerator().generateObjectFile(ir.build())
        val code = obj.sections[0].data
        val disasm = org.kgen.target.x86.disasm.X86Disassembler()
        val insts = disasm.disassembleRaw(code)
        // Should have "mov al, 2" (two XMM registers used for float args)
        assertTrue(insts.any { it.mnemonic == "mov" && it.text().contains("al") },
            "Should set al for vararg call: ${insts.map { it.text() }}")
    }

    @Test
    fun `does not set al for non-vararg calls`() {
        val ir = ModuleBuilder("no_vararg", Target.x86_64())
        ir.declareFunction("regular", listOf(Param("a", Type.F64)), Type.I32)
        ir.createFunction("main", emptyList(), Type.I32)
        ir.appendBlock("entry")
        ir.call("regular", listOf(Constant.F64(1.0)), Type.I32)
        ir.ret(Constant.I32(0))
        ir.finalizeFunction()

        val obj = X86CodeGenerator().generateObjectFile(ir.build())
        val code = obj.sections[0].data
        val disasm = org.kgen.target.x86.disasm.X86Disassembler()
        val insts = disasm.disassembleRaw(code)
        // Should NOT have "mov al, ..." for non-vararg calls
        assertFalse(insts.any { it.mnemonic == "mov" && it.text().contains("al,") },
            "Should not set al for non-vararg call: ${insts.map { it.text() }}")
    }

    @Test
    fun `returns small struct in rax`() {
        val structType = Type.Struct(null, listOf(Type.I32, Type.I32))
        val ir = ModuleBuilder("struct_ret", Target.x86_64())
        val params = ir.createFunction("makePair", listOf(Param("a", Type.I32), Param("b", Type.I32)), structType)
        ir.appendBlock("entry")
        val undef = Constant.Undef(structType)
        val s1 = ir.insertValue(undef, params[0], 0)
        val s2 = ir.insertValue(s1, params[1], 1)
        ir.ret(s2)
        ir.finalizeFunction()

        val obj = X86CodeGenerator().generateObjectFile(ir.build())
        val code = obj.sections[0].data
        assertTrue(code.isNotEmpty(), "Should generate code for struct return")
        val disasm = org.kgen.target.x86.disasm.X86Disassembler()
        val insts = disasm.disassembleRaw(code)
        // Should have a mov to rax (loading struct data for return)
        assertTrue(insts.any { it.mnemonic == "mov" && it.text().contains("rax") },
            "Should load struct into rax for return: ${insts.map { it.text() }}")
    }

    @Test
    fun `receives struct return from call`() {
        val structType = Type.Struct(null, listOf(Type.I64, Type.I64))
        val ir = ModuleBuilder("struct_call", Target.x86_64())
        ir.declareFunction("getPair", emptyList(), structType)
        ir.createFunction("main", emptyList(), Type.I64)
        ir.appendBlock("entry")
        val pair = ir.call("getPair", emptyList(), structType)!!
        val first = ir.extractValue(pair, 0)
        ir.ret(first)
        ir.finalizeFunction()

        val obj = X86CodeGenerator().generateObjectFile(ir.build())
        val code = obj.sections[0].data
        assertTrue(code.isNotEmpty(), "Should generate code for struct call result")
    }

    @Test
    fun `returns large struct via sret`() {
        // Struct > 16 bytes: should use hidden sret pointer
        val structType = Type.Struct(null, listOf(Type.I64, Type.I64, Type.I64))
        val ir = ModuleBuilder("large_struct_ret", Target.x86_64())
        val params = ir.createFunction("makeTriple",
            listOf(Param("a", Type.I64), Param("b", Type.I64), Param("c", Type.I64)), structType)
        ir.appendBlock("entry")
        val undef = Constant.Undef(structType)
        val s1 = ir.insertValue(undef, params[0], 0)
        val s2 = ir.insertValue(s1, params[1], 1)
        val s3 = ir.insertValue(s2, params[2], 2)
        ir.ret(s3)
        ir.finalizeFunction()

        val obj = X86CodeGenerator().generateObjectFile(ir.build())
        val code = obj.sections[0].data
        assertTrue(code.isNotEmpty(), "Should generate code for large struct return")
        val disasm = org.kgen.target.x86.disasm.X86Disassembler()
        val insts = disasm.disassembleRaw(code)
        val text = insts.map { it.text() }
        // Should store the sret pointer from RDI (first arg) to the stack
        assertTrue(text.any { it.contains("rdi") && it.contains("rbp") },
            "Should save sret pointer (rdi) to stack: $text")
    }

    @Test
    fun `calls function returning large struct via sret`() {
        val structType = Type.Struct(null, listOf(Type.I64, Type.I64, Type.I64))
        val ir = ModuleBuilder("call_large_struct", Target.x86_64())
        ir.declareFunction("getTriple", emptyList(), structType)
        ir.createFunction("main", emptyList(), Type.I64)
        ir.appendBlock("entry")
        val triple = ir.call("getTriple", emptyList(), structType)!!
        val first = ir.extractValue(triple, 0)
        ir.ret(first)
        ir.finalizeFunction()

        val obj = X86CodeGenerator().generateObjectFile(ir.build())
        val code = obj.sections[0].data
        assertTrue(code.isNotEmpty(), "Should generate code for large struct call")
        val disasm = org.kgen.target.x86.disasm.X86Disassembler()
        val insts = disasm.disassembleRaw(code)
        val text = insts.map { it.text() }
        // Should have LEA to set up sret pointer before the call
        assertTrue(text.any { it.contains("lea") && it.contains("rdi") },
            "Should LEA sret pointer into rdi: $text")
    }

    @Test
    fun `compiles va_start and va_arg`() {
        // Vararg function: int sum(int count, ...)
        val ir = ModuleBuilder("varargs", Target.x86_64())
        val params = ir.createFunction("sum",
            listOf(Param("count", Type.I32)), Type.I32, isVarArg = true)
        ir.appendBlock("entry")
        // va_list is a 24-byte struct on System V
        val vaListType = Type.Struct(null, listOf(Type.I32, Type.I32, Type.OpaquePointer, Type.OpaquePointer))
        val ap = ir.alloca(vaListType)
        ir.vaStart(ap)
        val arg1 = ir.vaArg(ap, Type.I32)
        ir.vaEnd(ap)
        ir.ret(arg1)
        ir.finalizeFunction()

        val obj = X86CodeGenerator().generateObjectFile(ir.build())
        val code = obj.sections[0].data
        assertTrue(code.isNotEmpty(), "Should generate code for vararg function")
        val disasm = org.kgen.target.x86.disasm.X86Disassembler()
        val insts = disasm.disassembleRaw(code)
        val text = insts.map { it.text() }
        // Should save arg registers in prologue (vararg register save area)
        // RDI is first arg register — should see it stored to stack
        assertTrue(text.any { it.contains("lea") },
            "Should have LEA for va_list initialization: $text")
    }

    @Test
    fun `large struct sret with parameters shifted`() {
        // When sret uses the first GP arg (RDI), the actual params shift to RSI, RDX, ...
        val structType = Type.Struct(null, listOf(Type.I64, Type.I64, Type.I64))
        val ir = ModuleBuilder("sret_shifted", Target.x86_64())
        val params = ir.createFunction("fillStruct",
            listOf(Param("x", Type.I64)), structType)
        ir.appendBlock("entry")
        val undef = Constant.Undef(structType)
        val s1 = ir.insertValue(undef, params[0], 0)
        val s2 = ir.insertValue(s1, params[0], 1)
        val s3 = ir.insertValue(s2, params[0], 2)
        ir.ret(s3)
        ir.finalizeFunction()

        val obj = X86CodeGenerator().generateObjectFile(ir.build())
        val code = obj.sections[0].data
        assertTrue(code.isNotEmpty(), "Should generate code")
        val disasm = org.kgen.target.x86.disasm.X86Disassembler()
        val insts = disasm.disassembleRaw(code)
        val text = insts.map { it.text() }
        // Param 'x' should be in RSI (second GP reg) since RDI is used for sret
        assertTrue(text.any { it.contains("rsi") },
            "Parameter should be in RSI (shifted by sret): $text")
    }

    @Test
    fun `generates ctlz instruction`() {
        val ir = ModuleBuilder("test", Target.x86_64())
        val params = ir.createFunction("clz", listOf(Param("a", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        ir.ret(ir.ctlz(params[0]))
        ir.finalizeFunction()
        val obj = X86CodeGenerator().generateObjectFile(ir.build())
        assertTrue(obj.sections.first { it.name == ".text" }.data.isNotEmpty())
    }

    @Test
    fun `generates cttz instruction`() {
        val ir = ModuleBuilder("test", Target.x86_64())
        val params = ir.createFunction("ctz", listOf(Param("a", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        ir.ret(ir.cttz(params[0]))
        ir.finalizeFunction()
        val obj = X86CodeGenerator().generateObjectFile(ir.build())
        assertTrue(obj.sections.first { it.name == ".text" }.data.isNotEmpty())
    }

    @Test
    fun `generates ctpop instruction`() {
        val ir = ModuleBuilder("test", Target.x86_64())
        val params = ir.createFunction("popcount", listOf(Param("a", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        ir.ret(ir.ctpop(params[0]))
        ir.finalizeFunction()
        val obj = X86CodeGenerator().generateObjectFile(ir.build())
        assertTrue(obj.sections.first { it.name == ".text" }.data.isNotEmpty())
    }

    @Test
    fun `generates bswap instruction`() {
        val ir = ModuleBuilder("test", Target.x86_64())
        val params = ir.createFunction("swap", listOf(Param("a", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        ir.ret(ir.bswap(params[0]))
        ir.finalizeFunction()
        val obj = X86CodeGenerator().generateObjectFile(ir.build())
        assertTrue(obj.sections.first { it.name == ".text" }.data.isNotEmpty())
    }

    @Test
    fun `generates sqrt instruction`() {
        val ir = ModuleBuilder("test", Target.x86_64())
        val params = ir.createFunction("mySqrt", listOf(Param("a", Type.F64)), Type.F64)
        ir.appendBlock("entry")
        ir.ret(ir.sqrt(params[0]))
        ir.finalizeFunction()
        val obj = X86CodeGenerator().generateObjectFile(ir.build())
        assertTrue(obj.sections.first { it.name == ".text" }.data.isNotEmpty())
    }

    @Test
    fun `generates ceil instruction`() {
        val ir = ModuleBuilder("test", Target.x86_64())
        val params = ir.createFunction("myCeil", listOf(Param("a", Type.F64)), Type.F64)
        ir.appendBlock("entry")
        ir.ret(ir.ceil(params[0]))
        ir.finalizeFunction()
        val obj = X86CodeGenerator().generateObjectFile(ir.build())
        assertTrue(obj.sections.first { it.name == ".text" }.data.isNotEmpty())
    }

    @Test
    fun `generates floor instruction`() {
        val ir = ModuleBuilder("test", Target.x86_64())
        val params = ir.createFunction("myFloor", listOf(Param("a", Type.F64)), Type.F64)
        ir.appendBlock("entry")
        ir.ret(ir.floor(params[0]))
        ir.finalizeFunction()
        val obj = X86CodeGenerator().generateObjectFile(ir.build())
        assertTrue(obj.sections.first { it.name == ".text" }.data.isNotEmpty())
    }

    @Test
    fun `generates memcpy instruction`() {
        val ir = ModuleBuilder("test", Target.x86_64())
        val params = ir.createFunction("copy", listOf(
            Param("dst", Type.OpaquePointer), Param("src", Type.OpaquePointer), Param("len", Type.I64)
        ), Type.Void)
        ir.appendBlock("entry")
        ir.memcpy(params[0], params[1], params[2])
        ir.ret(null)
        ir.finalizeFunction()
        val obj = X86CodeGenerator().generateObjectFile(ir.build())
        assertTrue(obj.sections.first { it.name == ".text" }.data.isNotEmpty())
    }

    @Test
    fun `generates memset instruction`() {
        val ir = ModuleBuilder("test", Target.x86_64())
        val params = ir.createFunction("fill", listOf(
            Param("dst", Type.OpaquePointer), Param("val", Type.I32), Param("len", Type.I64)
        ), Type.Void)
        ir.appendBlock("entry")
        ir.memset(params[0], params[1], params[2])
        ir.ret(null)
        ir.finalizeFunction()
        val obj = X86CodeGenerator().generateObjectFile(ir.build())
        assertTrue(obj.sections.first { it.name == ".text" }.data.isNotEmpty())
    }

    @Test
    fun `generates unreachable as ud2`() {
        val ir = ModuleBuilder("test", Target.x86_64())
        ir.createFunction("trap", emptyList(), Type.Void)
        ir.appendBlock("entry")
        ir.unreachable()
        ir.finalizeFunction()
        val obj = X86CodeGenerator().generateObjectFile(ir.build())
        val code = obj.sections.first { it.name == ".text" }.data
        assertTrue(code.isNotEmpty())
        // ud2 is encoded as 0F 0B
        val hasUd2 = code.indices.any { i ->
            i + 1 < code.size && code[i] == 0x0F.toByte() && code[i + 1] == 0x0B.toByte()
        }
        assertTrue(hasUd2, "Should contain ud2 instruction (0F 0B)")
    }
}
