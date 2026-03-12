package org.kgen.codegen

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*
import org.kgen.ir.build.*
import org.kgen.ir.target.Target
import org.kgen.target.x86.codegen.X86CodeGenerator
import org.kgen.target.arm64.codegen.Arm64CodeGenerator
import org.kgen.target.riscv.codegen.RiscVCodeGenerator

class EhFrameEmissionTest {

    private fun buildSimpleModule(target: Target): Module {
        val ir = IrBuilder("test", target)
        val params = ir.createFunction(
            "add",
            listOf(Param("a", Type.I64), Param("b", Type.I64)),
            Type.I64,
        )
        ir.positionAtEnd(ir.appendBlock("entry"))
        val sum = ir.add(params[0], params[1])
        ir.ret(sum)
        ir.finalizeFunction()
        return ir.build()
    }

    private fun buildMultiFunctionModule(target: Target): Module {
        val ir = IrBuilder("test", target)

        val p1 = ir.createFunction("func1", listOf(Param("x", Type.I64)), Type.I64)
        ir.positionAtEnd(ir.appendBlock("entry"))
        ir.ret(p1[0])
        ir.finalizeFunction()

        val p2 = ir.createFunction("func2", listOf(Param("y", Type.I64)), Type.I64)
        ir.positionAtEnd(ir.appendBlock("entry"))
        ir.ret(p2[0])
        ir.finalizeFunction()

        return ir.build()
    }

    @Test
    fun `x86 generates eh_frame for simple function`() {
        val module = buildSimpleModule(Target.x86_64())
        val code = X86CodeGenerator().generateCode(module)
        assertTrue(code.ehFrameBytes.isNotEmpty(), "Should produce eh_frame data")
    }

    @Test
    fun `x86 eh_frame starts with valid CIE`() {
        val module = buildSimpleModule(Target.x86_64())
        val code = X86CodeGenerator().generateCode(module)
        val frame = code.ehFrameBytes
        // First 4 bytes: CIE length (must be > 0)
        val cieLen = readInt32(frame, 0)
        assertTrue(cieLen > 0, "CIE length should be positive")
        // CIE ID at bytes 4-7 should be 0
        assertEquals(0, readInt32(frame, 4), "CIE ID should be 0 for .eh_frame")
    }

    @Test
    fun `x86 eh_frame ends with zero terminator`() {
        val module = buildSimpleModule(Target.x86_64())
        val code = X86CodeGenerator().generateCode(module)
        val frame = code.ehFrameBytes
        val len = frame.size
        assertEquals(0, readInt32(frame, len - 4), "Should end with zero-length terminator")
    }

    @Test
    fun `x86 multiple functions produce larger eh_frame`() {
        val single = X86CodeGenerator().generateCode(buildSimpleModule(Target.x86_64()))
        val multi = X86CodeGenerator().generateCode(buildMultiFunctionModule(Target.x86_64()))
        assertTrue(multi.ehFrameBytes.size > single.ehFrameBytes.size,
            "Multiple functions should produce more eh_frame data")
    }

    @Test
    fun `x86 toObjectFile includes eh_frame section`() {
        val module = buildSimpleModule(Target.x86_64())
        val code = X86CodeGenerator().generateCode(module)
        val obj = code.toObjectFile(
            org.kgen.binary.ObjectFormat.ELF,
            org.kgen.binary.Architecture(org.kgen.binary.ArchType.X86_64)
        )
        val ehSection = obj.sections.find { it.name == ".eh_frame" }
        assertNotNull(ehSection, "ObjectFile should contain .eh_frame section")
        assertTrue(ehSection!!.data.isNotEmpty())
    }

    @Test
    fun `arm64 generates eh_frame`() {
        val module = buildSimpleModule(Target.arm64())
        val code = Arm64CodeGenerator().generateCode(module)
        assertTrue(code.ehFrameBytes.isNotEmpty(), "Should produce eh_frame data")
    }

    @Test
    fun `arm64 eh_frame has valid structure`() {
        val module = buildSimpleModule(Target.arm64())
        val code = Arm64CodeGenerator().generateCode(module)
        val frame = code.ehFrameBytes
        assertTrue(readInt32(frame, 0) > 0, "CIE length should be positive")
        assertEquals(0, readInt32(frame, 4), "CIE ID should be 0")
    }

    @Test
    fun `riscv generates eh_frame`() {
        val module = buildSimpleModule(Target.riscv64())
        val code = RiscVCodeGenerator().generateCode(module)
        assertTrue(code.ehFrameBytes.isNotEmpty(), "Should produce eh_frame data")
    }

    @Test
    fun `riscv eh_frame has valid structure`() {
        val module = buildSimpleModule(Target.riscv64())
        val code = RiscVCodeGenerator().generateCode(module)
        val frame = code.ehFrameBytes
        assertTrue(readInt32(frame, 0) > 0, "CIE length should be positive")
        assertEquals(0, readInt32(frame, 4), "CIE ID should be 0")
    }

    @Test
    fun `all backends produce version 1 CIE`() {
        for ((target, gen) in listOf(
            Target.x86_64() to X86CodeGenerator(),
            Target.arm64() to Arm64CodeGenerator(),
            Target.riscv64() to RiscVCodeGenerator(),
        )) {
            val code = gen.generateCode(buildSimpleModule(target))
            val frame = code.ehFrameBytes
            // Version byte is at offset 8 (after length + CIE ID)
            assertEquals(1, frame[8].toInt(), "CIE version should be 1 for ${gen.targetName}")
        }
    }

    @Test
    fun `eh_frame absent when no text bytes`() {
        val code = CompiledCode(textBytes = ByteArray(0))
        assertTrue(code.ehFrameBytes.isEmpty())
    }

    private fun readInt32(data: ByteArray, offset: Int): Int {
        return (data[offset].toInt() and 0xFF) or
            ((data[offset + 1].toInt() and 0xFF) shl 8) or
            ((data[offset + 2].toInt() and 0xFF) shl 16) or
            ((data[offset + 3].toInt() and 0xFF) shl 24)
    }
}
