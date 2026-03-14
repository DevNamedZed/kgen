package org.kgen.ir

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class PipelinePhaseTest {

    @Nested
    inner class PhaseOrdering {

        @Test
        fun phasesAreOrdered() {
            assertTrue(PipelinePhase.FRONTEND_OBJECT.order < PipelinePhase.POST_OBJECT_LOWERING.order)
            assertTrue(PipelinePhase.POST_OBJECT_LOWERING.order < PipelinePhase.POST_RUNTIME_LOWERING.order)
            assertTrue(PipelinePhase.POST_RUNTIME_LOWERING.order < PipelinePhase.BACKEND_LEGAL.order)
        }

        @Test
        fun allFourPhasesExist() {
            assertEquals(4, PipelinePhase.entries.size)
        }
    }

    @Nested
    inner class PostPhaseInvariants {

        @Test
        fun postObjectLoweringAbsentContainsObjectInstructions() {
            val absent = PipelinePhase.POST_OBJECT_LOWERING_ABSENT
            assertTrue("NewObject" in absent)
            assertTrue("VirtualCall" in absent)
            assertTrue("InterfaceCall" in absent)
            assertTrue("GetField" in absent)
            assertTrue("PutField" in absent)
            assertTrue("ArrayGet" in absent)
            assertTrue("ArraySet" in absent)
            assertTrue("Throw" in absent)
            assertTrue("Box" in absent)
            assertTrue("Unbox" in absent)
        }

        @Test
        fun postRuntimeLoweringAbsentContainsRuntimeInstructions() {
            val absent = PipelinePhase.POST_RUNTIME_LOWERING_ABSENT
            assertTrue("GCAlloc" in absent)
            assertTrue("WriteBarrier" in absent)
            assertTrue("ReadBarrier" in absent)
        }

        @Test
        fun postObjectAbsentDoesNotOverlapWithRuntime() {
            val objectAbsent = PipelinePhase.POST_OBJECT_LOWERING_ABSENT
            val runtimeAbsent = PipelinePhase.POST_RUNTIME_LOWERING_ABSENT
            val overlap = objectAbsent.intersect(runtimeAbsent)
            assertTrue(overlap.isEmpty(), "Object and runtime absent sets should not overlap: $overlap")
        }
    }
}
