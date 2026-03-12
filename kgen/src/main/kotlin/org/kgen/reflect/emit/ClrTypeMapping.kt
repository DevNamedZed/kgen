package org.kgen.reflect.emit

import org.kgen.reflect.FieldFlag
import org.kgen.reflect.MethodFlag
import org.kgen.reflect.Signature
import org.kgen.reflect.TypeRef
import org.kgen.target.clr.CilClassBuilder
import org.kgen.target.clr.CilFieldFlags
import org.kgen.target.clr.CilMethodFlags
import org.kgen.target.clr.CilSigType

internal fun mapTypeRef(ref: TypeRef): CilSigType = when {
    ref === TypeRef.VOID -> CilSigType.VOID
    ref === TypeRef.BOOL -> CilSigType.BOOLEAN
    ref === TypeRef.I8 -> CilSigType.I1
    ref === TypeRef.I16 -> CilSigType.I2
    ref === TypeRef.I32 -> CilSigType.I4
    ref === TypeRef.I64 -> CilSigType.I8
    ref === TypeRef.U8 -> CilSigType.U1
    ref === TypeRef.U16 -> CilSigType.U2
    ref === TypeRef.U32 -> CilSigType.U4
    ref === TypeRef.U64 -> CilSigType.U8
    ref === TypeRef.F32 -> CilSigType.R4
    ref === TypeRef.F64 -> CilSigType.R8
    ref === TypeRef.POINTER -> CilSigType.I
    ref.fullName() == "string" || ref.fullName() == "System.String" -> CilSigType.STRING
    ref.fullName() == "object" || ref.fullName() == "System.Object" -> CilSigType.OBJECT
    else -> error("Unsupported type for CIL signature: $ref")
}

internal fun mapSignature(sig: Signature, instance: Boolean = false): ByteArray {
    val ret = mapTypeRef(sig.returnType())
    val params = sig.parameterTypes().map { mapTypeRef(it) }.toTypedArray()
    return if (instance) CilClassBuilder.instanceSig(ret, *params)
    else CilClassBuilder.sig(ret, *params)
}

internal fun mapMethodFlags(flags: Set<MethodFlag>): Int {
    var result = CilMethodFlags.HIDE_BY_SIG
    if (MethodFlag.PUBLIC in flags) result = result or CilMethodFlags.PUBLIC
    if (MethodFlag.PRIVATE in flags) result = result or CilMethodFlags.PRIVATE
    if (MethodFlag.PROTECTED in flags) result = result or CilMethodFlags.FAMILY
    if (MethodFlag.STATIC in flags) result = result or CilMethodFlags.STATIC
    if (MethodFlag.VIRTUAL in flags) result = result or CilMethodFlags.VIRTUAL
    if (MethodFlag.ABSTRACT in flags) result = result or CilMethodFlags.ABSTRACT
    if (MethodFlag.FINAL in flags) result = result or CilMethodFlags.FINAL
    if (MethodFlag.CONSTRUCTOR in flags) {
        result = result or CilMethodFlags.SPECIAL_NAME or CilMethodFlags.RT_SPECIAL_NAME
    }
    return result
}

internal fun mapFieldFlags(flags: Set<FieldFlag>): Int {
    var result = 0
    if (FieldFlag.PUBLIC in flags) result = result or CilFieldFlags.PUBLIC
    if (FieldFlag.PRIVATE in flags) result = result or CilFieldFlags.PRIVATE
    if (FieldFlag.PROTECTED in flags) result = result or CilFieldFlags.FAMILY
    if (FieldFlag.STATIC in flags) result = result or CilFieldFlags.STATIC
    if (FieldFlag.READONLY in flags) result = result or CilFieldFlags.INIT_ONLY
    if (FieldFlag.CONST in flags) result = result or CilFieldFlags.LITERAL
    return result
}
