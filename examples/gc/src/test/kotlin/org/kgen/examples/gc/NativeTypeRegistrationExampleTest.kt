package org.kgen.examples.gc

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NativeTypeRegistrationExampleTest {

    @Test
    fun registerNativeStringLayout() {
        val result = NativeTypeRegistrationExample.registerNativeStringLayout()

        assertTrue(result.typeName.contains("NativeString"))
        assertTrue(result.typeId > 0)
        assertTrue(result.fieldCount >= 2)
        assertTrue(result.fieldNames.contains("data"))
        assertTrue(result.fieldNames.contains("length"))
        assertTrue(result.totalSize >= 16 + 8)
        assertTrue(result.hasDestructor)
        assertEquals("destroy", result.destructorMethodName)
    }

    @Test
    fun gcManagedNativeString() {
        val result = NativeTypeRegistrationExample.gcManagedNativeString()

        assertEquals(0x1000L, result.liveDataPointer)
        assertEquals(0x2000L, result.deadDataPointer)
        assertTrue(result.reclaimedBytes > 0)
    }
}
