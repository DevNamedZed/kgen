package org.kgen.binary.dwarf

import org.kgen.binary.CompositeTag
import org.kgen.binary.DebugInfo
import org.kgen.binary.DebugMember
import org.kgen.binary.DebugType
import org.kgen.binary.DwarfEncoding
import org.kgen.reflect.*

/**
 * Maps DWARF debug info types to the unified [TypeInfo] model.
 *
 * Similar to [JvmTypeMapper] and [ClrTypeMapper] — given a [DebugInfo],
 * extracts struct/class/union/enum definitions and maps them to [TypeInfo].
 *
 * ```java
 * var debug = DwarfReader.read(objectFile);
 * var types = DwarfTypeMapper.map(debug, module);
 * for (var type : types) {
 *     System.out.println(type.name() + ": " + type.fields().size() + " fields");
 * }
 * ```
 */
object DwarfTypeMapper {

    @JvmStatic
    fun map(debugInfo: DebugInfo, module: Module? = null): List<TypeInfo> {
        val result = mutableListOf<TypeInfo>()
        for (cu in debugInfo.compileUnits) {
            for (type in cu.types) {
                val mapped = mapType(type, module)
                if (mapped != null) result.add(mapped)
            }
        }
        return result
    }

    private fun mapType(debugType: DebugType, module: Module?): TypeInfo? {
        return when (debugType) {
            is DebugType.Composite -> mapComposite(debugType, module)
            is DebugType.Enum -> mapEnum(debugType, module)
            is DebugType.Typedef -> mapType(debugType.baseType, module)
            else -> null // base types, pointers, etc. are not standalone TypeInfo
        }
    }

    private fun mapComposite(composite: DebugType.Composite, module: Module?): TypeInfo {
        val kind = when (composite.tag) {
            CompositeTag.CLASS -> TypeKind.CLASS
            CompositeTag.STRUCT -> TypeKind.STRUCT
            CompositeTag.UNION -> TypeKind.STRUCT
            CompositeTag.INTERFACE -> TypeKind.INTERFACE
        }

        val builder = TypeInfo.builder(QualifiedName.parse(composite.name.ifEmpty { "<anonymous>" }))
            .kind(kind)
            .module(module)
            .size((composite.sizeInBits / 8).toInt())

        for (member in composite.members) {
            builder.addField(mapField(member))
        }

        return builder.build()
    }

    private fun mapEnum(enumType: DebugType.Enum, module: Module?): TypeInfo {
        val builder = TypeInfo.builder(QualifiedName.parse(enumType.name.ifEmpty { "<anonymous>" }))
            .kind(TypeKind.ENUM)
            .module(module)
            .size((enumType.sizeInBits / 8).toInt())

        for ((name, _) in enumType.enumerators) {
            builder.addField(FieldInfo(name, mapDebugTypeRef(enumType.baseType)))
        }

        return builder.build()
    }

    private fun mapField(member: DebugMember): FieldInfo {
        val fieldType = mapDebugTypeRef(member.type)
        val flags = mutableSetOf<FieldFlag>()
        when (member.accessibility) {
            org.kgen.binary.DebugAccessibility.PRIVATE -> flags.add(FieldFlag.PRIVATE)
            org.kgen.binary.DebugAccessibility.PROTECTED -> flags.add(FieldFlag.PROTECTED)
            org.kgen.binary.DebugAccessibility.PUBLIC -> flags.add(FieldFlag.PUBLIC)
        }
        if (member.isStatic) flags.add(FieldFlag.STATIC)
        return FieldInfo(
            member.name, fieldType,
            null, // declaringType
            flags,
            null, // constantVal
            (member.offsetInBits / 8).toInt(),
        )
    }

    private fun mapDebugTypeRef(debugType: DebugType): TypeRef {
        return when (debugType) {
            is DebugType.Base -> mapBaseType(debugType)
            is DebugType.Pointer -> TypeRef.pointerTo(mapDebugTypeRef(debugType.pointee))
            is DebugType.Reference -> TypeRef.byRef(mapDebugTypeRef(debugType.referent))
            is DebugType.Array -> TypeRef.arrayOf(mapDebugTypeRef(debugType.element))
            is DebugType.Const -> mapDebugTypeRef(debugType.baseType)
            is DebugType.Volatile -> mapDebugTypeRef(debugType.baseType)
            is DebugType.Typedef -> TypeRef.of(debugType.name)
            is DebugType.Composite -> TypeRef.of(debugType.name.ifEmpty { "?" })
            is DebugType.Enum -> TypeRef.of(debugType.name.ifEmpty { "?" })
            is DebugType.Subroutine -> TypeRef.of("fn")
        }
    }

    private fun mapBaseType(base: DebugType.Base): TypeRef {
        val sizeBits = base.sizeInBits
        return when (base.encoding) {
            DwarfEncoding.BOOLEAN -> TypeRef.BOOL
            DwarfEncoding.FLOAT -> when (sizeBits) {
                32L -> TypeRef.F32
                64L -> TypeRef.F64
                else -> TypeRef.of(base.name)
            }
            DwarfEncoding.SIGNED, DwarfEncoding.SIGNED_CHAR -> when (sizeBits) {
                8L -> TypeRef.I8
                16L -> TypeRef.I16
                32L -> TypeRef.I32
                64L -> TypeRef.I64
                else -> TypeRef.of(base.name)
            }
            DwarfEncoding.UNSIGNED, DwarfEncoding.UNSIGNED_CHAR, DwarfEncoding.UTF -> when (sizeBits) {
                8L -> TypeRef.U8
                16L -> TypeRef.U16
                32L -> TypeRef.U32
                64L -> TypeRef.U64
                else -> TypeRef.of(base.name)
            }
            DwarfEncoding.ADDRESS -> TypeRef.POINTER
        }
    }
}
