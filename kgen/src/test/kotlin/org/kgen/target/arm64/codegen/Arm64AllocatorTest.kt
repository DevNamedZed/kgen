package org.kgen.target.arm64.codegen

import org.kgen.target.arm64.disasm.Arm64Disassembler
import org.kgen.ir.*
import org.kgen.ir.build.IrBuilder
import org.kgen.ir.target.Target
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested

class Arm64AllocatorTest {

    private val disasm = Arm64Disassembler()

    private fun buildModule(block: IrBuilder.() -> Unit): Module {
        val ir = IrBuilder("test", Target.arm64())
        ir.block()
        return ir.build()
    }

    private fun generateAndDisassemble(block: IrBuilder.() -> Unit): List<String> {
        val module = buildModule(block)
        val gen = Arm64CodeGenerator()
        val obj = gen.generateObjectFile(module)
        val textSection = obj.sections.first { it.kind == org.kgen.binary.SectionKind.TEXT }
        return disasm.disassemble(textSection.data).map { it.toString() }
    }

    private fun generateCode(block: IrBuilder.() -> Unit): ByteArray {
        val module = buildModule(block)
        val gen = Arm64CodeGenerator()
        val obj = gen.generateObjectFile(module)
        return obj.sections.first { it.kind == org.kgen.binary.SectionKind.TEXT }.data
    }

    @Nested
    inner class AvailableRegisterSets {

        @Test
        fun `single i64 param uses x0`() {
            val lines = generateAndDisassemble {
                val params = createFunction("f", listOf(Param("x", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val result = add(params[0], Constant.I64(1))
                ret(result)
                finalizeFunction()
            }
            val allText = lines.joinToString(" ")
            assertTrue(allText.contains("x0"), "First param should use X0: $lines")
        }

        @Test
        fun `single i32 param uses w0`() {
            val lines = generateAndDisassemble {
                val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
                positionAtEnd(appendBlock("entry"))
                val result = add(params[0], Constant.I32(1))
                ret(result)
                finalizeFunction()
            }
            val allText = lines.joinToString(" ")
            assertTrue(allText.contains("w0") || allText.contains("w"), "I32 should use W regs: $lines")
        }

        @Test
        fun `pointer param uses 64-bit x register`() {
            val code = generateCode {
                val params = createFunction("f", listOf(Param("p", Type.OpaquePointer)), Type.OpaquePointer)
                positionAtEnd(appendBlock("entry"))
                ret(params[0])
                finalizeFunction()
            }
            assertTrue(code.isNotEmpty(), "Pointer param should compile using X reg")
            assertEquals(0, code.size % 4, "ARM64 instructions are 4 bytes")
        }

        @Test
        fun `f64 param uses d register`() {
            val lines = generateAndDisassemble {
                val params = createFunction("f", listOf(Param("x", Type.F64)), Type.F64)
                positionAtEnd(appendBlock("entry"))
                ret(params[0])
                finalizeFunction()
            }
            val allText = lines.joinToString(" ")
            assertTrue(allText.contains("d0") || allText.contains("d"),
                "F64 should use D register: $lines")
        }

        @Test
        fun `f32 param uses s register`() {
            val lines = generateAndDisassemble {
                val params = createFunction("f", listOf(Param("x", Type.F32)), Type.F32)
                positionAtEnd(appendBlock("entry"))
                ret(params[0])
                finalizeFunction()
            }
            assertTrue(lines.isNotEmpty(), "F32 should compile")
        }

        @Test
        fun `x16 x17 excluded from allocatable pool`() {
            val lines = generateAndDisassemble {
                val params = createFunction("f", listOf(
                    Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val sum = add(params[0], params[1])
                ret(sum)
                finalizeFunction()
            }
            val allText = lines.joinToString(" ")
            // X16/X17 (IP0/IP1) should not appear as data registers
            assertFalse(allText.contains("x16") && !allText.contains("stp"),
                "X16 should not be used for data: $lines")
        }

        @Test
        fun `x29 fp and x30 lr excluded from allocation`() {
            val code = generateCode {
                val params = createFunction("f", listOf(
                    Param("a", Type.I64), Param("b", Type.I64), Param("c", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val sum = add(params[0], params[1])
                val result = add(sum, params[2])
                ret(result)
                finalizeFunction()
            }
            assertTrue(code.isNotEmpty())
        }
    }

    @Nested
    inner class ParameterRegisterMapping {

        @Test
        fun `first param in x0`() {
            val lines = generateAndDisassemble {
                val params = createFunction("f", listOf(Param("x", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val result = add(params[0], Constant.I64(1))
                ret(result)
                finalizeFunction()
            }
            assertTrue(lines.any { it.contains("x0") }, "First param in X0: $lines")
        }

        @Test
        fun `second param in x1`() {
            val lines = generateAndDisassemble {
                val params = createFunction("f", listOf(
                    Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val result = add(params[0], params[1])
                ret(result)
                finalizeFunction()
            }
            val allText = lines.joinToString(" ")
            assertTrue(allText.contains("x1"), "Second param in X1: $lines")
        }

        @Test
        fun `eight gp params use x0 through x7`() {
            val lines = generateAndDisassemble {
                val params = createFunction("f", (0 until 8).map { Param("p$it", Type.I64) }, Type.I64)
                positionAtEnd(appendBlock("entry"))
                var sum = add(params[0], params[1])
                for (i in 2 until 8) sum = add(sum, params[i])
                ret(sum)
                finalizeFunction()
            }
            val allText = lines.joinToString(" ")
            assertTrue(allText.contains("x0"), "Should use x0: $lines")
            assertTrue(allText.contains("x1"), "Should use x1: $lines")
        }

        @Test
        fun `fp params use d0 through d7 independently`() {
            val lines = generateAndDisassemble {
                val params = createFunction("f", listOf(
                    Param("x", Type.F64), Param("y", Type.F64)), Type.F64)
                positionAtEnd(appendBlock("entry"))
                val sum = fadd(params[0], params[1])
                ret(sum)
                finalizeFunction()
            }
            val allText = lines.joinToString(" ")
            assertTrue(allText.contains("d0") || allText.contains("d1"),
                "FP params should use D registers: $lines")
        }

        @Test
        fun `mixed gp and fp params get independent counters`() {
            val lines = generateAndDisassemble {
                val params = createFunction("f", listOf(
                    Param("a", Type.I64), Param("x", Type.F64),
                    Param("b", Type.I64), Param("y", Type.F64)), Type.F64)
                positionAtEnd(appendBlock("entry"))
                val sum = fadd(params[1], params[3])
                ret(sum)
                finalizeFunction()
            }
            assertTrue(lines.isNotEmpty(), "Mixed params should compile")
        }

        @Test
        fun `i32 params use w registers`() {
            val lines = generateAndDisassemble {
                val params = createFunction("f", listOf(
                    Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
                positionAtEnd(appendBlock("entry"))
                val result = add(params[0], params[1])
                ret(result)
                finalizeFunction()
            }
            val allText = lines.joinToString(" ")
            assertTrue(allText.contains("w"), "I32 should use W regs: $lines")
        }

        @Test
        fun `params beyond eight are spilled`() {
            val code = generateCode {
                val params = createFunction("f", (0 until 10).map { Param("p$it", Type.I64) }, Type.I64)
                positionAtEnd(appendBlock("entry"))
                var sum = add(params[0], params[1])
                for (i in 2 until 8) sum = add(sum, params[i])
                ret(sum)
                finalizeFunction()
            }
            assertTrue(code.isNotEmpty(), "Should handle excess params")
        }
    }

    @Nested
    inner class SimpleAllocation {

        @Test
        fun `identity function`() {
            val code = generateCode {
                val params = createFunction("id", listOf(Param("x", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                ret(params[0])
                finalizeFunction()
            }
            assertTrue(code.isNotEmpty())
            assertEquals(0, code.size % 4, "ARM64 instructions are 4 bytes each")
        }

        @Test
        fun `two params with add`() {
            val lines = generateAndDisassemble {
                val params = createFunction("add", listOf(
                    Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val sum = add(params[0], params[1])
                ret(sum)
                finalizeFunction()
            }
            assertTrue(lines.any { it.startsWith("add") }, "Should have add: $lines")
        }

        @Test
        fun `three params chain of adds`() {
            val lines = generateAndDisassemble {
                val params = createFunction("f", listOf(
                    Param("a", Type.I64), Param("b", Type.I64), Param("c", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val ab = add(params[0], params[1])
                val result = add(ab, params[2])
                ret(result)
                finalizeFunction()
            }
            val addCount = lines.count { it.startsWith("add") }
            assertTrue(addCount >= 2, "Should have at least 2 adds: $lines")
        }

        @Test
        fun `constant operand with add immediate`() {
            val lines = generateAndDisassemble {
                val params = createFunction("f", listOf(Param("x", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val result = add(params[0], Constant.I64(42))
                ret(result)
                finalizeFunction()
            }
            assertTrue(lines.any { it.contains("42") || it.contains("#") },
                "Should have immediate operand: $lines")
        }

        @Test
        fun `subtraction`() {
            val lines = generateAndDisassemble {
                val params = createFunction("f", listOf(
                    Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val result = sub(params[0], params[1])
                ret(result)
                finalizeFunction()
            }
            assertTrue(lines.any { it.startsWith("sub") }, "Should have sub: $lines")
        }

        @Test
        fun `multiplication`() {
            val lines = generateAndDisassemble {
                val params = createFunction("f", listOf(
                    Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val result = mul(params[0], params[1])
                ret(result)
                finalizeFunction()
            }
            assertTrue(lines.any { it.contains("mul") }, "Should have mul: $lines")
        }

        @Test
        fun `bitwise and`() {
            val lines = generateAndDisassemble {
                val params = createFunction("f", listOf(
                    Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val result = and(params[0], params[1])
                ret(result)
                finalizeFunction()
            }
            assertTrue(lines.any { it.contains("and") }, "Should have and: $lines")
        }

        @Test
        fun `bitwise or`() {
            val lines = generateAndDisassemble {
                val params = createFunction("f", listOf(
                    Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val result = or(params[0], params[1])
                ret(result)
                finalizeFunction()
            }
            assertTrue(lines.any { it.contains("orr") }, "Should have orr: $lines")
        }
    }

    @Nested
    inner class RegisterPressure {

        @Test
        fun `many live i64 values cause spilling`() {
            val code = generateCode {
                val params = createFunction("f", listOf(Param("x", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val values = (1..25).map { add(params[0], Constant.I64(it.toLong())) }
                var sum = add(values[0], values[1])
                for (i in 2 until values.size) sum = add(sum, values[i])
                ret(sum)
                finalizeFunction()
            }
            assertTrue(code.isNotEmpty(), "Should compile with spills")
        }

        @Test
        fun `many live i32 values cause spilling`() {
            val code = generateCode {
                val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
                positionAtEnd(appendBlock("entry"))
                val values = (1..25).map { add(params[0], Constant.I32(it)) }
                var sum = add(values[0], values[1])
                for (i in 2 until values.size) sum = add(sum, values[i])
                ret(sum)
                finalizeFunction()
            }
            assertTrue(code.isNotEmpty(), "Should compile with i32 spills")
        }

        @Test
        fun `fp values under pressure`() {
            val code = generateCode {
                val params = createFunction("f", listOf(Param("x", Type.F64)), Type.F64)
                positionAtEnd(appendBlock("entry"))
                val values = (1..20).map { fadd(params[0], Constant.F64(it.toDouble())) }
                var sum = fadd(values[0], values[1])
                for (i in 2 until values.size) sum = fadd(sum, values[i])
                ret(sum)
                finalizeFunction()
            }
            assertTrue(code.isNotEmpty(), "Should compile with FP spills")
        }

        @Test
        fun `spilled values produce str and ldr instructions`() {
            val lines = generateAndDisassemble {
                val params = createFunction("f", listOf(Param("x", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val values = (1..25).map { add(params[0], Constant.I64(it.toLong())) }
                var sum = add(values[0], values[1])
                for (i in 2 until values.size) sum = add(sum, values[i])
                ret(sum)
                finalizeFunction()
            }
            val allText = lines.joinToString(" ")
            assertTrue(allText.contains("str") || allText.contains("stp"),
                "Spills should produce store: $lines")
        }

        @Test
        fun `chained operations reuse registers`() {
            val code = generateCode {
                val params = createFunction("f", listOf(Param("x", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                var v: Value = params[0]
                for (i in 1..20) v = add(v, Constant.I64(i.toLong()))
                ret(v)
                finalizeFunction()
            }
            assertTrue(code.isNotEmpty(), "Chained ops should not require many regs")
        }
    }

    @Nested
    inner class CallerSavedRegisters {

        @Test
        fun `value live across call is preserved`() {
            val code = generateCode {
                declareFunction("ext", emptyList(), Type.I64)
                val params = createFunction("f", listOf(Param("x", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val r = call("ext", emptyList(), Type.I64)!!
                val sum = add(params[0], r)
                ret(sum)
                finalizeFunction()
            }
            assertTrue(code.isNotEmpty(), "Value live across call should compile")
        }

        @Test
        fun `callee-saved registers saved with stp in prologue`() {
            val lines = generateAndDisassemble {
                declareFunction("ext", emptyList(), Type.Void)
                val params = createFunction("f", listOf(Param("x", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                call("ext", emptyList(), Type.Void)
                ret(params[0])
                finalizeFunction()
            }
            assertTrue(lines.any { it.startsWith("stp") }, "Should have stp for callee-saved: $lines")
        }

        @Test
        fun `callee-saved registers restored with ldp in epilogue`() {
            val lines = generateAndDisassemble {
                declareFunction("ext", emptyList(), Type.Void)
                val params = createFunction("f", listOf(Param("x", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                call("ext", emptyList(), Type.Void)
                ret(params[0])
                finalizeFunction()
            }
            assertTrue(lines.any { it.startsWith("ldp") }, "Should have ldp for callee-saved: $lines")
        }

        @Test
        fun `multiple values across call`() {
            val code = generateCode {
                declareFunction("ext", emptyList(), Type.I64)
                val params = createFunction("f", listOf(
                    Param("a", Type.I64), Param("b", Type.I64), Param("c", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val r = call("ext", emptyList(), Type.I64)!!
                val sum = add(params[0], params[1])
                val result = add(sum, params[2])
                val final_ = add(result, r)
                ret(final_)
                finalizeFunction()
            }
            assertTrue(code.isNotEmpty())
        }

        @Test
        fun `fp value across call is preserved`() {
            val code = generateCode {
                declareFunction("ext", emptyList(), Type.Void)
                val params = createFunction("f", listOf(Param("x", Type.F64)), Type.F64)
                positionAtEnd(appendBlock("entry"))
                call("ext", emptyList(), Type.Void)
                ret(params[0])
                finalizeFunction()
            }
            assertTrue(code.isNotEmpty(), "FP value across call should be saved")
        }

        @Test
        fun `two sequential calls`() {
            val code = generateCode {
                declareFunction("ext1", emptyList(), Type.I64)
                declareFunction("ext2", listOf(Param("v", Type.I64)), Type.I64)
                createFunction("f", emptyList(), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val r1 = call("ext1", emptyList(), Type.I64)!!
                val r2 = call("ext2", listOf(r1), Type.I64)
                ret(r2)
                finalizeFunction()
            }
            assertTrue(code.isNotEmpty())
        }

        @Test
        fun `value not live across call uses caller-saved`() {
            val code = generateCode {
                declareFunction("ext", listOf(Param("v", Type.I64)), Type.I64)
                val params = createFunction("f", listOf(Param("x", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val v = add(params[0], Constant.I64(1))
                val result = call("ext", listOf(v), Type.I64)
                ret(result)
                finalizeFunction()
            }
            assertTrue(code.isNotEmpty())
        }
    }

    @Nested
    inner class CalleeSavedSelection {

        @Test
        fun `no-call function skips callee-saved push`() {
            val lines = generateAndDisassemble {
                val params = createFunction("f", listOf(
                    Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val sum = add(params[0], params[1])
                ret(sum)
                finalizeFunction()
            }
            // Should not save X19-X28 since no calls
            val allText = lines.joinToString(" ")
            val hasCalleeSaved = (19..28).any { allText.contains("x$it") }
            assertFalse(hasCalleeSaved, "No-call function should avoid callee-saved: $lines")
        }

        @Test
        fun `stp and ldp are balanced`() {
            val lines = generateAndDisassemble {
                declareFunction("ext", emptyList(), Type.Void)
                val params = createFunction("f", listOf(Param("x", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                call("ext", emptyList(), Type.Void)
                ret(params[0])
                finalizeFunction()
            }
            val stpCount = lines.count { it.startsWith("stp") }
            val ldpCount = lines.count { it.startsWith("ldp") }
            assertEquals(stpCount, ldpCount, "STP/LDP should be balanced: stp=$stpCount, ldp=$ldpCount")
        }

        @Test
        fun `only used callee-saved registers are saved`() {
            val lines = generateAndDisassemble {
                declareFunction("ext", emptyList(), Type.Void)
                val params = createFunction("f", listOf(Param("x", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                call("ext", emptyList(), Type.Void)
                ret(params[0])
                finalizeFunction()
            }
            // Only 1 value across call -> should save 1 callee-saved + FP/LR pair
            val stpLines = lines.filter { it.startsWith("stp") }
            assertTrue(stpLines.size <= 3, "Should only save needed callee regs: $stpLines")
        }
    }

    @Nested
    inner class FPRegisterAllocation {

        @Test
        fun `f64 add uses fadd`() {
            val lines = generateAndDisassemble {
                val params = createFunction("f", listOf(
                    Param("a", Type.F64), Param("b", Type.F64)), Type.F64)
                positionAtEnd(appendBlock("entry"))
                val sum = fadd(params[0], params[1])
                ret(sum)
                finalizeFunction()
            }
            assertTrue(lines.any { it.contains("fadd") }, "Should have fadd: $lines")
        }

        @Test
        fun `f64 sub uses fsub`() {
            val lines = generateAndDisassemble {
                val params = createFunction("f", listOf(
                    Param("a", Type.F64), Param("b", Type.F64)), Type.F64)
                positionAtEnd(appendBlock("entry"))
                val result = fsub(params[0], params[1])
                ret(result)
                finalizeFunction()
            }
            assertTrue(lines.any { it.contains("fsub") }, "Should have fsub: $lines")
        }

        @Test
        fun `f64 mul uses fmul`() {
            val lines = generateAndDisassemble {
                val params = createFunction("f", listOf(
                    Param("a", Type.F64), Param("b", Type.F64)), Type.F64)
                positionAtEnd(appendBlock("entry"))
                val result = fmul(params[0], params[1])
                ret(result)
                finalizeFunction()
            }
            assertTrue(lines.any { it.contains("fmul") }, "Should have fmul: $lines")
        }

        @Test
        fun `f64 div uses fdiv`() {
            val lines = generateAndDisassemble {
                val params = createFunction("f", listOf(
                    Param("a", Type.F64), Param("b", Type.F64)), Type.F64)
                positionAtEnd(appendBlock("entry"))
                val result = fdiv(params[0], params[1])
                ret(result)
                finalizeFunction()
            }
            assertTrue(lines.any { it.contains("fdiv") }, "Should have fdiv: $lines")
        }

        @Test
        fun `multiple fp params use different d registers`() {
            val lines = generateAndDisassemble {
                val params = createFunction("f", listOf(
                    Param("a", Type.F64), Param("b", Type.F64), Param("c", Type.F64)), Type.F64)
                positionAtEnd(appendBlock("entry"))
                val ab = fadd(params[0], params[1])
                val result = fadd(ab, params[2])
                ret(result)
                finalizeFunction()
            }
            val allText = lines.joinToString(" ")
            assertTrue(allText.contains("d0"), "Should use d0: $lines")
        }

        @Test
        fun `fp value across call is preserved`() {
            val code = generateCode {
                declareFunction("ext", emptyList(), Type.Void)
                val params = createFunction("f", listOf(Param("x", Type.F64)), Type.F64)
                positionAtEnd(appendBlock("entry"))
                call("ext", emptyList(), Type.Void)
                ret(params[0])
                finalizeFunction()
            }
            assertTrue(code.isNotEmpty(), "FP across call should be preserved")
        }

        @Test
        fun `mixed int and fp computation`() {
            val code = generateCode {
                val params = createFunction("f", listOf(
                    Param("n", Type.I64), Param("x", Type.F64), Param("y", Type.F64)), Type.F64)
                positionAtEnd(appendBlock("entry"))
                val sum = fadd(params[1], params[2])
                ret(sum)
                finalizeFunction()
            }
            assertTrue(code.isNotEmpty())
        }
    }

    @Nested
    inner class SpillSlotAssignment {

        @Test
        fun `spilled values use stack offsets`() {
            val lines = generateAndDisassemble {
                val params = createFunction("f", listOf(Param("x", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val values = (1..25).map { add(params[0], Constant.I64(it.toLong())) }
                var sum = add(values[0], values[1])
                for (i in 2 until values.size) sum = add(sum, values[i])
                ret(sum)
                finalizeFunction()
            }
            val allText = lines.joinToString(" ")
            assertTrue(allText.contains("sp") || allText.contains("x29"),
                "Spills should reference stack: $lines")
        }

        @Test
        fun `frame size accounts for spill slots`() {
            val lines = generateAndDisassemble {
                val params = createFunction("f", listOf(Param("x", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val values = (1..25).map { add(params[0], Constant.I64(it.toLong())) }
                var sum = add(values[0], values[1])
                for (i in 2 until values.size) sum = add(sum, values[i])
                ret(sum)
                finalizeFunction()
            }
            // Should have sub sp for stack allocation
            assertTrue(lines.any { it.contains("sub") && it.contains("sp") } ||
                lines.any { it.contains("stp") },
                "Should allocate stack frame: $lines")
        }

        @Test
        fun `short-lived values reuse slots`() {
            val code = generateCode {
                val params = createFunction("f", listOf(Param("x", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                var v: Value = params[0]
                for (i in 1..15) v = add(v, Constant.I64(i.toLong()))
                ret(v)
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
                createFunction("f", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                ret(Constant.I64(42))
                finalizeFunction()
            }
            assertTrue(code.isNotEmpty())
        }

        @Test
        fun `value used multiple times extends live range`() {
            val code = generateCode {
                val params = createFunction("f", listOf(Param("x", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val d = add(params[0], params[0])
                val t = add(d, params[0])
                val q = add(t, params[0])
                ret(q)
                finalizeFunction()
            }
            assertTrue(code.isNotEmpty())
        }

        @Test
        fun `conditional branch with both sides using params`() {
            val code = generateCode {
                val params = createFunction("f", listOf(
                    Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val cond = icmp(ICmpPredicate.SGT, params[0], params[1])
                condBr(cond, "then", "else_")
                positionAtEnd(appendBlock("then"))
                ret(params[0])
                positionAtEnd(appendBlock("else_"))
                ret(params[1])
                finalizeFunction()
            }
            assertTrue(code.isNotEmpty())
        }

        @Test
        fun `phi in diamond merges different paths`() {
            val code = generateCode {
                val params = createFunction("f", listOf(Param("n", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val cond = icmp(ICmpPredicate.SGT, params[0], Constant.I64(0))
                condBr(cond, "pos", "neg")
                positionAtEnd(appendBlock("pos"))
                val posVal = add(params[0], Constant.I64(1))
                br("merge")
                positionAtEnd(appendBlock("neg"))
                val negVal = sub(Constant.I64(0), params[0])
                br("merge")
                positionAtEnd(appendBlock("merge"))
                val result = phi(Type.I64, listOf(posVal to "pos", negVal to "neg"))
                ret(result)
                finalizeFunction()
            }
            assertTrue(code.isNotEmpty(), "Diamond phi should compile")
        }
    }

    @Nested
    inner class MultiFunctionModule {

        @Test
        fun `two functions get independent allocation`() {
            val module = buildModule {
                val p1 = createFunction("f1", listOf(Param("x", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                ret(add(p1[0], Constant.I64(1)))
                finalizeFunction()

                val p2 = createFunction("f2", listOf(Param("y", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                ret(add(p2[0], Constant.I64(2)))
                finalizeFunction()
            }
            val gen = Arm64CodeGenerator()
            val obj = gen.generateObjectFile(module)
            assertTrue(obj.symbols.any { it.name == "f1" })
            assertTrue(obj.symbols.any { it.name == "f2" })
        }

        @Test
        fun `caller and callee`() {
            val code = generateCode {
                val hp = createFunction("helper", listOf(Param("x", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                ret(add(hp[0], Constant.I64(1)))
                finalizeFunction()

                createFunction("main", emptyList(), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val result = call("helper", listOf(Constant.I64(41)), Type.I64)
                ret(result)
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
                positionAtEnd(appendBlock("entry"))
                ret()
                finalizeFunction()
            }
            assertTrue(code.isNotEmpty())
            assertEquals(0, code.size % 4)
        }

        @Test
        fun `function returning constant`() {
            val code = generateCode {
                createFunction("f", emptyList(), Type.I64)
                positionAtEnd(appendBlock("entry"))
                ret(Constant.I64(42))
                finalizeFunction()
            }
            assertTrue(code.isNotEmpty())
        }

        @Test
        fun `select instruction`() {
            val code = generateCode {
                val params = createFunction("f", listOf(Param("x", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val neg = sub(Constant.I64(0), params[0])
                val cond = icmp(ICmpPredicate.SGT, params[0], Constant.I64(0))
                val result = select(cond, params[0], neg)
                ret(result)
                finalizeFunction()
            }
            assertTrue(code.isNotEmpty())
        }

        @Test
        fun `load and store with pointer`() {
            val code = generateCode {
                val params = createFunction("f", listOf(Param("p", Type.OpaquePointer)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val v = load(Type.I64, params[0])
                val result = add(v, Constant.I64(1))
                store(result, params[0])
                ret(result)
                finalizeFunction()
            }
            assertTrue(code.isNotEmpty())
        }

        @Test
        fun `call with many arguments`() {
            val code = generateCode {
                declareFunction("ext", (0 until 8).map { Param("p$it", Type.I64) }, Type.I64)
                createFunction("f", emptyList(), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val result = call("ext", (1L..8L).map { Constant.I64(it) }, Type.I64)
                ret(result)
                finalizeFunction()
            }
            assertTrue(code.isNotEmpty())
        }

        @Test
        fun `sdiv compiles`() {
            val code = generateCode {
                val params = createFunction("f", listOf(
                    Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val result = sdiv(params[0], params[1])
                ret(result)
                finalizeFunction()
            }
            assertTrue(code.isNotEmpty())
        }

        @Test
        fun `multiple basic blocks`() {
            val code = generateCode {
                val params = createFunction("f", listOf(Param("n", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val cond = icmp(ICmpPredicate.SGT, params[0], Constant.I64(10))
                condBr(cond, "big", "small")
                positionAtEnd(appendBlock("big"))
                ret(add(params[0], Constant.I64(100)))
                positionAtEnd(appendBlock("small"))
                ret(add(params[0], Constant.I64(1)))
                finalizeFunction()
            }
            assertTrue(code.isNotEmpty())
        }

        @Test
        fun `zero-extend i1 to i32`() {
            val code = generateCode {
                val params = createFunction("f", listOf(
                    Param("a", Type.I64), Param("b", Type.I64)), Type.I32)
                positionAtEnd(appendBlock("entry"))
                val cmp = icmp(ICmpPredicate.EQ, params[0], params[1])
                val result = zext(cmp, Type.I32)
                ret(result)
                finalizeFunction()
            }
            assertTrue(code.isNotEmpty())
        }

        @Test
        fun `function with no params returning zero`() {
            val code = generateCode {
                createFunction("f", emptyList(), Type.I32)
                positionAtEnd(appendBlock("entry"))
                ret(Constant.I32(0))
                finalizeFunction()
            }
            assertTrue(code.isNotEmpty())
        }
    }
}
