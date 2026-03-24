package org.kgen.examples.api

import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class PipelineExampleTest {
    @Test fun presetPipeline() { PipelineExample.presetPipeline() }
    @Test fun manualPipeline() { PipelineExample.manualPipeline() }
    @Test fun customStage() { PipelineExample.customStage() }
    @Test fun pipelineBuilder() { PipelineExample.pipelineBuilder() }
    @Test fun namedStages() { PipelineExample.namedStages() }
    @Test fun phaseValidation() { PipelineExample.phaseValidation() }
}
