package org.kgen.target.wasm.module

import org.kgen.target.wasm.*
import java.io.ByteArrayOutputStream

/**
 * Serializes a [WasmModule] to a .wasm binary.
 *
 * This is the inverse of [WasmModuleReader]: read a module, transform it,
 * write it back out.
 *
 * ```java
 * WasmModule module = WasmModuleReader.read(originalBytes);
 * // ... transform module ...
 * byte[] output = WasmModuleWriter.write(module);
 * ```
 */
class WasmModuleWriter private constructor(private val module: WasmModule) {

    private val out = ByteArrayOutputStream()

    private fun write(): ByteArray {
        // Magic + version
        out.write(byteArrayOf(0x00, 0x61, 0x73, 0x6D))
        out.write(byteArrayOf(
            (module.version and 0xFF).toByte(),
            ((module.version shr 8) and 0xFF).toByte(),
            ((module.version shr 16) and 0xFF).toByte(),
            ((module.version shr 24) and 0xFF).toByte(),
        ))

        writeTypeSection()
        writeImportSection()
        writeFunctionSection()
        writeTableSection()
        writeMemorySection()
        writeGlobalSection()
        writeExportSection()
        writeStartSection()
        writeElementSection()
        writeDataCountSection()
        writeCodeSection()
        writeDataSection()
        writeCustomSections()

        return out.toByteArray()
    }

    private fun writeTypeSection() {
        if (module.types.isEmpty()) return
        writeSection(1) { s ->
            writeU32(s, module.types.size)
            for (ft in module.types) {
                s.write(0x60)
                writeU32(s, ft.params.size)
                for (p in ft.params) s.write(p.code)
                writeU32(s, ft.results.size)
                for (r in ft.results) s.write(r.code)
            }
        }
    }

    private fun writeImportSection() {
        if (module.imports.isEmpty()) return
        writeSection(2) { s ->
            writeU32(s, module.imports.size)
            for (imp in module.imports) {
                writeName(s, imp.module)
                writeName(s, imp.name)
                when (imp) {
                    is WasmModule.Import.Func -> {
                        s.write(0x00)
                        writeU32(s, imp.typeIndex)
                    }
                    is WasmModule.Import.Table -> {
                        s.write(0x01)
                        s.write(imp.refType.code)
                        writeLimits(s, imp.min, imp.max)
                    }
                    is WasmModule.Import.Memory -> {
                        s.write(0x02)
                        writeLimits(s, imp.min, imp.max)
                    }
                    is WasmModule.Import.Global -> {
                        s.write(0x03)
                        s.write(imp.type.code)
                        s.write(if (imp.mutable) 0x01 else 0x00)
                    }
                    is WasmModule.Import.Tag -> {
                        s.write(0x04)
                        s.write(imp.attribute)
                        writeU32(s, imp.typeIndex)
                    }
                }
            }
        }
    }

    private fun writeFunctionSection() {
        if (module.functions.isEmpty()) return
        writeSection(3) { s ->
            writeU32(s, module.functions.size)
            for (f in module.functions) writeU32(s, f.typeIndex)
        }
    }

    private fun writeTableSection() {
        if (module.tables.isEmpty()) return
        writeSection(4) { s ->
            writeU32(s, module.tables.size)
            for (t in module.tables) {
                s.write(t.refType.code)
                writeLimits(s, t.min, t.max)
            }
        }
    }

    private fun writeMemorySection() {
        if (module.memories.isEmpty()) return
        writeSection(5) { s ->
            writeU32(s, module.memories.size)
            for (m in module.memories) writeLimits(s, m.min, m.max)
        }
    }

    private fun writeGlobalSection() {
        if (module.globals.isEmpty()) return
        writeSection(6) { s ->
            writeU32(s, module.globals.size)
            for (g in module.globals) {
                s.write(g.type.code)
                s.write(if (g.mutable) 0x01 else 0x00)
                s.write(g.initExpr)
            }
        }
    }

    private fun writeExportSection() {
        if (module.exports.isEmpty()) return
        writeSection(7) { s ->
            writeU32(s, module.exports.size)
            for (e in module.exports) {
                writeName(s, e.name)
                s.write(e.kind.code)
                writeU32(s, e.index)
            }
        }
    }

    private fun writeStartSection() {
        val start = module.start ?: return
        writeSection(8) { s -> writeU32(s, start) }
    }

    private fun writeElementSection() {
        if (module.elements.isEmpty()) return
        writeSection(9) { s ->
            writeU32(s, module.elements.size)
            for (elem in module.elements) {
                val usesExprs = elem.initExprs.isNotEmpty()
                val flags = computeElementFlags(elem, usesExprs)
                writeU32(s, flags)

                when (elem) {
                    is WasmModule.Element.Active -> {
                        if (flags == 2 || flags == 6) {
                            writeU32(s, elem.tableIndex)
                        }
                        s.write(elem.offsetExpr)
                    }
                    is WasmModule.Element.Passive,
                    is WasmModule.Element.Declarative -> { /* no table/offset */ }
                }

                // Forms 1-3: elemkind byte; Forms 4-7: reftype byte
                when (flags) {
                    1, 2, 3 -> s.write(refTypeToElemKind(elem.refType))
                    4, 5, 6, 7 -> s.write(elem.refType.code)
                }

                if (usesExprs) {
                    writeU32(s, elem.initExprs.size)
                    for (expr in elem.initExprs) {
                        s.write(expr)
                    }
                } else {
                    writeU32(s, elem.funcIndices.size)
                    for (idx in elem.funcIndices) {
                        writeU32(s, idx)
                    }
                }
            }
        }
    }

    private fun computeElementFlags(elem: WasmModule.Element, usesExprs: Boolean): Int {
        return when (elem) {
            is WasmModule.Element.Active -> when {
                !usesExprs && elem.tableIndex == 0 -> 0
                !usesExprs -> 2
                elem.tableIndex == 0 -> 4
                else -> 6
            }
            is WasmModule.Element.Passive -> if (usesExprs) 5 else 1
            is WasmModule.Element.Declarative -> if (usesExprs) 7 else 3
        }
    }

    private fun refTypeToElemKind(refType: WasmRefType): Int {
        return when (refType) {
            WasmRefType.FUNCREF -> 0x00
            else -> error("Unsupported elemkind for ref type: $refType")
        }
    }

    private fun writeDataCountSection() {
        if (module.dataSegments.isEmpty()) return
        writeSection(12) { s -> writeU32(s, module.dataSegments.size) }
    }

    private fun writeCodeSection() {
        if (module.functions.isEmpty()) return
        writeSection(10) { s ->
            writeU32(s, module.functions.size)
            for (f in module.functions) {
                val body = ByteArrayOutputStream()
                val localGroups = compressLocals(f.locals)
                writeU32(body, localGroups.size)
                for ((count, type) in localGroups) {
                    writeU32(body, count)
                    body.write(type.code)
                }
                body.write(f.body)
                val bodyBytes = body.toByteArray()
                writeU32(s, bodyBytes.size)
                s.write(bodyBytes)
            }
        }
    }

    private fun writeDataSection() {
        if (module.dataSegments.isEmpty()) return
        writeSection(11) { s ->
            writeU32(s, module.dataSegments.size)
            for (seg in module.dataSegments) {
                when (seg) {
                    is WasmModule.DataSegment.Active -> {
                        writeU32(s, if (seg.memoryIndex == 0) 0 else 2)
                        if (seg.memoryIndex != 0) writeU32(s, seg.memoryIndex)
                        s.write(seg.offsetExpr)
                        writeU32(s, seg.data.size)
                        s.write(seg.data)
                    }
                    is WasmModule.DataSegment.Passive -> {
                        writeU32(s, 1)
                        writeU32(s, seg.data.size)
                        s.write(seg.data)
                    }
                }
            }
        }
    }

    private fun writeCustomSections() {
        for (section in module.customSections) {
            writeSection(0) { s ->
                writeName(s, section.name)
                s.write(section.data)
            }
        }
    }

    private fun compressLocals(locals: List<WasmValueType>): List<Pair<Int, WasmValueType>> {
        if (locals.isEmpty()) return emptyList()
        val groups = mutableListOf<Pair<Int, WasmValueType>>()
        var count = 1
        var current = locals[0]
        for (i in 1 until locals.size) {
            if (locals[i] == current) {
                count++
            } else {
                groups.add(count to current)
                current = locals[i]
                count = 1
            }
        }
        groups.add(count to current)
        return groups
    }

    private fun writeSection(id: Int, writer: (ByteArrayOutputStream) -> Unit) {
        val section = ByteArrayOutputStream()
        writer(section)
        val bytes = section.toByteArray()
        out.write(id)
        writeU32(out, bytes.size)
        out.write(bytes)
    }

    private fun writeU32(out: ByteArrayOutputStream, value: Int) {
        var v = value
        do {
            var b = v and 0x7F
            v = v ushr 7
            if (v != 0) b = b or 0x80
            out.write(b)
        } while (v != 0)
    }

    private fun writeName(out: ByteArrayOutputStream, name: String) {
        val bytes = name.toByteArray(Charsets.UTF_8)
        writeU32(out, bytes.size)
        out.write(bytes)
    }

    private fun writeLimits(out: ByteArrayOutputStream, min: Int, max: Int?) {
        if (max != null) {
            out.write(0x01)
            writeU32(out, min)
            writeU32(out, max)
        } else {
            out.write(0x00)
            writeU32(out, min)
        }
    }

    companion object {
        @JvmStatic
        fun write(module: WasmModule): ByteArray = WasmModuleWriter(module).write()
    }
}
