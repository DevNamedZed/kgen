package org.kgen.ir.build

import org.kgen.ir.*
import org.kgen.ir.target.Target
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class ModuleBuilderTypedLabelTest {

    @Test
    fun labelCreatesForwardReference() {
        val ir = ModuleBuilder("test", Target.x86_64())
        ir.createFunction("f", emptyList(), Type.Void)

        val block = ir.label()
        assertNotNull(block)
        assertNotNull(block.label)
    }

    @Test
    fun labelWithNameCreatesNamedBlock() {
        val ir = ModuleBuilder("test", Target.x86_64())
        ir.createFunction("f", emptyList(), Type.Void)

        val block = ir.label("myblock")
        assertEquals("myblock", block.label)
    }

    @Test
    fun markCreatesAndPositions() {
        val ir = ModuleBuilder("test", Target.x86_64())
        ir.createFunction("f", emptyList(), Type.Void)

        val block = ir.mark()
        assertEquals(block.label, ir.getInsertBlock())
    }

    @Test
    fun markWithNameCreatesNamedBlockAndPositions() {
        val ir = ModuleBuilder("test", Target.x86_64())
        ir.createFunction("f", emptyList(), Type.Void)

        val block = ir.mark("entry")
        assertEquals("entry", block.label)
        assertEquals("entry", ir.getInsertBlock())
    }

    @Test
    fun markExistingBlockPositions() {
        val ir = ModuleBuilder("test", Target.x86_64())
        ir.createFunction("f", emptyList(), Type.Void)

        val block = ir.label("deferred")
        ir.mark("entry")
        ir.ret()

        ir.mark(block)
        assertEquals("deferred", ir.getInsertBlock())
    }

    @Test
    fun labelIsEquivalentToCreateBlock() {
        val ir1 = ModuleBuilder("test1", Target.x86_64())
        ir1.createFunction("f", emptyList(), Type.Void)
        val viaLabel = ir1.label("blk")

        val ir2 = ModuleBuilder("test2", Target.x86_64())
        ir2.createFunction("f", emptyList(), Type.Void)
        val viaCreate = ir2.createBlock("blk")

        assertEquals(viaLabel.label, viaCreate.label)
    }

    @Test
    fun markIsEquivalentToAppendBlock() {
        val ir1 = ModuleBuilder("test1", Target.x86_64())
        ir1.createFunction("f", emptyList(), Type.Void)
        val viaMark = ir1.mark("blk")

        val ir2 = ModuleBuilder("test2", Target.x86_64())
        ir2.createFunction("f", emptyList(), Type.Void)
        val viaAppend = ir2.appendBlock("blk")

        assertEquals(viaMark.label, viaAppend.label)
    }

    @Test
    fun forwardReferenceWithCondBr() {
        val ir = ModuleBuilder("test", Target.x86_64())
        val params = ir.createFunction("abs", listOf(Param("x", Type.I32)), Type.I32)

        val thenBlock = ir.label("then")
        val elseBlock = ir.label("else")

        ir.mark("entry")
        val cmp = ir.icmp(ICmpPredicate.SGE, params[0], Type.i32(0))
        ir.condBr(cmp, thenBlock, elseBlock)

        ir.mark(thenBlock)
        ir.ret(params[0])

        ir.mark(elseBlock)
        val neg = ir.neg(params[0])
        ir.ret(neg)

        ir.finalizeFunction()
        val mod = ir.build()
        assertEquals(3, mod.functions[0].blocks.size)
    }

    @Test
    fun forwardAndBackwardMixed() {
        val ir = ModuleBuilder("test", Target.x86_64())
        val params = ir.createFunction("count", listOf(Param("n", Type.I32)), Type.I32)

        val loopCond = ir.label("loop.cond")
        val loopBody = ir.label("loop.body")
        val exit = ir.label("exit")

        ir.mark("entry")
        ir.br(loopCond)

        ir.mark(loopCond)
        val cmp = ir.icmp(ICmpPredicate.SGT, params[0], Type.i32(0))
        ir.condBr(cmp, loopBody, exit)

        ir.mark(loopBody)
        ir.br(loopCond) // backward reference

        ir.mark(exit)
        ir.ret(params[0])

        ir.finalizeFunction()
        val mod = ir.build()
        assertEquals(4, mod.functions[0].blocks.size)
    }

    @Test
    fun anonymousLabelAutoNames() {
        val ir = ModuleBuilder("test", Target.x86_64())
        ir.createFunction("f", emptyList(), Type.Void)

        val first = ir.label()
        val second = ir.label()
        assertNotEquals(first.label, second.label)
        assertTrue(first.label.startsWith("bb"))
        assertTrue(second.label.startsWith("bb"))
    }

    @Test
    fun anonymousMarkAutoNames() {
        val ir = ModuleBuilder("test", Target.x86_64())
        ir.createFunction("f", emptyList(), Type.Void)

        val first = ir.mark()
        ir.ret()
        val second = ir.mark()
        ir.ret()

        assertNotEquals(first.label, second.label)
    }

    @Test
    fun ifElsePatternWithLabels() {
        val ir = ModuleBuilder("test", Target.x86_64())
        val params = ir.createFunction("max", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32)

        val thenBlock = ir.label()
        val elseBlock = ir.label()
        val merge = ir.label()

        ir.mark("entry")
        val cmp = ir.icmp(ICmpPredicate.SGT, params[0], params[1])
        ir.condBr(cmp, thenBlock, elseBlock)

        ir.mark(thenBlock)
        ir.br(merge)

        ir.mark(elseBlock)
        ir.br(merge)

        ir.mark(merge)
        val phi = ir.phi(Type.I32, listOf(
            params[0] to thenBlock,
            params[1] to elseBlock,
        ))
        ir.ret(phi)

        ir.finalizeFunction()
        val mod = ir.build()
        assertEquals(4, mod.functions[0].blocks.size)
    }

    @Test
    fun switchWithForwardLabels() {
        val ir = ModuleBuilder("test", Target.x86_64())
        val params = ir.createFunction("dispatch", listOf(Param("sel", Type.I32)), Type.I32)

        val case0 = ir.label()
        val case1 = ir.label()
        val default = ir.label()

        ir.mark("entry")
        ir.switch(params[0], default, listOf(
            Type.i32(0) to case0,
            Type.i32(1) to case1,
        ))

        ir.mark(case0)
        ir.ret(Type.i32(10))

        ir.mark(case1)
        ir.ret(Type.i32(20))

        ir.mark(default)
        ir.ret(Type.i32(-1))

        ir.finalizeFunction()
        val mod = ir.build()
        assertEquals(4, mod.functions[0].blocks.size)
    }

    @Test
    fun multipleLabelsInSequence() {
        val ir = ModuleBuilder("test", Target.x86_64())
        ir.createFunction("chain", emptyList(), Type.Void)

        val first = ir.label()
        val second = ir.label()
        val third = ir.label()

        ir.mark("entry")
        ir.br(first)

        ir.mark(first)
        ir.br(second)

        ir.mark(second)
        ir.br(third)

        ir.mark(third)
        ir.ret()

        ir.finalizeFunction()
        val mod = ir.build()
        assertEquals(4, mod.functions[0].blocks.size)
    }

    @Test
    fun manyLabels() {
        val ir = ModuleBuilder("test", Target.x86_64())
        ir.createFunction("many", emptyList(), Type.Void)

        val labels = (0 until 20).map { ir.label() }

        ir.mark("entry")
        ir.br(labels[0])

        for (index in 0 until labels.size - 1) {
            ir.mark(labels[index])
            ir.br(labels[index + 1])
        }

        ir.mark(labels.last())
        ir.ret()

        ir.finalizeFunction()
        val mod = ir.build()
        assertEquals(21, mod.functions[0].blocks.size) // entry + 20 anonymous blocks
    }
}
