package org.kgen.pipeline

import org.kgen.ir.*
import org.kgen.ir.instructions.*
import org.kgen.ir.types.*

/**
 * Devirtualization pass — replaces virtual/interface calls with direct calls
 * when the concrete receiver type can be statically determined.
 *
 * Strategies:
 * - **Class Hierarchy Analysis (CHA)**: If a virtual method has only one implementation
 *   in the entire module's class hierarchy, replace with a direct call.
 * - **Type propagation**: If the receiver was created by a `New` instruction
 *   in the same function, the concrete type is known.
 * - **Final methods**: Calls to methods in final classes or final methods
 *   can always be devirtualized.
 *
 * ```java
 * // Before: vcall obj.toString() — dispatches via vtable
 * // After:  call MyClass.toString(obj) — direct call (if MyClass is the only impl)
 *
 * var devirt = new Devirtualization();
 * var optimized = devirt.run(module);
 * ```
 */
class Devirtualization : PipelineStage {

    override fun run(module: Module): Module {
        val hierarchy = buildClassHierarchy(module)
        var changed = false

        val newFunctions = module.functions.map { fn ->
            if (fn.isExternal) fn
            else {
                val result = devirtualizeFunction(fn, hierarchy)
                if (result !== fn) changed = true
                result
            }
        }

        return if (changed) module.copy(functions = newFunctions) else module
    }

    private fun devirtualizeFunction(fn: IrFunction, hierarchy: ClassHierarchy): IrFunction {
        val typeInfo = analyzeReceiverTypes(fn)
        var anyChanged = false

        val newBlocks = fn.blocks.map { block ->
            val newInstructions = mutableListOf<Instruction>()
            for (inst in block.instructions) {
                val devirtualized = tryDevirtualize(inst, hierarchy, typeInfo)
                if (devirtualized != null) {
                    newInstructions.add(devirtualized)
                    anyChanged = true
                } else {
                    newInstructions.add(inst)
                }
            }
            BasicBlock(block.label, newInstructions)
        }

        return if (anyChanged) fn.copy(blocks = newBlocks) else fn
    }

    private fun tryDevirtualize(
        inst: Instruction,
        hierarchy: ClassHierarchy,
        typeInfo: Map<String, String>,
    ): Instruction? {
        return when (inst) {
            is VirtualCall -> {
                val concreteType = resolveConcreteType(inst.obj, typeInfo)
                    ?: hierarchy.singleImplementor(inst.className, inst.methodName)

                if (concreteType != null) {
                    val targetName = "$concreteType.${inst.methodName}"
                    Call(
                        dest = inst.dest,
                        function = FunctionRef(targetName, inst.methodType),
                        args = listOf(inst.obj) + inst.args,
                        returnType = inst.methodType.ret,
                    )
                } else null
            }

            is InterfaceCall -> {
                val concreteType = resolveConcreteType(inst.obj, typeInfo)
                    ?: hierarchy.singleInterfaceImplementor(inst.interfaceName, inst.methodName)

                if (concreteType != null) {
                    val targetName = "$concreteType.${inst.methodName}"
                    Call(
                        dest = inst.dest,
                        function = FunctionRef(targetName, inst.methodType),
                        args = listOf(inst.obj) + inst.args,
                        returnType = inst.methodType.ret,
                    )
                } else null
            }

            else -> null
        }
    }

    /**
     * Try to determine the concrete type of a receiver value.
     * If the value was produced by a New instruction, we know the exact type.
     */
    private fun resolveConcreteType(obj: Value, typeInfo: Map<String, String>): String? {
        return typeInfo[obj.name]
    }

    /**
     * Analyze a function to find receiver values with known concrete types.
     * Maps value name → concrete class name.
     */
    private fun analyzeReceiverTypes(fn: IrFunction): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for (block in fn.blocks) {
            for (inst in block.instructions) {
                if (inst is GCAlloc && inst.allocType is Type.ClassRef) {
                    result[inst.dest.name] = (inst.allocType as Type.ClassRef).name
                }
            }
        }
        return result
    }

    /**
     * Build a class hierarchy from the module's class definitions.
     */
    private fun buildClassHierarchy(module: Module): ClassHierarchy {
        return ClassHierarchy(module.classes, module.interfaces, module.functions)
    }

    /**
     * Class hierarchy for devirtualization analysis.
     */
    class ClassHierarchy(
        classes: List<ClassDefinition>,
        interfaces: List<InterfaceDefinition>,
        functions: List<IrFunction>,
    ) {
        private val subclasses = mutableMapOf<String, MutableSet<String>>()
        private val implementors = mutableMapOf<String, MutableSet<String>>()
        private val finalClasses = mutableSetOf<String>()
        private val methodImplementations = mutableMapOf<String, MutableSet<String>>()

        init {
            for (cls in classes) {
                if (cls.isFinal) finalClasses.add(cls.name)
                val parent = cls.superClass
                if (parent != null) {
                    subclasses.getOrPut(parent) { mutableSetOf() }.add(cls.name)
                }
                for (iface in cls.interfaces) {
                    implementors.getOrPut(iface) { mutableSetOf() }.add(cls.name)
                }
            }

            // Track which classes implement which methods
            for (fn in functions) {
                if (fn.name.contains('.')) {
                    val className = fn.name.substringBeforeLast('.')
                    val methodName = fn.name.substringAfterLast('.')
                    methodImplementations
                        .getOrPut("$className.$methodName") { mutableSetOf() }
                        .add(className)
                }
            }
        }

        /**
         * If a virtual method on [className] has exactly one implementation
         * across the entire hierarchy, return that implementing class name.
         */
        fun singleImplementor(className: String, methodName: String): String? {
            if (className in finalClasses) return className

            val allSubclasses = getAllSubclasses(className)
            val allClasses = allSubclasses + className

            val implementingClasses = allClasses.filter { cls ->
                methodImplementations.containsKey("$cls.$methodName")
            }

            return if (implementingClasses.size == 1) implementingClasses[0] else null
        }

        /**
         * If an interface method has exactly one implementing class, return it.
         */
        fun singleInterfaceImplementor(interfaceName: String, methodName: String): String? {
            val impls = implementors[interfaceName] ?: return null
            if (impls.size == 1) return impls.first()
            return null
        }

        private fun getAllSubclasses(className: String): Set<String> {
            val result = mutableSetOf<String>()
            val queue = ArrayDeque<String>()
            queue.addAll(subclasses[className] ?: emptySet())
            while (queue.isNotEmpty()) {
                val cls = queue.removeFirst()
                if (result.add(cls)) {
                    queue.addAll(subclasses[cls] ?: emptySet())
                }
            }
            return result
        }
    }
}
