package org.kgen.target.wasm.module

import org.kgen.target.wasm.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Parses a .wasm binary into a [WasmModule].
 *
 * Supports WASM version 1 modules with all standard sections (1–12)
 * and custom sections. Validates the magic number and version header.
 *
 * ```java
 * byte[] bytes = Files.readAllBytes(Path.of("module.wasm"));
 * WasmModule module = WasmModuleReader.read(bytes);
 *
 * // Query imports
 * for (WasmModule.Import imp : module.getImports()) {
 *     System.out.println(imp.getModule() + "." + imp.getName());
 * }
 *
 * // Query exports
 * for (WasmModule.Export exp : module.getExports()) {
 *     System.out.println(exp.getName() + " (" + exp.getKind() + ")");
 * }
 * ```
 */
class WasmModuleReader private constructor(private val buf: ByteBuffer) {

    private val types = mutableListOf<WasmModule.FuncType>()
    private val imports = mutableListOf<WasmModule.Import>()
    private val functionTypeIndices = mutableListOf<Int>()
    private val tables = mutableListOf<WasmModule.Table>()
    private val memories = mutableListOf<WasmModule.Memory>()
    private val globals = mutableListOf<WasmModule.Global>()
    private val exports = mutableListOf<WasmModule.Export>()
    private var start: Int? = null
    private val elements = mutableListOf<WasmModule.Element>()
    private val functions = mutableListOf<WasmModule.Function>()
    private val dataSegments = mutableListOf<WasmModule.DataSegment>()
    private val customSections = mutableListOf<WasmModule.CustomSection>()
    private var functionNames: Map<Int, String> = emptyMap()

    private fun parse(): WasmModule {
        // Magic is a fixed byte sequence: \0asm (read as little-endian int = 0x6D736100)
        val magic = buf.int
        check(magic == 0x6D736100) { "Invalid WASM magic: 0x${magic.toString(16)}" }
        val version = buf.int
        check(version == 1) { "Unsupported WASM version: $version" }

        while (buf.hasRemaining()) {
            val sectionId = readU8()
            val sectionSize = readU32()
            val sectionEnd = buf.position() + sectionSize

            when (sectionId) {
                0 -> readCustomSection(sectionSize)
                1 -> readTypeSection()
                2 -> readImportSection()
                3 -> readFunctionSection()
                4 -> readTableSection()
                5 -> readMemorySection()
                6 -> readGlobalSection()
                7 -> readExportSection()
                8 -> readStartSection()
                9 -> readElementSection()
                10 -> readCodeSection()
                11 -> readDataSection()
                12 -> readDataCountSection()
                else -> buf.position(sectionEnd) // skip unknown
            }

            buf.position(sectionEnd)
        }

        return WasmModule(
            version = version,
            types = types.toList(),
            imports = imports.toList(),
            functions = functions.toList(),
            tables = tables.toList(),
            memories = memories.toList(),
            globals = globals.toList(),
            exports = exports.toList(),
            start = start,
            elements = elements.toList(),
            dataSegments = dataSegments.toList(),
            customSections = customSections.toList(),
        )
    }

    private fun readTypeSection() {
        val count = readU32()
        repeat(count) {
            val form = readU8()
            check(form == 0x60) { "Expected func type (0x60), got 0x${form.toString(16)}" }
            val params = readValueTypeVec()
            val results = readValueTypeVec()
            types.add(WasmModule.FuncType(params, results))
        }
    }

    private fun readImportSection() {
        val count = readU32()
        repeat(count) {
            val module = readName()
            val name = readName()
            when (val kind = readU8()) {
                0x00 -> imports.add(WasmModule.Import.Func(module, name, readU32()))
                0x01 -> {
                    val refType = readRefType()
                    val (min, max) = readLimits()
                    imports.add(WasmModule.Import.Table(module, name, refType, min, max))
                }
                0x02 -> {
                    val (min, max) = readLimits()
                    imports.add(WasmModule.Import.Memory(module, name, min, max))
                }
                0x03 -> {
                    val type = readValueType()
                    val mutable = readU8() == 1
                    imports.add(WasmModule.Import.Global(module, name, type, mutable))
                }
                else -> error("Unknown import kind: $kind")
            }
        }
    }

    private fun readFunctionSection() {
        val count = readU32()
        repeat(count) { functionTypeIndices.add(readU32()) }
    }

    private fun readTableSection() {
        val count = readU32()
        repeat(count) {
            val refType = readRefType()
            val (min, max) = readLimits()
            tables.add(WasmModule.Table(refType, min, max))
        }
    }

    private fun readMemorySection() {
        val count = readU32()
        repeat(count) {
            val (min, max) = readLimits()
            memories.add(WasmModule.Memory(min, max))
        }
    }

    private fun readGlobalSection() {
        val count = readU32()
        repeat(count) {
            val type = readValueType()
            val mutable = readU8() == 1
            val initExpr = readConstExpr()
            globals.add(WasmModule.Global(type, mutable, initExpr))
        }
    }

    private fun readExportSection() {
        val count = readU32()
        repeat(count) {
            val name = readName()
            val kind = WasmModule.ExportKind.fromCode(readU8())
            val index = readU32()
            exports.add(WasmModule.Export(name, kind, index))
        }
    }

    private fun readStartSection() {
        start = readU32()
    }

    private fun readElementSection() {
        val count = readU32()
        repeat(count) {
            val flags = readU32()
            when (flags) {
                0 -> {
                    val offsetExpr = readConstExpr()
                    val funcCount = readU32()
                    val indices = (0 until funcCount).map { readU32() }
                    elements.add(WasmModule.Element(flags, offsetExpr, indices))
                }
                1 -> {
                    val elemKind = readU8()
                    val funcCount = readU32()
                    val indices = (0 until funcCount).map { readU32() }
                    elements.add(WasmModule.Element(flags, null, indices))
                }
                2 -> {
                    val tableIdx = readU32()
                    val offsetExpr = readConstExpr()
                    val elemKind = readU8()
                    val funcCount = readU32()
                    val indices = (0 until funcCount).map { readU32() }
                    elements.add(WasmModule.Element(flags, offsetExpr, indices))
                }
                else -> {
                    // Simplified: skip complex element segment forms
                    elements.add(WasmModule.Element(flags, null, emptyList()))
                }
            }
        }
    }

    private fun readCodeSection() {
        val count = readU32()
        repeat(count) { i ->
            val bodySize = readU32()
            val bodyStart = buf.position()
            val bodyEnd = bodyStart + bodySize

            // Decode locals
            val localGroupCount = readU32()
            val locals = mutableListOf<WasmValueType>()
            repeat(localGroupCount) {
                val n = readU32()
                val type = readValueType()
                repeat(n) { locals.add(type) }
            }

            // Remaining bytes are the function body (including trailing 0x0B end)
            val codeStart = buf.position()
            val codeLen = bodyEnd - codeStart
            val body = ByteArray(codeLen)
            buf.get(body)

            val typeIndex = functionTypeIndices.getOrElse(i) { -1 }
            val name = functionNames[imports.count { it is WasmModule.Import.Func } + i]
                ?: exports.find { it.kind == WasmModule.ExportKind.FUNCTION && it.index == imports.count { imp -> imp is WasmModule.Import.Func } + i }?.name

            functions.add(WasmModule.Function(name, typeIndex, locals, body))

            buf.position(bodyEnd)
        }
    }

    private fun readDataSection() {
        val count = readU32()
        repeat(count) {
            when (val flags = readU32()) {
                0 -> {
                    val offsetExpr = readConstExpr()
                    val size = readU32()
                    val data = ByteArray(size)
                    buf.get(data)
                    dataSegments.add(WasmModule.DataSegment.Active(0, offsetExpr, data))
                }
                1 -> {
                    val size = readU32()
                    val data = ByteArray(size)
                    buf.get(data)
                    dataSegments.add(WasmModule.DataSegment.Passive(data))
                }
                2 -> {
                    val memIdx = readU32()
                    val offsetExpr = readConstExpr()
                    val size = readU32()
                    val data = ByteArray(size)
                    buf.get(data)
                    dataSegments.add(WasmModule.DataSegment.Active(memIdx, offsetExpr, data))
                }
                else -> error("Unknown data segment flags: $flags")
            }
        }
    }

    private fun readDataCountSection() {
        readU32() // data count — informational only
    }

    private fun readCustomSection(sectionSize: Int) {
        val start = buf.position()
        val name = readName()
        val dataLen = sectionSize - (buf.position() - start)
        val data = ByteArray(dataLen)
        buf.get(data)

        if (name == "name") {
            parseNameSection(data)
        }

        customSections.add(WasmModule.CustomSection(name, data))
    }

    private fun parseNameSection(data: ByteArray) {
        val nameBuf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        while (nameBuf.hasRemaining()) {
            val subsectionId = readU8(nameBuf)
            val subsectionSize = readU32(nameBuf)
            val subsectionEnd = nameBuf.position() + subsectionSize

            if (subsectionId == 1) { // function names
                val names = mutableMapOf<Int, String>()
                val count = readU32(nameBuf)
                repeat(count) {
                    val index = readU32(nameBuf)
                    val funcName = readName(nameBuf)
                    names[index] = funcName
                }
                functionNames = names
            } else {
                nameBuf.position(subsectionEnd)
            }
        }
    }

    // Primitive decoders

    private fun readU8(): Int = buf.get().toInt() and 0xFF
    private fun readU8(b: ByteBuffer): Int = b.get().toInt() and 0xFF

    private fun readU32(): Int = readU32(buf)

    private fun readName(): String = readName(buf)

    private fun readName(b: ByteBuffer): String {
        val len = readU32(b)
        val bytes = ByteArray(len)
        b.get(bytes)
        return String(bytes, Charsets.UTF_8)
    }

    private fun readValueType(): WasmValueType {
        val code = readU8()
        return WasmValueType.entries.firstOrNull { it.code == code }
            ?: error("Unknown value type: 0x${code.toString(16)}")
    }

    private fun readRefType(): WasmRefType {
        val code = readU8()
        return WasmRefType.entries.firstOrNull { it.code == code }
            ?: error("Unknown ref type: 0x${code.toString(16)}")
    }

    private fun readValueTypeVec(): List<WasmValueType> {
        val count = readU32()
        return (0 until count).map { readValueType() }
    }

    private fun readLimits(): Pair<Int, Int?> {
        return when (val flags = readU8()) {
            0x00 -> readU32() to null
            0x01 -> {
                val min = readU32()
                val max = readU32()
                min to max
            }
            else -> error("Unknown limits flags: $flags")
        }
    }

    private fun readConstExpr(): ByteArray {
        val start = buf.position()
        // Scan until we find 0x0B (end)
        while (true) {
            val b = readU8()
            if (b == 0x0B) break
            // Skip immediates for constant expression opcodes
            when (b) {
                0x41 -> readS32(buf)    // i32.const
                0x42 -> readS64(buf)    // i64.const
                0x43 -> buf.position(buf.position() + 4) // f32.const
                0x44 -> buf.position(buf.position() + 8) // f64.const
                0x23 -> readU32(buf)    // global.get
                0xD0 -> readU8()        // ref.null
                0xD2 -> readU32(buf)    // ref.func
                // else: single-byte opcode (end handled above)
            }
        }
        val end = buf.position()
        val expr = ByteArray(end - start)
        buf.position(start)
        buf.get(expr)
        return expr
    }

    companion object {
        @JvmStatic
        fun read(bytes: ByteArray): WasmModule {
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            return WasmModuleReader(buf).parse()
        }

        // LEB128 unsigned 32-bit
        internal fun readU32(buf: ByteBuffer): Int {
            var result = 0
            var shift = 0
            while (true) {
                val b = buf.get().toInt() and 0xFF
                result = result or ((b and 0x7F) shl shift)
                if (b and 0x80 == 0) break
                shift += 7
                check(shift < 35) { "LEB128 overflow" }
            }
            return result
        }

        // LEB128 signed 32-bit
        internal fun readS32(buf: ByteBuffer): Int {
            var result = 0
            var shift = 0
            var b: Int
            do {
                b = buf.get().toInt() and 0xFF
                result = result or ((b and 0x7F) shl shift)
                shift += 7
            } while (b and 0x80 != 0)
            if (shift < 32 && (b and 0x40) != 0) {
                result = result or ((-1) shl shift)
            }
            return result
        }

        // LEB128 signed 64-bit
        internal fun readS64(buf: ByteBuffer): Long {
            var result = 0L
            var shift = 0
            var b: Int
            do {
                b = buf.get().toInt() and 0xFF
                result = result or ((b.toLong() and 0x7F) shl shift)
                shift += 7
            } while (b and 0x80 != 0)
            if (shift < 64 && (b and 0x40) != 0) {
                result = result or ((-1L) shl shift)
            }
            return result
        }
    }
}
