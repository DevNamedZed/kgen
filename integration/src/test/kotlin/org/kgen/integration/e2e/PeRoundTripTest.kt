package org.kgen.integration.e2e

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.binary.*
import org.kgen.binary.pe.PeReader
import org.kgen.binary.pe.PeWriter
import org.kgen.codegen.CodeGenOptions
import org.kgen.codegen.OutputFormat
import org.kgen.ir.*
import org.kgen.ir.build.IrBuilder
import org.kgen.ir.target.Target
import org.kgen.target.x86.codegen.X86CodeGenerator
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * PE round-trip: IR → codegen → PeWriter → PeReader → verify structure.
 */
class PeRoundTripTest {

    private fun le(bytes: ByteArray): ByteBuffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

    private fun buildWindowsModule(): Module {
        val ir = IrBuilder("pe_test", Target.x86_64())
        ir.targetTriple = "x86_64-unknown-windows-msvc"

        val params = ir.createFunction("add", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
        ir.positionAtEnd(ir.appendBlock("entry"))
        ir.ret(ir.add(params[0], params[1]))
        ir.finalizeFunction()

        ir.createFunction("square", listOf(Param("x", Type.I64)), Type.I64)
        ir.positionAtEnd(ir.appendBlock("entry"))
        val x = Parameter("x", Type.I64, 0)
        ir.ret(ir.mul(x, x))
        ir.finalizeFunction()

        return ir.build()
    }

    private fun buildWindowsHelloModule(): Module {
        val ir = IrBuilder("hello_pe", Target.x86_64())
        ir.targetTriple = "x86_64-unknown-windows-msvc"

        val strType = Type.Array(Type.I8, 14)
        ir.addGlobal("hello_str", strType, Constant.StringConst("Hello, World!"),
            isConstant = true, linkage = Linkage.INTERNAL)

        ir.declareFunction("puts", listOf(Param("s", Type.OpaquePointer)), Type.I32)

        ir.createFunction("main", emptyList(), Type.I32)
        ir.positionAtEnd(ir.appendBlock("entry"))
        ir.ret(Constant.I32(0))
        ir.finalizeFunction()

        return ir.build()
    }

    @Test
    fun peExecutableHasCorrectMagic() {
        val module = buildWindowsHelloModule()
        val binary = X86CodeGenerator().generate(module, CodeGenOptions(outputFormat = OutputFormat.BINARY))

        assertEquals('M'.code, binary[0].toInt() and 0xFF)
        assertEquals('Z'.code, binary[1].toInt() and 0xFF)

        val peOff = le(binary).getInt(0x3C)
        assertEquals('P'.code, binary[peOff].toInt() and 0xFF)
        assertEquals('E'.code, binary[peOff + 1].toInt() and 0xFF)
    }

    @Test
    fun peRoundTripPreservesStructure() {
        val module = buildWindowsHelloModule()
        val binary = X86CodeGenerator().generate(module, CodeGenOptions(outputFormat = OutputFormat.BINARY))

        assertTrue(PeReader.canRead(binary))
        val pe = PeReader.read(binary)

        assertTrue(pe.isPe)
        assertTrue(pe.isPe32Plus, "x86-64 should be PE32+")

        val sections = pe.sections
        assertTrue(sections.isNotEmpty(), "PE should have sections")
        assertTrue(sections.any { it.name.startsWith(".text") }, "Should have .text section")
    }

    @Test
    fun peFlatWriteRoundTrip() {
        val module = buildWindowsModule()
        val obj = X86CodeGenerator().generateObjectFile(module)
        val code = obj.sections.first { it.kind == SectionKind.TEXT }.data

        val peBytes = PeWriter.writeFlat(code)
        assertTrue(PeReader.canRead(peBytes))

        val pe = PeReader.read(peBytes)
        assertTrue(pe.isPe)
        assertTrue(pe.sections.isNotEmpty())
    }

    @Test
    fun peToObjectFileProjection() {
        val module = buildWindowsHelloModule()
        val binary = X86CodeGenerator().generate(module, CodeGenOptions(outputFormat = OutputFormat.BINARY))

        val pe = PeReader.read(binary)
        val obj = PeReader.toObjectFile(pe)

        assertEquals(ObjectFormat.PE_COFF, obj.format)
        assertTrue(obj.sections.isNotEmpty())
    }

    @Test
    fun peContainsStringData() {
        val module = buildWindowsHelloModule()
        val binary = X86CodeGenerator().generate(module, CodeGenOptions(outputFormat = OutputFormat.BINARY))

        assertTrue(String(binary, Charsets.US_ASCII).contains("Hello, World!"),
            "PE binary should contain string constant")
    }
}
