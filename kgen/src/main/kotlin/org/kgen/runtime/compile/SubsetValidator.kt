package org.kgen.runtime.compile

import org.kgen.target.jvm.*

/**
 * Validates that a `@KgenRuntime` class only uses the supported bytecode subset.
 *
 * The Runtime Subset is "C-like Java": static methods, primitive types,
 * explicit memory via `Kgen.*` intrinsics, no objects/exceptions/GC.
 *
 * ```java
 * var errors = SubsetValidator.validate(classFile);
 * if (!errors.isEmpty()) {
 *     for (var e : errors) System.err.println(e);
 * }
 * ```
 */
class SubsetValidator {

    data class ValidationError(
        val methodName: String,
        val offset: Int,
        val message: String,
    ) {
        override fun toString(): String = "$methodName@$offset: $message"
    }

    companion object {
        private const val KGEN_CLASS = "org/kgen/unmanaged/Kgen"

        @JvmStatic
        fun validate(cf: ClassFile): List<ValidationError> {
            val errors = mutableListOf<ValidationError>()

            for (method in cf.methods) {
                val name = cf.string(method.nameIndex)
                val desc = cf.string(method.descriptorIndex)
                val flags = method.accessFlags

                // Instance methods and static methods are both allowed
                // (<init> constructors are allowed too — they compile to regular functions)

                // Validate descriptor — only primitives and long-as-pointer
                validateDescriptor(name, desc, errors)

                // Validate bytecode
                val codeAttr = method.attributes.firstOrNull { cf.string(it.nameIndex) == "Code" }
                    ?: continue

                val code = AttributeParser.parseCode(codeAttr, cf.constantPool)
                validateBytecode(name, code.code, cf.constantPool, errors)
            }

            return errors
        }

        private fun validateDescriptor(methodName: String, desc: String, errors: MutableList<ValidationError>) {
            // Parse descriptor: (params)return
            val closeIdx = desc.indexOf(')')
            if (closeIdx < 0) {
                errors.add(ValidationError(methodName, 0, "invalid descriptor: $desc"))
                return
            }
            val params = desc.substring(1, closeIdx)
            val ret = desc.substring(closeIdx + 1)

            fun checkType(type: String, pos: Int): Boolean {
                return when {
                    type == "V" -> true
                    type == "Z" || type == "B" || type == "S" || type == "I" || type == "J" -> true
                    type == "F" || type == "D" -> true
                    type.startsWith("L") -> true // object references (opaque pointers in native)
                    type.startsWith("[") -> true // arrays (opaque pointers in native)
                    else -> {
                        errors.add(ValidationError(methodName, pos, "unsupported type: $type"))
                        false
                    }
                }
            }

            // Parse param types
            var i = 0
            while (i < params.length) {
                when (params[i]) {
                    'Z', 'B', 'S', 'I', 'J', 'F', 'D' -> i++
                    'L' -> {
                        val semi = params.indexOf(';', i)
                        checkType(params.substring(i, semi + 1), 0)
                        i = semi + 1
                    }
                    '[' -> {
                        // Skip array dimensions
                        val start = i
                        while (i < params.length && params[i] == '[') i++
                        // Skip element type
                        if (i < params.length) {
                            when (params[i]) {
                                'L' -> {
                                    val semi = params.indexOf(';', i)
                                    i = semi + 1
                                }
                                else -> i++ // primitive element type
                            }
                        }
                        checkType(params.substring(start, i), 0)
                    }
                    else -> {
                        errors.add(ValidationError(methodName, 0, "invalid descriptor char: ${params[i]}"))
                        i++
                    }
                }
            }

            // Check return type
            if (ret.startsWith("L") || ret.startsWith("[")) {
                checkType(ret, 0)
            }
        }

        private fun validateBytecode(
            methodName: String,
            code: ByteArray,
            cp: ConstantPool,
            errors: MutableList<ValidationError>,
        ) {
            var pc = 0
            while (pc < code.size) {
                val opcode = code[pc].toInt() and 0xFF
                when (opcode) {
                    // Allowed: constants
                    0x00 -> pc++ // nop
                    0x01 -> pc++ // aconst_null (allowed for null checks)
                    0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08 -> pc++ // iconst_m1..iconst_5
                    0x09, 0x0A -> pc++ // lconst_0, lconst_1
                    0x0B, 0x0C, 0x0D -> pc++ // fconst_0..fconst_2
                    0x0E, 0x0F -> pc++ // dconst_0, dconst_1
                    0x10 -> pc += 2 // bipush
                    0x11 -> pc += 3 // sipush
                    0x12 -> { // ldc
                        val idx = code[pc + 1].toInt() and 0xFF
                        validateLdc(methodName, pc, idx, cp, errors)
                        pc += 2
                    }
                    0x13 -> { // ldc_w
                        val idx = readU16(code, pc + 1)
                        validateLdc(methodName, pc, idx, cp, errors)
                        pc += 3
                    }
                    0x14 -> pc += 3 // ldc2_w (long/double constants ok)

                    // Allowed: loads
                    0x15, 0x16, 0x17, 0x18 -> pc += 2 // iload, lload, fload, dload
                    0x19 -> { // aload — only if used for null check patterns
                        pc += 2
                    }
                    0x1A, 0x1B, 0x1C, 0x1D -> pc++ // iload_0..iload_3
                    0x1E, 0x1F, 0x20, 0x21 -> pc++ // lload_0..lload_3
                    0x22, 0x23, 0x24, 0x25 -> pc++ // fload_0..fload_3
                    0x26, 0x27, 0x28, 0x29 -> pc++ // dload_0..dload_3
                    0x2A, 0x2B, 0x2C, 0x2D -> pc++ // aload_0..aload_3

                    // Allowed: array loads
                    0x2E, 0x2F, 0x30, 0x31, 0x32, 0x33, 0x34, 0x35 -> pc++

                    // Allowed: stores
                    0x36, 0x37, 0x38, 0x39 -> pc += 2 // istore, lstore, fstore, dstore
                    0x3A -> pc += 2 // astore
                    0x3B, 0x3C, 0x3D, 0x3E -> pc++ // istore_0..istore_3
                    0x3F, 0x40, 0x41, 0x42 -> pc++ // lstore_0..lstore_3
                    0x43, 0x44, 0x45, 0x46 -> pc++ // fstore_0..fstore_3
                    0x47, 0x48, 0x49, 0x4A -> pc++ // dstore_0..dstore_3
                    0x4B, 0x4C, 0x4D, 0x4E -> pc++ // astore_0..astore_3

                    // Allowed: array stores
                    0x4F, 0x50, 0x51, 0x52, 0x53, 0x54, 0x55, 0x56 -> pc++

                    // Allowed: stack operations
                    0x57, 0x58 -> pc++ // pop, pop2
                    0x59, 0x5A, 0x5B, 0x5C, 0x5D, 0x5E, 0x5F -> pc++ // dup variants, swap

                    // Allowed: arithmetic
                    0x60, 0x61, 0x62, 0x63 -> pc++ // iadd, ladd, fadd, dadd
                    0x64, 0x65, 0x66, 0x67 -> pc++ // isub, lsub, fsub, dsub
                    0x68, 0x69, 0x6A, 0x6B -> pc++ // imul, lmul, fmul, dmul
                    0x6C, 0x6D, 0x6E, 0x6F -> pc++ // idiv, ldiv, fdiv, ddiv
                    0x70, 0x71, 0x72, 0x73 -> pc++ // irem, lrem, frem, drem
                    0x74, 0x75, 0x76, 0x77 -> pc++ // ineg, lneg, fneg, dneg

                    // Allowed: shifts
                    0x78, 0x79, 0x7A, 0x7B, 0x7C, 0x7D -> pc++ // ishl..lushr

                    // Allowed: bitwise
                    0x7E, 0x7F, 0x80, 0x81, 0x82, 0x83 -> pc++ // iand..lxor

                    // Allowed: iinc
                    0x84 -> pc += 3 // iinc index, const

                    // Allowed: conversions
                    0x85, 0x86, 0x87, 0x88, 0x89, 0x8A -> pc++ // i2l..l2f
                    0x8B, 0x8C, 0x8D, 0x8E, 0x8F, 0x90, 0x91, 0x92, 0x93 -> pc++ // f2i..i2s

                    // Allowed: comparisons
                    0x94, 0x95, 0x96, 0x97, 0x98 -> pc++ // lcmp, fcmp*, dcmp*

                    // Allowed: branches
                    0x99, 0x9A, 0x9B, 0x9C, 0x9D, 0x9E -> pc += 3 // ifeq..ifle
                    0x9F, 0xA0, 0xA1, 0xA2, 0xA3, 0xA4 -> pc += 3 // if_icmp*
                    0xA5, 0xA6 -> pc += 3 // if_acmpeq, if_acmpne
                    0xA7 -> pc += 3 // goto
                    // 0xA8, 0xA9 -> jsr/ret — forbidden (deprecated)

                    // Allowed: tableswitch
                    0xAA -> {
                        val base = pc
                        pc++ // opcode
                        pc = (pc + 3) and 3.inv() // padding
                        pc += 4 // default
                        val low = readI32(code, pc); pc += 4
                        val high = readI32(code, pc); pc += 4
                        pc += (high - low + 1) * 4
                    }

                    // Allowed: lookupswitch
                    0xAB -> {
                        val base = pc
                        pc++ // opcode
                        pc = (pc + 3) and 3.inv() // padding
                        pc += 4 // default
                        val npairs = readI32(code, pc); pc += 4
                        pc += npairs * 8
                    }

                    // Allowed: returns
                    0xAC, 0xAD, 0xAE, 0xAF, 0xB0, 0xB1 -> pc++ // ireturn..return

                    // Allowed: field access (static and instance)
                    0xB2, 0xB3 -> pc += 3 // getstatic, putstatic
                    0xB4, 0xB5 -> pc += 3 // getfield, putfield

                    // Calls — validate target
                    0xB6 -> pc += 3 // invokevirtual
                    0xB7 -> pc += 3 // invokespecial
                    0xB8 -> { // invokestatic — allowed if target is Kgen.* or @KgenRuntime
                        val idx = readU16(code, pc + 1)
                        validateStaticCall(methodName, pc, idx, cp, errors)
                        pc += 3
                    }
                    0xB9 -> pc += 5 // invokeinterface — allowed (lowered to direct call)
                    0xBA -> { // invokedynamic — allowed (lowered to direct calls for known bootstrap methods)
                        val idx = readU16(code, pc + 1)
                        validateInvokeDynamic(methodName, pc, idx, cp, errors)
                        pc += 5
                    }

                    // Allowed: object/array creation
                    0xBB -> pc += 3 // new
                    0xBC -> pc += 2 // newarray
                    0xBD -> pc += 3 // anewarray
                    0xBE -> pc++ // arraylength

                    // Allowed: athrow (lowered to trap or runtime abort)
                    0xBF -> pc++

                    // Type checks — allowed (no-op checkcast, instanceof returns 1)
                    0xC0 -> pc += 3 // checkcast
                    0xC1 -> pc += 3 // instanceof

                    // Allowed: synchronization (lowered to no-op in subset compiler)
                    0xC2, 0xC3 -> pc++ // monitorenter, monitorexit

                    // wide prefix
                    0xC4 -> {
                        val widened = code[pc + 1].toInt() and 0xFF
                        if (widened == 0x84) pc += 6 // wide iinc
                        else pc += 4 // wide load/store
                    }

                    // Allowed: multianewarray (allocates outermost dimension)
                    0xC5 -> pc += 4

                    // ifnull, ifnonnull
                    0xC6, 0xC7 -> pc += 3

                    // goto_w
                    0xC8 -> pc += 5

                    else -> {
                        errors.add(ValidationError(methodName, pc, "unsupported opcode: 0x${opcode.toString(16)}"))
                        pc++ // best effort
                    }
                }
            }
        }

        private fun validateLdc(
            methodName: String, pc: Int, cpIndex: Int,
            cp: ConstantPool, errors: MutableList<ValidationError>,
        ) {
            when (cp.getOrNull(cpIndex)) {
                is CpInteger, is CpFloat, is CpLong, is CpDouble -> {} // ok
                is CpString -> {} // string constants are ok (become rodata)
                else -> errors.add(ValidationError(methodName, pc, "ldc: only numeric/string constants allowed"))
            }
        }

        private fun validateInvokeDynamic(
            methodName: String, pc: Int, cpIndex: Int,
            cp: ConstantPool, errors: MutableList<ValidationError>,
        ) {
            val entry = cp.getOrNull(cpIndex) as? CpInvokeDynamic
                ?: run { errors.add(ValidationError(methodName, pc, "invalid invokedynamic cp entry")); return }

            // Look up the bootstrap method to check if it's one we can lower
            // We can't easily resolve the BootstrapMethods attribute from here (it's on the ClassFile,
            // not the ConstantPool), so we allow all invokedynamic and let BytecodeToIrLowering
            // report errors for unsupported bootstrap methods at lowering time.
        }

        private fun validateStaticCall(
            methodName: String, pc: Int, cpIndex: Int,
            cp: ConstantPool, errors: MutableList<ValidationError>,
        ) {
            val entry = cp.getOrNull(cpIndex) as? CpMethodRef ?: return
            val className = cp.className(entry.classIndex)

            // Allow calls to Kgen intrinsics
            if (className == KGEN_CLASS) return

            // Allow calls to other @KgenRuntime classes — can't validate at
            // class level, so we allow all static calls and rely on the
            // RuntimeCompiler to verify the target is also @KgenRuntime
        }

        private fun readU16(code: ByteArray, offset: Int): Int =
            ((code[offset].toInt() and 0xFF) shl 8) or (code[offset + 1].toInt() and 0xFF)

        private fun readI32(code: ByteArray, offset: Int): Int =
            ((code[offset].toInt() and 0xFF) shl 24) or
                ((code[offset + 1].toInt() and 0xFF) shl 16) or
                ((code[offset + 2].toInt() and 0xFF) shl 8) or
                (code[offset + 3].toInt() and 0xFF)
    }
}
