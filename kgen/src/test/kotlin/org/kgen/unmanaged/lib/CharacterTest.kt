package org.kgen.unmanaged.lib

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CharacterTest {

    @Test
    fun isDigitWithDigits() {
        for (digit in '0'..'9') {
            assertTrue(Character.isDigit(digit.code), "'$digit' should be a digit")
        }
    }

    @Test
    fun isDigitWithNonDigits() {
        assertFalse(Character.isDigit('a'.code))
        assertFalse(Character.isDigit('Z'.code))
        assertFalse(Character.isDigit(' '.code))
        assertFalse(Character.isDigit('/'.code))
        assertFalse(Character.isDigit(':'.code))
    }

    @Test
    fun isLetterWithLetters() {
        for (letter in 'a'..'z') {
            assertTrue(Character.isLetter(letter.code), "'$letter' should be a letter")
        }
        for (letter in 'A'..'Z') {
            assertTrue(Character.isLetter(letter.code), "'$letter' should be a letter")
        }
    }

    @Test
    fun isLetterWithNonLetters() {
        assertFalse(Character.isLetter('0'.code))
        assertFalse(Character.isLetter(' '.code))
        assertFalse(Character.isLetter('!'.code))
    }

    @Test
    fun isLetterOrDigit() {
        assertTrue(Character.isLetterOrDigit('a'.code))
        assertTrue(Character.isLetterOrDigit('Z'.code))
        assertTrue(Character.isLetterOrDigit('5'.code))
        assertFalse(Character.isLetterOrDigit(' '.code))
        assertFalse(Character.isLetterOrDigit('!'.code))
    }

    @Test
    fun isWhitespace() {
        assertTrue(Character.isWhitespace(' '.code))
        assertTrue(Character.isWhitespace('\t'.code))
        assertTrue(Character.isWhitespace('\n'.code))
        assertTrue(Character.isWhitespace('\r'.code))
        assertTrue(Character.isWhitespace(0x0C))  // form feed
        assertFalse(Character.isWhitespace('a'.code))
        assertFalse(Character.isWhitespace('0'.code))
    }

    @Test
    fun isUpperCase() {
        assertTrue(Character.isUpperCase('A'.code))
        assertTrue(Character.isUpperCase('Z'.code))
        assertFalse(Character.isUpperCase('a'.code))
        assertFalse(Character.isUpperCase('0'.code))
    }

    @Test
    fun isLowerCase() {
        assertTrue(Character.isLowerCase('a'.code))
        assertTrue(Character.isLowerCase('z'.code))
        assertFalse(Character.isLowerCase('A'.code))
        assertFalse(Character.isLowerCase('0'.code))
    }

    @Test
    fun toUpperCase() {
        assertEquals('A'.code, Character.toUpperCase('a'.code))
        assertEquals('Z'.code, Character.toUpperCase('z'.code))
        assertEquals('A'.code, Character.toUpperCase('A'.code))
        assertEquals('0'.code, Character.toUpperCase('0'.code))
    }

    @Test
    fun toLowerCase() {
        assertEquals('a'.code, Character.toLowerCase('A'.code))
        assertEquals('z'.code, Character.toLowerCase('Z'.code))
        assertEquals('a'.code, Character.toLowerCase('a'.code))
        assertEquals('0'.code, Character.toLowerCase('0'.code))
    }

    @Test
    fun isAlphabetic() {
        assertTrue(Character.isAlphabetic('a'.code))
        assertTrue(Character.isAlphabetic('Z'.code))
        assertFalse(Character.isAlphabetic('0'.code))
        assertFalse(Character.isAlphabetic(' '.code))
    }

    @Test
    fun digitInBase10() {
        assertEquals(0, Character.digit('0'.code, 10))
        assertEquals(9, Character.digit('9'.code, 10))
        assertEquals(-1, Character.digit('a'.code, 10))
    }

    @Test
    fun digitInBase16() {
        assertEquals(10, Character.digit('a'.code, 16))
        assertEquals(10, Character.digit('A'.code, 16))
        assertEquals(15, Character.digit('f'.code, 16))
        assertEquals(15, Character.digit('F'.code, 16))
        assertEquals(-1, Character.digit('g'.code, 16))
    }

    @Test
    fun digitInvalidRadix() {
        assertEquals(-1, Character.digit('5'.code, 1))
        assertEquals(-1, Character.digit('5'.code, 37))
    }
}
