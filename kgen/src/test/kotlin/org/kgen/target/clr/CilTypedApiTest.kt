package org.kgen.target.clr

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.kgen.target.clr.asm.CilAssembler
import org.kgen.target.clr.asm.CilLabel
import org.kgen.target.clr.asm.CilLocal
import org.kgen.target.clr.asm.CilToken
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CilTypedApiTest {

    // ── CilLabel ──

    @Test
    fun `defineLabel creates unique labels`() {
        val asm = CilAssembler()
        val a = asm.defineLabel()
        val b = asm.defineLabel()
        assertFalse(a == b)
    }

    @Test
    fun `markLabel sets position`() {
        val asm = CilAssembler()
        val label = asm.defineLabel()
        assertFalse(label.isMarked)
        asm.nop()
        asm.markLabel(label)
        assertTrue(label.isMarked)
    }

    @Test
    fun `markLabel twice throws`() {
        val asm = CilAssembler()
        val label = asm.defineLabel()
        asm.markLabel(label)
        assertThrows<IllegalArgumentException> {
            asm.markLabel(label)
        }
    }

    @Test
    fun `typed label forward branch`() {
        val asm = CilAssembler()
        val target = asm.defineLabel()
        asm.ldarg(0)           // 0: ldarg.0
        asm.brtrueS(target)    // 1: brtrue.s +2
        asm.ldcI4Auto(0)       // 3: ldc.i4.0
        asm.ret()              // 4: ret
        asm.markLabel(target)  // 5:
        asm.ldcI4Auto(1)       // 5: ldc.i4.1
        asm.ret()              // 6: ret

        val code = asm.toByteArray()
        assertEquals(0x2D, code[1].toInt() and 0xFF)
        assertEquals(2, code[2].toInt()) // offset = 5 - 3 = 2
    }

    @Test
    fun `typed label backward branch`() {
        val asm = CilAssembler()
        val loop = asm.defineLabel()
        asm.markLabel(loop)
        asm.ldloc(0)
        asm.ldarg(0)
        asm.clt()
        asm.brtrueS(loop)

        val code = asm.toByteArray()
        assertEquals(0x2D, code[4].toInt() and 0xFF)
        assertTrue(code[5].toInt() < 0) // backward
    }

    @Test
    fun `typed label long branch`() {
        val asm = CilAssembler()
        val target = asm.defineLabel()
        asm.ldarg(0)
        asm.brtrue(target)    // long branch
        asm.ldcI4Auto(0)
        asm.ret()
        asm.markLabel(target)
        asm.ldcI4Auto(1)
        asm.ret()

        val code = asm.toByteArray()
        assertEquals(0x3A, code[1].toInt() and 0xFF) // brtrue (long)
    }

    @Test
    fun `unresolved typed label throws on toByteArray`() {
        val asm = CilAssembler()
        val target = asm.defineLabel()
        asm.br(target)
        assertThrows<IllegalArgumentException> {
            asm.toByteArray()
        }
    }

    // ── CilLocal ──

    @Test
    fun `declareLocal assigns sequential indices`() {
        val asm = CilAssembler()
        val a = asm.declareLocal(CilSigType.I4)
        val b = asm.declareLocal(CilSigType.I8)
        val c = asm.declareLocal(CilSigType.R8)

        assertEquals(0, a.index)
        assertEquals(1, b.index)
        assertEquals(2, c.index)
        assertEquals(CilSigType.I4, a.type)
        assertEquals(CilSigType.I8, b.type)
        assertEquals(CilSigType.R8, c.type)
    }

    @Test
    fun `locals list tracks all declared locals`() {
        val asm = CilAssembler()
        asm.declareLocal(CilSigType.I4)
        asm.declareLocal(CilSigType.STRING)
        assertEquals(2, asm.localCount)
        assertEquals(2, asm.locals.size)
    }

    @Test
    fun `typed local ldloc stloc`() {
        val asm = CilAssembler()
        val counter = asm.declareLocal(CilSigType.I4)
        asm.ldcI4Auto(0)
        asm.stloc(counter)    // stloc.0
        asm.ldloc(counter)    // ldloc.0
        asm.ret()

        val code = asm.toByteArray()
        assertEquals(0x0A, code[1].toInt() and 0xFF) // stloc.0
        assertEquals(0x06, code[2].toInt() and 0xFF) // ldloc.0
    }

    @Test
    fun `typed local ldloca`() {
        val asm = CilAssembler()
        val x = asm.declareLocal(CilSigType.I4)
        asm.ldloca(x)

        val code = asm.toByteArray()
        assertEquals(0x12, code[0].toInt() and 0xFF) // ldloca.s
        assertEquals(0, code[1].toInt() and 0xFF)     // index 0
    }

    @Test
    fun `typed local with high index`() {
        val asm = CilAssembler()
        for (i in 0 until 10) asm.declareLocal(CilSigType.I4)
        val tenth = asm.locals[9]

        asm.ldloc(tenth)

        val code = asm.toByteArray()
        assertEquals(0x11, code[0].toInt() and 0xFF) // ldloc.s
        assertEquals(9, code[1].toInt() and 0xFF)
    }

    // ── CilToken ──

    @Test
    fun `CilToken methodDef`() {
        val token = CilToken.methodDef(1)
        assertEquals(0x06000001, token.value)
        assertTrue(token.isMethodDefinition)
        assertFalse(token.isMemberRef)
        assertEquals(0x06, token.tableId)
        assertEquals(1, token.rowIndex)
    }

    @Test
    fun `CilToken memberRef`() {
        val token = CilToken.memberRef(3)
        assertEquals(0x0A000003, token.value)
        assertTrue(token.isMemberRef)
        assertEquals(3, token.rowIndex)
    }

    @Test
    fun `CilToken typeRef`() {
        val token = CilToken.typeRef(1)
        assertTrue(token.isTypeRef)
        assertEquals(0x01000001, token.value)
    }

    @Test
    fun `CilToken field`() {
        val token = CilToken.field(2)
        assertTrue(token.isField)
        assertEquals(0x04000002, token.value)
    }

    @Test
    fun `CilToken userString`() {
        val token = CilToken.userString(5)
        assertTrue(token.isUserString)
        assertEquals(0x70000005, token.value)
    }

    @Test
    fun `CilToken fromRaw`() {
        val token = CilToken.fromRaw(0x0A000001)
        assertTrue(token.isMemberRef)
        assertEquals(1, token.rowIndex)
    }

    @Test
    fun `CilToken equality`() {
        val a = CilToken.methodDef(1)
        val b = CilToken.methodDef(1)
        val c = CilToken.methodDef(2)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertFalse(a == c)
    }

    @Test
    fun `typed token in call instruction`() {
        val asm = CilAssembler()
        val token = CilToken.memberRef(1)
        asm.call(token)

        val code = asm.toByteArray()
        assertEquals(0x28, code[0].toInt() and 0xFF) // call
        assertEquals(0x01, code[1].toInt() and 0xFF) // token low byte
        assertEquals(0x00, code[2].toInt() and 0xFF)
        assertEquals(0x0A, code[4].toInt() and 0xFF) // 0x0A table
    }

    @Test
    fun `typed token in newobj`() {
        val asm = CilAssembler()
        val ctorToken = CilToken.memberRef(1)
        asm.newobj(ctorToken)

        val code = asm.toByteArray()
        assertEquals(0x73, code[0].toInt() and 0xFF) // newobj
    }

    @Test
    fun `typed token in field access`() {
        val asm = CilAssembler()
        val fieldToken = CilToken.field(1)
        asm.ldsfld(fieldToken)

        val code = asm.toByteArray()
        assertEquals(0x7E, code[0].toInt() and 0xFF) // ldsfld
    }

    // ── Combined typed API ──

    @Test
    fun `full method with typed labels locals and tokens`() {
        val asm = CilAssembler()
        val counter = asm.declareLocal(CilSigType.I4)
        val sum = asm.declareLocal(CilSigType.I4)
        val loopStart = asm.defineLabel()
        val done = asm.defineLabel()

        // sum = 0
        asm.ldcI4Auto(0)
        asm.stloc(sum)

        // counter = arg0
        asm.ldarg(0)
        asm.stloc(counter)

        // loop:
        asm.markLabel(loopStart)

        // if (counter <= 0) goto done
        asm.ldloc(counter)
        asm.ldcI4Auto(0)
        asm.ble(done)

        // sum += counter
        asm.ldloc(sum)
        asm.ldloc(counter)
        asm.add()
        asm.stloc(sum)

        // counter--
        asm.ldloc(counter)
        asm.ldcI4Auto(1)
        asm.sub()
        asm.stloc(counter)

        // goto loop
        asm.br(loopStart)

        // done:
        asm.markLabel(done)
        asm.ldloc(sum)
        asm.ret()

        val code = asm.toByteArray()
        assertTrue(code.isNotEmpty())
        assertEquals(2, asm.localCount)
        assertEquals(CilSigType.I4, asm.locals[0].type)
        assertEquals(CilSigType.I4, asm.locals[1].type)
    }

    @Test
    fun `CilClassBuilder returns CilToken from addMemberRef`() {
        val builder = CilClassBuilder("TestAsm", "MyClass")
        val token = builder.addMemberRef(1, ".ctor",
            CilClassBuilder.instanceSig(CilSigType.VOID))

        assertTrue(token.isMemberRef)
        assertEquals(0x0A000001, token.value)

        // Can use directly in assembler
        val asm = CilAssembler()
        asm.call(token)
        val code = asm.toByteArray()
        assertEquals(5, code.size)
    }
}
