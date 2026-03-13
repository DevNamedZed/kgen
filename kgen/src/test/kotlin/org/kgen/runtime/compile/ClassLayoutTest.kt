package org.kgen.runtime.compile

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.kgen.target.jvm.*

class ClassLayoutTest {

    private fun buildClassBytes(
        className: String,
        block: ClassFileBuilder.() -> Unit,
    ): ByteArray {
        val builder = ClassFileBuilder(className)
        builder.block()
        return JvmClassWriter.write(builder.build())
    }

    @Nested
    inner class SingleFieldLayouts {

        @Test
        fun intFieldOffset() {
            val bytes = buildClassBytes("com/example/IntHolder") {
                field("value", "I")
            }
            val layout = ClassLayout.build(listOf(bytes))
            val offset = layout.fieldOffset("com/example/IntHolder", "value")
            assertNotNull(offset)
            assertEquals(ClassLayout.HEADER_SIZE, offset)
        }

        @Test
        fun longFieldOffset() {
            val bytes = buildClassBytes("com/example/LongHolder") {
                field("value", "J")
            }
            val layout = ClassLayout.build(listOf(bytes))
            val offset = layout.fieldOffset("com/example/LongHolder", "value")
            assertNotNull(offset)
            assertEquals(ClassLayout.HEADER_SIZE, offset)
        }

        @Test
        fun byteFieldOffset() {
            val bytes = buildClassBytes("com/example/ByteHolder") {
                field("value", "B")
            }
            val layout = ClassLayout.build(listOf(bytes))
            val offset = layout.fieldOffset("com/example/ByteHolder", "value")
            assertNotNull(offset)
            assertEquals(ClassLayout.HEADER_SIZE, offset)
        }

        @Test
        fun doubleFieldOffset() {
            val bytes = buildClassBytes("com/example/DoubleHolder") {
                field("value", "D")
            }
            val layout = ClassLayout.build(listOf(bytes))
            val offset = layout.fieldOffset("com/example/DoubleHolder", "value")
            assertNotNull(offset)
            assertEquals(ClassLayout.HEADER_SIZE, offset)
        }

        @Test
        fun referenceFieldOffset() {
            val bytes = buildClassBytes("com/example/RefHolder") {
                field("ref", "Ljava/lang/Object;")
            }
            val layout = ClassLayout.build(listOf(bytes))
            val offset = layout.fieldOffset("com/example/RefHolder", "ref")
            assertNotNull(offset)
            assertEquals(ClassLayout.HEADER_SIZE, offset)
        }

        @Test
        fun arrayFieldOffset() {
            val bytes = buildClassBytes("com/example/ArrHolder") {
                field("arr", "[I")
            }
            val layout = ClassLayout.build(listOf(bytes))
            val offset = layout.fieldOffset("com/example/ArrHolder", "arr")
            assertNotNull(offset)
            assertEquals(ClassLayout.HEADER_SIZE, offset)
        }
    }

    @Nested
    inner class MultiFieldLayouts {

        @Test
        fun twoIntFields() {
            val bytes = buildClassBytes("com/example/Point") {
                field("x", "I")
                field("y", "I")
            }
            val layout = ClassLayout.build(listOf(bytes))
            val xOffset = layout.fieldOffset("com/example/Point", "x")
            val yOffset = layout.fieldOffset("com/example/Point", "y")
            assertNotNull(xOffset)
            assertNotNull(yOffset)
            assertTrue(yOffset!! > xOffset!!)
            assertEquals(4, yOffset - xOffset)
        }

        @Test
        fun intAndLongFields() {
            val bytes = buildClassBytes("com/example/Mixed") {
                field("a", "I")
                field("b", "J")
            }
            val layout = ClassLayout.build(listOf(bytes))
            val aOffset = layout.fieldOffset("com/example/Mixed", "a")!!
            val bOffset = layout.fieldOffset("com/example/Mixed", "b")!!
            assertTrue(bOffset > aOffset)
            // long should be 8-byte aligned
            assertEquals(0L, bOffset % 8)
        }

        @Test
        fun byteAndIntFieldAlignment() {
            val bytes = buildClassBytes("com/example/ByteInt") {
                field("b", "B")
                field("i", "I")
            }
            val layout = ClassLayout.build(listOf(bytes))
            val bOffset = layout.fieldOffset("com/example/ByteInt", "b")!!
            val iOffset = layout.fieldOffset("com/example/ByteInt", "i")!!
            assertTrue(iOffset > bOffset)
            // int should be 4-byte aligned
            assertEquals(0L, iOffset % 4)
        }

        @Test
        fun threeFieldsSequential() {
            val bytes = buildClassBytes("com/example/Triple") {
                field("a", "I")
                field("b", "I")
                field("c", "I")
            }
            val layout = ClassLayout.build(listOf(bytes))
            val aOff = layout.fieldOffset("com/example/Triple", "a")!!
            val bOff = layout.fieldOffset("com/example/Triple", "b")!!
            val cOff = layout.fieldOffset("com/example/Triple", "c")!!
            assertEquals(4, bOff - aOff)
            assertEquals(4, cOff - bOff)
        }
    }

    @Nested
    inner class ObjectSizeTests {

        @Test
        fun emptyClassHasMinimumSize() {
            val bytes = buildClassBytes("com/example/Empty") {}
            val layout = ClassLayout.build(listOf(bytes))
            val size = layout.objectSize("com/example/Empty")
            // Minimum = HEADER_SIZE + 8 = 16, aligned to 8
            assertTrue(size >= ClassLayout.HEADER_SIZE + 8)
            assertEquals(0L, size % 8)
        }

        @Test
        fun singleIntFieldSize() {
            val bytes = buildClassBytes("com/example/IntOnly") {
                field("value", "I")
            }
            val layout = ClassLayout.build(listOf(bytes))
            val size = layout.objectSize("com/example/IntOnly")
            // header(8) + int(4) = 12, min is 16, aligned to 8 = 16
            assertEquals(16L, size)
        }

        @Test
        fun twoLongFieldsSize() {
            val bytes = buildClassBytes("com/example/TwoLongs") {
                field("a", "J")
                field("b", "J")
            }
            val layout = ClassLayout.build(listOf(bytes))
            val size = layout.objectSize("com/example/TwoLongs")
            // header(8) + long(8) + long(8) = 24, aligned to 8 = 24
            assertEquals(24L, size)
        }

        @Test
        fun sizeAligned8Bytes() {
            val bytes = buildClassBytes("com/example/Byte3") {
                field("a", "B")
                field("b", "B")
                field("c", "B")
            }
            val layout = ClassLayout.build(listOf(bytes))
            val size = layout.objectSize("com/example/Byte3")
            assertEquals(0L, size % 8)
        }

        @Test
        fun unknownClassReturnsDefault() {
            val layout = ClassLayout.build(listOf())
            val size = layout.objectSize("com/example/NonExistent")
            assertEquals(ClassLayout.DEFAULT_OBJECT_SIZE, size)
        }
    }

    @Nested
    inner class StaticFieldExclusion {

        @Test
        fun staticFieldsDoNotCountInLayout() {
            val bytes = buildClassBytes("com/example/WithStatic") {
                field("instanceField", "I")
                field("staticField", "J", AccessFlags.STATIC)
            }
            val layout = ClassLayout.build(listOf(bytes))
            assertNull(layout.fieldOffset("com/example/WithStatic", "staticField"))
            assertNotNull(layout.fieldOffset("com/example/WithStatic", "instanceField"))
        }

        @Test
        fun allStaticFieldsExcluded() {
            val bytes = buildClassBytes("com/example/AllStatic") {
                field("a", "I", AccessFlags.PUBLIC or AccessFlags.STATIC)
                field("b", "J", AccessFlags.PRIVATE or AccessFlags.STATIC)
            }
            val layout = ClassLayout.build(listOf(bytes))
            assertNull(layout.fieldOffset("com/example/AllStatic", "a"))
            assertNull(layout.fieldOffset("com/example/AllStatic", "b"))
            // Should have minimum size (no instance fields)
            assertEquals(16L, layout.objectSize("com/example/AllStatic"))
        }
    }

    @Nested
    inner class MultipleClasses {

        @Test
        fun classNamesReturnsAllBuiltClasses() {
            val class1 = buildClassBytes("com/example/Foo") {
                field("x", "I")
            }
            val class2 = buildClassBytes("com/example/Bar") {
                field("y", "J")
            }
            val layout = ClassLayout.build(listOf(class1, class2))
            val names = layout.classNames()
            assertEquals(2, names.size)
            assertTrue(names.contains("com/example/Foo"))
            assertTrue(names.contains("com/example/Bar"))
        }

        @Test
        fun fieldOffsetsIndependentPerClass() {
            val class1 = buildClassBytes("com/example/A") {
                field("x", "I")
            }
            val class2 = buildClassBytes("com/example/B") {
                field("x", "J")
            }
            val layout = ClassLayout.build(listOf(class1, class2))
            val aOffset = layout.fieldOffset("com/example/A", "x")!!
            val bOffset = layout.fieldOffset("com/example/B", "x")!!
            assertEquals(ClassLayout.HEADER_SIZE, aOffset)
            assertEquals(ClassLayout.HEADER_SIZE, bOffset)
        }

        @Test
        fun fieldOffsetForWrongClassReturnsNull() {
            val bytes = buildClassBytes("com/example/One") {
                field("val", "I")
            }
            val layout = ClassLayout.build(listOf(bytes))
            assertNull(layout.fieldOffset("com/example/Two", "val"))
        }

        @Test
        fun fieldOffsetForWrongFieldReturnsNull() {
            val bytes = buildClassBytes("com/example/One") {
                field("existing", "I")
            }
            val layout = ClassLayout.build(listOf(bytes))
            assertNull(layout.fieldOffset("com/example/One", "nonexistent"))
        }

        @Test
        fun emptyClassList() {
            val layout = ClassLayout.build(listOf())
            assertTrue(layout.classNames().isEmpty())
        }
    }

    @Nested
    inner class Constants {

        @Test
        fun headerSizeIs8() {
            assertEquals(8L, ClassLayout.HEADER_SIZE)
        }

        @Test
        fun defaultObjectSizeIs64() {
            assertEquals(64L, ClassLayout.DEFAULT_OBJECT_SIZE)
        }
    }

    @Nested
    inner class DescriptorSizeMapping {

        @Test
        fun booleanFieldSize1() {
            val bytes = buildClassBytes("com/example/Bool") {
                field("flag", "Z")
                field("after", "J")
            }
            val layout = ClassLayout.build(listOf(bytes))
            val flagOff = layout.fieldOffset("com/example/Bool", "flag")!!
            val afterOff = layout.fieldOffset("com/example/Bool", "after")!!
            // Boolean takes 1 byte, long needs 8-byte alignment
            assertTrue(afterOff - flagOff >= 1)
            assertEquals(0L, afterOff % 8)
        }

        @Test
        fun shortFieldSize2() {
            val bytes = buildClassBytes("com/example/ShortHolder") {
                field("s", "S")
                field("after", "I")
            }
            val layout = ClassLayout.build(listOf(bytes))
            val sOff = layout.fieldOffset("com/example/ShortHolder", "s")!!
            val afterOff = layout.fieldOffset("com/example/ShortHolder", "after")!!
            assertTrue(afterOff - sOff >= 2)
            assertEquals(0L, afterOff % 4)
        }

        @Test
        fun charFieldSize2() {
            val bytes = buildClassBytes("com/example/CharHolder") {
                field("c", "C")
                field("after", "I")
            }
            val layout = ClassLayout.build(listOf(bytes))
            val cOff = layout.fieldOffset("com/example/CharHolder", "c")!!
            val afterOff = layout.fieldOffset("com/example/CharHolder", "after")!!
            assertTrue(afterOff - cOff >= 2)
        }

        @Test
        fun floatFieldSize4() {
            val bytes = buildClassBytes("com/example/FloatHolder") {
                field("f", "F")
                field("after", "J")
            }
            val layout = ClassLayout.build(listOf(bytes))
            val fOff = layout.fieldOffset("com/example/FloatHolder", "f")!!
            val afterOff = layout.fieldOffset("com/example/FloatHolder", "after")!!
            assertTrue(afterOff - fOff >= 4)
        }
    }
}
