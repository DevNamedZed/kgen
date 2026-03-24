package org.kgen.examples.gc

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ManagedHeapExampleTest {

    @Test
    fun basicCollectionFinalizesDeadObject() {
        val result = ManagedHeapExample.basicCollection()
        assertEquals(1, result.liveCount)
        assertEquals(1, result.deadCount)
        assertTrue(result.reclaimedBytes > 0)
    }

    @Test
    fun referenceTracingKeepsChainAlive() {
        val result = ManagedHeapExample.referenceTracing()
        assertEquals(3, result.chainLength)
        assertTrue(result.orphanFinalized)
        assertEquals(0, result.chainNodesFinalized)
    }

    @Test
    fun mixedTypeCollectionFinalizesCorrectly() {
        val result = ManagedHeapExample.mixedTypeCollection()
        assertEquals(8, result.totalAllocated)
        assertEquals(3, result.stringsFinalized)
        assertEquals(3, result.nodesFinalized)
        assertTrue(result.reclaimedBytes > 0)
    }
}
