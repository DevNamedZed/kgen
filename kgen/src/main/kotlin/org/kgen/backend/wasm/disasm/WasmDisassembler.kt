package org.kgen.backend.wasm.disasm

import org.kgen.backend.wasm.*
import org.kgen.backend.wasm.module.WasmModule
import org.kgen.backend.wasm.module.WasmModuleReader
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Decodes WebAssembly bytecode into structured [WasmInstruction] sequences.
 *
 * Supports all MVP opcodes plus prefixed opcodes (0xFC, 0xFD, 0xFE, 0xFB).
 *
 * ```java
 * WasmModule module = WasmModuleReader.read(bytes);
 * WasmDisassembler disasm = new WasmDisassembler();
 * for (WasmModule.Function fn : module.getFunctions()) {
 *     List<WasmInstruction> instructions = disasm.disassemble(fn.getBody());
 *     for (WasmInstruction inst : instructions) {
 *         System.out.println(inst.text());
 *     }
 * }
 * ```
 */
class WasmDisassembler {

    fun disassemble(body: ByteArray): List<WasmInstruction> {
        val buf = ByteBuffer.wrap(body).order(ByteOrder.LITTLE_ENDIAN)
        val instructions = mutableListOf<WasmInstruction>()

        while (buf.hasRemaining()) {
            val offset = buf.position()
            val b = buf.get().toInt() and 0xFF

            val opcode = when (b) {
                0xFB -> {
                    val sub = WasmModuleReader.readU32(buf)
                    val key = (0xFB shl 8) or sub
                    WasmDisassemblerTable.prefixFBMap[key]
                        ?: error("Unknown GC opcode: 0xFB 0x${sub.toString(16)} at offset $offset")
                }
                0xFC -> {
                    val sub = WasmModuleReader.readU32(buf)
                    val key = (0xFC shl 8) or sub
                    WasmDisassemblerTable.prefixFCMap[key]
                        ?: error("Unknown 0xFC opcode: 0xFC $sub at offset $offset")
                }
                0xFD -> {
                    val sub = WasmModuleReader.readU32(buf)
                    val key = (0xFD shl 8) or sub
                    WasmDisassemblerTable.prefixFDMap[key]
                        ?: error("Unknown SIMD opcode: 0xFD $sub at offset $offset")
                }
                0xFE -> {
                    val sub = WasmModuleReader.readU32(buf)
                    val key = (0xFE shl 8) or sub
                    WasmDisassemblerTable.prefixFEMap[key]
                        ?: error("Unknown atomic opcode: 0xFE $sub at offset $offset")
                }
                else -> {
                    WasmDisassemblerTable.opcodeMap[b]
                        ?: error("Unknown opcode: 0x${b.toString(16)} at offset $offset")
                }
            }

            val operands = readOperands(opcode, buf)
            instructions.add(WasmInstruction(offset, opcode, operands))
        }

        return instructions
    }

    /**
     * Disassemble a single function from a parsed module, with context for name resolution.
     */
    fun disassemble(function: WasmModule.Function): List<WasmInstruction> = disassemble(function.body)

    private fun readOperands(opcode: WasmOpCode, buf: ByteBuffer): WasmInstruction.Operands {
        val imm = opcode.immediate ?: return WasmInstruction.Operands.None

        return when (imm) {
            WasmImmediate.BlockType -> {
                val type = WasmModuleReader.readS32(buf)
                WasmInstruction.Operands.BlockType(type)
            }

            WasmImmediate.LabelIdx,
            WasmImmediate.FuncIdx,
            WasmImmediate.LocalIdx,
            WasmImmediate.GlobalIdx,
            WasmImmediate.TableIdx,
            WasmImmediate.MemIdx,
            WasmImmediate.TagIdx,
            WasmImmediate.TypeIdx,
            WasmImmediate.DataIdx,
            WasmImmediate.ElemIdx,
            WasmImmediate.FieldIdx -> {
                WasmInstruction.Operands.Index(WasmModuleReader.readU32(buf))
            }

            WasmImmediate.I32 -> WasmInstruction.Operands.I32(WasmModuleReader.readS32(buf))
            WasmImmediate.I64 -> WasmInstruction.Operands.I64(WasmModuleReader.readS64(buf))

            WasmImmediate.F32 -> WasmInstruction.Operands.F32(Float.fromBits(buf.int))
            WasmImmediate.F64 -> WasmInstruction.Operands.F64(Double.fromBits(buf.long))

            WasmImmediate.V128 -> {
                val bytes = ByteArray(16)
                buf.get(bytes)
                WasmInstruction.Operands.V128(bytes)
            }

            WasmImmediate.MemArg -> {
                val align = WasmModuleReader.readU32(buf)
                val offset = WasmModuleReader.readU32(buf)
                WasmInstruction.Operands.MemArg(align, offset)
            }

            WasmImmediate.MemArgLane -> {
                val align = WasmModuleReader.readU32(buf)
                val offset = WasmModuleReader.readU32(buf)
                buf.get() // lane index byte
                WasmInstruction.Operands.MemArg(align, offset)
            }

            WasmImmediate.LaneIdx, WasmImmediate.LaneIdx16 -> {
                WasmInstruction.Operands.Index(buf.get().toInt() and 0xFF)
            }

            WasmImmediate.Byte -> {
                WasmInstruction.Operands.Index(buf.get().toInt() and 0xFF)
            }

            WasmImmediate.BrTable -> {
                val count = WasmModuleReader.readU32(buf)
                val labels = (0 until count).map { WasmModuleReader.readU32(buf) }
                val default = WasmModuleReader.readU32(buf)
                WasmInstruction.Operands.BrTable(labels, default)
            }

            WasmImmediate.CallIndirect -> {
                val typeIndex = WasmModuleReader.readU32(buf)
                val tableIndex = WasmModuleReader.readU32(buf)
                WasmInstruction.Operands.CallIndirect(typeIndex, tableIndex)
            }

            WasmImmediate.RefType, WasmImmediate.HeapType -> {
                WasmInstruction.Operands.RefType(WasmModuleReader.readS32(buf))
            }

            WasmImmediate.ValTypes -> {
                val count = WasmModuleReader.readU32(buf)
                val types = (0 until count).map { WasmModuleReader.readS32(buf) }
                WasmInstruction.Operands.ValTypes(types)
            }

            WasmImmediate.DataMemIdx,
            WasmImmediate.MemMemIdx,
            WasmImmediate.ElemTableIdx,
            WasmImmediate.TableTableIdx,
            WasmImmediate.TypeDataIdx,
            WasmImmediate.TypeElemIdx,
            WasmImmediate.TypeTypeIdx -> {
                val first = WasmModuleReader.readU32(buf)
                val second = WasmModuleReader.readU32(buf)
                WasmInstruction.Operands.TwoIndex(first, second)
            }

            WasmImmediate.TypeIdxLen -> {
                val typeIdx = WasmModuleReader.readU32(buf)
                val len = WasmModuleReader.readU32(buf)
                WasmInstruction.Operands.TwoIndex(typeIdx, len)
            }

            WasmImmediate.BrCast -> {
                val flags = WasmModuleReader.readU32(buf)
                val label = WasmModuleReader.readU32(buf)
                WasmInstruction.Operands.TwoIndex(flags, label)
            }
        }
    }
}
