package org.kgen.binary.pe.clr

import java.nio.ByteBuffer

/**
 * Parses CLR metadata streams and tables from a PE file's metadata root.
 *
 * The metadata root starts with the "BSJB" signature and contains stream headers
 * pointing to #Strings, #Blob, #GUID, #US, and #~ (or #-) streams.
 */
class ClrTableParser(private val buf: ByteBuffer, private val raw: ByteArray) {

    fun parse(
        metaOff: Int, metaSize: Int,
        majorRtVer: Int, minorRtVer: Int, flags: Int, entryPointToken: Int,
    ): ClrMetadata? {
        val sig = buf.getInt(metaOff)
        if (sig != 0x424A5342) return null // "BSJB"

        val versionLen = buf.getInt(metaOff + 12)
        val versionBytes = raw.copyOfRange(metaOff + 16, metaOff + 16 + versionLen)
        val version = String(versionBytes, Charsets.UTF_8).trimEnd('\u0000')

        val streamsOff = metaOff + 16 + ((versionLen + 3) and 3.inv())
        val numberOfStreams = buf.getShort(streamsOff + 2).toInt() and 0xFFFF

        var stringsData = ByteArray(0)
        var blobData = ByteArray(0)
        var guidData = ByteArray(0)
        var usData = ByteArray(0)
        var tablesOff = 0
        var tablesSize = 0

        var cursor = streamsOff + 4
        for (i in 0 until numberOfStreams) {
            val sOff = buf.getInt(cursor)
            val sSize = buf.getInt(cursor + 4)
            val sName = readNullTerminated(cursor + 8)
            cursor = cursor + 8 + ((sName.length + 1 + 3) and 3.inv())

            val absOff = metaOff + sOff
            val data = if (absOff + sSize <= raw.size) raw.copyOfRange(absOff, absOff + sSize) else ByteArray(0)
            when (sName) {
                "#Strings" -> stringsData = data
                "#Blob" -> blobData = data
                "#GUID" -> guidData = data
                "#US" -> usData = data
                "#~", "#-" -> { tablesOff = absOff; tablesSize = sSize }
            }
        }

        val tables = if (tablesOff > 0 && tablesSize > 0) {
            parseTables(tablesOff, stringsData.size, guidData.size, blobData.size)
        } else ClrTables()

        return ClrMetadata(
            majorRuntimeVersion = majorRtVer,
            minorRuntimeVersion = minorRtVer,
            flags = flags,
            entryPointToken = entryPointToken,
            metadataVersion = version,
            tables = tables,
            strings = ClrStringHeap(stringsData),
            blobs = ClrBlobHeap(blobData),
            guids = ClrGuidHeap(guidData),
            userStrings = ClrUserStringHeap(usData),
        )
    }

    private fun parseTables(off: Int, strHeapSize: Int, guidHeapSize: Int, blobHeapSize: Int): ClrTables {
        val heapSizes = raw[off + 6].toInt() and 0xFF
        val wideStrings = heapSizes and 0x01 != 0
        val wideGuid = heapSizes and 0x02 != 0
        val wideBlob = heapSizes and 0x04 != 0

        val valid = buf.getLong(off + 8)

        val rowCounts = IntArray(64)
        var cursor = off + 24
        for (i in 0 until 64) {
            if (valid and (1L shl i) != 0L) {
                rowCounts[i] = buf.getInt(cursor)
                cursor += 4
            }
        }

        val ctx = TableReadContext(buf, raw, cursor, rowCounts, wideStrings, wideGuid, wideBlob)

        val modules = ctx.parseTable(0x00) { it.readModule() }
        val typeRefs = ctx.parseTable(0x01) { it.readTypeRef() }
        val typeDefs = ctx.parseTable(0x02) { it.readTypeDef() }
        ctx.skipTable(0x03) // FieldPtr
        val fields = ctx.parseTable(0x04) { it.readField() }
        ctx.skipTable(0x05) // MethodPtr
        val methodDefs = ctx.parseTable(0x06) { it.readMethodDef() }
        ctx.skipTable(0x07) // ParamPtr
        val params = ctx.parseTable(0x08) { it.readParam() }
        val interfaceImpls = ctx.parseTable(0x09) { it.readInterfaceImpl() }
        val memberRefs = ctx.parseTable(0x0A) { it.readMemberRef() }
        val constants = ctx.parseTable(0x0B) { it.readConstant() }
        val customAttributes = ctx.parseTable(0x0C) { it.readCustomAttribute() }
        val fieldMarshals = ctx.parseTable(0x0D) { it.readFieldMarshal() }
        val declSecurities = ctx.parseTable(0x0E) { it.readDeclSecurity() }
        val classLayouts = ctx.parseTable(0x0F) { it.readClassLayout() }
        val fieldLayouts = ctx.parseTable(0x10) { it.readFieldLayout() }
        val standAloneSigs = ctx.parseTable(0x11) { it.readStandAloneSig() }
        val eventMaps = ctx.parseTable(0x12) { it.readEventMap() }
        ctx.skipTable(0x13) // EventPtr
        val events = ctx.parseTable(0x14) { it.readEvent() }
        val propertyMaps = ctx.parseTable(0x15) { it.readPropertyMap() }
        ctx.skipTable(0x16) // PropertyPtr
        val properties = ctx.parseTable(0x17) { it.readProperty() }
        val methodSemantics = ctx.parseTable(0x18) { it.readMethodSemantics() }
        val methodImpls = ctx.parseTable(0x19) { it.readMethodImpl() }
        val moduleRefs = ctx.parseTable(0x1A) { it.readModuleRef() }
        val typeSpecs = ctx.parseTable(0x1B) { it.readTypeSpec() }
        val implMaps = ctx.parseTable(0x1C) { it.readImplMap() }
        val fieldRVAs = ctx.parseTable(0x1D) { it.readFieldRVA() }
        ctx.skipTable(0x1E) // ENCLog
        ctx.skipTable(0x1F) // ENCMap
        val assemblies = ctx.parseTable(0x20) { it.readAssembly() }
        ctx.skipTable(0x21) // AssemblyProcessor
        ctx.skipTable(0x22) // AssemblyOS
        val assemblyRefs = ctx.parseTable(0x23) { it.readAssemblyRef() }
        ctx.skipTable(0x24) // AssemblyRefProcessor
        ctx.skipTable(0x25) // AssemblyRefOS
        ctx.skipTable(0x26) // File
        ctx.skipTable(0x27) // ExportedType
        ctx.skipTable(0x28) // ManifestResource
        val nestedClasses = ctx.parseTable(0x29) { it.readNestedClass() }
        val genericParams = ctx.parseTable(0x2A) { it.readGenericParam() }
        val methodSpecs = ctx.parseTable(0x2B) { it.readMethodSpec() }
        val genericParamConstraints = ctx.parseTable(0x2C) { it.readGenericParamConstraint() }

        return ClrTables(
            modules = modules,
            typeRefs = typeRefs,
            typeDefs = typeDefs,
            fields = fields,
            methodDefs = methodDefs,
            params = params,
            interfaceImpls = interfaceImpls,
            memberRefs = memberRefs,
            constants = constants,
            customAttributes = customAttributes,
            fieldMarshals = fieldMarshals,
            declSecurities = declSecurities,
            standAloneSigs = standAloneSigs,
            classlayouts = classLayouts,
            fieldLayouts = fieldLayouts,
            eventMaps = eventMaps,
            events = events,
            propertyMaps = propertyMaps,
            properties = properties,
            methodSemantics = methodSemantics,
            methodImpls = methodImpls,
            moduleRefs = moduleRefs,
            typeSpecs = typeSpecs,
            implMaps = implMaps,
            fieldRVAs = fieldRVAs,
            assemblies = assemblies,
            assemblyRefs = assemblyRefs,
            nestedClasses = nestedClasses,
            genericParams = genericParams,
            methodSpecs = methodSpecs,
            genericParamConstraints = genericParamConstraints,
        )
    }

    private fun readNullTerminated(off: Int): String {
        var end = off
        while (end < raw.size && raw[end] != 0.toByte()) end++
        return String(raw, off, end - off, Charsets.US_ASCII)
    }
}

class TableReadContext(
    private val buf: ByteBuffer,
    private val raw: ByteArray,
    var cursor: Int,
    val rowCounts: IntArray,
    val wideStrings: Boolean,
    val wideGuid: Boolean,
    val wideBlob: Boolean,
) {
    private var nextTableId = 0

    fun <T> parseTable(tableId: Int, rowParser: (TableReadContext) -> T): List<T> {
        advanceTo(tableId)
        val count = rowCounts[tableId]
        if (count == 0) return emptyList()
        val result = ArrayList<T>(count)
        for (i in 0 until count) {
            result.add(rowParser(this))
        }
        return result
    }

    fun skipTable(tableId: Int) {
        advanceTo(tableId)
        val count = rowCounts[tableId]
        if (count > 0) {
            cursor += count * estimateRowSize(tableId)
        }
    }

    private fun advanceTo(tableId: Int) {
        while (nextTableId < tableId) {
            val count = rowCounts[nextTableId]
            if (count > 0) {
                cursor += count * estimateRowSize(nextTableId)
            }
            nextTableId++
        }
        if (nextTableId == tableId) nextTableId++
    }

    fun readU16(): Int {
        val v = buf.getShort(cursor).toInt() and 0xFFFF
        cursor += 2
        return v
    }

    fun readU32(): Int {
        val v = buf.getInt(cursor)
        cursor += 4
        return v
    }

    fun readStringIdx(): Int = if (wideStrings) readU32() else readU16()
    fun readGuidIdx(): Int = if (wideGuid) readU32() else readU16()
    fun readBlobIdx(): Int = if (wideBlob) readU32() else readU16()

    fun readTableIdx(tableId: Int): Int =
        if (rowCounts[tableId] > 0xFFFF) readU32() else readU16()

    fun readCodedIdx(tagBits: Int, tables: IntArray): Int {
        val maxRows = tables.maxOf { if (it < 64) rowCounts[it] else 0 }
        return if (maxRows >= (1 shl (16 - tagBits))) readU32() else readU16()
    }

    private fun strSize() = if (wideStrings) 4 else 2
    private fun guidSize() = if (wideGuid) 4 else 2
    private fun blobSize() = if (wideBlob) 4 else 2
    private fun tableSize(id: Int) = if (id < 64 && rowCounts[id] > 0xFFFF) 4 else 2
    private fun codedSize(tagBits: Int, tables: IntArray): Int {
        val maxRows = tables.maxOf { if (it < 64) rowCounts[it] else 0 }
        return if (maxRows >= (1 shl (16 - tagBits))) 4 else 2
    }

    private fun estimateRowSize(tableId: Int): Int = when (tableId) {
        0x00 -> 2 + strSize() + guidSize() * 3
        0x01 -> codedSize(2, RESOLUTION_SCOPE) + strSize() * 2
        0x02 -> 4 + strSize() * 2 + codedSize(2, TYPE_DEF_OR_REF) + tableSize(0x04) + tableSize(0x06)
        0x03 -> tableSize(0x02) + strSize()
        0x04 -> 2 + strSize() + blobSize()
        0x05 -> tableSize(0x06)
        0x06 -> 4 + 2 + 2 + strSize() + blobSize() + tableSize(0x08)
        0x07 -> tableSize(0x08)
        0x08 -> 2 + 2 + strSize()
        0x09 -> tableSize(0x02) + codedSize(2, TYPE_DEF_OR_REF)
        0x0A -> codedSize(3, MEMBER_REF_PARENT) + strSize() + blobSize()
        0x0B -> 2 + codedSize(2, HAS_CONSTANT) + blobSize()
        0x0C -> codedSize(5, HAS_CUSTOM_ATTRIBUTE) + codedSize(3, CUSTOM_ATTRIBUTE_TYPE) + blobSize()
        0x0D -> codedSize(1, HAS_FIELD_MARSHAL) + blobSize()
        0x0E -> 2 + codedSize(2, HAS_DECL_SECURITY) + blobSize()
        0x0F -> 2 + 4 + tableSize(0x02)
        0x10 -> 4 + tableSize(0x04)
        0x11 -> blobSize()
        0x12 -> tableSize(0x02) + tableSize(0x14)
        0x13 -> tableSize(0x14)
        0x14 -> 2 + strSize() + codedSize(2, TYPE_DEF_OR_REF)
        0x15 -> tableSize(0x02) + tableSize(0x17)
        0x16 -> tableSize(0x17)
        0x17 -> 2 + strSize() + blobSize()
        0x18 -> 2 + codedSize(1, HAS_SEMANTICS) + tableSize(0x06)
        0x19 -> tableSize(0x02) + codedSize(1, METHOD_DEF_OR_REF) + codedSize(1, METHOD_DEF_OR_REF)
        0x1A -> strSize()
        0x1B -> blobSize()
        0x1C -> 2 + codedSize(1, MEMBER_FORWARDED) + strSize() + tableSize(0x1A)
        0x1D -> 4 + tableSize(0x04)
        0x1E -> 0
        0x1F -> 0
        0x20 -> 4 + 2 * 4 + 4 + blobSize() + strSize() * 2
        0x21 -> 4
        0x22 -> 4 + 4 + strSize()
        0x23 -> 2 * 4 + 4 + blobSize() + strSize() * 2 + blobSize()
        0x24 -> 4 + 4
        0x25 -> 4 + 4 + strSize() + tableSize(0x23)
        0x26 -> 4 + strSize() + blobSize()
        0x27 -> 4 + 4 + strSize() + codedSize(2, IMPLEMENTATION)
        0x28 -> 4 + 4 + strSize() + codedSize(2, IMPLEMENTATION)
        0x29 -> tableSize(0x02) + tableSize(0x02)
        0x2A -> 2 + 2 + codedSize(1, TYPE_OR_METHOD_DEF) + strSize()
        0x2B -> codedSize(1, METHOD_DEF_OR_REF) + blobSize()
        0x2C -> tableSize(0x2A) + codedSize(2, TYPE_DEF_OR_REF)
        else -> 0
    }

    fun readModule() = ClrModule(
        generation = readU16(), name = readStringIdx(),
        mvid = readGuidIdx(), encId = readGuidIdx(), encBaseId = readGuidIdx(),
    )

    fun readTypeRef() = ClrTypeRef(
        resolutionScope = readCodedIdx(2, RESOLUTION_SCOPE),
        name = readStringIdx(), namespace = readStringIdx(),
    )

    fun readTypeDef() = ClrTypeDef(
        flags = readU32(), name = readStringIdx(), namespace = readStringIdx(),
        extends = readCodedIdx(2, TYPE_DEF_OR_REF),
        fieldList = readTableIdx(0x04), methodList = readTableIdx(0x06),
    )

    fun readField() = ClrField(flags = readU16(), name = readStringIdx(), signature = readBlobIdx())

    fun readMethodDef() = ClrMethodDef(
        rva = readU32(), implFlags = readU16(), flags = readU16(),
        name = readStringIdx(), signature = readBlobIdx(), paramList = readTableIdx(0x08),
    )

    fun readParam() = ClrParam(flags = readU16(), sequence = readU16(), name = readStringIdx())

    fun readInterfaceImpl() = ClrInterfaceImpl(
        classIndex = readTableIdx(0x02),
        interfaceIndex = readCodedIdx(2, TYPE_DEF_OR_REF),
    )

    fun readMemberRef() = ClrMemberRef(
        classIndex = readCodedIdx(3, MEMBER_REF_PARENT),
        name = readStringIdx(), signature = readBlobIdx(),
    )

    fun readConstant() = ClrConstant(
        type = readU16(), parent = readCodedIdx(2, HAS_CONSTANT), value = readBlobIdx(),
    )

    fun readCustomAttribute() = ClrCustomAttribute(
        parent = readCodedIdx(5, HAS_CUSTOM_ATTRIBUTE),
        type = readCodedIdx(3, CUSTOM_ATTRIBUTE_TYPE),
        value = readBlobIdx(),
    )

    fun readFieldMarshal() = ClrFieldMarshal(
        parent = readCodedIdx(1, HAS_FIELD_MARSHAL), nativeType = readBlobIdx(),
    )

    fun readDeclSecurity() = ClrDeclSecurity(
        action = readU16(), parent = readCodedIdx(2, HAS_DECL_SECURITY),
        permissionSet = readBlobIdx(),
    )

    fun readStandAloneSig() = ClrStandAloneSig(signature = readBlobIdx())

    fun readEventMap() = ClrEventMap(
        parent = readTableIdx(0x02), eventList = readTableIdx(0x14),
    )

    fun readEvent() = ClrEvent(
        flags = readU16(), name = readStringIdx(),
        eventType = readCodedIdx(2, TYPE_DEF_OR_REF),
    )

    fun readPropertyMap() = ClrPropertyMap(
        parent = readTableIdx(0x02), propertyList = readTableIdx(0x17),
    )

    fun readProperty() = ClrProperty(
        flags = readU16(), name = readStringIdx(), type = readBlobIdx(),
    )

    fun readMethodSemantics() = ClrMethodSemantics(
        semantics = readU16(),
        method = readTableIdx(0x06),
        association = readCodedIdx(1, HAS_SEMANTICS),
    )

    fun readMethodImpl() = ClrMethodImpl(
        classIndex = readTableIdx(0x02),
        methodBody = readCodedIdx(1, METHOD_DEF_OR_REF),
        methodDeclaration = readCodedIdx(1, METHOD_DEF_OR_REF),
    )

    fun readClassLayout() = ClrClassLayout(
        packingSize = readU16(), classSize = readU32(), parent = readTableIdx(0x02),
    )

    fun readFieldLayout() = ClrFieldLayout(offset = readU32(), field = readTableIdx(0x04))
    fun readModuleRef() = ClrModuleRef(name = readStringIdx())
    fun readTypeSpec() = ClrTypeSpec(signature = readBlobIdx())

    fun readImplMap() = ClrImplMap(
        mappingFlags = readU16(), memberForwarded = readCodedIdx(1, MEMBER_FORWARDED),
        importName = readStringIdx(), importScope = readTableIdx(0x1A),
    )

    fun readFieldRVA() = ClrFieldRVA(rva = readU32(), field = readTableIdx(0x04))

    fun readAssembly() = ClrAssembly(
        hashAlgId = readU32(),
        majorVersion = readU16(), minorVersion = readU16(),
        buildNumber = readU16(), revisionNumber = readU16(),
        flags = readU32(),
        publicKey = readBlobIdx(), name = readStringIdx(), culture = readStringIdx(),
    )

    fun readAssemblyRef() = ClrAssemblyRef(
        majorVersion = readU16(), minorVersion = readU16(),
        buildNumber = readU16(), revisionNumber = readU16(),
        flags = readU32(),
        publicKeyOrToken = readBlobIdx(), name = readStringIdx(),
        culture = readStringIdx(), hashValue = readBlobIdx(),
    )

    fun readNestedClass() = ClrNestedClass(
        nestedClass = readTableIdx(0x02), enclosingClass = readTableIdx(0x02),
    )

    fun readGenericParam() = ClrGenericParam(
        number = readU16(), flags = readU16(),
        owner = readCodedIdx(1, TYPE_OR_METHOD_DEF), name = readStringIdx(),
    )

    fun readMethodSpec() = ClrMethodSpec(
        method = readCodedIdx(1, METHOD_DEF_OR_REF), instantiation = readBlobIdx(),
    )

    fun readGenericParamConstraint() = ClrGenericParamConstraint(
        owner = readTableIdx(0x2A), constraint = readCodedIdx(2, TYPE_DEF_OR_REF),
    )

    companion object {
        val TYPE_DEF_OR_REF get() = ClrCodedIndex.TYPE_DEF_OR_REF
        val HAS_CONSTANT get() = ClrCodedIndex.HAS_CONSTANT
        val HAS_CUSTOM_ATTRIBUTE get() = ClrCodedIndex.HAS_CUSTOM_ATTRIBUTE
        val HAS_FIELD_MARSHAL get() = ClrCodedIndex.HAS_FIELD_MARSHAL
        val HAS_DECL_SECURITY get() = ClrCodedIndex.HAS_DECL_SECURITY
        val MEMBER_REF_PARENT get() = ClrCodedIndex.MEMBER_REF_PARENT
        val HAS_SEMANTICS get() = ClrCodedIndex.HAS_SEMANTICS
        val METHOD_DEF_OR_REF get() = ClrCodedIndex.METHOD_DEF_OR_REF
        val MEMBER_FORWARDED get() = ClrCodedIndex.MEMBER_FORWARDED
        val IMPLEMENTATION get() = ClrCodedIndex.IMPLEMENTATION
        val CUSTOM_ATTRIBUTE_TYPE get() = ClrCodedIndex.CUSTOM_ATTRIBUTE_TYPE
        val RESOLUTION_SCOPE get() = ClrCodedIndex.RESOLUTION_SCOPE
        val TYPE_OR_METHOD_DEF get() = ClrCodedIndex.TYPE_OR_METHOD_DEF
    }
}
