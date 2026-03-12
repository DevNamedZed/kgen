package org.kgen.binary.pe.pdb

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PdbComprehensiveTest {

    private fun buildMinimalPdb(
        configure: PdbWriter.() -> Unit = {},
    ): PdbFile {
        val writer = PdbWriter()
        writer.guid = UUID.fromString("01020304-0506-0708-090a-0b0c0d0e0f10")
        writer.age = 1
        writer.configure()
        val bytes = writer.build()
        return PdbReader.read(bytes)
    }

    @Test
    fun `MSF superblock MAGIC is correct`() {
        val magic = MsfSuperBlock.MAGIC
        assertTrue(magic.isNotEmpty())
        val str = String(magic, Charsets.US_ASCII)
        assertTrue(str.startsWith("Microsoft C/C++ MSF 7.00"))
    }

    @Test
    fun `MSF superblock SIZE is 56`() {
        assertEquals(56, MsfSuperBlock.SIZE)
    }

    @Test
    fun `MSF superblock parse rejects data smaller than SIZE`() {
        assertThrows<IllegalArgumentException> {
            MsfSuperBlock.parse(ByteArray(55))
        }
    }

    @Test
    fun `MSF superblock parse rejects wrong magic`() {
        assertThrows<IllegalArgumentException> {
            MsfSuperBlock.parse(ByteArray(64))
        }
    }

    @Test
    fun `MSF superblock parse returns correct fields`() {
        val writer = MsfWriter(blockSize = 4096)
        writer.addStream("test".toByteArray())
        val data = writer.build()
        val sb = MsfSuperBlock.parse(data)
        assertEquals(4096, sb.blockSize)
        assertTrue(sb.blockCount > 0)
        assertTrue(sb.directorySize > 0)
        assertEquals(1, sb.freeBlockMapIndex)
    }

    @Test
    fun `MsfReader isMsf accepts valid MSF`() {
        val writer = MsfWriter()
        assertTrue(MsfReader.isMsf(writer.build()))
    }

    @Test
    fun `MsfReader isMsf rejects empty data`() {
        assertFalse(MsfReader.isMsf(byteArrayOf()))
    }

    @Test
    fun `MsfReader isMsf rejects short data`() {
        assertFalse(MsfReader.isMsf(ByteArray(32)))
    }

    @Test
    fun `MsfReader isMsf rejects random data`() {
        assertFalse(MsfReader.isMsf(ByteArray(100) { 0x42 }))
    }

    @Test
    fun `MsfWriter empty build produces valid MSF`() {
        val writer = MsfWriter()
        val bytes = writer.build()
        assertTrue(MsfReader.isMsf(bytes))
        val msf = MsfReader.read(bytes)
        assertEquals(0, msf.streamCount)
    }

    @Test
    fun `MsfWriter addStream returns sequential indices`() {
        val writer = MsfWriter()
        assertEquals(0, writer.addStream("a".toByteArray()))
        assertEquals(1, writer.addStream("b".toByteArray()))
        assertEquals(2, writer.addStream("c".toByteArray()))
    }

    @Test
    fun `MsfWriter addEmptyStream returns index`() {
        val writer = MsfWriter()
        assertEquals(0, writer.addEmptyStream())
    }

    @Test
    fun `MSF round-trip single stream preserves data`() {
        val writer = MsfWriter()
        val data = byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte())
        writer.addStream(data)
        val msf = MsfReader.read(writer.build())
        assertTrue(data.contentEquals(msf.streamData(0)))
    }

    @Test
    fun `MSF round-trip empty stream`() {
        val writer = MsfWriter()
        writer.addEmptyStream()
        val msf = MsfReader.read(writer.build())
        assertEquals(1, msf.streamCount)
        assertEquals(0, msf.streams[0].size)
        assertEquals(0, msf.streamData(0).size)
    }

    @Test
    fun `MSF round-trip multi-block stream`() {
        val writer = MsfWriter(blockSize = 4096)
        val data = ByteArray(12345) { (it % 256).toByte() }
        writer.addStream(data)
        val msf = MsfReader.read(writer.build())
        assertTrue(data.contentEquals(msf.streamData(0)))
        assertTrue(msf.streams[0].blocks.size >= 4) // 12345/4096 = 4 blocks
    }

    @Test
    fun `MSF round-trip exact block-sized stream`() {
        val writer = MsfWriter(blockSize = 4096)
        val data = ByteArray(4096) { 0xAA.toByte() }
        writer.addStream(data)
        val msf = MsfReader.read(writer.build())
        assertEquals(4096, msf.streamData(0).size)
        assertTrue(data.contentEquals(msf.streamData(0)))
    }

    @Test
    fun `MSF round-trip many streams`() {
        val writer = MsfWriter()
        val streams = (0..9).map { ByteArray(100 + it * 50) { (it % 256).toByte() } }
        streams.forEach { writer.addStream(it) }
        val msf = MsfReader.read(writer.build())
        assertEquals(10, msf.streamCount)
        for (i in streams.indices) {
            assertEquals(streams[i].size, msf.streams[i].size)
            assertTrue(streams[i].contentEquals(msf.streamData(i)))
        }
    }

    @Test
    fun `MSF mixed empty and non-empty streams`() {
        val writer = MsfWriter()
        writer.addEmptyStream()
        writer.addStream("hello".toByteArray())
        writer.addEmptyStream()
        writer.addStream("world".toByteArray())
        val msf = MsfReader.read(writer.build())
        assertEquals(4, msf.streamCount)
        assertEquals(0, msf.streamData(0).size)
        assertEquals("hello", String(msf.streamData(1)))
        assertEquals(0, msf.streamData(2).size)
        assertEquals("world", String(msf.streamData(3)))
    }

    @Test
    fun `MsfFile blockSize property`() {
        val writer = MsfWriter(blockSize = 4096)
        writer.addStream("x".toByteArray())
        val msf = MsfReader.read(writer.build())
        assertEquals(4096, msf.blockSize)
    }

    @Test
    fun `MsfFile blockCount property`() {
        val writer = MsfWriter(blockSize = 4096)
        writer.addStream("x".toByteArray())
        val msf = MsfReader.read(writer.build())
        assertTrue(msf.blockCount > 0)
    }

    @Test
    fun `MsfStream properties`() {
        val stream = MsfStream(size = 100, blocks = listOf(3, 4, 5))
        assertEquals(100, stream.size)
        assertEquals(listOf(3, 4, 5), stream.blocks)
    }

    @Test
    fun `MsfStream data class equality`() {
        val s1 = MsfStream(10, listOf(1, 2))
        val s2 = MsfStream(10, listOf(1, 2))
        assertEquals(s1, s2)
    }

    @Test
    fun `PdbReader isPdb accepts valid PDB`() {
        val writer = PdbWriter()
        writer.guid = UUID.randomUUID()
        writer.age = 1
        assertTrue(PdbReader.isPdb(writer.build()))
    }

    @Test
    fun `PdbReader isPdb rejects non-PDB data`() {
        assertFalse(PdbReader.isPdb(byteArrayOf()))
        assertFalse(PdbReader.isPdb(ByteArray(100)))
    }

    @Test
    fun `PdbReader read returns valid PdbFile`() {
        val pdb = buildMinimalPdb()
        assertNotNull(pdb)
        assertNotNull(pdb.guid)
        assertNotNull(pdb.info)
    }

    @Test
    fun `PDB GUID round-trips correctly`() {
        val guid = UUID.fromString("AABBCCDD-EEFF-0011-2233-445566778899")
        val pdb = buildMinimalPdb { this.guid = guid }
        assertEquals(guid, pdb.guid)
    }

    @Test
    fun `PDB age round-trips correctly`() {
        val pdb = buildMinimalPdb { this.age = 7 }
        assertEquals(7, pdb.age)
    }

    @Test
    fun `PDB version is VC70 by default`() {
        val pdb = buildMinimalPdb()
        assertEquals(PdbInfoStream.PdbStreamVersion.VC70, pdb.version)
    }

    @Test
    fun `PDB matchesGuid returns true for matching guid and age`() {
        val guid = UUID.randomUUID()
        val pdb = buildMinimalPdb {
            this.guid = guid
            this.age = 5
        }
        assertTrue(pdb.matchesGuid(guid, 5))
    }

    @Test
    fun `PDB matchesGuid returns false for wrong age`() {
        val guid = UUID.randomUUID()
        val pdb = buildMinimalPdb {
            this.guid = guid
            this.age = 5
        }
        assertFalse(pdb.matchesGuid(guid, 6))
    }

    @Test
    fun `PDB matchesGuid returns false for wrong guid`() {
        val pdb = buildMinimalPdb { this.age = 1 }
        assertFalse(pdb.matchesGuid(UUID.randomUUID(), 1))
    }

    @Test
    fun `PDB machine default is AMD64`() {
        val pdb = buildMinimalPdb()
        assertEquals(0x8664.toShort(), pdb.machine)
    }

    @Test
    fun `PDB machine round-trips`() {
        val pdb = buildMinimalPdb { this.machine = 0x014C.toShort() } // i386
        assertEquals(0x014C.toShort(), pdb.machine)
    }

    @Test
    fun `PDB with no types has empty types list`() {
        val pdb = buildMinimalPdb()
        assertTrue(pdb.types.isEmpty())
    }

    @Test
    fun `PDB with no id types has empty idTypes list`() {
        val pdb = buildMinimalPdb()
        assertTrue(pdb.idTypes.isEmpty())
    }

    @Test
    fun `PDB with no modules has empty modules list`() {
        val pdb = buildMinimalPdb()
        assertTrue(pdb.modules.isEmpty())
    }

    @Test
    fun `PDB addType returns sequential type indices starting at 0x1000`() {
        val writer = PdbWriter()
        writer.guid = UUID.randomUUID()
        val idx1 = writer.addType(CodeViewBuilder.argList(emptyList()))
        val idx2 = writer.addType(CodeViewBuilder.argList(listOf(0x74)))
        assertEquals(0x1000, idx1)
        assertEquals(0x1001, idx2)
    }

    @Test
    fun `PDB addIdType returns sequential type indices starting at 0x1000`() {
        val writer = PdbWriter()
        writer.guid = UUID.randomUUID()
        val idx = writer.addIdType(CvTypeRecord(CvTypeKind.LF_FUNC_ID.code, ByteArray(12)))
        assertEquals(0x1000, idx)
    }

    @Test
    fun `PDB types round-trip through write and read`() {
        val pdb = buildMinimalPdb {
            addType(CodeViewBuilder.argList(listOf(CvBuiltinType.T_INT4.code)))
            addType(CodeViewBuilder.procedure(
                returnType = CvBuiltinType.T_INT4.code, paramCount = 1, argListIndex = 0x1000
            ))
        }
        assertEquals(2, pdb.types.size)
        assertEquals(CvTypeKind.LF_ARGLIST, pdb.types[0].typeKind)
        assertEquals(CvTypeKind.LF_PROCEDURE, pdb.types[1].typeKind)
    }

    @Test
    fun `PDB structure type round-trips`() {
        val pdb = buildMinimalPdb {
            addType(CodeViewBuilder.structure(name = "Point", size = 8, memberCount = 2))
        }
        assertEquals(1, pdb.types.size)
        assertEquals(CvTypeKind.LF_STRUCTURE, pdb.types[0].typeKind)
    }

    @Test
    fun `PDB class type round-trips`() {
        val pdb = buildMinimalPdb {
            addType(CodeViewBuilder.structure(name = "Widget", isClass = true, size = 16))
        }
        assertEquals(1, pdb.types.size)
        assertEquals(CvTypeKind.LF_CLASS, pdb.types[0].typeKind)
    }

    @Test
    fun `PDB union type round-trips`() {
        val pdb = buildMinimalPdb {
            addType(CodeViewBuilder.union(name = "Value", size = 8, memberCount = 2))
        }
        assertEquals(CvTypeKind.LF_UNION, pdb.types[0].typeKind)
    }

    @Test
    fun `PDB enum type round-trips`() {
        val pdb = buildMinimalPdb {
            addType(CodeViewBuilder.enum_("Color", CvBuiltinType.T_INT4.code, memberCount = 3))
        }
        assertEquals(CvTypeKind.LF_ENUM, pdb.types[0].typeKind)
    }

    @Test
    fun `PDB pointer type round-trips`() {
        val pdb = buildMinimalPdb {
            addType(CodeViewBuilder.pointer(CvBuiltinType.T_INT4.code))
        }
        assertEquals(CvTypeKind.LF_POINTER, pdb.types[0].typeKind)
    }

    @Test
    fun `PDB modifier type round-trips`() {
        val pdb = buildMinimalPdb {
            addType(CodeViewBuilder.modifier(CvBuiltinType.T_INT4.code, isConst = true))
        }
        assertEquals(CvTypeKind.LF_MODIFIER, pdb.types[0].typeKind)
    }

    @Test
    fun `PDB array type round-trips`() {
        val pdb = buildMinimalPdb {
            addType(CodeViewBuilder.array(CvBuiltinType.T_INT4.code, CvBuiltinType.T_UINT4.code, 40))
        }
        assertEquals(CvTypeKind.LF_ARRAY, pdb.types[0].typeKind)
    }

    @Test
    fun `PDB bitfield type round-trips`() {
        val pdb = buildMinimalPdb {
            addType(CodeViewBuilder.bitfield(CvBuiltinType.T_INT4.code, 4, 0))
        }
        assertEquals(CvTypeKind.LF_BITFIELD, pdb.types[0].typeKind)
    }

    @Test
    fun `PDB global symbols round-trip`() {
        val pdb = buildMinimalPdb {
            addGlobalSymbol(CodeViewBuilder.gdata32("globalVar", CvBuiltinType.T_INT4.code, 0, 3))
            addGlobalSymbol(CodeViewBuilder.pub32("_main", 0, 1, CvPublicSymbolFlags.FUNCTION))
        }
        assertEquals(2, pdb.globalSymbols.size)
        assertEquals(CvSymbolKind.S_GDATA32, pdb.globalSymbols[0].symbolKind)
        assertEquals(CvSymbolKind.S_PUB32, pdb.globalSymbols[1].symbolKind)
    }

    @Test
    fun `PDB module round-trips name`() {
        val pdb = buildMinimalPdb {
            val mod = addModule("main.obj", "C:\\src\\main.c")
            mod.addSymbol(CodeViewBuilder.compile3(CvSourceLanguage.C, CvCpuType.X64_AMD64, "kgen"))
        }
        assertTrue(pdb.modules.isNotEmpty())
        assertEquals("main.obj", pdb.modules[0].moduleName)
    }

    @Test
    fun `PDB module round-trips object file name`() {
        val pdb = buildMinimalPdb {
            val mod = addModule("mod.obj", "C:\\src\\mod.c")
            mod.addSymbol(CodeViewBuilder.objname("mod.obj"))
        }
        assertEquals("C:\\src\\mod.c", pdb.modules[0].objectFileName)
    }

    @Test
    fun `PDB multiple modules`() {
        val pdb = buildMinimalPdb {
            val m1 = addModule("a.obj")
            m1.addSymbol(CodeViewBuilder.objname("a.obj"))
            val m2 = addModule("b.obj")
            m2.addSymbol(CodeViewBuilder.objname("b.obj"))
        }
        assertEquals(2, pdb.modules.size)
        assertEquals("a.obj", pdb.modules[0].moduleName)
        assertEquals("b.obj", pdb.modules[1].moduleName)
    }

    @Test
    fun `PDB DBI header present after write`() {
        val pdb = buildMinimalPdb()
        assertNotNull(pdb.dbiHeader)
    }

    @Test
    fun `PDB DBI header version is V70`() {
        val pdb = buildMinimalPdb()
        assertEquals(DbiStreamHeader.VERSION_V70, pdb.dbiHeader!!.versionHeader)
    }

    @Test
    fun `PDB TPI header present after write`() {
        val pdb = buildMinimalPdb()
        assertNotNull(pdb.tpiHeader)
    }

    @Test
    fun `PDB TPI header version is V80`() {
        val pdb = buildMinimalPdb()
        assertEquals(TpiStreamHeader.VERSION_V80, pdb.tpiHeader!!.version)
    }

    @Test
    fun `PDB TPI header SIZE constant is 56`() {
        assertEquals(56, TpiStreamHeader.SIZE)
    }

    @Test
    fun `PDB DBI header SIZE constant is 64`() {
        assertEquals(64, DbiStreamHeader.SIZE)
    }

    @Test
    fun `PDB TPI type index range`() {
        val pdb = buildMinimalPdb {
            addType(CodeViewBuilder.argList(emptyList()))
            addType(CodeViewBuilder.argList(listOf(0x74)))
        }
        val tpi = pdb.tpiHeader!!
        assertEquals(0x1000, tpi.typeIndexBegin)
        assertEquals(0x1002, tpi.typeIndexEnd)
    }

    @Test
    fun `PDB named streams round-trip`() {
        val pdb = buildMinimalPdb {
            addNamedStream("/names", "string table data".toByteArray())
        }
        assertTrue(pdb.namedStreams.containsKey("/names"))
    }

    @Test
    fun `PDB info stream has guid`() {
        val guid = UUID.randomUUID()
        val pdb = buildMinimalPdb { this.guid = guid }
        assertEquals(guid, pdb.info.guid)
    }

    @Test
    fun `PDB info stream has age`() {
        val pdb = buildMinimalPdb { this.age = 42 }
        assertEquals(42, pdb.info.age)
    }

    @Test
    fun `PDB info stream version`() {
        val pdb = buildMinimalPdb()
        assertEquals(PdbInfoStream.PdbStreamVersion.VC70, pdb.info.version)
    }

    @Test
    fun `PdbStreamVersion fromCode resolves known versions`() {
        assertEquals(PdbInfoStream.PdbStreamVersion.VC70, PdbInfoStream.PdbStreamVersion.fromCode(20000404))
        assertEquals(PdbInfoStream.PdbStreamVersion.VC80, PdbInfoStream.PdbStreamVersion.fromCode(20030901))
        assertEquals(PdbInfoStream.PdbStreamVersion.VC140, PdbInfoStream.PdbStreamVersion.fromCode(20140508))
    }

    @Test
    fun `PdbStreamVersion fromCode returns null for unknown`() {
        assertNull(PdbInfoStream.PdbStreamVersion.fromCode(12345))
    }

    @Test
    fun `PdbStreamVersion all entries have unique codes`() {
        val codes = PdbInfoStream.PdbStreamVersion.entries.map { it.code }
        assertEquals(codes.size, codes.toSet().size)
    }

    @Test
    fun `PdbStreamIndex constants`() {
        assertEquals(0, PdbStreamIndex.OLD_DIRECTORY)
        assertEquals(1, PdbStreamIndex.PDB_INFO)
        assertEquals(2, PdbStreamIndex.TPI)
        assertEquals(3, PdbStreamIndex.DBI)
        assertEquals(4, PdbStreamIndex.IPI)
    }

    @Test
    fun `CvTypeRecord equality with same data`() {
        val a = CvTypeRecord(0x1505, byteArrayOf(1, 2, 3))
        val b = CvTypeRecord(0x1505, byteArrayOf(1, 2, 3))
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `CvTypeRecord inequality with different kind`() {
        val a = CvTypeRecord(0x1505, byteArrayOf(1, 2, 3))
        val b = CvTypeRecord(0x1506, byteArrayOf(1, 2, 3))
        assertFalse(a == b)
    }

    @Test
    fun `CvTypeRecord inequality with different data`() {
        val a = CvTypeRecord(0x1505, byteArrayOf(1, 2, 3))
        val b = CvTypeRecord(0x1505, byteArrayOf(1, 2, 4))
        assertFalse(a == b)
    }

    @Test
    fun `CvTypeRecord typeKind resolves known kind`() {
        val r = CvTypeRecord(CvTypeKind.LF_STRUCTURE.code, byteArrayOf())
        assertEquals(CvTypeKind.LF_STRUCTURE, r.typeKind)
    }

    @Test
    fun `CvTypeRecord typeKind returns null for unknown kind`() {
        val r = CvTypeRecord(0x9999, byteArrayOf())
        assertNull(r.typeKind)
    }

    @Test
    fun `CvSymbolRecord equality`() {
        val a = CvSymbolRecord(0x1110, byteArrayOf(5, 6))
        val b = CvSymbolRecord(0x1110, byteArrayOf(5, 6))
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `CvSymbolRecord inequality`() {
        val a = CvSymbolRecord(0x1110, byteArrayOf(5, 6))
        val b = CvSymbolRecord(0x1110, byteArrayOf(5, 7))
        assertFalse(a == b)
    }

    @Test
    fun `CvSymbolRecord symbolKind resolves`() {
        val r = CvSymbolRecord(CvSymbolKind.S_GPROC32.code, byteArrayOf())
        assertEquals(CvSymbolKind.S_GPROC32, r.symbolKind)
    }

    @Test
    fun `CvSymbolRecord symbolKind returns null for unknown`() {
        val r = CvSymbolRecord(0x9999, byteArrayOf())
        assertNull(r.symbolKind)
    }

    @Test
    fun `CvTypeKind all entries have unique codes`() {
        val codes = CvTypeKind.entries.map { it.code }
        assertEquals(codes.size, codes.toSet().size)
    }

    @Test
    fun `CvSymbolKind all entries have unique codes`() {
        val codes = CvSymbolKind.entries.map { it.code }
        assertEquals(codes.size, codes.toSet().size)
    }

    @Test
    fun `CvBuiltinType all entries have unique codes`() {
        val codes = CvBuiltinType.entries.map { it.code }
        assertEquals(codes.size, codes.toSet().size)
    }

    @Test
    fun `CvBuiltinType fromCode resolves known types`() {
        assertEquals(CvBuiltinType.T_VOID, CvBuiltinType.fromCode(0x0003))
        assertEquals(CvBuiltinType.T_INT4, CvBuiltinType.fromCode(0x0074))
        assertEquals(CvBuiltinType.T_UINT4, CvBuiltinType.fromCode(0x0075))
        assertEquals(CvBuiltinType.T_REAL32, CvBuiltinType.fromCode(0x0040))
        assertEquals(CvBuiltinType.T_REAL64, CvBuiltinType.fromCode(0x0041))
        assertEquals(CvBuiltinType.T_NOTYPE, CvBuiltinType.fromCode(0x0000))
        assertEquals(CvBuiltinType.T_64PVOID, CvBuiltinType.fromCode(0x0603))
    }

    @Test
    fun `CvBuiltinType fromCode returns null for unknown`() {
        assertNull(CvBuiltinType.fromCode(0x9999))
    }

    @Test
    fun `CvPointerKind fromCode`() {
        assertEquals(CvPointerKind.PTR_64, CvPointerKind.fromCode(0x0C))
        assertEquals(CvPointerKind.PTR_NEAR32, CvPointerKind.fromCode(0x0A))
        assertNull(CvPointerKind.fromCode(0xFF))
    }

    @Test
    fun `CvPointerMode fromCode`() {
        assertEquals(CvPointerMode.POINTER, CvPointerMode.fromCode(0))
        assertEquals(CvPointerMode.LVALUE_REFERENCE, CvPointerMode.fromCode(1))
        assertEquals(CvPointerMode.POINTER_TO_MEMBER, CvPointerMode.fromCode(2))
        assertEquals(CvPointerMode.RVALUE_REFERENCE, CvPointerMode.fromCode(3))
        assertNull(CvPointerMode.fromCode(99))
    }

    @Test
    fun `CvMemberAccess fromCode`() {
        assertEquals(CvMemberAccess.PRIVATE, CvMemberAccess.fromCode(1))
        assertEquals(CvMemberAccess.PROTECTED, CvMemberAccess.fromCode(2))
        assertEquals(CvMemberAccess.PUBLIC, CvMemberAccess.fromCode(3))
        assertNull(CvMemberAccess.fromCode(0))
    }

    @Test
    fun `CvCpuType fromCode resolves known CPUs`() {
        assertEquals(CvCpuType.X64_AMD64, CvCpuType.fromCode(0xD0))
        assertEquals(CvCpuType.ARM64, CvCpuType.fromCode(0xF0))
        assertEquals(CvCpuType.INTEL_80386, CvCpuType.fromCode(0x03))
        assertEquals(CvCpuType.ARM, CvCpuType.fromCode(0x50))
    }

    @Test
    fun `CvCpuType fromCode returns null for unknown`() {
        assertNull(CvCpuType.fromCode(0xBB))
    }

    @Test
    fun `CvSourceLanguage fromCode resolves known languages`() {
        assertEquals(CvSourceLanguage.C, CvSourceLanguage.fromCode(0))
        assertEquals(CvSourceLanguage.CPP, CvSourceLanguage.fromCode(1))
        assertEquals(CvSourceLanguage.CSHARP, CvSourceLanguage.fromCode(0x0A))
        assertEquals(CvSourceLanguage.RUST, CvSourceLanguage.fromCode(0x13))
        assertEquals(CvSourceLanguage.KOTLIN, CvSourceLanguage.fromCode(0x14))
    }

    @Test
    fun `CvSourceLanguage fromCode returns null for unknown`() {
        assertNull(CvSourceLanguage.fromCode(0xFF))
    }

    @Test
    fun `CvPublicSymbolFlags constants`() {
        assertEquals(0x00000001, CvPublicSymbolFlags.CODE)
        assertEquals(0x00000002, CvPublicSymbolFlags.FUNCTION)
        assertEquals(0x00000004, CvPublicSymbolFlags.MANAGED)
        assertEquals(0x00000008, CvPublicSymbolFlags.MSIL)
    }

    @Test
    fun `CvProcFlags constants`() {
        assertEquals(0x01, CvProcFlags.FRAME_POINTER_PRESENT)
        assertEquals(0x02, CvProcFlags.HAS_ALLOCA)
        assertEquals(0x04, CvProcFlags.HAS_SETJMP)
        assertEquals(0x08, CvProcFlags.HAS_LONGJMP)
        assertEquals(0x10, CvProcFlags.HAS_INLINE_ASM)
        assertEquals(0x20, CvProcFlags.HAS_EH)
        assertEquals(0x40, CvProcFlags.INLINE_SPEC)
        assertEquals(0x80, CvProcFlags.HAS_SEH)
    }

    @Test
    fun `CvTypeProperties constants`() {
        assertEquals(0x0001, CvTypeProperties.PACKED)
        assertEquals(0x0002, CvTypeProperties.HAS_CTOR)
        assertEquals(0x0080, CvTypeProperties.FORWARD_REF)
        assertEquals(0x0200, CvTypeProperties.HAS_UNIQUE_NAME)
        assertEquals(0x0400, CvTypeProperties.SEALED)
    }

    @Test
    fun `DebugSubsectionKind fromCode resolves known kinds`() {
        assertEquals(DebugSubsectionKind.SYMBOLS, DebugSubsectionKind.fromCode(0xF1))
        assertEquals(DebugSubsectionKind.LINES, DebugSubsectionKind.fromCode(0xF2))
        assertEquals(DebugSubsectionKind.STRING_TABLE, DebugSubsectionKind.fromCode(0xF3))
        assertEquals(DebugSubsectionKind.FILE_CHECKSUMS, DebugSubsectionKind.fromCode(0xF4))
        assertEquals(DebugSubsectionKind.FRAME_DATA, DebugSubsectionKind.fromCode(0xF5))
        assertEquals(DebugSubsectionKind.INLINEE_LINES, DebugSubsectionKind.fromCode(0xF6))
    }

    @Test
    fun `DebugSubsectionKind fromCode returns null for unknown`() {
        assertNull(DebugSubsectionKind.fromCode(0x00))
    }

    @Test
    fun `CodeViewBuilder pointer creates LF_POINTER`() {
        val r = CodeViewBuilder.pointer(CvBuiltinType.T_INT4.code)
        assertEquals(CvTypeKind.LF_POINTER.code, r.kind)
        assertEquals(8, r.data.size)
    }

    @Test
    fun `CodeViewBuilder pointer with custom kind and mode`() {
        val r = CodeViewBuilder.pointer(
            CvBuiltinType.T_INT4.code,
            CvPointerKind.PTR_NEAR32,
            CvPointerMode.LVALUE_REFERENCE,
            size = 4,
        )
        assertEquals(CvTypeKind.LF_POINTER.code, r.kind)
    }

    @Test
    fun `CodeViewBuilder modifier creates LF_MODIFIER`() {
        val r = CodeViewBuilder.modifier(CvBuiltinType.T_INT4.code, isConst = true, isVolatile = true)
        assertEquals(CvTypeKind.LF_MODIFIER.code, r.kind)
        assertEquals(8, r.data.size)
    }

    @Test
    fun `CodeViewBuilder argList creates LF_ARGLIST with correct data`() {
        val r = CodeViewBuilder.argList(listOf(0x74, 0x75, 0x41))
        assertEquals(CvTypeKind.LF_ARGLIST.code, r.kind)
        // 4 bytes count + 3*4 bytes args = 16
        assertEquals(16, r.data.size)
        val buf = ByteBuffer.wrap(r.data).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(3, buf.getInt()) // count
        assertEquals(0x74, buf.getInt()) // arg 0
        assertEquals(0x75, buf.getInt()) // arg 1
        assertEquals(0x41, buf.getInt()) // arg 2
    }

    @Test
    fun `CodeViewBuilder argList empty`() {
        val r = CodeViewBuilder.argList(emptyList())
        assertEquals(CvTypeKind.LF_ARGLIST.code, r.kind)
        assertEquals(4, r.data.size) // just count
    }

    @Test
    fun `CodeViewBuilder procedure creates LF_PROCEDURE`() {
        val r = CodeViewBuilder.procedure(
            returnType = CvBuiltinType.T_INT4.code,
            paramCount = 2,
            argListIndex = 0x1000,
        )
        assertEquals(CvTypeKind.LF_PROCEDURE.code, r.kind)
        assertEquals(12, r.data.size)
    }

    @Test
    fun `CodeViewBuilder procedure data layout`() {
        val r = CodeViewBuilder.procedure(
            returnType = 0x0074,
            callingConvention = 0,
            paramCount = 3,
            argListIndex = 0x1001,
        )
        val buf = ByteBuffer.wrap(r.data).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(0x0074, buf.getInt()) // return type
        assertEquals(0, buf.get().toInt()) // calling convention
        assertEquals(0, buf.get().toInt()) // func attr
        assertEquals(3, buf.getShort().toInt()) // param count
        assertEquals(0x1001, buf.getInt()) // arg list
    }

    @Test
    fun `CodeViewBuilder structure creates LF_STRUCTURE`() {
        val r = CodeViewBuilder.structure(name = "MyStruct", size = 24, memberCount = 3)
        assertEquals(CvTypeKind.LF_STRUCTURE.code, r.kind)
        assertTrue(r.data.size > 0)
    }

    @Test
    fun `CodeViewBuilder structure with isClass creates LF_CLASS`() {
        val r = CodeViewBuilder.structure(name = "MyClass", isClass = true)
        assertEquals(CvTypeKind.LF_CLASS.code, r.kind)
    }

    @Test
    fun `CodeViewBuilder structure name appears in data`() {
        val r = CodeViewBuilder.structure(name = "Vertex")
        val str = String(r.data, r.data.size - "Vertex".length - 1, "Vertex".length)
        assertEquals("Vertex", str)
    }

    @Test
    fun `CodeViewBuilder structure forward reference`() {
        val r = CodeViewBuilder.structure(name = "FwdRef", properties = CvTypeProperties.FORWARD_REF)
        assertEquals(CvTypeKind.LF_STRUCTURE.code, r.kind)
        val buf = ByteBuffer.wrap(r.data).order(ByteOrder.LITTLE_ENDIAN)
        buf.getShort() // memberCount
        val props = buf.getShort().toInt() and 0xFFFF
        assertTrue(props and CvTypeProperties.FORWARD_REF != 0)
    }

    @Test
    fun `CodeViewBuilder union creates LF_UNION`() {
        val r = CodeViewBuilder.union(name = "U", size = 4, memberCount = 2)
        assertEquals(CvTypeKind.LF_UNION.code, r.kind)
    }

    @Test
    fun `CodeViewBuilder enum creates LF_ENUM`() {
        val r = CodeViewBuilder.enum_("Colors", CvBuiltinType.T_INT4.code, memberCount = 5)
        assertEquals(CvTypeKind.LF_ENUM.code, r.kind)
    }

    @Test
    fun `CodeViewBuilder array creates LF_ARRAY`() {
        val r = CodeViewBuilder.array(CvBuiltinType.T_INT4.code, CvBuiltinType.T_UINT4.code, 100)
        assertEquals(CvTypeKind.LF_ARRAY.code, r.kind)
    }

    @Test
    fun `CodeViewBuilder bitfield creates LF_BITFIELD`() {
        val r = CodeViewBuilder.bitfield(CvBuiltinType.T_INT4.code, 3, 5)
        assertEquals(CvTypeKind.LF_BITFIELD.code, r.kind)
        assertEquals(6, r.data.size)
        val buf = ByteBuffer.wrap(r.data).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(CvBuiltinType.T_INT4.code, buf.getInt())
        assertEquals(3, buf.get().toInt()) // length
        assertEquals(5, buf.get().toInt()) // position
    }

    @Test
    fun `CodeViewBuilder gproc32 creates S_GPROC32`() {
        val r = CodeViewBuilder.gproc32("main", 0x1000, 0, 1, 100)
        assertEquals(CvSymbolKind.S_GPROC32.code, r.kind)
    }

    @Test
    fun `CodeViewBuilder gproc32 contains name`() {
        val r = CodeViewBuilder.gproc32("myFunc", 0x1000, 0, 1, 50)
        val str = String(r.data, r.data.size - "myFunc".length - 1, "myFunc".length)
        assertEquals("myFunc", str)
    }

    @Test
    fun `CodeViewBuilder lproc32 creates S_LPROC32`() {
        val r = CodeViewBuilder.lproc32("helper", 0x1001, 100, 1, 50)
        assertEquals(CvSymbolKind.S_LPROC32.code, r.kind)
    }

    @Test
    fun `CodeViewBuilder end creates S_END with no data`() {
        val r = CodeViewBuilder.end()
        assertEquals(CvSymbolKind.S_END.code, r.kind)
        assertEquals(0, r.data.size)
    }

    @Test
    fun `CodeViewBuilder gdata32 creates S_GDATA32`() {
        val r = CodeViewBuilder.gdata32("g_count", CvBuiltinType.T_INT4.code, 0, 3)
        assertEquals(CvSymbolKind.S_GDATA32.code, r.kind)
    }

    @Test
    fun `CodeViewBuilder ldata32 creates S_LDATA32`() {
        val r = CodeViewBuilder.ldata32("staticVar", CvBuiltinType.T_INT4.code, 0, 3)
        assertEquals(CvSymbolKind.S_LDATA32.code, r.kind)
    }

    @Test
    fun `CodeViewBuilder pub32 creates S_PUB32`() {
        val r = CodeViewBuilder.pub32("_start", 0, 1, CvPublicSymbolFlags.FUNCTION)
        assertEquals(CvSymbolKind.S_PUB32.code, r.kind)
    }

    @Test
    fun `CodeViewBuilder udt creates S_UDT`() {
        val r = CodeViewBuilder.udt("MyStruct", 0x1005)
        assertEquals(CvSymbolKind.S_UDT.code, r.kind)
    }

    @Test
    fun `CodeViewBuilder constant small value`() {
        val r = CodeViewBuilder.constant("SIZE", CvBuiltinType.T_INT4.code, 42)
        assertEquals(CvSymbolKind.S_CONSTANT.code, r.kind)
    }

    @Test
    fun `CodeViewBuilder constant large value`() {
        val r = CodeViewBuilder.constant("BIG", CvBuiltinType.T_UINT8.code, 0x100000000L)
        assertEquals(CvSymbolKind.S_CONSTANT.code, r.kind)
    }

    @Test
    fun `CodeViewBuilder compile3 creates S_COMPILE3`() {
        val r = CodeViewBuilder.compile3(
            CvSourceLanguage.KOTLIN, CvCpuType.X64_AMD64, "kgen 1.0",
            frontendVersion = intArrayOf(1, 0, 0, 0),
            backendVersion = intArrayOf(1, 0, 0, 0),
        )
        assertEquals(CvSymbolKind.S_COMPILE3.code, r.kind)
    }

    @Test
    fun `CodeViewBuilder regrel32 creates S_REGREL32`() {
        val r = CodeViewBuilder.regrel32("localVar", CvBuiltinType.T_INT4.code, -8, 334)
        assertEquals(CvSymbolKind.S_REGREL32.code, r.kind)
    }

    @Test
    fun `CodeViewBuilder objname creates S_OBJNAME`() {
        val r = CodeViewBuilder.objname("test.obj", signature = 123)
        assertEquals(CvSymbolKind.S_OBJNAME.code, r.kind)
    }

    @Test
    fun `DbiModuleInfo data class properties`() {
        val m = DbiModuleInfo(
            moduleName = "main.obj", objectFileName = "main.c",
            moduleSymbolStreamIndex = 5, symbolSize = 100,
            linesSize = 0, c13LinesSize = 50, sourceFileCount = 1,
            sectionContributionOffset = 0, sectionContributionSize = 1024,
            sectionIndex = 1,
        )
        assertEquals("main.obj", m.moduleName)
        assertEquals("main.c", m.objectFileName)
        assertEquals(5, m.moduleSymbolStreamIndex)
        assertEquals(100, m.symbolSize)
        assertEquals(50, m.c13LinesSize)
        assertEquals(1, m.sourceFileCount)
    }

    @Test
    fun `DbiSectionContribution data class properties`() {
        val sc = DbiSectionContribution(
            section = 1, offset = 0x1000, size = 0x200,
            characteristics = 0x60000020, moduleIndex = 0,
            dataCrc = 0x12345678, relocCrc = 0,
        )
        assertEquals(1, sc.section)
        assertEquals(0x1000, sc.offset)
        assertEquals(0x200, sc.size)
        assertEquals(0x60000020, sc.characteristics)
        assertEquals(0, sc.moduleIndex)
    }

    @Test
    fun `CvLineBlock data class properties`() {
        val block = CvLineBlock(fileIndex = 0, lines = listOf(
            CvLineEntry(0, 10, 0, true),
            CvLineEntry(5, 11, 0, false),
        ))
        assertEquals(0, block.fileIndex)
        assertEquals(2, block.lines.size)
        assertEquals(10, block.lines[0].lineStart)
        assertTrue(block.lines[0].isStatement)
        assertFalse(block.lines[1].isStatement)
    }

    @Test
    fun `CvLineEntry data class properties`() {
        val entry = CvLineEntry(offset = 42, lineStart = 100, deltaLineEnd = 2, isStatement = true)
        assertEquals(42, entry.offset)
        assertEquals(100, entry.lineStart)
        assertEquals(2, entry.deltaLineEnd)
        assertTrue(entry.isStatement)
    }

    @Test
    fun `CvFileChecksum equality`() {
        val a = CvFileChecksum(0, 16, 1, byteArrayOf(1, 2, 3))
        val b = CvFileChecksum(0, 16, 1, byteArrayOf(1, 2, 3))
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `CvFileChecksum inequality`() {
        val a = CvFileChecksum(0, 16, 1, byteArrayOf(1, 2, 3))
        val b = CvFileChecksum(0, 16, 1, byteArrayOf(1, 2, 4))
        assertFalse(a == b)
    }

    @Test
    fun `PdbFile data class properties`() {
        val pdb = buildMinimalPdb()
        assertNotNull(pdb.guid)
        assertNotNull(pdb.info)
        assertNotNull(pdb.msf)
        assertTrue(pdb.publicSymbols.isEmpty())
    }

    @Test
    fun `PDB with module symbols`() {
        val pdb = buildMinimalPdb {
            val mod = addModule("test.obj")
            mod.addSymbol(CodeViewBuilder.compile3(CvSourceLanguage.CPP, CvCpuType.X64_AMD64, "test"))
            mod.addSymbol(CodeViewBuilder.gproc32("func", 0x1000, 0, 1, 20))
            mod.addSymbol(CodeViewBuilder.end())
        }
        assertTrue(pdb.moduleSymbols.isNotEmpty())
    }

    @Test
    fun `PdbModuleBuilder addSourceFile`() {
        val writer = PdbWriter()
        writer.guid = UUID.randomUUID()
        val mod = writer.addModule("test.obj")
        mod.addSourceFile("C:\\src\\test.cpp")
        // Should not throw, and source file count should be tracked
        assertEquals(1, mod.sourceFileCount())
    }

    @Test
    fun `PdbModuleBuilder symbolDataSize empty`() {
        val mod = PdbModuleBuilder("test.obj", "test.obj")
        assertEquals(4, mod.symbolDataSize()) // just CV_SIGNATURE_C13
    }

    @Test
    fun `PdbModuleBuilder symbolDataSize with symbols`() {
        val mod = PdbModuleBuilder("test.obj", "test.obj")
        mod.addSymbol(CodeViewBuilder.end())
        assertTrue(mod.symbolDataSize() > 4)
    }

    @Test
    fun `PdbModuleBuilder lineDataSize empty`() {
        val mod = PdbModuleBuilder("test.obj", "test.obj")
        assertEquals(0, mod.lineDataSize())
    }

    @Test
    fun `PdbModuleBuilder lineDataSize with lines`() {
        val mod = PdbModuleBuilder("test.obj", "test.obj")
        mod.addLineInfo(CvLineBlock(0, listOf(CvLineEntry(0, 1, 0, true))))
        assertTrue(mod.lineDataSize() > 0)
    }

    @Test
    fun `PDB with line info`() {
        val pdb = buildMinimalPdb {
            val mod = addModule("test.obj")
            mod.addLineInfo(CvLineBlock(
                fileIndex = 0,
                lines = listOf(
                    CvLineEntry(0, 10, 0, true),
                    CvLineEntry(8, 11, 0, true),
                ),
            ))
        }
        assertTrue(pdb.modules.isNotEmpty())
    }

    @Test
    fun `PdbReader parseSymbolRecords with valid data`() {
        // Build a minimal symbol stream
        val sym = CodeViewBuilder.gdata32("x", CvBuiltinType.T_INT4.code, 0, 1)
        val buf = ByteBuffer.allocate(100).order(ByteOrder.LITTLE_ENDIAN)
        buf.putShort((sym.data.size + 2).toShort())
        buf.putShort(sym.kind.toShort())
        buf.put(sym.data)
        val data = buf.array().copyOf(buf.position())
        val records = PdbReader.parseSymbolRecords(data, 0, data.size)
        assertEquals(1, records.size)
        assertEquals(CvSymbolKind.S_GDATA32, records[0].symbolKind)
    }

    @Test
    fun `PdbReader parseSymbolRecords with empty data`() {
        val records = PdbReader.parseSymbolRecords(byteArrayOf(), 0, 0)
        assertTrue(records.isEmpty())
    }

    @Test
    fun `PdbReader parseSymbolRecords with multiple records`() {
        val sym1 = CodeViewBuilder.gdata32("a", CvBuiltinType.T_INT4.code, 0, 1)
        val sym2 = CodeViewBuilder.pub32("b", 0, 1)
        val buf = ByteBuffer.allocate(200).order(ByteOrder.LITTLE_ENDIAN)
        for (sym in listOf(sym1, sym2)) {
            val len = sym.data.size + 2
            buf.putShort(len.toShort())
            buf.putShort(sym.kind.toShort())
            buf.put(sym.data)
            // Align to 4
            val pad = (4 - (buf.position() % 4)) % 4
            for (i in 0 until pad) buf.put(0)
        }
        val data = buf.array().copyOf(buf.position())
        val records = PdbReader.parseSymbolRecords(data, 0, data.size)
        assertEquals(2, records.size)
    }

    @Test
    fun `MSF superblock fields`() {
        val sb = MsfSuperBlock(
            blockSize = 4096,
            freeBlockMapIndex = 1,
            blockCount = 100,
            directorySize = 200,
            directoryMapBlockIndex = 3,
        )
        assertEquals(4096, sb.blockSize)
        assertEquals(1, sb.freeBlockMapIndex)
        assertEquals(100, sb.blockCount)
        assertEquals(200, sb.directorySize)
        assertEquals(3, sb.directoryMapBlockIndex)
    }

    @Test
    fun `MSF superblock data class equality`() {
        val a = MsfSuperBlock(4096, 1, 100, 200, 3)
        val b = MsfSuperBlock(4096, 1, 100, 200, 3)
        assertEquals(a, b)
    }

    @Test
    fun `PDB MSF property is accessible`() {
        val pdb = buildMinimalPdb()
        assertNotNull(pdb.msf)
        assertTrue(pdb.msf.streamCount >= 5) // 0=old, 1=info, 2=TPI, 3=DBI, 4=IPI
    }

    @Test
    fun `PDB multiple types of different kinds`() {
        val pdb = buildMinimalPdb {
            addType(CodeViewBuilder.argList(emptyList()))
            addType(CodeViewBuilder.pointer(CvBuiltinType.T_INT4.code))
            addType(CodeViewBuilder.modifier(CvBuiltinType.T_INT4.code, isConst = true))
            addType(CodeViewBuilder.structure(name = "S"))
            addType(CodeViewBuilder.union(name = "U"))
            addType(CodeViewBuilder.enum_("E", CvBuiltinType.T_INT4.code))
            addType(CodeViewBuilder.array(CvBuiltinType.T_INT4.code, CvBuiltinType.T_UINT4.code, 10))
            addType(CodeViewBuilder.bitfield(CvBuiltinType.T_INT4.code, 1, 0))
        }
        assertEquals(8, pdb.types.size)
        assertEquals(CvTypeKind.LF_ARGLIST, pdb.types[0].typeKind)
        assertEquals(CvTypeKind.LF_POINTER, pdb.types[1].typeKind)
        assertEquals(CvTypeKind.LF_MODIFIER, pdb.types[2].typeKind)
        assertEquals(CvTypeKind.LF_STRUCTURE, pdb.types[3].typeKind)
        assertEquals(CvTypeKind.LF_UNION, pdb.types[4].typeKind)
        assertEquals(CvTypeKind.LF_ENUM, pdb.types[5].typeKind)
        assertEquals(CvTypeKind.LF_ARRAY, pdb.types[6].typeKind)
        assertEquals(CvTypeKind.LF_BITFIELD, pdb.types[7].typeKind)
    }

    @Test
    fun `PDB full pipeline with types, symbols, and module`() {
        val writer = PdbWriter()
        writer.guid = UUID.randomUUID()
        writer.age = 1
        writer.machine = 0x8664.toShort()

        val argList = writer.addType(CodeViewBuilder.argList(listOf(CvBuiltinType.T_INT4.code)))
        val procType = writer.addType(CodeViewBuilder.procedure(
            returnType = CvBuiltinType.T_INT4.code, paramCount = 1, argListIndex = argList
        ))

        writer.addGlobalSymbol(CodeViewBuilder.pub32("_main", 0, 1, CvPublicSymbolFlags.FUNCTION))

        val mod = writer.addModule("main.obj", "C:\\src\\main.c")
        mod.addSymbol(CodeViewBuilder.compile3(CvSourceLanguage.C, CvCpuType.X64_AMD64, "kgen"))
        mod.addSymbol(CodeViewBuilder.gproc32("main", procType, 0, 1, 42))
        mod.addSymbol(CodeViewBuilder.regrel32("argc", CvBuiltinType.T_INT4.code, 8, 334))
        mod.addSymbol(CodeViewBuilder.end())

        val bytes = writer.build()
        val pdb = PdbReader.read(bytes)

        assertEquals(2, pdb.types.size)
        assertTrue(pdb.globalSymbols.isNotEmpty())
        assertEquals(1, pdb.modules.size)
        assertEquals("main.obj", pdb.modules[0].moduleName)
    }

    @Test
    fun `TpiStreamHeader properties`() {
        val h = TpiStreamHeader(
            version = TpiStreamHeader.VERSION_V80,
            headerSize = 56, typeIndexBegin = 0x1000, typeIndexEnd = 0x1005,
            typeRecordBytes = 100, hashStreamIndex = -1, hashAuxStreamIndex = -1,
            hashKeySize = 4, numHashBuckets = 0x3FFFF,
            hashValueBufferOffset = 0, hashValueBufferLength = 0,
            indexOffsetBufferOffset = 0, indexOffsetBufferLength = 0,
            hashAdjBufferOffset = 0, hashAdjBufferLength = 0,
        )
        assertEquals(0x1000, h.typeIndexBegin)
        assertEquals(0x1005, h.typeIndexEnd)
        assertEquals(100, h.typeRecordBytes)
    }

    @Test
    fun `DbiStreamHeader properties`() {
        val h = DbiStreamHeader(
            versionSignature = -1, versionHeader = DbiStreamHeader.VERSION_V70,
            age = 1, globalStreamIndex = 5, buildNumber = 0,
            publicStreamIndex = 6, pdbDllVersion = 0,
            symRecordStreamIndex = 7, pdbDllRbld = 0,
            modInfoSize = 100, sectionContributionSize = 50,
            sectionMapSize = 0, sourceInfoSize = 0,
            typeServerMapSize = 0, mfcTypeServerIndex = 0,
            optionalDbgHeaderSize = 0, ecSubstreamSize = 0,
            flags = 0, machine = 0x8664.toShort(),
        )
        assertEquals(DbiStreamHeader.VERSION_V70, h.versionHeader)
        assertEquals(1, h.age)
        assertEquals(5, h.globalStreamIndex)
        assertEquals(0x8664.toShort(), h.machine)
    }

    @Test
    fun `PDB section contributions from DBI`() {
        // Minimal PDB has empty section contributions (just version header)
        val pdb = buildMinimalPdb()
        assertTrue(pdb.sectionContributions.isEmpty())
    }

    @Test
    fun `PdbInfoStream properties`() {
        val guid = UUID.randomUUID()
        val info = PdbInfoStream(
            version = PdbInfoStream.PdbStreamVersion.VC70,
            signature = 12345,
            age = 3,
            guid = guid,
            namedStreams = mapOf("/names" to 5),
        )
        assertEquals(PdbInfoStream.PdbStreamVersion.VC70, info.version)
        assertEquals(12345, info.signature)
        assertEquals(3, info.age)
        assertEquals(guid, info.guid)
        assertEquals(mapOf("/names" to 5), info.namedStreams)
    }
}
