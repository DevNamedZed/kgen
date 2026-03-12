// Generated — do not edit
package org.kgen.target.wasm

/** Generated assembler methods for all WASM opcodes. */
abstract class WasmAssemblerOps {

    protected abstract fun emitByte(b: Int)
    protected abstract fun emitU32(value: Int)
    protected abstract fun emitS32(value: Int)
    protected abstract fun emitS64(value: Long)
    protected abstract fun emitF32(value: Float)
    protected abstract fun emitF64(value: Double)
    protected abstract fun emitBytes(bytes: ByteArray)

    /** Emit `unreachable`. */
    fun unreachable() {
        emitByte(0)
    }

    /** Emit `nop`. */
    fun nop() {
        emitByte(1)
    }

    /** Emit `block`. */
    fun block(blockType: Int) {
        emitByte(2)
        emitU32(blockType)
    }

    /** Emit `loop`. */
    fun loop(blockType: Int) {
        emitByte(3)
        emitU32(blockType)
    }

    /** Emit `if`. */
    fun if_(blockType: Int) {
        emitByte(4)
        emitU32(blockType)
    }

    /** Emit `else`. */
    fun else_() {
        emitByte(5)
    }

    /** Emit `end`. */
    fun end() {
        emitByte(11)
    }

    /** Emit `br`. */
    fun br(labelIdx: Int) {
        emitByte(12)
        emitU32(labelIdx)
    }

    /** Emit `br_if`. */
    fun brIf(labelIdx: Int) {
        emitByte(13)
        emitU32(labelIdx)
    }

    /** Emit `br_table`. */
    fun brTable(labels: IntArray) {
        emitByte(14)
        emitU32(labels.size - 1)
        for (label in labels) emitU32(label)
    }

    /** Emit `return`. */
    fun return_() {
        emitByte(15)
    }

    /** Emit `call`. */
    fun call(funcIdx: Int) {
        emitByte(16)
        emitU32(funcIdx)
    }

    /** Emit `call_indirect`. */
    fun callIndirect(typeIdx: Int, tableIdx: Int) {
        emitByte(17)
        emitU32(typeIdx)
        emitU32(tableIdx)
    }

    /** Emit `drop`. */
    fun drop() {
        emitByte(26)
    }

    /** Emit `select`. */
    fun select() {
        emitByte(27)
    }

    /** Emit `select t`. */
    fun selectTyped(typeCount: Int) {
        emitByte(28)
        emitU32(typeCount)
    }

    /** Emit `local.get`. */
    fun localGet(localIdx: Int) {
        emitByte(32)
        emitU32(localIdx)
    }

    /** Emit `local.set`. */
    fun localSet(localIdx: Int) {
        emitByte(33)
        emitU32(localIdx)
    }

    /** Emit `local.tee`. */
    fun localTee(localIdx: Int) {
        emitByte(34)
        emitU32(localIdx)
    }

    /** Emit `global.get`. */
    fun globalGet(globalIdx: Int) {
        emitByte(35)
        emitU32(globalIdx)
    }

    /** Emit `global.set`. */
    fun globalSet(globalIdx: Int) {
        emitByte(36)
        emitU32(globalIdx)
    }

    /** Emit `table.get`. */
    fun tableGet(tableIdx: Int) {
        emitByte(37)
        emitU32(tableIdx)
    }

    /** Emit `table.set`. */
    fun tableSet(tableIdx: Int) {
        emitByte(38)
        emitU32(tableIdx)
    }

    /** Emit `i32.load`. */
    fun i32Load(alignment: Int, offset: Int) {
        emitByte(40)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `i64.load`. */
    fun i64Load(alignment: Int, offset: Int) {
        emitByte(41)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `f32.load`. */
    fun f32Load(alignment: Int, offset: Int) {
        emitByte(42)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `f64.load`. */
    fun f64Load(alignment: Int, offset: Int) {
        emitByte(43)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `i32.load8_s`. */
    fun i32Load8S(alignment: Int, offset: Int) {
        emitByte(44)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `i32.load8_u`. */
    fun i32Load8U(alignment: Int, offset: Int) {
        emitByte(45)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `i32.load16_s`. */
    fun i32Load16S(alignment: Int, offset: Int) {
        emitByte(46)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `i32.load16_u`. */
    fun i32Load16U(alignment: Int, offset: Int) {
        emitByte(47)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `i64.load8_s`. */
    fun i64Load8S(alignment: Int, offset: Int) {
        emitByte(48)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `i64.load8_u`. */
    fun i64Load8U(alignment: Int, offset: Int) {
        emitByte(49)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `i64.load16_s`. */
    fun i64Load16S(alignment: Int, offset: Int) {
        emitByte(50)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `i64.load16_u`. */
    fun i64Load16U(alignment: Int, offset: Int) {
        emitByte(51)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `i64.load32_s`. */
    fun i64Load32S(alignment: Int, offset: Int) {
        emitByte(52)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `i64.load32_u`. */
    fun i64Load32U(alignment: Int, offset: Int) {
        emitByte(53)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `i32.store`. */
    fun i32Store(alignment: Int, offset: Int) {
        emitByte(54)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `i64.store`. */
    fun i64Store(alignment: Int, offset: Int) {
        emitByte(55)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `f32.store`. */
    fun f32Store(alignment: Int, offset: Int) {
        emitByte(56)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `f64.store`. */
    fun f64Store(alignment: Int, offset: Int) {
        emitByte(57)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `i32.store8`. */
    fun i32Store8(alignment: Int, offset: Int) {
        emitByte(58)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `i32.store16`. */
    fun i32Store16(alignment: Int, offset: Int) {
        emitByte(59)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `i64.store8`. */
    fun i64Store8(alignment: Int, offset: Int) {
        emitByte(60)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `i64.store16`. */
    fun i64Store16(alignment: Int, offset: Int) {
        emitByte(61)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `i64.store32`. */
    fun i64Store32(alignment: Int, offset: Int) {
        emitByte(62)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `memory.size`. */
    fun memorySize(memIdx: Int) {
        emitByte(63)
        emitU32(memIdx)
    }

    /** Emit `memory.grow`. */
    fun memoryGrow(memIdx: Int) {
        emitByte(64)
        emitU32(memIdx)
    }

    /** Emit `i32.const`. */
    fun i32Const(value: Int) {
        emitByte(65)
        emitS32(value)
    }

    /** Emit `i64.const`. */
    fun i64Const(value: Long) {
        emitByte(66)
        emitS64(value)
    }

    /** Emit `f32.const`. */
    fun f32Const(value: Float) {
        emitByte(67)
        emitF32(value)
    }

    /** Emit `f64.const`. */
    fun f64Const(value: Double) {
        emitByte(68)
        emitF64(value)
    }

    /** Emit `i32.eqz`. */
    fun i32Eqz() {
        emitByte(69)
    }

    /** Emit `i32.eq`. */
    fun i32Eq() {
        emitByte(70)
    }

    /** Emit `i32.ne`. */
    fun i32Ne() {
        emitByte(71)
    }

    /** Emit `i32.lt_s`. */
    fun i32LtS() {
        emitByte(72)
    }

    /** Emit `i32.lt_u`. */
    fun i32LtU() {
        emitByte(73)
    }

    /** Emit `i32.gt_s`. */
    fun i32GtS() {
        emitByte(74)
    }

    /** Emit `i32.gt_u`. */
    fun i32GtU() {
        emitByte(75)
    }

    /** Emit `i32.le_s`. */
    fun i32LeS() {
        emitByte(76)
    }

    /** Emit `i32.le_u`. */
    fun i32LeU() {
        emitByte(77)
    }

    /** Emit `i32.ge_s`. */
    fun i32GeS() {
        emitByte(78)
    }

    /** Emit `i32.ge_u`. */
    fun i32GeU() {
        emitByte(79)
    }

    /** Emit `i64.eqz`. */
    fun i64Eqz() {
        emitByte(80)
    }

    /** Emit `i64.eq`. */
    fun i64Eq() {
        emitByte(81)
    }

    /** Emit `i64.ne`. */
    fun i64Ne() {
        emitByte(82)
    }

    /** Emit `i64.lt_s`. */
    fun i64LtS() {
        emitByte(83)
    }

    /** Emit `i64.lt_u`. */
    fun i64LtU() {
        emitByte(84)
    }

    /** Emit `i64.gt_s`. */
    fun i64GtS() {
        emitByte(85)
    }

    /** Emit `i64.gt_u`. */
    fun i64GtU() {
        emitByte(86)
    }

    /** Emit `i64.le_s`. */
    fun i64LeS() {
        emitByte(87)
    }

    /** Emit `i64.le_u`. */
    fun i64LeU() {
        emitByte(88)
    }

    /** Emit `i64.ge_s`. */
    fun i64GeS() {
        emitByte(89)
    }

    /** Emit `i64.ge_u`. */
    fun i64GeU() {
        emitByte(90)
    }

    /** Emit `f32.eq`. */
    fun f32Eq() {
        emitByte(91)
    }

    /** Emit `f32.ne`. */
    fun f32Ne() {
        emitByte(92)
    }

    /** Emit `f32.lt`. */
    fun f32Lt() {
        emitByte(93)
    }

    /** Emit `f32.gt`. */
    fun f32Gt() {
        emitByte(94)
    }

    /** Emit `f32.le`. */
    fun f32Le() {
        emitByte(95)
    }

    /** Emit `f32.ge`. */
    fun f32Ge() {
        emitByte(96)
    }

    /** Emit `f64.eq`. */
    fun f64Eq() {
        emitByte(97)
    }

    /** Emit `f64.ne`. */
    fun f64Ne() {
        emitByte(98)
    }

    /** Emit `f64.lt`. */
    fun f64Lt() {
        emitByte(99)
    }

    /** Emit `f64.gt`. */
    fun f64Gt() {
        emitByte(100)
    }

    /** Emit `f64.le`. */
    fun f64Le() {
        emitByte(101)
    }

    /** Emit `f64.ge`. */
    fun f64Ge() {
        emitByte(102)
    }

    /** Emit `i32.clz`. */
    fun i32Clz() {
        emitByte(103)
    }

    /** Emit `i32.ctz`. */
    fun i32Ctz() {
        emitByte(104)
    }

    /** Emit `i32.popcnt`. */
    fun i32Popcnt() {
        emitByte(105)
    }

    /** Emit `i32.add`. */
    fun i32Add() {
        emitByte(106)
    }

    /** Emit `i32.sub`. */
    fun i32Sub() {
        emitByte(107)
    }

    /** Emit `i32.mul`. */
    fun i32Mul() {
        emitByte(108)
    }

    /** Emit `i32.div_s`. */
    fun i32DivS() {
        emitByte(109)
    }

    /** Emit `i32.div_u`. */
    fun i32DivU() {
        emitByte(110)
    }

    /** Emit `i32.rem_s`. */
    fun i32RemS() {
        emitByte(111)
    }

    /** Emit `i32.rem_u`. */
    fun i32RemU() {
        emitByte(112)
    }

    /** Emit `i32.and`. */
    fun i32And() {
        emitByte(113)
    }

    /** Emit `i32.or`. */
    fun i32Or() {
        emitByte(114)
    }

    /** Emit `i32.xor`. */
    fun i32Xor() {
        emitByte(115)
    }

    /** Emit `i32.shl`. */
    fun i32Shl() {
        emitByte(116)
    }

    /** Emit `i32.shr_s`. */
    fun i32ShrS() {
        emitByte(117)
    }

    /** Emit `i32.shr_u`. */
    fun i32ShrU() {
        emitByte(118)
    }

    /** Emit `i32.rotl`. */
    fun i32Rotl() {
        emitByte(119)
    }

    /** Emit `i32.rotr`. */
    fun i32Rotr() {
        emitByte(120)
    }

    /** Emit `i64.clz`. */
    fun i64Clz() {
        emitByte(121)
    }

    /** Emit `i64.ctz`. */
    fun i64Ctz() {
        emitByte(122)
    }

    /** Emit `i64.popcnt`. */
    fun i64Popcnt() {
        emitByte(123)
    }

    /** Emit `i64.add`. */
    fun i64Add() {
        emitByte(124)
    }

    /** Emit `i64.sub`. */
    fun i64Sub() {
        emitByte(125)
    }

    /** Emit `i64.mul`. */
    fun i64Mul() {
        emitByte(126)
    }

    /** Emit `i64.div_s`. */
    fun i64DivS() {
        emitByte(127)
    }

    /** Emit `i64.div_u`. */
    fun i64DivU() {
        emitByte(128)
    }

    /** Emit `i64.rem_s`. */
    fun i64RemS() {
        emitByte(129)
    }

    /** Emit `i64.rem_u`. */
    fun i64RemU() {
        emitByte(130)
    }

    /** Emit `i64.and`. */
    fun i64And() {
        emitByte(131)
    }

    /** Emit `i64.or`. */
    fun i64Or() {
        emitByte(132)
    }

    /** Emit `i64.xor`. */
    fun i64Xor() {
        emitByte(133)
    }

    /** Emit `i64.shl`. */
    fun i64Shl() {
        emitByte(134)
    }

    /** Emit `i64.shr_s`. */
    fun i64ShrS() {
        emitByte(135)
    }

    /** Emit `i64.shr_u`. */
    fun i64ShrU() {
        emitByte(136)
    }

    /** Emit `i64.rotl`. */
    fun i64Rotl() {
        emitByte(137)
    }

    /** Emit `i64.rotr`. */
    fun i64Rotr() {
        emitByte(138)
    }

    /** Emit `f32.abs`. */
    fun f32Abs() {
        emitByte(139)
    }

    /** Emit `f32.neg`. */
    fun f32Neg() {
        emitByte(140)
    }

    /** Emit `f32.ceil`. */
    fun f32Ceil() {
        emitByte(141)
    }

    /** Emit `f32.floor`. */
    fun f32Floor() {
        emitByte(142)
    }

    /** Emit `f32.trunc`. */
    fun f32Trunc() {
        emitByte(143)
    }

    /** Emit `f32.nearest`. */
    fun f32Nearest() {
        emitByte(144)
    }

    /** Emit `f32.sqrt`. */
    fun f32Sqrt() {
        emitByte(145)
    }

    /** Emit `f32.add`. */
    fun f32Add() {
        emitByte(146)
    }

    /** Emit `f32.sub`. */
    fun f32Sub() {
        emitByte(147)
    }

    /** Emit `f32.mul`. */
    fun f32Mul() {
        emitByte(148)
    }

    /** Emit `f32.div`. */
    fun f32Div() {
        emitByte(149)
    }

    /** Emit `f32.min`. */
    fun f32Min() {
        emitByte(150)
    }

    /** Emit `f32.max`. */
    fun f32Max() {
        emitByte(151)
    }

    /** Emit `f32.copysign`. */
    fun f32Copysign() {
        emitByte(152)
    }

    /** Emit `f64.abs`. */
    fun f64Abs() {
        emitByte(153)
    }

    /** Emit `f64.neg`. */
    fun f64Neg() {
        emitByte(154)
    }

    /** Emit `f64.ceil`. */
    fun f64Ceil() {
        emitByte(155)
    }

    /** Emit `f64.floor`. */
    fun f64Floor() {
        emitByte(156)
    }

    /** Emit `f64.trunc`. */
    fun f64Trunc() {
        emitByte(157)
    }

    /** Emit `f64.nearest`. */
    fun f64Nearest() {
        emitByte(158)
    }

    /** Emit `f64.sqrt`. */
    fun f64Sqrt() {
        emitByte(159)
    }

    /** Emit `f64.add`. */
    fun f64Add() {
        emitByte(160)
    }

    /** Emit `f64.sub`. */
    fun f64Sub() {
        emitByte(161)
    }

    /** Emit `f64.mul`. */
    fun f64Mul() {
        emitByte(162)
    }

    /** Emit `f64.div`. */
    fun f64Div() {
        emitByte(163)
    }

    /** Emit `f64.min`. */
    fun f64Min() {
        emitByte(164)
    }

    /** Emit `f64.max`. */
    fun f64Max() {
        emitByte(165)
    }

    /** Emit `f64.copysign`. */
    fun f64Copysign() {
        emitByte(166)
    }

    /** Emit `i32.wrap_i64`. */
    fun i32WrapI64() {
        emitByte(167)
    }

    /** Emit `i32.trunc_f32_s`. */
    fun i32TruncF32S() {
        emitByte(168)
    }

    /** Emit `i32.trunc_f32_u`. */
    fun i32TruncF32U() {
        emitByte(169)
    }

    /** Emit `i32.trunc_f64_s`. */
    fun i32TruncF64S() {
        emitByte(170)
    }

    /** Emit `i32.trunc_f64_u`. */
    fun i32TruncF64U() {
        emitByte(171)
    }

    /** Emit `i64.extend_i32_s`. */
    fun i64ExtendI32S() {
        emitByte(172)
    }

    /** Emit `i64.extend_i32_u`. */
    fun i64ExtendI32U() {
        emitByte(173)
    }

    /** Emit `i64.trunc_f32_s`. */
    fun i64TruncF32S() {
        emitByte(174)
    }

    /** Emit `i64.trunc_f32_u`. */
    fun i64TruncF32U() {
        emitByte(175)
    }

    /** Emit `i64.trunc_f64_s`. */
    fun i64TruncF64S() {
        emitByte(176)
    }

    /** Emit `i64.trunc_f64_u`. */
    fun i64TruncF64U() {
        emitByte(177)
    }

    /** Emit `f32.convert_i32_s`. */
    fun f32ConvertI32S() {
        emitByte(178)
    }

    /** Emit `f32.convert_i32_u`. */
    fun f32ConvertI32U() {
        emitByte(179)
    }

    /** Emit `f32.convert_i64_s`. */
    fun f32ConvertI64S() {
        emitByte(180)
    }

    /** Emit `f32.convert_i64_u`. */
    fun f32ConvertI64U() {
        emitByte(181)
    }

    /** Emit `f32.demote_f64`. */
    fun f32DemoteF64() {
        emitByte(182)
    }

    /** Emit `f64.convert_i32_s`. */
    fun f64ConvertI32S() {
        emitByte(183)
    }

    /** Emit `f64.convert_i32_u`. */
    fun f64ConvertI32U() {
        emitByte(184)
    }

    /** Emit `f64.convert_i64_s`. */
    fun f64ConvertI64S() {
        emitByte(185)
    }

    /** Emit `f64.convert_i64_u`. */
    fun f64ConvertI64U() {
        emitByte(186)
    }

    /** Emit `f64.promote_f32`. */
    fun f64PromoteF32() {
        emitByte(187)
    }

    /** Emit `i32.reinterpret_f32`. */
    fun i32ReinterpretF32() {
        emitByte(188)
    }

    /** Emit `i64.reinterpret_f64`. */
    fun i64ReinterpretF64() {
        emitByte(189)
    }

    /** Emit `f32.reinterpret_i32`. */
    fun f32ReinterpretI32() {
        emitByte(190)
    }

    /** Emit `f64.reinterpret_i64`. */
    fun f64ReinterpretI64() {
        emitByte(191)
    }

    /** Emit `ref.null`. */
    fun refNull(refType: Int) {
        emitByte(208)
        emitU32(refType)
    }

    /** Emit `ref.is_null`. */
    fun refIsNull() {
        emitByte(209)
    }

    /** Emit `ref.func`. */
    fun refFunc(funcIdx: Int) {
        emitByte(210)
        emitU32(funcIdx)
    }

    /** Emit `i32.extend8_s`. */
    fun i32Extend8S() {
        emitByte(192)
    }

    /** Emit `i32.extend16_s`. */
    fun i32Extend16S() {
        emitByte(193)
    }

    /** Emit `i64.extend8_s`. */
    fun i64Extend8S() {
        emitByte(194)
    }

    /** Emit `i64.extend16_s`. */
    fun i64Extend16S() {
        emitByte(195)
    }

    /** Emit `i64.extend32_s`. */
    fun i64Extend32S() {
        emitByte(196)
    }

    /** Emit `i32.trunc_sat_f32_s`. */
    fun i32TruncSatF32S() {
        emitByte(252)
        emitU32(64512)
    }

    /** Emit `i32.trunc_sat_f32_u`. */
    fun i32TruncSatF32U() {
        emitByte(252)
        emitU32(64513)
    }

    /** Emit `i32.trunc_sat_f64_s`. */
    fun i32TruncSatF64S() {
        emitByte(252)
        emitU32(64514)
    }

    /** Emit `i32.trunc_sat_f64_u`. */
    fun i32TruncSatF64U() {
        emitByte(252)
        emitU32(64515)
    }

    /** Emit `i64.trunc_sat_f32_s`. */
    fun i64TruncSatF32S() {
        emitByte(252)
        emitU32(64516)
    }

    /** Emit `i64.trunc_sat_f32_u`. */
    fun i64TruncSatF32U() {
        emitByte(252)
        emitU32(64517)
    }

    /** Emit `i64.trunc_sat_f64_s`. */
    fun i64TruncSatF64S() {
        emitByte(252)
        emitU32(64518)
    }

    /** Emit `i64.trunc_sat_f64_u`. */
    fun i64TruncSatF64U() {
        emitByte(252)
        emitU32(64519)
    }

    /** Emit `memory.init`. */
    fun memoryInit(dataIdx: Int, memIdx: Int) {
        emitByte(252)
        emitU32(64520)
        emitU32(dataIdx)
        emitU32(memIdx)
    }

    /** Emit `data.drop`. */
    fun dataDrop(dataIdx: Int) {
        emitByte(252)
        emitU32(64521)
        emitU32(dataIdx)
    }

    /** Emit `memory.copy`. */
    fun memoryCopy(destMemIdx: Int, srcMemIdx: Int) {
        emitByte(252)
        emitU32(64522)
        emitU32(destMemIdx)
        emitU32(srcMemIdx)
    }

    /** Emit `memory.fill`. */
    fun memoryFill(memIdx: Int) {
        emitByte(252)
        emitU32(64523)
        emitU32(memIdx)
    }

    /** Emit `table.init`. */
    fun tableInit(elemIdx: Int, tableIdx: Int) {
        emitByte(252)
        emitU32(64524)
        emitU32(elemIdx)
        emitU32(tableIdx)
    }

    /** Emit `elem.drop`. */
    fun elemDrop(elemIdx: Int) {
        emitByte(252)
        emitU32(64525)
        emitU32(elemIdx)
    }

    /** Emit `table.copy`. */
    fun tableCopy(destTableIdx: Int, srcTableIdx: Int) {
        emitByte(252)
        emitU32(64526)
        emitU32(destTableIdx)
        emitU32(srcTableIdx)
    }

    /** Emit `table.grow`. */
    fun tableGrow(tableIdx: Int) {
        emitByte(252)
        emitU32(64527)
        emitU32(tableIdx)
    }

    /** Emit `table.size`. */
    fun tableSize(tableIdx: Int) {
        emitByte(252)
        emitU32(64528)
        emitU32(tableIdx)
    }

    /** Emit `table.fill`. */
    fun tableFill(tableIdx: Int) {
        emitByte(252)
        emitU32(64529)
        emitU32(tableIdx)
    }

    /** Emit `ref.eq`. */
    fun refEq() {
        emitByte(211)
    }

    /** Emit `ref.as_non_null`. */
    fun refAsNonNull() {
        emitByte(212)
    }

    /** Emit `br_on_null`. */
    fun brOnNull(labelIdx: Int) {
        emitByte(213)
        emitU32(labelIdx)
    }

    /** Emit `br_on_non_null`. */
    fun brOnNonNull(labelIdx: Int) {
        emitByte(214)
        emitU32(labelIdx)
    }

    /** Emit `v128.load`. */
    fun v128Load(alignment: Int, offset: Int) {
        emitByte(253)
        emitU32(64768)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `v128.load8x8_s`. */
    fun v128Load8x8S(alignment: Int, offset: Int) {
        emitByte(253)
        emitU32(64769)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `v128.load8x8_u`. */
    fun v128Load8x8U(alignment: Int, offset: Int) {
        emitByte(253)
        emitU32(64770)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `v128.load16x4_s`. */
    fun v128Load16x4S(alignment: Int, offset: Int) {
        emitByte(253)
        emitU32(64771)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `v128.load16x4_u`. */
    fun v128Load16x4U(alignment: Int, offset: Int) {
        emitByte(253)
        emitU32(64772)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `v128.load32x2_s`. */
    fun v128Load32x2S(alignment: Int, offset: Int) {
        emitByte(253)
        emitU32(64773)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `v128.load32x2_u`. */
    fun v128Load32x2U(alignment: Int, offset: Int) {
        emitByte(253)
        emitU32(64774)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `v128.load8_splat`. */
    fun v128Load8Splat(alignment: Int, offset: Int) {
        emitByte(253)
        emitU32(64775)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `v128.load16_splat`. */
    fun v128Load16Splat(alignment: Int, offset: Int) {
        emitByte(253)
        emitU32(64776)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `v128.load32_splat`. */
    fun v128Load32Splat(alignment: Int, offset: Int) {
        emitByte(253)
        emitU32(64777)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `v128.load64_splat`. */
    fun v128Load64Splat(alignment: Int, offset: Int) {
        emitByte(253)
        emitU32(64778)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `v128.store`. */
    fun v128Store(alignment: Int, offset: Int) {
        emitByte(253)
        emitU32(64779)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `v128.const`. */
    fun v128Const(bytes: ByteArray) {
        emitByte(253)
        emitU32(64780)
        emitBytes(bytes)
    }

    /** Emit `i8x16.shuffle`. */
    fun i8x16Shuffle(lanes: ByteArray) {
        emitByte(253)
        emitU32(64781)
        emitBytes(lanes)
    }

    /** Emit `i8x16.swizzle`. */
    fun i8x16Swizzle() {
        emitByte(253)
        emitU32(64782)
    }

    /** Emit `i8x16.splat`. */
    fun i8x16Splat() {
        emitByte(253)
        emitU32(64783)
    }

    /** Emit `i16x8.splat`. */
    fun i16x8Splat() {
        emitByte(253)
        emitU32(64784)
    }

    /** Emit `i32x4.splat`. */
    fun i32x4Splat() {
        emitByte(253)
        emitU32(64785)
    }

    /** Emit `i64x2.splat`. */
    fun i64x2Splat() {
        emitByte(253)
        emitU32(64786)
    }

    /** Emit `f32x4.splat`. */
    fun f32x4Splat() {
        emitByte(253)
        emitU32(64787)
    }

    /** Emit `f64x2.splat`. */
    fun f64x2Splat() {
        emitByte(253)
        emitU32(64788)
    }

    /** Emit `i8x16.extract_lane_s`. */
    fun i8x16ExtractLaneS(laneIdx: Int) {
        emitByte(253)
        emitU32(64789)
        emitU32(laneIdx)
    }

    /** Emit `i8x16.extract_lane_u`. */
    fun i8x16ExtractLaneU(laneIdx: Int) {
        emitByte(253)
        emitU32(64790)
        emitU32(laneIdx)
    }

    /** Emit `i8x16.replace_lane`. */
    fun i8x16ReplaceLane(laneIdx: Int) {
        emitByte(253)
        emitU32(64791)
        emitU32(laneIdx)
    }

    /** Emit `i16x8.extract_lane_s`. */
    fun i16x8ExtractLaneS(laneIdx: Int) {
        emitByte(253)
        emitU32(64792)
        emitU32(laneIdx)
    }

    /** Emit `i16x8.extract_lane_u`. */
    fun i16x8ExtractLaneU(laneIdx: Int) {
        emitByte(253)
        emitU32(64793)
        emitU32(laneIdx)
    }

    /** Emit `i16x8.replace_lane`. */
    fun i16x8ReplaceLane(laneIdx: Int) {
        emitByte(253)
        emitU32(64794)
        emitU32(laneIdx)
    }

    /** Emit `i32x4.extract_lane`. */
    fun i32x4ExtractLane(laneIdx: Int) {
        emitByte(253)
        emitU32(64795)
        emitU32(laneIdx)
    }

    /** Emit `i32x4.replace_lane`. */
    fun i32x4ReplaceLane(laneIdx: Int) {
        emitByte(253)
        emitU32(64796)
        emitU32(laneIdx)
    }

    /** Emit `i64x2.extract_lane`. */
    fun i64x2ExtractLane(laneIdx: Int) {
        emitByte(253)
        emitU32(64797)
        emitU32(laneIdx)
    }

    /** Emit `i64x2.replace_lane`. */
    fun i64x2ReplaceLane(laneIdx: Int) {
        emitByte(253)
        emitU32(64798)
        emitU32(laneIdx)
    }

    /** Emit `f32x4.extract_lane`. */
    fun f32x4ExtractLane(laneIdx: Int) {
        emitByte(253)
        emitU32(64799)
        emitU32(laneIdx)
    }

    /** Emit `f32x4.replace_lane`. */
    fun f32x4ReplaceLane(laneIdx: Int) {
        emitByte(253)
        emitU32(64800)
        emitU32(laneIdx)
    }

    /** Emit `f64x2.extract_lane`. */
    fun f64x2ExtractLane(laneIdx: Int) {
        emitByte(253)
        emitU32(64801)
        emitU32(laneIdx)
    }

    /** Emit `f64x2.replace_lane`. */
    fun f64x2ReplaceLane(laneIdx: Int) {
        emitByte(253)
        emitU32(64802)
        emitU32(laneIdx)
    }

    /** Emit `i8x16.eq`. */
    fun i8x16Eq() {
        emitByte(253)
        emitU32(64803)
    }

    /** Emit `i8x16.ne`. */
    fun i8x16Ne() {
        emitByte(253)
        emitU32(64804)
    }

    /** Emit `i8x16.lt_s`. */
    fun i8x16LtS() {
        emitByte(253)
        emitU32(64805)
    }

    /** Emit `i8x16.lt_u`. */
    fun i8x16LtU() {
        emitByte(253)
        emitU32(64806)
    }

    /** Emit `i8x16.gt_s`. */
    fun i8x16GtS() {
        emitByte(253)
        emitU32(64807)
    }

    /** Emit `i8x16.gt_u`. */
    fun i8x16GtU() {
        emitByte(253)
        emitU32(64808)
    }

    /** Emit `i8x16.le_s`. */
    fun i8x16LeS() {
        emitByte(253)
        emitU32(64809)
    }

    /** Emit `i8x16.le_u`. */
    fun i8x16LeU() {
        emitByte(253)
        emitU32(64810)
    }

    /** Emit `i8x16.ge_s`. */
    fun i8x16GeS() {
        emitByte(253)
        emitU32(64811)
    }

    /** Emit `i8x16.ge_u`. */
    fun i8x16GeU() {
        emitByte(253)
        emitU32(64812)
    }

    /** Emit `i8x16.abs`. */
    fun i8x16Abs() {
        emitByte(253)
        emitU32(64864)
    }

    /** Emit `i8x16.neg`. */
    fun i8x16Neg() {
        emitByte(253)
        emitU32(64865)
    }

    /** Emit `i8x16.all_true`. */
    fun i8x16AllTrue() {
        emitByte(253)
        emitU32(64867)
    }

    /** Emit `i8x16.bitmask`. */
    fun i8x16Bitmask() {
        emitByte(253)
        emitU32(64868)
    }

    /** Emit `i8x16.add`. */
    fun i8x16Add() {
        emitByte(253)
        emitU32(64875)
    }

    /** Emit `i8x16.add_sat_s`. */
    fun i8x16AddSatS() {
        emitByte(253)
        emitU32(64876)
    }

    /** Emit `i8x16.add_sat_u`. */
    fun i8x16AddSatU() {
        emitByte(253)
        emitU32(64877)
    }

    /** Emit `i8x16.sub`. */
    fun i8x16Sub() {
        emitByte(253)
        emitU32(64878)
    }

    /** Emit `i8x16.sub_sat_s`. */
    fun i8x16SubSatS() {
        emitByte(253)
        emitU32(64879)
    }

    /** Emit `i8x16.sub_sat_u`. */
    fun i8x16SubSatU() {
        emitByte(253)
        emitU32(64880)
    }

    /** Emit `i8x16.min_s`. */
    fun i8x16MinS() {
        emitByte(253)
        emitU32(64886)
    }

    /** Emit `i8x16.min_u`. */
    fun i8x16MinU() {
        emitByte(253)
        emitU32(64887)
    }

    /** Emit `i8x16.max_s`. */
    fun i8x16MaxS() {
        emitByte(253)
        emitU32(64888)
    }

    /** Emit `i8x16.max_u`. */
    fun i8x16MaxU() {
        emitByte(253)
        emitU32(64889)
    }

    /** Emit `i16x8.add`. */
    fun i16x8Add() {
        emitByte(253)
        emitU32(64892)
    }

    /** Emit `i16x8.add_sat_s`. */
    fun i16x8AddSatS() {
        emitByte(253)
        emitU32(64893)
    }

    /** Emit `i16x8.add_sat_u`. */
    fun i16x8AddSatU() {
        emitByte(253)
        emitU32(64894)
    }

    /** Emit `i16x8.sub`. */
    fun i16x8Sub() {
        emitByte(253)
        emitU32(64895)
    }

    /** Emit `i16x8.sub_sat_s`. */
    fun i16x8SubSatS() {
        emitByte(253)
        emitU32(64896)
    }

    /** Emit `i16x8.sub_sat_u`. */
    fun i16x8SubSatU() {
        emitByte(253)
        emitU32(64897)
    }

    /** Emit `i16x8.mul`. */
    fun i16x8Mul() {
        emitByte(253)
        emitU32(64901)
    }

    /** Emit `i32x4.add`. */
    fun i32x4Add() {
        emitByte(253)
        emitU32(64910)
    }

    /** Emit `i32x4.sub`. */
    fun i32x4Sub() {
        emitByte(253)
        emitU32(64913)
    }

    /** Emit `i32x4.mul`. */
    fun i32x4Mul() {
        emitByte(253)
        emitU32(64917)
    }

    /** Emit `i64x2.add`. */
    fun i64x2Add() {
        emitByte(253)
        emitU32(64974)
    }

    /** Emit `i64x2.sub`. */
    fun i64x2Sub() {
        emitByte(253)
        emitU32(64977)
    }

    /** Emit `i64x2.mul`. */
    fun i64x2Mul() {
        emitByte(253)
        emitU32(64981)
    }

    /** Emit `f32x4.add`. */
    fun f32x4Add() {
        emitByte(253)
        emitU32(64992)
    }

    /** Emit `f32x4.sub`. */
    fun f32x4Sub() {
        emitByte(253)
        emitU32(64993)
    }

    /** Emit `f32x4.mul`. */
    fun f32x4Mul() {
        emitByte(253)
        emitU32(64994)
    }

    /** Emit `f32x4.div`. */
    fun f32x4Div() {
        emitByte(253)
        emitU32(64995)
    }

    /** Emit `f64x2.add`. */
    fun f64x2Add() {
        emitByte(253)
        emitU32(65008)
    }

    /** Emit `f64x2.sub`. */
    fun f64x2Sub() {
        emitByte(253)
        emitU32(65009)
    }

    /** Emit `f64x2.mul`. */
    fun f64x2Mul() {
        emitByte(253)
        emitU32(65010)
    }

    /** Emit `f64x2.div`. */
    fun f64x2Div() {
        emitByte(253)
        emitU32(65011)
    }

    /** Emit `v128.not`. */
    fun v128Not() {
        emitByte(253)
        emitU32(64845)
    }

    /** Emit `v128.and`. */
    fun v128And() {
        emitByte(253)
        emitU32(64846)
    }

    /** Emit `v128.andnot`. */
    fun v128AndNot() {
        emitByte(253)
        emitU32(64847)
    }

    /** Emit `v128.or`. */
    fun v128Or() {
        emitByte(253)
        emitU32(64848)
    }

    /** Emit `v128.xor`. */
    fun v128Xor() {
        emitByte(253)
        emitU32(64849)
    }

    /** Emit `v128.bitselect`. */
    fun v128Bitselect() {
        emitByte(253)
        emitU32(64850)
    }

    /** Emit `v128.any_true`. */
    fun v128AnyTrue() {
        emitByte(253)
        emitU32(64851)
    }

    /** Emit `v128.load8_lane`. */
    fun v128Load8Lane(alignment: Int, offset: Int, laneIdx: Int) {
        emitByte(253)
        emitU32(64852)
        emitU32(alignment)
        emitU32(offset)
        emitU32(laneIdx)
    }

    /** Emit `v128.load16_lane`. */
    fun v128Load16Lane(alignment: Int, offset: Int, laneIdx: Int) {
        emitByte(253)
        emitU32(64853)
        emitU32(alignment)
        emitU32(offset)
        emitU32(laneIdx)
    }

    /** Emit `v128.load32_lane`. */
    fun v128Load32Lane(alignment: Int, offset: Int, laneIdx: Int) {
        emitByte(253)
        emitU32(64854)
        emitU32(alignment)
        emitU32(offset)
        emitU32(laneIdx)
    }

    /** Emit `v128.load64_lane`. */
    fun v128Load64Lane(alignment: Int, offset: Int, laneIdx: Int) {
        emitByte(253)
        emitU32(64855)
        emitU32(alignment)
        emitU32(offset)
        emitU32(laneIdx)
    }

    /** Emit `v128.store8_lane`. */
    fun v128Store8Lane(alignment: Int, offset: Int, laneIdx: Int) {
        emitByte(253)
        emitU32(64856)
        emitU32(alignment)
        emitU32(offset)
        emitU32(laneIdx)
    }

    /** Emit `v128.store16_lane`. */
    fun v128Store16Lane(alignment: Int, offset: Int, laneIdx: Int) {
        emitByte(253)
        emitU32(64857)
        emitU32(alignment)
        emitU32(offset)
        emitU32(laneIdx)
    }

    /** Emit `v128.store32_lane`. */
    fun v128Store32Lane(alignment: Int, offset: Int, laneIdx: Int) {
        emitByte(253)
        emitU32(64858)
        emitU32(alignment)
        emitU32(offset)
        emitU32(laneIdx)
    }

    /** Emit `v128.store64_lane`. */
    fun v128Store64Lane(alignment: Int, offset: Int, laneIdx: Int) {
        emitByte(253)
        emitU32(64859)
        emitU32(alignment)
        emitU32(offset)
        emitU32(laneIdx)
    }

    /** Emit `v128.load32_zero`. */
    fun v128Load32Zero(alignment: Int, offset: Int) {
        emitByte(253)
        emitU32(64860)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `v128.load64_zero`. */
    fun v128Load64Zero(alignment: Int, offset: Int) {
        emitByte(253)
        emitU32(64861)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `return_call`. */
    fun returnCall(funcIdx: Int) {
        emitByte(18)
        emitU32(funcIdx)
    }

    /** Emit `return_call_indirect`. */
    fun returnCallIndirect(typeIdx: Int, tableIdx: Int) {
        emitByte(19)
        emitU32(typeIdx)
        emitU32(tableIdx)
    }

    /** Emit `try`. */
    fun try_(blockType: Int) {
        emitByte(6)
        emitU32(blockType)
    }

    /** Emit `catch`. */
    fun catch_(tagIdx: Int) {
        emitByte(7)
        emitU32(tagIdx)
    }

    /** Emit `throw`. */
    fun throw_(tagIdx: Int) {
        emitByte(8)
        emitU32(tagIdx)
    }

    /** Emit `rethrow`. */
    fun rethrow(labelIdx: Int) {
        emitByte(9)
        emitU32(labelIdx)
    }

    /** Emit `delegate`. */
    fun delegate(labelIdx: Int) {
        emitByte(24)
        emitU32(labelIdx)
    }

    /** Emit `catch_all`. */
    fun catchAll() {
        emitByte(25)
    }

    /** Emit `memory.atomic.notify`. */
    fun memoryAtomicNotify(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65024)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `memory.atomic.wait32`. */
    fun memoryAtomicWait32(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65025)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `memory.atomic.wait64`. */
    fun memoryAtomicWait64(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65026)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `atomic.fence`. */
    fun atomicFence(byteVal: Int) {
        emitByte(254)
        emitU32(65027)
        emitU32(byteVal)
    }

    /** Emit `i32.atomic.load`. */
    fun i32AtomicLoad(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65040)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `i64.atomic.load`. */
    fun i64AtomicLoad(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65041)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `i32.atomic.load8_u`. */
    fun i32AtomicLoad8U(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65042)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `i32.atomic.load16_u`. */
    fun i32AtomicLoad16U(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65043)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `i64.atomic.load8_u`. */
    fun i64AtomicLoad8U(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65044)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `i64.atomic.load16_u`. */
    fun i64AtomicLoad16U(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65045)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `i64.atomic.load32_u`. */
    fun i64AtomicLoad32U(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65046)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `i32.atomic.store`. */
    fun i32AtomicStore(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65047)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `i64.atomic.store`. */
    fun i64AtomicStore(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65048)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `i32.atomic.store8`. */
    fun i32AtomicStore8(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65049)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `i32.atomic.store16`. */
    fun i32AtomicStore16(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65050)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `i64.atomic.store8`. */
    fun i64AtomicStore8(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65051)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `i64.atomic.store16`. */
    fun i64AtomicStore16(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65052)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `i64.atomic.store32`. */
    fun i64AtomicStore32(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65053)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `i32.atomic.rmw.add`. */
    fun i32AtomicRmwAdd(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65054)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `i64.atomic.rmw.add`. */
    fun i64AtomicRmwAdd(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65055)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `i32.atomic.rmw8.add_u`. */
    fun i32AtomicRmw8AddU(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65056)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `i32.atomic.rmw16.add_u`. */
    fun i32AtomicRmw16AddU(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65057)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `i64.atomic.rmw8.add_u`. */
    fun i64AtomicRmw8AddU(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65058)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `i64.atomic.rmw16.add_u`. */
    fun i64AtomicRmw16AddU(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65059)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `i64.atomic.rmw32.add_u`. */
    fun i64AtomicRmw32AddU(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65060)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `i32.atomic.rmw.sub`. */
    fun i32AtomicRmwSub(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65061)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `i64.atomic.rmw.sub`. */
    fun i64AtomicRmwSub(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65062)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `i32.atomic.rmw8.sub_u`. */
    fun i32AtomicRmw8SubU(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65063)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `i32.atomic.rmw16.sub_u`. */
    fun i32AtomicRmw16SubU(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65064)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `i64.atomic.rmw8.sub_u`. */
    fun i64AtomicRmw8SubU(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65065)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `i64.atomic.rmw16.sub_u`. */
    fun i64AtomicRmw16SubU(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65066)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `i64.atomic.rmw32.sub_u`. */
    fun i64AtomicRmw32SubU(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65067)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `i32.atomic.rmw.and`. */
    fun i32AtomicRmwAnd(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65068)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `i64.atomic.rmw.and`. */
    fun i64AtomicRmwAnd(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65069)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `i32.atomic.rmw8.and_u`. */
    fun i32AtomicRmw8AndU(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65070)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `i32.atomic.rmw16.and_u`. */
    fun i32AtomicRmw16AndU(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65071)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `i64.atomic.rmw8.and_u`. */
    fun i64AtomicRmw8AndU(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65072)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `i64.atomic.rmw16.and_u`. */
    fun i64AtomicRmw16AndU(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65073)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `i64.atomic.rmw32.and_u`. */
    fun i64AtomicRmw32AndU(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65074)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `i32.atomic.rmw.or`. */
    fun i32AtomicRmwOr(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65075)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `i64.atomic.rmw.or`. */
    fun i64AtomicRmwOr(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65076)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `i32.atomic.rmw8.or_u`. */
    fun i32AtomicRmw8OrU(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65077)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `i32.atomic.rmw16.or_u`. */
    fun i32AtomicRmw16OrU(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65078)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `i64.atomic.rmw8.or_u`. */
    fun i64AtomicRmw8OrU(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65079)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `i64.atomic.rmw16.or_u`. */
    fun i64AtomicRmw16OrU(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65080)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `i64.atomic.rmw32.or_u`. */
    fun i64AtomicRmw32OrU(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65081)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `i32.atomic.rmw.xor`. */
    fun i32AtomicRmwXor(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65082)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `i64.atomic.rmw.xor`. */
    fun i64AtomicRmwXor(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65083)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `i32.atomic.rmw8.xor_u`. */
    fun i32AtomicRmw8XorU(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65084)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `i32.atomic.rmw16.xor_u`. */
    fun i32AtomicRmw16XorU(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65085)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `i64.atomic.rmw8.xor_u`. */
    fun i64AtomicRmw8XorU(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65086)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `i64.atomic.rmw16.xor_u`. */
    fun i64AtomicRmw16XorU(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65087)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `i64.atomic.rmw32.xor_u`. */
    fun i64AtomicRmw32XorU(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65088)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `i32.atomic.rmw.xchg`. */
    fun i32AtomicRmwXchg(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65089)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `i64.atomic.rmw.xchg`. */
    fun i64AtomicRmwXchg(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65090)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `i32.atomic.rmw8.xchg_u`. */
    fun i32AtomicRmw8XchgU(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65091)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `i32.atomic.rmw16.xchg_u`. */
    fun i32AtomicRmw16XchgU(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65092)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `i64.atomic.rmw8.xchg_u`. */
    fun i64AtomicRmw8XchgU(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65093)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `i64.atomic.rmw16.xchg_u`. */
    fun i64AtomicRmw16XchgU(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65094)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `i64.atomic.rmw32.xchg_u`. */
    fun i64AtomicRmw32XchgU(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65095)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `i32.atomic.rmw.cmpxchg`. */
    fun i32AtomicRmwCmpxchg(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65096)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `i64.atomic.rmw.cmpxchg`. */
    fun i64AtomicRmwCmpxchg(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65097)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `i32.atomic.rmw8.cmpxchg_u`. */
    fun i32AtomicRmw8CmpxchgU(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65098)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `i32.atomic.rmw16.cmpxchg_u`. */
    fun i32AtomicRmw16CmpxchgU(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65099)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `i64.atomic.rmw8.cmpxchg_u`. */
    fun i64AtomicRmw8CmpxchgU(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65100)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `i64.atomic.rmw16.cmpxchg_u`. */
    fun i64AtomicRmw16CmpxchgU(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65101)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `i64.atomic.rmw32.cmpxchg_u`. */
    fun i64AtomicRmw32CmpxchgU(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65102)
        emitU32(alignment)
        emitU32(offset)
    }

    /** Emit `struct.new`. */
    fun structNew(typeIdx: Int) {
        emitByte(251)
        emitU32(64256)
        emitU32(typeIdx)
    }

    /** Emit `struct.new_default`. */
    fun structNewDefault(typeIdx: Int) {
        emitByte(251)
        emitU32(64257)
        emitU32(typeIdx)
    }

    /** Emit `struct.get`. */
    fun structGet(typeIdx: Int, fieldIdx: Int) {
        emitByte(251)
        emitU32(64258)
        emitU32(typeIdx)
        emitU32(fieldIdx)
    }

    /** Emit `struct.get_s`. */
    fun structGetS(typeIdx: Int, fieldIdx: Int) {
        emitByte(251)
        emitU32(64259)
        emitU32(typeIdx)
        emitU32(fieldIdx)
    }

    /** Emit `struct.get_u`. */
    fun structGetU(typeIdx: Int, fieldIdx: Int) {
        emitByte(251)
        emitU32(64260)
        emitU32(typeIdx)
        emitU32(fieldIdx)
    }

    /** Emit `struct.set`. */
    fun structSet(typeIdx: Int, fieldIdx: Int) {
        emitByte(251)
        emitU32(64261)
        emitU32(typeIdx)
        emitU32(fieldIdx)
    }

    /** Emit `array.new`. */
    fun arrayNew(typeIdx: Int) {
        emitByte(251)
        emitU32(64262)
        emitU32(typeIdx)
    }

    /** Emit `array.new_default`. */
    fun arrayNewDefault(typeIdx: Int) {
        emitByte(251)
        emitU32(64263)
        emitU32(typeIdx)
    }

    /** Emit `array.new_fixed`. */
    fun arrayNewFixed(typeIdx: Int, length: Int) {
        emitByte(251)
        emitU32(64264)
        emitU32(typeIdx)
        emitU32(length)
    }

    /** Emit `array.new_data`. */
    fun arrayNewData(typeIdx: Int, dataIdx: Int) {
        emitByte(251)
        emitU32(64265)
        emitU32(typeIdx)
        emitU32(dataIdx)
    }

    /** Emit `array.new_elem`. */
    fun arrayNewElem(typeIdx: Int, elemIdx: Int) {
        emitByte(251)
        emitU32(64266)
        emitU32(typeIdx)
        emitU32(elemIdx)
    }

    /** Emit `array.get`. */
    fun arrayGet(typeIdx: Int) {
        emitByte(251)
        emitU32(64267)
        emitU32(typeIdx)
    }

    /** Emit `array.get_s`. */
    fun arrayGetS(typeIdx: Int) {
        emitByte(251)
        emitU32(64268)
        emitU32(typeIdx)
    }

    /** Emit `array.get_u`. */
    fun arrayGetU(typeIdx: Int) {
        emitByte(251)
        emitU32(64269)
        emitU32(typeIdx)
    }

    /** Emit `array.set`. */
    fun arraySet(typeIdx: Int) {
        emitByte(251)
        emitU32(64270)
        emitU32(typeIdx)
    }

    /** Emit `array.len`. */
    fun arrayLen() {
        emitByte(251)
        emitU32(64271)
    }

    /** Emit `array.fill`. */
    fun arrayFill(typeIdx: Int) {
        emitByte(251)
        emitU32(64272)
        emitU32(typeIdx)
    }

    /** Emit `array.copy`. */
    fun arrayCopy(destTypeIdx: Int, srcTypeIdx: Int) {
        emitByte(251)
        emitU32(64273)
        emitU32(destTypeIdx)
        emitU32(srcTypeIdx)
    }

    /** Emit `ref.test`. */
    fun refTest(heapType: Int) {
        emitByte(251)
        emitU32(64276)
        emitU32(heapType)
    }

    /** Emit `ref.test null`. */
    fun refTestNull(heapType: Int) {
        emitByte(251)
        emitU32(64277)
        emitU32(heapType)
    }

    /** Emit `ref.cast`. */
    fun refCast(heapType: Int) {
        emitByte(251)
        emitU32(64278)
        emitU32(heapType)
    }

    /** Emit `ref.cast null`. */
    fun refCastNull(heapType: Int) {
        emitByte(251)
        emitU32(64279)
        emitU32(heapType)
    }

    /** Emit `br_on_cast`. */
    fun brOnCast(flags: Int, labelIdx: Int, srcType: Int, destType: Int) {
        emitByte(251)
        emitU32(64280)
        emitU32(flags)
        emitU32(labelIdx)
        emitU32(srcType)
        emitU32(destType)
    }

    /** Emit `br_on_cast_fail`. */
    fun brOnCastFail(flags: Int, labelIdx: Int, srcType: Int, destType: Int) {
        emitByte(251)
        emitU32(64281)
        emitU32(flags)
        emitU32(labelIdx)
        emitU32(srcType)
        emitU32(destType)
    }

    /** Emit `any.convert_extern`. */
    fun anyConvertExtern() {
        emitByte(251)
        emitU32(64282)
    }

    /** Emit `extern.convert_any`. */
    fun externConvertAny() {
        emitByte(251)
        emitU32(64283)
    }

    /** Emit `ref.i31`. */
    fun refI31() {
        emitByte(251)
        emitU32(64284)
    }

    /** Emit `i31.get_s`. */
    fun i31GetS() {
        emitByte(251)
        emitU32(64285)
    }

    /** Emit `i31.get_u`. */
    fun i31GetU() {
        emitByte(251)
        emitU32(64286)
    }

}
