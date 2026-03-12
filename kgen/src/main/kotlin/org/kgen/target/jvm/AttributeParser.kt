package org.kgen.target.jvm

import java.io.ByteArrayInputStream
import java.io.DataInputStream

/**
 * Parses raw [AttributeInfo] data into structured attribute types.
 *
 * ```kotlin
 * val cf = JvmClassReader.read(bytes)
 * for (method in cf.methods) {
 *     val codeAttr = method.attributes
 *         .firstOrNull { cf.string(it.nameIndex) == "Code" }
 *         ?.let { AttributeParser.parseCode(it, cf.constantPool) }
 *
 *     if (codeAttr != null) {
 *         println("maxStack=${codeAttr.maxStack} maxLocals=${codeAttr.maxLocals}")
 *         println("bytecode: ${codeAttr.code.size} bytes")
 *     }
 * }
 * ```
 */
object AttributeParser {

    fun parseCode(attr: AttributeInfo, pool: ConstantPool): CodeAttribute {
        val dis = DataInputStream(ByteArrayInputStream(attr.data))
        val maxStack = dis.readUnsignedShort()
        val maxLocals = dis.readUnsignedShort()
        val codeLen = dis.readInt()
        val code = ByteArray(codeLen)
        dis.readFully(code)

        val excCount = dis.readUnsignedShort()
        val exceptions = (0 until excCount).map {
            ExceptionEntry(dis.readUnsignedShort(), dis.readUnsignedShort(),
                dis.readUnsignedShort(), dis.readUnsignedShort())
        }

        val attrCount = dis.readUnsignedShort()
        val attributes = (0 until attrCount).map {
            val nameIdx = dis.readUnsignedShort()
            val len = dis.readInt()
            val data = ByteArray(len)
            dis.readFully(data)
            AttributeInfo(nameIdx, data)
        }

        return CodeAttribute(maxStack, maxLocals, code, exceptions, attributes)
    }

    fun parseLineNumberTable(attr: AttributeInfo): LineNumberTableAttribute {
        val dis = DataInputStream(ByteArrayInputStream(attr.data))
        val count = dis.readUnsignedShort()
        val entries = (0 until count).map {
            LineNumberEntry(dis.readUnsignedShort(), dis.readUnsignedShort())
        }
        return LineNumberTableAttribute(entries)
    }

    fun parseLocalVariableTable(attr: AttributeInfo): LocalVariableTableAttribute {
        val dis = DataInputStream(ByteArrayInputStream(attr.data))
        val count = dis.readUnsignedShort()
        val entries = (0 until count).map {
            LocalVariableEntry(
                dis.readUnsignedShort(), dis.readUnsignedShort(),
                dis.readUnsignedShort(), dis.readUnsignedShort(), dis.readUnsignedShort(),
            )
        }
        return LocalVariableTableAttribute(entries)
    }

    fun parseLocalVariableTypeTable(attr: AttributeInfo): LocalVariableTypeTableAttribute {
        val dis = DataInputStream(ByteArrayInputStream(attr.data))
        val count = dis.readUnsignedShort()
        val entries = (0 until count).map {
            LocalVariableTypeEntry(
                dis.readUnsignedShort(), dis.readUnsignedShort(),
                dis.readUnsignedShort(), dis.readUnsignedShort(), dis.readUnsignedShort(),
            )
        }
        return LocalVariableTypeTableAttribute(entries)
    }

    fun parseSourceFile(attr: AttributeInfo): SourceFileAttribute {
        val dis = DataInputStream(ByteArrayInputStream(attr.data))
        return SourceFileAttribute(dis.readUnsignedShort())
    }

    fun parseInnerClasses(attr: AttributeInfo): InnerClassesAttribute {
        val dis = DataInputStream(ByteArrayInputStream(attr.data))
        val count = dis.readUnsignedShort()
        val classes = (0 until count).map {
            InnerClassEntry(
                dis.readUnsignedShort(), dis.readUnsignedShort(),
                dis.readUnsignedShort(), dis.readUnsignedShort(),
            )
        }
        return InnerClassesAttribute(classes)
    }

    fun parseEnclosingMethod(attr: AttributeInfo): EnclosingMethodAttribute {
        val dis = DataInputStream(ByteArrayInputStream(attr.data))
        return EnclosingMethodAttribute(dis.readUnsignedShort(), dis.readUnsignedShort())
    }

    fun parseSignature(attr: AttributeInfo): SignatureAttribute {
        val dis = DataInputStream(ByteArrayInputStream(attr.data))
        return SignatureAttribute(dis.readUnsignedShort())
    }

    fun parseBootstrapMethods(attr: AttributeInfo): BootstrapMethodsAttribute {
        val dis = DataInputStream(ByteArrayInputStream(attr.data))
        val count = dis.readUnsignedShort()
        val methods = (0 until count).map {
            val methodRef = dis.readUnsignedShort()
            val argCount = dis.readUnsignedShort()
            val args = (0 until argCount).map { dis.readUnsignedShort() }
            BootstrapMethodEntry(methodRef, args)
        }
        return BootstrapMethodsAttribute(methods)
    }

    fun parseNestHost(attr: AttributeInfo): NestHostAttribute {
        val dis = DataInputStream(ByteArrayInputStream(attr.data))
        return NestHostAttribute(dis.readUnsignedShort())
    }

    fun parseNestMembers(attr: AttributeInfo): NestMembersAttribute {
        val dis = DataInputStream(ByteArrayInputStream(attr.data))
        val count = dis.readUnsignedShort()
        return NestMembersAttribute((0 until count).map { dis.readUnsignedShort() })
    }

    fun parsePermittedSubclasses(attr: AttributeInfo): PermittedSubclassesAttribute {
        val dis = DataInputStream(ByteArrayInputStream(attr.data))
        val count = dis.readUnsignedShort()
        return PermittedSubclassesAttribute((0 until count).map { dis.readUnsignedShort() })
    }

    fun parseRecord(attr: AttributeInfo): RecordAttribute {
        val dis = DataInputStream(ByteArrayInputStream(attr.data))
        val count = dis.readUnsignedShort()
        val components = (0 until count).map {
            val nameIdx = dis.readUnsignedShort()
            val descIdx = dis.readUnsignedShort()
            val attrCount = dis.readUnsignedShort()
            val attrs = (0 until attrCount).map {
                val aNi = dis.readUnsignedShort()
                val aLen = dis.readInt()
                val aData = ByteArray(aLen)
                dis.readFully(aData)
                AttributeInfo(aNi, aData)
            }
            RecordComponentInfo(nameIdx, descIdx, attrs)
        }
        return RecordAttribute(components)
    }

    fun parseConstantValue(attr: AttributeInfo): ConstantValueAttribute {
        val dis = DataInputStream(ByteArrayInputStream(attr.data))
        return ConstantValueAttribute(dis.readUnsignedShort())
    }

    fun parseExceptions(attr: AttributeInfo): ExceptionsAttribute {
        val dis = DataInputStream(ByteArrayInputStream(attr.data))
        val count = dis.readUnsignedShort()
        return ExceptionsAttribute((0 until count).map { dis.readUnsignedShort() })
    }

    fun parseMethodParameters(attr: AttributeInfo): MethodParametersAttribute {
        val dis = DataInputStream(ByteArrayInputStream(attr.data))
        val count = dis.readUnsignedByte()
        val params = (0 until count).map {
            MethodParameterEntry(dis.readUnsignedShort(), dis.readUnsignedShort())
        }
        return MethodParametersAttribute(params)
    }

    fun parseStackMapTable(attr: AttributeInfo): StackMapTableAttribute {
        val dis = DataInputStream(ByteArrayInputStream(attr.data))
        val count = dis.readUnsignedShort()
        val frames = (0 until count).map { readStackMapFrame(dis) }
        return StackMapTableAttribute(frames)
    }

    private fun readStackMapFrame(dis: DataInputStream): StackMapFrame {
        val frameType = dis.readUnsignedByte()
        return when {
            frameType in 0..63 ->
                StackMapFrame.Same(frameType)
            frameType in 64..127 ->
                StackMapFrame.SameLocals1Stack(frameType - 64, readVerificationType(dis))
            frameType == 247 -> {
                val delta = dis.readUnsignedShort()
                StackMapFrame.SameLocals1Stack(delta, readVerificationType(dis))
            }
            frameType in 248..250 -> {
                val delta = dis.readUnsignedShort()
                StackMapFrame.Chop(delta, 251 - frameType)
            }
            frameType == 251 ->
                StackMapFrame.Same(dis.readUnsignedShort())
            frameType in 252..254 -> {
                val delta = dis.readUnsignedShort()
                val locals = (0 until frameType - 251).map { readVerificationType(dis) }
                StackMapFrame.Append(delta, locals)
            }
            frameType == 255 -> {
                val delta = dis.readUnsignedShort()
                val localCount = dis.readUnsignedShort()
                val locals = (0 until localCount).map { readVerificationType(dis) }
                val stackCount = dis.readUnsignedShort()
                val stack = (0 until stackCount).map { readVerificationType(dis) }
                StackMapFrame.Full(delta, locals, stack)
            }
            else -> throw IllegalArgumentException("Unknown stack map frame type: $frameType")
        }
    }

    private fun readVerificationType(dis: DataInputStream): VerificationType {
        return when (dis.readUnsignedByte()) {
            0 -> VerificationType.Top
            1 -> VerificationType.Integer
            2 -> VerificationType.Float
            3 -> VerificationType.Double
            4 -> VerificationType.Long
            5 -> VerificationType.Null
            6 -> VerificationType.UninitializedThis
            7 -> VerificationType.Object(dis.readUnsignedShort())
            8 -> VerificationType.Uninitialized(dis.readUnsignedShort())
            else -> VerificationType.Top
        }
    }

    fun parse(attr: AttributeInfo, pool: ConstantPool): Any? {
        val name = pool.utf8(attr.nameIndex)
        return when (name) {
            "Code" -> parseCode(attr, pool)
            "LineNumberTable" -> parseLineNumberTable(attr)
            "LocalVariableTable" -> parseLocalVariableTable(attr)
            "LocalVariableTypeTable" -> parseLocalVariableTypeTable(attr)
            "SourceFile" -> parseSourceFile(attr)
            "InnerClasses" -> parseInnerClasses(attr)
            "EnclosingMethod" -> parseEnclosingMethod(attr)
            "Signature" -> parseSignature(attr)
            "BootstrapMethods" -> parseBootstrapMethods(attr)
            "NestHost" -> parseNestHost(attr)
            "NestMembers" -> parseNestMembers(attr)
            "PermittedSubclasses" -> parsePermittedSubclasses(attr)
            "Record" -> parseRecord(attr)
            "ConstantValue" -> parseConstantValue(attr)
            "Exceptions" -> parseExceptions(attr)
            "MethodParameters" -> parseMethodParameters(attr)
            "StackMapTable" -> parseStackMapTable(attr)
            else -> null // unknown attribute — raw data preserved in AttributeInfo
        }
    }
}
