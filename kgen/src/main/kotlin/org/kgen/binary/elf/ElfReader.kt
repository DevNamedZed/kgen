package org.kgen.binary.elf

import org.kgen.binary.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Reads ELF64 binaries into a structured [ElfFile] model.
 *
 * ```kotlin
 * val elf = ElfReader.read(bytes)
 * println("Type: ${elf.header.type}")
 * for (s in elf.sections) println("${s.name}: ${s.size} bytes")
 * ```
 *
 * For the universal [ObjectFile] model, use [toObjectFile].
 */
object ElfReader {

    @JvmStatic
    fun canRead(bytes: ByteArray): Boolean {
        return bytes.size >= 16 &&
            bytes[0] == Elf.MAGIC[0] && bytes[1] == Elf.MAGIC[1] &&
            bytes[2] == Elf.MAGIC[2] && bytes[3] == Elf.MAGIC[3] &&
            bytes[4].toInt() and 0xFF == ElfClass.ELF64.code
    }

    @JvmStatic
    fun read(bytes: ByteArray): ElfFile {
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return ElfFileParser(buf, bytes).parse()
    }

    @JvmStatic
    fun toObjectFile(elf: ElfFile): ObjectFile = ElfObjectFileProjection.project(elf)
}

/**
 * Adapter that implements [ObjectFileReader] for integration with format-agnostic code.
 */
class ElfObjectFileReader : ObjectFileReader {
    override val format: ObjectFormat = ObjectFormat.ELF

    override fun canRead(bytes: ByteArray): Boolean = ElfReader.canRead(bytes)

    override fun read(bytes: ByteArray): ObjectFile = ElfReader.toObjectFile(ElfReader.read(bytes))
}

private class ElfFileParser(private val buf: ByteBuffer, private val raw: ByteArray) {

    private lateinit var header: ElfHeader
    private val rawShdrs = mutableListOf<RawShdr>()
    private val sectionNames = mutableMapOf<Int, String>()

    private data class RawShdr(
        val nameIdx: Int, val type: Int, val flags: Long, val addr: Long,
        val offset: Long, val size: Long, val link: Int, val info: Int,
        val addralign: Long, val entsize: Long,
    )

    fun parse(): ElfFile {
        header = parseHeader()
        parseRawSectionHeaders()
        resolveSectionNames()

        val sections = buildSections()
        val segments = buildSegments()
        val symbols = buildSymbolEntries(ElfSectionType.SYMTAB)
        val dynamicSymbols = buildSymbolEntries(ElfSectionType.DYNSYM)
        val relocations = buildRelocationEntries()
        val dynamicInfo = buildElfDynamicInfo()

        return ElfFile(
            header = header,
            sections = sections,
            segments = segments,
            symbols = symbols,
            dynamicSymbols = dynamicSymbols,
            relocations = relocations,
            dynamicInfo = dynamicInfo,
        )
    }

    private fun parseHeader(): ElfHeader {
        check(raw[0] == Elf.MAGIC[0] && raw[1] == Elf.MAGIC[1] &&
              raw[2] == Elf.MAGIC[2] && raw[3] == Elf.MAGIC[3]) { "Invalid ELF magic" }
        val elfClass = ElfClass.fromCode(raw[4].toInt() and 0xFF)
        check(elfClass == ElfClass.ELF64) { "Only ELF64 is supported" }
        val dataEncoding = ElfData.fromCode(raw[5].toInt() and 0xFF)
        check(dataEncoding == ElfData.LSB) { "Only little-endian ELF is supported" }

        return ElfHeader(
            elfClass = elfClass,
            dataEncoding = dataEncoding,
            osAbi = raw[7].toInt() and 0xFF,
            type = ElfObjectType.fromCode(buf.getShort(16).toInt() and 0xFFFF),
            machine = ElfMachine.fromCode(buf.getShort(18).toInt() and 0xFFFF),
            entryPoint = buf.getLong(24),
            programHeaderOffset = buf.getLong(32),
            sectionHeaderOffset = buf.getLong(40),
            flags = buf.getInt(48),
            programHeaderCount = buf.getShort(56).toInt() and 0xFFFF,
            sectionHeaderCount = buf.getShort(60).toInt() and 0xFFFF,
            sectionNameStringTableIndex = buf.getShort(62).toInt() and 0xFFFF,
        )
    }

    private fun parseRawSectionHeaders() {
        val base = header.sectionHeaderOffset.toInt()
        for (i in 0 until header.sectionHeaderCount) {
            val off = base + i * Elf.SHDR64_SIZE
            rawShdrs.add(RawShdr(
                nameIdx = buf.getInt(off),
                type = buf.getInt(off + 4),
                flags = buf.getLong(off + 8),
                addr = buf.getLong(off + 16),
                offset = buf.getLong(off + 24),
                size = buf.getLong(off + 32),
                link = buf.getInt(off + 40),
                info = buf.getInt(off + 44),
                addralign = buf.getLong(off + 48),
                entsize = buf.getLong(off + 56),
            ))
        }
    }

    private fun resolveSectionNames() {
        val idx = header.sectionNameStringTableIndex
        if (idx == 0 || idx >= rawShdrs.size) return
        val shstrtab = rawShdrs[idx]
        for ((i, shdr) in rawShdrs.withIndex()) {
            sectionNames[i] = readString(shstrtab.offset.toInt() + shdr.nameIdx)
        }
    }

    private fun buildSections(): List<ElfSectionEntry> {
        return rawShdrs.mapIndexed { i, shdr ->
            val data = if (shdr.type == ElfSectionType.NOBITS.code) {
                ByteArray(shdr.size.toInt())
            } else {
                extractBytes(shdr.offset.toInt(), shdr.size.toInt())
            }
            ElfSectionEntry(
                index = i,
                name = sectionNames[i] ?: "",
                type = ElfSectionType.fromCode(shdr.type),
                flags = shdr.flags,
                address = shdr.addr,
                offset = shdr.offset,
                size = shdr.size,
                link = shdr.link,
                info = shdr.info,
                alignment = shdr.addralign,
                entrySize = shdr.entsize,
                data = data,
            )
        }
    }

    private fun buildSegments(): List<ElfProgramHeader> {
        if (header.programHeaderOffset == 0L || header.programHeaderCount == 0) return emptyList()
        val base = header.programHeaderOffset.toInt()
        return (0 until header.programHeaderCount).map { i ->
            val off = base + i * Elf.PHDR64_SIZE
            ElfProgramHeader(
                type = ElfSegmentType.fromCode(buf.getInt(off)),
                flags = buf.getInt(off + 4),
                offset = buf.getLong(off + 8),
                virtualAddress = buf.getLong(off + 16),
                physicalAddress = buf.getLong(off + 24),
                fileSize = buf.getLong(off + 32),
                memorySize = buf.getLong(off + 40),
                alignment = buf.getLong(off + 48),
            )
        }
    }

    private fun buildSymbolEntries(targetType: ElfSectionType): List<ElfSymbolEntry> {
        val result = mutableListOf<ElfSymbolEntry>()
        for (shdr in rawShdrs) {
            if (shdr.type != targetType.code) continue
            val strtabIdx = shdr.link
            val entCount = if (shdr.entsize > 0) (shdr.size / shdr.entsize).toInt() else 0

            for (j in 1 until entCount) {
                val off = shdr.offset.toInt() + j * Elf.SYM64_SIZE
                val nameIdx = buf.getInt(off)
                val info = raw[off + 4].toInt() and 0xFF
                val other = raw[off + 5].toInt() and 0xFF
                val shndx = buf.getShort(off + 6).toInt() and 0xFFFF
                val value = buf.getLong(off + 8)
                val size = buf.getLong(off + 16)

                val sectionName = when {
                    shndx == Elf.SHN_UNDEF || shndx == Elf.SHN_ABS -> null
                    shndx < rawShdrs.size -> sectionNames[shndx]
                    else -> null
                }

                result.add(ElfSymbolEntry(
                    name = readStringFromSection(strtabIdx, nameIdx),
                    value = value,
                    size = size,
                    binding = ElfSymbolBinding.fromCode(info shr 4),
                    type = ElfSymbolType.fromCode(info and 0xF),
                    visibility = ElfSymbolVisibility.fromCode(other and 0x3),
                    sectionIndex = shndx,
                    sectionName = sectionName,
                ))
            }
        }
        return result
    }

    private fun buildRelocationEntries(): List<ElfRelocationEntry> {
        val result = mutableListOf<ElfRelocationEntry>()
        for (shdr in rawShdrs) {
            val stype = ElfSectionType.fromCode(shdr.type) ?: continue
            if (stype != ElfSectionType.RELA && stype != ElfSectionType.REL) continue
            val hasAddend = stype == ElfSectionType.RELA

            val targetSectionName = if (shdr.info > 0 && shdr.info < rawShdrs.size) {
                sectionNames[shdr.info]
            } else null

            val symtabShdr = if (shdr.link > 0 && shdr.link < rawShdrs.size) rawShdrs[shdr.link] else continue
            val symStrtabIdx = symtabShdr.link

            val entSize = if (hasAddend) Elf.RELA64_SIZE else 16
            val entCount = if (entSize > 0) (shdr.size / entSize).toInt() else 0

            for (j in 0 until entCount) {
                val off = shdr.offset.toInt() + j * entSize
                val rOffset = buf.getLong(off)
                val rInfo = buf.getLong(off + 8)
                val rAddend = if (hasAddend) buf.getLong(off + 16) else 0L

                val symIdx = (rInfo shr 32).toInt()
                val rType = (rInfo and 0xFFFFFFFFL).toInt()

                val symName = if (symIdx > 0) {
                    val symOff = symtabShdr.offset.toInt() + symIdx * Elf.SYM64_SIZE
                    val symNameIdx = buf.getInt(symOff)
                    readStringFromSection(symStrtabIdx, symNameIdx)
                } else ""

                result.add(ElfRelocationEntry(
                    offset = rOffset,
                    symbolIndex = symIdx,
                    symbolName = symName,
                    type = rType,
                    addend = rAddend,
                    sectionName = targetSectionName,
                    hasAddend = hasAddend,
                ))
            }
        }
        return result
    }

    private fun buildElfDynamicInfo(): ElfDynamicInfo? {
        val dynShdr = rawShdrs.firstOrNull { it.type == ElfSectionType.DYNAMIC.code } ?: return null
        val dynStrtabIdx = dynShdr.link

        val needed = mutableListOf<String>()
        var soName: String? = null
        val rpath = mutableListOf<String>()
        val runpath = mutableListOf<String>()
        val entries = mutableListOf<ElfDynamicEntry>()

        val entSize = if (dynShdr.entsize > 0) dynShdr.entsize.toInt() else 16
        val entCount = (dynShdr.size / entSize).toInt()

        for (j in 0 until entCount) {
            val off = dynShdr.offset.toInt() + j * entSize
            val tagCode = buf.getLong(off)
            val value = buf.getLong(off + 8)
            val tag = ElfDynamicTag.fromCode(tagCode)

            entries.add(ElfDynamicEntry(tag = tag, tagCode = tagCode, value = value))

            when (tag) {
                ElfDynamicTag.NULL -> break
                ElfDynamicTag.NEEDED -> needed.add(readStringFromSection(dynStrtabIdx, value.toInt()))
                ElfDynamicTag.SONAME -> soName = readStringFromSection(dynStrtabIdx, value.toInt())
                ElfDynamicTag.RPATH -> rpath.addAll(readStringFromSection(dynStrtabIdx, value.toInt()).split(':'))
                ElfDynamicTag.RUNPATH -> runpath.addAll(readStringFromSection(dynStrtabIdx, value.toInt()).split(':'))
                else -> {}
            }
        }

        return ElfDynamicInfo(
            neededLibraries = needed,
            soName = soName,
            rpath = rpath,
            runpath = runpath,
            entries = entries,
        )
    }

    private fun readString(offset: Int): String {
        if (offset >= raw.size) return ""
        var end = raw.size
        for (k in offset until raw.size) {
            if (raw[k] == 0.toByte()) { end = k; break }
        }
        return String(raw, offset, end - offset, Charsets.US_ASCII)
    }

    private fun readStringFromSection(sectionIdx: Int, nameOffset: Int): String {
        if (sectionIdx <= 0 || sectionIdx >= rawShdrs.size) return ""
        val strtab = rawShdrs[sectionIdx]
        return readString(strtab.offset.toInt() + nameOffset)
    }

    private fun extractBytes(offset: Int, size: Int): ByteArray {
        if (offset < 0 || offset + size > raw.size) return ByteArray(0)
        return raw.copyOfRange(offset, offset + size)
    }
}
