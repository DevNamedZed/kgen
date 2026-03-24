package org.kgen.codegen

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*
import org.kgen.ir.build.ModuleBuilder
import org.kgen.ir.target.Target
import org.kgen.ir.verify.IrVerifier
import org.kgen.target.x86.codegen.X86CodeGenerator
import org.kgen.target.arm64.codegen.Arm64CodeGenerator
import org.kgen.target.riscv.codegen.RiscVCodeGenerator
import org.kgen.target.wasm.codegen.WasmCodeGenerator
import org.kgen.binary.SectionKind

class CrossTargetCodegenTest {

    private fun buildModule(target: Target, block: ModuleBuilder.() -> Unit): Module {
        val ir = ModuleBuilder("cross_target_test", target)
        ir.block()
        val mod = ir.build()
        assertTrue(IrVerifier.verify(mod).isValid, "IR should be valid before codegen")
        return mod
    }

    // --- Same IR compiles to all native targets ---

    @Test
    fun addFunctionCompilesOnX86() {
        val mod = buildModule(Target.x86_64()) {
            val params = createFunction("add", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            val sum = add(params[0], params[1])
            ret(sum)
            finalizeFunction()
        }
        val obj = X86CodeGenerator().generateObjectFile(mod)
        val text = obj.sections.first { it.kind == SectionKind.TEXT }
        assertTrue(text.data.isNotEmpty())
        assertTrue(obj.symbols.any { it.name == "add" })
    }

    @Test
    fun addFunctionCompilesOnArm64() {
        val mod = buildModule(Target.arm64()) {
            val params = createFunction("add", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            val sum = add(params[0], params[1])
            ret(sum)
            finalizeFunction()
        }
        val obj = Arm64CodeGenerator().generateObjectFile(mod)
        val text = obj.sections.first { it.kind == SectionKind.TEXT }
        assertTrue(text.data.isNotEmpty())
        assertTrue(text.data.size % 4 == 0, "ARM64 instructions are 4 bytes")
    }

    @Test
    fun addFunctionCompilesOnRiscV() {
        val mod = buildModule(Target.riscv64()) {
            val params = createFunction("add", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            val sum = add(params[0], params[1])
            ret(sum)
            finalizeFunction()
        }
        val obj = RiscVCodeGenerator().generateObjectFile(mod)
        val text = obj.sections.first { it.kind == SectionKind.TEXT }
        assertTrue(text.data.isNotEmpty())
        assertTrue(text.data.size % 4 == 0, "RISC-V instructions are 4 bytes")
    }

    @Test
    fun addFunctionCompilesOnWasm() {
        val mod = buildModule(Target.wasm()) {
            val params = createFunction("add", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            val sum = add(params[0], params[1])
            ret(sum)
            finalizeFunction()
        }
        val bytes = WasmCodeGenerator().generate(mod)
        assertTrue(bytes.isNotEmpty())
        assertEquals(0x00, bytes[0].toInt() and 0xFF)
        assertEquals(0x61, bytes[1].toInt() and 0xFF)
        assertEquals(0x73, bytes[2].toInt() and 0xFF)
        assertEquals(0x6D, bytes[3].toInt() and 0xFF)
    }

    // --- Subtraction ---

    @Test
    fun subFunctionCompilesOnAllNative() {
        for ((target, gen) in listOf(
            Target.x86_64() to { mod: Module -> X86CodeGenerator().generateObjectFile(mod) },
            Target.arm64() to { mod: Module -> Arm64CodeGenerator().generateObjectFile(mod) },
            Target.riscv64() to { mod: Module -> RiscVCodeGenerator().generateObjectFile(mod) },
        )) {
            val mod = buildModule(target) {
                val params = createFunction("sub_fn", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
                appendBlock("entry")
                val result = sub(params[0], params[1])
                ret(result)
                finalizeFunction()
            }
            val obj = gen(mod)
            val text = obj.sections.first { it.kind == SectionKind.TEXT }
            assertTrue(text.data.isNotEmpty(), "sub should produce code on ${target.arch}")
        }
    }

    // --- Multiplication ---

    @Test
    fun mulFunctionCompilesOnAllNative() {
        for ((target, gen) in listOf(
            Target.x86_64() to { mod: Module -> X86CodeGenerator().generateObjectFile(mod) },
            Target.arm64() to { mod: Module -> Arm64CodeGenerator().generateObjectFile(mod) },
            Target.riscv64() to { mod: Module -> RiscVCodeGenerator().generateObjectFile(mod) },
        )) {
            val mod = buildModule(target) {
                val params = createFunction("mul_fn", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
                appendBlock("entry")
                val result = mul(params[0], params[1])
                ret(result)
                finalizeFunction()
            }
            val obj = gen(mod)
            assertTrue(obj.sections.any { it.kind == SectionKind.TEXT && it.data.isNotEmpty() })
        }
    }

    // --- Division ---

    @Test
    fun sdivFunctionCompilesOnAllNative() {
        for ((target, gen) in listOf(
            Target.x86_64() to { mod: Module -> X86CodeGenerator().generateObjectFile(mod) },
            Target.arm64() to { mod: Module -> Arm64CodeGenerator().generateObjectFile(mod) },
            Target.riscv64() to { mod: Module -> RiscVCodeGenerator().generateObjectFile(mod) },
        )) {
            val mod = buildModule(target) {
                val params = createFunction("div_fn", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
                appendBlock("entry")
                val result = sdiv(params[0], params[1])
                ret(result)
                finalizeFunction()
            }
            val obj = gen(mod)
            assertTrue(obj.sections.any { it.kind == SectionKind.TEXT && it.data.isNotEmpty() })
        }
    }

    // --- Bitwise operations ---

    @Test
    fun andFunctionCompilesOnAllNative() {
        for ((target, gen) in listOf(
            Target.x86_64() to { mod: Module -> X86CodeGenerator().generateObjectFile(mod) },
            Target.arm64() to { mod: Module -> Arm64CodeGenerator().generateObjectFile(mod) },
            Target.riscv64() to { mod: Module -> RiscVCodeGenerator().generateObjectFile(mod) },
        )) {
            val mod = buildModule(target) {
                val params = createFunction("and_fn", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
                appendBlock("entry")
                val result = and(params[0], params[1])
                ret(result)
                finalizeFunction()
            }
            val obj = gen(mod)
            assertTrue(obj.sections.any { it.kind == SectionKind.TEXT && it.data.isNotEmpty() })
        }
    }

    @Test
    fun orFunctionCompilesOnAllNative() {
        for ((target, gen) in listOf(
            Target.x86_64() to { mod: Module -> X86CodeGenerator().generateObjectFile(mod) },
            Target.arm64() to { mod: Module -> Arm64CodeGenerator().generateObjectFile(mod) },
            Target.riscv64() to { mod: Module -> RiscVCodeGenerator().generateObjectFile(mod) },
        )) {
            val mod = buildModule(target) {
                val params = createFunction("or_fn", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
                appendBlock("entry")
                val result = or(params[0], params[1])
                ret(result)
                finalizeFunction()
            }
            val obj = gen(mod)
            assertTrue(obj.sections.any { it.kind == SectionKind.TEXT && it.data.isNotEmpty() })
        }
    }

    @Test
    fun xorFunctionCompilesOnAllNative() {
        for ((target, gen) in listOf(
            Target.x86_64() to { mod: Module -> X86CodeGenerator().generateObjectFile(mod) },
            Target.arm64() to { mod: Module -> Arm64CodeGenerator().generateObjectFile(mod) },
            Target.riscv64() to { mod: Module -> RiscVCodeGenerator().generateObjectFile(mod) },
        )) {
            val mod = buildModule(target) {
                val params = createFunction("xor_fn", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
                appendBlock("entry")
                val result = xor(params[0], params[1])
                ret(result)
                finalizeFunction()
            }
            val obj = gen(mod)
            assertTrue(obj.sections.any { it.kind == SectionKind.TEXT && it.data.isNotEmpty() })
        }
    }

    // --- Shifts ---

    @Test
    fun shlFunctionCompilesOnAllNative() {
        for ((target, gen) in listOf(
            Target.x86_64() to { mod: Module -> X86CodeGenerator().generateObjectFile(mod) },
            Target.arm64() to { mod: Module -> Arm64CodeGenerator().generateObjectFile(mod) },
            Target.riscv64() to { mod: Module -> RiscVCodeGenerator().generateObjectFile(mod) },
        )) {
            val mod = buildModule(target) {
                val params = createFunction("shl_fn", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
                appendBlock("entry")
                val result = shl(params[0], params[1])
                ret(result)
                finalizeFunction()
            }
            val obj = gen(mod)
            assertTrue(obj.sections.any { it.kind == SectionKind.TEXT && it.data.isNotEmpty() })
        }
    }

    // --- Constant return ---

    @Test
    fun constantReturnCompilesOnAllNative() {
        for ((target, gen) in listOf(
            Target.x86_64() to { mod: Module -> X86CodeGenerator().generateObjectFile(mod) },
            Target.arm64() to { mod: Module -> Arm64CodeGenerator().generateObjectFile(mod) },
            Target.riscv64() to { mod: Module -> RiscVCodeGenerator().generateObjectFile(mod) },
        )) {
            val mod = buildModule(target) {
                createFunction("const42", emptyList(), Type.I32)
                appendBlock("entry")
                ret(Constant.I32(42))
                finalizeFunction()
            }
            val obj = gen(mod)
            assertTrue(obj.sections.any { it.kind == SectionKind.TEXT && it.data.isNotEmpty() })
        }
    }

    // --- Void function ---

    @Test
    fun voidFunctionCompilesOnAllNative() {
        for ((target, gen) in listOf(
            Target.x86_64() to { mod: Module -> X86CodeGenerator().generateObjectFile(mod) },
            Target.arm64() to { mod: Module -> Arm64CodeGenerator().generateObjectFile(mod) },
            Target.riscv64() to { mod: Module -> RiscVCodeGenerator().generateObjectFile(mod) },
        )) {
            val mod = buildModule(target) {
                createFunction("noop", emptyList(), Type.Void)
                appendBlock("entry")
                ret()
                finalizeFunction()
            }
            val obj = gen(mod)
            assertTrue(obj.sections.any { it.kind == SectionKind.TEXT && it.data.isNotEmpty() })
        }
    }

    // --- 64-bit operations ---

    @Test
    fun i64AddCompilesOnAllNative() {
        for ((target, gen) in listOf(
            Target.x86_64() to { mod: Module -> X86CodeGenerator().generateObjectFile(mod) },
            Target.arm64() to { mod: Module -> Arm64CodeGenerator().generateObjectFile(mod) },
            Target.riscv64() to { mod: Module -> RiscVCodeGenerator().generateObjectFile(mod) },
        )) {
            val mod = buildModule(target) {
                val params = createFunction("add64", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
                appendBlock("entry")
                val result = add(params[0], params[1])
                ret(result)
                finalizeFunction()
            }
            val obj = gen(mod)
            assertTrue(obj.sections.any { it.kind == SectionKind.TEXT && it.data.isNotEmpty() })
        }
    }

    // --- Conditional branching ---

    @Test
    fun diamondCFGCompilesOnX86() {
        val mod = buildModule(Target.x86_64()) {
            val params = createFunction("abs", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            val cond = icmp(ICmpPredicate.SGT, params[0], Constant.I32(0))
            condBr(cond, BlockRef("pos"), BlockRef("neg"))
            appendBlock("pos")
            br(BlockRef("merge"))
            appendBlock("neg")
            val negVal = sub(Constant.I32(0), params[0])
            br(BlockRef("merge"))
            appendBlock("merge")
            val phi = phi(Type.I32, listOf(params[0] to BlockRef("pos"), negVal to BlockRef("neg")))
            ret(phi)
            finalizeFunction()
        }
        val obj = X86CodeGenerator().generateObjectFile(mod)
        assertTrue(obj.sections.any { it.kind == SectionKind.TEXT && it.data.isNotEmpty() })
    }

    @Test
    fun diamondCFGCompilesOnArm64() {
        val mod = buildModule(Target.arm64()) {
            val params = createFunction("abs", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            val cond = icmp(ICmpPredicate.SGT, params[0], Constant.I32(0))
            condBr(cond, BlockRef("pos"), BlockRef("neg"))
            appendBlock("pos")
            br(BlockRef("merge"))
            appendBlock("neg")
            val negVal = sub(Constant.I32(0), params[0])
            br(BlockRef("merge"))
            appendBlock("merge")
            val phi = phi(Type.I32, listOf(params[0] to BlockRef("pos"), negVal to BlockRef("neg")))
            ret(phi)
            finalizeFunction()
        }
        val obj = Arm64CodeGenerator().generateObjectFile(mod)
        assertTrue(obj.sections.any { it.kind == SectionKind.TEXT && it.data.isNotEmpty() })
    }

    @Test
    fun diamondCFGCompilesOnRiscV() {
        val mod = buildModule(Target.riscv64()) {
            val params = createFunction("abs", listOf(Param("x", Type.I32)), Type.I32)
            appendBlock("entry")
            val cond = icmp(ICmpPredicate.SGT, params[0], Constant.I32(0))
            condBr(cond, BlockRef("pos"), BlockRef("neg"))
            appendBlock("pos")
            br(BlockRef("merge"))
            appendBlock("neg")
            val negVal = sub(Constant.I32(0), params[0])
            br(BlockRef("merge"))
            appendBlock("merge")
            val phi = phi(Type.I32, listOf(params[0] to BlockRef("pos"), negVal to BlockRef("neg")))
            ret(phi)
            finalizeFunction()
        }
        val obj = RiscVCodeGenerator().generateObjectFile(mod)
        assertTrue(obj.sections.any { it.kind == SectionKind.TEXT && it.data.isNotEmpty() })
    }

    // --- FP operations on x86 and ARM64 ---

    @Test
    fun faddCompilesOnX86() {
        val mod = buildModule(Target.x86_64()) {
            val params = createFunction("fadd_fn", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.F64)
            appendBlock("entry")
            val result = fadd(params[0], params[1])
            ret(result)
            finalizeFunction()
        }
        val obj = X86CodeGenerator().generateObjectFile(mod)
        assertTrue(obj.sections.any { it.kind == SectionKind.TEXT && it.data.isNotEmpty() })
    }

    @Test
    fun faddCompilesOnArm64() {
        val mod = buildModule(Target.arm64()) {
            val params = createFunction("fadd_fn", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.F64)
            appendBlock("entry")
            val result = fadd(params[0], params[1])
            ret(result)
            finalizeFunction()
        }
        val obj = Arm64CodeGenerator().generateObjectFile(mod)
        assertTrue(obj.sections.any { it.kind == SectionKind.TEXT && it.data.isNotEmpty() })
    }

    @Test
    fun fmulCompilesOnX86() {
        val mod = buildModule(Target.x86_64()) {
            val params = createFunction("fmul_fn", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.F64)
            appendBlock("entry")
            val result = fmul(params[0], params[1])
            ret(result)
            finalizeFunction()
        }
        val obj = X86CodeGenerator().generateObjectFile(mod)
        assertTrue(obj.sections.any { it.kind == SectionKind.TEXT && it.data.isNotEmpty() })
    }

    @Test
    fun fmulCompilesOnArm64() {
        val mod = buildModule(Target.arm64()) {
            val params = createFunction("fmul_fn", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.F64)
            appendBlock("entry")
            val result = fmul(params[0], params[1])
            ret(result)
            finalizeFunction()
        }
        val obj = Arm64CodeGenerator().generateObjectFile(mod)
        assertTrue(obj.sections.any { it.kind == SectionKind.TEXT && it.data.isNotEmpty() })
    }

    // --- WASM-specific tests ---

    @Test
    fun wasmAddI32() {
        val mod = buildModule(Target.wasm()) {
            val params = createFunction("add", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            ret(add(params[0], params[1]))
            finalizeFunction()
        }
        val bytes = WasmCodeGenerator().generate(mod)
        assertTrue(bytes.size > 8)
    }

    @Test
    fun wasmMulI32() {
        val mod = buildModule(Target.wasm()) {
            val params = createFunction("mul", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            ret(mul(params[0], params[1]))
            finalizeFunction()
        }
        val bytes = WasmCodeGenerator().generate(mod)
        assertTrue(bytes.size > 8)
    }

    @Test
    fun wasmSubI32() {
        val mod = buildModule(Target.wasm()) {
            val params = createFunction("sub", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            ret(sub(params[0], params[1]))
            finalizeFunction()
        }
        val bytes = WasmCodeGenerator().generate(mod)
        assertTrue(bytes.size > 8)
    }

    // --- Multiple functions in same module ---

    @Test
    fun multipleFunctionsCompilesOnX86() {
        val mod = buildModule(Target.x86_64()) {
            val p1 = createFunction("add", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            ret(add(p1[0], p1[1]))
            finalizeFunction()

            val p2 = createFunction("sub", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            ret(sub(p2[0], p2[1]))
            finalizeFunction()

            val p3 = createFunction("mul", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            ret(mul(p3[0], p3[1]))
            finalizeFunction()
        }
        val obj = X86CodeGenerator().generateObjectFile(mod)
        assertEquals(3, obj.symbols.count { it.kind == org.kgen.binary.SymbolKind.FUNCTION })
    }

    @Test
    fun multipleFunctionsCompilesOnArm64() {
        val mod = buildModule(Target.arm64()) {
            val p1 = createFunction("add", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            ret(add(p1[0], p1[1]))
            finalizeFunction()

            val p2 = createFunction("sub", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            appendBlock("entry")
            ret(sub(p2[0], p2[1]))
            finalizeFunction()
        }
        val obj = Arm64CodeGenerator().generateObjectFile(mod)
        assertEquals(2, obj.symbols.count { it.kind == org.kgen.binary.SymbolKind.FUNCTION })
    }

    // --- TargetRegistry ---

    @Test
    fun targetRegistryRegistersAndLooksUp() {
        val registry = TargetRegistry()
        registry.registerGenerator(X86CodeGenerator())
        registry.registerGenerator(Arm64CodeGenerator())
        registry.registerGenerator(RiscVCodeGenerator())
        registry.registerGenerator(WasmCodeGenerator())

        assertNotNull(registry.generator("x86_64"))
        assertNotNull(registry.generator("aarch64"))
        assertNotNull(registry.generator("riscv"))
        assertNotNull(registry.generator("wasm"))
    }

    @Test
    fun targetRegistryAvailableTargets() {
        val registry = TargetRegistry()
        registry.registerGenerator(X86CodeGenerator())
        registry.registerGenerator(WasmCodeGenerator())

        val targets = registry.availableTargets()
        assertTrue(targets.contains("x86_64"))
        assertTrue(targets.contains("wasm"))
    }

    @Test
    fun targetRegistryUnknownTargetThrows() {
        val registry = TargetRegistry()
        assertThrows(IllegalStateException::class.java) {
            registry.generator("nonexistent")
        }
    }
}
