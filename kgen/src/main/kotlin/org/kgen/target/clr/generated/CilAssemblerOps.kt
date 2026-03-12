// Generated from cil/opcodes.json — do not edit
package org.kgen.target.clr.generated

import org.kgen.target.clr.asm.CilLabel
import org.kgen.target.clr.asm.CilToken

/**
 * Generated assembler methods for CIL bytecode (ECMA-335).
 *
 * Each method emits the corresponding CIL instruction.
 * The concrete assembler provides emit primitives and may override
 * methods for smart encoding (e.g., short/long local/arg switching).
 */
abstract class CilAssemblerOps {

    protected abstract fun emitByte(v: Int)
    protected abstract fun emitU16(v: Int)
    protected abstract fun emitI32(v: Int)
    protected abstract fun emitI64(v: Long)
    protected abstract fun emitF32(v: Float)
    protected abstract fun emitF64(v: Double)
    protected abstract fun emitToken(token: Int)
    protected abstract fun emitBranch(opcode: Int, twoBytePrefix: Boolean, label: CilLabel)

    /** Do nothing. */
    fun nop() {
        emitByte(0)
    }

    /** Breakpoint for debugger. */
    fun break_() {
        emitByte(1)
    }

    /** Load argument 0. */
    fun ldarg0() {
        emitByte(2)
    }

    /** Load argument 1. */
    fun ldarg1() {
        emitByte(3)
    }

    /** Load argument 2. */
    fun ldarg2() {
        emitByte(4)
    }

    /** Load argument 3. */
    fun ldarg3() {
        emitByte(5)
    }

    /** Load local variable 0. */
    fun ldloc0() {
        emitByte(6)
    }

    /** Load local variable 1. */
    fun ldloc1() {
        emitByte(7)
    }

    /** Load local variable 2. */
    fun ldloc2() {
        emitByte(8)
    }

    /** Load local variable 3. */
    fun ldloc3() {
        emitByte(9)
    }

    /** Store to local variable 0. */
    fun stloc0() {
        emitByte(10)
    }

    /** Store to local variable 1. */
    fun stloc1() {
        emitByte(11)
    }

    /** Store to local variable 2. */
    fun stloc2() {
        emitByte(12)
    }

    /** Store to local variable 3. */
    fun stloc3() {
        emitByte(13)
    }

    /** Load argument by short index. */
    open fun ldargS(value: Int) {
        emitByte(14); emitByte(value)
    }

    /** Load address of argument by short index. */
    open fun ldargaS(value: Int) {
        emitByte(15); emitByte(value)
    }

    /** Store to argument by short index. */
    open fun stargS(value: Int) {
        emitByte(16); emitByte(value)
    }

    /** Load local variable by short index. */
    open fun ldlocS(value: Int) {
        emitByte(17); emitByte(value)
    }

    /** Load address of local variable by short index. */
    open fun ldlocaS(value: Int) {
        emitByte(18); emitByte(value)
    }

    /** Store to local variable by short index. */
    open fun stlocS(value: Int) {
        emitByte(19); emitByte(value)
    }

    /** Push null reference. */
    fun ldnull() {
        emitByte(20)
    }

    /** Push int constant -1. */
    fun ldcI4M1() {
        emitByte(21)
    }

    /** Push int constant 0. */
    fun ldcI4_0() {
        emitByte(22)
    }

    /** Push int constant 1. */
    fun ldcI4_1() {
        emitByte(23)
    }

    /** Push int constant 2. */
    fun ldcI4_2() {
        emitByte(24)
    }

    /** Push int constant 3. */
    fun ldcI4_3() {
        emitByte(25)
    }

    /** Push int constant 4. */
    fun ldcI4_4() {
        emitByte(26)
    }

    /** Push int constant 5. */
    fun ldcI4_5() {
        emitByte(27)
    }

    /** Push int constant 6. */
    fun ldcI4_6() {
        emitByte(28)
    }

    /** Push int constant 7. */
    fun ldcI4_7() {
        emitByte(29)
    }

    /** Push int constant 8. */
    fun ldcI4_8() {
        emitByte(30)
    }

    /** Push signed byte as int. */
    open fun ldcI4S(value: Int) {
        emitByte(31); emitByte(value)
    }

    /** Push 32-bit integer constant. */
    fun ldcI4(value: Int) {
        emitByte(32); emitI32(value)
    }

    /** Push 64-bit integer constant. */
    fun ldcI8(value: Long) {
        emitByte(33); emitI64(value)
    }

    /** Push 32-bit float constant. */
    fun ldcR4(value: Float) {
        emitByte(34); emitF32(value)
    }

    /** Push 64-bit double constant. */
    fun ldcR8(value: Double) {
        emitByte(35); emitF64(value)
    }

    /** Duplicate top stack value. */
    fun dup() {
        emitByte(37)
    }

    /** Pop top value from stack. */
    fun pop() {
        emitByte(38)
    }

    /** Jump to method. */
    fun jmp(token: CilToken) {
        emitByte(39); emitToken(token.value)
    }

    /** Call method. */
    fun call(token: CilToken) {
        emitByte(40); emitToken(token.value)
    }

    /** Call method via function pointer. */
    fun calli(token: CilToken) {
        emitByte(41); emitToken(token.value)
    }

    /** Return from method. */
    fun ret() {
        emitByte(42)
    }

    /** Unconditional short branch. */
    fun brS(label: CilLabel) {
        emitBranch(43, false, label)
    }

    /** Branch if false/null/zero (short). */
    fun brfalseS(label: CilLabel) {
        emitBranch(44, false, label)
    }

    /** Branch if true/non-null/non-zero (short). */
    fun brtrueS(label: CilLabel) {
        emitBranch(45, false, label)
    }

    /** Branch if equal (short). */
    fun beqS(label: CilLabel) {
        emitBranch(46, false, label)
    }

    /** Branch if greater than or equal (short). */
    fun bgeS(label: CilLabel) {
        emitBranch(47, false, label)
    }

    /** Branch if greater than (short). */
    fun bgtS(label: CilLabel) {
        emitBranch(48, false, label)
    }

    /** Branch if less than or equal (short). */
    fun bleS(label: CilLabel) {
        emitBranch(49, false, label)
    }

    /** Branch if less than (short). */
    fun bltS(label: CilLabel) {
        emitBranch(50, false, label)
    }

    /** Branch if not equal, unsigned (short). */
    fun bneUnS(label: CilLabel) {
        emitBranch(51, false, label)
    }

    /** Branch if greater than or equal, unsigned (short). */
    fun bgeUnS(label: CilLabel) {
        emitBranch(52, false, label)
    }

    /** Branch if greater than, unsigned (short). */
    fun bgtUnS(label: CilLabel) {
        emitBranch(53, false, label)
    }

    /** Branch if less than or equal, unsigned (short). */
    fun bleUnS(label: CilLabel) {
        emitBranch(54, false, label)
    }

    /** Branch if less than, unsigned (short). */
    fun bltUnS(label: CilLabel) {
        emitBranch(55, false, label)
    }

    /** Unconditional branch. */
    fun br(label: CilLabel) {
        emitBranch(56, false, label)
    }

    /** Branch if false/null/zero. */
    fun brfalse(label: CilLabel) {
        emitBranch(57, false, label)
    }

    /** Branch if true/non-null/non-zero. */
    fun brtrue(label: CilLabel) {
        emitBranch(58, false, label)
    }

    /** Branch if equal. */
    fun beq(label: CilLabel) {
        emitBranch(59, false, label)
    }

    /** Branch if greater than or equal. */
    fun bge(label: CilLabel) {
        emitBranch(60, false, label)
    }

    /** Branch if greater than. */
    fun bgt(label: CilLabel) {
        emitBranch(61, false, label)
    }

    /** Branch if less than or equal. */
    fun ble(label: CilLabel) {
        emitBranch(62, false, label)
    }

    /** Branch if less than. */
    fun blt(label: CilLabel) {
        emitBranch(63, false, label)
    }

    /** Branch if not equal, unsigned. */
    fun bneUn(label: CilLabel) {
        emitBranch(64, false, label)
    }

    /** Branch if greater than or equal, unsigned. */
    fun bgeUn(label: CilLabel) {
        emitBranch(65, false, label)
    }

    /** Branch if greater than, unsigned. */
    fun bgtUn(label: CilLabel) {
        emitBranch(66, false, label)
    }

    /** Branch if less than or equal, unsigned. */
    fun bleUn(label: CilLabel) {
        emitBranch(67, false, label)
    }

    /** Branch if less than, unsigned. */
    fun bltUn(label: CilLabel) {
        emitBranch(68, false, label)
    }

    /** Table-based branch. */
    abstract fun switch_(labels: List<CilLabel>)

    /** Indirect load signed byte. */
    fun ldindI1() {
        emitByte(70)
    }

    /** Indirect load unsigned byte. */
    fun ldindU1() {
        emitByte(71)
    }

    /** Indirect load signed short. */
    fun ldindI2() {
        emitByte(72)
    }

    /** Indirect load unsigned short. */
    fun ldindU2() {
        emitByte(73)
    }

    /** Indirect load 32-bit int. */
    fun ldindI4() {
        emitByte(74)
    }

    /** Indirect load unsigned 32-bit int. */
    fun ldindU4() {
        emitByte(75)
    }

    /** Indirect load 64-bit int. */
    fun ldindI8() {
        emitByte(76)
    }

    /** Indirect load native int. */
    fun ldindI() {
        emitByte(77)
    }

    /** Indirect load 32-bit float. */
    fun ldindR4() {
        emitByte(78)
    }

    /** Indirect load 64-bit float. */
    fun ldindR8() {
        emitByte(79)
    }

    /** Indirect load object reference. */
    fun ldindRef() {
        emitByte(80)
    }

    /** Indirect store object reference. */
    fun stindRef() {
        emitByte(81)
    }

    /** Indirect store byte. */
    fun stindI1() {
        emitByte(82)
    }

    /** Indirect store short. */
    fun stindI2() {
        emitByte(83)
    }

    /** Indirect store 32-bit int. */
    fun stindI4() {
        emitByte(84)
    }

    /** Indirect store 64-bit int. */
    fun stindI8() {
        emitByte(85)
    }

    /** Indirect store 32-bit float. */
    fun stindR4() {
        emitByte(86)
    }

    /** Indirect store 64-bit float. */
    fun stindR8() {
        emitByte(87)
    }

    /** Add two values. */
    fun add() {
        emitByte(88)
    }

    /** Subtract two values. */
    fun sub() {
        emitByte(89)
    }

    /** Multiply two values. */
    fun mul() {
        emitByte(90)
    }

    /** Divide two values. */
    fun div() {
        emitByte(91)
    }

    /** Divide two unsigned values. */
    fun divUn() {
        emitByte(92)
    }

    /** Remainder of two values. */
    fun rem() {
        emitByte(93)
    }

    /** Remainder of two unsigned values. */
    fun remUn() {
        emitByte(94)
    }

    /** Bitwise AND. */
    fun and() {
        emitByte(95)
    }

    /** Bitwise OR. */
    fun or() {
        emitByte(96)
    }

    /** Bitwise XOR. */
    fun xor() {
        emitByte(97)
    }

    /** Shift left. */
    fun shl() {
        emitByte(98)
    }

    /** Arithmetic shift right. */
    fun shr() {
        emitByte(99)
    }

    /** Logical shift right. */
    fun shrUn() {
        emitByte(100)
    }

    /** Negate value. */
    fun neg() {
        emitByte(101)
    }

    /** Bitwise complement. */
    fun not() {
        emitByte(102)
    }

    /** Convert to signed byte. */
    fun convI1() {
        emitByte(103)
    }

    /** Convert to signed short. */
    fun convI2() {
        emitByte(104)
    }

    /** Convert to 32-bit int. */
    fun convI4() {
        emitByte(105)
    }

    /** Convert to 64-bit int. */
    fun convI8() {
        emitByte(106)
    }

    /** Convert to 32-bit float. */
    fun convR4() {
        emitByte(107)
    }

    /** Convert to 64-bit float. */
    fun convR8() {
        emitByte(108)
    }

    /** Convert to unsigned 32-bit int. */
    fun convU4() {
        emitByte(109)
    }

    /** Convert to unsigned 64-bit int. */
    fun convU8() {
        emitByte(110)
    }

    /** Call virtual method on object. */
    fun callvirt(token: CilToken) {
        emitByte(111); emitToken(token.value)
    }

    /** Copy value type. */
    fun cpobj(token: CilToken) {
        emitByte(112); emitToken(token.value)
    }

    /** Load value type from address. */
    fun ldobj(token: CilToken) {
        emitByte(113); emitToken(token.value)
    }

    /** Load string literal. */
    fun ldstr(token: CilToken) {
        emitByte(114); emitToken(token.value)
    }

    /** Create new object and call constructor. */
    fun newobj(token: CilToken) {
        emitByte(115); emitToken(token.value)
    }

    /** Cast object to type, throw on failure. */
    fun castclass(token: CilToken) {
        emitByte(116); emitToken(token.value)
    }

    /** Test if object is instance of type. */
    fun isinst(token: CilToken) {
        emitByte(117); emitToken(token.value)
    }

    /** Convert unsigned to float. */
    fun convRUn() {
        emitByte(118)
    }

    /** Unbox value type. */
    fun unbox(token: CilToken) {
        emitByte(121); emitToken(token.value)
    }

    /** Throw exception. */
    fun throw_() {
        emitByte(122)
    }

    /** Load instance field value. */
    fun ldfld(token: CilToken) {
        emitByte(123); emitToken(token.value)
    }

    /** Load address of instance field. */
    fun ldflda(token: CilToken) {
        emitByte(124); emitToken(token.value)
    }

    /** Store to instance field. */
    fun stfld(token: CilToken) {
        emitByte(125); emitToken(token.value)
    }

    /** Load static field value. */
    fun ldsfld(token: CilToken) {
        emitByte(126); emitToken(token.value)
    }

    /** Load address of static field. */
    fun ldsflda(token: CilToken) {
        emitByte(127); emitToken(token.value)
    }

    /** Store to static field. */
    fun stsfld(token: CilToken) {
        emitByte(128); emitToken(token.value)
    }

    /** Store value type to address. */
    fun stobj(token: CilToken) {
        emitByte(129); emitToken(token.value)
    }

    /** Convert unsigned to signed byte with overflow check. */
    fun convOvfI1Un() {
        emitByte(130)
    }

    /** Convert unsigned to signed short with overflow check. */
    fun convOvfI2Un() {
        emitByte(131)
    }

    /** Convert unsigned to 32-bit int with overflow check. */
    fun convOvfI4Un() {
        emitByte(132)
    }

    /** Convert unsigned to 64-bit int with overflow check. */
    fun convOvfI8Un() {
        emitByte(133)
    }

    /** Convert unsigned to unsigned byte with overflow check. */
    fun convOvfU1Un() {
        emitByte(134)
    }

    /** Convert unsigned to unsigned short with overflow check. */
    fun convOvfU2Un() {
        emitByte(135)
    }

    /** Convert unsigned to unsigned 32-bit with overflow check. */
    fun convOvfU4Un() {
        emitByte(136)
    }

    /** Convert unsigned to unsigned 64-bit with overflow check. */
    fun convOvfU8Un() {
        emitByte(137)
    }

    /** Convert unsigned to native int with overflow check. */
    fun convOvfIUn() {
        emitByte(138)
    }

    /** Convert unsigned to native uint with overflow check. */
    fun convOvfUUn() {
        emitByte(139)
    }

    /** Box value type. */
    fun box(token: CilToken) {
        emitByte(140); emitToken(token.value)
    }

    /** Create zero-based one-dimensional array. */
    fun newarr(token: CilToken) {
        emitByte(141); emitToken(token.value)
    }

    /** Get array length. */
    fun ldlen() {
        emitByte(142)
    }

    /** Load address of array element. */
    fun ldelema(token: CilToken) {
        emitByte(143); emitToken(token.value)
    }

    /** Load signed byte array element. */
    fun ldelemI1() {
        emitByte(144)
    }

    /** Load unsigned byte array element. */
    fun ldelemU1() {
        emitByte(145)
    }

    /** Load signed short array element. */
    fun ldelemI2() {
        emitByte(146)
    }

    /** Load unsigned short array element. */
    fun ldelemU2() {
        emitByte(147)
    }

    /** Load 32-bit int array element. */
    fun ldelemI4() {
        emitByte(148)
    }

    /** Load unsigned 32-bit int array element. */
    fun ldelemU4() {
        emitByte(149)
    }

    /** Load 64-bit int array element. */
    fun ldelemI8() {
        emitByte(150)
    }

    /** Load native int array element. */
    fun ldelemI() {
        emitByte(151)
    }

    /** Load 32-bit float array element. */
    fun ldelemR4() {
        emitByte(152)
    }

    /** Load 64-bit float array element. */
    fun ldelemR8() {
        emitByte(153)
    }

    /** Load object reference array element. */
    fun ldelemRef() {
        emitByte(154)
    }

    /** Store native int to array element. */
    fun stelemI() {
        emitByte(155)
    }

    /** Store byte to array element. */
    fun stelemI1() {
        emitByte(156)
    }

    /** Store short to array element. */
    fun stelemI2() {
        emitByte(157)
    }

    /** Store 32-bit int to array element. */
    fun stelemI4() {
        emitByte(158)
    }

    /** Store 64-bit int to array element. */
    fun stelemI8() {
        emitByte(159)
    }

    /** Store 32-bit float to array element. */
    fun stelemR4() {
        emitByte(160)
    }

    /** Store 64-bit float to array element. */
    fun stelemR8() {
        emitByte(161)
    }

    /** Store object reference to array element. */
    fun stelemRef() {
        emitByte(162)
    }

    /** Load array element by type token. */
    fun ldelem(token: CilToken) {
        emitByte(163); emitToken(token.value)
    }

    /** Store array element by type token. */
    fun stelem(token: CilToken) {
        emitByte(164); emitToken(token.value)
    }

    /** Unbox or cast to type. */
    fun unboxAny(token: CilToken) {
        emitByte(165); emitToken(token.value)
    }

    /** Convert to signed byte with overflow check. */
    fun convOvfI1() {
        emitByte(179)
    }

    /** Convert to unsigned byte with overflow check. */
    fun convOvfU1() {
        emitByte(180)
    }

    /** Convert to signed short with overflow check. */
    fun convOvfI2() {
        emitByte(181)
    }

    /** Convert to unsigned short with overflow check. */
    fun convOvfU2() {
        emitByte(182)
    }

    /** Convert to 32-bit int with overflow check. */
    fun convOvfI4() {
        emitByte(183)
    }

    /** Convert to unsigned 32-bit with overflow check. */
    fun convOvfU4() {
        emitByte(184)
    }

    /** Convert to 64-bit int with overflow check. */
    fun convOvfI8() {
        emitByte(185)
    }

    /** Convert to unsigned 64-bit with overflow check. */
    fun convOvfU8() {
        emitByte(186)
    }

    /** Load address from typed reference. */
    fun refanyval(token: CilToken) {
        emitByte(194); emitToken(token.value)
    }

    /** Throw if value is not finite. */
    fun ckfinite() {
        emitByte(195)
    }

    /** Create typed reference. */
    fun mkrefany(token: CilToken) {
        emitByte(198); emitToken(token.value)
    }

    /** Load runtime handle for metadata token. */
    fun ldtoken(token: CilToken) {
        emitByte(208); emitToken(token.value)
    }

    /** Convert to unsigned short. */
    fun convU2() {
        emitByte(209)
    }

    /** Convert to unsigned byte. */
    fun convU1() {
        emitByte(210)
    }

    /** Convert to native int. */
    fun convI() {
        emitByte(211)
    }

    /** Convert to native int with overflow check. */
    fun convOvfI() {
        emitByte(212)
    }

    /** Convert to native uint with overflow check. */
    fun convOvfU() {
        emitByte(213)
    }

    /** Add with overflow check. */
    fun addOvf() {
        emitByte(214)
    }

    /** Add unsigned with overflow check. */
    fun addOvfUn() {
        emitByte(215)
    }

    /** Multiply with overflow check. */
    fun mulOvf() {
        emitByte(216)
    }

    /** Multiply unsigned with overflow check. */
    fun mulOvfUn() {
        emitByte(217)
    }

    /** Subtract with overflow check. */
    fun subOvf() {
        emitByte(218)
    }

    /** Subtract unsigned with overflow check. */
    fun subOvfUn() {
        emitByte(219)
    }

    /** End finally or fault handler. */
    fun endfinally() {
        emitByte(220)
    }

    /** Exit protected region. */
    fun leave(label: CilLabel) {
        emitBranch(221, false, label)
    }

    /** Exit protected region (short). */
    fun leaveS(label: CilLabel) {
        emitBranch(222, false, label)
    }

    /** Indirect store native int. */
    fun stindI() {
        emitByte(223)
    }

    /** Convert to native uint. */
    fun convU() {
        emitByte(224)
    }

    /** Compare equal. */
    fun ceq() {
        emitByte(0xFE); emitByte(1)
    }

    /** Compare greater than. */
    fun cgt() {
        emitByte(0xFE); emitByte(2)
    }

    /** Compare greater than, unsigned. */
    fun cgtUn() {
        emitByte(0xFE); emitByte(3)
    }

    /** Compare less than. */
    fun clt() {
        emitByte(0xFE); emitByte(4)
    }

    /** Compare less than, unsigned. */
    fun cltUn() {
        emitByte(0xFE); emitByte(5)
    }

    /** Load method pointer. */
    fun ldftn(token: CilToken) {
        emitByte(0xFE); emitByte(6); emitToken(token.value)
    }

    /** Load virtual method pointer. */
    fun ldvirtftn(token: CilToken) {
        emitByte(0xFE); emitByte(7); emitToken(token.value)
    }

    /** Load argument by wide index. */
    open fun ldargW(index: Int) {
        emitByte(0xFE); emitByte(9); emitU16(index)
    }

    /** Load address of argument by wide index. */
    open fun ldargaW(index: Int) {
        emitByte(0xFE); emitByte(10); emitU16(index)
    }

    /** Store to argument by wide index. */
    open fun stargW(index: Int) {
        emitByte(0xFE); emitByte(11); emitU16(index)
    }

    /** Load local variable by wide index. */
    open fun ldlocW(index: Int) {
        emitByte(0xFE); emitByte(12); emitU16(index)
    }

    /** Load address of local variable by wide index. */
    open fun ldlocaW(index: Int) {
        emitByte(0xFE); emitByte(13); emitU16(index)
    }

    /** Store to local variable by wide index. */
    open fun stlocW(index: Int) {
        emitByte(0xFE); emitByte(14); emitU16(index)
    }

    /** Allocate space on local stack. */
    fun localloc() {
        emitByte(0xFE); emitByte(15)
    }

    /** End exception filter. */
    fun endfilter() {
        emitByte(0xFE); emitByte(17)
    }

    /** Pointer alignment prefix. */
    open fun unaligned(value: Int) {
        emitByte(0xFE); emitByte(18); emitByte(value)
    }

    /** Volatile memory access prefix. */
    fun volatile_() {
        emitByte(0xFE); emitByte(19)
    }

    /** Tail call prefix. */
    fun tail() {
        emitByte(0xFE); emitByte(20)
    }

    /** Initialize value type at address. */
    fun initobj(token: CilToken) {
        emitByte(0xFE); emitByte(21); emitToken(token.value)
    }

    /** Constrained virtual call prefix. */
    fun constrained(token: CilToken) {
        emitByte(0xFE); emitByte(22); emitToken(token.value)
    }

    /** Copy block of memory. */
    fun cpblk() {
        emitByte(0xFE); emitByte(23)
    }

    /** Initialize block of memory. */
    fun initblk() {
        emitByte(0xFE); emitByte(24)
    }

    /** Rethrow current exception. */
    fun rethrow() {
        emitByte(0xFE); emitByte(26)
    }

    /** Get size of value type. */
    fun sizeof(token: CilToken) {
        emitByte(0xFE); emitByte(28); emitToken(token.value)
    }

    /** Get type from typed reference. */
    fun refanytype() {
        emitByte(0xFE); emitByte(29)
    }

    /** Readonly array element access prefix. */
    fun readonly() {
        emitByte(0xFE); emitByte(30)
    }

}
