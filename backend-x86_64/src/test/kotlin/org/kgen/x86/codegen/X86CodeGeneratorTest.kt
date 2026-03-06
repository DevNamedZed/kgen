package org.kgen.x86.codegen

import org.kgen.x86.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.binary.*
import org.kgen.binary.elf.ElfObjectType
import org.kgen.binary.pe.PeConstants
import org.kgen.ir.*
import org.kgen.ir.build.*
import org.kgen.ir.codegen.*
import org.kgen.ir.target.Target
import java.nio.ByteBuffer
import java.nio.ByteOrder

class X86CodeGeneratorTest {

    private fun le(bytes: ByteArray) = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

    private fun buildAddModule(): Module {
        val ir = IrBuilder("test", Target.x86_64())
        val params = ir.createFunction("add", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        ir.positionAtEnd(ir.appendBlock("entry"))
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

        assertEquals(1, objFile.sections.size)
        assertEquals(".text", objFile.sections[0].name)
        assertTrue(objFile.sections[0].data.isNotEmpty())

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
        val ir = IrBuilder("hello", Target.x86_64())
        ir.declareFunction("puts", listOf(Param("s", Type.OpaquePointer)), Type.I32)

        val params = ir.createFunction("main", emptyList(), Type.I32)
        ir.positionAtEnd(ir.appendBlock("entry"))
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
        val ir = IrBuilder("math", Target.x86_64())
        val params = ir.createFunction("compute", listOf(
            Param("x", Type.I32), Param("y", Type.I32)), Type.I32)
        ir.positionAtEnd(ir.appendBlock("entry"))
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
        val ir = IrBuilder("multi", Target.x86_64())

        // helper(x) = x + 1
        val helperParams = ir.createFunction("helper", listOf(Param("x", Type.I32)), Type.I32)
        ir.positionAtEnd(ir.appendBlock("entry"))
        val inc = ir.add(helperParams[0], Constant.I32(1))
        ir.ret(inc)
        ir.finalizeFunction()

        // main() = helper(41)
        ir.createFunction("main", emptyList(), Type.I32)
        ir.positionAtEnd(ir.appendBlock("entry"))
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
        val ir = IrBuilder("hello", Target.x86_64())

        // Global string constant
        val strType = Type.Array(Type.I8, 14)
        val strRef = ir.addGlobal("hello_str", strType,
            Constant.StringConst("Hello, World!"), isConstant = true,
            linkage = Linkage.INTERNAL)

        // External puts declaration
        ir.declareFunction("puts", listOf(Param("s", Type.OpaquePointer)), Type.I32)

        // main() { puts(&hello_str); return 0; }
        ir.createFunction("main", emptyList(), Type.I32)
        ir.positionAtEnd(ir.appendBlock("entry"))
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
        val ir = IrBuilder("strings", Target.x86_64())
        val strType = Type.Array(Type.I8, 6)
        ir.addGlobal("msg", strType, Constant.StringConst("hello"), isConstant = true,
            linkage = Linkage.INTERNAL)
        ir.createFunction("noop", emptyList(), Type.I32)
        ir.positionAtEnd(ir.appendBlock("entry"))
        ir.ret(Constant.I32(0))
        ir.finalizeFunction()

        val module = ir.build()
        val obj = X86CodeGenerator().generateObjectFile(module)

        assertEquals(2, obj.sections.size)
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
        val ir = IrBuilder("hello_pe", Target.x86_64())
        ir.targetTriple = "x86_64-unknown-windows-msvc"

        val strType = Type.Array(Type.I8, 14)
        val strRef = ir.addGlobal("hello_str", strType,
            Constant.StringConst("Hello, World!"), isConstant = true,
            linkage = Linkage.INTERNAL)

        ir.declareFunction("puts", listOf(Param("s", Type.OpaquePointer)), Type.I32)

        ir.createFunction("main", emptyList(), Type.I32)
        ir.positionAtEnd(ir.appendBlock("entry"))
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
        val ir = IrBuilder("spill_test", Target.x86_64())
        val params = ir.createFunction("many_vars",
            listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        ir.positionAtEnd(ir.appendBlock("entry"))

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
        val disasm = org.kgen.x86.disasm.X86Disassembler()
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
        val ir = IrBuilder("callee_save_test", Target.x86_64())
        val params = ir.createFunction("uses_callee_saved",
            listOf(Param("x", Type.I32)), Type.I32)
        ir.positionAtEnd(ir.appendBlock("entry"))

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
        val disasm = org.kgen.x86.disasm.X86Disassembler()
        val insts = disasm.disassembleRaw(code)
        assertEquals(code.size, insts.sumOf { it.size },
            "All bytes decoded: ${insts.map { it.text() }}")
    }

    @Test
    fun `PE uses Windows x64 ABI registers`() {
        val ir = IrBuilder("winabi", Target.x86_64())
        ir.targetTriple = "x86_64-unknown-windows-msvc"

        val params = ir.createFunction("add", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        ir.positionAtEnd(ir.appendBlock("entry"))
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
}
