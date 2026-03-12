package org.kgen.reflect

import org.kgen.binary.pe.clr.*

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

    // CLR MethodSemanticsAttributes
    private const val MS_SETTER = 0x0001
    private const val MS_GETTER = 0x0002
    private const val MS_OTHER = 0x0004
    private const val MS_ADD_ON = 0x0008
    private const val MS_REMOVE_ON = 0x0010
    private const val MS_FIRE = 0x0020

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

        // Build MethodInfo lookup by 1-based row index
        val methodInfoByRow = buildMethodInfoMap(clr)

        // Build semantics lookup: association coded index → list of (semantics, methodRow)
        val semanticsByAssociation = buildSemanticsMap(clr)

        // Build property ranges per TypeDef row (1-based)
        val propertyRanges = buildListRanges(clr.tables.propertyMaps.map { it.parent to it.propertyList }, clr.tables.properties.size)

        // Build event ranges per TypeDef row (1-based)
        val eventRanges = buildListRanges(clr.tables.eventMaps.map { it.parent to it.eventList }, clr.tables.events.size)

        val result = mutableListOf<TypeInfo>()

        for ((typeIdx, td) in typeDefs.withIndex()) {
            val typeName = clr.strings.get(td.name)
            val typeNs = clr.strings.get(td.namespace)

            // Skip the <Module> pseudo-type
            if (typeName == "<Module>") continue

            val typeRow = typeIdx + 1 // 1-based
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
                val methodInfo = methodInfoByRow[mi + 1] ?: continue
                val methodName = methodInfo.name()
                val isConstructor = methodName == ".ctor" || methodName == ".cctor"

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

            // Properties for this type
            val propRange = propertyRanges[typeRow]
            if (propRange != null) {
                for (propRow in propRange.first..propRange.second) {
                    if (propRow < 1 || propRow > clr.tables.properties.size) continue
                    val prop = clr.tables.properties[propRow - 1]
                    val propName = clr.strings.get(prop.name)

                    // HAS_SEMANTICS coded index for Property: tag=1, 1 bit
                    val assocKey = (propRow shl 1) or 1
                    val sems = semanticsByAssociation[assocKey] ?: emptyList()

                    val getter = sems.firstOrNull { it.first and MS_GETTER != 0 }?.let { methodInfoByRow[it.second] }
                    val setter = sems.firstOrNull { it.first and MS_SETTER != 0 }?.let { methodInfoByRow[it.second] }

                    builder.addProperty(PropertyInfo(propName, TypeRef.of("?"), getter = getter, setter = setter))
                }
            }

            // Events for this type
            val eventRange = eventRanges[typeRow]
            if (eventRange != null) {
                for (eventRow in eventRange.first..eventRange.second) {
                    if (eventRow < 1 || eventRow > clr.tables.events.size) continue
                    val event = clr.tables.events[eventRow - 1]
                    val eventName = clr.strings.get(event.name)

                    // HAS_SEMANTICS coded index for Event: tag=0, 1 bit
                    val assocKey = (eventRow shl 1) or 0
                    val sems = semanticsByAssociation[assocKey] ?: emptyList()

                    val addMethod = sems.firstOrNull { it.first and MS_ADD_ON != 0 }?.let { methodInfoByRow[it.second] }
                    val removeMethod = sems.firstOrNull { it.first and MS_REMOVE_ON != 0 }?.let { methodInfoByRow[it.second] }
                    val raiseMethod = sems.firstOrNull { it.first and MS_FIRE != 0 }?.let { methodInfoByRow[it.second] }

                    builder.addEvent(EventInfo(eventName, TypeRef.of("?"), addMethod = addMethod, removeMethod = removeMethod, raiseMethod = raiseMethod))
                }
            }

            result.add(builder.build())
        }

        return result
    }

    private fun buildMethodInfoMap(clr: ClrMetadata): Map<Int, MethodInfo> {
        val map = mutableMapOf<Int, MethodInfo>()
        for ((idx, md) in clr.tables.methodDefs.withIndex()) {
            val methodName = clr.strings.get(md.name)
            val mflags = methodFlags(md)
            val isConstructor = methodName == ".ctor" || methodName == ".cctor"
            map[idx + 1] = MethodInfo(
                name = methodName,
                flags = mflags + if (isConstructor) setOf(MethodFlag.CONSTRUCTOR) else emptySet(),
            )
        }
        return map
    }

    private fun buildSemanticsMap(clr: ClrMetadata): Map<Int, List<Pair<Int, Int>>> {
        val map = mutableMapOf<Int, MutableList<Pair<Int, Int>>>()
        for (sem in clr.tables.methodSemantics) {
            map.getOrPut(sem.association) { mutableListOf() }.add(sem.semantics to sem.method)
        }
        return map
    }

    private fun buildListRanges(mapEntries: List<Pair<Int, Int>>, totalItems: Int): Map<Int, Pair<Int, Int>> {
        if (mapEntries.isEmpty()) return emptyMap()
        val ranges = mutableMapOf<Int, Pair<Int, Int>>()
        for ((i, entry) in mapEntries.withIndex()) {
            val start = entry.second
            val end = if (i + 1 < mapEntries.size) mapEntries[i + 1].second - 1 else totalItems
            ranges[entry.first] = start to end
        }
        return ranges
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
