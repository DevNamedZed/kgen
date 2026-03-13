package org.kgen.target.jvm

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConstantPoolTest {

    private fun buildPool(): ConstantPool {
        val builder = ConstantPoolBuilder()
        builder.utf8("TestClass")          // 1
        builder.classEntry("TestClass")    // 2 (CpClass pointing to 1)
        builder.utf8("java/lang/Object")   // 3
        builder.classEntry("java/lang/Object") // 4
        builder.utf8("fieldName")          // 5
        builder.utf8("I")                  // 6
        builder.nameAndType("fieldName", "I") // 7
        builder.integer(42)                // 8
        builder.long(100L)                 // 9, 10 (null)
        builder.float(3.14f)               // 11
        builder.double(2.718)              // 12, 13 (null)
        builder.string("hello")            // uses utf8 for "hello" then CpString
        return builder.build()
    }

    @Nested
    inner class Indexing {

        @Test
        fun getValidIndex() {
            val pool = buildPool()
            val entry = pool[1]
            assertTrue(entry is CpUtf8)
            assertEquals("TestClass", (entry as CpUtf8).value)
        }

        @Test
        fun getOrNullReturnsNullForEmptySlot() {
            val pool = buildPool()
            assertNull(pool.getOrNull(10)) // slot after long
        }

        @Test
        fun getOrNullReturnsNullForOutOfBounds() {
            val pool = buildPool()
            assertNull(pool.getOrNull(999))
        }

        @Test
        fun getInvalidIndexThrows() {
            val pool = buildPool()
            assertFailsWith<IllegalArgumentException> {
                pool[999]
            }
        }

        @Test
        fun getIndexZeroThrows() {
            val pool = buildPool()
            assertFailsWith<IllegalArgumentException> {
                pool[0]
            }
        }

        @Test
        fun size() {
            val builder = ConstantPoolBuilder()
            builder.utf8("a")
            builder.utf8("b")
            val pool = builder.build()
            assertEquals(3, pool.size) // index 0 (null) + 2 entries
        }
    }

    @Nested
    inner class Utf8Lookup {

        @Test
        fun validUtf8() {
            val pool = buildPool()
            assertEquals("TestClass", pool.utf8(1))
        }

        @Test
        fun nonUtf8ThrowsWithMessage() {
            val pool = buildPool()
            val ex = assertFailsWith<IllegalArgumentException> {
                pool.utf8(2) // index 2 is CpClass, not CpUtf8
            }
            assertTrue(ex.message!!.contains("expected Utf8"))
        }
    }

    @Nested
    inner class ClassNameLookup {

        @Test
        fun validClassName() {
            val pool = buildPool()
            assertEquals("TestClass", pool.className(2))
        }

        @Test
        fun nonClassThrowsWithMessage() {
            val pool = buildPool()
            val ex = assertFailsWith<IllegalArgumentException> {
                pool.className(1) // index 1 is CpUtf8, not CpClass
            }
            assertTrue(ex.message!!.contains("expected Class"))
        }
    }

    @Nested
    inner class NameAndTypeLookup {

        @Test
        fun validNameAndType() {
            val pool = buildPool()
            val result = pool.nameAndType(7)
            assertEquals("fieldName", result.first)
            assertEquals("I", result.second)
        }
    }

    @Nested
    inner class AllEntries {

        @Test
        fun returnsAllIndexedEntries() {
            val pool = buildPool()
            val all = pool.allEntries()
            assertEquals(pool.size, all.size)
            // Index 0 should be null
            assertNull(all[0].value)
            // Index 1 should be CpUtf8
            assertTrue(all[1].value is CpUtf8)
        }
    }

    @Nested
    inner class FindAll {

        @Test
        fun findAllUtf8() {
            val pool = buildPool()
            val utf8s = pool.findAll<CpUtf8>()
            assertTrue(utf8s.isNotEmpty())
            for (iv in utf8s) {
                assertTrue(iv.value is CpUtf8)
            }
        }

        @Test
        fun findAllClasses() {
            val pool = buildPool()
            val classes = pool.findAll<CpClass>()
            assertTrue(classes.size >= 2) // TestClass and java/lang/Object
        }

        @Test
        fun findAllIntegers() {
            val pool = buildPool()
            val ints = pool.findAll<CpInteger>()
            assertEquals(1, ints.size)
            assertEquals(42, ints[0].value.value)
        }

        @Test
        fun findAllLongs() {
            val pool = buildPool()
            val longs = pool.findAll<CpLong>()
            assertEquals(1, longs.size)
            assertEquals(100L, longs[0].value.value)
        }

        @Test
        fun findAllDoubles() {
            val pool = buildPool()
            val doubles = pool.findAll<CpDouble>()
            assertEquals(1, doubles.size)
            assertEquals(2.718, doubles[0].value.value)
        }

        @Test
        fun findAllFloats() {
            val pool = buildPool()
            val floats = pool.findAll<CpFloat>()
            assertEquals(1, floats.size)
            assertEquals(3.14f, floats[0].value.value)
        }

        @Test
        fun findAllStrings() {
            val pool = buildPool()
            val strings = pool.findAll<CpString>()
            assertEquals(1, strings.size)
        }

        @Test
        fun findAllReturnsEmptyForAbsentType() {
            val builder = ConstantPoolBuilder()
            builder.utf8("only utf8")
            val pool = builder.build()
            val methodRefs = pool.findAll<CpMethodRef>()
            assertTrue(methodRefs.isEmpty())
        }

        @Test
        fun findAllWithClassParameter() {
            val pool = buildPool()
            val utf8s = pool.findAll(CpUtf8::class.java)
            assertTrue(utf8s.isNotEmpty())
        }
    }

    @Nested
    inner class CpEntryTags {

        @Test
        fun entryTagValues() {
            assertEquals(1, CpUtf8.TAG)
            assertEquals(3, CpInteger.TAG)
            assertEquals(4, CpFloat.TAG)
            assertEquals(5, CpLong.TAG)
            assertEquals(6, CpDouble.TAG)
            assertEquals(7, CpClass.TAG)
            assertEquals(8, CpString.TAG)
            assertEquals(9, CpFieldRef.TAG)
            assertEquals(10, CpMethodRef.TAG)
            assertEquals(11, CpInterfaceMethodRef.TAG)
            assertEquals(12, CpNameAndType.TAG)
            assertEquals(15, CpMethodHandle.TAG)
            assertEquals(16, CpMethodType.TAG)
            assertEquals(17, CpDynamic.TAG)
            assertEquals(18, CpInvokeDynamic.TAG)
            assertEquals(19, CpModule.TAG)
            assertEquals(20, CpPackage.TAG)
        }

        @Test
        fun entryTagMatchesInstanceTag() {
            assertEquals(CpUtf8.TAG, CpUtf8("test").tag)
            assertEquals(CpInteger.TAG, CpInteger(1).tag)
            assertEquals(CpFloat.TAG, CpFloat(1.0f).tag)
            assertEquals(CpLong.TAG, CpLong(1L).tag)
            assertEquals(CpDouble.TAG, CpDouble(1.0).tag)
            assertEquals(CpClass.TAG, CpClass(1).tag)
            assertEquals(CpString.TAG, CpString(1).tag)
            assertEquals(CpFieldRef.TAG, CpFieldRef(1, 2).tag)
            assertEquals(CpMethodRef.TAG, CpMethodRef(1, 2).tag)
            assertEquals(CpInterfaceMethodRef.TAG, CpInterfaceMethodRef(1, 2).tag)
            assertEquals(CpNameAndType.TAG, CpNameAndType(1, 2).tag)
            assertEquals(CpMethodHandle.TAG, CpMethodHandle(1, 2).tag)
            assertEquals(CpMethodType.TAG, CpMethodType(1).tag)
            assertEquals(CpDynamic.TAG, CpDynamic(1, 2).tag)
            assertEquals(CpInvokeDynamic.TAG, CpInvokeDynamic(1, 2).tag)
            assertEquals(CpModule.TAG, CpModule(1).tag)
            assertEquals(CpPackage.TAG, CpPackage(1).tag)
        }
    }

    @Nested
    inner class CpEntryEquality {

        @Test
        fun utf8Equality() {
            assertEquals(CpUtf8("hello"), CpUtf8("hello"))
            assertTrue(CpUtf8("a") != CpUtf8("b"))
        }

        @Test
        fun integerEquality() {
            assertEquals(CpInteger(42), CpInteger(42))
            assertTrue(CpInteger(1) != CpInteger(2))
        }

        @Test
        fun classEquality() {
            assertEquals(CpClass(5), CpClass(5))
            assertTrue(CpClass(1) != CpClass(2))
        }

        @Test
        fun fieldRefEquality() {
            assertEquals(CpFieldRef(1, 2), CpFieldRef(1, 2))
            assertTrue(CpFieldRef(1, 2) != CpFieldRef(1, 3))
        }

        @Test
        fun methodRefEquality() {
            assertEquals(CpMethodRef(1, 2), CpMethodRef(1, 2))
            assertTrue(CpMethodRef(1, 2) != CpMethodRef(3, 2))
        }
    }
}
