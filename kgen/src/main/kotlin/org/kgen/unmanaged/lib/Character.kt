package org.kgen.unmanaged.lib

import org.kgen.unmanaged.KgenExport
import org.kgen.unmanaged.KgenNative

/**
 * Native implementations of `java.lang.Character` static methods.
 *
 * Each function maps a Java Character method to a direct native implementation
 * using simple ASCII-range checks.
 */
@KgenNative
object Character {

    @JavaMapping("java/lang/Character", "isDigit", "(C)Z")
    @KgenExport("kgen_char_isDigit")
    @JvmStatic
    fun isDigit(character: Int): Boolean {
        return character >= '0'.code && character <= '9'.code
    }

    @JavaMapping("java/lang/Character", "isLetter", "(C)Z")
    @KgenExport("kgen_char_isLetter")
    @JvmStatic
    fun isLetter(character: Int): Boolean {
        return (character >= 'a'.code && character <= 'z'.code) ||
               (character >= 'A'.code && character <= 'Z'.code)
    }

    @JavaMapping("java/lang/Character", "isLetterOrDigit", "(C)Z")
    @KgenExport("kgen_char_isLetterOrDigit")
    @JvmStatic
    fun isLetterOrDigit(character: Int): Boolean {
        return isLetter(character) || isDigit(character)
    }

    @JavaMapping("java/lang/Character", "isWhitespace", "(C)Z")
    @KgenExport("kgen_char_isWhitespace")
    @JvmStatic
    fun isWhitespace(character: Int): Boolean {
        return character == ' '.code ||
               character == '\t'.code ||
               character == '\n'.code ||
               character == '\r'.code ||
               character == 0x0C  // form feed
    }

    @JavaMapping("java/lang/Character", "isUpperCase", "(C)Z")
    @KgenExport("kgen_char_isUpperCase")
    @JvmStatic
    fun isUpperCase(character: Int): Boolean {
        return character >= 'A'.code && character <= 'Z'.code
    }

    @JavaMapping("java/lang/Character", "isLowerCase", "(C)Z")
    @KgenExport("kgen_char_isLowerCase")
    @JvmStatic
    fun isLowerCase(character: Int): Boolean {
        return character >= 'a'.code && character <= 'z'.code
    }

    @JavaMapping("java/lang/Character", "toUpperCase", "(C)C")
    @KgenExport("kgen_char_toUpperCase")
    @JvmStatic
    fun toUpperCase(character: Int): Int {
        if (character >= 'a'.code && character <= 'z'.code) {
            return character - 32
        }
        return character
    }

    @JavaMapping("java/lang/Character", "toLowerCase", "(C)C")
    @KgenExport("kgen_char_toLowerCase")
    @JvmStatic
    fun toLowerCase(character: Int): Int {
        if (character >= 'A'.code && character <= 'Z'.code) {
            return character + 32
        }
        return character
    }

    @JavaMapping("java/lang/Character", "isAlphabetic", "(I)Z")
    @KgenExport("kgen_char_isAlphabetic")
    @JvmStatic
    fun isAlphabetic(codePoint: Int): Boolean {
        return isLetter(codePoint)
    }

    @JavaMapping("java/lang/Character", "digit", "(CI)I")
    @KgenExport("kgen_char_digit")
    @JvmStatic
    fun digit(character: Int, radix: Int): Int {
        if (radix < 2 || radix > 36) {
            return -1
        }
        val value: Int
        if (character >= '0'.code && character <= '9'.code) {
            value = character - '0'.code
        } else if (character >= 'a'.code && character <= 'z'.code) {
            value = character - 'a'.code + 10
        } else if (character >= 'A'.code && character <= 'Z'.code) {
            value = character - 'A'.code + 10
        } else {
            return -1
        }
        if (value >= radix) {
            return -1
        }
        return value
    }
}
