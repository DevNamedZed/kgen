package org.kgen.binary.jvm

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

/**
 * Serializes a [ClassFile] model to .class file bytes.
 *
 * This is a format-level writer — it takes a fully built ClassFile and emits
 * the binary representation. It does NOT perform validation or compute stack
 * maps; those are the responsibility of the code generator or builder.
 *
 * ```kotlin
 * val bytes = JvmClassWriter.write(classFile)
 * Files.write(Path.of("Hello.class"), bytes)
 * ```
 */
object JvmClassWriter {

    fun write(cf: ClassFile): ByteArray {
        val baos = ByteArrayOutputStream(4096)
        val dos = DataOutputStream(baos)

        dos.writeInt(JvmClassReader.MAGIC.toInt())
        dos.writeShort(cf.minorVersion)
        dos.writeShort(cf.majorVersion)

        writeConstantPool(dos, cf.constantPool)

        dos.writeShort(cf.accessFlags)
        dos.writeShort(cf.thisClass)
        dos.writeShort(cf.superClass)

        dos.writeShort(cf.interfaces.size)
        for (iface in cf.interfaces) dos.writeShort(iface)

        writeFields(dos, cf.fields)
        writeMethods(dos, cf.methods)
        writeAttributes(dos, cf.attributes)

        dos.flush()
        return baos.toByteArray()
    }

    private fun writeConstantPool(dos: DataOutputStream, pool: ConstantPool) {
        dos.writeShort(pool.size)
        var i = 1
        while (i < pool.size) {
            val entry = pool.getOrNull(i)
            if (entry == null) { i++; continue }
            dos.writeByte(entry.tag)
            when (entry) {
                is CpUtf8 -> {
                    val bytes = encodeModifiedUtf8(entry.value)
                    dos.writeShort(bytes.size)
                    dos.write(bytes)
                }
                is CpInteger -> dos.writeInt(entry.value)
                is CpFloat -> dos.writeInt(entry.value.toBits())
                is CpLong -> { dos.writeLong(entry.value); i++ }
                is CpDouble -> { dos.writeLong(entry.value.toBits()); i++ }
                is CpClass -> dos.writeShort(entry.nameIndex)
                is CpString -> dos.writeShort(entry.stringIndex)
                is CpFieldRef -> { dos.writeShort(entry.classIndex); dos.writeShort(entry.nameAndTypeIndex) }
                is CpMethodRef -> { dos.writeShort(entry.classIndex); dos.writeShort(entry.nameAndTypeIndex) }
                is CpInterfaceMethodRef -> { dos.writeShort(entry.classIndex); dos.writeShort(entry.nameAndTypeIndex) }
                is CpNameAndType -> { dos.writeShort(entry.nameIndex); dos.writeShort(entry.descriptorIndex) }
                is CpMethodHandle -> { dos.writeByte(entry.referenceKind); dos.writeShort(entry.referenceIndex) }
                is CpMethodType -> dos.writeShort(entry.descriptorIndex)
                is CpDynamic -> { dos.writeShort(entry.bootstrapMethodAttrIndex); dos.writeShort(entry.nameAndTypeIndex) }
                is CpInvokeDynamic -> { dos.writeShort(entry.bootstrapMethodAttrIndex); dos.writeShort(entry.nameAndTypeIndex) }
                is CpModule -> dos.writeShort(entry.nameIndex)
                is CpPackage -> dos.writeShort(entry.nameIndex)
            }
            i++
        }
    }

    private fun writeFields(dos: DataOutputStream, fields: List<FieldInfo>) {
        dos.writeShort(fields.size)
        for (f in fields) {
            dos.writeShort(f.accessFlags)
            dos.writeShort(f.nameIndex)
            dos.writeShort(f.descriptorIndex)
            writeAttributes(dos, f.attributes)
        }
    }

    private fun writeMethods(dos: DataOutputStream, methods: List<MethodInfo>) {
        dos.writeShort(methods.size)
        for (m in methods) {
            dos.writeShort(m.accessFlags)
            dos.writeShort(m.nameIndex)
            dos.writeShort(m.descriptorIndex)
            writeAttributes(dos, m.attributes)
        }
    }

    private fun writeAttributes(dos: DataOutputStream, attrs: List<AttributeInfo>) {
        dos.writeShort(attrs.size)
        for (a in attrs) {
            dos.writeShort(a.nameIndex)
            dos.writeInt(a.data.size)
            dos.write(a.data)
        }
    }

    private fun encodeModifiedUtf8(s: String): ByteArray {
        val baos = ByteArrayOutputStream(s.length)
        for (c in s) {
            val code = c.code
            when {
                code == 0 -> { baos.write(0xC0); baos.write(0x80) }
                code < 0x80 -> baos.write(code)
                code < 0x800 -> {
                    baos.write(0xC0 or (code shr 6))
                    baos.write(0x80 or (code and 0x3F))
                }
                else -> {
                    baos.write(0xE0 or (code shr 12))
                    baos.write(0x80 or ((code shr 6) and 0x3F))
                    baos.write(0x80 or (code and 0x3F))
                }
            }
        }
        return baos.toByteArray()
    }
}
