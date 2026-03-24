package org.kgen.unmanaged.lib

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.kgen.unmanaged.Kgen
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NativeStringBuilderTest {

    @BeforeEach
    fun resetMemory() {
        Kgen.resetHeap()
    }

    @Test
    fun newBuilderIsEmpty() {
        val builder = NativeStringBuilder()
        assertEquals(0, builder.length())
        assertTrue(builder.isEmpty())
    }

    @Test
    fun appendByte() {
        val builder = NativeStringBuilder()
        builder.appendByte('H'.code.toByte())
        builder.appendByte('i'.code.toByte())

        assertEquals(2, builder.length())
        assertEquals('H'.code.toByte(), builder.byteAt(0))
        assertEquals('i'.code.toByte(), builder.byteAt(1))
    }

    @Test
    fun appendStringConstant() {
        val builder = NativeStringBuilder()
        builder.append(Kgen.stringConst("Hello"))

        assertEquals(5, builder.length())
        assertEquals('H'.code.toByte(), builder.byteAt(0))
        assertEquals('e'.code.toByte(), builder.byteAt(1))
        assertEquals('l'.code.toByte(), builder.byteAt(2))
        assertEquals('l'.code.toByte(), builder.byteAt(3))
        assertEquals('o'.code.toByte(), builder.byteAt(4))
    }

    @Test
    fun appendMultipleStrings() {
        val builder = NativeStringBuilder()
        builder.append(Kgen.stringConst("Hello"))
        builder.append(Kgen.stringConst(", "))
        builder.append(Kgen.stringConst("World"))

        assertEquals(12, builder.length())
        assertStringEquals("Hello, World", builder)
    }

    @Test
    fun appendPositiveInt() {
        val builder = NativeStringBuilder()
        builder.appendInt(42)

        assertEquals(2, builder.length())
        assertStringEquals("42", builder)
    }

    @Test
    fun appendZeroInt() {
        val builder = NativeStringBuilder()
        builder.appendInt(0)

        assertEquals(1, builder.length())
        assertStringEquals("0", builder)
    }

    @Test
    fun appendNegativeInt() {
        val builder = NativeStringBuilder()
        builder.appendInt(-123)

        assertEquals(4, builder.length())
        assertStringEquals("-123", builder)
    }

    @Test
    fun appendLargeInt() {
        val builder = NativeStringBuilder()
        builder.appendInt(1000000)

        assertStringEquals("1000000", builder)
    }

    @Test
    fun appendPositiveLong() {
        val builder = NativeStringBuilder()
        builder.appendLong(9876543210L)

        assertStringEquals("9876543210", builder)
    }

    @Test
    fun appendZeroLong() {
        val builder = NativeStringBuilder()
        builder.appendLong(0L)

        assertStringEquals("0", builder)
    }

    @Test
    fun appendNegativeLong() {
        val builder = NativeStringBuilder()
        builder.appendLong(-42L)

        assertStringEquals("-42", builder)
    }

    @Test
    fun mixedAppends() {
        val builder = NativeStringBuilder()
        builder.append(Kgen.stringConst("count="))
        builder.appendInt(42)
        builder.append(Kgen.stringConst(", total="))
        builder.appendLong(100L)

        assertStringEquals("count=42, total=100", builder)
    }

    @Test
    fun clear() {
        val builder = NativeStringBuilder()
        builder.append(Kgen.stringConst("Hello"))
        builder.clear()

        assertEquals(0, builder.length())
        assertTrue(builder.isEmpty())
    }

    @Test
    fun appendAfterClear() {
        val builder = NativeStringBuilder()
        builder.append(Kgen.stringConst("Hello"))
        builder.clear()
        builder.append(Kgen.stringConst("World"))

        assertEquals(5, builder.length())
        assertStringEquals("World", builder)
    }

    @Test
    fun toAddress() {
        val builder = NativeStringBuilder()
        builder.append(Kgen.stringConst("Test"))

        val address = builder.toAddress()
        assertEquals('T'.code.toByte(), Kgen.loadByte(address))
        assertEquals('e'.code.toByte(), Kgen.loadByte(Kgen.offset(address, 1)))
        assertEquals('s'.code.toByte(), Kgen.loadByte(Kgen.offset(address, 2)))
        assertEquals('t'.code.toByte(), Kgen.loadByte(Kgen.offset(address, 3)))
        assertEquals(0.toByte(), Kgen.loadByte(Kgen.offset(address, 4)))
    }

    @Test
    fun notEmptyAfterAppend() {
        val builder = NativeStringBuilder()
        builder.appendByte('x'.code.toByte())
        assertFalse(builder.isEmpty())
    }

    @Test
    fun growsBeyondInitialCapacity() {
        val builder = NativeStringBuilder()
        for (index in 0 until 100) {
            builder.appendByte('A'.code.toByte())
        }
        assertEquals(100, builder.length())
        for (index in 0 until 100) {
            assertEquals('A'.code.toByte(), builder.byteAt(index))
        }
    }

    @Test
    fun destroy() {
        val builder = NativeStringBuilder()
        builder.append(Kgen.stringConst("Hello"))
        builder.destroy()
        assertEquals(0, builder.data)
        assertEquals(0, builder.length)
    }

    private fun assertStringEquals(expected: String, builder: NativeStringBuilder) {
        assertEquals(expected.length, builder.length())
        for (index in expected.indices) {
            assertEquals(
                expected[index].code.toByte(), builder.byteAt(index),
                "Mismatch at index $index: expected '${expected[index]}', got '${builder.byteAt(index).toInt().toChar()}'"
            )
        }
    }
}
