package org.kgen.runtime.compile

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class KgenDebugSectionTest {

    @Test
    fun roundTripEmptyDebugSection() {
        val debug = KgenDebugSection(
            sourceFiles = emptyList(),
            methods = emptyList(),
        )
        val bytes = KgenDebugSection.write(debug)
        val parsed = KgenDebugSection.read(bytes)

        assertTrue(parsed.sourceFiles.isEmpty())
        assertTrue(parsed.methods.isEmpty())
    }

    @Test
    fun roundTripSingleMethod() {
        val debug = KgenDebugSection(
            sourceFiles = listOf("Main.java"),
            methods = listOf(
                KgenDebugMethod(
                    name = "main",
                    linkageName = "Main_main",
                    sourceFileIndex = 0,
                    startLine = 5,
                    endLine = 10,
                    nativeOffset = 0,
                    nativeSize = 64,
                    lineMappings = listOf(
                        KgenLineMapping(0, 5, 0),
                        KgenLineMapping(8, 7, 0),
                        KgenLineMapping(16, 10, 0),
                    ),
                ),
            ),
        )
        val bytes = KgenDebugSection.write(debug)
        val parsed = KgenDebugSection.read(bytes)

        assertEquals(1, parsed.sourceFiles.size)
        assertEquals("Main.java", parsed.sourceFiles[0])
        assertEquals(1, parsed.methods.size)

        val method = parsed.methods[0]
        assertEquals("main", method.name)
        assertEquals("Main_main", method.linkageName)
        assertEquals(0, method.sourceFileIndex)
        assertEquals(5, method.startLine)
        assertEquals(10, method.endLine)
        assertEquals(0, method.nativeOffset)
        assertEquals(64, method.nativeSize)
        assertEquals(3, method.lineMappings.size)
        assertEquals(7, method.lineMappings[1].sourceLine)
    }

    @Test
    fun roundTripMultipleMethods() {
        val debug = KgenDebugSection(
            sourceFiles = listOf("Foo.java", "Bar.java"),
            methods = listOf(
                KgenDebugMethod(
                    name = "add",
                    linkageName = "Foo_add",
                    sourceFileIndex = 0,
                    startLine = 3,
                    endLine = 5,
                    nativeOffset = 0,
                    nativeSize = 32,
                    lineMappings = listOf(KgenLineMapping(0, 3, 0), KgenLineMapping(12, 5, 0)),
                ),
                KgenDebugMethod(
                    name = "sub",
                    linkageName = "Bar_sub",
                    sourceFileIndex = 1,
                    startLine = 10,
                    endLine = 15,
                    nativeOffset = 32,
                    nativeSize = 48,
                    lineMappings = listOf(KgenLineMapping(0, 10, 0), KgenLineMapping(20, 15, 0)),
                ),
            ),
        )
        val bytes = KgenDebugSection.write(debug)
        val parsed = KgenDebugSection.read(bytes)

        assertEquals(2, parsed.sourceFiles.size)
        assertEquals("Foo.java", parsed.sourceFiles[0])
        assertEquals("Bar.java", parsed.sourceFiles[1])
        assertEquals(2, parsed.methods.size)
        assertEquals("add", parsed.methods[0].name)
        assertEquals("sub", parsed.methods[1].name)
        assertEquals(1, parsed.methods[1].sourceFileIndex)
    }

    @Test
    fun roundTripWithColumns() {
        val debug = KgenDebugSection(
            sourceFiles = listOf("Test.java"),
            methods = listOf(
                KgenDebugMethod(
                    name = "calc",
                    linkageName = "Test_calc",
                    sourceFileIndex = 0,
                    startLine = 1,
                    endLine = 3,
                    nativeOffset = 0,
                    nativeSize = 24,
                    lineMappings = listOf(
                        KgenLineMapping(0, 1, 5),
                        KgenLineMapping(8, 2, 12),
                        KgenLineMapping(16, 3, 8),
                    ),
                ),
            ),
        )
        val bytes = KgenDebugSection.write(debug)
        val parsed = KgenDebugSection.read(bytes)

        val mappings = parsed.methods[0].lineMappings
        assertEquals(5, mappings[0].sourceColumn)
        assertEquals(12, mappings[1].sourceColumn)
        assertEquals(8, mappings[2].sourceColumn)
    }

    @Test
    fun sourceFileHelperMethod() {
        val debug = KgenDebugSection(
            sourceFiles = listOf("A.java", "B.java", "C.java"),
            methods = emptyList(),
        )
        assertEquals("A.java", debug.sourceFile(0))
        assertEquals("B.java", debug.sourceFile(1))
        assertEquals("C.java", debug.sourceFile(2))
    }

    @Test
    fun badMagicThrows() {
        assertThrows(IllegalArgumentException::class.java) {
            KgenDebugSection.read(byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0))
        }
    }

    @Test
    fun roundTripUnicodeSourcePaths() {
        val debug = KgenDebugSection(
            sourceFiles = listOf("src/日本語/Main.java", "ソース/Test.java"),
            methods = listOf(
                KgenDebugMethod(
                    name = "run",
                    linkageName = "Main_run",
                    sourceFileIndex = 0,
                    startLine = 1,
                    endLine = 1,
                    nativeOffset = 0,
                    nativeSize = 8,
                    lineMappings = emptyList(),
                ),
            ),
        )
        val bytes = KgenDebugSection.write(debug)
        val parsed = KgenDebugSection.read(bytes)

        assertEquals("src/日本語/Main.java", parsed.sourceFiles[0])
        assertEquals("ソース/Test.java", parsed.sourceFiles[1])
    }

    @Test
    fun roundTripEmptyLineMappings() {
        val debug = KgenDebugSection(
            sourceFiles = listOf("Test.java"),
            methods = listOf(
                KgenDebugMethod(
                    name = "noop",
                    linkageName = "Test_noop",
                    sourceFileIndex = 0,
                    startLine = 1,
                    endLine = 1,
                    nativeOffset = 0,
                    nativeSize = 4,
                    lineMappings = emptyList(),
                ),
            ),
        )
        val bytes = KgenDebugSection.write(debug)
        val parsed = KgenDebugSection.read(bytes)

        assertEquals(1, parsed.methods.size)
        assertTrue(parsed.methods[0].lineMappings.isEmpty())
    }

    @Test
    fun roundTripLargeLineNumbers() {
        val debug = KgenDebugSection(
            sourceFiles = listOf("Big.java"),
            methods = listOf(
                KgenDebugMethod(
                    name = "bigMethod",
                    linkageName = "Big_bigMethod",
                    sourceFileIndex = 0,
                    startLine = 50000,
                    endLine = 60000,
                    nativeOffset = 0x10000,
                    nativeSize = 0x8000,
                    lineMappings = listOf(
                        KgenLineMapping(0, 50000, 0),
                        KgenLineMapping(100, 55000, 0),
                        KgenLineMapping(200, 60000, 0),
                    ),
                ),
            ),
        )
        val bytes = KgenDebugSection.write(debug)
        val parsed = KgenDebugSection.read(bytes)

        val method = parsed.methods[0]
        assertEquals(50000, method.startLine)
        assertEquals(60000, method.endLine)
        assertEquals(0x10000, method.nativeOffset)
        assertEquals(0x8000, method.nativeSize)
        assertEquals(55000, method.lineMappings[1].sourceLine)
    }

    @Test
    fun headerSizeIsCorrect() {
        val debug = KgenDebugSection(sourceFiles = emptyList(), methods = emptyList())
        val bytes = KgenDebugSection.write(debug)

        // Magic(4) + version(2) + flags(2) + fileCount(4) + methodCount(4) = 16 bytes
        assertEquals(16, bytes.size)
        // Verify magic
        assertEquals('K'.code.toByte(), bytes[0])
        assertEquals('D'.code.toByte(), bytes[1])
        assertEquals('B'.code.toByte(), bytes[2])
        assertEquals('G'.code.toByte(), bytes[3])
    }
}
