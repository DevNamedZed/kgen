package org.kgen.wasm.asm

import org.kgen.wasm.*
import java.io.ByteArrayOutputStream

/**
 * WebAssembly assembler. Produces valid .wasm binary modules.
 *
 * Supports two control flow styles that produce identical bytecode:
 *
 * **Callback style** (auto-end):
 * ```java
 * WasmAssembler asm = WasmAssembler.create();
 * asm.function("add", List.of(WasmValueType.I32, WasmValueType.I32), List.of(WasmValueType.I32), (fn, a) -> {
 *     a.localGet(fn.getParameter(0));
 *     a.localGet(fn.getParameter(1));
 *     a.i32Add();
 * });
 * byte[] wasm = asm.assemble();
 * ```
 *
 * **Flat style** (manual end):
 * ```java
 * WasmAssembler asm = WasmAssembler.create();
 * WasmFunction fn = asm.beginFunction("add", List.of(WasmValueType.I32, WasmValueType.I32), List.of(WasmValueType.I32));
 * asm.localGet(fn.getParameter(0));
 * asm.localGet(fn.getParameter(1));
 * asm.i32Add();
 * asm.endFunction();
 * byte[] wasm = asm.assemble();
 * ```
 */
class WasmAssembler private constructor() : WasmAssemblerOps() {

    private val types = mutableListOf<FuncType>()
    private val imports = mutableListOf<Import>()
    private val functions = mutableListOf<FuncDef>()
    private val tables = mutableListOf<TableDef>()
    private val memories = mutableListOf<MemoryDef>()
    private val globals = mutableListOf<GlobalDef>()
    private val exports = mutableListOf<Export>()
    private var startFunction: Int? = null
    private val elements = mutableListOf<ElementSegment>()
    private val dataSegments = mutableListOf<DataSegment>()
    private val names = mutableMapOf<Int, String>()

    private var currentCode: ByteArrayOutputStream? = null
    private var currentLocals = mutableListOf<WasmLocal>()
    private var currentLocalCount = 0
    private var blockDepth = 0

    private val importFuncCount: Int get() = imports.count { it is Import.Func }

    // LEB128 encoding

    override fun emitByte(b: Int) {
        currentCode!!.write(b and 0xFF)
    }

    override fun emitU32(value: Int) {
        var v = value
        do {
            var b = v and 0x7F
            v = v ushr 7
            if (v != 0) b = b or 0x80
            currentCode!!.write(b)
        } while (v != 0)
    }

    override fun emitS32(value: Int) {
        var v = value
        var more = true
        while (more) {
            var b = v and 0x7F
            v = v shr 7
            if ((v == 0 && (b and 0x40) == 0) || (v == -1 && (b and 0x40) != 0)) {
                more = false
            } else {
                b = b or 0x80
            }
            currentCode!!.write(b)
        }
    }

    override fun emitS64(value: Long) {
        var v = value
        var more = true
        while (more) {
            var b = (v and 0x7F).toInt()
            v = v shr 7
            if ((v == 0L && (b and 0x40) == 0) || (v == -1L && (b and 0x40) != 0)) {
                more = false
            } else {
                b = b or 0x80
            }
            currentCode!!.write(b)
        }
    }

    override fun emitF32(value: Float) {
        val bits = java.lang.Float.floatToRawIntBits(value)
        currentCode!!.write(bits and 0xFF)
        currentCode!!.write((bits shr 8) and 0xFF)
        currentCode!!.write((bits shr 16) and 0xFF)
        currentCode!!.write((bits shr 24) and 0xFF)
    }

    override fun emitF64(value: Double) {
        val bits = java.lang.Double.doubleToRawLongBits(value)
        for (i in 0 until 8) {
            currentCode!!.write(((bits shr (i * 8)) and 0xFF).toInt())
        }
    }

    override fun emitBytes(bytes: ByteArray) {
        currentCode!!.write(bytes)
    }

    // Module-level declarations

    fun memory(name: String, minPages: Int, maxPages: Int? = null, exported: Boolean = false) {
        memories.add(MemoryDef(minPages, maxPages))
        if (exported) exports.add(Export(name, ExportKind.MEMORY, memories.size - 1))
    }

    fun global(name: String, type: WasmValueType, mutable: Boolean, initValue: Long, exported: Boolean = false) {
        globals.add(GlobalDef(type, mutable, initValue))
        if (exported) exports.add(Export(name, ExportKind.GLOBAL, globals.size - 1))
    }

    fun table(name: String, refType: WasmRefType, minSize: Int, maxSize: Int? = null, exported: Boolean = false) {
        tables.add(TableDef(refType, minSize, maxSize))
        if (exported) exports.add(Export(name, ExportKind.TABLE, tables.size - 1))
    }

    fun importFunction(module: String, name: String, params: List<WasmValueType>, results: List<WasmValueType>) {
        val typeIdx = internType(FuncType(params, results))
        imports.add(Import.Func(module, name, typeIdx))
    }

    fun importGlobal(module: String, name: String, type: WasmValueType, mutable: Boolean) {
        imports.add(Import.Global(module, name, type, mutable))
    }

    fun importMemory(module: String, name: String, minPages: Int, maxPages: Int? = null) {
        imports.add(Import.Memory(module, name, minPages, maxPages))
    }

    fun importTable(module: String, name: String, refType: WasmRefType, minSize: Int, maxSize: Int? = null) {
        imports.add(Import.Table(module, name, refType, minSize, maxSize))
    }

    fun exportFunction(name: String, funcName: String = name) {
        val idx = resolveFuncIndex(funcName)
        exports.add(Export(name, ExportKind.FUNCTION, idx))
    }

    fun startFunction(name: String) {
        startFunction = resolveFuncIndex(name)
    }

    fun dataSegment(memoryIndex: Int, offset: Int, data: ByteArray) {
        dataSegments.add(DataSegment.Active(memoryIndex, offset, data))
    }

    fun dataSegment(data: ByteArray) {
        dataSegments.add(DataSegment.Passive(data))
    }

    // Callback-style function definition

    @JvmOverloads
    fun function(
        name: String,
        params: List<WasmValueType>,
        results: List<WasmValueType>,
        exported: Boolean = false,
        body: (WasmFunction, WasmAssembler) -> Unit,
    ) {
        val fn = beginFunction(name, params, results)
        if (exported) exports.add(Export(name, ExportKind.FUNCTION, fn.funcIndex))
        body(fn, this)
        endFunction()
    }

    // Flat-style function definition

    @JvmOverloads
    fun beginFunction(name: String, params: List<WasmValueType>, results: List<WasmValueType>, exported: Boolean = false): WasmFunction {
        check(currentCode == null) { "Already inside a function" }
        val typeIdx = internType(FuncType(params, results))
        val funcIdx = importFuncCount + functions.size

        val paramLocals = params.mapIndexed { i, type -> WasmLocal(null, type, i) }
        currentCode = ByteArrayOutputStream()
        currentLocals = paramLocals.toMutableList()
        currentLocalCount = params.size
        blockDepth = 0

        names[funcIdx] = name
        functions.add(FuncDef(name, typeIdx, null, emptyList()))

        if (exported) exports.add(Export(name, ExportKind.FUNCTION, funcIdx))
        return WasmFunction(name, typeIdx, funcIdx, paramLocals)
    }

    fun endFunction() {
        check(currentCode != null) { "Not inside a function" }
        end() // implicit end opcode
        val code = currentCode!!.toByteArray()
        val declaredLocals = currentLocals.drop(functions.last().let {
            types[it.typeIndex].params.size
        })
        functions[functions.size - 1] = functions.last().copy(code = code, locals = declaredLocals)
        currentCode = null
        currentLocals.clear()
        currentLocalCount = 0
    }

    // Local variable declaration

    fun declareLocal(name: String?, type: WasmValueType): WasmLocal {
        check(currentCode != null) { "Not inside a function" }
        val local = WasmLocal(name, type, currentLocalCount++)
        currentLocals.add(local)
        return local
    }

    fun declareLocal(type: WasmValueType): WasmLocal = declareLocal(null, type)

    // Typed local/global access (convenience wrappers over generated index-based methods)

    fun localGet(local: WasmLocal) = localGet(local.index)
    fun localSet(local: WasmLocal) = localSet(local.index)
    fun localTee(local: WasmLocal) = localTee(local.index)

    // Typed control flow — callback style (auto-end)

    fun block(type: WasmBlockType = WasmBlockType.Void, body: (WasmLabel) -> Unit) {
        block(type.code)
        blockDepth++
        val label = WasmLabel(blockDepth)
        body(label)
        end()
        blockDepth--
    }

    fun loop(type: WasmBlockType = WasmBlockType.Void, body: (WasmLabel) -> Unit) {
        loop(type.code)
        blockDepth++
        val label = WasmLabel(blockDepth)
        body(label)
        end()
        blockDepth--
    }

    fun ifThen(type: WasmBlockType = WasmBlockType.Void, thenBody: () -> Unit) {
        if_(type.code)
        blockDepth++
        thenBody()
        end()
        blockDepth--
    }

    fun ifThenElse(type: WasmBlockType = WasmBlockType.Void, thenBody: () -> Unit, elseBody: () -> Unit) {
        if_(type.code)
        blockDepth++
        thenBody()
        else_()
        elseBody()
        end()
        blockDepth--
    }

    // Typed control flow — flat style (manual end)

    fun beginBlock(type: WasmBlockType = WasmBlockType.Void): WasmLabel {
        block(type.code)
        blockDepth++
        return WasmLabel(blockDepth)
    }

    fun endBlock() {
        end()
        blockDepth--
    }

    fun beginLoop(type: WasmBlockType = WasmBlockType.Void): WasmLabel {
        loop(type.code)
        blockDepth++
        return WasmLabel(blockDepth)
    }

    fun endLoop() {
        end()
        blockDepth--
    }

    fun beginIf(type: WasmBlockType = WasmBlockType.Void): WasmLabel {
        if_(type.code)
        blockDepth++
        return WasmLabel(blockDepth)
    }

    fun beginElse() {
        else_()
    }

    fun endIf() {
        end()
        blockDepth--
    }

    // Typed branch (resolves label depth automatically)

    fun br(label: WasmLabel) = br(blockDepth - label.depth)
    fun brIf(label: WasmLabel) = brIf(blockDepth - label.depth)
    fun brTable(default: WasmLabel, vararg targets: WasmLabel) {
        val labels = IntArray(targets.size + 1)
        for (i in targets.indices) labels[i] = blockDepth - targets[i].depth
        labels[targets.size] = blockDepth - default.depth
        brTable(labels)
    }

    // Call by name

    fun call(name: String) = call(resolveFuncIndex(name))

    // Assemble into .wasm binary

    fun assemble(): ByteArray {
        check(currentCode == null) { "Unclosed function" }
        val out = ByteArrayOutputStream()

        // Magic + version
        out.write(byteArrayOf(0x00, 0x61, 0x73, 0x6D)) // \0asm
        out.write(byteArrayOf(0x01, 0x00, 0x00, 0x00)) // version 1

        // Section 1: Types
        if (types.isNotEmpty()) {
            writeSection(out, 1) { s ->
                writeU32(s, types.size)
                for (ft in types) {
                    s.write(0x60) // func type
                    writeU32(s, ft.params.size)
                    for (p in ft.params) s.write(p.code)
                    writeU32(s, ft.results.size)
                    for (r in ft.results) s.write(r.code)
                }
            }
        }

        // Section 2: Imports
        if (imports.isNotEmpty()) {
            writeSection(out, 2) { s ->
                writeU32(s, imports.size)
                for (imp in imports) {
                    writeName(s, imp.module)
                    writeName(s, imp.name)
                    when (imp) {
                        is Import.Func -> { s.write(0x00); writeU32(s, imp.typeIndex) }
                        is Import.Table -> {
                            s.write(0x01); s.write(imp.refType.code)
                            writeLimits(s, imp.minSize, imp.maxSize)
                        }
                        is Import.Memory -> { s.write(0x02); writeLimits(s, imp.minPages, imp.maxPages) }
                        is Import.Global -> {
                            s.write(0x03); s.write(imp.type.code)
                            s.write(if (imp.mutable) 0x01 else 0x00)
                        }
                    }
                }
            }
        }

        // Section 3: Functions (type indices)
        if (functions.isNotEmpty()) {
            writeSection(out, 3) { s ->
                writeU32(s, functions.size)
                for (f in functions) writeU32(s, f.typeIndex)
            }
        }

        // Section 4: Tables
        if (tables.isNotEmpty()) {
            writeSection(out, 4) { s ->
                writeU32(s, tables.size)
                for (t in tables) {
                    s.write(t.refType.code)
                    writeLimits(s, t.minSize, t.maxSize)
                }
            }
        }

        // Section 5: Memories
        if (memories.isNotEmpty()) {
            writeSection(out, 5) { s ->
                writeU32(s, memories.size)
                for (m in memories) writeLimits(s, m.minPages, m.maxPages)
            }
        }

        // Section 6: Globals
        if (globals.isNotEmpty()) {
            writeSection(out, 6) { s ->
                writeU32(s, globals.size)
                for (g in globals) {
                    s.write(g.type.code)
                    s.write(if (g.mutable) 0x01 else 0x00)
                    writeConstExpr(s, g.type, g.initValue)
                }
            }
        }

        // Section 7: Exports
        if (exports.isNotEmpty()) {
            writeSection(out, 7) { s ->
                writeU32(s, exports.size)
                for (e in exports) {
                    writeName(s, e.name)
                    s.write(e.kind.code)
                    writeU32(s, e.index)
                }
            }
        }

        // Section 8: Start
        if (startFunction != null) {
            writeSection(out, 8) { s -> writeU32(s, startFunction!!) }
        }

        // Section 9: Element
        if (elements.isNotEmpty()) {
            writeSection(out, 9) { s ->
                writeU32(s, elements.size)
                for (elem in elements) {
                    writeU32(s, 0) // active, table 0
                    writeConstExprI32(s, elem.offset)
                    writeU32(s, elem.funcIndices.size)
                    for (idx in elem.funcIndices) writeU32(s, idx)
                }
            }
        }

        // Section 12: DataCount (must come before Code if data segments exist)
        if (dataSegments.isNotEmpty()) {
            writeSection(out, 12) { s -> writeU32(s, dataSegments.size) }
        }

        // Section 10: Code
        if (functions.isNotEmpty()) {
            writeSection(out, 10) { s ->
                writeU32(s, functions.size)
                for (f in functions) {
                    val body = ByteArrayOutputStream()
                    val localGroups = compressLocals(f.locals)
                    writeU32(body, localGroups.size)
                    for ((count, type) in localGroups) {
                        writeU32(body, count)
                        body.write(type.code)
                    }
                    body.write(f.code!!)
                    val bodyBytes = body.toByteArray()
                    writeU32(s, bodyBytes.size)
                    s.write(bodyBytes)
                }
            }
        }

        // Section 11: Data
        if (dataSegments.isNotEmpty()) {
            writeSection(out, 11) { s ->
                writeU32(s, dataSegments.size)
                for (seg in dataSegments) {
                    when (seg) {
                        is DataSegment.Active -> {
                            writeU32(s, 0) // active, memory 0
                            writeConstExprI32(s, seg.offset)
                            writeU32(s, seg.data.size)
                            s.write(seg.data)
                        }
                        is DataSegment.Passive -> {
                            writeU32(s, 1) // passive
                            writeU32(s, seg.data.size)
                            s.write(seg.data)
                        }
                    }
                }
            }
        }

        return out.toByteArray()
    }

    fun reset() {
        types.clear(); imports.clear(); functions.clear(); tables.clear()
        memories.clear(); globals.clear(); exports.clear(); elements.clear()
        dataSegments.clear(); names.clear(); startFunction = null
        currentCode = null; currentLocals.clear(); currentLocalCount = 0; blockDepth = 0
    }

    // Internal helpers

    private fun internType(ft: FuncType): Int {
        val existing = types.indexOf(ft)
        if (existing >= 0) return existing
        types.add(ft)
        return types.size - 1
    }

    private fun resolveFuncIndex(name: String): Int {
        // Search imports first
        var idx = 0
        for (imp in imports) {
            if (imp is Import.Func && imp.name == name) return idx
            if (imp is Import.Func) idx++
        }
        // Then defined functions
        for (f in functions) {
            if (f.name == name) return importFuncCount + functions.indexOf(f)
        }
        error("Unknown function: $name")
    }

    private fun compressLocals(locals: List<WasmLocal>): List<Pair<Int, WasmValueType>> {
        if (locals.isEmpty()) return emptyList()
        val groups = mutableListOf<Pair<Int, WasmValueType>>()
        var count = 1
        var current = locals[0].type
        for (i in 1 until locals.size) {
            if (locals[i].type == current) {
                count++
            } else {
                groups.add(count to current)
                current = locals[i].type
                count = 1
            }
        }
        groups.add(count to current)
        return groups
    }

    // Binary writing utilities

    private fun writeSection(out: ByteArrayOutputStream, id: Int, writer: (ByteArrayOutputStream) -> Unit) {
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

    private fun writeS32(out: ByteArrayOutputStream, value: Int) {
        var v = value
        var more = true
        while (more) {
            var b = v and 0x7F
            v = v shr 7
            if ((v == 0 && (b and 0x40) == 0) || (v == -1 && (b and 0x40) != 0)) {
                more = false
            } else {
                b = b or 0x80
            }
            out.write(b)
        }
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

    private fun writeConstExpr(out: ByteArrayOutputStream, type: WasmValueType, value: Long) {
        when (type) {
            WasmValueType.I32 -> { out.write(0x41); writeS32(out, value.toInt()) }
            WasmValueType.I64 -> { out.write(0x42); writeS64(out, value) }
            WasmValueType.F32 -> { out.write(0x43); writeF32(out, java.lang.Float.intBitsToFloat(value.toInt())) }
            WasmValueType.F64 -> { out.write(0x44); writeF64(out, java.lang.Double.longBitsToDouble(value)) }
            else -> error("Cannot create const expr for $type")
        }
        out.write(0x0B) // end
    }

    private fun writeConstExprI32(out: ByteArrayOutputStream, value: Int) {
        out.write(0x41)
        writeS32(out, value)
        out.write(0x0B)
    }

    private fun writeS64(out: ByteArrayOutputStream, value: Long) {
        var v = value
        var more = true
        while (more) {
            var b = (v and 0x7F).toInt()
            v = v shr 7
            if ((v == 0L && (b and 0x40) == 0) || (v == -1L && (b and 0x40) != 0)) {
                more = false
            } else {
                b = b or 0x80
            }
            out.write(b)
        }
    }

    private fun writeF32(out: ByteArrayOutputStream, value: Float) {
        val bits = java.lang.Float.floatToRawIntBits(value)
        for (i in 0 until 4) out.write((bits shr (i * 8)) and 0xFF)
    }

    private fun writeF64(out: ByteArrayOutputStream, value: Double) {
        val bits = java.lang.Double.doubleToRawLongBits(value)
        for (i in 0 until 8) out.write(((bits shr (i * 8)) and 0xFF).toInt())
    }

    // Internal data classes

    private data class FuncType(val params: List<WasmValueType>, val results: List<WasmValueType>)
    private data class FuncDef(val name: String, val typeIndex: Int, val code: ByteArray?, val locals: List<WasmLocal>) {
        fun copy(code: ByteArray?, locals: List<WasmLocal>) = FuncDef(name, typeIndex, code, locals)
    }
    private data class MemoryDef(val minPages: Int, val maxPages: Int?)
    private data class TableDef(val refType: WasmRefType, val minSize: Int, val maxSize: Int?)
    private data class GlobalDef(val type: WasmValueType, val mutable: Boolean, val initValue: Long)
    private data class Export(val name: String, val kind: ExportKind, val index: Int)
    private data class ElementSegment(val offset: Int, val funcIndices: List<Int>)

    private enum class ExportKind(val code: Int) { FUNCTION(0x00), TABLE(0x01), MEMORY(0x02), GLOBAL(0x03) }

    private sealed interface Import {
        val module: String
        val name: String
        data class Func(override val module: String, override val name: String, val typeIndex: Int) : Import
        data class Table(override val module: String, override val name: String, val refType: WasmRefType, val minSize: Int, val maxSize: Int?) : Import
        data class Memory(override val module: String, override val name: String, val minPages: Int, val maxPages: Int?) : Import
        data class Global(override val module: String, override val name: String, val type: WasmValueType, val mutable: Boolean) : Import
    }

    private sealed interface DataSegment {
        val data: ByteArray
        data class Active(val memoryIndex: Int, val offset: Int, override val data: ByteArray) : DataSegment
        data class Passive(override val data: ByteArray) : DataSegment
    }

    companion object {
        @JvmStatic fun create(): WasmAssembler = WasmAssembler()
    }
}
