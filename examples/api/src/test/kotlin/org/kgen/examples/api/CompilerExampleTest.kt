package org.kgen.examples.api

import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class CompilerExampleTest {
    @Test fun singleFunction() { CompilerExample.singleFunction() }
    @Test fun classWithMethods() { CompilerExample.classWithMethods() }
    @Test fun multiModule() { CompilerExample.multiModule() }
    @Test fun withResources() { CompilerExample.withResources() }
    @Test fun preBuiltModule() { CompilerExample.preBuiltModule() }
    @Test fun controlFlow() { CompilerExample.controlFlow() }
}
