package org.kgen.unmanaged.lib

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NativeStdlibTest {

    @Test
    fun resolvesStringLength() {
        val name = NativeStdlib.resolve("java/lang/String", "length", "()I")
        assertNotNull(name)
        assertEquals("kgen_string_length", name)
    }

    @Test
    fun resolvesStringEquals() {
        val name = NativeStdlib.resolve("java/lang/String", "equals", "(Ljava/lang/Object;)Z")
        assertNotNull(name)
        assertEquals("kgen_string_equals", name)
    }

    @Test
    fun resolvesPrintlnStr() {
        val name = NativeStdlib.resolve("java/io/PrintStream", "println", "(Ljava/lang/String;)V")
        assertNotNull(name)
        assertEquals("kgen_println_str", name)
    }

    @Test
    fun resolvesMathSqrt() {
        val name = NativeStdlib.resolve("java/lang/Math", "sqrt", "(D)D")
        assertNotNull(name)
        assertEquals("kgen_math_sqrt", name)
    }

    @Test
    fun resolvesMathSin() {
        val name = NativeStdlib.resolve("java/lang/Math", "sin", "(D)D")
        assertNotNull(name)
        assertEquals("kgen_math_sin", name)
    }

    @Test
    fun resolvesIntegerToString() {
        val name = NativeStdlib.resolve("java/lang/Integer", "toString", "(I)Ljava/lang/String;")
        assertNotNull(name)
        assertEquals("kgen_int_to_string", name)
    }

    @Test
    fun resolvesIntegerParseInt() {
        val name = NativeStdlib.resolve("java/lang/Integer", "parseInt", "(Ljava/lang/String;)I")
        assertNotNull(name)
        assertEquals("kgen_int_parse", name)
    }

    @Test
    fun returnsNullForUnhandledMethod() {
        val name = NativeStdlib.resolve("java/lang/Object", "wait", "()V")
        assertNull(name)
    }

    @Test
    fun isHandledReturnsTrueForKnownMethod() {
        assertTrue(NativeStdlib.isHandled("java/lang/String", "length", "()I"))
        assertTrue(NativeStdlib.isHandled("java/lang/Math", "abs", "(I)I"))
        assertTrue(NativeStdlib.isHandled("java/io/PrintStream", "println", "(I)V"))
    }

    @Test
    fun isHandledReturnsFalseForUnknownMethod() {
        assertFalse(NativeStdlib.isHandled("java/util/ArrayList", "add", "(Ljava/lang/Object;)Z"))
        assertFalse(NativeStdlib.isHandled("java/lang/Object", "toString", "()Ljava/lang/String;"))
    }

    @Test
    fun handledMethodsContainsAllMappings() {
        val methods = NativeStdlib.handledMethods()
        assertTrue(methods.contains("java/lang/String.length:()I"))
        assertTrue(methods.contains("java/lang/Math.sqrt:(D)D"))
        assertTrue(methods.contains("java/io/PrintStream.println:(Ljava/lang/String;)V"))
    }

    @Test
    fun allEntriesHaveNonEmptyNativeName() {
        for (entry in NativeStdlib.allEntries()) {
            assertTrue(entry.nativeName.isNotEmpty(), "Entry ${entry.owner}.${entry.name} has empty native name")
        }
    }

    @Test
    fun allEntriesHaveValidOwner() {
        for (entry in NativeStdlib.allEntries()) {
            assertTrue(entry.owner.contains("/"), "Entry ${entry.nativeName} has invalid owner: ${entry.owner}")
        }
    }

    @Test
    fun discoversIoFunctions() {
        val ioEntries = NativeStdlib.allEntries().filter { it.owner == "java/io/PrintStream" }
        assertTrue(ioEntries.size >= 10, "Expected at least 10 I/O entries, got ${ioEntries.size}")
    }

    @Test
    fun discoversStringFunctions() {
        val stringEntries = NativeStdlib.allEntries().filter { it.owner == "java/lang/String" }
        assertTrue(stringEntries.size >= 8, "Expected at least 8 String entries, got ${stringEntries.size}")
    }

    @Test
    fun discoversMathFunctions() {
        val mathEntries = NativeStdlib.allEntries().filter { it.owner == "java/lang/Math" }
        assertTrue(mathEntries.size >= 15, "Expected at least 15 Math entries, got ${mathEntries.size}")
    }

    @Test
    fun discoversNewTrigFunctions() {
        assertNotNull(NativeStdlib.resolve("java/lang/Math", "sin", "(D)D"))
        assertNotNull(NativeStdlib.resolve("java/lang/Math", "cos", "(D)D"))
        assertNotNull(NativeStdlib.resolve("java/lang/Math", "tan", "(D)D"))
        assertNotNull(NativeStdlib.resolve("java/lang/Math", "atan", "(D)D"))
        assertNotNull(NativeStdlib.resolve("java/lang/Math", "atan2", "(DD)D"))
    }

    @Test
    fun discoversNewStringFunctions() {
        assertNotNull(NativeStdlib.resolve("java/lang/String", "endsWith", "(Ljava/lang/String;)Z"))
        assertNotNull(NativeStdlib.resolve("java/lang/String", "compareTo", "(Ljava/lang/String;)I"))
        assertNotNull(NativeStdlib.resolve("java/lang/String", "trim", "()Ljava/lang/String;"))
        assertNotNull(NativeStdlib.resolve("java/lang/String", "toUpperCase", "()Ljava/lang/String;"))
        assertNotNull(NativeStdlib.resolve("java/lang/String", "toLowerCase", "()Ljava/lang/String;"))
        assertNotNull(NativeStdlib.resolve("java/lang/String", "hashCode", "()I"))
    }

    @Test
    fun discoversCharacterFunctions() {
        val charEntries = NativeStdlib.allEntries().filter { it.owner == "java/lang/Character" }
        assertTrue(charEntries.size >= 10, "Expected at least 10 Character entries, got ${charEntries.size}")
        assertNotNull(NativeStdlib.resolve("java/lang/Character", "isDigit", "(C)Z"))
        assertNotNull(NativeStdlib.resolve("java/lang/Character", "isLetter", "(C)Z"))
        assertNotNull(NativeStdlib.resolve("java/lang/Character", "toUpperCase", "(C)C"))
        assertNotNull(NativeStdlib.resolve("java/lang/Character", "toLowerCase", "(C)C"))
    }

    @Test
    fun discoversSystemFunctions() {
        val systemEntries = NativeStdlib.allEntries().filter { it.owner == "java/lang/System" }
        assertTrue(systemEntries.size >= 4, "Expected at least 4 System entries, got ${systemEntries.size}")
        assertNotNull(NativeStdlib.resolve("java/lang/System", "currentTimeMillis", "()J"))
        assertNotNull(NativeStdlib.resolve("java/lang/System", "nanoTime", "()J"))
        assertNotNull(NativeStdlib.resolve("java/lang/System", "exit", "(I)V"))
        assertNotNull(NativeStdlib.resolve("java/lang/System", "arraycopy", "(Ljava/lang/Object;ILjava/lang/Object;II)V"))
    }
}
