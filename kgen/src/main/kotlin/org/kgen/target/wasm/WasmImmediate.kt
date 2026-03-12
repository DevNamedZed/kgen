package org.kgen.target.wasm

enum class WasmImmediate {
    BlockType, LabelIdx, FuncIdx, LocalIdx, GlobalIdx, TableIdx, MemIdx, TagIdx, TypeIdx,
    DataIdx, ElemIdx, RefType, HeapType,
    I32, I64, F32, F64, V128, Byte,
    MemArg, MemArgLane, LaneIdx, LaneIdx16, ValTypes,
    BrTable, CallIndirect,
    DataMemIdx, MemMemIdx, ElemTableIdx, TableTableIdx,
    FieldIdx, TypeIdxLen, TypeDataIdx, TypeElemIdx, TypeTypeIdx,
    BrCast,
}
