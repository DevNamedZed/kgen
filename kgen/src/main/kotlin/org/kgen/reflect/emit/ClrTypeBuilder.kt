package org.kgen.reflect.emit

import org.kgen.reflect.FieldFlag
import org.kgen.reflect.MethodFlag
import org.kgen.reflect.Signature
import org.kgen.reflect.TypeFlag
import org.kgen.reflect.TypeRef
import org.kgen.target.clr.CilClassBuilder
import org.kgen.target.clr.asm.CilToken

/**
 * Defines a type within a [ClrModuleBuilder].
 */
class ClrTypeBuilder internal constructor(
    private val module: ClrModuleBuilder,
    private val fullName: String,
    private val flags: Set<TypeFlag>,
) {
    private val methods = mutableListOf<ClrMethodBuilder>()
    private val fields = mutableListOf<FieldDefinition>()
    private val typeRefs = mutableListOf<TypeRefDef>()
    private val memberRefs = mutableListOf<MemberRefDef>()

    fun defineMethod(name: String, signature: Signature, vararg flags: MethodFlag): ClrMethodBuilder {
        val mb = ClrMethodBuilder(name, signature, flags.toSet(), methods.size)
        methods.add(mb)
        return mb
    }

    fun defineField(name: String, type: TypeRef, vararg flags: FieldFlag): CilToken {
        fields.add(FieldDefinition(name, type, flags.toSet()))
        return CilToken.field(fields.size)
    }

    internal fun methodIndex(method: ClrMethodBuilder): Int = methods.indexOf(method)

    internal fun addTypeRef(assemblyRefIndex: Int, name: String, namespace: String): Int {
        typeRefs.add(TypeRefDef(assemblyRefIndex, name, namespace))
        return typeRefs.size + 1
    }

    internal fun addMemberRef(typeRefIndex: Int, name: String, signature: Signature, instance: Boolean): CilToken {
        memberRefs.add(MemberRefDef(typeRefIndex, name, signature, instance))
        return CilToken.memberRef(memberRefs.size)
    }

    internal fun toCilClassBuilder(assemblyName: String): CilClassBuilder {
        val builder = CilClassBuilder(assemblyName, fullName)

        for (tr in typeRefs) {
            builder.addTypeRef(tr.assemblyRefIndex, tr.name, tr.namespace)
        }
        for (mr in memberRefs) {
            builder.addMemberRef(mr.typeRefIndex, mr.name, mapSignature(mr.signature, mr.instance))
        }
        for (field in fields) {
            builder.field(field.name, mapTypeRef(field.type), mapFieldFlags(field.flags))
        }
        for (method in methods) {
            val sig = mapSignature(method.signature)
            val cilFlags = mapMethodFlags(method.flags)
            val code = method.il().toByteArray()
            builder.method(method.name, sig, cilFlags, code)
        }

        return builder
    }

    private data class FieldDefinition(val name: String, val type: TypeRef, val flags: Set<FieldFlag>)
    private data class TypeRefDef(val assemblyRefIndex: Int, val name: String, val namespace: String)
    private data class MemberRefDef(val typeRefIndex: Int, val name: String, val signature: Signature, val instance: Boolean)
}
