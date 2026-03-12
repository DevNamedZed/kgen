package org.kgen.pass

import org.kgen.ir.*
import org.kgen.ir.target.Arch
import org.kgen.ir.target.Target

/**
 * Mixed-mode compiler — splits a module into native and managed partitions,
 * compiles each with the appropriate backend, and produces an artifact
 * containing both.
 *
 * Functions annotated with [FnAttribute.NATIVE] are compiled to machine code
 * via the x86/ARM64 backend. Functions annotated with [FnAttribute.MANAGED]
 * are compiled to JVM bytecode. Unannotated functions follow the default mode.
 *
 * The compiler generates JNI bridge stubs so managed code can call native
 * hot paths, and native code can call back into managed code via upcalls.
 *
 * ```java
 * var compiler = new MixedModeCompiler(Target.x86_64());
 * var result = compiler.partition(module);
 * // result.nativeModule  — functions for native compilation
 * // result.managedModule — functions for JVM bytecode
 * // result.bridges       — JNI bridge declarations
 * ```
 */
class MixedModeCompiler(
    private val nativeTarget: Target = Target.x86_64(),
    private val defaultMode: FnAttribute = FnAttribute.MANAGED,
) {

    /**
     * Result of partitioning a module into native and managed parts.
     */
    data class PartitionResult(
        /** Functions to compile as native machine code. */
        val nativeModule: Module,
        /** Functions to compile as JVM bytecode. */
        val managedModule: Module,
        /** JNI bridge stubs for cross-boundary calls. */
        val bridges: List<Bridge>,
        /** Statistics about the partition. */
        val stats: PartitionStats,
    )

    data class PartitionStats(
        val nativeFunctionCount: Int,
        val managedFunctionCount: Int,
        val bridgeCount: Int,
        val crossBoundaryCalls: Int,
    )

    /**
     * A bridge declaration for calling across the native/managed boundary.
     */
    data class Bridge(
        /** Name of the bridge function. */
        val name: String,
        /** The original function being bridged. */
        val targetFunction: String,
        /** Direction of the bridge. */
        val direction: BridgeDirection,
        /** Parameter types for the bridge. */
        val paramTypes: List<Type>,
        /** Return type. */
        val returnType: Type,
    )

    enum class BridgeDirection {
        /** Managed code calling into native (JNI downcall). */
        MANAGED_TO_NATIVE,
        /** Native code calling into managed (JNI upcall). */
        NATIVE_TO_MANAGED,
    }

    /**
     * Partition a module into native and managed modules.
     * Generates bridges for cross-boundary function calls.
     */
    @JvmOverloads
    fun partition(module: Module, className: String = "GeneratedClass"): PartitionResult {
        val nativeFunctions = mutableListOf<IrFunction>()
        val managedFunctions = mutableListOf<IrFunction>()

        for (fn in module.functions) {
            val mode = classifyFunction(fn)
            when (mode) {
                FnAttribute.NATIVE -> nativeFunctions.add(fn)
                FnAttribute.MANAGED -> managedFunctions.add(fn)
                else -> {}
            }
        }

        // Detect cross-boundary calls and generate bridges
        val nativeNames = nativeFunctions.map { it.name }.toSet()
        val managedNames = managedFunctions.map { it.name }.toSet()
        val bridges = mutableListOf<Bridge>()
        var crossCalls = 0

        for (fn in managedFunctions) {
            for (callee in extractCallees(fn)) {
                if (callee in nativeNames) {
                    val target = nativeFunctions.first { it.name == callee }
                    bridges.add(Bridge(
                        name = "jni_${callee}",
                        targetFunction = callee,
                        direction = BridgeDirection.MANAGED_TO_NATIVE,
                        paramTypes = target.params.map { it.type },
                        returnType = target.returnType,
                    ))
                    crossCalls++
                }
            }
        }

        for (fn in nativeFunctions) {
            for (callee in extractCallees(fn)) {
                if (callee in managedNames) {
                    val target = managedFunctions.first { it.name == callee }
                    bridges.add(Bridge(
                        name = "upcall_${callee}",
                        targetFunction = callee,
                        direction = BridgeDirection.NATIVE_TO_MANAGED,
                        paramTypes = target.params.map { it.type },
                        returnType = target.returnType,
                    ))
                    crossCalls++
                }
            }
        }

        // Generate bridge declarations in each module
        val nativeBridgeDecls = bridges
            .filter { it.direction == BridgeDirection.NATIVE_TO_MANAGED }
            .map { bridge ->
                IrFunction(
                    name = bridge.name,
                    params = bridge.paramTypes.mapIndexed { i, t -> Parameter("p$i", t, i) },
                    returnType = bridge.returnType,
                    blocks = emptyList(),
                    isExternal = true,
                )
            }

        val managedBridgeDecls = bridges
            .filter { it.direction == BridgeDirection.MANAGED_TO_NATIVE }
            .map { bridge ->
                IrFunction(
                    name = bridge.name,
                    params = bridge.paramTypes.mapIndexed { i, t -> Parameter("p$i", t, i) },
                    returnType = bridge.returnType,
                    blocks = emptyList(),
                    isExternal = true,
                    attributes = setOf(FnAttribute.NATIVE),
                )
            }

        val nativeModule = module.copy(
            name = "${module.name}_native",
            functions = nativeFunctions + nativeBridgeDecls,
        )

        val managedModule = module.copy(
            name = "${module.name}_managed",
            functions = managedFunctions + managedBridgeDecls,
        )

        return PartitionResult(
            nativeModule = nativeModule,
            managedModule = managedModule,
            bridges = bridges,
            stats = PartitionStats(
                nativeFunctionCount = nativeFunctions.size,
                managedFunctionCount = managedFunctions.size,
                bridgeCount = bridges.size,
                crossBoundaryCalls = crossCalls,
            ),
        )
    }

    /**
     * Classify a function as native or managed based on its attributes.
     */
    fun classifyFunction(fn: IrFunction): FnAttribute {
        if (FnAttribute.NATIVE in fn.attributes) return FnAttribute.NATIVE
        if (FnAttribute.MANAGED in fn.attributes) return FnAttribute.MANAGED
        return defaultMode
    }

    /**
     * Generate a JNI-compatible native method descriptor for a bridge.
     */
    fun jniDescriptor(bridge: Bridge): String {
        val paramDesc = bridge.paramTypes.joinToString("") { jniTypeDescriptor(it) }
        val retDesc = jniTypeDescriptor(bridge.returnType)
        return "($paramDesc)$retDesc"
    }

    /**
     * Generate a JNI method name following the standard mangling convention.
     */
    fun jniMethodName(className: String, methodName: String): String {
        val escapedClass = className.replace('.', '_').replace('/', '_')
        return "Java_${escapedClass}_$methodName"
    }

    private fun jniTypeDescriptor(type: Type): String = when (type) {
        Type.I1 -> "Z"
        Type.I8 -> "B"
        Type.I16 -> "S"
        Type.I32 -> "I"
        Type.I64 -> "J"
        Type.F32 -> "F"
        Type.F64 -> "D"
        Type.Void -> "V"
        is Type.Pointer -> "J" // pointers map to long in JNI
        is Type.Reference -> "Ljava/lang/Object;"
        else -> "J" // default to long for unknown types
    }

    private fun extractCallees(fn: IrFunction): Set<String> {
        val callees = mutableSetOf<String>()
        for (block in fn.blocks) {
            for (inst in block.instructions) {
                when (inst) {
                    is Instruction.Call -> {
                        val target = inst.function
                        if (target is GlobalRef || target is FunctionRef) {
                            callees.add(target.name)
                        }
                    }
                    else -> {}
                }
            }
        }
        return callees
    }

    companion object {
        /**
         * Check if a module has any mixed-mode functions.
         */
        @JvmStatic
        fun hasMixedMode(module: Module): Boolean {
            val hasNative = module.functions.any { FnAttribute.NATIVE in it.attributes }
            val hasManaged = module.functions.any { FnAttribute.MANAGED in it.attributes }
            return hasNative && hasManaged
        }

        /**
         * Count functions by mode in a module.
         */
        @JvmStatic
        fun countByMode(module: Module): Map<FnAttribute, Int> {
            val counts = mutableMapOf<FnAttribute, Int>()
            for (fn in module.functions) {
                when {
                    FnAttribute.NATIVE in fn.attributes -> counts[FnAttribute.NATIVE] = (counts[FnAttribute.NATIVE] ?: 0) + 1
                    FnAttribute.MANAGED in fn.attributes -> counts[FnAttribute.MANAGED] = (counts[FnAttribute.MANAGED] ?: 0) + 1
                }
            }
            return counts
        }
    }
}
