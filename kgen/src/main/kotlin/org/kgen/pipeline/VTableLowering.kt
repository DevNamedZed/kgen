package org.kgen.pipeline

import org.kgen.ir.*
import org.kgen.ir.instructions.*
import org.kgen.ir.types.*

/**
 * Lowers remaining `VirtualCall` and `InterfaceCall` instructions to
 * indirect calls through vtable/itable pointers.
 *
 * Should run after [Devirtualization] — this pass handles calls that
 * could not be devirtualized statically.
 *
 * Object layout assumed:
 * ```
 * offset 0: vtable pointer (pointer to array of function pointers)
 * offset 8: type ID (i32)
 * offset 12: GC flags (i32)
 * offset 16+: fields
 * ```
 *
 * VTable layout:
 * ```
 * slot 0: method pointer for first virtual method
 * slot 1: method pointer for second virtual method
 * ...
 * ```
 *
 * For interface dispatch, the vtable pointer at object offset 0 points
 * to the class vtable, which also includes interface method slots.
 * The slot assignments are determined by the module's class definitions.
 *
 * ```java
 * var pass = new VTableLowering();
 * var lowered = pass.run(module);
 * ```
 */
class VTableLowering : PipelineStage {

    override fun run(module: Module): Module {
        val slotMap = buildSlotMap(module)
        var changed = false

        val newFunctions = module.functions.map { fn ->
            if (fn.isExternal) fn
            else {
                val result = lowerFunction(fn, slotMap)
                if (result !== fn) changed = true
                result
            }
        }

        return if (changed) module.copy(functions = newFunctions) else module
    }

    private fun lowerFunction(fn: IrFunction, slotMap: Map<String, Int>): IrFunction {
        var anyChanged = false
        var tempCounter = 0

        val newBlocks = fn.blocks.map { block ->
            val newInstructions = mutableListOf<Instruction>()
            for (inst in block.instructions) {
                when (inst) {
                    is VirtualCall -> {
                        val key = "${inst.className}.${inst.methodName}"
                        val slot = slotMap[key]
                        if (slot != null) {
                            newInstructions.addAll(
                                lowerVirtualCall(inst, slot, tempCounter++)
                            )
                        } else {
                            // Fallback: use mangled direct call
                            val mangledName = "${inst.className.replace('/', '_')}_${inst.methodName}"
                            newInstructions.add(Call(
                                dest = inst.dest,
                                function = FunctionRef(mangledName, inst.methodType),
                                args = listOf(inst.obj) + inst.args,
                                returnType = inst.methodType.ret,
                            ))
                        }
                        anyChanged = true
                    }
                    is InterfaceCall -> {
                        val key = "${inst.interfaceName}.${inst.methodName}"
                        val slot = slotMap[key]
                        if (slot != null) {
                            newInstructions.addAll(
                                lowerInterfaceCall(inst, slot, tempCounter++)
                            )
                        } else {
                            val mangledName = "${inst.interfaceName.replace('/', '_')}_${inst.methodName}"
                            newInstructions.add(Call(
                                dest = inst.dest,
                                function = FunctionRef(mangledName, inst.methodType),
                                args = listOf(inst.obj) + inst.args,
                                returnType = inst.methodType.ret,
                            ))
                        }
                        anyChanged = true
                    }
                    else -> newInstructions.add(inst)
                }
            }
            BasicBlock(block.label, newInstructions)
        }

        return if (anyChanged) fn.copy(blocks = newBlocks) else fn
    }

    /**
     * Lower a VirtualCall to:
     *   %vtable = load ptr, %obj          ; load vtable pointer from object
     *   %slot   = gep ptr, %vtable, slot  ; index into vtable
     *   %fptr   = load ptr, %slot         ; load function pointer
     *   %result = call %fptr(obj, args)   ; indirect call
     */
    private fun lowerVirtualCall(inst: VirtualCall, slot: Int, id: Int): List<Instruction> {
        val instructions = mutableListOf<Instruction>()

        val vtableRef = InstructionRef(".vtable.$id", Type.OpaquePointer)
        instructions.add(Load(vtableRef, inst.obj, Type.OpaquePointer))

        val slotRef = InstructionRef(".vslot.$id", Type.OpaquePointer)
        instructions.add(GetElementPtr(
            slotRef, Type.I8, vtableRef,
            listOf(Constant.I64(slot.toLong() * 8))
        ))

        val fptrRef = InstructionRef(".vfptr.$id", Type.OpaquePointer)
        instructions.add(Load(fptrRef, slotRef, Type.OpaquePointer))

        instructions.add(Call(
            dest = inst.dest,
            function = fptrRef,
            args = listOf(inst.obj) + inst.args,
            returnType = inst.methodType.ret,
        ))

        return instructions
    }

    private fun lowerInterfaceCall(inst: InterfaceCall, slot: Int, id: Int): List<Instruction> {
        // Same strategy as virtual call — interface methods are in the same vtable
        val instructions = mutableListOf<Instruction>()

        val vtableRef = InstructionRef(".itable.$id", Type.OpaquePointer)
        instructions.add(Load(vtableRef, inst.obj, Type.OpaquePointer))

        val slotRef = InstructionRef(".islot.$id", Type.OpaquePointer)
        instructions.add(GetElementPtr(
            slotRef, Type.I8, vtableRef,
            listOf(Constant.I64(slot.toLong() * 8))
        ))

        val fptrRef = InstructionRef(".ifptr.$id", Type.OpaquePointer)
        instructions.add(Load(fptrRef, slotRef, Type.OpaquePointer))

        instructions.add(Call(
            dest = inst.dest,
            function = fptrRef,
            args = listOf(inst.obj) + inst.args,
            returnType = inst.methodType.ret,
        ))

        return instructions
    }

    /**
     * Build a mapping from "ClassName.methodName" to vtable slot index.
     * Slots are assigned sequentially per class, starting from inherited methods.
     */
    private fun buildSlotMap(module: Module): Map<String, Int> {
        val slotMap = mutableMapOf<String, Int>()

        for (cls in module.classes) {
            var slot = 0

            // Inherit parent slots
            val parent = cls.superClass
            if (parent != null) {
                val parentEntries = slotMap.entries.filter { it.key.startsWith("$parent.") }
                for ((key, parentSlot) in parentEntries) {
                    val methodName = key.substringAfterLast('.')
                    slotMap["${cls.name}.$methodName"] = parentSlot
                    slot = maxOf(slot, parentSlot + 1)
                }
            }

            // Add new virtual methods defined in this class
            for (method in cls.methods) {
                val key = "${cls.name}.${method.name}"
                if (key !in slotMap) {
                    slotMap[key] = slot++
                }
            }
        }

        // Add interface methods
        for (iface in module.interfaces) {
            var slot = slotMap.values.maxOrNull()?.let { it + 1 } ?: 0
            for (method in iface.methods) {
                val key = "${iface.name}.${method.name}"
                if (key !in slotMap) {
                    slotMap[key] = slot++
                }
            }
        }

        return slotMap
    }
}
