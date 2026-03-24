package org.wark.compile

import org.kgen.ir.*
import org.kgen.ir.build.ModuleBuilder
import org.kgen.ir.target.Target
import org.kgen.target.wasm.WasmOpCode
import org.kgen.target.wasm.WasmValueType
import org.kgen.target.wasm.disasm.WasmDisassembler
import org.kgen.target.wasm.disasm.WasmInstruction
import org.kgen.target.wasm.module.WasmModule
import org.wark.compile.translate.ArithmeticTranslator
import org.wark.compile.translate.BlockTranslator
import org.wark.compile.translate.ControlFlowTranslator
import org.wark.compile.translate.ControlStack
import org.wark.compile.translate.GlobalTranslator
import org.wark.compile.translate.InstructionTranslator
import org.wark.compile.translate.MemoryTranslator
import org.wark.compile.translate.VariableTranslator

/**
 * Compiles WASM functions to kgen IR modules for JIT execution.
 *
 * Translates WASM's stack machine to SSA-form IR:
 * - WASM value stack → SSA virtual registers
 * - WASM locals → alloca + load/store (promoted by Mem2Reg)
 * - WASM memory access → base pointer + offset loads/stores
 *
 * ```kotlin
 * val compiler = WasmToIrCompiler(target, wasmModule)
 * val irModule = compiler.compileFunction(functionIndex)
 * ```
 */
class WasmToIrCompiler(
    private val target: Target,
    private val wasmModule: WasmModule,
) {
    private val translators: List<InstructionTranslator> = listOf(
        VariableTranslator(),
        ArithmeticTranslator(),
        MemoryTranslator(),
        GlobalTranslator(),
        BlockTranslator(),
        ControlFlowTranslator(wasmModule),
    )

    fun compileFunction(functionIndex: Int, functionName: String? = null): Module {
        val function = wasmModule.functions[functionIndex]
        val funcType = wasmModule.types[function.typeIndex]
        val name = functionName
            ?: wasmModule.functionName(functionIndex + wasmModule.importedFunctionCount)
            ?: "func_$functionIndex"

        val builder = ModuleBuilder(name, target)
        val irParams = buildParamList(funcType)
        val returnType = returnType(funcType)

        val params = builder.createFunction(name, irParams, returnType)
        builder.appendBlock("entry")

        val locals = initializeLocals(builder, params, funcType, function)
        val instructions = WasmDisassembler().disassemble(function.body)
        val context = CompilationContext(builder, params, params[0], locals, wasmModule)
        for (paramType in funcType.params) {
            context.localTypes.add(wasmTypeToIr(paramType))
        }
        for (localType in function.locals) {
            context.localTypes.add(wasmTypeToIr(localType))
        }

        translateAll(context, instructions)
        emitDefaultReturn(context, returnType)
        builder.finalizeFunction()
        return builder.build()
    }

    var traceEnabled = false
    var boundsCheckEnabled = false

    fun compileAll(): Module {
        val builder = ModuleBuilder("wasm_module", target)
        declareImports(builder)
        declareGlobals(builder)
        if (traceEnabled) {
            builder.declareFunction("__wark_trace_enter",
                listOf(Param("context", Type.I64), Param("funcId", Type.I32)), Type.Void)
            builder.declareFunction("__wark_trace_return",
                listOf(
                    Param("context", Type.I64),
                    Param("callerFuncId", Type.I64),
                    Param("calleeFuncId", Type.I64),
                    Param("returnValue", Type.I64),
                ), Type.Void)
        }
        if (boundsCheckEnabled) {
            builder.declareFunction("__wark_oob_trap",
                listOf(Param("addr", Type.I64), Param("memSize", Type.I64)), Type.Void)
        }
        if (traceEnabled) {
            builder.declareFunction("__wark_trace_block",
                listOf(Param("blockId", Type.I32)), Type.Void)
        }

        for ((index, function) in wasmModule.functions.withIndex()) {
            val globalIndex = index + wasmModule.importedFunctionCount
            val name = wasmModule.functionName(globalIndex) ?: "func_$index"
            val funcType = wasmModule.types[function.typeIndex]
            val irParams = buildParamList(funcType)
            val returnType = returnType(funcType)

            val params = builder.createFunction(name, irParams, returnType)
            builder.appendBlock("entry")

            if (traceEnabled) {
                builder.call("__wark_trace_enter", listOf(params[0], Constant.I32(index)), Type.Void)
            }
            // Debug: for specific functions, log the first WASM param
            if (traceEnabled && funcType.params.isNotEmpty() && index in setOf(75, 122, 123, 199, 34, 38, 41)) {
                builder.call("__wark_trace_arg", listOf(params[0], Constant.I32(index), params[1]), Type.Void)
            }

            val locals = initializeLocals(builder, params, funcType, function)
            val instructions = WasmDisassembler().disassemble(function.body)
            val context = CompilationContext(builder, params, params[0], locals, wasmModule)
            for (paramType in funcType.params) {
                context.localTypes.add(wasmTypeToIr(paramType))
            }
            for (localType in function.locals) {
                context.localTypes.add(wasmTypeToIr(localType))
            }
            context.boundsCheckEnabled = boundsCheckEnabled
            context.traceEnabled = traceEnabled
            context.functionIndex = index
            context.blockTraceEnabled = false

            try {
                translateAll(context, instructions)
                emitDefaultReturn(context, returnType)
            } catch (exception: IllegalStateException) {
                throw IllegalStateException("Failed compiling WASM function '$name' (index $index): ${exception.message}", exception)
            }
            builder.finalizeFunction()
        }

        generateIndirectCallDispatchers(builder)

        return builder.build()
    }

    /**
     * Generate arity-specific dispatch functions for call_indirect.
     * Each unique parameter count gets its own dispatcher: __wark_call_indirect_0,
     * __wark_call_indirect_1, etc. This avoids parameter count mismatches in
     * the IR and keeps each dispatcher's signature exact.
     */
    private fun generateIndirectCallDispatchers(builder: ModuleBuilder) {
        val functionTable = buildFunctionTable()
        if (functionTable.isEmpty()) {
            return
        }

        val importedFunctions = wasmModule.imports.filterIsInstance<WasmModule.Import.Func>()
        val importCount = wasmModule.importedFunctionCount

        // Collect all type indices referenced by call_indirect instructions.
        // For each, find table entries whose signature structurally matches.
        // WASM allows duplicate signatures under different type indices, so
        // we must match by structure, not by type index equality.
        val callIndirectTypeIndices = collectCallIndirectTypeIndices()

        for (typeIndex in callIndirectTypeIndices) {
            val expectedSig = wasmModule.types[typeIndex]
            val entries = mutableListOf<Pair<Int, Int>>()
            for ((tableSlot, funcIndex) in functionTable.withIndex()) {
                if (funcIndex < 0) { continue }
                val funcTypeIdx = functionTypeIndex(funcIndex, importedFunctions, importCount)
                val funcSig = wasmModule.types[funcTypeIdx]
                if (funcSig.params == expectedSig.params && funcSig.results == expectedSig.results) {
                    entries.add(tableSlot to funcIndex)
                }
            }
            generateDispatcherForType(builder, typeIndex, entries, importedFunctions, importCount)
        }
    }

    private fun collectCallIndirectTypeIndices(): Set<Int> {
        val indices = mutableSetOf<Int>()
        val disassembler = org.kgen.target.wasm.disasm.WasmDisassembler()
        for (func in wasmModule.functions) {
            for (instruction in disassembler.disassemble(func.body)) {
                if (instruction.mnemonic == "call_indirect") {
                    val operands = instruction.operands
                    if (operands is org.kgen.target.wasm.disasm.WasmInstruction.Operands.CallIndirect) {
                        indices.add(operands.typeIndex)
                    }
                }
            }
        }
        return indices
    }

    private fun functionTypeIndex(
        funcIndex: Int,
        importedFunctions: List<WasmModule.Import.Func>,
        importCount: Int,
    ): Int {
        if (funcIndex < importCount) {
            return importedFunctions[funcIndex].typeIndex
        }
        val localIndex = funcIndex - importCount
        return wasmModule.functions[localIndex].typeIndex
    }

    private fun generateDispatcherForType(
        builder: ModuleBuilder,
        typeIndex: Int,
        entries: List<Pair<Int, Int>>,
        importedFunctions: List<WasmModule.Import.Func>,
        importCount: Int,
    ) {
        val funcType = wasmModule.types[typeIndex]
        val dispatchParams = mutableListOf<Param>()
        dispatchParams.add(Param("context", Type.I64))
        dispatchParams.add(Param("tableIndex", Type.I32))
        for ((paramIndex, wasmType) in funcType.params.withIndex()) {
            dispatchParams.add(Param("arg$paramIndex", wasmTypeToIr(wasmType)))
        }

        val returnType = if (funcType.results.isEmpty()) {
            Type.Void
        } else {
            wasmTypeToIr(funcType.results[0])
        }

        val params = builder.createFunction("__wark_call_indirect_type$typeIndex", dispatchParams, returnType)
        builder.appendBlock("entry")
        val tableIndexParam = params[1]

        for ((tableSlot, funcIndex) in entries) {
            val calleeName: String
            if (funcIndex < importCount) {
                val importDecl = importedFunctions[funcIndex]
                calleeName = "${importDecl.module}_${importDecl.name}"
            } else {
                val localIndex = funcIndex - importCount
                calleeName = wasmModule.functionName(funcIndex) ?: "func_$localIndex"
            }

            val thenLabel = "type${typeIndex}_slot_${tableSlot}"
            val nextLabel = "type${typeIndex}_next_${tableSlot}"

            val cmp = builder.icmp(ICmpPredicate.EQ, tableIndexParam, Constant.I32(tableSlot))
            builder.condBr(cmp, thenLabel, nextLabel)

            builder.appendBlock(thenLabel)
            val callArgs = mutableListOf<Value>()
            callArgs.add(params[0])
            for (paramIndex in funcType.params.indices) {
                callArgs.add(params[paramIndex + 2])
            }
            val result = builder.call(calleeName, callArgs, returnType)
            if (returnType == Type.Void) {
                builder.ret()
            } else if (result != null) {
                builder.ret(result)
            } else {
                builder.ret()
            }

            builder.appendBlock(nextLabel)
        }

        // Default: trap for invalid table index
        builder.call("__wark_trap", listOf(Constant.I32(-1)), Type.Void)
        if (returnType == Type.Void) {
            builder.ret()
        } else {
            val defaultReturn = when (returnType) {
                Type.I32 -> Constant.I32(0)
                Type.I64 -> Constant.I64(0)
                Type.F32 -> Constant.F32(0f)
                Type.F64 -> Constant.F64(0.0)
                else -> Constant.I64(0)
            }
            builder.ret(defaultReturn)
        }
        builder.finalizeFunction()
    }

    private fun buildFunctionTable(): List<Int> {
        val table = mutableListOf<Int>()
        for (element in wasmModule.elements) {
            if (element is WasmModule.Element.Active) {
                val offset = evaluateOffset(element.offsetExpr)
                // Pad table to the offset position
                while (table.size < offset) {
                    table.add(-1)
                }
                for ((index, funcIndex) in element.funcIndices.withIndex()) {
                    val slot = offset + index
                    while (table.size <= slot) {
                        table.add(-1)
                    }
                    table[slot] = funcIndex
                }
            }
        }
        return table
    }

    private fun evaluateOffset(expr: ByteArray): Int {
        if (expr.isEmpty()) {
            return 0
        }
        if (expr[0].toInt() and 0xFF == 0x41) {
            // i32.const — decode LEB128
            var value = 0
            var shift = 0
            var position = 1
            while (position < expr.size) {
                val byte = expr[position].toInt() and 0xFF
                position++
                value = value or ((byte and 0x7F) shl shift)
                shift += 7
                if (byte and 0x80 == 0) {
                    break
                }
            }
            return value
        }
        return 0
    }

    private fun translateAll(context: CompilationContext, instructions: List<WasmInstruction>) {
        var deadDepth = 0
        for (instruction in instructions) {
            if (context.terminated) {
                break
            }
            val opcode = instruction.opcode

            if (deadDepth > 0) {
                when (opcode) {
                    WasmOpCode.BLOCK, WasmOpCode.LOOP, WasmOpCode.IF -> deadDepth++
                    WasmOpCode.END -> {
                        deadDepth--
                        if (deadDepth == 0) {
                            val translator = translators.firstOrNull { it.canHandle(opcode) }
                            translator?.translate(context, instruction)
                        }
                    }
                    WasmOpCode.ELSE -> {
                        if (deadDepth == 1) {
                            deadDepth = 0
                            val translator = translators.firstOrNull { it.canHandle(opcode) }
                            translator?.translate(context, instruction)
                        }
                    }
                    else -> { }
                }
                continue
            }

            val translator = translators.firstOrNull { it.canHandle(opcode) }
            if (translator == null) {
                throw IllegalStateException("Unhandled WASM opcode: '${instruction.text()}' (offset ${instruction.offset})")
            }
            try {
                translator.translate(context, instruction)
            } catch (exception: IllegalStateException) {
                throw IllegalStateException("at instruction '${instruction.text()}' (offset ${instruction.offset}): ${exception.message}", exception)
            }

            if (opcode == WasmOpCode.BR || opcode == WasmOpCode.BR_TABLE || opcode == WasmOpCode.RETURN || opcode == WasmOpCode.UNREACHABLE) {
                deadDepth = 1
            }
        }
    }

    private fun declareGlobals(builder: ModuleBuilder) {
        val importedGlobals = wasmModule.imports.filterIsInstance<WasmModule.Import.Global>()
        for ((index, imp) in importedGlobals.withIndex()) {
            val irType = wasmTypeToIr(imp.type)
            builder.addGlobal("__wasm_global_$index", irType, defaultValue(irType),
                isConstant = false, linkage = Linkage.INTERNAL)
        }
        for ((localIndex, global) in wasmModule.globals.withIndex()) {
            val index = importedGlobals.size + localIndex
            val irType = wasmTypeToIr(global.type)
            val initValue = evaluateInitExpr(global.initExpr)
            val initializer = when (irType) {
                Type.I32 -> Constant.I32(initValue.toInt())
                Type.I64 -> Constant.I64(initValue)
                Type.F32 -> Constant.F32(Float.fromBits(initValue.toInt()))
                Type.F64 -> Constant.F64(Double.fromBits(initValue))
                else -> Constant.I32(initValue.toInt())
            }
            builder.addGlobal("__wasm_global_$index", irType, initializer,
                isConstant = !global.mutable, linkage = Linkage.INTERNAL)
        }
    }

    private fun evaluateInitExpr(expr: ByteArray): Long {
        if (expr.isEmpty()) { return 0L }
        return when (expr[0].toInt() and 0xFF) {
            0x41 -> { var result = 0; var shift = 0; var pos = 1; while (pos < expr.size) { val byte = expr[pos].toInt() and 0xFF; result = result or ((byte and 0x7F) shl shift); shift += 7; pos++; if (byte and 0x80 == 0) { if (shift < 32 && byte and 0x40 != 0) { result = result or ((-1) shl shift) }; break } }; result.toLong() }
            0x42 -> { var result = 0L; var shift = 0; var pos = 1; while (pos < expr.size) { val byte = expr[pos].toInt() and 0xFF; result = result or ((byte.toLong() and 0x7F) shl shift); shift += 7; pos++; if (byte and 0x80 == 0) { if (shift < 64 && byte and 0x40 != 0) { result = result or (-1L shl shift) }; break } }; result }
            else -> 0L
        }
    }

    private fun declareImports(builder: ModuleBuilder) {
        for (importDecl in wasmModule.imports) {
            if (importDecl is WasmModule.Import.Func) {
                val funcType = wasmModule.types[importDecl.typeIndex]
                val importParams = buildParamList(funcType)
                val importReturnType = returnType(funcType)
                builder.declareFunction("${importDecl.module}_${importDecl.name}", importParams, importReturnType)
            }
        }
    }

    private fun buildParamList(funcType: WasmModule.FuncType): List<Param> {
        val params = mutableListOf<Param>()
        params.add(Param("context", Type.I64))
        for ((index, paramType) in funcType.params.withIndex()) {
            params.add(Param("p$index", wasmTypeToIr(paramType)))
        }
        return params
    }

    private fun returnType(funcType: WasmModule.FuncType): Type {
        return if (funcType.results.isEmpty()) {
            Type.Void
        } else {
            wasmTypeToIr(funcType.results[0])
        }
    }

    private fun initializeLocals(
        builder: ModuleBuilder,
        params: List<Value>,
        funcType: WasmModule.FuncType,
        function: WasmModule.Function,
    ): List<Value> {
        val locals = mutableListOf<Value>()
        for ((index, paramType) in funcType.params.withIndex()) {
            val alloca = builder.alloca(wasmTypeToIr(paramType))
            builder.store(params[index + 1], alloca)
            locals.add(alloca)
        }
        for (localType in function.locals) {
            val alloca = builder.alloca(wasmTypeToIr(localType))
            builder.store(defaultValue(wasmTypeToIr(localType)), alloca)
            locals.add(alloca)
        }
        return locals
    }

    private fun emitDefaultReturn(context: CompilationContext, returnType: Type) {
        if (!context.terminated) {
            if (returnType == Type.Void) {
                context.builder.ret()
            } else if (!context.stack.isEmpty()) {
                context.builder.ret(context.stack.pop())
            } else {
                context.builder.ret(defaultValue(returnType))
            }
        }
    }

    companion object {
        @JvmStatic
        fun wasmTypeToIr(wasmType: WasmValueType): Type = when (wasmType) {
            WasmValueType.I32 -> Type.I32
            WasmValueType.I64 -> Type.I64
            WasmValueType.F32 -> Type.F32
            WasmValueType.F64 -> Type.F64
            else -> Type.I64
        }

        @JvmStatic
        fun defaultValue(type: Type): Constant = when (type) {
            Type.I32 -> Constant.I32(0)
            Type.I64 -> Constant.I64(0)
            Type.F32 -> Constant.F32(0.0f)
            Type.F64 -> Constant.F64(0.0)
            else -> Constant.I64(0)
        }
    }
}

class CompilationContext(
    val builder: ModuleBuilder,
    val params: List<Value>,
    val contextPointer: Value,
    val locals: List<Value>,
    val wasmModule: WasmModule,
    val stack: ValueStack = ValueStack(),
    val controlStack: ControlStack = ControlStack(),
    var terminated: Boolean = false,
) {
    private var labelCounter = 0

    fun freshLabel(prefix: String): String {
        val label = "${prefix}_${labelCounter}"
        labelCounter++
        return label
    }

    fun loadMemoryBase(): Value {
        val basePtr = builder.add(contextPointer, Constant.I64(org.wark.RuntimeContextLayout.MEMORY_BASE))
        return builder.load(Type.I64, basePtr)
    }

    var boundsCheckEnabled = false
    var traceEnabled = false
    var functionIndex = 0
    var currentBlockLabel = "entry"
    var blockTraceEnabled = false
    var blockCounter = 0

    fun emitBlockTrace() {
        if (blockTraceEnabled) {
            builder.call("__wark_trace_block", listOf(Constant.I32(blockCounter++)), Type.Void)
        }
    }

    fun emitBoundsCheck(wasmAddress: Value, accessSize: Int) {
        if (!boundsCheckEnabled) {
            return
        }
        val address64 = if (wasmAddress.type == Type.I32) {
            builder.zext(wasmAddress, Type.I64)
        } else {
            wasmAddress
        }
        val endAddr = builder.add(address64, Constant.I64(accessSize.toLong()))
        val sizePtr = builder.add(contextPointer, Constant.I64(org.wark.RuntimeContextLayout.MEMORY_SIZE))
        val memSize = builder.load(Type.I64, sizePtr)
        val oob = builder.icmp(ICmpPredicate.UGT, endAddr, memSize)
        val trapLabel = freshLabel("oob_trap")
        val okLabel = freshLabel("oob_ok")
        builder.condBr(oob, trapLabel, okLabel)
        builder.appendBlock(trapLabel)
        // Log the failing address and memSize before trapping
        builder.call("__wark_oob_trap", listOf(address64, memSize), Type.Void)
        builder.ret()
        builder.appendBlock(okLabel)
    }

    val localTypes: MutableList<Type> = mutableListOf()

    fun localType(index: Int): Type {
        if (index < localTypes.size) {
            return localTypes[index]
        }
        return Type.I32
    }
}
