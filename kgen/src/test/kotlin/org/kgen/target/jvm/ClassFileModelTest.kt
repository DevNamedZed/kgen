package org.kgen.target.jvm

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClassFileModelTest {

    private fun minimalClassFile(
        majorVersion: Int = 65,
        minorVersion: Int = 0,
        superClass: Int = 0,
        interfaces: List<Int> = emptyList(),
        fields: List<FieldInfo> = emptyList(),
        methods: List<MethodInfo> = emptyList(),
        attributes: List<AttributeInfo> = emptyList(),
    ): ClassFile {
        val cp = ConstantPoolBuilder()
        val thisIdx = cp.classEntry("com/example/Test")
        val superIdx = if (superClass != 0) {
            superClass
        } else {
            cp.classEntry("java/lang/Object")
        }
        return ClassFile(
            minorVersion = minorVersion,
            majorVersion = majorVersion,
            constantPool = cp.build(),
            accessFlags = AccessFlags.PUBLIC or AccessFlags.SUPER,
            thisClass = thisIdx,
            superClass = superIdx,
            interfaces = interfaces,
            fields = fields,
            methods = methods,
            attributes = attributes,
        )
    }

    @Nested
    inner class JavaVersion {

        @Test
        fun java11() {
            assertEquals("1.1", minimalClassFile(majorVersion = 45).javaVersion)
        }

        @Test
        fun java12() {
            assertEquals("1.2", minimalClassFile(majorVersion = 46).javaVersion)
        }

        @Test
        fun java13() {
            assertEquals("1.3", minimalClassFile(majorVersion = 47).javaVersion)
        }

        @Test
        fun java14() {
            assertEquals("1.4", minimalClassFile(majorVersion = 48).javaVersion)
        }

        @Test
        fun java5() {
            assertEquals("5", minimalClassFile(majorVersion = 49).javaVersion)
        }

        @Test
        fun java6() {
            assertEquals("6", minimalClassFile(majorVersion = 50).javaVersion)
        }

        @Test
        fun java8() {
            assertEquals("8", minimalClassFile(majorVersion = 52).javaVersion)
        }

        @Test
        fun java11Version() {
            assertEquals("11", minimalClassFile(majorVersion = 55).javaVersion)
        }

        @Test
        fun java17() {
            assertEquals("17", minimalClassFile(majorVersion = 61).javaVersion)
        }

        @Test
        fun java21() {
            assertEquals("21", minimalClassFile(majorVersion = 65).javaVersion)
        }

        @Test
        fun java24() {
            assertEquals("24", minimalClassFile(majorVersion = 68).javaVersion)
        }

        @Test
        fun java25() {
            assertEquals("25", minimalClassFile(majorVersion = 69).javaVersion)
        }

        @Test
        fun unknownVersion() {
            val cf = minimalClassFile(majorVersion = 100, minorVersion = 3)
            assertEquals("100.3", cf.javaVersion)
        }
    }

    @Nested
    inner class ThisClassName {

        @Test
        fun resolvesName() {
            val cf = minimalClassFile()
            assertEquals("com/example/Test", cf.thisClassName)
        }
    }

    @Nested
    inner class SuperClassName {

        @Test
        fun resolvesName() {
            val cf = minimalClassFile()
            assertEquals("java/lang/Object", cf.superClassName)
        }

        @Test
        fun returnsNullWhenSuperIsZero() {
            val cp = ConstantPoolBuilder()
            val thisIdx = cp.classEntry("java/lang/Object")
            val cf = ClassFile(0, 65, cp.build(), AccessFlags.PUBLIC, thisIdx, 0,
                emptyList(), emptyList(), emptyList(), emptyList())
            assertNull(cf.superClassName)
        }
    }

    @Nested
    inner class InterfaceNames {

        @Test
        fun emptyInterfaces() {
            val cf = minimalClassFile()
            assertTrue(cf.interfaceNames.isEmpty())
        }

        @Test
        fun multipleInterfaces() {
            val cp = ConstantPoolBuilder()
            val thisIdx = cp.classEntry("com/example/Test")
            val superIdx = cp.classEntry("java/lang/Object")
            val i1 = cp.classEntry("java/io/Serializable")
            val i2 = cp.classEntry("java/lang/Cloneable")
            val cf = ClassFile(0, 65, cp.build(), AccessFlags.PUBLIC, thisIdx, superIdx,
                listOf(i1, i2), emptyList(), emptyList(), emptyList())
            assertEquals(2, cf.interfaceNames.size)
            assertEquals("java/io/Serializable", cf.interfaceNames[0])
            assertEquals("java/lang/Cloneable", cf.interfaceNames[1])
        }
    }

    @Nested
    inner class StringHelper {

        @Test
        fun resolvesByIndex() {
            val cp = ConstantPoolBuilder()
            val idx = cp.utf8("hello")
            cp.classEntry("Dummy") // ensure pool has other entries
            val cf = ClassFile(0, 65, cp.build(), 0, cp.classEntry("X"), 0,
                emptyList(), emptyList(), emptyList(), emptyList())
            assertEquals("hello", cf.string(idx))
        }
    }

    @Nested
    inner class SourceFileProperty {

        @Test
        fun returnsNullWhenAbsent() {
            val cf = minimalClassFile()
            assertNull(cf.sourceFile)
        }

        @Test
        fun returnsSourceFileWhenPresent() {
            val cp = ConstantPoolBuilder()
            val thisIdx = cp.classEntry("com/example/Test")
            val superIdx = cp.classEntry("java/lang/Object")
            val sfNameIdx = cp.utf8("SourceFile")
            val sfValueIdx = cp.utf8("Test.java")
            val data = byteArrayOf((sfValueIdx shr 8).toByte(), (sfValueIdx and 0xFF).toByte())
            val attr = AttributeInfo(sfNameIdx, data)
            val cf = ClassFile(0, 65, cp.build(), AccessFlags.PUBLIC, thisIdx, superIdx,
                emptyList(), emptyList(), emptyList(), listOf(attr))
            assertEquals("Test.java", cf.sourceFile)
        }
    }

    @Nested
    inner class DataClassBehavior {

        @Test
        fun copyWorks() {
            val cf = minimalClassFile()
            val cf2 = cf.copy(majorVersion = 52)
            assertEquals(52, cf2.majorVersion)
            assertEquals(cf.thisClass, cf2.thisClass)
        }

        @Test
        fun equalityWorks() {
            val cp = ConstantPoolBuilder()
            val thisIdx = cp.classEntry("A")
            val pool = cp.build()
            val cf1 = ClassFile(0, 65, pool, 0, thisIdx, 0,
                emptyList(), emptyList(), emptyList(), emptyList())
            val cf2 = ClassFile(0, 65, pool, 0, thisIdx, 0,
                emptyList(), emptyList(), emptyList(), emptyList())
            assertEquals(cf1, cf2)
        }
    }

    @Nested
    inner class FieldInfoModel {

        @Test
        fun construction() {
            val field = FieldInfo(
                accessFlags = AccessFlags.PRIVATE or AccessFlags.FINAL,
                nameIndex = 5,
                descriptorIndex = 6,
                attributes = emptyList(),
            )
            assertEquals(AccessFlags.PRIVATE or AccessFlags.FINAL, field.accessFlags)
            assertEquals(5, field.nameIndex)
            assertEquals(6, field.descriptorIndex)
            assertTrue(field.attributes.isEmpty())
        }

        @Test
        fun equality() {
            val f1 = FieldInfo(1, 2, 3, emptyList())
            val f2 = FieldInfo(1, 2, 3, emptyList())
            assertEquals(f1, f2)
        }
    }

    @Nested
    inner class MethodInfoModel {

        @Test
        fun construction() {
            val method = MethodInfo(
                accessFlags = AccessFlags.PUBLIC or AccessFlags.STATIC,
                nameIndex = 10,
                descriptorIndex = 11,
                attributes = emptyList(),
            )
            assertEquals(AccessFlags.PUBLIC or AccessFlags.STATIC, method.accessFlags)
            assertEquals(10, method.nameIndex)
            assertEquals(11, method.descriptorIndex)
        }

        @Test
        fun equality() {
            val m1 = MethodInfo(1, 2, 3, emptyList())
            val m2 = MethodInfo(1, 2, 3, emptyList())
            assertEquals(m1, m2)
        }
    }

    @Nested
    inner class AttributeInfoModel {

        @Test
        fun construction() {
            val data = byteArrayOf(0x01, 0x02, 0x03)
            val attr = AttributeInfo(5, data)
            assertEquals(5, attr.nameIndex)
            assertEquals(3, attr.data.size)
        }

        @Test
        fun equalityByContent() {
            val a1 = AttributeInfo(1, byteArrayOf(0x01, 0x02))
            val a2 = AttributeInfo(1, byteArrayOf(0x01, 0x02))
            assertEquals(a1, a2)
            assertEquals(a1.hashCode(), a2.hashCode())
        }

        @Test
        fun inequalityByNameIndex() {
            val a1 = AttributeInfo(1, byteArrayOf(0x01))
            val a2 = AttributeInfo(2, byteArrayOf(0x01))
            assertTrue(a1 != a2)
        }

        @Test
        fun inequalityByData() {
            val a1 = AttributeInfo(1, byteArrayOf(0x01))
            val a2 = AttributeInfo(1, byteArrayOf(0x02))
            assertTrue(a1 != a2)
        }

        @Test
        fun equalsWithSameReference() {
            val a = AttributeInfo(1, byteArrayOf(0x01))
            assertEquals(a, a)
        }

        @Test
        fun equalsWithDifferentType() {
            val a = AttributeInfo(1, byteArrayOf(0x01))
            assertTrue(a != (Any() as? AttributeInfo))
        }
    }
}
