package org.kgen.runtime.compile

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.kgen.target.jvm.AccessFlags
import org.kgen.target.jvm.ClassFileBuilder

class SubsetValidatorTest {

    private fun buildClass(block: ClassFileBuilder.() -> Unit) =
        ClassFileBuilder("com/example/Test").apply(block).build()

    @Test
    fun `valid static method with primitives passes`() {
        val cf = buildClass {
            method("add", "(II)I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.iload(0)
                code.iload(1)
                code.iadd()
                code.ireturn()
            }
        }
        val errors = SubsetValidator.validate(cf)
        assertTrue(errors.isEmpty(), "Expected no errors but got: $errors")
    }

    @Test
    fun `non-static method is allowed`() {
        val cf = buildClass {
            method("compute", "(I)I", AccessFlags.PUBLIC) { code ->
                code.iload(0)
                code.ireturn()
            }
        }
        val errors = SubsetValidator.validate(cf)
        assertTrue(errors.isEmpty(), "Instance methods should be allowed but got: $errors")
    }

    @Test
    fun `clinit is exempt from static requirement`() {
        val cf = buildClass {
            method("<clinit>", "()V", 0) { code ->
                code.return_()
            }
        }
        val errors = SubsetValidator.validate(cf)
        assertTrue(errors.isEmpty(), "Expected no errors for <clinit> but got: $errors")
    }

    @Test
    fun `object type in descriptor is allowed`() {
        val cf = buildClass {
            method("ok", "(Ljava/lang/String;)V", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.return_()
            }
        }
        val errors = SubsetValidator.validate(cf)
        assertTrue(errors.isEmpty(), "Object params should be allowed but got: $errors")
    }

    @Test
    fun `array type in descriptor is allowed`() {
        val cf = buildClass {
            method("ok", "([I)V", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.return_()
            }
        }
        val errors = SubsetValidator.validate(cf)
        assertTrue(errors.isEmpty(), "Array params should be allowed but got: $errors")
    }

    @Test
    fun `object return type in descriptor is allowed`() {
        val cf = buildClass {
            method("ok", "()Ljava/lang/Object;", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.aconstNull()
                code.areturn()
            }
        }
        val errors = SubsetValidator.validate(cf)
        assertTrue(errors.isEmpty(), "Object return should be allowed but got: $errors")
    }

    @Test
    fun `array return type in descriptor is allowed`() {
        val cf = buildClass {
            method("ok", "()[I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.aconstNull()
                code.areturn()
            }
        }
        val errors = SubsetValidator.validate(cf)
        assertTrue(errors.isEmpty(), "Array return should be allowed but got: $errors")
    }

    @Test
    fun `all primitive param types are allowed`() {
        val cf = buildClass {
            method("allPrimitives", "(ZBSIJFD)V", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.return_()
            }
        }
        val errors = SubsetValidator.validate(cf)
        assertTrue(errors.isEmpty(), "Expected no errors but got: $errors")
    }

    @Test
    fun `void return type is allowed`() {
        val cf = buildClass {
            method("noop", "()V", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.return_()
            }
        }
        val errors = SubsetValidator.validate(cf)
        assertTrue(errors.isEmpty())
    }

    // --- Bytecode validation tests ---

    @Test
    fun `arithmetic opcodes are allowed`() {
        val cf = buildClass {
            method("arith", "(II)I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.iload(0)
                code.iload(1)
                code.iadd()
                code.iload(0)
                code.isub()
                code.iload(1)
                code.imul()
                code.ireturn()
            }
        }
        val errors = SubsetValidator.validate(cf)
        assertTrue(errors.isEmpty(), "Expected no errors but got: $errors")
    }

    @Test
    fun `new object is allowed`() {
        val cf = buildClass {
            method("ok", "()V", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.new_("java/lang/Object")
                code.dup()
                code.invokespecial("java/lang/Object", "<init>", "()V")
                code.pop()
                code.return_()
            }
        }
        val errors = SubsetValidator.validate(cf)
        assertTrue(errors.isEmpty(), "new+invokespecial should be allowed but got: $errors")
    }

    @Test
    fun `invokevirtual is allowed`() {
        val cf = buildClass {
            method("ok", "()V", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.aconstNull()
                code.invokevirtual("java/lang/Object", "toString", "()Ljava/lang/String;")
                code.pop()
                code.return_()
            }
        }
        val errors = SubsetValidator.validate(cf)
        assertTrue(errors.isEmpty(), "invokevirtual should be allowed but got: $errors")
    }

    @Test
    fun `invokespecial is allowed`() {
        val cf = buildClass {
            method("ok", "()V", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.new_("java/lang/Object")
                code.dup()
                code.invokespecial("java/lang/Object", "<init>", "()V")
                code.pop()
                code.return_()
            }
        }
        val errors = SubsetValidator.validate(cf)
        assertTrue(errors.isEmpty(), "invokespecial should be allowed but got: $errors")
    }

    @Test
    fun `newarray is allowed`() {
        val cf = buildClass {
            method("ok", "()V", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.iconst(10)
                code.newarray(10) // T_INT
                code.pop()
                code.return_()
            }
        }
        val errors = SubsetValidator.validate(cf)
        assertTrue(errors.isEmpty(), "newarray should be allowed but got: $errors")
    }

    @Test
    fun `anewarray is allowed`() {
        val cf = buildClass {
            method("ok", "()V", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.iconst(5)
                code.anewarray("java/lang/Object")
                code.pop()
                code.return_()
            }
        }
        val errors = SubsetValidator.validate(cf)
        assertTrue(errors.isEmpty(), "anewarray should be allowed but got: $errors")
    }

    @Test
    fun `athrow is allowed`() {
        val cf = buildClass {
            method("bad", "()V", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.aconstNull()
                code.athrow()
            }
        }
        val errors = SubsetValidator.validate(cf)
        assertTrue(errors.isEmpty(), "athrow should be allowed but got: $errors")
    }

    @Test
    fun `checkcast is allowed`() {
        val cf = buildClass {
            method("bad", "()V", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.aconstNull()
                code.checkcast("java/lang/String")
                code.pop()
                code.return_()
            }
        }
        val errors = SubsetValidator.validate(cf)
        assertTrue(errors.none { it.message.contains("checkcast") }, "checkcast should be allowed: $errors")
    }

    @Test
    fun `instanceof is allowed`() {
        val cf = buildClass {
            method("bad", "()V", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.aconstNull()
                code.instanceof_("java/lang/String")
                code.pop()
                code.return_()
            }
        }
        val errors = SubsetValidator.validate(cf)
        assertTrue(errors.none { it.message.contains("instanceof") }, "instanceof should be allowed: $errors")
    }

    @Test
    fun `getfield is allowed`() {
        val cf = buildClass {
            method("ok", "()V", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.aconstNull()
                code.getfield("java/lang/Object", "x", "I")
                code.pop()
                code.return_()
            }
        }
        val errors = SubsetValidator.validate(cf)
        assertTrue(errors.isEmpty(), "getfield should be allowed but got: $errors")
    }

    @Test
    fun `invokestatic to Kgen intrinsics is allowed`() {
        val cf = buildClass {
            method("test", "(J)I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.lload(0)
                code.invokestatic("org/kgen/unmanaged/Kgen", "loadInt", "(J)I")
                code.ireturn()
            }
        }
        val errors = SubsetValidator.validate(cf)
        assertTrue(errors.isEmpty(), "Expected no errors for Kgen intrinsic but got: $errors")
    }

    @Test
    fun `invokestatic to other class is allowed`() {
        val cf = buildClass {
            method("test", "()I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.invokestatic("com/example/OtherRuntime", "compute", "()I")
                code.ireturn()
            }
        }
        val errors = SubsetValidator.validate(cf)
        // Static calls to non-Kgen classes are allowed (validated at link time)
        assertTrue(errors.isEmpty(), "Expected no errors for static call but got: $errors")
    }

    @Test
    fun `branches and comparisons are allowed`() {
        val cf = buildClass {
            method("max", "(II)I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.iload(0)
                code.iload(1)
                code.ifIcmpge("first")
                code.iload(1)
                code.ireturn()
                code.label("first")
                code.iload(0)
                code.ireturn()
            }
        }
        val errors = SubsetValidator.validate(cf)
        assertTrue(errors.isEmpty(), "Expected no errors but got: $errors")
    }

    @Test
    fun `conversions are allowed`() {
        val cf = buildClass {
            method("convert", "(I)J", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.iload(0)
                code.i2l()
                code.lreturn()
            }
        }
        val errors = SubsetValidator.validate(cf)
        assertTrue(errors.isEmpty(), "Expected no errors but got: $errors")
    }

    @Test
    fun `bitwise operations are allowed`() {
        val cf = buildClass {
            method("bits", "(II)I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.iload(0)
                code.iload(1)
                code.iand()
                code.iload(1)
                code.ior()
                code.iload(0)
                code.ixor()
                code.ireturn()
            }
        }
        val errors = SubsetValidator.validate(cf)
        assertTrue(errors.isEmpty(), "Expected no errors but got: $errors")
    }

    @Test
    fun `shift operations are allowed`() {
        val cf = buildClass {
            method("shift", "(II)I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.iload(0)
                code.iload(1)
                code.ishl()
                code.iload(1)
                code.ishr()
                code.iload(1)
                code.iushr()
                code.ireturn()
            }
        }
        val errors = SubsetValidator.validate(cf)
        assertTrue(errors.isEmpty(), "Expected no errors but got: $errors")
    }

    @Test
    fun `iinc is allowed`() {
        val cf = buildClass {
            method("inc", "(I)I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.maxLocals = 1
                code.iinc(0, 1)
                code.iload(0)
                code.ireturn()
            }
        }
        val errors = SubsetValidator.validate(cf)
        assertTrue(errors.isEmpty(), "Expected no errors but got: $errors")
    }

    @Test
    fun `multiple errors are accumulated`() {
        val cf = buildClass {
            method("bad2", "()V", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                // Use reserved opcodes 0xFE and 0xFF which are unsupported
                code.assembler.emit(0xFE)
                code.assembler.emit(0xFF)
                code.return_()
            }
        }
        val errors = SubsetValidator.validate(cf)
        assertTrue(errors.size >= 2, "Expected multiple errors but got: $errors")
    }

    @Test
    fun `validation error toString format`() {
        val error = SubsetValidator.ValidationError("test", 5, "something wrong")
        assertEquals("test@5: something wrong", error.toString())
    }

    @Test
    fun `long and double parameters are allowed`() {
        val cf = buildClass {
            method("addLongs", "(JJ)J", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.lload(0)
                code.lload(2)
                code.ladd()
                code.lreturn()
            }
        }
        val errors = SubsetValidator.validate(cf)
        assertTrue(errors.isEmpty(), "Expected no errors but got: $errors")
    }

    @Test
    fun `float arithmetic is allowed`() {
        val cf = buildClass {
            method("addFloats", "(FF)F", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.fload(0)
                code.fload(1)
                code.fadd()
                code.freturn()
            }
        }
        val errors = SubsetValidator.validate(cf)
        assertTrue(errors.isEmpty(), "Expected no errors but got: $errors")
    }

    @Test
    fun `double arithmetic is allowed`() {
        val cf = buildClass {
            method("addDoubles", "(DD)D", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.dload(0)
                code.dload(2)
                code.dadd()
                code.dreturn()
            }
        }
        val errors = SubsetValidator.validate(cf)
        assertTrue(errors.isEmpty(), "Expected no errors but got: $errors")
    }

    @Test
    fun `stack operations dup and pop are allowed`() {
        val cf = buildClass {
            method("test", "(I)I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.iload(0)
                code.dup()
                code.pop()
                code.ireturn()
            }
        }
        val errors = SubsetValidator.validate(cf)
        assertTrue(errors.isEmpty(), "Expected no errors but got: $errors")
    }

    @Test
    fun `ldc numeric constants are allowed`() {
        val cf = buildClass {
            method("bigConst", "()I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.iconst(100000) // uses ldc for large constants
                code.ireturn()
            }
        }
        val errors = SubsetValidator.validate(cf)
        assertTrue(errors.isEmpty(), "Expected no errors but got: $errors")
    }

    @Test
    fun `arraylength is allowed`() {
        val cf = buildClass {
            method("ok", "()V", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.aconstNull()
                code.arraylength()
                code.pop()
                code.return_()
            }
        }
        val errors = SubsetValidator.validate(cf)
        assertTrue(errors.isEmpty(), "arraylength should be allowed but got: $errors")
    }

    @Test
    fun `nop is allowed`() {
        val cf = buildClass {
            method("test", "()V", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.assembler.nop()
                code.return_()
            }
        }
        val errors = SubsetValidator.validate(cf)
        assertTrue(errors.isEmpty(), "Expected no errors but got: $errors")
    }

    @Test
    fun `goto is allowed`() {
        val cf = buildClass {
            method("test", "()V", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.goto("end")
                code.label("end")
                code.return_()
            }
        }
        val errors = SubsetValidator.validate(cf)
        assertTrue(errors.isEmpty(), "Expected no errors but got: $errors")
    }

    @Test
    fun `ifnull and ifnonnull are allowed`() {
        val cf = buildClass {
            method("test", "()V", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.aconstNull()
                code.ifnull("ok")
                code.return_()
                code.label("ok")
                code.return_()
            }
        }
        val errors = SubsetValidator.validate(cf)
        assertTrue(errors.isEmpty(), "Expected no errors but got: $errors")
    }

    @Test
    fun `comparison operations are allowed`() {
        val cf = buildClass {
            method("test", "(JJ)I", AccessFlags.PUBLIC or AccessFlags.STATIC) { code ->
                code.lload(0)
                code.lload(2)
                code.lcmp()
                code.ireturn()
            }
        }
        val errors = SubsetValidator.validate(cf)
        assertTrue(errors.isEmpty(), "Expected no errors but got: $errors")
    }

    @Test
    fun `method with no Code attribute is skipped`() {
        val cf = buildClass {
            method("nativeMethod", "()V", AccessFlags.PUBLIC or AccessFlags.STATIC or AccessFlags.NATIVE)
        }
        val errors = SubsetValidator.validate(cf)
        assertTrue(errors.isEmpty(), "Expected no errors for native method but got: $errors")
    }
}
