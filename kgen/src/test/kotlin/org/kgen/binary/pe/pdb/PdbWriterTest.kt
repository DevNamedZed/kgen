package org.kgen.binary.pe.pdb

import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PdbWriterTest {

    @Test
    fun `write and read empty PDB`() {
        val writer = PdbWriter()
        val guid = UUID.randomUUID()
        writer.guid = guid
        writer.age = 1
        val bytes = writer.build()

        assertTrue(MsfReader.isMsf(bytes))
        val pdb = PdbReader.read(bytes)
        assertEquals(guid, pdb.guid)
        assertEquals(1, pdb.age)
    }

    @Test
    fun `write PDB with types`() {
        val writer = PdbWriter()
        writer.guid = UUID.randomUUID()
        writer.age = 1

        // Add argument list
        val argListIndex = writer.addType(CodeViewBuilder.argList(listOf(
            CvBuiltinType.T_INT4.code,
            CvBuiltinType.T_INT4.code,
        )))

        // Add procedure type
        val procTypeIndex = writer.addType(CodeViewBuilder.procedure(
            returnType = CvBuiltinType.T_INT4.code,
            paramCount = 2,
            argListIndex = argListIndex,
        ))

        val bytes = writer.build()
        val pdb = PdbReader.read(bytes)

        assertEquals(2, pdb.types.size)
        assertEquals(CvTypeKind.LF_ARGLIST, pdb.types[0].typeKind)
        assertEquals(CvTypeKind.LF_PROCEDURE, pdb.types[1].typeKind)
    }

    @Test
    fun `write PDB with structure type`() {
        val writer = PdbWriter()
        writer.guid = UUID.randomUUID()
        writer.age = 1

        // Forward declaration
        val fwdIndex = writer.addType(CodeViewBuilder.structure(
            name = "Point",
            properties = CvTypeProperties.FORWARD_REF,
        ))

        // Full definition
        val defIndex = writer.addType(CodeViewBuilder.structure(
            name = "Point",
            size = 8,
            memberCount = 2,
        ))

        val bytes = writer.build()
        val pdb = PdbReader.read(bytes)

        assertEquals(2, pdb.types.size)
        assertEquals(CvTypeKind.LF_STRUCTURE, pdb.types[0].typeKind)
        assertEquals(CvTypeKind.LF_STRUCTURE, pdb.types[1].typeKind)
    }

    @Test
    fun `write PDB with global symbols`() {
        val writer = PdbWriter()
        writer.guid = UUID.randomUUID()
        writer.age = 1

        writer.addGlobalSymbol(CodeViewBuilder.gdata32(
            name = "globalVar",
            typeIndex = CvBuiltinType.T_INT4.code,
            offset = 0,
            section = 3,
        ))

        writer.addGlobalSymbol(CodeViewBuilder.pub32(
            name = "main",
            offset = 0,
            section = 1,
            flags = CvPublicSymbolFlags.FUNCTION,
        ))

        val bytes = writer.build()
        val pdb = PdbReader.read(bytes)

        assertEquals(2, pdb.globalSymbols.size)
        assertEquals(CvSymbolKind.S_GDATA32, pdb.globalSymbols[0].symbolKind)
        assertEquals(CvSymbolKind.S_PUB32, pdb.globalSymbols[1].symbolKind)
    }

    @Test
    fun `write PDB with module`() {
        val writer = PdbWriter()
        writer.guid = UUID.randomUUID()
        writer.age = 1
        writer.machine = 0x8664.toShort()

        val mod = writer.addModule("main.obj", "C:\\src\\main.c")
        mod.addSymbol(CodeViewBuilder.compile3(
            language = CvSourceLanguage.C,
            cpu = CvCpuType.X64_AMD64,
            compilerName = "kgen 1.0",
        ))
        mod.addSymbol(CodeViewBuilder.gproc32(
            name = "main",
            typeIndex = 0x1000,
            offset = 0,
            section = 1,
            codeSize = 42,
        ))
        mod.addSymbol(CodeViewBuilder.end())

        val bytes = writer.build()
        val pdb = PdbReader.read(bytes)

        assertTrue(pdb.modules.isNotEmpty())
        assertEquals("main.obj", pdb.modules[0].moduleName)
    }

    @Test
    fun `write PDB with line info`() {
        val writer = PdbWriter()
        writer.guid = UUID.randomUUID()
        writer.age = 1

        val mod = writer.addModule("test.obj")
        mod.addLineInfo(CvLineBlock(
            fileIndex = 0,
            lines = listOf(
                CvLineEntry(0, 10, 0, true),
                CvLineEntry(5, 11, 0, true),
                CvLineEntry(12, 12, 0, true),
            ),
        ))

        val bytes = writer.build()
        assertTrue(MsfReader.isMsf(bytes))
        val pdb = PdbReader.read(bytes)
        assertTrue(pdb.modules.isNotEmpty())
    }

    @Test
    fun `PDB GUID and age match`() {
        val guid = UUID.randomUUID()
        val writer = PdbWriter()
        writer.guid = guid
        writer.age = 3

        val bytes = writer.build()
        val pdb = PdbReader.read(bytes)

        assertTrue(pdb.matchesGuid(guid, 3))
    }

    @Test
    fun `write PDB with enum type`() {
        val writer = PdbWriter()
        writer.guid = UUID.randomUUID()
        writer.age = 1

        writer.addType(CodeViewBuilder.enum_(
            name = "Color",
            underlyingType = CvBuiltinType.T_INT4.code,
            memberCount = 3,
        ))

        val bytes = writer.build()
        val pdb = PdbReader.read(bytes)
        assertEquals(1, pdb.types.size)
        assertEquals(CvTypeKind.LF_ENUM, pdb.types[0].typeKind)
    }

    @Test
    fun `write PDB with union type`() {
        val writer = PdbWriter()
        writer.guid = UUID.randomUUID()
        writer.age = 1

        writer.addType(CodeViewBuilder.union(
            name = "Value",
            size = 8,
            memberCount = 2,
        ))

        val bytes = writer.build()
        val pdb = PdbReader.read(bytes)
        assertEquals(1, pdb.types.size)
        assertEquals(CvTypeKind.LF_UNION, pdb.types[0].typeKind)
    }

    @Test
    fun `write PDB with pointer type`() {
        val writer = PdbWriter()
        writer.guid = UUID.randomUUID()
        writer.age = 1

        writer.addType(CodeViewBuilder.pointer(CvBuiltinType.T_INT4.code))

        val bytes = writer.build()
        val pdb = PdbReader.read(bytes)
        assertEquals(1, pdb.types.size)
        assertEquals(CvTypeKind.LF_POINTER, pdb.types[0].typeKind)
    }

    @Test
    fun `write PDB with modifier type`() {
        val writer = PdbWriter()
        writer.guid = UUID.randomUUID()
        writer.age = 1

        writer.addType(CodeViewBuilder.modifier(CvBuiltinType.T_INT4.code, isConst = true))

        val bytes = writer.build()
        val pdb = PdbReader.read(bytes)
        assertEquals(1, pdb.types.size)
        assertEquals(CvTypeKind.LF_MODIFIER, pdb.types[0].typeKind)
    }

    @Test
    fun `write PDB with array type`() {
        val writer = PdbWriter()
        writer.guid = UUID.randomUUID()
        writer.age = 1

        writer.addType(CodeViewBuilder.array(
            elementType = CvBuiltinType.T_INT4.code,
            indexType = CvBuiltinType.T_UINT4.code,
            size = 40,
        ))

        val bytes = writer.build()
        val pdb = PdbReader.read(bytes)
        assertEquals(1, pdb.types.size)
        assertEquals(CvTypeKind.LF_ARRAY, pdb.types[0].typeKind)
    }

    @Test
    fun `write PDB with bitfield type`() {
        val writer = PdbWriter()
        writer.guid = UUID.randomUUID()
        writer.age = 1

        writer.addType(CodeViewBuilder.bitfield(CvBuiltinType.T_INT4.code, 4, 0))

        val bytes = writer.build()
        val pdb = PdbReader.read(bytes)
        assertEquals(1, pdb.types.size)
        assertEquals(CvTypeKind.LF_BITFIELD, pdb.types[0].typeKind)
    }
}
