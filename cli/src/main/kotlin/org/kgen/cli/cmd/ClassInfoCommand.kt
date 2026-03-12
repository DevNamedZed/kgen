package org.kgen.cli.cmd

import org.kgen.target.jvm.*
import org.kgen.cli.*

object ClassInfoCommand {
    fun run(args: List<String>) {
        val parsed = parseArgs(args)
        val path = parsed.requireFile("kgen classinfo <file.class>")
        val data = readFileOrExit(path)
        val showCode = parsed.has("c", "code")
        val showPrivate = parsed.has("p", "private")
        val showCp = parsed.has("cp")
        val showAttrs = parsed.has("attrs")

        if (detectFormat(data) != BinaryFormat.CLASS) {
            err("not a class file")
            return
        }

        val cf = JvmClassReader.read(data)

        // Header
        val access = AccessFlags.toString(cf.accessFlags, AccessFlags.Context.CLASS)
        println("class ${cf.thisClassName.replace('/', '.')}")
        println("  Version:    ${cf.majorVersion}.${cf.minorVersion} (Java ${cf.javaVersion})")
        println("  Access:     $access (0x${cf.accessFlags.toString(16)})")
        cf.superClassName?.let { println("  Extends:    ${it.replace('/', '.')}") }
        if (cf.interfaceNames.isNotEmpty())
            println("  Implements: ${cf.interfaceNames.joinToString(", ") { it.replace('/', '.') }}")

        // Source file
        for (attr in cf.attributes) {
            if (cf.string(attr.nameIndex) == "SourceFile") {
                val sf = AttributeParser.parseSourceFile(attr)
                println("  Source:     ${cf.string(sf.sourceFileIndex)}")
            }
            if (cf.string(attr.nameIndex) == "Signature") {
                val sig = AttributeParser.parseSignature(attr)
                println("  Signature: ${cf.string(sig.signatureIndex)}")
            }
        }

        // Constant pool
        if (showCp) {
            println()
            println("Constant Pool:")
            for (i in 1 until cf.constantPool.size) {
                val entry = cf.constantPool.getOrNull(i) ?: continue
                println("  #%-5d = %-22s %s".format(i, entry::class.simpleName, formatCpEntry(entry, cf)))
            }
        }

        // Fields
        val fields = if (showPrivate) cf.fields else cf.fields.filter {
            it.accessFlags and AccessFlags.PRIVATE == 0
        }
        if (fields.isNotEmpty()) {
            println()
            println("Fields:")
            for (f in fields) {
                val fAccess = AccessFlags.toString(f.accessFlags, AccessFlags.Context.FIELD)
                val desc = cf.string(f.descriptorIndex)
                val name = cf.string(f.nameIndex)
                val typeStr = descriptorToType(desc)
                print("  $fAccess $typeStr $name")

                // ConstantValue
                for (attr in f.attributes) {
                    if (cf.string(attr.nameIndex) == "ConstantValue") {
                        val cv = AttributeParser.parseConstantValue(attr)
                        val value = formatConstant(cf.constantPool[cv.constantValueIndex])
                        print(" = $value")
                    }
                }
                println(";")

                if (showAttrs) printAttributes(f.attributes, cf, "    ")
            }
        }

        // Methods
        val methods = if (showPrivate) cf.methods else cf.methods.filter {
            it.accessFlags and AccessFlags.PRIVATE == 0
        }
        if (methods.isNotEmpty()) {
            println()
            println("Methods:")
            for (m in methods) {
                val mAccess = AccessFlags.toString(m.accessFlags, AccessFlags.Context.METHOD)
                val desc = cf.string(m.descriptorIndex)
                val name = cf.string(m.nameIndex)
                val (paramTypes, returnType) = parseMethodDescriptor(desc)
                val displayName = if (name == "<init>") {
                    cf.thisClassName.substringAfterLast('/')
                } else if (name == "<clinit>") {
                    "static {}"
                } else name

                if (name == "<clinit>") {
                    println("  $displayName")
                } else {
                    println("  $mAccess $returnType $displayName(${paramTypes.joinToString(", ")});")
                }
                println("    descriptor: $desc")

                // Exceptions
                for (attr in m.attributes) {
                    if (cf.string(attr.nameIndex) == "Exceptions") {
                        val exc = AttributeParser.parseExceptions(attr)
                        val names = exc.exceptionIndexTable.map { cf.constantPool.className(it).replace('/', '.') }
                        println("    throws: ${names.joinToString(", ")}")
                    }
                }

                // Code
                if (showCode) {
                    for (attr in m.attributes) {
                        if (cf.string(attr.nameIndex) == "Code") {
                            val code = AttributeParser.parseCode(attr, cf.constantPool)
                            println("    Code:")
                            println("      max_stack=${code.maxStack}, max_locals=${code.maxLocals}")
                            printBytecode(code.code, cf)

                            // Line numbers
                            for (cAttr in code.attributes) {
                                if (cf.string(cAttr.nameIndex) == "LineNumberTable") {
                                    val lnt = AttributeParser.parseLineNumberTable(cAttr)
                                    println("      LineNumberTable:")
                                    for (e in lnt.entries) {
                                        println("        line ${e.lineNumber}: ${e.startPc}")
                                    }
                                }
                            }

                            if (code.exceptionTable.isNotEmpty()) {
                                println("      Exception table:")
                                println("        %-6s %-6s %-8s %s".format("from", "to", "handler", "type"))
                                for (e in code.exceptionTable) {
                                    val typeName = if (e.catchType == 0) "any"
                                    else cf.constantPool.className(e.catchType).replace('/', '.')
                                    println("        %-6d %-6d %-8d %s".format(
                                        e.startPc, e.endPc, e.handlerPc, typeName))
                                }
                            }
                        }
                    }
                }

                if (showAttrs) printAttributes(m.attributes, cf, "    ")
            }
        }

        // Class attributes
        if (showAttrs && cf.attributes.isNotEmpty()) {
            println()
            println("Class Attributes:")
            printAttributes(cf.attributes, cf, "  ")
        }
    }

    private fun printBytecode(code: ByteArray, cf: ClassFile) {
        var pc = 0
        while (pc < code.size) {
            val opcode = code[pc].toInt() and 0xFF
            val (mnemonic, length) = decodeBytecodeOp(opcode, code, pc, cf)
            val bytes = code.copyOfRange(pc, minOf(pc + length, code.size))
            val bytesStr = bytes.joinToString(" ") { "%02x".format(it) }
            println("      %4d: %-20s %s".format(pc, bytesStr, mnemonic))
            pc += length
        }
    }

    private fun decodeBytecodeOp(opcode: Int, code: ByteArray, pc: Int, cf: ClassFile): Pair<String, Int> {
        // Common JVM bytecodes
        return when (opcode) {
            0x00 -> "nop" to 1
            0x01 -> "aconst_null" to 1
            0x02 -> "iconst_m1" to 1
            in 0x03..0x08 -> "iconst_${opcode - 0x03}" to 1
            0x09, 0x0A -> "lconst_${opcode - 0x09}" to 1
            0x0B, 0x0C, 0x0D -> "fconst_${opcode - 0x0B}" to 1
            0x0E, 0x0F -> "dconst_${opcode - 0x0E}" to 1
            0x10 -> "bipush ${code.getOrSByte(pc + 1)}" to 2
            0x11 -> "sipush ${code.getOrShort(pc + 1)}" to 3
            0x12 -> { val idx = code.getOrUByte(pc + 1); "ldc #$idx" to 2 }
            0x13 -> { val idx = code.getOrUShort(pc + 1); "ldc_w #$idx" to 3 }
            0x14 -> { val idx = code.getOrUShort(pc + 1); "ldc2_w #$idx" to 3 }
            0x15 -> "iload ${code.getOrUByte(pc + 1)}" to 2
            0x16 -> "lload ${code.getOrUByte(pc + 1)}" to 2
            0x17 -> "fload ${code.getOrUByte(pc + 1)}" to 2
            0x18 -> "dload ${code.getOrUByte(pc + 1)}" to 2
            0x19 -> "aload ${code.getOrUByte(pc + 1)}" to 2
            in 0x1A..0x1D -> "iload_${opcode - 0x1A}" to 1
            in 0x1E..0x21 -> "lload_${opcode - 0x1E}" to 1
            in 0x22..0x25 -> "fload_${opcode - 0x22}" to 1
            in 0x26..0x29 -> "dload_${opcode - 0x26}" to 1
            in 0x2A..0x2D -> "aload_${opcode - 0x2A}" to 1
            0x2E -> "iaload" to 1; 0x2F -> "laload" to 1
            0x30 -> "faload" to 1; 0x31 -> "daload" to 1
            0x32 -> "aaload" to 1; 0x33 -> "baload" to 1
            0x34 -> "caload" to 1; 0x35 -> "saload" to 1
            0x36 -> "istore ${code.getOrUByte(pc + 1)}" to 2
            0x37 -> "lstore ${code.getOrUByte(pc + 1)}" to 2
            0x38 -> "fstore ${code.getOrUByte(pc + 1)}" to 2
            0x39 -> "dstore ${code.getOrUByte(pc + 1)}" to 2
            0x3A -> "astore ${code.getOrUByte(pc + 1)}" to 2
            in 0x3B..0x3E -> "istore_${opcode - 0x3B}" to 1
            in 0x3F..0x42 -> "lstore_${opcode - 0x3F}" to 1
            in 0x43..0x46 -> "fstore_${opcode - 0x43}" to 1
            in 0x47..0x4A -> "dstore_${opcode - 0x47}" to 1
            in 0x4B..0x4E -> "astore_${opcode - 0x4B}" to 1
            0x4F -> "iastore" to 1; 0x50 -> "lastore" to 1
            0x51 -> "fastore" to 1; 0x52 -> "dastore" to 1
            0x53 -> "aastore" to 1; 0x54 -> "bastore" to 1
            0x55 -> "castore" to 1; 0x56 -> "sastore" to 1
            0x57 -> "pop" to 1; 0x58 -> "pop2" to 1
            0x59 -> "dup" to 1; 0x5A -> "dup_x1" to 1
            0x5B -> "dup_x2" to 1; 0x5C -> "dup2" to 1
            0x5D -> "dup2_x1" to 1; 0x5E -> "dup2_x2" to 1
            0x5F -> "swap" to 1
            0x60 -> "iadd" to 1; 0x61 -> "ladd" to 1
            0x62 -> "fadd" to 1; 0x63 -> "dadd" to 1
            0x64 -> "isub" to 1; 0x65 -> "lsub" to 1
            0x66 -> "fsub" to 1; 0x67 -> "dsub" to 1
            0x68 -> "imul" to 1; 0x69 -> "lmul" to 1
            0x6A -> "fmul" to 1; 0x6B -> "dmul" to 1
            0x6C -> "idiv" to 1; 0x6D -> "ldiv" to 1
            0x6E -> "fdiv" to 1; 0x6F -> "ddiv" to 1
            0x70 -> "irem" to 1; 0x71 -> "lrem" to 1
            0x72 -> "frem" to 1; 0x73 -> "drem" to 1
            0x74 -> "ineg" to 1; 0x75 -> "lneg" to 1
            0x76 -> "fneg" to 1; 0x77 -> "dneg" to 1
            0x78 -> "ishl" to 1; 0x79 -> "lshl" to 1
            0x7A -> "ishr" to 1; 0x7B -> "lshr" to 1
            0x7C -> "iushr" to 1; 0x7D -> "lushr" to 1
            0x7E -> "iand" to 1; 0x7F -> "land" to 1
            0x80 -> "ior" to 1; 0x81 -> "lor" to 1
            0x82 -> "ixor" to 1; 0x83 -> "lxor" to 1
            0x84 -> "iinc ${code.getOrUByte(pc + 1)} ${code.getOrSByte(pc + 2)}" to 3
            0x85 -> "i2l" to 1; 0x86 -> "i2f" to 1; 0x87 -> "i2d" to 1
            0x88 -> "l2i" to 1; 0x89 -> "l2f" to 1; 0x8A -> "l2d" to 1
            0x8B -> "f2i" to 1; 0x8C -> "f2l" to 1; 0x8D -> "f2d" to 1
            0x8E -> "d2i" to 1; 0x8F -> "d2l" to 1; 0x90 -> "d2f" to 1
            0x91 -> "i2b" to 1; 0x92 -> "i2c" to 1; 0x93 -> "i2s" to 1
            0x94 -> "lcmp" to 1
            0x95 -> "fcmpl" to 1; 0x96 -> "fcmpg" to 1
            0x97 -> "dcmpl" to 1; 0x98 -> "dcmpg" to 1
            in 0x99..0x9E -> {
                val names = listOf("ifeq", "ifne", "iflt", "ifge", "ifgt", "ifle")
                val off = code.getOrShort(pc + 1)
                "${names[opcode - 0x99]} ${pc + off}" to 3
            }
            in 0x9F..0xA4 -> {
                val names = listOf("if_icmpeq", "if_icmpne", "if_icmplt", "if_icmpge", "if_icmpgt", "if_icmple")
                val off = code.getOrShort(pc + 1)
                "${names[opcode - 0x9F]} ${pc + off}" to 3
            }
            0xA5 -> { val off = code.getOrShort(pc + 1); "if_acmpeq ${pc + off}" to 3 }
            0xA6 -> { val off = code.getOrShort(pc + 1); "if_acmpne ${pc + off}" to 3 }
            0xA7 -> { val off = code.getOrShort(pc + 1); "goto ${pc + off}" to 3 }
            0xA8 -> { val off = code.getOrShort(pc + 1); "jsr ${pc + off}" to 3 }
            0xA9 -> "ret ${code.getOrUByte(pc + 1)}" to 2
            0xAA -> { // tableswitch
                val pad = (4 - ((pc + 1) % 4)) % 4
                "tableswitch" to (1 + pad + 12) // simplified
            }
            0xAB -> { // lookupswitch
                val pad = (4 - ((pc + 1) % 4)) % 4
                "lookupswitch" to (1 + pad + 8) // simplified
            }
            0xAC -> "ireturn" to 1; 0xAD -> "lreturn" to 1
            0xAE -> "freturn" to 1; 0xAF -> "dreturn" to 1
            0xB0 -> "areturn" to 1; 0xB1 -> "return" to 1
            0xB2 -> { val idx = code.getOrUShort(pc + 1); "getstatic #$idx" to 3 }
            0xB3 -> { val idx = code.getOrUShort(pc + 1); "putstatic #$idx" to 3 }
            0xB4 -> { val idx = code.getOrUShort(pc + 1); "getfield #$idx" to 3 }
            0xB5 -> { val idx = code.getOrUShort(pc + 1); "putfield #$idx" to 3 }
            0xB6 -> { val idx = code.getOrUShort(pc + 1); "invokevirtual #$idx" to 3 }
            0xB7 -> { val idx = code.getOrUShort(pc + 1); "invokespecial #$idx" to 3 }
            0xB8 -> { val idx = code.getOrUShort(pc + 1); "invokestatic #$idx" to 3 }
            0xB9 -> { val idx = code.getOrUShort(pc + 1); "invokeinterface #$idx" to 5 }
            0xBA -> { val idx = code.getOrUShort(pc + 1); "invokedynamic #$idx" to 5 }
            0xBB -> { val idx = code.getOrUShort(pc + 1); "new #$idx" to 3 }
            0xBC -> "newarray ${code.getOrUByte(pc + 1)}" to 2
            0xBD -> { val idx = code.getOrUShort(pc + 1); "anewarray #$idx" to 3 }
            0xBE -> "arraylength" to 1
            0xBF -> "athrow" to 1
            0xC0 -> { val idx = code.getOrUShort(pc + 1); "checkcast #$idx" to 3 }
            0xC1 -> { val idx = code.getOrUShort(pc + 1); "instanceof #$idx" to 3 }
            0xC2 -> "monitorenter" to 1; 0xC3 -> "monitorexit" to 1
            0xC4 -> "wide" to 1 // simplified
            0xC5 -> { val idx = code.getOrUShort(pc + 1); "multianewarray #$idx ${code.getOrUByte(pc + 3)}" to 4 }
            0xC6 -> { val off = code.getOrShort(pc + 1); "ifnull ${pc + off}" to 3 }
            0xC7 -> { val off = code.getOrShort(pc + 1); "ifnonnull ${pc + off}" to 3 }
            0xC8 -> { val off = code.getOrInt(pc + 1); "goto_w ${pc + off}" to 5 }
            0xC9 -> { val off = code.getOrInt(pc + 1); "jsr_w ${pc + off}" to 5 }
            else -> "unknown_0x${opcode.toString(16)}" to 1
        }
    }

    private fun formatCpEntry(entry: CpEntry, cf: ClassFile): String = when (entry) {
        is CpUtf8 -> "\"${entry.value.take(80)}\""
        is CpInteger -> "${entry.value}"
        is CpFloat -> "${entry.value}f"
        is CpLong -> "${entry.value}L"
        is CpDouble -> "${entry.value}d"
        is CpClass -> cf.constantPool.utf8(entry.nameIndex).replace('/', '.')
        is CpString -> "\"${cf.constantPool.utf8(entry.stringIndex).take(60)}\""
        is CpFieldRef -> {
            val (n, d) = cf.constantPool.nameAndType(entry.nameAndTypeIndex)
            "${cf.constantPool.className(entry.classIndex)}.$n:$d"
        }
        is CpMethodRef -> {
            val (n, d) = cf.constantPool.nameAndType(entry.nameAndTypeIndex)
            "${cf.constantPool.className(entry.classIndex)}.$n:$d"
        }
        is CpInterfaceMethodRef -> {
            val (n, d) = cf.constantPool.nameAndType(entry.nameAndTypeIndex)
            "${cf.constantPool.className(entry.classIndex)}.$n:$d"
        }
        is CpNameAndType -> "${cf.constantPool.utf8(entry.nameIndex)}:${cf.constantPool.utf8(entry.descriptorIndex)}"
        is CpMethodHandle -> "kind=${entry.referenceKind} #${entry.referenceIndex}"
        is CpMethodType -> cf.constantPool.utf8(entry.descriptorIndex)
        is CpInvokeDynamic -> "#${entry.bootstrapMethodAttrIndex}:#${entry.nameAndTypeIndex}"
        is CpDynamic -> "#${entry.bootstrapMethodAttrIndex}:#${entry.nameAndTypeIndex}"
        is CpModule -> cf.constantPool.utf8(entry.nameIndex)
        is CpPackage -> cf.constantPool.utf8(entry.nameIndex)
    }

    private fun formatConstant(entry: CpEntry): String = when (entry) {
        is CpInteger -> "${entry.value}"
        is CpFloat -> "${entry.value}f"
        is CpLong -> "${entry.value}L"
        is CpDouble -> "${entry.value}d"
        is CpString -> "\"...\""
        else -> "#?"
    }

    private fun descriptorToType(desc: String): String = parseType(desc, 0).first

    private fun parseType(desc: String, pos: Int): Pair<String, Int> = when (desc[pos]) {
        'V' -> "void" to pos + 1
        'Z' -> "boolean" to pos + 1
        'B' -> "byte" to pos + 1
        'C' -> "char" to pos + 1
        'S' -> "short" to pos + 1
        'I' -> "int" to pos + 1
        'J' -> "long" to pos + 1
        'F' -> "float" to pos + 1
        'D' -> "double" to pos + 1
        'L' -> {
            val end = desc.indexOf(';', pos)
            desc.substring(pos + 1, end).replace('/', '.') to end + 1
        }
        '[' -> {
            val (inner, next) = parseType(desc, pos + 1)
            "$inner[]" to next
        }
        else -> desc.substring(pos) to desc.length
    }

    private fun parseMethodDescriptor(desc: String): Pair<List<String>, String> {
        val params = mutableListOf<String>()
        var i = 1 // skip '('
        while (i < desc.length && desc[i] != ')') {
            val (type, next) = parseType(desc, i)
            params.add(type)
            i = next
        }
        val (ret, _) = parseType(desc, i + 1) // skip ')'
        return params to ret
    }

    private fun printAttributes(attrs: List<AttributeInfo>, cf: ClassFile, indent: String) {
        for (attr in attrs) {
            val name = cf.string(attr.nameIndex)
            println("$indent[Attribute] $name (${attr.data.size} bytes)")
        }
    }

    // Safe byte array access helpers
    private fun ByteArray.getOrUByte(i: Int): Int = if (i < size) this[i].toInt() and 0xFF else 0
    private fun ByteArray.getOrSByte(i: Int): Int = if (i < size) this[i].toInt() else 0
    private fun ByteArray.getOrUShort(i: Int): Int =
        if (i + 1 < size) ((this[i].toInt() and 0xFF) shl 8) or (this[i + 1].toInt() and 0xFF) else 0
    private fun ByteArray.getOrShort(i: Int): Int {
        val v = getOrUShort(i)
        return if (v >= 0x8000) v - 0x10000 else v
    }
    private fun ByteArray.getOrInt(i: Int): Int =
        if (i + 3 < size) ((this[i].toInt() and 0xFF) shl 24) or ((this[i+1].toInt() and 0xFF) shl 16) or
                ((this[i+2].toInt() and 0xFF) shl 8) or (this[i+3].toInt() and 0xFF) else 0
}
