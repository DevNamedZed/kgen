package org.kgen.target.jvm

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccessFlagsTest {

    @Nested
    inner class FlagValues {

        @Test
        fun visibilityFlags() {
            assertEquals(0x0001, AccessFlags.PUBLIC)
            assertEquals(0x0002, AccessFlags.PRIVATE)
            assertEquals(0x0004, AccessFlags.PROTECTED)
        }

        @Test
        fun modifierFlags() {
            assertEquals(0x0008, AccessFlags.STATIC)
            assertEquals(0x0010, AccessFlags.FINAL)
            assertEquals(0x0100, AccessFlags.NATIVE)
            assertEquals(0x0400, AccessFlags.ABSTRACT)
        }

        @Test
        fun sharedBitFlags() {
            assertEquals(AccessFlags.SYNCHRONIZED, AccessFlags.SUPER)
            assertEquals(AccessFlags.BRIDGE, AccessFlags.VOLATILE)
            assertEquals(AccessFlags.VARARGS, AccessFlags.TRANSIENT)
            assertEquals(AccessFlags.MODULE, AccessFlags.MANDATED)
        }

        @Test
        fun classFlags() {
            assertEquals(0x0200, AccessFlags.INTERFACE)
            assertEquals(0x2000, AccessFlags.ANNOTATION)
            assertEquals(0x4000, AccessFlags.ENUM)
            assertEquals(0x8000, AccessFlags.MODULE)
        }

        @Test
        fun syntheticFlag() {
            assertEquals(0x1000, AccessFlags.SYNTHETIC)
        }

        @Test
        fun strictFlag() {
            assertEquals(0x0800, AccessFlags.STRICT)
        }
    }

    @Nested
    inner class CombiningFlags {

        @Test
        fun orCombinesFlags() {
            val flags = AccessFlags.PUBLIC or AccessFlags.STATIC or AccessFlags.FINAL
            assertTrue(flags and AccessFlags.PUBLIC != 0)
            assertTrue(flags and AccessFlags.STATIC != 0)
            assertTrue(flags and AccessFlags.FINAL != 0)
            assertTrue(flags and AccessFlags.PRIVATE == 0)
        }
    }

    @Nested
    inner class ToStringClassContext {

        @Test
        fun publicClass() {
            val str = AccessFlags.toString(AccessFlags.PUBLIC, AccessFlags.Context.CLASS)
            assertTrue("public" in str)
        }

        @Test
        fun publicAbstractInterface() {
            val flags = AccessFlags.PUBLIC or AccessFlags.ABSTRACT or AccessFlags.INTERFACE
            val str = AccessFlags.toString(flags, AccessFlags.Context.CLASS)
            assertTrue("public" in str)
            assertTrue("abstract" in str)
            assertTrue("interface" in str)
        }

        @Test
        fun superFlag() {
            val str = AccessFlags.toString(AccessFlags.SUPER, AccessFlags.Context.CLASS)
            assertTrue("super" in str)
        }

        @Test
        fun annotationFlag() {
            val str = AccessFlags.toString(AccessFlags.ANNOTATION, AccessFlags.Context.CLASS)
            assertTrue("annotation" in str)
        }

        @Test
        fun enumFlag() {
            val str = AccessFlags.toString(AccessFlags.ENUM, AccessFlags.Context.CLASS)
            assertTrue("enum" in str)
        }

        @Test
        fun moduleFlag() {
            val str = AccessFlags.toString(AccessFlags.MODULE, AccessFlags.Context.CLASS)
            assertTrue("module" in str)
        }

        @Test
        fun syntheticInClassContext() {
            val str = AccessFlags.toString(AccessFlags.SYNTHETIC, AccessFlags.Context.CLASS)
            assertTrue("synthetic" in str)
        }

        @Test
        fun emptyFlags() {
            val str = AccessFlags.toString(0, AccessFlags.Context.CLASS)
            assertEquals("", str)
        }
    }

    @Nested
    inner class ToStringMethodContext {

        @Test
        fun synchronizedMethod() {
            val str = AccessFlags.toString(AccessFlags.SYNCHRONIZED, AccessFlags.Context.METHOD)
            assertTrue("synchronized" in str)
        }

        @Test
        fun bridgeMethod() {
            val str = AccessFlags.toString(AccessFlags.BRIDGE, AccessFlags.Context.METHOD)
            assertTrue("bridge" in str)
        }

        @Test
        fun varargsMethod() {
            val str = AccessFlags.toString(AccessFlags.VARARGS, AccessFlags.Context.METHOD)
            assertTrue("varargs" in str)
        }

        @Test
        fun nativeMethod() {
            val str = AccessFlags.toString(AccessFlags.NATIVE, AccessFlags.Context.METHOD)
            assertTrue("native" in str)
        }

        @Test
        fun abstractMethod() {
            val str = AccessFlags.toString(AccessFlags.ABSTRACT, AccessFlags.Context.METHOD)
            assertTrue("abstract" in str)
        }

        @Test
        fun strictfpMethod() {
            val str = AccessFlags.toString(AccessFlags.STRICT, AccessFlags.Context.METHOD)
            assertTrue("strictfp" in str)
        }

        @Test
        fun publicStaticFinal() {
            val flags = AccessFlags.PUBLIC or AccessFlags.STATIC or AccessFlags.FINAL
            val str = AccessFlags.toString(flags, AccessFlags.Context.METHOD)
            assertTrue("public" in str)
            assertTrue("static" in str)
            assertTrue("final" in str)
        }

        @Test
        fun syntheticMethod() {
            val str = AccessFlags.toString(AccessFlags.SYNTHETIC, AccessFlags.Context.METHOD)
            assertTrue("synthetic" in str)
        }
    }

    @Nested
    inner class ToStringFieldContext {

        @Test
        fun volatileField() {
            val str = AccessFlags.toString(AccessFlags.VOLATILE, AccessFlags.Context.FIELD)
            assertTrue("volatile" in str)
        }

        @Test
        fun transientField() {
            val str = AccessFlags.toString(AccessFlags.TRANSIENT, AccessFlags.Context.FIELD)
            assertTrue("transient" in str)
        }

        @Test
        fun enumField() {
            val str = AccessFlags.toString(AccessFlags.ENUM, AccessFlags.Context.FIELD)
            assertTrue("enum" in str)
        }

        @Test
        fun privateStaticFinal() {
            val flags = AccessFlags.PRIVATE or AccessFlags.STATIC or AccessFlags.FINAL
            val str = AccessFlags.toString(flags, AccessFlags.Context.FIELD)
            assertTrue("private" in str)
            assertTrue("static" in str)
            assertTrue("final" in str)
        }

        @Test
        fun syntheticField() {
            val str = AccessFlags.toString(AccessFlags.SYNTHETIC, AccessFlags.Context.FIELD)
            assertTrue("synthetic" in str)
        }
    }

    @Nested
    inner class DefaultContext {

        @Test
        fun defaultContextIsClass() {
            val flags = AccessFlags.PUBLIC or AccessFlags.INTERFACE
            val str = AccessFlags.toString(flags)
            assertTrue("interface" in str)
        }
    }
}
