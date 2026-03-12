package org.kgen.runtime.gc

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class TypeRegistryTest {

    @Test
    fun registerWithExplicitId() {
        val registry = TypeRegistry()
        val layout = ObjectLayout("Point", 16, listOf(), typeId = 5)
        val id = registry.register(layout)
        assertEquals(5, id)
    }

    @Test
    fun registerWithAutoId() {
        val registry = TypeRegistry()
        val layout = ObjectLayout("Point", 16, listOf(), typeId = 0)
        val id = registry.register(layout)
        assertTrue(id > 0)
    }

    @Test
    fun autoIdsIncrement() {
        val registry = TypeRegistry()
        val id1 = registry.register(ObjectLayout("A", 8, listOf()))
        val id2 = registry.register(ObjectLayout("B", 8, listOf()))
        assertEquals(id1 + 1, id2)
    }

    @Test
    fun lookupRegistered() {
        val registry = TypeRegistry()
        val layout = ObjectLayout("Point", 16, listOf(), typeId = 3)
        registry.register(layout)
        val result = registry.lookup(3)
        assertNotNull(result)
        assertEquals("Point", result!!.name)
        assertEquals(3, result.typeId)
    }

    @Test
    fun lookupMissing() {
        val registry = TypeRegistry()
        assertNull(registry.lookup(999))
    }

    @Test
    fun allTypesEmpty() {
        val registry = TypeRegistry()
        assertTrue(registry.allTypes().isEmpty())
    }

    @Test
    fun allTypesContainsRegistered() {
        val registry = TypeRegistry()
        registry.register(ObjectLayout("A", 8, listOf(), typeId = 1))
        registry.register(ObjectLayout("B", 16, listOf(), typeId = 2))
        assertEquals(2, registry.allTypes().size)
    }

    @Test
    fun overwriteSameId() {
        val registry = TypeRegistry()
        registry.register(ObjectLayout("A", 8, listOf(), typeId = 1))
        registry.register(ObjectLayout("B", 16, listOf(), typeId = 1))
        val result = registry.lookup(1)
        assertEquals("B", result!!.name)
    }

    @Test
    fun fieldsPreserved() {
        val registry = TypeRegistry()
        val fields = listOf(
            FieldDescriptor("x", 0, 8, false),
            FieldDescriptor("y", 8, 8, false),
            FieldDescriptor("next", 16, 8, true),
        )
        registry.register(ObjectLayout("Node", 24, fields, typeId = 1))
        val result = registry.lookup(1)!!
        assertEquals(3, result.fields.size)
        assertTrue(result.fields[2].isReference)
        assertEquals("next", result.fields[2].name)
    }

    @Test
    fun totalSizeIncludesHeader() {
        val layout = ObjectLayout("Point", 16, listOf())
        assertEquals(24, layout.totalSize()) // 8 header + 16 data
    }

    @Test
    fun headerSizeIs8() {
        assertEquals(8, ObjectLayout.HEADER_SIZE)
    }

    @Test
    fun fieldDescriptorProperties() {
        val fd = FieldDescriptor("value", 4, 8, true)
        assertEquals("value", fd.name)
        assertEquals(4, fd.offset)
        assertEquals(8, fd.size)
        assertTrue(fd.isReference)
    }

    @Test
    fun manyTypes() {
        val registry = TypeRegistry()
        for (i in 1..50) {
            registry.register(ObjectLayout("Type$i", i * 8, listOf(), typeId = i))
        }
        assertEquals(50, registry.allTypes().size)
        for (i in 1..50) {
            assertEquals("Type$i", registry.lookup(i)!!.name)
        }
    }
}
