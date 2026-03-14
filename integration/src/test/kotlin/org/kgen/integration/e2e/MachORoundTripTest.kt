package org.kgen.integration.e2e

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.binary.*
import org.kgen.binary.macho.MachO
import org.kgen.binary.macho.MachOLinker
import org.kgen.binary.macho.MachOObjectWriter
import org.kgen.binary.macho.MachOReader
import org.kgen.ir.*
import org.kgen.ir.build.IrBuilder
import org.kgen.ir.target.Target
import org.kgen.target.arm64.codegen.Arm64CodeGenerator
import org.kgen.target.x86.codegen.X86CodeGenerator
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Mach-O round-trip: IR → codegen → MachOObjectWriter → MachOLinker → MachOReader → verify.
 */
class MachORoundTripTest {

    private fun le(bytes: ByteArray): ByteBuffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

    private fun buildModule(target: Target): Module {
        val ir = IrBuilder("macho_test", target)
        val params = ir.createFunction("add", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
        ir.appendBlock("entry")
        ir.ret(ir.add(params[0], params[1]))
        ir.finalizeFunction()

        ir.createFunction("_main", emptyList(), Type.I64)
        ir.appendBlock("entry")
        ir.ret(Constant.I64(0))
        ir.finalizeFunction()

        return ir.build()
    }

    @Test
    fun x86MachOObjectRoundTrip() {
        val module = buildModule(Target.x86_64())
        val obj = X86CodeGenerator().generateObjectFile(module)
        val machoBytes = MachOObjectWriter().write(obj)

        val macho = MachOReader.read(machoBytes)
        assertTrue(macho.allSections.isNotEmpty())

        val textSection = macho.allSections.firstOrNull { it.sectionName == "__text" }
        assertNotNull(textSection, "Should have __text section")

        val symbols = macho.symbols
        assertTrue(symbols.any { it.name == "_add" || it.name == "add" },
            "Should have add symbol: ${symbols.map { it.name }}")
    }

    @Test
    fun arm64MachOObjectRoundTrip() {
        val module = buildModule(Target.arm64())
        val obj = Arm64CodeGenerator().generateObjectFile(module)
        val machoBytes = MachOObjectWriter(
            cpuType = MachO.CPU_TYPE_ARM64,
            cpuSubtype = MachO.CPU_SUBTYPE_ARM64_ALL,
        ).write(obj)

        val macho = MachOReader.read(machoBytes)
        assertTrue(macho.allSections.isNotEmpty())
    }

    @Test
    fun machOLinkerProducesExecutable() {
        val module = buildModule(Target.x86_64())
        val obj = X86CodeGenerator().generateObjectFile(module)
        val exeBytes = MachOLinker().link(listOf(obj))

        val buf = le(exeBytes)
        // Mach-O magic: 0xFEEDFACF (64-bit) or 0xFEEDFACE (32-bit)
        val magic = buf.getInt(0)
        assertTrue(magic == 0xFEEDFACF.toInt() || magic == 0xFEEDFACE.toInt(),
            "Expected Mach-O magic, got 0x${magic.toString(16)}")
    }

    @Test
    fun machOLinkerThenReaderRoundTrip() {
        val module = buildModule(Target.x86_64())
        val obj = X86CodeGenerator().generateObjectFile(module)
        val exeBytes = MachOLinker().link(listOf(obj))

        val macho = MachOReader.read(exeBytes)
        assertTrue(macho.allSections.isNotEmpty())

        val objProjection = MachOReader.toObjectFile(macho)
        assertEquals(ObjectFormat.MACH_O, objProjection.format)
        assertTrue(objProjection.sections.isNotEmpty())
    }

    @Test
    fun multiObjectMachOLink() {
        val ir1 = IrBuilder("lib", Target.x86_64())
        val p = ir1.createFunction("compute", listOf(Param("x", Type.I64)), Type.I64)
        ir1.appendBlock("entry")
        ir1.ret(ir1.mul(p[0], Constant.I64(3)))
        ir1.finalizeFunction()
        val obj1 = X86CodeGenerator().generateObjectFile(ir1.build())

        val ir2 = IrBuilder("main", Target.x86_64())
        ir2.createFunction("_main", emptyList(), Type.I64)
        ir2.appendBlock("entry")
        ir2.ret(Constant.I64(0))
        ir2.finalizeFunction()
        val obj2 = X86CodeGenerator().generateObjectFile(ir2.build())

        val exeBytes = MachOLinker().link(listOf(obj1, obj2))
        assertTrue(exeBytes.size > 100)
    }
}
