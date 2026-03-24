package org.kgen.ir

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class MetadataTest {

    @Nested
    inner class TypeAliasTests {

        @Test
        fun hasCorrectName() {
            val alias = TypeAlias("size_t", Type.I64)
            assertEquals("size_t", alias.name)
        }

        @Test
        fun hasCorrectType() {
            val alias = TypeAlias("size_t", Type.I64)
            assertEquals(Type.I64, alias.type)
        }

        @Test
        fun worksWithComplexType() {
            val structType = Type.Struct("Point", listOf(Type.F64, Type.F64))
            val alias = TypeAlias("Point2D", structType)
            assertEquals(structType, alias.type)
        }

        @Test
        fun equality() {
            val a = TypeAlias("int", Type.I32)
            val b = TypeAlias("int", Type.I32)
            assertEquals(a, b)
            assertEquals(a.hashCode(), b.hashCode())
        }

        @Test
        fun inequalityByName() {
            assertNotEquals(TypeAlias("int", Type.I32), TypeAlias("uint", Type.I32))
        }

        @Test
        fun inequalityByType() {
            assertNotEquals(TypeAlias("int", Type.I32), TypeAlias("int", Type.I64))
        }
    }

    @Nested
    inner class MetadataValueTests {

        @Nested
        inner class StringMDTests {

            @Test
            fun holdsStringValue() {
                val md = MetadataValue.StringMD("hello")
                assertEquals("hello", md.value)
            }

            @Test
            fun emptyString() {
                val md = MetadataValue.StringMD("")
                assertEquals("", md.value)
            }

            @Test
            fun equality() {
                assertEquals(MetadataValue.StringMD("a"), MetadataValue.StringMD("a"))
            }

            @Test
            fun inequality() {
                assertNotEquals(MetadataValue.StringMD("a"), MetadataValue.StringMD("b"))
            }
        }

        @Nested
        inner class IntMDTests {

            @Test
            fun holdsLongValue() {
                val md = MetadataValue.IntMD(42L)
                assertEquals(42L, md.value)
            }

            @Test
            fun negativeValue() {
                val md = MetadataValue.IntMD(-1L)
                assertEquals(-1L, md.value)
            }

            @Test
            fun equality() {
                assertEquals(MetadataValue.IntMD(100L), MetadataValue.IntMD(100L))
            }

            @Test
            fun inequality() {
                assertNotEquals(MetadataValue.IntMD(1L), MetadataValue.IntMD(2L))
            }
        }

        @Nested
        inner class NodeMDTests {

            @Test
            fun holdsListOfValues() {
                val node = MetadataValue.NodeMD(
                    listOf(MetadataValue.StringMD("file.c"), MetadataValue.IntMD(10L))
                )
                assertEquals(2, node.values.size)
            }

            @Test
            fun emptyNode() {
                val node = MetadataValue.NodeMD(emptyList())
                assertTrue(node.values.isEmpty())
            }

            @Test
            fun nestedNodes() {
                val inner = MetadataValue.NodeMD(listOf(MetadataValue.IntMD(1L)))
                val outer = MetadataValue.NodeMD(listOf(inner))
                val retrieved = outer.values[0] as MetadataValue.NodeMD
                assertEquals(1, retrieved.values.size)
            }

            @Test
            fun equality() {
                val a = MetadataValue.NodeMD(listOf(MetadataValue.IntMD(1L)))
                val b = MetadataValue.NodeMD(listOf(MetadataValue.IntMD(1L)))
                assertEquals(a, b)
            }
        }

        @Nested
        inner class RefMDTests {

            @Test
            fun holdsReferenceName() {
                val ref = MetadataValue.RefMD("!dbg.location")
                assertEquals("!dbg.location", ref.name)
            }

            @Test
            fun equality() {
                assertEquals(MetadataValue.RefMD("!0"), MetadataValue.RefMD("!0"))
            }

            @Test
            fun inequality() {
                assertNotEquals(MetadataValue.RefMD("!0"), MetadataValue.RefMD("!1"))
            }
        }

        @Test
        fun differentVariantsAreNotEqual() {
            val s: MetadataValue = MetadataValue.StringMD("42")
            val i: MetadataValue = MetadataValue.IntMD(42L)
            val r: MetadataValue = MetadataValue.RefMD("42")
            assertNotEquals(s, i)
            assertNotEquals(s, r)
            assertNotEquals(i, r)
        }
    }

    @Nested
    inner class AnnotationDefinitionTests {

        @Test
        fun hasCorrectType() {
            val ann = AnnotationDefinition("java.lang.Override")
            assertEquals("java.lang.Override", ann.type)
        }

        @Test
        fun defaultValuesAreEmpty() {
            val ann = AnnotationDefinition("Override")
            assertTrue(ann.values.isEmpty())
        }

        @Test
        fun defaultRetentionIsRuntime() {
            val ann = AnnotationDefinition("Override")
            assertEquals(AnnotationRetention.RUNTIME, ann.retention)
        }

        @Test
        fun customRetention() {
            val ann = AnnotationDefinition("SuppressWarnings", retention = AnnotationRetention.SOURCE)
            assertEquals(AnnotationRetention.SOURCE, ann.retention)
        }

        @Test
        fun withValues() {
            val ann = AnnotationDefinition(
                "RequestMapping",
                mapOf("path" to AnnotationValue.StringVal("/api"))
            )
            assertEquals(AnnotationValue.StringVal("/api"), ann.values["path"])
        }

        @Test
        fun equality() {
            val a = AnnotationDefinition("Test", mapOf("timeout" to AnnotationValue.IntVal(5000L)))
            val b = AnnotationDefinition("Test", mapOf("timeout" to AnnotationValue.IntVal(5000L)))
            assertEquals(a, b)
            assertEquals(a.hashCode(), b.hashCode())
        }

        @Test
        fun inequalityByType() {
            assertNotEquals(AnnotationDefinition("A"), AnnotationDefinition("B"))
        }
    }

    @Nested
    inner class AnnotationValueTests {

        @Test
        fun stringVal() {
            val v = AnnotationValue.StringVal("hello")
            assertEquals("hello", v.value)
        }

        @Test
        fun intVal() {
            val v = AnnotationValue.IntVal(42L)
            assertEquals(42L, v.value)
        }

        @Test
        fun floatVal() {
            val v = AnnotationValue.FloatVal(3.14)
            assertEquals(3.14, v.value)
        }

        @Test
        fun boolVal() {
            val v = AnnotationValue.BoolVal(true)
            assertTrue(v.value)
        }

        @Test
        fun enumVal() {
            val v = AnnotationValue.EnumVal("RetentionPolicy", "RUNTIME")
            assertEquals("RetentionPolicy", v.type)
            assertEquals("RUNTIME", v.name)
        }

        @Test
        fun classVal() {
            val v = AnnotationValue.ClassVal(Type.ClassRef("String"))
            assertEquals(Type.ClassRef("String"), v.type)
        }

        @Test
        fun arrayVal() {
            val v = AnnotationValue.ArrayVal(
                listOf(AnnotationValue.StringVal("a"), AnnotationValue.StringVal("b"))
            )
            assertEquals(2, v.values.size)
            assertEquals(AnnotationValue.StringVal("a"), v.values[0])
        }

        @Test
        fun emptyArrayVal() {
            val v = AnnotationValue.ArrayVal(emptyList())
            assertTrue(v.values.isEmpty())
        }

        @Test
        fun annotationVal() {
            val inner = AnnotationDefinition("Inner", mapOf("x" to AnnotationValue.IntVal(1L)))
            val v = AnnotationValue.AnnotationVal(inner)
            assertEquals("Inner", v.annotation.type)
        }

        @Test
        fun differentVariantsNotEqual() {
            val s: AnnotationValue = AnnotationValue.StringVal("1")
            val i: AnnotationValue = AnnotationValue.IntVal(1L)
            assertNotEquals(s, i)
        }

        @Test
        fun sameVariantEquality() {
            assertEquals(AnnotationValue.BoolVal(false), AnnotationValue.BoolVal(false))
            assertEquals(AnnotationValue.FloatVal(1.0), AnnotationValue.FloatVal(1.0))
        }
    }

    @Nested
    inner class AnnotationRetentionTests {

        @Test
        fun hasThreeValues() {
            assertEquals(3, AnnotationRetention.entries.size)
        }

        @Test
        fun containsAllExpectedValues() {
            val names = AnnotationRetention.entries.map { it.name }.toSet()
            assertEquals(setOf("SOURCE", "CLASS", "RUNTIME"), names)
        }
    }

    @Nested
    inner class StackMapTests {

        @Test
        fun hasFunctionName() {
            val sm = StackMap("myFunc", emptyList())
            assertEquals("myFunc", sm.functionName)
        }

        @Test
        fun hasEntries() {
            val entry = StackMapEntry(0x10L, listOf(StackMapLocation.Register(0)))
            val sm = StackMap("fn", listOf(entry))
            assertEquals(1, sm.entries.size)
            assertEquals(0x10L, sm.entries[0].instructionOffset)
        }

        @Test
        fun emptyEntries() {
            val sm = StackMap("fn", emptyList())
            assertTrue(sm.entries.isEmpty())
        }

        @Test
        fun equality() {
            val a = StackMap("fn", listOf(StackMapEntry(0L, emptyList())))
            val b = StackMap("fn", listOf(StackMapEntry(0L, emptyList())))
            assertEquals(a, b)
        }
    }

    @Nested
    inner class StackMapEntryTests {

        @Test
        fun hasInstructionOffset() {
            val entry = StackMapEntry(0x40L, emptyList())
            assertEquals(0x40L, entry.instructionOffset)
        }

        @Test
        fun hasLocations() {
            val locs = listOf(
                StackMapLocation.Register(3),
                StackMapLocation.Stack(-8)
            )
            val entry = StackMapEntry(0x20L, locs)
            assertEquals(2, entry.locations.size)
        }

        @Test
        fun equality() {
            val a = StackMapEntry(10L, listOf(StackMapLocation.Register(1)))
            val b = StackMapEntry(10L, listOf(StackMapLocation.Register(1)))
            assertEquals(a, b)
        }
    }

    @Nested
    inner class StackMapLocationTests {

        @Test
        fun registerHoldsIndex() {
            val loc = StackMapLocation.Register(5)
            assertEquals(5, loc.registerIndex)
        }

        @Test
        fun stackHoldsRbpOffset() {
            val loc = StackMapLocation.Stack(-16)
            assertEquals(-16, loc.rbpOffset)
        }

        @Test
        fun constantHoldsValue() {
            val loc = StackMapLocation.Constant(0L)
            assertEquals(0L, loc.value)
        }

        @Test
        fun registerEquality() {
            assertEquals(StackMapLocation.Register(0), StackMapLocation.Register(0))
        }

        @Test
        fun stackEquality() {
            assertEquals(StackMapLocation.Stack(-8), StackMapLocation.Stack(-8))
        }

        @Test
        fun constantEquality() {
            assertEquals(StackMapLocation.Constant(0L), StackMapLocation.Constant(0L))
        }

        @Test
        fun differentVariantsNotEqual() {
            val reg: StackMapLocation = StackMapLocation.Register(0)
            val stack: StackMapLocation = StackMapLocation.Stack(0)
            val const: StackMapLocation = StackMapLocation.Constant(0L)
            assertNotEquals(reg, stack)
            assertNotEquals(reg, const)
            assertNotEquals(stack, const)
        }
    }

    @Nested
    inner class BootstrapMethodTests {

        @Test
        fun hasClassName() {
            val bsm = BootstrapMethod(
                "java/lang/invoke/StringConcatFactory",
                "makeConcatWithConstants",
                Type.Function(listOf(Type.I32), Type.ClassRef("String"))
            )
            assertEquals("java/lang/invoke/StringConcatFactory", bsm.className)
        }

        @Test
        fun hasMethodName() {
            val bsm = BootstrapMethod(
                "Factory",
                "make",
                Type.Function(emptyList(), Type.Void)
            )
            assertEquals("make", bsm.methodName)
        }

        @Test
        fun hasMethodType() {
            val ft = Type.Function(listOf(Type.I32, Type.I64), Type.I32)
            val bsm = BootstrapMethod("Cls", "method", ft)
            assertEquals(ft, bsm.methodType)
        }

        @Test
        fun defaultStaticArgsEmpty() {
            val bsm = BootstrapMethod("Cls", "m", Type.Function(emptyList(), Type.Void))
            assertTrue(bsm.staticArgs.isEmpty())
        }

        @Test
        fun withStaticArgs() {
            val bsm = BootstrapMethod(
                "Cls", "m",
                Type.Function(emptyList(), Type.Void),
                listOf(Constant.I32(1), Constant.StringConst("hello"))
            )
            assertEquals(2, bsm.staticArgs.size)
            assertEquals(Constant.I32(1), bsm.staticArgs[0])
        }

        @Test
        fun equality() {
            val ft = Type.Function(emptyList(), Type.Void)
            val a = BootstrapMethod("Cls", "m", ft, listOf(Constant.I32(42)))
            val b = BootstrapMethod("Cls", "m", ft, listOf(Constant.I32(42)))
            assertEquals(a, b)
            assertEquals(a.hashCode(), b.hashCode())
        }

        @Test
        fun inequalityByClassName() {
            val ft = Type.Function(emptyList(), Type.Void)
            assertNotEquals(
                BootstrapMethod("A", "m", ft),
                BootstrapMethod("B", "m", ft)
            )
        }

        @Test
        fun inequalityByMethodName() {
            val ft = Type.Function(emptyList(), Type.Void)
            assertNotEquals(
                BootstrapMethod("Cls", "a", ft),
                BootstrapMethod("Cls", "b", ft)
            )
        }

        @Test
        fun inequalityByStaticArgs() {
            val ft = Type.Function(emptyList(), Type.Void)
            assertNotEquals(
                BootstrapMethod("Cls", "m", ft, listOf(Constant.I32(1))),
                BootstrapMethod("Cls", "m", ft, listOf(Constant.I32(2)))
            )
        }
    }
}
