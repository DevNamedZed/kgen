package org.kgen.binary.elf

import org.kgen.binary.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ElfLinkerComprehensiveTest {

    private fun le(bytes: ByteArray) = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    private fun readU16(buf: ByteBuffer, off: Int): Int = buf.getShort(off).toInt() and 0xFFFF
    private fun readU32(buf: ByteBuffer, off: Int): Int = buf.getInt(off)
    private fun readI32(buf: ByteBuffer, off: Int): Int = buf.getInt(off)
    private fun readU64(buf: ByteBuffer, off: Int): Long = buf.getLong(off)

    private fun elfMagicValid(binary: ByteArray) {
        assertEquals(0x7f, binary[0].toInt() and 0xFF)
        assertEquals('E'.code, binary[1].toInt() and 0xFF)
        assertEquals('L'.code, binary[2].toInt() and 0xFF)
        assertEquals('F'.code, binary[3].toInt() and 0xFF)
    }

    private fun x86Obj(
        textCode: ByteArray,
        symbols: List<Symbol>,
        relocations: List<Relocation> = emptyList(),
        sections: List<Section>? = null,
        imports: List<ImportEntry> = emptyList(),
    ) = ObjectFile(
        format = ObjectFormat.ELF,
        arch = Architecture(ArchType.X86_64),
        sections = sections ?: listOf(Section(".text", SectionKind.TEXT, textCode, align = 16)),
        symbols = symbols,
        relocations = relocations,
        imports = imports,
    )

    private fun retCode() = byteArrayOf(0xC3.toByte())
    private fun nopCode(n: Int) = ByteArray(n) { 0x90.toByte() }
    private fun movEaxImm(v: Int) = byteArrayOf(
        0xB8.toByte(),
        (v and 0xFF).toByte(),
        ((v shr 8) and 0xFF).toByte(),
        ((v shr 16) and 0xFF).toByte(),
        ((v shr 24) and 0xFF).toByte(),
    )

    private fun callPlaceholder() = byteArrayOf(
        0xE8.toByte(), 0x00, 0x00, 0x00, 0x00,
    )

    private fun leaRipPlaceholder() = byteArrayOf(
        0x48, 0x8D.toByte(), 0x3D, 0x00, 0x00, 0x00, 0x00,
    )

    private fun movRipPlaceholder() = byteArrayOf(
        0x8B.toByte(), 0x05, 0x00, 0x00, 0x00, 0x00,
    )

    @Nested
    inner class `static linking single object` {

        @Test
        fun `produces EXEC type binary`() {
            val code = movEaxImm(60) + byteArrayOf(0x0F, 0x05)
            val obj = x86Obj(code, listOf(
                Symbol("_start", value = 0, size = code.size.toLong(), section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = ElfStaticLinker().link(listOf(obj))
            val buf = le(binary)
            elfMagicValid(binary)
            assertEquals(ElfObjectType.EXEC.code, readU16(buf, 16))
        }

        @Test
        fun `has valid x86-64 machine type`() {
            val obj = x86Obj(retCode(), listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = ElfStaticLinker().link(listOf(obj))
            assertEquals(ElfMachine.X86_64.code, readU16(le(binary), 18))
        }

        @Test
        fun `entry point in valid address range`() {
            val obj = x86Obj(retCode(), listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = ElfStaticLinker().link(listOf(obj))
            val entry = readU64(le(binary), 24)
            assertTrue(entry >= 0x400000, "Entry >= 0x400000")
            assertTrue(entry < 0x500000, "Entry in reasonable range")
        }

        @Test
        fun `code bytes appear at entry offset`() {
            val code = movEaxImm(42) + retCode()
            val obj = x86Obj(code, listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = ElfStaticLinker().link(listOf(obj))
            val entry = readU64(le(binary), 24)
            val off = (entry - 0x400000).toInt()
            assertEquals(0xB8.toByte(), binary[off])
            assertEquals(42.toByte(), binary[off + 1])
        }

        @Test
        fun `program headers start after ELF header`() {
            val obj = x86Obj(retCode(), listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = ElfStaticLinker().link(listOf(obj))
            val phoff = readU64(le(binary), 32)
            assertEquals(Elf.EHDR64_SIZE.toLong(), phoff)
        }

        @Test
        fun `has three program headers`() {
            val obj = x86Obj(retCode(), listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = ElfStaticLinker().link(listOf(obj))
            assertEquals(3, readU16(le(binary), 56))
        }

        @Test
        fun `first segment is LOAD RX`() {
            val obj = x86Obj(retCode(), listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = ElfStaticLinker().link(listOf(obj))
            val buf = le(binary)
            val phoff = readU64(buf, 32).toInt()
            assertEquals(ElfSegmentType.LOAD.code, readU32(buf, phoff))
            val flags = readU32(buf, phoff + 4)
            assertTrue(flags and ElfSegmentFlags.R != 0)
            assertTrue(flags and ElfSegmentFlags.X != 0)
        }

        @Test
        fun `has GNU_STACK segment`() {
            val obj = x86Obj(retCode(), listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = ElfStaticLinker().link(listOf(obj))
            val buf = le(binary)
            val phoff = readU64(buf, 32).toInt()
            val phnum = readU16(buf, 56)
            var hasGnuStack = false
            for (i in 0 until phnum) {
                if (readU32(buf, phoff + i * Elf.PHDR64_SIZE) == ElfSegmentType.GNU_STACK.code)
                    hasGnuStack = true
            }
            assertTrue(hasGnuStack)
        }

        @Test
        fun `no dynamic linking artifacts in static binary`() {
            val obj = x86Obj(retCode(), listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = ElfStaticLinker().link(listOf(obj))
            val buf = le(binary)
            val phoff = readU64(buf, 32).toInt()
            val phnum = readU16(buf, 56)
            for (i in 0 until phnum) {
                val type = readU32(buf, phoff + i * Elf.PHDR64_SIZE)
                assertNotEquals(ElfSegmentType.INTERP.code, type)
                assertNotEquals(ElfSegmentType.DYNAMIC.code, type)
            }
        }

        @Test
        fun `no ld-linux reference in static binary`() {
            val obj = x86Obj(retCode(), listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = ElfStaticLinker().link(listOf(obj))
            assertFalse(String(binary, Charsets.ISO_8859_1).contains("ld-linux"))
        }

        @Test
        fun `version field is 1`() {
            val obj = x86Obj(retCode(), listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = ElfStaticLinker().link(listOf(obj))
            assertEquals(Elf.VERSION, readU32(le(binary), 20))
        }

        @Test
        fun `ELF class is 64-bit`() {
            val obj = x86Obj(retCode(), listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = ElfStaticLinker().link(listOf(obj))
            assertEquals(ElfClass.ELF64.code, binary[4].toInt())
        }

        @Test
        fun `ELF data is little endian`() {
            val obj = x86Obj(retCode(), listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = ElfStaticLinker().link(listOf(obj))
            assertEquals(ElfData.LSB.code, binary[5].toInt())
        }

        @Test
        fun `binary size is reasonable`() {
            val obj = x86Obj(retCode(), listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = ElfStaticLinker().link(listOf(obj))
            assertTrue(binary.size > 100)
            assertTrue(binary.size < 50000)
        }
    }

    @Nested
    inner class `static linking multiple objects` {

        @Test
        fun `resolves cross-object call`() {
            val obj1 = x86Obj(
                callPlaceholder() + retCode(),
                listOf(
                    Symbol("_start", value = 0, section = ".text",
                        binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                    Symbol("helper", value = 0, section = null,
                        binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
                ),
                listOf(Relocation(offset = 1, symbol = "helper", type = RelocationType.X86_64.PC32,
                    addend = -4, section = ".text")),
            )
            val obj2 = x86Obj(
                movEaxImm(42) + retCode(),
                listOf(Symbol("helper", value = 0, size = 6, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)),
            )
            val binary = ElfStaticLinker().link(listOf(obj1, obj2))
            val buf = le(binary)
            val entry = readU64(buf, 24)
            val off = (entry - 0x400000).toInt()
            assertNotEquals(0, readI32(buf, off + 1), "Call displacement patched")
        }

        @Test
        fun `call displacement points to correct target`() {
            val mainCode = callPlaceholder() + retCode()
            val helperCode = movEaxImm(7) + retCode()
            val obj1 = x86Obj(mainCode, listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("helper", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ), listOf(Relocation(1, "helper", RelocationType.X86_64.PC32, -4, ".text")))
            val obj2 = x86Obj(helperCode, listOf(
                Symbol("helper", value = 0, size = helperCode.size.toLong(), section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = ElfStaticLinker().link(listOf(obj1, obj2))
            val buf = le(binary)
            val entry = readU64(buf, 24)
            val off = (entry - 0x400000).toInt()
            val disp = readI32(buf, off + 1)
            val target = off + 5 + disp
            assertEquals(0xB8.toByte(), binary[target], "Target should be mov eax, imm")
            assertEquals(7.toByte(), binary[target + 1])
        }

        @Test
        fun `links three objects with transitive calls`() {
            val code1 = callPlaceholder() + retCode()
            val code2 = callPlaceholder() + retCode()
            val code3 = movEaxImm(99) + retCode()
            val obj1 = x86Obj(code1, listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("foo", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ), listOf(Relocation(1, "foo", RelocationType.X86_64.PC32, -4, ".text")))
            val obj2 = x86Obj(code2, listOf(
                Symbol("foo", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("bar", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ), listOf(Relocation(1, "bar", RelocationType.X86_64.PC32, -4, ".text")))
            val obj3 = x86Obj(code3, listOf(
                Symbol("bar", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = ElfStaticLinker().link(listOf(obj1, obj2, obj3))
            val buf = le(binary)
            val entry = readU64(buf, 24)
            val startOff = (entry - 0x400000).toInt()
            val fooDisp = readI32(buf, startOff + 1)
            assertNotEquals(0, fooDisp)
            val fooOff = startOff + 5 + fooDisp
            val barDisp = readI32(buf, fooOff + 1)
            assertNotEquals(0, barDisp)
            val barOff = fooOff + 5 + barDisp
            assertEquals(0xB8.toByte(), binary[barOff])
        }

        @Test
        fun `section alignment honored between objects`() {
            val code1 = byteArrayOf(0x90.toByte(), 0x90.toByte(), 0x90.toByte())
            val code2 = retCode()
            val obj1 = x86Obj(code1, listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val obj2 = x86Obj(code2, listOf(
                Symbol("func2", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = ElfStaticLinker().link(listOf(obj1, obj2))
            assertTrue(binary.size > 0)
        }

        @Test
        fun `four objects linked correctly`() {
            val objs = (0 until 4).map { i ->
                val name = if (i == 0) "_start" else "func$i"
                val code = if (i < 3) callPlaceholder() + retCode() else movEaxImm(i) + retCode()
                val nextName = if (i < 3) (if (i + 1 < 3) "func${i + 1}" else "func3") else null
                val syms = mutableListOf(Symbol(name, value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION))
                if (nextName != null) syms.add(Symbol(nextName, value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED))
                val rels = if (nextName != null) listOf(Relocation(1, nextName, RelocationType.X86_64.PC32, -4, ".text")) else emptyList()
                x86Obj(code, syms, rels)
            }
            val binary = ElfStaticLinker().link(objs)
            elfMagicValid(binary)
        }
    }

    @Nested
    inner class `static relocation types` {

        @Test
        fun `R_X86_64_PC32 relocation applied correctly`() {
            val code = leaRipPlaceholder() + retCode()
            val rodata = "test\u0000".toByteArray()
            val obj = x86Obj(code, listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("msg", value = 0, section = ".rodata",
                    binding = SymbolBinding.LOCAL, kind = SymbolKind.DATA),
            ), listOf(Relocation(3, "msg", RelocationType.X86_64.PC32, -4, ".text")),
                sections = listOf(
                    Section(".text", SectionKind.TEXT, code, align = 16),
                    Section(".rodata", SectionKind.RODATA, rodata, align = 1),
                ))
            val binary = ElfStaticLinker().link(listOf(obj))
            val buf = le(binary)
            val entry = readU64(buf, 24)
            val off = (entry - 0x400000).toInt()
            assertNotEquals(0, readI32(buf, off + 3))
        }

        @Test
        fun `R_X86_64_PLT32 treated as PC32 in static link`() {
            val code = callPlaceholder() + retCode()
            val helperCode = retCode()
            val obj1 = x86Obj(code, listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("helper", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ), listOf(Relocation(1, "helper", RelocationType.X86_64.PLT32, -4, ".text")))
            val obj2 = x86Obj(helperCode, listOf(
                Symbol("helper", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = ElfStaticLinker().link(listOf(obj1, obj2))
            val buf = le(binary)
            val entry = readU64(buf, 24)
            val off = (entry - 0x400000).toInt()
            assertNotEquals(0, readI32(buf, off + 1))
        }

        @Test
        fun `R_X86_64_64 absolute relocation in data`() {
            val code = retCode()
            val dataBytes = ByteArray(8)
            val obj = x86Obj(code, listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("ptr", value = 0, section = ".data",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.DATA),
            ), listOf(Relocation(0, "_start", RelocationType.X86_64.R_64, 0, ".data")),
                sections = listOf(
                    Section(".text", SectionKind.TEXT, code, align = 16),
                    Section(".data", SectionKind.DATA, dataBytes, align = 8),
                ))
            val binary = ElfStaticLinker().link(listOf(obj))
            assertTrue(binary.size > 0)
        }

        @Test
        fun `R_X86_64_32S relocation applied`() {
            val code = byteArrayOf(
                0xC7.toByte(), 0x05,
                0x00, 0x00, 0x00, 0x00,
                0x2A, 0x00, 0x00, 0x00,
            )
            val dataBytes = ByteArray(4)
            val obj = x86Obj(code, listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("var", value = 0, section = ".data",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.DATA),
            ), listOf(Relocation(2, "var", RelocationType.X86_64.PC32, -4, ".text")),
                sections = listOf(
                    Section(".text", SectionKind.TEXT, code, align = 16),
                    Section(".data", SectionKind.DATA, dataBytes, align = 4),
                ))
            val binary = ElfStaticLinker().link(listOf(obj))
            val buf = le(binary)
            val entry = readU64(buf, 24)
            val off = (entry - 0x400000).toInt()
            assertNotEquals(0, readI32(buf, off + 2))
        }
    }

    @Nested
    inner class `static section handling` {

        @Test
        fun `rodata section included in binary`() {
            val code = retCode()
            val rodata = "Hello World\u0000".toByteArray()
            val obj = x86Obj(code, listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ), sections = listOf(
                Section(".text", SectionKind.TEXT, code, align = 16),
                Section(".rodata", SectionKind.RODATA, rodata, align = 1),
            ))
            val binary = ElfStaticLinker().link(listOf(obj))
            assertTrue(String(binary, Charsets.US_ASCII).contains("Hello World"))
        }

        @Test
        fun `data section creates RW segment`() {
            val code = retCode()
            val data = byteArrayOf(1, 2, 3, 4)
            val obj = x86Obj(code, listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ), sections = listOf(
                Section(".text", SectionKind.TEXT, code, align = 16),
                Section(".data", SectionKind.DATA, data, align = 4),
            ))
            val binary = ElfStaticLinker().link(listOf(obj))
            val buf = le(binary)
            val phoff = readU64(buf, 32).toInt()
            val rwPhdr = phoff + Elf.PHDR64_SIZE
            assertEquals(ElfSegmentType.LOAD.code, readU32(buf, rwPhdr))
            val flags = readU32(buf, rwPhdr + 4)
            assertTrue(flags and ElfSegmentFlags.W != 0)
        }

        @Test
        fun `text-only produces no RW LOAD with data`() {
            val code = movEaxImm(0) + retCode()
            val obj = x86Obj(code, listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = ElfStaticLinker().link(listOf(obj))
            assertTrue(binary.size > 0)
        }

        @Test
        fun `multiple rodata sections merged from different objects`() {
            val obj1 = x86Obj(retCode(), listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ), sections = listOf(
                Section(".text", SectionKind.TEXT, retCode(), align = 16),
                Section(".rodata", SectionKind.RODATA, "AAA\u0000".toByteArray(), align = 1),
            ))
            val obj2 = x86Obj(retCode(), listOf(
                Symbol("func2", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ), sections = listOf(
                Section(".text", SectionKind.TEXT, retCode(), align = 16),
                Section(".rodata", SectionKind.RODATA, "BBB\u0000".toByteArray(), align = 1),
            ))
            val binary = ElfStaticLinker().link(listOf(obj1, obj2))
            val str = String(binary, Charsets.US_ASCII)
            assertTrue(str.contains("AAA"))
            assertTrue(str.contains("BBB"))
        }

        @Test
        fun `multiple data sections merged from different objects`() {
            val obj1 = x86Obj(retCode(), listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ), sections = listOf(
                Section(".text", SectionKind.TEXT, retCode(), align = 16),
                Section(".data", SectionKind.DATA, byteArrayOf(0x11), align = 1),
            ))
            val obj2 = x86Obj(retCode(), listOf(
                Symbol("func2", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ), sections = listOf(
                Section(".text", SectionKind.TEXT, retCode(), align = 16),
                Section(".data", SectionKind.DATA, byteArrayOf(0x22), align = 1),
            ))
            val binary = ElfStaticLinker().link(listOf(obj1, obj2))
            assertTrue(binary.size > 0)
        }

        @Test
        fun `large text section handled`() {
            val code = nopCode(4096) + retCode()
            val obj = x86Obj(code, listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = ElfStaticLinker().link(listOf(obj))
            assertTrue(binary.size > 4096)
        }
    }

    @Nested
    inner class `static error handling` {

        @Test
        fun `rejects undefined symbol`() {
            val obj = x86Obj(callPlaceholder() + retCode(), listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("missing", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ), listOf(Relocation(1, "missing", RelocationType.X86_64.PC32, -4, ".text")))
            val ex = assertThrows(IllegalStateException::class.java) {
                ElfStaticLinker().link(listOf(obj))
            }
            assertTrue(ex.message!!.contains("missing"))
        }

        @Test
        fun `rejects multiple undefined symbols with all names`() {
            val code = callPlaceholder() + callPlaceholder() + retCode()
            val obj = x86Obj(code, listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("alpha", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
                Symbol("beta", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ), listOf(
                Relocation(1, "alpha", RelocationType.X86_64.PC32, -4, ".text"),
                Relocation(6, "beta", RelocationType.X86_64.PC32, -4, ".text"),
            ))
            val ex = assertThrows(IllegalStateException::class.java) {
                ElfStaticLinker().link(listOf(obj))
            }
            assertTrue(ex.message!!.contains("alpha"))
            assertTrue(ex.message!!.contains("beta"))
        }

        @Test
        fun `rejects empty object list`() {
            assertThrows(IllegalArgumentException::class.java) {
                ElfStaticLinker().link(emptyList())
            }
        }

        @Test
        fun `throws on missing entry symbol`() {
            val obj = x86Obj(retCode(), listOf(
                Symbol("random_func", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            assertThrows(IllegalStateException::class.java) {
                ElfStaticLinker().link(listOf(obj))
            }
        }
    }

    @Nested
    inner class `dynamic linking` {

        @Test
        fun `produces EXEC with dynamic linking infrastructure`() {
            val code = callPlaceholder() + retCode()
            val obj = x86Obj(code, listOf(
                Symbol("main", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("puts", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ), listOf(Relocation(1, "puts", RelocationType.X86_64.PLT32, -4, ".text")))
            val binary = ElfLinker().link(listOf(obj))
            val buf = le(binary)
            assertEquals(ElfObjectType.EXEC.code, readU16(buf, 16))
        }

        @Test
        fun `has INTERP segment`() {
            val obj = x86Obj(callPlaceholder() + retCode(), listOf(
                Symbol("main", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("puts", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ), listOf(Relocation(1, "puts", RelocationType.X86_64.PLT32, -4, ".text")))
            val binary = ElfLinker().link(listOf(obj))
            val buf = le(binary)
            val phoff = readU64(buf, 32).toInt()
            val phnum = readU16(buf, 56)
            var hasInterp = false
            for (i in 0 until phnum) {
                if (readU32(buf, phoff + i * Elf.PHDR64_SIZE) == ElfSegmentType.INTERP.code)
                    hasInterp = true
            }
            assertTrue(hasInterp)
        }

        @Test
        fun `has DYNAMIC segment`() {
            val obj = x86Obj(callPlaceholder() + retCode(), listOf(
                Symbol("main", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("puts", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ), listOf(Relocation(1, "puts", RelocationType.X86_64.PLT32, -4, ".text")))
            val binary = ElfLinker().link(listOf(obj))
            val buf = le(binary)
            val phoff = readU64(buf, 32).toInt()
            val phnum = readU16(buf, 56)
            var hasDynamic = false
            for (i in 0 until phnum) {
                if (readU32(buf, phoff + i * Elf.PHDR64_SIZE) == ElfSegmentType.DYNAMIC.code)
                    hasDynamic = true
            }
            assertTrue(hasDynamic)
        }

        @Test
        fun `has six program headers`() {
            val obj = x86Obj(callPlaceholder() + retCode(), listOf(
                Symbol("main", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("puts", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ), listOf(Relocation(1, "puts", RelocationType.X86_64.PLT32, -4, ".text")))
            val binary = ElfLinker().link(listOf(obj))
            assertEquals(6, readU16(le(binary), 56))
        }

        @Test
        fun `contains interpreter path`() {
            val obj = x86Obj(callPlaceholder() + retCode(), listOf(
                Symbol("main", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("puts", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ), listOf(Relocation(1, "puts", RelocationType.X86_64.PLT32, -4, ".text")))
            val binary = ElfLinker().link(listOf(obj))
            assertTrue(String(binary, Charsets.ISO_8859_1).contains("ld-linux"))
        }

        @Test
        fun `contains imported symbol name`() {
            val obj = x86Obj(callPlaceholder() + retCode(), listOf(
                Symbol("main", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("printf", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ), listOf(Relocation(1, "printf", RelocationType.X86_64.PLT32, -4, ".text")))
            val binary = ElfLinker().link(listOf(obj))
            assertTrue(String(binary, Charsets.ISO_8859_1).contains("printf"))
        }

        @Test
        fun `contains shared library name`() {
            val obj = x86Obj(callPlaceholder() + retCode(), listOf(
                Symbol("main", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("puts", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ), listOf(Relocation(1, "puts", RelocationType.X86_64.PLT32, -4, ".text")))
            val binary = ElfLinker().link(listOf(obj))
            assertTrue(String(binary, Charsets.ISO_8859_1).contains("libc.so.6"))
        }

        @Test
        fun `PLT call displacement is patched`() {
            val code = callPlaceholder() + byteArrayOf(0x31, 0xC0.toByte()) + retCode()
            val obj = x86Obj(code, listOf(
                Symbol("main", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("puts", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ), listOf(Relocation(1, "puts", RelocationType.X86_64.PLT32, -4, ".text")))
            val binary = ElfLinker().link(listOf(obj))
            val buf = le(binary)
            val entry = readU64(buf, 24)
            val off = (entry - 0x400000).toInt()
            assertNotEquals(0, readI32(buf, off + 1))
        }

        @Test
        fun `multiple dynamic imports`() {
            val code = callPlaceholder() + callPlaceholder() + retCode()
            val obj = x86Obj(code, listOf(
                Symbol("main", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("puts", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
                Symbol("printf", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ), listOf(
                Relocation(1, "puts", RelocationType.X86_64.PLT32, -4, ".text"),
                Relocation(6, "printf", RelocationType.X86_64.PLT32, -4, ".text"),
            ))
            val binary = ElfLinker().link(listOf(obj))
            val str = String(binary, Charsets.ISO_8859_1)
            assertTrue(str.contains("puts"))
            assertTrue(str.contains("printf"))
        }

        @Test
        fun `dynamic with rodata`() {
            val code = leaRipPlaceholder() + callPlaceholder() + retCode()
            val rodata = "Hello\u0000".toByteArray()
            val obj = x86Obj(code, listOf(
                Symbol("main", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("msg", value = 0, section = ".rodata",
                    binding = SymbolBinding.LOCAL, kind = SymbolKind.DATA),
                Symbol("puts", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ), listOf(
                Relocation(3, "msg", RelocationType.X86_64.PC32, -4, ".text"),
                Relocation(8, "puts", RelocationType.X86_64.PLT32, -4, ".text"),
            ), sections = listOf(
                Section(".text", SectionKind.TEXT, code, align = 16),
                Section(".rodata", SectionKind.RODATA, rodata, align = 1),
            ))
            val binary = ElfLinker().link(listOf(obj))
            assertTrue(String(binary, Charsets.US_ASCII).contains("Hello"))
        }

        @Test
        fun `dynamic linker multiple objects with mixed defined and undefined`() {
            val obj1 = x86Obj(callPlaceholder() + callPlaceholder() + retCode(), listOf(
                Symbol("main", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("helper", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
                Symbol("puts", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ), listOf(
                Relocation(1, "helper", RelocationType.X86_64.PC32, -4, ".text"),
                Relocation(6, "puts", RelocationType.X86_64.PLT32, -4, ".text"),
            ))
            val obj2 = x86Obj(movEaxImm(1) + retCode(), listOf(
                Symbol("helper", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = ElfLinker().link(listOf(obj1, obj2))
            elfMagicValid(binary)
            assertTrue(String(binary, Charsets.ISO_8859_1).contains("puts"))
        }

        @Test
        fun `custom shared libs list`() {
            val obj = x86Obj(callPlaceholder() + retCode(), listOf(
                Symbol("main", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("foo", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ), listOf(Relocation(1, "foo", RelocationType.X86_64.PLT32, -4, ".text")))
            val binary = ElfLinker(sharedLibs = listOf("libfoo.so.1")).link(listOf(obj))
            assertTrue(String(binary, Charsets.ISO_8859_1).contains("libfoo.so.1"))
        }

        @Test
        fun `no shared libs means no NEEDED entries but still has infrastructure`() {
            val obj = x86Obj(callPlaceholder() + retCode(), listOf(
                Symbol("main", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("ext", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ), listOf(Relocation(1, "ext", RelocationType.X86_64.PLT32, -4, ".text")))
            val binary = ElfLinker(sharedLibs = emptyList()).link(listOf(obj))
            elfMagicValid(binary)
        }

        @Test
        fun `entry point valid for main-based program`() {
            val code = callPlaceholder() + retCode()
            val obj = x86Obj(code, listOf(
                Symbol("main", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("puts", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ), listOf(Relocation(1, "puts", RelocationType.X86_64.PLT32, -4, ".text")))
            val binary = ElfLinker().link(listOf(obj))
            val entry = readU64(le(binary), 24)
            assertTrue(entry > 0x400000)
        }

        @Test
        fun `RW LOAD segment present`() {
            val obj = x86Obj(callPlaceholder() + retCode(), listOf(
                Symbol("main", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("puts", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ), listOf(Relocation(1, "puts", RelocationType.X86_64.PLT32, -4, ".text")))
            val binary = ElfLinker().link(listOf(obj))
            val buf = le(binary)
            val phoff = readU64(buf, 32).toInt()
            val phnum = readU16(buf, 56)
            var hasRw = false
            for (i in 0 until phnum) {
                val type = readU32(buf, phoff + i * Elf.PHDR64_SIZE)
                val flags = readU32(buf, phoff + i * Elf.PHDR64_SIZE + 4)
                if (type == ElfSegmentType.LOAD.code && flags and ElfSegmentFlags.W != 0)
                    hasRw = true
            }
            assertTrue(hasRw)
        }
    }

    @Nested
    inner class `shared library linking` {

        @Test
        fun `produces DYN type`() {
            val obj = x86Obj(retCode(), listOf(
                Symbol("myfunc", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = ElfSharedLinker().link(listOf(obj))
            assertEquals(ElfObjectType.DYN.code, readU16(le(binary), 16))
        }

        @Test
        fun `entry point is zero`() {
            val obj = x86Obj(retCode(), listOf(
                Symbol("myfunc", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = ElfSharedLinker().link(listOf(obj))
            assertEquals(0L, readU64(le(binary), 24))
        }

        @Test
        fun `no INTERP segment`() {
            val obj = x86Obj(retCode(), listOf(
                Symbol("myfunc", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = ElfSharedLinker().link(listOf(obj))
            val buf = le(binary)
            val phoff = readU64(buf, 32).toInt()
            val phnum = readU16(buf, 56)
            for (i in 0 until phnum) {
                assertNotEquals(ElfSegmentType.INTERP.code, readU32(buf, phoff + i * Elf.PHDR64_SIZE))
            }
        }

        @Test
        fun `has DYNAMIC segment`() {
            val obj = x86Obj(retCode(), listOf(
                Symbol("myfunc", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = ElfSharedLinker().link(listOf(obj))
            val buf = le(binary)
            val phoff = readU64(buf, 32).toInt()
            val phnum = readU16(buf, 56)
            var hasDynamic = false
            for (i in 0 until phnum) {
                if (readU32(buf, phoff + i * Elf.PHDR64_SIZE) == ElfSegmentType.DYNAMIC.code)
                    hasDynamic = true
            }
            assertTrue(hasDynamic)
        }

        @Test
        fun `four program headers`() {
            val obj = x86Obj(retCode(), listOf(
                Symbol("myfunc", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = ElfSharedLinker().link(listOf(obj))
            assertEquals(4, readU16(le(binary), 56))
        }

        @Test
        fun `exports function name in dynstr`() {
            val obj = x86Obj(retCode(), listOf(
                Symbol("my_exported_func", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = ElfSharedLinker().link(listOf(obj))
            assertTrue(String(binary, Charsets.ISO_8859_1).contains("my_exported_func"))
        }

        @Test
        fun `exports multiple symbols`() {
            val code = byteArrayOf(0x8D.toByte(), 0x04, 0x37, 0xC3.toByte(), 0x89.toByte(), 0xF8.toByte(), 0xC3.toByte())
            val obj = x86Obj(code, listOf(
                Symbol("add", value = 0, size = 4, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("get", value = 4, size = 3, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = ElfSharedLinker().link(listOf(obj))
            val str = String(binary, Charsets.ISO_8859_1)
            assertTrue(str.contains("add"))
            assertTrue(str.contains("get"))
        }

        @Test
        fun `soname included when specified`() {
            val obj = x86Obj(retCode(), listOf(
                Symbol("func", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = ElfSharedLinker(soname = "libtest.so.2").link(listOf(obj))
            assertTrue(String(binary, Charsets.ISO_8859_1).contains("libtest.so.2"))
        }

        @Test
        fun `no soname by default`() {
            val obj = x86Obj(retCode(), listOf(
                Symbol("func", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = ElfSharedLinker().link(listOf(obj))
            assertTrue(binary.size > 0)
        }

        @Test
        fun `no ld-linux reference`() {
            val obj = x86Obj(retCode(), listOf(
                Symbol("func", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = ElfSharedLinker().link(listOf(obj))
            assertFalse(String(binary, Charsets.ISO_8859_1).contains("ld-linux"))
        }

        @Test
        fun `multiple objects into shared library`() {
            val obj1 = x86Obj(byteArrayOf(0xC3.toByte()), listOf(
                Symbol("func_a", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val obj2 = x86Obj(byteArrayOf(0xC3.toByte()), listOf(
                Symbol("func_b", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val obj3 = x86Obj(byteArrayOf(0xC3.toByte()), listOf(
                Symbol("func_c", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = ElfSharedLinker(soname = "libmulti.so").link(listOf(obj1, obj2, obj3))
            val str = String(binary, Charsets.ISO_8859_1)
            assertTrue(str.contains("func_a"))
            assertTrue(str.contains("func_b"))
            assertTrue(str.contains("func_c"))
        }

        @Test
        fun `internal cross-object calls resolved`() {
            val wrapperCode = callPlaceholder() + retCode()
            val obj1 = x86Obj(wrapperCode, listOf(
                Symbol("wrapper", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("inner", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ), listOf(Relocation(1, "inner", RelocationType.X86_64.PC32, -4, ".text")))
            val obj2 = x86Obj(retCode(), listOf(
                Symbol("inner", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = ElfSharedLinker().link(listOf(obj1, obj2))
            assertEquals(ElfObjectType.DYN.code, readU16(le(binary), 16))
        }

        @Test
        fun `shared lib with external PLT imports`() {
            val obj = x86Obj(callPlaceholder() + retCode(), listOf(
                Symbol("wrapper", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("printf", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ), listOf(Relocation(1, "printf", RelocationType.X86_64.PLT32, -4, ".text")))
            val binary = ElfSharedLinker(sharedLibs = listOf("libc.so.6")).link(listOf(obj))
            val str = String(binary, Charsets.ISO_8859_1)
            assertTrue(str.contains("printf"))
            assertTrue(str.contains("libc.so.6"))
        }

        @Test
        fun `shared lib with rodata`() {
            val code = leaRipPlaceholder() + retCode()
            val rodata = "shared data\u0000".toByteArray()
            val obj = x86Obj(code, listOf(
                Symbol("getter", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("str", value = 0, section = ".rodata",
                    binding = SymbolBinding.LOCAL, kind = SymbolKind.DATA),
            ), listOf(Relocation(3, "str", RelocationType.X86_64.PC32, -4, ".text")),
                sections = listOf(
                    Section(".text", SectionKind.TEXT, code, align = 16),
                    Section(".rodata", SectionKind.RODATA, rodata, align = 1),
                ))
            val binary = ElfSharedLinker().link(listOf(obj))
            assertTrue(String(binary, Charsets.US_ASCII).contains("shared data"))
        }

        @Test
        fun `rejects empty object list`() {
            assertThrows(IllegalArgumentException::class.java) {
                ElfSharedLinker().link(emptyList())
            }
        }

        @Test
        fun `local symbols not exported`() {
            val obj = x86Obj(retCode() + retCode(), listOf(
                Symbol("public_fn", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("private_fn", value = 1, section = ".text",
                    binding = SymbolBinding.LOCAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = ElfSharedLinker().link(listOf(obj))
            val str = String(binary, Charsets.ISO_8859_1)
            assertTrue(str.contains("public_fn"))
        }
    }

    @Nested
    inner class `program header generation` {

        @Test
        fun `PHDR segment is first in dynamic executable`() {
            val obj = x86Obj(callPlaceholder() + retCode(), listOf(
                Symbol("main", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("puts", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ), listOf(Relocation(1, "puts", RelocationType.X86_64.PLT32, -4, ".text")))
            val binary = ElfLinker().link(listOf(obj))
            val buf = le(binary)
            val phoff = readU64(buf, 32).toInt()
            assertEquals(ElfSegmentType.PHDR.code, readU32(buf, phoff))
        }

        @Test
        fun `LOAD RX covers text and rodata`() {
            val code = callPlaceholder() + retCode()
            val rodata = "data\u0000".toByteArray()
            val obj = x86Obj(code, listOf(
                Symbol("main", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("puts", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ), listOf(Relocation(1, "puts", RelocationType.X86_64.PLT32, -4, ".text")),
                sections = listOf(
                    Section(".text", SectionKind.TEXT, code, align = 16),
                    Section(".rodata", SectionKind.RODATA, rodata, align = 1),
                ))
            val binary = ElfLinker().link(listOf(obj))
            val buf = le(binary)
            val phoff = readU64(buf, 32).toInt()
            val rxPhdr = phoff + 2 * Elf.PHDR64_SIZE
            assertEquals(ElfSegmentType.LOAD.code, readU32(buf, rxPhdr))
            val flags = readU32(buf, rxPhdr + 4)
            assertTrue(flags and ElfSegmentFlags.R != 0)
            assertTrue(flags and ElfSegmentFlags.X != 0)
        }

        @Test
        fun `GNU_STACK present in dynamic executable`() {
            val obj = x86Obj(callPlaceholder() + retCode(), listOf(
                Symbol("main", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("puts", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ), listOf(Relocation(1, "puts", RelocationType.X86_64.PLT32, -4, ".text")))
            val binary = ElfLinker().link(listOf(obj))
            val buf = le(binary)
            val phoff = readU64(buf, 32).toInt()
            val phnum = readU16(buf, 56)
            var hasStack = false
            for (i in 0 until phnum) {
                if (readU32(buf, phoff + i * Elf.PHDR64_SIZE) == ElfSegmentType.GNU_STACK.code)
                    hasStack = true
            }
            assertTrue(hasStack)
        }

        @Test
        fun `segment alignment is page sized`() {
            val obj = x86Obj(callPlaceholder() + retCode(), listOf(
                Symbol("main", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("puts", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ), listOf(Relocation(1, "puts", RelocationType.X86_64.PLT32, -4, ".text")))
            val binary = ElfLinker().link(listOf(obj))
            val buf = le(binary)
            val phoff = readU64(buf, 32).toInt()
            val rxPhdr = phoff + 2 * Elf.PHDR64_SIZE
            val align = readU64(buf, rxPhdr + 48)
            assertEquals(0x1000L, align)
        }
    }

    @Nested
    inner class `output validation` {

        @Test
        fun `ehdr size field correct`() {
            val obj = x86Obj(retCode(), listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = ElfStaticLinker().link(listOf(obj))
            assertEquals(Elf.EHDR64_SIZE, readU16(le(binary), 52))
        }

        @Test
        fun `phdr entry size correct`() {
            val obj = x86Obj(retCode(), listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = ElfStaticLinker().link(listOf(obj))
            assertEquals(Elf.PHDR64_SIZE, readU16(le(binary), 54))
        }

        @Test
        fun `static binary non-trivial but compact`() {
            val code = nopCode(100) + retCode()
            val obj = x86Obj(code, listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = ElfStaticLinker().link(listOf(obj))
            assertTrue(binary.size > 200)
            assertTrue(binary.size < 50000)
        }

        @Test
        fun `dynamic binary larger than static for same code`() {
            val code = callPlaceholder() + retCode()
            val staticObj = x86Obj(code, listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val dynObj = x86Obj(code, listOf(
                Symbol("main", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("puts", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ), listOf(Relocation(1, "puts", RelocationType.X86_64.PLT32, -4, ".text")))
            val staticBin = ElfStaticLinker().link(listOf(staticObj))
            val dynBin = ElfLinker().link(listOf(dynObj))
            assertTrue(dynBin.size > staticBin.size,
                "Dynamic binary (${dynBin.size}) should be larger than static (${staticBin.size})")
        }

        @Test
        fun `shared library smaller than executable with same code`() {
            val code = retCode()
            val soObj = x86Obj(code, listOf(
                Symbol("func", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = ElfSharedLinker().link(listOf(soObj))
            assertTrue(binary.size > 0)
            assertTrue(binary.size < 100000)
        }
    }

    @Nested
    inner class `symbol handling` {

        @Test
        fun `main used as entry when no _start`() {
            val code = retCode()
            val obj = x86Obj(code, listOf(
                Symbol("main", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = ElfStaticLinker().link(listOf(obj))
            val entry = readU64(le(binary), 24)
            assertTrue(entry >= 0x400000)
        }

        @Test
        fun `_start preferred over main`() {
            val code = retCode() + movEaxImm(0) + retCode()
            val obj = x86Obj(code, listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("main", value = 1, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = ElfStaticLinker().link(listOf(obj))
            val entry = readU64(le(binary), 24)
            val off = (entry - 0x400000).toInt()
            assertEquals(0xC3.toByte(), binary[off], "Entry at _start (ret), not main")
        }

        @Test
        fun `symbol at offset within section`() {
            val code = nopCode(10) + movEaxImm(42) + retCode()
            val obj = x86Obj(code, listOf(
                Symbol("_start", value = 10, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = ElfStaticLinker().link(listOf(obj))
            val entry = readU64(le(binary), 24)
            val off = (entry - 0x400000).toInt()
            assertEquals(0xB8.toByte(), binary[off])
            assertEquals(42.toByte(), binary[off + 1])
        }

        @Test
        fun `multiple symbols in same section`() {
            val code = movEaxImm(1) + retCode() + movEaxImm(2) + retCode()
            val obj = x86Obj(code, listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("func2", value = 6, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = ElfStaticLinker().link(listOf(obj))
            assertTrue(binary.size > 0)
        }

        @Test
        fun `cross-object symbol resolution with multiple defined symbols`() {
            val code1 = callPlaceholder() + retCode()
            val code2 = movEaxImm(10) + retCode()
            val code3 = movEaxImm(20) + retCode()
            val obj1 = x86Obj(code1, listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("target", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ), listOf(Relocation(1, "target", RelocationType.X86_64.PC32, -4, ".text")))
            val obj2 = x86Obj(code2, listOf(
                Symbol("other", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val obj3 = x86Obj(code3, listOf(
                Symbol("target", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = ElfStaticLinker().link(listOf(obj1, obj2, obj3))
            elfMagicValid(binary)
        }
    }

    @Nested
    inner class `section merging` {

        @Test
        fun `text sections from multiple objects are concatenated`() {
            val code1 = nopCode(5) + retCode()
            val code2 = nopCode(3) + retCode()
            val obj1 = x86Obj(code1, listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val obj2 = x86Obj(code2, listOf(
                Symbol("func2", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = ElfStaticLinker().link(listOf(obj1, obj2))
            assertTrue(binary.size > 0)
        }

        @Test
        fun `data and text in same object produce separate segments`() {
            val code = movRipPlaceholder() + retCode()
            val data = byteArrayOf(0x42, 0x00, 0x00, 0x00)
            val obj = x86Obj(code, listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("val", value = 0, section = ".data",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.DATA),
            ), listOf(Relocation(2, "val", RelocationType.X86_64.PC32, -4, ".text")),
                sections = listOf(
                    Section(".text", SectionKind.TEXT, code, align = 16),
                    Section(".data", SectionKind.DATA, data, align = 4),
                ))
            val binary = ElfStaticLinker().link(listOf(obj))
            val buf = le(binary)
            val phoff = readU64(buf, 32).toInt()
            assertEquals(ElfSegmentType.LOAD.code, readU32(buf, phoff))
            val rwPhdr = phoff + Elf.PHDR64_SIZE
            assertEquals(ElfSegmentType.LOAD.code, readU32(buf, rwPhdr))
        }

        @Test
        fun `alignment padding between merged sections`() {
            val code1 = byteArrayOf(0x90.toByte())
            val code2 = retCode()
            val obj1 = x86Obj(code1, listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ), sections = listOf(Section(".text", SectionKind.TEXT, code1, align = 1)))
            val obj2 = x86Obj(code2, listOf(
                Symbol("func", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ), sections = listOf(Section(".text", SectionKind.TEXT, code2, align = 16)))
            val binary = ElfStaticLinker().link(listOf(obj1, obj2))
            assertTrue(binary.size > 0)
        }
    }

    @Nested
    inner class `edge cases` {

        @Test
        fun `single byte function`() {
            val obj = x86Obj(retCode(), listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = ElfStaticLinker().link(listOf(obj))
            elfMagicValid(binary)
        }

        @Test
        fun `large number of symbols`() {
            val code = ByteArray(100) { 0x90.toByte() } + retCode()
            val symbols = mutableListOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            )
            for (i in 1..20) {
                symbols.add(Symbol("sym$i", value = i.toLong(), section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION))
            }
            val obj = x86Obj(code, symbols)
            val binary = ElfStaticLinker().link(listOf(obj))
            elfMagicValid(binary)
        }

        @Test
        fun `empty rodata section handled`() {
            val obj = x86Obj(retCode(), listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ), sections = listOf(
                Section(".text", SectionKind.TEXT, retCode(), align = 16),
                Section(".rodata", SectionKind.RODATA, ByteArray(0), align = 1),
            ))
            val binary = ElfStaticLinker().link(listOf(obj))
            elfMagicValid(binary)
        }

        @Test
        fun `ten objects linked together`() {
            val objs = mutableListOf<ObjectFile>()
            for (i in 0 until 10) {
                val name = if (i == 0) "_start" else "f$i"
                val nextName = if (i < 9) "f${i + 1}" else null
                val code = if (nextName != null) callPlaceholder() + retCode() else retCode()
                val syms = mutableListOf(Symbol(name, value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION))
                if (nextName != null) syms.add(Symbol(nextName, value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED))
                val rels = if (nextName != null) listOf(Relocation(1, nextName, RelocationType.X86_64.PC32, -4, ".text")) else emptyList()
                objs.add(x86Obj(code, syms, rels))
            }
            val binary = ElfStaticLinker().link(objs)
            elfMagicValid(binary)
        }

        @Test
        fun `symbol defined in second object referenced from first`() {
            val code1 = callPlaceholder() + retCode()
            val code2 = movEaxImm(55) + retCode()
            val obj1 = x86Obj(code1, listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("late_func", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ), listOf(Relocation(1, "late_func", RelocationType.X86_64.PC32, -4, ".text")))
            val obj2 = x86Obj(code2, listOf(
                Symbol("late_func", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = ElfStaticLinker().link(listOf(obj1, obj2))
            val buf = le(binary)
            val entry = readU64(buf, 24)
            val off = (entry - 0x400000).toInt()
            val disp = readI32(buf, off + 1)
            val target = off + 5 + disp
            assertEquals(0xB8.toByte(), binary[target])
            assertEquals(55.toByte(), binary[target + 1])
        }

        @Test
        fun `shared library with zero exports but has code`() {
            val obj = x86Obj(retCode(), listOf(
                Symbol("internal", value = 0, section = ".text",
                    binding = SymbolBinding.LOCAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = ElfSharedLinker().link(listOf(obj))
            assertEquals(ElfObjectType.DYN.code, readU16(le(binary), 16))
        }

        @Test
        fun `dynamic linker generates start stub for main`() {
            val code = callPlaceholder() + retCode()
            val obj = x86Obj(code, listOf(
                Symbol("main", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("puts", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ), listOf(Relocation(1, "puts", RelocationType.X86_64.PLT32, -4, ".text")))
            val binary = ElfLinker().link(listOf(obj))
            assertTrue(binary.size > 0)
        }
    }

    @Nested
    inner class `custom base address` {

        @Test
        fun `static linker custom base address`() {
            val obj = x86Obj(retCode(), listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = ElfStaticLinker(baseAddr = 0x800000L).link(listOf(obj))
            val entry = readU64(le(binary), 24)
            assertTrue(entry >= 0x800000)
        }
    }

    @Nested
    inner class `relocation with data section` {

        @Test
        fun `PC32 relocation from text to data`() {
            val code = movRipPlaceholder() + retCode()
            val data = byteArrayOf(0x2A, 0x00, 0x00, 0x00)
            val obj = x86Obj(code, listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("myvar", value = 0, section = ".data",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.DATA),
            ), listOf(Relocation(2, "myvar", RelocationType.X86_64.PC32, -4, ".text")),
                sections = listOf(
                    Section(".text", SectionKind.TEXT, code, align = 16),
                    Section(".data", SectionKind.DATA, data, align = 4),
                ))
            val binary = ElfStaticLinker().link(listOf(obj))
            val buf = le(binary)
            val entry = readU64(buf, 24)
            val off = (entry - 0x400000).toInt()
            assertNotEquals(0, readI32(buf, off + 2))
        }

        @Test
        fun `PC32 relocation from text to rodata`() {
            val code = leaRipPlaceholder() + retCode()
            val rodata = "test string\u0000".toByteArray()
            val obj = x86Obj(code, listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("str", value = 0, section = ".rodata",
                    binding = SymbolBinding.LOCAL, kind = SymbolKind.DATA),
            ), listOf(Relocation(3, "str", RelocationType.X86_64.PC32, -4, ".text")),
                sections = listOf(
                    Section(".text", SectionKind.TEXT, code, align = 16),
                    Section(".rodata", SectionKind.RODATA, rodata, align = 1),
                ))
            val binary = ElfStaticLinker().link(listOf(obj))
            val buf = le(binary)
            val entry = readU64(buf, 24)
            val off = (entry - 0x400000).toInt()
            val disp = readI32(buf, off + 3)
            // The displacement + RIP should point to the string
            val strFileOff = off + 7 + disp
            assertTrue(strFileOff > 0 && strFileOff < binary.size)
            assertEquals('t'.code.toByte(), binary[strFileOff])
        }
    }

    @Nested
    inner class `shared library multiple shared libs` {

        @Test
        fun `references multiple shared libraries`() {
            val code = callPlaceholder() + callPlaceholder() + retCode()
            val obj = x86Obj(code, listOf(
                Symbol("wrapper", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("printf", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
                Symbol("dlopen", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ), listOf(
                Relocation(1, "printf", RelocationType.X86_64.PLT32, -4, ".text"),
                Relocation(6, "dlopen", RelocationType.X86_64.PLT32, -4, ".text"),
            ))
            val binary = ElfSharedLinker(sharedLibs = listOf("libc.so.6", "libdl.so.2")).link(listOf(obj))
            val str = String(binary, Charsets.ISO_8859_1)
            assertTrue(str.contains("libc.so.6"))
            assertTrue(str.contains("libdl.so.2"))
        }

        @Test
        fun `shared lib with data section`() {
            val code = retCode()
            val data = byteArrayOf(0x01, 0x02, 0x03, 0x04)
            val obj = x86Obj(code, listOf(
                Symbol("getter", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ), sections = listOf(
                Section(".text", SectionKind.TEXT, code, align = 16),
                Section(".data", SectionKind.DATA, data, align = 4),
            ))
            val binary = ElfSharedLinker().link(listOf(obj))
            assertEquals(ElfObjectType.DYN.code, readU16(le(binary), 16))
        }

        @Test
        fun `shared lib weak symbol exported`() {
            val obj = x86Obj(retCode(), listOf(
                Symbol("weak_func", value = 0, section = ".text",
                    binding = SymbolBinding.WEAK, kind = SymbolKind.FUNCTION),
            ))
            val binary = ElfSharedLinker().link(listOf(obj))
            assertTrue(String(binary, Charsets.ISO_8859_1).contains("weak_func"))
        }
    }

    @Nested
    inner class `dynamic linker with data` {

        @Test
        fun `dynamic executable with data section`() {
            val code = callPlaceholder() + retCode()
            val data = byteArrayOf(0x42, 0x00, 0x00, 0x00)
            val obj = x86Obj(code, listOf(
                Symbol("main", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("puts", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ), listOf(Relocation(1, "puts", RelocationType.X86_64.PLT32, -4, ".text")),
                sections = listOf(
                    Section(".text", SectionKind.TEXT, code, align = 16),
                    Section(".data", SectionKind.DATA, data, align = 4),
                ))
            val binary = ElfLinker().link(listOf(obj))
            elfMagicValid(binary)
            assertEquals(ElfObjectType.EXEC.code, readU16(le(binary), 16))
        }

        @Test
        fun `dynamic executable with multiple shared libs`() {
            val code = callPlaceholder() + retCode()
            val obj = x86Obj(code, listOf(
                Symbol("main", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("puts", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ), listOf(Relocation(1, "puts", RelocationType.X86_64.PLT32, -4, ".text")))
            val binary = ElfLinker(sharedLibs = listOf("libc.so.6", "libm.so.6")).link(listOf(obj))
            val str = String(binary, Charsets.ISO_8859_1)
            assertTrue(str.contains("libc.so.6"))
            assertTrue(str.contains("libm.so.6"))
        }

        @Test
        fun `dynamic executable three imports patched`() {
            val code = callPlaceholder() + callPlaceholder() + callPlaceholder() + retCode()
            val obj = x86Obj(code, listOf(
                Symbol("main", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("puts", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
                Symbol("printf", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
                Symbol("exit", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ), listOf(
                Relocation(1, "puts", RelocationType.X86_64.PLT32, -4, ".text"),
                Relocation(6, "printf", RelocationType.X86_64.PLT32, -4, ".text"),
                Relocation(11, "exit", RelocationType.X86_64.PLT32, -4, ".text"),
            ))
            val binary = ElfLinker().link(listOf(obj))
            val str = String(binary, Charsets.ISO_8859_1)
            assertTrue(str.contains("puts"))
            assertTrue(str.contains("printf"))
            assertTrue(str.contains("exit"))
        }
    }

    @Nested
    inner class `static linker data relocation` {

        @Test
        fun `R_64 relocation stores absolute address`() {
            val code = retCode()
            val data = ByteArray(8)
            val obj = x86Obj(code, listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("ptr", value = 0, section = ".data",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.DATA),
            ), listOf(Relocation(0, "_start", RelocationType.X86_64.R_64, 0, ".data")),
                sections = listOf(
                    Section(".text", SectionKind.TEXT, code, align = 16),
                    Section(".data", SectionKind.DATA, data, align = 8),
                ))
            val binary = ElfStaticLinker().link(listOf(obj))
            val buf = le(binary)
            val entry = readU64(buf, 24)
            assertTrue(entry >= 0x400000)
        }

        @Test
        fun `relocation with non-zero addend`() {
            val code = leaRipPlaceholder() + retCode()
            val rodata = "ABCDEFGH\u0000".toByteArray()
            val obj = x86Obj(code, listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("str", value = 0, section = ".rodata",
                    binding = SymbolBinding.LOCAL, kind = SymbolKind.DATA),
            ), listOf(Relocation(3, "str", RelocationType.X86_64.PC32, -4, ".text")),
                sections = listOf(
                    Section(".text", SectionKind.TEXT, code, align = 16),
                    Section(".rodata", SectionKind.RODATA, rodata, align = 1),
                ))
            val binary = ElfStaticLinker().link(listOf(obj))
            assertTrue(String(binary, Charsets.US_ASCII).contains("ABCDEFGH"))
        }
    }

    @Nested
    inner class `shared library hash table` {

        @Test
        fun `hash table present for single export`() {
            val obj = x86Obj(retCode(), listOf(
                Symbol("func", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val binary = ElfSharedLinker().link(listOf(obj))
            assertTrue(binary.size > 100)
        }

        @Test
        fun `hash table present for many exports`() {
            val code = ByteArray(10) { 0xC3.toByte() }
            val symbols = (0 until 10).map {
                Symbol("fn$it", value = it.toLong(), section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION)
            }
            val obj = x86Obj(code, symbols)
            val binary = ElfSharedLinker(soname = "libmany.so").link(listOf(obj))
            val str = String(binary, Charsets.ISO_8859_1)
            for (i in 0 until 10) {
                assertTrue(str.contains("fn$i"))
            }
        }
    }

    @Nested
    inner class `linker flags and options` {

        @Test
        fun `custom interpreter path`() {
            val code = callPlaceholder() + retCode()
            val obj = x86Obj(code, listOf(
                Symbol("main", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("puts", value = 0, section = null,
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.UNDEFINED),
            ), listOf(Relocation(1, "puts", RelocationType.X86_64.PLT32, -4, ".text")))
            val binary = ElfLinker(interpreter = "/lib/ld-musl-x86_64.so.1").link(listOf(obj))
            assertTrue(String(binary, Charsets.ISO_8859_1).contains("ld-musl"))
        }

        @Test
        fun `base address affects entry point in static linker`() {
            val obj = x86Obj(retCode(), listOf(
                Symbol("_start", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ))
            val bin1 = ElfStaticLinker(baseAddr = 0x400000L).link(listOf(obj))
            val bin2 = ElfStaticLinker(baseAddr = 0x800000L).link(listOf(obj))
            val entry1 = readU64(le(bin1), 24)
            val entry2 = readU64(le(bin2), 24)
            assertTrue(entry1 < entry2)
        }
    }
}
