package org.kgen.reflect

import org.kgen.binary.pe.clr.ClrMetadata
import org.kgen.binary.pe.clr.ClrTypeDef
import org.kgen.binary.pe.clr.ClrMethodDef
import org.kgen.binary.pe.clr.ClrField

/**
 * Maps CLR metadata tables to reflect [TypeInfo] instances.
 */
internal object ClrTypeMapper {

    // CLR TypeAttributes flags
    private const val TD_VISIBILITY_MASK = 0x00000007
    private const val TD_PUBLIC = 0x00000001
    private const val TD_NOT_PUBLIC = 0x00000000
    private const val TD_NESTED_PUBLIC = 0x00000002
    private const val TD_INTERFACE = 0x00000020
    private const val TD_ABSTRACT = 0x00000080
    private const val TD_SEALED = 0x00000100

    // CLR MethodAttributes flags
    private const val MD_MEMBER_ACCESS_MASK = 0x0007
    private const val MD_PUBLIC = 0x0006
    private const val MD_PRIVATE = 0x0001
    private const val MD_FAMILY = 0x0004
    private const val MD_STATIC = 0x0010
    private const val MD_FINAL = 0x0020
    private const val MD_VIRTUAL = 0x0040
    private const val MD_ABSTRACT = 0x0400
    private const val MD_PINVOKE_IMPL = 0x2000
    private const val MD_RT_SPECIAL_NAME = 0x1000
    private const val MD_SPECIAL_NAME = 0x0800

    // CLR FieldAttributes flags
    private const val FD_FIELD_ACCESS_MASK = 0x0007
    private const val FD_PUBLIC = 0x0006
    private const val FD_PRIVATE = 0x0001
    private const val FD_FAMILY = 0x0004
    private const val FD_STATIC = 0x0010
    private const val FD_INIT_ONLY = 0x0020
    private const val FD_LITERAL = 0x0040

    fun map(clr: ClrMetadata, module: Module?): List<TypeInfo> {
        val typeDefs = clr.tables.typeDefs
        val methodDefs = clr.tables.methodDefs
        val fields = clr.tables.fields
        val result = mutableListOf<TypeInfo>()

        for ((typeIdx, td) in typeDefs.withIndex()) {
            val typeName = clr.strings.get(td.name)
            val typeNs = clr.strings.get(td.namespace)

            // Skip the <Module> pseudo-type
            if (typeName == "<Module>") continue

            val fullName = if (typeNs.isNotEmpty()) "$typeNs.$typeName" else typeName
            val builder = TypeInfo.builder(fullName)
                .module(module)
                .kind(typeKind(td))

            // Flags
            val vis = td.flags and TD_VISIBILITY_MASK
            if (vis == TD_PUBLIC || vis == TD_NESTED_PUBLIC) builder.addFlag(TypeFlag.PUBLIC)
            if (td.flags and TD_ABSTRACT != 0 && td.flags and TD_INTERFACE == 0) builder.addFlag(TypeFlag.ABSTRACT)
            if (td.flags and TD_SEALED != 0) builder.addFlag(TypeFlag.SEALED)

            // Methods for this type
            val methodStart = td.methodList - 1
            val methodEnd = if (typeIdx + 1 < typeDefs.size)
                typeDefs[typeIdx + 1].methodList - 1
            else methodDefs.size

            for (mi in methodStart until methodEnd) {
                if (mi < 0 || mi >= methodDefs.size) continue
                val md = methodDefs[mi]
                val methodName = clr.strings.get(md.name)
                val mflags = methodFlags(md)
                val isConstructor = methodName == ".ctor" || methodName == ".cctor"

                val methodInfo = MethodInfo(
                    name = methodName,
                    flags = mflags + if (isConstructor) setOf(MethodFlag.CONSTRUCTOR) else emptySet(),
                )

                if (isConstructor) builder.addConstructor(methodInfo)
                else builder.addMethod(methodInfo)
            }

            // Fields for this type
            val fieldStart = td.fieldList - 1
            val fieldEnd = if (typeIdx + 1 < typeDefs.size)
                typeDefs[typeIdx + 1].fieldList - 1
            else fields.size

            for (fi in fieldStart until fieldEnd) {
                if (fi < 0 || fi >= fields.size) continue
                val fd = fields[fi]
                val fieldName = clr.strings.get(fd.name)
                val fflags = fieldFlags(fd)

                builder.addField(FieldInfo(fieldName, TypeRef.of("?"), flags = fflags))
            }

            result.add(builder.build())
        }

        return result
    }

    private fun typeKind(td: ClrTypeDef): TypeKind {
        if (td.flags and TD_INTERFACE != 0) return TypeKind.INTERFACE
        return TypeKind.CLASS
    }

    private fun methodFlags(md: ClrMethodDef): Set<MethodFlag> = buildSet {
        val access = md.flags and MD_MEMBER_ACCESS_MASK
        if (access == MD_PUBLIC) add(MethodFlag.PUBLIC)
        if (access == MD_PRIVATE) add(MethodFlag.PRIVATE)
        if (access == MD_FAMILY) add(MethodFlag.PROTECTED)
        if (md.flags and MD_STATIC != 0) add(MethodFlag.STATIC)
        if (md.flags and MD_FINAL != 0) add(MethodFlag.FINAL)
        if (md.flags and MD_VIRTUAL != 0) add(MethodFlag.VIRTUAL)
        if (md.flags and MD_ABSTRACT != 0) add(MethodFlag.ABSTRACT)
        if (md.flags and MD_PINVOKE_IMPL != 0) add(MethodFlag.NATIVE)
    }

    private fun fieldFlags(fd: ClrField): Set<FieldFlag> = buildSet {
        val access = fd.flags and FD_FIELD_ACCESS_MASK
        if (access == FD_PUBLIC) add(FieldFlag.PUBLIC)
        if (access == FD_PRIVATE) add(FieldFlag.PRIVATE)
        if (access == FD_FAMILY) add(FieldFlag.PROTECTED)
        if (fd.flags and FD_STATIC != 0) add(FieldFlag.STATIC)
        if (fd.flags and FD_INIT_ONLY != 0) add(FieldFlag.READONLY)
        if (fd.flags and FD_LITERAL != 0) add(FieldFlag.CONST)
    }
}
