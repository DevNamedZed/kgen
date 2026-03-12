package org.kgen.reflect

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.binary.pe.clr.*

class ClrReflectionPropertiesEventsTest {

    private fun buildMetadata(tables: ClrTables, strings: ClrStringHeap, blobs: ClrBlobHeap): ClrMetadata {
        return ClrMetadata(
            majorRuntimeVersion = 2, minorRuntimeVersion = 5,
            flags = ClrMetadata.COR_FLAGS_ILONLY,
            entryPointToken = 0,
            metadataVersion = "v4.0.30319",
            tables = tables,
            strings = strings,
            blobs = blobs,
            guids = ClrGuidHeapBuilder().apply { add(ByteArray(16)) }.build(),
            userStrings = ClrUserStringHeapBuilder().build(),
        )
    }

    @Test
    fun `maps property with getter and setter`() {
        val strings = ClrStringHeapBuilder()
        val blobs = ClrBlobHeapBuilder()
        val moduleName = strings.add("Test.dll")
        val typeName = strings.add("Person")
        val ns = strings.add("Model")
        val getName = strings.add("get_Name")
        val setName = strings.add("set_Name")
        val propName = strings.add("Name")
        val sig = blobs.add(byteArrayOf(0x00, 0x00, 0x0E))
        val propSig = blobs.add(byteArrayOf(0x28, 0x00, 0x0E))

        val emptyNs = strings.add("")
        val tables = ClrTables(
            modules = listOf(ClrModule(0, moduleName, 1, 0, 0)),
            typeDefs = listOf(
                ClrTypeDef(0, strings.add("<Module>"), emptyNs, 0, 1, 1),
                ClrTypeDef(0x01, typeName, ns, 0, 1, 1),
            ),
            methodDefs = listOf(
                ClrMethodDef(0, 0, 0x0006, getName, sig, 1),
                ClrMethodDef(0, 0, 0x0006, setName, sig, 1),
            ),
            propertyMaps = listOf(ClrPropertyMap(parent = 2, propertyList = 1)),
            properties = listOf(ClrProperty(0, propName, propSig)),
            methodSemantics = listOf(
                ClrMethodSemantics(semantics = 0x0002, method = 1, association = 3), // Getter → prop 1
                ClrMethodSemantics(semantics = 0x0001, method = 2, association = 3), // Setter → prop 1
            ),
        )
        val clr = buildMetadata(tables, strings.build(), blobs.build())
        val types = ClrTypeMapper.map(clr, null)

        assertEquals(1, types.size, "Types: ${types.map { it.name() }}")
        val person = types[0]
        assertTrue(person.name().endsWith("Person"), "Expected Person, got: ${person.name()}")

        val props = person.properties()
        assertEquals(1, props.size)
        assertEquals("Name", props[0].name())
        assertNotNull(props[0].getter(), "Should have getter")
        assertNotNull(props[0].setter(), "Should have setter")
        assertEquals("get_Name", props[0].getter()!!.name())
        assertEquals("set_Name", props[0].setter()!!.name())
        assertFalse(props[0].isReadOnly())
    }

    @Test
    fun `maps read-only property`() {
        val strings = ClrStringHeapBuilder()
        val blobs = ClrBlobHeapBuilder()
        val moduleName = strings.add("Test.dll")
        val typeName = strings.add("Config")
        val ns = strings.add("")
        val getName = strings.add("get_Version")
        val propName = strings.add("Version")
        val sig = blobs.add(byteArrayOf(0x00, 0x00, 0x0E))
        val propSig = blobs.add(byteArrayOf(0x28, 0x00, 0x0E))

        val tables = ClrTables(
            modules = listOf(ClrModule(0, moduleName, 1, 0, 0)),
            typeDefs = listOf(
                ClrTypeDef(0, strings.add("<Module>"), ns, 0, 1, 1),
                ClrTypeDef(0x01, typeName, ns, 0, 1, 1),
            ),
            methodDefs = listOf(ClrMethodDef(0, 0, 0x0006, getName, sig, 1)),
            propertyMaps = listOf(ClrPropertyMap(parent = 2, propertyList = 1)),
            properties = listOf(ClrProperty(0, propName, propSig)),
            methodSemantics = listOf(
                ClrMethodSemantics(semantics = 0x0002, method = 1, association = 3),
            ),
        )
        val clr = buildMetadata(tables, strings.build(), blobs.build())
        val types = ClrTypeMapper.map(clr, null)

        val prop = types[0].property("Version")
        assertNotNull(prop)
        assertTrue(prop!!.isReadOnly())
        assertNotNull(prop.getter())
        assertNull(prop.setter())
    }

    @Test
    fun `maps event with add and remove`() {
        val strings = ClrStringHeapBuilder()
        val blobs = ClrBlobHeapBuilder()
        val moduleName = strings.add("Test.dll")
        val typeName = strings.add("Button")
        val ns = strings.add("UI")
        val addClick = strings.add("add_Click")
        val removeClick = strings.add("remove_Click")
        val eventName = strings.add("Click")
        val sig = blobs.add(byteArrayOf(0x00, 0x00, 0x01))

        val emptyNs = strings.add("")
        val tables = ClrTables(
            modules = listOf(ClrModule(0, moduleName, 1, 0, 0)),
            typeDefs = listOf(
                ClrTypeDef(0, strings.add("<Module>"), emptyNs, 0, 1, 1),
                ClrTypeDef(0x01, typeName, ns, 0, 1, 1),
            ),
            methodDefs = listOf(
                ClrMethodDef(0, 0, 0x0006, addClick, sig, 1),
                ClrMethodDef(0, 0, 0x0006, removeClick, sig, 1),
            ),
            eventMaps = listOf(ClrEventMap(parent = 2, eventList = 1)),
            events = listOf(ClrEvent(flags = 0, name = eventName, eventType = 0)),
            methodSemantics = listOf(
                // HAS_SEMANTICS: Event tag=0, row 1 → coded = (1 shl 1) | 0 = 2
                ClrMethodSemantics(semantics = 0x0008, method = 1, association = 2), // AddOn
                ClrMethodSemantics(semantics = 0x0010, method = 2, association = 2), // RemoveOn
            ),
        )
        val clr = buildMetadata(tables, strings.build(), blobs.build())
        val types = ClrTypeMapper.map(clr, null)

        assertEquals(1, types.size)
        val events = types[0].events()
        assertEquals(1, events.size)
        assertEquals("Click", events[0].name())
        assertNotNull(events[0].addMethod())
        assertNotNull(events[0].removeMethod())
        assertNull(events[0].raiseMethod())
        assertEquals("add_Click", events[0].addMethod()!!.name())
        assertEquals("remove_Click", events[0].removeMethod()!!.name())
    }

    @Test
    fun `maps multiple properties on same type`() {
        val strings = ClrStringHeapBuilder()
        val blobs = ClrBlobHeapBuilder()
        val moduleName = strings.add("Test.dll")
        val typeName = strings.add("Item")
        val ns = strings.add("")
        val prop1 = strings.add("Id")
        val prop2 = strings.add("Label")
        val getter1 = strings.add("get_Id")
        val getter2 = strings.add("get_Label")
        val sig = blobs.add(byteArrayOf(0x00, 0x00, 0x08))
        val propSig = blobs.add(byteArrayOf(0x28, 0x00, 0x08))

        val tables = ClrTables(
            modules = listOf(ClrModule(0, moduleName, 1, 0, 0)),
            typeDefs = listOf(
                ClrTypeDef(0, strings.add("<Module>"), ns, 0, 1, 1),
                ClrTypeDef(0x01, typeName, ns, 0, 1, 1),
            ),
            methodDefs = listOf(
                ClrMethodDef(0, 0, 0x0006, getter1, sig, 1),
                ClrMethodDef(0, 0, 0x0006, getter2, sig, 1),
            ),
            propertyMaps = listOf(ClrPropertyMap(parent = 2, propertyList = 1)),
            properties = listOf(
                ClrProperty(0, prop1, propSig),
                ClrProperty(0, prop2, propSig),
            ),
            methodSemantics = listOf(
                ClrMethodSemantics(semantics = 0x0002, method = 1, association = 3), // prop 1
                ClrMethodSemantics(semantics = 0x0002, method = 2, association = 5), // prop 2
            ),
        )
        val clr = buildMetadata(tables, strings.build(), blobs.build())
        val types = ClrTypeMapper.map(clr, null)

        val props = types[0].properties()
        assertEquals(2, props.size)
        assertEquals("Id", props[0].name())
        assertEquals("Label", props[1].name())
        assertEquals("get_Id", props[0].getter()!!.name())
        assertEquals("get_Label", props[1].getter()!!.name())
    }

    @Test
    fun `type with no properties or events`() {
        val strings = ClrStringHeapBuilder()
        val blobs = ClrBlobHeapBuilder()
        val moduleName = strings.add("Test.dll")
        val typeName = strings.add("Plain")
        val ns = strings.add("")

        val tables = ClrTables(
            modules = listOf(ClrModule(0, moduleName, 1, 0, 0)),
            typeDefs = listOf(
                ClrTypeDef(0, strings.add("<Module>"), ns, 0, 1, 1),
                ClrTypeDef(0x01, typeName, ns, 0, 1, 1),
            ),
        )
        val clr = buildMetadata(tables, strings.build(), blobs.build())
        val types = ClrTypeMapper.map(clr, null)

        assertEquals(1, types.size)
        assertTrue(types[0].properties().isEmpty())
        assertTrue(types[0].events().isEmpty())
    }

    @Test
    fun `event with raise method`() {
        val strings = ClrStringHeapBuilder()
        val blobs = ClrBlobHeapBuilder()
        val moduleName = strings.add("Test.dll")
        val typeName = strings.add("Source")
        val ns = strings.add("")
        val addM = strings.add("add_Changed")
        val removeM = strings.add("remove_Changed")
        val raiseM = strings.add("raise_Changed")
        val eventName = strings.add("Changed")
        val sig = blobs.add(byteArrayOf(0x00, 0x00, 0x01))

        val tables = ClrTables(
            modules = listOf(ClrModule(0, moduleName, 1, 0, 0)),
            typeDefs = listOf(
                ClrTypeDef(0, strings.add("<Module>"), ns, 0, 1, 1),
                ClrTypeDef(0x01, typeName, ns, 0, 1, 1),
            ),
            methodDefs = listOf(
                ClrMethodDef(0, 0, 0x0006, addM, sig, 1),
                ClrMethodDef(0, 0, 0x0006, removeM, sig, 1),
                ClrMethodDef(0, 0, 0x0006, raiseM, sig, 1),
            ),
            eventMaps = listOf(ClrEventMap(parent = 2, eventList = 1)),
            events = listOf(ClrEvent(flags = 0, name = eventName, eventType = 0)),
            methodSemantics = listOf(
                ClrMethodSemantics(semantics = 0x0008, method = 1, association = 2),
                ClrMethodSemantics(semantics = 0x0010, method = 2, association = 2),
                ClrMethodSemantics(semantics = 0x0020, method = 3, association = 2),
            ),
        )
        val clr = buildMetadata(tables, strings.build(), blobs.build())
        val types = ClrTypeMapper.map(clr, null)

        val event = types[0].event("Changed")
        assertNotNull(event)
        assertNotNull(event!!.addMethod())
        assertNotNull(event.removeMethod())
        assertNotNull(event.raiseMethod())
        assertEquals("raise_Changed", event.raiseMethod()!!.name())
    }

    @Test
    fun `properties and events on different types`() {
        val strings = ClrStringHeapBuilder()
        val blobs = ClrBlobHeapBuilder()
        val moduleName = strings.add("Test.dll")
        val ns = strings.add("")
        val type1 = strings.add("ClassA")
        val type2 = strings.add("ClassB")
        val prop1 = strings.add("PropA")
        val event1 = strings.add("EventB")
        val getter = strings.add("get_PropA")
        val addM = strings.add("add_EventB")
        val sig = blobs.add(byteArrayOf(0x00, 0x00, 0x01))
        val propSig = blobs.add(byteArrayOf(0x28, 0x00, 0x08))

        val tables = ClrTables(
            modules = listOf(ClrModule(0, moduleName, 1, 0, 0)),
            typeDefs = listOf(
                ClrTypeDef(0, strings.add("<Module>"), ns, 0, 1, 1),
                ClrTypeDef(0x01, type1, ns, 0, 1, 1),
                ClrTypeDef(0x01, type2, ns, 0, 1, 2),
            ),
            methodDefs = listOf(
                ClrMethodDef(0, 0, 0x0006, getter, sig, 1),
                ClrMethodDef(0, 0, 0x0006, addM, sig, 1),
            ),
            propertyMaps = listOf(ClrPropertyMap(parent = 2, propertyList = 1)),
            properties = listOf(ClrProperty(0, prop1, propSig)),
            eventMaps = listOf(ClrEventMap(parent = 3, eventList = 1)),
            events = listOf(ClrEvent(flags = 0, name = event1, eventType = 0)),
            methodSemantics = listOf(
                ClrMethodSemantics(semantics = 0x0002, method = 1, association = 3),
                ClrMethodSemantics(semantics = 0x0008, method = 2, association = 2),
            ),
        )
        val clr = buildMetadata(tables, strings.build(), blobs.build())
        val types = ClrTypeMapper.map(clr, null)

        assertEquals(2, types.size)
        val classA = types[0]
        val classB = types[1]

        assertEquals(1, classA.properties().size)
        assertEquals("PropA", classA.properties()[0].name())
        assertTrue(classA.events().isEmpty())

        assertTrue(classB.properties().isEmpty())
        assertEquals(1, classB.events().size)
        assertEquals("EventB", classB.events()[0].name())
    }
}
