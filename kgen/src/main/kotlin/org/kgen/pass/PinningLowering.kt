package org.kgen.pass

import org.kgen.ir.*
import org.kgen.ir.instructions.*

/**
 * Lowers [Pin] and [Unpin] instructions to
 * platform-specific GC runtime calls.
 *
 * For moving GCs (statepoint, shadow-stack):
 * - Pin → call `__kgen_rt_pin(ref)` which returns a raw pointer
 * - Unpin → call `__kgen_rt_unpin(ref)` to release the pin
 *
 * For non-moving GCs or no GC:
 * - Pin → identity (the reference IS the pointer, no movement)
 * - Unpin → no-op (removed entirely)
 *
 * Also lowers [WriteBarrier] and [ReadBarrier]:
 * - WriteBarrier → call `__kgen_rt_write_barrier(obj, fieldIndex, value)`
 * - ReadBarrier → call `__kgen_rt_read_barrier(ref)` or identity for non-relocating GCs
 */
class PinningLowering(private val movingGC: Boolean = true) : ModulePass {

    override fun run(module: Module): Module {
        return module.copy(functions = module.functions.map { fn ->
            if (fn.isExternal) fn else lowerPinning(fn)
        })
    }

    private fun lowerPinning(fn: IrFunction): IrFunction {
        val newBlocks = fn.blocks.map { block ->
            val newInstructions = mutableListOf<Instruction>()
            for (inst in block.instructions) {
                when (inst) {
                    is Pin -> lowerPin(inst, newInstructions)
                    is Unpin -> lowerUnpin(inst, newInstructions)
                    is WriteBarrier -> lowerWriteBarrier(inst, newInstructions)
                    is ReadBarrier -> lowerReadBarrier(inst, newInstructions)
                    else -> newInstructions.add(inst)
                }
            }
            BasicBlock(block.label, newInstructions)
        }
        return fn.copy(blocks = newBlocks)
    }

    private fun lowerPin(pin: Pin, out: MutableList<Instruction>) {
        if (movingGC) {
            // Call runtime to pin the object
            out.add(Call(
                pin.dest,
                GlobalRef(RT_PIN, Type.Function(listOf(pin.ref.type), pin.dest.type)),
                listOf(pin.ref),
                pin.dest.type,
            ))
        } else {
            // Non-moving GC: pin is a bitcast/identity
            out.add(BitCast(pin.dest, pin.ref, pin.dest.type))
        }
    }

    private fun lowerUnpin(unpin: Unpin, out: MutableList<Instruction>) {
        if (movingGC) {
            out.add(Call(
                null,
                GlobalRef(RT_UNPIN, Type.Function(listOf(unpin.ref.type), Type.Void)),
                listOf(unpin.ref),
                Type.Void,
            ))
        }
        // Non-moving GC: unpin is a no-op, emit nothing
    }

    private fun lowerWriteBarrier(wb: WriteBarrier, out: MutableList<Instruction>) {
        out.add(Call(
            null,
            GlobalRef(RT_WRITE_BARRIER, Type.Function(
                listOf(wb.obj.type, Type.I32, wb.value.type), Type.Void
            )),
            listOf(wb.obj, wb.fieldIndex, wb.value),
            Type.Void,
        ))
    }

    private fun lowerReadBarrier(rb: ReadBarrier, out: MutableList<Instruction>) {
        if (movingGC) {
            out.add(Call(
                rb.dest,
                GlobalRef(RT_READ_BARRIER, Type.Function(listOf(rb.ref.type), rb.dest.type)),
                listOf(rb.ref),
                rb.dest.type,
            ))
        } else {
            // Non-relocating GC: read barrier is identity
            out.add(BitCast(rb.dest, rb.ref, rb.dest.type))
        }
    }

    companion object {
        const val RT_PIN = "__kgen_rt_pin"
        const val RT_UNPIN = "__kgen_rt_unpin"
        const val RT_WRITE_BARRIER = "__kgen_rt_write_barrier"
        const val RT_READ_BARRIER = "__kgen_rt_read_barrier"
    }
}
