package org.kgen.target.wasm.module

import org.kgen.target.wasm.*

/**
 * Parsed representation of a WebAssembly module.
 *
 * Produced by [WasmModuleReader.read] from a .wasm binary.
 * Provides structured access to all sections, types, functions, imports, exports, etc.
 *
 * ```java
 * WasmModule module = WasmModuleReader.read(bytes);
 * for (WasmModule.Export export : module.getExports()) {
 *     System.out.println(export.getName() + " -> " + export.getKind());
 * }
 * ```
 */
data class WasmModule(
    val version: Int,
    val types: List<FuncType>,
    val imports: List<Import>,
    val functions: List<Function>,
    val tables: List<Table>,
    val memories: List<Memory>,
    val globals: List<Global>,
    val exports: List<Export>,
    val start: Int?,
    val elements: List<Element>,
    val dataSegments: List<DataSegment>,
    val customSections: List<CustomSection>,
) {
    /** Number of imported functions (affects function index space). */
    val importedFunctionCount: Int get() = imports.count { it is Import.Func }
    val importedTableCount: Int get() = imports.count { it is Import.Table }
    val importedMemoryCount: Int get() = imports.count { it is Import.Memory }
    val importedGlobalCount: Int get() = imports.count { it is Import.Global }

    /** Resolve a function by index (imports first, then defined functions). */
    fun functionName(index: Int): String? {
        var importIdx = 0
        for (imp in imports) {
            if (imp is Import.Func) {
                if (importIdx == index) return imp.name
                importIdx++
            }
        }
        val localIdx = index - importedFunctionCount
        return if (localIdx in functions.indices) functions[localIdx].name else null
    }

    data class FuncType(val params: List<WasmValueType>, val results: List<WasmValueType>)

    sealed interface Import {
        val module: String
        val name: String
        data class Func(override val module: String, override val name: String, val typeIndex: Int) : Import
        data class Table(override val module: String, override val name: String, val refType: WasmRefType, val min: Int, val max: Int?) : Import
        data class Memory(override val module: String, override val name: String, val min: Int, val max: Int?) : Import
        data class Global(override val module: String, override val name: String, val type: WasmValueType, val mutable: Boolean) : Import
        data class Tag(override val module: String, override val name: String, val attribute: Int, val typeIndex: Int) : Import
    }

    data class Function(
        val name: String?,
        val typeIndex: Int,
        val locals: List<WasmValueType>,
        val body: ByteArray,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Function) return false
            return typeIndex == other.typeIndex && locals == other.locals && body.contentEquals(other.body)
        }
        override fun hashCode(): Int = 31 * typeIndex + body.contentHashCode()
    }

    data class Table(val refType: WasmRefType, val min: Int, val max: Int?)
    data class Memory(val min: Int, val max: Int?)
    data class Global(val type: WasmValueType, val mutable: Boolean, val initExpr: ByteArray) {
        override fun equals(other: Any?) = this === other || (other is Global && type == other.type && mutable == other.mutable && initExpr.contentEquals(other.initExpr))
        override fun hashCode(): Int = 31 * type.hashCode() + initExpr.contentHashCode()
    }

    data class Export(val name: String, val kind: ExportKind, val index: Int)

    enum class ExportKind(val code: Int) {
        FUNCTION(0x00), TABLE(0x01), MEMORY(0x02), GLOBAL(0x03);
        companion object {
            fun fromCode(code: Int): ExportKind = entries.first { it.code == code }
        }
    }

    /**
     * An element segment. WASM defines 8 forms (0–7) covering three modes
     * (active/passive/declarative) and two init encodings (func indices vs expressions).
     */
    sealed interface Element {
        /** Reference type for this segment (funcref or externref). */
        val refType: WasmRefType

        /** Function indices (forms 0–3). Empty for expression-based forms. */
        val funcIndices: List<Int>

        /** Init expressions as raw bytes including 0x0B terminator (forms 4–7). Empty for index-based forms. */
        val initExprs: List<ByteArray>

        /** Active element segment — initializes a table at the given offset. */
        data class Active(
            val tableIndex: Int,
            val offsetExpr: ByteArray,
            override val refType: WasmRefType,
            override val funcIndices: List<Int> = emptyList(),
            override val initExprs: List<ByteArray> = emptyList(),
        ) : Element {
            override fun equals(other: Any?) = this === other || (other is Active && tableIndex == other.tableIndex
                    && offsetExpr.contentEquals(other.offsetExpr) && refType == other.refType
                    && funcIndices == other.funcIndices && initExprs.size == other.initExprs.size
                    && initExprs.zip(other.initExprs).all { (a, b) -> a.contentEquals(b) })
            override fun hashCode(): Int = 31 * (31 * tableIndex + refType.hashCode()) + funcIndices.hashCode()
        }

        /** Passive element segment — can be used with table.init. */
        data class Passive(
            override val refType: WasmRefType,
            override val funcIndices: List<Int> = emptyList(),
            override val initExprs: List<ByteArray> = emptyList(),
        ) : Element {
            override fun equals(other: Any?) = this === other || (other is Passive && refType == other.refType
                    && funcIndices == other.funcIndices && initExprs.size == other.initExprs.size
                    && initExprs.zip(other.initExprs).all { (a, b) -> a.contentEquals(b) })
            override fun hashCode(): Int = 31 * refType.hashCode() + funcIndices.hashCode()
        }

        /** Declarative element segment — declares references for ref.func validation. */
        data class Declarative(
            override val refType: WasmRefType,
            override val funcIndices: List<Int> = emptyList(),
            override val initExprs: List<ByteArray> = emptyList(),
        ) : Element {
            override fun equals(other: Any?) = this === other || (other is Declarative && refType == other.refType
                    && funcIndices == other.funcIndices && initExprs.size == other.initExprs.size
                    && initExprs.zip(other.initExprs).all { (a, b) -> a.contentEquals(b) })
            override fun hashCode(): Int = 31 * refType.hashCode() + funcIndices.hashCode()
        }
    }

    sealed interface DataSegment {
        val data: ByteArray
        data class Active(val memoryIndex: Int, val offsetExpr: ByteArray, override val data: ByteArray) : DataSegment {
            override fun equals(other: Any?) = this === other || (other is Active && memoryIndex == other.memoryIndex && data.contentEquals(other.data))
            override fun hashCode(): Int = 31 * memoryIndex + data.contentHashCode()
        }
        data class Passive(override val data: ByteArray) : DataSegment {
            override fun equals(other: Any?) = this === other || (other is Passive && data.contentEquals(other.data))
            override fun hashCode(): Int = data.contentHashCode()
        }
    }

    data class CustomSection(val name: String, val data: ByteArray) {
        override fun equals(other: Any?) = this === other || (other is CustomSection && name == other.name && data.contentEquals(other.data))
        override fun hashCode(): Int = 31 * name.hashCode() + data.contentHashCode()
    }
}
