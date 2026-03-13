package org.kgen.ir.text

import org.kgen.ir.*
import org.kgen.ir.instructions.*
import org.kgen.ir.types.*

/**
 * Pretty-prints a [Module] as human-readable IR text.
 *
 * The output format resembles LLVM IR but includes high-level constructs
 * (classes, interfaces, enums, managed dispatch, etc.).
 *
 * ```kotlin
 * val text = IrPrinter.print(module)
 * println(text)
 * ```
 *
 * The companion object provides stateless helpers for printing individual types,
 * values, and constants — useful when building error messages or debug output:
 * ```kotlin
 * val typeText = IrPrinter.typeStr(Type.I32)           // "i32"
 * val valText = IrPrinter.valStr(someParameter)        // "%name"
 * val constText = IrPrinter.constStr(Constant.NullPtr) // "null"
 * ```
 */
class IrPrinter(private val sb: StringBuilder = StringBuilder()) {

    /** Print a [Module] to IR text. Reusable — clears internal state on each call. */
    fun print(module: Module): String {
        sb.setLength(0)
        printModule(module)
        return sb.toString()
    }

    private fun printModule(m: Module) {
        line("module ${quote(m.name)}")
        m.targetTriple?.let { line("target triple = ${quote(it)}") }
        m.dataLayout?.let { line("target datalayout = ${quote(it)}") }
        m.sourceFile?.let { line("source_file ${quote(it)}") }
        if (m.targetFeatures.isNotEmpty()) {
            line("target features = ${m.targetFeatures.joinToString(", ") { quote(it) }}")
        }
        m.constraints?.let {
            line("constraints = { ${it.joinToString(", ") { c -> c.name }} }")
        }
        blank()

        for (sub in m.submodules) {
            val cats = sub.constraints.joinToString(", ") { it.name }
            line("submodule ${quote(sub.name)} constraints { $cats } {")
            for (fn in sub.functions) line("  function @$fn")
            for (g in sub.globals) line("  global @$g")
            line("}")
        }
        if (m.submodules.isNotEmpty()) blank()

        for (alias in m.aliases) {
            line("type @${alias.name} = ${typeStr(alias.type)}")
        }
        if (m.aliases.isNotEmpty()) blank()

        for (s in m.structs) {
            val fields = s.fields.joinToString(", ") { (name, type) -> "$name: ${typeStr(type)}" }
            val packed = if (s.packed) " packed" else ""
            line("struct %${s.name}$packed { $fields }")
        }
        if (m.structs.isNotEmpty()) blank()

        for (cls in m.classes) printClass(cls)
        for (iface in m.interfaces) printInterface(iface)
        for (enum in m.enums) printEnum(enum)

        for (g in m.globals) printGlobal(g)
        if (m.globals.isNotEmpty()) blank()

        for (ctor in m.globalCtors) {
            line("@llvm.global_ctors = { ${ctor.priority}, @${ctor.function} }")
        }
        for (dtor in m.globalDtors) {
            line("@llvm.global_dtors = { ${dtor.priority}, @${dtor.function} }")
        }

        for (comdat in m.comdats) {
            line("comdat @${comdat.name} = ${comdat.selectionKind.name.lowercase()}")
        }
        if (m.comdats.isNotEmpty()) blank()

        for (ifunc in m.ifuncs) {
            val linkage = if (ifunc.linkage != Linkage.EXTERNAL) "${ifunc.linkage.name.lowercase()} " else ""
            val vis = if (ifunc.visibility != Visibility.DEFAULT) "${ifunc.visibility.name.lowercase()} " else ""
            line("@${ifunc.name} = ${linkage}${vis}ifunc ${typeStr(ifunc.type)}, @${ifunc.resolverFunction}")
        }
        if (m.ifuncs.isNotEmpty()) blank()

        m.moduleInlineAsm?.let {
            line("module asm ${quote(it)}")
            blank()
        }

        for (f in m.functions) {
            printFunction(f)
            blank()
        }

        for ((key, value) in m.metadata) {
            line("!$key = ${metadataStr(value)}")
        }

        for ((key, value) in m.moduleFlags) {
            val flagStr = when (value) {
                is ModuleFlagValue.IntFlag -> "!{${value.value}}"
                is ModuleFlagValue.StringFlag -> "!{${quote(value.value)}}"
                is ModuleFlagValue.MetadataFlag -> "!{${metadataStr(value.value)}}"
            }
            line("!llvm.module.flags.$key = $flagStr")
        }
    }

    private fun printClass(cls: ClassDef) {
        val vis = if (cls.visibility != ClassVisibility.PUBLIC) "${cls.visibility.name.lowercase()} " else ""
        val abs = if (cls.isAbstract) "abstract " else ""
        val fin = if (cls.isFinal) "final " else ""
        val extends = if (cls.superClass != null) " extends ${cls.superClass}" else ""
        val implements = if (cls.interfaces.isNotEmpty()) " implements ${cls.interfaces.joinToString(", ")}" else ""
        line("${vis}${abs}${fin}class ${cls.name}$extends$implements {")
        for (f in cls.fields) {
            val fvis = f.visibility.name.lowercase()
            val ffin = if (f.isFinal) " final" else ""
            line("  field $fvis$ffin ${f.name}: ${typeStr(f.type)}")
        }
        for (m in cls.methods) printMethodDef(m)
        for (c in cls.constructors) printMethodDef(c)
        line("}")
        blank()
    }

    private fun printInterface(iface: InterfaceDef) {
        val supers = if (iface.superInterfaces.isNotEmpty()) " extends ${iface.superInterfaces.joinToString(", ")}" else ""
        line("interface ${iface.name}$supers {")
        for (m in iface.methods) printMethodDef(m)
        line("}")
        blank()
    }

    private fun printEnum(enum: EnumDef) {
        line("enum ${enum.name} {")
        for (v in enum.variants) {
            val fields = if (v.fields.isNotEmpty()) "(${v.fields.joinToString(", ") { (n, t) -> "$n: ${typeStr(t)}" }})" else ""
            line("  ${v.name}$fields = ${v.ordinal}")
        }
        line("}")
        blank()
    }

    private fun printMethodDef(m: MethodDef) {
        val vis = m.visibility.name.lowercase()
        val abs = if (m.isAbstract) " abstract" else ""
        val fin = if (m.isFinal) " final" else ""
        val stat = if (m.isStatic) " static" else ""
        val params = m.params.joinToString(", ") { (n, t) -> "$n: ${typeStr(t)}" }
        line("  method $vis$abs$fin$stat ${m.name}($params): ${typeStr(m.returnType)}")
    }

    private fun printGlobal(g: Global) {
        val linkage = if (g.linkage != Linkage.EXTERNAL) "${g.linkage.name.lowercase()} " else ""
        val constant = if (g.isConstant) "constant" else "global"
        val init = if (g.initializer != null) " = ${constStr(g.initializer)}" else ""
        val align = if (g.align != null) ", align ${g.align}" else ""
        line("@${g.name} = $linkage$constant ${typeStr(g.type)}$init$align")
    }

    private fun printFunction(f: IrFunction) {
        val linkage = if (f.linkage != Linkage.EXTERNAL) "${f.linkage.name.lowercase()} " else ""
        val vis = if (f.visibility != Visibility.DEFAULT) "${f.visibility.name.lowercase()} " else ""
        val cc = if (f.callingConv != CallingConvention.C) "${f.callingConv.name.lowercase()} " else ""
        val attrs = if (f.attributes.isNotEmpty()) " ${f.attributes.joinToString(" ") { "#${it.name.lowercase()}" }}" else ""
        val vararg = if (f.isVarArg) ", ..." else ""
        val params = f.params.joinToString(", ") { "${typeStr(it.type)} %${it.name}" }
        val keyword = if (f.isExternal) "declare" else "define"
        val personality = if (f.personality != null) " personality @${f.personality.name}" else ""

        if (f.isExternal) {
            line("$keyword $linkage$vis${cc}${typeStr(f.returnType)} @${f.name}($params$vararg)$attrs$personality")
        } else {
            line("$keyword $linkage$vis${cc}${typeStr(f.returnType)} @${f.name}($params$vararg)$attrs$personality {")
            for (block in f.blocks) {
                printBlock(block)
            }
            line("}")
        }
    }

    private fun printBlock(block: BasicBlock) {
        line("${block.label}:")
        for (inst in block.instructions) {
            line("  ${instrStr(inst)}")
        }
    }

    private fun instrStr(inst: Instruction): String = when (inst) {
        // Integer arithmetic
        is Add -> "${inst.dest.name} = add${wrapFlags(inst.nuw, inst.nsw)} ${typeStr(inst.lhs.type)} ${valStr(inst.lhs)}, ${valStr(inst.rhs)}"
        is Sub -> "${inst.dest.name} = sub${wrapFlags(inst.nuw, inst.nsw)} ${typeStr(inst.lhs.type)} ${valStr(inst.lhs)}, ${valStr(inst.rhs)}"
        is Mul -> "${inst.dest.name} = mul${wrapFlags(inst.nuw, inst.nsw)} ${typeStr(inst.lhs.type)} ${valStr(inst.lhs)}, ${valStr(inst.rhs)}"
        is UDiv -> "${inst.dest.name} = udiv${if (inst.exact) " exact" else ""} ${typeStr(inst.lhs.type)} ${valStr(inst.lhs)}, ${valStr(inst.rhs)}"
        is SDiv -> "${inst.dest.name} = sdiv${if (inst.exact) " exact" else ""} ${typeStr(inst.lhs.type)} ${valStr(inst.lhs)}, ${valStr(inst.rhs)}"
        is URem -> "${inst.dest.name} = urem ${typeStr(inst.lhs.type)} ${valStr(inst.lhs)}, ${valStr(inst.rhs)}"
        is SRem -> "${inst.dest.name} = srem ${typeStr(inst.lhs.type)} ${valStr(inst.lhs)}, ${valStr(inst.rhs)}"
        is Neg -> "${inst.dest.name} = neg ${typeStr(inst.operand.type)} ${valStr(inst.operand)}"

        // Overflow-checked
        is SAddOverflow -> "${inst.dest.name} = sadd.overflow ${typeStr(inst.lhs.type)} ${valStr(inst.lhs)}, ${valStr(inst.rhs)}"
        is UAddOverflow -> "${inst.dest.name} = uadd.overflow ${typeStr(inst.lhs.type)} ${valStr(inst.lhs)}, ${valStr(inst.rhs)}"
        is SSubOverflow -> "${inst.dest.name} = ssub.overflow ${typeStr(inst.lhs.type)} ${valStr(inst.lhs)}, ${valStr(inst.rhs)}"
        is USubOverflow -> "${inst.dest.name} = usub.overflow ${typeStr(inst.lhs.type)} ${valStr(inst.lhs)}, ${valStr(inst.rhs)}"
        is SMulOverflow -> "${inst.dest.name} = smul.overflow ${typeStr(inst.lhs.type)} ${valStr(inst.lhs)}, ${valStr(inst.rhs)}"
        is UMulOverflow -> "${inst.dest.name} = umul.overflow ${typeStr(inst.lhs.type)} ${valStr(inst.lhs)}, ${valStr(inst.rhs)}"

        // Saturating
        is SAddSat -> "${inst.dest.name} = sadd.sat ${typeStr(inst.lhs.type)} ${valStr(inst.lhs)}, ${valStr(inst.rhs)}"
        is UAddSat -> "${inst.dest.name} = uadd.sat ${typeStr(inst.lhs.type)} ${valStr(inst.lhs)}, ${valStr(inst.rhs)}"
        is SSubSat -> "${inst.dest.name} = ssub.sat ${typeStr(inst.lhs.type)} ${valStr(inst.lhs)}, ${valStr(inst.rhs)}"
        is USubSat -> "${inst.dest.name} = usub.sat ${typeStr(inst.lhs.type)} ${valStr(inst.lhs)}, ${valStr(inst.rhs)}"

        // Min/max
        is SMin -> "${inst.dest.name} = smin ${typeStr(inst.lhs.type)} ${valStr(inst.lhs)}, ${valStr(inst.rhs)}"
        is SMax -> "${inst.dest.name} = smax ${typeStr(inst.lhs.type)} ${valStr(inst.lhs)}, ${valStr(inst.rhs)}"
        is UMin -> "${inst.dest.name} = umin ${typeStr(inst.lhs.type)} ${valStr(inst.lhs)}, ${valStr(inst.rhs)}"
        is UMax -> "${inst.dest.name} = umax ${typeStr(inst.lhs.type)} ${valStr(inst.lhs)}, ${valStr(inst.rhs)}"
        is Abs -> "${inst.dest.name} = abs ${typeStr(inst.operand.type)} ${valStr(inst.operand)}${if (inst.isIntMin) " int_min" else ""}"

        // Float arithmetic
        is FAdd -> "${inst.dest.name} = fadd${fmStr(inst.fastMath)} ${typeStr(inst.lhs.type)} ${valStr(inst.lhs)}, ${valStr(inst.rhs)}"
        is FSub -> "${inst.dest.name} = fsub${fmStr(inst.fastMath)} ${typeStr(inst.lhs.type)} ${valStr(inst.lhs)}, ${valStr(inst.rhs)}"
        is FMul -> "${inst.dest.name} = fmul${fmStr(inst.fastMath)} ${typeStr(inst.lhs.type)} ${valStr(inst.lhs)}, ${valStr(inst.rhs)}"
        is FDiv -> "${inst.dest.name} = fdiv${fmStr(inst.fastMath)} ${typeStr(inst.lhs.type)} ${valStr(inst.lhs)}, ${valStr(inst.rhs)}"
        is FRem -> "${inst.dest.name} = frem${fmStr(inst.fastMath)} ${typeStr(inst.lhs.type)} ${valStr(inst.lhs)}, ${valStr(inst.rhs)}"
        is FNeg -> "${inst.dest.name} = fneg${fmStr(inst.fastMath)} ${typeStr(inst.operand.type)} ${valStr(inst.operand)}"
        is FAbs -> "${inst.dest.name} = fabs ${typeStr(inst.operand.type)} ${valStr(inst.operand)}"
        is FMA -> "${inst.dest.name} = fma ${typeStr(inst.a.type)} ${valStr(inst.a)}, ${valStr(inst.b)}, ${valStr(inst.c)}"
        is FMin -> "${inst.dest.name} = fmin ${typeStr(inst.lhs.type)} ${valStr(inst.lhs)}, ${valStr(inst.rhs)}"
        is FMax -> "${inst.dest.name} = fmax ${typeStr(inst.lhs.type)} ${valStr(inst.lhs)}, ${valStr(inst.rhs)}"
        is Sqrt -> "${inst.dest.name} = sqrt ${typeStr(inst.operand.type)} ${valStr(inst.operand)}"
        is Ceil -> "${inst.dest.name} = ceil ${typeStr(inst.operand.type)} ${valStr(inst.operand)}"
        is Floor -> "${inst.dest.name} = floor ${typeStr(inst.operand.type)} ${valStr(inst.operand)}"
        is Round -> "${inst.dest.name} = round ${typeStr(inst.operand.type)} ${valStr(inst.operand)}"
        is Trunc -> "${inst.dest.name} = trunc ${typeStr(inst.operand.type)} ${valStr(inst.operand)}"
        is CopySign -> "${inst.dest.name} = copysign ${typeStr(inst.magnitude.type)} ${valStr(inst.magnitude)}, ${valStr(inst.sign)}"

        // Bitwise
        is And -> "${inst.dest.name} = and ${typeStr(inst.lhs.type)} ${valStr(inst.lhs)}, ${valStr(inst.rhs)}"
        is Or -> "${inst.dest.name} = or ${typeStr(inst.lhs.type)} ${valStr(inst.lhs)}, ${valStr(inst.rhs)}"
        is Xor -> "${inst.dest.name} = xor ${typeStr(inst.lhs.type)} ${valStr(inst.lhs)}, ${valStr(inst.rhs)}"
        is Not -> "${inst.dest.name} = not ${typeStr(inst.operand.type)} ${valStr(inst.operand)}"
        is Shl -> "${inst.dest.name} = shl${wrapFlags(inst.nuw, inst.nsw)} ${typeStr(inst.lhs.type)} ${valStr(inst.lhs)}, ${valStr(inst.rhs)}"
        is LShr -> "${inst.dest.name} = lshr${if (inst.exact) " exact" else ""} ${typeStr(inst.lhs.type)} ${valStr(inst.lhs)}, ${valStr(inst.rhs)}"
        is AShr -> "${inst.dest.name} = ashr${if (inst.exact) " exact" else ""} ${typeStr(inst.lhs.type)} ${valStr(inst.lhs)}, ${valStr(inst.rhs)}"
        is RotateLeft -> "${inst.dest.name} = rotl ${typeStr(inst.value.type)} ${valStr(inst.value)}, ${valStr(inst.amount)}"
        is RotateRight -> "${inst.dest.name} = rotr ${typeStr(inst.value.type)} ${valStr(inst.value)}, ${valStr(inst.amount)}"
        is Rotl -> "${inst.dest.name} = rotl ${typeStr(inst.value.type)} ${valStr(inst.value)}, ${valStr(inst.amount)}"
        is Rotr -> "${inst.dest.name} = rotr ${typeStr(inst.value.type)} ${valStr(inst.value)}, ${valStr(inst.amount)}"

        // Bit manipulation
        is Ctlz -> "${inst.dest.name} = ctlz ${typeStr(inst.operand.type)} ${valStr(inst.operand)}${if (inst.isZeroPoison) " zero_poison" else ""}"
        is Cttz -> "${inst.dest.name} = cttz ${typeStr(inst.operand.type)} ${valStr(inst.operand)}${if (inst.isZeroPoison) " zero_poison" else ""}"
        is Ctpop -> "${inst.dest.name} = ctpop ${typeStr(inst.operand.type)} ${valStr(inst.operand)}"
        is BSwap -> "${inst.dest.name} = bswap ${typeStr(inst.operand.type)} ${valStr(inst.operand)}"
        is BitReverse -> "${inst.dest.name} = bitreverse ${typeStr(inst.operand.type)} ${valStr(inst.operand)}"

        // Comparison
        is ICmp -> "${inst.dest.name} = icmp ${inst.predicate.name.lowercase()} ${typeStr(inst.lhs.type)} ${valStr(inst.lhs)}, ${valStr(inst.rhs)}"
        is FCmp -> "${inst.dest.name} = fcmp${fmStr(inst.fastMath)} ${inst.predicate.name.lowercase()} ${typeStr(inst.lhs.type)} ${valStr(inst.lhs)}, ${valStr(inst.rhs)}"

        // Memory
        is Alloca -> {
            val num = if (inst.numElements != null) ", ${typeStr(inst.numElements.type)} ${valStr(inst.numElements)}" else ""
            val align = if (inst.align != null) ", align ${inst.align}" else ""
            "${inst.dest.name} = alloca ${typeStr(inst.allocType)}$num$align"
        }
        is Load -> {
            val vol = if (inst.volatile) "volatile " else ""
            val align = if (inst.align != null) ", align ${inst.align}" else ""
            val ord = if (inst.ordering != null) " ${inst.ordering.name.lowercase()}" else ""
            "${inst.dest.name} = load ${vol}${typeStr(inst.loadType)}, ptr ${valStr(inst.ptr)}$align$ord"
        }
        is Store -> {
            val vol = if (inst.volatile) "volatile " else ""
            val align = if (inst.align != null) ", align ${inst.align}" else ""
            val ord = if (inst.ordering != null) " ${inst.ordering.name.lowercase()}" else ""
            "store ${vol}${typeStr(inst.value.type)} ${valStr(inst.value)}, ptr ${valStr(inst.ptr)}$align$ord"
        }
        is GetElementPtr -> {
            val ib = if (inst.inBounds) " inbounds" else ""
            val indices = inst.indices.joinToString(", ") { "${typeStr(it.type)} ${valStr(it)}" }
            "${inst.dest.name} = getelementptr$ib ${typeStr(inst.baseType)}, ptr ${valStr(inst.ptr)}, $indices"
        }
        is Fence -> {
            val scope = if (inst.syncScope != null) " syncscope(${quote(inst.syncScope)})" else ""
            "fence$scope ${inst.ordering.name.lowercase()}"
        }
        is CmpXchg -> {
            val weak = if (inst.weak) " weak" else ""
            val vol = if (inst.volatile) " volatile" else ""
            "${inst.dest.name} = cmpxchg$weak$vol ptr ${valStr(inst.ptr)}, ${typeStr(inst.cmp.type)} ${valStr(inst.cmp)}, ${typeStr(inst.new.type)} ${valStr(inst.new)} ${inst.successOrdering.name.lowercase()} ${inst.failureOrdering.name.lowercase()}"
        }
        is AtomicRMW -> {
            val vol = if (inst.volatile) " volatile" else ""
            "${inst.dest.name} = atomicrmw$vol ${inst.op.name.lowercase()} ptr ${valStr(inst.ptr)}, ${typeStr(inst.value.type)} ${valStr(inst.value)} ${inst.ordering.name.lowercase()}"
        }
        is MemCpy -> "memcpy ptr ${valStr(inst.dst)}, ptr ${valStr(inst.src)}, ${typeStr(inst.len.type)} ${valStr(inst.len)}${if (inst.volatile) " volatile" else ""}"
        is MemSet -> "memset ptr ${valStr(inst.dst)}, ${typeStr(inst.value.type)} ${valStr(inst.value)}, ${typeStr(inst.len.type)} ${valStr(inst.len)}${if (inst.volatile) " volatile" else ""}"
        is MemMove -> "memmove ptr ${valStr(inst.dst)}, ptr ${valStr(inst.src)}, ${typeStr(inst.len.type)} ${valStr(inst.len)}${if (inst.volatile) " volatile" else ""}"
        is Prefetch -> "prefetch ptr ${valStr(inst.address)}, ${inst.rw}, ${inst.locality}, ${inst.cacheType}"

        // Stack
        is StackSave -> "${inst.dest.name} = stacksave"
        is StackRestore -> "stackrestore ${valStr(inst.ptr)}"

        // Lifetime
        is LifetimeStart -> "lifetime.start ptr ${valStr(inst.ptr)}, ${inst.size}"
        is LifetimeEnd -> "lifetime.end ptr ${valStr(inst.ptr)}, ${inst.size}"

        // Conversions
        is IntTrunc -> "${inst.dest.name} = inttrunc ${typeStr(inst.value.type)} ${valStr(inst.value)} to ${typeStr(inst.toType)}"
        is ZExt -> "${inst.dest.name} = zext ${typeStr(inst.value.type)} ${valStr(inst.value)} to ${typeStr(inst.toType)}"
        is SExt -> "${inst.dest.name} = sext ${typeStr(inst.value.type)} ${valStr(inst.value)} to ${typeStr(inst.toType)}"
        is FPTrunc -> "${inst.dest.name} = fptrunc ${typeStr(inst.value.type)} ${valStr(inst.value)} to ${typeStr(inst.toType)}"
        is FPExt -> "${inst.dest.name} = fpext ${typeStr(inst.value.type)} ${valStr(inst.value)} to ${typeStr(inst.toType)}"
        is FPToUI -> "${inst.dest.name} = fptoui ${typeStr(inst.value.type)} ${valStr(inst.value)} to ${typeStr(inst.toType)}"
        is FPToSI -> "${inst.dest.name} = fptosi ${typeStr(inst.value.type)} ${valStr(inst.value)} to ${typeStr(inst.toType)}"
        is UIToFP -> "${inst.dest.name} = uitofp ${typeStr(inst.value.type)} ${valStr(inst.value)} to ${typeStr(inst.toType)}"
        is SIToFP -> "${inst.dest.name} = sitofp ${typeStr(inst.value.type)} ${valStr(inst.value)} to ${typeStr(inst.toType)}"
        is PtrToInt -> "${inst.dest.name} = ptrtoint ptr ${valStr(inst.value)} to ${typeStr(inst.toType)}"
        is IntToPtr -> "${inst.dest.name} = inttoptr ${typeStr(inst.value.type)} ${valStr(inst.value)} to ptr"
        is BitCast -> "${inst.dest.name} = bitcast ${typeStr(inst.value.type)} ${valStr(inst.value)} to ${typeStr(inst.toType)}"
        is AddrSpaceCast -> "${inst.dest.name} = addrspacecast ${typeStr(inst.value.type)} ${valStr(inst.value)} to ${typeStr(inst.toType)}"

        // Control flow
        is Ret -> if (inst.value != null) "ret ${typeStr(inst.value.type)} ${valStr(inst.value)}" else "ret void"
        is Br -> "br label %${inst.target}"
        is CondBr -> "br ${typeStr(Type.I1)} ${valStr(inst.condition)}, label %${inst.trueTarget}, label %${inst.falseTarget}"
        is Switch -> {
            val cases = inst.cases.joinToString(", ") { (c, t) -> "${typeStr(c.type)} ${constStr(c)} -> label %$t" }
            "switch ${typeStr(inst.value.type)} ${valStr(inst.value)}, label %${inst.defaultTarget} [$cases]"
        }
        is IndirectBr -> "indirectbr ptr ${valStr(inst.address)}, [${inst.targets.joinToString(", ") { "label %$it" }}]"
        is Unreachable -> "unreachable"
        is Trap -> "trap"
        is DebugTrap -> "debugtrap"

        // Calls
        is Call -> {
            val tail = if (inst.tailCall != TailCallKind.NONE) "${inst.tailCall.name.lowercase()} " else ""
            val cc = if (inst.callingConv != CallingConvention.C) "${inst.callingConv.name.lowercase()} " else ""
            val args = inst.args.joinToString(", ") { "${typeStr(it.type)} ${valStr(it)}" }
            if (inst.dest != null) {
                "${inst.dest.name} = ${tail}call $cc${typeStr(inst.returnType)} ${valStr(inst.function)}($args)"
            } else {
                "${tail}call $cc${typeStr(inst.returnType)} ${valStr(inst.function)}($args)"
            }
        }
        is Invoke -> {
            val args = inst.args.joinToString(", ") { "${typeStr(it.type)} ${valStr(it)}" }
            val dest = if (inst.dest != null) "${inst.dest.name} = " else ""
            "${dest}invoke ${typeStr(inst.returnType)} ${valStr(inst.function)}($args) to label %${inst.normalDest} unwind label %${inst.unwindDest}"
        }
        is CallBr -> {
            val args = inst.args.joinToString(", ") { "${typeStr(it.type)} ${valStr(it)}" }
            val dest = if (inst.dest != null) "${inst.dest.name} = " else ""
            val indirect = inst.indirectDests.joinToString(", ") { "label %$it" }
            "${dest}callbr ${typeStr(inst.returnType)} ${valStr(inst.function)}($args) to label %${inst.fallthrough} [$indirect]"
        }

        // Varargs
        is VAStart -> "va_start ${valStr(inst.argList)}"
        is VAEnd -> "va_end ${valStr(inst.argList)}"
        is VACopy -> "va_copy ${valStr(inst.dst)}, ${valStr(inst.src)}"
        is VAArg -> "${inst.dest.name} = va_arg ${valStr(inst.argList)}, ${typeStr(inst.argType)}"

        // Exception handling
        is LandingPad -> {
            val cleanup = if (inst.cleanup) " cleanup" else ""
            val clauses = inst.clauses.joinToString(" ") {
                when (it) {
                    is LandingPadClause.Catch -> "catch ${typeStr(it.type.type)} ${valStr(it.type)}"
                    is LandingPadClause.Filter -> "filter [${it.types.joinToString(", ") { t -> "${typeStr(t.type)} ${valStr(t)}" }}]"
                }
            }
            "${inst.dest.name} = landingpad ${typeStr(inst.resultType)}$cleanup $clauses"
        }
        is Resume -> "resume ${typeStr(inst.value.type)} ${valStr(inst.value)}"
        is CatchSwitch -> {
            val parent = if (inst.parentPad != null) valStr(inst.parentPad) else "none"
            val handlers = inst.handlers.joinToString(", ") { "label %$it" }
            val unwind = if (inst.unwindDest != null) " unwind label %${inst.unwindDest}" else " unwind to caller"
            "${inst.dest.name} = catchswitch within $parent [$handlers]$unwind"
        }
        is CatchPad -> {
            val args = inst.args.joinToString(", ") { "${typeStr(it.type)} ${valStr(it)}" }
            "${inst.dest.name} = catchpad within ${valStr(inst.catchSwitch)} [$args]"
        }
        is CleanupPad -> {
            val parent = if (inst.parentPad != null) valStr(inst.parentPad) else "none"
            val args = inst.args.joinToString(", ") { "${typeStr(it.type)} ${valStr(it)}" }
            "${inst.dest.name} = cleanuppad within $parent [$args]"
        }
        is CatchRet -> "catchret from ${valStr(inst.catchPad)} to label %${inst.dest}"
        is CleanupRet -> {
            val unwind = if (inst.unwindDest != null) "unwind label %${inst.unwindDest}" else "unwind to caller"
            "cleanupret from ${valStr(inst.cleanupPad)} $unwind"
        }

        // SSA
        is Phi -> {
            val incoming = inst.incoming.joinToString(", ") { (v, b) -> "[${valStr(v)}, %$b]" }
            "${inst.dest.name} = phi ${typeStr(inst.dest.type)} $incoming"
        }
        is Select -> "${inst.dest.name} = select ${typeStr(Type.I1)} ${valStr(inst.condition)}, ${typeStr(inst.trueValue.type)} ${valStr(inst.trueValue)}, ${typeStr(inst.falseValue.type)} ${valStr(inst.falseValue)}"
        is Freeze -> "${inst.dest.name} = freeze ${typeStr(inst.value.type)} ${valStr(inst.value)}"

        // Vector
        is ExtractElement -> "${inst.dest.name} = extractelement ${typeStr(inst.vector.type)} ${valStr(inst.vector)}, ${typeStr(inst.index.type)} ${valStr(inst.index)}"
        is InsertElement -> "${inst.dest.name} = insertelement ${typeStr(inst.vector.type)} ${valStr(inst.vector)}, ${typeStr(inst.element.type)} ${valStr(inst.element)}, ${typeStr(inst.index.type)} ${valStr(inst.index)}"
        is ShuffleVector -> "${inst.dest.name} = shufflevector ${typeStr(inst.v1.type)} ${valStr(inst.v1)}, ${typeStr(inst.v2.type)} ${valStr(inst.v2)}, <${inst.mask.joinToString(", ")}>"
        is Splat -> "${inst.dest.name} = splat ${typeStr(inst.scalar.type)} ${valStr(inst.scalar)} to ${typeStr(inst.vectorType)}"
        is VectorReduce -> "${inst.dest.name} = vector.reduce.${inst.op.name.lowercase()} ${typeStr(inst.vector.type)} ${valStr(inst.vector)}"

        // Aggregate
        is ExtractValue -> "${inst.dest.name} = extractvalue ${typeStr(inst.aggregate.type)} ${valStr(inst.aggregate)}, ${inst.indices.joinToString(", ")}"
        is InsertValue -> "${inst.dest.name} = insertvalue ${typeStr(inst.aggregate.type)} ${valStr(inst.aggregate)}, ${typeStr(inst.element.type)} ${valStr(inst.element)}, ${inst.indices.joinToString(", ")}"

        // High-level: Objects
        is NewObject -> {
            val typeArgs = if (inst.typeArgs.isNotEmpty()) "<${inst.typeArgs.joinToString(", ") { typeStr(it) }}>" else ""
            "${inst.dest.name} = new ${inst.className}$typeArgs"
        }
        is NewArray -> "${inst.dest.name} = newarray ${typeStr(inst.elementType)}, ${typeStr(inst.size.type)} ${valStr(inst.size)}"
        is NewMultiArray -> {
            val dims = inst.dimensions.joinToString(", ") { "${typeStr(it.type)} ${valStr(it)}" }
            "${inst.dest.name} = newmultiarray ${typeStr(inst.elementType)}, [$dims]"
        }

        // High-level: Fields
        is GetField -> "${inst.dest.name} = getfield ${inst.className}.${inst.fieldName}: ${typeStr(inst.fieldType)}, ${valStr(inst.obj)}"
        is PutField -> "putfield ${inst.className}.${inst.fieldName}: ${typeStr(inst.fieldType)}, ${valStr(inst.obj)}, ${valStr(inst.value)}"
        is GetStatic -> "${inst.dest.name} = getstatic ${inst.className}.${inst.fieldName}: ${typeStr(inst.fieldType)}"
        is PutStatic -> "putstatic ${inst.className}.${inst.fieldName}: ${typeStr(inst.fieldType)}, ${valStr(inst.value)}"

        // High-level: Dispatch
        is VirtualCall -> {
            val args = inst.args.joinToString(", ") { "${typeStr(it.type)} ${valStr(it)}" }
            val dest = if (inst.dest != null) "${inst.dest.name} = " else ""
            "${dest}virtualcall ${valStr(inst.obj)}.${inst.className}::${inst.methodName}($args): ${typeStr(inst.methodType.ret)}"
        }
        is InterfaceCall -> {
            val args = inst.args.joinToString(", ") { "${typeStr(it.type)} ${valStr(it)}" }
            val dest = if (inst.dest != null) "${inst.dest.name} = " else ""
            "${dest}interfacecall ${valStr(inst.obj)}.${inst.interfaceName}::${inst.methodName}($args): ${typeStr(inst.methodType.ret)}"
        }
        is SpecialCall -> {
            val args = inst.args.joinToString(", ") { "${typeStr(it.type)} ${valStr(it)}" }
            val dest = if (inst.dest != null) "${inst.dest.name} = " else ""
            "${dest}specialcall ${valStr(inst.obj)}.${inst.className}::${inst.methodName}($args): ${typeStr(inst.methodType.ret)}"
        }
        is StaticCall -> {
            val args = inst.args.joinToString(", ") { "${typeStr(it.type)} ${valStr(it)}" }
            val dest = if (inst.dest != null) "${inst.dest.name} = " else ""
            "${dest}staticcall ${inst.className}::${inst.methodName}($args): ${typeStr(inst.methodType.ret)}"
        }
        is DynamicCall -> {
            val args = inst.args.joinToString(", ") { "${typeStr(it.type)} ${valStr(it)}" }
            val dest = if (inst.dest != null) "${inst.dest.name} = " else ""
            "${dest}dynamiccall ${inst.name}($args): ${typeStr(inst.methodType.ret)}"
        }
        is ConstructorCall -> {
            val args = inst.args.joinToString(", ") { "${typeStr(it.type)} ${valStr(it)}" }
            "constructorcall ${inst.className}::init(${valStr(inst.obj)}, $args)"
        }

        // High-level: Type ops
        is InstanceOf -> "${inst.dest.name} = instanceof ${valStr(inst.obj)}, ${typeStr(inst.checkType)}"
        is CheckCast -> "${inst.dest.name} = checkcast ${valStr(inst.obj)} to ${typeStr(inst.castType)}"
        is TypeId -> "${inst.dest.name} = typeid ${valStr(inst.obj)}"

        // High-level: Managed arrays
        is ArrayGet -> "${inst.dest.name} = arrayget ${typeStr(inst.elementType)} ${valStr(inst.array)}, ${valStr(inst.index)}"
        is ArraySet -> "arrayset ${typeStr(inst.elementType)} ${valStr(inst.array)}, ${valStr(inst.index)}, ${valStr(inst.value)}"
        is ArrayLength -> "${inst.dest.name} = arraylength ${valStr(inst.array)}"

        // High-level: Monitors
        is MonitorEnter -> "monitorenter ${valStr(inst.obj)}"
        is MonitorExit -> "monitorexit ${valStr(inst.obj)}"

        // High-level: Exceptions
        is Throw -> "throw ${valStr(inst.exception)}"
        is TryCatchRegion -> {
            val catches = inst.catches.joinToString(", ") { "catch ${typeStr(it.exceptionType)} -> %${it.handlerBlock}" }
            val fin = if (inst.finallyBlock != null) " finally %${inst.finallyBlock}" else ""
            "trycatch %${inst.tryBlock} [$catches]$fin"
        }

        // High-level: Box/unbox
        is Box -> "${inst.dest.name} = box ${typeStr(inst.value.type)} ${valStr(inst.value)} to ${typeStr(inst.boxType)}"
        is Unbox -> "${inst.dest.name} = unbox ${valStr(inst.obj)} to ${typeStr(inst.unboxType)}"

        // High-level: Weak references
        is CatchValue -> "${inst.dest.name} = catch.value ${typeStr(inst.exceptionType)}"
        is MakeWeakRef -> "${inst.dest.name} = make.weakref ${valStr(inst.obj)}"
        is ReadWeakRef -> "${inst.dest.name} = read.weakref ${valStr(inst.weakRef)}"
        is ClearWeakRef -> "clear.weakref ${valStr(inst.weakRef)}"

        // High-level: Closures
        is ClosureCreate -> {
            val captures = inst.captures.joinToString(", ") { "${typeStr(it.type)} ${valStr(it)}" }
            "${inst.dest.name} = closure.create ${valStr(inst.function)}, [$captures]: ${typeStr(inst.closureType)}"
        }
        is ClosureInvoke -> {
            val args = inst.args.joinToString(", ") { "${typeStr(it.type)} ${valStr(it)}" }
            val dest = if (inst.dest != null) "${inst.dest.name} = " else ""
            "${dest}closure.invoke ${valStr(inst.closure)}($args): ${typeStr(inst.returnType)}"
        }

        // High-level: Tagged unions
        is ConstructVariant -> {
            val fields = inst.fields.joinToString(", ") { "${typeStr(it.type)} ${valStr(it)}" }
            "${inst.dest.name} = construct.variant ${typeStr(inst.unionType)} ${inst.variantName}($fields)"
        }
        is GetTag -> "${inst.dest.name} = gettag ${valStr(inst.union)}"
        is GetVariantField -> "${inst.dest.name} = getvariantfield ${valStr(inst.union)}.${inst.variantName}[${inst.fieldIndex}]"
        is TagSwitch -> {
            val cases = inst.cases.joinToString(", ") { (variant, target) -> "$variant -> %$target" }
            val default = if (inst.defaultTarget != null) ", default -> %${inst.defaultTarget}" else ""
            "tagswitch ${valStr(inst.union)} [$cases$default]"
        }

        // GC
        is GCAlloc -> {
            val size = if (inst.size != null) ", ${typeStr(inst.size.type)} ${valStr(inst.size)}" else ""
            "${inst.dest.name} = gc.alloc ${typeStr(inst.allocType)}$size"
        }
        is GCSafepoint -> "gc.safepoint"
        is GCRoot -> "gc.root ${valStr(inst.ptr)}${if (inst.metadata != null) ", ${valStr(inst.metadata)}" else ""}"

        // Pinning and interior pointers
        is Pin -> "${inst.dest.name} = gc.pin ${typeStr(inst.ref.type)} ${valStr(inst.ref)}"
        is Unpin -> "gc.unpin ${typeStr(inst.ref.type)} ${valStr(inst.ref)}"
        is InteriorPtr -> "${inst.dest.name} = gc.interior_ptr ${typeStr(inst.ref.type)} ${valStr(inst.ref)}, ${typeStr(inst.index.type)} ${valStr(inst.index)}, ${typeStr(inst.pointeeType)}"
        is WriteBarrier -> "gc.write_barrier ${typeStr(inst.obj.type)} ${valStr(inst.obj)}, ${typeStr(inst.fieldIndex.type)} ${valStr(inst.fieldIndex)}, ${typeStr(inst.value.type)} ${valStr(inst.value)}"
        is ReadBarrier -> "${inst.dest.name} = gc.read_barrier ${typeStr(inst.ref.type)} ${valStr(inst.ref)}"
        is ManagedCall -> {
            val args = inst.args.joinToString(", ") { "${typeStr(it.type)} ${valStr(it)}" }
            val dest = if (inst.dest != null) "${inst.dest.name} = " else ""
            val dir = if (inst.direction == ManagedCallDirection.MANAGED_TO_NATIVE) "managed_to_native" else "native_to_managed"
            "${dest}managed.call $dir ${typeStr(inst.returnType)} ${valStr(inst.function)}($args)"
        }

        // Refcounting
        is RefRetain -> "ref.retain ${valStr(inst.obj)}"
        is RefRelease -> "ref.release ${valStr(inst.obj)}"
        is RefCount -> "${inst.dest.name} = ref.count ${valStr(inst.obj)}"

        // Coroutines
        is CoroBegin -> "${inst.dest.name} = coro.begin ${valStr(inst.id)}, ${valStr(inst.mem)}"
        is CoroEnd -> "coro.end ${valStr(inst.handle)}${if (inst.unwind) " unwind" else ""}"
        is CoroSuspend -> "${inst.dest.name} = coro.suspend${if (inst.save != null) " ${valStr(inst.save)}" else ""}${if (inst.isFinal) " final" else ""}"
        is CoroResume -> "coro.resume ${valStr(inst.handle)}"
        is CoroDestroy -> "coro.destroy ${valStr(inst.handle)}"
        is CoroSize -> "${inst.dest.name} = coro.size"

        // Intrinsic
        is Intrinsic -> {
            val args = inst.args.joinToString(", ") { "${typeStr(it.type)} ${valStr(it)}" }
            val dest = if (inst.dest != null) "${inst.dest.name} = " else ""
            "${dest}intrinsic @${inst.name}($args): ${typeStr(inst.returnType)}"
        }

        // Inline assembly
        is InlineAsm -> {
            val args = inst.args.joinToString(", ") { "${typeStr(it.type)} ${valStr(it)}" }
            val dest = if (inst.dest != null) "${inst.dest.name} = " else ""
            val se = if (inst.sideEffects) " sideeffect" else ""
            "${dest}asm$se ${quote(inst.assembly)}, ${quote(inst.constraints)}($args)"
        }

        // Debug
        is DebugLoc -> "dbg.loc ${inst.line}:${inst.col} scope ${quote(inst.scope)}${if (inst.inlinedAt != null) " inlined_at ${quote(inst.inlinedAt)}" else ""}"
        is DebugValue -> "dbg.value ${quote(inst.variable)} = ${valStr(inst.value)}${if (inst.expression != null) " expr ${quote(inst.expression)}" else ""}"
        is DebugDeclare -> "dbg.declare ${quote(inst.variable)} = ${valStr(inst.address)}${if (inst.expression != null) " expr ${quote(inst.expression)}" else ""}"

        // Hints
        is Assume -> "assume ${valStr(inst.condition)}"
        is Expect -> "${inst.dest.name} = expect ${typeStr(inst.value.type)} ${valStr(inst.value)}, ${constStr(inst.expected)}"
    }

    companion object {
        fun print(module: Module): String = IrPrinter().print(module)

        fun typeStr(type: Type): String = when (type) {
            is Type.I1 -> "i1"
            is Type.I8 -> "i8"
            is Type.I16 -> "i16"
            is Type.I32 -> "i32"
            is Type.I64 -> "i64"
            is Type.I128 -> "i128"
            is Type.IntN -> "i${type.bits}"
            is Type.F16 -> "f16"
            is Type.BF16 -> "bf16"
            is Type.F32 -> "f32"
            is Type.F64 -> "f64"
            is Type.F80 -> "f80"
            is Type.F128 -> "f128"
            is Type.Void -> "void"
            is Type.Label -> "label"
            is Type.Metadata -> "metadata"
            is Type.Token -> "token"
            is Type.Pointer -> if (type.addressSpace != 0) "ptr addrspace(${type.addressSpace})" else "ptr"
            is Type.OpaquePointer -> "ptr"
            is Type.Reference -> {
                val nullable = if (type.nullable) "?" else ""
                "ref<${typeStr(type.referent)}>$nullable"
            }
            is Type.WeakReference -> "weakref<${typeStr(type.referent)}>"
            is Type.InteriorRef -> "interiorref<${typeStr(type.pointee)}>"
            is Type.PinnedRef -> "pinnedref<${typeStr(type.referent)}>"
            is Type.Array -> "[${type.size} x ${typeStr(type.element)}]"
            is Type.Vector -> {
                val scalable = if (type.scalable) "vscale x " else ""
                "<$scalable${type.lanes} x ${typeStr(type.element)}>"
            }
            is Type.Struct -> {
                val packed = if (type.packed) "<" else ""
                val packedEnd = if (type.packed) ">" else ""
                val fields = type.fields.joinToString(", ") { typeStr(it) }
                if (type.name != null) "%${type.name}" else "$packed{ $fields }$packedEnd"
            }
            is Type.OpaqueStruct -> "%${type.name}"
            is Type.Union -> {
                val variants = type.variants.joinToString(" | ") { typeStr(it) }
                if (type.name != null) "%${type.name}" else "union { $variants }"
            }
            is Type.TaggedUnion -> "%${type.name}"
            is Type.Function -> {
                val params = type.params.joinToString(", ") { typeStr(it) }
                val vararg = if (type.vararg) ", ..." else ""
                "${typeStr(type.ret)} ($params$vararg)"
            }
            is Type.ClassRef -> "class @${type.name}"
            is Type.InterfaceRef -> "interface @${type.name}"
            is Type.TypeParam -> "!${type.name}"
            is Type.Parameterized -> "${typeStr(type.base)}<${type.typeArgs.joinToString(", ") { typeStr(it) }}>"
            is Type.Nullable -> "${typeStr(type.inner)}?"
            is Type.PlatformType -> "platform(${type.name})"
        }

        fun valStr(value: Value): String = when (value) {
            is Parameter -> "%${value.name}"
            is InstructionRef -> value.name
            is GlobalRef -> "@${value.name}"
            is FunctionRef -> "@${value.name}"
            is BlockRef -> "%${value.label}"
            is Constant -> constStr(value)
        }

        fun constStr(c: Constant): String = when (c) {
            is Constant.I1 -> if (c.value) "1" else "0"
            is Constant.I8 -> c.value.toString()
            is Constant.I16 -> c.value.toString()
            is Constant.I32 -> c.value.toString()
            is Constant.I64 -> c.value.toString()
            is Constant.I128 -> c.value.toString()
            is Constant.IntN -> c.value.toString()
            is Constant.F16 -> c.value.toString()
            is Constant.BF16 -> c.value.toString()
            is Constant.F32 -> c.value.toString()
            is Constant.F64 -> c.value.toString()
            is Constant.F80 -> c.value.toString()
            is Constant.F128 -> c.value.toString()
            is Constant.NullPtr -> "null"
            is Constant.NullRef -> "null"
            is Constant.Undef -> "undef"
            is Constant.Poison -> "poison"
            is Constant.ZeroInitializer -> "zeroinitializer"
            is Constant.ArrayConst -> "[${c.elements.joinToString(", ") { "${typeStr(it.type)} ${constStr(it)}" }}]"
            is Constant.VectorConst -> "<${c.elements.joinToString(", ") { "${typeStr(it.type)} ${constStr(it)}" }}>"
            is Constant.StructConst -> "{ ${c.fields.joinToString(", ") { "${typeStr(it.type)} ${constStr(it)}" }} }"
            is Constant.StringConst -> "c${quote(c.value)}"
            is Constant.GetElementPtr -> {
                val ib = if (c.inBounds) " inbounds" else ""
                "getelementptr$ib (${typeStr(c.type)}, ${constStr(c.base)}, ${c.indices.joinToString(", ") { constStr(it) }})"
            }
            is Constant.BitCast -> "bitcast (${constStr(c.value)} to ${typeStr(c.type)})"
            is Constant.IntToPtr -> "inttoptr (${constStr(c.value)} to ${typeStr(c.type)})"
            is Constant.PtrToInt -> "ptrtoint (${constStr(c.value)} to ${typeStr(c.type)})"
        }

        private fun metadataStr(md: MetadataValue): String = when (md) {
            is MetadataValue.StringMD -> "!${quote(md.value)}"
            is MetadataValue.IntMD -> "!${md.value}"
            is MetadataValue.NodeMD -> "!{${md.values.joinToString(", ") { metadataStr(it) }}}"
            is MetadataValue.RefMD -> "!${md.name}"
        }

        private fun quote(s: String) = "\"${s.replace("\\", "\\\\").replace("\"", "\\\"")}\""
    }

    private fun line(text: String) { sb.appendLine(text) }
    private fun blank() { sb.appendLine() }
    private fun quote(s: String) = Companion.quote(s)
    private fun typeStr(type: Type) = Companion.typeStr(type)
    private fun valStr(value: Value) = Companion.valStr(value)
    private fun constStr(c: Constant) = Companion.constStr(c)
    private fun metadataStr(md: MetadataValue) = Companion.metadataStr(md)

    private fun wrapFlags(nuw: Boolean, nsw: Boolean): String {
        val flags = buildString {
            if (nuw) append(" nuw")
            if (nsw) append(" nsw")
        }
        return flags
    }

    private fun fmStr(fm: FastMathFlags): String {
        if (fm == FastMathFlags.NONE) return ""
        return buildString {
            if (fm.noNaNs) append(" nnan")
            if (fm.noInfs) append(" ninf")
            if (fm.noSignedZeros) append(" nsz")
            if (fm.allowReciprocal) append(" arcp")
            if (fm.allowContract) append(" contract")
            if (fm.approxFunc) append(" afn")
            if (fm.reassoc) append(" reassoc")
        }
    }
}
