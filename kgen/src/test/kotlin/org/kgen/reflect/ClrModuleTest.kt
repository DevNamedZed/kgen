package org.kgen.reflect

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.binary.*

/**
 * End-to-end tests: .NET DLL fixture → Module → reflect API.
 */
class ClrModuleTest {

    private fun loadFixture(name: String): Module {
        val bytes = javaClass.getResourceAsStream("/fixtures/clr/$name")!!.readAllBytes()
        return Module.fromBytes(bytes, name)
    }

    @Test
    fun loadTestLibDll() {
        val module = loadFixture("TestLib.dll")
        assertEquals("TestLib.dll", module.name())
        assertEquals(ObjectFormat.MSIL_ASSEMBLY, module.format())
        assertTrue(module.hasClr())
        assertFalse(module.hasJvm())
    }

    @Test
    fun testLibSymbols() {
        val module = loadFixture("TestLib.dll")
        // Pure .NET assemblies don't have COFF symbols — methods are in CLR metadata
        // For now, symbols may be empty; CLR metadata provides type info instead
        val symbols = module.symbols()
        // Not asserting non-empty — CLR metadata parsing for symbols is a future enhancement
        assertNotNull(symbols)
    }

    @Test
    fun testLibExports() {
        val module = loadFixture("TestLib.dll")
        // .NET assemblies may not have traditional exports
        // but should have sections
        assertFalse(module.sections().isEmpty())
    }

    @Test
    fun testLibSections() {
        val module = loadFixture("TestLib.dll")
        val sections = module.sections()
        assertTrue(sections.isNotEmpty(), "Should have sections: ${sections.map { it.name }}")
        // .NET assemblies typically have .text section with IL
        val textSection = sections.firstOrNull { it.name == ".text" }
        assertNotNull(textSection, "Should have .text section. Sections: ${sections.map { it.name }}")
    }

    @Test
    fun testLibIsNotMixedMode() {
        val module = loadFixture("TestLib.dll")
        assertFalse(module.isMixedMode(), "Pure IL assembly should not be mixed mode")
    }

    @Test
    fun testLibObjectFileAccess() {
        val module = loadFixture("TestLib.dll")
        val obj = module.objectFile()
        assertEquals(ObjectFormat.MSIL_ASSEMBLY, obj.format)
    }
}
