package org.kgen.binary.inspect

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class ExtractStringsTest {

    @Nested
    inner class BasicExtraction {
        @Test
        fun extractsSimpleString() {
            val data = "Hello World\u0000".toByteArray(Charsets.US_ASCII)
            val strings = extractStrings(data, 4)
            assertEquals(1, strings.size)
            assertEquals("Hello World", strings[0].second)
        }

        @Test
        fun extractsMultipleStrings() {
            val data = "Hello\u0000World\u0000".toByteArray(Charsets.US_ASCII)
            val strings = extractStrings(data, 4)
            assertEquals(2, strings.size)
            assertEquals("Hello", strings[0].second)
            assertEquals("World", strings[1].second)
        }

        @Test
        fun respectsMinLength() {
            val data = "Hi\u0000Hello\u0000AB\u0000Test\u0000".toByteArray(Charsets.US_ASCII)
            val strings = extractStrings(data, 4)
            assertEquals(2, strings.size)
            assertEquals("Hello", strings[0].second)
            assertEquals("Test", strings[1].second)
        }

        @Test
        fun minLengthOfOne() {
            val data = "A\u0000BC\u0000".toByteArray(Charsets.US_ASCII)
            val strings = extractStrings(data, 1)
            assertEquals(2, strings.size)
            assertEquals("A", strings[0].second)
            assertEquals("BC", strings[1].second)
        }
    }

    @Nested
    inner class Offsets {
        @Test
        fun reportsCorrectOffset() {
            val data = "\u0000\u0000\u0000Hello\u0000".toByteArray(Charsets.US_ASCII)
            val strings = extractStrings(data, 4)
            assertEquals(1, strings.size)
            assertEquals(3L, strings[0].first)
            assertEquals("Hello", strings[0].second)
        }

        @Test
        fun multipleStringsHaveCorrectOffsets() {
            val data = "ABCD\u0000\u0000EFGH\u0000".toByteArray(Charsets.US_ASCII)
            val strings = extractStrings(data, 4)
            assertEquals(2, strings.size)
            assertEquals(0L, strings[0].first)
            assertEquals(6L, strings[1].first)
        }
    }

    @Nested
    inner class EdgeCases {
        @Test
        fun emptyDataReturnsEmpty() {
            val strings = extractStrings(byteArrayOf(), 4)
            assertTrue(strings.isEmpty())
        }

        @Test
        fun allNullBytesReturnsEmpty() {
            val data = ByteArray(10) { 0 }
            val strings = extractStrings(data, 4)
            assertTrue(strings.isEmpty())
        }

        @Test
        fun allNonPrintableReturnsEmpty() {
            val data = ByteArray(10) { 0x01 }
            val strings = extractStrings(data, 4)
            assertTrue(strings.isEmpty())
        }

        @Test
        fun stringAtEndOfDataWithoutTerminator() {
            val data = "Hello".toByteArray(Charsets.US_ASCII)
            val strings = extractStrings(data, 4)
            assertEquals(1, strings.size)
            assertEquals("Hello", strings[0].second)
        }

        @Test
        fun shortStringAtEndIgnored() {
            val data = "AB".toByteArray(Charsets.US_ASCII)
            val strings = extractStrings(data, 4)
            assertTrue(strings.isEmpty())
        }

        @Test
        fun exactMinLength() {
            val data = "ABCD\u0000".toByteArray(Charsets.US_ASCII)
            val strings = extractStrings(data, 4)
            assertEquals(1, strings.size)
            assertEquals("ABCD", strings[0].second)
        }

        @Test
        fun belowMinLength() {
            val data = "ABC\u0000".toByteArray(Charsets.US_ASCII)
            val strings = extractStrings(data, 4)
            assertTrue(strings.isEmpty())
        }
    }

    @Nested
    inner class PrintableRange {
        @Test
        fun includesSpace() {
            val data = "A B C D\u0000".toByteArray(Charsets.US_ASCII)
            val strings = extractStrings(data, 4)
            assertEquals(1, strings.size)
            assertEquals("A B C D", strings[0].second)
        }

        @Test
        fun includesTilde() {
            val data = "test~value\u0000".toByteArray(Charsets.US_ASCII)
            val strings = extractStrings(data, 4)
            assertEquals(1, strings.size)
            assertTrue(strings[0].second.contains("~"))
        }

        @Test
        fun excludesControlChars() {
            val data = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05)
            val strings = extractStrings(data, 1)
            assertTrue(strings.isEmpty())
        }

        @Test
        fun excludesDel() {
            val data = byteArrayOf(0x7F, 0x7F, 0x7F, 0x7F, 0x7F)
            val strings = extractStrings(data, 1)
            assertTrue(strings.isEmpty())
        }

        @Test
        fun excludesHighBytes() {
            val data = byteArrayOf(0x80.toByte(), 0x90.toByte(), 0xFF.toByte(), 0xFE.toByte())
            val strings = extractStrings(data, 1)
            assertTrue(strings.isEmpty())
        }

        @Test
        fun mixedPrintableAndNonPrintable() {
            val data = byteArrayOf(
                0x01, 0x02,
                0x48, 0x65, 0x6C, 0x6C, 0x6F,  // Hello
                0x00,
                0xFF.toByte(), 0xFE.toByte(),
                0x57, 0x6F, 0x72, 0x6C, 0x64,  // World
                0x00,
            )
            val strings = extractStrings(data, 4)
            assertEquals(2, strings.size)
            assertEquals("Hello", strings[0].second)
            assertEquals("World", strings[1].second)
        }
    }

    @Nested
    inner class BinaryData {
        @Test
        fun extractsFromBinaryWithEmbeddedStrings() {
            val binary = ByteArray(100)
            val hello = "Hello, World!".toByteArray(Charsets.US_ASCII)
            System.arraycopy(hello, 0, binary, 20, hello.size)
            val strings = extractStrings(binary, 4)
            assertTrue(strings.any { it.second == "Hello, World!" })
            val match = strings.first { it.second == "Hello, World!" }
            assertEquals(20L, match.first)
        }
    }
}
