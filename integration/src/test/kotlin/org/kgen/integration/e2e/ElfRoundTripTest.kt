package org.kgen.integration.e2e

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.binary.*
import org.kgen.binary.elf.*
import org.kgen.codegen.CodeGenOptions
import org.kgen.codegen.OutputFormat
import org.kgen.ir.*
import org.kgen.ir.build.ModuleBuilder
import org.kgen.ir.target.Target
import org.kgen.target.arm64.codegen.Arm64CodeGenerator
import org.kgen.target.riscv.codegen.RiscVCodeGenerator
import org.kgen.target.x86.codegen.X86CodeGenerator
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * ELF round-trip: IR → codegen → ElfObjectWriter → ElfReader → verify structure.
 * Also tests: IR → codegen → ElfStaticLinker → ElfReader → verify executable.
 */
class ElfRoundTripTest {

    private fun le(bytes: ByteArray): ByteBuffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

    // -- Helpers --

    private fun buildAddModule(target: Target): Module {
        val ir = ModuleBuilder("elf_test", target)
        val params = ir.createFunction("add", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
        ir.appendBlock("entry")
        ir.ret(ir.add(params[0], params[1]))
        ir.finalizeFunction()
        return ir.build()
    }

    private fun buildMultiModule(target: Target): Module {
        val ir = ModuleBuilder("multi_test", target)

        val addParams = ir.createFunction("add", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
        ir.appendBlock("entry")
        ir.ret(ir.add(addParams[0], addParams[1]))
        ir.finalizeFunction()

        val mulParams = ir.createFunction("mul", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
        ir.appendBlock("entry")
        ir.ret(ir.mul(mulParams[0], mulParams[1]))
        ir.finalizeFunction()

        ir.createFunction("answer", emptyList(), Type.I64)
        ir.appendBlock("entry")
        ir.ret(Constant.I64(42))
        ir.finalizeFunction()

        return ir.build()
    }

    // -- x86-64 ELF round-trip --

    @Test
    fun x86ObjectFileRoundTrip() {
        val module = buildAddModule(Target.x86_64())
        val obj = X86CodeGenerator().generateObjectFile(module)

        // Write to ELF
        val elfBytes = ElfObjectWriter().write(obj)
        assertTrue(ElfReader.canRead(elfBytes))

        // Read back
        val elf = ElfReader.read(elfBytes)
        val roundTripped = ElfReader.toObjectFile(elf)

        // Verify structure
        assertEquals(ObjectFormat.ELF, roundTripped.format)
        assertTrue(roundTripped.sections.any { it.kind == SectionKind.TEXT })
        assertTrue(roundTripped.symbols.any { it.name == "add" })

        val addSym = roundTripped.symbols.first { it.name == "add" }
        assertEquals(SymbolBinding.GLOBAL, addSym.binding)
        assertEquals(SymbolKind.FUNCTION, addSym.kind)
    }

    @Test
    fun x86MultiSymbolRoundTrip() {
        val module = buildMultiModule(Target.x86_64())
        val obj = X86CodeGenerator().generateObjectFile(module)
        val elfBytes = ElfObjectWriter().write(obj)
        val roundTripped = ElfReader.toObjectFile(ElfReader.read(elfBytes))

        val funcNames = roundTripped.symbols.filter { it.kind == SymbolKind.FUNCTION }.map { it.name }
        assertTrue("add" in funcNames, "Missing add: $funcNames")
        assertTrue("mul" in funcNames, "Missing mul: $funcNames")
        assertTrue("answer" in funcNames, "Missing answer: $funcNames")
    }

    @Test
    fun x86ElfStaticLinkerProducesExecutable() {
        val module = buildAddModule(Target.x86_64())
        val obj = X86CodeGenerator().generateObjectFile(module)

        // Rename "add" to "_start" for static linker
        val startObj = obj.copy(
            symbols = obj.symbols.map { s ->
                if (s.name == "add") s.copy(name = "_start") else s
            }
        )

        val exeBytes = ElfStaticLinker().link(listOf(startObj))
        val buf = le(exeBytes)

        // Verify ELF executable header
        assertEquals(0x7F, exeBytes[0].toInt() and 0xFF)
        assertEquals('E'.code, exeBytes[1].toInt() and 0xFF)
        assertEquals('L'.code, exeBytes[2].toInt() and 0xFF)
        assertEquals('F'.code, exeBytes[3].toInt() and 0xFF)

        // e_type = ET_EXEC (2)
        assertEquals(2, buf.getShort(16).toInt() and 0xFFFF)

        // Entry point should be non-zero
        val entryPoint = buf.getLong(24)
        assertTrue(entryPoint > 0, "Entry point should be non-zero")
    }

    @Test
    fun x86ElfSharedLinkerProducesSharedObject() {
        val module = buildMultiModule(Target.x86_64())
        val obj = X86CodeGenerator().generateObjectFile(module)
        val soBytes = ElfSharedLinker(soname = "libtest.so").link(listOf(obj))
        val buf = le(soBytes)

        // ELF magic
        assertEquals(0x7F, soBytes[0].toInt() and 0xFF)

        // e_type = ET_DYN (3)
        assertEquals(3, buf.getShort(16).toInt() and 0xFFFF)
    }

    @Test
    fun x86ElfDynamicLinkerProducesExecutable() {
        val module = buildAddModule(Target.x86_64())
        val obj = X86CodeGenerator().generateObjectFile(module)
        val startObj = obj.copy(
            symbols = obj.symbols.map { s ->
                if (s.name == "add") s.copy(name = "_start") else s
            }
        )

        val exeBytes = ElfLinker().link(listOf(startObj))
        val buf = le(exeBytes)

        assertEquals(0x7F, exeBytes[0].toInt() and 0xFF)
        // Should be ET_EXEC (2) or ET_DYN (3) depending on PIE
        val eType = buf.getShort(16).toInt() and 0xFFFF
        assertTrue(eType == 2 || eType == 3)
    }

    // -- ARM64 ELF round-trip --

    @Test
    fun arm64ObjectFileRoundTrip() {
        val module = buildAddModule(Target.arm64())
        val obj = Arm64CodeGenerator().generateObjectFile(module)
        val elfBytes = ElfObjectWriter(ElfMachine.AARCH64.code).write(obj)

        assertTrue(ElfReader.canRead(elfBytes))
        val roundTripped = ElfReader.toObjectFile(ElfReader.read(elfBytes))

        assertTrue(roundTripped.sections.any { it.kind == SectionKind.TEXT })
        assertTrue(roundTripped.symbols.any { it.name == "add" })
    }

    // -- RISC-V ELF round-trip --

    @Test
    fun riscvObjectFileRoundTrip() {
        val module = buildAddModule(Target.riscv64())
        val obj = RiscVCodeGenerator().generateObjectFile(module)
        val elfBytes = ElfObjectWriter(ElfMachine.RISCV.code).write(obj)

        assertTrue(ElfReader.canRead(elfBytes))
        val roundTripped = ElfReader.toObjectFile(ElfReader.read(elfBytes))

        assertTrue(roundTripped.sections.any { it.kind == SectionKind.TEXT })
        assertTrue(roundTripped.symbols.any { it.name == "add" })
    }

    // -- Multi-object linking --

    @Test
    fun twoObjectFilesLinkedIntoStaticExecutable() {
        val ir1 = ModuleBuilder("mod1", Target.x86_64())
        val p1 = ir1.createFunction("compute", listOf(Param("x", Type.I64)), Type.I64)
        ir1.appendBlock("entry")
        ir1.ret(ir1.mul(p1[0], Constant.I64(2)))
        ir1.finalizeFunction()
        val obj1 = X86CodeGenerator().generateObjectFile(ir1.build())

        val ir2 = ModuleBuilder("mod2", Target.x86_64())
        ir2.createFunction("_start", emptyList(), Type.I64)
        ir2.appendBlock("entry")
        ir2.ret(Constant.I64(0))
        ir2.finalizeFunction()
        val obj2 = X86CodeGenerator().generateObjectFile(ir2.build())

        val exeBytes = ElfStaticLinker().link(listOf(obj1, obj2))
        assertTrue(exeBytes.size > 100)
        assertEquals(0x7F, exeBytes[0].toInt() and 0xFF)
    }

    // -- ELF content preservation --

    @Test
    fun codeContentPreservedThroughRoundTrip() {
        val module = buildAddModule(Target.x86_64())
        val obj = X86CodeGenerator().generateObjectFile(module)
        val originalCode = obj.sections.first { it.kind == SectionKind.TEXT }.data.clone()

        val elfBytes = ElfObjectWriter().write(obj)
        val roundTripped = ElfReader.toObjectFile(ElfReader.read(elfBytes))
        val roundTrippedCode = roundTripped.sections.first { it.kind == SectionKind.TEXT }.data

        assertArrayEquals(originalCode, roundTrippedCode,
            "Machine code should survive ELF round-trip unchanged")
    }
}
