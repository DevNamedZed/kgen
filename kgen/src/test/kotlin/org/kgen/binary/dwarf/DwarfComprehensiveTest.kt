package org.kgen.binary.dwarf

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.kgen.binary.*
import org.kgen.binary.dwarf.DwarfReaderTest.*
import org.kgen.reflect.TypeKind
import org.kgen.reflect.TypeRef

class DwarfComprehensiveTest {

    @Nested
    inner class DwarfTagTests {

        @Test
        fun `ARRAY_TYPE has correct code`() {
            assertEquals(0x01, DwarfTag.ARRAY_TYPE.code)
        }

        @Test
        fun `CLASS_TYPE has correct code`() {
            assertEquals(0x02, DwarfTag.CLASS_TYPE.code)
        }

        @Test
        fun `ENTRY_POINT has correct code`() {
            assertEquals(0x03, DwarfTag.ENTRY_POINT.code)
        }

        @Test
        fun `ENUMERATION_TYPE has correct code`() {
            assertEquals(0x04, DwarfTag.ENUMERATION_TYPE.code)
        }

        @Test
        fun `FORMAL_PARAMETER has correct code`() {
            assertEquals(0x05, DwarfTag.FORMAL_PARAMETER.code)
        }

        @Test
        fun `LEXICAL_BLOCK has correct code`() {
            assertEquals(0x0B, DwarfTag.LEXICAL_BLOCK.code)
        }

        @Test
        fun `MEMBER has correct code`() {
            assertEquals(0x0D, DwarfTag.MEMBER.code)
        }

        @Test
        fun `POINTER_TYPE has correct code`() {
            assertEquals(0x0F, DwarfTag.POINTER_TYPE.code)
        }

        @Test
        fun `REFERENCE_TYPE has correct code`() {
            assertEquals(0x10, DwarfTag.REFERENCE_TYPE.code)
        }

        @Test
        fun `COMPILE_UNIT has correct code`() {
            assertEquals(0x11, DwarfTag.COMPILE_UNIT.code)
        }

        @Test
        fun `STRING_TYPE has correct code`() {
            assertEquals(0x12, DwarfTag.STRING_TYPE.code)
        }

        @Test
        fun `STRUCTURE_TYPE has correct code`() {
            assertEquals(0x13, DwarfTag.STRUCTURE_TYPE.code)
        }

        @Test
        fun `SUBROUTINE_TYPE has correct code`() {
            assertEquals(0x15, DwarfTag.SUBROUTINE_TYPE.code)
        }

        @Test
        fun `TYPEDEF has correct code`() {
            assertEquals(0x16, DwarfTag.TYPEDEF.code)
        }

        @Test
        fun `UNION_TYPE has correct code`() {
            assertEquals(0x17, DwarfTag.UNION_TYPE.code)
        }

        @Test
        fun `VARIABLE has correct code`() {
            assertEquals(0x34, DwarfTag.VARIABLE.code)
        }

        @Test
        fun `VOLATILE_TYPE has correct code`() {
            assertEquals(0x35, DwarfTag.VOLATILE_TYPE.code)
        }

        @Test
        fun `BASE_TYPE has correct code`() {
            assertEquals(0x24, DwarfTag.BASE_TYPE.code)
        }

        @Test
        fun `CONST_TYPE has correct code`() {
            assertEquals(0x26, DwarfTag.CONST_TYPE.code)
        }

        @Test
        fun `ENUMERATOR has correct code`() {
            assertEquals(0x28, DwarfTag.ENUMERATOR.code)
        }

        @Test
        fun `SUBPROGRAM has correct code`() {
            assertEquals(0x2E, DwarfTag.SUBPROGRAM.code)
        }

        @Test
        fun `SUBRANGE_TYPE has correct code`() {
            assertEquals(0x21, DwarfTag.SUBRANGE_TYPE.code)
        }

        @Test
        fun `NAMESPACE has correct code`() {
            assertEquals(0x39, DwarfTag.NAMESPACE.code)
        }

        @Test
        fun `RVALUE_REFERENCE_TYPE has correct code`() {
            assertEquals(0x42, DwarfTag.RVALUE_REFERENCE_TYPE.code)
        }

        @Test
        fun `RESTRICT_TYPE has correct code`() {
            assertEquals(0x37, DwarfTag.RESTRICT_TYPE.code)
        }

        @Test
        fun `TEMPLATE_TYPE_PARAMETER has correct code`() {
            assertEquals(0x2F, DwarfTag.TEMPLATE_TYPE_PARAMETER.code)
        }

        @Test
        fun `TEMPLATE_VALUE_PARAMETER has correct code`() {
            assertEquals(0x30, DwarfTag.TEMPLATE_VALUE_PARAMETER.code)
        }

        @Test
        fun `INHERITANCE has correct code`() {
            assertEquals(0x1C, DwarfTag.INHERITANCE.code)
        }

        @Test
        fun `fromCode returns correct tag`() {
            assertEquals(DwarfTag.COMPILE_UNIT, DwarfTag.fromCode(0x11))
        }

        @Test
        fun `fromCode returns null for unknown code`() {
            assertNull(DwarfTag.fromCode(0xFF))
        }

        @Test
        fun `fromCode roundtrips all tags`() {
            for (tag in DwarfTag.entries) {
                assertEquals(tag, DwarfTag.fromCode(tag.code))
            }
        }
    }

    @Nested
    inner class DwarfAttributeTests {

        @Test
        fun `NAME has correct code`() {
            assertEquals(0x03, DwarfAttribute.NAME.code)
        }

        @Test
        fun `BYTE_SIZE has correct code`() {
            assertEquals(0x0B, DwarfAttribute.BYTE_SIZE.code)
        }

        @Test
        fun `LOW_PC has correct code`() {
            assertEquals(0x11, DwarfAttribute.LOW_PC.code)
        }

        @Test
        fun `HIGH_PC has correct code`() {
            assertEquals(0x12, DwarfAttribute.HIGH_PC.code)
        }

        @Test
        fun `LANGUAGE has correct code`() {
            assertEquals(0x13, DwarfAttribute.LANGUAGE.code)
        }

        @Test
        fun `COMP_DIR has correct code`() {
            assertEquals(0x1B, DwarfAttribute.COMP_DIR.code)
        }

        @Test
        fun `ENCODING has correct code`() {
            assertEquals(0x3E, DwarfAttribute.ENCODING.code)
        }

        @Test
        fun `TYPE has correct code`() {
            assertEquals(0x49, DwarfAttribute.TYPE.code)
        }

        @Test
        fun `PRODUCER has correct code`() {
            assertEquals(0x25, DwarfAttribute.PRODUCER.code)
        }

        @Test
        fun `LINKAGE_NAME has correct code`() {
            assertEquals(0x6E, DwarfAttribute.LINKAGE_NAME.code)
        }

        @Test
        fun `DECL_FILE has correct code`() {
            assertEquals(0x3A, DwarfAttribute.DECL_FILE.code)
        }

        @Test
        fun `DECL_LINE has correct code`() {
            assertEquals(0x3B, DwarfAttribute.DECL_LINE.code)
        }

        @Test
        fun `DATA_MEMBER_LOCATION has correct code`() {
            assertEquals(0x38, DwarfAttribute.DATA_MEMBER_LOCATION.code)
        }

        @Test
        fun `ACCESSIBILITY has correct code`() {
            assertEquals(0x32, DwarfAttribute.ACCESSIBILITY.code)
        }

        @Test
        fun `ARTIFICIAL has correct code`() {
            assertEquals(0x33, DwarfAttribute.ARTIFICIAL.code)
        }

        @Test
        fun `CONST_VALUE has correct code`() {
            assertEquals(0x1C, DwarfAttribute.CONST_VALUE.code)
        }

        @Test
        fun `UPPER_BOUND has correct code`() {
            assertEquals(0x2F, DwarfAttribute.UPPER_BOUND.code)
        }

        @Test
        fun `COUNT has correct code`() {
            assertEquals(0x37, DwarfAttribute.COUNT.code)
        }

        @Test
        fun `EXTERNAL has correct code`() {
            assertEquals(0x3F, DwarfAttribute.EXTERNAL.code)
        }

        @Test
        fun `VIRTUALITY has correct code`() {
            assertEquals(0x4C, DwarfAttribute.VIRTUALITY.code)
        }

        @Test
        fun `fromCode returns correct attribute`() {
            assertEquals(DwarfAttribute.NAME, DwarfAttribute.fromCode(0x03))
        }

        @Test
        fun `fromCode returns null for unknown code`() {
            assertNull(DwarfAttribute.fromCode(0xFF))
        }

        @Test
        fun `fromCode roundtrips all attributes`() {
            for (attr in DwarfAttribute.entries) {
                assertEquals(attr, DwarfAttribute.fromCode(attr.code))
            }
        }
    }

    @Nested
    inner class DwarfFormTests {

        @Test
        fun `ADDR has correct code`() {
            assertEquals(0x01, DwarfForm.ADDR.code)
        }

        @Test
        fun `DATA1 has correct code`() {
            assertEquals(0x0B, DwarfForm.DATA1.code)
        }

        @Test
        fun `DATA2 has correct code`() {
            assertEquals(0x05, DwarfForm.DATA2.code)
        }

        @Test
        fun `DATA4 has correct code`() {
            assertEquals(0x06, DwarfForm.DATA4.code)
        }

        @Test
        fun `DATA8 has correct code`() {
            assertEquals(0x07, DwarfForm.DATA8.code)
        }

        @Test
        fun `STRING has correct code`() {
            assertEquals(0x08, DwarfForm.STRING.code)
        }

        @Test
        fun `STRP has correct code`() {
            assertEquals(0x0E, DwarfForm.STRP.code)
        }

        @Test
        fun `FLAG has correct code`() {
            assertEquals(0x0C, DwarfForm.FLAG.code)
        }

        @Test
        fun `FLAG_PRESENT has correct code`() {
            assertEquals(0x19, DwarfForm.FLAG_PRESENT.code)
        }

        @Test
        fun `REF1 has correct code`() {
            assertEquals(0x11, DwarfForm.REF1.code)
        }

        @Test
        fun `REF2 has correct code`() {
            assertEquals(0x12, DwarfForm.REF2.code)
        }

        @Test
        fun `REF4 has correct code`() {
            assertEquals(0x13, DwarfForm.REF4.code)
        }

        @Test
        fun `REF8 has correct code`() {
            assertEquals(0x14, DwarfForm.REF8.code)
        }

        @Test
        fun `EXPRLOC has correct code`() {
            assertEquals(0x18, DwarfForm.EXPRLOC.code)
        }

        @Test
        fun `SEC_OFFSET has correct code`() {
            assertEquals(0x17, DwarfForm.SEC_OFFSET.code)
        }

        @Test
        fun `IMPLICIT_CONST has correct code`() {
            assertEquals(0x21, DwarfForm.IMPLICIT_CONST.code)
        }

        @Test
        fun `SDATA has correct code`() {
            assertEquals(0x0D, DwarfForm.SDATA.code)
        }

        @Test
        fun `UDATA has correct code`() {
            assertEquals(0x0F, DwarfForm.UDATA.code)
        }

        @Test
        fun `LINE_STRP has correct code`() {
            assertEquals(0x1F, DwarfForm.LINE_STRP.code)
        }

        @Test
        fun `DATA16 has correct code`() {
            assertEquals(0x1E, DwarfForm.DATA16.code)
        }

        @Test
        fun `INDIRECT has correct code`() {
            assertEquals(0x16, DwarfForm.INDIRECT.code)
        }

        @Test
        fun `fromCode returns correct form`() {
            assertEquals(DwarfForm.ADDR, DwarfForm.fromCode(0x01))
        }

        @Test
        fun `fromCode returns null for unknown code`() {
            assertNull(DwarfForm.fromCode(0xFF))
        }

        @Test
        fun `fromCode roundtrips all forms`() {
            for (form in DwarfForm.entries) {
                assertEquals(form, DwarfForm.fromCode(form.code))
            }
        }
    }

    @Nested
    inner class DwarfLanguageTests {

        @Test
        fun `C89 has correct code`() {
            assertEquals(0x01, DwarfLanguage.C89.code)
        }

        @Test
        fun `C has correct code`() {
            assertEquals(0x02, DwarfLanguage.C.code)
        }

        @Test
        fun `C_PLUS_PLUS has correct code`() {
            assertEquals(0x04, DwarfLanguage.C_PLUS_PLUS.code)
        }

        @Test
        fun `JAVA has correct code`() {
            assertEquals(0x0B, DwarfLanguage.JAVA.code)
        }

        @Test
        fun `RUST has correct code`() {
            assertEquals(0x1C, DwarfLanguage.RUST.code)
        }

        @Test
        fun `SWIFT has correct code`() {
            assertEquals(0x1E, DwarfLanguage.SWIFT.code)
        }

        @Test
        fun `GO has correct code`() {
            assertEquals(0x26, DwarfLanguage.GO.code)
        }

        @Test
        fun `PYTHON has correct code`() {
            assertEquals(0x14, DwarfLanguage.PYTHON.code)
        }

        @Test
        fun `KOTLIN has correct code`() {
            assertEquals(0x25, DwarfLanguage.KOTLIN.code)
        }

        @Test
        fun `D has correct code`() {
            assertEquals(0x13, DwarfLanguage.D.code)
        }

        @Test
        fun `ZIG has correct code`() {
            assertEquals(0x2D, DwarfLanguage.ZIG.code)
        }

        @Test
        fun `C_PLUS_PLUS_14 has correct code`() {
            assertEquals(0x21, DwarfLanguage.C_PLUS_PLUS_14.code)
        }

        @Test
        fun `C_PLUS_PLUS_17 has correct code`() {
            assertEquals(0x2A, DwarfLanguage.C_PLUS_PLUS_17.code)
        }

        @Test
        fun `C11 has correct code`() {
            assertEquals(0x1D, DwarfLanguage.C11.code)
        }

        @Test
        fun `C17 has correct code`() {
            assertEquals(0x2C, DwarfLanguage.C17.code)
        }

        @Test
        fun `FORTRAN77 has correct code`() {
            assertEquals(0x07, DwarfLanguage.FORTRAN77.code)
        }

        @Test
        fun `FORTRAN90 has correct code`() {
            assertEquals(0x08, DwarfLanguage.FORTRAN90.code)
        }

        @Test
        fun `PASCAL83 has correct code`() {
            assertEquals(0x09, DwarfLanguage.PASCAL83.code)
        }

        @Test
        fun `ADA83 has correct code`() {
            assertEquals(0x03, DwarfLanguage.ADA83.code)
        }

        @Test
        fun `ADA95 has correct code`() {
            assertEquals(0x0D, DwarfLanguage.ADA95.code)
        }

        @Test
        fun `fromCode returns correct language`() {
            assertEquals(DwarfLanguage.C, DwarfLanguage.fromCode(0x02))
        }

        @Test
        fun `fromCode returns null for unknown code`() {
            assertNull(DwarfLanguage.fromCode(0xFF))
        }

        @Test
        fun `fromCode roundtrips all languages`() {
            for (lang in DwarfLanguage.entries) {
                assertEquals(lang, DwarfLanguage.fromCode(lang.code))
            }
        }
    }

    @Nested
    inner class DwarfTypeEncodingTests {

        @Test
        fun `ADDRESS has correct code`() {
            assertEquals(0x01, DwarfTypeEncoding.ADDRESS.code)
        }

        @Test
        fun `BOOLEAN has correct code`() {
            assertEquals(0x02, DwarfTypeEncoding.BOOLEAN.code)
        }

        @Test
        fun `COMPLEX_FLOAT has correct code`() {
            assertEquals(0x03, DwarfTypeEncoding.COMPLEX_FLOAT.code)
        }

        @Test
        fun `FLOAT has correct code`() {
            assertEquals(0x04, DwarfTypeEncoding.FLOAT.code)
        }

        @Test
        fun `SIGNED has correct code`() {
            assertEquals(0x05, DwarfTypeEncoding.SIGNED.code)
        }

        @Test
        fun `SIGNED_CHAR has correct code`() {
            assertEquals(0x06, DwarfTypeEncoding.SIGNED_CHAR.code)
        }

        @Test
        fun `UNSIGNED has correct code`() {
            assertEquals(0x07, DwarfTypeEncoding.UNSIGNED.code)
        }

        @Test
        fun `UNSIGNED_CHAR has correct code`() {
            assertEquals(0x08, DwarfTypeEncoding.UNSIGNED_CHAR.code)
        }

        @Test
        fun `UTF has correct code`() {
            assertEquals(0x10, DwarfTypeEncoding.UTF.code)
        }

        @Test
        fun `fromCode returns correct encoding`() {
            assertEquals(DwarfTypeEncoding.SIGNED, DwarfTypeEncoding.fromCode(0x05))
        }

        @Test
        fun `fromCode returns null for unknown code`() {
            assertNull(DwarfTypeEncoding.fromCode(0xFF))
        }

        @Test
        fun `fromCode roundtrips all encodings`() {
            for (enc in DwarfTypeEncoding.entries) {
                assertEquals(enc, DwarfTypeEncoding.fromCode(enc.code))
            }
        }
    }

    @Nested
    inner class DwarfReaderParsingTests {

        private fun buildDwarf4(
            abbrevBuilder: DwarfAbbrevBuilder.() -> Unit,
            infoBuilder: DwarfInfoBuilder.() -> Unit,
        ): Pair<ByteArray, ByteArray> {
            val abbrev = DwarfAbbrevBuilder().apply(abbrevBuilder).build()
            val info = DwarfInfoBuilder(abbrev.size, null, emptyList()).apply(infoBuilder).build()
            return info to abbrev
        }

        @Test
        fun `empty debug info via ObjectFile returns null`() {
            val obj = ObjectFile(
                ObjectFormat.ELF, Architecture.X86_64_LINUX,
                emptyList(), emptyList(), emptyList(),
            )
            assertNull(DwarfReader.read(obj))
        }

        @Test
        fun `missing debug_abbrev section returns null`() {
            val obj = ObjectFile(
                ObjectFormat.ELF, Architecture.X86_64_LINUX,
                sections = listOf(
                    Section(".debug_info", SectionKind.DEBUG_INFO, byteArrayOf(0)),
                ),
                symbols = emptyList(),
                relocations = emptyList(),
            )
            assertNull(DwarfReader.read(obj))
        }

        @Test
        fun `minimal compile unit`() {
            val (info, abbrev) = buildDwarf4(
                abbrevBuilder = {
                    abbreviation(1, DwarfTag.COMPILE_UNIT.code, hasChildren = false) {
                        attr(DwarfAttribute.NAME.code, DwarfForm.STRING.code)
                        attr(DwarfAttribute.LANGUAGE.code, DwarfForm.DATA1.code)
                    }
                },
                infoBuilder = {
                    compileUnit(version = 4, abbrevOffset = 0, addrSize = 8) {
                        die(1) {
                            string("hello.c")
                            data1(DwarfLanguage.C.code)
                        }
                    }
                },
            )

            val debug = DwarfReader.read(info, abbrev)
            assertEquals(1, debug.compileUnits.size)
            assertEquals("hello.c", debug.compileUnits[0].name)
            assertEquals(SourceLanguage.C, debug.compileUnits[0].language)
        }

        @Test
        fun `compile unit with C++ language`() {
            val (info, abbrev) = buildDwarf4(
                abbrevBuilder = {
                    abbreviation(1, DwarfTag.COMPILE_UNIT.code, hasChildren = false) {
                        attr(DwarfAttribute.NAME.code, DwarfForm.STRING.code)
                        attr(DwarfAttribute.LANGUAGE.code, DwarfForm.DATA1.code)
                    }
                },
                infoBuilder = {
                    compileUnit(version = 4, abbrevOffset = 0, addrSize = 8) {
                        die(1) {
                            string("main.cpp")
                            data1(DwarfLanguage.C_PLUS_PLUS.code)
                        }
                    }
                },
            )

            val debug = DwarfReader.read(info, abbrev)
            assertEquals(SourceLanguage.C_PLUS_PLUS, debug.compileUnits[0].language)
        }

        @Test
        fun `compile unit with Rust language`() {
            val (info, abbrev) = buildDwarf4(
                abbrevBuilder = {
                    abbreviation(1, DwarfTag.COMPILE_UNIT.code, hasChildren = false) {
                        attr(DwarfAttribute.NAME.code, DwarfForm.STRING.code)
                        attr(DwarfAttribute.LANGUAGE.code, DwarfForm.DATA1.code)
                    }
                },
                infoBuilder = {
                    compileUnit(version = 4, abbrevOffset = 0, addrSize = 8) {
                        die(1) {
                            string("lib.rs")
                            data1(DwarfLanguage.RUST.code)
                        }
                    }
                },
            )

            val debug = DwarfReader.read(info, abbrev)
            assertEquals(SourceLanguage.RUST, debug.compileUnits[0].language)
        }

        @Test
        fun `compile unit with Java language`() {
            val (info, abbrev) = buildDwarf4(
                abbrevBuilder = {
                    abbreviation(1, DwarfTag.COMPILE_UNIT.code, hasChildren = false) {
                        attr(DwarfAttribute.NAME.code, DwarfForm.STRING.code)
                        attr(DwarfAttribute.LANGUAGE.code, DwarfForm.DATA1.code)
                    }
                },
                infoBuilder = {
                    compileUnit(version = 4, abbrevOffset = 0, addrSize = 8) {
                        die(1) {
                            string("Main.java")
                            data1(DwarfLanguage.JAVA.code)
                        }
                    }
                },
            )

            val debug = DwarfReader.read(info, abbrev)
            assertEquals(SourceLanguage.JAVA, debug.compileUnits[0].language)
        }

        @Test
        fun `compile unit with Go language`() {
            val (info, abbrev) = buildDwarf4(
                abbrevBuilder = {
                    abbreviation(1, DwarfTag.COMPILE_UNIT.code, hasChildren = false) {
                        attr(DwarfAttribute.NAME.code, DwarfForm.STRING.code)
                        attr(DwarfAttribute.LANGUAGE.code, DwarfForm.DATA1.code)
                    }
                },
                infoBuilder = {
                    compileUnit(version = 4, abbrevOffset = 0, addrSize = 8) {
                        die(1) {
                            string("main.go")
                            data1(DwarfLanguage.GO.code)
                        }
                    }
                },
            )

            val debug = DwarfReader.read(info, abbrev)
            assertEquals(SourceLanguage.GO, debug.compileUnits[0].language)
        }

        @Test
        fun `compile unit with Swift language`() {
            val (info, abbrev) = buildDwarf4(
                abbrevBuilder = {
                    abbreviation(1, DwarfTag.COMPILE_UNIT.code, hasChildren = false) {
                        attr(DwarfAttribute.NAME.code, DwarfForm.STRING.code)
                        attr(DwarfAttribute.LANGUAGE.code, DwarfForm.DATA1.code)
                    }
                },
                infoBuilder = {
                    compileUnit(version = 4, abbrevOffset = 0, addrSize = 8) {
                        die(1) {
                            string("main.swift")
                            data1(DwarfLanguage.SWIFT.code)
                        }
                    }
                },
            )

            val debug = DwarfReader.read(info, abbrev)
            assertEquals(SourceLanguage.SWIFT, debug.compileUnits[0].language)
        }

        @Test
        fun `compile unit with Kotlin language`() {
            val (info, abbrev) = buildDwarf4(
                abbrevBuilder = {
                    abbreviation(1, DwarfTag.COMPILE_UNIT.code, hasChildren = false) {
                        attr(DwarfAttribute.NAME.code, DwarfForm.STRING.code)
                        attr(DwarfAttribute.LANGUAGE.code, DwarfForm.DATA1.code)
                    }
                },
                infoBuilder = {
                    compileUnit(version = 4, abbrevOffset = 0, addrSize = 8) {
                        die(1) {
                            string("Main.kt")
                            data1(DwarfLanguage.KOTLIN.code)
                        }
                    }
                },
            )

            val debug = DwarfReader.read(info, abbrev)
            assertEquals(SourceLanguage.KOTLIN, debug.compileUnits[0].language)
        }

        @Test
        fun `compile unit with Zig language`() {
            val (info, abbrev) = buildDwarf4(
                abbrevBuilder = {
                    abbreviation(1, DwarfTag.COMPILE_UNIT.code, hasChildren = false) {
                        attr(DwarfAttribute.NAME.code, DwarfForm.STRING.code)
                        attr(DwarfAttribute.LANGUAGE.code, DwarfForm.DATA1.code)
                    }
                },
                infoBuilder = {
                    compileUnit(version = 4, abbrevOffset = 0, addrSize = 8) {
                        die(1) {
                            string("main.zig")
                            data1(DwarfLanguage.ZIG.code)
                        }
                    }
                },
            )

            val debug = DwarfReader.read(info, abbrev)
            assertEquals(SourceLanguage.ZIG, debug.compileUnits[0].language)
        }

        @Test
        fun `compile unit with Python language`() {
            val (info, abbrev) = buildDwarf4(
                abbrevBuilder = {
                    abbreviation(1, DwarfTag.COMPILE_UNIT.code, hasChildren = false) {
                        attr(DwarfAttribute.NAME.code, DwarfForm.STRING.code)
                        attr(DwarfAttribute.LANGUAGE.code, DwarfForm.DATA1.code)
                    }
                },
                infoBuilder = {
                    compileUnit(version = 4, abbrevOffset = 0, addrSize = 8) {
                        die(1) {
                            string("script.py")
                            data1(DwarfLanguage.PYTHON.code)
                        }
                    }
                },
            )

            val debug = DwarfReader.read(info, abbrev)
            assertEquals(SourceLanguage.PYTHON, debug.compileUnits[0].language)
        }

        @Test
        fun `compile unit with D language`() {
            val (info, abbrev) = buildDwarf4(
                abbrevBuilder = {
                    abbreviation(1, DwarfTag.COMPILE_UNIT.code, hasChildren = false) {
                        attr(DwarfAttribute.NAME.code, DwarfForm.STRING.code)
                        attr(DwarfAttribute.LANGUAGE.code, DwarfForm.DATA1.code)
                    }
                },
                infoBuilder = {
                    compileUnit(version = 4, abbrevOffset = 0, addrSize = 8) {
                        die(1) {
                            string("app.d")
                            data1(DwarfLanguage.D.code)
                        }
                    }
                },
            )

            val debug = DwarfReader.read(info, abbrev)
            assertEquals(SourceLanguage.D, debug.compileUnits[0].language)
        }

        @Test
        fun `compile unit with Fortran language`() {
            val (info, abbrev) = buildDwarf4(
                abbrevBuilder = {
                    abbreviation(1, DwarfTag.COMPILE_UNIT.code, hasChildren = false) {
                        attr(DwarfAttribute.NAME.code, DwarfForm.STRING.code)
                        attr(DwarfAttribute.LANGUAGE.code, DwarfForm.DATA1.code)
                    }
                },
                infoBuilder = {
                    compileUnit(version = 4, abbrevOffset = 0, addrSize = 8) {
                        die(1) {
                            string("prog.f90")
                            data1(DwarfLanguage.FORTRAN90.code)
                        }
                    }
                },
            )

            val debug = DwarfReader.read(info, abbrev)
            assertEquals(SourceLanguage.FORTRAN, debug.compileUnits[0].language)
        }

        @Test
        fun `compile unit with Ada language`() {
            val (info, abbrev) = buildDwarf4(
                abbrevBuilder = {
                    abbreviation(1, DwarfTag.COMPILE_UNIT.code, hasChildren = false) {
                        attr(DwarfAttribute.NAME.code, DwarfForm.STRING.code)
                        attr(DwarfAttribute.LANGUAGE.code, DwarfForm.DATA1.code)
                    }
                },
                infoBuilder = {
                    compileUnit(version = 4, abbrevOffset = 0, addrSize = 8) {
                        die(1) {
                            string("hello.adb")
                            data1(DwarfLanguage.ADA95.code)
                        }
                    }
                },
            )

            val debug = DwarfReader.read(info, abbrev)
            assertEquals(SourceLanguage.ADA, debug.compileUnits[0].language)
        }

        @Test
        fun `compile unit with Pascal language`() {
            val (info, abbrev) = buildDwarf4(
                abbrevBuilder = {
                    abbreviation(1, DwarfTag.COMPILE_UNIT.code, hasChildren = false) {
                        attr(DwarfAttribute.NAME.code, DwarfForm.STRING.code)
                        attr(DwarfAttribute.LANGUAGE.code, DwarfForm.DATA1.code)
                    }
                },
                infoBuilder = {
                    compileUnit(version = 4, abbrevOffset = 0, addrSize = 8) {
                        die(1) {
                            string("hello.pas")
                            data1(DwarfLanguage.PASCAL83.code)
                        }
                    }
                },
            )

            val debug = DwarfReader.read(info, abbrev)
            assertEquals(SourceLanguage.PASCAL, debug.compileUnits[0].language)
        }

        @Test
        fun `unknown language maps to CUSTOM`() {
            val (info, abbrev) = buildDwarf4(
                abbrevBuilder = {
                    abbreviation(1, DwarfTag.COMPILE_UNIT.code, hasChildren = false) {
                        attr(DwarfAttribute.NAME.code, DwarfForm.STRING.code)
                        attr(DwarfAttribute.LANGUAGE.code, DwarfForm.DATA1.code)
                    }
                },
                infoBuilder = {
                    compileUnit(version = 4, abbrevOffset = 0, addrSize = 8) {
                        die(1) {
                            string("test.x")
                            data1(0xFE) // unknown language
                        }
                    }
                },
            )

            val debug = DwarfReader.read(info, abbrev)
            assertEquals(SourceLanguage.CUSTOM, debug.compileUnits[0].language)
        }

        @Test
        fun `dwarf4 format detection`() {
            val (info, abbrev) = buildDwarf4(
                abbrevBuilder = {
                    abbreviation(1, DwarfTag.COMPILE_UNIT.code, hasChildren = false) {
                        attr(DwarfAttribute.NAME.code, DwarfForm.STRING.code)
                        attr(DwarfAttribute.LANGUAGE.code, DwarfForm.DATA1.code)
                    }
                },
                infoBuilder = {
                    compileUnit(version = 4, abbrevOffset = 0, addrSize = 8) {
                        die(1) {
                            string("test.c")
                            data1(DwarfLanguage.C.code)
                        }
                    }
                },
            )

            val debug = DwarfReader.read(info, abbrev)
            assertEquals(DebugFormat.DWARF4, debug.format)
        }

        @Test
        fun `base type int parsed correctly`() {
            val (info, abbrev) = buildDwarf4(
                abbrevBuilder = {
                    abbreviation(1, DwarfTag.COMPILE_UNIT.code, hasChildren = true) {
                        attr(DwarfAttribute.NAME.code, DwarfForm.STRING.code)
                        attr(DwarfAttribute.LANGUAGE.code, DwarfForm.DATA1.code)
                    }
                    abbreviation(2, DwarfTag.BASE_TYPE.code, hasChildren = false) {
                        attr(DwarfAttribute.NAME.code, DwarfForm.STRING.code)
                        attr(DwarfAttribute.BYTE_SIZE.code, DwarfForm.DATA1.code)
                        attr(DwarfAttribute.ENCODING.code, DwarfForm.DATA1.code)
                    }
                },
                infoBuilder = {
                    compileUnit(version = 4, abbrevOffset = 0, addrSize = 8) {
                        die(1) {
                            string("test.c")
                            data1(DwarfLanguage.C.code)
                        }
                        die(2) {
                            string("int")
                            data1(4)
                            data1(DwarfTypeEncoding.SIGNED.code)
                        }
                        nullEntry()
                    }
                },
            )

            val debug = DwarfReader.read(info, abbrev)
            assertEquals(1, debug.compileUnits.size)
        }

        @Test
        fun `ObjectFile integration with debug sections`() {
            val (info, abbrev) = buildDwarf4(
                abbrevBuilder = {
                    abbreviation(1, DwarfTag.COMPILE_UNIT.code, hasChildren = false) {
                        attr(DwarfAttribute.NAME.code, DwarfForm.STRING.code)
                        attr(DwarfAttribute.LANGUAGE.code, DwarfForm.DATA1.code)
                    }
                },
                infoBuilder = {
                    compileUnit(version = 4, abbrevOffset = 0, addrSize = 8) {
                        die(1) {
                            string("main.c")
                            data1(DwarfLanguage.C.code)
                        }
                    }
                },
            )

            val obj = ObjectFile(
                ObjectFormat.ELF, Architecture.X86_64_LINUX,
                sections = listOf(
                    Section(".debug_info", SectionKind.DEBUG_INFO, info),
                    Section(".debug_abbrev", SectionKind.DEBUG_ABBREV, abbrev),
                ),
                symbols = emptyList(),
                relocations = emptyList(),
            )

            val debug = DwarfReader.read(obj)
            assertNotNull(debug)
            assertEquals("main.c", debug!!.compileUnits[0].name)
        }

        @Test
        fun `subprogram with parameters`() {
            val (info, abbrev) = buildDwarf4(
                abbrevBuilder = {
                    abbreviation(1, DwarfTag.COMPILE_UNIT.code, hasChildren = true) {
                        attr(DwarfAttribute.NAME.code, DwarfForm.STRING.code)
                        attr(DwarfAttribute.LANGUAGE.code, DwarfForm.DATA1.code)
                    }
                    abbreviation(2, DwarfTag.BASE_TYPE.code, hasChildren = false) {
                        attr(DwarfAttribute.NAME.code, DwarfForm.STRING.code)
                        attr(DwarfAttribute.BYTE_SIZE.code, DwarfForm.DATA1.code)
                        attr(DwarfAttribute.ENCODING.code, DwarfForm.DATA1.code)
                    }
                    abbreviation(3, DwarfTag.SUBPROGRAM.code, hasChildren = true) {
                        attr(DwarfAttribute.NAME.code, DwarfForm.STRING.code)
                        attr(DwarfAttribute.TYPE.code, DwarfForm.REF4.code)
                    }
                    abbreviation(4, DwarfTag.FORMAL_PARAMETER.code, hasChildren = false) {
                        attr(DwarfAttribute.NAME.code, DwarfForm.STRING.code)
                        attr(DwarfAttribute.TYPE.code, DwarfForm.REF4.code)
                    }
                },
                infoBuilder = {
                    compileUnit(version = 4, abbrevOffset = 0, addrSize = 8) {
                        die(1) {
                            string("math.c")
                            data1(DwarfLanguage.C.code)
                        }
                        val intOff = currentOffset()
                        die(2) {
                            string("int")
                            data1(4)
                            data1(DwarfTypeEncoding.SIGNED.code)
                        }
                        die(3) {
                            string("multiply")
                            ref4(intOff)
                        }
                        die(4) {
                            string("x")
                            ref4(intOff)
                        }
                        die(4) {
                            string("y")
                            ref4(intOff)
                        }
                        nullEntry() // end subprogram
                        nullEntry() // end CU
                    }
                },
            )

            val debug = DwarfReader.read(info, abbrev)
            val cu = debug.compileUnits[0]
            assertEquals(1, cu.subprograms.size)
            assertEquals("multiply", cu.subprograms[0].name)
            assertEquals(2, cu.subprograms[0].params.size)
            assertEquals("x", cu.subprograms[0].params[0].name)
            assertEquals("y", cu.subprograms[0].params[1].name)
        }

        @Test
        fun `enum type with enumerators`() {
            val (info, abbrev) = buildDwarf4(
                abbrevBuilder = {
                    abbreviation(1, DwarfTag.COMPILE_UNIT.code, hasChildren = true) {
                        attr(DwarfAttribute.NAME.code, DwarfForm.STRING.code)
                        attr(DwarfAttribute.LANGUAGE.code, DwarfForm.DATA1.code)
                    }
                    abbreviation(2, DwarfTag.BASE_TYPE.code, hasChildren = false) {
                        attr(DwarfAttribute.NAME.code, DwarfForm.STRING.code)
                        attr(DwarfAttribute.BYTE_SIZE.code, DwarfForm.DATA1.code)
                        attr(DwarfAttribute.ENCODING.code, DwarfForm.DATA1.code)
                    }
                    abbreviation(3, DwarfTag.ENUMERATION_TYPE.code, hasChildren = true) {
                        attr(DwarfAttribute.NAME.code, DwarfForm.STRING.code)
                        attr(DwarfAttribute.BYTE_SIZE.code, DwarfForm.DATA1.code)
                        attr(DwarfAttribute.TYPE.code, DwarfForm.REF4.code)
                    }
                    abbreviation(4, DwarfTag.ENUMERATOR.code, hasChildren = false) {
                        attr(DwarfAttribute.NAME.code, DwarfForm.STRING.code)
                        attr(DwarfAttribute.CONST_VALUE.code, DwarfForm.DATA1.code)
                    }
                },
                infoBuilder = {
                    compileUnit(version = 4, abbrevOffset = 0, addrSize = 8) {
                        die(1) {
                            string("status.c")
                            data1(DwarfLanguage.C.code)
                        }
                        val intOff = currentOffset()
                        die(2) {
                            string("int")
                            data1(4)
                            data1(DwarfTypeEncoding.SIGNED.code)
                        }
                        die(3) {
                            string("Status")
                            data1(4)
                            ref4(intOff)
                        }
                        die(4) { string("OK"); data1(0) }
                        die(4) { string("PENDING"); data1(1) }
                        die(4) { string("ERROR"); data1(2) }
                        die(4) { string("FATAL"); data1(3) }
                        nullEntry() // end enum
                        nullEntry() // end CU
                    }
                },
            )

            val debug = DwarfReader.read(info, abbrev)
            val cu = debug.compileUnits[0]
            val enumType = cu.types.filterIsInstance<DebugType.Enum>().first()
            assertEquals("Status", enumType.name)
            assertEquals(4, enumType.enumerators.size)
            assertEquals("OK", enumType.enumerators[0].first)
            assertEquals(0L, enumType.enumerators[0].second)
            assertEquals("FATAL", enumType.enumerators[3].first)
            assertEquals(3L, enumType.enumerators[3].second)
        }

        @Test
        fun `struct type with members`() {
            val (info, abbrev) = buildDwarf4(
                abbrevBuilder = {
                    abbreviation(1, DwarfTag.COMPILE_UNIT.code, hasChildren = true) {
                        attr(DwarfAttribute.NAME.code, DwarfForm.STRING.code)
                        attr(DwarfAttribute.LANGUAGE.code, DwarfForm.DATA1.code)
                    }
                    abbreviation(2, DwarfTag.BASE_TYPE.code, hasChildren = false) {
                        attr(DwarfAttribute.NAME.code, DwarfForm.STRING.code)
                        attr(DwarfAttribute.BYTE_SIZE.code, DwarfForm.DATA1.code)
                        attr(DwarfAttribute.ENCODING.code, DwarfForm.DATA1.code)
                    }
                    abbreviation(3, DwarfTag.STRUCTURE_TYPE.code, hasChildren = true) {
                        attr(DwarfAttribute.NAME.code, DwarfForm.STRING.code)
                        attr(DwarfAttribute.BYTE_SIZE.code, DwarfForm.DATA1.code)
                    }
                    abbreviation(4, DwarfTag.MEMBER.code, hasChildren = false) {
                        attr(DwarfAttribute.NAME.code, DwarfForm.STRING.code)
                        attr(DwarfAttribute.TYPE.code, DwarfForm.REF4.code)
                        attr(DwarfAttribute.DATA_MEMBER_LOCATION.code, DwarfForm.DATA1.code)
                    }
                },
                infoBuilder = {
                    compileUnit(version = 4, abbrevOffset = 0, addrSize = 8) {
                        die(1) {
                            string("vec.c")
                            data1(DwarfLanguage.C.code)
                        }
                        val floatOff = currentOffset()
                        die(2) {
                            string("float")
                            data1(4)
                            data1(DwarfTypeEncoding.FLOAT.code)
                        }
                        die(3) {
                            string("Vec3")
                            data1(12)
                        }
                        die(4) { string("x"); ref4(floatOff); data1(0) }
                        die(4) { string("y"); ref4(floatOff); data1(4) }
                        die(4) { string("z"); ref4(floatOff); data1(8) }
                        nullEntry() // end struct
                        nullEntry() // end CU
                    }
                },
            )

            val debug = DwarfReader.read(info, abbrev)
            val cu = debug.compileUnits[0]
            val struct = cu.types.filterIsInstance<DebugType.Composite>().first { it.name == "Vec3" }
            assertEquals("Vec3", struct.name)
            assertEquals(96, struct.sizeInBits) // 12 bytes * 8
            assertEquals(CompositeTag.STRUCT, struct.tag)
            assertEquals(3, struct.members.size)
            assertEquals("x", struct.members[0].name)
            assertEquals("y", struct.members[1].name)
            assertEquals("z", struct.members[2].name)
            assertEquals(0, struct.members[0].offsetInBits)
            assertEquals(32, struct.members[1].offsetInBits)
            assertEquals(64, struct.members[2].offsetInBits)
        }
    }

    @Nested
    inner class DwarfTypeMapperTests {

        @Test
        fun `maps struct with int fields`() {
            val cu = CompileUnit(
                name = "test.c", directory = "/tmp", producer = "gcc",
                language = SourceLanguage.C,
                types = listOf(
                    DebugType.Composite(
                        "Point", 64, CompositeTag.STRUCT,
                        listOf(
                            DebugMember("x", DebugType.Base("int", 32, DwarfEncoding.SIGNED), 0),
                            DebugMember("y", DebugType.Base("int", 32, DwarfEncoding.SIGNED), 32),
                        ),
                    ),
                ),
            )
            val types = DwarfTypeMapper.map(DebugInfo(listOf(cu)))
            assertEquals(1, types.size)
            assertEquals("Point", types[0].name())
            assertEquals(TypeKind.STRUCT, types[0].kind())
            assertEquals(2, types[0].fields().size)
        }

        @Test
        fun `maps class type`() {
            val cu = CompileUnit(
                name = "test.cpp", directory = "/tmp", producer = "g++",
                language = SourceLanguage.C_PLUS_PLUS,
                types = listOf(
                    DebugType.Composite(
                        "Widget", 32, CompositeTag.CLASS,
                        listOf(DebugMember("value", DebugType.Base("int", 32, DwarfEncoding.SIGNED), 0)),
                    ),
                ),
            )
            val types = DwarfTypeMapper.map(DebugInfo(listOf(cu)))
            assertEquals(1, types.size)
            assertEquals("Widget", types[0].name())
            assertEquals(TypeKind.CLASS, types[0].kind())
        }

        @Test
        fun `maps union type as struct kind`() {
            val cu = CompileUnit(
                name = "test.c", directory = "/tmp", producer = "gcc",
                language = SourceLanguage.C,
                types = listOf(
                    DebugType.Composite(
                        "Data", 64, CompositeTag.UNION,
                        listOf(
                            DebugMember("i", DebugType.Base("int", 32, DwarfEncoding.SIGNED), 0),
                            DebugMember("d", DebugType.Base("double", 64, DwarfEncoding.FLOAT), 0),
                        ),
                    ),
                ),
            )
            val types = DwarfTypeMapper.map(DebugInfo(listOf(cu)))
            assertEquals(1, types.size)
            assertEquals("Data", types[0].name())
            assertEquals(TypeKind.STRUCT, types[0].kind())
        }

        @Test
        fun `maps enum type`() {
            val cu = CompileUnit(
                name = "test.c", directory = "/tmp", producer = "gcc",
                language = SourceLanguage.C,
                types = listOf(
                    DebugType.Enum(
                        "Color", 32,
                        DebugType.Base("unsigned int", 32, DwarfEncoding.UNSIGNED),
                        listOf("RED" to 0L, "GREEN" to 1L, "BLUE" to 2L),
                    ),
                ),
            )
            val types = DwarfTypeMapper.map(DebugInfo(listOf(cu)))
            assertEquals(1, types.size)
            assertEquals("Color", types[0].name())
            assertEquals(TypeKind.ENUM, types[0].kind())
            assertEquals(3, types[0].fields().size)
        }

        @Test
        fun `maps signed int 8-bit to I8`() {
            val cu = CompileUnit(
                name = "t.c", directory = "", producer = "gcc",
                language = SourceLanguage.C,
                types = listOf(
                    DebugType.Composite("S", 8, CompositeTag.STRUCT,
                        listOf(DebugMember("v", DebugType.Base("char", 8, DwarfEncoding.SIGNED_CHAR), 0))),
                ),
            )
            val types = DwarfTypeMapper.map(DebugInfo(listOf(cu)))
            val field = types[0].fields()[0]
            assertEquals(TypeRef.I8, field.fieldType())
        }

        @Test
        fun `maps signed int 16-bit to I16`() {
            val cu = CompileUnit(
                name = "t.c", directory = "", producer = "gcc",
                language = SourceLanguage.C,
                types = listOf(
                    DebugType.Composite("S", 16, CompositeTag.STRUCT,
                        listOf(DebugMember("v", DebugType.Base("short", 16, DwarfEncoding.SIGNED), 0))),
                ),
            )
            val types = DwarfTypeMapper.map(DebugInfo(listOf(cu)))
            assertEquals(TypeRef.I16, types[0].fields()[0].fieldType())
        }

        @Test
        fun `maps signed int 32-bit to I32`() {
            val cu = CompileUnit(
                name = "t.c", directory = "", producer = "gcc",
                language = SourceLanguage.C,
                types = listOf(
                    DebugType.Composite("S", 32, CompositeTag.STRUCT,
                        listOf(DebugMember("v", DebugType.Base("int", 32, DwarfEncoding.SIGNED), 0))),
                ),
            )
            val types = DwarfTypeMapper.map(DebugInfo(listOf(cu)))
            assertEquals(TypeRef.I32, types[0].fields()[0].fieldType())
        }

        @Test
        fun `maps signed int 64-bit to I64`() {
            val cu = CompileUnit(
                name = "t.c", directory = "", producer = "gcc",
                language = SourceLanguage.C,
                types = listOf(
                    DebugType.Composite("S", 64, CompositeTag.STRUCT,
                        listOf(DebugMember("v", DebugType.Base("long", 64, DwarfEncoding.SIGNED), 0))),
                ),
            )
            val types = DwarfTypeMapper.map(DebugInfo(listOf(cu)))
            assertEquals(TypeRef.I64, types[0].fields()[0].fieldType())
        }

        @Test
        fun `maps unsigned int 8-bit to U8`() {
            val cu = CompileUnit(
                name = "t.c", directory = "", producer = "gcc",
                language = SourceLanguage.C,
                types = listOf(
                    DebugType.Composite("S", 8, CompositeTag.STRUCT,
                        listOf(DebugMember("v", DebugType.Base("unsigned char", 8, DwarfEncoding.UNSIGNED_CHAR), 0))),
                ),
            )
            val types = DwarfTypeMapper.map(DebugInfo(listOf(cu)))
            assertEquals(TypeRef.U8, types[0].fields()[0].fieldType())
        }

        @Test
        fun `maps unsigned int 16-bit to U16`() {
            val cu = CompileUnit(
                name = "t.c", directory = "", producer = "gcc",
                language = SourceLanguage.C,
                types = listOf(
                    DebugType.Composite("S", 16, CompositeTag.STRUCT,
                        listOf(DebugMember("v", DebugType.Base("unsigned short", 16, DwarfEncoding.UNSIGNED), 0))),
                ),
            )
            val types = DwarfTypeMapper.map(DebugInfo(listOf(cu)))
            assertEquals(TypeRef.U16, types[0].fields()[0].fieldType())
        }

        @Test
        fun `maps unsigned int 32-bit to U32`() {
            val cu = CompileUnit(
                name = "t.c", directory = "", producer = "gcc",
                language = SourceLanguage.C,
                types = listOf(
                    DebugType.Composite("S", 32, CompositeTag.STRUCT,
                        listOf(DebugMember("v", DebugType.Base("unsigned int", 32, DwarfEncoding.UNSIGNED), 0))),
                ),
            )
            val types = DwarfTypeMapper.map(DebugInfo(listOf(cu)))
            assertEquals(TypeRef.U32, types[0].fields()[0].fieldType())
        }

        @Test
        fun `maps unsigned int 64-bit to U64`() {
            val cu = CompileUnit(
                name = "t.c", directory = "", producer = "gcc",
                language = SourceLanguage.C,
                types = listOf(
                    DebugType.Composite("S", 64, CompositeTag.STRUCT,
                        listOf(DebugMember("v", DebugType.Base("unsigned long", 64, DwarfEncoding.UNSIGNED), 0))),
                ),
            )
            val types = DwarfTypeMapper.map(DebugInfo(listOf(cu)))
            assertEquals(TypeRef.U64, types[0].fields()[0].fieldType())
        }

        @Test
        fun `maps float 32-bit to F32`() {
            val cu = CompileUnit(
                name = "t.c", directory = "", producer = "gcc",
                language = SourceLanguage.C,
                types = listOf(
                    DebugType.Composite("S", 32, CompositeTag.STRUCT,
                        listOf(DebugMember("v", DebugType.Base("float", 32, DwarfEncoding.FLOAT), 0))),
                ),
            )
            val types = DwarfTypeMapper.map(DebugInfo(listOf(cu)))
            assertEquals(TypeRef.F32, types[0].fields()[0].fieldType())
        }

        @Test
        fun `maps float 64-bit to F64`() {
            val cu = CompileUnit(
                name = "t.c", directory = "", producer = "gcc",
                language = SourceLanguage.C,
                types = listOf(
                    DebugType.Composite("S", 64, CompositeTag.STRUCT,
                        listOf(DebugMember("v", DebugType.Base("double", 64, DwarfEncoding.FLOAT), 0))),
                ),
            )
            val types = DwarfTypeMapper.map(DebugInfo(listOf(cu)))
            assertEquals(TypeRef.F64, types[0].fields()[0].fieldType())
        }

        @Test
        fun `maps bool to BOOL`() {
            val cu = CompileUnit(
                name = "t.c", directory = "", producer = "gcc",
                language = SourceLanguage.C,
                types = listOf(
                    DebugType.Composite("S", 8, CompositeTag.STRUCT,
                        listOf(DebugMember("v", DebugType.Base("_Bool", 8, DwarfEncoding.BOOLEAN), 0))),
                ),
            )
            val types = DwarfTypeMapper.map(DebugInfo(listOf(cu)))
            assertEquals(TypeRef.BOOL, types[0].fields()[0].fieldType())
        }

        @Test
        fun `maps address encoding to POINTER`() {
            val cu = CompileUnit(
                name = "t.c", directory = "", producer = "gcc",
                language = SourceLanguage.C,
                types = listOf(
                    DebugType.Composite("S", 64, CompositeTag.STRUCT,
                        listOf(DebugMember("v", DebugType.Base("ptr", 64, DwarfEncoding.ADDRESS), 0))),
                ),
            )
            val types = DwarfTypeMapper.map(DebugInfo(listOf(cu)))
            assertEquals(TypeRef.POINTER, types[0].fields()[0].fieldType())
        }

        @Test
        fun `maps pointer type to pointer TypeRef`() {
            val cu = CompileUnit(
                name = "t.c", directory = "", producer = "gcc",
                language = SourceLanguage.C,
                types = listOf(
                    DebugType.Composite("S", 64, CompositeTag.STRUCT,
                        listOf(DebugMember("v",
                            DebugType.Pointer(DebugType.Base("int", 32, DwarfEncoding.SIGNED), 64), 0))),
                ),
            )
            val types = DwarfTypeMapper.map(DebugInfo(listOf(cu)))
            val field = types[0].fields()[0]
            assertEquals(TypeRef.pointerTo(TypeRef.I32), field.fieldType())
        }

        @Test
        fun `maps reference type to byRef TypeRef`() {
            val cu = CompileUnit(
                name = "t.c", directory = "", producer = "gcc",
                language = SourceLanguage.C,
                types = listOf(
                    DebugType.Composite("S", 64, CompositeTag.STRUCT,
                        listOf(DebugMember("v",
                            DebugType.Reference(DebugType.Base("int", 32, DwarfEncoding.SIGNED), 64), 0))),
                ),
            )
            val types = DwarfTypeMapper.map(DebugInfo(listOf(cu)))
            assertEquals(TypeRef.byRef(TypeRef.I32), types[0].fields()[0].fieldType())
        }

        @Test
        fun `maps array type to array TypeRef`() {
            val cu = CompileUnit(
                name = "t.c", directory = "", producer = "gcc",
                language = SourceLanguage.C,
                types = listOf(
                    DebugType.Composite("S", 320, CompositeTag.STRUCT,
                        listOf(DebugMember("v",
                            DebugType.Array(DebugType.Base("int", 32, DwarfEncoding.SIGNED), 10, 320), 0))),
                ),
            )
            val types = DwarfTypeMapper.map(DebugInfo(listOf(cu)))
            assertEquals(TypeRef.arrayOf(TypeRef.I32), types[0].fields()[0].fieldType())
        }

        @Test
        fun `maps const type by unwrapping`() {
            val cu = CompileUnit(
                name = "t.c", directory = "", producer = "gcc",
                language = SourceLanguage.C,
                types = listOf(
                    DebugType.Composite("S", 32, CompositeTag.STRUCT,
                        listOf(DebugMember("v",
                            DebugType.Const(DebugType.Base("int", 32, DwarfEncoding.SIGNED)), 0))),
                ),
            )
            val types = DwarfTypeMapper.map(DebugInfo(listOf(cu)))
            assertEquals(TypeRef.I32, types[0].fields()[0].fieldType())
        }

        @Test
        fun `maps volatile type by unwrapping`() {
            val cu = CompileUnit(
                name = "t.c", directory = "", producer = "gcc",
                language = SourceLanguage.C,
                types = listOf(
                    DebugType.Composite("S", 32, CompositeTag.STRUCT,
                        listOf(DebugMember("v",
                            DebugType.Volatile(DebugType.Base("int", 32, DwarfEncoding.SIGNED)), 0))),
                ),
            )
            val types = DwarfTypeMapper.map(DebugInfo(listOf(cu)))
            assertEquals(TypeRef.I32, types[0].fields()[0].fieldType())
        }

        @Test
        fun `maps typedef to named TypeRef`() {
            val cu = CompileUnit(
                name = "t.c", directory = "", producer = "gcc",
                language = SourceLanguage.C,
                types = listOf(
                    DebugType.Composite("S", 32, CompositeTag.STRUCT,
                        listOf(DebugMember("v",
                            DebugType.Typedef("size_t", DebugType.Base("unsigned long", 64, DwarfEncoding.UNSIGNED)), 0))),
                ),
            )
            val types = DwarfTypeMapper.map(DebugInfo(listOf(cu)))
            assertEquals(TypeRef.of("size_t"), types[0].fields()[0].fieldType())
        }

        @Test
        fun `maps subroutine type to fn TypeRef`() {
            val cu = CompileUnit(
                name = "t.c", directory = "", producer = "gcc",
                language = SourceLanguage.C,
                types = listOf(
                    DebugType.Composite("S", 64, CompositeTag.STRUCT,
                        listOf(DebugMember("v",
                            DebugType.Subroutine(DebugType.Base("int", 32, DwarfEncoding.SIGNED), emptyList()), 0))),
                ),
            )
            val types = DwarfTypeMapper.map(DebugInfo(listOf(cu)))
            assertEquals(TypeRef.of("fn"), types[0].fields()[0].fieldType())
        }

        @Test
        fun `maps nested composite type to named TypeRef`() {
            val inner = DebugType.Composite("Inner", 32, CompositeTag.STRUCT,
                listOf(DebugMember("x", DebugType.Base("int", 32, DwarfEncoding.SIGNED), 0)))
            val cu = CompileUnit(
                name = "t.c", directory = "", producer = "gcc",
                language = SourceLanguage.C,
                types = listOf(
                    DebugType.Composite("Outer", 64, CompositeTag.STRUCT,
                        listOf(DebugMember("inner", inner, 0))),
                ),
            )
            val types = DwarfTypeMapper.map(DebugInfo(listOf(cu)))
            assertEquals(TypeRef.of("Inner"), types[0].fields()[0].fieldType())
        }

        @Test
        fun `maps enum field type to named TypeRef`() {
            val enumType = DebugType.Enum("Color", 32,
                DebugType.Base("int", 32, DwarfEncoding.SIGNED),
                listOf("RED" to 0L))
            val cu = CompileUnit(
                name = "t.c", directory = "", producer = "gcc",
                language = SourceLanguage.C,
                types = listOf(
                    DebugType.Composite("S", 32, CompositeTag.STRUCT,
                        listOf(DebugMember("c", enumType, 0))),
                ),
            )
            val types = DwarfTypeMapper.map(DebugInfo(listOf(cu)))
            assertEquals(TypeRef.of("Color"), types[0].fields()[0].fieldType())
        }

        @Test
        fun `empty debug info returns empty types`() {
            val types = DwarfTypeMapper.map(DebugInfo(emptyList()))
            assertTrue(types.isEmpty())
        }

        @Test
        fun `compile unit with no types returns empty`() {
            val cu = CompileUnit(
                name = "t.c", directory = "", producer = "gcc",
                language = SourceLanguage.C,
            )
            val types = DwarfTypeMapper.map(DebugInfo(listOf(cu)))
            assertTrue(types.isEmpty())
        }

        @Test
        fun `base types are not mapped as standalone TypeInfo`() {
            val cu = CompileUnit(
                name = "t.c", directory = "", producer = "gcc",
                language = SourceLanguage.C,
                types = listOf(
                    DebugType.Base("int", 32, DwarfEncoding.SIGNED),
                ),
            )
            val types = DwarfTypeMapper.map(DebugInfo(listOf(cu)))
            assertTrue(types.isEmpty())
        }

        @Test
        fun `pointer types are not mapped as standalone TypeInfo`() {
            val cu = CompileUnit(
                name = "t.c", directory = "", producer = "gcc",
                language = SourceLanguage.C,
                types = listOf(
                    DebugType.Pointer(DebugType.Base("int", 32, DwarfEncoding.SIGNED), 64),
                ),
            )
            val types = DwarfTypeMapper.map(DebugInfo(listOf(cu)))
            assertTrue(types.isEmpty())
        }

        @Test
        fun `typedef is resolved through to underlying type`() {
            val cu = CompileUnit(
                name = "t.c", directory = "", producer = "gcc",
                language = SourceLanguage.C,
                types = listOf(
                    DebugType.Typedef("MyInt",
                        DebugType.Composite("Wrapped", 32, CompositeTag.STRUCT,
                            listOf(DebugMember("v", DebugType.Base("int", 32, DwarfEncoding.SIGNED), 0)))),
                ),
            )
            val types = DwarfTypeMapper.map(DebugInfo(listOf(cu)))
            assertEquals(1, types.size)
            assertEquals("Wrapped", types[0].name())
        }

        @Test
        fun `struct size in bytes`() {
            val cu = CompileUnit(
                name = "t.c", directory = "", producer = "gcc",
                language = SourceLanguage.C,
                types = listOf(
                    DebugType.Composite("Big", 256, CompositeTag.STRUCT, emptyList()),
                ),
            )
            val types = DwarfTypeMapper.map(DebugInfo(listOf(cu)))
            assertEquals(32, types[0].size()) // 256 bits / 8 = 32 bytes
        }

        @Test
        fun `maps UTF encoding to U types`() {
            val cu = CompileUnit(
                name = "t.c", directory = "", producer = "gcc",
                language = SourceLanguage.C,
                types = listOf(
                    DebugType.Composite("S", 32, CompositeTag.STRUCT,
                        listOf(DebugMember("c", DebugType.Base("char32_t", 32, DwarfEncoding.UTF), 0))),
                ),
            )
            val types = DwarfTypeMapper.map(DebugInfo(listOf(cu)))
            assertEquals(TypeRef.U32, types[0].fields()[0].fieldType())
        }

        @Test
        fun `multiple compile units aggregated`() {
            val cu1 = CompileUnit(
                name = "a.c", directory = "", producer = "gcc",
                language = SourceLanguage.C,
                types = listOf(
                    DebugType.Composite("TypeA", 32, CompositeTag.STRUCT, emptyList()),
                ),
            )
            val cu2 = CompileUnit(
                name = "b.c", directory = "", producer = "gcc",
                language = SourceLanguage.C,
                types = listOf(
                    DebugType.Composite("TypeB", 64, CompositeTag.STRUCT, emptyList()),
                ),
            )
            val types = DwarfTypeMapper.map(DebugInfo(listOf(cu1, cu2)))
            assertEquals(2, types.size)
            val names = types.map { it.name() }.toSet()
            assertTrue(names.contains("TypeA"))
            assertTrue(names.contains("TypeB"))
        }
    }

    @Nested
    inner class DebugInfoModelTests {

        @Test
        fun `DebugType Base has name and sizeInBits`() {
            val t = DebugType.Base("int", 32, DwarfEncoding.SIGNED)
            assertEquals("int", t.name)
            assertEquals(32, t.sizeInBits)
            assertEquals(DwarfEncoding.SIGNED, t.encoding)
        }

        @Test
        fun `DebugType Pointer name includes star`() {
            val t = DebugType.Pointer(DebugType.Base("int", 32, DwarfEncoding.SIGNED), 64)
            assertEquals("int*", t.name)
            assertEquals(64, t.sizeInBits)
        }

        @Test
        fun `DebugType Reference name includes ampersand`() {
            val t = DebugType.Reference(DebugType.Base("int", 32, DwarfEncoding.SIGNED), 64)
            assertEquals("int&", t.name)
        }

        @Test
        fun `DebugType Array name includes count`() {
            val t = DebugType.Array(DebugType.Base("int", 32, DwarfEncoding.SIGNED), 10, 320)
            assertEquals("int[10]", t.name)
            assertEquals(320, t.sizeInBits)
        }

        @Test
        fun `DebugType Const name includes const prefix`() {
            val t = DebugType.Const(DebugType.Base("int", 32, DwarfEncoding.SIGNED))
            assertEquals("const int", t.name)
            assertEquals(32, t.sizeInBits)
        }

        @Test
        fun `DebugType Volatile name includes volatile prefix`() {
            val t = DebugType.Volatile(DebugType.Base("int", 32, DwarfEncoding.SIGNED))
            assertEquals("volatile int", t.name)
            assertEquals(32, t.sizeInBits)
        }

        @Test
        fun `DebugType Typedef delegates sizeInBits`() {
            val base = DebugType.Base("unsigned long", 64, DwarfEncoding.UNSIGNED)
            val t = DebugType.Typedef("size_t", base)
            assertEquals("size_t", t.name)
            assertEquals(64, t.sizeInBits)
        }

        @Test
        fun `DebugType Subroutine name describes signature`() {
            val ret = DebugType.Base("int", 32, DwarfEncoding.SIGNED)
            val params = listOf(
                DebugType.Base("int", 32, DwarfEncoding.SIGNED),
                DebugType.Base("float", 32, DwarfEncoding.FLOAT),
            )
            val t = DebugType.Subroutine(ret, params)
            assertEquals("(int, float) -> int", t.name)
            assertEquals(0, t.sizeInBits)
        }

        @Test
        fun `DebugType Subroutine with no return type shows void`() {
            val t = DebugType.Subroutine(null, emptyList())
            assertEquals("() -> void", t.name)
        }

        @Test
        fun `DebugMember default values`() {
            val m = DebugMember("field", DebugType.Base("int", 32, DwarfEncoding.SIGNED), 0)
            assertEquals("field", m.name)
            assertEquals(0, m.offsetInBits)
            assertEquals(32, m.sizeInBits)
            assertEquals(DebugAccessibility.PUBLIC, m.accessibility)
            assertFalse(m.isStatic)
            assertFalse(m.isVirtual)
            assertFalse(m.isBitField)
        }

        @Test
        fun `DebugMember with private accessibility`() {
            val m = DebugMember("field", DebugType.Base("int", 32, DwarfEncoding.SIGNED), 0,
                accessibility = DebugAccessibility.PRIVATE)
            assertEquals(DebugAccessibility.PRIVATE, m.accessibility)
        }

        @Test
        fun `DebugFormat enum values exist`() {
            assertNotNull(DebugFormat.DWARF4)
            assertNotNull(DebugFormat.DWARF5)
            assertNotNull(DebugFormat.CODEVIEW)
            assertNotNull(DebugFormat.STABS)
        }

        @Test
        fun `CompositeTag enum values exist`() {
            assertNotNull(CompositeTag.STRUCT)
            assertNotNull(CompositeTag.CLASS)
            assertNotNull(CompositeTag.UNION)
            assertNotNull(CompositeTag.INTERFACE)
        }

        @Test
        fun `DwarfEncoding enum values exist`() {
            assertEquals(8, DwarfEncoding.entries.size)
            assertNotNull(DwarfEncoding.ADDRESS)
            assertNotNull(DwarfEncoding.BOOLEAN)
            assertNotNull(DwarfEncoding.FLOAT)
            assertNotNull(DwarfEncoding.SIGNED)
            assertNotNull(DwarfEncoding.UNSIGNED)
            assertNotNull(DwarfEncoding.SIGNED_CHAR)
            assertNotNull(DwarfEncoding.UNSIGNED_CHAR)
            assertNotNull(DwarfEncoding.UTF)
        }

        @Test
        fun `DebugAccessibility enum values exist`() {
            assertEquals(3, DebugAccessibility.entries.size)
        }

        @Test
        fun `SourceLanguage has key entries`() {
            val langs = SourceLanguage.entries
            assertTrue(langs.contains(SourceLanguage.C))
            assertTrue(langs.contains(SourceLanguage.C_PLUS_PLUS))
            assertTrue(langs.contains(SourceLanguage.RUST))
            assertTrue(langs.contains(SourceLanguage.JAVA))
            assertTrue(langs.contains(SourceLanguage.KOTLIN))
            assertTrue(langs.contains(SourceLanguage.GO))
            assertTrue(langs.contains(SourceLanguage.SWIFT))
            assertTrue(langs.contains(SourceLanguage.PYTHON))
            assertTrue(langs.contains(SourceLanguage.ZIG))
            assertTrue(langs.contains(SourceLanguage.CUSTOM))
        }

        @Test
        fun `DebugInfo default values`() {
            val di = DebugInfo()
            assertTrue(di.compileUnits.isEmpty())
            assertEquals(DebugFormat.DWARF5, di.format)
        }

        @Test
        fun `CompileUnit default values`() {
            val cu = CompileUnit("test.c", "/tmp", "gcc", SourceLanguage.C)
            assertTrue(cu.lineInfo.isEmpty())
            assertTrue(cu.types.isEmpty())
            assertTrue(cu.variables.isEmpty())
            assertTrue(cu.subprograms.isEmpty())
            assertEquals(0L, cu.lowPC)
            assertEquals(0L, cu.highPC)
        }

        @Test
        fun `DebugSubprogram default values`() {
            val sp = DebugSubprogram("func", file = "test.c", line = 10, returnType = null, params = emptyList())
            assertNull(sp.linkageName)
            assertTrue(sp.localVariables.isEmpty())
            assertEquals(0L, sp.lowPC)
            assertEquals(0L, sp.highPC)
            assertTrue(sp.isDefinition)
            assertFalse(sp.isInlined)
            assertNull(sp.inlinedAt)
        }

        @Test
        fun `DebugVariable default values`() {
            val v = DebugVariable("x", DebugType.Base("int", 32, DwarfEncoding.SIGNED))
            assertNull(v.file)
            assertEquals(0, v.line)
            assertNull(v.location)
            assertFalse(v.isParameter)
            assertFalse(v.isArtificial)
        }
    }
}
