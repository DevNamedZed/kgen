package org.kgen.binary.pe.pdb

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.kgen.binary.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

class CodeViewEmitterTest {

    @Test
    fun `empty debug info produces empty sections`() {
        val result = CodeViewEmitter.emit(DebugInfo())
        assertEquals(0, result.debugS.size)
        assertEquals(0, result.debugT.size)
    }

    @Test
    fun `minimal compile unit emits symbols`() {
        val debug = DebugInfo(listOf(CompileUnit(
            name = "test.c", directory = "/src", producer = "kgen 1.0", language = SourceLanguage.C,
        )))
        val result = CodeViewEmitter.emit(debug, "test.obj")
        assertTrue(result.debugS.isNotEmpty(), "Expected non-empty .debug\$S")
        // Should start with CV signature (4)
        val sig = ByteBuffer.wrap(result.debugS, 0, 4).order(ByteOrder.LITTLE_ENDIAN).int
        assertEquals(4, sig, "Expected CV_SIGNATURE_C13")
    }

    @Test
    fun `subprogram emits type records and symbols`() {
        val intType = DebugType.Base("int", 32, DwarfEncoding.SIGNED)
        val debug = DebugInfo(listOf(CompileUnit(
            name = "test.c", directory = "", producer = "kgen", language = SourceLanguage.C,
            types = listOf(intType),
            subprograms = listOf(DebugSubprogram(
                name = "add", file = "test.c", line = 5, returnType = intType,
                params = listOf(
                    DebugVariable("a", intType, isParameter = true),
                    DebugVariable("b", intType, isParameter = true),
                ),
                lowPC = 0, highPC = 0x30,
            )),
        )))
        val result = CodeViewEmitter.emit(debug)
        assertTrue(result.debugT.isNotEmpty(), "Expected type records for procedure")
        assertTrue(result.debugS.isNotEmpty(), "Expected symbol records")
    }

    @Test
    fun `type records start with CV signature`() {
        val intType = DebugType.Base("int", 32, DwarfEncoding.SIGNED)
        val ptrType = DebugType.Pointer(intType, 64)
        val debug = DebugInfo(listOf(CompileUnit(
            name = "test.c", directory = "", producer = "kgen", language = SourceLanguage.C,
            types = listOf(intType, ptrType),
        )))
        val result = CodeViewEmitter.emit(debug)
        if (result.debugT.isNotEmpty()) {
            val sig = ByteBuffer.wrap(result.debugT, 0, 4).order(ByteOrder.LITTLE_ENDIAN).int
            assertEquals(4, sig, "Expected CV_SIGNATURE_C13")
        }
    }

    @Test
    fun `toSections produces sections with correct names`() {
        val debug = DebugInfo(listOf(CompileUnit(
            name = "test.c", directory = "", producer = "kgen", language = SourceLanguage.C,
            subprograms = listOf(DebugSubprogram(
                name = "main", file = "test.c", line = 1, returnType = null,
                params = emptyList(), lowPC = 0, highPC = 0x10,
            )),
        )))
        val result = CodeViewEmitter.emit(debug)
        val sections = result.toSections()
        assertTrue(sections.any { it.name == ".debug\$S" })
    }

    @Test
    fun `global variable emits symbol`() {
        val intType = DebugType.Base("int", 32, DwarfEncoding.SIGNED)
        val debug = DebugInfo(listOf(CompileUnit(
            name = "test.c", directory = "", producer = "kgen", language = SourceLanguage.C,
            variables = listOf(DebugVariable("counter", intType, location = DebugLocation.Address(0x4000))),
        )))
        val result = CodeViewEmitter.emit(debug)
        assertTrue(result.debugS.isNotEmpty(), "Expected symbol records for global variable")
    }

    @Test
    fun `composite struct emits type record`() {
        val intType = DebugType.Base("int", 32, DwarfEncoding.SIGNED)
        val structType = DebugType.Composite(
            name = "Point", sizeInBits = 64, tag = CompositeTag.STRUCT,
            members = listOf(
                DebugMember("x", intType, offsetInBits = 0),
                DebugMember("y", intType, offsetInBits = 32),
            )
        )
        val debug = DebugInfo(listOf(CompileUnit(
            name = "test.c", directory = "", producer = "kgen", language = SourceLanguage.C,
            types = listOf(intType, structType),
        )))
        val result = CodeViewEmitter.emit(debug)
        assertTrue(result.debugT.isNotEmpty(), "Expected type records for struct")
    }

    @Test
    fun `enum type emits type record`() {
        val intType = DebugType.Base("int", 32, DwarfEncoding.SIGNED)
        val enumType = DebugType.Enum(
            name = "Color", sizeInBits = 32, baseType = intType,
            enumerators = listOf("RED" to 0L, "GREEN" to 1L),
        )
        val debug = DebugInfo(listOf(CompileUnit(
            name = "test.c", directory = "", producer = "kgen", language = SourceLanguage.C,
            types = listOf(intType, enumType),
        )))
        val result = CodeViewEmitter.emit(debug)
        assertTrue(result.debugT.isNotEmpty(), "Expected type records for enum")
    }

    @Test
    fun `const modifier emits type record`() {
        val intType = DebugType.Base("int", 32, DwarfEncoding.SIGNED)
        val constInt = DebugType.Const(intType)
        val debug = DebugInfo(listOf(CompileUnit(
            name = "test.c", directory = "", producer = "kgen", language = SourceLanguage.C,
            types = listOf(intType, constInt),
        )))
        val result = CodeViewEmitter.emit(debug)
        assertTrue(result.debugT.isNotEmpty(), "Expected type records for const modifier")
    }

    @Test
    fun `typedef emits UDT symbol`() {
        val intType = DebugType.Base("int", 32, DwarfEncoding.SIGNED)
        val typedef = DebugType.Typedef("myint", intType)
        val debug = DebugInfo(listOf(CompileUnit(
            name = "test.c", directory = "", producer = "kgen", language = SourceLanguage.C,
            types = listOf(intType, typedef),
        )))
        val result = CodeViewEmitter.emit(debug)
        assertTrue(result.debugS.isNotEmpty(), "Expected symbol records for typedef")
    }

    @Test
    fun `pointer type emits type record`() {
        val intType = DebugType.Base("int", 32, DwarfEncoding.SIGNED)
        val ptrType = DebugType.Pointer(intType, 64)
        val debug = DebugInfo(listOf(CompileUnit(
            name = "test.c", directory = "", producer = "kgen", language = SourceLanguage.C,
            types = listOf(intType, ptrType),
        )))
        val result = CodeViewEmitter.emit(debug)
        assertTrue(result.debugT.isNotEmpty(), "Expected type records for pointer")
    }

    @Test
    fun `subprogram with local variables`() {
        val intType = DebugType.Base("int", 32, DwarfEncoding.SIGNED)
        val debug = DebugInfo(listOf(CompileUnit(
            name = "test.c", directory = "", producer = "kgen", language = SourceLanguage.C,
            subprograms = listOf(DebugSubprogram(
                name = "compute", file = "test.c", line = 10, returnType = intType,
                params = listOf(DebugVariable("n", intType, isParameter = true, location = DebugLocation.FrameOffset(8))),
                localVariables = listOf(DebugVariable("result", intType, location = DebugLocation.FrameOffset(-4))),
                lowPC = 0, highPC = 0x40,
            )),
        )))
        val result = CodeViewEmitter.emit(debug)
        assertTrue(result.debugS.isNotEmpty())
        assertTrue(result.debugT.isNotEmpty())
    }

    @Test
    fun `various base type encodings map to builtin types`() {
        val types = listOf(
            DebugType.Base("bool", 8, DwarfEncoding.BOOLEAN),
            DebugType.Base("float", 32, DwarfEncoding.FLOAT),
            DebugType.Base("double", 64, DwarfEncoding.FLOAT),
            DebugType.Base("unsigned", 32, DwarfEncoding.UNSIGNED),
            DebugType.Base("uint64", 64, DwarfEncoding.UNSIGNED),
        )
        val debug = DebugInfo(listOf(CompileUnit(
            name = "test.c", directory = "", producer = "kgen", language = SourceLanguage.C,
            types = types,
        )))
        // Should not throw — all base types map to builtins (no type records needed)
        val result = CodeViewEmitter.emit(debug)
        assertNotNull(result)
    }

    @Test
    fun `union type emits type record`() {
        val intType = DebugType.Base("int", 32, DwarfEncoding.SIGNED)
        val floatType = DebugType.Base("float", 32, DwarfEncoding.FLOAT)
        val union = DebugType.Composite(
            name = "Value", sizeInBits = 32, tag = CompositeTag.UNION,
            members = listOf(
                DebugMember("i", intType, offsetInBits = 0),
                DebugMember("f", floatType, offsetInBits = 0),
            )
        )
        val debug = DebugInfo(listOf(CompileUnit(
            name = "test.c", directory = "", producer = "kgen", language = SourceLanguage.C,
            types = listOf(intType, floatType, union),
        )))
        val result = CodeViewEmitter.emit(debug)
        assertTrue(result.debugT.isNotEmpty(), "Expected type records for union")
    }
}
