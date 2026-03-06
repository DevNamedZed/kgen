package org.kgen.binary

// Object file readers and writers

interface ObjectFileWriter {
    val format: ObjectFormat

    fun write(obj: ObjectFile): ByteArray

    fun supportsArchitecture(arch: ArchType): Boolean
}

interface ObjectFileReader {
    val format: ObjectFormat

    fun read(bytes: ByteArray): ObjectFile

    fun canRead(bytes: ByteArray): Boolean
}

// Detect format from magic bytes
fun detectFormat(bytes: ByteArray): ObjectFormat? {
    if (bytes.size < 4) return null
    return when {
        // ELF: 0x7f 'E' 'L' 'F'
        bytes[0] == 0x7f.toByte() && bytes[1] == 0x45.toByte() &&
                bytes[2] == 0x4c.toByte() && bytes[3] == 0x46.toByte() -> ObjectFormat.ELF

        // PE: 'M' 'Z'
        bytes[0] == 0x4d.toByte() && bytes[1] == 0x5a.toByte() -> ObjectFormat.PE_COFF

        // Mach-O: magic numbers (32/64, LE/BE)
        bytes.size >= 4 && (
            (bytes[0] == 0xfe.toByte() && bytes[1] == 0xed.toByte() && bytes[2] == 0xfa.toByte() && bytes[3] == 0xce.toByte()) ||
            (bytes[0] == 0xce.toByte() && bytes[1] == 0xfa.toByte() && bytes[2] == 0xed.toByte() && bytes[3] == 0xfe.toByte()) ||
            (bytes[0] == 0xfe.toByte() && bytes[1] == 0xed.toByte() && bytes[2] == 0xfa.toByte() && bytes[3] == 0xcf.toByte()) ||
            (bytes[0] == 0xcf.toByte() && bytes[1] == 0xfa.toByte() && bytes[2] == 0xed.toByte() && bytes[3] == 0xfe.toByte())
        ) -> ObjectFormat.MACH_O

        // WASM: 0x00 'a' 's' 'm'
        bytes[0] == 0x00.toByte() && bytes[1] == 0x61.toByte() &&
                bytes[2] == 0x73.toByte() && bytes[3] == 0x6d.toByte() -> ObjectFormat.WASM_MODULE

        // JVM .class: 0xCA 0xFE 0xBA 0xBE
        bytes[0] == 0xca.toByte() && bytes[1] == 0xfe.toByte() &&
                bytes[2] == 0xba.toByte() && bytes[3] == 0xbe.toByte() -> ObjectFormat.JVM_CLASS

        // COFF: starts with machine type (no MZ — raw COFF, not PE)
        isCoffHeader(bytes) -> ObjectFormat.PE_COFF

        else -> null
    }
}

private fun isCoffHeader(bytes: ByteArray): Boolean {
    if (bytes.size < 2) return false
    val machine = (bytes[0].toInt() and 0xFF) or ((bytes[1].toInt() and 0xFF) shl 8)
    return machine in setOf(
        0x8664,  // AMD64
        0xAA64,  // ARM64
        0x01c0,  // ARM
        0x014c,  // i386
    )
}
