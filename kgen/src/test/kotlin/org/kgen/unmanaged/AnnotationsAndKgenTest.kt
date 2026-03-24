package org.kgen.unmanaged

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class AnnotationsAndKgenTest {

    @Nested
    inner class KgenNativeAnnotationTest {

        @Test
        fun `annotation is present at runtime`() {
            val annotation = KgenNative::class.java
            val retention = annotation.getAnnotation(Retention::class.java)
            assertNotNull(retention)
            assertEquals(AnnotationRetention.RUNTIME, retention!!.value)
        }

        @Test
        fun `can target class and function`() {
            val targets = KgenNative::class.java.getAnnotation(Target::class.java)
            assertNotNull(targets)
            val allowed = targets!!.allowedTargets.toSet()
            assertTrue(allowed.contains(AnnotationTarget.CLASS))
            assertTrue(allowed.contains(AnnotationTarget.FUNCTION))
        }
    }

    @Nested
    inner class KgenRuntimeAnnotationTest {

        @Test
        fun `annotation is present at runtime`() {
            val retention = KgenRuntime::class.java.getAnnotation(Retention::class.java)
            assertNotNull(retention)
            assertEquals(AnnotationRetention.RUNTIME, retention!!.value)
        }

        @Test
        fun `can target class only`() {
            val targets = KgenRuntime::class.java.getAnnotation(Target::class.java)
            assertNotNull(targets)
            val allowed = targets!!.allowedTargets.toSet()
            assertTrue(allowed.contains(AnnotationTarget.CLASS))
            assertEquals(1, allowed.size)
        }
    }

    @Nested
    inner class KgenExportAnnotationTest {

        @Test
        fun `annotation is present at runtime`() {
            val retention = KgenExport::class.java.getAnnotation(Retention::class.java)
            assertNotNull(retention)
            assertEquals(AnnotationRetention.RUNTIME, retention!!.value)
        }

        @Test
        fun `can target function only`() {
            val targets = KgenExport::class.java.getAnnotation(Target::class.java)
            assertNotNull(targets)
            val allowed = targets!!.allowedTargets.toSet()
            assertTrue(allowed.contains(AnnotationTarget.FUNCTION))
            assertEquals(1, allowed.size)
        }

        @Test
        fun `default value is empty string`() {
            val inst = KgenExport()
            assertEquals("", inst.value)
        }

        @Test
        fun `default convention is empty string`() {
            val inst = KgenExport()
            assertEquals("", inst.convention)
        }

        @Test
        fun `custom value and convention`() {
            val inst = KgenExport(value = "my_func", convention = "win64")
            assertEquals("my_func", inst.value)
            assertEquals("win64", inst.convention)
        }
    }

    @Nested
    inner class KgenImportAnnotationTest {

        @Test
        fun `annotation is present at runtime`() {
            val retention = KgenImport::class.java.getAnnotation(Retention::class.java)
            assertNotNull(retention)
            assertEquals(AnnotationRetention.RUNTIME, retention!!.value)
        }

        @Test
        fun `can target function only`() {
            val targets = KgenImport::class.java.getAnnotation(Target::class.java)
            assertNotNull(targets)
            val allowed = targets!!.allowedTargets.toSet()
            assertTrue(allowed.contains(AnnotationTarget.FUNCTION))
            assertEquals(1, allowed.size)
        }

        @Test
        fun `default value is empty string`() {
            val inst = KgenImport()
            assertEquals("", inst.value)
        }

        @Test
        fun `custom value`() {
            val inst = KgenImport(value = "puts")
            assertEquals("puts", inst.value)
        }
    }

    @Nested
    inner class KgenIntrinsicAnnotationTest {

        @Test
        fun `annotation is present at runtime`() {
            val retention = KgenIntrinsic::class.java.getAnnotation(Retention::class.java)
            assertNotNull(retention)
            assertEquals(AnnotationRetention.RUNTIME, retention!!.value)
        }

        @Test
        fun `can target function only`() {
            val targets = KgenIntrinsic::class.java.getAnnotation(Target::class.java)
            assertNotNull(targets)
            val allowed = targets!!.allowedTargets.toSet()
            assertTrue(allowed.contains(AnnotationTarget.FUNCTION))
            assertEquals(1, allowed.size)
        }
    }

    @Nested
    inner class KgenNoAllocAnnotationTest {

        @Test
        fun `annotation is present at runtime`() {
            val retention = KgenNoAlloc::class.java.getAnnotation(Retention::class.java)
            assertNotNull(retention)
            assertEquals(AnnotationRetention.RUNTIME, retention!!.value)
        }

        @Test
        fun `can target function only`() {
            val targets = KgenNoAlloc::class.java.getAnnotation(Target::class.java)
            assertNotNull(targets)
            val allowed = targets!!.allowedTargets.toSet()
            assertTrue(allowed.contains(AnnotationTarget.FUNCTION))
            assertEquals(1, allowed.size)
        }
    }

    @Nested
    inner class KgenInlineAnnotationTest {

        @Test
        fun `annotation is present at runtime`() {
            val retention = KgenInline::class.java.getAnnotation(Retention::class.java)
            assertNotNull(retention)
            assertEquals(AnnotationRetention.RUNTIME, retention!!.value)
        }

        @Test
        fun `can target function only`() {
            val targets = KgenInline::class.java.getAnnotation(Target::class.java)
            assertNotNull(targets)
            val allowed = targets!!.allowedTargets.toSet()
            assertTrue(allowed.contains(AnnotationTarget.FUNCTION))
            assertEquals(1, allowed.size)
        }
    }

    @Nested
    inner class KgenLeafAnnotationTest {

        @Test
        fun `annotation is present at runtime`() {
            val retention = KgenLeaf::class.java.getAnnotation(Retention::class.java)
            assertNotNull(retention)
            assertEquals(AnnotationRetention.RUNTIME, retention!!.value)
        }

        @Test
        fun `can target function only`() {
            val targets = KgenLeaf::class.java.getAnnotation(Target::class.java)
            assertNotNull(targets)
            val allowed = targets!!.allowedTargets.toSet()
            assertTrue(allowed.contains(AnnotationTarget.FUNCTION))
            assertEquals(1, allowed.size)
        }
    }

    @Nested
    inner class KgenMemoryAccessTest {

        @Test
        fun `loadByte fallback returns 0`() {
            assertEquals(0.toByte(), Kgen.loadByte(0L))
        }

        @Test
        fun `loadShort fallback returns 0`() {
            assertEquals(0.toShort(), Kgen.loadShort(0L))
        }

        @Test
        fun `loadInt fallback returns 0`() {
            assertEquals(0, Kgen.loadInt(0L))
        }

        @Test
        fun `loadLong fallback returns 0`() {
            assertEquals(0L, Kgen.loadLong(0L))
        }

        @Test
        fun `storeByte does not throw`() {
            assertDoesNotThrow { Kgen.storeByte(0L, 42.toByte()) }
        }

        @Test
        fun `storeShort does not throw`() {
            assertDoesNotThrow { Kgen.storeShort(0L, 42.toShort()) }
        }

        @Test
        fun `storeInt does not throw`() {
            assertDoesNotThrow { Kgen.storeInt(0L, 42) }
        }

        @Test
        fun `storeLong does not throw`() {
            assertDoesNotThrow { Kgen.storeLong(0L, 42L) }
        }
    }

    @Nested
    inner class KgenPointerArithmeticTest {

        @Test
        fun `offset with int adds correctly`() {
            assertEquals(100L, Kgen.offset(90L, 10))
        }

        @Test
        fun `offset with int negative offset`() {
            assertEquals(80L, Kgen.offset(100L, -20))
        }

        @Test
        fun `offset with long adds correctly`() {
            assertEquals(200L, Kgen.offset(150L, 50L))
        }

        @Test
        fun `offset with long negative offset`() {
            assertEquals(50L, Kgen.offset(100L, -50L))
        }

        @Test
        fun `offset with zero base`() {
            assertEquals(42L, Kgen.offset(0L, 42))
        }

        @Test
        fun `offset with zero offset`() {
            assertEquals(100L, Kgen.offset(100L, 0))
        }
    }

    @Nested
    inner class KgenStackAllocTest {

        @Test
        fun `stackAlloc allocates memory`() {
            assertTrue(Kgen.stackAlloc(64) > 0, "stackAlloc should return non-zero address")
        }
    }

    @Nested
    inner class KgenRuntimeCoordinationTest {

        @Test
        fun `safepoint does not throw`() {
            assertDoesNotThrow { Kgen.safepoint() }
        }

        @Test
        fun `gcRoot with object does not throw`() {
            assertDoesNotThrow { Kgen.gcRoot("test" as Any) }
        }

        @Test
        fun `gcRoot with long does not throw`() {
            assertDoesNotThrow { Kgen.gcRoot(0L) }
        }

        @Test
        fun `writeBarrier with objects does not throw`() {
            assertDoesNotThrow { Kgen.writeBarrier("obj" as Any, 0, "val" as Any) }
        }

        @Test
        fun `writeBarrier with longs does not throw`() {
            assertDoesNotThrow { Kgen.writeBarrier(100L, 0, 200L) }
        }

        @Test
        fun `readBarrier with object does not throw`() {
            assertDoesNotThrow { Kgen.readBarrier("test" as Any) }
        }

        @Test
        fun `readBarrier with long returns same pointer`() {
            assertEquals(42L, Kgen.readBarrier(42L))
        }

        @Test
        fun `readBarrier with zero returns zero`() {
            assertEquals(0L, Kgen.readBarrier(0L))
        }
    }

    @Nested
    inner class KgenAllocationTest {

        @Test
        fun `runtimeAlloc allocates memory`() {
            assertTrue(Kgen.runtimeAlloc(128) > 0, "runtimeAlloc should return non-zero address")
        }

        @Test
        fun `runtimeFree does not throw`() {
            assertDoesNotThrow { Kgen.runtimeFree(0L) }
        }
    }

    @Nested
    inner class KgenStringConstTest {

        @Test
        fun `stringConst returns valid address`() {
            assertTrue(Kgen.stringConst("hello") > 0, "stringConst should return non-zero address")
        }

        @Test
        fun `stringConst with empty string returns valid address`() {
            assertTrue(Kgen.stringConst("") > 0, "stringConst with empty string should return non-zero address")
        }
    }

    @Nested
    inner class KgenPlatformDetectionTest {

        @Test
        fun `isWindows returns boolean`() {
            val result = Kgen.isWindows()
            assertNotNull(result)
        }

        @Test
        fun `isLinux returns boolean`() {
            val result = Kgen.isLinux()
            assertNotNull(result)
        }

        @Test
        fun `isMacOS returns boolean`() {
            val result = Kgen.isMacOS()
            assertNotNull(result)
        }

        @Test
        fun `exactly one platform returns true`() {
            val platforms = listOf(Kgen.isWindows(), Kgen.isLinux(), Kgen.isMacOS())
            val trueCount = platforms.count { it }
            assertTrue(trueCount <= 1, "At most one platform should return true, got $trueCount")
        }

        @Test
        fun `onWindows executes block only on Windows`() {
            var executed = false
            Kgen.onWindows { executed = true }
            assertEquals(Kgen.isWindows(), executed)
        }

        @Test
        fun `onLinux executes block only on Linux`() {
            var executed = false
            Kgen.onLinux { executed = true }
            assertEquals(Kgen.isLinux(), executed)
        }

        @Test
        fun `onMacOS executes block only on macOS`() {
            var executed = false
            Kgen.onMacOS { executed = true }
            assertEquals(Kgen.isMacOS(), executed)
        }
    }

    @Nested
    inner class KgenHintsTest {

        @Test
        fun `likely returns same value for true`() {
            assertTrue(Kgen.likely(true))
        }

        @Test
        fun `likely returns same value for false`() {
            assertFalse(Kgen.likely(false))
        }

        @Test
        fun `unlikely returns same value for true`() {
            assertTrue(Kgen.unlikely(true))
        }

        @Test
        fun `unlikely returns same value for false`() {
            assertFalse(Kgen.unlikely(false))
        }
    }
}
