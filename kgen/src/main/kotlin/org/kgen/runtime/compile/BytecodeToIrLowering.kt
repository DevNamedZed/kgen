package org.kgen.runtime.compile

import org.kgen.ir.*
import org.kgen.ir.build.IrBuilder
import org.kgen.ir.instructions.*
import org.kgen.target.jvm.*
import org.kgen.target.jvm.JvmOpCode.*
import org.kgen.target.jvm.AttributeParser
import org.kgen.unmanaged.lib.StdlibProvider

/**
 * Lowers JVM bytecode from a single method to kgen IR.
 *
 * Translates the JVM operand stack model to SSA form: each stack push
 * becomes a named value, each pop becomes a use of that value.
 * Intrinsic calls to `Kgen.*` are recognized and replaced with
 * corresponding IR instructions.
 */
class BytecodeToIrLowering(
    private val builder: IrBuilder,
    private val cf: ClassFile,
    private val methodName: String,
    private val descriptor: String,
    private val codeAttr: CodeAttribute,
    private val exported: Boolean,
    private val fnAttributes: Set<FnAttribute> = emptySet(),
    private val isInstance: Boolean = false,
    private val classLayout: ClassLayout? = null,
    private val importMap: Map<String, String> = emptyMap(),
    private val callingConv: CallingConvention = CallingConvention.C,
) {
    private val cp = cf.constantPool
    private val code = codeAttr.code
    private val stack = mutableListOf<Value>()
    private val locals = mutableMapOf<Int, Value>()
    private val localAllocas = mutableMapOf<Int, Value>()
    private val mutatedLocals = mutableSetOf<Int>()
    private var nextTemp = 0
    private val lineMap = buildLineMap()
    private var currentPc = 0
    private val exceptionTable = codeAttr.exceptionTable
    private var normalBlockCounter = 0
    private val bootstrapMethods: BootstrapMethodsAttribute? by lazy {
        cf.attributes.firstOrNull { cf.string(it.nameIndex) == "BootstrapMethods" }
            ?.let { AttributeParser.parseBootstrapMethods(it) }
    }

    companion object {
        const val KGEN_CLASS = "org/kgen/unmanaged/Kgen"

        private val TERMINATORS = setOf(
            IFEQ, IFNE, IFLT, IFGE, IFGT, IFLE,
            IF_ICMPEQ, IF_ICMPNE, IF_ICMPLT, IF_ICMPGE, IF_ICMPGT, IF_ICMPLE,
            IF_ACMPEQ, IF_ACMPNE,
            GOTO, GOTO_W,
            IRETURN, LRETURN, FRETURN, DRETURN, ARETURN, RETURN,
            ATHROW, TABLESWITCH, LOOKUPSWITCH,
            IFNULL, IFNONNULL,
        )

        /** Parse JVM descriptor parameter types to IR types. */
        fun parseDescriptorTypes(desc: String): List<Type> {
            val params = mutableListOf<Type>()
            val paramStr = desc.substring(1, desc.indexOf(')'))
            var i = 0
            while (i < paramStr.length) {
                when (paramStr[i]) {
                    'Z' -> { params.add(Type.I1); i++ }
                    'B' -> { params.add(Type.I8); i++ }
                    'S' -> { params.add(Type.I16); i++ }
                    'I' -> { params.add(Type.I32); i++ }
                    'J' -> { params.add(Type.I64); i++ }
                    'F' -> { params.add(Type.F32); i++ }
                    'D' -> { params.add(Type.F64); i++ }
                    'L' -> { params.add(Type.OpaquePointer); i = paramStr.indexOf(';', i) + 1 }
                    '[' -> {
                        params.add(Type.OpaquePointer)
                        i++
                        while (i < paramStr.length && paramStr[i] == '[') i++
                        if (i < paramStr.length && paramStr[i] == 'L') i = paramStr.indexOf(';', i) + 1
                        else i++
                    }
                    else -> error("Unknown descriptor char: ${paramStr[i]}")
                }
            }
            return params
        }

        /** Parse JVM descriptor return type to IR type. */
        fun parseReturnType(desc: String): Type {
            val ret = desc.substring(desc.indexOf(')') + 1)
            return when {
                ret == "V" -> Type.Void
                ret == "Z" -> Type.I1
                ret == "B" -> Type.I8
                ret == "S" -> Type.I16
                ret == "I" -> Type.I32
                ret == "J" -> Type.I64
                ret == "F" -> Type.F32
                ret == "D" -> Type.F64
                ret.startsWith("L") || ret.startsWith("[") -> Type.OpaquePointer
                else -> error("Unsupported return type: $ret")
            }
        }
    }

    fun lower() {
        val descriptorParams = parseDescriptor(descriptor)
        val params = if (isInstance) listOf(Type.OpaquePointer) + descriptorParams else descriptorParams
        val returnType = parseReturnType(descriptor)
        val linkage = if (exported) Linkage.EXTERNAL else Linkage.INTERNAL

        val irParams = params.mapIndexed { i, type ->
            if (isInstance && i == 0) Param("this", type)
            else Param("p${if (isInstance) i - 1 else i}", type)
        }
        val paramValues = builder.createFunction(methodName, irParams, returnType, linkage = linkage, callingConv = callingConv, attributes = fnAttributes)

        val branchTargets = findBranchTargets()
        findMutatedLocals(params, irParams)

        builder.appendBlock("entry")

        for (idx in mutatedLocals.sorted()) {
            val type = localType(idx, params, irParams)
            val alloca = builder.alloca(type)
            localAllocas[idx] = alloca
        }

        for (i in paramValues.indices) {
            val slot = localIndex(i, irParams)
            if (slot in mutatedLocals) {
                builder.store(paramValues[i], localAllocas[slot]!!)
            } else {
                locals[slot] = paramValues[i]
            }
        }

        for (target in branchTargets.sorted()) {
            builder.createBlock("L$target")
        }

        var pc = 0
        var lastInstructionPc = -1
        var lastEmittedLine = -1
        val sourceFile = cf.sourceFile ?: cf.thisClassName.substringAfterLast('/') + ".java"
        val scope = methodName

        val handlerPcs = exceptionTable.map { it.handlerPc }.toSet()

        while (pc < code.size) {
            if (pc in branchTargets && pc > 0) {
                val label = "L$pc"
                if (lastInstructionPc < 0 || !isTerminator(lastInstructionPc)) {
                    builder.br(BlockRef(label))
                }
                builder.appendBlock(label)

                // At a catch handler entry, the JVM stack has the exception reference.
                // Push a placeholder — the real value comes from the landing pad.
                if (pc in handlerPcs) {
                    stack.clear()
                    push(Constant.NullPtr)
                }
            }

            val line = lineMap[pc]
            if (line != null && line != lastEmittedLine) {
                builder.debugLoc(line, 0, scope)
                lastEmittedLine = line
            }

            lastInstructionPc = pc
            currentPc = pc
            pc = lowerInstruction(pc)
        }

        // Emit landing pad blocks for exception handlers
        for (handlerPc in landingPadBlocks) {
            val catchLabel = "catch.$handlerPc"
            val handlerLabel = "L$handlerPc"
            builder.appendBlock(catchLabel)

            // Build catch clauses from the exception table
            val entries = exceptionTable.filter { it.handlerPc == handlerPc }
            val clauses = entries.mapNotNull { entry ->
                if (entry.catchType == 0) {
                    null // catch-all (finally) — cleanup landing pad
                } else {
                    val typeName = cp.className(entry.catchType)
                    LandingPadClause.Catch(GlobalRef(typeName, Type.OpaquePointer))
                }
            }
            val isCleanup = entries.any { it.catchType == 0 }

            val exPtr = builder.landingPad(Type.OpaquePointer, clauses, cleanup = isCleanup)

            // JVM handler expects the exception reference on the stack
            stack.clear()
            push(exPtr)
            builder.br(BlockRef(handlerLabel))
        }

        builder.finalizeFunction()
    }

    // ── Main dispatch ──────────────────────────────────────────────────

    private fun lowerInstruction(pc: Int): Int {
        val op = opcodeAt(pc)
        return when (op) {
            NOP -> pc + 1
            ACONST_NULL -> { push(Constant.NullPtr); pc + 1 }
            null -> error("Unknown opcode 0x${(code[pc].toInt() and 0xFF).toString(16)} at $methodName:$pc")
            else -> lowerConstants(op, pc)
                ?: lowerLoads(op, pc)
                ?: lowerStores(op, pc)
                ?: lowerArrayOps(op, pc)
                ?: lowerStackOps(op, pc)
                ?: lowerArithmetic(op, pc)
                ?: lowerConversions(op, pc)
                ?: lowerComparisons(op, pc)
                ?: lowerBranches(op, pc)
                ?: lowerReturns(op, pc)
                ?: lowerFieldOps(op, pc)
                ?: lowerInvocations(op, pc)
                ?: lowerObjectOps(op, pc)
                ?: error("Unsupported opcode ${op.name} (0x${op.code.toString(16)}) at $methodName:$pc")
        }
    }

    // ── Constants ──────────────────────────────────────────────────────

    private fun lowerConstants(op: JvmOpCode, pc: Int): Int? = when (op) {
        ICONST_M1 -> { push(const32(-1)); pc + 1 }
        ICONST_0  -> { push(const32(0)); pc + 1 }
        ICONST_1  -> { push(const32(1)); pc + 1 }
        ICONST_2  -> { push(const32(2)); pc + 1 }
        ICONST_3  -> { push(const32(3)); pc + 1 }
        ICONST_4  -> { push(const32(4)); pc + 1 }
        ICONST_5  -> { push(const32(5)); pc + 1 }
        LCONST_0  -> { push(const64(0)); pc + 1 }
        LCONST_1  -> { push(const64(1)); pc + 1 }
        FCONST_0  -> { push(Constant.F32(0.0f)); pc + 1 }
        FCONST_1  -> { push(Constant.F32(1.0f)); pc + 1 }
        FCONST_2  -> { push(Constant.F32(2.0f)); pc + 1 }
        DCONST_0  -> { push(Constant.F64(0.0)); pc + 1 }
        DCONST_1  -> { push(Constant.F64(1.0)); pc + 1 }
        BIPUSH    -> { push(const32(code[pc + 1].toInt())); pc + 2 }
        SIPUSH    -> { push(const32(readI16(pc + 1))); pc + 3 }
        LDC       -> { lowerLdc(readU8(pc + 1)); pc + 2 }
        LDC_W     -> { lowerLdc(readU16(pc + 1)); pc + 3 }
        LDC2_W    -> { lowerLdc(readU16(pc + 1)); pc + 3 }
        else -> null
    }

    // ── Loads ─────────────────────────────────────────────────────────

    private fun lowerLoads(op: JvmOpCode, pc: Int): Int? = when (op) {
        ILOAD -> { push(loadLocal(readU8(pc + 1), Type.I32)); pc + 2 }
        LLOAD -> { push(loadLocal(readU8(pc + 1), Type.I64)); pc + 2 }
        FLOAD -> { push(loadLocal(readU8(pc + 1), Type.F32)); pc + 2 }
        DLOAD -> { push(loadLocal(readU8(pc + 1), Type.F64)); pc + 2 }
        ALOAD -> { push(loadLocal(readU8(pc + 1), Type.OpaquePointer)); pc + 2 }

        ILOAD_0, ILOAD_1, ILOAD_2, ILOAD_3 -> { push(loadLocal(op.code - ILOAD_0.code, Type.I32)); pc + 1 }
        LLOAD_0, LLOAD_1, LLOAD_2, LLOAD_3 -> { push(loadLocal(op.code - LLOAD_0.code, Type.I64)); pc + 1 }
        FLOAD_0, FLOAD_1, FLOAD_2, FLOAD_3 -> { push(loadLocal(op.code - FLOAD_0.code, Type.F32)); pc + 1 }
        DLOAD_0, DLOAD_1, DLOAD_2, DLOAD_3 -> { push(loadLocal(op.code - DLOAD_0.code, Type.F64)); pc + 1 }
        ALOAD_0, ALOAD_1, ALOAD_2, ALOAD_3 -> { push(loadLocal(op.code - ALOAD_0.code, Type.OpaquePointer)); pc + 1 }
        else -> null
    }

    // ── Stores ────────────────────────────────────────────────────────

    private fun lowerStores(op: JvmOpCode, pc: Int): Int? = when (op) {
        ISTORE, LSTORE, FSTORE, DSTORE, ASTORE -> { storeLocal(readU8(pc + 1), pop()); pc + 2 }

        ISTORE_0, ISTORE_1, ISTORE_2, ISTORE_3 -> { storeLocal(op.code - ISTORE_0.code, pop()); pc + 1 }
        LSTORE_0, LSTORE_1, LSTORE_2, LSTORE_3 -> { storeLocal(op.code - LSTORE_0.code, pop()); pc + 1 }
        FSTORE_0, FSTORE_1, FSTORE_2, FSTORE_3 -> { storeLocal(op.code - FSTORE_0.code, pop()); pc + 1 }
        DSTORE_0, DSTORE_1, DSTORE_2, DSTORE_3 -> { storeLocal(op.code - DSTORE_0.code, pop()); pc + 1 }
        ASTORE_0, ASTORE_1, ASTORE_2, ASTORE_3 -> { storeLocal(op.code - ASTORE_0.code, pop()); pc + 1 }
        else -> null
    }

    // ── Array operations ──────────────────────────────────────────────

    private fun lowerArrayOps(op: JvmOpCode, pc: Int): Int? = when (op) {
        IALOAD -> { lowerArrayLoad(Type.I32, 4); pc + 1 }
        LALOAD -> { lowerArrayLoad(Type.I64, 8); pc + 1 }
        FALOAD -> { lowerArrayLoad(Type.F32, 4); pc + 1 }
        DALOAD -> { lowerArrayLoad(Type.F64, 8); pc + 1 }
        AALOAD -> { lowerArrayLoad(Type.OpaquePointer, 8); pc + 1 }
        BALOAD -> { lowerArrayLoad(Type.I8, 1); pc + 1 }
        CALOAD -> { lowerArrayLoad(Type.I16, 2); pc + 1 }
        SALOAD -> { lowerArrayLoad(Type.I16, 2); pc + 1 }

        IASTORE -> { lowerArrayStore(Type.I32, 4); pc + 1 }
        LASTORE -> { lowerArrayStore(Type.I64, 8); pc + 1 }
        FASTORE -> { lowerArrayStore(Type.F32, 4); pc + 1 }
        DASTORE -> { lowerArrayStore(Type.F64, 8); pc + 1 }
        AASTORE -> { lowerArrayStore(Type.OpaquePointer, 8); pc + 1 }
        BASTORE -> { lowerArrayStore(Type.I8, 1); pc + 1 }
        CASTORE -> { lowerArrayStore(Type.I16, 2); pc + 1 }
        SASTORE -> { lowerArrayStore(Type.I16, 2); pc + 1 }

        ARRAYLENGTH -> {
            val arrayRef = pop()
            push(builder.load(Type.I32, arrayRef))
            pc + 1
        }

        NEWARRAY -> {
            val atype = readU8(pc + 1)
            val elemSize = when (atype) {
                4 -> 1    // T_BOOLEAN
                5 -> 2    // T_CHAR
                6 -> 4    // T_FLOAT
                7 -> 8    // T_DOUBLE
                8 -> 1    // T_BYTE
                9 -> 2    // T_SHORT
                10 -> 4   // T_INT
                11 -> 8   // T_LONG
                else -> error("Unknown newarray type: $atype")
            }
            val count = pop()
            val countI64 = if (count.type == Type.I32) builder.sext(count, Type.I64) else count
            val dataSize = builder.mul(countI64, const64(elemSize.toLong()))
            val totalSize = builder.add(dataSize, const64(4))
            ensureExternalFunction("malloc", listOf(Param("size", Type.I64)), Type.OpaquePointer)
            val arrayPtr = builder.call("malloc", listOf(totalSize), Type.OpaquePointer)!!
            builder.store(count, arrayPtr as Value)
            push(arrayPtr)
            pc + 2
        }

        ANEWARRAY -> {
            // anewarray: allocate array of object references (8 bytes each)
            val count = pop()
            val countI64 = if (count.type == Type.I32) builder.sext(count, Type.I64) else count
            val dataSize = builder.mul(countI64, const64(8)) // ptr size
            val totalSize = builder.add(dataSize, const64(4)) // 4-byte length header
            ensureExternalFunction("malloc", listOf(Param("size", Type.I64)), Type.OpaquePointer)
            val arrayPtr = builder.call("malloc", listOf(totalSize), Type.OpaquePointer)!!
            builder.store(count, arrayPtr as Value)
            push(arrayPtr)
            pc + 3 // opcode + 2-byte class index
        }

        MULTIANEWARRAY -> {
            // multianewarray: allocate multi-dimensional array
            // Layout: each dimension is a flat array of pointers to the next dimension,
            // except the innermost which is a regular array.
            // For subset compiler: allocate as nested 1D arrays.
            val classIndex = readU16(pc + 1)
            val dimensions = readU8(pc + 3)

            // Pop dimension sizes (outermost first on stack after reversal)
            val dimSizes = (0 until dimensions).map { pop() }.reversed()

            // Allocate outermost array (array of pointers)
            val outerCount = dimSizes[0]
            val outerCountI64 = if (outerCount.type == Type.I32) builder.sext(outerCount, Type.I64) else outerCount
            val outerDataSize = builder.mul(outerCountI64, const64(8))
            val outerTotalSize = builder.add(outerDataSize, const64(4))
            ensureExternalFunction("malloc", listOf(Param("size", Type.I64)), Type.OpaquePointer)
            val outerArray = builder.call("malloc", listOf(outerTotalSize), Type.OpaquePointer)!!
            builder.store(outerCount, outerArray as Value)

            // For simplicity in the subset compiler, we only allocate the outermost dimension.
            // Inner dimensions would require loops to allocate each sub-array.
            // Java semantics: multianewarray only allocates as many dimensions as specified,
            // and the rest are null. We allocate the outer array and leave contents zeroed.
            push(outerArray)
            pc + 4
        }
        else -> null
    }

    // ── Stack manipulation ────────────────────────────────────────────

    private fun lowerStackOps(op: JvmOpCode, pc: Int): Int? = when (op) {
        POP  -> { pop(); pc + 1 }
        POP2 -> {
            // JVM POP2: pops either one category-2 value (long/double = 2 slots)
            // or two category-1 values (int/float/ref = 1 slot each).
            // In the kgen stack, long/double are single Values, so check the type.
            val top = pop()
            if (top.type != Type.I64 && top.type != Type.F64) pop()
            pc + 1
        }

        DUP -> { push(peek()); pc + 1 }
        DUP_X1 -> {
            val v1 = pop(); val v2 = pop()
            push(v1); push(v2); push(v1); pc + 1
        }
        DUP_X2 -> {
            val v1 = pop(); val v2 = pop(); val v3 = pop()
            push(v1); push(v3); push(v2); push(v1); pc + 1
        }
        DUP2 -> {
            val v1 = pop(); val v2 = pop()
            push(v2); push(v1); push(v2); push(v1); pc + 1
        }
        DUP2_X1 -> {
            val v1 = pop(); val v2 = pop(); val v3 = pop()
            push(v2); push(v1); push(v3); push(v2); push(v1); pc + 1
        }
        DUP2_X2 -> {
            val v1 = pop(); val v2 = pop(); val v3 = pop(); val v4 = pop()
            push(v2); push(v1); push(v4); push(v3); push(v2); push(v1); pc + 1
        }
        SWAP -> {
            val v1 = pop(); val v2 = pop()
            push(v1); push(v2); pc + 1
        }
        else -> null
    }

    // ── Arithmetic ────────────────────────────────────────────────────

    private fun lowerArithmetic(op: JvmOpCode, pc: Int): Int? = when (op) {
        IADD, LADD -> { val r = pop(); val l = pop(); push(builder.add(l, r)); pc + 1 }
        ISUB, LSUB -> { val r = pop(); val l = pop(); push(builder.sub(l, r)); pc + 1 }
        IMUL, LMUL -> { val r = pop(); val l = pop(); push(builder.mul(l, r)); pc + 1 }
        IDIV, LDIV -> { val r = pop(); val l = pop(); push(builder.sdiv(l, r)); pc + 1 }
        IREM, LREM -> { val r = pop(); val l = pop(); push(builder.srem(l, r)); pc + 1 }
        INEG, LNEG -> { push(builder.neg(pop())); pc + 1 }

        FADD, DADD -> { val r = pop(); val l = pop(); push(builder.fadd(l, r)); pc + 1 }
        FSUB, DSUB -> { val r = pop(); val l = pop(); push(builder.fsub(l, r)); pc + 1 }
        FMUL, DMUL -> { val r = pop(); val l = pop(); push(builder.fmul(l, r)); pc + 1 }
        FDIV, DDIV -> { val r = pop(); val l = pop(); push(builder.fdiv(l, r)); pc + 1 }
        FREM, DREM -> { val r = pop(); val l = pop(); push(builder.frem(l, r)); pc + 1 }
        FNEG, DNEG -> { push(builder.fneg(pop())); pc + 1 }

        ISHL, LSHL   -> { val r = pop(); val l = pop(); push(builder.shl(l, r)); pc + 1 }
        ISHR, LSHR   -> { val r = pop(); val l = pop(); push(builder.ashr(l, r)); pc + 1 }
        IUSHR, LUSHR -> { val r = pop(); val l = pop(); push(builder.lshr(l, r)); pc + 1 }

        IAND, LAND -> { val r = pop(); val l = pop(); push(builder.and(l, r)); pc + 1 }
        IOR, LOR   -> { val r = pop(); val l = pop(); push(builder.or(l, r)); pc + 1 }
        IXOR, LXOR -> { val r = pop(); val l = pop(); push(builder.xor(l, r)); pc + 1 }

        IINC -> {
            val idx = readU8(pc + 1)
            val inc = code[pc + 2].toInt()
            val cur = loadLocal(idx, Type.I32)
            storeLocal(idx, builder.add(cur, const32(inc)))
            pc + 3
        }
        else -> null
    }

    // ── Type conversions ──────────────────────────────────────────────

    private fun lowerConversions(op: JvmOpCode, pc: Int): Int? = when (op) {
        I2L -> { push(builder.sext(pop(), Type.I64)); pc + 1 }
        I2F -> { push(builder.sitofp(pop(), Type.F32)); pc + 1 }
        I2D -> { push(builder.sitofp(pop(), Type.F64)); pc + 1 }
        L2I -> { push(builder.trunc(pop(), Type.I32)); pc + 1 }
        L2F -> { push(builder.sitofp(pop(), Type.F32)); pc + 1 }
        L2D -> { push(builder.sitofp(pop(), Type.F64)); pc + 1 }
        F2I -> { push(builder.fptosi(pop(), Type.I32)); pc + 1 }
        F2L -> { push(builder.fptosi(pop(), Type.I64)); pc + 1 }
        F2D -> { push(builder.fpext(pop(), Type.F64)); pc + 1 }
        D2I -> { push(builder.fptosi(pop(), Type.I32)); pc + 1 }
        D2L -> { push(builder.fptosi(pop(), Type.I64)); pc + 1 }
        D2F -> { push(builder.fptrunc(pop(), Type.F32)); pc + 1 }
        I2B -> { push(builder.trunc(pop(), Type.I8)); pc + 1 }
        I2C -> { push(builder.trunc(pop(), Type.I16)); pc + 1 }
        I2S -> { push(builder.trunc(pop(), Type.I16)); pc + 1 }
        else -> null
    }

    // ── Comparisons ───────────────────────────────────────────────────

    private fun lowerComparisons(op: JvmOpCode, pc: Int): Int? = when (op) {
        LCMP -> {
            val r = pop(); val l = pop()
            val gt = builder.icmp(ICmpPredicate.SGT, l, r)
            val lt = builder.icmp(ICmpPredicate.SLT, l, r)
            val gtVal = builder.select(gt, const32(1), const32(0))
            val ltVal = builder.select(lt, const32(-1), const32(0))
            push(builder.add(gtVal, ltVal))
            pc + 1
        }
        FCMPL, FCMPG, DCMPL, DCMPG -> {
            val r = pop(); val l = pop()
            val gt = builder.fcmp(FCmpPredicate.OGT, l, r)
            val lt = builder.fcmp(FCmpPredicate.OLT, l, r)
            val gtVal = builder.select(gt, const32(1), const32(0))
            val ltVal = builder.select(lt, const32(-1), const32(0))
            push(builder.add(gtVal, ltVal))
            pc + 1
        }
        else -> null
    }

    // ── Branches ──────────────────────────────────────────────────────

    private fun lowerBranches(op: JvmOpCode, pc: Int): Int? = when (op) {
        IFEQ -> { lowerIfZero(pc, ICmpPredicate.EQ); pc + 3 }
        IFNE -> { lowerIfZero(pc, ICmpPredicate.NE); pc + 3 }
        IFLT -> { lowerIfZero(pc, ICmpPredicate.SLT); pc + 3 }
        IFGE -> { lowerIfZero(pc, ICmpPredicate.SGE); pc + 3 }
        IFGT -> { lowerIfZero(pc, ICmpPredicate.SGT); pc + 3 }
        IFLE -> { lowerIfZero(pc, ICmpPredicate.SLE); pc + 3 }

        IF_ICMPEQ -> { lowerIfICmp(pc, ICmpPredicate.EQ); pc + 3 }
        IF_ICMPNE -> { lowerIfICmp(pc, ICmpPredicate.NE); pc + 3 }
        IF_ICMPLT -> { lowerIfICmp(pc, ICmpPredicate.SLT); pc + 3 }
        IF_ICMPGE -> { lowerIfICmp(pc, ICmpPredicate.SGE); pc + 3 }
        IF_ICMPGT -> { lowerIfICmp(pc, ICmpPredicate.SGT); pc + 3 }
        IF_ICMPLE -> { lowerIfICmp(pc, ICmpPredicate.SLE); pc + 3 }

        GOTO -> {
            builder.br(BlockRef("L${pc + readI16(pc + 1)}"))
            pc + 3
        }
        GOTO_W -> {
            builder.br(BlockRef("L${pc + readI32(pc + 1)}"))
            pc + 5
        }
        else -> null
    }

    // ── Returns ───────────────────────────────────────────────────────

    private fun lowerReturns(op: JvmOpCode, pc: Int): Int? = when (op) {
        IRETURN, LRETURN, FRETURN, DRETURN, ARETURN -> { builder.ret(pop()); pc + 1 }
        RETURN -> { builder.ret(); pc + 1 }
        else -> null
    }

    // ── Field access ──────────────────────────────────────────────────

    private fun lowerFieldOps(op: JvmOpCode, pc: Int): Int? = when (op) {
        GETSTATIC -> { lowerGetStatic(readU16(pc + 1)); pc + 3 }
        PUTSTATIC -> { lowerPutStatic(readU16(pc + 1)); pc + 3 }
        GETFIELD  -> { lowerGetField(readU16(pc + 1)); pc + 3 }
        PUTFIELD  -> { lowerPutField(readU16(pc + 1)); pc + 3 }
        else -> null
    }

    // ── Invocations ───────────────────────────────────────────────────

    private fun lowerInvocations(op: JvmOpCode, pc: Int): Int? = when (op) {
        INVOKEVIRTUAL, INVOKESPECIAL -> {
            lowerInstanceCall(readU16(pc + 1))
            pc + 3
        }
        INVOKESTATIC -> {
            lowerStaticCall(readU16(pc + 1))
            pc + 3
        }
        INVOKEINTERFACE -> {
            lowerInterfaceCall(readU16(pc + 1))
            pc + 5
        }
        INVOKEDYNAMIC -> {
            lowerInvokeDynamic(readU16(pc + 1))
            pc + 5
        }
        else -> null
    }

    // ── Object operations ─────────────────────────────────────────────

    private fun lowerObjectOps(op: JvmOpCode, pc: Int): Int? = when (op) {
        NEW -> {
            val className = cp.className(readU16(pc + 1))
            val size = classLayout?.objectSize(className) ?: ClassLayout.DEFAULT_OBJECT_SIZE
            ensureExternalFunction("malloc", listOf(Param("size", Type.I64)), Type.OpaquePointer)
            val objPtr = builder.call("malloc", listOf(const64(size)), Type.OpaquePointer)!!
            push(objPtr as Value)
            pc + 3
        }
        ATHROW -> {
            val exception = pop()
            ensureExternalFunction("kgen_throw", listOf(Param("exception", Type.OpaquePointer)), Type.Void)
            builder.call("kgen_throw", listOf(exception), Type.Void)
            builder.unreachable()
            pc + 1
        }
        CHECKCAST -> pc + 3  // no-op in subset (no type hierarchy checks)
        INSTANCEOF -> {
            pop()
            push(const32(1))  // always 1 in subset (no type hierarchy)
            pc + 3
        }
        MONITORENTER -> {
            pop() // object reference — synchronization is a no-op in subset compiler
            pc + 1
        }
        MONITOREXIT -> {
            pop() // object reference — synchronization is a no-op in subset compiler
            pc + 1
        }
        else -> null
    }

    // ── Lowering helpers ──────────────────────────────────────────────

    private var stringConstantCounter = 0
    private var lastLdcString: String? = null

    private fun lowerLdc(cpIndex: Int) {
        when (val entry = cp[cpIndex]) {
            is CpInteger -> push(const32(entry.value))
            is CpFloat -> push(Constant.F32(entry.value))
            is CpLong -> push(const64(entry.value))
            is CpDouble -> push(Constant.F64(entry.value))
            is CpString -> {
                val str = (cp[entry.stringIndex] as CpUtf8).value
                lastLdcString = str
                push(emitStringConstant(str))
            }
            else -> error("Unsupported ldc constant: ${entry::class.simpleName}")
        }
    }

    private fun lowerIfZero(pc: Int, pred: ICmpPredicate) {
        val value = pop()
        val zero = if (value.type == Type.I64) const64(0) else const32(0)
        val cmp = builder.icmp(pred, value, zero)
        val target = pc + readI16(pc + 1)
        builder.condBr(cmp, BlockRef("L$target"), BlockRef("L${pc + 3}"))
    }

    private fun lowerIfICmp(pc: Int, pred: ICmpPredicate) {
        val r = pop(); val l = pop()
        val cmp = builder.icmp(pred, l, r)
        val target = pc + readI16(pc + 1)
        builder.condBr(cmp, BlockRef("L$target"), BlockRef("L${pc + 3}"))
    }

    private fun lowerArrayLoad(elemType: Type, elemSize: Int) {
        val index = pop()
        val arrayRef = pop()
        val indexI64 = if (index.type == Type.I32) builder.sext(index, Type.I64) else index
        val offset = builder.add(const64(4), builder.mul(indexI64, const64(elemSize.toLong())))
        val elemPtr = builder.gep(Type.I8, arrayRef, offset)
        push(builder.load(elemType, elemPtr))
    }

    private fun lowerArrayStore(elemType: Type, elemSize: Int) {
        val value = pop()
        val index = pop()
        val arrayRef = pop()
        val indexI64 = if (index.type == Type.I32) builder.sext(index, Type.I64) else index
        val offset = builder.add(const64(4), builder.mul(indexI64, const64(elemSize.toLong())))
        val elemPtr = builder.gep(Type.I8, arrayRef, offset)
        builder.store(value, elemPtr)
    }

    // ── Static/instance field access ──────────────────────────────────

    private val declaredExternals = mutableSetOf<String>()
    private val declaredGlobals = mutableSetOf<String>()

    private fun ensureExternalFunction(name: String, params: List<Param>, returnType: Type): GlobalRef {
        if (declaredExternals.add(name)) {
            builder.declareFunction(name, params, returnType)
        }
        return GlobalRef(name, Type.Pointer(returnType))
    }

    private fun staticFieldGlobalName(className: String, fieldName: String): String =
        "${className.replace('/', '_')}__$fieldName"

    private fun lowerGetStatic(cpIndex: Int) {
        val entry = cp[cpIndex] as CpFieldRef
        val className = cp.className(entry.classIndex)
        val (fieldName, fieldDesc) = cp.nameAndType(entry.nameAndTypeIndex)

        // Special handling for System.out / System.err — push a null placeholder
        // (the receiver is dropped when calling println/print via StdlibProvider)
        if (className == "java/lang/System" && (fieldName == "out" || fieldName == "err")) {
            push(Constant.NullPtr)
            return
        }

        val type = fieldDescriptorToType(fieldDesc)
        val globalName = staticFieldGlobalName(className, fieldName)
        if (declaredGlobals.add(globalName)) {
            builder.addGlobal(globalName, type, initializer = defaultConstant(type))
        }
        push(builder.load(type, GlobalRef(globalName, type)))
    }

    private fun lowerPutStatic(cpIndex: Int) {
        val entry = cp[cpIndex] as CpFieldRef
        val className = cp.className(entry.classIndex)
        val (fieldName, fieldDesc) = cp.nameAndType(entry.nameAndTypeIndex)
        val type = fieldDescriptorToType(fieldDesc)
        val globalName = staticFieldGlobalName(className, fieldName)
        if (declaredGlobals.add(globalName)) {
            builder.addGlobal(globalName, type, initializer = defaultConstant(type))
        }
        builder.store(pop(), GlobalRef(globalName, type))
    }

    // ── Instance field access ─────────────────────────────────────────

    private val fieldOffsets = mutableMapOf<String, Long>()
    private val classNextOffset = mutableMapOf<String, Long>()

    private fun getFieldOffset(className: String, fieldName: String, fieldType: Type): Long {
        classLayout?.fieldOffset(className, fieldName)?.let { return it }
        val key = "$className.$fieldName"
        return fieldOffsets.getOrPut(key) {
            val offset = classNextOffset.getOrPut(className) { 8L }
            classNextOffset[className] = offset + typeSize(fieldType)
            offset
        }
    }

    private fun lowerGetField(cpIndex: Int) {
        val entry = cp[cpIndex] as CpFieldRef
        val className = cp.className(entry.classIndex)
        val (fieldName, fieldDesc) = cp.nameAndType(entry.nameAndTypeIndex)
        val type = fieldDescriptorToType(fieldDesc)
        val offset = getFieldOffset(className, fieldName, type)
        val objRef = pop()
        val fieldPtr = builder.gep(Type.I8, objRef, const64(offset))
        push(builder.load(type, fieldPtr))
    }

    private fun lowerPutField(cpIndex: Int) {
        val entry = cp[cpIndex] as CpFieldRef
        val className = cp.className(entry.classIndex)
        val (fieldName, fieldDesc) = cp.nameAndType(entry.nameAndTypeIndex)
        val type = fieldDescriptorToType(fieldDesc)
        val offset = getFieldOffset(className, fieldName, type)
        val value = pop()
        val objRef = pop()
        val fieldPtr = builder.gep(Type.I8, objRef, const64(offset))
        builder.store(value, fieldPtr)
    }

    // ── Method calls ──────────────────────────────────────────────────

    private fun lowerInstanceCall(cpIndex: Int) {
        val entry = cp[cpIndex] as CpMethodRef
        val className = cp.className(entry.classIndex)
        val (name, desc) = cp.nameAndType(entry.nameAndTypeIndex)
        val paramTypes = parseDescriptor(desc)
        val returnType = parseReturnType(desc)

        val args = popReversed(paramTypes.size)
        val objRef = pop()

        if (name == "<init>" && className == "java/lang/Object") return

        // Check if this is a stdlib method we can redirect
        val stdlibName = StdlibProvider.nativeName(className, name, desc)
        if (stdlibName != null) {
            // For String methods, pass the receiver as the first arg
            // For PrintStream methods (System.out), drop the receiver
            val stdlibArgs = if (className == "java/lang/String") {
                mutableListOf(objRef).also { it.addAll(args) }
            } else {
                args
            }
            emitCall(stdlibName, stdlibArgs, returnType)
            return
        }

        val mangledName = if (name == "<init>") {
            "${className.replace('/', '_')}_init"
        } else {
            "${className.replace('/', '_')}_$name"
        }

        val allArgs = mutableListOf(objRef)
        allArgs.addAll(args)
        emitCall(mangledName, allArgs, returnType)
    }

    private fun lowerInterfaceCall(cpIndex: Int) {
        val entry = cp[cpIndex] as CpInterfaceMethodRef
        val className = cp.className(entry.classIndex)
        val (name, desc) = cp.nameAndType(entry.nameAndTypeIndex)
        val paramTypes = parseDescriptor(desc)
        val returnType = parseReturnType(desc)

        val args = popReversed(paramTypes.size)
        val objRef = pop()
        val mangledName = "${className.replace('/', '_')}_$name"

        val allArgs = mutableListOf(objRef)
        allArgs.addAll(args)
        emitCall(mangledName, allArgs, returnType)
    }

    private fun lowerInvokeDynamic(cpIndex: Int) {
        val entry = cp[cpIndex] as CpInvokeDynamic
        val (name, desc) = cp.nameAndType(entry.nameAndTypeIndex)
        val bsm = bootstrapMethods?.methods?.get(entry.bootstrapMethodAttrIndex)
            ?: error("Missing BootstrapMethods attribute for invokedynamic at $methodName")

        val bsmHandle = cp[bsm.methodRefIndex] as CpMethodHandle
        val bsmRef = cp[bsmHandle.referenceIndex]
        val bsmClassName = when (bsmRef) {
            is CpMethodRef -> cp.className(bsmRef.classIndex)
            is CpInterfaceMethodRef -> cp.className(bsmRef.classIndex)
            else -> ""
        }
        val bsmMethodName = when (bsmRef) {
            is CpMethodRef -> cp.nameAndType(bsmRef.nameAndTypeIndex).first
            is CpInterfaceMethodRef -> cp.nameAndType(bsmRef.nameAndTypeIndex).first
            else -> ""
        }

        when {
            bsmClassName == "java/lang/invoke/StringConcatFactory" &&
                bsmMethodName == "makeConcatWithConstants" -> lowerStringConcat(name, desc, bsm)
            bsmClassName == "java/lang/invoke/LambdaMetafactory" &&
                bsmMethodName == "metafactory" -> lowerLambdaMetafactory(name, desc, bsm)
            else -> error("Unsupported invokedynamic bootstrap: $bsmClassName.$bsmMethodName at $methodName")
        }
    }

    private fun lowerStringConcat(name: String, desc: String, bsm: BootstrapMethodEntry) {
        // StringConcatFactory.makeConcatWithConstants uses a recipe string (first static arg)
        // that interleaves literal text with \u0001 placeholders for dynamic args.
        // We lower this to calls to kgen_concat_* helper functions.
        val paramTypes = parseDescriptor(desc)
        val args = popReversed(paramTypes.size)

        // Get the recipe string from bootstrap method static args
        val recipeIndex = bsm.arguments.firstOrNull() ?: error("StringConcatFactory missing recipe argument")
        val recipe = when (val recipeEntry = cp[recipeIndex]) {
            is CpString -> cp.utf8(recipeEntry.stringIndex)
            is CpUtf8 -> recipeEntry.value
            else -> error("StringConcatFactory recipe is not a string: ${recipeEntry::class.simpleName}")
        }

        // Build the concatenated string by calling kgen_strconcat_begin/append_*/finish
        ensureExternalFunction("kgen_strconcat_begin", emptyList(), Type.OpaquePointer)
        ensureExternalFunction("kgen_strconcat_str", listOf(Param("buf", Type.OpaquePointer), Param("s", Type.OpaquePointer)), Type.OpaquePointer)
        ensureExternalFunction("kgen_strconcat_int", listOf(Param("buf", Type.OpaquePointer), Param("v", Type.I32)), Type.OpaquePointer)
        ensureExternalFunction("kgen_strconcat_long", listOf(Param("buf", Type.OpaquePointer), Param("v", Type.I64)), Type.OpaquePointer)
        ensureExternalFunction("kgen_strconcat_finish", listOf(Param("buf", Type.OpaquePointer)), Type.OpaquePointer)

        var buf: Value = builder.call("kgen_strconcat_begin", emptyList(), Type.OpaquePointer) as Value

        var argIdx = 0
        var literalStart = 0
        for (i in recipe.indices) {
            if (recipe[i] == '\u0001') {
                // Emit any preceding literal text
                if (i > literalStart) {
                    val literal = recipe.substring(literalStart, i)
                    val strGlobal = emitStringConstant(literal)
                    buf = builder.call("kgen_strconcat_str", listOf(buf, strGlobal), Type.OpaquePointer) as Value
                }
                // Emit the dynamic argument
                val arg = args[argIdx]
                buf = when (paramTypes[argIdx]) {
                    Type.I32, Type.I1, Type.I8, Type.I16 -> {
                        val widened = if (paramTypes[argIdx] != Type.I32) builder.sext(arg, Type.I32) else arg
                        builder.call("kgen_strconcat_int", listOf(buf, widened), Type.OpaquePointer) as Value
                    }
                    Type.I64 -> builder.call("kgen_strconcat_long", listOf(buf, arg), Type.OpaquePointer) as Value
                    else -> builder.call("kgen_strconcat_str", listOf(buf, arg), Type.OpaquePointer) as Value
                }
                argIdx++
                literalStart = i + 1
            } else if (recipe[i] == '\u0002') {
                // \u0002 means a constant from the BSM static args (index 1+)
                literalStart = i + 1
            }
        }
        // Emit trailing literal text
        if (literalStart < recipe.length) {
            val literal = recipe.substring(literalStart)
            val strGlobal = emitStringConstant(literal)
            buf = builder.call("kgen_strconcat_str", listOf(buf, strGlobal), Type.OpaquePointer) as Value
        }

        val result = builder.call("kgen_strconcat_finish", listOf(buf), Type.OpaquePointer)!!
        push(result as Value)
    }

    private fun emitStringConstant(str: String): Value {
        val globalName = ".str.${stringConstantCounter++}"
        val bytes = str.toByteArray(Charsets.UTF_8)
        builder.addGlobal(globalName, Type.Array(Type.I8, (bytes.size + 1).toLong()),
            initializer = Constant.StringConst(str),
            isConstant = true, linkage = Linkage.INTERNAL)
        return GlobalRef(globalName, Type.OpaquePointer)
    }

    private fun lowerLambdaMetafactory(name: String, desc: String, bsm: BootstrapMethodEntry) {
        // LambdaMetafactory.metafactory creates a functional interface implementation
        // that delegates to a target method. In the subset compiler, we lower this
        // to a direct function pointer (the lambda target method).
        //
        // The BSM static args are:
        //   [0] MethodType — erased interface method type
        //   [1] MethodHandle — the target implementation method
        //   [2] MethodType — specialized interface method type
        val paramTypes = parseDescriptor(desc)
        val returnType = parseReturnType(desc)

        // Pop captured variables (the invokedynamic params are the lambda captures)
        val captures = popReversed(paramTypes.size)

        // The target method handle is the second static arg
        if (bsm.arguments.size < 3) error("LambdaMetafactory missing static arguments")
        val targetHandle = cp[bsm.arguments[1]] as CpMethodHandle
        val targetRef = cp[targetHandle.referenceIndex]
        val (targetClass, targetName, targetDesc) = when (targetRef) {
            is CpMethodRef -> {
                val c = cp.className(targetRef.classIndex)
                val (n, d) = cp.nameAndType(targetRef.nameAndTypeIndex)
                Triple(c, n, d)
            }
            is CpInterfaceMethodRef -> {
                val c = cp.className(targetRef.classIndex)
                val (n, d) = cp.nameAndType(targetRef.nameAndTypeIndex)
                Triple(c, n, d)
            }
            else -> error("Unsupported method handle reference: ${targetRef::class.simpleName}")
        }

        // Determine the mangled name for the target method
        val mangledTarget = when (targetHandle.referenceKind) {
            6 -> targetName // REF_invokeStatic — use the method name directly
            5, 7, 9 -> "${targetClass.replace('/', '_')}_$targetName" // REF_invokeVirtual/Special/Interface
            else -> targetName
        }

        // For lambdas with no captures, just push a function pointer to the target
        // For lambdas with captures, we'd need a closure struct — for now, push the function pointer
        // and pass captures as the object argument when the interface method is called
        if (captures.isEmpty()) {
            push(GlobalRef(mangledTarget, Type.OpaquePointer))
        } else {
            // Create a closure: allocate struct with captures + function pointer
            // For subset compiler simplicity, if there's exactly one capture (the receiver),
            // just push it — the caller will pass it as first arg when invoking
            if (captures.size == 1) {
                push(captures[0])
            } else {
                // Multi-capture: allocate a struct
                val structSize = captures.size * 8L + 8 // captures + function pointer
                ensureExternalFunction("malloc", listOf(Param("size", Type.I64)), Type.OpaquePointer)
                val closure = builder.call("malloc", listOf(const64(structSize)), Type.OpaquePointer)!! as Value
                // Store function pointer at offset 0
                builder.store(GlobalRef(mangledTarget, Type.OpaquePointer), closure)
                // Store captures
                for (i in captures.indices) {
                    val ptr = builder.gep(Type.I8, closure, const64((i + 1) * 8L))
                    builder.store(captures[i], ptr)
                }
                push(closure)
            }
        }
    }

    private fun lowerStaticCall(cpIndex: Int) {
        val entry = cp[cpIndex] as CpMethodRef
        val className = cp.className(entry.classIndex)
        val (name, desc) = cp.nameAndType(entry.nameAndTypeIndex)

        if (className == KGEN_CLASS) {
            lowerIntrinsic(name, desc)
        } else {
            val paramTypes = parseDescriptor(desc)
            val returnType = parseReturnType(desc)
            val args = popReversed(paramTypes.size)

            // Check for @KgenImport name mapping (java name → native symbol)
            val importedName = importMap[name]
            if (importedName != null) {
                emitCall(importedName, args, returnType)
            } else {
                // Check for stdlib static methods (Math.*, Integer.toString, etc.)
                val stdlibName = StdlibProvider.nativeName(className, name, desc)
                if (stdlibName != null) {
                    emitCall(stdlibName, args, returnType)
                } else {
                    emitCall(name, args, returnType)
                }
            }
        }
    }

    private fun emitCall(name: String, args: List<Value>, returnType: Type) {
        // Check if this call site is inside a try region
        val handler = findExceptionHandler(currentPc)
        if (handler != null) {
            emitInvoke(name, args, returnType, handler)
        } else {
            if (returnType == Type.Void) {
                builder.call(name, args, Type.Void)
            } else {
                val result = builder.call(name, args, returnType)
                if (result != null) push(result)
            }
        }
    }

    private fun emitInvoke(name: String, args: List<Value>, returnType: Type, handler: ExceptionEntry) {
        val normalLabel = "invoke.normal.${normalBlockCounter++}"
        val unwindLabel = "catch.${handler.handlerPc}"

        // Create forward references for normal and unwind blocks
        builder.createBlock(normalLabel)
        if (handler.handlerPc !in landingPadBlocks) {
            builder.createBlock(unwindLabel)
            landingPadBlocks.add(handler.handlerPc)
        }

        val funcRef = GlobalRef(name, Type.Function(args.map { it.type }, returnType))
        val result = builder.invoke(funcRef, args, returnType, BlockRef(normalLabel), BlockRef(unwindLabel))

        // Continue in the normal block
        builder.appendBlock(normalLabel)
        if (result != null && returnType != Type.Void) {
            push(result)
        }
    }

    /**
     * Find the innermost exception handler covering the given PC.
     */
    private fun findExceptionHandler(pc: Int): ExceptionEntry? {
        return exceptionTable.firstOrNull { pc >= it.startPc && pc < it.endPc }
    }

    private val landingPadBlocks = mutableSetOf<Int>()

    // ── Kgen intrinsics ───────────────────────────────────────────────

    private fun lowerIntrinsic(name: String, desc: String) {
        when (name) {
            "loadByte"  -> push(builder.load(Type.I8, pop()))
            "loadShort" -> push(builder.load(Type.I16, pop()))
            "loadInt"   -> push(builder.load(Type.I32, pop()))
            "loadLong"  -> push(builder.load(Type.I64, pop()))
            "storeByte", "storeShort", "storeInt", "storeLong" -> {
                val value = pop(); val addr = pop()
                builder.store(value, addr)
            }
            "offset" -> {
                val offset = pop(); val base = pop()
                push(builder.add(base, if (offset.type == Type.I32) builder.sext(offset, Type.I64) else offset))
            }
            "stackAlloc" -> push(builder.alloca(Type.I8, pop()))
            "safepoint" -> builder.gcSafepoint()
            "gcRoot" -> builder.gcRoot(pop(), null)
            "writeBarrier" -> {
                val value = pop(); val fieldIndex = pop(); val obj = pop()
                builder.writeBarrier(obj, fieldIndex, value)
            }
            "readBarrier" -> push(builder.readBarrier(pop()))
            "stringConst" -> {
                // Pop the string Value (OpaquePointer from ldc), extract the literal from the
                // constant pool (the immediately preceding ldc pushed a CpString), create a
                // global string constant, and push its address as I64 (pointer-as-long convention).
                pop() // discard the OpaquePointer value — we use the raw string from lastLdcString
                val str = lastLdcString ?: error("Kgen.stringConst() must be called with a string literal")
                val globalRef = emitStringConstant(str)
                push(builder.ptrtoint(globalRef, Type.I64))
                lastLdcString = null
            }
            "isWindows" -> {
                val triple = builder.target.tripleString()
                push(Constant.I1("windows" in triple))
            }
            "isLinux" -> {
                val triple = builder.target.tripleString()
                push(Constant.I1("linux" in triple))
            }
            "isMacOS" -> {
                val triple = builder.target.tripleString()
                push(Constant.I1("darwin" in triple))
            }
            "likely", "unlikely" -> {} // no-op hints
            "runtimeAlloc" -> {
                val result = builder.call("kgen_runtime_alloc", listOf(pop()), Type.I64)
                if (result != null) push(result)
            }
            "runtimeFree" -> builder.call("kgen_runtime_free", listOf(pop()), Type.Void)
            else -> error("Unknown Kgen intrinsic: $name")
        }
    }

    // ── Local variable access ─────────────────────────────────────────

    private fun loadLocal(idx: Int, type: Type): Value {
        if (idx in mutatedLocals) return builder.load(type, localAllocas[idx]!!)
        return locals[idx]!!
    }

    private fun storeLocal(idx: Int, value: Value) {
        if (idx in mutatedLocals) {
            builder.store(value, localAllocas[idx]!!)
        } else {
            locals[idx] = value
        }
    }

    // ── Stack management ──────────────────────────────────────────────

    private fun push(value: Value) { stack.add(value) }
    private fun pop(): Value = stack.removeLast()
    private fun peek(): Value = stack.last()

    private fun popReversed(count: Int): List<Value> {
        val args = mutableListOf<Value>()
        for (i in 0 until count) args.add(0, pop())
        return args
    }

    // ── Bytecode scanning ─────────────────────────────────────────────

    private fun findMutatedLocals(params: List<Type>, irParams: List<Param>) {
        scanBytecode { pc, op ->
            when (op) {
                ISTORE, LSTORE, FSTORE, DSTORE, ASTORE -> mutatedLocals.add(readU8(pc + 1))
                ISTORE_0, ISTORE_1, ISTORE_2, ISTORE_3 -> mutatedLocals.add(op.code - ISTORE_0.code)
                LSTORE_0, LSTORE_1, LSTORE_2, LSTORE_3 -> mutatedLocals.add(op.code - LSTORE_0.code)
                FSTORE_0, FSTORE_1, FSTORE_2, FSTORE_3 -> mutatedLocals.add(op.code - FSTORE_0.code)
                DSTORE_0, DSTORE_1, DSTORE_2, DSTORE_3 -> mutatedLocals.add(op.code - DSTORE_0.code)
                ASTORE_0, ASTORE_1, ASTORE_2, ASTORE_3 -> mutatedLocals.add(op.code - ASTORE_0.code)
                IINC -> mutatedLocals.add(readU8(pc + 1))
                else -> {}
            }
        }
    }

    private fun findBranchTargets(): Set<Int> {
        val targets = mutableSetOf<Int>()
        scanBytecode { pc, op ->
            when (op) {
                IFEQ, IFNE, IFLT, IFGE, IFGT, IFLE,
                IF_ICMPEQ, IF_ICMPNE, IF_ICMPLT, IF_ICMPGE, IF_ICMPGT, IF_ICMPLE,
                IF_ACMPEQ, IF_ACMPNE,
                IFNULL, IFNONNULL -> {
                    targets.add(pc + readI16(pc + 1))
                    targets.add(pc + 3)
                }
                GOTO -> targets.add(pc + readI16(pc + 1))
                GOTO_W -> targets.add(pc + readI32(pc + 1))
                TABLESWITCH -> {
                    val aligned = (pc + 4) and 3.inv()
                    targets.add(pc + readI32(aligned))
                    val low = readI32(aligned + 4)
                    val high = readI32(aligned + 8)
                    for (i in 0..(high - low)) {
                        targets.add(pc + readI32(aligned + 12 + i * 4))
                    }
                }
                LOOKUPSWITCH -> {
                    val aligned = (pc + 4) and 3.inv()
                    targets.add(pc + readI32(aligned))
                    val npairs = readI32(aligned + 4)
                    for (i in 0 until npairs) {
                        targets.add(pc + readI32(aligned + 12 + i * 8))
                    }
                }
                else -> {}
            }
        }
        // Exception handler PCs are also branch targets
        for (entry in exceptionTable) {
            targets.add(entry.handlerPc)
        }
        targets.remove(0)
        return targets
    }

    private fun localType(idx: Int, params: List<Type>, irParams: List<Param>): Type {
        var slot = 0
        for (i in params.indices) {
            if (slot == idx) return params[i]
            slot++
            if (params[i] == Type.I64 || params[i] == Type.F64) slot++
        }
        return inferLocalType(idx)
    }

    private fun inferLocalType(idx: Int): Type {
        var result: Type? = null
        scanBytecode { pc, op ->
            if (result != null) return@scanBytecode
            when (op) {
                ISTORE -> if (readU8(pc + 1) == idx) result = Type.I32
                LSTORE -> if (readU8(pc + 1) == idx) result = Type.I64
                FSTORE -> if (readU8(pc + 1) == idx) result = Type.F32
                DSTORE -> if (readU8(pc + 1) == idx) result = Type.F64
                ASTORE -> if (readU8(pc + 1) == idx) result = Type.OpaquePointer
                ISTORE_0, ISTORE_1, ISTORE_2, ISTORE_3 ->
                    if (op.code - ISTORE_0.code == idx) result = Type.I32
                LSTORE_0, LSTORE_1, LSTORE_2, LSTORE_3 ->
                    if (op.code - LSTORE_0.code == idx) result = Type.I64
                FSTORE_0, FSTORE_1, FSTORE_2, FSTORE_3 ->
                    if (op.code - FSTORE_0.code == idx) result = Type.F32
                DSTORE_0, DSTORE_1, DSTORE_2, DSTORE_3 ->
                    if (op.code - DSTORE_0.code == idx) result = Type.F64
                ASTORE_0, ASTORE_1, ASTORE_2, ASTORE_3 ->
                    if (op.code - ASTORE_0.code == idx) result = Type.OpaquePointer
                IINC -> if (readU8(pc + 1) == idx) result = Type.I32
                else -> {}
            }
        }
        return result ?: Type.I32
    }

    /**
     * Walk all bytecode instructions, calling [action] with (pc, opcode) for each.
     * Handles variable-length instructions (wide, tableswitch, lookupswitch) internally.
     */
    private inline fun scanBytecode(action: (pc: Int, op: JvmOpCode) -> Unit) {
        var pc = 0
        while (pc < code.size) {
            val op = opcodeAt(pc)
            if (op != null) action(pc, op)
            pc = nextPc(pc, op)
        }
    }

    /**
     * Advance past the instruction at [pc]. Uses [JvmOpCode.operandSize] for fixed-length
     * instructions and handles variable-length instructions (wide, tableswitch, lookupswitch).
     */
    private fun nextPc(pc: Int, op: JvmOpCode?): Int {
        if (op == null) return pc + 1
        val size = op.operandSize
        if (size >= 0) return pc + 1 + size
        return when (op) {
            WIDE -> {
                val widened = code[pc + 1].toInt() and 0xFF
                if (widened == IINC.code) pc + 6 else pc + 4
            }
            TABLESWITCH -> {
                val aligned = (pc + 4) and 3.inv()
                val low = readI32(aligned + 4)
                val high = readI32(aligned + 8)
                aligned + 12 + (high - low + 1) * 4
            }
            LOOKUPSWITCH -> {
                val aligned = (pc + 4) and 3.inv()
                val npairs = readI32(aligned + 4)
                aligned + 8 + npairs * 8
            }
            else -> pc + 1
        }
    }

    private fun isTerminator(lastPc: Int): Boolean {
        val op = opcodeAt(lastPc) ?: return false
        return op in TERMINATORS
    }

    // ── Descriptor parsing ────────────────────────────────────────────

    private fun parseDescriptor(desc: String): List<Type> = parseDescriptorTypes(desc)

    private fun parseReturnType(desc: String): Type = Companion.parseReturnType(desc)

    private fun localIndex(paramIdx: Int, params: List<Param>): Int {
        var slot = 0
        for (i in 0 until paramIdx) {
            slot++
            if (params[i].type == Type.I64 || params[i].type == Type.F64) slot++
        }
        return slot
    }

    // ── Type utilities ────────────────────────────────────────────────

    private fun fieldDescriptorToType(desc: String): Type = when (desc[0]) {
        'I' -> Type.I32
        'J' -> Type.I64
        'F' -> Type.F32
        'D' -> Type.F64
        'B' -> Type.I8
        'S' -> Type.I16
        'Z' -> Type.I1
        'C' -> Type.I16
        'L', '[' -> Type.OpaquePointer
        else -> error("Unsupported field descriptor: $desc")
    }

    private fun defaultConstant(type: Type): Constant = when (type) {
        Type.I1 -> Constant.I1(false)
        Type.I8 -> Constant.I8(0)
        Type.I16 -> Constant.I16(0)
        Type.I32 -> Constant.I32(0)
        Type.I64 -> Constant.I64(0)
        Type.F32 -> Constant.F32(0f)
        Type.F64 -> Constant.F64(0.0)
        else -> Constant.NullPtr
    }

    private fun typeSize(type: Type): Long = when (type) {
        Type.I1, Type.I8 -> 1
        Type.I16 -> 2
        Type.I32, Type.F32 -> 4
        Type.I64, Type.F64, Type.OpaquePointer -> 8
        is Type.Pointer -> 8
        else -> 8
    }

    // ── Byte reading ──────────────────────────────────────────────────

    private fun opcodeAt(pc: Int): JvmOpCode? = JvmOpCode.fromCode(code[pc].toInt() and 0xFF)
    private fun readU8(offset: Int): Int = code[offset].toInt() and 0xFF
    private fun readU16(offset: Int): Int =
        ((code[offset].toInt() and 0xFF) shl 8) or (code[offset + 1].toInt() and 0xFF)
    private fun readI16(offset: Int): Int {
        val v = readU16(offset)
        return if (v >= 0x8000) v - 0x10000 else v
    }
    private fun readI32(offset: Int): Int =
        ((code[offset].toInt() and 0xFF) shl 24) or
            ((code[offset + 1].toInt() and 0xFF) shl 16) or
            ((code[offset + 2].toInt() and 0xFF) shl 8) or
            (code[offset + 3].toInt() and 0xFF)

    // ── Constant helpers ──────────────────────────────────────────────

    private fun const32(v: Int): Value = Constant.I32(v)
    private fun const64(v: Long): Value = Constant.I64(v)

    // ── Debug info ────────────────────────────────────────────────────

    private fun buildLineMap(): Map<Int, Int> {
        val lineAttr = codeAttr.attributes.firstOrNull {
            cf.string(it.nameIndex) == "LineNumberTable"
        } ?: return emptyMap()
        val table = AttributeParser.parseLineNumberTable(lineAttr)
        return table.entries.associate { it.startPc to it.lineNumber }
    }

}
