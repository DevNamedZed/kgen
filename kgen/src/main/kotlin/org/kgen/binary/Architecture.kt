package org.kgen.binary

data class Architecture(
    val arch: ArchType,
    val subArch: String? = null,
    val vendor: String? = null,
    val os: String? = null,
    val environment: String? = null,
    val endianness: Endianness = Endianness.LITTLE,
    val pointerSize: Int = 8,  // bytes
    val features: Set<String> = emptySet(),
) {
    val triple: String
        get() = listOfNotNull(arch.canonicalName, vendor, os, environment).joinToString("-")

    companion object {
        val X86_64_LINUX = Architecture(ArchType.X86_64, os = "linux", environment = "gnu")
        val X86_64_WINDOWS = Architecture(ArchType.X86_64, os = "windows", environment = "msvc")
        val X86_64_MACOS = Architecture(ArchType.X86_64, vendor = "apple", os = "macos")
        val AARCH64_LINUX = Architecture(ArchType.AARCH64, os = "linux", environment = "gnu")
        val AARCH64_MACOS = Architecture(ArchType.AARCH64, vendor = "apple", os = "macos")
        val AARCH64_IOS = Architecture(ArchType.AARCH64, vendor = "apple", os = "ios")
        val WASM32 = Architecture(ArchType.WASM32, pointerSize = 4)
        val WASM64 = Architecture(ArchType.WASM64)
        val JVM = Architecture(ArchType.JVM, endianness = Endianness.BIG)
        val MSIL = Architecture(ArchType.MSIL)
        val MSIL_MIXED = Architecture(ArchType.MSIL_MIXED)
    }
}

enum class ArchType(val canonicalName: String, val bits: Int) {
    X86("i386", 32),
    X86_64("x86_64", 64),
    AARCH64("aarch64", 64),
    ARM("arm", 32),
    ARM_THUMB("thumb", 32),
    RISCV32("riscv32", 32),
    RISCV64("riscv64", 64),
    MIPS("mips", 32),
    MIPS64("mips64", 64),
    POWERPC("powerpc", 32),
    POWERPC64("powerpc64", 64),
    S390X("s390x", 64),
    SPARC("sparc", 32),
    SPARC64("sparc64", 64),
    WASM32("wasm32", 32),
    WASM64("wasm64", 64),
    JVM("jvm", 64),
    LOONGARCH64("loongarch64", 64),
    MSIL("msil", 64),
    MSIL_MIXED("msil-mixed", 64),
}

enum class Endianness { LITTLE, BIG }
