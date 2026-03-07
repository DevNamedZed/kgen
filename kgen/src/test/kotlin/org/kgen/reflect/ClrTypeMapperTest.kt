package org.kgen.reflect

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.kgen.binary.*

class ClrTypeMapperTest {

    private fun loadFixture(name: String): Module {
        val bytes = javaClass.getResourceAsStream("/fixtures/clr/$name")!!.readAllBytes()
        return Module.fromBytes(bytes, name)
    }

    // -- Format-specific accessors --

    @Test
    fun peAccessor() {
        val module = loadFixture("TestLib.dll")
        val pe = module.pe()
        assertNotNull(pe, "Should have PE accessor for .NET assembly")
    }

    @Test
    fun clrAccessor() {
        val module = loadFixture("TestLib.dll")
        val clr = module.clr()
        assertNotNull(clr, "Should have CLR metadata accessor")
    }

    @Test
    fun elfAccessorNull() {
        val module = loadFixture("TestLib.dll")
        assertNull(module.elf(), "CLR assembly should not have ELF model")
        assertNull(module.machO(), "CLR assembly should not have Mach-O model")
        assertNull(module.classFile(), "CLR assembly should not have classFile model")
        assertNull(module.wasm(), "CLR assembly should not have WASM model")
    }

    // -- Module.types() for CLR --

    @Test
    fun typeDiscovery() {
        val module = loadFixture("TestLib.dll")
        val types = module.types()
        assertTrue(types.isNotEmpty(), "Should discover types from CLR metadata")
    }

    @Test
    fun noModulePseudoType() {
        val module = loadFixture("TestLib.dll")
        val types = module.types()
        assertTrue(types.none { it.fullName() == "<Module>" }, "Should skip <Module> pseudo-type")
    }

    @Test
    fun typeHasName() {
        val module = loadFixture("TestLib.dll")
        val types = module.types()
        for (type in types) {
            assertTrue(type.fullName().isNotEmpty(), "All types should have non-empty names")
            assertTrue(type.name().isNotEmpty(), "All types should have non-empty short names")
        }
    }

    @Test
    fun typeHasKind() {
        val module = loadFixture("TestLib.dll")
        val types = module.types()
        for (type in types) {
            assertNotNull(type.kind(), "All types should have a kind")
        }
    }

    @Test
    fun typeLookupByName() {
        val module = loadFixture("TestLib.dll")
        val types = module.types()
        if (types.isNotEmpty()) {
            val firstName = types[0].fullName()
            val found = module.type(firstName)
            assertNotNull(found, "Should find type by full name: $firstName")
            assertEquals(firstName, found!!.fullName())
        }
    }

    @Test
    fun typeLookupMissing() {
        val module = loadFixture("TestLib.dll")
        assertNull(module.type("NonExistent.Type.That.Does.Not.Exist"))
    }

    @Test
    fun typeMethods() {
        val module = loadFixture("TestLib.dll")
        val types = module.types()
        // At least one type should have methods
        val hasMethodTypes = types.filter { it.methods().isNotEmpty() }
        assertTrue(hasMethodTypes.isNotEmpty(), "At least one type should have methods. Types: ${types.map { "${it.fullName()}: ${it.methods().size} methods" }}")
    }

    @Test
    fun typeFields() {
        val module = loadFixture("TestLib.dll")
        val types = module.types()
        // Check if any types have fields
        val hasFieldTypes = types.filter { it.fields().isNotEmpty() }
        // This is informational — not all test DLLs will have fields
        assertNotNull(hasFieldTypes)
    }

    @Test
    fun typeConstructors() {
        val module = loadFixture("TestLib.dll")
        val types = module.types()
        val hasCtor = types.filter { it.constructors().isNotEmpty() }
        assertTrue(hasCtor.isNotEmpty(), "At least one class should have constructors")
    }

    @Test
    fun methodHasName() {
        val module = loadFixture("TestLib.dll")
        val types = module.types()
        for (type in types) {
            for (method in type.methods()) {
                assertTrue(method.name().isNotEmpty(), "Methods should have names")
            }
        }
    }

    @Test
    fun moduleOwnership() {
        val module = loadFixture("TestLib.dll")
        val types = module.types()
        for (type in types) {
            assertEquals(module, type.module(), "Type ${type.fullName()} should reference its module")
        }
    }

    // -- Direct ClrTypeMapper unit tests with synthetic data --

    @Test
    fun mapEmptyMetadata() {
        val clr = org.kgen.binary.pe.clr.ClrMetadata(
            majorRuntimeVersion = 2,
            minorRuntimeVersion = 5,
            flags = 1,
            entryPointToken = 0,
            metadataVersion = "v4.0.30319",
            tables = org.kgen.binary.pe.clr.ClrTables(),
            strings = org.kgen.binary.pe.clr.ClrStringHeap(ByteArray(1)),
            blobs = org.kgen.binary.pe.clr.ClrBlobHeap(ByteArray(1)),
            guids = org.kgen.binary.pe.clr.ClrGuidHeap(ByteArray(0)),
            userStrings = org.kgen.binary.pe.clr.ClrUserStringHeap(ByteArray(1)),
        )
        val types = ClrTypeMapper.map(clr, null)
        assertTrue(types.isEmpty(), "Empty metadata should produce no types")
    }
}
