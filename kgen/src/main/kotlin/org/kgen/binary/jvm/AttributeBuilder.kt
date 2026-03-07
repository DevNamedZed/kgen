package org.kgen.binary.jvm

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

/**
 * Serializes structured attribute types back to raw [AttributeInfo] data.
 *
 * This is the inverse of [AttributeParser] — it takes typed attribute objects
 * and produces the binary representation for embedding in a class file.
 *
 * ```kotlin
 * val codeAttrInfo = AttributeBuilder.buildCode(
 *     nameIndex = cp.utf8("Code"),
 *     code = CodeAttribute(maxStack = 2, maxLocals = 1, code = bytecode,
 *         exceptionTable = emptyList(), attributes = emptyList()),
 * )
 * ```
 */
object AttributeBuilder {

    fun buildCode(nameIndex: Int, code: CodeAttribute): AttributeInfo {
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)
        dos.writeShort(code.maxStack)
        dos.writeShort(code.maxLocals)
        dos.writeInt(code.code.size)
        dos.write(code.code)
        dos.writeShort(code.exceptionTable.size)
        for (e in code.exceptionTable) {
            dos.writeShort(e.startPc)
            dos.writeShort(e.endPc)
            dos.writeShort(e.handlerPc)
            dos.writeShort(e.catchType)
        }
        dos.writeShort(code.attributes.size)
        for (a in code.attributes) {
            dos.writeShort(a.nameIndex)
            dos.writeInt(a.data.size)
            dos.write(a.data)
        }
        dos.flush()
        return AttributeInfo(nameIndex, baos.toByteArray())
    }

    fun buildLineNumberTable(nameIndex: Int, table: LineNumberTableAttribute): AttributeInfo {
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)
        dos.writeShort(table.entries.size)
        for (e in table.entries) {
            dos.writeShort(e.startPc)
            dos.writeShort(e.lineNumber)
        }
        dos.flush()
        return AttributeInfo(nameIndex, baos.toByteArray())
    }

    fun buildSourceFile(nameIndex: Int, attr: SourceFileAttribute): AttributeInfo {
        val baos = ByteArrayOutputStream(2)
        val dos = DataOutputStream(baos)
        dos.writeShort(attr.sourceFileIndex)
        dos.flush()
        return AttributeInfo(nameIndex, baos.toByteArray())
    }

    fun buildConstantValue(nameIndex: Int, attr: ConstantValueAttribute): AttributeInfo {
        val baos = ByteArrayOutputStream(2)
        val dos = DataOutputStream(baos)
        dos.writeShort(attr.constantValueIndex)
        dos.flush()
        return AttributeInfo(nameIndex, baos.toByteArray())
    }

    fun buildExceptions(nameIndex: Int, attr: ExceptionsAttribute): AttributeInfo {
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)
        dos.writeShort(attr.exceptionIndexTable.size)
        for (idx in attr.exceptionIndexTable) dos.writeShort(idx)
        dos.flush()
        return AttributeInfo(nameIndex, baos.toByteArray())
    }

    fun buildInnerClasses(nameIndex: Int, attr: InnerClassesAttribute): AttributeInfo {
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)
        dos.writeShort(attr.classes.size)
        for (c in attr.classes) {
            dos.writeShort(c.innerClassInfoIndex)
            dos.writeShort(c.outerClassInfoIndex)
            dos.writeShort(c.innerNameIndex)
            dos.writeShort(c.innerClassAccessFlags)
        }
        dos.flush()
        return AttributeInfo(nameIndex, baos.toByteArray())
    }

    fun buildSignature(nameIndex: Int, attr: SignatureAttribute): AttributeInfo {
        val baos = ByteArrayOutputStream(2)
        val dos = DataOutputStream(baos)
        dos.writeShort(attr.signatureIndex)
        dos.flush()
        return AttributeInfo(nameIndex, baos.toByteArray())
    }

    fun buildBootstrapMethods(nameIndex: Int, attr: BootstrapMethodsAttribute): AttributeInfo {
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)
        dos.writeShort(attr.methods.size)
        for (m in attr.methods) {
            dos.writeShort(m.methodRefIndex)
            dos.writeShort(m.arguments.size)
            for (a in m.arguments) dos.writeShort(a)
        }
        dos.flush()
        return AttributeInfo(nameIndex, baos.toByteArray())
    }

    fun buildNestHost(nameIndex: Int, attr: NestHostAttribute): AttributeInfo {
        val baos = ByteArrayOutputStream(2)
        val dos = DataOutputStream(baos)
        dos.writeShort(attr.hostClassIndex)
        dos.flush()
        return AttributeInfo(nameIndex, baos.toByteArray())
    }

    fun buildNestMembers(nameIndex: Int, attr: NestMembersAttribute): AttributeInfo {
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)
        dos.writeShort(attr.classes.size)
        for (c in attr.classes) dos.writeShort(c)
        dos.flush()
        return AttributeInfo(nameIndex, baos.toByteArray())
    }

    fun buildStackMapTable(nameIndex: Int, attr: StackMapTableAttribute): AttributeInfo {
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)
        dos.writeShort(attr.entries.size)
        for (frame in attr.entries) writeStackMapFrame(dos, frame)
        dos.flush()
        return AttributeInfo(nameIndex, baos.toByteArray())
    }

    fun buildMethodParameters(nameIndex: Int, attr: MethodParametersAttribute): AttributeInfo {
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)
        dos.writeByte(attr.parameters.size)
        for (p in attr.parameters) {
            dos.writeShort(p.nameIndex)
            dos.writeShort(p.accessFlags)
        }
        dos.flush()
        return AttributeInfo(nameIndex, baos.toByteArray())
    }

    private fun writeStackMapFrame(dos: DataOutputStream, frame: StackMapFrame) {
        when (frame) {
            is StackMapFrame.Same -> {
                if (frame.offsetDelta < 64) {
                    dos.writeByte(frame.offsetDelta)
                } else {
                    dos.writeByte(251)
                    dos.writeShort(frame.offsetDelta)
                }
            }
            is StackMapFrame.SameLocals1Stack -> {
                if (frame.offsetDelta < 64) {
                    dos.writeByte(64 + frame.offsetDelta)
                } else {
                    dos.writeByte(247)
                    dos.writeShort(frame.offsetDelta)
                }
                writeVerificationType(dos, frame.stack)
            }
            is StackMapFrame.Chop -> {
                dos.writeByte(251 - frame.k)
                dos.writeShort(frame.offsetDelta)
            }
            is StackMapFrame.Append -> {
                dos.writeByte(251 + frame.locals.size)
                dos.writeShort(frame.offsetDelta)
                for (v in frame.locals) writeVerificationType(dos, v)
            }
            is StackMapFrame.Full -> {
                dos.writeByte(255)
                dos.writeShort(frame.offsetDelta)
                dos.writeShort(frame.locals.size)
                for (v in frame.locals) writeVerificationType(dos, v)
                dos.writeShort(frame.stack.size)
                for (v in frame.stack) writeVerificationType(dos, v)
            }
        }
    }

    private fun writeVerificationType(dos: DataOutputStream, vt: VerificationType) {
        when (vt) {
            is VerificationType.Top -> dos.writeByte(0)
            is VerificationType.Integer -> dos.writeByte(1)
            is VerificationType.Float -> dos.writeByte(2)
            is VerificationType.Double -> dos.writeByte(3)
            is VerificationType.Long -> dos.writeByte(4)
            is VerificationType.Null -> dos.writeByte(5)
            is VerificationType.UninitializedThis -> dos.writeByte(6)
            is VerificationType.Object -> { dos.writeByte(7); dos.writeShort(vt.cpoolIndex) }
            is VerificationType.Uninitialized -> { dos.writeByte(8); dos.writeShort(vt.offset) }
        }
    }
}
