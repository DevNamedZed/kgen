package org.kgen.ir.build

import org.kgen.ir.*
import org.kgen.ir.instructions.*
import org.kgen.ir.target.*
import org.kgen.ir.target.Target
import org.kgen.ir.text.IrPrinter
import org.kgen.ir.verify.IrVerifier
import org.kgen.ir.types.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class ModuleBuilderTest {

    @Test
    fun `create simple function`() {
        val ir = ModuleBuilder("test", Target.wasm())
        val params = ir.createFunction("add", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val sum = ir.add(params[0], params[1])
        ir.ret(sum)
        ir.finalizeFunction()

        val mod = ir.build()
        assertEquals(1, mod.functions.size)
        assertEquals("add", mod.functions[0].name)
        assertEquals(2, mod.functions[0].params.size)
        assertEquals(Type.I32, mod.functions[0].returnType)
    }

    @Test
    fun `target is stored`() {
        val target = Target.x86_64(X86CPU.HASWELL)
        val ir = ModuleBuilder("test", target)
        assertEquals(target, ir.target)
    }

    @Test
    fun `default target is wasm`() {
        val ir = ModuleBuilder("test")
        assertEquals(Arch.WASM32, ir.target.arch)
    }

    @Test
    fun `declare external function`() {
        val ir = ModuleBuilder("test")
        val ref = ir.declareFunction("puts", listOf(Param("s", Type.OpaquePointer)), Type.I32)
        assertEquals("puts", ref.name)

        val mod = ir.build()
        assertTrue(mod.functions[0].isExternal)
        assertTrue(mod.functions[0].blocks.isEmpty())
    }

    @Test
    fun `add global`() {
        val ir = ModuleBuilder("test")
        val ref = ir.addGlobal("counter", Type.I32, Type.i32(0))
        assertEquals("counter", ref.name)

        val mod = ir.build()
        assertEquals(1, mod.globals.size)
        assertEquals("counter", mod.globals[0].name)
    }

    @Test
    fun `add struct`() {
        val ir = ModuleBuilder("test")
        ir.addStruct("Point", listOf(Param("x", Type.F64), Param("y", Type.F64)))

        val mod = ir.build()
        assertEquals(1, mod.structs.size)
        assertEquals("Point", mod.structs[0].name)
        assertEquals(2, mod.structs[0].fields.size)
    }

    @Test
    fun `add class and find it`() {
        val ir = ModuleBuilder("test")
        val cls = ClassDefinition("Counter", fields = listOf(FieldDefinition("count", Type.I32)))
        ir.addClass(cls)

        assertNotNull(ir.findClass("Counter"))
        assertNull(ir.findClass("NonExistent"))
        assertEquals("Counter", ir.findClass("Counter")!!.name)
    }

    @Test
    fun `multiple blocks with control flow`() {
        val ir = ModuleBuilder("test")
        val params = ir.createFunction("abs", listOf(Param("x", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val cmp = ir.icmp(ICmpPredicate.SGE, params[0], Type.i32(0))
        ir.condBr(cmp, BlockRef("positive"), BlockRef("negative"))

        ir.appendBlock("positive")
        ir.ret(params[0])

        ir.appendBlock("negative")
        val neg = ir.neg(params[0])
        ir.ret(neg)

        ir.finalizeFunction()
        val mod = ir.build()

        assertEquals(3, mod.functions[0].blocks.size)
    }

    @Test
    fun `function with linkage and visibility`() {
        val ir = ModuleBuilder("test")
        ir.createFunction("internal_fn", emptyList(), Type.Void,
            linkage = Linkage.INTERNAL, visibility = Visibility.HIDDEN)
        ir.appendBlock("entry")
        ir.ret()
        ir.finalizeFunction()

        val mod = ir.build()
        assertEquals(Linkage.INTERNAL, mod.functions[0].linkage)
        assertEquals(Visibility.HIDDEN, mod.functions[0].visibility)
    }

    @Test
    fun `cannot start function without finalizing previous`() {
        val ir = ModuleBuilder("test")
        ir.createFunction("f1", emptyList(), Type.Void)
        assertThrows(IllegalStateException::class.java) {
            ir.createFunction("f2", emptyList(), Type.Void)
        }
    }

    @Test
    fun `module name and properties`() {
        val ir = ModuleBuilder("myModule", Target.x86_64())
        ir.targetTriple = "x86_64-unknown-linux-gnu"
        ir.sourceFile = "main.kt"

        val mod = ir.build()
        assertEquals("myModule", mod.name)
        assertEquals("x86_64-unknown-linux-gnu", mod.targetTriple)
        assertEquals("main.kt", mod.sourceFile)
    }

    // --- Arithmetic operations ---

    @Test
    fun `add instruction`() {
        val ir = ModuleBuilder("test")
        val params = ir.createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val result = ir.add(params[0], params[1])
        ir.ret(result)
        ir.finalizeFunction()

        val mod = ir.build()
        val inst = mod.functions[0].blocks[0].instructions[0]
        assertTrue(inst is Add)
    }

    @Test
    fun `sub instruction`() {
        val ir = ModuleBuilder("test")
        val params = ir.createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val result = ir.sub(params[0], params[1])
        ir.ret(result)
        ir.finalizeFunction()

        val mod = ir.build()
        val inst = mod.functions[0].blocks[0].instructions[0]
        assertTrue(inst is Sub)
    }

    @Test
    fun `mul instruction`() {
        val ir = ModuleBuilder("test")
        val params = ir.createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val result = ir.mul(params[0], params[1])
        ir.ret(result)
        ir.finalizeFunction()

        val mod = ir.build()
        val inst = mod.functions[0].blocks[0].instructions[0]
        assertTrue(inst is Mul)
    }

    @Test
    fun `sdiv instruction`() {
        val ir = ModuleBuilder("test")
        val params = ir.createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val result = ir.sdiv(params[0], params[1])
        ir.ret(result)
        ir.finalizeFunction()

        val mod = ir.build()
        val inst = mod.functions[0].blocks[0].instructions[0]
        assertTrue(inst is SDiv)
    }

    @Test
    fun `udiv instruction`() {
        val ir = ModuleBuilder("test")
        val params = ir.createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val result = ir.udiv(params[0], params[1])
        ir.ret(result)
        ir.finalizeFunction()

        val mod = ir.build()
        val inst = mod.functions[0].blocks[0].instructions[0]
        assertTrue(inst is UDiv)
    }

    @Test
    fun `srem instruction`() {
        val ir = ModuleBuilder("test")
        val params = ir.createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val result = ir.srem(params[0], params[1])
        ir.ret(result)
        ir.finalizeFunction()

        val mod = ir.build()
        val inst = mod.functions[0].blocks[0].instructions[0]
        assertTrue(inst is SRem)
    }

    @Test
    fun `urem instruction`() {
        val ir = ModuleBuilder("test")
        val params = ir.createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val result = ir.urem(params[0], params[1])
        ir.ret(result)
        ir.finalizeFunction()

        val mod = ir.build()
        val inst = mod.functions[0].blocks[0].instructions[0]
        assertTrue(inst is URem)
    }

    @Test
    fun `neg instruction`() {
        val ir = ModuleBuilder("test")
        val params = ir.createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val result = ir.neg(params[0])
        ir.ret(result)
        ir.finalizeFunction()

        val mod = ir.build()
        val inst = mod.functions[0].blocks[0].instructions[0]
        assertTrue(inst is Neg)
    }

    // --- Float arithmetic ---

    @Test
    fun `fadd instruction`() {
        val ir = ModuleBuilder("test")
        val params = ir.createFunction("f", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.F64)
        ir.appendBlock("entry")
        val result = ir.fadd(params[0], params[1])
        ir.ret(result)
        ir.finalizeFunction()

        val mod = ir.build()
        val inst = mod.functions[0].blocks[0].instructions[0]
        assertTrue(inst is FAdd)
    }

    @Test
    fun `fsub instruction`() {
        val ir = ModuleBuilder("test")
        val params = ir.createFunction("f", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.F64)
        ir.appendBlock("entry")
        val result = ir.fsub(params[0], params[1])
        ir.ret(result)
        ir.finalizeFunction()

        val mod = ir.build()
        val inst = mod.functions[0].blocks[0].instructions[0]
        assertTrue(inst is FSub)
    }

    @Test
    fun `fmul instruction`() {
        val ir = ModuleBuilder("test")
        val params = ir.createFunction("f", listOf(Param("a", Type.F32), Param("b", Type.F32)), Type.F32)
        ir.appendBlock("entry")
        val result = ir.fmul(params[0], params[1])
        ir.ret(result)
        ir.finalizeFunction()

        val mod = ir.build()
        val inst = mod.functions[0].blocks[0].instructions[0]
        assertTrue(inst is FMul)
    }

    @Test
    fun `fdiv instruction`() {
        val ir = ModuleBuilder("test")
        val params = ir.createFunction("f", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.F64)
        ir.appendBlock("entry")
        val result = ir.fdiv(params[0], params[1])
        ir.ret(result)
        ir.finalizeFunction()

        val mod = ir.build()
        val inst = mod.functions[0].blocks[0].instructions[0]
        assertTrue(inst is FDiv)
    }

    @Test
    fun `frem instruction`() {
        val ir = ModuleBuilder("test")
        val params = ir.createFunction("f", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.F64)
        ir.appendBlock("entry")
        val result = ir.frem(params[0], params[1])
        ir.ret(result)
        ir.finalizeFunction()

        val mod = ir.build()
        val inst = mod.functions[0].blocks[0].instructions[0]
        assertTrue(inst is FRem)
    }

    @Test
    fun `fneg instruction`() {
        val ir = ModuleBuilder("test")
        val params = ir.createFunction("f", listOf(Param("x", Type.F64)), Type.F64)
        ir.appendBlock("entry")
        val result = ir.fneg(params[0])
        ir.ret(result)
        ir.finalizeFunction()

        val mod = ir.build()
        val inst = mod.functions[0].blocks[0].instructions[0]
        assertTrue(inst is FNeg)
    }

    // --- Comparison operations ---

    @Test
    fun `icmp all predicates produce I1`() {
        for (pred in ICmpPredicate.entries) {
            val ir = ModuleBuilder("test")
            val params = ir.createFunction("cmp", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
            ir.appendBlock("entry")
            val cmp = ir.icmp(pred, params[0], params[1])
            assertEquals(Type.I1, cmp.type)
            val result = ir.select(cmp, Constant.I32(1), Constant.I32(0))
            ir.ret(result)
            ir.finalizeFunction()

            val mod = ir.build()
            val inst = mod.functions[0].blocks[0].instructions[0]
            assertTrue(inst is ICmp)
            assertEquals(pred, (inst as ICmp).predicate)
        }
    }

    @Test
    fun `fcmp produces I1`() {
        val ir = ModuleBuilder("test")
        val params = ir.createFunction("f", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.I32)
        ir.appendBlock("entry")
        val cmp = ir.fcmp(FCmpPredicate.OLT, params[0], params[1])
        assertEquals(Type.I1, cmp.type)
        val result = ir.select(cmp, Constant.I32(1), Constant.I32(0))
        ir.ret(result)
        ir.finalizeFunction()

        val mod = ir.build()
        val inst = mod.functions[0].blocks[0].instructions[0]
        assertTrue(inst is FCmp)
    }

    // --- Bitwise operations ---

    @Test
    fun `and instruction`() {
        val ir = ModuleBuilder("test")
        val params = ir.createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val result = ir.and(params[0], params[1])
        ir.ret(result)
        ir.finalizeFunction()

        val inst = ir.build().functions[0].blocks[0].instructions[0]
        assertTrue(inst is And)
    }

    @Test
    fun `or instruction`() {
        val ir = ModuleBuilder("test")
        val params = ir.createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val result = ir.or(params[0], params[1])
        ir.ret(result)
        ir.finalizeFunction()

        val inst = ir.build().functions[0].blocks[0].instructions[0]
        assertTrue(inst is Or)
    }

    @Test
    fun `xor instruction`() {
        val ir = ModuleBuilder("test")
        val params = ir.createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val result = ir.xor(params[0], params[1])
        ir.ret(result)
        ir.finalizeFunction()

        val inst = ir.build().functions[0].blocks[0].instructions[0]
        assertTrue(inst is Xor)
    }

    @Test
    fun `shl instruction`() {
        val ir = ModuleBuilder("test")
        val params = ir.createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val result = ir.shl(params[0], params[1])
        ir.ret(result)
        ir.finalizeFunction()

        val inst = ir.build().functions[0].blocks[0].instructions[0]
        assertTrue(inst is Shl)
    }

    @Test
    fun `lshr instruction`() {
        val ir = ModuleBuilder("test")
        val params = ir.createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val result = ir.lshr(params[0], params[1])
        ir.ret(result)
        ir.finalizeFunction()

        val inst = ir.build().functions[0].blocks[0].instructions[0]
        assertTrue(inst is LShr)
    }

    @Test
    fun `ashr instruction`() {
        val ir = ModuleBuilder("test")
        val params = ir.createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val result = ir.ashr(params[0], params[1])
        ir.ret(result)
        ir.finalizeFunction()

        val inst = ir.build().functions[0].blocks[0].instructions[0]
        assertTrue(inst is AShr)
    }

    @Test
    fun `not instruction`() {
        val ir = ModuleBuilder("test")
        val params = ir.createFunction("f", listOf(Param("a", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val result = ir.not(params[0])
        ir.ret(result)
        ir.finalizeFunction()

        val inst = ir.build().functions[0].blocks[0].instructions[0]
        assertTrue(inst is Not)
    }

    // --- Memory operations ---

    @Test
    fun `alloca load store`() {
        val ir = ModuleBuilder("test")
        ir.createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val ptr = ir.alloca(Type.I32)
        ir.store(ir.param(0), ptr)
        val loaded = ir.load(Type.I32, ptr)
        ir.ret(loaded)
        ir.finalizeFunction()

        val mod = ir.build()
        val instrs = mod.functions[0].blocks[0].instructions
        assertTrue(instrs[0] is Alloca)
        assertTrue(instrs[1] is Store)
        assertTrue(instrs[2] is Load)
    }

    @Test
    fun `gep instruction`() {
        val ir = ModuleBuilder("test")
        ir.createFunction("f", listOf(Param("ptr", Type.Pointer(Type.I32))), Type.I32)
        ir.appendBlock("entry")
        val elemPtr = ir.gep(Type.I32, ir.param(0), Constant.I32(3))
        val loaded = ir.load(Type.I32, elemPtr)
        ir.ret(loaded)
        ir.finalizeFunction()

        val mod = ir.build()
        val inst = mod.functions[0].blocks[0].instructions[0]
        assertTrue(inst is GetElementPtr)
        val gep = inst as GetElementPtr
        assertTrue(gep.inBounds)
        assertEquals(1, gep.indices.size)
    }

    // --- Control flow ---

    @Test
    fun `unconditional branch`() {
        val ir = ModuleBuilder("test")
        ir.createFunction("f", emptyList(), Type.Void)
        ir.appendBlock("entry")
        ir.br(BlockRef("exit"))
        ir.appendBlock("exit")
        ir.ret()
        ir.finalizeFunction()

        val mod = ir.build()
        assertEquals(2, mod.functions[0].blocks.size)
        assertTrue(mod.functions[0].blocks[0].instructions[0] is Br)
    }

    @Test
    fun `conditional branch`() {
        val ir = ModuleBuilder("test")
        val params = ir.createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val cmp = ir.icmp(ICmpPredicate.SGT, params[0], Constant.I32(0))
        ir.condBr(cmp, BlockRef("then"), BlockRef("else"))

        ir.appendBlock("then")
        ir.ret(Constant.I32(1))

        ir.appendBlock("else")
        ir.ret(Constant.I32(0))

        ir.finalizeFunction()
        val mod = ir.build()
        assertEquals(3, mod.functions[0].blocks.size)
        val condBr = mod.functions[0].blocks[0].instructions.last()
        assertTrue(condBr is CondBr)
        assertEquals(BlockRef("then"), (condBr as CondBr).trueTarget)
        assertEquals(BlockRef("else"), condBr.falseTarget)
    }

    @Test
    fun `switch instruction`() {
        val ir = ModuleBuilder("test")
        val params = ir.createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        ir.switch(params[0], "default", listOf(
            Constant.I32(0) to "case0",
            Constant.I32(1) to "case1",
            Constant.I32(2) to "case2",
        ))

        ir.appendBlock("case0")
        ir.ret(Constant.I32(10))
        ir.appendBlock("case1")
        ir.ret(Constant.I32(20))
        ir.appendBlock("case2")
        ir.ret(Constant.I32(30))
        ir.appendBlock("default")
        ir.ret(Constant.I32(-1))

        ir.finalizeFunction()
        val mod = ir.build()
        assertEquals(5, mod.functions[0].blocks.size)
        val sw = mod.functions[0].blocks[0].instructions[0]
        assertTrue(sw is Switch)
        assertEquals(3, (sw as Switch).cases.size)
    }

    @Test
    fun `select instruction`() {
        val ir = ModuleBuilder("test")
        val params = ir.createFunction("max", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val cmp = ir.icmp(ICmpPredicate.SGT, params[0], params[1])
        val result = ir.select(cmp, params[0], params[1])
        ir.ret(result)
        ir.finalizeFunction()

        val mod = ir.build()
        val instrs = mod.functions[0].blocks[0].instructions
        assertTrue(instrs[1] is Select)
        assertEquals(Type.I32, instrs[1].result!!.type)
    }

    // --- PHI nodes ---

    @Test
    fun `phi instruction`() {
        val ir = ModuleBuilder("test")
        val params = ir.createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val cmp = ir.icmp(ICmpPredicate.SGT, params[0], Constant.I32(0))
        ir.condBr(cmp, BlockRef("then"), BlockRef("else"))

        ir.appendBlock("then")
        ir.br(BlockRef("merge"))

        ir.appendBlock("else")
        val negated = ir.neg(params[0])
        ir.br(BlockRef("merge"))

        ir.appendBlock("merge")
        val phi = ir.phi(Type.I32, listOf(
            params[0] to "then",
            negated to "else"
        ))
        ir.ret(phi)
        ir.finalizeFunction()

        val mod = ir.build()
        assertEquals(4, mod.functions[0].blocks.size)
        val mergeBlock = mod.functions[0].blocks[3]
        assertTrue(mergeBlock.instructions[0] is Phi)
        val phiInst = mergeBlock.instructions[0] as Phi
        assertEquals(2, phiInst.incoming.size)
    }

    // --- Calls ---

    @Test
    fun `call instruction with return value`() {
        val ir = ModuleBuilder("test")
        ir.declareFunction("getVal", emptyList(), Type.I32)
        ir.createFunction("f", emptyList(), Type.I32)
        ir.appendBlock("entry")
        val result = ir.call("getVal", emptyList(), Type.I32)
        assertNotNull(result)
        ir.ret(result!!)
        ir.finalizeFunction()

        val mod = ir.build()
        val inst = mod.functions[1].blocks[0].instructions[0]
        assertTrue(inst is Call)
        assertNotNull(inst.result)
    }

    @Test
    fun `call instruction with void return`() {
        val ir = ModuleBuilder("test")
        ir.declareFunction("doStuff", emptyList(), Type.Void)
        ir.createFunction("f", emptyList(), Type.Void)
        ir.appendBlock("entry")
        val result = ir.call("doStuff", emptyList(), Type.Void)
        assertNull(result)
        ir.ret()
        ir.finalizeFunction()

        val mod = ir.build()
        val inst = mod.functions[1].blocks[0].instructions[0]
        assertTrue(inst is Call)
        assertNull(inst.result)
    }

    @Test
    fun `call with arguments`() {
        val ir = ModuleBuilder("test")
        ir.declareFunction("add", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        ir.createFunction("f", emptyList(), Type.I32)
        ir.appendBlock("entry")
        val result = ir.call("add", listOf(Constant.I32(3), Constant.I32(4)), Type.I32)
        ir.ret(result!!)
        ir.finalizeFunction()

        val mod = ir.build()
        val inst = mod.functions[1].blocks[0].instructions[0] as Call
        assertEquals(2, inst.args.size)
    }

    // --- Conversion operations ---

    @Test
    fun `trunc instruction`() {
        val ir = ModuleBuilder("test")
        val params = ir.createFunction("f", listOf(Param("x", Type.I64)), Type.I32)
        ir.appendBlock("entry")
        val truncated = ir.trunc(params[0], Type.I32)
        assertEquals(Type.I32, truncated.type)
        ir.ret(truncated)
        ir.finalizeFunction()

        val inst = ir.build().functions[0].blocks[0].instructions[0]
        assertTrue(inst is IntTrunc)
    }

    @Test
    fun `zext instruction`() {
        val ir = ModuleBuilder("test")
        val params = ir.createFunction("f", listOf(Param("x", Type.I32)), Type.I64)
        ir.appendBlock("entry")
        val extended = ir.zext(params[0], Type.I64)
        assertEquals(Type.I64, extended.type)
        ir.ret(extended)
        ir.finalizeFunction()

        val inst = ir.build().functions[0].blocks[0].instructions[0]
        assertTrue(inst is ZExt)
    }

    @Test
    fun `sext instruction`() {
        val ir = ModuleBuilder("test")
        val params = ir.createFunction("f", listOf(Param("x", Type.I32)), Type.I64)
        ir.appendBlock("entry")
        val extended = ir.sext(params[0], Type.I64)
        assertEquals(Type.I64, extended.type)
        ir.ret(extended)
        ir.finalizeFunction()

        val inst = ir.build().functions[0].blocks[0].instructions[0]
        assertTrue(inst is SExt)
    }

    @Test
    fun `fptrunc instruction`() {
        val ir = ModuleBuilder("test")
        val params = ir.createFunction("f", listOf(Param("x", Type.F64)), Type.F32)
        ir.appendBlock("entry")
        val truncated = ir.fptrunc(params[0], Type.F32)
        assertEquals(Type.F32, truncated.type)
        ir.ret(truncated)
        ir.finalizeFunction()

        val inst = ir.build().functions[0].blocks[0].instructions[0]
        assertTrue(inst is FPTrunc)
    }

    @Test
    fun `fpext instruction`() {
        val ir = ModuleBuilder("test")
        val params = ir.createFunction("f", listOf(Param("x", Type.F32)), Type.F64)
        ir.appendBlock("entry")
        val extended = ir.fpext(params[0], Type.F64)
        assertEquals(Type.F64, extended.type)
        ir.ret(extended)
        ir.finalizeFunction()

        val inst = ir.build().functions[0].blocks[0].instructions[0]
        assertTrue(inst is FPExt)
    }

    @Test
    fun `fptosi instruction`() {
        val ir = ModuleBuilder("test")
        val params = ir.createFunction("f", listOf(Param("x", Type.F64)), Type.I32)
        ir.appendBlock("entry")
        val converted = ir.fptosi(params[0], Type.I32)
        assertEquals(Type.I32, converted.type)
        ir.ret(converted)
        ir.finalizeFunction()

        val inst = ir.build().functions[0].blocks[0].instructions[0]
        assertTrue(inst is FPToSI)
    }

    @Test
    fun `sitofp instruction`() {
        val ir = ModuleBuilder("test")
        val params = ir.createFunction("f", listOf(Param("x", Type.I32)), Type.F64)
        ir.appendBlock("entry")
        val converted = ir.sitofp(params[0], Type.F64)
        assertEquals(Type.F64, converted.type)
        ir.ret(converted)
        ir.finalizeFunction()

        val inst = ir.build().functions[0].blocks[0].instructions[0]
        assertTrue(inst is SIToFP)
    }

    @Test
    fun `bitcast instruction`() {
        val ir = ModuleBuilder("test")
        val params = ir.createFunction("f", listOf(Param("x", Type.I32)), Type.F32)
        ir.appendBlock("entry")
        val cast = ir.bitcast(params[0], Type.F32)
        assertEquals(Type.F32, cast.type)
        ir.ret(cast)
        ir.finalizeFunction()

        val inst = ir.build().functions[0].blocks[0].instructions[0]
        assertTrue(inst is BitCast)
    }

    // --- Multiple functions ---

    @Test
    fun `multiple functions in one module`() {
        val ir = ModuleBuilder("test")

        ir.createFunction("f1", emptyList(), Type.I32)
        ir.appendBlock("entry")
        ir.ret(Constant.I32(1))
        ir.finalizeFunction()

        ir.createFunction("f2", emptyList(), Type.I32)
        ir.appendBlock("entry")
        ir.ret(Constant.I32(2))
        ir.finalizeFunction()

        ir.createFunction("f3", emptyList(), Type.Void)
        ir.appendBlock("entry")
        ir.ret()
        ir.finalizeFunction()

        val mod = ir.build()
        assertEquals(3, mod.functions.size)
        assertEquals("f1", mod.functions[0].name)
        assertEquals("f2", mod.functions[1].name)
        assertEquals("f3", mod.functions[2].name)
    }

    // --- Globals ---

    @Test
    fun `global with constant flag`() {
        val ir = ModuleBuilder("test")
        ir.addGlobal("PI", Type.F64, Constant.F64(3.14159), isConstant = true)

        val mod = ir.build()
        assertEquals(1, mod.globals.size)
        assertTrue(mod.globals[0].isConstant)
        assertEquals(Type.F64, mod.globals[0].type)
    }

    @Test
    fun `multiple globals`() {
        val ir = ModuleBuilder("test")
        ir.addGlobal("a", Type.I32, Constant.I32(0))
        ir.addGlobal("b", Type.I64, Constant.I64(0L))
        ir.addGlobal("c", Type.F64, Constant.F64(0.0))

        val mod = ir.build()
        assertEquals(3, mod.globals.size)
    }

    // --- Unreachable ---

    @Test
    fun `unreachable instruction`() {
        val ir = ModuleBuilder("test")
        ir.createFunction("f", emptyList(), Type.Void)
        ir.appendBlock("entry")
        ir.unreachable()
        ir.finalizeFunction()

        val inst = ir.build().functions[0].blocks[0].instructions[0]
        assertTrue(inst is Unreachable)
    }

    // --- Error cases ---

    @Test
    fun `emit without insertion point fails`() {
        val ir = ModuleBuilder("test")
        ir.createFunction("f", emptyList(), Type.Void)
        // No positionAtEnd call
        assertThrows(IllegalStateException::class.java) {
            ir.ret()
        }
    }

    // --- Add with flags ---

    @Test
    fun `add with nuw nsw flags`() {
        val ir = ModuleBuilder("test")
        val params = ir.createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val result = ir.add(params[0], params[1], nuw = true, nsw = true)
        ir.ret(result)
        ir.finalizeFunction()

        val inst = ir.build().functions[0].blocks[0].instructions[0] as Add
        assertTrue(inst.nuw)
        assertTrue(inst.nsw)
    }

    // --- Complex control flow ---

    @Test
    fun `loop pattern with blocks`() {
        val ir = ModuleBuilder("test")
        val params = ir.createFunction("sum", listOf(Param("n", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val sumPtr = ir.alloca(Type.I32)
        ir.store(Constant.I32(0), sumPtr)
        val iPtr = ir.alloca(Type.I32)
        ir.store(Constant.I32(0), iPtr)
        ir.br(BlockRef("loop.cond"))

        ir.appendBlock("loop.cond")
        val i = ir.load(Type.I32, iPtr)
        val cmp = ir.icmp(ICmpPredicate.SLT, i, params[0])
        ir.condBr(cmp, BlockRef("loop.body"), BlockRef("loop.exit"))

        ir.appendBlock("loop.body")
        val currentSum = ir.load(Type.I32, sumPtr)
        val currentI = ir.load(Type.I32, iPtr)
        val newSum = ir.add(currentSum, currentI)
        ir.store(newSum, sumPtr)
        val nextI = ir.add(currentI, Constant.I32(1))
        ir.store(nextI, iPtr)
        ir.br(BlockRef("loop.cond"))

        ir.appendBlock("loop.exit")
        val finalSum = ir.load(Type.I32, sumPtr)
        ir.ret(finalSum)

        ir.finalizeFunction()
        val mod = ir.build()
        assertEquals(4, mod.functions[0].blocks.size)
    }

    // --- Chained arithmetic ---

    @Test
    fun `complex expression chain`() {
        val ir = ModuleBuilder("test")
        val params = ir.createFunction("f", listOf(
            Param("a", Type.I32), Param("b", Type.I32), Param("c", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        // (a + b) * c - (a % b)
        val sum = ir.add(params[0], params[1])
        val prod = ir.mul(sum, params[2])
        val rem = ir.srem(params[0], params[1])
        val result = ir.sub(prod, rem)
        ir.ret(result)
        ir.finalizeFunction()

        val mod = ir.build()
        val instrs = mod.functions[0].blocks[0].instructions
        assertEquals(5, instrs.size) // add, mul, srem, sub, ret
    }

    // --- I64 types ---

    @Test
    fun `i64 arithmetic`() {
        val ir = ModuleBuilder("test")
        val params = ir.createFunction("f", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
        ir.appendBlock("entry")
        val sum = ir.add(params[0], params[1])
        assertEquals(Type.I64, sum.type)
        val diff = ir.sub(sum, Constant.I64(1L))
        assertEquals(Type.I64, diff.type)
        ir.ret(diff)
        ir.finalizeFunction()
    }

    // --- Param access ---

    @Test
    fun `param access by index`() {
        val ir = ModuleBuilder("test")
        val params = ir.createFunction("f", listOf(
            Param("x", Type.I32), Param("y", Type.F64)), Type.Void)
        ir.appendBlock("entry")

        assertEquals("x", ir.param(0).name)
        assertEquals(Type.I32, ir.param(0).type)
        assertEquals("y", ir.param(1).name)
        assertEquals(Type.F64, ir.param(1).type)
        assertEquals(2, ir.paramCount)

        ir.ret()
        ir.finalizeFunction()
    }

    // --- Block management ---

    @Test
    fun `getInsertBlock returns current block`() {
        val ir = ModuleBuilder("test")
        ir.createFunction("f", emptyList(), Type.Void)
        assertNull(ir.getInsertBlock())
        ir.appendBlock("entry")
        assertEquals("entry", ir.getInsertBlock())
        ir.appendBlock("other")
        assertEquals("other", ir.getInsertBlock())
        ir.ret()
        ir.finalizeFunction()
    }

    // --- Interface and Enum ---

    @Test
    fun `add interface`() {
        val ir = ModuleBuilder("test")
        val iface = InterfaceDefinition("Comparable", methods = listOf(
            MethodDefinition("compareTo", listOf(Param("other", Type.ClassRef("Object"))), Type.I32)
        ))
        ir.addInterface(iface)

        val mod = ir.build()
        assertEquals(1, mod.interfaces.size)
        assertEquals("Comparable", mod.interfaces[0].name)
    }

    @Test
    fun `add enum`() {
        val ir = ModuleBuilder("test")
        val enumDef = EnumDefinition("Color", listOf(
            EnumVariant("RED", 0), EnumVariant("GREEN", 1), EnumVariant("BLUE", 2)
        ))
        ir.addEnum(enumDef)

        val mod = ir.build()
        assertEquals(1, mod.enums.size)
        assertEquals("Color", mod.enums[0].name)
        assertEquals(3, mod.enums[0].variants.size)
    }

    // --- Type alias ---

    @Test
    fun `add type alias`() {
        val ir = ModuleBuilder("test")
        ir.addTypeAlias("IntPtr", Type.Pointer(Type.I32))

        val mod = ir.build()
        assertEquals(1, mod.aliases.size)
        assertEquals("IntPtr", mod.aliases[0].name)
    }

    // --- Metadata ---

    @Test
    fun `add metadata`() {
        val ir = ModuleBuilder("test")
        ir.addMetadata("version", MetadataValue.StringMD("1.0"))

        val mod = ir.build()
        assertTrue(mod.metadata.containsKey("version"))
    }
}
