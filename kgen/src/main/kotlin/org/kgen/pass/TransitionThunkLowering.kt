package org.kgen.pass

import org.kgen.ir.*

/**
 * Lowers [Instruction.ManagedCall] instructions into a sequence that handles
 * managed/native boundary transitions.
 *
 * For managed-to-native transitions:
 * 1. GCSafepoint (so GC knows we're leaving managed code)
 * 2. Save managed state (GC roots on shadow stack)
 * 3. Call the target function
 * 4. Restore managed state
 *
 * For native-to-managed transitions:
 * 1. Attach to managed runtime (if thread is not yet attached)
 * 2. Call the target function
 * 3. GCSafepoint (re-enter managed mode)
 *
 * The actual runtime calls (attach/detach) are emitted as calls to well-known
 * runtime function names that the runtime linker resolves.
 */
class TransitionThunkLowering : ModulePass {

    override fun run(module: Module): Module {
        return module.copy(functions = module.functions.map { fn ->
            if (fn.isExternal) fn else lowerTransitions(fn)
        })
    }

    private fun lowerTransitions(fn: IrFunction): IrFunction {
        var counter = 0
        val newBlocks = fn.blocks.map { block ->
            val newInstructions = mutableListOf<Instruction>()
            for (inst in block.instructions) {
                if (inst is Instruction.ManagedCall) {
                    counter++
                    lowerManagedCall(inst, newInstructions, counter)
                } else {
                    newInstructions.add(inst)
                }
            }
            BasicBlock(block.label, newInstructions)
        }
        return fn.copy(blocks = newBlocks)
    }

    private fun lowerManagedCall(
        mc: Instruction.ManagedCall,
        out: MutableList<Instruction>,
        id: Int,
    ) {
        when (mc.direction) {
            ManagedCallDirection.MANAGED_TO_NATIVE -> {
                // Safepoint before leaving managed code
                out.add(Instruction.GCSafepoint())
                // Notify runtime we're entering native code
                out.add(Instruction.Call(
                    null,
                    GlobalRef(RT_LEAVE_MANAGED, Type.Function(emptyList(), Type.Void)),
                    emptyList(),
                    Type.Void,
                ))
                // Actual call
                out.add(Instruction.Call(mc.dest, mc.function, mc.args, mc.returnType))
                // Notify runtime we're back in managed code
                out.add(Instruction.Call(
                    null,
                    GlobalRef(RT_ENTER_MANAGED, Type.Function(emptyList(), Type.Void)),
                    emptyList(),
                    Type.Void,
                ))
            }
            ManagedCallDirection.NATIVE_TO_MANAGED -> {
                // Notify runtime we're entering managed code
                out.add(Instruction.Call(
                    null,
                    GlobalRef(RT_ENTER_MANAGED, Type.Function(emptyList(), Type.Void)),
                    emptyList(),
                    Type.Void,
                ))
                // Actual call
                out.add(Instruction.Call(mc.dest, mc.function, mc.args, mc.returnType))
                // Safepoint on return to managed code
                out.add(Instruction.GCSafepoint())
                // Notify runtime we're leaving managed code
                out.add(Instruction.Call(
                    null,
                    GlobalRef(RT_LEAVE_MANAGED, Type.Function(emptyList(), Type.Void)),
                    emptyList(),
                    Type.Void,
                ))
            }
        }
    }

    companion object {
        const val RT_ENTER_MANAGED = "__kgen_rt_enter_managed"
        const val RT_LEAVE_MANAGED = "__kgen_rt_leave_managed"
    }
}
