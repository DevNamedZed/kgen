package org.kgen.examples.api

import kotlin.test.Test
import kotlin.test.assertTrue

class JavaToNativeJavaExampleTest {

    @Test
    fun buildCalculatorClassProducesValidClassFile() {
        val classBytes = JavaToNativeJavaExample.buildCalculatorClass()
        assertTrue(classBytes.isNotEmpty())
        assertTrue(classBytes[0] == 0xCA.toByte())
        assertTrue(classBytes[1] == 0xFE.toByte())
        assertTrue(classBytes[2] == 0xBA.toByte())
        assertTrue(classBytes[3] == 0xBE.toByte())
    }

    @Test
    fun compileToNativeProducesExecutable() {
        val classBytes = JavaToNativeJavaExample.buildCalculatorClass()
        val executable = JavaToNativeJavaExample.compileToNative(classBytes)
        assertTrue(executable.isNotEmpty())
        assertTrue(executable.size > 100)
    }
}
