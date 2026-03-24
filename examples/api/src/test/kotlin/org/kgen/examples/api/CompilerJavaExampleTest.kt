package org.kgen.examples.api

import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class CompilerJavaExampleTest {
    @Test fun singleFunction() { assertTrue(CompilerJavaExample.singleFunction().isNotEmpty()) }
    @Test fun classWithMethods() { assertTrue(CompilerJavaExample.classWithMethods().isNotEmpty()) }
    @Test fun loopExample() { assertTrue(CompilerJavaExample.loopExample().isNotEmpty()) }
    @Test fun multiModuleWithResources() { assertTrue(CompilerJavaExample.multiModuleWithResources().isNotEmpty()) }
}
