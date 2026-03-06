// Generated — do not edit
package org.kgen.wasm

/** Generated assembler methods for all WASM opcodes. */
abstract class WasmAssemblerOps {

    protected abstract fun emitByte(b: Int)
    protected abstract fun emitU32(value: Int)
    protected abstract fun emitS32(value: Int)
    protected abstract fun emitS64(value: Long)
    protected abstract fun emitF32(value: Float)
    protected abstract fun emitF64(value: Double)
    protected abstract fun emitBytes(bytes: ByteArray)

    fun unreachable() {
        emitByte(0)
    }

    fun nop() {
        emitByte(1)
    }

    fun block(blockType: Int) {
        emitByte(2)
        emitU32(blockType)
    }

    fun loop(blockType: Int) {
        emitByte(3)
        emitU32(blockType)
    }

    fun if_(blockType: Int) {
        emitByte(4)
        emitU32(blockType)
    }

    fun else_() {
        emitByte(5)
    }

    fun end() {
        emitByte(11)
    }

    fun br(labelIdx: Int) {
        emitByte(12)
        emitU32(labelIdx)
    }

    fun brIf(labelIdx: Int) {
        emitByte(13)
        emitU32(labelIdx)
    }

    fun brTable(labels: IntArray) {
        emitByte(14)
        emitU32(labels.size - 1)
        for (label in labels) emitU32(label)
    }

    fun return_() {
        emitByte(15)
    }

    fun call(funcIdx: Int) {
        emitByte(16)
        emitU32(funcIdx)
    }

    fun callIndirect(typeIdx: Int, tableIdx: Int) {
        emitByte(17)
        emitU32(typeIdx)
        emitU32(tableIdx)
    }

    fun drop() {
        emitByte(26)
    }

    fun select() {
        emitByte(27)
    }

    fun selectTyped(typeCount: Int) {
        emitByte(28)
        emitU32(typeCount)
    }

    fun localGet(localIdx: Int) {
        emitByte(32)
        emitU32(localIdx)
    }

    fun localSet(localIdx: Int) {
        emitByte(33)
        emitU32(localIdx)
    }

    fun localTee(localIdx: Int) {
        emitByte(34)
        emitU32(localIdx)
    }

    fun globalGet(globalIdx: Int) {
        emitByte(35)
        emitU32(globalIdx)
    }

    fun globalSet(globalIdx: Int) {
        emitByte(36)
        emitU32(globalIdx)
    }

    fun tableGet(tableIdx: Int) {
        emitByte(37)
        emitU32(tableIdx)
    }

    fun tableSet(tableIdx: Int) {
        emitByte(38)
        emitU32(tableIdx)
    }

    fun i32Load(alignment: Int, offset: Int) {
        emitByte(40)
        emitU32(alignment)
        emitU32(offset)
    }

    fun i64Load(alignment: Int, offset: Int) {
        emitByte(41)
        emitU32(alignment)
        emitU32(offset)
    }

    fun f32Load(alignment: Int, offset: Int) {
        emitByte(42)
        emitU32(alignment)
        emitU32(offset)
    }

    fun f64Load(alignment: Int, offset: Int) {
        emitByte(43)
        emitU32(alignment)
        emitU32(offset)
    }

    fun i32Load8S(alignment: Int, offset: Int) {
        emitByte(44)
        emitU32(alignment)
        emitU32(offset)
    }

    fun i32Load8U(alignment: Int, offset: Int) {
        emitByte(45)
        emitU32(alignment)
        emitU32(offset)
    }

    fun i32Load16S(alignment: Int, offset: Int) {
        emitByte(46)
        emitU32(alignment)
        emitU32(offset)
    }

    fun i32Load16U(alignment: Int, offset: Int) {
        emitByte(47)
        emitU32(alignment)
        emitU32(offset)
    }

    fun i64Load8S(alignment: Int, offset: Int) {
        emitByte(48)
        emitU32(alignment)
        emitU32(offset)
    }

    fun i64Load8U(alignment: Int, offset: Int) {
        emitByte(49)
        emitU32(alignment)
        emitU32(offset)
    }

    fun i64Load16S(alignment: Int, offset: Int) {
        emitByte(50)
        emitU32(alignment)
        emitU32(offset)
    }

    fun i64Load16U(alignment: Int, offset: Int) {
        emitByte(51)
        emitU32(alignment)
        emitU32(offset)
    }

    fun i64Load32S(alignment: Int, offset: Int) {
        emitByte(52)
        emitU32(alignment)
        emitU32(offset)
    }

    fun i64Load32U(alignment: Int, offset: Int) {
        emitByte(53)
        emitU32(alignment)
        emitU32(offset)
    }

    fun i32Store(alignment: Int, offset: Int) {
        emitByte(54)
        emitU32(alignment)
        emitU32(offset)
    }

    fun i64Store(alignment: Int, offset: Int) {
        emitByte(55)
        emitU32(alignment)
        emitU32(offset)
    }

    fun f32Store(alignment: Int, offset: Int) {
        emitByte(56)
        emitU32(alignment)
        emitU32(offset)
    }

    fun f64Store(alignment: Int, offset: Int) {
        emitByte(57)
        emitU32(alignment)
        emitU32(offset)
    }

    fun i32Store8(alignment: Int, offset: Int) {
        emitByte(58)
        emitU32(alignment)
        emitU32(offset)
    }

    fun i32Store16(alignment: Int, offset: Int) {
        emitByte(59)
        emitU32(alignment)
        emitU32(offset)
    }

    fun i64Store8(alignment: Int, offset: Int) {
        emitByte(60)
        emitU32(alignment)
        emitU32(offset)
    }

    fun i64Store16(alignment: Int, offset: Int) {
        emitByte(61)
        emitU32(alignment)
        emitU32(offset)
    }

    fun i64Store32(alignment: Int, offset: Int) {
        emitByte(62)
        emitU32(alignment)
        emitU32(offset)
    }

    fun memorySize(memIdx: Int) {
        emitByte(63)
        emitU32(memIdx)
    }

    fun memoryGrow(memIdx: Int) {
        emitByte(64)
        emitU32(memIdx)
    }

    fun i32Const(value: Int) {
        emitByte(65)
        emitS32(value)
    }

    fun i64Const(value: Long) {
        emitByte(66)
        emitS64(value)
    }

    fun f32Const(value: Float) {
        emitByte(67)
        emitF32(value)
    }

    fun f64Const(value: Double) {
        emitByte(68)
        emitF64(value)
    }

    fun i32Eqz() {
        emitByte(69)
    }

    fun i32Eq() {
        emitByte(70)
    }

    fun i32Ne() {
        emitByte(71)
    }

    fun i32LtS() {
        emitByte(72)
    }

    fun i32LtU() {
        emitByte(73)
    }

    fun i32GtS() {
        emitByte(74)
    }

    fun i32GtU() {
        emitByte(75)
    }

    fun i32LeS() {
        emitByte(76)
    }

    fun i32LeU() {
        emitByte(77)
    }

    fun i32GeS() {
        emitByte(78)
    }

    fun i32GeU() {
        emitByte(79)
    }

    fun i64Eqz() {
        emitByte(80)
    }

    fun i64Eq() {
        emitByte(81)
    }

    fun i64Ne() {
        emitByte(82)
    }

    fun i64LtS() {
        emitByte(83)
    }

    fun i64LtU() {
        emitByte(84)
    }

    fun i64GtS() {
        emitByte(85)
    }

    fun i64GtU() {
        emitByte(86)
    }

    fun i64LeS() {
        emitByte(87)
    }

    fun i64LeU() {
        emitByte(88)
    }

    fun i64GeS() {
        emitByte(89)
    }

    fun i64GeU() {
        emitByte(90)
    }

    fun f32Eq() {
        emitByte(91)
    }

    fun f32Ne() {
        emitByte(92)
    }

    fun f32Lt() {
        emitByte(93)
    }

    fun f32Gt() {
        emitByte(94)
    }

    fun f32Le() {
        emitByte(95)
    }

    fun f32Ge() {
        emitByte(96)
    }

    fun f64Eq() {
        emitByte(97)
    }

    fun f64Ne() {
        emitByte(98)
    }

    fun f64Lt() {
        emitByte(99)
    }

    fun f64Gt() {
        emitByte(100)
    }

    fun f64Le() {
        emitByte(101)
    }

    fun f64Ge() {
        emitByte(102)
    }

    fun i32Clz() {
        emitByte(103)
    }

    fun i32Ctz() {
        emitByte(104)
    }

    fun i32Popcnt() {
        emitByte(105)
    }

    fun i32Add() {
        emitByte(106)
    }

    fun i32Sub() {
        emitByte(107)
    }

    fun i32Mul() {
        emitByte(108)
    }

    fun i32DivS() {
        emitByte(109)
    }

    fun i32DivU() {
        emitByte(110)
    }

    fun i32RemS() {
        emitByte(111)
    }

    fun i32RemU() {
        emitByte(112)
    }

    fun i32And() {
        emitByte(113)
    }

    fun i32Or() {
        emitByte(114)
    }

    fun i32Xor() {
        emitByte(115)
    }

    fun i32Shl() {
        emitByte(116)
    }

    fun i32ShrS() {
        emitByte(117)
    }

    fun i32ShrU() {
        emitByte(118)
    }

    fun i32Rotl() {
        emitByte(119)
    }

    fun i32Rotr() {
        emitByte(120)
    }

    fun i64Clz() {
        emitByte(121)
    }

    fun i64Ctz() {
        emitByte(122)
    }

    fun i64Popcnt() {
        emitByte(123)
    }

    fun i64Add() {
        emitByte(124)
    }

    fun i64Sub() {
        emitByte(125)
    }

    fun i64Mul() {
        emitByte(126)
    }

    fun i64DivS() {
        emitByte(127)
    }

    fun i64DivU() {
        emitByte(128)
    }

    fun i64RemS() {
        emitByte(129)
    }

    fun i64RemU() {
        emitByte(130)
    }

    fun i64And() {
        emitByte(131)
    }

    fun i64Or() {
        emitByte(132)
    }

    fun i64Xor() {
        emitByte(133)
    }

    fun i64Shl() {
        emitByte(134)
    }

    fun i64ShrS() {
        emitByte(135)
    }

    fun i64ShrU() {
        emitByte(136)
    }

    fun i64Rotl() {
        emitByte(137)
    }

    fun i64Rotr() {
        emitByte(138)
    }

    fun f32Abs() {
        emitByte(139)
    }

    fun f32Neg() {
        emitByte(140)
    }

    fun f32Ceil() {
        emitByte(141)
    }

    fun f32Floor() {
        emitByte(142)
    }

    fun f32Trunc() {
        emitByte(143)
    }

    fun f32Nearest() {
        emitByte(144)
    }

    fun f32Sqrt() {
        emitByte(145)
    }

    fun f32Add() {
        emitByte(146)
    }

    fun f32Sub() {
        emitByte(147)
    }

    fun f32Mul() {
        emitByte(148)
    }

    fun f32Div() {
        emitByte(149)
    }

    fun f32Min() {
        emitByte(150)
    }

    fun f32Max() {
        emitByte(151)
    }

    fun f32Copysign() {
        emitByte(152)
    }

    fun f64Abs() {
        emitByte(153)
    }

    fun f64Neg() {
        emitByte(154)
    }

    fun f64Ceil() {
        emitByte(155)
    }

    fun f64Floor() {
        emitByte(156)
    }

    fun f64Trunc() {
        emitByte(157)
    }

    fun f64Nearest() {
        emitByte(158)
    }

    fun f64Sqrt() {
        emitByte(159)
    }

    fun f64Add() {
        emitByte(160)
    }

    fun f64Sub() {
        emitByte(161)
    }

    fun f64Mul() {
        emitByte(162)
    }

    fun f64Div() {
        emitByte(163)
    }

    fun f64Min() {
        emitByte(164)
    }

    fun f64Max() {
        emitByte(165)
    }

    fun f64Copysign() {
        emitByte(166)
    }

    fun i32WrapI64() {
        emitByte(167)
    }

    fun i32TruncF32S() {
        emitByte(168)
    }

    fun i32TruncF32U() {
        emitByte(169)
    }

    fun i32TruncF64S() {
        emitByte(170)
    }

    fun i32TruncF64U() {
        emitByte(171)
    }

    fun i64ExtendI32S() {
        emitByte(172)
    }

    fun i64ExtendI32U() {
        emitByte(173)
    }

    fun i64TruncF32S() {
        emitByte(174)
    }

    fun i64TruncF32U() {
        emitByte(175)
    }

    fun i64TruncF64S() {
        emitByte(176)
    }

    fun i64TruncF64U() {
        emitByte(177)
    }

    fun f32ConvertI32S() {
        emitByte(178)
    }

    fun f32ConvertI32U() {
        emitByte(179)
    }

    fun f32ConvertI64S() {
        emitByte(180)
    }

    fun f32ConvertI64U() {
        emitByte(181)
    }

    fun f32DemoteF64() {
        emitByte(182)
    }

    fun f64ConvertI32S() {
        emitByte(183)
    }

    fun f64ConvertI32U() {
        emitByte(184)
    }

    fun f64ConvertI64S() {
        emitByte(185)
    }

    fun f64ConvertI64U() {
        emitByte(186)
    }

    fun f64PromoteF32() {
        emitByte(187)
    }

    fun i32ReinterpretF32() {
        emitByte(188)
    }

    fun i64ReinterpretF64() {
        emitByte(189)
    }

    fun f32ReinterpretI32() {
        emitByte(190)
    }

    fun f64ReinterpretI64() {
        emitByte(191)
    }

    fun refNull(refType: Int) {
        emitByte(208)
        emitU32(refType)
    }

    fun refIsNull() {
        emitByte(209)
    }

    fun refFunc(funcIdx: Int) {
        emitByte(210)
        emitU32(funcIdx)
    }

    fun i32Extend8S() {
        emitByte(192)
    }

    fun i32Extend16S() {
        emitByte(193)
    }

    fun i64Extend8S() {
        emitByte(194)
    }

    fun i64Extend16S() {
        emitByte(195)
    }

    fun i64Extend32S() {
        emitByte(196)
    }

    fun i32TruncSatF32S() {
        emitByte(252)
        emitU32(64512)
    }

    fun i32TruncSatF32U() {
        emitByte(252)
        emitU32(64513)
    }

    fun i32TruncSatF64S() {
        emitByte(252)
        emitU32(64514)
    }

    fun i32TruncSatF64U() {
        emitByte(252)
        emitU32(64515)
    }

    fun i64TruncSatF32S() {
        emitByte(252)
        emitU32(64516)
    }

    fun i64TruncSatF32U() {
        emitByte(252)
        emitU32(64517)
    }

    fun i64TruncSatF64S() {
        emitByte(252)
        emitU32(64518)
    }

    fun i64TruncSatF64U() {
        emitByte(252)
        emitU32(64519)
    }

    fun memoryInit(dataIdx: Int, memIdx: Int) {
        emitByte(252)
        emitU32(64520)
        emitU32(dataIdx)
        emitU32(memIdx)
    }

    fun dataDrop(dataIdx: Int) {
        emitByte(252)
        emitU32(64521)
        emitU32(dataIdx)
    }

    fun memoryCopy(destMemIdx: Int, srcMemIdx: Int) {
        emitByte(252)
        emitU32(64522)
        emitU32(destMemIdx)
        emitU32(srcMemIdx)
    }

    fun memoryFill(memIdx: Int) {
        emitByte(252)
        emitU32(64523)
        emitU32(memIdx)
    }

    fun tableInit(elemIdx: Int, tableIdx: Int) {
        emitByte(252)
        emitU32(64524)
        emitU32(elemIdx)
        emitU32(tableIdx)
    }

    fun elemDrop(elemIdx: Int) {
        emitByte(252)
        emitU32(64525)
        emitU32(elemIdx)
    }

    fun tableCopy(destTableIdx: Int, srcTableIdx: Int) {
        emitByte(252)
        emitU32(64526)
        emitU32(destTableIdx)
        emitU32(srcTableIdx)
    }

    fun tableGrow(tableIdx: Int) {
        emitByte(252)
        emitU32(64527)
        emitU32(tableIdx)
    }

    fun tableSize(tableIdx: Int) {
        emitByte(252)
        emitU32(64528)
        emitU32(tableIdx)
    }

    fun tableFill(tableIdx: Int) {
        emitByte(252)
        emitU32(64529)
        emitU32(tableIdx)
    }

    fun refEq() {
        emitByte(211)
    }

    fun refAsNonNull() {
        emitByte(212)
    }

    fun brOnNull(labelIdx: Int) {
        emitByte(213)
        emitU32(labelIdx)
    }

    fun brOnNonNull(labelIdx: Int) {
        emitByte(214)
        emitU32(labelIdx)
    }

    fun v128Load(alignment: Int, offset: Int) {
        emitByte(253)
        emitU32(64768)
        emitU32(alignment)
        emitU32(offset)
    }

    fun v128Load8x8S(alignment: Int, offset: Int) {
        emitByte(253)
        emitU32(64769)
        emitU32(alignment)
        emitU32(offset)
    }

    fun v128Load8x8U(alignment: Int, offset: Int) {
        emitByte(253)
        emitU32(64770)
        emitU32(alignment)
        emitU32(offset)
    }

    fun v128Load16x4S(alignment: Int, offset: Int) {
        emitByte(253)
        emitU32(64771)
        emitU32(alignment)
        emitU32(offset)
    }

    fun v128Load16x4U(alignment: Int, offset: Int) {
        emitByte(253)
        emitU32(64772)
        emitU32(alignment)
        emitU32(offset)
    }

    fun v128Load32x2S(alignment: Int, offset: Int) {
        emitByte(253)
        emitU32(64773)
        emitU32(alignment)
        emitU32(offset)
    }

    fun v128Load32x2U(alignment: Int, offset: Int) {
        emitByte(253)
        emitU32(64774)
        emitU32(alignment)
        emitU32(offset)
    }

    fun v128Load8Splat(alignment: Int, offset: Int) {
        emitByte(253)
        emitU32(64775)
        emitU32(alignment)
        emitU32(offset)
    }

    fun v128Load16Splat(alignment: Int, offset: Int) {
        emitByte(253)
        emitU32(64776)
        emitU32(alignment)
        emitU32(offset)
    }

    fun v128Load32Splat(alignment: Int, offset: Int) {
        emitByte(253)
        emitU32(64777)
        emitU32(alignment)
        emitU32(offset)
    }

    fun v128Load64Splat(alignment: Int, offset: Int) {
        emitByte(253)
        emitU32(64778)
        emitU32(alignment)
        emitU32(offset)
    }

    fun v128Store(alignment: Int, offset: Int) {
        emitByte(253)
        emitU32(64779)
        emitU32(alignment)
        emitU32(offset)
    }

    fun v128Const(bytes: ByteArray) {
        emitByte(253)
        emitU32(64780)
        emitBytes(bytes)
    }

    fun i8x16Shuffle(lanes: ByteArray) {
        emitByte(253)
        emitU32(64781)
        emitBytes(lanes)
    }

    fun i8x16Swizzle() {
        emitByte(253)
        emitU32(64782)
    }

    fun i8x16Splat() {
        emitByte(253)
        emitU32(64783)
    }

    fun i16x8Splat() {
        emitByte(253)
        emitU32(64784)
    }

    fun i32x4Splat() {
        emitByte(253)
        emitU32(64785)
    }

    fun i64x2Splat() {
        emitByte(253)
        emitU32(64786)
    }

    fun f32x4Splat() {
        emitByte(253)
        emitU32(64787)
    }

    fun f64x2Splat() {
        emitByte(253)
        emitU32(64788)
    }

    fun i8x16ExtractLaneS(laneIdx: Int) {
        emitByte(253)
        emitU32(64789)
        emitU32(laneIdx)
    }

    fun i8x16ExtractLaneU(laneIdx: Int) {
        emitByte(253)
        emitU32(64790)
        emitU32(laneIdx)
    }

    fun i8x16ReplaceLane(laneIdx: Int) {
        emitByte(253)
        emitU32(64791)
        emitU32(laneIdx)
    }

    fun i16x8ExtractLaneS(laneIdx: Int) {
        emitByte(253)
        emitU32(64792)
        emitU32(laneIdx)
    }

    fun i16x8ExtractLaneU(laneIdx: Int) {
        emitByte(253)
        emitU32(64793)
        emitU32(laneIdx)
    }

    fun i16x8ReplaceLane(laneIdx: Int) {
        emitByte(253)
        emitU32(64794)
        emitU32(laneIdx)
    }

    fun i32x4ExtractLane(laneIdx: Int) {
        emitByte(253)
        emitU32(64795)
        emitU32(laneIdx)
    }

    fun i32x4ReplaceLane(laneIdx: Int) {
        emitByte(253)
        emitU32(64796)
        emitU32(laneIdx)
    }

    fun i64x2ExtractLane(laneIdx: Int) {
        emitByte(253)
        emitU32(64797)
        emitU32(laneIdx)
    }

    fun i64x2ReplaceLane(laneIdx: Int) {
        emitByte(253)
        emitU32(64798)
        emitU32(laneIdx)
    }

    fun f32x4ExtractLane(laneIdx: Int) {
        emitByte(253)
        emitU32(64799)
        emitU32(laneIdx)
    }

    fun f32x4ReplaceLane(laneIdx: Int) {
        emitByte(253)
        emitU32(64800)
        emitU32(laneIdx)
    }

    fun f64x2ExtractLane(laneIdx: Int) {
        emitByte(253)
        emitU32(64801)
        emitU32(laneIdx)
    }

    fun f64x2ReplaceLane(laneIdx: Int) {
        emitByte(253)
        emitU32(64802)
        emitU32(laneIdx)
    }

    fun i8x16Eq() {
        emitByte(253)
        emitU32(64803)
    }

    fun i8x16Ne() {
        emitByte(253)
        emitU32(64804)
    }

    fun i8x16LtS() {
        emitByte(253)
        emitU32(64805)
    }

    fun i8x16LtU() {
        emitByte(253)
        emitU32(64806)
    }

    fun i8x16GtS() {
        emitByte(253)
        emitU32(64807)
    }

    fun i8x16GtU() {
        emitByte(253)
        emitU32(64808)
    }

    fun i8x16LeS() {
        emitByte(253)
        emitU32(64809)
    }

    fun i8x16LeU() {
        emitByte(253)
        emitU32(64810)
    }

    fun i8x16GeS() {
        emitByte(253)
        emitU32(64811)
    }

    fun i8x16GeU() {
        emitByte(253)
        emitU32(64812)
    }

    fun i8x16Abs() {
        emitByte(253)
        emitU32(64864)
    }

    fun i8x16Neg() {
        emitByte(253)
        emitU32(64865)
    }

    fun i8x16AllTrue() {
        emitByte(253)
        emitU32(64867)
    }

    fun i8x16Bitmask() {
        emitByte(253)
        emitU32(64868)
    }

    fun i8x16Add() {
        emitByte(253)
        emitU32(64875)
    }

    fun i8x16AddSatS() {
        emitByte(253)
        emitU32(64876)
    }

    fun i8x16AddSatU() {
        emitByte(253)
        emitU32(64877)
    }

    fun i8x16Sub() {
        emitByte(253)
        emitU32(64878)
    }

    fun i8x16SubSatS() {
        emitByte(253)
        emitU32(64879)
    }

    fun i8x16SubSatU() {
        emitByte(253)
        emitU32(64880)
    }

    fun i8x16MinS() {
        emitByte(253)
        emitU32(64886)
    }

    fun i8x16MinU() {
        emitByte(253)
        emitU32(64887)
    }

    fun i8x16MaxS() {
        emitByte(253)
        emitU32(64888)
    }

    fun i8x16MaxU() {
        emitByte(253)
        emitU32(64889)
    }

    fun i16x8Add() {
        emitByte(253)
        emitU32(64892)
    }

    fun i16x8AddSatS() {
        emitByte(253)
        emitU32(64893)
    }

    fun i16x8AddSatU() {
        emitByte(253)
        emitU32(64894)
    }

    fun i16x8Sub() {
        emitByte(253)
        emitU32(64895)
    }

    fun i16x8SubSatS() {
        emitByte(253)
        emitU32(64896)
    }

    fun i16x8SubSatU() {
        emitByte(253)
        emitU32(64897)
    }

    fun i16x8Mul() {
        emitByte(253)
        emitU32(64901)
    }

    fun i32x4Add() {
        emitByte(253)
        emitU32(64910)
    }

    fun i32x4Sub() {
        emitByte(253)
        emitU32(64913)
    }

    fun i32x4Mul() {
        emitByte(253)
        emitU32(64917)
    }

    fun i64x2Add() {
        emitByte(253)
        emitU32(64974)
    }

    fun i64x2Sub() {
        emitByte(253)
        emitU32(64977)
    }

    fun i64x2Mul() {
        emitByte(253)
        emitU32(64981)
    }

    fun f32x4Add() {
        emitByte(253)
        emitU32(64992)
    }

    fun f32x4Sub() {
        emitByte(253)
        emitU32(64993)
    }

    fun f32x4Mul() {
        emitByte(253)
        emitU32(64994)
    }

    fun f32x4Div() {
        emitByte(253)
        emitU32(64995)
    }

    fun f64x2Add() {
        emitByte(253)
        emitU32(65008)
    }

    fun f64x2Sub() {
        emitByte(253)
        emitU32(65009)
    }

    fun f64x2Mul() {
        emitByte(253)
        emitU32(65010)
    }

    fun f64x2Div() {
        emitByte(253)
        emitU32(65011)
    }

    fun v128Not() {
        emitByte(253)
        emitU32(64845)
    }

    fun v128And() {
        emitByte(253)
        emitU32(64846)
    }

    fun v128AndNot() {
        emitByte(253)
        emitU32(64847)
    }

    fun v128Or() {
        emitByte(253)
        emitU32(64848)
    }

    fun v128Xor() {
        emitByte(253)
        emitU32(64849)
    }

    fun v128Bitselect() {
        emitByte(253)
        emitU32(64850)
    }

    fun v128AnyTrue() {
        emitByte(253)
        emitU32(64851)
    }

    fun v128Load8Lane(alignment: Int, offset: Int, laneIdx: Int) {
        emitByte(253)
        emitU32(64852)
        emitU32(alignment)
        emitU32(offset)
        emitU32(laneIdx)
    }

    fun v128Load16Lane(alignment: Int, offset: Int, laneIdx: Int) {
        emitByte(253)
        emitU32(64853)
        emitU32(alignment)
        emitU32(offset)
        emitU32(laneIdx)
    }

    fun v128Load32Lane(alignment: Int, offset: Int, laneIdx: Int) {
        emitByte(253)
        emitU32(64854)
        emitU32(alignment)
        emitU32(offset)
        emitU32(laneIdx)
    }

    fun v128Load64Lane(alignment: Int, offset: Int, laneIdx: Int) {
        emitByte(253)
        emitU32(64855)
        emitU32(alignment)
        emitU32(offset)
        emitU32(laneIdx)
    }

    fun v128Store8Lane(alignment: Int, offset: Int, laneIdx: Int) {
        emitByte(253)
        emitU32(64856)
        emitU32(alignment)
        emitU32(offset)
        emitU32(laneIdx)
    }

    fun v128Store16Lane(alignment: Int, offset: Int, laneIdx: Int) {
        emitByte(253)
        emitU32(64857)
        emitU32(alignment)
        emitU32(offset)
        emitU32(laneIdx)
    }

    fun v128Store32Lane(alignment: Int, offset: Int, laneIdx: Int) {
        emitByte(253)
        emitU32(64858)
        emitU32(alignment)
        emitU32(offset)
        emitU32(laneIdx)
    }

    fun v128Store64Lane(alignment: Int, offset: Int, laneIdx: Int) {
        emitByte(253)
        emitU32(64859)
        emitU32(alignment)
        emitU32(offset)
        emitU32(laneIdx)
    }

    fun v128Load32Zero(alignment: Int, offset: Int) {
        emitByte(253)
        emitU32(64860)
        emitU32(alignment)
        emitU32(offset)
    }

    fun v128Load64Zero(alignment: Int, offset: Int) {
        emitByte(253)
        emitU32(64861)
        emitU32(alignment)
        emitU32(offset)
    }

    fun returnCall(funcIdx: Int) {
        emitByte(18)
        emitU32(funcIdx)
    }

    fun returnCallIndirect(typeIdx: Int, tableIdx: Int) {
        emitByte(19)
        emitU32(typeIdx)
        emitU32(tableIdx)
    }

    fun try_(blockType: Int) {
        emitByte(6)
        emitU32(blockType)
    }

    fun catch_(tagIdx: Int) {
        emitByte(7)
        emitU32(tagIdx)
    }

    fun throw_(tagIdx: Int) {
        emitByte(8)
        emitU32(tagIdx)
    }

    fun rethrow(labelIdx: Int) {
        emitByte(9)
        emitU32(labelIdx)
    }

    fun delegate(labelIdx: Int) {
        emitByte(24)
        emitU32(labelIdx)
    }

    fun catchAll() {
        emitByte(25)
    }

    fun memoryAtomicNotify(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65024)
        emitU32(alignment)
        emitU32(offset)
    }

    fun memoryAtomicWait32(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65025)
        emitU32(alignment)
        emitU32(offset)
    }

    fun memoryAtomicWait64(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65026)
        emitU32(alignment)
        emitU32(offset)
    }

    fun atomicFence(byteVal: Int) {
        emitByte(254)
        emitU32(65027)
        emitU32(byteVal)
    }

    fun i32AtomicLoad(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65040)
        emitU32(alignment)
        emitU32(offset)
    }

    fun i64AtomicLoad(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65041)
        emitU32(alignment)
        emitU32(offset)
    }

    fun i32AtomicLoad8U(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65042)
        emitU32(alignment)
        emitU32(offset)
    }

    fun i32AtomicLoad16U(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65043)
        emitU32(alignment)
        emitU32(offset)
    }

    fun i64AtomicLoad8U(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65044)
        emitU32(alignment)
        emitU32(offset)
    }

    fun i64AtomicLoad16U(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65045)
        emitU32(alignment)
        emitU32(offset)
    }

    fun i64AtomicLoad32U(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65046)
        emitU32(alignment)
        emitU32(offset)
    }

    fun i32AtomicStore(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65047)
        emitU32(alignment)
        emitU32(offset)
    }

    fun i64AtomicStore(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65048)
        emitU32(alignment)
        emitU32(offset)
    }

    fun i32AtomicStore8(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65049)
        emitU32(alignment)
        emitU32(offset)
    }

    fun i32AtomicStore16(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65050)
        emitU32(alignment)
        emitU32(offset)
    }

    fun i64AtomicStore8(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65051)
        emitU32(alignment)
        emitU32(offset)
    }

    fun i64AtomicStore16(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65052)
        emitU32(alignment)
        emitU32(offset)
    }

    fun i64AtomicStore32(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65053)
        emitU32(alignment)
        emitU32(offset)
    }

    fun i32AtomicRmwAdd(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65054)
        emitU32(alignment)
        emitU32(offset)
    }

    fun i64AtomicRmwAdd(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65055)
        emitU32(alignment)
        emitU32(offset)
    }

    fun i32AtomicRmw8AddU(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65056)
        emitU32(alignment)
        emitU32(offset)
    }

    fun i32AtomicRmw16AddU(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65057)
        emitU32(alignment)
        emitU32(offset)
    }

    fun i64AtomicRmw8AddU(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65058)
        emitU32(alignment)
        emitU32(offset)
    }

    fun i64AtomicRmw16AddU(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65059)
        emitU32(alignment)
        emitU32(offset)
    }

    fun i64AtomicRmw32AddU(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65060)
        emitU32(alignment)
        emitU32(offset)
    }

    fun i32AtomicRmwSub(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65061)
        emitU32(alignment)
        emitU32(offset)
    }

    fun i64AtomicRmwSub(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65062)
        emitU32(alignment)
        emitU32(offset)
    }

    fun i32AtomicRmw8SubU(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65063)
        emitU32(alignment)
        emitU32(offset)
    }

    fun i32AtomicRmw16SubU(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65064)
        emitU32(alignment)
        emitU32(offset)
    }

    fun i64AtomicRmw8SubU(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65065)
        emitU32(alignment)
        emitU32(offset)
    }

    fun i64AtomicRmw16SubU(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65066)
        emitU32(alignment)
        emitU32(offset)
    }

    fun i64AtomicRmw32SubU(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65067)
        emitU32(alignment)
        emitU32(offset)
    }

    fun i32AtomicRmwAnd(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65068)
        emitU32(alignment)
        emitU32(offset)
    }

    fun i64AtomicRmwAnd(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65069)
        emitU32(alignment)
        emitU32(offset)
    }

    fun i32AtomicRmw8AndU(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65070)
        emitU32(alignment)
        emitU32(offset)
    }

    fun i32AtomicRmw16AndU(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65071)
        emitU32(alignment)
        emitU32(offset)
    }

    fun i64AtomicRmw8AndU(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65072)
        emitU32(alignment)
        emitU32(offset)
    }

    fun i64AtomicRmw16AndU(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65073)
        emitU32(alignment)
        emitU32(offset)
    }

    fun i64AtomicRmw32AndU(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65074)
        emitU32(alignment)
        emitU32(offset)
    }

    fun i32AtomicRmwOr(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65075)
        emitU32(alignment)
        emitU32(offset)
    }

    fun i64AtomicRmwOr(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65076)
        emitU32(alignment)
        emitU32(offset)
    }

    fun i32AtomicRmw8OrU(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65077)
        emitU32(alignment)
        emitU32(offset)
    }

    fun i32AtomicRmw16OrU(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65078)
        emitU32(alignment)
        emitU32(offset)
    }

    fun i64AtomicRmw8OrU(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65079)
        emitU32(alignment)
        emitU32(offset)
    }

    fun i64AtomicRmw16OrU(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65080)
        emitU32(alignment)
        emitU32(offset)
    }

    fun i64AtomicRmw32OrU(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65081)
        emitU32(alignment)
        emitU32(offset)
    }

    fun i32AtomicRmwXor(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65082)
        emitU32(alignment)
        emitU32(offset)
    }

    fun i64AtomicRmwXor(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65083)
        emitU32(alignment)
        emitU32(offset)
    }

    fun i32AtomicRmw8XorU(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65084)
        emitU32(alignment)
        emitU32(offset)
    }

    fun i32AtomicRmw16XorU(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65085)
        emitU32(alignment)
        emitU32(offset)
    }

    fun i64AtomicRmw8XorU(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65086)
        emitU32(alignment)
        emitU32(offset)
    }

    fun i64AtomicRmw16XorU(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65087)
        emitU32(alignment)
        emitU32(offset)
    }

    fun i64AtomicRmw32XorU(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65088)
        emitU32(alignment)
        emitU32(offset)
    }

    fun i32AtomicRmwXchg(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65089)
        emitU32(alignment)
        emitU32(offset)
    }

    fun i64AtomicRmwXchg(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65090)
        emitU32(alignment)
        emitU32(offset)
    }

    fun i32AtomicRmw8XchgU(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65091)
        emitU32(alignment)
        emitU32(offset)
    }

    fun i32AtomicRmw16XchgU(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65092)
        emitU32(alignment)
        emitU32(offset)
    }

    fun i64AtomicRmw8XchgU(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65093)
        emitU32(alignment)
        emitU32(offset)
    }

    fun i64AtomicRmw16XchgU(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65094)
        emitU32(alignment)
        emitU32(offset)
    }

    fun i64AtomicRmw32XchgU(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65095)
        emitU32(alignment)
        emitU32(offset)
    }

    fun i32AtomicRmwCmpxchg(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65096)
        emitU32(alignment)
        emitU32(offset)
    }

    fun i64AtomicRmwCmpxchg(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65097)
        emitU32(alignment)
        emitU32(offset)
    }

    fun i32AtomicRmw8CmpxchgU(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65098)
        emitU32(alignment)
        emitU32(offset)
    }

    fun i32AtomicRmw16CmpxchgU(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65099)
        emitU32(alignment)
        emitU32(offset)
    }

    fun i64AtomicRmw8CmpxchgU(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65100)
        emitU32(alignment)
        emitU32(offset)
    }

    fun i64AtomicRmw16CmpxchgU(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65101)
        emitU32(alignment)
        emitU32(offset)
    }

    fun i64AtomicRmw32CmpxchgU(alignment: Int, offset: Int) {
        emitByte(254)
        emitU32(65102)
        emitU32(alignment)
        emitU32(offset)
    }

    fun structNew(typeIdx: Int) {
        emitByte(251)
        emitU32(64256)
        emitU32(typeIdx)
    }

    fun structNewDefault(typeIdx: Int) {
        emitByte(251)
        emitU32(64257)
        emitU32(typeIdx)
    }

    fun structGet(typeIdx: Int, fieldIdx: Int) {
        emitByte(251)
        emitU32(64258)
        emitU32(typeIdx)
        emitU32(fieldIdx)
    }

    fun structGetS(typeIdx: Int, fieldIdx: Int) {
        emitByte(251)
        emitU32(64259)
        emitU32(typeIdx)
        emitU32(fieldIdx)
    }

    fun structGetU(typeIdx: Int, fieldIdx: Int) {
        emitByte(251)
        emitU32(64260)
        emitU32(typeIdx)
        emitU32(fieldIdx)
    }

    fun structSet(typeIdx: Int, fieldIdx: Int) {
        emitByte(251)
        emitU32(64261)
        emitU32(typeIdx)
        emitU32(fieldIdx)
    }

    fun arrayNew(typeIdx: Int) {
        emitByte(251)
        emitU32(64262)
        emitU32(typeIdx)
    }

    fun arrayNewDefault(typeIdx: Int) {
        emitByte(251)
        emitU32(64263)
        emitU32(typeIdx)
    }

    fun arrayNewFixed(typeIdx: Int, length: Int) {
        emitByte(251)
        emitU32(64264)
        emitU32(typeIdx)
        emitU32(length)
    }

    fun arrayNewData(typeIdx: Int, dataIdx: Int) {
        emitByte(251)
        emitU32(64265)
        emitU32(typeIdx)
        emitU32(dataIdx)
    }

    fun arrayNewElem(typeIdx: Int, elemIdx: Int) {
        emitByte(251)
        emitU32(64266)
        emitU32(typeIdx)
        emitU32(elemIdx)
    }

    fun arrayGet(typeIdx: Int) {
        emitByte(251)
        emitU32(64267)
        emitU32(typeIdx)
    }

    fun arrayGetS(typeIdx: Int) {
        emitByte(251)
        emitU32(64268)
        emitU32(typeIdx)
    }

    fun arrayGetU(typeIdx: Int) {
        emitByte(251)
        emitU32(64269)
        emitU32(typeIdx)
    }

    fun arraySet(typeIdx: Int) {
        emitByte(251)
        emitU32(64270)
        emitU32(typeIdx)
    }

    fun arrayLen() {
        emitByte(251)
        emitU32(64271)
    }

    fun arrayFill(typeIdx: Int) {
        emitByte(251)
        emitU32(64272)
        emitU32(typeIdx)
    }

    fun arrayCopy(destTypeIdx: Int, srcTypeIdx: Int) {
        emitByte(251)
        emitU32(64273)
        emitU32(destTypeIdx)
        emitU32(srcTypeIdx)
    }

    fun refTest(heapType: Int) {
        emitByte(251)
        emitU32(64276)
        emitU32(heapType)
    }

    fun refTestNull(heapType: Int) {
        emitByte(251)
        emitU32(64277)
        emitU32(heapType)
    }

    fun refCast(heapType: Int) {
        emitByte(251)
        emitU32(64278)
        emitU32(heapType)
    }

    fun refCastNull(heapType: Int) {
        emitByte(251)
        emitU32(64279)
        emitU32(heapType)
    }

    fun brOnCast(flags: Int, labelIdx: Int, srcType: Int, destType: Int) {
        emitByte(251)
        emitU32(64280)
        emitU32(flags)
        emitU32(labelIdx)
        emitU32(srcType)
        emitU32(destType)
    }

    fun brOnCastFail(flags: Int, labelIdx: Int, srcType: Int, destType: Int) {
        emitByte(251)
        emitU32(64281)
        emitU32(flags)
        emitU32(labelIdx)
        emitU32(srcType)
        emitU32(destType)
    }

    fun anyConvertExtern() {
        emitByte(251)
        emitU32(64282)
    }

    fun externConvertAny() {
        emitByte(251)
        emitU32(64283)
    }

    fun refI31() {
        emitByte(251)
        emitU32(64284)
    }

    fun i31GetS() {
        emitByte(251)
        emitU32(64285)
    }

    fun i31GetU() {
        emitByte(251)
        emitU32(64286)
    }

}
