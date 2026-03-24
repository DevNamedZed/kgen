package org.kgen.binary.pe.clr

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

/**
 * Serializes CLR metadata tables and heaps into the binary metadata format.
 *
 * Produces the complete metadata root (starting with "BSJB" signature)
 * containing all streams: #~, #Strings, #Blob, #GUID, #US.
 *
 * ```kotlin
 * val writer = ClrTableWriter()
 * val bytes = writer.write(metadata)
 * ```
 */
class ClrTableWriter {

    fun write(meta: ClrMetadata): ByteArray {
        val stringsData = meta.strings.raw
        val blobData = meta.blobs.raw
        val guidData = meta.guids.raw
        val usData = meta.userStrings.raw
        val tablesData = writeTables(meta.tables, stringsData.size, guidData.size, blobData.size)

        return writeMetadataRoot(meta.metadataVersion, tablesData, stringsData, blobData, guidData, usData)
    }

    private fun writeMetadataRoot(
        version: String,
        tablesData: ByteArray,
        stringsData: ByteArray,
        blobData: ByteArray,
        guidData: ByteArray,
        usData: ByteArray,
    ): ByteArray {
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)

        // Signature
        dos.writeIntLE(0x424A5342) // "BSJB"
        dos.writeShortLE(1) // major version
        dos.writeShortLE(1) // minor version
        dos.writeIntLE(0) // reserved

        // Version string (padded to 4-byte boundary)
        val versionBytes = version.toByteArray(Charsets.UTF_8)
        val paddedLen = (versionBytes.size + 1 + 3) and 3.inv()
        dos.writeIntLE(paddedLen)
        dos.write(versionBytes)
        dos.write(ByteArray(paddedLen - versionBytes.size)) // null padding

        // Flags + number of streams
        dos.writeShortLE(0) // flags
        val streamCount = listOf(tablesData, stringsData, blobData, guidData, usData).count { it.isNotEmpty() }
        dos.writeShortLE(streamCount)

        // Compute stream offsets (relative to metadata root start)
        // Header size so far = 16 + paddedLen + 4
        // Stream headers come next, then stream data
        data class StreamInfo(val name: String, val data: ByteArray)

        val streams = mutableListOf<StreamInfo>()
        if (tablesData.isNotEmpty()) streams += StreamInfo("#~", tablesData)
        if (stringsData.isNotEmpty()) streams += StreamInfo("#Strings", stringsData)
        if (usData.isNotEmpty()) streams += StreamInfo("#US", usData)
        if (guidData.isNotEmpty()) streams += StreamInfo("#GUID", guidData)
        if (blobData.isNotEmpty()) streams += StreamInfo("#Blob", blobData)

        // Calculate stream header sizes
        val headerBaseSize = baos.size()
        var headersSize = 0
        for (s in streams) {
            // offset(4) + size(4) + name(padded to 4)
            headersSize += 8 + ((s.name.length + 1 + 3) and 3.inv())
        }

        // Stream data starts after all headers
        var dataOffset = headerBaseSize + headersSize
        // But offsets are relative to metadata root start, which is 0 in our output
        // Recalculate: the metadata root starts at byte 0 of our output
        var currentDataOffset = headerBaseSize + headersSize

        // Write stream headers
        for (s in streams) {
            dos.writeIntLE(currentDataOffset)
            dos.writeIntLE(s.data.size)
            val nameBytes = s.name.toByteArray(Charsets.US_ASCII)
            val namePadded = (nameBytes.size + 1 + 3) and 3.inv()
            dos.write(nameBytes)
            dos.write(ByteArray(namePadded - nameBytes.size)) // null + padding
            currentDataOffset += (s.data.size + 3) and 3.inv()
        }

        // Write stream data (each padded to 4-byte boundary)
        for (s in streams) {
            dos.write(s.data)
            val padding = ((s.data.size + 3) and 3.inv()) - s.data.size
            if (padding > 0) dos.write(ByteArray(padding))
        }

        dos.flush()
        return baos.toByteArray()
    }

    private fun writeTables(tables: ClrTables, strHeapSize: Int, guidHeapSize: Int, blobHeapSize: Int): ByteArray {
        val rowCounts = IntArray(64)
        rowCounts[0x00] = tables.modules.size
        rowCounts[0x01] = tables.typeRefs.size
        rowCounts[0x02] = tables.typeDefs.size
        rowCounts[0x04] = tables.fields.size
        rowCounts[0x06] = tables.methodDefs.size
        rowCounts[0x08] = tables.params.size
        rowCounts[0x09] = tables.interfaceImpls.size
        rowCounts[0x0A] = tables.memberRefs.size
        rowCounts[0x0B] = tables.constants.size
        rowCounts[0x0C] = tables.customAttributes.size
        rowCounts[0x0D] = tables.fieldMarshals.size
        rowCounts[0x0E] = tables.declSecurities.size
        rowCounts[0x0F] = tables.classlayouts.size
        rowCounts[0x10] = tables.fieldLayouts.size
        rowCounts[0x11] = tables.standAloneSigs.size
        rowCounts[0x12] = tables.eventMaps.size
        rowCounts[0x14] = tables.events.size
        rowCounts[0x15] = tables.propertyMaps.size
        rowCounts[0x17] = tables.properties.size
        rowCounts[0x18] = tables.methodSemantics.size
        rowCounts[0x19] = tables.methodImpls.size
        rowCounts[0x1A] = tables.moduleRefs.size
        rowCounts[0x1B] = tables.typeSpecs.size
        rowCounts[0x1C] = tables.implMaps.size
        rowCounts[0x1D] = tables.fieldRVAs.size
        rowCounts[0x20] = tables.assemblies.size
        rowCounts[0x23] = tables.assemblyRefs.size
        rowCounts[0x29] = tables.nestedClasses.size
        rowCounts[0x2A] = tables.genericParams.size
        rowCounts[0x2B] = tables.methodSpecs.size
        rowCounts[0x2C] = tables.genericParamConstraints.size

        val wideStrings = strHeapSize > 0xFFFF
        val wideGuid = guidHeapSize > 0xFFFF
        val wideBlob = blobHeapSize > 0xFFFF

        var valid = 0L
        for (i in 0 until 64) {
            if (rowCounts[i] > 0) valid = valid or (1L shl i)
        }

        val heapSizes = (if (wideStrings) 0x01 else 0) or
                (if (wideGuid) 0x02 else 0) or
                (if (wideBlob) 0x04 else 0)

        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)

        // Header: reserved(4), majorVersion(1), minorVersion(1), heapSizes(1), reserved(1), valid(8), sorted(8)
        dos.writeIntLE(0) // reserved
        dos.writeByte(2) // major version
        dos.writeByte(0) // minor version
        dos.writeByte(heapSizes)
        dos.writeByte(0) // reserved

        dos.writeLongLE(valid)
        dos.writeLongLE(0) // sorted (not used for writing)

        // Row counts
        for (i in 0 until 64) {
            if (rowCounts[i] > 0) dos.writeIntLE(rowCounts[i])
        }

        val ctx = TableWriteContext(dos, rowCounts, wideStrings, wideGuid, wideBlob)

        // Write table rows in order
        for (m in tables.modules) ctx.writeModule(m)
        for (r in tables.typeRefs) ctx.writeTypeRef(r)
        for (d in tables.typeDefs) ctx.writeTypeDef(d)
        for (f in tables.fields) ctx.writeField(f)
        for (m in tables.methodDefs) ctx.writeMethodDefinition(m)
        for (p in tables.params) ctx.writeParam(p)
        for (i in tables.interfaceImpls) ctx.writeInterfaceImpl(i)
        for (r in tables.memberRefs) ctx.writeMemberRef(r)
        for (c in tables.constants) ctx.writeConstant(c)
        for (a in tables.customAttributes) ctx.writeCustomAttribute(a)
        for (f in tables.fieldMarshals) ctx.writeFieldMarshal(f)
        for (d in tables.declSecurities) ctx.writeDeclSecurity(d)
        for (c in tables.classlayouts) ctx.writeClassLayout(c)
        for (f in tables.fieldLayouts) ctx.writeFieldLayout(f)
        for (s in tables.standAloneSigs) ctx.writeStandAloneSig(s)
        for (e in tables.eventMaps) ctx.writeEventMap(e)
        for (e in tables.events) ctx.writeEvent(e)
        for (p in tables.propertyMaps) ctx.writePropertyMap(p)
        for (p in tables.properties) ctx.writeProperty(p)
        for (s in tables.methodSemantics) ctx.writeMethodSemantics(s)
        for (i in tables.methodImpls) ctx.writeMethodImpl(i)
        for (r in tables.moduleRefs) ctx.writeModuleRef(r)
        for (t in tables.typeSpecs) ctx.writeTypeSpec(t)
        for (i in tables.implMaps) ctx.writeImplMap(i)
        for (r in tables.fieldRVAs) ctx.writeFieldRVA(r)
        for (a in tables.assemblies) ctx.writeAssembly(a)
        for (r in tables.assemblyRefs) ctx.writeAssemblyRef(r)
        for (n in tables.nestedClasses) ctx.writeNestedClass(n)
        for (g in tables.genericParams) ctx.writeGenericParam(g)
        for (s in tables.methodSpecs) ctx.writeMethodSpec(s)
        for (c in tables.genericParamConstraints) ctx.writeGenericParamConstraint(c)

        dos.flush()
        return baos.toByteArray()
    }
}

private class TableWriteContext(
    private val dos: DataOutputStream,
    val rowCounts: IntArray,
    val wideStrings: Boolean,
    val wideGuid: Boolean,
    val wideBlob: Boolean,
) {
    fun writeU16(v: Int) { dos.writeShortLE(v) }
    fun writeU32(v: Int) { dos.writeIntLE(v) }
    fun writeStringIdx(v: Int) { if (wideStrings) writeU32(v) else writeU16(v) }
    fun writeGuidIdx(v: Int) { if (wideGuid) writeU32(v) else writeU16(v) }
    fun writeBlobIdx(v: Int) { if (wideBlob) writeU32(v) else writeU16(v) }
    fun writeTableIdx(tableId: Int, v: Int) {
        if (rowCounts[tableId] > 0xFFFF) writeU32(v) else writeU16(v)
    }
    fun writeCodedIdx(tagBits: Int, tables: IntArray, v: Int) {
        val maxRows = tables.maxOf { if (it < 64) rowCounts[it] else 0 }
        if (maxRows >= (1 shl (16 - tagBits))) writeU32(v) else writeU16(v)
    }

    fun writeModule(m: ClrModule) {
        writeU16(m.generation); writeStringIdx(m.name)
        writeGuidIdx(m.mvid); writeGuidIdx(m.encId); writeGuidIdx(m.encBaseId)
    }

    fun writeTypeRef(r: ClrTypeRef) {
        writeCodedIdx(2, ClrCodedIndex.RESOLUTION_SCOPE, r.resolutionScope)
        writeStringIdx(r.name); writeStringIdx(r.namespace)
    }

    fun writeTypeDef(d: ClrTypeDef) {
        writeU32(d.flags); writeStringIdx(d.name); writeStringIdx(d.namespace)
        writeCodedIdx(2, ClrCodedIndex.TYPE_DEF_OR_REF, d.extends)
        writeTableIdx(0x04, d.fieldList); writeTableIdx(0x06, d.methodList)
    }

    fun writeField(f: ClrField) {
        writeU16(f.flags); writeStringIdx(f.name); writeBlobIdx(f.signature)
    }

    fun writeMethodDefinition(m: ClrMethodDefinition) {
        writeU32(m.rva); writeU16(m.implFlags); writeU16(m.flags)
        writeStringIdx(m.name); writeBlobIdx(m.signature); writeTableIdx(0x08, m.paramList)
    }

    fun writeParam(p: ClrParam) {
        writeU16(p.flags); writeU16(p.sequence); writeStringIdx(p.name)
    }

    fun writeInterfaceImpl(i: ClrInterfaceImpl) {
        writeTableIdx(0x02, i.classIndex)
        writeCodedIdx(2, ClrCodedIndex.TYPE_DEF_OR_REF, i.interfaceIndex)
    }

    fun writeMemberRef(r: ClrMemberRef) {
        writeCodedIdx(3, ClrCodedIndex.MEMBER_REF_PARENT, r.classIndex)
        writeStringIdx(r.name); writeBlobIdx(r.signature)
    }

    fun writeConstant(c: ClrConstant) {
        writeU16(c.type)
        writeCodedIdx(2, ClrCodedIndex.HAS_CONSTANT, c.parent)
        writeBlobIdx(c.value)
    }

    fun writeCustomAttribute(a: ClrCustomAttribute) {
        writeCodedIdx(5, ClrCodedIndex.HAS_CUSTOM_ATTRIBUTE, a.parent)
        writeCodedIdx(3, ClrCodedIndex.CUSTOM_ATTRIBUTE_TYPE, a.type)
        writeBlobIdx(a.value)
    }

    fun writeClassLayout(c: ClrClassLayout) {
        writeU16(c.packingSize); writeU32(c.classSize); writeTableIdx(0x02, c.parent)
    }

    fun writeFieldLayout(f: ClrFieldLayout) {
        writeU32(f.offset); writeTableIdx(0x04, f.field)
    }

    fun writeFieldMarshal(f: ClrFieldMarshal) {
        writeCodedIdx(1, ClrCodedIndex.HAS_FIELD_MARSHAL, f.parent)
        writeBlobIdx(f.nativeType)
    }

    fun writeDeclSecurity(d: ClrDeclSecurity) {
        writeU16(d.action)
        writeCodedIdx(2, ClrCodedIndex.HAS_DECL_SECURITY, d.parent)
        writeBlobIdx(d.permissionSet)
    }

    fun writeStandAloneSig(s: ClrStandAloneSig) { writeBlobIdx(s.signature) }

    fun writeEventMap(e: ClrEventMap) {
        writeTableIdx(0x02, e.parent); writeTableIdx(0x14, e.eventList)
    }

    fun writeEvent(e: ClrEvent) {
        writeU16(e.flags); writeStringIdx(e.name)
        writeCodedIdx(2, ClrCodedIndex.TYPE_DEF_OR_REF, e.eventType)
    }

    fun writePropertyMap(p: ClrPropertyMap) {
        writeTableIdx(0x02, p.parent); writeTableIdx(0x17, p.propertyList)
    }

    fun writeProperty(p: ClrProperty) {
        writeU16(p.flags); writeStringIdx(p.name); writeBlobIdx(p.type)
    }

    fun writeMethodSemantics(s: ClrMethodSemantics) {
        writeU16(s.semantics); writeTableIdx(0x06, s.method)
        writeCodedIdx(1, ClrCodedIndex.HAS_SEMANTICS, s.association)
    }

    fun writeMethodImpl(i: ClrMethodImpl) {
        writeTableIdx(0x02, i.classIndex)
        writeCodedIdx(1, ClrCodedIndex.METHOD_DEF_OR_REF, i.methodBody)
        writeCodedIdx(1, ClrCodedIndex.METHOD_DEF_OR_REF, i.methodDeclaration)
    }

    fun writeModuleRef(r: ClrModuleRef) { writeStringIdx(r.name) }
    fun writeTypeSpec(t: ClrTypeSpec) { writeBlobIdx(t.signature) }

    fun writeImplMap(i: ClrImplMap) {
        writeU16(i.mappingFlags)
        writeCodedIdx(1, ClrCodedIndex.MEMBER_FORWARDED, i.memberForwarded)
        writeStringIdx(i.importName); writeTableIdx(0x1A, i.importScope)
    }

    fun writeFieldRVA(r: ClrFieldRVA) { writeU32(r.rva); writeTableIdx(0x04, r.field) }

    fun writeAssembly(a: ClrAssembly) {
        writeU32(a.hashAlgId)
        writeU16(a.majorVersion); writeU16(a.minorVersion)
        writeU16(a.buildNumber); writeU16(a.revisionNumber)
        writeU32(a.flags)
        writeBlobIdx(a.publicKey); writeStringIdx(a.name); writeStringIdx(a.culture)
    }

    fun writeAssemblyRef(r: ClrAssemblyRef) {
        writeU16(r.majorVersion); writeU16(r.minorVersion)
        writeU16(r.buildNumber); writeU16(r.revisionNumber)
        writeU32(r.flags)
        writeBlobIdx(r.publicKeyOrToken); writeStringIdx(r.name)
        writeStringIdx(r.culture); writeBlobIdx(r.hashValue)
    }

    fun writeNestedClass(n: ClrNestedClass) {
        writeTableIdx(0x02, n.nestedClass); writeTableIdx(0x02, n.enclosingClass)
    }

    fun writeGenericParam(g: ClrGenericParam) {
        writeU16(g.number); writeU16(g.flags)
        writeCodedIdx(1, ClrCodedIndex.TYPE_OR_METHOD_DEF, g.owner)
        writeStringIdx(g.name)
    }

    fun writeMethodSpec(s: ClrMethodSpec) {
        writeCodedIdx(1, ClrCodedIndex.METHOD_DEF_OR_REF, s.method)
        writeBlobIdx(s.instantiation)
    }

    fun writeGenericParamConstraint(c: ClrGenericParamConstraint) {
        writeTableIdx(0x2A, c.owner)
        writeCodedIdx(2, ClrCodedIndex.TYPE_DEF_OR_REF, c.constraint)
    }
}

// Little-endian write helpers for DataOutputStream
private fun DataOutputStream.writeShortLE(v: Int) {
    write(v and 0xFF)
    write((v shr 8) and 0xFF)
}

private fun DataOutputStream.writeIntLE(v: Int) {
    write(v and 0xFF)
    write((v shr 8) and 0xFF)
    write((v shr 16) and 0xFF)
    write((v shr 24) and 0xFF)
}

private fun DataOutputStream.writeLongLE(v: Long) {
    writeIntLE(v.toInt())
    writeIntLE((v shr 32).toInt())
}
