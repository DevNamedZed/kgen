package org.kgen.binary.pe.pdb

import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * End-to-end tests that create a full PDB with types, symbols, modules, and line info,
 * then verify the reader can parse it all back.
 */
class PdbEndToEndTest {

    @Test
    fun `full PDB with C program debug info`() {
        val writer = PdbWriter()
        val guid = UUID.randomUUID()
        writer.guid = guid
        writer.age = 1
        writer.machine = 0x8664.toShort() // AMD64

        // Build type info for: int main(int argc, char** argv)
        val charType = CvBuiltinType.T_RCHAR.code
        val intType = CvBuiltinType.T_INT4.code
        val charPtrIdx = writer.addType(CodeViewBuilder.pointer(charType))        // char*
        val charPtrPtrIdx = writer.addType(CodeViewBuilder.pointer(charPtrIdx))   // char**
        val argListIdx = writer.addType(CodeViewBuilder.argList(listOf(intType, charPtrPtrIdx)))
        val mainTypeIdx = writer.addType(CodeViewBuilder.procedure(
            returnType = intType,
            paramCount = 2,
            argListIndex = argListIdx,
        ))

        // Build type info for: struct Point { int x; int y; }
        val pointFwdIdx = writer.addType(CodeViewBuilder.structure(
            name = "Point",
            properties = CvTypeProperties.FORWARD_REF or CvTypeProperties.HAS_UNIQUE_NAME,
        ))
        val pointIdx = writer.addType(CodeViewBuilder.structure(
            name = "Point",
            size = 8,
            memberCount = 2,
            properties = CvTypeProperties.HAS_UNIQUE_NAME,
        ))

        // Global symbols
        writer.addGlobalSymbol(CodeViewBuilder.pub32(
            name = "main",
            offset = 0,
            section = 1,
            flags = CvPublicSymbolFlags.FUNCTION,
        ))
        writer.addGlobalSymbol(CodeViewBuilder.gdata32(
            name = "globalCounter",
            typeIndex = intType,
            offset = 0,
            section = 3,
        ))

        // Module: main.obj
        val mainMod = writer.addModule("main.obj", "C:\\project\\main.c")
        mainMod.addSymbol(CodeViewBuilder.objname("main.obj"))
        mainMod.addSymbol(CodeViewBuilder.compile3(
            language = CvSourceLanguage.C,
            cpu = CvCpuType.X64_AMD64,
            compilerName = "kgen 1.0.0",
        ))
        mainMod.addSymbol(CodeViewBuilder.gproc32(
            name = "main",
            typeIndex = mainTypeIdx,
            offset = 0,
            section = 1,
            codeSize = 64,
        ))
        mainMod.addSymbol(CodeViewBuilder.regrel32(
            name = "argc",
            typeIndex = intType,
            offset = 8,
            register = 335, // CV_AMD64_RSP
        ))
        mainMod.addSymbol(CodeViewBuilder.regrel32(
            name = "argv",
            typeIndex = charPtrPtrIdx,
            offset = 16,
            register = 335,
        ))
        mainMod.addSymbol(CodeViewBuilder.end())
        mainMod.addSourceFile("C:\\project\\main.c")
        mainMod.addLineInfo(CvLineBlock(
            fileIndex = 0,
            lines = listOf(
                CvLineEntry(0, 5, 0, true),   // line 5
                CvLineEntry(4, 6, 0, true),   // line 6
                CvLineEntry(12, 7, 0, true),  // line 7
                CvLineEntry(20, 8, 0, true),  // line 8
                CvLineEntry(32, 10, 0, true), // line 10
            ),
        ))

        // Module: util.obj
        val utilMod = writer.addModule("util.obj", "C:\\project\\util.c")
        utilMod.addSymbol(CodeViewBuilder.objname("util.obj"))
        utilMod.addSymbol(CodeViewBuilder.compile3(
            language = CvSourceLanguage.C,
            cpu = CvCpuType.X64_AMD64,
            compilerName = "kgen 1.0.0",
        ))

        // Build the PDB
        val pdbBytes = writer.build()

        // Verify via RSDS matching
        val rsdsBytes = RsdsEntry.build(guid, 1, "C:\\project\\out\\program.pdb")
        val rsds = RsdsEntry.parse(rsdsBytes)
        val pdb = PdbReader.read(pdbBytes)
        assertTrue(rsds.matches(pdb))

        // Verify types
        assertEquals(6, pdb.types.size) // char*, char**, arglist, procedure, Point fwd, Point def
        assertEquals(CvTypeKind.LF_POINTER, pdb.types[0].typeKind)
        assertEquals(CvTypeKind.LF_POINTER, pdb.types[1].typeKind)
        assertEquals(CvTypeKind.LF_ARGLIST, pdb.types[2].typeKind)
        assertEquals(CvTypeKind.LF_PROCEDURE, pdb.types[3].typeKind)
        assertEquals(CvTypeKind.LF_STRUCTURE, pdb.types[4].typeKind)
        assertEquals(CvTypeKind.LF_STRUCTURE, pdb.types[5].typeKind)

        // Verify global symbols
        assertEquals(2, pdb.globalSymbols.size)
        assertEquals(CvSymbolKind.S_PUB32, pdb.globalSymbols[0].symbolKind)
        assertEquals(CvSymbolKind.S_GDATA32, pdb.globalSymbols[1].symbolKind)

        // Verify modules
        assertEquals(2, pdb.modules.size)
        assertEquals("main.obj", pdb.modules[0].moduleName)
        assertEquals("C:\\project\\main.c", pdb.modules[0].objectFileName)
        assertEquals("util.obj", pdb.modules[1].moduleName)

        // Verify DBI header
        assertNotNull(pdb.dbiHeader)
        assertEquals(0x8664.toShort(), pdb.machine)

        // Verify GUID roundtrip
        assertEquals(guid, pdb.guid)
        assertEquals(1, pdb.age)
    }

    @Test
    fun `PDB with enum and union types`() {
        val writer = PdbWriter()
        writer.guid = UUID.randomUUID()
        writer.age = 1

        val intType = CvBuiltinType.T_INT4.code
        val floatType = CvBuiltinType.T_REAL32.code

        // enum Color { RED, GREEN, BLUE }
        writer.addType(CodeViewBuilder.enum_(
            name = "Color",
            underlyingType = intType,
            memberCount = 3,
        ))

        // union Value { int i; float f; }
        writer.addType(CodeViewBuilder.union(
            name = "Value",
            size = 4,
            memberCount = 2,
        ))

        // int[10]
        writer.addType(CodeViewBuilder.array(
            elementType = intType,
            indexType = CvBuiltinType.T_UINT4.code,
            size = 40,
        ))

        // const int
        writer.addType(CodeViewBuilder.modifier(intType, isConst = true))

        // int:4 (bitfield)
        writer.addType(CodeViewBuilder.bitfield(intType, 4, 0))

        val bytes = writer.build()
        val pdb = PdbReader.read(bytes)

        assertEquals(5, pdb.types.size)
        assertEquals(CvTypeKind.LF_ENUM, pdb.types[0].typeKind)
        assertEquals(CvTypeKind.LF_UNION, pdb.types[1].typeKind)
        assertEquals(CvTypeKind.LF_ARRAY, pdb.types[2].typeKind)
        assertEquals(CvTypeKind.LF_MODIFIER, pdb.types[3].typeKind)
        assertEquals(CvTypeKind.LF_BITFIELD, pdb.types[4].typeKind)
    }

    @Test
    fun `PDB with UDT and constant symbols`() {
        val writer = PdbWriter()
        writer.guid = UUID.randomUUID()
        writer.age = 1

        val structIdx = writer.addType(CodeViewBuilder.structure(
            name = "MyStruct",
            size = 16,
            memberCount = 2,
        ))

        writer.addGlobalSymbol(CodeViewBuilder.udt("MyStruct", structIdx))
        writer.addGlobalSymbol(CodeViewBuilder.constant("MAX_SIZE", CvBuiltinType.T_INT4.code, 1024))

        val bytes = writer.build()
        val pdb = PdbReader.read(bytes)

        assertEquals(2, pdb.globalSymbols.size)
        assertEquals(CvSymbolKind.S_UDT, pdb.globalSymbols[0].symbolKind)
        assertEquals(CvSymbolKind.S_CONSTANT, pdb.globalSymbols[1].symbolKind)
    }

    @Test
    fun `PDB with class type`() {
        val writer = PdbWriter()
        writer.guid = UUID.randomUUID()
        writer.age = 1

        writer.addType(CodeViewBuilder.structure(
            name = "MyClass",
            size = 24,
            memberCount = 3,
            isClass = true,
        ))

        val bytes = writer.build()
        val pdb = PdbReader.read(bytes)

        assertEquals(1, pdb.types.size)
        assertEquals(CvTypeKind.LF_CLASS, pdb.types[0].typeKind)
    }

    @Test
    fun `PDB with multiple modules and local symbols`() {
        val writer = PdbWriter()
        writer.guid = UUID.randomUUID()
        writer.age = 1

        val intType = CvBuiltinType.T_INT4.code

        // Local function in module 1
        val mod1 = writer.addModule("file1.obj")
        mod1.addSymbol(CodeViewBuilder.lproc32(
            name = "helper",
            typeIndex = intType,
            offset = 0,
            section = 1,
            codeSize = 20,
        ))
        mod1.addSymbol(CodeViewBuilder.end())

        // Local data in module 2
        val mod2 = writer.addModule("file2.obj")
        mod2.addSymbol(CodeViewBuilder.ldata32(
            name = "localVar",
            typeIndex = intType,
            offset = 0,
            section = 3,
        ))

        val bytes = writer.build()
        val pdb = PdbReader.read(bytes)
        assertEquals(2, pdb.modules.size)
    }

    @Test
    fun `PDB with named streams`() {
        val writer = PdbWriter()
        writer.guid = UUID.randomUUID()
        writer.age = 1

        // Add a string table named stream
        val stringTable = byteArrayOf(0) + "main.c".toByteArray() + byteArrayOf(0) + "util.c".toByteArray() + byteArrayOf(0)
        writer.addNamedStream("/names", stringTable)

        val bytes = writer.build()
        val pdb = PdbReader.read(bytes)
        assertTrue(pdb.namedStreams.containsKey("/names"))
    }

    @Test
    fun `PDB IPI stream roundtrip`() {
        val writer = PdbWriter()
        writer.guid = UUID.randomUUID()
        writer.age = 1

        // Add ID types to IPI stream
        writer.addIdType(CvTypeRecord(CvTypeKind.LF_FUNC_ID.code, ByteArray(8)))
        writer.addIdType(CvTypeRecord(CvTypeKind.LF_STRING_ID.code, ByteArray(4) + "test".toByteArray() + byteArrayOf(0)))

        val bytes = writer.build()
        val pdb = PdbReader.read(bytes)

        assertEquals(2, pdb.idTypes.size)
        assertEquals(CvTypeKind.LF_FUNC_ID, pdb.idTypes[0].typeKind)
        assertEquals(CvTypeKind.LF_STRING_ID, pdb.idTypes[1].typeKind)
    }
}
