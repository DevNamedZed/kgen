package org.kgen.binary.jvm

/**
 * Structured representations of well-known JVM class file attributes.
 *
 * These are parsed from raw [AttributeInfo] data and provide typed access
 * to attribute contents. Use [AttributeParser] to decode them.
 */

data class CodeAttribute(
    val maxStack: Int,
    val maxLocals: Int,
    val code: ByteArray,
    val exceptionTable: List<ExceptionEntry>,
    val attributes: List<AttributeInfo>,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CodeAttribute) return false
        return maxStack == other.maxStack && maxLocals == other.maxLocals &&
                code.contentEquals(other.code) && exceptionTable == other.exceptionTable &&
                attributes == other.attributes
    }

    override fun hashCode(): Int {
        var h = maxStack * 31 + maxLocals
        h = h * 31 + code.contentHashCode()
        h = h * 31 + exceptionTable.hashCode()
        h = h * 31 + attributes.hashCode()
        return h
    }
}

data class ExceptionEntry(
    val startPc: Int,
    val endPc: Int,
    val handlerPc: Int,
    val catchType: Int,
)

data class LineNumberEntry(val startPc: Int, val lineNumber: Int)

data class LineNumberTableAttribute(val entries: List<LineNumberEntry>)

data class LocalVariableEntry(
    val startPc: Int,
    val length: Int,
    val nameIndex: Int,
    val descriptorIndex: Int,
    val index: Int,
)

data class LocalVariableTableAttribute(val entries: List<LocalVariableEntry>)

data class LocalVariableTypeEntry(
    val startPc: Int,
    val length: Int,
    val nameIndex: Int,
    val signatureIndex: Int,
    val index: Int,
)

data class LocalVariableTypeTableAttribute(val entries: List<LocalVariableTypeEntry>)

data class SourceFileAttribute(val sourceFileIndex: Int)

data class InnerClassEntry(
    val innerClassInfoIndex: Int,
    val outerClassInfoIndex: Int,
    val innerNameIndex: Int,
    val innerClassAccessFlags: Int,
)

data class InnerClassesAttribute(val classes: List<InnerClassEntry>)

data class EnclosingMethodAttribute(val classIndex: Int, val methodIndex: Int)

data class SignatureAttribute(val signatureIndex: Int)

data class BootstrapMethodEntry(
    val methodRefIndex: Int,
    val arguments: List<Int>,
)

data class BootstrapMethodsAttribute(val methods: List<BootstrapMethodEntry>)

data class NestHostAttribute(val hostClassIndex: Int)
data class NestMembersAttribute(val classes: List<Int>)
data class PermittedSubclassesAttribute(val classes: List<Int>)
data class RecordComponentInfo(
    val nameIndex: Int,
    val descriptorIndex: Int,
    val attributes: List<AttributeInfo>,
)
data class RecordAttribute(val components: List<RecordComponentInfo>)

data class AnnotationValue(val tag: Char, val value: Any)

data class Annotation(
    val typeIndex: Int,
    val elementValuePairs: List<Pair<Int, AnnotationValue>>,
)

data class RuntimeAnnotationsAttribute(val annotations: List<Annotation>)

data class MethodParameterEntry(val nameIndex: Int, val accessFlags: Int)
data class MethodParametersAttribute(val parameters: List<MethodParameterEntry>)

data class ExceptionsAttribute(val exceptionIndexTable: List<Int>)

data class ConstantValueAttribute(val constantValueIndex: Int)

// Stack map table types
sealed class VerificationType {
    data object Top : VerificationType()
    data object Integer : VerificationType()
    data object Float : VerificationType()
    data object Double : VerificationType()
    data object Long : VerificationType()
    data object Null : VerificationType()
    data object UninitializedThis : VerificationType()
    data class Object(val cpoolIndex: Int) : VerificationType()
    data class Uninitialized(val offset: Int) : VerificationType()
}

sealed class StackMapFrame {
    abstract val offsetDelta: Int

    data class Same(override val offsetDelta: Int) : StackMapFrame()
    data class SameLocals1Stack(override val offsetDelta: Int, val stack: VerificationType) : StackMapFrame()
    data class Chop(override val offsetDelta: Int, val k: Int) : StackMapFrame()
    data class Append(override val offsetDelta: Int, val locals: List<VerificationType>) : StackMapFrame()
    data class Full(
        override val offsetDelta: Int,
        val locals: List<VerificationType>,
        val stack: List<VerificationType>,
    ) : StackMapFrame()
}

data class StackMapTableAttribute(val entries: List<StackMapFrame>)
