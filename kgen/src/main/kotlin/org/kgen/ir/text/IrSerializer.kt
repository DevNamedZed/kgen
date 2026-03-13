package org.kgen.ir.text

import org.kgen.ir.*
import org.kgen.ir.instructions.*
import org.kgen.ir.types.*
import java.io.*

class IrSerializer {

    fun serialize(module: Module, output: OutputStream) {
        DataOutputStream(BufferedOutputStream(output)).use { out ->
            out.writeInt(MAGIC)
            out.writeInt(VERSION)
            writeModule(out, module)
        }
    }

    fun serialize(module: Module): ByteArray {
        val baos = ByteArrayOutputStream()
        serialize(module, baos)
        return baos.toByteArray()
    }

    fun deserialize(input: InputStream): Module {
        DataInputStream(BufferedInputStream(input)).use { inp ->
            val magic = inp.readInt()
            if (magic != MAGIC) throw IllegalArgumentException("Invalid kgen IR magic: 0x${magic.toString(16)}")
            val version = inp.readInt()
            if (version != VERSION) throw IllegalArgumentException("Unsupported kgen IR version: $version (expected $VERSION)")
            return readModule(inp)
        }
    }

    fun deserialize(bytes: ByteArray): Module = deserialize(ByteArrayInputStream(bytes))

    private fun writeModule(out: DataOutputStream, m: Module) {
        writeString(out, m.name)
        writeNullableString(out, m.targetTriple)
        writeNullableString(out, m.dataLayout)
        writeNullableString(out, m.sourceFile)

        writeList(out, m.targetFeatures.toList()) { writeString(out, it) }
        writeList(out, m.aliases) { writeTypeAlias(out, it) }
        writeList(out, m.structs) { writeStructDef(out, it) }
        writeList(out, m.globals) { writeGlobal(out, it) }
        writeList(out, m.functions) { writeFunction(out, it) }
        writeList(out, m.classes) { writeClassDef(out, it) }
        writeList(out, m.interfaces) { writeInterfaceDef(out, it) }
        writeList(out, m.enums) { writeEnumDef(out, it) }

        // Metadata
        writeList(out, m.metadata.entries.toList()) {
            writeString(out, it.key)
            writeMetadata(out, it.value)
        }

        // Constraints
        if (m.constraints != null) {
            out.writeByte(1)
            writeList(out, m.constraints.toList()) { out.writeByte(it.ordinal) }
        } else {
            out.writeByte(0)
        }

        // Submodules
        writeList(out, m.submodules) { writeSubmodule(out, it) }
    }

    private fun writeSubmodule(out: DataOutputStream, sub: Submodule) {
        writeString(out, sub.name)
        writeList(out, sub.constraints.toList()) { out.writeByte(it.ordinal) }
        writeList(out, sub.functions) { writeString(out, it) }
        writeList(out, sub.globals) { writeString(out, it) }
    }

    private fun readModule(inp: DataInputStream): Module {
        val name = readString(inp)
        val targetTriple = readNullableString(inp)
        val dataLayout = readNullableString(inp)
        val sourceFile = readNullableString(inp)

        val targetFeatures = readList(inp) { readString(inp) }.toSet()
        val aliases = readList(inp) { readTypeAlias(inp) }
        val structs = readList(inp) { readStructDef(inp) }
        val globals = readList(inp) { readGlobal(inp) }
        val functions = readList(inp) { readFunction(inp) }
        val classes = readList(inp) { readClassDef(inp) }
        val interfaces = readList(inp) { readInterfaceDef(inp) }
        val enums = readList(inp) { readEnumDef(inp) }

        val metadata = readList(inp) {
            readString(inp) to readMetadata(inp)
        }.toMap()

        // Constraints
        val hasConstraints = inp.readByte().toInt()
        val constraints = if (hasConstraints == 1) {
            readList(inp) { IrCategory.entries[inp.readByte().toInt()] }.toSet()
        } else null

        // Submodules
        val submodules = readList(inp) { readSubmodule(inp) }

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
            constraints = constraints,
            submodules = submodules,
        )
    }

    private fun readSubmodule(inp: DataInputStream): Submodule {
        val name = readString(inp)
        val constraints = readList(inp) { IrCategory.entries[inp.readByte().toInt()] }.toSet()
        val functions = readList(inp) { readString(inp) }
        val globals = readList(inp) { readString(inp) }
        return Submodule(name, constraints, functions, globals)
    }

    // Type serialization

    private fun writeType(out: DataOutputStream, type: Type) {
        when (type) {
            is Type.I1 -> out.writeByte(T_I1)
            is Type.I8 -> out.writeByte(T_I8)
            is Type.I16 -> out.writeByte(T_I16)
            is Type.I32 -> out.writeByte(T_I32)
            is Type.I64 -> out.writeByte(T_I64)
            is Type.I128 -> out.writeByte(T_I128)
            is Type.IntN -> { out.writeByte(T_INTN); out.writeInt(type.bits) }
            is Type.F16 -> out.writeByte(T_F16)
            is Type.BF16 -> out.writeByte(T_BF16)
            is Type.F32 -> out.writeByte(T_F32)
            is Type.F64 -> out.writeByte(T_F64)
            is Type.F80 -> out.writeByte(T_F80)
            is Type.F128 -> out.writeByte(T_F128)
            is Type.Void -> out.writeByte(T_VOID)
            is Type.Label -> out.writeByte(T_LABEL)
            is Type.Metadata -> out.writeByte(T_METADATA)
            is Type.Token -> out.writeByte(T_TOKEN)
            is Type.Pointer -> { out.writeByte(T_POINTER); writeType(out, type.pointee); out.writeInt(type.addressSpace) }
            is Type.OpaquePointer -> out.writeByte(T_OPAQUE_PTR)
            is Type.Reference -> { out.writeByte(T_REFERENCE); writeType(out, type.referent); out.writeBoolean(type.nullable) }
            is Type.WeakReference -> { out.writeByte(T_WEAK_REF); writeType(out, type.referent) }
            is Type.InteriorRef -> { out.writeByte(T_INTERIOR_REF); writeType(out, type.pointee) }
            is Type.PinnedRef -> { out.writeByte(T_PINNED_REF); writeType(out, type.referent) }
            is Type.Array -> { out.writeByte(T_ARRAY); writeType(out, type.element); out.writeLong(type.size) }
            is Type.Vector -> { out.writeByte(T_VECTOR); writeType(out, type.element); out.writeInt(type.lanes); out.writeBoolean(type.scalable) }
            is Type.Struct -> { out.writeByte(T_STRUCT); writeNullableString(out, type.name); writeList(out, type.fields) { writeType(out, it) }; out.writeBoolean(type.packed) }
            is Type.OpaqueStruct -> { out.writeByte(T_OPAQUE_STRUCT); writeString(out, type.name) }
            is Type.Union -> { out.writeByte(T_UNION); writeNullableString(out, type.name); writeList(out, type.variants) { writeType(out, it) } }
            is Type.TaggedUnion -> { out.writeByte(T_TAGGED_UNION); writeString(out, type.name); writeType(out, type.tagType); writeList(out, type.variants) { writeTaggedVariant(out, it) } }
            is Type.Function -> { out.writeByte(T_FUNCTION); writeList(out, type.params) { writeType(out, it) }; writeType(out, type.ret); out.writeBoolean(type.vararg) }
            is Type.ClassRef -> { out.writeByte(T_CLASS_REF); writeString(out, type.name) }
            is Type.InterfaceRef -> { out.writeByte(T_INTERFACE_REF); writeString(out, type.name) }
            is Type.TypeParam -> { out.writeByte(T_TYPE_PARAM); writeString(out, type.name); out.writeInt(type.index); writeList(out, type.bounds) { writeType(out, it) } }
            is Type.Parameterized -> { out.writeByte(T_PARAMETERIZED); writeType(out, type.base); writeList(out, type.typeArgs) { writeType(out, it) } }
            is Type.Nullable -> { out.writeByte(T_NULLABLE); writeType(out, type.inner) }
            is Type.PlatformType -> { out.writeByte(T_PLATFORM); writeString(out, type.name) }
        }
    }

    private fun readType(inp: DataInputStream): Type = when (val tag = inp.readByte().toInt()) {
        T_I1 -> Type.I1
        T_I8 -> Type.I8
        T_I16 -> Type.I16
        T_I32 -> Type.I32
        T_I64 -> Type.I64
        T_I128 -> Type.I128
        T_INTN -> Type.IntN(inp.readInt())
        T_F16 -> Type.F16
        T_BF16 -> Type.BF16
        T_F32 -> Type.F32
        T_F64 -> Type.F64
        T_F80 -> Type.F80
        T_F128 -> Type.F128
        T_VOID -> Type.Void
        T_LABEL -> Type.Label
        T_METADATA -> Type.Metadata
        T_TOKEN -> Type.Token
        T_POINTER -> Type.Pointer(readType(inp), inp.readInt())
        T_OPAQUE_PTR -> Type.OpaquePointer
        T_REFERENCE -> Type.Reference(readType(inp), inp.readBoolean())
        T_WEAK_REF -> Type.WeakReference(readType(inp))
        T_INTERIOR_REF -> Type.InteriorRef(readType(inp))
        T_PINNED_REF -> Type.PinnedRef(readType(inp))
        T_ARRAY -> Type.Array(readType(inp), inp.readLong())
        T_VECTOR -> Type.Vector(readType(inp), inp.readInt(), inp.readBoolean())
        T_STRUCT -> Type.Struct(readNullableString(inp), readList(inp) { readType(inp) }, inp.readBoolean())
        T_OPAQUE_STRUCT -> Type.OpaqueStruct(readString(inp))
        T_UNION -> Type.Union(readNullableString(inp), readList(inp) { readType(inp) })
        T_TAGGED_UNION -> Type.TaggedUnion(readString(inp), readType(inp), readList(inp) { readTaggedVariant(inp) })
        T_FUNCTION -> { val params = readList(inp) { readType(inp) }; val ret = readType(inp); Type.Function(params, ret, inp.readBoolean()) }
        T_CLASS_REF -> Type.ClassRef(readString(inp))
        T_INTERFACE_REF -> Type.InterfaceRef(readString(inp))
        T_TYPE_PARAM -> Type.TypeParam(readString(inp), inp.readInt(), readList(inp) { readType(inp) })
        T_PARAMETERIZED -> Type.Parameterized(readType(inp), readList(inp) { readType(inp) })
        T_NULLABLE -> Type.Nullable(readType(inp))
        T_PLATFORM -> Type.PlatformType(readString(inp))
        else -> throw IllegalArgumentException("Unknown type tag: $tag")
    }

    // Value serialization

    private fun writeValue(out: DataOutputStream, v: Value) {
        when (v) {
            is Parameter -> { out.writeByte(V_PARAM); writeString(out, v.name); writeType(out, v.type); out.writeInt(v.index) }
            is InstructionRef -> { out.writeByte(V_INST_REF); writeString(out, v.name); writeType(out, v.type) }
            is GlobalRef -> { out.writeByte(V_GLOBAL_REF); writeString(out, v.name); writeType(out, v.type) }
            is FunctionRef -> { out.writeByte(V_FUNC_REF); writeString(out, v.name); writeType(out, v.type) as Unit }
            is BlockRef -> { out.writeByte(V_BLOCK_REF); writeString(out, v.label) }
            is Constant -> { out.writeByte(V_CONSTANT); writeConstant(out, v) }
        }
    }

    private fun readValue(inp: DataInputStream): Value = when (val tag = inp.readByte().toInt()) {
        V_PARAM -> Parameter(readString(inp), readType(inp), inp.readInt())
        V_INST_REF -> InstructionRef(readString(inp), readType(inp))
        V_GLOBAL_REF -> GlobalRef(readString(inp), readType(inp))
        V_FUNC_REF -> FunctionRef(readString(inp), readType(inp) as Type.Function)
        V_BLOCK_REF -> BlockRef(readString(inp))
        V_CONSTANT -> readConstant(inp)
        else -> throw IllegalArgumentException("Unknown value tag: $tag")
    }

    private fun writeConstant(out: DataOutputStream, c: Constant) {
        when (c) {
            is Constant.I1 -> { out.writeByte(C_INT); writeType(out, c.type); out.writeLong(if (c.value) 1L else 0L) }
            is Constant.I8 -> { out.writeByte(C_INT); writeType(out, c.type); out.writeLong(c.value.toLong()) }
            is Constant.I16 -> { out.writeByte(C_INT); writeType(out, c.type); out.writeLong(c.value.toLong()) }
            is Constant.I32 -> { out.writeByte(C_INT); writeType(out, c.type); out.writeLong(c.value.toLong()) }
            is Constant.I64 -> { out.writeByte(C_INT); writeType(out, c.type); out.writeLong(c.value) }
            is Constant.I128 -> { out.writeByte(C_INT); writeType(out, c.type); out.writeLong(c.value) }
            is Constant.IntN -> { out.writeByte(C_INT); writeType(out, c.type); out.writeLong(c.value) }
            is Constant.F16 -> { out.writeByte(C_FLOAT); writeType(out, c.type); out.writeDouble(c.value.toDouble()) }
            is Constant.BF16 -> { out.writeByte(C_FLOAT); writeType(out, c.type); out.writeDouble(c.value.toDouble()) }
            is Constant.F32 -> { out.writeByte(C_FLOAT); writeType(out, c.type); out.writeDouble(c.value.toDouble()) }
            is Constant.F64 -> { out.writeByte(C_FLOAT); writeType(out, c.type); out.writeDouble(c.value) }
            is Constant.F80 -> { out.writeByte(C_FLOAT); writeType(out, c.type); out.writeDouble(c.value) }
            is Constant.F128 -> { out.writeByte(C_FLOAT); writeType(out, c.type); out.writeDouble(c.value) }
            is Constant.NullPtr -> out.writeByte(C_NULL_PTR)
            is Constant.NullRef -> out.writeByte(C_NULL_REF)
            is Constant.Undef -> { out.writeByte(C_UNDEF); writeType(out, c.type) }
            is Constant.Poison -> { out.writeByte(C_POISON); writeType(out, c.type) }
            is Constant.ZeroInitializer -> { out.writeByte(C_ZERO_INIT); writeType(out, c.type) }
            is Constant.ArrayConst -> { out.writeByte(C_ARRAY); writeType(out, c.type); writeList(out, c.elements) { writeConstant(out, it) } }
            is Constant.VectorConst -> { out.writeByte(C_VECTOR); writeType(out, c.type); writeList(out, c.elements) { writeConstant(out, it) } }
            is Constant.StructConst -> { out.writeByte(C_STRUCT); writeType(out, c.type); writeList(out, c.fields) { writeConstant(out, it) } }
            is Constant.StringConst -> { out.writeByte(C_STRING); writeString(out, c.value); out.writeBoolean(c.nullTerminated) }
            is Constant.GetElementPtr -> { out.writeByte(C_GEP); writeType(out, c.type); writeConstant(out, c.base); writeList(out, c.indices) { writeConstant(out, it) }; out.writeBoolean(c.inBounds) }
            is Constant.BitCast -> { out.writeByte(C_BITCAST); writeType(out, c.type); writeConstant(out, c.value) }
            is Constant.IntToPtr -> { out.writeByte(C_INTTOPTR); writeType(out, c.type); writeConstant(out, c.value) }
            is Constant.PtrToInt -> { out.writeByte(C_PTRTOINT); writeType(out, c.type); writeConstant(out, c.value) }
        }
    }

    private fun readConstant(inp: DataInputStream): Constant = when (val tag = inp.readByte().toInt()) {
        C_INT -> {
            val type = readType(inp)
            val value = inp.readLong()
            when (type) {
                is Type.I1 -> Constant.I1(value != 0L)
                is Type.I8 -> Constant.I8(value.toByte())
                is Type.I16 -> Constant.I16(value.toShort())
                is Type.I32 -> Constant.I32(value.toInt())
                is Type.I64 -> Constant.I64(value)
                is Type.I128 -> Constant.I128(value)
                is Type.IntN -> Constant.IntN(value, type.bits)
                else -> Constant.I64(value)
            }
        }
        C_FLOAT -> {
            val type = readType(inp)
            val value = inp.readDouble()
            when (type) {
                is Type.F16 -> Constant.F16(value.toFloat())
                is Type.BF16 -> Constant.BF16(value.toFloat())
                is Type.F32 -> Constant.F32(value.toFloat())
                is Type.F64 -> Constant.F64(value)
                is Type.F80 -> Constant.F80(value)
                is Type.F128 -> Constant.F128(value)
                else -> Constant.F64(value)
            }
        }
        C_NULL_PTR -> Constant.NullPtr
        C_NULL_REF -> Constant.NullRef
        C_UNDEF -> Constant.Undef(readType(inp))
        C_POISON -> Constant.Poison(readType(inp))
        C_ZERO_INIT -> Constant.ZeroInitializer(readType(inp))
        C_ARRAY -> Constant.ArrayConst(readType(inp), readList(inp) { readConstant(inp) })
        C_VECTOR -> Constant.VectorConst(readType(inp), readList(inp) { readConstant(inp) })
        C_STRUCT -> Constant.StructConst(readType(inp), readList(inp) { readConstant(inp) })
        C_STRING -> Constant.StringConst(readString(inp), inp.readBoolean())
        C_GEP -> Constant.GetElementPtr(readType(inp), readConstant(inp), readList(inp) { readConstant(inp) }, inp.readBoolean())
        C_BITCAST -> Constant.BitCast(readType(inp), readConstant(inp))
        C_INTTOPTR -> Constant.IntToPtr(readType(inp), readConstant(inp))
        C_PTRTOINT -> Constant.PtrToInt(readType(inp), readConstant(inp))
        else -> throw IllegalArgumentException("Unknown constant tag: $tag")
    }

    // Instruction serialization

    private fun writeInstruction(out: DataOutputStream, inst: Instruction) {
        out.writeShort(instructionTag(inst))
        when (inst) {
            is Add -> { writeValue(out, inst.dest); writeValue(out, inst.lhs); writeValue(out, inst.rhs); out.writeBoolean(inst.nuw); out.writeBoolean(inst.nsw) }
            is Sub -> { writeValue(out, inst.dest); writeValue(out, inst.lhs); writeValue(out, inst.rhs); out.writeBoolean(inst.nuw); out.writeBoolean(inst.nsw) }
            is Mul -> { writeValue(out, inst.dest); writeValue(out, inst.lhs); writeValue(out, inst.rhs); out.writeBoolean(inst.nuw); out.writeBoolean(inst.nsw) }
            is UDiv -> { writeValue(out, inst.dest); writeValue(out, inst.lhs); writeValue(out, inst.rhs); out.writeBoolean(inst.exact) }
            is SDiv -> { writeValue(out, inst.dest); writeValue(out, inst.lhs); writeValue(out, inst.rhs); out.writeBoolean(inst.exact) }
            is URem -> { writeValue(out, inst.dest); writeValue(out, inst.lhs); writeValue(out, inst.rhs) }
            is SRem -> { writeValue(out, inst.dest); writeValue(out, inst.lhs); writeValue(out, inst.rhs) }
            is Neg -> { writeValue(out, inst.dest); writeValue(out, inst.operand) }

            is SAddOverflow -> { writeValue(out, inst.dest); writeValue(out, inst.lhs); writeValue(out, inst.rhs) }
            is UAddOverflow -> { writeValue(out, inst.dest); writeValue(out, inst.lhs); writeValue(out, inst.rhs) }
            is SSubOverflow -> { writeValue(out, inst.dest); writeValue(out, inst.lhs); writeValue(out, inst.rhs) }
            is USubOverflow -> { writeValue(out, inst.dest); writeValue(out, inst.lhs); writeValue(out, inst.rhs) }
            is SMulOverflow -> { writeValue(out, inst.dest); writeValue(out, inst.lhs); writeValue(out, inst.rhs) }
            is UMulOverflow -> { writeValue(out, inst.dest); writeValue(out, inst.lhs); writeValue(out, inst.rhs) }

            is SAddSat -> { writeValue(out, inst.dest); writeValue(out, inst.lhs); writeValue(out, inst.rhs) }
            is UAddSat -> { writeValue(out, inst.dest); writeValue(out, inst.lhs); writeValue(out, inst.rhs) }
            is SSubSat -> { writeValue(out, inst.dest); writeValue(out, inst.lhs); writeValue(out, inst.rhs) }
            is USubSat -> { writeValue(out, inst.dest); writeValue(out, inst.lhs); writeValue(out, inst.rhs) }

            is SMin -> { writeValue(out, inst.dest); writeValue(out, inst.lhs); writeValue(out, inst.rhs) }
            is SMax -> { writeValue(out, inst.dest); writeValue(out, inst.lhs); writeValue(out, inst.rhs) }
            is UMin -> { writeValue(out, inst.dest); writeValue(out, inst.lhs); writeValue(out, inst.rhs) }
            is UMax -> { writeValue(out, inst.dest); writeValue(out, inst.lhs); writeValue(out, inst.rhs) }
            is Abs -> { writeValue(out, inst.dest); writeValue(out, inst.operand); out.writeBoolean(inst.isIntMin) }

            is FAdd -> { writeValue(out, inst.dest); writeValue(out, inst.lhs); writeValue(out, inst.rhs); writeFastMath(out, inst.fastMath) }
            is FSub -> { writeValue(out, inst.dest); writeValue(out, inst.lhs); writeValue(out, inst.rhs); writeFastMath(out, inst.fastMath) }
            is FMul -> { writeValue(out, inst.dest); writeValue(out, inst.lhs); writeValue(out, inst.rhs); writeFastMath(out, inst.fastMath) }
            is FDiv -> { writeValue(out, inst.dest); writeValue(out, inst.lhs); writeValue(out, inst.rhs); writeFastMath(out, inst.fastMath) }
            is FRem -> { writeValue(out, inst.dest); writeValue(out, inst.lhs); writeValue(out, inst.rhs); writeFastMath(out, inst.fastMath) }
            is FNeg -> { writeValue(out, inst.dest); writeValue(out, inst.operand); writeFastMath(out, inst.fastMath) }
            is FAbs -> { writeValue(out, inst.dest); writeValue(out, inst.operand) }
            is FMA -> { writeValue(out, inst.dest); writeValue(out, inst.a); writeValue(out, inst.b); writeValue(out, inst.c) }
            is FMin -> { writeValue(out, inst.dest); writeValue(out, inst.lhs); writeValue(out, inst.rhs) }
            is FMax -> { writeValue(out, inst.dest); writeValue(out, inst.lhs); writeValue(out, inst.rhs) }
            is Sqrt -> { writeValue(out, inst.dest); writeValue(out, inst.operand) }
            is Ceil -> { writeValue(out, inst.dest); writeValue(out, inst.operand) }
            is Floor -> { writeValue(out, inst.dest); writeValue(out, inst.operand) }
            is Round -> { writeValue(out, inst.dest); writeValue(out, inst.operand) }
            is Trunc -> { writeValue(out, inst.dest); writeValue(out, inst.operand) }
            is CopySign -> { writeValue(out, inst.dest); writeValue(out, inst.magnitude); writeValue(out, inst.sign) }

            is And -> { writeValue(out, inst.dest); writeValue(out, inst.lhs); writeValue(out, inst.rhs) }
            is Or -> { writeValue(out, inst.dest); writeValue(out, inst.lhs); writeValue(out, inst.rhs) }
            is Xor -> { writeValue(out, inst.dest); writeValue(out, inst.lhs); writeValue(out, inst.rhs) }
            is Not -> { writeValue(out, inst.dest); writeValue(out, inst.operand) }
            is Shl -> { writeValue(out, inst.dest); writeValue(out, inst.lhs); writeValue(out, inst.rhs); out.writeBoolean(inst.nuw); out.writeBoolean(inst.nsw) }
            is LShr -> { writeValue(out, inst.dest); writeValue(out, inst.lhs); writeValue(out, inst.rhs); out.writeBoolean(inst.exact) }
            is AShr -> { writeValue(out, inst.dest); writeValue(out, inst.lhs); writeValue(out, inst.rhs); out.writeBoolean(inst.exact) }
            is RotateLeft -> { writeValue(out, inst.dest); writeValue(out, inst.value); writeValue(out, inst.amount) }
            is RotateRight -> { writeValue(out, inst.dest); writeValue(out, inst.value); writeValue(out, inst.amount) }
            is Rotl -> { writeValue(out, inst.dest); writeValue(out, inst.value); writeValue(out, inst.amount) }
            is Rotr -> { writeValue(out, inst.dest); writeValue(out, inst.value); writeValue(out, inst.amount) }

            is Ctlz -> { writeValue(out, inst.dest); writeValue(out, inst.operand); out.writeBoolean(inst.isZeroPoison) }
            is Cttz -> { writeValue(out, inst.dest); writeValue(out, inst.operand); out.writeBoolean(inst.isZeroPoison) }
            is Ctpop -> { writeValue(out, inst.dest); writeValue(out, inst.operand) }
            is BSwap -> { writeValue(out, inst.dest); writeValue(out, inst.operand) }
            is BitReverse -> { writeValue(out, inst.dest); writeValue(out, inst.operand) }

            is ICmp -> { writeValue(out, inst.dest); out.writeInt(inst.predicate.ordinal); writeValue(out, inst.lhs); writeValue(out, inst.rhs) }
            is FCmp -> { writeValue(out, inst.dest); out.writeInt(inst.predicate.ordinal); writeValue(out, inst.lhs); writeValue(out, inst.rhs); writeFastMath(out, inst.fastMath) }

            is Alloca -> { writeValue(out, inst.dest); writeType(out, inst.allocType); writeNullableValue(out, inst.numElements); writeNullableInt(out, inst.align) }
            is Load -> { writeValue(out, inst.dest); writeValue(out, inst.ptr); writeType(out, inst.loadType); writeNullableInt(out, inst.align); out.writeBoolean(inst.volatile); writeNullableOrdering(out, inst.ordering) }
            is Store -> { writeValue(out, inst.value); writeValue(out, inst.ptr); writeNullableInt(out, inst.align); out.writeBoolean(inst.volatile); writeNullableOrdering(out, inst.ordering) }
            is GetElementPtr -> { writeValue(out, inst.dest); writeType(out, inst.baseType); writeValue(out, inst.ptr); writeList(out, inst.indices) { writeValue(out, it) }; out.writeBoolean(inst.inBounds) }
            is Fence -> { out.writeInt(inst.ordering.ordinal); writeNullableString(out, inst.syncScope) }
            is CmpXchg -> { writeValue(out, inst.dest); writeValue(out, inst.ptr); writeValue(out, inst.cmp); writeValue(out, inst.new); out.writeInt(inst.successOrdering.ordinal); out.writeInt(inst.failureOrdering.ordinal); out.writeBoolean(inst.weak); out.writeBoolean(inst.volatile) }
            is AtomicRMW -> { writeValue(out, inst.dest); out.writeInt(inst.op.ordinal); writeValue(out, inst.ptr); writeValue(out, inst.value); out.writeInt(inst.ordering.ordinal); out.writeBoolean(inst.volatile) }
            is MemCpy -> { writeValue(out, inst.dst); writeValue(out, inst.src); writeValue(out, inst.len); out.writeBoolean(inst.volatile) }
            is MemSet -> { writeValue(out, inst.dst); writeValue(out, inst.value); writeValue(out, inst.len); out.writeBoolean(inst.volatile) }
            is MemMove -> { writeValue(out, inst.dst); writeValue(out, inst.src); writeValue(out, inst.len); out.writeBoolean(inst.volatile) }
            is Prefetch -> { writeValue(out, inst.address); out.writeInt(inst.rw); out.writeInt(inst.locality); out.writeInt(inst.cacheType) }

            is StackSave -> writeValue(out, inst.dest)
            is StackRestore -> writeValue(out, inst.ptr)
            is LifetimeStart -> { writeValue(out, inst.ptr); out.writeLong(inst.size) }
            is LifetimeEnd -> { writeValue(out, inst.ptr); out.writeLong(inst.size) }

            is IntTrunc -> { writeValue(out, inst.dest); writeValue(out, inst.value); writeType(out, inst.toType) }
            is ZExt -> { writeValue(out, inst.dest); writeValue(out, inst.value); writeType(out, inst.toType) }
            is SExt -> { writeValue(out, inst.dest); writeValue(out, inst.value); writeType(out, inst.toType) }
            is FPTrunc -> { writeValue(out, inst.dest); writeValue(out, inst.value); writeType(out, inst.toType) }
            is FPExt -> { writeValue(out, inst.dest); writeValue(out, inst.value); writeType(out, inst.toType) }
            is FPToUI -> { writeValue(out, inst.dest); writeValue(out, inst.value); writeType(out, inst.toType) }
            is FPToSI -> { writeValue(out, inst.dest); writeValue(out, inst.value); writeType(out, inst.toType) }
            is UIToFP -> { writeValue(out, inst.dest); writeValue(out, inst.value); writeType(out, inst.toType) }
            is SIToFP -> { writeValue(out, inst.dest); writeValue(out, inst.value); writeType(out, inst.toType) }
            is PtrToInt -> { writeValue(out, inst.dest); writeValue(out, inst.value); writeType(out, inst.toType) }
            is IntToPtr -> { writeValue(out, inst.dest); writeValue(out, inst.value); writeType(out, inst.toType) }
            is BitCast -> { writeValue(out, inst.dest); writeValue(out, inst.value); writeType(out, inst.toType) }
            is AddrSpaceCast -> { writeValue(out, inst.dest); writeValue(out, inst.value); writeType(out, inst.toType) }

            is Ret -> writeNullableValue(out, inst.value)
            is Br -> writeString(out, inst.target)
            is CondBr -> { writeValue(out, inst.condition); writeString(out, inst.trueTarget); writeString(out, inst.falseTarget) }
            is Switch -> { writeValue(out, inst.value); writeString(out, inst.defaultTarget); writeList(out, inst.cases) { writeConstant(out, it.first); writeString(out, it.second) } }
            is IndirectBr -> { writeValue(out, inst.address); writeList(out, inst.targets) { writeString(out, it) } }
            is Unreachable -> {}
            is Trap -> {}
            is DebugTrap -> {}

            is Call -> {
                writeNullableValue(out, inst.dest); writeValue(out, inst.function); writeList(out, inst.args) { writeValue(out, it) }
                writeType(out, inst.returnType); out.writeInt(inst.callingConv.ordinal); out.writeInt(inst.tailCall.ordinal)
            }
            is Invoke -> {
                writeNullableValue(out, inst.dest); writeValue(out, inst.function); writeList(out, inst.args) { writeValue(out, it) }
                writeType(out, inst.returnType); writeString(out, inst.normalDest); writeString(out, inst.unwindDest); out.writeInt(inst.callingConv.ordinal)
            }
            is CallBr -> {
                writeNullableValue(out, inst.dest); writeValue(out, inst.function); writeList(out, inst.args) { writeValue(out, it) }
                writeType(out, inst.returnType); writeString(out, inst.fallthrough); writeList(out, inst.indirectDests) { writeString(out, it) }
            }

            is VAStart -> writeValue(out, inst.argList)
            is VAEnd -> writeValue(out, inst.argList)
            is VACopy -> { writeValue(out, inst.dst); writeValue(out, inst.src) }
            is VAArg -> { writeValue(out, inst.dest); writeValue(out, inst.argList); writeType(out, inst.argType) }

            is LandingPad -> { writeValue(out, inst.dest); writeType(out, inst.resultType); writeList(out, inst.clauses) { writeLandingPadClause(out, it) }; out.writeBoolean(inst.cleanup) }
            is Resume -> writeValue(out, inst.value)
            is CatchSwitch -> { writeValue(out, inst.dest); writeNullableValue(out, inst.parentPad); writeList(out, inst.handlers) { writeString(out, it) }; writeNullableString(out, inst.unwindDest) }
            is CatchPad -> { writeValue(out, inst.dest); writeValue(out, inst.catchSwitch); writeList(out, inst.args) { writeValue(out, it) } }
            is CleanupPad -> { writeValue(out, inst.dest); writeNullableValue(out, inst.parentPad); writeList(out, inst.args) { writeValue(out, it) } }
            is CatchRet -> { writeValue(out, inst.catchPad); writeString(out, inst.dest) }
            is CleanupRet -> { writeValue(out, inst.cleanupPad); writeNullableString(out, inst.unwindDest) }

            is Phi -> { writeValue(out, inst.dest); writeList(out, inst.incoming) { writeValue(out, it.first); writeString(out, it.second) } }
            is Select -> { writeValue(out, inst.dest); writeValue(out, inst.condition); writeValue(out, inst.trueValue); writeValue(out, inst.falseValue) }
            is Freeze -> { writeValue(out, inst.dest); writeValue(out, inst.value) }

            is ExtractElement -> { writeValue(out, inst.dest); writeValue(out, inst.vector); writeValue(out, inst.index) }
            is InsertElement -> { writeValue(out, inst.dest); writeValue(out, inst.vector); writeValue(out, inst.element); writeValue(out, inst.index) }
            is ShuffleVector -> { writeValue(out, inst.dest); writeValue(out, inst.v1); writeValue(out, inst.v2); writeList(out, inst.mask) { out.writeInt(it) } }
            is Splat -> { writeValue(out, inst.dest); writeValue(out, inst.scalar); writeType(out, inst.vectorType) }
            is VectorReduce -> { writeValue(out, inst.dest); out.writeInt(inst.op.ordinal); writeValue(out, inst.vector) }

            is ExtractValue -> { writeValue(out, inst.dest); writeValue(out, inst.aggregate); writeList(out, inst.indices) { out.writeInt(it) } }
            is InsertValue -> { writeValue(out, inst.dest); writeValue(out, inst.aggregate); writeValue(out, inst.element); writeList(out, inst.indices) { out.writeInt(it) } }

            // High-level
            is NewObject -> { writeValue(out, inst.dest); writeString(out, inst.className); writeList(out, inst.typeArgs) { writeType(out, it) } }
            is NewArray -> { writeValue(out, inst.dest); writeType(out, inst.elementType); writeValue(out, inst.size) }
            is NewMultiArray -> { writeValue(out, inst.dest); writeType(out, inst.elementType); writeList(out, inst.dimensions) { writeValue(out, it) } }

            is GetField -> { writeValue(out, inst.dest); writeValue(out, inst.obj); writeString(out, inst.className); writeString(out, inst.fieldName); writeType(out, inst.fieldType) }
            is PutField -> { writeValue(out, inst.obj); writeString(out, inst.className); writeString(out, inst.fieldName); writeType(out, inst.fieldType); writeValue(out, inst.value) }
            is GetStatic -> { writeValue(out, inst.dest); writeString(out, inst.className); writeString(out, inst.fieldName); writeType(out, inst.fieldType) }
            is PutStatic -> { writeString(out, inst.className); writeString(out, inst.fieldName); writeType(out, inst.fieldType); writeValue(out, inst.value) }

            is VirtualCall -> { writeNullableValue(out, inst.dest); writeValue(out, inst.obj); writeString(out, inst.className); writeString(out, inst.methodName); writeType(out, inst.methodType); writeList(out, inst.args) { writeValue(out, it) } }
            is InterfaceCall -> { writeNullableValue(out, inst.dest); writeValue(out, inst.obj); writeString(out, inst.interfaceName); writeString(out, inst.methodName); writeType(out, inst.methodType); writeList(out, inst.args) { writeValue(out, it) } }
            is SpecialCall -> { writeNullableValue(out, inst.dest); writeValue(out, inst.obj); writeString(out, inst.className); writeString(out, inst.methodName); writeType(out, inst.methodType); writeList(out, inst.args) { writeValue(out, it) } }
            is StaticCall -> { writeNullableValue(out, inst.dest); writeString(out, inst.className); writeString(out, inst.methodName); writeType(out, inst.methodType); writeList(out, inst.args) { writeValue(out, it) } }
            is DynamicCall -> { writeNullableValue(out, inst.dest); writeBootstrapMethod(out, inst.bootstrapMethod); writeString(out, inst.name); writeType(out, inst.methodType); writeList(out, inst.args) { writeValue(out, it) } }
            is ConstructorCall -> { writeValue(out, inst.obj); writeString(out, inst.className); writeType(out, inst.constructorType); writeList(out, inst.args) { writeValue(out, it) } }

            is InstanceOf -> { writeValue(out, inst.dest); writeValue(out, inst.obj); writeType(out, inst.checkType) }
            is CheckCast -> { writeValue(out, inst.dest); writeValue(out, inst.obj); writeType(out, inst.castType) }
            is TypeId -> { writeValue(out, inst.dest); writeValue(out, inst.obj) }

            is ArrayGet -> { writeValue(out, inst.dest); writeValue(out, inst.array); writeValue(out, inst.index); writeType(out, inst.elementType) }
            is ArraySet -> { writeValue(out, inst.array); writeValue(out, inst.index); writeValue(out, inst.value); writeType(out, inst.elementType) }
            is ArrayLength -> { writeValue(out, inst.dest); writeValue(out, inst.array) }

            is MonitorEnter -> writeValue(out, inst.obj)
            is MonitorExit -> writeValue(out, inst.obj)

            is Throw -> writeValue(out, inst.exception)
            is TryCatchRegion -> { writeString(out, inst.tryBlock); writeList(out, inst.catches) { writeCatchHandler(out, it) }; writeNullableString(out, inst.finallyBlock) }

            is Box -> { writeValue(out, inst.dest); writeValue(out, inst.value); writeType(out, inst.boxType) }
            is Unbox -> { writeValue(out, inst.dest); writeValue(out, inst.obj); writeType(out, inst.unboxType) }

            is CatchValue -> { writeValue(out, inst.dest); writeType(out, inst.exceptionType) }
            is MakeWeakRef -> { writeValue(out, inst.dest); writeValue(out, inst.obj) }
            is ReadWeakRef -> { writeValue(out, inst.dest); writeValue(out, inst.weakRef) }
            is ClearWeakRef -> writeValue(out, inst.weakRef)

            is ClosureCreate -> { writeValue(out, inst.dest); writeValue(out, inst.function); writeList(out, inst.captures) { writeValue(out, it) }; writeType(out, inst.closureType) }
            is ClosureInvoke -> { writeNullableValue(out, inst.dest); writeValue(out, inst.closure); writeList(out, inst.args) { writeValue(out, it) }; writeType(out, inst.returnType) }

            is ConstructVariant -> { writeValue(out, inst.dest); writeType(out, inst.unionType); writeString(out, inst.variantName); writeList(out, inst.fields) { writeValue(out, it) } }
            is GetTag -> { writeValue(out, inst.dest); writeValue(out, inst.union) }
            is GetVariantField -> { writeValue(out, inst.dest); writeValue(out, inst.union); writeString(out, inst.variantName); out.writeInt(inst.fieldIndex) }
            is TagSwitch -> { writeValue(out, inst.union); writeList(out, inst.cases) { writeString(out, it.first); writeString(out, it.second) }; writeNullableString(out, inst.defaultTarget) }

            is GCAlloc -> { writeValue(out, inst.dest); writeType(out, inst.allocType); writeNullableValue(out, inst.size) }
            is GCSafepoint -> {}
            is GCRoot -> { writeValue(out, inst.ptr); writeNullableValue(out, inst.metadata) }
            is Pin -> { writeValue(out, inst.dest); writeValue(out, inst.ref) }
            is Unpin -> writeValue(out, inst.ref)
            is InteriorPtr -> { writeValue(out, inst.dest); writeValue(out, inst.ref); writeValue(out, inst.index); writeType(out, inst.pointeeType) }
            is WriteBarrier -> { writeValue(out, inst.obj); writeValue(out, inst.fieldIndex); writeValue(out, inst.value) }
            is ReadBarrier -> { writeValue(out, inst.dest); writeValue(out, inst.ref) }
            is ManagedCall -> { writeNullableValue(out, inst.dest); writeValue(out, inst.function); writeList(out, inst.args) { writeValue(out, it) }; writeType(out, inst.returnType); out.writeInt(inst.direction.ordinal) }

            is RefRetain -> writeValue(out, inst.obj)
            is RefRelease -> writeValue(out, inst.obj)
            is RefCount -> { writeValue(out, inst.dest); writeValue(out, inst.obj) }

            is CoroBegin -> { writeValue(out, inst.dest); writeValue(out, inst.id); writeValue(out, inst.mem) }
            is CoroEnd -> { writeValue(out, inst.handle); out.writeBoolean(inst.unwind) }
            is CoroSuspend -> { writeValue(out, inst.dest); writeNullableValue(out, inst.save); out.writeBoolean(inst.isFinal) }
            is CoroResume -> writeValue(out, inst.handle)
            is CoroDestroy -> writeValue(out, inst.handle)
            is CoroSize -> writeValue(out, inst.dest)

            is Intrinsic -> { writeNullableValue(out, inst.dest); writeString(out, inst.name); writeList(out, inst.args) { writeValue(out, it) }; writeType(out, inst.returnType) }
            is InlineAsm -> { writeNullableValue(out, inst.dest); writeString(out, inst.assembly); writeString(out, inst.constraints); out.writeBoolean(inst.sideEffects); out.writeBoolean(inst.alignStack); out.writeInt(inst.dialect.ordinal); writeList(out, inst.args) { writeValue(out, it) }; writeType(out, inst.returnType) }

            is DebugLoc -> { out.writeInt(inst.line); out.writeInt(inst.col); writeString(out, inst.scope); writeNullableString(out, inst.inlinedAt) }
            is DebugValue -> { writeString(out, inst.variable); writeValue(out, inst.value); writeNullableString(out, inst.expression) }
            is DebugDeclare -> { writeString(out, inst.variable); writeValue(out, inst.address); writeNullableString(out, inst.expression) }

            is Assume -> writeValue(out, inst.condition)
            is Expect -> { writeValue(out, inst.dest); writeValue(out, inst.value); writeConstant(out, inst.expected) }
        }
    }

    private fun readInstruction(inp: DataInputStream): Instruction = when (val tag = inp.readShort().toInt()) {
        I_ADD -> Add(readValue(inp) as InstructionRef, readValue(inp), readValue(inp), inp.readBoolean(), inp.readBoolean())
        I_SUB -> Sub(readValue(inp) as InstructionRef, readValue(inp), readValue(inp), inp.readBoolean(), inp.readBoolean())
        I_MUL -> Mul(readValue(inp) as InstructionRef, readValue(inp), readValue(inp), inp.readBoolean(), inp.readBoolean())
        I_UDIV -> UDiv(readValue(inp) as InstructionRef, readValue(inp), readValue(inp), inp.readBoolean())
        I_SDIV -> SDiv(readValue(inp) as InstructionRef, readValue(inp), readValue(inp), inp.readBoolean())
        I_UREM -> URem(readValue(inp) as InstructionRef, readValue(inp), readValue(inp))
        I_SREM -> SRem(readValue(inp) as InstructionRef, readValue(inp), readValue(inp))
        I_NEG -> Neg(readValue(inp) as InstructionRef, readValue(inp))

        I_SADD_OVF -> SAddOverflow(readValue(inp) as InstructionRef, readValue(inp), readValue(inp))
        I_UADD_OVF -> UAddOverflow(readValue(inp) as InstructionRef, readValue(inp), readValue(inp))
        I_SSUB_OVF -> SSubOverflow(readValue(inp) as InstructionRef, readValue(inp), readValue(inp))
        I_USUB_OVF -> USubOverflow(readValue(inp) as InstructionRef, readValue(inp), readValue(inp))
        I_SMUL_OVF -> SMulOverflow(readValue(inp) as InstructionRef, readValue(inp), readValue(inp))
        I_UMUL_OVF -> UMulOverflow(readValue(inp) as InstructionRef, readValue(inp), readValue(inp))

        I_SADD_SAT -> SAddSat(readValue(inp) as InstructionRef, readValue(inp), readValue(inp))
        I_UADD_SAT -> UAddSat(readValue(inp) as InstructionRef, readValue(inp), readValue(inp))
        I_SSUB_SAT -> SSubSat(readValue(inp) as InstructionRef, readValue(inp), readValue(inp))
        I_USUB_SAT -> USubSat(readValue(inp) as InstructionRef, readValue(inp), readValue(inp))

        I_SMIN -> SMin(readValue(inp) as InstructionRef, readValue(inp), readValue(inp))
        I_SMAX -> SMax(readValue(inp) as InstructionRef, readValue(inp), readValue(inp))
        I_UMIN -> UMin(readValue(inp) as InstructionRef, readValue(inp), readValue(inp))
        I_UMAX -> UMax(readValue(inp) as InstructionRef, readValue(inp), readValue(inp))
        I_ABS -> Abs(readValue(inp) as InstructionRef, readValue(inp), inp.readBoolean())

        I_FADD -> FAdd(readValue(inp) as InstructionRef, readValue(inp), readValue(inp), readFastMath(inp))
        I_FSUB -> FSub(readValue(inp) as InstructionRef, readValue(inp), readValue(inp), readFastMath(inp))
        I_FMUL -> FMul(readValue(inp) as InstructionRef, readValue(inp), readValue(inp), readFastMath(inp))
        I_FDIV -> FDiv(readValue(inp) as InstructionRef, readValue(inp), readValue(inp), readFastMath(inp))
        I_FREM -> FRem(readValue(inp) as InstructionRef, readValue(inp), readValue(inp), readFastMath(inp))
        I_FNEG -> FNeg(readValue(inp) as InstructionRef, readValue(inp), readFastMath(inp))
        I_FABS -> FAbs(readValue(inp) as InstructionRef, readValue(inp))
        I_FMA -> FMA(readValue(inp) as InstructionRef, readValue(inp), readValue(inp), readValue(inp))
        I_FMIN -> FMin(readValue(inp) as InstructionRef, readValue(inp), readValue(inp))
        I_FMAX -> FMax(readValue(inp) as InstructionRef, readValue(inp), readValue(inp))
        I_SQRT -> Sqrt(readValue(inp) as InstructionRef, readValue(inp))
        I_CEIL -> Ceil(readValue(inp) as InstructionRef, readValue(inp))
        I_FLOOR -> Floor(readValue(inp) as InstructionRef, readValue(inp))
        I_ROUND -> Round(readValue(inp) as InstructionRef, readValue(inp))
        I_FTRUNC -> Trunc(readValue(inp) as InstructionRef, readValue(inp))
        I_COPYSIGN -> CopySign(readValue(inp) as InstructionRef, readValue(inp), readValue(inp))

        I_AND -> And(readValue(inp) as InstructionRef, readValue(inp), readValue(inp))
        I_OR -> Or(readValue(inp) as InstructionRef, readValue(inp), readValue(inp))
        I_XOR -> Xor(readValue(inp) as InstructionRef, readValue(inp), readValue(inp))
        I_NOT -> Not(readValue(inp) as InstructionRef, readValue(inp))
        I_SHL -> Shl(readValue(inp) as InstructionRef, readValue(inp), readValue(inp), inp.readBoolean(), inp.readBoolean())
        I_LSHR -> LShr(readValue(inp) as InstructionRef, readValue(inp), readValue(inp), inp.readBoolean())
        I_ASHR -> AShr(readValue(inp) as InstructionRef, readValue(inp), readValue(inp), inp.readBoolean())
        I_ROTL -> RotateLeft(readValue(inp) as InstructionRef, readValue(inp), readValue(inp))
        I_ROTR -> RotateRight(readValue(inp) as InstructionRef, readValue(inp), readValue(inp))
        I_ROTL2 -> Rotl(readValue(inp) as InstructionRef, readValue(inp), readValue(inp))
        I_ROTR2 -> Rotr(readValue(inp) as InstructionRef, readValue(inp), readValue(inp))

        I_CTLZ -> Ctlz(readValue(inp) as InstructionRef, readValue(inp), inp.readBoolean())
        I_CTTZ -> Cttz(readValue(inp) as InstructionRef, readValue(inp), inp.readBoolean())
        I_CTPOP -> Ctpop(readValue(inp) as InstructionRef, readValue(inp))
        I_BSWAP -> BSwap(readValue(inp) as InstructionRef, readValue(inp))
        I_BITREVERSE -> BitReverse(readValue(inp) as InstructionRef, readValue(inp))

        I_ICMP -> ICmp(readValue(inp) as InstructionRef, ICmpPredicate.entries[inp.readInt()], readValue(inp), readValue(inp))
        I_FCMP -> FCmp(readValue(inp) as InstructionRef, FCmpPredicate.entries[inp.readInt()], readValue(inp), readValue(inp), readFastMath(inp))

        I_ALLOCA -> Alloca(readValue(inp) as InstructionRef, readType(inp), readNullableValue(inp), readNullableInt(inp))
        I_LOAD -> Load(readValue(inp) as InstructionRef, readValue(inp), readType(inp), readNullableInt(inp), inp.readBoolean(), readNullableOrdering(inp))
        I_STORE -> Store(readValue(inp), readValue(inp), readNullableInt(inp), inp.readBoolean(), readNullableOrdering(inp))
        I_GEP -> GetElementPtr(readValue(inp) as InstructionRef, readType(inp), readValue(inp), readList(inp) { readValue(inp) }, inp.readBoolean())
        I_FENCE -> Fence(AtomicOrdering.entries[inp.readInt()], readNullableString(inp))
        I_CMPXCHG -> CmpXchg(readValue(inp) as InstructionRef, readValue(inp), readValue(inp), readValue(inp), AtomicOrdering.entries[inp.readInt()], AtomicOrdering.entries[inp.readInt()], inp.readBoolean(), inp.readBoolean())
        I_ATOMICRMW -> AtomicRMW(readValue(inp) as InstructionRef, AtomicRMWOp.entries[inp.readInt()], readValue(inp), readValue(inp), AtomicOrdering.entries[inp.readInt()], inp.readBoolean())
        I_MEMCPY -> MemCpy(readValue(inp), readValue(inp), readValue(inp), inp.readBoolean())
        I_MEMSET -> MemSet(readValue(inp), readValue(inp), readValue(inp), inp.readBoolean())
        I_MEMMOVE -> MemMove(readValue(inp), readValue(inp), readValue(inp), inp.readBoolean())
        I_PREFETCH -> Prefetch(readValue(inp), inp.readInt(), inp.readInt(), inp.readInt())

        I_STACKSAVE -> StackSave(readValue(inp) as InstructionRef)
        I_STACKRESTORE -> StackRestore(readValue(inp))
        I_LIFETIME_START -> LifetimeStart(readValue(inp), inp.readLong())
        I_LIFETIME_END -> LifetimeEnd(readValue(inp), inp.readLong())

        I_INTTRUNC -> IntTrunc(readValue(inp) as InstructionRef, readValue(inp), readType(inp))
        I_ZEXT -> ZExt(readValue(inp) as InstructionRef, readValue(inp), readType(inp))
        I_SEXT -> SExt(readValue(inp) as InstructionRef, readValue(inp), readType(inp))
        I_FPTRUNC -> FPTrunc(readValue(inp) as InstructionRef, readValue(inp), readType(inp))
        I_FPEXT -> FPExt(readValue(inp) as InstructionRef, readValue(inp), readType(inp))
        I_FPTOUI -> FPToUI(readValue(inp) as InstructionRef, readValue(inp), readType(inp))
        I_FPTOSI -> FPToSI(readValue(inp) as InstructionRef, readValue(inp), readType(inp))
        I_UITOFP -> UIToFP(readValue(inp) as InstructionRef, readValue(inp), readType(inp))
        I_SITOFP -> SIToFP(readValue(inp) as InstructionRef, readValue(inp), readType(inp))
        I_PTRTOINT -> PtrToInt(readValue(inp) as InstructionRef, readValue(inp), readType(inp))
        I_INTTOPTR -> IntToPtr(readValue(inp) as InstructionRef, readValue(inp), readType(inp))
        I_BITCAST -> BitCast(readValue(inp) as InstructionRef, readValue(inp), readType(inp))
        I_ADDRSPACECAST -> AddrSpaceCast(readValue(inp) as InstructionRef, readValue(inp), readType(inp))

        I_RET -> Ret(readNullableValue(inp))
        I_BR -> Br(readString(inp))
        I_CONDBR -> CondBr(readValue(inp), readString(inp), readString(inp))
        I_SWITCH -> Switch(readValue(inp), readString(inp), readList(inp) { readConstant(inp) to readString(inp) })
        I_INDIRECTBR -> IndirectBr(readValue(inp), readList(inp) { readString(inp) })
        I_UNREACHABLE -> Unreachable()
        I_TRAP -> Trap()
        I_DEBUGTRAP -> DebugTrap()

        I_CALL -> Call(readNullableValue(inp) as InstructionRef?, readValue(inp), readList(inp) { readValue(inp) }, readType(inp), CallingConvention.entries[inp.readInt()], TailCallKind.entries[inp.readInt()])
        I_INVOKE -> { val d = readNullableValue(inp) as InstructionRef?; val f = readValue(inp); val a = readList(inp) { readValue(inp) }; val rt = readType(inp); Invoke(d, f, a, rt, readString(inp), readString(inp), CallingConvention.entries[inp.readInt()]) }
        I_CALLBR -> { val d = readNullableValue(inp) as InstructionRef?; val f = readValue(inp); val a = readList(inp) { readValue(inp) }; val rt = readType(inp); CallBr(d, f, a, rt, readString(inp), readList(inp) { readString(inp) }) }

        I_VASTART -> VAStart(readValue(inp))
        I_VAEND -> VAEnd(readValue(inp))
        I_VACOPY -> VACopy(readValue(inp), readValue(inp))
        I_VAARG -> VAArg(readValue(inp) as InstructionRef, readValue(inp), readType(inp))

        I_LANDINGPAD -> { val d = readValue(inp) as InstructionRef; val rt = readType(inp); LandingPad(d, rt, readList(inp) { readLandingPadClause(inp) }, inp.readBoolean()) }
        I_RESUME -> Resume(readValue(inp))
        I_CATCHSWITCH -> CatchSwitch(readValue(inp) as InstructionRef, readNullableValue(inp), readList(inp) { readString(inp) }, readNullableString(inp))
        I_CATCHPAD -> CatchPad(readValue(inp) as InstructionRef, readValue(inp), readList(inp) { readValue(inp) })
        I_CLEANUPPAD -> CleanupPad(readValue(inp) as InstructionRef, readNullableValue(inp), readList(inp) { readValue(inp) })
        I_CATCHRET -> CatchRet(readValue(inp), readString(inp))
        I_CLEANUPRET -> CleanupRet(readValue(inp), readNullableString(inp))

        I_PHI -> Phi(readValue(inp) as InstructionRef, readList(inp) { readValue(inp) to readString(inp) })
        I_SELECT -> Select(readValue(inp) as InstructionRef, readValue(inp), readValue(inp), readValue(inp))
        I_FREEZE -> Freeze(readValue(inp) as InstructionRef, readValue(inp))

        I_EXTRACTELEMENT -> ExtractElement(readValue(inp) as InstructionRef, readValue(inp), readValue(inp))
        I_INSERTELEMENT -> InsertElement(readValue(inp) as InstructionRef, readValue(inp), readValue(inp), readValue(inp))
        I_SHUFFLEVECTOR -> ShuffleVector(readValue(inp) as InstructionRef, readValue(inp), readValue(inp), readList(inp) { inp.readInt() })
        I_SPLAT -> Splat(readValue(inp) as InstructionRef, readValue(inp), readType(inp) as Type.Vector)
        I_VECTORREDUCE -> VectorReduce(readValue(inp) as InstructionRef, VectorReduceOp.entries[inp.readInt()], readValue(inp))

        I_EXTRACTVALUE -> ExtractValue(readValue(inp) as InstructionRef, readValue(inp), readList(inp) { inp.readInt() })
        I_INSERTVALUE -> InsertValue(readValue(inp) as InstructionRef, readValue(inp), readValue(inp), readList(inp) { inp.readInt() })

        I_NEWOBJECT -> NewObject(readValue(inp) as InstructionRef, readString(inp), readList(inp) { readType(inp) })
        I_NEWARRAY -> NewArray(readValue(inp) as InstructionRef, readType(inp), readValue(inp))
        I_NEWMULTIARRAY -> NewMultiArray(readValue(inp) as InstructionRef, readType(inp), readList(inp) { readValue(inp) })

        I_GETFIELD -> GetField(readValue(inp) as InstructionRef, readValue(inp), readString(inp), readString(inp), readType(inp))
        I_PUTFIELD -> PutField(readValue(inp), readString(inp), readString(inp), readType(inp), readValue(inp))
        I_GETSTATIC -> GetStatic(readValue(inp) as InstructionRef, readString(inp), readString(inp), readType(inp))
        I_PUTSTATIC -> PutStatic(readString(inp), readString(inp), readType(inp), readValue(inp))

        I_VIRTUALCALL -> { val d = readNullableValue(inp) as InstructionRef?; val o = readValue(inp); VirtualCall(d, o, readString(inp), readString(inp), readType(inp) as Type.Function, readList(inp) { readValue(inp) }) }
        I_INTERFACECALL -> { val d = readNullableValue(inp) as InstructionRef?; val o = readValue(inp); InterfaceCall(d, o, readString(inp), readString(inp), readType(inp) as Type.Function, readList(inp) { readValue(inp) }) }
        I_SPECIALCALL -> { val d = readNullableValue(inp) as InstructionRef?; val o = readValue(inp); SpecialCall(d, o, readString(inp), readString(inp), readType(inp) as Type.Function, readList(inp) { readValue(inp) }) }
        I_STATICCALL -> StaticCall(readNullableValue(inp) as InstructionRef?, readString(inp), readString(inp), readType(inp) as Type.Function, readList(inp) { readValue(inp) })
        I_DYNAMICCALL -> DynamicCall(readNullableValue(inp) as InstructionRef?, readBootstrapMethod(inp), readString(inp), readType(inp) as Type.Function, readList(inp) { readValue(inp) })
        I_CONSTRUCTORCALL -> ConstructorCall(readValue(inp), readString(inp), readType(inp) as Type.Function, readList(inp) { readValue(inp) })

        I_INSTANCEOF -> InstanceOf(readValue(inp) as InstructionRef, readValue(inp), readType(inp))
        I_CHECKCAST -> CheckCast(readValue(inp) as InstructionRef, readValue(inp), readType(inp))
        I_TYPEID -> TypeId(readValue(inp) as InstructionRef, readValue(inp))

        I_ARRAYGET -> ArrayGet(readValue(inp) as InstructionRef, readValue(inp), readValue(inp), readType(inp))
        I_ARRAYSET -> ArraySet(readValue(inp), readValue(inp), readValue(inp), readType(inp))
        I_ARRAYLENGTH -> ArrayLength(readValue(inp) as InstructionRef, readValue(inp))

        I_MONITORENTER -> MonitorEnter(readValue(inp))
        I_MONITOREXIT -> MonitorExit(readValue(inp))

        I_THROW -> Throw(readValue(inp))
        I_TRYCATCH -> TryCatchRegion(readString(inp), readList(inp) { readCatchHandler(inp) }, readNullableString(inp))

        I_BOX -> Box(readValue(inp) as InstructionRef, readValue(inp), readType(inp))
        I_UNBOX -> Unbox(readValue(inp) as InstructionRef, readValue(inp), readType(inp))

        I_CATCHVALUE -> CatchValue(readValue(inp) as InstructionRef, readType(inp))
        I_MAKEWEAKREF -> MakeWeakRef(readValue(inp) as InstructionRef, readValue(inp))
        I_READWEAKREF -> ReadWeakRef(readValue(inp) as InstructionRef, readValue(inp))
        I_CLEARWEAKREF -> ClearWeakRef(readValue(inp))

        I_CLOSURE_CREATE -> ClosureCreate(readValue(inp) as InstructionRef, readValue(inp), readList(inp) { readValue(inp) }, readType(inp) as Type.Function)
        I_CLOSURE_INVOKE -> ClosureInvoke(readNullableValue(inp) as InstructionRef?, readValue(inp), readList(inp) { readValue(inp) }, readType(inp))

        I_CONSTRUCT_VARIANT -> ConstructVariant(readValue(inp) as InstructionRef, readType(inp) as Type.TaggedUnion, readString(inp), readList(inp) { readValue(inp) })
        I_GETTAG -> GetTag(readValue(inp) as InstructionRef, readValue(inp))
        I_GETVARIANTFIELD -> GetVariantField(readValue(inp) as InstructionRef, readValue(inp), readString(inp), inp.readInt())
        I_TAGSWITCH -> TagSwitch(readValue(inp), readList(inp) { readString(inp) to readString(inp) }, readNullableString(inp))

        I_GCALLOC -> GCAlloc(readValue(inp) as InstructionRef, readType(inp), readNullableValue(inp))
        I_GCSAFEPOINT -> GCSafepoint()
        I_GCROOT -> GCRoot(readValue(inp), readNullableValue(inp))
        I_PIN -> Pin(readValue(inp) as InstructionRef, readValue(inp))
        I_UNPIN -> Unpin(readValue(inp))
        I_INTERIOR_PTR -> InteriorPtr(readValue(inp) as InstructionRef, readValue(inp), readValue(inp), readType(inp))
        I_WRITE_BARRIER -> WriteBarrier(readValue(inp), readValue(inp), readValue(inp))
        I_READ_BARRIER -> ReadBarrier(readValue(inp) as InstructionRef, readValue(inp))
        I_MANAGED_CALL -> ManagedCall(readNullableValue(inp) as InstructionRef?, readValue(inp), readList(inp) { readValue(inp) }, readType(inp), ManagedCallDirection.entries[inp.readInt()])

        I_REFRETAIN -> RefRetain(readValue(inp))
        I_REFRELEASE -> RefRelease(readValue(inp))
        I_REFCOUNT -> RefCount(readValue(inp) as InstructionRef, readValue(inp))

        I_COROBEGIN -> CoroBegin(readValue(inp) as InstructionRef, readValue(inp), readValue(inp))
        I_COROEND -> CoroEnd(readValue(inp), inp.readBoolean())
        I_COROSUSPEND -> CoroSuspend(readValue(inp) as InstructionRef, readNullableValue(inp), inp.readBoolean())
        I_CORORESUME -> CoroResume(readValue(inp))
        I_CORODESTROY -> CoroDestroy(readValue(inp))
        I_COROSIZE -> CoroSize(readValue(inp) as InstructionRef)

        I_INTRINSIC -> Intrinsic(readNullableValue(inp) as InstructionRef?, readString(inp), readList(inp) { readValue(inp) }, readType(inp))
        I_INLINEASM -> InlineAsm(readNullableValue(inp) as InstructionRef?, readString(inp), readString(inp), inp.readBoolean(), inp.readBoolean(), AsmDialect.entries[inp.readInt()], readList(inp) { readValue(inp) }, readType(inp))

        I_DEBUGLOC -> DebugLoc(inp.readInt(), inp.readInt(), readString(inp), readNullableString(inp))
        I_DEBUGVALUE -> DebugValue(readString(inp), readValue(inp), readNullableString(inp))
        I_DEBUGDECLARE -> DebugDeclare(readString(inp), readValue(inp), readNullableString(inp))

        I_ASSUME -> Assume(readValue(inp))
        I_EXPECT -> Expect(readValue(inp) as InstructionRef, readValue(inp), readConstant(inp))

        else -> throw IllegalArgumentException("Unknown instruction tag: $tag")
    }

    // Helper structures

    private fun writeFunction(out: DataOutputStream, fn: IrFunction) {
        writeString(out, fn.name)
        writeList(out, fn.params) { writeValue(out, it) }
        writeType(out, fn.returnType)
        out.writeBoolean(fn.isExternal)
        out.writeInt(fn.linkage.ordinal)
        out.writeInt(fn.visibility.ordinal)
        out.writeInt(fn.callingConv.ordinal)
        writeList(out, fn.attributes.toList()) { out.writeInt(it.ordinal) }
        out.writeBoolean(fn.isVarArg)
        writeList(out, fn.blocks) { writeBasicBlock(out, it) }
    }

    private fun readFunction(inp: DataInputStream): IrFunction {
        val name = readString(inp)
        val params = readList(inp) { readValue(inp) as Parameter }
        val retType = readType(inp)
        val isExternal = inp.readBoolean()
        val linkage = Linkage.entries[inp.readInt()]
        val visibility = Visibility.entries[inp.readInt()]
        val cc = CallingConvention.entries[inp.readInt()]
        val attrs = readList(inp) { FnAttribute.entries[inp.readInt()] }.toSet()
        val isVarArg = inp.readBoolean()
        val blocks = readList(inp) { readBasicBlock(inp) }
        return IrFunction(name, params, retType, blocks, isExternal, linkage, visibility, cc, attrs, isVarArg = isVarArg)
    }

    private fun writeBasicBlock(out: DataOutputStream, bb: BasicBlock) {
        writeString(out, bb.label)
        writeList(out, bb.instructions) { writeInstruction(out, it) }
    }

    private fun readBasicBlock(inp: DataInputStream): BasicBlock =
        BasicBlock(readString(inp), readList(inp) { readInstruction(inp) })

    private fun writeGlobal(out: DataOutputStream, g: Global) {
        writeString(out, g.name)
        writeType(out, g.type)
        out.writeBoolean(g.initializer != null)
        if (g.initializer != null) writeConstant(out, g.initializer)
        out.writeBoolean(g.isConstant)
        out.writeInt(g.linkage.ordinal)
        out.writeInt(g.visibility.ordinal)
        writeNullableInt(out, g.align)
    }

    private fun readGlobal(inp: DataInputStream): Global {
        val name = readString(inp)
        val type = readType(inp)
        val init = if (inp.readBoolean()) readConstant(inp) else null
        val isConst = inp.readBoolean()
        val linkage = Linkage.entries[inp.readInt()]
        val vis = Visibility.entries[inp.readInt()]
        val align = readNullableInt(inp)
        return Global(name, type, init, isConst, linkage, vis, align = align)
    }

    private fun writeStructDef(out: DataOutputStream, s: StructDef) {
        writeString(out, s.name)
        writeList(out, s.fields) { writeString(out, it.name); writeType(out, it.type) }
        out.writeBoolean(s.packed)
    }

    private fun readStructDef(inp: DataInputStream): StructDef =
        StructDef(readString(inp), readList(inp) { Param(readString(inp), readType(inp)) }, inp.readBoolean())

    private fun writeTypeAlias(out: DataOutputStream, a: TypeAlias) {
        writeString(out, a.name); writeType(out, a.type)
    }

    private fun readTypeAlias(inp: DataInputStream): TypeAlias = TypeAlias(readString(inp), readType(inp))

    private fun writeClassDef(out: DataOutputStream, cls: ClassDef) {
        writeString(out, cls.name)
        writeNullableString(out, cls.superClass)
        writeList(out, cls.interfaces) { writeString(out, it) }
        writeList(out, cls.fields) { writeFieldDef(out, it) }
        writeList(out, cls.methods) { writeMethodDef(out, it) }
        writeList(out, cls.constructors) { writeMethodDef(out, it) }
        out.writeBoolean(cls.isAbstract)
        out.writeBoolean(cls.isFinal)
        out.writeInt(cls.visibility.ordinal)
    }

    private fun readClassDef(inp: DataInputStream): ClassDef {
        val name = readString(inp)
        val superClass = readNullableString(inp)
        val interfaces = readList(inp) { readString(inp) }
        val fields = readList(inp) { readFieldDef(inp) }
        val methods = readList(inp) { readMethodDef(inp) }
        val ctors = readList(inp) { readMethodDef(inp) }
        val isAbstract = inp.readBoolean()
        val isFinal = inp.readBoolean()
        val vis = ClassVisibility.entries[inp.readInt()]
        return ClassDef(name, superClass, interfaces, fields, methods, ctors, isAbstract = isAbstract, isFinal = isFinal, visibility = vis)
    }

    private fun writeInterfaceDef(out: DataOutputStream, iface: InterfaceDef) {
        writeString(out, iface.name)
        writeList(out, iface.superInterfaces) { writeString(out, it) }
        writeList(out, iface.methods) { writeMethodDef(out, it) }
        out.writeInt(iface.visibility.ordinal)
    }

    private fun readInterfaceDef(inp: DataInputStream): InterfaceDef {
        val name = readString(inp)
        val supers = readList(inp) { readString(inp) }
        val methods = readList(inp) { readMethodDef(inp) }
        val vis = ClassVisibility.entries[inp.readInt()]
        return InterfaceDef(name, supers, methods, visibility = vis)
    }

    private fun writeEnumDef(out: DataOutputStream, e: EnumDef) {
        writeString(out, e.name)
        writeList(out, e.variants) {
            writeString(out, it.name)
            out.writeInt(it.ordinal)
            writeList(out, it.fields) { f -> writeString(out, f.name); writeType(out, f.type) }
        }
        out.writeInt(e.visibility.ordinal)
    }

    private fun readEnumDef(inp: DataInputStream): EnumDef {
        val name = readString(inp)
        val variants = readList(inp) {
            EnumVariant(readString(inp), inp.readInt(), readList(inp) { Param(readString(inp), readType(inp)) })
        }
        val vis = ClassVisibility.entries[inp.readInt()]
        return EnumDef(name, variants, visibility = vis)
    }

    private fun writeFieldDef(out: DataOutputStream, f: FieldDef) {
        writeString(out, f.name); writeType(out, f.type); out.writeInt(f.visibility.ordinal); out.writeBoolean(f.isFinal)
    }

    private fun readFieldDef(inp: DataInputStream): FieldDef =
        FieldDef(readString(inp), readType(inp), MemberVisibility.entries[inp.readInt()], inp.readBoolean())

    private fun writeMethodDef(out: DataOutputStream, m: MethodDef) {
        writeString(out, m.name)
        writeList(out, m.params) { writeString(out, it.name); writeType(out, it.type) }
        writeType(out, m.returnType)
        out.writeInt(m.visibility.ordinal)
        out.writeBoolean(m.isAbstract); out.writeBoolean(m.isFinal); out.writeBoolean(m.isStatic)
    }

    private fun readMethodDef(inp: DataInputStream): MethodDef {
        val name = readString(inp)
        val params = readList(inp) { Param(readString(inp), readType(inp)) }
        val ret = readType(inp)
        val vis = MemberVisibility.entries[inp.readInt()]
        val isAbstract = inp.readBoolean(); val isFinal = inp.readBoolean(); val isStatic = inp.readBoolean()
        return MethodDef(name, params, ret, visibility = vis, isAbstract = isAbstract, isFinal = isFinal, isStatic = isStatic)
    }

    private fun writeMetadata(out: DataOutputStream, md: MetadataValue) {
        when (md) {
            is MetadataValue.StringMD -> { out.writeByte(0); writeString(out, md.value) }
            is MetadataValue.IntMD -> { out.writeByte(1); out.writeLong(md.value) }
            is MetadataValue.NodeMD -> { out.writeByte(2); writeList(out, md.values) { writeMetadata(out, it) } }
            is MetadataValue.RefMD -> { out.writeByte(3); writeString(out, md.name) }
        }
    }

    private fun readMetadata(inp: DataInputStream): MetadataValue = when (inp.readByte().toInt()) {
        0 -> MetadataValue.StringMD(readString(inp))
        1 -> MetadataValue.IntMD(inp.readLong())
        2 -> MetadataValue.NodeMD(readList(inp) { readMetadata(inp) })
        3 -> MetadataValue.RefMD(readString(inp))
        else -> throw IllegalArgumentException("Unknown metadata tag")
    }

    private fun writeTaggedVariant(out: DataOutputStream, v: TaggedVariant) {
        writeString(out, v.name); out.writeLong(v.tag); writeList(out, v.fields) { writeType(out, it) }
    }

    private fun readTaggedVariant(inp: DataInputStream): TaggedVariant =
        TaggedVariant(readString(inp), inp.readLong(), readList(inp) { readType(inp) })

    private fun writeLandingPadClause(out: DataOutputStream, c: LandingPadClause) {
        when (c) {
            is LandingPadClause.Catch -> { out.writeByte(0); writeValue(out, c.type) }
            is LandingPadClause.Filter -> { out.writeByte(1); writeList(out, c.types) { writeValue(out, it) } }
        }
    }

    private fun readLandingPadClause(inp: DataInputStream): LandingPadClause = when (inp.readByte().toInt()) {
        0 -> LandingPadClause.Catch(readValue(inp))
        1 -> LandingPadClause.Filter(readList(inp) { readValue(inp) })
        else -> throw IllegalArgumentException("Unknown landing pad clause tag")
    }

    private fun writeCatchHandler(out: DataOutputStream, c: CatchHandler) {
        writeType(out, c.exceptionType); writeString(out, c.handlerBlock)
    }

    private fun readCatchHandler(inp: DataInputStream): CatchHandler = CatchHandler(readType(inp), readString(inp))

    private fun writeBootstrapMethod(out: DataOutputStream, bm: BootstrapMethod) {
        writeString(out, bm.className); writeString(out, bm.methodName); writeType(out, bm.methodType)
        writeList(out, bm.staticArgs) { writeConstant(out, it) }
    }

    private fun readBootstrapMethod(inp: DataInputStream): BootstrapMethod =
        BootstrapMethod(readString(inp), readString(inp), readType(inp) as Type.Function, readList(inp) { readConstant(inp) })

    private fun writeFastMath(out: DataOutputStream, fm: FastMathFlags) {
        var bits = 0
        if (fm.noNaNs) bits = bits or 1
        if (fm.noInfs) bits = bits or 2
        if (fm.noSignedZeros) bits = bits or 4
        if (fm.allowReciprocal) bits = bits or 8
        if (fm.allowContract) bits = bits or 16
        if (fm.approxFunc) bits = bits or 32
        if (fm.reassoc) bits = bits or 64
        out.writeByte(bits)
    }

    private fun readFastMath(inp: DataInputStream): FastMathFlags {
        val bits = inp.readByte().toInt()
        return FastMathFlags(
            noNaNs = bits and 1 != 0,
            noInfs = bits and 2 != 0,
            noSignedZeros = bits and 4 != 0,
            allowReciprocal = bits and 8 != 0,
            allowContract = bits and 16 != 0,
            approxFunc = bits and 32 != 0,
            reassoc = bits and 64 != 0,
        )
    }

    // Primitive helpers

    private fun writeString(out: DataOutputStream, s: String) { out.writeUTF(s) }
    private fun readString(inp: DataInputStream): String = inp.readUTF()
    private fun writeNullableString(out: DataOutputStream, s: String?) { out.writeBoolean(s != null); if (s != null) writeString(out, s) }
    private fun readNullableString(inp: DataInputStream): String? = if (inp.readBoolean()) readString(inp) else null
    private fun writeNullableInt(out: DataOutputStream, v: Int?) { out.writeBoolean(v != null); if (v != null) out.writeInt(v) }
    private fun readNullableInt(inp: DataInputStream): Int? = if (inp.readBoolean()) inp.readInt() else null
    private fun writeNullableValue(out: DataOutputStream, v: Value?) { out.writeBoolean(v != null); if (v != null) writeValue(out, v) }
    private fun readNullableValue(inp: DataInputStream): Value? = if (inp.readBoolean()) readValue(inp) else null
    private fun writeNullableOrdering(out: DataOutputStream, o: AtomicOrdering?) { out.writeBoolean(o != null); if (o != null) out.writeInt(o.ordinal) }
    private fun readNullableOrdering(inp: DataInputStream): AtomicOrdering? = if (inp.readBoolean()) AtomicOrdering.entries[inp.readInt()] else null

    private fun <T> writeList(out: DataOutputStream, list: List<T>, writer: (T) -> Unit) {
        out.writeInt(list.size)
        for (item in list) writer(item)
    }

    private fun <T> readList(inp: DataInputStream, reader: () -> T): List<T> {
        val size = inp.readInt()
        return List(size) { reader() }
    }

    private fun instructionTag(inst: Instruction): Int = when (inst) {
        is Add -> I_ADD; is Sub -> I_SUB; is Mul -> I_MUL
        is UDiv -> I_UDIV; is SDiv -> I_SDIV
        is URem -> I_UREM; is SRem -> I_SREM; is Neg -> I_NEG
        is SAddOverflow -> I_SADD_OVF; is UAddOverflow -> I_UADD_OVF
        is SSubOverflow -> I_SSUB_OVF; is USubOverflow -> I_USUB_OVF
        is SMulOverflow -> I_SMUL_OVF; is UMulOverflow -> I_UMUL_OVF
        is SAddSat -> I_SADD_SAT; is UAddSat -> I_UADD_SAT
        is SSubSat -> I_SSUB_SAT; is USubSat -> I_USUB_SAT
        is SMin -> I_SMIN; is SMax -> I_SMAX
        is UMin -> I_UMIN; is UMax -> I_UMAX; is Abs -> I_ABS
        is FAdd -> I_FADD; is FSub -> I_FSUB; is FMul -> I_FMUL
        is FDiv -> I_FDIV; is FRem -> I_FREM; is FNeg -> I_FNEG
        is FAbs -> I_FABS; is FMA -> I_FMA
        is FMin -> I_FMIN; is FMax -> I_FMAX
        is Sqrt -> I_SQRT; is Ceil -> I_CEIL; is Floor -> I_FLOOR
        is Round -> I_ROUND; is Trunc -> I_FTRUNC; is CopySign -> I_COPYSIGN
        is And -> I_AND; is Or -> I_OR; is Xor -> I_XOR; is Not -> I_NOT
        is Shl -> I_SHL; is LShr -> I_LSHR; is AShr -> I_ASHR
        is RotateLeft -> I_ROTL; is RotateRight -> I_ROTR
        is Rotl -> I_ROTL2; is Rotr -> I_ROTR2
        is Ctlz -> I_CTLZ; is Cttz -> I_CTTZ; is Ctpop -> I_CTPOP
        is BSwap -> I_BSWAP; is BitReverse -> I_BITREVERSE
        is ICmp -> I_ICMP; is FCmp -> I_FCMP
        is Alloca -> I_ALLOCA; is Load -> I_LOAD; is Store -> I_STORE
        is GetElementPtr -> I_GEP; is Fence -> I_FENCE
        is CmpXchg -> I_CMPXCHG; is AtomicRMW -> I_ATOMICRMW
        is MemCpy -> I_MEMCPY; is MemSet -> I_MEMSET; is MemMove -> I_MEMMOVE
        is Prefetch -> I_PREFETCH
        is StackSave -> I_STACKSAVE; is StackRestore -> I_STACKRESTORE
        is LifetimeStart -> I_LIFETIME_START; is LifetimeEnd -> I_LIFETIME_END
        is IntTrunc -> I_INTTRUNC; is ZExt -> I_ZEXT; is SExt -> I_SEXT
        is FPTrunc -> I_FPTRUNC; is FPExt -> I_FPEXT
        is FPToUI -> I_FPTOUI; is FPToSI -> I_FPTOSI
        is UIToFP -> I_UITOFP; is SIToFP -> I_SITOFP
        is PtrToInt -> I_PTRTOINT; is IntToPtr -> I_INTTOPTR
        is BitCast -> I_BITCAST; is AddrSpaceCast -> I_ADDRSPACECAST
        is Ret -> I_RET; is Br -> I_BR; is CondBr -> I_CONDBR
        is Switch -> I_SWITCH; is IndirectBr -> I_INDIRECTBR
        is Unreachable -> I_UNREACHABLE; is Trap -> I_TRAP; is DebugTrap -> I_DEBUGTRAP
        is Call -> I_CALL; is Invoke -> I_INVOKE; is CallBr -> I_CALLBR
        is VAStart -> I_VASTART; is VAEnd -> I_VAEND
        is VACopy -> I_VACOPY; is VAArg -> I_VAARG
        is LandingPad -> I_LANDINGPAD; is Resume -> I_RESUME
        is CatchSwitch -> I_CATCHSWITCH; is CatchPad -> I_CATCHPAD
        is CleanupPad -> I_CLEANUPPAD; is CatchRet -> I_CATCHRET; is CleanupRet -> I_CLEANUPRET
        is Phi -> I_PHI; is Select -> I_SELECT; is Freeze -> I_FREEZE
        is ExtractElement -> I_EXTRACTELEMENT; is InsertElement -> I_INSERTELEMENT
        is ShuffleVector -> I_SHUFFLEVECTOR; is Splat -> I_SPLAT
        is VectorReduce -> I_VECTORREDUCE
        is ExtractValue -> I_EXTRACTVALUE; is InsertValue -> I_INSERTVALUE
        is NewObject -> I_NEWOBJECT; is NewArray -> I_NEWARRAY; is NewMultiArray -> I_NEWMULTIARRAY
        is GetField -> I_GETFIELD; is PutField -> I_PUTFIELD
        is GetStatic -> I_GETSTATIC; is PutStatic -> I_PUTSTATIC
        is VirtualCall -> I_VIRTUALCALL; is InterfaceCall -> I_INTERFACECALL
        is SpecialCall -> I_SPECIALCALL; is StaticCall -> I_STATICCALL
        is DynamicCall -> I_DYNAMICCALL; is ConstructorCall -> I_CONSTRUCTORCALL
        is InstanceOf -> I_INSTANCEOF; is CheckCast -> I_CHECKCAST; is TypeId -> I_TYPEID
        is ArrayGet -> I_ARRAYGET; is ArraySet -> I_ARRAYSET; is ArrayLength -> I_ARRAYLENGTH
        is MonitorEnter -> I_MONITORENTER; is MonitorExit -> I_MONITOREXIT
        is Throw -> I_THROW; is TryCatchRegion -> I_TRYCATCH
        is Box -> I_BOX; is Unbox -> I_UNBOX
        is CatchValue -> I_CATCHVALUE; is MakeWeakRef -> I_MAKEWEAKREF
        is ReadWeakRef -> I_READWEAKREF; is ClearWeakRef -> I_CLEARWEAKREF
        is ClosureCreate -> I_CLOSURE_CREATE; is ClosureInvoke -> I_CLOSURE_INVOKE
        is ConstructVariant -> I_CONSTRUCT_VARIANT; is GetTag -> I_GETTAG
        is GetVariantField -> I_GETVARIANTFIELD; is TagSwitch -> I_TAGSWITCH
        is GCAlloc -> I_GCALLOC; is GCSafepoint -> I_GCSAFEPOINT; is GCRoot -> I_GCROOT
        is Pin -> I_PIN; is Unpin -> I_UNPIN; is InteriorPtr -> I_INTERIOR_PTR
        is WriteBarrier -> I_WRITE_BARRIER; is ReadBarrier -> I_READ_BARRIER; is ManagedCall -> I_MANAGED_CALL
        is RefRetain -> I_REFRETAIN; is RefRelease -> I_REFRELEASE; is RefCount -> I_REFCOUNT
        is CoroBegin -> I_COROBEGIN; is CoroEnd -> I_COROEND
        is CoroSuspend -> I_COROSUSPEND; is CoroResume -> I_CORORESUME
        is CoroDestroy -> I_CORODESTROY; is CoroSize -> I_COROSIZE
        is Intrinsic -> I_INTRINSIC; is InlineAsm -> I_INLINEASM
        is DebugLoc -> I_DEBUGLOC; is DebugValue -> I_DEBUGVALUE; is DebugDeclare -> I_DEBUGDECLARE
        is Assume -> I_ASSUME; is Expect -> I_EXPECT
    }

    companion object {
        private const val MAGIC = 0x4B47454E // "KGEN"
        private const val VERSION = 2

        // Type tags
        private const val T_I1 = 0; private const val T_I8 = 1; private const val T_I16 = 2; private const val T_I32 = 3
        private const val T_I64 = 4; private const val T_I128 = 5; private const val T_INTN = 6
        private const val T_F16 = 7; private const val T_BF16 = 8; private const val T_F32 = 9; private const val T_F64 = 10
        private const val T_F80 = 11; private const val T_F128 = 12
        private const val T_VOID = 13; private const val T_LABEL = 14; private const val T_METADATA = 15; private const val T_TOKEN = 16
        private const val T_POINTER = 17; private const val T_OPAQUE_PTR = 18
        private const val T_REFERENCE = 19; private const val T_WEAK_REF = 20
        private const val T_ARRAY = 21; private const val T_VECTOR = 22
        private const val T_STRUCT = 23; private const val T_OPAQUE_STRUCT = 24
        private const val T_UNION = 25; private const val T_TAGGED_UNION = 26
        private const val T_FUNCTION = 27
        private const val T_CLASS_REF = 28; private const val T_INTERFACE_REF = 29
        private const val T_TYPE_PARAM = 30; private const val T_PARAMETERIZED = 31
        private const val T_NULLABLE = 32; private const val T_PLATFORM = 33
        private const val T_INTERIOR_REF = 34; private const val T_PINNED_REF = 35

        // Value tags
        private const val V_PARAM = 0; private const val V_INST_REF = 1; private const val V_GLOBAL_REF = 2
        private const val V_FUNC_REF = 3; private const val V_BLOCK_REF = 4; private const val V_CONSTANT = 5

        // Constant tags
        private const val C_INT = 0; private const val C_FLOAT = 1; private const val C_NULL_PTR = 2; private const val C_NULL_REF = 3
        private const val C_UNDEF = 4; private const val C_POISON = 5; private const val C_ZERO_INIT = 6
        private const val C_ARRAY = 7; private const val C_VECTOR = 8; private const val C_STRUCT = 9; private const val C_STRING = 10
        private const val C_GEP = 11; private const val C_BITCAST = 12; private const val C_INTTOPTR = 13; private const val C_PTRTOINT = 14

        // Instruction tags
        private const val I_ADD = 0; private const val I_SUB = 1; private const val I_MUL = 2
        private const val I_UDIV = 3; private const val I_SDIV = 4; private const val I_UREM = 5; private const val I_SREM = 6; private const val I_NEG = 7
        private const val I_SADD_OVF = 8; private const val I_UADD_OVF = 9; private const val I_SSUB_OVF = 10; private const val I_USUB_OVF = 11
        private const val I_SMUL_OVF = 12; private const val I_UMUL_OVF = 13
        private const val I_SADD_SAT = 14; private const val I_UADD_SAT = 15; private const val I_SSUB_SAT = 16; private const val I_USUB_SAT = 17
        private const val I_SMIN = 18; private const val I_SMAX = 19; private const val I_UMIN = 20; private const val I_UMAX = 21; private const val I_ABS = 22
        private const val I_FADD = 23; private const val I_FSUB = 24; private const val I_FMUL = 25; private const val I_FDIV = 26; private const val I_FREM = 27
        private const val I_FNEG = 28; private const val I_FABS = 29; private const val I_FMA = 30; private const val I_FMIN = 31; private const val I_FMAX = 32
        private const val I_SQRT = 33; private const val I_CEIL = 34; private const val I_FLOOR = 35; private const val I_ROUND = 36
        private const val I_FTRUNC = 37; private const val I_COPYSIGN = 38
        private const val I_AND = 39; private const val I_OR = 40; private const val I_XOR = 41; private const val I_NOT = 42
        private const val I_SHL = 43; private const val I_LSHR = 44; private const val I_ASHR = 45
        private const val I_ROTL = 46; private const val I_ROTR = 47
        private const val I_CTLZ = 48; private const val I_CTTZ = 49; private const val I_CTPOP = 50
        private const val I_BSWAP = 51; private const val I_BITREVERSE = 52
        private const val I_ICMP = 53; private const val I_FCMP = 54
        private const val I_ALLOCA = 55; private const val I_LOAD = 56; private const val I_STORE = 57; private const val I_GEP = 58
        private const val I_FENCE = 59; private const val I_CMPXCHG = 60; private const val I_ATOMICRMW = 61
        private const val I_MEMCPY = 62; private const val I_MEMSET = 63; private const val I_MEMMOVE = 64; private const val I_PREFETCH = 65
        private const val I_STACKSAVE = 66; private const val I_STACKRESTORE = 67
        private const val I_LIFETIME_START = 68; private const val I_LIFETIME_END = 69
        private const val I_INTTRUNC = 70; private const val I_ZEXT = 71; private const val I_SEXT = 72
        private const val I_FPTRUNC = 73; private const val I_FPEXT = 74
        private const val I_FPTOUI = 75; private const val I_FPTOSI = 76; private const val I_UITOFP = 77; private const val I_SITOFP = 78
        private const val I_PTRTOINT = 79; private const val I_INTTOPTR = 80; private const val I_BITCAST = 81; private const val I_ADDRSPACECAST = 82
        private const val I_RET = 83; private const val I_BR = 84; private const val I_CONDBR = 85
        private const val I_SWITCH = 86; private const val I_INDIRECTBR = 87
        private const val I_UNREACHABLE = 88; private const val I_TRAP = 89; private const val I_DEBUGTRAP = 90
        private const val I_CALL = 91; private const val I_INVOKE = 92; private const val I_CALLBR = 93
        private const val I_VASTART = 94; private const val I_VAEND = 95; private const val I_VACOPY = 96; private const val I_VAARG = 97
        private const val I_LANDINGPAD = 98; private const val I_RESUME = 99
        private const val I_CATCHSWITCH = 100; private const val I_CATCHPAD = 101; private const val I_CLEANUPPAD = 102
        private const val I_CATCHRET = 103; private const val I_CLEANUPRET = 104
        private const val I_PHI = 105; private const val I_SELECT = 106; private const val I_FREEZE = 107
        private const val I_EXTRACTELEMENT = 108; private const val I_INSERTELEMENT = 109
        private const val I_SHUFFLEVECTOR = 110; private const val I_SPLAT = 111; private const val I_VECTORREDUCE = 112
        private const val I_EXTRACTVALUE = 113; private const val I_INSERTVALUE = 114
        private const val I_NEWOBJECT = 115; private const val I_NEWARRAY = 116; private const val I_NEWMULTIARRAY = 117
        private const val I_GETFIELD = 118; private const val I_PUTFIELD = 119; private const val I_GETSTATIC = 120; private const val I_PUTSTATIC = 121
        private const val I_VIRTUALCALL = 122; private const val I_INTERFACECALL = 123
        private const val I_SPECIALCALL = 124; private const val I_STATICCALL = 125
        private const val I_DYNAMICCALL = 126; private const val I_CONSTRUCTORCALL = 127
        private const val I_INSTANCEOF = 128; private const val I_CHECKCAST = 129; private const val I_TYPEID = 130
        private const val I_ARRAYGET = 131; private const val I_ARRAYSET = 132; private const val I_ARRAYLENGTH = 133
        private const val I_MONITORENTER = 134; private const val I_MONITOREXIT = 135
        private const val I_THROW = 136; private const val I_TRYCATCH = 137
        private const val I_BOX = 138; private const val I_UNBOX = 139
        private const val I_CLOSURE_CREATE = 140; private const val I_CLOSURE_INVOKE = 141
        private const val I_CONSTRUCT_VARIANT = 142; private const val I_GETTAG = 143
        private const val I_GETVARIANTFIELD = 144; private const val I_TAGSWITCH = 145
        private const val I_GCALLOC = 146; private const val I_GCSAFEPOINT = 147; private const val I_GCROOT = 148
        private const val I_REFRETAIN = 149; private const val I_REFRELEASE = 150; private const val I_REFCOUNT = 151
        private const val I_COROBEGIN = 152; private const val I_COROEND = 153; private const val I_COROSUSPEND = 154
        private const val I_CORORESUME = 155; private const val I_CORODESTROY = 156; private const val I_COROSIZE = 157
        private const val I_INTRINSIC = 158; private const val I_INLINEASM = 159
        private const val I_DEBUGLOC = 160; private const val I_DEBUGVALUE = 161; private const val I_DEBUGDECLARE = 162
        private const val I_ASSUME = 163; private const val I_EXPECT = 164
        private const val I_PIN = 165; private const val I_UNPIN = 166; private const val I_INTERIOR_PTR = 167
        private const val I_WRITE_BARRIER = 168; private const val I_READ_BARRIER = 169; private const val I_MANAGED_CALL = 170
        private const val I_ROTL2 = 171; private const val I_ROTR2 = 172
        private const val I_CATCHVALUE = 173; private const val I_MAKEWEAKREF = 174
        private const val I_READWEAKREF = 175; private const val I_CLEARWEAKREF = 176
    }
}
