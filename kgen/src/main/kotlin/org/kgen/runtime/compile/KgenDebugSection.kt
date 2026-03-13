package org.kgen.runtime.compile

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Model for the `.kgen.debug` section in kgen native executables.
 *
 * Maps Java source file locations to native code offsets, enabling
 * stack traces and debuggers to show Java source context for compiled
 * native code.
 *
 * ```java
 * var debug = KgenDebugSection.read(sectionBytes);
 * for (var method : debug.methods()) {
 *     System.out.println(method.name() + " from " + debug.sourceFile(method.sourceFileIndex()));
 *     for (var mapping : method.lineMappings()) {
 *         System.out.printf("  native +0x%x → line %d%n", mapping.nativeOffset(), mapping.sourceLine());
 *     }
 * }
 * ```
 */
data class KgenDebugSection(
    val sourceFiles: List<String>,
    val methods: List<KgenDebugMethod>,
) {
    fun sourceFile(index: Int): String = sourceFiles[index]

    companion object {
        private const val MAGIC = "KDBG"
        private const val VERSION = 1

        @JvmStatic
        fun write(debug: KgenDebugSection): ByteArray {
            val buf = ByteArrayOutputStream()

            // Header
            buf.write(MAGIC.toByteArray())
            writeU16(buf, VERSION)
            writeU16(buf, 0) // flags

            // Source files
            writeU32(buf, debug.sourceFiles.size)
            for (file in debug.sourceFiles) {
                writeString(buf, file)
            }

            // Methods
            writeU32(buf, debug.methods.size)
            for (method in debug.methods) {
                writeString(buf, method.name)
                writeString(buf, method.linkageName)
                writeU16(buf, method.sourceFileIndex)
                writeU32(buf, method.startLine)
                writeU32(buf, method.endLine)
                writeU32(buf, method.nativeOffset)
                writeU32(buf, method.nativeSize)

                // Line mappings
                writeU16(buf, method.lineMappings.size)
                for (mapping in method.lineMappings) {
                    writeU16(buf, mapping.nativeOffset)
                    writeU32(buf, mapping.sourceLine)
                    writeU16(buf, mapping.sourceColumn)
                }
            }

            return buf.toByteArray()
        }

        @JvmStatic
        fun read(data: ByteArray): KgenDebugSection {
            val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

            // Header
            val magic = ByteArray(4)
            buf.get(magic)
            require(String(magic) == MAGIC) { "Not a kgen debug section (bad magic: ${String(magic)})" }
            val version = buf.getShort().toInt() and 0xFFFF
            val flags = buf.getShort().toInt() and 0xFFFF

            // Source files
            val fileCount = buf.getInt()
            val sourceFiles = (0 until fileCount).map { readString(buf) }

            // Methods
            val methodCount = buf.getInt()
            val methods = (0 until methodCount).map {
                val name = readString(buf)
                val linkageName = readString(buf)
                val sourceFileIndex = buf.getShort().toInt() and 0xFFFF
                val startLine = buf.getInt()
                val endLine = buf.getInt()
                val nativeOffset = buf.getInt()
                val nativeSize = buf.getInt()

                val mappingCount = buf.getShort().toInt() and 0xFFFF
                val lineMappings = (0 until mappingCount).map {
                    val nativeOff = buf.getShort().toInt() and 0xFFFF
                    val sourceLine = buf.getInt()
                    val sourceColumn = buf.getShort().toInt() and 0xFFFF
                    KgenLineMapping(nativeOff, sourceLine, sourceColumn)
                }

                KgenDebugMethod(name, linkageName, sourceFileIndex, startLine, endLine, nativeOffset, nativeSize, lineMappings)
            }

            return KgenDebugSection(sourceFiles, methods)
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

        private fun readString(buf: ByteBuffer): String {
            val len = buf.getShort().toInt() and 0xFFFF
            val bytes = ByteArray(len)
            buf.get(bytes)
            return String(bytes, Charsets.UTF_8)
        }
    }
}

/**
 * Debug mapping for a single compiled method.
 *
 * @param name the Java method name (e.g., "add")
 * @param linkageName the native symbol name (e.g., "AddOps_add")
 * @param sourceFileIndex index into [KgenDebugSection.sourceFiles]
 * @param startLine first source line of the method
 * @param endLine last source line of the method
 * @param nativeOffset byte offset in `.text` section where this method starts
 * @param nativeSize byte size of the compiled native code
 * @param lineMappings per-instruction source line mappings
 */
data class KgenDebugMethod(
    val name: String,
    val linkageName: String,
    val sourceFileIndex: Int,
    val startLine: Int,
    val endLine: Int,
    val nativeOffset: Int,
    val nativeSize: Int,
    val lineMappings: List<KgenLineMapping>,
)

/**
 * Maps a native code offset (relative to method start) to a Java source location.
 *
 * @param nativeOffset byte offset from the start of the method's native code
 * @param sourceLine Java source line number
 * @param sourceColumn Java source column (0 if unavailable)
 */
data class KgenLineMapping(
    val nativeOffset: Int,
    val sourceLine: Int,
    val sourceColumn: Int = 0,
)
