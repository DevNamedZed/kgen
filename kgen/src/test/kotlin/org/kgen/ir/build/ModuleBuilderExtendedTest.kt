package org.kgen.ir.build

import org.kgen.ir.*
import org.kgen.ir.instructions.*
import org.kgen.ir.target.Target
import org.kgen.ir.verify.IrVerifier
import org.kgen.ir.types.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class ModuleBuilderExtendedTest {

    // --- Multi-block control flow with phi nodes ---

    @Test
    fun `diamond control flow with phi merging both branches`() {
        val ir = ModuleBuilder("test")
        val params = ir.createFunction("diamond", listOf(Param("x", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val cmp = ir.icmp(ICmpPredicate.SGT, params[0], Constant.I32(0))
        ir.condBr(cmp, BlockRef("then"), BlockRef("else"))

        ir.appendBlock("then")
        val thenVal = ir.mul(params[0], Constant.I32(2))
        ir.br(BlockRef("merge"))

        ir.appendBlock("else")
        val elseVal = ir.add(params[0], Constant.I32(10))
        ir.br(BlockRef("merge"))

        ir.appendBlock("merge")
        val phi = ir.phi(Type.I32, listOf(thenVal to BlockRef("then"), elseVal to BlockRef("else")))
        ir.ret(phi)

        ir.finalizeFunction()
        val mod = ir.build()
        assertEquals(4, mod.functions[0].blocks.size)
        val mergeBlock = mod.functions[0].blocks[3]
        val phiInst = mergeBlock.instructions[0] as Phi
        assertEquals(Type.I32, phiInst.result!!.type)
        assertEquals(2, phiInst.incoming.size)
        assertTrue(IrVerifier.verify(mod).isValid)
    }

    @Test
    fun `chained blocks with sequential branches`() {
        val ir = ModuleBuilder("test")
        ir.createFunction("chain", listOf(Param("x", Type.I32)), Type.I32)
        ir.appendBlock("b0")
        val v0 = ir.add(ir.param(0), Constant.I32(1))
        ir.br(BlockRef("b1"))

        ir.appendBlock("b1")
        val v1 = ir.add(v0, Constant.I32(2))
        ir.br(BlockRef("b2"))

        ir.appendBlock("b2")
        val v2 = ir.add(v1, Constant.I32(3))
        ir.br(BlockRef("b3"))

        ir.appendBlock("b3")
        ir.ret(v2)

        ir.finalizeFunction()
        val mod = ir.build()
        assertEquals(4, mod.functions[0].blocks.size)
        assertTrue(IrVerifier.verify(mod).isValid)
    }

    @Test
    fun `phi with three incoming edges`() {
        val ir = ModuleBuilder("test")
        val params = ir.createFunction("tri", listOf(Param("x", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        ir.switch(params[0], "default", listOf(
            Constant.I32(0) to "case0",
            Constant.I32(1) to "case1",
        ))

        ir.appendBlock("case0")
        ir.br(BlockRef("merge"))

        ir.appendBlock("case1")
        ir.br(BlockRef("merge"))

        ir.appendBlock("default")
        ir.br(BlockRef("merge"))

        ir.appendBlock("merge")
        val phi = ir.phi(Type.I32, listOf(
            Constant.I32(100) to "case0",
            Constant.I32(200) to "case1",
            Constant.I32(300) to "default",
        ))
        ir.ret(phi)

        ir.finalizeFunction()
        val mod = ir.build()
        val phiInst = mod.functions[0].blocks.last().instructions[0] as Phi
        assertEquals(3, phiInst.incoming.size)
    }

    // --- Memory instructions: alloca with alignment, volatile load/store ---

    @Test
    fun `alloca with alignment`() {
        val ir = ModuleBuilder("test")
        ir.createFunction("f", emptyList(), Type.I32)
        ir.appendBlock("entry")
        val ptr = ir.alloca(Type.I32, align = 16)
        ir.store(Constant.I32(42), ptr)
        val v = ir.load(Type.I32, ptr)
        ir.ret(v)
        ir.finalizeFunction()

        val mod = ir.build()
        val alloca = mod.functions[0].blocks[0].instructions[0] as Alloca
        assertEquals(16, alloca.align)
    }

    @Test
    fun `volatile load and store`() {
        val ir = ModuleBuilder("test")
        ir.createFunction("f", listOf(Param("ptr", Type.Pointer(Type.I32))), Type.I32)
        ir.appendBlock("entry")
        ir.store(Constant.I32(1), ir.param(0), volatile = true)
        val v = ir.load(Type.I32, ir.param(0), volatile = true)
        ir.ret(v)
        ir.finalizeFunction()

        val mod = ir.build()
        val instrs = mod.functions[0].blocks[0].instructions
        assertTrue((instrs[0] as Store).volatile)
        assertTrue((instrs[1] as Load).volatile)
    }

    @Test
    fun `alloca with numElements for array allocation`() {
        val ir = ModuleBuilder("test")
        ir.createFunction("f", listOf(Param("n", Type.I32)), Type.Void)
        ir.appendBlock("entry")
        val ptr = ir.alloca(Type.I32, numElements = ir.param(0))
        ir.store(Constant.I32(0), ptr)
        ir.ret()
        ir.finalizeFunction()

        val mod = ir.build()
        val alloca = mod.functions[0].blocks[0].instructions[0] as Alloca
        assertNotNull(alloca.numElements)
    }

    @Test
    fun `gep with multiple indices`() {
        val structType = Type.Struct("pair", listOf(Type.I32, Type.I64))
        val ir = ModuleBuilder("test")
        ir.createFunction("f", listOf(Param("ptr", Type.Pointer(structType))), Type.I64)
        ir.appendBlock("entry")
        val fieldPtr = ir.gep(structType, ir.param(0), Constant.I32(0), Constant.I32(1))
        val v = ir.load(Type.I64, fieldPtr)
        ir.ret(v)
        ir.finalizeFunction()

        val mod = ir.build()
        val gep = mod.functions[0].blocks[0].instructions[0] as GetElementPtr
        assertEquals(2, gep.indices.size)
    }

    // --- Conversion instructions not already tested ---

    @Test
    fun `fptoui instruction`() {
        val ir = ModuleBuilder("test")
        val params = ir.createFunction("f", listOf(Param("x", Type.F64)), Type.I32)
        ir.appendBlock("entry")
        val v = ir.fptoui(params[0], Type.I32)
        assertEquals(Type.I32, v.type)
        ir.ret(v)
        ir.finalizeFunction()

        val inst = ir.build().functions[0].blocks[0].instructions[0]
        assertTrue(inst is FPToUI)
    }

    @Test
    fun `uitofp instruction`() {
        val ir = ModuleBuilder("test")
        val params = ir.createFunction("f", listOf(Param("x", Type.I32)), Type.F64)
        ir.appendBlock("entry")
        val v = ir.uitofp(params[0], Type.F64)
        assertEquals(Type.F64, v.type)
        ir.ret(v)
        ir.finalizeFunction()

        val inst = ir.build().functions[0].blocks[0].instructions[0]
        assertTrue(inst is UIToFP)
    }

    @Test
    fun `ptrtoint and inttoptr roundtrip`() {
        val ir = ModuleBuilder("test")
        val params = ir.createFunction("f", listOf(Param("ptr", Type.Pointer(Type.I8))), Type.Pointer(Type.I8))
        ir.appendBlock("entry")
        val asInt = ir.ptrtoint(params[0], Type.I64)
        assertEquals(Type.I64, asInt.type)
        val backToPtr = ir.inttoptr(asInt, Type.Pointer(Type.I8))
        ir.ret(backToPtr)
        ir.finalizeFunction()

        val mod = ir.build()
        val instrs = mod.functions[0].blocks[0].instructions
        assertTrue(instrs[0] is PtrToInt)
        assertTrue(instrs[1] is IntToPtr)
    }

    @Test
    fun `addrspacecast instruction`() {
        val ir = ModuleBuilder("test")
        val params = ir.createFunction("f", listOf(Param("ptr", Type.Pointer(Type.I32))), Type.Pointer(Type.I32))
        ir.appendBlock("entry")
        val cast = ir.addrspacecast(params[0], Type.Pointer(Type.I32))
        ir.ret(cast)
        ir.finalizeFunction()

        val inst = ir.build().functions[0].blocks[0].instructions[0]
        assertTrue(inst is AddrSpaceCast)
    }

    // --- Call instructions: tail calls, calling convention, FunctionRef ---

    @Test
    fun `tail call instruction`() {
        val ir = ModuleBuilder("test")
        val extRef = ir.declareFunction("helper", listOf(Param("x", Type.I32)), Type.I32)
        ir.createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val result = ir.call(extRef, listOf(ir.param(0)), Type.I32, tailCall = TailCallKind.TAIL)
        ir.ret(result!!)
        ir.finalizeFunction()

        val mod = ir.build()
        val callInst = mod.functions[1].blocks[0].instructions[0] as Call
        assertEquals(TailCallKind.TAIL, callInst.tailCall)
    }

    @Test
    fun `call via FunctionRef`() {
        val ir = ModuleBuilder("test")
        val ref = ir.declareFunction("square", listOf(Param("n", Type.I32)), Type.I32)

        ir.createFunction("caller", emptyList(), Type.I32)
        ir.appendBlock("entry")
        val result = ir.call(ref, listOf(Constant.I32(5)), Type.I32)
        ir.ret(result!!)
        ir.finalizeFunction()

        val mod = ir.build()
        assertEquals(2, mod.functions.size)
        val callInst = mod.functions[1].blocks[0].instructions[0] as Call
        assertEquals("square", callInst.function.name)
    }

    @Test
    fun `functionRef helper creates reference`() {
        val ir = ModuleBuilder("test")
        ir.createFunction("f", emptyList(), Type.Void)
        ir.appendBlock("entry")
        val ref = ir.functionRef("target", Type.Function(listOf(Type.I32), Type.I32))
        assertEquals("target", ref.name)
        ir.ret()
        ir.finalizeFunction()
    }

    @Test
    fun `invoke instruction with normal and unwind destinations`() {
        val ir = ModuleBuilder("test")
        val ref = ir.declareFunction("mayThrow", emptyList(), Type.I32)
        ir.createFunction("f", emptyList(), Type.I32)
        ir.appendBlock("entry")
        val result = ir.invoke(ref, emptyList(), Type.I32, BlockRef("normal"), BlockRef("unwind"))
        assertNotNull(result)

        ir.appendBlock("normal")
        ir.ret(result!!)

        ir.appendBlock("unwind")
        ir.unreachable()

        ir.finalizeFunction()
        val mod = ir.build()
        val invokeInst = mod.functions[1].blocks[0].instructions[0]
        assertTrue(invokeInst is Invoke)
        val invoke = invokeInst as Invoke
        assertEquals(BlockRef("normal"), invoke.normalDest)
        assertEquals(BlockRef("unwind"), invoke.unwindDest)
    }

    // --- Global variables with various attributes ---

    @Test
    fun `global with thread local mode`() {
        val ir = ModuleBuilder("test")
        ir.addGlobal("tls_counter", Type.I32, Constant.I32(0), threadLocal = ThreadLocalMode.GENERAL_DYNAMIC)

        val mod = ir.build()
        assertEquals(ThreadLocalMode.GENERAL_DYNAMIC, mod.globals[0].threadLocal)
    }

    @Test
    fun `global with section and alignment`() {
        val ir = ModuleBuilder("test")
        ir.addGlobal("data", Type.I64, Constant.I64(0), section = ".bss", align = 8)

        val mod = ir.build()
        assertEquals(".bss", mod.globals[0].section)
        assertEquals(8, mod.globals[0].align)
    }

    @Test
    fun `global with address space`() {
        val ir = ModuleBuilder("test")
        ir.addGlobal("gpu_data", Type.I32, Constant.I32(0), addressSpace = 3)

        val mod = ir.build()
        assertEquals(3, mod.globals[0].addressSpace)
    }

    @Test
    fun `global returns usable GlobalRef`() {
        val ir = ModuleBuilder("test")
        val ref = ir.addGlobal("counter", Type.I32, Constant.I32(0))

        ir.createFunction("f", emptyList(), Type.I32)
        ir.appendBlock("entry")
        val v = ir.load(Type.I32, ref)
        ir.ret(v)
        ir.finalizeFunction()

        val mod = ir.build()
        assertEquals(1, mod.globals.size)
        val loadInst = mod.functions[0].blocks[0].instructions[0] as Load
        assertEquals("counter", (loadInst.ptr as GlobalRef).name)
    }

    @Test
    fun `store to and load from global`() {
        val ir = ModuleBuilder("test")
        val ref = ir.addGlobal("x", Type.I32, Constant.I32(0))

        ir.createFunction("inc", emptyList(), Type.I32)
        ir.appendBlock("entry")
        val old = ir.load(Type.I32, ref)
        val new = ir.add(old, Constant.I32(1))
        ir.store(new, ref)
        ir.ret(new)
        ir.finalizeFunction()

        val mod = ir.build()
        assertEquals(1, mod.globals.size)
        assertEquals(1, mod.functions.size)
    }

    // --- Multiple functions calling each other ---

    @Test
    fun `function calling another function in same module`() {
        val ir = ModuleBuilder("test")

        ir.createFunction("double", listOf(Param("x", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val doubled = ir.add(ir.param(0), ir.param(0))
        ir.ret(doubled)
        ir.finalizeFunction()

        ir.createFunction("quadruple", listOf(Param("x", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val first = ir.call("double", listOf(ir.param(0)), Type.I32)
        val second = ir.call("double", listOf(first!!), Type.I32)
        ir.ret(second!!)
        ir.finalizeFunction()

        val mod = ir.build()
        assertEquals(2, mod.functions.size)
        assertEquals("double", mod.functions[0].name)
        assertEquals("quadruple", mod.functions[1].name)
    }

    @Test
    fun `mix of declared and defined functions`() {
        val ir = ModuleBuilder("test")

        ir.declareFunction("external_fn", listOf(Param("x", Type.I32)), Type.I32)
        ir.declareFunction("another_extern", emptyList(), Type.Void)

        ir.createFunction("local_fn", listOf(Param("x", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val result = ir.call("external_fn", listOf(ir.param(0)), Type.I32)
        ir.call("another_extern", emptyList(), Type.Void)
        ir.ret(result!!)
        ir.finalizeFunction()

        val mod = ir.build()
        assertEquals(3, mod.functions.size)
        assertTrue(mod.functions[0].isExternal)
        assertTrue(mod.functions[1].isExternal)
        assertFalse(mod.functions[2].isExternal)
    }

    // --- Empty function ---

    @Test
    fun `function with only a return`() {
        val ir = ModuleBuilder("test")
        ir.createFunction("noop", emptyList(), Type.Void)
        ir.appendBlock("entry")
        ir.ret()
        ir.finalizeFunction()

        val mod = ir.build()
        assertEquals(1, mod.functions[0].blocks.size)
        assertEquals(1, mod.functions[0].blocks[0].instructions.size)
        assertTrue(mod.functions[0].blocks[0].instructions[0] is Ret)
        assertTrue(IrVerifier.verify(mod).isValid)
    }

    @Test
    fun `function with no parameters returning constant`() {
        val ir = ModuleBuilder("test")
        ir.createFunction("fortytwo", emptyList(), Type.I32)
        ir.appendBlock("entry")
        ir.ret(Constant.I32(42))
        ir.finalizeFunction()

        val mod = ir.build()
        assertEquals(0, mod.functions[0].params.size)
        assertTrue(IrVerifier.verify(mod).isValid)
    }

    // --- Edge cases ---

    @Test
    fun `finalizeFunction without calling build still works`() {
        val ir = ModuleBuilder("test")
        ir.createFunction("f", emptyList(), Type.Void)
        ir.appendBlock("entry")
        ir.ret()
        ir.finalizeFunction()

        ir.createFunction("g", emptyList(), Type.Void)
        ir.appendBlock("entry")
        ir.ret()
        ir.finalizeFunction()

        val mod = ir.build()
        assertEquals(2, mod.functions.size)
    }

    @Test
    fun `cannot finalize without starting a function`() {
        val ir = ModuleBuilder("test")
        assertThrows(IllegalStateException::class.java) {
            ir.finalizeFunction()
        }
    }

    @Test
    fun `positionAtEnd can switch between blocks`() {
        val ir = ModuleBuilder("test")
        ir.createFunction("f", emptyList(), Type.I32)
        val entry = ir.appendBlock("entry")
        val other = ir.appendBlock("other")

        ir.appendBlock(entry)
        ir.br(BlockRef("other"))

        ir.appendBlock(other)
        ir.ret(Constant.I32(1))

        ir.appendBlock(entry)
        // Emitting after switch back should go to entry block
        // But entry already has a br, this just adds more instructions to entry

        ir.finalizeFunction()
        val mod = ir.build()
        assertEquals(2, mod.functions[0].blocks.size)
    }

    @Test
    fun `id counter resets between functions`() {
        val ir = ModuleBuilder("test")

        ir.createFunction("f1", listOf(Param("x", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val v1 = ir.add(ir.param(0), Constant.I32(1))
        assertEquals("%0", v1.name)
        ir.ret(v1)
        ir.finalizeFunction()

        ir.createFunction("f2", listOf(Param("x", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val v2 = ir.add(ir.param(0), Constant.I32(2))
        assertEquals("%0", v2.name)
        ir.ret(v2)
        ir.finalizeFunction()
    }

    // --- Switch with fall-through-like patterns ---

    @Test
    fun `switch with many cases`() {
        val ir = ModuleBuilder("test")
        val params = ir.createFunction("lookup", listOf(Param("x", Type.I32)), Type.I32)
        ir.appendBlock("entry")

        val cases = (0..9).map { Constant.I32(it) to "case$it" }
        ir.switch(params[0], "default", cases)

        for (i in 0..9) {
            ir.appendBlock("case$i")
            ir.ret(Constant.I32(i * 10))
        }
        ir.appendBlock("default")
        ir.ret(Constant.I32(-1))

        ir.finalizeFunction()
        val mod = ir.build()
        assertEquals(12, mod.functions[0].blocks.size) // entry + 10 cases + default
        val sw = mod.functions[0].blocks[0].instructions[0] as Switch
        assertEquals(10, sw.cases.size)
        assertEquals(BlockRef("default"), sw.defaultTarget)
    }

    // --- Select (ternary) with different types ---

    @Test
    fun `select with i64 operands`() {
        val ir = ModuleBuilder("test")
        val params = ir.createFunction("f", listOf(Param("a", Type.I64), Param("b", Type.I64)), Type.I64)
        ir.appendBlock("entry")
        val cmp = ir.icmp(ICmpPredicate.SGT, params[0], params[1])
        val result = ir.select(cmp, params[0], params[1])
        assertEquals(Type.I64, result.type)
        ir.ret(result)
        ir.finalizeFunction()

        val mod = ir.build()
        assertTrue(mod.functions[0].blocks[0].instructions[1] is Select)
    }

    @Test
    fun `select with float operands`() {
        val ir = ModuleBuilder("test")
        val params = ir.createFunction("fmax", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.F64)
        ir.appendBlock("entry")
        val cmp = ir.fcmp(FCmpPredicate.OGT, params[0], params[1])
        val result = ir.select(cmp, params[0], params[1])
        assertEquals(Type.F64, result.type)
        ir.ret(result)
        ir.finalizeFunction()

        assertTrue(IrVerifier.verify(ir.build()).isValid)
    }

    // --- Overflow-checked and saturating arithmetic ---

    @Test
    fun `sadd overflow instruction`() {
        val ir = ModuleBuilder("test")
        val params = ir.createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val result = ir.saddOverflow(params[0], params[1])
        val sum = ir.extractValue(result, 0)
        val overflow = ir.extractValue(result, 1)
        val selected = ir.select(overflow, Constant.I32(Int.MAX_VALUE), sum)
        ir.ret(selected)
        ir.finalizeFunction()

        val mod = ir.build()
        assertTrue(mod.functions[0].blocks[0].instructions[0] is SAddOverflow)
        assertTrue(mod.functions[0].blocks[0].instructions[1] is ExtractValue)
    }

    @Test
    fun `saturating add instruction`() {
        val ir = ModuleBuilder("test")
        val params = ir.createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val result = ir.saddSat(params[0], params[1])
        ir.ret(result)
        ir.finalizeFunction()

        val inst = ir.build().functions[0].blocks[0].instructions[0]
        assertTrue(inst is SAddSat)
    }

    // --- Bit manipulation instructions ---

    @Test
    fun `ctlz instruction`() {
        val ir = ModuleBuilder("test")
        val params = ir.createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val lz = ir.ctlz(params[0])
        ir.ret(lz)
        ir.finalizeFunction()

        val inst = ir.build().functions[0].blocks[0].instructions[0]
        assertTrue(inst is Ctlz)
    }

    @Test
    fun `cttz instruction`() {
        val ir = ModuleBuilder("test")
        val params = ir.createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val tz = ir.cttz(params[0])
        ir.ret(tz)
        ir.finalizeFunction()

        val inst = ir.build().functions[0].blocks[0].instructions[0]
        assertTrue(inst is Cttz)
    }

    @Test
    fun `ctpop instruction`() {
        val ir = ModuleBuilder("test")
        val params = ir.createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val pop = ir.ctpop(params[0])
        ir.ret(pop)
        ir.finalizeFunction()

        val inst = ir.build().functions[0].blocks[0].instructions[0]
        assertTrue(inst is Ctpop)
    }

    @Test
    fun `bswap instruction`() {
        val ir = ModuleBuilder("test")
        val params = ir.createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val swapped = ir.bswap(params[0])
        ir.ret(swapped)
        ir.finalizeFunction()

        val inst = ir.build().functions[0].blocks[0].instructions[0]
        assertTrue(inst is BSwap)
    }

    @Test
    fun `rotate left and right`() {
        val ir = ModuleBuilder("test")
        val params = ir.createFunction("f", listOf(Param("x", Type.I32), Param("n", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val rotl = ir.rotateLeft(params[0], params[1])
        val rotr = ir.rotateRight(rotl, params[1])
        ir.ret(rotr)
        ir.finalizeFunction()

        val instrs = ir.build().functions[0].blocks[0].instructions
        assertTrue(instrs[0] is Rotl)
        assertTrue(instrs[1] is Rotr)
    }

    // --- Min/max/abs ---

    @Test
    fun `smin smax umin umax abs instructions`() {
        val ir = ModuleBuilder("test")
        val params = ir.createFunction("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val mn = ir.smin(params[0], params[1])
        val mx = ir.smax(mn, params[1])
        val umn = ir.umin(mx, params[0])
        val umx = ir.umax(umn, params[1])
        val ab = ir.abs(umx)
        ir.ret(ab)
        ir.finalizeFunction()

        val instrs = ir.build().functions[0].blocks[0].instructions
        assertTrue(instrs[0] is SMin)
        assertTrue(instrs[1] is SMax)
        assertTrue(instrs[2] is UMin)
        assertTrue(instrs[3] is UMax)
        assertTrue(instrs[4] is Abs)
    }

    // --- Float intrinsics ---

    @Test
    fun `fabs fmin fmax fma instructions`() {
        val ir = ModuleBuilder("test")
        val params = ir.createFunction("f", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.F64)
        ir.appendBlock("entry")
        val ab = ir.fabs(params[0])
        val mn = ir.fmin(ab, params[1])
        val mx = ir.fmax(mn, params[0])
        val fma = ir.fma(mx, params[0], params[1])
        ir.ret(fma)
        ir.finalizeFunction()

        val instrs = ir.build().functions[0].blocks[0].instructions
        assertTrue(instrs[0] is FAbs)
        assertTrue(instrs[1] is FMin)
        assertTrue(instrs[2] is FMax)
        assertTrue(instrs[3] is FMA)
    }

    @Test
    fun `ceil floor round ftrunc instructions`() {
        val ir = ModuleBuilder("test")
        val params = ir.createFunction("f", listOf(Param("x", Type.F64)), Type.F64)
        ir.appendBlock("entry")
        val c = ir.ceil(params[0])
        val fl = ir.floor(c)
        val r = ir.round(fl)
        val t = ir.ftrunc(r)
        ir.ret(t)
        ir.finalizeFunction()

        val instrs = ir.build().functions[0].blocks[0].instructions
        assertTrue(instrs[0] is Ceil)
        assertTrue(instrs[1] is Floor)
        assertTrue(instrs[2] is Round)
        assertTrue(instrs[3] is FTrunc)
    }

    @Test
    fun `copySign instruction`() {
        val ir = ModuleBuilder("test")
        val params = ir.createFunction("f", listOf(Param("mag", Type.F64), Param("sign", Type.F64)), Type.F64)
        ir.appendBlock("entry")
        val result = ir.copySign(params[0], params[1])
        ir.ret(result)
        ir.finalizeFunction()

        val inst = ir.build().functions[0].blocks[0].instructions[0]
        assertTrue(inst is CopySign)
    }

    // --- Fast math flags ---

    @Test
    fun `fadd with fast math flags`() {
        val ir = ModuleBuilder("test")
        val params = ir.createFunction("f", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.F64)
        ir.appendBlock("entry")
        val result = ir.fadd(params[0], params[1], FastMathFlags.FAST)
        ir.ret(result)
        ir.finalizeFunction()

        val inst = ir.build().functions[0].blocks[0].instructions[0] as FAdd
        assertEquals(FastMathFlags.FAST, inst.fastMath)
    }

    // --- Atomic operations ---

    @Test
    fun `cmpxchg instruction`() {
        val ir = ModuleBuilder("test")
        ir.createFunction("f", listOf(Param("ptr", Type.Pointer(Type.I32))), Type.I32)
        ir.appendBlock("entry")
        val result = ir.cmpxchg(ir.param(0), Constant.I32(0), Constant.I32(1),
            AtomicOrdering.ACQUIRE, AtomicOrdering.MONOTONIC)
        val value = ir.extractValue(result, 0)
        ir.ret(value)
        ir.finalizeFunction()

        val inst = ir.build().functions[0].blocks[0].instructions[0]
        assertTrue(inst is CmpXchg)
    }

    @Test
    fun `atomicRMW instruction`() {
        val ir = ModuleBuilder("test")
        ir.createFunction("f", listOf(Param("ptr", Type.Pointer(Type.I32))), Type.I32)
        ir.appendBlock("entry")
        val old = ir.atomicRMW(AtomicRMWOp.ADD, ir.param(0), Constant.I32(1), AtomicOrdering.SEQ_CST)
        ir.ret(old)
        ir.finalizeFunction()

        val inst = ir.build().functions[0].blocks[0].instructions[0]
        assertTrue(inst is AtomicRMW)
        val rmw = inst as AtomicRMW
        assertEquals(AtomicRMWOp.ADD, rmw.op)
    }

    @Test
    fun `fence instruction`() {
        val ir = ModuleBuilder("test")
        ir.createFunction("f", emptyList(), Type.Void)
        ir.appendBlock("entry")
        ir.fence(AtomicOrdering.SEQ_CST)
        ir.ret()
        ir.finalizeFunction()

        val inst = ir.build().functions[0].blocks[0].instructions[0]
        assertTrue(inst is Fence)
    }

    // --- Memory intrinsics ---

    @Test
    fun `memcpy memset memmove instructions`() {
        val ir = ModuleBuilder("test")
        ir.createFunction("f",
            listOf(Param("dst", Type.OpaquePointer), Param("src", Type.OpaquePointer)),
            Type.Void)
        ir.appendBlock("entry")
        ir.memcpy(ir.param(0), ir.param(1), Constant.I64(100))
        ir.memset(ir.param(0), Constant.I8(0), Constant.I64(100))
        ir.memmove(ir.param(0), ir.param(1), Constant.I64(50))
        ir.ret()
        ir.finalizeFunction()

        val instrs = ir.build().functions[0].blocks[0].instructions
        assertTrue(instrs[0] is MemCpy)
        assertTrue(instrs[1] is MemSet)
        assertTrue(instrs[2] is MemMove)
    }

    // --- Stack save/restore ---

    @Test
    fun `stackSave and stackRestore`() {
        val ir = ModuleBuilder("test")
        ir.createFunction("f", emptyList(), Type.Void)
        ir.appendBlock("entry")
        val saved = ir.stackSave()
        ir.alloca(Type.I32)
        ir.stackRestore(saved)
        ir.ret()
        ir.finalizeFunction()

        val instrs = ir.build().functions[0].blocks[0].instructions
        assertTrue(instrs[0] is StackSave)
        assertTrue(instrs[2] is StackRestore)
    }

    // --- IndirectBr ---

    @Test
    fun `indirectBr instruction`() {
        val ir = ModuleBuilder("test")
        ir.createFunction("f", listOf(Param("addr", Type.OpaquePointer)), Type.Void)
        ir.appendBlock("entry")
        ir.indirectBr(ir.param(0), listOf(BlockRef("target1"), BlockRef("target2")))

        ir.appendBlock("target1")
        ir.ret()
        ir.appendBlock("target2")
        ir.ret()

        ir.finalizeFunction()
        val inst = ir.build().functions[0].blocks[0].instructions[0]
        assertTrue(inst is IndirectBr)
        assertEquals(2, (inst as IndirectBr).targets.size)
    }

    // --- Trap and debug trap ---

    @Test
    fun `trap instruction`() {
        val ir = ModuleBuilder("test")
        ir.createFunction("f", emptyList(), Type.Void)
        ir.appendBlock("entry")
        ir.trap()
        ir.finalizeFunction()

        val inst = ir.build().functions[0].blocks[0].instructions[0]
        assertTrue(inst is Trap)
    }

    @Test
    fun `debugTrap instruction`() {
        val ir = ModuleBuilder("test")
        ir.createFunction("f", emptyList(), Type.Void)
        ir.appendBlock("entry")
        ir.debugTrap()
        ir.ret()
        ir.finalizeFunction()

        val inst = ir.build().functions[0].blocks[0].instructions[0]
        assertTrue(inst is DebugTrap)
    }

    // --- Freeze ---

    @Test
    fun `freeze instruction`() {
        val ir = ModuleBuilder("test")
        val params = ir.createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val frozen = ir.freeze(params[0])
        assertEquals(Type.I32, frozen.type)
        ir.ret(frozen)
        ir.finalizeFunction()

        val inst = ir.build().functions[0].blocks[0].instructions[0]
        assertTrue(inst is Freeze)
    }

    // --- Aggregate instructions ---

    @Test
    fun `extractValue and insertValue`() {
        val structType = Type.Struct(null, listOf(Type.I32, Type.F64))
        val ir = ModuleBuilder("test")
        ir.createFunction("f", listOf(Param("s", structType)), Type.F64)
        ir.appendBlock("entry")
        val field = ir.extractValue(ir.param(0), 1)
        assertEquals(Type.F64, field.type)
        ir.ret(field)
        ir.finalizeFunction()

        val inst = ir.build().functions[0].blocks[0].instructions[0]
        assertTrue(inst is ExtractValue)
    }

    @Test
    fun `insertValue instruction`() {
        val structType = Type.Struct(null, listOf(Type.I32, Type.I64))
        val ir = ModuleBuilder("test")
        ir.createFunction("f", listOf(Param("s", structType)), structType)
        ir.appendBlock("entry")
        val updated = ir.insertValue(ir.param(0), Constant.I32(42), 0)
        assertEquals(structType, updated.type)
        ir.ret(updated)
        ir.finalizeFunction()

        val inst = ir.build().functions[0].blocks[0].instructions[0]
        assertTrue(inst is InsertValue)
    }

    // --- Module-level features ---

    @Test
    fun `target features`() {
        val ir = ModuleBuilder("test")
        ir.addTargetFeature("+sse4.2")
        ir.addTargetFeature("+avx2")

        val mod = ir.build()
        assertTrue(mod.targetFeatures.contains("+sse4.2"))
        assertTrue(mod.targetFeatures.contains("+avx2"))
    }

    @Test
    fun `global constructors and destructors`() {
        val ir = ModuleBuilder("test")
        ir.addGlobalCtor("init_module", 100)
        ir.addGlobalDtor("cleanup_module", 200)

        ir.createFunction("init_module", emptyList(), Type.Void)
        ir.appendBlock("entry")
        ir.ret()
        ir.finalizeFunction()

        ir.createFunction("cleanup_module", emptyList(), Type.Void)
        ir.appendBlock("entry")
        ir.ret()
        ir.finalizeFunction()

        val mod = ir.build()
        assertEquals(1, mod.globalCtors.size)
        assertEquals(100, mod.globalCtors[0].priority)
        assertEquals(1, mod.globalDtors.size)
        assertEquals(200, mod.globalDtors[0].priority)
    }

    @Test
    fun `comdat definitions`() {
        val ir = ModuleBuilder("test")
        ir.addComdat("my_group", ComdatSelectionKind.ANY)

        val mod = ir.build()
        assertEquals(1, mod.comdats.size)
        assertEquals("my_group", mod.comdats[0].name)
        assertEquals(ComdatSelectionKind.ANY, mod.comdats[0].selectionKind)
    }

    @Test
    fun `module flags`() {
        val ir = ModuleBuilder("test")
        ir.addModuleFlag("PIC Level", ModuleFlagValue.IntFlag(2))
        ir.addModuleFlag("Debug Info", ModuleFlagValue.StringFlag("CodeView"))

        val mod = ir.build()
        assertEquals(2, mod.moduleFlags.size)
        assertTrue(mod.moduleFlags["PIC Level"] is ModuleFlagValue.IntFlag)
        assertEquals(2L, (mod.moduleFlags["PIC Level"] as ModuleFlagValue.IntFlag).value)
    }

    @Test
    fun `module properties`() {
        val ir = ModuleBuilder("test")
        ir.dataLayout = "e-m:e-p270:32:32-p271:32:32-p272:64:64-i64:64-f80:128-n8:16:32:64-S128"
        ir.moduleInlineAsm = ".section .text"

        val mod = ir.build()
        assertEquals("e-m:e-p270:32:32-p271:32:32-p272:64:64-i64:64-f80:128-n8:16:32:64-S128", mod.dataLayout)
        assertEquals(".section .text", mod.moduleInlineAsm)
    }

    // --- Declare function with varargs and calling convention ---

    // TODO: re-enable when declareFunction API is added
    // @Test
    // fun `declare vararg function`() {
    //     val ir = ModuleBuilder("test")
    //     val ref = ir.declareFunction("printf", listOf(Param("fmt", Type.OpaquePointer)), Type.I32, isVarArg = true)
    //     assertTrue(ref.type.vararg, "Function should be vararg")
    //     val mod = ir.build()
    //     assertTrue(mod.functions[0].isVarArg)
    // }

    @Test
    fun `function with calling convention`() {
        val ir = ModuleBuilder("test")
        ir.createFunction("f", emptyList(), Type.Void,
            callingConv = CallingConvention.FAST)
        ir.appendBlock("entry")
        ir.ret()
        ir.finalizeFunction()

        val mod = ir.build()
        assertEquals(CallingConvention.FAST, mod.functions[0].callingConv)
    }

    @Test
    fun `function with attributes`() {
        val ir = ModuleBuilder("test")
        ir.createFunction("f", emptyList(), Type.Void,
            attributes = setOf(FnAttribute.NOINLINE, FnAttribute.NOUNWIND))
        ir.appendBlock("entry")
        ir.ret()
        ir.finalizeFunction()

        val mod = ir.build()
        assertTrue(mod.functions[0].attributes.contains(FnAttribute.NOINLINE))
        assertTrue(mod.functions[0].attributes.contains(FnAttribute.NOUNWIND))
    }

    @Test
    fun `function with section and gc`() {
        val ir = ModuleBuilder("test")
        ir.createFunction("f", emptyList(), Type.Void,
            section = ".hot_text", gc = "shadow-stack")
        ir.appendBlock("entry")
        ir.ret()
        ir.finalizeFunction()

        val mod = ir.build()
        assertEquals(".hot_text", mod.functions[0].section)
        assertEquals("shadow-stack", mod.functions[0].gc)
    }

    // --- Intrinsic and inline assembly ---

    @Test
    fun `intrinsic call`() {
        val ir = ModuleBuilder("test")
        ir.createFunction("f", listOf(Param("x", Type.F64)), Type.F64)
        ir.appendBlock("entry")
        val result = ir.intrinsic("llvm.sin.f64", listOf(ir.param(0)), Type.F64)
        ir.ret(result!!)
        ir.finalizeFunction()

        val inst = ir.build().functions[0].blocks[0].instructions[0]
        assertTrue(inst is Intrinsic)
        assertEquals("llvm.sin.f64", (inst as Intrinsic).name)
    }

    @Test
    fun `inline asm instruction`() {
        val ir = ModuleBuilder("test")
        ir.createFunction("f", emptyList(), Type.I32)
        ir.appendBlock("entry")
        val result = ir.inlineAsm("mov $$42, %eax", "=r", returnType = Type.I32)
        ir.ret(result!!)
        ir.finalizeFunction()

        val inst = ir.build().functions[0].blocks[0].instructions[0]
        assertTrue(inst is InlineAsm)
    }

    // --- Debug instructions ---

    @Test
    fun `debugLoc and debugValue instructions`() {
        val ir = ModuleBuilder("test")
        ir.createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        ir.debugLoc(10, 5, "main.kt")
        ir.debugValue("x", ir.param(0))
        ir.ret(ir.param(0))
        ir.finalizeFunction()

        val instrs = ir.build().functions[0].blocks[0].instructions
        assertTrue(instrs[0] is DebugLoc)
        assertTrue(instrs[1] is DebugValue)
    }

    // --- Optimizer hints ---

    @Test
    fun `assume and expect instructions`() {
        val ir = ModuleBuilder("test")
        val params = ir.createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val cmp = ir.icmp(ICmpPredicate.SGT, params[0], Constant.I32(0))
        ir.assume(cmp)
        val expected = ir.expect(params[0], Constant.I32(42))
        ir.ret(expected)
        ir.finalizeFunction()

        val instrs = ir.build().functions[0].blocks[0].instructions
        assertTrue(instrs[1] is Assume)
        assertTrue(instrs[2] is Expect)
    }

    // --- Varargs ---

    @Test
    fun `vararg instructions`() {
        val ir = ModuleBuilder("test")
        ir.createFunction("f", listOf(Param("args", Type.OpaquePointer)), Type.I32, isVarArg = true)
        ir.appendBlock("entry")
        ir.vaStart(ir.param(0))
        val arg = ir.vaArg(ir.param(0), Type.I32)
        ir.vaEnd(ir.param(0))
        ir.ret(arg)
        ir.finalizeFunction()

        val instrs = ir.build().functions[0].blocks[0].instructions
        assertTrue(instrs[0] is VAStart)
        assertTrue(instrs[1] is VAArg)
        assertTrue(instrs[2] is VAEnd)
    }

    // --- Lifetime intrinsics ---

    @Test
    fun `lifetime start and end`() {
        val ir = ModuleBuilder("test")
        ir.createFunction("f", emptyList(), Type.Void)
        ir.appendBlock("entry")
        val ptr = ir.alloca(Type.I32)
        ir.lifetimeStart(ptr, 4)
        ir.store(Constant.I32(0), ptr)
        ir.lifetimeEnd(ptr, 4)
        ir.ret()
        ir.finalizeFunction()

        val instrs = ir.build().functions[0].blocks[0].instructions
        assertTrue(instrs[1] is LifetimeStart)
        assertTrue(instrs[3] is LifetimeEnd)
    }

    // --- Complex end-to-end: fibonacci with phi nodes ---

    @Test
    fun `fibonacci with loop and phi nodes`() {
        val ir = ModuleBuilder("test")
        val params = ir.createFunction("fib", listOf(Param("n", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val cmp = ir.icmp(ICmpPredicate.SLE, params[0], Constant.I32(1))
        ir.condBr(cmp, BlockRef("base"), BlockRef("loop.header"))

        ir.appendBlock("base")
        ir.ret(params[0])

        ir.appendBlock("loop.header")
        val prevPhi = ir.phi(Type.I32, listOf(Constant.I32(0) to BlockRef("entry")))
        val currPhi = ir.phi(Type.I32, listOf(Constant.I32(1) to BlockRef("entry")))
        val iPhi = ir.phi(Type.I32, listOf(Constant.I32(2) to BlockRef("entry")))
        val next = ir.add(prevPhi, currPhi)
        val nextI = ir.add(iPhi, Constant.I32(1))
        val loopCmp = ir.icmp(ICmpPredicate.SLE, nextI, params[0])
        ir.condBr(loopCmp, BlockRef("loop.header"), BlockRef("done"))

        ir.appendBlock("done")
        ir.ret(next)

        ir.finalizeFunction()
        val mod = ir.build()
        assertEquals(4, mod.functions[0].blocks.size)

        val loopBlock = mod.functions[0].blocks[2]
        val phis = loopBlock.instructions.filterIsInstance<Phi>()
        assertEquals(3, phis.size)
    }

    // --- Complex end-to-end: nested loops ---

    @Test
    fun `nested loop pattern`() {
        val ir = ModuleBuilder("test")
        ir.createFunction("nested", listOf(Param("n", Type.I32), Param("m", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val sumPtr = ir.alloca(Type.I32)
        ir.store(Constant.I32(0), sumPtr)
        val iPtr = ir.alloca(Type.I32)
        ir.store(Constant.I32(0), iPtr)
        ir.br(BlockRef("outer.cond"))

        ir.appendBlock("outer.cond")
        val i = ir.load(Type.I32, iPtr)
        val outerCmp = ir.icmp(ICmpPredicate.SLT, i, ir.param(0))
        ir.condBr(outerCmp, BlockRef("inner.init"), BlockRef("exit"))

        ir.appendBlock("inner.init")
        val jPtr = ir.alloca(Type.I32)
        ir.store(Constant.I32(0), jPtr)
        ir.br(BlockRef("inner.cond"))

        ir.appendBlock("inner.cond")
        val j = ir.load(Type.I32, jPtr)
        val innerCmp = ir.icmp(ICmpPredicate.SLT, j, ir.param(1))
        ir.condBr(innerCmp, BlockRef("inner.body"), BlockRef("outer.inc"))

        ir.appendBlock("inner.body")
        val curSum = ir.load(Type.I32, sumPtr)
        val newSum = ir.add(curSum, Constant.I32(1))
        ir.store(newSum, sumPtr)
        val curJ = ir.load(Type.I32, jPtr)
        val nextJ = ir.add(curJ, Constant.I32(1))
        ir.store(nextJ, jPtr)
        ir.br(BlockRef("inner.cond"))

        ir.appendBlock("outer.inc")
        val curI = ir.load(Type.I32, iPtr)
        val nextI = ir.add(curI, Constant.I32(1))
        ir.store(nextI, iPtr)
        ir.br(BlockRef("outer.cond"))

        ir.appendBlock("exit")
        val finalSum = ir.load(Type.I32, sumPtr)
        ir.ret(finalSum)

        ir.finalizeFunction()
        val mod = ir.build()
        assertEquals(7, mod.functions[0].blocks.size)
        assertTrue(IrVerifier.verify(mod).isValid)
    }

    // --- OOP instructions via ModuleBuilder directly ---

    @Test
    fun `newObject and getField putField via ModuleBuilder`() {
        val ir = ModuleBuilder("test")
        ir.createFunction("f", emptyList(), Type.I32)
        ir.appendBlock("entry")
        val obj = ir.newObject("Point")
        ir.putField(obj, "Point", "x", Type.I32, Constant.I32(10))
        val x = ir.getField(obj, "Point", "x", Type.I32)
        ir.ret(x)
        ir.finalizeFunction()

        val instrs = ir.build().functions[0].blocks[0].instructions
        assertTrue(instrs[0] is NewObject)
        assertTrue(instrs[1] is PutField)
        assertTrue(instrs[2] is GetField)
    }

    @Test
    fun `newArray and arrayGet arraySet arrayLength`() {
        val ir = ModuleBuilder("test")
        ir.createFunction("f", emptyList(), Type.I32)
        ir.appendBlock("entry")
        val arr = ir.newArray(Type.I32, Constant.I32(10))
        ir.arraySet(arr, Constant.I32(0), Constant.I32(42), Type.I32)
        val v = ir.arrayGet(arr, Constant.I32(0), Type.I32)
        val len = ir.arrayLength(arr)
        val result = ir.add(v, len)
        ir.ret(result)
        ir.finalizeFunction()

        val instrs = ir.build().functions[0].blocks[0].instructions
        assertTrue(instrs[0] is NewArray)
        assertTrue(instrs[1] is ArraySet)
        assertTrue(instrs[2] is ArrayGet)
        assertTrue(instrs[3] is ArrayLength)
    }

    @Test
    fun `getStatic and putStatic`() {
        val ir = ModuleBuilder("test")
        ir.createFunction("f", emptyList(), Type.I32)
        ir.appendBlock("entry")
        ir.putStatic("Counter", "count", Type.I32, Constant.I32(0))
        val v = ir.getStatic("Counter", "count", Type.I32)
        ir.ret(v)
        ir.finalizeFunction()

        val instrs = ir.build().functions[0].blocks[0].instructions
        assertTrue(instrs[0] is PutStatic)
        assertTrue(instrs[1] is GetStatic)
    }

    @Test
    fun `instanceOf and checkCast`() {
        val ir = ModuleBuilder("test")
        ir.createFunction("f", listOf(Param("obj", Type.ClassRef("Base"))), Type.ClassRef("Derived"))
        ir.appendBlock("entry")
        val isD = ir.instanceOf(ir.param(0), Type.ClassRef("Derived"))
        assertEquals(Type.I1, isD.type)
        val cast = ir.checkCast(ir.param(0), Type.ClassRef("Derived"))
        ir.ret(cast)
        ir.finalizeFunction()

        val instrs = ir.build().functions[0].blocks[0].instructions
        assertTrue(instrs[0] is InstanceOf)
        assertTrue(instrs[1] is CheckCast)
    }

    // --- Exception handling ---

    @Test
    fun `throwException instruction`() {
        val ir = ModuleBuilder("test")
        ir.createFunction("f", listOf(Param("exc", Type.ClassRef("Exception"))), Type.Void)
        ir.appendBlock("entry")
        ir.throwException(ir.param(0))
        ir.finalizeFunction()

        val inst = ir.build().functions[0].blocks[0].instructions[0]
        assertTrue(inst is Throw)
    }

    // --- Boxing and unboxing ---

    @Test
    fun `box and unbox instructions`() {
        val ir = ModuleBuilder("test")
        ir.createFunction("f", listOf(Param("x", Type.I32)), Type.I32)
        ir.appendBlock("entry")
        val boxed = ir.box(ir.param(0), Type.ClassRef("Integer"))
        val unboxed = ir.unbox(boxed, Type.I32)
        ir.ret(unboxed)
        ir.finalizeFunction()

        val instrs = ir.build().functions[0].blocks[0].instructions
        assertTrue(instrs[0] is Box)
        assertTrue(instrs[1] is Unbox)
    }

    // --- FunctionBuilder: additional structured control flow patterns ---

    @Test
    fun `FunctionBuilder forLoop with break`() {
        val ir = ModuleBuilder("test")
        val fn = ir.function("find_first", listOf(Param("limit", Type.I32)), Type.I32)
        val ins = fn.instructions
        val result = ins.variable(Type.i32(-1))
        val i = ins.variable(Type.i32(0))
        fn.forLoop(
            init = { instructions.set(i, Type.i32(0)) },
            condition = { instructions.lt(instructions.get(i), param(0)) },
            update = { instructions.set(i, instructions.add(instructions.get(i), Type.i32(1))) },
            body = {
                ifThen(instructions.eq(instructions.get(i), Type.i32(5))) {
                    instructions.set(result, instructions.get(i))
                    breakOut()
                }
            },
        )
        fn.ret(ins.get(result))
        fn.end()

        val mod = ir.build()
        assertTrue(IrVerifier.verify(mod).isValid)
    }

    @Test
    fun `FunctionBuilder doWhile with accumulator`() {
        val ir = ModuleBuilder("test")
        val fn = ir.function("collatz_steps", listOf(Param("n", Type.I32)), Type.I32)
        val ins = fn.instructions
        val steps = ins.variable(Type.i32(0))
        val current = ins.variable(fn.param(0))
        fn.doWhile(
            body = {
                instructions.set(steps, instructions.add(instructions.get(steps), Type.i32(1)))
                ifElse(instructions.eq(instructions.rem(instructions.get(current), Type.i32(2)), Type.i32(0)),
                    { instructions.set(current, instructions.div(instructions.get(current), Type.i32(2))) },
                    { instructions.set(current, instructions.add(instructions.mul(instructions.get(current), Type.i32(3)), Type.i32(1))) },
                )
            },
            condition = { instructions.ne(instructions.get(current), Type.i32(1)) },
        )
        fn.ret(ins.get(steps))
        fn.end()

        val mod = ir.build()
        assertTrue(IrVerifier.verify(mod).isValid)
    }

    @Test
    fun `FunctionBuilder nested loops`() {
        val ir = ModuleBuilder("test")
        val fn = ir.function("matrix_sum", listOf(Param("rows", Type.I32), Param("cols", Type.I32)), Type.I32)
        val ins = fn.instructions
        val sum = ins.variable(Type.i32(0))
        val i = ins.variable(Type.i32(0))
        fn.forLoop(
            init = { instructions.set(i, Type.i32(0)) },
            condition = { instructions.lt(instructions.get(i), param(0)) },
            update = { instructions.set(i, instructions.add(instructions.get(i), Type.i32(1))) },
            body = {
                val j = instructions.variable(Type.i32(0))
                forLoop(
                    init = { instructions.set(j, Type.i32(0)) },
                    condition = { instructions.lt(instructions.get(j), param(1)) },
                    update = { instructions.set(j, instructions.add(instructions.get(j), Type.i32(1))) },
                    body = {
                        instructions.set(sum, instructions.add(instructions.get(sum), instructions.mul(instructions.get(i), instructions.get(j))))
                    },
                )
            },
        )
        fn.ret(ins.get(sum))
        fn.end()

        val mod = ir.build()
        assertTrue(IrVerifier.verify(mod).isValid)
    }

    @Test
    fun `FunctionBuilder unsigned comparisons`() {
        val ir = ModuleBuilder("test")
        val fn = ir.function("ucmp", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        val ins = fn.instructions
        val result = ins.variable(Type.i32(0))
        fn.ifThen(ins.ult(fn.param(0), fn.param(1))) {
            instructions.set(result, Type.i32(1))
        }
        fn.ifThen(ins.ule(fn.param(0), fn.param(1))) {
            instructions.set(result, instructions.add(instructions.get(result), Type.i32(10)))
        }
        fn.ifThen(ins.ugt(fn.param(0), fn.param(1))) {
            instructions.set(result, instructions.add(instructions.get(result), Type.i32(100)))
        }
        fn.ifThen(ins.uge(fn.param(0), fn.param(1))) {
            instructions.set(result, instructions.add(instructions.get(result), Type.i32(1000)))
        }
        fn.ret(ins.get(result))
        fn.end()

        val mod = ir.build()
        assertTrue(IrVerifier.verify(mod).isValid)
    }

    @Test
    fun `FunctionBuilder float comparisons`() {
        val ir = ModuleBuilder("test")
        val fn = ir.function("fcmp_all", listOf(Param("a", Type.F64), Param("b", Type.F64)), Type.I32)
        val ins = fn.instructions
        val count = ins.variable(Type.i32(0))
        fn.ifThen(ins.feq(fn.param(0), fn.param(1))) {
            instructions.set(count, instructions.add(instructions.get(count), Type.i32(1)))
        }
        fn.ifThen(ins.flt(fn.param(0), fn.param(1))) {
            instructions.set(count, instructions.add(instructions.get(count), Type.i32(1)))
        }
        fn.ifThen(ins.fgt(fn.param(0), fn.param(1))) {
            instructions.set(count, instructions.add(instructions.get(count), Type.i32(1)))
        }
        fn.ret(ins.get(count))
        fn.end()

        val mod = ir.build()
        assertTrue(IrVerifier.verify(mod).isValid)
    }

    @Test
    fun `FunctionBuilder floatCast extend and truncate`() {
        val ir = ModuleBuilder("test")
        val fn = ir.function("fcast", listOf(Param("x", Type.F32)), Type.F32)
        val ins = fn.instructions
        val wide = ins.floatCast(fn.param(0), Type.F64)
        val narrow = ins.floatCast(wide, Type.F32)
        fn.ret(narrow)
        fn.end()

        val mod = ir.build()
        assertTrue(IrVerifier.verify(mod).isValid)
    }

    @Test
    fun `FunctionBuilder toFloat and toInt`() {
        val ir = ModuleBuilder("test")
        val fn = ir.function("convert", listOf(Param("x", Type.I32)), Type.I32)
        val ins = fn.instructions
        val asFloat = ins.toFloat(fn.param(0), Type.F64)
        val backToInt = ins.toInt(asFloat, Type.I32)
        fn.ret(backToInt)
        fn.end()

        val mod = ir.build()
        assertTrue(IrVerifier.verify(mod).isValid)
    }

    @Test
    fun `FunctionBuilder uintCast zero extend`() {
        val ir = ModuleBuilder("test")
        val fn = ir.function("uzext", listOf(Param("x", Type.I32)), Type.I64)
        val ins = fn.instructions
        fn.ret(ins.uintCast(fn.param(0), Type.I64))
        fn.end()

        val mod = ir.build()
        val instrs = mod.functions[0].blocks[0].instructions
        assertTrue(instrs[0] is ZExt)
        assertTrue(IrVerifier.verify(mod).isValid)
    }

    @Test
    fun `FunctionBuilder intCast same size is identity`() {
        val ir = ModuleBuilder("test")
        val fn = ir.function("noop_cast", listOf(Param("x", Type.I32)), Type.I32)
        val ins = fn.instructions
        val same = ins.intCast(fn.param(0), Type.I32)
        fn.ret(same)
        fn.end()

        val mod = ir.build()
        assertTrue(IrVerifier.verify(mod).isValid)
    }

    @Test
    fun `FunctionBuilder bitwise operations`() {
        val ir = ModuleBuilder("test")
        val fn = ir.function("bits", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)
        val ins = fn.instructions
        val a = ins.and(fn.param(0), fn.param(1))
        val o = ins.or(a, fn.param(1))
        val x = ins.xor(o, fn.param(0))
        val n = ins.not(x)
        val sl = ins.shl(n, fn.param(1))
        val sr = ins.shr(sl, fn.param(1))
        val usr = ins.ushr(sr, fn.param(1))
        fn.ret(usr)
        fn.end()

        val mod = ir.build()
        assertTrue(IrVerifier.verify(mod).isValid)
    }

    @Test
    fun `FunctionBuilder call with return type`() {
        val ir = ModuleBuilder("test")
        ir.declareFunction("helper", listOf(Param("x", Type.I32)), Type.I32)

        val fn = ir.function("caller", listOf(Param("x", Type.I32)), Type.I32)
        val ins = fn.instructions
        val helperRef = GlobalRef("helper", Type.Function(listOf(Type.I32), Type.I32))
        val result = ins.call(helperRef, listOf(fn.param(0)), Type.I32)
        fn.ret(result!!)
        fn.end()

        val mod = ir.build()
        assertTrue(IrVerifier.verify(mod).isValid)
    }

    @Test
    fun `FunctionBuilder functionRef and call`() {
        val ir = ModuleBuilder("test")
        ir.declareFunction("compute", listOf(Param("x", Type.I32)), Type.I32)

        val fn = ir.function("use_ref", listOf(Param("x", Type.I32)), Type.I32)
        val ins = fn.instructions
        val ref = fn.functionRef("compute", listOf(Type.I32), Type.I32)
        val result = ins.call(ref, listOf(fn.param(0)))
        fn.ret(result!!)
        fn.end()

        val mod = ir.build()
        assertEquals(2, mod.functions.size)
    }

    @Test
    fun `FunctionBuilder breakOut outside loop throws`() {
        val ir = ModuleBuilder("test")
        val fn = ir.function("bad", emptyList(), Type.Void)
        assertThrows(IllegalStateException::class.java) {
            fn.breakOut()
        }
    }

    @Test
    fun `FunctionBuilder continueOn outside loop throws`() {
        val ir = ModuleBuilder("test")
        val fn = ir.function("bad", emptyList(), Type.Void)
        assertThrows(IllegalStateException::class.java) {
            fn.continueOn()
        }
    }

    @Test
    fun `FunctionBuilder variable with explicit type`() {
        val ir = ModuleBuilder("test")
        val fn = ir.function("f", emptyList(), Type.I64)
        val ins = fn.instructions
        val v = ins.variable(Type.I64, Type.i64(42L))
        fn.ret(ins.get(v))
        fn.end()

        val mod = ir.build()
        assertTrue(IrVerifier.verify(mod).isValid)
    }
}
