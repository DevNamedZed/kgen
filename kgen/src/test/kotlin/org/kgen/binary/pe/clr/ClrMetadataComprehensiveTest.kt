package org.kgen.binary.pe.clr

import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClrMetadataComprehensiveTest {

    private fun buildAndParse(
        tables: ClrTables,
        strings: ClrStringHeap = ClrStringHeapBuilder().apply { add("Test.dll") }.build(),
        blobs: ClrBlobHeap = ClrBlobHeapBuilder().build(),
        guids: ClrGuidHeap = ClrGuidHeapBuilder().apply { add(ByteArray(16)) }.build(),
        userStrings: ClrUserStringHeap = ClrUserStringHeapBuilder().build(),
        version: String = "v4.0.30319",
    ): ClrMetadata {
        val meta = ClrMetadata(
            majorRuntimeVersion = 2, minorRuntimeVersion = 5,
            flags = ClrMetadata.COR_FLAGS_ILONLY, entryPointToken = 0,
            metadataVersion = version, tables = tables,
            strings = strings, blobs = blobs, guids = guids, userStrings = userStrings,
        )
        val bytes = ClrTableWriter().write(meta)
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return ClrTableParser(buf, bytes).parse(0, bytes.size, 2, 5, meta.flags, 0)!!
    }

    private fun makeHeaps(): Heaps {
        return Heaps(
            ClrStringHeapBuilder(),
            ClrBlobHeapBuilder(),
            ClrGuidHeapBuilder(),
            ClrUserStringHeapBuilder(),
        )
    }

    private data class Heaps(
        val strings: ClrStringHeapBuilder,
        val blobs: ClrBlobHeapBuilder,
        val guids: ClrGuidHeapBuilder,
        val userStrings: ClrUserStringHeapBuilder,
    )

    @Test
    fun `string heap builder returns 0 for empty string`() {
        val builder = ClrStringHeapBuilder()
        assertEquals(0, builder.add(""))
    }

    @Test
    fun `string heap builder assigns sequential indices`() {
        val builder = ClrStringHeapBuilder()
        val idx1 = builder.add("A")
        val idx2 = builder.add("B")
        assertTrue(idx1 > 0)
        assertTrue(idx2 > idx1)
    }

    @Test
    fun `string heap builder deduplicates identical strings`() {
        val builder = ClrStringHeapBuilder()
        val idx1 = builder.add("duplicate")
        val idx2 = builder.add("duplicate")
        assertEquals(idx1, idx2)
    }

    @Test
    fun `string heap builder handles UTF-8 strings`() {
        val builder = ClrStringHeapBuilder()
        val idx = builder.add("\u00E9\u00E0\u00FC") // e-acute, a-grave, u-umlaut
        val heap = builder.build()
        assertEquals("\u00E9\u00E0\u00FC", heap.get(idx))
    }

    @Test
    fun `string heap builder size increases with entries`() {
        val builder = ClrStringHeapBuilder()
        val sizeBefore = builder.size
        builder.add("test")
        assertTrue(builder.size > sizeBefore)
    }

    @Test
    fun `string heap get returns empty for out-of-bounds index`() {
        val heap = ClrStringHeapBuilder().build()
        assertEquals("", heap.get(999))
    }

    @Test
    fun `string heap get returns empty for index 0`() {
        val builder = ClrStringHeapBuilder()
        builder.add("hello")
        val heap = builder.build()
        assertEquals("", heap.get(0))
    }

    @Test
    fun `blob heap builder returns 0 for empty data`() {
        val builder = ClrBlobHeapBuilder()
        assertEquals(0, builder.add(byteArrayOf()))
    }

    @Test
    fun `blob heap builder deduplicates identical blobs`() {
        val builder = ClrBlobHeapBuilder()
        val data = byteArrayOf(1, 2, 3, 4, 5)
        val idx1 = builder.add(data)
        val idx2 = builder.add(data)
        assertEquals(idx1, idx2)
    }

    @Test
    fun `blob heap builder round-trips small blob`() {
        val builder = ClrBlobHeapBuilder()
        val data = byteArrayOf(0x0A, 0x0B, 0x0C)
        val idx = builder.add(data)
        val heap = builder.build()
        assertTrue(data.contentEquals(heap.get(idx)))
    }

    @Test
    fun `blob heap builder round-trips medium blob over 127 bytes`() {
        val builder = ClrBlobHeapBuilder()
        val data = ByteArray(200) { (it % 256).toByte() }
        val idx = builder.add(data)
        val heap = builder.build()
        assertTrue(data.contentEquals(heap.get(idx)))
    }

    @Test
    fun `blob heap builder round-trips large blob over 16383 bytes`() {
        val builder = ClrBlobHeapBuilder()
        val data = ByteArray(20000) { (it % 256).toByte() }
        val idx = builder.add(data)
        val heap = builder.build()
        assertTrue(data.contentEquals(heap.get(idx)))
    }

    @Test
    fun `blob heap get returns empty for out-of-bounds index`() {
        val heap = ClrBlobHeapBuilder().build()
        assertTrue(heap.get(999).isEmpty())
    }

    @Test
    fun `blob heap get returns empty for index 0`() {
        val builder = ClrBlobHeapBuilder()
        builder.add(byteArrayOf(1, 2))
        val heap = builder.build()
        assertTrue(heap.get(0).isEmpty())
    }

    @Test
    fun `blob heap compressed int read 1-byte value`() {
        val data = byteArrayOf(0x7F.toByte())
        val (value, bytesRead) = ClrBlobHeap.readCompressedInt(data, 0)
        assertEquals(0x7F, value)
        assertEquals(1, bytesRead)
    }

    @Test
    fun `blob heap compressed int read 2-byte value`() {
        val data = byteArrayOf(0x80.toByte(), 0x80.toByte())
        val (value, bytesRead) = ClrBlobHeap.readCompressedInt(data, 0)
        assertEquals(0x0080, value)
        assertEquals(2, bytesRead)
    }

    @Test
    fun `blob heap compressed int read 4-byte value`() {
        val data = byteArrayOf(0xC0.toByte(), 0x00, 0x40, 0x00)
        val (value, bytesRead) = ClrBlobHeap.readCompressedInt(data, 0)
        assertEquals(0x4000, value)
        assertEquals(4, bytesRead)
    }

    @Test
    fun `blob heap compressed int write and read round-trip for small value`() {
        val baos = ByteArrayOutputStream()
        ClrBlobHeapBuilder.writeCompressedInt(baos, 42)
        val data = baos.toByteArray()
        val (value, _) = ClrBlobHeap.readCompressedInt(data, 0)
        assertEquals(42, value)
    }

    @Test
    fun `blob heap compressed int write and read round-trip for medium value`() {
        val baos = ByteArrayOutputStream()
        ClrBlobHeapBuilder.writeCompressedInt(baos, 300)
        val data = baos.toByteArray()
        val (value, _) = ClrBlobHeap.readCompressedInt(data, 0)
        assertEquals(300, value)
    }

    @Test
    fun `blob heap compressed int write and read round-trip for large value`() {
        val baos = ByteArrayOutputStream()
        ClrBlobHeapBuilder.writeCompressedInt(baos, 0x10000)
        val data = baos.toByteArray()
        val (value, _) = ClrBlobHeap.readCompressedInt(data, 0)
        assertEquals(0x10000, value)
    }

    @Test
    fun `guid heap builder returns 1-based index`() {
        val builder = ClrGuidHeapBuilder()
        val guid1 = ByteArray(16) { 1 }
        val guid2 = ByteArray(16) { 2 }
        assertEquals(1, builder.add(guid1))
        assertEquals(2, builder.add(guid2))
    }

    @Test
    fun `guid heap builder rejects non-16-byte arrays`() {
        val builder = ClrGuidHeapBuilder()
        try {
            builder.add(ByteArray(8))
            assertTrue(false, "Should have thrown")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("16"))
        }
    }

    @Test
    fun `guid heap round-trips multiple guids`() {
        val builder = ClrGuidHeapBuilder()
        val guid1 = ByteArray(16) { (it + 10).toByte() }
        val guid2 = ByteArray(16) { (it + 20).toByte() }
        val idx1 = builder.add(guid1)
        val idx2 = builder.add(guid2)
        val heap = builder.build()
        assertTrue(guid1.contentEquals(heap.get(idx1)))
        assertTrue(guid2.contentEquals(heap.get(idx2)))
    }

    @Test
    fun `guid heap get returns zeroes for index 0`() {
        val builder = ClrGuidHeapBuilder()
        builder.add(ByteArray(16) { 0xFF.toByte() })
        val heap = builder.build()
        val zero = heap.get(0)
        assertTrue(zero.all { it == 0.toByte() })
    }

    @Test
    fun `guid heap get returns zeroes for out-of-bounds index`() {
        val heap = ClrGuidHeapBuilder().build()
        val result = heap.get(99)
        assertEquals(16, result.size)
        assertTrue(result.all { it == 0.toByte() })
    }

    @Test
    fun `guid heap builder size tracks entries`() {
        val builder = ClrGuidHeapBuilder()
        assertEquals(0, builder.size)
        builder.add(ByteArray(16))
        assertEquals(16, builder.size)
        builder.add(ByteArray(16))
        assertEquals(32, builder.size)
    }

    @Test
    fun `user string heap builder returns 0 for empty string`() {
        val builder = ClrUserStringHeapBuilder()
        assertEquals(0, builder.add(""))
    }

    @Test
    fun `user string heap round-trips basic ASCII string`() {
        val builder = ClrUserStringHeapBuilder()
        val idx = builder.add("Hello")
        val heap = builder.build()
        assertEquals("Hello", heap.get(idx))
    }

    @Test
    fun `user string heap round-trips unicode string`() {
        val builder = ClrUserStringHeapBuilder()
        val idx = builder.add("\u0410\u0411\u0412") // Cyrillic
        val heap = builder.build()
        assertEquals("\u0410\u0411\u0412", heap.get(idx))
    }

    @Test
    fun `user string heap round-trips multiple strings`() {
        val builder = ClrUserStringHeapBuilder()
        val idx1 = builder.add("first")
        val idx2 = builder.add("second")
        val heap = builder.build()
        assertEquals("first", heap.get(idx1))
        assertEquals("second", heap.get(idx2))
    }

    @Test
    fun `user string heap get returns empty for index 0`() {
        val builder = ClrUserStringHeapBuilder()
        builder.add("test")
        val heap = builder.build()
        assertEquals("", heap.get(0))
    }

    @Test
    fun `user string heap get returns empty for out-of-bounds`() {
        val heap = ClrUserStringHeapBuilder().build()
        assertEquals("", heap.get(999))
    }

    @Test
    fun `user string heap builder size increases`() {
        val builder = ClrUserStringHeapBuilder()
        val s0 = builder.size
        builder.add("test")
        assertTrue(builder.size > s0)
    }

    @Test
    fun `metadata BSJB signature written correctly`() {
        val meta = ClrMetadata(
            majorRuntimeVersion = 2, minorRuntimeVersion = 5,
            flags = 0, entryPointToken = 0,
            metadataVersion = "v4.0.30319",
            tables = ClrTables(),
            strings = ClrStringHeapBuilder().build(),
            blobs = ClrBlobHeapBuilder().build(),
            guids = ClrGuidHeapBuilder().build(),
            userStrings = ClrUserStringHeapBuilder().build(),
        )
        val bytes = ClrTableWriter().write(meta)
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(0x424A5342, buf.getInt(0))
    }

    @Test
    fun `metadata version round-trips through writer and parser`() {
        val parsed = buildAndParse(ClrTables())
        assertEquals("v4.0.30319", parsed.metadataVersion)
    }

    @Test
    fun `metadata version round-trips with custom version string`() {
        val parsed = buildAndParse(ClrTables(), version = "v2.0.50727")
        assertEquals("v2.0.50727", parsed.metadataVersion)
    }

    @Test
    fun `metadata flags isILOnly`() {
        val meta = ClrMetadata(
            majorRuntimeVersion = 2, minorRuntimeVersion = 5,
            flags = ClrMetadata.COR_FLAGS_ILONLY, entryPointToken = 0,
            metadataVersion = "v4.0.30319", tables = ClrTables(),
            strings = ClrStringHeapBuilder().build(), blobs = ClrBlobHeapBuilder().build(),
            guids = ClrGuidHeapBuilder().build(), userStrings = ClrUserStringHeapBuilder().build(),
        )
        assertTrue(meta.isILOnly)
    }

    @Test
    fun `metadata flags is32BitRequired`() {
        val meta = ClrMetadata(
            majorRuntimeVersion = 2, minorRuntimeVersion = 5,
            flags = ClrMetadata.COR_FLAGS_32BITREQUIRED, entryPointToken = 0,
            metadataVersion = "v4.0.30319", tables = ClrTables(),
            strings = ClrStringHeapBuilder().build(), blobs = ClrBlobHeapBuilder().build(),
            guids = ClrGuidHeapBuilder().build(), userStrings = ClrUserStringHeapBuilder().build(),
        )
        assertTrue(meta.is32BitRequired)
    }

    @Test
    fun `metadata flags isStrongNameSigned`() {
        val meta = ClrMetadata(
            majorRuntimeVersion = 2, minorRuntimeVersion = 5,
            flags = ClrMetadata.COR_FLAGS_STRONGNAMESIGNED, entryPointToken = 0,
            metadataVersion = "v4.0.30319", tables = ClrTables(),
            strings = ClrStringHeapBuilder().build(), blobs = ClrBlobHeapBuilder().build(),
            guids = ClrGuidHeapBuilder().build(), userStrings = ClrUserStringHeapBuilder().build(),
        )
        assertTrue(meta.isStrongNameSigned)
    }

    @Test
    fun `metadata flags isNativeEntryPoint`() {
        val meta = ClrMetadata(
            majorRuntimeVersion = 2, minorRuntimeVersion = 5,
            flags = ClrMetadata.COR_FLAGS_NATIVE_ENTRYPOINT, entryPointToken = 0,
            metadataVersion = "v4.0.30319", tables = ClrTables(),
            strings = ClrStringHeapBuilder().build(), blobs = ClrBlobHeapBuilder().build(),
            guids = ClrGuidHeapBuilder().build(), userStrings = ClrUserStringHeapBuilder().build(),
        )
        assertTrue(meta.isNativeEntryPoint)
    }

    @Test
    fun `ClrTableId fromId resolves known tables`() {
        assertEquals(ClrTableId.MODULE, ClrTableId.fromId(0x00))
        assertEquals(ClrTableId.TYPE_REF, ClrTableId.fromId(0x01))
        assertEquals(ClrTableId.TYPE_DEF, ClrTableId.fromId(0x02))
        assertEquals(ClrTableId.FIELD, ClrTableId.fromId(0x04))
        assertEquals(ClrTableId.METHOD_DEF, ClrTableId.fromId(0x06))
        assertEquals(ClrTableId.PARAM, ClrTableId.fromId(0x08))
        assertEquals(ClrTableId.INTERFACE_IMPL, ClrTableId.fromId(0x09))
        assertEquals(ClrTableId.MEMBER_REF, ClrTableId.fromId(0x0A))
        assertEquals(ClrTableId.CONSTANT, ClrTableId.fromId(0x0B))
        assertEquals(ClrTableId.CUSTOM_ATTRIBUTE, ClrTableId.fromId(0x0C))
        assertEquals(ClrTableId.ASSEMBLY, ClrTableId.fromId(0x20))
        assertEquals(ClrTableId.ASSEMBLY_REF, ClrTableId.fromId(0x23))
        assertEquals(ClrTableId.GENERIC_PARAM, ClrTableId.fromId(0x2A))
        assertEquals(ClrTableId.METHOD_SPEC, ClrTableId.fromId(0x2B))
        assertEquals(ClrTableId.GENERIC_PARAM_CONSTRAINT, ClrTableId.fromId(0x2C))
    }

    @Test
    fun `ClrTableId fromId returns null for unknown id`() {
        assertNull(ClrTableId.fromId(0x03))
        assertNull(ClrTableId.fromId(0xFF))
    }

    @Test
    fun `ClrTables defaults to empty lists`() {
        val tables = ClrTables()
        assertTrue(tables.modules.isEmpty())
        assertTrue(tables.typeRefs.isEmpty())
        assertTrue(tables.typeDefs.isEmpty())
        assertTrue(tables.fields.isEmpty())
        assertTrue(tables.methodDefs.isEmpty())
        assertTrue(tables.params.isEmpty())
        assertTrue(tables.interfaceImpls.isEmpty())
        assertTrue(tables.memberRefs.isEmpty())
        assertTrue(tables.constants.isEmpty())
        assertTrue(tables.customAttributes.isEmpty())
        assertTrue(tables.standAloneSigs.isEmpty())
        assertTrue(tables.classlayouts.isEmpty())
        assertTrue(tables.fieldLayouts.isEmpty())
        assertTrue(tables.moduleRefs.isEmpty())
        assertTrue(tables.typeSpecs.isEmpty())
        assertTrue(tables.implMaps.isEmpty())
        assertTrue(tables.fieldRVAs.isEmpty())
        assertTrue(tables.assemblies.isEmpty())
        assertTrue(tables.assemblyRefs.isEmpty())
        assertTrue(tables.nestedClasses.isEmpty())
        assertTrue(tables.genericParams.isEmpty())
        assertTrue(tables.methodSpecs.isEmpty())
        assertTrue(tables.genericParamConstraints.isEmpty())
    }

    @Test
    fun `round-trip Module table`() {
        val h = makeHeaps()
        val name = h.strings.add("MyModule.dll")
        val mvid = h.guids.add(ByteArray(16) { it.toByte() })
        val tables = ClrTables(modules = listOf(ClrModule(0, name, mvid, 0, 0)))
        val parsed = buildAndParse(tables, h.strings.build(), h.blobs.build(), h.guids.build(), h.userStrings.build())
        assertEquals(1, parsed.tables.modules.size)
        assertEquals("MyModule.dll", parsed.strings.get(parsed.tables.modules[0].name))
        assertEquals(0, parsed.tables.modules[0].generation)
    }

    @Test
    fun `round-trip TypeRef table`() {
        val h = makeHeaps()
        val name = h.strings.add("Object")
        val ns = h.strings.add("System")
        val tables = ClrTables(typeRefs = listOf(ClrTypeRef(resolutionScope = 5, name = name, namespace = ns)))
        val parsed = buildAndParse(tables, h.strings.build(), h.blobs.build(), h.guids.build(), h.userStrings.build())
        assertEquals(1, parsed.tables.typeRefs.size)
        assertEquals("Object", parsed.strings.get(parsed.tables.typeRefs[0].name))
        assertEquals("System", parsed.strings.get(parsed.tables.typeRefs[0].namespace))
        assertEquals(5, parsed.tables.typeRefs[0].resolutionScope)
    }

    @Test
    fun `round-trip TypeDef table`() {
        val h = makeHeaps()
        val name = h.strings.add("Foo")
        val ns = h.strings.add("My.Ns")
        val tables = ClrTables(typeDefs = listOf(
            ClrTypeDef(flags = 0x00100001, name = name, namespace = ns, extends = 0, fieldList = 1, methodList = 1)
        ))
        val parsed = buildAndParse(tables, h.strings.build(), h.blobs.build(), h.guids.build(), h.userStrings.build())
        assertEquals(1, parsed.tables.typeDefs.size)
        val td = parsed.tables.typeDefs[0]
        assertEquals(0x00100001, td.flags)
        assertEquals("Foo", parsed.strings.get(td.name))
        assertEquals("My.Ns", parsed.strings.get(td.namespace))
        assertEquals(1, td.fieldList)
        assertEquals(1, td.methodList)
    }

    @Test
    fun `round-trip Field table`() {
        val h = makeHeaps()
        val name = h.strings.add("myField")
        val sig = h.blobs.add(byteArrayOf(0x06, 0x08))
        val tables = ClrTables(fields = listOf(ClrField(flags = 0x0006, name = name, signature = sig)))
        val parsed = buildAndParse(tables, h.strings.build(), h.blobs.build(), h.guids.build(), h.userStrings.build())
        assertEquals(1, parsed.tables.fields.size)
        assertEquals(0x0006, parsed.tables.fields[0].flags)
        assertEquals("myField", parsed.strings.get(parsed.tables.fields[0].name))
    }

    @Test
    fun `round-trip MethodDefinition table`() {
        val h = makeHeaps()
        val name = h.strings.add("Execute")
        val sig = h.blobs.add(byteArrayOf(0x00, 0x00, 0x01))
        val tables = ClrTables(methodDefs = listOf(
            ClrMethodDefinition(rva = 0x2050, implFlags = 0, flags = 0x0086, name = name, signature = sig, paramList = 1)
        ))
        val parsed = buildAndParse(tables, h.strings.build(), h.blobs.build(), h.guids.build(), h.userStrings.build())
        assertEquals(1, parsed.tables.methodDefs.size)
        val m = parsed.tables.methodDefs[0]
        assertEquals(0x2050, m.rva)
        assertEquals(0x0086, m.flags)
        assertEquals("Execute", parsed.strings.get(m.name))
        assertEquals(1, m.paramList)
    }

    @Test
    fun `round-trip Param table`() {
        val h = makeHeaps()
        val name = h.strings.add("count")
        val tables = ClrTables(params = listOf(ClrParam(flags = 0, sequence = 1, name = name)))
        val parsed = buildAndParse(tables, h.strings.build(), h.blobs.build(), h.guids.build(), h.userStrings.build())
        assertEquals(1, parsed.tables.params.size)
        assertEquals(1, parsed.tables.params[0].sequence)
        assertEquals("count", parsed.strings.get(parsed.tables.params[0].name))
    }

    @Test
    fun `round-trip InterfaceImpl table`() {
        val h = makeHeaps()
        val tables = ClrTables(
            typeDefs = listOf(ClrTypeDef(0, h.strings.add("<Module>"), h.strings.add(""), 0, 1, 1)),
            interfaceImpls = listOf(ClrInterfaceImpl(classIndex = 1, interfaceIndex = 5))
        )
        val parsed = buildAndParse(tables, h.strings.build(), h.blobs.build(), h.guids.build(), h.userStrings.build())
        assertEquals(1, parsed.tables.interfaceImpls.size)
        assertEquals(1, parsed.tables.interfaceImpls[0].classIndex)
        assertEquals(5, parsed.tables.interfaceImpls[0].interfaceIndex)
    }

    @Test
    fun `round-trip MemberRef table`() {
        val h = makeHeaps()
        val name = h.strings.add(".ctor")
        val sig = h.blobs.add(byteArrayOf(0x20, 0x00, 0x01))
        val tables = ClrTables(
            typeRefs = listOf(ClrTypeRef(0, h.strings.add("Object"), h.strings.add("System"))),
            memberRefs = listOf(ClrMemberRef(classIndex = (1 shl 3) or 1, name = name, signature = sig))
        )
        val parsed = buildAndParse(tables, h.strings.build(), h.blobs.build(), h.guids.build(), h.userStrings.build())
        assertEquals(1, parsed.tables.memberRefs.size)
        assertEquals(".ctor", parsed.strings.get(parsed.tables.memberRefs[0].name))
    }

    @Test
    fun `round-trip Constant table`() {
        val h = makeHeaps()
        val value = h.blobs.add(byteArrayOf(0x2A, 0x00, 0x00, 0x00)) // int32 42
        val tables = ClrTables(
            fields = listOf(ClrField(0x8056, h.strings.add("MY_CONST"), h.blobs.add(byteArrayOf(0x06, 0x08)))),
            constants = listOf(ClrConstant(type = 0x08, parent = (1 shl 2) or 0, value = value))
        )
        val parsed = buildAndParse(tables, h.strings.build(), h.blobs.build(), h.guids.build(), h.userStrings.build())
        assertEquals(1, parsed.tables.constants.size)
        assertEquals(0x08, parsed.tables.constants[0].type)
    }

    @Test
    fun `round-trip CustomAttribute table`() {
        val h = makeHeaps()
        val value = h.blobs.add(byteArrayOf(0x01, 0x00, 0x00, 0x00))
        val tables = ClrTables(
            typeDefs = listOf(ClrTypeDef(0, h.strings.add("<Module>"), h.strings.add(""), 0, 1, 1)),
            methodDefs = listOf(ClrMethodDefinition(0, 0, 0x0086, h.strings.add(".ctor"), h.blobs.add(byteArrayOf(0x20, 0x00, 0x01)), 1)),
            customAttributes = listOf(ClrCustomAttribute(
                parent = (1 shl 5) or 2, // TypeDef tag=2
                type = (1 shl 3) or 2,   // MethodDefinition tag=2
                value = value,
            ))
        )
        val parsed = buildAndParse(tables, h.strings.build(), h.blobs.build(), h.guids.build(), h.userStrings.build())
        assertEquals(1, parsed.tables.customAttributes.size)
    }

    @Test
    fun `round-trip StandAloneSig table`() {
        val h = makeHeaps()
        val sig = h.blobs.add(byteArrayOf(0x07, 0x01, 0x08))
        val tables = ClrTables(standAloneSigs = listOf(ClrStandAloneSig(signature = sig)))
        val parsed = buildAndParse(tables, h.strings.build(), h.blobs.build(), h.guids.build(), h.userStrings.build())
        assertEquals(1, parsed.tables.standAloneSigs.size)
        assertEquals(sig, parsed.tables.standAloneSigs[0].signature)
    }

    @Test
    fun `round-trip ClassLayout table`() {
        val h = makeHeaps()
        val tables = ClrTables(
            typeDefs = listOf(ClrTypeDef(0, h.strings.add("<Module>"), h.strings.add(""), 0, 1, 1)),
            classlayouts = listOf(ClrClassLayout(packingSize = 1, classSize = 32, parent = 1))
        )
        val parsed = buildAndParse(tables, h.strings.build(), h.blobs.build(), h.guids.build(), h.userStrings.build())
        assertEquals(1, parsed.tables.classlayouts.size)
        assertEquals(1, parsed.tables.classlayouts[0].packingSize)
        assertEquals(32, parsed.tables.classlayouts[0].classSize)
        assertEquals(1, parsed.tables.classlayouts[0].parent)
    }

    @Test
    fun `round-trip FieldLayout table`() {
        val h = makeHeaps()
        val tables = ClrTables(
            fields = listOf(ClrField(0, h.strings.add("f"), h.blobs.add(byteArrayOf(0x06, 0x08)))),
            fieldLayouts = listOf(ClrFieldLayout(offset = 16, field = 1))
        )
        val parsed = buildAndParse(tables, h.strings.build(), h.blobs.build(), h.guids.build(), h.userStrings.build())
        assertEquals(1, parsed.tables.fieldLayouts.size)
        assertEquals(16, parsed.tables.fieldLayouts[0].offset)
        assertEquals(1, parsed.tables.fieldLayouts[0].field)
    }

    @Test
    fun `round-trip ModuleRef table`() {
        val h = makeHeaps()
        val name = h.strings.add("kernel32.dll")
        val tables = ClrTables(moduleRefs = listOf(ClrModuleRef(name = name)))
        val parsed = buildAndParse(tables, h.strings.build(), h.blobs.build(), h.guids.build(), h.userStrings.build())
        assertEquals(1, parsed.tables.moduleRefs.size)
        assertEquals("kernel32.dll", parsed.strings.get(parsed.tables.moduleRefs[0].name))
    }

    @Test
    fun `round-trip TypeSpec table`() {
        val h = makeHeaps()
        val sig = h.blobs.add(byteArrayOf(0x15, 0x12, 0x08))
        val tables = ClrTables(typeSpecs = listOf(ClrTypeSpec(signature = sig)))
        val parsed = buildAndParse(tables, h.strings.build(), h.blobs.build(), h.guids.build(), h.userStrings.build())
        assertEquals(1, parsed.tables.typeSpecs.size)
        assertEquals(sig, parsed.tables.typeSpecs[0].signature)
    }

    @Test
    fun `round-trip ImplMap table`() {
        val h = makeHeaps()
        val importName = h.strings.add("MessageBoxA")
        val tables = ClrTables(
            methodDefs = listOf(ClrMethodDefinition(0, 0, 0x0086, h.strings.add("MsgBox"), h.blobs.add(byteArrayOf(0x00, 0x00, 0x01)), 1)),
            moduleRefs = listOf(ClrModuleRef(h.strings.add("user32.dll"))),
            implMaps = listOf(ClrImplMap(
                mappingFlags = 0x0001, // CharSetAnsi
                memberForwarded = (1 shl 1) or 1, // MethodDefinition #1, tag=1
                importName = importName,
                importScope = 1,
            ))
        )
        val parsed = buildAndParse(tables, h.strings.build(), h.blobs.build(), h.guids.build(), h.userStrings.build())
        assertEquals(1, parsed.tables.implMaps.size)
        assertEquals(0x0001, parsed.tables.implMaps[0].mappingFlags)
        assertEquals("MessageBoxA", parsed.strings.get(parsed.tables.implMaps[0].importName))
    }

    @Test
    fun `round-trip FieldRVA table`() {
        val h = makeHeaps()
        val tables = ClrTables(
            fields = listOf(ClrField(0, h.strings.add("data"), h.blobs.add(byteArrayOf(0x06, 0x08)))),
            fieldRVAs = listOf(ClrFieldRVA(rva = 0x3000, field = 1))
        )
        val parsed = buildAndParse(tables, h.strings.build(), h.blobs.build(), h.guids.build(), h.userStrings.build())
        assertEquals(1, parsed.tables.fieldRVAs.size)
        assertEquals(0x3000, parsed.tables.fieldRVAs[0].rva)
        assertEquals(1, parsed.tables.fieldRVAs[0].field)
    }

    @Test
    fun `round-trip Assembly table`() {
        val h = makeHeaps()
        val name = h.strings.add("MyAssembly")
        val culture = h.strings.add("")
        val tables = ClrTables(assemblies = listOf(ClrAssembly(
            hashAlgId = 0x8004, majorVersion = 2, minorVersion = 1,
            buildNumber = 3, revisionNumber = 4, flags = 0,
            publicKey = 0, name = name, culture = culture,
        )))
        val parsed = buildAndParse(tables, h.strings.build(), h.blobs.build(), h.guids.build(), h.userStrings.build())
        assertEquals(1, parsed.tables.assemblies.size)
        val a = parsed.tables.assemblies[0]
        assertEquals(0x8004, a.hashAlgId)
        assertEquals(2, a.majorVersion)
        assertEquals(1, a.minorVersion)
        assertEquals(3, a.buildNumber)
        assertEquals(4, a.revisionNumber)
        assertEquals("MyAssembly", parsed.strings.get(a.name))
    }

    @Test
    fun `round-trip AssemblyRef table`() {
        val h = makeHeaps()
        val name = h.strings.add("mscorlib")
        val token = h.blobs.add(byteArrayOf(0xB7.toByte(), 0x7A, 0x5C, 0x56, 0x19, 0x34, 0xE0.toByte(), 0x89.toByte()))
        val tables = ClrTables(assemblyRefs = listOf(ClrAssemblyRef(
            majorVersion = 4, minorVersion = 0, buildNumber = 0, revisionNumber = 0,
            flags = 0, publicKeyOrToken = token, name = name, culture = 0, hashValue = 0,
        )))
        val parsed = buildAndParse(tables, h.strings.build(), h.blobs.build(), h.guids.build(), h.userStrings.build())
        assertEquals(1, parsed.tables.assemblyRefs.size)
        assertEquals("mscorlib", parsed.strings.get(parsed.tables.assemblyRefs[0].name))
        assertEquals(4, parsed.tables.assemblyRefs[0].majorVersion)
    }

    @Test
    fun `round-trip NestedClass table`() {
        val h = makeHeaps()
        val tables = ClrTables(
            typeDefs = listOf(
                ClrTypeDef(0, h.strings.add("<Module>"), h.strings.add(""), 0, 1, 1),
                ClrTypeDef(0x00100001, h.strings.add("Outer"), h.strings.add(""), 0, 1, 1),
                ClrTypeDef(0x00100002, h.strings.add("Inner"), h.strings.add(""), 0, 1, 1),
            ),
            nestedClasses = listOf(ClrNestedClass(nestedClass = 3, enclosingClass = 2))
        )
        val parsed = buildAndParse(tables, h.strings.build(), h.blobs.build(), h.guids.build(), h.userStrings.build())
        assertEquals(1, parsed.tables.nestedClasses.size)
        assertEquals(3, parsed.tables.nestedClasses[0].nestedClass)
        assertEquals(2, parsed.tables.nestedClasses[0].enclosingClass)
    }

    @Test
    fun `round-trip GenericParam table`() {
        val h = makeHeaps()
        val tables = ClrTables(
            typeDefs = listOf(ClrTypeDef(0, h.strings.add("<Module>"), h.strings.add(""), 0, 1, 1)),
            genericParams = listOf(ClrGenericParam(
                number = 0, flags = 0x10,
                owner = (1 shl 1) or 0, // TypeDef #1, tag=0
                name = h.strings.add("T"),
            ))
        )
        val parsed = buildAndParse(tables, h.strings.build(), h.blobs.build(), h.guids.build(), h.userStrings.build())
        assertEquals(1, parsed.tables.genericParams.size)
        assertEquals(0, parsed.tables.genericParams[0].number)
        assertEquals(0x10, parsed.tables.genericParams[0].flags)
        assertEquals("T", parsed.strings.get(parsed.tables.genericParams[0].name))
    }

    @Test
    fun `round-trip MethodSpec table`() {
        val h = makeHeaps()
        val inst = h.blobs.add(byteArrayOf(0x0A, 0x01, 0x08))
        val tables = ClrTables(
            methodDefs = listOf(ClrMethodDefinition(0, 0, 0x0086, h.strings.add("M"), h.blobs.add(byteArrayOf(0x10, 0x01, 0x00, 0x01)), 1)),
            methodSpecs = listOf(ClrMethodSpec(method = (1 shl 1) or 0, instantiation = inst))
        )
        val parsed = buildAndParse(tables, h.strings.build(), h.blobs.build(), h.guids.build(), h.userStrings.build())
        assertEquals(1, parsed.tables.methodSpecs.size)
        assertEquals(inst, parsed.tables.methodSpecs[0].instantiation)
    }

    @Test
    fun `round-trip GenericParamConstraint table`() {
        val h = makeHeaps()
        val tables = ClrTables(
            typeDefs = listOf(ClrTypeDef(0, h.strings.add("<Module>"), h.strings.add(""), 0, 1, 1)),
            genericParams = listOf(ClrGenericParam(0, 0, (1 shl 1) or 0, h.strings.add("T"))),
            genericParamConstraints = listOf(ClrGenericParamConstraint(owner = 1, constraint = 5))
        )
        val parsed = buildAndParse(tables, h.strings.build(), h.blobs.build(), h.guids.build(), h.userStrings.build())
        assertEquals(1, parsed.tables.genericParamConstraints.size)
        assertEquals(1, parsed.tables.genericParamConstraints[0].owner)
        assertEquals(5, parsed.tables.genericParamConstraints[0].constraint)
    }

    @Test
    fun `round-trip multiple TypeDefs`() {
        val h = makeHeaps()
        val tables = ClrTables(typeDefs = listOf(
            ClrTypeDef(0, h.strings.add("<Module>"), h.strings.add(""), 0, 1, 1),
            ClrTypeDef(0x00100001, h.strings.add("ClassA"), h.strings.add("Ns"), 0, 1, 1),
            ClrTypeDef(0x00100001, h.strings.add("ClassB"), h.strings.add("Ns"), 0, 1, 1),
            ClrTypeDef(0x00100001, h.strings.add("ClassC"), h.strings.add("Ns"), 0, 1, 1),
        ))
        val parsed = buildAndParse(tables, h.strings.build(), h.blobs.build(), h.guids.build(), h.userStrings.build())
        assertEquals(4, parsed.tables.typeDefs.size)
        assertEquals("ClassA", parsed.strings.get(parsed.tables.typeDefs[1].name))
        assertEquals("ClassB", parsed.strings.get(parsed.tables.typeDefs[2].name))
        assertEquals("ClassC", parsed.strings.get(parsed.tables.typeDefs[3].name))
    }

    @Test
    fun `round-trip multiple MethodDefinitions`() {
        val h = makeHeaps()
        val sig = h.blobs.add(byteArrayOf(0x00, 0x00, 0x01))
        val tables = ClrTables(methodDefs = listOf(
            ClrMethodDefinition(0, 0, 0x0086, h.strings.add("A"), sig, 1),
            ClrMethodDefinition(0x100, 0, 0x0086, h.strings.add("B"), sig, 1),
            ClrMethodDefinition(0x200, 0, 0x0086, h.strings.add("C"), sig, 1),
        ))
        val parsed = buildAndParse(tables, h.strings.build(), h.blobs.build(), h.guids.build(), h.userStrings.build())
        assertEquals(3, parsed.tables.methodDefs.size)
        assertEquals("A", parsed.strings.get(parsed.tables.methodDefs[0].name))
        assertEquals(0x100, parsed.tables.methodDefs[1].rva)
        assertEquals(0x200, parsed.tables.methodDefs[2].rva)
    }

    @Test
    fun `round-trip multiple Params`() {
        val h = makeHeaps()
        val tables = ClrTables(params = listOf(
            ClrParam(0, 1, h.strings.add("a")),
            ClrParam(0, 2, h.strings.add("b")),
            ClrParam(0, 3, h.strings.add("c")),
        ))
        val parsed = buildAndParse(tables, h.strings.build(), h.blobs.build(), h.guids.build(), h.userStrings.build())
        assertEquals(3, parsed.tables.params.size)
        assertEquals(1, parsed.tables.params[0].sequence)
        assertEquals(2, parsed.tables.params[1].sequence)
        assertEquals(3, parsed.tables.params[2].sequence)
    }

    @Test
    fun `round-trip multiple Fields`() {
        val h = makeHeaps()
        val sig = h.blobs.add(byteArrayOf(0x06, 0x08))
        val tables = ClrTables(fields = listOf(
            ClrField(0x0006, h.strings.add("x"), sig),
            ClrField(0x0006, h.strings.add("y"), sig),
        ))
        val parsed = buildAndParse(tables, h.strings.build(), h.blobs.build(), h.guids.build(), h.userStrings.build())
        assertEquals(2, parsed.tables.fields.size)
        assertEquals("x", parsed.strings.get(parsed.tables.fields[0].name))
        assertEquals("y", parsed.strings.get(parsed.tables.fields[1].name))
    }

    @Test
    fun `round-trip multiple AssemblyRefs`() {
        val h = makeHeaps()
        val tables = ClrTables(assemblyRefs = listOf(
            ClrAssemblyRef(4, 0, 0, 0, 0, 0, h.strings.add("mscorlib"), 0, 0),
            ClrAssemblyRef(4, 0, 0, 0, 0, 0, h.strings.add("System"), 0, 0),
            ClrAssemblyRef(4, 0, 0, 0, 0, 0, h.strings.add("System.Core"), 0, 0),
        ))
        val parsed = buildAndParse(tables, h.strings.build(), h.blobs.build(), h.guids.build(), h.userStrings.build())
        assertEquals(3, parsed.tables.assemblyRefs.size)
        assertEquals("mscorlib", parsed.strings.get(parsed.tables.assemblyRefs[0].name))
        assertEquals("System", parsed.strings.get(parsed.tables.assemblyRefs[1].name))
        assertEquals("System.Core", parsed.strings.get(parsed.tables.assemblyRefs[2].name))
    }

    @Test
    fun `round-trip multiple GenericParams on one type`() {
        val h = makeHeaps()
        val tables = ClrTables(
            typeDefs = listOf(
                ClrTypeDef(0, h.strings.add("<Module>"), h.strings.add(""), 0, 1, 1),
                ClrTypeDef(0x00100001, h.strings.add("Dict"), h.strings.add(""), 0, 1, 1),
            ),
            genericParams = listOf(
                ClrGenericParam(0, 0, (2 shl 1) or 0, h.strings.add("TKey")),
                ClrGenericParam(1, 0, (2 shl 1) or 0, h.strings.add("TValue")),
            )
        )
        val parsed = buildAndParse(tables, h.strings.build(), h.blobs.build(), h.guids.build(), h.userStrings.build())
        assertEquals(2, parsed.tables.genericParams.size)
        assertEquals("TKey", parsed.strings.get(parsed.tables.genericParams[0].name))
        assertEquals("TValue", parsed.strings.get(parsed.tables.genericParams[1].name))
        assertEquals(0, parsed.tables.genericParams[0].number)
        assertEquals(1, parsed.tables.genericParams[1].number)
    }

    @Test
    fun `round-trip combined tables`() {
        val h = makeHeaps()
        val mvid = h.guids.add(ByteArray(16))
        val sig = h.blobs.add(byteArrayOf(0x00, 0x00, 0x01))
        val tables = ClrTables(
            modules = listOf(ClrModule(0, h.strings.add("Full.dll"), mvid, 0, 0)),
            typeDefs = listOf(
                ClrTypeDef(0, h.strings.add("<Module>"), h.strings.add(""), 0, 1, 1),
                ClrTypeDef(0x00100001, h.strings.add("Program"), h.strings.add("App"), 0, 1, 1),
            ),
            methodDefs = listOf(
                ClrMethodDefinition(0x2050, 0, 0x0096, h.strings.add("Main"), sig, 1),
            ),
            params = listOf(ClrParam(0, 1, h.strings.add("args"))),
            assemblies = listOf(ClrAssembly(0x8004, 1, 0, 0, 0, 0, 0, h.strings.add("App"), h.strings.add(""))),
            assemblyRefs = listOf(ClrAssemblyRef(4, 0, 0, 0, 0, 0, h.strings.add("mscorlib"), 0, 0)),
        )
        val parsed = buildAndParse(tables, h.strings.build(), h.blobs.build(), h.guids.build(), h.userStrings.build())
        assertEquals(1, parsed.tables.modules.size)
        assertEquals(2, parsed.tables.typeDefs.size)
        assertEquals(1, parsed.tables.methodDefs.size)
        assertEquals(1, parsed.tables.params.size)
        assertEquals(1, parsed.tables.assemblies.size)
        assertEquals(1, parsed.tables.assemblyRefs.size)
        assertEquals("Program", parsed.strings.get(parsed.tables.typeDefs[1].name))
        assertEquals("Main", parsed.strings.get(parsed.tables.methodDefs[0].name))
        assertEquals("args", parsed.strings.get(parsed.tables.params[0].name))
    }

    @Test
    fun `parser returns null for invalid signature`() {
        val data = ByteArray(100)
        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val parser = ClrTableParser(buf, data)
        val result = parser.parse(0, data.size, 2, 5, 0, 0)
        assertNull(result)
    }

    @Test
    fun `ClrCodedIndex TYPE_DEF_OR_REF has correct tables`() {
        val tables = ClrCodedIndex.TYPE_DEF_OR_REF
        assertEquals(3, tables.size)
        assertEquals(0x02, tables[0]) // TypeDef
        assertEquals(0x01, tables[1]) // TypeRef
        assertEquals(0x1B, tables[2]) // TypeSpec
    }

    @Test
    fun `ClrCodedIndex HAS_CONSTANT has correct tables`() {
        val tables = ClrCodedIndex.HAS_CONSTANT
        assertEquals(3, tables.size)
        assertEquals(0x04, tables[0]) // Field
        assertEquals(0x08, tables[1]) // Param
        assertEquals(0x17, tables[2]) // Property
    }

    @Test
    fun `ClrCodedIndex RESOLUTION_SCOPE has correct tables`() {
        val tables = ClrCodedIndex.RESOLUTION_SCOPE
        assertEquals(4, tables.size)
        assertEquals(0x00, tables[0]) // Module
        assertEquals(0x1A, tables[1]) // ModuleRef
        assertEquals(0x23, tables[2]) // AssemblyRef
        assertEquals(0x01, tables[3]) // TypeRef
    }

    @Test
    fun `ClrCodedIndex METHOD_DEF_OR_REF has correct tables`() {
        val tables = ClrCodedIndex.METHOD_DEF_OR_REF
        assertEquals(2, tables.size)
        assertEquals(0x06, tables[0]) // MethodDefinition
        assertEquals(0x0A, tables[1]) // MemberRef
    }

    @Test
    fun `ClrCodedIndex TYPE_OR_METHOD_DEF has correct tables`() {
        val tables = ClrCodedIndex.TYPE_OR_METHOD_DEF
        assertEquals(2, tables.size)
        assertEquals(0x02, tables[0]) // TypeDef
        assertEquals(0x06, tables[1]) // MethodDefinition
    }

    @Test
    fun `ClrCodedIndex MEMBER_REF_PARENT has correct tables`() {
        val tables = ClrCodedIndex.MEMBER_REF_PARENT
        assertEquals(5, tables.size)
        assertEquals(0x02, tables[0]) // TypeDef
        assertEquals(0x01, tables[1]) // TypeRef
        assertEquals(0x1A, tables[2]) // ModuleRef
        assertEquals(0x06, tables[3]) // MethodDefinition
        assertEquals(0x1B, tables[4]) // TypeSpec
    }

    @Test
    fun `ClrCodedIndex MEMBER_FORWARDED has correct tables`() {
        val tables = ClrCodedIndex.MEMBER_FORWARDED
        assertEquals(2, tables.size)
        assertEquals(0x04, tables[0]) // Field
        assertEquals(0x06, tables[1]) // MethodDefinition
    }

    @Test
    fun `ClrCodedIndex CUSTOM_ATTRIBUTE_TYPE has correct tables`() {
        val tables = ClrCodedIndex.CUSTOM_ATTRIBUTE_TYPE
        assertEquals(5, tables.size)
        assertEquals(0x06, tables[2]) // MethodDefinition
        assertEquals(0x0A, tables[3]) // MemberRef
    }

    @Test
    fun `ClrCodedIndex HAS_CUSTOM_ATTRIBUTE has many tables`() {
        val tables = ClrCodedIndex.HAS_CUSTOM_ATTRIBUTE
        assertTrue(tables.size > 10)
        assertEquals(0x06, tables[0]) // MethodDefinition
    }

    @Test
    fun `coded index encoding TypeDefOrRef with TypeDef`() {
        // Tag for TypeDef is 0, TypeRef is 1, TypeSpec is 2 (2-bit tag)
        val typeDefRow = 5
        val coded = (typeDefRow shl 2) or 0 // TypeDef tag=0
        assertEquals(20, coded)
        assertEquals(5, coded shr 2) // row
        assertEquals(0, coded and 3) // tag
    }

    @Test
    fun `coded index encoding TypeDefOrRef with TypeRef`() {
        val typeRefRow = 3
        val coded = (typeRefRow shl 2) or 1 // TypeRef tag=1
        assertEquals(13, coded)
        assertEquals(3, coded shr 2)
        assertEquals(1, coded and 3)
    }

    @Test
    fun `coded index encoding TypeDefOrRef with TypeSpec`() {
        val typeSpecRow = 7
        val coded = (typeSpecRow shl 2) or 2 // TypeSpec tag=2
        assertEquals(30, coded)
        assertEquals(7, coded shr 2)
        assertEquals(2, coded and 3)
    }

    @Test
    fun `coded index encoding ResolutionScope with AssemblyRef`() {
        val asmRefRow = 1
        val coded = (asmRefRow shl 2) or 2 // AssemblyRef tag=2
        assertEquals(6, coded)
        assertEquals(1, coded shr 2)
        assertEquals(2, coded and 3)
    }

    @Test
    fun `coded index encoding MemberRefParent with TypeRef`() {
        val row = 2
        val coded = (row shl 3) or 1 // TypeRef tag=1 (3-bit tag)
        assertEquals(17, coded)
        assertEquals(2, coded shr 3)
        assertEquals(1, coded and 7)
    }

    @Test
    fun `coded index encoding MethodDefinitionOrRef with MethodDefinition`() {
        val row = 10
        val coded = (row shl 1) or 0 // MethodDefinition tag=0 (1-bit tag)
        assertEquals(20, coded)
        assertEquals(10, coded shr 1)
        assertEquals(0, coded and 1)
    }

    @Test
    fun `coded index encoding TypeOrMethodDefinition with TypeDef`() {
        val row = 4
        val coded = (row shl 1) or 0 // TypeDef tag=0
        assertEquals(8, coded)
        assertEquals(4, coded shr 1)
        assertEquals(0, coded and 1)
    }

    @Test
    fun `coded index encoding TypeOrMethodDefinition with MethodDefinition`() {
        val row = 3
        val coded = (row shl 1) or 1 // MethodDefinition tag=1
        assertEquals(7, coded)
        assertEquals(3, coded shr 1)
        assertEquals(1, coded and 1)
    }

    @Test
    fun `ClrModule data class properties`() {
        val m = ClrModule(generation = 1, name = 5, mvid = 2, encId = 3, encBaseId = 4)
        assertEquals(1, m.generation)
        assertEquals(5, m.name)
        assertEquals(2, m.mvid)
        assertEquals(3, m.encId)
        assertEquals(4, m.encBaseId)
    }

    @Test
    fun `ClrTypeDef data class properties`() {
        val td = ClrTypeDef(flags = 0x100001, name = 1, namespace = 2, extends = 3, fieldList = 4, methodList = 5)
        assertEquals(0x100001, td.flags)
        assertEquals(1, td.name)
        assertEquals(2, td.namespace)
        assertEquals(3, td.extends)
        assertEquals(4, td.fieldList)
        assertEquals(5, td.methodList)
    }

    @Test
    fun `ClrMethodDefinition data class properties`() {
        val m = ClrMethodDefinition(rva = 0x2000, implFlags = 0x10, flags = 0x86, name = 1, signature = 2, paramList = 3)
        assertEquals(0x2000, m.rva)
        assertEquals(0x10, m.implFlags)
        assertEquals(0x86, m.flags)
        assertEquals(1, m.name)
        assertEquals(2, m.signature)
        assertEquals(3, m.paramList)
    }

    @Test
    fun `ClrAssembly data class properties`() {
        val a = ClrAssembly(
            hashAlgId = 0x8004, majorVersion = 1, minorVersion = 2,
            buildNumber = 3, revisionNumber = 4, flags = 5,
            publicKey = 6, name = 7, culture = 8,
        )
        assertEquals(0x8004, a.hashAlgId)
        assertEquals(1, a.majorVersion)
        assertEquals(2, a.minorVersion)
        assertEquals(3, a.buildNumber)
        assertEquals(4, a.revisionNumber)
        assertEquals(5, a.flags)
        assertEquals(6, a.publicKey)
        assertEquals(7, a.name)
        assertEquals(8, a.culture)
    }

    @Test
    fun `ClrAssemblyRef data class properties`() {
        val r = ClrAssemblyRef(
            majorVersion = 1, minorVersion = 2, buildNumber = 3, revisionNumber = 4,
            flags = 5, publicKeyOrToken = 6, name = 7, culture = 8, hashValue = 9,
        )
        assertEquals(1, r.majorVersion)
        assertEquals(2, r.minorVersion)
        assertEquals(3, r.buildNumber)
        assertEquals(4, r.revisionNumber)
        assertEquals(5, r.flags)
        assertEquals(6, r.publicKeyOrToken)
        assertEquals(7, r.name)
        assertEquals(8, r.culture)
        assertEquals(9, r.hashValue)
    }

    @Test
    fun `ClrField data class properties`() {
        val f = ClrField(flags = 0x0006, name = 1, signature = 2)
        assertEquals(0x0006, f.flags)
        assertEquals(1, f.name)
        assertEquals(2, f.signature)
    }

    @Test
    fun `ClrParam data class properties`() {
        val p = ClrParam(flags = 1, sequence = 2, name = 3)
        assertEquals(1, p.flags)
        assertEquals(2, p.sequence)
        assertEquals(3, p.name)
    }

    @Test
    fun `ClrInterfaceImpl data class properties`() {
        val i = ClrInterfaceImpl(classIndex = 1, interfaceIndex = 2)
        assertEquals(1, i.classIndex)
        assertEquals(2, i.interfaceIndex)
    }

    @Test
    fun `ClrMemberRef data class properties`() {
        val m = ClrMemberRef(classIndex = 1, name = 2, signature = 3)
        assertEquals(1, m.classIndex)
        assertEquals(2, m.name)
        assertEquals(3, m.signature)
    }

    @Test
    fun `ClrConstant data class properties`() {
        val c = ClrConstant(type = 0x08, parent = 5, value = 3)
        assertEquals(0x08, c.type)
        assertEquals(5, c.parent)
        assertEquals(3, c.value)
    }

    @Test
    fun `ClrCustomAttribute data class properties`() {
        val a = ClrCustomAttribute(parent = 1, type = 2, value = 3)
        assertEquals(1, a.parent)
        assertEquals(2, a.type)
        assertEquals(3, a.value)
    }

    @Test
    fun `ClrGenericParam data class properties`() {
        val g = ClrGenericParam(number = 0, flags = 0x10, owner = 4, name = 7)
        assertEquals(0, g.number)
        assertEquals(0x10, g.flags)
        assertEquals(4, g.owner)
        assertEquals(7, g.name)
    }

    @Test
    fun `ClrMethodSpec data class properties`() {
        val s = ClrMethodSpec(method = 5, instantiation = 3)
        assertEquals(5, s.method)
        assertEquals(3, s.instantiation)
    }

    @Test
    fun `ClrGenericParamConstraint data class properties`() {
        val c = ClrGenericParamConstraint(owner = 1, constraint = 7)
        assertEquals(1, c.owner)
        assertEquals(7, c.constraint)
    }

    @Test
    fun `ClrNestedClass data class properties`() {
        val n = ClrNestedClass(nestedClass = 3, enclosingClass = 2)
        assertEquals(3, n.nestedClass)
        assertEquals(2, n.enclosingClass)
    }

    @Test
    fun `ClrStandAloneSig data class properties`() {
        val s = ClrStandAloneSig(signature = 42)
        assertEquals(42, s.signature)
    }

    @Test
    fun `ClrClassLayout data class properties`() {
        val c = ClrClassLayout(packingSize = 4, classSize = 64, parent = 2)
        assertEquals(4, c.packingSize)
        assertEquals(64, c.classSize)
        assertEquals(2, c.parent)
    }

    @Test
    fun `ClrFieldLayout data class properties`() {
        val f = ClrFieldLayout(offset = 8, field = 3)
        assertEquals(8, f.offset)
        assertEquals(3, f.field)
    }

    @Test
    fun `ClrModuleRef data class properties`() {
        val r = ClrModuleRef(name = 5)
        assertEquals(5, r.name)
    }

    @Test
    fun `ClrTypeSpec data class properties`() {
        val t = ClrTypeSpec(signature = 10)
        assertEquals(10, t.signature)
    }

    @Test
    fun `ClrImplMap data class properties`() {
        val m = ClrImplMap(mappingFlags = 1, memberForwarded = 2, importName = 3, importScope = 4)
        assertEquals(1, m.mappingFlags)
        assertEquals(2, m.memberForwarded)
        assertEquals(3, m.importName)
        assertEquals(4, m.importScope)
    }

    @Test
    fun `ClrFieldRVA data class properties`() {
        val r = ClrFieldRVA(rva = 0x4000, field = 5)
        assertEquals(0x4000, r.rva)
        assertEquals(5, r.field)
    }

    @Test
    fun `ClrTypeRef data class properties`() {
        val tr = ClrTypeRef(resolutionScope = 6, name = 1, namespace = 2)
        assertEquals(6, tr.resolutionScope)
        assertEquals(1, tr.name)
        assertEquals(2, tr.namespace)
    }

    @Test
    fun `metadata COR_FLAGS constants have correct values`() {
        assertEquals(0x00000001, ClrMetadata.COR_FLAGS_ILONLY)
        assertEquals(0x00000002, ClrMetadata.COR_FLAGS_32BITREQUIRED)
        assertEquals(0x00000008, ClrMetadata.COR_FLAGS_STRONGNAMESIGNED)
        assertEquals(0x00000010, ClrMetadata.COR_FLAGS_NATIVE_ENTRYPOINT)
        assertEquals(0x00010000, ClrMetadata.COR_FLAGS_TRACKDEBUGDATA)
        assertEquals(0x00020000, ClrMetadata.COR_FLAGS_PREFER32BIT)
    }

    @Test
    fun `round-trip empty tables produces empty metadata`() {
        val parsed = buildAndParse(ClrTables())
        assertTrue(parsed.tables.modules.isEmpty())
        assertTrue(parsed.tables.typeDefs.isEmpty())
        assertTrue(parsed.tables.methodDefs.isEmpty())
    }

    @Test
    fun `round-trip full assembly with all table types`() {
        val h = makeHeaps()
        val mvid = h.guids.add(ByteArray(16) { it.toByte() })
        val sig = h.blobs.add(byteArrayOf(0x00, 0x00, 0x01))
        val fieldSig = h.blobs.add(byteArrayOf(0x06, 0x08))

        val tables = ClrTables(
            modules = listOf(ClrModule(0, h.strings.add("Full.dll"), mvid, 0, 0)),
            typeRefs = listOf(ClrTypeRef(6, h.strings.add("Object"), h.strings.add("System"))),
            typeDefs = listOf(
                ClrTypeDef(0, h.strings.add("<Module>"), h.strings.add(""), 0, 1, 1),
                ClrTypeDef(0x00100001, h.strings.add("MyClass"), h.strings.add("NS"), (1 shl 2) or 1, 1, 1),
            ),
            fields = listOf(ClrField(0x0006, h.strings.add("_val"), fieldSig)),
            methodDefs = listOf(ClrMethodDefinition(0x2050, 0, 0x0086, h.strings.add("Run"), sig, 1)),
            params = listOf(ClrParam(0, 1, h.strings.add("arg"))),
            memberRefs = listOf(ClrMemberRef((1 shl 3) or 1, h.strings.add(".ctor"), sig)),
            assemblies = listOf(ClrAssembly(0x8004, 1, 0, 0, 0, 0, 0, h.strings.add("Full"), h.strings.add(""))),
            assemblyRefs = listOf(ClrAssemblyRef(4, 0, 0, 0, 0, 0, h.strings.add("mscorlib"), 0, 0)),
            nestedClasses = emptyList(),
            genericParams = emptyList(),
        )

        val parsed = buildAndParse(tables, h.strings.build(), h.blobs.build(), h.guids.build(), h.userStrings.build())
        assertEquals(1, parsed.tables.modules.size)
        assertEquals(1, parsed.tables.typeRefs.size)
        assertEquals(2, parsed.tables.typeDefs.size)
        assertEquals(1, parsed.tables.fields.size)
        assertEquals(1, parsed.tables.methodDefs.size)
        assertEquals(1, parsed.tables.params.size)
        assertEquals(1, parsed.tables.memberRefs.size)
        assertEquals(1, parsed.tables.assemblies.size)
        assertEquals(1, parsed.tables.assemblyRefs.size)
    }

    @Test
    fun `ClrTableId enum entries count`() {
        assertTrue(ClrTableId.entries.size >= 20)
    }

    @Test
    fun `ClrTableId MODULE has id 0`() {
        assertEquals(0x00, ClrTableId.MODULE.id)
    }

    @Test
    fun `ClrTableId ASSEMBLY_REF has id 0x23`() {
        assertEquals(0x23, ClrTableId.ASSEMBLY_REF.id)
    }

    @Test
    fun `ClrTableId NESTED_CLASS has id 0x29`() {
        assertEquals(0x29, ClrTableId.NESTED_CLASS.id)
    }

    @Test
    fun `metadata data class equality`() {
        val meta1 = ClrMetadata(
            majorRuntimeVersion = 2, minorRuntimeVersion = 5,
            flags = 1, entryPointToken = 0,
            metadataVersion = "v4.0", tables = ClrTables(),
            strings = ClrStringHeapBuilder().build(), blobs = ClrBlobHeapBuilder().build(),
            guids = ClrGuidHeapBuilder().build(), userStrings = ClrUserStringHeapBuilder().build(),
        )
        val meta2 = meta1.copy()
        assertEquals(meta1, meta2)
    }

    @Test
    fun `multiple blobs with different sizes`() {
        val builder = ClrBlobHeapBuilder()
        val small = byteArrayOf(1)
        val medium = ByteArray(100) { it.toByte() }
        val large = ByteArray(500) { (it % 256).toByte() }
        val idx1 = builder.add(small)
        val idx2 = builder.add(medium)
        val idx3 = builder.add(large)
        val heap = builder.build()
        assertTrue(small.contentEquals(heap.get(idx1)))
        assertTrue(medium.contentEquals(heap.get(idx2)))
        assertTrue(large.contentEquals(heap.get(idx3)))
    }

    @Test
    fun `string heap multiple unique strings`() {
        val builder = ClrStringHeapBuilder()
        val indices = (1..20).map { builder.add("string_$it") }
        val heap = builder.build()
        for (i in indices.indices) {
            assertEquals("string_${i + 1}", heap.get(indices[i]))
        }
    }

    @Test
    fun `guid heap does not deduplicate`() {
        val builder = ClrGuidHeapBuilder()
        val guid = ByteArray(16) { 0xAA.toByte() }
        val idx1 = builder.add(guid)
        val idx2 = builder.add(guid)
        assertEquals(1, idx1)
        assertEquals(2, idx2) // no dedup: separate entries
    }

    @Test
    fun `user string heap special character flag`() {
        val builder = ClrUserStringHeapBuilder()
        // String with special character (hyphen triggers hasSpecial)
        val idx = builder.add("hello-world")
        val heap = builder.build()
        assertEquals("hello-world", heap.get(idx))
    }

    @Test
    fun `ClrCodedIndex HAS_SEMANTICS has correct tables`() {
        val tables = ClrCodedIndex.HAS_SEMANTICS
        assertEquals(2, tables.size)
        assertEquals(0x14, tables[0]) // Event
        assertEquals(0x17, tables[1]) // Property
    }

    @Test
    fun `ClrCodedIndex IMPLEMENTATION has correct tables`() {
        val tables = ClrCodedIndex.IMPLEMENTATION
        assertEquals(3, tables.size)
        assertEquals(0x26, tables[0]) // File
        assertEquals(0x23, tables[1]) // AssemblyRef
        assertEquals(0x27, tables[2]) // ExportedType
    }

    @Test
    fun `ClrCodedIndex HAS_FIELD_MARSHAL has correct tables`() {
        val tables = ClrCodedIndex.HAS_FIELD_MARSHAL
        assertEquals(2, tables.size)
        assertEquals(0x04, tables[0]) // Field
        assertEquals(0x08, tables[1]) // Param
    }

    @Test
    fun `ClrCodedIndex HAS_DECL_SECURITY has correct tables`() {
        val tables = ClrCodedIndex.HAS_DECL_SECURITY
        assertEquals(3, tables.size)
        assertEquals(0x02, tables[0]) // TypeDef
        assertEquals(0x06, tables[1]) // MethodDefinition
        assertEquals(0x20, tables[2]) // Assembly
    }
}
