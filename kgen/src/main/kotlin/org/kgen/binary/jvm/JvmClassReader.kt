package org.kgen.binary.jvm

import java.io.DataInputStream
import java.io.ByteArrayInputStream

/**
 * Parses JVM .class files into a structured [ClassFile] model.
 *
 * ```kotlin
 * val cf = JvmClassReader.read(bytes)
 * println(cf.thisClassName)           // "com/example/Hello"
 * println(cf.javaVersion)             // "21"
 * println(cf.methods.size)            // 3
 *
 * for (m in cf.methods) {
 *     val name = cf.string(m.nameIndex)
 *     val desc = cf.string(m.descriptorIndex)
 *     println("$name$desc")
 * }
 * ```
 */
object JvmClassReader {

    fun read(bytes: ByteArray): ClassFile {
        val dis = DataInputStream(ByteArrayInputStream(bytes))
        val magic = dis.readInt()
        if (magic != MAGIC.toInt()) {
            throw IllegalArgumentException("Not a class file: magic 0x${magic.toString(16)}")
        }

        val minorVersion = dis.readUnsignedShort()
        val majorVersion = dis.readUnsignedShort()
        val constantPool = readConstantPool(dis)
        val accessFlags = dis.readUnsignedShort()
        val thisClass = dis.readUnsignedShort()
        val superClass = dis.readUnsignedShort()

        val interfaceCount = dis.readUnsignedShort()
        val interfaces = (0 until interfaceCount).map { dis.readUnsignedShort() }

        val fields = readMembers(dis) { flags, name, desc, attrs ->
            FieldInfo(flags, name, desc, attrs)
        }
        val methods = readMembers(dis) { flags, name, desc, attrs ->
            MethodInfo(flags, name, desc, attrs)
        }
        val attributes = readAttributes(dis)

        return ClassFile(
            minorVersion = minorVersion,
            majorVersion = majorVersion,
            constantPool = constantPool,
            accessFlags = accessFlags,
            thisClass = thisClass,
            superClass = superClass,
            interfaces = interfaces,
            fields = fields,
            methods = methods,
            attributes = attributes,
        )
    }

    private fun readConstantPool(dis: DataInputStream): ConstantPool {
        val count = dis.readUnsignedShort()
        val entries = ArrayList<CpEntry?>(count)
        entries.add(null) // index 0
        var i = 1
        while (i < count) {
            val tag = dis.readUnsignedByte()
            val entry = readCpEntry(dis, tag)
            entries.add(entry)
            i++
            if (entry is CpLong || entry is CpDouble) {
                entries.add(null) // takes 2 slots
                i++
            }
        }
        return ConstantPool(entries)
    }

    private fun readCpEntry(dis: DataInputStream, tag: Int): CpEntry = when (tag) {
        CpUtf8.TAG -> {
            val len = dis.readUnsignedShort()
            val bytes = ByteArray(len)
            dis.readFully(bytes)
            CpUtf8(decodeModifiedUtf8(bytes))
        }
        CpInteger.TAG -> CpInteger(dis.readInt())
        CpFloat.TAG -> CpFloat(Float.fromBits(dis.readInt()))
        CpLong.TAG -> CpLong(dis.readLong())
        CpDouble.TAG -> CpDouble(Double.fromBits(dis.readLong()))
        CpClass.TAG -> CpClass(dis.readUnsignedShort())
        CpString.TAG -> CpString(dis.readUnsignedShort())
        CpFieldRef.TAG -> CpFieldRef(dis.readUnsignedShort(), dis.readUnsignedShort())
        CpMethodRef.TAG -> CpMethodRef(dis.readUnsignedShort(), dis.readUnsignedShort())
        CpInterfaceMethodRef.TAG -> CpInterfaceMethodRef(dis.readUnsignedShort(), dis.readUnsignedShort())
        CpNameAndType.TAG -> CpNameAndType(dis.readUnsignedShort(), dis.readUnsignedShort())
        CpMethodHandle.TAG -> CpMethodHandle(dis.readUnsignedByte(), dis.readUnsignedShort())
        CpMethodType.TAG -> CpMethodType(dis.readUnsignedShort())
        CpDynamic.TAG -> CpDynamic(dis.readUnsignedShort(), dis.readUnsignedShort())
        CpInvokeDynamic.TAG -> CpInvokeDynamic(dis.readUnsignedShort(), dis.readUnsignedShort())
        CpModule.TAG -> CpModule(dis.readUnsignedShort())
        CpPackage.TAG -> CpPackage(dis.readUnsignedShort())
        else -> throw IllegalArgumentException("Unknown constant pool tag: $tag")
    }

    private fun <T> readMembers(
        dis: DataInputStream,
        factory: (Int, Int, Int, List<AttributeInfo>) -> T,
    ): List<T> {
        val count = dis.readUnsignedShort()
        return (0 until count).map {
            val flags = dis.readUnsignedShort()
            val name = dis.readUnsignedShort()
            val desc = dis.readUnsignedShort()
            val attrs = readAttributes(dis)
            factory(flags, name, desc, attrs)
        }
    }

    private fun readAttributes(dis: DataInputStream): List<AttributeInfo> {
        val count = dis.readUnsignedShort()
        return (0 until count).map {
            val nameIndex = dis.readUnsignedShort()
            val length = dis.readInt()
            val data = ByteArray(length)
            dis.readFully(data)
            AttributeInfo(nameIndex, data)
        }
    }

    private fun decodeModifiedUtf8(bytes: ByteArray): String {
        val chars = StringBuilder(bytes.size)
        var i = 0
        while (i < bytes.size) {
            val b = bytes[i].toInt() and 0xFF
            when {
                b == 0 -> { chars.append('\u0000'); i++ }
                b < 0x80 -> { chars.append(b.toChar()); i++ }
                b and 0xE0 == 0xC0 -> {
                    val b2 = bytes[i + 1].toInt() and 0x3F
                    chars.append(((b and 0x1F) shl 6 or b2).toChar())
                    i += 2
                }
                b and 0xF0 == 0xE0 -> {
                    val b2 = bytes[i + 1].toInt() and 0x3F
                    val b3 = bytes[i + 2].toInt() and 0x3F
                    chars.append(((b and 0x0F) shl 12 or (b2 shl 6) or b3).toChar())
                    i += 3
                }
                else -> { chars.append(b.toChar()); i++ }
            }
        }
        return chars.toString()
    }

    const val MAGIC = 0xCAFEBABEu
}
