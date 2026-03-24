package org.kgen.examples.gc

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ManagedHeapJavaExampleTest {

    @Test
    fun basicCollectionReclaimsMemory() {
        val reclaimed = ManagedHeapJavaExample.basicCollection()
        assertTrue(reclaimed > 0)
    }

    @Test
    fun referenceTracingFinalizesOnlyDetached() {
        val finalizedCount = ManagedHeapJavaExample.referenceTracing()
        assertEquals(1, finalizedCount)
    }
}
