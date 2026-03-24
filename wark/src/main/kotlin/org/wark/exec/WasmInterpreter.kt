package org.wark.exec

import org.kgen.target.wasm.WasmValueType
import org.kgen.target.wasm.disasm.WasmDisassembler
import org.kgen.target.wasm.disasm.WasmInstruction
import org.kgen.target.wasm.disasm.WasmInstruction.Operands
import org.kgen.target.wasm.module.WasmModule
import org.wark.HostFunction
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
            val importDecl = wasmModule.imports.filterIsInstance<WasmModule.Import.Func>()[functionIndex]
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
        onFunctionEntry?.let { /* already invoked above */ }

        val previousFunctionIndex = currentFunctionIndex
        currentFunctionIndex = functionIndex
        callDepth++
        val frame = InterpreterFrame(locals, funcType)
        try {
            return execute(frame, instructions)
        } finally {
            callDepth--
            currentFunctionIndex = previousFunctionIndex
        }
    }

    fun functionName(index: Int): String {
        return wasmModule.functionName(index) ?: "func_${index - wasmModule.importedFunctionCount}"
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
    var onFunctionEntry: ((Int, LongArray) -> Unit)? = null

    /** Current call depth for debugger display. */
    var callDepth: Int = 0
        private set

    /** Snapshot of state at the current instruction, for debugger inspection. */
    data class StepInfo(
        val functionIndex: Int,
        val programCounter: Int,
        val mnemonic: String,
        val stack: List<Long>,
        val locals: LongArray,
        val callDepth: Int,
        val totalInstructions: Long,
    )

    /** Paused state — set when onStep returns false. */
    var paused = false
        private set

    private var currentFunctionIndex = -1

    private fun execute(frame: InterpreterFrame, instructions: List<WasmInstruction>): LongArray {
        val stack = frame.stack
        val locals = frame.locals
        var programCounter = 0

        val controlStack = mutableListOf<ControlFrame>()
        val functionEndPc = instructions.indexOfLast { it.opcode.mnemonic == "end" }
        controlStack.add(ControlFrame(ControlKind.BLOCK, if (functionEndPc >= 0) { functionEndPc } else { instructions.size }, 0))

        while (programCounter < instructions.size) {
            totalInstructions++
            if (totalInstructions > instructionLimit) {
                throw WasmTrap("instruction limit exceeded ($instructionLimit)")
            }

            val stepCallback = onStep
            if (stepCallback != null) {
                val instruction = instructions[programCounter]
                val info = StepInfo(
                    currentFunctionIndex, programCounter, instruction.opcode.mnemonic,
                    stack.toList(), locals.clone(), callDepth, totalInstructions,
                )
                if (!stepCallback(info)) {
                    paused = true
                    throw WasmTrap("debugger pause")
                }
            }

            val instruction = instructions[programCounter]
            programCounter++

            when (instruction.opcode.mnemonic) {
                "i32.const" -> stack.addLast((instruction.operands as Operands.I32).value.toLong())
                "i64.const" -> stack.addLast((instruction.operands as Operands.I64).value)
                "f32.const" -> stack.addLast(java.lang.Float.floatToRawIntBits((instruction.operands as Operands.F32).value).toLong())
                "f64.const" -> stack.addLast(java.lang.Double.doubleToRawLongBits((instruction.operands as Operands.F64).value))

                "local.get" -> stack.addLast(locals[(instruction.operands as Operands.Index).value])
                "local.set" -> locals[(instruction.operands as Operands.Index).value] = stack.removeLast()
                "local.tee" -> locals[(instruction.operands as Operands.Index).value] = stack.last()

                "i32.add" -> { val b = stack.removeLast().toInt(); val a = stack.removeLast().toInt(); stack.addLast((a + b).toLong()) }
                "i32.sub" -> { val b = stack.removeLast().toInt(); val a = stack.removeLast().toInt(); stack.addLast((a - b).toLong()) }
                "i32.mul" -> { val b = stack.removeLast().toInt(); val a = stack.removeLast().toInt(); stack.addLast((a * b).toLong()) }
                "i32.div_s" -> { val b = stack.removeLast().toInt(); val a = stack.removeLast().toInt(); if (b == 0) throw WasmTrap("division by zero"); stack.addLast((a / b).toLong()) }
                "i32.div_u" -> { val b = stack.removeLast().toInt().toLong() and 0xFFFFFFFFL; val a = stack.removeLast().toInt().toLong() and 0xFFFFFFFFL; if (b == 0L) throw WasmTrap("division by zero"); stack.addLast(a / b) }
                "i32.rem_s" -> { val b = stack.removeLast().toInt(); val a = stack.removeLast().toInt(); if (b == 0) throw WasmTrap("division by zero"); stack.addLast((a % b).toLong()) }
                "i32.rem_u" -> { val b = stack.removeLast().toInt().toLong() and 0xFFFFFFFFL; val a = stack.removeLast().toInt().toLong() and 0xFFFFFFFFL; if (b == 0L) throw WasmTrap("division by zero"); stack.addLast(a % b) }
                "i32.and" -> { val b = stack.removeLast().toInt(); val a = stack.removeLast().toInt(); stack.addLast((a and b).toLong()) }
                "i32.or" -> { val b = stack.removeLast().toInt(); val a = stack.removeLast().toInt(); stack.addLast((a or b).toLong()) }
                "i32.xor" -> { val b = stack.removeLast().toInt(); val a = stack.removeLast().toInt(); stack.addLast((a xor b).toLong()) }
                "i32.shl" -> { val b = stack.removeLast().toInt(); val a = stack.removeLast().toInt(); stack.addLast((a shl (b and 31)).toLong()) }
                "i32.shr_s" -> { val b = stack.removeLast().toInt(); val a = stack.removeLast().toInt(); stack.addLast((a shr (b and 31)).toLong()) }
                "i32.shr_u" -> { val b = stack.removeLast().toInt(); val a = stack.removeLast().toInt(); stack.addLast((a ushr (b and 31)).toLong()) }
                "i32.rotl" -> { val b = stack.removeLast().toInt(); val a = stack.removeLast().toInt(); stack.addLast(Integer.rotateLeft(a, b).toLong()) }
                "i32.rotr" -> { val b = stack.removeLast().toInt(); val a = stack.removeLast().toInt(); stack.addLast(Integer.rotateRight(a, b).toLong()) }

                "i64.add" -> { val b = stack.removeLast(); val a = stack.removeLast(); stack.addLast(a + b) }
                "i64.sub" -> { val b = stack.removeLast(); val a = stack.removeLast(); stack.addLast(a - b) }
                "i64.mul" -> { val b = stack.removeLast(); val a = stack.removeLast(); stack.addLast(a * b) }
                "i64.div_s" -> { val b = stack.removeLast(); val a = stack.removeLast(); if (b == 0L) throw WasmTrap("division by zero"); stack.addLast(a / b) }
                "i64.rem_s" -> { val b = stack.removeLast(); val a = stack.removeLast(); if (b == 0L) throw WasmTrap("division by zero"); stack.addLast(a % b) }
                "i64.and" -> { val b = stack.removeLast(); val a = stack.removeLast(); stack.addLast(a and b) }
                "i64.or" -> { val b = stack.removeLast(); val a = stack.removeLast(); stack.addLast(a or b) }
                "i64.xor" -> { val b = stack.removeLast(); val a = stack.removeLast(); stack.addLast(a xor b) }
                "i64.shl" -> { val b = stack.removeLast().toInt(); val a = stack.removeLast(); stack.addLast(a shl (b and 63)) }
                "i64.shr_s" -> { val b = stack.removeLast().toInt(); val a = stack.removeLast(); stack.addLast(a shr (b and 63)) }
                "i64.shr_u" -> { val b = stack.removeLast().toInt(); val a = stack.removeLast(); stack.addLast(a ushr (b and 63)) }
                "i64.rotl" -> { val b = stack.removeLast().toInt(); val a = stack.removeLast(); stack.addLast(java.lang.Long.rotateLeft(a, b)) }
                "i64.rotr" -> { val b = stack.removeLast().toInt(); val a = stack.removeLast(); stack.addLast(java.lang.Long.rotateRight(a, b)) }

                "f32.add" -> { val b = f(stack.removeLast()); val a = f(stack.removeLast()); stack.addLast(fi(a + b)) }
                "f32.sub" -> { val b = f(stack.removeLast()); val a = f(stack.removeLast()); stack.addLast(fi(a - b)) }
                "f32.mul" -> { val b = f(stack.removeLast()); val a = f(stack.removeLast()); stack.addLast(fi(a * b)) }
                "f32.div" -> { val b = f(stack.removeLast()); val a = f(stack.removeLast()); stack.addLast(fi(a / b)) }
                "f32.neg" -> { stack.addLast(fi(-f(stack.removeLast()))) }
                "f32.abs" -> { stack.addLast(fi(kotlin.math.abs(f(stack.removeLast())))) }
                "f32.sqrt" -> { stack.addLast(fi(kotlin.math.sqrt(f(stack.removeLast()).toDouble()).toFloat())) }
                "f32.ceil" -> { stack.addLast(fi(kotlin.math.ceil(f(stack.removeLast()).toDouble()).toFloat())) }
                "f32.floor" -> { stack.addLast(fi(kotlin.math.floor(f(stack.removeLast()).toDouble()).toFloat())) }
                "f32.trunc" -> { stack.addLast(fi(truncateFloat(f(stack.removeLast())))) }
                "f32.nearest" -> { stack.addLast(fi(nearestFloat(f(stack.removeLast())))) }
                "f32.min" -> { val b = f(stack.removeLast()); val a = f(stack.removeLast()); stack.addLast(fi(wasmMinF32(a, b))) }
                "f32.max" -> { val b = f(stack.removeLast()); val a = f(stack.removeLast()); stack.addLast(fi(wasmMaxF32(a, b))) }
                "f32.copysign" -> { val b = f(stack.removeLast()); val a = f(stack.removeLast()); stack.addLast(fi(Math.copySign(a, b))) }
                "f32.eq" -> { val b = f(stack.removeLast()); val a = f(stack.removeLast()); stack.addLast(if (a == b) 1L else 0L) }
                "f32.ne" -> { val b = f(stack.removeLast()); val a = f(stack.removeLast()); stack.addLast(if (a != b) 1L else 0L) }
                "f32.lt" -> { val b = f(stack.removeLast()); val a = f(stack.removeLast()); stack.addLast(if (a < b) 1L else 0L) }
                "f32.gt" -> { val b = f(stack.removeLast()); val a = f(stack.removeLast()); stack.addLast(if (a > b) 1L else 0L) }
                "f32.le" -> { val b = f(stack.removeLast()); val a = f(stack.removeLast()); stack.addLast(if (a <= b) 1L else 0L) }
                "f32.ge" -> { val b = f(stack.removeLast()); val a = f(stack.removeLast()); stack.addLast(if (a >= b) 1L else 0L) }

                "f64.add" -> { val b = d(stack.removeLast()); val a = d(stack.removeLast()); stack.addLast(l(a + b)) }
                "f64.sub" -> { val b = d(stack.removeLast()); val a = d(stack.removeLast()); stack.addLast(l(a - b)) }
                "f64.mul" -> { val b = d(stack.removeLast()); val a = d(stack.removeLast()); stack.addLast(l(a * b)) }
                "f64.div" -> { val b = d(stack.removeLast()); val a = d(stack.removeLast()); stack.addLast(l(a / b)) }
                "f64.neg" -> { stack.addLast(l(-d(stack.removeLast()))) }
                "f64.abs" -> { stack.addLast(l(kotlin.math.abs(d(stack.removeLast())))) }
                "f64.sqrt" -> { stack.addLast(l(kotlin.math.sqrt(d(stack.removeLast())))) }
                "f64.ceil" -> { stack.addLast(l(kotlin.math.ceil(d(stack.removeLast())))) }
                "f64.floor" -> { stack.addLast(l(kotlin.math.floor(d(stack.removeLast())))) }
                "f64.trunc" -> { stack.addLast(l(truncateDouble(d(stack.removeLast())))) }
                "f64.nearest" -> { stack.addLast(l(nearestDouble(d(stack.removeLast())))) }
                "f64.min" -> { val b = d(stack.removeLast()); val a = d(stack.removeLast()); stack.addLast(l(wasmMinF64(a, b))) }
                "f64.max" -> { val b = d(stack.removeLast()); val a = d(stack.removeLast()); stack.addLast(l(wasmMaxF64(a, b))) }
                "f64.copysign" -> { val b = d(stack.removeLast()); val a = d(stack.removeLast()); stack.addLast(l(Math.copySign(a, b))) }
                "f64.eq" -> { val b = d(stack.removeLast()); val a = d(stack.removeLast()); stack.addLast(if (a == b) 1L else 0L) }
                "f64.ne" -> { val b = d(stack.removeLast()); val a = d(stack.removeLast()); stack.addLast(if (a != b) 1L else 0L) }
                "f64.lt" -> { val b = d(stack.removeLast()); val a = d(stack.removeLast()); stack.addLast(if (a < b) 1L else 0L) }
                "f64.gt" -> { val b = d(stack.removeLast()); val a = d(stack.removeLast()); stack.addLast(if (a > b) 1L else 0L) }
                "f64.le" -> { val b = d(stack.removeLast()); val a = d(stack.removeLast()); stack.addLast(if (a <= b) 1L else 0L) }
                "f64.ge" -> { val b = d(stack.removeLast()); val a = d(stack.removeLast()); stack.addLast(if (a >= b) 1L else 0L) }

                "i64.eqz" -> stack.addLast(if (stack.removeLast() == 0L) 1L else 0L)
                "i64.eq" -> { val b = stack.removeLast(); val a = stack.removeLast(); stack.addLast(if (a == b) 1L else 0L) }
                "i64.ne" -> { val b = stack.removeLast(); val a = stack.removeLast(); stack.addLast(if (a != b) 1L else 0L) }
                "i64.lt_s" -> { val b = stack.removeLast(); val a = stack.removeLast(); stack.addLast(if (a < b) 1L else 0L) }
                "i64.gt_s" -> { val b = stack.removeLast(); val a = stack.removeLast(); stack.addLast(if (a > b) 1L else 0L) }
                "i64.le_s" -> { val b = stack.removeLast(); val a = stack.removeLast(); stack.addLast(if (a <= b) 1L else 0L) }
                "i64.ge_s" -> { val b = stack.removeLast(); val a = stack.removeLast(); stack.addLast(if (a >= b) 1L else 0L) }

                "i32.wrap_i64" -> stack.addLast(stack.removeLast() and 0xFFFFFFFFL)
                "i64.extend_i32_s" -> stack.addLast(stack.removeLast().toInt().toLong())
                "i64.extend_i32_u" -> stack.addLast(stack.removeLast() and 0xFFFFFFFFL)
                "f64.convert_i32_s" -> stack.addLast(l(stack.removeLast().toInt().toDouble()))
                "f64.convert_i32_u" -> stack.addLast(l((stack.removeLast().toInt().toLong() and 0xFFFFFFFFL).toDouble()))
                "f64.convert_i64_s" -> stack.addLast(l(stack.removeLast().toDouble()))
                "f64.convert_i64_u" -> {
                    val value = stack.removeLast()
                    val result = if (value >= 0) { value.toDouble() } else { (value ushr 1).toDouble() * 2.0 + (value and 1L).toDouble() }
                    stack.addLast(l(result))
                }
                "f32.convert_i32_s" -> stack.addLast(fi(stack.removeLast().toInt().toFloat()))
                "f32.convert_i32_u" -> stack.addLast(fi((stack.removeLast().toInt().toLong() and 0xFFFFFFFFL).toFloat()))
                "f32.convert_i64_s" -> stack.addLast(fi(stack.removeLast().toFloat()))
                "f32.convert_i64_u" -> {
                    val value = stack.removeLast()
                    val result = if (value >= 0) { value.toFloat() } else { (value ushr 1).toFloat() * 2.0f + (value and 1L).toFloat() }
                    stack.addLast(fi(result))
                }
                "i32.trunc_f64_s" -> stack.addLast(d(stack.removeLast()).toInt().toLong())
                "i32.trunc_f64_u" -> {
                    val value = d(stack.removeLast())
                    if (value.isNaN()) { throw WasmTrap("invalid conversion to integer") }
                    if (value >= 4294967296.0 || value < 0.0) { throw WasmTrap("integer overflow") }
                    stack.addLast(value.toLong() and 0xFFFFFFFFL)
                }
                "i32.trunc_f32_s" -> stack.addLast(f(stack.removeLast()).toInt().toLong())
                "i32.trunc_f32_u" -> {
                    val value = f(stack.removeLast())
                    if (value.isNaN()) { throw WasmTrap("invalid conversion to integer") }
                    if (value >= 4294967296.0f || value < 0.0f) { throw WasmTrap("integer overflow") }
                    stack.addLast(value.toLong() and 0xFFFFFFFFL)
                }
                "i64.trunc_f64_s" -> {
                    val value = d(stack.removeLast())
                    if (value.isNaN()) { throw WasmTrap("invalid conversion to integer") }
                    stack.addLast(value.toLong())
                }
                "i64.trunc_f64_u" -> {
                    val value = d(stack.removeLast())
                    if (value.isNaN()) { throw WasmTrap("invalid conversion to integer") }
                    if (value < 0.0 || value >= 1.8446744073709552E19) { throw WasmTrap("integer overflow") }
                    stack.addLast(if (value < 9.223372036854776E18) { value.toLong() } else { (value - 9.223372036854776E18).toLong() + Long.MIN_VALUE })
                }
                "i64.trunc_f32_s" -> {
                    val value = f(stack.removeLast())
                    if (value.isNaN()) { throw WasmTrap("invalid conversion to integer") }
                    stack.addLast(value.toLong())
                }
                "i64.trunc_f32_u" -> {
                    val value = f(stack.removeLast()).toDouble()
                    if (value.isNaN()) { throw WasmTrap("invalid conversion to integer") }
                    if (value < 0.0 || value >= 1.8446744073709552E19) { throw WasmTrap("integer overflow") }
                    stack.addLast(if (value < 9.223372036854776E18) { value.toLong() } else { (value - 9.223372036854776E18).toLong() + Long.MIN_VALUE })
                }
                "f64.promote_f32" -> stack.addLast(l(f(stack.removeLast()).toDouble()))
                "f32.demote_f64" -> stack.addLast(fi(d(stack.removeLast()).toFloat()))
                "i32.reinterpret_f32" -> { }
                "f32.reinterpret_i32" -> { }
                "i64.reinterpret_f64" -> { }
                "f64.reinterpret_i64" -> { }
                "i32.extend8_s" -> stack.addLast(stack.removeLast().toByte().toLong())
                "i32.extend16_s" -> stack.addLast(stack.removeLast().toShort().toLong())
                "i64.extend8_s" -> stack.addLast(stack.removeLast().toByte().toLong())
                "i64.extend16_s" -> stack.addLast(stack.removeLast().toShort().toLong())
                "i64.extend32_s" -> stack.addLast(stack.removeLast().toInt().toLong())

                "i32.eqz" -> stack.addLast(if (stack.removeLast().toInt() == 0) 1L else 0L)
                "i32.eq" -> { val b = stack.removeLast().toInt(); val a = stack.removeLast().toInt(); stack.addLast(if (a == b) 1L else 0L) }
                "i32.ne" -> { val b = stack.removeLast().toInt(); val a = stack.removeLast().toInt(); stack.addLast(if (a != b) 1L else 0L) }
                "i32.lt_s" -> { val b = stack.removeLast().toInt(); val a = stack.removeLast().toInt(); stack.addLast(if (a < b) 1L else 0L) }
                "i32.lt_u" -> { val b = stack.removeLast().toInt().toLong() and 0xFFFFFFFFL; val a = stack.removeLast().toInt().toLong() and 0xFFFFFFFFL; stack.addLast(if (a < b) 1L else 0L) }
                "i32.gt_s" -> { val b = stack.removeLast().toInt(); val a = stack.removeLast().toInt(); stack.addLast(if (a > b) 1L else 0L) }
                "i32.gt_u" -> { val b = stack.removeLast().toInt().toLong() and 0xFFFFFFFFL; val a = stack.removeLast().toInt().toLong() and 0xFFFFFFFFL; stack.addLast(if (a > b) 1L else 0L) }
                "i32.le_s" -> { val b = stack.removeLast().toInt(); val a = stack.removeLast().toInt(); stack.addLast(if (a <= b) 1L else 0L) }
                "i32.le_u" -> { val b = stack.removeLast().toInt().toLong() and 0xFFFFFFFFL; val a = stack.removeLast().toInt().toLong() and 0xFFFFFFFFL; stack.addLast(if (a <= b) 1L else 0L) }
                "i32.ge_s" -> { val b = stack.removeLast().toInt(); val a = stack.removeLast().toInt(); stack.addLast(if (a >= b) 1L else 0L) }
                "i32.ge_u" -> { val b = stack.removeLast().toInt().toLong() and 0xFFFFFFFFL; val a = stack.removeLast().toInt().toLong() and 0xFFFFFFFFL; stack.addLast(if (a >= b) 1L else 0L) }
                "i32.clz" -> stack.addLast(Integer.numberOfLeadingZeros(stack.removeLast().toInt()).toLong())
                "i32.ctz" -> stack.addLast(Integer.numberOfTrailingZeros(stack.removeLast().toInt()).toLong())
                "i32.popcnt" -> stack.addLast(Integer.bitCount(stack.removeLast().toInt()).toLong())
                "i64.clz" -> stack.addLast(java.lang.Long.numberOfLeadingZeros(stack.removeLast()).toLong())
                "i64.ctz" -> stack.addLast(java.lang.Long.numberOfTrailingZeros(stack.removeLast()).toLong())
                "i64.popcnt" -> stack.addLast(java.lang.Long.bitCount(stack.removeLast()).toLong())
                "i64.lt_u" -> { val b = stack.removeLast(); val a = stack.removeLast(); stack.addLast(if (java.lang.Long.compareUnsigned(a, b) < 0) 1L else 0L) }
                "i64.gt_u" -> { val b = stack.removeLast(); val a = stack.removeLast(); stack.addLast(if (java.lang.Long.compareUnsigned(a, b) > 0) 1L else 0L) }
                "i64.le_u" -> { val b = stack.removeLast(); val a = stack.removeLast(); stack.addLast(if (java.lang.Long.compareUnsigned(a, b) <= 0) 1L else 0L) }
                "i64.ge_u" -> { val b = stack.removeLast(); val a = stack.removeLast(); stack.addLast(if (java.lang.Long.compareUnsigned(a, b) >= 0) 1L else 0L) }
                "i64.div_u" -> { val b = stack.removeLast(); val a = stack.removeLast(); if (b == 0L) throw WasmTrap("division by zero"); stack.addLast(java.lang.Long.divideUnsigned(a, b)) }
                "i64.rem_u" -> { val b = stack.removeLast(); val a = stack.removeLast(); if (b == 0L) throw WasmTrap("division by zero"); stack.addLast(java.lang.Long.remainderUnsigned(a, b)) }

                "i32.load" -> {
                    val memArg = instruction.operands as Operands.MemArg
                    val base = stack.removeLast()
                    val address = checkedAddress("i32.load", base, memArg.offset, 4)
                    stack.addLast(memories[0].readI32(address).toLong())
                }
                "i32.store" -> {
                    val memArg = instruction.operands as Operands.MemArg
                    val value = stack.removeLast().toInt()
                    val base = stack.removeLast()
                    val address = checkedAddress("i32.store", base, memArg.offset, 4)
                    memories[0].writeI32(address, value)
                }
                "i64.load" -> {
                    val memArg = instruction.operands as Operands.MemArg
                    val base = stack.removeLast()
                    val address = checkedAddress("i64.load", base, memArg.offset, 8)
                    stack.addLast(memories[0].readI64(address))
                }
                "i64.store" -> {
                    val memArg = instruction.operands as Operands.MemArg
                    val value = stack.removeLast()
                    val base = stack.removeLast()
                    val address = checkedAddress("i64.store", base, memArg.offset, 8)
                    memories[0].writeI64(address, value)
                }

                "global.get" -> stack.addLast(globals[(instruction.operands as Operands.Index).value])
                "global.set" -> globals[(instruction.operands as Operands.Index).value] = stack.removeLast()

                "memory.copy" -> {
                    val length = stack.removeLast().toInt()
                    val source = stack.removeLast().toInt()
                    val destination = stack.removeLast().toInt()
                    if (memories.isNotEmpty()) { memories[0].copy(destination, source, length) }
                }
                "memory.fill" -> {
                    val length = stack.removeLast().toInt()
                    val value = stack.removeLast().toByte()
                    val destination = stack.removeLast().toInt()
                    if (memories.isNotEmpty()) { memories[0].fill(destination, value, length) }
                }
                "memory.init" -> {
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
                "data.drop" -> {
                    val operands = instruction.operands as Operands.Index
                    dataSegments.remove(operands.value)
                }

                "memory.size" -> stack.addLast(if (memories.isNotEmpty()) memories[0].pages().toLong() else 0L)
                "memory.grow" -> {
                    val delta = stack.removeLast().toInt()
                    if (memories.isNotEmpty()) {
                        val result = memories[0].grow(delta)
                        stack.addLast(result.toLong())
                    } else {
                        stack.addLast(-1L)
                    }
                }

                "i32.load8_s" -> {
                    val memArg = instruction.operands as Operands.MemArg
                    val base = stack.removeLast()
                    val address = checkedAddress("i32.load8_s", base, memArg.offset, 1)
                    stack.addLast(memories[0].readByte(address).toLong())
                }
                "i32.load8_u" -> {
                    val memArg = instruction.operands as Operands.MemArg
                    val base = stack.removeLast()
                    val address = checkedAddress("i32.load8_u", base, memArg.offset, 1)
                    stack.addLast((memories[0].readByte(address).toInt() and 0xFF).toLong())
                }
                "i32.load16_s" -> {
                    val memArg = instruction.operands as Operands.MemArg
                    val base = stack.removeLast()
                    val address = checkedAddress("i32.load16_s", base, memArg.offset, 2)
                    val low = memories[0].readByte(address).toInt() and 0xFF
                    val high = memories[0].readByte(address + 1).toInt()
                    stack.addLast(((high shl 8) or low).toLong())
                }
                "i32.load16_u" -> {
                    val memArg = instruction.operands as Operands.MemArg
                    val base = stack.removeLast()
                    val address = checkedAddress("i32.load16_u", base, memArg.offset, 2)
                    val low = memories[0].readByte(address).toInt() and 0xFF
                    val high = memories[0].readByte(address + 1).toInt() and 0xFF
                    stack.addLast(((high shl 8) or low).toLong())
                }
                "i32.store8" -> {
                    val memArg = instruction.operands as Operands.MemArg
                    val value = stack.removeLast().toByte()
                    val base = stack.removeLast()
                    val address = checkedAddress("i32.store8", base, memArg.offset, 1)
                    memories[0].writeByte(address, value)
                }
                "i32.store16" -> {
                    val memArg = instruction.operands as Operands.MemArg
                    val value = stack.removeLast().toInt()
                    val base = stack.removeLast()
                    val address = checkedAddress("i32.store16", base, memArg.offset, 2)
                    memories[0].writeByte(address, (value and 0xFF).toByte())
                    memories[0].writeByte(address + 1, ((value shr 8) and 0xFF).toByte())
                }
                "i64.load8_s" -> {
                    val memArg = instruction.operands as Operands.MemArg
                    val base = stack.removeLast()
                    val address = checkedAddress("i64.load8_s", base, memArg.offset, 1)
                    stack.addLast(memories[0].readByte(address).toLong())
                }
                "i64.load8_u" -> {
                    val memArg = instruction.operands as Operands.MemArg
                    val base = stack.removeLast()
                    val address = checkedAddress("i64.load8_u", base, memArg.offset, 1)
                    stack.addLast((memories[0].readByte(address).toLong() and 0xFF))
                }
                "i64.load16_s" -> {
                    val memArg = instruction.operands as Operands.MemArg
                    val base = stack.removeLast()
                    val address = checkedAddress("i64.load16_s", base, memArg.offset, 2)
                    val low = memories[0].readByte(address).toInt() and 0xFF
                    val high = memories[0].readByte(address + 1).toInt()
                    stack.addLast(((high shl 8) or low).toLong())
                }
                "i64.load16_u" -> {
                    val memArg = instruction.operands as Operands.MemArg
                    val base = stack.removeLast()
                    val address = checkedAddress("i64.load16_u", base, memArg.offset, 2)
                    val low = memories[0].readByte(address).toInt() and 0xFF
                    val high = memories[0].readByte(address + 1).toInt() and 0xFF
                    stack.addLast(((high shl 8) or low).toLong())
                }
                "i64.load32_s" -> {
                    val memArg = instruction.operands as Operands.MemArg
                    val base = stack.removeLast()
                    val address = checkedAddress("i64.load32_s", base, memArg.offset, 4)
                    stack.addLast(memories[0].readI32(address).toLong())
                }
                "i64.load32_u" -> {
                    val memArg = instruction.operands as Operands.MemArg
                    val base = stack.removeLast()
                    val address = checkedAddress("i64.load32_u", base, memArg.offset, 4)
                    stack.addLast(memories[0].readI32(address).toLong() and 0xFFFFFFFFL)
                }
                "i64.store8" -> {
                    val memArg = instruction.operands as Operands.MemArg
                    val value = stack.removeLast().toByte()
                    val base = stack.removeLast()
                    val address = checkedAddress("i64.store8", base, memArg.offset, 1)
                    memories[0].writeByte(address, value)
                }
                "i64.store16" -> {
                    val memArg = instruction.operands as Operands.MemArg
                    val value = stack.removeLast().toInt()
                    val base = stack.removeLast()
                    val address = checkedAddress("i64.store16", base, memArg.offset, 2)
                    memories[0].writeByte(address, (value and 0xFF).toByte())
                    memories[0].writeByte(address + 1, ((value shr 8) and 0xFF).toByte())
                }
                "i64.store32" -> {
                    val memArg = instruction.operands as Operands.MemArg
                    val value = stack.removeLast().toInt()
                    val base = stack.removeLast()
                    val address = checkedAddress("i64.store32", base, memArg.offset, 4)
                    memories[0].writeI32(address, value)
                }
                "f32.load" -> {
                    val memArg = instruction.operands as Operands.MemArg
                    val base = stack.removeLast()
                    val address = checkedAddress("f32.load", base, memArg.offset, 4)
                    stack.addLast(memories[0].readI32(address).toLong() and 0xFFFFFFFFL)
                }
                "f32.store" -> {
                    val memArg = instruction.operands as Operands.MemArg
                    val value = stack.removeLast().toInt()
                    val base = stack.removeLast()
                    val address = checkedAddress("f32.store", base, memArg.offset, 4)
                    memories[0].writeI32(address, value)
                }
                "f64.load" -> {
                    val memArg = instruction.operands as Operands.MemArg
                    val base = stack.removeLast()
                    val address = checkedAddress("f64.load", base, memArg.offset, 8)
                    stack.addLast(memories[0].readI64(address))
                }
                "f64.store" -> {
                    val memArg = instruction.operands as Operands.MemArg
                    val value = stack.removeLast()
                    val base = stack.removeLast()
                    val address = checkedAddress("f64.store", base, memArg.offset, 8)
                    memories[0].writeI64(address, value)
                }

                "drop" -> stack.removeLast()
                "select" -> {
                    val condition = stack.removeLast().toInt()
                    val falseValue = stack.removeLast()
                    val trueValue = stack.removeLast()
                    stack.addLast(if (condition != 0) trueValue else falseValue)
                }

                "call" -> {
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

                "call_indirect" -> {
                    val operands = instruction.operands as Operands.CallIndirect
                    val tableIndex = stack.removeLast().toInt()
                    if (tableIndex < 0 || tableIndex >= functionTable.size) {
                        throw WasmTrap("call_indirect: table index out of bounds: $tableIndex")
                    }
                    val funcIndex = functionTable[tableIndex]
                    val calleeType = resolveCalleeType(funcIndex)
                    val expectedType = wasmModule.types[operands.typeIndex]
                    if (calleeType.params.size != expectedType.params.size || calleeType.results.size != expectedType.results.size) {
                        throw WasmTrap("call_indirect: type mismatch")
                    }
                    val callArgs = LongArray(calleeType.params.size)
                    for (index in callArgs.indices.reversed()) {
                        callArgs[index] = stack.removeLast()
                    }
                    val results = call(funcIndex, callArgs)
                    for (result in results) {
                        stack.addLast(result)
                    }
                }

                "return" -> {
                    return if (frame.funcType.results.isEmpty()) {
                        longArrayOf()
                    } else {
                        longArrayOf(stack.removeLast())
                    }
                }

                "block" -> {
                    val endPc = findMatchingEnd(instructions, programCounter - 1)
                    controlStack.add(ControlFrame(ControlKind.BLOCK, endPc + 1, stack.size))
                }
                "loop" -> {
                    val endPc = findMatchingEnd(instructions, programCounter - 1)
                    controlStack.add(ControlFrame(ControlKind.LOOP, programCounter, stack.size))
                }
                "if" -> {
                    val condition = stack.removeLast().toInt()
                    val endPc = findMatchingEnd(instructions, programCounter - 1)
                    val elsePc = findMatchingElse(instructions, programCounter - 1)
                    if (condition == 0) {
                        programCounter = elsePc ?: endPc
                        if (elsePc == null) {
                            programCounter = endPc + 1
                        }
                    }
                    controlStack.add(ControlFrame(ControlKind.IF, endPc + 1, stack.size))
                }
                "else" -> {
                    val frame2 = controlStack.last()
                    programCounter = frame2.targetPc
                    controlStack.removeAt(controlStack.size - 1)
                    val endPc = programCounter - 1
                    controlStack.add(ControlFrame(ControlKind.IF, endPc + 1, stack.size))
                }
                "end" -> {
                    if (controlStack.isNotEmpty()) {
                        controlStack.removeAt(controlStack.size - 1)
                    }
                }
                "br" -> {
                    val depth = (instruction.operands as Operands.Index).value
                    programCounter = branchTo(controlStack, depth, stack)
                }
                "br_if" -> {
                    val depth = (instruction.operands as Operands.Index).value
                    val condition = stack.removeLast().toInt()
                    if (condition != 0) {
                        programCounter = branchTo(controlStack, depth, stack)
                    }
                }
                "br_table" -> {
                    val operands = instruction.operands as Operands.BrTable
                    val index = stack.removeLast().toInt()
                    val depth = if (index >= 0 && index < operands.labels.size) {
                        operands.labels[index]
                    } else {
                        operands.default
                    }
                    programCounter = branchTo(controlStack, depth, stack)
                }

                "unreachable" -> throw WasmTrap("unreachable")
                "nop" -> { }
                else -> throw WasmTrap("unimplemented opcode: ${instruction.opcode.mnemonic} in ${functionName(currentFunctionIndex)} at PC=$programCounter")
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

    private fun findMatchingEnd(instructions: List<WasmInstruction>, startPc: Int): Int {
        var depth = 0
        for (index in startPc until instructions.size) {
            val mnemonic = instructions[index].opcode.mnemonic
            if (mnemonic == "block" || mnemonic == "loop" || mnemonic == "if") {
                depth++
            } else if (mnemonic == "end") {
                depth--
                if (depth == 0) {
                    return index
                }
            }
        }
        return instructions.size
    }

    private fun findMatchingElse(instructions: List<WasmInstruction>, startPc: Int): Int? {
        var depth = 0
        for (index in startPc until instructions.size) {
            val mnemonic = instructions[index].opcode.mnemonic
            if (mnemonic == "block" || mnemonic == "loop" || mnemonic == "if") {
                depth++
            } else if (mnemonic == "end") {
                depth--
                if (depth == 0) {
                    return null
                }
            } else if (mnemonic == "else" && depth == 1) {
                return index + 1
            }
        }
        return null
    }

    private fun resolveCalleeType(funcIndex: Int): WasmModule.FuncType {
        val importCount = wasmModule.importedFunctionCount
        return if (funcIndex < importCount) {
            val importDecl = wasmModule.imports.filterIsInstance<WasmModule.Import.Func>()[funcIndex]
            wasmModule.types[importDecl.typeIndex]
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
}

class InterpreterFrame(
    val locals: LongArray,
    val funcType: WasmModule.FuncType,
    val stack: ArrayDeque<Long> = ArrayDeque(),
)

enum class ControlKind { BLOCK, LOOP, IF }

class ControlFrame(
    val kind: ControlKind,
    val targetPc: Int,
    val stackHeight: Int,
)
