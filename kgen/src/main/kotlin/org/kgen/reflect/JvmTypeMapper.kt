package org.kgen.reflect

import org.kgen.binary.jvm.AccessFlags
import org.kgen.binary.jvm.ClassFile

/**
 * Maps a JVM [ClassFile] to reflect [TypeInfo] with methods, fields, and constructors.
 */
internal object JvmTypeMapper {

    fun map(cf: ClassFile, module: Module?): List<TypeInfo> {
        val builder = TypeInfo.builder(cf.thisClassName.replace('/', '.'))
            .module(module)
            .kind(classKind(cf.accessFlags))

        // Flags
        if (cf.accessFlags and AccessFlags.PUBLIC != 0) builder.addFlag(TypeFlag.PUBLIC)
        if (cf.accessFlags and AccessFlags.ABSTRACT != 0) builder.addFlag(TypeFlag.ABSTRACT)
        if (cf.accessFlags and AccessFlags.FINAL != 0) builder.addFlag(TypeFlag.SEALED)

        // Methods and constructors
        for (m in cf.methods) {
            val name = cf.string(m.nameIndex)
            val descriptor = cf.string(m.descriptorIndex)
            val flags = methodFlags(m.accessFlags)
            val isConstructor = name == "<init>" || name == "<clinit>"

            val (returnType, paramTypes) = parseMethodDescriptor(descriptor)

            val params = paramTypes.mapIndexed { i, type ->
                ParameterInfo(null, type, i)
            }

            val methodInfo = MethodInfo(
                name = name,
                returnType = returnType,
                params = params,
                flags = flags + if (isConstructor) setOf(MethodFlag.CONSTRUCTOR) else emptySet(),
            )

            if (isConstructor) builder.addConstructor(methodInfo)
            else builder.addMethod(methodInfo)
        }

        // Fields
        for (f in cf.fields) {
            val name = cf.string(f.nameIndex)
            val descriptor = cf.string(f.descriptorIndex)
            val fieldType = descriptorToTypeRef(descriptor)
            val flags = fieldFlags(f.accessFlags)

            builder.addField(FieldInfo(name, fieldType, flags = flags))
        }

        return listOf(builder.build())
    }

    private fun classKind(flags: Int): TypeKind = when {
        flags and AccessFlags.ANNOTATION != 0 -> TypeKind.ANNOTATION
        flags and AccessFlags.ENUM != 0 -> TypeKind.ENUM
        flags and AccessFlags.INTERFACE != 0 -> TypeKind.INTERFACE
        else -> TypeKind.CLASS
    }

    private fun methodFlags(flags: Int): Set<MethodFlag> = buildSet {
        if (flags and AccessFlags.PUBLIC != 0) add(MethodFlag.PUBLIC)
        if (flags and AccessFlags.PRIVATE != 0) add(MethodFlag.PRIVATE)
        if (flags and AccessFlags.PROTECTED != 0) add(MethodFlag.PROTECTED)
        if (flags and AccessFlags.STATIC != 0) add(MethodFlag.STATIC)
        if (flags and AccessFlags.FINAL != 0) add(MethodFlag.FINAL)
        if (flags and AccessFlags.ABSTRACT != 0) add(MethodFlag.ABSTRACT)
        if (flags and AccessFlags.NATIVE != 0) add(MethodFlag.NATIVE)
        if (flags and AccessFlags.SYNCHRONIZED != 0) add(MethodFlag.SYNCHRONIZED)
    }

    private fun fieldFlags(flags: Int): Set<FieldFlag> = buildSet {
        if (flags and AccessFlags.PUBLIC != 0) add(FieldFlag.PUBLIC)
        if (flags and AccessFlags.PRIVATE != 0) add(FieldFlag.PRIVATE)
        if (flags and AccessFlags.PROTECTED != 0) add(FieldFlag.PROTECTED)
        if (flags and AccessFlags.STATIC != 0) add(FieldFlag.STATIC)
        if (flags and AccessFlags.FINAL != 0) add(FieldFlag.READONLY)
        if (flags and AccessFlags.VOLATILE != 0) add(FieldFlag.VOLATILE)
        if (flags and AccessFlags.TRANSIENT != 0) add(FieldFlag.TRANSIENT)
    }

    internal fun parseMethodDescriptor(descriptor: String): Pair<TypeRef, List<TypeRef>> {
        if (!descriptor.startsWith("(")) return TypeRef.VOID to emptyList()
        val closeIdx = descriptor.indexOf(')')
        if (closeIdx < 0) return TypeRef.VOID to emptyList()

        val paramsPart = descriptor.substring(1, closeIdx)
        val returnPart = descriptor.substring(closeIdx + 1)

        val params = mutableListOf<TypeRef>()
        var i = 0
        while (i < paramsPart.length) {
            val (type, nextI) = parseFieldType(paramsPart, i)
            params.add(type)
            i = nextI
        }

        val returnType = if (returnPart == "V") TypeRef.VOID else {
            parseFieldType(returnPart, 0).first
        }

        return returnType to params
    }

    private fun parseFieldType(desc: String, pos: Int): Pair<TypeRef, Int> {
        if (pos >= desc.length) return TypeRef.VOID to pos
        return when (desc[pos]) {
            'B' -> TypeRef.I8 to pos + 1
            'C' -> TypeRef.of("char") to pos + 1
            'D' -> TypeRef.F64 to pos + 1
            'F' -> TypeRef.F32 to pos + 1
            'I' -> TypeRef.I32 to pos + 1
            'J' -> TypeRef.I64 to pos + 1
            'S' -> TypeRef.I16 to pos + 1
            'Z' -> TypeRef.BOOL to pos + 1
            'V' -> TypeRef.VOID to pos + 1
            '[' -> {
                val (element, nextPos) = parseFieldType(desc, pos + 1)
                TypeRef.arrayOf(element) to nextPos
            }
            'L' -> {
                val semi = desc.indexOf(';', pos)
                if (semi < 0) return TypeRef.of("?") to desc.length
                val className = desc.substring(pos + 1, semi).replace('/', '.')
                TypeRef.of(className) to semi + 1
            }
            else -> TypeRef.of("?") to pos + 1
        }
    }

    internal fun descriptorToTypeRef(descriptor: String): TypeRef {
        return parseFieldType(descriptor, 0).first
    }
}
