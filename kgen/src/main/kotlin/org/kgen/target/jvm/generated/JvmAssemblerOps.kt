// Generated from jvm/opcodes.json — do not edit
package org.kgen.target.jvm.generated

/**
 * Generated assembler methods for JVM bytecode.
 *
 * Each method emits the corresponding bytecode instruction.
 * Methods are `open` where the concrete assembler may need to override
 * (e.g., branch instructions with label support, ldc with size switching).
 */
abstract class JvmAssemblerOps {

    protected abstract fun emitByte(b: Int)
    protected abstract fun emitShort(v: Int)

    /** Do nothing. */
    fun nop() {
        emitByte(0)
    }

    /** Push null reference. */
    fun aconstNull() {
        emitByte(1)
    }

    /** Push int constant -1. */
    fun iconstM1() {
        emitByte(2)
    }

    /** Push int constant 0. */
    fun iconst0() {
        emitByte(3)
    }

    /** Push int constant 1. */
    fun iconst1() {
        emitByte(4)
    }

    /** Push int constant 2. */
    fun iconst2() {
        emitByte(5)
    }

    /** Push int constant 3. */
    fun iconst3() {
        emitByte(6)
    }

    /** Push int constant 4. */
    fun iconst4() {
        emitByte(7)
    }

    /** Push int constant 5. */
    fun iconst5() {
        emitByte(8)
    }

    /** Push long constant 0. */
    fun lconst0() {
        emitByte(9)
    }

    /** Push long constant 1. */
    fun lconst1() {
        emitByte(10)
    }

    /** Push float constant 0.0. */
    fun fconst0() {
        emitByte(11)
    }

    /** Push float constant 1.0. */
    fun fconst1() {
        emitByte(12)
    }

    /** Push float constant 2.0. */
    fun fconst2() {
        emitByte(13)
    }

    /** Push double constant 0.0. */
    fun dconst0() {
        emitByte(14)
    }

    /** Push double constant 1.0. */
    fun dconst1() {
        emitByte(15)
    }

    /** Push byte as int. */
    fun bipush(value: Int) {
        emitByte(16); emitByte(value and 0xFF)
    }

    /** Push short as int. */
    fun sipush(value: Int) {
        emitByte(17); emitShort(value)
    }

    /** Push constant pool entry (int, float, string, class). */
    open fun ldc(index: Int) {
        emitByte(18); emitByte(index)
    }

    /** Push constant pool entry (wide index). */
    fun ldcW(index: Int) {
        emitByte(19); emitShort(index)
    }

    /** Push long or double from constant pool. */
    fun ldc2w(index: Int) {
        emitByte(20); emitShort(index)
    }

    /** Load int from local variable. */
    open fun iload(index: Int) {
        if (index in 0..3) emitByte(26 + index)
        else { emitByte(21); emitByte(index) }
    }

    /** Load long from local variable. */
    open fun lload(index: Int) {
        if (index in 0..3) emitByte(30 + index)
        else { emitByte(22); emitByte(index) }
    }

    /** Load float from local variable. */
    open fun fload(index: Int) {
        if (index in 0..3) emitByte(34 + index)
        else { emitByte(23); emitByte(index) }
    }

    /** Load double from local variable. */
    open fun dload(index: Int) {
        if (index in 0..3) emitByte(38 + index)
        else { emitByte(24); emitByte(index) }
    }

    /** Load reference from local variable. */
    open fun aload(index: Int) {
        if (index in 0..3) emitByte(42 + index)
        else { emitByte(25); emitByte(index) }
    }

    /** Load int from array. */
    fun iaload() {
        emitByte(46)
    }

    /** Load long from array. */
    fun laload() {
        emitByte(47)
    }

    /** Load float from array. */
    fun faload() {
        emitByte(48)
    }

    /** Load double from array. */
    fun daload() {
        emitByte(49)
    }

    /** Load reference from array. */
    fun aaload() {
        emitByte(50)
    }

    /** Load byte/boolean from array. */
    fun baload() {
        emitByte(51)
    }

    /** Load char from array. */
    fun caload() {
        emitByte(52)
    }

    /** Load short from array. */
    fun saload() {
        emitByte(53)
    }

    /** Store int to local variable. */
    open fun istore(index: Int) {
        if (index in 0..3) emitByte(59 + index)
        else { emitByte(54); emitByte(index) }
    }

    /** Store long to local variable. */
    open fun lstore(index: Int) {
        if (index in 0..3) emitByte(63 + index)
        else { emitByte(55); emitByte(index) }
    }

    /** Store float to local variable. */
    open fun fstore(index: Int) {
        if (index in 0..3) emitByte(67 + index)
        else { emitByte(56); emitByte(index) }
    }

    /** Store double to local variable. */
    open fun dstore(index: Int) {
        if (index in 0..3) emitByte(71 + index)
        else { emitByte(57); emitByte(index) }
    }

    /** Store reference to local variable. */
    open fun astore(index: Int) {
        if (index in 0..3) emitByte(75 + index)
        else { emitByte(58); emitByte(index) }
    }

    /** Store int to array. */
    fun iastore() {
        emitByte(79)
    }

    /** Store long to array. */
    fun lastore() {
        emitByte(80)
    }

    /** Store float to array. */
    fun fastore() {
        emitByte(81)
    }

    /** Store double to array. */
    fun dastore() {
        emitByte(82)
    }

    /** Store reference to array. */
    fun aastore() {
        emitByte(83)
    }

    /** Store byte/boolean to array. */
    fun bastore() {
        emitByte(84)
    }

    /** Store char to array. */
    fun castore() {
        emitByte(85)
    }

    /** Store short to array. */
    fun sastore() {
        emitByte(86)
    }

    /** Pop top value from stack. */
    fun pop() {
        emitByte(87)
    }

    /** Pop top one or two values from stack. */
    fun pop2() {
        emitByte(88)
    }

    /** Duplicate top stack value. */
    fun dup() {
        emitByte(89)
    }

    /** Duplicate top value and insert two deep. */
    fun dupX1() {
        emitByte(90)
    }

    /** Duplicate top value and insert three deep. */
    fun dupX2() {
        emitByte(91)
    }

    /** Duplicate top one or two stack values. */
    fun dup2() {
        emitByte(92)
    }

    /** Duplicate top one or two values and insert two or three deep. */
    fun dup2X1() {
        emitByte(93)
    }

    /** Duplicate top one or two values and insert three or four deep. */
    fun dup2X2() {
        emitByte(94)
    }

    /** Swap top two stack values. */
    fun swap() {
        emitByte(95)
    }

    /** Add two ints. */
    fun iadd() {
        emitByte(96)
    }

    /** Add two longs. */
    fun ladd() {
        emitByte(97)
    }

    /** Add two floats. */
    fun fadd() {
        emitByte(98)
    }

    /** Add two doubles. */
    fun dadd() {
        emitByte(99)
    }

    /** Subtract two ints. */
    fun isub() {
        emitByte(100)
    }

    /** Subtract two longs. */
    fun lsub() {
        emitByte(101)
    }

    /** Subtract two floats. */
    fun fsub() {
        emitByte(102)
    }

    /** Subtract two doubles. */
    fun dsub() {
        emitByte(103)
    }

    /** Multiply two ints. */
    fun imul() {
        emitByte(104)
    }

    /** Multiply two longs. */
    fun lmul() {
        emitByte(105)
    }

    /** Multiply two floats. */
    fun fmul() {
        emitByte(106)
    }

    /** Multiply two doubles. */
    fun dmul() {
        emitByte(107)
    }

    /** Divide two ints. */
    fun idiv() {
        emitByte(108)
    }

    /** Divide two longs. */
    fun ldiv() {
        emitByte(109)
    }

    /** Divide two floats. */
    fun fdiv() {
        emitByte(110)
    }

    /** Divide two doubles. */
    fun ddiv() {
        emitByte(111)
    }

    /** Remainder of two ints. */
    fun irem() {
        emitByte(112)
    }

    /** Remainder of two longs. */
    fun lrem() {
        emitByte(113)
    }

    /** Remainder of two floats. */
    fun frem() {
        emitByte(114)
    }

    /** Remainder of two doubles. */
    fun drem() {
        emitByte(115)
    }

    /** Negate int. */
    fun ineg() {
        emitByte(116)
    }

    /** Negate long. */
    fun lneg() {
        emitByte(117)
    }

    /** Negate float. */
    fun fneg() {
        emitByte(118)
    }

    /** Negate double. */
    fun dneg() {
        emitByte(119)
    }

    /** Shift int left. */
    fun ishl() {
        emitByte(120)
    }

    /** Shift long left. */
    fun lshl() {
        emitByte(121)
    }

    /** Arithmetic shift int right. */
    fun ishr() {
        emitByte(122)
    }

    /** Arithmetic shift long right. */
    fun lshr() {
        emitByte(123)
    }

    /** Logical shift int right. */
    fun iushr() {
        emitByte(124)
    }

    /** Logical shift long right. */
    fun lushr() {
        emitByte(125)
    }

    /** Bitwise AND two ints. */
    fun iand() {
        emitByte(126)
    }

    /** Bitwise AND two longs. */
    fun land() {
        emitByte(127)
    }

    /** Bitwise OR two ints. */
    fun ior() {
        emitByte(128)
    }

    /** Bitwise OR two longs. */
    fun lor() {
        emitByte(129)
    }

    /** Bitwise XOR two ints. */
    fun ixor() {
        emitByte(130)
    }

    /** Bitwise XOR two longs. */
    fun lxor() {
        emitByte(131)
    }

    /** Increment local int variable by constant. */
    open fun iinc(index: Int, increment: Int) {
        emitByte(132); emitByte(index); emitByte(increment and 0xFF)
    }

    /** Convert int to long. */
    fun i2l() {
        emitByte(133)
    }

    /** Convert int to float. */
    fun i2f() {
        emitByte(134)
    }

    /** Convert int to double. */
    fun i2d() {
        emitByte(135)
    }

    /** Convert long to int. */
    fun l2i() {
        emitByte(136)
    }

    /** Convert long to float. */
    fun l2f() {
        emitByte(137)
    }

    /** Convert long to double. */
    fun l2d() {
        emitByte(138)
    }

    /** Convert float to int. */
    fun f2i() {
        emitByte(139)
    }

    /** Convert float to long. */
    fun f2l() {
        emitByte(140)
    }

    /** Convert float to double. */
    fun f2d() {
        emitByte(141)
    }

    /** Convert double to int. */
    fun d2i() {
        emitByte(142)
    }

    /** Convert double to long. */
    fun d2l() {
        emitByte(143)
    }

    /** Convert double to float. */
    fun d2f() {
        emitByte(144)
    }

    /** Convert int to byte. */
    fun i2b() {
        emitByte(145)
    }

    /** Convert int to char. */
    fun i2c() {
        emitByte(146)
    }

    /** Convert int to short. */
    fun i2s() {
        emitByte(147)
    }

    /** Compare two longs. */
    fun lcmp() {
        emitByte(148)
    }

    /** Compare two floats (NaN returns -1). */
    fun fcmpl() {
        emitByte(149)
    }

    /** Compare two floats (NaN returns 1). */
    fun fcmpg() {
        emitByte(150)
    }

    /** Compare two doubles (NaN returns -1). */
    fun dcmpl() {
        emitByte(151)
    }

    /** Compare two doubles (NaN returns 1). */
    fun dcmpg() {
        emitByte(152)
    }

    /** Branch if int equals zero. */
    open fun ifeq(label: String) {
        emitBranch(153, label)
    }

    /** Branch if int not equal to zero. */
    open fun ifne(label: String) {
        emitBranch(154, label)
    }

    /** Branch if int less than zero. */
    open fun iflt(label: String) {
        emitBranch(155, label)
    }

    /** Branch if int greater than or equal to zero. */
    open fun ifge(label: String) {
        emitBranch(156, label)
    }

    /** Branch if int greater than zero. */
    open fun ifgt(label: String) {
        emitBranch(157, label)
    }

    /** Branch if int less than or equal to zero. */
    open fun ifle(label: String) {
        emitBranch(158, label)
    }

    /** Branch if two ints are equal. */
    open fun ifIcmpeq(label: String) {
        emitBranch(159, label)
    }

    /** Branch if two ints are not equal. */
    open fun ifIcmpne(label: String) {
        emitBranch(160, label)
    }

    /** Branch if first int less than second. */
    open fun ifIcmplt(label: String) {
        emitBranch(161, label)
    }

    /** Branch if first int greater than or equal to second. */
    open fun ifIcmpge(label: String) {
        emitBranch(162, label)
    }

    /** Branch if first int greater than second. */
    open fun ifIcmpgt(label: String) {
        emitBranch(163, label)
    }

    /** Branch if first int less than or equal to second. */
    open fun ifIcmple(label: String) {
        emitBranch(164, label)
    }

    /** Branch if two references are equal. */
    open fun ifAcmpeq(label: String) {
        emitBranch(165, label)
    }

    /** Branch if two references are not equal. */
    open fun ifAcmpne(label: String) {
        emitBranch(166, label)
    }

    /** Unconditional branch. */
    open fun goto(label: String) {
        emitBranch(167, label)
    }

    /** Return int from method. */
    fun ireturn() {
        emitByte(172)
    }

    /** Return long from method. */
    fun lreturn() {
        emitByte(173)
    }

    /** Return float from method. */
    fun freturn() {
        emitByte(174)
    }

    /** Return double from method. */
    fun dreturn() {
        emitByte(175)
    }

    /** Return reference from method. */
    fun areturn() {
        emitByte(176)
    }

    /** Return void from method. */
    fun return_() {
        emitByte(177)
    }

    /** Get static field value. */
    fun getstatic(index: Int) {
        emitByte(178); emitShort(index)
    }

    /** Set static field value. */
    fun putstatic(index: Int) {
        emitByte(179); emitShort(index)
    }

    /** Get instance field value. */
    fun getfield(index: Int) {
        emitByte(180); emitShort(index)
    }

    /** Set instance field value. */
    fun putfield(index: Int) {
        emitByte(181); emitShort(index)
    }

    /** Invoke virtual method on object. */
    fun invokevirtual(index: Int) {
        emitByte(182); emitShort(index)
    }

    /** Invoke constructor, superclass, or private method. */
    fun invokespecial(index: Int) {
        emitByte(183); emitShort(index)
    }

    /** Invoke static method. */
    fun invokestatic(index: Int) {
        emitByte(184); emitShort(index)
    }

    /** Invoke interface method. */
    fun invokeinterface(index: Int, count: Int) {
        emitByte(185); emitShort(index); emitByte(count); emitByte(0)
    }

    /** Create new object. */
    fun new_(index: Int) {
        emitByte(187); emitShort(index)
    }

    /** Create new primitive array. */
    fun newarray(type: Int) {
        emitByte(188); emitByte(type)
    }

    /** Create new reference array. */
    fun anewarray(index: Int) {
        emitByte(189); emitShort(index)
    }

    /** Get array length. */
    fun arraylength() {
        emitByte(190)
    }

    /** Throw exception. */
    fun athrow() {
        emitByte(191)
    }

    /** Check if object is of given type. */
    fun checkcast(index: Int) {
        emitByte(192); emitShort(index)
    }

    /** Test if object is of given type. */
    fun instanceof_(index: Int) {
        emitByte(193); emitShort(index)
    }

    /** Enter monitor for object synchronization. */
    fun monitorenter() {
        emitByte(194)
    }

    /** Exit monitor for object synchronization. */
    fun monitorexit() {
        emitByte(195)
    }

    /** Branch if reference is null. */
    open fun ifnull(label: String) {
        emitBranch(198, label)
    }

    /** Branch if reference is not null. */
    open fun ifnonnull(label: String) {
        emitBranch(199, label)
    }

    /** Emit a branch instruction with label-based offset resolution. */
    protected abstract fun emitBranch(opcode: Int, label: String)
}
