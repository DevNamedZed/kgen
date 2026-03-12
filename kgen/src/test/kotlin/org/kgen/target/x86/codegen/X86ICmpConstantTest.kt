package org.kgen.target.x86.codegen

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*
import org.kgen.ir.build.IrBuilder
import org.kgen.ir.target.Target

class X86ICmpConstantTest {

    private fun compileAndCheck(builder: (IrBuilder) -> Unit): ByteArray {
        val ir = IrBuilder("test", Target.x86_64())
        builder(ir)
        val obj = X86CodeGenerator().generateObjectFile(ir.build())
        val code = obj.sections.first { it.name == ".text" }.data
        assertTrue(code.isNotEmpty(), "Generated code should not be empty")
        return code
    }

    @Test
    fun `icmp with constant zero on rhs compiles`() {
        compileAndCheck { ir ->
            val params = ir.createFunction("fn", listOf(Param("x", Type.I32)), Type.I32)
            ir.positionAtEnd(ir.appendBlock("entry"))
            val cond = ir.icmp(ICmpPredicate.EQ, params[0], Constant.I32(0))
            ir.condBr(cond, "then", "else")

            ir.positionAtEnd(ir.appendBlock("then"))
            ir.ret(Constant.I32(1))

            ir.positionAtEnd(ir.appendBlock("else"))
            ir.ret(Constant.I32(0))
            ir.finalizeFunction()
        }
    }

    @Test
    fun `icmp with constant on lhs compiles`() {
        compileAndCheck { ir ->
            val params = ir.createFunction("fn", listOf(Param("x", Type.I32)), Type.I32)
            ir.positionAtEnd(ir.appendBlock("entry"))
            val cond = ir.icmp(ICmpPredicate.SGT, Constant.I32(5), params[0])
            ir.condBr(cond, "then", "else")

            ir.positionAtEnd(ir.appendBlock("then"))
            ir.ret(Constant.I32(1))

            ir.positionAtEnd(ir.appendBlock("else"))
            ir.ret(Constant.I32(0))
            ir.finalizeFunction()
        }
    }

    @Test
    fun `icmp with both constants compiles`() {
        compileAndCheck { ir ->
            ir.createFunction("fn", emptyList(), Type.I32)
            ir.positionAtEnd(ir.appendBlock("entry"))
            val cond = ir.icmp(ICmpPredicate.SLT, Constant.I32(3), Constant.I32(10))
            ir.condBr(cond, "then", "else")

            ir.positionAtEnd(ir.appendBlock("then"))
            ir.ret(Constant.I32(1))

            ir.positionAtEnd(ir.appendBlock("else"))
            ir.ret(Constant.I32(0))
            ir.finalizeFunction()
        }
    }

    @Test
    fun `icmp in non-fused context compiles`() {
        compileAndCheck { ir ->
            val params = ir.createFunction("fn", listOf(Param("x", Type.I32)), Type.I32)
            ir.positionAtEnd(ir.appendBlock("entry"))
            val cond = ir.icmp(ICmpPredicate.SGT, params[0], Constant.I32(0))
            val result = ir.select(cond, Constant.I32(1), Constant.I32(-1))
            ir.ret(result)
            ir.finalizeFunction()
        }
    }

    @Test
    fun `icmp sge with constant rhs in loop compiles`() {
        // Build a simple counted loop: while (i < 10) i++; return i;
        // Tests that ICmp with constant RHS works in a loop context with phis.
        val ir = IrBuilder("test", Target.x86_64())
        ir.createFunction("fn", listOf(Param("n", Type.I32)), Type.I32)

        // We need to build blocks manually with correct phi references.
        // First build body to get the iNext ref, then patch the phi.
        ir.positionAtEnd(ir.appendBlock("entry"))
        ir.br("body")

        // body: i = phi(0 from entry, iNext from body); if i >= 10 goto exit else loop
        ir.positionAtEnd(ir.appendBlock("body"))
        // Placeholder phi — incoming from body will reference iNext which we create next
        val iPhi = ir.phi(Type.I32, listOf(Constant.I32(0) to "entry"))
        val iNext = ir.add(iPhi, Constant.I32(1))
        val cmp = ir.icmp(ICmpPredicate.SGE, iNext, Constant.I32(10))
        ir.condBr(cmp, "exit", "body")

        ir.positionAtEnd(ir.appendBlock("exit"))
        ir.ret(iNext)
        ir.finalizeFunction()

        // Patch the phi to include the back-edge value
        val module = ir.build()
        val fn = module.functions[0]
        val bodyBlock = fn.blocks.first { it.label == "body" }
        val phiInst = bodyBlock.instructions[0] as Instruction.Phi
        val patched = phiInst.copy(incoming = phiInst.incoming + (iNext to "body"))
        val patchedBlock = bodyBlock.copy(instructions = listOf(patched) + bodyBlock.instructions.drop(1))
        val patchedFn = fn.copy(blocks = fn.blocks.map { if (it.label == "body") patchedBlock else it })
        val patchedModule = module.copy(functions = listOf(patchedFn))

        val obj = X86CodeGenerator().generateObjectFile(patchedModule)
        val code = obj.sections.first { it.name == ".text" }.data
        assertTrue(code.isNotEmpty(), "Generated code should not be empty")
    }

    @Test
    fun `i64 icmp with constant compiles`() {
        compileAndCheck { ir ->
            val params = ir.createFunction("fn", listOf(Param("x", Type.I64)), Type.I64)
            ir.positionAtEnd(ir.appendBlock("entry"))
            val cond = ir.icmp(ICmpPredicate.NE, params[0], Constant.I64(0))
            ir.condBr(cond, "then", "else")

            ir.positionAtEnd(ir.appendBlock("then"))
            ir.ret(params[0])

            ir.positionAtEnd(ir.appendBlock("else"))
            ir.ret(Constant.I64(42))
            ir.finalizeFunction()
        }
    }
}
