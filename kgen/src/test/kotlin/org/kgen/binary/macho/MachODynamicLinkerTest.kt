package org.kgen.binary.macho

import org.kgen.binary.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MachODynamicLinkerTest {

    private fun le(bytes: ByteArray) = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    private fun readU32(buf: ByteBuffer, off: Int): Int = buf.getInt(off)
    private fun readU64(buf: ByteBuffer, off: Int): Long = buf.getLong(off)

    private fun simpleObj(code: ByteArray = byteArrayOf(0xC3.toByte()),
                          symbolName: String = "_main",
                          imports: List<String> = emptyList()): ObjectFile {
        val syms = mutableListOf(
            Symbol(symbolName, value = 0, section = ".text",
                binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
        )
        for (imp in imports) {
            syms.add(Symbol(imp, kind = SymbolKind.UNDEFINED))
        }
        return ObjectFile(
            format = ObjectFormat.MACH_O,
            arch = Architecture(ArchType.X86_64),
            sections = listOf(Section(".text", SectionKind.TEXT, code, align = 16)),
            symbols = syms,
            relocations = emptyList(),
        )
    }

    @Test
    fun producesMachOExecutable() {
        val obj = simpleObj()
        val binary = MachODynamicLinker().link(listOf(obj))
        val buf = le(binary)

        assertEquals(MachO.MH_MAGIC_64.toInt(), readU32(buf, 0))
        assertEquals(MachO.CPU_TYPE_X86_64, readU32(buf, 4))
        assertEquals(MachO.MH_EXECUTE, readU32(buf, 12))
    }

    @Test
    fun hasDyldLinkFlags() {
        val obj = simpleObj()
        val binary = MachODynamicLinker().link(listOf(obj))
        val buf = le(binary)

        val flags = readU32(buf, 24)
        assertTrue(flags and MachO.MH_DYLDLINK != 0, "Should have MH_DYLDLINK")
        assertTrue(flags and MachO.MH_TWOLEVEL != 0, "Should have MH_TWOLEVEL")
        assertTrue(flags and MachO.MH_PIE != 0, "Should have MH_PIE")
    }

    @Test
    fun hasPagezeroAndTextSegments() {
        val obj = simpleObj()
        val binary = MachODynamicLinker().link(listOf(obj))
        val buf = le(binary)

        val ncmds = readU32(buf, 16)
        assertTrue(ncmds >= 2, "Should have at least 2 load commands (PAGEZERO + TEXT)")

        // First load command should be __PAGEZERO
        val firstCmd = readU32(buf, 32)
        assertEquals(MachO.LC_SEGMENT_64, firstCmd)
        val segName = ByteArray(16)
        buf.position(40)
        buf.get(segName)
        assertTrue(String(segName, Charsets.US_ASCII).trimEnd('\u0000') == "__PAGEZERO")
    }

    @Test
    fun hasLoadDylinkerCommand() {
        val obj = simpleObj()
        val binary = MachODynamicLinker().link(listOf(obj))
        val found = findLoadCommand(binary, MachO.LC_LOAD_DYLINKER)
        assertTrue(found, "Should have LC_LOAD_DYLINKER")
    }

    @Test
    fun hasLoadDylibCommand() {
        val obj = simpleObj()
        val binary = MachODynamicLinker(
            sharedLibs = listOf("/usr/lib/libSystem.B.dylib"),
        ).link(listOf(obj))
        val found = findLoadCommand(binary, MachO.LC_LOAD_DYLIB)
        assertTrue(found, "Should have LC_LOAD_DYLIB")
    }

    @Test
    fun hasMultipleLoadDylibCommands() {
        val obj = simpleObj()
        val binary = MachODynamicLinker(
            sharedLibs = listOf("/usr/lib/libSystem.B.dylib", "/usr/lib/libc++.1.dylib"),
        ).link(listOf(obj))
        val count = countLoadCommands(binary, MachO.LC_LOAD_DYLIB)
        assertEquals(2, count, "Should have 2 LC_LOAD_DYLIB commands")
    }

    @Test
    fun hasMainCommand() {
        val obj = simpleObj()
        val binary = MachODynamicLinker().link(listOf(obj))
        val found = findLoadCommand(binary, MachO.LC_MAIN)
        assertTrue(found, "Should have LC_MAIN")
    }

    @Test
    fun hasSymtabAndDysymtab() {
        val obj = simpleObj()
        val binary = MachODynamicLinker().link(listOf(obj))
        assertTrue(findLoadCommand(binary, MachO.LC_SYMTAB), "Should have LC_SYMTAB")
        assertTrue(findLoadCommand(binary, MachO.LC_DYSYMTAB), "Should have LC_DYSYMTAB")
    }

    @Test
    fun importsProduceChainedFixupsCommand() {
        val obj = simpleObj(imports = listOf("_printf"))
        val binary = MachODynamicLinker().link(listOf(obj))
        val found = findLoadCommand(binary, MachO.LC_DYLD_CHAINED_FIXUPS)
        assertTrue(found, "Should have LC_DYLD_CHAINED_FIXUPS when imports exist")
    }

    @Test
    fun noImportsNoChainedFixups() {
        val obj = simpleObj()
        val binary = MachODynamicLinker().link(listOf(obj))
        val found = findLoadCommand(binary, MachO.LC_DYLD_CHAINED_FIXUPS)
        assertFalse(found, "Should NOT have LC_DYLD_CHAINED_FIXUPS when no imports")
    }

    @Test
    fun hasStubsSection() {
        val obj = simpleObj(imports = listOf("_printf"))
        val binary = MachODynamicLinker().link(listOf(obj))

        // Scan for __stubs section name in load commands
        val stubsFound = findSectionName(binary, "__stubs")
        assertTrue(stubsFound, "Should have __stubs section when imports exist")
    }

    @Test
    fun hasGotSection() {
        val obj = simpleObj(imports = listOf("_printf"))
        val binary = MachODynamicLinker().link(listOf(obj))

        val gotFound = findSectionName(binary, "__got")
        assertTrue(gotFound, "Should have __got section when imports exist")
    }

    @Test
    fun relocationsResolveToStubs() {
        // Code that calls _printf via PLT32 relocation
        val code = byteArrayOf(
            0xE8.toByte(), 0x00, 0x00, 0x00, 0x00, // call _printf (placeholder)
            0xC3.toByte(), // ret
        )
        val obj = ObjectFile(
            format = ObjectFormat.MACH_O,
            arch = Architecture(ArchType.X86_64),
            sections = listOf(Section(".text", SectionKind.TEXT, code, align = 16)),
            symbols = listOf(
                Symbol("_main", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
                Symbol("_printf", kind = SymbolKind.UNDEFINED),
            ),
            relocations = listOf(
                Relocation(
                    offset = 1, symbol = "_printf", type = RelocationType.X86_64.PLT32,
                    addend = -4, section = ".text",
                ),
            ),
        )

        val binary = MachODynamicLinker().link(listOf(obj))
        val buf = le(binary)

        // The call instruction should have been patched with a non-zero displacement
        // (pointing to the stub, not the original zero)
        val textOffset = findTextSectionOffset(binary)
        val callDisp = readU32(buf, textOffset + 1)
        assertNotEquals(0, callDisp, "Call displacement should be patched to point to stub")
    }

    @Test
    fun arm64LinkProducesMachO() {
        val code = byteArrayOf(
            0xC0.toByte(), 0x03, 0x5F, 0xD6.toByte(), // ret (ARM64)
        )
        val obj = ObjectFile(
            format = ObjectFormat.MACH_O,
            arch = Architecture(ArchType.AARCH64),
            sections = listOf(Section(".text", SectionKind.TEXT, code, align = 16)),
            symbols = listOf(
                Symbol("_main", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
            relocations = emptyList(),
        )

        val binary = MachODynamicLinker(
            cpuType = MachO.CPU_TYPE_ARM64,
        ).link(listOf(obj))
        val buf = le(binary)

        assertEquals(MachO.MH_MAGIC_64.toInt(), readU32(buf, 0))
        assertEquals(MachO.CPU_TYPE_ARM64, readU32(buf, 4))
    }

    @Test
    fun multipleImportsProduceMultipleStubs() {
        val obj = simpleObj(imports = listOf("_printf", "_malloc", "_free"))
        val binary = MachODynamicLinker().link(listOf(obj))

        // Should have __stubs and __got sections
        assertTrue(findSectionName(binary, "__stubs"))
        assertTrue(findSectionName(binary, "__got"))

        // LC_DYLD_CHAINED_FIXUPS should be present
        assertTrue(findLoadCommand(binary, MachO.LC_DYLD_CHAINED_FIXUPS))
    }

    @Test
    fun withDataSection() {
        val code = byteArrayOf(0xC3.toByte())
        val data = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val obj = ObjectFile(
            format = ObjectFormat.MACH_O,
            arch = Architecture(ArchType.X86_64),
            sections = listOf(
                Section(".text", SectionKind.TEXT, code, align = 16),
                Section(".data", SectionKind.DATA, data, align = 4),
            ),
            symbols = listOf(
                Symbol("_main", value = 0, section = ".text",
                    binding = SymbolBinding.GLOBAL, kind = SymbolKind.FUNCTION),
            ),
            relocations = emptyList(),
        )

        val binary = MachODynamicLinker().link(listOf(obj))
        assertTrue(binary.isNotEmpty())
        assertTrue(findSectionName(binary, "__data"))
    }

    // -- Helpers --

    private fun findLoadCommand(binary: ByteArray, cmdType: Int): Boolean {
        return countLoadCommands(binary, cmdType) > 0
    }

    private fun countLoadCommands(binary: ByteArray, cmdType: Int): Int {
        val buf = le(binary)
        val ncmds = readU32(buf, 16)
        var off = 32 // after header
        var count = 0
        for (i in 0 until ncmds) {
            if (off + 8 > binary.size) { break }
            val cmd = readU32(buf, off)
            val size = readU32(buf, off + 4)
            if (cmd == cmdType) { count++ }
            off += size
        }
        return count
    }

    private fun findSectionName(binary: ByteArray, name: String): Boolean {
        val target = name.toByteArray(Charsets.US_ASCII)
        for (i in 0 until binary.size - target.size) {
            var match = true
            for (j in target.indices) {
                if (binary[i + j] != target[j]) {
                    match = false
                    break
                }
            }
            if (match && (i + target.size >= binary.size || binary[i + target.size] == 0.toByte())) {
                return true
            }
        }
        return false
    }

    private fun findTextSectionOffset(binary: ByteArray): Int {
        val buf = le(binary)
        val ncmds = readU32(buf, 16)
        var off = 32
        for (i in 0 until ncmds) {
            if (off + 8 > binary.size) { break }
            val cmd = readU32(buf, off)
            val size = readU32(buf, off + 4)
            if (cmd == MachO.LC_SEGMENT_64) {
                val segName = ByteArray(16)
                buf.position(off + 8)
                buf.get(segName)
                val name = String(segName, Charsets.US_ASCII).trimEnd('\u0000')
                if (name == "__TEXT") {
                    val nsects = readU32(buf, off + 64)
                    var sectOff = off + 72
                    for (s in 0 until nsects) {
                        val sectNameBytes = ByteArray(16)
                        buf.position(sectOff)
                        buf.get(sectNameBytes)
                        val sectName = String(sectNameBytes, Charsets.US_ASCII).trimEnd('\u0000')
                        if (sectName == "__text") {
                            return readU32(buf, sectOff + 48) // offset field
                        }
                        sectOff += 80
                    }
                }
            }
            off += size
        }
        return -1
    }
}
