package org.kgen.binary.pe.clr

import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith

class ClrWriterExtendedTest {

    private fun buildAndParse(
        strings: ClrStringHeapBuilder = ClrStringHeapBuilder(),
        blobs: ClrBlobHeapBuilder = ClrBlobHeapBuilder(),
        guids: ClrGuidHeapBuilder = ClrGuidHeapBuilder(),
        us: ClrUserStringHeapBuilder = ClrUserStringHeapBuilder(),
        tables: ClrTables,
        metadataVersion: String = "v4.0.30319",
        flags: Int = ClrMetadata.COR_FLAGS_ILONLY,
    ): ClrMetadata {
        val meta = ClrMetadata(
            majorRuntimeVersion = 2,
            minorRuntimeVersion = 5,
            flags = flags,
            entryPointToken = 0,
            metadataVersion = metadataVersion,
            tables = tables,
            strings = strings.build(),
            blobs = blobs.build(),
            guids = guids.build(),
            userStrings = us.build(),
        )
        val bytes = ClrTableWriter().write(meta)
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return ClrTableParser(buf, bytes).parse(0, bytes.size, 2, 5, flags, 0)!!
    }

    private fun minimalModule(strings: ClrStringHeapBuilder, guids: ClrGuidHeapBuilder): Pair<Int, Int> {
        val name = strings.add("Test.dll")
        val mvid = guids.add(ByteArray(16))
        return name to mvid
    }

    // --- String Heap Tests ---

    @Test
    fun stringHeapMultipleEntries() {
        val builder = ClrStringHeapBuilder()
        val idx1 = builder.add("Alpha")
        val idx2 = builder.add("Beta")
        val idx3 = builder.add("Gamma")
        val heap = builder.build()
        assertEquals("Alpha", heap.get(idx1))
        assertEquals("Beta", heap.get(idx2))
        assertEquals("Gamma", heap.get(idx3))
    }

    @Test
    fun stringHeapDeduplication() {
        val builder = ClrStringHeapBuilder()
        val idx1 = builder.add("Duplicate")
        val idx2 = builder.add("Other")
        val idx3 = builder.add("Duplicate")
        assertEquals(idx1, idx3)
        assertTrue(idx1 != idx2)
    }

    @Test
    fun stringHeapDifferentIndices() {
        val builder = ClrStringHeapBuilder()
        val indices = (1..10).map { builder.add("String$it") }
        val heap = builder.build()
        for ((i, idx) in indices.withIndex()) {
            assertEquals("String${i + 1}", heap.get(idx))
        }
        // All indices should be unique
        assertEquals(indices.size, indices.toSet().size)
    }

    // --- Blob Heap Tests ---

    @Test
    fun blobHeapEmptyBlob() {
        val builder = ClrBlobHeapBuilder()
        val idx = builder.add(byteArrayOf())
        val heap = builder.build()
        assertEquals(0, heap.get(idx).size)
    }

    @Test
    fun blobHeapLargeBlob() {
        val builder = ClrBlobHeapBuilder()
        val data = ByteArray(512) { (it % 256).toByte() }
        val idx = builder.add(data)
        val heap = builder.build()
        assertTrue(data.contentEquals(heap.get(idx)))
    }

    @Test
    fun blobHeapDeduplication() {
        val builder = ClrBlobHeapBuilder()
        val data = byteArrayOf(0x01, 0x02, 0x03)
        val idx1 = builder.add(data)
        val idx2 = builder.add(byteArrayOf(0x04, 0x05))
        val idx3 = builder.add(data)
        assertEquals(idx1, idx3)
        assertTrue(idx1 != idx2)
    }

    // --- GUID Heap Tests ---

    @Test
    fun guidHeapMultipleGuids() {
        val builder = ClrGuidHeapBuilder()
        val guid1 = ByteArray(16) { 0x01 }
        val guid2 = ByteArray(16) { 0x02 }
        val idx1 = builder.add(guid1)
        val idx2 = builder.add(guid2)
        assertEquals(1, idx1) // 1-based
        assertEquals(2, idx2)
        val heap = builder.build()
        assertTrue(guid1.contentEquals(heap.get(1)))
        assertTrue(guid2.contentEquals(heap.get(2)))
    }

    @Test
    fun guidHeapInvalidSizeThrows() {
        val builder = ClrGuidHeapBuilder()
        assertFailsWith<IllegalArgumentException> {
            builder.add(ByteArray(10))
        }
    }

    // --- User String Heap Tests ---

    @Test
    fun userStringHeapMultipleStrings() {
        val builder = ClrUserStringHeapBuilder()
        val idx1 = builder.add("Hello")
        val idx2 = builder.add("World")
        val heap = builder.build()
        assertEquals("Hello", heap.get(idx1))
        assertEquals("World", heap.get(idx2))
    }

    @Test
    fun userStringHeapEmptyString() {
        val builder = ClrUserStringHeapBuilder()
        val idx = builder.add("")
        val heap = builder.build()
        assertEquals("", heap.get(idx))
    }

    @Test
    fun userStringHeapUnicodeChars() {
        val builder = ClrUserStringHeapBuilder()
        val idx = builder.add("\u00C0\u00E9\u00FC")
        val heap = builder.build()
        assertEquals("\u00C0\u00E9\u00FC", heap.get(idx))
    }

    // --- Write/Read Module Only ---

    @Test
    fun writeReadModuleOnly() {
        val strings = ClrStringHeapBuilder()
        val guids = ClrGuidHeapBuilder()
        val (moduleName, mvid) = minimalModule(strings, guids)
        val tables = ClrTables(
            modules = listOf(ClrModule(0, moduleName, mvid, 0, 0)),
        )
        val parsed = buildAndParse(strings = strings, guids = guids, tables = tables)
        assertEquals(1, parsed.tables.modules.size)
        assertEquals("Test.dll", parsed.strings.get(parsed.tables.modules[0].name))
    }

    // --- BSJB Signature ---

    @Test
    fun bsjbSignaturePresent() {
        val strings = ClrStringHeapBuilder()
        val guids = ClrGuidHeapBuilder()
        val (moduleName, mvid) = minimalModule(strings, guids)
        val meta = ClrMetadata(
            majorRuntimeVersion = 2, minorRuntimeVersion = 5,
            flags = ClrMetadata.COR_FLAGS_ILONLY, entryPointToken = 0,
            metadataVersion = "v4.0.30319",
            tables = ClrTables(modules = listOf(ClrModule(0, moduleName, mvid, 0, 0))),
            strings = strings.build(), blobs = ClrBlobHeapBuilder().build(),
            guids = guids.build(), userStrings = ClrUserStringHeapBuilder().build(),
        )
        val bytes = ClrTableWriter().write(meta)
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(0x424A5342, buf.getInt(0))
    }

    // --- Multiple TypeDefs ---

    @Test
    fun multipleTypeDefs() {
        val strings = ClrStringHeapBuilder()
        val guids = ClrGuidHeapBuilder()
        val (moduleName, mvid) = minimalModule(strings, guids)
        val emptyNs = strings.add("")
        val tables = ClrTables(
            modules = listOf(ClrModule(0, moduleName, mvid, 0, 0)),
            typeDefs = listOf(
                ClrTypeDef(0, strings.add("<Module>"), emptyNs, 0, 1, 1),
                ClrTypeDef(0x00100001, strings.add("ClassA"), emptyNs, 0, 1, 1),
                ClrTypeDef(0x00100001, strings.add("ClassB"), emptyNs, 0, 1, 1),
                ClrTypeDef(0x00100001, strings.add("ClassC"), emptyNs, 0, 1, 1),
            ),
        )
        val parsed = buildAndParse(strings = strings, guids = guids, tables = tables)
        assertEquals(4, parsed.tables.typeDefs.size)
        assertEquals("ClassA", parsed.strings.get(parsed.tables.typeDefs[1].name))
        assertEquals("ClassB", parsed.strings.get(parsed.tables.typeDefs[2].name))
        assertEquals("ClassC", parsed.strings.get(parsed.tables.typeDefs[3].name))
    }

    // --- Multiple Assembly Refs ---

    @Test
    fun multipleAssemblyRefs() {
        val strings = ClrStringHeapBuilder()
        val guids = ClrGuidHeapBuilder()
        val (moduleName, mvid) = minimalModule(strings, guids)
        val tables = ClrTables(
            modules = listOf(ClrModule(0, moduleName, mvid, 0, 0)),
            assemblyRefs = listOf(
                ClrAssemblyRef(4, 0, 0, 0, 0, 0, strings.add("mscorlib"), 0, 0),
                ClrAssemblyRef(6, 0, 0, 0, 0, 0, strings.add("System.Core"), 0, 0),
            ),
        )
        val parsed = buildAndParse(strings = strings, guids = guids, tables = tables)
        assertEquals(2, parsed.tables.assemblyRefs.size)
        assertEquals("mscorlib", parsed.strings.get(parsed.tables.assemblyRefs[0].name))
        assertEquals("System.Core", parsed.strings.get(parsed.tables.assemblyRefs[1].name))
        assertEquals(4, parsed.tables.assemblyRefs[0].majorVersion)
        assertEquals(6, parsed.tables.assemblyRefs[1].majorVersion)
    }

    // --- Fields ---

    @Test
    fun fieldsRoundTrip() {
        val strings = ClrStringHeapBuilder()
        val blobs = ClrBlobHeapBuilder()
        val guids = ClrGuidHeapBuilder()
        val (moduleName, mvid) = minimalModule(strings, guids)
        val emptyNs = strings.add("")
        val fieldSig = blobs.add(byteArrayOf(0x06, 0x08)) // field sig for int32
        val tables = ClrTables(
            modules = listOf(ClrModule(0, moduleName, mvid, 0, 0)),
            typeDefs = listOf(
                ClrTypeDef(0, strings.add("<Module>"), emptyNs, 0, 1, 1),
                ClrTypeDef(0x00100001, strings.add("Foo"), emptyNs, 0, 1, 1),
            ),
            fields = listOf(
                ClrField(0x0006, strings.add("value"), fieldSig),
                ClrField(0x0001, strings.add("name"), fieldSig),
            ),
        )
        val parsed = buildAndParse(strings = strings, blobs = blobs, guids = guids, tables = tables)
        assertEquals(2, parsed.tables.fields.size)
        assertEquals("value", parsed.strings.get(parsed.tables.fields[0].name))
        assertEquals("name", parsed.strings.get(parsed.tables.fields[1].name))
    }

    // --- Params ---

    @Test
    fun paramsRoundTrip() {
        val strings = ClrStringHeapBuilder()
        val blobs = ClrBlobHeapBuilder()
        val guids = ClrGuidHeapBuilder()
        val (moduleName, mvid) = minimalModule(strings, guids)
        val emptyNs = strings.add("")
        val methodSig = blobs.add(byteArrayOf(0x00, 0x02, 0x01, 0x08, 0x08))
        val tables = ClrTables(
            modules = listOf(ClrModule(0, moduleName, mvid, 0, 0)),
            typeDefs = listOf(
                ClrTypeDef(0, strings.add("<Module>"), emptyNs, 0, 1, 1),
            ),
            methodDefs = listOf(
                ClrMethodDefinition(0, 0, 0x0086, strings.add("Add"), methodSig, 1),
            ),
            params = listOf(
                ClrParam(0, 1, strings.add("a")),
                ClrParam(0, 2, strings.add("b")),
            ),
        )
        val parsed = buildAndParse(strings = strings, blobs = blobs, guids = guids, tables = tables)
        assertEquals(2, parsed.tables.params.size)
        assertEquals("a", parsed.strings.get(parsed.tables.params[0].name))
        assertEquals("b", parsed.strings.get(parsed.tables.params[1].name))
        assertEquals(1, parsed.tables.params[0].sequence)
        assertEquals(2, parsed.tables.params[1].sequence)
    }

    // --- Interface Impl ---

    @Test
    fun interfaceImplRoundTrip() {
        val strings = ClrStringHeapBuilder()
        val guids = ClrGuidHeapBuilder()
        val (moduleName, mvid) = minimalModule(strings, guids)
        val emptyNs = strings.add("")
        val tables = ClrTables(
            modules = listOf(ClrModule(0, moduleName, mvid, 0, 0)),
            typeDefs = listOf(
                ClrTypeDef(0, strings.add("<Module>"), emptyNs, 0, 1, 1),
                ClrTypeDef(0x00100001, strings.add("Impl"), emptyNs, 0, 1, 1),
            ),
            typeRefs = listOf(
                ClrTypeRef(0, strings.add("IDisposable"), strings.add("System")),
            ),
            interfaceImpls = listOf(
                ClrInterfaceImpl(classIndex = 2, interfaceIndex = (1 shl 2) or 1),
            ),
        )
        val parsed = buildAndParse(strings = strings, guids = guids, tables = tables)
        assertEquals(1, parsed.tables.interfaceImpls.size)
        assertEquals(2, parsed.tables.interfaceImpls[0].classIndex)
    }

    // --- Multiple Generic Params ---

    @Test
    fun multipleGenericParams() {
        val strings = ClrStringHeapBuilder()
        val guids = ClrGuidHeapBuilder()
        val (moduleName, mvid) = minimalModule(strings, guids)
        val emptyNs = strings.add("")
        val tables = ClrTables(
            modules = listOf(ClrModule(0, moduleName, mvid, 0, 0)),
            typeDefs = listOf(
                ClrTypeDef(0, strings.add("<Module>"), emptyNs, 0, 1, 1),
                ClrTypeDef(0x00100001, strings.add("Pair"), emptyNs, 0, 1, 1),
            ),
            genericParams = listOf(
                ClrGenericParam(0, 0, (2 shl 1) or 0, strings.add("TKey")),
                ClrGenericParam(1, 0, (2 shl 1) or 0, strings.add("TValue")),
            ),
        )
        val parsed = buildAndParse(strings = strings, guids = guids, tables = tables)
        assertEquals(2, parsed.tables.genericParams.size)
        assertEquals("TKey", parsed.strings.get(parsed.tables.genericParams[0].name))
        assertEquals("TValue", parsed.strings.get(parsed.tables.genericParams[1].name))
        assertEquals(0, parsed.tables.genericParams[0].number)
        assertEquals(1, parsed.tables.genericParams[1].number)
    }

    // --- Multiple Nested Classes ---

    @Test
    fun multipleNestedClasses() {
        val strings = ClrStringHeapBuilder()
        val guids = ClrGuidHeapBuilder()
        val (moduleName, mvid) = minimalModule(strings, guids)
        val emptyNs = strings.add("")
        val tables = ClrTables(
            modules = listOf(ClrModule(0, moduleName, mvid, 0, 0)),
            typeDefs = listOf(
                ClrTypeDef(0, strings.add("<Module>"), emptyNs, 0, 1, 1),
                ClrTypeDef(0x00100001, strings.add("Outer"), emptyNs, 0, 1, 1),
                ClrTypeDef(0x00100002, strings.add("InnerA"), emptyNs, 0, 1, 1),
                ClrTypeDef(0x00100002, strings.add("InnerB"), emptyNs, 0, 1, 1),
            ),
            nestedClasses = listOf(
                ClrNestedClass(nestedClass = 3, enclosingClass = 2),
                ClrNestedClass(nestedClass = 4, enclosingClass = 2),
            ),
        )
        val parsed = buildAndParse(strings = strings, guids = guids, tables = tables)
        assertEquals(2, parsed.tables.nestedClasses.size)
        assertEquals(3, parsed.tables.nestedClasses[0].nestedClass)
        assertEquals(4, parsed.tables.nestedClasses[1].nestedClass)
        assertEquals(2, parsed.tables.nestedClasses[0].enclosingClass)
        assertEquals(2, parsed.tables.nestedClasses[1].enclosingClass)
    }

    // --- Metadata Version ---

    @Test
    fun metadataVersionPreserved() {
        val strings = ClrStringHeapBuilder()
        val guids = ClrGuidHeapBuilder()
        val (moduleName, mvid) = minimalModule(strings, guids)
        val tables = ClrTables(modules = listOf(ClrModule(0, moduleName, mvid, 0, 0)))
        val parsed = buildAndParse(strings = strings, guids = guids, tables = tables, metadataVersion = "v4.0.30319")
        assertEquals("v4.0.30319", parsed.metadataVersion)
    }

    // --- Multiple Method Defs ---

    @Test
    fun multipleMethodDefinitions() {
        val strings = ClrStringHeapBuilder()
        val blobs = ClrBlobHeapBuilder()
        val guids = ClrGuidHeapBuilder()
        val (moduleName, mvid) = minimalModule(strings, guids)
        val emptyNs = strings.add("")
        val voidSig = blobs.add(byteArrayOf(0x00, 0x00, 0x01))
        val tables = ClrTables(
            modules = listOf(ClrModule(0, moduleName, mvid, 0, 0)),
            typeDefs = listOf(
                ClrTypeDef(0, strings.add("<Module>"), emptyNs, 0, 1, 1),
            ),
            methodDefs = listOf(
                ClrMethodDefinition(0, 0, 0x0086, strings.add("MethodA"), voidSig, 1),
                ClrMethodDefinition(0, 0, 0x0086, strings.add("MethodB"), voidSig, 1),
                ClrMethodDefinition(0, 0, 0x0086, strings.add("MethodC"), voidSig, 1),
            ),
        )
        val parsed = buildAndParse(strings = strings, blobs = blobs, guids = guids, tables = tables)
        assertEquals(3, parsed.tables.methodDefs.size)
        assertEquals("MethodA", parsed.strings.get(parsed.tables.methodDefs[0].name))
        assertEquals("MethodB", parsed.strings.get(parsed.tables.methodDefs[1].name))
        assertEquals("MethodC", parsed.strings.get(parsed.tables.methodDefs[2].name))
    }

    // --- COR Flags ---

    @Test
    fun corFlagsILOnly() {
        val strings = ClrStringHeapBuilder()
        val guids = ClrGuidHeapBuilder()
        val (moduleName, mvid) = minimalModule(strings, guids)
        val tables = ClrTables(modules = listOf(ClrModule(0, moduleName, mvid, 0, 0)))
        val meta = ClrMetadata(
            majorRuntimeVersion = 2, minorRuntimeVersion = 5,
            flags = ClrMetadata.COR_FLAGS_ILONLY,
            entryPointToken = 0, metadataVersion = "v4.0.30319",
            tables = tables, strings = strings.build(),
            blobs = ClrBlobHeapBuilder().build(), guids = guids.build(),
            userStrings = ClrUserStringHeapBuilder().build(),
        )
        assertTrue(meta.isILOnly)
        assertFalse(meta.is32BitRequired)
        assertFalse(meta.isStrongNameSigned)
    }

    // --- Module Refs ---

    @Test
    fun moduleRefsRoundTrip() {
        val strings = ClrStringHeapBuilder()
        val guids = ClrGuidHeapBuilder()
        val (moduleName, mvid) = minimalModule(strings, guids)
        val tables = ClrTables(
            modules = listOf(ClrModule(0, moduleName, mvid, 0, 0)),
            moduleRefs = listOf(
                ClrModuleRef(name = strings.add("kernel32.dll")),
            ),
        )
        val parsed = buildAndParse(strings = strings, guids = guids, tables = tables)
        assertEquals(1, parsed.tables.moduleRefs.size)
        assertEquals("kernel32.dll", parsed.strings.get(parsed.tables.moduleRefs[0].name))
    }

    // --- Stand-Alone Sigs ---

    @Test
    fun standAloneSigsRoundTrip() {
        val strings = ClrStringHeapBuilder()
        val blobs = ClrBlobHeapBuilder()
        val guids = ClrGuidHeapBuilder()
        val (moduleName, mvid) = minimalModule(strings, guids)
        val sig = blobs.add(byteArrayOf(0x07, 0x01, 0x08))
        val tables = ClrTables(
            modules = listOf(ClrModule(0, moduleName, mvid, 0, 0)),
            standAloneSigs = listOf(ClrStandAloneSig(signature = sig)),
        )
        val parsed = buildAndParse(strings = strings, blobs = blobs, guids = guids, tables = tables)
        assertEquals(1, parsed.tables.standAloneSigs.size)
    }

    // --- Multiple Member Refs ---

    @Test
    fun multipleMemberRefs() {
        val strings = ClrStringHeapBuilder()
        val blobs = ClrBlobHeapBuilder()
        val guids = ClrGuidHeapBuilder()
        val (moduleName, mvid) = minimalModule(strings, guids)
        val emptyNs = strings.add("")
        val ctorSig = blobs.add(byteArrayOf(0x20, 0x00, 0x01))
        val tables = ClrTables(
            modules = listOf(ClrModule(0, moduleName, mvid, 0, 0)),
            typeDefs = listOf(ClrTypeDef(0, strings.add("<Module>"), emptyNs, 0, 1, 1)),
            typeRefs = listOf(
                ClrTypeRef(0, strings.add("Object"), strings.add("System")),
                ClrTypeRef(0, strings.add("Console"), strings.add("System")),
            ),
            memberRefs = listOf(
                ClrMemberRef((1 shl 3) or 1, strings.add(".ctor"), ctorSig),
                ClrMemberRef((2 shl 3) or 1, strings.add("WriteLine"), ctorSig),
            ),
        )
        val parsed = buildAndParse(strings = strings, blobs = blobs, guids = guids, tables = tables)
        assertEquals(2, parsed.tables.memberRefs.size)
        assertEquals(".ctor", parsed.strings.get(parsed.tables.memberRefs[0].name))
        assertEquals("WriteLine", parsed.strings.get(parsed.tables.memberRefs[1].name))
    }

    // --- Assembly with Version ---

    @Test
    fun assemblyWithVersion() {
        val strings = ClrStringHeapBuilder()
        val guids = ClrGuidHeapBuilder()
        val (moduleName, mvid) = minimalModule(strings, guids)
        val tables = ClrTables(
            modules = listOf(ClrModule(0, moduleName, mvid, 0, 0)),
            assemblies = listOf(ClrAssembly(
                hashAlgId = 0x8004,
                majorVersion = 2, minorVersion = 3,
                buildNumber = 4, revisionNumber = 5,
                flags = 0,
                publicKey = 0, name = strings.add("MyAssembly"), culture = strings.add(""),
            )),
        )
        val parsed = buildAndParse(strings = strings, guids = guids, tables = tables)
        assertEquals(1, parsed.tables.assemblies.size)
        val asm = parsed.tables.assemblies[0]
        assertEquals("MyAssembly", parsed.strings.get(asm.name))
        assertEquals(2, asm.majorVersion)
        assertEquals(3, asm.minorVersion)
        assertEquals(4, asm.buildNumber)
        assertEquals(5, asm.revisionNumber)
        assertEquals(0x8004, asm.hashAlgId)
    }

    // --- TypeSpec Round Trip ---

    @Test
    fun typeSpecRoundTrip() {
        val strings = ClrStringHeapBuilder()
        val blobs = ClrBlobHeapBuilder()
        val guids = ClrGuidHeapBuilder()
        val (moduleName, mvid) = minimalModule(strings, guids)
        val sig = blobs.add(byteArrayOf(0x15, 0x12, 0x01, 0x01, 0x08))
        val tables = ClrTables(
            modules = listOf(ClrModule(0, moduleName, mvid, 0, 0)),
            typeSpecs = listOf(ClrTypeSpec(signature = sig)),
        )
        val parsed = buildAndParse(strings = strings, blobs = blobs, guids = guids, tables = tables)
        assertEquals(1, parsed.tables.typeSpecs.size)
    }

    // --- Empty Tables ---

    @Test
    fun emptyTablesRoundTrip() {
        val strings = ClrStringHeapBuilder()
        val guids = ClrGuidHeapBuilder()
        val (moduleName, mvid) = minimalModule(strings, guids)
        val tables = ClrTables(
            modules = listOf(ClrModule(0, moduleName, mvid, 0, 0)),
        )
        val parsed = buildAndParse(strings = strings, guids = guids, tables = tables)
        assertTrue(parsed.tables.typeDefs.isEmpty())
        assertTrue(parsed.tables.fields.isEmpty())
        assertTrue(parsed.tables.methodDefs.isEmpty())
        assertTrue(parsed.tables.params.isEmpty())
        assertTrue(parsed.tables.genericParams.isEmpty())
        assertTrue(parsed.tables.nestedClasses.isEmpty())
    }

    // --- Method Spec ---

    @Test
    fun methodSpecRoundTrip() {
        val strings = ClrStringHeapBuilder()
        val blobs = ClrBlobHeapBuilder()
        val guids = ClrGuidHeapBuilder()
        val (moduleName, mvid) = minimalModule(strings, guids)
        val emptyNs = strings.add("")
        val methodSig = blobs.add(byteArrayOf(0x00, 0x00, 0x01))
        val instantiation = blobs.add(byteArrayOf(0x0A, 0x01, 0x08))
        val tables = ClrTables(
            modules = listOf(ClrModule(0, moduleName, mvid, 0, 0)),
            typeDefs = listOf(ClrTypeDef(0, strings.add("<Module>"), emptyNs, 0, 1, 1)),
            methodDefs = listOf(
                ClrMethodDefinition(0, 0, 0x0086, strings.add("GenericMethod"), methodSig, 1),
            ),
            methodSpecs = listOf(
                ClrMethodSpec(method = (1 shl 1) or 0, instantiation = instantiation),
            ),
        )
        val parsed = buildAndParse(strings = strings, blobs = blobs, guids = guids, tables = tables)
        assertEquals(1, parsed.tables.methodSpecs.size)
    }

    // --- Generic Param Constraint ---

    @Test
    fun genericParamConstraintRoundTrip() {
        val strings = ClrStringHeapBuilder()
        val guids = ClrGuidHeapBuilder()
        val (moduleName, mvid) = minimalModule(strings, guids)
        val emptyNs = strings.add("")
        val tables = ClrTables(
            modules = listOf(ClrModule(0, moduleName, mvid, 0, 0)),
            typeDefs = listOf(
                ClrTypeDef(0, strings.add("<Module>"), emptyNs, 0, 1, 1),
                ClrTypeDef(0x00100001, strings.add("Constrained"), emptyNs, 0, 1, 1),
            ),
            typeRefs = listOf(
                ClrTypeRef(0, strings.add("IComparable"), strings.add("System")),
            ),
            genericParams = listOf(
                ClrGenericParam(0, 0, (2 shl 1) or 0, strings.add("T")),
            ),
            genericParamConstraints = listOf(
                ClrGenericParamConstraint(owner = 1, constraint = (1 shl 2) or 1),
            ),
        )
        val parsed = buildAndParse(strings = strings, guids = guids, tables = tables)
        assertEquals(1, parsed.tables.genericParamConstraints.size)
        assertEquals(1, parsed.tables.genericParamConstraints[0].owner)
    }
}
