package org.kgen.target.wasm.codegen

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.ir.*
import org.kgen.ir.instructions.*
import org.kgen.ir.build.*
import org.kgen.ir.target.Target

class WasmControlFlowTest {

    private val gen = WasmCodeGenerator()

    private fun assertValidWasm(wasm: ByteArray) {
        assertTrue(wasm.size > 8, "WASM binary too small")
        assertEquals(0x00, wasm[0].toInt() and 0xFF)
        assertEquals(0x61, wasm[1].toInt() and 0xFF)
        assertEquals(0x73, wasm[2].toInt() and 0xFF)
        assertEquals(0x6D, wasm[3].toInt() and 0xFF)
    }

    // ── Unconditional branch ───────────────────────────────────────────

    @Test
    fun `br to next block falls through`() {
        val ir = IrBuilder("test", Target.wasm())
        val params = ir.createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        ir.br(BlockRef("exit"))
        ir.appendBlock("exit")
        ir.ret(params[0])
        ir.finalizeFunction()
        assertValidWasm(gen.generate(ir.build()))
    }

    @Test
    fun `br forward skip`() {
        val ir = IrBuilder("test", Target.wasm())
        val params = ir.createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        ir.br(BlockRef("exit"))
        ir.appendBlock("middle")
        val sum = ir.add(params[0], Constant.I32(1))
        ir.ret(sum)
        ir.appendBlock("exit")
        ir.ret(params[0])
        ir.finalizeFunction()
        assertValidWasm(gen.generate(ir.build()))
    }

    // ── Conditional branch ─────────────────────────────────────────────

    @Test
    fun `condbr simple if-then`() {
        val ir = IrBuilder("test", Target.wasm())
        val params = ir.createFunction("abs", listOf(Param("x", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val isNeg = ir.icmp(ICmpPredicate.SLT, params[0], Constant.I32(0))
        ir.condBr(isNeg, BlockRef("negate"), BlockRef("done"))
        ir.appendBlock("negate")
        val neg = ir.sub(Constant.I32(0), params[0])
        ir.ret(neg)
        ir.appendBlock("done")
        ir.ret(params[0])
        ir.finalizeFunction()
        assertValidWasm(gen.generate(ir.build()))
    }

    @Test
    fun `condbr if-then-else diamond`() {
        val ir = IrBuilder("test", Target.wasm())
        val params = ir.createFunction("max", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val cmp = ir.icmp(ICmpPredicate.SGT, params[0], params[1])
        ir.condBr(cmp, BlockRef("then"), BlockRef("else"))
        ir.appendBlock("then")
        ir.br(BlockRef("merge"))
        ir.appendBlock("else")
        ir.br(BlockRef("merge"))
        ir.appendBlock("merge")
        val phi = ir.phi(Type.I32, listOf(params[0] to BlockRef("then"), params[1] to BlockRef("else")))
        ir.ret(phi)
        ir.finalizeFunction()
        assertValidWasm(gen.generate(ir.build()))
    }

    @Test
    fun `condbr with both targets same`() {
        val ir = IrBuilder("test", Target.wasm())
        val params = ir.createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val cmp = ir.icmp(ICmpPredicate.EQ, params[0], Constant.I32(0))
        ir.condBr(cmp, BlockRef("done"), BlockRef("done"))
        ir.appendBlock("done")
        ir.ret(params[0])
        ir.finalizeFunction()
        assertValidWasm(gen.generate(ir.build()))
    }

    @Test
    fun `condbr neither target is next block`() {
        val ir = IrBuilder("test", Target.wasm())
        val params = ir.createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val cmp = ir.icmp(ICmpPredicate.SGT, params[0], Constant.I32(0))
        ir.condBr(cmp, BlockRef("positive"), BlockRef("negative"))
        ir.appendBlock("dead")
        ir.ret(Constant.I32(-1))
        ir.appendBlock("positive")
        ir.ret(Constant.I32(1))
        ir.appendBlock("negative")
        ir.ret(Constant.I32(0))
        ir.finalizeFunction()
        assertValidWasm(gen.generate(ir.build()))
    }

    // ── Loops (constructed directly to allow forward-referenced phi values) ──

    private fun buildLoopModule(fnName: String, params: List<Parameter>, retType: Type, blocks: List<BasicBlock>): Module =
        Module(name = "test", targetTriple = "wasm32-unknown-unknown",
            functions = listOf(IrFunction(fnName, params, retType, blocks)))

    @Test
    fun `simple while loop`() {
        val n = Parameter("n", Type.I32, 0)
        val i = InstructionRef("%i", Type.I32)
        val sum = InstructionRef("%sum", Type.I32)
        val iNext = InstructionRef("%i_next", Type.I32)
        val sumNext = InstructionRef("%sum_next", Type.I32)
        val cond = InstructionRef("%cond", Type.I32)

        val module = buildLoopModule("sumTo", listOf(n), Type.I32, listOf(
            BasicBlock("entry", listOf(Br(BlockRef("loop")))),
            BasicBlock("loop", listOf(
                Phi(i, listOf(Constant.I32(0) to BlockRef("entry"), iNext to BlockRef("body"))),
                Phi(sum, listOf(Constant.I32(0) to BlockRef("entry"), sumNext to BlockRef("body"))),
                ICmp(cond, ICmpPredicate.SLT, i, n),
                CondBr(cond, BlockRef("body"), BlockRef("exit")),
            )),
            BasicBlock("body", listOf(
                Add(sumNext, sum, i),
                Add(iNext, i, Constant.I32(1)),
                Br(BlockRef("loop")),
            )),
            BasicBlock("exit", listOf(Ret(sum))),
        ))
        assertValidWasm(gen.generate(module))
    }

    @Test
    fun `countdown loop`() {
        val n = Parameter("n", Type.I32, 0)
        val count = InstructionRef("%count", Type.I32)
        val next = InstructionRef("%next", Type.I32)
        val done = InstructionRef("%done", Type.I32)

        val module = buildLoopModule("countdown", listOf(n), Type.I32, listOf(
            BasicBlock("entry", listOf(Br(BlockRef("header")))),
            BasicBlock("header", listOf(
                Phi(count, listOf(n to BlockRef("entry"), next to BlockRef("body"))),
                ICmp(done, ICmpPredicate.SLE, count, Constant.I32(0)),
                CondBr(done, BlockRef("exit"), BlockRef("body")),
            )),
            BasicBlock("body", listOf(
                Sub(next, count, Constant.I32(1)),
                Br(BlockRef("header")),
            )),
            BasicBlock("exit", listOf(Ret(count))),
        ))
        assertValidWasm(gen.generate(module))
    }

    @Test
    fun `loop with conditional exit`() {
        val start = Parameter("start", Type.I32, 0)
        val v = InstructionRef("%v", Type.I32)
        val dec = InstructionRef("%dec", Type.I32)
        val isZero = InstructionRef("%isZero", Type.I32)

        val module = buildLoopModule("findZero", listOf(start), Type.I32, listOf(
            BasicBlock("entry", listOf(Br(BlockRef("loop")))),
            BasicBlock("loop", listOf(
                Phi(v, listOf(start to BlockRef("entry"), dec to BlockRef("continue"))),
                ICmp(isZero, ICmpPredicate.EQ, v, Constant.I32(0)),
                CondBr(isZero, BlockRef("done"), BlockRef("continue")),
            )),
            BasicBlock("continue", listOf(
                Sub(dec, v, Constant.I32(1)),
                Br(BlockRef("loop")),
            )),
            BasicBlock("done", listOf(Ret(v))),
        ))
        assertValidWasm(gen.generate(module))
    }

    // ── Switch ─────────────────────────────────────────────────────────

    @Test
    fun `switch with multiple cases`() {
        val ir = IrBuilder("test", Target.wasm())
        val params = ir.createFunction("categorize", listOf(Param("x", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        ir.switch(params[0], "default", listOf(
            Constant.I32(0) to "zero",
            Constant.I32(1) to "one",
            Constant.I32(2) to "two",
        ))
        ir.appendBlock("zero")
        ir.ret(Constant.I32(10))
        ir.appendBlock("one")
        ir.ret(Constant.I32(20))
        ir.appendBlock("two")
        ir.ret(Constant.I32(30))
        ir.appendBlock("default")
        ir.ret(Constant.I32(-1))
        ir.finalizeFunction()
        assertValidWasm(gen.generate(ir.build()))
    }

    @Test
    fun `nested conditionals with merge`() {
        val ir = IrBuilder("test", Target.wasm())
        val params = ir.createFunction("toSign", listOf(Param("x", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val cmp = ir.icmp(ICmpPredicate.SGT, params[0], Constant.I32(0))
        ir.condBr(cmp, BlockRef("pos"), BlockRef("nonPos"))

        ir.appendBlock("pos")
        ir.br(BlockRef("done"))

        ir.appendBlock("nonPos")
        val cmpZero = ir.icmp(ICmpPredicate.EQ, params[0], Constant.I32(0))
        ir.condBr(cmpZero, BlockRef("zero"), BlockRef("neg"))

        ir.appendBlock("zero")
        ir.br(BlockRef("done"))

        ir.appendBlock("neg")
        ir.br(BlockRef("done"))

        ir.appendBlock("done")
        val result = ir.phi(Type.I32, listOf(
            Constant.I32(1) to "pos",
            Constant.I32(0) to "zero",
            Constant.I32(-1) to "neg",
        ))
        ir.ret(result)
        ir.finalizeFunction()
        assertValidWasm(gen.generate(ir.build()))
    }

    // ── Phi nodes ──────────────────────────────────────────────────────

    @Test
    fun `phi with two incoming values`() {
        val ir = IrBuilder("test", Target.wasm())
        val params = ir.createFunction("pick", listOf(Param("cond", Type.I32), Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val isTrue = ir.icmp(ICmpPredicate.NE, params[0], Constant.I32(0))
        ir.condBr(isTrue, BlockRef("left"), BlockRef("right"))
        ir.appendBlock("left")
        ir.br(BlockRef("merge"))
        ir.appendBlock("right")
        ir.br(BlockRef("merge"))
        ir.appendBlock("merge")
        val result = ir.phi(Type.I32, listOf(params[1] to BlockRef("left"), params[2] to BlockRef("right")))
        ir.ret(result)
        ir.finalizeFunction()
        assertValidWasm(gen.generate(ir.build()))
    }

    @Test
    fun `phi in loop header`() {
        val acc = InstructionRef("%acc", Type.I32)
        val next = InstructionRef("%next", Type.I32)
        val done = InstructionRef("%done", Type.I32)

        val module = buildLoopModule("loopPhi", emptyList(), Type.I32, listOf(
            BasicBlock("entry", listOf(Br(BlockRef("loop")))),
            BasicBlock("loop", listOf(
                Phi(acc, listOf(Constant.I32(0) to BlockRef("entry"), next to BlockRef("loop"))),
                Add(next, acc, Constant.I32(1)),
                ICmp(done, ICmpPredicate.SGE, next, Constant.I32(10)),
                CondBr(done, BlockRef("exit"), BlockRef("loop")),
            )),
            BasicBlock("exit", listOf(Ret(acc))),
        ))
        assertValidWasm(gen.generate(module))
    }

    // ── Alloca ──────────────────────────────────────────────────────────

    @Test
    fun `alloca basic`() {
        val ir = IrBuilder("test", Target.wasm())
        ir.createFunction("f", emptyList(), Type.I32)
        ir.appendBlock("entry")
        val ptr = ir.alloca(Type.I32)
        ir.store(Constant.I32(42), ptr)
        val loaded = ir.load(Type.I32, ptr)
        ir.ret(loaded)
        ir.finalizeFunction()
        assertValidWasm(gen.generate(ir.build()))
    }

    @Test
    fun `alloca with control flow`() {
        val ir = IrBuilder("test", Target.wasm())
        val params = ir.createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val ptr = ir.alloca(Type.I32)
        ir.store(params[0], ptr)
        val cmp = ir.icmp(ICmpPredicate.SGT, params[0], Constant.I32(0))
        ir.condBr(cmp, BlockRef("then"), BlockRef("else"))

        ir.appendBlock("then")
        ir.store(Constant.I32(1), ptr)
        ir.br(BlockRef("done"))

        ir.appendBlock("else")
        ir.store(Constant.I32(0), ptr)
        ir.br(BlockRef("done"))

        ir.appendBlock("done")
        val result = ir.load(Type.I32, ptr)
        ir.ret(result)
        ir.finalizeFunction()
        assertValidWasm(gen.generate(ir.build()))
    }

    // ── Complex patterns ───────────────────────────────────────────────

    @Test
    fun `nested if-else`() {
        val ir = IrBuilder("test", Target.wasm())
        val params = ir.createFunction("classify", listOf(Param("x", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val isPos = ir.icmp(ICmpPredicate.SGT, params[0], Constant.I32(0))
        ir.condBr(isPos, BlockRef("positive"), BlockRef("nonpos"))

        ir.appendBlock("positive")
        val isBig = ir.icmp(ICmpPredicate.SGT, params[0], Constant.I32(100))
        ir.condBr(isBig, BlockRef("big"), BlockRef("small"))

        ir.appendBlock("big")
        ir.ret(Constant.I32(3))
        ir.appendBlock("small")
        ir.ret(Constant.I32(2))

        ir.appendBlock("nonpos")
        val isZero = ir.icmp(ICmpPredicate.EQ, params[0], Constant.I32(0))
        ir.condBr(isZero, BlockRef("zero"), BlockRef("negative"))

        ir.appendBlock("zero")
        ir.ret(Constant.I32(0))
        ir.appendBlock("negative")
        ir.ret(Constant.I32(1))
        ir.finalizeFunction()
        assertValidWasm(gen.generate(ir.build()))
    }

    @Test
    fun `loop with nested conditional`() {
        val n = Parameter("n", Type.I32, 0)
        val i = InstructionRef("%i", Type.I32)
        val sum = InstructionRef("%sum", Type.I32)
        val iNext = InstructionRef("%i_next", Type.I32)
        val sumNext = InstructionRef("%sum_next", Type.I32)
        val loopCond = InstructionRef("%loopCond", Type.I32)
        val isPos = InstructionRef("%isPos", Type.I32)
        val added = InstructionRef("%added", Type.I32)

        val module = buildLoopModule("sumPositive", listOf(n), Type.I32, listOf(
            BasicBlock("entry", listOf(Br(BlockRef("header")))),
            BasicBlock("header", listOf(
                Phi(i, listOf(Constant.I32(0) to BlockRef("entry"), iNext to BlockRef("inc"))),
                Phi(sum, listOf(Constant.I32(0) to BlockRef("entry"), sumNext to BlockRef("inc"))),
                ICmp(loopCond, ICmpPredicate.SLT, i, n),
                CondBr(loopCond, BlockRef("body"), BlockRef("exit")),
            )),
            BasicBlock("body", listOf(
                ICmp(isPos, ICmpPredicate.SGT, i, Constant.I32(0)),
                CondBr(isPos, BlockRef("add"), BlockRef("skip")),
            )),
            BasicBlock("add", listOf(
                Add(added, sum, i),
                Br(BlockRef("inc")),
            )),
            BasicBlock("skip", listOf(Br(BlockRef("inc")))),
            BasicBlock("inc", listOf(
                Phi(sumNext, listOf(added to BlockRef("add"), sum to BlockRef("skip"))),
                Add(iNext, i, Constant.I32(1)),
                Br(BlockRef("header")),
            )),
            BasicBlock("exit", listOf(Ret(sum))),
        ))
        assertValidWasm(gen.generate(module))
    }

    @Test
    fun `multiple returns in different blocks`() {
        val ir = IrBuilder("test", Target.wasm())
        val params = ir.createFunction("early", listOf(Param("x", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val isZero = ir.icmp(ICmpPredicate.EQ, params[0], Constant.I32(0))
        ir.condBr(isZero, BlockRef("retZero"), BlockRef("check"))

        ir.appendBlock("retZero")
        ir.ret(Constant.I32(0))

        ir.appendBlock("check")
        val isOne = ir.icmp(ICmpPredicate.EQ, params[0], Constant.I32(1))
        ir.condBr(isOne, BlockRef("retOne"), BlockRef("retDefault"))

        ir.appendBlock("retOne")
        ir.ret(Constant.I32(1))

        ir.appendBlock("retDefault")
        ir.ret(Constant.I32(-1))
        ir.finalizeFunction()
        assertValidWasm(gen.generate(ir.build()))
    }

    @Test
    fun `fibonacci iterative`() {
        val n = Parameter("n", Type.I32, 0)
        val a = InstructionRef("%a", Type.I32)
        val b = InstructionRef("%b", Type.I32)
        val i = InstructionRef("%i", Type.I32)
        val sum = InstructionRef("%sum", Type.I32)
        val bVal = InstructionRef("%b_val", Type.I32)
        val iNext = InstructionRef("%i_next", Type.I32)
        val cond = InstructionRef("%cond", Type.I32)

        val module = buildLoopModule("fib", listOf(n), Type.I32, listOf(
            BasicBlock("entry", listOf(Br(BlockRef("loop")))),
            BasicBlock("loop", listOf(
                Phi(a, listOf(Constant.I32(0) to BlockRef("entry"), bVal to BlockRef("body"))),
                Phi(b, listOf(Constant.I32(1) to BlockRef("entry"), sum to BlockRef("body"))),
                Phi(i, listOf(Constant.I32(0) to BlockRef("entry"), iNext to BlockRef("body"))),
                ICmp(cond, ICmpPredicate.SLT, i, n),
                CondBr(cond, BlockRef("body"), BlockRef("done")),
            )),
            BasicBlock("body", listOf(
                Add(sum, a, b),
                Add(bVal, b, Constant.I32(0)),
                Add(iNext, i, Constant.I32(1)),
                Br(BlockRef("loop")),
            )),
            BasicBlock("done", listOf(Ret(a))),
        ))
        assertValidWasm(gen.generate(module))
    }

    // ── Stackifier edge cases ──────────────────────────────────────────

    @Test
    fun `stackifier orders blocks correctly`() {
        val ir = IrBuilder("test", Target.wasm())
        ir.createFunction("f", emptyList(), Type.I32)
        ir.appendBlock("entry")
        ir.br(BlockRef("a"))
        ir.appendBlock("a")
        ir.br(BlockRef("b"))
        ir.appendBlock("b")
        ir.ret(Constant.I32(0))
        ir.finalizeFunction()

        val fn = ir.build().functions.first { it.name == "f" }
        val stackifier = WasmStackifier(fn)
        val ordered = stackifier.analyze()
        assertEquals(listOf("entry", "a", "b"), ordered.map { it.label })
    }

    @Test
    fun `stackifier identifies loop headers`() {
        val ir = IrBuilder("test", Target.wasm())
        ir.createFunction("f", emptyList(), Type.I32)
        ir.appendBlock("entry")
        ir.br(BlockRef("header"))
        ir.appendBlock("header")
        val cond = ir.icmp(ICmpPredicate.EQ, Constant.I32(1), Constant.I32(0))
        ir.condBr(cond, BlockRef("exit"), BlockRef("body"))
        ir.appendBlock("body")
        ir.br(BlockRef("header"))
        ir.appendBlock("exit")
        ir.ret(Constant.I32(0))
        ir.finalizeFunction()

        val fn = ir.build().functions.first { it.name == "f" }
        val stackifier = WasmStackifier(fn)
        stackifier.analyze()
        assertTrue(stackifier.loopHeaders.contains("header"))
    }
}
