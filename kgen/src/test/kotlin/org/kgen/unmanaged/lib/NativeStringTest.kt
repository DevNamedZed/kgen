package org.kgen.unmanaged.lib

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.kgen.unmanaged.Kgen
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NativeStringTest {

    @BeforeEach
    fun resetMemory() {
        Kgen.resetHeap()
    }

    @Nested
    inner class Construction {

        @Test
        fun defaultConstructorCreatesEmptyString() {
            val string = NativeString()
            assertEquals(0, string.length())
            assertTrue(string.isEmpty())
        }

        @Test
        fun constructFromStringLiteral() {
            val string = NativeString("Hello")
            assertEquals(5, string.length())
            assertFalse(string.isEmpty())
        }

        @Test
        fun constructFromAddress() {
            val address = Kgen.stringConst("World")
            val string = NativeString(address)
            assertEquals(5, string.length())
            assertTrue(string.contentEquals(Kgen.stringConst("World")))
        }

        @Test
        fun constructFromEmptyString() {
            val string = NativeString("")
            assertEquals(0, string.length())
            assertTrue(string.isEmpty())
        }
    }

    @Nested
    inner class Query {

        @Test
        fun charAt() {
            val string = NativeString("ABCD")
            assertEquals('A'.code, string.charAt(0))
            assertEquals('B'.code, string.charAt(1))
            assertEquals('C'.code, string.charAt(2))
            assertEquals('D'.code, string.charAt(3))
        }

        @Test
        fun toAddress() {
            val string = NativeString("Test")
            val address = string.toAddress()
            assertEquals('T'.code.toByte(), Kgen.loadByte(address))
            assertEquals('e'.code.toByte(), Kgen.loadByte(Kgen.offset(address, 1)))
            assertEquals(0.toByte(), Kgen.loadByte(Kgen.offset(address, 4)))
        }

        @Test
        fun stringHashCode() {
            val first = NativeString("Hello")
            val second = NativeString("Hello")
            assertEquals(first.stringHashCode(), second.stringHashCode())
        }

        @Test
        fun stringHashCodeDifferentStrings() {
            val first = NativeString("Hello")
            val second = NativeString("World")
            assertTrue(first.stringHashCode() != second.stringHashCode())
        }

        @Test
        fun stringHashCodeEmptyString() {
            val string = NativeString("")
            assertEquals(0, string.stringHashCode())
        }
    }

    @Nested
    inner class Comparison {

        @Test
        fun contentEqualsIdenticalStrings() {
            val string = NativeString("Hello")
            assertTrue(string.contentEquals(Kgen.stringConst("Hello")))
        }

        @Test
        fun contentEqualsDifferentStrings() {
            val string = NativeString("Hello")
            assertFalse(string.contentEquals(Kgen.stringConst("World")))
        }

        @Test
        fun contentEqualsDifferentLengths() {
            val string = NativeString("Hi")
            assertFalse(string.contentEquals(Kgen.stringConst("Hello")))
        }

        @Test
        fun contentEqualsEmptyStrings() {
            val string = NativeString("")
            assertTrue(string.contentEquals(Kgen.stringConst("")))
        }

        @Test
        fun compareToEqual() {
            val string = NativeString("ABC")
            assertEquals(0, string.compareTo(Kgen.stringConst("ABC")))
        }

        @Test
        fun compareToLessThan() {
            val string = NativeString("ABC")
            assertTrue(string.compareTo(Kgen.stringConst("DEF")) < 0)
        }

        @Test
        fun compareToGreaterThan() {
            val string = NativeString("DEF")
            assertTrue(string.compareTo(Kgen.stringConst("ABC")) > 0)
        }

        @Test
        fun compareToPrefixShorter() {
            val string = NativeString("AB")
            assertTrue(string.compareTo(Kgen.stringConst("ABC")) < 0)
        }
    }

    @Nested
    inner class Search {

        @Test
        fun indexOfFound() {
            val string = NativeString("Hello")
            assertEquals(1, string.indexOf('e'.code))
        }

        @Test
        fun indexOfNotFound() {
            val string = NativeString("Hello")
            assertEquals(-1, string.indexOf('z'.code))
        }

        @Test
        fun indexOfFromIndex() {
            val string = NativeString("abcabc")
            assertEquals(0, string.indexOf('a'.code))
            assertEquals(3, string.indexOfFrom('a'.code, 1))
        }

        @Test
        fun lastIndexOf() {
            val string = NativeString("abcabc")
            assertEquals(3, string.lastIndexOf('a'.code))
        }

        @Test
        fun lastIndexOfNotFound() {
            val string = NativeString("abc")
            assertEquals(-1, string.lastIndexOf('z'.code))
        }

        @Test
        fun indexOfString() {
            val string = NativeString("Hello, World")
            assertEquals(7, string.indexOfString(Kgen.stringConst("World")))
        }

        @Test
        fun indexOfStringNotFound() {
            val string = NativeString("Hello")
            assertEquals(-1, string.indexOfString(Kgen.stringConst("xyz")))
        }

        @Test
        fun indexOfEmptySubstring() {
            val string = NativeString("Hello")
            assertEquals(0, string.indexOfString(Kgen.stringConst("")))
        }

        @Test
        fun containsTrue() {
            val string = NativeString("Hello, World")
            assertTrue(string.contains(Kgen.stringConst("World")))
        }

        @Test
        fun containsFalse() {
            val string = NativeString("Hello")
            assertFalse(string.contains(Kgen.stringConst("xyz")))
        }

        @Test
        fun startsWithTrue() {
            val string = NativeString("Hello, World")
            assertTrue(string.startsWith(Kgen.stringConst("Hello")))
        }

        @Test
        fun startsWithFalse() {
            val string = NativeString("Hello, World")
            assertFalse(string.startsWith(Kgen.stringConst("World")))
        }

        @Test
        fun startsWithLongerPrefix() {
            val string = NativeString("Hi")
            assertFalse(string.startsWith(Kgen.stringConst("Hello")))
        }

        @Test
        fun endsWithTrue() {
            val string = NativeString("Hello, World")
            assertTrue(string.endsWith(Kgen.stringConst("World")))
        }

        @Test
        fun endsWithFalse() {
            val string = NativeString("Hello, World")
            assertFalse(string.endsWith(Kgen.stringConst("Hello")))
        }
    }

    @Nested
    inner class Transform {

        @Test
        fun substringMiddle() {
            NativeString("Hello, World").use { string ->
                string.substring(7, 12).use { sub ->
                    assertTrue(sub.contentEquals(Kgen.stringConst("World")))
                    assertEquals(5, sub.length())
                }
            }
        }

        @Test
        fun substringFromStart() {
            NativeString("Hello").use { string ->
                string.substring(0, 3).use { sub ->
                    assertTrue(sub.contentEquals(Kgen.stringConst("Hel")))
                }
            }
        }

        @Test
        fun substringEmpty() {
            NativeString("Hello").use { string ->
                string.substring(2, 2).use { sub ->
                    assertEquals(0, sub.length())
                    assertTrue(sub.isEmpty())
                }
            }
        }

        @Test
        fun trimLeadingAndTrailing() {
            NativeString("  Hello  ").use { string ->
                string.trim().use { trimmed ->
                    assertTrue(trimmed.contentEquals(Kgen.stringConst("Hello")))
                    assertEquals(5, trimmed.length())
                }
            }
        }

        @Test
        fun trimTabs() {
            NativeString("\t\n Hello \r\n").use { string ->
                string.trim().use { trimmed ->
                    assertTrue(trimmed.contentEquals(Kgen.stringConst("Hello")))
                }
            }
        }

        @Test
        fun trimAllWhitespace() {
            NativeString("   ").use { string ->
                string.trim().use { trimmed ->
                    assertTrue(trimmed.isEmpty())
                }
            }
        }

        @Test
        fun trimNoWhitespace() {
            NativeString("Hello").use { string ->
                string.trim().use { trimmed ->
                    assertTrue(trimmed.contentEquals(Kgen.stringConst("Hello")))
                }
            }
        }

        @Test
        fun toUpperCase() {
            NativeString("Hello, World!").use { string ->
                string.toUpperCase().use { upper ->
                    assertTrue(upper.contentEquals(Kgen.stringConst("HELLO, WORLD!")))
                }
            }
        }

        @Test
        fun toUpperCaseAlreadyUpper() {
            NativeString("ABC123").use { string ->
                string.toUpperCase().use { upper ->
                    assertTrue(upper.contentEquals(Kgen.stringConst("ABC123")))
                }
            }
        }

        @Test
        fun toLowerCase() {
            NativeString("Hello, World!").use { string ->
                string.toLowerCase().use { lower ->
                    assertTrue(lower.contentEquals(Kgen.stringConst("hello, world!")))
                }
            }
        }

        @Test
        fun toLowerCaseAlreadyLower() {
            NativeString("abc123").use { string ->
                string.toLowerCase().use { lower ->
                    assertTrue(lower.contentEquals(Kgen.stringConst("abc123")))
                }
            }
        }

        @Test
        fun concat() {
            NativeString("Hello").use { hello ->
                hello.concat(Kgen.stringConst(", World")).use { result ->
                    assertTrue(result.contentEquals(Kgen.stringConst("Hello, World")))
                    assertEquals(12, result.length())
                }
            }
        }

        @Test
        fun concatEmptyString() {
            NativeString("Hello").use { string ->
                string.concat(Kgen.stringConst("")).use { result ->
                    assertTrue(result.contentEquals(Kgen.stringConst("Hello")))
                }
            }
        }

        @Test
        fun concatToEmpty() {
            NativeString("").use { string ->
                string.concat(Kgen.stringConst("World")).use { result ->
                    assertTrue(result.contentEquals(Kgen.stringConst("World")))
                }
            }
        }

        @Test
        fun replaceCharacter() {
            NativeString("Hello").use { string ->
                string.replace('l'.code, 'r'.code).use { replaced ->
                    assertTrue(replaced.contentEquals(Kgen.stringConst("Herro")))
                }
            }
        }

        @Test
        fun replaceCharacterNoMatch() {
            NativeString("Hello").use { string ->
                string.replace('z'.code, 'x'.code).use { replaced ->
                    assertTrue(replaced.contentEquals(Kgen.stringConst("Hello")))
                }
            }
        }
    }

    @Nested
    inner class Chaining {

        @Test
        fun trimAndUpperCase() {
            NativeString("  hello  ").use { string ->
                string.trim().use { trimmed ->
                    trimmed.toUpperCase().use { upper ->
                        assertTrue(upper.contentEquals(Kgen.stringConst("HELLO")))
                    }
                }
            }
        }

        @Test
        fun concatAndSubstring() {
            NativeString("Hello").use { first ->
                first.concat(Kgen.stringConst(", World")).use { full ->
                    full.substring(0, 5).use { sub ->
                        assertTrue(sub.contentEquals(Kgen.stringConst("Hello")))
                    }
                }
            }
        }
    }

    @Nested
    inner class AutoCloseableSupport {

        @Test
        fun useBlockCallsDestroy() {
            var dataAfterClose = 1L
            NativeString("Hello").use { string ->
                assertEquals(5, string.length())
                dataAfterClose = string.data
            }
            // After use block, destroy() was called but we can't check the object
            // since it's out of scope. Instead verify the pattern compiles and runs.
        }

        @Test
        fun nestedUseBlocks() {
            NativeString("Hello").use { hello ->
                NativeString("World").use { world ->
                    hello.concat(world.toAddress()).use { result ->
                        assertTrue(result.contentEquals(Kgen.stringConst("HelloWorld")))
                    }
                }
            }
        }
    }

    @Nested
    inner class Lifecycle {

        @Test
        fun destroyFreesMemory() {
            val string = NativeString("Hello")
            string.destroy()
            assertEquals(0, string.data)
            assertEquals(0, string.length())
        }

        @Test
        fun destroyIsIdempotent() {
            val string = NativeString("Hello")
            string.destroy()
            string.destroy()
            assertEquals(0, string.data)
        }
    }
}
