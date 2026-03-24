package org.kgen.binary.pe.clr

import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ClrWriterTest {

    @Test
    fun stringHeapBuilderDeduplicates() {
        val builder = ClrStringHeapBuilder()
        val idx1 = builder.add("Hello")
        val idx2 = builder.add("World")
        val idx3 = builder.add("Hello")

        assertEquals(idx1, idx3)
        assertTrue(idx2 != idx1)

        val heap = builder.build()
        assertEquals("Hello", heap.get(idx1))
        assertEquals("World", heap.get(idx2))
    }

    @Test
    fun stringHeapEmptyString() {
        val builder = ClrStringHeapBuilder()
        val idx = builder.add("")
        assertEquals(0, idx)

        val heap = builder.build()
        assertEquals("", heap.get(0))
    }

    @Test
    fun blobHeapRoundTrip() {
        val builder = ClrBlobHeapBuilder()
        val data1 = byteArrayOf(0x01, 0x02, 0x03)
        val data2 = byteArrayOf(0x0A, 0x0B)

        val idx1 = builder.add(data1)
        val idx2 = builder.add(data2)
        val idx3 = builder.add(data1) // dedup

        assertEquals(idx1, idx3)
        assertTrue(idx2 != idx1)

        val heap = builder.build()
        assertTrue(data1.contentEquals(heap.get(idx1)))
        assertTrue(data2.contentEquals(heap.get(idx2)))
    }

    @Test
    fun guidHeapRoundTrip() {
        val builder = ClrGuidHeapBuilder()
        val guid = ByteArray(16) { it.toByte() }
        val idx = builder.add(guid)

        assertEquals(1, idx) // 1-based

        val heap = builder.build()
        assertTrue(guid.contentEquals(heap.get(1)))
    }

    @Test
    fun userStringHeapRoundTrip() {
        val builder = ClrUserStringHeapBuilder()
        val idx = builder.add("Hello")

        assertTrue(idx > 0)
        val heap = builder.build()
        assertEquals("Hello", heap.get(idx))
    }

    @Test
    fun writeAndReadSimpleMetadata() {
        val strings = ClrStringHeapBuilder()
        val blobs = ClrBlobHeapBuilder()
        val guids = ClrGuidHeapBuilder()
        val us = ClrUserStringHeapBuilder()

        val moduleName = strings.add("TestModule.dll")
        val mvid = guids.add(ByteArray(16) { (it + 1).toByte() })

        val asmName = strings.add("TestAssembly")
        val culture = strings.add("")

        val tables = ClrTables(
            modules = listOf(ClrModule(
                generation = 0, name = moduleName,
                mvid = mvid, encId = 0, encBaseId = 0,
            )),
            assemblies = listOf(ClrAssembly(
                hashAlgId = 0x8004, // SHA1
                majorVersion = 1, minorVersion = 0,
                buildNumber = 0, revisionNumber = 0,
                flags = 0,
                publicKey = 0, name = asmName, culture = culture,
            )),
        )

        val meta = ClrMetadata(
            majorRuntimeVersion = 2,
            minorRuntimeVersion = 5,
            flags = ClrMetadata.COR_FLAGS_ILONLY,
            entryPointToken = 0,
            metadataVersion = "v4.0.30319",
            tables = tables,
            strings = strings.build(),
            blobs = blobs.build(),
            guids = guids.build(),
            userStrings = us.build(),
        )

        val writer = ClrTableWriter()
        val bytes = writer.write(meta)
        assertTrue(bytes.isNotEmpty())

        // Verify BSJB signature
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(0x424A5342, buf.getInt(0))

        // Read back using existing parser
        val parser = ClrTableParser(buf, bytes)
        val parsed = parser.parse(0, bytes.size, 2, 5, meta.flags, 0)
        assertNotNull(parsed)

        assertEquals("v4.0.30319", parsed.metadataVersion)
        assertEquals(1, parsed.tables.modules.size)
        assertEquals("TestModule.dll", parsed.strings.get(parsed.tables.modules[0].name))

        assertEquals(1, parsed.tables.assemblies.size)
        val asm = parsed.tables.assemblies[0]
        assertEquals("TestAssembly", parsed.strings.get(asm.name))
        assertEquals(1, asm.majorVersion)
        assertEquals(0x8004, asm.hashAlgId)
    }

    @Test
    fun writeAndReadTypeDefs() {
        val strings = ClrStringHeapBuilder()
        val blobs = ClrBlobHeapBuilder()
        val guids = ClrGuidHeapBuilder()
        val us = ClrUserStringHeapBuilder()

        val moduleName = strings.add("Test.dll")
        val mvid = guids.add(ByteArray(16))

        // <Module> (required)
        val moduleTypeName = strings.add("<Module>")
        val emptyNs = strings.add("")
        // MyClass
        val className = strings.add("MyClass")
        val nsName = strings.add("TestNamespace")
        // Method
        val methodName = strings.add("DoSomething")
        val methodSig = blobs.add(byteArrayOf(0x00, 0x00, 0x01)) // void, no params, returns void

        val tables = ClrTables(
            modules = listOf(ClrModule(0, moduleName, mvid, 0, 0)),
            typeDefs = listOf(
                ClrTypeDef(0, moduleTypeName, emptyNs, 0, 1, 1),
                ClrTypeDef(
                    flags = 0x00100001, // public + class
                    name = className,
                    namespace = nsName,
                    extends = 0, // coded index
                    fieldList = 1,
                    methodList = 1,
                ),
            ),
            methodDefs = listOf(ClrMethodDefinition(
                rva = 0,
                implFlags = 0,
                flags = 0x0086, // public static hidebysig
                name = methodName,
                signature = methodSig,
                paramList = 1,
            )),
        )

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
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val parsed = ClrTableParser(buf, bytes).parse(0, bytes.size, 2, 5, meta.flags, 0)
        assertNotNull(parsed)

        assertEquals(2, parsed.tables.typeDefs.size)
        val td = parsed.tables.typeDefs[1]
        assertEquals("MyClass", parsed.strings.get(td.name))
        assertEquals("TestNamespace", parsed.strings.get(td.namespace))

        assertEquals(1, parsed.tables.methodDefs.size)
        assertEquals("DoSomething", parsed.strings.get(parsed.tables.methodDefs[0].name))
    }

    @Test
    fun writeAndReadTypeRefs() {
        val strings = ClrStringHeapBuilder()
        val blobs = ClrBlobHeapBuilder()
        val guids = ClrGuidHeapBuilder()
        val us = ClrUserStringHeapBuilder()

        val moduleName = strings.add("Test.dll")
        val mvid = guids.add(ByteArray(16))
        val asmRefName = strings.add("mscorlib")
        val objectName = strings.add("Object")
        val systemNs = strings.add("System")

        val tables = ClrTables(
            modules = listOf(ClrModule(0, moduleName, mvid, 0, 0)),
            typeRefs = listOf(ClrTypeRef(
                resolutionScope = (1 shl 2) or 3, // AssemblyRef #1, tag=3
                name = objectName,
                namespace = systemNs,
            )),
            assemblyRefs = listOf(ClrAssemblyRef(
                majorVersion = 4, minorVersion = 0,
                buildNumber = 0, revisionNumber = 0,
                flags = 0,
                publicKeyOrToken = 0, name = asmRefName,
                culture = 0, hashValue = 0,
            )),
        )

        val meta = ClrMetadata(
            majorRuntimeVersion = 2, minorRuntimeVersion = 5,
            flags = ClrMetadata.COR_FLAGS_ILONLY, entryPointToken = 0,
            metadataVersion = "v4.0.30319",
            tables = tables,
            strings = strings.build(), blobs = blobs.build(),
            guids = guids.build(), userStrings = us.build(),
        )

        val bytes = ClrTableWriter().write(meta)
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val parsed = ClrTableParser(buf, bytes).parse(0, bytes.size, 2, 5, meta.flags, 0)
        assertNotNull(parsed)

        assertEquals(1, parsed.tables.typeRefs.size)
        assertEquals("Object", parsed.strings.get(parsed.tables.typeRefs[0].name))
        assertEquals("System", parsed.strings.get(parsed.tables.typeRefs[0].namespace))

        assertEquals(1, parsed.tables.assemblyRefs.size)
        assertEquals("mscorlib", parsed.strings.get(parsed.tables.assemblyRefs[0].name))
    }

    @Test
    fun writeAndReadMemberRefs() {
        val strings = ClrStringHeapBuilder()
        val blobs = ClrBlobHeapBuilder()
        val guids = ClrGuidHeapBuilder()
        val us = ClrUserStringHeapBuilder()

        val moduleName = strings.add("Test.dll")
        val mvid = guids.add(ByteArray(16))
        val ctorName = strings.add(".ctor")
        val ctorSig = blobs.add(byteArrayOf(0x20, 0x00, 0x01)) // instance void()
        val objectName = strings.add("Object")
        val systemNs = strings.add("System")

        val tables = ClrTables(
            modules = listOf(ClrModule(0, moduleName, mvid, 0, 0)),
            typeDefs = listOf(ClrTypeDef(0, strings.add("<Module>"), strings.add(""), 0, 1, 1)),
            typeRefs = listOf(ClrTypeRef(0, objectName, systemNs)),
            memberRefs = listOf(ClrMemberRef(
                classIndex = (1 shl 3) or 1, // TypeRef #1, tag=1
                name = ctorName,
                signature = ctorSig,
            )),
        )

        val meta = ClrMetadata(
            majorRuntimeVersion = 2, minorRuntimeVersion = 5,
            flags = ClrMetadata.COR_FLAGS_ILONLY, entryPointToken = 0,
            metadataVersion = "v4.0.30319",
            tables = tables,
            strings = strings.build(), blobs = blobs.build(),
            guids = guids.build(), userStrings = us.build(),
        )

        val bytes = ClrTableWriter().write(meta)
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val parsed = ClrTableParser(buf, bytes).parse(0, bytes.size, 2, 5, meta.flags, 0)
        assertNotNull(parsed)

        assertEquals(1, parsed.tables.memberRefs.size)
        assertEquals(".ctor", parsed.strings.get(parsed.tables.memberRefs[0].name))
    }

    @Test
    fun writeAndReadGenericParams() {
        val strings = ClrStringHeapBuilder()
        val blobs = ClrBlobHeapBuilder()
        val guids = ClrGuidHeapBuilder()
        val us = ClrUserStringHeapBuilder()

        val moduleName = strings.add("Test.dll")
        val mvid = guids.add(ByteArray(16))
        val className = strings.add("MyGeneric")
        val tParamName = strings.add("T")

        val tables = ClrTables(
            modules = listOf(ClrModule(0, moduleName, mvid, 0, 0)),
            typeDefs = listOf(
                ClrTypeDef(0, strings.add("<Module>"), strings.add(""), 0, 1, 1),
                ClrTypeDef(0x00100001, className, strings.add(""), 0, 1, 1),
            ),
            genericParams = listOf(ClrGenericParam(
                number = 0,
                flags = 0,
                owner = (2 shl 1) or 0, // TypeDef #2, tag=0
                name = tParamName,
            )),
        )

        val meta = ClrMetadata(
            majorRuntimeVersion = 2, minorRuntimeVersion = 5,
            flags = ClrMetadata.COR_FLAGS_ILONLY, entryPointToken = 0,
            metadataVersion = "v4.0.30319",
            tables = tables,
            strings = strings.build(), blobs = blobs.build(),
            guids = guids.build(), userStrings = us.build(),
        )

        val bytes = ClrTableWriter().write(meta)
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val parsed = ClrTableParser(buf, bytes).parse(0, bytes.size, 2, 5, meta.flags, 0)
        assertNotNull(parsed)

        assertEquals(1, parsed.tables.genericParams.size)
        assertEquals("T", parsed.strings.get(parsed.tables.genericParams[0].name))
        assertEquals(0, parsed.tables.genericParams[0].number)
    }

    @Test
    fun writeAndReadNestedClasses() {
        val strings = ClrStringHeapBuilder()
        val blobs = ClrBlobHeapBuilder()
        val guids = ClrGuidHeapBuilder()
        val us = ClrUserStringHeapBuilder()

        val moduleName = strings.add("Test.dll")
        val mvid = guids.add(ByteArray(16))
        val emptyNs = strings.add("")

        val tables = ClrTables(
            modules = listOf(ClrModule(0, moduleName, mvid, 0, 0)),
            typeDefs = listOf(
                ClrTypeDef(0, strings.add("<Module>"), emptyNs, 0, 1, 1),
                ClrTypeDef(0x00100001, strings.add("Outer"), emptyNs, 0, 1, 1),
                ClrTypeDef(0x00100002, strings.add("Inner"), emptyNs, 0, 1, 1),
            ),
            nestedClasses = listOf(ClrNestedClass(
                nestedClass = 3, // Inner (1-based)
                enclosingClass = 2, // Outer
            )),
        )

        val meta = ClrMetadata(
            majorRuntimeVersion = 2, minorRuntimeVersion = 5,
            flags = ClrMetadata.COR_FLAGS_ILONLY, entryPointToken = 0,
            metadataVersion = "v4.0.30319",
            tables = tables,
            strings = strings.build(), blobs = blobs.build(),
            guids = guids.build(), userStrings = us.build(),
        )

        val bytes = ClrTableWriter().write(meta)
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val parsed = ClrTableParser(buf, bytes).parse(0, bytes.size, 2, 5, meta.flags, 0)
        assertNotNull(parsed)

        assertEquals(1, parsed.tables.nestedClasses.size)
        assertEquals(3, parsed.tables.nestedClasses[0].nestedClass)
        assertEquals(2, parsed.tables.nestedClasses[0].enclosingClass)
    }
}
