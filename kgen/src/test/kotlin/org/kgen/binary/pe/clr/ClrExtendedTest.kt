package org.kgen.binary.pe.clr

import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ClrExtendedTest {

    private fun buildAndParse(tables: ClrTables, strings: ClrStringHeapBuilder, blobs: ClrBlobHeapBuilder,
                              guids: ClrGuidHeapBuilder, us: ClrUserStringHeapBuilder): ClrMetadata {
        val meta = ClrMetadata(
            majorRuntimeVersion = 2, minorRuntimeVersion = 5,
            flags = ClrMetadata.COR_FLAGS_ILONLY,
            entryPointToken = 0,
            metadataVersion = "v4.0.30319",
            tables = tables,
            strings = strings.build(),
            blobs = blobs.build(),
            guids = guids.build(),
            userStrings = us.build(),
        )
        val bytes = ClrTableWriter().write(meta)
        assertTrue(bytes.isNotEmpty())
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(0x424A5342, buf.getInt(0))
        val parsed = ClrTableParser(buf, bytes).parse(0, bytes.size, 2, 5, meta.flags, 0)
        assertNotNull(parsed)
        return parsed
    }

    private fun newHeaps() = Heaps()

    private data class Heaps(
        val strings: ClrStringHeapBuilder = ClrStringHeapBuilder(),
        val blobs: ClrBlobHeapBuilder = ClrBlobHeapBuilder(),
        val guids: ClrGuidHeapBuilder = ClrGuidHeapBuilder(),
        val us: ClrUserStringHeapBuilder = ClrUserStringHeapBuilder(),
    )

    private fun Heaps.baseModule(): Pair<Int, Int> {
        val moduleName = strings.add("Test.dll")
        val mvid = guids.add(ByteArray(16))
        return moduleName to mvid
    }

    // --- String Heap Tests ---

    @Test
    fun stringHeapMultipleDistinctStrings() {
        val builder = ClrStringHeapBuilder()
        val indices = (1..10).map { builder.add("String$it") }
        val heap = builder.build()
        for ((i, idx) in indices.withIndex()) {
            assertEquals("String${i + 1}", heap.get(idx))
        }
        assertEquals(indices.toSet().size, 10, "All indices should be unique")
    }

    @Test
    fun stringHeapUtf8Characters() {
        val builder = ClrStringHeapBuilder()
        val idx = builder.add("Caf\u00e9")
        val heap = builder.build()
        assertEquals("Caf\u00e9", heap.get(idx))
    }

    @Test
    fun stringHeapOutOfBoundsReturnsEmpty() {
        val builder = ClrStringHeapBuilder()
        builder.add("test")
        val heap = builder.build()
        assertEquals("", heap.get(99999))
    }

    // --- Blob Heap Tests ---

    @Test
    fun blobHeapEmptyBlobReturnsZero() {
        val builder = ClrBlobHeapBuilder()
        val idx = builder.add(ByteArray(0))
        assertEquals(0, idx)
        val heap = builder.build()
        assertTrue(heap.get(0).isEmpty())
    }

    @Test
    fun blobHeapLargeBlob() {
        val builder = ClrBlobHeapBuilder()
        val data = ByteArray(300) { (it % 256).toByte() }
        val idx = builder.add(data)
        val heap = builder.build()
        assertTrue(data.contentEquals(heap.get(idx)))
    }

    @Test
    fun blobHeapMultipleBlobs() {
        val builder = ClrBlobHeapBuilder()
        val blobs = (1..5).map { size -> ByteArray(size * 10) { (it + size).toByte() } }
        val indices = blobs.map { builder.add(it) }
        val heap = builder.build()
        for ((i, idx) in indices.withIndex()) {
            assertTrue(blobs[i].contentEquals(heap.get(idx)))
        }
    }

    // --- GUID Heap Tests ---

    @Test
    fun guidHeapMultipleGuids() {
        val builder = ClrGuidHeapBuilder()
        val guid1 = ByteArray(16) { 0xAA.toByte() }
        val guid2 = ByteArray(16) { 0xBB.toByte() }
        val idx1 = builder.add(guid1)
        val idx2 = builder.add(guid2)
        assertEquals(1, idx1)
        assertEquals(2, idx2)
        val heap = builder.build()
        assertTrue(guid1.contentEquals(heap.get(1)))
        assertTrue(guid2.contentEquals(heap.get(2)))
    }

    @Test
    fun guidHeapIndexZeroReturnsEmpty() {
        val builder = ClrGuidHeapBuilder()
        builder.add(ByteArray(16))
        val heap = builder.build()
        val empty = heap.get(0)
        assertEquals(16, empty.size)
        assertTrue(empty.all { it == 0.toByte() })
    }

    // --- User String Heap Tests ---

    @Test
    fun userStringHeapEmptyReturnsZero() {
        val builder = ClrUserStringHeapBuilder()
        val idx = builder.add("")
        assertEquals(0, idx)
    }

    @Test
    fun userStringHeapSpecialChars() {
        val builder = ClrUserStringHeapBuilder()
        val idx = builder.add("Hello\u00e9World")
        val heap = builder.build()
        assertEquals("Hello\u00e9World", heap.get(idx))
    }

    @Test
    fun userStringHeapMultipleStrings() {
        val builder = ClrUserStringHeapBuilder()
        val idx1 = builder.add("First")
        val idx2 = builder.add("Second")
        val heap = builder.build()
        assertEquals("First", heap.get(idx1))
        assertEquals("Second", heap.get(idx2))
    }

    // --- Module Table Tests ---

    @Test
    fun moduleTableRoundTrip() {
        val h = newHeaps()
        val (modName, mvid) = h.baseModule()
        val tables = ClrTables(modules = listOf(ClrModule(0, modName, mvid, 0, 0)))
        val parsed = buildAndParse(tables, h.strings, h.blobs, h.guids, h.us)
        assertEquals(1, parsed.tables.modules.size)
        assertEquals("Test.dll", parsed.strings.get(parsed.tables.modules[0].name))
        assertEquals(0, parsed.tables.modules[0].generation)
    }

    // --- Assembly Tests ---

    @Test
    fun assemblyWithFullVersionRoundTrip() {
        val h = newHeaps()
        val (modName, mvid) = h.baseModule()
        val asmName = h.strings.add("MyAssembly")
        val culture = h.strings.add("en-US")
        val tables = ClrTables(
            modules = listOf(ClrModule(0, modName, mvid, 0, 0)),
            assemblies = listOf(ClrAssembly(
                hashAlgId = 0x8004, majorVersion = 3, minorVersion = 2,
                buildNumber = 1, revisionNumber = 42, flags = 0,
                publicKey = 0, name = asmName, culture = culture,
            )),
        )
        val parsed = buildAndParse(tables, h.strings, h.blobs, h.guids, h.us)
        val asm = parsed.tables.assemblies[0]
        assertEquals("MyAssembly", parsed.strings.get(asm.name))
        assertEquals("en-US", parsed.strings.get(asm.culture))
        assertEquals(3, asm.majorVersion)
        assertEquals(2, asm.minorVersion)
        assertEquals(1, asm.buildNumber)
        assertEquals(42, asm.revisionNumber)
    }

    @Test
    fun multipleAssemblyRefsRoundTrip() {
        val h = newHeaps()
        val (modName, mvid) = h.baseModule()
        val refs = listOf(
            ClrAssemblyRef(4, 0, 0, 0, 0, 0, h.strings.add("mscorlib"), 0, 0),
            ClrAssemblyRef(5, 0, 0, 0, 0, 0, h.strings.add("System.Core"), 0, 0),
            ClrAssemblyRef(6, 1, 0, 0, 0, 0, h.strings.add("System.Linq"), 0, 0),
        )
        val tables = ClrTables(
            modules = listOf(ClrModule(0, modName, mvid, 0, 0)),
            assemblyRefs = refs,
        )
        val parsed = buildAndParse(tables, h.strings, h.blobs, h.guids, h.us)
        assertEquals(3, parsed.tables.assemblyRefs.size)
        assertEquals("mscorlib", parsed.strings.get(parsed.tables.assemblyRefs[0].name))
        assertEquals("System.Core", parsed.strings.get(parsed.tables.assemblyRefs[1].name))
        assertEquals("System.Linq", parsed.strings.get(parsed.tables.assemblyRefs[2].name))
        assertEquals(4, parsed.tables.assemblyRefs[0].majorVersion)
        assertEquals(5, parsed.tables.assemblyRefs[1].majorVersion)
        assertEquals(6, parsed.tables.assemblyRefs[2].majorVersion)
    }

    // --- TypeDef Tests ---

    @Test
    fun multipleTypeDefsRoundTrip() {
        val h = newHeaps()
        val (modName, mvid) = h.baseModule()
        val emptyNs = h.strings.add("")
        val ns = h.strings.add("MyApp")
        val tables = ClrTables(
            modules = listOf(ClrModule(0, modName, mvid, 0, 0)),
            typeDefs = listOf(
                ClrTypeDef(0, h.strings.add("<Module>"), emptyNs, 0, 1, 1),
                ClrTypeDef(0x00100001, h.strings.add("ClassA"), ns, 0, 1, 1),
                ClrTypeDef(0x00100001, h.strings.add("ClassB"), ns, 0, 1, 1),
                ClrTypeDef(0x00100001, h.strings.add("ClassC"), ns, 0, 1, 1),
            ),
        )
        val parsed = buildAndParse(tables, h.strings, h.blobs, h.guids, h.us)
        assertEquals(4, parsed.tables.typeDefs.size)
        assertEquals("<Module>", parsed.strings.get(parsed.tables.typeDefs[0].name))
        assertEquals("ClassA", parsed.strings.get(parsed.tables.typeDefs[1].name))
        assertEquals("ClassB", parsed.strings.get(parsed.tables.typeDefs[2].name))
        assertEquals("ClassC", parsed.strings.get(parsed.tables.typeDefs[3].name))
        for (i in 1..3) {
            assertEquals("MyApp", parsed.strings.get(parsed.tables.typeDefs[i].namespace))
        }
    }

    @Test
    fun typeDefFlagsPreserved() {
        val h = newHeaps()
        val (modName, mvid) = h.baseModule()
        val emptyNs = h.strings.add("")
        val flags = 0x00100101 // public | sealed | class
        val tables = ClrTables(
            modules = listOf(ClrModule(0, modName, mvid, 0, 0)),
            typeDefs = listOf(
                ClrTypeDef(0, h.strings.add("<Module>"), emptyNs, 0, 1, 1),
                ClrTypeDef(flags, h.strings.add("SealedClass"), emptyNs, 0, 1, 1),
            ),
        )
        val parsed = buildAndParse(tables, h.strings, h.blobs, h.guids, h.us)
        assertEquals(flags, parsed.tables.typeDefs[1].flags)
    }

    // --- TypeRef Tests ---

    @Test
    fun multipleTypeRefsWithDifferentScopes() {
        val h = newHeaps()
        val (modName, mvid) = h.baseModule()
        val asmRef1 = ClrAssemblyRef(4, 0, 0, 0, 0, 0, h.strings.add("mscorlib"), 0, 0)
        val asmRef2 = ClrAssemblyRef(5, 0, 0, 0, 0, 0, h.strings.add("System.Runtime"), 0, 0)
        val sysNs = h.strings.add("System")
        val tables = ClrTables(
            modules = listOf(ClrModule(0, modName, mvid, 0, 0)),
            typeRefs = listOf(
                ClrTypeRef((1 shl 2) or 3, h.strings.add("Object"), sysNs),
                ClrTypeRef((1 shl 2) or 3, h.strings.add("String"), sysNs),
                ClrTypeRef((2 shl 2) or 3, h.strings.add("Console"), sysNs),
            ),
            assemblyRefs = listOf(asmRef1, asmRef2),
        )
        val parsed = buildAndParse(tables, h.strings, h.blobs, h.guids, h.us)
        assertEquals(3, parsed.tables.typeRefs.size)
        assertEquals("Object", parsed.strings.get(parsed.tables.typeRefs[0].name))
        assertEquals("String", parsed.strings.get(parsed.tables.typeRefs[1].name))
        assertEquals("Console", parsed.strings.get(parsed.tables.typeRefs[2].name))
    }

    // --- MethodDefinition Tests ---

    @Test
    fun multipleMethodDefinitionsRoundTrip() {
        val h = newHeaps()
        val (modName, mvid) = h.baseModule()
        val emptyNs = h.strings.add("")
        val sig1 = h.blobs.add(byteArrayOf(0x00, 0x00, 0x01))
        val sig2 = h.blobs.add(byteArrayOf(0x00, 0x01, 0x08))
        val tables = ClrTables(
            modules = listOf(ClrModule(0, modName, mvid, 0, 0)),
            typeDefs = listOf(
                ClrTypeDef(0, h.strings.add("<Module>"), emptyNs, 0, 1, 1),
                ClrTypeDef(0x00100001, h.strings.add("MyClass"), emptyNs, 0, 1, 1),
            ),
            methodDefs = listOf(
                ClrMethodDefinition(0, 0, 0x0086, h.strings.add("Method1"), sig1, 1),
                ClrMethodDefinition(0, 0, 0x0086, h.strings.add("Method2"), sig2, 1),
                ClrMethodDefinition(0, 0, 0x0091, h.strings.add("Method3"), sig1, 1),
            ),
        )
        val parsed = buildAndParse(tables, h.strings, h.blobs, h.guids, h.us)
        assertEquals(3, parsed.tables.methodDefs.size)
        assertEquals("Method1", parsed.strings.get(parsed.tables.methodDefs[0].name))
        assertEquals("Method2", parsed.strings.get(parsed.tables.methodDefs[1].name))
        assertEquals("Method3", parsed.strings.get(parsed.tables.methodDefs[2].name))
        assertEquals(0x0091, parsed.tables.methodDefs[2].flags)
    }

    // --- Field Tests ---

    @Test
    fun fieldDefsRoundTrip() {
        val h = newHeaps()
        val (modName, mvid) = h.baseModule()
        val emptyNs = h.strings.add("")
        val fieldSig = h.blobs.add(byteArrayOf(0x06, 0x08))
        val tables = ClrTables(
            modules = listOf(ClrModule(0, modName, mvid, 0, 0)),
            typeDefs = listOf(
                ClrTypeDef(0, h.strings.add("<Module>"), emptyNs, 0, 1, 1),
                ClrTypeDef(0x00100001, h.strings.add("MyClass"), emptyNs, 0, 1, 1),
            ),
            fields = listOf(
                ClrField(0x0006, h.strings.add("myField"), fieldSig),
                ClrField(0x0016, h.strings.add("myStaticField"), fieldSig),
            ),
        )
        val parsed = buildAndParse(tables, h.strings, h.blobs, h.guids, h.us)
        assertEquals(2, parsed.tables.fields.size)
        assertEquals("myField", parsed.strings.get(parsed.tables.fields[0].name))
        assertEquals("myStaticField", parsed.strings.get(parsed.tables.fields[1].name))
        assertEquals(0x0006, parsed.tables.fields[0].flags)
        assertEquals(0x0016, parsed.tables.fields[1].flags)
    }

    // --- Param Tests ---

    @Test
    fun paramDefsRoundTrip() {
        val h = newHeaps()
        val (modName, mvid) = h.baseModule()
        val emptyNs = h.strings.add("")
        val sig = h.blobs.add(byteArrayOf(0x00, 0x02, 0x08, 0x08))
        val tables = ClrTables(
            modules = listOf(ClrModule(0, modName, mvid, 0, 0)),
            typeDefs = listOf(ClrTypeDef(0, h.strings.add("<Module>"), emptyNs, 0, 1, 1)),
            methodDefs = listOf(ClrMethodDefinition(0, 0, 0x0086, h.strings.add("Add"), sig, 1)),
            params = listOf(
                ClrParam(0, 1, h.strings.add("a")),
                ClrParam(0, 2, h.strings.add("b")),
            ),
        )
        val parsed = buildAndParse(tables, h.strings, h.blobs, h.guids, h.us)
        assertEquals(2, parsed.tables.params.size)
        assertEquals("a", parsed.strings.get(parsed.tables.params[0].name))
        assertEquals("b", parsed.strings.get(parsed.tables.params[1].name))
        assertEquals(1, parsed.tables.params[0].sequence)
        assertEquals(2, parsed.tables.params[1].sequence)
    }

    // --- InterfaceImpl Tests ---

    @Test
    fun interfaceImplRoundTrip() {
        val h = newHeaps()
        val (modName, mvid) = h.baseModule()
        val emptyNs = h.strings.add("")
        val tables = ClrTables(
            modules = listOf(ClrModule(0, modName, mvid, 0, 0)),
            typeDefs = listOf(
                ClrTypeDef(0, h.strings.add("<Module>"), emptyNs, 0, 1, 1),
                ClrTypeDef(0x00100001, h.strings.add("MyClass"), emptyNs, 0, 1, 1),
            ),
            typeRefs = listOf(
                ClrTypeRef(0, h.strings.add("IDisposable"), h.strings.add("System")),
            ),
            interfaceImpls = listOf(
                ClrInterfaceImpl(
                    classIndex = 2,
                    interfaceIndex = (1 shl 2) or 1, // TypeRef #1, tag=1
                ),
            ),
        )
        val parsed = buildAndParse(tables, h.strings, h.blobs, h.guids, h.us)
        assertEquals(1, parsed.tables.interfaceImpls.size)
        assertEquals(2, parsed.tables.interfaceImpls[0].classIndex)
    }

    // --- MemberRef Tests ---

    @Test
    fun multipleMemberRefsRoundTrip() {
        val h = newHeaps()
        val (modName, mvid) = h.baseModule()
        val emptyNs = h.strings.add("")
        val ctorSig = h.blobs.add(byteArrayOf(0x20, 0x00, 0x01))
        val methodSig = h.blobs.add(byteArrayOf(0x20, 0x01, 0x01, 0x08))
        val tables = ClrTables(
            modules = listOf(ClrModule(0, modName, mvid, 0, 0)),
            typeDefs = listOf(ClrTypeDef(0, h.strings.add("<Module>"), emptyNs, 0, 1, 1)),
            typeRefs = listOf(ClrTypeRef(0, h.strings.add("Object"), h.strings.add("System"))),
            memberRefs = listOf(
                ClrMemberRef((1 shl 3) or 1, h.strings.add(".ctor"), ctorSig),
                ClrMemberRef((1 shl 3) or 1, h.strings.add("ToString"), methodSig),
            ),
        )
        val parsed = buildAndParse(tables, h.strings, h.blobs, h.guids, h.us)
        assertEquals(2, parsed.tables.memberRefs.size)
        assertEquals(".ctor", parsed.strings.get(parsed.tables.memberRefs[0].name))
        assertEquals("ToString", parsed.strings.get(parsed.tables.memberRefs[1].name))
    }

    // --- Custom Attribute Tests ---

    @Test
    fun customAttributeRoundTrip() {
        val h = newHeaps()
        val (modName, mvid) = h.baseModule()
        val emptyNs = h.strings.add("")
        val ctorSig = h.blobs.add(byteArrayOf(0x20, 0x00, 0x01))
        val attrValue = h.blobs.add(byteArrayOf(0x01, 0x00, 0x00, 0x00))
        val tables = ClrTables(
            modules = listOf(ClrModule(0, modName, mvid, 0, 0)),
            typeDefs = listOf(
                ClrTypeDef(0, h.strings.add("<Module>"), emptyNs, 0, 1, 1),
                ClrTypeDef(0x00100001, h.strings.add("MyClass"), emptyNs, 0, 1, 1),
            ),
            typeRefs = listOf(ClrTypeRef(0, h.strings.add("ObsoleteAttribute"), h.strings.add("System"))),
            memberRefs = listOf(ClrMemberRef((1 shl 3) or 1, h.strings.add(".ctor"), ctorSig)),
            customAttributes = listOf(
                ClrCustomAttribute(
                    parent = (2 shl 5) or 3, // TypeDef #2, tag=3 (but HAS_CUSTOM_ATTRIBUTE has 5 tag bits)
                    type = (1 shl 3) or 3,   // MemberRef #1, tag=3
                    value = attrValue,
                ),
            ),
        )
        val parsed = buildAndParse(tables, h.strings, h.blobs, h.guids, h.us)
        assertEquals(1, parsed.tables.customAttributes.size)
    }

    // --- Constant Tests ---

    @Test
    fun constantRoundTrip() {
        val h = newHeaps()
        val (modName, mvid) = h.baseModule()
        val emptyNs = h.strings.add("")
        val fieldSig = h.blobs.add(byteArrayOf(0x06, 0x08))
        val constValue = h.blobs.add(byteArrayOf(0x2A, 0x00, 0x00, 0x00))
        val tables = ClrTables(
            modules = listOf(ClrModule(0, modName, mvid, 0, 0)),
            typeDefs = listOf(
                ClrTypeDef(0, h.strings.add("<Module>"), emptyNs, 0, 1, 1),
                ClrTypeDef(0x00100001, h.strings.add("MyClass"), emptyNs, 0, 1, 1),
            ),
            fields = listOf(ClrField(0x8056, h.strings.add("MY_CONST"), fieldSig)),
            constants = listOf(
                ClrConstant(
                    type = 0x08, // ELEMENT_TYPE_I4
                    parent = (1 shl 2) or 0, // Field #1, tag=0
                    value = constValue,
                ),
            ),
        )
        val parsed = buildAndParse(tables, h.strings, h.blobs, h.guids, h.us)
        assertEquals(1, parsed.tables.constants.size)
        assertEquals(0x08, parsed.tables.constants[0].type)
    }

    // --- StandAloneSig Tests ---

    @Test
    fun standAloneSigRoundTrip() {
        val h = newHeaps()
        val (modName, mvid) = h.baseModule()
        val localSig = h.blobs.add(byteArrayOf(0x07, 0x01, 0x08))
        val tables = ClrTables(
            modules = listOf(ClrModule(0, modName, mvid, 0, 0)),
            standAloneSigs = listOf(ClrStandAloneSig(localSig)),
        )
        val parsed = buildAndParse(tables, h.strings, h.blobs, h.guids, h.us)
        assertEquals(1, parsed.tables.standAloneSigs.size)
        val sig = parsed.blobs.get(parsed.tables.standAloneSigs[0].signature)
        assertTrue(byteArrayOf(0x07, 0x01, 0x08).contentEquals(sig))
    }

    // --- ModuleRef Tests ---

    @Test
    fun moduleRefRoundTrip() {
        val h = newHeaps()
        val (modName, mvid) = h.baseModule()
        val tables = ClrTables(
            modules = listOf(ClrModule(0, modName, mvid, 0, 0)),
            moduleRefs = listOf(
                ClrModuleRef(h.strings.add("kernel32.dll")),
                ClrModuleRef(h.strings.add("user32.dll")),
            ),
        )
        val parsed = buildAndParse(tables, h.strings, h.blobs, h.guids, h.us)
        assertEquals(2, parsed.tables.moduleRefs.size)
        assertEquals("kernel32.dll", parsed.strings.get(parsed.tables.moduleRefs[0].name))
        assertEquals("user32.dll", parsed.strings.get(parsed.tables.moduleRefs[1].name))
    }

    // --- TypeSpec Tests ---

    @Test
    fun typeSpecRoundTrip() {
        val h = newHeaps()
        val (modName, mvid) = h.baseModule()
        val genInstSig = h.blobs.add(byteArrayOf(0x15, 0x12, 0x01, 0x01, 0x08))
        val tables = ClrTables(
            modules = listOf(ClrModule(0, modName, mvid, 0, 0)),
            typeSpecs = listOf(ClrTypeSpec(genInstSig)),
        )
        val parsed = buildAndParse(tables, h.strings, h.blobs, h.guids, h.us)
        assertEquals(1, parsed.tables.typeSpecs.size)
        val sig = parsed.blobs.get(parsed.tables.typeSpecs[0].signature)
        assertTrue(byteArrayOf(0x15, 0x12, 0x01, 0x01, 0x08).contentEquals(sig))
    }

    // --- ImplMap (P/Invoke) Tests ---

    @Test
    fun implMapRoundTrip() {
        val h = newHeaps()
        val (modName, mvid) = h.baseModule()
        val emptyNs = h.strings.add("")
        val sig = h.blobs.add(byteArrayOf(0x00, 0x01, 0x08))
        val tables = ClrTables(
            modules = listOf(ClrModule(0, modName, mvid, 0, 0)),
            typeDefs = listOf(ClrTypeDef(0, h.strings.add("<Module>"), emptyNs, 0, 1, 1)),
            methodDefs = listOf(ClrMethodDefinition(0, 0, 0x0086, h.strings.add("MessageBoxA"), sig, 1)),
            moduleRefs = listOf(ClrModuleRef(h.strings.add("user32.dll"))),
            implMaps = listOf(
                ClrImplMap(
                    mappingFlags = 0x0001, // CharSetNotSpec | NoMangle
                    memberForwarded = (1 shl 1) or 1, // MethodDefinition #1, tag=1
                    importName = h.strings.add("MessageBoxA"),
                    importScope = 1,
                ),
            ),
        )
        val parsed = buildAndParse(tables, h.strings, h.blobs, h.guids, h.us)
        assertEquals(1, parsed.tables.implMaps.size)
        assertEquals("MessageBoxA", parsed.strings.get(parsed.tables.implMaps[0].importName))
        assertEquals(0x0001, parsed.tables.implMaps[0].mappingFlags)
    }

    // --- MethodSpec Tests ---

    @Test
    fun methodSpecRoundTrip() {
        val h = newHeaps()
        val (modName, mvid) = h.baseModule()
        val emptyNs = h.strings.add("")
        val methodSig = h.blobs.add(byteArrayOf(0x10, 0x01, 0x00, 0x01))
        val instSig = h.blobs.add(byteArrayOf(0x0A, 0x01, 0x08))
        val tables = ClrTables(
            modules = listOf(ClrModule(0, modName, mvid, 0, 0)),
            typeDefs = listOf(ClrTypeDef(0, h.strings.add("<Module>"), emptyNs, 0, 1, 1)),
            methodDefs = listOf(ClrMethodDefinition(0, 0, 0x0086, h.strings.add("GenericMethod"), methodSig, 1)),
            methodSpecs = listOf(
                ClrMethodSpec(
                    method = (1 shl 1) or 0, // MethodDefinition #1, tag=0
                    instantiation = instSig,
                ),
            ),
        )
        val parsed = buildAndParse(tables, h.strings, h.blobs, h.guids, h.us)
        assertEquals(1, parsed.tables.methodSpecs.size)
    }

    // --- GenericParamConstraint Tests ---

    @Test
    fun genericParamConstraintRoundTrip() {
        val h = newHeaps()
        val (modName, mvid) = h.baseModule()
        val emptyNs = h.strings.add("")
        val tables = ClrTables(
            modules = listOf(ClrModule(0, modName, mvid, 0, 0)),
            typeDefs = listOf(
                ClrTypeDef(0, h.strings.add("<Module>"), emptyNs, 0, 1, 1),
                ClrTypeDef(0x00100001, h.strings.add("Container"), emptyNs, 0, 1, 1),
            ),
            typeRefs = listOf(ClrTypeRef(0, h.strings.add("IComparable"), h.strings.add("System"))),
            genericParams = listOf(ClrGenericParam(0, 0, (2 shl 1) or 0, h.strings.add("T"))),
            genericParamConstraints = listOf(
                ClrGenericParamConstraint(
                    owner = 1,
                    constraint = (1 shl 2) or 1, // TypeRef #1, tag=1
                ),
            ),
        )
        val parsed = buildAndParse(tables, h.strings, h.blobs, h.guids, h.us)
        assertEquals(1, parsed.tables.genericParamConstraints.size)
        assertEquals(1, parsed.tables.genericParamConstraints[0].owner)
    }

    // --- Multiple Generic Params Tests ---

    @Test
    fun multipleGenericParamsRoundTrip() {
        val h = newHeaps()
        val (modName, mvid) = h.baseModule()
        val emptyNs = h.strings.add("")
        val tables = ClrTables(
            modules = listOf(ClrModule(0, modName, mvid, 0, 0)),
            typeDefs = listOf(
                ClrTypeDef(0, h.strings.add("<Module>"), emptyNs, 0, 1, 1),
                ClrTypeDef(0x00100001, h.strings.add("Dictionary"), emptyNs, 0, 1, 1),
            ),
            genericParams = listOf(
                ClrGenericParam(0, 0, (2 shl 1) or 0, h.strings.add("TKey")),
                ClrGenericParam(1, 0, (2 shl 1) or 0, h.strings.add("TValue")),
            ),
        )
        val parsed = buildAndParse(tables, h.strings, h.blobs, h.guids, h.us)
        assertEquals(2, parsed.tables.genericParams.size)
        assertEquals("TKey", parsed.strings.get(parsed.tables.genericParams[0].name))
        assertEquals("TValue", parsed.strings.get(parsed.tables.genericParams[1].name))
        assertEquals(0, parsed.tables.genericParams[0].number)
        assertEquals(1, parsed.tables.genericParams[1].number)
    }

    // --- ClassLayout Tests ---

    @Test
    fun classLayoutRoundTrip() {
        val h = newHeaps()
        val (modName, mvid) = h.baseModule()
        val emptyNs = h.strings.add("")
        val tables = ClrTables(
            modules = listOf(ClrModule(0, modName, mvid, 0, 0)),
            typeDefs = listOf(
                ClrTypeDef(0, h.strings.add("<Module>"), emptyNs, 0, 1, 1),
                ClrTypeDef(0x00100109, h.strings.add("MyStruct"), emptyNs, 0, 1, 1),
            ),
            classlayouts = listOf(ClrClassLayout(packingSize = 8, classSize = 32, parent = 2)),
        )
        val parsed = buildAndParse(tables, h.strings, h.blobs, h.guids, h.us)
        assertEquals(1, parsed.tables.classlayouts.size)
        assertEquals(8, parsed.tables.classlayouts[0].packingSize)
        assertEquals(32, parsed.tables.classlayouts[0].classSize)
        assertEquals(2, parsed.tables.classlayouts[0].parent)
    }

    // --- FieldLayout Tests ---

    @Test
    fun fieldLayoutRoundTrip() {
        val h = newHeaps()
        val (modName, mvid) = h.baseModule()
        val emptyNs = h.strings.add("")
        val fieldSig = h.blobs.add(byteArrayOf(0x06, 0x08))
        val tables = ClrTables(
            modules = listOf(ClrModule(0, modName, mvid, 0, 0)),
            typeDefs = listOf(
                ClrTypeDef(0, h.strings.add("<Module>"), emptyNs, 0, 1, 1),
                ClrTypeDef(0x00100109, h.strings.add("MyStruct"), emptyNs, 0, 1, 1),
            ),
            fields = listOf(
                ClrField(0x0006, h.strings.add("x"), fieldSig),
                ClrField(0x0006, h.strings.add("y"), fieldSig),
            ),
            fieldLayouts = listOf(
                ClrFieldLayout(offset = 0, field = 1),
                ClrFieldLayout(offset = 4, field = 2),
            ),
        )
        val parsed = buildAndParse(tables, h.strings, h.blobs, h.guids, h.us)
        assertEquals(2, parsed.tables.fieldLayouts.size)
        assertEquals(0, parsed.tables.fieldLayouts[0].offset)
        assertEquals(4, parsed.tables.fieldLayouts[1].offset)
    }

    // --- FieldRVA Tests ---

    @Test
    fun fieldRvaRoundTrip() {
        val h = newHeaps()
        val (modName, mvid) = h.baseModule()
        val emptyNs = h.strings.add("")
        val fieldSig = h.blobs.add(byteArrayOf(0x06, 0x08))
        val tables = ClrTables(
            modules = listOf(ClrModule(0, modName, mvid, 0, 0)),
            typeDefs = listOf(ClrTypeDef(0, h.strings.add("<Module>"), emptyNs, 0, 1, 1)),
            fields = listOf(ClrField(0x0056, h.strings.add("data"), fieldSig)),
            fieldRVAs = listOf(ClrFieldRVA(rva = 0x2050, field = 1)),
        )
        val parsed = buildAndParse(tables, h.strings, h.blobs, h.guids, h.us)
        assertEquals(1, parsed.tables.fieldRVAs.size)
        assertEquals(0x2050, parsed.tables.fieldRVAs[0].rva)
        assertEquals(1, parsed.tables.fieldRVAs[0].field)
    }

    // --- Metadata Flags Tests ---

    @Test
    fun metadataFlagsPreserved() {
        val h = newHeaps()
        val (modName, mvid) = h.baseModule()
        val flags = ClrMetadata.COR_FLAGS_ILONLY or ClrMetadata.COR_FLAGS_STRONGNAMESIGNED
        val meta = ClrMetadata(
            majorRuntimeVersion = 2, minorRuntimeVersion = 5,
            flags = flags,
            entryPointToken = 0x06000001,
            metadataVersion = "v4.0.30319",
            tables = ClrTables(modules = listOf(ClrModule(0, modName, mvid, 0, 0))),
            strings = h.strings.build(),
            blobs = h.blobs.build(),
            guids = h.guids.build(),
            userStrings = h.us.build(),
        )
        assertTrue(meta.isILOnly)
        assertTrue(meta.isStrongNameSigned)
        assertTrue(!meta.is32BitRequired)
        assertTrue(!meta.isNativeEntryPoint)
    }

    // --- Comprehensive Round-Trip: All Table Types ---

    @Test
    fun comprehensiveRoundTrip() {
        val h = newHeaps()
        val (modName, mvid) = h.baseModule()
        val emptyNs = h.strings.add("")
        val sysNs = h.strings.add("System")
        val ctorSig = h.blobs.add(byteArrayOf(0x20, 0x00, 0x01))
        val fieldSig = h.blobs.add(byteArrayOf(0x06, 0x08))

        val tables = ClrTables(
            modules = listOf(ClrModule(0, modName, mvid, 0, 0)),
            assemblies = listOf(ClrAssembly(0x8004, 1, 0, 0, 0, 0, 0, h.strings.add("TestAsm"), 0)),
            assemblyRefs = listOf(ClrAssemblyRef(4, 0, 0, 0, 0, 0, h.strings.add("mscorlib"), 0, 0)),
            typeDefs = listOf(
                ClrTypeDef(0, h.strings.add("<Module>"), emptyNs, 0, 1, 1),
                ClrTypeDef(0x00100001, h.strings.add("Program"), emptyNs, 0, 1, 1),
            ),
            typeRefs = listOf(ClrTypeRef((1 shl 2) or 3, h.strings.add("Object"), sysNs)),
            methodDefs = listOf(ClrMethodDefinition(0, 0, 0x0096, h.strings.add("Main"), ctorSig, 1)),
            fields = listOf(ClrField(0x0016, h.strings.add("count"), fieldSig)),
            memberRefs = listOf(ClrMemberRef((1 shl 3) or 1, h.strings.add(".ctor"), ctorSig)),
            nestedClasses = emptyList(),
            genericParams = emptyList(),
        )

        val parsed = buildAndParse(tables, h.strings, h.blobs, h.guids, h.us)

        assertEquals(1, parsed.tables.modules.size)
        assertEquals(1, parsed.tables.assemblies.size)
        assertEquals(1, parsed.tables.assemblyRefs.size)
        assertEquals(2, parsed.tables.typeDefs.size)
        assertEquals(1, parsed.tables.typeRefs.size)
        assertEquals(1, parsed.tables.methodDefs.size)
        assertEquals(1, parsed.tables.fields.size)
        assertEquals(1, parsed.tables.memberRefs.size)

        assertEquals("Program", parsed.strings.get(parsed.tables.typeDefs[1].name))
        assertEquals("Main", parsed.strings.get(parsed.tables.methodDefs[0].name))
        assertEquals("count", parsed.strings.get(parsed.tables.fields[0].name))
    }
}
