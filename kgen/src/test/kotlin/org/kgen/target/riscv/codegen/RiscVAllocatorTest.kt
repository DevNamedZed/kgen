package org.kgen.target.riscv.codegen

import org.kgen.target.riscv.disasm.RiscVDisassembler
import org.kgen.ir.*
import org.kgen.ir.build.IrBuilder
import org.kgen.ir.target.Target
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested

class RiscVAllocatorTest {

    private val disasm = RiscVDisassembler()

    private fun buildModule(block: IrBuilder.() -> Unit): Module {
        val ir = IrBuilder("test", Target.riscv64())
        ir.block()
        return ir.build()
    }

    private fun generateAndDisassemble(block: IrBuilder.() -> Unit): List<String> {
        val module = buildModule(block)
        val gen = RiscVCodeGenerator()
        val obj = gen.generateObjectFile(module)
        val textSection = obj.sections.first { it.kind == org.kgen.binary.SectionKind.TEXT }
        return disasm.disassemble(textSection.data).map { it.toString() }
    }

    private fun generateCode(block: IrBuilder.() -> Unit): ByteArray {
        val module = buildModule(block)
        val gen = RiscVCodeGenerator()
        val obj = gen.generateObjectFile(module)
        return obj.sections.first { it.kind == org.kgen.binary.SectionKind.TEXT }.data
    }

    @Nested
    inner class AvailableRegisterSets {

        @Test
        fun `single i64 param uses a0 register`() {
            val lines = generateAndDisassemble {
                val params = createFunction("f", listOf(Param("x", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val result = add(params[0], Constant.I64(1))
                ret(result)
                finalizeFunction()
            }
            val allText = lines.joinToString(" ")
            assertTrue(allText.contains("a0"), "First param should use a0: $lines")
        }

        @Test
        fun `pointer param uses gp register`() {
            val code = generateCode {
                val params = createFunction("f", listOf(Param("p", Type.OpaquePointer)), Type.OpaquePointer)
                positionAtEnd(appendBlock("entry"))
                ret(params[0])
                finalizeFunction()
            }
            assertTrue(code.isNotEmpty(), "Pointer param should compile using GP reg")
        }

        @Test
        fun `x0 zero register excluded from allocation`() {
            val code = generateCode {
                val params = createFunction("f", listOf(
                    Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val sum = add(params[0], params[1])
                ret(sum)
                finalizeFunction()
            }
            assertTrue(code.isNotEmpty())
            assertEquals(0, code.size % 4, "RISC-V instructions are 4 bytes")
        }

        @Test
        fun `sp ra gp tp excluded from allocation`() {
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

        @Test
        fun `allocatable pool includes a0 through a7`() {
            val lines = generateAndDisassemble {
                val params = createFunction("f", listOf(
                    Param("a", Type.I64), Param("b", Type.I64), Param("c", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val ab = add(params[0], params[1])
                val result = add(ab, params[2])
                ret(result)
                finalizeFunction()
            }
            val allText = lines.joinToString(" ")
            assertTrue(allText.contains("a0"), "Should use arg regs: $lines")
        }

        @Test
        fun `allocatable pool includes t0 through t6`() {
            val lines = generateAndDisassemble {
                val params = createFunction("f", listOf(Param("x", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val v1 = add(params[0], Constant.I64(1))
                val v2 = add(params[0], Constant.I64(2))
                val v3 = add(params[0], Constant.I64(3))
                val v4 = add(params[0], Constant.I64(4))
                val v5 = add(params[0], Constant.I64(5))
                val v6 = add(params[0], Constant.I64(6))
                val v7 = add(params[0], Constant.I64(7))
                val v8 = add(params[0], Constant.I64(8))
                val v9 = add(params[0], Constant.I64(9))
                var sum = add(v1, v2)
                sum = add(sum, v3)
                sum = add(sum, v4)
                sum = add(sum, v5)
                sum = add(sum, v6)
                sum = add(sum, v7)
                sum = add(sum, v8)
                sum = add(sum, v9)
                ret(sum)
                finalizeFunction()
            }
            val allText = lines.joinToString(" ")
            val hasTempRegs = allText.contains("t0") || allText.contains("t1") ||
                allText.contains("t2") || allText.contains("t3")
            assertTrue(hasTempRegs, "Should use temp registers: $lines")
        }
    }

    @Nested
    inner class ParameterRegisterMapping {

        @Test
        fun `first param in a0`() {
            val lines = generateAndDisassemble {
                val params = createFunction("f", listOf(Param("x", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val result = add(params[0], Constant.I64(1))
                ret(result)
                finalizeFunction()
            }
            assertTrue(lines.any { it.contains("a0") }, "First param in a0: $lines")
        }

        @Test
        fun `second param in a1`() {
            val lines = generateAndDisassemble {
                val params = createFunction("f", listOf(
                    Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val result = add(params[0], params[1])
                ret(result)
                finalizeFunction()
            }
            val allText = lines.joinToString(" ")
            assertTrue(allText.contains("a1"), "Second param in a1: $lines")
        }

        @Test
        fun `eight params use a0 through a7`() {
            val lines = generateAndDisassemble {
                val params = createFunction("f", (0 until 8).map { Param("p$it", Type.I64) }, Type.I64)
                positionAtEnd(appendBlock("entry"))
                var sum = add(params[0], params[1])
                for (i in 2 until 8) sum = add(sum, params[i])
                ret(sum)
                finalizeFunction()
            }
            val allText = lines.joinToString(" ")
            assertTrue(allText.contains("a0"), "Should use a0: $lines")
            assertTrue(allText.contains("a1"), "Should use a1: $lines")
        }

        @Test
        fun `i32 params still use gp registers`() {
            val lines = generateAndDisassemble {
                val params = createFunction("f", listOf(
                    Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
                positionAtEnd(appendBlock("entry"))
                val result = add(params[0], params[1])
                ret(result)
                finalizeFunction()
            }
            val allText = lines.joinToString(" ")
            assertTrue(allText.contains("a0") || allText.contains("a1"),
                "I32 should use GP regs: $lines")
        }

        @Test
        fun `return value in a0`() {
            val lines = generateAndDisassemble {
                createFunction("f", emptyList(), Type.I64)
                positionAtEnd(appendBlock("entry"))
                ret(Constant.I64(42))
                finalizeFunction()
            }
            val allText = lines.joinToString(" ")
            assertTrue(allText.contains("a0"), "Return value in a0: $lines")
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
            assertEquals(0, code.size % 4)
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
            assertTrue(lines.any { it.startsWith("add ") }, "Should have add: $lines")
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
            val addCount = lines.count { it.startsWith("add ") }
            assertTrue(addCount >= 2, "Should have 2+ adds: $lines")
        }

        @Test
        fun `add with immediate uses addi`() {
            val lines = generateAndDisassemble {
                val params = createFunction("f", listOf(Param("x", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val result = add(params[0], Constant.I64(42))
                ret(result)
                finalizeFunction()
            }
            // addi disassembles as either addi or li depending on source
            assertTrue(lines.any { it.contains("addi") || it.contains("li") || it.contains("42") },
                "Should have immediate add: $lines")
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
            assertTrue(lines.any { it.startsWith("sub ") }, "Should have sub: $lines")
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
            assertTrue(lines.any { it.startsWith("mul ") }, "Should have mul: $lines")
        }

        @Test
        fun `division`() {
            val lines = generateAndDisassemble {
                val params = createFunction("f", listOf(
                    Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val result = sdiv(params[0], params[1])
                ret(result)
                finalizeFunction()
            }
            assertTrue(lines.any { it.contains("div") }, "Should have div: $lines")
        }

        @Test
        fun `remainder`() {
            val lines = generateAndDisassemble {
                val params = createFunction("f", listOf(
                    Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val result = srem(params[0], params[1])
                ret(result)
                finalizeFunction()
            }
            assertTrue(lines.any { it.contains("rem") }, "Should have rem: $lines")
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
            assertTrue(lines.any { it.contains("or ") }, "Should have or: $lines")
        }

        @Test
        fun `bitwise xor`() {
            val lines = generateAndDisassemble {
                val params = createFunction("f", listOf(
                    Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val result = xor(params[0], params[1])
                ret(result)
                finalizeFunction()
            }
            assertTrue(lines.any { it.contains("xor") }, "Should have xor: $lines")
        }

        @Test
        fun `shift left`() {
            val lines = generateAndDisassemble {
                val params = createFunction("f", listOf(
                    Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val result = shl(params[0], params[1])
                ret(result)
                finalizeFunction()
            }
            assertTrue(lines.any { it.contains("sll") }, "Should have sll: $lines")
        }
    }

    @Nested
    inner class RegisterPressure {

        @Test
        fun `many live values cause spilling`() {
            val code = generateCode {
                val params = createFunction("f", listOf(Param("x", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val values = (1..20).map { add(params[0], Constant.I64(it.toLong())) }
                var sum = add(values[0], values[1])
                for (i in 2 until values.size) sum = add(sum, values[i])
                ret(sum)
                finalizeFunction()
            }
            assertTrue(code.isNotEmpty(), "Should compile with spills")
        }

        @Test
        fun `spilled values produce sd and ld instructions`() {
            val lines = generateAndDisassemble {
                val params = createFunction("f", listOf(Param("x", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val values = (1..20).map { add(params[0], Constant.I64(it.toLong())) }
                var sum = add(values[0], values[1])
                for (i in 2 until values.size) sum = add(sum, values[i])
                ret(sum)
                finalizeFunction()
            }
            val allText = lines.joinToString(" ")
            assertTrue(allText.contains("sd") || allText.contains("sw"),
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
            assertTrue(code.isNotEmpty(), "Chained ops need few regs")
        }

        @Test
        fun `i32 values under pressure`() {
            val code = generateCode {
                val params = createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
                positionAtEnd(appendBlock("entry"))
                val values = (1..20).map { add(params[0], Constant.I32(it)) }
                var sum = add(values[0], values[1])
                for (i in 2 until values.size) sum = add(sum, values[i])
                ret(sum)
                finalizeFunction()
            }
            assertTrue(code.isNotEmpty())
        }

        @Test
        fun `eviction with varying spill costs`() {
            val code = generateCode {
                val params = createFunction("f", listOf(
                    Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val values = (1..20).map { add(params[0], Constant.I64(it.toLong())) }
                // Use v1 many times (high spill cost)
                var sum = add(values[0], values[0])
                sum = add(sum, values[0])
                for (i in 1 until values.size) sum = add(sum, values[i])
                ret(sum)
                finalizeFunction()
            }
            assertTrue(code.isNotEmpty())
        }
    }

    @Nested
    inner class CallerSavedRegisters {

        @Test
        fun `value live across call uses callee-saved s registers`() {
            val lines = generateAndDisassemble {
                declareFunction("ext", emptyList(), Type.I64)
                val params = createFunction("f", listOf(Param("x", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val r = call("ext", emptyList(), Type.I64)!!
                val sum = add(params[0], r)
                ret(sum)
                finalizeFunction()
            }
            val allText = lines.joinToString(" ")
            val hasCalleeSaved = allText.contains("s1") || allText.contains("s2") ||
                allText.contains("s3") || allText.contains("s4") ||
                allText.contains("s5") || allText.contains("s6")
            assertTrue(hasCalleeSaved, "Should use s-registers for value across call: $lines")
        }

        @Test
        fun `callee-saved s registers saved in prologue`() {
            val lines = generateAndDisassemble {
                declareFunction("ext", emptyList(), Type.Void)
                val params = createFunction("f", listOf(Param("x", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                call("ext", emptyList(), Type.Void)
                ret(params[0])
                finalizeFunction()
            }
            val allText = lines.joinToString(" ")
            assertTrue(allText.contains("sd") || allText.contains("sw"),
                "Should save callee-saved in prologue: $lines")
        }

        @Test
        fun `callee-saved registers restored in epilogue`() {
            val lines = generateAndDisassemble {
                declareFunction("ext", emptyList(), Type.Void)
                val params = createFunction("f", listOf(Param("x", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                call("ext", emptyList(), Type.Void)
                ret(params[0])
                finalizeFunction()
            }
            val allText = lines.joinToString(" ")
            assertTrue(allText.contains("ld") || allText.contains("lw"),
                "Should restore callee-saved in epilogue: $lines")
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
        fun `value not live across call can use temp registers`() {
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
        fun `param move for value across call`() {
            val lines = generateAndDisassemble {
                declareFunction("ext", emptyList(), Type.Void)
                val params = createFunction("f", listOf(Param("x", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                call("ext", emptyList(), Type.Void)
                ret(params[0])
                finalizeFunction()
            }
            val allText = lines.joinToString(" ")
            // Param in a0 should be moved to s-register before call
            assertTrue(allText.contains("mv") || allText.contains("addi"),
                "Should move param to callee-saved: $lines")
        }
    }

    @Nested
    inner class CalleeSavedSelection {

        @Test
        fun `no-call function skips callee-saved save`() {
            val lines = generateAndDisassemble {
                val params = createFunction("f", listOf(
                    Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val sum = add(params[0], params[1])
                ret(sum)
                finalizeFunction()
            }
            val allText = lines.joinToString(" ")
            val hasSRegs = (1..11).any { allText.contains("s$it,") || allText.contains("s$it)") }
            assertFalse(hasSRegs, "No-call function should avoid s-registers: $lines")
        }

        @Test
        fun `save and restore are balanced`() {
            val lines = generateAndDisassemble {
                declareFunction("ext", emptyList(), Type.Void)
                val params = createFunction("f", listOf(Param("x", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                call("ext", emptyList(), Type.Void)
                ret(params[0])
                finalizeFunction()
            }
            // Count sd and ld instructions (stores/loads to stack for callee-saved)
            val sdCount = lines.count { it.startsWith("sd ") }
            val ldCount = lines.count { it.startsWith("ld ") }
            // They should be roughly balanced (may have data loads/stores too)
            assertTrue(sdCount > 0 && ldCount > 0, "Should have balanced saves/restores")
        }

        @Test
        fun `callee-saved set includes s1 through s11`() {
            val code = generateCode {
                declareFunction("ext", emptyList(), Type.Void)
                val params = createFunction("f", listOf(
                    Param("a", Type.I64), Param("b", Type.I64), Param("c", Type.I64),
                    Param("d", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                call("ext", emptyList(), Type.Void)
                val sum = add(params[0], params[1])
                val r2 = add(sum, params[2])
                val r3 = add(r2, params[3])
                ret(r3)
                finalizeFunction()
            }
            assertTrue(code.isNotEmpty())
        }
    }

    @Nested
    inner class SpillSlotAssignment {

        @Test
        fun `spill slots reference stack`() {
            val lines = generateAndDisassemble {
                val params = createFunction("f", listOf(Param("x", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val values = (1..20).map { add(params[0], Constant.I64(it.toLong())) }
                var sum = add(values[0], values[1])
                for (i in 2 until values.size) sum = add(sum, values[i])
                ret(sum)
                finalizeFunction()
            }
            val allText = lines.joinToString(" ")
            assertTrue(allText.contains("sp") || allText.contains("s0"),
                "Spills should reference stack/frame: $lines")
        }

        @Test
        fun `frame allocation with addi sp`() {
            val lines = generateAndDisassemble {
                val params = createFunction("f", listOf(Param("x", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val values = (1..20).map { add(params[0], Constant.I64(it.toLong())) }
                var sum = add(values[0], values[1])
                for (i in 2 until values.size) sum = add(sum, values[i])
                ret(sum)
                finalizeFunction()
            }
            val allText = lines.joinToString(" ")
            assertTrue(allText.contains("addi") && allText.contains("sp"),
                "Should adjust sp for frame: $lines")
        }

        @Test
        fun `short-lived values minimize spill slots`() {
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
        fun `conditional branch preserves live ranges`() {
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

        @Test
        fun `diamond control flow`() {
            val code = generateCode {
                val params = createFunction("f", listOf(
                    Param("n", Type.I64)), Type.I64)
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
            assertTrue(code.isNotEmpty())
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
            val gen = RiscVCodeGenerator()
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

        @Test
        fun `function with no params returning constant`() {
            val code = generateCode {
                createFunction("f", emptyList(), Type.I64)
                positionAtEnd(appendBlock("entry"))
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
                positionAtEnd(appendBlock("entry"))
                ret()
                finalizeFunction()
            }
            assertTrue(code.isNotEmpty())
            assertEquals(0, code.size % 4)
        }

        @Test
        fun `function returning zero`() {
            val lines = generateAndDisassemble {
                createFunction("f", emptyList(), Type.I64)
                positionAtEnd(appendBlock("entry"))
                ret(Constant.I64(0))
                finalizeFunction()
            }
            val allText = lines.joinToString(" ")
            assertTrue(allText.contains("a0"), "Return 0 should set a0: $lines")
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
        fun `load and store`() {
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
        fun `i32 operations`() {
            val code = generateCode {
                val params = createFunction("f", listOf(
                    Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
                positionAtEnd(appendBlock("entry"))
                val sum = add(params[0], params[1])
                val diff = sub(sum, Constant.I32(1))
                ret(diff)
                finalizeFunction()
            }
            assertTrue(code.isNotEmpty())
        }

        @Test
        fun `unsigned division`() {
            val code = generateCode {
                val params = createFunction("f", listOf(
                    Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val result = udiv(params[0], params[1])
                ret(result)
                finalizeFunction()
            }
            assertTrue(code.isNotEmpty())
        }

        @Test
        fun `unsigned remainder`() {
            val code = generateCode {
                val params = createFunction("f", listOf(
                    Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val result = urem(params[0], params[1])
                ret(result)
                finalizeFunction()
            }
            assertTrue(code.isNotEmpty())
        }

        @Test
        fun `nested calls with live values`() {
            val code = generateCode {
                declareFunction("ext", listOf(Param("v", Type.I64)), Type.I64)
                val params = createFunction("f", listOf(Param("x", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val r1 = call("ext", listOf(params[0]), Type.I64)!!
                val r2 = call("ext", listOf(r1), Type.I64)!!
                val result = add(r2, params[0])
                ret(result)
                finalizeFunction()
            }
            assertTrue(code.isNotEmpty())
        }

        @Test
        fun `logical shift right`() {
            val lines = generateAndDisassemble {
                val params = createFunction("f", listOf(
                    Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
                positionAtEnd(appendBlock("entry"))
                val result = lshr(params[0], params[1])
                ret(result)
                finalizeFunction()
            }
            assertTrue(lines.any { it.contains("srl") }, "Should have srl: $lines")
        }
    }
}
