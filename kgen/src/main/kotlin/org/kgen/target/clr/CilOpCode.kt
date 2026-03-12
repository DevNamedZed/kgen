package org.kgen.target.clr

/**
 * CIL (Common Intermediate Language) opcodes from ECMA-335 Partition III.
 *
 * Single-byte opcodes (0x00-0xFE) and two-byte opcodes (0xFE prefix + second byte).
 * Two-byte opcodes are encoded as `0xFE00 | secondByte` in the [code] field.
 *
 * ```java
 * CilOpCode op = CilOpCode.ADD;
 * System.out.println(op.mnemonic());  // "add"
 * System.out.println(op.code());      // 0x58
 *
 * CilOpCode op2 = CilOpCode.fromCode(0x58);  // ADD
 * CilOpCode op3 = CilOpCode.fromCode(0xFE01); // CEQ (two-byte)
 * ```
 */
enum class CilOpCode(
    val code: Int,
    private val _mnemonic: String,
    val operandType: CilOperandType,
    val stackPush: Int,
    val stackPop: Int,
) {
    // Single-byte opcodes (Partition III, Table III.1)
    NOP(0x00, "nop", CilOperandType.NONE, 0, 0),
    BREAK(0x01, "break", CilOperandType.NONE, 0, 0),
    LDARG_0(0x02, "ldarg.0", CilOperandType.NONE, 1, 0),
    LDARG_1(0x03, "ldarg.1", CilOperandType.NONE, 1, 0),
    LDARG_2(0x04, "ldarg.2", CilOperandType.NONE, 1, 0),
    LDARG_3(0x05, "ldarg.3", CilOperandType.NONE, 1, 0),
    LDLOC_0(0x06, "ldloc.0", CilOperandType.NONE, 1, 0),
    LDLOC_1(0x07, "ldloc.1", CilOperandType.NONE, 1, 0),
    LDLOC_2(0x08, "ldloc.2", CilOperandType.NONE, 1, 0),
    LDLOC_3(0x09, "ldloc.3", CilOperandType.NONE, 1, 0),
    STLOC_0(0x0A, "stloc.0", CilOperandType.NONE, 0, 1),
    STLOC_1(0x0B, "stloc.1", CilOperandType.NONE, 0, 1),
    STLOC_2(0x0C, "stloc.2", CilOperandType.NONE, 0, 1),
    STLOC_3(0x0D, "stloc.3", CilOperandType.NONE, 0, 1),
    LDARG_S(0x0E, "ldarg.s", CilOperandType.U8, 1, 0),
    LDARGA_S(0x0F, "ldarga.s", CilOperandType.U8, 1, 0),
    STARG_S(0x10, "starg.s", CilOperandType.U8, 0, 1),
    LDLOC_S(0x11, "ldloc.s", CilOperandType.U8, 1, 0),
    LDLOCA_S(0x12, "ldloca.s", CilOperandType.U8, 1, 0),
    STLOC_S(0x13, "stloc.s", CilOperandType.U8, 0, 1),
    LDNULL(0x14, "ldnull", CilOperandType.NONE, 1, 0),
    LDC_I4_M1(0x15, "ldc.i4.m1", CilOperandType.NONE, 1, 0),
    LDC_I4_0(0x16, "ldc.i4.0", CilOperandType.NONE, 1, 0),
    LDC_I4_1(0x17, "ldc.i4.1", CilOperandType.NONE, 1, 0),
    LDC_I4_2(0x18, "ldc.i4.2", CilOperandType.NONE, 1, 0),
    LDC_I4_3(0x19, "ldc.i4.3", CilOperandType.NONE, 1, 0),
    LDC_I4_4(0x1A, "ldc.i4.4", CilOperandType.NONE, 1, 0),
    LDC_I4_5(0x1B, "ldc.i4.5", CilOperandType.NONE, 1, 0),
    LDC_I4_6(0x1C, "ldc.i4.6", CilOperandType.NONE, 1, 0),
    LDC_I4_7(0x1D, "ldc.i4.7", CilOperandType.NONE, 1, 0),
    LDC_I4_8(0x1E, "ldc.i4.8", CilOperandType.NONE, 1, 0),
    LDC_I4_S(0x1F, "ldc.i4.s", CilOperandType.I8, 1, 0),
    LDC_I4(0x20, "ldc.i4", CilOperandType.I32, 1, 0),
    LDC_I8(0x21, "ldc.i8", CilOperandType.I64, 1, 0),
    LDC_R4(0x22, "ldc.r4", CilOperandType.F32, 1, 0),
    LDC_R8(0x23, "ldc.r8", CilOperandType.F64, 1, 0),
    DUP(0x25, "dup", CilOperandType.NONE, 2, 1),
    POP(0x26, "pop", CilOperandType.NONE, 0, 1),
    JMP(0x27, "jmp", CilOperandType.TOKEN, 0, 0),
    CALL(0x28, "call", CilOperandType.TOKEN, -1, -1), // variable
    CALLI(0x29, "calli", CilOperandType.TOKEN, -1, -1),
    RET(0x2A, "ret", CilOperandType.NONE, 0, -1),

    // Branches
    BR_S(0x2B, "br.s", CilOperandType.I8, 0, 0),
    BRFALSE_S(0x2C, "brfalse.s", CilOperandType.I8, 0, 1),
    BRTRUE_S(0x2D, "brtrue.s", CilOperandType.I8, 0, 1),
    BEQ_S(0x2E, "beq.s", CilOperandType.I8, 0, 2),
    BGE_S(0x2F, "bge.s", CilOperandType.I8, 0, 2),
    BGT_S(0x30, "bgt.s", CilOperandType.I8, 0, 2),
    BLE_S(0x31, "ble.s", CilOperandType.I8, 0, 2),
    BLT_S(0x32, "blt.s", CilOperandType.I8, 0, 2),
    BNE_UN_S(0x33, "bne.un.s", CilOperandType.I8, 0, 2),
    BGE_UN_S(0x34, "bge.un.s", CilOperandType.I8, 0, 2),
    BGT_UN_S(0x35, "bgt.un.s", CilOperandType.I8, 0, 2),
    BLE_UN_S(0x36, "ble.un.s", CilOperandType.I8, 0, 2),
    BLT_UN_S(0x37, "blt.un.s", CilOperandType.I8, 0, 2),
    BR(0x38, "br", CilOperandType.I32, 0, 0),
    BRFALSE(0x39, "brfalse", CilOperandType.I32, 0, 1),
    BRTRUE(0x3A, "brtrue", CilOperandType.I32, 0, 1),
    BEQ(0x3B, "beq", CilOperandType.I32, 0, 2),
    BGE(0x3C, "bge", CilOperandType.I32, 0, 2),
    BGT(0x3D, "bgt", CilOperandType.I32, 0, 2),
    BLE(0x3E, "ble", CilOperandType.I32, 0, 2),
    BLT(0x3F, "blt", CilOperandType.I32, 0, 2),
    BNE_UN(0x40, "bne.un", CilOperandType.I32, 0, 2),
    BGE_UN(0x41, "bge.un", CilOperandType.I32, 0, 2),
    BGT_UN(0x42, "bgt.un", CilOperandType.I32, 0, 2),
    BLE_UN(0x43, "ble.un", CilOperandType.I32, 0, 2),
    BLT_UN(0x44, "blt.un", CilOperandType.I32, 0, 2),

    SWITCH(0x45, "switch", CilOperandType.SWITCH, 0, 1),

    // Indirect loads
    LDIND_I1(0x46, "ldind.i1", CilOperandType.NONE, 1, 1),
    LDIND_U1(0x47, "ldind.u1", CilOperandType.NONE, 1, 1),
    LDIND_I2(0x48, "ldind.i2", CilOperandType.NONE, 1, 1),
    LDIND_U2(0x49, "ldind.u2", CilOperandType.NONE, 1, 1),
    LDIND_I4(0x4A, "ldind.i4", CilOperandType.NONE, 1, 1),
    LDIND_U4(0x4B, "ldind.u4", CilOperandType.NONE, 1, 1),
    LDIND_I8(0x4C, "ldind.i8", CilOperandType.NONE, 1, 1),
    LDIND_I(0x4D, "ldind.i", CilOperandType.NONE, 1, 1),
    LDIND_R4(0x4E, "ldind.r4", CilOperandType.NONE, 1, 1),
    LDIND_R8(0x4F, "ldind.r8", CilOperandType.NONE, 1, 1),
    LDIND_REF(0x50, "ldind.ref", CilOperandType.NONE, 1, 1),

    // Indirect stores
    STIND_REF(0x51, "stind.ref", CilOperandType.NONE, 0, 2),
    STIND_I1(0x52, "stind.i1", CilOperandType.NONE, 0, 2),
    STIND_I2(0x53, "stind.i2", CilOperandType.NONE, 0, 2),
    STIND_I4(0x54, "stind.i4", CilOperandType.NONE, 0, 2),
    STIND_I8(0x55, "stind.i8", CilOperandType.NONE, 0, 2),
    STIND_R4(0x56, "stind.r4", CilOperandType.NONE, 0, 2),
    STIND_R8(0x57, "stind.r8", CilOperandType.NONE, 0, 2),

    // Arithmetic
    ADD(0x58, "add", CilOperandType.NONE, 1, 2),
    SUB(0x59, "sub", CilOperandType.NONE, 1, 2),
    MUL(0x5A, "mul", CilOperandType.NONE, 1, 2),
    DIV(0x5B, "div", CilOperandType.NONE, 1, 2),
    DIV_UN(0x5C, "div.un", CilOperandType.NONE, 1, 2),
    REM(0x5D, "rem", CilOperandType.NONE, 1, 2),
    REM_UN(0x5E, "rem.un", CilOperandType.NONE, 1, 2),
    AND(0x5F, "and", CilOperandType.NONE, 1, 2),
    OR(0x60, "or", CilOperandType.NONE, 1, 2),
    XOR(0x61, "xor", CilOperandType.NONE, 1, 2),
    SHL(0x62, "shl", CilOperandType.NONE, 1, 2),
    SHR(0x63, "shr", CilOperandType.NONE, 1, 2),
    SHR_UN(0x64, "shr.un", CilOperandType.NONE, 1, 2),
    NEG(0x65, "neg", CilOperandType.NONE, 1, 1),
    NOT(0x66, "not", CilOperandType.NONE, 1, 1),

    // Conversions
    CONV_I1(0x67, "conv.i1", CilOperandType.NONE, 1, 1),
    CONV_I2(0x68, "conv.i2", CilOperandType.NONE, 1, 1),
    CONV_I4(0x69, "conv.i4", CilOperandType.NONE, 1, 1),
    CONV_I8(0x6A, "conv.i8", CilOperandType.NONE, 1, 1),
    CONV_R4(0x6B, "conv.r4", CilOperandType.NONE, 1, 1),
    CONV_R8(0x6C, "conv.r8", CilOperandType.NONE, 1, 1),
    CONV_U4(0x6D, "conv.u4", CilOperandType.NONE, 1, 1),
    CONV_U8(0x6E, "conv.u8", CilOperandType.NONE, 1, 1),

    // Object model
    CALLVIRT(0x6F, "callvirt", CilOperandType.TOKEN, -1, -1),
    CPOBJ(0x70, "cpobj", CilOperandType.TOKEN, 0, 2),
    LDOBJ(0x71, "ldobj", CilOperandType.TOKEN, 1, 1),
    LDSTR(0x72, "ldstr", CilOperandType.TOKEN, 1, 0),
    NEWOBJ(0x73, "newobj", CilOperandType.TOKEN, 1, -1),
    CASTCLASS(0x74, "castclass", CilOperandType.TOKEN, 1, 1),
    ISINST(0x75, "isinst", CilOperandType.TOKEN, 1, 1),
    CONV_R_UN(0x76, "conv.r.un", CilOperandType.NONE, 1, 1),
    UNBOX(0x79, "unbox", CilOperandType.TOKEN, 1, 1),
    THROW(0x7A, "throw", CilOperandType.NONE, 0, 1),
    LDFLD(0x7B, "ldfld", CilOperandType.TOKEN, 1, 1),
    LDFLDA(0x7C, "ldflda", CilOperandType.TOKEN, 1, 1),
    STFLD(0x7D, "stfld", CilOperandType.TOKEN, 0, 2),
    LDSFLD(0x7E, "ldsfld", CilOperandType.TOKEN, 1, 0),
    LDSFLDA(0x7F, "ldsflda", CilOperandType.TOKEN, 1, 0),
    STSFLD(0x80, "stsfld", CilOperandType.TOKEN, 0, 1),
    STOBJ(0x81, "stobj", CilOperandType.TOKEN, 0, 2),
    CONV_OVF_I1_UN(0x82, "conv.ovf.i1.un", CilOperandType.NONE, 1, 1),
    CONV_OVF_I2_UN(0x83, "conv.ovf.i2.un", CilOperandType.NONE, 1, 1),
    CONV_OVF_I4_UN(0x84, "conv.ovf.i4.un", CilOperandType.NONE, 1, 1),
    CONV_OVF_I8_UN(0x85, "conv.ovf.i8.un", CilOperandType.NONE, 1, 1),
    CONV_OVF_U1_UN(0x86, "conv.ovf.u1.un", CilOperandType.NONE, 1, 1),
    CONV_OVF_U2_UN(0x87, "conv.ovf.u2.un", CilOperandType.NONE, 1, 1),
    CONV_OVF_U4_UN(0x88, "conv.ovf.u4.un", CilOperandType.NONE, 1, 1),
    CONV_OVF_U8_UN(0x89, "conv.ovf.u8.un", CilOperandType.NONE, 1, 1),
    CONV_OVF_I_UN(0x8A, "conv.ovf.i.un", CilOperandType.NONE, 1, 1),
    CONV_OVF_U_UN(0x8B, "conv.ovf.u.un", CilOperandType.NONE, 1, 1),

    BOX(0x8C, "box", CilOperandType.TOKEN, 1, 1),
    NEWARR(0x8D, "newarr", CilOperandType.TOKEN, 1, 1),
    LDLEN(0x8E, "ldlen", CilOperandType.NONE, 1, 1),
    LDELEMA(0x8F, "ldelema", CilOperandType.TOKEN, 1, 2),
    LDELEM_I1(0x90, "ldelem.i1", CilOperandType.NONE, 1, 2),
    LDELEM_U1(0x91, "ldelem.u1", CilOperandType.NONE, 1, 2),
    LDELEM_I2(0x92, "ldelem.i2", CilOperandType.NONE, 1, 2),
    LDELEM_U2(0x93, "ldelem.u2", CilOperandType.NONE, 1, 2),
    LDELEM_I4(0x94, "ldelem.i4", CilOperandType.NONE, 1, 2),
    LDELEM_U4(0x95, "ldelem.u4", CilOperandType.NONE, 1, 2),
    LDELEM_I8(0x96, "ldelem.i8", CilOperandType.NONE, 1, 2),
    LDELEM_I(0x97, "ldelem.i", CilOperandType.NONE, 1, 2),
    LDELEM_R4(0x98, "ldelem.r4", CilOperandType.NONE, 1, 2),
    LDELEM_R8(0x99, "ldelem.r8", CilOperandType.NONE, 1, 2),
    LDELEM_REF(0x9A, "ldelem.ref", CilOperandType.NONE, 1, 2),
    STELEM_I(0x9B, "stelem.i", CilOperandType.NONE, 0, 3),
    STELEM_I1(0x9C, "stelem.i1", CilOperandType.NONE, 0, 3),
    STELEM_I2(0x9D, "stelem.i2", CilOperandType.NONE, 0, 3),
    STELEM_I4(0x9E, "stelem.i4", CilOperandType.NONE, 0, 3),
    STELEM_I8(0x9F, "stelem.i8", CilOperandType.NONE, 0, 3),
    STELEM_R4(0xA0, "stelem.r4", CilOperandType.NONE, 0, 3),
    STELEM_R8(0xA1, "stelem.r8", CilOperandType.NONE, 0, 3),
    STELEM_REF(0xA2, "stelem.ref", CilOperandType.NONE, 0, 3),
    LDELEM(0xA3, "ldelem", CilOperandType.TOKEN, 1, 2),
    STELEM(0xA4, "stelem", CilOperandType.TOKEN, 0, 3),
    UNBOX_ANY(0xA5, "unbox.any", CilOperandType.TOKEN, 1, 1),

    CONV_OVF_I1(0xB3, "conv.ovf.i1", CilOperandType.NONE, 1, 1),
    CONV_OVF_U1(0xB4, "conv.ovf.u1", CilOperandType.NONE, 1, 1),
    CONV_OVF_I2(0xB5, "conv.ovf.i2", CilOperandType.NONE, 1, 1),
    CONV_OVF_U2(0xB6, "conv.ovf.u2", CilOperandType.NONE, 1, 1),
    CONV_OVF_I4(0xB7, "conv.ovf.i4", CilOperandType.NONE, 1, 1),
    CONV_OVF_U4(0xB8, "conv.ovf.u4", CilOperandType.NONE, 1, 1),
    CONV_OVF_I8(0xB9, "conv.ovf.i8", CilOperandType.NONE, 1, 1),
    CONV_OVF_U8(0xBA, "conv.ovf.u8", CilOperandType.NONE, 1, 1),

    REFANYVAL(0xC2, "refanyval", CilOperandType.TOKEN, 1, 1),
    CKFINITE(0xC3, "ckfinite", CilOperandType.NONE, 1, 1),
    MKREFANY(0xC6, "mkrefany", CilOperandType.TOKEN, 1, 1),

    LDTOKEN(0xD0, "ldtoken", CilOperandType.TOKEN, 1, 0),
    CONV_U2(0xD1, "conv.u2", CilOperandType.NONE, 1, 1),
    CONV_U1(0xD2, "conv.u1", CilOperandType.NONE, 1, 1),
    CONV_I(0xD3, "conv.i", CilOperandType.NONE, 1, 1),
    CONV_OVF_I(0xD4, "conv.ovf.i", CilOperandType.NONE, 1, 1),
    CONV_OVF_U(0xD5, "conv.ovf.u", CilOperandType.NONE, 1, 1),
    ADD_OVF(0xD6, "add.ovf", CilOperandType.NONE, 1, 2),
    ADD_OVF_UN(0xD7, "add.ovf.un", CilOperandType.NONE, 1, 2),
    MUL_OVF(0xD8, "mul.ovf", CilOperandType.NONE, 1, 2),
    MUL_OVF_UN(0xD9, "mul.ovf.un", CilOperandType.NONE, 1, 2),
    SUB_OVF(0xDA, "sub.ovf", CilOperandType.NONE, 1, 2),
    SUB_OVF_UN(0xDB, "sub.ovf.un", CilOperandType.NONE, 1, 2),
    ENDFINALLY(0xDC, "endfinally", CilOperandType.NONE, 0, 0),
    LEAVE(0xDD, "leave", CilOperandType.I32, 0, 0),
    LEAVE_S(0xDE, "leave.s", CilOperandType.I8, 0, 0),
    STIND_I(0xDF, "stind.i", CilOperandType.NONE, 0, 2),
    CONV_U(0xE0, "conv.u", CilOperandType.NONE, 1, 1),

    // Two-byte opcodes (0xFE prefix)
    CEQ(0xFE01, "ceq", CilOperandType.NONE, 1, 2),
    CGT(0xFE02, "cgt", CilOperandType.NONE, 1, 2),
    CGT_UN(0xFE03, "cgt.un", CilOperandType.NONE, 1, 2),
    CLT(0xFE04, "clt", CilOperandType.NONE, 1, 2),
    CLT_UN(0xFE05, "clt.un", CilOperandType.NONE, 1, 2),
    LDFTN(0xFE06, "ldftn", CilOperandType.TOKEN, 1, 0),
    LDVIRTFTN(0xFE07, "ldvirtftn", CilOperandType.TOKEN, 1, 1),
    LDARG(0xFE09, "ldarg", CilOperandType.U16, 1, 0),
    LDARGA(0xFE0A, "ldarga", CilOperandType.U16, 1, 0),
    STARG(0xFE0B, "starg", CilOperandType.U16, 0, 1),
    LDLOC(0xFE0C, "ldloc", CilOperandType.U16, 1, 0),
    LDLOCA(0xFE0D, "ldloca", CilOperandType.U16, 1, 0),
    STLOC(0xFE0E, "stloc", CilOperandType.U16, 0, 1),
    LOCALLOC(0xFE0F, "localloc", CilOperandType.NONE, 1, 1),
    ENDFILTER(0xFE11, "endfilter", CilOperandType.NONE, 0, 1),
    UNALIGNED(0xFE12, "unaligned.", CilOperandType.U8, 0, 0),
    VOLATILE(0xFE13, "volatile.", CilOperandType.NONE, 0, 0),
    TAIL(0xFE14, "tail.", CilOperandType.NONE, 0, 0),
    INITOBJ(0xFE15, "initobj", CilOperandType.TOKEN, 0, 1),
    CONSTRAINED(0xFE16, "constrained.", CilOperandType.TOKEN, 0, 0),
    CPBLK(0xFE17, "cpblk", CilOperandType.NONE, 0, 3),
    INITBLK(0xFE18, "initblk", CilOperandType.NONE, 0, 3),
    RETHROW(0xFE1A, "rethrow", CilOperandType.NONE, 0, 0),
    SIZEOF(0xFE1C, "sizeof", CilOperandType.TOKEN, 1, 0),
    REFANYTYPE(0xFE1D, "refanytype", CilOperandType.NONE, 1, 1),
    READONLY(0xFE1E, "readonly.", CilOperandType.NONE, 0, 0),
    ;

    fun mnemonic(): String = _mnemonic

    /** True if this is a two-byte opcode (0xFE prefix). */
    fun isTwoByte(): Boolean = code > 0xFF

    /** Size of the operand in bytes. */
    fun operandSize(): Int = operandType.size

    companion object {
        private val singleByteMap = arrayOfNulls<CilOpCode>(256)
        private val twoByteMap = HashMap<Int, CilOpCode>()

        init {
            for (op in entries) {
                if (op.code <= 0xFF) {
                    singleByteMap[op.code] = op
                } else {
                    twoByteMap[op.code and 0xFF] = op
                }
            }
        }

        /** Look up a CIL opcode by its numeric code. */
        @JvmStatic
        fun fromCode(code: Int): CilOpCode? {
            return if (code <= 0xFF) singleByteMap[code]
            else twoByteMap[code and 0xFF]
        }

        /** Look up a single-byte opcode. */
        @JvmStatic
        fun fromByte(byte: Int): CilOpCode? = singleByteMap[byte and 0xFF]

        /** Look up a two-byte opcode (second byte after 0xFE prefix). */
        @JvmStatic
        fun fromTwoByte(secondByte: Int): CilOpCode? = twoByteMap[secondByte and 0xFF]
    }
}

/**
 * CIL instruction operand types.
 */
enum class CilOperandType(val size: Int) {
    NONE(0),
    I8(1),
    U8(1),
    I16(2),
    U16(2),
    I32(4),
    I64(8),
    F32(4),
    F64(8),
    TOKEN(4),
    SWITCH(-1),
}
