package org.kgen.reflect

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.binary.*

class ClrModuleExtendedTest {

    private fun loadFixture(name: String): Module {
        val bytes = javaClass.getResourceAsStream("/fixtures/clr/$name")!!.readAllBytes()
        return Module.fromBytes(bytes, name)
    }

    @Test
    fun moduleNameMatchesFilename() {
        val module = loadFixture("TestLib.dll")
        assertEquals("TestLib.dll", module.name())
    }

    @Test
    fun modulePathIsNullForBytesBased() {
        val module = loadFixture("TestLib.dll")
        assertNull(module.path())
    }

    @Test
    fun moduleIsNotLoaded() {
        val module = loadFixture("TestLib.dll")
        assertFalse(module.isLoaded())
    }

    @Test
    fun moduleBaseAddressIsZero() {
        val module = loadFixture("TestLib.dll")
        assertEquals(0L, module.baseAddress())
    }

    @Test
    fun formatIsMsilAssembly() {
        val module = loadFixture("TestLib.dll")
        assertEquals(ObjectFormat.MSIL_ASSEMBLY, module.format())
    }

    @Test
    fun hasClrIsTrue() {
        val module = loadFixture("TestLib.dll")
        assertTrue(module.hasClr())
    }

    @Test
    fun hasJvmIsFalse() {
        val module = loadFixture("TestLib.dll")
        assertFalse(module.hasJvm())
    }

    @Test
    fun hasWasmIsFalse() {
        val module = loadFixture("TestLib.dll")
        assertFalse(module.hasWasm())
    }

    @Test
    fun hasNativeCodeIsFalseForPureIl() {
        val module = loadFixture("TestLib.dll")
        assertFalse(module.hasNativeCode())
    }

    @Test
    fun isNotMixedMode() {
        val module = loadFixture("TestLib.dll")
        assertFalse(module.isMixedMode())
    }

    @Test
    fun sectionsContainText() {
        val module = loadFixture("TestLib.dll")
        assertNotNull(module.section(".text"), "Should have .text section")
    }

    @Test
    fun textSectionShortcut() {
        val module = loadFixture("TestLib.dll")
        val text = module.textSection()
        assertNotNull(text)
        assertEquals(".text", text!!.name)
    }

    @Test
    fun sectionByKindReturnsText() {
        val module = loadFixture("TestLib.dll")
        val text = module.section(SectionKind.TEXT)
        assertNotNull(text)
    }

    @Test
    fun sectionByNameMissing() {
        val module = loadFixture("TestLib.dll")
        assertNull(module.section(".nonexistent"))
    }

    @Test
    fun symbolsListIsNotNull() {
        val module = loadFixture("TestLib.dll")
        assertNotNull(module.symbols())
    }

    @Test
    fun objectFileFormatMatches() {
        val module = loadFixture("TestLib.dll")
        assertEquals(ObjectFormat.MSIL_ASSEMBLY, module.objectFile().format)
    }

    @Test
    fun peAccessorNotNull() {
        val module = loadFixture("TestLib.dll")
        assertNotNull(module.pe())
    }

    @Test
    fun clrAccessorNotNull() {
        val module = loadFixture("TestLib.dll")
        assertNotNull(module.clr())
    }

    @Test
    fun elfAccessorIsNull() {
        val module = loadFixture("TestLib.dll")
        assertNull(module.elf())
    }

    @Test
    fun machOAccessorIsNull() {
        val module = loadFixture("TestLib.dll")
        assertNull(module.machO())
    }

    @Test
    fun classFileAccessorIsNull() {
        val module = loadFixture("TestLib.dll")
        assertNull(module.classFile())
    }

    @Test
    fun wasmAccessorIsNull() {
        val module = loadFixture("TestLib.dll")
        assertNull(module.wasm())
    }

    @Test
    fun bytesReturnsNonEmptyArray() {
        val module = loadFixture("TestLib.dll")
        assertTrue(module.bytes().isNotEmpty())
    }

    @Test
    fun bytesReturnsCopy() {
        val module = loadFixture("TestLib.dll")
        val a = module.bytes()
        val b = module.bytes()
        assertNotSame(a, b)
        assertArrayEquals(a, b)
    }

    @Test
    fun toStringContainsModuleName() {
        val module = loadFixture("TestLib.dll")
        val str = module.toString()
        assertTrue(str.contains("TestLib.dll"), "toString should contain module name: $str")
    }

    @Test
    fun toStringContainsFormat() {
        val module = loadFixture("TestLib.dll")
        val str = module.toString()
        assertTrue(str.contains("MSIL_ASSEMBLY"), "toString should contain format: $str")
    }

    @Test
    fun typesNotEmpty() {
        val module = loadFixture("TestLib.dll")
        assertTrue(module.types().isNotEmpty(), "Should discover types from CLR metadata")
    }

    @Test
    fun typeLookupByFullName() {
        val module = loadFixture("TestLib.dll")
        val types = module.types()
        if (types.isNotEmpty()) {
            val first = types[0]
            val found = module.type(first.fullName())
            assertNotNull(found)
            assertEquals(first.fullName(), found!!.fullName())
        }
    }

    @Test
    fun typeLookupReturnsNullForMissing() {
        val module = loadFixture("TestLib.dll")
        assertNull(module.type("Does.Not.Exist.Anywhere"))
    }

    @Test
    fun moduleImportsListNotNull() {
        val module = loadFixture("TestLib.dll")
        assertNotNull(module.imports())
    }

    @Test
    fun moduleExportsListNotNull() {
        val module = loadFixture("TestLib.dll")
        assertNotNull(module.exports())
    }
}
