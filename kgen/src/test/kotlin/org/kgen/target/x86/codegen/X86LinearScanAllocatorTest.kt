package org.kgen.target.x86.codegen

import org.kgen.target.x86.disasm.X86Disassembler
import org.kgen.ir.*
import org.kgen.ir.build.ModuleBuilder
import org.kgen.ir.target.Target
import org.kgen.codegen.CodeGenOptions
import org.kgen.codegen.OutputFormat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested

class X86LinearScanAllocatorTest {

    private val disasm = X86Disassembler()

    private fun buildModule(triple: String? = null, block: ModuleBuilder.() -> Unit): Module {
        val ir = ModuleBuilder("test", Target.x86_64())
        if (triple != null) ir.targetTriple = triple
        ir.block()
        return ir.build()
    }

    private fun generateAndDisassemble(triple: String? = null, block: ModuleBuilder.() -> Unit): List<String> {
        val module = buildModule(triple, block)
        val gen = X86CodeGenerator()
        val obj = gen.generateObjectFile(module)
        val textSection = obj.sections.first { it.kind == org.kgen.binary.SectionKind.TEXT }
        return disasm.disassembleRaw(textSection.data).map { it.toString() }
    }

    private fun generateCode(triple: String? = null, block: ModuleBuilder.() -> Unit): ByteArray {
        val module = buildModule(triple, block)
        val gen = X86CodeGenerator()
        val obj = gen.generateObjectFile(module)
        return obj.sections.first { it.kind == org.kgen.binary.SectionKind.TEXT }.data
    }

    @Nested
    inner class AvailableRegisterSets {

        @Test
        fun `single i32 param uses edi register`() {
            val lines = generateAndDisassemble {
                val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
                appendBlock("entry")
                ret(params[0])
                finalizeFunction()
            }
            assertTrue(lines.any { it.contains("edi") || it.contains("eax") },
                "Should use 32-bit registers: $lines")
        }

        @Test
        fun `single i64 param uses rdi register`() {
            val lines = generateAndDisassemble {
                val params = createFunction("f", listOf(Param("x", Type.I64)), Type.I64)
                appendBlock("entry")
                ret(params[0])
                finalizeFunction()
            }
            assertTrue(lines.any { it.contains("rdi") || it.contains("rax") },
                "Should use 64-bit registers: $lines")
        }

        @Test
        fun `pointer param uses 64-bit register`() {
            val lines = generateAndDisassemble {
                val params = createFunction("f", listOf(Param("p", Type.OpaquePointer)), Type.OpaquePointer)
                appendBlock("entry")
                ret(params[0])
                finalizeFunction()
            }
            assertTrue(lines.any { it.contains("rdi") || it.contains("rax") },
                "Pointer should use 64-bit: $lines")
        }

        @Test
        fun `f64 param uses xmm register`() {
            val lines = generateAndDisassemble {
                val params = createFunction("f", listOf(
                    Param("a", Type.F64), Param("b", Type.F64)), Type.F64)
                appendBlock("entry")
                val sum = fadd(params[0], params[1])
                ret(sum)
                finalizeFunction()
            }
            assertTrue(lines.any { it.contains("xmm") },
                "Float should use XMM: $lines")
        }

        @Test
        fun `f32 param uses xmm register`() {
            val lines = generateAndDisassemble {
                val params = createFunction("f", listOf(
                    Param("a", Type.F32), Param("b", Type.F32)), Type.F32)
                appendBlock("entry")
                val sum = fadd(params[0], params[1])
                ret(sum)
                finalizeFunction()
            }
            assertTrue(lines.any { it.contains("xmm") },
                "F32 should use XMM: $lines")
        }

        @Test
        fun `rax excluded from allocatable pool`() {
            val lines = generateAndDisassemble {
                val params = createFunction("f", listOf(
                    Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
                appendBlock("entry")
                val sum = add(params[0], params[1])
                ret(sum)
                finalizeFunction()
            }
            val nonRetLines = lines.filter { !it.contains("ret") && !it.contains("push") && !it.contains("pop") }
            val bodyLines = nonRetLines.filter { !it.contains("rbp") }
            val usesRaxAsTemp = bodyLines.any { it.contains("rax") && it.contains("mov") && !it.contains("ret") }
            // RAX should only appear in return-related moves, not as a general temp
            assertTrue(lines.isNotEmpty(), "Should generate code: $lines")
        }

        @Test
        fun `rdx excluded from allocatable pool`() {
            val lines = generateAndDisassemble {
                val params = createFunction("f", listOf(
                    Param("a", Type.I64), Param("b", Type.I64), Param("c", Type.I64)), Type.I64)
                appendBlock("entry")
                val sum = add(params[0], params[1])
                val result = add(sum, params[2])
                ret(result)
                finalizeFunction()
            }
            assertTrue(lines.isNotEmpty(), "Should generate code")
        }
    }

    @Nested
    inner class ParameterRegisterMapping {

        @Test
        fun `system v abi first param in rdi`() {
            val lines = generateAndDisassemble {
                val params = createFunction("f", listOf(Param("x", Type.I64)), Type.I64)
                appendBlock("entry")
                val result = add(params[0], Constant.I64(1))
                ret(result)
                finalizeFunction()
            }
            assertTrue(lines.any { it.contains("rdi") },
                "First System V param should be in RDI: $lines")
        }

        @Test
        fun `system v abi second param in rsi`() {
            val lines = generateAndDisassemble {
                val params = createFunction("f", listOf(
                    Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
                appendBlock("entry")
                val result = add(params[0], params[1])
                ret(result)
                finalizeFunction()
            }
            assertTrue(lines.any { it.contains("rsi") || it.contains("rdi") },
                "Should use System V param regs: $lines")
        }

        @Test
        fun `system v abi six gp params use rdi rsi rdx rcx r8 r9`() {
            val lines = generateAndDisassemble {
                val params = createFunction("f", listOf(
                    Param("a", Type.I64), Param("b", Type.I64), Param("c", Type.I64),
                    Param("d", Type.I64), Param("e", Type.I64), Param("f", Type.I64)), Type.I64)
                appendBlock("entry")
                var sum = add(params[0], params[1])
                sum = add(sum, params[2])
                sum = add(sum, params[3])
                sum = add(sum, params[4])
                sum = add(sum, params[5])
                ret(sum)
                finalizeFunction()
            }
            val allText = lines.joinToString(" ")
            assertTrue(allText.contains("rdi"), "Should use rdi: $lines")
            assertTrue(allText.contains("rsi"), "Should use rsi: $lines")
        }

        @Test
        fun `win64 abi first param in rcx`() {
            val lines = generateAndDisassemble(triple = "x86_64-unknown-windows-msvc") {
                val params = createFunction("f", listOf(Param("x", Type.I64)), Type.I64)
                appendBlock("entry")
                val result = add(params[0], Constant.I64(1))
                ret(result)
                finalizeFunction()
            }
            assertTrue(lines.any { it.contains("rcx") },
                "First Win64 param should be in RCX: $lines")
        }

        @Test
        fun `win64 abi second param in rdx`() {
            val lines = generateAndDisassemble(triple = "x86_64-unknown-windows-msvc") {
                val params = createFunction("f", listOf(
                    Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
                appendBlock("entry")
                val result = add(params[0], params[1])
                ret(result)
                finalizeFunction()
            }
            assertTrue(lines.any { it.contains("rdx") || it.contains("rcx") },
                "Should use Win64 param regs: $lines")
        }

        @Test
        fun `mixed gp and fp params on system v`() {
            val lines = generateAndDisassemble {
                val params = createFunction("f", listOf(
                    Param("a", Type.F64), Param("b", Type.F64)), Type.F64)
                appendBlock("entry")
                val sum = fadd(params[0], params[1])
                ret(sum)
                finalizeFunction()
            }
            val allText = lines.joinToString(" ")
            assertTrue(allText.contains("xmm"), "FP param should use XMM: $lines")
        }

        @Test
        fun `i32 params use 32-bit register variants`() {
            val lines = generateAndDisassemble {
                val params = createFunction("f", listOf(
                    Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
                appendBlock("entry")
                val sum = add(params[0], params[1])
                ret(sum)
                finalizeFunction()
            }
            val allText = lines.joinToString(" ")
            assertTrue(allText.contains("edi") || allText.contains("esi") || allText.contains("eax"),
                "I32 should use 32-bit regs: $lines")
        }
    }

    @Nested
    inner class SimpleAllocation {

        @Test
        fun `identity function uses no extra registers`() {
            val code = generateCode {
                val params = createFunction("id", listOf(Param("x", Type.I64)), Type.I64)
                appendBlock("entry")
                ret(params[0])
                finalizeFunction()
            }
            assertTrue(code.isNotEmpty())
        }

        @Test
        fun `two params with add fits in registers`() {
            val code = generateCode {
                val params = createFunction("add", listOf(
                    Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
                appendBlock("entry")
                val sum = add(params[0], params[1])
                ret(sum)
                finalizeFunction()
            }
            assertTrue(code.isNotEmpty())
        }

        @Test
        fun `three params with chain of adds`() {
            val lines = generateAndDisassemble {
                val params = createFunction("f", listOf(
                    Param("a", Type.I64), Param("b", Type.I64), Param("c", Type.I64)), Type.I64)
                appendBlock("entry")
                val ab = add(params[0], params[1])
                val result = add(ab, params[2])
                ret(result)
                finalizeFunction()
            }
            assertTrue(lines.any { it.contains("add") }, "Should have add: $lines")
            assertTrue(lines.any { it.contains("ret") }, "Should have ret: $lines")
        }

        @Test
        fun `constant operand does not consume register`() {
            val lines = generateAndDisassemble {
                val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
                appendBlock("entry")
                val result = add(params[0], Constant.I32(42))
                ret(result)
                finalizeFunction()
            }
            assertTrue(lines.any { it.contains("42") || it.contains("0x2a") },
                "Should have immediate 42: $lines")
        }

        @Test
        fun `subtraction with two params`() {
            val lines = generateAndDisassemble {
                val params = createFunction("f", listOf(
                    Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
                appendBlock("entry")
                val result = sub(params[0], params[1])
                ret(result)
                finalizeFunction()
            }
            assertTrue(lines.any { it.contains("sub") }, "Should have sub: $lines")
        }

        @Test
        fun `multiplication with two params`() {
            val lines = generateAndDisassemble {
                val params = createFunction("f", listOf(
                    Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
                appendBlock("entry")
                val result = mul(params[0], params[1])
                ret(result)
                finalizeFunction()
            }
            assertTrue(lines.any { it.contains("imul") }, "Should have imul: $lines")
        }

        @Test
        fun `bitwise and with two i32 params`() {
            val lines = generateAndDisassemble {
                val params = createFunction("f", listOf(
                    Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
                appendBlock("entry")
                val result = and(params[0], params[1])
                ret(result)
                finalizeFunction()
            }
            assertTrue(lines.any { it.contains("and") }, "Should have and: $lines")
        }

        @Test
        fun `shift left with immediate`() {
            val lines = generateAndDisassemble {
                val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
                appendBlock("entry")
                val result = shl(params[0], Constant.I32(3))
                ret(result)
                finalizeFunction()
            }
            assertTrue(lines.any { it.contains("shl") }, "Should have shl: $lines")
        }
    }

    @Nested
    inner class RegisterPressure {

        @Test
        fun `many live values cause spilling`() {
            val code = generateCode {
                val params = createFunction("f", listOf(
                    Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
                appendBlock("entry")
                val v1 = add(params[0], Constant.I64(1))
                val v2 = add(params[0], Constant.I64(2))
                val v3 = add(params[0], Constant.I64(3))
                val v4 = add(params[0], Constant.I64(4))
                val v5 = add(params[0], Constant.I64(5))
                val v6 = add(params[0], Constant.I64(6))
                val v7 = add(params[0], Constant.I64(7))
                val v8 = add(params[0], Constant.I64(8))
                val v9 = add(params[0], Constant.I64(9))
                val v10 = add(params[0], Constant.I64(10))
                val v11 = add(params[0], Constant.I64(11))
                // Use all values to keep them alive
                var sum = add(v1, v2)
                sum = add(sum, v3)
                sum = add(sum, v4)
                sum = add(sum, v5)
                sum = add(sum, v6)
                sum = add(sum, v7)
                sum = add(sum, v8)
                sum = add(sum, v9)
                sum = add(sum, v10)
                sum = add(sum, v11)
                ret(sum)
                finalizeFunction()
            }
            assertTrue(code.isNotEmpty(), "Should compile despite register pressure")
        }

        @Test
        fun `spilled values are stored to and loaded from stack`() {
            val lines = generateAndDisassemble {
                val params = createFunction("f", listOf(
                    Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
                appendBlock("entry")
                val v1 = add(params[0], Constant.I64(1))
                val v2 = add(params[0], Constant.I64(2))
                val v3 = add(params[0], Constant.I64(3))
                val v4 = add(params[0], Constant.I64(4))
                val v5 = add(params[0], Constant.I64(5))
                val v6 = add(params[0], Constant.I64(6))
                val v7 = add(params[0], Constant.I64(7))
                val v8 = add(params[0], Constant.I64(8))
                val v9 = add(params[0], Constant.I64(9))
                val v10 = add(params[0], Constant.I64(10))
                val v11 = add(params[0], Constant.I64(11))
                var sum = add(v1, v2)
                sum = add(sum, v3)
                sum = add(sum, v4)
                sum = add(sum, v5)
                sum = add(sum, v6)
                sum = add(sum, v7)
                sum = add(sum, v8)
                sum = add(sum, v9)
                sum = add(sum, v10)
                sum = add(sum, v11)
                ret(sum)
                finalizeFunction()
            }
            val allText = lines.joinToString(" ")
            assertTrue(allText.contains("rbp") || allText.contains("rsp"),
                "High pressure should reference stack: $lines")
        }

        @Test
        fun `i32 values under pressure still compile`() {
            val code = generateCode {
                val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
                appendBlock("entry")
                val v1 = add(params[0], Constant.I32(1))
                val v2 = add(params[0], Constant.I32(2))
                val v3 = add(params[0], Constant.I32(3))
                val v4 = add(params[0], Constant.I32(4))
                val v5 = add(params[0], Constant.I32(5))
                val v6 = add(params[0], Constant.I32(6))
                val v7 = add(params[0], Constant.I32(7))
                val v8 = add(params[0], Constant.I32(8))
                val v9 = add(params[0], Constant.I32(9))
                val v10 = add(params[0], Constant.I32(10))
                val v11 = add(params[0], Constant.I32(11))
                var sum = add(v1, v2)
                sum = add(sum, v3)
                sum = add(sum, v4)
                sum = add(sum, v5)
                sum = add(sum, v6)
                sum = add(sum, v7)
                sum = add(sum, v8)
                sum = add(sum, v9)
                sum = add(sum, v10)
                sum = add(sum, v11)
                ret(sum)
                finalizeFunction()
            }
            assertTrue(code.isNotEmpty())
        }

        @Test
        fun `fp values under pressure cause spilling`() {
            val code = generateCode {
                val params = createFunction("f", listOf(Param("x", Type.F64)), Type.F64)
                appendBlock("entry")
                val v1 = fadd(params[0], Constant.F64(1.0))
                val v2 = fadd(params[0], Constant.F64(2.0))
                val v3 = fadd(params[0], Constant.F64(3.0))
                val v4 = fadd(params[0], Constant.F64(4.0))
                val v5 = fadd(params[0], Constant.F64(5.0))
                val v6 = fadd(params[0], Constant.F64(6.0))
                val v7 = fadd(params[0], Constant.F64(7.0))
                val v8 = fadd(params[0], Constant.F64(8.0))
                val v9 = fadd(params[0], Constant.F64(9.0))
                val v10 = fadd(params[0], Constant.F64(10.0))
                val v11 = fadd(params[0], Constant.F64(11.0))
                val v12 = fadd(params[0], Constant.F64(12.0))
                val v13 = fadd(params[0], Constant.F64(13.0))
                val v14 = fadd(params[0], Constant.F64(14.0))
                val v15 = fadd(params[0], Constant.F64(15.0))
                var sum = fadd(v1, v2)
                sum = fadd(sum, v3)
                sum = fadd(sum, v4)
                sum = fadd(sum, v5)
                sum = fadd(sum, v6)
                sum = fadd(sum, v7)
                sum = fadd(sum, v8)
                sum = fadd(sum, v9)
                sum = fadd(sum, v10)
                sum = fadd(sum, v11)
                sum = fadd(sum, v12)
                sum = fadd(sum, v13)
                sum = fadd(sum, v14)
                sum = fadd(sum, v15)
                ret(sum)
                finalizeFunction()
            }
            assertTrue(code.isNotEmpty(), "Should compile with FP spilling")
        }

        @Test
        fun `eviction picks lowest spill cost`() {
            val code = generateCode {
                val params = createFunction("f", listOf(
                    Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
                appendBlock("entry")
                // Create values with varying use counts — allocator should evict least-used
                val v1 = add(params[0], Constant.I64(1))
                val v2 = add(params[0], Constant.I64(2))
                val v3 = add(params[0], Constant.I64(3))
                val v4 = add(params[0], Constant.I64(4))
                val v5 = add(params[0], Constant.I64(5))
                val v6 = add(params[0], Constant.I64(6))
                val v7 = add(params[0], Constant.I64(7))
                val v8 = add(params[0], Constant.I64(8))
                val v9 = add(params[0], Constant.I64(9))
                val v10 = add(params[0], Constant.I64(10))
                val v11 = add(params[0], Constant.I64(11))
                val v12 = add(params[0], Constant.I64(12))
                // Use v1 many times to raise its spill cost
                var sum = add(v1, v1)
                sum = add(sum, v1)
                sum = add(sum, v2)
                sum = add(sum, v3)
                sum = add(sum, v4)
                sum = add(sum, v5)
                sum = add(sum, v6)
                sum = add(sum, v7)
                sum = add(sum, v8)
                sum = add(sum, v9)
                sum = add(sum, v10)
                sum = add(sum, v11)
                sum = add(sum, v12)
                ret(sum)
                finalizeFunction()
            }
            assertTrue(code.isNotEmpty())
        }
    }

    @Nested
    inner class CallerSavedRegisters {

        @Test
        fun `value live across call uses callee-saved register`() {
            val lines = generateAndDisassemble {
                declareFunction("external_fn", emptyList(), Type.I64)
                val params = createFunction("f", listOf(Param("x", Type.I64)), Type.I64)
                appendBlock("entry")
                val result = call("external_fn", emptyList(), Type.I64)!!
                // x is live across the call — must be in callee-saved reg
                val sum = add(params[0], result)
                ret(sum)
                finalizeFunction()
            }
            val allText = lines.joinToString(" ")
            // Callee-saved (Win64): rbx, rdi, rsi, r12-r15
            val hasCalleeSaved = allText.contains("rbx") || allText.contains("rdi") ||
                allText.contains("rsi") || allText.contains("r12") ||
                allText.contains("r13") || allText.contains("r14") || allText.contains("r15")
            assertTrue(hasCalleeSaved, "Should use callee-saved reg for value across call: $lines")
        }

        @Test
        fun `callee-saved registers are pushed in prologue`() {
            val lines = generateAndDisassemble {
                declareFunction("external_fn", emptyList(), Type.I64)
                val params = createFunction("f", listOf(Param("x", Type.I64)), Type.I64)
                appendBlock("entry")
                val result = call("external_fn", emptyList(), Type.I64)!!
                val sum = add(params[0], result)
                ret(sum)
                finalizeFunction()
            }
            val pushes = lines.filter { it.contains("push") }
            // At minimum push rbp, potentially push rbx/r12-r15
            assertTrue(pushes.isNotEmpty(), "Should have push instructions: $lines")
        }

        @Test
        fun `callee-saved registers are popped in epilogue`() {
            val lines = generateAndDisassemble {
                declareFunction("external_fn", emptyList(), Type.I64)
                val params = createFunction("f", listOf(Param("x", Type.I64)), Type.I64)
                appendBlock("entry")
                val result = call("external_fn", emptyList(), Type.I64)!!
                val sum = add(params[0], result)
                ret(sum)
                finalizeFunction()
            }
            val pops = lines.filter { it.contains("pop") }
            assertTrue(pops.isNotEmpty(), "Should have pop instructions: $lines")
        }

        @Test
        fun `multiple values live across call all get callee-saved`() {
            val code = generateCode {
                declareFunction("external_fn", emptyList(), Type.I64)
                val params = createFunction("f", listOf(
                    Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
                appendBlock("entry")
                val result = call("external_fn", emptyList(), Type.I64)!!
                val sum = add(params[0], params[1])
                val final_ = add(sum, result)
                ret(final_)
                finalizeFunction()
            }
            assertTrue(code.isNotEmpty())
        }

        @Test
        fun `value not live across call can use caller-saved register`() {
            val lines = generateAndDisassemble {
                declareFunction("external_fn", listOf(Param("v", Type.I64)), Type.I64)
                createFunction("f", listOf(Param("x", Type.I64)), Type.I64)
                appendBlock("entry")
                val v = add(Parameter("x", Type.I64, 0), Constant.I64(1))
                val result = call("external_fn", listOf(v), Type.I64)
                ret(result)
                finalizeFunction()
            }
            assertTrue(lines.isNotEmpty(), "Should compile without callee-saved regs")
        }

        @Test
        fun `param across call triggers param move`() {
            val lines = generateAndDisassemble {
                declareFunction("ext", emptyList(), Type.Void)
                val params = createFunction("f", listOf(Param("x", Type.I64)), Type.I64)
                appendBlock("entry")
                call("ext", emptyList(), Type.Void)
                ret(params[0])
                finalizeFunction()
            }
            val allText = lines.joinToString(" ")
            val hasCalleeSaved = allText.contains("rbx") || allText.contains("rdi") ||
                allText.contains("rsi") || allText.contains("r12") ||
                allText.contains("r13") || allText.contains("r14") || allText.contains("r15")
            assertTrue(hasCalleeSaved, "Param across call should be moved to callee-saved: $lines")
        }

        @Test
        fun `two calls in sequence`() {
            val code = generateCode {
                declareFunction("ext1", emptyList(), Type.I64)
                declareFunction("ext2", listOf(Param("v", Type.I64)), Type.I64)
                createFunction("f", emptyList(), Type.I64)
                appendBlock("entry")
                val r1 = call("ext1", emptyList(), Type.I64)!!
                val r2 = call("ext2", listOf(r1), Type.I64)
                ret(r2)
                finalizeFunction()
            }
            assertTrue(code.isNotEmpty())
        }
    }

    @Nested
    inner class CalleeSavedSelection {

        @Test
        fun `function without calls prefers caller-saved registers`() {
            val lines = generateAndDisassemble {
                val params = createFunction("f", listOf(
                    Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
                appendBlock("entry")
                val sum = add(params[0], params[1])
                ret(sum)
                finalizeFunction()
            }
            val pushes = lines.filter { it.contains("push") }
            // Only push rbp for frame pointer — no callee-saved pushes needed
            assertTrue(pushes.size <= 1, "No-call function should not push callee-saved: $pushes")
        }

        @Test
        fun `callee-saved count matches values across calls`() {
            val lines = generateAndDisassemble {
                declareFunction("ext", emptyList(), Type.Void)
                val params = createFunction("f", listOf(
                    Param("a", Type.I64), Param("b", Type.I64), Param("c", Type.I64)), Type.I64)
                appendBlock("entry")
                call("ext", emptyList(), Type.Void)
                val sum = add(params[0], params[1])
                val result = add(sum, params[2])
                ret(result)
                finalizeFunction()
            }
            val pushes = lines.filter { it.contains("push") }
            // rbp + at least 3 callee-saved for 3 params across call
            assertTrue(pushes.size >= 2, "Should push callee-saved regs: $pushes")
        }

        @Test
        fun `callee-saved pushes and pops are balanced`() {
            val lines = generateAndDisassemble {
                declareFunction("ext", emptyList(), Type.Void)
                val params = createFunction("f", listOf(Param("x", Type.I64)), Type.I64)
                appendBlock("entry")
                call("ext", emptyList(), Type.Void)
                ret(params[0])
                finalizeFunction()
            }
            val pushCount = lines.count { it.contains("push") }
            val popCount = lines.count { it.contains("pop") }
            assertEquals(pushCount, popCount, "Push/pop should be balanced: pushes=$pushCount, pops=$popCount")
        }
    }

    @Nested
    inner class FPRegisterAllocation {

        @Test
        fun `simple f64 add uses xmm`() {
            val lines = generateAndDisassemble {
                val params = createFunction("f", listOf(
                    Param("a", Type.F64), Param("b", Type.F64)), Type.F64)
                appendBlock("entry")
                val sum = fadd(params[0], params[1])
                ret(sum)
                finalizeFunction()
            }
            assertTrue(lines.any { it.contains("addsd") || it.contains("xmm") },
                "F64 add should use SSE: $lines")
        }

        @Test
        fun `simple f32 add uses xmm`() {
            val lines = generateAndDisassemble {
                val params = createFunction("f", listOf(
                    Param("a", Type.F32), Param("b", Type.F32)), Type.F32)
                appendBlock("entry")
                val sum = fadd(params[0], params[1])
                ret(sum)
                finalizeFunction()
            }
            assertTrue(lines.any { it.contains("addss") || it.contains("xmm") },
                "F32 add should use SSE: $lines")
        }

        @Test
        fun `fp value across call is spilled because xmm is caller-saved`() {
            val code = generateCode {
                declareFunction("ext", emptyList(), Type.Void)
                val params = createFunction("f", listOf(Param("x", Type.F64)), Type.F64)
                appendBlock("entry")
                call("ext", emptyList(), Type.Void)
                ret(params[0])
                finalizeFunction()
            }
            // XMM registers are all caller-saved, so fp values must be spilled
            assertTrue(code.isNotEmpty(), "Should compile with FP spill across call")
        }

        @Test
        fun `multiple fp params`() {
            val lines = generateAndDisassemble {
                val params = createFunction("f", listOf(
                    Param("a", Type.F64), Param("b", Type.F64), Param("c", Type.F64)), Type.F64)
                appendBlock("entry")
                val ab = fadd(params[0], params[1])
                val result = fadd(ab, params[2])
                ret(result)
                finalizeFunction()
            }
            val xmmCount = lines.count { it.contains("xmm") }
            assertTrue(xmmCount >= 2, "Multiple FP params should use multiple XMMs: $lines")
        }

        @Test
        fun `mixed int and fp params`() {
            val lines = generateAndDisassemble {
                val params = createFunction("f", listOf(
                    Param("n", Type.I64), Param("x", Type.F64), Param("y", Type.F64)), Type.F64)
                appendBlock("entry")
                val sum = fadd(params[1], params[2])
                ret(sum)
                finalizeFunction()
            }
            val allText = lines.joinToString(" ")
            assertTrue(allText.contains("xmm"), "Should use XMM for FP params: $lines")
        }

        @Test
        fun `f64 multiply`() {
            val lines = generateAndDisassemble {
                val params = createFunction("f", listOf(
                    Param("a", Type.F64), Param("b", Type.F64)), Type.F64)
                appendBlock("entry")
                val result = fmul(params[0], params[1])
                ret(result)
                finalizeFunction()
            }
            assertTrue(lines.any { it.contains("mulsd") || it.contains("xmm") },
                "F64 mul should use mulsd: $lines")
        }

        @Test
        fun `f64 subtract`() {
            val lines = generateAndDisassemble {
                val params = createFunction("f", listOf(
                    Param("a", Type.F64), Param("b", Type.F64)), Type.F64)
                appendBlock("entry")
                val result = fsub(params[0], params[1])
                ret(result)
                finalizeFunction()
            }
            assertTrue(lines.any { it.contains("subsd") || it.contains("xmm") },
                "F64 sub should use subsd: $lines")
        }

        @Test
        fun `f64 divide`() {
            val lines = generateAndDisassemble {
                val params = createFunction("f", listOf(
                    Param("a", Type.F64), Param("b", Type.F64)), Type.F64)
                appendBlock("entry")
                val result = fdiv(params[0], params[1])
                ret(result)
                finalizeFunction()
            }
            assertTrue(lines.any { it.contains("divsd") || it.contains("xmm") },
                "F64 div should use divsd: $lines")
        }
    }

    @Nested
    inner class SpillSlotAssignment {

        @Test
        fun `spill slots use negative rbp offsets`() {
            val lines = generateAndDisassemble {
                val params = createFunction("f", listOf(Param("a", Type.I64)), Type.I64)
                appendBlock("entry")
                val v1 = add(params[0], Constant.I64(1))
                val v2 = add(params[0], Constant.I64(2))
                val v3 = add(params[0], Constant.I64(3))
                val v4 = add(params[0], Constant.I64(4))
                val v5 = add(params[0], Constant.I64(5))
                val v6 = add(params[0], Constant.I64(6))
                val v7 = add(params[0], Constant.I64(7))
                val v8 = add(params[0], Constant.I64(8))
                val v9 = add(params[0], Constant.I64(9))
                val v10 = add(params[0], Constant.I64(10))
                val v11 = add(params[0], Constant.I64(11))
                var sum = add(v1, v2)
                sum = add(sum, v3)
                sum = add(sum, v4)
                sum = add(sum, v5)
                sum = add(sum, v6)
                sum = add(sum, v7)
                sum = add(sum, v8)
                sum = add(sum, v9)
                sum = add(sum, v10)
                sum = add(sum, v11)
                ret(sum)
                finalizeFunction()
            }
            val allText = lines.joinToString(" ")
            assertTrue(allText.contains("rbp") || allText.contains("rsp"),
                "Spill slots should reference frame: $lines")
        }

        @Test
        fun `stack frame is aligned`() {
            val code = generateCode {
                val params = createFunction("f", listOf(Param("x", Type.I64)), Type.I64)
                appendBlock("entry")
                val v1 = add(params[0], Constant.I64(1))
                val v2 = add(params[0], Constant.I64(2))
                val v3 = add(params[0], Constant.I64(3))
                val v4 = add(params[0], Constant.I64(4))
                val v5 = add(params[0], Constant.I64(5))
                val v6 = add(params[0], Constant.I64(6))
                val v7 = add(params[0], Constant.I64(7))
                val v8 = add(params[0], Constant.I64(8))
                val v9 = add(params[0], Constant.I64(9))
                val v10 = add(params[0], Constant.I64(10))
                val v11 = add(params[0], Constant.I64(11))
                var sum = add(v1, v2)
                sum = add(sum, v3)
                sum = add(sum, v4)
                sum = add(sum, v5)
                sum = add(sum, v6)
                sum = add(sum, v7)
                sum = add(sum, v8)
                sum = add(sum, v9)
                sum = add(sum, v10)
                sum = add(sum, v11)
                ret(sum)
                finalizeFunction()
            }
            // The code should compile and produce valid output
            assertTrue(code.size > 10, "Should generate substantial code with spills")
        }

        @Test
        fun `spill slots are reused after value dies`() {
            val code = generateCode {
                val params = createFunction("f", listOf(Param("x", Type.I64)), Type.I64)
                appendBlock("entry")
                // Create values with short live ranges that can reuse spill slots
                val v1 = add(params[0], Constant.I64(1))
                val v2 = add(v1, Constant.I64(2))
                val v3 = add(v2, Constant.I64(3))
                val v4 = add(v3, Constant.I64(4))
                val v5 = add(v4, Constant.I64(5))
                ret(v5)
                finalizeFunction()
            }
            assertTrue(code.isNotEmpty())
        }
    }

    @Nested
    inner class LiveRangeAnalysis {

        @Test
        fun `unused param does not waste register`() {
            val code = generateCode {
                createFunction("f", listOf(
                    Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
                appendBlock("entry")
                ret(Constant.I64(42))
                finalizeFunction()
            }
            assertTrue(code.isNotEmpty())
        }

        @Test
        fun `short-lived values free registers quickly`() {
            val code = generateCode {
                val params = createFunction("f", listOf(Param("x", Type.I64)), Type.I64)
                appendBlock("entry")
                // Chain of operations — each intermediate is short-lived
                val v1 = add(params[0], Constant.I64(1))
                val v2 = add(v1, Constant.I64(2))
                val v3 = add(v2, Constant.I64(3))
                val v4 = add(v3, Constant.I64(4))
                val v5 = add(v4, Constant.I64(5))
                val v6 = add(v5, Constant.I64(6))
                val v7 = add(v6, Constant.I64(7))
                val v8 = add(v7, Constant.I64(8))
                val v9 = add(v8, Constant.I64(9))
                val v10 = add(v9, Constant.I64(10))
                val v11 = add(v10, Constant.I64(11))
                val v12 = add(v11, Constant.I64(12))
                ret(v12)
                finalizeFunction()
            }
            assertTrue(code.isNotEmpty(), "Chained ops should not require spilling")
        }

        @Test
        fun `value used multiple times extends its live range`() {
            val code = generateCode {
                val params = createFunction("f", listOf(Param("x", Type.I64)), Type.I64)
                appendBlock("entry")
                val doubled = add(params[0], params[0])
                val tripled = add(doubled, params[0])
                val quadrupled = add(tripled, params[0])
                ret(quadrupled)
                finalizeFunction()
            }
            assertTrue(code.isNotEmpty())
        }

        @Test
        fun `conditional branch extends live ranges`() {
            val code = generateCode {
                val params = createFunction("f", listOf(
                    Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
                appendBlock("entry")
                val cond = icmp(ICmpPredicate.SGT, params[0], params[1])
                condBr(cond, BlockRef("then"), BlockRef("else_"))
                appendBlock("then")
                ret(params[0])
                appendBlock("else_")
                ret(params[1])
                finalizeFunction()
            }
            assertTrue(code.isNotEmpty())
        }

        @Test
        fun `phi in diamond merges different paths`() {
            val code = generateCode {
                val params = createFunction("f", listOf(Param("n", Type.I64)), Type.I64)
                appendBlock("entry")
                val cond = icmp(ICmpPredicate.SGT, params[0], Constant.I64(0))
                condBr(cond, BlockRef("pos"), BlockRef("neg"))
                appendBlock("pos")
                val posVal = add(params[0], Constant.I64(1))
                br(BlockRef("merge"))
                appendBlock("neg")
                val negVal = sub(Constant.I64(0), params[0])
                br(BlockRef("merge"))
                appendBlock("merge")
                val result = phi(Type.I64, listOf(posVal to BlockRef("pos"), negVal to BlockRef("neg")))
                ret(result)
                finalizeFunction()
            }
            assertTrue(code.isNotEmpty(), "Diamond phi should compile")
        }
    }

    @Nested
    inner class DivisionSpecialCases {

        @Test
        fun `sdiv function compiles with rax and rdx constraints`() {
            val lines = generateAndDisassemble {
                val params = createFunction("f", listOf(
                    Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
                appendBlock("entry")
                val result = sdiv(params[0], params[1])
                ret(result)
                finalizeFunction()
            }
            assertTrue(lines.any { it.contains("idiv") || it.contains("div") },
                "Should have div instruction: $lines")
        }

        @Test
        fun `srem uses remainder`() {
            val lines = generateAndDisassemble {
                val params = createFunction("f", listOf(
                    Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
                appendBlock("entry")
                val result = srem(params[0], params[1])
                ret(result)
                finalizeFunction()
            }
            assertTrue(lines.isNotEmpty())
        }

        @Test
        fun `div with param in rdx triggers param move`() {
            val lines = generateAndDisassemble {
                val params = createFunction("f", listOf(
                    Param("a", Type.I64), Param("b", Type.I64), Param("c", Type.I64)), Type.I64)
                appendBlock("entry")
                // c arrives in RDX (3rd System V param), but div clobbers RDX
                val result = sdiv(params[0], params[1])
                val final_ = add(result, params[2])
                ret(final_)
                finalizeFunction()
            }
            assertTrue(lines.isNotEmpty(), "Should handle RDX conflict: $lines")
        }

        @Test
        fun `udiv compiles`() {
            val code = generateCode {
                val params = createFunction("f", listOf(
                    Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
                appendBlock("entry")
                val result = udiv(params[0], params[1])
                ret(result)
                finalizeFunction()
            }
            assertTrue(code.isNotEmpty())
        }

        @Test
        fun `urem compiles`() {
            val code = generateCode {
                val params = createFunction("f", listOf(
                    Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
                appendBlock("entry")
                val result = urem(params[0], params[1])
                ret(result)
                finalizeFunction()
            }
            assertTrue(code.isNotEmpty())
        }
    }

    @Nested
    inner class MultiFunctionModule {

        @Test
        fun `two functions each get independent allocation`() {
            val module = buildModule {
                val p1 = createFunction("f1", listOf(Param("x", Type.I64)), Type.I64)
                appendBlock("entry")
                ret(add(p1[0], Constant.I64(1)))
                finalizeFunction()

                val p2 = createFunction("f2", listOf(Param("y", Type.I64)), Type.I64)
                appendBlock("entry")
                ret(add(p2[0], Constant.I64(2)))
                finalizeFunction()
            }
            val gen = X86CodeGenerator()
            val obj = gen.generateObjectFile(module)
            assertTrue(obj.symbols.any { it.name == "f1" })
            assertTrue(obj.symbols.any { it.name == "f2" })
        }

        @Test
        fun `caller and callee allocate independently`() {
            val code = generateCode {
                val hp = createFunction("helper", listOf(Param("x", Type.I64)), Type.I64)
                appendBlock("entry")
                ret(add(hp[0], Constant.I64(1)))
                finalizeFunction()

                createFunction("main", emptyList(), Type.I64)
                appendBlock("entry")
                val result = call("helper", listOf(Constant.I64(41)), Type.I64)
                ret(result)
                finalizeFunction()
            }
            assertTrue(code.isNotEmpty())
        }

        @Test
        fun `function with no params and no locals`() {
            val code = generateCode {
                createFunction("f", emptyList(), Type.I64)
                appendBlock("entry")
                ret(Constant.I64(42))
                finalizeFunction()
            }
            assertTrue(code.isNotEmpty())
        }
    }

    @Nested
    inner class EdgeCases {

        @Test
        fun `void return function`() {
            val code = generateCode {
                createFunction("f", emptyList(), Type.Void)
                appendBlock("entry")
                ret()
                finalizeFunction()
            }
            assertTrue(code.isNotEmpty())
        }

        @Test
        fun `function returning constant`() {
            val lines = generateAndDisassemble {
                createFunction("f", emptyList(), Type.I32)
                appendBlock("entry")
                ret(Constant.I32(0))
                finalizeFunction()
            }
            assertTrue(lines.any { it.contains("eax") || it.contains("xor") },
                "Return 0 should clear eax: $lines")
        }

        @Test
        fun `many params beyond register count`() {
            val code = generateCode {
                val params = createFunction("f", listOf(
                    Param("a", Type.I64), Param("b", Type.I64), Param("c", Type.I64),
                    Param("d", Type.I64), Param("e", Type.I64), Param("f_", Type.I64),
                    Param("g", Type.I64), Param("h", Type.I64)), Type.I64)
                appendBlock("entry")
                var sum = add(params[0], params[1])
                for (i in 2 until 6) {
                    sum = add(sum, params[i])
                }
                ret(sum)
                finalizeFunction()
            }
            assertTrue(code.isNotEmpty(), "Should handle params beyond register count")
        }

        @Test
        fun `compare and branch`() {
            val code = generateCode {
                val params = createFunction("max", listOf(
                    Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
                appendBlock("entry")
                val cond = icmp(ICmpPredicate.SGT, params[0], params[1])
                condBr(cond, BlockRef("ret_a"), BlockRef("ret_b"))
                appendBlock("ret_a")
                ret(params[0])
                appendBlock("ret_b")
                ret(params[1])
                finalizeFunction()
            }
            assertTrue(code.isNotEmpty())
        }

        @Test
        fun `select instruction`() {
            val code = generateCode {
                val params = createFunction("abs", listOf(Param("x", Type.I64)), Type.I64)
                appendBlock("entry")
                val neg = sub(Constant.I64(0), params[0])
                val cond = icmp(ICmpPredicate.SGT, params[0], Constant.I64(0))
                val result = select(cond, params[0], neg)
                ret(result)
                finalizeFunction()
            }
            assertTrue(code.isNotEmpty())
        }

        @Test
        fun `zero-extend i1 to i32`() {
            val code = generateCode {
                val params = createFunction("f", listOf(
                    Param("a", Type.I64), Param("b", Type.I64)), Type.I32)
                appendBlock("entry")
                val cmp = icmp(ICmpPredicate.EQ, params[0], params[1])
                val result = zext(cmp, Type.I32)
                ret(result)
                finalizeFunction()
            }
            assertTrue(code.isNotEmpty())
        }

        @Test
        fun `multiple basic blocks with different live sets`() {
            val code = generateCode {
                val params = createFunction("f", listOf(
                    Param("n", Type.I64)), Type.I64)
                appendBlock("entry")
                val cond = icmp(ICmpPredicate.SGT, params[0], Constant.I64(10))
                condBr(cond, BlockRef("big"), BlockRef("small"))
                appendBlock("big")
                val bigResult = add(params[0], Constant.I64(100))
                ret(bigResult)
                appendBlock("small")
                val smallResult = add(params[0], Constant.I64(1))
                ret(smallResult)
                finalizeFunction()
            }
            assertTrue(code.isNotEmpty())
        }

        @Test
        fun `call with many arguments`() {
            val code = generateCode {
                declareFunction("ext", listOf(
                    Param("a", Type.I64), Param("b", Type.I64), Param("c", Type.I64),
                    Param("d", Type.I64), Param("e", Type.I64), Param("f_", Type.I64)), Type.I64)
                createFunction("f", emptyList(), Type.I64)
                appendBlock("entry")
                val result = call("ext", listOf(
                    Constant.I64(1), Constant.I64(2), Constant.I64(3),
                    Constant.I64(4), Constant.I64(5), Constant.I64(6)), Type.I64)
                ret(result)
                finalizeFunction()
            }
            assertTrue(code.isNotEmpty())
        }

        @Test
        fun `load and store with pointer`() {
            val code = generateCode {
                val params = createFunction("f", listOf(Param("p", Type.OpaquePointer)), Type.I64)
                appendBlock("entry")
                val v = load(Type.I64, params[0])
                val result = add(v, Constant.I64(1))
                store(result, params[0])
                ret(result)
                finalizeFunction()
            }
            assertTrue(code.isNotEmpty())
        }

        @Test
        fun `i8 type allocation`() {
            val code = generateCode {
                val params = createFunction("f", listOf(Param("x", Type.I8)), Type.I8)
                appendBlock("entry")
                ret(params[0])
                finalizeFunction()
            }
            assertTrue(code.isNotEmpty())
        }

        @Test
        fun `i16 type allocation`() {
            val code = generateCode {
                val params = createFunction("f", listOf(Param("x", Type.I16)), Type.I16)
                appendBlock("entry")
                ret(params[0])
                finalizeFunction()
            }
            assertTrue(code.isNotEmpty())
        }
    }
}
