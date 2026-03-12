package org.kgen.target.jvm

/**
 * JVM bytecode opcodes (Java Virtual Machine Specification, Chapter 7).
 *
 * Each opcode has a mnemonic and byte value. The [operandSize] property returns
 * the number of operand bytes following the opcode, or -1 for variable-length
 * instructions.
 *
 * ```java
 * JvmOpCode op = JvmOpCode.fromCode(0x60); // IADD
 * System.out.println(op.name());            // "IADD"
 * System.out.println(op.getCode());         // 96
 * System.out.println(op.getOperandSize());  // 0
 * ```
 */
enum class JvmOpCode(val code: Int) {
    // Constants
    /** Do nothing. */
    NOP(0x00),
    /** Push null reference. */
    ACONST_NULL(0x01),
    /** Push int constant -1. */
    ICONST_M1(0x02),
    /** Push int constant 0. */
    ICONST_0(0x03),
    /** Push int constant 1. */
    ICONST_1(0x04),
    /** Push int constant 2. */
    ICONST_2(0x05),
    /** Push int constant 3. */
    ICONST_3(0x06),
    /** Push int constant 4. */
    ICONST_4(0x07),
    /** Push int constant 5. */
    ICONST_5(0x08),
    /** Push long constant 0. */
    LCONST_0(0x09),
    /** Push long constant 1. */
    LCONST_1(0x0A),
    /** Push float constant 0.0. */
    FCONST_0(0x0B),
    /** Push float constant 1.0. */
    FCONST_1(0x0C),
    /** Push float constant 2.0. */
    FCONST_2(0x0D),
    /** Push double constant 0.0. */
    DCONST_0(0x0E),
    /** Push double constant 1.0. */
    DCONST_1(0x0F),
    /** Push byte as int (-128 to 127). */
    BIPUSH(0x10),
    /** Push short as int (-32768 to 32767). */
    SIPUSH(0x11),
    /** Push constant pool entry (1-byte index). */
    LDC(0x12),
    /** Push constant pool entry (2-byte index). */
    LDC_W(0x13),
    /** Push long or double from constant pool. */
    LDC2_W(0x14),

    // Loads
    /** Load int from local variable. */
    ILOAD(0x15),
    /** Load long from local variable. */
    LLOAD(0x16),
    /** Load float from local variable. */
    FLOAD(0x17),
    /** Load double from local variable. */
    DLOAD(0x18),
    /** Load reference from local variable. */
    ALOAD(0x19),
    /** Load int from local variable 0. */
    ILOAD_0(0x1A),
    /** Load int from local variable 1. */
    ILOAD_1(0x1B),
    /** Load int from local variable 2. */
    ILOAD_2(0x1C),
    /** Load int from local variable 3. */
    ILOAD_3(0x1D),
    /** Load long from local variable 0. */
    LLOAD_0(0x1E),
    /** Load long from local variable 1. */
    LLOAD_1(0x1F),
    /** Load long from local variable 2. */
    LLOAD_2(0x20),
    /** Load long from local variable 3. */
    LLOAD_3(0x21),
    /** Load float from local variable 0. */
    FLOAD_0(0x22),
    /** Load float from local variable 1. */
    FLOAD_1(0x23),
    /** Load float from local variable 2. */
    FLOAD_2(0x24),
    /** Load float from local variable 3. */
    FLOAD_3(0x25),
    /** Load double from local variable 0. */
    DLOAD_0(0x26),
    /** Load double from local variable 1. */
    DLOAD_1(0x27),
    /** Load double from local variable 2. */
    DLOAD_2(0x28),
    /** Load double from local variable 3. */
    DLOAD_3(0x29),
    /** Load reference from local variable 0. */
    ALOAD_0(0x2A),
    /** Load reference from local variable 1. */
    ALOAD_1(0x2B),
    /** Load reference from local variable 2. */
    ALOAD_2(0x2C),
    /** Load reference from local variable 3. */
    ALOAD_3(0x2D),
    /** Load int from array. */
    IALOAD(0x2E),
    /** Load long from array. */
    LALOAD(0x2F),
    /** Load float from array. */
    FALOAD(0x30),
    /** Load double from array. */
    DALOAD(0x31),
    /** Load reference from array. */
    AALOAD(0x32),
    /** Load byte/boolean from array. */
    BALOAD(0x33),
    /** Load char from array. */
    CALOAD(0x34),
    /** Load short from array. */
    SALOAD(0x35),

    // Stores
    /** Store int to local variable. */
    ISTORE(0x36),
    /** Store long to local variable. */
    LSTORE(0x37),
    /** Store float to local variable. */
    FSTORE(0x38),
    /** Store double to local variable. */
    DSTORE(0x39),
    /** Store reference to local variable. */
    ASTORE(0x3A),
    /** Store int to local variable 0. */
    ISTORE_0(0x3B),
    /** Store int to local variable 1. */
    ISTORE_1(0x3C),
    /** Store int to local variable 2. */
    ISTORE_2(0x3D),
    /** Store int to local variable 3. */
    ISTORE_3(0x3E),
    /** Store long to local variable 0. */
    LSTORE_0(0x3F),
    /** Store long to local variable 1. */
    LSTORE_1(0x40),
    /** Store long to local variable 2. */
    LSTORE_2(0x41),
    /** Store long to local variable 3. */
    LSTORE_3(0x42),
    /** Store float to local variable 0. */
    FSTORE_0(0x43),
    /** Store float to local variable 1. */
    FSTORE_1(0x44),
    /** Store float to local variable 2. */
    FSTORE_2(0x45),
    /** Store float to local variable 3. */
    FSTORE_3(0x46),
    /** Store double to local variable 0. */
    DSTORE_0(0x47),
    /** Store double to local variable 1. */
    DSTORE_1(0x48),
    /** Store double to local variable 2. */
    DSTORE_2(0x49),
    /** Store double to local variable 3. */
    DSTORE_3(0x4A),
    /** Store reference to local variable 0. */
    ASTORE_0(0x4B),
    /** Store reference to local variable 1. */
    ASTORE_1(0x4C),
    /** Store reference to local variable 2. */
    ASTORE_2(0x4D),
    /** Store reference to local variable 3. */
    ASTORE_3(0x4E),
    /** Store int to array. */
    IASTORE(0x4F),
    /** Store long to array. */
    LASTORE(0x50),
    /** Store float to array. */
    FASTORE(0x51),
    /** Store double to array. */
    DASTORE(0x52),
    /** Store reference to array. */
    AASTORE(0x53),
    /** Store byte/boolean to array. */
    BASTORE(0x54),
    /** Store char to array. */
    CASTORE(0x55),
    /** Store short to array. */
    SASTORE(0x56),

    // Stack
    /** Pop top value from stack. */
    POP(0x57),
    /** Pop top one or two values from stack. */
    POP2(0x58),
    /** Duplicate top stack value. */
    DUP(0x59),
    /** Duplicate top value and insert two deep. */
    DUP_X1(0x5A),
    /** Duplicate top value and insert three deep. */
    DUP_X2(0x5B),
    /** Duplicate top one or two stack values. */
    DUP2(0x5C),
    /** Duplicate top one or two values and insert two or three deep. */
    DUP2_X1(0x5D),
    /** Duplicate top one or two values and insert three or four deep. */
    DUP2_X2(0x5E),
    /** Swap top two stack values. */
    SWAP(0x5F),

    // Arithmetic
    /** Add two ints. */
    IADD(0x60),
    /** Add two longs. */
    LADD(0x61),
    /** Add two floats. */
    FADD(0x62),
    /** Add two doubles. */
    DADD(0x63),
    /** Subtract two ints. */
    ISUB(0x64),
    /** Subtract two longs. */
    LSUB(0x65),
    /** Subtract two floats. */
    FSUB(0x66),
    /** Subtract two doubles. */
    DSUB(0x67),
    /** Multiply two ints. */
    IMUL(0x68),
    /** Multiply two longs. */
    LMUL(0x69),
    /** Multiply two floats. */
    FMUL(0x6A),
    /** Multiply two doubles. */
    DMUL(0x6B),
    /** Divide two ints. */
    IDIV(0x6C),
    /** Divide two longs. */
    LDIV(0x6D),
    /** Divide two floats. */
    FDIV(0x6E),
    /** Divide two doubles. */
    DDIV(0x6F),
    /** Remainder of two ints. */
    IREM(0x70),
    /** Remainder of two longs. */
    LREM(0x71),
    /** Remainder of two floats. */
    FREM(0x72),
    /** Remainder of two doubles. */
    DREM(0x73),
    /** Negate int. */
    INEG(0x74),
    /** Negate long. */
    LNEG(0x75),
    /** Negate float. */
    FNEG(0x76),
    /** Negate double. */
    DNEG(0x77),
    /** Shift int left. */
    ISHL(0x78),
    /** Shift long left. */
    LSHL(0x79),
    /** Arithmetic shift int right. */
    ISHR(0x7A),
    /** Arithmetic shift long right. */
    LSHR(0x7B),
    /** Logical shift int right. */
    IUSHR(0x7C),
    /** Logical shift long right. */
    LUSHR(0x7D),
    /** Bitwise AND two ints. */
    IAND(0x7E),
    /** Bitwise AND two longs. */
    LAND(0x7F),
    /** Bitwise OR two ints. */
    IOR(0x80),
    /** Bitwise OR two longs. */
    LOR(0x81),
    /** Bitwise XOR two ints. */
    IXOR(0x82),
    /** Bitwise XOR two longs. */
    LXOR(0x83),
    /** Increment local int variable by constant. */
    IINC(0x84),

    // Conversions
    /** Convert int to long. */
    I2L(0x85),
    /** Convert int to float. */
    I2F(0x86),
    /** Convert int to double. */
    I2D(0x87),
    /** Convert long to int. */
    L2I(0x88),
    /** Convert long to float. */
    L2F(0x89),
    /** Convert long to double. */
    L2D(0x8A),
    /** Convert float to int. */
    F2I(0x8B),
    /** Convert float to long. */
    F2L(0x8C),
    /** Convert float to double. */
    F2D(0x8D),
    /** Convert double to int. */
    D2I(0x8E),
    /** Convert double to long. */
    D2L(0x8F),
    /** Convert double to float. */
    D2F(0x90),
    /** Convert int to byte. */
    I2B(0x91),
    /** Convert int to char. */
    I2C(0x92),
    /** Convert int to short. */
    I2S(0x93),

    // Comparisons
    /** Compare two longs. */
    LCMP(0x94),
    /** Compare two floats (NaN returns -1). */
    FCMPL(0x95),
    /** Compare two floats (NaN returns 1). */
    FCMPG(0x96),
    /** Compare two doubles (NaN returns -1). */
    DCMPL(0x97),
    /** Compare two doubles (NaN returns 1). */
    DCMPG(0x98),
    /** Branch if int equals zero. */
    IFEQ(0x99),
    /** Branch if int not equal to zero. */
    IFNE(0x9A),
    /** Branch if int less than zero. */
    IFLT(0x9B),
    /** Branch if int greater than or equal to zero. */
    IFGE(0x9C),
    /** Branch if int greater than zero. */
    IFGT(0x9D),
    /** Branch if int less than or equal to zero. */
    IFLE(0x9E),
    /** Branch if two ints are equal. */
    IF_ICMPEQ(0x9F),
    /** Branch if two ints are not equal. */
    IF_ICMPNE(0xA0),
    /** Branch if first int less than second. */
    IF_ICMPLT(0xA1),
    /** Branch if first int greater than or equal to second. */
    IF_ICMPGE(0xA2),
    /** Branch if first int greater than second. */
    IF_ICMPGT(0xA3),
    /** Branch if first int less than or equal to second. */
    IF_ICMPLE(0xA4),
    /** Branch if two references are equal. */
    IF_ACMPEQ(0xA5),
    /** Branch if two references are not equal. */
    IF_ACMPNE(0xA6),

    // Control
    /** Unconditional branch. */
    GOTO(0xA7),
    /** Jump subroutine (deprecated). */
    JSR(0xA8),
    /** Return from subroutine (deprecated). */
    RET(0xA9),
    /** Table-based branch (variable-length). */
    TABLESWITCH(0xAA),
    /** Key-based branch (variable-length). */
    LOOKUPSWITCH(0xAB),
    /** Return int from method. */
    IRETURN(0xAC),
    /** Return long from method. */
    LRETURN(0xAD),
    /** Return float from method. */
    FRETURN(0xAE),
    /** Return double from method. */
    DRETURN(0xAF),
    /** Return reference from method. */
    ARETURN(0xB0),
    /** Return void from method. */
    RETURN(0xB1),

    // References
    /** Get static field value. */
    GETSTATIC(0xB2),
    /** Set static field value. */
    PUTSTATIC(0xB3),
    /** Get instance field value. */
    GETFIELD(0xB4),
    /** Set instance field value. */
    PUTFIELD(0xB5),
    /** Invoke virtual method on object. */
    INVOKEVIRTUAL(0xB6),
    /** Invoke constructor, superclass, or private method. */
    INVOKESPECIAL(0xB7),
    /** Invoke static method. */
    INVOKESTATIC(0xB8),
    /** Invoke interface method. */
    INVOKEINTERFACE(0xB9),
    /** Invoke dynamic method (bootstrap). */
    INVOKEDYNAMIC(0xBA),
    /** Create new object. */
    NEW(0xBB),
    /** Create new primitive array. */
    NEWARRAY(0xBC),
    /** Create new reference array. */
    ANEWARRAY(0xBD),
    /** Get array length. */
    ARRAYLENGTH(0xBE),
    /** Throw exception. */
    ATHROW(0xBF),
    /** Check if object is of given type. */
    CHECKCAST(0xC0),
    /** Test if object is of given type. */
    INSTANCEOF(0xC1),
    /** Enter monitor for object synchronization. */
    MONITORENTER(0xC2),
    /** Exit monitor for object synchronization. */
    MONITOREXIT(0xC3),

    // Extended
    /** Extend local variable index to 2 bytes. */
    WIDE(0xC4),
    /** Create new multi-dimensional array. */
    MULTIANEWARRAY(0xC5),
    /** Branch if reference is null. */
    IFNULL(0xC6),
    /** Branch if reference is not null. */
    IFNONNULL(0xC7),
    /** Unconditional branch (4-byte offset). */
    GOTO_W(0xC8),
    /** Jump subroutine wide (deprecated). */
    JSR_W(0xC9),
    ;

    /**
     * Number of operand bytes following this opcode.
     * Returns -1 for variable-length instructions (TABLESWITCH, LOOKUPSWITCH, WIDE).
     */
    val operandSize: Int get() = when (this) {
        BIPUSH, LDC, NEWARRAY -> 1
        ILOAD, LLOAD, FLOAD, DLOAD, ALOAD,
        ISTORE, LSTORE, FSTORE, DSTORE, ASTORE -> 1
        SIPUSH, LDC_W, LDC2_W, IINC -> 2
        IFEQ, IFNE, IFLT, IFGE, IFGT, IFLE,
        IF_ICMPEQ, IF_ICMPNE, IF_ICMPLT, IF_ICMPGE, IF_ICMPGT, IF_ICMPLE,
        IF_ACMPEQ, IF_ACMPNE,
        GOTO, JSR, IFNULL, IFNONNULL -> 2
        GETSTATIC, PUTSTATIC, GETFIELD, PUTFIELD,
        INVOKEVIRTUAL, INVOKESPECIAL, INVOKESTATIC,
        NEW, ANEWARRAY, CHECKCAST, INSTANCEOF -> 2
        MULTIANEWARRAY -> 3
        INVOKEINTERFACE, INVOKEDYNAMIC, GOTO_W, JSR_W -> 4
        TABLESWITCH, LOOKUPSWITCH, WIDE -> -1
        else -> 0
    }

    companion object {
        private val byCode = entries.associateBy { it.code }

        /** Look up opcode by byte value. */
        @JvmStatic
        fun fromCode(code: Int): JvmOpCode? = byCode[code]
    }
}
