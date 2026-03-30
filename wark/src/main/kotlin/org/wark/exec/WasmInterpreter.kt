package org.wark.exec

import org.kgen.target.wasm.WasmOpCode
import org.kgen.target.wasm.disasm.WasmDisassembler
import org.kgen.target.wasm.disasm.WasmInstruction
import org.kgen.target.wasm.disasm.WasmInstruction.Operands
import org.kgen.target.wasm.module.WasmModule
import org.wark.WarkImports
import org.wark.WarkMemory
import org.wark.WasmTrap

/**
 * Bytecode interpreter for WASM modules. Executes instructions directly
 * without compilation. Slower than JIT but fully portable and useful for
 * debugging.
 *
 * ```kotlin
 * val interpreter = WasmInterpreter(wasmModule, memories, globals, imports)
 * val result = interpreter.call(functionIndex, longArrayOf(3, 4))
 * ```
 */
class WasmInterpreter(
    private val wasmModule: WasmModule,
    private val memories: List<WarkMemory>,
    private val globals: MutableList<Long>,
    private val imports: WarkImports,
    private val instance: org.wark.WarkInstance,
) {
    val functionTable: MutableList<Int> = mutableListOf()
    private val dataSegments: MutableMap<Int, ByteArray> = mutableMapOf()

    init {
        // Store all data segments for memory.init access
        for ((index, segment) in wasmModule.dataSegments.withIndex()) {
            dataSegments[index] = segment.data
        }
    }

    init {
        for (element in wasmModule.elements) {
            if (element is WasmModule.Element.Active && element.tableIndex == 0) {
                val offset = evaluateTableOffset(element.offsetExpr)
                while (functionTable.size < offset) {
                    functionTable.add(-1)
                }
                for ((index, funcIndex) in element.funcIndices.withIndex()) {
                    val slot = offset + index
                    while (functionTable.size <= slot) {
                        functionTable.add(-1)
                    }
                    functionTable[slot] = funcIndex
                }
            }
        }
    }

    private fun evaluateTableOffset(expr: ByteArray): Int {
        if (expr.isNotEmpty() && expr[0].toInt() and 0xFF == 0x41) {
            var value = 0; var shift = 0; var position = 1
            while (position < expr.size) {
                val byte = expr[position].toInt() and 0xFF; position++
                value = value or ((byte and 0x7F) shl shift); shift += 7
                if (byte and 0x80 == 0) { break }
            }
            return value
        }
        return 0
    }
    private val disassembler = WasmDisassembler()
    private val functionBodies = mutableMapOf<Int, List<WasmInstruction>>()
    private val importedFunctions: List<WasmModule.Import.Func> =
        wasmModule.imports.filterIsInstance<WasmModule.Import.Func>()
    private val calleeTypeCache = mutableMapOf<Int, WasmModule.FuncType>()
    private val blockStructureCache = mutableMapOf<Int, BlockStructure>()

    var traceEnabled = false
    private val tracedCalls = mutableListOf<String>()

    fun tracedCalls(): List<String> = tracedCalls.toList()

    fun call(functionIndex: Int, args: LongArray): LongArray {
        val importCount = wasmModule.importedFunctionCount

        if (traceEnabled && tracedCalls.size < 100000) {
            val name = wasmModule.functionName(functionIndex) ?: "func_${functionIndex - importCount}"
            tracedCalls.add(name)
        }

        if (functionIndex < importCount) {
            val importDecl = importedFunctions[functionIndex]
            val hostFunc = imports.resolveFunction(importDecl.module, importDecl.name)
                ?: throw WasmTrap("unresolved import: ${importDecl.module}.${importDecl.name}")
            return hostFunc.call(instance, args)
        }

        val localIndex = functionIndex - importCount
        val function = wasmModule.functions[localIndex]
        val funcType = wasmModule.types[function.typeIndex]

        val instructions = functionBodies.getOrPut(localIndex) {
            disassembler.disassemble(function.body)
        }

        val locals = LongArray(funcType.params.size + function.locals.size)
        for (index in args.indices) {
            if (index < funcType.params.size) {
                locals[index] = args[index]
            }
        }

        onFunctionEntry?.invoke(functionIndex, args)

        val previousFunctionIndex = currentFunctionIndex
        currentFunctionIndex = functionIndex
        callDepth++
        val frame = InterpreterFrame(locals, funcType)
        try {
            return execute(frame, instructions, localIndex)
        } finally {
            callDepth--
            currentFunctionIndex = previousFunctionIndex
        }
    }

    fun functionName(index: Int): String = wasmModule.functionName(index) ?: "func_${index - wasmModule.importedFunctionCount}"

    fun setGlobal(index: Int, value: Long) {
        if (index in globals.indices) {
            globals[index] = value
        }
    }

    var instructionLimit: Long = Long.MAX_VALUE
    var totalInstructions: Long = 0
        private set

    /**
     * Called before each instruction executes.
     * Return false to pause execution (for debugger stepping).
     */
    var onStep: ((StepInfo) -> Boolean)? = null

    /**
     * Called on function entry with (functionIndex, args).
     * Useful for logging/debugging specific function calls.
     */
    fun functionTableEntry(tableIndex: Int): Int =
        if (tableIndex in functionTable.indices) functionTable[tableIndex] else -1

    var onFunctionEntry: ((Int, LongArray) -> Unit)? = null

    /** Current call depth for debugger display. */
    var callDepth: Int = 0
        private set

    /** Snapshot of state at the current instruction, for debugger inspection. */
    class StepInfo(
        val functionIndex: Int,
        val programCounter: Int,
        val mnemonic: String,
        private val stackRef: ArrayDeque<Long>,
        private val localsRef: LongArray,
        val callDepth: Int,
        val totalInstructions: Long,
    ) {
        fun stack(): List<Long> = stackRef.toList()
        fun locals(): LongArray = localsRef.clone()
        fun stackSize(): Int = stackRef.size
        fun stackPeek(index: Int): Long = stackRef[stackRef.size - 1 - index]
        fun local(index: Int): Long = localsRef[index]
    }

    /** Paused state — set when onStep returns false. */
    var paused = false
        private set

    private var currentFunctionIndex = -1

    private fun execute(frame: InterpreterFrame, instructions: List<WasmInstruction>, localIndex: Int): LongArray {
        val stack = frame.stack
        val locals = frame.locals
        var programCounter = 0
        val blockStructure = getBlockStructure(localIndex, instructions)

        val controlStack = mutableListOf<ControlFrame>()
        val functionEndPc = instructions.indexOfLast { it.opcode == WasmOpCode.END }
        val functionHasResult = frame.funcType.results.isNotEmpty()
        controlStack.add(ControlFrame(ControlKind.BLOCK, if (functionEndPc >= 0) { functionEndPc } else { instructions.size }, 0, functionHasResult))

        while (programCounter < instructions.size) {
            totalInstructions++
            if (totalInstructions > instructionLimit) {
                throw WasmTrap("instruction limit exceeded ($instructionLimit)")
            }

            val instruction = instructions[programCounter]

            try {

            val stepCallback = onStep
            if (stepCallback != null) {
                val info = StepInfo(
                    currentFunctionIndex, programCounter, instruction.opcode.mnemonic,
                    stack, locals, callDepth, totalInstructions,
                )
                if (!stepCallback(info)) {
                    paused = true
                    throw WasmTrap("debugger pause")
                }
            }

            programCounter++

            when (instruction.opcode) {
                WasmOpCode.I32_CONST -> stack.addLast((instruction.operands as Operands.I32).value.toLong())
                WasmOpCode.I64_CONST -> stack.addLast((instruction.operands as Operands.I64).value)
                WasmOpCode.F32_CONST -> stack.addLast(java.lang.Float.floatToRawIntBits((instruction.operands as Operands.F32).value).toLong())
                WasmOpCode.F64_CONST -> stack.addLast(java.lang.Double.doubleToRawLongBits((instruction.operands as Operands.F64).value))

                WasmOpCode.LOCAL_GET -> stack.addLast(locals[(instruction.operands as Operands.Index).value])
                WasmOpCode.LOCAL_SET -> {
                    if (stack.isEmpty()) {
                        throw WasmTrap("stack underflow at local.set in ${functionName(currentFunctionIndex)}, PC=$programCounter, controlStack=${controlStack.size}")
                    }
                    locals[(instruction.operands as Operands.Index).value] = stack.removeLast()
                }
                WasmOpCode.LOCAL_TEE -> locals[(instruction.operands as Operands.Index).value] = stack.last()

                WasmOpCode.I32_ADD -> { val b = stack.removeLast().toInt(); val a = stack.removeLast().toInt(); stack.addLast((a + b).toLong()) }
                WasmOpCode.I32_SUB -> { val b = stack.removeLast().toInt(); val a = stack.removeLast().toInt(); stack.addLast((a - b).toLong()) }
                WasmOpCode.I32_MUL -> { val b = stack.removeLast().toInt(); val a = stack.removeLast().toInt(); stack.addLast((a * b).toLong()) }
                WasmOpCode.I32_DIV_S -> { val b = stack.removeLast().toInt(); val a = stack.removeLast().toInt(); if (b == 0) throw WasmTrap("division by zero"); stack.addLast((a / b).toLong()) }
                WasmOpCode.I32_DIV_U -> { val b = stack.removeLast().toInt().toLong() and 0xFFFFFFFFL; val a = stack.removeLast().toInt().toLong() and 0xFFFFFFFFL; if (b == 0L) throw WasmTrap("division by zero"); stack.addLast(a / b) }
                WasmOpCode.I32_REM_S -> { val b = stack.removeLast().toInt(); val a = stack.removeLast().toInt(); if (b == 0) throw WasmTrap("division by zero"); stack.addLast((a % b).toLong()) }
                WasmOpCode.I32_REM_U -> { val b = stack.removeLast().toInt().toLong() and 0xFFFFFFFFL; val a = stack.removeLast().toInt().toLong() and 0xFFFFFFFFL; if (b == 0L) throw WasmTrap("division by zero"); stack.addLast(a % b) }
                WasmOpCode.I32_AND -> { val b = stack.removeLast().toInt(); val a = stack.removeLast().toInt(); stack.addLast((a and b).toLong()) }
                WasmOpCode.I32_OR -> { val b = stack.removeLast().toInt(); val a = stack.removeLast().toInt(); stack.addLast((a or b).toLong()) }
                WasmOpCode.I32_XOR -> { val b = stack.removeLast().toInt(); val a = stack.removeLast().toInt(); stack.addLast((a xor b).toLong()) }
                WasmOpCode.I32_SHL -> { val b = stack.removeLast().toInt(); val a = stack.removeLast().toInt(); stack.addLast((a shl (b and 31)).toLong()) }
                WasmOpCode.I32_SHR_S -> { val b = stack.removeLast().toInt(); val a = stack.removeLast().toInt(); stack.addLast((a shr (b and 31)).toLong()) }
                WasmOpCode.I32_SHR_U -> { val b = stack.removeLast().toInt(); val a = stack.removeLast().toInt(); stack.addLast((a ushr (b and 31)).toLong()) }
                WasmOpCode.I32_ROTL -> { val b = stack.removeLast().toInt(); val a = stack.removeLast().toInt(); stack.addLast(Integer.rotateLeft(a, b).toLong()) }
                WasmOpCode.I32_ROTR -> { val b = stack.removeLast().toInt(); val a = stack.removeLast().toInt(); stack.addLast(Integer.rotateRight(a, b).toLong()) }

                WasmOpCode.I64_ADD -> { val b = stack.removeLast(); val a = stack.removeLast(); stack.addLast(a + b) }
                WasmOpCode.I64_SUB -> { val b = stack.removeLast(); val a = stack.removeLast(); stack.addLast(a - b) }
                WasmOpCode.I64_MUL -> { val b = stack.removeLast(); val a = stack.removeLast(); stack.addLast(a * b) }
                WasmOpCode.I64_DIV_S -> { val b = stack.removeLast(); val a = stack.removeLast(); if (b == 0L) throw WasmTrap("division by zero"); stack.addLast(a / b) }
                WasmOpCode.I64_REM_S -> { val b = stack.removeLast(); val a = stack.removeLast(); if (b == 0L) throw WasmTrap("division by zero"); stack.addLast(a % b) }
                WasmOpCode.I64_AND -> { val b = stack.removeLast(); val a = stack.removeLast(); stack.addLast(a and b) }
                WasmOpCode.I64_OR -> { val b = stack.removeLast(); val a = stack.removeLast(); stack.addLast(a or b) }
                WasmOpCode.I64_XOR -> { val b = stack.removeLast(); val a = stack.removeLast(); stack.addLast(a xor b) }
                WasmOpCode.I64_SHL -> { val b = stack.removeLast().toInt(); val a = stack.removeLast(); stack.addLast(a shl (b and 63)) }
                WasmOpCode.I64_SHR_S -> { val b = stack.removeLast().toInt(); val a = stack.removeLast(); stack.addLast(a shr (b and 63)) }
                WasmOpCode.I64_SHR_U -> { val b = stack.removeLast().toInt(); val a = stack.removeLast(); stack.addLast(a ushr (b and 63)) }
                WasmOpCode.I64_ROTL -> { val b = stack.removeLast().toInt(); val a = stack.removeLast(); stack.addLast(java.lang.Long.rotateLeft(a, b)) }
                WasmOpCode.I64_ROTR -> { val b = stack.removeLast().toInt(); val a = stack.removeLast(); stack.addLast(java.lang.Long.rotateRight(a, b)) }

                WasmOpCode.F32_ADD -> { val b = f(stack.removeLast()); val a = f(stack.removeLast()); stack.addLast(fi(a + b)) }
                WasmOpCode.F32_SUB -> { val b = f(stack.removeLast()); val a = f(stack.removeLast()); stack.addLast(fi(a - b)) }
                WasmOpCode.F32_MUL -> { val b = f(stack.removeLast()); val a = f(stack.removeLast()); stack.addLast(fi(a * b)) }
                WasmOpCode.F32_DIV -> { val b = f(stack.removeLast()); val a = f(stack.removeLast()); stack.addLast(fi(a / b)) }
                WasmOpCode.F32_NEG -> { stack.addLast(fi(-f(stack.removeLast()))) }
                WasmOpCode.F32_ABS -> { stack.addLast(fi(kotlin.math.abs(f(stack.removeLast())))) }
                WasmOpCode.F32_SQRT -> { stack.addLast(fi(kotlin.math.sqrt(f(stack.removeLast()).toDouble()).toFloat())) }
                WasmOpCode.F32_CEIL -> { stack.addLast(fi(kotlin.math.ceil(f(stack.removeLast()).toDouble()).toFloat())) }
                WasmOpCode.F32_FLOOR -> { stack.addLast(fi(kotlin.math.floor(f(stack.removeLast()).toDouble()).toFloat())) }
                WasmOpCode.F32_TRUNC -> { stack.addLast(fi(truncateFloat(f(stack.removeLast())))) }
                WasmOpCode.F32_NEAREST -> { stack.addLast(fi(nearestFloat(f(stack.removeLast())))) }
                WasmOpCode.F32_MIN -> { val b = f(stack.removeLast()); val a = f(stack.removeLast()); stack.addLast(fi(wasmMinF32(a, b))) }
                WasmOpCode.F32_MAX -> { val b = f(stack.removeLast()); val a = f(stack.removeLast()); stack.addLast(fi(wasmMaxF32(a, b))) }
                WasmOpCode.F32_COPYSIGN -> { val b = f(stack.removeLast()); val a = f(stack.removeLast()); stack.addLast(fi(Math.copySign(a, b))) }
                WasmOpCode.F32_EQ -> { val b = f(stack.removeLast()); val a = f(stack.removeLast()); stack.addLast(if (a == b) 1L else 0L) }
                WasmOpCode.F32_NE -> { val b = f(stack.removeLast()); val a = f(stack.removeLast()); stack.addLast(if (a != b) 1L else 0L) }
                WasmOpCode.F32_LT -> { val b = f(stack.removeLast()); val a = f(stack.removeLast()); stack.addLast(if (a < b) 1L else 0L) }
                WasmOpCode.F32_GT -> { val b = f(stack.removeLast()); val a = f(stack.removeLast()); stack.addLast(if (a > b) 1L else 0L) }
                WasmOpCode.F32_LE -> { val b = f(stack.removeLast()); val a = f(stack.removeLast()); stack.addLast(if (a <= b) 1L else 0L) }
                WasmOpCode.F32_GE -> { val b = f(stack.removeLast()); val a = f(stack.removeLast()); stack.addLast(if (a >= b) 1L else 0L) }

                WasmOpCode.F64_ADD -> { val b = d(stack.removeLast()); val a = d(stack.removeLast()); stack.addLast(l(a + b)) }
                WasmOpCode.F64_SUB -> { val b = d(stack.removeLast()); val a = d(stack.removeLast()); stack.addLast(l(a - b)) }
                WasmOpCode.F64_MUL -> { val b = d(stack.removeLast()); val a = d(stack.removeLast()); stack.addLast(l(a * b)) }
                WasmOpCode.F64_DIV -> { val b = d(stack.removeLast()); val a = d(stack.removeLast()); stack.addLast(l(a / b)) }
                WasmOpCode.F64_NEG -> { stack.addLast(l(-d(stack.removeLast()))) }
                WasmOpCode.F64_ABS -> { stack.addLast(l(kotlin.math.abs(d(stack.removeLast())))) }
                WasmOpCode.F64_SQRT -> { stack.addLast(l(kotlin.math.sqrt(d(stack.removeLast())))) }
                WasmOpCode.F64_CEIL -> { stack.addLast(l(kotlin.math.ceil(d(stack.removeLast())))) }
                WasmOpCode.F64_FLOOR -> { stack.addLast(l(kotlin.math.floor(d(stack.removeLast())))) }
                WasmOpCode.F64_TRUNC -> { stack.addLast(l(truncateDouble(d(stack.removeLast())))) }
                WasmOpCode.F64_NEAREST -> { stack.addLast(l(nearestDouble(d(stack.removeLast())))) }
                WasmOpCode.F64_MIN -> { val b = d(stack.removeLast()); val a = d(stack.removeLast()); stack.addLast(l(wasmMinF64(a, b))) }
                WasmOpCode.F64_MAX -> { val b = d(stack.removeLast()); val a = d(stack.removeLast()); stack.addLast(l(wasmMaxF64(a, b))) }
                WasmOpCode.F64_COPYSIGN -> { val b = d(stack.removeLast()); val a = d(stack.removeLast()); stack.addLast(l(Math.copySign(a, b))) }
                WasmOpCode.F64_EQ -> { val b = d(stack.removeLast()); val a = d(stack.removeLast()); stack.addLast(if (a == b) 1L else 0L) }
                WasmOpCode.F64_NE -> { val b = d(stack.removeLast()); val a = d(stack.removeLast()); stack.addLast(if (a != b) 1L else 0L) }
                WasmOpCode.F64_LT -> { val b = d(stack.removeLast()); val a = d(stack.removeLast()); stack.addLast(if (a < b) 1L else 0L) }
                WasmOpCode.F64_GT -> { val b = d(stack.removeLast()); val a = d(stack.removeLast()); stack.addLast(if (a > b) 1L else 0L) }
                WasmOpCode.F64_LE -> { val b = d(stack.removeLast()); val a = d(stack.removeLast()); stack.addLast(if (a <= b) 1L else 0L) }
                WasmOpCode.F64_GE -> { val b = d(stack.removeLast()); val a = d(stack.removeLast()); stack.addLast(if (a >= b) 1L else 0L) }

                WasmOpCode.I64_EQZ -> stack.addLast(if (stack.removeLast() == 0L) 1L else 0L)
                WasmOpCode.I64_EQ -> { val b = stack.removeLast(); val a = stack.removeLast(); stack.addLast(if (a == b) 1L else 0L) }
                WasmOpCode.I64_NE -> { val b = stack.removeLast(); val a = stack.removeLast(); stack.addLast(if (a != b) 1L else 0L) }
                WasmOpCode.I64_LT_S -> { val b = stack.removeLast(); val a = stack.removeLast(); stack.addLast(if (a < b) 1L else 0L) }
                WasmOpCode.I64_GT_S -> { val b = stack.removeLast(); val a = stack.removeLast(); stack.addLast(if (a > b) 1L else 0L) }
                WasmOpCode.I64_LE_S -> { val b = stack.removeLast(); val a = stack.removeLast(); stack.addLast(if (a <= b) 1L else 0L) }
                WasmOpCode.I64_GE_S -> { val b = stack.removeLast(); val a = stack.removeLast(); stack.addLast(if (a >= b) 1L else 0L) }

                WasmOpCode.I32_WRAP_I64 -> stack.addLast(stack.removeLast() and 0xFFFFFFFFL)
                WasmOpCode.I64_EXTEND_I32_S -> stack.addLast(stack.removeLast().toInt().toLong())
                WasmOpCode.I64_EXTEND_I32_U -> stack.addLast(stack.removeLast() and 0xFFFFFFFFL)
                WasmOpCode.F64_CONVERT_I32_S -> stack.addLast(l(stack.removeLast().toInt().toDouble()))
                WasmOpCode.F64_CONVERT_I32_U -> stack.addLast(l((stack.removeLast().toInt().toLong() and 0xFFFFFFFFL).toDouble()))
                WasmOpCode.F64_CONVERT_I64_S -> stack.addLast(l(stack.removeLast().toDouble()))
                WasmOpCode.F64_CONVERT_I64_U -> {
                    val value = stack.removeLast()
                    val result = if (value >= 0) { value.toDouble() } else { (value ushr 1).toDouble() * 2.0 + (value and 1L).toDouble() }
                    stack.addLast(l(result))
                }
                WasmOpCode.F32_CONVERT_I32_S -> stack.addLast(fi(stack.removeLast().toInt().toFloat()))
                WasmOpCode.F32_CONVERT_I32_U -> stack.addLast(fi((stack.removeLast().toInt().toLong() and 0xFFFFFFFFL).toFloat()))
                WasmOpCode.F32_CONVERT_I64_S -> stack.addLast(fi(stack.removeLast().toFloat()))
                WasmOpCode.F32_CONVERT_I64_U -> {
                    val value = stack.removeLast()
                    val result = if (value >= 0) { value.toFloat() } else { (value ushr 1).toFloat() * 2.0f + (value and 1L).toFloat() }
                    stack.addLast(fi(result))
                }
                WasmOpCode.I32_TRUNC_F64_S -> stack.addLast(d(stack.removeLast()).toInt().toLong())
                WasmOpCode.I32_TRUNC_F64_U -> {
                    val value = d(stack.removeLast())
                    if (value.isNaN()) { throw WasmTrap("invalid conversion to integer") }
                    if (value >= 4294967296.0 || value < 0.0) { throw WasmTrap("integer overflow") }
                    stack.addLast(value.toLong() and 0xFFFFFFFFL)
                }
                WasmOpCode.I32_TRUNC_F32_S -> stack.addLast(f(stack.removeLast()).toInt().toLong())
                WasmOpCode.I32_TRUNC_F32_U -> {
                    val value = f(stack.removeLast())
                    if (value.isNaN()) { throw WasmTrap("invalid conversion to integer") }
                    if (value >= 4294967296.0f || value < 0.0f) { throw WasmTrap("integer overflow") }
                    stack.addLast(value.toLong() and 0xFFFFFFFFL)
                }
                WasmOpCode.I64_TRUNC_F64_S -> {
                    val value = d(stack.removeLast())
                    if (value.isNaN()) { throw WasmTrap("invalid conversion to integer") }
                    stack.addLast(value.toLong())
                }
                WasmOpCode.I64_TRUNC_F64_U -> {
                    val value = d(stack.removeLast())
                    if (value.isNaN()) { throw WasmTrap("invalid conversion to integer") }
                    if (value < 0.0 || value >= 1.8446744073709552E19) { throw WasmTrap("integer overflow") }
                    stack.addLast(if (value < 9.223372036854776E18) { value.toLong() } else { (value - 9.223372036854776E18).toLong() + Long.MIN_VALUE })
                }
                WasmOpCode.I64_TRUNC_F32_S -> {
                    val value = f(stack.removeLast())
                    if (value.isNaN()) { throw WasmTrap("invalid conversion to integer") }
                    stack.addLast(value.toLong())
                }
                WasmOpCode.I64_TRUNC_F32_U -> {
                    val value = f(stack.removeLast()).toDouble()
                    if (value.isNaN()) { throw WasmTrap("invalid conversion to integer") }
                    if (value < 0.0 || value >= 1.8446744073709552E19) { throw WasmTrap("integer overflow") }
                    stack.addLast(if (value < 9.223372036854776E18) { value.toLong() } else { (value - 9.223372036854776E18).toLong() + Long.MIN_VALUE })
                }
                WasmOpCode.F64_PROMOTE_F32 -> stack.addLast(l(f(stack.removeLast()).toDouble()))
                WasmOpCode.F32_DEMOTE_F64 -> stack.addLast(fi(d(stack.removeLast()).toFloat()))
                WasmOpCode.I32_REINTERPRET_F32 -> { }
                WasmOpCode.F32_REINTERPRET_I32 -> { }
                WasmOpCode.I64_REINTERPRET_F64 -> { }
                WasmOpCode.F64_REINTERPRET_I64 -> { }
                WasmOpCode.I32_EXTEND8_S -> stack.addLast(stack.removeLast().toByte().toLong())
                WasmOpCode.I32_EXTEND16_S -> stack.addLast(stack.removeLast().toShort().toLong())
                WasmOpCode.I64_EXTEND8_S -> stack.addLast(stack.removeLast().toByte().toLong())
                WasmOpCode.I64_EXTEND16_S -> stack.addLast(stack.removeLast().toShort().toLong())
                WasmOpCode.I64_EXTEND32_S -> stack.addLast(stack.removeLast().toInt().toLong())

                WasmOpCode.I32_EQZ -> stack.addLast(if (stack.removeLast().toInt() == 0) 1L else 0L)
                WasmOpCode.I32_EQ -> { val b = stack.removeLast().toInt(); val a = stack.removeLast().toInt(); stack.addLast(if (a == b) 1L else 0L) }
                WasmOpCode.I32_NE -> { val b = stack.removeLast().toInt(); val a = stack.removeLast().toInt(); stack.addLast(if (a != b) 1L else 0L) }
                WasmOpCode.I32_LT_S -> { val b = stack.removeLast().toInt(); val a = stack.removeLast().toInt(); stack.addLast(if (a < b) 1L else 0L) }
                WasmOpCode.I32_LT_U -> { val b = stack.removeLast().toInt().toLong() and 0xFFFFFFFFL; val a = stack.removeLast().toInt().toLong() and 0xFFFFFFFFL; stack.addLast(if (a < b) 1L else 0L) }
                WasmOpCode.I32_GT_S -> { val b = stack.removeLast().toInt(); val a = stack.removeLast().toInt(); stack.addLast(if (a > b) 1L else 0L) }
                WasmOpCode.I32_GT_U -> { val b = stack.removeLast().toInt().toLong() and 0xFFFFFFFFL; val a = stack.removeLast().toInt().toLong() and 0xFFFFFFFFL; stack.addLast(if (a > b) 1L else 0L) }
                WasmOpCode.I32_LE_S -> { val b = stack.removeLast().toInt(); val a = stack.removeLast().toInt(); stack.addLast(if (a <= b) 1L else 0L) }
                WasmOpCode.I32_LE_U -> { val b = stack.removeLast().toInt().toLong() and 0xFFFFFFFFL; val a = stack.removeLast().toInt().toLong() and 0xFFFFFFFFL; stack.addLast(if (a <= b) 1L else 0L) }
                WasmOpCode.I32_GE_S -> { val b = stack.removeLast().toInt(); val a = stack.removeLast().toInt(); stack.addLast(if (a >= b) 1L else 0L) }
                WasmOpCode.I32_GE_U -> { val b = stack.removeLast().toInt().toLong() and 0xFFFFFFFFL; val a = stack.removeLast().toInt().toLong() and 0xFFFFFFFFL; stack.addLast(if (a >= b) 1L else 0L) }
                WasmOpCode.I32_CLZ -> stack.addLast(Integer.numberOfLeadingZeros(stack.removeLast().toInt()).toLong())
                WasmOpCode.I32_CTZ -> stack.addLast(Integer.numberOfTrailingZeros(stack.removeLast().toInt()).toLong())
                WasmOpCode.I32_POPCNT -> stack.addLast(Integer.bitCount(stack.removeLast().toInt()).toLong())
                WasmOpCode.I64_CLZ -> stack.addLast(java.lang.Long.numberOfLeadingZeros(stack.removeLast()).toLong())
                WasmOpCode.I64_CTZ -> stack.addLast(java.lang.Long.numberOfTrailingZeros(stack.removeLast()).toLong())
                WasmOpCode.I64_POPCNT -> stack.addLast(java.lang.Long.bitCount(stack.removeLast()).toLong())
                WasmOpCode.I64_LT_U -> { val b = stack.removeLast(); val a = stack.removeLast(); stack.addLast(if (java.lang.Long.compareUnsigned(a, b) < 0) 1L else 0L) }
                WasmOpCode.I64_GT_U -> { val b = stack.removeLast(); val a = stack.removeLast(); stack.addLast(if (java.lang.Long.compareUnsigned(a, b) > 0) 1L else 0L) }
                WasmOpCode.I64_LE_U -> { val b = stack.removeLast(); val a = stack.removeLast(); stack.addLast(if (java.lang.Long.compareUnsigned(a, b) <= 0) 1L else 0L) }
                WasmOpCode.I64_GE_U -> { val b = stack.removeLast(); val a = stack.removeLast(); stack.addLast(if (java.lang.Long.compareUnsigned(a, b) >= 0) 1L else 0L) }
                WasmOpCode.I64_DIV_U -> { val b = stack.removeLast(); val a = stack.removeLast(); if (b == 0L) throw WasmTrap("division by zero"); stack.addLast(java.lang.Long.divideUnsigned(a, b)) }
                WasmOpCode.I64_REM_U -> { val b = stack.removeLast(); val a = stack.removeLast(); if (b == 0L) throw WasmTrap("division by zero"); stack.addLast(java.lang.Long.remainderUnsigned(a, b)) }

                WasmOpCode.I32_LOAD -> {
                    val memArg = instruction.operands as Operands.MemArg
                    val base = stack.removeLast()
                    val address = checkedAddress("i32.load", base, memArg.offset, 4)
                    stack.addLast(memories[0].readI32(address).toLong())
                }
                WasmOpCode.I32_STORE -> {
                    val memArg = instruction.operands as Operands.MemArg
                    val value = stack.removeLast().toInt()
                    val base = stack.removeLast()
                    val address = checkedAddress("i32.store", base, memArg.offset, 4)
                    memories[0].writeI32(address, value)
                }
                WasmOpCode.I64_LOAD -> {
                    val memArg = instruction.operands as Operands.MemArg
                    val base = stack.removeLast()
                    val address = checkedAddress("i64.load", base, memArg.offset, 8)
                    stack.addLast(memories[0].readI64(address))
                }
                WasmOpCode.I64_STORE -> {
                    val memArg = instruction.operands as Operands.MemArg
                    val value = stack.removeLast()
                    val base = stack.removeLast()
                    val address = checkedAddress("i64.store", base, memArg.offset, 8)
                    memories[0].writeI64(address, value)
                }

                WasmOpCode.GLOBAL_GET -> stack.addLast(globals[(instruction.operands as Operands.Index).value])
                WasmOpCode.GLOBAL_SET -> globals[(instruction.operands as Operands.Index).value] = stack.removeLast()

                WasmOpCode.MEMORY_COPY -> {
                    val length = stack.removeLast().toInt()
                    val source = stack.removeLast().toInt()
                    val destination = stack.removeLast().toInt()
                    if (memories.isNotEmpty()) { memories[0].copy(destination, source, length) }
                }
                WasmOpCode.MEMORY_FILL -> {
                    val length = stack.removeLast().toInt()
                    val value = stack.removeLast().toByte()
                    val destination = stack.removeLast().toInt()
                    if (memories.isNotEmpty()) { memories[0].fill(destination, value, length) }
                }
                WasmOpCode.MEMORY_INIT -> {
                    val operands = instruction.operands as Operands.TwoIndex
                    val dataIndex = operands.first
                    val length = stack.removeLast().toInt()
                    val source = stack.removeLast().toInt()
                    val destination = stack.removeLast().toInt()
                    val segmentData = dataSegments[dataIndex]
                        ?: throw WasmTrap("memory.init: data segment $dataIndex dropped")
                    if (length > 0) {
                        if (source < 0 || source + length > segmentData.size) {
                            throw WasmTrap("memory.init: source out of bounds (src=$source, len=$length, segSize=${segmentData.size})")
                        }
                        if (memories.isNotEmpty()) {
                            memories[0].writeBytes(destination, segmentData.copyOfRange(source, source + length))
                        }
                    }
                }
                WasmOpCode.DATA_DROP -> {
                    val operands = instruction.operands as Operands.Index
                    dataSegments.remove(operands.value)
                }

                WasmOpCode.MEMORY_SIZE -> stack.addLast(if (memories.isNotEmpty()) memories[0].pages().toLong() else 0L)
                WasmOpCode.MEMORY_GROW -> {
                    val delta = stack.removeLast().toInt()
                    if (memories.isNotEmpty()) {
                        val result = memories[0].grow(delta)
                        stack.addLast(result.toLong())
                    } else {
                        stack.addLast(-1L)
                    }
                }

                WasmOpCode.I32_LOAD8_S -> {
                    val memArg = instruction.operands as Operands.MemArg
                    val base = stack.removeLast()
                    val address = checkedAddress("i32.load8_s", base, memArg.offset, 1)
                    stack.addLast(memories[0].readByte(address).toLong())
                }
                WasmOpCode.I32_LOAD8_U -> {
                    val memArg = instruction.operands as Operands.MemArg
                    val base = stack.removeLast()
                    val address = checkedAddress("i32.load8_u", base, memArg.offset, 1)
                    stack.addLast((memories[0].readByte(address).toInt() and 0xFF).toLong())
                }
                WasmOpCode.I32_LOAD16_S -> {
                    val memArg = instruction.operands as Operands.MemArg
                    val base = stack.removeLast()
                    val address = checkedAddress("i32.load16_s", base, memArg.offset, 2)
                    val low = memories[0].readByte(address).toInt() and 0xFF
                    val high = memories[0].readByte(address + 1).toInt()
                    stack.addLast(((high shl 8) or low).toLong())
                }
                WasmOpCode.I32_LOAD16_U -> {
                    val memArg = instruction.operands as Operands.MemArg
                    val base = stack.removeLast()
                    val address = checkedAddress("i32.load16_u", base, memArg.offset, 2)
                    val low = memories[0].readByte(address).toInt() and 0xFF
                    val high = memories[0].readByte(address + 1).toInt() and 0xFF
                    stack.addLast(((high shl 8) or low).toLong())
                }
                WasmOpCode.I32_STORE8 -> {
                    val memArg = instruction.operands as Operands.MemArg
                    val value = stack.removeLast().toByte()
                    val base = stack.removeLast()
                    val address = checkedAddress("i32.store8", base, memArg.offset, 1)
                    memories[0].writeByte(address, value)
                }
                WasmOpCode.I32_STORE16 -> {
                    val memArg = instruction.operands as Operands.MemArg
                    val value = stack.removeLast().toInt()
                    val base = stack.removeLast()
                    val address = checkedAddress("i32.store16", base, memArg.offset, 2)
                    memories[0].writeByte(address, (value and 0xFF).toByte())
                    memories[0].writeByte(address + 1, ((value shr 8) and 0xFF).toByte())
                }
                WasmOpCode.I64_LOAD8_S -> {
                    val memArg = instruction.operands as Operands.MemArg
                    val base = stack.removeLast()
                    val address = checkedAddress("i64.load8_s", base, memArg.offset, 1)
                    stack.addLast(memories[0].readByte(address).toLong())
                }
                WasmOpCode.I64_LOAD8_U -> {
                    val memArg = instruction.operands as Operands.MemArg
                    val base = stack.removeLast()
                    val address = checkedAddress("i64.load8_u", base, memArg.offset, 1)
                    stack.addLast((memories[0].readByte(address).toLong() and 0xFF))
                }
                WasmOpCode.I64_LOAD16_S -> {
                    val memArg = instruction.operands as Operands.MemArg
                    val base = stack.removeLast()
                    val address = checkedAddress("i64.load16_s", base, memArg.offset, 2)
                    val low = memories[0].readByte(address).toInt() and 0xFF
                    val high = memories[0].readByte(address + 1).toInt()
                    stack.addLast(((high shl 8) or low).toLong())
                }
                WasmOpCode.I64_LOAD16_U -> {
                    val memArg = instruction.operands as Operands.MemArg
                    val base = stack.removeLast()
                    val address = checkedAddress("i64.load16_u", base, memArg.offset, 2)
                    val low = memories[0].readByte(address).toInt() and 0xFF
                    val high = memories[0].readByte(address + 1).toInt() and 0xFF
                    stack.addLast(((high shl 8) or low).toLong())
                }
                WasmOpCode.I64_LOAD32_S -> {
                    val memArg = instruction.operands as Operands.MemArg
                    val base = stack.removeLast()
                    val address = checkedAddress("i64.load32_s", base, memArg.offset, 4)
                    stack.addLast(memories[0].readI32(address).toLong())
                }
                WasmOpCode.I64_LOAD32_U -> {
                    val memArg = instruction.operands as Operands.MemArg
                    val base = stack.removeLast()
                    val address = checkedAddress("i64.load32_u", base, memArg.offset, 4)
                    stack.addLast(memories[0].readI32(address).toLong() and 0xFFFFFFFFL)
                }
                WasmOpCode.I64_STORE8 -> {
                    val memArg = instruction.operands as Operands.MemArg
                    val value = stack.removeLast().toByte()
                    val base = stack.removeLast()
                    val address = checkedAddress("i64.store8", base, memArg.offset, 1)
                    memories[0].writeByte(address, value)
                }
                WasmOpCode.I64_STORE16 -> {
                    val memArg = instruction.operands as Operands.MemArg
                    val value = stack.removeLast().toInt()
                    val base = stack.removeLast()
                    val address = checkedAddress("i64.store16", base, memArg.offset, 2)
                    memories[0].writeByte(address, (value and 0xFF).toByte())
                    memories[0].writeByte(address + 1, ((value shr 8) and 0xFF).toByte())
                }
                WasmOpCode.I64_STORE32 -> {
                    val memArg = instruction.operands as Operands.MemArg
                    val value = stack.removeLast().toInt()
                    val base = stack.removeLast()
                    val address = checkedAddress("i64.store32", base, memArg.offset, 4)
                    memories[0].writeI32(address, value)
                }
                WasmOpCode.F32_LOAD -> {
                    val memArg = instruction.operands as Operands.MemArg
                    val base = stack.removeLast()
                    val address = checkedAddress("f32.load", base, memArg.offset, 4)
                    stack.addLast(memories[0].readI32(address).toLong() and 0xFFFFFFFFL)
                }
                WasmOpCode.F32_STORE -> {
                    val memArg = instruction.operands as Operands.MemArg
                    val value = stack.removeLast().toInt()
                    val base = stack.removeLast()
                    val address = checkedAddress("f32.store", base, memArg.offset, 4)
                    memories[0].writeI32(address, value)
                }
                WasmOpCode.F64_LOAD -> {
                    val memArg = instruction.operands as Operands.MemArg
                    val base = stack.removeLast()
                    val address = checkedAddress("f64.load", base, memArg.offset, 8)
                    stack.addLast(memories[0].readI64(address))
                }
                WasmOpCode.F64_STORE -> {
                    val memArg = instruction.operands as Operands.MemArg
                    val value = stack.removeLast()
                    val base = stack.removeLast()
                    val address = checkedAddress("f64.store", base, memArg.offset, 8)
                    memories[0].writeI64(address, value)
                }

                WasmOpCode.DROP -> stack.removeLast()
                WasmOpCode.SELECT -> {
                    val condition = stack.removeLast().toInt()
                    val falseValue = stack.removeLast()
                    val trueValue = stack.removeLast()
                    stack.addLast(if (condition != 0) trueValue else falseValue)
                }

                WasmOpCode.CALL -> {
                    val funcIndex = (instruction.operands as Operands.Index).value
                    val calleeType = resolveCalleeType(funcIndex)
                    val callArgs = LongArray(calleeType.params.size)
                    for (index in callArgs.indices.reversed()) {
                        callArgs[index] = stack.removeLast()
                    }
                    val results = call(funcIndex, callArgs)
                    for (result in results) {
                        stack.addLast(result)
                    }
                }

                WasmOpCode.CALL_INDIRECT -> {
                    val operands = instruction.operands as Operands.CallIndirect
                    val tableIndex = stack.removeLast().toInt()
                    val expectedType = wasmModule.types[operands.typeIndex]
                    if (tableIndex < 0 || tableIndex >= functionTable.size) {
                        throw WasmTrap("call_indirect: table index $tableIndex out of bounds (table size ${functionTable.size}) in ${functionName(currentFunctionIndex)}")
                    } else {
                        val funcIndex = functionTable[tableIndex]
                        if (funcIndex < 0) {
                            throw WasmTrap("call_indirect: null function at table slot $tableIndex in ${functionName(currentFunctionIndex)}")
                        }
                        val calleeType = resolveCalleeType(funcIndex)
                        if (calleeType.params.size != expectedType.params.size || calleeType.results.size != expectedType.results.size) {
                            throw WasmTrap("call_indirect: type mismatch at table slot $tableIndex in ${functionName(currentFunctionIndex)} (expected ${expectedType.params.size} params/${expectedType.results.size} results, got ${calleeType.params.size}/${calleeType.results.size})")
                        } else {
                            val callArgs = LongArray(calleeType.params.size)
                            for (index in callArgs.indices.reversed()) {
                                callArgs[index] = stack.removeLast()
                            }
                            val results = call(funcIndex, callArgs)
                            for (result in results) {
                                stack.addLast(result)
                            }
                        }
                    }
                }

                WasmOpCode.RETURN -> {
                    return if (frame.funcType.results.isEmpty()) {
                        longArrayOf()
                    } else {
                        longArrayOf(stack.removeLast())
                    }
                }

                WasmOpCode.BLOCK -> {
                    val openPc = programCounter - 1
                    val endPc = blockStructure.endMap[openPc]
                    val hasResult = blockHasResult(instruction)
                    controlStack.add(ControlFrame(ControlKind.BLOCK, endPc + 1, stack.size, hasResult))
                }
                WasmOpCode.TRY -> {
                    val openPc = programCounter - 1
                    val endPc = blockStructure.endMap[openPc]
                    val hasResult = blockHasResult(instruction)
                    val handlers = blockStructure.catchMap[openPc] ?: emptyList()
                    controlStack.add(ControlFrame(ControlKind.TRY, endPc + 1, stack.size, hasResult, handlers))
                }
                WasmOpCode.CATCH -> {
                    val tryFrame = controlStack.last()
                    val savedStackHeight = tryFrame.stackHeight
                    controlStack.removeAt(controlStack.size - 1)
                    if (tryFrame.hasResult) {
                        val result = if (stack.size > savedStackHeight) { stack.removeLast() } else { 0L }
                        while (stack.size > savedStackHeight) { stack.removeLast() }
                        stack.addLast(result)
                    } else {
                        while (stack.size > savedStackHeight) { stack.removeLast() }
                    }
                    programCounter = tryFrame.targetPc
                }
                WasmOpCode.CATCH_ALL -> {
                    val tryFrame = controlStack.last()
                    val savedStackHeight = tryFrame.stackHeight
                    controlStack.removeAt(controlStack.size - 1)
                    if (tryFrame.hasResult) {
                        val result = if (stack.size > savedStackHeight) { stack.removeLast() } else { 0L }
                        while (stack.size > savedStackHeight) { stack.removeLast() }
                        stack.addLast(result)
                    } else {
                        while (stack.size > savedStackHeight) { stack.removeLast() }
                    }
                    programCounter = tryFrame.targetPc
                }
                WasmOpCode.THROW -> {
                    val tagIndex = (instruction.operands as WasmInstruction.Operands.Index).value
                    val tagType = resolveTagType(tagIndex)
                    val values = LongArray(tagType.params.size) { stack.removeLast() }.reversedArray()
                    throw WasmException(tagIndex, values)
                }
                WasmOpCode.RETHROW -> {
                    val depth = (instruction.operands as WasmInstruction.Operands.Index).value
                    val targetIndex = controlStack.size - 1 - depth
                    val target = controlStack[targetIndex]
                    throw WasmException(target.catchHandlers.firstOrNull()?.tagIndex ?: -1, longArrayOf())
                }
                WasmOpCode.DELEGATE -> {
                    if (controlStack.isNotEmpty()) {
                        controlStack.removeAt(controlStack.size - 1)
                    }
                }
                WasmOpCode.LOOP -> {
                    val hasResult = blockHasResult(instruction)
                    controlStack.add(ControlFrame(ControlKind.LOOP, programCounter, stack.size, hasResult))
                }
                WasmOpCode.IF -> {
                    val condition = stack.removeLast().toInt()
                    val openPc = programCounter - 1
                    val endPc = blockStructure.endMap[openPc]
                    val elsePc = blockStructure.elseMap[openPc]
                    val hasResult = blockHasResult(instruction)
                    if (condition == 0) {
                        if (elsePc >= 0) {
                            programCounter = elsePc
                            controlStack.add(ControlFrame(ControlKind.IF, endPc + 1, stack.size, hasResult))
                        } else {
                            programCounter = endPc + 1
                        }
                    } else {
                        controlStack.add(ControlFrame(ControlKind.IF, endPc + 1, stack.size, hasResult))
                    }
                }
                WasmOpCode.ELSE -> {
                    val ifFrame = controlStack.last()
                    val savedStackHeight = ifFrame.stackHeight
                    controlStack.removeAt(controlStack.size - 1)
                    if (ifFrame.hasResult) {
                        val result = if (stack.size > savedStackHeight) { stack.removeLast() } else { 0L }
                        while (stack.size > savedStackHeight) {
                            stack.removeLast()
                        }
                        stack.addLast(result)
                    } else {
                        while (stack.size > savedStackHeight) {
                            stack.removeLast()
                        }
                    }
                    programCounter = ifFrame.targetPc
                }
                WasmOpCode.END -> {
                    if (controlStack.isNotEmpty()) {
                        val frame = controlStack.removeAt(controlStack.size - 1)
                        if (frame.hasResult) {
                            val result = if (stack.size > frame.stackHeight) { stack.removeLast() } else { 0L }
                            while (stack.size > frame.stackHeight) {
                                stack.removeLast()
                            }
                            stack.addLast(result)
                        }
                    }
                }
                WasmOpCode.BR -> {
                    val depth = (instruction.operands as Operands.Index).value
                    programCounter = branchTo(controlStack, depth, stack)
                }
                WasmOpCode.BR_IF -> {
                    val depth = (instruction.operands as Operands.Index).value
                    val condition = stack.removeLast().toInt()
                    if (condition != 0) {
                        programCounter = branchTo(controlStack, depth, stack)
                    }
                }
                WasmOpCode.BR_TABLE -> {
                    val operands = instruction.operands as Operands.BrTable
                    val index = stack.removeLast().toInt()
                    val depth = if (index >= 0 && index < operands.labels.size) {
                        operands.labels[index]
                    } else {
                        operands.default
                    }
                    programCounter = branchTo(controlStack, depth, stack)
                }

                WasmOpCode.SELECT_TYPED -> {
                    val condition = stack.removeLast().toInt()
                    val falseValue = stack.removeLast()
                    val trueValue = stack.removeLast()
                    stack.addLast(if (condition != 0) trueValue else falseValue)
                }

                WasmOpCode.I32_TRUNC_SAT_F32_S -> {
                    val value = f(stack.removeLast()).toDouble()
                    stack.addLast(i32TruncSatS(value).toLong())
                }
                WasmOpCode.I32_TRUNC_SAT_F32_U -> {
                    val value = f(stack.removeLast()).toDouble()
                    stack.addLast(i32TruncSatU(value))
                }
                WasmOpCode.I32_TRUNC_SAT_F64_S -> {
                    val value = d(stack.removeLast())
                    stack.addLast(i32TruncSatS(value).toLong())
                }
                WasmOpCode.I32_TRUNC_SAT_F64_U -> {
                    val value = d(stack.removeLast())
                    stack.addLast(i32TruncSatU(value))
                }
                WasmOpCode.I64_TRUNC_SAT_F32_S -> {
                    val value = f(stack.removeLast()).toDouble()
                    stack.addLast(i64TruncSatS(value))
                }
                WasmOpCode.I64_TRUNC_SAT_F32_U -> {
                    val value = f(stack.removeLast()).toDouble()
                    stack.addLast(i64TruncSatU(value))
                }
                WasmOpCode.I64_TRUNC_SAT_F64_S -> {
                    val value = d(stack.removeLast())
                    stack.addLast(i64TruncSatS(value))
                }
                WasmOpCode.I64_TRUNC_SAT_F64_U -> {
                    val value = d(stack.removeLast())
                    stack.addLast(i64TruncSatU(value))
                }

                WasmOpCode.UNREACHABLE -> throw WasmTrap("unreachable in ${functionName(currentFunctionIndex)} PC=$programCounter depth=$callDepth")
                WasmOpCode.NOP -> { }
                else -> throw WasmTrap("unimplemented opcode: ${instruction.opcode} in ${functionName(currentFunctionIndex)} at PC=$programCounter")
            }

            } catch (wasmException: WasmException) {
                programCounter = handleException(wasmException, controlStack, stack)
                    ?: throw wasmException
            }
        }

        return if (frame.funcType.results.isEmpty()) {
            longArrayOf()
        } else if (stack.isNotEmpty()) {
            longArrayOf(stack.removeLast())
        } else {
            longArrayOf(0L)
        }
    }

    private fun branchTo(controlStack: MutableList<ControlFrame>, depth: Int, stack: ArrayDeque<Long>): Int {
        val targetIndex = controlStack.size - 1 - depth
        val target = controlStack[targetIndex]

        if (target.kind != ControlKind.LOOP && target.hasResult) {
            val result = if (stack.size > target.stackHeight) { stack.removeLast() } else { 0L }
            while (stack.size > target.stackHeight) {
                stack.removeLast()
            }
            stack.addLast(result)
        } else {
            while (stack.size > target.stackHeight) {
                stack.removeLast()
            }
        }

        while (controlStack.size > targetIndex + 1) {
            controlStack.removeAt(controlStack.size - 1)
        }

        if (target.kind == ControlKind.LOOP) {
            return target.targetPc
        } else {
            controlStack.removeAt(controlStack.size - 1)
            return target.targetPc
        }
    }

    private fun blockHasResult(instruction: WasmInstruction): Boolean {
        val blockType = instruction.operands as? Operands.BlockType ?: return false
        return blockType.type != -64
    }

    private fun getBlockStructure(localIndex: Int, instructions: List<WasmInstruction>): BlockStructure = blockStructureCache.getOrPut(localIndex) {
        buildBlockStructure(instructions)
    }

    private fun buildBlockStructure(instructions: List<WasmInstruction>): BlockStructure {
        val endMap = IntArray(instructions.size) { -1 }
        val elseMap = IntArray(instructions.size) { -1 }
        val catchMap = mutableMapOf<Int, MutableList<CatchHandler>>()
        val blockStack = ArrayDeque<Int>()

        for (index in instructions.indices) {
            val opcode = instructions[index].opcode
            if (opcode == WasmOpCode.BLOCK || opcode == WasmOpCode.LOOP || opcode == WasmOpCode.IF || opcode == WasmOpCode.TRY) {
                blockStack.addLast(index)
            } else if (opcode == WasmOpCode.ELSE) {
                if (blockStack.isNotEmpty()) {
                    val ifPc = blockStack.last()
                    elseMap[ifPc] = index + 1
                }
            } else if (opcode == WasmOpCode.CATCH) {
                if (blockStack.isNotEmpty()) {
                    val tryPc = blockStack.last()
                    val tagIndex = (instructions[index].operands as WasmInstruction.Operands.Index).value
                    catchMap.getOrPut(tryPc) { mutableListOf() }
                        .add(CatchHandler(tagIndex, index + 1))
                }
            } else if (opcode == WasmOpCode.CATCH_ALL) {
                if (blockStack.isNotEmpty()) {
                    val tryPc = blockStack.last()
                    catchMap.getOrPut(tryPc) { mutableListOf() }
                        .add(CatchHandler(-1, index + 1, isCatchAll = true))
                }
            } else if (opcode == WasmOpCode.DELEGATE) {
                if (blockStack.isNotEmpty()) {
                    val tryPc = blockStack.removeLast()
                    endMap[tryPc] = index
                }
            } else if (opcode == WasmOpCode.END) {
                if (blockStack.isNotEmpty()) {
                    val openPc = blockStack.removeLast()
                    endMap[openPc] = index
                }
            }
        }
        return BlockStructure(endMap, elseMap, catchMap)
    }

    private fun handleException(
        exception: WasmException,
        controlStack: MutableList<ControlFrame>,
        stack: ArrayDeque<Long>,
    ): Int? {
        for (frameIndex in controlStack.indices.reversed()) {
            val frame = controlStack[frameIndex]
            if (frame.kind != ControlKind.TRY) {
                continue
            }
            for (handler in frame.catchHandlers) {
                if (handler.isCatchAll || handler.tagIndex == exception.tagIndex) {
                    while (stack.size > frame.stackHeight) {
                        stack.removeLast()
                    }
                    if (!handler.isCatchAll) {
                        for (value in exception.values) {
                            stack.addLast(value)
                        }
                    }
                    while (controlStack.size > frameIndex + 1) {
                        controlStack.removeAt(controlStack.size - 1)
                    }
                    return handler.pc
                }
            }
        }
        return null
    }

    private fun resolveTagType(tagIndex: Int): WasmModule.FuncType {
        val tagImports = wasmModule.imports.filterIsInstance<WasmModule.Import.Tag>()
        if (tagIndex < tagImports.size) {
            return wasmModule.types[tagImports[tagIndex].typeIndex]
        }
        return WasmModule.FuncType(emptyList(), emptyList())
    }

    private fun resolveCalleeType(funcIndex: Int): WasmModule.FuncType = calleeTypeCache.getOrPut(funcIndex) {
        val importCount = wasmModule.importedFunctionCount
        if (funcIndex < importCount) {
            wasmModule.types[importedFunctions[funcIndex].typeIndex]
        } else {
            val localIndex = funcIndex - importCount
            wasmModule.types[wasmModule.functions[localIndex].typeIndex]
        }
    }

    private fun checkedAddress(opcode: String, base: Long, offset: Int, size: Int): Int {
        val address = base.toInt() + offset
        if (address < 0 || address + size > memories[0].sizeBytes()) {
            throw WasmTrap(
                "$opcode OOB: base=$base (0x${java.lang.Long.toHexString(base)}), " +
                    "offset=$offset (0x${Integer.toHexString(offset)}), " +
                    "effective=$address (0x${Integer.toHexString(address)}), " +
                    "memSize=${memories[0].sizeBytes()} (${memories[0].pages()} pages), " +
                    "func=${functionName(currentFunctionIndex)}"
            )
        }
        return address
    }

    private fun d(bits: Long): Double = java.lang.Double.longBitsToDouble(bits)
    private fun l(value: Double): Long = java.lang.Double.doubleToRawLongBits(value)
    private fun f(bits: Long): Float = java.lang.Float.intBitsToFloat(bits.toInt())
    private fun fi(value: Float): Long = java.lang.Float.floatToRawIntBits(value).toLong() and 0xFFFFFFFFL

    private fun truncateFloat(value: Float): Float {
        if (value.isNaN() || value.isInfinite()) { return value }
        return if (value >= 0) { kotlin.math.floor(value.toDouble()).toFloat() } else { kotlin.math.ceil(value.toDouble()).toFloat() }
    }

    private fun truncateDouble(value: Double): Double {
        if (value.isNaN() || value.isInfinite()) { return value }
        return if (value >= 0) { kotlin.math.floor(value) } else { kotlin.math.ceil(value) }
    }

    private fun nearestFloat(value: Float): Float {
        if (value.isNaN() || value.isInfinite()) { return value }
        return Math.rint(value.toDouble()).toFloat()
    }

    private fun nearestDouble(value: Double): Double {
        if (value.isNaN() || value.isInfinite()) { return value }
        return Math.rint(value)
    }

    private fun wasmMinF32(a: Float, b: Float): Float {
        if (a.isNaN() || b.isNaN()) { return Float.NaN }
        if (a == 0.0f && b == 0.0f) { return if (java.lang.Float.floatToRawIntBits(a) < 0 || java.lang.Float.floatToRawIntBits(b) < 0) { -0.0f } else { 0.0f } }
        return if (a < b) { a } else { b }
    }

    private fun wasmMaxF32(a: Float, b: Float): Float {
        if (a.isNaN() || b.isNaN()) { return Float.NaN }
        if (a == 0.0f && b == 0.0f) { return if (java.lang.Float.floatToRawIntBits(a) < 0 && java.lang.Float.floatToRawIntBits(b) < 0) { -0.0f } else { 0.0f } }
        return if (a > b) { a } else { b }
    }

    private fun wasmMinF64(a: Double, b: Double): Double {
        if (a.isNaN() || b.isNaN()) { return Double.NaN }
        if (a == 0.0 && b == 0.0) { return if (java.lang.Double.doubleToRawLongBits(a) < 0 || java.lang.Double.doubleToRawLongBits(b) < 0) { -0.0 } else { 0.0 } }
        return if (a < b) { a } else { b }
    }

    private fun wasmMaxF64(a: Double, b: Double): Double {
        if (a.isNaN() || b.isNaN()) { return Double.NaN }
        if (a == 0.0 && b == 0.0) { return if (java.lang.Double.doubleToRawLongBits(a) < 0 && java.lang.Double.doubleToRawLongBits(b) < 0) { -0.0 } else { 0.0 } }
        return if (a > b) { a } else { b }
    }

    private fun i32TruncSatS(value: Double): Int {
        if (value.isNaN()) { return 0 }
        if (value >= Int.MAX_VALUE.toDouble()) { return Int.MAX_VALUE }
        if (value <= Int.MIN_VALUE.toDouble()) { return Int.MIN_VALUE }
        return value.toInt()
    }

    private fun i32TruncSatU(value: Double): Long {
        if (value.isNaN()) { return 0L }
        if (value >= 4294967295.0) { return 0xFFFFFFFFL }
        if (value <= 0.0) { return 0L }
        return value.toLong() and 0xFFFFFFFFL
    }

    private fun i64TruncSatS(value: Double): Long {
        if (value.isNaN()) { return 0L }
        if (value >= Long.MAX_VALUE.toDouble()) { return Long.MAX_VALUE }
        if (value <= Long.MIN_VALUE.toDouble()) { return Long.MIN_VALUE }
        return value.toLong()
    }

    private fun i64TruncSatU(value: Double): Long {
        if (value.isNaN()) { return 0L }
        if (value <= 0.0) { return 0L }
        if (value >= 18446744073709551615.0) { return -1L }
        if (value < 9.223372036854776E18) { return value.toLong() }
        return (value - 9.223372036854776E18).toLong() + Long.MIN_VALUE
    }
}

class InterpreterFrame(
    val locals: LongArray,
    val funcType: WasmModule.FuncType,
    val stack: ArrayDeque<Long> = ArrayDeque(),
)

enum class ControlKind { BLOCK, LOOP, IF, TRY }

class ControlFrame(
    val kind: ControlKind,
    val targetPc: Int,
    val stackHeight: Int,
    val hasResult: Boolean = false,
    val catchHandlers: List<CatchHandler> = emptyList(),
)

class CatchHandler(
    val tagIndex: Int,
    val pc: Int,
    val isCatchAll: Boolean = false,
)

class BlockStructure(
    val endMap: IntArray,
    val elseMap: IntArray,
    val catchMap: Map<Int, List<CatchHandler>>,
)
