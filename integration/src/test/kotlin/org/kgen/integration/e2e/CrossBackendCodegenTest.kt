package org.kgen.integration.e2e

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.binary.SectionKind
import org.kgen.codegen.CodeGenOptions
import org.kgen.codegen.OutputFormat
import org.kgen.ir.*
import org.kgen.ir.build.IrBuilder
import org.kgen.ir.target.Target
import org.kgen.target.arm64.codegen.Arm64CodeGenerator
import org.kgen.target.arm64.disasm.Arm64Disassembler
import org.kgen.target.clr.codegen.CilCodeGenerator
import org.kgen.target.jvm.codegen.JvmCodeGenerator
import org.kgen.target.riscv.codegen.RiscVCodeGenerator
import org.kgen.target.riscv.disasm.RiscVDisassembler
import org.kgen.target.wasm.codegen.WasmCodeGenerator
import org.kgen.target.x86.codegen.X86CodeGenerator
import org.kgen.target.x86.disasm.X86Disassembler

/**
 * Cross-backend codegen: same IR compiled to all 6 backends.
 * Verifies that each backend produces valid output for the same input.
 */
class CrossBackendCodegenTest {

    // -- Helpers --

    private fun buildModule(target: Target, block: IrBuilder.() -> Unit): Module {
        val ir = IrBuilder("cross_test", target)
        ir.block()
        return ir.build()
    }

    private fun buildArithmeticModule(target: Target): Module = buildModule(target) {
        val params = createFunction("add", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        positionAtEnd(appendBlock("entry"))
        ret(add(params[0], params[1]))
        finalizeFunction()
    }

    private fun buildBranchModule(target: Target): Module = buildModule(target) {
        val params = createFunction("abs", listOf(Param("x", Type.I32)), Type.I32)
        positionAtEnd(appendBlock("entry"))
        val cond = icmp(ICmpPredicate.SGE, params[0], Constant.I32(0))
        condBr(cond, "positive", "negative")

        positionAtEnd(appendBlock("positive"))
        ret(params[0])

        positionAtEnd(appendBlock("negative"))
        val neg = sub(Constant.I32(0), params[0])
        ret(neg)
        finalizeFunction()
    }

    private fun buildMultiFunctionModule(target: Target): Module = buildModule(target) {
        val addParams = createFunction("add", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
        positionAtEnd(appendBlock("entry"))
        ret(add(addParams[0], addParams[1]))
        finalizeFunction()

        val mulParams = createFunction("mul", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
        positionAtEnd(appendBlock("entry"))
        ret(mul(mulParams[0], mulParams[1]))
        finalizeFunction()

        createFunction("constant", emptyList(), Type.I64)
        positionAtEnd(appendBlock("entry"))
        ret(Constant.I64(42))
        finalizeFunction()
    }

    // -- x86-64 --

    @Test
    fun x86ArithmeticProducesValidCode() {
        val module = buildArithmeticModule(Target.x86_64())
        val obj = X86CodeGenerator().generateObjectFile(module)
        val text = obj.sections.first { it.kind == SectionKind.TEXT }
        assertTrue(text.data.isNotEmpty())

        val insts = X86Disassembler().disassembleRaw(text.data)
        assertTrue(insts.isNotEmpty())
        assertEquals(text.data.size, insts.sumOf { it.size }, "All bytes decode cleanly")

        assertTrue(obj.symbols.any { it.name == "add" })
    }

    @Test
    fun x86BranchProducesValidCode() {
        val module = buildBranchModule(Target.x86_64())
        val obj = X86CodeGenerator().generateObjectFile(module)
        val text = obj.sections.first { it.kind == SectionKind.TEXT }
        val insts = X86Disassembler().disassembleRaw(text.data)
        assertEquals(text.data.size, insts.sumOf { it.size })
    }

    @Test
    fun x86MultiFunctionProducesValidCode() {
        val module = buildMultiFunctionModule(Target.x86_64())
        val obj = X86CodeGenerator().generateObjectFile(module)
        assertTrue(obj.symbols.any { it.name == "add" })
        assertTrue(obj.symbols.any { it.name == "mul" })
        assertTrue(obj.symbols.any { it.name == "constant" })
    }

    // -- ARM64 --

    @Test
    fun arm64ArithmeticProducesValidCode() {
        val module = buildArithmeticModule(Target.arm64())
        val obj = Arm64CodeGenerator().generateObjectFile(module)
        val text = obj.sections.first { it.kind == SectionKind.TEXT }
        assertTrue(text.data.isNotEmpty())
        assertEquals(0, text.data.size % 4, "ARM64 instructions are 4-byte aligned")

        val insts = Arm64Disassembler().disassemble(text.data)
        assertTrue(insts.isNotEmpty())
        assertTrue(obj.symbols.any { it.name == "add" })
    }

    @Test
    fun arm64BranchProducesValidCode() {
        val module = buildBranchModule(Target.arm64())
        val obj = Arm64CodeGenerator().generateObjectFile(module)
        val text = obj.sections.first { it.kind == SectionKind.TEXT }
        assertTrue(text.data.isNotEmpty())
        assertEquals(0, text.data.size % 4)
    }

    @Test
    fun arm64MultiFunctionProducesValidCode() {
        val module = buildMultiFunctionModule(Target.arm64())
        val obj = Arm64CodeGenerator().generateObjectFile(module)
        assertTrue(obj.symbols.any { it.name == "add" })
        assertTrue(obj.symbols.any { it.name == "mul" })
        assertTrue(obj.symbols.any { it.name == "constant" })
    }

    // -- RISC-V --

    @Test
    fun riscvArithmeticProducesValidCode() {
        val module = buildArithmeticModule(Target.riscv64())
        val obj = RiscVCodeGenerator().generateObjectFile(module)
        val text = obj.sections.first { it.kind == SectionKind.TEXT }
        assertTrue(text.data.isNotEmpty())
        assertEquals(0, text.data.size % 4, "RISC-V instructions are 4-byte aligned")

        val insts = RiscVDisassembler().disassemble(text.data)
        assertTrue(insts.isNotEmpty())
        assertTrue(obj.symbols.any { it.name == "add" })
    }

    @Test
    fun riscvBranchProducesValidCode() {
        val module = buildBranchModule(Target.riscv64())
        val obj = RiscVCodeGenerator().generateObjectFile(module)
        val text = obj.sections.first { it.kind == SectionKind.TEXT }
        assertTrue(text.data.isNotEmpty())
        assertEquals(0, text.data.size % 4)
    }

    @Test
    fun riscvMultiFunctionProducesValidCode() {
        val module = buildMultiFunctionModule(Target.riscv64())
        val obj = RiscVCodeGenerator().generateObjectFile(module)
        assertTrue(obj.symbols.any { it.name == "add" })
        assertTrue(obj.symbols.any { it.name == "mul" })
        assertTrue(obj.symbols.any { it.name == "constant" })
    }

    // -- JVM --

    @Test
    fun jvmArithmeticProducesValidClassfile() {
        val module = buildArithmeticModule(Target.jvm())
        val bytes = JvmCodeGenerator().generate(module, CodeGenOptions())
        assertTrue(bytes.isNotEmpty())
        // Class file magic: 0xCAFEBABE
        assertEquals(0xCA, bytes[0].toInt() and 0xFF)
        assertEquals(0xFE, bytes[1].toInt() and 0xFF)
        assertEquals(0xBA, bytes[2].toInt() and 0xFF)
        assertEquals(0xBE, bytes[3].toInt() and 0xFF)
    }

    @Test
    fun jvmMultiFunctionProducesValidClassfile() {
        val module = buildMultiFunctionModule(Target.jvm())
        val bytes = JvmCodeGenerator().generate(module, CodeGenOptions())
        assertEquals(0xCA, bytes[0].toInt() and 0xFF)
        assertTrue(bytes.size > 100, "Multi-function classfile should be non-trivial")
    }

    // -- CIL --

    @Test
    fun cilArithmeticProducesValidOutput() {
        val module = buildArithmeticModule(Target.msil())
        val bytes = CilCodeGenerator().generate(module, CodeGenOptions())
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun cilMultiFunctionProducesValidOutput() {
        val module = buildMultiFunctionModule(Target.msil())
        val bytes = CilCodeGenerator().generate(module, CodeGenOptions())
        assertTrue(bytes.isNotEmpty())
    }

    // -- WASM --

    @Test
    fun wasmArithmeticProducesValidModule() {
        val module = buildArithmeticModule(Target.wasm())
        val bytes = WasmCodeGenerator().generate(module, CodeGenOptions())
        assertTrue(bytes.size > 8)
        // WASM magic: 0x00 0x61 0x73 0x6D
        assertEquals(0x00, bytes[0].toInt() and 0xFF)
        assertEquals(0x61, bytes[1].toInt() and 0xFF)
        assertEquals(0x73, bytes[2].toInt() and 0xFF)
        assertEquals(0x6D, bytes[3].toInt() and 0xFF)
    }

    @Test
    fun wasmMultiFunctionProducesValidModule() {
        val module = buildMultiFunctionModule(Target.wasm())
        val bytes = WasmCodeGenerator().generate(module, CodeGenOptions())
        assertEquals(0x00, bytes[0].toInt() and 0xFF)
        assertEquals(0x61, bytes[1].toInt() and 0xFF)
        assertTrue(bytes.size > 20, "Multi-function WASM should be non-trivial")
    }

    // -- Cross-backend consistency --

    @Test
    fun allNativeBackendsProduceSymbolsForSameIR() {
        data class BackendResult(val name: String, val symbols: List<String>, val codeSize: Int)

        val results = mutableListOf<BackendResult>()

        val x86Module = buildMultiFunctionModule(Target.x86_64())
        val x86Obj = X86CodeGenerator().generateObjectFile(x86Module)
        results += BackendResult("x86", x86Obj.symbols.map { it.name }, x86Obj.sections.first { it.kind == SectionKind.TEXT }.data.size)

        val arm64Module = buildMultiFunctionModule(Target.arm64())
        val arm64Obj = Arm64CodeGenerator().generateObjectFile(arm64Module)
        results += BackendResult("arm64", arm64Obj.symbols.map { it.name }, arm64Obj.sections.first { it.kind == SectionKind.TEXT }.data.size)

        val riscvModule = buildMultiFunctionModule(Target.riscv64())
        val riscvObj = RiscVCodeGenerator().generateObjectFile(riscvModule)
        results += BackendResult("riscv", riscvObj.symbols.map { it.name }, riscvObj.sections.first { it.kind == SectionKind.TEXT }.data.size)

        // All backends should emit the same set of function symbols
        for (r in results) {
            assertTrue("add" in r.symbols, "${r.name} missing 'add' symbol")
            assertTrue("mul" in r.symbols, "${r.name} missing 'mul' symbol")
            assertTrue("constant" in r.symbols, "${r.name} missing 'constant' symbol")
            assertTrue(r.codeSize > 0, "${r.name} produced empty code")
        }
    }

    @Test
    fun allSixBackendsCompileWithoutError() {
        val targets = listOf(
            "x86" to Target.x86_64(),
            "arm64" to Target.arm64(),
            "riscv" to Target.riscv64(),
            "jvm" to Target.jvm(),
            "cil" to Target.msil(),
            "wasm" to Target.wasm(),
        )

        val objectFormat = CodeGenOptions(outputFormat = OutputFormat.OBJECT)
        val generators = mapOf(
            "x86" to { m: Module -> X86CodeGenerator().generate(m, objectFormat) },
            "arm64" to { m: Module -> Arm64CodeGenerator().generate(m, objectFormat) },
            "riscv" to { m: Module -> RiscVCodeGenerator().generate(m, objectFormat) },
            "jvm" to { m: Module -> JvmCodeGenerator().generate(m, CodeGenOptions()) },
            "cil" to { m: Module -> CilCodeGenerator().generate(m, CodeGenOptions()) },
            "wasm" to { m: Module -> WasmCodeGenerator().generate(m, CodeGenOptions()) },
        )

        for ((name, target) in targets) {
            val module = buildArithmeticModule(target)
            val bytes = generators[name]!!(module)
            assertTrue(bytes.isNotEmpty(), "$name backend produced empty output")
        }
    }
}
