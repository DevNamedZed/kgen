package org.kgen.runtime.compile

import org.kgen.binary.*
import org.kgen.codegen.CodeGenerator
import org.kgen.codegen.CompiledCode
import org.kgen.ir.instructions.*
import org.kgen.ir.target.Target
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Builds a native executable with embedded kgen metadata sections.
 *
 * Extends [NativeCompiler] by adding `.kgen.meta`, `.kgen.types`,
 * `.kgen.resources`, and `.kgen.debug` sections to the output binary.
 * These sections are non-loaded (not mapped into process memory) and
 * can be read back with [ExecutableReader].
 *
 * ```java
 * var builder = new ExecutableBuilder(Target.x86_64(), OutputPlatform.LINUX);
 * builder.addClassFile(classBytes);
 * builder.setMainClass("com/example/Main");
 * builder.addResource("config.txt", configBytes);
 * byte[] exe = builder.build();
 * ```
 */
class ExecutableBuilder(
    private val target: Target,
    private val platform: OutputPlatform = OutputPlatform.LINUX,
    private val codeGenerator: CodeGenerator? = null,
) {
    private val classFiles = mutableListOf<ByteArray>()
    private var mainClass: String? = null
    private val resources = mutableMapOf<String, ByteArray>()
    private val metadata = mutableMapOf<String, String>()
    private var version: String? = null
    private var moduleName: String? = null

    fun addClassFile(classBytes: ByteArray): ExecutableBuilder {
        classFiles.add(classBytes)
        return this
    }

    fun addClassFiles(files: List<ByteArray>): ExecutableBuilder {
        classFiles.addAll(files)
        return this
    }

    fun setMainClass(className: String): ExecutableBuilder {
        mainClass = className
        return this
    }

    fun addResource(name: String, data: ByteArray): ExecutableBuilder {
        resources[name] = data
        return this
    }

    fun setVersion(version: String): ExecutableBuilder {
        this.version = version
        return this
    }

    fun setModuleName(name: String): ExecutableBuilder {
        this.moduleName = name
        return this
    }

    fun setMetadata(key: String, value: String): ExecutableBuilder {
        metadata[key] = value
        return this
    }

    /**
     * Build the executable with embedded metadata.
     */
    fun build(): ByteArray {
        require(classFiles.isNotEmpty()) { "No class files added" }

        val compiler = NativeCompiler(target, platform, codeGenerator)
        val modules = compiler.compileToModules(classFiles)

        // Build kgen metadata sections and add to the object file
        val obj = compiler.compileToObject(classFiles, mainClass)
        val sections = obj.sections.toMutableList()

        // Add .kgen.meta section
        val metaBytes = buildMetaSection(obj)
        sections.add(Section(".kgen.meta", SectionKind.CUSTOM, metaBytes))

        // Add .kgen.types section (export types from compiled modules)
        val typesBytes = buildTypesSection(modules)
        if (typesBytes.isNotEmpty()) {
            sections.add(Section(".kgen.types", SectionKind.CUSTOM, typesBytes))
        }

        // Add .kgen.resources section
        if (resources.isNotEmpty()) {
            val resBytes = buildResourcesSection()
            sections.add(Section(".kgen.resources", SectionKind.CUSTOM, resBytes))
        }

        // Add .kgen.debug section (Java source → native code mapping)
        val debugBytes = buildDebugSection(modules, obj)
        if (debugBytes.isNotEmpty()) {
            sections.add(Section(".kgen.debug", SectionKind.CUSTOM, debugBytes))
        }

        // Create modified object file with kgen sections
        val enriched = ObjectFile(
            format = obj.format,
            arch = obj.arch,
            sections = sections,
            symbols = obj.symbols,
            relocations = obj.relocations,
            imports = obj.imports,
        )

        // Link
        return NativeCompiler(target, platform, codeGenerator).link(listOf(enriched))
    }

    private fun buildMetaSection(obj: ObjectFile): ByteArray {
        val buf = ByteArrayOutputStream()
        // Magic: "KGEN"
        buf.write("KGEN".toByteArray())
        // Version: u16
        writeU16(buf, 1)
        // Flags: u16
        writeU16(buf, 0)

        // Module name (length-prefixed UTF-8)
        val name = moduleName ?: mainClass ?: "unnamed"
        writeString(buf, name)

        // Version string
        writeString(buf, version ?: "0.0.0")

        // Target triple
        writeString(buf, obj.arch.triple)

        // Export count + names
        val exports = obj.symbols.filter { it.binding == SymbolBinding.GLOBAL && it.kind == SymbolKind.FUNCTION }
        writeU32(buf, exports.size)
        for (sym in exports) {
            writeString(buf, sym.name)
        }

        // Metadata key-value pairs
        writeU32(buf, metadata.size)
        for ((k, v) in metadata) {
            writeString(buf, k)
            writeString(buf, v)
        }

        return buf.toByteArray()
    }

    private fun buildTypesSection(modules: List<org.kgen.ir.Module>): ByteArray {
        val buf = ByteArrayOutputStream()
        // Collect exported functions with their signatures
        val functions = modules.flatMap { it.functions }
            .filter { it.linkage == org.kgen.ir.Linkage.EXTERNAL && it.blocks.isNotEmpty() }
        if (functions.isEmpty()) return ByteArray(0)

        writeU32(buf, functions.size)
        for (fn in functions) {
            writeString(buf, fn.name)
            writeString(buf, fn.returnType.toString())
            writeU16(buf, fn.params.size)
            for (p in fn.params) {
                writeString(buf, p.name)
                writeString(buf, p.type.toString())
            }
        }
        return buf.toByteArray()
    }

    private fun buildDebugSection(modules: List<org.kgen.ir.Module>, obj: ObjectFile): ByteArray {
        // Collect DebugLoc instructions from all IR functions
        val sourceFiles = mutableListOf<String>()
        val sourceFileIndex = mutableMapOf<String, Int>()
        val methods = mutableListOf<KgenDebugMethod>()

        // Build a symbol offset map from the object file
        val symbolOffsets = obj.symbols
            .filter { it.kind == SymbolKind.FUNCTION && it.binding == SymbolBinding.GLOBAL }
            .associate { it.name to it.value }

        for (module in modules) {
            for (fn in module.functions) {
                if (fn.blocks.isEmpty()) { continue }

                // Collect DebugLoc instructions from this function
                val debugLocs = fn.blocks.flatMap { block ->
                    block.instructions.filterIsInstance<DebugLoc>()
                }
                if (debugLocs.isEmpty()) { continue }

                // Determine source file from the scope (format: "ClassName.methodName" or just file)
                val sourceFile = debugLocs.firstOrNull()?.scope ?: continue
                val fileIdx = sourceFileIndex.getOrPut(sourceFile) {
                    sourceFiles.add(sourceFile)
                    sourceFiles.size - 1
                }

                val lines = debugLocs.map { it.line }
                val startLine = lines.min()
                val endLine = lines.max()

                val nativeOffset = symbolOffsets[fn.name]?.toInt() ?: 0
                // We don't have per-instruction native offsets from IR alone,
                // but we can record the line mapping with relative offset 0
                // (the actual offsets are in the DebugLineMap from codegen)
                val lineMappings = debugLocs.mapIndexed { idx, loc ->
                    KgenLineMapping(
                        nativeOffset = idx * 4, // approximate — actual offsets from codegen
                        sourceLine = loc.line,
                        sourceColumn = loc.col,
                    )
                }.distinctBy { it.sourceLine } // deduplicate same-line entries

                methods.add(KgenDebugMethod(
                    name = fn.name.substringAfterLast('_'),
                    linkageName = fn.name,
                    sourceFileIndex = fileIdx,
                    startLine = startLine,
                    endLine = endLine,
                    nativeOffset = nativeOffset,
                    nativeSize = 0, // filled by linker/loader
                    lineMappings = lineMappings,
                ))
            }
        }

        if (methods.isEmpty()) { return ByteArray(0) }

        return KgenDebugSection.write(KgenDebugSection(sourceFiles, methods))
    }

    private fun buildResourcesSection(): ByteArray {
        val buf = ByteArrayOutputStream()
        writeU32(buf, resources.size)
        for ((name, data) in resources) {
            writeString(buf, name)
            writeU32(buf, data.size)
            buf.write(data)
        }
        return buf.toByteArray()
    }

    private fun writeU16(buf: ByteArrayOutputStream, value: Int) {
        buf.write(value and 0xFF)
        buf.write((value shr 8) and 0xFF)
    }

    private fun writeU32(buf: ByteArrayOutputStream, value: Int) {
        buf.write(value and 0xFF)
        buf.write((value shr 8) and 0xFF)
        buf.write((value shr 16) and 0xFF)
        buf.write((value shr 24) and 0xFF)
    }

    private fun writeString(buf: ByteArrayOutputStream, s: String) {
        val bytes = s.toByteArray(Charsets.UTF_8)
        writeU16(buf, bytes.size)
        buf.write(bytes)
    }
}

/**
 * Reads kgen metadata from a native executable built by [ExecutableBuilder].
 *
 * Parses the `.kgen.meta`, `.kgen.types`, `.kgen.resources`, and `.kgen.debug` sections.
 *
 * ```java
 * var meta = ExecutableReader.read(exeBytes);
 * System.out.println(meta.moduleName());
 * System.out.println(meta.exports());
 * byte[] config = meta.resource("config.txt");
 * var debug = meta.debugInfo();
 * ```
 */
class ExecutableReader private constructor(
    val moduleName: String,
    val version: String,
    val targetTriple: String,
    val exports: List<String>,
    val metadata: Map<String, String>,
    val types: List<ExportedFunction>,
    val resources: Map<String, ByteArray>,
    val debugInfo: KgenDebugSection? = null,
) {

    fun resource(name: String): ByteArray? = resources[name]

    data class ExportedFunction(
        val name: String,
        val returnType: String,
        val params: List<Pair<String, String>>,
    )

    companion object {
        /**
         * Read kgen metadata from sections extracted from an executable.
         *
         * @param metaSection raw bytes of .kgen.meta section
         * @param typesSection raw bytes of .kgen.types section (or null)
         * @param resourcesSection raw bytes of .kgen.resources section (or null)
         */
        @JvmStatic
        @JvmOverloads
        fun read(
            metaSection: ByteArray,
            typesSection: ByteArray? = null,
            resourcesSection: ByteArray? = null,
            debugSection: ByteArray? = null,
        ): ExecutableReader {
            val buf = ByteBuffer.wrap(metaSection).order(ByteOrder.LITTLE_ENDIAN)

            // Magic
            val magic = ByteArray(4)
            buf.get(magic)
            require(String(magic) == "KGEN") { "Not a kgen executable (bad magic)" }

            // Version, flags
            val formatVersion = buf.getShort().toInt() and 0xFFFF
            val flags = buf.getShort().toInt() and 0xFFFF

            val moduleName = readString(buf)
            val version = readString(buf)
            val targetTriple = readString(buf)

            val exportCount = buf.getInt()
            val exports = (0 until exportCount).map { readString(buf) }

            val metaCount = buf.getInt()
            val metadata = mutableMapOf<String, String>()
            for (i in 0 until metaCount) {
                metadata[readString(buf)] = readString(buf)
            }

            // Types
            val types = if (typesSection != null && typesSection.isNotEmpty()) {
                val tbuf = ByteBuffer.wrap(typesSection).order(ByteOrder.LITTLE_ENDIAN)
                val fnCount = tbuf.getInt()
                (0 until fnCount).map {
                    val name = readString(tbuf)
                    val retType = readString(tbuf)
                    val paramCount = tbuf.getShort().toInt() and 0xFFFF
                    val params = (0 until paramCount).map {
                        readString(tbuf) to readString(tbuf)
                    }
                    ExportedFunction(name, retType, params)
                }
            } else emptyList()

            // Resources
            val resources = if (resourcesSection != null && resourcesSection.isNotEmpty()) {
                val rbuf = ByteBuffer.wrap(resourcesSection).order(ByteOrder.LITTLE_ENDIAN)
                val resCount = rbuf.getInt()
                val map = mutableMapOf<String, ByteArray>()
                for (i in 0 until resCount) {
                    val name = readString(rbuf)
                    val size = rbuf.getInt()
                    val data = ByteArray(size)
                    rbuf.get(data)
                    map[name] = data
                }
                map
            } else emptyMap()

            // Debug info
            val debugInfo = if (debugSection != null && debugSection.isNotEmpty()) {
                KgenDebugSection.read(debugSection)
            } else { null }

            return ExecutableReader(moduleName, version, targetTriple, exports, metadata, types, resources, debugInfo)
        }

        private fun readString(buf: ByteBuffer): String {
            val len = buf.getShort().toInt() and 0xFFFF
            val bytes = ByteArray(len)
            buf.get(bytes)
            return String(bytes, Charsets.UTF_8)
        }
    }
}
