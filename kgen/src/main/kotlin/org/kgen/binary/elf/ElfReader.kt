package org.kgen.binary.elf

import org.kgen.binary.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Reads ELF binaries (both ELF32 and ELF64) into a structured [ElfFile] model.
 *
 * Parses all major ELF structures: file header, section headers, program headers (segments),
 * symbol tables (SYMTAB + DYNSYM), relocations (REL + RELA), and the DYNAMIC segment.
 * Both little-endian and big-endian byte orders are detected automatically.
 *
 * ```java
 * ElfFile elf = ElfReader.read(bytes);
 * System.out.println("Type: " + elf.getHeader().getType());
 * for (ElfSectionEntry s : elf.getSections()) {
 *     System.out.println(s.getName() + ": " + s.getSize() + " bytes");
 * }
 * ```
 *
 * For the universal [ObjectFile] model, use [toObjectFile].
 *
 * See `spec/object-formats.md` for the object format model specification.
 */
object ElfReader {

    @JvmStatic
    fun canRead(bytes: ByteArray): Boolean {
        return bytes.size >= 16 &&
            bytes[0] == Elf.MAGIC[0] && bytes[1] == Elf.MAGIC[1] &&
            bytes[2] == Elf.MAGIC[2] && bytes[3] == Elf.MAGIC[3] &&
            (bytes[4].toInt() and 0xFF).let { it == ElfClass.ELF32.code || it == ElfClass.ELF64.code }
    }

    @JvmStatic
    fun read(bytes: ByteArray): ElfFile {
        check(bytes.size >= 6) { "File too short for ELF header" }
        val order = if ((bytes[5].toInt() and 0xFF) == ElfData.MSB.code) ByteOrder.BIG_ENDIAN else ByteOrder.LITTLE_ENDIAN
        val buf = ByteBuffer.wrap(bytes).order(order)
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

/**
 * Internal parser that walks the raw ELF byte buffer in multiple passes:
 * header, section headers, section names, sections, segments, symbols, relocations, dynamic info.
 */
private class ElfFileParser(private val buf: ByteBuffer, private val raw: ByteArray) {

    private lateinit var header: ElfHeader
    private var is32 = false
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
        check(elfClass == ElfClass.ELF32 || elfClass == ElfClass.ELF64) { "Invalid ELF class: ${raw[4]}" }
        is32 = elfClass == ElfClass.ELF32
        val dataEncoding = ElfData.fromCode(raw[5].toInt() and 0xFF)
        check(dataEncoding == ElfData.LSB || dataEncoding == ElfData.MSB) { "Invalid ELF data encoding: ${raw[5]}" }

        return if (is32) parseHeader32(elfClass!!, dataEncoding!!) else parseHeader64(elfClass!!, dataEncoding!!)
    }

    private fun parseHeader32(elfClass: ElfClass, dataEncoding: ElfData): ElfHeader {
        return ElfHeader(
            elfClass = elfClass,
            dataEncoding = dataEncoding,
            osAbi = raw[7].toInt() and 0xFF,
            type = ElfObjectType.fromCode(buf.getShort(16).toInt() and 0xFFFF),
            machine = ElfMachine.fromCode(buf.getShort(18).toInt() and 0xFFFF),
            entryPoint = buf.getInt(24).toLong() and 0xFFFFFFFFL,
            programHeaderOffset = buf.getInt(28).toLong() and 0xFFFFFFFFL,
            sectionHeaderOffset = buf.getInt(32).toLong() and 0xFFFFFFFFL,
            flags = buf.getInt(36),
            programHeaderCount = buf.getShort(44).toInt() and 0xFFFF,
            sectionHeaderCount = buf.getShort(48).toInt() and 0xFFFF,
            sectionNameStringTableIndex = buf.getShort(50).toInt() and 0xFFFF,
        )
    }

    private fun parseHeader64(elfClass: ElfClass, dataEncoding: ElfData): ElfHeader {
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
        val entrySize = if (is32) Elf.SHDR32_SIZE else Elf.SHDR64_SIZE
        for (i in 0 until header.sectionHeaderCount) {
            val off = base + i * entrySize
            if (is32) {
                rawShdrs.add(RawShdr(
                    nameIdx = buf.getInt(off),
                    type = buf.getInt(off + 4),
                    flags = buf.getInt(off + 8).toLong() and 0xFFFFFFFFL,
                    addr = buf.getInt(off + 12).toLong() and 0xFFFFFFFFL,
                    offset = buf.getInt(off + 16).toLong() and 0xFFFFFFFFL,
                    size = buf.getInt(off + 20).toLong() and 0xFFFFFFFFL,
                    link = buf.getInt(off + 24),
                    info = buf.getInt(off + 28),
                    addralign = buf.getInt(off + 32).toLong() and 0xFFFFFFFFL,
                    entsize = buf.getInt(off + 36).toLong() and 0xFFFFFFFFL,
                ))
            } else {
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
            if (is32) parseSegment32(base, i) else parseSegment64(base, i)
        }
    }

    private fun parseSegment32(base: Int, i: Int): ElfProgramHeader {
        val off = base + i * Elf.PHDR32_SIZE
        return ElfProgramHeader(
            type = ElfSegmentType.fromCode(buf.getInt(off)),
            flags = buf.getInt(off + 24),
            offset = buf.getInt(off + 4).toLong() and 0xFFFFFFFFL,
            virtualAddress = buf.getInt(off + 8).toLong() and 0xFFFFFFFFL,
            physicalAddress = buf.getInt(off + 12).toLong() and 0xFFFFFFFFL,
            fileSize = buf.getInt(off + 16).toLong() and 0xFFFFFFFFL,
            memorySize = buf.getInt(off + 20).toLong() and 0xFFFFFFFFL,
            alignment = buf.getInt(off + 28).toLong() and 0xFFFFFFFFL,
        )
    }

    private fun parseSegment64(base: Int, i: Int): ElfProgramHeader {
        val off = base + i * Elf.PHDR64_SIZE
        return ElfProgramHeader(
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

    private fun buildSymbolEntries(targetType: ElfSectionType): List<ElfSymbolEntry> {
        val result = mutableListOf<ElfSymbolEntry>()
        val symSize = if (is32) Elf.SYM32_SIZE else Elf.SYM64_SIZE
        for (shdr in rawShdrs) {
            if (shdr.type != targetType.code) continue
            val strtabIdx = shdr.link
            val entCount = if (shdr.entsize > 0) (shdr.size / shdr.entsize).toInt() else 0

            for (j in 1 until entCount) {
                val off = shdr.offset.toInt() + j * symSize
                val entry = if (is32) parseSymbol32(off) else parseSymbol64(off)

                val sectionName = when {
                    entry.shndx == Elf.SHN_UNDEF || entry.shndx == Elf.SHN_ABS -> null
                    entry.shndx < rawShdrs.size -> sectionNames[entry.shndx]
                    else -> null
                }

                result.add(ElfSymbolEntry(
                    name = readStringFromSection(strtabIdx, entry.nameIdx),
                    value = entry.value,
                    size = entry.size,
                    binding = ElfSymbolBinding.fromCode(entry.info shr 4),
                    type = ElfSymbolType.fromCode(entry.info and 0xF),
                    visibility = ElfSymbolVisibility.fromCode(entry.other and 0x3),
                    sectionIndex = entry.shndx,
                    sectionName = sectionName,
                ))
            }
        }
        return result
    }

    private data class RawSymbol(val nameIdx: Int, val info: Int, val other: Int, val shndx: Int, val value: Long, val size: Long)

    private fun parseSymbol32(off: Int): RawSymbol {
        return RawSymbol(
            nameIdx = buf.getInt(off),
            value = buf.getInt(off + 4).toLong() and 0xFFFFFFFFL,
            size = buf.getInt(off + 8).toLong() and 0xFFFFFFFFL,
            info = raw[off + 12].toInt() and 0xFF,
            other = raw[off + 13].toInt() and 0xFF,
            shndx = buf.getShort(off + 14).toInt() and 0xFFFF,
        )
    }

    private fun parseSymbol64(off: Int): RawSymbol {
        return RawSymbol(
            nameIdx = buf.getInt(off),
            info = raw[off + 4].toInt() and 0xFF,
            other = raw[off + 5].toInt() and 0xFF,
            shndx = buf.getShort(off + 6).toInt() and 0xFFFF,
            value = buf.getLong(off + 8),
            size = buf.getLong(off + 16),
        )
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

            val entSize = resolveRelocationEntrySize(hasAddend)
            val entCount = if (entSize > 0) (shdr.size / entSize).toInt() else 0

            for (j in 0 until entCount) {
                val off = shdr.offset.toInt() + j * entSize
                val (symIdx, rType, rOffset, rAddend) = if (is32) {
                    parseRelocation32(off, hasAddend)
                } else {
                    parseRelocation64(off, hasAddend)
                }

                val symName = resolveRelocationSymbolName(symIdx, symtabShdr, symStrtabIdx)

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

    private fun resolveRelocationEntrySize(hasAddend: Boolean): Int {
        return if (is32) {
            if (hasAddend) Elf.RELA32_SIZE else Elf.REL32_SIZE
        } else {
            if (hasAddend) Elf.RELA64_SIZE else Elf.REL64_SIZE
        }
    }

    private data class RawRelocation(val symIdx: Int, val type: Int, val offset: Long, val addend: Long)

    private fun parseRelocation32(off: Int, hasAddend: Boolean): RawRelocation {
        val rOffset = buf.getInt(off).toLong() and 0xFFFFFFFFL
        val rInfo = buf.getInt(off + 4)
        val rAddend = if (hasAddend) buf.getInt(off + 8).toLong() else 0L
        val symIdx = (rInfo ushr 8) and 0xFFFFFF
        val rType = rInfo and 0xFF
        return RawRelocation(symIdx, rType, rOffset, rAddend)
    }

    private fun parseRelocation64(off: Int, hasAddend: Boolean): RawRelocation {
        val rOffset = buf.getLong(off)
        val rInfo = buf.getLong(off + 8)
        val rAddend = if (hasAddend) buf.getLong(off + 16) else 0L
        val symIdx = (rInfo shr 32).toInt()
        val rType = (rInfo and 0xFFFFFFFFL).toInt()
        return RawRelocation(symIdx, rType, rOffset, rAddend)
    }

    private fun resolveRelocationSymbolName(symIdx: Int, symtabShdr: RawShdr, symStrtabIdx: Int): String {
        if (symIdx <= 0) return ""
        val symSize = if (is32) Elf.SYM32_SIZE else Elf.SYM64_SIZE
        val symOff = symtabShdr.offset.toInt() + symIdx * symSize
        val symNameIdx = buf.getInt(symOff)
        return readStringFromSection(symStrtabIdx, symNameIdx)
    }

    private fun buildElfDynamicInfo(): ElfDynamicInfo? {
        val dynShdr = rawShdrs.firstOrNull { it.type == ElfSectionType.DYNAMIC.code } ?: return null
        val dynStrtabIdx = dynShdr.link

        val needed = mutableListOf<String>()
        var soName: String? = null
        val rpath = mutableListOf<String>()
        val runpath = mutableListOf<String>()
        val entries = mutableListOf<ElfDynamicEntry>()

        val entSize = if (dynShdr.entsize > 0) dynShdr.entsize.toInt() else if (is32) 8 else 16
        val entCount = (dynShdr.size / entSize).toInt()

        for (j in 0 until entCount) {
            val off = dynShdr.offset.toInt() + j * entSize
            val tagCode: Long
            val value: Long
            if (is32) {
                tagCode = buf.getInt(off).toLong() and 0xFFFFFFFFL
                value = buf.getInt(off + 4).toLong() and 0xFFFFFFFFL
            } else {
                tagCode = buf.getLong(off)
                value = buf.getLong(off + 8)
            }
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
