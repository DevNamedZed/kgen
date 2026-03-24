package org.kgen.target.x86.asm

import org.kgen.target.x86.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class X86TypedLabelTest {

    private val rax = X86Register.RAX as X86Register64
    private val rcx = X86Register.RCX as X86Register64
    private val rdx = X86Register.RDX as X86Register64
    private val eax = X86Register.EAX as X86Register32
    private val ecx = X86Register.ECX as X86Register32

    @Test
    fun forwardJumpResolvesCorrectly() {
        val asm = X86Assembler()
        val target = asm.label()
        asm.jmp(target)
        asm.nop()
        asm.nop()
        asm.mark(target)
        asm.nop()

        val bytes = asm.toByteArray()
        // jmp rel32 = E9 + 4-byte offset, should skip 2 nops
        assertEquals(0xE9.toByte(), bytes[0])
        val offset = readInt32(bytes, 1)
        assertEquals(2, offset)
    }

    @Test
    fun backwardJumpResolvesCorrectly() {
        val asm = X86Assembler()
        val top = asm.mark()
        asm.nop()
        asm.jmp(top)

        val bytes = asm.toByteArray()
        // nop(1 byte), jmp rel32(5 bytes) → offset = 0 - (1 + 5) = -6
        assertEquals(0xE9.toByte(), bytes[1])
        val offset = readInt32(bytes, 2)
        assertEquals(-6, offset)
    }

    @Test
    fun conditionalBranchForward() {
        val asm = X86Assembler()
        val skip = asm.label()
        asm.je(skip)
        asm.nop()
        asm.mark(skip)
        asm.ret()

        val bytes = asm.toByteArray()
        // je rel32 = 0F 84 + 4-byte offset, then 1 nop, then ret
        assertEquals(0x0F.toByte(), bytes[0])
        assertEquals(0x84.toByte(), bytes[1])
        val offset = readInt32(bytes, 2)
        assertEquals(1, offset)
    }

    @Test
    fun conditionalBranchBackward() {
        val asm = X86Assembler()
        val loop = asm.mark()
        asm.nop()
        asm.jne(loop)

        val bytes = asm.toByteArray()
        // nop(1) + 0F 85 rel32(6) → offset = 0 - (1 + 6) = -7
        assertEquals(0x0F.toByte(), bytes[1])
        assertEquals(0x85.toByte(), bytes[2])
        val offset = readInt32(bytes, 3)
        assertEquals(-7, offset)
    }

    @Test
    fun allConditionalBranchVariants() {
        val conditions = listOf(
            ::runJe to X86Condition.EQUAL,
            ::runJne to X86Condition.NOT_EQUAL,
            ::runJl to X86Condition.LESS,
            ::runJge to X86Condition.GREATER_EQUAL,
            ::runJle to X86Condition.LESS_EQUAL,
            ::runJg to X86Condition.GREATER,
            ::runJb to X86Condition.BELOW,
            ::runJae to X86Condition.ABOVE_EQUAL,
            ::runJa to X86Condition.ABOVE,
            ::runJbe to X86Condition.BELOW_EQUAL,
            ::runJo to X86Condition.OVERFLOW,
            ::runJno to X86Condition.NOT_OVERFLOW,
            ::runJs to X86Condition.SIGN,
            ::runJns to X86Condition.NOT_SIGN,
        )

        for ((emitter, condition) in conditions) {
            val bytes = emitter()
            assertEquals(0x0F.toByte(), bytes[0], "prefix for $condition")
            assertEquals((0x80 + condition.ordinal).toByte(), bytes[1], "opcode for $condition")
        }
    }

    @Test
    fun callWithForwardLabel() {
        val asm = X86Assembler()
        val fn = asm.label()
        asm.call(fn)
        asm.ret()
        asm.mark(fn)
        asm.nop()
        asm.ret()

        val bytes = asm.toByteArray()
        assertEquals(0xE8.toByte(), bytes[0])
        val offset = readInt32(bytes, 1)
        assertEquals(1, offset) // skip the ret after call
    }

    @Test
    fun jccWithConditionEnum() {
        val asm = X86Assembler()
        val target = asm.label()
        asm.jcc(X86Condition.LESS, target)
        asm.mark(target)

        val bytes = asm.toByteArray()
        assertEquals(0x0F.toByte(), bytes[0])
        assertEquals((0x80 + X86Condition.LESS.ordinal).toByte(), bytes[1])
        assertEquals(0, readInt32(bytes, 2))
    }

    @Test
    fun multipleLabelsInSequence() {
        val asm = X86Assembler()
        val first = asm.label()
        val second = asm.label()
        val third = asm.label()

        asm.jmp(first)
        asm.mark(first)
        asm.jmp(second)
        asm.mark(second)
        asm.jmp(third)
        asm.mark(third)
        asm.ret()

        val bytes = asm.toByteArray()
        assertNotNull(bytes)
        // last instruction is ret
        assertEquals(0xC3.toByte(), bytes[bytes.size - 1])
    }

    @Test
    fun forwardAndBackwardMixed() {
        val asm = X86Assembler()
        val loopTop = asm.mark()
        val exit = asm.label()

        asm.nop()
        asm.je(exit)
        asm.nop()
        asm.jmp(loopTop)
        asm.mark(exit)
        asm.ret()

        val bytes = asm.toByteArray()
        assertNotNull(bytes)
        assertEquals(0xC3.toByte(), bytes[bytes.size - 1])
    }

    @Test
    fun doubleMarkThrows() {
        val asm = X86Assembler()
        val label = asm.mark()
        assertThrows(IllegalStateException::class.java) {
            asm.mark(label)
        }
    }

    @Test
    fun labelNotMarkedThrows() {
        val asm = X86Assembler()
        val label = asm.label()
        asm.jmp(label)
        assertThrows(IllegalStateException::class.java) {
            asm.toByteArray()
        }
    }

    @Test
    fun labelProperties() {
        val asm = X86Assembler()
        val forward = asm.label()
        assertFalse(forward.marked)
        assertEquals(0, forward.id)

        asm.mark(forward)
        assertTrue(forward.marked)

        val backward = asm.mark()
        assertTrue(backward.marked)
        assertEquals(1, backward.id)
    }

    @Test
    fun jzAndJnzAliases() {
        val asm = X86Assembler()
        val target = asm.label()
        asm.jz(target)
        asm.mark(target)

        val bytes = asm.toByteArray()
        assertEquals(0x0F.toByte(), bytes[0])
        assertEquals(0x84.toByte(), bytes[1]) // same as je
    }

    @Test
    fun typedAndStringLabelsCoexist() {
        val asm = X86Assembler()
        val typed = asm.label()
        asm.jmpLabel("string_target")
        asm.jmp(typed)
        asm.label("string_target")
        asm.mark(typed)
        asm.ret()

        val bytes = asm.toByteArray()
        assertEquals(0xC3.toByte(), bytes[bytes.size - 1])
    }

    @Test
    fun binarySearchPattern() {
        val asm = X86Assembler()
        val found = asm.label()
        val notFound = asm.label()
        val loop = asm.mark()

        asm.cmp(eax, ecx as X86Operand32)
        asm.jge(notFound)
        asm.je(found)
        asm.jmp(loop)

        asm.mark(found)
        asm.mov(eax, 1)
        asm.ret()

        asm.mark(notFound)
        asm.mov(eax, 0)
        asm.ret()

        val bytes = asm.toByteArray()
        assertNotNull(bytes)
        assertTrue(bytes.size > 10)
    }

    @Test
    fun manyLabels() {
        val asm = X86Assembler()
        val labels = (0 until 50).map { asm.label() }

        for (label in labels) {
            asm.jmp(label)
        }
        for (label in labels) {
            asm.mark(label)
            asm.nop()
        }
        asm.ret()

        val bytes = asm.toByteArray()
        assertEquals(0xC3.toByte(), bytes[bytes.size - 1])
    }

    @Test
    fun dslForwardAndBackward() {
        val code = x86 {
            val done = label()
            nop()
            jz(done)
            nop()
            mark(done)
            ret()
        }
        assertTrue(code.isNotEmpty())
        assertEquals(0xC3.toByte(), code[code.size - 1])
    }

    @Test
    fun dslCountdownLoop() {
        val code = x86 {
            val loop = mark()
            nop()
            nop()
            jne(loop)
            ret()
        }
        assertTrue(code.isNotEmpty())
    }

    @Test
    fun dslCallAndReturn() {
        val code = x86 {
            val fn = label()
            call(fn)
            ret()
            mark(fn)
            nop()
            ret()
        }
        assertTrue(code.isNotEmpty())
    }

    @Test
    fun dslIfElsePattern() {
        val code = x86 {
            val elseBlock = label()
            val end = label()

            nop()
            je(elseBlock)
            nop()
            jmp(end)

            mark(elseBlock)
            nop()

            mark(end)
            ret()
        }
        assertEquals(0xC3.toByte(), code[code.size - 1])
    }

    @Test
    fun dslNestedLoops() {
        val code = x86 {
            val outerLoop = mark()
            val innerDone = label()
            val outerDone = label()

            val innerLoop = mark()
            nop()
            je(innerDone)
            jmp(innerLoop)

            mark(innerDone)
            nop()
            jne(outerLoop)
            jmp(outerDone)

            mark(outerDone)
            ret()
        }
        assertTrue(code.isNotEmpty())
    }

    private fun runJe(): ByteArray { val asm = X86Assembler(); val l = asm.label(); asm.je(l); asm.mark(l); return asm.toByteArray() }
    private fun runJne(): ByteArray { val asm = X86Assembler(); val l = asm.label(); asm.jne(l); asm.mark(l); return asm.toByteArray() }
    private fun runJl(): ByteArray { val asm = X86Assembler(); val l = asm.label(); asm.jl(l); asm.mark(l); return asm.toByteArray() }
    private fun runJge(): ByteArray { val asm = X86Assembler(); val l = asm.label(); asm.jge(l); asm.mark(l); return asm.toByteArray() }
    private fun runJle(): ByteArray { val asm = X86Assembler(); val l = asm.label(); asm.jle(l); asm.mark(l); return asm.toByteArray() }
    private fun runJg(): ByteArray { val asm = X86Assembler(); val l = asm.label(); asm.jg(l); asm.mark(l); return asm.toByteArray() }
    private fun runJb(): ByteArray { val asm = X86Assembler(); val l = asm.label(); asm.jb(l); asm.mark(l); return asm.toByteArray() }
    private fun runJae(): ByteArray { val asm = X86Assembler(); val l = asm.label(); asm.jae(l); asm.mark(l); return asm.toByteArray() }
    private fun runJa(): ByteArray { val asm = X86Assembler(); val l = asm.label(); asm.ja(l); asm.mark(l); return asm.toByteArray() }
    private fun runJbe(): ByteArray { val asm = X86Assembler(); val l = asm.label(); asm.jbe(l); asm.mark(l); return asm.toByteArray() }
    private fun runJo(): ByteArray { val asm = X86Assembler(); val l = asm.label(); asm.jo(l); asm.mark(l); return asm.toByteArray() }
    private fun runJno(): ByteArray { val asm = X86Assembler(); val l = asm.label(); asm.jno(l); asm.mark(l); return asm.toByteArray() }
    private fun runJs(): ByteArray { val asm = X86Assembler(); val l = asm.label(); asm.js(l); asm.mark(l); return asm.toByteArray() }
    private fun runJns(): ByteArray { val asm = X86Assembler(); val l = asm.label(); asm.jns(l); asm.mark(l); return asm.toByteArray() }

    private fun readInt32(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
        ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
        ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
        ((bytes[offset + 3].toInt() and 0xFF) shl 24)
}
