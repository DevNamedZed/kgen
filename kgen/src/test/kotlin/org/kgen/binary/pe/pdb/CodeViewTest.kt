package org.kgen.binary.pe.pdb

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CodeViewTest {

    @Test
    fun `CvTypeKind fromCode resolves known types`() {
        assertEquals(CvTypeKind.LF_POINTER, CvTypeKind.fromCode(0x1002))
        assertEquals(CvTypeKind.LF_PROCEDURE, CvTypeKind.fromCode(0x1008))
        assertEquals(CvTypeKind.LF_STRUCTURE, CvTypeKind.fromCode(0x1505))
        assertEquals(CvTypeKind.LF_CLASS, CvTypeKind.fromCode(0x1504))
        assertEquals(CvTypeKind.LF_ENUM, CvTypeKind.fromCode(0x1507))
        assertEquals(CvTypeKind.LF_UNION, CvTypeKind.fromCode(0x1506))
        assertEquals(CvTypeKind.LF_ARGLIST, CvTypeKind.fromCode(0x1201))
        assertEquals(CvTypeKind.LF_MEMBER, CvTypeKind.fromCode(0x150D))
        assertEquals(CvTypeKind.LF_MODIFIER, CvTypeKind.fromCode(0x1001))
        assertEquals(CvTypeKind.LF_BITFIELD, CvTypeKind.fromCode(0x1205))
        assertEquals(CvTypeKind.LF_ARRAY, CvTypeKind.fromCode(0x1503))
    }

    @Test
    fun `CvTypeKind fromCode returns null for unknown`() {
        assertNull(CvTypeKind.fromCode(0x0000))
        assertNull(CvTypeKind.fromCode(0xFFFF))
    }

    @Test
    fun `CvSymbolKind fromCode resolves known symbols`() {
        assertEquals(CvSymbolKind.S_GPROC32, CvSymbolKind.fromCode(0x1110))
        assertEquals(CvSymbolKind.S_LPROC32, CvSymbolKind.fromCode(0x110F))
        assertEquals(CvSymbolKind.S_GDATA32, CvSymbolKind.fromCode(0x110D))
        assertEquals(CvSymbolKind.S_PUB32, CvSymbolKind.fromCode(0x110E))
        assertEquals(CvSymbolKind.S_UDT, CvSymbolKind.fromCode(0x1108))
        assertEquals(CvSymbolKind.S_END, CvSymbolKind.fromCode(0x0006))
        assertEquals(CvSymbolKind.S_COMPILE3, CvSymbolKind.fromCode(0x113C))
        assertEquals(CvSymbolKind.S_REGREL32, CvSymbolKind.fromCode(0x1111))
    }

    @Test
    fun `CvSymbolKind fromCode returns null for unknown`() {
        assertNull(CvSymbolKind.fromCode(0xFFFF))
    }

    @Test
    fun `CvBuiltinType fromCode resolves common types`() {
        assertEquals(CvBuiltinType.T_VOID, CvBuiltinType.fromCode(0x0003))
        assertEquals(CvBuiltinType.T_INT4, CvBuiltinType.fromCode(0x0074))
        assertEquals(CvBuiltinType.T_UINT4, CvBuiltinType.fromCode(0x0075))
        assertEquals(CvBuiltinType.T_REAL32, CvBuiltinType.fromCode(0x0040))
        assertEquals(CvBuiltinType.T_REAL64, CvBuiltinType.fromCode(0x0041))
        assertEquals(CvBuiltinType.T_64PVOID, CvBuiltinType.fromCode(0x0603))
        assertEquals(CvBuiltinType.T_NOTYPE, CvBuiltinType.fromCode(0x0000))
    }

    @Test
    fun `CvCpuType fromCode resolves known CPUs`() {
        assertEquals(CvCpuType.X64_AMD64, CvCpuType.fromCode(0xD0))
        assertEquals(CvCpuType.ARM64, CvCpuType.fromCode(0xF0))
        assertEquals(CvCpuType.INTEL_80386, CvCpuType.fromCode(0x03))
    }

    @Test
    fun `CvSourceLanguage fromCode resolves known languages`() {
        assertEquals(CvSourceLanguage.C, CvSourceLanguage.fromCode(0))
        assertEquals(CvSourceLanguage.CPP, CvSourceLanguage.fromCode(1))
        assertEquals(CvSourceLanguage.RUST, CvSourceLanguage.fromCode(0x13))
        assertEquals(CvSourceLanguage.KOTLIN, CvSourceLanguage.fromCode(0x14))
    }

    @Test
    fun `CvPointerKind fromCode`() {
        assertEquals(CvPointerKind.PTR_64, CvPointerKind.fromCode(0x0C))
        assertEquals(CvPointerKind.PTR_NEAR32, CvPointerKind.fromCode(0x0A))
    }

    @Test
    fun `CvPointerMode fromCode`() {
        assertEquals(CvPointerMode.POINTER, CvPointerMode.fromCode(0))
        assertEquals(CvPointerMode.LVALUE_REFERENCE, CvPointerMode.fromCode(1))
        assertEquals(CvPointerMode.RVALUE_REFERENCE, CvPointerMode.fromCode(3))
    }

    @Test
    fun `CvMemberAccess fromCode`() {
        assertEquals(CvMemberAccess.PRIVATE, CvMemberAccess.fromCode(1))
        assertEquals(CvMemberAccess.PROTECTED, CvMemberAccess.fromCode(2))
        assertEquals(CvMemberAccess.PUBLIC, CvMemberAccess.fromCode(3))
    }

    @Test
    fun `DebugSubsectionKind fromCode`() {
        assertEquals(DebugSubsectionKind.LINES, DebugSubsectionKind.fromCode(0xF2))
        assertEquals(DebugSubsectionKind.FILE_CHECKSUMS, DebugSubsectionKind.fromCode(0xF4))
        assertEquals(DebugSubsectionKind.STRING_TABLE, DebugSubsectionKind.fromCode(0xF3))
    }

    @Test
    fun `CodeViewBuilder pointer record`() {
        val record = CodeViewBuilder.pointer(CvBuiltinType.T_INT4.code)
        assertEquals(CvTypeKind.LF_POINTER.code, record.kind)
        assertEquals(8, record.data.size)
    }

    @Test
    fun `CodeViewBuilder argList record`() {
        val record = CodeViewBuilder.argList(listOf(CvBuiltinType.T_INT4.code, CvBuiltinType.T_REAL64.code))
        assertEquals(CvTypeKind.LF_ARGLIST.code, record.kind)
        // 4 bytes count + 2 * 4 bytes args = 12
        assertEquals(12, record.data.size)
    }

    @Test
    fun `CodeViewBuilder procedure record`() {
        val record = CodeViewBuilder.procedure(
            returnType = CvBuiltinType.T_INT4.code,
            paramCount = 2,
            argListIndex = 0x1000,
        )
        assertEquals(CvTypeKind.LF_PROCEDURE.code, record.kind)
    }

    @Test
    fun `CodeViewBuilder structure record contains name`() {
        val record = CodeViewBuilder.structure(name = "TestStruct", size = 16, memberCount = 3)
        assertEquals(CvTypeKind.LF_STRUCTURE.code, record.kind)
        val nameStr = String(record.data, record.data.size - "TestStruct".length - 1, "TestStruct".length)
        assertEquals("TestStruct", nameStr)
    }

    @Test
    fun `CodeViewBuilder gproc32 record`() {
        val record = CodeViewBuilder.gproc32(
            name = "main",
            typeIndex = 0x1000,
            offset = 0,
            section = 1,
            codeSize = 100,
        )
        assertEquals(CvSymbolKind.S_GPROC32.code, record.kind)
        // Name should be in the data
        val str = String(record.data, record.data.size - "main".length - 1, "main".length)
        assertEquals("main", str)
    }

    @Test
    fun `CodeViewBuilder lproc32 record`() {
        val record = CodeViewBuilder.lproc32("helper", 0x1001, 100, 1, 50)
        assertEquals(CvSymbolKind.S_LPROC32.code, record.kind)
    }

    @Test
    fun `CodeViewBuilder gdata32 record`() {
        val record = CodeViewBuilder.gdata32("myGlobal", CvBuiltinType.T_INT4.code, 0, 3)
        assertEquals(CvSymbolKind.S_GDATA32.code, record.kind)
    }

    @Test
    fun `CodeViewBuilder pub32 record`() {
        val record = CodeViewBuilder.pub32("_main", 0, 1, CvPublicSymbolFlags.FUNCTION)
        assertEquals(CvSymbolKind.S_PUB32.code, record.kind)
    }

    @Test
    fun `CodeViewBuilder udt record`() {
        val record = CodeViewBuilder.udt("MyType", 0x1005)
        assertEquals(CvSymbolKind.S_UDT.code, record.kind)
    }

    @Test
    fun `CodeViewBuilder compile3 record`() {
        val record = CodeViewBuilder.compile3(CvSourceLanguage.C, CvCpuType.X64_AMD64, "kgen")
        assertEquals(CvSymbolKind.S_COMPILE3.code, record.kind)
    }

    @Test
    fun `CodeViewBuilder regrel32 record`() {
        val record = CodeViewBuilder.regrel32("localVar", CvBuiltinType.T_INT4.code, -8, 334)
        assertEquals(CvSymbolKind.S_REGREL32.code, record.kind)
    }

    @Test
    fun `CodeViewBuilder objname record`() {
        val record = CodeViewBuilder.objname("test.obj")
        assertEquals(CvSymbolKind.S_OBJNAME.code, record.kind)
    }

    @Test
    fun `CodeViewBuilder end record`() {
        val record = CodeViewBuilder.end()
        assertEquals(CvSymbolKind.S_END.code, record.kind)
        assertEquals(0, record.data.size)
    }

    @Test
    fun `CodeViewBuilder constant record small value`() {
        val record = CodeViewBuilder.constant("MAX_SIZE", CvBuiltinType.T_INT4.code, 256)
        assertEquals(CvSymbolKind.S_CONSTANT.code, record.kind)
    }

    @Test
    fun `CvTypeRecord equals and hashCode`() {
        val a = CvTypeRecord(0x1505, byteArrayOf(1, 2, 3))
        val b = CvTypeRecord(0x1505, byteArrayOf(1, 2, 3))
        val c = CvTypeRecord(0x1505, byteArrayOf(1, 2, 4))
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assert(a != c)
    }

    @Test
    fun `CvSymbolRecord equals and hashCode`() {
        val a = CvSymbolRecord(0x1110, byteArrayOf(5, 6))
        val b = CvSymbolRecord(0x1110, byteArrayOf(5, 6))
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }
}
