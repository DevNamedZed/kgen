package org.kgen.binary.pe.clr

import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ClrMissingTablesTest {

    private fun buildAndParse(tables: ClrTables, strings: ClrStringHeap, blobs: ClrBlobHeap): ClrMetadata {
        val guids = ClrGuidHeapBuilder().apply { add(ByteArray(16)) }.build()
        val meta = ClrMetadata(
            majorRuntimeVersion = 2, minorRuntimeVersion = 5,
            flags = ClrMetadata.COR_FLAGS_ILONLY,
            entryPointToken = 0,
            metadataVersion = "v4.0.30319",
            tables = tables,
            strings = strings,
            blobs = blobs,
            guids = guids,
            userStrings = ClrUserStringHeapBuilder().build(),
        )
        val bytes = ClrTableWriter().write(meta)
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(0x424A5342, buf.getInt(0))
        val parsed = ClrTableParser(buf, bytes).parse(0, bytes.size, 2, 5, meta.flags, 0)
        assertNotNull(parsed)
        return parsed
    }

    @Test
    fun `FieldMarshal round-trip`() {
        val strings = ClrStringHeapBuilder()
        val blobs = ClrBlobHeapBuilder()
        val moduleName = strings.add("Test.dll")
        val fieldName = strings.add("myField")
        val fieldSig = blobs.add(byteArrayOf(0x06, 0x08))
        val nativeType = blobs.add(byteArrayOf(0x19)) // NATIVE_TYPE_LPSTR

        // HAS_FIELD_MARSHAL coded index: 1 bit tag, Field=0
        // Field row 1 → coded index = (1 shl 1) or 0 = 2
        val tables = ClrTables(
            modules = listOf(ClrModule(0, moduleName, 1, 0, 0)),
            fields = listOf(ClrField(0x06, fieldName, fieldSig)),
            fieldMarshals = listOf(ClrFieldMarshal(parent = 2, nativeType = nativeType)),
        )
        val parsed = buildAndParse(tables, strings.build(), blobs.build())

        assertEquals(1, parsed.tables.fieldMarshals.size)
        assertEquals(2, parsed.tables.fieldMarshals[0].parent)
        assertEquals(nativeType, parsed.tables.fieldMarshals[0].nativeType)
    }

    @Test
    fun `DeclSecurity round-trip`() {
        val strings = ClrStringHeapBuilder()
        val blobs = ClrBlobHeapBuilder()
        val moduleName = strings.add("Test.dll")
        val typeName = strings.add("SecureClass")
        val ns = strings.add("Test")
        val permSet = blobs.add(byteArrayOf(0x2E, 0x01, 0x00))

        // HAS_DECL_SECURITY coded index: 2 bits tag, TypeDef=0
        // TypeDef row 1 → coded index = (1 shl 2) or 0 = 4
        val tables = ClrTables(
            modules = listOf(ClrModule(0, moduleName, 1, 0, 0)),
            typeDefs = listOf(ClrTypeDef(0, typeName, ns, 0, 1, 1)),
            declSecurities = listOf(ClrDeclSecurity(action = 2, parent = 4, permissionSet = permSet)),
        )
        val parsed = buildAndParse(tables, strings.build(), blobs.build())

        assertEquals(1, parsed.tables.declSecurities.size)
        assertEquals(2, parsed.tables.declSecurities[0].action)
        assertEquals(4, parsed.tables.declSecurities[0].parent)
        assertEquals(permSet, parsed.tables.declSecurities[0].permissionSet)
    }

    @Test
    fun `EventMap round-trip`() {
        val strings = ClrStringHeapBuilder()
        val blobs = ClrBlobHeapBuilder()
        val moduleName = strings.add("Test.dll")
        val typeName = strings.add("EventSource")
        val ns = strings.add("Test")
        val eventName = strings.add("OnChange")

        val tables = ClrTables(
            modules = listOf(ClrModule(0, moduleName, 1, 0, 0)),
            typeDefs = listOf(ClrTypeDef(0, typeName, ns, 0, 1, 1)),
            eventMaps = listOf(ClrEventMap(parent = 1, eventList = 1)),
            events = listOf(ClrEvent(flags = 0, name = eventName, eventType = 0)),
        )
        val parsed = buildAndParse(tables, strings.build(), blobs.build())

        assertEquals(1, parsed.tables.eventMaps.size)
        assertEquals(1, parsed.tables.eventMaps[0].parent)
        assertEquals(1, parsed.tables.eventMaps[0].eventList)
    }

    @Test
    fun `Event round-trip`() {
        val strings = ClrStringHeapBuilder()
        val blobs = ClrBlobHeapBuilder()
        val moduleName = strings.add("Test.dll")
        val eventName = strings.add("Click")

        // TYPE_DEF_OR_REF coded index: 2 bits tag, TypeRef=1
        // TypeRef row 1 → coded index = (1 shl 2) or 1 = 5
        val tables = ClrTables(
            modules = listOf(ClrModule(0, moduleName, 1, 0, 0)),
            typeRefs = listOf(ClrTypeRef(0, strings.add("EventHandler"), strings.add("System"))),
            events = listOf(ClrEvent(flags = 0x0200, name = eventName, eventType = 5)),
        )
        val parsed = buildAndParse(tables, strings.build(), blobs.build())

        assertEquals(1, parsed.tables.events.size)
        assertEquals(0x0200, parsed.tables.events[0].flags)
        assertEquals("Click", parsed.strings.get(parsed.tables.events[0].name))
        assertEquals(5, parsed.tables.events[0].eventType)
    }

    @Test
    fun `PropertyMap round-trip`() {
        val strings = ClrStringHeapBuilder()
        val blobs = ClrBlobHeapBuilder()
        val moduleName = strings.add("Test.dll")
        val typeName = strings.add("MyClass")
        val ns = strings.add("Test")
        val propName = strings.add("Name")
        val propSig = blobs.add(byteArrayOf(0x28, 0x00, 0x0E))

        val tables = ClrTables(
            modules = listOf(ClrModule(0, moduleName, 1, 0, 0)),
            typeDefs = listOf(ClrTypeDef(0, typeName, ns, 0, 1, 1)),
            propertyMaps = listOf(ClrPropertyMap(parent = 1, propertyList = 1)),
            properties = listOf(ClrProperty(flags = 0, name = propName, type = propSig)),
        )
        val parsed = buildAndParse(tables, strings.build(), blobs.build())

        assertEquals(1, parsed.tables.propertyMaps.size)
        assertEquals(1, parsed.tables.propertyMaps[0].parent)
        assertEquals(1, parsed.tables.propertyMaps[0].propertyList)
    }

    @Test
    fun `Property round-trip`() {
        val strings = ClrStringHeapBuilder()
        val blobs = ClrBlobHeapBuilder()
        val moduleName = strings.add("Test.dll")
        val propName = strings.add("Count")
        val propSig = blobs.add(byteArrayOf(0x28, 0x00, 0x08))

        val tables = ClrTables(
            modules = listOf(ClrModule(0, moduleName, 1, 0, 0)),
            properties = listOf(ClrProperty(flags = 0x0200, name = propName, type = propSig)),
        )
        val parsed = buildAndParse(tables, strings.build(), blobs.build())

        assertEquals(1, parsed.tables.properties.size)
        assertEquals(0x0200, parsed.tables.properties[0].flags)
        assertEquals("Count", parsed.strings.get(parsed.tables.properties[0].name))
        assertEquals(propSig, parsed.tables.properties[0].type)
    }

    @Test
    fun `MethodSemantics round-trip`() {
        val strings = ClrStringHeapBuilder()
        val blobs = ClrBlobHeapBuilder()
        val moduleName = strings.add("Test.dll")
        val typeName = strings.add("MyClass")
        val ns = strings.add("Test")
        val getterName = strings.add("get_Name")
        val methodSig = blobs.add(byteArrayOf(0x00, 0x00, 0x0E))
        val propName = strings.add("Name")
        val propSig = blobs.add(byteArrayOf(0x28, 0x00, 0x0E))

        // HAS_SEMANTICS coded index: 1 bit tag, Property=1
        // Property row 1 → coded index = (1 shl 1) or 1 = 3
        val tables = ClrTables(
            modules = listOf(ClrModule(0, moduleName, 1, 0, 0)),
            typeDefs = listOf(ClrTypeDef(0, typeName, ns, 0, 1, 1)),
            methodDefs = listOf(ClrMethodDefinition(0, 0, 0x0006, getterName, methodSig, 1)),
            properties = listOf(ClrProperty(0, propName, propSig)),
            methodSemantics = listOf(ClrMethodSemantics(
                semantics = 0x0002, // Getter
                method = 1,
                association = 3, // Property row 1
            )),
        )
        val parsed = buildAndParse(tables, strings.build(), blobs.build())

        assertEquals(1, parsed.tables.methodSemantics.size)
        assertEquals(0x0002, parsed.tables.methodSemantics[0].semantics)
        assertEquals(1, parsed.tables.methodSemantics[0].method)
        assertEquals(3, parsed.tables.methodSemantics[0].association)
    }

    @Test
    fun `MethodImpl round-trip`() {
        val strings = ClrStringHeapBuilder()
        val blobs = ClrBlobHeapBuilder()
        val moduleName = strings.add("Test.dll")
        val typeName = strings.add("MyClass")
        val ns = strings.add("Test")
        val methodName = strings.add("DoWork")
        val methodSig = blobs.add(byteArrayOf(0x00, 0x00, 0x01))
        val ifaceName = strings.add("IWorker")
        val ifaceMethodName = strings.add("DoWork")
        val memberRefSig = blobs.add(byteArrayOf(0x00, 0x00, 0x01))

        // METHOD_DEF_OR_REF coded index: 1 bit tag, MethodDefinition=0, MemberRef=1
        // MethodDefinition row 1 → coded index = (1 shl 1) or 0 = 2
        // MemberRef row 1 → coded index = (1 shl 1) or 1 = 3
        val tables = ClrTables(
            modules = listOf(ClrModule(0, moduleName, 1, 0, 0)),
            typeDefs = listOf(ClrTypeDef(0, typeName, ns, 0, 1, 1)),
            methodDefs = listOf(ClrMethodDefinition(0, 0, 0x0006, methodName, methodSig, 1)),
            memberRefs = listOf(ClrMemberRef(0, ifaceMethodName, memberRefSig)),
            methodImpls = listOf(ClrMethodImpl(
                classIndex = 1,
                methodBody = 2,       // MethodDefinition row 1
                methodDeclaration = 3, // MemberRef row 1
            )),
        )
        val parsed = buildAndParse(tables, strings.build(), blobs.build())

        assertEquals(1, parsed.tables.methodImpls.size)
        assertEquals(1, parsed.tables.methodImpls[0].classIndex)
        assertEquals(2, parsed.tables.methodImpls[0].methodBody)
        assertEquals(3, parsed.tables.methodImpls[0].methodDeclaration)
    }

    @Test
    fun `multiple FieldMarshal entries`() {
        val strings = ClrStringHeapBuilder()
        val blobs = ClrBlobHeapBuilder()
        val moduleName = strings.add("Test.dll")
        val field1 = strings.add("field1")
        val field2 = strings.add("field2")
        val sig = blobs.add(byteArrayOf(0x06, 0x08))
        val nt1 = blobs.add(byteArrayOf(0x19))
        val nt2 = blobs.add(byteArrayOf(0x14))

        val tables = ClrTables(
            modules = listOf(ClrModule(0, moduleName, 1, 0, 0)),
            fields = listOf(
                ClrField(0x06, field1, sig),
                ClrField(0x06, field2, sig),
            ),
            fieldMarshals = listOf(
                ClrFieldMarshal(parent = 2, nativeType = nt1),  // Field row 1
                ClrFieldMarshal(parent = 4, nativeType = nt2),  // Field row 2
            ),
        )
        val parsed = buildAndParse(tables, strings.build(), blobs.build())

        assertEquals(2, parsed.tables.fieldMarshals.size)
        assertEquals(2, parsed.tables.fieldMarshals[0].parent)
        assertEquals(4, parsed.tables.fieldMarshals[1].parent)
    }

    @Test
    fun `multiple events with EventMap`() {
        val strings = ClrStringHeapBuilder()
        val blobs = ClrBlobHeapBuilder()
        val moduleName = strings.add("Test.dll")
        val typeName = strings.add("Button")
        val ns = strings.add("UI")
        val event1 = strings.add("Click")
        val event2 = strings.add("Hover")

        val tables = ClrTables(
            modules = listOf(ClrModule(0, moduleName, 1, 0, 0)),
            typeDefs = listOf(ClrTypeDef(0, typeName, ns, 0, 1, 1)),
            eventMaps = listOf(ClrEventMap(parent = 1, eventList = 1)),
            events = listOf(
                ClrEvent(flags = 0, name = event1, eventType = 0),
                ClrEvent(flags = 0, name = event2, eventType = 0),
            ),
        )
        val parsed = buildAndParse(tables, strings.build(), blobs.build())

        assertEquals(1, parsed.tables.eventMaps.size)
        assertEquals(2, parsed.tables.events.size)
        assertEquals("Click", parsed.strings.get(parsed.tables.events[0].name))
        assertEquals("Hover", parsed.strings.get(parsed.tables.events[1].name))
    }

    @Test
    fun `multiple properties with PropertyMap and MethodSemantics`() {
        val strings = ClrStringHeapBuilder()
        val blobs = ClrBlobHeapBuilder()
        val moduleName = strings.add("Test.dll")
        val typeName = strings.add("Person")
        val ns = strings.add("Model")
        val prop1 = strings.add("Name")
        val prop2 = strings.add("Age")
        val propSig1 = blobs.add(byteArrayOf(0x28, 0x00, 0x0E))
        val propSig2 = blobs.add(byteArrayOf(0x28, 0x00, 0x08))
        val getName = strings.add("get_Name")
        val setName = strings.add("set_Name")
        val getAge = strings.add("get_Age")
        val mSig = blobs.add(byteArrayOf(0x00, 0x00, 0x01))

        // HAS_SEMANTICS: 1 bit tag, Property=1
        // Property row 1 → (1 shl 1) | 1 = 3
        // Property row 2 → (2 shl 1) | 1 = 5
        val tables = ClrTables(
            modules = listOf(ClrModule(0, moduleName, 1, 0, 0)),
            typeDefs = listOf(ClrTypeDef(0, typeName, ns, 0, 1, 1)),
            methodDefs = listOf(
                ClrMethodDefinition(0, 0, 0x0006, getName, mSig, 1),
                ClrMethodDefinition(0, 0, 0x0006, setName, mSig, 1),
                ClrMethodDefinition(0, 0, 0x0006, getAge, mSig, 1),
            ),
            propertyMaps = listOf(ClrPropertyMap(parent = 1, propertyList = 1)),
            properties = listOf(
                ClrProperty(0, prop1, propSig1),
                ClrProperty(0, prop2, propSig2),
            ),
            methodSemantics = listOf(
                ClrMethodSemantics(semantics = 0x0002, method = 1, association = 3), // get_Name → Name
                ClrMethodSemantics(semantics = 0x0001, method = 2, association = 3), // set_Name → Name
                ClrMethodSemantics(semantics = 0x0002, method = 3, association = 5), // get_Age → Age
            ),
        )
        val parsed = buildAndParse(tables, strings.build(), blobs.build())

        assertEquals(1, parsed.tables.propertyMaps.size)
        assertEquals(2, parsed.tables.properties.size)
        assertEquals("Name", parsed.strings.get(parsed.tables.properties[0].name))
        assertEquals("Age", parsed.strings.get(parsed.tables.properties[1].name))
        assertEquals(3, parsed.tables.methodSemantics.size)
        assertEquals(0x0002, parsed.tables.methodSemantics[0].semantics) // Getter
        assertEquals(0x0001, parsed.tables.methodSemantics[1].semantics) // Setter
    }

    @Test
    fun `all 8 tables coexist`() {
        val strings = ClrStringHeapBuilder()
        val blobs = ClrBlobHeapBuilder()
        val moduleName = strings.add("Full.dll")
        val typeName = strings.add("MyClass")
        val ns = strings.add("NS")
        val fieldName = strings.add("f1")
        val methodName = strings.add("M1")
        val eventName = strings.add("E1")
        val propName = strings.add("P1")
        val sig = blobs.add(byteArrayOf(0x06, 0x08))
        val nativeType = blobs.add(byteArrayOf(0x19))
        val permSet = blobs.add(byteArrayOf(0x2E, 0x01))
        val propSig = blobs.add(byteArrayOf(0x28, 0x00, 0x08))

        val tables = ClrTables(
            modules = listOf(ClrModule(0, moduleName, 1, 0, 0)),
            typeDefs = listOf(ClrTypeDef(0, typeName, ns, 0, 1, 1)),
            fields = listOf(ClrField(0x06, fieldName, sig)),
            methodDefs = listOf(ClrMethodDefinition(0, 0, 0x0006, methodName, sig, 1)),
            fieldMarshals = listOf(ClrFieldMarshal(parent = 2, nativeType = nativeType)),
            declSecurities = listOf(ClrDeclSecurity(action = 2, parent = 4, permissionSet = permSet)),
            eventMaps = listOf(ClrEventMap(parent = 1, eventList = 1)),
            events = listOf(ClrEvent(flags = 0, name = eventName, eventType = 0)),
            propertyMaps = listOf(ClrPropertyMap(parent = 1, propertyList = 1)),
            properties = listOf(ClrProperty(flags = 0, name = propName, type = propSig)),
            methodSemantics = listOf(ClrMethodSemantics(semantics = 0x0002, method = 1, association = 3)),
            methodImpls = listOf(ClrMethodImpl(classIndex = 1, methodBody = 2, methodDeclaration = 2)),
        )
        val parsed = buildAndParse(tables, strings.build(), blobs.build())

        assertEquals(1, parsed.tables.fieldMarshals.size)
        assertEquals(1, parsed.tables.declSecurities.size)
        assertEquals(1, parsed.tables.eventMaps.size)
        assertEquals(1, parsed.tables.events.size)
        assertEquals(1, parsed.tables.propertyMaps.size)
        assertEquals(1, parsed.tables.properties.size)
        assertEquals(1, parsed.tables.methodSemantics.size)
        assertEquals(1, parsed.tables.methodImpls.size)

        // Verify names survived round-trip
        assertEquals("E1", parsed.strings.get(parsed.tables.events[0].name))
        assertEquals("P1", parsed.strings.get(parsed.tables.properties[0].name))
    }
}
