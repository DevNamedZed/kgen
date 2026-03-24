package org.kgen.runtime.gc

import org.junit.jupiter.api.Test
import org.kgen.target.jvm.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NativeTypeLayoutBuilderTest {

    private val builder = NativeTypeLayoutBuilder()

    private fun buildClassWithFields(vararg fields: Pair<String, String>): ByteArray {
        val constantPool = ConstantPoolBuilder()
        val thisClass = constantPool.classEntry("test/NativeObj")
        val superClass = constantPool.classEntry("java/lang/Object")
        val fieldInfos = fields.map { (name, descriptor) ->
            FieldInfo(0, constantPool.utf8(name), constantPool.utf8(descriptor), emptyList())
        }
        return JvmClassWriter.write(ClassFile(
            0, 50, constantPool.build(),
            AccessFlags.PUBLIC, thisClass, superClass,
            emptyList(), fieldInfos, emptyList(), emptyList(),
        ))
    }

    @Test
    fun layoutFromClassWithLongAndIntFields() {
        val classBytes = buildClassWithFields("data" to "J", "size" to "I")
        val layout = builder.buildLayout(classBytes)

        assertEquals("test_NativeObj", layout.name)
        assertEquals(2, layout.fields.size)

        val dataField = layout.fields.first { it.name == "data" }
        assertEquals(8, dataField.size)
        assertEquals(0, dataField.offset)
        assertFalse(dataField.isReference)

        val sizeField = layout.fields.first { it.name == "size" }
        assertEquals(4, sizeField.size)
    }

    @Test
    fun layoutFromClassWithReferenceField() {
        val classBytes = buildClassWithFields("data" to "J", "next" to "Ljava/lang/Object;")
        val layout = builder.buildLayout(classBytes)

        val nextField = layout.fields.first { it.name == "next" }
        assertTrue(nextField.isReference)
        assertEquals(8, nextField.size)
    }

    @Test
    fun layoutFromClassWithExplicitReferenceFields() {
        val classBytes = buildClassWithFields("data" to "J", "size" to "I")
        val layout = builder.buildLayout(classBytes, referenceFields = setOf("data"))

        val dataField = layout.fields.first { it.name == "data" }
        assertTrue(dataField.isReference)
    }

    @Test
    fun layoutSkipsStaticFields() {
        val constantPool = ConstantPoolBuilder()
        val thisClass = constantPool.classEntry("test/WithStatics")
        val superClass = constantPool.classEntry("java/lang/Object")
        val fieldInfos = listOf(
            FieldInfo(AccessFlags.STATIC, constantPool.utf8("counter"), constantPool.utf8("I"), emptyList()),
            FieldInfo(0, constantPool.utf8("value"), constantPool.utf8("J"), emptyList()),
        )
        val classBytes = JvmClassWriter.write(ClassFile(
            0, 50, constantPool.build(),
            AccessFlags.PUBLIC, thisClass, superClass,
            emptyList(), fieldInfos, emptyList(), emptyList(),
        ))

        val layout = builder.buildLayout(classBytes)
        assertEquals(1, layout.fields.size)
        assertEquals("value", layout.fields[0].name)
    }

    @Test
    fun layoutSizeAlignedTo8Bytes() {
        val classBytes = buildClassWithFields("flag" to "Z")
        val layout = builder.buildLayout(classBytes)

        assertEquals(0, layout.size % 8)
    }

    @Test
    fun totalSizeIncludesHeader() {
        val classBytes = buildClassWithFields("data" to "J")
        val layout = builder.buildLayout(classBytes)

        assertTrue(layout.totalSize() >= ObjectLayout.HEADER_SIZE + 8)
    }

    @Test
    fun layoutRegistersWithTypeRegistry() {
        val classBytes = buildClassWithFields("data" to "J", "size" to "I")
        val layout = builder.buildLayout(classBytes)
        val registry = TypeRegistry()
        val typeId = registry.register(layout)

        val retrieved = registry.lookup(typeId)
        assertNotNull(retrieved)
        assertEquals("test_NativeObj", retrieved.name)
        assertEquals(2, retrieved.fields.size)
    }

    @Test
    fun emptyClassGetsMinimumSize() {
        val classBytes = buildClassWithFields()
        val layout = builder.buildLayout(classBytes)

        assertTrue(layout.size >= 8)
    }
}
