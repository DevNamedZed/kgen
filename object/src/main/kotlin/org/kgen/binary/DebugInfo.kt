package org.kgen.binary

// Debug info model — DWARF-based but abstract enough for other formats

data class DebugInfo(
    val compileUnits: List<CompileUnit> = emptyList(),
    val format: DebugFormat = DebugFormat.DWARF5,
)

enum class DebugFormat {
    DWARF4, DWARF5,
    CODEVIEW,            // Windows / PDB
    STABS,               // legacy Unix
}

data class CompileUnit(
    val name: String,                    // source file name
    val directory: String,               // compilation directory
    val producer: String,                // compiler identification
    val language: SourceLanguage,
    val lineInfo: List<LineEntry> = emptyList(),
    val types: List<DebugType> = emptyList(),
    val variables: List<DebugVariable> = emptyList(),
    val subprograms: List<DebugSubprogram> = emptyList(),
    val lowPC: Long = 0,
    val highPC: Long = 0,
)

data class LineEntry(
    val address: Long,
    val file: String,
    val line: Int,
    val column: Int = 0,
    val isStatement: Boolean = true,
    val isBasicBlockStart: Boolean = false,
    val isPrologueEnd: Boolean = false,
    val isEpilogueBegin: Boolean = false,
    val discriminator: Int = 0,
)

data class DebugSubprogram(
    val name: String,
    val linkageName: String? = null,
    val file: String,
    val line: Int,
    val returnType: DebugType?,
    val params: List<DebugVariable>,
    val localVariables: List<DebugVariable> = emptyList(),
    val lowPC: Long = 0,
    val highPC: Long = 0,
    val isDefinition: Boolean = true,
    val isInlined: Boolean = false,
    val inlinedAt: InlineSite? = null,
    val lexicalBlocks: List<LexicalBlock> = emptyList(),
)

data class DebugVariable(
    val name: String,
    val type: DebugType,
    val file: String? = null,
    val line: Int = 0,
    val location: DebugLocation? = null,
    val isParameter: Boolean = false,
    val isArtificial: Boolean = false,
)

sealed interface DebugLocation {
    data class Register(val regNum: Int) : DebugLocation
    data class FrameOffset(val offset: Int) : DebugLocation
    data class Address(val address: Long) : DebugLocation
    data class Expression(val ops: List<DwarfOp>) : DebugLocation
}

data class DwarfOp(val op: Int, val operands: List<Long> = emptyList())

sealed interface DebugType {
    val name: String
    val sizeInBits: Long

    data class Base(override val name: String, override val sizeInBits: Long, val encoding: DwarfEncoding) : DebugType
    data class Pointer(val pointee: DebugType, override val sizeInBits: Long) : DebugType {
        override val name: String get() = "${pointee.name}*"
    }
    data class Reference(val referent: DebugType, override val sizeInBits: Long) : DebugType {
        override val name: String get() = "${referent.name}&"
    }
    data class Array(val element: DebugType, val count: Long, override val sizeInBits: Long) : DebugType {
        override val name: String get() = "${element.name}[$count]"
    }
    data class Composite(
        override val name: String,
        override val sizeInBits: Long,
        val tag: CompositeTag,
        val members: List<DebugMember>,
        val file: String? = null,
        val line: Int = 0,
    ) : DebugType

    data class Enum(
        override val name: String,
        override val sizeInBits: Long,
        val baseType: DebugType,
        val enumerators: List<Pair<String, Long>>,
    ) : DebugType

    data class Subroutine(
        val returnType: DebugType?,
        val paramTypes: List<DebugType>,
    ) : DebugType {
        override val name: String get() = "(${paramTypes.joinToString(", ") { it.name }}) -> ${returnType?.name ?: "void"}"
        override val sizeInBits: Long get() = 0
    }

    data class Typedef(override val name: String, val baseType: DebugType) : DebugType {
        override val sizeInBits: Long get() = baseType.sizeInBits
    }

    data class Const(val baseType: DebugType) : DebugType {
        override val name: String get() = "const ${baseType.name}"
        override val sizeInBits: Long get() = baseType.sizeInBits
    }

    data class Volatile(val baseType: DebugType) : DebugType {
        override val name: String get() = "volatile ${baseType.name}"
        override val sizeInBits: Long get() = baseType.sizeInBits
    }
}

data class DebugMember(
    val name: String,
    val type: DebugType,
    val offsetInBits: Long,
    val sizeInBits: Long = type.sizeInBits,
    val accessibility: DebugAccessibility = DebugAccessibility.PUBLIC,
    val isStatic: Boolean = false,
    val isVirtual: Boolean = false,
    val isBitField: Boolean = false,
)

enum class CompositeTag { STRUCT, CLASS, UNION, INTERFACE }
enum class DebugAccessibility { PUBLIC, PROTECTED, PRIVATE }
enum class DwarfEncoding { ADDRESS, BOOLEAN, FLOAT, SIGNED, UNSIGNED, SIGNED_CHAR, UNSIGNED_CHAR, UTF }

data class InlineSite(val file: String, val line: Int, val column: Int)
data class LexicalBlock(val lowPC: Long, val highPC: Long, val variables: List<DebugVariable>)

enum class SourceLanguage {
    C, C_PLUS_PLUS, JAVA, KOTLIN, RUST, SWIFT, GO, PYTHON,
    D, FORTRAN, ADA, PASCAL, ASSEMBLY, HASKELL, SCALA,
    C_SHARP, JAVASCRIPT, TYPESCRIPT, ZIG, ODIN, NIM,
    CUSTOM,
}
