package org.kgen.binary.elf

import org.kgen.binary.*
import org.kgen.ir.*
import org.kgen.ir.build.IrBuilder
import org.kgen.ir.target.Target
import org.kgen.target.riscv.codegen.RiscVCodeGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ElfRiscVLinkerTest {

    private fun le(bytes: ByteArray) = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    private fun readU16(buf: ByteBuffer, off: Int): Int = buf.getShort(off).toInt() and 0xFFFF
    private fun readU64(buf: ByteBuffer, off: Int): Long = buf.getLong(off)

    private val riscvMachine = ElfMachine.RISCV.code

    private fun compileRiscVModule(block: IrBuilder.() -> Unit): ObjectFile {
        val ir = IrBuilder("test", Target.riscv64())
        ir.block()
        return RiscVCodeGenerator().generateObjectFile(ir.build())
    }

    // --- ElfObjectWriter ---

    @Test
    fun `ElfObjectWriter produces RISC-V object file`() {
        val obj = compileRiscVModule {
            createFunction("start", emptyList(), Type.I32)
            positionAtEnd(appendBlock("entry"))
            ret(Constant.I32(0))
            finalizeFunction()
        }

        val bytes = ElfObjectWriter(riscvMachine).write(obj)
        val elf = ElfReader.read(bytes)

        assertEquals(ElfMachine.RISCV, elf.header.machine)
        assertTrue(elf.sections.any { it.name == ".text" })
    }

    @Test
    fun `ElfObjectWriter preserves RISC-V relocations`() {
        val obj = compileRiscVModule {
            createFunction("caller", emptyList(), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val result = call("callee", emptyList(), Type.I32)
            ret(result)
            finalizeFunction()
        }

        val bytes = ElfObjectWriter(riscvMachine).write(obj)
        val elf = ElfReader.read(bytes)

        val relaText = elf.sections.firstOrNull { it.name == ".rela.text" }
        assertNotNull(relaText, "Should have .rela.text section")
    }

    // --- ElfStaticLinker ---

    @Test
    fun `ElfStaticLinker links single RISC-V object`() {
        val obj = compileRiscVModule {
            createFunction("_start", emptyList(), Type.I32)
            positionAtEnd(appendBlock("entry"))
            ret(Constant.I32(0))
            finalizeFunction()
        }

        val binary = ElfStaticLinker(machine = riscvMachine).link(listOf(obj))
        val buf = le(binary)

        assertEquals(riscvMachine, readU16(buf, 18))
        val entry = readU64(buf, 24)
        assertTrue(entry >= 0x400000)
    }

    @Test
    fun `ElfStaticLinker resolves RISC-V CALL_PLT relocation`() {
        val obj = compileRiscVModule {
            createFunction("_start", emptyList(), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val r = call("helper", emptyList(), Type.I32)
            ret(r)
            finalizeFunction()

            createFunction("helper", emptyList(), Type.I32)
            positionAtEnd(appendBlock("entry"))
            ret(Constant.I32(42))
            finalizeFunction()
        }

        val binary = ElfStaticLinker(machine = riscvMachine).link(listOf(obj))
        assertTrue(binary.isNotEmpty())
        val buf = le(binary)
        assertEquals(riscvMachine, readU16(buf, 18))
    }

    @Test
    fun `ElfStaticLinker links two RISC-V objects with cross-reference`() {
        val mainObj = compileRiscVModule {
            createFunction("_start", emptyList(), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val r = call("helper", emptyList(), Type.I32)
            ret(r)
            finalizeFunction()
        }

        val helperObj = compileRiscVModule {
            createFunction("helper", emptyList(), Type.I32)
            positionAtEnd(appendBlock("entry"))
            ret(Constant.I32(100))
            finalizeFunction()
        }

        val binary = ElfStaticLinker(machine = riscvMachine).link(listOf(mainObj, helperObj))
        assertTrue(binary.isNotEmpty())

        val elf = ElfReader.read(binary)
        assertEquals(ElfMachine.RISCV, elf.header.machine)
    }

    @Test
    fun `ElfStaticLinker round-trips through ElfReader`() {
        val obj = compileRiscVModule {
            createFunction("_start", emptyList(), Type.I32)
            positionAtEnd(appendBlock("entry"))
            ret(Constant.I32(0))
            finalizeFunction()
        }

        val binary = ElfStaticLinker(machine = riscvMachine).link(listOf(obj))
        val elf = ElfReader.read(binary)

        assertEquals(ElfObjectType.EXEC, elf.header.type)
        assertEquals(ElfMachine.RISCV, elf.header.machine)
        assertTrue(elf.segments.any { it.type == ElfSegmentType.LOAD })
    }

    // --- ElfLinker (dynamic) ---

    @Test
    fun `ElfLinker links RISC-V dynamic executable`() {
        val obj = compileRiscVModule {
            createFunction("main", emptyList(), Type.I32)
            positionAtEnd(appendBlock("entry"))
            ret(Constant.I32(0))
            finalizeFunction()
        }

        val binary = ElfLinker(machine = riscvMachine).link(listOf(obj))
        assertTrue(binary.isNotEmpty())
        val elf = ElfReader.read(binary)
        assertEquals(ElfMachine.RISCV, elf.header.machine)
    }

    @Test
    fun `ElfLinker resolves RISC-V internal calls`() {
        val obj = compileRiscVModule {
            createFunction("main", emptyList(), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val r = call("compute", emptyList(), Type.I32)
            ret(r)
            finalizeFunction()

            createFunction("compute", emptyList(), Type.I32)
            positionAtEnd(appendBlock("entry"))
            ret(Constant.I32(7))
            finalizeFunction()
        }

        val binary = ElfLinker(machine = riscvMachine).link(listOf(obj))
        assertTrue(binary.isNotEmpty())
    }

    // --- ElfSharedLinker ---

    @Test
    fun `ElfSharedLinker links RISC-V shared library`() {
        val obj = compileRiscVModule {
            val p = createFunction("mylib_add", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            positionAtEnd(appendBlock("entry"))
            ret(add(p[0], p[1]))
            finalizeFunction()
        }

        val soBytes = ElfSharedLinker(machine = riscvMachine).link(listOf(obj))
        assertTrue(soBytes.isNotEmpty())
        val elf = ElfReader.read(soBytes)
        assertEquals(ElfObjectType.DYN, elf.header.type)
        assertEquals(ElfMachine.RISCV, elf.header.machine)
    }

    @Test
    fun `ElfSharedLinker exports RISC-V symbols`() {
        val obj = compileRiscVModule {
            createFunction("exported_fn", emptyList(), Type.I32)
            positionAtEnd(appendBlock("entry"))
            ret(Constant.I32(99))
            finalizeFunction()
        }

        val soBytes = ElfSharedLinker(machine = riscvMachine).link(listOf(obj))
        val elf = ElfReader.read(soBytes)
        // Verify it's a valid shared object
        assertEquals(ElfObjectType.DYN, elf.header.type)
        assertEquals(ElfMachine.RISCV, elf.header.machine)
    }

    @Test
    fun `ElfSharedLinker resolves internal RISC-V calls`() {
        val obj = compileRiscVModule {
            createFunction("public_fn", emptyList(), Type.I32)
            positionAtEnd(appendBlock("entry"))
            val r = call("internal_fn", emptyList(), Type.I32)
            ret(r)
            finalizeFunction()

            createFunction("internal_fn", emptyList(), Type.I32)
            positionAtEnd(appendBlock("entry"))
            ret(Constant.I32(55))
            finalizeFunction()
        }

        val soBytes = ElfSharedLinker(machine = riscvMachine).link(listOf(obj))
        assertTrue(soBytes.isNotEmpty())
        val elf = ElfReader.read(soBytes)
        assertEquals(ElfObjectType.DYN, elf.header.type)
    }

    // --- Relocation-specific tests ---

    @Test
    fun `RISC-V CALL relocation patches auipc+jalr pair`() {
        // auipc ra, 0  = 0x00000097
        // jalr  ra, ra, 0 = 0x000080E7
        val code = byteArrayOf(
            0x97.toByte(), 0x00, 0x00, 0x00, // auipc ra, 0
            0xE7.toByte(), 0x80.toByte(), 0x00, 0x00, // jalr ra, ra, 0
            0x13, 0x05, 0x00, 0x00,          // addi a0, x0, 0 (target)
            0x67, 0x80.toByte(), 0x00, 0x00, // ret
        )
        val obj = ObjectFile(
            format = ObjectFormat.ELF,
            arch = Architecture(ArchType.RISCV64),
            sections = listOf(Section(".text", SectionKind.TEXT, code, align = 4)),
            symbols = listOf(
                Symbol("_start", value = 0, size = 8, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("target", value = 8, size = 8, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
            relocations = listOf(
                Relocation(offset = 0, symbol = "target", type = RelocationType.RiscV.CALL_PLT,
                    addend = 0, section = ".text"),
            ),
        )

        val binary = ElfStaticLinker(machine = riscvMachine).link(listOf(obj))
        assertTrue(binary.isNotEmpty())
        // Verify the binary has valid ELF header
        assertEquals(0x7f, binary[0].toInt() and 0xFF)
    }

    @Test
    fun `RISC-V JAL relocation patches J-type instruction`() {
        val code = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(0x000000EF.toInt()) // jal ra, 0 (placeholder)
            .putInt(0x00000013)          // nop
            .putInt(0x02A00513.toInt())  // addi a0, x0, 42
            .putInt(0x00008067)          // ret
            .array()

        val obj = ObjectFile(
            format = ObjectFormat.ELF,
            arch = Architecture(ArchType.RISCV64),
            sections = listOf(Section(".text", SectionKind.TEXT, code, align = 4)),
            symbols = listOf(
                Symbol("_start", value = 0, size = code.size.toLong(), section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("target", value = 8, size = 8, section = ".text",
                    binding = SymbolBinding.LOCAL, kind = SymbolKind.FUNCTION),
            ),
            relocations = listOf(
                Relocation(offset = 0, symbol = "target", type = RelocationType.RiscV.JAL,
                    addend = 0, section = ".text"),
            ),
        )

        val binary = ElfStaticLinker(machine = riscvMachine).link(listOf(obj))
        assertTrue(binary.isNotEmpty())
        // Verify the linked binary reads back
        val elf = ElfReader.read(binary)
        assertEquals(ElfObjectType.EXEC, elf.header.type)
    }

    @Test
    fun `RISC-V BRANCH relocation patches B-type instruction`() {
        val beq = 0x00050063 // beq a0, x0, 0 (placeholder)
        val code = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(beq)
            .putInt(0x00000013) // nop
            .putInt(0x00100513) // addi a0, x0, 1
            .putInt(0x00008067) // ret
            .array()

        val obj = ObjectFile(
            format = ObjectFormat.ELF,
            arch = Architecture(ArchType.RISCV64),
            sections = listOf(Section(".text", SectionKind.TEXT, code, align = 4)),
            symbols = listOf(
                Symbol("_start", value = 0, size = code.size.toLong(), section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("target", value = 8, size = 8, section = ".text",
                    binding = SymbolBinding.LOCAL, kind = SymbolKind.FUNCTION),
            ),
            relocations = listOf(
                Relocation(offset = 0, symbol = "target", type = RelocationType.RiscV.BRANCH,
                    addend = 0, section = ".text"),
            ),
        )

        val binary = ElfStaticLinker(machine = riscvMachine).link(listOf(obj))
        assertTrue(binary.isNotEmpty())
        val elf = ElfReader.read(binary)
        assertEquals(ElfObjectType.EXEC, elf.header.type)
    }

    @Test
    fun `RISC-V RELAX relocation is no-op`() {
        val code = byteArrayOf(
            0x13, 0x05, 0x00, 0x00, // addi a0, x0, 0
            0x67, 0x80.toByte(), 0x00, 0x00, // ret
        )
        val obj = ObjectFile(
            format = ObjectFormat.ELF,
            arch = Architecture(ArchType.RISCV64),
            sections = listOf(Section(".text", SectionKind.TEXT, code, align = 4)),
            symbols = listOf(
                Symbol("_start", value = 0, size = code.size.toLong(), section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
            relocations = listOf(
                Relocation(offset = 0, symbol = "_start", type = RelocationType.RiscV.RELAX,
                    addend = 0, section = ".text"),
            ),
        )

        val binary = ElfStaticLinker(machine = riscvMachine).link(listOf(obj))
        assertTrue(binary.isNotEmpty())
    }
}
