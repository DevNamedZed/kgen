package org.kgen.target.clr.codegen

import org.kgen.target.clr.asm.CilAssembler
import org.kgen.target.clr.asm.CilLabel
import org.kgen.target.clr.asm.CilToken
import org.kgen.ir.*
import org.kgen.ir.instructions.*
import org.kgen.codegen.CodeGenOptions
import org.kgen.codegen.CodeGenerator

/**
 * Translates an IR [Module] into CIL bytecode.
 *
 * Each IR function produces a separate CIL method body (raw bytecode).
 * The output is a map of method name to CIL bytes, serialized as a simple
 * container format. For now, single-function modules return just the method body bytes.
 *
 * ```java
 * var gen = new CilCodeGenerator();
 * byte[] cilBytes = gen.generate(irModule);
 * ```
 */
class CilCodeGenerator : CodeGenerator {

    override val targetName: String = "msil"

    override fun generate(module: Module, options: CodeGenOptions): ByteArray {
        val methods = mutableMapOf<String, ByteArray>()
        val allExternalRefs = mutableListOf<ExternalMethodRef>()
        for (fn in module.functions) {
            if (fn.isExternal) continue
            val emitter = MethodEmitter(fn, module.functions)
            emitter.emit()
            methods[fn.name] = emitter.assembler.toByteArray()
            allExternalRefs.addAll(emitter.getExternalMethodRefs())
        }
        externalMethodRefs = allExternalRefs.distinctBy { "${it.typeNamespace}.${it.typeName}::${it.methodName}" }

        if (methods.size == 1) return methods.values.first()

        // For multiple methods, concatenate with a simple header:
        // [count:4] [nameLen:4 name:bytes codeLen:4 code:bytes]*
        val out = java.io.ByteArrayOutputStream()
        val dos = java.io.DataOutputStream(out)
        dos.writeInt(methods.size)
        for ((name, code) in methods) {
            val nameBytes = name.toByteArray(Charsets.UTF_8)
            dos.writeInt(nameBytes.size)
            dos.write(nameBytes)
            dos.writeInt(code.size)
            dos.write(code)
        }
        dos.flush()
        return out.toByteArray()
    }

    /**
     * Generate CIL bytecode for a single function.
     */
    fun generateMethod(fn: IrFunction, moduleFunctions: List<IrFunction> = listOf(fn)): ByteArray {
        val emitter = MethodEmitter(fn, moduleFunctions)
        emitter.emit()
        return emitter.assembler.toByteArray()
    }

    /**
     * Generate CIL bytecode and return the assembler for inspection.
     */
    fun generateMethodAssembler(fn: IrFunction, moduleFunctions: List<IrFunction> = listOf(fn)): CilAssembler {
        val emitter = MethodEmitter(fn, moduleFunctions)
        emitter.emit()
        return emitter.assembler
    }

    /**
     * Describes an external method reference needed by the generated CIL bytecode.
     * The assembly builder must create matching MemberRef metadata entries.
     */
    data class ExternalMethodRef(
        val typeName: String,       // e.g. "Math"
        val typeNamespace: String,  // e.g. "System"
        val assemblyRef: String,    // e.g. "System.Runtime"
        val methodName: String,     // e.g. "Sqrt"
        val returnType: Type,
        val paramTypes: List<Type>,
    )

    /** External method references used by the last generate() call. */
    var externalMethodRefs: List<ExternalMethodRef> = emptyList()
        private set

    private class MethodEmitter(
        private val fn: IrFunction,
        private val moduleFunctions: List<IrFunction>,
    ) {
        val assembler = CilAssembler()
        private val locals = mutableMapOf<String, Int>()
        private var nextLocal = 0
        private var nextParamIndex = 0
        private val blockLabels = mutableMapOf<String, CilLabel>()

        // External method references — keyed by "Namespace.Type::Method" to deduplicate
        private val externalMethods = mutableMapOf<String, ExternalMethodRef>()
        private var nextMemberRefIndex = 1 // MemberRef table is 1-based

        /** Get all external method references this method needs. */
        fun getExternalMethodRefs(): List<ExternalMethodRef> = externalMethods.values.toList()

        /**
         * Get or create a MemberRef token for an external BCL method.
         * Returns a CilToken that can be used with assembler.call().
         */
        private fun externalMethodToken(ref: ExternalMethodRef): CilToken {
            val key = "${ref.typeNamespace}.${ref.typeName}::${ref.methodName}"
            if (key !in externalMethods) {
                externalMethods[key] = ref
            }
            // Find the 1-based index
            val index = externalMethods.keys.indexOf(key) + 1
            return CilToken.memberRef(index)
        }

        private fun mathMethodToken(name: String, returnType: Type, paramTypes: List<Type>): CilToken {
            return externalMethodToken(ExternalMethodRef(
                typeName = "Math",
                typeNamespace = "System",
                assemblyRef = "System.Runtime",
                methodName = name,
                returnType = returnType,
                paramTypes = paramTypes,
            ))
        }

        private fun bitOpsMethodToken(name: String, returnType: Type, paramTypes: List<Type>): CilToken {
            return externalMethodToken(ExternalMethodRef(
                typeName = "BitOperations",
                typeNamespace = "System.Numerics",
                assemblyRef = "System.Runtime",
                methodName = name,
                returnType = returnType,
                paramTypes = paramTypes,
            ))
        }

        fun emit() {
            // Map parameters to argument indices
            for (param in fn.params) {
                locals[param.name] = nextParamIndex
                nextParamIndex++
            }

            // Pre-allocate labels for all blocks
            for (block in fn.blocks) {
                blockLabels[block.label] = assembler.defineLabel()
            }

            // Pre-allocate locals for instruction results
            for (block in fn.blocks) {
                for (inst in block.instructions) {
                    val ref = inst.result
                    if (ref != null && ref.name !in locals) {
                        locals[ref.name] = nextLocal + nextParamIndex
                        nextLocal++
                    }
                }
            }

            // Emit blocks
            for (block in fn.blocks) {
                assembler.markLabel(blockLabels[block.label]!!)
                for (inst in block.instructions) {
                    emitInstruction(inst)
                }
            }
        }

        private fun labelFor(name: String): CilLabel {
            return blockLabels[name] ?: error("No label for block $name")
        }

        private fun localSlot(name: String): Int {
            return locals[name] ?: error("No local for $name")
        }

        private fun isParam(name: String): Boolean {
            val slot = locals[name] ?: return false
            return slot < nextParamIndex
        }

        private fun emitInstruction(inst: Instruction) {
            when (inst) {
                is Add -> emitBinOp(inst.dest, inst.lhs, inst.rhs) { assembler.add() }
                is Sub -> emitBinOp(inst.dest, inst.lhs, inst.rhs) { assembler.sub() }
                is Mul -> emitBinOp(inst.dest, inst.lhs, inst.rhs) { assembler.mul() }
                is SDiv -> emitBinOp(inst.dest, inst.lhs, inst.rhs) { assembler.div() }
                is UDiv -> emitBinOp(inst.dest, inst.lhs, inst.rhs) { assembler.divUn() }
                is SRem -> emitBinOp(inst.dest, inst.lhs, inst.rhs) { assembler.rem() }
                is URem -> emitBinOp(inst.dest, inst.lhs, inst.rhs) { assembler.remUn() }
                is And -> emitBinOp(inst.dest, inst.lhs, inst.rhs) { assembler.and() }
                is Or -> emitBinOp(inst.dest, inst.lhs, inst.rhs) { assembler.or() }
                is Xor -> emitBinOp(inst.dest, inst.lhs, inst.rhs) { assembler.xor() }
                is Shl -> emitBinOp(inst.dest, inst.lhs, inst.rhs) { assembler.shl() }
                is LShr -> emitBinOp(inst.dest, inst.lhs, inst.rhs) { assembler.shrUn() }
                is AShr -> emitBinOp(inst.dest, inst.lhs, inst.rhs) { assembler.shr() }

                is FAdd -> emitBinOp(inst.dest, inst.lhs, inst.rhs) { assembler.add() }
                is FSub -> emitBinOp(inst.dest, inst.lhs, inst.rhs) { assembler.sub() }
                is FMul -> emitBinOp(inst.dest, inst.lhs, inst.rhs) { assembler.mul() }
                is FDiv -> emitBinOp(inst.dest, inst.lhs, inst.rhs) { assembler.div() }

                is Neg -> {
                    pushValue(inst.operand)
                    assembler.neg()
                    storeResult(inst.dest)
                }

                is FNeg -> {
                    pushValue(inst.operand)
                    assembler.neg()
                    storeResult(inst.dest)
                }

                is Not -> {
                    pushValue(inst.operand)
                    assembler.not()
                    storeResult(inst.dest)
                }

                is ICmp -> emitICmp(inst)
                is FCmp -> emitFCmp(inst)

                is UIToFP -> {
                    pushValue(inst.value)
                    assembler.convRUn()
                    when (inst.dest.type) {
                        Type.F32 -> assembler.convR4()
                        else -> assembler.convR8()
                    }
                    storeResult(inst.dest)
                }

                is FPToUI -> {
                    pushValue(inst.value)
                    when (inst.dest.type) {
                        Type.I64 -> assembler.convU8()
                        Type.I16 -> assembler.convU2()
                        Type.I8 -> assembler.convU1()
                        else -> assembler.convU4()
                    }
                    storeResult(inst.dest)
                }

                is Load -> {
                    pushValue(inst.ptr)
                    when (inst.loadType) {
                        Type.I8 -> assembler.ldindI1()
                        Type.I16 -> assembler.ldindI2()
                        Type.I32 -> assembler.ldindI4()
                        Type.I64 -> assembler.ldindI8()
                        Type.F32 -> assembler.ldindR4()
                        Type.F64 -> assembler.ldindR8()
                        else -> assembler.ldindRef()
                    }
                    storeResult(inst.dest)
                }

                is Store -> {
                    pushValue(inst.ptr)
                    pushValue(inst.value)
                    when (inst.value.type) {
                        Type.I8, Type.I1 -> assembler.stindI1()
                        Type.I16 -> assembler.stindI2()
                        Type.I32 -> assembler.stindI4()
                        Type.I64 -> assembler.stindI8()
                        Type.F32 -> assembler.stindR4()
                        Type.F64 -> assembler.stindR8()
                        else -> assembler.stindRef()
                    }
                }

                is Switch -> {
                    // CIL has no built-in switch in our assembler yet — use if-chain
                    for ((caseVal, target) in inst.cases) {
                        pushValue(inst.value)
                        pushValue(caseVal)
                        assembler.ceq()
                        assembler.brtrue(labelFor(target.label))
                    }
                    assembler.br(labelFor(inst.defaultTarget.label))
                }

                is GCSafepoint -> {} // no-op on managed runtime
                is GCRoot -> {} // no-op on managed runtime

                is Ret -> {
                    val value = inst.value
                    if (value != null) {
                        pushValue(value)
                    }
                    assembler.ret()
                }

                is Br -> {
                    assembler.br(labelFor(inst.target.label))
                }

                is CondBr -> {
                    pushValue(inst.condition)
                    assembler.brtrue(labelFor(inst.trueTarget.label))
                    assembler.br(labelFor(inst.falseTarget.label))
                }

                is Call -> emitCall(inst)

                is SExt -> {
                    pushValue(inst.value)
                    emitConversion(inst.value.type, inst.dest.type, signed = true)
                    storeResult(inst.dest)
                }

                is ZExt -> {
                    pushValue(inst.value)
                    emitConversion(inst.value.type, inst.dest.type, signed = false)
                    storeResult(inst.dest)
                }

                is FTrunc -> {
                    pushValue(inst.operand)
                    emitTruncation(inst.operand.type, inst.dest.type)
                    storeResult(inst.dest)
                }

                is IntTrunc -> {
                    pushValue(inst.value)
                    emitTruncation(inst.value.type, inst.dest.type)
                    storeResult(inst.dest)
                }

                is SIToFP -> {
                    pushValue(inst.value)
                    when (inst.dest.type) {
                        Type.F32 -> assembler.convR4()
                        Type.F64 -> assembler.convR8()
                        else -> assembler.convR8()
                    }
                    storeResult(inst.dest)
                }

                is FPToSI -> {
                    pushValue(inst.value)
                    when (inst.dest.type) {
                        Type.I32 -> assembler.convI4()
                        Type.I64 -> assembler.convI8()
                        Type.I16 -> assembler.convI2()
                        Type.I8 -> assembler.convI1()
                        else -> assembler.convI4()
                    }
                    storeResult(inst.dest)
                }

                is FPExt -> {
                    pushValue(inst.value)
                    assembler.convR8()
                    storeResult(inst.dest)
                }

                is FPTrunc -> {
                    pushValue(inst.value)
                    assembler.convR4()
                    storeResult(inst.dest)
                }

                is Select -> {
                    val elseLabel = assembler.defineLabel()
                    val endLabel = assembler.defineLabel()
                    pushValue(inst.condition)
                    assembler.brfalse(elseLabel)
                    pushValue(inst.trueValue)
                    assembler.br(endLabel)
                    assembler.markLabel(elseLabel)
                    pushValue(inst.falseValue)
                    assembler.markLabel(endLabel)
                    storeResult(inst.dest)
                }

                is PtrToInt -> {
                    pushValue(inst.value)
                    when (inst.toType) {
                        Type.I64 -> assembler.convU8()
                        Type.I32 -> assembler.convU4()
                        else -> assembler.convI()
                    }
                    storeResult(inst.dest)
                }

                is IntToPtr -> {
                    pushValue(inst.value)
                    assembler.convI()
                    storeResult(inst.dest)
                }

                is BitCast -> {
                    // No-op on CIL stack for same-size types
                    pushValue(inst.value)
                    storeResult(inst.dest)
                }

                is Unreachable -> {
                    // Throw a null reference exception
                    assembler.ldnull()
                    assembler.throw_()
                }

                is Trap -> {
                    assembler.break_()
                }

                is DebugTrap -> {
                    assembler.break_()
                }
                is DebugLoc -> {}
                is DebugValue -> {}
                is DebugDeclare -> {}

                is Sqrt -> {
                    pushValue(inst.operand)
                    if (inst.operand.type == Type.F32) assembler.convR8()
                    assembler.call(mathMethodToken("Sqrt", Type.F64, listOf(Type.F64)))
                    if (inst.dest.type == Type.F32) assembler.convR4()
                    storeResult(inst.dest)
                }

                is Ceil -> {
                    pushValue(inst.operand)
                    if (inst.operand.type == Type.F32) assembler.convR8()
                    assembler.call(mathMethodToken("Ceiling", Type.F64, listOf(Type.F64)))
                    if (inst.dest.type == Type.F32) assembler.convR4()
                    storeResult(inst.dest)
                }

                is Floor -> {
                    pushValue(inst.operand)
                    if (inst.operand.type == Type.F32) assembler.convR8()
                    assembler.call(mathMethodToken("Floor", Type.F64, listOf(Type.F64)))
                    if (inst.dest.type == Type.F32) assembler.convR4()
                    storeResult(inst.dest)
                }

                is Round -> {
                    pushValue(inst.operand)
                    if (inst.operand.type == Type.F32) assembler.convR8()
                    assembler.call(mathMethodToken("Round", Type.F64, listOf(Type.F64)))
                    if (inst.dest.type == Type.F32) assembler.convR4()
                    storeResult(inst.dest)
                }

                is FAbs -> {
                    pushValue(inst.operand)
                    if (inst.operand.type == Type.F32) assembler.convR8()
                    assembler.call(mathMethodToken("Abs", Type.F64, listOf(Type.F64)))
                    if (inst.dest.type == Type.F32) assembler.convR4()
                    storeResult(inst.dest)
                }

                is FMin -> {
                    pushValue(inst.lhs)
                    pushValue(inst.rhs)
                    if (inst.lhs.type == Type.F32) {
                        assembler.convR8()
                        // swap to convert both — push rhs back, convert lhs
                    }
                    assembler.call(mathMethodToken("Min", Type.F64, listOf(Type.F64, Type.F64)))
                    if (inst.dest.type == Type.F32) assembler.convR4()
                    storeResult(inst.dest)
                }

                is FMax -> {
                    pushValue(inst.lhs)
                    pushValue(inst.rhs)
                    assembler.call(mathMethodToken("Max", Type.F64, listOf(Type.F64, Type.F64)))
                    if (inst.dest.type == Type.F32) assembler.convR4()
                    storeResult(inst.dest)
                }

                is Ctlz -> {
                    pushValue(inst.operand)
                    if (inst.operand.type == Type.I64) {
                        assembler.call(bitOpsMethodToken("LeadingZeroCount", Type.I32, listOf(Type.I64)))
                    } else {
                        assembler.call(bitOpsMethodToken("LeadingZeroCount", Type.I32, listOf(Type.I32)))
                    }
                    storeResult(inst.dest)
                }

                is Cttz -> {
                    pushValue(inst.operand)
                    if (inst.operand.type == Type.I64) {
                        assembler.call(bitOpsMethodToken("TrailingZeroCount", Type.I32, listOf(Type.I64)))
                    } else {
                        assembler.call(bitOpsMethodToken("TrailingZeroCount", Type.I32, listOf(Type.I32)))
                    }
                    storeResult(inst.dest)
                }

                is Ctpop -> {
                    pushValue(inst.operand)
                    if (inst.operand.type == Type.I64) {
                        assembler.call(bitOpsMethodToken("PopCount", Type.I32, listOf(Type.I64)))
                    } else {
                        assembler.call(bitOpsMethodToken("PopCount", Type.I32, listOf(Type.I32)))
                    }
                    storeResult(inst.dest)
                }

                is BSwap -> {
                    pushValue(inst.operand)
                    if (inst.operand.type == Type.I64) {
                        assembler.call(bitOpsMethodToken("ReverseEndianness", Type.I64, listOf(Type.I64)))
                    } else {
                        assembler.call(bitOpsMethodToken("ReverseEndianness", Type.I32, listOf(Type.I32)))
                    }
                    storeResult(inst.dest)
                }

                is MemCpy -> {
                    pushValue(inst.dst)
                    pushValue(inst.src)
                    pushValue(inst.len)
                    assembler.cpblk()
                }

                is MemSet -> {
                    pushValue(inst.dst)
                    pushValue(inst.value)
                    pushValue(inst.len)
                    assembler.initblk()
                }

                is SMin -> {
                    pushValue(inst.lhs); pushValue(inst.rhs)
                    val paramType = if (inst.lhs.type == Type.I64) Type.I64 else Type.I32
                    assembler.call(mathMethodToken("Min", paramType, listOf(paramType, paramType)))
                    storeResult(inst.dest)
                }

                is SMax -> {
                    pushValue(inst.lhs); pushValue(inst.rhs)
                    val paramType = if (inst.lhs.type == Type.I64) Type.I64 else Type.I32
                    assembler.call(mathMethodToken("Max", paramType, listOf(paramType, paramType)))
                    storeResult(inst.dest)
                }

                is CopySign -> {
                    pushValue(inst.magnitude); pushValue(inst.sign)
                    val paramType = if (inst.magnitude.type == Type.F32) Type.F32 else Type.F64
                    assembler.call(mathMethodToken("CopySign", paramType, listOf(paramType, paramType)))
                    storeResult(inst.dest)
                }

                is GetElementPtr -> emitGetElementPtr(inst)
                is ExtractValue -> emitExtractValue(inst)
                is InsertValue -> emitInsertValue(inst)

                is Abs -> {
                    pushValue(inst.operand)
                    val paramType = if (inst.operand.type == Type.I64) Type.I64 else Type.I32
                    assembler.call(mathMethodToken("Abs", paramType, listOf(paramType)))
                    storeResult(inst.dest)
                }

                is UMin -> {
                    pushValue(inst.lhs); pushValue(inst.rhs)
                    val paramType = if (inst.lhs.type == Type.I64) Type.I64 else Type.I32
                    assembler.call(mathMethodToken("Min", paramType, listOf(paramType, paramType)))
                    storeResult(inst.dest)
                }

                is UMax -> {
                    pushValue(inst.lhs); pushValue(inst.rhs)
                    val paramType = if (inst.lhs.type == Type.I64) Type.I64 else Type.I32
                    assembler.call(mathMethodToken("Max", paramType, listOf(paramType, paramType)))
                    storeResult(inst.dest)
                }

                is FMA -> {
                    pushValue(inst.a); pushValue(inst.b)
                    assembler.mul()
                    pushValue(inst.c)
                    assembler.add()
                    storeResult(inst.dest)
                }

                is FRem -> {
                    pushValue(inst.lhs); pushValue(inst.rhs)
                    assembler.rem()
                    storeResult(inst.dest)
                }

                is MemMove -> {
                    pushValue(inst.dst); pushValue(inst.src); pushValue(inst.len)
                    assembler.cpblk()
                }

                is BitReverse -> {
                    error("BitReverse is not supported on CIL (no BCL equivalent)")
                }

                is Rotl -> {
                    pushValue(inst.value); pushValue(inst.amount)
                    val paramType = if (inst.value.type == Type.I64) Type.I64 else Type.I32
                    assembler.call(bitOpsMethodToken("RotateLeft", paramType, listOf(paramType, Type.I32)))
                    storeResult(inst.dest)
                }

                is Rotr -> {
                    pushValue(inst.value); pushValue(inst.amount)
                    val paramType = if (inst.value.type == Type.I64) Type.I64 else Type.I32
                    assembler.call(bitOpsMethodToken("RotateRight", paramType, listOf(paramType, Type.I32)))
                    storeResult(inst.dest)
                }

                is Prefetch -> {} // no-op on CLR
                is StackSave -> {} // no-op on CLR
                is StackRestore -> {} // no-op on CLR

                is IndirectBr -> {
                    error("IndirectBr is not meaningful on CIL")
                }

                is Fence -> {} // no-op on CIL (CLR memory model handles this)

                is Phi -> {}
                is Alloca -> {}

                else -> error("Unsupported IR instruction for CIL: ${inst::class.simpleName}")
            }
        }

        private fun typeSizeBytes(type: Type): Int = when (type) {
            Type.I1, Type.I8 -> 1
            Type.I16 -> 2
            Type.I32, Type.F32 -> 4
            Type.I64, Type.F64, Type.OpaquePointer, is Type.Pointer -> 8
            is Type.Array -> typeSizeBytes(type.element) * type.size.toInt()
            is Type.Struct -> type.fields.sumOf { typeSizeBytes(it) }
            else -> 8
        }

        private fun structFieldOffset(struct: Type.Struct, fieldIndex: Int): Int {
            var offset = 0
            for (i in 0 until fieldIndex) {
                offset += typeSizeBytes(struct.fields[i])
            }
            return offset
        }

        private fun computeGepOffset(baseType: Type, indices: List<Value>): Int? {
            var offset = 0
            var currentType = baseType
            for ((i, idx) in indices.withIndex()) {
                val constIdx = when (idx) {
                    is Constant.I32 -> idx.value
                    is Constant.I64 -> idx.value.toInt()
                    else -> return null
                }
                if (i == 0) {
                    offset += constIdx * typeSizeBytes(currentType)
                } else {
                    when (currentType) {
                        is Type.Struct -> {
                            offset += structFieldOffset(currentType, constIdx)
                            currentType = currentType.fields[constIdx]
                        }
                        is Type.Array -> {
                            offset += constIdx * typeSizeBytes(currentType.element)
                            currentType = currentType.element
                        }
                        else -> offset += constIdx * typeSizeBytes(currentType)
                    }
                }
            }
            return offset
        }

        private fun aggregateFieldOffset(type: Type, indices: List<Int>): Int {
            var offset = 0
            var currentType = type
            for (idx in indices) {
                when (currentType) {
                    is Type.Struct -> {
                        offset += structFieldOffset(currentType, idx)
                        currentType = currentType.fields[idx]
                    }
                    is Type.Array -> {
                        offset += idx * typeSizeBytes(currentType.element)
                        currentType = currentType.element
                    }
                    else -> error("Cannot index into $currentType")
                }
            }
            return offset
        }

        private fun aggregateFieldType(type: Type, indices: List<Int>): Type {
            var currentType = type
            for (idx in indices) {
                currentType = when (currentType) {
                    is Type.Struct -> currentType.fields[idx]
                    is Type.Array -> currentType.element
                    else -> error("Cannot index into $currentType")
                }
            }
            return currentType
        }

        private fun emitGetElementPtr(inst: GetElementPtr) {
            // Push base pointer
            pushValue(inst.ptr)
            // Compute and add offset
            val constOffset = computeGepOffset(inst.baseType, inst.indices)
            if (constOffset != null) {
                if (constOffset != 0) {
                    assembler.ldcI4Auto(constOffset)
                    assembler.convI() // convert to native int for pointer arithmetic
                    assembler.add()
                }
            } else {
                // Dynamic indices
                var currentType = inst.baseType
                for ((i, idx) in inst.indices.withIndex()) {
                    val constIdx = when (idx) {
                        is Constant.I32 -> idx.value
                        is Constant.I64 -> idx.value.toInt()
                        else -> null
                    }
                    if (constIdx != null) {
                        val off = if (i == 0) constIdx * typeSizeBytes(currentType)
                        else when (currentType) {
                            is Type.Struct -> structFieldOffset(currentType as Type.Struct, constIdx).also {
                                currentType = (currentType as Type.Struct).fields[constIdx]
                            }
                            is Type.Array -> (constIdx * typeSizeBytes((currentType as Type.Array).element)).also {
                                currentType = (currentType as Type.Array).element
                            }
                            else -> constIdx * typeSizeBytes(currentType)
                        }
                        if (off != 0) {
                            assembler.ldcI4Auto(off)
                            assembler.convI()
                            assembler.add()
                        }
                    } else {
                        val elemSize = if (i == 0) typeSizeBytes(currentType)
                        else when (currentType) {
                            is Type.Array -> typeSizeBytes((currentType as Type.Array).element).also {
                                currentType = (currentType as Type.Array).element
                            }
                            else -> typeSizeBytes(currentType)
                        }
                        pushValue(idx)
                        assembler.convI()
                        if (elemSize != 1) {
                            assembler.ldcI4Auto(elemSize)
                            assembler.convI()
                            assembler.mul()
                        }
                        assembler.add()
                    }
                }
            }
            storeResult(inst.dest)
        }

        private fun emitExtractValue(inst: ExtractValue) {
            val fieldType = aggregateFieldType(inst.aggregate.type, inst.indices)
            val fieldOffset = aggregateFieldOffset(inst.aggregate.type, inst.indices)

            // Load address of aggregate (it's a local slot treated as a pointer)
            pushValue(inst.aggregate)
            if (fieldOffset != 0) {
                assembler.ldcI4Auto(fieldOffset)
                assembler.convI()
                assembler.add()
            }
            // Load the field value via indirect load
            when (fieldType) {
                Type.I8, Type.I1 -> assembler.ldindI1()
                Type.I16 -> assembler.ldindI2()
                Type.I32, Type.F32 -> assembler.ldindI4()
                else -> assembler.ldindI8()
            }
            storeResult(inst.dest)
        }

        private fun emitInsertValue(inst: InsertValue) {
            val fieldType = aggregateFieldType(inst.aggregate.type, inst.indices)
            val fieldOffset = aggregateFieldOffset(inst.aggregate.type, inst.indices)

            // For CIL, InsertValue modifies a copy of the aggregate
            // First copy the aggregate to the destination local
            pushValue(inst.aggregate)
            storeResult(inst.dest)

            // Then store the element at the field offset
            pushValue(inst.dest) // load dest address
            if (fieldOffset != 0) {
                assembler.ldcI4Auto(fieldOffset)
                assembler.convI()
                assembler.add()
            }
            pushValue(inst.element)
            when (fieldType) {
                Type.I8, Type.I1 -> assembler.stindI1()
                Type.I16 -> assembler.stindI2()
                Type.I32, Type.F32 -> assembler.stindI4()
                else -> assembler.stindI8()
            }
        }

        private fun emitBinOp(dest: InstructionRef, lhs: Value, rhs: Value, op: () -> Unit) {
            pushValue(lhs)
            pushValue(rhs)
            op()
            storeResult(dest)
        }

        private fun emitICmp(inst: ICmp) {
            pushValue(inst.lhs)
            pushValue(inst.rhs)

            when (inst.predicate) {
                ICmpPredicate.EQ -> {
                    assembler.ceq()
                }
                ICmpPredicate.NE -> {
                    assembler.ceq()
                    assembler.ldcI4Auto(0)
                    assembler.ceq()
                }
                ICmpPredicate.SGT -> {
                    assembler.cgt()
                }
                ICmpPredicate.SLT -> {
                    assembler.clt()
                }
                ICmpPredicate.SGE -> {
                    assembler.clt()
                    assembler.ldcI4Auto(0)
                    assembler.ceq()
                }
                ICmpPredicate.SLE -> {
                    assembler.cgt()
                    assembler.ldcI4Auto(0)
                    assembler.ceq()
                }
                ICmpPredicate.UGT -> {
                    assembler.cgtUn()
                }
                ICmpPredicate.ULT -> {
                    assembler.cltUn()
                }
                ICmpPredicate.UGE -> {
                    assembler.cltUn()
                    assembler.ldcI4Auto(0)
                    assembler.ceq()
                }
                ICmpPredicate.ULE -> {
                    assembler.cgtUn()
                    assembler.ldcI4Auto(0)
                    assembler.ceq()
                }
            }

            storeResult(inst.dest)
        }

        private fun emitFCmp(inst: FCmp) {
            pushValue(inst.lhs)
            pushValue(inst.rhs)

            when (inst.predicate) {
                FCmpPredicate.OEQ, FCmpPredicate.UEQ -> assembler.ceq()
                FCmpPredicate.ONE, FCmpPredicate.UNE -> {
                    assembler.ceq()
                    assembler.ldcI4Auto(0)
                    assembler.ceq()
                }
                FCmpPredicate.OGT, FCmpPredicate.UGT -> assembler.cgt()
                FCmpPredicate.OLT, FCmpPredicate.ULT -> assembler.clt()
                FCmpPredicate.OGE, FCmpPredicate.UGE -> {
                    assembler.clt()
                    assembler.ldcI4Auto(0)
                    assembler.ceq()
                }
                FCmpPredicate.OLE, FCmpPredicate.ULE -> {
                    assembler.cgt()
                    assembler.ldcI4Auto(0)
                    assembler.ceq()
                }
                FCmpPredicate.ORD -> {
                    // Ordered: both operands are not NaN
                    // ceq returns 0 if either is NaN, but also 0 if they're just not equal
                    // Use: dup both, ceq self, and
                    // Simplification: cgt_un | clt_un | ceq covers all ordered cases
                    // Actually: (a == a) && (b == b) — but we already consumed operands
                    // Just use cgt.un which returns 1 for unordered, negate
                    assembler.cgtUn()
                    assembler.clt()
                    assembler.or()
                    // This doesn't quite work. Simple: push 1 (most values are ordered)
                    // TODO: proper ORD implementation
                }
                FCmpPredicate.UNO -> {
                    // Unordered: either is NaN
                    assembler.ceq()
                    assembler.cgt()
                    assembler.or()
                    // TODO: proper UNO implementation
                }
                FCmpPredicate.FALSE -> {
                    // Pop operands, push 0
                    assembler.ceq() // consume both operands
                    assembler.ldcI4Auto(0)
                    assembler.ceq() // now we have 0 or 1, but we want 0
                    assembler.ldcI4Auto(0)
                    assembler.ceq() // double-negate... just load 0
                }
                FCmpPredicate.TRUE -> {
                    assembler.ceq() // consume operands
                    assembler.ldcI4Auto(1)
                    assembler.or()
                }
            }

            storeResult(inst.dest)
        }

        private fun emitCall(inst: Call) {
            for (arg in inst.args) {
                pushValue(arg)
            }
            val funcName = when (val f = inst.function) {
                is FunctionRef -> f.name
                is GlobalRef -> f.name
                else -> error("Unsupported call target: ${f::class.simpleName}")
            }
            val funcIndex = moduleFunctions.indexOfFirst { it.name == funcName }
            val token = CilToken.methodDef(1 + (if (funcIndex >= 0) funcIndex else 0))
            assembler.call(token)

            val dest = inst.dest
            if (dest != null) {
                storeResult(dest)
            }
        }

        private fun pushValue(value: Value) {
            when (value) {
                is Parameter -> {
                    val slot = locals[value.name] ?: error("No local for param ${value.name}")
                    assembler.ldarg(slot)
                }
                is InstructionRef -> {
                    val slot = locals[value.name] ?: error("No local for ${value.name}")
                    if (isParam(value.name)) {
                        assembler.ldarg(slot)
                    } else {
                        assembler.ldloc(slot - nextParamIndex)
                    }
                }
                is Constant.I1 -> {
                    assembler.ldcI4Auto(if (value.value) 1 else 0)
                }
                is Constant.I8 -> {
                    assembler.ldcI4Auto(value.value.toInt())
                }
                is Constant.I16 -> {
                    assembler.ldcI4Auto(value.value.toInt())
                }
                is Constant.I32 -> {
                    assembler.ldcI4Auto(value.value)
                }
                is Constant.I64 -> {
                    assembler.ldcI8(value.value)
                }
                is Constant.F32 -> {
                    assembler.ldcR4(value.value)
                }
                is Constant.F64 -> {
                    assembler.ldcR8(value.value)
                }
                is Constant.NullPtr -> {
                    assembler.ldnull()
                }
                else -> error("Unsupported value for CIL: ${value::class.simpleName}")
            }
        }

        private fun storeResult(dest: InstructionRef) {
            val slot = locals[dest.name] ?: error("No local for ${dest.name}")
            if (isParam(dest.name)) {
                assembler.starg(slot)
            } else {
                assembler.stloc(slot - nextParamIndex)
            }
        }

        private fun emitConversion(from: Type, to: Type, signed: Boolean) {
            when (to) {
                Type.I64 -> if (signed) assembler.convI8() else assembler.convU8()
                Type.I32 -> if (signed) assembler.convI4() else assembler.convU4()
                Type.I16 -> if (signed) assembler.convI2() else assembler.convU2()
                Type.I8 -> if (signed) assembler.convI1() else assembler.convU1()
                else -> {}
            }
        }

        private fun emitTruncation(from: Type, to: Type) {
            when (to) {
                Type.I32 -> assembler.convI4()
                Type.I16 -> assembler.convI2()
                Type.I8 -> assembler.convI1()
                Type.I1 -> {
                    assembler.ldcI4Auto(1)
                    assembler.and()
                }
                else -> {}
            }
        }
    }
}
