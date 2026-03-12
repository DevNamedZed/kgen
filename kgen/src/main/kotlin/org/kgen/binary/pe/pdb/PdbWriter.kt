package org.kgen.binary.pe.pdb

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/**
 * Writes a PDB (Program Database) file.
 *
 * Constructs valid PDB files with all required streams:
 * - PDB Info stream (GUID, age, version)
 * - TPI stream (type records)
 * - DBI stream (module info, section contributions)
 * - IPI stream (ID records)
 * - Global/Public symbol hash streams
 * - Per-module symbol/line streams
 *
 * ```kotlin
 * val writer = PdbWriter()
 * writer.guid = UUID.randomUUID()
 * writer.age = 1
 * writer.machine = 0x8664 // AMD64
 *
 * // Add types
 * writer.addType(typeRecord)
 *
 * // Add a module with symbols
 * val mod = writer.addModule("main.obj", "C:\\src\\main.c")
 * mod.addSymbol(procSymbol)
 * mod.addLineInfo(lineBlock)
 *
 * val pdbBytes = writer.build()
 * ```
 */
class PdbWriter {
    var guid: UUID = UUID.randomUUID()
    var age: Int = 1
    var machine: Short = 0x8664.toShort() // AMD64

    private val types = mutableListOf<CvTypeRecord>()
    private val idTypes = mutableListOf<CvTypeRecord>()
    private val modules = mutableListOf<PdbModuleBuilder>()
    private val globalSymbols = mutableListOf<CvSymbolRecord>()
    private val namedStreams = mutableMapOf<String, ByteArray>()

    /** Add a type record to the TPI stream. Returns its type index (0x1000 + offset). */
    fun addType(record: CvTypeRecord): Int {
        types.add(record)
        return 0x1000 + types.size - 1
    }

    /** Add an ID record to the IPI stream. Returns its type index. */
    fun addIdType(record: CvTypeRecord): Int {
        idTypes.add(record)
        return 0x1000 + idTypes.size - 1
    }

    /** Add a global symbol. */
    fun addGlobalSymbol(record: CvSymbolRecord) {
        globalSymbols.add(record)
    }

    /** Add a module. */
    fun addModule(name: String, objectFileName: String = name): PdbModuleBuilder {
        val mod = PdbModuleBuilder(name, objectFileName)
        modules.add(mod)
        return mod
    }

    /** Add a named stream (e.g., "/names" for the string table). */
    fun addNamedStream(name: String, data: ByteArray) {
        namedStreams[name] = data
    }

    /** Build the complete PDB file. */
    fun build(): ByteArray {
        // Pre-build stream data
        val tpiData = buildTpiStream(types)
        val ipiData = buildTpiStream(idTypes)
        val globalData = buildSymbolStream(globalSymbols)
        val symRecData = buildSymbolStream(globalSymbols)

        // Compute stream indices:
        // 0 = Old Directory (empty)
        // 1 = PDB Info
        // 2 = TPI
        // 3 = DBI
        // 4 = IPI
        // 5.. = module streams, then global, public, symrec, named streams
        var nextStream = 5

        val moduleStreamIndices = modules.map { mod ->
            val data = buildModuleSymbolStream(mod)
            if (data.isNotEmpty()) nextStream++ else -1
        }

        val globalStreamIndex = if (globalData.isNotEmpty()) nextStream++ else -1
        val publicStreamIndex = nextStream++ // always present (empty)
        val symRecStreamIndex = if (symRecData.isNotEmpty()) nextStream++ else -1

        val namedStreamIndices = mutableMapOf<String, Int>()
        for (name in namedStreams.keys) {
            namedStreamIndices[name] = nextStream++
        }

        // Build DBI with correct stream indices
        val dbiData = buildDbiStream(moduleStreamIndices, globalStreamIndex.toShort(),
            publicStreamIndex.toShort(), symRecStreamIndex.toShort())

        // Assemble MSF
        val msf = MsfWriter()
        msf.addStream(byteArrayOf())                                  // 0: Old Directory
        msf.addStream(buildPdbInfoStreamFinal(namedStreamIndices))     // 1: PDB Info
        msf.addStream(tpiData)                                         // 2: TPI
        msf.addStream(dbiData)                                         // 3: DBI
        msf.addStream(ipiData)                                         // 4: IPI

        for (mod in modules) {
            val data = buildModuleSymbolStream(mod)
            if (data.isNotEmpty()) msf.addStream(data)
        }

        if (globalData.isNotEmpty()) msf.addStream(globalData)
        msf.addStream(byteArrayOf()) // public (empty)
        if (symRecData.isNotEmpty()) msf.addStream(symRecData)

        for ((_, data) in namedStreams) {
            msf.addStream(data)
        }

        return msf.build()
    }

    private fun buildPdbInfoStreamFinal(namedStreamIndices: Map<String, Int>): ByteArray {
        val buf = ByteArrayOutputStream()
        val header = ByteBuffer.allocate(28).order(ByteOrder.LITTLE_ENDIAN)
        header.putInt(PdbInfoStream.PdbStreamVersion.VC70.code)
        header.putInt((System.currentTimeMillis() / 1000).toInt()) // signature
        header.putInt(age)

        // GUID (mixed endian): Data1 (LE uint32), Data2 (LE uint16), Data3 (LE uint16), Data4 (8 bytes big-endian)
        header.putInt((guid.mostSignificantBits shr 32).toInt())
        header.putShort(((guid.mostSignificantBits shr 16) and 0xFFFF).toInt().toShort())
        header.putShort((guid.mostSignificantBits and 0xFFFF).toInt().toShort())
        var lsb = guid.leastSignificantBits
        for (i in 7 downTo 0) {
            header.put((lsb shr (i * 8) and 0xFF).toByte())
        }
        buf.write(header.array())

        // Named stream map
        val allNamedStreams = namedStreams.keys.toList() + namedStreamIndices.keys
        writeNamedStreamMap(buf, namedStreamIndices)

        return buf.toByteArray()
    }

    private fun writeNamedStreamMap(buf: ByteArrayOutputStream, indices: Map<String, Int>) {
        if (indices.isEmpty()) {
            // Write empty named stream map
            val empty = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
            empty.putInt(0) // string buffer size
            empty.putInt(0) // count
            empty.putInt(0) // capacity
            empty.putInt(0) // present words count
            buf.write(empty.array())
            return
        }

        // Build string buffer
        val stringBuf = ByteArrayOutputStream()
        val offsets = mutableMapOf<String, Int>()
        for (name in indices.keys) {
            offsets[name] = stringBuf.size()
            stringBuf.write(name.toByteArray(Charsets.UTF_8))
            stringBuf.write(0)
        }
        val stringBytes = stringBuf.toByteArray()

        val header = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
        header.putInt(stringBytes.size)
        buf.write(header.array())
        buf.write(stringBytes)

        val capacity = indices.size * 2 // load factor ~50%
        val mapHeader = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        mapHeader.putInt(indices.size)
        mapHeader.putInt(capacity)
        buf.write(mapHeader.array())

        // Present bit vector
        val presentWords = (capacity + 31) / 32
        val presentBuf = ByteBuffer.allocate(4 + presentWords * 4).order(ByteOrder.LITTLE_ENDIAN)
        presentBuf.putInt(presentWords)
        val presentBits = IntArray(presentWords)
        var slot = 0
        for (name in indices.keys) {
            presentBits[slot / 32] = presentBits[slot / 32] or (1 shl (slot % 32))
            slot++
        }
        for (word in presentBits) presentBuf.putInt(word)
        buf.write(presentBuf.array())

        // Deleted bit vector (empty)
        val deletedBuf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
        deletedBuf.putInt(0)
        buf.write(deletedBuf.array())

        // Entries
        for ((name, streamIdx) in indices) {
            val entry = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            entry.putInt(offsets[name]!!)
            entry.putInt(streamIdx)
            buf.write(entry.array())
        }
    }

    private fun buildTpiStream(records: List<CvTypeRecord>): ByteArray {
        val recordData = ByteArrayOutputStream()
        for (record in records) {
            val len = record.data.size + 2 // +2 for kind
            val header = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
            header.putShort(len.toShort())
            header.putShort(record.kind.toShort())
            recordData.write(header.array())
            recordData.write(record.data)
            // Align to 4 bytes
            val pad = (4 - (recordData.size() % 4)) % 4
            for (i in 0 until pad) recordData.write(0xF0 + pad - 1) // LF_PADx
        }

        val recordBytes = recordData.toByteArray()
        val header = ByteBuffer.allocate(TpiStreamHeader.SIZE).order(ByteOrder.LITTLE_ENDIAN)
        header.putInt(TpiStreamHeader.VERSION_V80)
        header.putInt(TpiStreamHeader.SIZE)
        header.putInt(0x1000) // typeIndexBegin
        header.putInt(0x1000 + records.size) // typeIndexEnd
        header.putInt(recordBytes.size)
        header.putShort((-1).toShort()) // hashStreamIndex (none)
        header.putShort((-1).toShort()) // hashAuxStreamIndex (none)
        header.putInt(4) // hashKeySize
        header.putInt(0x3FFFF) // numHashBuckets
        header.putInt(0) // hashValueBufferOffset
        header.putInt(0) // hashValueBufferLength
        header.putInt(0) // indexOffsetBufferOffset
        header.putInt(0) // indexOffsetBufferLength
        header.putInt(0) // hashAdjBufferOffset
        header.putInt(0) // hashAdjBufferLength

        val result = ByteArrayOutputStream()
        result.write(header.array())
        result.write(recordBytes)
        return result.toByteArray()
    }

    private fun buildDbiStream(
        moduleStreamIndices: List<Int>,
        globalStreamIndex: Short,
        publicStreamIndex: Short,
        symRecStreamIndex: Short,
    ): ByteArray {
        val modInfoSubstream = buildModuleInfoSubstream(moduleStreamIndices)
        val secContribSubstream = buildSectionContribSubstream()

        val header = ByteBuffer.allocate(DbiStreamHeader.SIZE).order(ByteOrder.LITTLE_ENDIAN)
        header.putInt(-1) // versionSignature
        header.putInt(DbiStreamHeader.VERSION_V70)
        header.putInt(age)
        header.putShort(globalStreamIndex)
        header.putShort(0) // buildNumber
        header.putShort(publicStreamIndex)
        header.putShort(0) // pdbDllVersion
        header.putShort(symRecStreamIndex)
        header.putShort(0) // pdbDllRbld
        header.putInt(modInfoSubstream.size) // modInfoSize
        header.putInt(secContribSubstream.size) // sectionContribSize
        header.putInt(0) // sectionMapSize
        header.putInt(0) // sourceInfoSize
        header.putInt(0) // typeServerMapSize
        header.putInt(0) // mfcTypeServerIndex
        header.putInt(0) // optionalDbgHeaderSize
        header.putInt(0) // ecSubstreamSize
        header.putShort(0) // flags
        header.putShort(machine)

        val result = ByteArrayOutputStream()
        result.write(header.array())
        result.write(modInfoSubstream)
        result.write(secContribSubstream)
        return result.toByteArray()
    }

    private fun buildModuleInfoSubstream(streamIndices: List<Int>): ByteArray {
        val buf = ByteArrayOutputStream()
        for (i in modules.indices) {
            val mod = modules[i]
            val streamIdx = if (i < streamIndices.size) streamIndices[i] else -1

            val entry = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN)
            entry.putInt(0) // unused
            entry.putShort(0) // scSection
            entry.putShort(0) // padding
            entry.putInt(0) // scOffset
            entry.putInt(0) // scSize
            entry.putInt(0) // scCharacteristics
            entry.putShort(i.toShort()) // scModuleIndex
            entry.putShort(0) // padding
            entry.putInt(0) // dataCrc
            entry.putInt(0) // relocCrc
            entry.putShort(0) // flags
            entry.putShort(if (streamIdx < 0) 0xFFFF.toShort() else streamIdx.toShort())
            entry.putInt(mod.symbolDataSize()) // symbolSize
            entry.putInt(0) // linesSize
            entry.putInt(mod.lineDataSize()) // c13LinesSize
            entry.putShort(mod.sourceFileCount().toShort())
            entry.putShort(0) // padding
            entry.putInt(0) // unused
            entry.putInt(0) // sourceFileNameIndex
            entry.putInt(0) // pdbFilePathNameIndex
            buf.write(entry.array())

            // Module name (null-terminated)
            buf.write(mod.name.toByteArray(Charsets.UTF_8))
            buf.write(0)

            // Object file name (null-terminated)
            buf.write(mod.objectFileName.toByteArray(Charsets.UTF_8))
            buf.write(0)

            // Align to 4 bytes
            val pad = (4 - (buf.size() % 4)) % 4
            for (j in 0 until pad) buf.write(0)
        }
        return buf.toByteArray()
    }

    private fun buildSectionContribSubstream(): ByteArray {
        val buf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(0xF12EBA2D.toInt()) // version = Ver60
        return buf.array()
    }

    private fun buildModuleSymbolStream(mod: PdbModuleBuilder): ByteArray {
        if (mod.symbols.isEmpty() && mod.lineBlocks.isEmpty()) return byteArrayOf()

        val buf = ByteArrayOutputStream()
        // CV_SIGNATURE_C13
        val sig = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
        sig.putInt(4)
        buf.write(sig.array())

        // Symbols
        for (sym in mod.symbols) {
            val len = sym.data.size + 2
            val header = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
            header.putShort(len.toShort())
            header.putShort(sym.kind.toShort())
            buf.write(header.array())
            buf.write(sym.data)
            // Align to 4 bytes
            val pad = (4 - (buf.size() % 4)) % 4
            for (i in 0 until pad) buf.write(0)
        }

        // C13 line info
        if (mod.lineBlocks.isNotEmpty()) {
            writeC13Lines(buf, mod.lineBlocks)
        }

        return buf.toByteArray()
    }

    private fun writeC13Lines(buf: ByteArrayOutputStream, lineBlocks: List<CvLineBlock>) {
        for (block in lineBlocks) {
            val subsectionData = ByteArrayOutputStream()
            // File block header
            val blockHeader = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
            blockHeader.putInt(block.fileIndex)
            blockHeader.putInt(block.lines.size)
            blockHeader.putInt(12 + block.lines.size * 8) // size of this block
            subsectionData.write(blockHeader.array())

            for (line in block.lines) {
                val lineEntry = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
                lineEntry.putInt(line.offset)
                val flags = line.lineStart and 0x00FFFFFF or
                    ((line.deltaLineEnd and 0x7F) shl 24) or
                    (if (line.isStatement) 0x80000000.toInt() else 0)
                lineEntry.putInt(flags)
                subsectionData.write(lineEntry.array())
            }

            val subsectionBytes = subsectionData.toByteArray()

            // Subsection header: kind (4) + length (4)
            val header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            header.putInt(DebugSubsectionKind.LINES.code)
            header.putInt(subsectionBytes.size)
            buf.write(header.array())
            buf.write(subsectionBytes)

            // Align to 4
            val pad = (4 - (buf.size() % 4)) % 4
            for (i in 0 until pad) buf.write(0)
        }
    }

    private fun buildSymbolStream(symbols: List<CvSymbolRecord>): ByteArray {
        if (symbols.isEmpty()) return byteArrayOf()
        val buf = ByteArrayOutputStream()
        for (sym in symbols) {
            val len = sym.data.size + 2
            val header = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
            header.putShort(len.toShort())
            header.putShort(sym.kind.toShort())
            buf.write(header.array())
            buf.write(sym.data)
            val pad = (4 - (buf.size() % 4)) % 4
            for (i in 0 until pad) buf.write(0)
        }
        return buf.toByteArray()
    }
}

/**
 * Builder for a single module within a PDB file.
 */
class PdbModuleBuilder(
    val name: String,
    val objectFileName: String,
) {
    internal val symbols = mutableListOf<CvSymbolRecord>()
    internal val lineBlocks = mutableListOf<CvLineBlock>()
    private val sourceFiles = mutableSetOf<String>()

    /** Add a symbol record to this module. */
    fun addSymbol(record: CvSymbolRecord) {
        symbols.add(record)
    }

    /** Add line number information. */
    fun addLineInfo(block: CvLineBlock) {
        lineBlocks.add(block)
    }

    /** Register a source file for this module. */
    fun addSourceFile(path: String) {
        sourceFiles.add(path)
    }

    internal fun symbolDataSize(): Int {
        var size = 4 // CV_SIGNATURE_C13
        for (sym in symbols) {
            size += 4 + sym.data.size // header + data
            size = (size + 3) and 3.inv() // align
        }
        return size
    }

    internal fun lineDataSize(): Int {
        var size = 0
        for (block in lineBlocks) {
            size += 8 // subsection header
            size += 12 + block.lines.size * 8
            size = (size + 3) and 3.inv()
        }
        return size
    }

    internal fun sourceFileCount(): Int = sourceFiles.size
}
