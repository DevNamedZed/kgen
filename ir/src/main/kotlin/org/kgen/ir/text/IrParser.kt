package org.kgen.ir.text

import org.kgen.ir.*
import org.kgen.ir.types.*

/**
 * Parses IR text (as produced by [IrPrinter]) back into a [Module].
 *
 * ```kotlin
 * val module = IrParser.parse(irText)
 * ```
 *
 * The parser is a recursive-descent parser with a simple character-level lexer.
 * It handles all module-level constructs (structs, classes, interfaces, enums,
 * globals, functions) and all instruction types.
 */
class IrParser private constructor(private val input: String) {

    private var pos = 0
    private val length = input.length

    companion object {
        @JvmStatic
        fun parse(text: String): Module = IrParser(text).parseModule()
    }

    private fun parseModule(): Module {
        var name = "module"
        var targetTriple: String? = null
        var dataLayout: String? = null
        var sourceFile: String? = null
        val targetFeatures = mutableSetOf<String>()
        val aliases = mutableListOf<TypeAlias>()
        val structs = mutableListOf<StructDef>()
        val classes = mutableListOf<ClassDef>()
        val interfaces = mutableListOf<InterfaceDef>()
        val enums = mutableListOf<EnumDef>()
        val globals = mutableListOf<Global>()
        val globalCtors = mutableListOf<GlobalCtor>()
        val globalDtors = mutableListOf<GlobalCtor>()
        val comdats = mutableListOf<ComdatDef>()
        val ifuncs = mutableListOf<IFunc>()
        var moduleInlineAsm: String? = null
        val functions = mutableListOf<IrFunction>()
        val metadata = mutableMapOf<String, MetadataValue>()
        val moduleFlags = mutableMapOf<String, ModuleFlagValue>()

        skipWhitespace()
        while (pos < length) {
            when {
                lookingAt("module asm ") -> {
                    advance("module asm ".length)
                    moduleInlineAsm = parseString()
                }
                lookingAt("module ") -> {
                    advance("module ".length)
                    name = parseString()
                }
                lookingAt("target triple = ") -> {
                    advance("target triple = ".length)
                    targetTriple = parseString()
                }
                lookingAt("target datalayout = ") -> {
                    advance("target datalayout = ".length)
                    dataLayout = parseString()
                }
                lookingAt("target features = ") -> {
                    advance("target features = ".length)
                    do {
                        targetFeatures.add(parseString())
                    } while (tryConsume(", "))
                }
                lookingAt("source_file ") -> {
                    advance("source_file ".length)
                    sourceFile = parseString()
                }
                lookingAt("type @") -> {
                    advance("type @".length)
                    val aliasName = parseIdent()
                    expect(" = ")
                    val aliasType = parseType()
                    aliases.add(TypeAlias(aliasName, aliasType))
                }
                lookingAt("struct %") -> {
                    structs.add(parseStructDef())
                }
                lookingAt("comdat @") -> {
                    advance("comdat @".length)
                    val cName = parseIdent()
                    expect(" = ")
                    val kind = parseEnumValue<ComdatSelectionKind>()
                    comdats.add(ComdatDef(cName, kind))
                }
                lookingAt("enum ") -> {
                    enums.add(parseEnumDef())
                }
                lookingAtClassKeyword() -> {
                    classes.add(parseClassDef())
                }
                lookingAt("interface ") -> {
                    interfaces.add(parseInterfaceDef())
                }
                lookingAt("@llvm.global_ctors") -> {
                    advance("@llvm.global_ctors".length)
                    expect(" = { ")
                    val priority = parseInt()
                    expect(", @")
                    val func = parseIdent()
                    expect(" }")
                    globalCtors.add(GlobalCtor(func, priority))
                }
                lookingAt("@llvm.global_dtors") -> {
                    advance("@llvm.global_dtors".length)
                    expect(" = { ")
                    val priority = parseInt()
                    expect(", @")
                    val func = parseIdent()
                    expect(" }")
                    globalDtors.add(GlobalCtor(func, priority))
                }
                lookingAt("@") && lookingAtGlobal() -> {
                    val result = parseGlobalOrIFunc()
                    when (result) {
                        is GlobalOrIFunc.GlobalResult -> globals.add(result.global)
                        is GlobalOrIFunc.IFuncResult -> ifuncs.add(result.ifunc)
                    }
                }
                lookingAt("define ") || lookingAt("declare ") -> {
                    functions.add(parseFunction())
                }
                lookingAt("!llvm.module.flags.") -> {
                    advance("!llvm.module.flags.".length)
                    val key = parseIdent()
                    expect(" = !{")
                    skipWhitespace()
                    val value = if (peek() == '"') {
                        ModuleFlagValue.StringFlag(parseString())
                    } else if (peek() == '!') {
                        advance(1)
                        ModuleFlagValue.MetadataFlag(parseMetadataInner())
                    } else {
                        ModuleFlagValue.IntFlag(parseLong())
                    }
                    expect("}")
                    moduleFlags[key] = value
                }
                lookingAt("!") -> {
                    advance(1)
                    val key = parseIdent()
                    expect(" = ")
                    val md = parseMetadataValue()
                    metadata[key] = md
                }
                else -> {
                    advance(1)
                }
            }
            skipWhitespace()
        }

        return Module(
            name = name,
            targetTriple = targetTriple,
            dataLayout = dataLayout,
            functions = functions,
            globals = globals,
            structs = structs,
            classes = classes,
            interfaces = interfaces,
            enums = enums,
            aliases = aliases,
            metadata = metadata,
            sourceFile = sourceFile,
            targetFeatures = targetFeatures,
            globalCtors = globalCtors,
            globalDtors = globalDtors,
            ifuncs = ifuncs,
            comdats = comdats,
            moduleInlineAsm = moduleInlineAsm,
            moduleFlags = moduleFlags,
        )
    }

    private fun parseStructDef(): StructDef {
        expect("struct %")
        val name = parseIdent()
        skipWhitespace()
        val packed = tryConsume("packed")
        if (packed) skipWhitespace()
        expect("{ ")
        val fields = mutableListOf<Param>()
        while (!lookingAt("}")) {
            if (fields.isNotEmpty()) expect(", ")
            val fname = parseIdent()
            expect(": ")
            val ftype = parseType()
            fields.add(Param(fname, ftype))
        }
        expect("}")
        return StructDef(name, fields, packed)
    }

    private fun parseClassDef(): ClassDef {
        var visibility = ClassVisibility.PUBLIC
        var isAbstract = false
        var isFinal = false

        if (tryConsume("private ")) visibility = ClassVisibility.PRIVATE
        else if (tryConsume("internal ")) visibility = ClassVisibility.INTERNAL

        if (tryConsume("abstract ")) isAbstract = true
        if (tryConsume("final ")) isFinal = true

        expect("class ")
        val name = parseIdent()
        skipWhitespace()

        var superClass: String? = null
        if (tryConsume("extends ")) {
            superClass = parseIdent()
            skipWhitespace()
        }

        val ifaces = mutableListOf<String>()
        if (tryConsume("implements ")) {
            do {
                ifaces.add(parseIdent())
                skipWhitespace()
            } while (tryConsume(", "))
        }

        expect("{")
        skipWhitespace()

        val fields = mutableListOf<FieldDef>()
        val methods = mutableListOf<MethodDef>()
        val constructors = mutableListOf<MethodDef>()

        while (!lookingAt("}")) {
            if (lookingAt("field ")) {
                fields.add(parseFieldDef())
            } else if (lookingAt("method ")) {
                val m = parseMethodDef()
                if (m.name == "<init>" || m.name == "<clinit>") constructors.add(m) else methods.add(m)
            }
            skipWhitespace()
        }
        expect("}")

        return ClassDef(
            name = name,
            superClass = superClass,
            interfaces = ifaces,
            fields = fields,
            methods = methods,
            constructors = constructors,
            visibility = visibility,
            isAbstract = isAbstract,
            isFinal = isFinal,
        )
    }

    private fun parseFieldDef(): FieldDef {
        expect("field ")
        val vis = parseMemberVisibility()
        val isFinal = tryConsume(" final")
        skipWhitespace()
        val name = parseIdent()
        expect(": ")
        val type = parseType()
        return FieldDef(name, type, visibility = vis, isFinal = isFinal)
    }

    private fun parseMemberVisibility(): MemberVisibility {
        for (v in MemberVisibility.entries) {
            if (tryConsume(v.name.lowercase())) return v
        }
        return MemberVisibility.PUBLIC
    }

    private fun parseMethodDef(): MethodDef {
        expect("method ")
        val vis = parseMemberVisibility()
        val isAbstract = tryConsume(" abstract")
        val isFinal = tryConsume(" final")
        val isStatic = tryConsume(" static")
        skipWhitespace()
        val name = parseIdent()
        expect("(")
        val params = mutableListOf<Param>()
        while (!lookingAt(")")) {
            if (params.isNotEmpty()) expect(", ")
            val pname = parseIdent()
            expect(": ")
            val ptype = parseType()
            params.add(Param(pname, ptype))
        }
        expect(")")
        expect(": ")
        val returnType = parseType()
        return MethodDef(
            name = name,
            params = params,
            returnType = returnType,
            visibility = vis,
            isAbstract = isAbstract,
            isFinal = isFinal,
            isStatic = isStatic,
        )
    }

    private fun parseInterfaceDef(): InterfaceDef {
        expect("interface ")
        val name = parseIdent()
        skipWhitespace()

        val supers = mutableListOf<String>()
        if (tryConsume("extends ")) {
            do {
                supers.add(parseIdent())
                skipWhitespace()
            } while (tryConsume(", "))
        }

        expect("{")
        skipWhitespace()

        val methods = mutableListOf<MethodDef>()
        while (!lookingAt("}")) {
            if (lookingAt("method ")) {
                methods.add(parseMethodDef())
            }
            skipWhitespace()
        }
        expect("}")

        return InterfaceDef(name = name, superInterfaces = supers, methods = methods)
    }

    private fun parseEnumDef(): EnumDef {
        expect("enum ")
        val name = parseIdent()
        skipWhitespace()
        expect("{")
        skipWhitespace()

        val variants = mutableListOf<EnumVariant>()
        while (!lookingAt("}")) {
            val vname = parseIdent()
            val fields = mutableListOf<Param>()
            if (tryConsume("(")) {
                while (!lookingAt(")")) {
                    if (fields.isNotEmpty()) expect(", ")
                    val fname = parseIdent()
                    expect(": ")
                    val ftype = parseType()
                    fields.add(Param(fname, ftype))
                }
                expect(")")
            }
            expect(" = ")
            val ordinal = parseInt()
            variants.add(EnumVariant(vname, ordinal, fields))
            skipWhitespace()
        }
        expect("}")

        return EnumDef(name = name, variants = variants)
    }

    private sealed interface GlobalOrIFunc {
        data class GlobalResult(val global: Global) : GlobalOrIFunc
        data class IFuncResult(val ifunc: IFunc) : GlobalOrIFunc
    }

    private fun parseGlobalOrIFunc(): GlobalOrIFunc {
        expect("@")
        val name = parseIdent()
        expect(" = ")

        var linkage = Linkage.EXTERNAL
        var visibility = Visibility.DEFAULT

        linkage = tryParseLinkage() ?: Linkage.EXTERNAL
        visibility = tryParseVisibility() ?: Visibility.DEFAULT

        if (tryConsume("ifunc ")) {
            val type = parseType()
            val funcType = type as? Type.Function ?: Type.Function(emptyList(), type)
            expect(", @")
            val resolver = parseIdent()
            return GlobalOrIFunc.IFuncResult(IFunc(name, resolver, funcType, linkage, visibility))
        }

        val isConstant = if (tryConsume("constant ")) true
        else { tryConsume("global "); false }

        val type = parseType()
        var initializer: Constant? = null
        if (tryConsume(" = ")) {
            initializer = parseConstant(type)
        }
        var align: Int? = null
        if (tryConsume(", align ")) {
            align = parseInt()
        }

        return GlobalOrIFunc.GlobalResult(Global(
            name = name,
            type = type,
            initializer = initializer,
            isConstant = isConstant,
            linkage = linkage,
            align = align,
        ))
    }

    private fun parseFunction(): IrFunction {
        val isExternal = if (tryConsume("declare ")) true
        else { expect("define "); false }

        val linkage = tryParseLinkage() ?: Linkage.EXTERNAL
        val visibility = tryParseVisibility() ?: Visibility.DEFAULT
        val callingConv = tryParseCallingConv() ?: CallingConvention.C

        val returnType = parseType()
        expect(" @")
        val name = parseIdent()
        expect("(")

        val params = mutableListOf<Parameter>()
        var isVarArg = false
        while (!lookingAt(")")) {
            if (params.isNotEmpty()) expect(", ")
            if (tryConsume("...")) {
                isVarArg = true
                break
            }
            val ptype = parseType()
            expect(" %")
            val pname = parseIdent()
            params.add(Parameter(pname, ptype, params.size))
            if (lookingAt(", ...")) {
                expect(", ...")
                isVarArg = true
                break
            }
        }
        expect(")")

        val attributes = mutableSetOf<FnAttribute>()
        while (lookingAt(" #")) {
            advance(2)
            val attrName = parseIdent()
            val attr = FnAttribute.entries.find { it.name.lowercase() == attrName }
            if (attr != null) attributes.add(attr)
        }

        var personality: FunctionRef? = null
        if (tryConsume(" personality @")) {
            val persName = parseIdent()
            personality = FunctionRef(persName, Type.Function(emptyList(), Type.Void))
        }

        val blocks = mutableListOf<BasicBlock>()
        if (!isExternal) {
            skipWhitespace()
            expect("{")
            skipWhitespace()
            while (!lookingAt("}")) {
                blocks.add(parseBlock(params))
                skipWhitespace()
            }
            expect("}")
        }

        return IrFunction(
            name = name,
            params = params,
            returnType = returnType,
            blocks = blocks,
            linkage = linkage,
            visibility = visibility,
            callingConv = callingConv,
            isVarArg = isVarArg,
            attributes = attributes,
            isExternal = isExternal,
            personality = personality,
        )
    }

    private fun parseBlock(params: List<Parameter>): BasicBlock {
        val label = parseIdent()
        expect(":")
        skipWhitespace()

        val instructions = mutableListOf<Instruction>()
        while (pos < length && !lookingAt("}") && !lookingAtBlockLabel()) {
            val inst = parseInstruction(params)
            if (inst != null) instructions.add(inst)
            skipWhitespace()
        }

        return BasicBlock(label, instructions)
    }

    private fun lookingAtBlockLabel(): Boolean {
        val saved = pos
        try {
            if (pos >= length || !isIdentStart(input[pos])) return false
            while (pos < length && isIdentChar(input[pos])) pos++
            skipSpaces()
            return pos < length && input[pos] == ':' && (pos + 1 >= length || input[pos + 1] != ':')
        } finally {
            pos = saved
        }
    }

    private fun parseInstruction(params: List<Parameter>): Instruction? {
        if (pos >= length) return null

        // Check if this is an assignment: %name = ...
        if (lookingAt("%")) {
            val saved = pos
            advance(1)
            val destName = parseIdent()
            skipSpaces()
            if (tryConsume("= ")) {
                return parseInstructionWithDest("%$destName", params)
            }
            pos = saved
        }

        return parseInstructionNoDest(params)
    }

    private fun parseInstructionWithDest(destName: String, params: List<Parameter>): Instruction {
        return when {
            // Integer arithmetic
            tryConsume("add") -> parseBinaryOp(destName) { dest, lhs, rhs, flags -> Instruction.Add(dest, lhs, rhs, "nuw" in flags, "nsw" in flags) }
            tryConsume("sub") -> parseBinaryOp(destName) { dest, lhs, rhs, flags -> Instruction.Sub(dest, lhs, rhs, "nuw" in flags, "nsw" in flags) }
            tryConsume("mul") -> parseBinaryOp(destName) { dest, lhs, rhs, flags -> Instruction.Mul(dest, lhs, rhs, "nuw" in flags, "nsw" in flags) }
            tryConsume("udiv") -> parseBinaryOp(destName) { dest, lhs, rhs, flags -> Instruction.UDiv(dest, lhs, rhs, "exact" in flags) }
            tryConsume("sdiv") -> parseBinaryOp(destName) { dest, lhs, rhs, flags -> Instruction.SDiv(dest, lhs, rhs, "exact" in flags) }
            tryConsume("urem ") -> parseBinarySimple(destName) { dest, lhs, rhs -> Instruction.URem(dest, lhs, rhs) }
            tryConsume("srem ") -> parseBinarySimple(destName) { dest, lhs, rhs -> Instruction.SRem(dest, lhs, rhs) }
            tryConsume("neg ") -> parseUnary(destName) { dest, op -> Instruction.Neg(dest, op) }

            // Overflow-checked
            tryConsume("sadd.overflow ") -> parseBinarySimple(destName) { dest, lhs, rhs -> Instruction.SAddOverflow(dest, lhs, rhs) }
            tryConsume("uadd.overflow ") -> parseBinarySimple(destName) { dest, lhs, rhs -> Instruction.UAddOverflow(dest, lhs, rhs) }
            tryConsume("ssub.overflow ") -> parseBinarySimple(destName) { dest, lhs, rhs -> Instruction.SSubOverflow(dest, lhs, rhs) }
            tryConsume("usub.overflow ") -> parseBinarySimple(destName) { dest, lhs, rhs -> Instruction.USubOverflow(dest, lhs, rhs) }
            tryConsume("smul.overflow ") -> parseBinarySimple(destName) { dest, lhs, rhs -> Instruction.SMulOverflow(dest, lhs, rhs) }
            tryConsume("umul.overflow ") -> parseBinarySimple(destName) { dest, lhs, rhs -> Instruction.UMulOverflow(dest, lhs, rhs) }

            // Saturating
            tryConsume("sadd.sat ") -> parseBinarySimple(destName) { dest, lhs, rhs -> Instruction.SAddSat(dest, lhs, rhs) }
            tryConsume("uadd.sat ") -> parseBinarySimple(destName) { dest, lhs, rhs -> Instruction.UAddSat(dest, lhs, rhs) }
            tryConsume("ssub.sat ") -> parseBinarySimple(destName) { dest, lhs, rhs -> Instruction.SSubSat(dest, lhs, rhs) }
            tryConsume("usub.sat ") -> parseBinarySimple(destName) { dest, lhs, rhs -> Instruction.USubSat(dest, lhs, rhs) }

            // Min/max
            tryConsume("smin ") -> parseBinarySimple(destName) { dest, lhs, rhs -> Instruction.SMin(dest, lhs, rhs) }
            tryConsume("smax ") -> parseBinarySimple(destName) { dest, lhs, rhs -> Instruction.SMax(dest, lhs, rhs) }
            tryConsume("umin ") -> parseBinarySimple(destName) { dest, lhs, rhs -> Instruction.UMin(dest, lhs, rhs) }
            tryConsume("umax ") -> parseBinarySimple(destName) { dest, lhs, rhs -> Instruction.UMax(dest, lhs, rhs) }
            tryConsume("abs ") -> {
                val (type, operand) = parseTypedValue(params)
                val isIntMin = tryConsume(" int_min")
                Instruction.Abs(InstructionRef(destName, type), operand, isIntMin)
            }

            // Float arithmetic
            tryConsume("fadd") -> parseFloatBinaryOp(destName, params) { dest, lhs, rhs, fm -> Instruction.FAdd(dest, lhs, rhs, fm) }
            tryConsume("fsub") -> parseFloatBinaryOp(destName, params) { dest, lhs, rhs, fm -> Instruction.FSub(dest, lhs, rhs, fm) }
            tryConsume("fmul") -> parseFloatBinaryOp(destName, params) { dest, lhs, rhs, fm -> Instruction.FMul(dest, lhs, rhs, fm) }
            tryConsume("fdiv") -> parseFloatBinaryOp(destName, params) { dest, lhs, rhs, fm -> Instruction.FDiv(dest, lhs, rhs, fm) }
            tryConsume("frem") -> parseFloatBinaryOp(destName, params) { dest, lhs, rhs, fm -> Instruction.FRem(dest, lhs, rhs, fm) }
            tryConsume("fneg") -> {
                val fm = parseFastMathFlags()
                expect(" ")
                val (type, operand) = parseTypedValue(params)
                Instruction.FNeg(InstructionRef(destName, type), operand, fm)
            }
            tryConsume("fabs ") -> parseUnary(destName) { dest, op -> Instruction.FAbs(dest, op) }
            tryConsume("fma ") -> {
                val (aType, a) = parseTypedValue(params)
                expect(", ")
                val b = parseValue(aType, params)
                expect(", ")
                val c = parseValue(aType, params)
                Instruction.FMA(InstructionRef(destName, aType), a, b, c)
            }
            tryConsume("fmin ") -> parseBinarySimple(destName) { dest, lhs, rhs -> Instruction.FMin(dest, lhs, rhs) }
            tryConsume("fmax ") -> parseBinarySimple(destName) { dest, lhs, rhs -> Instruction.FMax(dest, lhs, rhs) }
            tryConsume("sqrt ") -> parseUnary(destName) { dest, op -> Instruction.Sqrt(dest, op) }
            tryConsume("ceil ") -> parseUnary(destName) { dest, op -> Instruction.Ceil(dest, op) }
            tryConsume("floor ") -> parseUnary(destName) { dest, op -> Instruction.Floor(dest, op) }
            tryConsume("round ") -> parseUnary(destName) { dest, op -> Instruction.Round(dest, op) }
            tryConsume("trunc ") -> parseUnary(destName) { dest, op -> Instruction.Trunc(dest, op) }
            tryConsume("copysign ") -> {
                val (type, mag) = parseTypedValue(params)
                expect(", ")
                val sign = parseValue(type, params)
                Instruction.CopySign(InstructionRef(destName, type), mag, sign)
            }

            // Bitwise
            tryConsume("and ") -> parseBinarySimple(destName) { dest, lhs, rhs -> Instruction.And(dest, lhs, rhs) }
            tryConsume("or ") -> parseBinarySimple(destName) { dest, lhs, rhs -> Instruction.Or(dest, lhs, rhs) }
            tryConsume("xor ") -> parseBinarySimple(destName) { dest, lhs, rhs -> Instruction.Xor(dest, lhs, rhs) }
            tryConsume("not ") -> parseUnary(destName) { dest, op -> Instruction.Not(dest, op) }
            tryConsume("shl") -> parseBinaryOp(destName) { dest, lhs, rhs, flags -> Instruction.Shl(dest, lhs, rhs, "nuw" in flags, "nsw" in flags) }
            tryConsume("lshr") -> parseBinaryOp(destName) { dest, lhs, rhs, flags -> Instruction.LShr(dest, lhs, rhs, "exact" in flags) }
            tryConsume("ashr") -> parseBinaryOp(destName) { dest, lhs, rhs, flags -> Instruction.AShr(dest, lhs, rhs, "exact" in flags) }
            tryConsume("rotl ") -> {
                val (type, value) = parseTypedValue(params)
                expect(", ")
                val amount = parseValue(type, params)
                Instruction.RotateLeft(InstructionRef(destName, type), value, amount)
            }
            tryConsume("rotr ") -> {
                val (type, value) = parseTypedValue(params)
                expect(", ")
                val amount = parseValue(type, params)
                Instruction.RotateRight(InstructionRef(destName, type), value, amount)
            }

            // Bit manipulation
            tryConsume("ctlz ") -> {
                val (type, operand) = parseTypedValue(params)
                val zp = tryConsume(" zero_poison")
                Instruction.Ctlz(InstructionRef(destName, type), operand, zp)
            }
            tryConsume("cttz ") -> {
                val (type, operand) = parseTypedValue(params)
                val zp = tryConsume(" zero_poison")
                Instruction.Cttz(InstructionRef(destName, type), operand, zp)
            }
            tryConsume("ctpop ") -> parseUnary(destName) { dest, op -> Instruction.Ctpop(dest, op) }
            tryConsume("bswap ") -> parseUnary(destName) { dest, op -> Instruction.BSwap(dest, op) }
            tryConsume("bitreverse ") -> parseUnary(destName) { dest, op -> Instruction.BitReverse(dest, op) }

            // Comparison
            tryConsume("icmp ") -> {
                val pred = parseEnumValue<ICmpPredicate>()
                expect(" ")
                val (type, lhs) = parseTypedValue(params)
                expect(", ")
                val rhs = parseValue(type, params)
                Instruction.ICmp(InstructionRef(destName, Type.I1), pred, lhs, rhs)
            }
            tryConsume("fcmp") -> {
                val fm = parseFastMathFlags()
                expect(" ")
                val pred = parseEnumValue<FCmpPredicate>()
                expect(" ")
                val (type, lhs) = parseTypedValue(params)
                expect(", ")
                val rhs = parseValue(type, params)
                Instruction.FCmp(InstructionRef(destName, Type.I1), pred, lhs, rhs, fm)
            }

            // Memory
            tryConsume("alloca ") -> {
                val allocType = parseType()
                var numElements: Value? = null
                var align: Int? = null
                if (tryConsume(", ") && !lookingAt("align ")) {
                    val (_, num) = parseTypedValue(params)
                    numElements = num
                    if (tryConsume(", ")) { /* fall through to align */ }
                }
                if (tryConsume("align ")) align = parseInt()
                Instruction.Alloca(InstructionRef(destName, Type.OpaquePointer), allocType, numElements, align)
            }
            tryConsume("load ") -> {
                val volatile = tryConsume("volatile ")
                val loadType = parseType()
                expect(", ptr ")
                val ptr = parseValue(Type.OpaquePointer, params)
                var align: Int? = null
                var ordering: AtomicOrdering? = null
                if (tryConsume(", align ")) align = parseInt()
                if (tryConsume(" ")) ordering = tryParseAtomicOrdering()
                Instruction.Load(InstructionRef(destName, loadType), ptr, loadType, align, volatile, ordering)
            }
            tryConsume("getelementptr") -> {
                val inBounds = tryConsume(" inbounds")
                expect(" ")
                val baseType = parseType()
                expect(", ptr ")
                val ptr = parseValue(Type.OpaquePointer, params)
                val indices = mutableListOf<Value>()
                while (tryConsume(", ")) {
                    val (_, idx) = parseTypedValue(params)
                    indices.add(idx)
                }
                Instruction.GetElementPtr(InstructionRef(destName, Type.OpaquePointer), baseType, ptr, indices, inBounds)
            }

            // Atomics
            tryConsume("cmpxchg") -> {
                val weak = tryConsume(" weak")
                val volatile = tryConsume(" volatile")
                expect(" ptr ")
                val ptr = parseValue(Type.OpaquePointer, params)
                expect(", ")
                val (cmpType, cmp) = parseTypedValue(params)
                expect(", ")
                val (_, newVal) = parseTypedValue(params)
                expect(" ")
                val succOrd = parseEnumValue<AtomicOrdering>()
                expect(" ")
                val failOrd = parseEnumValue<AtomicOrdering>()
                Instruction.CmpXchg(InstructionRef(destName, Type.Struct(null, listOf(cmpType, Type.I1))), ptr, cmp, newVal, succOrd, failOrd, weak, volatile)
            }
            tryConsume("atomicrmw") -> {
                val volatile = tryConsume(" volatile")
                expect(" ")
                val op = parseEnumValue<AtomicRMWOp>()
                expect(" ptr ")
                val ptr = parseValue(Type.OpaquePointer, params)
                expect(", ")
                val (type, value) = parseTypedValue(params)
                expect(" ")
                val ordering = parseEnumValue<AtomicOrdering>()
                Instruction.AtomicRMW(InstructionRef(destName, type), op, ptr, value, ordering, volatile)
            }

            // Stack
            tryConsume("stacksave") -> Instruction.StackSave(InstructionRef(destName, Type.OpaquePointer))

            // Conversions
            tryConsume("inttrunc ") -> parseConversion(destName, params) { dest, value, toType -> Instruction.IntTrunc(dest, value, toType) }
            tryConsume("zext ") -> parseConversion(destName, params) { dest, value, toType -> Instruction.ZExt(dest, value, toType) }
            tryConsume("sext ") -> parseConversion(destName, params) { dest, value, toType -> Instruction.SExt(dest, value, toType) }
            tryConsume("fptrunc ") -> parseConversion(destName, params) { dest, value, toType -> Instruction.FPTrunc(dest, value, toType) }
            tryConsume("fpext ") -> parseConversion(destName, params) { dest, value, toType -> Instruction.FPExt(dest, value, toType) }
            tryConsume("fptoui ") -> parseConversion(destName, params) { dest, value, toType -> Instruction.FPToUI(dest, value, toType) }
            tryConsume("fptosi ") -> parseConversion(destName, params) { dest, value, toType -> Instruction.FPToSI(dest, value, toType) }
            tryConsume("uitofp ") -> parseConversion(destName, params) { dest, value, toType -> Instruction.UIToFP(dest, value, toType) }
            tryConsume("sitofp ") -> parseConversion(destName, params) { dest, value, toType -> Instruction.SIToFP(dest, value, toType) }
            tryConsume("ptrtoint ") -> {
                expect("ptr ")
                val value = parseValue(Type.OpaquePointer, params)
                expect(" to ")
                val toType = parseType()
                Instruction.PtrToInt(InstructionRef(destName, toType), value, toType)
            }
            tryConsume("inttoptr ") -> {
                val (fromType, value) = parseTypedValue(params)
                expect(" to ptr")
                Instruction.IntToPtr(InstructionRef(destName, Type.OpaquePointer), value, Type.OpaquePointer)
            }
            tryConsume("bitcast ") -> parseConversion(destName, params) { dest, value, toType -> Instruction.BitCast(dest, value, toType) }
            tryConsume("addrspacecast ") -> parseConversion(destName, params) { dest, value, toType -> Instruction.AddrSpaceCast(dest, value, toType) }

            // Calls
            tryConsume("tail call ") || tryConsume("musttail call ") || tryConsume("notail call ") || tryConsume("call ") -> {
                val tailStr = input.substring(input.lastIndexOf('\n', pos - 1).coerceAtLeast(0), pos)
                val tailKind = when {
                    "musttail" in tailStr -> TailCallKind.MUSTTAIL
                    "notail" in tailStr -> TailCallKind.NOTAIL
                    "tail call" in tailStr && "musttail" !in tailStr -> TailCallKind.TAIL
                    else -> TailCallKind.NONE
                }
                val cc = tryParseCallingConv() ?: CallingConvention.C
                if (cc != CallingConvention.C) skipWhitespace()
                val retType = parseType()
                expect(" ")
                val function = parseValue(Type.Function(emptyList(), retType), params)
                expect("(")
                val args = parseCallArgs(params)
                expect(")")
                val dest = if (retType != Type.Void) InstructionRef(destName, retType) else null
                Instruction.Call(dest, function, args, retType, cc, tailKind)
            }
            tryConsume("invoke ") -> {
                val retType = parseType()
                expect(" ")
                val function = parseValue(Type.Function(emptyList(), retType), params)
                expect("(")
                val args = parseCallArgs(params)
                expect(")")
                expect(" to label %")
                val normalDest = parseIdent()
                expect(" unwind label %")
                val unwindDest = parseIdent()
                val dest = if (retType != Type.Void) InstructionRef(destName, retType) else null
                Instruction.Invoke(dest, function, args, retType, normalDest, unwindDest)
            }
            tryConsume("callbr ") -> {
                val retType = parseType()
                expect(" ")
                val function = parseValue(Type.Function(emptyList(), retType), params)
                expect("(")
                val args = parseCallArgs(params)
                expect(")")
                expect(" to label %")
                val fallthrough = parseIdent()
                expect(" [")
                val indirect = mutableListOf<String>()
                while (!lookingAt("]")) {
                    if (indirect.isNotEmpty()) expect(", ")
                    expect("label %")
                    indirect.add(parseIdent())
                }
                expect("]")
                val dest = if (retType != Type.Void) InstructionRef(destName, retType) else null
                Instruction.CallBr(dest, function, args, retType, fallthrough, indirect)
            }

            // Varargs
            tryConsume("va_arg ") -> {
                val argList = parseValue(Type.OpaquePointer, params)
                expect(", ")
                val argType = parseType()
                Instruction.VAArg(InstructionRef(destName, argType), argList, argType)
            }

            // Exception handling
            tryConsume("landingpad ") -> {
                val resultType = parseType()
                val cleanup = tryConsume(" cleanup")
                skipWhitespace()
                val clauses = mutableListOf<LandingPadClause>()
                while (tryConsume("catch ") || lookingAt("filter ")) {
                    if (input[pos - 1] == ' ' || tryConsume("filter ")) {
                        // already consumed "catch " or "filter "
                        if (input.substring(pos - 7, pos).startsWith("filter")) {
                            expect("[")
                            val types = mutableListOf<Value>()
                            while (!lookingAt("]")) {
                                if (types.isNotEmpty()) expect(", ")
                                val (_, v) = parseTypedValue(params)
                                types.add(v)
                            }
                            expect("]")
                            clauses.add(LandingPadClause.Filter(types))
                        } else {
                            val (_, v) = parseTypedValue(params)
                            clauses.add(LandingPadClause.Catch(v))
                        }
                    }
                    skipWhitespace()
                }
                Instruction.LandingPad(InstructionRef(destName, resultType), resultType, clauses, cleanup)
            }
            tryConsume("catchswitch within ") -> {
                val parentPad = if (tryConsume("none")) null else parseValue(Type.Token, params)
                expect(" [")
                val handlers = mutableListOf<String>()
                while (!lookingAt("]")) {
                    if (handlers.isNotEmpty()) expect(", ")
                    expect("label %")
                    handlers.add(parseIdent())
                }
                expect("]")
                val unwindDest = if (tryConsume(" unwind label %")) parseIdent()
                else { tryConsume(" unwind to caller"); null }
                Instruction.CatchSwitch(InstructionRef(destName, Type.Token), parentPad, handlers, unwindDest)
            }
            tryConsume("catchpad within ") -> {
                val catchSwitch = parseValue(Type.Token, params)
                expect(" [")
                val args = mutableListOf<Value>()
                while (!lookingAt("]")) {
                    if (args.isNotEmpty()) expect(", ")
                    val (_, v) = parseTypedValue(params)
                    args.add(v)
                }
                expect("]")
                Instruction.CatchPad(InstructionRef(destName, Type.Token), catchSwitch, args)
            }
            tryConsume("cleanuppad within ") -> {
                val parentPad = if (tryConsume("none")) null else parseValue(Type.Token, params)
                expect(" [")
                val args = mutableListOf<Value>()
                while (!lookingAt("]")) {
                    if (args.isNotEmpty()) expect(", ")
                    val (_, v) = parseTypedValue(params)
                    args.add(v)
                }
                expect("]")
                Instruction.CleanupPad(InstructionRef(destName, Type.Token), parentPad, args)
            }

            // SSA
            tryConsume("phi ") -> {
                val type = parseType()
                expect(" ")
                val incoming = mutableListOf<Pair<Value, String>>()
                do {
                    expect("[")
                    val v = parseValue(type, params)
                    expect(", %")
                    val block = parseIdent()
                    expect("]")
                    incoming.add(v to block)
                } while (tryConsume(", "))
                Instruction.Phi(InstructionRef(destName, type), incoming)
            }
            tryConsume("select ") -> {
                parseType() // i1
                expect(" ")
                val cond = parseValue(Type.I1, params)
                expect(", ")
                val (trueType, trueVal) = parseTypedValue(params)
                expect(", ")
                val (_, falseVal) = parseTypedValue(params)
                Instruction.Select(InstructionRef(destName, trueType), cond, trueVal, falseVal)
            }
            tryConsume("freeze ") -> {
                val (type, value) = parseTypedValue(params)
                Instruction.Freeze(InstructionRef(destName, type), value)
            }

            // Vector
            tryConsume("extractelement ") -> {
                val (vecType, vector) = parseTypedValue(params)
                expect(", ")
                val (_, index) = parseTypedValue(params)
                val elemType = (vecType as Type.Vector).element
                Instruction.ExtractElement(InstructionRef(destName, elemType), vector, index)
            }
            tryConsume("insertelement ") -> {
                val (vecType, vector) = parseTypedValue(params)
                expect(", ")
                val (_, element) = parseTypedValue(params)
                expect(", ")
                val (_, index) = parseTypedValue(params)
                Instruction.InsertElement(InstructionRef(destName, vecType), vector, element, index)
            }
            tryConsume("shufflevector ") -> {
                val (v1Type, v1) = parseTypedValue(params)
                expect(", ")
                val (_, v2) = parseTypedValue(params)
                expect(", <")
                val mask = mutableListOf<Int>()
                while (!lookingAt(">")) {
                    if (mask.isNotEmpty()) expect(", ")
                    mask.add(parseInt())
                }
                expect(">")
                Instruction.ShuffleVector(InstructionRef(destName, v1Type), v1, v2, mask)
            }
            tryConsume("splat ") -> {
                val (_, scalar) = parseTypedValue(params)
                expect(" to ")
                val vectorType = parseType()
                Instruction.Splat(InstructionRef(destName, vectorType), scalar, vectorType as Type.Vector)
            }
            tryConsume("vector.reduce.") -> {
                val opName = parseIdent()
                val op = VectorReduceOp.entries.first { it.name.lowercase() == opName }
                expect(" ")
                val (_, vector) = parseTypedValue(params)
                val elemType = (vector.type as Type.Vector).element
                Instruction.VectorReduce(InstructionRef(destName, elemType), op, vector)
            }

            // Aggregate
            tryConsume("extractvalue ") -> {
                val (aggType, aggregate) = parseTypedValue(params)
                expect(", ")
                val indices = mutableListOf<Int>()
                do {
                    indices.add(parseInt())
                } while (tryConsume(", "))
                Instruction.ExtractValue(InstructionRef(destName, Type.I32), aggregate, indices)
            }
            tryConsume("insertvalue ") -> {
                val (aggType, aggregate) = parseTypedValue(params)
                expect(", ")
                val (_, element) = parseTypedValue(params)
                expect(", ")
                val indices = mutableListOf<Int>()
                do {
                    indices.add(parseInt())
                } while (tryConsume(", "))
                Instruction.InsertValue(InstructionRef(destName, aggType), aggregate, element, indices)
            }

            // High-level: Objects
            tryConsume("new ") -> {
                val className = parseIdent()
                val typeArgs = mutableListOf<Type>()
                if (tryConsume("<")) {
                    do {
                        typeArgs.add(parseType())
                    } while (tryConsume(", "))
                    expect(">")
                }
                Instruction.NewObject(InstructionRef(destName, Type.ClassRef(className)), className, typeArgs)
            }
            tryConsume("newarray ") -> {
                val elemType = parseType()
                expect(", ")
                val (_, size) = parseTypedValue(params)
                Instruction.NewArray(InstructionRef(destName, Type.Array(elemType, 0L)), elemType, size)
            }
            tryConsume("newmultiarray ") -> {
                val elemType = parseType()
                expect(", [")
                val dims = mutableListOf<Value>()
                while (!lookingAt("]")) {
                    if (dims.isNotEmpty()) expect(", ")
                    val (_, d) = parseTypedValue(params)
                    dims.add(d)
                }
                expect("]")
                Instruction.NewMultiArray(InstructionRef(destName, Type.Array(elemType, 0L)), elemType, dims)
            }

            // High-level: Fields
            tryConsume("getfield ") -> {
                val (className, fieldName) = parseDotted()
                expect(": ")
                val fieldType = parseType()
                expect(", ")
                val obj = parseValue(Type.ClassRef(className), params)
                Instruction.GetField(InstructionRef(destName, fieldType), obj, className, fieldName, fieldType)
            }
            tryConsume("getstatic ") -> {
                val (className, fieldName) = parseDotted()
                expect(": ")
                val fieldType = parseType()
                Instruction.GetStatic(InstructionRef(destName, fieldType), className, fieldName, fieldType)
            }

            // High-level: Dispatch
            tryConsume("virtualcall ") -> parseHighLevelCall(destName, params, "virtualcall")
            tryConsume("interfacecall ") -> parseHighLevelCall(destName, params, "interfacecall")
            tryConsume("specialcall ") -> parseHighLevelCall(destName, params, "specialcall")
            tryConsume("staticcall ") -> parseStaticCall(destName, params)
            tryConsume("dynamiccall ") -> parseDynamicCall(destName, params)

            // High-level: Type ops
            tryConsume("instanceof ") -> {
                val obj = parseValue(Type.ClassRef(""), params)
                expect(", ")
                val checkType = parseType()
                Instruction.InstanceOf(InstructionRef(destName, Type.I1), obj, checkType)
            }
            tryConsume("checkcast ") -> {
                val obj = parseValue(Type.ClassRef(""), params)
                expect(" to ")
                val castType = parseType()
                Instruction.CheckCast(InstructionRef(destName, castType), obj, castType)
            }
            tryConsume("typeid ") -> {
                val obj = parseValue(Type.ClassRef(""), params)
                Instruction.TypeId(InstructionRef(destName, Type.I32), obj)
            }

            // High-level: Managed arrays
            tryConsume("arrayget ") -> {
                val elemType = parseType()
                expect(" ")
                val array = parseValue(Type.Array(elemType, 0L), params)
                expect(", ")
                val index = parseValue(Type.I32, params)
                Instruction.ArrayGet(InstructionRef(destName, elemType), array, index, elemType)
            }
            tryConsume("arraylength ") -> {
                val array = parseValue(Type.Array(Type.I32, 0), params)
                Instruction.ArrayLength(InstructionRef(destName, Type.I32), array)
            }

            // High-level: Box/unbox
            tryConsume("box ") -> {
                val (_, value) = parseTypedValue(params)
                expect(" to ")
                val boxType = parseType()
                Instruction.Box(InstructionRef(destName, boxType), value, boxType)
            }
            tryConsume("unbox ") -> {
                val obj = parseValue(Type.ClassRef(""), params)
                expect(" to ")
                val unboxType = parseType()
                Instruction.Unbox(InstructionRef(destName, unboxType), obj, unboxType)
            }

            // High-level: Closures
            tryConsume("closure.create ") -> {
                val function = parseValue(Type.Void, params)
                expect(", [")
                val captures = mutableListOf<Value>()
                while (!lookingAt("]")) {
                    if (captures.isNotEmpty()) expect(", ")
                    val (_, v) = parseTypedValue(params)
                    captures.add(v)
                }
                expect("]")
                expect(": ")
                val closureType = parseType()
                Instruction.ClosureCreate(InstructionRef(destName, closureType), function, captures, closureType as Type.Function)
            }
            tryConsume("closure.invoke ") -> {
                val closure = parseValue(Type.Void, params)
                expect("(")
                val args = parseCallArgs(params)
                expect(")")
                expect(": ")
                val retType = parseType()
                val dest = if (retType != Type.Void) InstructionRef(destName, retType) else null
                Instruction.ClosureInvoke(dest, closure, args, retType)
            }

            // High-level: Tagged unions
            tryConsume("construct.variant ") -> {
                val unionType = parseType()
                expect(" ")
                val variantName = parseIdent()
                expect("(")
                val fields = mutableListOf<Value>()
                while (!lookingAt(")")) {
                    if (fields.isNotEmpty()) expect(", ")
                    val (_, v) = parseTypedValue(params)
                    fields.add(v)
                }
                expect(")")
                Instruction.ConstructVariant(InstructionRef(destName, unionType), unionType as Type.TaggedUnion, variantName, fields)
            }
            tryConsume("gettag ") -> {
                val union = parseValue(Type.I32, params)
                Instruction.GetTag(InstructionRef(destName, Type.I32), union)
            }
            tryConsume("getvariantfield ") -> {
                val union = parseValue(Type.I32, params)
                expect(".")
                val variantName = parseIdent()
                expect("[")
                val fieldIndex = parseInt()
                expect("]")
                Instruction.GetVariantField(InstructionRef(destName, Type.I32), union, variantName, fieldIndex)
            }

            // GC
            tryConsume("gc.alloc ") -> {
                val allocType = parseType()
                var size: Value? = null
                if (tryConsume(", ")) {
                    val (_, s) = parseTypedValue(params)
                    size = s
                }
                Instruction.GCAlloc(InstructionRef(destName, Type.OpaquePointer), allocType, size)
            }

            // Refcounting
            tryConsume("ref.count ") -> {
                val obj = parseValue(Type.ClassRef(""), params)
                Instruction.RefCount(InstructionRef(destName, Type.I32), obj)
            }

            // Coroutines
            tryConsume("coro.begin ") -> {
                val id = parseValue(Type.Token, params)
                expect(", ")
                val mem = parseValue(Type.OpaquePointer, params)
                Instruction.CoroBegin(InstructionRef(destName, Type.OpaquePointer), id, mem)
            }
            tryConsume("coro.suspend") -> {
                var save: Value? = null
                var isFinal = false
                if (tryConsume(" ") && !lookingAt("final")) {
                    save = parseValue(Type.Token, params)
                }
                if (tryConsume("final") || tryConsume(" final")) isFinal = true
                Instruction.CoroSuspend(InstructionRef(destName, Type.I8), save, isFinal)
            }
            tryConsume("coro.size") -> {
                Instruction.CoroSize(InstructionRef(destName, Type.I64))
            }

            // Intrinsic
            tryConsume("intrinsic @") -> {
                val intrName = parseIdent()
                expect("(")
                val args = parseCallArgs(params)
                expect(")")
                expect(": ")
                val retType = parseType()
                val dest = if (retType != Type.Void) InstructionRef(destName, retType) else null
                Instruction.Intrinsic(dest, intrName, args, retType)
            }

            // Inline assembly
            tryConsume("asm") -> {
                val sideEffects = tryConsume(" sideeffect")
                expect(" ")
                val assembly = parseString()
                expect(", ")
                val constraints = parseString()
                expect("(")
                val args = parseCallArgs(params)
                expect(")")
                val dest = InstructionRef(destName, Type.I32)
                Instruction.InlineAsm(dest, assembly, constraints, sideEffects = sideEffects, args = args)
            }

            // Hints
            tryConsume("expect ") -> {
                val (type, value) = parseTypedValue(params)
                expect(", ")
                val expected = parseConstant(type)
                Instruction.Expect(InstructionRef(destName, type), value, expected)
            }

            else -> error("Unknown instruction at pos $pos: ${input.substring(pos, (pos + 40).coerceAtMost(length))}")
        }
    }

    private fun parseInstructionNoDest(params: List<Parameter>): Instruction? {
        return when {
            tryConsume("ret void") -> Instruction.Ret(null)
            tryConsume("ret ") -> {
                val (_, value) = parseTypedValue(params)
                Instruction.Ret(value)
            }
            tryConsume("br label %") -> Instruction.Br(parseIdent())
            tryConsume("br ") -> {
                parseType() // i1
                expect(" ")
                val cond = parseValue(Type.I1, params)
                expect(", label %")
                val trueTarget = parseIdent()
                expect(", label %")
                val falseTarget = parseIdent()
                Instruction.CondBr(cond, trueTarget, falseTarget)
            }
            tryConsume("switch ") -> {
                val (type, value) = parseTypedValue(params)
                expect(", label %")
                val defaultTarget = parseIdent()
                expect(" [")
                val cases = mutableListOf<Pair<Constant, String>>()
                while (!lookingAt("]")) {
                    val (_, c) = parseTypedConstant()
                    expect(" -> label %")
                    val target = parseIdent()
                    cases.add(c to target)
                    if (!lookingAt("]")) tryConsume(", ")
                }
                expect("]")
                Instruction.Switch(value, defaultTarget, cases)
            }
            tryConsume("indirectbr ptr ") -> {
                val address = parseValue(Type.OpaquePointer, params)
                expect(", [")
                val targets = mutableListOf<String>()
                while (!lookingAt("]")) {
                    if (targets.isNotEmpty()) expect(", ")
                    expect("label %")
                    targets.add(parseIdent())
                }
                expect("]")
                Instruction.IndirectBr(address, targets)
            }
            tryConsume("unreachable") -> Instruction.Unreachable()
            tryConsume("trap") -> Instruction.Trap()
            tryConsume("debugtrap") -> Instruction.DebugTrap()

            tryConsume("store ") -> {
                val volatile = tryConsume("volatile ")
                val (_, value) = parseTypedValue(params)
                expect(", ptr ")
                val ptr = parseValue(Type.OpaquePointer, params)
                var align: Int? = null
                var ordering: AtomicOrdering? = null
                if (tryConsume(", align ")) align = parseInt()
                if (tryConsume(" ")) ordering = tryParseAtomicOrdering()
                Instruction.Store(value, ptr, align, volatile, ordering)
            }
            tryConsume("fence") -> {
                var syncScope: String? = null
                if (tryConsume(" syncscope(")) {
                    syncScope = parseString()
                    expect(")")
                }
                expect(" ")
                val ordering = parseEnumValue<AtomicOrdering>()
                Instruction.Fence(ordering, syncScope)
            }
            tryConsume("memcpy ptr ") -> {
                val dst = parseValue(Type.OpaquePointer, params)
                expect(", ptr ")
                val src = parseValue(Type.OpaquePointer, params)
                expect(", ")
                val (_, len) = parseTypedValue(params)
                val volatile = tryConsume(" volatile")
                Instruction.MemCpy(dst, src, len, volatile)
            }
            tryConsume("memset ptr ") -> {
                val dst = parseValue(Type.OpaquePointer, params)
                expect(", ")
                val (_, value) = parseTypedValue(params)
                expect(", ")
                val (_, len) = parseTypedValue(params)
                val volatile = tryConsume(" volatile")
                Instruction.MemSet(dst, value, len, volatile)
            }
            tryConsume("memmove ptr ") -> {
                val dst = parseValue(Type.OpaquePointer, params)
                expect(", ptr ")
                val src = parseValue(Type.OpaquePointer, params)
                expect(", ")
                val (_, len) = parseTypedValue(params)
                val volatile = tryConsume(" volatile")
                Instruction.MemMove(dst, src, len, volatile)
            }
            tryConsume("prefetch ptr ") -> {
                val address = parseValue(Type.OpaquePointer, params)
                expect(", ")
                val rw = parseInt()
                expect(", ")
                val locality = parseInt()
                expect(", ")
                val cacheType = parseInt()
                Instruction.Prefetch(address, rw, locality, cacheType)
            }
            tryConsume("stackrestore ") -> {
                val ptr = parseValue(Type.OpaquePointer, params)
                Instruction.StackRestore(ptr)
            }
            tryConsume("lifetime.start ptr ") -> {
                val ptr = parseValue(Type.OpaquePointer, params)
                expect(", ")
                val size = parseLong()
                Instruction.LifetimeStart(ptr, size)
            }
            tryConsume("lifetime.end ptr ") -> {
                val ptr = parseValue(Type.OpaquePointer, params)
                expect(", ")
                val size = parseLong()
                Instruction.LifetimeEnd(ptr, size)
            }

            // Varargs
            tryConsume("va_start ") -> Instruction.VAStart(parseValue(Type.OpaquePointer, params))
            tryConsume("va_end ") -> Instruction.VAEnd(parseValue(Type.OpaquePointer, params))
            tryConsume("va_copy ") -> {
                val dst = parseValue(Type.OpaquePointer, params)
                expect(", ")
                val src = parseValue(Type.OpaquePointer, params)
                Instruction.VACopy(dst, src)
            }

            // Exception handling
            tryConsume("resume ") -> {
                val (_, value) = parseTypedValue(params)
                Instruction.Resume(value)
            }
            tryConsume("catchret from ") -> {
                val catchPad = parseValue(Type.Token, params)
                expect(" to label %")
                val dest = parseIdent()
                Instruction.CatchRet(catchPad, dest)
            }
            tryConsume("cleanupret from ") -> {
                val cleanupPad = parseValue(Type.Token, params)
                val unwindDest = if (tryConsume(" unwind label %")) parseIdent()
                else { tryConsume(" unwind to caller"); null }
                Instruction.CleanupRet(cleanupPad, unwindDest)
            }

            // Calls without dest
            tryConsume("tail call ") || tryConsume("musttail call ") || tryConsume("notail call ") || tryConsume("call ") -> {
                val tailStr = input.substring(input.lastIndexOf('\n', pos - 1).coerceAtLeast(0), pos)
                val tailKind = when {
                    "musttail" in tailStr -> TailCallKind.MUSTTAIL
                    "notail" in tailStr -> TailCallKind.NOTAIL
                    "tail call" in tailStr && "musttail" !in tailStr -> TailCallKind.TAIL
                    else -> TailCallKind.NONE
                }
                val cc = tryParseCallingConv() ?: CallingConvention.C
                if (cc != CallingConvention.C) skipWhitespace()
                val retType = parseType()
                expect(" ")
                val function = parseValue(Type.Function(emptyList(), retType), params)
                expect("(")
                val args = parseCallArgs(params)
                expect(")")
                Instruction.Call(null, function, args, retType, cc, tailKind)
            }
            tryConsume("invoke ") -> {
                val retType = parseType()
                expect(" ")
                val function = parseValue(Type.Function(emptyList(), retType), params)
                expect("(")
                val args = parseCallArgs(params)
                expect(")")
                expect(" to label %")
                val normalDest = parseIdent()
                expect(" unwind label %")
                val unwindDest = parseIdent()
                Instruction.Invoke(null, function, args, retType, normalDest, unwindDest)
            }

            // High-level: Fields (no dest)
            tryConsume("putfield ") -> {
                val (className, fieldName) = parseDotted()
                expect(": ")
                val fieldType = parseType()
                expect(", ")
                val obj = parseValue(Type.ClassRef(className), params)
                expect(", ")
                val value = parseValue(fieldType, params)
                Instruction.PutField(obj, className, fieldName, fieldType, value)
            }
            tryConsume("putstatic ") -> {
                val (className, fieldName) = parseDotted()
                expect(": ")
                val fieldType = parseType()
                expect(", ")
                val value = parseValue(fieldType, params)
                Instruction.PutStatic(className, fieldName, fieldType, value)
            }

            // High-level: Dispatch (no dest)
            tryConsume("virtualcall ") -> parseHighLevelCall(null, params, "virtualcall")
            tryConsume("interfacecall ") -> parseHighLevelCall(null, params, "interfacecall")
            tryConsume("specialcall ") -> parseHighLevelCall(null, params, "specialcall")
            tryConsume("staticcall ") -> parseStaticCall(null, params)
            tryConsume("dynamiccall ") -> parseDynamicCall(null, params)
            tryConsume("constructorcall ") -> {
                val className = parseIdent()
                expect("::init(")
                val obj = parseValue(Type.ClassRef(className), params)
                val args = mutableListOf<Value>()
                while (tryConsume(", ")) {
                    val (_, v) = parseTypedValue(params)
                    args.add(v)
                }
                expect(")")
                val ctorType = Type.Function(args.map { it.type }, Type.Void)
                Instruction.ConstructorCall(obj, className, ctorType, args)
            }

            // High-level: Managed arrays (no dest)
            tryConsume("arrayset ") -> {
                val elemType = parseType()
                expect(" ")
                val array = parseValue(Type.Array(elemType, 0L), params)
                expect(", ")
                val index = parseValue(Type.I32, params)
                expect(", ")
                val value = parseValue(elemType, params)
                Instruction.ArraySet(array, index, value, elemType)
            }

            // High-level: Monitors
            tryConsume("monitorenter ") -> Instruction.MonitorEnter(parseValue(Type.ClassRef(""), params))
            tryConsume("monitorexit ") -> Instruction.MonitorExit(parseValue(Type.ClassRef(""), params))

            // High-level: Exceptions
            tryConsume("throw ") -> Instruction.Throw(parseValue(Type.ClassRef(""), params))
            tryConsume("trycatch %") -> {
                val tryBlock = parseIdent()
                expect(" [")
                val catches = mutableListOf<CatchHandler>()
                while (!lookingAt("]")) {
                    if (catches.isNotEmpty()) expect(", ")
                    expect("catch ")
                    val exType = parseType()
                    expect(" -> %")
                    val handler = parseIdent()
                    catches.add(CatchHandler(exType, handler))
                }
                expect("]")
                val finallyBlock = if (tryConsume(" finally %")) parseIdent() else null
                Instruction.TryCatchRegion(tryBlock, catches, finallyBlock)
            }

            // High-level: Tagged unions
            tryConsume("tagswitch ") -> {
                val union = parseValue(Type.I32, params)
                expect(" [")
                val cases = mutableListOf<Pair<String, String>>()
                var defaultTarget: String? = null
                while (!lookingAt("]")) {
                    if (cases.isNotEmpty() || defaultTarget != null) expect(", ")
                    if (tryConsume("default -> %")) {
                        defaultTarget = parseIdent()
                    } else {
                        val variant = parseIdent()
                        expect(" -> %")
                        val target = parseIdent()
                        cases.add(variant to target)
                    }
                }
                expect("]")
                Instruction.TagSwitch(union, cases, defaultTarget)
            }

            // GC
            tryConsume("gc.safepoint") -> Instruction.GCSafepoint()
            tryConsume("gc.root ") -> {
                val ptr = parseValue(Type.OpaquePointer, params)
                val metadata = if (tryConsume(", ")) parseValue(Type.Metadata, params) else null
                Instruction.GCRoot(ptr, metadata)
            }

            // Refcounting
            tryConsume("ref.retain ") -> Instruction.RefRetain(parseValue(Type.ClassRef(""), params))
            tryConsume("ref.release ") -> Instruction.RefRelease(parseValue(Type.ClassRef(""), params))

            // Coroutines
            tryConsume("coro.end ") -> {
                val handle = parseValue(Type.OpaquePointer, params)
                val unwind = tryConsume(" unwind")
                Instruction.CoroEnd(handle, unwind)
            }
            tryConsume("coro.resume ") -> Instruction.CoroResume(parseValue(Type.OpaquePointer, params))
            tryConsume("coro.destroy ") -> Instruction.CoroDestroy(parseValue(Type.OpaquePointer, params))

            // Debug
            tryConsume("dbg.loc ") -> {
                val line = parseInt()
                expect(":")
                val col = parseInt()
                expect(" scope ")
                val scope = parseString()
                val inlinedAt = if (tryConsume(" inlined_at ")) parseString() else null
                Instruction.DebugLoc(line, col, scope, inlinedAt)
            }
            tryConsume("dbg.value ") -> {
                val variable = parseString()
                expect(" = ")
                val value = parseValue(Type.I32, params)
                val expression = if (tryConsume(" expr ")) parseString() else null
                Instruction.DebugValue(variable, value, expression)
            }
            tryConsume("dbg.declare ") -> {
                val variable = parseString()
                expect(" = ")
                val address = parseValue(Type.OpaquePointer, params)
                val expression = if (tryConsume(" expr ")) parseString() else null
                Instruction.DebugDeclare(variable, address, expression)
            }

            // Hints
            tryConsume("assume ") -> Instruction.Assume(parseValue(Type.I1, params))

            // Closure invoke without dest
            tryConsume("closure.invoke ") -> {
                val closure = parseValue(Type.Void, params)
                expect("(")
                val args = parseCallArgs(params)
                expect(")")
                expect(": ")
                val retType = parseType()
                Instruction.ClosureInvoke(null, closure, args, retType)
            }

            else -> {
                skipToEndOfLine()
                null
            }
        }
    }

    // Helpers for parsing common instruction patterns

    private fun parseBinaryOp(destName: String, make: (InstructionRef, Value, Value, Set<String>) -> Instruction): Instruction {
        val flags = mutableSetOf<String>()
        while (true) {
            if (tryConsume(" nuw")) flags.add("nuw")
            else if (tryConsume(" nsw")) flags.add("nsw")
            else if (tryConsume(" exact")) flags.add("exact")
            else break
        }
        expect(" ")
        val (type, lhs) = parseTypedValue(emptyList())
        expect(", ")
        val rhs = parseValue(type, emptyList())
        return make(InstructionRef(destName, type), lhs, rhs, flags)
    }

    private fun parseBinarySimple(destName: String, make: (InstructionRef, Value, Value) -> Instruction): Instruction {
        val (type, lhs) = parseTypedValue(emptyList())
        expect(", ")
        val rhs = parseValue(type, emptyList())
        return make(InstructionRef(destName, type), lhs, rhs)
    }

    private fun parseUnary(destName: String, make: (InstructionRef, Value) -> Instruction): Instruction {
        val (type, operand) = parseTypedValue(emptyList())
        return make(InstructionRef(destName, type), operand)
    }

    private fun parseFloatBinaryOp(destName: String, params: List<Parameter>, make: (InstructionRef, Value, Value, FastMathFlags) -> Instruction): Instruction {
        val fm = parseFastMathFlags()
        expect(" ")
        val (type, lhs) = parseTypedValue(params)
        expect(", ")
        val rhs = parseValue(type, params)
        return make(InstructionRef(destName, type), lhs, rhs, fm)
    }

    private fun parseConversion(destName: String, params: List<Parameter>, make: (InstructionRef, Value, Type) -> Instruction): Instruction {
        val (_, value) = parseTypedValue(params)
        expect(" to ")
        val toType = parseType()
        return make(InstructionRef(destName, toType), value, toType)
    }

    private fun parseCallArgs(params: List<Parameter>): List<Value> {
        val args = mutableListOf<Value>()
        while (!lookingAt(")")) {
            if (args.isNotEmpty()) expect(", ")
            val (_, v) = parseTypedValue(params)
            args.add(v)
        }
        return args
    }

    private fun parseDotted(): Pair<String, String> {
        val first = parseIdent()
        expect(".")
        val second = parseIdent()
        return first to second
    }

    private fun parseHighLevelCall(destName: String?, params: List<Parameter>, kind: String): Instruction {
        val obj = parseValue(Type.ClassRef(""), params)
        expect(".")
        val className = parseIdent()
        expect("::")
        val methodName = parseIdent()
        expect("(")
        val args = parseCallArgs(params)
        expect(")")
        expect(": ")
        val retType = parseType()
        val methodType = Type.Function(args.map { it.type }, retType)
        val dest = if (retType != Type.Void && destName != null) InstructionRef(destName, retType) else null
        return when (kind) {
            "virtualcall" -> Instruction.VirtualCall(dest, obj, className, methodName, methodType, args)
            "interfacecall" -> Instruction.InterfaceCall(dest, obj, className, methodName, methodType, args)
            "specialcall" -> Instruction.SpecialCall(dest, obj, className, methodName, methodType, args)
            else -> error("Unknown call kind: $kind")
        }
    }

    private fun parseStaticCall(destName: String?, params: List<Parameter>): Instruction {
        val className = parseIdent()
        expect("::")
        val methodName = parseIdent()
        expect("(")
        val args = parseCallArgs(params)
        expect(")")
        expect(": ")
        val retType = parseType()
        val methodType = Type.Function(args.map { it.type }, retType)
        val dest = if (retType != Type.Void && destName != null) InstructionRef(destName, retType) else null
        return Instruction.StaticCall(dest, className, methodName, methodType, args)
    }

    private fun parseDynamicCall(destName: String?, params: List<Parameter>): Instruction {
        val name = parseIdent()
        expect("(")
        val args = parseCallArgs(params)
        expect(")")
        expect(": ")
        val retType = parseType()
        val methodType = Type.Function(args.map { it.type }, retType)
        val bootstrap = BootstrapMethod("", name, methodType)
        val dest = if (retType != Type.Void && destName != null) InstructionRef(destName, retType) else null
        return Instruction.DynamicCall(dest, bootstrap, name, methodType, args)
    }

    // Type parsing

    private fun parseType(): Type {
        return when {
            tryConsume("void") -> Type.Void
            tryConsume("label") -> Type.Label
            tryConsume("metadata") -> Type.Metadata
            tryConsume("token") -> Type.Token
            tryConsume("ptr addrspace(") -> {
                val addrSpace = parseInt()
                expect(")")
                Type.Pointer(Type.I8, addrSpace)
            }
            tryConsume("ptr") -> Type.OpaquePointer
            tryConsume("i1") && !isDigit() -> Type.I1
            tryConsume("i8") && !isDigit() -> Type.I8
            tryConsume("i16") && !isDigit() -> Type.I16
            tryConsume("i32") && !isDigit() -> Type.I32
            tryConsume("i64") && !isDigit() -> Type.I64
            tryConsume("i128") && !isDigit() -> Type.I128
            lookingAt("i") && pos + 1 < length && input[pos + 1].isDigit() -> {
                advance(1)
                val bits = parseInt()
                Type.IntN(bits)
            }
            tryConsume("f16") && !isDigit() -> Type.F16
            tryConsume("bf16") -> Type.BF16
            tryConsume("f32") && !isDigit() -> Type.F32
            tryConsume("f64") && !isDigit() -> Type.F64
            tryConsume("f80") -> Type.F80
            tryConsume("f128") -> Type.F128
            tryConsume("ref<") -> {
                val referent = parseType()
                expect(">")
                val nullable = tryConsume("?")
                Type.Reference(referent, nullable)
            }
            tryConsume("weakref<") -> {
                val referent = parseType()
                expect(">")
                Type.WeakReference(referent)
            }
            tryConsume("[") -> {
                val size = parseLong()
                expect(" x ")
                val element = parseType()
                expect("]")
                Type.Array(element, size)
            }
            tryConsume("<vscale x ") -> {
                val lanes = parseInt()
                expect(" x ")
                val element = parseType()
                expect(">")
                Type.Vector(element, lanes, scalable = true)
            }
            tryConsume("<") -> {
                if (lookingAt("{")) {
                    // packed struct: <{ ... }>
                    expect("{ ")
                    val fields = mutableListOf<Type>()
                    while (!lookingAt("}")) {
                        if (fields.isNotEmpty()) expect(", ")
                        fields.add(parseType())
                    }
                    expect("}>")
                    Type.Struct(null, fields, packed = true)
                } else {
                    val lanes = parseInt()
                    expect(" x ")
                    val element = parseType()
                    expect(">")
                    Type.Vector(element, lanes)
                }
            }
            tryConsume("{ ") -> {
                val fields = mutableListOf<Type>()
                while (!lookingAt("}")) {
                    if (fields.isNotEmpty()) expect(", ")
                    fields.add(parseType())
                }
                expect("}")
                Type.Struct(null, fields)
            }
            tryConsume("union { ") -> {
                val variants = mutableListOf<Type>()
                while (!lookingAt("}")) {
                    if (variants.isNotEmpty()) expect(" | ")
                    variants.add(parseType())
                }
                expect("}")
                Type.Union(null, variants)
            }
            tryConsume("class @") -> {
                val name = parseIdent()
                Type.ClassRef(name)
            }
            tryConsume("interface @") -> {
                val name = parseIdent()
                Type.InterfaceRef(name)
            }
            tryConsume("platform(") -> {
                val name = parseIdent()
                expect(")")
                Type.PlatformType(name)
            }
            tryConsume("!") -> {
                val name = parseIdent()
                Type.TypeParam(name, 0)
            }
            tryConsume("%") -> {
                val name = parseIdent()
                // Could be named struct or tagged union — parse as opaque struct ref
                val base = Type.Struct(name = name, fields = emptyList())
                if (tryConsume("<")) {
                    val typeArgs = mutableListOf<Type>()
                    do {
                        typeArgs.add(parseType())
                    } while (tryConsume(", "))
                    expect(">")
                    Type.Parameterized(base, typeArgs)
                } else {
                    base
                }
            }
            else -> {
                // Try parsing a function type: retType (params)
                // This is tricky — punt for now and return void
                error("Cannot parse type at pos $pos: ${input.substring(pos, (pos + 30).coerceAtMost(length))}")
            }
        }.let { baseType ->
            // Check for trailing ? (nullable)
            if (baseType !is Type.Reference && tryConsume("?")) Type.Nullable(baseType)
            else baseType
        }
    }

    // Value parsing

    private fun parseTypedValue(params: List<Parameter>): Pair<Type, Value> {
        val type = parseType()
        expect(" ")
        val value = parseValue(type, params)
        return type to value
    }

    private fun parseTypedConstant(): Pair<Type, Constant> {
        val type = parseType()
        expect(" ")
        val c = parseConstant(type)
        return type to c
    }

    private fun parseValue(type: Type, params: List<Parameter>): Value {
        return when {
            lookingAt("%") -> {
                advance(1)
                val name = parseIdent()
                val param = params.find { it.name == name }
                if (param != null) param
                else InstructionRef("%$name", type)
            }
            lookingAt("@") -> {
                advance(1)
                val name = parseIdent()
                val funcType = type as? Type.Function ?: Type.Function(emptyList(), type)
                FunctionRef(name, funcType)
            }
            else -> parseConstant(type)
        }
    }

    private fun parseConstant(type: Type): Constant {
        return when {
            tryConsume("null") -> if (type is Type.Reference || type is Type.ClassRef || type is Type.InterfaceRef) Constant.NullRef else Constant.NullPtr
            tryConsume("undef") -> Constant.Undef(type)
            tryConsume("poison") -> Constant.Poison(type)
            tryConsume("zeroinitializer") -> Constant.ZeroInitializer(type)
            tryConsume("c\"") -> {
                pos -= 1 // back up to include the quote
                val s = parseString()
                Constant.StringConst(s)
            }
            tryConsume("getelementptr") -> {
                val inBounds = tryConsume(" inbounds")
                expect(" (")
                val gepType = parseType()
                expect(", ")
                val base = parseConstant(Type.OpaquePointer)
                val indices = mutableListOf<Constant>()
                while (tryConsume(", ")) {
                    indices.add(parseConstant(Type.I32))
                }
                expect(")")
                Constant.GetElementPtr(type, base, indices, inBounds)
            }
            tryConsume("bitcast (") -> {
                val inner = parseConstant(type)
                expect(" to ")
                val toType = parseType()
                expect(")")
                Constant.BitCast(toType, inner)
            }
            tryConsume("inttoptr (") -> {
                val inner = parseConstant(Type.I64)
                expect(" to ")
                val toType = parseType()
                expect(")")
                Constant.IntToPtr(toType, inner)
            }
            tryConsume("ptrtoint (") -> {
                val inner = parseConstant(Type.OpaquePointer)
                expect(" to ")
                val toType = parseType()
                expect(")")
                Constant.PtrToInt(toType, inner)
            }
            lookingAt("[") -> {
                advance(1)
                val elements = mutableListOf<Constant>()
                while (!lookingAt("]")) {
                    if (elements.isNotEmpty()) expect(", ")
                    val (_, elem) = parseTypedConstant()
                    elements.add(elem)
                }
                expect("]")
                Constant.ArrayConst(type, elements)
            }
            lookingAt("<") -> {
                advance(1)
                val elements = mutableListOf<Constant>()
                while (!lookingAt(">")) {
                    if (elements.isNotEmpty()) expect(", ")
                    val (_, elem) = parseTypedConstant()
                    elements.add(elem)
                }
                expect(">")
                Constant.VectorConst(type, elements)
            }
            lookingAt("{ ") -> {
                advance(2)
                val fields = mutableListOf<Constant>()
                while (!lookingAt("}")) {
                    if (fields.isNotEmpty()) expect(", ")
                    val (_, f) = parseTypedConstant()
                    fields.add(f)
                }
                expect("}")
                Constant.StructConst(type, fields)
            }
            lookingAt("true") -> {
                advance(4)
                Constant.I1(true)
            }
            lookingAt("false") -> {
                advance(5)
                Constant.I1(false)
            }
            else -> {
                val neg = tryConsume("-")
                if (pos < length && (input[pos].isDigit() || input[pos] == '.')) {
                    val sb = StringBuilder()
                    if (neg) sb.append('-')
                    while (pos < length) {
                        val c = input[pos]
                        if (c.isDigit() || c == '.' || c == 'E' || c == 'e' || c == '+' || (c == '-' && pos > 0 && (input[pos-1] == 'e' || input[pos-1] == 'E'))) {
                            sb.append(c)
                            pos++
                        } else break
                    }
                    val numStr = sb.toString()
                    val isFloat = type is Type.F16 || type is Type.BF16 || type is Type.F32 || type is Type.F64 || type is Type.F80 || type is Type.F128 || '.' in numStr || 'e' in numStr || 'E' in numStr
                    if (isFloat) {
                        val d = numStr.toDouble()
                        when (type) {
                            is Type.F16 -> Constant.F16(d.toFloat())
                            is Type.BF16 -> Constant.BF16(d.toFloat())
                            is Type.F32 -> Constant.F32(d.toFloat())
                            is Type.F80 -> Constant.F80(d)
                            is Type.F128 -> Constant.F128(d)
                            else -> Constant.F64(d)
                        }
                    } else {
                        val v = numStr.toLong()
                        when (type) {
                            is Type.I1 -> Constant.I1(v != 0L)
                            is Type.I8 -> Constant.I8(v.toByte())
                            is Type.I16 -> Constant.I16(v.toShort())
                            is Type.I64 -> Constant.I64(v)
                            is Type.I128 -> Constant.I128(v)
                            is Type.IntN -> Constant.IntN(v, type.bits)
                            else -> Constant.I32(v.toInt())
                        }
                    }
                } else {
                    error("Cannot parse constant at pos $pos: ${input.substring(pos, (pos + 20).coerceAtMost(length))}")
                }
            }
        }
    }

    // Metadata

    private fun parseMetadataValue(): MetadataValue {
        expect("!")
        return parseMetadataInner()
    }

    private fun parseMetadataInner(): MetadataValue {
        return when {
            lookingAt("\"") -> MetadataValue.StringMD(parseString())
            lookingAt("{") -> {
                advance(1)
                val values = mutableListOf<MetadataValue>()
                while (!lookingAt("}")) {
                    if (values.isNotEmpty()) expect(", ")
                    values.add(parseMetadataValue())
                }
                expect("}")
                MetadataValue.NodeMD(values)
            }
            lookingAt("!") -> {
                advance(1)
                MetadataValue.RefMD(parseIdent())
            }
            else -> {
                val num = parseLong()
                MetadataValue.IntMD(num)
            }
        }
    }

    // Fast math flags

    private fun parseFastMathFlags(): FastMathFlags {
        var noNaNs = false; var noInfs = false; var noSignedZeros = false
        var allowReciprocal = false; var allowContract = false; var approxFunc = false; var reassoc = false
        while (true) {
            when {
                tryConsume(" nnan") -> noNaNs = true
                tryConsume(" ninf") -> noInfs = true
                tryConsume(" nsz") -> noSignedZeros = true
                tryConsume(" arcp") -> allowReciprocal = true
                tryConsume(" contract") -> allowContract = true
                tryConsume(" afn") -> approxFunc = true
                tryConsume(" reassoc") -> reassoc = true
                else -> break
            }
        }
        return if (!noNaNs && !noInfs && !noSignedZeros && !allowReciprocal && !allowContract && !approxFunc && !reassoc)
            FastMathFlags.NONE
        else FastMathFlags(noNaNs, noInfs, noSignedZeros, allowReciprocal, allowContract, approxFunc, reassoc)
    }

    // Linkage, visibility, calling convention parsers

    private fun tryParseLinkage(): Linkage? {
        for (l in Linkage.entries) {
            if (l == Linkage.EXTERNAL) continue
            if (tryConsume("${l.name.lowercase()} ")) return l
        }
        return null
    }

    private fun tryParseVisibility(): Visibility? {
        for (v in Visibility.entries) {
            if (v == Visibility.DEFAULT) continue
            if (tryConsume("${v.name.lowercase()} ")) return v
        }
        return null
    }

    private fun tryParseCallingConv(): CallingConvention? {
        for (cc in CallingConvention.entries) {
            if (cc == CallingConvention.C) continue
            if (tryConsume("${cc.name.lowercase()} ")) return cc
        }
        return null
    }

    private fun tryParseAtomicOrdering(): AtomicOrdering? {
        for (o in AtomicOrdering.entries) {
            if (tryConsume(o.name.lowercase())) return o
        }
        return null
    }

    private inline fun <reified E : Enum<E>> parseEnumValue(): E {
        val entries = enumValues<E>()
        // Try longest match first to avoid prefix conflicts
        for (e in entries.sortedByDescending { it.name.length }) {
            if (tryConsume(e.name.lowercase())) return e
        }
        error("Expected one of ${entries.map { it.name.lowercase() }} at pos $pos: ${input.substring(pos, (pos + 20).coerceAtMost(length))}")
    }

    // Low-level lexer helpers

    private fun lookingAt(s: String): Boolean {
        if (pos + s.length > length) return false
        return input.regionMatches(pos, s, 0, s.length)
    }

    @Suppress("SameParameterValue")
    private fun lookingAt(c: Char): Boolean = pos < length && input[pos] == c

    private fun advance(n: Int) { pos += n }

    private fun tryConsume(s: String): Boolean {
        if (lookingAt(s)) { pos += s.length; return true }
        return false
    }

    private fun expect(s: String) {
        if (!tryConsume(s)) {
            val context = input.substring(pos, (pos + 40).coerceAtMost(length))
            error("Expected '$s' at pos $pos, found: $context")
        }
    }

    private fun peek(): Char = if (pos < length) input[pos] else '\u0000'

    private fun isDigit(): Boolean = pos < length && input[pos].isDigit()

    private fun isIdentStart(c: Char) = c.isLetterOrDigit() || c == '_' || c == '.' || c == '<' || c == '-'
    private fun isIdentChar(c: Char) = c.isLetterOrDigit() || c == '_' || c == '.' || c == '<' || c == '>' || c == '-'

    private fun parseIdent(): String {
        val start = pos
        while (pos < length && isIdentChar(input[pos])) pos++
        check(pos > start) { "Expected identifier at pos $start: ${input.substring(start, (start + 20).coerceAtMost(length))}" }
        return input.substring(start, pos)
    }

    private fun parseString(): String {
        expect("\"")
        val sb = StringBuilder()
        while (pos < length && input[pos] != '"') {
            if (input[pos] == '\\' && pos + 1 < length) {
                pos++
                when (input[pos]) {
                    '\\' -> sb.append('\\')
                    '"' -> sb.append('"')
                    'n' -> sb.append('\n')
                    't' -> sb.append('\t')
                    else -> { sb.append('\\'); sb.append(input[pos]) }
                }
            } else {
                sb.append(input[pos])
            }
            pos++
        }
        expect("\"")
        return sb.toString()
    }

    private fun parseInt(): Int {
        val neg = tryConsume("-")
        val start = pos
        while (pos < length && input[pos].isDigit()) pos++
        check(pos > start) { "Expected integer at pos $start" }
        val n = input.substring(start, pos).toInt()
        return if (neg) -n else n
    }

    private fun parseLong(): Long {
        val neg = tryConsume("-")
        val start = pos
        while (pos < length && input[pos].isDigit()) pos++
        check(pos > start) { "Expected integer at pos $start" }
        val n = input.substring(start, pos).toLong()
        return if (neg) -n else n
    }

    private fun skipWhitespace() {
        while (pos < length && input[pos].isWhitespace()) pos++
    }

    private fun skipSpaces() {
        while (pos < length && input[pos] == ' ') pos++
    }

    private fun skipToEndOfLine() {
        while (pos < length && input[pos] != '\n') pos++
    }

    private fun lookingAtGlobal(): Boolean {
        val saved = pos
        try {
            advance(1) // skip @
            parseIdent()
            skipSpaces()
            return lookingAt("=")
        } catch (_: Exception) {
            return false
        } finally {
            pos = saved
        }
    }

    private fun lookingAtClassKeyword(): Boolean {
        val saved = pos
        try {
            // Might start with visibility: private/protected/internal/package_private
            tryConsume("private ") || tryConsume("protected ") || tryConsume("internal ") || tryConsume("package_private ")
            tryConsume("abstract ")
            tryConsume("final ")
            return lookingAt("class ")
        } finally {
            pos = saved
        }
    }
}
