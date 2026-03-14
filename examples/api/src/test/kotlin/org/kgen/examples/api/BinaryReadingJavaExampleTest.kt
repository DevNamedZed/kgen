package org.kgen.examples.api

import kotlin.test.Test
import kotlin.test.assertEquals

class BinaryReadingJavaExampleTest {

    @Test
    fun formatDetectionMatchesKotlinVersion() {
        val garbage = byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7)
        assertEquals("Unknown", BinaryReadingJavaExample.detectFormat(garbage))
    }
}
